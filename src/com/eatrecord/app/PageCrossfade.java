package com.eatrecord.app;

import android.animation.*;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.*;
import java.util.ArrayList;

/** Opaque child-page changes; root navigation belongs to RootPagePager. */
final class PageCrossfade {
    private final ViewGroup host;
    private Slide image;
    private ValueAnimator animator;
    private int paperColor=UiTokens.paper(false);
    private Drawable paperBackground;
    private ViewTreeObserver.OnPreDrawListener pending;
    private final ArrayList<View> incoming=new ArrayList<>();
    private final ArrayList<IncomingSurface> incomingSurfaces=new ArrayList<>();
    PageCrossfade(ViewGroup host){this.host=host;host.addOnLayoutChangeListener((view,l,t,r,b,ol,ot,or,ob)->{
        if(image!=null&&(image.width!=r-l||image.height!=b-t))clear();
    });}
    void run(Runnable render,float direction){run(render,direction,false);}
    void setPaperColor(int color){paperColor=color;}
    void setPaperBackground(Drawable background){paperBackground=background;}
    boolean retains(View view){return image!=null&&image.outgoing.contains(view);}
    void run(Runnable render,float direction,boolean gentle){
        boolean animate=ValueAnimator.areAnimatorsEnabled()&&host.isAttachedToWindow()
                &&host.getWidth()>0&&host.getHeight()>0;
        boolean laidOut=image!=null;
        for(int i=0;i<host.getChildCount();i++)laidOut|=host.getChildAt(i).getWidth()>0&&host.getChildAt(i).getHeight()>0;
        // Routine navigation retains real Views/display lists. Before the first layout,
        // several quick taps can reuse the same captured frame instead of repeatedly
        // copying it (or trying to capture a new logical page that has no size yet).
        boolean interrupting=animate&&laidOut&&image!=null;
        Bitmap interrupted=null;
        if(interrupting){
            if(pending!=null&&image.progress==0&&image.bitmap!=null){interrupted=image.bitmap;image.bitmap=null;}
            else interrupted=capturePresentation();
            // A low-memory/drawing failure must show the new page at rest, never
            // retain an unlaid-out, off-screen incoming page as an opaque surface.
            if(interrupted==null)animate=false;
        }
        // Several direct roots can form one page. Flatten that uncommon case
        // so its layers move together as one complete, opaque presentation.
        if(!interrupting&&animate&&laidOut&&host.getChildCount()>1){
            interrupted=capturePresentation();if(interrupted==null)animate=false;
        }
        clear();host.animate().cancel();host.setAlpha(1);host.setTranslationX(0);host.setTranslationY(0);host.setScaleX(1);host.setScaleY(1);
        if(animate&&laidOut){
            image=new Slide(interrupted,direction>=0,paperColor,paperBackground);
            if(interrupted==null&&host.getChildCount()==1){
                // Retain the real page and give its transparent root a complete
                // paper surface while it travels alongside the arriving page.
                View previous=host.getChildAt(0);IncomingSurface surface=new IncomingSurface(previous,image);
                incomingSurfaces.add(surface);surface.install();image.retain(previous);
            }else{
                PaperLayer backing=new PaperLayer(image);
                host.addView(backing,interrupted==null?0:host.getChildCount(),new ViewGroup.LayoutParams(host.getWidth(),host.getHeight()));
                backing.measure(View.MeasureSpec.makeMeasureSpec(host.getWidth(),View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(host.getHeight(),View.MeasureSpec.EXACTLY));
                backing.layout(0,0,host.getWidth(),host.getHeight());
                if(interrupted==null)for(int i=0;i<host.getChildCount();i++)image.retain(host.getChildAt(i));
                else image.retain(backing);
            }
            // Native disappearing children preserve attachment and display lists while the
            // renderer replaces normal children. Reparenting into an overlay would reset
            // TodayDayPager/HomeCalendar in their onDetachedFromWindow callbacks.
            host.removeAllViews();
        }
        try{render.run();}catch(RuntimeException e){clear();throw e;}
        host.setAlpha(1);host.setTranslationX(0);host.setTranslationY(0);
        if(image==null)return;
        prepareIncoming();
    }
    private void prepareIncoming(){
        try{
            for(int i=0;i<host.getChildCount();i++){
                View child=host.getChildAt(i);child.animate().cancel();child.setAlpha(1);incoming.add(child);
                IncomingSurface surface=new IncomingSurface(child,image);incomingSurfaces.add(surface);surface.install();
            }
        }catch(OutOfMemoryError|RuntimeException unavailable){clear();return;}
        position(0);
        final Slide current=image;
        pending=new ViewTreeObserver.OnPreDrawListener(){public boolean onPreDraw(){
            // Removing a listener cannot revoke an invocation already copied into
            // this frame's dispatch list. It may only operate on its own generation.
            if(pending!=this||image!=current){if(host.getViewTreeObserver().isAlive())host.getViewTreeObserver().removeOnPreDrawListener(this);return true;}
            boolean replaced=incoming.isEmpty()||incoming.size()!=host.getChildCount();
            boolean sized=true;
            for(View child:incoming){replaced|=child.getParent()!=host;sized&=child.getWidth()>0&&child.getHeight()>0;}
            if(!replaced&&!sized){host.requestLayout();host.invalidate();return false;}
            if(host.getViewTreeObserver().isAlive())host.getViewTreeObserver().removeOnPreDrawListener(this);pending=null;
            if(replaced){clear();return true;}
            current.prepareLayers(incoming);
            position(current.progress);
            animator=ValueAnimator.ofFloat(0,1);animator.setDuration(current.durationMs);animator.setInterpolator(new android.view.animation.LinearInterpolator());
            animator.addUpdateListener(a->{if(image==current)position((Float)a.getAnimatedValue());});
            animator.addListener(new AnimatorListenerAdapter(){@Override public void onAnimationEnd(Animator a){if(image==current)clear();}});animator.start();return true;
        }};host.getViewTreeObserver().addOnPreDrawListener(pending);host.invalidate();
    }
    private Bitmap capturePresentation(){
        Bitmap snapshot=null;
        try{
            snapshot=Bitmap.createBitmap(host.getWidth(),host.getHeight(),Bitmap.Config.ARGB_8888);
            Canvas canvas=new Canvas(snapshot);canvas.drawColor(paperColor);
            if(paperBackground!=null){int save=canvas.save();canvas.translate(-host.getLeft(),-host.getTop());paperBackground.draw(canvas);canvas.restoreToCount(save);}
            boolean content=false;
            // ViewGroup.draw's disappearing-child handling differs from its hardware
            // presentation path. Compose the two owned layers explicitly so rapid
            // interruptions cannot capture only the blank backing paper.
            for(int i=0;i<host.getChildCount();i++)content|=drawPresentationView(canvas,host.getChildAt(i));
            if(image!=null)for(View child:image.outgoing)content|=drawPresentationView(canvas,child);
            if(content)return snapshot;
            snapshot.recycle();return null;
        }catch(OutOfMemoryError|RuntimeException ignored){if(snapshot!=null&&!snapshot.isRecycled())snapshot.recycle();return null;}
    }
    private boolean drawPresentationView(Canvas canvas,View child){
        if(child.getVisibility()!=View.VISIBLE||child.getWidth()<=0||child.getHeight()<=0||child.getAlpha()<=0)return false;
        RectF visible=new RectF(0,0,child.getWidth(),child.getHeight());child.getMatrix().mapRect(visible);visible.offset(child.getLeft(),child.getTop());
        if(!RectF.intersects(visible,new RectF(0,0,host.getWidth(),host.getHeight())))return false;
        int save=child.getAlpha()<1?canvas.saveLayerAlpha(0,0,host.getWidth(),host.getHeight(),Math.round(child.getAlpha()*255)):canvas.save();
        canvas.translate(child.getLeft(),child.getTop());canvas.concat(child.getMatrix());
        // Direct child.draw() does not apply ViewGroup.drawChild's local clip.
        // Without it a translated backing can erase the other visible page.
        canvas.clipRect(0,0,child.getWidth(),child.getHeight());canvas.translate(-child.getScrollX(),-child.getScrollY());child.draw(canvas);canvas.restoreToCount(save);
        return !(child instanceof PaperLayer)||((PaperLayer)child).slide.bitmap!=null;
    }
    private void position(float progress){
        if(image==null)return;
        image.progress=progress;
        float direction=image.forward?1:-1;
        float travel=image.travel;
        float movement=image.positionEase.getInterpolation(progress);
        for(int i=0;i<image.outgoing.size();i++){
            View child=image.outgoing.get(i);child.setTranslationX(image.origins.get(i)-direction*travel*movement);
            // Native disappearing children draw above incoming roots. Move the
            // opaque pages by the same full span so their edges meet instead of
            // blending two moving sets of text in the same pixels.
            child.setAlpha(image.alphas.get(i));
        }
        for(View child:incoming){child.setTranslationX(direction*travel*(1-movement));child.setAlpha(1);}
        // A freshly created incoming page starts outside the viewport. Re-record
        // this parent as it enters, even when only RenderNode translations change.
        host.invalidate();
    }
    void clear(){
        removePending();
        if(animator!=null){ValueAnimator previous=animator;animator=null;previous.removeAllUpdateListeners();previous.removeAllListeners();previous.cancel();}
        for(View child:incoming){child.setTranslationX(0);child.setAlpha(1);}incoming.clear();
        for(IncomingSurface surface:incomingSurfaces)surface.restore();incomingSurfaces.clear();
        // A data refresh may have replaced the normal children while a transition
        // owned an earlier list. The currently displayed page must always settle.
        for(int i=0;i<host.getChildCount();i++){View child=host.getChildAt(i);child.setTranslationX(0);child.setAlpha(1);}
        if(image!=null){Slide previous=image;image=null;previous.release();}
    }
    private void removePending(){
        if(pending!=null){if(host.getViewTreeObserver().isAlive())host.getViewTreeObserver().removeOnPreDrawListener(pending);pending=null;}
    }
    boolean active(){return image!=null;}
    private final class Slide {
        Bitmap bitmap;final boolean forward;float progress;final float travel=host.getWidth();
        final int durationMs;
        final TimeInterpolator positionEase;
        final int color,width=host.getWidth(),height=host.getHeight();final Drawable background;
        final ArrayList<View> outgoing=new ArrayList<>();
        final ArrayList<Float> origins=new ArrayList<>(),alphas=new ArrayList<>();
        final ArrayList<View> layerViews=new ArrayList<>();
        boolean layersPrepared;
        Slide(Bitmap bitmap,boolean forward,int color,Drawable background){
            this.bitmap=bitmap;this.forward=forward;this.color=color;this.background=background;
            durationMs=UiTokens.PAGE_FLOW_MS;positionEase=UiTokens.motionEasePage();
        }
        void adopt(View child){
            child.animate().cancel();outgoing.add(child);origins.add(child.getTranslationX());alphas.add(child.getAlpha());
        }
        void retain(View child){adopt(child);host.startViewTransition(child);}
        void prepareLayers(ArrayList<View> arriving){
            if(layersPrepared)return;layersPrepared=true;
            // Settings and root sheets are dense with text, ruled paper and media.
            // Cache each laid-out sheet before the first moving frame; translation reuses
            // that texture instead of redrawing the hierarchy on every frame.
            // Temporary full-screen layers are bounded to 32 MiB. Software rendering,
            // explicit software layers and larger windows keep the normal path.
            if(!host.isHardwareAccelerated()||(long)width*height*8>32L*1024*1024)return;
            for(View child:outgoing)prepareLayer(child);
            for(View child:arriving)prepareLayer(child);
        }
        private void prepareLayer(View child){
            if((long)width*height*4*(layerViews.size()+1)>32L*1024*1024)return;
            if(child instanceof PaperLayer||child.getLayerType()!=View.LAYER_TYPE_NONE
                    ||!child.isAttachedToWindow()||child.getWidth()<=0||child.getHeight()<=0)return;
            try{
                child.setLayerType(View.LAYER_TYPE_HARDWARE,null);layerViews.add(child);
                child.buildLayer();
            }catch(OutOfMemoryError|RuntimeException ignored){
                // Layering is only an optimization; preserve the normal view
                // path if the renderer cannot allocate a temporary texture.
                child.setLayerType(View.LAYER_TYPE_NONE,null);layerViews.remove(child);
            }
        }
        void release(){
            for(int i=0;i<outgoing.size();i++){
                View child=outgoing.get(i);child.setTranslationX(origins.get(i));child.setAlpha(alphas.get(i));
            }
            for(View child:layerViews)child.setLayerType(View.LAYER_TYPE_NONE,null);
            layerViews.clear();
            for(View child:outgoing)host.endViewTransition(child);
            outgoing.clear();origins.clear();alphas.clear();if(bitmap!=null&&!bitmap.isRecycled())bitmap.recycle();
        }
    }
    /** A temporary opaque surface belongs to the existing root View; no reparenting. */
    private final class IncomingSurface {
        final View view;final Drawable original;final Drawable.Callback originalCallback;
        final int left,top,right,bottom;final PaperSurface surface;
        IncomingSurface(View view,Slide slide){
            this.view=view;original=view.getBackground();originalCallback=original==null?null:original.getCallback();
            left=view.getPaddingLeft();top=view.getPaddingTop();right=view.getPaddingRight();bottom=view.getPaddingBottom();
            surface=new PaperSurface(view,slide,original,originalCallback);
        }
        void install(){
            view.setBackground(surface);view.setPadding(left,top,right,bottom);surface.attachOriginal();
        }
        void restore(){
            boolean owns=view.getBackground()==surface;surface.releaseOriginal(owns);
            if(owns){
                int l=view.getPaddingLeft(),t=view.getPaddingTop(),r=view.getPaddingRight(),b=view.getPaddingBottom();
                view.setBackground(original);view.setPadding(l,t,r,b);
                if(original!=null)original.setCallback(originalCallback);
            }
        }
    }
    private final class PaperSurface extends Drawable implements Drawable.Callback {
        final View view;final Slide slide;final Drawable original;final Drawable.Callback originalCallback;
        final Paint paint=new Paint(),layerPaint=new Paint();private int opacity=255;
        private ColorFilter filter;
        PaperSurface(View view,Slide slide,Drawable original,Drawable.Callback originalCallback){
            this.view=view;this.slide=slide;this.original=original;this.originalCallback=originalCallback;
            if(original!=null){setState(original.getState());setLevel(original.getLevel());setLayoutDirection(original.getLayoutDirection());}
        }
        void attachOriginal(){if(original!=null)original.setCallback(this);}
        void releaseOriginal(boolean restore){if(original!=null&&original.getCallback()==this)original.setCallback(restore?originalCallback:null);}
        @Override public void draw(Canvas canvas){
            int saved=canvas.save();canvas.clipRect(getBounds());
            if(opacity!=255||filter!=null){layerPaint.setAlpha(opacity);layerPaint.setColorFilter(filter);canvas.saveLayer(getBounds().left,getBounds().top,getBounds().right,getBounds().bottom,layerPaint);}
            paint.setColor(slide.color|0xff000000);canvas.drawRect(getBounds(),paint);
            if(slide.background!=null){int art=canvas.save();canvas.translate(-host.getLeft()-view.getLeft(),-host.getTop()-view.getTop());slide.background.draw(canvas);canvas.restoreToCount(art);}
            if(original!=null)original.draw(canvas);
            canvas.restoreToCount(saved);
        }
        @Override protected void onBoundsChange(Rect bounds){if(original!=null)original.setBounds(bounds);}
        @Override public boolean getPadding(Rect padding){return original!=null&&original.getPadding(padding);}
        @Override public int getMinimumWidth(){return original==null?0:original.getMinimumWidth();}
        @Override public int getMinimumHeight(){return original==null?0:original.getMinimumHeight();}
        @Override public int getIntrinsicWidth(){return original==null?-1:original.getIntrinsicWidth();}
        @Override public int getIntrinsicHeight(){return original==null?-1:original.getIntrinsicHeight();}
        @Override public boolean isStateful(){return original!=null&&original.isStateful();}
        @Override protected boolean onStateChange(int[] state){return original!=null&&original.setState(state);}
        @Override protected boolean onLevelChange(int level){return original!=null&&original.setLevel(level);}
        @Override public boolean onLayoutDirectionChanged(int direction){return original!=null&&original.setLayoutDirection(direction);}
        @Override public boolean setVisible(boolean visible,boolean restart){boolean changed=super.setVisible(visible,restart);return original==null?changed:original.setVisible(visible,restart)||changed;}
        @Override public void setHotspot(float x,float y){if(original!=null)original.setHotspot(x,y);}
        @Override public void setHotspotBounds(int l,int t,int r,int b){if(original!=null)original.setHotspotBounds(l,t,r,b);}
        @Override public void getOutline(Outline outline){if(original!=null)original.getOutline(outline);else outline.setEmpty();}
        @Override public void setAlpha(int alpha){opacity=Math.max(0,Math.min(255,alpha));invalidateSelf();}
        @Override public int getAlpha(){return opacity;}
        @Override public void setColorFilter(ColorFilter value){filter=value;invalidateSelf();}
        @Override public ColorFilter getColorFilter(){return filter;}
        @Override public int getOpacity(){return opacity==255&&filter==null?PixelFormat.OPAQUE:PixelFormat.TRANSLUCENT;}
        @Override public void invalidateDrawable(Drawable who){invalidateSelf();}
        @Override public void scheduleDrawable(Drawable who,Runnable what,long when){scheduleSelf(what,when);}
        @Override public void unscheduleDrawable(Drawable who,Runnable what){unscheduleSelf(what);}
    }
    private final class PaperLayer extends View {
        final Slide slide;
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        PaperLayer(Slide slide){super(host.getContext());this.slide=slide;setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas canvas){
            int saved=canvas.save();canvas.clipRect(0,0,getWidth(),getHeight());
            paint.setColor(slide.color);canvas.drawRect(0,0,getWidth(),getHeight(),paint);
            if(slide.bitmap!=null){if(!slide.bitmap.isRecycled())canvas.drawBitmap(slide.bitmap,0,0,paint);}
            else if(slide.background!=null){int save=canvas.save();canvas.translate(-host.getLeft(),-host.getTop());slide.background.draw(canvas);canvas.restoreToCount(save);}
            canvas.restoreToCount(saved);
        }
    }
}
