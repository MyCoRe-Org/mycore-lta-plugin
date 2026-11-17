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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class MCRLTATableReportImporterTest {

    @TempDir
    Path tempDir;

    private MCRLTATableReportImporter importer;

    @BeforeEach
    public void setUp() {
        importer = new MCRLTATableReportImporter();
        importer.entityIdColumn = "IE PID";
        importer.submissionIdColumn = "SIP ID";
        importer.creationDateColumn = "IE Creation Date";
        importer.objectIdColumn = "Identifier 1";
        importer.dateFormat = "yyyy-MM-dd HH:mm:ss";
        importer.dateZone = "UTC";
        importer.separator = ",";
        importer.charset = "UTF-8";
        importer.init();
    }

    @Test
    public void testCsv() throws IOException {
        Path file = tempDir.resolve("report.csv");
        Files.writeString(file, "﻿IE PID,SIP ID,Other,IE Creation Date,Identifier 1\n"
            + "IE1,100,x,2026-01-02 03:04:05,mir_mods_00000001\n"
            + ",,,,\n"
            + "IE2,,x,2026-01-02 03:04:05,mir_mods_00000002\n"
            + "IE3,1.01E2,x,,\n"
            + "IE4,102,x,not a date,mir_mods_00000004\n", StandardCharsets.UTF_8);
        MCRLTAReport report = importer.read(file);
        assertEquals("report.csv", report.fileName());
        assertEquals(2, report.rows().size());
        MCRLTAReportRow first = report.rows().getFirst();
        assertEquals(2, first.rowNumber());
        assertEquals("IE1", first.entityId());
        assertEquals("100", first.submissionId());
        assertEquals(Instant.parse("2026-01-02T03:04:05Z"), first.created());
        assertEquals("mir_mods_00000001", first.objectId());
        MCRLTAReportRow second = report.rows().get(1);
        assertEquals("101", second.submissionId());
        assertNull(second.created());
        assertNull(second.objectId());
        assertEquals(2, report.problems().size());
    }

    @Test
    public void testMissingColumn() {
        assertThrows(IOException.class, () -> importer.convert("x.csv", List.of(List.of("IE PID", "Other"))));
    }

    @Test
    public void testXlsx() throws IOException {
        Path file = tempDir.resolve("report.xlsx");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            write(zip, "xl/sharedStrings.xml", "<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/"
                + "main\"><si><t>IE PID</t></si><si><t>SIP ID</t></si><si><t>IE Creation Date</t></si>"
                + "<si><t>Identifier 1</t></si><si><r><t>IE</t></r><r><t>1</t></r></si>"
                + "<si><t>mir_mods_00000001</t></si></sst>");
            write(zip, "xl/worksheets/sheet1.xml", "<worksheet xmlns=\"http://schemas.openxmlformats.org/"
                + "spreadsheetml/2006/main\"><sheetData>"
                + "<row r=\"1\"><c r=\"A1\" t=\"s\"><v>0</v></c><c r=\"B1\" t=\"s\"><v>1</v></c>"
                + "<c r=\"C1\" t=\"s\"><v>2</v></c><c r=\"D1\" t=\"s\"><v>3</v></c></row>"
                + "<row r=\"3\"><c r=\"A3\" t=\"s\"><v>4</v></c><c r=\"B3\"><v>12345</v></c>"
                + "<c r=\"C3\"><v>46024.5</v></c><c r=\"D3\" t=\"s\"><v>5</v></c></row>"
                + "<row r=\"4\"><c r=\"A4\" t=\"inlineStr\"><is><t>IE2</t></is></c><c r=\"B4\"><v>2.0</v></c>"
                + "</row></sheetData></worksheet>");
        }
        MCRLTAReport report = importer.read(file);
        assertEquals(2, report.rows().size(), report.problems().toString());
        MCRLTAReportRow first = report.rows().getFirst();
        assertEquals(3, first.rowNumber());
        assertEquals("IE1", first.entityId());
        assertEquals("12345", first.submissionId());
        assertEquals(Instant.parse("2026-01-02T12:00:00Z"), first.created());
        assertEquals("mir_mods_00000001", first.objectId());
        assertEquals("IE2", report.rows().get(1).entityId());
        assertEquals("2", report.rows().get(1).submissionId());
    }

    @Test
    public void testColumnIndex() {
        assertEquals(0, MCRLTAXlsxReader.columnIndex("A1"));
        assertEquals(25, MCRLTAXlsxReader.columnIndex("Z9"));
        assertEquals(27, MCRLTAXlsxReader.columnIndex("AB12"));
    }

    private static void write(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        OutputStream out = zip;
        out.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

}
