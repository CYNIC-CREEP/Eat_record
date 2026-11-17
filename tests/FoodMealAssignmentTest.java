package com.eatrecord.app;

import org.json.JSONArray;
import org.json.JSONObject;

/** Pure transaction and running-task routing checks, using synthetic diary records only. */
public final class FoodMealAssignmentTest {
    private static int checks;
    private static final String DATE = "2026-11-13";
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
    private static JSONObject food(String id, String source, String cutout, int calories) throws Exception {
        JSONObject nutrients = new JSONObject();
        for (String key : FoodAnalysisResult.LOCAL) nutrients.put(key, 0);
        nutrients.put("caloriesKcal", calories);
        return new JSONObject().put("id", id).put("analysisId", "analysis-" + id)
                .put("cutoutRequestId", "cutout-" + id).put("sourcePhoto", source).put("cutoutFile", cutout)
                .put("status", "completed").put("analysisStatus", "completed").put("nutrients", nutrients)
                .put("dishName", "合成餐食").put("estimatedGrams", 200).put("displayRotation", 90)
                .put("cutoutEdited", true).put("cutoutRect", new JSONArray().put(1).put(2).put(90).put(80))
                .put("userEdited", true).put("createdAt", 123456789);
    }
    private static JSONObject meal(JSONObject... items) throws Exception {
        JSONArray foods = new JSONArray(), photos = new JSONArray(), stickers = new JSONArray();
        for (JSONObject item : items) {
            foods.put(item);
            if (!NutritionStore.contains(photos, item.optString("sourcePhoto"))) photos.put(item.optString("sourcePhoto"));
            if (!NutritionStore.contains(stickers, item.optString("cutoutFile"))) stickers.put(item.optString("cutoutFile"));
        }
        return new JSONObject().put("foodItems", foods).put("photos", photos).put("stickers", stickers);
    }
    private static JSONObject job(String id, String key, boolean cutout) throws Exception {
        return new JSONObject().put("id", id).put("analysisId", (cutout ? "cutout-" : "analysis-") + id)
                .put("date", DATE).put("meal", key).put("draft", false).put("cutoutOnly", cutout)
                .put("epoch", 7).put("recognitionStarted", true).put("attempts", 2);
    }
    private static int count(JSONObject day, String id) {
        int count = 0;
        for (String key : NutritionStore.MEALS) {
            JSONArray items = day.optJSONObject(key) == null ? null : day.optJSONObject(key).optJSONArray("foodItems");
            for (int i = 0; items != null && i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item != null && id.equals(item.optString("id"))) count++;
            }
        }
        return count;
    }
    public static void main(String[] args) throws Exception {
        String[] keys = {"lunch", "breakfast", "snack", "dinner", "lunch"};
        for (int i = 0; i < keys.length - 1; i++) check(keys[i + 1].equals(FoodMealAssignment.nextMeal(keys[i])), "specified cycle");
        check("lunch".equals(FoodMealAssignment.nextMeal("afternoon_tea")), "legacy afternoon tea remains until explicit correction");

        JSONObject selected = food("chosen", "shared.jpg", "chosen.png", 500);
        JSONObject sibling = food("sibling", "shared.jpg", "sibling.png", 300);
        JSONObject unrelated = food("other", "other.jpg", "other.png", 200);
        JSONObject lunch = meal(selected, sibling).put("text", "午餐原备注").put("manualCalories", 1200);
        lunch.getJSONArray("photos").put("legacy.jpg");
        lunch.put("photoStacks", new JSONObject().put("shared.jpg", "legacy.jpg"));
        lunch.put("stickerStacks", new JSONObject().put("chosen.png", "sibling.png"));
        JSONObject breakfast = meal(unrelated).put("text", "早餐备注").put("manualCalories", 700).put("skipped", true);
        JSONObject day = new JSONObject().put("lunch", lunch).put("breakfast", breakfast)
                .put("manualCaloriesOverride", true).put("manualCaloriesByMeal", true)
                .put("extraMeals", new JSONArray().put("afternoon_tea")).put("favorite", true);
        JSONObject days = new JSONObject().put(DATE, day);
        JSONArray queue = new JSONArray().put(job("chosen", "lunch", false)).put(job("chosen", "lunch", true))
                .put(job("sibling", "lunch", false)).put(job("chosen", "lunch", false).put("draft", true))
                .put(job("chosen", "lunch", false).put("date", "2026-11-12"));
        JSONObject running = new JSONObject(queue.getJSONObject(0).toString());
        String itemBefore = selected.toString(), extras = day.getJSONArray("extraMeals").toString();
        check(FoodMealAssignment.move(days, queue, DATE, "lunch", "breakfast", "chosen"), "first move succeeds");
        check(NutritionStore.find(lunch, "chosen") == null, "selected item leaves source");
        check(NutritionStore.find(breakfast, "chosen").toString().equals(itemBefore), "all food metadata stays exact");
        check(NutritionStore.find(lunch, "sibling") != null && NutritionStore.find(breakfast, "other") != null, "other foods stay in place");
        check(count(day, "chosen") == 1, "one item after assignment");
        check(FoodSavedPhotoPolicy.sourceItem(lunch, "shared.jpg") == sibling, "legacy source-only callers retain first-item selection");
        check(FoodSavedPhotoPolicy.sourceItem(lunch, "shared.jpg", "sibling") == sibling, "detail selection uses immutable identity");
        check(FoodSavedPhotoPolicy.sourceItem(lunch, "other.jpg", "sibling") == null, "identity lookup also validates original photo");
        check(FoodSavedPhotoPolicy.sourceItem(lunch, "shared.jpg", "deleted") == null, "deleted identity cannot fall back to sibling");
        check(NutritionStore.contains(lunch.getJSONArray("photos"), "shared.jpg"), "sibling retains shared source photo");
        check(NutritionStore.contains(breakfast.getJSONArray("photos"), "shared.jpg"), "new meal owns source reference");
        check(NutritionStore.contains(lunch.getJSONArray("photos"), "legacy.jpg"), "unrelated legacy photo survives");
        check(!NutritionStore.contains(lunch.getJSONArray("stickers"), "chosen.png"), "unique alpha reference leaves source");
        check(NutritionStore.contains(breakfast.getJSONArray("stickers"), "chosen.png"), "unique alpha reference follows food");
        check(lunch.has("photoStacks") && !lunch.has("stickerStacks"), "split removes dangling stack edges only");
        check("午餐原备注".equals(lunch.getString("text")) && "早餐备注".equals(breakfast.getString("text")), "meal-wide notes never move");
        check(lunch.getInt("manualCalories") == 1200 && breakfast.getInt("manualCalories") == 700, "manual calories remain meal-wide");
        check(day.getBoolean("manualCaloriesOverride") && day.getBoolean("manualCaloriesByMeal") && day.getBoolean("favorite"), "day metadata retained");
        check(day.getJSONArray("extraMeals").toString().equals(extras), "legacy extra-meal configuration untouched");
        check(!breakfast.has("skipped"), "destination with a food becomes visible");
        check(queue.getJSONObject(0).getString("meal").equals("breakfast") && queue.getJSONObject(1).getString("meal").equals("breakfast"), "nutrition and cutout task routes move together");
        check(queue.getJSONObject(2).getString("meal").equals("lunch"), "other item task does not move");
        check(queue.getJSONObject(3).getString("meal").equals("lunch") && queue.getJSONObject(4).getString("meal").equals("lunch"), "draft and other-day jobs stay unchanged");
        FoodMealAssignment.refreshJobTarget(running, queue);
        check(running.getString("meal").equals("breakfast"), "detached running task finds new durable route");
        check(running.getBoolean("recognitionStarted") && running.getInt("attempts") == 2
                && running.getString("analysisId").equals("analysis-chosen"), "routing keeps request identity and retry state");
        JSONObject wrongEpoch = new JSONObject(running.toString()).put("epoch", 8).put("meal", "lunch");
        FoodMealAssignment.refreshJobTarget(wrongEpoch, queue);
        check(wrongEpoch.getString("meal").equals("lunch"), "old epoch cannot adopt a different generation route");

        for (int i = 1; i < keys.length - 1; i++) {
            check(FoodMealAssignment.move(days, queue, DATE, keys[i], keys[i + 1], "chosen"), "successive correction succeeds");
            FoodMealAssignment.refreshJobTarget(running, queue);
            check(running.getString("meal").equals(keys[i + 1]), "running task follows repeated correction");
            check(count(day, "chosen") == 1, "rapid corrections do not duplicate food");
        }
        check(NutritionStore.find(lunch, "chosen") != null, "whole cycle returns to lunch");
        check(FoodSavedPhotoPolicy.sourceItem(lunch, "shared.jpg", "chosen") == selected, "returned food can be edited despite a sibling preceding it");
        check(NutritionStore.totals(day).getDouble("caloriesKcal") == 1000, "all recognized calories preserved across cycle");
        check(!NutritionStore.contains(breakfast.getJSONArray("photos"), "shared.jpg"), "temporary destination releases shared source when its moved food leaves");
        check(NutritionStore.find(breakfast, "other") != null, "destination's preexisting food remains");
        String dataBefore = days.toString(), queueBefore = queue.toString();
        check(!FoodMealAssignment.move(days, queue, DATE, "breakfast", "snack", "chosen"), "stale source is rejected");
        check(!FoodMealAssignment.move(days, queue, DATE, "lunch", "bad", "chosen"), "unknown meal is rejected");
        check(!FoodMealAssignment.move(days, queue, DATE, "lunch", "breakfast", "deleted"), "deleted food never resurrects");
        check(!FoodMealAssignment.move(days, queue, null, "lunch", "breakfast", "chosen"), "null date is rejected");
        check(dataBefore.equals(days.toString()) && queueBefore.equals(queue.toString()), "invalid requests do not mutate transaction copies");

        JSONObject legacy = food("legacy", "old.jpg", "old.png", 42);
        JSONObject legacyDay = new JSONObject().put("afternoon_tea", new JSONObject().put("foodItems", new JSONArray().put(legacy)).put("text", "下午茶"));
        JSONObject legacyDays = new JSONObject().put(DATE, legacyDay);
        check(FoodMealAssignment.move(legacyDays, new JSONArray(), DATE, "afternoon_tea", "lunch", "legacy"), "item-only legacy record can be reassigned");
        check("下午茶".equals(legacyDay.getJSONObject("afternoon_tea").getString("text")), "legacy note survives correction");
        check(NutritionStore.contains(legacyDay.getJSONObject("lunch").getJSONArray("photos"), "old.jpg"), "legacy original remains editable after assignment");
        JSONObject duplicate = new JSONObject().put(DATE, new JSONObject().put("lunch", meal(selected)).put("breakfast", meal(selected)));
        String duplicateBefore = duplicate.toString();
        check(!FoodMealAssignment.move(duplicate, new JSONArray(), DATE, "lunch", "dinner", "chosen"), "ambiguous duplicate identity is rejected");
        check(duplicateBefore.equals(duplicate.toString()), "ambiguous identity never mutates data");
        System.out.println("PASS: " + checks + " food meal assignment and running-task routing checks");
    }
}
