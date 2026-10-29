package com.eatrecord.app;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** Calendar-only projections. Browsing never creates or changes a diary record. */
public final class HomeCalendarDates {
    private HomeCalendarDates() { }

    public static Calendar day(Calendar value) {
        Calendar result = (Calendar) value.clone();
        // Noon is stable across local DST transitions; only the date is displayed or returned.
        result.set(Calendar.HOUR_OF_DAY, 12);
        result.set(Calendar.MINUTE, 0);
        result.set(Calendar.SECOND, 0);
        result.set(Calendar.MILLISECOND, 0);
        return result;
    }

    public static Calendar month(Calendar value) {
        Calendar result = day(value);
        result.set(Calendar.DAY_OF_MONTH, 1);
        return result;
    }

    public static Calendar monday(Calendar value) {
        Calendar result = day(value);
        result.add(Calendar.DATE, -((result.get(Calendar.DAY_OF_WEEK) + 5) % 7));
        return result;
    }

    public static String key(Calendar value) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        format.setTimeZone(value.getTimeZone());
        return format.format(value.getTime());
    }

    public static int rows(Calendar value) {
        Calendar first = month(value);
        int before = (first.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        return (before + first.getActualMaximum(Calendar.DAY_OF_MONTH) + 6) / 7;
    }

    public static List<Calendar> grid(Calendar value) {
        Calendar date = monday(month(value));
        List<Calendar> result = new ArrayList<>();
        int count = rows(value) * 7;
        for (int i = 0; i < count; i++) {
            result.add((Calendar) date.clone());
            date.add(Calendar.DATE, 1);
        }
        return result;
    }

    public static boolean sameMonth(Calendar left, Calendar right) {
        return left.get(Calendar.ERA) == right.get(Calendar.ERA)
                && left.get(Calendar.YEAR) == right.get(Calendar.YEAR)
                && left.get(Calendar.MONTH) == right.get(Calendar.MONTH);
    }
}
