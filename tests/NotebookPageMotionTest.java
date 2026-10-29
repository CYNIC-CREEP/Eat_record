package com.eatrecord.app;

/** Sequence-level tests: queued swipes are independent of render timing and do not touch diary data. */
public final class NotebookPageMotionTest {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static void close(float actual,float wanted,String message){check(Math.abs(actual-wanted)<.0001f,message+": "+actual+" != "+wanted);}
    private static int source(NotebookPageMotion m,int direction){return direction>0?(int)Math.floor(m.position()):(int)Math.ceil(m.position());}
    private static void burst(int direction,float fraction){
        NotebookPageMotion m=new NotebookPageMotion(9,direction>0?0:8);
        for(int i=1;i<=4;i++){
            m.grab(source(m,direction));float start=m.position();m.drag(direction*.13f);m.drag(direction*.64f);
            close(m.position(),start+direction*.64f,"UP endpoint must be usable without a terminal MOVE");
            int target=m.release(0,false);check(target==(direction>0?i:8-i),"every same-direction swipe advances its own target");
            m.animate(m.position()+(target-m.position())*fraction);
        }
        m.finish();close(m.position(),direction>0?4:4,"four rapid turns land on the fourth neighbour");
        m.grab(4);m.drag(-direction*.66f);check(m.release(0,false)==4-direction,"reverse is immediately usable after burst");m.finish();
    }
    public static void main(String[] args){
        for(int direction:new int[]{1,-1})for(float fraction:new float[]{0,.05f,.4f,.95f,1})burst(direction,fraction);
        NotebookPageMotion m=new NotebookPageMotion(8,0);
        m.grab(0);m.drag(.7f);check(m.release(0,true)==0,"cancel first drag preserves source");m.finish();
        m.grab(0);m.drag(.64f);check(m.release(0,false)==1,"first release commits neighbour");m.animate(.75f);
        m.grab(0);m.drag(-.3f);check(m.release(0,true)==0,"reverse/cancel during settle restores grabbed source");m.finish();
        m.grab(0);m.drag(.64f);m.release(0,false);m.animate(.7f);m.grab(0);m.drag(.2f);
        check(m.release(0,true)==1,"cancel continuation preserves prior committed target");m.finish();
        m.grab(1);m.drag(.6f);int target=m.release(0,true);float position=m.position();m.drag(5);
        check(m.target()==target,"cancelled stream cannot recommit on a remaining finger");close(m.position(),position,"released stream ignores stale MOVE");m.finish();
        m.reset(5,2);m.grab(2);m.drag(.08f);check(m.release(0,false)==2,"short slow drag rests");m.finish();
        m.grab(2);m.drag(.08f);check(m.release(1,false)==3,"short deliberate fling advances");m.finish();
        m.grab(3);m.drag(-.6f);check(m.release(0,false)==2,"reverse from settled page");m.finish();
        for(int i=0;i<20;i++){m.grab((int)m.position());m.drag(1);m.release(3,false);m.finish();}close(m.position(),4,"oldest boundary stops safely");
        for(int i=0;i<20;i++){m.grab((int)m.position());m.drag(-1);m.release(-3,false);m.finish();}close(m.position(),0,"newest boundary stops safely");
        m.grab(0);m.drag(-1);check(m.overscroll()<0,"boundary has reversible resistance input");m.release(0,false);m.finish();
        m.grab(0);m.drag(.65f);check(m.release(0,false)==1,"boundary never latches next valid swipe");m.finish();
        m.reset(1,0);m.grab(0);m.drag(100);m.release(100,false);m.finish();close(m.position(),0,"single-record notebook remains valid");
        m.reset(7,3);m.grab(3);m.drag(Float.NaN);m.animate(Float.POSITIVE_INFINITY);close(m.position(),3,"invalid coordinates cannot poison state");m.release(Float.NaN,true);m.finish();
        m.reset(8,0);m.grab(0);m.drag(.65f);m.release(100,false);m.animate(4);close(m.position(),1,"high release velocity cannot overshoot to an unintended third page");m.finish();
        m.grab(1);m.drag(.2f);float held=m.position();m.animate(0);close(m.position(),held,"stale animation cannot overwrite a new grab");m.release(0,true);m.finish();
        m.reset(7,3);
        java.util.Random random=new java.util.Random(27);
        for(int i=0;i<400;i++){
            m.grab(Math.round(m.position()));m.drag(random.nextFloat()*2-1);m.release(random.nextFloat()*4-2,random.nextInt(5)==0);
            check(m.target()>=0&&m.target()<7,"target bounded after interruption");
            m.animate(m.position()+(m.target()-m.position())*random.nextFloat());
            check(Float.isFinite(m.position())&&m.position()>=0&&m.position()<=6,"presentation finite and bounded");
            if(i%7==0){m.finish();close(m.position(),m.target(),"settle reaches intended page");}
        }
        System.out.println("PASS: "+checks+" notebook continuous-motion sequence checks (no device/frame-rate claim)");
    }
}
