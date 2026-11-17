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

package org.mycore.lta.report;

import java.time.Instant;
import java.util.Objects;

/**
 * A row of a report of the archive that confirms a submission.
 *
 * @param rowNumber the number of the row in the report, starting with 1 for the header
 * @param entityId the ID of the archived entity, e.g. the IE PID
 * @param submissionId the ID of the submission information package
 * @param created the creation date of the archived entity, may be null
 * @param objectId the ID of the MyCoRe object according to the report, may be null
 */
public record MCRLTAReportRow(int rowNumber, String entityId, String submissionId, Instant created,
    String objectId) {

    /**
     * Creates a new row.
     *
     * @param rowNumber the number of the row in the report
     * @param entityId the ID of the archived entity
     * @param submissionId the ID of the submission information package
     * @param created the creation date of the archived entity, may be null
     * @param objectId the ID of the MyCoRe object according to the report, may be null
     */
    public MCRLTAReportRow {
        Objects.requireNonNull(entityId, "entityId must not be null");
        Objects.requireNonNull(submissionId, "submissionId must not be null");
    }

}
