package com.eatrecord.app;

/** A/B motion contracts. Decoded device frames supply the visual acceptance. */
public final class SettingsContainerExperimentTest {
    private static int checks;

    private static void check(boolean value,String reason){
        checks++;
        if(!value)throw new AssertionError(reason);
    }

    private static void near(float actual,float expected,float tolerance,String reason){
        check(Math.abs(actual-expected)<=tolerance,reason+" actual="+actual+" expected="+expected);
    }

    private static boolean finite(float value){
        return !Float.isNaN(value)&&!Float.isInfinite(value);
    }

    private static double cubic(double t,double first,double second){
        double remaining=1-t;
        return 3*remaining*remaining*t*first+3*remaining*t*t*second+t*t*t;
    }

    private static float smooth(float value){
        float clamped=Math.max(0,Math.min(1,value));
        return clamped*clamped*(3-2*clamped);
    }

    private static float[] rectangle(float[] source,float[] target,float progress,int variant){
        float[] result=new float[4];
        for(int edge=0;edge<4;edge++){
            float fraction=SettingsContainerMath.edgeProgress(progress,edge,variant);
            result[edge]=SettingsContainerMath.lerp(source[edge],target[edge],fraction);
        }
        return result;
    }

    private static float reveal(float[] frame,float radius,float[] content,float feather){
        return SettingsContainerMath.contentReveal(frame[0],frame[1],frame[2],frame[3],radius,
                content[0],content[1],content[2],content[3],feather);
    }

    private static void variantsAndClock(){
        check(UiTokens.SETTINGS_CONTAINER_EXPERIMENT_DEFAULT==SettingsContainerMath.FULL_A,"selected meal pilot defaults to A");
        check(SettingsContainerMath.duration(UiTokens.SETTINGS_CONTAINER_EXPERIMENT_DEFAULT)==480,"selected default retains the verified A clock");
        check(SettingsContainerMath.variant(0)==SettingsContainerMath.BASELINE,"baseline has a stable identity");
        check(SettingsContainerMath.variant(1)==SettingsContainerMath.FULL_A,"full expansion has a stable identity");
        check(SettingsContainerMath.variant(2)==SettingsContainerMath.LOCAL_B,"local expansion has a stable identity");
        for(int invalid:new int[]{Integer.MIN_VALUE,-10,-1,3,10,Integer.MAX_VALUE}){
            check(SettingsContainerMath.variant(invalid)==SettingsContainerMath.BASELINE,"unknown experiments safely select baseline");
            check(SettingsContainerMath.duration(SettingsContainerMath.variant(invalid))==480,"invalid selection retains the baseline clock");
        }
        check(SettingsContainerMath.duration(SettingsContainerMath.BASELINE)==480,"baseline duration is preserved");
        check(SettingsContainerMath.duration(SettingsContainerMath.FULL_A)==480,"A uses the established full expansion duration");
        check(SettingsContainerMath.duration(SettingsContainerMath.LOCAL_B)==320,"B has a bounded local and handoff clock");
        near(SettingsContainerMath.duration(SettingsContainerMath.LOCAL_B)*.75f,240,0,"B local phase lasts 240ms");
        near(SettingsContainerMath.duration(SettingsContainerMath.LOCAL_B)*.25f,80,0,"B handoff phase lasts 80ms");

        float prior=0;
        for(int i=0;i<=1000;i++){
            double parameter=i/1000d;
            float time=(float)cubic(parameter,.25,.25);
            float movement=(float)cubic(parameter,0,1);
            float recovered=SettingsContainerMath.clockProgress(movement);
            near(recovered,time,.00005f,"canonical clock inverts the independent Bezier parameterization");
            check(finite(recovered)&&recovered>=prior-.000001f&&recovered>=0&&recovered<=1,"recovered clock is monotonic and bounded");
            prior=recovered;
        }
        near(SettingsContainerMath.clockProgress(0),0,.000001f,"inverse clock starts at rest");
        near(SettingsContainerMath.clockProgress(1),1,.000001f,"inverse clock reaches its endpoint");
    }

