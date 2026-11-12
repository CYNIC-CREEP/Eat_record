package com.eatrecord.app;

/** Visual state only. Distances are pixels; derivatives are converted to measured item units. */
final class BottomNavMotion {
    static final double POSITION_K=246.740110, POSITION_DAMPING=.85;
    static final double FOLLOW_K=1024, FOLLOW_DAMPING=.9;
    static final double LIFT_K=631.654682, JELLY_K=100;
    static final class Channel {
        double value,velocity,target;
        void snap(double x){value=target=x;velocity=0;}
        void step(double dt,double stiffness,double damping){
            double w=Math.sqrt(stiffness),y=value-target,v=velocity;
            if(damping==1){
                double b=v+w*y,e=Math.exp(-w*dt);
                value=target+(y+b*dt)*e;velocity=(v-w*b*dt)*e;
            }else{
                double d=w*damping,q=w*Math.sqrt(1-damping*damping),b=(v+d*y)/q;
                double c=Math.cos(q*dt),s=Math.sin(q*dt),e=Math.exp(-d*dt);
                value=target+e*(y*c+b*s);
                velocity=e*((-d*y+q*b)*c+(-d*b-q*y)*s);
            }
        }
        boolean rest(double epsilon){return Math.abs(value-target)<epsilon&&Math.abs(velocity)<epsilon*8;}
    }
    final Channel position=new Channel(),lift=new Channel(),jelly=new Channel();
    private double unit=1;
    private boolean pressed,releasePending,following;
    void unit(double pixels){if(Double.isFinite(pixels)&&pixels>0)unit=pixels;}
    void snap(double x){position.snap(x);lift.snap(0);jelly.snap(0);pressed=releasePending=following=false;}
    void select(double x){
        if(!Double.isFinite(x))return;
        if(position.target!=x){lift.target=1;releasePending=!pressed;}
        position.target=x;following=false;
    }
    void follow(double x){if(Double.isFinite(x)){position.target=x;following=true;lift.target=1;releasePending=!pressed;}}
    void press(){pressed=true;releasePending=false;lift.target=1;}
    void release(boolean cancelled){pressed=false;releasePending=!cancelled;if(cancelled)lift.target=0;}
    boolean step(double seconds){
        if(!Double.isFinite(seconds)||seconds<0)return running();
        double remaining=Math.min(.25,seconds);
        while(remaining>0){
            double dt=Math.min(remaining,1d/240);remaining-=dt;
            double k=following?FOLLOW_K:POSITION_K,z=following?FOLLOW_DAMPING:POSITION_DAMPING;
            position.step(dt,k,z);
            double v=position.velocity/unit;
            double a=(-k*(position.value-position.target)-2*z*Math.sqrt(k)*position.velocity)/unit;
            jelly.target=.2*Math.tanh(Math.abs(v)/3)-.15*Math.tanh(Math.max(0,-a*Math.signum(v))/25);
            if(releasePending&&lift.value>=.975&&Math.abs(position.value-position.target)/unit<=.04){
                releasePending=false;lift.target=0;
            }
            lift.step(dt,LIFT_K,1);jelly.step(dt,JELLY_K,.4);
        }
        if(position.rest(unit*.0005))position.snap(position.target);
        if(lift.rest(.0005))lift.snap(lift.target);
        if(position.velocity==0&&jelly.rest(.0005)){jelly.snap(0);}
        return running();
    }
    boolean running(){return releasePending||!position.rest(unit*.0005)||!lift.rest(.0005)||!jelly.rest(.0005);}
}
