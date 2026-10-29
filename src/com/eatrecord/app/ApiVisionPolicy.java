package com.eatrecord.app;

import java.net.URI;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Provider-declared input metadata, never a model-name allowlist or a real vision test. */
final class ApiVisionPolicy {
    static final String UNKNOWN = "unknown";
    static final String IMAGE = "image";
    static final String NO_IMAGE = "no_image";

    private ApiVisionPolicy() {}

    static boolean isChatEndpoint(String endpoint) {
        try {
            URI uri = new URI(endpoint == null ? "" : endpoint.trim());
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    && uri.getUserInfo() == null && uri.getFragment() == null
                    && uri.getPath() != null && uri.getPath().endsWith("/chat/completions");
        } catch (Exception ignored) { return false; }
    }

    static String fromMetadata(JSONObject item) {
        if (item == null) return UNKNOWN;
        JSONArray inputs = item.optJSONArray("input_modalities");
        JSONObject architecture = item.optJSONObject("architecture");
        if (inputs == null && architecture != null) inputs = architecture.optJSONArray("input_modalities");
        if (inputs == null || inputs.length() == 0) return UNKNOWN;
        boolean declaredInput = false;
        for (int i = 0; i < inputs.length(); i++) {
            String value = inputs.optString(i, "").trim().toLowerCase(Locale.US);
            if ("image".equals(value)) return IMAGE;
            if ("text".equals(value) || "audio".equals(value) || "video".equals(value)) declaredInput = true;
        }
        return declaredInput ? NO_IMAGE : UNKNOWN;
    }

    static String status(JSONObject profile, String endpoint, String model) {
        if (profile == null || endpoint == null || model == null
                || !endpoint.trim().equals(profile.optString("apiModelImageInputsEndpoint", "").trim())) return UNKNOWN;
        JSONObject inputs = profile.optJSONObject("apiModelImageInputs");
        String value = inputs == null ? UNKNOWN : inputs.optString(model, UNKNOWN);
        return IMAGE.equals(value) || NO_IMAGE.equals(value) ? value : UNKNOWN;
    }

    static boolean mayAttempt(String status) { return !NO_IMAGE.equals(status); }

    static String label(String model, String status) {
        return model + " · " + description(status);
    }

    static String description(String status) {
        if (IMAGE.equals(status)) return "支持图片输入（服务商声明，未实测）";
        if (NO_IMAGE.equals(status)) return "仅声明非图片输入";
        return "图片输入待验证";
    }
}
