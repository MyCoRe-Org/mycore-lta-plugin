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

import java.util.List;

/**
 * The content of a report of the archive.
 *
 * @param fileName the file name of the report
 * @param rows the valid rows
 * @param problems descriptions of rows that could not be read, these are reported and not silently ignored
 */
public record MCRLTAReport(String fileName, List<MCRLTAReportRow> rows, List<String> problems) {

    /**
     * Creates a new report.
     *
     * @param fileName the file name of the report
     * @param rows the valid rows
     * @param problems descriptions of rows that could not be read
     */
    public MCRLTAReport {
        rows = List.copyOf(rows);
        problems = List.copyOf(problems);
    }

}
