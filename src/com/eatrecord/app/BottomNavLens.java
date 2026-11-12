package com.eatrecord.app;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import android.view.ViewGroup;

/** A small noninteractive foreground lens; draws the existing icon/text Views, never clones them. */
final class BottomNavLens extends View {
    private final ViewGroup bar,host;
    private final RectF bounds=new RectF();
    private final Path contour=new Path();
    private final float[] radii=new float[8];
    private final Paint body=new Paint(Paint.ANTI_ALIAS_FLAG),rim=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconTint=new Paint(),textTint=new Paint();
    private final Matrix gradientMatrix=new Matrix();
    private final Paint glint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mist=new Paint(Paint.ANTI_ALIAS_FLAG),sheenPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mistBounds=new RectF();
    private LinearGradient water,sheen;
    private int tint;
    private float lift,jelly,contentScale=1;
    private int fill,accent,ink,line;
    private boolean night,ready;
    BottomNavLens(Context context,ViewGroup bar,ViewGroup host){
        super(context);this.bar=bar;this.host=host;
        setWillNotDraw(false);setLayerType(LAYER_TYPE_SOFTWARE,null);
        mist.setMaskFilter(new BlurMaskFilter(6*getResources().getDisplayMetrics().density,BlurMaskFilter.Blur.NORMAL));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);setFocusable(false);setClickable(false);
    }
    void appearance(int fill,int tint,int accent,int ink,int line,boolean night){
        if(this.fill==fill&&this.tint==tint&&this.accent==accent&&this.ink==ink&&this.line==line&&this.night==night)return;
        this.fill=fill;this.tint=tint;this.accent=accent;this.ink=ink;this.line=line;this.night=night;
        water=new LinearGradient(0,0,0,1,new int[]{alpha(tint,night?115:146),alpha(tint,night?84:112),alpha(tint,night?108:139)},new float[]{0,.48f,1},Shader.TileMode.CLAMP);
        int milk=UiTokens.card(false);
        sheen=new LinearGradient(0,0,0,1,new int[]{alpha(milk,night?32:76),alpha(milk,0),alpha(milk,night?5:14)},new float[]{0,.6f,1},Shader.TileMode.CLAMP);
        iconTint.setColorFilter(new PorterDuffColorFilter(accent,PorterDuff.Mode.SRC_IN));
        textTint.setColorFilter(new PorterDuffColorFilter(ink,PorterDuff.Mode.SRC_IN));invalidate();
    }
    void geometry(float center,float top,float width,float height,float lift,float jelly,float scale,float direction){
        this.lift=Math.max(0,Math.min(1,lift));this.jelly=jelly;contentScale=scale;
        float x=host.getLeft()-getLeft()+center,y=host.getTop()-getTop()+top;
        bounds.set(x-width*.5f,y,x+width*.5f,y+height);
        float round=Math.min(width,height)*.5f;
        float asym=Math.min(.16f,Math.abs(jelly))*(direction<0?-1:1);
        for(int i=0;i<8;i+=2){boolean left=i==0||i==6;radii[i]=round*(1+(left?asym:-asym));radii[i+1]=round;}
        contour.reset();contour.addRoundRect(bounds,radii,Path.Direction.CW);ready=width>0&&height>0;invalidate();
        if(bar instanceof BottomNavItems)((BottomNavItems)bar).lensClip(contour,getLeft()-host.getLeft()-bar.getLeft(),getTop()-host.getTop()-bar.getTop());
    }
    void hide(){ready=false;if(bar instanceof BottomNavItems)((BottomNavItems)bar).clearLensClip();invalidate();}
    private static int alpha(int color,int opacity){return (color&0x00ffffff)|(opacity<<24);}
    @Override protected void onDraw(Canvas canvas){
        if(!ready||bar.getVisibility()!=VISIBLE||host.getVisibility()!=VISIBLE)return;
        float density=getResources().getDisplayMetrics().density;
        float emphasis=Math.max(lift,Math.min(1,Math.abs(jelly)*4));
        // Frost approximation stays entirely in the background: no backdrop capture or blurred text.
        body.setShader(null);body.setColor(UiTokens.card(night));body.setAlpha(night?218:232);body.setStyle(Paint.Style.FILL);
        body.setShadowLayer((2.5f+2*emphasis)*density,0,(.6f+.8f*emphasis)*density,alpha(0xff000000,Math.round((night?48:22)+(night?15:12)*emphasis)));
        canvas.drawPath(contour,body);body.clearShadowLayer();body.setAlpha(255);
        gradientMatrix.setScale(1,bounds.height());gradientMatrix.postTranslate(0,bounds.top);
        if(water!=null){water.setLocalMatrix(gradientMatrix);body.setShader(water);canvas.drawPath(contour,body);}
        int frost=canvas.save();canvas.clipPath(contour);
        mistBounds.set(bounds);mistBounds.inset(bounds.width()*.15f,bounds.height()*.23f);
        mist.setColor(UiTokens.card(false));mist.setAlpha(night?12:30);canvas.drawRoundRect(mistBounds,mistBounds.height()*.5f,mistBounds.height()*.5f,mist);
        if(sheen!=null){sheen.setLocalMatrix(gradientMatrix);sheenPaint.setShader(sheen);canvas.drawPath(contour,sheenPaint);}
        canvas.restoreToCount(frost);
        int clipped=canvas.save();canvas.clipPath(contour);
        for(int i=0;i<bar.getChildCount();i++){
            View item=bar.getChildAt(i);if(!(item instanceof ViewGroup)||item.getWidth()<=0)continue;
            float x=host.getLeft()-getLeft()+bar.getLeft()+item.getLeft();
            float y=host.getTop()-getTop()+bar.getTop()+item.getTop();
            if(x+item.getWidth()<bounds.left||x>bounds.right)continue;
            float influence=Math.max(0,1-Math.abs(x+item.getWidth()*.5f-bounds.centerX())/Math.max(1,bounds.width()));
            float scale=1+(contentScale-1)*influence;
            int local=canvas.save();canvas.translate(x,y);
            canvas.scale(scale,scale,item.getWidth()*.5f,item.getHeight()*.5f);
            ViewGroup group=(ViewGroup)item;
            for(int c=0;c<group.getChildCount();c++){
                View child=group.getChildAt(c);if(child.getVisibility()!=VISIBLE)continue;
                int layer=canvas.saveLayer(0,0,item.getWidth(),item.getHeight(),c==0?iconTint:textTint);
                canvas.translate(child.getLeft()+child.getTranslationX(),child.getTop()+child.getTranslationY());child.draw(canvas);canvas.restoreToCount(layer);
            }
            canvas.restoreToCount(local);
        }
        canvas.restoreToCount(clipped);
        {
            rim.setStyle(Paint.Style.STROKE);rim.setStrokeWidth(density*(.7f+.4f*lift));
            rim.setColor(tint);rim.setAlpha(Math.round((night?88:72)*(0.65f+.35f*emphasis)));canvas.drawPath(contour,rim);
            glint.setColor(UiTokens.card(false));glint.setAlpha(Math.round((night?65:135)*(0.65f+.35f*emphasis)));glint.setStyle(Paint.Style.STROKE);glint.setStrokeWidth(density);
            int edge=canvas.save();canvas.clipPath(contour);canvas.clipRect(bounds.left,bounds.top,bounds.right,bounds.top+bounds.height()*.32f);
            canvas.drawPath(contour,glint);canvas.restoreToCount(edge);
        }
    }
}
