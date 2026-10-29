package com.eatrecord.app;

import org.json.JSONObject;

/** A provider display name is presentation metadata; the request model ID never changes. */
final class ApiModelPresentation {
    private ApiModelPresentation() { }

    static String metadataName(JSONObject model) {
        Object raw = model == null ? null : model.opt("name");
        if (!(raw instanceof String)) return "";
        StringBuilder clean = new StringBuilder();
        String value = ((String) raw).trim();
        for (int offset = 0, count = 0; offset < value.length() && count < 160; count++) {
            int point = value.codePointAt(offset);
            offset += Character.charCount(point);
            if (!Character.isISOControl(point)) clean.appendCodePoint(point);
        }
        return clean.toString().trim();
    }

    static JSONObject names(JSONObject profile, String endpoint) {
        if (profile == null || endpoint == null
                || !endpoint.trim().equals(profile.optString("apiModelDisplayNamesEndpoint").trim())) return null;
        return profile.optJSONObject("apiModelDisplayNames");
    }

    static String name(String modelId, JSONObject names) {
        if (names == null) return "";
        Object raw = names.opt(modelId);
        return raw instanceof String ? metadataName(newObjectName((String) raw)) : "";
    }

    private static JSONObject newObjectName(String value) {
        JSONObject object = new JSONObject();
        try { object.put("name", value); } catch (Exception ignored) { }
        return object;
    }

    static String selection(String modelId, JSONObject names) {
        String name = name(modelId, names);
        if (!name.isEmpty() && !name.equals(modelId)) return name + "\nAPI ID: " + modelId;
        return "API ID: " + modelId + (name.isEmpty() ? "\n显示名称待刷新模型列表" : "");
    }

    static String summary(JSONObject profile, String endpoint, String modelId) {
        String name = name(modelId, names(profile, endpoint));
        if (!name.isEmpty() && !name.equals(modelId)) return name + "（API: " + modelId + "）";
        return modelId + (name.isEmpty() ? "（API 标识，名称待刷新）" : "");
    }

    static String choice(String modelId, JSONObject names, String imageStatus) {
        return selection(modelId, names) + "\n" + ApiVisionPolicy.description(imageStatus);
    }
}
