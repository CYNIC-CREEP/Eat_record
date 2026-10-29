package com.eatrecord.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/** One-way retirement of an old processor; credentials, diary and media are never deleted. */
public final class FoodRecognitionRetirement {
    private static final String CLEANED="retired_recognition_cache_removed";
    private static final AtomicBoolean CLEANING=new AtomicBoolean();
    private FoodRecognitionRetirement() { }
    /** Returns true when the caller should reload its profile snapshot. */
    public static boolean apply(Context context){
        final Context app=context.getApplicationContext();
        final SharedPreferences prefs=app.getSharedPreferences(NutritionStore.PREFS,0);
        boolean changed=false,retired=false;
        synchronized(NutritionStore.LOCK){
            JSONObject profile=PrivateProfileStore.read(app);
            retired="offline".equals(profile.optString("foodRecognitionSource"));
            try{if(FoodRecognitionPolicy.retireRemovedSource(profile))changed=PrivateProfileStore.write(app,profile);}
            catch(org.json.JSONException ignored) { }
            // Queue holds and profile migration share the same ownership lock.
            if(changed&&retired)FoodAnalysisJobService.retireRemovedRoute(app);
        }
        if(!prefs.getBoolean(CLEANED,false)&&CLEANING.compareAndSet(false,true)){
            new Thread(()->{
                try{removeModelCache(app.getFilesDir());prefs.edit().putBoolean(CLEANED,true).apply();}
                catch(IOException|RuntimeException unavailable){android.util.Log.w("EatRecord","Retired model cleanup deferred: "+unavailable.getClass().getSimpleName());}
                finally{CLEANING.set(false);}
            },"retired-recognition-cleanup").start();
        }
        return changed;
    }
    /** NOFOLLOW traversal removes only this app's exact retired directory, not linked targets. */
    static void removeModelCache(File filesDirectory)throws IOException {
        RetiredRecognitionCache.remove(filesDirectory);
    }
}
