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

package org.mycore.lta.filter;

import java.util.Set;
import java.util.stream.Collectors;

import org.mycore.common.config.MCRConfiguration2;
import org.mycore.common.config.annotation.MCRProperty;
import org.mycore.datamodel.metadata.MCRDerivate;

/**
 * Accepts derivates by their type, i.e. by their category in the classification <code>derivate_types</code>.
 * <p>
 * Properties:
 * <ul>
 *   <li><code>Types</code>: comma separated list of accepted categories, e.g. <code>content</code></li>
 *   <li><code>ClassificationId</code>: the classification of the types, defaults to
 *   <code>MCR.LTA.Default.DerivateTypeClassification</code></li>
 * </ul>
 */
public class MCRLTADerivateTypeFilter implements MCRLTADerivateFilter {

    /**
     * The classification of the types.
     */
    @MCRProperty(name = "ClassificationId", defaultName = "MCR.LTA.Default.DerivateTypeClassification")
    public String classificationId;

    private Set<String> types = Set.of();

    /**
     * Sets the accepted types.
     *
     * @param value comma separated list of categories
     */
    @MCRProperty(name = "Types")
    public void setTypes(String value) {
        this.types = MCRConfiguration2.splitValue(value).collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public boolean accept(MCRDerivate derivate) {
        return derivate.getDerivate().getClassifications().stream()
            .filter(c -> classificationId.equals(c.getClassId()))
            .anyMatch(c -> types.contains(c.getCategId()));
    }

}
