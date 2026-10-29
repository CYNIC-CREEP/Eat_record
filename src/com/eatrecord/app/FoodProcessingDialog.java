package com.eatrecord.app;

import android.animation.ValueAnimator;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;

/** Full-screen review. New imports remain local drafts until the explicit checkmark. */
public final class FoodProcessingDialog implements SharedPreferences.OnSharedPreferenceChangeListener {
    private final Activity activity;
    private final boolean night;
    private final String date,meal;
    private final ArrayList<String> sources;
    private final SharedPreferences prefs;
    private final Runnable setup,closed;
    private final ExecutorService images=Executors.newSingleThreadExecutor();
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final Dialog dialog;
    private final StageView preview;
    private final TextView dish,calories,status,explanation,configure,confirm;
    private final View modify;
    private final LinearLayout macroGrid,selection;
    private final LinearLayout tips;
    private final TextView tipBody,tipNote;
    private final ProgressMark progressMark;
    private final TextView[] macroValues=new TextView[6];
    private ValueAnimator resultAnimator;
    private Runnable settleResult;
    private boolean receiverRegistered;
    private final BroadcastReceiver progressReceiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent intent){if(!disposed)refresh();}};
    private int selected;
    private boolean disposed,resolved;
    private String imageKey="",resultKey="";

    public FoodProcessingDialog(Activity activity,String date,String meal,List<String> sources,boolean night,Runnable setup,Runnable closed){
        this.activity=activity;this.date=date;this.meal=meal;this.sources=new ArrayList<>(sources);this.night=night;this.setup=setup;this.closed=closed;
        prefs=activity.getSharedPreferences(NutritionStore.PREFS,0);
        dialog=new Dialog(activity,night?android.R.style.Theme_Material_NoActionBar:android.R.style.Theme_Material_Light_NoActionBar);
        dialog.setCanceledOnTouchOutside(false);
        LinearLayout root=new LinearLayout(activity);root.setOrientation(1);root.setBackground(new PaperComponents.Paper(activity,night));
        root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(0,insets.getSystemWindowInsetTop(),0,insets.getSystemWindowInsetBottom());return insets;});
        TextView heading=text("添加"+mealTitle(meal),18,true);heading.setPadding(dp(24),dp(14),dp(24),dp(8));root.addView(heading);
        ScrollView scroll=new ScrollView(activity);scroll.setVerticalScrollBarEnabled(false);
        LinearLayout page=new LinearLayout(activity);page.setOrientation(1);page.setPadding(dp(24),0,dp(24),dp(20));scroll.addView(page);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        selection=new LinearLayout(activity);selection.setGravity(Gravity.CENTER);page.addView(selection);buildSelection();
        preview=new StageView(activity);preview.setTag("food_preview");preview.setContentDescription("食物贴纸预览");
        int imageHeight=Math.max(dp(220),Math.min(dp(350),(int)(activity.getResources().getDisplayMetrics().heightPixels*.39f)));
        page.addView(preview,new LinearLayout.LayoutParams(-1,imageHeight));
        dish=text("正在制作食物贴纸…",23,true);dish.setTag("food_dish_name");dish.setGravity(Gravity.CENTER);dish.setPadding(0,dp(12),0,dp(4));dish.setMinHeight(dp(48));dish.setOnClickListener(v->renameFood());dish.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);page.addView(dish);
        calories=text("",28,false);calories.setTag("food_calories");calories.setGravity(Gravity.CENTER);page.addView(calories);
        LinearLayout statusLine=new LinearLayout(activity);statusLine.setGravity(Gravity.CENTER);statusLine.setPadding(0,dp(10),0,dp(12));
        progressMark=new ProgressMark(activity);progressMark.setTag("food_progress_mark");statusLine.addView(progressMark,new LinearLayout.LayoutParams(dp(24),dp(24)));
        status=text("照片仍是草稿，点确定后才加入今日",13,false);status.setTextColor(UiTokens.secondary(night));status.setGravity(Gravity.CENTER);status.setPadding(dp(5),0,0,0);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);statusLine.addView(status,new LinearLayout.LayoutParams(-2,-2));page.addView(statusLine);
        macroGrid=new LinearLayout(activity);macroGrid.setTag("food_macro_grid");macroGrid.setOrientation(1);macroGrid.setVisibility(View.GONE);
        String[] names={"碳水","蛋白质","脂肪","膳食纤维","钠","份量"};
        for(int row=0;row<2;row++){
            LinearLayout line=new LinearLayout(activity);line.setTag("food_macro_row_"+row);line.setPadding(0,dp(14),0,dp(12));
            for(int col=0;col<3;col++){
                int index=row*3+col;LinearLayout field=new LinearLayout(activity);field.setOrientation(1);field.setGravity(Gravity.CENTER);
                TextView label=text(names[index],12,false);label.setTextColor(UiTokens.secondary(night));field.addView(label);
                macroValues[index]=text("—",19,true);macroValues[index].setTag("food_macro_"+index);macroValues[index].setGravity(Gravity.CENTER);macroValues[index].setPadding(0,dp(5),0,0);field.addView(macroValues[index]);line.addView(field,new LinearLayout.LayoutParams(0,-2,1));
            }macroGrid.addView(line);
        }page.addView(macroGrid);
        explanation=text("",12,false);explanation.setTag("food_result_status_note");explanation.setGravity(Gravity.CENTER);explanation.setTextColor(UiTokens.secondary(night));explanation.setPadding(0,dp(12),0,dp(8));page.addView(explanation);
        tips=new LinearLayout(activity);tips.setTag("food_tips");tips.setOrientation(1);tips.setPadding(dp(6),dp(16),dp(6),dp(10));tips.setVisibility(View.GONE);
        TextView tipHeading=text("小贴士",16,true);tips.addView(tipHeading);
        tipBody=text("",13,false);tipBody.setTag("food_tip_body");tipBody.setTextColor(UiTokens.secondary(night));tipBody.setPadding(0,dp(6),0,0);tips.addView(tipBody);
        tipNote=text("",11,false);tipNote.setTag("food_result_reference");tipNote.setTextColor(UiTokens.secondary(night));tipNote.setPadding(0,dp(10),0,0);tips.addView(tipNote);page.addView(tips);
        configure=text("设置营养识别  ›",14,true);configure.setMinHeight(dp(48));configure.setGravity(Gravity.CENTER);configure.setOnClickListener(v->setup.run());page.addView(configure);
        LinearLayout actions=new LinearLayout(activity);actions.setGravity(Gravity.CENTER_VERTICAL);actions.setPadding(dp(24),dp(8),dp(24),dp(12));actions.setBackgroundColor(UiTokens.paper(night));
        ActionIcon cancel=new ActionIcon(activity,0);cancel.setContentDescription("取消");cancel.setOnClickListener(v->cancelImports());actions.addView(cancel,new LinearLayout.LayoutParams(dp(56),dp(56)));
        confirm=PaperComponents.button(activity,"✓",night,v->confirmImports());confirm.setTextSize(29);confirm.setContentDescription("确定");
        LinearLayout.LayoutParams center=new LinearLayout.LayoutParams(0,dp(60),1);center.leftMargin=dp(28);center.rightMargin=dp(28);actions.addView(confirm,center);
        ActionIcon edit=new ActionIcon(activity,1);edit.setContentDescription("修改");edit.setOnClickListener(v->editActions());actions.addView(edit,new LinearLayout.LayoutParams(dp(56),dp(56)));modify=edit;
        root.addView(actions);dialog.setContentView(root);dialog.setOnCancelListener(d->cancelImports());dialog.setOnDismissListener(d->dispose());
    }
    public void show(){
        dialog.show();Window window=dialog.getWindow();
        if(window!=null){window.setLayout(-1,-1);window.setStatusBarColor(UiTokens.paper(night));window.setNavigationBarColor(UiTokens.paper(night));window.getDecorView().setSystemUiVisibility(night?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);window.getDecorView().requestApplyInsets();}
        prefs.registerOnSharedPreferenceChangeListener(this);
        IntentFilter filter=new IntentFilter(FoodAnalysisJobService.CHANGED);
        if(android.os.Build.VERSION.SDK_INT>=33)activity.registerReceiver(progressReceiver,filter,Context.RECEIVER_NOT_EXPORTED);else activity.registerReceiver(progressReceiver,filter);
        receiverRegistered=true;refresh();
    }
    /** Lifecycle suspension retains the draft. Only user cancellation discards it. */
    public void dismiss(){dialog.dismiss();dispose();}
    private void dispose(){
        if(disposed)return;disposed=true;prefs.unregisterOnSharedPreferenceChangeListener(this);if(receiverRegistered){activity.unregisterReceiver(progressReceiver);receiverRegistered=false;}stopResultMotion(true);stopTitleMotion();progressMark.release();ui.removeCallbacksAndMessages(null);images.shutdownNow();preview.release();closed.run();
    }
    private void cancelImports(){
        if(resolved||disposed)return;resolved=true;FoodDraftStore.discard(activity,sources);dialog.dismiss();
    }
    private void confirmImports(){
        if(resolved||disposed||!confirm.isEnabled())return;
        if(!FoodDraftStore.confirm(activity,sources)){toast("保存未完成，请重试；草稿仍保留");return;}
        resolved=true;toast(sources.size()>1?"已添加 "+sources.size()+" 张食物贴纸":"食物贴纸已加入今日");dialog.dismiss();
    }
    private void buildSelection(){
        selection.removeAllViews();selection.setVisibility(sources.size()>1?View.VISIBLE:View.GONE);
        for(int i=0;i<sources.size();i++){
            final int index=i;TextView chip=text("第 "+(i+1)+" 张",13,i==selected);chip.setGravity(Gravity.CENTER);chip.setMinHeight(dp(48));
            chip.setOnClickListener(v->{selected=index;imageKey="";resultKey="";buildSelection();refresh();});selection.addView(chip,new LinearLayout.LayoutParams(0,-2,1));
        }
    }
    @Override public void onSharedPreferenceChanged(SharedPreferences p,String key){
        if(FoodDraftStore.KEY.equals(key)||NutritionStore.DAYS.equals(key)||NutritionStore.PROFILE.equals(key))ui.post(()->{if(!disposed)refresh();});
    }
    private JSONObject item(String source){
        JSONObject draft=FoodDraftStore.item(activity,source);if(draft!=null)return draft;
        return NutritionStore.forSource(NutritionStore.meal(NutritionStore.read(prefs,NutritionStore.DAYS),date,meal),source).optJSONObject(0);
    }
    private void refresh(){
        if(disposed||sources.isEmpty())return;
        JSONObject current=item(sources.get(selected));
        if(current==null){stopResultMotion(true);progressMark.working(false);dish.setText("这张图片已取消或移除");confirm.setEnabled(false);configure.setVisibility(View.GONE);return;}
        boolean ready=true;for(String source:sources){JSONObject value=item(source);ready&=value!=null&&(!value.optString("cutoutFile").isEmpty()||!value.optString("cutoutError").isEmpty());}
        confirm.setEnabled(ready);confirm.setAlpha(ready?1:.35f);
        String state=current.optString("analysisStatus"),cutout=current.optString("cutoutFile"),name=current.optString("dishName");
        loadImage(sources.get(selected),cutout,current.optLong("cutoutEditedAt"),NutritionStore.displayRotation(current));
        boolean hasCutout=!cutout.isEmpty(),completed="completed".equals(current.optString("status"))&&current.optJSONObject("nutrients")!=null&&("completed".equals(state)||state.isEmpty());
        String cutoutState=current.optString("cutoutStatus");
        boolean localWorking=("pending".equals(cutoutState)||"cutting_out".equals(cutoutState))&&FoodAnalysisJobService.cutoutPending(activity,current);
        boolean cloudQueued=FoodAnalysisJobService.nutritionPending(activity,current);
        preview.working(localWorking||cloudQueued&&"cutting_out".equals(state)||(!hasCutout&&current.optString("cutoutError").isEmpty()));
        String title=!name.isEmpty()?name:hasCutout?"这份美味":"正在制作食物贴纸…";
        if(!title.contentEquals(dish.getText())){dish.animate().cancel();dish.setText(title);dish.setAlpha(1);if(!name.isEmpty()&&dish.isShown()&&ValueAnimator.areAnimatorsEnabled()){dish.setAlpha(0);dish.animate().alpha(1).setDuration(160).setInterpolator(UiTokens.motionEaseOut()).start();}}
        dish.setEnabled(hasCutout||!current.optString("cutoutError").isEmpty());dish.setContentDescription(dish.getText()+"，点击修改名称并重新估算营养");
        boolean waiting="waiting_api".equals(state)||"waiting_model".equals(state)||"awaiting_consent".equals(state);
        Double early="recognizing".equals(state)?FoodAnalysisJobService.previewCalories(current.optString("analysisId")):null;
        progressMark.working(localWorking||cloudQueued&&("cutting_out".equals(state)||"recognizing".equals(state)||"pending".equals(state)));
        String message="cutting_out".equals(state)?"正在本机抠图，保留完整餐食主体…":
            "recognizing".equals(state)?(early!=null?"营养分析中…":name.isEmpty()?"食物识别中…":current.optBoolean("dishNameUserEdited")?"结合新菜名与原图估算中…":"热量计算中…"):
            "waiting_api".equals(state)?"贴纸已就绪 · 营养识别需要支持图片的 HTTPS API":
            "waiting_model".equals(state)?"贴纸已就绪 · 请配置自己的 API":
            "awaiting_consent".equals(state)?"贴纸已就绪 · 可配置自己的 API 识别营养":
            "needs_retry".equals(state)?current.optString("error","营养识别未完成，可修改或重试"):
            "edited".equals(state)?"修改已保存在草稿，点确定后加入今日":
            completed?"识别完成 · 点确定后加入今日":"正在排队处理，草稿会自动保留";
        if(!current.optString("cutoutError").isEmpty())message="抠图未完成，原图已保留；可在修改中修整或重新抠图";
        if(current.optBoolean("cutoutFallback"))message+="\n当前内存不足，已使用轻量模型，可在修改中重新抠图。";
        if(localWorking)message="正在本机重新抠图，不重新识别或改动营养…";
        else if("completed".equals(cutoutState)&&!completed&&!cloudQueued)
            message="抠图已更新 · 可在修改中选择重新识别营养";
        status.setText(message);configure.setVisibility(waiting?View.VISIBLE:View.GONE);
        configure.setText("选择或配置识别方式  ›");
        modify.setEnabled(hasCutout||!current.optString("cutoutError").isEmpty());modify.setAlpha(modify.isEnabled()?1:.35f);
        if(completed){
            JSONObject n=current.optJSONObject("nutrients");calories.setVisibility(View.VISIBLE);macroGrid.setVisibility(View.VISIBLE);
            // Keep the saved uncertainty data, but put provenance after the final tip
            // instead of a separate estimate/range row above it.
            explanation.setText("");explanation.setVisibility(View.GONE);
            String tip=FoodAnalysisResult.tip(current);tipBody.setText(tip);tipBody.setVisibility(tip.isEmpty()?View.GONE:View.VISIBLE);
            tipNote.setText(FoodUi.resultReference(current));tips.setVisibility(View.VISIBLE);
            String key=current.optString("analysisId")+n.toString()+current.optDouble("estimatedGrams");
            if(!key.equals(resultKey)){stopResultMotion(true);resultKey=key;animateResult(current,n);}
        }else{
            stopResultMotion(true);resultKey="";calories.setAlpha(1);calories.setVisibility(early==null?View.GONE:View.VISIBLE);
            if(early!=null){calories.setText("约 "+FoodUi.format(early)+" kcal");calories.setContentDescription("热量预估，尚在校验："+FoodUi.format(early)+"千卡");}
            macroGrid.setVisibility(View.GONE);tips.setVisibility(View.GONE);explanation.setVisibility(View.VISIBLE);explanation.setText(early!=null?"热量预估中 · 完整校验后展示营养，可先确认保存贴纸。":hasCutout?"可以先确认保存贴纸，营养识别稍后继续。":"原照片与分析结果在确认前不会加入记录。");
        }
    }
    private void animateResult(JSONObject item,JSONObject nutrients){
        final double energy=nutrients.optDouble("caloriesKcal");final double[] values=new double[6];
        for(int i=0;i<6;i++)values[i]=i==5?item.optDouble("estimatedGrams"):nutrients.optDouble(FoodAnalysisResult.LOCAL[i+1]);
        calories.setContentDescription(FoodUi.format(energy)+"千卡，"+(item.optBoolean("userEdited")?"用户修正":"AI估算"));
        settleResult=()->{calories.setAlpha(1);calories.setText(FoodUi.format(energy)+" kcal");for(int i=0;i<6;i++){macroValues[i].setText(FoodUi.format(values[i])+(i==4?" mg":" g"));macroGrid.getChildAt(i/3).setAlpha(1);}tips.setAlpha(1);};
        if(!ValueAnimator.areAnimatorsEnabled()||!preview.isShown()){settleResult.run();settleResult=null;return;}
        final android.animation.TimeInterpolator easing=UiTokens.motionEaseOut();
        resultAnimator=ValueAnimator.ofFloat(0,300);resultAnimator.setDuration(300);resultAnimator.setInterpolator(new android.view.animation.LinearInterpolator());
        resultAnimator.addUpdateListener(animation->{
            float elapsed=(float)animation.getAnimatedValue(),energyProgress=easing.getInterpolation(Math.min(1,elapsed/200));
            calories.setAlpha(energyProgress);calories.setText(FoodUi.format(Math.round(energy*energyProgress))+" kcal");
            for(int row=0;row<2;row++){
                float progress=easing.getInterpolation(Math.max(0,Math.min(1,(elapsed-(row+1)*50)/200)));macroGrid.getChildAt(row).setAlpha(progress);
                for(int col=0;col<3;col++){int i=row*3+col;macroValues[i].setText(FoodUi.format(Math.round(values[i]*progress*10)/10d)+(i==4?" mg":" g"));}
            }tips.setAlpha(easing.getInterpolation(Math.max(0,Math.min(1,(elapsed-100)/200))));
        });
        resultAnimator.addListener(new android.animation.AnimatorListenerAdapter(){@Override public void onAnimationEnd(android.animation.Animator animation){if(settleResult!=null){settleResult.run();settleResult=null;}resultAnimator=null;}});resultAnimator.start();
    }
    private void stopResultMotion(boolean settle){
        if(resultAnimator!=null){ValueAnimator motion=resultAnimator;resultAnimator=null;motion.cancel();}
        if(settle&&settleResult!=null)settleResult.run();settleResult=null;
    }
    private void stopTitleMotion(){if(dish!=null){dish.animate().cancel();dish.setAlpha(1);}}
    private void loadImage(String source,String cutout,long editedAt,int rotation){
        String key=source+"|"+cutout+"|"+editedAt+"|"+rotation+"|"+FoodStickerRenderer.style(activity);if(key.equals(imageKey))return;imageKey=key;
        images.execute(()->{
            Bitmap raw=null,sticker=null;
            try{
                raw=FoodUi.thumbnail(new File(activity.getFilesDir(),source));
                if(NutritionStore.safeName(cutout)){
                    Bitmap alpha=FoodUi.thumbnail(new File(activity.getFilesDir(),cutout));
                    if(alpha!=null){try{sticker=FoodStickerRenderer.create(activity,alpha,Math.max(7,Math.max(alpha.getWidth(),alpha.getHeight())/40));}finally{alpha.recycle();}}
                }
                Bitmap rotated=FoodStickerRenderer.rotateDisplay(raw,rotation);if(rotated!=raw)recycle(raw);raw=rotated;
                rotated=FoodStickerRenderer.rotateDisplay(sticker,rotation);if(rotated!=sticker)recycle(sticker);sticker=rotated;
            }catch(OutOfMemoryError ignored){}
            final Bitmap original=raw,outlined=sticker;
            ui.post(()->{if(disposed||!key.equals(imageKey)){recycle(original);recycle(outlined);return;}preview.set(original,outlined);});
        });
    }
    private void editActions(){
        if(item(sources.get(selected))==null)return;
        builder().setTitle("修改这份餐食").setItems(new String[]{"修改菜名、份量与营养","擦除 / 恢复贴纸边缘","重新抠图","识别信息"},(d,which)->{
            if(which==0)editFood();else if(which==1)editCutout();else if(which==2)retry(true);else{
                builder().setTitle("重新识别营养？").setMessage("使用原图和已修正的菜名估算营养，保留现有透明图；仅在已授权时调用你配置的 API，可能产生费用。")
                    .setNegativeButton("取消",null).setPositiveButton("重新识别",(dialog,w)->retry(false)).show();
            }
        }).show();
    }
    private void renameFood(){
        final String source=sources.get(selected);JSONObject current=item(source);if(current==null)return;
        LinearLayout box=new LinearLayout(activity);box.setOrientation(1);box.setPadding(dp(20),dp(8),dp(20),dp(12));
        box.addView(text("保存后结合原图和新菜名重新估算营养，不重复抠图。未更改名称不会再次请求。",13,false));
        EditText input=PaperComponents.input(activity,night);input.setTag("food_name_input");input.setContentDescription("食物名称");input.setSingleLine(true);input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(100)});input.setText(current.optString("dishName"));input.selectAll();box.addView(input);
        AlertDialog edit=builder().setTitle("修改食物名称").setView(box).setNegativeButton("取消",null).setPositiveButton("保存并重新识别",null).create();
        edit.setOnShowListener(d->edit.getButton(-1).setOnClickListener(v->{
            String name=FoodNameHint.normalize(input.getText().toString());if(name.isEmpty()){input.setError("请输入1–100字菜名");return;}
            if(!FoodAnalysisJobService.renameAndAnalyze(activity,date,meal,source,name)){toast("名称暂未保存，请稍后重试");return;}
            edit.dismiss();refresh();
        }));edit.show();PaperComponents.styleDialog(edit,night);
    }
    private void retry(boolean cutoutOnly){
        String source=sources.get(selected);JSONObject current=item(source);if(current==null)return;
        boolean accepted=cutoutOnly?FoodAnalysisJobService.enqueueCutout(activity,date,meal,source):FoodAnalysisJobService.enqueueNutrition(activity,date,meal,source);
        if(!accepted){toast("暂时无法重新处理，请确认原图和记录仍在");return;}
        imageKey="";resultKey="";refresh();
    }
    private void editCutout(){
        String source=sources.get(selected);JSONObject current=item(source);if(current==null)return;
        String file=current.optString("cutoutFile");if(file.isEmpty())file="food_cutout_manual_"+current.optString("id")+".png";
        final String output=file;
        CutoutEditor.show(activity,new File(activity.getFilesDir(),source),new File(activity.getFilesDir(),output),current.optJSONArray("cutoutRect"),night,bounds->{
            if(!FoodAnalysisJobService.saveCutout(activity,date,meal,source,output,bounds))toast("记录已变更，贴纸未写入；请重新打开后编辑");
            imageKey="";refresh();
        });
    }
    private void editFood(){
        final String source=sources.get(selected);final JSONObject current=item(source);if(current==null)return;
        LinearLayout fields=new LinearLayout(activity);fields.setOrientation(1);fields.setPadding(dp(20),dp(8),dp(20),dp(16));
        fields.addView(text("只改份量时会按比例调整已有营养。没有识别结果时可只填菜名；要手动填写营养，请补全所有数值。",12,false));
        String[] labels={"菜名","份量 g","热量 kcal","碳水 g","蛋白质 g","脂肪 g","纤维 g","钠 mg"};EditText[] inputs=new EditText[8];JSONObject old=current.optJSONObject("nutrients");
        for(int i=0;i<8;i++){
            fields.addView(text(labels[i],13,false));EditText input=PaperComponents.input(activity,night);if(i>0)input.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
            input.setText(i==0?current.optString("dishName"):i==1?current.has("estimatedGrams")?FoodUi.format(current.optDouble("estimatedGrams")):"":old==null?"":FoodUi.format(old.optDouble(FoodAnalysisResult.LOCAL[i-2])));inputs[i]=input;fields.addView(input);
        }
        ScrollView scroll=new ScrollView(activity);scroll.addView(fields);
        AlertDialog edit=builder().setTitle("修改餐食信息").setView(scroll).setNegativeButton("取消",null).setPositiveButton("保存修改",null).create();
        edit.setOnShowListener(d->edit.getButton(-1).setOnClickListener(v->{
            try{
                JSONObject changed=new JSONObject(current.toString());String name=inputs[0].getText().toString().trim();if(name.isEmpty()||name.length()>100)throw new Exception();changed.put("dishName",name);
                String weight=inputs[1].getText().toString().trim();double grams=weight.isEmpty()?0:Double.parseDouble(weight);if(!weight.isEmpty()&&(!FoodAnalysisResult.finite(grams)||grams<=0||grams>10000))throw new Exception();if(grams>0)changed.put("estimatedGrams",grams);
                boolean any=false;for(int i=2;i<8;i++)any|=inputs[i].getText().length()>0;
                if(any){
                    if(grams<=0)throw new Exception();JSONObject nutrients=new JSONObject();boolean untouched=old!=null;
                    for(int i=0;i<6;i++){
                        String value=inputs[i+2].getText().toString().trim();if(value.isEmpty())throw new Exception();double n=Double.parseDouble(value);if(!FoodAnalysisResult.finite(n)||n<0||n>(i==5?100000:i==0?20000:5000))throw new Exception();nutrients.put(FoodAnalysisResult.LOCAL[i],n);untouched&=old!=null&&Math.abs(n-old.optDouble(FoodAnalysisResult.LOCAL[i]))<.051;
                    }
                    if(untouched&&current.optDouble("estimatedGrams")>0){double ratio=grams/current.optDouble("estimatedGrams");for(String key:FoodAnalysisResult.LOCAL)nutrients.put(key,Math.round(nutrients.getDouble(key)*ratio*10)/10.0);}
                    for(int i=0;i<6;i++)FoodAnalysisResult.number(nutrients,FoodAnalysisResult.LOCAL[i],0,i==5?100000:i==0?20000:5000);
                    changed.put("nutrients",nutrients).put("status","completed").put("analysisStatus","completed").put("calorieLow",nutrients.getDouble("caloriesKcal")).put("calorieHigh",nutrients.getDouble("caloriesKcal"));
                }
                if(!saveEdited(source,changed))throw new Exception();edit.dismiss();refresh();
            }catch(Exception e){toast("请检查菜名、份量与营养数值；无识别结果时不要填入猜测的零值");}
        }));edit.show();PaperComponents.styleDialog(edit,night);
    }
    private boolean saveEdited(String source,JSONObject current){
        return FoodAnalysisJobService.saveNutritionEdit(activity,date,meal,source,current);
    }
    private AlertDialog.Builder builder(){return PaperComponents.dialogBuilder(activity,night);}
    private void toast(String value){Toast.makeText(activity,value,Toast.LENGTH_LONG).show();}
    private int dp(float value){return PaperComponents.dp(activity,value);}
    private TextView text(String value,float size,boolean bold){return PaperComponents.text(activity,value,size,UiTokens.ink(night),bold);}
    private static String mealTitle(String key){return DayFoodEntries.mealTitle(key);}
    private static void recycle(Bitmap bitmap){if(bitmap!=null&&!bitmap.isRecycled())bitmap.recycle();}
    private final class ActionIcon extends View {
        private final int kind;private final Paint paint=new Paint(3);
        ActionIcon(Context c,int kind){super(c);this.kind=kind;GradientDrawable bg=new GradientDrawable();bg.setColor(UiTokens.card(night));bg.setCornerRadius(dp(28));setBackground(bg);setClickable(true);setFocusable(true);}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);float cx=getWidth()/2f,cy=getHeight()/2f;paint.setColor(UiTokens.ink(night));paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2.3f));paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);
            if(kind==0){canvas.drawLine(cx-dp(7),cy-dp(7),cx+dp(7),cy+dp(7),paint);canvas.drawLine(cx+dp(7),cy-dp(7),cx-dp(7),cy+dp(7),paint);}
            else{Path pencil=new Path();pencil.moveTo(cx-dp(8),cy+dp(7));pencil.lineTo(cx-dp(6),cy+dp(1));pencil.lineTo(cx+dp(6),cy-dp(11));pencil.lineTo(cx+dp(10),cy-dp(7));pencil.lineTo(cx-dp(2),cy+dp(5));pencil.close();canvas.drawPath(pencil,paint);canvas.drawLine(cx-dp(8),cy+dp(12),cx+dp(9),cy+dp(12),paint);}
        }
    }
    private final class StageView extends View {
        private Bitmap raw,sticker;private ValueAnimator animator,scanAnimator;private float reveal,scan;private boolean processing;private final Paint paint=new Paint(3);
        StageView(Context context){super(context);}
        void working(boolean value){if(processing==value)return;processing=value;if(raw==null)return;stopMotion();reveal=0;if(value)scanLoop();else if(sticker!=null)revealSticker();invalidate();}
        void set(Bitmap original,Bitmap outlined){
            stopMotion();recycle(raw);recycle(sticker);raw=original;sticker=outlined;reveal=0;
            if(processing&&raw!=null)scanLoop();else if(sticker!=null)revealSticker();invalidate();
        }
        private void revealSticker(){if(!ValueAnimator.areAnimatorsEnabled()){reveal=1;invalidate();return;}animator=ValueAnimator.ofFloat(reveal,1);animator.setDuration(600);animator.setInterpolator(new android.view.animation.LinearInterpolator());animator.addUpdateListener(v->{reveal=(float)v.getAnimatedValue();scan=reveal;invalidate();});animator.start();}
        private void scanLoop(){if(!ValueAnimator.areAnimatorsEnabled()||scanAnimator!=null)return;scanAnimator=ValueAnimator.ofFloat(0,1);scanAnimator.setDuration(1200);scanAnimator.setRepeatCount(ValueAnimator.INFINITE);scanAnimator.setRepeatMode(ValueAnimator.REVERSE);scanAnimator.setInterpolator(new android.view.animation.LinearInterpolator());scanAnimator.addUpdateListener(v->{scan=(float)v.getAnimatedValue();invalidate();});scanAnimator.start();}
        private RectF fit(Bitmap b,float amount){float scale=Math.min(getWidth()/(float)b.getWidth(),getHeight()/(float)b.getHeight())*amount;float w=b.getWidth()*scale,h=b.getHeight()*scale;return new RectF((getWidth()-w)/2,(getHeight()-h)/2,(getWidth()+w)/2,(getHeight()+h)/2);}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);paint.setShader(null);paint.setStyle(Paint.Style.FILL);
            if(raw!=null&&!raw.isRecycled()){paint.setAlpha(Math.round(255*(1-reveal)));canvas.drawBitmap(raw,null,fit(raw,.94f),paint);}
            if(sticker!=null&&!sticker.isRecycled()){
                paint.setAlpha(255);int save=canvas.save();if(reveal<1)canvas.clipRect(0,0,getWidth(),getHeight()*reveal);canvas.drawBitmap(sticker,null,fit(sticker,.95f),paint);canvas.restoreToCount(save);
            }
            if(ValueAnimator.areAnimatorsEnabled()&&raw!=null&&(sticker==null&&processing||sticker!=null&&reveal<1)){
                RectF area=fit(raw,.94f);float y=area.top+area.height()*scan;int save=canvas.save();canvas.clipRect(area);paint.setAlpha(255);
                int tint=UiTokens.yellow();paint.setShader(new LinearGradient(0,y-dp(30),0,y,new int[]{Color.TRANSPARENT,(tint&0x00ffffff)|0x66000000},null,Shader.TileMode.CLAMP));canvas.drawRect(area.left,y-dp(30),area.right,y,paint);paint.setShader(null);paint.setColor(tint);paint.setAlpha(Math.round(220*(1-Math.abs(scan-.5f)*1.5f)));paint.setStrokeWidth(dp(2));canvas.drawLine(area.left,y,area.right,y,paint);canvas.restoreToCount(save);
            }
            paint.setAlpha(255);paint.setShader(null);
        }
        private void stopMotion(){if(animator!=null){animator.cancel();animator=null;}if(scanAnimator!=null){scanAnimator.cancel();scanAnimator=null;}}
        @Override protected void onDetachedFromWindow(){stopMotion();super.onDetachedFromWindow();}
        @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(visibility!=VISIBLE){stopMotion();stopResultMotion(true);stopTitleMotion();}else if(raw!=null){if(processing)scanLoop();else if(sticker!=null&&reveal<1)revealSticker();}}
        void release(){stopMotion();recycle(raw);recycle(sticker);raw=null;sticker=null;}
    }
    /** Indeterminate status feedback, not a model progress percentage. */
    private final class ProgressMark extends View {
        private final Paint paint=new Paint(3);private ValueAnimator spin;private float phase;private boolean active;
        ProgressMark(Context c){super(c);setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}
        void working(boolean value){active=value;setVisibility(value?VISIBLE:GONE);if(value&&isShown())start();else release();}
        private void start(){if(spin!=null||!active||!ValueAnimator.areAnimatorsEnabled())return;spin=ValueAnimator.ofFloat(0,360);spin.setDuration(1200);spin.setRepeatCount(ValueAnimator.INFINITE);spin.setInterpolator(new android.view.animation.LinearInterpolator());spin.addUpdateListener(v->{phase=(float)v.getAnimatedValue();invalidate();});spin.start();}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float cx=getWidth()/2f,cy=getHeight()/2f;int save=canvas.save();canvas.rotate(phase,cx,cy);paint.setColor(UiTokens.yellow());paint.setStyle(Paint.Style.FILL);Path star=new Path();for(int i=0;i<8;i++){double angle=i*Math.PI/4-Math.PI/2;float radius=dp(i%2==0?7:2.8f),x=cx+(float)Math.cos(angle)*radius,y=cy+(float)Math.sin(angle)*radius;if(i==0)star.moveTo(x,y);else star.lineTo(x,y);}star.close();canvas.drawPath(star,paint);canvas.restoreToCount(save);}
        @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(visibility==VISIBLE)start();else release();}
        @Override protected void onDetachedFromWindow(){release();super.onDetachedFromWindow();}
        void release(){if(spin!=null){spin.cancel();spin=null;}}
    }
}