    private static void fullExpansionA(){
        float[] source={62.275f,928.455f,1017.725f,1119.545f};
        float[] end={0,0,1080,2340};
        float[] header={55,110,1025,242};
        float[] body={55,297,1025,780};
        float[] previous=source.clone();
        float previousSource=1,previousTarget=0;

        for(int i=0;i<=1000;i++){
            float time=i/1000f;
            float movement=SettingsContainerMath.ease(time);
            float expectedFraction=smooth(movement/.90f);
            float[] frame=rectangle(source,end,movement,SettingsContainerMath.FULL_A);
            check(frame[2]>frame[0]&&frame[3]>frame[1],"A keeps positive width and height");
            check(frame[0]<=previous[0]+.001f&&frame[1]<=previous[1]+.001f,"A upper edges move continuously toward the page");
            check(frame[2]>=previous[2]-.001f&&frame[3]>=previous[3]-.001f,"A lower edges move continuously toward the page");
            for(int edge=0;edge<4;edge++){
                near(SettingsContainerMath.edgeProgress(movement,edge,SettingsContainerMath.FULL_A),expectedFraction,.000001f,"A uses one proportionate geometry channel for all four edges");
                near(SettingsContainerMath.edgeProgress(movement,edge,SettingsContainerMath.BASELINE),SettingsContainerMath.edgeProgress(movement,edge),0,"variant adapter preserves baseline geometry");
                near(frame[edge],SettingsContainerMath.lerp(end[edge],source[edge],1-expectedFraction),.001f,"A return follows the same measured path");
            }
            check(SettingsContainerMath.edgeProgress(movement,1,SettingsContainerMath.FULL_A)<=SettingsContainerMath.topProgress(movement)+.000001f,"A upper and lower expansion converge later than baseline");

            float corner=SettingsContainerMath.radius(95.545f,movement);
            float headerReveal=reveal(frame,corner,header,22);
            float bodyReveal=reveal(frame,corner,body,22);
            float safeReveal=Math.min(headerReveal,bodyReveal);
            float sourceAlpha=SettingsContainerMath.aSourceAlpha(movement);
            float targetAlpha=SettingsContainerMath.aTargetAlpha(movement,safeReveal);
            near(sourceAlpha,1-smooth((time-.24f)/.20f),.00003f,"A source fade is spread over the canonical 24..44 percent window");
            near(targetAlpha,Math.max(0,Math.min(1,(time-.44f)/.36f))*safeReveal,.00003f,"A destination fade uses the canonical 44..80 percent window and safe admission");
            check(finite(sourceAlpha)&&sourceAlpha>=0&&sourceAlpha<=1&&finite(targetAlpha)&&targetAlpha>=0&&targetAlpha<=1,"A source and destination channels are bounded");
            check(sourceAlpha<=previousSource+.000001f&&targetAlpha>=previousTarget-.000001f,"A handoff remains monotonic with safe geometry");
            near(sourceAlpha*targetAlpha,0,.000001f,"A source and destination titles never compete");
            check(targetAlpha<=safeReveal+.000001f,"A cannot outrun either complete content band");
            if(targetAlpha>0){
                check(headerReveal>0&&bodyReveal>0,"A admits the header and body together");
                for(float[] band:new float[][]{header,body}){
                    check(band[0]>frame[0]&&band[2]<frame[2],"A readable band fits horizontally");
                    check(band[1]>frame[1]+corner&&band[3]<frame[3]-corner,"A readable band fits its conservative rounded clip");
                }
            }
            near(SettingsContainerMath.aTargetAlpha(movement,0),0,0,"A completely withholds unsafe content");
            near(SettingsContainerMath.pageOffsetY(frame[1],end[1]),0,0,"A retains final content coordinates");
            previous=frame;
            previousSource=sourceAlpha;
            previousTarget=targetAlpha;
        }
        near(SettingsContainerMath.edgeProgress(.89f,1,SettingsContainerMath.FULL_A),smooth(.89f/.90f),.000001f,"A vertical edges are still approaching the new completion threshold");
        near(SettingsContainerMath.edgeProgress(.90f,1,SettingsContainerMath.FULL_A),1,0,"A geometry completes at the selected threshold");
        for(int edge=0;edge<4;edge++){
            near(rectangle(source,end,0,SettingsContainerMath.FULL_A)[edge],source[edge],.001f,"A starts at the measured row");
            near(rectangle(source,end,1,SettingsContainerMath.FULL_A)[edge],end[edge],.001f,"A lands on the page edge");
        }
    }

