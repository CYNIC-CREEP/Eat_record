package com.eatrecord.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Expanded drawing space for the original nav paper. No child layout, input or accessibility. */
final class BottomNavBackdrop extends View {
    private final View host;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect=new RectF();
    private float swell;
    private int surface,line;
    private boolean night;
    BottomNavBackdrop(Context context,View host){
        super(context);this.host=host;
        setWillNotDraw(false);setLayerType(LAYER_TYPE_SOFTWARE,null);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);setClickable(false);setFocusable(false);
        addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(l!=ol||t!=ot||r!=or||b!=ob)host.invalidate();});
    }
    void appearance(int surface,int line,boolean night){
        if(this.surface!=surface||this.line!=line||this.night!=night){this.surface=surface;this.line=line;this.night=night;invalidate();}
    }
    void swell(float value){float next=Math.max(0,Math.min(1,value));if(next!=swell){swell=next;invalidate();}}
    @Override protected void onDraw(Canvas canvas){
        if(host.getVisibility()!=VISIBLE||host.getWidth()<=0||host.getHeight()<=0)return;
        float d=getResources().getDisplayMetrics().density,inset=6*d;
        float x=host.getLeft()-getLeft(),y=host.getTop()-getTop();
        rect.set(x+inset,y+inset,x+host.getWidth()-inset,y+host.getHeight()-inset);
        int saved=canvas.save();canvas.scale(NavLensGeometry.barScaleX(swell),NavLensGeometry.barScaleY(swell),rect.centerX(),rect.centerY());
        float radius=rect.height()*.5f;
        paint.setStyle(Paint.Style.FILL);paint.setColor(surface);
        paint.setShadowLayer(5*d*(1+.25f*swell),0,.5f*d*swell,night?Color.argb(70+Math.round(18*swell),0,0,0):Color.argb(36+Math.round(12*swell),72,60,48));
        canvas.drawRoundRect(rect,radius,radius,paint);paint.clearShadowLayer();
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(d);paint.setColor(line);canvas.drawRoundRect(rect,radius,radius,paint);
        canvas.restoreToCount(saved);
    }
}
