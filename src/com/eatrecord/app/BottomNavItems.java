package com.eatrecord.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Path;
import android.widget.LinearLayout;

/** Removes only the foreground lens region from normal drawing, never from layout/input/semantics. */
final class BottomNavItems extends LinearLayout {
    private final Path cutout=new Path();
    private final Matrix offset=new Matrix();
    private boolean lensVisible;
    BottomNavItems(Context context){super(context);}
    void lensClip(Path path,float dx,float dy){
        lensVisible=true;offset.setTranslate(dx,dy);cutout.set(path);cutout.transform(offset);invalidate();
    }
    void clearLensClip(){lensVisible=false;cutout.reset();invalidate();}
    @Override protected void dispatchDraw(Canvas canvas){
        int saved=canvas.save();if(lensVisible)canvas.clipOutPath(cutout);
        super.dispatchDraw(canvas);canvas.restoreToCount(saved);
    }
}
