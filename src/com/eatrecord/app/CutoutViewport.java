package com.eatrecord.app;

/** Display-only geometry. Mask pixels and saved bounds always remain in image coordinates. */
final class CutoutViewport {
    static final float MIN_ZOOM=1f,MAX_ZOOM=6f;
    private final int imageWidth,imageHeight;
    private int width,height;
    private float fitScale,zoom=1f,panX,panY;

    CutoutViewport(int imageWidth,int imageHeight){
        if(imageWidth<=0||imageHeight<=0)throw new IllegalArgumentException("Empty image");
        this.imageWidth=imageWidth;this.imageHeight=imageHeight;
    }
    void resize(int width,int height){
        this.width=Math.max(0,width);this.height=Math.max(0,height);
        fitScale=this.width==0||this.height==0?0:Math.min(Math.max(1,this.width-24f)/imageWidth,Math.max(1,this.height-24f)/imageHeight);
        clampPan();
    }
    boolean ready(){return fitScale>0;}
    float zoom(){return zoom;}
    float scale(){return fitScale*zoom;}
    float left(){return width*.5f+panX-imageWidth*scale()*.5f;}
    float top(){return height*.5f+panY-imageHeight*scale()*.5f;}
    float right(){return left()+imageWidth*scale();}
    float bottom(){return top()+imageHeight*scale();}
    float imageX(float screenX){return (screenX-left())/scale();}
    float imageY(float screenY){return (screenY-top())/scale();}
    float brushImageWidth(float screenWidth){return screenWidth/scale();}
    boolean contains(float x,float y){return ready()&&x>=left()&&x<=right()&&y>=top()&&y<=bottom();}

    /** Anchor at the previous two-finger focus; translation follows the new focus 1:1. */
    void transform(float fromX,float fromY,float toX,float toY,float factor){
        if(!ready()||!finite(fromX)||!finite(fromY)||!finite(toX)||!finite(toY)||!finite(factor)||factor<=0)return;
        float imageX=imageX(fromX),imageY=imageY(fromY);
        zoom=Math.max(MIN_ZOOM,Math.min(MAX_ZOOM,zoom*factor));
        panX=toX-width*.5f+(imageWidth*.5f-imageX)*scale();
        panY=toY-height*.5f+(imageHeight*.5f-imageY)*scale();
        clampPan();
    }
    void reset(){zoom=1;panX=panY=0;clampPan();}
    private void clampPan(){
        float maxX=Math.max(0,(imageWidth*scale()-Math.max(1,width-24f))*.5f);
        float maxY=Math.max(0,(imageHeight*scale()-Math.max(1,height-24f))*.5f);
        panX=Math.max(-maxX,Math.min(maxX,panX));panY=Math.max(-maxY,Math.min(maxY,panY));
    }
    private static boolean finite(float n){return !Float.isNaN(n)&&!Float.isInfinite(n);}
}
