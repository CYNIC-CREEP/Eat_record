package com.eatrecord.app;

import org.json.JSONObject;

/** The pen edits the existing meal text; it creates no image, analysis task or measured nutrients. */
public final class ManualMealText {
    public static final int MAX_LENGTH = 2000;
    private ManualMealText() { }

    public static String normalize(String value) {
        String name = clean(value);
        return name == null || name.isEmpty() ? null : name;
    }

    /** Inline editors may clear existing text without removing photos or nutrition. */
    public static String clean(String value) {
        if (value == null) return null;
        String name = value.replace("\r\n", "\n").replace('\r', '\n')
                .replaceAll("[\\p{Cc}&&[^\\n\\t]]", "").replace('\t', ' ');
        // Android uses ICU regex, not the desktop JDK's (?U) flag. Trim by Unicode code point.
        int start=0,end=name.length();
        while(start<end){int cp=name.codePointAt(start);if(!Character.isWhitespace(cp)&&!Character.isSpaceChar(cp))break;start+=Character.charCount(cp);}
        while(end>start){int cp=name.codePointBefore(end);if(!Character.isWhitespace(cp)&&!Character.isSpaceChar(cp))break;end-=Character.charCount(cp);}
        name=name.substring(start,end);
        if (name.codePointCount(0, name.length()) > MAX_LENGTH) return null;
        return name;
    }

    public static JSONObject updatedInlineMeal(JSONObject current, String value) {
        String text = clean(value);
        if (text == null) return null;
        try {
            JSONObject meal = current == null ? new JSONObject() : new JSONObject(current.toString());
            if (text.isEmpty()) { meal.remove("text"); meal.remove("manualTextEntry"); }
            else { meal.put("text", text).put("manualTextEntry", true).put("skipped", false); }
            return meal;
        } catch (Exception ignored) { return null; }
    }

    /** Return a deep copy; cancellation/validation cannot mutate the displayed or saved record. */
    public static JSONObject updatedMeal(JSONObject current, String value) {
        String text = normalize(value);
        if (text == null) return null;
        try {
            JSONObject meal = current == null ? new JSONObject() : new JSONObject(current.toString());
            meal.put("text", text);
            meal.put("manualTextEntry", true);
            meal.put("skipped", false);
            return meal;
        } catch (Exception ignored) { return null; }
    }
}
