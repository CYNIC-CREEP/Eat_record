package com.eatrecord.app;

import org.json.JSONObject;

/** Shared appearance policy. An explicit in-app choice wins over historical theme flags. */
public final class AppearanceMode {
    private AppearanceMode() { }

    public static boolean isNight(JSONObject profile, boolean systemNight) {
        if (profile == null) return false;
        Object explicit = profile.opt("nightMode");
        if (explicit instanceof Boolean) return (Boolean) explicit;
        // Preserve the existing appearance on upgrade until the user changes the switch.
        return "night".equals(profile.optString("decoration"))
                || (profile.optBoolean("followSystemNight", false) && systemNight);
    }
}
