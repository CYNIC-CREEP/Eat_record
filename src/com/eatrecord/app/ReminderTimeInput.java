package com.eatrecord.app;

import java.util.Locale;

/** Validation for manually entered 24-hour reminder times; never clamps invalid input. */
public final class ReminderTimeInput {
    private ReminderTimeInput() {}

    public static String normalize(String value) {
        String text = value == null ? "" : value.trim().replace('：', ':');
        if (!text.matches("[0-9]{1,2}:[0-9]{2}")) return null;
        String[] parts = text.split(":");
        try {
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);
            if (hour > 23 || minute > 59) return null;
            return String.format(Locale.ROOT, "%02d:%02d", hour, minute);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
