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

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.mycore.common.config.annotation.MCRPostConstruction;
import org.mycore.common.config.annotation.MCRProperty;

/**
 * Reads reports in CSV or XLSX format (detected by the file extension <code>.xlsx</code>). The first row contains
 * the column names; columns are found by name, so their order does not matter. Empty rows are skipped, rows with
 * missing or invalid values are collected as problems.
 * <p>
 * Properties:
 * <ul>
 *   <li><code>EntityIdColumn</code>: column of the ID of the archived entity, defaults to
 *   <code>MCR.LTA.Default.Report.EntityIdColumn</code></li>
 *   <li><code>SubmissionIdColumn</code>: column of the ID of the submission information package, defaults to
 *   <code>MCR.LTA.Default.Report.SubmissionIdColumn</code></li>
 *   <li><code>CreationDateColumn</code> (optional): column of the creation date of the entity</li>
 *   <li><code>ObjectIdColumn</code> (optional): column of the ID of the MyCoRe object</li>
 *   <li><code>DateFormat</code>, <code>DateZone</code>: format and time zone of text dates, defaults to
 *   <code>MCR.LTA.Default.Report.DateFormat</code> and <code>MCR.LTA.Default.Report.DateZone</code>. In XLSX
 *   files, numeric dates are supported as well.</li>
 *   <li><code>Separator</code>: the separator of CSV files, defaults to
 *   <code>MCR.LTA.Default.Report.Separator</code></li>
 *   <li><code>Charset</code>: the charset of CSV files, defaults to <code>MCR.LTA.Default.Report.Charset</code></li>
 * </ul>
 */
public class MCRLTATableReportImporter implements MCRLTAReportImporter {

    private static final long EXCEL_EPOCH_DAY = LocalDateTime.of(1899, 12, 30, 0, 0).toLocalDate().toEpochDay();

    private static final long SECONDS_PER_DAY = 86_400L;

    private static final char BYTE_ORDER_MARK = (char) 0xFEFF;

    /**
     * The column of the entity ID.
     */
    @MCRProperty(name = "EntityIdColumn", defaultName = "MCR.LTA.Default.Report.EntityIdColumn")
    public String entityIdColumn;

    /**
     * The column of the submission ID.
     */
    @MCRProperty(name = "SubmissionIdColumn", defaultName = "MCR.LTA.Default.Report.SubmissionIdColumn")
    public String submissionIdColumn;

    /**
     * The column of the creation date, may be null.
     */
    @MCRProperty(name = "CreationDateColumn", required = false)
    public String creationDateColumn;

    /**
     * The column of the object ID, may be null.
     */
    @MCRProperty(name = "ObjectIdColumn", required = false)
    public String objectIdColumn;

    /**
     * The format of text dates.
     */
    @MCRProperty(name = "DateFormat", defaultName = "MCR.LTA.Default.Report.DateFormat")
    public String dateFormat;

    /**
     * The time zone of dates.
     */
    @MCRProperty(name = "DateZone", defaultName = "MCR.LTA.Default.Report.DateZone")
    public String dateZone;

    /**
     * The separator of CSV files.
     */
    @MCRProperty(name = "Separator", defaultName = "MCR.LTA.Default.Report.Separator")
    public String separator;

    /**
     * The charset of CSV files.
     */
    @MCRProperty(name = "Charset", defaultName = "MCR.LTA.Default.Report.Charset")
    public String charset;

    private DateTimeFormatter formatter;

    private ZoneId zone;

    /**
     * Validates the configuration.
     */
    @MCRPostConstruction
    public void init() {
        formatter = DateTimeFormatter.ofPattern(dateFormat, Locale.ROOT);
        zone = ZoneId.of(dateZone);
        if (separator.length() != 1) {
            throw new IllegalArgumentException("Separator must be a single character: '" + separator + "'");
        }
    }

    @Override
    public MCRLTAReport read(Path file) throws IOException {
        String fileName = file.getFileName().toString();
        List<List<String>> table = fileName.toLowerCase(Locale.ROOT).endsWith(".xlsx")
            ? MCRLTAXlsxReader.read(file)
            : readCsv(file);
        return convert(fileName, table);
    }

    private List<List<String>> readCsv(Path file) throws IOException {
        CSVFormat format = CSVFormat.DEFAULT.builder().setDelimiter(separator.charAt(0)).get();
        List<List<String>> table = new ArrayList<>();
        try (Reader reader = Files.newBufferedReader(file, Charset.forName(charset));
            CSVParser parser = CSVParser.parse(reader, format)) {
            for (CSVRecord csvRecord : parser) {
                table.add(csvRecord.toList());
            }
        }
        return table;
    }

