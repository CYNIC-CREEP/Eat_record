package com.eatrecord.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** The UI and JobService share one process and serialize every days_json write. */
public final class NutritionStore {
    public static final Object LOCK = new Object();
    public static final String PREFS = "eat_record_prefs", DAYS = "days_json", PROFILE = "profile_json";
    public static final String RESTORE_MARKER = "pending_backup_restore";
    public static final String[] MEALS = {"breakfast", "lunch", "afternoon_tea", "dinner", "snack"};
    public static JSONObject read(SharedPreferences prefs, String key) {
        try { return new JSONObject(prefs.getString(key, "{}")); }
        catch (Exception e) { return new JSONObject(); }
    }
    public static JSONObject meal(JSONObject days, String date, String key) {
        JSONObject day = days.optJSONObject(date);
        return day == null ? null : day.optJSONObject(key);
    }
    public static boolean containsPhoto(JSONObject meal, String file) {
        return contains(meal == null ? null : meal.optJSONArray("photos"), file)
                || contains(meal == null ? null : meal.optJSONArray("stickers"), file);
    }
    public static boolean contains(JSONArray a, String file) {
        if (a != null) for (int i = 0; i < a.length(); i++) if (file.equals(a.optString(i))) return true;
        return false;
    }
    public static List<String> media(JSONObject meal) {
        List<String> out = new ArrayList<String>();
        JSONArray a = meal == null ? null : meal.optJSONArray("foodItems");
        if (a != null) for (int i = 0; i < a.length(); i++) {
            JSONObject item = a.optJSONObject(i);
            if (item == null) continue;
            for (String k : new String[]{"sourcePhoto", "cutoutFile"}) {
                String name = item.optString(k, "");
                if (safeName(name) && !out.contains(name)) out.add(name);
            }
        }
        return out;
    }
    public static boolean safeName(String name) {
        return name != null && name.length() > 0 && !name.contains("..") && !name.contains("/") && !name.contains("\\");
    }
    public static JSONObject totals(JSONObject day) {
        JSONObject total = new JSONObject();
        double[] sums = new double[6]; int count = 0;
        if (day != null) for (String key : MEALS) {
            JSONObject meal = day.optJSONObject(key);
            if (meal == null || meal.optBoolean("skipped")) continue;
            JSONArray items = meal.optJSONArray("foodItems");
            if (items == null) continue;
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null || !"completed".equals(item.optString("status"))) continue;
                JSONObject n = item.optJSONObject("nutrients");
                if (n == null) continue;
                count++;
                for (int j = 0; j < sums.length; j++) sums[j] += Math.max(0, n.optDouble(FoodAnalysisResult.LOCAL[j], 0));
            }
        }
        try {
            for (int j = 0; j < sums.length; j++) total.put(FoodAnalysisResult.LOCAL[j], Math.round(sums[j] * 10) / 10.0);
            total.put("count", count);
        } catch (JSONException ignored) { }
        return total;
    }
    /** Preserve fresh worker results when an older UI snapshot is saved. Photos remain UI-owned. */
    public static void mergeLatest(JSONObject target, JSONObject latest) {
        JSONArray dates = target.names();
        if (dates == null) return;
        try {
            for (int d = 0; d < dates.length(); d++) for (String key : MEALS) {
                String date = dates.optString(d);
                JSONObject to = meal(target, date, key), from = meal(latest, date, key);
                if (to == null || from == null || !from.has("foodItems")) continue;
                JSONArray items = from.optJSONArray("foodItems"), keep = new JSONArray();
                if (items != null) for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.optJSONObject(i);
                    if (item != null && containsPhoto(to, item.optString("sourcePhoto"))) keep.put(item);
                }
                to.put("foodItems", keep);
            }
        } catch (JSONException ignored) { }
    }
    public static JSONObject find(JSONObject meal, String id) {
        JSONArray a = meal == null ? null : meal.optJSONArray("foodItems");
        if (a != null) for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && id.equals(o.optString("id"))) return o;
        }
        return null;
    }
    /** Read-only lookup: a food's original and alpha image represent the same diary media. */
    public static JSONObject findByMedia(JSONObject meal, String file) {
        if (!safeName(file)) return null;
        JSONArray items = meal == null ? null : meal.optJSONArray("foodItems");
        for (int i = 0; items != null && i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item != null && (file.equals(item.optString("sourcePhoto"))
                    || file.equals(item.optString("cutoutFile")))) return item;
        }
        return null;
    }
    /** Invalid/non-quarter-turn legacy metadata is displayed upright, never as an arbitrary tilt. */
    public static int normalizeDisplayRotation(int degrees) {
        int normalized = ((degrees % 360) + 360) % 360;
        return normalized % 90 == 0 ? normalized : 0;
    }
    public static int displayRotation(JSONObject item) {
        return item == null ? 0 : normalizeDisplayRotation(item.optInt("displayRotation", 0));
    }
    public static int leftDisplayRotation(int degrees) {
        return normalizeDisplayRotation(normalizeDisplayRotation(degrees) - 90);
    }
    /**
     * Rotate only presentation metadata on the freshest saved item. The source pixels, alpha mask,
     * source-aligned cutoutRect and inference identity stay unchanged so paint editing and an
     * in-flight nutrition request remain valid. A deleted item is never reconstructed here.
     */
    public static boolean rotateDisplayLeft(Context context, String date, String key, String id) {
        if (context == null || id == null || id.isEmpty()) return false;
        synchronized (LOCK) {
            SharedPreferences prefs = context.getSharedPreferences(PREFS, 0);
            JSONObject days = read(prefs, DAYS), item = find(meal(days, date, key), id);
            if (item == null) return false;
            try {
                item.put("displayRotation", leftDisplayRotation(displayRotation(item)));
                return prefs.edit().putString(DAYS, days.toString()).commit();
            } catch (JSONException ignored) { return false; }
        }
    }
    public static JSONArray forSource(JSONObject meal, String file) {
        JSONArray result=new JSONArray(), items=meal==null?null:meal.optJSONArray("foodItems");
        for(int i=0;items!=null&&i<items.length();i++){
            JSONObject item=items.optJSONObject(i);
            if(item!=null && file.equals(item.optString("sourcePhoto"))) {
                try {result.put(new JSONObject(item.toString()));}catch(JSONException ignored){}
            }
        }
        return result;
    }
    public static void restoreBundle(Context c,String date,String key,JSONArray bundle) {
        if(bundle==null)return;
        synchronized(LOCK){
            SharedPreferences p=c.getSharedPreferences(PREFS,0);JSONObject days=read(p,DAYS), meal=meal(days,date,key);
            if(meal==null)return;
            JSONArray items=meal.optJSONArray("foodItems");if(items==null)items=new JSONArray();
            try{
                for(int i=0;i<bundle.length();i++){
                    JSONObject item=new JSONObject(bundle.getJSONObject(i).toString());
                    if(containsPhoto(meal,item.optString("sourcePhoto"))&&find(meal,item.optString("id"))==null){
                        item.put("analysisId",java.util.UUID.randomUUID().toString());
                        item.put("analysisStatus",item.has("nutrients")?"completed":"needs_retry");items.put(item);
                    }
                }
                meal.put("foodItems",items);p.edit().putString(DAYS,days.toString()).commit();
            }catch(JSONException ignored){}
        }
    }
    public static boolean replace(Context c, String date, String key, String id, JSONObject replacement) {
        synchronized (LOCK) {
            SharedPreferences p = c.getSharedPreferences(PREFS, 0);
            JSONObject days = read(p, DAYS), meal = meal(days, date, key);
            JSONArray a = meal == null ? null : meal.optJSONArray("foodItems");
            if (a == null) return false;
            JSONArray next = new JSONArray(); boolean found = false;
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o != null && id.equals(o.optString("id"))) {
                    found = true;
                    if (replacement != null) next.put(replacement);
                } else next.put(a.opt(i));
            }
            try { meal.put("foodItems", next); } catch (JSONException e) { return false; }
            return found && p.edit().putString(DAYS, days.toString()).commit();
        }
    }
    public static String summary(JSONObject day) {
        StringBuilder text = new StringBuilder();
        if (day != null) for (String key : MEALS) {
            JSONObject meal = day.optJSONObject(key);
            JSONArray a = meal == null || meal.optBoolean("skipped") ? null : meal.optJSONArray("foodItems");
            if (a == null) continue;
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null || !"completed".equals(o.optString("status"))) continue;
                text.append(key).append(':').append(o.optString("dishName")).append(' ')
                    .append(o.optInt("estimatedGrams")).append("g ").append(o.optJSONObject("nutrients"))
                    .append(o.optBoolean("userEdited") ? "（用户修正）" : "（AI估算）").append('；');
            }
        }
        return text.toString();
    }
}
