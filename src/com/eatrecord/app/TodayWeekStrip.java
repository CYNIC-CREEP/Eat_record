package com.eatrecord.app;

import android.animation.ValueAnimator;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.os.Bundle;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.VelocityTracker;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;

/** Three real pages: drag tracks the finger; release settles without replacing visible text. */
public final class TodayWeekStrip extends FrameLayout {
    public interface Listener { void onDay(Calendar date); void onWeek(Calendar start); }
    private final Calendar selected,start;
    private final boolean night;
    private final Listener listener;
    private final LinearLayout[] pages=new LinearLayout[3];
    private final GestureSpring spring;
    private VelocityTracker velocityTracker;
    private int pointerId = -1, settleDirection;
    private float offset,downX,downY,lastX;
    private boolean dragging,cancelled;
    private boolean rootActive=true,rootResumePending;
    private float rootResumeVelocity;
    private final Drawable dayMarker;
    private final RectF selectionBounds=new RectF();
    private long previewFromStamp=Long.MIN_VALUE,previewToStamp=Long.MIN_VALUE;
    private String selectedKey,previewFromKey,previewToKey,selectedTag,previewFromTag,previewToTag;
    private int previewWeekShift;
    private float dayProgress;
    private boolean dayPreview;
    private String previewTargetWeek;
    private String referenceMonth;
    private final Map<String,Integer> calorieDays=new HashMap<>();
    private int calorieGoal;
    private boolean waterActive=true;
    private int waterAccent,waterSoft;
    private final Paint waterPaint=new Paint(Paint.ANTI_ALIAS_FLAG),waterLine=new Paint(Paint.ANTI_ALIAS_FLAG),bottleLine=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final DashPathEffect bottleDash;
    private final Path waterClip=new Path(),waterFill=new Path(),waterSurface=new Path();
    private final RectF waterBounds=new RectF();
    private ValueAnimator waterAnimator;
    private float waterPhase,waterAmplitude;
    private String waterDate,waterFromDate;

