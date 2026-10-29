package com.eatrecord.app;

import android.app.*;
import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import org.json.JSONArray;
import java.io.*;

/** Destructive-free brush preview; only replaces the PNG after Save. */
public final class CutoutEditor extends View {
    public interface Saved { void onSaved(JSONArray bounds); }
    private final Bitmap source, mask, initial;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint brush=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint maskCombine=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path stroke=new Path();
    private final RectF target=new RectF();
    private final CutoutViewport viewport;
    private static final float BRUSH_SCREEN_PX=28f;
    private boolean restore;
    private boolean strokeActive,navigationGesture,disposed;
    private int brushPointer=-1,firstPointer=-1,secondPointer=-1;
    private float previousX,previousY,focusX,focusY,span,strokeWidth;
    private CutoutEditor(Context c,Bitmap source,Bitmap cutout){
        super(c);this.source=source;
        mask=Bitmap.createBitmap(cutout.getWidth(),cutout.getHeight(),Bitmap.Config.ARGB_8888);
        new Canvas(mask).drawBitmap(cutout,0,0,null);
        initial=mask.copy(Bitmap.Config.ARGB_8888,false);
        viewport=new CutoutViewport(source.getWidth(),source.getHeight());
        brush.setStrokeCap(Paint.Cap.ROUND);brush.setStrokeJoin(Paint.Join.ROUND);brush.setStyle(Paint.Style.STROKE);brush.setColor(Color.WHITE);
        maskCombine.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        setTag("cutout_editor_canvas");setLayerType(View.LAYER_TYPE_SOFTWARE,null);
        setContentDescription("食物贴纸修整画布，拖动画笔擦除或恢复");
        setTooltipText("单指擦除或恢复，双指缩放和平移");
        if(android.os.Build.VERSION.SDK_INT>=30)setStateDescription("支持双指缩放和平移，画笔屏幕粗细保持不变");
    }
    @Override protected void onDraw(Canvas canvas){
        if(disposed)return;
        canvas.drawColor(Color.rgb(225,216,201));
        updateViewport();if(!viewport.ready())return;
        paint.setAlpha(40);canvas.drawBitmap(source,null,target,paint);paint.setAlpha(255);
        // Keep temporary layers viewport-sized even when the image is magnified six times.
        int layer=canvas.saveLayer(0,0,getWidth(),getHeight(),null);
        canvas.drawBitmap(source,null,target,paint);
        if(strokeActive){
            int maskLayer=canvas.saveLayer(0,0,getWidth(),getHeight(),maskCombine);
            canvas.drawBitmap(mask,null,target,paint);
            int saved=canvas.save();canvas.translate(target.left,target.top);canvas.scale(viewport.scale(),viewport.scale());canvas.drawPath(stroke,brush);canvas.restoreToCount(saved);
            canvas.restoreToCount(maskLayer);
        }else{
            // Pinch/pan has no preview stroke: retain the original single-layer fast path.
            paint.setXfermode(maskCombine.getXfermode());canvas.drawBitmap(mask,null,target,paint);paint.setXfermode(null);
        }
        canvas.restoreToCount(layer);
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        if(disposed)return false;updateViewport();if(!viewport.ready())return false;
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){
            cancelInteraction();navigationGesture=false;brushPointer=event.getPointerId(0);
            if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);
            if(viewport.contains(event.getX(),event.getY())){
                previousX=viewport.imageX(event.getX());previousY=viewport.imageY(event.getY());
                strokeWidth=viewport.brushImageWidth(BRUSH_SCREEN_PX);brush.setStrokeWidth(strokeWidth);
                brush.setXfermode(restore?null:new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
                stroke.moveTo(previousX,previousY);stroke.lineTo(previousX+.01f,previousY);strokeActive=true;invalidate();
            }
            return true;
        }
        if(action==MotionEvent.ACTION_POINTER_DOWN){
            discardStroke();navigationGesture=true;brushPointer=-1;beginPair(event,-1);invalidate();return true;
        }
        if(action==MotionEvent.ACTION_POINTER_UP){
            discardStroke();navigationGesture=true;brushPointer=-1;beginPair(event,event.getActionIndex());invalidate();return true;
        }
        if(action==MotionEvent.ACTION_MOVE){
            if(navigationGesture||event.getPointerCount()>1){
                if(!navigationGesture){discardStroke();navigationGesture=true;brushPointer=-1;beginPair(event,-1);}
                movePair(event);invalidate();return true;
            }
            int index=event.findPointerIndex(brushPointer);
            if(index<0){discardStroke();navigationGesture=true;return true;}
            if(strokeActive){appendPoint(event.getX(index),event.getY(index));invalidate();}return true;
        }
        if(action==MotionEvent.ACTION_UP){
            int index=event.findPointerIndex(brushPointer);
            if(!navigationGesture&&strokeActive&&index>=0){appendPoint(event.getX(index),event.getY(index));new Canvas(mask).drawPath(stroke,brush);performClick();}
            cancelInteraction();invalidate();return true;
        }
        if(action==MotionEvent.ACTION_CANCEL){cancelInteraction();invalidate();return true;}
        return true;
    }
    private void appendPoint(float x,float y){previousX=viewport.imageX(x);previousY=viewport.imageY(y);stroke.lineTo(previousX,previousY);}
    private void updateViewport(){viewport.resize(getWidth(),getHeight());target.set(viewport.left(),viewport.top(),viewport.right(),viewport.bottom());}
    private void discardStroke(){strokeActive=false;stroke.reset();}
    private void cancelInteraction(){discardStroke();brushPointer=firstPointer=secondPointer=-1;navigationGesture=false;span=0;if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);}
    private void beginPair(MotionEvent event,int excluded){
        firstPointer=secondPointer=-1;span=0;
        for(int i=0;i<event.getPointerCount();i++)if(i!=excluded){if(firstPointer<0)firstPointer=event.getPointerId(i);else{secondPointer=event.getPointerId(i);break;}}
        if(secondPointer>=0){int a=event.findPointerIndex(firstPointer),b=event.findPointerIndex(secondPointer);focusX=(event.getX(a)+event.getX(b))*.5f;focusY=(event.getY(a)+event.getY(b))*.5f;span=distance(event,a,b);}
    }
    private void movePair(MotionEvent event){
        if(event.getPointerCount()<2)return;
        int a=event.findPointerIndex(firstPointer),b=event.findPointerIndex(secondPointer);
        if(a<0||b<0){beginPair(event,-1);return;}
        float x=(event.getX(a)+event.getX(b))*.5f,y=(event.getY(a)+event.getY(b))*.5f,nextSpan=distance(event,a,b);
        viewport.transform(focusX,focusY,x,y,span>1&&nextSpan>1?nextSpan/span:1);
        focusX=x;focusY=y;span=nextSpan;updateViewport();
    }
    private static float distance(MotionEvent event,int a,int b){float x=event.getX(a)-event.getX(b),y=event.getY(a)-event.getY(b);return (float)Math.sqrt(x*x+y*y);}
    private void setRestore(boolean value){cancelInteraction();restore=value;invalidate();}
    private void resetMask(){cancelInteraction();Canvas reset=new Canvas(mask);reset.drawColor(Color.TRANSPARENT,PorterDuff.Mode.CLEAR);reset.drawBitmap(initial,0,0,new Paint());viewport.reset();updateViewport();invalidate();}
    private void release(){if(disposed)return;cancelInteraction();disposed=true;source.recycle();mask.recycle();initial.recycle();}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);cancelInteraction();updateViewport();}
    @Override protected void onDetachedFromWindow(){cancelInteraction();super.onDetachedFromWindow();}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(visibility!=VISIBLE&&viewport!=null)cancelInteraction();}
    @Override public boolean performClick(){super.performClick();return true;}
    public static void show(Activity a,File original,File output,JSONArray bounds,boolean night,Saved saved){
        Bitmap image=null,cutout=null;
        try{
            BitmapFactory.Options opts=new BitmapFactory.Options();BitmapFactory.Options size=new BitmapFactory.Options();size.inJustDecodeBounds=true;
            BitmapFactory.decodeFile(original.getAbsolutePath(),size);
            opts.inSampleSize=1;while(Math.max(size.outWidth,size.outHeight)/opts.inSampleSize>2048)opts.inSampleSize*=2;
            image=BitmapFactory.decodeFile(original.getAbsolutePath(),opts);cutout=BitmapFactory.decodeFile(output.getAbsolutePath());
            if(image==null)throw new IOException();
            float ratio=Math.min(1,1024f/Math.max(image.getWidth(),image.getHeight()));
            Bitmap scaled=Bitmap.createScaledBitmap(image,Math.max(1,Math.round(image.getWidth()*ratio)),Math.max(1,Math.round(image.getHeight()*ratio)),true);
            if(scaled!=image)image.recycle();image=scaled;
            if(cutout==null)cutout=image.copy(Bitmap.Config.ARGB_8888,false);
            else if(bounds!=null&&bounds.length()==4){
                // Work on the full original so Restore can recover a missing plate outside the old crop.
                Rect region=new Rect(bounds.getInt(0),bounds.getInt(1),bounds.getInt(2),bounds.getInt(3));
                if(region.left<0||region.top<0||region.right>image.getWidth()||region.bottom>image.getHeight()||region.isEmpty())throw new IOException();
                Bitmap aligned=Bitmap.createBitmap(image.getWidth(),image.getHeight(),Bitmap.Config.ARGB_8888);
                new Canvas(aligned).drawBitmap(cutout,null,region,new Paint(Paint.FILTER_BITMAP_FLAG));cutout.recycle();cutout=aligned;
            }
            if(image.getWidth()!=cutout.getWidth()||image.getHeight()!=cutout.getHeight())throw new IOException();
            final CutoutEditor editor=new CutoutEditor(a,image,cutout);cutout.recycle();
            LinearLayout box=new LinearLayout(a);box.setOrientation(1);box.addView(editor,new LinearLayout.LayoutParams(-1,PaperComponents.dp(a,360)));
            TextView hint=PaperComponents.text(a,"单指擦除或恢复；双指缩放、平移，画笔粗细不变。",13,UiTokens.secondary(night),false);box.addView(hint);
            ToggleButton mode=new ToggleButton(a);mode.setTextOff("擦除");mode.setTextOn("恢复");mode.setChecked(false);mode.setOnCheckedChangeListener((v,on)->editor.setRestore(on));box.addView(mode);
            AlertDialog dialog=PaperComponents.dialogBuilder(a,night).setTitle("修整食物贴纸").setView(box).setNegativeButton("取消",null).setNeutralButton("重置",null).setPositiveButton("保存",null).create();
            dialog.setOnShowListener(d->{
                dialog.getButton(-3).setOnClickListener(v->editor.resetMask());
                dialog.getButton(-1).setOnClickListener(v->{
                    Bitmap result=Bitmap.createBitmap(editor.source.getWidth(),editor.source.getHeight(),Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(result);canvas.drawBitmap(editor.source,0,0,null);
                    Paint p=new Paint();p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));canvas.drawBitmap(editor.mask,0,0,p);
                    int left=result.getWidth(),top=result.getHeight(),right=-1,bottom=-1;int[] row=new int[result.getWidth()];
                    for(int y=0;y<result.getHeight();y++){result.getPixels(row,0,row.length,0,y,row.length,1);for(int x=0;x<row.length;x++)if(Color.alpha(row[x])>24){left=Math.min(left,x);top=Math.min(top,y);right=Math.max(right,x);bottom=Math.max(bottom,y);}}
                    if(right<left){result.recycle();Toast.makeText(a,"贴纸已被擦空，请恢复部分主体",Toast.LENGTH_LONG).show();return;}
                    left=Math.max(0,left-8);top=Math.max(0,top-8);right=Math.min(result.getWidth(),right+9);bottom=Math.min(result.getHeight(),bottom+9);
                    final JSONArray savedBounds=new JSONArray().put(left).put(top).put(right).put(bottom);
                    Bitmap trimmed=Bitmap.createBitmap(result,left,top,right-left,bottom-top);if(trimmed!=result)result.recycle();result=trimmed;
                    try(FileOutputStream stream=new FileOutputStream(new File(output.getAbsolutePath()+".partial"))){
                        if(!result.compress(Bitmap.CompressFormat.PNG,100,stream))throw new IOException();
                        stream.flush();
                    }catch(Exception e){Toast.makeText(a,"贴纸保存失败",Toast.LENGTH_LONG).show();result.recycle();return;}
                    result.recycle();File partial=new File(output.getAbsolutePath()+".partial");
                    if(!partial.renameTo(output)){Toast.makeText(a,"贴纸无法替换",Toast.LENGTH_LONG).show();return;}
                    dialog.dismiss();saved.onSaved(savedBounds);
                });
            });
            dialog.setOnDismissListener(d->editor.release());dialog.show();PaperComponents.styleDialog(dialog,night);
        }catch(Exception|OutOfMemoryError e){if(image!=null&&!image.isRecycled())image.recycle();if(cutout!=null&&!cutout.isRecycled())cutout.recycle();Toast.makeText(a,"图片无法编辑，请先重新抠图",Toast.LENGTH_LONG).show();}
    }
}
