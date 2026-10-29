package com.eatrecord.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.view.View;
import android.view.MotionEvent;
import android.view.ViewParent;
import android.widget.FrameLayout;

/** Three retained root sheets. Input frames only update render transforms, never create pages. */
final class RootPagePager extends FrameLayout {
    interface Listener {
        void onProgress(float position);
        void onTarget(int index);
        void onRest(int index);
    }
    private final View[] pages;
    private final Listener listener;
    private final RootPageMotion motion;
    private final GestureSpring gestureSpring,tapSpring;
    private boolean frozen,homeOwned,ownershipKnown;
    RootPagePager(Context context,View[] pages,int selected,Listener listener){
        super(context);this.pages=pages;this.listener=listener;motion=new RootPageMotion(selected);
        setClipChildren(true);setClipToPadding(true);setFocusable(true);setClickable(true);setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        GestureSpring.Listener updates=new GestureSpring.Listener(){
            public void onUpdate(float value){
                motion.animate(value/Math.max(1,getWidth()));
                // Clamped release momentum must not leave an invisible settling lock.
                if(!motion.isDragging()&&Math.abs(motion.position()-motion.target())<.0001f){
                    cancelSprings();motion.finish();positionPages();if(RootPagePager.this.listener!=null)RootPagePager.this.listener.onRest(motion.target());
                }else positionPages();
            }
            public void onEnd(){motion.finish();positionPages();if(RootPagePager.this.listener!=null)RootPagePager.this.listener.onRest(motion.target());}
        };
        gestureSpring=new GestureSpring(updates,UiTokens.GESTURE_RESPONSE_SECONDS);
        tapSpring=new GestureSpring(updates,UiTokens.NAV_TAP_RESPONSE_SECONDS);
        for(View page:pages)addView(page,new LayoutParams(-1,-1));positionPages();
    }
    View page(int index){return pages[Math.max(0,Math.min(2,index))];}
    View selectedPage(){return page(motion.target());}
    float position(){return motion.position();}
    int target(){return motion.target();}
    boolean isInteracting(){return motion.isDragging()||gestureSpring.isRunning()||tapSpring.isRunning();}
    boolean isAnimating(){return gestureSpring.isRunning()||tapSpring.isRunning();}
    boolean isFrozen(){return frozen;}
    boolean ownsView(View view){
        if(frozen||isInteracting())return false;
        View node=view;View owner=selectedPage();
        while(node!=null&&node!=this){if(node==owner)return true;ViewParent parent=node.getParent();node=parent instanceof View?(View)parent:null;}
        return false;
    }
    void grab(){cancelSprings();motion.grab(Math.round(motion.position()));positionPages();}
    void drag(float dx){motion.drag(-dx/Math.max(1,getWidth()));positionPages();}
    void release(float velocity,boolean cancelled){
        motion.release(-velocity/Math.max(1,getWidth()),cancelled);
        if(listener!=null)listener.onTarget(motion.target());start(false,-velocity/Math.max(1,getWidth()));
    }
    void select(int index,boolean animated){
        cancelSprings();motion.select(index);frozen=false;
        if(listener!=null)listener.onTarget(motion.target());
        if(!animated){motion.finish();positionPages();if(listener!=null)listener.onRest(motion.target());}
        else start(true,0);
    }
    void freeze(){
        frozen=true;cancelSprings();if(motion.isDragging())motion.release(0,true);updateOwnership();
    }
    void resume(){if(!frozen)return;frozen=false;if(listener!=null)listener.onTarget(motion.target());start(false,0);}
    private void start(boolean tap,float velocity){
        cancelSprings();
        if(frozen)return;
        if(!ValueAnimator.areAnimatorsEnabled()||!isAttachedToWindow()||getWidth()<=0){motion.finish();positionPages();if(listener!=null)listener.onRest(motion.target());return;}
        GestureSpring spring=tap?tapSpring:gestureSpring;
        spring.start(motion.position()*getWidth(),motion.target()*getWidth(),velocity*getWidth());positionPages();
    }
    private void cancelSprings(){gestureSpring.cancel();tapSpring.cancel();}
    private void positionPages(){
        float value=motion.position();int logical=motion.target();
        setTag(logical==0?"main_peer_today":logical==1?"main_peer_footprints":"main_peer_me");
        for(int i=0;i<pages.length;i++){
            View page=pages[i];float x=(i-value)*getWidth();
            page.setTranslationX(x);page.setTranslationY(0);page.setAlpha(1);
            page.setVisibility(Math.abs(i-value)<1.001f?View.VISIBLE:View.INVISIBLE);
            page.setImportantForAccessibility(!isInteracting()&&i==Math.round(value)?View.IMPORTANT_FOR_ACCESSIBILITY_AUTO:View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        }
        updateOwnership();if(listener!=null)listener.onProgress(value);invalidate();
    }
    private void updateOwnership(){
        boolean active=!frozen&&!isInteracting()&&motion.target()==0;
        if(ownershipKnown&&active==homeOwned)return;ownershipKnown=true;homeOwned=active;
        View day=pages[0].findViewWithTag("today_day_pager"),calendar=pages[0].findViewWithTag("home_calendar");
        if(day instanceof TodayDayPager)((TodayDayPager)day).setRootActive(active);
        if(calendar instanceof HomeCalendarView)((HomeCalendarView)calendar).setRootActive(active);
    }
    // While a root sheet is moving, taps cannot activate an outgoing cached page.
    // MainActivity still owns horizontal grab/reverse input before this dispatch.
    @Override public boolean onInterceptTouchEvent(MotionEvent event){return isInteracting()||super.onInterceptTouchEvent(event);}
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
        super.onLayout(changed,l,t,r,b);positionPages();
    }
    @Override protected void onSizeChanged(int w,int h,int ow,int oh){
        super.onSizeChanged(w,h,ow,oh);
        if(ow>0&&(w!=ow||h!=oh)){cancelSprings();motion.reset(motion.target());if(listener!=null)listener.onRest(motion.target());}
    }
    @Override protected void onDetachedFromWindow(){freeze();super.onDetachedFromWindow();}
}
