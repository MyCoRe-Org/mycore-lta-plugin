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

/**
 * The state of a single submission of a MyCoRe object to a long term archiving service.
 */
public enum MCRLTAState {

    /**
     * The package was uploaded and the deposit was created, the archive has not yet finished processing it.
     */
    SUBMITTED,

    /**
     * The archive reported that the deposit was processed successfully.
     */
    FINISHED,

    /**
     * The upload, the deposit or the processing in the archive failed. The object may be submitted again.
     */
    FAILED,

    /**
     * The report of the archive confirmed the submission and provided the identifier of the archived entity.
     */
    VERIFIED;

    /**
     * Returns whether a submission in this state prevents another submission of the same object to the same
     * service. Only failed submissions allow another attempt.
     *
     * @return true, if the state is open or successful
     */
    public boolean isBlocking() {
        return this != FAILED;
    }

}
