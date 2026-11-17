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

import org.mycore.datamodel.metadata.MCRObjectID;

/**
 * A selection of candidates that is read page by page. The selection keeps its position itself.
 */
public interface MCRLTASelection extends AutoCloseable {

    /**
     * Returns the next page of candidates.
     *
     * @param limit the maximum number of IDs
     * @return the next IDs, at most <code>limit</code>; empty if there are no more
     */
    List<MCRObjectID> next(int limit);

    /**
     * Releases all resources of the selection.
     */
    @Override
    void close();

}
