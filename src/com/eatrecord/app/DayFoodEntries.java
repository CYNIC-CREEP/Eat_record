package com.eatrecord.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Read-only projection of one old/new diary day onto a continuous food journal. */
public final class DayFoodEntries {
    private DayFoodEntries() { }
    private static final String[] MEALS = {"breakfast", "lunch", "afternoon_tea", "dinner", "snack"};
    private static final String[] PREFIXES = {"早", "午", "茶", "晚", "夜"};
    private static final String[] TITLES = {"早餐", "午餐", "下午茶", "晚餐", "夜宵"};

    public static final class Entry {
        public final String mealKey, prefix, mealTitle, title, fileName, fallbackFile;
        public final JSONObject item;
        public final boolean sticker;
        public final double calories;
        public final String textValue;
        private Entry(String mealKey, String title, String file, String fallback, JSONObject item,
                      boolean sticker, double calories) {
            this(mealKey,title,file,fallback,item,sticker,calories,null);
        }
        private Entry(String mealKey, String title, String file, String fallback, JSONObject item,
                      boolean sticker, double calories,String textValue) {
            this.mealKey = mealKey;
            this.prefix = prefix(mealKey);
            this.mealTitle = mealTitle(mealKey);
            this.title = title;
            this.fileName = file;
            this.fallbackFile = fallback;
            this.item = item;
            this.sticker = sticker;
            this.calories = calories;
            this.textValue = textValue;
        }
        public boolean isFood() { return item != null; }
        public boolean isText() { return textValue != null; }
    }

    public static String prefix(String mealKey) {
        for (int i = 0; i < MEALS.length; i++) if (MEALS[i].equals(mealKey)) return PREFIXES[i];
        return "餐";
    }
    public static String mealTitle(String mealKey) {
        for (int i = 0; i < MEALS.length; i++) if (MEALS[i].equals(mealKey)) return TITLES[i];
        return "用餐";
    }
    private static String safeFile(String file) {
        return file != null && !file.isEmpty() && !file.contains("..")
                && !file.contains("/") && !file.contains("\\") ? file : "";
    }
    private static String clean(String text) { return text == null ? "" : text.trim().replaceAll("\\s+", " "); }

    /** Does not migrate, reorder, remove or change any JSON fields. Drafts are never passed here. */
    public static List<Entry> collect(JSONObject day) {
        List<Entry> entries = new ArrayList<Entry>();
        if (day == null) return entries;
        for (String key : MEALS) {
            JSONObject meal = day.optJSONObject(key);
            if (meal == null) continue;
            if (meal.optBoolean("skipped")) {
                entries.add(new Entry(key, "已标记未用餐", "", "", null, false, Double.NaN));
                continue;
            }
            Set<String> represented = new HashSet<String>();
            JSONArray items = meal.optJSONArray("foodItems");
            for (int i = 0; items != null && i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null) continue;
                String source = safeFile(item.optString("sourcePhoto"));
                String cutout = safeFile(item.optString("cutoutFile"));
                if (!source.isEmpty()) represented.add(source);
                if (!cutout.isEmpty()) represented.add(cutout);
                String title = clean(item.optString("dishName"));
                JSONObject nutrients = item.optJSONObject("nutrients");
                double calories = "completed".equals(item.optString("status")) && nutrients != null
                        ? nutrients.optDouble("caloriesKcal", Double.NaN) : Double.NaN;
                if (!Double.isFinite(calories) || calories < 0) calories = Double.NaN;
                entries.add(new Entry(key, title.isEmpty() ? "这份美味" : title,
                        cutout.isEmpty() ? source : cutout, source, item, !cutout.isEmpty(), calories));
            }
            String note = clean(meal.optString("text"));
            boolean manualText = meal.optBoolean("manualTextEntry");
            boolean usedNote = false;
            for (String arrayKey : new String[]{"photos", "stickers"}) {
                JSONArray media = meal.optJSONArray(arrayKey);
                for (int i = 0; media != null && i < media.length(); i++) {
                    String file = safeFile(media.optString(i));
                    if (file.isEmpty() || !represented.add(file)) continue;
                    String title = !manualText && !usedNote && !note.isEmpty() ? note : "餐食" + ("stickers".equals(arrayKey) ? "贴纸" : "照片");
                    usedNote = usedNote || !manualText && !note.isEmpty();
                    entries.add(new Entry(key, title, file, "", null, "stickers".equals(arrayKey), Double.NaN));
                }
            }
            if (!usedNote && !note.isEmpty() && (manualText || items == null || items.length() == 0)) {
                entries.add(new Entry(key, note, "", "", null, false, Double.NaN,meal.optString("text")));
            } else if ((items == null || items.length() == 0) && !usedNote && represented.isEmpty()) {
                String emoji = clean(meal.optString("emoji"));
                double manual = meal.optDouble("manualCalories", 0);
                if (!emoji.isEmpty() || manual > 0) {
                    entries.add(new Entry(key, emoji.isEmpty() ? "手动记录" : "用餐心情 " + emoji,
                            "", "", null, false, manual > 0 ? manual : Double.NaN));
                }
            }
        }
        return entries;
    }
}
