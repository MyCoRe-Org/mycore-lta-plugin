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
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Minimal reader of the first worksheet of an XLSX file. Supports shared strings, inline strings, numbers and
 * booleans. Formulas are read by their cached value. Dates are returned as Excel serial numbers.
 */
final class MCRLTAXlsxReader {

    private static final String SHARED_STRINGS = "xl/sharedStrings.xml";

    private static final String FIRST_SHEET = "xl/worksheets/sheet1.xml";

    private static final long MAX_ENTRY_SIZE = 256L * 1024 * 1024;

    private MCRLTAXlsxReader() {
    }

    /**
     * Reads the first worksheet.
     *
     * @param file the XLSX file
     * @return the rows with their cells, missing rows and cells are returned as empty values
     * @throws IOException if the file can not be read
     */
    static List<List<String>> read(Path file) throws IOException {
        try (ZipFile zip = new ZipFile(file.toFile())) {
            List<String> sharedStrings = List.of();
            ZipEntry sharedStringsEntry = zip.getEntry(SHARED_STRINGS);
            if (sharedStringsEntry != null) {
                try (InputStream in = open(zip, sharedStringsEntry)) {
                    sharedStrings = readSharedStrings(in);
                }
            }
            ZipEntry sheetEntry = zip.getEntry(FIRST_SHEET);
            if (sheetEntry == null) {
                throw new IOException("No worksheet found in " + file);
            }
            try (InputStream in = open(zip, sheetEntry)) {
                return readSheet(in, sharedStrings);
            }
        } catch (XMLStreamException e) {
            throw new IOException("Invalid XLSX file " + file, e);
        }
    }

    private static InputStream open(ZipFile zip, ZipEntry entry) throws IOException {
        if (entry.getSize() > MAX_ENTRY_SIZE) {
            throw new IOException("Entry " + entry.getName() + " is too large");
        }
        return zip.getInputStream(entry);
    }

    private static XMLStreamReader createReader(InputStream in) throws XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        return factory.createXMLStreamReader(in);
    }

    /**
     * Reads the shared strings table.
     *
     * @param in the content of <code>xl/sharedStrings.xml</code>
     * @return the shared strings
     * @throws XMLStreamException if the XML is invalid
     */
    static List<String> readSharedStrings(InputStream in) throws XMLStreamException {
        List<String> strings = new ArrayList<>();
        XMLStreamReader reader = createReader(in);
        try {
            StringBuilder current = null;
            boolean inText = false;
            boolean inPhonetic = false;
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    switch (reader.getLocalName()) {
                        case "si" -> current = new StringBuilder();
                        case "rPh" -> inPhonetic = true;
                        case "t" -> inText = !inPhonetic;
                        default -> {
                        }
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    switch (reader.getLocalName()) {
                        case "si" -> strings.add(current == null ? "" : current.toString());
                        case "rPh" -> inPhonetic = false;
                        case "t" -> inText = false;
                        default -> {
                        }
                    }
                } else if (inText && current != null && isText(event)) {
                    current.append(reader.getText());
                }
            }
        } finally {
            reader.close();
        }
        return strings;
    }

    /**
     * Reads a worksheet.
     *
     * @param in the content of the worksheet
     * @param sharedStrings the shared strings
     * @return the rows with their cells
     * @throws XMLStreamException if the XML is invalid
     */
    static List<List<String>> readSheet(InputStream in, List<String> sharedStrings) throws XMLStreamException {
        List<List<String>> rows = new ArrayList<>();
        XMLStreamReader reader = createReader(in);
        try {
            List<String> row = null;
            String cellType = null;
            int cellIndex = -1;
            StringBuilder value = null;
            boolean inValue = false;
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    switch (reader.getLocalName()) {
                        case "row" -> {
                            int rowNumber = parseRowNumber(reader.getAttributeValue(null, "r"), rows.size() + 1);
                            while (rows.size() < rowNumber - 1) {
                                rows.add(new ArrayList<>());
                            }
                            row = new ArrayList<>();
                        }
                        case "c" -> {
                            cellType = reader.getAttributeValue(null, "t");
                            String ref = reader.getAttributeValue(null, "r");
                            cellIndex = ref == null ? (row == null ? 0 : row.size()) : columnIndex(ref);
                            value = new StringBuilder();
                        }
                        case "v", "t" -> inValue = value != null;
                        default -> {
                        }
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    switch (reader.getLocalName()) {
                        case "row" -> {
                            rows.add(row == null ? new ArrayList<>() : row);
                            row = null;
                        }
                        case "c" -> {
                            if (row != null && value != null) {
                                while (row.size() < cellIndex) {
                                    row.add("");
                                }
                                row.add(resolve(cellType, value.toString(), sharedStrings));
                            }
                            value = null;
                        }
                        case "v", "t" -> inValue = false;
                        default -> {
                        }
                    }
                } else if (inValue && isText(event)) {
                    value.append(reader.getText());
                }
            }
        } finally {
            reader.close();
        }
        return rows;
    }

    private static boolean isText(int event) {
        return event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA
            || event == XMLStreamConstants.SPACE;
    }

    private static int parseRowNumber(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * Returns the zero based column index of a cell reference like <code>AB12</code>.
     *
     * @param ref the cell reference
     * @return the column index
     */
    static int columnIndex(String ref) {
        int index = 0;
        for (char c : ref.toUpperCase(Locale.ROOT).toCharArray()) {
            if (c < 'A' || c > 'Z') {
                break;
            }
            index = index * 26 + (c - 'A' + 1);
        }
        return index - 1;
    }

    private static String resolve(String type, String value, List<String> sharedStrings) {
        if (type == null || "n".equals(type)) {
            return normalizeNumber(value);
        }
        return switch (type) {
            case "s" -> {
                int index = Integer.parseInt(value.trim());
                yield index < sharedStrings.size() ? sharedStrings.get(index) : "";
            }
            case "b" -> "1".equals(value.trim()) ? "TRUE" : "FALSE";
            default -> value;
        };
    }

    private static String normalizeNumber(String value) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        try {
            return new BigDecimal(trimmed).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException e) {
            return trimmed;
        }
    }

}
