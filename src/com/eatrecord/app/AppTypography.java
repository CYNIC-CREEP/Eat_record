package com.eatrecord.app;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import org.json.JSONObject;
import java.io.File;

/** Offline bundled default. The system face is an error/missing-glyph fallback, never the normal choice. */
public final class AppTypography {
    private static Typeface bundled,selected;
    private static String previousProfile="";
    private static float percent=1f;
    private AppTypography() { }
    public static synchronized Typeface defaultTypeface(Context c,boolean bold){
        if(bundled==null){try{bundled=c.getResources().getFont(R.font.xiaolai_regular);}catch(RuntimeException e){bundled=Typeface.DEFAULT;}}
        return Typeface.create(bundled,bold?Typeface.BOLD:Typeface.NORMAL);
    }
    public static synchronized Typeface typeface(Context c,boolean bold){
        String raw=c.getSharedPreferences(NutritionStore.PREFS,0).getString(NutritionStore.PROFILE,"{}");
        if(selected==null||!raw.equals(previousProfile)){
            previousProfile=raw;selected=defaultTypeface(c,false);percent=1f;
            try{
                JSONObject profile=new JSONObject(raw);String style=fontStyle(profile),file="";
                percent=Math.max(.7f,Math.min(1.4f,profile.optInt("font",100)/100f));
                if("yozai".equals(style))file="Yozai-Regular.ttf";
                else if("wenkai".equals(style))file="LXGWWenKaiLite-Regular.ttf";
                else if("marker".equals(style))file="LXGWMarkerGothic-Regular.ttf";
                else if("zcool".equals(style))file="ZCOOLKuaiLe-Regular.ttf";
                File downloaded=new File(new File(c.getFilesDir(),"downloaded_fonts"),file);
                if(!file.isEmpty()&&downloaded.isFile()&&downloaded.length()>=65536L)selected=Typeface.createFromFile(downloaded);
            }catch(Exception ignored){}
        }
        return Typeface.create(selected,bold?Typeface.BOLD:Typeface.NORMAL);
    }
    /** One selected face for the notebook and the rest of the application, including restored aliases. */
    public static String fontStyle(JSONObject profile){
        String style=profile==null?"xiaolai":profile.optString("bookFont","xiaolai");
        if("comic".equals(style)||"block".equals(style)||"scribble".equals(style)||"diary".equals(style))return "yozai";
        if("serif".equals(style)||"film".equals(style))return "wenkai";
        if("yozai".equals(style)||"wenkai".equals(style)||"marker".equals(style)||"zcool".equals(style))return style;
        return "xiaolai";
    }
    public static synchronized float sp(Context c,float size){typeface(c,false);return size*percent;}
    public static synchronized void invalidate(){selected=null;previousProfile="";}
    public static void apply(View view){
        if(view==null)return;
        if(view instanceof TextView){TextView text=(TextView)view;Typeface old=text.getTypeface();text.setTypeface(typeface(view.getContext(),old!=null&&old.isBold()));}
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)apply(group.getChildAt(i));}
    }
}
