package com.eatrecord.app;

import org.json.JSONArray;
import org.json.JSONObject;

/** Read-only retry policy: batch recognition must not replace corrected or completed history. */
final class FoodSavedPhotoPolicy {
    private FoodSavedPhotoPolicy() { }

    static JSONObject sourceItem(JSONObject meal, String source) {
        JSONArray foods = meal == null ? null : meal.optJSONArray("foodItems");
        for (int i = 0; foods != null && i < foods.length(); i++) {
            JSONObject item = foods.optJSONObject(i);
            if (item != null && source.equals(item.optString("sourcePhoto"))) return item;
        }
        return null;
    }

    static boolean skip(JSONObject item, String hash, boolean force) {
        if (force || item == null) return false;
        if (item.optBoolean("userEdited")) return true;
        String previous = item.optString("imageHash");
        // A restored completed result without a hash is not permission to overwrite it.
        if (previous.isEmpty() && ("completed".equals(item.optString("status"))
                || "completed".equals(item.optString("analysisStatus")))) return true;
        return previous.equals(hash) && validNutrients(item);
    }

    static boolean validNutrients(JSONObject item) {
        JSONObject nutrients = item == null ? null : item.optJSONObject("nutrients");
        if (nutrients == null) return false;
        for (int i = 0; i < FoodAnalysisResult.LOCAL.length; i++) {
            Object raw = nutrients.opt(FoodAnalysisResult.LOCAL[i]);
            if (!(raw instanceof Number)) return false;
            double number = ((Number) raw).doubleValue();
            if (!FoodAnalysisResult.finite(number) || number < 0 || number > (i == 5 ? 100000 : i == 0 ? 20000 : 5000)) return false;
        }
        return true;
    }

    static boolean reuseCutout(JSONObject item, String hash, boolean force, boolean alphaExists) {
        if (force || item == null || !alphaExists || !NutritionStore.safeName(item.optString("cutoutFile"))) return false;
        String previous = item.optString("imageHash");
        return previous.isEmpty() || previous.equals(hash);
    }

    static boolean queued(JSONArray jobs, JSONObject item, String hash, long epoch) {
        if (item == null || item.optString("id").isEmpty() || item.optString("analysisId").isEmpty()) return false;
        for (int i = 0; i < jobs.length(); i++) {
            JSONObject job = jobs.optJSONObject(i);
            if (job != null && epoch == job.optLong("epoch") && hash.equals(job.optString("hash"))
                    && item.optString("id").equals(job.optString("id"))
                    && item.optString("analysisId").equals(job.optString("analysisId"))) return true;
        }
        return false;
    }

    /** Local image work owns a separate generation so paid nutrition can finish unchanged. */
    static JSONObject prepareCutout(JSONObject previous, String requestId) throws Exception {
        JSONObject next = new JSONObject(previous.toString());
        next.put("cutoutRequestId", requestId).put("cutoutStatus", "pending").put("cutoutError", "");
        return next;
    }

    /** Re-estimation is explicit; retain the old valid result until the replacement is validated. */
    static JSONObject prepareNutrition(JSONObject previous, String requestId) throws Exception {
        JSONObject next = new JSONObject(previous.toString());
        String hint = correctedName(previous);
        next.put("analysisId", requestId).put("analysisStatus", "pending").put("error", "");
        if (!hint.isEmpty()) next.put("dishNameHint", hint).put("dishNameUserEdited", true);
        if (!next.has("nutrients")) next.put("status", "pending");
        return next;
    }

    static String correctedName(JSONObject item) {
        if (item == null) return "";
        if (!item.optBoolean("dishNameUserEdited") && !item.optBoolean("userEdited")
                && item.optString("dishNameHint").isEmpty()) return "";
        if(item.optBoolean("userEdited")){
            String latest=FoodNameHint.normalize(item.optString("dishName"));
            if(!latest.isEmpty())return latest;
        }
        String hint = FoodNameHint.normalize(item.optString("dishNameHint"));
        return hint.isEmpty() ? FoodNameHint.normalize(item.optString("dishName")) : hint;
    }

    static boolean cutoutGenerationCurrent(JSONObject item, JSONObject job) {
        return item != null && item.optString("cutoutRequestId").equals(job.optString("analysisId"))
                && !job.optString("analysisId").isEmpty()
                && item.optLong("cutoutEditedAt") == job.optLong("cutoutBaseEditedAt");
    }

    /** A nutrition form may be stale with respect to local mask work or rotation. */
    static JSONObject mergeNutritionEdit(JSONObject latest,JSONObject edited,String requestId)throws Exception {
        JSONObject next=new JSONObject(edited.toString());
        java.util.ArrayList<String> imageFields=new java.util.ArrayList<>();
        for(java.util.Iterator<String> keys=next.keys();keys.hasNext();){String key=keys.next();if(key.startsWith("cutout")||"displayRotation".equals(key))imageFields.add(key);}
        for(String key:imageFields)next.remove(key);
        for(java.util.Iterator<String> keys=latest.keys();keys.hasNext();){String key=keys.next();if(key.startsWith("cutout")||"displayRotation".equals(key))next.put(key,latest.get(key));}
        for(String key:new String[]{"id","sourcePhoto","imageHash","createdAt"}){
            if(latest.has(key))next.put(key,latest.get(key));else next.remove(key);
        }
        next.remove("draft");next.remove("draftDate");next.remove("draftMeal");
        next.put("analysisId",requestId).put("userEdited",true).put("error","");
        if(next.has("nutrients"))next.put("status","completed").put("analysisStatus","completed");
        else next.put("status","pending").put("analysisStatus","edited");
        return next;
    }

    /** Editing text/nutrition only supersedes cloud inference, never independent local work. */
    static JSONArray withoutNutritionJobs(JSONArray queue,String id) {
        JSONArray next=new JSONArray();
        for(int i=0;i<queue.length();i++){
            JSONObject job=queue.optJSONObject(i);
            if(job!=null&&(!id.equals(job.optString("id"))||job.optBoolean("cutoutOnly")))next.put(job);
        }
        return next;
    }
}
