package com.eatrecord.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;

/** Standalone JVM notebook projection checks; no Android, storage, credentials or network. */
public final class NotebookMediaTest {
    private static int checks;
    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
        checks++;
    }
    private static JSONObject food(String source, String cutout) throws Exception {
        return new JSONObject().put("sourcePhoto", source).put("cutoutFile", cutout);
    }
    private static JSONObject day(JSONObject meal) throws Exception {
        return new JSONObject().put("breakfast", meal);
    }
    public static void main(String[] args) throws Exception {
        check(NotebookMediaEntries.collect(null, true).isEmpty(), "null day");
        check(NotebookMediaEntries.collect(new JSONObject(), true).isEmpty(), "empty day");
        JSONObject legacy = new JSONObject().put("text", "旧备注")
                .put("photos", new JSONArray().put("photo.jpg"))
                .put("stickers", new JSONArray().put("sticker.png"));
        List<NotebookMediaEntries.Entry> entries = NotebookMediaEntries.collect(day(legacy), true);
        check(entries.size() == 2, "old sticker and photo retained");
        check(entries.get(0).sticker && entries.get(0).fileName.equals("sticker.png"), "legacy sticker order unchanged");
        check(!entries.get(1).sticker && entries.get(1).fileName.equals("photo.jpg"), "legacy photo follows sticker");
        check(entries.get(0).fallbackFile.isEmpty() && entries.get(0).displayRotation == 0, "legacy has no synthetic transform");
        entries = NotebookMediaEntries.collect(day(legacy), false);
        check(entries.size() == 1 && entries.get(0).sticker, "hide plain photos but retain old sticker");

        JSONObject current = new JSONObject().put("foodItems", new JSONArray().put(food("source.jpg", "cutout.png")))
                .put("photos", new JSONArray().put("source.jpg").put("unrelated.jpg"))
                .put("stickers", new JSONArray().put("cutout.png").put("old.png"));
        String before = current.toString();
        entries = NotebookMediaEntries.collect(day(current), true);
        check(entries.size() == 3, "new food suppresses original and alpha duplicates");
        check(entries.get(0).fileName.equals("cutout.png") && entries.get(0).sticker, "food cutout is first class sticker");
        check(entries.get(0).fallbackFile.equals("source.jpg"), "missing alpha can fall back without duplicate entry");
        check(entries.get(1).fileName.equals("old.png") && entries.get(2).fileName.equals("unrelated.jpg"), "unrelated old media retained");
        check(current.toString().equals(before), "projection never changes meal JSON");
        entries = NotebookMediaEntries.collect(day(current), false);
        check(entries.size() == 2 && entries.get(0).fileName.equals("cutout.png"), "food cutout does not depend on plain photo toggle");

        JSONObject sourceOnly = new JSONObject().put("foodItems", new JSONArray().put(food("waiting.jpg", "")))
                .put("photos", new JSONArray().put("waiting.jpg"));
        entries = NotebookMediaEntries.collect(day(sourceOnly), true);
        check(entries.size() == 1 && !entries.get(0).sticker && entries.get(0).fileName.equals("waiting.jpg"), "source-only food falls back once");
        check(NotebookMediaEntries.collect(day(sourceOnly), false).isEmpty(), "source-only food respects photo toggle");
        sourceOnly.put("stickers", new JSONArray().put("legacy.png"));
        entries = NotebookMediaEntries.collect(day(sourceOnly), false);
        check(entries.size() == 1 && entries.get(0).fileName.equals("legacy.png"), "pending cutout does not hide unrelated old sticker");

        JSONObject repeated = new JSONObject().put("foodItems", new JSONArray()
                .put(food("same.jpg", "same.png")).put(food("same.jpg", "same.png")))
                .put("photos", new JSONArray().put("same.jpg").put("same.png"))
                .put("stickers", new JSONArray().put("same.png").put("same.jpg"));
        check(NotebookMediaEntries.collect(day(repeated), true).size() == 1, "cross-array and repeated-food duplicates suppressed");
        JSONObject distinctMeals = day(repeated).put("lunch", new JSONObject(repeated.toString()));
        entries = NotebookMediaEntries.collect(distinctMeals, true);
        check(entries.size() == 2 && entries.get(0).mealKey.equals("breakfast") && entries.get(1).mealKey.equals("lunch"),
                "same physical file remains in distinct meals");
        JSONObject skipped = new JSONObject(current.toString()).put("skipped", true);
        check(NotebookMediaEntries.collect(day(skipped), true).isEmpty(), "skipped meals are not drawn");
        check(NotebookMediaEntries.collect(day(new JSONObject().put("text", "只有文字")), true).isEmpty(), "text-only meal has no artificial media");

        JSONObject invalid = new JSONObject().put("foodItems", new JSONArray().put(JSONObject.NULL)
                .put(food("safe.jpg", "../escape.png")))
                .put("stickers", new JSONArray().put("../bad.png").put("path/bad.png").put("path\\bad.png"));
        entries = NotebookMediaEntries.collect(day(invalid), true);
        check(entries.size() == 1 && entries.get(0).fileName.equals("safe.jpg"), "unsafe alpha and legacy paths rejected");
        check(NotebookMediaEntries.collect(day(invalid), false).isEmpty(), "invalid alpha cannot bypass photo setting");

        JSONObject rotated = day(new JSONObject().put("foodItems", new JSONArray().put(
                food("source.jpg", "cutout.png").put("displayRotation", -90))));
        before = rotated.toString();
        entries = NotebookMediaEntries.collect(rotated, false);
        check(entries.get(0).displayRotation == 270, "left turn normalized to 270");
        check(rotated.toString().equals(before), "rotation normalization is display only");
        check(NotebookMediaEntries.normalizeRotation(450) == 90 && NotebookMediaEntries.normalizeRotation(-450) == 270,
                "multiple rotations wrap quarter turns");
        check(NotebookMediaEntries.normalizeRotation(35) == 0 && NotebookMediaEntries.normalizeRotation(110) == 0,
                "malformed non-quarter turns reset to zero");
        check(NotebookMediaEntries.normalizeRotation(Integer.MIN_VALUE) == 0, "invalid extreme stored int resets to zero");
        JSONObject extraMeals = new JSONObject().put("snack", legacy).put("afternoon_tea", legacy).put("dinner", legacy);
        entries = NotebookMediaEntries.collect(extraMeals, false);
        check(entries.size() == 3 && entries.get(0).mealKey.equals("afternoon_tea") && entries.get(2).mealKey.equals("snack"),
                "tea dinner and late snack retain meal order");
        System.out.println("Notebook media projection: " + checks + " checks passed");
    }
}
