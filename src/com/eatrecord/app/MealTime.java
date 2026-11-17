package com.eatrecord.app;

import java.util.Calendar;

/** New entries use the phone's local clock and keep the currently selected diary date. */
final class MealTime {
    private MealTime() { }

    static String currentMeal() {
        return forHour(Calendar.getInstance().get(Calendar.HOUR_OF_DAY));
    }

    static String forHour(int hour) {
        if (hour < 0 || hour > 23) throw new IllegalArgumentException("Hour must be between 0 and 23");
        if (hour < 5 || hour >= 23) return "snack";
        if (hour < 11) return "breakfast";
        if (hour < 16) return "lunch";
        return "dinner";
    }
}
