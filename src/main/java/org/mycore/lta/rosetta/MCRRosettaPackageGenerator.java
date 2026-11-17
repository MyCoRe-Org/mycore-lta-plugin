/*
 * This file is part of ***  M y C o R e  ***
 * See https://www.mycore.de/ for details.
 *
 * MyCoRe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MyCoRe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MyCoRe.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.mycore.lta.rosetta;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mycore.common.config.annotation.MCRInstance;
import org.mycore.common.config.annotation.MCRInstanceList;
import org.mycore.common.digest.MCRDigest;
import org.mycore.common.digest.MCRMD5Digest;
import org.mycore.datamodel.metadata.MCRDerivate;
import org.mycore.datamodel.metadata.MCRMetaEnrichedLinkID;
import org.mycore.datamodel.metadata.MCRMetadataManager;
import org.mycore.datamodel.metadata.MCRObject;
import org.mycore.datamodel.metadata.MCRObjectID;
import org.mycore.datamodel.niofs.MCRFileAttributes;
import org.mycore.datamodel.niofs.MCRPath;
import org.mycore.lta.MCRLTAManifest;
import org.mycore.lta.MCRLTAManifestTransformer;
import org.mycore.lta.MCRLTAObjectTransformer;
import org.mycore.lta.MCRLTAPackage;
import org.mycore.lta.MCRLTAPackageGenerator;
import org.mycore.lta.filter.MCRLTADerivateFilter;
import org.mycore.lta.filter.MCRLTAFileFilter;

/**
 * Builds packages in the layout expected by the Rosetta deposit of the TIB:
 * <pre>
 * dc.xml
 * content/ie1.xml
 * content/streams/{derivateId}/{path in derivate}
 * </pre>
 * The files are copied unchanged.
 * <p>
 * Configurable instances:
 * <ul>
 *   <li><code>DerivateFilters.{n}</code> (optional): all filters have to accept a derivate</li>
 *   <li><code>FileFilters.{n}</code> (optional): all filters have to accept a file</li>
 *   <li><code>ObjectTransformer</code>: creates <code>dc.xml</code> from the object</li>
 *   <li><code>ManifestTransformer</code>: creates <code>content/ie1.xml</code> (METS) from the manifest</li>
 * </ul>
 */
public class MCRRosettaPackageGenerator implements MCRLTAPackageGenerator {

    /**
     * The path of the descriptive metadata in the package.
     */
    public static final String DC_PATH = "dc.xml";

    /**
     * The path of the METS document in the package.
     */
    public static final String METS_PATH = "content/ie1.xml";

    /**
     * The directory of the files in the package.
     */
    public static final String STREAMS_PATH = "content/streams/";

    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * The filters of the derivates.
     */
    @MCRInstanceList(name = "DerivateFilters", valueClass = MCRLTADerivateFilter.class, required = false)
    public List<MCRLTADerivateFilter> derivateFilters = List.of();

    /**
     * The filters of the files.
     */
    @MCRInstanceList(name = "FileFilters", valueClass = MCRLTAFileFilter.class, required = false)
    public List<MCRLTAFileFilter> fileFilters = List.of();

    /**
     * The transformer of the object.
     */
    @MCRInstance(name = "ObjectTransformer", valueClass = MCRLTAObjectTransformer.class)
    public MCRLTAObjectTransformer objectTransformer;

    /**
     * The transformer of the manifest.
     */
    @MCRInstance(name = "ManifestTransformer", valueClass = MCRLTAManifestTransformer.class)
    public MCRLTAManifestTransformer manifestTransformer;

    @Override
    public MCRLTAPackage generate(MCRObject object, String reason) throws IOException {
        MCRObjectID objectId = object.getId();
        MCRLTAManifest manifest = new MCRLTAManifest(objectId.toString(), reason);
        List<MCRLTAPackage.Entry> files = new ArrayList<>();
        int number = 0;
        for (MCRMetaEnrichedLinkID link : object.getStructure().getDerivates()) {
            MCRObjectID derivateId = link.getXLinkHrefID();
            MCRDerivate derivate = MCRMetadataManager.retrieveMCRDerivate(derivateId);
            if (!derivateFilters.stream().allMatch(f -> f.accept(derivate))) {
                LOGGER.debug("Skipping derivate {} of {}", derivateId, objectId);
                continue;
            }
            MCRLTAManifest.Derivate manifestDerivate = manifest.addDerivate(derivateId.toString());
            for (MCRPath file : listFiles(derivateId)) {
                String originalPath = file.getOwnerRelativePath();
                if (!fileFilters.stream().allMatch(f -> f.accept(derivate, originalPath))) {
                    LOGGER.debug("Skipping file {} of {}", originalPath, derivateId);
                    continue;
                }
                String path = STREAMS_PATH + derivateId + originalPath;
                long size = Files.size(file);
                manifestDerivate.getFiles().add(new MCRLTAManifest.File(++number, path, originalPath, size,
                    md5(file)));
                files.add(MCRLTAPackage.Entry.ofFile(path, file));
            }
        }
        if (number == 0) {
            throw new IOException("No files of " + objectId + " are selected for archiving");
        }
        List<MCRLTAPackage.Entry> entries = new ArrayList<>();
        entries.add(MCRLTAPackage.Entry.ofBytes(DC_PATH, objectTransformer.transform(object).asByteArray()));
        entries.add(MCRLTAPackage.Entry.ofBytes(METS_PATH, manifestTransformer.transform(manifest).asByteArray()));
        entries.addAll(files);
        return new MCRLTAPackage(objectId, manifest, entries);
    }

    private static List<MCRPath> listFiles(MCRObjectID derivateId) throws IOException {
        MCRPath root = MCRPath.getRootPath(derivateId.toString());
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile)
                .map(MCRPath::ofPath)
                .sorted((a, b) -> a.getOwnerRelativePath().compareTo(b.getOwnerRelativePath()))
                .toList();
        }
    }

    @SuppressWarnings("rawtypes")
    private static String md5(MCRPath file) throws IOException {
        MCRFileAttributes attributes = Files.readAttributes(file, MCRFileAttributes.class);
        MCRDigest digest = attributes.digest();
        if (digest != null && MCRMD5Digest.ALGORITHM.equals(digest.getAlgorithm())) {
            return digest.toHexString().toLowerCase(java.util.Locale.ROOT);
        }
        return computeMd5(file);
    }

    private static String computeMd5(Path file) throws IOException {
        MessageDigest md5;
        try {
            md5 = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("MD5 is not supported", e);
        }
        byte[] buffer = new byte[64 * 1024];
        try (InputStream in = Files.newInputStream(file)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                md5.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(md5.digest());
    }

}
