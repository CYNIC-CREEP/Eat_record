package com.eatrecord.app;

import android.app.job.*;
import android.content.*;
import android.graphics.*;
import android.net.*;
import org.json.*;
import java.io.*;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.UUID;

/** Durable per-photo queue; camera process recreation does not lose work. */
public final class FoodAnalysisJobService extends JobService {
    public static final String CHANGED = "com.eatrecord.app.FOOD_ANALYSIS_CHANGED";
    static final String QUEUE = "food_analysis_jobs";
    private static final int JOB = 61001;
    private static volatile boolean active;
    private static volatile FoodAnalysisJobService runningService;
    private volatile boolean stopped;
    private volatile String activeRequestId = "";
    private Thread worker;
    // Display-only SSE previews, keyed by generation. Never enter diary/draft nutrition or backups.
    private static final java.util.concurrent.ConcurrentHashMap<String,Double> CALORIE_PREVIEWS=new java.util.concurrent.ConcurrentHashMap<>();
    public static Double previewCalories(String analysisId){return analysisId==null?null:CALORIE_PREVIEWS.get(analysisId);}

    public static boolean enabled(JSONObject profile) {
        return FoodRecognitionPolicy.enabled(profile);
    }
    public static boolean apiConfigured(JSONObject profile) {
        return FoodRecognitionPolicy.apiConfigured(profile);
    }
    public static boolean configured(Context context,JSONObject profile){
        return FoodRecognitionPolicy.configured(profile);
    }
    private static String waitingStatus(Context context,JSONObject profile,boolean held){
        return FoodRecognitionPolicy.waitingStatus(profile,held);
    }
    /** Cancels the current API request and its transient previews. */
    public static void cancelRequest(String id){
        if(id==null||id.isEmpty())return;
        CALORIE_PREVIEWS.remove(id);FoodAnalysisClient.cancelRequest(id);
    }
    /** Call after committing route/consent and its generation under NutritionStore.LOCK. */
    public static void recognitionSettingsChanged(Context context){recognitionSettingsChanged(context,false);}
    /** Old local-only jobs must require an explicit new operation before a paid API request. */
    public static void retireRemovedRoute(Context context){recognitionSettingsChanged(context,true);}
    private static void recognitionSettingsChanged(Context context,boolean holdAllNutrition){
        java.util.Set<String> cancel=new java.util.HashSet<>();
        synchronized(NutritionStore.LOCK){
            SharedPreferences p=context.getSharedPreferences(NutritionStore.PREFS,0);
            JSONObject profile=PrivateProfileStore.read(context),days=NutritionStore.read(p,NutritionStore.DAYS),drafts=FoodDraftStore.read(p);
            JSONArray queue=jobs(p),next=new JSONArray();boolean changed=false;
            for(int i=0;i<queue.length();i++){
                JSONObject job=queue.optJSONObject(i);if(job==null)continue;
                if(job.optBoolean("cutoutOnly")){next.put(job);continue;}
                cancel.add(job.optString("analysisId"));
                JSONObject record=drafts.optJSONObject(job.optString("source"));
                JSONObject item=record==null?NutritionStore.find(NutritionStore.meal(days,job.optString("date"),job.optString("meal")),job.optString("id")):record.optJSONObject("item");
                if(item==null||job.optLong("epoch")!=p.getLong("food_jobs_epoch",0)
                        ||!job.optString("analysisId").equals(item.optString("analysisId"))){next.put(job);continue;}
                // The completed response may have won the same lock before the settings change.
                if("completed".equals(item.optString("analysisStatus"))&&item.optJSONObject("nutrients")!=null){changed=true;continue;}
                try{
                    if(holdAllNutrition||job.optBoolean("recognitionStarted")||!FoodRecognitionPolicy.jobRouteCurrent(job,profile))job.put("recognitionHold",true);
                    job.remove("recognitionStarted");job.remove("notBeforeAt");
                    if(job.optBoolean("cutoutDone"))item.put("analysisStatus",waitingStatus(context,profile,job.optBoolean("recognitionHold")))
                            .put("error",job.optBoolean("recognitionHold")?"识别方式或授权已改变，旧请求已停止；可按需重新识别信息":"");
                    changed=true;
                }catch(JSONException ignored){}
                next.put(job);
            }
            if(changed)p.edit().putString(QUEUE,next.toString()).putString(NutritionStore.DAYS,days.toString()).putString(FoodDraftStore.KEY,drafts.toString()).commit();
        }
        FoodAnalysisJobService service=runningService;if(service!=null)cancel.add(service.activeRequestId);
        for(String id:cancel)cancelRequest(id);
        CALORIE_PREVIEWS.clear();FoodDraftStore.changed(context);schedule(context,false,0);
    }
    public static void enqueue(Context c, String date, String key, String source, boolean force) {
        if (!NutritionStore.safeName(source)) return;
        synchronized (NutritionStore.LOCK) {
            SharedPreferences p = c.getSharedPreferences(NutritionStore.PREFS, 0);
            JSONObject days = NutritionStore.read(p, NutritionStore.DAYS), meal = NutritionStore.meal(days, date, key);
            JSONObject old = FoodSavedPhotoPolicy.sourceItem(meal, source);
            boolean arrayReference = NutritionStore.containsPhoto(meal, source);
            if (!arrayReference && old == null) return;
            try {
                String hash = FoodCutoutEngine.hash(new File(c.getFilesDir(), source));
                JSONArray items = meal.optJSONArray("foodItems"); if (items == null) items = new JSONArray();
                JSONArray jobs = jobs(p);
                long epoch = p.getLong("food_jobs_epoch", 0);
                if (FoodSavedPhotoPolicy.skip(old, hash, force)
                        || (!force && FoodSavedPhotoPolicy.queued(jobs, old, hash, epoch))) return;
                if (old == null && !force) for (int i=0;i<items.length();i++) {
                    JSONObject duplicate = items.optJSONObject(i);
                    if (duplicate != null && hash.equals(duplicate.optString("imageHash"))
                            && (FoodSavedPhotoPolicy.skip(duplicate, hash, false)
                            || FoodSavedPhotoPolicy.queued(jobs, duplicate, hash, epoch))) return;
                }
                boolean reuseCutout = FoodSavedPhotoPolicy.reuseCutout(old, hash, force,
                        old != null && NutritionStore.safeName(old.optString("cutoutFile"))
                        && new File(c.getFilesDir(), old.optString("cutoutFile")).isFile());
                String id = old == null ? "" : old.optString("id");
                if (id.isEmpty()) id = UUID.randomUUID().toString();
                JSONObject item = old == null ? new JSONObject() : old;
                if(force){item.remove("cutoutEdited");item.remove("cutoutUserEdited");}
                String analysisId = UUID.randomUUID().toString();
                item.put("id", id).put("analysisId", analysisId).put("sourcePhoto", source).put("imageHash", hash)
                    .put("userEdited", false)
                    .put("status", old != null && old.has("nutrients") ? "completed" : "pending")
                    .put("analysisStatus", reuseCutout?waitingStatus(c,PrivateProfileStore.read(c),false):"pending").put("createdAt", item.optLong("createdAt", System.currentTimeMillis()));
                if (old == null) items.put(item);
                meal.put("foodItems", items);
                JSONArray next = new JSONArray();
                for(int i=0;i<jobs.length();i++) { JSONObject queued=jobs.optJSONObject(i); if(queued!=null&&!id.equals(queued.optString("id")))next.put(queued); }
                next.put(new JSONObject().put("id", id).put("analysisId", analysisId).put("epoch",epoch).put("date", date).put("meal", key)
                    .put("source", source).put("hash", hash).put("attempts", 0).put("cutoutDone", reuseCutout)
                    .put("sourceReferenceOnly", !arrayReference).put("dishNameHint",item.optString("dishNameHint")));
                p.edit().putString(NutritionStore.DAYS, days.toString()).putString(QUEUE, next.toString()).commit();
            } catch (Exception ignored) { return; }
        }
        schedule(c, false, 0);
    }
    /** Stage a new import without adding anything to the diary until the user confirms. */
    public static boolean enqueueDraft(Context c, String date, String key, String source, boolean force) {
        if (!NutritionStore.safeName(source) || !FoodDraftStore.validTarget(date, key)) return false;
        synchronized (NutritionStore.LOCK) {
            SharedPreferences p = c.getSharedPreferences(NutritionStore.PREFS, 0);
            JSONObject drafts = FoodDraftStore.read(p), oldRecord = drafts.optJSONObject(source);
            JSONObject old = oldRecord == null ? null : oldRecord.optJSONObject("item");
            // A retry of a saved record must use enqueue, never convert that record into an owned draft.
            if (FoodDraftStore.references(NutritionStore.read(p, NutritionStore.DAYS), source)
                || FoodDraftStore.references(NutritionStore.read(p, NutritionStore.PROFILE), source)) return false;
            try {
                String hash = FoodCutoutEngine.hash(new File(c.getFilesDir(), source));
                if (old != null && !force && hash.equals(old.optString("imageHash"))) return true;
                JSONObject item = old == null ? new JSONObject() : new JSONObject(old.toString());
                if(force){item.remove("cutoutEdited");item.remove("cutoutUserEdited");}
                String id = old == null ? UUID.randomUUID().toString() : old.getString("id");
                String analysisId = UUID.randomUUID().toString();
                item.put("id", id).put("analysisId", analysisId).put("sourcePhoto", source).put("imageHash", hash)
                    .put("userEdited", false).put("cutoutEdited", false).put("status", item.has("nutrients") ? "completed" : "pending")
                    .put("analysisStatus", "pending").put("createdAt", item.optLong("createdAt", System.currentTimeMillis()));
                JSONObject record = (oldRecord == null ? new JSONObject() : new JSONObject(oldRecord.toString()))
                    .put("date", date).put("meal", key).put("item", item)
                    .put("ownedSource", oldRecord != null ? oldRecord.optBoolean("ownedSource")
                        : source.startsWith("meal_") || source.startsWith("draft_") || source.startsWith("sticker_"));
                drafts.put(source, record);
                JSONArray queue = jobs(p), next = new JSONArray();
                for (int i = 0; i < queue.length(); i++) {
                    JSONObject job = queue.optJSONObject(i);
                    if (job != null && !id.equals(job.optString("id"))) next.put(job);
                }
                next.put(new JSONObject().put("id", id).put("analysisId", analysisId)
                    .put("epoch", p.getLong("food_jobs_epoch", 0)).put("date", date).put("meal", key)
                    .put("source", source).put("hash", hash).put("attempts", 0).put("cutoutDone", false).put("draft", true)
                    .put("interactive",true).put("enqueuedAt",System.currentTimeMillis()).put("dishNameHint",item.optString("dishNameHint")));
                if (!p.edit().putString(FoodDraftStore.KEY, drafts.toString()).putString(QUEUE, next.toString()).commit()) return false;
            } catch (Exception ignored) { return false; }
        }
        FoodDraftStore.changed(c); schedule(c, false, 0); return true;
    }
    static JSONArray jobs(SharedPreferences p) {
        try { return new JSONArray(p.getString(QUEUE,"[]")); } catch (Exception e) { return new JSONArray(); }
    }
    static boolean cutoutPending(Context context,JSONObject item){
        if(item==null)return false;
        SharedPreferences p=context.getSharedPreferences(NutritionStore.PREFS,0);JSONArray queue=jobs(p);
        for(int i=0;i<queue.length();i++){
            JSONObject job=queue.optJSONObject(i);
            if(job!=null&&job.optBoolean("cutoutOnly")&&job.optLong("epoch")==p.getLong("food_jobs_epoch",0)
                &&item.optString("id").equals(job.optString("id"))&&FoodSavedPhotoPolicy.cutoutGenerationCurrent(item,job))return true;
        }return false;
    }
    static boolean nutritionPending(Context context,JSONObject item){
        if(item==null)return false;
        SharedPreferences p=context.getSharedPreferences(NutritionStore.PREFS,0);JSONArray queue=jobs(p);
        for(int i=0;i<queue.length();i++){
            JSONObject job=queue.optJSONObject(i);
            if(job!=null&&!job.optBoolean("cutoutOnly")&&job.optLong("epoch")==p.getLong("food_jobs_epoch",0)
                &&item.optString("id").equals(job.optString("id"))&&item.optString("analysisId").equals(job.optString("analysisId")))return true;
        }return false;
    }

