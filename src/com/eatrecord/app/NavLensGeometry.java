package com.eatrecord.app;

/** Pure visual geometry in pixels; never changes a slot's layout or input bounds. */
final class NavLensGeometry {
    static float barScaleX(float swell){return 1+.045f*bounded(swell);}
    static float barScaleY(float swell){return 1+.075f*bounded(swell);}
    private static float bounded(float value){return Float.isFinite(value)?Math.max(0,Math.min(1,value)):0;}
    float width,height,rise,contentScale,growth,containerSwell;
    void update(float baseWidth,float baseHeight,float lift,float jelly,float maxWidth,float maxHeight,float density){
        float d=Float.isFinite(density)&&density>0?density:1;
        float l=Float.isFinite(lift)?Math.max(0,Math.min(1,lift)):0;
        float j=Float.isFinite(jelly)?Math.max(-.18f,Math.min(.24f,jelly)):0;
        float w=Float.isFinite(baseWidth)?Math.max(1,baseWidth):1;
        float h=Float.isFinite(baseHeight)?Math.max(1,baseHeight):1;
        float limitW=Float.isFinite(maxWidth)?Math.max(1,maxWidth):w;
        float limitH=Float.isFinite(maxHeight)?Math.max(1,maxHeight):h;
        growth=Math.min(20*d,Math.min(h*.36f,Math.max(0,limitW-w)))*l;
        width=Math.max(1,Math.min(limitW,(w+growth)*(1+j*.8f)));
        height=Math.max(1,Math.min(limitH,(h+growth*.98f)*(1-j*.38f)));
        // Keep the body and glyphs on the nav's vertical center; depth comes from the rim/shadow.
        rise=0;
        contentScale=1+.035f*l;
        containerSwell=Math.min(1,l+Math.abs(j)*.5f);
    }
}
