package com.eatrecord.app;

import org.json.JSONArray;
import org.json.JSONObject;

public final class ApiVisionPolicyTest {
    private static int checks;
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
    public static void main(String[] args) throws Exception {
        String endpoint = "https://model.example/v1/chat/completions";
        check(ApiVisionPolicy.isChatEndpoint(endpoint), "explicit HTTPS Chat Completions supported");
        check(!ApiVisionPolicy.isChatEndpoint("http://model.example/v1/chat/completions"), "no cleartext secret probe");
        check(!ApiVisionPolicy.isChatEndpoint("https://user:secret@model.example/v1/chat/completions"), "userinfo rejected");
        check(!ApiVisionPolicy.isChatEndpoint("https://model.example/v1/responses"), "Responses payload cannot masquerade as Chat Completions");
        check(!ApiVisionPolicy.isChatEndpoint(""), "missing endpoint cannot trigger key broadcast");
        JSONObject bare = new JSONObject().put("id", "my-food-model");
        check(ApiVisionPolicy.UNKNOWN.equals(ApiVisionPolicy.fromMetadata(bare)), "name only is unknown");
        JSONObject image = new JSONObject().put("id", "custom-deployment-71").put("architecture",
                new JSONObject().put("input_modalities", new JSONArray().put("text").put("image")));
        check(ApiVisionPolicy.IMAGE.equals(ApiVisionPolicy.fromMetadata(image)), "custom alias uses provider input metadata");
        JSONObject text = new JSONObject().put("id", "misleading-vision-name").put("input_modalities", new JSONArray().put("text"));
        check(ApiVisionPolicy.NO_IMAGE.equals(ApiVisionPolicy.fromMetadata(text)), "input metadata beats a vision-looking name");
        JSONObject outputOnly = new JSONObject().put("output_modalities", new JSONArray().put("image"));
        check(ApiVisionPolicy.UNKNOWN.equals(ApiVisionPolicy.fromMetadata(outputOnly)), "image output is not image input evidence");
        check(ApiVisionPolicy.UNKNOWN.equals(ApiVisionPolicy.fromMetadata(new JSONObject().put("input_modalities", new JSONArray()))), "empty metadata unknown");
        check(ApiVisionPolicy.UNKNOWN.equals(ApiVisionPolicy.fromMetadata(new JSONObject().put("input_modalities", new JSONArray().put("future-modality")))), "unrecognized metadata unknown");
        JSONObject profile = new JSONObject().put("apiVisionSupported", false);
        check(ApiVisionPolicy.UNKNOWN.equals(ApiVisionPolicy.status(profile, endpoint, "my-food-model")), "old false heuristic is not negative evidence");
        check(ApiVisionPolicy.mayAttempt(ApiVisionPolicy.UNKNOWN), "unknown can try under existing consent");
        check(ApiVisionPolicy.mayAttempt(ApiVisionPolicy.IMAGE), "provider-declared image can try");
        check(!ApiVisionPolicy.mayAttempt(ApiVisionPolicy.NO_IMAGE), "explicit no-image metadata is not treated as supported");
        profile.put("apiModelImageInputsEndpoint", endpoint).put("apiModelImageInputs", new JSONObject().put("custom-deployment-71", ApiVisionPolicy.IMAGE));
        check(ApiVisionPolicy.IMAGE.equals(ApiVisionPolicy.status(profile, endpoint, "custom-deployment-71")), "matching provider/model retains metadata");
        check(ApiVisionPolicy.UNKNOWN.equals(ApiVisionPolicy.status(profile, "https://other.example/v1/chat/completions", "custom-deployment-71")), "other endpoint cannot reuse capability");
        check(ApiVisionPolicy.UNKNOWN.equals(ApiVisionPolicy.status(profile, endpoint, "new-deployment")), "other model cannot reuse capability");
        check(ApiVisionPolicy.label("vision-alias", ApiVisionPolicy.UNKNOWN).contains("待验证"), "unknown never claims not supported or tested support");
        check(ApiVisionPolicy.label("custom", ApiVisionPolicy.IMAGE).contains("服务商声明"), "provider claim is not an image probe");
        System.out.println("PASS: " + checks + " API vision policy checks");
    }
}
