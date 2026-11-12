package com.eatrecord.app;

/** Time-coordinate inversion for the requested two-piece Bezier. No navigation or Android state. */
final class SettingsTransitionMath {
    static final int DURATION_MS=450;
    static final float TRAVEL=.25f;
    static float bounded(float u){return Float.isFinite(u)?Math.max(0,Math.min(1,u)):0;}
    static float ease(float progress){
        double x=bounded(progress);if(x==0||x==1)return (float)x;
        boolean first=x<=.166666;
        double x0=first?0:.166666,x1=first?.05:.208333,x2=first?.133333:.25,x3=first?.166666:1;
        double y0=first?0:.4,y1=first?0:.82,y2=first?.06:1,y3=first?.4:1;
        double lo=0,hi=1;
        for(int i=0;i<28;i++){double t=(lo+hi)*.5;if(cubic(t,x0,x1,x2,x3)<x)lo=t;else hi=t;}
        return (float)cubic((lo+hi)*.5,y0,y1,y2,y3);
    }
    private static double cubic(double t,double a,double b,double c,double d){double v=1-t;return v*v*v*a+3*v*v*t*b+3*v*t*t*c+t*t*t*d;}
    static float incomingAlpha(float progress){float t=bounded((bounded(progress)-.15f)/.4f);float v=1-t;return 1-v*v*v;}
    static float outgoingAlpha(float progress){return 1-bounded(bounded(progress)/.30f);}
    static float outgoingHeader(float progress){float v=1-bounded(bounded(progress)/.16f);return v*v;}
    static float incomingHeader(float progress){float v=1-bounded((bounded(progress)-.16f)/.18f);return 1-v*v*v;}
    static float incomingX(float width,float progress,boolean forward,boolean rtl){return sign(forward,rtl)*TRAVEL*Math.max(0,width)*(1-ease(progress));}
    static float outgoingX(float width,float progress,boolean forward,boolean rtl){return -sign(forward,rtl)*TRAVEL*Math.max(0,width)*ease(progress);}
    private static int sign(boolean forward,boolean rtl){return (forward?1:-1)*(rtl?-1:1);}
}