    public TodayWeekStrip(Context context,boolean night,Calendar selected,Calendar anchor,Listener listener){
        super(context);this.night=night;this.selected=(Calendar)selected.clone();this.start=(Calendar)anchor.clone();this.listener=listener;
        waterAccent=UiTokens.ink(night);waterSoft=UiTokens.line(night);waterLine.setStyle(Paint.Style.STROKE);waterLine.setStrokeWidth(Math.max(1,dp(.65f)));
        bottleLine.setStyle(Paint.Style.STROKE);bottleLine.setStrokeWidth(Math.max(1,dp(.75f)));bottleDash=new DashPathEffect(new float[]{dp(3),dp(3)},0);
        selectedKey=key(selected);selectedTag="today_week_day_"+selectedKey;dayMarker=PaperComponents.rounded(context,night);setWillNotDraw(false);
        spring = new GestureSpring(new GestureSpring.Listener() {
            public void onUpdate(float value) {
                if(isAttachedToWindow()&&!ownsRoot()){pauseRootMotion();return;}
                offset = value; positionPages();
            }
            public void onEnd() {
                int direction = settleDirection;
                settleDirection = 0;
                offset = 0;
                if (direction != 0) commit(direction);
                positionPages();
            }
        });
        start.add(Calendar.DATE,-((start.get(Calendar.DAY_OF_WEEK)+5)%7));
        setTag("today_week");setClickable(true);setFocusable(true);setClipChildren(true);setClipToPadding(true);
        for(int i=0;i<3;i++){
            pages[i]=new LinearLayout(context);pages[i].setOrientation(LinearLayout.HORIZONTAL);pages[i].setGravity(Gravity.CENTER);
            pages[i].setImportantForAccessibility(i==1?IMPORTANT_FOR_ACCESSIBILITY_AUTO:IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            addView(pages[i],new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER_VERTICAL));
        }
        rebuild();
    }
    private int dp(float n){return PaperComponents.dp(getContext(),n);}
    private float px(float n){return n*getResources().getDisplayMetrics().density;}
    private String key(Calendar day){return new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).format(day.getTime());}
    public Calendar weekStart(){return (Calendar)start.clone();}
    public LinearLayout currentRow(){return pages[1];}
    /** Read-only data supplied alongside the meal sheet; drawing never reads preferences. */
    public void setCalorieProgress(int goal,Map<String,Integer> byDate){
        calorieGoal=Math.max(0,goal);calorieDays.clear();
        if(byDate!=null)for(Map.Entry<String,Integer> entry:byDate.entrySet())if(entry.getKey()!=null&&entry.getValue()!=null&&entry.getValue()>0)calorieDays.put(entry.getKey(),entry.getValue());
        for(LinearLayout row:pages)for(int i=0;i<row.getChildCount();i++)updateWater((DayCell)row.getChildAt(i));
        if(calorieGoal==0||!hasWater(waterDate))stopWaterMotion();invalidate();
    }
    public void setPalette(int accent,int accentSoft){waterAccent=accent;waterSoft=accentSoft;invalidate();}
    /** The month calendar is a separate presentation and has no calorie bottles. */
    public void setWaterActive(boolean active){if(waterActive==active)return;waterActive=active;if(!active)stopWaterMotion();invalidate();}
    private final class DayCell extends TextView{
        final String date,month;final boolean today;
        final int weekday;
        final float waveAmplitudeFactor,waveFrequencyFactor,wavePhaseOffset;
        int calories;float waterLevel;
        DayCell(String date,String text,boolean today,int weekday){
            super(TodayWeekStrip.this.getContext());this.date=date;this.month=date.substring(0,7);this.today=today;this.weekday=weekday;
            waveAmplitudeFactor=UiTokens.weekWaterAmplitude(weekday);waveFrequencyFactor=UiTokens.weekWaterFrequency(weekday);wavePhaseOffset=UiTokens.weekWaterPhase(weekday);
            setText(text);setTextSize(AppTypography.sp(getContext(),14));setTextColor(UiTokens.ink(night));setTypeface(AppTypography.typeface(getContext(),false));setIncludeFontPadding(true);setLineSpacing(dp(2),1.04f);
        }
    }
    private void updateWater(DayCell cell){
        boolean outMonth=!cell.month.equals(referenceMonth);cell.setAlpha(outMonth?.43f:1f);
        Integer value=calorieDays.get(cell.date);cell.calories=cell.isEnabled()&&value!=null?value:0;
        cell.waterLevel=calorieGoal>0&&cell.calories>0?Math.min(1f,(float)cell.calories/calorieGoal):0;
        cell.setContentDescription(cell.date+(outMonth?"，非本月":"")+(!cell.isEnabled()?"，未来日期不可选择":cell.isSelected()?"，已选择":"")
            +(calorieGoal>0&&cell.calories>0?"，摄入"+cell.calories+"千卡，目标"+calorieGoal+"千卡":""));
    }
    private void populateRow(int page){Calendar base=(Calendar)start.clone();base.add(Calendar.DATE,(page-1)*7);populateRow(page,base);}
    private void populateRow(int page,Calendar base){
        String today=key(Calendar.getInstance());String[] names={"一","二","三","四","五","六","日"};
            LinearLayout row=pages[page];row.removeAllViews();Calendar day=(Calendar)base.clone();
            for(int i=0;i<7;i++){
                final Calendar picked=(Calendar)day.clone();String date=key(picked);boolean active=date.equals(selectedKey),future=date.compareTo(today)>0;
                DayCell cell=new DayCell(date,names[i]+"\n"+day.get(Calendar.DAY_OF_MONTH),date.equals(today),(picked.get(Calendar.DAY_OF_WEEK)+5)%7);
                cell.getPaint().setFakeBoldText(active);
                cell.setTag("today_week_day_"+date);cell.setGravity(Gravity.CENTER);cell.setPadding(0,dp(6),0,dp(6));cell.setMinHeight(dp(64));
                cell.setEnabled(!future);cell.setSelected(active);
                updateWater(cell);
                cell.setOnClickListener(v->{if(!future&&listener!=null&&ownsRoot()){startWaterMotion(selectedKey,date,cell.weekday);listener.onDay((Calendar)picked.clone());}});
                row.addView(cell,new LinearLayout.LayoutParams(0,-2,1));day.add(Calendar.DATE,1);
            }
    }
    private void rebuild(){
        // The compact strip has no browsed-month heading. Its fading follows the
        // real current month, never the diary's changing selected date.
        referenceMonth=key(Calendar.getInstance()).substring(0,7);
        previewTargetWeek=null;
        for(int page=0;page<3;page++)populateRow(page);
        updateRangeDescription();positionPages();
    }
    private void updateRangeDescription(){
        Calendar end=(Calendar)start.clone();end.add(Calendar.DATE,6);
        setContentDescription("周日期，"+key(start)+"至"+key(end)+"，左右滑动切换周");
    }
    private void positionPages(){for(int i=0;i<3;i++)pages[i].setTranslationX((i-1)*getWidth()+offset);invalidate();}
    private void alignWeek(Calendar day){
        Calendar monday=HomeCalendarDates.monday(day);
        if(start.getTimeInMillis()==monday.getTimeInMillis())return;
        Calendar previous=(Calendar)start.clone(),next=(Calendar)start.clone();previous.add(Calendar.DATE,-7);next.add(Calendar.DATE,7);
        int direction=next.getTimeInMillis()==monday.getTimeInMillis()?1:previous.getTimeInMillis()==monday.getTimeInMillis()?-1:0;
        start.setTimeInMillis(monday.getTimeInMillis());
        if(direction>0){LinearLayout far=pages[0];pages[0]=pages[1];pages[1]=pages[2];pages[2]=far;populateRow(2);}
        else if(direction<0){LinearLayout far=pages[2];pages[2]=pages[1];pages[1]=pages[0];pages[0]=far;populateRow(0);}
        else for(int i=0;i<3;i++)populateRow(i);
        for(int i=0;i<3;i++)pages[i].setImportantForAccessibility(i==1?IMPORTANT_FOR_ACCESSIBILITY_AUTO:IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        updateRangeDescription();
        positionPages();
    }
    /** The diary owns this progress; do not start an independent calendar animator. */
    public void setDayProgress(Calendar from,Calendar to,float fraction){
        boolean moving=!key(from).equals(key(to));
        if(previewFromStamp!=from.getTimeInMillis()||previewToStamp!=to.getTimeInMillis()){
            if(moving&&dayPreview&&previewToStamp==from.getTimeInMillis()){
                alignWeek(from);previewTargetWeek=null;
            }
            previewFromStamp=from.getTimeInMillis();previewToStamp=to.getTimeInMillis();previewFromKey=key(from);previewToKey=key(to);
            if(moving&&(!previewToKey.equals(waterDate)||!previewFromKey.equals(waterFromDate)))startWaterMotion(previewFromKey,previewToKey,(to.get(Calendar.DAY_OF_WEEK)+5)%7);
            previewFromTag="today_week_day_"+previewFromKey;previewToTag="today_week_day_"+previewToKey;previewWeekShift=HomeDayMotion.weekShift(from,to);
            rootResumePending=false;rootResumeVelocity=0;stopAnimation();
            if(!moving){
                alignWeek(from);
                if(previewTargetWeek!=null){for(int i=0;i<3;i++)populateRow(i);previewTargetWeek=null;}
            }else{
                // A browsed week may already show the tapped date; do not jump back
                // to the old diary week before starting the shared transition.
                previewWeekShift=HomeDayMotion.weekShift(start,to);
                String targetWeek=key(HomeCalendarDates.monday(to));
                boolean natural=HomeDayMotion.hasWeekPage(start,to);
                if(!(natural&&previewTargetWeek==null)&&!targetWeek.equals(previewTargetWeek)){
                    populateRow(0);populateRow(2);
                    if(!natural&&previewWeekShift!=0)populateRow(1+previewWeekShift,HomeCalendarDates.monday(to));
                    previewTargetWeek=natural?null:targetWeek;
                }
            }
        }
        dayProgress=Math.max(0,Math.min(1,fraction));dayPreview=!previewFromKey.equals(previewToKey)&&dayProgress>0;
        Calendar shown=dayProgress>=.5f?to:from;String shownKey=dayProgress>=.5f?previewToKey:previewFromKey;
        if(!shownKey.equals(selectedKey)){selected.setTimeInMillis(shown.getTimeInMillis());selectedKey=shownKey;selectedTag="today_week_day_"+selectedKey;updateSelectedSemantics();}
        offset=dayPreview?-previewWeekShift*getWidth()*dayProgress:0;
        positionPages();
    }
    private void updateSelectedSemantics(){
        for(LinearLayout page:pages)for(int i=0;i<page.getChildCount();i++){
            View cell=page.getChildAt(i);String tag=String.valueOf(cell.getTag()),date=tag.substring("today_week_day_".length());boolean active=date.equals(selectedKey);
            if(cell.isSelected()!=active){cell.setSelected(active);((TextView)cell).getPaint().setFakeBoldText(active);cell.invalidate();}
            updateWater((DayCell)cell);
        }
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);selectionBounds.setEmpty();
        // Draw the stationary date containers first. The moving selection marker
        // keeps its existing solid paper surface and covers any crossing dashes.
        if(waterActive&&calorieGoal>0)for(LinearLayout row:pages)for(int i=0;i<row.getChildCount();i++)drawBottle(canvas,row,(DayCell)row.getChildAt(i));
        for(LinearLayout row:pages){
            View from=row.findViewWithTag(dayPreview?previewFromTag:selectedTag);
            View to=dayPreview?row.findViewWithTag(previewToTag):null;
            if(from!=null&&to!=null){drawMarker(canvas,row,from.getLeft()+(to.getLeft()-from.getLeft())*dayProgress,from.getTop(),from.getWidth(),from.getHeight(),1);}
            else if(from!=null)drawMarker(canvas,row,from.getLeft(),from.getTop(),from.getWidth(),from.getHeight(),dayPreview?1-dayProgress:1);
            else if(to!=null)drawMarker(canvas,row,to.getLeft(),to.getTop(),to.getWidth(),to.getHeight(),dayProgress);
        }
        if(waterActive&&calorieGoal>0)for(LinearLayout row:pages)for(int i=0;i<row.getChildCount();i++)drawWater(canvas,row,(DayCell)row.getChildAt(i));
    }
    private void drawBottle(Canvas canvas,LinearLayout row,DayCell cell){
        if(cell.isSelected()||cell.getWidth()<=0||cell.getHeight()<=0)return;
        float left=row.getLeft()+row.getTranslationX()+cell.getLeft(),top=row.getTop()+cell.getTop();
        if(left+cell.getWidth()<=0||left>=getWidth())return;
        float inset=dp(1),width=cell.getWidth()-inset*2,height=cell.getHeight()-inset*2;
        if(width<=0||height<=0)return;
        waterBounds.set(left+inset,top+inset,left+inset+width,top+inset+height);float radius=Math.min(width,height)*.5f;
        // Month membership dims the date label, not its progress container.
        bottleLine.setColor(UiTokens.line(night));bottleLine.setAlpha(cell.today?190:160);
        bottleLine.setPathEffect(cell.today?null:bottleDash);canvas.drawRoundRect(waterBounds,radius,radius,bottleLine);
    }
    private void drawWater(Canvas canvas,LinearLayout row,DayCell cell){
        float level=cell.waterLevel;if(level<=0||cell.getWidth()<=0||cell.getHeight()<=0)return;
        float left=row.getLeft()+row.getTranslationX()+cell.getLeft(),top=row.getTop()+cell.getTop();
        if(left+cell.getWidth()<=0||left>=getWidth())return;
        float inset=dp(1),width=cell.getWidth()-inset*2,height=cell.getHeight()-inset*2;
        if(width<=0||height<=0)return;
        waterBounds.set(left+inset,top+inset,left+inset+width,top+inset+height);float radius=Math.min(width,height)*.5f;
        // Keep historical progress just as legible as this month's progress.
        waterPaint.setColor(waterSoft);waterPaint.setAlpha(night?45:35);
        if(level>=1){canvas.drawRoundRect(waterBounds,radius,radius,waterPaint);return;}
        float room=height*Math.min(level,1-level),still=Math.min(px(UiTokens.WEEK_WATER_STATIC_DP),room*.4f);
        boolean sloshing=cell.date.equals(waterDate);
        float amplitude=sloshing?Math.min(px(UiTokens.WEEK_WATER_SLOSH_DP)*cell.waveAmplitudeFactor*waterAmplitude,room*.5f):0;
        float baseline=waterBounds.bottom-height*level;
        waterClip.reset();waterClip.addRoundRect(waterBounds,radius,radius,Path.Direction.CW);
        waterFill.reset();waterSurface.reset();
        for(int i=0;i<=24;i++){
            float u=i/24f,x=waterBounds.left+width*u;
            float y=baseline+still*(float)Math.sin(u*Math.PI*2)+amplitude*(float)Math.sin(u*Math.PI*2+waterPhase+cell.wavePhaseOffset);
            if(i==0){waterFill.moveTo(x,y);waterSurface.moveTo(x,y);}else{waterFill.lineTo(x,y);waterSurface.lineTo(x,y);}
        }
        waterFill.lineTo(waterBounds.right,waterBounds.bottom);waterFill.lineTo(waterBounds.left,waterBounds.bottom);waterFill.close();
        int clipped=canvas.save();canvas.clipPath(waterClip);canvas.drawPath(waterFill,waterPaint);
        waterLine.setColor(waterAccent);waterLine.setAlpha(night?20:15);canvas.drawPath(waterSurface,waterLine);canvas.restoreToCount(clipped);
    }
    private boolean hasWater(String date){Integer calories=calorieDays.get(date);return calorieGoal>0&&calories!=null&&calories>0;}
    private boolean canMoveWater(){return waterActive&&calorieGoal>0&&ownsRoot()&&isShown()&&getWindowVisibility()==VISIBLE&&ValueAnimator.areAnimatorsEnabled();}
    private void startWaterMotion(String from,String to,int weekday){
        if(!canMoveWater()||!hasWater(to)){stopWaterMotion();return;}
        // Capture the displayed phase/amplitude so rapid taps do not reset the surface.
        boolean sameTarget=to.equals(waterDate);
        float phase=sameTarget?waterPhase:0,amplitude=sameTarget?waterAmplitude:0;
        final float frequency=UiTokens.weekWaterFrequency(weekday);
        cancelWaterAnimator();waterFromDate=from;waterDate=to;waterPhase=phase;waterAmplitude=amplitude;
        final ValueAnimator animator=ValueAnimator.ofFloat(0,1);waterAnimator=animator;
        animator.setDuration(UiTokens.WEEK_WATER_SLOSH_MS);animator.setInterpolator(new android.view.animation.LinearInterpolator());
        animator.addUpdateListener(value->{
            if(waterAnimator!=animator)return;
            if(!canMoveWater()){stopWaterMotion();return;}
            float t=(float)value.getAnimatedValue(),remaining=1-t;
            waterPhase=phase+t*(float)(Math.PI*3)*frequency;
            waterAmplitude=Math.min(1f,amplitude*remaining*remaining+1.6f*(float)Math.sin(Math.PI*t)*remaining);
            invalidate();
        });
        animator.addListener(new AnimatorListenerAdapter(){@Override public void onAnimationEnd(Animator animation){if(waterAnimator==animator){waterAnimator=null;waterAmplitude=0;waterPhase=0;invalidate();}}});animator.start();
    }
    private void cancelWaterAnimator(){ValueAnimator previous=waterAnimator;waterAnimator=null;if(previous!=null)previous.cancel();}
    private void stopWaterMotion(){cancelWaterAnimator();waterAmplitude=0;waterPhase=0;waterDate=waterFromDate=null;invalidate();}
    private void drawMarker(Canvas canvas,LinearLayout row,float x,float y,int width,int height,float opacity){
        if(width<=0||height<=0||opacity<=0)return;float left=row.getLeft()+row.getTranslationX()+x,top=row.getTop()+y;
        if(left+width<=0||left>=getWidth())return;
        dayMarker.setBounds(0,0,width,height);dayMarker.setAlpha(Math.round(255*opacity));int saved=canvas.save();canvas.translate(left,top);dayMarker.draw(canvas);canvas.restoreToCount(saved);dayMarker.setAlpha(255);
        if(opacity>=.5f)selectionBounds.set(left,top,left+width,top+height);
    }
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);if(w!=oldw||h!=oldh)stopWaterMotion();if(w!=oldw){stopAnimation();offset=0;}positionPages();}
    private void commit(int direction){start.add(Calendar.DATE,direction*7);rebuild();if(listener!=null&&ownsRoot())listener.onWeek(weekStart());sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_SCROLLED);}
    private boolean ownsRoot(){return rootActive&&TodayDayPager.isLiveHierarchy(this);}
    void setRootActive(boolean active){
        if(rootActive==active){if(active)resumeRootMotion();return;}
        rootActive=active;if(!active)pauseRootMotion();else resumeRootMotion();
    }
    private void pauseRootMotion(){
        stopWaterMotion();
        boolean running=spring.isRunning();rootResumePending|=running||dragging;
        if(running)rootResumeVelocity=spring.velocity();
        stopAnimation();releaseTracker();dragging=false;cancelled=true;pointerId=-1;
        if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);
    }
    private void resumeRootMotion(){
        if(!rootResumePending||!ownsRoot())return;
        rootResumePending=false;float velocity=rootResumeVelocity;rootResumeVelocity=0;cancelled=false;
        if(!dayPreview)settle(settleDirection,velocity);
    }
    public boolean isInteracting(){return rootActive&&(dragging||spring.isRunning());}
    private void stopAnimation(){spring.cancel();}
    private void settle(final int direction){settle(direction,0f);}
    private void settle(final int direction,float velocity){
        stopAnimation();settleDirection=direction;float target=-direction*getWidth();
        if(!ValueAnimator.areAnimatorsEnabled()||!isAttachedToWindow()||getWidth()==0||Math.abs(target-offset)<.5f){offset=0;if(direction!=0)commit(direction);positionPages();return;}
        spring.start(offset,target,velocity);
    }
    private void dragBy(float dx){
        if(!ValueAnimator.areAnimatorsEnabled())return;
        offset+=dx;int width=getWidth();
        // Rotate pages at a full-width boundary without changing visible pixels.
        // An interrupted settle can continue or reverse from its current offset.
        if(width>0){while(offset<=-width){offset+=width;commit(1);}while(offset>=width){offset-=width;commit(-1);}}
        positionPages();
    }
    private void instant(int direction){stopAnimation();offset=0;commit(direction);positionPages();}
    private void releaseTracker(){if(velocityTracker!=null){velocityTracker.recycle();velocityTracker=null;}}
    @Override protected void onDetachedFromWindow(){stopWaterMotion();rootResumePending=false;rootResumeVelocity=0;stopAnimation();releaseTracker();dragging=false;pointerId=-1;offset=0;positionPages();super.onDetachedFromWindow();}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(visibility!=VISIBLE&&spring!=null){stopWaterMotion();rootResumePending=false;rootResumeVelocity=0;stopAnimation();releaseTracker();dragging=false;pointerId=-1;offset=0;positionPages();}}
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);info.setScrollable(true);info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);}
    @Override public boolean performAccessibilityAction(int action,Bundle args){
        if(!rootActive)return false;
        if(action==AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD){instant(-1);return true;}
        if(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD){instant(1);return true;}
        return super.performAccessibilityAction(action,args);
    }
    private boolean horizontal(float x,float y){float dx=x-downX,dy=y-downY;return Math.abs(dx)>ViewConfiguration.get(getContext()).getScaledTouchSlop()&&Math.abs(dx)>Math.abs(dy)*1.3f;}
    @Override public boolean onInterceptTouchEvent(MotionEvent event){return rootActive;}
    @Override public boolean onTouchEvent(MotionEvent event){
        if(!rootActive)return false;
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){stopAnimation();releaseTracker();velocityTracker=VelocityTracker.obtain();velocityTracker.addMovement(event);pointerId=event.getPointerId(0);downX=lastX=event.getX();downY=event.getY();dragging=false;cancelled=false;return true;}
        if(velocityTracker!=null)velocityTracker.addMovement(event);
        if(action==MotionEvent.ACTION_POINTER_DOWN){cancelled=true;dragging=false;settle(0);releaseTracker();if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);return true;}
        int pointer=event.findPointerIndex(pointerId);
        if(pointer<0){cancelled=true;dragging=false;pointerId=-1;settle(0);releaseTracker();if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);return true;}
        float x=event.getX(pointer),y=event.getY(pointer);
        if(action==MotionEvent.ACTION_MOVE&&!cancelled){
            if(dragging||horizontal(x,y)){if(!dragging){dragging=true;dragBy(x-downX);}else dragBy(x-lastX);if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);}
            else if(Math.abs(y-downY)>ViewConfiguration.get(getContext()).getScaledTouchSlop())cancelled=true;
            lastX=x;
        }
        if(action==MotionEvent.ACTION_UP){
            float dx=x-downX,dy=y-downY,velocity=0;
            if(velocityTracker!=null){velocityTracker.computeCurrentVelocity(1000,ViewConfiguration.get(getContext()).getScaledMaximumFlingVelocity());velocity=velocityTracker.getXVelocity(pointerId);}
            if(!cancelled&&(dragging||horizontal(x,y))){
                if(!dragging)dragBy(dx);else dragBy(x-lastX);
                float position=ValueAnimator.areAnimatorsEnabled()?offset:dx;
                float projected=position+velocity/1000f*.99f/(1f-.99f);
                boolean change=Math.abs(projected)>=Math.min(dp(36),getWidth()*.15f);
                settle(change?(projected<0?1:-1):0,velocity);
            }else if(!cancelled&&Math.abs(dx)<=ViewConfiguration.get(getContext()).getScaledTouchSlop()&&Math.abs(dy)<=ViewConfiguration.get(getContext()).getScaledTouchSlop()){
                performClick();boolean picked=false;
                for(int p=0;p<3&&!picked;p++){
                    float localX=x-pages[p].getTranslationX();android.graphics.Rect hit=new android.graphics.Rect();
                    for(int i=0;i<7;i++){View cell=pages[p].getChildAt(i);cell.getHitRect(hit);if(hit.contains((int)localX,(int)y)){if(cell.isEnabled()){picked=true;cell.performClick();}break;}}
                }
                if(!picked)settle(Math.abs(offset)>getWidth()/2f?(offset<0?1:-1):0);
            }else settle(0);
            dragging=false;releaseTracker();pointerId=-1;if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);
        }else if(action==MotionEvent.ACTION_CANCEL){dragging=false;cancelled=true;settle(0);releaseTracker();pointerId=-1;if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);}
        return true;
    }
}
