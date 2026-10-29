package com.eatrecord.app;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import org.json.JSONArray;
import org.json.JSONObject;

/** Pure hint policy checks: no preferences, UI, request or diary mutation. */
public final class ManualMealPromptsTest {
    private static int checks;
    private static void check(boolean value,String name){checks++;if(!value)throw new AssertionError(name);}
    private static void put(JSONObject days,String date,String key,JSONObject meal)throws Exception{
        JSONObject day=days.optJSONObject(date);if(day==null){day=new JSONObject();days.put(date,day);}day.put(key,meal);
    }
    public static void main(String[] args)throws Exception{
        JSONObject days=new JSONObject();String date="2026-10-22";
        check(ManualMealPrompts.skippedStreak(days,date,"breakfast")==0,"empty dates are not invented absences");
        put(days,"2026-10-21","breakfast",new JSONObject().put("skipped",true));
        check(ManualMealPrompts.skippedStreak(days,date,"breakfast")==1,"previous saved absence counts before recording");
        put(days,"2026-10-20","breakfast",new JSONObject().put("skipped",true));
        put(days,"2026-10-18","breakfast",new JSONObject().put("skipped",true));
        check(ManualMealPrompts.skippedStreak(days,date,"breakfast")==2,"missing date breaks the absence chain");
        put(days,date,"breakfast",new JSONObject().put("skipped",true));
        check(ManualMealPrompts.skippedStreak(days,date,"breakfast")==3,"explicit current absence joins prior chain");
        put(days,date,"breakfast",new JSONObject().put("text","豆浆"));
        check(ManualMealPrompts.skippedStreak(days,date,"breakfast")==0,"editing a recorded meal does not revive a prior absence");
        check("".equals(ManualMealPrompts.absenceGroup(0)),"zero absence uses normal meal hints");
        check("one".equals(ManualMealPrompts.absenceGroup(1)),"one absence group");
        check("two".equals(ManualMealPrompts.absenceGroup(2)),"two absence group");
        check("three".equals(ManualMealPrompts.absenceGroup(3)),"three absence group");
        check("many".equals(ManualMealPrompts.absenceGroup(4)),"four and later absence group");
        check(ManualMealPrompts.mealHintIndex("dinner")==2,"dinner uses dinner pool, not afternoon tea");
        check(ManualMealPrompts.mealHintIndex("afternoon_tea")==3,"afternoon tea uses its own legacy pool index");
        check(ManualMealPrompts.mealHintIndex("snack")==4,"snack retains independent late-night pool");
        check("B".equals(ManualMealPrompts.choose(Arrays.asList("A","B"),"A",new Random(1))),"same absence group avoids immediately repeating last prompt");
        HashSet<String> varied=new HashSet<>();Random random=new Random(7);
        for(int i=0;i<30;i++){String value=ManualMealPrompts.choose(Arrays.asList("A","B","C"),"A",random);check(!"A".equals(value),"last phrase excluded");varied.add(value);}
        check(varied.size()==2,"equal counts still choose randomly among alternatives");
        check("A".equals(ManualMealPrompts.choose(Arrays.asList("A","A"),"A",new Random(1))),"single unique phrase remains valid");
        check(!ManualMealPrompts.choose(Arrays.asList(" ",null),null,new Random()).isEmpty(),"empty custom pool has safe display-only fallback");
        for(String day:new String[]{"2026-10-19","2026-10-20","2026-10-21"})put(days,day,"snack",new JSONObject().put("text","热牛奶"));
        check(ManualMealPrompts.snackRecordedStreak(days,date)==3,"three previous real night meals do not claim a fourth recorded one");
        JSONObject snack=ManualMealText.updatedMeal(null,"小点心");put(days,date,"snack",snack);
        check(ManualMealPrompts.snackRecordedStreak(days,date)==4,"fourth saved night meal crosses the gentle reminder threshold");
        String before=days.toString();
        ManualMealPrompts.skippedStreak(days,date,"lunch");ManualMealPrompts.snackRecordedStreak(days,date);
        check(before.equals(days.toString()),"reading prompt statistics never changes saved records");
        JSONObject day=new JSONObject().put("snack",snack);
        check(DayFoodEntries.collect(day).size()==1&&"夜".equals(DayFoodEntries.collect(day).get(0).prefix),"manual night meal uses the existing night prefix");
        put(days,"2026-10-20","snack",new JSONObject().put("skipped",true));
        check(ManualMealPrompts.snackRecordedStreak(days,date)==2,"night meal absence stops its actual-record streak");
        put(days,date,"snack",new JSONObject().put("emoji","😀"));
        check(ManualMealPrompts.snackRecordedStreak(days,date)==1,"emoji alone does not count as eaten night food");
        check(ManualMealPrompts.skippedStreak(days,"bad-date","lunch")==0,"invalid date cannot manufacture absence statistics");
        System.out.println("PASS: "+checks+" manual meal prompt policy checks");
    }
}
