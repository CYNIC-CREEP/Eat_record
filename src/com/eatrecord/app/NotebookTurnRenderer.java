package com.eatrecord.app;

import android.graphics.*;
import android.view.View;

/** A moving fold: front text stays at its original size; the folded back is opaque paper. */
final class NotebookTurnRenderer {
    private final Bitmap front;
    private final Canvas frontCanvas;
    private final RectF bounds;
    private final int top, bottom;
    private final float density;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Path face=new Path(),back=new Path(),paperOutline=new Path(),reflectedOutline=new Path();
    private final RectF reflectedBounds=new RectF();
    private final float radius,exitCrease;
    private final LinearGradient paperBack;
    private final LinearGradient sheetShadow=new LinearGradient(0,0,1,0,
            0x44000000,Color.TRANSPARENT,Shader.TileMode.CLAMP);
    private final LinearGradient foldShadow=new LinearGradient(0,0,1,0,
            new int[]{0x09000000,0x00ffffff,0x22000000},new float[]{0,.65f,1},Shader.TileMode.CLAMP);
    private final Matrix sheetShadowMatrix=new Matrix(),foldShadowMatrix=new Matrix();

    NotebookTurnRenderer(View paper,RectF bounds,int top,int bottom){this(paper,bounds,top,bottom,0);}
    NotebookTurnRenderer(View paper,RectF bounds,int top,int bottom,float viewportLeft){
        this.bounds=new RectF(bounds);this.top=top|0xff000000;this.bottom=bottom|0xff000000;
        density=paper.getResources().getDisplayMetrics().density;
        // The reflected sheet ends at local 2*fold. Move that edge past the
        // actual viewport, including stroke AA, before the live page takes over.
        float gutter=Math.max(0,bounds.left-viewportLeft);
        float aa=Math.max(1,density*.6f)*.5f+1;
        exitCrease=(gutter+aa)*.5f;
        front=Bitmap.createBitmap(paper.getWidth(),paper.getHeight(),Bitmap.Config.ARGB_8888);
        paperBack=new LinearGradient(0,0,0,front.getHeight(),this.top,this.bottom,Shader.TileMode.CLAMP);
        radius=Math.min(UiTokens.NOTEBOOK_RADIUS_DP*density,Math.min(front.getWidth(),front.getHeight())*.5f);
        paperOutline.addRoundRect(new RectF(0,0,front.getWidth(),front.getHeight()),radius,radius,Path.Direction.CW);
        frontCanvas=new Canvas(front);
        int saved=frontCanvas.save();
        try{frontCanvas.clipPath(paperOutline);paper.draw(frontCanvas);}finally{frontCanvas.restoreToCount(saved);}
    }
    /** Refresh a late thumbnail in the same texture; never allocate or decode while drawing a frame. */
    boolean refresh(View paper){
        if(paper==null||front.isRecycled()||paper.getWidth()!=front.getWidth()||paper.getHeight()!=front.getHeight())return false;
        front.eraseColor(Color.TRANSPARENT);
        int saved=frontCanvas.save();
        try{frontCanvas.clipPath(paperOutline);paper.draw(frontCanvas);}finally{frontCanvas.restoreToCount(saved);}
        return true;
    }
    void draw(Canvas canvas,float progress,boolean forward){
        if(front.isRecycled())return;
        float t=Math.max(0,Math.min(1,forward?progress:1-progress));
        int saved=canvas.save();canvas.translate(bounds.left,bounds.top);
        float w=front.getWidth(),h=front.getHeight();
        if(t<=0){canvas.drawBitmap(front,0,0,null);canvas.restoreToCount(saved);return;}
        if(t>=1){canvas.restoreToCount(saved);return;}
        float fold=w-(w+exitCrease)*t,tip=2*fold-w,wave=(float)Math.sin(Math.PI*t),lift=12*density*wave;
        // The crease travels with the finger. No easing is applied during direct manipulation.
        face.reset();face.moveTo(0,0);face.lineTo(fold+lift*.35f,0);
        face.cubicTo(fold-lift*.3f,h*.3f,fold+lift*.3f,h*.7f,fold-lift*.35f,h);
        face.lineTo(0,h);face.close();
        int clip=canvas.save();canvas.clipPath(face);paint.setShader(null);paint.setColor(Color.WHITE);
        canvas.drawBitmap(front,0,0,paint);canvas.restoreToCount(clip);
        // Shadow lands on the next sheet, not on the text of the turning sheet.
        float shade=24*density*wave;
        if(shade>1){clip=canvas.save();canvas.clipPath(paperOutline);
            // Map the reusable unit gradient to the original moving endpoints.
            sheetShadowMatrix.setScale((fold+shade)-fold,1);sheetShadowMatrix.postTranslate(fold,0);
            sheetShadow.setLocalMatrix(sheetShadowMatrix);paint.setShader(sheetShadow);
            canvas.drawRect(fold,0,fold+shade,h,paint);paint.setShader(null);canvas.restoreToCount(clip);}
        // The fold bends sideways; it does not shorten the sheet's top and bottom.
        // Both faces use the same full-height silhouette as the real rounded paper.
        back.reset();back.moveTo(tip,0);
        back.lineTo(fold+lift*.35f,0);
        back.cubicTo(fold-lift*.3f,h*.3f,fold+lift*.3f,h*.7f,fold-lift*.35f,h);
        back.lineTo(tip,h);
        back.quadTo(tip-lift*.6f,h*.5f,tip,0);back.close();
        // Reflect the actual rounded sheet, not a square rectangle. Its outer corners remain
        // rounded as they cross the crease, including the last part of a complete turn.
        reflectedOutline.reset();
        reflectedBounds.set(tip,0,2*fold,h);
        reflectedOutline.addRoundRect(reflectedBounds,radius,radius,Path.Direction.CW);
        back.op(reflectedOutline,Path.Op.INTERSECT);
        paint.setColor(Color.WHITE);paint.setStyle(Paint.Style.FILL);
        paint.setShader(paperBack);canvas.drawPath(back,paint);paint.setShader(null);
        clip=canvas.save();canvas.clipPath(back);
        foldShadowMatrix.setScale(Math.max(tip+1,fold)-tip,1);foldShadowMatrix.postTranslate(tip,0);
        foldShadow.setLocalMatrix(foldShadowMatrix);paint.setShader(foldShadow);
        canvas.drawPath(back,paint);paint.setShader(null);canvas.restoreToCount(clip);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(1,density*.6f));
        paint.setColor(Color.argb(38,Color.red(top)/2,Color.green(top)/2,Color.blue(top)/2));
        canvas.drawPath(back,paint);paint.setStyle(Paint.Style.FILL);
        canvas.restoreToCount(saved);
    }
    void release(){if(!front.isRecycled())front.recycle();}
}
