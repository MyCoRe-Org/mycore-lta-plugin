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

import java.time.Instant;

import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;

import org.jdom2.Element;
import org.jdom2.transform.JDOMSource;
import org.mycore.datamodel.metadata.MCRMetadataManager;
import org.mycore.datamodel.metadata.MCRObject;
import org.mycore.datamodel.metadata.MCRObjectID;

/**
 * Returns the submissions of an object as XML. Syntax: <code>lta:{objectId}</code>.
 * <p>
 * The submissions are read from the service flags of the stored object, so the result does not depend on the
 * order of the event handlers. Example:
 * <pre>{@code
 * <lta>
 *   <submission service="TIB" state="VERIFIED" created="..." submissionId="..." entityId="IE12345" />
 * </lta>
 * }</pre>
 */
public class MCRLTAResolver implements URIResolver {

    @Override
    public Source resolve(String href, String base) throws TransformerException {
        String id = href.substring(href.indexOf(':') + 1);
        if (!MCRObjectID.isValid(id)) {
            throw new TransformerException("Invalid object ID: " + href);
        }
        MCRObjectID objectId = MCRObjectID.getInstance(id);
        Element lta = new Element("lta");
        lta.setAttribute("objectId", id);
        if (MCRMetadataManager.exists(objectId)) {
            MCRObject object = MCRMetadataManager.retrieveMCRObject(objectId);
            for (MCRLTASubmission submission : MCRLTAFlags.list(object)) {
                lta.addContent(toElement(submission));
            }
        }
        return new JDOMSource(lta);
    }

    private static Element toElement(MCRLTASubmission submission) {
        Element element = new Element("submission");
        set(element, "service", submission.getService());
        set(element, "state", submission.getState().name());
        set(element, "created", submission.getCreated());
        set(element, "reason", submission.getReason());
        set(element, "depositId", submission.getDepositId());
        set(element, "submissionId", submission.getSubmissionId());
        set(element, "depositState", submission.getDepositState());
        set(element, "verified", submission.getVerified());
        set(element, "reportFileName", submission.getReportFileName());
        set(element, "processed", submission.getProcessed());
        set(element, "entityId", submission.getEntityId());
        return element;
    }

    private static void set(Element element, String name, String value) {
        if (value != null) {
            element.setAttribute(name, value);
        }
    }

    private static void set(Element element, String name, Instant value) {
        if (value != null) {
            element.setAttribute(name, value.toString());
        }
    }

}
