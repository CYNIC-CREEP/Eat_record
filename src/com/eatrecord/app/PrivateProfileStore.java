package com.eatrecord.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import org.json.JSONException;
import java.io.IOException;

/** Split persistence only; callers continue using a merged in-memory profile under the existing lock. */
public final class PrivateProfileStore {
    public static final String PRIVATE_PREFS = "eat_record_private";
    private static final String LOCAL = "local_profile_json", READY = "initialized_v1";
    private PrivateProfileStore() { }

    private static Context app(Context context) {
        Context application = context.getApplicationContext();
        return application == null ? context : application;
    }

    public static JSONObject read(Context context) {
        Context c = app(context);
        return read(c.getSharedPreferences(NutritionStore.PREFS, 0), c.getSharedPreferences(PRIVATE_PREFS, 0));
    }

    static JSONObject read(SharedPreferences publicPrefs, SharedPreferences privatePrefs) {
        synchronized (NutritionStore.LOCK) {
            JSONObject oldPublic = NutritionStore.read(publicPrefs, NutritionStore.PROFILE);
            JSONObject local;
            boolean ready = privatePrefs.getBoolean(READY, false);
            if (ready) local = privateJson(privatePrefs);
            else {
                local = ProfilePrivacy.localOnly(oldPublic);
                // The private copy must reach disk first. A failed copy never removes the legacy source.
                ready = privatePrefs.edit().putString(LOCAL, local.toString()).putBoolean(READY, true).commit();
            }
            JSONObject merged = ProfilePrivacy.publicProfile(oldPublic);
            ProfilePrivacy.preserveLocalOnly(merged, local);
            // An initialized private file is authoritative, including absent/deleted keys. Retrying an
            // interrupted public scrub must not import its stale credential over that authority.
            if (ready && ProfilePrivacy.hasLocalOnly(oldPublic)) {
                // commit(false) can still update SharedPreferences' in-memory READY/payload. Flush
                // the authoritative copy again before deleting the only potentially durable source.
                boolean durable = privatePrefs.edit().putString(LOCAL, local.toString()).putBoolean(READY, true).commit();
                if (durable) publicPrefs.edit().putString(NutritionStore.PROFILE,
                        ProfilePrivacy.publicProfile(oldPublic).toString()).commit();
            }
            return merged;
        }
    }

    public static boolean write(Context context, JSONObject profile) {
        Context c = app(context);
        return write(c.getSharedPreferences(NutritionStore.PREFS, 0), c.getSharedPreferences(PRIVATE_PREFS, 0), profile);
    }

    static boolean write(SharedPreferences publicPrefs, SharedPreferences privatePrefs, JSONObject profile) {
        synchronized (NutritionStore.LOCK) {
            JSONObject local = ProfilePrivacy.localOnly(profile), shared = ProfilePrivacy.publicProfile(profile);
            if (!privatePrefs.edit().putString(LOCAL, local.toString()).putBoolean(READY, true).commit()) return false;
            return publicPrefs.edit().putString(NutritionStore.PROFILE, shared.toString()).commit();
        }
    }

    /** Ordinary preference saves retain their asynchronous public write; authority is durable first. */
    public static boolean apply(Context context, JSONObject profile) {
        Context c = app(context);
        synchronized (NutritionStore.LOCK) {
            SharedPreferences privatePrefs = c.getSharedPreferences(PRIVATE_PREFS, 0);
            JSONObject local = ProfilePrivacy.localOnly(profile);
            if (!privatePrefs.edit().putString(LOCAL, local.toString()).putBoolean(READY, true).commit()) return false;
            c.getSharedPreferences(NutritionStore.PREFS, 0).edit()
                    .putString(NutritionStore.PROFILE, ProfilePrivacy.publicProfile(profile).toString()).apply();
            return true;
        }
    }

    private static JSONObject privateJson(SharedPreferences prefs) {
        try { return new JSONObject(prefs.getString(LOCAL, "{}")); }
        catch (JSONException invalid) { throw new IllegalStateException("Local configuration is unavailable"); }
    }

    /** Full backup must stop if either half of an interrupted migration cannot reach disk. */
    public static void prepareForBackup(Context context) throws IOException {
        Context c = app(context);
        prepareForBackup(c.getSharedPreferences(NutritionStore.PREFS, 0), c.getSharedPreferences(PRIVATE_PREFS, 0));
    }

    static void prepareForBackup(SharedPreferences publicPrefs, SharedPreferences privatePrefs) throws IOException {
        synchronized (NutritionStore.LOCK) {
            JSONObject merged = read(publicPrefs, privatePrefs);
            if (!write(publicPrefs, privatePrefs, merged)
                    || ProfilePrivacy.hasLocalOnly(NutritionStore.read(publicPrefs, NutritionStore.PROFILE)))
                throw new IOException("Local configuration could not be excluded from backup");
        }
    }

    /** System restore cannot import an older APK's credentials or upload grants into this install. */
    public static void finishSystemRestore(Context context) throws IOException {
        Context c = app(context);
        finishSystemRestore(c.getSharedPreferences(NutritionStore.PREFS, 0));
    }

    static void finishSystemRestore(SharedPreferences shared) throws IOException {
        synchronized (NutritionStore.LOCK) {
            JSONObject clean = ProfilePrivacy.publicProfile(NutritionStore.read(shared, NutritionStore.PROFILE));
            if (!shared.edit().putString(NutritionStore.PROFILE, clean.toString())
                    .remove(NutritionStore.RESTORE_MARKER).commit())
                throw new IOException("Restored local configuration could not be excluded");
        }
    }
}
