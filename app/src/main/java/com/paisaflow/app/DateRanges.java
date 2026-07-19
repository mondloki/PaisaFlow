package com.paisaflow.app;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

final class DateRanges {
    enum Preset { WEEK, MONTH, YEAR, FINANCIAL_YEAR, ALL, CUSTOM }

    private DateRanges() {}

    static int todayKey() { return key(Calendar.getInstance()); }

    static LedgerModels.DateWindow forPreset(Preset preset) {
        Calendar now = atMidnight(Calendar.getInstance());
        Calendar start = (Calendar) now.clone();
        switch (preset) {
            case WEEK:
                int day = start.get(Calendar.DAY_OF_WEEK);
                int daysFromMonday = (day + 5) % 7;
                start.add(Calendar.DAY_OF_MONTH, -daysFromMonday);
                break;
            case MONTH:
                start.set(Calendar.DAY_OF_MONTH, 1);
                break;
            case YEAR:
                start.set(Calendar.MONTH, Calendar.JANUARY);
                start.set(Calendar.DAY_OF_MONTH, 1);
                break;
            case FINANCIAL_YEAR:
                if (start.get(Calendar.MONTH) < Calendar.APRIL) {
                    start.add(Calendar.YEAR, -1);
                }
                start.set(Calendar.MONTH, Calendar.APRIL);
                start.set(Calendar.DAY_OF_MONTH, 1);
                break;
            case ALL:
                return new LedgerModels.DateWindow(0, key(now), "Start till now");
            default:
                break;
        }
        return custom(key(start), key(now));
    }

    static LedgerModels.DateWindow custom(int start, int end) {
        int low = Math.min(start, end);
        int high = Math.max(start, end);
        return new LedgerModels.DateWindow(low, high, format(low) + " – " + format(high));
    }

    static int key(Calendar calendar) {
        return calendar.get(Calendar.YEAR) * 10000
                + (calendar.get(Calendar.MONTH) + 1) * 100
                + calendar.get(Calendar.DAY_OF_MONTH);
    }

    static Calendar calendar(int key) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(key / 10000, ((key / 100) % 100) - 1, key % 100);
        return calendar;
    }

    static String format(int key) {
        if (key <= 0) return "Beginning";
        Date date = calendar(key).getTime();
        return new SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(date);
    }

    static String shortFormat(int key) {
        Date date = calendar(key).getTime();
        return new SimpleDateFormat("d MMM", Locale.getDefault()).format(date);
    }

    private static Calendar atMidnight(Calendar value) {
        value.set(Calendar.HOUR_OF_DAY, 0);
        value.set(Calendar.MINUTE, 0);
        value.set(Calendar.SECOND, 0);
        value.set(Calendar.MILLISECOND, 0);
        return value;
    }
}

