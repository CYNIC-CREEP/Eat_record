package com.eatrecord.app;

import org.json.JSONArray;
import org.json.JSONObject;

public final class ManualMealTextTest {
    private static int checks;
    private static void check(boolean okay,String message) { checks++;if(!okay)throw new AssertionError(message); }
    public static void main(String[] args)throws Exception {
        check(!AppearanceMode.isNight(null,true),"missing profile stays light");
        check(AppearanceMode.isNight(new JSONObject().put("decoration","night"),false),"old dark theme preserved");
        check(AppearanceMode.isNight(new JSONObject().put("followSystemNight",true),true),"old follow preference preserved");
        check(!AppearanceMode.isNight(new JSONObject().put("followSystemNight",true),false),"old follow in light system");
        check(AppearanceMode.isNight(new JSONObject().put("nightMode",true),false),"manual night on light system");
        check(!AppearanceMode.isNight(new JSONObject().put("nightMode",false).put("decoration","night").put("followSystemNight",true),true),"manual light wins over old dark flags");
        check(AppearanceMode.isNight(new JSONObject().put("nightMode","bad").put("decoration","night"),false),"invalid explicit flag uses prior appearance");
        check(ManualMealText.normalize(null)==null,"null name");
        check(ManualMealText.normalize(" \n\t ")==null,"empty name");
        check(ManualMealText.normalize("　　")==null,"full-width empty name");
        check("青菜汤面".equals(ManualMealText.normalize("  青菜汤面  ")),"trim name");
        check("米饭\n鸡蛋".equals(ManualMealText.normalize("米饭\r\n鸡蛋")),"multiline names");
        check(ManualMealText.normalize(new String(new char[2001]).replace('\0','饭'))==null,"bounded input");
        JSONObject meal=ManualMealText.updatedMeal(null,"煎蛋汤面");
        check(meal!=null&&"煎蛋汤面".equals(meal.getString("text"))&&meal.getBoolean("manualTextEntry"),"manual text saved");
        check(!meal.has("photos")&&!meal.has("stickers")&&!meal.has("foodItems")&&!meal.has("nutrients"),"no invented images or nutrients");
        check(FoodRecordText.hasMeal(meal)&&FoodRecordText.story("早上",meal).contains("煎蛋汤面"),"notebook reads existing text");
        JSONObject day=new JSONObject().put("breakfast",meal);
        check(DayFoodEntries.collect(day).size()==1&&DayFoodEntries.collect(day).get(0).fileName.isEmpty(),"text-only home entry");
        check(DayFoodEntries.collect(day).get(0).isText()&&"煎蛋汤面".equals(DayFoodEntries.collect(day).get(0).textValue),"text affordance owns exact saved text");
        JSONObject multiline=new JSONObject().put("text","凉面\n煎蛋").put("manualTextEntry",true);
        DayFoodEntries.Entry line=DayFoodEntries.collect(new JSONObject().put("lunch",multiline)).get(0);
        check("凉面 煎蛋".equals(line.title)&&"凉面\n煎蛋".equals(line.textValue),"display normalization retains deletion ownership text");
        check(!DayFoodEntries.collect(new JSONObject().put("lunch",new JSONObject().put("skipped",true))).get(0).isText(),"skipped status is not deletable meal text");
        check(!DayFoodEntries.collect(new JSONObject().put("lunch",new JSONObject().put("manualCalories",50))).get(0).isText(),"manual calorie record is not a text entry");
        JSONObject photo=new JSONObject().put("dishName","炒饭").put("sourcePhoto","photo.jpg").put("id","food-1");
        meal.put("foodItems",new JSONArray().put(photo)).put("photos",new JSONArray().put("photo.jpg")).put("skipped",true);
        String original=meal.toString();
        JSONObject updated=ManualMealText.updatedMeal(meal,"豆浆和包子");
        check(original.equals(meal.toString()),"editing does not mutate caller before save");
        check(!updated.optBoolean("skipped")&&updated.getJSONArray("foodItems").toString().equals(meal.getJSONArray("foodItems").toString()),"unskip without losing recognized food");
        check(updated.getJSONArray("photos").toString().equals(meal.getJSONArray("photos").toString()),"old photo preserved");
        day.put("breakfast",updated);
        check(DayFoodEntries.collect(day).size()==2&&DayFoodEntries.collect(day).get(1).title.equals("豆浆和包子"),"manual name coexists with recognized image");
        check(NutritionStore.totals(day).getInt("count")==0,"unknown nutrition is not measured");
        check(ManualMealText.updatedMeal(updated," ")==null&&original.equals(meal.toString()),"invalid edit has no mutation");
        JSONObject fresh=new JSONObject(updated.toString()).put("calories",123).put("workerVersion",2);
        JSONObject inline=ManualMealText.updatedInlineMeal(fresh," 青菜面\n煎蛋 ");
        check("青菜面\n煎蛋".equals(inline.optString("text")),"inline multi-line input normalized");
        check(inline.getJSONArray("foodItems").toString().equals(fresh.getJSONArray("foodItems").toString())&&inline.optInt("workerVersion")==2,"inline update retains fresh worker fields");
        JSONObject cleared=ManualMealText.updatedInlineMeal(fresh,"　 \n");
        check(!cleared.has("text")&&!cleared.has("manualTextEntry")&&cleared.getJSONArray("photos").length()==1&&cleared.optInt("calories")==123,"clear only text, retain media/nutrition");
        check(ManualMealText.updatedInlineMeal(null,"").length()==0,"blank inline editor has no fabricated record fields");
        check(ManualMealText.updatedInlineMeal(fresh,new String(new char[2001]).replace('\0','饭'))==null,"inline over-limit input rejected");
        check(fresh.has("text")&&fresh.optInt("workerVersion")==2,"inline clear does not mutate caller");
        updated.remove("manualTextEntry");
        check(DayFoodEntries.collect(new JSONObject().put("breakfast",updated)).size()==1,"old image notes stay in details");
        System.out.println("ManualMealTextTest: "+checks+" passed");
    }
}
