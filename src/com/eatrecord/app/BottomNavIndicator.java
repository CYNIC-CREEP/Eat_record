package com.eatrecord.app;

import android.animation.ValueAnimator;
import android.graphics.drawable.GradientDrawable;
import android.view.Choreographer;
import android.view.View;
import android.view.ViewGroup;

/** Moves only the existing decoration. Item layout, touch bounds and navigation remain owned by Activity. */
final class BottomNavIndicator implements Choreographer.FrameCallback {
    interface ContainerFeedback {void swell(float value);}
    private final View indicator;
    private final ViewGroup bar,host;
    private final BottomNavLens lens;
    private final ContainerFeedback container;
    final NavLensGeometry geometry=new NavLensGeometry();
    final BottomNavMotion motion=new BottomNavMotion();
    private boolean initialized,scheduled;
    private long lastFrame;
    private int targetId;
    private GradientDrawable background;
    private float cornerRadius;
    private final float[] corners=new float[8];
    BottomNavIndicator(View indicator,ViewGroup bar,ViewGroup host,BottomNavLens lens,ContainerFeedback container){
        this.indicator=indicator;this.bar=bar;this.host=host;this.lens=lens;this.container=container;
        indicator.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){
            public void onViewAttachedToWindow(View v){}
            public void onViewDetachedFromWindow(View v){motion.snap(motion.position.target);stop();lens.hide();container.swell(0);}
        });
        lens.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(initialized)draw();});
    }
    private View item(int id){for(int i=0;i<bar.getChildCount();i++){View v=bar.getChildAt(i);if(Integer.valueOf(id).equals(v.getTag()))return v;}return null;}
    private double center(View v){return bar.getLeft()+v.getLeft()+v.getWidth()*.5;}
    boolean select(int id,boolean animate){
        View target=item(id);
        if(target==null||target.getWidth()<=0||indicator.getWidth()<=0||host.getWidth()<=0)return false;
        targetId=id;double pitch=0;int pairs=0;
        for(int i=1;i<bar.getChildCount();i++){double gap=Math.abs(center(bar.getChildAt(i))-center(bar.getChildAt(i-1)));if(gap>0){pitch+=gap;pairs++;}}
        motion.unit(pairs>0?pitch/pairs:target.getWidth());
        if(!initialized||!animate||!ValueAnimator.areAnimatorsEnabled()){motion.snap(center(target));initialized=true;stop();}
        else motion.select(center(target));
        indicator.setVisibility(View.VISIBLE);indicator.setAlpha(0);draw();wake();return true;
    }
    void press(){if(initialized&&ValueAnimator.areAnimatorsEnabled()){motion.press();wake();}}
    void release(boolean cancel){if(initialized){motion.release(cancel);if(cancel){motion.lift.snap(0);motion.jelly.snap(0);draw();}wake();}}
    void follow(int from,int to,float progress){
        View a=item(from),b=item(to);if(!initialized||a==null||b==null)return;
        motion.follow(center(a)+(center(b)-center(a))*Math.max(0,Math.min(1,progress)));wake();
    }
    void settle(){select(targetId,false);}
    void stop(){Choreographer.getInstance().removeFrameCallback(this);scheduled=false;lastFrame=0;}
    private void wake(){if(!scheduled&&indicator.isAttachedToWindow()&&motion.running()){scheduled=true;lastFrame=System.nanoTime();Choreographer.getInstance().postFrameCallback(this);}}
    @Override public void doFrame(long time){
        if(!scheduled)return;scheduled=false;
        if(!ValueAnimator.areAnimatorsEnabled()){settle();return;}
        boolean moving=motion.step(Math.max(0,(time-lastFrame)/1e9));lastFrame=time;draw();
        if(moving&&indicator.isAttachedToWindow()){scheduled=true;Choreographer.getInstance().postFrameCallback(this);}
    }
    private float dp(float value){return value*indicator.getResources().getDisplayMetrics().density;}
    private void draw(){
        double x=motion.position.value;float width=0,best=Float.MAX_VALUE;
        // Interpolate measured widths in physical space, which also handles RTL and unequal slots.
        View before=null,after=null;
        for(int i=0;i<bar.getChildCount();i++){
            View v=bar.getChildAt(i);double c=center(v);float d=(float)Math.abs(c-x);
            if(d<best){best=d;width=Math.max(dp(74),v.getWidth()-dp(6));}
            if(c<=x&&(before==null||c>center(before)))before=v;
            if(c>=x&&(after==null||c<center(after)))after=v;
        }
        if(before!=null&&after!=null&&before!=after){
            float t=(float)((x-center(before))/(center(after)-center(before)));
            width=Math.max(dp(74),before.getWidth()-dp(6))*(1-t)+Math.max(dp(74),after.getWidth()-dp(6))*t;
        }
        float lift=(float)Math.max(0,Math.min(1,motion.lift.value));
        float jelly=(float)Math.max(-.18,Math.min(.24,motion.jelly.value));
        float centeredRoom=(float)Math.max(1,2*Math.min(x+dp(12),host.getWidth()+dp(12)-x));
        geometry.update(width,indicator.getHeight(),lift,jelly,centeredRoom,indicator.getHeight()+dp(28),getDensity());
        float desired=geometry.width;
        double safeX=Math.max(-dp(12)+desired*.5,Math.min(host.getWidth()+dp(12)-desired*.5,x));
        container.swell(geometry.containerSwell);
        indicator.setPivotX(indicator.getWidth()*.5f);indicator.setPivotY(indicator.getHeight()*.5f);
        indicator.setTranslationX((float)safeX-indicator.getLeft()-indicator.getWidth()*.5f);
        float sy=geometry.height/Math.max(1,indicator.getHeight());
        indicator.setTranslationY(-geometry.rise);
        float sx=desired/Math.max(1,indicator.getWidth());
        indicator.setScaleX(sx);indicator.setScaleY(sy);
        lens.geometry((float)safeX,indicator.getTop()+(indicator.getHeight()-geometry.height)*.5f-geometry.rise,
                desired,geometry.height,lift,jelly,geometry.contentScale,(float)Math.signum(motion.position.velocity));
        if(indicator.getBackground() instanceof GradientDrawable){
            GradientDrawable current=(GradientDrawable)indicator.getBackground();
            if(current!=background){background=current;cornerRadius=current.getCornerRadius();}
            // Inverse radii preserve the existing steady shape even across unequal-width items.
            for(int i=0;i<8;i+=2){corners[i]=cornerRadius/sx;corners[i+1]=cornerRadius/sy;}
            current.setCornerRadii(corners);
        }
    }
    private float getDensity(){return indicator.getResources().getDisplayMetrics().density;}
}
