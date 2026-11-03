package com.eatrecord.app;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** A single outlined body morphs between crescent and sun without a glyph swap. */
final class ThemeModeIcon extends View {
    private float sun;
    private int ink;
    private Shader revealInk;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path body=new Path(),cut=new Path();
    ThemeModeIcon(Context c,boolean night){super(c);setTransition(night,night,1);setClickable(true);}
    void setTransition(boolean fromNight,boolean toNight,float progress){
        float t=Math.max(0,Math.min(1,progress));sun=(fromNight?1:0)+((toNight?1:0)-(fromNight?1:0))*t;
        int a=UiTokens.ink(fromNight),b=UiTokens.ink(toNight);
        ink=Color.rgb(Math.round(Color.red(a)+(Color.red(b)-Color.red(a))*t),
                Math.round(Color.green(a)+(Color.green(b)-Color.green(a))*t),Math.round(Color.blue(a)+(Color.blue(b)-Color.blue(a))*t));
        invalidate();
    }
    void setRevealInk(float radius,float feather,boolean fromNight){
        if(radius<=0){revealInk=null;ink=UiTokens.ink(fromNight);return;}
        if(radius-feather>=Math.hypot(getWidth()*.5f,getHeight()*.5f)){
            // Once the wave has cleared the button, use precisely the static
            // painter (including edge AA), so the final ownership handoff is exact.
            revealInk=null;ink=UiTokens.ink(!fromNight);return;
        }
        revealInk=new RadialGradient(getWidth()*.5f,getHeight()*.5f,radius,
                new int[]{UiTokens.ink(!fromNight),UiTokens.ink(!fromNight),UiTokens.ink(fromNight)},
                new float[]{0,Math.max(0,radius-feather)/radius,1},Shader.TileMode.CLAMP);
    }
    @Override protected void onDraw(Canvas canvas){
        float d=getResources().getDisplayMetrics().density,cx=getWidth()*.5f,cy=getHeight()*.5f;
        float r=(9-4*sun)*d,offset=(4+16*sun)*d;
        paint.setShader(revealInk);paint.setColor(ink);paint.setAlpha(255);paint.setStrokeWidth(1.7f*d);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStyle(Paint.Style.STROKE);
        body.reset();body.addCircle(cx,cy,r,Path.Direction.CW);
        cut.reset();cut.addCircle(cx+offset,cy-offset,9*d,Path.Direction.CW);body.op(cut,Path.Op.DIFFERENCE);
        canvas.drawPath(body,paint);
        if(sun>0){
            paint.setAlpha(Math.round(255*sun));
            for(int i=0;i<8;i++){double a=i*Math.PI/4;float start=8*d,end=(8+3*sun)*d;
                canvas.drawLine(cx+(float)Math.cos(a)*start,cy+(float)Math.sin(a)*start,cx+(float)Math.cos(a)*end,cy+(float)Math.sin(a)*end,paint);}
        }
    }
}
