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

import org.mycore.datamodel.metadata.MCRDerivate;

/**
 * Decides which files of a derivate are part of a package.
 */
public interface MCRLTAFileFilter {

    /**
     * @param derivate the derivate
     * @param path the path of the file in the derivate, starting with a slash
     * @return true, if the file is part of the package
     */
    boolean accept(MCRDerivate derivate, String path);

}
