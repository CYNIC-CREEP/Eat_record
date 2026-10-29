package com.eatrecord.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Local import drafts are deliberately outside days_json, profile_json and cloud backup. */
public final class FoodDraftStore {
    public static final String KEY = "food_analysis_drafts";
    private FoodDraftStore() { }

    static JSONObject read(SharedPreferences prefs) { return NutritionStore.read(prefs, KEY); }

    /** A detached UI snapshot. Draft routing fields never enter a confirmed food item. */
    public static JSONObject item(Context context, String source) {
        synchronized (NutritionStore.LOCK) {
            JSONObject record = read(context.getSharedPreferences(NutritionStore.PREFS, 0)).optJSONObject(source);
            if (record == null || record.optJSONObject("item") == null) return null;
            try {
                return new JSONObject(record.getJSONObject("item").toString())
                    .put("draft", true).put("draftDate", record.optString("date")).put("draftMeal", record.optString("meal"));
            } catch (Exception ignored) { return null; }
        }
    }

    public static List<String> pendingSources(Context context) {
        synchronized (NutritionStore.LOCK) {
            ArrayList<String> result = new ArrayList<String>();
            JSONObject drafts = read(context.getSharedPreferences(NutritionStore.PREFS, 0));
            Iterator<String> keys = drafts.keys();
            while (keys.hasNext()) {
                String source = keys.next();
                if (NutritionStore.safeName(source) && drafts.optJSONObject(source) != null) result.add(source);
            }
            return result;
        }
    }

    /** Used by private-media cleanup only; do not include this list in backups or cloud media. */
    public static List<String> mediaFiles(Context context) {
        synchronized (NutritionStore.LOCK) {
            return media(read(context.getSharedPreferences(NutritionStore.PREFS, 0)));
        }
    }

    private static List<String> media(JSONObject drafts) {
        ArrayList<String> result = new ArrayList<String>();
        Iterator<String> keys = drafts.keys();
        while (keys.hasNext()) {
            JSONObject record = drafts.optJSONObject(keys.next());
            JSONObject item = record == null ? null : record.optJSONObject("item");
            if (item == null) continue;
            for (String key : new String[]{"sourcePhoto", "cutoutFile"}) {
                String name = item.optString(key);
                if (NutritionStore.safeName(name) && !result.contains(name)) result.add(name);
            }
            JSONArray previous = record.optJSONArray("ownedCutouts");
            for (int i = 0; previous != null && i < previous.length(); i++) {
                String name = previous.optString(i);
                if (NutritionStore.safeName(name) && !result.contains(name)) result.add(name);
            }
        }
        return result;
    }