    /** Explicit local-only operation. It neither clears nutrition nor starts/restarts a paid request. */
    public static boolean enqueueCutout(Context c,String date,String key,String source){
        return enqueueOperation(c,date,key,source,true);
    }

    /** Explicit nutrition-only operation, with the existing alpha and corrected name retained. */
    public static boolean enqueueNutrition(Context c,String date,String key,String source){
        return enqueueOperation(c,date,key,source,false);
    }

    /** A manual mask edit merges only image metadata into the latest record, never a UI snapshot. */
    public static boolean saveCutout(Context c,String date,String key,String source,String cutout,JSONArray bounds){
        if(!FoodDraftStore.validTarget(date,key)||!NutritionStore.safeName(source)||!NutritionStore.safeName(cutout)
            ||!cutout.startsWith("food_cutout_")||!cutout.endsWith(".png")||!new File(c.getFilesDir(),cutout).isFile())return false;
        boolean committed=false;
        synchronized(NutritionStore.LOCK){
            SharedPreferences p=c.getSharedPreferences(NutritionStore.PREFS,0);
            JSONObject days=NutritionStore.read(p,NutritionStore.DAYS),drafts=FoodDraftStore.read(p),record=drafts.optJSONObject(source);
            JSONObject item;
            if(record!=null){
                if(!date.equals(record.optString("date"))||!key.equals(record.optString("meal")))return false;
                item=record.optJSONObject("item");
            }else item=FoodSavedPhotoPolicy.sourceItem(NutritionStore.meal(days,date,key),source);
            if(item==null||item.optString("id").isEmpty())return false;
            try{
                if(record!=null){FoodDraftStore.rememberCutout(record,item.optString("cutoutFile"));FoodDraftStore.rememberCutout(record,cutout);}
                item.put("cutoutFile",cutout).put("cutoutEdited",true).put("cutoutUserEdited",true)
                    .put("cutoutStatus","completed").put("cutoutError","").put("cutoutEditedAt",System.currentTimeMillis())
                    .put("cutoutRequestId",UUID.randomUUID().toString());
                if(bounds!=null&&bounds.length()==4)item.put("cutoutRect",new JSONArray(bounds.toString()));
                JSONArray existing=jobs(p),next=new JSONArray();
                for(int i=0;i<existing.length();i++){
                    JSONObject job=existing.optJSONObject(i);
                    if(job!=null&&!(job.optBoolean("cutoutOnly")&&item.getString("id").equals(job.optString("id"))))next.put(job);
                }
                committed=p.edit().putString(record==null?NutritionStore.DAYS:FoodDraftStore.KEY,record==null?days.toString():drafts.toString())
                    .putString(QUEUE,next.toString()).commit();
            }catch(Exception ignored){return false;}
        }
        if(committed)FoodDraftStore.changed(c);return committed;
    }

