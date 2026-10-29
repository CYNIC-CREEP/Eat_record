package com.eatrecord.app;

public final class ReminderTimeInputTest {
    public static void main(String[] args) {
        valid("8:00", "08:00");
        valid("08:00", "08:00");
        valid("00:00", "00:00");
        valid("23:59", "23:59");
        valid(" 18:30 ", "18:30");
        valid("8：00", "08:00");
        for (String value : new String[]{null, "", "24:00", "23:60", "-1:00", "12:-1", "8:0", "0800", "008:00", "8:000", "eight:00", "8:00:00", "8 :00"}) {
            if (ReminderTimeInput.normalize(value) != null) throw new AssertionError("Unexpected valid time: " + value);
        }
        System.out.println("ReminderTimeInputTest: 19 checks passed");
    }

    private static void valid(String value, String expected) {
        if (!expected.equals(ReminderTimeInput.normalize(value))) throw new AssertionError("Wrong normalized time: " + value);
    }
}
