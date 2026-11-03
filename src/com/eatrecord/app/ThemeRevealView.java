package com.eatrecord.app;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.view.View;
import android.view.animation.PathInterpolator;

/** The old, real UI above the new UI; only a circular feathered hole is removed. */
final class ThemeRevealView extends View {
    static final long DURATION_MILLIS = 1440;
    static final float FEATHER_WIDTH_DP = 20f;
    static final long ICON_DURATION_MILLIS = 250;
    static android.view.animation.Interpolator easing() { return new PathInterpolator(.65f,0,.35f,1); }
    private Bitmap snapshot;
    private final Paint image = new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Paint erase = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float cx,cy,maximum,feather;
    private float radius;
    private ValueAnimator animator;
    private Runnable finished;
    private ThemeModeIcon icon;
    private boolean fromNight;
    private ValueAnimator iconAnimator;
    private float iconProgress;
    private int iconWidth,iconHeight;
    private boolean finishing;
    ThemeRevealView(Context context,Bitmap snapshot,float x,float y,Runnable finished) {
        super(context);this.snapshot=snapshot;cx=x;cy=y;this.finished=finished;
        feather=FEATHER_WIDTH_DP*getResources().getDisplayMetrics().density;
        maximum=(float)Math.hypot(Math.max(x,snapshot.getWidth()-x),Math.max(y,snapshot.getHeight()-y))+feather;
        erase.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        setClickable(true);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        setTag("theme_circle_reveal");
    }
    boolean matchesSize(int width,int height){return snapshot!=null&&snapshot.getWidth()==width&&snapshot.getHeight()==height;}
    void setIconTransition(boolean fromNight,int width,int height){
        this.fromNight=fromNight;iconWidth=width;iconHeight=height;
        icon=new ThemeModeIcon(getContext(),fromNight);icon.layout(0,0,width,height);
    }
    void start() {
        if(animator!=null||snapshot==null)return;
        animator=ValueAnimator.ofFloat(0,maximum);animator.setDuration(DURATION_MILLIS);animator.setInterpolator(easing());
        animator.addUpdateListener(a->{radius=(float)a.getAnimatedValue();invalidate();});
        animator.addListener(new AnimatorListenerAdapter(){@Override public void onAnimationEnd(Animator a){finish();}});
        if(icon!=null){
            iconAnimator=ValueAnimator.ofFloat(0,1);iconAnimator.setDuration(ICON_DURATION_MILLIS);iconAnimator.setInterpolator(UiTokens.motionEaseOut());
            iconAnimator.addUpdateListener(a->{iconProgress=(float)a.getAnimatedValue();icon.setTransition(fromNight,!fromNight,iconProgress);invalidate();});
            iconAnimator.start();
        }
        animator.start();
    }
    @Override protected void onDraw(Canvas canvas) {
        if(snapshot==null)return;
        int layer=canvas.saveLayer(0,0,getWidth(),getHeight(),null);
        canvas.drawBitmap(snapshot,0,0,image);
        if(radius>0){
            float inner=Math.max(0,radius-feather);
            erase.setShader(new RadialGradient(cx,cy,radius,new int[]{Color.WHITE,Color.WHITE,Color.TRANSPARENT},
                    new float[]{0,inner/radius,1},Shader.TileMode.CLAMP));
            canvas.drawCircle(cx,cy,radius,erase);
        }
        canvas.restoreToCount(layer);
        if(icon!=null){icon.setRevealInk(radius,feather,fromNight);int save=canvas.save();canvas.translate(cx-iconWidth*.5f,cy-iconHeight*.5f);icon.draw(canvas);canvas.restoreToCount(save);}
    }
    void finish() {
        if(finishing)return;finishing=true;
        if(iconAnimator!=null){iconAnimator.removeAllUpdateListeners();iconAnimator.cancel();iconAnimator=null;}
        ValueAnimator old=animator;animator=null;
        if(old!=null){old.removeAllListeners();old.removeAllUpdateListeners();old.cancel();}
        if(getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)getParent()).removeView(this);
        if(snapshot!=null){snapshot.recycle();snapshot=null;}erase.setShader(null);
        Runnable action=finished;finished=null;if(action!=null)action.run();
    }
    @Override protected void onDetachedFromWindow(){super.onDetachedFromWindow();finish();}
}
