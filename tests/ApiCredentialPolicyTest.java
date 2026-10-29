package com.eatrecord.app;

import org.json.JSONArray;
import org.json.JSONObject;

public final class ApiCredentialPolicyTest {
    private static int checks;
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
    private static JSONObject configured() throws Exception {
        return new JSONObject().put("apiKey", "example-key-for-local-tests-only")
                .put("apiEndpoint", "https://model.example/v1/chat/completions")
                .put("apiModel", "food-model-example").put("apiEnabled", true)
                .put("foodAutoAnalyze", true).put("foodPhotoConsent", true)
                .put("aiAnalysisEnabled", true).put("aiAutoAnalysis", true)
                .put("aiRecordAccess", true).put("aiChatMemory", true);
    }
    public static void main(String[] args) throws Exception {
        JSONObject profile = configured();
        String endpoint = profile.getString("apiEndpoint"), model = profile.getString("apiModel");
        profile.put("name", "example-user").put("apiModels", new JSONArray().put(model));
        ApiCredentialPolicy.Snapshot old = ApiCredentialPolicy.capture(profile);
        check(ApiCredentialPolicy.current(old, profile), "configured request starts current");
        long foodGeneration = profile.optLong("foodRecognitionGeneration");
        ApiCredentialPolicy.remove(profile);
        check(!profile.has("apiKey"), "removal deletes the stored property, rather than a display placeholder");
        for (String key : new String[]{"apiEnabled", "foodAutoAnalyze", "foodPhotoConsent",
                "calorieRecognition", "aiAnalysisEnabled", "aiAutoAnalysis", "aiVisionEnabled",
                "aiRecordAccess", "aiChatMemory"}) check(!profile.optBoolean(key), "API-dependent grant revoked: " + key);
        check(!ApiCredentialPolicy.current(old, profile), "late result cannot revive deletion");
        check(profile.optLong("foodRecognitionGeneration") != foodGeneration, "food job generation revoked");
        check(endpoint.equals(profile.optString("apiEndpoint")) && model.equals(profile.optString("apiModel")), "non-secret endpoint and model retained");
        check("example-user".equals(profile.optString("name")) && profile.getJSONArray("apiModels").length() == 1, "unrelated settings retained");
        JSONObject persisted = new JSONObject(profile.toString());
        check(!persisted.has("apiKey") && !ApiCredentialPolicy.current(old, persisted), "serialized reload remains revoked");
        persisted.put("apiKey", "example-key-for-local-tests-only").put("apiEnabled", true);
        ApiCredentialPolicy.changed(persisted);
        check(!ApiCredentialPolicy.current(old, persisted), "re-adding the same key does not authorize an older request");
        ApiCredentialPolicy.Snapshot replacement = ApiCredentialPolicy.capture(persisted);
        check(ApiCredentialPolicy.current(replacement, persisted), "new request uses new authority");
        persisted.put("apiModel", "another-example-model");
        check(!ApiCredentialPolicy.current(replacement, persisted), "model mismatch rejects stale response even without a generation write");
        check(!ApiCredentialPolicy.current(null, persisted) && !ApiCredentialPolicy.current(old, null), "missing authority rejected");
        JSONObject wrap = configured().put("apiCredentialGeneration", Long.MAX_VALUE)
                .put("foodRecognitionGeneration", Long.MAX_VALUE);
        ApiCredentialPolicy.Snapshot maximum = ApiCredentialPolicy.capture(wrap);
        ApiCredentialPolicy.changed(wrap);
        check(ApiCredentialPolicy.generation(wrap) == 1 && wrap.optLong("foodRecognitionGeneration") == 1, "generation rollover stays positive");
        check(!ApiCredentialPolicy.current(maximum, wrap), "rollover revokes prior authority");
        ApiCredentialPolicy.remove(wrap);ApiCredentialPolicy.remove(wrap);
        check(!wrap.has("apiKey") && !wrap.optBoolean("apiEnabled"), "repeat removal stays revoked");
        System.out.println("PASS: " + checks + " API credential deletion and stale-authority checks");
    }
}