    /**
     * Converts a table into a report. The first non empty row is the header.
     *
     * @param fileName the file name of the report
     * @param table the rows and cells of the report
     * @return the report
     * @throws IOException if a required column is missing
     */
    public MCRLTAReport convert(String fileName, List<List<String>> table) throws IOException {
        List<MCRLTAReportRow> rows = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        int headerIndex = 0;
        while (headerIndex < table.size() && isEmpty(table.get(headerIndex))) {
            headerIndex++;
        }
        if (headerIndex == table.size()) {
            return new MCRLTAReport(fileName, rows, problems);
        }
        Map<String, Integer> header = indexHeader(table.get(headerIndex));
        int entityIdIndex = requireColumn(header, entityIdColumn, fileName);
        int submissionIdIndex = requireColumn(header, submissionIdColumn, fileName);
        int creationDateIndex = optionalColumn(header, creationDateColumn, fileName);
        int objectIdIndex = optionalColumn(header, objectIdColumn, fileName);
        for (int i = headerIndex + 1; i < table.size(); i++) {
            List<String> cells = table.get(i);
            int rowNumber = i + 1;
            if (isEmpty(cells)) {
                continue;
            }
            String entityId = cell(cells, entityIdIndex);
            String submissionId = normalizeId(cell(cells, submissionIdIndex));
            if (entityId.isEmpty() || submissionId.isEmpty()) {
                problems.add("Row " + rowNumber + ": missing " + (entityId.isEmpty() ? entityIdColumn
                    : submissionIdColumn));
                continue;
            }
            Instant created = null;
            if (creationDateIndex >= 0 && !cell(cells, creationDateIndex).isEmpty()) {
                try {
                    created = parseDate(cell(cells, creationDateIndex));
                } catch (DateTimeException | NumberFormatException e) {
                    problems.add("Row " + rowNumber + ": invalid date '" + cell(cells, creationDateIndex) + "'");
                    continue;
                }
            }
            String objectId = objectIdIndex >= 0 ? cell(cells, objectIdIndex) : "";
            rows.add(new MCRLTAReportRow(rowNumber, entityId, submissionId, created,
                objectId.isEmpty() ? null : objectId));
        }
        return new MCRLTAReport(fileName, rows, problems);
    }

    private static Map<String, Integer> indexHeader(List<String> cells) {
        Map<String, Integer> header = new HashMap<>();
        for (int i = 0; i < cells.size(); i++) {
            header.putIfAbsent(normalizeName(cells.get(i)), i);
        }
        return header;
    }

    private static int requireColumn(Map<String, Integer> header, String name, String fileName) throws IOException {
        Integer index = header.get(normalizeName(name));
        if (index == null) {
            throw new IOException("Column '" + name + "' not found in " + fileName + ", found " + header.keySet());
        }
        return index;
    }

    private static int optionalColumn(Map<String, Integer> header, String name, String fileName)
        throws IOException {
        if (name == null || name.isBlank()) {
            return -1;
        }
        return requireColumn(header, name, fileName);
    }

    private static String normalizeName(String name) {
        return name.replace(String.valueOf(BYTE_ORDER_MARK), "").trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isEmpty(List<String> cells) {
        return cells.stream().allMatch(c -> c == null || c.isBlank());
    }

    private static String cell(List<String> cells, int index) {
        if (index >= cells.size() || cells.get(index) == null) {
            return "";
        }
        return cells.get(index).trim();
    }

    /**
     * Normalizes an ID that may have been stored as a number, e.g. <code>1.2345E5</code> or <code>12345.0</code>
     * becomes <code>12345</code>.
     *
     * @param value the value of the cell
     * @return the normalized value
     */
    static String normalizeId(String value) {
        if (value.matches("[0-9]+(\\.[0-9]+)?([eE][+-]?[0-9]+)?") && (value.contains(".") || value.contains("E")
            || value.contains("e"))) {
            try {
                BigDecimal number = new BigDecimal(value);
                if (number.stripTrailingZeros().scale() <= 0) {
                    return number.toBigIntegerExact().toString();
                }
            } catch (ArithmeticException | NumberFormatException e) {
                return value;
            }
        }
        return value;
    }

    private Instant parseDate(String value) {
        if (value.matches("[0-9]+(\\.[0-9]+)?")) {
            BigDecimal serial = new BigDecimal(value);
            long days = serial.longValue();
            long seconds = serial.subtract(BigDecimal.valueOf(days)).multiply(BigDecimal.valueOf(SECONDS_PER_DAY))
                .setScale(0, RoundingMode.HALF_UP).longValue();
            return LocalDateTime.of(1970, 1, 1, 0, 0)
                .plusDays(EXCEL_EPOCH_DAY + days)
                .plusSeconds(seconds)
                .atZone(zone)
                .toInstant();
        }
        return LocalDateTime.parse(value, formatter).atZone(zone).toInstant();
    }

}
