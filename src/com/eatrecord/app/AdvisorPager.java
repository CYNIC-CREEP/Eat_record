package com.eatrecord.app;

import android.content.Context;
import android.view.*;
import android.widget.*;

/** Two actual retained sheets; the Activity arbitrates with the calendar and horizontal pager. */
final class AdvisorPager extends FrameLayout {
    interface Listener { void onMoving(); void onProgress(float position,float height); void onRest(boolean advisor); }
    private final View today;
    private View advisor;
    private final Listener listener;
    private final GestureSpring spring;
    private float position,origin,downX,downY;
    private int target,pointer=-1;
    private boolean tracking,dragging,swallow;
    private VelocityTracker velocity;
    AdvisorPager(Context c,View today,View advisor,Listener listener){
        super(c);this.today=today;this.advisor=advisor;this.listener=listener;
        setTag("today_advisor_pager");setClipChildren(true);setClipToPadding(true);
        addView(today,new LayoutParams(-1,-1));addView(advisor,new LayoutParams(-1,-1));
        spring=new GestureSpring(new GestureSpring.Listener(){
            public void onUpdate(float value){position=Math.max(0,Math.min(getHeight(),value));place();}
            public void onEnd(){position=target*getHeight();place();listener.onRest(target==1);}
        });place();
    }
    void replaceAdvisor(View page){removeView(advisor);advisor=page;addView(page,new LayoutParams(-1,-1));place();}
    View selectedPage(){return target==0?today:advisor;}
    boolean atAdvisor(){return target==1&&!interacting()&&getHeight()>0&&Math.abs(position-getHeight())<1;}
    boolean atToday(){return target==0&&!dragging&&!spring.isRunning()&&Math.abs(position)<1;}
    boolean interacting(){return dragging||spring.isRunning();}
    void showAdvisorImmediately(){clearPointer();spring.cancel();target=1;position=getHeight();place();listener.onMoving();listener.onRest(true);}
    void select(boolean next){clearPointer();target=next?1:0;listener.onMoving();spring.start(position,target*getHeight(),0);}
    boolean handle(MotionEvent e,boolean eligible,Runnable cancelChild){
        int action=e.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){
            clearPointer();swallow=false;if(!eligible)return false;
            tracking=true;pointer=e.getPointerId(0);downX=e.getRawX();downY=e.getRawY();origin=position;
            velocity=VelocityTracker.obtain();velocity.addMovement(e);return false;
        }
        if(swallow){if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)swallow=false;return true;}
        if(!tracking)return false;
        velocity.addMovement(e);
        if(action==MotionEvent.ACTION_POINTER_DOWN||e.findPointerIndex(pointer)<0){
            boolean owned=dragging;if(owned){cancelChild.run();settle(true,0);}clearPointer();swallow=owned;return owned;
        }
        float dx=e.getRawX()-downX,dy=e.getRawY()-downY;
        if(action==MotionEvent.ACTION_MOVE){
            float slop=Math.max(dp(14),ViewConfiguration.get(getContext()).getScaledTouchSlop());
            if(!dragging&&Math.abs(dx)>slop&&Math.abs(dx)>Math.abs(dy)){clearPointer();return false;}
            if(!dragging&&Math.abs(dy)>slop&&Math.abs(dy)>Math.abs(dx)*1.4f){
                int direction=dy<0?1:-1;
                if(!spring.isRunning()&&((target==0&&dy>=0)||(target==1&&dy<=0)
                        ||scrollableAt(target==0?today:advisor,downX,downY,direction))){clearPointer();return false;}
                spring.cancel();dragging=true;origin=position;cancelChild.run();listener.onMoving();
            }
            if(dragging){drag(dy);return true;}
        }
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){
            boolean owned=dragging;
            if(owned){drag(dy);velocity.computeCurrentVelocity(1000);settle(action==MotionEvent.ACTION_CANCEL,-velocity.getYVelocity(pointer));}
            clearPointer();return owned;
        }
        return dragging;
    }
    private void drag(float dy){position=Math.max(0,Math.min(getHeight(),origin-dy*.62f));place();}
    private void settle(boolean cancelled,float speed){
        float delta=position-origin;
        if(!cancelled&&(Math.abs(delta)>dp(96)||Math.abs(delta)>dp(30)&&Math.signum(delta)*speed>dp(1100)))target=delta>0?1:0;
        dragging=false;spring.start(position,target*getHeight(),speed*.62f);
    }
    void stopForRootNavigation(){clearPointer();spring.cancel();}
    void resetForRootExit(){clearPointer();spring.cancel();target=0;position=0;place();}
    void resumeVertical(){if(Math.abs(position-target*getHeight())<1)return;listener.onMoving();spring.start(position,target*getHeight(),0);}
    void pause(){clearPointer();spring.cancel();position=target*getHeight();place();listener.onRest(target==1);}
    private void clearPointer(){if(velocity!=null){velocity.recycle();velocity=null;}tracking=dragging=false;pointer=-1;}
    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private void place(){
        today.setTranslationY(-position);today.setVisibility(position<getHeight()||getHeight()==0?VISIBLE:INVISIBLE);
        advisor.setTranslationY(getHeight()-position);
        advisor.setVisibility(position>0?VISIBLE:INVISIBLE);
        today.setImportantForAccessibility(target==0&&!interacting()?IMPORTANT_FOR_ACCESSIBILITY_AUTO:IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        advisor.setImportantForAccessibility(target==1&&!interacting()?IMPORTANT_FOR_ACCESSIBILITY_AUTO:IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        listener.onProgress(position,getHeight());
    }
    private static boolean scrollableAt(View v,float x,float y,int direction){
        if(v.getVisibility()!=VISIBLE)return false;int[] p=new int[2];v.getLocationOnScreen(p);
        if(x<p[0]||x>=p[0]+v.getWidth()||y<p[1]||y>=p[1]+v.getHeight())return false;
        if(v instanceof ScrollView&&v.canScrollVertically(direction))return true;
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=g.getChildCount()-1;i>=0;i--)if(scrollableAt(g.getChildAt(i),x,y,direction))return true;}
        return v instanceof EditText||v instanceof SeekBar;
    }
    @Override public boolean onInterceptTouchEvent(MotionEvent event){return interacting()||super.onInterceptTouchEvent(event);}
    @Override public boolean onTouchEvent(MotionEvent event){return true;}
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){super.onLayout(changed,l,t,r,b);if(!interacting())position=target*getHeight();place();}
    @Override protected void onSizeChanged(int w,int h,int ow,int oh){super.onSizeChanged(w,h,ow,oh);if(oh>0&&h!=oh)pause();}
    @Override protected void onDetachedFromWindow(){clearPointer();spring.cancel();super.onDetachedFromWindow();}
}
