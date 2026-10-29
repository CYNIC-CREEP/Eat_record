package com.eatrecord.app;

import org.json.JSONException;
import org.json.JSONObject;
import java.util.Iterator;

/** Credentials and upload/session authority belong to this installation, never to a backup. */
public final class ProfilePrivacy {
    private ProfilePrivacy() { }

    public static boolean isLocalOnly(String key) {
        if (key == null) return false;
        if (key.startsWith("api") || key.startsWith("cloud") || key.startsWith("lastCloud")) return true;
        for (String name : new String[]{"userId", "userIdSource", "autoCloudSync", "lastAutoCloudSyncAt",
                "foodRecognitionSource", "foodRecognitionGeneration", "foodRecognitionOnboardingSeen",
                "foodOfflineAnalyze", "foodAutoAnalyze", "foodPhotoConsent", "foodJsonSchema",
                "calorieRecognition", "aiAnalysisEnabled", "aiAutoAnalysis", "aiVisionEnabled",
                "aiRecordAccess", "aiChatMemory"}) if (name.equals(key)) return true;
        return false;
    }

    public static JSONObject publicProfile(JSONObject source) {
        return partition(source, false);
    }

    public static JSONObject portable(JSONObject source) { return publicProfile(source); }

    public static JSONObject localOnly(JSONObject source) { return partition(source, true); }

    private static JSONObject partition(JSONObject source, boolean local) {
        JSONObject result = new JSONObject();
        if (source == null) return result;
        try {
            // A JSON round trip detaches nested objects so snapshots cannot mutate the live profile.
            JSONObject copy = new JSONObject(source.toString());
            Iterator<String> keys = copy.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (isLocalOnly(key) == local) result.put(key, copy.get(key));
            }
        } catch (JSONException invalid) { throw new IllegalStateException("Invalid profile snapshot"); }
        return result;
    }

    /** Replace, rather than merely overlay: deleted local keys cannot reappear from an old backup. */
    public static void preserveLocalOnly(JSONObject target, JSONObject local) {
        if (target == null) throw new IllegalArgumentException("Missing restored profile");
        java.util.ArrayList<String> remove = new java.util.ArrayList<String>();
        for (Iterator<String> keys = target.keys(); keys.hasNext();) {
            String key = keys.next(); if (isLocalOnly(key)) remove.add(key);
        }
        for (String key : remove) target.remove(key);
        JSONObject saved = localOnly(local);
        try {
            for (Iterator<String> keys = saved.keys(); keys.hasNext();) {
                String key = keys.next(); target.put(key, saved.get(key));
            }
        } catch (JSONException invalid) { throw new IllegalStateException("Invalid local profile"); }
    }

    public static boolean hasLocalOnly(JSONObject source) {
        if (source != null) for (Iterator<String> keys = source.keys(); keys.hasNext();)
            if (isLocalOnly(keys.next())) return true;
        return false;
    }
}
