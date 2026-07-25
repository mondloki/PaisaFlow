package com.paisaflow.app;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

final class TransactionCsv {
    private static final int MAX_ROWS = 1_000_000;

    private TransactionCsv() {}

    static String create(List<LedgerModels.Entry> entries) {
        StringBuilder result = new StringBuilder(Math.max(128, entries.size() * 64));
        result.append("Date,Category,Type,Amount INR,Note\n");
        for (LedgerModels.Entry entry : entries) {
            result.append(csv(isoDate(entry.dateKey))).append(',');
            result.append(csv(entry.category.name)).append(',');
            result.append(csv(displayFlow(entry.category.flow))).append(',');
            result.append(BigDecimal.valueOf(entry.amountMinor, 2).setScale(2, RoundingMode.UNNECESSARY));
            result.append(',').append(csv(entry.note)).append('\n');
        }
        return result.toString();
    }

    static Parsed parse(String csv) {
        if (csv == null || csv.isEmpty()) throw new IllegalArgumentException("CSV file is empty");
        List<List<String>> records = records(csv);
        if (records.isEmpty() || records.get(0).size() != 5
                || !"Date".equalsIgnoreCase(records.get(0).get(0).trim())
                || !"Category".equalsIgnoreCase(records.get(0).get(1).trim())
                || !"Type".equalsIgnoreCase(records.get(0).get(2).trim())
                || !"Amount INR".equalsIgnoreCase(records.get(0).get(3).trim())
                || !"Note".equalsIgnoreCase(records.get(0).get(4).trim())) {
            throw new IllegalArgumentException("CSV header must be Date, Category, Type, Amount INR, Note");
        }
        ArrayList<Row> rows = new ArrayList<>();
        for (int i = 1; i < records.size(); i++) {
            List<String> record = records.get(i);
            if (record.size() == 1 && record.get(0).trim().isEmpty()) continue;
            if (record.size() != 5) throw new IllegalArgumentException("CSV row " + (i + 1) + " has the wrong number of columns");
            String category = record.get(1).trim();
            String note = record.get(4).trim();
            String flow = parseFlow(record.get(2));
            if (category.isEmpty() || category.length() > 80 || note.length() > 2000) {
                throw new IllegalArgumentException("CSV row " + (i + 1) + " contains invalid text");
            }
            rows.add(new Row(parseDate(record.get(0), i + 1), category, flow,
                    parseAmount(record.get(3), i + 1), note));
            if (rows.size() > MAX_ROWS) throw new IllegalArgumentException("CSV contains too many transactions");
        }
        if (rows.isEmpty()) throw new IllegalArgumentException("CSV contains no transactions");
        return new Parsed(rows);
    }

    private static List<List<String>> records(String csv) {
        ArrayList<List<String>> records = new ArrayList<>();
        ArrayList<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < csv.length(); i++) {
            char value = csv.charAt(i);
            if (quoted) {
                if (value == '"') {
                    if (i + 1 < csv.length() && csv.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    field.append(value);
                }
            } else if (value == '"' && field.length() == 0) {
                quoted = true;
            } else if (value == ',') {
                row.add(field.toString());
                field.setLength(0);
            } else if (value == '\n' || value == '\r') {
                if (value == '\r' && i + 1 < csv.length() && csv.charAt(i + 1) == '\n') i++;
                row.add(field.toString());
                field.setLength(0);
                records.add(row);
                row = new ArrayList<>();
            } else {
                field.append(value);
            }
        }
        if (quoted) throw new IllegalArgumentException("CSV contains an unfinished quoted field");
        if (field.length() > 0 || !row.isEmpty()) {
            row.add(field.toString());
            records.add(row);
        }
        return records;
    }

    private static int parseDate(String value, int row) {
        String trimmed = value.trim();
        if (trimmed.matches("\\d{4}-\\d{2}-\\d{2}")) {
            int key = Integer.parseInt(trimmed.substring(0, 4) + trimmed.substring(5, 7) + trimmed.substring(8, 10));
            if (validDate(key)) return key;
        }
        if (trimmed.matches("\\d{2}-\\d{2}-\\d{4}")) {
            int key = Integer.parseInt(trimmed.substring(6, 10)
                    + trimmed.substring(3, 5) + trimmed.substring(0, 2));
            if (validDate(key)) return key;
        }
        String[] patterns = {"d MMM yyyy", "dd MMM yyyy"};
        Locale[] locales = {Locale.getDefault(), Locale.ENGLISH};
        for (Locale locale : locales) {
            for (String pattern : patterns) {
                SimpleDateFormat format = new SimpleDateFormat(pattern, locale);
                format.setLenient(false);
                ParsePosition position = new ParsePosition(0);
                Date parsed = format.parse(trimmed, position);
                if (parsed != null && position.getIndex() == trimmed.length()) {
                    java.util.Calendar calendar = java.util.Calendar.getInstance();
                    calendar.setTime(parsed);
                    return DateRanges.key(calendar);
                }
            }
        }
        throw new IllegalArgumentException("CSV row " + row + " has an invalid date");
    }

    private static boolean validDate(int key) {
        java.util.Calendar calendar = DateRanges.calendar(key);
        calendar.setLenient(false);
        try {
            calendar.getTime();
            return DateRanges.key(calendar) == key;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static long parseAmount(String value, int row) {
        try {
            BigDecimal amount = new BigDecimal(value.trim()).setScale(2, RoundingMode.UNNECESSARY);
            long minor = amount.movePointRight(2).longValueExact();
            if (minor <= 0) throw new ArithmeticException();
            return minor;
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("CSV row " + row + " has an invalid amount");
        }
    }

    private static String parseFlow(String value) {
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if ("EXPENSE".equals(normalized)) return LedgerModels.EXPENSE;
        if ("INVESTMENT".equals(normalized)) return LedgerModels.INVESTMENT;
        if ("CREDIT".equals(normalized)) return LedgerModels.CREDIT;
        throw new IllegalArgumentException("CSV contains an invalid transaction type");
    }

    private static String displayFlow(String flow) {
        if (LedgerModels.INVESTMENT.equals(flow)) return "Investment";
        if (LedgerModels.CREDIT.equals(flow)) return "Credit";
        return "Expense";
    }

    private static String isoDate(int key) {
        return String.format(Locale.US, "%04d-%02d-%02d", key / 10000, (key / 100) % 100, key % 100);
    }

    private static String csv(String value) {
        return "\"" + (value == null ? "" : value.replace("\"", "\"\"")) + "\"";
    }

    static final class Parsed {
        final List<Row> rows;
        Parsed(List<Row> rows) { this.rows = rows; }
    }

    static final class Row {
        final int dateKey;
        final String category;
        final String flow;
        final long amountMinor;
        final String note;

        Row(int dateKey, String category, String flow, long amountMinor, String note) {
            this.dateKey = dateKey;
            this.category = category;
            this.flow = flow;
            this.amountMinor = amountMinor;
            this.note = note;
        }
    }
}
