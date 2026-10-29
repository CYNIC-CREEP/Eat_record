package com.eatrecord.app;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/** A code-drawn handwriting nib, using the same paper and theme ink as the rest of the app. */
public final class ManualMealButtonDrawable extends Drawable {
    private final int paper, line, ink;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    public ManualMealButtonDrawable(int paper, int line, int ink) { this.paper=paper;this.line=line;this.ink=ink; }
    @Override public void draw(Canvas canvas) {
        Rect b=getBounds();float size=Math.min(b.width(),b.height()),unit=size/56f,cx=b.exactCenterX(),cy=b.exactCenterY();
        paint.setStyle(Paint.Style.FILL);paint.setColor(paper);canvas.drawCircle(cx,cy,size/2f-unit,paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(unit);paint.setColor(line);canvas.drawCircle(cx,cy,size/2f-unit,paint);
        canvas.save();canvas.translate(cx-12*unit,cy-12*unit);canvas.scale(unit,unit);
        paint.setColor(ink);paint.setStrokeWidth(2f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);
        path.reset();path.moveTo(4,17);path.lineTo(5,12);path.lineTo(15.5f,2);
        path.quadTo(16.5f,1,17.5f,2);path.lineTo(21,5.5f);path.quadTo(22,6.5f,21,7.5f);
        path.lineTo(10,17);path.close();canvas.drawPath(path,paint);
        canvas.drawLine(14,3.5f,19.5f,9,paint);
        path.reset();path.moveTo(3,22);path.quadTo(7,19.5f,11,21);path.quadTo(15,22.5f,21,20.5f);canvas.drawPath(path,paint);
        canvas.restore();
    }
    @Override public void setAlpha(int alpha) { }
    @Override public void setColorFilter(ColorFilter filter) { }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
