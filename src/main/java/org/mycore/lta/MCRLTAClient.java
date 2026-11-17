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
 * Communicates with the long term archiving service: creates deposits and queries their status.
 */
public interface MCRLTAClient {

    /**
     * Creates a deposit for an uploaded package.
     *
     * @param subdirectory the subdirectory returned by {@link MCRLTATransport#upload(MCRLTAPackage)}
     * @return the result of the deposit
     * @throws IOException if the deposit can not be created
     */
    MCRLTASubmitResult submit(String subdirectory) throws IOException;

    /**
     * Queries the status of a deposit.
     *
     * @param depositId the ID of the deposit
     * @return the status
     * @throws IOException if the status can not be queried
     */
    MCRLTADepositStatus getStatus(String depositId) throws IOException;

}
