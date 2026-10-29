package com.eatrecord.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import java.util.ArrayList;
import java.util.Calendar;

/** Three real diary pages; browsing is read-only and never manufactures a future diary. */
public final class TodayDayPager extends FrameLayout {
    public interface Factory { View create(Calendar date); }
    public interface Listener {
        void onDate(Calendar date); boolean canSwipe();
        default void onVisibleDate(Calendar date) { }
        /** Borrowed immutable dates and the same normalized displacement as the real sheets. */
        default void onDayProgress(Calendar from,Calendar to,float fraction) { }
    }
    private final Factory factory;
    private final Listener listener;
    private final View[] pages = new View[3];
    private final Calendar[] pageDates=new Calendar[3];
    private final String[] pageKeys=new String[3];
    private String todayKey;
    private Calendar date;
    private final GestureSpring spring;
    private float offset, downX, downY, dragOrigin;
    private boolean dragging, cancelled;
    private int pointer = -1, destination;
    private VelocityTracker tracker;
    private boolean attached;
    private boolean rootActive=true,rootResumePending;
    private float rootResumeVelocity;
    private String visibleDate;
    private long trackedTime=Long.MIN_VALUE;
    private int trackedAction=-1;
    private float trackedX,trackedY;
    /** During a date tap only the visible sheets and the requested date are retained. */
    private ArrayList<Sheet> selection;
    private Sheet selectionFrom,selectionTo;
    private Calendar retainedSelection;
    private Calendar selectionCancelTarget;
    private boolean selectionRebasedDuringGesture;
    private boolean resetSelectionOnAttach;
    private static final class Sheet {
        final View view;
        final Calendar date;
        float position;
        Sheet(View view,Calendar date,float position){this.view=view;this.date=HomeCalendarDates.day(date);this.position=position;}
    }

    public TodayDayPager(Context context, Calendar selected, Factory factory, Listener listener) {
        super(context);
        date = HomeCalendarDates.day(selected);
        refreshDates();
        this.factory = factory;
        this.listener = listener;
        setTag("today_day_pager");
        setClipChildren(true);setClipToPadding(true);setFocusable(true);setClickable(true);
        spring = new GestureSpring(new GestureSpring.Listener() {
            public void onUpdate(float value) { if(freezeRetained())return;offset = value; positionPages(); }
            public void onEnd() {
                if(selection!=null){finishSelection(selectionTo,0);return;}
                int direction = destination; destination = 0;
                if (direction != 0 && allowed(direction)) commit(direction);
                else { offset = 0; positionPages(); }
            }
        });
        for (int i = 0; i < 3; i++) install(i);
        updateAccessibility();
    }

