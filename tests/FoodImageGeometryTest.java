package com.eatrecord.app;

public final class FoodImageGeometryTest {
    private static int checks;
    private static void check(boolean condition,String title){if(!condition)throw new AssertionError(title);checks++;}
    private static void close(float a,float b,String title){check(Math.abs(a-b)<.01f,title+": "+a+" != "+b);}
    public static void main(String[] args){
        FoodImageGeometry g=new FoodImageGeometry();
        g.set(200,200,100,50,4,0);
        close(g.left,4,"fit inset");close(g.top,52,"fit letterbox");
        check(!g.contains(100,10,0),"letterbox cannot open details");
        check(g.contains(100,100,0),"visible photo center can open details");
        close(g.imageX(100,100),50,"center source X");close(g.imageY(100,100),25,"center source Y");
        check(!g.contains(4,52,16),"rounded non-sticker corner rejected");
        check(g.contains(20,52,16),"rounded photo top tangent included");
        for(int rotation:new int[]{0,90,180,270}){
            g.set(240,160,100,50,4,rotation);
            float radians=(float)Math.toRadians(rotation),cos=(float)Math.cos(radians),sin=(float)Math.sin(radians);
            float rawX=g.left+25*g.scale,rawY=g.top+15*g.scale;
            float dx=rawX-120,dy=rawY-80;
            float x=120+cos*dx-sin*dy,y=80+sin*dx+cos*dy;
            check(g.contains(x,y,0),"rotated source point within image "+rotation);
            close(g.imageX(x,y),25,"inverse FIT X "+rotation);close(g.imageY(x,y),15,"inverse FIT Y "+rotation);
            check(!g.contains(0,0,0),"outside rotated image rejected "+rotation);
        }
        g.set(0,0,100,50,4,0);check(!g.contains(0,0,0),"zero geometry rejected");
        g.set(200,200,0,0,4,0);check(!g.contains(100,100,0),"missing image rejected");
        g.set(200,200,100,50,4,0);check(!g.contains(Float.NaN,100,0),"invalid point rejected");
        System.out.println("PASS: "+checks+" food image geometry checks");
    }
}
