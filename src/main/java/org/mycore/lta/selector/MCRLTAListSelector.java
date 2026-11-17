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

package org.mycore.lta.selector;

import java.util.List;

import org.mycore.common.config.MCRConfiguration2;
import org.mycore.common.config.annotation.MCRProperty;
import org.mycore.datamodel.metadata.MCRObjectID;

/**
 * Selects a fixed list of objects. Useful for tests and for archiving a small, known set of objects.
 * <p>
 * Properties:
 * <ul>
 *   <li><code>ObjectIds</code>: comma separated list of object IDs</li>
 * </ul>
 */
public class MCRLTAListSelector implements MCRLTASelector {

    private List<MCRObjectID> objectIds = List.of();

    /**
     * Sets the selected objects.
     *
     * @param ids comma separated list of object IDs
     */
    @MCRProperty(name = "ObjectIds")
    public void setObjectIds(String ids) {
        this.objectIds = MCRConfiguration2.splitValue(ids).map(MCRObjectID::getInstance).toList();
    }

    @Override
    public MCRLTASelection open() {
        return new MCRLTASelection() {

            private int position;

            @Override
            public List<MCRObjectID> next(int limit) {
                int end = Math.min(objectIds.size(), position + limit);
                List<MCRObjectID> page = objectIds.subList(position, end);
                position = end;
                return page;
            }

            @Override
            public void close() {
                position = objectIds.size();
            }

        };
    }

}
