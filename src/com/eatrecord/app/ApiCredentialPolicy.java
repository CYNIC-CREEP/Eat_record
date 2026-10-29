package com.eatrecord.app;

import org.json.JSONException;
import org.json.JSONObject;

/** Local API authority. Removing a key revokes pending work without removing saved meals. */
public final class ApiCredentialPolicy {
    private ApiCredentialPolicy() { }

    public static long generation(JSONObject profile) {
        return profile == null ? 0 : Math.max(0, profile.optLong("apiCredentialGeneration"));
    }

    private static long next(long value) {
        return value == Long.MAX_VALUE ? 1 : Math.max(0, value) + 1;
    }

    /** Call while committing any new credential/model authority under the profile lock. */
    public static void changed(JSONObject profile) throws JSONException {
        profile.put("apiCredentialGeneration", next(generation(profile)));
        profile.put("foodRecognitionGeneration", next(profile.optLong("foodRecognitionGeneration")));
    }

    public static void remove(JSONObject profile) throws JSONException {
        profile.remove("apiKey");
        for (String key : new String[]{"apiEnabled", "foodAutoAnalyze", "foodPhotoConsent",
                "calorieRecognition", "aiAnalysisEnabled", "aiAutoAnalysis", "aiVisionEnabled",
                "aiRecordAccess", "aiChatMemory"}) profile.put(key, false);
        changed(profile);
    }

    private static boolean configured(JSONObject profile) {
        return profile != null && profile.optBoolean("apiEnabled")
                && !profile.optString("apiKey").trim().isEmpty();
    }

    public static final class Snapshot {
        private final long generation;
        private final String key, endpoint, model;
        private Snapshot(JSONObject profile) {
            generation = generation(profile);
            key = profile == null ? "" : profile.optString("apiKey");
            endpoint = profile == null ? "" : profile.optString("apiEndpoint");
            model = profile == null ? "" : profile.optString("apiModel");
        }
        /** Deliberately has no credential-bearing toString or public key accessor. */
        public boolean matchesRequest(String key, String endpoint, String model) {
            return this.key.equals(key) && this.endpoint.equals(endpoint) && this.model.equals(model);
        }
    }

    public static Snapshot capture(JSONObject profile) { return new Snapshot(profile); }

    public static boolean current(Snapshot started, JSONObject profile) {
        return started != null && configured(profile) && started.generation == generation(profile)
                && started.matchesRequest(profile.optString("apiKey"), profile.optString("apiEndpoint"),
                        profile.optString("apiModel"));
    }
}
