package com.eatrecord.app;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.TextureView;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ScrollView;
import org.json.JSONObject;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** In-app viewfinder only. Analysis starts after an explicit capture or photo selection. */
public final class FoodCaptureActivity extends Activity {
    public static final String EXTRA_DATE="capture_date", EXTRA_MEAL="capture_meal", EXTRA_GALLERY="capture_gallery";
    private static final int CAMERA_PERMISSION=81, PICK_PHOTOS=82;
    private static final String STATE_PICKER="picker_open", STATE_INITIAL="initial_picker_opened", STATE_ASKED="asked_camera", STATE_OWNED="owned_output", STATE_CAMERA="camera_wanted";
    private final ExecutorService files=Executors.newSingleThreadExecutor();
    private MealCameraController camera;
    private TextureView texture;
    private TextView status,permission;
    private CaptureButton shutter,gallery;
    private boolean night,resumed,askedCamera,permissionInFlight,galleryInFlight,openedInitialPicker;
    private boolean cameraWanted=true,ready,busy,writing,delivered;
    private volatile boolean abandoned;
    private Uri ownedOutput;
    private String date,meal;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        setResult(RESULT_CANCELED);
        date=getIntent().getStringExtra(EXTRA_DATE);meal=getIntent().getStringExtra(EXTRA_MEAL);
        if(date==null||!date.matches("\\d{4}-\\d{2}-\\d{2}")||meal==null||!knownMeal(meal)){finish();return;}
        JSONObject profile=NutritionStore.read(getSharedPreferences(NutritionStore.PREFS,0),NutritionStore.PROFILE);
        UiTokens.configure(profile.optString("decoration","yellow"));
        night=AppearanceMode.isNight(profile,
                (android.content.res.Resources.getSystem().getConfiguration().uiMode&android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES);
        setTheme(night?R.style.AppNightTheme:R.style.AppDayTheme);
        if(state!=null){galleryInFlight=state.getBoolean(STATE_PICKER);openedInitialPicker=state.getBoolean(STATE_INITIAL);askedCamera=state.getBoolean(STATE_ASKED);String old=state.getString(STATE_OWNED);if(old!=null)deleteOwned(Uri.parse(old));}
        cameraWanted=state==null?!getIntent().getBooleanExtra(EXTRA_GALLERY,false):state.getBoolean(STATE_CAMERA,true);
        getWindow().setStatusBarColor(UiTokens.paper(night));getWindow().setNavigationBarColor(UiTokens.paper(night));
        if(Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);
        int flags=View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        if(!night)flags|=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        getWindow().getDecorView().setSystemUiVisibility(flags);
        buildPage();
        if(Build.VERSION.SDK_INT>=33)getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,()->cancel());
        camera=new MealCameraController(this,texture,new MealCameraController.Listener(){
            public void onReady(){if(abandoned||!resumed||galleryInFlight)return;ready=true;setBusy(false);setStatus("把完整餐盘放入画面，边缘留一点空隙");}
            public void onJpeg(byte[] jpeg){if(abandoned||delivered||jpeg==null)return;writeCapture(jpeg);}
            public void onError(String message){if(abandoned||delivered)return;ready=false;setBusy(false);setStatus(message+"，也可以从相册添加");permission.setVisibility(View.VISIBLE);permission.setText("重试相机");}
        });
        if(getIntent().getBooleanExtra(EXTRA_GALLERY,false)&&!openedInitialPicker){openedInitialPicker=true;cameraWanted=false;texture.post(()->openGallery());}
    }
    private static boolean knownMeal(String value){for(String key:NutritionStore.MEALS)if(key.equals(value))return true;return false;}
    private int dp(float value){return PaperComponents.dp(this,value);}
    private TextView text(String value,float size){return PaperComponents.text(this,value,size,UiTokens.ink(night),false);}
    private void buildPage(){
        LinearLayout page=new LinearLayout(this);page.setTag("food_capture_screen");page.setOrientation(LinearLayout.VERTICAL);page.setPadding(dp(20),dp(16),dp(20),dp(12));page.setBackground(new PaperComponents.Paper(this,night));
        setContentView(page);
        page.setOnApplyWindowInsetsListener((view,insets)->{
            int left,right,top,bottom;
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets safe=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());left=safe.left;right=safe.right;top=safe.top;bottom=safe.bottom;}
            else{left=insets.getSystemWindowInsetLeft();right=insets.getSystemWindowInsetRight();top=insets.getSystemWindowInsetTop();bottom=insets.getSystemWindowInsetBottom();
                if(Build.VERSION.SDK_INT>=28&&insets.getDisplayCutout()!=null){android.view.DisplayCutout cutout=insets.getDisplayCutout();left=Math.max(left,cutout.getSafeInsetLeft());right=Math.max(right,cutout.getSafeInsetRight());top=Math.max(top,cutout.getSafeInsetTop());bottom=Math.max(bottom,cutout.getSafeInsetBottom());}}
            view.setPadding(dp(20)+left,dp(16)+top,dp(20)+right,dp(12)+bottom);return insets;
        });page.post(()->page.requestApplyInsets());
        ScrollView scroll=new ScrollView(this);scroll.setTag("food_capture_body_scroll");scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);scroll.addView(body,new ScrollView.LayoutParams(-1,-2));page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        TextView title=text("添加"+DayFoodEntries.mealTitle(meal),20);title.setTag("food_capture_title");title.setGravity(Gravity.CENTER);title.setPadding(0,dp(8),0,dp(20));body.addView(title,new LinearLayout.LayoutParams(-1,-2));
        FrameLayout frame=new FrameLayout(this){@Override protected void onMeasure(int widthSpec,int heightSpec){int width=View.MeasureSpec.getSize(widthSpec);super.onMeasure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(Math.round(width*4f/3),View.MeasureSpec.EXACTLY));}};
        frame.setTag("food_camera_frame");frame.setBackground(PaperComponents.rounded(this,night));frame.setClipToOutline(true);
        texture=new TextureView(this);texture.setTag("food_camera_texture");texture.setContentDescription("实时相机取景");frame.addView(texture,new FrameLayout.LayoutParams(-1,-1));
        frame.addView(new GuideView(),new FrameLayout.LayoutParams(-1,-1));
        LinearLayout.LayoutParams previewLp=new LinearLayout.LayoutParams(-1,-2);previewLp.topMargin=dp(18);body.addView(frame,previewLp);
        status=text("正在开启相机…",12);status.setTag("food_capture_status");status.setGravity(Gravity.CENTER);status.setMaxLines(3);status.setPadding(dp(4),dp(16),dp(4),dp(4));body.addView(status,new LinearLayout.LayoutParams(-1,-2));
        permission=text("允许相机权限",13);permission.setTag("food_capture_permission");permission.setGravity(Gravity.CENTER);permission.setMinHeight(dp(48));permission.setOnClickListener(v->enableCamera(true));permission.setVisibility(View.GONE);body.addView(permission,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout controls=new LinearLayout(this);controls.setGravity(Gravity.CENTER_VERTICAL);controls.setPadding(0,dp(8),0,0);
        CaptureButton cancel=new CaptureButton(0);cancel.setTag("food_capture_cancel");cancel.setContentDescription("取消添加餐图");cancel.setOnClickListener(v->cancel());controls.addView(cancel,new LinearLayout.LayoutParams(dp(56),dp(64)));
        controls.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
        shutter=new CaptureButton(1);shutter.setTag("food_capture_shutter");shutter.setContentDescription("拍照并制作食物贴纸");shutter.setOnClickListener(v->{if(!ready){enableCamera(true);return;}if(busy)return;setBusy(true);setStatus("正在拍照…");camera.capture();});controls.addView(shutter,new LinearLayout.LayoutParams(dp(84),dp(84)));
        controls.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
        gallery=new CaptureButton(2);gallery.setTag("food_capture_gallery");gallery.setContentDescription("打开相册");gallery.setOnClickListener(v->openGallery());controls.addView(gallery,new LinearLayout.LayoutParams(dp(56),dp(64)));page.addView(controls,new LinearLayout.LayoutParams(-1,dp(100)));
    }
    @Override protected void onResume(){super.onResume();resumed=true;if(cameraWanted&&!galleryInFlight)enableCamera(false);}
    @Override protected void onPause(){resumed=false;ready=false;if(!writing)setBusy(false);if(camera!=null)camera.stop();super.onPause();}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putBoolean(STATE_CAMERA,cameraWanted);state.putBoolean(STATE_PICKER,galleryInFlight);state.putBoolean(STATE_INITIAL,openedInitialPicker);state.putBoolean(STATE_ASKED,askedCamera);if(ownedOutput!=null&&!delivered)state.putString(STATE_OWNED,ownedOutput.toString());}
    @Override protected void onDestroy(){abandoned=true;if(camera!=null)camera.stop();files.shutdownNow();if(!delivered&&ownedOutput!=null)deleteOwned(ownedOutput);super.onDestroy();}
    private void setStatus(String message){if(status!=null)status.setText(message);}
    private void setBusy(boolean value){busy=value;if(shutter!=null)shutter.setAlpha(value?.45f:1f);if(gallery!=null)gallery.setEnabled(!value);}
    private void enableCamera(boolean explicit){
        if(camera==null||abandoned||busy||galleryInFlight)return;cameraWanted=true;
        if(checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED){permission.setVisibility(View.GONE);if(resumed){setStatus("正在开启相机…");camera.start();}return;}
        ready=false;setStatus("相机需要你的授权；不授权也可以从相册添加");permission.setVisibility(View.VISIBLE);permission.setText("允许相机权限");
        if(!permissionInFlight&&(!askedCamera||explicit)){
            if(askedCamera&&explicit&&!shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)){
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));return;
            }
            askedCamera=true;permissionInFlight=true;requestPermissions(new String[]{Manifest.permission.CAMERA},CAMERA_PERMISSION);
        }
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grantResults){super.onRequestPermissionsResult(request,permissions,grantResults);if(request==CAMERA_PERMISSION){permissionInFlight=false;if(checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)enableCamera(false);else{setStatus("未开启相机权限，仍可从相册选择餐图");permission.setVisibility(View.VISIBLE);permission.setText("设置相机权限");}}}
    private void openGallery(){
        if(abandoned||busy||galleryInFlight)return;cameraWanted=false;ready=false;permission.setVisibility(View.GONE);if(camera!=null)camera.stop();
        Intent picker;
        if(Build.VERSION.SDK_INT>=33){picker=new Intent("android.provider.action.PICK_IMAGES");picker.setType("image/*");picker.putExtra("android.provider.extra.PICK_IMAGES_MAX",3);}
        else{picker=new Intent(Intent.ACTION_OPEN_DOCUMENT);picker.setType("image/*");picker.addCategory(Intent.CATEGORY_OPENABLE);picker.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);}
        picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);galleryInFlight=true;
        try{startActivityForResult(picker,PICK_PHOTOS);}
        catch(Exception first){try{Intent fallback=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivityForResult(fallback,PICK_PHOTOS);}catch(Exception error){galleryInFlight=false;setStatus("没有可用的相册选择器，请使用相机或稍后重试");}}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(request!=PICK_PHOTOS)return;galleryInFlight=false;
        if(result!=RESULT_OK||data==null){cameraWanted=checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED;setStatus("尚未选择图片，可重选相册或点快门开启相机");if(cameraWanted&&resumed)enableCamera(false);return;}
        ArrayList<Uri> selected=new ArrayList<>();ClipData clip=data.getClipData();
        if(clip!=null)for(int i=0;i<Math.min(3,clip.getItemCount());i++){Uri uri=clip.getItemAt(i).getUri();if(uri!=null&&!selected.contains(uri))selected.add(uri);}
        if(selected.isEmpty()&&data.getData()!=null)selected.add(data.getData());
        for(Uri uri:selected)try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
        if(!selected.isEmpty())deliver(selected);else setStatus("图片读取失败，请重新选择");
    }
    private void writeCapture(final byte[] jpeg){
        if(abandoned||delivered)return;setBusy(true);writing=true;ready=false;setStatus("正在准备照片…");
        try{
            final Uri output=MealCameraProvider.createOutputUri(this);ownedOutput=output;
            files.execute(()->{
                boolean saved=false;
                try(OutputStream stream=getContentResolver().openOutputStream(output,"w")){if(stream==null)throw new java.io.IOException();stream.write(jpeg);stream.flush();saved=true;}catch(Exception ignored){}
                final boolean success=saved;
                runOnUiThread(()->{writing=false;if(abandoned||isFinishing()||isDestroyed()){deleteOwned(output);return;}if(success){ArrayList<Uri> photos=new ArrayList<>();photos.add(output);deliver(photos);}else{deleteOwned(output);ownedOutput=null;setBusy(false);setStatus("照片保存失败，请重拍");enableCamera(false);}});
            });
        }catch(Exception error){writing=false;setBusy(false);setStatus("照片准备失败，请重试或从相册选择");}
    }
    private void deliver(List<Uri> photos){
        if(abandoned||delivered||photos.isEmpty())return;Intent data=new Intent().setData(photos.get(0)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        ClipData clip=ClipData.newRawUri("餐图",photos.get(0));for(int i=1;i<photos.size();i++)clip.addItem(new ClipData.Item(photos.get(i)));data.setClipData(clip);
        data.putExtra(EXTRA_DATE,date).putExtra(EXTRA_MEAL,meal);delivered=true;if(camera!=null)camera.stop();setResult(RESULT_OK,data);finish();
    }
    private void deleteOwned(Uri uri){if(uri==null||!MealCameraProvider.AUTHORITY.equals(uri.getAuthority()))return;try{getContentResolver().delete(uri,null,null);}catch(Exception ignored){}}
    private void cancel(){abandoned=true;if(camera!=null)camera.stop();setResult(RESULT_CANCELED);finish();}
    @Override public void onBackPressed(){cancel();}
    private final class GuideView extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        GuideView(){super(FoodCaptureActivity.this);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas canvas){paint.setColor(Color.argb(150,255,255,255));paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));paint.setPathEffect(new DashPathEffect(new float[]{dp(9),dp(8)},0));float x=getWidth()*.11f,y=getHeight()*.13f;canvas.drawRoundRect(new RectF(x,y,getWidth()-x,getHeight()-y),dp(26),dp(26),paint);}
    }
    private final class CaptureButton extends View {
        private final int type;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        CaptureButton(int type){super(FoodCaptureActivity.this);this.type=type;setClickable(true);setFocusable(true);}
        @Override protected void onDraw(Canvas canvas){float cx=getWidth()/2f,cy=getHeight()/2f;int ink=UiTokens.action(night);paint.setColor(type==1?ink:UiTokens.card(night));paint.setStyle(Paint.Style.FILL);canvas.drawCircle(cx,cy,dp(type==1?32:24),paint);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(type==1?2:2.4f));paint.setColor(type==1?UiTokens.line(night):UiTokens.ink(night));if(type==1){canvas.drawCircle(cx,cy,dp(38),paint);}else if(type==0){canvas.drawLine(cx-dp(6),cy-dp(6),cx+dp(6),cy+dp(6),paint);canvas.drawLine(cx+dp(6),cy-dp(6),cx-dp(6),cy+dp(6),paint);}else{canvas.drawRoundRect(new RectF(cx-dp(10),cy-dp(8),cx+dp(10),cy+dp(8)),dp(3),dp(3),paint);canvas.drawCircle(cx-dp(4),cy-dp(2),dp(1.5f),paint);canvas.drawLine(cx-dp(7),cy+dp(7),cx+dp(1),cy-dp(1),paint);canvas.drawLine(cx+dp(1),cy-dp(1),cx+dp(7),cy+dp(6),paint);}paint.setStyle(Paint.Style.FILL);}
    }
}
