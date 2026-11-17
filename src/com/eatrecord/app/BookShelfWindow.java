package com.eatrecord.app;

import android.content.Context;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;
import java.util.HashMap;
import java.util.Map;

/** A bounded window inside the existing ScrollView, retaining the full logical scroll extent. */
final class BookShelfWindow extends LinearLayout {
    interface Adapter { int count(); int extent(int position); View create(int position); }
    private final Adapter adapter;
    private final boolean horizontal;
    private final int crossExtent;
    private final int[] offsets;
    private final Map<String,Integer> datePositions;
    private final Map<Integer,View> live=new HashMap<Integer,View>();
    private final Rect visible=new Rect();
    private boolean rebuilding,queued;
    private Runnable onWindowChanged;
    private ViewTreeObserver scrollObserver;
    private final Runnable refresh=()->{queued=false;if(isAttachedToWindow())updateWindow();};
    private final ViewTreeObserver.OnScrollChangedListener scrollListener=()->{
        if(!queued){queued=true;postOnAnimation(refresh);}
    };

    BookShelfWindow(Context context,boolean horizontal,int crossExtent,Adapter adapter,Map<String,Integer> datePositions){
        super(context);this.horizontal=horizontal;this.crossExtent=crossExtent;this.adapter=adapter;
        this.datePositions=new HashMap<String,Integer>(datePositions);
        setOrientation(horizontal?HORIZONTAL:VERTICAL);setClipChildren(false);setClipToPadding(false);
        setTag(horizontal?"virtual_book_carousel":"virtual_book_grid");
        offsets=new int[adapter.count()+1];
        for(int i=0;i<adapter.count();i++)offsets[i+1]=(int)Math.min(Integer.MAX_VALUE-1L,(long)offsets[i]+Math.max(1,adapter.extent(i)));
    }
    boolean ensureDate(String date){
        Integer position=datePositions.get(date);if(position==null)return false;
        int first=Math.max(0,position-3),end=Math.min(adapter.count(),position+5);
        if(live.containsKey(position))return true;
        rebuild(first,end);return true;
    }
    int materializedCount(){return live.size();}
    void setOnWindowChanged(Runnable callback){onWindowChanged=callback;}
    private int positionAt(int coordinate){
        int low=0,high=adapter.count();
        while(low<high){int mid=(low+high)>>>1;if(offsets[mid+1]<=coordinate)low=mid+1;else high=mid;}
        return Math.min(Math.max(0,adapter.count()-1),low);
    }
    private void updateWindow(){
        if(rebuilding||adapter.count()==0)return;
        int start=0,span=horizontal?getResources().getDisplayMetrics().widthPixels:getResources().getDisplayMetrics().heightPixels;
        if(getLocalVisibleRect(visible)){
            start=horizontal?visible.left:visible.top;span=horizontal?visible.width():visible.height();
            if(horizontal&&getLayoutDirection()==View.LAYOUT_DIRECTION_RTL)start=offsets[offsets.length-1]-visible.right;
        }else if(!live.isEmpty())return;
        int first=Math.max(0,positionAt(Math.max(0,start))-3);
        int end=Math.min(adapter.count(),positionAt(start+Math.max(1,span))+4);
        rebuild(first,end);
    }
    private void rebuild(int first,int end){
        if(rebuilding)return;rebuilding=true;
        try{
            for(Integer key:new java.util.ArrayList<Integer>(live.keySet()))if(key<first||key>=end){View old=live.remove(key);removeViewInLayout(old);}
            for(int i=first;i<end;i++)if(!live.containsKey(i)){
                View child=adapter.create(i);int insertion=0;
                for(Integer key:live.keySet())if(key<i)insertion++;
                live.put(i,child);
                addViewInLayout(child,insertion,new LinearLayout.LayoutParams(horizontal?adapter.extent(i):-1,horizontal?crossExtent:adapter.extent(i)),true);
            }
            measureAndPlace();if(onWindowChanged!=null)onWindowChanged.run();invalidate();
        }finally{rebuilding=false;}
    }
    private void measureAndPlace(){
        int width=getMeasuredWidth();
        for(Map.Entry<Integer,View> entry:live.entrySet()){
            int position=entry.getKey(),extent=adapter.extent(position);View child=entry.getValue();
            int childWidth=horizontal?extent:Math.max(1,width),childHeight=horizontal?crossExtent:extent;
            child.measure(MeasureSpec.makeMeasureSpec(childWidth,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(childHeight,MeasureSpec.EXACTLY));
            if(horizontal){
                boolean rtl=getLayoutDirection()==View.LAYOUT_DIRECTION_RTL;int total=offsets[offsets.length-1];
                child.layout(rtl?total-offsets[position+1]:offsets[position],0,rtl?total-offsets[position]:offsets[position+1],childHeight);
            }
            else child.layout(0,offsets[position],childWidth,offsets[position+1]);
        }
    }
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        int extent=offsets[offsets.length-1];
        setMeasuredDimension(horizontal?extent:MeasureSpec.getSize(widthSpec),horizontal?crossExtent:extent);
        if(live.isEmpty())updateWindow();else measureAndPlace();
    }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){measureAndPlace();}
    @Override protected void onAttachedToWindow(){
        super.onAttachedToWindow();scrollObserver=getViewTreeObserver();scrollObserver.addOnScrollChangedListener(scrollListener);scrollListener.onScrollChanged();
    }
    @Override public void onVisibilityAggregated(boolean visible){
        super.onVisibilityAggregated(visible);if(visible)scrollListener.onScrollChanged();
    }
    @Override protected void onDetachedFromWindow(){
        removeCallbacks(refresh);queued=false;
        if(scrollObserver!=null&&scrollObserver.isAlive())scrollObserver.removeOnScrollChangedListener(scrollListener);
        scrollObserver=null;super.onDetachedFromWindow();
    }
}
