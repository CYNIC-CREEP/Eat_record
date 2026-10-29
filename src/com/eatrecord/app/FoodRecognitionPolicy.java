package com.eatrecord.app;

import org.json.JSONObject;

/** API recognition and its explicit photo consent; unknown retired routes fail closed. */
public final class FoodRecognitionPolicy {
    public static final String API="api";
    private FoodRecognitionPolicy() { }
    public static String source(JSONObject profile){
        String value=profile==null?"":profile.optString("foodRecognitionSource","");
        return value.isEmpty()||API.equals(value)?API:"";
    }
    public static boolean enabled(JSONObject profile){
        return profile!=null&&API.equals(source(profile))&&profile.optBoolean("foodAutoAnalyze")&&profile.optBoolean("foodPhotoConsent");
    }
    public static boolean apiConfigured(JSONObject profile){
        return profile!=null&&profile.optBoolean("apiEnabled")&&!profile.optString("apiKey").trim().isEmpty()
                &&!profile.optString("apiModel").trim().isEmpty()&&profile.optString("apiEndpoint").startsWith("https://");
    }
    public static boolean configured(JSONObject profile){return API.equals(source(profile))&&apiConfigured(profile);}
    /** Upgrade never turns a previously local-only choice into permission to upload photos. */
    public static boolean retireRemovedSource(JSONObject profile)throws org.json.JSONException {
        if(profile==null)return false;
        boolean retired="offline".equals(profile.optString("foodRecognitionSource"));
        boolean removedFlag=profile.has("foodOfflineAnalyze");
        profile.remove("foodOfflineAnalyze");
        if(retired){
            profile.put("foodRecognitionSource",API).put("foodAutoAnalyze",false).put("foodPhotoConsent",false)
                    .put("foodRecognitionGeneration",generation(profile)==Long.MAX_VALUE?1:generation(profile)+1).put("foodRecognitionOnboardingSeen",false);
        }
        return retired||removedFlag;
    }
    public static long generation(JSONObject profile){return profile==null?0:Math.max(0,profile.optLong("foodRecognitionGeneration"));}
    public static final class Snapshot {
        public final String source;
        public final long generation;
        private Snapshot(JSONObject profile){source=source(profile);generation=generation(profile);}
    }
    public static Snapshot capture(JSONObject profile){return new Snapshot(profile);}
    public static boolean current(Snapshot started,JSONObject profile){
        return started!=null&&enabled(profile)&&started.source.equals(source(profile))&&started.generation==generation(profile);
    }
    public static boolean jobRouteCurrent(JSONObject job,JSONObject profile){
        if(job==null)return true;
        if(job.has("recognitionSource")&&!API.equals(job.optString("recognitionSource")))return false;
        return !job.has("recognitionGeneration")
                ||job.optLong("recognitionGeneration")==generation(profile)&&API.equals(source(profile));
    }
    public static String waitingStatus(JSONObject profile,boolean held){
        if(!enabled(profile))return "awaiting_consent";
        if(!configured(profile))return "waiting_api";
        return held?"needs_retry":"pending";
    }
    /** Recognition holds do not block the independent local cutout stage. */
    public static boolean runnable(JSONObject job,boolean nutritionAvailable){
        return job!=null&&(!job.optBoolean("cutoutDone")
                ||!job.optBoolean("cutoutOnly")&&nutritionAvailable&&!job.optBoolean("recognitionHold"));
    }
    /** Historical records stay readable, but a newly accepted result must be from the API. */
    public static void prepareResultMerge(JSONObject record,JSONObject result)throws org.json.JSONException {
        String route=result.getString("recognitionSource");
        if(!API.equals(route))throw new org.json.JSONException("未知识别来源");
        result.put("analysisSource",API);
        for(String key:new String[]{"offlineBasicEstimate","offlineLoadMs","offlineVisionMs","offlinePrefillMs","offlineDecodeMs","offlineTokens","offlineNutritionSource","offlineReferenceFdcIds"})record.remove(key);
        for(String key:new String[]{"nameLatencyMs","calorieLatencyMs"})if(!result.has(key))record.remove(key);
    }
}
