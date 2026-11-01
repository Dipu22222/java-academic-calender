package bd.ac.mbstu.calendar.util;

import java.util.ArrayList;
import java.util.List;

/**
 * A small CSV reader and writer: commas, "quoted, cells", doubled quotes ("") and
 * line breaks inside quotes all work. Empty lines are skipped.
 */
public final class CsvUtil {

    private CsvUtil() {
    }

    /** Splits CSV text into rows of cells. */
    public static List<List<String>> parse(String text) {
        List<List<String>> rows = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return rows;
        }
        if (text.charAt(0) == '\uFEFF') {          // Excel adds this invisible mark at the start
            text = text.substring(1);
        }

        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        cell.append('"');           // "" inside quotes means one quote mark
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cell.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                row.add(cell.toString());
                cell.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;                            // Windows line break (\r\n)
                }
                row.add(cell.toString());
                cell.setLength(0);
                addIfNotBlank(rows, row);
                row = new ArrayList<>();
            } else {
                cell.append(c);
            }
        }
        if (inQuotes) {
            throw new ValidationException("The CSV file has a quotation mark that is never closed.");
        }
        if (cell.length() > 0 || !row.isEmpty()) {
            row.add(cell.toString());
            addIfNotBlank(rows, row);
        }
        return rows;
    }

    /** Turns cells into one CSV line (no line break at the end). */
    public static String line(List<String> cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escape(cells.get(i)));
        }
        return sb.toString();
    }

    /**
     * Spreadsheet programs run a cell as a formula if it starts with = + - or @
     * ("CSV injection"). On export we put a ' in front so the cell stays plain text.
     */
    public static String protect(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        char first = value.charAt(0);
        boolean risky = first == '=' || first == '+' || first == '-' || first == '@'
                || first == '\t' || first == '\r';
        return risky ? "'" + value : value;
    }

    /** The reverse of protect(), used on import so a round trip gives the same text. */
    public static String unprotect(String value) {
        if (value != null && value.length() > 1 && value.charAt(0) == '\'') {
            char second = value.charAt(1);
            if (second == '=' || second == '+' || second == '-' || second == '@') {
                return value.substring(1);
            }
        }
        return value;
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuotes = value.contains(",") || value.contains("\"")
                || value.contains("\n") || value.contains("\r");
        String doubled = value.replace("\"", "\"\"");
        return needsQuotes ? "\"" + doubled + "\"" : doubled;
    }

    private static void addIfNotBlank(List<List<String>> rows, List<String> row) {
        for (String cell : row) {
            if (!cell.isBlank()) {
                rows.add(row);
                return;
            }
        }
    }
}
