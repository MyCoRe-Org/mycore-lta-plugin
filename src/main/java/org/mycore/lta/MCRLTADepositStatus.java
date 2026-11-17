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

import java.util.Objects;

/**
 * The status of a deposit as reported by a long term archiving service.
 *
 * @param depositState the raw state reported by the archive
 * @param state the resulting state of the submission, {@link MCRLTAState#SUBMITTED} if processing is not finished
 */
public record MCRLTADepositStatus(String depositState, MCRLTAState state) {

    /**
     * Creates a new status.
     *
     * @param depositState the raw state reported by the archive
     * @param state the resulting state of the submission
     */
    public MCRLTADepositStatus {
        Objects.requireNonNull(depositState, "depositState must not be null");
        Objects.requireNonNull(state, "state must not be null");
        if (state == MCRLTAState.VERIFIED) {
            throw new IllegalArgumentException("A deposit status can not verify a submission");
        }
    }

    /**
     * @return true, if the archive has not finished processing the deposit
     */
    public boolean isPending() {
        return state == MCRLTAState.SUBMITTED;
    }

}