    private static void localExpansionB(){
        float[] source={55,927,1025,1121};
        float density=2.75f;
        float[] local={source[0]-8*density,source[1]-24*density,source[2]+8*density,source[3]+24*density};
        near(local[2]-local[0]-(source[2]-source[0]),16*density,0,"local experiment adds 8dp on each side");
        near(local[3]-local[1]-(source[3]-source[1]),48*density,0,"local experiment adds 48dp total height");

        float previousProgress=0,previousOld=1,previousTarget=0;
        for(int i=0;i<=1000;i++){
            float time=i/1000f;
            float progress=SettingsContainerMath.localProgress(time);
            float old=SettingsContainerMath.localOldAlpha(time);
            float target=SettingsContainerMath.localTargetAlpha(time);
            check(finite(progress)&&progress>=previousProgress-.000001f&&progress>=0&&progress<=1,"B local geometry is bounded and monotonic");
            check(finite(old)&&old>=0&&old<=1&&finite(target)&&target>=0&&target<=1,"B old and target content stay bounded");
            check(old<=previousOld+.000001f&&target>=previousTarget-.000001f,"B content uses a single directional handoff");
            near(old*target,0,.000001f,"B old and destination titles never become readable together");
            if(old==0&&target==0)near(time,.85f,.00001f,"B has one fade-through crossing rather than an empty interval");
            if(time<=.75f){near(old,1,0,"B source stays clear throughout local expansion");near(target,0,0,"B target waits for the local presentation phase");}
            if(time>=.75f)near(progress,1,0,"B geometry holds while the final 80ms handoff runs");
            if(target>0)near(old,0,0,"B destination identity requires full retirement of old content");
            for(int edge=0;edge<4;edge++){
                float position=SettingsContainerMath.lerp(source[edge],local[edge],progress);
                check(position>=Math.min(source[edge],local[edge])-.001f&&position<=Math.max(source[edge],local[edge])+.001f,"B local edge stays within its measured bounds");
                near(position,SettingsContainerMath.lerp(local[edge],source[edge],1-progress),.001f,"B return restores the same source-card path");
            }
            near(SettingsContainerMath.pageOffsetY(local[1],0),0,0,"B full-page handoff retains final content coordinates");
            previousProgress=progress;
            previousOld=old;
            previousTarget=target;
        }
        near(SettingsContainerMath.localProgress(0),0,0,"B starts at the source row");
        near(SettingsContainerMath.localProgress(.75f),1,0,"B reaches its local size at 240ms");
        near(SettingsContainerMath.localOldAlpha(.80f),.5f,.000002f,"B old identity midpoint lies within the final handoff");
        near(SettingsContainerMath.localTargetAlpha(.925f),.5f,.000002f,"B target identity midpoint lies within the final handoff");
        near(SettingsContainerMath.localOldAlpha(1),0,0,"B old presentation fully retires");
        near(SettingsContainerMath.localTargetAlpha(1),1,0,"B final real page is fully readable");
    }

