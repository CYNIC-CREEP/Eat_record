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
        profile.put("name", "example-user").put("apiModels", new JSONArray().put(model))
                .put("apiModelMode", "thinking").put("apiModelImageInputs", new JSONObject().put(model, "image"))
                .put("apiModelImageInputsEndpoint", endpoint).put("apiModelDisplayNames", new JSONObject().put(model, "Example model"))
                .put("apiModelDisplayNamesEndpoint", endpoint).put("apiVisionSupported", true)
                .put("cloudUserId", "example-cloud-user").put("foodJsonSchema", true).put("dailyCalorieGoal", 2000);
        ApiCredentialPolicy.Snapshot old = ApiCredentialPolicy.capture(profile);
        check(ApiCredentialPolicy.current(old, profile), "configured request starts current");
        JSONObject disabled = new JSONObject(profile.toString()).put("apiEnabled", false);
        check(ApiCredentialPolicy.sameAuthority(old, disabled), "saved disabled configuration still has a removable authority");
        check(!ApiCredentialPolicy.current(old, disabled), "disabled configuration cannot make requests");
        check(!ApiCredentialPolicy.sameAuthority(old, new JSONObject(profile.toString()).put("apiEndpoint", "https://other.example/chat/completions")), "changed configuration cannot be deleted by an older owner");
        long foodGeneration = profile.optLong("foodRecognitionGeneration");
        ApiCredentialPolicy.remove(profile);
        check(!profile.has("apiKey"), "removal deletes the stored property, rather than a display placeholder");
        for (String key : new String[]{"apiEnabled", "foodAutoAnalyze", "foodPhotoConsent",
                "calorieRecognition", "aiAnalysisEnabled", "aiAutoAnalysis", "aiVisionEnabled",
                "aiRecordAccess", "aiChatMemory"}) check(!profile.optBoolean(key), "API-dependent grant revoked: " + key);
        check(!ApiCredentialPolicy.current(old, profile), "late result cannot revive deletion");
        check(profile.optLong("foodRecognitionGeneration") != foodGeneration, "food job generation revoked");
        for (String key : new String[]{"apiKey", "apiEndpoint", "apiModel", "apiModelMode", "apiModels",
                "apiModelImageInputs", "apiModelImageInputsEndpoint", "apiModelDisplayNames",
                "apiModelDisplayNamesEndpoint", "apiVisionSupported"}) check(!profile.has(key), "associated API configuration removed: " + key);
        check("example-user".equals(profile.optString("name")) && "example-cloud-user".equals(profile.optString("cloudUserId"))
                && profile.optBoolean("foodJsonSchema") && profile.optInt("dailyCalorieGoal") == 2000, "unrelated cloud, appearance, goal and output preferences retained");
        JSONObject persisted = new JSONObject(profile.toString());
        check(!persisted.has("apiKey") && !ApiCredentialPolicy.current(old, persisted), "serialized reload remains revoked");
        persisted.put("apiKey", "example-key-for-local-tests-only").put("apiEndpoint", endpoint).put("apiModel", model).put("apiEnabled", true);
        ApiCredentialPolicy.changed(persisted);
        check(!ApiCredentialPolicy.current(old, persisted), "re-adding the same key does not authorize an older request");
        ApiCredentialPolicy.Snapshot replacement = ApiCredentialPolicy.capture(persisted);
        check(ApiCredentialPolicy.current(replacement, persisted), "new request uses new authority");
        persisted.put("apiModel", "another-example-model");
        check(!ApiCredentialPolicy.current(replacement, persisted), "model mismatch rejects stale response even without a generation write");
        check(!ApiCredentialPolicy.current(null, persisted) && !ApiCredentialPolicy.current(old, null), "missing authority rejected");
        check(!ApiCredentialPolicy.sameAuthority(null, persisted) && !ApiCredentialPolicy.sameAuthority(old, null), "missing deletion owner rejected");
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
