package com.eatrecord.app;

import android.app.*;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import org.json.JSONArray;
import java.io.*;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    // Commands retain geometry, not a 4 MiB image for every stroke. Older commands
    // are folded into one checkpoint; Reset still refers to the opening mask.
    private static final int HISTORY_LIMIT=32;
    private final ArrayList<Edit> history=new ArrayList<>();
    private int historyPosition;
    private Bitmap historyBase;
    private Runnable historyChanged;
    private final Path previewClip=new Path(),photoClip=new Path();
    private final RectF previewBounds=new RectF(),outlineTarget=new RectF();
    private final boolean night;
    private final String outlineStyle;
    private boolean outlineVisible=true,outlineBusy;
    private long maskRevision,outlineRevision=-1,outlineEpoch,outlineAppliedEpoch=-1,outlineLastRequest;
    private int outlineImageWidth,outlineImageHeight;
    private Bitmap outline;
    private final ExecutorService outlineWorker=Executors.newSingleThreadExecutor();
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final Runnable outlineRefresh=()->requestOutline();
    private static final float BRUSH_SCREEN_PX=28f;
    private boolean restore;
    private boolean strokeActive,navigationGesture,disposed;
    private int brushPointer=-1,firstPointer=-1,secondPointer=-1;
    private float previousX,previousY,focusX,focusY,span,strokeWidth;
    private CutoutEditor(Context c,Bitmap source,Bitmap cutout){
        this(c,source,cutout,AppearanceMode.isNight(NutritionStore.read(c.getSharedPreferences(NutritionStore.PREFS,0),NutritionStore.PROFILE),false));
    }
    private CutoutEditor(Context c,Bitmap source,Bitmap cutout,boolean night){
        super(c);this.source=source;
        this.night=night;outlineStyle=FoodStickerRenderer.style(c);
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
        int clipped=canvas.save();canvas.clipPath(previewClip);
        canvas.drawColor(night?UiTokens.paper(true):Color.rgb(234,227,215));
        updateViewport();if(!viewport.ready()){canvas.restoreToCount(clipped);return;}
        photoClip.reset();float photoRadius=PaperComponents.dp(getContext(),16);photoClip.addRoundRect(target,photoRadius,photoRadius,Path.Direction.CW);
        int photo=canvas.save();canvas.clipPath(photoClip);paint.setAlpha(40);canvas.drawBitmap(source,null,target,paint);paint.setAlpha(255);canvas.restoreToCount(photo);
        if(outlineVisible&&outline!=null&&outlineAppliedEpoch==outlineEpoch){
            float imageWidth=outlineImageWidth,imageHeight=outlineImageHeight;
            float padX=(outline.getWidth()-imageWidth)*.5f*target.width()/imageWidth,padY=(outline.getHeight()-imageHeight)*.5f*target.height()/imageHeight;
            outlineTarget.set(target.left-padX,target.top-padY,target.right+padX,target.bottom+padY);
            canvas.drawBitmap(outline,null,outlineTarget,paint);
        }
        // Keep temporary layers viewport-sized even when the image is magnified six times.
        photo=canvas.save();canvas.clipPath(photoClip);
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
        canvas.restoreToCount(photo);
        canvas.restoreToCount(clipped);
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
                stroke.moveTo(previousX,previousY);stroke.lineTo(previousX+.01f,previousY);strokeActive=true;outlineEpoch++;maskRevision++;requestOutline();invalidate();
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
            if(!navigationGesture&&strokeActive&&index>=0){appendPoint(event.getX(index),event.getY(index));commit(new Edit(stroke,brush));performClick();}
            cancelInteraction();invalidate();return true;
        }
        if(action==MotionEvent.ACTION_CANCEL){cancelInteraction();invalidate();return true;}
        return true;
    }
    private void appendPoint(float x,float y){previousX=viewport.imageX(x);previousY=viewport.imageY(y);stroke.lineTo(previousX,previousY);maskRevision++;requestOutline();}
    private void updateViewport(){viewport.resize(getWidth(),getHeight());target.set(viewport.left(),viewport.top(),viewport.right(),viewport.bottom());}
    private void discardStroke(){boolean active=strokeActive;strokeActive=false;stroke.reset();if(active){outlineEpoch++;maskRevision++;requestOutline();}}
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
    private static final class Edit{
        final Path path;final Paint paint;
        Edit(Path path,Paint paint){this.path=new Path(path);this.paint=new Paint(paint);}
        Edit(){path=null;paint=null;}
    }
    private void apply(Edit edit,Bitmap destination){
        Canvas canvas=new Canvas(destination);
        if(edit.path==null){canvas.drawColor(Color.TRANSPARENT,PorterDuff.Mode.CLEAR);canvas.drawBitmap(initial,0,0,null);}
        else canvas.drawPath(edit.path,edit.paint);
    }
    private void commit(Edit edit){
        // Allocate the single checkpoint before changing the current mask/history.
        if(historyPosition==HISTORY_LIMIT&&historyBase==null){
            try{historyBase=initial.copy(Bitmap.Config.ARGB_8888,true);if(historyBase==null)throw new OutOfMemoryError();}
            catch(OutOfMemoryError ignored){Toast.makeText(getContext(),"内存不足，请先保存当前修整",Toast.LENGTH_SHORT).show();return;}
        }
        while(history.size()>historyPosition)history.remove(history.size()-1);
        apply(edit,mask);history.add(edit);historyPosition++;
        if(history.size()>HISTORY_LIMIT){apply(history.remove(0),historyBase);historyPosition--;}
        maskChanged();
    }
    private void replay(){
        Canvas canvas=new Canvas(mask);canvas.drawColor(Color.TRANSPARENT,PorterDuff.Mode.CLEAR);canvas.drawBitmap(historyBase==null?initial:historyBase,0,0,null);
        for(int i=0;i<historyPosition;i++)apply(history.get(i),mask);
        maskChanged();
    }
    private void undo(){cancelInteraction();if(historyPosition>0){historyPosition--;replay();}invalidate();}
    private void redo(){cancelInteraction();if(historyPosition<history.size()){historyPosition++;replay();}invalidate();}
    private void maskChanged(){outlineEpoch++;maskRevision++;requestOutline();if(historyChanged!=null)historyChanged.run();invalidate();}
    private void setOutlineVisible(boolean visible){cancelInteraction();outlineVisible=visible;if(visible)requestOutline();invalidate();}
    private void requestOutline(){
        if(disposed||!outlineVisible||outlineBusy||outlineRevision==maskRevision)return;
        // One in-flight reduced preview; never queue one full image per MOVE.
        long now=android.os.SystemClock.uptimeMillis(),wait=80-(now-outlineLastRequest);
        ui.removeCallbacks(outlineRefresh);
        if(strokeActive&&wait>0){ui.postDelayed(outlineRefresh,wait);return;}
        final Bitmap snapshot;
        try{
            float ratio=Math.min(1,(strokeActive?256f:512f)/Math.max(mask.getWidth(),mask.getHeight()));
            snapshot=Bitmap.createBitmap(Math.max(1,Math.round(mask.getWidth()*ratio)),Math.max(1,Math.round(mask.getHeight()*ratio)),Bitmap.Config.ARGB_8888);
            Canvas preview=new Canvas(snapshot);preview.scale((float)snapshot.getWidth()/mask.getWidth(),(float)snapshot.getHeight()/mask.getHeight());preview.drawBitmap(mask,0,0,paint);
            if(strokeActive)preview.drawPath(stroke,brush);
        }catch(OutOfMemoryError ignored){return;} // Editing and Save remain available without decoration.
        final long revision=maskRevision,epoch=outlineEpoch;outlineBusy=true;outlineLastRequest=now;
        final int imageWidth=snapshot.getWidth(),imageHeight=snapshot.getHeight(),edge=Math.max(1,Math.round(5*Math.max(imageWidth,imageHeight)/Math.min(512f,Math.max(mask.getWidth(),mask.getHeight()))));
        outlineWorker.execute(()->{
            Bitmap result=null;
            try{result=FoodStickerRenderer.createOutline(snapshot,edge,outlineStyle,night);}catch(RuntimeException|OutOfMemoryError ignored){}finally{snapshot.recycle();}
            final Bitmap ready=result;
            ui.post(()->{
                outlineBusy=false;
                if(disposed){if(ready!=null)ready.recycle();return;}
                if(epoch==outlineEpoch&&(revision==maskRevision||strokeActive)){
                    if(outline!=null)outline.recycle();outline=ready;outlineRevision=revision;outlineAppliedEpoch=epoch;outlineImageWidth=imageWidth;outlineImageHeight=imageHeight;invalidate();
                }else if(ready!=null)ready.recycle();
                if(revision!=maskRevision)requestOutline();
            });
        });
    }
    private void resetMask(){cancelInteraction();if(!mask.sameAs(initial))commit(new Edit());viewport.reset();updateViewport();invalidate();}
    private void release(){if(disposed)return;cancelInteraction();disposed=true;ui.removeCallbacks(outlineRefresh);outlineWorker.shutdown();historyChanged=null;history.clear();if(outline!=null){outline.recycle();outline=null;}if(historyBase!=null)historyBase.recycle();source.recycle();mask.recycle();initial.recycle();}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);cancelInteraction();updateViewport();previewBounds.set(0,0,w,h);previewClip.reset();float radius=PaperComponents.dp(getContext(),20);previewClip.addRoundRect(previewBounds,radius,radius,Path.Direction.CW);requestOutline();}
    @Override protected void onDetachedFromWindow(){cancelInteraction();super.onDetachedFromWindow();}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(visibility!=VISIBLE&&viewport!=null)cancelInteraction();}
    @Override public boolean performClick(){super.performClick();return true;}

    private static android.graphics.drawable.Drawable toolBackground(Context c,boolean night,boolean filled){
        GradientDrawable shape=new GradientDrawable();shape.setColor(filled?UiTokens.line(night):UiTokens.paper(night));shape.setCornerRadius(PaperComponents.dp(c,24));
        return new RippleDrawable(ColorStateList.valueOf((UiTokens.ink(night)&0x00ffffff)|0x22000000),shape,null);
    }
    private static TextView textButton(Context c,String text,boolean night){
        TextView button=PaperComponents.text(c,text,16,UiTokens.ink(night),false);button.setGravity(Gravity.CENTER);button.setMinHeight(PaperComponents.dp(c,48));button.setPadding(PaperComponents.dp(c,12),0,PaperComponents.dp(c,12),0);button.setBackground(toolBackground(c,night,false));return button;
    }
    /** Fixed vector strokes avoid font-dependent undo/redo/eye glyphs. */
    private static final class ToolIcon extends View{
        final int kind;final boolean night;boolean visible=true;
        final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);final Path path=new Path();
        ToolIcon(Context c,int kind,boolean night){super(c);this.kind=kind;this.night=night;setBackground(toolBackground(c,night,false));setFocusable(true);}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);int save=canvas.save();canvas.translate(getWidth()*.5f,getHeight()*.5f);float scale=PaperComponents.dp(getContext(),24)/24f;canvas.scale(scale,scale);
            ink.setColor(UiTokens.ink(night));ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(1.8f);ink.setStrokeCap(Paint.Cap.ROUND);ink.setStrokeJoin(Paint.Join.ROUND);path.reset();
            if(kind<2){
                if(kind==1)canvas.scale(-1,1);
                path.moveTo(-8,-1);path.lineTo(-3,-6);path.moveTo(-8,-1);path.lineTo(-3,4);path.moveTo(-8,-1);path.lineTo(1,-1);path.cubicTo(8,-1,9,3,8,7);canvas.drawPath(path,ink);
            }else{
                path.moveTo(-10,0);path.cubicTo(-5,-8,5,-8,10,0);path.cubicTo(5,8,-5,8,-10,0);canvas.drawPath(path,ink);
                if(visible)canvas.drawCircle(0,0,2.6f,ink);else{ink.setStrokeWidth(4.8f);ink.setColor(UiTokens.paper(night));canvas.drawLine(-8,-8,8,8,ink);ink.setStrokeWidth(1.8f);ink.setColor(UiTokens.ink(night));canvas.drawLine(-8,-8,8,8,ink);}
            }
            canvas.restoreToCount(save);
        }
    }
    /** Give short windows to the controls first, shrinking only the preview. */
    private static final class EditorPanel extends LinearLayout{
        View preview;TextView hint,actionText;View tools;
        EditorPanel(Context c){super(c);setOrientation(VERTICAL);}
        @Override protected void onMeasure(int width,int height){
            if(preview!=null){
                int used=getPaddingTop()+getPaddingBottom();
                for(int i=0;i<getChildCount();i++){View child=getChildAt(i);if(child==preview)continue;measureChildWithMargins(child,width,0,MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED),0);LayoutParams p=(LayoutParams)child.getLayoutParams();used+=child.getMeasuredHeight()+p.topMargin+p.bottomMargin;}
                if(hint!=null&&actionText!=null&&hint.getLayout()!=null){
                    Rect ink=new Rect();String text=hint.getText().toString();hint.getPaint().getTextBounds(text,0,text.length(),ink);
                    int last=hint.getLayout().getLineCount()-1;
                    int hintBottom=hint.getBaseline()+hint.getLayout().getLineBaseline(last)-hint.getLayout().getLineBaseline(0)+ink.bottom;
                    text=actionText.getText().toString();actionText.getPaint().getTextBounds(text,0,text.length(),ink);
                    LayoutParams p=(LayoutParams)tools.getLayoutParams();int old=p.bottomMargin;
                    // Match the visible text gaps, not just the 48dp touch rectangles.
                    p.bottomMargin=Math.max(0,p.topMargin+hint.getMeasuredHeight()-hintBottom-actionText.getBaseline()-ink.top);used+=p.bottomMargin-old;
                }
                int available=MeasureSpec.getMode(height)==MeasureSpec.UNSPECIFIED?getResources().getDisplayMetrics().heightPixels-PaperComponents.dp(getContext(),160):MeasureSpec.getSize(height);
                preview.getLayoutParams().height=Math.max(PaperComponents.dp(getContext(),48),Math.min(PaperComponents.dp(getContext(),360),available-used));
            }
            super.onMeasure(width,height);
        }
    }
    public static void show(Activity a,File original,File output,JSONArray bounds,boolean night,Saved saved){
        Bitmap image=null,cutout=null;CutoutEditor owned=null;
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
            final CutoutEditor editor=new CutoutEditor(a,image,cutout,night);owned=editor;cutout.recycle();
            int side=PaperComponents.dp(a,16),gap=PaperComponents.dp(a,12),small=PaperComponents.dp(a,8),control=PaperComponents.dp(a,48);
            LinearLayout title=new LinearLayout(a);title.setGravity(Gravity.CENTER_VERTICAL);title.setPadding(side,small,side,small);
            TextView heading=PaperComponents.text(a,"修整食物贴纸",20,UiTokens.ink(night),false);title.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
            ToolIcon eye=new ToolIcon(a,2,night);eye.setTag("cutout_editor_outline");eye.setContentDescription("隐藏贴纸轮廓");eye.setTooltipText("显示或隐藏贴纸轮廓");title.addView(eye,new LinearLayout.LayoutParams(control,control));
            eye.setOnClickListener(v->{editor.setOutlineVisible(!editor.outlineVisible);eye.visible=editor.outlineVisible;eye.setContentDescription(eye.visible?"隐藏贴纸轮廓":"显示贴纸轮廓");eye.invalidate();});
            EditorPanel box=new EditorPanel(a);box.setPadding(side,0,side,small);box.preview=editor;box.addView(editor,new LinearLayout.LayoutParams(-1,PaperComponents.dp(a,360)));
            TextView hint=PaperComponents.text(a,"单指擦除或恢复；双指缩放、平移，画笔粗细不变。",13,UiTokens.secondary(night),false);hint.setTag("cutout_editor_hint");LinearLayout.LayoutParams hintLp=new LinearLayout.LayoutParams(-1,-2);hintLp.topMargin=gap;box.addView(hint,hintLp);
            LinearLayout tools=new LinearLayout(a);tools.setTag("cutout_editor_tools");tools.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams toolLp=new LinearLayout.LayoutParams(-1,control);toolLp.topMargin=gap;toolLp.bottomMargin=gap;box.addView(tools,toolLp);
            TextView mode=textButton(a,"擦除",night);mode.setTag("cutout_editor_mode");mode.setBackground(toolBackground(a,night,true));mode.setContentDescription("当前擦除，点击切换为恢复");tools.addView(mode,new LinearLayout.LayoutParams(0,-1,1));
            mode.setOnClickListener(v->{editor.setRestore(!editor.restore);mode.setText(editor.restore?"恢复":"擦除");mode.setContentDescription(editor.restore?"当前恢复，点击切换为擦除":"当前擦除，点击切换为恢复");mode.setSelected(editor.restore);});
            ToolIcon undo=new ToolIcon(a,0,night),redo=new ToolIcon(a,1,night);
            undo.setTag("cutout_editor_undo");redo.setTag("cutout_editor_redo");undo.setContentDescription("撤销上一步");redo.setContentDescription("重做上一步");undo.setTooltipText("撤销上一步");redo.setTooltipText("重做上一步");
            for(ToolIcon icon:new ToolIcon[]{undo,redo}){LinearLayout.LayoutParams iconLp=new LinearLayout.LayoutParams(control,control);iconLp.leftMargin=small;tools.addView(icon,iconLp);}
            undo.setOnClickListener(v->editor.undo());redo.setOnClickListener(v->editor.redo());
            editor.historyChanged=()->{undo.setEnabled(editor.historyPosition>0);redo.setEnabled(editor.historyPosition<editor.history.size());undo.setAlpha(undo.isEnabled()?1:.3f);redo.setAlpha(redo.isEnabled()?1:.3f);};editor.historyChanged.run();
            LinearLayout actions=new LinearLayout(a);actions.setTag("cutout_editor_actions");actions.setGravity(Gravity.CENTER_VERTICAL);box.addView(actions,new LinearLayout.LayoutParams(-1,control));
            TextView reset=textButton(a,"重置",night),cancel=textButton(a,"取消",night),save=textButton(a,"保存",night);reset.setBackground(null);cancel.setBackground(null);save.setBackground(null);
            box.hint=hint;box.actionText=reset;box.tools=tools;
            actions.addView(reset,new LinearLayout.LayoutParams(-2,-1));Space spacer=new Space(a);actions.addView(spacer,new LinearLayout.LayoutParams(0,1,1));actions.addView(cancel,new LinearLayout.LayoutParams(-2,-1));actions.addView(save,new LinearLayout.LayoutParams(-2,-1));
            AlertDialog dialog=PaperComponents.dialogBuilder(a,night).setCustomTitle(title).create();dialog.setView(box,0,0,0,0);
            reset.setOnClickListener(v->editor.resetMask());cancel.setOnClickListener(v->dialog.dismiss());
            save.setOnClickListener(v->{
                    editor.cancelInteraction();
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
            dialog.setOnDismissListener(d->editor.release());dialog.show();PaperComponents.styleDialog(dialog,night);
        }catch(Exception|OutOfMemoryError e){if(owned!=null)owned.release();if(image!=null&&!image.isRecycled())image.recycle();if(cutout!=null&&!cutout.isRecycled())cutout.recycle();Toast.makeText(a,"图片无法编辑，请先重新抠图",Toast.LENGTH_LONG).show();}
    }
}
