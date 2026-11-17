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

import java.io.IOException;
import java.util.Map;

import org.mycore.common.config.annotation.MCRProperty;
import org.mycore.common.config.annotation.MCRRawProperties;
import org.mycore.common.content.MCRContent;
import org.mycore.common.content.MCRJDOMContent;
import org.mycore.datamodel.metadata.MCRObject;

/**
 * Transforms the XML of the object with a configured content transformer, usually an XSL stylesheet.
 * <p>
 * Properties:
 * <ul>
 *   <li><code>TransformerId</code>: the ID of the content transformer, configured with
 *   <code>MCR.ContentTransformer.{id}.*</code></li>
 *   <li><code>Parameters.*</code> (optional): parameters passed to the stylesheet, the key without prefix is the
 *   name of the parameter</li>
 * </ul>
 */
public class MCRLTAXSLObjectTransformer implements MCRLTAObjectTransformer {

    /**
     * The ID of the content transformer.
     */
    @MCRProperty(name = "TransformerId")
    public String transformerId;

    /**
     * The parameters passed to the transformer.
     */
    @MCRRawProperties(namePattern = "Parameters.*", required = false)
    public Map<String, String> parameters = Map.of();

    @Override
    public MCRContent transform(MCRObject object) throws IOException {
        return MCRLTAXSLTransformerSupport.transform(transformerId, parameters, new MCRJDOMContent(object.createXML()));
    }

}
