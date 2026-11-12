package com.eatrecord.app;

import android.animation.*;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import java.util.ArrayList;
import java.util.function.Supplier;

/** One short-lived visual session. Navigation/render is committed once by the caller. */
final class SettingsContainerTransform {
    // Opt-in geometric evidence only; no user text, snapshots or persistent app data.
    private static void trace(String message){if(android.util.Log.isLoggable("MealContainer",android.util.Log.DEBUG))android.util.Log.i("MealContainer",message);}
    private static final NavigationTransitionHistory.Entry MEAL_ENTRY=
            new NavigationTransitionHistory.Entry("preferences","meal-management","meal_management_entry");
    private final ViewGroup host;
    private Session current;
    private int selectedVariant=UiTokens.SETTINGS_CONTAINER_EXPERIMENT_DEFAULT;
    SettingsContainerTransform(ViewGroup host){
        this.host=host;
        host.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(current!=null&&(current.width!=r-l||current.height!=b-t))clear();});
        host.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View v){}public void onViewDetachedFromWindow(View v){clear();}});
    }
    boolean active(){return current!=null;}
    boolean blocksInput(){return active();}
    void setVariant(int value){
        int next=SettingsContainerMath.variant(value);
        if(selectedVariant==next)return;
        clear();selectedVariant=next;
    }
    void run(Runnable render,boolean opening,View source,int color,Drawable paper){
        runInternal(render,opening,source,color,paper,MEAL_ENTRY.key(),()->MEAL_ENTRY,false);
    }
    void runNavigation(Runnable render,boolean opening,View source,int color,Drawable paper,
            String requestedKey,Supplier<NavigationTransitionHistory.Entry> acceptedEntry){
        runInternal(render,opening,source,color,paper,requestedKey,acceptedEntry,true);
    }
    private void runInternal(Runnable render,boolean opening,View source,int color,Drawable paper,
            String requestedKey,Supplier<NavigationTransitionHistory.Entry> acceptedEntry,boolean navigation){
        trace("begin opening="+opening+" enabled="+ValueAnimator.areAnimatorsEnabled()+" size="+host.getWidth()+"x"+host.getHeight()+" attached="+host.isAttachedToWindow());
        Session previous=current;
        if(previous!=null&&(!previous.ready||previous.entry==null
                ||!previous.entry.key().equals(requestedKey))){clear();render.run();return;}
        String reusedIdentity=previous==null?null:previous.entry.identity();
        int variant=navigation?SettingsContainerMath.FULL_A:selectedVariant;
        Bitmap preparationFrame=null;
        if(previous!=null&&previous.ready&&variant==SettingsContainerMath.FULL_A&&ValueAnimator.areAnimatorsEnabled()){
            preparationFrame=capturePreparation(previous);
            if(preparationFrame==null){clear();render.run();return;}
        }
        float expansion=previous!=null&&previous.ready?previous.expansion:(opening?0:1);
        RectF from=previous!=null&&previous.ready?new RectF(previous.bounds):null;
        RectF fromSource=previous!=null&&!previous.sourceDestination.isEmpty()?new RectF(previous.sourceDestination):null;
        float fromRadius=previous!=null&&previous.ready?previous.currentRadius:-1;
        Bitmap page=null;
        PageContent content=previous==null?null:previous.content;
        // A reversal reuses the full-size page texture, never screenshots hidden live content.
        if(previous!=null&&previous.ready){page=previous.page;previous.page=null;}
        clear();
        if(!ValueAnimator.areAnimatorsEnabled()||!host.isAttachedToWindow()||host.getWidth()<=0||host.getHeight()<=0
                ||(long)host.getWidth()*host.getHeight()*8>32L*1024*1024){recycle(page);recycle(preparationFrame);render.run();return;}
        Session session=new Session(opening,color,paper,expansion,from,fromRadius,fromSource,variant);session.navigation=navigation;current=session;session.page=page;session.content=content;session.preparationFrame=preparationFrame;
        try{
            if(opening){
                if(!session.anchor(source)){trace("fallback: invalid opening anchor");clear();render.run();return;}
                session.source=measureSource(source,session);session.parentHeader=measureHeader();session.row=captureView(source);session.parent=captureHost(source,session);
            }else if(session.page==null){session.content=measurePageContent(session.navigation);session.page=captureHost(null,session);}
            // The existing route owner renders exactly once, even on allocation/anchor failure.
        }catch(OutOfMemoryError|RuntimeException unavailable){trace("capture fallback: "+unavailable.getClass().getSimpleName());clear();render.run();return;}
        try{render.run();}catch(RuntimeException failure){clear();throw failure;}
        if(current!=session)return;
        try{
            session.entry=acceptedEntry==null?null:acceptedEntry.get();
            if(session.entry==null||(reusedIdentity!=null&&!reusedIdentity.equals(session.entry.identity()))){clear();return;}
        }catch(RuntimeException unavailable){clear();return;}
        session.hideRealChildren();
        session.pending=new ViewTreeObserver.OnPreDrawListener(){public boolean onPreDraw(){
            if(current!=session){session.removePending();return true;}
            if(!session.sameChildren()||!ValueAnimator.areAnimatorsEnabled()){trace("fallback: children replaced or animation disabled");clear();return true;}
            for(ViewState state:session.views)if(state.view.getWidth()<=0||state.view.getHeight()<=0){clear();return true;}
            session.removePending();
            try{
                if(session.variant==SettingsContainerMath.FULL_A){
                    if(session.opening?(session.parent==null||session.row==null||session.source==null):session.page==null){clear();return true;}
                    session.layer=new Layer(session);session.layer.layout(0,0,session.width,session.height);
                    host.getOverlay().add(session.layer);
                    session.showRealChildren(true);
                }else session.captureIncoming();
            }catch(OutOfMemoryError|RuntimeException unavailable){trace("layout fallback: "+unavailable.getClass().getSimpleName());clear();}
            return true;
        }};
        host.getViewTreeObserver().addOnPreDrawListener(session.pending);host.invalidate();
    }
    void clear(){Session previous=current;current=null;if(previous!=null)previous.release();}
    private static void recycle(Bitmap bitmap){if(bitmap!=null&&!bitmap.isRecycled())bitmap.recycle();}
    private Bitmap capturePreparation(Session previous){
        long bytes=(long)previous.width*previous.height*4;
        if(previous.parent!=null)bytes+=previous.parent.getByteCount();
        if(previous.row!=null)bytes+=previous.row.getByteCount();
        if(previous.page!=null)bytes+=previous.page.getByteCount();
        if(previous.layer==null||previous.width!=host.getWidth()||previous.height!=host.getHeight()
                ||bytes>32L*1024*1024)return null;
        Bitmap frame=null,pixel=null;
        try{
            frame=Bitmap.createBitmap(previous.width,previous.height,Bitmap.Config.ARGB_8888);
            pixel=Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888);pixel.setPixel(0,0,previous.color);
            Canvas canvas=new Canvas(frame);canvas.drawBitmap(pixel,null,previous.end,null);
            previous.layer.draw(canvas);return frame;
        }catch(OutOfMemoryError|RuntimeException unavailable){recycle(frame);return null;}
        finally{recycle(pixel);}
    }
    private static final class CaptureAppearance {
        final ArrayList<View> changed=new ArrayList<>();
        final ArrayList<PaperBackground> papers=new ArrayList<>();
        void disable(View view){
            if(view.getBackground() instanceof PaperComponents.Paper){
                PaperBackground paper=new PaperBackground(view);papers.add(paper);paper.hide();
            }
            if(android.os.Build.VERSION.SDK_INT>=29&&view.isForceDarkAllowed()){
                changed.add(view);view.setForceDarkAllowed(false);
            }
            if(view instanceof ViewGroup){
                ViewGroup group=(ViewGroup)view;
                for(int i=0;i<group.getChildCount();i++)disable(group.getChildAt(i));
            }
        }
        void restore(){
            for(PaperBackground paper:papers)paper.restore();papers.clear();
            for(View view:changed)view.setForceDarkAllowed(true);changed.clear();
        }
    }
    private static final class PaperBackground {
        final View view;final Drawable background;final Drawable.Callback callback;
        final int left,top,right,bottom;
        PaperBackground(View view){
            this.view=view;background=view.getBackground();callback=background.getCallback();
            left=view.getPaddingLeft();top=view.getPaddingTop();right=view.getPaddingRight();bottom=view.getPaddingBottom();
        }
        void hide(){view.setBackground(null);view.setPadding(left,top,right,bottom);}
        void restore(){
            view.setBackground(background);view.setPadding(left,top,right,bottom);background.setCallback(callback);
        }
    }
    private Bitmap captureView(View view){
        Bitmap bitmap=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);
        Drawable background=view.getBackground();int alpha=background==null?255:background.getAlpha();
        CaptureAppearance appearance=new CaptureAppearance();
        try{
            appearance.disable(view);
            // One shared card surface owns the fill/stroke; don't keep a second pill inside it.
            if(background!=null)background.setAlpha(0);
            view.draw(new Canvas(bitmap));return bitmap;
        }catch(RuntimeException|OutOfMemoryError failure){recycle(bitmap);throw failure;}
        finally{if(background!=null)background.setAlpha(alpha);appearance.restore();}
    }
    private Bitmap captureHost(View exclude,Session session){
        Bitmap bitmap=Bitmap.createBitmap(session.width,session.height,Bitmap.Config.ARGB_8888);
        float alpha=exclude==null?1:exclude.getAlpha();
        CaptureAppearance appearance=new CaptureAppearance();
        try{
            appearance.disable(host);
            if(exclude!=null)exclude.setAlpha(0);
            // Both pilot roots are transparent FrameLayouts. Keep only their content:
            // offscreen Canvas dark treatment on some devices rewrites even drawColor.
            // The existing native root Paper remains the sole background owner.
            Canvas c=new Canvas(bitmap);
            for(int i=0;i<host.getChildCount();i++){
                View child=host.getChildAt(i);if(child==exclude)continue;
                int save=c.save();c.translate(child.getLeft(),child.getTop());c.concat(child.getMatrix());
                c.clipRect(0,0,child.getWidth(),child.getHeight());c.translate(-child.getScrollX(),-child.getScrollY());child.draw(c);c.restoreToCount(save);
            }
            return bitmap;
        }catch(RuntimeException|OutOfMemoryError failure){recycle(bitmap);throw failure;}
        finally{if(exclude!=null)exclude.setAlpha(alpha);appearance.restore();}
    }
    private RectF hostBounds(View view){
        Matrix matrix=new Matrix();view.transformMatrixToGlobal(matrix);host.transformMatrixToLocal(matrix);
        RectF rect=new RectF(0,0,view.getWidth(),view.getHeight());matrix.mapRect(rect);return rect;
    }
    private void collectTextBounds(View view,RectF result){
        if(view.getVisibility()!=View.VISIBLE)return;
        if(view instanceof android.widget.TextView&&((android.widget.TextView)view).getText().toString().trim().length()>0){
            RectF rect=hostBounds(view);if(!rect.isEmpty())result.union(rect);
        }else if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)collectTextBounds(group.getChildAt(i),result);}
    }
    private boolean visibleInHost(View view){
        if(view.getVisibility()!=View.VISIBLE||view.getWidth()<=0||view.getHeight()<=0)return false;
        RectF bounds=hostBounds(view);
        return RectF.intersects(bounds,new RectF(0,0,host.getWidth(),host.getHeight()));
    }
    private View findVisibleTag(View view,Object tag){
        if(!visibleInHost(view))return null;
        if(tag.equals(view.getTag()))return view;
        if(view instanceof ViewGroup){
            ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++){
                View found=findVisibleTag(group.getChildAt(i),tag);if(found!=null)return found;
            }
        }
        return null;
    }
    private android.widget.TextView primaryText(View view){
        if(view==null||view.getVisibility()!=View.VISIBLE)return null;
        if(view instanceof android.widget.TextView){
            android.widget.TextView text=(android.widget.TextView)view;
            String value=text.getText().toString().trim();
            boolean meaningful=false;
            for(int i=0;i<value.length();i++)if(Character.isLetterOrDigit(value.charAt(i))){meaningful=true;break;}
            return meaningful?text:null;
        }
        android.widget.TextView best=null;
        if(view instanceof ViewGroup){
            ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++){
                android.widget.TextView candidate=primaryText(group.getChildAt(i));
                if(candidate!=null&&(best==null||candidate.getTextSize()>best.getTextSize()))best=candidate;
            }
        }
        return best;
    }
    private android.widget.TextView markedTitle(View view){
        if(!visibleInHost(view))return null;
        if(view instanceof android.widget.TextView&&Boolean.TRUE.equals(view.getTag(R.id.navigation_header_title)))
            return (android.widget.TextView)view;
        if(view instanceof ViewGroup){
            ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++){
                android.widget.TextView found=markedTitle(group.getChildAt(i));if(found!=null)return found;
            }
        }
        return null;
    }
    private HeaderBounds measureHeader(){
        View header=findVisibleTag(host,"sticky_paper_header");
        android.widget.TextView title=markedTitle(host);
        if(title==null)title=primaryText(header);
        if(title==null)return null;
        RectF ink=new RectF();
        if(header!=null)collectTextBounds(header,ink);
        else ink.set(hostBounds(title));
        return ink.isEmpty()?null:new HeaderBounds(header==null?new RectF(ink):hostBounds(header),ink,baseline(title));
    }
    private void collectVisibleText(View view,HeaderBounds header,ArrayList<RectF> lines,ArrayList<RectF> complete){
        if(!visibleInHost(view))return;
        RectF frame=hostBounds(view);
        if(RectF.intersects(frame,header.region)&&frame.bottom<=header.region.bottom)return;
        if(view instanceof android.widget.TextView){
            android.widget.TextView text=(android.widget.TextView)view;
            boolean empty=text.getText().toString().trim().isEmpty();
            if(empty&&(text.getHint()==null||text.getHint().toString().trim().isEmpty()))return;
            RectF viewport=new RectF(0,header.region.bottom,host.getWidth(),host.getHeight());
            if(viewport.contains(frame))complete.add(new RectF(frame));
            // TextView keeps a separate internal layout for hints. Reveal its
            // measured field together instead of clipping an empty main layout.
            if(empty){lines.add(new RectF(frame));return;}
            android.text.Layout layout=text.getLayout();
            if(layout==null)return;
            Matrix matrix=new Matrix();text.transformMatrixToGlobal(matrix);host.transformMatrixToLocal(matrix);
            Matrix inverse=new Matrix();RectF localViewport=new RectF(viewport);
            int first=0,last=layout.getLineCount()-1;
            if(matrix.invert(inverse)){
                inverse.mapRect(localViewport);
                first=layout.getLineForVertical(Math.max(0,Math.round(localViewport.top+text.getScrollY()-text.getTotalPaddingTop())));
                last=layout.getLineForVertical(Math.max(0,Math.round(localViewport.bottom+text.getScrollY()-text.getTotalPaddingTop())));
            }
            for(int i=first;i<=last;i++){
                RectF line=new RectF(layout.getLineLeft(i)+text.getCompoundPaddingLeft(),
                        layout.getLineTop(i)+text.getTotalPaddingTop(),
                        layout.getLineRight(i)+text.getCompoundPaddingLeft(),
                        layout.getLineBottom(i)+text.getTotalPaddingTop());
                line.offset(-text.getScrollX(),-text.getScrollY());matrix.mapRect(line);
                if(!line.isEmpty()&&viewport.contains(line))lines.add(line);
            }
            return;
        }
        if(view instanceof ViewGroup){
            ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++)collectVisibleText(group.getChildAt(i),header,lines,complete);
        }
    }
    private PageContent measurePageContent(boolean navigation){
        HeaderBounds header=measureHeader();if(header==null)return null;
        ArrayList<RectF> lines=new ArrayList<>(),complete=new ArrayList<>();
        collectVisibleText(host,header,lines,complete);
        RectF body=new RectF();
        if(navigation){
            for(RectF band:complete)if(body.isEmpty()||band.top<body.top)body.set(band);
            if(body.isEmpty())for(RectF line:lines)if(body.isEmpty()||line.top<body.top)body.set(line);
        }else for(RectF band:complete)body.union(band);
        if(body.isEmpty()){
            float top=Math.min(host.getHeight()-1,Math.max(header.region.bottom,header.ink.bottom));
            body.set(host.getWidth()*.5f,top,host.getWidth()*.5f+1,top+1);
        }
        return new PageContent(header,body,header.titleBaseline,navigation?lines:new ArrayList<RectF>());
    }
    private float[] baseline(android.widget.TextView view){
        float[] point={view.getCompoundPaddingLeft(),view.getBaseline()};
        Matrix matrix=new Matrix();
        view.transformMatrixToGlobal(matrix);
        host.transformMatrixToLocal(matrix);
        matrix.mapPoints(point);
        return point;
    }
    private SourceContent measureSource(View row,Session session){
        android.widget.TextView title=primaryText(row);
        if(title==null)return null;
        RectF crop=hostBounds(title);
        crop.offset(-session.start.left,-session.start.top);
        RectF ink=new RectF();
        if(row instanceof ViewGroup){
            ViewGroup group=(ViewGroup)row;
            for(int i=0;i<group.getChildCount();i++)ink.union(hostBounds(group.getChildAt(i)));
        }
        if(ink.isEmpty())ink.set(session.start);
        ink.offset(-session.start.left,-session.start.top);
        float[] point=baseline(title);
        point[0]-=session.start.left;
        point[1]-=session.start.top;
        GradientDrawable outline=null;
        Drawable background=row.getBackground();
        if(background!=null&&background.getConstantState()!=null){
            // Runtime card dimensions are already pixels. A Resources clone would
            // scale its stroke by the display density a second time.
            Drawable copy=background.getConstantState().newDrawable().mutate();
            if(copy instanceof GradientDrawable){
                outline=(GradientDrawable)copy;
                outline.setColor(Color.TRANSPARENT);
            }
        }
        return new SourceContent(crop,ink,point,outline);
    }
    private static final class HeaderBounds {
        final RectF region,ink;final float[] titleBaseline;
        HeaderBounds(RectF region,RectF ink,float[] titleBaseline){this.region=region;this.ink=ink;this.titleBaseline=titleBaseline;}
    }
    private static final class PageContent {
        final HeaderBounds header;final RectF body;final float[] titleBaseline;final ArrayList<RectF> lines;
        PageContent(HeaderBounds header,RectF body,float[] titleBaseline,ArrayList<RectF> lines){
            this.header=header;this.body=body;this.titleBaseline=titleBaseline;this.lines=lines;
        }
    }
    private static final class SourceContent {
        final RectF title,ink;final float[] baseline;final GradientDrawable outline;
        SourceContent(RectF title,RectF ink,float[] baseline,GradientDrawable outline){this.title=title;this.ink=ink;this.baseline=baseline;this.outline=outline;}
    }
    /** Record the original Paper once; don't issue thousands of dot commands every frame. */
    private static final class Api29Paper {
        final RenderNode node=new RenderNode("MealContainerPaper");
        Api29Paper(Drawable paper,int width,int height,int left,int top){
            node.setPosition(0,0,width,height);node.setForceDarkAllowed(false);
            Canvas canvas=node.beginRecording(width,height);
            try{canvas.translate(-left,-top);paper.draw(canvas);}finally{node.endRecording();}
        }
        void draw(Canvas canvas){canvas.drawRenderNode(node);}
        void release(){node.discardDisplayList();}
    }
    private static final class ViewState {
        final View view;final float alpha;final int accessibility,focus;
        ViewState(View view){this.view=view;alpha=view.getAlpha();accessibility=view.getImportantForAccessibility();focus=view instanceof ViewGroup?((ViewGroup)view).getDescendantFocusability():0;view.clearFocus();view.setAlpha(0);view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);if(view instanceof ViewGroup)((ViewGroup)view).setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);}
        void restore(){view.setAlpha(alpha);view.setImportantForAccessibility(accessibility);if(view instanceof ViewGroup)((ViewGroup)view).setDescendantFocusability(focus);}
    }
    private final class Session {
        final boolean opening;final int width=host.getWidth(),height=host.getHeight(),color,variant;final Drawable paper;
        final RectF start=new RectF(),end=new RectF(0,0,width,height),bounds=new RectF();final RectF from;
        final RectF localEnd=new RectF();
        final RectF fromSource,sourceDestination=new RectF();
        final float initial,fromRadius;float expansion,radius,currentRadius;int cardColor;boolean ready;
        Bitmap parent,row,page,preparationFrame;Layer layer;ValueAnimator animator;ViewTreeObserver.OnPreDrawListener pending;Runnable beginMotion,captureAfterDraw;
        HeaderBounds parentHeader;PageContent content;SourceContent source;
        final ArrayList<ViewState> views=new ArrayList<>();
        NavigationTransitionHistory.Entry entry;
        boolean navigation;
        Session(boolean opening,int color,Drawable paper,float expansion,RectF from,float fromRadius,RectF fromSource,int variant){this.opening=opening;this.color=color;this.paper=paper;this.expansion=expansion;initial=expansion;this.from=from;this.fromRadius=fromRadius;this.fromSource=fromSource;this.variant=variant;cardColor=color;}
        boolean anchor(View row){
            if(row==null||row.getWidth()<=0||row.getHeight()<=0||!row.isAttachedToWindow())return false;
            ViewParent parent=row.getParent();while(parent!=host&&parent instanceof View)parent=parent.getParent();if(parent!=host)return false;
            Matrix matrix=new Matrix();row.transformMatrixToGlobal(matrix);host.transformMatrixToLocal(matrix);
            start.set(0,0,row.getWidth(),row.getHeight());matrix.mapRect(start);
            Rect visible=new Rect();if(!row.getGlobalVisibleRect(visible))return false;
            trace("anchor="+start+" visible="+visible+" end="+end);
            // A partly hidden row/header is an unsafe source; fall back to the real page at rest.
            if(visible.width()+2<start.width()||visible.height()+2<start.height()||!end.contains(start))return false;
            Drawable bg=row.getBackground();radius=Math.min(start.width(),start.height())*.5f;
            if(bg instanceof GradientDrawable){GradientDrawable d=(GradientDrawable)bg;radius=Math.min(radius,d.getCornerRadius()*Math.abs(row.getScaleY()));if(d.getColor()!=null)cardColor=d.getColor().getColorForState(row.getDrawableState(),d.getColor().getDefaultColor());}
            float density=host.getResources().getDisplayMetrics().density;
            float horizontal=Math.min(8*density,Math.min(start.left,width-start.right));
            float vertical=Math.min(24*density,Math.min(start.top,height-start.bottom));
            localEnd.set(start.left-horizontal,start.top-vertical,start.right+horizontal,start.bottom+vertical);
            return true;
        }
        void hideRealChildren(){for(int i=0;i<host.getChildCount();i++)views.add(new ViewState(host.getChildAt(i)));}
        void showRealChildren(boolean visible){for(ViewState state:views)state.view.setAlpha(visible?state.alpha:0);}
        void captureIncoming(){
            if(current!=this||ready)return;
            captureAfterDraw=null;
            if(!sameChildren()||!host.isAttachedToWindow()||width!=host.getWidth()||height!=host.getHeight()
                    ||!ValueAnimator.areAnimatorsEnabled()){clear();return;}
            try{
                if(opening){if(page==null){content=measurePageContent(navigation);page=captureHost(null,this);}}
                else{
                    View target=findVisibleTag(host,entry.sourceTag);
                    if(!anchor(target)){trace("fallback: invalid returning anchor");clear();return;}
                    source=measureSource(target,this);parentHeader=measureHeader();row=captureView(target);parent=captureHost(target,this);
                }
                if(row==null||page==null||parent==null||content==null||source==null){clear();return;}
                if(variant!=SettingsContainerMath.LOCAL_B&&(revealAt(end,0,content.body)<.999f
                        ||revealAt(end,0,content.header.ink)<.999f)){clear();return;}
                showRealChildren(false);
                recycle(preparationFrame);preparationFrame=null;
                parent.prepareToDraw();row.prepareToDraw();page.prepareToDraw();
                if(layer==null){
                    layer=new Layer(this);layer.layout(0,0,width,height);host.getOverlay().add(layer);
                }
                ready=true;update(expansion);
                animator=ValueAnimator.ofFloat(expansion,opening?1:0);
                animator.setDuration(Math.max(1,Math.round(SettingsContainerMath.duration(variant)*Math.abs((opening?1:0)-expansion))));
                animator.setInterpolator(variant==SettingsContainerMath.LOCAL_B?new android.view.animation.LinearInterpolator():SettingsContainerMath::ease);
                animator.addUpdateListener(a->{if(current==this){if(!sameChildren()||!ValueAnimator.areAnimatorsEnabled())clear();else update((Float)a.getAnimatedValue());}});
                animator.addListener(new AnimatorListenerAdapter(){@Override public void onAnimationEnd(Animator a){if(current==Session.this)clear();}});
            }catch(OutOfMemoryError|RuntimeException unavailable){trace("capture fallback: "+unavailable.getClass().getSimpleName());if(current==this)clear();}
        }
        boolean sameChildren(){if(host.getChildCount()!=views.size())return false;for(ViewState v:views)if(v.view.getParent()!=host)return false;return true;}
        float revealAt(RectF frame,float corner,RectF content){
            return SettingsContainerMath.contentReveal(frame.left,frame.top,frame.right,frame.bottom,corner,
                    content.left,content.top,content.right,content.bottom,host.getResources().getDisplayMetrics().density*8);
        }
        void removePending(){if(pending!=null){if(host.getViewTreeObserver().isAlive())host.getViewTreeObserver().removeOnPreDrawListener(pending);pending=null;}}
        void update(float value){
            expansion=SettingsContainerMath.bounded(value);
            if(variant==SettingsContainerMath.LOCAL_B){
                RectF target=opening?localEnd:start;
                RectF origin=from!=null?from:(opening?start:localEnd);
                float fraction=SettingsContainerMath.localFraction(expansion,initial,opening);
                bounds.set(SettingsContainerMath.lerp(origin.left,target.left,fraction),SettingsContainerMath.lerp(origin.top,target.top,fraction),
                        SettingsContainerMath.lerp(origin.right,target.right,fraction),SettingsContainerMath.lerp(origin.bottom,target.bottom,fraction));
                currentRadius=fromRadius>=0?SettingsContainerMath.lerp(fromRadius,radius,fraction):radius;
                if(layer!=null)layer.invalidate();host.invalidate();return;
            }
            RectF target=opening?end:start;
            RectF origin=from!=null?from:(opening?start:end);
            bounds.set(SettingsContainerMath.lerp(origin.left,target.left,SettingsContainerMath.redirectFraction(expansion,initial,opening,0,variant)),
                    SettingsContainerMath.lerp(origin.top,target.top,SettingsContainerMath.redirectFraction(expansion,initial,opening,1,variant)),
                    SettingsContainerMath.lerp(origin.right,target.right,SettingsContainerMath.redirectFraction(expansion,initial,opening,2,variant)),
                    SettingsContainerMath.lerp(origin.bottom,target.bottom,SettingsContainerMath.redirectFraction(expansion,initial,opening,3,variant)));
            float radiusStart=initial*initial;
            float radiusDistance=(opening?1:0)-radiusStart;
            float radiusFraction=Math.abs(radiusDistance)<.00001f
                    ?SettingsContainerMath.redirectFraction(expansion,initial,opening,0)
                    :SettingsContainerMath.bounded((expansion*expansion-radiusStart)/radiusDistance);
            currentRadius=fromRadius>=0?SettingsContainerMath.lerp(fromRadius,opening?0:radius,radiusFraction):SettingsContainerMath.radius(radius,expansion);
            if(layer!=null)layer.invalidate();host.invalidate();
        }
        void release(){
            if(layer!=null){
                if(beginMotion!=null)layer.removeCallbacks(beginMotion);
                if(captureAfterDraw!=null)layer.removeCallbacks(captureAfterDraw);
            }
            beginMotion=captureAfterDraw=null;
            removePending();if(animator!=null){animator.removeAllUpdateListeners();animator.removeAllListeners();animator.cancel();animator=null;}
            if(layer!=null){host.getOverlay().remove(layer);layer.release();layer=null;}
            for(ViewState state:views)state.restore();views.clear();recycle(parent);recycle(row);recycle(page);recycle(preparationFrame);parent=row=page=preparationFrame=null;
        }
    }
    private final class Layer extends View {
        final Session session;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);final Path path=new Path();final RectF rowDestination=new RectF();
        final RectF initialSourceCanonical=new RectF(),sourceTitleClip=new RectF();
        final Bitmap fillPixel=Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888);
        final Bitmap paperPixel=Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888);
        Api29Paper cachedPaper;
        Layer(Session session){
            super(host.getContext());this.session=session;paperPixel.setPixel(0,0,session.color);
            // ViewOverlay has its own drawing ancestry: preserve already-themed pixels.
            if(android.os.Build.VERSION.SDK_INT>=29)setForceDarkAllowed(false);
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);setFocusable(false);setClickable(false);
            if(session.variant!=SettingsContainerMath.FULL_A&&android.os.Build.VERSION.SDK_INT>=29&&host.isHardwareAccelerated()&&session.paper!=null){
                try{cachedPaper=new Api29Paper(session.paper,session.width,session.height,host.getLeft(),host.getTop());}
                catch(OutOfMemoryError|RuntimeException unavailable){cachedPaper=null;}
            }
        }
        void release(){recycle(fillPixel);recycle(paperPixel);if(cachedPaper!=null){cachedPaper.release();cachedPaper=null;}}
        private void drawParent(Canvas canvas,Session s,float sourceAlpha){
            paint.setAlpha(255);
            if(s.parentHeader==null){canvas.drawBitmap(s.parent,0,0,paint);return;}
            int body=canvas.save();canvas.clipOutRect(s.parentHeader.region);canvas.drawBitmap(s.parent,0,0,paint);canvas.restoreToCount(body);
            // Only the old title yields as the growing edge actually reaches it.
            float alpha=SettingsContainerMath.parentHeaderAlpha(s.bounds.top,s.parentHeader.ink.top,s.parentHeader.ink.bottom)*sourceAlpha;
            if(alpha>0){int header=canvas.save();canvas.clipRect(s.parentHeader.region);paint.setAlpha(Math.round(255*alpha));canvas.drawBitmap(s.parent,0,0,paint);canvas.restoreToCount(header);}
        }
        private float reveal(Session s,RectF content){
            return s.revealAt(s.bounds,s.currentRadius,content);
        }
        private void drawPaper(Canvas canvas,Session s,float alpha){
            if(s.paper==null||alpha<=0)return;
            int saved=alpha<1?canvas.saveLayerAlpha(s.end,Math.round(alpha*255)):canvas.save();
            if(cachedPaper!=null&&canvas.isHardwareAccelerated())cachedPaper.draw(canvas);
            else{canvas.translate(-host.getLeft(),-host.getTop());s.paper.draw(canvas);}
            canvas.restoreToCount(saved);
        }
        private void positionSource(Session s){
            float travel=SettingsContainerMath.sourceTravel(s.expansion);
            float targetLeft=s.content.titleBaseline[0]-s.source.baseline[0];
            float targetTop=s.content.titleBaseline[1]-s.source.baseline[1];
            float left=SettingsContainerMath.lerp(s.start.left,targetLeft,travel);
            float top=SettingsContainerMath.lerp(s.start.top,targetTop,travel);
            float gap=Math.min(left+s.source.ink.left-s.bounds.left,s.bounds.right-left-s.source.ink.right);
            float inset=SettingsContainerMath.cornerInset(s.currentRadius,gap);
            float minimum=s.bounds.top+inset-s.source.ink.top,maximum=s.bounds.bottom-inset-s.source.ink.bottom;
            top=Math.max(minimum,Math.min(maximum,top));
            rowDestination.set(left,top,left+s.start.width(),top+s.start.height());
            if(initialSourceCanonical.isEmpty())initialSourceCanonical.set(rowDestination);
            if(s.fromSource!=null){
                float keep=1-SettingsContainerMath.redirectFraction(s.expansion,s.initial,s.opening,0);
                rowDestination.set(rowDestination.left+(s.fromSource.left-initialSourceCanonical.left)*keep,
                        rowDestination.top+(s.fromSource.top-initialSourceCanonical.top)*keep,
                        rowDestination.right+(s.fromSource.right-initialSourceCanonical.right)*keep,
                        rowDestination.bottom+(s.fromSource.bottom-initialSourceCanonical.bottom)*keep);
            }
            s.sourceDestination.set(rowDestination);
            float sx=rowDestination.width()/s.start.width(),sy=rowDestination.height()/s.start.height();
            sourceTitleClip.set(rowDestination.left+s.source.title.left*sx,rowDestination.top+s.source.title.top*sy,
                    rowDestination.left+s.source.title.right*sx,rowDestination.top+s.source.title.bottom*sy);
        }
        private void drawSource(Canvas canvas,Session s,float label){
            positionSource(s);
            if(label<=0)return;
            float decoration=label*SettingsContainerMath.decorationAlpha(s.expansion);
            if(decoration>0){
                int rest=canvas.save();
                canvas.clipOutRect(sourceTitleClip);
                paint.setAlpha(Math.round(255*decoration));
                canvas.drawBitmap(s.row,null,rowDestination,paint);
                canvas.restoreToCount(rest);
            }
            int title=canvas.save();
            canvas.clipRect(sourceTitleClip);
            paint.setAlpha(Math.round(255*label));
            canvas.drawBitmap(s.row,null,rowDestination,paint);
            canvas.restoreToCount(title);
        }
        private void drawOutline(Canvas canvas,Session s,float alpha){
            if(s.source.outline==null||alpha<=0)return;
            GradientDrawable outline=s.source.outline;
            outline.setBounds(0,0,Math.round(s.bounds.width()),Math.round(s.bounds.height()));
            outline.setCornerRadius(s.currentRadius);
            outline.setAlpha(Math.round(255*alpha));
            int border=canvas.save();
            canvas.translate(s.bounds.left,s.bounds.top);
            outline.draw(canvas);
            canvas.restoreToCount(border);
        }
        private void drawLocal(Canvas canvas,Session s){
            float oldAlpha=SettingsContainerMath.localOldAlpha(s.expansion);
            float targetAlpha=SettingsContainerMath.localTargetAlpha(s.expansion);
            path.reset();path.addRoundRect(s.bounds,s.currentRadius,s.currentRadius,Path.Direction.CW);
            if(oldAlpha>0){
                int old=oldAlpha<1?canvas.saveLayerAlpha(s.end,Math.round(255*oldAlpha)):canvas.save();
                paint.setAlpha(255);canvas.drawBitmap(s.parent,0,0,paint);
                int card=canvas.save();canvas.clipPath(path);
                fillPixel.setPixel(0,0,s.cardColor);canvas.drawBitmap(fillPixel,null,s.bounds,paint);
                drawOutline(canvas,s,1);
                rowDestination.set(s.start);
                if(s.fromSource!=null){
                    float span=(s.opening?1:0)-s.initial;
                    float fraction=Math.abs(span)<.00001f?1:SettingsContainerMath.bounded((s.expansion-s.initial)/span);
                    rowDestination.set(SettingsContainerMath.lerp(s.fromSource.left,s.start.left,fraction),SettingsContainerMath.lerp(s.fromSource.top,s.start.top,fraction),
                            SettingsContainerMath.lerp(s.fromSource.right,s.start.right,fraction),SettingsContainerMath.lerp(s.fromSource.bottom,s.start.bottom,fraction));
                }
                s.sourceDestination.set(rowDestination);paint.setAlpha(255);canvas.drawBitmap(s.row,null,rowDestination,paint);
                canvas.restoreToCount(card);canvas.restoreToCount(old);
            }
            if(targetAlpha>0){
                int target=targetAlpha<1?canvas.saveLayerAlpha(s.end,Math.round(255*targetAlpha)):canvas.save();
                if(s.paper==null){paint.setAlpha(255);canvas.drawBitmap(paperPixel,null,s.end,paint);}
                else drawPaper(canvas,s,1);
                paint.setAlpha(255);canvas.drawBitmap(s.page,0,0,paint);canvas.restoreToCount(target);
            }
        }
        private void drawPreparation(Canvas canvas,Session s){
            paint.setAlpha(255);canvas.drawBitmap(paperPixel,null,s.end,paint);
            if(s.preparationFrame!=null){canvas.drawBitmap(s.preparationFrame,0,0,paint);return;}
            if(!s.opening){canvas.drawBitmap(s.page,0,0,paint);return;}
            canvas.drawBitmap(s.parent,0,0,paint);
            int source=canvas.save();path.reset();path.addRoundRect(s.start,s.radius,s.radius,Path.Direction.CW);canvas.clipPath(path);
            fillPixel.setPixel(0,0,s.cardColor);canvas.drawBitmap(fillPixel,null,s.start,paint);
            if(s.source.outline!=null){
                int border=canvas.save();canvas.translate(s.start.left,s.start.top);
                s.source.outline.setBounds(0,0,Math.round(s.start.width()),Math.round(s.start.height()));
                s.source.outline.setCornerRadius(s.radius);s.source.outline.setAlpha(255);s.source.outline.draw(canvas);canvas.restoreToCount(border);
            }
            canvas.drawBitmap(s.row,null,s.start,paint);canvas.restoreToCount(source);
        }
        @Override protected void onDraw(Canvas canvas){
            Session s=session;if(current!=s)return;
            if(!s.ready){
                drawPreparation(canvas,s);
                if(s.captureAfterDraw==null){s.captureAfterDraw=s::captureIncoming;postOnAnimation(s.captureAfterDraw);}
                return;
            }
            if(s.parent==null)return;
            if(s.beginMotion==null){s.beginMotion=()->{if(current==s&&s.animator!=null)s.animator.start();};postOnAnimation(s.beginMotion);}
            if(s.variant==SettingsContainerMath.LOCAL_B){drawLocal(canvas,s);return;}
            // Real page roots are hidden; the original root Paper remains underneath.
            // Leave it visible instead of recording thousands of duplicate paper dots
            // every frame. A host without that background still gets an opaque fallback.
            if(s.paper==null){paint.setAlpha(255);canvas.drawBitmap(paperPixel,null,s.end,paint);}
            float radius=s.currentRadius;
            float identity=SettingsContainerMath.identity(s.expansion,reveal(s,s.content.header.ink),reveal(s,s.content.body));
            float sourceAlpha=s.variant==SettingsContainerMath.FULL_A?SettingsContainerMath.aSourceAlpha(s.expansion):SettingsContainerMath.rowAlpha(identity);
            float targetAlpha=s.variant==SettingsContainerMath.FULL_A
                    ?SettingsContainerMath.aTargetAlpha(s.expansion,Math.min(reveal(s,s.content.header.ink),reveal(s,s.content.body)))
                    :SettingsContainerMath.targetAlpha(identity);
            path.reset();path.addRoundRect(s.bounds,radius,radius,Path.Direction.CW);
            int outside=canvas.save();canvas.clipOutPath(path);drawParent(canvas,s,sourceAlpha);canvas.restoreToCount(outside);
            int save=canvas.save();canvas.clipPath(path);
            // The edge excludes the static parent. A later reveals only the real root Paper.
            fillPixel.setPixel(0,0,blend(s.cardColor,s.color,s.expansion));
            float fillAlpha=s.variant==SettingsContainerMath.FULL_A&&s.paper!=null
                    ?1-SettingsContainerMath.pageAlpha(s.expansion):1;
            paint.setAlpha(Math.round(255*fillAlpha));canvas.drawBitmap(fillPixel,null,s.bounds,paint);
            // A keeps the real root Paper behind the overlay. Its own opaque
            // theme pixel yields to that same background; replaying Paper here can be
            // transformed again by the device dark renderer and flash gray.
            if(s.variant!=SettingsContainerMath.FULL_A)drawPaper(canvas,s,SettingsContainerMath.pageAlpha(s.expansion));
            drawOutline(canvas,s,sourceAlpha);
            // Header, group, toggle and explanation share one identity handoff at (0,0).
            if(targetAlpha>0){
                int text=canvas.save();
                for(RectF line:s.content.lines)if(reveal(s,line)<.999f)canvas.clipOutRect(line);
                paint.setAlpha(Math.round(255*targetAlpha));canvas.drawBitmap(s.page,0,0,paint);canvas.restoreToCount(text);
            }
            drawSource(canvas,s,sourceAlpha);
            canvas.restoreToCount(save);
        }
    }
    private static int blend(int a,int b,float t){return Color.rgb(Math.round(SettingsContainerMath.lerp(Color.red(a),Color.red(b),t)),Math.round(SettingsContainerMath.lerp(Color.green(a),Color.green(b),t)),Math.round(SettingsContainerMath.lerp(Color.blue(a),Color.blue(b),t)));}
}