    /** Merge a user nutrition/name form with the newest local image metadata atomically. */
    public static boolean saveNutritionEdit(Context c,String date,String key,String source,JSONObject edited){
        if(edited==null||!FoodDraftStore.validTarget(date,key)||!NutritionStore.safeName(source))return false;
        String cancelled="";
        synchronized(NutritionStore.LOCK){
            SharedPreferences p=c.getSharedPreferences(NutritionStore.PREFS,0);
            JSONObject days=NutritionStore.read(p,NutritionStore.DAYS),drafts=FoodDraftStore.read(p),record=drafts.optJSONObject(source);
            JSONObject meal=NutritionStore.meal(days,date,key),latest;
            if(record!=null){
                if(!date.equals(record.optString("date"))||!key.equals(record.optString("meal")))return false;
                latest=record.optJSONObject("item");
            }else latest=FoodSavedPhotoPolicy.sourceItem(meal,source);
            if(latest==null||latest.optString("id").isEmpty()||!latest.optString("id").equals(edited.optString("id")))return false;
            try{
                JSONObject merged=FoodSavedPhotoPolicy.mergeNutritionEdit(latest,edited,UUID.randomUUID().toString());
                cancelled=latest.optString("analysisId");
                if(record!=null){record.put("item",merged);FoodDraftStore.rememberCutout(record,merged.optString("cutoutFile"));}
                else{
                    JSONArray foods=meal.getJSONArray("foodItems");
                    for(int i=0;i<foods.length();i++){JSONObject item=foods.optJSONObject(i);if(item!=null&&latest.getString("id").equals(item.optString("id"))){foods.put(i,merged);break;}}
                }
                JSONArray queue=FoodSavedPhotoPolicy.withoutNutritionJobs(jobs(p),latest.getString("id"));
                if(!p.edit().putString(record==null?NutritionStore.DAYS:FoodDraftStore.KEY,record==null?days.toString():drafts.toString())
                    .putString(QUEUE,queue.toString()).commit())return false;
            }catch(Exception ignored){return false;}
        }
        cancelRequest(cancelled);FoodDraftStore.changed(c);return true;
    }

