package com.eatrecord.app;

import android.app.Activity;
import android.content.*;
import android.graphics.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Read-only seven-day report. Missing days are gaps, never zero intake. */
public final class FoodWeeklyActivity extends Activity {
    private final SimpleDateFormat format=new SimpleDateFormat("yyyy-MM-dd",Locale.CHINA);
    private Calendar end;
    private boolean night;
    private JSONObject days;
    private int dp(float n){return PaperComponents.dp(this,n);}
    private TextView text(String value,float size,boolean bold){return PaperComponents.text(this,value,size,UiTokens.ink(night),bold);}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);end=Calendar.getInstance();
        String key=state==null?getIntent().getStringExtra("date"):state.getString("date");
        try{end.setTime(format.parse(key));}catch(Exception ignored){}
        if(format.format(end.getTime()).compareTo(format.format(new Date()))>0)end=Calendar.getInstance();
    }
    @Override protected void onResume(){super.onResume();render();}
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putString("date",format.format(end.getTime()));}
    private void render(){
        SharedPreferences prefs=getSharedPreferences(NutritionStore.PREFS,0);
        days=NutritionStore.read(prefs,NutritionStore.DAYS);JSONObject profile=NutritionStore.read(prefs,NutritionStore.PROFILE);
        UiTokens.configure(profile.optString("decoration","yellow"));
        night=AppearanceMode.isNight(profile,
            (android.content.res.Resources.getSystem().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES);
        setTheme(night?R.style.AppNightTheme:R.style.AppDayTheme);
        getWindow().setStatusBarColor(UiTokens.paper(night));getWindow().setNavigationBarColor(UiTokens.paper(night));
        getWindow().getDecorView().setSystemUiVisibility(night?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(UiTokens.paper(night));
        root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(0,insets.getSystemWindowInsetTop(),0,insets.getSystemWindowInsetBottom());return insets;});
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(12),dp(8),dp(20),dp(8));
        TextView back=text("‹",30,false);back.setGravity(Gravity.CENTER);back.setContentDescription("返回");back.setOnClickListener(v->finish());header.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));
        TextView title=text("这一周的味道",22,true);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));root.addView(header);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout page=new LinearLayout(this);page.setOrientation(1);page.setPadding(dp(24),dp(8),dp(24),dp(32));scroll.addView(page);
        Calendar first=(Calendar)end.clone();first.add(Calendar.DATE,-6);
        LinearLayout range=new LinearLayout(this);range.setGravity(Gravity.CENTER_VERTICAL);
        TextView prev=text("‹",24,false);prev.setGravity(Gravity.CENTER);prev.setContentDescription("前七天");prev.setOnClickListener(v->{end.add(Calendar.DATE,-7);render();});range.addView(prev,new LinearLayout.LayoutParams(dp(48),dp(48)));
        TextView dates=text(format.format(first.getTime()).substring(5)+" — "+format.format(end.getTime()).substring(5),15,true);dates.setGravity(Gravity.CENTER);range.addView(dates,new LinearLayout.LayoutParams(0,-2,1));
        boolean canNext=format.format(end.getTime()).compareTo(format.format(new Date()))<0;
        TextView next=text("›",24,false);next.setGravity(Gravity.CENTER);next.setAlpha(canNext?1:.25f);next.setContentDescription("后七天");next.setEnabled(canNext);
        next.setOnClickListener(v->{end.add(Calendar.DATE,7);if(format.format(end.getTime()).compareTo(format.format(new Date()))>0)end=Calendar.getInstance();render();});range.addView(next,new LinearLayout.LayoutParams(dp(48),dp(48)));page.addView(range);
        int recorded=0,macroDays=0,total=0;double protein=0,carbs=0,fat=0;
        int[] calories=new int[7];boolean[] present=new boolean[7];String[] labels=new String[7];JSONObject[] nutrition=new JSONObject[7];
        for(int i=0;i<7;i++){
            Calendar cursor=(Calendar)first.clone();cursor.add(Calendar.DATE,i);String date=format.format(cursor.getTime());
            JSONObject day=days.optJSONObject(date),n=NutritionStore.totals(day);nutrition[i]=n;labels[i]=date.substring(5);
            present[i]=n.optInt("count")>0||(day!=null&&(day.optBoolean("manualCaloriesOverride")||day.optInt("calories")>0));
            calories[i]=FoodUi.displayCalories(day);
            if(present[i]){recorded++;total+=calories[i];}
            if(n.optInt("count")>0){macroDays++;protein+=n.optDouble("proteinG");carbs+=n.optDouble("carbsG");fat+=n.optDouble("fatG");}
        }
        if(recorded==0){
            LinearLayout empty=new LinearLayout(this);empty.setOrientation(1);empty.setGravity(Gravity.CENTER);empty.setPadding(dp(12),dp(72),dp(12),dp(64));
            TextView heading=text("本周暂无营养数据",20,true);heading.setTag("weekly_empty");empty.addView(heading);
            TextView hint=text("记录餐图并完成营养识别，\n或填写热量后，就能在这里看到分析。",14,false);hint.setTextColor(UiTokens.secondary(night));hint.setGravity(Gravity.CENTER);hint.setPadding(0,dp(14),0,0);empty.addView(hint);page.addView(empty);
        }else{
            LinearLayout summary=PaperComponents.card(this,night);
            summary.addView(text("记录日平均热量",13,false));summary.addView(text(Math.round(total/(double)recorded)+" kcal",30,true));
            summary.addView(text("已记录 "+recorded+" / 7 天 · 无记录日期不计入均值",12,false));page.addView(summary);
            TextView section=text("七日热量趋势",17,true);section.setPadding(0,dp(28),0,dp(14));page.addView(section);
            page.addView(new Trend(this,calories,present,labels),new LinearLayout.LayoutParams(-1,dp(180)));
            if(macroDays>0){
                LinearLayout macros=PaperComponents.card(this,night);macros.addView(text("日均营养素",17,true));
                macros.addView(text("碳水  "+FoodUi.format(carbs/macroDays)+" g\n蛋白质  "+FoodUi.format(protein/macroDays)+" g\n脂肪  "+FoodUi.format(fat/macroDays)+" g",15,false));
                macros.addView(text("仅统计 "+macroDays+" 天含结构化营养素的记录",12,false));page.addView(macros);
            }
            TextView detail=text("每日记录",17,true);detail.setPadding(0,dp(28),0,dp(8));page.addView(detail);
            for(int i=0;i<7;i++){
                LinearLayout line=new LinearLayout(this);line.setGravity(Gravity.CENTER_VERTICAL);line.setPadding(0,dp(12),0,dp(12));
                line.addView(text(labels[i],14,false),new LinearLayout.LayoutParams(0,-2,1));
                TextView number=text(present[i]?calories[i]+" kcal":"未记录",14,present[i]);if(!present[i])number.setTextColor(UiTokens.secondary(night));line.addView(number);page.addView(line);
            }
            TextView note=text("以上仅统计已记录餐食，不能代表全天实际摄入。AI 营养值为估算，手动热量仍优先显示。",12,false);note.setTextColor(UiTokens.secondary(night));note.setPadding(0,dp(20),0,0);page.addView(note);
        }
        setContentView(root);root.requestApplyInsets();
    }
    private final class Trend extends View {
        final int[] values;final boolean[] present;final String[] labels;final Paint paint=new Paint(3);final float max;
        Trend(Context context,int[] values,boolean[] present,String[] labels){
            super(context);this.values=values;this.present=present;this.labels=labels;
            int highest=1;StringBuilder description=new StringBuilder("七日热量趋势。");
            for(int i=0;i<7;i++){highest=Math.max(highest,values[i]);description.append(labels[i]).append(present[i]?"，"+values[i]+"千卡。":"，未记录。");}
            max=highest;setContentDescription(description.toString());setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        }
        @Override public void onDraw(Canvas canvas){
            float cell=getWidth()/7f,bottom=getHeight()-dp(32),height=bottom-dp(22);
            paint.setTextAlign(Paint.Align.CENTER);paint.setTypeface(AppTypography.typeface(getContext(),false));
            for(int i=0;i<7;i++){
                float center=cell*(i+.5f),w=Math.min(dp(24),cell*.55f),h=height*values[i]/max;
                paint.setColor(present[i]?UiTokens.yellow():UiTokens.line(night));
                if(present[i])canvas.drawRoundRect(center-w/2,bottom-Math.max(dp(3),h),center+w/2,bottom,dp(6),dp(6),paint);
                else canvas.drawCircle(center,bottom-dp(2),dp(2),paint);
                paint.setColor(UiTokens.secondary(night));paint.setTextSize(dp(10));canvas.drawText(labels[i].replaceFirst("^0",""),center,bottom+dp(22),paint);
            }
        }
    }
}
