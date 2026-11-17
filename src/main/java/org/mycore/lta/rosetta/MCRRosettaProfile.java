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

import org.mycore.common.config.annotation.MCRInstance;
import org.mycore.common.config.annotation.MCRProperty;
import org.mycore.datamodel.metadata.MCRObject;
import org.mycore.lta.condition.MCRLTACondition;

/**
 * The values of the holding structure (Bestandsstrukturierung) agreed with the archive, used for objects that
 * match the condition of the profile.
 * <p>
 * Properties:
 * <ul>
 *   <li><code>Condition</code> (optional): configurable instance of {@link MCRLTACondition}, a profile without
 *   condition matches every object</li>
 *   <li><code>IeEntityType</code>, <code>PreservationType</code>, <code>UsageType</code>: required values</li>
 *   <li><code>UserDefinedA</code>, <code>UserDefinedB</code>, <code>UserDefinedC</code>: optional values</li>
 * </ul>
 */
public final class MCRRosettaProfile {

    /**
     * The condition, may be null.
     */
    @MCRInstance(name = "Condition", valueClass = MCRLTACondition.class, required = false)
    public MCRLTACondition condition;

    /**
     * The IE entity type.
     */
    @MCRProperty(name = "IeEntityType")
    public String ieEntityType;

    /**
     * The user defined value A, may be null.
     */
    @MCRProperty(name = "UserDefinedA", required = false)
    public String userDefinedA;

    /**
     * The user defined value B, may be null.
     */
    @MCRProperty(name = "UserDefinedB", required = false)
    public String userDefinedB;

    /**
     * The user defined value C, may be null.
     */
    @MCRProperty(name = "UserDefinedC", required = false)
    public String userDefinedC;

    /**
     * The preservation type of the representation.
     */
    @MCRProperty(name = "PreservationType")
    public String preservationType;

    /**
     * The usage type of the representation.
     */
    @MCRProperty(name = "UsageType")
    public String usageType;

    /**
     * @param object the object
     * @return true, if the profile applies to the object
     */
    public boolean matches(MCRObject object) {
        return condition == null || condition.matches(object);
    }

}
