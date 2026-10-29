package com.eatrecord.app;

import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/** Re-evaluate between stages so an open draft does not wait behind a saved-photo batch. */
final class FoodJobPriority {
    private FoodJobPriority() { }

    static int next(JSONArray jobs, Set<String> deferred) {
        return next(jobs,deferred,System.currentTimeMillis());
    }
    static int next(JSONArray jobs, Set<String> deferred,long now) {
        int selected = -1, best = Integer.MAX_VALUE;
        for (int i = 0; i < jobs.length(); i++) {
            JSONObject job = jobs.optJSONObject(i);
            if (job == null || deferred.contains(job.optString("analysisId")) || job.optLong("notBeforeAt")>now) continue;
            // Confirmation changes storage, not how recently the user requested this work.
            int priority = (job.optBoolean("draft") || job.optBoolean("interactive") ? 0 : 2)
                    + (job.optBoolean("cutoutDone") ? 1 : 0);
            if (priority < best) { selected = i; best = priority; }
        }
        return selected;
    }

    /** Earliest eligible job deadline, not a global retry floor shared by fresh interactive work. */
    static long nextDelay(JSONArray jobs,boolean cloudAvailable,long now){
        long earliest=Long.MAX_VALUE;
        for(int i=0;i<jobs.length();i++){
            JSONObject job=jobs.optJSONObject(i);if(job==null)continue;
            if(job.optBoolean("cutoutDone")&&(job.optBoolean("cutoutOnly")||!cloudAvailable))continue;
            earliest=Math.min(earliest,Math.max(0,job.optLong("notBeforeAt")-now));
        }
        return earliest==Long.MAX_VALUE?-1:earliest;
    }
}
