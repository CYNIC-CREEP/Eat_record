package com.eatrecord.app;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import org.json.JSONObject;

/** Read-only form prompts. A missing diary is not a skipped or eaten meal. */
public final class ManualMealPrompts {
    private ManualMealPrompts() { }

    private static JSONObject meal(JSONObject days, LocalDate date, String key) {
        JSONObject day = days == null ? null : days.optJSONObject(date.toString());
        return day == null ? null : day.optJSONObject(key);
    }

    public static int skippedStreak(JSONObject days, String date, String key) {
        try {
            LocalDate cursor = LocalDate.parse(date);
            JSONObject current = meal(days, cursor, key);
            if (FoodRecordText.hasMeal(current)) return 0;
            if (current == null || !current.optBoolean("skipped")) cursor = cursor.minusDays(1);
            int count = 0;
            while (count < 30) {
                JSONObject value = meal(days, cursor, key);
                if (value == null || !value.optBoolean("skipped")) break;
                count++; cursor = cursor.minusDays(1);
            }
            return count;
        } catch (Exception ignored) { return 0; }
    }

    public static int snackRecordedStreak(JSONObject days, String date) {
        try {
            LocalDate cursor = LocalDate.parse(date);
            if (!FoodRecordText.hasMeal(meal(days, cursor, "snack"))) cursor = cursor.minusDays(1);
            int count = 0;
            while (count < 30 && FoodRecordText.hasMeal(meal(days, cursor, "snack"))) {
                count++; cursor = cursor.minusDays(1);
            }
            return count;
        } catch (Exception ignored) { return 0; }
    }

    public static String absenceGroup(int count) {
        return count >= 4 ? "many" : count == 3 ? "three" : count == 2 ? "two" : count == 1 ? "one" : "";
    }

    /** Legacy MEAL_HINTS order differs from the persisted meal order. */
    public static int mealHintIndex(String key) {
        if ("lunch".equals(key)) return 1;
        if ("dinner".equals(key)) return 2;
        if ("afternoon_tea".equals(key)) return 3;
        if ("snack".equals(key)) return 4;
        return 0;
    }

    /** Reopening the same group draws afresh, avoiding an immediate repeat when possible. */
    public static String choose(List<String> pool, String previous, Random random) {
        LinkedHashSet<String> distinct = new LinkedHashSet<>();
        if (pool != null) for (String value : pool) if (value != null && !value.trim().isEmpty()) distinct.add(value.trim());
        if (distinct.isEmpty()) return "今天吃了什么好味道？";
        if (distinct.size() > 1) distinct.remove(previous);
        List<String> choices = new ArrayList<>(distinct);
        return choices.get(random.nextInt(choices.size()));
    }
}
