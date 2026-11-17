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

package org.mycore.lta.condition;

import org.jdom2.Document;
import org.jdom2.filter.Filters;
import org.jdom2.xpath.XPathExpression;
import org.jdom2.xpath.XPathFactory;
import org.mycore.common.MCRConstants;
import org.mycore.common.config.annotation.MCRPostConstruction;
import org.mycore.common.config.annotation.MCRProperty;
import org.mycore.datamodel.metadata.MCRObject;

/**
 * Matches objects whose XML contains at least one node selected by an XPath expression. The standard MyCoRe
 * namespace prefixes (e.g. <code>mods</code>, <code>xlink</code>) can be used.
 * <p>
 * Properties:
 * <ul>
 *   <li><code>XPath</code>: the XPath expression, e.g.
 *   <code>/mycoreobject[.//mods:genre[contains(@valueURI,'#dissertation')]]</code></li>
 * </ul>
 */
public class MCRLTAXPathCondition implements MCRLTACondition {

    /**
     * The XPath expression.
     */
    @MCRProperty(name = "XPath")
    public String xPath;

    private XPathExpression<Object> expression;

    /**
     * Compiles the expression.
     */
    @MCRPostConstruction
    public void compile() {
        expression = XPathFactory.instance().compile(xPath, Filters.fpassthrough(), null,
            MCRConstants.getStandardNamespaces());
    }

    @Override
    public boolean matches(MCRObject object) {
        Document xml = object.createXML();
        Object result = expression.evaluateFirst(xml);
        return result != null && !Boolean.FALSE.equals(result);
    }

}
