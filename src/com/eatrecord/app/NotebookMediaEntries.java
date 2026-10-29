package com.eatrecord.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Read-only media projection shared by the old notebook board and its exported image. */
public final class NotebookMediaEntries {
    private NotebookMediaEntries() { }
    private static final String[] MEALS = {"breakfast", "lunch", "afternoon_tea", "dinner", "snack"};

    public static final class Entry {
        public final String mealKey, fileName, fallbackFile;
        public final boolean sticker;
        public final int displayRotation;

        private Entry(String mealKey, String fileName, String fallbackFile, boolean sticker, int rotation) {
            this.mealKey = mealKey;
            this.fileName = fileName;
            this.fallbackFile = fallbackFile;
            this.sticker = sticker;
            this.displayRotation = normalizeRotation(rotation);
        }
    }

    /** Display-only quarter turns. Never rotates or changes the source photograph/alpha PNG. */
    public static int normalizeRotation(int rotation) {
        if (rotation % 90 != 0) return 0;
        return ((rotation % 360) + 360) % 360;
    }

    private static String safeFile(String file) {
        return file != null && !file.isEmpty() && !file.contains("..")
                && !file.contains("/") && !file.contains("\\") ? file : "";
    }

    /**
     * Food alpha files use the existing sticker board even when plain photos are hidden.
     * fallbackFile is metadata for a missing-file fallback, not a second board image.
     * De-duplication stays within each meal: separate meal records may use the same file.
     * This method does no I/O, does not migrate records and never changes the supplied JSON.
     */
    public static List<Entry> collect(JSONObject day, boolean includePhotos) {
        List<Entry> entries = new ArrayList<Entry>();
        if (day == null) return entries;
        for (String mealKey : MEALS) {
            JSONObject meal = day.optJSONObject(mealKey);
            if (meal == null || meal.optBoolean("skipped")) continue;
            Set<String> represented = new HashSet<String>();
            JSONArray foods = meal.optJSONArray("foodItems");
            for (int i = 0; foods != null && i < foods.length(); i++) {
                JSONObject food = foods.optJSONObject(i);
                if (food == null) continue;
                String source = safeFile(food.optString("sourcePhoto"));
                String cutout = safeFile(food.optString("cutoutFile"));
                boolean sticker = !cutout.isEmpty();
                String display = sticker ? cutout : (includePhotos ? source : "");
                if (display.isEmpty()) continue;
                if (represented.add(display)) {
                    entries.add(new Entry(mealKey, display, sticker ? source : "", sticker,
                            food.optInt("displayRotation", 0)));
                }
                // Keep the original addressable for edit/retry, but not as another visible image.
                if (!source.isEmpty()) represented.add(source);
            }
            addLegacy(entries, represented, mealKey, meal.optJSONArray("stickers"), true);
            if (includePhotos) addLegacy(entries, represented, mealKey, meal.optJSONArray("photos"), false);
        }
        return entries;
    }

    private static void addLegacy(List<Entry> entries, Set<String> represented, String mealKey,
                                  JSONArray media, boolean sticker) {
        for (int i = 0; media != null && i < media.length(); i++) {
            String file = safeFile(media.optString(i));
            if (!file.isEmpty() && represented.add(file)) {
                entries.add(new Entry(mealKey, file, "", sticker, 0));
            }
        }
    }
}
