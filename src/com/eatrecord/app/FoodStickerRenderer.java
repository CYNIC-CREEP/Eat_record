package com.eatrecord.app;

import android.content.Context;
import android.graphics.*;
import org.json.JSONObject;
import java.util.*;

/** Display-only alpha-following paper edges. The editable PNG is never overwritten. */
public final class FoodStickerRenderer {
    public static final String[] STYLES={"dashed","paper","double","dots"};
    public static final String[] NAMES={"白边＋虚线（默认）","素白纸边","双线纸边","点点纸边"};
    private FoodStickerRenderer() { }
    /** Returns source when upright, otherwise a new bitmap; never changes source pixels. */
    public static Bitmap rotateDisplay(Bitmap source, int degrees) {
        int rotation = NutritionStore.normalizeDisplayRotation(degrees);
        if (source == null || rotation == 0) return source;
        Matrix matrix = new Matrix(); matrix.postRotate(rotation);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }
    public static String style(Context context){
        String value=NutritionStore.read(context.getSharedPreferences(NutritionStore.PREFS,0),NutritionStore.PROFILE).optString("foodStickerBorder","dashed");
        for(String known:STYLES)if(known.equals(value))return value;return "dashed";
    }
    public static String styleTitle(String value){for(int i=0;i<STYLES.length;i++)if(STYLES[i].equals(value))return NAMES[i];return NAMES[0];}
    public static Bitmap create(Context c,Bitmap cutout,int borderPx){
        JSONObject profile=NutritionStore.read(c.getSharedPreferences(NutritionStore.PREFS,0),NutritionStore.PROFILE);
        boolean night=AppearanceMode.isNight(profile,
            (android.content.res.Resources.getSystem().getConfiguration().uiMode&android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES);
        return create(cutout,borderPx,style(c),night);
    }
    /** Existing low-level white-edge contract for old callers/tests. */
    public static Bitmap create(Bitmap cutout,int borderPx){return create(cutout,borderPx,"paper",false);}
    public static Bitmap create(Bitmap cutout,int borderPx,String style,boolean night){
        if(cutout==null||cutout.isRecycled())return null;
        if("none".equals(style)){Bitmap copy=cutout.copy(Bitmap.Config.ARGB_8888,false);if(copy==null)throw new OutOfMemoryError();return copy;}
        int radius=Math.max(1,Math.min(48,borderPx));
        boolean outer=!"paper".equals(style);
        float edge=radius+Math.max(4,radius*.65f),stroke=Math.max(2,radius*.23f);
        int padding=outer?(int)Math.ceil(edge+stroke+3):radius+3;
        int w=cutout.getWidth()+padding*2,h=cutout.getHeight()+padding*2,n=w*h;
        int[] colors=new int[n],distance=new int[n],row=new int[cutout.getWidth()];
        Arrays.fill(distance,1000000);
        for(int y=0;y<cutout.getHeight();y++){
            cutout.getPixels(row,0,row.length,0,y,row.length,1);
            for(int x=0;x<row.length;x++)if(Color.alpha(row[x])>24)distance[(y+padding)*w+x+padding]=0;
        }
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int at=y*w+x,d=distance[at];
            if(x>0)d=Math.min(d,distance[at-1]+3);
            if(y>0){d=Math.min(d,distance[at-w]+3);if(x>0)d=Math.min(d,distance[at-w-1]+4);if(x+1<w)d=Math.min(d,distance[at-w+1]+4);}
            distance[at]=d;
        }
        for(int y=h-1;y>=0;y--)for(int x=w-1;x>=0;x--){
            int at=y*w+x,d=distance[at];
            if(x+1<w)d=Math.min(d,distance[at+1]+3);
            if(y+1<h){d=Math.min(d,distance[at+w]+3);if(x>0)d=Math.min(d,distance[at+w-1]+4);if(x+1<w)d=Math.min(d,distance[at+w+1]+4);}
            distance[at]=d;
            int alpha=Math.max(0,Math.min(255,(radius*3+3-d)*255/3));
            colors[at]=(alpha<<24)|0x00ffffff;
        }
        Bitmap result=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        result.setPixels(colors,0,w,0,0,w,h);
        Canvas canvas=new Canvas(result);
        if(outer){
            Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(stroke);ink.setStrokeCap(Paint.Cap.ROUND);ink.setStrokeJoin(Paint.Join.ROUND);
            ink.setColor(night?Color.argb(165,201,180,153):Color.argb(115,145,121,101));
            if("dashed".equals(style))ink.setPathEffect(new DashPathEffect(new float[]{Math.max(5,radius),Math.max(4,radius*.7f)},0));
            else if("dots".equals(style))ink.setPathEffect(new DashPathEffect(new float[]{1,Math.max(4,radius*.7f)},0));
            canvas.drawPath(contour(distance,w,h,Math.round(edge*3)),ink);
            if("double".equals(style)){ink.setStrokeWidth(Math.max(1,stroke*.65f));canvas.drawPath(contour(distance,w,h,radius*3),ink);}
        }
        canvas.drawBitmap(cutout,padding,padding,new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG));
        return result;
    }
    /** Trace the expanded alpha boundary; dashes follow the subject, not a rectangle or ellipse. */
    private static Path contour(int[] distance,int w,int h,int limit){
        Map<Integer,ArrayDeque<Integer>> edges=new HashMap<>();int vertices=w+1;
        for(int y=1;y<h-1;y++)for(int x=1;x<w-1;x++){
            int at=y*w+x;if(distance[at]>limit)continue;
            int top=y*vertices+x,bottom=(y+1)*vertices+x;
            if(distance[at-w]>limit)add(edges,top,top+1);
            if(distance[at+1]>limit)add(edges,top+1,bottom+1);
            if(distance[at+w]>limit)add(edges,bottom+1,bottom);
            if(distance[at-1]>limit)add(edges,bottom,top);
        }
        Path path=new Path();
        while(!edges.isEmpty()){
            int start=edges.keySet().iterator().next(),at=start;path.moveTo(at%vertices,at/vertices);
            while(true){
                ArrayDeque<Integer> next=edges.get(at);if(next==null||next.isEmpty())break;
                int target=next.removeFirst();if(next.isEmpty())edges.remove(at);
                path.lineTo(target%vertices,target/vertices);at=target;
                if(at==start){path.close();break;}
            }
        }
        return path;
    }
    private static void add(Map<Integer,ArrayDeque<Integer>> edges,int from,int to){
        ArrayDeque<Integer> list=edges.get(from);if(list==null){list=new ArrayDeque<>();edges.put(from,list);}list.add(to);
    }
}
