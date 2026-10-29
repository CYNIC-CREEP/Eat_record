package com.eatrecord.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Small, theme-tinted outline glyphs; intentionally no tile, shadow or emoji font. */
public final class ProfileSettingIcon extends View {
    private final String kind;
    private final int ink;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();

    public ProfileSettingIcon(Context context, String kind, int accent, boolean night) {
        this(context,kind,accent,night,false);
    }

    /** Settings rows supply their already softened ink; personal-profile glyphs retain their accent. */
    public ProfileSettingIcon(Context context, String kind, int accent, boolean night, boolean exactTint) {
        super(context);
        this.kind = kind == null ? "profile" : kind;
        // Lift saturated accents on dark surfaces without replacing their selected hue.
        float white = night ? .25f : 0f;
        ink = exactTint ? accent : Color.rgb(Math.round(Color.red(accent) * (1f - white) + 255 * white),
                Math.round(Color.green(accent) * (1f - white) + 255 * white),
                Math.round(Color.blue(accent) * (1f - white) + 255 * white));
        setTag("profile_info_icon");
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        setWillNotDraw(false);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float size = Math.min(getWidth(), getHeight()) * .78f;
        if (size <= 0) return;
        // Composite the completed glyph once: semitransparent joins must not become darker
        // than the ordinary settings icons merely because two outline segments overlap.
        int saved = Color.alpha(ink) < 255
                ? canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), Color.alpha(ink)) : canvas.save();
        canvas.translate((getWidth() - size) / 2f, (getHeight() - size) / 2f);
        canvas.scale(size / 24f, size / 24f);
        paint.setColor(ink | 0xff000000);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.7f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        path.reset();
        if ("id".equals(kind)) {
            round(canvas, 2.5f, 5, 21.5f, 19, 3);
            canvas.drawCircle(8, 10.5f, 2, paint);
            arc(canvas, 4.5f, 12, 11.5f, 18, 205, 130);
            line(canvas, 14, 9, 18, 9); line(canvas, 14, 13, 18, 13);
        } else if ("signature".equals(kind)) {
            path.moveTo(4, 17); path.lineTo(5, 12.5f); path.lineTo(15.5f, 2.5f);
            path.quadTo(16.5f, 1.5f, 17.5f, 2.5f); path.lineTo(20.5f, 5.5f);
            path.quadTo(21.5f, 6.5f, 20.5f, 7.5f); path.lineTo(10, 17);
            path.close(); canvas.drawPath(path, paint);
            line(canvas, 14, 4, 19, 9); line(canvas, 4, 21, 20, 21);
        } else if ("gender".equals(kind)) {
            canvas.drawCircle(9.5f, 10, 5, paint);
            line(canvas, 9.5f, 15, 9.5f, 22); line(canvas, 6.5f, 19, 12.5f, 19);
            line(canvas, 13, 6.5f, 19.5f, 1); line(canvas, 15, 1, 19.5f, 1);
            line(canvas, 19.5f, 1, 19.5f, 5.5f);
        } else if ("body".equals(kind)) {
            round(canvas, 3, 3, 21, 22, 3);
            arc(canvas, 7, 5, 17, 15, 205, 130);
            line(canvas, 12, 10, 14.5f, 7.5f);
            line(canvas, 7.5f, 18, 16.5f, 18);
        } else if ("habit".equals(kind)) {
            path.moveTo(4, 15); path.cubicTo(4, 8, 11, 4, 20, 3);
            path.cubicTo(22, 12, 18, 19, 10, 20); path.cubicTo(7, 20, 4, 18, 4, 15);
            canvas.drawPath(path, paint); line(canvas, 3, 22, 15, 9);
            line(canvas, 9, 15, 14, 15);
        } else if ("dining".equals(kind)) {
            canvas.drawCircle(12, 12, 5.5f, paint);
            line(canvas, 2.5f, 2, 2.5f, 22); line(canvas, .5f, 2, .5f, 7);
            line(canvas, 4.5f, 2, 4.5f, 7); line(canvas, .5f, 7, 4.5f, 7);
            line(canvas, 22, 2, 22, 22);
            path.reset(); path.moveTo(22, 2); path.quadTo(18, 7, 22, 11);
            canvas.drawPath(path, paint);
        } else if ("spend".equals(kind)) {
            round(canvas, 2.5f, 5, 21.5f, 20, 3);
            path.moveTo(5, 5); path.lineTo(17, 2); path.lineTo(19, 5); canvas.drawPath(path, paint);
            round(canvas, 14.5f, 10, 22, 16, 1.5f);
            dot(canvas, 17.5f, 13, .7f);
        } else if ("password".equals(kind)) {
            round(canvas, 4, 10, 20, 22, 3);
            path.moveTo(7, 10); path.lineTo(7, 7); path.cubicTo(7, 0, 17, 0, 17, 7);
            path.lineTo(17, 10); canvas.drawPath(path, paint);
            dot(canvas, 12, 15, 1); line(canvas, 12, 16, 12, 18);
        } else if ("login".equals(kind)) {
            path.moveTo(12, 3); path.lineTo(18, 3); path.quadTo(21, 3, 21, 6);
            path.lineTo(21, 18); path.quadTo(21, 21, 18, 21); path.lineTo(12, 21);
            canvas.drawPath(path, paint); line(canvas, 2, 12, 15, 12);
            path.reset(); path.moveTo(10, 7); path.lineTo(15, 12); path.lineTo(10, 17);
            canvas.drawPath(path, paint);
        } else if ("logout".equals(kind)) {
            path.moveTo(11, 3); path.lineTo(6, 3); path.quadTo(3, 3, 3, 6);
            path.lineTo(3, 18); path.quadTo(3, 21, 6, 21); path.lineTo(11, 21);
            canvas.drawPath(path, paint); line(canvas, 10, 12, 23, 12);
            path.reset(); path.moveTo(18, 7); path.lineTo(23, 12); path.lineTo(18, 17);
            canvas.drawPath(path, paint);
        } else if ("delete".equals(kind)) {
            canvas.drawCircle(9, 6, 3.5f, paint);
            arc(canvas, 1.5f, 12, 16.5f, 25, 205, 130);
            line(canvas, 17, 12, 23, 18); line(canvas, 23, 12, 17, 18);
        } else if ("reminder".equals(kind)) {
            path.moveTo(5, 16);path.lineTo(6, 10);path.cubicTo(6, 3, 18, 3, 18, 10);
            path.lineTo(19, 16);path.lineTo(21, 18);path.lineTo(3, 18);path.close();canvas.drawPath(path,paint);
            line(canvas,12,2,12,4);arc(canvas,9,18,15,24,15,150);
        } else if ("doll".equals(kind)) {
            // A quiet little companion: one head outline, two eyes and a smile.
            round(canvas,3,3,21,20,7);
            dot(canvas,8,10,.8f);dot(canvas,16,10,.8f);
            arc(canvas,9,11,15,16,20,140);
        } else if ("smart".equals(kind)) {
            path.moveTo(12, 2); path.quadTo(13.5f, 9.5f, 21, 11);
            path.quadTo(13.5f, 12.5f, 12, 20); path.quadTo(10.5f, 12.5f, 3, 11);
            path.quadTo(10.5f, 9.5f, 12, 2); path.close(); canvas.drawPath(path, paint);
            line(canvas, 20, 2, 20, 6); line(canvas, 18, 4, 22, 4);
            dot(canvas, 4, 20, 1.2f);
        } else if ("switch".equals(kind)) {
            line(canvas,3,7,21,7);path.moveTo(16,2);path.lineTo(21,7);path.lineTo(16,12);canvas.drawPath(path,paint);
            line(canvas,21,17,3,17);path.reset();path.moveTo(8,12);path.lineTo(3,17);path.lineTo(8,22);canvas.drawPath(path,paint);
        } else if ("reset".equals(kind)) {
            path.moveTo(4, 9); path.cubicTo(5.2f, 5.4f, 8, 3, 12, 3);
            path.cubicTo(17, 3, 21, 7, 21, 12); path.cubicTo(21, 17, 17, 21, 12, 21);
            path.cubicTo(8, 21, 5.4f, 18.8f, 4, 16); canvas.drawPath(path, paint);
            path.reset(); path.moveTo(4, 3); path.lineTo(4, 9); path.lineTo(10, 9);
            canvas.drawPath(path, paint); line(canvas, 12, 7.5f, 12, 12.5f);
            line(canvas, 12, 12.5f, 15.5f, 14.5f);
        } else if ("back".equals(kind) || "chevron".equals(kind)) {
            boolean back = "back".equals(kind);
            path.moveTo(back ? 15 : 9, 5); path.lineTo(back ? 8 : 16, 12);
            path.lineTo(back ? 15 : 9, 19); canvas.drawPath(path, paint);
        } else {
            canvas.drawCircle(12, 6.5f, 4, paint);
            arc(canvas, 3, 12, 21, 27, 205, 130);
        }
        canvas.restoreToCount(saved);
    }

    private void line(Canvas c, float x1, float y1, float x2, float y2) { c.drawLine(x1, y1, x2, y2, paint); }
    private void round(Canvas c, float left, float top, float right, float bottom, float radius) {
        rect.set(left, top, right, bottom); c.drawRoundRect(rect, radius, radius, paint);
    }
    private void arc(Canvas c, float left, float top, float right, float bottom, float start, float sweep) {
        rect.set(left, top, right, bottom); c.drawArc(rect, start, sweep, false, paint);
    }
    private void dot(Canvas c, float x, float y, float radius) {
        paint.setStyle(Paint.Style.FILL); c.drawCircle(x, y, radius, paint); paint.setStyle(Paint.Style.STROKE);
    }
}
