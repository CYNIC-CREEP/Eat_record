package com.eatrecord.app;

public final class SettingsTransitionMathTest {
    private static int checks;
    private static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
    private static void near(float a,float b,float e,String why){check(Math.abs(a-b)<e,why+": "+a+" vs "+b);}
    private static double cubic(double t,double a,double b,double c,double d){double v=1-t;return v*v*v*a+3*v*v*t*b+3*v*t*t*c+t*t*t*d;}
    public static void main(String[] args){
        for(int segment=0;segment<2;segment++)for(int i=0;i<=100;i++){
            double t=i/100d;
            double x=segment==0?cubic(t,0,.05,.133333,.166666):cubic(t,.166666,.208333,.25,1);
            double y=segment==0?cubic(t,0,0,.06,.4):cubic(t,.4,.82,1,1);
            near(SettingsTransitionMath.ease((float)x),(float)y,.00001f,"invert time-coordinate rather than using Bezier parameter");
        }
        near(SettingsTransitionMath.ease(.166666f),.4f,.00001f,"segments meet at requested point");
        float previous=0;
        for(int i=0;i<=1000;i++){
            float p=i/1000f,e=SettingsTransitionMath.ease(p);check(e>=previous&&e>=0&&e<=1,"continuous monotone bounded easing");previous=e;
            float a=SettingsTransitionMath.incomingAlpha(p),b=SettingsTransitionMath.outgoingAlpha(p);
            check(a>=0&&a<=1&&b>=0&&b<=1,"bounded content channels");
            check(a+b>=.49f,"no completely empty content handoff");
            check(SettingsTransitionMath.outgoingHeader(p)*SettingsTransitionMath.incomingHeader(p)==0,"old/new header channels never overlap");
            near(SettingsTransitionMath.incomingX(1080,p,true,false),-SettingsTransitionMath.incomingX(1080,p,true,true),.0001f,"RTL mirrors push");
            near(SettingsTransitionMath.outgoingX(1080,p,true,false),SettingsTransitionMath.outgoingX(1080,p,false,true),.0001f,"pop/RTL direction ownership");
        }
        near(SettingsTransitionMath.incomingX(1000,0,true,false),250,.0001f,"push begins at quarter width");
        near(SettingsTransitionMath.outgoingX(1000,1,true,false),-250,.0001f,"old page yields quarter width");
        near(SettingsTransitionMath.incomingX(1000,0,false,false),-250,.0001f,"parent restores from left on pop");
        near(SettingsTransitionMath.outgoingX(1000,1,false,false),250,.0001f,"child exits right on pop");
        near(SettingsTransitionMath.incomingAlpha(.15f),0,.0001f,"fade-in gate");near(SettingsTransitionMath.incomingAlpha(.55f),1,.0001f,"fade-in complete");
        near(SettingsTransitionMath.outgoingAlpha(.30f),0,.0001f,"old content gone by 30 percent");
        near(SettingsTransitionMath.incomingAlpha(.2f),SettingsTransitionMath.outgoingAlpha(.2f),.005f,"content handoff around .20");
        near(SettingsTransitionMath.ease(Float.NaN),0,.0001f,"nonfinite input guarded");
        check(SettingsTransitionMath.DURATION_MS==450,"ordinary settings clock 450ms");
        System.out.println("PASS: "+checks+" settings curve, slide and content-gate checks");
    }
}
