package com.eatrecord.app;

/** Geometry progress for the meal-settings pilot; no routes or Android state. */
final class SettingsContainerMath {
    static final int BASELINE=0, FULL_A=1, LOCAL_B=2;
    static final int DURATION_MS=480;
    static int variant(int value){return value>=BASELINE&&value<=LOCAL_B?value:BASELINE;}
    static int duration(int variant){return variant==LOCAL_B?320:DURATION_MS;}
    static float bounded(float v){return Float.isFinite(v)?Math.max(0,Math.min(1,v)):0;}
    static float ease(float u){
        double x=bounded(u);if(x==0||x==1)return (float)x;
        double lo=0,hi=1;
        for(int i=0;i<28;i++){double t=(lo+hi)*.5,v=1-t;if(3*v*v*t*.25+3*v*t*t*.25+t*t*t<x)lo=t;else hi=t;}
        double t=(lo+hi)*.5;return (float)(3*(1-t)*t*t+t*t*t);
    }
    static float lerp(float a,float b,float e){return a+(b-a)*bounded(e);}
    static float radius(float start,float e){float x=bounded(e);return Math.max(0,start)*(1-x*x);}
    private static float smooth(float v){float x=bounded(v);return x*x*(3-2*x);}
    static float topProgress(float e){return smooth(bounded(e)/.62f);}
    static float bottomProgress(float e){return topProgress(e);}
    static float edgeProgress(float e,int edge){return edge==1?topProgress(e):edge==3?bottomProgress(e):bounded(e);}
    static float edgeProgress(float e,int edge,int variant){return variant==FULL_A?smooth(bounded(e)/.90f):edgeProgress(e,edge);}
    static float redirectFraction(float e,float initial,boolean opening,int edge){
        return redirectFraction(e,initial,opening,edge,BASELINE);
    }
    static float redirectFraction(float e,float initial,boolean opening,int edge,int variant){
        float start=edgeProgress(initial,edge,variant);
        float target=opening?1:0;
        float distance=target-start;
        if(Math.abs(distance)<.00001f){
            // A saturated edge can still have a newly measured endpoint.
            float span=target-bounded(initial);
            return Math.abs(span)<.00001f?1:bounded((bounded(e)-bounded(initial))/span);
        }
        return bounded((edgeProgress(e,edge,variant)-start)/distance);
    }
    /** Recover the canonical timing coordinate from the existing Bezier movement value. */
    static float clockProgress(float e){
        double y=bounded(e),lo=0,hi=1;
        for(int i=0;i<24;i++){double t=(lo+hi)*.5;if(3*(1-t)*t*t+t*t*t<y)lo=t;else hi=t;}
        double t=(lo+hi)*.5,v=1-t;return (float)(3*v*v*t*.25+3*v*t*t*.25+t*t*t);
    }
    static float aSourceAlpha(float e){return 1-smooth((clockProgress(e)-.24f)/.20f);}
    static float aTargetAlpha(float e,float safeReveal){return bounded((clockProgress(e)-.44f)/.36f)*bounded(safeReveal);}
    static float localProgress(float u){return ease(bounded(u/.75f));}
    static float localFraction(float u,float initial,boolean opening){
        float start=localProgress(initial),distance=(opening?1:0)-start;
        if(Math.abs(distance)<.00001f){float span=(opening?1:0)-bounded(initial);return Math.abs(span)<.00001f?1:bounded((bounded(u)-bounded(initial))/span);}
        return bounded((localProgress(u)-start)/distance);
    }
    static float localOldAlpha(float u){return 1-smooth(bounded((bounded(u)-.75f)/.25f)/.40f);}
    static float localTargetAlpha(float u){return smooth((bounded((bounded(u)-.75f)/.25f)-.40f)/.60f);}
    static float parentAlpha(float e){return 1;}
    static float rowAlpha(float identity){return 1-smooth(bounded(identity)/.40f);}
    static float targetAlpha(float identity){return smooth((bounded(identity)-.40f)/.60f);}
    static float identity(float e,float headerReveal,float bodyReveal){return pageAlpha(e)*Math.min(bounded(headerReveal),bounded(bodyReveal));}
    static float decorationAlpha(float e){return 1-smooth((bounded(e)-.08f)/.27f);}
    static float sourceTravel(float e){return smooth(bounded(e)/.50f);}
    static float cornerInset(float radius,float horizontalGap){
        float r=Math.max(0,radius),g=Math.max(0,horizontalGap);
        return g>=r?0:r-(float)Math.sqrt(Math.max(0,r*r-(r-g)*(r-g)));
    }
    static float pageAlpha(float e){return smooth((bounded(e)-.20f)/.45f);}
    static float pageOffsetY(float containerTop,float endTop){return 0;}
    /** Conservative rounded-rectangle inset: reveal only complete content, never half a glyph. */
    static float contentReveal(float left,float top,float right,float bottom,float radius,
            float contentLeft,float contentTop,float contentRight,float contentBottom,float feather){
        if(!(right>left&&bottom>top&&contentRight>contentLeft&&contentBottom>contentTop))return 0;
        float r=Math.max(0,Math.min(radius,Math.min(right-left,bottom-top)*.5f));
        float clearance=Math.min(Math.min(contentLeft-left,right-contentRight),
                Math.min(contentTop-top-r,bottom-r-contentBottom));
        return smooth(clearance/Math.max(1,feather));
    }
    static float parentHeaderAlpha(float containerTop,float inkTop,float inkBottom){
        return bounded((containerTop-inkTop)/Math.max(1,inkBottom-inkTop));
    }
}
