package com.eatrecord.app;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;

/** Plain JVM checks; run with HomeCalendarDates.java, no Android classes required. */
public final class HomeCalendarDatesTest {
    private static int checks;
    private static Calendar date(int year, int month, int day) {
        Calendar value = new GregorianCalendar(TimeZone.getTimeZone("Asia/Shanghai"));
        value.clear(); value.set(year, month - 1, day, 0, 0, 0); return value;
    }
    private static void check(boolean value, String message) {
        checks++; if (!value) throw new AssertionError(message);
    }
    public static void main(String[] arguments) {
        check(HomeCalendarDates.rows(date(2021, 2, 1)) == 4, "Monday February fits four rows");
        check(HomeCalendarDates.rows(date(2026, 10, 19)) == 5, "October has five rows");
        check(HomeCalendarDates.rows(date(2026, 8, 19)) == 6, "August has six rows");
        List<Calendar> october = HomeCalendarDates.grid(date(2026, 10, 19));
        check(october.size() == 35, "full five-week grid");
        check("2026-09-28".equals(HomeCalendarDates.key(october.get(0))), "adjacent previous month");
        check("2026-11-01".equals(HomeCalendarDates.key(october.get(34))), "adjacent next month");
        check(!HomeCalendarDates.sameMonth(october.get(0), date(2026, 10, 19)), "adjacent date dim predicate");
        List<Calendar> leap = HomeCalendarDates.grid(date(2024, 2, 29));
        boolean foundLeap = false;
        for (Calendar value : leap) if ("2024-02-29".equals(HomeCalendarDates.key(value))) foundLeap = true;
        check(foundLeap, "leap day preserved");
        check("2025-12-29".equals(HomeCalendarDates.key(HomeCalendarDates.monday(date(2026, 1, 1)))), "cross-year Monday");
        Calendar original = date(2026, 10, 19);
        long timestamp = original.getTimeInMillis();
        List<Calendar> projected = HomeCalendarDates.grid(original);
        projected.get(0).add(Calendar.DATE, 20);
        check(original.getTimeInMillis() == timestamp, "projection leaves caller calendar untouched");
        check("2026-09-29".equals(HomeCalendarDates.key(projected.get(1))), "grid entries are independent clones");
        Calendar dst = new GregorianCalendar(TimeZone.getTimeZone("America/Los_Angeles"));
        dst.clear(); dst.set(2026, Calendar.MARCH, 8, 0, 0, 0);
        check("2026-03-08".equals(HomeCalendarDates.key(HomeCalendarDates.day(dst))), "DST date remains stable");
        System.out.println("HomeCalendarDatesTest: " + checks + " passed");
    }
}
