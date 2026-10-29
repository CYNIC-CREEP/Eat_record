package com.eatrecord.app;

/** Pure geometry contracts; Android QA checks mask pixels and real multi-pointer delivery. */
public final class CutoutViewportTest {
    private static int checks;
    private static void check(boolean value,String name){if(!value)throw new AssertionError(name);checks++;}
    private static void close(float actual,float expected,String name){check(Math.abs(actual-expected)<.01f,name+": "+actual+" != "+expected);}
    public static void main(String[] args){
        CutoutViewport v=new CutoutViewport(1024,768);
        check(!v.ready(),"before layout has no usable inverse matrix");
        v.resize(480,720);check(v.ready(),"layout creates usable fit geometry");
        close(v.zoom(),1,"starts at fit");close(v.left(),12,"legacy horizontal inset preserved");
        close(v.imageX(240),512,"fit screen center maps to source center X");close(v.imageY(360),384,"fit center maps to source center Y");
        check(v.contains(240,360)&&!v.contains(0,0),"letterbox taps are outside paintable image");
        v.transform(240,360,280,400,1);close(v.left(),12,"fit cannot pan into empty space");
        close(v.top()+768*v.scale()*.5f,360,"fit cannot drift vertically");
        for(int factor=1;factor<=6;factor++){
            v.reset();v.transform(240,360,240,360,factor);
            close(v.zoom(),factor,"requested bounded zoom "+factor);
            close(v.brushImageWidth(28)*v.scale(),28,"screen brush remains 28px at zoom "+factor);
            close(v.imageX(240),512,"zoom keeps center source anchor "+factor);
            close(v.imageY(360),384,"zoom keeps center source anchor Y "+factor);
        }
        v.reset();v.transform(240,360,240,360,4);
        float x=v.imageX(200),y=v.imageY(300);
        v.transform(200,300,210,320,1.1f);
        close(v.imageX(210),x,"pinch focus source X stays under fingers");
        close(v.imageY(320),y,"pinch focus source Y stays under fingers");
        float left=v.left(),top=v.top();v.transform(210,320,230,350,1);
        close(v.left(),left+20,"two finger pan follows horizontal delta 1:1");
        close(v.top(),top+30,"two finger pan follows vertical delta 1:1");
        v.transform(240,360,100000,100000,1);
        close(v.left(),12,"pan clamps at first image edge");close(v.top(),12,"vertical pan clamps at image edge");
        v.transform(240,360,-100000,-100000,1);
        close(v.right(),468,"pan clamps at last image edge");close(v.bottom(),708,"last vertical edge remains in viewport");
        v.reset();v.transform(240,360,240,360,100);close(v.zoom(),6,"maximum zoom is six times fit");
        v.transform(240,360,240,360,2);close(v.zoom(),6,"further expansion saturates without overshoot");
        v.transform(240,360,240,360,.5f);close(v.zoom(),3,"reversing at max responds immediately");
        v.transform(240,360,240,360,.001f);close(v.zoom(),1,"pinch cannot shrink below fit");
        left=v.left();v.transform(240,360,Float.NaN,0,2);close(v.left(),left,"invalid focus cannot poison viewport");
        v.transform(240,360,240,360,Float.POSITIVE_INFINITY);close(v.zoom(),1,"invalid ratio ignored");
        v.transform(240,360,240,360,0);close(v.zoom(),1,"zero ratio ignored");
        v.transform(240,360,240,360,4);v.resize(240,360);
        check(v.scale()>0&&v.zoom()==4,"resize preserves bounded magnification");
        close(v.brushImageWidth(28)*v.scale(),28,"resize retains screen brush size");
        v.reset();close(v.zoom(),1,"reset restores fit zoom");close(v.imageX(120),512,"reset recenters source after resize");
        CutoutViewport tiny=new CutoutViewport(8,3);tiny.resize(240,360);tiny.transform(120,180,120,180,6);
        close(tiny.brushImageWidth(28)*tiny.scale(),28,"small original keeps screen brush contract");
        tiny.resize(0,0);check(!tiny.ready(),"detached/zero-size geometry cannot be used for drawing");
        System.out.println("PASS: "+checks+" cutout viewport geometry checks");
    }
}
