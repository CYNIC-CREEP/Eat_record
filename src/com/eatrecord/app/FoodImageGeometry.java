package com.eatrecord.app;

/** Shared FIT_CENTER/quarter-turn geometry for drawing and pixel-accurate food hit testing. */
final class FoodImageGeometry {
    float left, top, right, bottom, scale;
    private float centerX, centerY;
    private int rotation;

    void set(int viewWidth, int viewHeight, int imageWidth, int imageHeight, float gap, int degrees) {
        rotation = ((degrees % 360) + 360) % 360;
        centerX = viewWidth * .5f; centerY = viewHeight * .5f;
        boolean quarter = rotation == 90 || rotation == 270;
        float fitWidth = quarter ? imageHeight : imageWidth;
        float fitHeight = quarter ? imageWidth : imageHeight;
        scale = imageWidth > 0 && imageHeight > 0
                ? Math.max(0, Math.min((viewWidth - 2 * gap) / fitWidth, (viewHeight - 2 * gap) / fitHeight)) : 0;
        left = centerX - imageWidth * scale * .5f; right = centerX + imageWidth * scale * .5f;
        top = centerY - imageHeight * scale * .5f; bottom = centerY + imageHeight * scale * .5f;
    }
    private float unrotatedX(float x, float y) {
        float dx=x-centerX,dy=y-centerY;
        return centerX+(rotation==90?dy:rotation==180?-dx:rotation==270?-dy:dx);
    }
    private float unrotatedY(float x, float y) {
        float dx=x-centerX,dy=y-centerY;
        return centerY+(rotation==90?-dx:rotation==180?-dy:rotation==270?dx:dy);
    }
    float imageX(float x, float y) { return scale>0?(unrotatedX(x,y)-left)/scale:Float.NaN; }
    float imageY(float x, float y) { return scale>0?(unrotatedY(x,y)-top)/scale:Float.NaN; }
    boolean contains(float x, float y, float cornerRadius) {
        if(scale<=0||!Float.isFinite(x)||!Float.isFinite(y))return false;
        float ux=unrotatedX(x,y),uy=unrotatedY(x,y);
        if(ux<left||ux>=right||uy<top||uy>=bottom)return false;
        float radius=Math.max(0,Math.min(cornerRadius,Math.min(right-left,bottom-top)*.5f));
        if(radius==0)return true;
        float cx=Math.max(left+radius,Math.min(right-radius,ux));
        float cy=Math.max(top+radius,Math.min(bottom-radius,uy));
        float dx=ux-cx,dy=uy-cy;
        return dx*dx+dy*dy<=radius*radius;
    }
}
