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

/**
 * Uploads a package to the long term archiving service.
 */
public interface MCRLTATransport {

    /**
     * Uploads a package. Content of a former upload of the same object is replaced.
     *
     * @param pkg the package
     * @return the subdirectory that contains the package, relative to the configured base directory
     * @throws IOException if the upload fails
     */
    String upload(MCRLTAPackage pkg) throws IOException;

}