    private static void redirects(){
        for(float initial:new float[]{0,.05f,.25f,.50f,.74f,.75f,.8f,.9f,.99f,1}){
            for(boolean opening:new boolean[]{false,true}){
                float target=opening?1:0;
                if(initial==target)continue;
                float previous=0;
                for(int i=0;i<=100;i++){
                    float current=SettingsContainerMath.lerp(initial,target,i/100f);
                    float localFraction=SettingsContainerMath.localFraction(current,initial,opening);
                    check(finite(localFraction)&&localFraction>=previous-.000001f&&localFraction>=0&&localFraction<=1,"B redirected geometry advances continuously from the current pose");
                    if(i==0)near(localFraction,0,.000001f,"B reversal begins at the actual intermediate rectangle");
                    if(i==100)near(localFraction,1,.000001f,"B reversal reaches the newly measured endpoint");
                    previous=localFraction;
                    for(int edge=0;edge<4;edge++){
                        float fraction=SettingsContainerMath.redirectFraction(current,initial,opening,edge,SettingsContainerMath.FULL_A);
                        check(finite(fraction)&&fraction>=0&&fraction<=1,"A redirected fractions stay bounded");
                        if(i==0)near(fraction,0,.000001f,"A redirect retains its actual first edge");
                        if(i==100)near(fraction,1,.000001f,"A redirect reaches the new edge");
                    }
                }
            }
        }
        near(SettingsContainerMath.localFraction(.9f,.8f,true),.5f,.000001f,"saturated B local geometry still reaches a moved local endpoint");
        near(SettingsContainerMath.redirectFraction(.97f,.94f,true,1,SettingsContainerMath.FULL_A),.5f,.000002f,"saturated A geometry still reaches a moved endpoint");
    }

    private static void invalidInputsAndSettling(){
        for(float invalid:new float[]{Float.NaN,Float.NEGATIVE_INFINITY,Float.POSITIVE_INFINITY}){
            for(float value:new float[]{SettingsContainerMath.clockProgress(invalid),SettingsContainerMath.aSourceAlpha(invalid),SettingsContainerMath.aTargetAlpha(invalid,1),SettingsContainerMath.localProgress(invalid),SettingsContainerMath.localOldAlpha(invalid),SettingsContainerMath.localTargetAlpha(invalid)}){
                check(finite(value)&&value>=0&&value<=1,"invalid experimental clocks safely converge to bounded values");
            }
            near(SettingsContainerMath.aTargetAlpha(.9f,invalid),0,0,"invalid clip admission never exposes content");
        }
        for(float progress:new float[]{-10,2,10}){
            float endpoint=progress<0?0:1;
            near(SettingsContainerMath.localProgress(progress),SettingsContainerMath.localProgress(endpoint),0,"B clamps timeline overshoot");
            near(SettingsContainerMath.edgeProgress(progress,0,SettingsContainerMath.FULL_A),SettingsContainerMath.edgeProgress(endpoint,0,SettingsContainerMath.FULL_A),0,"A clamps geometry overshoot");
        }
        // These exact endpoints are used when the Android owner settles without animation.
        near(SettingsContainerMath.aSourceAlpha(0),1,0,"A source endpoint is immediately usable");
        near(SettingsContainerMath.aSourceAlpha(1),0,0,"A completed source is immediately cleared");
        near(SettingsContainerMath.aTargetAlpha(0,1),0,0,"A target starts hidden");
        near(SettingsContainerMath.aTargetAlpha(1,1),1,0,"A target settles fully visible");
        near(SettingsContainerMath.localOldAlpha(0),1,0,"B settled parent is fully visible");
        near(SettingsContainerMath.localOldAlpha(1),0,0,"B settled target fully replaces its presentation");
        near(SettingsContainerMath.localTargetAlpha(0),0,0,"B target starts hidden");
        near(SettingsContainerMath.localTargetAlpha(1),1,0,"B target ends fully visible");
    }

    public static void main(String[] args){
        variantsAndClock();
        fullExpansionA();
        localExpansionB();
        redirects();
        invalidInputsAndSettling();
        System.out.println("PASS: "+checks+" container A/B timing, clip safety, single identity and reversible endpoint checks");
    }
}
