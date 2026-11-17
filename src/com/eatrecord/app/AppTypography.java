package com.eatrecord.app;

import android.content.Context;
import android.graphics.Typeface;
import android.os.FileObserver;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import org.json.JSONObject;
import java.io.File;

/** Offline bundled default. The system face is an error/missing-glyph fallback, never the normal choice. */
public final class AppTypography {
    private static Typeface bundled,bundledBold,selected,selectedBold;
    private static String previousProfile="",selectedStyle="xiaolai",filesPath="";
    private static float percent=1f;
    private static boolean sourceDirty=true;
    private static File fontsDirectory;
    private static DirectoryWatch.Subscription filesObserver;
    private static FileObserver fontObserver;
    private static final int FILE_EVENTS=FileObserver.CLOSE_WRITE|FileObserver.CREATE|FileObserver.DELETE
            |FileObserver.MOVED_FROM|FileObserver.MOVED_TO|FileObserver.DELETE_SELF|FileObserver.MOVE_SELF;
    private AppTypography() { }
    public static synchronized Typeface defaultTypeface(Context c,boolean bold){
        if(bundled==null){
            try{bundled=c.getResources().getFont(R.font.xiaolai_regular);}
            catch(RuntimeException e){bundled=Typeface.DEFAULT;}
            bundledBold=Typeface.create(bundled,Typeface.BOLD);
        }
        return bold?bundledBold:bundled;
    }
    public static synchronized Typeface typeface(Context c,boolean bold){
        String raw=c.getSharedPreferences(NutritionStore.PREFS,0).getString(NutritionStore.PROFILE,"{}");
        if(!raw.equals(previousProfile)){
            previousProfile=raw;
            String style="xiaolai";percent=1f;
            try{
                JSONObject profile=new JSONObject(raw);style=fontStyle(profile);
                percent=Math.max(.7f,Math.min(1.4f,profile.optInt("font",100)/100f));
            }catch(Exception ignored){}
            if(!style.equals(selectedStyle)){selectedStyle=style;sourceDirty=true;}
        }
        observeFontDirectory(c);
        if(selected==null||sourceDirty){
            selected=defaultTypeface(c,false);selectedBold=defaultTypeface(c,true);
            String file=fontFile(selectedStyle);
            if(!file.isEmpty()){
                File downloaded=new File(fontsDirectory,file);
                try{
                    if(downloaded.isFile()&&downloaded.length()>=65536L){
                        selected=Typeface.createFromFile(downloaded);
                        selectedBold=Typeface.create(selected,Typeface.BOLD);
                    }
                }catch(RuntimeException unavailable){selected=defaultTypeface(c,false);selectedBold=defaultTypeface(c,true);}
            }
            sourceDirty=false;
        }
        return bold?selectedBold:selected;
    }

    private static String fontFile(String style){
        if("yozai".equals(style))return "Yozai-Regular.ttf";
        if("wenkai".equals(style))return "LXGWWenKaiLite-Regular.ttf";
        if("marker".equals(style))return "LXGWMarkerGothic-Regular.ttf";
        if("zcool".equals(style))return "ZCOOLKuaiLe-Regular.ttf";
        return "";
    }

    /** Watches file generations without statting or reopening a font on every text/style lookup. */
    private static void observeFontDirectory(Context context){
        File files=context.getFilesDir();
        if(files.getAbsolutePath().equals(filesPath))return;
        if(filesObserver!=null)filesObserver.close();
        if(fontObserver!=null)fontObserver.stopWatching();
        filesPath=files.getAbsolutePath();fontsDirectory=new File(files,"downloaded_fonts");
        sourceDirty=true;
        filesObserver=DirectoryWatch.subscribe(files,(event,name)->{
            if("downloaded_fonts".equals(name))synchronized(AppTypography.class){
                sourceDirty=true;watchFontFiles();
            }
        });
        watchFontFiles();
    }

    private static void watchFontFiles(){
        if(fontObserver!=null)fontObserver.stopWatching();
        fontObserver=null;
        if(fontsDirectory==null||!fontsDirectory.isDirectory())return;
        fontObserver=new FileObserver(fontsDirectory.getAbsolutePath(),FILE_EVENTS){
            @Override public void onEvent(int event,String name){
                synchronized(AppTypography.class){
                    if(name==null||fontFile(selectedStyle).equals(name))sourceDirty=true;
                }
            }
        };
        fontObserver.startWatching();
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
    public static synchronized void invalidate(){sourceDirty=true;previousProfile="";watchFontFiles();}
    /** Downloading an unselected face keeps the current regular/bold pair warm. */
    public static synchronized void invalidate(String style){
        if(style.equals(selectedStyle))sourceDirty=true;
        watchFontFiles();
    }
    public static void apply(View view){
        if(view==null)return;
        if(view instanceof TextView){TextView text=(TextView)view;Typeface old=text.getTypeface();text.setTypeface(typeface(view.getContext(),old!=null&&old.isBold()));}
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)apply(group.getChildAt(i));}
    }
}
