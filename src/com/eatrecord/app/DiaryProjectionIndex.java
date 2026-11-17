package com.eatrecord.app;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Read-only, incrementally rebuilt day projections. Raw day slices avoid reparsing unchanged meals. */
final class DiaryProjectionIndex {
    private static final class Entry {
        final String raw;
        final int meals, calories;
        final boolean recorded;
        Entry(String raw, JSONObject day) {
            this.raw=raw;
            meals=mealCount(day);
            calories=NutritionStore.displayDayCalories(day);
            recorded=meals>0||hasMood(day)||hasDining(day);
        }
    }
    private final Map<String,Entry> entries=new HashMap<String,Entry>();
    private final TreeSet<String> dates=new TreeSet<String>(Collections.reverseOrder());
    private final Map<String,Integer> calories=new HashMap<String,Integer>();
    private String previous;
    private int parsedDays;

    void update(String source) {
        String raw=source==null?"{}":source;
        if(raw.equals(previous))return;
        Map<String,int[]> slices=daySlices(raw);
        if(slices==null){raw="{}";slices=Collections.emptyMap();}
        Set<String> removed=new HashSet<String>(entries.keySet());
        for(Map.Entry<String,int[]> item:slices.entrySet()){
            String date=item.getKey();int[] range=item.getValue();removed.remove(date);
            Entry old=entries.get(date);
            if(old!=null&&old.raw.length()==range[1]-range[0]&&raw.regionMatches(range[0],old.raw,0,old.raw.length()))continue;
            String dayRaw=raw.substring(range[0],range[1]);JSONObject day;
            try{day=new JSONObject(dayRaw);}catch(Exception invalid){day=new JSONObject();}
            Entry next=new Entry(dayRaw,day);entries.put(date,next);parsedDays++;
            if(next.recorded)dates.add(date);else dates.remove(date);
            if(next.calories>0)calories.put(date,next.calories);else calories.remove(date);
        }
        for(String date:removed){entries.remove(date);dates.remove(date);calories.remove(date);}
        previous=raw;
    }
    List<String> recordedDates(){return new ArrayList<String>(dates);}
    HashMap<String,Integer> calorieDays(){return new HashMap<String,Integer>(calories);}
    int mealCount(String date){Entry entry=entries.get(date);return entry==null?0:entry.meals;}
    int parsedDayCount(){return parsedDays;}

    static int mealCount(JSONObject day){
        if(day==null)return 0;int count=0;
        for(String key:NutritionStore.MEALS){
            JSONObject meal=day.optJSONObject(key);if(meal==null)continue;
            JSONArray photos=meal.optJSONArray("photos"),stickers=meal.optJSONArray("stickers");
            if(!meal.optString("text","").trim().isEmpty()||meal.optBoolean("skipped")
                    ||photos!=null&&photos.length()>0||stickers!=null&&stickers.length()>0)count++;
        }
        return count;
    }
    private static boolean hasMood(JSONObject day){
        JSONArray moods=day.optJSONArray("moods");
        if(moods==null)return !day.optString("mood","").isEmpty();
        for(int i=0;i<Math.min(2,moods.length());i++)if(!moods.optString(i).isEmpty())return true;
        return false;
    }
    private static boolean nonempty(JSONArray values){
        if(values!=null)for(int i=0;i<values.length();i++)if(!values.optString(i).isEmpty())return true;
        return false;
    }
    private static boolean hasDining(JSONObject day){
        JSONObject byMeal=day.optJSONObject("mealDiningMethods");
        if(byMeal==null||byMeal.length()==0)return nonempty(day.optJSONArray("diningMethods"));
        for(String key:NutritionStore.MEALS)if(nonempty(byMeal.optJSONArray(key)))return true;
        return false;
    }
    /** Scan one JSON object, respecting quoted braces, escaped keys and nested arrays/objects. */
    private static Map<String,int[]> daySlices(String raw){
        try{
            Map<String,int[]> out=new HashMap<String,int[]>();int cursor=space(raw,0);
            if(cursor>=raw.length()||raw.charAt(cursor++)!='{')return null;
            cursor=space(raw,cursor);
            while(cursor<raw.length()&&raw.charAt(cursor)!='}'){
                int keyStart=cursor,keyEnd=quotedEnd(raw,keyStart);
                String key=(String)new JSONTokener(raw.substring(keyStart,keyEnd)).nextValue();
                cursor=space(raw,keyEnd);if(raw.charAt(cursor++)!=':')return null;
                int start=space(raw,cursor),end=valueEnd(raw,start);out.put(key,new int[]{start,end});
                cursor=space(raw,end);if(raw.charAt(cursor)==',')cursor=space(raw,cursor+1);else break;
            }
            if(cursor>=raw.length()||raw.charAt(cursor)!='}'||space(raw,cursor+1)!=raw.length())return null;
            return out;
        }catch(Exception invalid){return null;}
    }
    private static int space(String raw,int at){while(at<raw.length()&&Character.isWhitespace(raw.charAt(at)))at++;return at;}
    private static int quotedEnd(String raw,int at){
        if(raw.charAt(at++)!='"')throw new IllegalArgumentException();
        while(at<raw.length()){
            char ch=raw.charAt(at++);if(ch=='"')return at;if(ch=='\\')at++;
        }
        throw new IllegalArgumentException();
    }
    private static int valueEnd(String raw,int at){
        if(raw.charAt(at)=='"')return quotedEnd(raw,at);
        if(raw.charAt(at)!='{'&&raw.charAt(at)!='['){int end=at;while(end<raw.length()&&raw.charAt(end)!=','&&raw.charAt(end)!='}')end++;return end;}
        int depth=0;
        for(int i=at;i<raw.length();i++){
            char ch=raw.charAt(i);
            if(ch=='"'){i=quotedEnd(raw,i)-1;continue;}
            if(ch=='{'||ch=='[')depth++;
            if((ch=='}'||ch==']')&&--depth==0)return i+1;
        }
        throw new IllegalArgumentException();
    }
}
