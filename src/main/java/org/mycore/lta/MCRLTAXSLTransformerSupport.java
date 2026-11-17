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

import org.mycore.common.content.MCRContent;
import org.mycore.common.content.transformer.MCRContentTransformer;
import org.mycore.common.content.transformer.MCRContentTransformerFactory;
import org.mycore.common.content.transformer.MCRParameterizedTransformer;
import org.mycore.common.xsl.MCRParameterCollector;

/**
 * Runs a configured content transformer, passing parameters if the transformer supports them.
 */
final class MCRLTAXSLTransformerSupport {

    private MCRLTAXSLTransformerSupport() {
    }

    /**
     * Transforms the source.
     *
     * @param transformerId the ID of the content transformer
     * @param parameters the parameters for the transformer
     * @param source the source
     * @return the result
     * @throws IOException if the transformation fails
     */
    static MCRContent transform(String transformerId, Map<String, String> parameters, MCRContent source)
        throws IOException {
        MCRContentTransformer transformer = MCRContentTransformerFactory.getTransformer(transformerId);
        if (transformer == null) {
            throw new IOException("Content transformer " + transformerId + " is not configured");
        }
        if (transformer instanceof MCRParameterizedTransformer parameterized) {
            MCRParameterCollector collector = new MCRParameterCollector();
            collector.setParameters(parameters);
            return parameterized.transform(source, collector);
        }
        if (!parameters.isEmpty()) {
            throw new IOException("Content transformer " + transformerId + " does not accept parameters");
        }
        return transformer.transform(source);
    }

}
