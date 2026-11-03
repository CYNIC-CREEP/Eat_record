package com.eatrecord.app;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

/** New food UI uses the same days_json records and existing navigation. */
public final class FoodUi {
    public interface Host {
        JSONObject days(); JSONObject profile(); boolean night();
        void saveProfile(); void saveDays(); void reload(); void refresh(); void api();
        /** Merge only the daily target into the latest settings and report durable success. */
        boolean saveDailyGoal(int value);
        default void recognition(){api();}
        default void recognitionEnabled(boolean enabled){try{profile().put("foodAutoAnalyze",enabled).put("foodPhotoConsent",enabled);saveProfile();refresh();}catch(JSONException ignored){}}
        void preview(String date,String meal,String file);
        /** Detail dialogs close only after their host has installed a live preview. */
        default boolean previewDetails(String date,String meal,String file){preview(date,meal,file);return true;}
        default void previewDetails(String date,String meal,String file,java.util.function.BooleanSupplier current,java.util.function.Consumer<Boolean> ready){
            ready.accept(current.getAsBoolean()&&previewDetails(date,meal,file));
        }
        void processing(String date,String meal,String source);
        /** Existing hosts may use the warm paper accent; MainActivity supplies its active theme. */
        default int detailAccentColor(){return night()?Color.rgb(68,53,39):Color.rgb(246,226,181);}
    }
    private final Activity a; private final Host host;
    public FoodUi(Activity activity,Host host){a=activity;this.host=host;}
    private TextView text(String s,float size,boolean bold){return PaperComponents.text(a,s,size,UiTokens.ink(host.night()),bold);}
    private int dp(float value){return PaperComponents.dp(a,value);}
    private AlertDialog.Builder builder(){return PaperComponents.dialogBuilder(a,host.night());}
    private void message(String s){Toast.makeText(a,s,Toast.LENGTH_LONG).show();}
    /** The home is a journal; nutrition and management live behind an explicit tap. */
    public AlertDialog details(String date,String meal,JSONObject snapshot){
        host.reload();JSONObject latest=NutritionStore.find(NutritionStore.meal(host.days(),date,meal),snapshot.optString("id"));
        final JSONObject item=latest==null?snapshot:latest;
        LinearLayout content=new LinearLayout(a);content.setOrientation(1);content.setPadding(dp(22),dp(32),dp(22),dp(32));content.setTag("food_detail");
        String file=item.optString("cutoutFile");boolean cutout=!file.isEmpty();if(!cutout)file=item.optString("sourcePhoto");
        Bitmap decoded=NutritionStore.safeName(file)?thumbnail(new File(a.getFilesDir(),file)):null;
        if(decoded==null&&cutout){decoded=thumbnail(new File(a.getFilesDir(),item.optString("sourcePhoto")));cutout=false;}
        Bitmap outlined=decoded;
        if(decoded!=null&&cutout){try{outlined=FoodStickerRenderer.create(a,decoded,Math.max(7,Math.max(decoded.getWidth(),decoded.getHeight())/40));}catch(OutOfMemoryError ignored){}if(outlined!=decoded)decoded.recycle();}
        Bitmap rotated=FoodStickerRenderer.rotateDisplay(outlined,NutritionStore.displayRotation(item));
        if(rotated!=outlined&&outlined!=null)outlined.recycle();
        final Bitmap display=rotated;
        final ImageView image=new ImageView(a);image.setTag("food_detail_image");image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setImageBitmap(display);image.setContentDescription(item.optString("dishName","食物贴纸")+"，点击查看原图");
        if(display!=null)content.addView(image,new LinearLayout.LayoutParams(-1,dp(190)));
        TextView name=text(DayFoodEntries.prefix(meal)+" · "+item.optString("dishName","食物贴纸"),19,true);name.setGravity(Gravity.CENTER);name.setPadding(0,dp(10),0,dp(8));content.addView(name);
        JSONObject n=item.optJSONObject("nutrients");
        View nutritionEnd=null;
        if(n!=null&&"completed".equals(item.optString("status"))){
            TextView energy=text(format(n.optDouble("caloriesKcal"))+" kcal",29,false);energy.setGravity(Gravity.CENTER);content.addView(energy);
            String[] labels={"碳水","蛋白质","脂肪","膳食纤维","钠","份量"};
            for(int row=0;row<2;row++){
                LinearLayout line=new LinearLayout(a);line.setPadding(0,dp(14),0,dp(8));
                for(int col=0;col<3;col++){
                    int index=row*3+col;LinearLayout field=new LinearLayout(a);field.setOrientation(1);field.setGravity(Gravity.CENTER);
                    TextView label=text(labels[index],12,false);label.setTextColor(UiTokens.secondary(host.night()));field.addView(label);
                    String value=index==5?format(item.optDouble("estimatedGrams"))+" g":format(n.optDouble(FoodAnalysisResult.LOCAL[index+1]))+(index==4?" mg":" g");
                    TextView number=text(value,17,true);number.setGravity(Gravity.CENTER);field.addView(number);line.addView(field,new LinearLayout.LayoutParams(0,-2,1));
                }content.addView(line);
                if(row==1){line.setTag("food_detail_macros_end");nutritionEnd=line;}
            }
            TextView tip=text("小贴士\n"+FoodAnalysisResult.tip(item),13,false);tip.setTag("food_detail_tip");tip.setPadding(0,dp(10),0,dp(8));
            LinearLayout.LayoutParams tipLp=new LinearLayout.LayoutParams(-1,-2);tipLp.topMargin=dp(12);content.addView(tip,tipLp);
            TextView reference=text(resultReference(item),11,false);reference.setTag("food_detail_reference");reference.setTextColor(UiTokens.secondary(host.night()));reference.setPadding(0,0,0,dp(16));content.addView(reference);
        }else{TextView pending=text("这张贴纸暂无营养数据，可配置视觉 API 后识别，或手动填写。",13,false);pending.setPadding(0,dp(12),0,dp(12));content.addView(pending);}
        String error=item.optString("error");if(!error.isEmpty())content.addView(text(error,12,false));
        JSONObject mealRecord=NutritionStore.meal(host.days(),date,meal);
        if(mealRecord!=null&&!mealRecord.optString("text").trim().isEmpty()){
            TextView note=text("餐次备注\n"+mealRecord.optString("text"),13,false);note.setTag("food_detail_note");note.setPadding(0,dp(14),0,dp(14));content.addView(note);
        }
        ScrollView scroll=new CompactDetailScroll(content,nutritionEnd);scroll.setTag("food_detail_scroll");
        scroll.setVerticalScrollBarEnabled(true);scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);scroll.addView(content);
        FrameLayout shell=new FrameLayout(a);shell.setTag("food_detail_shell");shell.addView(scroll,new FrameLayout.LayoutParams(-1,-2));
        AlertDialog dialog=builder().create();dialog.setView(shell,0,0,0,0);
        final boolean[] previewOpening={false};
        image.setOnClickListener(v->{
            if(previewOpening[0]||a.isFinishing()||a.isDestroyed())return;
            String preview=preferredPreviewFile(a.getFilesDir(),item);
            if(preview.isEmpty()){message("图片文件已不存在，餐食详情已保留");return;}
            previewOpening[0]=true;
            try{host.previewDetails(date,meal,preview,()->dialog.isShowing()&&!a.isFinishing()&&!a.isDestroyed(),opened->{
                if(!dialog.isShowing())return;
                previewOpening[0]=false;
                if(opened)dialog.dismiss();else message("图片暂时无法打开，餐食详情已保留");
            });}
            catch(RuntimeException unavailable){
                // The exception message may contain a private filename. Log only its type.
                android.util.Log.w("EatRecord","Food detail preview unavailable: "+unavailable.getClass().getSimpleName());
                previewOpening[0]=false;message("图片暂时无法打开，餐食详情已保留");
            }
        });
        TextView edit=PaperComponents.button(a,"修改菜名、份量和营养",host.night(),v->{dialog.dismiss();edit(date,meal,item);});edit.setTag("food_detail_edit");content.addView(edit);
        TextView more=PaperComponents.button(a,"更多操作",host.night(),v->{dialog.dismiss();actions(date,meal,item);});more.setTag("food_detail_more");
        int accent=host.detailAccentColor();android.graphics.drawable.GradientDrawable moreBackground=new android.graphics.drawable.GradientDrawable();moreBackground.setColor(accent);moreBackground.setCornerRadius(dp(UiTokens.CONTROL_RADIUS));more.setBackground(moreBackground);
        double luminance=(.2126*linearColor(Color.red(accent))+.7152*linearColor(Color.green(accent))+.0722*linearColor(Color.blue(accent)));
        more.setTextColor(luminance>.32?UiTokens.ink(false):UiTokens.ink(true));
        LinearLayout.LayoutParams moreLp=new LinearLayout.LayoutParams(-1,-2);moreLp.topMargin=dp(12);content.addView(more,moreLp);
        TextView close=text("×",21,false);close.setTag("food_detail_close");close.setGravity(Gravity.CENTER);close.setContentDescription("关闭餐食详情");close.setOnClickListener(v->dialog.dismiss());
        FrameLayout.LayoutParams closeLp=new FrameLayout.LayoutParams(dp(48),dp(48),Gravity.TOP|Gravity.END);closeLp.topMargin=dp(4);closeLp.rightMargin=dp(4);shell.addView(close,closeLp);
        dialog.setCanceledOnTouchOutside(true);
        dialog.setOnDismissListener(d->{image.setImageDrawable(null);if(display!=null&&!display.isRecycled())display.recycle();});dialog.show();PaperComponents.styleDialog(dialog,host.night());return dialog;
    }
    /** Select an existing original, then its saved cutout; never escape private media storage. */
    static String preferredPreviewFile(File files,JSONObject item){
        if(files==null||item==null)return "";
        for(String key:new String[]{"sourcePhoto","cutoutFile"}){
            String name=item.optString(key);
            if(NutritionStore.safeName(name)&&new File(files,name).isFile())return name;
        }
        return "";
    }
    /** First view is the photo and the complete macro grid; the rest stays in this same scroll. */
    private final class CompactDetailScroll extends ScrollView {
        private final LinearLayout content;
        private final View nutritionEnd;
        CompactDetailScroll(LinearLayout content,View nutritionEnd){
            super(FoodUi.this.a);this.content=content;this.nutritionEnd=nutritionEnd;
            setFillViewport(false);
        }
        @Override protected void onMeasure(int widthSpec,int heightSpec){
            Rect visible=new Rect();a.getWindow().getDecorView().getWindowVisibleDisplayFrame(visible);
            int available=visible.height()>0?visible.height():getResources().getDisplayMetrics().heightPixels;
            int limit=Math.max(dp(96),available-dp(64));
            if(MeasureSpec.getMode(heightSpec)!=MeasureSpec.UNSPECIFIED)limit=Math.min(limit,MeasureSpec.getSize(heightSpec));
            super.onMeasure(widthSpec,MeasureSpec.makeMeasureSpec(limit,MeasureSpec.AT_MOST));
            int wanted=Math.min(getMeasuredHeight(),dp(480));
            if(nutritionEnd!=null){
                // The measured child heights are available before the first layout/pre-draw,
                // avoiding a full-height first frame and a visibly shrinking dialog.
                wanted=content.getPaddingTop();
                for(int i=0;i<content.getChildCount();i++){
                    View child=content.getChildAt(i);
                    if(child.getVisibility()==View.GONE)continue;
                    ViewGroup.LayoutParams raw=child.getLayoutParams();
                    if(raw instanceof ViewGroup.MarginLayoutParams){
                        ViewGroup.MarginLayoutParams margins=(ViewGroup.MarginLayoutParams)raw;
                        wanted+=margins.topMargin+margins.bottomMargin;
                    }
                    wanted+=child.getMeasuredHeight();
                    if(child==nutritionEnd)break;
                }
                wanted+=dp(12);
            }
            setMeasuredDimension(getMeasuredWidth(),Math.max(1,Math.min(limit,wanted)));
        }
    }
    /** Display-only provenance: never infer a missing model from today's API settings. */
    static String resultReference(JSONObject item){
        String model=FoodRecordText.modelName(item.optString("providerModel"));
        boolean edited=item.optBoolean("userEdited");
        String source=model.isEmpty()?"此记录未保留模型名称，数据仅供参考。":"数据由"+model+"生成，仅供参考。";
        if("offline".equals(item.optString("recognitionSource")))source=FoodRecordText.provenance(item);
        if(edited)source="当前数据含用户修正。\n"+source;
        else if(item.optDouble("confidence",1)<.55)source="建议核对：这份餐食识别把握较低。\n"+source;
        return source;
    }
    private static double linearColor(int channel){double value=channel/255.0;return value<=.04045?value/12.92:Math.pow((value+.055)/1.055,2.4);}
    public AlertDialog dayNutrition(String date){
        SummaryPanel panel=new SummaryPanel(date);
        SummaryScroll scroll=new SummaryScroll();scroll.setTag("day_nutrition_scroll");scroll.setVerticalScrollBarEnabled(false);scroll.addView(panel);panel.scroll=scroll;
        AlertDialog dialog=builder().setTitle("当天营养与热量目标").create();dialog.setView(scroll,0,0,0,0);
        TextView done=text("完成",15,false);done.setTag("day_nutrition_done");done.setMinHeight(dp(48));done.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);done.setOnClickListener(v->dialog.dismiss());
        panel.addView(done,new LinearLayout.LayoutParams(-1,-2));
        dialog.setOnDismissListener(d->panel.close());dialog.show();PaperComponents.styleDialog(dialog,host.night());return dialog;
    }
    public View summary(final String date){return new SummaryPanel(date);}
    /** One editor and one dialog owner; no draft target is written before explicit Save. */
    private final class SummaryPanel extends LinearLayout {
        final String date;
        final TextView energy,goalEntry;
        final LinearLayout editor;
        final EditText goalInput;
        SummaryScroll scroll;
        boolean editorOpen,closed;
        int editGeneration;
        SummaryPanel(String date){
        super(a);this.date=date;
        JSONObject day=host.days().optJSONObject(date), n=NutritionStore.totals(day);
        // The dialog is already a paper surface; a second outlined card adds no hierarchy.
        LinearLayout box=this;box.setOrientation(LinearLayout.VERTICAL);
        box.setTag("day_nutrition_summary");box.setPadding(dp(24),dp(8),dp(24),dp(4));setFocusableInTouchMode(true);
        box.addView(text("今天，好好吃饭",13,false));
        int calories=displayCalories(day), target=host.profile().optInt("dailyCalorieGoal");
        energy=text(calories+(target>0 ? " / "+target : "")+" kcal",30,true);energy.setTag("day_nutrition_energy");
        energy.setPadding(0,dp(8),0,dp(8));box.addView(energy);
        boolean manual=day!=null && day.optBoolean("manualCaloriesOverride");
        String label=manual?"手动热量优先 · 其他营养素为 AI 估算":n.optInt("count")>0?"AI 估算 · 点食物可修改份量与营养":day!=null && day.optInt("calories")>0?"旧版 AI 热量 · 暂无营养素数据":"从一张餐图开始，留下今天的美味";
        box.addView(text(label,12,false));
        if(manual && n.optInt("count")>0){
            TextView use=text("改用食物识别热量  ›",13,true);use.setMinHeight(dp(48));
            use.setOnClickListener(v->{try{day.remove("manualCaloriesOverride");day.remove("manualCaloriesTotal");day.remove("manualCaloriesByMeal");for(String key:NutritionStore.MEALS){JSONObject meal=day.optJSONObject(key);if(meal!=null)meal.remove("manualCalories");}host.saveDays();host.refresh();}catch(Exception ignored){}});box.addView(use);
        }
        LinearLayout macros=new LinearLayout(a);macros.setPadding(0,dp(14),0,dp(8));
        String[] keys={"carbsG","proteinG","fatG"}, names={"碳水","蛋白质","脂肪"};
        for(int i=0;i<3;i++){
            LinearLayout column=new LinearLayout(a);column.setOrientation(LinearLayout.VERTICAL);
            TextView name=text(names[i],12,false);name.setTextColor(UiTokens.secondary(host.night()));column.addView(name);
            TextView value=text(n.optInt("count")>0?format(n.optDouble(keys[i]))+" g":"—",16,true);
            value.setPadding(0,dp(6),0,dp(4));column.addView(value);
            macros.addView(column,new LinearLayout.LayoutParams(0,-2,1));
        }box.addView(macros);
        goalEntry=text(target>0?"调整每日目标  ›":"设置每日热量目标  ›",13,false);
        goalEntry.setTag("day_nutrition_goal");goalEntry.setMinHeight(dp(48));goalEntry.setGravity(Gravity.CENTER_VERTICAL);
        goalEntry.setOnClickListener(v->setEditorOpen(!editorOpen));box.addView(goalEntry);
        editor=new LinearLayout(a);editor.setOrientation(LinearLayout.VERTICAL);editor.setTag("daily_calorie_goal_body");editor.setPadding(0,dp(2),0,0);editor.setVisibility(View.GONE);
        TextView hint=text("填写你自己的目标，留空可关闭。",12,false);hint.setTextColor(UiTokens.secondary(host.night()));editor.addView(hint);
        goalInput=goalInput();editor.addView(goalInput,new LinearLayout.LayoutParams(-1,-2));
        goalInput.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        goalInput.setOnEditorActionListener((view,action,event)->{if(action==android.view.inputmethod.EditorInfo.IME_ACTION_DONE){saveGoal();return true;}return false;});
        LinearLayout actions=new LinearLayout(a);actions.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
        TextView cancel=text("取消",14,false);cancel.setTag("daily_calorie_goal_cancel");cancel.setGravity(Gravity.CENTER);cancel.setMinHeight(dp(48));cancel.setOnClickListener(v->setEditorOpen(false));
        TextView save=PaperComponents.button(a,"保存",host.night(),v->saveGoal());save.setTag("daily_calorie_goal_save");
        LinearLayout.LayoutParams cancelLp=new LinearLayout.LayoutParams(0,-2,1);cancelLp.rightMargin=dp(8);actions.addView(cancel,cancelLp);actions.addView(save,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout.LayoutParams actionsLp=new LinearLayout.LayoutParams(-1,-2);actionsLp.topMargin=dp(6);editor.addView(actions,actionsLp);box.addView(editor,new LinearLayout.LayoutParams(-1,-2));
        }
        void setEditorOpen(boolean open){
            if(closed||editorOpen==open)return;
            if(android.animation.ValueAnimator.areAnimatorsEnabled()&&isLaidOut()){
                android.transition.ChangeBounds change=new android.transition.ChangeBounds();change.setDuration(220);change.setInterpolator(UiTokens.motionEaseOut());android.transition.TransitionManager.beginDelayedTransition(this,change);
            }
            editorOpen=open;int generation=++editGeneration;
            if(open){
                int target=host.profile().optInt("dailyCalorieGoal");goalInput.setText(target>0?Integer.toString(target):"");goalInput.setError(null);editor.setVisibility(View.VISIBLE);goalEntry.setText("收起目标设置  ⌃");goalInput.requestFocus();goalInput.setSelection(goalInput.length());
                goalInput.post(()->{
                    if(closed||!editorOpen||generation!=editGeneration||!isAttachedToWindow())return;
                    // Reveal the field/actions without moving keyboard focus to the footer.
                    goalInput.requestRectangleOnScreen(new Rect(0,0,goalInput.getWidth(),goalInput.getHeight()+dp(62)),true);
                    android.view.inputmethod.InputMethodManager ime=(android.view.inputmethod.InputMethodManager)a.getSystemService(Context.INPUT_METHOD_SERVICE);if(ime!=null)ime.showSoftInput(goalInput,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                });
            }else{
                hideInput();editor.setVisibility(View.GONE);goalInput.setError(null);goalInput.setText("");refreshGoalDisplay();
            }
        }
        void saveGoal(){
            if(closed||!editorOpen||!isAttachedToWindow()||a.isFinishing()||a.isDestroyed())return;
            final int value;
            try{String entered=goalInput.getText().toString().trim();value=entered.isEmpty()?0:Integer.parseInt(entered);}
            catch(NumberFormatException invalid){goalInput.setError("目标无效，请填写300–10000 kcal，或留空");return;}
            if(value!=0&&(value<300||value>10000)){goalInput.setError("请填写300–10000 kcal，或留空");return;}
            if(!host.saveDailyGoal(value)){goalInput.setError("目标保存失败，请重试");return;}
            setEditorOpen(false);
        }
        void refreshGoalDisplay(){
            int target=host.profile().optInt("dailyCalorieGoal"),calories=displayCalories(host.days().optJSONObject(date));
            energy.setText(calories+(target>0?" / "+target:"")+" kcal");goalEntry.setText(target>0?"调整每日目标  ›":"设置每日热量目标  ›");
        }
        void hideInput(){
            boolean ownsKeyboard=goalInput.hasFocus()&&goalInput.hasWindowFocus();android.os.IBinder token=goalInput.getWindowToken();goalInput.clearFocus();requestFocus();
            if(ownsKeyboard&&token!=null){android.view.inputmethod.InputMethodManager ime=(android.view.inputmethod.InputMethodManager)a.getSystemService(Context.INPUT_METHOD_SERVICE);if(ime!=null)ime.hideSoftInputFromWindow(token,0);}
        }
        void close(){if(closed)return;closed=true;editorOpen=false;editGeneration++;android.transition.TransitionManager.endTransitions(this);hideInput();goalInput.setOnEditorActionListener(null);goalInput.setText("");}
        @Override protected void onDetachedFromWindow(){close();super.onDetachedFromWindow();}
    }
    /** Content shrinks to its actual height, but stays scrollable above a real IME. */
    private final class SummaryScroll extends ScrollView {
        SummaryScroll(){super(a);setFillViewport(false);setOverScrollMode(View.OVER_SCROLL_NEVER);}
        @Override protected void onMeasure(int widthSpec,int heightSpec){
            // AlertDialog has already removed its title and the IME from this
            // bound. Subtracting another fixed allowance can strand actions
            // below an unnecessarily tiny viewport on short, large-font screens.
            int limit=MeasureSpec.getSize(heightSpec);
            if(MeasureSpec.getMode(heightSpec)==MeasureSpec.UNSPECIFIED){
                Rect visible=new Rect();a.getWindow().getDecorView().getWindowVisibleDisplayFrame(visible);limit=visible.height()>0?visible.height():getResources().getDisplayMetrics().heightPixels;
            }
            super.onMeasure(widthSpec,MeasureSpec.makeMeasureSpec(limit,MeasureSpec.AT_MOST));
        }
    }
    public static int displayCalories(JSONObject day){
        if(day==null)return 0;
        if(day.optBoolean("manualCaloriesOverride")){
            // New meal edits replace only that meal; untouched meals remain live AI values.
            // Legacy whole-day overrides retain their original total until explicitly edited.
            if(day.optBoolean("manualCaloriesByMeal")){
                double total=0;for(String key:NutritionStore.MEALS){double value=NutritionStore.displayMealCalories(day,key);if(Double.isFinite(value))total+=value;}return (int)Math.round(total);
            }
            if(day.optInt("manualCaloriesTotal")>0)return day.optInt("manualCaloriesTotal");
            int total=0;for(String k:NutritionStore.MEALS){JSONObject m=day.optJSONObject(k);if(m!=null&&!m.optBoolean("skipped"))total+=Math.max(0,m.optInt("manualCalories"));}return total;
        }
        JSONObject totals=NutritionStore.totals(day);
        return totals.optInt("count")>0?(int)Math.round(totals.optDouble("caloriesKcal")):Math.max(0,day.optInt("calories"));
    }
    private EditText goalInput(){
        // Keep the native focus underline, without the filled capsule used by standalone forms.
        EditText input=new EditText(a);input.setTag("daily_calorie_goal_input");input.setMinHeight(dp(48));
        input.setTextSize(AppTypography.sp(a,16));input.setTypeface(AppTypography.typeface(a,false));
        input.setTextColor(UiTokens.ink(host.night()));input.setHintTextColor(UiTokens.secondary(host.night()));
        input.setBackgroundTintList(new android.content.res.ColorStateList(new int[][]{{android.R.attr.state_focused},{}},new int[]{UiTokens.ink(host.night()),UiTokens.line(host.night())}));
        input.setPadding(0,dp(10),0,dp(10));input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);input.setHint("不填则隐藏目标");input.setContentDescription("每日热量目标，千卡，留空关闭");
        input.setMinHeight(dp(48)); // setSingleLine/setInputType can reset TextView's pixel minimum to one line.
        return input;
    }
    public void addMealItems(LinearLayout parent,final String date,final String meal,JSONObject value){
        JSONArray items=value.optJSONArray("foodItems");if(items==null)return;
        for(int i=0;i<items.length();i++){
            final JSONObject item=items.optJSONObject(i);if(item==null)continue;
            LinearLayout card=PaperComponents.card(a,host.night());
            String cutout=item.optString("cutoutFile"),file=cutout.isEmpty()?item.optString("sourcePhoto"):cutout;
            Bitmap bitmap=thumbnail(new File(a.getFilesDir(),file));
            if(bitmap!=null&&!cutout.isEmpty()){
                Bitmap original=bitmap;
                try{bitmap=FoodStickerRenderer.create(a,original,Math.max(7,Math.max(original.getWidth(),original.getHeight())/40));}
                catch(OutOfMemoryError ignored){bitmap=original;}
                if(bitmap!=original)original.recycle();
            }
            if(bitmap!=null){
                Bitmap rotated=FoodStickerRenderer.rotateDisplay(bitmap,NutritionStore.displayRotation(item));
                if(rotated!=bitmap)bitmap.recycle();bitmap=rotated;
                final Bitmap displayed=bitmap;
                ImageView image=new ImageView(a);image.setImageBitmap(displayed);image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                image.setContentDescription(item.optString("dishName","食物照片"));
                image.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){
                    public void onViewAttachedToWindow(View view){}
                    public void onViewDetachedFromWindow(View view){image.setImageDrawable(null);displayed.recycle();}
                });
                card.addView(image,new LinearLayout.LayoutParams(-1,dp(152)));
                image.setOnClickListener(v->host.preview(date,meal,item.optString("sourcePhoto")));
            }
            boolean ready="completed".equals(item.optString("status"));
            card.addView(text(item.optString("dishName").isEmpty()?"食物贴纸":item.optString("dishName"),18,true));
            JSONObject n=item.optJSONObject("nutrients");
            if(ready&&n!=null){
                card.addView(text(format(n.optDouble("caloriesKcal"))+" kcal",24,true));
                card.addView(text("碳水 "+format(n.optDouble("carbsG"))+"g    蛋白质 "+format(n.optDouble("proteinG"))+"g    脂肪 "+format(n.optDouble("fatG"))+"g",13,false));
                card.addView(text("膳食纤维 "+format(n.optDouble("fiberG"))+"g · 钠 "+format(n.optDouble("sodiumMg"))+"mg",12,false));
                card.addView(text((item.optBoolean("userEdited")?"用户修正":"AI 估算")+" · "+item.optInt("estimatedGrams")+"g · "+item.optInt("calorieLow")+"–"+item.optInt("calorieHigh")+" kcal",12,false));
                if(!item.optBoolean("userEdited")&&item.optDouble("confidence")<.55){TextView warning=text("建议核对：这份餐食识别把握较低",13,true);warning.setTextColor(UiTokens.yellow());card.addView(warning);}
            }
            String status=item.optString("analysisStatus");
            if("pending".equals(status))card.addView(text("识别排队中 · 联网后会自动保存",12,false));
            if("cutting_out".equals(status))card.addView(text("正在本地抠图…",12,false));
            if("recognizing".equals(status))card.addView(text("正在识别菜品与营养…",12,false));
            if("needs_retry".equals(status))card.addView(text(item.optString("error","请重试"),12,false));
            if("waiting_api".equals(status)||"awaiting_consent".equals(status))card.addView(text("营养待设置 · 我 > 智能助手",12,false));
            if(!item.optString("cutoutError").isEmpty())card.addView(text(item.optString("cutoutError"),12,false));
            TextView action=text("查看餐食详情  ›",14,true);action.setMinHeight(dp(48));action.setGravity(Gravity.CENTER_VERTICAL);
            action.setOnClickListener(v->details(date,meal,item));card.addView(action);parent.addView(card);
        }
    }
    private void actions(String date,String meal,JSONObject item){
        builder().setTitle(item.optString("dishName","食物记录"))
            .setItems(new String[]{"重新抠图","识别信息","手动修整贴纸","查看估算依据"},(d,which)->{
                if(which==0){
                    reprocess(date,meal,item,true);
                }else if(which==1){
                    AlertDialog confirm=builder().setTitle("重新识别营养？")
                        .setMessage("将使用原图和你已修正的菜名重新估算营养，不重新抠图。仅在识别已授权时发送至你配置的 API，可能产生费用；新结果通过校验前保留原数据。")
                        .setNegativeButton("取消",null).setPositiveButton("重新识别",(dialog,w)->reprocess(date,meal,item,false)).create();
                    confirm.show();PaperComponents.styleDialog(confirm,host.night());
                }else if(which==2){
                    String f=item.optString("cutoutFile");
                    final String output=f.isEmpty()?"food_cutout_manual_"+item.optString("id")+".png":f;
                    CutoutEditor.show(a,new File(a.getFilesDir(),item.optString("sourcePhoto")),new File(a.getFilesDir(),output),item.optJSONArray("cutoutRect"),host.night(),bounds->{
                        if(!FoodAnalysisJobService.saveCutout(a,date,meal,item.optString("sourcePhoto"),output,bounds))message("记录已变更，贴纸未写入；请重新打开后编辑");
                        host.reload();host.refresh();
                    });
                }else if(which==3){
                    builder().setTitle("AI 估算依据").setMessage(FoodRecordText.estimateNotes(item)).setPositiveButton("知道了",null).show();
                }
            }).show();
    }
    private void reprocess(String date,String meal,JSONObject item,boolean cutoutOnly){
        String source=item.optString("sourcePhoto");
        boolean accepted=cutoutOnly?FoodAnalysisJobService.enqueueCutout(a,date,meal,source):FoodAnalysisJobService.enqueueNutrition(a,date,meal,source);
        if(!accepted){message("暂时无法重新处理，请确认原图和记录仍在");return;}
        host.reload();host.refresh();host.processing(date,meal,source);
    }
    private void edit(String date,String meal,JSONObject item){
        LinearLayout box=new LinearLayout(a);box.setOrientation(1);box.setPadding(dp(20),0,dp(20),0);
        ScrollView scroll=new ScrollView(a);scroll.addView(box);
        String[] labels={"菜名","份量 g","热量 kcal","碳水 g","蛋白质 g","脂肪 g","纤维 g","钠 mg"};
        box.addView(text("只修改份量时，营养值会按重量比例调整；也可以直接填写营养值。",12,false));
        EditText[] inputs=new EditText[8];JSONObject old=item.optJSONObject("nutrients");
        for(int i=0;i<inputs.length;i++){
            box.addView(text(labels[i],13,false));EditText in=PaperComponents.input(a,host.night());
            if(i>0)in.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
            String value=i==0?item.optString("dishName"):i==1?(item.has("estimatedGrams")?format(item.optDouble("estimatedGrams")):""):old==null?"":format(old.optDouble(FoodAnalysisResult.LOCAL[i-2]));
            in.setText(value);inputs[i]=in;box.addView(in);
        }
        AlertDialog dialog=builder().setTitle("修正食物记录").setView(scroll)
            .setNegativeButton("取消",null).setPositiveButton("保存",null).create();
        dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{
            try{
                String name=inputs[0].getText().toString().trim();if(name.isEmpty()||name.length()>100)throw new Exception();
                String weight=inputs[1].getText().toString().trim();double grams=weight.isEmpty()?0:Double.parseDouble(weight);if(!weight.isEmpty()&&(!FoodAnalysisResult.finite(grams)||grams<=0||grams>10000))throw new Exception();
                JSONObject changed=new JSONObject(item.toString()), n=new JSONObject();
                boolean supplied=false;for(int i=2;i<8;i++)supplied|=inputs[i].getText().toString().trim().length()>0;
                changed.put("dishName",name).put("userEdited",true).put("analysisId",UUID.randomUUID().toString());
                if(grams>0)changed.put("estimatedGrams",grams);
                if(supplied){
                if(grams<=0)throw new Exception();
                boolean nutritionUntouched=old!=null;
                for(int i=0;i<6&&nutritionUntouched;i++)nutritionUntouched=Math.abs(Double.parseDouble(inputs[i+2].getText().toString())-old.optDouble(FoodAnalysisResult.LOCAL[i]))<.051;
                double ratio=nutritionUntouched&&item.optDouble("estimatedGrams")>0?grams/item.optDouble("estimatedGrams"):1;
                for(int i=0;i<6;i++){double x=Double.parseDouble(inputs[i+2].getText().toString());if(!FoodAnalysisResult.finite(x)||x<0||x>(i==5?100000:i==0?20000:5000))throw new Exception();n.put(FoodAnalysisResult.LOCAL[i],x);}
                if(ratio!=1)for(String key:FoodAnalysisResult.LOCAL)n.put(key,Math.round(n.getDouble(key)*ratio*10)/10.0);
                for(int i=0;i<6;i++)FoodAnalysisResult.number(n,FoodAnalysisResult.LOCAL[i],0,i==5?100000:i==0?20000:5000);
                changed.put("dishName",name).put("estimatedGrams",grams).put("nutrients",n).put("status","completed")
                    .put("analysisStatus","completed").put("userEdited",true).put("analysisId",UUID.randomUUID().toString())
                    .put("calorieLow",n.getDouble("caloriesKcal")).put("calorieHigh",n.getDouble("caloriesKcal"));
                }else{changed.remove("nutrients");changed.remove("calorieLow");changed.remove("calorieHigh");changed.put("status","pending").put("analysisStatus","edited");}
                if(!FoodAnalysisJobService.saveNutritionEdit(a,date,meal,item.optString("sourcePhoto"),changed))throw new Exception();dialog.dismiss();host.reload();host.refresh();
            }catch(Exception e){message("请检查菜名、份量和营养数值");}
        }));dialog.show();PaperComponents.styleDialog(dialog,host.night());
    }
    public void disableNutrition(){host.recognitionEnabled(false);message("已关闭自动营养识别，本地抠图仍可用");}

    public void recognizeSavedPhotos(){
        if(!FoodAnalysisJobService.enabled(host.profile())){host.recognition();return;}
        if(!FoodAnalysisJobService.configured(a,host.profile())){host.recognition();return;}
        AlertDialog dialog=builder().setTitle("识别已保存食物图片？")
            .setMessage("会将尚无识别结果的食物图片发送至你配置的视觉 API，可能产生费用。已有有效结果不会重复调用，不会重新抠图或覆盖已修正的营养。")
            .setNegativeButton("取消",null).setPositiveButton("开始识别",(d,w)->retryAll()).create();
        dialog.show();PaperComponents.styleDialog(dialog,host.night());
    }
    public void enableNutrition(){
        host.recognition();
    }
    private void retryAll(){
        if(!FoodAnalysisJobService.enabled(host.profile())){message("请先开启自动识别");return;}
        JSONArray dates=host.days().names();int count=0;
        for(int d=0;dates!=null&&d<dates.length();d++)for(String key:NutritionStore.MEALS){
            JSONObject meal=NutritionStore.meal(host.days(),dates.optString(d),key);JSONArray photos=meal==null?null:meal.optJSONArray("photos");
            Set<String> sources=new LinkedHashSet<>();
            for(int i=0;photos!=null&&i<photos.length();i++)sources.add(photos.optString(i));
            JSONArray foods=meal==null?null:meal.optJSONArray("foodItems");
            for(int i=0;foods!=null&&i<foods.length();i++){JSONObject food=foods.optJSONObject(i);if(food!=null)sources.add(food.optString("sourcePhoto"));}
            for(String source:sources)if(NutritionStore.safeName(source)){FoodAnalysisJobService.enqueue(a,dates.optString(d),key,source,false);count++;}
        }host.reload();host.refresh();message("已检查 "+count+" 张照片，已有结果不会重复调用");
    }
    public void weekly(String endDate){
        a.startActivity(new Intent(a,FoodWeeklyActivity.class).putExtra("date",endDate));
    }
    static Bitmap thumbnail(File file){
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getAbsolutePath(),bounds);
        BitmapFactory.Options opts=new BitmapFactory.Options();opts.inSampleSize=1;while(Math.max(bounds.outWidth,bounds.outHeight)/opts.inSampleSize>512)opts.inSampleSize*=2;
        return BitmapFactory.decodeFile(file.getAbsolutePath(),opts);
    }
    static String format(double n){return n==Math.rint(n)?""+(long)n:String.format(Locale.CHINA,"%.1f",n);}
}
