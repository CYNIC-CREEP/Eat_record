package com.eatrecord.app;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.LinkedHashSet;
import java.util.Set;

/** A food changes its meal without changing inference identity or meal-wide settings. */
final class FoodMealAssignment {
    private FoodMealAssignment() { }

    static String nextMeal(String current) {
        if ("lunch".equals(current)) return "breakfast";
        if ("breakfast".equals(current)) return "snack";
        if ("snack".equals(current)) return "dinner";
        return "lunch";
    }

    private static boolean knownMeal(String key) {
        for (String meal : NutritionStore.MEALS) if (meal.equals(key)) return true;
        return false;
    }

    /** Mutates fresh transaction copies only. Validate identity before creating a destination. */
    static boolean move(JSONObject days, JSONArray queue, String date, String from, String to, String id)
            throws JSONException {
        if (days == null || queue == null || date == null || id == null || id.isEmpty()
                || !knownMeal(from) || !knownMeal(to)) return false;
        JSONObject day = days.optJSONObject(date), source = NutritionStore.meal(days, date, from);
        JSONArray items = source == null ? null : source.optJSONArray("foodItems");
        JSONObject selected = null;
        JSONArray remaining = new JSONArray();
        int matches = 0;
        for (int i = 0; items != null && i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item != null && id.equals(item.optString("id"))) {
                selected = item;
                matches++;
            } else remaining.put(items.opt(i));
        }
        if (matches != 1) return false;
        if (from.equals(to)) return true;
        for (String key : NutritionStore.MEALS) {
            if (!from.equals(key) && NutritionStore.find(day.optJSONObject(key), id) != null) return false;
        }
        JSONObject destination = day.optJSONObject(to);
        if (destination == null) destination = new JSONObject();
        JSONArray movedItems = destination.optJSONArray("foodItems");
        if (movedItems == null) movedItems = new JSONArray();
        movedItems.put(selected);
        destination.put("foodItems", movedItems);
        destination.remove("skipped");
        source.put("foodItems", remaining);

        Set<String> linkedFiles = new LinkedHashSet<>();
        for (String field : new String[]{"sourcePhoto", "cutoutFile"}) {
            String file = selected.optString(field);
            if (NutritionStore.safeName(file)) linkedFiles.add(file);
        }
        for (String arrayKey : new String[]{"photos", "stickers"}) {
            JSONArray previous = source.optJSONArray(arrayKey), kept = new JSONArray();
            JSONArray accepted = destination.optJSONArray(arrayKey);
            if (accepted == null) accepted = new JSONArray();
            Set<String> removed = new LinkedHashSet<>();
            for (int i = 0; previous != null && i < previous.length(); i++) {
                String file = previous.optString(i);
                if (linkedFiles.contains(file)) {
                    if (!NutritionStore.contains(accepted, file)) accepted.put(file);
                    if (!referenced(remaining, file)) removed.add(file);
                }
                if (!removed.contains(file)) kept.put(previous.opt(i));
            }
            // Confirmed photos normally have this reference. Keep older item-only records usable too.
            String original = selected.optString("sourcePhoto");
            if ("photos".equals(arrayKey) && NutritionStore.safeName(original)
                    && !NutritionStore.contains(accepted, original)) accepted.put(original);
            if (previous != null) source.put(arrayKey, kept);
            if (accepted.length() > 0) destination.put(arrayKey, accepted);
            removeStackLinks(source, "photos".equals(arrayKey) ? "photoStacks" : "stickerStacks", removed);
        }
        day.put(to, destination);
        for (int i = 0; i < queue.length(); i++) {
            JSONObject job = queue.optJSONObject(i);
            if (job != null && !job.optBoolean("draft") && date.equals(job.optString("date"))
                    && from.equals(job.optString("meal")) && id.equals(job.optString("id"))) {
                job.put("meal", to);
            }
        }
        return true;
    }

    private static boolean referenced(JSONArray items, String file) {
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item != null && (file.equals(item.optString("sourcePhoto"))
                    || file.equals(item.optString("cutoutFile")))) return true;
        }
        return false;
    }

    private static void removeStackLinks(JSONObject meal, String key, Set<String> removed) {
        JSONObject stacks = meal.optJSONObject(key);
        JSONArray names = stacks == null ? null : stacks.names();
        for (int i = 0; names != null && i < names.length(); i++) {
            String file = names.optString(i);
            if (removed.contains(file) || removed.contains(stacks.optString(file))) stacks.remove(file);
        }
        if (stacks != null && stacks.length() == 0) meal.remove(key);
    }

    /** An in-flight worker may still own a pre-move snapshot of the durable task. */
    static void refreshJobTarget(JSONObject job, JSONArray queue) throws JSONException {
        String request = job.optString("analysisId"), id = job.optString("id");
        if (request.isEmpty() || id.isEmpty()) return;
        for (int i = 0; i < queue.length(); i++) {
            JSONObject stored = queue.optJSONObject(i);
            if (stored != null && request.equals(stored.optString("analysisId"))
                    && id.equals(stored.optString("id")) && job.optLong("epoch") == stored.optLong("epoch")) {
                job.put("date", stored.optString("date")).put("meal", stored.optString("meal"))
                        .put("draft", stored.optBoolean("draft"));
                return;
            }
        }
    }
}
