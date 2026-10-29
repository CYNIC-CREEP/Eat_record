package com.eatrecord.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Read-only display text: no image counts as food, and no invented ingredient amounts/model identity. */
public final class FoodRecordText {
    private FoodRecordText(){}
    public static boolean hasMeal(JSONObject meal){
        if(meal==null||meal.optBoolean("skipped"))return false;
        if(!meal.optString("text").trim().isEmpty())return true;
        for(String key:new String[]{"photos","stickers","foodItems"}){JSONArray a=meal.optJSONArray(key);if(a!=null&&a.length()>0)return true;}
        return false;
    }
    public static String foodNames(JSONObject meal){
        Set<String> names=new LinkedHashSet<>();JSONArray items=meal==null?null:meal.optJSONArray("foodItems");
        for(int i=0;items!=null&&i<items.length();i++){
            JSONObject item=items.optJSONObject(i);if(item==null)continue;
            String name=item.optString("dishName").replaceAll("[\\p{Cntrl}\\p{Cf}]","").trim();
            if(!name.isEmpty()&&!"这份美味".equals(name)&&!"食物记录".equals(name))names.add(name);
        }
        return String.join("、",names);
    }
    public static String description(JSONObject meal){
        if(meal==null)return "";
        String names=foodNames(meal),text=meal.optString("text").trim();
        if(names.isEmpty())return text;
        if(text.isEmpty()||text.equals(names))return names;
        return names+"（"+text+"）";
    }
    public static String story(String period,JSONObject meal){
        String text=description(meal);
        return text.isEmpty()?period+"的餐食已记录，待补充名称":period+"吃了"+text;
    }
    private static boolean uncertainty(String text){
        String lower=text.toLowerCase(Locale.ROOT);
        for(String word:new String[]{"无法","不能确定","不能判断","难以","不确定","不清楚","看不出","无法判断","unknown","uncertain","cannot","can't","unable","not possible"})if(lower.contains(word))return true;
        return false;
    }
    public static String estimateNotes(JSONObject item){
        StringBuilder out=new StringBuilder();Set<String> notes=new LinkedHashSet<>();
        JSONArray assumptions=item.optBoolean("userEdited")?null:item.optJSONArray("assumptions");
        for(int i=0;assumptions!=null&&i<assumptions.length();i++){
            // Only present substantive model assumptions; keep "按/假设/约" rather than turning estimates into facts.
            for(String clause:assumptions.optString(i).split("[。；;\\n]")){
                String note=clause.replaceFirst("^[\\s•·\\-]+","").trim();
                if(!note.isEmpty()&&!uncertainty(note))notes.add(note);
            }
        }
        if(notes.isEmpty()){
            JSONArray components=item.optBoolean("userEdited")?null:item.optJSONArray("components");
            for(int i=0;components!=null&&i<components.length();i++){
                JSONObject part=components.optJSONObject(i);if(part==null)continue;
                double grams=part.optDouble("estimated_grams",Double.NaN);
                if(!part.optString("name").isEmpty()&&Double.isFinite(grams)&&grams>0)notes.add(part.optString("name")+"：按约"+number(grams)+"g估算");
            }
            if(notes.isEmpty()&&item.optDouble("estimatedGrams")>0)notes.add("份量按约"+number(item.optDouble("estimatedGrams"))+"g估算");
            JSONObject nutrients=item.optJSONObject("nutrients");
            if(nutrients!=null)for(String[] field:new String[][]{{"carbsG","碳水","g"},{"proteinG","蛋白质","g"},{"fatG","脂肪","g"},{"fiberG","膳食纤维","g"},{"sodiumMg","钠","mg"}}){
                double value=nutrients.optDouble(field[0],Double.NaN);if(Double.isFinite(value)&&value>=0)notes.add(field[1]+"估算约"+number(value)+field[2]);
            }
        }
        for(String note:notes){if(out.length()>0)out.append('\n');out.append("• ").append(note);}
        if(out.length()==0)out.append("暂无已保存的食材含量估算。");
        if(item.optBoolean("userEdited"))out.append("\n\n当前数据含手动修改。");
        out.append("\n\n").append(provenance(item));
        return out.toString();
    }
    public static String provenance(JSONObject item){
        String model=modelName(item.optString("providerModel"));
        if("offline".equals(item.optString("recognitionSource"))){
            if(item.has("offlineNutritionSource"))return (item.optBoolean("userEdited")?"原始":"")+"菜名和份量由"+(model.isEmpty()?"此前使用的模型":model)+"识别；"+(item.optBoolean("userEdited")?"原始":"")+"营养按USDA FoodData Central SR Legacy（2018-04）食物参考值折算。照片无法测量实际重量、油盐和隐藏食材，未识别食材不计入，请核对。";
            return "历史基础估算：照片无法准确测量重量、油盐及隐藏食材，请核对。\n"+(model.isEmpty()?"此记录未保留模型名称，数据仅供参考。":"数据由"+model+"生成，仅供参考。");
        }
        return model.isEmpty()?"此记录未保留模型名称，数据仅供参考。":"数据由"+model+"生成，仅供参考。";
    }
    public static String modelName(String model){
        if(model==null)return "";
        String name=model.replaceAll("[\\p{Cntrl}\\p{Cf}]","").trim();
        // Model IDs must not contain an endpoint, key or arbitrary provider response.
        if(name.length()>160||name.contains("://")||name.startsWith("sk-")||name.toLowerCase(Locale.ROOT).startsWith("bearer "))return "";
        return name;
    }
    private static String number(double value){return Math.abs(value-Math.rint(value))<.05?Long.toString(Math.round(value)):String.format(Locale.ROOT,"%.1f",value);}
}