    private static boolean enqueueOperation(Context c,String date,String key,String source,boolean cutoutOnly){
        if(!NutritionStore.safeName(source)||!FoodDraftStore.validTarget(date,key))return false;
        String cancel="";
        synchronized(NutritionStore.LOCK){
            SharedPreferences p=c.getSharedPreferences(NutritionStore.PREFS,0);
            JSONObject days=NutritionStore.read(p,NutritionStore.DAYS),drafts=FoodDraftStore.read(p),record=drafts.optJSONObject(source);
            JSONObject meal=NutritionStore.meal(days,date,key),old;
            if(record!=null){
                if(!date.equals(record.optString("date"))||!key.equals(record.optString("meal")))return false;
                old=record.optJSONObject("item");
            }else old=FoodSavedPhotoPolicy.sourceItem(meal,source);
            File photo=new File(c.getFilesDir(),source);
            if(old==null||old.optString("id").isEmpty()||!photo.isFile())return false;
            try{
                String hash=FoodCutoutEngine.hash(photo),previousHash=old.optString("imageHash");
                if(!previousHash.isEmpty()&&!previousHash.equals(hash))return false;
                String request=UUID.randomUUID().toString();
                JSONObject updated=cutoutOnly?FoodSavedPhotoPolicy.prepareCutout(old,request):FoodSavedPhotoPolicy.prepareNutrition(old,request);
                JSONObject profile=PrivateProfileStore.read(c);
                if(!cutoutOnly){
                    updated.put("analysisStatus",waitingStatus(c,profile,false));
                    cancel=old.optString("analysisId");
                }
                updated.put("imageHash",hash);
                JSONArray existing=jobs(p),next=new JSONArray();
                for(int i=0;i<existing.length();i++){
                    JSONObject queued=existing.optJSONObject(i);if(queued==null)continue;
                    boolean same=old.optString("id").equals(queued.optString("id"));
                    if(same&&queued.optBoolean("cutoutOnly")==cutoutOnly)continue;
                    next.put(queued);
                }
                JSONObject job=new JSONObject().put("id",old.getString("id")).put("analysisId",request)
                    .put("epoch",p.getLong("food_jobs_epoch",0)).put("date",date).put("meal",key)
                    .put("source",source).put("hash",hash).put("attempts",0).put("cutoutDone",!cutoutOnly)
                    .put("draft",record!=null).put("interactive",true).put("enqueuedAt",System.currentTimeMillis())
                    .put("sourceReferenceOnly",true).put("cutoutOnly",cutoutOnly).put("nutritionOnly",!cutoutOnly)
                    .put("allowUserEdited",!cutoutOnly).put("dishNameHint",FoodSavedPhotoPolicy.correctedName(old))
                    .put("cutoutBaseEditedAt",old.optLong("cutoutEditedAt"));
                next.put(job);
                if(record!=null)record.put("item",updated);
                else{
                    JSONArray foods=meal.getJSONArray("foodItems");
                    for(int i=0;i<foods.length();i++){JSONObject item=foods.optJSONObject(i);if(item!=null&&old.getString("id").equals(item.optString("id"))){foods.put(i,updated);break;}}
                }
                if(!p.edit().putString(record==null?NutritionStore.DAYS:FoodDraftStore.KEY,record==null?days.toString():drafts.toString())
                    .putString(QUEUE,next.toString()).commit())return false;
            }catch(Exception ignored){return false;}
        }
        if(!cancel.isEmpty())cancelRequest(cancel);
        FoodDraftStore.changed(c);schedule(c,false,0);return true;
    }
    /** Atomic name correction + generation change + nutrition-only durable task. */
    public static boolean renameAndAnalyze(Context context,String date,String mealKey,String source,String requested){
        String name=FoodNameHint.normalize(requested),cancel="";
        if(name.isEmpty()||!NutritionStore.safeName(source)||!FoodDraftStore.validTarget(date,mealKey))return false;
        synchronized(NutritionStore.LOCK){
            SharedPreferences p=context.getSharedPreferences(NutritionStore.PREFS,0);
            JSONObject drafts=FoodDraftStore.read(p),record=drafts.optJSONObject(source),days=NutritionStore.read(p,NutritionStore.DAYS);
            JSONObject meal=NutritionStore.meal(days,date,mealKey),old=null;
            if(record!=null){if(!date.equals(record.optString("date"))||!mealKey.equals(record.optString("meal")))return false;old=record.optJSONObject("item");}
            else{JSONArray values=NutritionStore.forSource(meal,source);if(values.length()>0)old=values.optJSONObject(0);}
            if(old==null||old.optString("id").isEmpty()||!new File(context.getFilesDir(),source).isFile())return false;
            if(name.equals(old.optString("dishName")))return true; // Saving an unchanged label is not another paid API call.
            if(old.optString("cutoutFile").isEmpty()&&old.optString("cutoutError").isEmpty())return false;
            try{
                String hash=FoodCutoutEngine.hash(new File(context.getFilesDir(),source));
                JSONObject updated=FoodNameHint.reanalysis(old,name);updated.put("imageHash",hash);
                JSONObject profile=PrivateProfileStore.read(context);
                updated.put("analysisStatus",waitingStatus(context,profile,false));
                String id=old.getString("id");cancel=old.optString("analysisId");
                if(record!=null)record.put("item",updated);
                else{JSONArray items=meal.getJSONArray("foodItems");for(int i=0;i<items.length();i++){JSONObject item=items.optJSONObject(i);if(item!=null&&id.equals(item.optString("id"))){items.put(i,updated);break;}}}
                JSONArray queue=FoodSavedPhotoPolicy.withoutNutritionJobs(jobs(p),id);
                queue.put(new JSONObject().put("id",id).put("analysisId",updated.getString("analysisId")).put("epoch",p.getLong("food_jobs_epoch",0))
                    .put("date",date).put("meal",mealKey).put("source",source).put("hash",hash).put("attempts",0).put("cutoutDone",true)
                    .put("draft",record!=null).put("dishNameHint",name).put("nutritionOnly",true)
                    .put("interactive",true).put("enqueuedAt",System.currentTimeMillis()));
                if(!p.edit().putString(record==null?NutritionStore.DAYS:FoodDraftStore.KEY,record==null?days.toString():drafts.toString()).putString(QUEUE,queue.toString()).commit())return false;
            }catch(Exception ignored){return false;}
        }
        cancelRequest(cancel);FoodDraftStore.changed(context);schedule(context,false,0);return true;
    }
    public static void schedule(Context c, boolean network, long delay) {
        if (active) return;
        SharedPreferences p=c.getSharedPreferences(NutritionStore.PREFS,0);
        JSONObject profile=PrivateProfileStore.read(c);
        JSONArray queue=jobs(p),eligible=new JSONArray(); boolean local=false,interactive=false;
        boolean available=enabled(profile)&&configured(c,profile);
        long now=System.currentTimeMillis();
        for(int i=0;i<queue.length();i++){JSONObject job=queue.optJSONObject(i);if(FoodRecognitionPolicy.runnable(job,available))eligible.put(job);}
        long earliest=FoodJobPriority.nextDelay(eligible,available,now);
        if(earliest<0)return; // Waiting for consent/API is durable, but must not spin every 30 seconds.
        for(int i=0;i<queue.length();i++) {
            JSONObject job=queue.optJSONObject(i); if(job==null)continue;
            boolean runnable=FoodRecognitionPolicy.runnable(job,available);
            if(!runnable)continue;
            long wait=Math.max(0,job.optLong("notBeforeAt")-now);
            if(wait==0){if(!job.optBoolean("cutoutDone"))local=true;interactive|=job.optBoolean("interactive")||job.optBoolean("draft");}
        }
        long wait=Math.max(Math.max(0,delay),earliest);
        JobScheduler scheduler=(JobScheduler)c.getSystemService(JOB_SCHEDULER_SERVICE);
        // No latency/deadline/idle/charging constraints on an expedited job. Persistence is supported.
        // A quota refusal falls back to the same durable queue, not another HTTP request or worker.
        if(android.os.Build.VERSION.SDK_INT>=31&&wait==0&&interactive){
            try{
                JobInfo.Builder expedited=new JobInfo.Builder(JOB,new ComponentName(c,FoodAnalysisJobService.class))
                    .setPersisted(true).setExpedited(true).setBackoffCriteria(30000,JobInfo.BACKOFF_POLICY_EXPONENTIAL);
                if(network&&!local)expedited.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
                if(scheduler.schedule(expedited.build())==JobScheduler.RESULT_SUCCESS)return;
            }catch(RuntimeException ignored){ }
        }
        JobInfo.Builder builder=new JobInfo.Builder(JOB,new ComponentName(c,FoodAnalysisJobService.class))
            .setPersisted(true).setMinimumLatency(wait).setBackoffCriteria(30000,JobInfo.BACKOFF_POLICY_EXPONENTIAL);
        if (network && !local) builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
        scheduler.schedule(builder.build());
    }
    @Override public boolean onStartJob(final JobParameters params) {
        if (active) return false;
        active=true;
        runningService=this;
        stopped=false;
        worker=new Thread(new Runnable(){ public void run(){
            boolean retry=false,usable=true;
            try {
                BackupRestoreRecovery.recover(FoodAnalysisJobService.this);
                FoodRecognitionRetirement.apply(FoodAnalysisJobService.this);retry=drain();
            } catch(IllegalStateException unavailable) {
                usable=false;
                android.util.Log.w("EatRecord","Food worker paused until local state can be recovered");
            } finally {
                active=false;
                if(runningService==FoodAnalysisJobService.this)runningService=null;
                if (!stopped) {
                    jobFinished(params,false);
                }
                if (usable&&jobs(getSharedPreferences(NutritionStore.PREFS,0)).length()>0)
                    // Each failed job already owns a notBeforeAt deadline. A second global
                    // floor could delay a new interactive photo arriving as this drain exits.
                    schedule(FoodAnalysisJobService.this,retry,0);
            }
        }},"food-analysis");
        worker.start(); return true;
    }
    @Override public boolean onStopJob(JobParameters p) { stopped=true;CALORIE_PREVIEWS.clear(); cancelRequest(activeRequestId); if(worker!=null)worker.interrupt(); return true; }
    public static void cancelAll(Context c) {
        CALORIE_PREVIEWS.clear();
        java.util.Set<String> requests=new java.util.HashSet<>();
        synchronized(NutritionStore.LOCK) {
            FoodDraftStore.discard(c, FoodDraftStore.pendingSources(c));
            SharedPreferences p=c.getSharedPreferences(NutritionStore.PREFS,0);
            JSONArray queue=jobs(p);for(int i=0;i<queue.length();i++){JSONObject job=queue.optJSONObject(i);if(job!=null)requests.add(job.optString("analysisId"));}
            p.edit().putString(QUEUE,"[]").putLong("food_jobs_epoch",p.getLong("food_jobs_epoch",0)+1).commit();
            ((JobScheduler)c.getSystemService(JOB_SCHEDULER_SERVICE)).cancel(JOB);
        }
        FoodAnalysisJobService service=runningService;if(service!=null)requests.add(service.activeRequestId);
        for(String id:requests)cancelRequest(id);
    }
    private boolean drain() {
        SharedPreferences p=getSharedPreferences(NutritionStore.PREFS,0);
        java.util.Set<String> deferred = new java.util.HashSet<>();
        boolean needsNetwork=false;
        // Fresh queue after each stage: a newly opened draft can overtake a saved-photo batch.
        // There is still exactly one worker and at most one request in flight.
        while (!stopped) {
            JSONObject job;
            synchronized (NutritionStore.LOCK) {
                JSONArray queue = jobs(p);
                int next = FoodJobPriority.next(queue, deferred);
                if (next < 0) break;
                job = queue.optJSONObject(next);
            }
            JSONObject profile=PrivateProfileStore.read(this);
            if(!current(job)){ remove(job); continue; }
            File photo=new File(getFilesDir(),job.optString("source"));
            FoodRecognitionPolicy.Snapshot requestPolicy=null;
            try {
                if(!photo.isFile() || !job.optString("hash").equals(FoodCutoutEngine.hash(photo))) { remove(job); continue; }
                if(!job.optBoolean("cutoutDone")) {
                    update(job,new JSONObject().put(job.optBoolean("cutoutOnly")?"cutoutStatus":"analysisStatus","cutting_out"));
                    Bitmap source=null, cutout=null;
                    String file="food_cutout_"+job.getString("analysisId")+".png";
                    try {
                        BitmapFactory.Options opts=new BitmapFactory.Options(); opts.inSampleSize=1;
                        BitmapFactory.Options bounds=new BitmapFactory.Options(); bounds.inJustDecodeBounds=true;
                        BitmapFactory.decodeFile(photo.getAbsolutePath(),bounds);
                        while(Math.max(bounds.outWidth,bounds.outHeight)/opts.inSampleSize>2048)opts.inSampleSize*=2;
                        opts.inPreferredConfig=Bitmap.Config.ARGB_8888;
                        source=BitmapFactory.decodeFile(photo.getAbsolutePath(),opts);
                        if(source!=null)cutout=FoodCutoutEngine.create(this,source);
                        if(cutout==null)throw new IOException("Empty mask");
                        if(!stopped && current(job)) {
                            try(FileOutputStream out=new FileOutputStream(new File(getFilesDir(),file))) {
                                if(!cutout.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException();
                            }
                            Rect cutoutBounds=FoodCutoutEngine.lastBounds();
                            JSONObject cutoutData=new JSONObject().put("cutoutFile",file).put("cutoutError","")
                                .put("cutoutModel",FoodCutoutEngine.lastModel()).put("cutoutFallback",FoodCutoutEngine.wasFallback());
                            if(job.optBoolean("cutoutOnly"))cutoutData.put("cutoutStatus","completed").put("cutoutEdited",false)
                                .put("cutoutUserEdited",false).put("cutoutEditedAt",System.currentTimeMillis());
                            if(cutoutBounds!=null)cutoutData.put("cutoutRect",new JSONArray().put(cutoutBounds.left).put(cutoutBounds.top).put(cutoutBounds.right).put(cutoutBounds.bottom));
                            if(!update(job,cutoutData))new File(getFilesDir(),file).delete();
                        }
                    } catch(Throwable e) {
                        android.util.Log.w("EatRecordVision", "Cutout failed: " + e.getClass().getSimpleName());
                        JSONObject failure=new JSONObject().put("cutoutError","抠图未完成，原照片已保留");
                        if(job.optBoolean("cutoutOnly"))failure.put("cutoutStatus","needs_retry");update(job,failure);
                    } finally { if(cutout!=null)cutout.recycle(); if(source!=null)source.recycle(); }
                    if(stopped)return true;
                    if(job.optBoolean("cutoutOnly")){remove(job);continue;} // Never fall through to the cloud phase.
                    job.put("cutoutDone",true); replaceJob(job);
                    update(job,new JSONObject().put("analysisStatus","pending"));
                    continue;
                }
                if(stopped)return true;
                if(job.optBoolean("cutoutOnly")){remove(job);continue;}
                // Waiting/retry jobs are visited once per drain, retaining scheduler backoff.
                deferred.add(job.optString("analysisId"));
                profile=PrivateProfileStore.read(this);
                if(completed(job)){remove(job);continue;}
                if(!FoodRecognitionPolicy.jobRouteCurrent(job,profile)){
                    holdRequest(job,"识别方式或授权已改变；请按需重新识别信息");continue;
                }
                if(job.optBoolean("recognitionHold")){
                    update(job,new JSONObject().put("analysisStatus",waitingStatus(this,profile,true))
                            .put("error","上次识别已停止或识别方式已改变；请按需重新识别信息"));continue;
                }
                if(job.optBoolean("recognitionStarted")){
                    // A process may have died after sending a billed request, before removing its job.
                    holdRequest(job,"上次识别被中断，原数据已保留；请按需重新识别信息");continue;
                }
                if(!enabled(profile) || !configured(this,profile)) {
                    update(job,new JSONObject().put("analysisStatus",waitingStatus(this,profile,false)).put("error",""));
                    continue;
                }
                if(!online()){job.put("notBeforeAt",System.currentTimeMillis()+30000);replaceJob(job);needsNetwork=true;continue;}
                final FoodRecognitionPolicy.Snapshot started=FoodRecognitionPolicy.capture(profile);requestPolicy=started;
                if(!update(job,new JSONObject().put("analysisStatus","recognizing")
                    .put("queueWaitMs",Math.max(0,System.currentTimeMillis()-job.optLong("enqueuedAt",System.currentTimeMillis()))))){remove(job);continue;}
                job.put("recognitionStarted",true).put("recognitionSource",started.source).put("recognitionGeneration",started.generation);replaceJob(job);
                String hint=FoodNameHint.normalize(job.optString("dishNameHint"));
                long requestStarted=android.os.SystemClock.elapsedRealtime();
                final long[] firstName={-1},firstCalories={-1};
                activeRequestId = job.optString("analysisId");
                FoodAnalysisClient.NameListener previews=new FoodAnalysisClient.NameListener(){
                    @Override public void onDishName(String name){
                        if(!requestCurrent(job,started))return;
                        if(firstName[0]<0)firstName[0]=android.os.SystemClock.elapsedRealtime()-requestStarted;
                        try { update(job,new JSONObject().put("dishName",hint.isEmpty()?name:hint).put("analysisStatus","recognizing"),started); }
                        catch(JSONException ignored) { }
                    }
                    @Override public void onCalories(double calories){
                        synchronized(NutritionStore.LOCK){
                            if(!requestCurrent(job,started))return;
                            firstCalories[0]=android.os.SystemClock.elapsedRealtime()-requestStarted;
                            CALORIE_PREVIEWS.put(job.optString("analysisId"),calories);
                            sendBroadcast(new Intent(CHANGED).setPackage(getPackageName()).putExtra("date",job.optString("date")).putExtra("analysisId",job.optString("analysisId")));
                        }
                    }
                };
                FoodAnalysisResult result=new FoodAnalysisClient().analyze(profile,photo,previews,hint,job.optString("analysisId"),()->requestCurrent(job,started));
                if(!resultCurrent(job,started)) {
                    if(current(job))holdRequest(job,"识别方式或授权已改变，旧结果未保存；可按需重新识别信息");
                    continue;
                }
                JSONObject changes=new JSONObject(result.value.toString());
                changes.put("recognitionSource",started.source);
                changes.put("recognitionMs",android.os.SystemClock.elapsedRealtime()-requestStarted);
                if(firstName[0]>=0)changes.put("nameLatencyMs",firstName[0]);if(firstCalories[0]>=0)changes.put("calorieLatencyMs",firstCalories[0]);
                if(!hint.isEmpty())changes.put("dishName",hint).put("dishNameUserEdited",true).put("nutritionBasedOnName",hint);
                changes.put("status","completed").put("analysisStatus","completed").put("error","")
                    .put("userEdited",false).put("analyzedAt",System.currentTimeMillis());
                if(update(job,changes,started))remove(job);
                else if(current(job)&&!resultCurrent(job,started))holdRequest(job,"识别方式或授权已改变，旧结果未保存；可按需重新识别信息");
            } catch(Exception e) {
                deferred.add(job.optString("analysisId"));
                if(!current(job)){remove(job);continue;}
                if(job.optBoolean("cutoutOnly")){
                    try{update(job,new JSONObject().put("cutoutStatus","needs_retry").put("cutoutError","重新抠图未完成，原透明图和营养已保留"));}catch(JSONException ignored){}
                    remove(job);continue;
                }
                if(requestPolicy!=null&&!requestCurrent(job,requestPolicy)){
                    holdRequest(job,stopped?"后台识别被系统暂停，原数据已保留；可按需重新识别信息":"识别方式或授权已改变，旧请求已停止；可按需重新识别信息");
                    if(stopped)return needsNetwork;continue;
                }
                if(stopped&&!activeRequestId.isEmpty()){
                    // A stopped in-flight request may already have been billed. Do not
                    // silently submit it again without a user's explicit retry.
                    try{update(job,new JSONObject().put("analysisStatus","needs_retry").put("error","后台识别被系统暂停，原数据已保留；可手动重新识别"));}catch(JSONException ignored){}
                    remove(job);return needsNetwork;
                }
                // Invalid replies keep the API retry policy; there is no alternate processor.
                boolean retry=!(e instanceof JSONException) && (!(e instanceof FoodAnalysisClient.Failure) || ((FoodAnalysisClient.Failure)e).retryable);
                int attempts=job.optInt("attempts")+1;
                try {
                    update(job,new JSONObject().put("analysisStatus",retry && attempts<4 ? "pending" : "needs_retry")
                        .put("error",e instanceof FoodAnalysisClient.Failure ? e.getMessage()
                            : e instanceof JSONException ? FoodAnalysisResult.validationMessage((JSONException)e) : "识别暂未完成，请稍后重试"));
                    if(retry && attempts<4){ job.remove("recognitionStarted");job.put("attempts",attempts).put("notBeforeAt",System.currentTimeMillis()+30000); replaceJob(job); needsNetwork=true; }
                    else remove(job);
                } catch(Exception ignored) { remove(job); }
            } finally {activeRequestId = ""; CALORIE_PREVIEWS.remove(job.optString("analysisId"));}
        }
        return needsNetwork;
    }
    private boolean online(){
        ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
        Network n=cm.getActiveNetwork(); NetworkCapabilities caps=n==null?null:cm.getNetworkCapabilities(n);
        return caps!=null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }
    private boolean requestCurrent(JSONObject job,FoodRecognitionPolicy.Snapshot started){
        return !stopped&&resultCurrent(job,started);
    }
    /** A response already validated before an OS stop need not be paid for a second time. */
    private boolean resultCurrent(JSONObject job,FoodRecognitionPolicy.Snapshot started){
        synchronized(NutritionStore.LOCK){
            JSONObject profile=PrivateProfileStore.read(this);
            return current(job)&&FoodRecognitionPolicy.current(started,profile)&&configured(this,profile)&&!job.optBoolean("recognitionHold");
        }
    }
    private boolean completed(JSONObject job){
        synchronized(NutritionStore.LOCK){
            SharedPreferences p=getSharedPreferences(NutritionStore.PREFS,0);
            JSONObject record=FoodDraftStore.read(p).optJSONObject(job.optString("source"));
            JSONObject item=record==null?NutritionStore.find(NutritionStore.meal(NutritionStore.read(p,NutritionStore.DAYS),job.optString("date"),job.optString("meal")),job.optString("id")):record.optJSONObject("item");
            return item!=null&&job.optString("analysisId").equals(item.optString("analysisId"))
                    &&"completed".equals(item.optString("analysisStatus"))&&item.optJSONObject("nutrients")!=null;
        }
    }
    private void holdRequest(JSONObject job,String message){
        synchronized(NutritionStore.LOCK){
            if(!current(job))return;
            try{
                job.put("recognitionHold",true);job.remove("recognitionStarted");job.remove("notBeforeAt");replaceJob(job);
                JSONObject profile=PrivateProfileStore.read(this);
                update(job,new JSONObject().put("analysisStatus",waitingStatus(this,profile,true)).put("error",message));
            }catch(JSONException ignored){}
        }
    }
    private boolean current(JSONObject job){
        synchronized(NutritionStore.LOCK){
            SharedPreferences p=getSharedPreferences(NutritionStore.PREFS,0);
            if (job.optLong("epoch") != p.getLong("food_jobs_epoch",0)) return false;
            JSONObject record=FoodDraftStore.read(p).optJSONObject(job.optString("source"));
            JSONObject meal=NutritionStore.meal(NutritionStore.read(p,NutritionStore.DAYS),job.optString("date"),job.optString("meal"));
            JSONObject item=record==null?NutritionStore.find(meal,job.optString("id")):record.optJSONObject("item");
            boolean cutout=job.optBoolean("cutoutOnly"),generation=item!=null&&job.optString("analysisId").equals(item.optString("analysisId"));
            if(cutout){
                generation=FoodSavedPhotoPolicy.cutoutGenerationCurrent(item,job);
                boolean queued=false;JSONArray queue=jobs(p);
                for(int i=0;i<queue.length();i++){JSONObject pending=queue.optJSONObject(i);if(pending!=null&&job.optString("analysisId").equals(pending.optString("analysisId"))){queued=true;break;}}
                generation&=queued;
            }
            return item!=null && generation
                && job.optString("id").equals(item.optString("id"))
                && (record!=null || NutritionStore.containsPhoto(meal,job.optString("source"))
                    || ((job.optBoolean("nutritionOnly") || job.optBoolean("sourceReferenceOnly"))
                        && job.optString("source").equals(item.optString("sourcePhoto"))))
                && (!item.optBoolean("userEdited")||cutout||job.optBoolean("nutritionOnly")&&job.optBoolean("allowUserEdited"));
        }
    }
    private boolean update(JSONObject job,JSONObject changes){
        return update(job,changes,null);
    }
    private boolean update(JSONObject job,JSONObject changes,FoodRecognitionPolicy.Snapshot started){
        synchronized(NutritionStore.LOCK){
            boolean finalResult="completed".equals(changes.optString("analysisStatus"))&&changes.optJSONObject("nutrients")!=null;
            if(started!=null&&!(finalResult?resultCurrent(job,started):requestCurrent(job,started)))return false;
            if(!current(job))return false;
            SharedPreferences p=getSharedPreferences(NutritionStore.PREFS,0);
            JSONObject days=NutritionStore.read(p,NutritionStore.DAYS),drafts=FoodDraftStore.read(p);
            JSONObject record=drafts.optJSONObject(job.optString("source"));
            JSONObject item=record==null?NutritionStore.find(NutritionStore.meal(days,job.optString("date"),job.optString("meal")),job.optString("id")):record.optJSONObject("item");
            try {
                if(item.optBoolean("cutoutEdited") && changes.has("cutoutFile")&&!job.optBoolean("cutoutOnly"))return false;
                if("completed".equals(changes.optString("analysisStatus"))&&changes.optJSONObject("nutrients")!=null&&changes.has("recognitionSource"))
                    FoodRecognitionPolicy.prepareResultMerge(item,changes);
                JSONArray keys=changes.names();
                for(int i=0;keys!=null && i<keys.length();i++) {
                    String key=keys.getString(i);
                    if(job.optBoolean("cutoutOnly")&&!key.startsWith("cutout"))continue;
                    if(item.optBoolean("cutoutEdited")&&!job.optBoolean("cutoutOnly") && ("cutoutFile".equals(key)||"cutoutRect".equals(key)||"cutoutError".equals(key)))continue;
                    item.put(key,changes.get(key));
                }
                if(record!=null)FoodDraftStore.rememberCutout(record,item.optString("cutoutFile"));
                if(!p.edit().putString(record==null?NutritionStore.DAYS:FoodDraftStore.KEY,record==null?days.toString():drafts.toString()).commit())return false;
                sendBroadcast(new Intent(CHANGED).setPackage(getPackageName()).putExtra("date",job.optString("date")));
                return true;
            }catch(Exception e){return false;}
        }
    }
    private void replaceJob(JSONObject job){ changeQueue(job,false); }
    private void remove(JSONObject job){ changeQueue(job,true); }
    private void changeQueue(JSONObject job,boolean remove){
        synchronized(NutritionStore.LOCK){
            SharedPreferences p=getSharedPreferences(NutritionStore.PREFS,0); JSONArray list=jobs(p), next=new JSONArray();
            for(int i=0;i<list.length();i++){
                JSONObject o=list.optJSONObject(i);
                if(o!=null && job.optString("analysisId").equals(o.optString("analysisId"))) {
                    if(!remove) {
                        // Confirm may have migrated this task while inference used a detached snapshot.
                        try { job.put("draft",o.optBoolean("draft")); } catch(JSONException ignored) { }
                        next.put(job);
                    }
                }
                else next.put(o);
            }
            p.edit().putString(QUEUE,next.toString()).commit();
        }
    }
}