    /** Preserve an explicit in-flight date choice when an idle root cache must be rebuilt. */
    Calendar rootDestination(){
        if(selection!=null&&selectionTo!=null)return (Calendar)(retainedSelection==null?selectionTo.date:retainedSelection).clone();
        if(destination!=0&&allowed(destination))return (Calendar)relative(destination).clone();
        return currentDate();
    }
    boolean hasPendingRootMotion(){return rootResumePending||selection!=null||destination!=0||dragging||spring.isRunning()||Math.abs(offset)>.5f;}
    public Calendar currentDate() { return (Calendar) (retainedSelection==null?date:retainedSelection).clone(); }
    /** Disappearing transition children remain attached, but no longer own screen state. */
    static boolean isLiveHierarchy(View view){
        if(!view.isAttachedToWindow())return false;
        View child=view;ViewParent parent=child.getParent();
        while(parent instanceof ViewGroup){
            ViewGroup group=(ViewGroup)parent;
            if(group.indexOfChild(child)<0)return false;
            if(group instanceof RootPagePager&&!((RootPagePager)group).ownsView(view))return false;
            child=group;parent=child.getParent();
        }
        // A retained disappearing root may already have lost mParent while Android
        // deliberately keeps its window attachment until endViewTransition().
        return parent!=null;
    }
    private boolean freezeRetained(){
        if(attached&&(!rootActive||!isLiveHierarchy(this))){pauseRootMotion();return true;}return false;
    }
    /** Root pages stay attached so food bitmaps remain owned by their real Views. */
    void setRootActive(boolean active){
        if(rootActive==active){if(active)resumeRootMotion();return;}
        rootActive=active;
        if(!active)pauseRootMotion();else{visibleDate=null;positionPages();resumeRootMotion();}
    }
    private void pauseRootMotion(){
        boolean running=spring.isRunning();
        rootResumePending|=running||dragging||selection!=null||Math.abs(offset)>.5f;
        if(running)rootResumeVelocity=spring.velocity();
        spring.cancel();recycleTracker();pointer=-1;dragging=false;cancelled=true;
        selectionCancelTarget=null;selectionRebasedDuringGesture=false;
        if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);
    }
    private void resumeRootMotion(){
        if(!rootResumePending||!rootActive||!attached||!isLiveHierarchy(this))return;
        rootResumePending=false;float velocity=rootResumeVelocity;rootResumeVelocity=0;cancelled=false;
        if(selection!=null){
            if(!ValueAnimator.areAnimatorsEnabled()){finishSelection(selectionTo,0);return;}
            spring.start(offset,-selectionTo.position,velocity);
        }else settle(destination,velocity);
    }
    public boolean isInteracting(){return rootActive&&(dragging||spring.isRunning()||selection!=null);}
    public View currentPage() { return selection==null?pages[1]:nearestSelection(offset).view; }
    /**
     * Date taps use the same physical sheets/spring as a swipe. A distant date creates
     * that date only, never walks through the intervening diary days. Retargeting keeps
     * all visible sheets at their presentation coordinates and carries the velocity.
     */
    public boolean animateToDate(Calendar requested) {
        if(!rootActive||requested==null)return false;
        Calendar target=HomeCalendarDates.day(requested);
        todayKey=HomeCalendarDates.key(Calendar.getInstance());
        String key=HomeCalendarDates.key(target);
        if(key.compareTo(todayKey)>0)return false;
        if(listener!=null&&!listener.canSwipe())return false;
        retainedSelection=null;
        if(selection!=null&&HomeCalendarDates.key(selectionTo.date).equals(key)){
            if(!spring.isRunning()&&!dragging)spring.start(offset,-selectionTo.position,0);
            return true;
        }
        float velocity=spring.isRunning()?spring.velocity():0;
        spring.cancel();destination=0;dragging=false;pointer=-1;recycleTracker();
        if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);
        if(selection==null&&key.equals(HomeCalendarDates.key(date))){settle(0,velocity);return true;}
        if(!ValueAnimator.areAnimatorsEnabled()||!isAttachedToWindow()||getWidth()==0){
            View retained=null;
            if(selection!=null)for(Sheet sheet:selection)if(key.equals(HomeCalendarDates.key(sheet.date)))retained=sheet.view;
            if(retained==null)for(int i=0;i<3;i++)if(key.equals(pageKeys[i]))retained=pages[i];
            replaceDate(target,retained,0);return true;
        }
        ArrayList<Sheet> candidates=new ArrayList<>();
        if(selection==null){for(int i=0;i<3;i++)candidates.add(new Sheet(pages[i],pageDates[i],(i-1)*stride()+offset));}
        else for(Sheet sheet:selection){sheet.position+=offset;candidates.add(sheet);}
        offset=0;selection=new ArrayList<>();selectionTo=null;
        Sheet nearest=null;
        for(Sheet sheet:candidates){
            boolean wanted=key.equals(HomeCalendarDates.key(sheet.date));
            boolean visible=HomeCalendarDates.key(sheet.date).compareTo(todayKey)<=0
                    &&sheet.position<getWidth()&&sheet.position+getWidth()>0;
            if(visible||wanted){selection.add(sheet);if(wanted)selectionTo=sheet;
                if(nearest==null||Math.abs(sheet.position)<Math.abs(nearest.position))nearest=sheet;
            }else removeView(sheet.view);
        }
        if(nearest==null){ // A size/attachment race must still land on the exact request.
            replaceDate(target,null,0);return true;
        }
        if(selectionTo==null){
            int direction=key.compareTo(HomeCalendarDates.key(nearest.date))>0?1:-1;
            float edge=nearest.position;
            for(Sheet sheet:selection)edge=direction>0?Math.max(edge,sheet.position):Math.min(edge,sheet.position);
            View page=factory.create((Calendar)target.clone());addView(page,new LayoutParams(-1,-1));
            selectionTo=new Sheet(page,target,HomeDayMotion.incoming(stride(),direction,edge));selection.add(selectionTo);
        }
        selectionFrom=null;
        for(Sheet sheet:selection)if(sheet!=selectionTo&&(selectionFrom==null
                ||Math.abs(sheet.position)<Math.abs(selectionFrom.position)))selectionFrom=sheet;
        if(selectionFrom==null){finishSelection(selectionTo,0);return true;}
        for(Sheet sheet:selection)sheet.view.setImportantForAccessibility(sheet==nearest
                ?IMPORTANT_FOR_ACCESSIBILITY_AUTO:IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        positionPages();spring.start(offset,-selectionTo.position,velocity);return true;
    }
    /** A tap may land on the incoming sheet before its release spring has committed the date. */
    public boolean retainDate(Calendar shown) {
        if(!rootActive)return false;
        String requested=HomeCalendarDates.key(shown),current=HomeCalendarDates.key(date);
        if(selection!=null){
            for(Sheet sheet:selection)if(requested.equals(HomeCalendarDates.key(sheet.date))){
                float velocity=spring.isRunning()?spring.velocity():0;spring.cancel();
                retainedSelection=HomeCalendarDates.day(shown);selectionTo=sheet;
                for(Sheet other:selection)if(other!=sheet){selectionFrom=other;break;}
                // Pin the clicked record's ownership now, but retain every visible
                // sheet until it is off screen. Far-date neighbours are unrelated.
                if(!ValueAnimator.areAnimatorsEnabled()||!isAttachedToWindow()||getWidth()==0)finishSelection(sheet,0);
                else spring.start(offset,-sheet.position,velocity);
                return true;
            }
            return false;
        }
        int direction=requested.compareTo(current)>0?1:-1;
        if(!requested.equals(current)&&(!requested.equals(HomeCalendarDates.key(relative(direction)))||!allowed(direction)))return false;
        spring.cancel();destination=0;
        if(!requested.equals(current)) {
            float remaining=offset+direction*stride();
            commit(direction);
            offset=remaining;positionPages();
        }
        settle(0,0);
        return true;
    }
    private void refreshDates(){
        todayKey=HomeCalendarDates.key(Calendar.getInstance());
        for(int i=0;i<3;i++){Calendar value=(Calendar)date.clone();value.add(Calendar.DATE,i-1);pageDates[i]=value;pageKeys[i]=HomeCalendarDates.key(value);}
    }
    private Calendar relative(int difference) { return (Calendar)pageDates[difference+1].clone(); }
    private boolean allowed(int direction) {
        return pageKeys[direction+1].compareTo(todayKey)<=0;
    }
    private int dp(float value) { return PaperComponents.dp(getContext(), value); }
    private float stride(){return getWidth()+dp(24);}
    private void install(int index) {
        Calendar value = relative(index - 1);
        View page = pageKeys[index].compareTo(todayKey)>0
                ? new View(getContext()) : factory.create(value);
        pages[index] = page;
        addView(page, new LayoutParams(-1, -1));
    }
    private void positionPages() {
        if(selection!=null){
            for(Sheet sheet:selection)sheet.view.setTranslationX(sheet.position+offset);
            if(!rootActive||!attached||!isLiveHierarchy(this)||getWidth()==0||selectionFrom==null||selectionTo==null)return;
            Sheet left=null,right=null;
            for(Sheet sheet:selection){float x=sheet.position+offset;
                if(x<=0&&(left==null||sheet.position>left.position))left=sheet;
                if(x>=0&&(right==null||sheet.position<right.position))right=sheet;
            }
            Sheet from,to;
            if(left==null||right==null||left==right)from=to=nearestSelection(offset);
            else if(selectionTo.position+offset>0){from=left;to=right;}else{from=right;to=left;}
            float progress=from==to?0:HomeDayMotion.between(offset,from.position,to.position);
            if(listener!=null)listener.onDayProgress(from.date,to.date,progress);
            notifyVisible(progress>=.5f?to.date:from.date);return;
        }
        for (int i = 0; i < 3; i++) if(pages[i]!=null)pages[i].setTranslationX((i - 1) * stride() + offset);
        if(!rootActive||!attached||!isLiveHierarchy(this)||getWidth()==0)return;
        int shift=offset>0?-1:offset<0&&allowed(1)?1:0;
        float progress=shift==0?0:HomeDayMotion.fraction(offset,stride());
        if(listener!=null)listener.onDayProgress(pageDates[1],pageDates[1+shift],progress);
        int direction=progress>=.5f?shift:0;
        notifyVisible(pageDates[1+direction]);
    }
    private void notifyVisible(Calendar shown){
        if(retainedSelection!=null)return;
        String key=HomeCalendarDates.key(shown);
        if(!key.equals(visibleDate)){visibleDate=key;if(listener!=null&&rootActive&&isLiveHierarchy(this))listener.onVisibleDate((Calendar)shown.clone());}
    }
    private void finishSelection(Sheet chosen,float remaining){replaceDate(chosen.date,chosen.view,remaining);}
    private Sheet nearestSelection(float presentation){
        Sheet nearest=selection.get(0);
        for(Sheet sheet:selection)if(Math.abs(sheet.position+presentation)<Math.abs(nearest.position+presentation))nearest=sheet;
        return nearest;
    }
    private void replaceDate(Calendar chosen,View retained,float remaining){
        selection=null;selectionFrom=selectionTo=null;retainedSelection=null;destination=0;
        date=HomeCalendarDates.day(chosen);refreshDates();
        // The chosen sheet is already visible. Keep it attached so its image decoder,
        // scroll state and display list survive a selection/gesture handoff.
        for(int i=getChildCount()-1;i>=0;i--)if(getChildAt(i)!=retained)removeViewAt(i);
        for(int i=0;i<3;i++)if(i==1&&retained!=null){pages[i]=retained;if(retained.getParent()!=this)addView(retained,new LayoutParams(-1,-1));}else install(i);
        offset=remaining;positionPages();updateAccessibility();
        if(listener!=null&&rootActive&&isLiveHierarchy(this))listener.onDate(currentDate());
        sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SCROLLED);
    }
    private void updateAccessibility() {
        for (int i = 0; i < 3; i++) pages[i].setImportantForAccessibility(i == 1
                ? IMPORTANT_FOR_ACCESSIBILITY_AUTO : IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        setContentDescription(HomeCalendarDates.key(date) + "的餐食，向右查看前一天，向左返回后一天，不可进入未来");
    }
    private void commit(int direction) {
        date.add(Calendar.DATE, direction);
        refreshDates();
        if (direction > 0) {
            removeView(pages[0]);pages[0] = pages[1];pages[1] = pages[2];install(2);
        } else {
            removeView(pages[2]);pages[2] = pages[1];pages[1] = pages[0];install(0);
        }
        offset = 0;positionPages();updateAccessibility();
        if (listener != null&&rootActive&&isLiveHierarchy(this)) listener.onDate(currentDate());
        sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SCROLLED);
    }
    private void settle(int direction, float velocity) {
        spring.cancel();destination = allowed(direction) ? direction : 0;
        if (!ValueAnimator.areAnimatorsEnabled() || !isAttachedToWindow() || getWidth() == 0) {
            int next = destination;destination = 0;
            if (next != 0) commit(next);else { offset = 0;positionPages(); }
            return;
        }
        spring.start(offset, -destination * stride(), velocity);
    }
    private void drag(float displacement) {
        if (!ValueAnimator.areAnimatorsEnabled()) return;
        float proposed = dragOrigin + displacement;
        if(selection!=null){
            Sheet left=selection.get(0),right=left;
            for(Sheet sheet:selection){if(sheet.position<left.position)left=sheet;if(sheet.position>right.position)right=sheet;}
            float min=-right.position,max=-left.position;
            if(proposed<min||proposed>max){
                Sheet reached=proposed<min?right:left;
                // Crossing the centered endpoint puts every old source sheet off-screen.
                // Continue into its natural neighbour instead of rubber-banding forever
                // inside the two dates retained for a previous calendar tap.
                float origin=dragOrigin+reached.position;selectionRebasedDuringGesture=true;
                finishSelection(reached,0);dragOrigin=origin;drag(displacement);return;
            }
            offset=proposed<min?min+HomeDayMotion.rubber(proposed-min,Math.max(1,getWidth()))
                    :proposed>max?max+HomeDayMotion.rubber(proposed-max,Math.max(1,getWidth())):proposed;
            positionPages();return;
        }
        if (proposed < 0 && !allowed(1)) {
            // A progressive elastic edge makes the future limit visible without opening it.
            float dimension = Math.max(1, getWidth());
            proposed = HomeDayMotion.rubber(proposed,dimension);
        }
        offset = Math.max(-stride(), Math.min(stride(), proposed));positionPages();
    }
    private void recycleTracker() { if (tracker != null) { tracker.recycle();tracker = null; } }
    private void track(MotionEvent event){
        if(tracker==null)return;
        if(trackedTime==event.getEventTime()&&trackedAction==event.getAction()
                &&trackedX==event.getX()&&trackedY==event.getY())return;
        trackedTime=event.getEventTime();trackedAction=event.getAction();trackedX=event.getX();trackedY=event.getY();tracker.addMovement(event);
    }
    private void grabVisibleSheet(){
        if(selection==null){
            int direction=HomeDayMotion.grabDirection(offset,stride());
            if(direction!=0&&allowed(direction)){
                float remaining=HomeDayMotion.rebasedOffset(offset,stride(),direction);
                commit(direction);offset=remaining;positionPages();
            }
        }else{
            Sheet target=selectionTo;float remaining=target.position+offset;
            boolean alone=Math.abs(remaining)<=dp(24);
            for(Sheet sheet:selection)if(sheet!=target&&sheet.position+offset<getWidth()
                    &&sheet.position+offset+getWidth()>0)alone=false;
            if(alone)finishSelection(target,remaining);
        }
    }
    private boolean beginHorizontalDrag(MotionEvent event){
        if(cancelled||pointer<0)return false;
        track(event);
        if(listener!=null&&!listener.canSwipe()){cancelled=true;return false;}
        int index=event.findPointerIndex(pointer);if(index<0)return false;
        float dx=event.getX(index)-downX,dy=event.getY(index)-downY;
        int slop=Math.max(dp(10),ViewConfiguration.get(getContext()).getScaledTouchSlop());
        if(Math.abs(dx)>slop&&Math.abs(dx)>Math.abs(dy)*1.3f){
            dragging=true;retainedSelection=null;
            if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);
            return true;
        }
        if(Math.abs(dy)>slop&&Math.abs(dy)>Math.abs(dx)*1.3f)cancelled=true;
        return false;
    }
    private void releaseWithoutDrag(boolean abort){
        pointer=-1;recycleTracker();
        if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);
        if(selection!=null)settleSelection(0,abort);
        else if(Math.abs(offset)>.5f)settle(0,0);
        selectionCancelTarget=null;selectionRebasedDuringGesture=false;
    }
    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
        if(!rootActive)return false;
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            recycleTracker();tracker = VelocityTracker.obtain();trackedTime=Long.MIN_VALUE;track(event);
            todayKey=HomeCalendarDates.key(Calendar.getInstance());
            pointer = event.getPointerId(0);downX = event.getX();downY = event.getY();
            dragging = false;cancelled = listener != null && !listener.canSwipe();
            // Grab the current presentation value, not the old target.
            if (!cancelled && spring.isRunning()) { spring.cancel();destination = 0; }
            if(!cancelled)grabVisibleSheet();
            selectionCancelTarget=selection==null?null:HomeCalendarDates.day(selectionTo.date);
            selectionRebasedDuringGesture=false;
            dragOrigin = selection==null&&offset<0&&!allowed(1)?HomeDayMotion.inverseRubber(offset,Math.max(1,getWidth())):offset;
        } else if (action == MotionEvent.ACTION_POINTER_DOWN) {
            cancelled = true;if (dragging) return true;
        } else if (action == MotionEvent.ACTION_MOVE && !cancelled) {
            if(beginHorizontalDrag(event))return true;
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            // If a child still owns this stream, resume a grabbed animation here.
            // Once interception owns it, onTouchEvent must receive the live pointer.
            if(!dragging)releaseWithoutDrag(action==MotionEvent.ACTION_CANCEL);
        }
        return dragging;
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if(!rootActive)return false;
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN)return true;
        if(!dragging){
            if(event.getActionMasked()==MotionEvent.ACTION_MOVE){if(!beginHorizontalDrag(event))return super.onTouchEvent(event);}
            else if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL){releaseWithoutDrag(event.getActionMasked()==MotionEvent.ACTION_CANCEL);return true;}
            else return super.onTouchEvent(event);
        }
        track(event);
        int action = event.getActionMasked(), index = event.findPointerIndex(pointer);
        float dx = index < 0 ? 0 : event.getX(index) - downX;
        if(action==MotionEvent.ACTION_UP&&!cancelled&&index>=0)drag(dx);
        if (action == MotionEvent.ACTION_MOVE && !cancelled) { drag(dx);return true; }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_POINTER_DOWN) {
            boolean abort = cancelled || action != MotionEvent.ACTION_UP;
            float velocity = 0;
            if (tracker != null && pointer >= 0) { tracker.computeCurrentVelocity(1000,ViewConfiguration.get(getContext()).getScaledMaximumFlingVelocity());velocity = tracker.getXVelocity(pointer); }
            int next = 0;
            if (!abort) {
                if (!ValueAnimator.areAnimatorsEnabled()) next = Math.abs(dx) >= dp(36) ? (dx > 0 ? -1 : 1) : 0;
                else {
                    float projected = offset + velocity / 1000f * .99f / (1f - .99f);
                    if (Math.abs(projected) > stride() * .5f) next = projected > 0 ? -1 : 1;
                }
            }
            dragging = false;pointer = -1;recycleTracker();
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
            Calendar restore=abort&&selectionRebasedDuringGesture?selectionCancelTarget:null;
            selectionCancelTarget=null;selectionRebasedDuringGesture=false;
            if(restore!=null){animateToDate(restore);return true;}
            if(selection!=null){settleSelection(abort?0:velocity,abort);return true;}
            settle(next, abort ? 0 : velocity);return true;
        }
        return true;
    }
    private void settleSelection(float velocity,boolean abort){
        float projected=offset+velocity/1000f*.99f/(1f-.99f);
        Sheet closest=nearestSelection(projected);
        // A tap/long press has already chosen the record owning the next dialog.
        // Releasing its touch must not replace that explicit choice by the nearest sheet.
        if(!abort&&retainedSelection==null&&closest!=selectionTo){
            Sheet previous=selectionTo;selectionTo=closest;selectionFrom=previous;
        }
        if(!ValueAnimator.areAnimatorsEnabled()){finishSelection(selectionTo,0);return;}
        spring.start(offset,-selectionTo.position,velocity);
    }
    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (width != oldWidth) { spring.cancel();if(selection!=null)finishSelection(selectionTo,0);offset = 0;destination = 0;dragging=false;pointer=-1;recycleTracker(); }
        positionPages();
    }
    @Override protected void onDetachedFromWindow() {
        attached=false;rootResumePending=false;rootResumeVelocity=0;spring.cancel();recycleTracker();resetSelectionOnAttach=selection!=null;
        if(selection==null){offset=0;positionPages();}
        destination = 0;dragging = false;pointer=-1;super.onDetachedFromWindow();
    }
    @Override protected void onAttachedToWindow(){
        super.onAttachedToWindow();attached=true;visibleDate=null;
        if(resetSelectionOnAttach&&selection!=null){resetSelectionOnAttach=false;finishSelection(retainedSelection==null?nearestSelection(offset):selectionTo,0);}
        positionPages();
    }
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);info.setScrollable(true);
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
        if (allowed(1)) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
    }
    @Override public boolean performAccessibilityAction(int action, Bundle args) {
        if(!rootActive)return false;
        int direction = action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD ? -1 : action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD ? 1 : 0;
        if (direction != 0 && allowed(direction) && (listener == null || listener.canSwipe())) {
            spring.cancel();
            if(selection!=null){Calendar next=(Calendar)nearestSelection(offset).date.clone();next.add(Calendar.DATE,direction);
                if(HomeCalendarDates.key(next).compareTo(todayKey)>0)return false;
                replaceDate(next,null,0);
            }else commit(direction);
            return true;
        }
        return super.performAccessibilityAction(action, args);
    }
}
