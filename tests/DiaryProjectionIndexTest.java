package com.eatrecord.app;

import org.json.JSONArray;
import org.json.JSONObject;

public final class DiaryProjectionIndexTest {
    private static int checks;
    private static void check(boolean okay,String message){checks++;if(!okay)throw new AssertionError(message);}
    public static void main(String[] arguments)throws Exception{
        DiaryProjectionIndex index=new DiaryProjectionIndex();
        JSONObject meal=new JSONObject().put("text","饭 { 菜 } \"引号\" [数组] \\尾巴");
        JSONObject day=new JSONObject().put("lunch",meal).put("calories",450);
        JSONObject data=new JSONObject().put("2026-11-15",day).put("2026-11-14",new JSONObject().put("mood","开心"));
        index.update(data.toString());
        check(index.recordedDates().size()==2&&index.recordedDates().get(0).equals("2026-11-15"),"descending dates and nested quoted braces");
        check(index.mealCount("2026-11-15")==1&&index.calorieDays().get("2026-11-15")==450,"legacy meal/calorie projections");
        int parsed=index.parsedDayCount();index.update(data.toString());
        check(index.parsedDayCount()==parsed,"unchanged generation parses no days");
        day.put("calories",700);index.update(data.toString());
        check(index.parsedDayCount()==parsed+1&&index.calorieDays().get("2026-11-15")==700,"one-day change recomputes one projection");
        data.remove("2026-11-15");index.update(data.toString());
        check(index.recordedDates().size()==1&&!index.calorieDays().containsKey("2026-11-15"),"removed day leaves all projections");
        index.update("{\"2026\\u002d11\\u002d10\":{\"dinner\":{\"text\":\"粥\"}}}");
        check(index.recordedDates().get(0).equals("2026-11-10"),"escaped object key follows JSON semantics");
        JSONObject manual=new JSONObject().put("manualCaloriesOverride",true).put("manualCaloriesByMeal",true)
                .put("breakfast",new JSONObject().put("manualCalories",150)).put("dinner",new JSONObject().put("manualCalories",900).put("skipped",true));
        index.update(new JSONObject().put("2026-11-02",manual).toString());
        check(index.calorieDays().get("2026-11-02")==150,"shared nutrition arithmetic respects skipped/manual meals");
        JSONObject legacy=new JSONObject().put("moods",new JSONArray().put("").put("").put("ignored third mood"));
        data=new JSONObject().put("a",legacy).put("b",new JSONObject().put("diningMethods",new JSONArray().put("外卖")))
                .put("c",new JSONObject().put("mealDiningMethods",new JSONObject().put("snack",new JSONArray().put("堂食"))))
                .put("d",new JSONObject().put("dinner",new JSONObject().put("stickers",new JSONArray().put("legacy.png"))))
                .put("e",new JSONObject().put("breakfast",new JSONObject().put("skipped",true)));
        index.update(data.toString());
        check(index.recordedDates().size()==4&&!index.recordedDates().contains("a"),"mood/dining/sticker/skipped legacy semantics");
        data=new JSONObject();
        for(int i=0;i<1825;i++)data.put(String.format(java.util.Locale.ROOT,"date-%04d",i),new JSONObject().put("lunch",new JSONObject().put("text","饭")));
        index.update(data.toString());parsed=index.parsedDayCount();
        check(index.recordedDates().size()==1825,"five-year-sized date index");
        for(int i=0;i<20;i++){index.recordedDates();index.calorieDays();}
        index.update(data.toString());check(index.parsedDayCount()==parsed,"repeated queries and unchanged data reuse all day projections");
        data.getJSONObject("date-0900").put("calories",100);index.update(data.toString());
        check(index.parsedDayCount()==parsed+1&&index.calorieDays().get("date-0900")==100,"large-diary single-day update");
        index.update("{}");check(index.recordedDates().isEmpty()&&index.calorieDays().isEmpty(),"whole-diary replacement clears cache");
        index.update("{bad");check(index.recordedDates().isEmpty(),"invalid persisted source uses empty projection");
        System.out.println("PASS: "+checks+" incremental diary projection checks");
    }
}
