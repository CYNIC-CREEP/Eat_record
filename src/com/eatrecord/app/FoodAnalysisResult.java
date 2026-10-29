package com.eatrecord.app;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Validated, versioned result. No raw provider response is persisted. */
public final class FoodAnalysisResult {
    public final JSONObject value;
    public static final String[] WIRE = {"calories_kcal", "carbs_g", "protein_g", "fat_g", "fiber_g", "sodium_mg"};
    public static final String[] LOCAL = {"caloriesKcal", "carbsG", "proteinG", "fatG", "fiberG", "sodiumMg"};
    private FoodAnalysisResult(JSONObject value) { this.value = value; }

    public static FoodAnalysisResult parse(String text) throws JSONException {
        if (text == null || text.length() > 65536) throw new JSONException("识别结果过大或为空");
        int start = text.indexOf('{'), end = text.lastIndexOf('}');
        if (start < 0 || end <= start) throw new JSONException("模型没有返回有效 JSON");
        JSONObject root = new JSONObject(text.substring(start, end + 1));
        if (root.optInt("schema_version") != 1) throw new JSONException("不支持的识别结果版本");
        String name = root.getString("dish_name").trim();
        if (name.length() == 0 || name.length() > 100) throw new JSONException("菜名无效");
        JSONArray items = root.getJSONArray("items");
        if (items.length() < 1 || items.length() > 30) throw new JSONException("没有识别到可记录的食物");
        double grams = 0;
        double[] sums = new double[WIRE.length];
        JSONArray cleanItems = new JSONArray();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i), clean = new JSONObject();
            String itemName = item.getString("name").trim();
            if (itemName.length() == 0 || itemName.length() > 100) throw new JSONException("食物名称无效");
            clean.put("name", itemName);
            double weight = number(item, "estimated_grams", 0.1, 10000);
            grams += weight;
            clean.put("estimated_grams", weight);
            clean.put("confidence", number(item, "confidence", 0, 1));
            for (int j = 0; j < WIRE.length; j++) {
                double n = number(item, WIRE[j], 0, j == 5 ? 100000 : (j == 0 ? 20000 : 5000));
                sums[j] += n;
                clean.put(WIRE[j], n);
            }
            cleanItems.put(clean);
        }
        JSONObject total = root.getJSONObject("totals"), nutrients = new JSONObject();
        for (int j = 0; j < WIRE.length; j++) {
            double n = number(total, WIRE[j], 0, j == 5 ? 100000 : (j == 0 ? 20000 : 5000));
            if (Math.abs(n - sums[j]) > Math.max(j == 0 ? 20 : 2, sums[j] * .12))
                throw new JSONException("食物明细与营养总量不一致");
            nutrients.put(LOCAL[j], Math.round(sums[j] * 10) / 10.0);
        }
        if (grams > 10000) throw new JSONException("份量超出有效范围");
        double energy = sums[1] * 4 + sums[2] * 4 + sums[3] * 9;
        if (sums[0] > 30 && Math.abs(sums[0] - energy) > Math.max(80, sums[0] * .4))
            throw new JSONException("热量与宏量营养素不一致");
        JSONArray range = root.getJSONArray("calorie_range");
        if (range.length() != 2) throw new JSONException("缺少热量估算范围");
        double low = range.getDouble(0), high = range.getDouble(1);
        if (!finite(low) || !finite(high) || low < 0 || high > 30000 || low > sums[0] || high < sums[0])
            throw new JSONException("热量估算范围无效");
        JSONArray assumptions = root.getJSONArray("assumptions");
        JSONArray cleanAssumptions = new JSONArray();
        for (int i = 0; i < Math.min(8, assumptions.length()); i++) {
            String s = assumptions.getString(i);
            if (s.length() > 250) throw new JSONException("估算说明过长");
            cleanAssumptions.put(s);
        }
        JSONObject out = new JSONObject();
        out.put("dishName", name);
        out.put("estimatedGrams", Math.round(grams));
        out.put("nutrients", nutrients);
        out.put("components", cleanItems);
        out.put("calorieLow", Math.round(low));
        out.put("calorieHigh", Math.round(high));
        out.put("confidence", number(root, "confidence", 0, 1));
        out.put("assumptions", cleanAssumptions);
        out.put("schemaVersion", 1);
        return new FoodAnalysisResult(out);
    }

    static double number(JSONObject o, String key, double min, double max) throws JSONException {
        Object raw = o.get(key);
        if (!(raw instanceof Number)) throw new JSONException("营养值必须是数值: " + key);
        double n = ((Number) raw).doubleValue();
        if (!finite(n) || n < min || n > max) throw new JSONException("营养值超出范围: " + key);
        return n;
    }
    static boolean finite(double n) { return !Double.isNaN(n) && !Double.isInfinite(n); }

    static String validationMessage(JSONException error){
        String reason=error==null?"":String.valueOf(error.getMessage());
        if(reason.contains("不一致"))return "本次模型返回的营养明细与总量不一致，未保存营养数据；请重试，不需要重新配置 API";
        if(reason.contains("范围")||reason.contains("数值"))return "本次模型返回的营养数值不符合校验要求，未保存；请重试";
        return "本次模型输出为空、缺字段或不是有效 JSON，未保存营养数据；请重试，不代表 API 配置失效";
    }

    /** Local tips use the already validated result; no second model request or fabricated attribution. */
    public static String tip(JSONObject item) {
        if(item==null)return "";
        JSONObject n=item.optJSONObject("nutrients");if(n==null)return "";
        double protein=n.optDouble("proteinG",Double.NaN),fiber=n.optDouble("fiberG",Double.NaN),sodium=n.optDouble("sodiumMg",Double.NaN);
        if(!finite(protein)||!finite(fiber)||!finite(sodium)||protein<0||fiber<0||sodium<0)return "";
        String name=item.optString("dishName"),lead="这份餐食";
        if(name.contains("汤")||name.contains("面"))lead="这一碗";
        else if(name.contains("饭")||name.contains("炒"))lead="这一盘";
        String intro=lead+"估算含蛋白质约"+display(protein)+"g、膳食纤维约"+display(fiber)+"g。";
        if(sodium>=1000)return intro+(name.contains("汤")?"尝过汤底就好，剩下的汤汁可以少喝一些；下一餐清淡搭配更舒服。":"搭配清淡的蔬菜和白水，让这一餐吃得更清爽。");
        if(protein<10)return intro+"可以搭配鸡蛋、豆腐或其他喜欢的蛋白质食物，组合更丰富。";
        if(fiber<3)return intro+"顺手搭一份蔬菜或水果，为这一餐添点颜色和口感。";
        return intro+"主食、蛋白质食物与蔬菜轮换搭配，慢慢吃，享受这一份美味。";
    }
    private static String display(double value){return Math.abs(value-Math.rint(value))<.05?Long.toString(Math.round(value)):String.format(java.util.Locale.ROOT,"%.1f",value);}
}
