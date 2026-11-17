package com.eatrecord.app;

public final class MealTimeTest {
    public static void main(String[] args) {
        for (int hour = 0; hour < 24; hour++) {
            String expected;
            if (hour <= 4 || hour == 23) expected = "snack";
            else if (hour <= 10) expected = "breakfast";
            else if (hour <= 15) expected = "lunch";
            else expected = "dinner";
            if (!expected.equals(MealTime.forHour(hour))) throw new AssertionError("Wrong meal for hour " + hour);
        }
        reject(-1);
        reject(24);
        if (MealTime.currentMeal().isEmpty()) throw new AssertionError("Current local meal missing");
        System.out.println("PASS 27 local meal-time checks");
    }

    private static void reject(int hour) {
        try {
            MealTime.forHour(hour);
            throw new AssertionError("Invalid hour accepted");
        } catch (IllegalArgumentException expected) { }
    }
}
