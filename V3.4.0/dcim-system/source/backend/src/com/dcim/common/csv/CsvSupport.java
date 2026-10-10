package com.dcim.common.csv;

import java.util.ArrayList;
import java.util.List;

public final class CsvSupport {
    private CsvSupport() {
    }

    public static List<List<String>> parse(String text) {
        List<List<String>> rows = new ArrayList<>();
        if (text == null) {
            return rows;
        } else {
            if (!text.isEmpty() && text.charAt(0) == '\ufeff') {
                text = text.substring(1);
            }

            List<String> cur = new ArrayList<>();
            StringBuilder cell = new StringBuilder();
            boolean inQuotes = false;
            boolean cellStarted = false;
            int i = 0;
            int n = text.length();

            while (i < n) {
                char c = text.charAt(i);
                if (inQuotes) {
                    if (c == '"') {
                        if (i + 1 < n && text.charAt(i + 1) == '"') {
                            cell.append('"');
                            i += 2;
                        } else {
                            inQuotes = false;
                            i++;
                        }
                    } else {
                        cell.append(c);
                        i++;
                    }
                } else {
                    switch (c) {
                        case '\n':
                            cur.add(cell.toString());
                            cell.setLength(0);
                            rows.add(cur);
                            cur = new ArrayList<>();
                            cellStarted = false;
                            i++;
                            break;
                        case '\r':
                            if (i + 1 < n && text.charAt(i + 1) == '\n') {
                                i++;
                            }

                            cur.add(cell.toString());
                            cell.setLength(0);
                            rows.add(cur);
                            cur = new ArrayList<>();
                            cellStarted = false;
                            i++;
                            break;
                        case '"':
                            inQuotes = true;
                            cellStarted = true;
                            i++;
                            break;
                        case ',':
                            cur.add(cell.toString());
                            cell.setLength(0);
                            cellStarted = true;
                            i++;
                            break;
                        default:
                            cell.append(c);
                            cellStarted = true;
                            i++;
                    }
                }
            }

            if (cellStarted || cell.length() > 0 || !cur.isEmpty()) {
                cur.add(cell.toString());
                rows.add(cur);
            }

            return rows;
        }
    }

    public static String cell(Object value) {
        String s = value == null ? "" : String.valueOf(value);
        if (!s.isEmpty() && (s.charAt(0) == '=' || s.charAt(0) == '+' || s.charAt(0) == '-' || s.charAt(0) == '@')) {
            s = "'" + s;
        }

        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }

        return s;
    }

    public static String line(List<?> cells) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }

            sb.append(cell(cells.get(i)));
        }

        return sb.toString();
    }
}
