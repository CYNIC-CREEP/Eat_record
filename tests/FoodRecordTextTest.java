package com.eatrecord.app;

import org.json.*;

public final class FoodRecordTextTest {
    private static int checks;
    private static void check(boolean passed,String name){if(!passed)throw new AssertionError(name);checks++;}
    public static void main(String[] args)throws Exception {
        JSONObject item=new JSONObject().put("dishName","鸡蛋炒饭").put("estimatedGrams",325).put("providerModel","qa-served-model")
            .put("nutrients",new JSONObject().put("carbsG",72).put("proteinG",14).put("fatG",15).put("sodiumMg",650))
            .put("assumptions",new JSONArray().put("按中盘炒饭约325克估算").put("假设用油约15克；照片无法判断隐藏用油").put("肉丁按瘦猪肉或牛肉混合估算"));
        JSONObject meal=new JSONObject().put("foodItems",new JSONArray().put(item).put(new JSONObject().put("dishName","炒面")));
        String before=meal.toString();
        check(FoodRecordText.hasMeal(meal),"foodItems-only meal is recorded");
        check("鸡蛋炒饭、炒面".equals(FoodRecordText.foodNames(meal)),"all dish names in stable order");
        check("早上吃了鸡蛋炒饭、炒面".equals(FoodRecordText.story("早上",meal)),"narrative says food not image counts");
        check(before.equals(meal.toString()),"narrative reads do not migrate diary");
        meal.put("text","旧版文字保留");
        check(FoodRecordText.description(meal).contains("旧版文字保留")&&FoodRecordText.description(meal).startsWith("鸡蛋炒饭"),"manual note preserved alongside food names");
        meal.put("skipped",true);check(!FoodRecordText.hasMeal(meal),"skipped meal excluded");meal.remove("skipped");
        JSONObject legacy=new JSONObject().put("photos",new JSONArray().put("legacy.jpg"));
        check(FoodRecordText.hasMeal(legacy)&&FoodRecordText.story("中午",legacy).contains("待补充名称"),"unrecognized legacy photos not invented as food");
        check(!FoodRecordText.story("中午",legacy).contains("张图片"),"never says ate images");
        check(!FoodRecordText.hasMeal(new JSONObject()),"empty meal excluded");
        JSONObject placeholder=new JSONObject().put("foodItems",new JSONArray().put(new JSONObject().put("dishName","这份美味")));
        check(FoodRecordText.foodNames(placeholder).isEmpty(),"placeholder not presented as recognized dish");
        String notes=FoodRecordText.estimateNotes(item);
        check(notes.contains("约325克")&&notes.contains("假设用油约15克"),"quantitative assumptions retained as estimates");
        check(!notes.contains("无法")&&!notes.contains("不确定"),"generic uncertainty prose removed from this panel");
        check(notes.endsWith("数据由qa-served-model生成，仅供参考。"),"footer uses recorded model identity");
        check(item.getJSONArray("assumptions").getString(1).contains("无法"),"display filtering never destroys original assumptions");
        item.put("userEdited",true);notes=FoodRecordText.estimateNotes(item);
        check(notes.contains("含手动修改")&&!notes.contains("用油约15克"),"manual values not mixed with stale ingredient quantities");
        check(notes.contains("碳水估算约72g")&&notes.contains("钠估算约650mg"),"fallback uses saved nutrient amounts and correct units");
        item.remove("providerModel");check(FoodRecordText.estimateNotes(item).contains("未保留模型名称"),"historical model identity not fabricated from current settings");
        check(FoodRecordText.modelName("sk-private-value").isEmpty()&&FoodRecordText.modelName("https://api.invalid").isEmpty(),"no key or endpoint as model label");
        FoodAnalysisResult result=FoodAnalysisResult.parse("{\"schema_version\":1,\"dish_name\":\"鸡蛋\",\"items\":[{\"name\":\"鸡蛋\",\"estimated_grams\":50,\"calories_kcal\":70,\"carbs_g\":1,\"protein_g\":6,\"fat_g\":5,\"fiber_g\":0,\"sodium_mg\":60,\"confidence\":0.8}],\"totals\":{\"calories_kcal\":70,\"carbs_g\":1,\"protein_g\":6,\"fat_g\":5,\"fiber_g\":0,\"sodium_mg\":60},\"calorie_range\":[50,90],\"assumptions\":[],\"confidence\":0.8}");
        FoodAnalysisClient.withModel(result,"returned-model","configured-alias");
        check("returned-model".equals(result.value.getString("providerModel"))&&"response".equals(result.value.getString("providerModelSource")),"server-returned model wins over alias");
        FoodAnalysisClient.withModel(result,"","configured-alias");
        check("configured-alias".equals(result.value.getString("providerModel"))&&"request".equals(result.value.getString("providerModelSource")),"compatible provider with no model uses request snapshot");
        check(FoodAnalysisClient.prompt().contains("真实保留confidence和calorie_range"),"concise prompt retains uncertainty metadata");
        JSONObject original=new JSONObject().put("id","stable").put("analysisId","old-generation").put("sourcePhoto","photo.jpg").put("cutoutFile","alpha.png")
            .put("cutoutRect",new JSONArray().put(1).put(2).put(300).put(400)).put("displayRotation",90).put("nutrients",new JSONObject().put("caloriesKcal",700));
        String stable=original.toString();JSONObject corrected=FoodNameHint.reanalysis(original,"  鸡蛋青菜汤面  ");
        check("鸡蛋青菜汤面".equals(corrected.getString("dishNameHint"))&&corrected.getBoolean("dishNameUserEdited"),"corrected name is a durable analysis input");
        check(!corrected.has("nutrients")&&!corrected.optBoolean("userEdited")&&"pending".equals(corrected.optString("status")),"old nutrients hidden while recalculating, not mislabelled manual nutrition");
        check("alpha.png".equals(corrected.getString("cutoutFile"))&&corrected.getInt("displayRotation")==90&&corrected.getJSONArray("cutoutRect").length()==4,"rename preserves source aligned alpha and angle");
        check(!"old-generation".equals(corrected.getString("analysisId"))&&"stable".equals(corrected.getString("id")),"generation changes without duplicating food identity");
        check(stable.equals(original.toString()),"rename helper does not mutate caller snapshot");
        check(FoodNameHint.prompt("汤面").contains(JSONObject.quote("汤面"))&&FoodNameHint.prompt("").isEmpty(),"name hint is quoted data, default prompt stays unchanged");
        check(FoodNameHint.normalize("\n 汤面\u200b ").equals("汤面")&&FoodNameHint.normalize(String.join("",java.util.Collections.nCopies(101,"面"))).isEmpty(),"corrected name length and invisible characters validated");
        JSONObject local=new JSONObject().put("recognitionSource","offline").put("providerModel","Qwen3.5-2B").put("offlineNutritionSource","USDA");
        check(FoodRecordText.provenance(local).contains("USDA")&&FoodRecordText.provenance(local).contains("折算")&&!FoodRecordText.provenance(local).contains("数据由Qwen3.5-2B生成"),"offline distinguishes generated ingredients from reference nutrients");
        local.put("userEdited",true);check(FoodRecordText.provenance(local).contains("原始营养"),"manual corrections are not falsely attributed to catalog arithmetic");
        local.remove("offlineNutritionSource");check(FoodRecordText.provenance(local).contains("数据由Qwen3.5-2B生成"),"historical experimental result never falsely claims catalog provenance");
        System.out.println("PASS: "+checks+" notebook and estimate display checks");
    }
}
