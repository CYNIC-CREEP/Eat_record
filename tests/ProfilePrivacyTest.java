package com.eatrecord.app;

import android.content.SharedPreferences;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Synthetic placeholders only. These checks never read an installed app's credentials. */
public final class ProfilePrivacyTest {
    private static int checks;
    private static void check(boolean pass, String message) {
        checks++; if (!pass) throw new AssertionError(message);
    }
    private static JSONObject legacy() throws Exception {
        return new JSONObject().put("name", "record owner").put("nightMode", true)
                .put("recycleBin", new JSONArray().put(new JSONObject().put("date", "2026-10-01")))
                .put("aiHeightCm", 170).put("apiKey", "synthetic-placeholder")
                .put("apiEndpoint", "https://example.invalid/chat/completions")
                .put("apiModel", "qa-model").put("apiModels", new JSONArray().put("qa-model"))
                .put("apiEnabled", true).put("apiCredentialGeneration", 19)
                .put("foodAutoAnalyze", true).put("foodPhotoConsent", true)
                .put("foodRecognitionGeneration", 27).put("foodRecognitionSource", "api")
                .put("aiAnalysisEnabled", true).put("aiRecordAccess", true).put("aiChatMemory", true)
                .put("cloudToken", "synthetic-session").put("userId", "synthetic-user")
                .put("userIdSource", "cloud").put("autoCloudSync", true);
    }
    public static void main(String[] args) throws Exception {
        JSONObject source = legacy(), publicPart = ProfilePrivacy.publicProfile(source);
        for (String key : new String[]{"apiKey", "apiEndpoint", "apiModel", "apiModels", "apiEnabled",
                "apiCredentialGeneration", "foodAutoAnalyze", "foodPhotoConsent", "foodRecognitionGeneration",
                "foodRecognitionSource", "aiAnalysisEnabled", "aiRecordAccess", "aiChatMemory", "cloudToken",
                "userId", "userIdSource", "autoCloudSync"}) check(!publicPart.has(key), "Export contains local field " + key);
        check(publicPart.optString("name").equals("record owner"), "Export lost ordinary name");
        check(publicPart.optInt("aiHeightCm") == 170 && publicPart.optBoolean("nightMode"), "Export lost record settings");
        check(publicPart.getJSONArray("recycleBin").length() == 1, "Export lost recycled record data");
        publicPart.getJSONArray("recycleBin").getJSONObject(0).put("date", "changed");
        check(!source.getJSONArray("recycleBin").getJSONObject(0).getString("date").equals("changed"), "Export snapshot aliases live data");
        JSONObject localPart = ProfilePrivacy.localOnly(source);
        check(!localPart.has("recycleBin") && !localPart.has("name"), "Local partition swallowed diary preferences");
        check(localPart.getLong("apiCredentialGeneration") == 19 && localPart.getLong("foodRecognitionGeneration") == 27, "Generations were lost");
        JSONObject restored = legacy().put("apiFutureCapability", "incoming").put("cloudFutureSession", "incoming").put("name", "restored name");
        JSONObject deletedLocal = ProfilePrivacy.localOnly(source);
        ApiCredentialPolicy.remove(deletedLocal); deletedLocal.remove("cloudToken");
        ProfilePrivacy.preserveLocalOnly(restored, deletedLocal);
        check(!restored.has("apiKey") && !restored.has("cloudToken"), "Old archive resurrected a deleted credential");
        check(!restored.has("apiFutureCapability") && !restored.has("cloudFutureSession"), "Imported future local fields escaped sanitization");
        check(restored.optString("name").equals("restored name"), "Restore did not retain ordinary incoming preferences");
        check(!restored.optBoolean("foodPhotoConsent") && !restored.optBoolean("aiRecordAccess"), "Restore imported upload grants");
        check(restored.optLong("apiCredentialGeneration") == 20, "Restore replaced the local authority generation");

        FakePrefs shared = new FakePrefs(), secrets = new FakePrefs();
        shared.values.put(NutritionStore.PROFILE, source.toString());
        secrets.failCommits = 1;
        JSONObject failedMigration = PrivateProfileStore.read(shared, secrets);
        check(failedMigration.optString("apiKey").equals("synthetic-placeholder"), "Failed migration lost the usable old local configuration");
        check(NutritionStore.read(shared, NutritionStore.PROFILE).has("apiKey"), "Failed private commit removed the only old copy");
        check(!secrets.getBoolean("initialized_v1", false), "Failed private commit marked migration complete");
        JSONObject migrated = PrivateProfileStore.read(shared, secrets);
        check(migrated.optString("apiKey").equals("synthetic-placeholder"), "Retry did not preserve credentials");
        check(!ProfilePrivacy.hasLocalOnly(NutritionStore.read(shared, NutritionStore.PROFILE)), "Migration left sensitive public fields");
        check(secrets.getBoolean("initialized_v1", false), "Durable private copy was not initialized");
        check(PrivateProfileStore.read(shared, secrets).optBoolean("foodPhotoConsent"), "Reading split profile lost a local grant");

        // Death after private commit but before public scrub must not revive stale fields.
        shared.values.put(NutritionStore.PROFILE, source.toString());
        JSONObject removed = PrivateProfileStore.read(shared, secrets);
        ApiCredentialPolicy.remove(removed);
        shared.values.put(NutritionStore.PROFILE, source.toString()); shared.failCommits = 1;
        check(!PrivateProfileStore.write(shared, secrets, removed), "Injected public scrub failure was hidden");
        check(NutritionStore.read(shared, NutritionStore.PROFILE).has("apiKey"), "Failure fixture did not retain old public data");
        JSONObject recovered = PrivateProfileStore.read(shared, secrets);
        check(!recovered.has("apiKey") && !recovered.optBoolean("apiEnabled"), "Interrupted migration resurrected deleted API credentials");
        check(!ProfilePrivacy.hasLocalOnly(NutritionStore.read(shared, NutritionStore.PROFILE)), "Recovery did not finish public scrub");
        check(recovered.optLong("apiCredentialGeneration") == 20, "Recovery lost revocation generation");
        shared.values.put(NutritionStore.PROFILE, legacy().put("apiKey", "stale-placeholder").toString());
        check(!PrivateProfileStore.read(shared, secrets).has("apiKey"), "Initialized private authority accepted injected legacy key");

        FakePrefs failShared = new FakePrefs(), failSecrets = new FakePrefs();
        failShared.values.put(NutritionStore.PROFILE, source.toString()); failSecrets.failCommits = 5;
        boolean stopped = false;
        try { PrivateProfileStore.prepareForBackup(failShared, failSecrets); } catch (IOException expected) { stopped = true; }
        check(stopped, "Full backup did not fail closed on private persistence failure");
        check(NutritionStore.read(failShared, NutritionStore.PROFILE).has("apiKey"), "Barrier destroyed source on copy failure");
        PrivateProfileStore.prepareForBackup(shared, secrets);
        check(!ProfilePrivacy.hasLocalOnly(NutritionStore.read(shared, NutritionStore.PROFILE)), "Successful backup barrier left public credentials");
        check(!PrivateProfileStore.read(shared, secrets).has("apiKey"), "Backup barrier revived revoked credentials");
        FakePrefs androidShared = new FakePrefs(), androidSecrets = new FakePrefs();
        androidShared.values.put(NutritionStore.PROFILE, source.toString());
        androidSecrets.failCommits = 2; androidSecrets.memoryOnFailedCommit = true;
        PrivateProfileStore.read(androidShared, androidSecrets);
        check(androidSecrets.getBoolean("initialized_v1", false), "Android-style failure did not publish READY to memory");
        check(androidSecrets.durable.isEmpty(), "Failure unexpectedly wrote the private copy to disk");
        PrivateProfileStore.read(androidShared, androidSecrets);
        check(NutritionStore.read(androidShared, NutritionStore.PROFILE).has("apiKey"), "In-memory READY discarded the only durable old credential");
        check(androidSecrets.durable.isEmpty(), "Second failed flush unexpectedly became durable");
        JSONObject retried = PrivateProfileStore.read(androidShared, androidSecrets);
        check(retried.optString("apiKey").equals("synthetic-placeholder"), "Durability retry lost the original local credential");
        check(!ProfilePrivacy.hasLocalOnly(NutritionStore.read(androidShared, NutritionStore.PROFILE)), "Successful private flush did not scrub the public source");
        check(androidSecrets.durable.containsKey("local_profile_json"), "Public source was scrubbed without a durable private copy");
        FakePrefs incomingSystem = new FakePrefs(), emptyPrivate = new FakePrefs();
        incomingSystem.values.put(NutritionStore.PROFILE, source.toString());
        incomingSystem.values.put(NutritionStore.RESTORE_MARKER, "foreign-install-journal");
        PrivateProfileStore.finishSystemRestore(incomingSystem);
        check(!incomingSystem.contains(NutritionStore.RESTORE_MARKER), "System restore imported a foreign recovery journal");
        check(!ProfilePrivacy.hasLocalOnly(NutritionStore.read(incomingSystem, NutritionStore.PROFILE)), "System restore kept imported local configuration");
        check(!PrivateProfileStore.read(incomingSystem, emptyPrivate).has("apiKey"), "First read migrated a key from system restore");
        incomingSystem.values.put(NutritionStore.PROFILE, source.toString());
        PrivateProfileStore.finishSystemRestore(incomingSystem);
        check(!PrivateProfileStore.read(incomingSystem, secrets).has("apiKey"), "System restore undid the existing install's key deletion");
        System.out.println("PASS: " + checks + " profile privacy, split-store migration, failure/recovery and stale-authority checks (synthetic data only)");
    }

