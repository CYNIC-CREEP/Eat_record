package com.eatrecord.app;

/** Read-only notebook navigation in continuous page units, independent of rendering and clocks. */
final class NotebookPageMotion {
    private int count,target,startTarget,source,direction;
    private float position,origin,displacement,overscroll,animationMin,animationMax;
    private boolean touching;
    NotebookPageMotion(int count,int index){reset(count,index);}
    void reset(int count,int index){this.count=Math.max(1,count);target=bound(index);position=target;origin=position;animationMin=animationMax=position;displacement=overscroll=0;direction=0;touching=false;}
    private int bound(int index){return Math.max(0,Math.min(count-1,index));}
    private float bound(float value){return Math.max(0,Math.min(count-1,value));}
    float position(){return position;}
    float overscroll(){return overscroll;}
    int target(){return target;}
    void grab(int visibleSource){source=bound(visibleSource);startTarget=target;origin=position;displacement=overscroll=0;direction=0;touching=true;}
    void drag(float pages){
        if(!touching||!Float.isFinite(pages))return;
        displacement=pages;if(Math.abs(pages)>.001f)direction=pages>0?1:-1;
        float proposed=origin+pages;position=bound(proposed);overscroll=proposed-position;
    }
    int release(float velocity,boolean cancelled){
        if(!touching)return target;
        if(!Float.isFinite(velocity))velocity=0;
        if(cancelled){
            // Continuing in the prior committed direction preserves that destination;
            // reversing and cancelling restores the page that owned this new grab.
            target=direction!=0&&direction*(startTarget-origin)>0?startTarget:source;
        }else if(direction==0)target=startTarget;
        else if(Math.abs(displacement)>.35f||Math.abs(displacement)>.03f&&direction*velocity>.55f){
            boolean opposing=direction*(startTarget-origin)<0;
            int wanted=bound((opposing?source:startTarget)+direction);
            int nearest=bound(Math.round(position));
            target=direction>0?Math.max(wanted,nearest):Math.min(wanted,nearest);
        }else target=direction*(startTarget-origin)<0?bound(Math.round(position)):startTarget;
        touching=false;overscroll=0;animationMin=Math.min(position,target);animationMax=Math.max(position,target);return target;
    }
    void animate(float value){if(!touching&&Float.isFinite(value))position=Math.max(animationMin,Math.min(animationMax,bound(value)));overscroll=0;}
    void select(int index){target=bound(index);touching=false;overscroll=0;animationMin=Math.min(position,target);animationMax=Math.max(position,target);}
    void finish(){position=target;animationMin=animationMax=position;touching=false;overscroll=0;}
}
