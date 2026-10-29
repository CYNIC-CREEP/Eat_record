package com.eatrecord.app;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/** Pure queue and short-lived compatibility policy checks. No network, images or credentials. */
public final class FoodSchedulingTest {
    private static int checks;
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
    private static JSONObject job(String id, boolean draft, boolean cutout) throws Exception {
        return new JSONObject().put("analysisId", id).put("draft", draft).put("cutoutDone", cutout);
    }
    public static void main(String[] args) throws Exception {
        Set<String> deferred = new HashSet<>();
        JSONArray queue = new JSONArray().put(job("saved-network", false, true))
                .put(job("saved-local", false, false)).put(job("draft-network", true, true))
                .put(job("draft-local", true, false));
        String original = queue.toString();
        check(FoodJobPriority.next(queue, deferred) == 3, "open local draft overtakes batch work");
        deferred.add("draft-local");
        check(FoodJobPriority.next(queue, deferred) == 2, "ready draft does not wait for saved-photo cutouts");
        deferred.add("draft-network");
        check(FoodJobPriority.next(queue, deferred) == 1, "saved local work precedes saved network work");
        deferred.add("saved-local");
        check(FoodJobPriority.next(queue, deferred) == 0, "saved network work remains eligible");
        deferred.add("saved-network");
        check(FoodJobPriority.next(queue, deferred) == -1, "waiting jobs do not spin within the same drain");
        check(original.equals(queue.toString()), "selection cannot mutate persisted jobs or input flags");
        check(FoodJobPriority.next(new JSONArray(), deferred) == -1, "empty queue exits");
        JSONArray fifo = new JSONArray().put(job("a", true, false)).put(job("b", true, false));
        check(FoodJobPriority.next(fifo, Collections.emptySet()) == 0, "equal priority stays FIFO");
        fifo.getJSONObject(0).put("cutoutDone", true);
        check(FoodJobPriority.next(fifo, Collections.emptySet()) == 1, "all open drafts obtain local previews first");
        fifo.put(job("newly-imported", true, false));
        check(FoodJobPriority.next(fifo, new HashSet<>(Collections.singletonList("b"))) == 2,
                "a new draft is observed at the very next stage boundary");
        JSONArray changedGeneration = new JSONArray().put(job("replacement-generation", true, true));
        check(FoodJobPriority.next(changedGeneration, deferred) == 0, "old deferred identity never suppresses replacement generation");
        JSONArray confirmedPriority=new JSONArray().put(job("older-saved-local",false,false))
                .put(job("confirmed-interactive",false,true).put("interactive",true));
        check(FoodJobPriority.next(confirmedPriority,Collections.emptySet(),1000)==1,"confirming a just-added photo does not demote its nutrition behind saved-photo cutouts");
        confirmedPriority.getJSONObject(1).put("notBeforeAt",1100);
        check(FoodJobPriority.next(confirmedPriority,Collections.emptySet(),1000)==0,"new local work cannot bypass a cloud request's per-job retry delay");
        check(FoodJobPriority.next(confirmedPriority,Collections.emptySet(),1100)==1,"backoff becomes eligible at its own deadline");
        check(FoodJobPriority.next(new JSONArray().put(job("paused",true,true).put("notBeforeAt",1100)),Collections.emptySet(),1000)==-1,"an all-delayed queue does not spin or immediately repost");
        JSONArray delayed=new JSONArray().put(job("failed-cloud",false,true).put("notBeforeAt",31000));
        String delayedBefore=delayed.toString();
        check(FoodJobPriority.nextDelay(delayed,true,1000)==30000,"failed request retains its own thirty-second deadline");
        delayed.put(job("new-interactive",true,false));
        check(FoodJobPriority.nextDelay(delayed,true,1000)==0,"new interactive work at drain exit is not held behind a global retry floor");
        check(FoodJobPriority.nextDelay(delayed,false,1000)==0,"offline or unconfigured cloud never delays ready local cutout work");
        check(FoodJobPriority.nextDelay(new JSONArray().put(job("waiting-api",true,true)),false,1000)==-1,"waiting consent/API alone does not schedule an idle polling job");
        check(FoodJobPriority.nextDelay(new JSONArray().put(job("finished-local-only",true,true).put("cutoutOnly",true)),true,1000)==-1,"completed local-only jobs never fall through to cloud scheduling");
        check(FoodJobPriority.nextDelay(new JSONArray().put(JSONObject.NULL),true,1000)==-1,"empty or null-only queues have no runnable deadline");
        JSONArray staggered=new JSONArray().put(job("later",false,true).put("notBeforeAt",61000))
                .put(job("sooner",false,true).put("notBeforeAt",16000));
        String staggeredBefore=staggered.toString();
        check(FoodJobPriority.nextDelay(staggered,true,1000)==15000,"scheduling uses the earliest per-job backoff instead of the last retry");
        check(staggeredBefore.equals(staggered.toString())&&delayedBefore.equals(new JSONArray().put(delayed.getJSONObject(0)).toString()),"deadline scheduling is read-only on all persisted queue entries");
        check(FoodJobPriority.next(new JSONArray().put(JSONObject.NULL).put(new JSONObject()), Collections.emptySet()) == 1,
                "null entries are skipped but invalid jobs still reach current/remove validation");

        FoodRequestCompatibility cache = new FoodRequestCompatibility(2, 100);
        check(cache.rejected("scope-a", 0) == 0, "unknown scope has all capabilities enabled");
        cache.remember("scope-a", FoodRequestCompatibility.STREAM, 10);
        check(cache.rejected("scope-a", 11) == FoodRequestCompatibility.STREAM, "explicit stream refusal remembered");
        cache.remember("scope-a", FoodRequestCompatibility.SCHEMA, 12);
        check(cache.rejected("scope-a", 13) == (FoodRequestCompatibility.STREAM | FoodRequestCompatibility.SCHEMA),
                "separate optional refusals accumulate without disabling unrelated tuning");
        check(cache.rejected("another-credential-or-model", 13) == 0, "different scope cannot reuse capability refusal");
        cache.remember("scope-b", FoodRequestCompatibility.TUNING, 14);
        cache.remember("scope-c", FoodRequestCompatibility.STREAM, 15);
        check(cache.rejected("scope-a", 16) == 0, "bounded cache evicts oldest scope");
        check(cache.rejected("scope-b", 113) == FoodRequestCompatibility.TUNING, "unexpired hint retained");
        check(cache.rejected("scope-b", 114) == 0, "expiry retries actual provider capabilities");
        check(cache.rejected("scope-c", 2) == 0, "clock reversal invalidates hint");

        JSONObject unfinished = new JSONObject().put("id", "old-item").put("analysisId", "old-generation")
                .put("sourcePhoto", "restored.jpg").put("imageHash", "same-image")
                .put("status", "pending").put("cutoutFile", "restored-alpha.png")
                .put("cutoutEdited", true).put("cutoutUserEdited", true);
        JSONObject restoredMeal = new JSONObject().put("foodItems", new JSONArray().put(unfinished));
        String restoredOriginal = restoredMeal.toString();
        check(FoodSavedPhotoPolicy.sourceItem(restoredMeal, "restored.jpg") == unfinished,
                "foodItems-only backup resolves original without legacy photos array");
        check(FoodSavedPhotoPolicy.sourceItem(restoredMeal, "other.jpg") == null
                && FoodSavedPhotoPolicy.sourceItem(null, "restored.jpg") == null, "unreferenced source is rejected");
        check(!FoodSavedPhotoPolicy.skip(unfinished, "same-image", false),
                "unfinished same-hash record without an active task can be retried");
        check(FoodSavedPhotoPolicy.reuseCutout(unfinished, "same-image", false, true),
                "nutrition retry reuses existing alpha and its edited geometry");
        check(!FoodSavedPhotoPolicy.reuseCutout(unfinished, "same-image", true, true),
                "explicit force still requests a new cutout");
        check(!FoodSavedPhotoPolicy.reuseCutout(unfinished, "changed-image", false, true),
                "alpha for different original bytes cannot be reused");
        check(!FoodSavedPhotoPolicy.reuseCutout(unfinished, "same-image", false, false),
                "missing alpha file still needs local processing");
        JSONObject missingHash = new JSONObject(unfinished.toString()); missingHash.remove("imageHash");
        check(FoodSavedPhotoPolicy.reuseCutout(missingHash, "same-image", false, true),
                "old missing-hash record retains its existing source-aligned alpha");
        JSONObject unsafeAlpha = new JSONObject(unfinished.toString()).put("cutoutFile", "../bad.png");
        check(!FoodSavedPhotoPolicy.reuseCutout(unsafeAlpha, "same-image", false, true), "unsafe alpha cannot be reused");
        JSONObject edited = new JSONObject(missingHash.toString()).put("userEdited", true);
        check(FoodSavedPhotoPolicy.skip(edited, "same-image", false), "manual nutrition is never replaced by batch retry");
        check(!FoodSavedPhotoPolicy.skip(edited, "same-image", true), "explicit force can replace edited nutrition as before");
        JSONObject completedLegacy = new JSONObject(missingHash.toString()).put("status", "completed");
        check(FoodSavedPhotoPolicy.skip(completedLegacy, "same-image", false),
                "completed missing-hash legacy result is preserved conservatively");
        JSONObject localNutrients = new JSONObject();
        for (String field : FoodAnalysisResult.LOCAL) localNutrients.put(field, 1);
        JSONObject completed = new JSONObject(unfinished.toString()).put("nutrients", localNutrients);
        check(FoodSavedPhotoPolicy.skip(completed, "same-image", false), "valid same-image nutrients do not trigger another paid request");
        check(!FoodSavedPhotoPolicy.skip(completed, "changed-image", false), "changed original is not mistaken for same result");
        JSONObject invalidNutrients = new JSONObject(completed.toString()); invalidNutrients.getJSONObject("nutrients").put("caloriesKcal", -1);
        check(!FoodSavedPhotoPolicy.skip(invalidNutrients, "same-image", false), "negative legacy nutrient is not a valid result");
        invalidNutrients.getJSONObject("nutrients").put("caloriesKcal", "120");
        check(!FoodSavedPhotoPolicy.skip(invalidNutrients, "same-image", false), "string nutrient is not a validated number");
        invalidNutrients.getJSONObject("nutrients").put("caloriesKcal", 120).remove("fiberG");
        check(!FoodSavedPhotoPolicy.skip(invalidNutrients, "same-image", false), "partial legacy nutrition can be completed");
        JSONArray existingJobs = new JSONArray().put(new JSONObject().put("id", "old-item")
                .put("analysisId", "old-generation").put("hash", "same-image").put("epoch", 3));
        check(FoodSavedPhotoPolicy.queued(existingJobs, unfinished, "same-image", 3),
                "unfinished current task is resumed rather than replaced by batch selection");
        check(!FoodSavedPhotoPolicy.queued(existingJobs, unfinished, "same-image", 4), "stale restore epoch is not a current task");
        check(!FoodSavedPhotoPolicy.queued(existingJobs, unfinished, "changed-image", 3), "different bytes are not a queued duplicate");
        check(!FoodSavedPhotoPolicy.queued(existingJobs, new JSONObject(unfinished.toString())
                .put("analysisId", "new-generation"), "same-image", 3), "obsolete request generation cannot prevent a retry");
        check(restoredOriginal.equals(restoredMeal.toString()), "batch policy does not mutate saved text, alpha edits or nutrition");
        JSONObject manual=new JSONObject(completed.toString()).put("userEdited",true).put("dishName","用户最新菜名")
                .put("dishNameHint","旧的提示菜名").put("dishNameUserEdited",true).put("cutoutEditedAt",123)
                .put("displayRotation",270).put("cutoutRect",new JSONArray().put(1).put(2).put(3).put(4));
        String manualBefore=manual.toString();JSONObject local=FoodSavedPhotoPolicy.prepareCutout(manual,"local-only-generation");
        check(manual.getString("analysisId").equals(local.getString("analysisId")),"recut does not invalidate an already authorized nutrition generation");
        check(local.getBoolean("userEdited")&&local.getJSONObject("nutrients").toString().equals(manual.getJSONObject("nutrients").toString()),"recut keeps manual nutrition and its provenance");
        check(local.getString("dishName").equals(manual.getString("dishName"))&&local.getInt("displayRotation")==270,"recut does not alter corrected food names or display rotation");
        check("pending".equals(local.optString("cutoutStatus"))&&"local-only-generation".equals(local.optString("cutoutRequestId")),"local processing uses a separate state and generation");
        JSONObject localJob=new JSONObject().put("analysisId","local-only-generation").put("cutoutBaseEditedAt",123);
        check(FoodSavedPhotoPolicy.cutoutGenerationCurrent(local,localJob),"local result is accepted for its exact generation and starting mask revision");
        check(!FoodSavedPhotoPolicy.cutoutGenerationCurrent(new JSONObject(local.toString()).put("cutoutEditedAt",124),localJob),"a newer manual mask invalidates an earlier local model result");
        check(!FoodSavedPhotoPolicy.cutoutGenerationCurrent(new JSONObject(local.toString()).put("cutoutRequestId","replacement"),localJob),"a newer recut request rejects the older mask result");
        check(!FoodSavedPhotoPolicy.cutoutGenerationCurrent(null,localJob),"deleted item cannot be revived by a local mask");
        JSONObject nutrition=FoodSavedPhotoPolicy.prepareNutrition(manual,"nutrition-only-generation");
        check("nutrition-only-generation".equals(nutrition.optString("analysisId")),"explicit recognition starts a new nutrition generation");
        check(nutrition.getJSONObject("nutrients").toString().equals(manual.getJSONObject("nutrients").toString())&&nutrition.optBoolean("userEdited"),"old valid manual values remain until a replacement validates");
        check(nutrition.getString("cutoutFile").equals(manual.getString("cutoutFile"))&&nutrition.getJSONArray("cutoutRect").toString().equals(manual.getJSONArray("cutoutRect").toString())
                &&nutrition.getBoolean("cutoutEdited")&&nutrition.getInt("displayRotation")==270,"nutrition-only never resets alpha, edited bounds or angle");
        check("用户最新菜名".equals(nutrition.optString("dishNameHint")),"latest manually corrected name wins over an older hint");
        check("用户最新菜名".equals(nutrition.optString("dishName")),"nutrition-only does not rename the food before response");
        check(FoodSavedPhotoPolicy.correctedName(new JSONObject().put("dishName","模型名称")).isEmpty(),"an uncorrected model name is not fabricated into a user hint");
        check(manualBefore.equals(manual.toString()),"reprocessing policies do not mutate caller snapshots");
        JSONObject newest=new JSONObject(manual.toString()).put("cutoutFile","new-alpha.png").put("cutoutStatus","completed")
                .put("cutoutEditedAt",456).put("cutoutRequestId","local-new").put("displayRotation",90);
        JSONObject stale=new JSONObject(manual.toString()).put("dishName","最新手填菜名").put("cutoutObsolete",true);
        stale.getJSONObject("nutrients").put("caloriesKcal",700);
        String newestBefore=newest.toString(),staleBefore=stale.toString();
        JSONObject merged=FoodSavedPhotoPolicy.mergeNutritionEdit(newest,stale,"manual-nutrition-new");
        check("new-alpha.png".equals(merged.optString("cutoutFile"))&&merged.optLong("cutoutEditedAt")==456,"stale nutrition form keeps newly generated alpha revision");
        check("local-new".equals(merged.optString("cutoutRequestId"))&&"completed".equals(merged.optString("cutoutStatus")),"nutrition form preserves independent local generation and status");
        check(merged.optInt("displayRotation")==90&&!merged.has("cutoutObsolete"),"latest rotation wins and stale image-only fields cannot revive");
        check(merged.getJSONObject("nutrients").optInt("caloriesKcal")==700&&"最新手填菜名".equals(merged.optString("dishName")),"new manual nutrition and name are accepted");
        check(merged.optBoolean("userEdited")&&"manual-nutrition-new".equals(merged.optString("analysisId")),"manual nutrition supersedes only the cloud generation");
        check(newestBefore.equals(newest.toString())&&staleBefore.equals(stale.toString()),"atomic edit merge does not mutate input snapshots");
        JSONArray mixed=new JSONArray().put(new JSONObject().put("id",manual.getString("id")).put("analysisId","cloud"))
            .put(new JSONObject().put("id",manual.getString("id")).put("analysisId","local").put("cutoutOnly",true))
            .put(new JSONObject().put("id","other").put("analysisId","other-cloud"));
        JSONArray kept=FoodSavedPhotoPolicy.withoutNutritionJobs(mixed,manual.getString("id"));
        check(kept.length()==2&&"local".equals(kept.getJSONObject(0).optString("analysisId")),"manual edit keeps independently queued cutout work");
        check("other-cloud".equals(kept.getJSONObject(1).optString("analysisId")),"manual edit does not cancel another meal's inference");
        System.out.println("PASS: " + checks + " scheduling and compatibility policy checks");
    }
}
