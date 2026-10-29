package com.eatrecord.app;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Background and recognition glyph are drawn together, independent of TextView/OEM font rendering. */
public final class RecognitionButtonDrawable extends Drawable {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path star=new Path();
    private final int fill,ink;
    public RecognitionButtonDrawable(int fill,int ink){this.fill=fill;this.ink=ink|0xff000000;}
    @Override public void draw(Canvas canvas){
        Rect b=getBounds();float cx=b.exactCenterX(),cy=b.exactCenterY(),r=b.height()*.19f;
        paint.setStyle(Paint.Style.FILL);paint.setColor(fill);
        canvas.drawRoundRect(new RectF(b),b.height()/2f,b.height()/2f,paint);
        star.reset();star.moveTo(cx,cy-r);
        star.cubicTo(cx+r*.2f,cy-r*.2f,cx+r*.2f,cy-r*.2f,cx+r,cy);
        star.cubicTo(cx+r*.2f,cy+r*.2f,cx+r*.2f,cy+r*.2f,cx,cy+r);
        star.cubicTo(cx-r*.2f,cy+r*.2f,cx-r*.2f,cy+r*.2f,cx-r,cy);
        star.cubicTo(cx-r*.2f,cy-r*.2f,cx-r*.2f,cy-r*.2f,cx,cy-r);star.close();
        paint.setColor(ink);canvas.drawPath(star,paint);
    }
    @Override public void getOutline(Outline outline){Rect b=getBounds();outline.setRoundRect(b,b.height()/2f);}
    @Override public void setAlpha(int alpha){} @Override public void setColorFilter(ColorFilter filter){}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
