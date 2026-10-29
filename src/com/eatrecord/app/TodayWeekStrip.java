package com.eatrecord.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.os.Bundle;
import android.graphics.Canvas;
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

    public TodayWeekStrip(Context context,boolean night,Calendar selected,Calendar anchor,Listener listener){
        super(context);this.night=night;this.selected=(Calendar)selected.clone();this.start=(Calendar)anchor.clone();this.listener=listener;
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
    private String key(Calendar day){return new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).format(day.getTime());}
    public Calendar weekStart(){return (Calendar)start.clone();}
    public LinearLayout currentRow(){return pages[1];}
    private void populateRow(int page){Calendar base=(Calendar)start.clone();base.add(Calendar.DATE,(page-1)*7);populateRow(page,base);}
    private void populateRow(int page,Calendar base){
        String today=key(Calendar.getInstance());String[] names={"一","二","三","四","五","六","日"};
            LinearLayout row=pages[page];row.removeAllViews();Calendar day=(Calendar)base.clone();
            for(int i=0;i<7;i++){
                final Calendar picked=(Calendar)day.clone();String date=key(picked);boolean active=date.equals(selectedKey),future=date.compareTo(today)>0;
                TextView cell=PaperComponents.text(getContext(),names[i]+"\n"+day.get(Calendar.DAY_OF_MONTH),14,UiTokens.ink(night),false);
                cell.getPaint().setFakeBoldText(active);
                cell.setTag("today_week_day_"+date);cell.setGravity(Gravity.CENTER);cell.setPadding(0,dp(6),0,dp(6));cell.setMinHeight(dp(64));
                cell.setAlpha(future?.3f:1f);cell.setEnabled(!future);cell.setSelected(active);
                cell.setContentDescription(date+(future?"，未来日期不可选择":active?"，已选择":""));
                cell.setOnClickListener(v->{if(!future&&listener!=null&&ownsRoot())listener.onDay((Calendar)picked.clone());});
                row.addView(cell,new LinearLayout.LayoutParams(0,-2,1));day.add(Calendar.DATE,1);
            }
    }
    private void rebuild(){
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
            cell.setContentDescription(date+(!cell.isEnabled()?"，未来日期不可选择":active?"，已选择":""));
        }
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);selectionBounds.setEmpty();
        for(LinearLayout row:pages){
            View from=row.findViewWithTag(dayPreview?previewFromTag:selectedTag);
            View to=dayPreview?row.findViewWithTag(previewToTag):null;
            if(from!=null&&to!=null){drawMarker(canvas,row,from.getLeft()+(to.getLeft()-from.getLeft())*dayProgress,from.getTop(),from.getWidth(),from.getHeight(),1);}
            else if(from!=null)drawMarker(canvas,row,from.getLeft(),from.getTop(),from.getWidth(),from.getHeight(),dayPreview?1-dayProgress:1);
            else if(to!=null)drawMarker(canvas,row,to.getLeft(),to.getTop(),to.getWidth(),to.getHeight(),dayProgress);
        }
    }
    private void drawMarker(Canvas canvas,LinearLayout row,float x,float y,int width,int height,float opacity){
        if(width<=0||height<=0||opacity<=0)return;float left=row.getLeft()+row.getTranslationX()+x,top=row.getTop()+y;
        if(left+width<=0||left>=getWidth())return;
        dayMarker.setBounds(0,0,width,height);dayMarker.setAlpha(Math.round(255*opacity));int saved=canvas.save();canvas.translate(left,top);dayMarker.draw(canvas);canvas.restoreToCount(saved);dayMarker.setAlpha(255);
        if(opacity>=.5f)selectionBounds.set(left,top,left+width,top+height);
    }
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);if(w!=oldw){stopAnimation();offset=0;}positionPages();}
    private void commit(int direction){start.add(Calendar.DATE,direction*7);rebuild();if(listener!=null&&ownsRoot())listener.onWeek(weekStart());sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_SCROLLED);}
    private boolean ownsRoot(){return rootActive&&TodayDayPager.isLiveHierarchy(this);}
    void setRootActive(boolean active){
        if(rootActive==active){if(active)resumeRootMotion();return;}
        rootActive=active;if(!active)pauseRootMotion();else resumeRootMotion();
    }
    private void pauseRootMotion(){
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
    @Override protected void onDetachedFromWindow(){rootResumePending=false;rootResumeVelocity=0;stopAnimation();releaseTracker();dragging=false;pointerId=-1;offset=0;positionPages();super.onDetachedFromWindow();}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(visibility!=VISIBLE&&spring!=null){rootResumePending=false;rootResumeVelocity=0;stopAnimation();releaseTracker();dragging=false;pointerId=-1;offset=0;positionPages();}}
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
