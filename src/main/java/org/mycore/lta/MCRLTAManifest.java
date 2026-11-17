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

package org.mycore.lta;

import java.util.ArrayList;
import java.util.List;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;

import org.mycore.common.MCRException;
import org.mycore.common.content.MCRContent;
import org.mycore.common.content.MCRJAXBContent;

/**
 * The packing list of a package: the files that remain after filtering, their location in the package, size and
 * MD5 checksum. These values are not part of the object XML but are required for the METS document.
 * <p>
 * As XML, a manifest looks like this:
 * <pre>{@code
 * <manifest objectId="mir_mods_00001234" reason="initial submission">
 *   <derivate id="mir_derivate_00005678">
 *     <file number="1" path="content/streams/mir_derivate_00005678/foo.pdf" originalPath="/foo.pdf"
 *           size="12345" md5="..."/>
 *   </derivate>
 * </manifest>
 * }</pre>
 */
@XmlRootElement(name = "manifest")
@XmlAccessorType(XmlAccessType.FIELD)
public final class MCRLTAManifest {

    private static final JAXBContext CONTEXT = createContext();

    @XmlAttribute(name = "objectId", required = true)
    private String objectId;

    @XmlAttribute(name = "reason")
    private String reason;

    @XmlElement(name = "derivate")
    private List<Derivate> derivates = new ArrayList<>();

    /**
     * Required by JAXB.
     */
    public MCRLTAManifest() {
    }

    /**
     * Creates a new manifest.
     *
     * @param objectId the ID of the object
     * @param reason the reason of the submission
     */
    public MCRLTAManifest(String objectId, String reason) {
        this.objectId = objectId;
        this.reason = reason;
    }

    private static JAXBContext createContext() {
        try {
            return JAXBContext.newInstance(MCRLTAManifest.class);
        } catch (JAXBException e) {
            throw new MCRException("Unable to create JAXB context for LTA manifest", e);
        }
    }

    /**
     * @return the manifest as XML content
     */
    public MCRContent toContent() {
        return new MCRJAXBContent<>(CONTEXT, this);
    }

    /**
     * Adds a derivate to the manifest.
     *
     * @param derivateId the ID of the derivate
     * @return the added derivate
     */
    public Derivate addDerivate(String derivateId) {
        Derivate derivate = new Derivate(derivateId);
        derivates.add(derivate);
        return derivate;
    }

    /**
     * @return the number of files in all derivates
     */
    public int getFileCount() {
        return derivates.stream().mapToInt(d -> d.getFiles().size()).sum();
    }

    /**
     * @return the ID of the object
     */
    public String getObjectId() {
        return objectId;
    }

    /**
     * @return the reason of the submission
     */
    public String getReason() {
        return reason;
    }

    /**
     * @return the derivates of the manifest
     */
    public List<Derivate> getDerivates() {
        return derivates;
    }

    /**
     * A derivate of the object with its files.
     */
    @XmlType(name = "derivate")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static final class Derivate {

        @XmlAttribute(name = "id", required = true)
        private String id;

        @XmlElement(name = "file")
        private List<File> files = new ArrayList<>();

        /**
         * Required by JAXB.
         */
        public Derivate() {
        }

        private Derivate(String id) {
            this.id = id;
        }

        /**
         * @return the ID of the derivate
         */
        public String getId() {
            return id;
        }

        /**
         * @return the files of the derivate
         */
        public List<File> getFiles() {
            return files;
        }

    }

    /**
     * A file in the package.
     */
    @XmlType(name = "file")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static final class File {

        @XmlAttribute(name = "number", required = true)
        private int number;

        @XmlAttribute(name = "path", required = true)
        private String path;

        @XmlAttribute(name = "originalPath", required = true)
        private String originalPath;

        @XmlAttribute(name = "size", required = true)
        private long size;

        @XmlAttribute(name = "md5", required = true)
        private String md5;

        /**
         * Required by JAXB.
         */
        public File() {
        }

        /**
         * Creates a new file entry.
         *
         * @param number the consecutive number of the file in the manifest, starting with 1
         * @param path the path in the package
         * @param originalPath the path in the derivate, starting with a slash
         * @param size the size in bytes
         * @param md5 the MD5 checksum as lower case hex string
         */
        public File(int number, String path, String originalPath, long size, String md5) {
            this.number = number;
            this.path = path;
            this.originalPath = originalPath;
            this.size = size;
            this.md5 = md5;
        }

        /**
         * @return the consecutive number of the file in the manifest
         */
        public int getNumber() {
            return number;
        }

        /**
         * @return the path in the package
         */
        public String getPath() {
            return path;
        }

        /**
         * @return the path in the derivate, starting with a slash
         */
        public String getOriginalPath() {
            return originalPath;
        }

        /**
         * @return the size in bytes
         */
        public long getSize() {
            return size;
        }

        /**
         * @return the MD5 checksum as lower case hex string
         */
        public String getMd5() {
            return md5;
        }

    }

}
