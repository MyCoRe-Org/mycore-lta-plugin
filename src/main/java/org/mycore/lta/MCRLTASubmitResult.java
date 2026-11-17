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

import java.time.Instant;
import java.util.Objects;

/**
 * The result of creating a deposit in a long term archiving service.
 *
 * @param depositId the ID of the deposit
 * @param submissionId the ID of the submission information package
 * @param created the creation date of the deposit
 */
public record MCRLTASubmitResult(String depositId, String submissionId, Instant created) {

    /**
     * Creates a new result.
     *
     * @param depositId the ID of the deposit
     * @param submissionId the ID of the submission information package
     * @param created the creation date of the deposit
     */
    public MCRLTASubmitResult {
        Objects.requireNonNull(depositId, "depositId must not be null");
    }

}