    /** Commit the entire selection in one preferences transaction. Repeated confirmation is harmless. */
    public static boolean confirm(Context context, List<String> sources) {
        if (sources == null || sources.isEmpty()) return false;
        boolean committed;
        synchronized (NutritionStore.LOCK) {
            SharedPreferences prefs = context.getSharedPreferences(NutritionStore.PREFS, 0);
            JSONObject drafts = read(prefs), days = NutritionStore.read(prefs, NutritionStore.DAYS);
            JSONArray queue = FoodAnalysisJobService.jobs(prefs);
            Set<String> selected = new HashSet<String>(sources);
            Set<String> previousCutouts = new HashSet<String>();
            try {
                // Validate the whole selection before changing any data.
                for (String source : selected) {
                    if (!NutritionStore.safeName(source)) return false;
                    JSONObject record = drafts.optJSONObject(source);
                    if (record == null) {
                        if (!references(days, source)) return false;
                        continue;
                    }
                    JSONObject item = record.optJSONObject("item");
                    if (!validTarget(record.optString("date"), record.optString("meal")) || item == null
                        || !source.equals(item.optString("sourcePhoto")) || item.optString("id").isEmpty()
                        || !new File(context.getFilesDir(), source).isFile()) return false;
                    // A confirmation can keep the original after a real local failure, but cannot race
                    // an unfinished first cutout and unexpectedly place an unprocessed image at home.
                    if (item.optString("cutoutFile").isEmpty() && item.optString("cutoutError").isEmpty()) return false;
                }
                for (String source : selected) {
                    JSONObject record = drafts.optJSONObject(source);
                    if (record == null) continue;
                    String date = record.getString("date"), key = record.getString("meal");
                    JSONObject day = days.optJSONObject(date);
                    if (day == null) { day = new JSONObject(); days.put(date, day); }
                    JSONObject meal = day.optJSONObject(key);
                    if (meal == null) { meal = new JSONObject(); day.put(key, meal); }
                    JSONArray photos = meal.optJSONArray("photos");
                    if (photos == null) { photos = new JSONArray(); meal.put("photos", photos); }
                    if (!NutritionStore.contains(photos, source)) photos.put(source);
                    JSONObject accepted = new JSONObject(record.getJSONObject("item").toString());
                    JSONArray previous = record.optJSONArray("ownedCutouts");
                    for (int i = 0; previous != null && i < previous.length(); i++) previousCutouts.add(previous.optString(i));
                    accepted.remove("draft"); accepted.remove("draftDate"); accepted.remove("draftMeal");
                    JSONArray items = meal.optJSONArray("foodItems");
                    if (items == null) { items = new JSONArray(); meal.put("foodItems", items); }
                    if (NutritionStore.find(meal, accepted.optString("id")) == null) items.put(accepted);
                    meal.remove("skipped");
                    // Preserve identity/generation so an in-flight request can finish on the accepted item.
                    for (int i = 0; i < queue.length(); i++) {
                        JSONObject job = queue.optJSONObject(i);
                        if (job != null && accepted.optString("id").equals(job.optString("id"))
                            && (accepted.optString("analysisId").equals(job.optString("analysisId"))
                                || job.optBoolean("cutoutOnly") && accepted.optString("cutoutRequestId").equals(job.optString("analysisId")))) {
                            // Also preserves intent for drafts created by earlier Beta builds.
                            job.put("draft", false).put("interactive", true);
                        }
                    }
                    drafts.remove(source);
                }
                committed = prefs.edit().putString(NutritionStore.DAYS, days.toString())
                    .putString(KEY, drafts.toString()).putString(FoodAnalysisJobService.QUEUE, queue.toString()).commit();
                if (committed) purgeUnreferenced(context, prefs, previousCutouts);
            } catch (Exception ignored) { return false; }
        }
        if (committed) { changed(context); FoodAnalysisJobService.schedule(context, false, 0); }
        return committed;
    }

    /** User edits invalidate old inference responses, including requests already in flight. */
    public static boolean replace(Context context, String source, JSONObject replacement) {
        if (replacement == null) return false;
        SharedPreferences prefs = context.getSharedPreferences(NutritionStore.PREFS, 0);
        JSONObject record = read(prefs).optJSONObject(source);
        if (record == null) return false;
        // The common operation re-reads the latest draft/confirmed record under its lock.
        return FoodAnalysisJobService.saveNutritionEdit(context,record.optString("date"),record.optString("meal"),source,replacement);
    }

    /** Editing only the alpha image must not cancel a valid cloud nutrition request. */
    public static boolean replaceCutout(Context context, String source, String cutout) {
        return replaceCutout(context, source, cutout, null);
    }
    public static boolean replaceCutout(Context context, String source, String cutout, JSONArray bounds) {
        if (!NutritionStore.safeName(cutout) || !cutout.startsWith("food_cutout_") || !cutout.endsWith(".png")) return false;
        synchronized (NutritionStore.LOCK) {
            SharedPreferences prefs = context.getSharedPreferences(NutritionStore.PREFS, 0);
            JSONObject drafts = read(prefs), record = drafts.optJSONObject(source);
            JSONObject item = record == null ? null : record.optJSONObject("item");
            if (item == null || !new File(context.getFilesDir(), cutout).isFile()) return false;
            try {
                rememberCutout(record, item.optString("cutoutFile"));
                rememberCutout(record, cutout);
                item.put("cutoutFile", cutout).put("cutoutEdited", true).put("cutoutEditedAt", System.currentTimeMillis()).put("cutoutError", "");
                if (bounds != null && bounds.length() == 4) item.put("cutoutRect", new JSONArray(bounds.toString()));
                boolean ok = prefs.edit().putString(KEY, drafts.toString()).commit();
                if (ok) changed(context);
                return ok;
            } catch (Exception ignored) { return false; }
        }
    }

