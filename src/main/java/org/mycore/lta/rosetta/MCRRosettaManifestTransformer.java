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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mycore.common.config.annotation.MCRInstanceList;
import org.mycore.common.config.annotation.MCRProperty;
import org.mycore.common.content.MCRContent;
import org.mycore.common.content.transformer.MCRContentTransformer;
import org.mycore.common.content.transformer.MCRContentTransformerFactory;
import org.mycore.common.content.transformer.MCRParameterizedTransformer;
import org.mycore.common.xsl.MCRParameterCollector;
import org.mycore.datamodel.metadata.MCRMetadataManager;
import org.mycore.datamodel.metadata.MCRObject;
import org.mycore.datamodel.metadata.MCRObjectID;
import org.mycore.lta.MCRLTAException;
import org.mycore.lta.MCRLTAManifest;
import org.mycore.lta.MCRLTAManifestTransformer;

/**
 * Creates the METS document of a Rosetta package. The values of the holding structure are chosen by profiles:
 * the first profile whose condition matches the object wins. If no profile matches, the submission fails.
 * <p>
 * The METS document itself is created by the stylesheet of the configured content transformer, which receives the
 * values of the profile as parameters <code>DcTransformerId</code>, <code>Status</code>, <code>IeEntityType</code>, <code>UserDefinedA</code>,
 * <code>UserDefinedB</code>, <code>UserDefinedC</code>, <code>PreservationType</code>, <code>UsageType</code> and
 * <code>RevisionNumber</code>.
 * <p>
 * Properties:
 * <ul>
 *   <li><code>Status</code>: the status of the IE, e.g. <code>SUPPRESSED</code></li>
 *   <li><code>RevisionNumber</code>: the revision number of the representation, defaults to
 *   <code>MCR.LTA.Default.Rosetta.RevisionNumber</code></li>
 *   <li><code>TransformerId</code>: the content transformer of the METS stylesheet, defaults to
 *   <code>MCR.LTA.Default.Rosetta.MetsTransformerId</code></li>
 *   <li><code>DcTransformerId</code>: the content transformer of the Dublin Core description embedded into the
 *   METS document, defaults to <code>MCR.LTA.Default.Rosetta.DcTransformerId</code></li>
 *   <li><code>Profiles.{n}</code>: the profiles, see {@link MCRRosettaProfile}, ordered by number</li>
 * </ul>
 */
public class MCRRosettaManifestTransformer implements MCRLTAManifestTransformer {

    /**
     * The status of the IE.
     */
    @MCRProperty(name = "Status")
    public String status;

    /**
     * The revision number of the representation.
     */
    @MCRProperty(name = "RevisionNumber", defaultName = "MCR.LTA.Default.Rosetta.RevisionNumber")
    public String revisionNumber;

    /**
     * The content transformer of the METS stylesheet.
     */
    @MCRProperty(name = "TransformerId", defaultName = "MCR.LTA.Default.Rosetta.MetsTransformerId")
    public String transformerId;

    /**
     * The content transformer of the Dublin Core description.
     */
    @MCRProperty(name = "DcTransformerId", defaultName = "MCR.LTA.Default.Rosetta.DcTransformerId")
    public String dcTransformerId;

    /**
     * The profiles, ordered by number.
     */
    @MCRInstanceList(name = "Profiles", valueClass = MCRRosettaProfile.class)
    public List<MCRRosettaProfile> profiles;

    @Override
    public MCRContent transform(MCRLTAManifest manifest) throws IOException {
        MCRObject object = MCRMetadataManager.retrieveMCRObject(MCRObjectID.getInstance(manifest.getObjectId()));
        MCRRosettaProfile profile = selectProfile(object);
        MCRContentTransformer transformer = MCRContentTransformerFactory.getTransformer(transformerId);
        if (!(transformer instanceof MCRParameterizedTransformer parameterized)) {
            throw new IOException("Content transformer " + transformerId + " does not accept parameters");
        }
        MCRParameterCollector collector = new MCRParameterCollector();
        collector.setParameters(toParameters(profile));
        return parameterized.transform(manifest.toContent(), collector);
    }

    /**
     * Returns the first profile that matches the object.
     *
     * @param object the object
     * @return the profile
     * @throws MCRLTAException if no profile matches
     */
    public MCRRosettaProfile selectProfile(MCRObject object) {
        return profiles.stream()
            .filter(p -> p.matches(object))
            .findFirst()
            .orElseThrow(() -> new MCRLTAException("No Rosetta profile matches " + object.getId()));
    }

    private Map<String, String> toParameters(MCRRosettaProfile profile) {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("DcTransformerId", dcTransformerId);
        parameters.put("Status", status);
        parameters.put("RevisionNumber", revisionNumber);
        parameters.put("IeEntityType", profile.ieEntityType);
        parameters.put("PreservationType", profile.preservationType);
        parameters.put("UsageType", profile.usageType);
        putIfPresent(parameters, "UserDefinedA", profile.userDefinedA);
        putIfPresent(parameters, "UserDefinedB", profile.userDefinedB);
        putIfPresent(parameters, "UserDefinedC", profile.userDefinedC);
        return parameters;
    }

    private static void putIfPresent(Map<String, String> parameters, String key, String value) {
        if (value != null && !value.isBlank()) {
            parameters.put(key, value);
        }
    }

}
