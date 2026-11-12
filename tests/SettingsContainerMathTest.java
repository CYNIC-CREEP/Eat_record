package com.eatrecord.app;

/** Pure geometry/identity contracts. Real decoded device frames remain the visual oracle. */
public final class SettingsContainerMathTest {
    private static int checks;
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static void near(float actual,float expected,float tolerance,String why){check(Math.abs(actual-expected)<=tolerance,why+": "+actual+" vs "+expected);}
    private static boolean finite(float x){return !Float.isNaN(x)&&!Float.isInfinite(x);}
    private static double cubic(double t,double p1,double p2){double u=1-t;return 3*u*u*t*p1+3*u*t*t*p2+t*t*t;}
    private static float[] rect(float[] start,float[] end,float e){
        float[] result=new float[4];for(int edge=0;edge<4;edge++)result[edge]=SettingsContainerMath.lerp(start[edge],end[edge],SettingsContainerMath.edgeProgress(e,edge));return result;
    }
    private static float reveal(float[] path,float radius,float[] content,float feather){return SettingsContainerMath.contentReveal(path[0],path[1],path[2],path[3],radius,content[0],content[1],content[2],content[3],feather);}
    private static void geometry(float[] start,float[] end,float radius){
        float[] previous=start.clone();float priorRadius=radius;
        for(int i=0;i<=200;i++){
            float e=SettingsContainerMath.ease(i/200f);float[] current=rect(start,end,e);float width=current[2]-current[0],height=current[3]-current[1];
            check(finite(width)&&finite(height)&&width>0&&height>0,"phased edges keep positive finite geometry");
            check(current[0]<=previous[0]+.001f&&current[1]<=previous[1]+.001f,"left/top continuously approach page edges");
            check(current[2]>=previous[2]-.001f&&current[3]>=previous[3]-.001f,"right/bottom continuously approach page edges");
            check(width>=previous[2]-previous[0]-.001f&&height>=previous[3]-previous[1]-.001f,"expansion never reverses width/height");
            float r=SettingsContainerMath.radius(radius,e);check(finite(r)&&r>=0&&r<=priorRadius+.0001f,"corner continuously settles without overshoot");
            near(SettingsContainerMath.pageOffsetY(current[1],end[1]),0,0,"only geometry/source elements move; target page stays at its final origin");
            for(int edge=0;edge<4;edge++){
                check(current[edge]>=Math.min(start[edge],end[edge])-.001f&&current[edge]<=Math.max(start[edge],end[edge])+.001f,"each measured edge stays within its endpoints");
                float reverse=SettingsContainerMath.lerp(end[edge],start[edge],1-SettingsContainerMath.edgeProgress(e,edge));near(current[edge],reverse,.001f,"return uses the same phased edge path");
            }
            previous=current;priorRadius=r;
        }
        for(int edge=0;edge<4;edge++){near(rect(start,end,0)[edge],start[edge],.001f,"source bound is exact");near(rect(start,end,1)[edge],end[edge],.001f,"destination bound is exact");}
        float[] middle=rect(start,end,.5f);check(middle[2]-middle[0]>start[2]-start[0]&&middle[2]-middle[0]<end[2]-end[0],"middle width proves a growing container rather than a full-width slide");
        check(middle[3]-middle[1]>start[3]-start[1]&&middle[3]-middle[1]<end[3]-end[1],"middle height remains below the final sparse page height");
        near(SettingsContainerMath.radius(radius,0),radius,.0001f,"actual card corner starts unchanged");near(SettingsContainerMath.radius(radius,1),0,.0001f,"page corner ends at zero");
    }
    private static void redirects(){
        for(float initial:new float[]{0,.05f,.25f,.45f,.5f,.62f,.8f,.99f,1})for(boolean opening:new boolean[]{false,true}){
            float target=opening?1:0;if(initial==target)continue;
            float[] origin={47,318,1033,1265},destination=opening?new float[]{0,0,1080,2340}:new float[]{55,635,1025,829};
            float[] previous=origin.clone();
            for(int i=0;i<=100;i++){
                float e=SettingsContainerMath.lerp(initial,target,i/100f);
                for(int edge=0;edge<4;edge++){
                    float f=SettingsContainerMath.redirectFraction(e,initial,opening,edge);check(finite(f)&&f>=0&&f<=1,"redirect fraction stays finite and bounded for saturated channels");
                    float position=SettingsContainerMath.lerp(origin[edge],destination[edge],f);
                    if(destination[edge]>=origin[edge])check(position>=previous[edge]-.001f,"redirect edge cannot jump backward");else check(position<=previous[edge]+.001f,"redirect edge cannot jump backward");
                    if(i==0)near(position,origin[edge],.001f,"redirect starts from current actual edge");if(i==100)near(position,destination[edge],.001f,"redirect settles to remeasured endpoint");
                    previous[edge]=position;
                }
            }
        }
        // Both vertical channels have arrived, but a remeasured endpoint can still differ.
        for(int edge:new int[]{1,3}){
            near(SettingsContainerMath.redirectFraction(.9f,.9f,true,edge),0,0,"saturated vertical edge starts at its actual position");
            near(SettingsContainerMath.redirectFraction(.95f,.9f,true,edge),.5f,.000001f,"saturated vertical edge uses the remaining global progress for a moved target");
            near(SettingsContainerMath.redirectFraction(1,.9f,true,edge),1,0,"saturated vertical edge reaches its changed endpoint");
            near(SettingsContainerMath.redirectFraction(.3f,.3f,false,edge),0,0,"returning vertical edge begins at the current position");
            float middle=SettingsContainerMath.redirectFraction(.15f,.3f,false,edge);
            check(middle>0&&middle<1,"both vertical edges continuously return to the newly located source");
            near(SettingsContainerMath.redirectFraction(0,.3f,false,edge),1,0,"both vertical edges settle to the source endpoint");
        }
        near(SettingsContainerMath.redirectFraction(.15f,.3f,false,1),SettingsContainerMath.redirectFraction(.15f,.3f,false,3),0,"up/down retargeting uses identical normalized progress");
    }
    private static void bandSequence(float[] start,float[] end,float radius,float[] header,float[] body,float feather){
        float priorIdentity=0,priorSource=1,priorTarget=0;
        for(int i=0;i<=300;i++){
            float e=i/300f;float[] path=rect(start,end,e);float r=SettingsContainerMath.radius(radius,e);
            float h=reveal(path,r,header,feather),b=reveal(path,r,body,feather),id=SettingsContainerMath.identity(e,h,b),source=SettingsContainerMath.rowAlpha(id),target=SettingsContainerMath.targetAlpha(id);
            check(finite(h)&&h>=0&&h<=1&&finite(b)&&b>=0&&b<=1,"measured complete-band gates stay bounded");
            for(float[] band:new float[][]{header,body})if(reveal(path,r,band,feather)>0){check(band[0]>path[0]&&band[2]<path[2],"admitted band fits horizontally");check(band[1]>path[1]+r&&band[3]<path[3]-r,"target glyph band is complete within conservative rounded bounds");}
            check(id>=priorIdentity-.000001f&&source<=priorSource+.000001f&&target>=priorTarget-.000001f,"one geometry/identity state gives monotonic content exchange");
            near(source*target,0,.000001f,"two readable source/target titles never coexist");
            if(source==0&&target==0)near(id,.4f,.00001f,"only the single fade-through crossover is empty");
            if(h==0||b==0){near(id,0,0,"identity waits for both whole header and body");near(target,0,0,"body cannot precede safely visible page title");near(source,1,0,"source remains recognizable while either target band is unavailable");}
            near(SettingsContainerMath.targetAlpha(id),target,0,"header and body share exactly one target alpha");
            near(SettingsContainerMath.pageOffsetY(path[1],end[1]),0,0,"admission never introduces full-page drift");
            float reverse=1-(300-i)/300f;float[] reversePath=rect(start,end,reverse);
            float reverseId=SettingsContainerMath.identity(reverse,reveal(reversePath,SettingsContainerMath.radius(radius,reverse),header,feather),reveal(reversePath,SettingsContainerMath.radius(radius,reverse),body,feather));
            near(reverseId,id,.0001f,"reverse derives the same identity from the same current geometry");
            priorIdentity=id;priorSource=source;priorTarget=target;
        }
    }
    public static void main(String[] args){
        check(SettingsContainerMath.DURATION_MS==480,"established duration is unchanged");
        for(int i=0;i<=1000;i++){double t=i/1000d;near(SettingsContainerMath.ease((float)cubic(t,.25,.25)),(float)cubic(t,0,1),.00001f,"Bezier still solves its time coordinate");}
        float priorTop=0,priorBottom=0,priorPage=0,priorTravel=0,priorDecor=1;
        for(int i=0;i<=1000;i++){
            float e=i/1000f,top=SettingsContainerMath.topProgress(e),bottom=SettingsContainerMath.bottomProgress(e),page=SettingsContainerMath.pageAlpha(e),travel=SettingsContainerMath.sourceTravel(e),decor=SettingsContainerMath.decorationAlpha(e);
            check(top>=priorTop&&top>=0&&top<=1&&bottom>=priorBottom&&bottom>=0&&bottom<=1,"phased top/bottom remain monotonic and bounded");
            check(page>=priorPage&&page>=0&&page<=1,"identity time window remains bounded");
            check(travel>=priorTravel&&travel>=0&&travel<=1&&decor<=priorDecor+.000001f&&decor>=0&&decor<=1,"source-only movement and decoration exit stay continuous");
            near(SettingsContainerMath.parentAlpha(e),1,0,"static parent reference never globally fades");
            near(SettingsContainerMath.edgeProgress(e,0),e,0,"left edge retains normalized interpolation");near(SettingsContainerMath.edgeProgress(e,2),e,0,"right edge retains normalized interpolation");
            near(top,bottom,0,"top and bottom expand together on the same normalized progress");
            if(e>0&&e<.62f)check(top>0&&top<1&&bottom>0&&bottom<1,"both vertical edges start immediately and remain active together");
            if(e>=.62f){near(top,1,0,"upper edge completes the shared expansion window");near(bottom,1,0,"lower edge completes at the same progress");}
            if(e<=.08f)near(decor,1,.000001f,"source decoration is recognizable at the start");if(e>=.35f)near(decor,0,.000001f,"icon/subtitle decoration yields before target identity");
            if(e>=.5f)near(travel,1,0,"source label settles near target baseline before final content");
            float sx=SettingsContainerMath.lerp(254,236,travel),sy=SettingsContainerMath.lerp(1010,202,travel);
            check(sx>=236&&sx<=254&&sy>=202&&sy<=1010,"only source baseline travels between measured anchors without overshoot");
            near(SettingsContainerMath.pageOffsetY(928*(1-top),0),0,0,"source travel never moves the target business page");
            priorTop=top;priorBottom=bottom;priorPage=page;priorTravel=travel;priorDecor=decor;
        }
        near(SettingsContainerMath.topProgress(0),0,0,"top exact source");near(SettingsContainerMath.bottomProgress(0),0,0,"bottom starts with top");
        near(SettingsContainerMath.topProgress(.62f),1,0,"top exact shared completion");near(SettingsContainerMath.bottomProgress(.62f),1,0,"bottom completes with top");
        near(SettingsContainerMath.topProgress(1),1,0,"top page endpoint");near(SettingsContainerMath.bottomProgress(1),1,0,"bottom page endpoint");
        check(SettingsContainerMath.topProgress(.5f)>0&&SettingsContainerMath.topProgress(.5f)<1,"middle frame retains visible vertical expansion");
        near(SettingsContainerMath.topProgress(.5f),SettingsContainerMath.bottomProgress(.5f),0,"middle frame has synchronized upper and lower growth");
        near(SettingsContainerMath.pageAlpha(.425f),.5f,.000001f,"20..65 handoff window unchanged");
        for(int i=0;i<=1000;i++){
            float id=i/1000f,source=SettingsContainerMath.rowAlpha(id),target=SettingsContainerMath.targetAlpha(id);
            check(finite(source)&&source>=0&&source<=1&&finite(target)&&target>=0&&target<=1,"both identity channels are finite and bounded");
            near(source*target,0,.000001f,"source text and target title cannot be readable together");
            if(id<.4f)check(source>0&&target==0,"source owns the first identity segment");else if(id>.4f)check(source==0&&target>0,"target owns the later identity segment");else near(source+target,0,0,"fade through has one zero crossing");
            near(SettingsContainerMath.identity(.8f,id,1),id,.000001f,"identity uses the lesser complete-band reveal");near(SettingsContainerMath.identity(.8f,1,id),id,.000001f,"header/body cannot compete for identity");
        }
        near(SettingsContainerMath.rowAlpha(0),1,0,"source starts fully visible");near(SettingsContainerMath.rowAlpha(.2f),.5f,.000001f,"source fade midpoint");near(SettingsContainerMath.targetAlpha(.7f),.5f,.000001f,"target fade midpoint");near(SettingsContainerMath.targetAlpha(1),1,0,"target settles fully visible");
        for(float r:new float[]{0,1,40,95.545f,300}){
            float prior=r;
            for(int i=0;i<=200;i++){
                float gap=r*i/200f,inset=SettingsContainerMath.cornerInset(r,gap);check(finite(inset)&&inset>=0&&inset<=prior+.0001f&&inset<=r+.0001f,"source corner inset is monotonic and bounded");
                if(r>0){float equation=(r-gap)*(r-gap)+(r-inset)*(r-inset);near(equation,r*r,Math.max(.001f,r*r*.00001f),"inset lies on the actual corner circle");}
                prior=inset;
            }
            near(SettingsContainerMath.cornerInset(r,0),r,.0001f,"source at corner edge needs full radius inset");near(SettingsContainerMath.cornerInset(r,r+10),0,0,"source beyond the corner needs no full-radius padding");
        }
        near(SettingsContainerMath.cornerInset(-10,-5),0,0,"negative source radius/gap safely clamps");
        near(SettingsContainerMath.radius(100,.25f),93.75f,.0001f,"card radius profile unchanged");near(SettingsContainerMath.radius(100,.5f),75,0,"middle radius unchanged");near(SettingsContainerMath.radius(100,1),0,0,"radius final is square");
        float[] path={0,0,500,500};near(reveal(path,40,new float[]{100,20,200,80},20),0,0,"target top-clipped band is withheld");near(reveal(path,40,new float[]{100,430,200,480},20),0,0,"target bottom-clipped band is withheld");near(reveal(path,40,new float[]{-1,80,200,120},20),0,0,"partial left band is withheld");near(reveal(path,40,new float[]{100,80,501,120},20),0,0,"partial right band is withheld");near(reveal(path,40,new float[]{100,50,200,90},20),.5f,.000001f,"safe clearance feathers only after the full band fits");near(reveal(path,40,new float[]{100,80,200,120},20),1,0,"safe band becomes complete");
        near(reveal(new float[]{0,0,0,500},40,new float[]{10,50,20,90},20),0,0,"zero container withholds content");near(reveal(path,40,new float[]{100,80,100,120},20),0,0,"zero content safely ignored");
        near(reveal(new float[]{0,0,1000,600},30,new float[]{620,120,800,180},20),1,0,"wide viewport uses actual bounds");near(reveal(new float[]{0,0,600,600},30,new float[]{620,120,800,180},20),0,0,"narrow viewport cannot reuse stale wide geometry");
        for(float top:new float[]{80,120,170,220,300}){
            float h=SettingsContainerMath.parentHeaderAlpha(top,120,220);check(finite(h)&&h>=0&&h<=1,"parent title edge gate remains bounded");if(top>=220)near(h,1,0,"uncovered old header remains clear");if(top<=120)near(h,0,0,"covered old header is retired");
        }
        for(float invalid:new float[]{Float.NaN,Float.NEGATIVE_INFINITY,Float.POSITIVE_INFINITY}){
            check(finite(SettingsContainerMath.ease(invalid))&&finite(SettingsContainerMath.topProgress(invalid))&&finite(SettingsContainerMath.bottomProgress(invalid)),"invalid clocks cannot poison geometry");
            check(finite(SettingsContainerMath.rowAlpha(invalid))&&finite(SettingsContainerMath.targetAlpha(invalid))&&finite(SettingsContainerMath.identity(invalid,1,1)),"invalid identity stays finite");check(finite(SettingsContainerMath.sourceTravel(invalid))&&finite(SettingsContainerMath.decorationAlpha(invalid)),"invalid local source clock stays finite");near(SettingsContainerMath.pageOffsetY(invalid,0),0,0,"invalid geometry never moves the target");near(SettingsContainerMath.parentAlpha(invalid),1,0,"invalid clock never blanks context");check(finite(reveal(path,40,new float[]{invalid,80,200,120},8)),"invalid measured band stays finite");
        }
        for(float outside:new float[]{-2,2}){float endpoint=outside<0?0:1;near(SettingsContainerMath.topProgress(outside),SettingsContainerMath.topProgress(endpoint),0,"top clamps overshoot");near(SettingsContainerMath.bottomProgress(outside),SettingsContainerMath.bottomProgress(endpoint),0,"bottom clamps overshoot");near(SettingsContainerMath.radius(36,outside),SettingsContainerMath.radius(36,endpoint),0,"corner clamps overshoot");}
        float[] end={0,0,1080,2256};
        for(float[] start:new float[][]{{54,520,1026,720},{54,180,1026,380},{54,1820,1026,2020}}){geometry(start,end,108);bandSequence(start,end,108,new float[]{198,125,700,226},new float[]{90,500,990,700},22);}
        geometry(new float[]{24,300,576,560},new float[]{0,0,600,900},72);bandSequence(new float[]{24,300,576,560},new float[]{0,0,600,900},72,new float[]{70,120,530,190},new float[]{60,220,540,350},16);
        geometry(new float[]{90,140,1140,240},new float[]{0,0,1280,720},54);bandSequence(new float[]{90,140,1140,240},new float[]{0,0,1280,720},54,new float[]{198,80,700,150},new float[]{400,180,1100,290},16);
        redirects();
        System.out.println("PASS: "+checks+" synchronized vertical container geometry, redirected edges, unified identity and source-only travel checks");
    }
}