    /** Cancel only drafts. A confirmed item with the same source is never removed. */
    public static void discard(Context context, List<String> sources) {
        if (sources == null || sources.isEmpty()) return;
        Set<String> requests=new HashSet<String>();
        synchronized (NutritionStore.LOCK) {
            SharedPreferences prefs = context.getSharedPreferences(NutritionStore.PREFS, 0);
            JSONObject drafts = read(prefs);
            Set<String> ids = new HashSet<String>(), candidates = new HashSet<String>();
            for (String source : sources) {
                JSONObject record = drafts.optJSONObject(source);
                JSONObject item = record == null ? null : record.optJSONObject("item");
                if (item == null) continue;
                ids.add(item.optString("id"));
                requests.add(item.optString("analysisId"));
                if (record.optBoolean("ownedSource")) candidates.add(source);
                String cutout = item.optString("cutoutFile");
                if (cutout.startsWith("food_cutout_")) candidates.add(cutout);
                JSONArray previous = record.optJSONArray("ownedCutouts");
                for (int i = 0; previous != null && i < previous.length(); i++) candidates.add(previous.optString(i));
                drafts.remove(source);
            }
            JSONArray oldQueue=FoodAnalysisJobService.jobs(prefs);
            for(int i=0;i<oldQueue.length();i++){JSONObject job=oldQueue.optJSONObject(i);if(job!=null&&ids.contains(job.optString("id")))requests.add(job.optString("analysisId"));}
            JSONArray next = withoutJobs(oldQueue, ids);
            // Remove records first: any in-flight worker must fail its next generation check.
            if (!prefs.edit().putString(KEY, drafts.toString()).putString(FoodAnalysisJobService.QUEUE, next.toString()).commit()) return;
            purgeUnreferenced(context, prefs, candidates);
        }
        for(String request:requests)FoodAnalysisJobService.cancelRequest(request);
        changed(context);
    }

    static JSONArray withoutJobs(JSONArray queue, Set<String> ids) {
        JSONArray next = new JSONArray();
        for (int i = 0; i < queue.length(); i++) {
            JSONObject job = queue.optJSONObject(i);
            if (job != null && !ids.contains(job.optString("id"))) next.put(job);
        }
        return next;
    }

    static void rememberCutout(JSONObject record, String name) throws org.json.JSONException {
        if (!NutritionStore.safeName(name) || !name.startsWith("food_cutout_")) return;
        JSONArray owned = record.optJSONArray("ownedCutouts");
        if (owned == null) { owned = new JSONArray(); record.put("ownedCutouts", owned); }
        if (!NutritionStore.contains(owned, name)) owned.put(name);
    }

    private static void purgeUnreferenced(Context context, SharedPreferences prefs, Set<String> candidates) {
        JSONObject days = NutritionStore.read(prefs, NutritionStore.DAYS), profile = NutritionStore.read(prefs, NutritionStore.PROFILE);
        JSONObject drafts = read(prefs);
        for (String name : candidates) {
            if (!NutritionStore.safeName(name) || references(days, name) || references(profile, name) || references(drafts, name)) continue;
            try {
                File target = new File(context.getFilesDir(), name);
                if (target.getCanonicalFile().getParentFile().equals(context.getFilesDir().getCanonicalFile())) target.delete();
            } catch (Exception ignored) { }
        }
    }

    static boolean validTarget(String date, String key) {
        if (date == null || !date.matches("\\d{4}-\\d{2}-\\d{2}")) return false;
        for (String meal : NutritionStore.MEALS) if (meal.equals(key)) return true;
        return false;
    }

    /** Conservative reference scan also protects recycle-bin media and existing stickers. */
    static boolean references(Object value, String source) {
        if (value instanceof String) return source.equals(value);
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject)value;
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) if (references(object.opt(keys.next()), source)) return true;
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray)value;
            for (int i = 0; i < array.length(); i++) if (references(array.opt(i), source)) return true;
        }
        return false;
    }

    static void changed(Context context) {
        context.sendBroadcast(new Intent(FoodAnalysisJobService.CHANGED).setPackage(context.getPackageName()));
    }
}