    private static final class FakePrefs implements SharedPreferences {
        final Map<String,Object> values = new HashMap<String,Object>(), durable = new HashMap<String,Object>();
        int failCommits; boolean memoryOnFailedCommit;
        public Map<String,?> getAll() { return new HashMap<String,Object>(values); }
        public String getString(String key,String fallback) { Object value=values.get(key);return value instanceof String?(String)value:fallback; }
        @SuppressWarnings("unchecked") public Set<String> getStringSet(String key,Set<String> fallback) { Object value=values.get(key);return value instanceof Set?new HashSet<String>((Set<String>)value):fallback; }
        public int getInt(String key,int fallback) { Object v=values.get(key);return v instanceof Number?((Number)v).intValue():fallback; }
        public long getLong(String key,long fallback) { Object v=values.get(key);return v instanceof Number?((Number)v).longValue():fallback; }
        public float getFloat(String key,float fallback) { Object v=values.get(key);return v instanceof Number?((Number)v).floatValue():fallback; }
        public boolean getBoolean(String key,boolean fallback) { Object v=values.get(key);return v instanceof Boolean?(Boolean)v:fallback; }
        public boolean contains(String key) { return values.containsKey(key); }
        public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) { }
        public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) { }
        public Editor edit() { return new Editor() {
            final Map<String,Object> changes=new HashMap<String,Object>();boolean clear;
            public Editor putString(String k,String v){changes.put(k,v);return this;}
            public Editor putStringSet(String k,Set<String> v){changes.put(k,new HashSet<String>(v));return this;}
            public Editor putInt(String k,int v){changes.put(k,v);return this;}
            public Editor putLong(String k,long v){changes.put(k,v);return this;}
            public Editor putFloat(String k,float v){changes.put(k,v);return this;}
            public Editor putBoolean(String k,boolean v){changes.put(k,v);return this;}
            public Editor remove(String k){changes.put(k,null);return this;}
            public Editor clear(){clear=true;return this;}
            public boolean commit(){boolean failed=failCommits>0;if(failed)failCommits--;if(!failed||memoryOnFailedCommit){if(clear)values.clear();for(Map.Entry<String,Object> entry:changes.entrySet())if(entry.getValue()==null)values.remove(entry.getKey());else values.put(entry.getKey(),entry.getValue());}if(failed)return false;durable.clear();durable.putAll(values);return true;}
            public void apply(){commit();}
        }; }
    }
}
