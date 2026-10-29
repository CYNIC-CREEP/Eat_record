package com.eatrecord.app;

import org.json.JSONObject;

public final class FoodRecognitionPolicyTest {
    private static int checks;
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    private static JSONObject api()throws Exception{return new JSONObject().put("foodAutoAnalyze",true).put("foodPhotoConsent",true)
            .put("apiEnabled",true).put("apiKey","test-not-a-real-key").put("apiModel","test-model").put("apiEndpoint","https://example.invalid/chat/completions");}
    public static void main(String[] args)throws Exception{
        JSONObject legacy=api();
        check(FoodRecognitionPolicy.API.equals(FoodRecognitionPolicy.source(legacy)),"Missing route retains a previously authorized API");
        check(FoodRecognitionPolicy.enabled(legacy)&&FoodRecognitionPolicy.configured(legacy),"Existing authorized API remains usable");
        check(!FoodRecognitionPolicy.enabled(new JSONObject())&&!FoodRecognitionPolicy.enabled(null),"New users never upload photos without consent");
        for(String route:new String[]{"offline","other"," API ","OFFLINE","fallback"}){
            JSONObject invalid=api().put("foodRecognitionSource",route);
            check(!FoodRecognitionPolicy.enabled(invalid),"Unknown or retired route fails closed");
            check(!FoodRecognitionPolicy.configured(invalid),"API key cannot substitute for a retired route");
        }
        JSONObject local=api().put("foodRecognitionSource","offline").put("foodOfflineAnalyze",true).put("foodRecognitionGeneration",7);
        FoodRecognitionPolicy.Snapshot old=FoodRecognitionPolicy.capture(local);
        check(FoodRecognitionPolicy.retireRemovedSource(local),"Upgrade retires selected local-only route");
        check("api".equals(local.optString("foodRecognitionSource"))&&!local.has("foodOfflineAnalyze"),"Only API is selected after migration");
        check(!local.optBoolean("foodAutoAnalyze")&&!local.optBoolean("foodPhotoConsent")&&!FoodRecognitionPolicy.enabled(local),"Retirement never grants cloud authorization");
        check(local.optLong("foodRecognitionGeneration")==8&&!FoodRecognitionPolicy.current(old,local),"Old generation cannot write results");
        check(local.optBoolean("apiEnabled")&&"test-not-a-real-key".equals(local.optString("apiKey")),"Migration preserves separate API settings");
        check(!local.optBoolean("foodRecognitionOnboardingSeen"),"Migrated user can configure the API explicitly");
        check(!FoodRecognitionPolicy.retireRemovedSource(local)&&local.optLong("foodRecognitionGeneration")==8,"Migration is idempotent");
        JSONObject normal=api().put("foodRecognitionGeneration",12).put("foodOfflineAnalyze",false);
        check(FoodRecognitionPolicy.retireRemovedSource(normal),"Obsolete flag is removed from an API profile");
        check(FoodRecognitionPolicy.enabled(normal)&&normal.optLong("foodRecognitionGeneration")==12,"Cleaning a stale flag never disables a normal API user");
        check(!FoodRecognitionPolicy.retireRemovedSource(normal),"Normal API cleanup is also idempotent");
        check(!FoodRecognitionPolicy.retireRemovedSource(null),"Missing profile is harmless");
        FoodRecognitionPolicy.Snapshot started=FoodRecognitionPolicy.capture(legacy);
        check(FoodRecognitionPolicy.current(started,legacy),"Current authorized request can write");
        legacy.put("foodRecognitionGeneration",1);check(!FoodRecognitionPolicy.current(started,legacy),"Generation change rejects old results");
        legacy.put("foodRecognitionGeneration",0).put("foodPhotoConsent",false);check(!FoodRecognitionPolicy.current(started,legacy),"Revoked consent rejects old callbacks");
        check(!FoodRecognitionPolicy.current(null,legacy),"Missing snapshot cannot write");
        JSONObject cutout=new JSONObject().put("cutoutDone",false).put("recognitionHold",true);
        check(FoodRecognitionPolicy.runnable(cutout,false),"Recognition hold cannot block local cutout");
        JSONObject nutrition=new JSONObject().put("cutoutDone",true);
        check(!FoodRecognitionPolicy.runnable(nutrition,false),"Waiting-API work does not poll");
        check(FoodRecognitionPolicy.runnable(nutrition,true),"A configured API can run pending nutrition");
        nutrition.put("recognitionHold",true);check(!FoodRecognitionPolicy.runnable(nutrition,true),"Held old work cannot silently rebill");
        check(!FoodRecognitionPolicy.runnable(new JSONObject().put("cutoutDone",true).put("cutoutOnly",true),true),"Cutout-only jobs never fall through to API");
        JSONObject badHttp=api().put("apiEndpoint","http://example.invalid/chat/completions");check(!FoodRecognitionPolicy.apiConfigured(badHttp),"Credentials still require HTTPS");
        check("waiting_api".equals(FoodRecognitionPolicy.waitingStatus(badHttp,false)),"Missing API is durable waiting state");
        check("awaiting_consent".equals(FoodRecognitionPolicy.waitingStatus(local,true)),"Retired source awaits explicit authorization");
        check("needs_retry".equals(FoodRecognitionPolicy.waitingStatus(api(),true)),"Uncertain request requires explicit retry");
        check("pending".equals(FoodRecognitionPolicy.waitingStatus(api(),false)),"Authorized new request is pending");
        check(FoodRecognitionPolicy.jobRouteCurrent(new JSONObject(),api()),"Previously authorized legacy API jobs remain valid");
        JSONObject queued=new JSONObject().put("recognitionSource","api").put("recognitionGeneration",0);
        check(FoodRecognitionPolicy.jobRouteCurrent(queued,api()),"Same authorized generation remains valid");
        check(!FoodRecognitionPolicy.jobRouteCurrent(queued,api().put("foodRecognitionGeneration",1)),"Changed API generation rejects old job");
        check(!FoodRecognitionPolicy.jobRouteCurrent(new JSONObject().put("recognitionSource","offline"),api()),"Explicit retired job is rejected even without generation");
        check(!FoodRecognitionPolicy.jobRouteCurrent(new JSONObject().put("recognitionSource","unknown"),api()),"Corrupt route never uploads");
        JSONObject prior=new JSONObject().put("analysisSource","offline").put("offlineBasicEstimate",true).put("offlineTokens",100).put("offlineVisionMs",45).put("calorieLatencyMs",77).put("offlineNutritionSource","USDA").put("offlineReferenceFdcIds",new org.json.JSONArray().put(169757));
        JSONObject cloudResult=new JSONObject().put("recognitionSource","api");FoodRecognitionPolicy.prepareResultMerge(prior,cloudResult);
        check("api".equals(cloudResult.optString("analysisSource")),"Only new validated API result receives API source");
        check(!prior.has("offlineBasicEstimate")&&!prior.has("offlineTokens")&&!prior.has("offlineVisionMs"),"New API rerecognition clears retired metadata");
        check(!prior.has("calorieLatencyMs")&&!prior.has("offlineNutritionSource")&&!prior.has("offlineReferenceFdcIds"),"No stale attribution or timings inherit");
        boolean rejected=false;try{FoodRecognitionPolicy.prepareResultMerge(new JSONObject(),new JSONObject().put("recognitionSource","offline"));}catch(org.json.JSONException expected){rejected=true;}
        check(rejected,"A new retired-processor result can never be saved");
        System.out.println("PASS: "+checks+" API-only recognition/migration/consent/generation checks");
    }
}
