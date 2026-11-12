package com.eatrecord.app;

/** Deterministic physics and interruption tests; Android integration is tested by the QA runner. */
public final class TabMotionTest {
    private static int checks;
    private static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
    private static void near(double a,double b,double tolerance,String why){check(Math.abs(a-b)<tolerance,why+": "+a+" vs "+b);}
    private static void settle(BottomNavMotion m){int frames=0;while(m.step(1d/120)&&++frames<2400){}check(frames<2400,"no permanent frame loop");}
    public static void main(String[] args){
        BottomNavMotion m=new BottomNavMotion();m.unit(100);m.snap(50);
        near(m.position.velocity,0,1e-12,"initial velocity zero");m.press();m.release(false);m.select(250);
        boolean lifted=false,deformed=false,returned=false;
        for(int i=0;i<250;i++){m.step(1d/120);lifted|=m.lift.value>.975;deformed|=Math.abs(m.jelly.value)>.005;returned|=lifted&&m.lift.value<.02;}
        check(lifted&&deformed&&returned,"travel produces visible lift, velocity jelly and return");settle(m);
        near(m.position.value,250,.001,"final measured center");near(m.lift.value,0,.001,"lift rests");near(m.jelly.value,0,.001,"jelly rests");
        for(int next:new int[]{0,2,1,0,1,0,2,0,2,1}){
            m.select(next*100+50);m.step(.035);double x=m.position.value,v=m.position.velocity;
            m.select((2-next)*100+50);near(m.position.value,x,1e-12,"redirect retains presentation");near(m.position.velocity,v,1e-12,"redirect retains velocity");
            for(int i=0;i<8;i++){m.step(.016);check(Double.isFinite(m.position.value)&&Double.isFinite(m.jelly.value),"finite reverse trajectory");}
        }
        settle(m);near(m.position.value,m.position.target,.001,"rapid latest target wins");
        m.press();for(int i=0;i<200;i++)m.step(.016);check(!m.running(),"held rest does not schedule forever");near(m.lift.value,1,.001,"hold lift");m.release(true);settle(m);near(m.lift.value,0,.001,"cancel recovers");
        m.snap(50);m.follow(165);m.step(.05);check(m.position.value>50,"existing gesture follow");m.select(50);settle(m);near(m.position.value,50,.001,"follow release restores accepted tab");
        m.unit(0);m.unit(Double.NaN);m.select(Double.NaN);m.step(Double.NaN);check(Double.isFinite(m.position.value),"invalid geometry does not poison state");
        BottomNavMotion a=new BottomNavMotion(),b=new BottomNavMotion();a.unit(100);b.unit(100);a.snap(0);b.snap(0);a.select(200);b.select(200);
        for(int i=0;i<60;i++)a.step(1d/60);for(int i=0;i<120;i++)b.step(1d/120);
        near(a.position.value,b.position.value,.0001,"refresh-independent physical position");near(a.jelly.value,b.jelly.value,.001,"refresh-independent deformation");
        // A held root drag has no clock: background ticks cannot advance it before UP.
        RootPageMotion root=new RootPageMotion(0);root.grab(0);root.drag(.42f);
        for(int i=0;i<120;i++)root.animate(1);
        near(root.position(),.42,.0001,"held drag stays at exact middle progress");
        root.drag(.18f);near(root.position(),.18,.0001,"reverse MOVE follows immediately");
        check(root.target()==0,"preview never commits navigation");
        root.release(0,true);root.finish();near(root.position(),0,.0001,"cancel restores original root");
        root.grab(0);root.drag(.65f);check(root.release(0,false)==1,"slow committed drag uses original threshold");root.finish();
        root.grab(1);root.drag(.04f);check(root.release(1,false)==2,"short fling uses original speed rule");root.finish();
        for(int target:new int[]{0,2,1,0,1,0}){float x=root.position();root.select(target);near(root.position(),x,.0001,"click retarget retains actual page position");root.animate(x+(target-x)*.3f);}
        root.finish();near(root.position(),0,.0001,"latest accepted click wins");
        NavLensGeometry g=new NavLensGeometry();g.update(98,52,1,0,350,80,1);
        check(g.growth>=18&&g.growth<=20,"reference-inspired lens grows visibly within measured space");
        check(g.width>=116&&g.height>=70,"lens expands beyond the responding outer bar");near(g.rise,0,.0001,"pressed lens stays vertically centered");near(g.contentScale,1.035,.0001,"glyph emphasis remains auxiliary");
        near(NavLensGeometry.barScaleX(g.containerSwell),1.045,.0001,"whole bar visibly widens 4.5 percent");
        near(NavLensGeometry.barScaleY(g.containerSwell),1.075,.0001,"whole bar visibly grows 7.5 percent vertically");
        float pressedWidth=g.width;g.update(98,52,1,.12f,350,80,1);check(g.width>pressedWidth+10,"moving lens has visible velocity-derived horizontal stretch");
        float stretchHeight=g.height;g.update(98,52,1,-.1f,350,80,1);check(g.width<pressedWidth&&g.height>stretchHeight,"braking compresses width and recovers height");
        g.update(98,52,0,0,350,80,1);near(g.width,98,.0001,"original steady width");near(g.height,52,.0001,"original steady height");near(g.rise,0,.0001,"steady origin");near(g.contentScale,1,.0001,"steady text scale");near(g.containerSwell,0,.0001,"whole bar rests exactly");
        near(NavLensGeometry.barScaleX(g.containerSwell),1,.0001,"outer bar steady width restored");near(NavLensGeometry.barScaleY(g.containerSwell),1,.0001,"outer bar steady height restored");
        near(NavLensGeometry.barScaleX(Float.NaN),1,.0001,"invalid feedback stays at original dimensions");
        g.update(98,52,1,0,350,80,1);check(g.containerSwell>.8f,"whole-bar feedback is driven by visible lift");check(g.width/98>g.contentScale&&g.height/52>g.contentScale,"lens dominates text on both axes");
        for(float width:new float[]{0,20,74,160,350,Float.NaN})for(float lift:new float[]{-1,0,.5f,1,2,Float.NaN})for(float jelly:new float[]{-10,-.1f,0,.2f,10,Float.NaN}){
            g.update(width,52,lift,jelly,100,80,1);
            check(Float.isFinite(g.width)&&g.width>0&&g.width<=100&&Float.isFinite(g.height)&&g.height>0&&g.height<=80,"finite bounded visual geometry even with zero/extreme input");
        }
        System.out.println("PASS: "+checks+" spring, restored root drag and liquid geometry checks");
    }
}
