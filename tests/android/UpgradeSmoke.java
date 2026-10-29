package com.eatrecord.app;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.Bundle;
import android.view.*;
import android.widget.FrameLayout;
import org.json.*;
import java.io.*;
import java.lang.reflect.Method;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Included only by build.ps1 -Qa, never in the distributable APK. */
public final class UpgradeSmoke extends Instrumentation {
    private MainActivity gestureScreenV20;
    private int localPointerFallbacksV20;
    private Bundle args;
    private static final java.util.concurrent.atomic.AtomicInteger apiCalls=new java.util.concurrent.atomic.AtomicInteger();
    private static volatile boolean holdResponse;
    private static final java.util.concurrent.CountDownLatch responseEntered=new java.util.concurrent.CountDownLatch(1);
    private static final java.util.concurrent.CountDownLatch responseReleased=new java.util.concurrent.CountDownLatch(1);
    private static volatile boolean streamName=true;
    private static volatile boolean streamTotalsFirst;
    private static volatile boolean sawNameHint,sawCorrectedName,sameHintImage=true;
    private static volatile java.util.function.Consumer<JSONObject> requestObserverV17;
    private static volatile boolean deepSeekV18;
    private static String originalImageDigest;
    private static final java.util.concurrent.CountDownLatch nutritionReleased=new java.util.concurrent.CountDownLatch(1);
    private static final String RESPONSE="{\"schema_version\":1,\"dish_name\":\"牛肉蛋炒饭（测试数据）\",\"items\":[{\"name\":\"炒饭\",\"estimated_grams\":450,\"calories_kcal\":676,\"carbs_g\":88,\"protein_g\":24,\"fat_g\":24,\"fiber_g\":3,\"sodium_mg\":1131,\"confidence\":0.76}],\"totals\":{\"calories_kcal\":676,\"carbs_g\":88,\"protein_g\":24,\"fat_g\":24,\"fiber_g\":3,\"sodium_mg\":1131},\"calorie_range\":[520,820],\"assumptions\":[\"QA 夹具，不是真实营养识别\"],\"confidence\":0.76}";
    /** Process-local QA transport only; never compiled into a normal APK. */
    private void installTransport(){
        URL.setURLStreamHandlerFactory(protocol->!"https".equals(protocol)?null:new URLStreamHandler(){
            @Override protected URLConnection openConnection(URL url)throws IOException {
                if(!"food.qa.invalid".equals(url.getHost())&&!(deepSeekV18&&"api.deepseek.com".equals(url.getHost())))throw new IOException("QA blocks external requests");
                return new HttpURLConnection(url){
                    private final ByteArrayOutputStream request=new ByteArrayOutputStream();
                    private boolean hinted;
                    @Override public void connect(){} @Override public void disconnect(){} @Override public boolean usingProxy(){return false;}
                    @Override public OutputStream getOutputStream(){return request;}
                    @Override public int getResponseCode()throws IOException {
                        if(deepSeekV18&&"GET".equals(getRequestMethod()))return 200;
                        try {
                            JSONObject body=new JSONObject(request.toString("UTF-8"));
                            if(deepSeekV18&&(!"deepseek-flash".equals(body.optString("model"))
                                ||body.optJSONObject("response_format")==null||!"json_object".equals(body.getJSONObject("response_format").optString("type"))))return 400;
                            if(deepSeekV18&&!"disabled".equals(body.getJSONObject("thinking").optString("type")))throw new IOException("QA official fast mode not explicitly disabled");
                            JSONArray parts=body.getJSONArray("messages").getJSONObject(0).getJSONArray("content");
                            if(!parts.getJSONObject(1).getJSONObject("image_url").getString("url").startsWith("data:image/jpeg;base64,"))throw new IOException("Missing photo");
                            hinted=parts.getJSONObject(0).optString("text").contains("用户更正的食物名称");
                            sawCorrectedName|=parts.getJSONObject(0).optString("text").contains(JSONObject.quote("鸡蛋青菜汤面（测试改名）"));
                            try{String digest=java.util.Arrays.toString(java.security.MessageDigest.getInstance("SHA-256").digest(parts.getJSONObject(1).getJSONObject("image_url").getString("url").getBytes("UTF-8")));if(originalImageDigest==null)originalImageDigest=digest;if(hinted){sawNameHint=true;sameHintImage&=digest.equals(originalImageDigest);}}catch(Exception error){throw new IOException("QA image digest",error);}
                            if(!"Bearer qa-not-a-real-key".equals(getRequestProperty("Authorization")))throw new IOException("Missing auth");
                            if(streamName&&!body.optBoolean("stream"))throw new IOException("Missing streaming request");
                        }catch(JSONException e){throw new IOException(e);}
                        apiCalls.incrementAndGet();
                        java.util.function.Consumer<JSONObject> observer=requestObserverV17;
                        if(observer!=null)try{observer.accept(new JSONObject(request.toString("UTF-8")));}catch(Exception error){throw new IOException("QA request observer",error);}
                        return 200;
                    }
                    @Override public InputStream getInputStream()throws IOException {
                        if(deepSeekV18&&"GET".equals(getRequestMethod()))return new ByteArrayInputStream(("{\"data\":[{\"id\":\"deepseek-flash\",\"name\":\"DeepSeek-V4.1-Flash\",\"input_modalities\":[\"text\",\"image\"]},{\"id\":\"deepseek-v4-pro\",\"name\":\"DeepSeek-V4-Pro\",\"input_modalities\":[\"text\"]}]}").getBytes("UTF-8"));
                        String reply=hinted?RESPONSE.replace("牛肉蛋炒饭（测试数据）","服务端另起的菜名（测试）").replace("676","500").replace("\"carbs_g\":88","\"carbs_g\":60").replace("\"protein_g\":24","\"protein_g\":20").replace("\"fat_g\":24","\"fat_g\":20").replace("[520,820]","[400,650]"):RESPONSE;
                        if(streamTotalsFirst)try{
                            JSONObject value=new JSONObject(reply);
                            reply="{\"schema_version\":1,\"dish_name\":"+JSONObject.quote(value.getString("dish_name"))+",\"totals\":"+value.getJSONObject("totals")
                                +",\"items\":"+value.getJSONArray("items")+",\"calorie_range\":"+value.getJSONArray("calorie_range")+",\"assumptions\":"+value.getJSONArray("assumptions")+",\"confidence\":"+value.getDouble("confidence")+"}";
                        }catch(JSONException error){throw new IOException(error);}
                        if(holdResponse){
                            responseEntered.countDown();
                            try{if(!responseReleased.await(30,java.util.concurrent.TimeUnit.SECONDS))throw new IOException("QA response release timed out");}
                            catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException(e);}
                        }
                        if(streamName){
                            try{
                                int split=reply.indexOf(",\"items\"");
                                final byte[] prefix=event(reply.substring(0,split)).getBytes("UTF-8");
                                String end=streamTotalsFirst?"data: "+new JSONObject().put("model","qa-returned-vision").put("choices",new JSONArray().put(new JSONObject().put("delta",new JSONObject()).put("finish_reason","stop")))+"\n\n":"data: [DONE]\n\n";
                                final byte[] suffix=(event(reply.substring(split))+end).getBytes("UTF-8");
                                return new InputStream(){
                                    private byte[] bytes=prefix;private int index;private boolean tail;
                                    @Override public int read()throws IOException {byte[] one=new byte[1];return read(one,0,1)<0?-1:one[0]&255;}
                                    @Override public int read(byte[] buffer,int offset,int count)throws IOException {
                                        if(index==bytes.length){
                                            if(tail){if(streamTotalsFirst)throw new IOException("QA finished SSE kept alive; must not read after stop");return -1;}
                                            try{if(!nutritionReleased.await(30,java.util.concurrent.TimeUnit.SECONDS))throw new IOException("QA nutrient stream release timed out");}
                                            catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException(e);}
                                            bytes=suffix;index=0;tail=true;
                                        }
                                        int length=Math.min(count,bytes.length-index);System.arraycopy(bytes,index,buffer,offset,length);index+=length;return length;
                                    }
                                };
                            }catch(JSONException e){throw new IOException(e);}
                        }
                        try{return new ByteArrayInputStream(new JSONObject().put("model",deepSeekV18?"deepseek-flash":"qa-returned-vision").put("choices",new JSONArray().put(new JSONObject().put("message",new JSONObject().put("content",reply)))).toString().getBytes("UTF-8"));}
                        catch(JSONException e){throw new IOException(e);}
                    }
                    @Override public InputStream getErrorStream(){try{return new ByteArrayInputStream("{\"error\":{\"message\":\"unknown variant json_schema, expected text or json_object\",\"param\":\"response_format.type\"}}".getBytes("UTF-8"));}catch(Exception ignored){return null;}}
                };
            }
        });
    }
    private static String event(String delta)throws JSONException {
        return "data: "+new JSONObject().put("model","qa-returned-vision").put("choices",new JSONArray().put(new JSONObject().put("delta",new JSONObject().put("content",delta))))+"\n\n";
    }
    @Override public void onCreate(Bundle arguments){args=arguments;start();}
    private void cutoutQuality(Context c)throws Exception {
        File dir=new File(c.getExternalFilesDir(null),"qa");dir.mkdirs();
        for(String name:new String[]{"noodles","blue"}) {
            Bitmap source=BitmapFactory.decodeFile(new File(dir,"input-"+name+".jpg").getAbsolutePath());
            if(source==null)throw new Exception("Missing local quality fixture: "+name);
            long started=android.os.SystemClock.elapsedRealtime();
            Bitmap cutout=FoodCutoutEngine.create(c,source);long ms=android.os.SystemClock.elapsedRealtime()-started;
            Rect bounds=FoodCutoutEngine.lastBounds();String model=FoodCutoutEngine.lastModel();
            int width=source.getWidth(),height=source.getHeight();float ratio=Math.min(1,1024f/Math.max(width,height));
            Bitmap full=Bitmap.createBitmap(Math.round(width*ratio),Math.round(height*ratio),Bitmap.Config.ARGB_8888);
            new Canvas(full).drawBitmap(cutout,null,bounds,new Paint(Paint.FILTER_BITMAP_FLAG));
            if("noodles".equals(name)) {
                if(!model.contains("EdgeTAM"))throw new Exception("White plate was not refined: "+model);
                for(float[] point:new float[][]{{.5f,.19f},{.13f,.49f},{.5f,.83f},{.5f,.5f}})
                    if(Color.alpha(full.getPixel(Math.round(full.getWidth()*point[0]),Math.round(full.getHeight()*point[1])))<200)
                        throw new Exception("Lost white plate/food at "+java.util.Arrays.toString(point));
            }
            if(Color.alpha(full.getPixel(2,2))>24||Color.alpha(full.getPixel(full.getWidth()-3,full.getHeight()-3))>24)
                throw new Exception("Kept image corner background: "+name);
            try(FileOutputStream out=new FileOutputStream(new File(dir,"beta8-"+name+"-cutout.png"))){full.compress(Bitmap.CompressFormat.PNG,100,out);}
            Bitmap preview=Bitmap.createBitmap(full.getWidth(),full.getHeight(),Bitmap.Config.ARGB_8888);Canvas paper=new Canvas(preview);paper.drawColor(Color.rgb(249,246,238));paper.drawBitmap(full,0,0,null);
            try(FileOutputStream out=new FileOutputStream(new File(dir,"beta8-"+name+"-preview.png"))){preview.compress(Bitmap.CompressFormat.PNG,100,out);}preview.recycle();
            android.os.Debug.MemoryInfo info=new android.os.Debug.MemoryInfo();android.os.Debug.getMemoryInfo(info);
            String peak="unavailable";try(BufferedReader reader=new BufferedReader(new FileReader("/proc/self/status"))){String line;while((line=reader.readLine())!=null)if(line.startsWith("VmHWM:")){peak=line;break;}}
            Bundle status=new Bundle();status.putString("stream",name+": "+model+", "+ms+"ms, post-inference PSS "+info.getTotalPss()+"KiB (not peak); process peak RSS "+peak+"\n");sendStatus(0,status);
            source.recycle();cutout.recycle();full.recycle();
        }
        Bundle result=new Bundle();result.putString("stream","PASS: actual ARM64 JNI for white plate noodles and blue plate, white plate retained, background corners transparent; original local photos, no API/network or fabricated nutrition\n");finish(Activity.RESULT_OK,result);
    }
    private Object invoke(MainActivity a,String method,Class<?>[] types,Object... values)throws Exception{
        Method m=MainActivity.class.getDeclaredMethod(method,types);m.setAccessible(true);return m.invoke(a,values);
    }
    private void screenshot(String name)throws Exception {
        waitForIdleSync();Thread.sleep(350);
        Bitmap screen=getUiAutomation().takeScreenshot();
        if(screen==null)throw new Exception("screenshot unavailable");
        File dir=new File(getTargetContext().getExternalFilesDir(null),"qa");dir.mkdirs();
        try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){screen.compress(Bitmap.CompressFormat.PNG,100,out);}
        screen.recycle();
    }
    private Object field(Object target,String name)throws Exception {
        java.lang.reflect.Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(target);
    }
    private android.view.accessibility.AccessibilityNodeInfo node(String text){
        android.view.accessibility.AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();
        if(root==null)return null;
        for(android.view.accessibility.AccessibilityNodeInfo n:root.findAccessibilityNodeInfosByText(text))if(text.contentEquals(n.getText()==null?"":n.getText()))return n;
        return null;
    }
    private void click(String text)throws Exception {
        waitForIdleSync();long deadline=android.os.SystemClock.elapsedRealtime()+3000;
        do{
            android.view.accessibility.AccessibilityNodeInfo n=node(text);
            if(n!=null&&n.isVisibleToUser()&&n.isEnabled()&&n.isClickable()&&n.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)){waitForIdleSync();return;}
            Thread.sleep(100);
        }while(android.os.SystemClock.elapsedRealtime()<deadline);
        if(clickLocalTextV20(null,text))return;
        throw new Exception("Missing/cannot click "+text+" after window transition");
    }
    private android.view.accessibility.AccessibilityNodeInfo descriptionNode(android.view.accessibility.AccessibilityNodeInfo current,String description){
        if(current==null)return null;
        if(description.contentEquals(current.getContentDescription()==null?"":current.getContentDescription()))return current;
        for(int i=0;i<current.getChildCount();i++){
            android.view.accessibility.AccessibilityNodeInfo match=descriptionNode(current.getChild(i),description);
            if(match!=null)return match;
        }
        return null;
    }
    private android.view.accessibility.AccessibilityNodeInfo descriptionNode(String description){
        android.view.accessibility.AccessibilityNodeInfo found=descriptionNode(getUiAutomation().getRootInActiveWindow(),description);
        if(found!=null)return found;
        final android.view.accessibility.AccessibilityNodeInfo[] local={null};runOnMainSync(()->{for(View root:android.view.inspector.WindowInspector.getGlobalWindowViews()){List<View> views=new ArrayList<>();collectViews(root,views);for(View view:views)if(description.contentEquals(view.getContentDescription()==null?"":view.getContentDescription())&&view.isShown()&&view.getGlobalVisibleRect(new Rect()))local[0]=view.createAccessibilityNodeInfo();}});return local[0];
    }
    private void clickDescription(String description)throws Exception {
        waitForIdleSync();long deadline=android.os.SystemClock.elapsedRealtime()+3000;
        do{
            android.view.accessibility.AccessibilityNodeInfo found=descriptionNode(description);
            if(found!=null&&found.isVisibleToUser()&&found.isEnabled()&&found.isClickable()&&found.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)){waitForIdleSync();return;}
            Thread.sleep(100);
        }while(android.os.SystemClock.elapsedRealtime()<deadline);
        final boolean[] localClicked={false};uiAssert(()->{List<View> roots=android.view.inspector.WindowInspector.getGlobalWindowViews();for(int i=roots.size()-1;i>=0&&!localClicked[0];i--){List<View> views=new ArrayList<>();collectViews(roots.get(i),views);for(View view:views)if(description.contentEquals(view.getContentDescription()==null?"":view.getContentDescription())&&view.isShown()&&view.getGlobalVisibleRect(new Rect())){View target=view;while(target!=null){if(target.isClickable()&&target.performClick()){localClicked[0]=true;return;}target=target.getParent() instanceof View?(View)target.getParent():null;}}}});
        if(localClicked[0])return;
        throw new Exception("Missing/cannot click description "+description+" after window transition");
    }
    private String draftSource(Context c,String date,String meal)throws Exception {
        long start=android.os.SystemClock.elapsedRealtime();
        do{
            for(String source:FoodDraftStore.pendingSources(c)){
                JSONObject item=FoodDraftStore.item(c,source);
                if(item!=null&&date.equals(item.optString("draftDate"))&&meal.equals(item.optString("draftMeal")))return source;
            }
            Thread.sleep(100);
        }while(android.os.SystemClock.elapsedRealtime()-start<15000);
        throw new Exception("Missing persisted draft for "+meal);
    }
    private JSONObject waitDraft(Context c,String source,String status,long timeout)throws Exception {
        long start=android.os.SystemClock.elapsedRealtime();JSONObject item;
        do{
            item=FoodDraftStore.item(c,source);
            if(item!=null&&status.equals(item.optString("analysisStatus")))return item;
            Thread.sleep(250);
        }while(android.os.SystemClock.elapsedRealtime()-start<timeout);
        throw new Exception("Draft did not reach "+status+": "+item);
    }
    private JSONObject waitDraftName(Context c,String source,long timeout)throws Exception {
        long start=android.os.SystemClock.elapsedRealtime();JSONObject item;
        do{
            item=FoodDraftStore.item(c,source);
            if(item!=null&&"牛肉蛋炒饭（测试数据）".equals(item.optString("dishName")))return item;
            Thread.sleep(100);
        }while(android.os.SystemClock.elapsedRealtime()-start<timeout);
        throw new Exception("Stream did not reveal a preliminary dish name: "+item);
    }
    private void assertNoMealPhoto(android.content.SharedPreferences prefs,String date,String meal,String source)throws Exception {
        JSONObject value=NutritionStore.meal(NutritionStore.read(prefs,NutritionStore.DAYS),date,meal);
        if(NutritionStore.containsPhoto(value,source)||NutritionStore.forSource(value,source).length()!=0)throw new Exception("Unconfirmed photo leaked into home records");
    }
    private void stickerBorder()throws Exception {
        Bitmap alpha=Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888);
        Paint paint=new Paint();paint.setColor(Color.RED);new Canvas(alpha).drawRect(4,4,12,12,paint);
        Bitmap paper=FoodStickerRenderer.create(alpha,3);
        try{
            if(paper==null||paper.getWidth()!=28||paper.getHeight()!=28)throw new Exception("Sticker border missing transparent padding");
            if(Color.alpha(paper.getPixel(0,0))!=0||paper.getPixel(8,14)!=Color.WHITE||paper.getPixel(14,14)!=Color.RED)throw new Exception("Sticker border is not white alpha silhouette with intact subject");
            if(alpha.isRecycled()||Color.alpha(alpha.getPixel(2,8))!=0)throw new Exception("Display border modified editable alpha source");
        }finally{if(paper!=null)paper.recycle();alpha.recycle();}
    }
    private void borderVariants()throws Exception {
        Bitmap alpha=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888);Paint brush=new Paint();brush.setColor(Color.RED);new Canvas(alpha).drawCircle(16,16,9,brush);
        try{
            for(String style:FoodStickerRenderer.STYLES){
                Bitmap result=FoodStickerRenderer.create(alpha,5,style,false);
                try{
                    if(result==null||Color.alpha(result.getPixel(0,0))!=0)throw new Exception("Border variant loses transparent margin: "+style);
                    if("none".equals(style)){if(result.getWidth()!=32||result.getPixel(16,16)!=Color.RED)throw new Exception("No-border variant changed source");}
                    else if(!"paper".equals(style)){
                        int ink=0;for(int y=0;y<result.getHeight();y++)for(int x=0;x<result.getWidth();x++){int pixel=result.getPixel(x,y),rgb=pixel&0xffffff;if(Color.alpha(pixel)>10&&rgb!=0xffffff&&rgb!=0xff0000)ink++;}
                        if(ink==0)throw new Exception("Border variant is missing contour ink: "+style);
                    }
                }finally{if(result!=null)result.recycle();}
            }
            if(alpha.isRecycled()||alpha.getPixel(16,16)!=Color.RED)throw new Exception("Border choices damaged editable source");
        }finally{alpha.recycle();}
    }
    private android.view.accessibility.AccessibilityNodeInfo scrollable(android.view.accessibility.AccessibilityNodeInfo root){
        if(root==null)return null;if(root.isScrollable())return root;for(int i=0;i<root.getChildCount();i++){android.view.accessibility.AccessibilityNodeInfo child=scrollable(root.getChild(i));if(child!=null)return child;}return null;
    }
    private void clickSetting(String title)throws Exception {
        for(int i=0;i<8;i++){
            waitForIdleSync();android.view.accessibility.AccessibilityNodeInfo match=node(title);
            if(match!=null&&match.isVisibleToUser()){
                while(match!=null&&!match.isClickable())match=match.getParent();
                if(match!=null&&match.isEnabled()&&match.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)){Thread.sleep(400);waitForIdleSync();return;}
            }
            android.view.accessibility.AccessibilityNodeInfo list=scrollable(getUiAutomation().getRootInActiveWindow());
            if(list!=null)list.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);Thread.sleep(180);
        }throw new Exception("Setting is not reachable: "+title);
    }
    private void settingsScenario(Context c,android.content.SharedPreferences prefs)throws Exception {
        FoodAnalysisJobService.cancelAll(c);borderVariants();
        JSONObject profile=new JSONObject().put("new_ui_v2",true).put("seenAnnouncementVersion",c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName);
        prefs.edit().putString(NutritionStore.PROFILE,profile.toString()).putString(NutritionStore.DAYS,"{}").commit();
        MainActivity screen=(MainActivity)startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        if(AppTypography.defaultTypeface(c,false).equals(Typeface.DEFAULT))throw new Exception("Bundled default font fell back to the phone font");
        final Throwable[] error={null};runOnMainSync(()->{try{
            invoke(screen,"showTab",new Class[]{int.class,boolean.class},3,false);
            if(!Boolean.TRUE.equals(invoke(screen,"downloadedBookFontReady",new Class[]{String.class},"xiaolai")))throw new Exception("Bundled font not available offline");
        }catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new Exception(error[0]);screenshot("beta5-settings-me");
        if(node("个性化")==null||node("其他设置")==null||node("字体大小")!=null||node("AI 与餐图识别")!=null||node("三餐提醒")!=null)throw new Exception("My-page settings hierarchy is not simplified");
        clickSetting("个性化");screenshot("beta5-settings-personalization");
        if(node("字体大小")==null||node("食物贴纸边框")==null)throw new Exception("Personalization lacks font size or borders");
        clickSetting("食物贴纸边框");click("点点纸边");
        if(!"dots".equals(NutritionStore.read(prefs,NutritionStore.PROFILE).optString("foodStickerBorder")))throw new Exception("Border preference did not persist");
        clickSetting("字体大小");click("确认");Thread.sleep(650);screenshot("beta5-settings-after-size");
        if(!Boolean.TRUE.equals(field(screen,"inThemeSettingsPage")))throw new Exception("Font-size confirmation left personalization unexpectedly");
        sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);waitForIdleSync();Thread.sleep(650);screenshot("beta5-settings-after-back");clickSetting("其他设置");screenshot("beta5-settings-other");
        uiAssert(()->assertSettingsRoot(screen,true));
        clickSetting("三餐提醒");screenshot("beta12-settings-reminders");
        if(node("三餐通知")==null||node("早餐提醒")==null||node("午餐提醒")==null||node("晚餐提醒")==null)throw new Exception("Dedicated reminder settings are incomplete");
        if(!"{}".equals(prefs.getString(NutritionStore.DAYS,"{}")))throw new Exception("Settings navigation changed meal records");
        profile=NutritionStore.read(prefs,NutritionStore.PROFILE);profile.put("foodStickerBorder","dashed");prefs.edit().putString(NutritionStore.PROFILE,profile.toString()).commit();
        Bundle result=new Bundle();result.putString("stream","PASS: bundled handwriting available offline, five alpha-preserving border variants, simplified My page, personalization font/border options, saved border choice, font-size stays in personalization, direct AI settings and dedicated global reminder entry without Other Features, no diary writes\n");finish(Activity.RESULT_OK,result);
    }
    private View setting(View decor,String title){
        View found=decor.findViewWithTag("setting_row_"+title);
        return found==null?decor.findViewWithTag("setting_toggle_"+title):found;
    }
    private void assertSettingPresence(View decor,String title,boolean present)throws Exception {
        if((setting(decor,title)!=null)!=present)throw new Exception("Wrong mode visibility for setting "+title+": expected "+present);
    }
    private void assertUnderSection(View decor,String title,String section)throws Exception {
        View row=setting(decor,title),header=decor.findViewWithTag("settings_section_"+section);
        if(row==null||header==null||row.getParent()!=header.getParent())throw new Exception("Setting not directly under section "+section+": "+title);
        ViewGroup page=(ViewGroup)row.getParent();int index=page.indexOfChild(row),start=page.indexOfChild(header);
        if(index<=start)throw new Exception("Setting precedes section: "+title);
        for(int i=start+1;i<index;i++)if(page.getChildAt(i).getTag() instanceof String&&((String)page.getChildAt(i).getTag()).startsWith("settings_section_"))throw new Exception("Setting is in another section: "+title);
    }
    private void assertSettingsRoot(MainActivity screen,boolean modern)throws Exception {
        View decor=screen.getWindow().getDecorView();
        assertSettingPresence(decor,"其他功能",false);
        assertSettingPresence(decor,modern?"旧版入口":"新版入口",true);
        assertSettingPresence(decor,modern?"新版入口":"旧版入口",false);
        assertUnderSection(decor,"三餐提醒","全局");
        View reminder=setting(decor,"三餐提醒");ViewGroup page=(ViewGroup)reminder.getParent();
        for(int i=page.indexOfChild(reminder)+1;i<page.getChildCount();i++){
            Object tag=page.getChildAt(i).getTag();if(tag instanceof String&&((String)tag).startsWith("settings_section_"))break;
            if(tag instanceof String&&(((String)tag).startsWith("setting_row_")||((String)tag).startsWith("setting_toggle_")))throw new Exception("Reminders are not the last global button");
        }
        for(String title:new String[]{"调用 API","AI分析","自动分析","食物识别","AI读取记录","开启记忆","自动识别热量"})assertUnderSection(decor,title,"其他");
        assertSettingPresence(decor,"餐次管理",!modern);
        assertSettingPresence(decor,"实验性功能",false);assertSettingPresence(decor,"餐图识别与隐私",modern);
        if(modern)assertUnderSection(decor,"餐图识别与隐私","其他");
        if(!Boolean.TRUE.equals(field(screen,"inPreferencesPage"))||Boolean.TRUE.equals(field(screen,"inMoreSettingsPage")))throw new Exception("Root setting route still targets removed Other Features");
    }
    private void settingsRoot(MainActivity screen)throws Exception {
        uiAssert(()->invoke(screen,"renderPreferencesPage",new Class[]{int.class},0));waitForIdleSync();Thread.sleep(250);
    }
    private void cleanGlyph(View icon,Context c)throws Exception {
        if(icon instanceof android.widget.TextView)throw new Exception("Settings icon is still a font/emoji glyph");
        int side=PaperComponents.dp(c,36);if(icon.getWidth()==0||icon.getHeight()==0){icon.measure(View.MeasureSpec.makeMeasureSpec(side,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(side,View.MeasureSpec.EXACTLY));icon.layout(0,0,side,side);}else side=icon.getWidth();
        Bitmap bitmap=Bitmap.createBitmap(side,side,Bitmap.Config.ARGB_8888);icon.draw(new Canvas(bitmap));
        try{
            int painted=0;for(int y=0;y<side;y++)for(int x=0;x<side;x++)if(Color.alpha(bitmap.getPixel(x,y))>32)painted++;
            if(painted==0||painted>side*side/2||Color.alpha(bitmap.getPixel(side/2,1))>10||Color.alpha(bitmap.getPixel(1,1))>10)throw new Exception("Settings icon is empty or paints a square tile");
        }finally{bitmap.recycle();}
    }
    private void profileV12Checks(MainActivity screen,Context c,String name)throws Exception {
        uiAssert(()->invoke(screen,"renderProfileSettingsPage",new Class[]{}));waitForIdleSync();Thread.sleep(200);
        uiAssert(()->{
            View decor=screen.getWindow().getDecorView(),id=decor.findViewWithTag("profile_info_ID"),nickname=decor.findViewWithTag("profile_info_昵称");
            if(id==null||nickname==null||id.getParent()!=nickname.getParent()||((ViewGroup)id.getParent()).indexOfChild(id)!=0||((ViewGroup)id.getParent()).indexOfChild(nickname)!=1)throw new Exception("Profile ID and nickname order is wrong");
            if(decor.findViewWithTag("profile_info_绑定微信")!=null||visibleText(decor).contains("绑定微信"))throw new Exception("Unused WeChat binding remains");
            if(id.isClickable()||!nickname.isClickable()||nickname.getMinimumHeight()<PaperComponents.dp(c,48))throw new Exception("Profile row interaction/accessibility changed");
            for(String title:new String[]{"ID","昵称","个性签名","性别","身高体重","餐饮习惯","就餐方式","平均消费","登录"}){
                View row=decor.findViewWithTag("profile_info_"+title);if(!(row instanceof ViewGroup))throw new Exception("Missing personal info row "+title);
                View icon=((ViewGroup)row).getChildAt(0);if(!"profile_info_icon".equals(icon.getTag()))throw new Exception("Personal info lacks vector icon: "+title);cleanGlyph(icon,c);
            }
            // Binding glyphs are rendered locally; no account, password or cloud endpoint is contacted.
            int[] accents={Color.rgb(86,191,112),Color.rgb(244,93,80),Color.rgb(78,158,220)};
            for(int accent:accents)for(boolean night:new boolean[]{false,true})for(String kind:new String[]{"password","login","logout","delete","reset"}){
                ProfileSettingIcon icon=new ProfileSettingIcon(screen,kind,accent,night);cleanGlyph(icon,c);
                int side=icon.getWidth();Bitmap bitmap=Bitmap.createBitmap(side,side,Bitmap.Config.ARGB_8888);icon.draw(new Canvas(bitmap));boolean tinted=false;
                float white=night?.25f:0;int expected=Color.rgb(Math.round(Color.red(accent)*(1-white)+255*white),Math.round(Color.green(accent)*(1-white)+255*white),Math.round(Color.blue(accent)*(1-white)+255*white));
                for(int y=0;y<side&&!tinted;y++)for(int x=0;x<side;x++){int pixel=bitmap.getPixel(x,y);if(Color.alpha(pixel)>220&&Math.abs(Color.red(pixel)-Color.red(expected))<3&&Math.abs(Color.green(pixel)-Color.green(expected))<3&&Math.abs(Color.blue(pixel)-Color.blue(expected))<3){tinted=true;break;}}
                bitmap.recycle();if(!tinted)throw new Exception("Profile/reset icon ignored theme tint: "+kind);
            }
        });screenshot(name+"-profile");
    }
    private void modeSpecificSettings(MainActivity screen,boolean modern)throws Exception {
        uiAssert(()->{
            invoke(screen,"renderThemeSettingsPage",new Class[]{});View decor=screen.getWindow().getDecorView();
            assertSettingPresence(decor,"食物贴纸边框",modern);
            for(String title:new String[]{"头像背景半透明","底栏半透明","顶栏透明"})assertSettingPresence(decor,title,!modern);
            invoke(screen,"renderInterfaceLayoutPage",new Class[]{});decor=screen.getWindow().getDecorView();
            for(String title:new String[]{"文本框","左侧条痕"})assertSettingPresence(decor,title,!modern);
            JSONObject settings=(JSONObject)field(screen,"profile");Object savedLayout=settings.opt("footprintLayout");
            try{
                settings.put("footprintLayout","original");invoke(screen,"renderInterfaceLayoutPage",new Class[]{});assertSettingPresence(screen.getWindow().getDecorView(),"每行本数",false);
                settings.put("footprintLayout","expanded");invoke(screen,"renderInterfaceLayoutPage",new Class[]{});assertSettingPresence(screen.getWindow().getDecorView(),"每行本数",true);
            }finally{if(savedLayout==null)settings.remove("footprintLayout");else settings.put("footprintLayout",savedLayout);}
            invoke(screen,"renderStickerSettingsPage",new Class[]{});decor=screen.getWindow().getDecorView();
            for(String title:new String[]{"贴纸上滑收缩","合并贴纸","允许叠放图片/贴纸","图片合集"})assertSettingPresence(decor,title,!modern);
            assertSettingPresence(decor,"上传贴纸",false);
            // These are still read by the shared photo viewer/notebook and must not be hidden with legacy home-only controls.
            assertSettingPresence(decor,"图片背景",true);assertSettingPresence(decor,modern?"本子小图边框":"贴纸边框",true);
            invoke(screen,"renderHintManagerPage",new Class[]{});decor=screen.getWindow().getDecorView();
            for(String title:new String[]{"今日提示","早餐提示","午餐提示","晚餐提示"})assertSettingPresence(decor,title,!modern);
            assertSettingPresence(decor,"缺席一次",true);
            invoke(screen,"renderHiddenSettingsPage",new Class[]{});decor=screen.getWindow().getDecorView();
            assertSettingPresence(decor,modern?"隐藏美食栏":"隐藏贴纸栏",true);assertSettingPresence(decor,modern?"隐藏贴纸栏":"隐藏美食栏",false);
        });settingsRoot(screen);
    }
    private void resetV12Checks(MainActivity screen,Context c,android.content.SharedPreferences prefs,String before)throws Exception {
        String[] keys={"new_ui_v2","name","signature","apiEnabled","apiKey","apiEndpoint","apiModel","aiAnalysisEnabled","aiAutoAnalysis","aiVisionEnabled","aiRecordAccess","aiChatMemory","calorieRecognition","foodAutoAnalyze","foodPhotoConsent","mealNotificationsEnabled","breakfastNotificationTime","lunchNotificationTime","dinnerNotificationTime","autoCloudSync","cloudToken","profileGlassCard","navGlass","headerTransparent","timeline","mealCardAlpha","mealCardGlass","mediaStacking","galleryStack","photoCollection","stickerCollapse"};
        JSONObject[] snapshot={null};
        uiAssert(()->{
            JSONObject p=(JSONObject)field(screen,"profile");
            p.put("apiEnabled",true).put("apiKey","qa-not-a-real-key").put("apiEndpoint","https://food.qa.invalid/chat/completions").put("apiModel","qa-local-settings")
                .put("aiAnalysisEnabled",true).put("aiAutoAnalysis",true).put("aiVisionEnabled",true).put("aiRecordAccess",true).put("aiChatMemory",true).put("calorieRecognition",true)
                .put("foodPhotoConsent",true).put("foodAutoAnalyze",true).put("autoCloudSync",true).put("cloudToken","qa-local-only-no-account")
                .put("timeline","soft").put("mealCardAlpha",180).put("mealCardGlass",9).put("profileGlassCard",true).put("navGlass",true).put("headerTransparent",true);
            prefs.edit().putString(NutritionStore.PROFILE,p.toString()).commit();snapshot[0]=new JSONObject(p.toString());
        });settingsRoot(screen);
        uiAssert(()->{View row=setting(screen.getWindow().getDecorView(),"恢复默认设置");if(!(row instanceof ViewGroup)||!(((ViewGroup)row).getChildAt(0) instanceof ProfileSettingIcon))throw new Exception("Restore defaults is not using the new theme vector");cleanGlyph(((ViewGroup)row).getChildAt(0),c);});
        clickSetting("恢复默认设置");click("取消");
        if(!snapshot[0].toString().equals(prefs.getString(NutritionStore.PROFILE,"{}")))throw new Exception("Cancelling reset changed settings");
        clickSetting("恢复默认设置");click("恢复");waitForIdleSync();Thread.sleep(200);
        JSONObject actual=NutritionStore.read(prefs,NutritionStore.PROFILE);
        for(String key:keys)if(!String.valueOf(snapshot[0].opt(key)).equals(String.valueOf(actual.opt(key))))throw new Exception("Reset erased protected/non-current-mode setting: "+key);
        if(!"dashed".equals(actual.optString("foodStickerBorder")))throw new Exception("New-mode personalization was not reset");
        uiAssert(()->{assertSettingsRoot(screen,true);assertRenderPure(screen,prefs,before);});
        // Leave scheduled notifications/API automation disabled in the isolated test install after the assertions.
        uiAssert(()->{JSONObject p=(JSONObject)field(screen,"profile");p.put("foodAutoAnalyze",false).put("autoCloudSync",false).put("mealNotificationsEnabled",false);prefs.edit().putString(NutritionStore.PROFILE,p.toString()).commit();});
        MealReminderReceiver.cancelAll(c);
    }
    private void settingsV12(Context c,android.content.SharedPreferences prefs)throws Exception {
        FoodAnalysisJobService.cancelAll(c);FoodDraftStore.discard(c,FoodDraftStore.pendingSources(c));installTransport();
        if(android.os.Build.VERSION.SDK_INT>=33&&c.checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED)throw new Exception("Grant POST_NOTIFICATIONS on the isolated QA emulator before settings-v12; this scenario never requests permission");
        String name=args.getString("name","beta12"),date=new SimpleDateFormat("yyyy-MM-dd",Locale.CHINA).format(new Date());
        JSONObject diary=new JSONObject().put(date,new JSONObject().put("breakfast",new JSONObject().put("text","保留原来的餐食记录")).put("manualCaloriesOverride",true).put("manualCaloriesTotal",321));
        // Legacy rendering normally materializes all five empty meal containers in memory.
        for(String meal:NutritionStore.MEALS)if(!diary.getJSONObject(date).has(meal))diary.getJSONObject(date).put(meal,new JSONObject());
        JSONObject profile=new JSONObject().put("new_ui_v2",true).put("name","设置专项测试").put("signature","切换界面不更改资料")
            .put("decoration","true".equals(args.getString("night"))?"night":"red").put("followSystemNight",false)
            .put("foodStickerBorder","dots").put("photoCollection",true).put("galleryStack",true).put("stickerCollapse",true).put("mediaStacking",true)
            .put("profileGlassCard",false).put("navGlass",false).put("headerTransparent",false).put("mealNotificationsEnabled",false)
            .put("breakfastNotificationTime","07:15").put("lunchNotificationTime","12:20").put("dinnerNotificationTime","18:45")
            .put("autoCloudSync",false).put("foodPhotoConsent",false).put("foodAutoAnalyze",false)
            .put("seenAnnouncementVersion",c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName);
        String before=diary.toString();prefs.edit().putString(NutritionStore.PROFILE,profile.toString()).putString(NutritionStore.DAYS,before).commit();
        MainActivity screen=(MainActivity)startActivitySync(new Intent(c,MainActivity.class).putExtra("new_ui_v2",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        if(screen.getIntent().hasExtra("new_ui_v2"))throw new Exception("Launch mode extra was not consumed");
        uiAssert(()->invoke(screen,"showTab",new Class[]{int.class,boolean.class},3,false));waitForIdleSync();Thread.sleep(250);
        clickSetting("其他设置");uiAssert(()->assertSettingsRoot(screen,true));screenshot(name+"-new-settings");
        modeSpecificSettings(screen,true);profileV12Checks(screen,c,name);settingsRoot(screen);
        clickSetting("三餐提醒");waitForIdleSync();Thread.sleep(200);
        uiAssert(()->{
            if(!Boolean.TRUE.equals(field(screen,"inMoreSettingsPage")))throw new Exception("Reminder page route did not persist");
            View decor=screen.getWindow().getDecorView();for(String title:new String[]{"三餐通知","早餐提醒","午餐提醒","晚餐提醒"})assertSettingPresence(decor,title,true);
            for(String title:new String[]{"调用 API","AI分析","餐图识别与隐私"})assertSettingPresence(decor,title,false);
            String text=visibleText(decor);for(String time:new String[]{"07:15","12:20","18:45"})if(!text.contains(time))throw new Exception("Saved reminder time not displayed: "+time);
        });screenshot(name+"-reminders");
        clickSetting("三餐通知");if(!NutritionStore.read(prefs,NutritionStore.PROFILE).optBoolean("mealNotificationsEnabled"))throw new Exception("Reminder enable did not persist");
        clickSetting("三餐通知");if(NutritionStore.read(prefs,NutritionStore.PROFILE).optBoolean("mealNotificationsEnabled"))throw new Exception("Reminder disable did not persist");
        int reminderWindow=getUiAutomation().getRootInActiveWindow().getWindowId();
        clickSetting("早餐提醒");if(getUiAutomation().getRootInActiveWindow().getWindowId()==reminderWindow)throw new Exception("Reminder time picker not shown");screenshot(name+"-reminder-time-picker");sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);waitForIdleSync();
        if(!"07:15".equals(NutritionStore.read(prefs,NutritionStore.PROFILE).optString("breakfastNotificationTime")))throw new Exception("Cancelling reminder time changed it");
        TimePickerDialog[] picker={null};uiAssert(()->{picker[0]=(TimePickerDialog)invoke(screen,"showNotificationTimePicker",new Class[]{String.class,String.class,String.class},"早餐提醒","breakfastNotificationTime","08:00");picker[0].updateTime(9,25);});waitForIdleSync();
        uiAssert(()->picker[0].getButton(AlertDialog.BUTTON_POSITIVE).performClick());waitForIdleSync();Thread.sleep(250);
        if(!"09:25".equals(NutritionStore.read(prefs,NutritionStore.PROFILE).optString("breakfastNotificationTime")))throw new Exception("Reminder time save did not persist");
        uiAssert(()->{if(!Boolean.TRUE.equals(field(screen,"inMoreSettingsPage"))||setting(screen.getWindow().getDecorView(),"早餐提醒")==null||setting(screen.getWindow().getDecorView(),"调用 API")!=null)throw new Exception("Saving time did not return to reminder page");});
        sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);waitForIdleSync();uiAssert(()->assertSettingsRoot(screen,true));settingsRoot(screen);
        clickSetting("调用 API");click("API 官网");if(!Boolean.TRUE.equals(field(screen,"inApiOfficialSitesPage")))throw new Exception("API website route missing");
        sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);waitForIdleSync();Thread.sleep(400);uiAssert(()->assertSettingsRoot(screen,true));settingsRoot(screen);
        clickSetting("餐图识别与隐私");click("开启云端营养自动识别");click("暂不开启");
        if(NutritionStore.read(prefs,NutritionStore.PROFILE).optBoolean("foodPhotoConsent"))throw new Exception("Cancelling photo consent enabled upload");
        uiAssert(()->assertSettingsRoot(screen,true));settingsRoot(screen);
        // Exact profile snapshot: mode switching must not erase hidden legacy choices, credentials or custom values.
        JSONObject baseline=NutritionStore.read(prefs,NutritionStore.PROFILE);baseline.remove("new_ui_v2");
        String stable=baseline.toString();
        for(boolean modern:new boolean[]{false,true}){
            settingsRoot(screen);clickSetting(modern?"新版入口":"旧版入口");
            if(node(modern?"切换到新版？":"切换到旧版？")==null)throw new Exception("Missing explicit version confirmation");
            screenshot(name+(modern?"-switch-new-confirm":"-switch-old-confirm"));click("取消");
            if(NutritionStore.read(prefs,NutritionStore.PROFILE).optBoolean("new_ui_v2")==modern)throw new Exception("Cancelling version switch changed mode");
            clickSetting(modern?"新版入口":"旧版入口");click("切换");waitForIdleSync();Thread.sleep(300);
            uiAssert(()->{if(screen.isDestroyed()||screen.isFinishing())throw new Exception("Mode switch destroyed settings activity");assertSettingsRoot(screen,modern);assertRenderPure(screen,prefs,before);});
            JSONObject stored=NutritionStore.read(prefs,NutritionStore.PROFILE);if(stored.optBoolean("new_ui_v2")!=modern)throw new Exception("Mode switch not persisted");stored.remove("new_ui_v2");
            // JSONObject key order is not user data (put/remove in temporary layout previews may reorder it).
            Set<String> profileKeys=new TreeSet<>();for(Iterator<String> it=baseline.keys();it.hasNext();)profileKeys.add(it.next());for(Iterator<String> it=stored.keys();it.hasNext();)profileKeys.add(it.next());
            List<String> changedKeys=new ArrayList<>();for(String key:profileKeys)if(!String.valueOf(baseline.opt(key)).equals(String.valueOf(stored.opt(key))))changedKeys.add(key);
            if(!changedKeys.isEmpty())throw new Exception("Version switch changed non-mode preference keys: "+changedKeys);
            // Exercise both live home FAB layouts; changing modes must not leave old/new geometry mixed.
            uiAssert(()->invoke(screen,"showTab",new Class[]{int.class,boolean.class},0,false));waitForIdleSync();Thread.sleep(300);
            uiAssert(()->{
                View fab=(View)field(screen,"fab"),icon=(View)field(screen,"fabIcon");android.widget.FrameLayout.LayoutParams lp=(android.widget.FrameLayout.LayoutParams)fab.getLayoutParams();
                if(modern){if((lp.gravity&Gravity.HORIZONTAL_GRAVITY_MASK)!=Gravity.CENTER_HORIZONTAL||fab.getWidth()!=PaperComponents.dp(c,104)||icon.getVisibility()!=View.GONE)throw new Exception("New home FAB not restored");}
                else{if((lp.gravity&Gravity.HORIZONTAL_GRAVITY_MASK)!=Gravity.RIGHT||lp.rightMargin!=PaperComponents.dp(c,24)||Math.abs(screenRect(fab).centerX()-screenRect(icon).centerX())>1||Math.abs(screenRect(fab).centerY()-screenRect(icon).centerY())>1)throw new Exception("Old home FAB and icon diverged after switching");}
            });
            screenshot(name+(modern?"-new-home-after-switch":"-old-home-after-switch"));
            modeSpecificSettings(screen,modern);screenshot(name+(modern?"-new-settings-return":"-old-settings"));
        }
        uiAssert(()->{invoke(screen,"renderMoreSettingsPage",new Class[]{});assertSettingsRoot(screen,true);});
        // A real Activity recreation must reload the persisted mode and still expose only that mode's options.
        ActivityMonitor monitor=addMonitor(MainActivity.class.getName(),null,false);MainActivity recreated;
        try{runOnMainSync(screen::recreate);Activity fresh=waitForMonitorWithTimeout(monitor,10000);if(!(fresh instanceof MainActivity))throw new Exception("Recreated settings activity unavailable");recreated=(MainActivity)fresh;}finally{removeMonitor(monitor);}
        waitForIdleSync();settingsRoot(recreated);uiAssert(()->{assertSettingsRoot(recreated,true);assertRenderPure(recreated,prefs,before);});
        resetV12Checks(recreated,c,prefs,before);
        if(apiCalls.get()!=0)throw new Exception("Settings-only scenario made food API requests");
        Bundle result=new Bundle();result.putString("stream","PASS: ID-first profile and theme-tinted untiled vector binding/reset icons, no WeChat binding, direct Other AI controls, one global reminder button/standalone times, granted-permission notification enable/disable and time cancel, explicit two-way version confirmation/cancel, mode-only persistence with all other preferences and diary intact, new/legacy theme/layout/media/hint/hidden-option isolation, compatibility redirect and Activity recreation, reset cancel/apply preserves mode/API/notifications/cloud/legacy-only preferences; no external API requests\n");finish(Activity.RESULT_OK,result);
    }
    private void collectInputs(android.view.accessibility.AccessibilityNodeInfo current,List<android.view.accessibility.AccessibilityNodeInfo> out){
        if(current==null)return;
        if("android.widget.EditText".contentEquals(current.getClassName()==null?"":current.getClassName()))out.add(current);
        for(int i=0;i<current.getChildCount();i++)collectInputs(current.getChild(i),out);
    }
    private void fillFoodEditor(String[] values)throws Exception {
        waitForIdleSync();List<android.view.accessibility.AccessibilityNodeInfo> inputs=new ArrayList<>();collectInputs(getUiAutomation().getRootInActiveWindow(),inputs);
        if(inputs.size()!=8)throw new Exception("Expected 8 meal editor fields, found "+inputs.size());
        for(int i=0;i<values.length;i++){
            Bundle value=new Bundle();value.putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,values[i]);
            if(!inputs.get(i).performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT,value))throw new Exception("Unable to set meal editor field "+i);
        }waitForIdleSync();
    }
    private void restoreOutsideCrop(MainActivity screen,Context c)throws Exception {
        String token=UUID.randomUUID().toString();File original=new File(c.getFilesDir(),"qa_editor_source_"+token+".png"),output=new File(c.getFilesDir(),"qa_editor_cutout_"+token+".png");
        Bitmap source=Bitmap.createBitmap(96,96,Bitmap.Config.ARGB_8888);source.eraseColor(Color.rgb(180,70,30));
        Bitmap crop=Bitmap.createBitmap(source,36,36,24,24);
        try(OutputStream out=new FileOutputStream(original)){source.compress(Bitmap.CompressFormat.PNG,100,out);}
        try(OutputStream out=new FileOutputStream(output)){crop.compress(Bitmap.CompressFormat.PNG,100,out);}
        source.recycle();crop.recycle();final JSONArray[] saved={null};
        runOnMainSync(()->CutoutEditor.show(screen,original,output,new JSONArray().put(36).put(36).put(60).put(60),false,bounds->saved[0]=bounds));
        waitForIdleSync();click("擦除");
        final View[] canvas={null};uiAssert(()->{for(View root:android.view.inspector.WindowInspector.getGlobalWindowViews()){View found=root.findViewWithTag("cutout_editor_canvas");if(found!=null&&found.isShown())canvas[0]=found;}});
        if(canvas[0]==null)throw new Exception("Missing repair canvas");
        float side=Math.min(canvas[0].getWidth()-24f,canvas[0].getHeight()-24f);
        float x=canvas[0].getWidth()/2f-side/2+side*20/96f,y=canvas[0].getHeight()/2f-side/2+side*20/96f;
        long now=android.os.SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,x,y,0),up=MotionEvent.obtain(now,now+50,MotionEvent.ACTION_UP,x,y,0);
        try{uiAssert(()->{canvas[0].dispatchTouchEvent(down);canvas[0].dispatchTouchEvent(up);});}finally{down.recycle();up.recycle();}
        waitForIdleSync();screenshot("beta4-edit-restore-outside-crop");click("保存");
        if(saved[0]==null||saved[0].optInt(0)>=36||saved[0].optInt(1)>=36)throw new Exception("Restore cannot recover original outside old crop");
        Bitmap repaired=BitmapFactory.decodeFile(output.getAbsolutePath());
        try{
            if(repaired==null||Color.alpha(repaired.getPixel(20-saved[0].getInt(0),20-saved[0].getInt(1)))<200)throw new Exception("Restored source pixel missing from saved cutout");
        }finally{if(repaired!=null)repaired.recycle();original.delete();output.delete();}
    }
    private void editDraft(Context c,android.content.SharedPreferences prefs)throws Exception {
        FoodAnalysisJobService.cancelAll(c);
        JSONObject profile=new JSONObject().put("new_ui_v2",true).put("decoration","true".equals(args.getString("night"))?"night":"yellow")
            .put("foodAutoAnalyze",false).put("foodPhotoConsent",false)
            .put("seenAnnouncementVersion",c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName);
        String date=new SimpleDateFormat("yyyy-MM-dd",Locale.CHINA).format(new Date());
        JSONObject original=new JSONObject().put(date,new JSONObject().put("breakfast",new JSONObject().put("text","保留原来的早餐文字"))
            .put("manualCaloriesOverride",true).put("manualCaloriesTotal",321));
        prefs.edit().putString(NutritionStore.PROFILE,profile.toString()).putString(NutritionStore.DAYS,original.toString()).commit();
        MainActivity screen=(MainActivity)startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        Bitmap photo;try(InputStream in=c.getAssets().open("qa/food.jpg")){photo=BitmapFactory.decodeStream(in);}
        if(photo==null)throw new Exception("Missing draft editor fixture");
        final Bitmap bitmap=photo;final Throwable[] failure={null};
        runOnMainSync(()->{try{if(!Boolean.TRUE.equals(invoke(screen,"saveMealBitmap",new Class[]{String.class,String.class,Bitmap.class,boolean.class,boolean.class},date,"lunch",bitmap,false,false)))throw new Exception("Unable to stage editor photo");}catch(Throwable e){failure[0]=e;}});
        photo.recycle();if(failure[0]!=null)throw new Exception("stage editor photo",failure[0]);
        String source=draftSource(c,date,"lunch");JSONObject item=waitDraft(c,source,"awaiting_consent",120000);
        if(item.optString("cutoutFile").isEmpty())throw new Exception("Draft editor fixture did not cut out");
        runOnMainSync(()->{try{invoke(screen,"showFoodProcessing",new Class[]{String.class,String.class,List.class},date,"lunch",Collections.singletonList(source));}catch(Exception e){throw new RuntimeException(e);}});
        screenshot("beta4-edit-initial-preview");clickDescription("修改");click("修改菜名、份量与营养");
        fillFoodEditor(new String[]{"自己修改的炒饭","","","","","","",""});click("保存修改");
        item=FoodDraftStore.item(c,source);
        if(item==null||!item.optBoolean("userEdited")||!"自己修改的炒饭".equals(item.optString("dishName"))||item.has("nutrients"))throw new Exception("Name-only draft edit invented nutrients or lost name");
        assertNoMealPhoto(prefs,date,"lunch",source);
        if(!original.toString().equals(prefs.getString(NutritionStore.DAYS,"{}")))throw new Exception("Name-only edit modified diary before confirm");
        screenshot("beta4-edit-name-only");
        clickDescription("修改");click("修改菜名、份量与营养");
        fillFoodEditor(new String[]{"手工录入炒饭","100","120","15","6","4","1","100"});click("保存修改");
        item=FoodDraftStore.item(c,source);JSONObject nutrients=item==null?null:item.optJSONObject("nutrients");
        if(item==null||!item.optBoolean("userEdited")||item.optDouble("estimatedGrams")!=100||nutrients==null||nutrients.optDouble("caloriesKcal")!=120||nutrients.optDouble("sodiumMg")!=100)throw new Exception("Full draft editor failed to save user nutrition");
        assertNoMealPhoto(prefs,date,"lunch",source);screenshot("beta4-edit-complete-draft");clickDescription("确定");
        JSONObject meal=NutritionStore.meal(NutritionStore.read(prefs,NutritionStore.DAYS),date,"lunch");
        JSONObject accepted=NutritionStore.forSource(meal,source).optJSONObject(0);
        if(accepted==null||!accepted.optBoolean("userEdited")||accepted.optDouble("estimatedGrams")!=100||accepted.getJSONObject("nutrients").getDouble("caloriesKcal")!=120||FoodDraftStore.item(c,source)!=null)throw new Exception("Edited draft confirm lost supplied values");
        JSONObject day=NutritionStore.read(prefs,NutritionStore.DAYS).getJSONObject(date);
        if(FoodUi.displayCalories(day)!=321||!"保留原来的早餐文字".equals(day.getJSONObject("breakfast").optString("text")))throw new Exception("Edited draft confirm damaged old record/manual override");
        screenshot("beta4-edit-confirmed-home");
        restoreOutsideCrop(screen,c);
        Bundle result=new Bundle();result.putString("stream","PASS: actual draft edit UI, name-only without fabricated nutrients, complete manual grams/macros/sodium, no diary change before explicit confirmation, accepted values and manual override preserved, brush Restore recovers and saves source outside old cropped bounds\n");finish(Activity.RESULT_OK,result);
    }
    private void addFlow(Context c,android.content.SharedPreferences prefs)throws Exception {
        FoodAnalysisJobService.cancelAll(c);
        FoodDraftStore.discard(c,FoodDraftStore.pendingSources(c));
        JSONObject profile=new JSONObject().put("new_ui_v2",true).put("decoration","yellow")
            .put("seenAnnouncementVersion",c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName);
        prefs.edit().putString(NutritionStore.PROFILE,profile.toString()).putString(NutritionStore.DAYS,"{}").commit();
        MainActivity screen=(MainActivity)startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        if(node("识别设置")!=null)throw new Exception("Home still exposes recognition settings");
        screenshot("beta4-home-person-icon");
        final Throwable[] error={null};
        ActivityMonitor external=new ActivityMonitor(){
            @Override public ActivityResult onStartActivity(Intent intent){
                if(Intent.ACTION_CHOOSER.equals(intent.getAction()))intent=intent.getParcelableExtra(Intent.EXTRA_INTENT);
                if(intent==null)return null;
                String action=intent.getAction();
                if(!android.provider.MediaStore.ACTION_IMAGE_CAPTURE.equals(action)&&!Intent.ACTION_GET_CONTENT.equals(action))return null;
                try{
                    android.net.Uri uri=intent.getParcelableExtra(android.provider.MediaStore.EXTRA_OUTPUT);
                    if(uri==null)uri=(android.net.Uri)invoke(screen,"createMealCameraOutputUri",new Class[]{});
                    try(InputStream in=c.getAssets().open("qa/food.jpg");OutputStream out=c.getContentResolver().openOutputStream(uri)){
                        byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);
                    }
                    return new ActivityResult(Activity.RESULT_OK,new Intent().setData(uri));
                }catch(Throwable e){error[0]=e;return new ActivityResult(Activity.RESULT_CANCELED,null);}
            }
        };addMonitor(external);
        for(String source:new String[]{"拍照","相册"}){
            final String meal="拍照".equals(source)?"breakfast":"lunch";
            runOnMainSync(()->{try{invoke(screen,"chooseMealForPhoto",new Class[]{});}catch(Exception e){throw new RuntimeException(e);}});
            waitForIdleSync();Thread.sleep(350);screenshot("beta4-add-meal");
            if(node("添加图片")==null||node("早餐")==null||node("午餐")==null||node("晚餐")==null)throw new Exception("Meal menu contract");
            click("拍照".equals(source)?"早餐":"午餐");Thread.sleep(350);
            if(node("拍照")==null||node("相册")==null||node("导入透明贴纸")!=null)throw new Exception("Source menu contract");
            screenshot("beta4-add-source");click(source);
            String date=new SimpleDateFormat("yyyy-MM-dd",Locale.CHINA).format(new Date());
            String photo=draftSource(c,date,meal);
            assertNoMealPhoto(prefs,date,meal,photo);
            JSONObject item=waitDraft(c,photo,"awaiting_consent",90000);
            if(error[0]!=null)throw new Exception("External capture fixture",error[0]);
            if(item.optString("cutoutFile").isEmpty())throw new Exception(source+" didn't trigger cutout");
            Thread.sleep(900);screenshot("beta4-"+meal+"-source-cutout");
            if(descriptionNode("取消")==null||descriptionNode("确定")==null||descriptionNode("修改")==null||descriptionNode("食物贴纸预览")==null)throw new Exception("Preview controls/accessibility contract");
            assertNoMealPhoto(prefs,date,meal,photo);
            if("拍照".equals(source)){
                clickDescription("取消");
                if(FoodDraftStore.item(c,photo)!=null)throw new Exception("Cancel left camera draft");
                assertNoMealPhoto(prefs,date,meal,photo);
                if(new File(c.getFilesDir(),photo).exists())throw new Exception("Cancelled draft source was not cleaned");
            }else{
                clickDescription("确定");
                JSONObject value=NutritionStore.meal(NutritionStore.read(prefs,NutritionStore.DAYS),date,meal);
                if(!NutritionStore.containsPhoto(value,photo)||NutritionStore.forSource(value,photo).length()!=1)throw new Exception("Confirm did not add gallery sticker");
                if(FoodDraftStore.item(c,photo)!=null)throw new Exception("Confirm left gallery draft");
                if(!FoodDraftStore.confirm(c,Collections.singletonList(photo))||value.getJSONArray("photos").length()!=1)throw new Exception("Confirm is not idempotent");
                screenshot("beta4-confirmed-gallery-home");
            }
        }
        removeMonitor(external);
        Bundle result=new Bundle();result.putString("stream","PASS: exact meal/source menus, no home settings, camera + gallery callbacks stage cutouts, accessible fullscreen preview controls, no home write before confirm, camera cancel/cleanup, gallery confirm/idempotency; external camera/picker use QA fixture\n");finish(Activity.RESULT_OK,result);
    }
    private void cancellationInFlight(Context c,android.content.SharedPreferences prefs,String date)throws Exception {
        // Restoring a portable backup intentionally drops local credentials and photo-upload consent.
        JSONObject profile=NutritionStore.read(prefs,NutritionStore.PROFILE);
        profile.put("foodAutoAnalyze",true).put("foodPhotoConsent",true).put("apiEnabled",true)
            .put("apiEndpoint","https://food.qa.invalid/v1/chat/completions").put("apiModel","qa-vision").put("apiKey","qa-not-a-real-key");
        prefs.edit().putString(NutritionStore.PROFILE,profile.toString()).commit();
        String source="draft_qa_cancel_"+UUID.randomUUID().toString()+".jpg";
        try(InputStream in=c.getAssets().open("qa/food.jpg");OutputStream out=new FileOutputStream(new File(c.getFilesDir(),source))){
            byte[] buffer=new byte[8192];int count;while((count=in.read(buffer))!=-1)out.write(buffer,0,count);
        }
        String before=prefs.getString(NutritionStore.DAYS,"{}");
        holdResponse=true;
        try{
            if(!FoodAnalysisJobService.enqueueDraft(c,date,"dinner",source,false))throw new Exception("Unable to enqueue cancellation draft");
            if(!responseEntered.await(120,java.util.concurrent.TimeUnit.SECONDS))throw new Exception("Cancellation test did not reach held QA response");
            JSONObject inFlight=FoodDraftStore.item(c,source);
            if(inFlight==null)throw new Exception("In-flight draft disappeared before cancel");
            String cutout=inFlight.optString("cutoutFile");
            FoodDraftStore.discard(c,Collections.singletonList(source));
            responseReleased.countDown();holdResponse=false;
            long start=android.os.SystemClock.elapsedRealtime();
            do{
                Thread.sleep(150);
                if(!prefs.getString("food_analysis_jobs","[]").contains(source))break;
            }while(android.os.SystemClock.elapsedRealtime()-start<10000);
            Thread.sleep(500); // Let an already-read response attempt its generation-checked write.
            if(FoodDraftStore.item(c,source)!=null)throw new Exception("Cancelled draft was resurrected by worker");
            assertNoMealPhoto(prefs,date,"dinner",source);
            if(!before.equals(prefs.getString(NutritionStore.DAYS,"{}")))throw new Exception("Cancelled draft changed existing records");
            if(new File(c.getFilesDir(),source).exists()||(!cutout.isEmpty()&&new File(c.getFilesDir(),cutout).exists()))throw new Exception("Cancelled owned draft files leaked");
        }finally{responseReleased.countDown();holdResponse=false;}
    }
    private void collectViews(View current,List<View> out){
        out.add(current);if(current instanceof ViewGroup){ViewGroup group=(ViewGroup)current;for(int i=0;i<group.getChildCount();i++)collectViews(group.getChildAt(i),out);}
    }
    private List<View> tagged(View root,String prefix){
        List<View> all=new ArrayList<>(),matches=new ArrayList<>();collectViews(root,all);
        for(View view:all)if(view.getTag() instanceof String&&((String)view.getTag()).startsWith(prefix))matches.add(view);
        return matches;
    }
    private String visibleText(View root){
        List<View> all=new ArrayList<>();collectViews(root,all);StringBuilder text=new StringBuilder();
        for(View view:all)if(view.getVisibility()==View.VISIBLE&&view instanceof android.widget.TextView)text.append(((android.widget.TextView)view).getText()).append('\n');
        return text.toString();
    }
    private void assertRenderPure(MainActivity screen,android.content.SharedPreferences prefs,String before)throws Exception {
        if(!before.equals(prefs.getString(NutritionStore.DAYS,"{}")))throw new Exception("Rendering changed persisted day JSON");
        if(!before.equals(((JSONObject)field(screen,"days")).toString()))throw new Exception("Rendering mutated live day JSON");
    }
    private File layoutSource(Context c,String name)throws Exception {
        File output=new File(c.getFilesDir(),name);
        try(InputStream in=c.getAssets().open("qa/food.jpg");OutputStream out=new FileOutputStream(output)){
            byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
        }return output;
    }
    /** Geometry fixtures are labelled QA data; no fixture nutrition is a real model result. */
    private JSONObject unifiedDays(Context c,String date)throws Exception {
        String[] meals={"breakfast","lunch","dinner"},names={"番茄鸡蛋面（测试）","牛肉蛋炒饭（测试）","晚餐一碗热汤面和青菜（测试）"};
        File photo=layoutSource(c,"qa_unified_breakfast.jpg"),cutout=new File(c.getFilesDir(),"qa_unified_cutout.png");
        // A single real local cutout provides the alpha/display fixture, outside the UI thread.
        if(!cutout.isFile()){
            Bitmap source=BitmapFactory.decodeFile(photo.getAbsolutePath()),alpha=null;
            if(source==null)throw new Exception("Missing unified-paper photo fixture");
            try{alpha=FoodCutoutEngine.create(c,source);if(alpha==null)throw new Exception("Missing unified-paper alpha fixture");
                try(OutputStream out=new FileOutputStream(cutout)){alpha.compress(Bitmap.CompressFormat.PNG,100,out);}
            }finally{source.recycle();if(alpha!=null)alpha.recycle();}
        }
        JSONObject day=new JSONObject();
        for(int i=0;i<meals.length;i++){
            String source="qa_unified_"+meals[i]+".jpg";if(i>0)layoutSource(c,source);
            JSONObject food=FoodAnalysisResult.parse(RESPONSE).value;
            food.put("id","qa-"+meals[i]).put("sourcePhoto",source).put("cutoutFile",cutout.getName()).put("dishName",names[i])
                .put("status","completed").put("analysisStatus","completed").put("createdAt",1000L+i).put("userEdited",false);
            JSONObject meal=new JSONObject().put("foodItems",new JSONArray().put(food))
                .put("photos",new JSONArray().put(source)).put("stickers",new JSONArray().put(cutout.getName()));
            if(i==0)meal.put("text","早餐备注：今天慢慢吃");
            if(i==2){layoutSource(c,"qa_unified_legacy.jpg");meal.getJSONArray("photos").put("qa_unified_legacy.jpg");}
            day.put(meals[i],meal);
        }
        return new JSONObject().put(date,day);
    }
    private void assertUnifiedHome(MainActivity screen,Context c,boolean empty)throws Exception {
        View decor=screen.getWindow().getDecorView(),paper=decor.findViewWithTag("today_paper");
        int paperCount=0;for(View candidate:tagged(decor,"today_paper"))if("today_paper".equals(candidate.getTag()))paperCount++;
        if(paper==null||paperCount!=1)throw new Exception("Home must have exactly one daily paper");
        List<View> all=new ArrayList<>();collectViews(decor,all);
        for(View view:all){
            if(view instanceof android.widget.EditText&&view.isShown())throw new Exception("Home still contains an inline meal text editor");
            if("meal_card".equals(view.getTag())&&view.isShown())throw new Exception("Nested legacy meal card is still on new home");
        }
        String text=visibleText(paper);
        for(String forbidden:new String[]{"早餐\n","午餐\n","晚餐\n","编辑 / 重试 / 撤销","蛋白质","碳水","膳食纤维","脂肪","mg","已记录","未记录"})
            if(text.contains(forbidden))throw new Exception("Home leaks verbose meal/detail text: "+forbidden);
        List<View> foods=tagged(paper,"day_food_");
        if(foods.size()!=(empty?0:3))throw new Exception("Expected "+(empty?0:3)+" merged foods, got "+foods.size()+"; source/cutout may be duplicated");
        if(!empty){
            for(String meal:new String[]{"breakfast","lunch","dinner"})if(paper.findViewWithTag("day_food_"+meal+"_qa-"+meal)==null)throw new Exception("Missing merged "+meal+" food");
            if(text.contains("早餐备注：今天慢慢吃"))throw new Exception("Old meal notes must move into details rather than duplicate a home entry");
            List<View> labels=tagged(paper,"day_entry_label");if(labels.size()<3)throw new Exception("Missing accessible meal-prefixed dish labels");
            boolean[] prefixes=new boolean[3];String[] marks={"早","午","晚"};
            for(View view:labels){
                if(!(view instanceof android.widget.TextView))throw new Exception("Entry label must expose readable text");
                android.widget.TextView label=(android.widget.TextView)view;String value=label.getText().toString();
                for(int i=0;i<marks.length;i++)if(value.startsWith(marks[i]))prefixes[i]=true;
                int[] position=new int[2],paperPosition=new int[2];view.getLocationOnScreen(position);paper.getLocationOnScreen(paperPosition);
                if(view.getWidth()<=0||position[0]<paperPosition[0]||position[0]+view.getWidth()>paperPosition[0]+paper.getWidth()+1)throw new Exception("Dish label exceeds paper width");
                android.text.Layout layout=label.getLayout();if(layout==null||layout.getHeight()>label.getHeight()-label.getPaddingTop()-label.getPaddingBottom()+1)throw new Exception("Dish label clipped vertically");
                for(int line=0;line<layout.getLineCount();line++)if(layout.getEllipsisCount(line)>0)throw new Exception("Dish label unexpectedly truncated");
            }
            for(int i=0;i<prefixes.length;i++)if(!prefixes[i])throw new Exception("Dish labels lost meal prefix "+marks[i]);
        }
    }
    private void layoutScenario(Context c,android.content.SharedPreferences prefs)throws Exception {
        String name=args.getString("name","layout");
        boolean empty="unified-empty".equals(args.getString("scenario"));
        FoodAnalysisJobService.cancelAll(c);FoodDraftStore.discard(c,FoodDraftStore.pendingSources(c));
        JSONObject profile=new JSONObject().put("new_ui_v2",true)
            .put("decoration","true".equals(args.getString("night"))?"night":"yellow")
            .put("footprintLayout","expanded").put("footprintColumns",3)
            .put("seenAnnouncementVersion",c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName);
        Calendar date=Calendar.getInstance();
        java.text.SimpleDateFormat format=new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.CHINA);
        String key=format.format(date.getTime());JSONObject days=empty?new JSONObject():unifiedDays(c,key);String before=days.toString();
        prefs.edit().putString(NutritionStore.PROFILE,profile.toString()).putString(NutritionStore.DAYS,days.toString()).commit();
        MainActivity screen=(MainActivity)startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        screenshot(name+"-today-top");
        final Throwable[] failure={null};
        final int[] headerY={0};
        runOnMainSync(()->{try{
            assertUnifiedHome(screen,c,empty);assertRenderPure(screen,prefs,before);
            String expectedWidth=args.getString("expected_width_dp"),expectedFont=args.getString("expected_font_scale");
            if(expectedWidth!=null&&Math.abs(screen.getWindow().getDecorView().getWidth()/c.getResources().getDisplayMetrics().density-Float.parseFloat(expectedWidth))>2)throw new Exception("QA device width is not configured to "+expectedWidth+"dp");
            if(expectedFont!=null&&Math.abs(screen.getResources().getConfiguration().fontScale-Float.parseFloat(expectedFont))>.02f)throw new Exception("QA device font scale is not "+expectedFont);
            android.widget.ScrollView scroll=(android.widget.ScrollView)invoke(screen,"currentPageScroll",new Class[]{});
            View header=(View)field(screen,"activeStickyHeader");int[] headerPosition=new int[2];header.getLocationOnScreen(headerPosition);headerY[0]=headerPosition[1];
            scroll.scrollTo(0,PaperComponents.dp(c,300));
            screen.getWindow().getDecorView().invalidate();header.invalidate();
        }catch(Throwable e){failure[0]=e;}});
        if(failure[0]!=null)throw new Exception("unified home layout",failure[0]);
        screenshot(name+"-today-scroll");
        runOnMainSync(()->{try{
            View header=(View)field(screen,"activeStickyHeader");int[] location=new int[2];header.getLocationOnScreen(location);
            if(!header.isShown()||location[1]!=headerY[0])throw new Exception("Header moved with scrolling content");
        }catch(Throwable e){failure[0]=e;}});
        if(failure[0]!=null)throw new Exception("sticky header",failure[0]);
        if(!empty){
            runOnMainSync(()->{try{
                android.widget.ScrollView scroll=(android.widget.ScrollView)invoke(screen,"currentPageScroll",new Class[]{});scroll.fullScroll(View.FOCUS_DOWN);
            }catch(Exception e){throw new RuntimeException(e);}});
            screenshot(name+"-today-bottom");
            runOnMainSync(()->{try{
                View decor=screen.getWindow().getDecorView(),paper=decor.findViewWithTag("today_paper"),fab=(View)field(screen,"fab"),nav=(View)field(screen,"navHost");
                int[] fabPosition=new int[2],navPosition=new int[2];fab.getLocationOnScreen(fabPosition);nav.getLocationOnScreen(navPosition);int safeBottom=Math.min(fabPosition[1],navPosition[1]);
                for(View label:tagged(paper,"day_entry_label")){int[] position=new int[2];label.getLocationOnScreen(position);if(position[1]+label.getHeight()>safeBottom-PaperComponents.dp(c,6))throw new Exception("Last food label is hidden behind bottom controls after scrolling");}
                assertRenderPure(screen,prefs,before);
                View food=paper.findViewWithTag("day_food_breakfast_qa-breakfast");if(!food.performClick())throw new Exception("Merged food has no detail action");
            }catch(Throwable e){failure[0]=e;}});
            if(failure[0]!=null)throw new Exception("bottom safe area or detail entry",failure[0]);
            screenshot(name+"-food-detail");
            if(node("餐次备注\n早餐备注：今天慢慢吃")==null)throw new Exception("Food details lost legacy meal note");
            if(node("蛋白质")==null&&node("蛋白质 24g")==null)throw new Exception("Food details missing nutrition");
            if(descriptionNode("修改菜名、份量和营养")==null&&node("修改菜名、份量和营养")==null)throw new Exception("Food details missing edit access");
            if(descriptionNode("番茄鸡蛋面（测试），点击查看原图")==null)throw new Exception("Food details missing image-based original photo access");
            if(node("查看原图")!=null)throw new Exception("Obsolete original-photo text row is still present");
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);waitForIdleSync();
        }
        runOnMainSync(()->{try{invoke(screen,"showTab",new Class[]{int.class,boolean.class},1,false);}catch(Exception e){throw new RuntimeException(e);}});
        screenshot(name+"-footprints");
        runOnMainSync(()->{try{
            View report=screen.getWindow().getDecorView().findViewWithTag("v2_week_report");
            android.view.ViewGroup parent=(android.view.ViewGroup)report.getParent();
            View next=parent.getChildAt(parent.indexOfChild(report)+1);
            if(next.getTop()-report.getBottom()<PaperComponents.dp(c,20))throw new Exception("Weekly entry touches notebooks");
        }catch(Throwable e){failure[0]=e;}});
        if(failure[0]!=null)throw new Exception("footprints layout",failure[0]);
        Bundle result=new Bundle();result.putString("stream","PASS: one merged daily paper, meal-prefixed labels, no nested meal cards/inline editors/macros/actions, no repeated source/cutout entries, legacy text/photo retention, day JSON render purity, bottom control safe area, detail access, weekly separation and fixed progressive header; QA nutrition is fixture data, not model accuracy\n");
        finish(Activity.RESULT_OK,result);
    }
    private String fileDigest(File file)throws Exception {
        java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");
        try(InputStream in=new FileInputStream(file)){byte[] buffer=new byte[8192];int count;while((count=in.read(buffer))!=-1)digest.update(buffer,0,count);}
        return java.util.Arrays.toString(digest.digest());
    }
    private View foodMedia(MainActivity screen,String meal,String id)throws Exception {
        View row=screen.getWindow().getDecorView().findViewWithTag("day_food_"+meal+"_"+id);
        if(row==null)throw new Exception("Missing food row "+id);
        View image=row.findViewWithTag("day_entry_image");
        if(image==null)throw new Exception("Missing food image "+id);
        return (View)image.getParent();
    }
    private void stickerControlsScenario(Context c,android.content.SharedPreferences prefs)throws Exception {
        FoodAnalysisJobService.cancelAll(c);FoodDraftStore.discard(c,FoodDraftStore.pendingSources(c));
        String date=new SimpleDateFormat("yyyy-MM-dd",Locale.CHINA).format(new Date());
        JSONObject sample=unifiedDays(c,date),breakfast=sample.getJSONObject(date).getJSONObject("breakfast");
        JSONObject first=breakfast.getJSONArray("foodItems").getJSONObject(0);
        first.put("analysisId","qa-sticker-generation");
        File source=new File(c.getFilesDir(),first.getString("sourcePhoto")),alpha=new File(c.getFilesDir(),first.getString("cutoutFile"));
        String sourceHash=fileDigest(source),alphaHash=fileDigest(alpha);
        JSONObject profile=new JSONObject().put("new_ui_v2",true).put("decoration","true".equals(args.getString("night"))?"night":"yellow")
            .put("bookStickerLayout","straight").put("bookPhotoBoard",false)
            .put("seenAnnouncementVersion",c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName);
        prefs.edit().putString(NutritionStore.PROFILE,profile.toString()).putString(NutritionStore.DAYS,sample.toString()).commit();
        MainActivity screen=(MainActivity)startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        final Throwable[] failure={null};final View[] media={null};
        runOnMainSync(()->{try{
            media[0]=foodMedia(screen,"breakfast","qa-breakfast");
            media[0].requestRectangleOnScreen(new Rect(0,0,media[0].getWidth(),media[0].getHeight()),true);
        }catch(Throwable e){failure[0]=e;}});
        if(failure[0]!=null)throw new Exception("Sticker selection",failure[0]);
        Thread.sleep(250);
        final int[] point=new int[2];runOnMainSync(()->media[0].getLocationOnScreen(point));
        float x=point[0]+media[0].getWidth()/2f,y=point[1]+media[0].getHeight()/2f;
        long down=android.os.SystemClock.uptimeMillis();
        MotionEvent event=MotionEvent.obtain(down,down,MotionEvent.ACTION_DOWN,x,y,0);getUiAutomation().injectInputEvent(event,true);event.recycle();
        Thread.sleep(ViewConfiguration.getLongPressTimeout()+150);
        event=MotionEvent.obtain(down,android.os.SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,x,y,0);getUiAutomation().injectInputEvent(event,true);event.recycle();waitForIdleSync();
        if(descriptionNode("向左旋转90度")==null||descriptionNode("删除这张贴纸")==null)throw new Exception("Actual long press did not expose both corner controls");
        screenshot("beta6-sticker-long-press");
        runOnMainSync(()->{try{
            android.view.ViewGroup frame=(android.view.ViewGroup)media[0];View rotate=null,remove=null;
            for(int i=0;i<frame.getChildCount();i++){View child=frame.getChildAt(i);if("向左旋转90度".contentEquals(child.getContentDescription()==null?"":child.getContentDescription()))rotate=child;if("删除这张贴纸".contentEquals(child.getContentDescription()==null?"":child.getContentDescription()))remove=child;}
            if(rotate==null||remove==null)throw new Exception("Missing corner controls");
            if(!rotate.isShown()||!remove.isShown()||rotate.getLeft()>=remove.getLeft()||rotate.getTop()!=remove.getTop())throw new Exception("Controls are not top-left / top-right");
            if(rotate.getWidth()<PaperComponents.dp(c,48)||remove.getWidth()<PaperComponents.dp(c,48))throw new Exception("Sticker control touch targets too small");
        }catch(Throwable e){failure[0]=e;}});
        if(failure[0]!=null)throw new Exception("Corner geometry",failure[0]);
        for(int i=0;i<5;i++){
            clickDescription("向左旋转90度");
            JSONObject item=NutritionStore.find(NutritionStore.meal(NutritionStore.read(prefs,NutritionStore.DAYS),date,"breakfast"),"qa-breakfast");
            int expected=(360-((i+1)*90)%360)%360;
            if(NutritionStore.displayRotation(item)!=expected)throw new Exception("Rotation not persisted / full-turn mismatch");
            first.put("displayRotation",expected);
            if(!sample.toString().equals(prefs.getString(NutritionStore.DAYS,"{}")))throw new Exception("Rotation changed fields other than displayRotation");
        }
        if(!sourceHash.equals(fileDigest(source))||!alphaHash.equals(fileDigest(alpha)))throw new Exception("Rotation rewrote original photograph or editable alpha");
        screenshot("beta6-sticker-rotated");
        runOnMainSync(()->{try{invoke(screen,"renderBookDetail",new Class[]{String.class},date);}catch(Throwable e){failure[0]=e;}});waitForIdleSync();
        screenshot("beta6-notebook-small-media");
        runOnMainSync(()->{try{
            View decor=screen.getWindow().getDecorView(),board=decor.findViewWithTag("notebook_media_board");
            List<View> images=new ArrayList<>();if(board instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)board;for(int i=0;i<group.getChildCount();i++)if(group.getChildAt(i) instanceof android.widget.ImageView)images.add(group.getChildAt(i));}
            if(board==null||images.size()!=3)throw new Exception("Notebook does not contain exactly one small sticker per meal with photos hidden");
            if(board.getParent() instanceof android.view.ViewGroup){android.view.ViewGroup parent=(android.view.ViewGroup)board.getParent();if(parent.indexOfChild(board)<2)throw new Exception("Notebook stickers must follow its text");}
            for(int i=0;i<images.size();i++)if(images.get(i).getWidth()>PaperComponents.dp(c,64)||images.get(i).getHeight()>PaperComponents.dp(c,64))throw new Exception("Food rendered as a large notebook card");
            if(images.get(0).getRotation()!=270)throw new Exception("Notebook forgot food display angle");
            if(visibleText(decor).contains("编辑 / 重试 / 撤销"))throw new Exception("Notebook still appends food management cards");
            String before=prefs.getString(NutritionStore.DAYS,"{}");
            Bitmap share=(Bitmap)invoke(screen,"buildNotebookShareBitmap",new Class[]{String.class},date);
            if(share==null||share.getHeight()<=0)throw new Exception("Notebook export unavailable");
            File output=new File(c.getExternalFilesDir(null),"qa/beta6-notebook-export.png");output.getParentFile().mkdirs();
            try(OutputStream out=new FileOutputStream(output)){share.compress(Bitmap.CompressFormat.PNG,100,out);}share.recycle();
            if(!before.equals(prefs.getString(NutritionStore.DAYS,"{}")))throw new Exception("Notebook render/export changed records");
            invoke(screen,"showTab",new Class[]{int.class,boolean.class},0,false);
            media[0]=foodMedia(screen,"breakfast","qa-breakfast");if(!media[0].performLongClick())throw new Exception("Rebuilt sticker lost long press");
        }catch(Throwable e){failure[0]=e;}});
        if(failure[0]!=null)throw new Exception("Notebook compatibility",failure[0]);
        String beforeDelete=prefs.getString(NutritionStore.DAYS,"{}");
        clickDescription("删除这张贴纸");click("取消");
        if(!beforeDelete.equals(prefs.getString(NutritionStore.DAYS,"{}")))throw new Exception("Cancelling sticker deletion changed records");
        clickDescription("删除这张贴纸");click("确认");
        JSONObject removed=NutritionStore.meal(NutritionStore.read(prefs,NutritionStore.DAYS),date,"breakfast");
        if(NutritionStore.find(removed,"qa-breakfast")!=null||NutritionStore.containsPhoto(removed,source.getName())||NutritionStore.contains(removed.optJSONArray("stickers"),alpha.getName()))throw new Exception("Deleted food left duplicate original or alpha");
        if(NutritionStore.rotateDisplayLeft(c,date,"breakfast","qa-breakfast"))throw new Exception("Rotation resurrected a deleted food");
        JSONObject bin=NutritionStore.read(prefs,NutritionStore.PROFILE);int index=bin.getJSONArray("recycleBin").length()-1;
        if(bin.getJSONArray("recycleBin").getJSONObject(index).getJSONArray("foodItems").getJSONObject(0).optInt("displayRotation")!=270)throw new Exception("Recycle bundle lost display rotation");
        runOnMainSync(()->{try{invoke(screen,"restoreRecycleItem",new Class[]{int.class},index);}catch(Throwable e){failure[0]=e;}});
        if(failure[0]!=null)throw new Exception("Recycle restore",failure[0]);
        JSONObject restored=NutritionStore.meal(NutritionStore.read(prefs,NutritionStore.DAYS),date,"breakfast"),food=NutritionStore.find(restored,"qa-breakfast");
        if(food==null||NutritionStore.displayRotation(food)!=270||food.getJSONObject("nutrients").optInt("caloriesKcal")!=676||!"早餐备注：今天慢慢吃".equals(restored.optString("text")))throw new Exception("Restoring food lost angle, nutrients or old text");
        if(!sourceHash.equals(fileDigest(source))||!alphaHash.equals(fileDigest(alpha)))throw new Exception("Delete/restore damaged source media");
        screenshot("beta6-sticker-restored");
        // Pre-0.6 transparent imports can be the source of a food, stored in stickers not photos.
        runOnMainSync(()->{try{
            JSONObject all=NutritionStore.read(prefs,NutritionStore.DAYS),old=NutritionStore.meal(all,date,"breakfast");
            old.remove("photos");old.put("stickers",new JSONArray().put(source.getName()));
            prefs.edit().putString(NutritionStore.DAYS,all.toString()).commit();
            java.lang.reflect.Field data=MainActivity.class.getDeclaredField("days");data.setAccessible(true);data.set(screen,all);
            View thumb=(View)invoke(screen,"mediaThumbView",new Class[]{String.class,String.class,String.class,String.class,String.class,boolean.class,int.class},date,"breakfast",source.getName(),"stickers","贴纸",true,PaperComponents.dp(c,64));
            Object action=thumb.getTag();java.lang.reflect.Method rotate=MainActivity.class.getDeclaredMethod("rotateMealMediaLeft",action.getClass());rotate.setAccessible(true);rotate.invoke(screen,action);
            JSONObject turned=NutritionStore.find(NutritionStore.meal(NutritionStore.read(prefs,NutritionStore.DAYS),date,"breakfast"),"qa-breakfast");
            if(turned==null||NutritionStore.displayRotation(turned)!=180)throw new Exception("Legacy thumbnail did not use food metadata rotation");
            invoke(screen,"deleteMealSticker",new Class[]{String.class,String.class,String.class},date,"breakfast",source.getName());
            JSONObject gone=NutritionStore.meal(NutritionStore.read(prefs,NutritionStore.DAYS),date,"breakfast");
            if(NutritionStore.containsPhoto(gone,source.getName())||NutritionStore.find(gone,"qa-breakfast")!=null)throw new Exception("Sticker-array source survived food deletion");
            int last=NutritionStore.read(prefs,NutritionStore.PROFILE).getJSONArray("recycleBin").length()-1;
            invoke(screen,"restoreRecycleItem",new Class[]{int.class},last);
            JSONObject again=NutritionStore.meal(NutritionStore.read(prefs,NutritionStore.DAYS),date,"breakfast");
            if(!NutritionStore.contains(again.optJSONArray("stickers"),source.getName())||NutritionStore.displayRotation(NutritionStore.find(again,"qa-breakfast"))!=180)throw new Exception("Legacy source array/rotation was not restored");
        }catch(Throwable e){failure[0]=e;}});
        if(failure[0]!=null)throw new Exception("Legacy food-as-sticker compatibility",failure[0]);
        if(!sourceHash.equals(fileDigest(source))||!alphaHash.equals(fileDigest(alpha)))throw new Exception("Legacy media controls rewrote food pixels");
        Bundle result=new Bundle();result.putString("stream","PASS: actual long press with top-left rotation/top-right delete, 48dp targets, five persisted quarter turns without changing source/alpha/nutrition/generation, notebook small stickers below text without duplicate source or large cards, export purity, cancel deletion, delete/no resurrection and recycle restore with angle/nutrients/old text; nutrition is QA fixture\n");finish(Activity.RESULT_OK,result);
    }
    private interface UiAssertion { void run() throws Exception; }
    private void waitForCamera(FoodCaptureActivity capture)throws Exception {
        long deadline=android.os.SystemClock.elapsedRealtime()+30000;
        do{Object controller=ownField(capture,"camera");if(controller instanceof MealCameraController&&((MealCameraController)controller).isReady())return;Thread.sleep(100);}while(android.os.SystemClock.elapsedRealtime()<deadline);
        android.widget.TextView status=(android.widget.TextView)ownField(capture,"status");
        throw new Exception("Camera not ready: "+(status==null?"no status":status.getText()));
    }
    private static void cameraNear(double actual,double expected,String label)throws Exception {
        if(Double.isNaN(actual)||Double.isInfinite(actual)||Math.abs(actual-expected)>.0001)
            throw new Exception(label+": expected "+expected+", got "+actual);
    }
    private void cameraWideGeometryV14()throws Exception {
        cameraNear(MealCameraController.minimumZoomRatio(null),1,"Missing zoom range");
        cameraNear(MealCameraController.minimumZoomRatio(new android.util.Range<Float>(.5f,10f)),.5,"Ultrawide minimum");
        cameraNear(MealCameraController.minimumZoomRatio(new android.util.Range<Float>(1f,8f)),1,"Single lens minimum");
        cameraNear(MealCameraController.minimumZoomRatio(new android.util.Range<Float>(0f,8f)),1,"Zero minimum fallback");
        cameraNear(MealCameraController.minimumZoomRatio(new android.util.Range<Float>(-1f,8f)),1,"Negative minimum fallback");
        cameraNear(MealCameraController.minimumZoomRatio(new android.util.Range<Float>(1f,Float.POSITIVE_INFINITY)),1,"Infinite maximum fallback");
        cameraNear(MealCameraController.minimumZoomRatio(new android.util.Range<Float>(Float.NaN,Float.NaN)),1,"NaN range fallback");
        double main=MealCameraController.fieldOfViewScore(new android.util.SizeF(6.4f,4.8f),new float[]{4f});
        double wide=MealCameraController.fieldOfViewScore(new android.util.SizeF(5.12f,3.84f),new float[]{2f});
        double macro=MealCameraController.fieldOfViewScore(new android.util.SizeF(1.6f,1.2f),new float[]{1.5f});
        double tele=MealCameraController.fieldOfViewScore(new android.util.SizeF(6.4f,4.8f),new float[]{8f});
        if(!(wide>main&&main>macro&&macro>tele))throw new Exception("FoV ranking confuses shorter focal length with wider sensor view");
        cameraNear(main,2,"Main sensor FoV");
        cameraNear(MealCameraController.fieldOfViewScore(new android.util.SizeF(6.4f,4.8f),new float[]{Float.NaN,-1f,0f,8f,4f}),main,"Valid shortest focal selection");
        for(android.util.SizeF invalid:new android.util.SizeF[]{null,new android.util.SizeF(0,4),new android.util.SizeF(4,0)})
            cameraNear(MealCameraController.fieldOfViewScore(invalid,new float[]{4f}),-1,"Invalid sensor fallback");
        cameraNear(MealCameraController.fieldOfViewScore(new android.util.SizeF(6.4f,4.8f),null),-1,"Missing focal lengths");
        cameraNear(MealCameraController.fieldOfViewScore(new android.util.SizeF(6.4f,4.8f),new float[]{0,Float.NaN,Float.POSITIVE_INFINITY}),-1,"Invalid focal lengths");
        android.util.Size full=new android.util.Size(3264,2448),portrait=new android.util.Size(2448,3264),wideJpeg=new android.util.Size(3456,1944),small=new android.util.Size(640,480);
        if(!full.equals(MealCameraController.chooseJpegSize(new android.util.Size[]{wideJpeg,full,new android.util.Size(8000,6000),small})))throw new Exception("JPEG did not prefer a detailed 4:3 photo within budget");
        if(!portrait.equals(MealCameraController.chooseJpegSize(new android.util.Size[]{wideJpeg,portrait})))throw new Exception("Portrait JPEG aspect was misclassified");
        android.util.Size sufficient=new android.util.Size(1920,1080);
        if(!sufficient.equals(MealCameraController.chooseJpegSize(new android.util.Size[]{small,sufficient})))throw new Exception("4:3 preference selected a tiny photo instead of sufficient detail");
        android.util.Size floor=new android.util.Size(1200,900);
        if(!floor.equals(MealCameraController.chooseJpegSize(new android.util.Size[]{small,sufficient,floor})))throw new Exception("Megapixel 4:3 size was not preferred");
        android.util.Size low=new android.util.Size(800,600);
        if(!low.equals(MealCameraController.chooseJpegSize(new android.util.Size[]{small,low})))throw new Exception("Low resolution camera did not keep its largest supported photo");
        android.util.Size over=new android.util.Size(4000,3000);
        if(!over.equals(MealCameraController.chooseJpegSize(new android.util.Size[]{new android.util.Size(8000,6000),over})))throw new Exception("Over-budget-only camera did not retain bounded fallback");
        if(MealCameraController.chooseJpegSize(null)!=null||MealCameraController.chooseJpegSize(new android.util.Size[]{null,new android.util.Size(0,0)})!=null)throw new Exception("Invalid JPEG size list did not fail safely");
        android.util.Size matched=new android.util.Size(1280,960);
        android.util.Size preview=MealCameraController.choosePreviewSize(new android.util.Size[]{new android.util.Size(1920,1080),small,matched},full,900,1200,90,false,0);
        if(!matched.equals(preview))throw new Exception("Preview did not preserve JPEG ratio and sufficient resolution");
        if(MealCameraController.choosePreviewSize(null,full,900,1200,90,false,0)!=null)throw new Exception("Missing preview sizes accepted");
        for(android.util.Size buffer:new android.util.Size[]{new android.util.Size(1920,1080),new android.util.Size(1280,960),new android.util.Size(1024,1024)})
            for(int sensor:new int[]{0,90,180,270})for(int rotation:new int[]{0,90,180,270}){
                int width=900,height=1200;
                Matrix matrix=MealCameraController.previewTransform(width,height,buffer,sensor,rotation);
                RectF mapped=new RectF(0,0,width,height);matrix.mapRect(mapped);
                if(mapped.left<-.05f||mapped.top<-.05f||mapped.right>width+.05f||mapped.bottom>height+.05f)throw new Exception("Preview crops the buffer at sensor/display "+sensor+"/"+rotation+": "+mapped);
                if(Math.abs(mapped.centerX()-width/2f)>.05f||Math.abs(mapped.centerY()-height/2f)>.05f)throw new Exception("Fit preview is not centered");
                if(Math.abs(mapped.width()-width)>.05f&&Math.abs(mapped.height()-height)>.05f)throw new Exception("Fit preview leaves unnecessary margins on both axes");
                boolean swapped=((sensor+rotation)%180)!=0;
                double expected=swapped?(double)buffer.getHeight()/buffer.getWidth():(double)buffer.getWidth()/buffer.getHeight();
                cameraNear(mapped.width()/mapped.height(),expected,"Aspect-preserving fit transform");
            }
        if(!MealCameraController.previewTransform(900,1200,null,90,0).isIdentity()||!MealCameraController.previewTransform(0,1200,full,90,0).isIdentity())throw new Exception("Unready preview matrix is not safe");
        int[] back={90,180,270,0},front={90,0,270,180};
        for(int i=0;i<4;i++){
            int angle=i*90;
            if(MealCameraController.jpegOrientation(90,false,false,angle,0)!=back[i]||MealCameraController.jpegOrientation(90,true,false,angle,0)!=front[i])throw new Exception("JPEG orientation is incorrect at "+angle);
            if(MealCameraController.jpegOrientation(90,false,false,-1,angle)!=front[i]||MealCameraController.jpegOrientation(90,true,false,-1,angle)!=back[i])throw new Exception("JPEG display-rotation fallback is incorrect at "+angle);
            if(MealCameraController.jpegOrientation(90,false,true,angle,angle)!=0)throw new Exception("External camera must not acquire phone orientation");
        }
        if(MealCameraController.jpegOrientation(90,false,false,44,0)!=90||MealCameraController.jpegOrientation(90,false,false,46,0)!=180)throw new Exception("JPEG orientation rounding changed");
    }
    private static boolean cameraHasMode(int[] values,int expected){if(values!=null)for(int value:values)if(value==expected)return true;return false;}
    private void assertWideRequestV14(android.hardware.camera2.CameraCharacteristics c,android.hardware.camera2.CaptureRequest request)throws Exception {
        List<android.hardware.camera2.CaptureRequest.Key<?>> keys=c.getAvailableCaptureRequestKeys();
        if(android.os.Build.VERSION.SDK_INT>=30&&keys!=null&&keys.contains(android.hardware.camera2.CaptureRequest.CONTROL_ZOOM_RATIO)){
            android.util.Range<Float> range=c.get(android.hardware.camera2.CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
            if(range!=null&&range.getLower()>0&&range.getUpper()>0&&!Float.isInfinite(range.getLower())&&!Float.isInfinite(range.getUpper())&&!Float.isNaN(range.getLower())&&!Float.isNaN(range.getUpper())){
                Float zoom=request.get(android.hardware.camera2.CaptureRequest.CONTROL_ZOOM_RATIO);
                if(zoom==null)throw new Exception("A supported zoom request is missing");
                cameraNear(zoom,MealCameraController.minimumZoomRatio(range),"Request minimum zoom");
            }
        }
        Rect active=c.get(android.hardware.camera2.CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        if(active!=null&&active.width()>0&&active.height()>0&&keys!=null&&keys.contains(android.hardware.camera2.CaptureRequest.SCALER_CROP_REGION)&&!active.equals(request.get(android.hardware.camera2.CaptureRequest.SCALER_CROP_REGION)))throw new Exception("Request adds an active-array crop");
        if(keys!=null&&keys.contains(android.hardware.camera2.CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE)&&cameraHasMode(c.get(android.hardware.camera2.CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES),android.hardware.camera2.CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF)&&!Integer.valueOf(android.hardware.camera2.CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF).equals(request.get(android.hardware.camera2.CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE)))throw new Exception("Video stabilization may crop the meal");
        if(android.os.Build.VERSION.SDK_INT>=31&&keys!=null&&keys.contains(android.hardware.camera2.CaptureRequest.SCALER_ROTATE_AND_CROP)&&cameraHasMode(c.get(android.hardware.camera2.CameraCharacteristics.SCALER_AVAILABLE_ROTATE_AND_CROP_MODES),android.hardware.camera2.CaptureRequest.SCALER_ROTATE_AND_CROP_NONE)&&!Integer.valueOf(android.hardware.camera2.CaptureRequest.SCALER_ROTATE_AND_CROP_NONE).equals(request.get(android.hardware.camera2.CaptureRequest.SCALER_ROTATE_AND_CROP)))throw new Exception("System rotate-and-crop was not disabled");
    }
    private Object cameraWideRequestsV14(MealCameraController controller)throws Exception {
        final Object run=ownField(controller,"current");if(run==null)throw new Exception("Camera has no active run");
        android.os.Handler handler=(android.os.Handler)ownField(run,"handler");
        final Throwable[] failure={null};java.util.concurrent.CountDownLatch completed=new java.util.concurrent.CountDownLatch(1);
        if(!handler.post(()->{
            try{
                android.hardware.camera2.CameraCharacteristics c=(android.hardware.camera2.CameraCharacteristics)ownField(run,"characteristics");
                assertWideRequestV14(c,(android.hardware.camera2.CaptureRequest)ownField(run,"previewRequest"));
                android.hardware.camera2.CameraDevice camera=(android.hardware.camera2.CameraDevice)ownField(run,"camera");
                Method controls=MealCameraController.class.getDeclaredMethod("autoControls",run.getClass(),android.hardware.camera2.CaptureRequest.Builder.class);controls.setAccessible(true);
                for(int template:new int[]{android.hardware.camera2.CameraDevice.TEMPLATE_PREVIEW,android.hardware.camera2.CameraDevice.TEMPLATE_STILL_CAPTURE}){
                    android.hardware.camera2.CaptureRequest.Builder request=camera.createCaptureRequest(template);
                    request.addTarget(template==android.hardware.camera2.CameraDevice.TEMPLATE_STILL_CAPTURE?((android.media.ImageReader)ownField(run,"reader")).getSurface():(Surface)ownField(run,"previewSurface"));
                    controls.invoke(controller,run,request);assertWideRequestV14(c,request.build());
                }
            }catch(Throwable error){failure[0]=error;}finally{completed.countDown();}
        }))throw new Exception("Camera handler rejected request checks");
        if(!completed.await(10,java.util.concurrent.TimeUnit.SECONDS))throw new Exception("Camera request checks timed out");
        if(failure[0]!=null)throw new Exception("Wide-view capture request assertion",failure[0]);
        return run;
    }
    private void cameraWideV14(Context c,android.content.SharedPreferences prefs)throws Exception {
        cameraWideGeometryV14();
        if(c.checkSelfPermission(android.Manifest.permission.CAMERA)!=android.content.pm.PackageManager.PERMISSION_GRANTED)throw new Exception("Grant CAMERA on the isolated emulator before camera-wide-v14");
        FoodAnalysisJobService.cancelAll(c);FoodDraftStore.discard(c,FoodDraftStore.pendingSources(c));
        String date=new SimpleDateFormat("yyyy-MM-dd",Locale.CHINA).format(new Date());
        JSONObject profile=new JSONObject().put("new_ui_v2",true).put("decoration","true".equals(args.getString("night"))?"night":"yellow").put("seenAnnouncementVersion",c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName);
        String before=new JSONObject().put(date,new JSONObject().put("breakfast",new JSONObject().put("text","保留最小倍率测试前的早餐"))).toString();
        prefs.edit().putString(NutritionStore.PROFILE,profile.toString()).putString(NutritionStore.DAYS,before).commit();
        MainActivity screen=(MainActivity)startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        final FoodCaptureActivity[] capture={null};
        ActivityMonitor monitor=new ActivityMonitor(FoodCaptureActivity.class.getName(),null,false);addMonitor(monitor);
        try{
            uiAssert(()->invoke(screen,"chooseMealForPhoto",new Class[]{}));click("午餐");
            capture[0]=(FoodCaptureActivity)waitForMonitorWithTimeout(monitor,8000);removeMonitor(monitor);
            if(capture[0]==null)throw new Exception("Meal choice did not open the in-app camera");waitForCamera(capture[0]);
            MealCameraController controller=(MealCameraController)ownField(capture[0],"camera");
            Object oldRun=cameraWideRequestsV14(controller);
            uiAssert(()->{
                View decor=capture[0].getWindow().getDecorView();View viewport=decor.findViewWithTag("food_camera_frame");
                if(Math.abs(viewport.getWidth()*4-viewport.getHeight()*3)>4)throw new Exception("Wide camera changed portrait 3:4 viewport");
                android.view.TextureView texture=(android.view.TextureView)decor.findViewWithTag("food_camera_texture");
                Bitmap bitmap=texture.getBitmap(120,160);if(bitmap==null)throw new Exception("Wide-view live preview has no frame");bitmap.recycle();
            });screenshot("beta14-camera-minimum-live");
            uiAssert(()->{callActivityOnPause(capture[0]);callActivityOnResume(capture[0]);});waitForCamera(capture[0]);
            Object resumed=cameraWideRequestsV14(controller);
            if(oldRun==resumed||Boolean.TRUE.equals(ownField(oldRun,"active")))throw new Exception("Pause/resume reused a stale camera run");
            screenshot("beta14-camera-minimum-resumed");
            if(!before.equals(prefs.getString(NutritionStore.DAYS,"{}"))||!FoodDraftStore.pendingSources(c).isEmpty()||apiCalls.get()!=0)throw new Exception("Opening/widening camera wrote a diary, draft or API request");
            uiAssert(()->capture[0].getWindow().getDecorView().findViewWithTag("food_capture_cancel").performClick());waitForIdleSync();
            long deadline=android.os.SystemClock.elapsedRealtime()+5000;
            while((ownField(controller,"current")!=null||ownField(controller,"retiring")!=null)&&android.os.SystemClock.elapsedRealtime()<deadline){waitForIdleSync();Thread.sleep(40);}
            if(ownField(controller,"current")!=null||ownField(controller,"retiring")!=null)throw new Exception("Cancelled camera retained an active or retiring run");
            android.os.HandlerThread worker=(android.os.HandlerThread)ownField(resumed,"thread");worker.join(3000);
            if(worker.isAlive()||ownField(resumed,"camera")!=null||ownField(resumed,"reader")!=null||ownField(resumed,"session")!=null||ownField(resumed,"previewSurface")!=null)throw new Exception("Cancelled camera retained hardware/thread resources");
            if(!before.equals(prefs.getString(NutritionStore.DAYS,"{}"))||!FoodDraftStore.pendingSources(c).isEmpty())throw new Exception("Camera cancellation changed diary/drafts");
        }finally{
            removeMonitor(monitor);
            uiAssert(()->{if(capture[0]!=null&&!capture[0].isFinishing())capture[0].finish();screen.finish();});
        }
        Bundle result=new Bundle();result.putString("stream","PASS: minimum zoom valid/null/invalid ranges; sensor-aware wide/main/macro/tele FoV; detailed 4:3 JPEG selection and safe size fallbacks; 48 sensor/display/aspect full-fit matrices and JPEG orientation; live preview and PREVIEW/STILL template requests at minimum advertised zoom/full active crop/stabilization OFF/rotate NONE when supported; pause/resume retains minimum, camera/thread cleanup and unchanged diary/drafts. Emulator cannot prove real-phone ultrawide exposure or optical field of view.\n");finish(Activity.RESULT_OK,result);
    }
    private void internalCaptureFlow(Context c,android.content.SharedPreferences prefs,boolean galleryOnly)throws Exception {
        FoodAnalysisJobService.cancelAll(c);FoodDraftStore.discard(c,FoodDraftStore.pendingSources(c));
        String date=new SimpleDateFormat("yyyy-MM-dd",Locale.CHINA).format(new Date());
        JSONObject profile=new JSONObject().put("new_ui_v2",true).put("decoration","true".equals(args.getString("night"))?"night":"yellow").put("seenAnnouncementVersion",c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName);
        JSONObject baseline=new JSONObject().put(date,new JSONObject().put("breakfast",new JSONObject().put("text","保留已有早餐")));
        String before=baseline.toString();prefs.edit().putString(NutritionStore.PROFILE,profile.toString()).putString(NutritionStore.DAYS,before).commit();
        MainActivity screen=(MainActivity)startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        if(!galleryOnly){
            if(c.checkSelfPermission(android.Manifest.permission.CAMERA)!=android.content.pm.PackageManager.PERMISSION_GRANTED)throw new Exception("Grant camera permission on the isolated emulator before capture-live");
            ActivityMonitor captureMonitor=new ActivityMonitor(FoodCaptureActivity.class.getName(),null,false);addMonitor(captureMonitor);
            uiAssert(()->invoke(screen,"chooseMealForPhoto",new Class[]{}));click("午餐");
            FoodCaptureActivity capture=(FoodCaptureActivity)waitForMonitorWithTimeout(captureMonitor,8000);removeMonitor(captureMonitor);
            if(capture==null)throw new Exception("Photo action did not open in-app camera");waitForCamera(capture);
            uiAssert(()->{
                View decor=capture.getWindow().getDecorView();android.view.TextureView texture=(android.view.TextureView)decor.findViewWithTag("food_camera_texture");
                if(texture==null||!texture.isAvailable()||texture.getWidth()==0||texture.getHeight()==0)throw new Exception("No live Camera2 TextureView");
                Bitmap frame=texture.getBitmap(120,160);if(frame==null)throw new Exception("Live preview did not produce a frame");frame.recycle();
                if(!before.equals(prefs.getString(NutritionStore.DAYS,"{}"))||!FoodDraftStore.pendingSources(c).isEmpty())throw new Exception("Viewfinder created records or analysis jobs before shutter");
                View viewport=decor.findViewWithTag("food_camera_frame");if(!viewport.getClipToOutline())throw new Exception("Camera viewport lost rounded corners");
                if(Math.abs(viewport.getWidth()*4-viewport.getHeight()*3)>4)throw new Exception("Camera viewport must be portrait height:width 4:3");
                List<View> views=new ArrayList<>();collectViews(decor,views);int albums=0;
                for(View view:views){if("打开相册".equals(view.getContentDescription()))albums++;if(view instanceof android.widget.TextView&&("相机".contentEquals(((android.widget.TextView)view).getText())||"相册".contentEquals(((android.widget.TextView)view).getText())))throw new Exception("Duplicate top camera/album tab row remains");}
                if(albums!=1)throw new Exception("Capture page must have one album action");
                int safeTop=decor.getRootWindowInsets().getSystemWindowInsetTop(),safeBottom=decor.getRootWindowInsets().getSystemWindowInsetBottom();
                if(android.os.Build.VERSION.SDK_INT>=30){android.graphics.Insets safe=decor.getRootWindowInsets().getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());safeTop=safe.top;safeBottom=safe.bottom;}
                Rect title=screenRect(decor.findViewWithTag("food_capture_title")),controls=screenRect(decor.findViewWithTag("food_capture_shutter"));
                if(title.top<safeTop+PaperComponents.dp(c,16)||controls.bottom>decor.getHeight()-safeBottom)throw new Exception("Capture title/shutter overlaps system bars or cutout");
            });screenshot("beta10-camera-live");
            ActivityMonitor cancelPicker=new ActivityMonitor(){@Override public ActivityResult onStartActivity(Intent intent){if(intent!=null&&("android.provider.action.PICK_IMAGES".equals(intent.getAction())||Intent.ACTION_OPEN_DOCUMENT.equals(intent.getAction())))return new ActivityResult(Activity.RESULT_CANCELED,null);return null;}};
            addMonitor(cancelPicker);uiAssert(()->capture.getWindow().getDecorView().findViewWithTag("food_capture_gallery").performClick());waitForCamera(capture);removeMonitor(cancelPicker);
            if(!FoodDraftStore.pendingSources(c).isEmpty())throw new Exception("Cancelling bottom album picker created a draft");
            // Pause a still request before its JPEG is delivered. Reopening must not leave busy=true.
            uiAssert(()->{capture.getWindow().getDecorView().findViewWithTag("food_capture_shutter").performClick();callActivityOnPause(capture);callActivityOnResume(capture);});
            waitForCamera(capture);
            if(!FoodDraftStore.pendingSources(c).isEmpty())throw new Exception("A stale JPEG was delivered after pause");
            uiAssert(()->capture.getWindow().getDecorView().findViewWithTag("food_capture_shutter").performClick());
            String source=draftSource(c,date,"lunch");JSONObject draft=waitDraft(c,source,"awaiting_consent",90000);
            if(draft.has("nutrients")||!before.equals(prefs.getString(NutritionStore.DAYS,"{}")))throw new Exception("Camera committed/invented nutrition before explicit confirmation");
            Object output=ownField(capture,"ownedOutput");if(output instanceof android.net.Uri){try(InputStream ignored=c.getContentResolver().openInputStream((android.net.Uri)output)){throw new Exception("Camera temporary JPEG not cleaned after import");}catch(java.io.FileNotFoundException expected){}}
            screenshot("beta8-camera-processing");clickDescription("取消");
            if(FoodDraftStore.item(c,source)!=null||new File(c.getFilesDir(),source).exists()||!before.equals(prefs.getString(NutritionStore.DAYS,"{}")))throw new Exception("Camera draft cancellation damaged old diary or left source");
        }
        // The app screen owns this flow; only the OS media picker result is supplied as a fixture.
        final Throwable[] error={null};final android.net.Uri[] selected={null};
        ActivityMonitor picker=new ActivityMonitor(){@Override public ActivityResult onStartActivity(Intent intent){
            if(Intent.ACTION_CHOOSER.equals(intent.getAction()))intent=intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if(intent==null||(!"android.provider.action.PICK_IMAGES".equals(intent.getAction())&&!Intent.ACTION_OPEN_DOCUMENT.equals(intent.getAction())))return null;
            try{selected[0]=MealCameraProvider.createOutputUri(c);try(InputStream in=c.getAssets().open("qa/food.jpg");OutputStream out=c.getContentResolver().openOutputStream(selected[0])){byte[] bytes=new byte[8192];int count;while((count=in.read(bytes))!=-1)out.write(bytes,0,count);}return new ActivityResult(Activity.RESULT_OK,new Intent().setData(selected[0]));}
            catch(Throwable e){error[0]=e;return new ActivityResult(Activity.RESULT_CANCELED,null);}
        }};addMonitor(picker);
        uiAssert(()->{setMainField(screen,"pendingPhotoDate",date);setMainField(screen,"pendingPhotoMeal","dinner");invoke(screen,"launchMealPhotoPicker",new Class[]{});});
        String photo=draftSource(c,date,"dinner");JSONObject item=waitDraft(c,photo,"awaiting_consent",90000);removeMonitor(picker);
        if(error[0]!=null)throw new Exception("Gallery fixture result",error[0]);
        if(item.optString("cutoutFile").isEmpty())throw new Exception("Gallery did not generate its local cutout");
        if(!before.equals(prefs.getString(NutritionStore.DAYS,"{}")))throw new Exception("Gallery auto-committed before confirmation");
        if(galleryOnly&&c.checkSelfPermission(android.Manifest.permission.CAMERA)==android.content.pm.PackageManager.PERMISSION_GRANTED)throw new Exception("Gallery unexpectedly gained camera permission");
        screenshot("beta8-gallery-processing");clickDescription("确定");
        JSONObject meal=NutritionStore.meal(NutritionStore.read(prefs,NutritionStore.DAYS),date,"dinner");
        if(!NutritionStore.containsPhoto(meal,photo)||NutritionStore.forSource(meal,photo).length()!=1||FoodDraftStore.item(c,photo)!=null)throw new Exception("Gallery confirmation not idempotent/complete");
        // Duplicate edit action is absent from More, but the primary edit button remains reachable.
        final AlertDialog[] detail={null};uiAssert(()->detail[0]=((FoodUi)invoke(screen,"foodUi",new Class[]{})).details(date,"dinner",meal.getJSONArray("foodItems").getJSONObject(0)));
        waitForIdleSync();uiAssert(()->{View decor=detail[0].getWindow().getDecorView();if(decor.findViewWithTag("food_detail_edit")==null)throw new Exception("Primary edit button disappeared");decor.findViewWithTag("food_detail_more").performClick();});
        if(node("修改菜名、份量和营养")!=null||node("重新抠图")==null||node("重新识别")==null||node("手动修整贴纸")==null||node("查看估算依据")==null||node("撤销这次识别（保留原图）")!=null)throw new Exception("More menu has duplicate edit, obsolete undo or missing independent actions");
        screenshot("beta8-more-without-duplicate-edit");click("查看估算依据");if(node("AI 估算依据")==null)throw new Exception("More menu index mapping broken");click("知道了");
        Bundle result=new Bundle();result.putString("stream","PASS: "+(galleryOnly?"gallery with camera permission denied":"real Camera2 live preview, still JPEG, pause/reopen without stale capture, temporary cleanup, draft cancel")+", in-app gallery handoff to real local cutout, explicit confirm preserves old text, primary edit retained and four correctly mapped More actions; OS gallery selection uses QA fixture, emulator sensor is not a real phone\n");finish(Activity.RESULT_OK,result);
    }
    private void uiAssert(UiAssertion assertion)throws Exception {
        Throwable[] failure={null};runOnMainSync(()->{try{assertion.run();}catch(Throwable e){failure[0]=e;}});
        if(failure[0]!=null)throw new Exception("UI assertion",failure[0]);
    }
    private Object ownField(Object target,String name)throws Exception {
        java.lang.reflect.Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);
    }
    private void swipeWeek(View view,boolean forward)throws Exception {
        waitForIdleSync();long deadline=android.os.SystemClock.elapsedRealtime()+5000;
        while((!view.isLaidOut()||view.getWidth()==0||view.getHeight()==0)&&android.os.SystemClock.elapsedRealtime()<deadline)Thread.sleep(20);
        if(!view.isLaidOut()||view.getWidth()==0||view.getHeight()==0)throw new Exception("Week layout not ready for pointer injection");
        Rect bounds=screenRect(view);float x0=bounds.left+bounds.width()*(forward?.8f:.2f),x1=bounds.left+bounds.width()*(forward?.2f:.8f),y=bounds.centerY();
        long down=android.os.SystemClock.uptimeMillis();
        for(int i=0;i<=9;i++){
            int action=i==0?MotionEvent.ACTION_DOWN:i==9?MotionEvent.ACTION_UP:MotionEvent.ACTION_MOVE;
            MotionEvent event=MotionEvent.obtain(down,android.os.SystemClock.uptimeMillis(),action,x0+(x1-x0)*i/9,y,0);event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            getUiAutomation().injectInputEvent(event,true);event.recycle();Thread.sleep(16);
        }waitForIdleSync();Thread.sleep(UiTokens.WEEK_SNAP_MS+80);waitForIdleSync();
    }
    private void localTouch(View view,int action,float x,float y){long now=android.os.SystemClock.uptimeMillis();MotionEvent event=MotionEvent.obtain(now,now,action,x,y,0);view.onTouchEvent(event);event.recycle();}
    private void weekMotionChecks(MainActivity screen)throws Exception {
        TodayWeekStrip week=(TodayWeekStrip)screen.getWindow().getDecorView().findViewWithTag("today_week");Calendar before=week.weekStart();
        if(!android.animation.ValueAnimator.areAnimatorsEnabled()){uiAssert(()->{if(Math.abs(week.currentRow().getTranslationX())>.1f||ownField(week,"animator")!=null)throw new Exception("Reduced motion week not static");});return;}
        uiAssert(()->{
            float w=week.getWidth(),h=week.getHeight()/2f;localTouch(week,MotionEvent.ACTION_DOWN,w*.7f,h);localTouch(week,MotionEvent.ACTION_MOVE,w*.4f,h);
            if(week.currentRow().getTranslationX()>=-w*.25f)throw new Exception("Week does not follow drag");
            localTouch(week,MotionEvent.ACTION_UP,w*.4f,h);android.animation.ValueAnimator animation=(android.animation.ValueAnimator)ownField(week,"animator");
            if(animation==null)throw new Exception("Week release has no settle animation");animation.pause();animation.setCurrentPlayTime(60);
            if(week.currentRow().getTranslationX()>=-w*.3f||week.currentRow().getTranslationX()<=-w)throw new Exception("Missing actual intermediate translation frame");
            if(week.weekStart().getTimeInMillis()!=before.getTimeInMillis())throw new Exception("Week replaced content before sliding out");
        });screenshot("beta10-week-mid-transition");
        uiAssert(()->{
            float w=week.getWidth(),h=week.getHeight()/2f,offset=week.currentRow().getTranslationX();
            localTouch(week,MotionEvent.ACTION_DOWN,w*.4f,h);
            if(Math.abs(week.currentRow().getTranslationX()-offset)>.1f)throw new Exception("Interrupting transition snapped pixels");
            localTouch(week,MotionEvent.ACTION_MOVE,w*.8f,h);localTouch(week,MotionEvent.ACTION_CANCEL,w*.8f,h);
        });Thread.sleep(350);waitForIdleSync();
        uiAssert(()->{if(week.weekStart().getTimeInMillis()!=before.getTimeInMillis()||Math.abs(week.currentRow().getTranslationX())>.1f)throw new Exception("Interrupted/reversed/cancelled drag changed week or left translation");});
        uiAssert(()->{float w=week.getWidth(),h=week.getHeight()/2f;localTouch(week,MotionEvent.ACTION_DOWN,w*.7f,h);localTouch(week,MotionEvent.ACTION_MOVE,w*.4f,h);localTouch(week,MotionEvent.ACTION_UP,w*.4f,h);invoke(screen,"renderCurrent",new Class[]{});if(ownField(week,"animator")!=null)throw new Exception("Detached week kept animator");});
        Thread.sleep(250);uiAssert(()->{TodayWeekStrip current=(TodayWeekStrip)screen.getWindow().getDecorView().findViewWithTag("today_week");if(current.weekStart().getTimeInMillis()!=before.getTimeInMillis())throw new Exception("Detached animation callback changed new week");});
    }
    private void renameScan(Context c,android.content.SharedPreferences prefs)throws Exception {
        installTransport();streamName=false;FoodAnalysisJobService.cancelAll(c);FoodDraftStore.discard(c,FoodDraftStore.pendingSources(c));
        String date=new SimpleDateFormat("yyyy-MM-dd",Locale.CHINA).format(new Date()),source="draft_rename_"+UUID.randomUUID()+".jpg";
        layoutSource(c,source);JSONObject profile=new JSONObject().put("new_ui_v2",true).put("decoration","true".equals(args.getString("night"))?"night":"yellow")
            .put("seenAnnouncementVersion",c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName);
        String originalDays=new JSONObject().put(date,new JSONObject().put("manualCaloriesOverride",true).put("manualCaloriesTotal",300).put("manualCalories",300)).toString();
        prefs.edit().putString(NutritionStore.PROFILE,profile.toString()).putString(NutritionStore.DAYS,originalDays).commit();
        MainActivity screen=(MainActivity)startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        FoodAnalysisJobService.enqueueDraft(c,date,"lunch",source,false);
        uiAssert(()->invoke(screen,"showFoodProcessing",new Class[]{String.class,String.class,List.class},date,"lunch",Collections.singletonList(source)));
        Object processing=field(screen,"foodProcessing"),preview=ownField(processing,"preview");
        long deadline=android.os.SystemClock.elapsedRealtime()+8000;
        while(ownField(preview,"raw")==null&&android.os.SystemClock.elapsedRealtime()<deadline)Thread.sleep(40);
        if(ownField(preview,"raw")==null)throw new Exception("No original photo for scan preview");
        if(android.animation.ValueAnimator.areAnimatorsEnabled()){
            uiAssert(()->{android.animation.ValueAnimator scan=(android.animation.ValueAnimator)ownField(preview,"scanAnimator");if(scan==null)throw new Exception("No processing scan animation");scan.pause();scan.setCurrentPlayTime(450);});
            screenshot("beta10-original-scanning");
        }
        JSONObject draft=waitDraft(c,source,"awaiting_consent",120000);
        deadline=android.os.SystemClock.elapsedRealtime()+3000;while(ownField(preview,"sticker")==null&&android.os.SystemClock.elapsedRealtime()<deadline)Thread.sleep(20);
        if(android.animation.ValueAnimator.areAnimatorsEnabled()){
            uiAssert(()->{android.animation.ValueAnimator transition=(android.animation.ValueAnimator)ownField(preview,"animator");if(transition==null)throw new Exception("No original-to-sticker scan reveal");transition.pause();transition.setCurrentPlayTime(300);float reveal=(float)ownField(preview,"reveal");if(reveal<.45f||reveal>.55f)throw new Exception("Sticker appears without intermediate reveal");});
            screenshot("beta10-cutout-half-revealed");uiAssert(()->((android.animation.ValueAnimator)ownField(preview,"animator")).resume());Thread.sleep(450);
        }else if((float)ownField(preview,"reveal")!=1f)throw new Exception("Reduced motion cutout did not appear directly");
        String cutout=draft.getString("cutoutFile"),digest=FoodCutoutEngine.hash(new File(c.getFilesDir(),cutout));long modified=new File(c.getFilesDir(),cutout).lastModified();
        if(!FoodAnalysisJobService.renameAndAnalyze(c,date,"lunch",source,"待识别餐食（未授权测试）")||!"awaiting_consent".equals(FoodDraftStore.item(c,source).optString("analysisStatus"))||apiCalls.get()!=0)throw new Exception("Name correction before consent uploaded or lost waiting state");
        holdResponse=true;profile.put("foodAutoAnalyze",true).put("foodPhotoConsent",true).put("apiEnabled",true).put("apiEndpoint","https://food.qa.invalid/v1/chat/completions").put("apiModel","qa-vision").put("apiKey","qa-not-a-real-key");
        prefs.edit().putString(NutritionStore.PROFILE,profile.toString()).commit();FoodAnalysisJobService.schedule(c,false,0);
        if(!responseEntered.await(20,java.util.concurrent.TimeUnit.SECONDS))throw new Exception("Did not stage original in-flight API response");
        String oldId=FoodDraftStore.item(c,source).getString("analysisId"),renamed="鸡蛋青菜汤面（测试改名）";
        uiAssert(()->((View)ownField(processing,"dish")).performClick());click("取消");
        if(!oldId.equals(FoodDraftStore.item(c,source).getString("analysisId")))throw new Exception("Cancelling name edit changed task");
        uiAssert(()->((View)ownField(processing,"dish")).performClick());
        android.view.accessibility.AccessibilityNodeInfo input=descriptionNode("食物名称");Bundle value=new Bundle();value.putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,renamed);
        if(input==null||!input.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT,value))throw new Exception("Name editor not accessible");
        click("保存并重新识别");JSONObject pending=FoodDraftStore.item(c,source);
        if(!renamed.equals(pending.optString("dishName"))||oldId.equals(pending.optString("analysisId"))||pending.has("nutrients"))throw new Exception("Name correction did not invalidate prior nutrition generation");
        if(!prefs.getString(FoodAnalysisJobService.QUEUE,"[]").contains("\"cutoutDone\":true"))throw new Exception("Rename unnecessarily scheduled segmentation");
        holdResponse=false;responseReleased.countDown();JSONObject complete=waitDraft(c,source,"completed",45000);
        if(!renamed.equals(complete.optString("dishName"))||complete.getJSONObject("nutrients").getInt("caloriesKcal")!=500||!sawNameHint||!sawCorrectedName||!sameHintImage)throw new Exception("Corrected name + same original image did not produce new nutrients");
        if(apiCalls.get()!=2||!cutout.equals(complete.optString("cutoutFile"))||modified!=new File(c.getFilesDir(),cutout).lastModified()||!digest.equals(FoodCutoutEngine.hash(new File(c.getFilesDir(),cutout))))throw new Exception("Rename duplicated API work or altered alpha");
        String generation=complete.getString("analysisId");if(!FoodAnalysisJobService.renameAndAnalyze(c,date,"lunch",source,renamed)||!generation.equals(FoodDraftStore.item(c,source).getString("analysisId")))throw new Exception("Unchanged name was not idempotent");
        screenshot("beta10-renamed-reestimated");clickDescription("确定");
        JSONObject saved=NutritionStore.forSource(NutritionStore.meal(NutritionStore.read(prefs,NutritionStore.DAYS),date,"lunch"),source).getJSONObject(0);
        if(!renamed.equals(saved.optString("dishName"))||FoodUi.displayCalories(NutritionStore.read(prefs,NutritionStore.DAYS).getJSONObject(date))!=300)throw new Exception("Confirmation lost corrected name or manual calorie priority");
        if(!NutritionStore.rotateDisplayLeft(c,date,"lunch",saved.getString("id")))throw new Exception("Cannot stage display angle for saved rename");
        JSONObject days=NutritionStore.read(prefs,NutritionStore.DAYS),savedMeal=NutritionStore.meal(days,date,"lunch");savedMeal.remove("photos");savedMeal.remove("stickers");prefs.edit().putString(NutritionStore.DAYS,days.toString()).commit();
        String savedName="鸡蛋汤面（已保存改名测试）";
        if(!FoodAnalysisJobService.renameAndAnalyze(c,date,"lunch",source,savedName)||FoodDraftStore.item(c,source)!=null)throw new Exception("Saved food rename created a draft");
        deadline=android.os.SystemClock.elapsedRealtime()+30000;JSONObject updated=null;
        do{updated=NutritionStore.forSource(NutritionStore.meal(NutritionStore.read(prefs,NutritionStore.DAYS),date,"lunch"),source).optJSONObject(0);if(updated!=null&&"completed".equals(updated.optString("analysisStatus")))break;Thread.sleep(80);}while(android.os.SystemClock.elapsedRealtime()<deadline);
        if(updated==null||!"completed".equals(updated.optString("analysisStatus"))||!savedName.equals(updated.optString("dishName"))||NutritionStore.displayRotation(updated)!=270||apiCalls.get()!=3||!digest.equals(FoodCutoutEngine.hash(new File(c.getFilesDir(),cutout))))throw new Exception("Saved foodItems-only rename lost generation/angle/nutrition or rewrote cutout");
        Bundle result=new Bundle();result.putString("stream","PASS: scanner + partial cutout reveal (or reduced-motion direct display), title name editor cancel/save, same original JPEG + corrected name request, stale response protection, durable nutrition-only queue, no alpha rewrite, unchanged-name idempotency, explicit confirm and daily manual priority; nutrient responses are QA fixtures\n");finish(Activity.RESULT_OK,result);
    }
    private void progressV13(Context c,android.content.SharedPreferences prefs)throws Exception {
        installTransport();streamName=true;streamTotalsFirst=true;holdResponse=false;
        FoodAnalysisJobService.cancelAll(c);FoodDraftStore.discard(c,FoodDraftStore.pendingSources(c));
        String name=args.getString("name","beta13-progress"),date=new SimpleDateFormat("yyyy-MM-dd",Locale.CHINA).format(new Date());
        String source="draft_progress_"+UUID.randomUUID()+".jpg";layoutSource(c,source);
        JSONObject profile=new JSONObject().put("new_ui_v2",true).put("decoration","true".equals(args.getString("night"))?"night":"yellow").put("followSystemNight",false)
            .put("seenAnnouncementVersion",c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName)
            .put("foodAutoAnalyze",true).put("foodPhotoConsent",true).put("apiEnabled",true).put("apiEndpoint","https://food.qa.invalid/v1/chat/completions")
            .put("apiModel","qa-vision").put("apiModelMode","fast").put("apiKey","qa-not-a-real-key");
        String before=new JSONObject().put(date,new JSONObject().put("breakfast",new JSONObject().put("text","保留原来的早餐文字"))).toString();
        prefs.edit().remove("food_processing_preview").putString(NutritionStore.PROFILE,profile.toString()).putString(NutritionStore.DAYS,before).commit();
        MainActivity screen=(MainActivity)startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
        if(!FoodAnalysisJobService.enqueueDraft(c,date,"lunch",source,false))throw new Exception("Unable to create progressive recognition draft");
        uiAssert(()->invoke(screen,"showFoodProcessing",new Class[]{String.class,String.class,List.class},date,"lunch",Collections.singletonList(source)));
        Object processing=field(screen,"foodProcessing");Dialog review=(Dialog)ownField(processing,"dialog");View decor=review.getWindow().getDecorView();
        JSONObject draft=waitDraftName(c,source,120000);String generation=draft.getString("analysisId");
        long deadline=android.os.SystemClock.elapsedRealtime()+5000;
        while(FoodAnalysisJobService.previewCalories(generation)==null&&android.os.SystemClock.elapsedRealtime()<deadline)Thread.sleep(25);
        Double early=FoodAnalysisJobService.previewCalories(generation);
        if(early==null||early!=676d)throw new Exception("Same SSE request did not deliver temporary total before component suffix");
        draft=FoodDraftStore.item(c,source);
        if(draft==null||draft.has("nutrients")||draft.has("estimatedGrams")||draft.optString("cutoutFile").isEmpty())throw new Exception("Incomplete stream persisted unvalidated nutrition or missed real local cutout");
        if(!before.equals(prefs.getString(NutritionStore.DAYS,"{}"))||NutritionStore.totals(NutritionStore.read(prefs,NutritionStore.DAYS).getJSONObject(date)).optInt("count")!=0)throw new Exception("Display-only calories leaked into diary/day totals");
        if(prefs.getString(FoodDraftStore.KEY,"{}").contains("previewCalories")||draft.has("caloriesKcal")||draft.has("calorieLow")||draft.has("calorieHigh"))throw new Exception("Temporary SSE calorie value was persisted into draft JSON");
        waitForIdleSync();Thread.sleep(100);
        uiAssert(()->{
            android.widget.TextView calories=(android.widget.TextView)decor.findViewWithTag("food_calories"),status=(android.widget.TextView)ownField(processing,"status");
            View macro=decor.findViewWithTag("food_macro_grid"),tips=decor.findViewWithTag("food_tips"),mark=decor.findViewWithTag("food_progress_mark");
            if(calories==null||!calories.isShown()||!"约 676 kcal".contentEquals(calories.getText())||calories.getContentDescription()==null||!calories.getContentDescription().toString().contains("尚在校验"))throw new Exception("Early calorie preview is not visibly labelled provisional");
            if(macro==null||macro.getVisibility()!=View.GONE||tips==null||tips.getVisibility()!=View.GONE)throw new Exception("Unvalidated nutrients/tips became visible before SSE completion");
            if(!status.getText().toString().contains("营养分析中")||mark==null||!mark.isShown()||!Boolean.TRUE.equals(ownField(mark,"active")))throw new Exception("Processing stage does not reflect actual received totals");
            if(android.animation.ValueAnimator.areAnimatorsEnabled()&&ownField(mark,"spin")==null)throw new Exception("Active recognition star has no indeterminate motion");
            if(!android.animation.ValueAnimator.areAnimatorsEnabled()&&ownField(mark,"spin")!=null)throw new Exception("Reduced motion left an active status animator");
            if(!((View)ownField(processing,"confirm")).isEnabled())throw new Exception("Progress UI unnecessarily blocks early sticker confirmation");
        });screenshot(name+"-provisional-calories");
        if(apiCalls.get()!=1)throw new Exception("Showing early calories triggered an additional model request");
        nutritionReleased.countDown();JSONObject complete=waitDraft(c,source,"completed",15000);
        deadline=android.os.SystemClock.elapsedRealtime()+3000;while(FoodAnalysisJobService.previewCalories(generation)!=null&&android.os.SystemClock.elapsedRealtime()<deadline)Thread.sleep(20);
        if(FoodAnalysisJobService.previewCalories(generation)!=null||apiCalls.get()!=1)throw new Exception("Completed response left a preview or performed a tips request");
        if(complete.getJSONObject("nutrients").getInt("caloriesKcal")!=676||complete.optLong("recognitionMs",-1)<=0||complete.optLong("nameLatencyMs",-1)<0||complete.optLong("calorieLatencyMs",-1)<complete.optLong("nameLatencyMs",-1)||complete.optLong("recognitionMs")<complete.optLong("calorieLatencyMs"))throw new Exception("Final validation/timing metadata did not match the one-request staged fixture");
        if(!"qa-returned-vision".equals(complete.optString("providerModel"))||!"response".equals(complete.optString("providerModelSource")))throw new Exception("Progressive response lost same-request model provenance");
        if(!before.equals(prefs.getString(NutritionStore.DAYS,"{}")))throw new Exception("Completed preview auto-confirmed into diary");
        waitForIdleSync();Thread.sleep(400);
        uiAssert(()->{
            android.widget.TextView calories=(android.widget.TextView)decor.findViewWithTag("food_calories"),tip=(android.widget.TextView)decor.findViewWithTag("food_tip_body");
            if(!"676 kcal".contentEquals(calories.getText())||calories.getAlpha()!=1||!decor.findViewWithTag("food_macro_grid").isShown()||!decor.findViewWithTag("food_tips").isShown())throw new Exception("Final calorie/nutrient/tip groups did not settle promptly");
            String[] values={"88 g","24 g","24 g","3 g","1131 mg","450 g"};
            for(int i=0;i<values.length;i++)if(!values[i].contentEquals(((android.widget.TextView)decor.findViewWithTag("food_macro_"+i)).getText()))throw new Exception("Wrong final animated nutrient value at "+i);
            if(tip==null||!tip.getText().toString().contains("蛋白质约24g")||tip.getText().toString().contains("WHO")||tip.getText().toString().contains("USDA"))throw new Exception("Meal-specific local tip is absent or claims invented standards attribution");
            assertResultFooterV15(decor,complete,false);
            View mark=decor.findViewWithTag("food_progress_mark");if(mark.isShown()||ownField(mark,"spin")!=null||ownField(processing,"resultAnimator")!=null)throw new Exception("Completed result retained loading or long result animation");
        });screenshot(name+"-complete-nutrition-tips");
        clickDescription("确定");waitForIdleSync();
        JSONObject saved=NutritionStore.forSource(NutritionStore.meal(NutritionStore.read(prefs,NutritionStore.DAYS),date,"lunch"),source).optJSONObject(0);
        if(saved==null||FoodDraftStore.item(c,source)!=null||saved.getJSONObject("nutrients").getInt("caloriesKcal")!=676)throw new Exception("Explicit confirm did not atomically save the validated result");
        if(!Boolean.TRUE.equals(ownField(processing,"disposed"))||ownField(processing,"resultAnimator")!=null||ownField(ownField(processing,"progressMark"),"spin")!=null||ownField(ownField(processing,"preview"),"raw")!=null||ownField(ownField(processing,"preview"),"sticker")!=null)throw new Exception("Closing review retained timers/bitmaps");
        if(NutritionStore.totals(NutritionStore.read(prefs,NutritionStore.DAYS).getJSONObject(date)).optInt("count")!=1||apiCalls.get()!=1)throw new Exception("Progress/confirm duplicated validated food or API work");
        Bundle result=new Bundle();result.putString("stream","PASS: real local cutout then same-request SSE name/calorie preview, incomplete nutrients never persisted or totaled, explicit provisional UI, completed validation/timing/provenance, promptly settled numeric groups + local tips, stop frame without waiting for EOF, no extra tips API, explicit confirm and timer/bitmap cleanup; nutrition is a QA fixture, not real API accuracy or latency\n");finish(Activity.RESULT_OK,result);
    }
    private void assertResultFooterV15(View decor,JSONObject item,boolean detail)throws Exception {
        View reference=decor.findViewWithTag(detail?"food_detail_reference":"food_result_reference");
        if(!(reference instanceof android.widget.TextView)||!reference.isShown())throw new Exception("Missing final model/reference footnote");
        String value=((android.widget.TextView)reference).getText().toString(),model=FoodRecordText.modelName(item.optString("providerModel"));
        if(!value.endsWith("仅供参考。")||(!model.isEmpty()&&!value.contains("数据由"+model+"生成")))throw new Exception("Footer lost saved model provenance/reference wording");
        if(!detail){
            ViewGroup tips=(ViewGroup)decor.findViewWithTag("food_tips");
            if(reference.getParent()!=tips||tips.getChildAt(tips.getChildCount()-1)!=reference)throw new Exception("Model provenance is not the last tip child");
            View old=decor.findViewWithTag("food_result_status_note");
            if(old==null||old.getVisibility()!=View.GONE)throw new Exception("Completed result retains the old estimate/range row");
        }else{
            View tip=decor.findViewWithTag("food_detail_tip");ViewGroup parent=(ViewGroup)reference.getParent();
            if(tip==null||tip.getParent()!=parent||parent.indexOfChild(reference)!=parent.indexOfChild(tip)+1)throw new Exception("Detail provenance is not immediately after the tip");
        }
        if(visibleText(decor).matches("(?s).*AI\\s*估算\\s*·[^\\n]*[–-][^\\n]*kcal.*"))throw new Exception("Old standalone AI estimate/range is still visible");
        if(!item.has("calorieLow")||!item.has("calorieHigh")||!item.has("confidence"))throw new Exception("Display simplification removed saved uncertainty fields");
    }
    private void refinementsV9(Context c,android.content.SharedPreferences prefs)throws Exception {
        FoodAnalysisJobService.cancelAll(c);FoodDraftStore.discard(c,FoodDraftStore.pendingSources(c));
        String date=new SimpleDateFormat("yyyy-MM-dd",Locale.CHINA).format(new Date());
        JSONObject profile=new JSONObject().put("new_ui_v2",true).put("decoration","true".equals(args.getString("night"))?"night":"yellow")
            .put("seenAnnouncementVersion",c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName);
        prefs.edit().putString(NutritionStore.PROFILE,profile.toString()).putString(NutritionStore.DAYS,"{}").commit();
        MainActivity screen=(MainActivity)startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        uiAssert(()->{View decor=screen.getWindow().getDecorView();String text=visibleText(decor);if(decor.findViewWithTag("day_empty")!=null||text.contains("添加第一张餐图")||text.contains("拍照或从相册添加食物贴纸"))throw new Exception("Redundant empty add block still exists");assertUnifiedHome(screen,c,true);});
        screenshot("beta9-empty-paper");
        weekMotionChecks(screen);
        TodayWeekStrip strip=(TodayWeekStrip)screen.getWindow().getDecorView().findViewWithTag("today_week");Calendar initial=strip.weekStart();
        swipeWeek(strip,false);Calendar previous=(Calendar)initial.clone();previous.add(Calendar.DATE,-7);
        if(strip.weekStart().getTimeInMillis()!=previous.getTimeInMillis())throw new Exception("Right swipe did not move to previous week");
        if(!date.equals(field(screen,"homeDateKey"))||!"{}".equals(prefs.getString(NutritionStore.DAYS,"{}")))throw new Exception("Browsing week changed selected day or wrote records");
        uiAssert(()->invoke(screen,"renderCurrent",new Class[]{}));strip=(TodayWeekStrip)screen.getWindow().getDecorView().findViewWithTag("today_week");
        if(strip.weekStart().getTimeInMillis()!=previous.getTimeInMillis())throw new Exception("Normal refresh lost browsed week");
        swipeWeek(strip,true);swipeWeek(strip,true);Calendar next=(Calendar)initial.clone();next.add(Calendar.DATE,7);
        if(strip.weekStart().getTimeInMillis()!=next.getTimeInMillis())throw new Exception("Left swipe did not move to next week: expected "+next.getTime()+", actual "+strip.weekStart().getTime());
        uiAssert(()->{TodayWeekStrip week=(TodayWeekStrip)screen.getWindow().getDecorView().findViewWithTag("today_week");for(int i=0;i<7;i++)if(week.currentRow().getChildAt(i).isEnabled())throw new Exception("Future week allows selecting a future diary day");});
        swipeWeek(strip,false);swipeWeek(strip,false);screenshot("beta9-week-previous");
        uiAssert(()->{TodayWeekStrip week=(TodayWeekStrip)screen.getWindow().getDecorView().findViewWithTag("today_week");week.currentRow().getChildAt(2).performClick();});
        Calendar picked=(Calendar)previous.clone();picked.add(Calendar.DATE,2);String pickedKey=new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).format(picked.getTime());
        if(!pickedKey.equals(field(screen,"homeDateKey")))throw new Exception("Clicking previous-week date did not switch diary");
        uiAssert(()->{Calendar jan=Calendar.getInstance();jan.set(2026,Calendar.JANUARY,5);TodayWeekStrip isolated=new TodayWeekStrip(screen,false,jan,jan,null);isolated.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD,null);
            Calendar year=isolated.weekStart();if(year.get(Calendar.YEAR)!=2025||year.get(Calendar.MONTH)!=Calendar.DECEMBER||year.get(Calendar.DAY_OF_MONTH)!=29)throw new Exception("Cross-year week or accessibility action is wrong");});
        JSONObject sample=unifiedDays(c,date),day=sample.getJSONObject(date),food=day.getJSONObject("breakfast").getJSONArray("foodItems").getJSONObject(0);
        food.put("dishName","番茄炒蛋饭（测试）").put("providerModel","qa-display-model").put("assumptions",new JSONArray().put("按约350克估算").put("假设用油约12克").put("照片无法准确判断隐藏用油"));
        day.getJSONObject("breakfast").remove("text");day.getJSONObject("lunch").getJSONArray("foodItems").getJSONObject(0).put("dishName","蔬菜炒面（测试）");
        // Existing book construction materializes the five legacy meal objects.
        // Supply that baseline here; the new text readers must not change it.
        day.put("afternoon_tea",new JSONObject()).put("snack",new JSONObject());
        String before=sample.toString();prefs.edit().putString(NutritionStore.DAYS,before).commit();
        uiAssert(()->{setMainField(screen,"days",sample);invoke(screen,"renderBookDetail",new Class[]{String.class},date);
            String story=(String)invoke(screen,"notebookDayStory",new Class[]{String.class,JSONObject.class},date,day);
            if(!story.contains("番茄炒蛋饭")||!story.contains("蔬菜炒面")||story.contains("张图片")||story.contains("张贴纸"))throw new Exception("Notebook still narrates media counts instead of dish names");
            @SuppressWarnings("unchecked") List<String> lines=(List<String>)invoke(screen,"notebookStoryLines",new Class[]{String.class,JSONObject.class},date,day);
            if(!lines.get(0).contains("番茄炒蛋饭"))throw new Exception("Split notebook does not use food name");
            if(!((String)invoke(screen,"notebookMediaLine",new Class[]{JSONObject.class},day)).isEmpty())throw new Exception("Structured meal has redundant photo/sticker count line");
            assertRenderPure(screen,prefs,before);
        });screenshot("beta9-notebook-food-names");
        final AlertDialog[] detail={null};uiAssert(()->detail[0]=((FoodUi)invoke(screen,"foodUi",new Class[]{})).details(date,"breakfast",food));
        uiAssert(()->detail[0].getWindow().getDecorView().findViewWithTag("food_detail_more").performClick());click("查看估算依据");
        List<android.view.accessibility.AccessibilityNodeInfo> notes=getUiAutomation().getRootInActiveWindow().findAccessibilityNodeInfosByText("qa-display-model");
        if(notes.isEmpty())throw new Exception("Estimate panel missing actual record model footer");String shown=notes.get(0).getText().toString();
        if(shown.contains("无法")||!shown.contains("假设用油约12克")||!shown.contains("仅供参考"))throw new Exception("Estimate panel text is not concise quantified assumptions");
        screenshot("beta9-estimate-notes");click("知道了");
        if(!before.equals(prefs.getString(NutritionStore.DAYS,"{}")))throw new Exception("Reading new notebook/estimate text mutated diary");
        Bundle result=new Bundle();result.putString("stream","PASS: real left/right week swipes, read-only future weeks, refresh retention, previous-day selection, cross-year accessibility, no redundant empty add block, notebook food names in both layouts, no structured media-count line, concise estimate assumptions and recorded model footer, read purity; names/nutrition are labelled QA fixtures\n");finish(Activity.RESULT_OK,result);
    }
    private void setMainField(MainActivity screen,String name,Object value)throws Exception {
        java.lang.reflect.Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(screen,value);
    }
    private Rect screenRect(View view){int[] p=new int[2];view.getLocationOnScreen(p);return new Rect(p[0],p[1],p[0]+view.getWidth(),p[1]+view.getHeight());}
    private void assertMoreAlignment(MainActivity screen,Context c)throws Exception {
        View decor=screen.getWindow().getDecorView(),paper=decor.findViewWithTag("today_paper_viewport"),more=decor.findViewWithTag("today_more");
        if(more==null||more.getWidth()<PaperComponents.dp(c,48)||more.getHeight()<PaperComponents.dp(c,48)||!more.isClickable())throw new Exception("More lost its 48dp click target");
        Bitmap icon=Bitmap.createBitmap(more.getWidth(),more.getHeight(),Bitmap.Config.ARGB_8888);more.draw(new Canvas(icon));
        int left=icon.getWidth(),right=-1;for(int y=0;y<icon.getHeight();y++)for(int x=0;x<icon.getWidth();x++)if(Color.alpha(icon.getPixel(x,y))>64){left=Math.min(left,x);right=Math.max(right,x);}
        icon.recycle();Rect bounds=screenRect(more),sheet=screenRect(paper);
        int ruleRight=sheet.right-PaperComponents.dp(c,TodayPaperView.RULE_INSET);
        if(right<0||right-left>PaperComponents.dp(c,18)||Math.abs(bounds.left+right+1-ruleRight)>2)throw new Exception("More glyph is not compact/right-aligned to the paper ruling");
        if(!sheet.contains(bounds))throw new Exception("More hit area extends outside paper");
        Rect visible=new Rect();more.getGlobalVisibleRect(visible);if(visible.width()!=more.getWidth()||visible.height()!=more.getHeight())throw new Exception("Ancestors reduced More's accessible target");
        // Also paint ancestors: a child-only snapshot would miss clipping by its padded parents.
        Bitmap rendered=Bitmap.createBitmap(paper.getWidth(),paper.getHeight(),Bitmap.Config.ARGB_8888);paper.draw(new Canvas(rendered));
        int x=ruleRight-sheet.left-2,y=bounds.centerY()-sheet.top;int actual=rendered.getPixel(x,y),ink=UiTokens.ink("night".equals(((JSONObject)field(screen,"profile")).optString("decoration")));
        rendered.recycle();if(Math.abs(Color.red(actual)-Color.red(ink))>25)throw new Exception("Paper ancestors clipped the trailing dot");
    }
    private AlertDialog goalDialog(FoodUi foodUi)throws Exception {
        Method method=FoodUi.class.getDeclaredMethod("goal");method.setAccessible(true);return (AlertDialog)method.invoke(foodUi);
    }
    private void minimalDialogs(Context c,android.content.SharedPreferences prefs)throws Exception {
        FoodAnalysisJobService.cancelAll(c);FoodDraftStore.discard(c,FoodDraftStore.pendingSources(c));
        String date=new SimpleDateFormat("yyyy-MM-dd",Locale.CHINA).format(new Date()),name=args.getString("name","beta11");
        JSONObject sample=unifiedDays(c,date);sample.getJSONObject(date).put("manualCaloriesOverride",true).put("manualCaloriesTotal",1990);
        JSONObject profile=new JSONObject().put("new_ui_v2",true).put("decoration","true".equals(args.getString("night"))?"night":"yellow")
            .put("photoCollection",false).put("seenAnnouncementVersion",c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName);
        String before=sample.toString();prefs.edit().putString(NutritionStore.PROFILE,profile.toString()).putString(NutritionStore.DAYS,before).commit();
        MainActivity screen=(MainActivity)startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        Thread.sleep(500);uiAssert(()->assertMoreAlignment(screen,c));screenshot(name+"-aligned-home");
        Rect[] touch={null};uiAssert(()->touch[0]=screenRect(screen.getWindow().getDecorView().findViewWithTag("today_more")));
        float touchX=touch[0].right-PaperComponents.dp(c,5),touchY=touch[0].exactCenterY();long touchTime=android.os.SystemClock.uptimeMillis();
        android.view.MotionEvent down=android.view.MotionEvent.obtain(touchTime,touchTime,android.view.MotionEvent.ACTION_DOWN,touchX,touchY,0),up=android.view.MotionEvent.obtain(touchTime,touchTime+80,android.view.MotionEvent.ACTION_UP,touchX,touchY,0);
        sendPointerSync(down);sendPointerSync(up);down.recycle();up.recycle();
        waitForIdleSync();long menuDeadline=android.os.SystemClock.elapsedRealtime()+3000;
        while(node("当天营养与热量目标")==null&&android.os.SystemClock.elapsedRealtime()<menuDeadline)Thread.sleep(50);
        if(node("当天营养与热量目标")==null)throw new Exception("Right-aligned More no longer opens day menu");
        sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);waitForIdleSync();
        final FoodUi[] foodUi={null};final AlertDialog[] summary={null},goal={null};
        uiAssert(()->{foodUi[0]=(FoodUi)invoke(screen,"foodUi",new Class[]{});summary[0]=foodUi[0].dayNutrition(date);});
        waitForIdleSync();
        uiAssert(()->{
            View decor=summary[0].getWindow().getDecorView(),body=decor.findViewWithTag("day_nutrition_summary"),entry=decor.findViewWithTag("day_nutrition_goal");
            if(body==null||body.getBackground()!=null||body.getPaddingLeft()!=PaperComponents.dp(c,24)||body.getPaddingRight()!=body.getPaddingLeft())throw new Exception("Nutrition summary still has nested card or lost side space");
            if(entry==null||!entry.isClickable()||entry.getHeight()<PaperComponents.dp(c,48))throw new Exception("Goal entry inaccessible");
            if(!visibleText(body).contains("1990 kcal"))throw new Exception("Nutrition summary lost manual calorie priority");
        });screenshot(name+"-nutrition-unframed");
        // Exercise the visible entry and cancel route before inspecting a second instance directly.
        click("设置每日热量目标  ›");if(descriptionNode("每日热量目标，千卡，留空关闭")==null)throw new Exception("Visible goal entry no longer opens editor");
        screenshot(name+"-goal-via-entry");click("取消");uiAssert(()->summary[0].dismiss());
        uiAssert(()->goal[0]=goalDialog(foodUi[0]));
        waitForIdleSync();
        uiAssert(()->{
            View decor=goal[0].getWindow().getDecorView(),body=decor.findViewWithTag("daily_calorie_goal_body");
            android.widget.EditText input=(android.widget.EditText)decor.findViewWithTag("daily_calorie_goal_input");
            if(body==null||body.getBackground()!=null||input==null||input.getHeight()<PaperComponents.dp(c,48)||!input.isSingleLine())throw new Exception("Goal input lost plain single-line layout: height="+(input==null?-1:input.getHeight())+", expected="+PaperComponents.dp(c,48)+", singleLine="+(input!=null&&input.isSingleLine()));
            Rect box=screenRect(body),field=screenRect(input);if(field.left-box.left!=PaperComponents.dp(c,24)||box.right-field.right!=PaperComponents.dp(c,24))throw new Exception("Goal input touches dialog edges");
            Bitmap background=Bitmap.createBitmap(input.getWidth(),input.getHeight(),Bitmap.Config.ARGB_8888);input.getBackground().draw(new Canvas(background));
            int upper=0,lower=0;for(int y=0;y<background.getHeight();y++)for(int x=0;x<background.getWidth();x++)if(Color.alpha(background.getPixel(x,y))>16){if(y<background.getHeight()*3/4)upper++;else lower++;}
            background.recycle();if(upper>0||lower==0)throw new Exception("Goal background is not a simple underline");
            input.setText("2000");
        });screenshot(name+"-goal-underline");
        click("保存");
        if(NutritionStore.read(prefs,NutritionStore.PROFILE).optInt("dailyCalorieGoal")!=2000)throw new Exception("Saving target failed");
        for(String value:new String[]{"299","10001","999999999999999999999"}){
            uiAssert(()->{goal[0]=goalDialog(foodUi[0]);((android.widget.EditText)goal[0].getWindow().getDecorView().findViewWithTag("daily_calorie_goal_input")).setText(value);});click("保存");
            if(NutritionStore.read(prefs,NutritionStore.PROFILE).optInt("dailyCalorieGoal")!=2000)throw new Exception("Invalid target was persisted: "+value);
        }
        uiAssert(()->{goal[0]=goalDialog(foodUi[0]);((android.widget.EditText)goal[0].getWindow().getDecorView().findViewWithTag("daily_calorie_goal_input")).setText("2300");});click("取消");
        if(NutritionStore.read(prefs,NutritionStore.PROFILE).optInt("dailyCalorieGoal")!=2000)throw new Exception("Cancel changed target");
        for(String value:new String[]{"300","10000",""}){
            uiAssert(()->{goal[0]=goalDialog(foodUi[0]);((android.widget.EditText)goal[0].getWindow().getDecorView().findViewWithTag("daily_calorie_goal_input")).setText(value);});click("保存");
            int expected=value.isEmpty()?0:Integer.parseInt(value);
            if(NutritionStore.read(prefs,NutritionStore.PROFILE).optInt("dailyCalorieGoal")!=expected)throw new Exception("Valid/empty target was not saved");
        }
        uiAssert(()->{assertMoreAlignment(screen,c);assertRenderPure(screen,prefs,before);if(FoodUi.displayCalories(NutritionStore.read(prefs,NutritionStore.DAYS).optJSONObject(date))!=1990)throw new Exception("Goal edits changed calorie override");});
        Bundle result=new Bundle();result.putString("stream","PASS: unframed summary, inset native underline field, actual goal/menu click routes, compact trailing dots aligned within 2px including ancestor clipping, 48dp targets, save/cancel/empty/min/max/invalid/overflow targets, unchanged diary/manual priority; QA nutrition fixtures, no API requests\n");finish(Activity.RESULT_OK,result);
    }
    private void polishScenario(Context c,android.content.SharedPreferences prefs)throws Exception {
        FoodAnalysisJobService.cancelAll(c);FoodDraftStore.discard(c,FoodDraftStore.pendingSources(c));
        String date=new SimpleDateFormat("yyyy-MM-dd",Locale.CHINA).format(new Date()),name=args.getString("name","beta7");
        JSONObject sample=unifiedDays(c,date),profile=new JSONObject().put("new_ui_v2",true)
            .put("decoration","true".equals(args.getString("night"))?"night":"yellow").put("photoCollection",false)
            .put("seenAnnouncementVersion",c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName);
        prefs.edit().putString(NutritionStore.PROFILE,profile.toString()).putString(NutritionStore.DAYS,sample.toString()).commit();
        MainActivity screen=(MainActivity)startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        String before=prefs.getString(NutritionStore.DAYS,"{}");Rect[] fixed={null};
        uiAssert(()->{
            View decor=screen.getWindow().getDecorView(),viewport=decor.findViewWithTag("today_paper_viewport"),scroll=decor.findViewWithTag("today_paper_scroll"),triangle=decor.findViewWithTag("today_date_triangle"),more=decor.findViewWithTag("today_more");
            if(viewport==null||scroll==null||viewport.getHeight()<PaperComponents.dp(c,100)||!viewport.getClipToOutline())throw new Exception("Missing fixed rounded paper viewport");
            android.graphics.Outline outline=new android.graphics.Outline();viewport.getBackground().getOutline(outline);if(!outline.canClip()||outline.getRadius()<PaperComponents.dp(c,30))throw new Exception("Paper does not have four native rounded corners");
            fixed[0]=screenRect(viewport);Rect fab=screenRect((View)field(screen,"fab"));
            if(fixed[0].bottom>fab.top-PaperComponents.dp(c,10))throw new Exception("Paper lower corners overlap bottom controls");
            if(triangle==null||triangle instanceof android.widget.TextView)throw new Exception("Date arrow still depends on the handwriting glyph");
            if(more==null||more.getWidth()<PaperComponents.dp(c,48)||more.getHeight()<PaperComponents.dp(c,48))throw new Exception("More control touch target too small");
            Bitmap icon=Bitmap.createBitmap(more.getWidth(),more.getHeight(),Bitmap.Config.ARGB_8888);more.draw(new Canvas(icon));
            int left=icon.getWidth(),right=-1;for(int y=0;y<icon.getHeight();y++)for(int x=0;x<icon.getWidth();x++)if(Color.alpha(icon.getPixel(x,y))>64){left=Math.min(left,x);right=Math.max(right,x);}
            icon.recycle();if(right-left>PaperComponents.dp(c,18))throw new Exception("Three dots are not compact");assertRenderPure(screen,prefs,before);
        });screenshot(name+"-rounded-paper-top");
        uiAssert(()->((android.widget.ScrollView)screen.getWindow().getDecorView().findViewWithTag("today_paper_scroll")).scrollTo(0,PaperComponents.dp(c,250)));
        screenshot(name+"-rounded-paper-middle");
        uiAssert(()->{View view=screen.getWindow().getDecorView().findViewWithTag("today_paper_viewport");if(!fixed[0].equals(screenRect(view)))throw new Exception("Paper corners moved while scrolling");((android.widget.ScrollView)screen.getWindow().getDecorView().findViewWithTag("today_paper_scroll")).fullScroll(View.FOCUS_DOWN);});
        screenshot(name+"-rounded-paper-bottom");
        uiAssert(()->{View decor=screen.getWindow().getDecorView(),view=decor.findViewWithTag("today_paper_viewport");if(!fixed[0].equals(screenRect(view)))throw new Exception("Paper lower corners appear only at end");List<View> labels=tagged(decor.findViewWithTag("today_paper"),"day_entry_label");Rect last=screenRect(labels.get(labels.size()-1));if(last.bottom>fixed[0].bottom||last.top<fixed[0].top)throw new Exception("Last food label not reachable inside paper");assertRenderPure(screen,prefs,before);});
        // Inspect actual dialog geometry and the two real click routes, not a web mockup.
        final AlertDialog[] detail={null};
        JSONObject meal=sample.getJSONObject(date).getJSONObject("breakfast"),food=meal.getJSONArray("foodItems").getJSONObject(0);
        uiAssert(()->detail[0]=((FoodUi)invoke(screen,"foodUi",new Class[]{})).details(date,"breakfast",food));
        screenshot(name+"-food-detail");
        uiAssert(()->{
            View decor=detail[0].getWindow().getDecorView();android.view.ViewGroup body=(android.view.ViewGroup)decor.findViewWithTag("food_detail");
            View image=decor.findViewWithTag("food_detail_image"),edit=decor.findViewWithTag("food_detail_edit"),more=decor.findViewWithTag("food_detail_more");
            if(body==null||body.getPaddingTop()!=body.getPaddingBottom()||body.getPaddingTop()<PaperComponents.dp(c,24))throw new Exception("Detail top/bottom white space is not equal");
            if(body.getHeight()-more.getBottom()!=body.getPaddingTop())throw new Exception("Extra blank remains below More button");
            if(decor.findViewWithTag("food_detail_original")!=null||visibleText(body).contains("查看原图"))throw new Exception("Standalone original-photo row still exists");
            if(!image.isClickable()||more.getWidth()!=edit.getWidth()||more.getHeight()!=edit.getHeight())throw new Exception("Detail image/button sizing inconsistent");
            if(((android.graphics.drawable.GradientDrawable)more.getBackground()).getColor().getDefaultColor()!=((Integer)field(screen,"accentSoft")))throw new Exception("More button ignores active theme");
            image.performClick();if(!Boolean.TRUE.equals(field(screen,"inPhotoViewer")))throw new Exception("Clicking food image did not open original");assertRenderPure(screen,prefs,before);invoke(screen,"closePhotoViewer",new Class[]{});
        });
        uiAssert(()->{JSONObject active=(JSONObject)field(screen,"profile");active.put("decoration","blue");invoke(screen,"saveProfile",new Class[]{});invoke(screen,"applyDecoration",new Class[]{});detail[0]=((FoodUi)invoke(screen,"foodUi",new Class[]{})).details(date,"breakfast",food);});
        screenshot(name+"-food-detail-blue-theme");
        uiAssert(()->{View more=detail[0].getWindow().getDecorView().findViewWithTag("food_detail_more");int fill=((android.graphics.drawable.GradientDrawable)more.getBackground()).getColor().getDefaultColor();if(fill!=((Integer)field(screen,"accentSoft")))throw new Exception("Detail theme did not update");more.performClick();});
        if(node("重新抠图")==null||node("重新识别")==null)throw new Exception("Full-width More button lost independent actions");sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);waitForIdleSync();
        uiAssert(()->{JSONObject active=(JSONObject)field(screen,"profile");active.put("decoration","true".equals(args.getString("night"))?"night":"yellow");invoke(screen,"saveProfile",new Class[]{});invoke(screen,"applyDecoration",new Class[]{});invoke(screen,"showTab",new Class[]{int.class,boolean.class},3,false);});
        screenshot(name+"-my-clean-icons");
        uiAssert(()->{List<View> icons=tagged(screen.getWindow().getDecorView(),"settings_row_icon");if(icons.isEmpty())throw new Exception("Settings icon markers missing");for(View icon:icons){if(icon.getBackground()!=null||icon.getElevation()!=0)throw new Exception("Settings icon still has a gray tile/shadow");Bitmap rendered=Bitmap.createBitmap(icon.getWidth(),icon.getHeight(),Bitmap.Config.ARGB_8888);icon.draw(new Canvas(rendered));int alpha=Color.alpha(rendered.getPixel(icon.getWidth()/2,Math.min(icon.getHeight()-1,PaperComponents.dp(c,2))));rendered.recycle();if(alpha>10)throw new Exception("Icon renderer still paints a square tile");}assertRenderPure(screen,prefs,before);});
        calendarPolishChecks(screen,c,name,before,prefs);
        galleryPolishChecks(screen,c,date,name,food,prefs);
        Bundle result=new Bundle();result.putString("stream","PASS: fixed four-corner paper at top/middle/bottom, safe FAB spacing, compact Canvas dots/triangle, equal detail padding and matched active-theme buttons, image opens original without diary writes, clean My icons, calendar geometry and unified food gallery including saturated decode queue; fixture nutrition is not real API quality\n");finish(Activity.RESULT_OK,result);
    }
    private void calendarPolishChecks(MainActivity screen,Context c,String name,String before,android.content.SharedPreferences prefs)throws Exception {
        // Calendar check is intentionally based on actual adapter cells, not a hardcoded October height.
        int[][] months={{2021,1},{2026,9},{2026,7}};
        for(int[] month:months){
            uiAssert(()->{setMainField(screen,"viewingYear",month[0]);setMainField(screen,"viewingMonth",month[1]);setMainField(screen,"calendarCollapsed",false);invoke(screen,"showTab",new Class[]{int.class,boolean.class},2,false);});
            waitForIdleSync();Thread.sleep(100);
            uiAssert(()->{
                android.widget.GridView grid=(android.widget.GridView)screen.getWindow().getDecorView().findViewWithTag("calendar_grid");
                if(grid==null||grid.getChildCount()!=grid.getAdapter().getCount())throw new Exception("Calendar last row is omitted: "+month[0]+"-"+(month[1]+1));
                View last=grid.getChildAt(grid.getChildCount()-1);if(last.getBottom()>grid.getHeight()-grid.getPaddingBottom())throw new Exception("Calendar last row is clipped");
                for(int i=0;i<grid.getChildCount();i++){View cell=grid.getChildAt(i);if(cell.getHeight()==0||cell.getTop()<0)throw new Exception("Calendar cell did not fit");}
                if(!before.equals(prefs.getString(NutritionStore.DAYS,"{}")))throw new Exception("Calendar navigation wrote diary storage");
            });screenshot(name+"-calendar-"+month[0]+"-"+(month[1]+1));
        }
        uiAssert(()->{setMainField(screen,"calendarCollapsed",true);invoke(screen,"refreshCalendarOnly",new Class[]{boolean.class},false);});
        waitForIdleSync();Thread.sleep(100);
        uiAssert(()->{android.widget.GridView grid=(android.widget.GridView)screen.getWindow().getDecorView().findViewWithTag("calendar_grid");if(grid.getAdapter().getCount()!=14||grid.getChildCount()!=14||grid.getChildAt(13).getBottom()>grid.getHeight())throw new Exception("Collapsed week clipped or lost days");});
    }
    private void galleryPolishChecks(MainActivity screen,Context c,String date,String name,JSONObject template,android.content.SharedPreferences prefs)throws Exception {
        JSONObject bulk=new JSONObject();Calendar day=Calendar.getInstance();SimpleDateFormat format=new SimpleDateFormat("yyyy-MM-dd",Locale.CHINA);
        for(int i=0;i<90;i++){JSONObject item=new JSONObject(template.toString());item.put("id","qa-gallery-"+i).put("dishName","美食栏测试 "+i);String key=format.format(day.getTime());bulk.put(key,new JSONObject().put("lunch",new JSONObject().put("foodItems",new JSONArray().put(item))));day.add(Calendar.DATE,-1);}
        // foodItems-only records intentionally omit legacy arrays; original viewing must still work.
        String before=bulk.toString();
        uiAssert(()->{prefs.edit().putString(NutritionStore.DAYS,before).commit();setMainField(screen,"days",new JSONObject(before));invoke(screen,"showTab",new Class[]{int.class,boolean.class},0,false);});
        Class<?> imageClass=Class.forName("com.eatrecord.app.TodayPaperView$FoodImage");java.lang.reflect.Field workers=imageClass.getDeclaredField("LOADERS");workers.setAccessible(true);
        java.util.concurrent.ThreadPoolExecutor pool=(java.util.concurrent.ThreadPoolExecutor)workers.get(null);int core=pool.getCorePoolSize();
        java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(2),release=new java.util.concurrent.CountDownLatch(1);
        pool.setCorePoolSize(2);
        for(int i=0;i<2;i++)pool.submit(()->{entered.countDown();try{release.await();}catch(InterruptedException ignored){}});
        if(!entered.await(5,java.util.concurrent.TimeUnit.SECONDS))throw new Exception("Could not stage thumbnail queue saturation");
        for(int i=0;i<64;i++)pool.submit(()->{});
        try{
            uiAssert(()->{View brand=screen.getWindow().getDecorView().findViewWithTag("food_gallery_entry");if(brand==null||!brand.performClick())throw new Exception("Brand does not open food gallery");});
            long deadline=android.os.SystemClock.elapsedRealtime()+12000;int[] count={0};
            do{uiAssert(()->count[0]=tagged(screen.getWindow().getDecorView(),"food_gallery_image").size());if(count[0]>=90)break;Thread.sleep(100);}while(android.os.SystemClock.elapsedRealtime()<deadline);
            if(count[0]!=90)throw new Exception("Unified gallery lost foodItems-only records or duplicated originals: "+count[0]);
            uiAssert(()->{if(screen.getWindow().getDecorView().findViewWithTag("food_gallery_title")==null)throw new Exception("Gallery title not renamed");for(View image:tagged(screen.getWindow().getDecorView(),"food_gallery_image"))if(Boolean.TRUE.equals(ownField(image,"missing")))throw new Exception("Busy decode queue falsely marked an image missing");});
        }finally{release.countDown();pool.setCorePoolSize(core);}
        long deadline=android.os.SystemClock.elapsedRealtime()+45000;int[] loaded={0};
        do{uiAssert(()->{loaded[0]=0;for(View image:tagged(screen.getWindow().getDecorView(),"food_gallery_image"))if(ownField(image,"bitmap")!=null)loaded[0]++;});if(loaded[0]>=90)break;Thread.sleep(150);}while(android.os.SystemClock.elapsedRealtime()<deadline);
        if(loaded[0]!=90)throw new Exception("Thumbnail queue did not recover: "+loaded[0]+"/90");
        screenshot(name+"-food-gallery");
        uiAssert(()->{assertRenderPure(screen,prefs,before);View image=tagged(screen.getWindow().getDecorView(),"food_gallery_image").get(0);((View)image.getParent()).performClick();if(!Boolean.TRUE.equals(field(screen,"inPhotoViewer")))throw new Exception("Food-only gallery item cannot open original");assertRenderPure(screen,prefs,before);invoke(screen,"closePhotoViewer",new Class[]{});});
    }
    private void revisionSettingsRoot(MainActivity screen,boolean modern)throws Exception {
        View decor=screen.getWindow().getDecorView();
        for(String removed:new String[]{"启动页面","隐藏设置","隐藏功能","隐藏日历","隐藏美食栏","隐藏贴纸栏","合并足迹与日历","足迹与日历","标签标记","其他功能","实验性功能"})assertSettingPresence(decor,removed,false);
        assertSettingPresence(decor,"智能助手",true);assertUnderSection(decor,"智能助手","其他");
        assertSettingPresence(decor,modern?"旧版入口":"新版入口",true);
        assertSettingPresence(decor,modern?"新版入口":"旧版入口",false);
        assertSettingPresence(decor,"餐次管理",!modern);assertUnderSection(decor,"三餐提醒","全局");
        for(String ai:new String[]{"调用 API","AI分析","自动分析","食物识别","AI读取记录","开启记忆","自动识别热量","餐图识别与隐私"})assertSettingPresence(decor,ai,false);
        if(!Boolean.TRUE.equals(field(screen,"inPreferencesPage"))||Boolean.TRUE.equals(field(screen,"inSmartAssistantPage")))throw new Exception("Other settings root has assistant-page flags");
    }
    private void revisionCalendar(MainActivity screen,Context c,String before,android.content.SharedPreferences prefs,String name)throws Exception {
        final HomeCalendarView[] calendar={null};
        uiAssert(()->{calendar[0]=(HomeCalendarView)screen.getWindow().getDecorView().findViewWithTag("home_calendar");if(calendar[0]==null||calendar[0].isExpanded())throw new Exception("Home must initially show a collapsed shared calendar");
            View grid=calendar[0].findViewWithTag("home_calendar_grid");if(grid==null||grid.isShown())throw new Exception("Collapsed calendar still shows holidays/month cells");
            View toggle=calendar[0].findViewWithTag("home_calendar_toggle");if(toggle==null||toggle.getHeight()<PaperComponents.dp(c,48)||!toggle.isClickable())throw new Exception("Calendar lacks an accessible 48dp expansion control");
        });
        Calendar weekBefore=calendar[0].weekStart();swipeWeek(calendar[0].weekStrip(),true);
        Calendar expected=(Calendar)weekBefore.clone();expected.add(Calendar.DATE,7);
        if(!HomeCalendarDates.key(expected).equals(HomeCalendarDates.key(calendar[0].weekStart())))throw new Exception("Shared home week no longer slides to the next week");
        swipeWeek(calendar[0].weekStrip(),false);
        // A real screen-space vertical gesture starts in the week, not a direct toggle invocation.
        revisionCalendarSwipe(calendar[0],true);
        uiAssert(()->{if(!calendar[0].isExpanded()||!Boolean.TRUE.equals(field(screen,"homeCalendarExpanded")))throw new Exception("Downward date gesture did not expand the actual home calendar");assertRenderPure(screen,prefs,before);});
        screenshot(name+"-calendar-expanded");
        // Inspect real four/five/six-row views and paper geometry, including night/large-font runs.
        int[][] months={{2021,2,4},{2026,10,5},{2026,8,6}};
        for(int[] entry:months){
            Calendar anchor=Calendar.getInstance();anchor.set(entry[0],entry[1]-1,1,12,0,0);
            uiAssert(()->{setMainField(screen,"todayWeekAnchor",anchor);setMainField(screen,"todayWeekSelection",invoke(screen,"homeDate",new Class[]{}));setMainField(screen,"homeCalendarExpanded",true);invoke(screen,"renderCurrent",new Class[]{});});
            waitForIdleSync();Thread.sleep(250);
            uiAssert(()->{
                HomeCalendarView actual=(HomeCalendarView)screen.getWindow().getDecorView().findViewWithTag("home_calendar");calendar[0]=actual;
                ViewGroup grid=(ViewGroup)actual.findViewWithTag("home_calendar_grid");
                if(grid.getChildCount()!=entry[2]||tagged(grid,"home_calendar_day_").size()!=entry[2]*7)throw new Exception("Month grid row/date count mismatch for "+entry[0]+"-"+entry[1]);
                if(!visibleText(actual.findViewWithTag("home_calendar_month")).contains(entry[1]+"月"))throw new Exception("Calendar browse month header did not update");
                Rect last=screenRect(grid.getChildAt(grid.getChildCount()-1)),bounds=screenRect(actual);
                if(last.bottom>bounds.bottom+1||last.height()<PaperComponents.dp(c,40))throw new Exception("Final calendar week is clipped or too small");
                View paper=screen.getWindow().getDecorView().findViewWithTag("today_paper_viewport");
                if(paper==null||paper.getHeight()<PaperComponents.dp(c,100)||!paper.getClipToOutline())throw new Exception("Expanded calendar consumes or unrounds the daily paper");
                if(screenRect(paper).top<bounds.bottom||screenRect(paper).bottom>screenRect((View)field(screen,"fab")).top-PaperComponents.dp(c,10))throw new Exception("Calendar/paper/FAB layout overlaps");
                if(entry[1]==10){
                    if(!visibleText(grid).contains("国庆节"))throw new Exception("Expanded month lost the existing holiday mapping");
                    View adjacent=grid.findViewWithTag("home_calendar_day_2026-09-28");if(adjacent==null||adjacent.getAlpha()>=.8f)throw new Exception("Adjacent-month dates are not visually muted");
                    Calendar future=Calendar.getInstance();future.add(Calendar.MONTH,1);future.set(Calendar.DAY_OF_MONTH,1);
                    // The fixed October fixture has Nov 1 in its final week on the current test date.
                    View nextMonth=grid.findViewWithTag("home_calendar_day_2026-11-01");
                    if("2026-10".equals(HomeCalendarDates.key(Calendar.getInstance()).substring(0,7))&&nextMonth!=null&&nextMonth.isEnabled())throw new Exception("Future adjacent-month day is selectable");
                }
                assertRenderPure(screen,prefs,before);
            });
            screenshot(name+"-month-"+entry[2]+"-rows");
        }
        revisionCalendarSwipe(calendar[0],false);
        uiAssert(()->{if(calendar[0].isExpanded()||Boolean.TRUE.equals(field(screen,"homeCalendarExpanded")))throw new Exception("Upward date gesture did not collapse the home calendar");if(calendar[0].findViewWithTag("home_calendar_grid").isShown())throw new Exception("Collapsed calendar exposes month holidays");});
        // Restore current month, then select a real past date without materializing an empty diary.
        uiAssert(()->{String today=new SimpleDateFormat("yyyy-MM-dd",Locale.CHINA).format(new Date());setMainField(screen,"homeDateKey",today);setMainField(screen,"todayWeekAnchor",Calendar.getInstance());setMainField(screen,"todayWeekSelection",today);invoke(screen,"renderCurrent",new Class[]{});calendar[0]=(HomeCalendarView)screen.getWindow().getDecorView().findViewWithTag("home_calendar");calendar[0].setExpanded(true,false);});
        Calendar yesterday=Calendar.getInstance();yesterday.add(Calendar.DATE,-1);String selected=HomeCalendarDates.key(yesterday);
        uiAssert(()->{View day=calendar[0].findViewWithTag("home_calendar_day_"+selected);if(day==null||!day.isEnabled()||!day.performClick())throw new Exception("Past calendar date cannot be selected");});waitForIdleSync();
        uiAssert(()->{if(!selected.equals(invoke(screen,"homeDate",new Class[]{}))||!Boolean.TRUE.equals(field(screen,"homeCalendarExpanded")))throw new Exception("Date selection did not retain expanded state");assertRenderPure(screen,prefs,before);});
        uiAssert(()->{setMainField(screen,"homeDateKey",new SimpleDateFormat("yyyy-MM-dd",Locale.CHINA).format(new Date()));setMainField(screen,"homeCalendarExpanded",false);invoke(screen,"renderCurrent",new Class[]{});});
    }
    private void revisionCalendarSwipe(HomeCalendarView calendar,boolean expand)throws Exception {
        waitForIdleSync();Thread.sleep(80);Rect bounds=screenRect(calendar),week=screenRect(calendar.weekStrip());
        float x=bounds.exactCenterX(),start=expand?week.exactCenterY():bounds.top+PaperComponents.dp(calendar.getContext(),110);
        float finish=start+(expand?1:-1)*PaperComponents.dp(calendar.getContext(),100);long down=android.os.SystemClock.uptimeMillis();
        for(int i=0;i<=10;i++){
            MotionEvent event=MotionEvent.obtain(down,android.os.SystemClock.uptimeMillis(),i==0?MotionEvent.ACTION_DOWN:i==10?MotionEvent.ACTION_UP:MotionEvent.ACTION_MOVE,x,start+(finish-start)*i/10,0);
            event.setSource(InputDevice.SOURCE_TOUCHSCREEN);getUiAutomation().injectInputEvent(event,true);event.recycle();Thread.sleep(16);
        }
        waitForIdleSync();Thread.sleep(280);waitForIdleSync();
    }
    private void revisionRecognitionGlyph(MainActivity screen,Context c)throws Exception {
        View fab=(View)field(screen,"fab");
        if(!(fab.getBackground() instanceof RecognitionButtonDrawable))throw new Exception("Home add action does not use its font-independent recognition drawable");
        int width=PaperComponents.dp(c,104),height=PaperComponents.dp(c,56);
        Bitmap icon=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);android.graphics.drawable.Drawable drawable=fab.getBackground();Rect old=new Rect(drawable.getBounds());
        try{
            drawable.setBounds(0,0,width,height);drawable.draw(new Canvas(icon));
            int center=icon.getPixel(width/2,height/2),background=icon.getPixel(width/4,height/2);
            if(Color.alpha(center)<250||Math.abs(Color.red(center)-Color.red(background))+Math.abs(Color.green(center)-Color.green(background))+Math.abs(Color.blue(center)-Color.blue(background))<240)throw new Exception("Recognition star is missing/black-on-black");
            int painted=0;for(int y=height/4;y<height*3/4;y++)for(int x=width/3;x<width*2/3;x++){int value=icon.getPixel(x,y);if(Math.abs(Color.red(value)-Color.red(background))+Math.abs(Color.green(value)-Color.green(background))+Math.abs(Color.blue(value)-Color.blue(background))>240)painted++;}
            if(painted<PaperComponents.dp(c,6)*PaperComponents.dp(c,6))throw new Exception("Recognition action contains only a tiny dot");
        }finally{drawable.setBounds(old);icon.recycle();}
    }
    private void revisionV13(Context c,android.content.SharedPreferences prefs)throws Exception {
        FoodAnalysisJobService.cancelAll(c);FoodDraftStore.discard(c,FoodDraftStore.pendingSources(c));installTransport();
        String name=args.getString("name","beta13"),date=new SimpleDateFormat("yyyy-MM-dd",Locale.CHINA).format(new Date());
        JSONObject fixture=unifiedDays(c,date),day=fixture.getJSONObject(date);JSONArray three=new JSONArray(),photos=new JSONArray();
        for(String meal:new String[]{"breakfast","lunch","dinner"}){JSONObject food=new JSONObject(day.getJSONObject(meal).getJSONArray("foodItems").getJSONObject(0).toString());three.put(food);photos.put(food.getString("sourcePhoto"));}
        for(String meal:NutritionStore.MEALS)day.put(meal,new JSONObject());
        day.put("breakfast",new JSONObject().put("foodItems",three).put("photos",photos).put("text","保留旧备注，不在小图菜单重复展示"));
        day.put("manualCaloriesOverride",true).put("manualCaloriesTotal",1990);
        String before=fixture.toString();
        JSONObject profile=new JSONObject().put("new_ui_v2",true).put("startTab",3).put("hideSettings",true).put("hideStickerGallery",true).put("hideCalendar",true).put("mergeFootprintCalendar",true)
            .put("decoration","true".equals(args.getString("night"))?"night":"blue").put("followSystemNight",false).put("font",100).put("footprintLayout","expanded")
            .put("mealNotificationsEnabled",false).put("foodAutoAnalyze",false).put("foodPhotoConsent",false).put("autoCloudSync",false)
            .put("breakfastNotificationTime","07:15").put("lunchNotificationTime","12:20").put("dinnerNotificationTime","18:45")
            .put("seenAnnouncementVersion",c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName);
        prefs.edit().remove("food_processing_preview").putString(NutritionStore.PROFILE,profile.toString()).putString(NutritionStore.DAYS,before).commit();
        MainActivity screen=(MainActivity)startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();Thread.sleep(350);
        uiAssert(()->{if(((Integer)field(screen,"currentTab"))!=0)throw new Exception("Saved startTab/hideSettings overrode default Today startup");ViewGroup nav=(ViewGroup)field(screen,"navBar");if(nav.getChildCount()!=3)throw new Exception("Navigation retains calendar/hidden tab");for(int i=0;i<3;i++)if(!Integer.valueOf(new int[]{0,1,3}[i]).equals(nav.getChildAt(i).getTag()))throw new Exception("Wrong navigation tab identity");revisionRecognitionGlyph(screen,c);assertRenderPure(screen,prefs,before);});
        screenshot(name+"-home-recognition");revisionCalendar(screen,c,before,prefs,name);
        // More is now exclusively meal management, not duplicate nutrition/book links.
        uiAssert(()->screen.getWindow().getDecorView().findViewWithTag("today_more").performClick());
        waitForIdleSync();Thread.sleep(100);
        if(node("当天营养与热量目标")!=null||node("查看这一天的本子")!=null||node("编辑早餐记录")==null||node("编辑午餐记录")==null||node("编辑晚餐记录")==null)throw new Exception("Day menu has duplicate shortcuts or lost meal editing");
        screenshot(name+"-meal-only-menu");sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);waitForIdleSync();
        final AlertDialog[] mealDialog={null};uiAssert(()->mealDialog[0]=(AlertDialog)invoke(screen,"showMealRecordEditor",new Class[]{String.class,String.class},date,"breakfast"));waitForIdleSync();
        uiAssert(()->{
            View decor=mealDialog[0].getWindow().getDecorView();ViewGroup grid=(ViewGroup)decor.findViewWithTag("meal_record_grid");List<View> cells=tagged(grid,"meal_record_food");
            if(grid.getBackground()!=null||cells.size()!=3||tagged(grid,"meal_record_name").size()!=3)throw new Exception("Meal editor is not the unframed name/image grid");
            if(!(grid.getChildAt(0) instanceof ViewGroup)||!(grid.getChildAt(1) instanceof ViewGroup)||((ViewGroup)grid.getChildAt(0)).getChildCount()!=2||((ViewGroup)grid.getChildAt(1)).getChildCount()!=2)throw new Exception("Three meal images do not wrap as two plus one");
            for(View cell:cells)if(cell.getBackground()!=null)throw new Exception("Meal image cell retains an extra card border");
            String visible=visibleText(grid);for(String forbidden:new String[]{"kcal","蛋白质","查看餐食详情","已记录","添加文字备注"})if(visible.contains(forbidden))throw new Exception("Meal grid leaks old nutrition/editor text: "+forbidden);
            assertRenderPure(screen,prefs,before);
        });screenshot(name+"-meal-two-columns");
        uiAssert(()->{ViewGroup grid=(ViewGroup)mealDialog[0].getWindow().getDecorView().findViewWithTag("meal_record_grid");ViewGroup cell=(ViewGroup)tagged(grid,"meal_record_food").get(0);cell.getChildAt(0).performClick();});waitForIdleSync();
        clickDescription(three.getJSONObject(0).getString("dishName")+"，点击查看原图");
        uiAssert(()->{if(mealDialog[0].isShowing()||!Boolean.TRUE.equals(field(screen,"inPhotoViewer")))throw new Exception("Meal grid overlays the original-photo viewer");invoke(screen,"closePhotoViewer",new Class[]{});assertRenderPure(screen,prefs,before);});waitForIdleSync();
        // Footprints remains a notebook shelf even after repeated bottom-tab or title taps.
        uiAssert(()->invoke(screen,"showTab",new Class[]{int.class,boolean.class},1,false));waitForIdleSync();Thread.sleep(200);
        uiAssert(()->{
            View decor=screen.getWindow().getDecorView(),title=decor.findViewWithTag("footprints_title");
            if(title==null||title.isClickable()||title.isLongClickable())throw new Exception("Footprints title still switches into calendar");
            for(View cover:tagged(decor,"footprint_plain_cover"))if(!(cover instanceof ViewGroup)||((ViewGroup)cover).getChildCount()!=0)throw new Exception("Footprint cover still contains label badges");
            ViewGroup nav=(ViewGroup)field(screen,"navBar");nav.getChildAt(1).performClick();
            if(((Integer)field(screen,"currentTab"))!=1||screen.getWindow().getDecorView().findViewWithTag("calendar_card")!=null)throw new Exception("Repeated Footprints tab switches calendar");
            assertRenderPure(screen,prefs,before);
        });screenshot(name+"-plain-footprints");
        for(boolean modern:new boolean[]{true,false,true}){
            if(NutritionStore.read(prefs,NutritionStore.PROFILE).optBoolean("new_ui_v2")!=modern){
                settingsRoot(screen);String entry=modern?"新版入口":"旧版入口";clickSetting(entry);
                if(node(modern?"切换到新版？":"切换到旧版？")==null)throw new Exception("Missing beta mode confirmation");click("取消");
                if(NutritionStore.read(prefs,NutritionStore.PROFILE).optBoolean("new_ui_v2")==modern)throw new Exception("Cancelling beta mode switch changed mode");
                clickSetting(entry);click("切换");waitForIdleSync();Thread.sleep(250);
            }else settingsRoot(screen);
            uiAssert(()->revisionSettingsRoot(screen,modern));screenshot(name+(modern?"-new-settings":"-legacy-settings"));
            clickSetting("智能助手");uiAssert(()->{
                View decor=screen.getWindow().getDecorView();if(decor.findViewWithTag("smart_assistant_page")==null||!Boolean.TRUE.equals(field(screen,"inSmartAssistantPage")))throw new Exception("Intelligent Assistant is not its own page");
                for(String ai:new String[]{"调用 API","AI分析","自动分析","食物识别","AI读取记录","开启记忆","自动识别热量"})assertSettingPresence(decor,ai,true);
                assertSettingPresence(decor,"餐图识别与隐私",modern);assertSettingPresence(decor,"三餐提醒",false);
            });screenshot(name+(modern?"-new-assistant":"-legacy-assistant"));sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);waitForIdleSync();Thread.sleep(350);uiAssert(()->revisionSettingsRoot(screen,modern));
            uiAssert(()->{invoke(screen,"renderFootprintSettingsPage",new Class[]{});View decor=screen.getWindow().getDecorView();for(String removed:new String[]{"标签标记","显示热量","显示消费","显示心情","日历","足迹日历","日历折叠","日历布局"})assertSettingPresence(decor,removed,false);invoke(screen,"renderHiddenSettingsPage",new Class[]{});revisionSettingsRoot(screen,modern);});
            uiAssert(()->invoke(screen,"showTab",new Class[]{int.class,boolean.class},0,false));waitForIdleSync();Thread.sleep(250);
            uiAssert(()->{if(screen.getWindow().getDecorView().findViewWithTag("home_calendar")==null)throw new Exception("Legacy/new home calendar not shared");assertRenderPure(screen,prefs,before);});
        }
        settingsRoot(screen);clickSetting("三餐提醒");
        clickSetting("早餐提醒");if(descriptionNode("早餐提醒时间，24 小时制")==null)throw new Exception("Reminder row did not open a manual input");click("取消");
        final AlertDialog[] time={null};
        uiAssert(()->{time[0]=(AlertDialog)invoke(screen,"showNotificationTimePicker",new Class[]{String.class,String.class,String.class},"早餐提醒","breakfastNotificationTime","08:00");View decor=time[0].getWindow().getDecorView();List<View> all=new ArrayList<>();collectViews(decor,all);for(View view:all)if(view instanceof android.widget.TimePicker)throw new Exception("Reminder still embeds a clock/time picker");android.widget.EditText input=(android.widget.EditText)decor.findViewWithTag("reminder_time_input");if(input==null||input.getMinimumHeight()<PaperComponents.dp(c,48))throw new Exception("Reminder input missing or inaccessible");input.setText("24:99");});
        waitForIdleSync();click("保存");
        uiAssert(()->{android.widget.EditText input=(android.widget.EditText)time[0].getWindow().getDecorView().findViewWithTag("reminder_time_input");if(!time[0].isShowing()||input.getError()==null)throw new Exception("Invalid reminder time was accepted");});
        if(!"07:15".equals(NutritionStore.read(prefs,NutritionStore.PROFILE).optString("breakfastNotificationTime")))throw new Exception("Invalid reminder changed stored time");screenshot(name+"-manual-time-invalid");
        uiAssert(()->{((android.widget.EditText)time[0].getWindow().getDecorView().findViewWithTag("reminder_time_input")).setText("8:05");time[0].getButton(AlertDialog.BUTTON_POSITIVE).performClick();});waitForIdleSync();Thread.sleep(250);
        if(!"08:05".equals(NutritionStore.read(prefs,NutritionStore.PROFILE).optString("breakfastNotificationTime")))throw new Exception("Manual reminder time not normalized");
        uiAssert(()->{time[0]=(AlertDialog)invoke(screen,"showNotificationTimePicker",new Class[]{String.class,String.class,String.class},"早餐提醒","breakfastNotificationTime","08:00");((android.widget.EditText)time[0].getWindow().getDecorView().findViewWithTag("reminder_time_input")).setText("11:22");});waitForIdleSync();click("取消");
        if(!"08:05".equals(NutritionStore.read(prefs,NutritionStore.PROFILE).optString("breakfastNotificationTime")))throw new Exception("Cancelling manual reminder persisted the draft time");
        // An empty new home has one gentle guide and the same real recognition button.
        uiAssert(()->{setMainField(screen,"days",new JSONObject());prefs.edit().putString(NutritionStore.DAYS,"{}").commit();setMainField(screen,"homeDateKey",date);setMainField(screen,"homeCalendarExpanded",false);invoke(screen,"showTab",new Class[]{int.class,boolean.class},0,false);});waitForIdleSync();Thread.sleep(250);
        uiAssert(()->{View decor=screen.getWindow().getDecorView(),guide=decor.findViewWithTag("day_empty_guide");if(guide==null||guide.isClickable()||!visibleText(guide).contains("点击下方星标可以添加美食喔")||guide.performClick())throw new Exception("Empty paper hint still acts as an accidental add target");revisionRecognitionGlyph(screen,c);if(visibleText(decor).contains("添加第一张餐图")||visibleText(guide).contains("或轻点下方的识别星标"))throw new Exception("Duplicate old empty-page add component remains");assertRenderPure(screen,prefs,"{}");((View)field(screen,"fab")).performClick();});
        waitForIdleSync();Thread.sleep(100);
        if(node("早餐")==null||node("午餐")==null||node("晚餐")==null)throw new Exception("Bottom star no longer opens the meal selection");screenshot(name+"-empty-guide-meal-choice");sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);waitForIdleSync();screenshot(name+"-empty-guide");
        uiAssert(()->{setMainField(screen,"days",fixture);prefs.edit().putString(NutritionStore.DAYS,before).commit();invoke(screen,"renderCurrent",new Class[]{});assertRenderPure(screen,prefs,before);});
        if(apiCalls.get()!=0)throw new Exception("Layout/settings revision made a food API request");MealReminderReceiver.cancelAll(c);
        Bundle result=new Bundle();result.putString("stream","PASS: Today startup despite legacy hidden/start preferences; three-tab navigation, native recognition star contrast, real week/vertical month gestures, 4/5/6 complete calendar rows/holiday/adjacent-day styles, preserved expanded selection and paper corners; meal-only menu and unframed two-column wrapping grid; plain Footprints/no calendar switching; grouped assistant and new/legacy confirmation/isolation; manual time invalid/save/normalize/cancel; functional empty guide and unchanged diary; no external API calls. Geometry/food values are labelled QA fixtures, not nutrition accuracy.\n");finish(Activity.RESULT_OK,result);
    }
    private HomeCalendarView calendarV15(MainActivity screen)throws Exception {
        View calendar=screen.getWindow().getDecorView().findViewWithTag("home_calendar");
        if(!(calendar instanceof HomeCalendarView))throw new Exception("Missing shared home calendar");return (HomeCalendarView)calendar;
    }
    private TodayDayPager pagerV15(MainActivity screen)throws Exception {
        View pager=screen.getWindow().getDecorView().findViewWithTag("today_day_pager");
        if(!(pager instanceof TodayDayPager))throw new Exception("Missing real three-page diary pager");return (TodayDayPager)pager;
    }
    private View paperV15(MainActivity screen)throws Exception {
        View[] pages=(View[])ownField(pagerV15(screen),"pages");
        if(pages.length!=3||pages[1]==null)throw new Exception("Diary pager does not own three actual pages");return pages[1];
    }
    private void pointerV15(long down,int action,float x,float y)throws Exception {
        MotionEvent event=MotionEvent.obtain(down,android.os.SystemClock.uptimeMillis(),action,x,y,0);event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        try{if(gestureScreenV20!=null){
            uiAssert(()->{View target=null;for(View view:android.view.inspector.WindowInspector.getGlobalWindowViews())if(view.isShown()&&view.hasWindowFocus())target=view;
                if(target==null)throw new Exception("No focused application window for QA pointer fallback");
                int[] origin=new int[2];target.getLocationOnScreen(origin);MotionEvent local=MotionEvent.obtain(event);local.offsetLocation(-origin[0],-origin[1]);
                try{if(target==gestureScreenV20.getWindow().getDecorView())gestureScreenV20.dispatchTouchEvent(local);else target.dispatchTouchEvent(local);}finally{local.recycle();}});
            localPointerFallbacksV20++;
        }else if(!getUiAutomation().injectInputEvent(event,true))throw new Exception("Pointer injection failed: action="+action+", x="+x+", y="+y);
        }finally{event.recycle();}
    }
    private void movesV15(long down,float x0,float y0,float x1,float y1,int count)throws Exception {
        for(int i=1;i<=count;i++){pointerV15(down,MotionEvent.ACTION_MOVE,x0+(x1-x0)*i/count,y0+(y1-y0)*i/count);Thread.sleep(18);}
    }
    private void settleV15()throws Exception {waitForIdleSync();Thread.sleep(950);waitForIdleSync();}
    private void screenshotNowV15(String name)throws Exception {
        Bitmap image=getUiAutomation().takeScreenshot();if(image==null)throw new Exception("Intermediate screenshot unavailable");
        File dir=new File(getTargetContext().getExternalFilesDir(null),"qa");dir.mkdirs();
        try(OutputStream out=new FileOutputStream(new File(dir,name+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}finally{image.recycle();}
    }
    private float offsetV15(Object pager)throws Exception {return ((Number)ownField(pager,"offset")).floatValue();}
    private void slidesV15(Object pager,int sign)throws Exception {
        View[] pages=(View[])ownField(pager,"pages");float width=((View)pager).getWidth(),offset=offsetV15(pager);
        if(pages.length!=3||width<=0||sign*offset<=PaperComponents.dp(((View)pager).getContext(),6)||Math.abs(offset)>=width)throw new Exception("No actual intermediate page translation");
        for(int i=0;i<3;i++)if(Math.abs(pages[i].getTranslationX()-((i-1)*width+offset))>1.1f)throw new Exception("Adjacent real pages do not follow the same displacement");
    }
    private void expandDatesV15(MainActivity screen,Context c,String name)throws Exception {
        HomeCalendarView calendar=calendarV15(screen);Rect area=screenRect(calendar.weekStrip());
        float x=area.exactCenterX(),y=area.exactCenterY(),half=y+PaperComponents.dp(c,52),end=y+PaperComponents.dp(c,125);long down=android.os.SystemClock.uptimeMillis();
        pointerV15(down,MotionEvent.ACTION_DOWN,x,y);movesV15(down,x,y,x,half,5);
        uiAssert(()->{float reveal=((Number)ownField(calendar,"reveal")).floatValue();if(android.animation.ValueAnimator.areAnimatorsEnabled()&&(reveal<=0||reveal>=1))throw new Exception("Calendar does not follow date-region downward drag");});
        screenshotNowV15(name+"-date-pull-mid");movesV15(down,x,half,x,end,5);pointerV15(down,MotionEvent.ACTION_UP,x,end);settleV15();
        uiAssert(()->{if(!calendarV15(screen).isExpanded()||!Boolean.TRUE.equals(field(screen,"homeCalendarExpanded")))throw new Exception("Date-region downward drag did not expand month");});
    }
    private void collapsePaperV15(MainActivity screen,Context c,String name)throws Exception {
        HomeCalendarView calendar=calendarV15(screen);Rect paper=screenRect(paperV15(screen));
        float x=paper.left+paper.width()*.90f,y=paper.exactCenterY(),half=y-PaperComponents.dp(c,30),end=y-PaperComponents.dp(c,110);long down=android.os.SystemClock.uptimeMillis();
        pointerV15(down,MotionEvent.ACTION_DOWN,x,y);movesV15(down,x,y,x,half,4);
        uiAssert(()->{float reveal=((Number)ownField(calendar,"reveal")).floatValue();if(android.animation.ValueAnimator.areAnimatorsEnabled()&&(reveal<=0||reveal>=1))throw new Exception("Upward gesture from middle of daily paper does not follow collapse");});
        screenshotNowV15(name+"-paper-collapse-mid");movesV15(down,x,half,x,end,6);pointerV15(down,MotionEvent.ACTION_UP,x,end);settleV15();
        uiAssert(()->{if(calendarV15(screen).isExpanded()||calendarV15(screen).canCollapse()||Boolean.TRUE.equals(field(screen,"homeCalendarExpanded")))throw new Exception("Paper upward gesture did not finish collapse");});
    }
    private void monthGesturesV15(MainActivity screen,Context c,String before,android.content.SharedPreferences prefs,String name)throws Exception {
        HomeCalendarView calendar=calendarV15(screen);Object pager=ownField(calendar,"monthPager");Rect area=screenRect((View)pager);
        Calendar initial=calendar.monthAnchor();String selected=(String)invoke(screen,"homeDate",new Class[]{});
        float x=area.left+area.width()*.75f,y=area.top+Math.min(area.height()*.42f,PaperComponents.dp(c,85)),partial=x-area.width()*.27f;long down=android.os.SystemClock.uptimeMillis();
        pointerV15(down,MotionEvent.ACTION_DOWN,x,y);movesV15(down,x,y,partial,y,7);
        uiAssert(()->{if(android.animation.ValueAnimator.areAnimatorsEnabled())slidesV15(pager,-1);if(!HomeCalendarDates.key(initial).equals(HomeCalendarDates.key(calendar.monthAnchor())))throw new Exception("Partial month drag replaced content prematurely");assertRenderPure(screen,prefs,before);});
        screenshotNowV15(name+"-month-left-mid");movesV15(down,partial,y,x+area.width()*.08f,y,7);pointerV15(down,MotionEvent.ACTION_CANCEL,x+area.width()*.08f,y);settleV15();
        uiAssert(()->{if(!HomeCalendarDates.key(initial).equals(HomeCalendarDates.key(calendar.monthAnchor()))||Math.abs(offsetV15(pager))>.5f)throw new Exception("Reversed/cancelled month drag changed browse month or left pixels");});
        down=android.os.SystemClock.uptimeMillis();pointerV15(down,MotionEvent.ACTION_DOWN,x,y);movesV15(down,x,y,x-area.width()*.55f,y,10);pointerV15(down,MotionEvent.ACTION_UP,x-area.width()*.55f,y);settleV15();
        Calendar next=(Calendar)initial.clone();next.add(Calendar.MONTH,1);next=HomeCalendarDates.month(next);final Calendar expected=next;
        uiAssert(()->{if(!HomeCalendarDates.key(expected).equals(HomeCalendarDates.key(calendar.monthAnchor())))throw new Exception("Left month gesture did not commit next month");if(!selected.equals(invoke(screen,"homeDate",new Class[]{})))throw new Exception("Browsing month changed selected diary date");
            String today=HomeCalendarDates.key(Calendar.getInstance());for(View day:tagged(calendar.findViewWithTag("home_calendar_grid"),"home_calendar_day_")){String key=day.getTag().toString().substring("home_calendar_day_".length());if(key.compareTo(today)>0&&day.isEnabled())throw new Exception("Future month day became selectable");}assertRenderPure(screen,prefs,before);
        });screenshot(name+"-month-browsed");
        if(android.animation.ValueAnimator.areAnimatorsEnabled()){
            Rect updated=screenRect((View)pager);float sx=updated.left+updated.width()*.25f,sy=updated.top+Math.min(updated.height()*.4f,PaperComponents.dp(c,75)),ex=sx+updated.width()*.38f;
            long gesture=android.os.SystemClock.uptimeMillis();pointerV15(gesture,MotionEvent.ACTION_DOWN,sx,sy);movesV15(gesture,sx,sy,ex,sy,7);pointerV15(gesture,MotionEvent.ACTION_UP,ex,sy);Thread.sleep(35);
            uiAssert(()->{slidesV15(pager,1);if(!((GestureSpring)ownField(pager,"spring")).isRunning())throw new Exception("Month release does not use interruptible spring");});
            long interrupt=android.os.SystemClock.uptimeMillis();pointerV15(interrupt,MotionEvent.ACTION_DOWN,ex,sy);
            uiAssert(()->{if(Math.abs(offsetV15(pager))<PaperComponents.dp(c,10))throw new Exception("Grabbing month spring snapped directly to zero");});
            movesV15(interrupt,ex,sy,ex-updated.width()*.3f,sy,6);pointerV15(interrupt,MotionEvent.ACTION_CANCEL,ex-updated.width()*.3f,sy);settleV15();
            uiAssert(()->{if(!HomeCalendarDates.key(expected).equals(HomeCalendarDates.key(calendar.monthAnchor()))||Math.abs(offsetV15(pager))>.5f)throw new Exception("Interrupted month spring committed stale target");});
        }
    }
    private void dayGestureV15(MainActivity screen,boolean forward,String name)throws Exception {
        TodayDayPager pager=pagerV15(screen);Calendar initial=pager.currentDate(),expected=(Calendar)initial.clone();expected.add(Calendar.DATE,forward?1:-1);
        Rect area=screenRect(pager);float x=area.left+area.width()*(forward?.76f:.24f),y=area.top+Math.min(area.height()*.4f,PaperComponents.dp(screen,105)),end=x+(forward?-1:1)*area.width()*.52f;
        long down=android.os.SystemClock.uptimeMillis();pointerV15(down,MotionEvent.ACTION_DOWN,x,y);movesV15(down,x,y,end,y,10);
        uiAssert(()->{if(android.animation.ValueAnimator.areAnimatorsEnabled()){
                slidesV15(pager,forward?-1:1);
                if(!HomeCalendarDates.key(expected).equals(invoke(screen,"homeDate",new Class[]{}))||!HomeCalendarDates.key(expected).equals(HomeCalendarDates.key((Calendar)ownField(calendarV15(screen),"selected"))))throw new Exception("Visible food page and calendar marker lag behind the finger");
            }if(!HomeCalendarDates.key(initial).equals(HomeCalendarDates.key(pager.currentDate())))throw new Exception("Diary date changed before page release");});
        screenshotNowV15(name+"-day-"+(forward?"left":"right")+"-mid");pointerV15(down,MotionEvent.ACTION_UP,end,y);settleV15();
        uiAssert(()->{if(!HomeCalendarDates.key(expected).equals(HomeCalendarDates.key(pagerV15(screen).currentDate()))||!HomeCalendarDates.key(expected).equals(invoke(screen,"homeDate",new Class[]{})))throw new Exception("Diary gesture/calendar selected date diverged");if(Math.abs(offsetV15(pagerV15(screen)))>.5f)throw new Exception("Diary settle left translated pixels");});
    }
    private int iconInkV15(View icon,Context c)throws Exception {
        cleanGlyph(icon,c);int width=icon.getWidth(),height=icon.getHeight(),best=0;Bitmap image=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);icon.draw(new Canvas(image));
        try{for(int y=0;y<height;y++)for(int x=0;x<width;x++){int value=image.getPixel(x,y);if(Color.alpha(value)>Color.alpha(best))best=value;}}finally{image.recycle();}return best;
    }
    private void dialogWidthV15(Context c,View decor)throws Exception {
        int expected=PaperComponents.dialogWidth(c);if(Math.abs(decor.getWidth()-expected)>2)throw new Exception("Dialog outer width is not shared screen-48dp/max560dp policy: "+decor.getWidth()+" vs "+expected);
    }
    private View dialogRootV15(String tag)throws Exception {
        for(View root:android.view.inspector.WindowInspector.getGlobalWindowViews())if(root.findViewWithTag(tag)!=null)return root;
        throw new Exception("Missing active dialog tag "+tag);
    }
    private void privacyRowV15(android.widget.ListView list,boolean last,Context c,boolean night)throws Exception {
        int index=last?list.getChildCount()-1:0;View row=index<0?null:list.getChildAt(index);List<View> all=new ArrayList<>();if(row!=null)collectViews(row,all);android.widget.TextView text=null;
        for(View view:all)if(view instanceof android.widget.TextView){text=(android.widget.TextView)view;break;}
        if(text==null||text.getCurrentTextColor()!=UiTokens.ink(night)||!AppTypography.typeface(c,false).equals(text.getTypeface()))throw new Exception("Privacy first/last row lost selected font or theme color");
        // API34+ SP scaling is nonlinear; multiplying scaledDensity misreports large fonts.
        float expected=android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,AppTypography.sp(c,17),c.getResources().getDisplayMetrics());if(Math.abs(text.getTextSize()-expected)>1)throw new Exception("Privacy menu font size does not follow app/system text scale: "+text.getTextSize()+" vs "+expected);
        if(last&&list.getLastVisiblePosition()!=list.getCount()-1||!last&&list.getFirstVisiblePosition()!=0)throw new Exception("Privacy edge row was not actually rendered");
    }
    private void settingsDialogsV15(MainActivity screen,Context c,android.content.SharedPreferences prefs,String before,String name)throws Exception {
        settingsRoot(screen);uiAssert(()->{
            View decor=screen.getWindow().getDecorView();ViewGroup reference=(ViewGroup)setting(decor,"震动");int tint=iconInkV15(reference.getChildAt(0),c);
            assertSettingPresence(decor,"旧版入口",false);assertSettingPresence(decor,"新版入口",false);
            for(String title:new String[]{"三餐提醒","智能助手","恢复默认设置"}){
                ViewGroup row=(ViewGroup)setting(decor,title);View icon=row.getChildAt(0);if(!(icon instanceof ProfileSettingIcon))throw new Exception("Setting lost outline glyph: "+title);
                int actual=iconInkV15(icon,c);for(int channel=0;channel<4;channel++)if(Math.abs((actual>>>(channel*8)&255)-(tint>>>(channel*8)&255))>2)throw new Exception("Setting glyph uses different tint/alpha from ordinary setting icon: "+title+" actual="+Integer.toHexString(actual)+" reference="+Integer.toHexString(tint));
            }assertRenderPure(screen,prefs,before);
        });screenshot(name+"-matched-settings-icons");
        clickSetting("智能助手");clickSetting("餐图识别");waitForIdleSync();Thread.sleep(150);
        uiAssert(()->{View decor=screen.getWindow().getDecorView();if(decor.findViewWithTag("food_recognition_settings_page")==null)throw new Exception("Photo settings not a dedicated page");assertSettingPresence(decor,"每日热量目标",false);assertRenderPure(screen,prefs,before);});screenshot(name+"-recognition-settings-theme");sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);waitForIdleSync();
        settingsRoot(screen);clickSetting("三餐提醒");final AlertDialog[] time={null};final android.widget.EditText[] input={null};
        uiAssert(()->{time[0]=(AlertDialog)invoke(screen,"showNotificationTimePicker",new Class[]{String.class,String.class,String.class},"早餐提醒","breakfastNotificationTime","08:00");input[0]=(android.widget.EditText)time[0].getWindow().getDecorView().findViewWithTag("reminder_time_input");if(input[0]==null||input[0].getMinimumHeight()<PaperComponents.dp(c,48))throw new Exception("Manual reminder input missing/too small");input[0].requestFocus();((android.view.inputmethod.InputMethodManager)c.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(input[0],android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);});
        waitForIdleSync();Thread.sleep(450);uiAssert(()->{View decor=time[0].getWindow().getDecorView();dialogWidthV15(c,decor);Rect visible=new Rect();decor.getWindowVisibleDisplayFrame(visible);Rect field=screenRect(input[0]);if(field.bottom>visible.bottom||field.top<visible.top)throw new Exception("Keyboard or large font clips reminder input");if(input[0].getCurrentTextColor()!=UiTokens.ink("true".equals(args.getString("night")))||!AppTypography.typeface(c,false).equals(input[0].getTypeface()))throw new Exception("Reminder text does not follow font/theme");input[0].setText("24:99");time[0].getButton(AlertDialog.BUTTON_POSITIVE).performClick();if(!time[0].isShowing()||input[0].getError()==null)throw new Exception("Invalid HH:mm reminder accepted");});
        screenshot(name+"-reminder-keyboard-invalid");uiAssert(()->{input[0].setText("8:05");input[0].onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);});waitForIdleSync();Thread.sleep(300);
        if(!"08:05".equals(NutritionStore.read(prefs,NutritionStore.PROFILE).optString("breakfastNotificationTime")))throw new Exception("Reminder IME save did not normalize/persist HH:mm");
        uiAssert(()->{time[0]=(AlertDialog)invoke(screen,"showNotificationTimePicker",new Class[]{String.class,String.class,String.class},"早餐提醒","breakfastNotificationTime","08:00");((android.widget.EditText)time[0].getWindow().getDecorView().findViewWithTag("reminder_time_input")).setText("11:22");time[0].getButton(AlertDialog.BUTTON_NEGATIVE).performClick();});waitForIdleSync();
        if(!"08:05".equals(NutritionStore.read(prefs,NutritionStore.PROFILE).optString("breakfastNotificationTime")))throw new Exception("Cancelling reminder changed stored time");uiAssert(()->assertRenderPure(screen,prefs,before));
    }
    private void revisionV15(Context c,android.content.SharedPreferences prefs)throws Exception {
        FoodAnalysisJobService.cancelAll(c);FoodDraftStore.discard(c,FoodDraftStore.pendingSources(c));installTransport();
        String name=args.getString("name","beta15"),today=HomeCalendarDates.key(Calendar.getInstance());Calendar yesterdayDate=Calendar.getInstance();yesterdayDate.add(Calendar.DATE,-1);String yesterday=HomeCalendarDates.key(yesterdayDate);
        JSONObject fixture=unifiedDays(c,today),yesterdayDay=new JSONObject(fixture.getJSONObject(today).toString());
        for(String meal:new String[]{"breakfast","lunch","dinner"}){JSONObject food=yesterdayDay.getJSONObject(meal).getJSONArray("foodItems").getJSONObject(0);food.put("dishName","昨天的"+food.optString("dishName")).put("providerModel","qa-recorded-vision");fixture.getJSONObject(today).getJSONObject(meal).getJSONArray("foodItems").getJSONObject(0).put("providerModel","qa-recorded-vision");}
        fixture.put(yesterday,yesterdayDay);String before=fixture.toString();
        JSONObject profile=new JSONObject().put("new_ui_v2",true).put("decoration","true".equals(args.getString("night"))?"night":"blue").put("followSystemNight",false).put("font",100)
            .put("foodAutoAnalyze",false).put("foodPhotoConsent",false).put("autoCloudSync",false).put("mealNotificationsEnabled",false).put("breakfastNotificationTime","07:15")
            .put("seenAnnouncementVersion",c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName);
        prefs.edit().remove("food_processing_preview").putString(NutritionStore.PROFILE,profile.toString()).putString(NutritionStore.DAYS,before).commit();
        MainActivity screen=(MainActivity)startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));settleV15();
        uiAssert(()->{if(Math.abs(UiTokens.GESTURE_RESPONSE_SECONDS-.4f)>.001f)throw new Exception("Gesture spring response is not 0.4 seconds");if(!today.equals(HomeCalendarDates.key(pagerV15(screen).currentDate())))throw new Exception("Day pager does not start today");assertRenderPure(screen,prefs,before);});
        // Pulling the paper cannot expand the calendar: expansion is owned by the date area.
        Rect paper=screenRect(paperV15(screen));float x=paper.left+paper.width()*.9f,y=paper.top+paper.height()*.35f;long down=android.os.SystemClock.uptimeMillis();
        pointerV15(down,MotionEvent.ACTION_DOWN,x,y);movesV15(down,x,y,x,y+PaperComponents.dp(c,85),9);pointerV15(down,MotionEvent.ACTION_UP,x,y+PaperComponents.dp(c,85));settleV15();
        uiAssert(()->{if(calendarV15(screen).isExpanded()||calendarV15(screen).canCollapse())throw new Exception("Downward paper gesture unexpectedly expands month");assertRenderPure(screen,prefs,before);});
        expandDatesV15(screen,c,name);monthGesturesV15(screen,c,before,prefs,name);collapsePaperV15(screen,c,name);
        uiAssert(()->{Calendar week=HomeCalendarDates.monday(Calendar.getInstance());if(!HomeCalendarDates.key(week).equals(HomeCalendarDates.key(calendarV15(screen).weekStart())))throw new Exception("Collapsing an unselected browsed month did not restore today's week");if(!today.equals(invoke(screen,"homeDate",new Class[]{})))throw new Exception("Month browsing changed today selection");assertRenderPure(screen,prefs,before);});
        expandDatesV15(screen,c,name+"-historical");final View[] picked={null};uiAssert(()->{picked[0]=calendarV15(screen).findViewWithTag("home_calendar_day_"+yesterday);if(picked[0]==null||!picked[0].isEnabled())throw new Exception("Past date is not selectable");});
        Rect target=screenRect(picked[0]);down=android.os.SystemClock.uptimeMillis();pointerV15(down,MotionEvent.ACTION_DOWN,target.exactCenterX(),target.exactCenterY());pointerV15(down,MotionEvent.ACTION_UP,target.exactCenterX(),target.exactCenterY());settleV15();
        uiAssert(()->{if(!yesterday.equals(invoke(screen,"homeDate",new Class[]{}))||!calendarV15(screen).isExpanded()||!calendarV15(screen).wasDatePickedDuringExpansion())throw new Exception("Selected historical day was not retained across expanded rerender");});
        monthGesturesV15(screen,c,before,prefs,name+"-selected");collapsePaperV15(screen,c,name+"-selected");
        uiAssert(()->{if(!yesterday.equals(HomeCalendarDates.key(pagerV15(screen).currentDate()))||!HomeCalendarDates.key(HomeCalendarDates.monday(yesterdayDate)).equals(HomeCalendarDates.key(calendarV15(screen).weekStart())))throw new Exception("Collapsing after a real selection lost historical date/week");assertRenderPure(screen,prefs,before);});
        dayGestureV15(screen,true,name+"-return-today");
        // Future boundary is elastic, never a fake/future diary page or write.
        TodayDayPager pager=pagerV15(screen);Rect bounds=screenRect(pager);float sx=bounds.left+bounds.width()*.76f,sy=bounds.top+Math.min(bounds.height()*.4f,PaperComponents.dp(c,105)),ex=sx-bounds.width()*.55f;down=android.os.SystemClock.uptimeMillis();
        pointerV15(down,MotionEvent.ACTION_DOWN,sx,sy);movesV15(down,sx,sy,ex,sy,10);
        uiAssert(()->{if(android.animation.ValueAnimator.areAnimatorsEnabled()&&(offsetV15(pager)>=0||Math.abs(offsetV15(pager))>=pager.getWidth()*.55f))throw new Exception("Future boundary has no progressive elastic resistance");if(!today.equals(HomeCalendarDates.key(pager.currentDate())))throw new Exception("Future drag entered tomorrow");});
        screenshotNowV15(name+"-future-elastic-edge");pointerV15(down,MotionEvent.ACTION_UP,ex,sy);settleV15();
        uiAssert(()->{if(!today.equals(HomeCalendarDates.key(pagerV15(screen).currentDate()))||Math.abs(offsetV15(pagerV15(screen)))>.5f)throw new Exception("Future edge failed to return to today");assertRenderPure(screen,prefs,before);});
        dayGestureV15(screen,false,name+"-yesterday");uiAssert(()->{if(!visibleText(paperV15(screen)).contains("昨天的"))throw new Exception("Yesterday page reuses today's content instead of actual diary");});dayGestureV15(screen,true,name+"-today");
        // A release may be grabbed and reversed without snapping or a stale date commit.
        if(android.animation.ValueAnimator.areAnimatorsEnabled()){
            TodayDayPager moving=pagerV15(screen);Rect a=screenRect(moving);float ax=a.left+a.width()*.23f,ay=a.top+Math.min(a.height()*.4f,PaperComponents.dp(c,105)),bx=ax+a.width()*.4f;long first=android.os.SystemClock.uptimeMillis();
            pointerV15(first,MotionEvent.ACTION_DOWN,ax,ay);movesV15(first,ax,ay,bx,ay,8);pointerV15(first,MotionEvent.ACTION_UP,bx,ay);Thread.sleep(35);
            uiAssert(()->{slidesV15(moving,1);if(!((GestureSpring)ownField(moving,"spring")).isRunning())throw new Exception("Day page lacks release spring");});
            long interrupt=android.os.SystemClock.uptimeMillis();pointerV15(interrupt,MotionEvent.ACTION_DOWN,bx,ay);uiAssert(()->{if(Math.abs(offsetV15(moving))<PaperComponents.dp(c,10))throw new Exception("Grabbing diary spring snapped position");});
            movesV15(interrupt,bx,ay,bx-a.width()*.25f,ay,6);pointerV15(interrupt,MotionEvent.ACTION_CANCEL,bx-a.width()*.25f,ay);settleV15();
            uiAssert(()->{if(!today.equals(HomeCalendarDates.key(pagerV15(screen).currentDate()))||Math.abs(offsetV15(moving))>.5f)throw new Exception("Interrupted/cancelled diary gesture committed stale date");assertRenderPure(screen,prefs,before);});
        }
        // Real media long press keeps its rotate/delete controller; editing cannot become a day swipe.
        final View[] media={null};uiAssert(()->{View row=paperV15(screen).findViewWithTag("day_food_breakfast_qa-breakfast");media[0]=(View)row.findViewWithTag("day_entry_image").getParent();media[0].requestRectangleOnScreen(new Rect(0,0,media[0].getWidth(),media[0].getHeight()),true);});waitForIdleSync();Thread.sleep(200);
        Rect image=screenRect(media[0]);float mx=image.exactCenterX(),my=image.exactCenterY();down=android.os.SystemClock.uptimeMillis();pointerV15(down,MotionEvent.ACTION_DOWN,mx,my);Thread.sleep(ViewConfiguration.getLongPressTimeout()+100);pointerV15(down,MotionEvent.ACTION_UP,mx,my);waitForIdleSync();
        if(descriptionNode("向左旋转90度")==null||descriptionNode("删除这张贴纸")==null)throw new Exception("New day gestures stole real media long press");screenshot(name+"-media-long-press");
        down=android.os.SystemClock.uptimeMillis();pointerV15(down,MotionEvent.ACTION_DOWN,mx,my);movesV15(down,mx,my,mx+PaperComponents.dp(c,60),my,8);pointerV15(down,MotionEvent.ACTION_UP,mx+PaperComponents.dp(c,60),my);settleV15();
        uiAssert(()->{if(!today.equals(HomeCalendarDates.key(pagerV15(screen).currentDate()))||Math.abs(offsetV15(pagerV15(screen)))>.5f)throw new Exception("Editing a sticker accidentally changed day");invoke(screen,"exitMediaEditMode",new Class[]{boolean.class},false);assertRenderPure(screen,prefs,before);});
        final AlertDialog[] detail={null};final JSONObject food=fixture.getJSONObject(today).getJSONObject("breakfast").getJSONArray("foodItems").getJSONObject(0);
        uiAssert(()->{FoodUi ui=(FoodUi)invoke(screen,"foodUi",new Class[]{});detail[0]=ui.details(today,"breakfast",food);});waitForIdleSync();Thread.sleep(150);
        uiAssert(()->{View decor=detail[0].getWindow().getDecorView();dialogWidthV15(c,decor);assertResultFooterV15(decor,food,true);});screenshot(name+"-detail-footer");
        View detailImage=detail[0].getWindow().getDecorView().findViewWithTag("food_detail_image");Rect original=screenRect(detailImage);down=android.os.SystemClock.uptimeMillis();pointerV15(down,MotionEvent.ACTION_DOWN,original.exactCenterX(),original.exactCenterY());pointerV15(down,MotionEvent.ACTION_UP,original.exactCenterX(),original.exactCenterY());waitForIdleSync();
        uiAssert(()->{if(detail[0].isShowing()||!Boolean.TRUE.equals(field(screen,"inPhotoViewer")))throw new Exception("Image tap no longer opens original photo");invoke(screen,"closePhotoViewer",new Class[]{});assertRenderPure(screen,prefs,before);});
        settingsDialogsV15(screen,c,prefs,before,name);if(apiCalls.get()!=0)throw new Exception("UI gestures/settings called a vision API");MealReminderReceiver.cancelAll(c);
        Bundle result=new Bundle();result.putString("stream","PASS: real screen-space date-only expansion and paper-wide collapse, intermediate month/day pages, reverse/interruption/cancel and 0.4s spring, unselected browse restores today vs historical selection retained, future elastic boundary, media long press/rotation-delete affordances and original-image tap preserved; matched outline icon tint/alpha, shared dialog gutters, first/last privacy font/theme and manual reminder keyboard invalid/save/cancel, final tip provenance footer and unchanged diary. Photos/nutrients are explicit QA fixtures, not real model quality.\n");finish(Activity.RESULT_OK,result);
    }
    private void dialogPolicyV15(Context c,android.content.SharedPreferences prefs)throws Exception {
        FoodAnalysisJobService.cancelAll(c);FoodDraftStore.discard(c,FoodDraftStore.pendingSources(c));
        String date=HomeCalendarDates.key(Calendar.getInstance()),before=unifiedDays(c,date).toString();
        JSONObject profile=new JSONObject().put("new_ui_v2",true).put("decoration","true".equals(args.getString("night"))?"night":"blue")
            .put("font",100).put("autoCloudSync",false).put("foodAutoAnalyze",false).put("mealNotificationsEnabled",false)
            .put("breakfastNotificationTime","07:15").put("seenAnnouncementVersion",c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName);
        prefs.edit().putString(NutritionStore.DAYS,before).putString(NutritionStore.PROFILE,profile.toString()).commit();
        MainActivity screen=(MainActivity)startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));settleV15();
        settingsDialogsV15(screen,c,prefs,before,"beta15-ime");
        final AlertDialog[] dialog={null};
        settingsRoot(screen);uiAssert(()->{assertSettingPresence(screen.getWindow().getDecorView(),"旧版入口",false);assertSettingPresence(screen.getWindow().getDecorView(),"新版入口",false);});
        uiAssert(()->dialog[0]=((FoodUi)invoke(screen,"foodUi",new Class[]{})).dayNutrition(date));waitForIdleSync();
        uiAssert(()->dialogWidthV15(c,dialog[0].getWindow().getDecorView()));screenshot("beta15-day-nutrition-gutters");
        uiAssert(()->{dialog[0].dismiss();assertRenderPure(screen,prefs,before);});
        Bundle result=new Bundle();result.putString("stream","PASS: themed dialog gutters/font/menu recycling, reminder with requested system IME and invalid/save/cancel, legacy-switch cancellation and daily nutrition shared width, unchanged diary\n");finish(Activity.RESULT_OK,result);
    }
    private void modernOnlyV16(Context c,android.content.SharedPreferences prefs)throws Exception {
        FoodAnalysisJobService.cancelAll(c);FoodDraftStore.discard(c,FoodDraftStore.pendingSources(c));installTransport();
        String today=HomeCalendarDates.key(Calendar.getInstance());JSONObject fixture=unifiedDays(c,today);
        JSONObject day=fixture.getJSONObject(today);day.put("manualCaloriesOverride",true).put("manualCaloriesTotal",1234);
        String before=fixture.toString();
        JSONObject profile=new JSONObject().put("new_ui_v2",false).put("globalFontFollowsBook",false).put("bookFont","xiaolai")
            .put("nightMode",false).put("decoration","blue").put("foodAutoAnalyze",false).put("foodPhotoConsent",false).put("autoCloudSync",false)
            .put("seenAnnouncementVersion",c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName);
        prefs.edit().remove("food_processing_preview").putString(NutritionStore.PROFILE,profile.toString()).putString(NutritionStore.DAYS,before).commit();
        MainActivity screen=(MainActivity)startActivitySync(new Intent(c,MainActivity.class).putExtra("new_ui_v2",false).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));settleV15();
        uiAssert(()->{
            View decor=screen.getWindow().getDecorView();if(decor.findViewWithTag("today_day_pager")==null)throw new Exception("Old profile/Intent still selects legacy home");
            HomeCalendarView calendar=calendarV15(screen);View header=calendar.findViewWithTag("home_calendar_header");
            if(header==null||header.getHeight()!=0||header.getVisibility()==View.VISIBLE||calendar.findViewWithTag("home_calendar_toggle").isEnabled())throw new Exception("Collapsed year/month header still consumes space or accepts taps");
            if(calendar.weekStrip().getHeight()<PaperComponents.dp(c,64))throw new Exception("Week dates were clipped while removing header");
            View photo=decor.findViewWithTag("smart_add_food"),pen=decor.findViewWithTag("manual_add_food");if(pen==null||!pen.isShown()||!pen.isClickable())throw new Exception("No handwritten entry next to recognition");
            Rect left=screenRect(photo),right=screenRect(pen);if(Rect.intersects(left,right)||right.left<=left.left||right.height()<PaperComponents.dp(c,48))throw new Exception("Pen and recognition actions overlap or are inaccessible");
            if(FoodUi.displayCalories(NutritionStore.read(prefs,NutritionStore.DAYS).getJSONObject(today))!=1234)throw new Exception("Upgrade lost manual calorie priority");
            assertRenderPure(screen,prefs,before);
        });screenshot("beta16-compact-home");
        expandDatesV15(screen,c,"beta16");uiAssert(()->{View header=calendarV15(screen).findViewWithTag("home_calendar_header");if(header.getHeight()<PaperComponents.dp(c,48)||!calendarV15(screen).findViewWithTag("home_calendar_toggle").isEnabled())throw new Exception("Expanded month header missing");});collapsePaperV15(screen,c,"beta16");
        settingsRoot(screen);uiAssert(()->{View decor=screen.getWindow().getDecorView();assertSettingPresence(decor,"旧版入口",false);assertSettingPresence(decor,"新版入口",false);assertRenderPure(screen,prefs,before);});
        uiAssert(()->invoke(screen,"renderThemeSettingsPage",new Class[]{}));waitForIdleSync();
        uiAssert(()->{View decor=screen.getWindow().getDecorView();assertSettingPresence(decor,"全局字体跟随笔记字体",false);if(!visibleText(setting(decor,"笔记字体")).contains("小赖手写(默认)"))throw new Exception("Wrong default font subtitle");});
        uiAssert(()->invoke(screen,"chooseBookFont",new Class[]{}));waitForIdleSync();if(node("默认")==null||node("小赖手写（内置默认）")!=null)throw new Exception("Default font menu label not simplified");sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);waitForIdleSync();
        clickSetting("夜间模式");settleV15();uiAssert(()->{if(!Boolean.TRUE.equals(invoke(screen,"nightDecoration",new Class[]{}))||!NutritionStore.read(prefs,NutritionStore.PROFILE).optBoolean("nightMode"))throw new Exception("Manual switch did not enable night on a light system");});screenshot("beta16-manual-night");
        clickSetting("夜间模式");settleV15();uiAssert(()->{if(!Boolean.FALSE.equals(invoke(screen,"nightDecoration",new Class[]{}))||NutritionStore.read(prefs,NutritionStore.PROFILE).optBoolean("nightMode",true))throw new Exception("Manual switch cannot return to light");if(!"blue".equals(NutritionStore.read(prefs,NutritionStore.PROFILE).optString("decoration")))throw new Exception("Night switch lost selected accent");assertRenderPure(screen,prefs,before);invoke(screen,"showTab",new Class[]{int.class,boolean.class},0,false);});settleV15();
        uiAssert(()->screen.getWindow().getDecorView().findViewWithTag("manual_add_food").performClick());click("早餐");waitForIdleSync();
        final View[] editor={null};uiAssert(()->{editor[0]=dialogRootV15("manual_food_text");((android.widget.EditText)editor[0].findViewWithTag("manual_food_text")).setText("取消不保存的面");});click("取消");uiAssert(()->assertRenderPure(screen,prefs,before));
        final AlertDialog[] dialog={null};uiAssert(()->dialog[0]=(AlertDialog)invoke(screen,"showManualMealTextEditor",new Class[]{String.class,String.class},today,"breakfast"));waitForIdleSync();
        uiAssert(()->{android.widget.EditText input=(android.widget.EditText)dialog[0].getWindow().getDecorView().findViewWithTag("manual_food_text");input.setText(" ");dialog[0].getButton(-1).performClick();if(!dialog[0].isShowing()||input.getError()==null)throw new Exception("Empty name accepted");input.setText("手写豆浆和包子");dialog[0].getButton(-1).performClick();});settleV15();
        JSONObject saved=NutritionStore.read(prefs,NutritionStore.DAYS),meal=saved.getJSONObject(today).getJSONObject("breakfast"),old=fixture.getJSONObject(today).getJSONObject("breakfast");
        if(!meal.optBoolean("manualTextEntry")||!"手写豆浆和包子".equals(meal.optString("text"))||!meal.getJSONArray("foodItems").toString().equals(old.getJSONArray("foodItems").toString())||!meal.getJSONArray("photos").toString().equals(old.getJSONArray("photos").toString()))throw new Exception("Manual text lost recognized food/photo or failed to save");
        if(!saved.getJSONObject(today).getJSONObject("lunch").toString().equals(fixture.getJSONObject(today).getJSONObject("lunch").toString())||FoodUi.displayCalories(saved.getJSONObject(today))!=1234)throw new Exception("Manual meal changed another meal/calorie override");
        uiAssert(()->{if(!visibleText(paperV15(screen)).contains("手写豆浆和包子"))throw new Exception("Text does not coexist visibly with photo on home");});
        if(!FoodRecordText.story("早上",meal).contains("手写豆浆和包子")||apiCalls.get()!=0||!FoodDraftStore.pendingSources(c).isEmpty())throw new Exception("Text needs image/API/draft or is absent from notebook");screenshot("beta16-written-food-with-photo");
        uiAssert(()->dialog[0]=(AlertDialog)invoke(screen,"showManualMealTextEditor",new Class[]{String.class,String.class},today,"dinner"));waitForIdleSync();uiAssert(()->{((android.widget.EditText)dialog[0].getWindow().getDecorView().findViewWithTag("manual_food_text")).setText("清粥");dialog[0].getButton(-1).performClick();});settleV15();
        Bundle result=new Bundle();result.putString("stream","PASS: old flags cannot activate removed UI, no version/font-follow controls, compact calendar header follows expansion, default font labels, manual night on/off preserves accent and diary, separate accessible pen, cancel/empty/save text without images/API/drafts, text coexists with recognized food and notebook names, unknown nutrition and old media/manual calories preserved\n");finish(Activity.RESULT_OK,result);
    }
    private void fixedCalendarGeometryV17(MainActivity screen,Context c,String before,android.content.SharedPreferences prefs)throws Exception {
        for(int[] date:new int[][]{{2026,9},{2026,7},{2021,1}}){
            Calendar anchor=Calendar.getInstance();anchor.set(date[0],date[1],1);
            uiAssert(()->{setMainField(screen,"todayWeekAnchor",anchor);setMainField(screen,"todayWeekSelection",(String)invoke(screen,"homeDate",new Class[]{}));setMainField(screen,"homeCalendarExpanded",true);invoke(screen,"renderCurrent",new Class[]{});});settleV15();
            HomeCalendarView calendar=calendarV15(screen);View pager=(View)ownField(calendar,"monthPager");
            Rect paper=screenRect(paperV15(screen));int height=calendar.getHeight(),pagerHeight=pager.getHeight();
            uiAssert(()->{ViewGroup grid=(ViewGroup)calendar.findViewWithTag("home_calendar_grid");if(grid.getChildCount()!=6||tagged(grid,"home_calendar_day_").size()!=42)throw new Exception("Expanded calendar must have six real weeks");});
            Rect area=screenRect(pager);float x=area.left+area.width()*.93f,y=area.top+PaperComponents.dp(c,30);long down=android.os.SystemClock.uptimeMillis();
            pointerV15(down,MotionEvent.ACTION_DOWN,x,y);float previous=x;
            for(float fraction:new float[]{.2f,.5f,.85f}){
                float end=x-area.width()*fraction;movesV15(down,previous,y,end,y,5);previous=end;
                uiAssert(()->{if(calendar.getHeight()!=height||pager.getHeight()!=pagerHeight||!paper.equals(screenRect(paperV15(screen))))throw new Exception("Month drag displaced food paper vertically");assertRenderPure(screen,prefs,before);});
            }
            pointerV15(down,MotionEvent.ACTION_CANCEL,previous,y);settleV15();
            uiAssert(()->{if(calendar.getHeight()!=height||!paper.equals(screenRect(paperV15(screen))))throw new Exception("Cancelled month drag changed geometry");});
            down=android.os.SystemClock.uptimeMillis();pointerV15(down,MotionEvent.ACTION_DOWN,x,y);movesV15(down,x,y,x-area.width()*.65f,y,9);pointerV15(down,MotionEvent.ACTION_UP,x-area.width()*.65f,y);settleV15();
            uiAssert(()->{if(calendar.getHeight()!=height||pager.getHeight()!=pagerHeight||!paper.equals(screenRect(paperV15(screen))))throw new Exception("Settled adjacent month changed geometry");assertRenderPure(screen,prefs,before);});
        }
        screenshot("beta17-calendar-stable-six-weeks");
    }
    private void revisionV17(Context c,android.content.SharedPreferences prefs)throws Exception {
        FoodAnalysisJobService.cancelAll(c);FoodDraftStore.discard(c,FoodDraftStore.pendingSources(c));installTransport();
        String today=HomeCalendarDates.key(Calendar.getInstance()),before=unifiedDays(c,today).toString();
        JSONObject profile=new JSONObject().put("nightMode","true".equals(args.getString("night"))).put("bookFont","xiaolai").put("decoration","blue")
            .put("apiEnabled",true).put("apiKey","qa-not-a-real-key").put("apiEndpoint","https://food.qa.invalid/v1/chat/completions").put("apiModel","custom-food-model")
            .put("apiVisionSupported",false).put("foodAutoAnalyze",false).put("foodPhotoConsent",false).put("aiAnalysisEnabled",false).put("aiAutoAnalysis",false)
            .put("calorieRecognition",true).put("aiVisionEnabled",true).put("autoCloudSync",false).put("seenAnnouncementVersion",c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName);
        prefs.edit().remove("food_processing_preview").putString(NutritionStore.DAYS,before).putString(NutritionStore.PROFILE,profile.toString()).commit();
        MainActivity screen=(MainActivity)startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));settleV15();
        fixedCalendarGeometryV17(screen,c,before,prefs);
        settingsRoot(screen);clickSetting("智能助手");
        uiAssert(()->{View decor=screen.getWindow().getDecorView();View api=setting(decor,"调用 API"),food=setting(decor,"餐图识别"),nutritionist=setting(decor,"营养师");if(screenRect(api).top>=screenRect(food).top||screenRect(food).top>=screenRect(nutritionist).top)throw new Exception("Assistant root order is not API, photo, nutritionist");for(String removed:new String[]{"餐图识别与隐私","食物识别","自动识别热量","AI分析","自动分析","AI读取记录","开启记忆","每日热量目标"})assertSettingPresence(decor,removed,false);
            if(!Boolean.TRUE.equals(invoke(screen,"apiMaySendImages",new Class[]{})))throw new Exception("Old false name heuristic still blocks unknown model");List<?> endpoints=(List<?>)invoke(screen,"apiEndpointCandidates",new Class[]{String.class},"https://food.qa.invalid/v1/chat/completions");if(endpoints.size()!=1||!endpoints.get(0).equals("https://food.qa.invalid/v1/chat/completions"))throw new Exception("Key probe can cross providers");});
        screenshot("beta17-assistant-root");clickSetting("营养师");
        uiAssert(()->{View decor=screen.getWindow().getDecorView();if(decor.findViewWithTag("nutritionist_settings_page")==null)throw new Exception("Missing nutritionist page");for(String title:new String[]{"营养师","自动分析","AI读取记录","开启记忆"})assertSettingPresence(decor,title,true);for(String title:new String[]{"食物识别","自动识别热量","云端营养自动识别","AI分析"})assertSettingPresence(decor,title,false);});
        clickSetting("营养师");clickSetting("AI读取记录");clickSetting("开启记忆");
        if(!NutritionStore.read(prefs,NutritionStore.PROFILE).optBoolean("aiAnalysisEnabled")||FoodAnalysisJobService.enabled(NutritionStore.read(prefs,NutritionStore.PROFILE)))throw new Exception("Nutritionist toggles changed photo consent");
        screenshot("beta17-nutritionist-settings");sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);settleV15();
        uiAssert(()->{if(screen.getWindow().getDecorView().findViewWithTag("smart_assistant_page")==null)throw new Exception("Subpage back skips assistant root");});clickSetting("餐图识别");
        uiAssert(()->{View decor=screen.getWindow().getDecorView();for(String title:new String[]{"自动识别菜名和营养","严格 JSON Schema（兼容模式）","识别已保存食物图片"})assertSettingPresence(decor,title,true);for(String title:new String[]{"每日热量目标","AI分析","自动分析","AI读取记录","开启记忆"})assertSettingPresence(decor,title,false);});
        clickSetting("严格 JSON Schema（兼容模式）");if(!NutritionStore.read(prefs,NutritionStore.PROFILE).optBoolean("foodJsonSchema"))throw new Exception("Schema toggle not saved");
        clickSetting("自动识别菜名和营养");click("暂不开启");if(FoodAnalysisJobService.enabled(NutritionStore.read(prefs,NutritionStore.PROFILE)))throw new Exception("Cancelled photo consent saved");
        clickSetting("自动识别菜名和营养");click("同意并开启");settleV15();
        uiAssert(()->{if(screen.getWindow().getDecorView().findViewWithTag("food_recognition_settings_page")==null||!FoodAnalysisJobService.enabled(NutritionStore.read(prefs,NutritionStore.PROFILE)))throw new Exception("Consent did not preserve photo subpage");});
        clickSetting("识别已保存食物图片");click("取消");if(apiCalls.get()!=0)throw new Exception("Cancelling batch caused an upload");
        clickSetting("自动识别菜名和营养");if(FoodAnalysisJobService.enabled(NutritionStore.read(prefs,NutritionStore.PROFILE))||!NutritionStore.read(prefs,NutritionStore.PROFILE).optBoolean("aiAnalysisEnabled"))throw new Exception("Turning off photo recognition disabled nutritionist");
        screenshot("beta17-photo-settings");sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);settleV15();sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);settleV15();
        uiAssert(()->{invoke(screen,"showTab",new Class[]{int.class,boolean.class},0,false);});settleV15();
        uiAssert(()->{ViewGroup nav=(ViewGroup)field(screen,"navBar");nav.getChildAt(0).performClick();});settleV15();
        uiAssert(()->{if(!Boolean.TRUE.equals(field(screen,"inAiAnalysisPage")))throw new Exception("Nutritionist no longer opens by Today retap");invoke(screen,"closeAiAnalysisPage",new Class[]{});});settleV15();
        uiAssert(()->{invoke(screen,"renderBookDetail",new Class[]{String.class,boolean.class},today,false);});settleV15();
        uiAssert(()->{View paper=screen.getWindow().getDecorView().findViewWithTag("book_detail_notebook_page");if(paper==null||paper.getHeight()>PaperComponents.dp(c,486))throw new Exception("Notebook paper not shortened");assertRenderPure(screen,prefs,before);});screenshot("beta17-notebook-shorter");
        if(apiCalls.get()!=0)throw new Exception("Removed legacy automatic calorie feature still made requests");
        FoodAnalysisJobService.cancelAll(c);Bundle result=new Bundle();result.putString("stream","PASS: stable calendar and food-paper geometry through partial/cancel/committed drags across 4/5/6-week months; separate assistant root/photo/nutritionist pages, independent consent/toggles, batch cancellation with no upload, Today retap nutritionist, shorter notebook and no hidden legacy calorie calls; unknown model not rejected by name, endpoint probe restricted, diary unchanged. No vivo or real provider latency validation.\n");finish(Activity.RESULT_OK,result);
    }
    private void queuePriorityV17(Context c,android.content.SharedPreferences prefs)throws Exception {
        FoodAnalysisJobService.cancelAll(c);FoodDraftStore.discard(c,FoodDraftStore.pendingSources(c));
        java.lang.reflect.Field active=FoodAnalysisJobService.class.getDeclaredField("active");active.setAccessible(true);
        long idleDeadline=android.os.SystemClock.elapsedRealtime()+10000;
        while(active.getBoolean(null)&&android.os.SystemClock.elapsedRealtime()<idleDeadline)Thread.sleep(25);
        if(active.getBoolean(null))throw new Exception("Previous worker did not stop before queue scenario");
        installTransport();streamName=false;streamTotalsFirst=false;holdResponse=true;
        final List<String> order=Collections.synchronizedList(new ArrayList<String>());
        final java.util.concurrent.CountDownLatch draftEntered=new java.util.concurrent.CountDownLatch(1),draftReleased=new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.atomic.AtomicReference<Throwable> observedFailure=new java.util.concurrent.atomic.AtomicReference<>();
        requestObserverV17=body->{try{
            String prompt=body.getJSONArray("messages").getJSONObject(0).getJSONArray("content").getJSONObject(0).getString("text"),label="";
            for(String expected:new String[]{"QA_QUEUE_A","QA_QUEUE_B","QA_QUEUE_C"})if(prompt.contains(JSONObject.quote(expected)))label=expected;
            if(label.isEmpty())throw new Exception("Unexpected queue request without test identity");
            order.add(label);
            if("QA_QUEUE_C".equals(label)){draftEntered.countDown();if(!draftReleased.await(20,java.util.concurrent.TimeUnit.SECONDS))throw new Exception("C response was never released");}
        }catch(Throwable error){observedFailure.set(error);throw new RuntimeException(error);}};
        String date=HomeCalendarDates.key(Calendar.getInstance()),suffix=UUID.randomUUID().toString();
        String[] sources={"qa_queue_a_"+suffix+".jpg","qa_queue_b_"+suffix+".jpg","draft_qa_queue_c_"+suffix+".jpg",
            "qa_queue_edited_"+suffix+".jpg","qa_queue_complete_"+suffix+".jpg"};
        String[] alphas={"food_cutout_qa_queue_ab_"+suffix+".png","food_cutout_qa_queue_c_"+suffix+".png"};
        for(String source:sources)layoutSource(c,source);
        Bitmap alpha=Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(alpha);Paint ink=new Paint();ink.setColor(Color.RED);canvas.drawCircle(8,8,5,ink);
        try{for(String name:alphas)try(OutputStream out=new FileOutputStream(new File(c.getFilesDir(),name))){if(!alpha.compress(Bitmap.CompressFormat.PNG,100,out))throw new Exception("Unable to seed local alpha fixture");}}finally{alpha.recycle();}
        String alphaHash=FoodCutoutEngine.hash(new File(c.getFilesDir(),alphas[0]));long alphaModified=new File(c.getFilesDir(),alphas[0]).lastModified();
        JSONObject[] initial=new JSONObject[2];
        for(int i=0;i<2;i++)initial[i]=new JSONObject().put("id","qa-queue-"+i).put("analysisId","qa-queue-old-"+i)
            .put("sourcePhoto",sources[i]).put("imageHash",FoodCutoutEngine.hash(new File(c.getFilesDir(),sources[i])))
            .put("dishNameHint",i==0?"QA_QUEUE_A":"QA_QUEUE_B").put("dishName","等待队列测试")
            .put("cutoutFile",alphas[0]).put("cutoutRect",new JSONArray().put(2).put(3).put(14).put(15))
            .put("cutoutEdited",true).put("cutoutUserEdited",true).put("cutoutModel","qa-reused-alpha-not-inference")
            .put("status","pending").put("analysisStatus","needs_retry").put("userEdited",false).put("createdAt",1000L+i);
        JSONObject edited=FoodAnalysisResult.parse(RESPONSE).value.put("id","qa-queue-edited").put("analysisId","edited-generation")
            .put("sourcePhoto",sources[3]).put("cutoutFile",alphas[0]).put("userEdited",true).put("status","completed").put("analysisStatus","completed");
        JSONObject complete=FoodAnalysisResult.parse(RESPONSE).value.put("id","qa-queue-complete").put("analysisId","complete-generation")
            .put("sourcePhoto",sources[4]).put("cutoutFile",alphas[0]).put("userEdited",false).put("status","completed").put("analysisStatus","completed");
        String editedBefore=edited.toString(),completeBefore=complete.toString();
        JSONObject day=new JSONObject().put("breakfast",new JSONObject().put("photos",new JSONArray().put(sources[0])).put("foodItems",new JSONArray().put(initial[0])))
            .put("lunch",new JSONObject().put("foodItems",new JSONArray().put(initial[1])))
            .put("dinner",new JSONObject().put("text","保留手写晚餐").put("foodItems",new JSONArray().put(edited).put(complete)))
            .put("manualCaloriesOverride",true).put("manualCaloriesTotal",1234);
        JSONObject profile=new JSONObject().put("apiEnabled",true).put("apiEndpoint","https://food.qa.invalid/v1/chat/completions")
            .put("apiKey","qa-not-a-real-key").put("apiModel","qa-vision").put("foodAutoAnalyze",true).put("foodPhotoConsent",true)
            .put("aiAnalysisEnabled",false).put("aiAutoAnalysis",false).put("autoCloudSync",false);
        prefs.edit().putString(NutritionStore.PROFILE,profile.toString()).putString(NutritionStore.DAYS,new JSONObject().put(date,day).toString())
            .putString(FoodDraftStore.KEY,"{}").putString(FoodAnalysisJobService.QUEUE,"[]").commit();
        try{
            FoodAnalysisJobService.enqueue(c,date,"breakfast",sources[0],false);
            if(!responseEntered.await(20,java.util.concurrent.TimeUnit.SECONDS))throw new Exception("A did not enter held network response");
            if(order.size()!=1||!"QA_QUEUE_A".equals(order.get(0)))throw new Exception("First held request is not A: "+order);
            FoodAnalysisJobService.enqueue(c,date,"lunch",sources[1],false);
            FoodAnalysisJobService.enqueue(c,date,"dinner",sources[3],false);FoodAnalysisJobService.enqueue(c,date,"dinner",sources[4],false);
            JSONObject savedDinner=NutritionStore.meal(NutritionStore.read(prefs,NutritionStore.DAYS),date,"dinner");
            if(!editedBefore.equals(NutritionStore.find(savedDinner,"qa-queue-edited").toString())
                ||!completeBefore.equals(NutritionStore.find(savedDinner,"qa-queue-complete").toString()))throw new Exception("Batch overwrote corrected/completed missing-hash history");
            JSONObject queuedB=null;JSONArray pending=FoodAnalysisJobService.jobs(prefs);
            for(int i=0;i<pending.length();i++){JSONObject job=pending.optJSONObject(i);if(job!=null&&sources[1].equals(job.optString("source")))queuedB=job;}
            if(queuedB==null||!queuedB.optBoolean("cutoutDone")||!queuedB.optBoolean("sourceReferenceOnly"))throw new Exception("foodItems-only B did not reuse existing alpha");
            synchronized(NutritionStore.LOCK){
                if(!FoodAnalysisJobService.enqueueDraft(c,date,"dinner",sources[2],false))throw new Exception("Cannot enqueue current draft C");
                JSONObject drafts=FoodDraftStore.read(prefs),record=drafts.getJSONObject(sources[2]),item=record.getJSONObject("item");
                item.put("cutoutFile",alphas[1]).put("dishNameHint","QA_QUEUE_C").put("cutoutRect",new JSONArray().put(0).put(0).put(16).put(16));
                pending=FoodAnalysisJobService.jobs(prefs);boolean found=false;
                for(int i=0;i<pending.length();i++){JSONObject job=pending.optJSONObject(i);if(job!=null&&sources[2].equals(job.optString("source"))){job.put("cutoutDone",true).put("dishNameHint","QA_QUEUE_C");found=true;}}
                if(!found)throw new Exception("Draft C durable task missing");
                prefs.edit().putString(FoodDraftStore.KEY,drafts.toString()).putString(FoodAnalysisJobService.QUEUE,pending.toString()).commit();
            }
            holdResponse=false;responseReleased.countDown();
            if(!draftEntered.await(20,java.util.concurrent.TimeUnit.SECONDS))throw new Exception("New draft did not preempt saved-photo batch: "+order);
            if(order.size()!=2||!"QA_QUEUE_C".equals(order.get(1)))throw new Exception("Expected A,C before B, got "+order);
            FoodDraftStore.discard(c,Collections.singletonList(sources[2]));draftReleased.countDown();
            long deadline=android.os.SystemClock.elapsedRealtime()+20000;
            while(android.os.SystemClock.elapsedRealtime()<deadline&&(FoodAnalysisJobService.jobs(prefs).length()!=0||active.getBoolean(null)))Thread.sleep(25);
            if(observedFailure.get()!=null)throw new Exception("Queue observer failed",observedFailure.get());
            if(FoodAnalysisJobService.jobs(prefs).length()!=0||active.getBoolean(null))throw new Exception("Queue did not fully settle");
            if(!order.equals(Arrays.asList("QA_QUEUE_A","QA_QUEUE_C","QA_QUEUE_B"))||apiCalls.get()!=3)throw new Exception("Wrong request order or duplicates: "+order+", calls="+apiCalls.get());
            JSONObject saved=NutritionStore.read(prefs,NutritionStore.DAYS),savedDay=saved.getJSONObject(date);
            for(int i=0;i<2;i++){
                JSONObject result=NutritionStore.find(savedDay.getJSONObject(i==0?"breakfast":"lunch"),"qa-queue-"+i);
                if(result==null||!"completed".equals(result.optString("analysisStatus"))||!result.has("nutrients"))throw new Exception("Saved request did not complete: "+i);
                if(!alphas[0].equals(result.optString("cutoutFile"))||!result.optBoolean("cutoutEdited")||!result.optBoolean("cutoutUserEdited")
                    ||!"qa-reused-alpha-not-inference".equals(result.optString("cutoutModel"))||!initial[i].getJSONArray("cutoutRect").toString().equals(result.getJSONArray("cutoutRect").toString()))throw new Exception("Saved task rewrote existing alpha/edits");
            }
            if(new File(c.getFilesDir(),alphas[0]).lastModified()!=alphaModified||!alphaHash.equals(FoodCutoutEngine.hash(new File(c.getFilesDir(),alphas[0]))))throw new Exception("Existing alpha bytes changed");
            if(FoodDraftStore.item(c,sources[2])!=null||FoodDraftStore.references(saved,sources[2])||new File(c.getFilesDir(),sources[2]).exists()||new File(c.getFilesDir(),alphas[1]).exists())throw new Exception("Cancelled C was resurrected or leaked owned files");
            savedDinner=savedDay.getJSONObject("dinner");
            if(!editedBefore.equals(NutritionStore.find(savedDinner,"qa-queue-edited").toString())||!completeBefore.equals(NutritionStore.find(savedDinner,"qa-queue-complete").toString())
                ||!"保留手写晚餐".equals(savedDinner.optString("text"))||FoodUi.displayCalories(savedDay)!=1234)throw new Exception("Batch changed corrected results/text/manual calories");
            String completedBefore=saved.toString();FoodAnalysisJobService.enqueue(c,date,"breakfast",sources[0],false);FoodAnalysisJobService.enqueue(c,date,"lunch",sources[1],false);
            if(FoodAnalysisJobService.jobs(prefs).length()!=0||apiCalls.get()!=3||!completedBefore.equals(prefs.getString(NutritionStore.DAYS,"{}")))throw new Exception("Completed same-hash results were queued again");
            Bundle result=new Bundle();result.putString("stream","PASS: real single worker observes newly queued draft after held saved request (A,C,B order), cancelled in-flight draft cannot revive or leak files, foodItems-only saved source recognized without recut, alpha bytes/edited bounds preserved, corrected/completed missing-hash results untouched, duplicate batch has no new request, manual calories and old text preserved. Transport and alpha are labelled QA fixtures, not model quality or real provider speed.\n");finish(Activity.RESULT_OK,result);
        }finally{requestObserverV17=null;holdResponse=false;responseReleased.countDown();draftReleased.countDown();FoodAnalysisJobService.cancelAll(c);}
    }
    private void deepSeekV18(Context c,android.content.SharedPreferences prefs)throws Exception {
        FoodAnalysisJobService.cancelAll(c);FoodDraftStore.discard(c,FoodDraftStore.pendingSources(c));
        deepSeekV18=true;streamName=false;installTransport();
        String version=c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName,date=HomeCalendarDates.key(Calendar.getInstance());
        JSONObject profile=new JSONObject().put("nightMode","true".equals(args.getString("night"))).put("decoration","blue").put("bookFont","xiaolai")
            .put("apiEnabled",true).put("apiKey","qa-not-a-real-key").put("apiEndpoint","https://api.deepseek.com/chat/completions")
            .put("apiModel","deepseek-flash").put("apiModelMode","fast").put("foodJsonSchema",false).put("foodAutoAnalyze",true).put("foodPhotoConsent",true)
            .put("aiAnalysisEnabled",false).put("aiAutoAnalysis",false).put("autoCloudSync",false).put("seenAnnouncementVersion",version);
        prefs.edit().remove("food_processing_preview").putString(NutritionStore.DAYS,"{}").putString(NutritionStore.PROFILE,profile.toString()).commit();
        MainActivity screen=(MainActivity)startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));settleV15();
        Object probe=invoke(screen,"detectApiConfiguration",new Class[]{String.class,String.class,String.class},"qa-not-a-real-key","https://api.deepseek.com/chat/completions","deepseek-flash");
        if(!Boolean.TRUE.equals(ownField(probe,"valid")))throw new Exception("Fake official models list not accepted");
        JSONObject names=(JSONObject)ownField(probe,"displayNames"),inputs=(JSONObject)ownField(probe,"imageInputs");JSONArray models=(JSONArray)ownField(probe,"models");
        if(!"DeepSeek-V4.1-Flash".equals(names.optString("deepseek-flash"))||!"image".equals(inputs.optString("deepseek-flash"))||!"no_image".equals(inputs.optString("deepseek-v4-pro")))throw new Exception("GET/models dropped provider name or input declarations");
        uiAssert(()->{JSONObject current=(JSONObject)field(screen,"profile");current.put("apiModels",models).put("apiModelDisplayNames",names).put("apiModelDisplayNamesEndpoint","https://api.deepseek.com/chat/completions").put("apiModelImageInputs",inputs).put("apiModelImageInputsEndpoint","https://api.deepseek.com/chat/completions");invoke(screen,"saveProfile",new Class[]{});invoke(screen,"configureApi",new Class[]{});});waitForIdleSync();Thread.sleep(250);
        uiAssert(()->{View config=dialogRootV15("api_model_select");android.widget.TextView selected=(android.widget.TextView)config.findViewWithTag("api_model_select");String value=selected.getText().toString();if(!value.contains("DeepSeek-V4.1-Flash")||!value.contains("API ID: deepseek-flash"))throw new Exception("Config does not show name separate from API ID");selected.performClick();});waitForIdleSync();Thread.sleep(250);
        uiAssert(()->{View dialog=dialogRootV15("api_model_choices");android.widget.TextView row=(android.widget.TextView)dialog.findViewWithTag("api_model_choice_deepseek-flash");if(row==null||!row.getText().toString().contains("DeepSeek-V4.1-Flash")||!row.getText().toString().contains("API ID: deepseek-flash"))throw new Exception("Picker still displays raw ID as version name");if(row.getLayout()==null||row.getLayout().getHeight()+row.getCompoundPaddingTop()+row.getCompoundPaddingBottom()>row.getHeight()+1)throw new Exception("Long model name/ID/capability clipped by fixed row height");});screenshot("beta18-model-names"+args.getString("night","false"));
        uiAssert(()->dialogRootV15("api_model_choices").findViewWithTag("api_model_choice_deepseek-flash").performClick());waitForIdleSync();click("取消");
        uiAssert(()->invoke(screen,"showStartupAnnouncement",new Class[]{String.class},version));waitForIdleSync();
        uiAssert(()->{String text=visibleText(dialogRootV15("update_announcement"));if(!text.contains(version)||!text.contains("接口兼容")||text.contains("0.6.0-beta.7"))throw new Exception("Startup announcement still substitutes old beta notes");});screenshot("beta18-current-announcement"+args.getString("night","false"));click("我已了解");
        uiAssert(()->invoke(screen,"showNoUpdateDialog",new Class[]{String.class,String.class},"0.5.26","QA_OLD_REMOTE_NOTES_DO_NOT_SHOW"));waitForIdleSync();
        uiAssert(()->{String text=visibleText(dialogRootV15("current_release_notes"));if(!text.contains(version)||text.contains("QA_OLD_REMOTE_NOTES_DO_NOT_SHOW"))throw new Exception("Old server notes still replace current Beta features");});click("好");
        uiAssert(()->invoke(screen,"renderUpdateLogPage",new Class[]{}));waitForIdleSync();
        uiAssert(()->{String notes=(String)invoke(screen,"recentReleaseNotesText",new Class[]{int.class},100);if(!notes.startsWith(version+"\n")||!notes.contains("0.6.0-beta.17")||!notes.contains("0.6.0-beta.8"))throw new Exception("Update log still missing recent betas");invoke(screen,"showTab",new Class[]{int.class,boolean.class},0,false);});settleV15();
        String source="draft_qa_deepseek_"+UUID.randomUUID()+".jpg";layoutSource(c,source);
        if(!FoodAnalysisJobService.enqueueDraft(c,date,"lunch",source,false))throw new Exception("Unable to stage official protocol draft");
        uiAssert(()->invoke(screen,"showFoodProcessing",new Class[]{String.class,String.class,List.class},date,"lunch",Collections.singletonList(source)));
        JSONObject item=null;long deadline=android.os.SystemClock.elapsedRealtime()+120000;
        while(android.os.SystemClock.elapsedRealtime()<deadline){item=FoodDraftStore.item(c,source);if(item!=null&&"completed".equals(item.optString("analysisStatus")))break;if(item!=null&&"needs_retry".equals(item.optString("analysisStatus")))throw new Exception("Official protocol fixture rejected: "+item.optString("error"));Thread.sleep(180);}
        if(item==null||!"completed".equals(item.optString("analysisStatus"))||item.optString("cutoutFile").isEmpty()||item.getJSONObject("nutrients").getInt("caloriesKcal")!=676)throw new Exception("Official JSON compatibility did not finish real cutout + validated nutrition");
        if(apiCalls.get()!=1||!"deepseek-flash".equals(item.optString("providerModel"))||!"{}".equals(prefs.getString(NutritionStore.DAYS,"{}")))throw new Exception("Protocol fix changed model ID, duplicated paid work, or auto-confirmed");
        screenshot("beta18-deepseek-json-nutrition"+args.getString("night","false"));clickDescription("确定");waitForIdleSync();
        JSONObject meal=NutritionStore.meal(NutritionStore.read(prefs,NutritionStore.DAYS),date,"lunch");if(NutritionStore.forSource(meal,source).length()!=1||FoodDraftStore.item(c,source)!=null||apiCalls.get()!=1)throw new Exception("Explicit confirmation did not preserve exact validated food");
        Bundle result=new Bundle();result.putString("stream","PASS: official-host fake models retain V4.1-Flash name separate from deepseek-flash API ID, full multiline picker, current beta announcement/log and old-server-note isolation; actual ARM64 local cutout, official JSON Output + explicit fast-mode wire contract, validated same-request nutrition in draft only, one request and explicit confirm. Fake transport, not a live DeepSeek accuracy/availability test.\n");finish(Activity.RESULT_OK,result);
    }
    private void opaqueFrameV19(MainActivity screen)throws Exception {
        ViewGroup host=(ViewGroup)field(screen,"content");
        if(host.getAlpha()!=1f||host.getChildCount()==0||host.getScaleX()!=1f||host.getScaleY()!=1f)throw new Exception("Navigation exposed a blank/scaled content container");
        if(host.getChildAt(0).getWidth()==0||host.getChildAt(0).getHeight()==0)return; // Before layout is not a displayed frame.
        Bitmap bitmap=Bitmap.createBitmap(180,360,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);canvas.scale(180f/host.getWidth(),360f/host.getHeight());host.draw(canvas);
        int opaque=0,total=0;for(int y=30;y<330;y+=12)for(int x=12;x<168;x+=12){total++;if(Color.alpha(bitmap.getPixel(x,y))>64)opaque++;}bitmap.recycle();
        if(opaque<total*.04f)throw new Exception("Transition frame has no visible page content");
    }
    private void revisionV19(Context c,android.content.SharedPreferences prefs)throws Exception {
        FoodAnalysisJobService.cancelAll(c);FoodDraftStore.discard(c,FoodDraftStore.pendingSources(c));
        String date=HomeCalendarDates.key(Calendar.getInstance()),before=unifiedDays(c,date).toString();boolean night="true".equals(args.getString("night"));
        JSONObject profile=new JSONObject().put("nightMode",night).put("decoration","yellow").put("bookFont","xiaolai").put("foodStickerBorder","none")
            .put("foodAutoAnalyze",false).put("foodPhotoConsent",false).put("autoCloudSync",false).put("seenAnnouncementVersion",c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName);
        prefs.edit().putString(NutritionStore.DAYS,before).putString(NutritionStore.PROFILE,profile.toString()).commit();
        MainActivity screen=(MainActivity)startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));settleV15();
        uiAssert(()->{invoke(screen,"showTab",new Class[]{int.class,boolean.class},3,false);invoke(screen,"openPreferencesPage",new Class[]{});});
        for(int i=0;i<12;i++){Thread.sleep(25);uiAssert(()->opaqueFrameV19(screen));}settleV15();
        uiAssert(()->{invoke(screen,"openInterfaceLayoutPage",new Class[]{});opaqueFrameV19(screen);});Thread.sleep(110);
        uiAssert(()->{opaqueFrameV19(screen);invoke(screen,"closeInterfaceLayoutPage",new Class[]{});});
        for(int i=0;i<10;i++){Thread.sleep(25);uiAssert(()->opaqueFrameV19(screen));}settleV15();
        uiAssert(()->{if(!Boolean.TRUE.equals(field(screen,"inPreferencesPage"))||Boolean.TRUE.equals(field(screen,"inInterfaceLayoutPage")))throw new Exception("Interrupted page transition committed stale navigation");invoke(screen,"renderInterfaceLayoutPage",new Class[]{});View decor=screen.getWindow().getDecorView();assertSettingPresence(decor,"个性字体",false);assertSettingPresence(decor,"笔记字体",false);invoke(screen,"renderThemeSettingsPage",new Class[]{});});settleV15();
        uiAssert(()->{View decor=screen.getWindow().getDecorView();assertSettingPresence(decor,"个性字体",true);String all=visibleText(decor);if(all.indexOf("柠檬黄色")>all.indexOf("清新绿色"))throw new Exception("Yellow theme is not first");UiTokens.configure("yellow");if(UiTokens.paper(false)!=0xfffaf7f0||UiTokens.card(false)!=0xfffffdf7||UiTokens.ink(false)!=0xff573f2f||UiTokens.action(false)!=0xff2b231d)throw new Exception("Yellow baseline colors changed");});
        HashSet<Integer> papers=new HashSet<>(),cards=new HashSet<>(),inks=new HashSet<>(),actions=new HashSet<>();
        for(String mode:new String[]{"yellow","green","red","blue","night"}){
            uiAssert(()->invoke(screen,"applyThemeChoice",new Class[]{String.class},mode));
            for(int i=0;i<7;i++){Thread.sleep(25);uiAssert(()->opaqueFrameV19(screen));}settleV15();
            uiAssert(()->{papers.add(UiTokens.paper(night));cards.add(UiTokens.card(night));inks.add(UiTokens.ink(night));actions.add(UiTokens.action(night));if(field(screen,"themeModeTransitionOverlay")!=null)throw new Exception("Theme snapshot leaked after transition");assertRenderPure(screen,prefs,before);});
        }
        if(papers.size()!=5||cards.size()!=5||inks.size()!=5||actions.size()!=5)throw new Exception("Themes still change only navigation accent");
        screenshot("beta19-themes-"+night);uiAssert(()->invoke(screen,"chooseFoodStickerBorder",new Class[]{}));waitForIdleSync();
        if(node("无边框")!=null)throw new Exception("Border picker retains removed no-border option");sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);waitForIdleSync();
        if(!"dashed".equals(FoodStickerRenderer.style(c))||!"none".equals(NutritionStore.read(prefs,NutritionStore.PROFILE).optString("foodStickerBorder")))throw new Exception("Old no-border preference was destructively migrated instead of display fallback");
        uiAssert(()->{invoke(screen,"renderSmartAssistantPage",new Class[]{});View decor=screen.getWindow().getDecorView();ViewGroup food=(ViewGroup)setting(decor,"餐图识别"),diet=(ViewGroup)setting(decor,"营养师");if(!"dining".equals(ownField(food.getChildAt(0),"kind"))||!"doll".equals(ownField(diet.getChildAt(0),"kind")))throw new Exception("Assistant icons not differentiated as meal/mascot");});screenshot("beta19-assistant-doll-"+night);
        uiAssert(()->invoke(screen,"renderBookDetail",new Class[]{String.class,boolean.class},date,false));settleV15();
        uiAssert(()->{View paper=screen.getWindow().getDecorView().findViewWithTag("book_detail_notebook_page");ViewGroup page=(ViewGroup)paper.getParent();float center=screenRect(page).top+page.getPaddingTop()+(page.getHeight()-page.getPaddingTop()-page.getPaddingBottom())/2f;if(Math.abs(screenRect(paper).exactCenterY()-center)>2)throw new Exception("Notebook paper is not vertically centered in safe content region");assertRenderPure(screen,prefs,before);});screenshot("beta19-centered-notebook-"+night);
        Bundle result=new Bundle();result.putString("stream","PASS: opaque intermediate navigation/theme frames, interrupted return and no stale overlays, distinct whole-app palettes with yellow baseline preserved/first, personalized-font single entry, four border choices with nondestructive legacy fallback, meal/doll glyphs, centered safe notebook and unchanged diary.\n");finish(Activity.RESULT_OK,result);
    }
    private void liveDownloadsV19(Context c,android.content.SharedPreferences prefs)throws Exception {
        FoodAnalysisJobService.cancelAll(c);JSONObject p=new JSONObject().put("decoration","yellow").put("nightMode",false).put("autoCloudSync",false).put("seenAnnouncementVersion",c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName);prefs.edit().putString(NutritionStore.PROFILE,p.toString()).putString(NutritionStore.DAYS,"{}").commit();
        MainActivity screen=(MainActivity)startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));settleV15();
        JSONObject update=(JSONObject)invoke(screen,"fetchOpenListUpdateJson",new Class[]{});if(update.optString("versionName").isEmpty())throw new Exception("Authenticated update manifest unavailable");
        final java.util.concurrent.CountDownLatch complete=new java.util.concurrent.CountDownLatch(1);
        uiAssert(()->invoke(screen,"downloadBookFont",new Class[]{String.class,String.class,Runnable.class},"zcool","站酷快乐体",(Runnable)()->complete.countDown()));
        if(!complete.await(90,java.util.concurrent.TimeUnit.SECONDS))throw new Exception("Live authenticated font download did not complete");
        uiAssert(()->{if(!Boolean.TRUE.equals(invoke(screen,"downloadedBookFontReady",new Class[]{String.class},"zcool")))throw new Exception("Downloaded font is not loadable on Android");});
        Bundle result=new Bundle();result.putString("stream","PASS: real passwordless distribution login, account-relative update manifest, actual Android font download and Typeface validation; no server mutations and no token/signed link logged.\n");finish(Activity.RESULT_OK,result);
    }
    private void tapPointV21(float x,float y)throws Exception {
        long down=android.os.SystemClock.uptimeMillis();pointerV15(down,MotionEvent.ACTION_DOWN,x,y);pointerV15(down,MotionEvent.ACTION_UP,x,y);waitForIdleSync();
    }
    private void noFoodDialogV21(MainActivity screen)throws Exception {
        for(View root:android.view.inspector.WindowInspector.getGlobalWindowViews())if(root!=screen.getWindow().getDecorView()&&root.isShown()&&root.findViewWithTag("food_detail")!=null)throw new Exception("Non-image tap unexpectedly opened nutrition details");
    }
    private void drawableStableV21(android.graphics.drawable.Drawable drawable,String name)throws Exception {
        drawable.setBounds(0,0,240,360);Bitmap first=Bitmap.createBitmap(240,360,Bitmap.Config.ARGB_8888),next=Bitmap.createBitmap(240,360,Bitmap.Config.ARGB_8888);
        try{
            drawable.draw(new Canvas(first));if(Color.alpha(first.getPixel(120,180))!=255||Color.alpha(first.getPixel(0,0))>20)throw new Exception("Drawable lost opaque interior/rounded corner: "+name);
            for(int i=0;i<4;i++){next.eraseColor(Color.TRANSPARENT);drawable.draw(new Canvas(next));if(!first.sameAs(next))throw new Exception("Snapshot/repeated draw changes color: "+name+" frame "+i);}
            drawable.setAlpha(96);next.eraseColor(Color.TRANSPARENT);drawable.draw(new Canvas(next));if(Math.abs(Color.alpha(next.getPixel(120,180))-96)>1)throw new Exception("Drawable alpha does not compose once: "+name);
            drawable.setAlpha(255);next.eraseColor(Color.TRANSPARENT);drawable.draw(new Canvas(next));if(!first.sameAs(next))throw new Exception("Drawable alpha restoration changes cover/paper: "+name);
        }finally{first.recycle();next.recycle();}
    }
    private void bookSurfaceV21(MainActivity screen,Context c,boolean night)throws Exception {
        uiAssert(()->{
            Class<?> paperType=Class.forName("com.eatrecord.app.MainActivity$NotebookPageDrawable"),coverType=Class.forName("com.eatrecord.app.MainActivity$BookCoverDrawable");
            java.lang.reflect.Constructor<?> paperCtor=paperType.getDeclaredConstructor(MainActivity.class,String.class),coverCtor=coverType.getDeclaredConstructor(MainActivity.class,String.class,int[].class,String.class);paperCtor.setAccessible(true);coverCtor.setAccessible(true);
            JSONObject profile=(JSONObject)field(screen,"profile");String previous=profile.optString("decoration","yellow"),oldPaper=profile.optString("notebookPageStyle","cream");
            try{
                for(String theme:new String[]{"yellow","green","red","blue","night"}){
                    profile.put("decoration",theme);invoke(screen,"applyDecoration",new Class[]{});
                    int[] colors=(int[])invoke(screen,"bookPalette",new Class[]{String.class,int.class},HomeCalendarDates.key(Calendar.getInstance()),0xff87582e);
                    for(String style:new String[]{"food","strawberry","bear","ocean","checker","cat","flower","nightstar","clear","rabbit","puppy","duck","sheep","business","pastel","classic","duotone","tritone"})drawableStableV21((android.graphics.drawable.Drawable)coverCtor.newInstance(screen,"2026-10-21",colors,style),theme+" cover "+style);
                    for(String style:new String[]{"cream","fresh","pink","grid","plain","recipe","dotted","memo","letter","fruit","sunset","blueprint"}){
                        profile.put("notebookPageStyle",style);android.graphics.drawable.Drawable drawable=(android.graphics.drawable.Drawable)paperCtor.newInstance(screen,style);drawableStableV21(drawable,theme+" paper "+style);
                        int[] palette=(int[])invoke(screen,"notebookPaperPalette",new Class[]{String.class},style);
                        View paper=new View(screen);paper.setBackground(drawable);paper.layout(0,0,600,900);NotebookTurnRenderer turn=new NotebookTurnRenderer(paper,new RectF(0,0,600,900),palette[0],palette[1]);
                        try{
                            if(((Number)ownField(turn,"top")).intValue()!=palette[0]||((Number)ownField(turn,"bottom")).intValue()!=palette[1])throw new Exception("Paper back diverges from selected front palette");
                            ((Bitmap)ownField(turn,"front")).eraseColor(Color.TRANSPARENT);Bitmap back=Bitmap.createBitmap(600,900,Bitmap.Config.ARGB_8888);
                            try{turn.draw(new Canvas(back),.15f,true);float lift=12*c.getResources().getDisplayMetrics().density*(float)Math.sin(Math.PI*.15);
                                if(Color.alpha(back.getPixel(421,Math.min(899,(int)lift+1)))>40)throw new Exception("Folded paper outer corner is square: "+theme+"/"+style);
                                if(Color.alpha(back.getPixel(465,450))<250)throw new Exception("Folded paper back is not opaque: "+theme+"/"+style);
                            }finally{back.recycle();}
                        }finally{turn.release();}
                    }
                }
            }finally{profile.put("decoration",previous).put("notebookPageStyle",oldPaper);invoke(screen,"applyDecoration",new Class[]{});}
        });
    }
    private void foodTapAndDetailV21(MainActivity screen,Context c,android.content.SharedPreferences prefs,String before,String name)throws Exception {
        TodayDayPager pager=pagerV15(screen);View current=pager.currentPage(),row=tagged(current,"day_food_").get(0),image=tagged(row,"day_entry_image").get(0),label=row.findViewWithTag("day_entry_label");
        long deadline=android.os.SystemClock.elapsedRealtime()+6000;while(ownField(image,"bitmap")==null&&android.os.SystemClock.elapsedRealtime()<deadline){Thread.sleep(60);waitForIdleSync();}
        if(ownField(image,"bitmap")==null)throw new Exception("Food image did not finish loading for hit-test QA");
        uiAssert(()->{if(row.isClickable()||label.isClickable())throw new Exception("Food row or dish name still opens details");});
        Rect labelBounds=screenRect(label),imageBounds=screenRect(image),rowBounds=screenRect(row);
        if(label.getGlobalVisibleRect(new Rect())){tapPointV21(labelBounds.exactCenterX(),labelBounds.exactCenterY());uiAssert(()->noFoodDialogV21(screen));}
        tapPointV21(rowBounds.left+PaperComponents.dp(c,5),Math.max(rowBounds.top+2,imageBounds.exactCenterY()));uiAssert(()->noFoodDialogV21(screen));
        final float[][] hit={null},miss={null};
        uiAssert(()->{TodayPaperView.HitTestMedia test=(TodayPaperView.HitTestMedia)image;Rect visible=new Rect();image.getGlobalVisibleRect(visible);
            for(int y=2;y<image.getHeight()-2;y+=3)for(int x=2;x<image.getWidth()-2;x+=3){if(!visible.contains(imageBounds.left+x,imageBounds.top+y))continue;boolean opaque=test.containsMediaPoint(x,y);if(opaque&&hit[0]==null)hit[0]=new float[]{imageBounds.left+x,imageBounds.top+y};if(!opaque&&miss[0]==null)miss[0]=new float[]{imageBounds.left+x,imageBounds.top+y};}
            if(hit[0]==null||miss[0]==null)throw new Exception("Hit-test fixture lacks visible subject or transparent padding");
        });
        tapPointV21(miss[0][0],miss[0][1]);uiAssert(()->noFoodDialogV21(screen));
        tapPointV21(hit[0][0],hit[0][1]);settleV15();
        uiAssert(()->{View root=dialogRootV15("food_detail"),scroll=root.findViewWithTag("food_detail_scroll"),end=root.findViewWithTag("food_detail_macros_end"),tip=root.findViewWithTag("food_detail_tip");
            if(scroll==null||end==null||tip==null||!(scroll instanceof android.widget.ScrollView))throw new Exception("Compact food detail lacks macro/scroll markers");
            Rect viewport=screenRect(scroll),macros=screenRect(end);if(macros.bottom>viewport.bottom+2)throw new Exception("Initial food window clips complete second macro row");
            if(screenRect(tip).top<viewport.bottom-PaperComponents.dp(c,2))throw new Exception("Tips are still in the initial full-height food window");
            if(((android.widget.ScrollView)scroll).getScrollY()!=0)throw new Exception("Food detail opens pre-scrolled");assertRenderPure(screen,prefs,before);
        });screenshot(name+"-compact-food-first-view");
        uiAssert(()->((android.widget.ScrollView)dialogRootV15("food_detail").findViewWithTag("food_detail_scroll")).fullScroll(View.FOCUS_DOWN));settleV15();
        uiAssert(()->{View root=dialogRootV15("food_detail"),more=root.findViewWithTag("food_detail_more");if(!more.getGlobalVisibleRect(new Rect())||((android.widget.ScrollView)root.findViewWithTag("food_detail_scroll")).getScrollY()==0)throw new Exception("Food detail tips/actions are not scroll-reachable");more.performClick();});settleV15();
        String actions=dialogTextsV20(screen);if(!actions.contains("识别信息")||!actions.contains("重新抠图")||actions.contains("重新识别\n"))throw new Exception("Food More labels not split/renamed: "+actions);
        tapDialogV20(screen,"识别信息");settleV15();if(!dialogTextsV20(screen).contains("取消"))throw new Exception("Information recognition lost explicit fee confirmation");tapDialogV20(screen,"取消");settleV15();
        uiAssert(()->assertRenderPure(screen,prefs,before));
    }
    private void mediaEditBroadcastV21(MainActivity screen,Context c,android.content.SharedPreferences prefs,String before)throws Exception {
        uiAssert(()->mainFocusV21(screen));final TodayDayPager pager=pagerV15(screen);final String selected=HomeCalendarDates.key(pager.currentDate());
        final View image=tagged(tagged(pager.currentPage(),"day_food_").get(0),"day_entry_image").get(0),wrapper=(View)image.getParent();final Object action=wrapper.getTag();
        uiAssert(()->{if(!wrapper.performLongClick()||!Boolean.TRUE.equals(ownField(action,"editing"))||!((Collection<?>)field(screen,"editingMediaItems")).contains(wrapper))throw new Exception("Real food wrapper long-click did not enter media edit mode");
            for(String field:new String[]{"closeButton","rotateButton"})if(((View)ownField(action,field)).getVisibility()!=View.VISIBLE)throw new Exception("Media long-press lacks rotate/delete controls");
        });
        c.sendBroadcast(new Intent(FoodAnalysisJobService.CHANGED).setPackage(c.getPackageName()).putExtra("date",selected));
        long deadline=android.os.SystemClock.elapsedRealtime()+2500;while(!Boolean.TRUE.equals(field(screen,"homeFoodRefreshPending"))&&android.os.SystemClock.elapsedRealtime()<deadline){waitForIdleSync();Thread.sleep(40);}
        Thread.sleep(220);uiAssert(()->{if(pagerV15(screen)!=pager||!Boolean.TRUE.equals(field(screen,"homeFoodRefreshPending"))||!((Collection<?>)field(screen,"editingMediaItems")).contains(wrapper)||!Boolean.TRUE.equals(ownField(action,"editing")))throw new Exception("Nutrition broadcast destroyed active rotate/delete editing controls after long-press");
            for(String field:new String[]{"closeButton","rotateButton"})if(((View)ownField(action,field)).getVisibility()!=View.VISIBLE)throw new Exception("Deferred food refresh hid media editing controls");assertRenderPure(screen,prefs,before);invoke(screen,"exitMediaEditMode",new Class[]{boolean.class},false);
        });
        deadline=android.os.SystemClock.elapsedRealtime()+3500;while(Boolean.TRUE.equals(field(screen,"homeFoodRefreshPending"))&&android.os.SystemClock.elapsedRealtime()<deadline){waitForIdleSync();Thread.sleep(50);}settleV15();
        uiAssert(()->{if(Boolean.TRUE.equals(field(screen,"homeFoodRefreshPending"))||!((Collection<?>)field(screen,"editingMediaItems")).isEmpty()||!selected.equals(HomeCalendarDates.key(pagerV15(screen).currentDate())))throw new Exception("Food refresh did not flush cleanly after leaving media edit mode");assertRenderPure(screen,prefs,before);});
    }
    private void mealPromptV21(MainActivity screen,Context c,android.content.SharedPreferences prefs,String before,String today)throws Exception {
        uiAssert(()->invoke(screen,"manageDayRecords",new Class[]{String.class},today));settleV15();if(!dialogTextsV20(screen).contains("编辑夜宵记录"))throw new Exception("Today management omits night meal");
        // Exercise the real choice callback and the custom footer, not a synthetic BACK that
        // some window/IME combinations consume without closing the current application dialog.
        tapDialogV20(screen,"编辑夜宵记录");settleV15();tapDialogV20(screen,"完成");settleV15();uiAssert(()->mainFocusV21(screen));
        final AlertDialog[] dialog={null};final String[] prompt={null};
        uiAssert(()->dialog[0]=(AlertDialog)invoke(screen,"showMealRecordEditor",new Class[]{String.class,String.class},today,"lunch"));settleV15();
        uiAssert(()->{View root=dialog[0].getWindow().getDecorView();android.widget.TextView text=(android.widget.TextView)root.findViewWithTag("meal_record_text");View add=root.findViewWithTag("meal_record_add_image"),done=root.findViewWithTag("meal_record_done");prompt[0]=text.getText().toString();
            prompt[0]=text.getHint().toString();
            if(!(text instanceof android.widget.EditText)||!prompt[0].startsWith("QA两次缺席")||!text.isClickable()||visibleText(root).contains("还没有餐食图片"))throw new Exception("Empty meal does not use inline configured absence prompt");
            int gap=screenRect(done).top-screenRect(add).bottom;if(gap<0||gap>PaperComponents.dp(c,18))throw new Exception("Meal image action/Done gap is still too wide: "+gap);
            text.performClick();
        });settleV15();
        uiAssert(()->{View root=dialog[0].getWindow().getDecorView();android.widget.EditText input=(android.widget.EditText)root.findViewWithTag("meal_record_text");if(!dialog[0].isShowing()||!prompt[0].contentEquals(input.getHint())||root.findViewWithTag("manual_food_text")!=null)throw new Exception("Inline input opened a second editor");assertRenderPure(screen,prefs,before);dialog[0].dismiss();});settleV15();
        uiAssert(()->dialog[0]=(AlertDialog)invoke(screen,"showMealRecordEditor",new Class[]{String.class,String.class},today,"snack"));settleV15();
        uiAssert(()->{String night=((android.widget.TextView)dialog[0].getWindow().getDecorView().findViewWithTag("meal_record_text")).getHint().toString();if(night.startsWith("QA两次缺席"))throw new Exception("Night prompt incorrectly uses daytime skip pool");dialog[0].dismiss();});settleV15();
    }
    private void daySlidesV21(TodayDayPager pager,int sign)throws Exception {
        float offset=offsetV15(pager),stride=pager.getWidth()+PaperComponents.dp(pager.getContext(),24);View[] pages=(View[])ownField(pager,"pages");
        if(sign*offset<=PaperComponents.dp(pager.getContext(),6)||Math.abs(offset)>=stride)throw new Exception("Day carousel does not track an intermediate frame: offset="+offset+", width="+pager.getWidth()+", stride="+stride+", attached="+pager.isAttachedToWindow()+", identity="+System.identityHashCode(pager)+", windowFocus="+pager.hasWindowFocus()+", dragging="+ownField(pager,"dragging")+", cancelled="+ownField(pager,"cancelled")+", pointer="+ownField(pager,"pointer")+", down="+ownField(pager,"downX")+","+ownField(pager,"downY")+", dragOrigin="+ownField(pager,"dragOrigin")+", date="+HomeCalendarDates.key(pager.currentDate())+", windows="+windowsV21());
        for(int i=0;i<3;i++)if(Math.abs(pages[i].getTranslationX()-((i-1)*stride+offset))>1.1f||pages[i].getAlpha()!=1)throw new Exception("Day carousel pages do not share one opaque gapped displacement");
        float gap=pages[1].getTranslationX()-pages[0].getTranslationX()-pager.getWidth();if(Math.abs(gap-PaperComponents.dp(pager.getContext(),24))>1)throw new Exception("Adjacent food papers still touch: "+gap);
    }
    private String windowsV21()throws Exception {
        StringBuilder out=new StringBuilder();for(View root:android.view.inspector.WindowInspector.getGlobalWindowViews()){
            int[] location=new int[2];root.getLocationOnScreen(location);out.append(root.getClass().getSimpleName()).append(" shown=").append(root.isShown()).append(" focus=").append(root.hasWindowFocus()).append(" at=").append(location[0]).append(',').append(location[1]).append(" size=").append(root.getWidth()).append('x').append(root.getHeight()).append(" main=").append(gestureScreenV20!=null&&root==gestureScreenV20.getWindow().getDecorView()).append(";");
        }
        if(gestureScreenV20!=null)out.append(" tab=").append(field(gestureScreenV20,"currentTab")).append(" touch=").append(field(gestureScreenV20,"windowTouchActive")).append(" peerTracking=").append(field(gestureScreenV20,"peerTracking")).append(" peerDragging=").append(field(gestureScreenV20,"peerDragging")).append(" refresh=").append(field(gestureScreenV20,"homeFoodRefreshPending")).append(" activeGlass=").append(field(gestureScreenV20,"activeGlassDialogCount")).append(" editing=").append(((Collection<?>)field(gestureScreenV20,"editingMediaItems")).size()).append(" livePagerIdentity=").append(System.identityHashCode(gestureScreenV20.getWindow().getDecorView().findViewWithTag("today_day_pager")));
        return out.toString();
    }
    private void mainFocusV21(MainActivity screen)throws Exception {
        View main=screen.getWindow().getDecorView();if(!main.hasWindowFocus()||((Integer)field(screen,"activeGlassDialogCount"))!=0)throw new Exception("Peer phase starts behind an active dialog/IME: "+windowsV21());
        for(View root:android.view.inspector.WindowInspector.getGlobalWindowViews())if(root!=main&&root.isShown()&&root.getGlobalVisibleRect(new Rect()))throw new Exception("Peer phase retains a shown dialog window: "+windowsV21());
    }
    private void peerMiddleV21(MainActivity screen)throws Exception {
        throw new Exception("Retired PageCrossfade peer-navigation contract: use revision-v28 navigation or revision-v33, not peerMiddleV21");
    }
    private void rootSwipeV21(MainActivity screen,int expected,boolean forward,boolean cancel,String name)throws Exception {
        throw new Exception("Retired PageCrossfade peer-navigation contract: use revision-v28 navigation or revision-v33, not rootSwipeV21");
    }
    private void broadcastDuringDayV21(MainActivity screen,Context c,android.content.SharedPreferences prefs,String before)throws Exception {
        final TodayDayPager original=pagerV15(screen);final String selected=HomeCalendarDates.key(original.currentDate());Rect area=screenRect(original);
        float x=area.left+area.width()*.18f,y=area.top+Math.min(area.height()*.3f,PaperComponents.dp(c,95)),end=x+area.width()*.38f;
        long down=android.os.SystemClock.uptimeMillis();pointerV15(down,MotionEvent.ACTION_DOWN,x,y);movesV15(down,x,y,end,y,8);
        screenshotNowV15(args.getString("name","beta21")+"-broadcast-day-before-assert");
        final float[] offset={0};uiAssert(()->{if(!original.isInteracting()||!Boolean.TRUE.equals(field(screen,"windowTouchActive")))throw new Exception("Broadcast test has no active day gesture: offset="+offsetV15(original)+", attached="+original.isAttachedToWindow()+", dragging="+ownField(original,"dragging")+", cancelled="+ownField(original,"cancelled")+", "+windowsV21());offset[0]=offsetV15(original);});
        c.sendBroadcast(new Intent(FoodAnalysisJobService.CHANGED).setPackage(c.getPackageName()).putExtra("date",selected));
        long deadline=android.os.SystemClock.elapsedRealtime()+2500;while(!Boolean.TRUE.equals(field(screen,"homeFoodRefreshPending"))&&android.os.SystemClock.elapsedRealtime()<deadline){waitForIdleSync();Thread.sleep(40);}
        Thread.sleep(180);uiAssert(()->{if(pagerV15(screen)!=original||Math.abs(offsetV15(original)-offset[0])>.5f||!Boolean.TRUE.equals(field(screen,"homeFoodRefreshPending")))throw new Exception("Nutrition broadcast rebuilt/snapped an actively dragged day paper");assertRenderPure(screen,prefs,before);});
        pointerV15(down,MotionEvent.ACTION_CANCEL,end,y);settleV15();deadline=android.os.SystemClock.elapsedRealtime()+2500;
        while(Boolean.TRUE.equals(field(screen,"homeFoodRefreshPending"))&&android.os.SystemClock.elapsedRealtime()<deadline){waitForIdleSync();Thread.sleep(50);}
        uiAssert(()->{if(Boolean.TRUE.equals(field(screen,"homeFoodRefreshPending"))||!selected.equals(HomeCalendarDates.key(pagerV15(screen).currentDate()))||!selected.equals(invoke(screen,"homeDate",new Class[]{})))throw new Exception("Deferred food refresh failed to settle on the cancelled original date");assertRenderPure(screen,prefs,before);});
    }
    private void peerPointersV21(MainActivity screen,long down,int action,float... points)throws Exception {
        int count=points.length/2;MotionEvent.PointerProperties[] properties=new MotionEvent.PointerProperties[count];MotionEvent.PointerCoords[] coordinates=new MotionEvent.PointerCoords[count];
        for(int i=0;i<count;i++){properties[i]=new MotionEvent.PointerProperties();properties[i].id=i;properties[i].toolType=MotionEvent.TOOL_TYPE_FINGER;coordinates[i]=new MotionEvent.PointerCoords();coordinates[i].x=points[i*2];coordinates[i].y=points[i*2+1];coordinates[i].pressure=1;coordinates[i].size=1;}
        MotionEvent event=MotionEvent.obtain(down,android.os.SystemClock.uptimeMillis(),action,count,properties,coordinates,0,0,1,1,0,0,InputDevice.SOURCE_TOUCHSCREEN,0);
        try{uiAssert(()->{View decor=screen.getWindow().getDecorView();int[] location=new int[2];decor.getLocationOnScreen(location);MotionEvent local=MotionEvent.obtain(event);local.offsetLocation(-location[0],-location[1]);try{screen.dispatchTouchEvent(local);}finally{local.recycle();}});}finally{event.recycle();}localPointerFallbacksV20++;
    }
    private void peerCancellationFallbackV21(MainActivity screen,android.content.SharedPreferences prefs,String before)throws Exception {
        throw new Exception("Retired PageCrossfade peer-navigation contract: use revision-v28 navigation or revision-v33, not peerCancellationFallbackV21");
    }
    private void peerAndDayV21(MainActivity screen,Context c,android.content.SharedPreferences prefs,String before,String name)throws Exception {
        final String today=HomeCalendarDates.key(Calendar.getInstance());uiAssert(()->{setMainField(screen,"homeDateKey",today);setMainField(screen,"homeCalendarExpanded",false);invoke(screen,"showTab",new Class[]{int.class,boolean.class},0,false);});settleV15();
        broadcastDuringDayV21(screen,c,prefs,before);
        TodayDayPager pager=pagerV15(screen);Rect area=screenRect(pager);float x=area.left+area.width()*.18f,y=area.top+Math.min(area.height()*.3f,PaperComponents.dp(c,95)),end=x+area.width()*.65f;
        long down=android.os.SystemClock.uptimeMillis();pointerV15(down,MotionEvent.ACTION_DOWN,x,y);movesV15(down,x,y,end,y,10);
        screenshotNowV15(name+"-gapped-day-before-assert");uiAssert(()->{if(((Integer)field(screen,"currentTab"))!=0)throw new Exception("Food-paper swipe switched root tab");if(android.animation.ValueAnimator.areAnimatorsEnabled())daySlidesV21(pager,1);});screenshotNowV15(name+"-gapped-day-mid");
        movesV15(down,end,y,x+area.width()*.08f,y,8);pointerV15(down,MotionEvent.ACTION_CANCEL,x,y);settleV15();
        uiAssert(()->{if(!today.equals(HomeCalendarDates.key(pager.currentDate()))||!today.equals(invoke(screen,"homeDate",new Class[]{})))throw new Exception("Day reversal/cancel left calendar/date on incoming day");});
        down=android.os.SystemClock.uptimeMillis();pointerV15(down,MotionEvent.ACTION_DOWN,x,y);movesV15(down,x,y,end,y,10);
        Calendar previous=Calendar.getInstance();previous.add(Calendar.DATE,-1);String yesterday=HomeCalendarDates.key(previous);
        uiAssert(()->{if(android.animation.ValueAnimator.areAnimatorsEnabled()){
            daySlidesV21(pager,1);if(!yesterday.equals(invoke(screen,"homeDate",new Class[]{}))||!yesterday.equals(HomeCalendarDates.key((Calendar)ownField(calendarV15(screen),"selected"))))throw new Exception("Calendar still lags the incoming food day");
        }});pointerV15(down,MotionEvent.ACTION_UP,end,y);settleV15();
        uiAssert(()->{if(!yesterday.equals(HomeCalendarDates.key(pagerV15(screen).currentDate()))||((Integer)field(screen,"currentTab"))!=0)throw new Exception("Internal day swipe changed tab or failed date commit");assertRenderPure(screen,prefs,before);});
        area=screenRect(pager);x=area.left+area.width()*.82f;end=x-area.width()*.65f;down=android.os.SystemClock.uptimeMillis();pointerV15(down,MotionEvent.ACTION_DOWN,x,y);movesV15(down,x,y,end,y,10);pointerV15(down,MotionEvent.ACTION_UP,end,y);settleV15();
        uiAssert(()->{if(!today.equals(HomeCalendarDates.key(pagerV15(screen).currentDate())))throw new Exception("Day carousel did not return to today");});
        area=screenRect(pager);x=area.left+area.width()*.82f;end=x-area.width()*.65f;down=android.os.SystemClock.uptimeMillis();pointerV15(down,MotionEvent.ACTION_DOWN,x,y);movesV15(down,x,y,end,y,10);pointerV15(down,MotionEvent.ACTION_UP,end,y);settleV15();
        uiAssert(()->{if(!today.equals(HomeCalendarDates.key(pagerV15(screen).currentDate()))||!today.equals(invoke(screen,"homeDate",new Class[]{})))throw new Exception("Day carousel opened a future date");assertRenderPure(screen,prefs,before);});
        // Bottom navigation and finger navigation use the same adjacent-sheet model.
        uiAssert(()->((ViewGroup)field(screen,"navBar")).getChildAt(1).performClick());Thread.sleep(100);uiAssert(()->peerMiddleV21(screen));screenshotNowV15(name+"-nav-today-to-footprints-mid");settleV15();
        uiAssert(()->((ViewGroup)field(screen,"navBar")).getChildAt(2).performClick());Thread.sleep(100);uiAssert(()->peerMiddleV21(screen));screenshotNowV15(name+"-nav-footprints-to-me-mid");settleV15();
        rootSwipeV21(screen,1,false,false,name+"-me-to-footprints");rootSwipeV21(screen,1,true,true,name+"-footprints-cancel");rootSwipeV21(screen,0,false,false,name+"-footprints-to-today");
        rootSwipeV21(screen,1,true,false,name+"-today-external-to-footprints");rootSwipeV21(screen,3,true,false,name+"-footprints-to-me");
        peerCancellationFallbackV21(screen,prefs,before);
        uiAssert(()->assertRenderPure(screen,prefs,before));
    }
    private void revisionV21(Context c,android.content.SharedPreferences prefs)throws Exception {
        throw new Exception("revision-v21 peer navigation is retired; use revision-v28 navigation and revision-v33 all. Date/media/book coverage remains in later explicit phases.");
    }
    private void revisionV20(Context c,android.content.SharedPreferences prefs)throws Exception {
        FoodAnalysisJobService.cancelAll(c);FoodDraftStore.discard(c,FoodDraftStore.pendingSources(c));installTransport();
        String name=args.getString("name","beta20"),today=HomeCalendarDates.key(Calendar.getInstance());
        JSONObject fixture=unifiedDays(c,today);Calendar day=Calendar.getInstance();
        for(int i=1;i<=3;i++){day.add(Calendar.DATE,-1);JSONObject copy=new JSONObject(fixture.getJSONObject(today).toString());
            for(String meal:new String[]{"breakfast","lunch","dinner"})copy.getJSONObject(meal).getJSONArray("foodItems").getJSONObject(0).put("dishName","前"+i+"天的餐食");fixture.put(HomeCalendarDates.key(day),copy);}
        String before=fixture.toString();boolean night="true".equals(args.getString("night"));
        JSONObject profile=new JSONObject().put("nightMode",night).put("decoration","yellow").put("bookFont","xiaolai").put("notebookPageStyle","fresh").put("bookTransitionMode","flip")
            .put("foodAutoAnalyze",false).put("foodPhotoConsent",false).put("autoCloudSync",false).put("seenAnnouncementVersion",c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName);
        prefs.edit().putString(NutritionStore.PROFILE,profile.toString()).putString(NutritionStore.DAYS,before).commit();
        MainActivity screen=(MainActivity)startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));gestureScreenV20=screen;settleV15();
        screenshot(name+"-before-day-gesture");
        dayGestureV15(screen,false,name);String selected=HomeCalendarDates.key(pagerV15(screen).currentDate());TodayDayPager pager=pagerV15(screen);
        View image=tagged(pager.currentPage(),"day_entry_image").get(0);Rect area=screenRect(image);
        long down=android.os.SystemClock.uptimeMillis();pointerV15(down,MotionEvent.ACTION_DOWN,area.exactCenterX(),area.exactCenterY());pointerV15(down,MotionEvent.ACTION_UP,area.exactCenterX(),area.exactCenterY());settleV15();
        uiAssert(()->{dialogRootV15("food_detail");if(pagerV15(screen)!=pager||!selected.equals(invoke(screen,"homeDate",new Class[]{}))||!selected.equals(HomeCalendarDates.key(pagerV15(screen).currentDate())))throw new Exception("Opening historical food moved background day from "+selected+" to "+invoke(screen,"homeDate",new Class[]{}));});
        screenshot(name+"-historical-food-detail");sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);settleV15();
        uiAssert(()->{if(!selected.equals(invoke(screen,"homeDate",new Class[]{})))throw new Exception("Closing food changed background day");assertRenderPure(screen,prefs,before);});
        if(android.animation.ValueAnimator.areAnimatorsEnabled()){
            // Reproduce a tap on the incoming day before the release spring commits it.
            TodayDayPager moving=pagerV15(screen);Rect paper=screenRect(moving);float x=paper.left+paper.width()*.08f,y=paper.top+PaperComponents.dp(c,95),end=x+paper.width()*.8f;
            long gesture=android.os.SystemClock.uptimeMillis();pointerV15(gesture,MotionEvent.ACTION_DOWN,x,y);movesV15(gesture,x,y,end,y,10);pointerV15(gesture,MotionEvent.ACTION_UP,end,y);
            final View[] incoming={null};final String oldDay=HomeCalendarDates.key(moving.currentDate());
            uiAssert(()->{if(offsetV15(moving)<moving.getWidth()*.5f||!((GestureSpring)ownField(moving,"spring")).isRunning())throw new Exception("Rapid food tap setup missed intermediate day frame");
                // Freeze this genuine intermediate frame for deterministic cross-device event timing.
                ((GestureSpring)ownField(moving,"spring")).cancel();incoming[0]=((View[])ownField(moving,"pages"))[0];});
            Rect target=screenRect(tagged(incoming[0],"day_entry_image").get(0));long tap=android.os.SystemClock.uptimeMillis();
            pointerV15(tap,MotionEvent.ACTION_DOWN,target.exactCenterX(),target.exactCenterY());pointerV15(tap,MotionEvent.ACTION_UP,target.exactCenterX(),target.exactCenterY());settleV15();
            Calendar expected=(Calendar)invoke(screen,"calendarFromKey",new Class[]{String.class},oldDay);expected.add(Calendar.DATE,-1);String kept=HomeCalendarDates.key(expected);
            uiAssert(()->{dialogRootV15("food_detail");if(!kept.equals(invoke(screen,"homeDate",new Class[]{}))||!kept.equals(HomeCalendarDates.key(pagerV15(screen).currentDate())))throw new Exception("Rapid food tap returned background to "+oldDay);});
            screenshot(name+"-rapid-historical-food-detail");sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);settleV15();
        }
        uiAssert(()->{
            Calendar invalid=(Calendar)invoke(screen,"calendarFromKey",new Class[]{String.class},today);invalid.add(Calendar.DATE,-20);
            String active=HomeCalendarDates.key(pagerV15(screen).currentDate());if(pagerV15(screen).retainDate(invalid)||!active.equals(HomeCalendarDates.key(pagerV15(screen).currentDate())))throw new Exception("Out-of-window food tap changed pager date");
            JSONObject duplicate=new JSONObject().put("breakfast",new JSONObject().put("foodItems",new JSONArray()
                .put(new JSONObject().put("id","missing1").put("sourcePhoto","qa_unified_breakfast.jpg").put("cutoutFile","qa_absent_alpha1.png"))
                .put(new JSONObject().put("id","missing2").put("sourcePhoto","qa_unified_breakfast.jpg").put("cutoutFile","qa_absent_alpha2.png"))));
            if(((java.util.List<?>)invoke(screen,"dayNotebookMedia",new Class[]{JSONObject.class},duplicate)).size()!=1)throw new Exception("Missing alpha fallback duplicates original photo");
            Class<?> cls=Class.forName("com.eatrecord.app.MainActivity$NotebookPageDrawable");java.lang.reflect.Constructor<?> constructor=cls.getDeclaredConstructor(MainActivity.class,String.class);constructor.setAccessible(true);
            for(String style:new String[]{"cream","fresh","pink","grid","plain","recipe","dotted","memo","letter","fruit","sunset","blueprint"}){
                android.graphics.drawable.Drawable drawable=(android.graphics.drawable.Drawable)constructor.newInstance(screen,style);drawable.setBounds(0,0,500,700);
                Bitmap bitmap=Bitmap.createBitmap(500,700,Bitmap.Config.ARGB_8888);int reference=0;
                for(int frame=0;frame<8;frame++){bitmap.eraseColor(Color.TRANSPARENT);drawable.draw(new Canvas(bitmap));int color=bitmap.getPixel(250,350);if(Color.alpha(color)!=255||frame>0&&color!=reference)throw new Exception("Paper opacity/color changed on repeated draw: "+style+" frame "+frame);reference=color;}
                drawable.setAlpha(128);bitmap.eraseColor(Color.TRANSPARENT);drawable.draw(new Canvas(bitmap));if(Math.abs(Color.alpha(bitmap.getPixel(250,350))-128)>1)throw new Exception("Paper drawable alpha contract lost");
                drawable.setAlpha(255);bitmap.eraseColor(Color.TRANSPARENT);drawable.draw(new Canvas(bitmap));if(bitmap.getPixel(250,350)!=reference)throw new Exception("Paper alpha reset leaked into later draw");bitmap.recycle();
            }
        });
        uiAssert(()->invoke(screen,"showTab",new Class[]{int.class,boolean.class},3,false));settleV15();
        uiAssert(()->invoke(screen,"openPreferencesPage",new Class[]{}));Thread.sleep(90);
        uiAssert(()->{ViewGroup host=(ViewGroup)field(screen,"content");if(android.animation.ValueAnimator.areAnimatorsEnabled()&&host.getChildAt(0).getTranslationX()<=0)throw new Exception("Forward settings navigation has no directional slide");visibleFrameV20(screen);});screenshotNowV15(name+"-settings-forward-mid");
        uiAssert(()->invoke(screen,"openInterfaceLayoutPage",new Class[]{}));Thread.sleep(90);uiAssert(()->{opaqueFrameV19(screen);invoke(screen,"closeInterfaceLayoutPage",new Class[]{});});Thread.sleep(90);
        uiAssert(()->{ViewGroup host=(ViewGroup)field(screen,"content");if(android.animation.ValueAnimator.areAnimatorsEnabled()&&host.getChildAt(0).getTranslationX()>=0)throw new Exception("Back settings navigation has no reverse slide");visibleFrameV20(screen);});settleV15();
        uiAssert(()->{invoke(screen,"renderVibrationPage",new Class[]{});View decor=screen.getWindow().getDecorView();if(visibleText(decor).contains("日历振动")||visibleText(decor).contains("滑动页面振动"))throw new Exception("Removed haptic options remain");});settleV15();
        uiAssert(()->{View box=screen.getWindow().getDecorView().findViewWithTag("vibration_strength"),indicator=screen.getWindow().getDecorView().findViewWithTag("vibration_strength_indicator");if(Math.abs(screenRect(box).exactCenterY()-screenRect(indicator).exactCenterY())>1)throw new Exception("Vibration indicator is not vertically centered");});screenshot(name+"-vibration-centered");
        uiAssert(()->{invoke(screen,"renderNotebookSettingsPage",new Class[]{});View decor=screen.getWindow().getDecorView();assertSettingPresence(decor,"翻页",true);assertSettingPresence(decor,"衔接动画",false);assertSettingPresence(decor,"贴纸摆放",false);assertSettingPresence(decor,"图片摆放",true);if(!visibleText(setting(decor,"图片摆放")).contains("自然斜放"))throw new Exception("Old photo-board switch replaced unified placement");invoke(screen,"renderSmartAssistantPage",new Class[]{});});settleV15();
        uiAssert(()->{View decor=screen.getWindow().getDecorView();ViewGroup food=(ViewGroup)setting(decor,"餐图识别"),diet=(ViewGroup)setting(decor,"营养师");if(!"spark".equals(ownField(food.getChildAt(0),"type"))||!"doll".equals(ownField(diet.getChildAt(0),"kind")))throw new Exception("Assistant icons not restored/simplified");});screenshot(name+"-assistant-icons");
        uiAssert(()->invoke(screen,"renderBookDetail",new Class[]{String.class,boolean.class},today,false));settleV15();
        notebookGesturesV20(screen,c,prefs,before,name);
        uiAssert(()->assertRenderPure(screen,prefs,before));if(apiCalls.get()!=0)throw new Exception("UI-only test made food API calls");
        Bundle result=new Bundle();result.putString("stream","PASS: historical and mid-spring food taps retain background date, live calendar marker, 12 paper styles stable over repeated frames/alpha reset, directional settings/interruptions, centered vibration control and removed legacy settings, distinct assistant icons, notebook fold/cancel/reversal/interruption/reduced motion and unchanged data. App-window pointer fallbacks: "+localPointerFallbacksV20+".\n");finish(Activity.RESULT_OK,result);
    }
    private void notebookGesturesV20(MainActivity screen,Context c,android.content.SharedPreferences prefs,String before,String name)throws Exception {
        View frame=(View)field(screen,"bookDetailSwipeFrame");String initial=(String)ownField(frame,"currentDate");
        Rect area=screenRect(frame),paper=screenRect(frame.findViewWithTag("book_detail_notebook_page"));float x=area.left+area.width()*.82f,y=paper.top+paper.height()*.48f,end=x-area.width()*.66f;
        long down=android.os.SystemClock.uptimeMillis();pointerV15(down,MotionEvent.ACTION_DOWN,x,y);movesV15(down,x,y,end,y,10);
        uiAssert(()->{
            float progress=((Number)ownField(frame,"flipProgress")).floatValue();if(progress<=.1f||progress>=.99f)throw new Exception("Notebook does not track intermediate fold");
            if(android.animation.ValueAnimator.areAnimatorsEnabled()&&ownField(frame,"turn")==null)throw new Exception("No opaque page-back renderer");
            for(String field:new String[]{"currentPage","nextPage"}){View page=(View)ownField(frame,field);if(page.getScaleX()!=1||page.getAlpha()!=1||page.findViewWithTag("book_detail_notebook_page").getScaleX()!=1)throw new Exception("Book text is still flattened or faded during turn");}
            Bitmap bitmap=Bitmap.createBitmap(frame.getWidth(),frame.getHeight(),Bitmap.Config.ARGB_8888);frame.draw(new Canvas(bitmap));
            for(int px:new int[]{frame.getWidth()/4,frame.getWidth()/2,frame.getWidth()*3/4})if(Color.alpha(bitmap.getPixel(px,paper.centerY()-area.top))!=255)throw new Exception("Page fold has a transparent interior frame");bitmap.recycle();
        });screenshotNowV15(name+"-notebook-forward-fold-mid");
        pointerV15(down,MotionEvent.ACTION_CANCEL,end,y);settleV15();
        uiAssert(()->{if(!initial.equals(ownField(frame,"currentDate"))||ownField(frame,"nextPage")!=null||ownField(frame,"turn")!=null)throw new Exception("ACTION_CANCEL committed notebook date or leaked paper snapshot");});
        down=android.os.SystemClock.uptimeMillis();pointerV15(down,MotionEvent.ACTION_DOWN,x,y);movesV15(down,x,y,end,y,8);
        uiAssert(()->{
            // Two pointers cancel the gesture; terminal UP must not re-enable the pending commit.
            long time=android.os.SystemClock.uptimeMillis();MotionEvent.PointerProperties[] properties=new MotionEvent.PointerProperties[2];MotionEvent.PointerCoords[] coords=new MotionEvent.PointerCoords[2];
            for(int i=0;i<2;i++){properties[i]=new MotionEvent.PointerProperties();properties[i].id=i;properties[i].toolType=MotionEvent.TOOL_TYPE_FINGER;coords[i]=new MotionEvent.PointerCoords();coords[i].x=frame.getWidth()*.3f+i*20;coords[i].y=frame.getHeight()*.5f;coords[i].pressure=1;coords[i].size=1;}
            for(int action:new int[]{MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),MotionEvent.ACTION_POINTER_UP|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),MotionEvent.ACTION_UP}){
                MotionEvent event=MotionEvent.obtain(time,time,action,action==MotionEvent.ACTION_UP?1:2,properties,coords,0,0,1,1,0,0,android.view.InputDevice.SOURCE_TOUCHSCREEN,0);frame.onTouchEvent(event);event.recycle();}
        });pointerV15(down,MotionEvent.ACTION_CANCEL,end,y);settleV15();
        uiAssert(()->{if(!initial.equals(ownField(frame,"currentDate"))||ownField(frame,"nextPage")!=null)throw new Exception("Multi-pointer cancellation was recommitted by terminal UP");});
        down=android.os.SystemClock.uptimeMillis();pointerV15(down,MotionEvent.ACTION_DOWN,x,y);movesV15(down,x,y,end,y,10);pointerV15(down,MotionEvent.ACTION_UP,end,y);settleV15();
        final String older=(String)ownField(frame,"currentDate");if(initial.equals(older))throw new Exception("Notebook forward gesture did not change date");screenshot(name+"-notebook-next-opaque");
        float rx=area.left+area.width()*.18f,re=rx+area.width()*.66f;
        down=android.os.SystemClock.uptimeMillis();pointerV15(down,MotionEvent.ACTION_DOWN,rx,y);movesV15(down,rx,y,re,y,10);screenshotNowV15(name+"-notebook-reverse-fold-mid");pointerV15(down,MotionEvent.ACTION_UP,re,y);settleV15();
        uiAssert(()->{if(!initial.equals(ownField(frame,"currentDate")))throw new Exception("Reverse notebook turn did not return to starting date");});
        if(android.animation.ValueAnimator.areAnimatorsEnabled()){
            down=android.os.SystemClock.uptimeMillis();pointerV15(down,MotionEvent.ACTION_DOWN,x,y);movesV15(down,x,y,end,y,8);pointerV15(down,MotionEvent.ACTION_UP,end,y);Thread.sleep(35);
            long interrupt=android.os.SystemClock.uptimeMillis();pointerV15(interrupt,MotionEvent.ACTION_DOWN,end,y);
            uiAssert(()->{if(((Number)ownField(frame,"flipProgress")).floatValue()<.2f||((GestureSpring)ownField(frame,"spring")).isRunning())throw new Exception("Grabbing notebook settlement snapped or failed to stop spring");});
            movesV15(interrupt,end,y,end+area.width()*.3f,y,6);pointerV15(interrupt,MotionEvent.ACTION_CANCEL,end+area.width()*.3f,y);settleV15();
            uiAssert(()->{if(!initial.equals(ownField(frame,"currentDate")))throw new Exception("Interrupted notebook spring committed stale destination");});
        }
        // Slide preference remains available; it shares cancellation and persistence rules.
        uiAssert(()->((JSONObject)field(screen,"profile")).put("bookTransitionMode","normal"));
        down=android.os.SystemClock.uptimeMillis();pointerV15(down,MotionEvent.ACTION_DOWN,x,y);movesV15(down,x,y,end,y,8);pointerV15(down,MotionEvent.ACTION_CANCEL,end,y);settleV15();
        uiAssert(()->{if(!initial.equals(ownField(frame,"currentDate")))throw new Exception("Cancelled slide mode changed notebook date");((JSONObject)field(screen,"profile")).put("bookTransitionMode","flip");});
        down=android.os.SystemClock.uptimeMillis();pointerV15(down,MotionEvent.ACTION_DOWN,x,y);movesV15(down,x,y,end,y,6);
        uiAssert(()->invoke(screen,"showTab",new Class[]{int.class,boolean.class},3,false));settleV15();
        uiAssert(()->{if(ownField(frame,"turn")!=null||((GestureSpring)ownField(frame,"spring")).isRunning()||ownField(frame,"nextPage")!=null)throw new Exception("Leaving notebook retained an active turn or bitmap");assertRenderPure(screen,prefs,before);});
    }
    private void visibleFrameV20(MainActivity screen)throws Exception {
        opaqueFrameV19(screen);View host=(View)field(screen,"content");Bitmap bitmap=Bitmap.createBitmap(180,360,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);canvas.scale(180f/host.getWidth(),360f/host.getHeight());host.draw(canvas);
        int background=UiTokens.paper(Boolean.TRUE.equals(invoke(screen,"nightDecoration",new Class[]{}))),ink=0;
        for(int y=20;y<340;y+=2)for(int x=8;x<172;x+=2){int color=bitmap.getPixel(x,y);int difference=Math.abs(Color.red(color)-Color.red(background))+Math.abs(Color.green(color)-Color.green(background))+Math.abs(Color.blue(color)-Color.blue(background));if(Color.alpha(color)>200&&difference>95)ink++;}
        bitmap.recycle();if(ink<16)throw new Exception("Navigation intermediate frame has paper color but no visible content: "+ink);
    }
    private void supplementV20(Context c,android.content.SharedPreferences prefs)throws Exception {
        FoodAnalysisJobService.cancelAll(c);FoodDraftStore.discard(c,FoodDraftStore.pendingSources(c));installTransport();streamName=false;
        String today=HomeCalendarDates.key(Calendar.getInstance());JSONObject profile=new JSONObject().put("autoCloudSync",false).put("foodAutoAnalyze",false).put("foodPhotoConsent",false).put("seenAnnouncementVersion",c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName);
        prefs.edit().clear().putString(NutritionStore.PROFILE,profile.toString()).putString(NutritionStore.DAYS,"{}").commit();
        MainActivity screen=(MainActivity)startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));settleV15();
        uiAssert(()->{JSONObject defaults=(JSONObject)field(screen,"profile");if(!"yellow".equals(defaults.optString("decoration"))||!Boolean.TRUE.equals(invoke(screen,"footprintExpanded",new Class[]{})))throw new Exception("Fresh user does not default to yellow/expanded footprints");invoke(screen,"showTab",new Class[]{int.class,boolean.class},1,false);});settleV15();
        uiAssert(()->{String text=visibleText(screen.getWindow().getDecorView());if(!text.contains("还没有吃饭记录本呢(●'◡'●)")||text.contains("\n😀\n"))throw new Exception("Fresh footprint empty state retained smile tile or old title");invoke(screen,"renderInterfaceLayoutPage",new Class[]{});assertSettingPresence(screen.getWindow().getDecorView(),"足迹页面布局",true);invoke(screen,"chooseFootprintLayout",new Class[]{});});settleV15();
        String choices=dialogTextsV20(screen);if(!choices.contains("展开")||!choices.contains("平滑")||choices.indexOf("展开")>choices.indexOf("平滑"))throw new Exception("Footprint layout choices/order missing: "+choices);dismissDialogV20(screen);settleV15();
        uiAssert(()->{invoke(screen,"resetPersonalizationDefaults",new Class[]{});if(!"yellow".equals(((JSONObject)field(screen,"profile")).optString("decoration"))||!Boolean.TRUE.equals(invoke(screen,"footprintExpanded",new Class[]{})))throw new Exception("Restore defaults does not use yellow/expanded");invoke(screen,"showTab",new Class[]{int.class,boolean.class},0,false);});settleV15();
        final AlertDialog[] editor={null};final String[] previous={null};
        JSONObject skipped=new JSONObject();Calendar day=Calendar.getInstance();for(int i=0;i<2;i++){day.add(Calendar.DATE,-1);skipped.put(HomeCalendarDates.key(day),new JSONObject().put("breakfast",new JSONObject().put("skipped",true)));}
        uiAssert(()->{JSONObject p=(JSONObject)field(screen,"profile");p.put("skipHints",new JSONObject().put("two",new JSONArray().put("两次缺席提示A").put("两次缺席提示B")));prefs.edit().putString(NutritionStore.PROFILE,p.toString()).putString(NutritionStore.DAYS,skipped.toString()).commit();setMainField(screen,"days",skipped);
            editor[0]=(AlertDialog)invoke(screen,"showManualMealTextEditor",new Class[]{String.class,String.class},today,"breakfast");});waitForIdleSync();
        uiAssert(()->{View decor=editor[0].getWindow().getDecorView();android.widget.EditText input=(android.widget.EditText)decor.findViewWithTag("manual_food_text");previous[0]=input.getHint().toString();if(!previous[0].startsWith("两次缺席提示")||input.getHint().toString().contains("例如")||!visibleText(decor.findViewWithTag("manual_food_examples")).contains("例如")||visibleText(decor).contains("只保存你填写"))throw new Exception("Manual field examples/custom absence hints are misplaced");editor[0].dismiss();editor[0]=(AlertDialog)invoke(screen,"showManualMealTextEditor",new Class[]{String.class,String.class},today,"breakfast");});waitForIdleSync();
        uiAssert(()->{String next=((android.widget.EditText)editor[0].getWindow().getDecorView().findViewWithTag("manual_food_text")).getHint().toString();if(previous[0].equals(next))throw new Exception("Same absence group never rotates prompts");editor[0].dismiss();editor[0]=(AlertDialog)invoke(screen,"showManualMealTextEditor",new Class[]{String.class,String.class},today,"snack");});waitForIdleSync();
        uiAssert(()->{android.widget.EditText input=(android.widget.EditText)editor[0].getWindow().getDecorView().findViewWithTag("manual_food_text");if(input.getHint().toString().startsWith("两次缺席提示"))throw new Exception("Night meal wrongly uses daytime absence prompt");input.setText("夜晚的热牛奶");editor[0].getButton(AlertDialog.BUTTON_POSITIVE).performClick();});settleV15();
        JSONObject meal=NutritionStore.meal(NutritionStore.read(prefs,NutritionStore.DAYS),today,"snack");if(meal==null||!"夜晚的热牛奶".equals(meal.optString("text"))||DayFoodEntries.collect(new JSONObject().put("snack",meal)).size()!=1||!"夜".equals(DayFoodEntries.collect(new JSONObject().put("snack",meal)).get(0).prefix))throw new Exception("Manual night meal is not preserved as a night-prefixed record");
        uiAssert(()->{if(!visibleText(pagerV15(screen).currentPage()).contains("夜 · 夜晚的热牛奶"))throw new Exception("Default three-meal preference hides saved night meal");invoke(screen,"renderBookDetail",new Class[]{String.class,boolean.class},today,false);});settleV15();
        uiAssert(()->{String text=visibleText(screen.getWindow().getDecorView());if(!text.contains("吃饭记录本")||!text.contains("夜晚的热牛奶"))throw new Exception("Record book omits saved night meal or uses old title");invoke(screen,"showTab",new Class[]{int.class,boolean.class},0,false);invoke(screen,"chooseMealForText",new Class[]{});});settleV15();if(!dialogTextsV20(screen).contains("夜宵"))throw new Exception("Handwriting chooser has no night meal");dismissDialogV20(screen);settleV15();
        uiAssert(()->invoke(screen,"chooseMealForPhoto",new Class[]{}));settleV15();if(!dialogTextsV20(screen).contains("夜宵"))throw new Exception("Photo chooser has no night meal");dismissDialogV20(screen);settleV15();
        cutoutGesturesV20(screen,c);restoreOutsideCrop(screen,c);
        if(apiCalls.get()!=0)throw new Exception("Manual night food/editor UI made a paid API request");
        Bundle result=new Bundle();result.putString("stream","PASS: fresh/reset yellow+expanded footprints, renamed empty state/book, custom absence hint rotation and separate examples, night meal menus/save/default visibility, editor pinch/pan cancellation and constant-screen-width brush, restore outside crop.\n");finish(Activity.RESULT_OK,result);
    }
    private void cutoutEventV20(CutoutEditor editor,int action,float... points){
        int count=points.length/2;MotionEvent.PointerProperties[] properties=new MotionEvent.PointerProperties[count];MotionEvent.PointerCoords[] coords=new MotionEvent.PointerCoords[count];
        for(int i=0;i<count;i++){properties[i]=new MotionEvent.PointerProperties();properties[i].id=i;properties[i].toolType=MotionEvent.TOOL_TYPE_FINGER;coords[i]=new MotionEvent.PointerCoords();coords[i].x=points[i*2];coords[i].y=points[i*2+1];coords[i].pressure=1;coords[i].size=1;}
        long now=android.os.SystemClock.uptimeMillis();MotionEvent event=MotionEvent.obtain(now,now,action,count,properties,coords,0,0,1,1,0,0,android.view.InputDevice.SOURCE_TOUCHSCREEN,0);editor.onTouchEvent(event);event.recycle();
    }
    private int maskHashV20(Bitmap bitmap){int[] pixels=new int[bitmap.getWidth()*bitmap.getHeight()];bitmap.getPixels(pixels,0,bitmap.getWidth(),0,0,bitmap.getWidth(),bitmap.getHeight());return Arrays.hashCode(pixels);}
    private void cutoutGesturesV20(MainActivity screen,Context c)throws Exception {
        uiAssert(()->{
            Bitmap source=Bitmap.createBitmap(600,600,Bitmap.Config.ARGB_8888);source.eraseColor(Color.rgb(180,70,30));Bitmap alpha=source.copy(Bitmap.Config.ARGB_8888,false);
            java.lang.reflect.Constructor<CutoutEditor> constructor=CutoutEditor.class.getDeclaredConstructor(Context.class,Bitmap.class,Bitmap.class);constructor.setAccessible(true);CutoutEditor editor=constructor.newInstance(screen,source,alpha);alpha.recycle();editor.layout(0,0,600,600);
            Bitmap mask=(Bitmap)ownField(editor,"mask");int initial=maskHashV20(mask),sourceHash=maskHashV20(source);CutoutViewport viewport=(CutoutViewport)ownField(editor,"viewport");
            cutoutEventV20(editor,MotionEvent.ACTION_DOWN,270,300);cutoutEventV20(editor,MotionEvent.ACTION_MOVE,290,300);
            cutoutEventV20(editor,MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),260,300,340,300);
            cutoutEventV20(editor,MotionEvent.ACTION_MOVE,180,300,420,300);
            if(Math.abs(viewport.zoom()-3)>.02f)throw new Exception("Real two-pointer editor input did not magnify three times");
            cutoutEventV20(editor,MotionEvent.ACTION_MOVE,200,320,440,320);
            cutoutEventV20(editor,MotionEvent.ACTION_POINTER_UP|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),200,320,440,320);
            cutoutEventV20(editor,MotionEvent.ACTION_MOVE,300,320);cutoutEventV20(editor,MotionEvent.ACTION_UP,300,320);
            if(maskHashV20(mask)!=initial)throw new Exception("Pinch/pan or remaining finger painted mask pixels");
            for(int zoom:new int[]{1,3}){
                invokeAnyV20(editor,"resetMask",new Class[]{});viewport.transform(300,300,300,300,zoom);
                cutoutEventV20(editor,MotionEvent.ACTION_DOWN,240,300);cutoutEventV20(editor,MotionEvent.ACTION_MOVE,360,300);cutoutEventV20(editor,MotionEvent.ACTION_UP,360,300);
                int centerX=Math.round(viewport.imageX(300)),centerY=Math.round(viewport.imageY(300));float coverage=0;
                // Integrate AA edge coverage: counting only fully-clear pixels undercounts at high zoom.
                for(int y=Math.max(0,centerY-70);y<=Math.min(599,centerY+70);y++)coverage+=1-Color.alpha(mask.getPixel(centerX,y))/255f;
                if(Math.abs(coverage*viewport.scale()-28)>3)throw new Exception("Paint width changes with zoom "+zoom+": "+coverage*viewport.scale());
            }
            invokeAnyV20(editor,"resetMask",new Class[]{});if(maskHashV20(mask)!=initial||viewport.zoom()!=1||maskHashV20(source)!=sourceHash)throw new Exception("Reset altered source or failed to restore fit/mask");invokeAnyV20(editor,"release",new Class[]{});
        });
    }
    private JSONObject waitSavedOperationV20(Context c,android.content.SharedPreferences prefs,String date,String source,boolean cutout)throws Exception {
        long end=android.os.SystemClock.elapsedRealtime()+150000;JSONObject item=null;
        do{item=FoodSavedPhotoPolicy.sourceItem(NutritionStore.meal(NutritionStore.read(prefs,NutritionStore.DAYS),date,"breakfast"),source);
            if(item!=null&&(cutout?"completed".equals(item.optString("cutoutStatus"))&&!FoodAnalysisJobService.cutoutPending(c,item):"completed".equals(item.optString("analysisStatus"))&&!FoodAnalysisJobService.nutritionPending(c,item)))return item;
            Thread.sleep(150);
        }while(android.os.SystemClock.elapsedRealtime()<end);
        throw new Exception("Independent operation did not complete: "+item);
    }
    private void reprocessV20(Context c,android.content.SharedPreferences prefs)throws Exception {
        FoodAnalysisJobService.cancelAll(c);FoodDraftStore.discard(c,FoodDraftStore.pendingSources(c));installTransport();streamName=false;
        String today=HomeCalendarDates.key(Calendar.getInstance()),source="qa_unified_breakfast.jpg";JSONObject fixture=unifiedDays(c,today);
        JSONObject original=fixture.getJSONObject(today).getJSONObject("breakfast").getJSONArray("foodItems").getJSONObject(0);
        original.put("analysisId","qa-preserved-analysis").put("userEdited",true).put("dishName","用户更正的夜宵").put("displayRotation",90).put("cutoutEditedAt",1234);
        String nutrients=original.getJSONObject("nutrients").toString(),alphaBefore=original.optString("cutoutFile");
        JSONObject profile=new JSONObject().put("decoration","yellow").put("foodAutoAnalyze",false).put("foodPhotoConsent",false).put("autoCloudSync",false).put("apiEnabled",true).put("apiKey","qa-not-a-real-key").put("apiEndpoint","https://food.qa.invalid/chat/completions").put("apiModel","qa-vision").put("seenAnnouncementVersion",c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName);
        prefs.edit().remove("food_processing_preview").putString(NutritionStore.DAYS,fixture.toString()).putString(NutritionStore.PROFILE,profile.toString()).commit();
        MainActivity screen=(MainActivity)startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));settleV15();
        if(!FoodAnalysisJobService.enqueueCutout(c,today,"breakfast",source))throw new Exception("Explicit local cutout was rejected");
        JSONObject cut=waitSavedOperationV20(c,prefs,today,source,true);
        if(apiCalls.get()!=0||!nutrients.equals(cut.getJSONObject("nutrients").toString())||!"qa-preserved-analysis".equals(cut.optString("analysisId"))||!cut.optBoolean("userEdited")||cut.optInt("displayRotation")!=90||alphaBefore.equals(cut.optString("cutoutFile")))throw new Exception("Local-only cutout changed nutrition/edit/rotation or called API");
        String alpha=cut.getString("cutoutFile"),alphaHash=FoodCutoutEngine.hash(new File(c.getFilesDir(),alpha));
        profile.put("foodAutoAnalyze",true).put("foodPhotoConsent",true);prefs.edit().putString(NutritionStore.PROFILE,profile.toString()).commit();holdResponse=true;
        try{
            if(!FoodAnalysisJobService.enqueueNutrition(c,today,"breakfast",source))throw new Exception("Explicit nutrition retry was rejected");
            if(!responseEntered.await(45,java.util.concurrent.TimeUnit.SECONDS))throw new Exception("Nutrition-only request did not start promptly after local stage");
            JSONObject pending=FoodSavedPhotoPolicy.sourceItem(NutritionStore.meal(NutritionStore.read(prefs,NutritionStore.DAYS),today,"breakfast"),source);String generation=pending.getString("analysisId");
            if(!alpha.equals(pending.optString("cutoutFile"))||!alphaHash.equals(FoodCutoutEngine.hash(new File(c.getFilesDir(),alpha)))||!nutrients.equals(pending.getJSONObject("nutrients").toString()))throw new Exception("Nutrition-only request reran cutout or removed current valid nutrition");
            String repaired="food_cutout_qa_repaired_preserve_nutrition.png";try(InputStream in=new FileInputStream(new File(c.getFilesDir(),alpha));OutputStream out=new FileOutputStream(new File(c.getFilesDir(),repaired))){byte[] bytes=new byte[8192];int n;while((n=in.read(bytes))>=0)out.write(bytes,0,n);}
            if(!FoodAnalysisJobService.saveCutout(c,today,"breakfast",source,repaired,pending.optJSONArray("cutoutRect")))throw new Exception("Saving a repaired cutout was rejected");
            responseReleased.countDown();holdResponse=false;JSONObject result=waitSavedOperationV20(c,prefs,today,source,false);
            if(apiCalls.get()!=1||!repaired.equals(result.optString("cutoutFile"))||!generation.equals(result.optString("analysisId"))||!"用户更正的夜宵".equals(result.optString("dishName"))||result.getJSONObject("nutrients").optInt("caloriesKcal")!=500||result.optInt("displayRotation")!=90)throw new Exception("Paid nutrition result or manual cutout/name was overwritten by independent operation");
            uiAssert(()->{Object food=invoke(screen,"foodUi",new Class[]{});invokeAnyV20(food,"actions",new Class[]{String.class,String.class,JSONObject.class},today,"breakfast",result);});settleV15();
            String actions=dialogTextsV20(screen);if(!actions.contains("重新抠图")||!actions.contains("识别信息")||actions.contains("撤销这次识别")||actions.contains("重新识别和抠图"))throw new Exception("Food action menu retains combined retry or obsolete undo: "+actions);
            tapDialogV20(screen,"识别信息");settleV15();if(!dialogTextsV20(screen).contains("取消"))throw new Exception("Nutrition retry has no fee confirmation");tapDialogV20(screen,"取消");settleV15();if(apiCalls.get()!=1)throw new Exception("Cancelling fee confirmation made another API request");
            JSONObject staleForm=new JSONObject(result.toString());staleForm.getJSONObject("nutrients").put("caloriesKcal",700);staleForm.put("dishName","编辑期间的新菜名");
            synchronized(NutritionStore.LOCK){
                if(!FoodAnalysisJobService.enqueueCutout(c,today,"breakfast",source)||!FoodAnalysisJobService.saveNutritionEdit(c,today,"breakfast",source,staleForm))throw new Exception("Editing nutrition during queued cutout was rejected");
                JSONObject merged=FoodSavedPhotoPolicy.sourceItem(NutritionStore.meal(NutritionStore.read(prefs,NutritionStore.DAYS),today,"breakfast"),source);
                if(!FoodAnalysisJobService.cutoutPending(c,merged))throw new Exception("Nutrition form cancelled an independent local cutout task");
            }
            JSONObject merged=waitSavedOperationV20(c,prefs,today,source,true);
            if(repaired.equals(merged.optString("cutoutFile"))||merged.getJSONObject("nutrients").optInt("caloriesKcal")!=700||!"编辑期间的新菜名".equals(merged.optString("dishName"))||apiCalls.get()!=1)throw new Exception("Local completion rolled back manual nutrition or stale form cancelled new alpha");
            JSONObject staleAfter=new JSONObject(staleForm.toString());staleAfter.getJSONObject("nutrients").put("caloriesKcal",720);
            String latestAlpha=merged.optString("cutoutFile");
            if(!FoodAnalysisJobService.saveNutritionEdit(c,today,"breakfast",source,staleAfter))throw new Exception("Saving stale nutrition form after local completion failed");
            JSONObject after=FoodSavedPhotoPolicy.sourceItem(NutritionStore.meal(NutritionStore.read(prefs,NutritionStore.DAYS),today,"breakfast"),source);
            if(!latestAlpha.equals(after.optString("cutoutFile"))||after.getJSONObject("nutrients").optInt("caloriesKcal")!=720)throw new Exception("Stale nutrition form restored an obsolete alpha");
            String draftSource="draft_qa_nutrition_merge.jpg";layoutSource(c,draftSource);JSONObject draftItem=new JSONObject(after.toString()).put("id","qa-merge-draft").put("sourcePhoto",draftSource).put("cutoutRequestId","qa-local-draft");
            JSONObject draftRecord=new JSONObject().put("date",today).put("meal","snack").put("ownedSource",true).put("item",draftItem);
            JSONArray pendingDraft=new JSONArray().put(new JSONObject().put("id","qa-merge-draft").put("analysisId",draftItem.optString("analysisId")))
                .put(new JSONObject().put("id","qa-merge-draft").put("analysisId","qa-local-draft").put("cutoutOnly",true).put("epoch",prefs.getLong("food_jobs_epoch",0)).put("cutoutBaseEditedAt",draftItem.optLong("cutoutEditedAt")).put("notBeforeAt",System.currentTimeMillis()+60000));
            prefs.edit().putString(FoodDraftStore.KEY,new JSONObject().put(draftSource,draftRecord).toString()).putString(FoodAnalysisJobService.QUEUE,pendingDraft.toString()).commit();
            JSONObject staleDraft=new JSONObject(draftItem.toString()).put("cutoutFile","food_cutout_obsolete.png");
            if(!FoodDraftStore.replace(c,draftSource,staleDraft))throw new Exception("Legacy draft nutrition editor failed atomic merge");
            JSONObject finalDraft=FoodDraftStore.item(c,draftSource);
            if(!latestAlpha.equals(finalDraft.optString("cutoutFile"))||!FoodAnalysisJobService.cutoutPending(c,finalDraft)||FoodAnalysisJobService.jobs(prefs).length()!=1)throw new Exception("Draft form rolled back latest alpha or cancelled local queue");
            FoodDraftStore.discard(c,java.util.Collections.singletonList(draftSource));
        }finally{responseReleased.countDown();holdResponse=false;}
        Bundle result=new Bundle();result.putString("stream","PASS: real local-only cutout calls API zero times and preserves edited nutrition/rotation/generation; nutrition-only sends one mocked request, reuses alpha and corrected name; bidirectional image/nutrition merge before/after local completion and draft queue preserved; split menus/no undo and fee cancellation.\n");finish(Activity.RESULT_OK,result);
    }
    private Object invokeAnyV20(Object target,String name,Class[] types,Object... args)throws Exception {Method method=target.getClass().getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(target,args);}
    private String dialogTextsV20(MainActivity screen)throws Exception {final StringBuilder result=new StringBuilder();uiAssert(()->{for(View view:android.view.inspector.WindowInspector.getGlobalWindowViews())if(view!=screen.getWindow().getDecorView())result.append(visibleText(view));});return result.toString();}
    private void tapDialogV20(MainActivity screen,String text)throws Exception {if(!clickLocalTextV20(screen,text))throw new Exception("Missing shown dialog text "+text);waitForIdleSync();}
    private boolean clickLocalTextV20(MainActivity screen,String text)throws Exception {final boolean[] clicked={false};uiAssert(()->{List<View> roots=android.view.inspector.WindowInspector.getGlobalWindowViews();for(int i=roots.size()-1;i>=0&&!clicked[0];i--){View root=roots.get(i);if(screen!=null&&root==screen.getWindow().getDecorView())continue;List<View> views=new ArrayList<>();collectViews(root,views);for(View view:views)if(view instanceof android.widget.TextView&&text.contentEquals(((android.widget.TextView)view).getText())&&view.isShown()&&view.getGlobalVisibleRect(new Rect())){View target=view;while(target!=null){if(target.isClickable()&&target.performClick()){clicked[0]=true;return;}if(target.getParent() instanceof android.widget.AdapterView){android.widget.AdapterView list=(android.widget.AdapterView)target.getParent();int p=list.getPositionForView(target);if(p>=0&&list.performItemClick(target,p,list.getAdapter().getItemId(p))){clicked[0]=true;return;}}target=target.getParent() instanceof View?(View)target.getParent():null;}}}});return clicked[0];}
    private void dismissDialogV20(MainActivity screen)throws Exception {uiAssert(()->{List<View> roots=android.view.inspector.WindowInspector.getGlobalWindowViews();for(int i=roots.size()-1;i>=0;i--){View root=roots.get(i);if(root!=screen.getWindow().getDecorView()&&root.isShown()){root.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BACK));root.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BACK));return;}}});}
    /** No canned inference response: these scenarios fail if any Java HTTP(S) request is attempted. */
    private java.util.concurrent.atomic.AtomicInteger denyNetworkV22(){
        final java.util.concurrent.atomic.AtomicInteger requests=new java.util.concurrent.atomic.AtomicInteger();
        URL.setURLStreamHandlerFactory(protocol->!("https".equals(protocol)||"http".equals(protocol))?null:new URLStreamHandler(){
            @Override protected URLConnection openConnection(URL url)throws IOException {requests.incrementAndGet();throw new IOException("QA forbids HTTP(S)");}
        });return requests;
    }
    private void setHomeDateV22(MainActivity screen,String date,boolean expanded)throws Exception {
        uiAssert(()->{setMainField(screen,"homeDateKey",date);setMainField(screen,"homeCalendarExpanded",expanded);
            invoke(screen,"showTab",new Class[]{int.class,boolean.class},0,false);});settleV15();
    }
    private Calendar dayV22(String key)throws Exception {Calendar day=Calendar.getInstance();day.setTime(new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).parse(key));return HomeCalendarDates.day(day);}
    private RectF markerV22(TodayWeekStrip week)throws Exception {
        Bitmap frame=Bitmap.createBitmap(week.getWidth(),week.getHeight(),Bitmap.Config.ARGB_8888);
        try{week.draw(new Canvas(frame));return new RectF((RectF)ownField(week,"selectionBounds"));}finally{frame.recycle();}
    }
    private void linkedProgressV22(MainActivity screen,String fromKey,String toKey,boolean expanded)throws Exception {
        setHomeDateV22(screen,fromKey,expanded);
        final HomeCalendarView calendar=calendarV15(screen);final TodayWeekStrip week=calendar.weekStrip();
        final Calendar from=dayV22(fromKey),to=dayV22(toKey);
        final View[] weekRows=(View[])ownField(week,"pages");final View row=week.currentRow();
        final int weekShift=HomeDayMotion.weekShift(from,to),monthShift=HomeDayMotion.monthShift(from,to);
        final RectF[] markers=new RectF[3];final float[] samples={.12f,.32f,.64f};
        for(int sample=0;sample<samples.length;sample++){
            final int index=sample;final float progress=samples[sample];uiAssert(()->{
                calendar.setDayProgress(from,to,progress);markers[index]=markerV22(week);
                if(calendar.weekStrip()!=week||week.currentRow()!=row)throw new Exception("Linked day MOVE rebuilt calendar/week rows");
                View[] now=(View[])ownField(week,"pages");for(int i=0;i<3;i++)if(now[i]!=weekRows[i])throw new Exception("Linked progress allocated another week");
                float actual=((Number)ownField(week,"dayProgress")).floatValue();
                if(Math.abs(actual-progress)>.001f)throw new Exception("Week marker does not use exact sheet progress before half");
                String selected=HomeCalendarDates.key((Calendar)ownField(calendar,"selected"));
                if(!(progress>=.5f?toKey:fromKey).equals(selected))throw new Exception("Semantic date differs from live sheet midpoint");
                float expected=-weekShift*week.getWidth()*progress;
                if(Math.abs(((Number)ownField(week,"offset")).floatValue()-expected)>1.5f)throw new Exception("Cross-week row jumps rather than follows day progress");
                if(expanded){Object month=ownField(calendar,"monthPager");float width=((View)month).getWidth();
                    if(Math.abs(((Number)ownField(month,"offset")).floatValue()+monthShift*width*progress)>1.5f)throw new Exception("Expanded month is not tied to day displacement");
                    android.widget.TextView label=(android.widget.TextView)ownField(calendar,"monthLabel");
                    if(monthShift!=0&&Math.abs(label.getAlpha()-Math.abs(1-2*progress))>.02f)throw new Exception("Month title changes without the linked transition");}
            });
        }
        if(weekShift==0)uiAssert(()->{
            float first=markers[0].centerX(),second=markers[1].centerX(),third=markers[2].centerX();
            if(markers[0].isEmpty()||markers[1].isEmpty()||markers[2].isEmpty()||Math.abs(second-first)<2||Math.abs(third-second)<2||(second-first)*(third-second)<=0)
                throw new Exception("Selected date pill jumps at half instead of moving continuously");
        });
        uiAssert(()->{calendar.setDayProgress(from,to,.18f);markerV22(week);calendar.setDayProgress(from,from,0);markerV22(week);
            if(!fromKey.equals(HomeCalendarDates.key((Calendar)ownField(calendar,"selected")))||((Number)ownField(week,"offset")).floatValue()!=0||Boolean.TRUE.equals(ownField(week,"dayPreview")))
                throw new Exception("Reversal/cancel left a preview date or row displacement");
        });
    }
    private void realLinkedGestureV22(MainActivity screen)throws Exception {
        setHomeDateV22(screen,"2000-03-06",false);TodayDayPager pager=pagerV15(screen);HomeCalendarView calendar=calendarV15(screen);TodayWeekStrip week=calendar.weekStrip();
        Rect area=screenRect(pager);float x=area.left+area.width()*.14f,y=area.top+Math.min(area.height()*.3f,PaperComponents.dp(screen,95));
        long down=android.os.SystemClock.uptimeMillis();pointerV15(down,MotionEvent.ACTION_DOWN,x,y);
        movesV15(down,x,y,x+area.width()*.25f,y,8);
        uiAssert(()->{if(android.animation.ValueAnimator.areAnimatorsEnabled()){
            float fraction=HomeDayMotion.fraction(offsetV15(pager),pager.getWidth()+PaperComponents.dp(screen,24));
            if(fraction<=.05f||fraction>=.5f||Math.abs(((Number)ownField(week,"dayProgress")).floatValue()-fraction)>.002f)
                throw new Exception("Actual food drag has no linked date progress before half");
        }});
        screenshotNowV15(args.getString("name","beta22")+"-date-before-half");
        movesV15(down,x+area.width()*.25f,y,x+area.width()*.7f,y,9);
        uiAssert(()->{if(android.animation.ValueAnimator.areAnimatorsEnabled()&&!"2000-03-05".equals(HomeCalendarDates.key((Calendar)ownField(calendar,"selected"))))throw new Exception("Actual cross-week swipe did not synchronize semantic date");});
        movesV15(down,x+area.width()*.7f,y,x+area.width()*.09f,y,9);pointerV15(down,MotionEvent.ACTION_CANCEL,x+area.width()*.09f,y);settleV15();
        uiAssert(()->{if(!"2000-03-06".equals(HomeCalendarDates.key(pager.currentDate()))||!"2000-03-06".equals(invoke(screen,"homeDate",new Class[]{}))||calendar.weekStrip()!=week)
            throw new Exception("Actual reversal/cancel rebuilt calendar or committed incoming date");});
        down=android.os.SystemClock.uptimeMillis();pointerV15(down,MotionEvent.ACTION_DOWN,x,y);movesV15(down,x,y,x+area.width()*.7f,y,10);pointerV15(down,MotionEvent.ACTION_UP,x+area.width()*.7f,y);settleV15();
        uiAssert(()->{if(!"2000-03-05".equals(HomeCalendarDates.key(pagerV15(screen).currentDate()))||!"2000-03-05".equals(invoke(screen,"homeDate",new Class[]{})))throw new Exception("Day commit / reduced-motion commit differs from calendar");});
    }
    private FoodAnalysisJobService contextServiceV22(Context context)throws Exception {
        FoodAnalysisJobService service=new FoodAnalysisJobService();Method attach=ContextWrapper.class.getDeclaredMethod("attachBaseContext",Context.class);attach.setAccessible(true);attach.invoke(service,context);return service;
    }
    private JSONObject pendingItemV22(String source,String generation)throws Exception {
        return new JSONObject().put("id","qa-v22-item").put("analysisId",generation).put("sourcePhoto",source).put("dishName","待识别")
            .put("status","pending").put("analysisStatus","waiting_api").put("createdAt",System.currentTimeMillis());
    }
    private JSONObject seedPendingV22(Context c,android.content.SharedPreferences prefs,String source,JSONObject item)throws Exception {
        String date=HomeCalendarDates.key(Calendar.getInstance());JSONObject record=new JSONObject().put("date",date).put("meal","lunch").put("ownedSource",false).put("item",item);
        JSONObject job=new JSONObject().put("id",item.getString("id")).put("analysisId",item.getString("analysisId")).put("source",source).put("date",date).put("meal","lunch")
            .put("draft",true).put("interactive",true).put("cutoutDone",true).put("epoch",prefs.getLong("food_jobs_epoch",0)).put("attempts",0);
        prefs.edit().putString(FoodDraftStore.KEY,new JSONObject().put(source,record).toString()).putString(FoodAnalysisJobService.QUEUE,new JSONArray().put(job).toString()).commit();return job;
    }
    /** Exercise the production final-write gate; no inference, transport or scheduler is replaced. */
    private void stoppedCompletionGuardsV22(Context c,android.content.SharedPreferences prefs,JSONObject selected,JSONObject validated)throws Exception {
        String originalProfile=prefs.getString(NutritionStore.PROFILE,"{}"),originalDrafts=prefs.getString(FoodDraftStore.KEY,"{}"),originalQueue=prefs.getString(FoodAnalysisJobService.QUEUE,"[]");
        String originalDays=prefs.getString(NutritionStore.DAYS,"{}");long epoch=prefs.getLong("food_jobs_epoch",0);
        String source="qa_v22_completed_guard.jpg";FoodAnalysisJobService service=contextServiceV22(c);
        java.lang.reflect.Field stopped=FoodAnalysisJobService.class.getDeclaredField("stopped");stopped.setAccessible(true);
        try{
            JSONObject profile=new JSONObject(selected.toString());prefs.edit().putString(NutritionStore.PROFILE,profile.toString()).commit();
            JSONObject job=seedPendingV22(c,prefs,source,pendingItemV22(source,"qa-completed-guard-generation"));FoodRecognitionPolicy.Snapshot started=FoodRecognitionPolicy.capture(profile);
            JSONObject finalValue=new JSONObject(validated.toString()).put("recognitionSource",FoodRecognitionPolicy.source(profile)).put("status","completed").put("analysisStatus","completed");
            if(finalValue.optJSONObject("nutrients")==null)throw new Exception("Guard test requires already validated final nutrition");
            stopped.setBoolean(service,true);String untouched=prefs.getString(FoodDraftStore.KEY,"{}");
            if(Boolean.TRUE.equals(invokeAnyV20(service,"requestCurrent",new Class[]{JSONObject.class,FoodRecognitionPolicy.Snapshot.class},job,started)))throw new Exception("OS-stopped request still accepts preview/native work");
            if(!Boolean.TRUE.equals(invokeAnyV20(service,"resultCurrent",new Class[]{JSONObject.class,FoodRecognitionPolicy.Snapshot.class},job,started)))throw new Exception("OS stop discarded already validated current result");
            if(Boolean.TRUE.equals(invokeAnyV20(service,"update",new Class[]{JSONObject.class,JSONObject.class,FoodRecognitionPolicy.Snapshot.class},job,new JSONObject().put("dishName","must not publish stopped preview"),started))||!untouched.equals(prefs.getString(FoodDraftStore.KEY,"{}")))throw new Exception("OS stop accepted a provisional update");
            if(!Boolean.TRUE.equals(invokeAnyV20(service,"update",new Class[]{JSONObject.class,JSONObject.class,FoodRecognitionPolicy.Snapshot.class},job,finalValue,started)))throw new Exception("Current complete result was lost after OS stop and would require another paid request");
            String saved=prefs.getString(FoodDraftStore.KEY,"{}");
            for(String reason:new String[]{"source","route-generation","disabled","epoch","item-generation","held","cancelled"}){
                prefs.edit().putString(NutritionStore.PROFILE,profile.toString()).putString(FoodDraftStore.KEY,saved).putLong("food_jobs_epoch",epoch).commit();job.remove("recognitionHold");
                if("source".equals(reason)){JSONObject changed=new JSONObject(profile.toString()).put("foodRecognitionSource","removed-processor");prefs.edit().putString(NutritionStore.PROFILE,changed.toString()).commit();}
                else if("route-generation".equals(reason)){JSONObject changed=new JSONObject(profile.toString()).put("foodRecognitionGeneration",FoodRecognitionPolicy.generation(profile)+1);prefs.edit().putString(NutritionStore.PROFILE,changed.toString()).commit();}
                else if("disabled".equals(reason)){JSONObject changed=new JSONObject(profile.toString()).put("foodAutoAnalyze",false).put("foodPhotoConsent",false);prefs.edit().putString(NutritionStore.PROFILE,changed.toString()).commit();}
                else if("epoch".equals(reason))prefs.edit().putLong("food_jobs_epoch",epoch+1).commit();
                else if("item-generation".equals(reason)){JSONObject changed=new JSONObject(saved);changed.getJSONObject(source).getJSONObject("item").put("analysisId","qa-newer-generation");prefs.edit().putString(FoodDraftStore.KEY,changed.toString()).commit();}
                else if("held".equals(reason))job.put("recognitionHold",true);
                else if("cancelled".equals(reason))prefs.edit().putString(FoodDraftStore.KEY,"{}").commit();
                String before=prefs.getString(FoodDraftStore.KEY,"{}");
                if(Boolean.TRUE.equals(invokeAnyV20(service,"update",new Class[]{JSONObject.class,JSONObject.class,FoodRecognitionPolicy.Snapshot.class},job,new JSONObject(finalValue.toString()).put("dishName","forbidden "+reason),started))||!before.equals(prefs.getString(FoodDraftStore.KEY,"{}")))throw new Exception("OS-stopped completed result bypassed "+reason+" guard");
            }
            if(!originalDays.equals(prefs.getString(NutritionStore.DAYS,"{}")))throw new Exception("Draft completion guard implicitly changed diary");
        }finally{
            stopped.setBoolean(service,false);
            if(!prefs.edit().putString(NutritionStore.PROFILE,originalProfile).putString(FoodDraftStore.KEY,originalDrafts).putString(FoodAnalysisJobService.QUEUE,originalQueue).putLong("food_jobs_epoch",epoch).commit())throw new Exception("Unable to restore isolated final-write guard state");
        }
    }
    private void firstUseAndPolicyV22(MainActivity screen,Context c,android.content.SharedPreferences prefs,java.util.concurrent.atomic.AtomicInteger requests)throws Exception {
        uiAssert(()->{JSONObject local=(JSONObject)field(screen,"profile");local.put("foodRecognitionOnboardingSeen",false);invoke(screen,"saveProfile",new Class[]{});
            Object setup=invoke(screen,"recognitionSetup",new Class[]{});invokeAnyV20(setup,"choose",new Class[]{boolean.class},true);});settleV15();
        uiAssert(()->{View choice=dialogRootV15("food_recognition_choice");if(choice.findViewWithTag("food_source_api")==null||choice.findViewWithTag("food_source_offline")!=null)throw new Exception("First-use setup must offer only the configured API");choice.findViewWithTag("food_source_api").performClick();});settleV15();
        if(!dialogTextsV20(screen).contains("同意并配置"))throw new Exception("API bypassed explicit photo-upload/fee consent");tapDialogV20(screen,"取消");settleV15();
        JSONObject disabled=NutritionStore.read(prefs,NutritionStore.PROFILE);
        if(FoodRecognitionPolicy.enabled(disabled)||disabled.optBoolean("foodPhotoConsent")||requests.get()!=0)throw new Exception("Declining consent enabled recognition or network");
        uiAssert(()->{Object setup=invoke(screen,"recognitionSetup",new Class[]{});invokeAnyV20(setup,"choose",new Class[]{boolean.class},true);});settleV15();tapDialogV20(screen,"稍后设置");settleV15();
        if(!NutritionStore.read(prefs,NutritionStore.PROFILE).optBoolean("foodRecognitionOnboardingSeen"))throw new Exception("Later setup choice was not remembered");
        String source="qa_v22_waiting.jpg";layoutSource(c,source);
        JSONObject local=NutritionStore.read(prefs,NutritionStore.PROFILE).put("foodRecognitionSource","offline").put("foodOfflineAnalyze",true).put("foodRecognitionGeneration",7);
        prefs.edit().putString(NutritionStore.PROFILE,local.toString()).commit();JSONObject item=pendingItemV22(source,"qa-waiting-api"),job=seedPendingV22(c,prefs,source,item);
        if(FoodAnalysisJobService.enabled(local)||FoodAnalysisJobService.configured(c,local))throw new Exception("Raw retired route is allowed to send photos before Activity migration");
        if(!FoodRecognitionRetirement.apply(c))throw new Exception("Retired route upgrade did not migrate");
        JSONObject migrated=NutritionStore.read(prefs,NutritionStore.PROFILE);JSONArray migratedJobs=FoodAnalysisJobService.jobs(prefs);
        if(!"api".equals(migrated.optString("foodRecognitionSource"))||FoodRecognitionPolicy.enabled(migrated)||migrated.optBoolean("foodPhotoConsent")||migrated.optLong("foodRecognitionGeneration")!=8
                ||migratedJobs.length()!=1||!migratedJobs.getJSONObject(0).optBoolean("recognitionHold")
                ||!"awaiting_consent".equals(FoodDraftStore.item(c,source).optString("analysisStatus")))throw new Exception("Retired queue migration granted API consent, lost work or failed to hold old requests");
        // A control request exercises the real service's write gate without sending HTTP.
        FoodAnalysisJobService service=contextServiceV22(c);JSONObject api=new JSONObject(migrated.toString()).put("foodRecognitionSource","api").put("foodRecognitionGeneration",8)
                .put("foodAutoAnalyze",true).put("foodPhotoConsent",true).put("apiEnabled",true).put("apiEndpoint","https://food.qa.invalid/chat/completions").put("apiModel","qa-vision").put("apiKey","qa-not-a-real-key");
        prefs.edit().putString(NutritionStore.PROFILE,api.toString()).commit();FoodRecognitionPolicy.Snapshot started=FoodRecognitionPolicy.capture(api);
        JSONObject touch=new JSONObject().put("dishName","QA current guarded update");
        if(!Boolean.TRUE.equals(invokeAnyV20(service,"update",new Class[]{JSONObject.class,JSONObject.class,FoodRecognitionPolicy.Snapshot.class},job,touch,started)))throw new Exception("Guarded-write control did not accept a current request");
        JSONObject changed=new JSONObject(api.toString()).put("foodRecognitionSource","removed-processor").put("foodRecognitionGeneration",9).put("foodAutoAnalyze",false).put("foodPhotoConsent",false);
        prefs.edit().putString(NutritionStore.PROFILE,changed.toString()).commit();String before=FoodDraftStore.item(c,source).toString();
        if(Boolean.TRUE.equals(invokeAnyV20(service,"update",new Class[]{JSONObject.class,JSONObject.class,FoodRecognitionPolicy.Snapshot.class},job,new JSONObject().put("dishName","must not save stale source"),started))||!before.equals(FoodDraftStore.item(c,source).toString()))throw new Exception("Source change accepted a stale result");
        api.put("foodRecognitionGeneration",10);prefs.edit().putString(NutritionStore.PROFILE,api.toString()).commit();
        if(Boolean.TRUE.equals(invokeAnyV20(service,"update",new Class[]{JSONObject.class,JSONObject.class,FoodRecognitionPolicy.Snapshot.class},job,new JSONObject().put("dishName","must not save old generation"),started)))throw new Exception("Changing back revived an old generation");
        job.put("recognitionStarted",true).put("recognitionSource","api").put("recognitionGeneration",8);prefs.edit().putString(FoodAnalysisJobService.QUEUE,new JSONArray().put(job).toString()).putString(NutritionStore.PROFILE,changed.toString()).commit();
        FoodAnalysisJobService.recognitionSettingsChanged(c);JSONArray held=FoodAnalysisJobService.jobs(prefs);if(held.length()!=1||!held.getJSONObject(0).optBoolean("recognitionHold")||held.getJSONObject(0).optBoolean("recognitionStarted"))throw new Exception("In-flight request change was silently retried or lost");
        uiAssert(()->{setMainField(screen,"profile",changed);JSONObject portable=(JSONObject)invoke(screen,"cloudSafeProfile",new Class[]{});
            for(String key:new String[]{"foodRecognitionSource","foodRecognitionGeneration","foodRecognitionOnboardingSeen","foodAutoAnalyze","foodPhotoConsent","apiKey","apiEndpoint","apiModel"})if(portable.has(key))throw new Exception("Portable profile retained per-device recognition/consent: "+key);
            JSONObject restore=new JSONObject(changed.toString());invoke(screen,"stripApiProfileFields",new Class[]{JSONObject.class},restore);if(FoodRecognitionPolicy.enabled(restore))throw new Exception("Backup consent stripping enables API upload");
            invoke(screen,"renderFoodRecognitionSettingsPage",new Class[]{});assertSettingPresence(screen.getWindow().getDecorView(),"自动识别菜名和营养",true);
        });
        if(requests.get()!=0)throw new Exception("Setup/queue/policy test attempted HTTP(S)");FoodAnalysisJobService.cancelAll(c);FoodDraftStore.discard(c,Collections.singletonList(source));
    }
    private void revisionV22(Context c,android.content.SharedPreferences prefs)throws Exception {
        FoodAnalysisJobService.cancelAll(c);FoodDraftStore.discard(c,FoodDraftStore.pendingSources(c));java.util.concurrent.atomic.AtomicInteger requests=denyNetworkV22();
        JSONObject profile=new JSONObject().put("decoration","yellow").put("nightMode","true".equals(args.getString("night"))).put("foodRecognitionOnboardingSeen",true).put("autoCloudSync",false)
            .put("foodAutoAnalyze",false).put("foodPhotoConsent",false).put("apiEnabled",false).put("seenAnnouncementVersion",c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName);
        prefs.edit().putString(NutritionStore.PROFILE,profile.toString()).putString(NutritionStore.DAYS,"{}").remove("food_processing_preview").commit();
        MainActivity screen=(MainActivity)startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));gestureScreenV20=screen;settleV15();
        linkedProgressV22(screen,"2000-03-08","2000-03-07",false);linkedProgressV22(screen,"2000-03-06","2000-03-05",false);linkedProgressV22(screen,"2000-03-01","2000-02-29",true);
        realLinkedGestureV22(screen);uiAssert(()->assertRenderPure(screen,prefs,"{}"));
        JSONObject guardedApi=new JSONObject(profile.toString()).put("foodRecognitionSource","api").put("foodRecognitionGeneration",80).put("foodAutoAnalyze",true).put("foodPhotoConsent",true)
            .put("apiEnabled",true).put("apiEndpoint","https://food.qa.invalid/chat/completions").put("apiModel","qa-vision").put("apiKey","qa-not-a-real-key");
        stoppedCompletionGuardsV22(c,prefs,guardedApi,FoodAnalysisResult.parse(RESPONSE).value);
        boolean motionOnly="motion".equals(args.getString("phase"));if(!motionOnly)firstUseAndPolicyV22(screen,c,prefs,requests);
        if(requests.get()!=0)throw new Exception("Revision test attempted HTTP(S)");Bundle result=new Bundle();
        result.putString("stream","PASS: continuous before-half date pill, exact meal/calendar displacement, same-week/cross-week/cross-month transitions, reused calendar rows, reversal/cancel and final reduced-motion state; OS-stopped current complete result preserved, stopped previews / stale source / route generation / disabled consent / epoch / item generation / held request / cancelled draft all rejected; "+(motionOnly?"motion-only (consent/queue stages skipped)":"API-only setup and declined consent, retired-source queue migration without upload permission, real service stale source/generation guards, held requests and portable consent stripping")+". App-window pointer events: "+localPointerFallbacksV20+"; HTTP(S) attempts: "+requests.get()+".\n");finish(Activity.RESULT_OK,result);
    }
    private void onboardingV22(Context c,android.content.SharedPreferences prefs)throws Exception {
        FoodAnalysisJobService.cancelAll(c);FoodDraftStore.discard(c,FoodDraftStore.pendingSources(c));java.util.concurrent.atomic.AtomicInteger requests=denyNetworkV22();
        JSONObject profile=new JSONObject().put("decoration","yellow").put("foodRecognitionOnboardingSeen",false).put("seenAnnouncementVersion","")
            .put("foodAutoAnalyze",false).put("foodPhotoConsent",false).put("apiEnabled",false).put("autoCloudSync",false);
        prefs.edit().putString(NutritionStore.PROFILE,profile.toString()).putString(NutritionStore.DAYS,"{}").commit();
        MainActivity screen=(MainActivity)startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));gestureScreenV20=screen;Thread.sleep(1600);
        uiAssert(()->{View announcement=dialogRootV15("update_announcement");if(!visibleText(announcement).contains(c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName))throw new Exception("Real startup announcement missing current version");});
        tapDialogV20(screen,"我已了解");Thread.sleep(1200);
        uiAssert(()->{View choice=dialogRootV15("food_recognition_choice");if(choice.findViewWithTag("food_source_api")==null||choice.findViewWithTag("food_source_offline")!=null)throw new Exception("Automatic first-use setup must offer only API");choice.findViewWithTag("food_source_api").performClick();});settleV15();
        tapDialogV20(screen,"同意并配置");settleV15();tapDialogV20(screen,"取消");Thread.sleep(800);
        JSONObject after=NutritionStore.read(prefs,NutritionStore.PROFILE);
        if(FoodRecognitionPolicy.enabled(after)||after.optBoolean("foodPhotoConsent")||requests.get()!=0)throw new Exception("Cancelled initial API configuration enabled recognition or network");
        if(!after.optBoolean("foodRecognitionOnboardingSeen"))throw new Exception("First-use selection not remembered");
        Bundle result=new Bundle();result.putString("stream","PASS: actual onCreate current announcement -> confirmation -> automatic focus-restored API-only setup; accepting API consent but cancelling configuration does not authorize/enable recognition, upload. HTTP(S) attempts="+requests.get()+".\n");finish(Activity.RESULT_OK,result);
    }
    private void queuedSwipeV24(MainActivity screen,Rect area,boolean forward,float amount,boolean cancel)throws Exception{
        final float start=area.left+area.width()*(forward?.8f:.2f),end=start+(forward?-1:1)*area.width()*amount;
        final float y=area.top+Math.min(area.height()*.3f,PaperComponents.dp(screen,92));
        final long down=android.os.SystemClock.uptimeMillis();
        // Separate gestures must not rewind event time. Fixed hardware timestamps
        // keep a 120ms finger stream even when a host/UI callback takes longer.
        for(int i=0;i<=6;i++){
            long remaining=down+i*20-android.os.SystemClock.uptimeMillis();if(remaining>0)Thread.sleep(remaining);
            final int step=i;
            uiAssert(()->{int[] origin=new int[2];screen.getWindow().getDecorView().getLocationOnScreen(origin);int action=step==0?MotionEvent.ACTION_DOWN:step==6?(cancel?MotionEvent.ACTION_CANCEL:MotionEvent.ACTION_UP):MotionEvent.ACTION_MOVE;
                MotionEvent event=MotionEvent.obtain(down,down+step*20,action,start+(end-start)*step/6-origin[0],y-origin[1],0);event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
                try{screen.dispatchTouchEvent(event);}finally{event.recycle();}localPointerFallbacksV20++;
            });
        }
    }
    private String pagerStateV24(TodayDayPager pager)throws Exception{
        return "date="+HomeCalendarDates.key(pager.currentDate())+", visible="+ownField(pager,"visibleDate")+", offset="+offsetV15(pager)+", selection="+(ownField(pager,"selection")!=null)
            +", destination="+ownField(pager,"destination")+", dragging="+ownField(pager,"dragging")+", pointer="+ownField(pager,"pointer")+", spring="+((GestureSpring)ownField(pager,"spring")).isRunning();
    }
    private void pagerAtRestV24(MainActivity screen)throws Exception{
        TodayDayPager pager=pagerV15(screen);
        if(pager.isInteracting()||((Number)ownField(pager,"pointer")).intValue()!=-1||Math.abs(offsetV15(pager))>.5f||ownField(pager,"selection")!=null)
            throw new Exception("Date stream did not release ownership: "+pagerStateV24(pager));
        if(!HomeCalendarDates.key(pager.currentDate()).equals(invoke(screen,"homeDate",new Class[]{})))throw new Exception("Visible/committed date differ: "+pagerStateV24(pager));
    }
    private void continuousDatesV24(MainActivity screen,android.content.SharedPreferences prefs)throws Exception{
        String before=prefs.getString(NutritionStore.DAYS,"{}");
        for(int delay:new int[]{30,80,160})for(boolean forward:new boolean[]{true,false}){
            uiAssert(()->{setMainField(screen,"homeDateKey","2000-03-20");setMainField(screen,"homeCalendarExpanded",false);invoke(screen,"showTab",new Class[]{int.class,boolean.class},0,false);});settleV15();
            final TodayDayPager pager=pagerV15(screen);Rect area=screenRect(pager);
            for(int count=0;count<3;count++){queuedSwipeV24(screen,area,forward,.62f,false);Thread.sleep(delay);}
            settleV15();
            Calendar expected=new GregorianCalendar(2000,Calendar.MARCH,20);expected.add(Calendar.DATE,forward?3:-3);String wanted=HomeCalendarDates.key(expected);expected.add(Calendar.DATE,forward?-1:1);String two=HomeCalendarDates.key(expected);
            uiAssert(()->{pagerAtRestV24(screen);String actual=HomeCalendarDates.key(pager.currentDate());boolean three=wanted.equals(actual),continuous=two.equals(actual);
                if(!three&&(!android.animation.ValueAnimator.areAnimatorsEnabled()||!continuous))throw new Exception("Rapid sequence did not progress through two/three visible dates; delay="+delay+", forward="+forward+", "+pagerStateV24(pager));});
            expected=pager.currentDate();
            queuedSwipeV24(screen,area,forward,.62f,false);settleV15();expected.add(Calendar.DATE,forward?1:-1);String next=HomeCalendarDates.key(expected);
            uiAssert(()->{pagerAtRestV24(screen);if(!next.equals(HomeCalendarDates.key(pager.currentDate())))throw new Exception("Pager stuck after rapid sequence: "+pagerStateV24(pager));});
            queuedSwipeV24(screen,area,!forward,.62f,false);Thread.sleep(30);queuedSwipeV24(screen,area,!forward,.62f,false);settleV15();
            expected.add(Calendar.DATE,forward?-2:2);String reversed=HomeCalendarDates.key(expected);
            uiAssert(()->{pagerAtRestV24(screen);if(!reversed.equals(HomeCalendarDates.key(pager.currentDate())))throw new Exception("Rapid reverse did not recover; wanted="+reversed+", delay="+delay+", forward="+forward+", "+pagerStateV24(pager));});
            queuedSwipeV24(screen,area,forward,.2f,true);settleV15();uiAssert(()->pagerAtRestV24(screen));
        }
        uiAssert(()->{invoke(screen,"showTab",new Class[]{int.class,boolean.class},0,false);pagerV15(screen).animateToDate(new GregorianCalendar(1999,Calendar.NOVEMBER,3));});Thread.sleep(80);
        final View[] pinnedText={null};final Rect[] exposed={null};
        uiAssert(()->{for(View row:tagged(pagerV15(screen),"day_legacy_breakfast_")){View parent=(View)row.getParent();Rect visible=new Rect();if(parent.getGlobalVisibleRect(visible)&&visible.width()>2&&visible.height()>2){pinnedText[0]=parent;exposed[0]=visible;break;}}if(pinnedText[0]==null)throw new Exception("Far-date text target is not exposed");});
        long pinDown=android.os.SystemClock.uptimeMillis();pointerV15(pinDown,MotionEvent.ACTION_DOWN,exposed[0].exactCenterX(),exposed[0].exactCenterY());Thread.sleep(ViewConfiguration.getLongPressTimeout()+110);pointerV15(pinDown,MotionEvent.ACTION_UP,exposed[0].exactCenterX(),exposed[0].exactCenterY());settleV15();
        uiAssert(()->{pagerAtRestV24(screen);if(!"1999-11-03".equals(HomeCalendarDates.key(pagerV15(screen).currentDate()))||pinnedText[0].findViewWithTag("meal_text_delete").getVisibility()!=View.VISIBLE)throw new Exception("Long-pressed target text returned to its source date");invoke(screen,"exitMediaEditMode",new Class[]{boolean.class},false);});
        Rect area=screenRect(pagerV15(screen));queuedSwipeV24(screen,area,false,.72f,false);Thread.sleep(30);queuedSwipeV24(screen,area,false,.62f,false);settleV15();
        uiAssert(()->{pagerAtRestV24(screen);if(HomeCalendarDates.key(pagerV15(screen).currentDate()).compareTo("1999-11-03")>=0)throw new Exception("Date tap retained a finite two-endpoint trap: "+pagerStateV24(pagerV15(screen)));});
        String today=HomeCalendarDates.key(Calendar.getInstance());uiAssert(()->{setMainField(screen,"homeDateKey",today);invoke(screen,"showTab",new Class[]{int.class,boolean.class},0,false);});settleV15();
        area=screenRect(pagerV15(screen));queuedSwipeV24(screen,area,true,.62f,false);Thread.sleep(30);queuedSwipeV24(screen,area,true,.62f,false);settleV15();
        uiAssert(()->{pagerAtRestV24(screen);if(!today.equals(HomeCalendarDates.key(pagerV15(screen).currentDate())))throw new Exception("Future edge admitted a future diary");});
        queuedSwipeV24(screen,area,false,.62f,false);settleV15();uiAssert(()->{pagerAtRestV24(screen);if(today.equals(HomeCalendarDates.key(pagerV15(screen).currentDate())))throw new Exception("Future edge could not be grabbed back");assertRenderPure(screen,prefs,before);});
    }
    private void nonBlankContentV24(MainActivity screen,String phase)throws Exception{
        final Rect[] bounds={null};final int[] background={0};
        uiAssert(()->{ViewGroup content=(ViewGroup)field(screen,"content");if(content.getChildCount()==0)throw new Exception("Content lost every normal child: "+phase);
            Rect area=screenRect(content),nav=screenRect((View)field(screen,"navHost"));area.top+=PaperComponents.dp(screen,55);area.bottom=Math.min(area.bottom,nav.top-PaperComponents.dp(screen,84));
            bounds[0]=area;background[0]=UiTokens.paper(Boolean.TRUE.equals(invoke(screen,"nightDecoration",new Class[]{})));});
        Bitmap frame=getUiAutomation().takeScreenshot();if(frame==null)throw new Exception("No frame for quick navigation QA");
        try{Rect area=bounds[0];int ink=0,minX=area.right,maxX=area.left;int[] columns=new int[6];
            for(int y=Math.max(0,area.top);y<Math.min(frame.getHeight(),area.bottom);y+=3)for(int x=Math.max(0,area.left);x<Math.min(frame.getWidth(),area.right);x+=3){int c=frame.getPixel(x,y),b=background[0];if(Math.abs(Color.red(c)-Color.red(b))+Math.abs(Color.green(c)-Color.green(b))+Math.abs(Color.blue(c)-Color.blue(b))>75){ink++;minX=Math.min(minX,x);maxX=Math.max(maxX,x);columns[Math.min(columns.length-1,Math.max(0,(x-area.left)*columns.length/Math.max(1,area.width())))]++;}}
            int occupied=0;for(int count:columns)if(count>=8)occupied++;
            // A sliver of the previous Today page at one edge used to pass the
            // total-ink test while most of the viewport was empty. Root fixtures
            // have full-width date/section/card content: require visible spread.
            if(ink<45||occupied<3||maxX-minX<area.width()*.5f){saveFrameV24(frame,"navigation-gap-"+phase);throw new Exception("Quick bottom navigation lost broad content; phase="+phase+", ink="+ink+", columns="+Arrays.toString(columns)+", span="+(maxX-minX)+", bounds="+area);}
        }finally{frame.recycle();}
    }
    private void saveFrameV24(Bitmap frame,String phase)throws Exception{
        File dir=new File(getTargetContext().getExternalFilesDir(null),"qa");dir.mkdirs();
        String name=(args.getString("name","beta24")+"-"+phase).replaceAll("[^a-zA-Z0-9_.-]","_");
        try(OutputStream out=new FileOutputStream(new File(dir,name+".png"))){frame.compress(Bitmap.CompressFormat.PNG,100,out);}
    }
    private void bottomTabV24(MainActivity screen,int tab)throws Exception{
        final Rect[] target={null};uiAssert(()->{View item=((ViewGroup)field(screen,"navBar")).findViewWithTag(Integer.valueOf(tab));if(item==null||!item.isShown())throw new Exception("Missing visible bottom tab "+tab);target[0]=screenRect(item);});
        long down=android.os.SystemClock.uptimeMillis();pointerV15(down,MotionEvent.ACTION_DOWN,target[0].exactCenterX(),target[0].exactCenterY());pointerV15(down,MotionEvent.ACTION_UP,target[0].exactCenterX(),target[0].exactCenterY());
    }
    private void fullPeerFrameV24(View scratch,String phase)throws Exception{
        final Rect[] bounds={null};uiAssert(()->bounds[0]=screenRect(scratch));Bitmap frame=getUiAutomation().takeScreenshot();if(frame==null)throw new Exception("No hardware frame for peer coverage");
        try{Rect area=bounds[0];for(float y:new float[]{.2f,.5f,.8f})for(float x:new float[]{.08f,.25f,.5f,.75f,.92f}){
            int pixel=frame.getPixel(area.left+Math.round(area.width()*x),area.top+Math.round(area.height()*y));
            // Every fixture page is opaque and belongs to the same red family.
            // Interruption may blend colors but cannot expose the paper backing.
            if(Color.red(pixel)<130||Color.green(pixel)>110){saveFrameV24(frame,"peer-coverage-"+phase);throw new Exception("Peer transition exposed paper at "+x+","+y+" in "+phase+": "+Integer.toHexString(pixel));}
        }}finally{frame.recycle();}
    }
    private void interruptedPeerCoverageV24(MainActivity screen)throws Exception{
        throw new Exception("Retired PageCrossfade peer-navigation contract: use revision-v28 navigation or revision-v33, not interruptedPeerCoverageV24");
    }
    private void transitionOwnershipV24(MainActivity screen)throws Exception{
        if(!android.animation.ValueAnimator.areAnimatorsEnabled())return;
        uiAssert(()->{
            FrameLayout scratch=new FrameLayout(screen);ViewGroup root=(ViewGroup)field(screen,"root");root.addView(scratch,new ViewGroup.LayoutParams(600,600));scratch.measure(View.MeasureSpec.makeMeasureSpec(600,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(600,View.MeasureSpec.EXACTLY));scratch.layout(0,0,600,600);
            View old=new View(screen);old.setBackgroundColor(Color.RED);scratch.addView(old,new FrameLayout.LayoutParams(-1,-1));old.layout(0,0,600,600);
            PageCrossfade transition=new PageCrossfade(scratch);Runnable next=()->{scratch.removeAllViews();View page=new View(screen);page.setBackgroundColor(Color.BLUE);scratch.addView(page,new FrameLayout.LayoutParams(-1,-1));};
            transition.run(next,1);ViewTreeObserver.OnPreDrawListener obsolete=(ViewTreeObserver.OnPreDrawListener)ownField(transition,"pending");transition.run(next,-1);
            Object owner=ownField(transition,"image"),pending=ownField(transition,"pending");obsolete.onPreDraw();
            if(owner!=ownField(transition,"image")||pending!=ownField(transition,"pending"))throw new Exception("Obsolete preDraw stole the new transition");transition.clear();scratch.removeAllViews();
            View unavailable=new View(screen){@Override public void draw(Canvas canvas){if(!canvas.isHardwareAccelerated())throw new IllegalStateException("QA unavailable software snapshot");super.draw(canvas);}};unavailable.setBackgroundColor(Color.RED);scratch.addView(unavailable,new FrameLayout.LayoutParams(-1,-1));unavailable.layout(0,0,600,600);
            transition.run(next,1);transition.run(next,-1);
            if(transition.active()||scratch.getChildCount()!=1||scratch.getChildAt(0).getTranslationX()!=0||scratch.getChildAt(0).getAlpha()!=1)throw new Exception("Failed capture retained an off-screen/blank destination");transition.clear();root.removeView(scratch);
        });settleV15();
    }
    private void rapidNavigationV24(MainActivity screen)throws Exception{
        throw new Exception("Retired PageCrossfade peer-navigation contract: use revision-v28 navigation or revision-v33, not rapidNavigationV24");
    }
    private void textDeleteV24(MainActivity screen,Context c,android.content.SharedPreferences prefs,String today)throws Exception{
        final View[] frame={null};final String text="鸡蛋面\n加一份青菜";
        uiAssert(()->{JSONObject latest=NutritionStore.read(prefs,NutritionStore.DAYS),day=latest.getJSONObject(today);day.remove("breakfast");day.remove("afternoon_tea");day.remove("snack");day.put("lunch",new JSONObject().put("text",text).put("manualTextEntry",true));prefs.edit().putString(NutritionStore.DAYS,latest.toString()).commit();setMainField(screen,"days",latest);setMainField(screen,"homeDateKey",today);invoke(screen,"showTab",new Class[]{int.class,boolean.class},0,false);});settleV15();
        uiAssert(()->{List<View> labels=tagged(screen.getWindow().getDecorView(),"day_legacy_lunch_");if(labels.size()!=1)throw new Exception("No text-only lunch component");frame[0]=(View)labels.get(0).getParent();frame[0].requestRectangleOnScreen(new Rect(0,0,frame[0].getWidth(),frame[0].getHeight()),false);});settleV15();
        Rect hit=screenRect(frame[0]);long down=android.os.SystemClock.uptimeMillis();pointerV15(down,MotionEvent.ACTION_DOWN,hit.exactCenterX(),hit.exactCenterY());Thread.sleep(ViewConfiguration.getLongPressTimeout()+110);pointerV15(down,MotionEvent.ACTION_UP,hit.exactCenterX(),hit.exactCenterY());
        uiAssert(()->{View row=tagged(frame[0],"day_legacy_lunch_").get(0);
            View label=row.findViewWithTag("day_entry_label"),close=frame[0].findViewWithTag("meal_text_delete");Rect body=screenRect(frame[0]),title=screenRect(label),button=screenRect(close);
            if(close==null||close.getVisibility()!=View.VISIBLE||Math.abs(body.exactCenterY()-title.exactCenterY())>2||frame[0].getRotation()!=0||button.right>body.right+1||button.top<body.top-1)throw new Exception("Text selection/delete is off-center or rotates like a photo");
            if(row.isFocusable()||row.getImportantForAccessibility()!=View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS||frame[0].getImportantForAccessibility()!=View.IMPORTANT_FOR_ACCESSIBILITY_YES)throw new Exception("Text edit affordance retained a duplicate inaccessible row");
            if(visibleText(frame[0]).contains("↺"))throw new Exception("Text gained a rotation control");
        });screenshot(args.getString("name","beta24")+"-text-selected");
        String before=prefs.getString(NutritionStore.DAYS,"{}");uiAssert(()->frame[0].findViewWithTag("meal_text_delete").performClick());settleV15();tapDialogV20(screen,"取消");settleV15();
        if(!before.equals(prefs.getString(NutritionStore.DAYS,"{}")))throw new Exception("Cancelling text deletion changed the diary");
        uiAssert(()->{JSONObject latest=NutritionStore.read(prefs,NutritionStore.DAYS),meal=latest.getJSONObject(today).getJSONObject("lunch");meal.put("workerMarker",24).put("manualCalories",123).put("photos",new JSONArray().put("fresh-worker-photo.jpg")).put("foodItems",new JSONArray().put(new JSONObject().put("id","fresh-worker-food").put("nutrients",new JSONObject().put("caloriesKcal",456))));prefs.edit().putString(NutritionStore.DAYS,latest.toString()).commit();frame[0].findViewWithTag("meal_text_delete").performClick();});settleV15();tapDialogV20(screen,"删除");settleV15();
        JSONObject meal=NutritionStore.meal(NutritionStore.read(prefs,NutritionStore.DAYS),today,"lunch");
        if(meal.has("text")||meal.has("manualTextEntry")||meal.optInt("workerMarker")!=24||meal.optInt("manualCalories")!=123||meal.getJSONArray("photos").length()!=1||meal.getJSONArray("foodItems").getJSONObject(0).getJSONObject("nutrients").getInt("caloriesKcal")!=456)throw new Exception("Text deletion lost fresh image/nutrition/manual data");
        uiAssert(()->{JSONObject latest=NutritionStore.read(prefs,NutritionStore.DAYS);latest.getJSONObject(today).getJSONObject("lunch").put("text","更新后的文字");prefs.edit().putString(NutritionStore.DAYS,latest.toString()).commit();if(Boolean.TRUE.equals(invoke(screen,"deleteMealText",new Class[]{String.class,String.class,String.class},today,"lunch",text)))throw new Exception("Stale delete removed newer text");});
    }
    private List<View> bookOpenLayersV25(MainActivity screen)throws Exception {
        List<View> views=new ArrayList<>(),result=new ArrayList<>();collectViews((View)field(screen,"root"),views);
        for(View view:views)if(view.getClass().getSimpleName().contains("BookOpen"))result.add(view);
        return result;
    }
    private void bookCardTapV25(MainActivity screen)throws Exception {
        final Rect[] hit={null};uiAssert(()->{
            List<View> covers=tagged(screen.getWindow().getDecorView(),"footprint_plain_cover");
            for(View cover:covers){Rect visible=new Rect();if(cover.getGlobalVisibleRect(visible)&&visible.width()>12&&visible.height()>12){hit[0]=visible;break;}}
            if(hit[0]==null)throw new Exception("No visible notebook cover to open");
        });
        long down=android.os.SystemClock.uptimeMillis();pointerV15(down,MotionEvent.ACTION_DOWN,hit[0].exactCenterX(),hit[0].exactCenterY());pointerV15(down,MotionEvent.ACTION_UP,hit[0].exactCenterX(),hit[0].exactCenterY());
    }
    /** Visual extent contract: paper back must meet the same top/bottom as its sheet. */
    private void notebookEdgeGeometryV25(MainActivity screen)throws Exception {
        uiAssert(()->{
            final int width=600,height=900,pad=24;View sheet=new View(screen);sheet.setBackgroundColor(0xffc32f34);
            sheet.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));sheet.layout(0,0,width,height);
            NotebookTurnRenderer renderer=new NotebookTurnRenderer(sheet,new RectF(pad,pad,pad+width,pad+height),0xff70b878,0xff427d49);
            Bitmap frame=Bitmap.createBitmap(width+pad*2,height+pad*2,Bitmap.Config.ARGB_8888);
            try{for(boolean forward:new boolean[]{true,false})for(float progress:new float[]{.2f,.5f,.8f}){
                frame.eraseColor(0xff15233d);renderer.draw(new Canvas(frame),progress,forward);
                float t=forward?progress:1-progress,fold=width*(1-t);int sampleX=pad+Math.max(1,Math.round(fold)-18),first=height,last=-1;
                for(int y=0;y<height;y++){int pixel=frame.getPixel(sampleX,pad+y);if(Color.alpha(pixel)>240&&Color.green(pixel)>Color.red(pixel)+15&&Color.green(pixel)>Color.blue(pixel)+15){first=Math.min(first,y);last=y;}}
                // Corners stay rounded; the sample is beside the crease and away from the outer corner.
                if(first>6||last<height-7){saveFrameV24(frame,"notebook-edge-"+forward+"-"+progress);throw new Exception("Turning paper back is shorter than its sheet: forward="+forward+", progress="+progress+", first="+first+", last="+last+", height="+height);}
            }}finally{renderer.release();frame.recycle();}
        });
    }
    private void notebookOpeningV25(MainActivity screen,android.content.SharedPreferences prefs,String before,String name)throws Exception {
        uiAssert(()->invoke(screen,"showTab",new Class[]{int.class,boolean.class},1,false));settleV15();
        bookCardTapV25(screen);Thread.sleep(UiTokens.BOOK_OPEN_MS*15/100);
        uiAssert(()->{if(!Boolean.TRUE.equals(field(screen,"inBookDetail")))throw new Exception("Cover tap did not enter notebook");if(bookOpenLayersV25(screen).size()>1)throw new Exception("Opening created duplicate cover layers");});
        screenshotNowV15(name+"-book-open-early");Thread.sleep(UiTokens.BOOK_OPEN_MS*40/100);screenshotNowV15(name+"-book-open-middle");Thread.sleep(UiTokens.BOOK_OPEN_MS*30/100);screenshotNowV15(name+"-book-open-late");settleV15();
        uiAssert(()->{if(!bookOpenLayersV25(screen).isEmpty())throw new Exception("Completed opening retained a blocking cover layer");View frame=(View)field(screen,"bookDetailSwipeFrame"),paper=frame.findViewWithTag("book_detail_notebook_page");if(paper==null||paper.getWidth()<=0||paper.getHeight()<=0||paper.getScaleX()!=1||paper.getScaleY()!=1||paper.getAlpha()!=1)throw new Exception("Opening did not land on full-size opaque paper");assertRenderPure(screen,prefs,before);});
        screenshotNowV15(name+"-book-open-rest");
        // Interrupt through the real public navigation path; an old overlay must not block the next page.
        uiAssert(()->invoke(screen,"showTab",new Class[]{int.class,boolean.class},1,false));settleV15();bookCardTapV25(screen);Thread.sleep(55);
        uiAssert(()->invoke(screen,"showTab",new Class[]{int.class,boolean.class},0,true));Thread.sleep(40);
        uiAssert(()->{if(!bookOpenLayersV25(screen).isEmpty())throw new Exception("Leaving notebook while opening retained its cover overlay");});settleV15();
        uiAssert(()->{if(((Integer)field(screen,"currentTab"))!=0||Boolean.TRUE.equals(field(screen,"inBookDetail"))||!bookOpenLayersV25(screen).isEmpty())throw new Exception("Stale opening callback restored notebook after leaving");assertRenderPure(screen,prefs,before);});
        uiAssert(()->invoke(screen,"showTab",new Class[]{int.class,boolean.class},1,false));settleV15();bookCardTapV25(screen);Thread.sleep(55);
        uiAssert(()->invoke(screen,"closeBookDetail",new Class[]{}));settleV15();
        uiAssert(()->{if(Boolean.TRUE.equals(field(screen,"inBookDetail"))||!bookOpenLayersV25(screen).isEmpty())throw new Exception("Closing during opening left a cover overlay or stale detail state");assertRenderPure(screen,prefs,before);});
    }
    /** Samples actual presented root transforms and bottom selection on every Choreographer frame. */
    private final class NavigationFramesV26 implements Choreographer.FrameCallback {
        final MainActivity screen;final StringBuilder trace=new StringBuilder("frame,from,to,progress,rest,travel,indicator,expected\n");
        boolean running=true;Throwable failure;int samples;
        NavigationFramesV26(MainActivity screen){this.screen=screen;}
        @Override public void doFrame(long time){
            if(!running)return;
            try{if(peerIndicatorV26(screen,trace,time))samples++;}catch(Throwable error){if(failure==null)failure=error;}
            if(running)Choreographer.getInstance().postFrameCallback(this);
        }
    }
    private float peerMetricV26(PageCrossfade transition,String method,float fallback)throws Exception {
        try{Method getter=PageCrossfade.class.getDeclaredMethod(method);getter.setAccessible(true);return ((Number)getter.invoke(transition)).floatValue();}
        catch(NoSuchMethodException missing){return fallback;}
    }
    private float navCenterV26(MainActivity screen,int tab)throws Exception {
        ViewGroup nav=(ViewGroup)field(screen,"navBar");View item=nav.findViewWithTag(Integer.valueOf(tab));View indicator=(View)field(screen,"navIndicator");
        if(item==null||item.getWidth()<=0)throw new Exception("No measured bottom item for tab "+tab);
        return item.getLeft()+(item.getWidth()-indicator.getLayoutParams().width)/2f;
    }
    /** Returns true only for a real intermediate sheet/indicator frame, never an idle final position. */
    private boolean peerIndicatorV26(MainActivity screen,StringBuilder trace,long stamp)throws Exception {
        throw new Exception("Retired PageCrossfade peer-navigation contract: use revision-v28 navigation or revision-v33, not peerIndicatorV26");
    }
    private void rootAtRestV26(MainActivity screen,int expected)throws Exception {
        RootPagePager pager=(RootPagePager)field(screen,"rootPager");
        if(pager==null)throw new Exception("Current root pager is missing");
        rootRestV28(screen,pager,new View[]{pager.page(0),pager.page(1),pager.page(2)},expected==3?2:expected);
    }
    private float[] rootPointsV26(MainActivity screen,boolean forward)throws Exception {
        final float[][] result={null};uiAssert(()->{Rect area=screenRect((View)field(screen,"content"));int tab=((Number)field(screen,"currentTab")).intValue();
            float x=area.left+area.width()*(forward?.8f:.2f),y=tab==0?area.top+PaperComponents.dp(screen,42):area.top+area.height()*.62f;
            result[0]=new float[]{x,y,x+(forward?-1:1)*area.width()*.64f,area.width()};});return result[0];
    }
    /** A short complete MainActivity window stream. The caller decides when the NEXT gesture begins. */
    private void queuedRootV26(MainActivity screen,boolean forward,int terminal,boolean verify)throws Exception {
        throw new Exception("Retired PageCrossfade peer-navigation contract: use revision-v28 navigation or revision-v33, not queuedRootV26");
    }
    /** Android may coalesce the final MOVE: UP itself must publish the finger's last position. */
    private void rootWithoutTerminalMoveV26(MainActivity screen,boolean forward,int pauseMs)throws Exception {
        throw new Exception("Retired PageCrossfade peer-navigation contract: use revision-v28 navigation or revision-v33, not rootWithoutTerminalMoveV26");
    }
    private void rootMultiV26(MainActivity screen,long down,int action,float x,float y)throws Exception {
        uiAssert(()->{int[] origin=new int[2];screen.getWindow().getDecorView().getLocationOnScreen(origin);MotionEvent.PointerProperties[] properties=new MotionEvent.PointerProperties[2];MotionEvent.PointerCoords[] coords=new MotionEvent.PointerCoords[2];
            for(int i=0;i<2;i++){properties[i]=new MotionEvent.PointerProperties();properties[i].id=i;properties[i].toolType=MotionEvent.TOOL_TYPE_FINGER;coords[i]=new MotionEvent.PointerCoords();coords[i].x=x-origin[0]+i*24;coords[i].y=y-origin[1]+i*12;coords[i].pressure=1;coords[i].size=1;}
            MotionEvent event=MotionEvent.obtain(down,android.os.SystemClock.uptimeMillis(),action,2,properties,coords,0,0,1,1,0,0,InputDevice.SOURCE_TOUCHSCREEN,0);try{screen.dispatchTouchEvent(event);localPointerFallbacksV20++;}finally{event.recycle();}});
    }
    private void navigationV26(MainActivity screen,android.content.SharedPreferences prefs,String before,String name)throws Exception {
        throw new Exception("Retired PageCrossfade peer-navigation contract: use revision-v28 navigation or revision-v33, not navigationV26");
    }
    private static final int[] MEDIA_COLORS_V26={0xffed2657,0xff16b73f,0xff2864e8,0xffe5b21b,0xff16bcc8,0xffb836db};
    /** Six local colored subjects make empty frames distinguishable from correctly decoded legacy/new media. */
    private JSONObject mediaFixtureV26(Context c,String date)throws Exception {
        String[] files=new String[6];for(int i=0;i<6;i++){files[i]="qa_v26_subject_"+i+".png";Bitmap image=Bitmap.createBitmap(160,160,Bitmap.Config.ARGB_8888);image.eraseColor(Color.TRANSPARENT);Canvas canvas=new Canvas(image);Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setColor(MEDIA_COLORS_V26[i]);canvas.drawRoundRect(new RectF(12,12,148,148),22,22,paint);try(OutputStream out=new FileOutputStream(new File(c.getFilesDir(),files[i]))){if(!image.compress(Bitmap.CompressFormat.PNG,100,out))throw new Exception("Cannot write media fixture");}finally{image.recycle();}}
        JSONObject food=new JSONObject().put("id","qa-v26-food").put("dishName","测试餐").put("sourcePhoto",files[1]).put("cutoutFile",files[0]).put("status","completed").put("analysisStatus","completed");
        JSONObject meal=new JSONObject().put("foodItems",new JSONArray().put(food)).put("stickers",new JSONArray().put(files[0]).put(files[2]).put(files[3])).put("photos",new JSONArray().put(files[1]).put(files[4]).put(files[5]));
        // 0 is merged food alpha (1 is represented source), 2/3 old stickers, 4/5 old photos; one extra old sticker = six.
        String sixth="qa_v26_legacy_extra.png";try(InputStream in=new FileInputStream(new File(c.getFilesDir(),files[1]));OutputStream out=new FileOutputStream(new File(c.getFilesDir(),sixth))){byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);}meal.getJSONArray("stickers").put(sixth);
        JSONObject day=new JSONObject().put("breakfast",meal);if(NotebookMediaEntries.collect(day,true).size()!=6)throw new Exception("Six-media fixture projection is not six");return new JSONObject().put(date,day);
    }
    private List<android.widget.ImageView> boardImagesV26(View paper)throws Exception {
        View board=paper.findViewWithTag("notebook_media_board");if(board==null)throw new Exception("Notebook has no media board");List<View> views=new ArrayList<>();collectViews(board,views);List<android.widget.ImageView> images=new ArrayList<>();for(View view:views)if(view instanceof android.widget.ImageView)images.add((android.widget.ImageView)view);if(images.size()!=6)throw new Exception("Notebook should display six projected media, got "+images.size());return images;
    }
    private int subjectCountV26(Bitmap image,int color){int count=0;for(int y=0;y<image.getHeight();y+=2)for(int x=0;x<image.getWidth();x+=2){int p=image.getPixel(x,y);if(Color.alpha(p)>220&&Math.abs(Color.red(p)-Color.red(color))<22&&Math.abs(Color.green(p)-Color.green(color))<22&&Math.abs(Color.blue(p)-Color.blue(color))<22)count++;}return count;}
    private void assertSubjectsV26(Bitmap image,String location)throws Exception {for(int color:MEDIA_COLORS_V26){int count=subjectCountV26(image,color);if(count<35)throw new Exception("Decoded subject missing from "+location+": color="+Integer.toHexString(color)+", pixels="+count);}}
    private Bitmap renderViewV26(View view){Bitmap image=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);view.draw(new Canvas(image));return image;}
    private void mediaLoadedV26(View paper)throws Exception {
        for(android.widget.ImageView image:boardImagesV26(paper))if(!(image.getDrawable() instanceof android.graphics.drawable.BitmapDrawable)||((android.graphics.drawable.BitmapDrawable)image.getDrawable()).getBitmap().isRecycled())throw new Exception("Notebook still has an empty/dead media frame after loading");
    }
    private void waitMediaV26(View paper)throws Exception {
        long limit=android.os.SystemClock.elapsedRealtime()+8000;Throwable[] error={null};do{uiAssert(()->{try{mediaLoadedV26(paper);error[0]=null;}catch(Throwable missing){error[0]=missing;}});if(error[0]==null)return;Thread.sleep(25);}while(android.os.SystemClock.elapsedRealtime()<limit);throw new Exception("Six notebook thumbnails never completed",error[0]);
    }
    private void notebookMediaV26(MainActivity screen,android.content.SharedPreferences prefs,String before,String name)throws Exception {
        final boolean animated=android.animation.ValueAnimator.areAnimatorsEnabled();final Object[] opening={null};final android.animation.ValueAnimator[] animator={null};final Bitmap[] texture={null};final int[] generation={0};final View[] paper={null};
        final java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(3),release=new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.ExecutorService pool=(java.util.concurrent.ExecutorService)field(screen,"mediaThumbnailExecutor");
        uiAssert(()->invoke(screen,"showTab",new Class[]{int.class,boolean.class},1,false));settleV15();
        for(int i=0;i<3;i++)pool.execute(()->{entered.countDown();try{release.await(20,java.util.concurrent.TimeUnit.SECONDS);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}});
        if(!entered.await(5,java.util.concurrent.TimeUnit.SECONDS)){release.countDown();throw new Exception("Cannot hold all three real thumbnail workers");}
        try{
            uiAssert(()->((android.util.LruCache<?,?>)field(screen,"mediaThumbnailCache")).evictAll());
            bookCardTapV25(screen);long limit=android.os.SystemClock.elapsedRealtime()+3000;boolean[] ready={false};
            do{uiAssert(()->{
                if(!Boolean.TRUE.equals(field(screen,"inBookDetail")))return;
                if(!animated){paper[0]=((View)field(screen,"bookDetailSwipeFrame")).findViewWithTag("book_detail_notebook_page");ready[0]=paper[0]!=null;return;}
                Object overlay=field(screen,"bookOpeningOverlay");if(overlay==null)return;Object value=ownField(overlay,"animator");if(!(value instanceof android.animation.ValueAnimator))return;
                opening[0]=overlay;animator[0]=(android.animation.ValueAnimator)value;animator[0].pause();animator[0].setCurrentPlayTime(animator[0].getDuration()*3/4);
                texture[0]=(Bitmap)ownField(overlay,"pageImage");paper[0]=(View)ownField(overlay,"paper");if(texture[0]==null||texture[0].isRecycled())throw new Exception("Book opening has no allocated paper texture");generation[0]=texture[0].getGenerationId();ready[0]=true;
            });if(ready[0])break;Thread.sleep(5);}while(android.os.SystemClock.elapsedRealtime()<limit);
            if(!ready[0]||paper[0]==null)throw new Exception(animated?"No actual BookOpen overlay/animator was observed on real cover tap":"Book did not open with system animations disabled");
            uiAssert(()->{for(android.widget.ImageView image:boardImagesV26(paper[0]))if(image.getDrawable()!=null)throw new Exception("Cold delayed fixture unexpectedly hit a loaded thumbnail");if(animated)for(int color:MEDIA_COLORS_V26)if(subjectCountV26(texture[0],color)>8)throw new Exception("Blocked media already appears in cold opening texture");});
            screenshotNowV15(name+"-book-delayed-before");release.countDown();waitMediaV26(paper[0]);Thread.sleep(40);
            if(animated){
                limit=android.os.SystemClock.elapsedRealtime()+3000;boolean[] repainted={false};do{uiAssert(()->{if(field(screen,"bookOpeningOverlay")!=opening[0])throw new Exception("Opening overlay ended before delayed subject verification");Bitmap current=(Bitmap)ownField(opening[0],"pageImage");if(current!=texture[0])throw new Exception("Delayed thumbnails replaced rather than repainted the opening texture");repainted[0]=current.getGenerationId()!=generation[0];});if(repainted[0])break;Thread.sleep(18);}while(android.os.SystemClock.elapsedRealtime()<limit);
                if(!repainted[0])throw new Exception("Real thumbnail completion did not refresh opening texture");
                uiAssert(()->{mediaLoadedV26(paper[0]);assertSubjectsV26(texture[0],"same opening texture");Bitmap drawn=renderViewV26((View)opening[0]);try{assertSubjectsV26(drawn,"actual opening overlay draw");saveFrameV24(drawn,name+"-book-delayed-overlay");}finally{drawn.recycle();}assertRenderPure(screen,prefs,before);});
                Thread.sleep(45);Bitmap presented=getUiAutomation().takeScreenshot();if(presented==null)throw new Exception("Cannot inspect actual HWUI opening presentation");try{assertSubjectsV26(presented,"actual HWUI opening screenshot");saveFrameV24(presented,name+"-book-delayed-presented");}finally{presented.recycle();}
                uiAssert(()->animator[0].resume());
            }
            settleV15();uiAssert(()->{if(!bookOpenLayersV25(screen).isEmpty())throw new Exception("Finished opening retained its overlay");mediaLoadedV26(paper[0]);Bitmap live=renderViewV26(paper[0]);try{assertSubjectsV26(live,"settled live notebook");saveFrameV24(live,name+"-book-six-live");}finally{live.recycle();}if(paper[0].getAlpha()!=1)throw new Exception("Finished opening left its paper hidden");assertRenderPure(screen,prefs,before);});
            screenshotNowV15(name+"-book-six-rest");
        }finally{release.countDown();uiAssert(()->{if(animator[0]!=null&&animator[0].isPaused())animator[0].resume();});}
        // Reopening must synchronously capture the already decoded six subjects as well.
        uiAssert(()->invoke(screen,"showTab",new Class[]{int.class,boolean.class},1,false));settleV15();bookCardTapV25(screen);
        if(animated){final boolean[] warm={false};long limit=android.os.SystemClock.elapsedRealtime()+2500;do{uiAssert(()->{Object overlay=field(screen,"bookOpeningOverlay");if(overlay==null)return;Bitmap page=(Bitmap)ownField(overlay,"pageImage");if(page==null)return;assertSubjectsV26(page,"warm opening texture");warm[0]=true;});if(warm[0])break;Thread.sleep(5);}while(android.os.SystemClock.elapsedRealtime()<limit);if(!warm[0])throw new Exception("Warm reopen never presented its real opening texture");}
        settleV15();uiAssert(()->{View frame=(View)field(screen,"bookDetailSwipeFrame"),live=frame.findViewWithTag("book_detail_notebook_page");mediaLoadedV26(live);Bitmap image=renderViewV26(live);try{assertSubjectsV26(image,"warm settled notebook");}finally{image.recycle();}assertRenderPure(screen,prefs,before);});
        notebookCurlMediaV26(screen,prefs,before,name);
        notebookOpeningV25(screen,prefs,before,name+"-cleanup");
    }
    private static final class CurlSubjectV26 {
        final Rect frameBlock,screenBlock;final int color;
        CurlSubjectV26(Rect frameBlock,Rect screenBlock,int color){this.frameBlock=frameBlock;this.screenBlock=screenBlock;this.color=color;}
    }
    /** Sample an interior block that the real folded paper exposes, never its opaque back. */
    private List<CurlSubjectV26> visibleCurlSubjectsV26(View frame,View paper,Object renderer,StringBuilder geometry)throws Exception {
        Bitmap front=(Bitmap)ownField(renderer,"front");RectF bounds=(RectF)ownField(renderer,"bounds");
        float progress=((Number)ownField(frame,"flipProgress")).floatValue();int offset=((Number)ownField(frame,"offset")).intValue();
        if(offset!=-1)throw new Exception("Expected the incoming backward sheet, offset="+offset);
        float tip=(2*progress-1)*front.getWidth();int safe=PaperComponents.dp(frame.getContext(),20),radius=Math.max(3,PaperComponents.dp(frame.getContext(),3));
        Rect screen=screenRect(frame);List<CurlSubjectV26> subjects=new ArrayList<>();
        geometry.append("progress=").append(progress).append(", offset=").append(offset).append(", travel=").append(ownField(frame,"travel"))
            .append(", texture=").append(front.getWidth()).append('x').append(front.getHeight()).append(", bounds=").append(bounds)
            .append(", tip=").append(tip).append(", safe=").append(safe).append(", frameScreen=").append(screen).append('\n');
        int index=0;for(android.widget.ImageView image:boardImagesV26(paper)){
            Rect block=new Rect(image.getWidth()/2-radius,image.getHeight()/2-radius,image.getWidth()/2+radius+1,image.getHeight()/2+radius+1);
            ((ViewGroup)paper).offsetDescendantRectToMyCoords(image,block);
            if(block.left<safe||block.right>=tip-safe||block.top<safe||block.bottom>=front.getHeight()-safe){index++;continue;}
            if(image.getRotation()!=0||image.getScaleX()!=1||image.getScaleY()!=1)throw new Exception("Curl fixture gained a transformed subject: "+index);
            Bitmap bitmap=((android.graphics.drawable.BitmapDrawable)image.getDrawable()).getBitmap();int color=bitmap.getPixel(bitmap.getWidth()/2,bitmap.getHeight()/2);boolean fixture=false;
            for(int expected:MEDIA_COLORS_V26)fixture|=color==expected;if(!fixture)throw new Exception("Curl subject center is not a fixture color: "+Integer.toHexString(color));
            Rect local=new Rect(block);local.offset(Math.round(bounds.left),Math.round(bounds.top));Rect global=new Rect(local);global.offset(screen.left,screen.top);
            subjects.add(new CurlSubjectV26(local,global,color));geometry.append("subject=").append(index).append(", color=").append(Integer.toHexString(color)).append(", paperBlock=").append(block).append(", frameBlock=").append(local).append(", screenBlock=").append(global).append('\n');index++;
        }
        if(subjects.isEmpty())throw new Exception("Curl drag exposes no safe subject block; "+geometry);return subjects;
    }
    private void assertCurlSubjectsV26(Bitmap image,List<CurlSubjectV26> subjects,boolean screen,boolean present,String location,String geometry)throws Exception {
        for(CurlSubjectV26 subject:subjects){Rect block=screen?subject.screenBlock:subject.frameBlock;
            if(block.left<0||block.top<0||block.right>image.getWidth()||block.bottom>image.getHeight())throw new Exception("Curl subject outside "+location+": "+block+"; "+geometry);
            int count=0;for(int y=block.top;y<block.bottom;y++)for(int x=block.left;x<block.right;x++){int pixel=image.getPixel(x,y);
                if(Color.alpha(pixel)>220&&Math.abs(Color.red(pixel)-Color.red(subject.color))<22&&Math.abs(Color.green(pixel)-Color.green(subject.color))<22&&Math.abs(Color.blue(pixel)-Color.blue(subject.color))<22)count++;}
            int required=(int)Math.ceil(block.width()*block.height()*.8);if(present?count<required:count!=0)throw new Exception("Curl subject "+(present?"missing after load":"visible before load")+" in "+location+": color="+Integer.toHexString(subject.color)+", pixels="+count+", required="+(present?required:0)+", block="+block+"; "+geometry);
        }
    }
    /** Exercise the incoming folded sheet, whose bitmap previously froze before async media arrived. */
    private void notebookCurlMediaV26(MainActivity screen,android.content.SharedPreferences prefs,String before,String name)throws Exception {
        Calendar date=Calendar.getInstance();date.add(Calendar.DATE,-1);String yesterday=HomeCalendarDates.key(date);
        uiAssert(()->invoke(screen,"renderBookDetail",new Class[]{String.class,boolean.class},yesterday,false));settleV15();
        if(!android.animation.ValueAnimator.areAnimatorsEnabled())return;
        final View[] frame={null},sheet={null};final Object[] renderer={null};final Bitmap[] texture={null};final int[] generation={0};
        uiAssert(()->frame[0]=(View)field(screen,"bookDetailSwipeFrame"));
        final java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(3),release=new java.util.concurrent.CountDownLatch(1);java.util.concurrent.ExecutorService pool=(java.util.concurrent.ExecutorService)field(screen,"mediaThumbnailExecutor");
        for(int i=0;i<3;i++)pool.execute(()->{entered.countDown();try{release.await(20,java.util.concurrent.TimeUnit.SECONDS);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}});
        if(!entered.await(5,java.util.concurrent.TimeUnit.SECONDS)){release.countDown();throw new Exception("Cannot hold incoming curl thumbnail workers");}
        Rect bounds=screenRect(frame[0]);float start=bounds.left+bounds.width()*.1f,end=bounds.left+bounds.width()*.85f,y=bounds.top+bounds.height()*.62f;long down=android.os.SystemClock.uptimeMillis();
        final Bitmap[] coldDraw={null};Bitmap coldPresented=null;
        try{
            uiAssert(()->((android.util.LruCache<?,?>)field(screen,"mediaThumbnailCache")).evictAll());
            pointerV15(down,MotionEvent.ACTION_DOWN,start,y);movesV15(down,start,y,end,y,6);
            long limit=android.os.SystemClock.elapsedRealtime()+2500;boolean[] ready={false};do{uiAssert(()->{Object turn=ownField(frame[0],"turn");View next=(View)ownField(frame[0],"nextPage");if(turn==null||next==null)return;
                if(((Number)ownField(frame[0],"offset")).intValue()!=-1)throw new Exception("Curl fixture did not reach newer incoming page");
                renderer[0]=turn;texture[0]=(Bitmap)ownField(turn,"front");sheet[0]=next.findViewWithTag("book_detail_notebook_page");if(sheet[0]==null)throw new Exception("Incoming curl paper missing");generation[0]=texture[0].getGenerationId();for(android.widget.ImageView image:boardImagesV26(sheet[0]))if(image.getDrawable()!=null)throw new Exception("Held incoming curl was not cold");ready[0]=true;});if(ready[0])break;Thread.sleep(10);}while(android.os.SystemClock.elapsedRealtime()<limit);
            if(!ready[0])throw new Exception("Real notebook drag did not produce an incoming curl renderer");
            uiAssert(()->{coldDraw[0]=renderViewV26(frame[0]);saveFrameV24(coldDraw[0],name+"-curl-delayed-before-draw");});
            coldPresented=getUiAutomation().takeScreenshot();if(coldPresented==null)throw new Exception("No hardware cold incoming curl screenshot");saveFrameV24(coldPresented,name+"-curl-delayed-before-presented");
            release.countDown();waitMediaV26(sheet[0]);Thread.sleep(40);
            limit=android.os.SystemClock.elapsedRealtime()+3000;boolean[] updated={false};do{uiAssert(()->{if(ownField(frame[0],"turn")!=renderer[0]||ownField(renderer[0],"front")!=texture[0])throw new Exception("Late curl media replaced or lost its renderer/texture");updated[0]=texture[0].getGenerationId()!=generation[0];});if(updated[0])break;Thread.sleep(18);}while(android.os.SystemClock.elapsedRealtime()<limit);
            if(!updated[0])throw new Exception("Incoming curl texture did not repaint on thumbnail completion");
            final List<CurlSubjectV26> subjects=new ArrayList<>();final StringBuilder geometry=new StringBuilder();
            uiAssert(()->{mediaLoadedV26(sheet[0]);subjects.addAll(visibleCurlSubjectsV26(frame[0],sheet[0],renderer[0],geometry));android.util.Log.i("EatRecordQA","beta.26 curl geometry: "+geometry);
                File dir=new File(getTargetContext().getExternalFilesDir(null),"qa");dir.mkdirs();try(OutputStream out=new FileOutputStream(new File(dir,name+"-curl-geometry.txt"))){out.write(geometry.toString().getBytes("UTF-8"));}
                saveFrameV24(texture[0],name+"-curl-refreshed-texture");assertSubjectsV26(texture[0],"same incoming curl texture");Bitmap drawn=renderViewV26(frame[0]);try{saveFrameV24(drawn,name+"-curl-delayed-draw");assertCurlSubjectsV26(coldDraw[0],subjects,false,false,"cold notebook curl draw",geometry.toString());assertCurlSubjectsV26(drawn,subjects,false,true,"real notebook curl presentation",geometry.toString());}finally{drawn.recycle();}assertRenderPure(screen,prefs,before);});
            Thread.sleep(40);Bitmap presented=getUiAutomation().takeScreenshot();if(presented==null)throw new Exception("No hardware incoming curl screenshot");try{saveFrameV24(presented,name+"-curl-delayed-presented");assertCurlSubjectsV26(coldPresented,subjects,true,false,"cold HWUI notebook curl frame",geometry.toString());assertCurlSubjectsV26(presented,subjects,true,true,"HWUI notebook curl frame",geometry.toString());}finally{presented.recycle();}
        }finally{release.countDown();if(coldDraw[0]!=null)coldDraw[0].recycle();if(coldPresented!=null)coldPresented.recycle();pointerV15(down,MotionEvent.ACTION_CANCEL,end,y);settleV15();}
        uiAssert(()->{if(!yesterday.equals(ownField(frame[0],"currentDate"))||ownField(frame[0],"turn")!=null||ownField(frame[0],"nextPage")!=null||((GestureSpring)ownField(frame[0],"spring")).isRunning())throw new Exception("Delayed incoming curl cancellation lost state or retained texture");assertRenderPure(screen,prefs,before);});
    }
    private void bookDispatchV27(View frame,long down,int action,float... points){
        int count=points.length/2;MotionEvent.PointerProperties[] properties=new MotionEvent.PointerProperties[count];MotionEvent.PointerCoords[] coords=new MotionEvent.PointerCoords[count];
        for(int i=0;i<count;i++){properties[i]=new MotionEvent.PointerProperties();properties[i].id=i;properties[i].toolType=MotionEvent.TOOL_TYPE_FINGER;coords[i]=new MotionEvent.PointerCoords();coords[i].x=points[i*2];coords[i].y=points[i*2+1];coords[i].pressure=1;coords[i].size=1;}
        MotionEvent event=MotionEvent.obtain(down,android.os.SystemClock.uptimeMillis(),action,count,properties,coords,0,0,1,1,0,0,InputDevice.SOURCE_TOUCHSCREEN,0);try{frame.dispatchTouchEvent(event);}finally{event.recycle();}
    }
    private void notebookChildCancelV27(MainActivity screen)throws Exception {
        uiAssert(()->{
            ViewGroup frame=(ViewGroup)field(screen,"bookDetailSwipeFrame");String before=(String)ownField(frame,"currentDate");
            View child=new View(screen);final int[] clicks={0},cancels={0};child.setOnClickListener(v->clicks[0]++);child.setOnTouchListener((v,e)->{if(e.getActionMasked()==MotionEvent.ACTION_CANCEL)cancels[0]++;return false;});
            frame.addView(child,new ViewGroup.LayoutParams(frame.getWidth(),frame.getHeight()));child.layout(0,0,frame.getWidth(),frame.getHeight());float x=frame.getWidth()*.5f,y=frame.getHeight()*.5f;long down=android.os.SystemClock.uptimeMillis();
            try{
                bookDispatchV27(frame,down,MotionEvent.ACTION_DOWN,x,y);
                bookDispatchV27(frame,down,MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),x,y,x+20,y+20);
                bookDispatchV27(frame,down,MotionEvent.ACTION_MOVE,x-180,y,x-160,y+20);
                bookDispatchV27(frame,down,MotionEvent.ACTION_POINTER_UP|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),x-180,y,x-160,y+20);
                bookDispatchV27(frame,down,MotionEvent.ACTION_UP,x-180,y);
                if(clicks[0]!=0||cancels[0]!=1||Boolean.TRUE.equals(ownField(frame,"gestureCancelled"))||((Number)ownField(frame,"pointer")).intValue()!=-1||ownField(frame,"tracker")!=null||!before.equals(ownField(frame,"currentDate")))throw new Exception("Child-owned multipointer stream was not cancelled/cleaned before the next notebook swipe");
            }finally{frame.removeView(child);}
        });
    }
    private void continuousBooksV27(MainActivity screen,android.content.SharedPreferences prefs,String before)throws Exception {
        final List<String>[] dates=new List[]{null};uiAssert(()->dates[0]=(List<String>)invoke(screen,"recordedDates",new Class[]{}));
        if(dates[0].size()<5)throw new Exception("Continuous book fixture needs at least five actual dates");
        for(String mode:new String[]{"flip","normal"})for(int gap:new int[]{0,45})for(boolean older:new boolean[]{true,false}){
            int start=older?0:dates[0].size()-1;String initial=dates[0].get(start);
            uiAssert(()->{((JSONObject)field(screen,"profile")).put("bookTransitionMode",mode);invoke(screen,"renderBookDetail",new Class[]{String.class,boolean.class},initial,false);});settleV15();
            View frame=(View)field(screen,"bookDetailSwipeFrame");Rect area=screenRect(frame);float x=area.left+area.width()*(older?.85f:.15f),end=x+(older?-1:1)*area.width()*.65f,y=area.top+area.height()*.55f;
            for(int step=0;step<4;step++){
                long down=android.os.SystemClock.uptimeMillis();pointerV15(down,MotionEvent.ACTION_DOWN,x,y);
                // No final MOVE: the release owns the final coordinate, like real coalesced input.
                movesV15(down,x,y,x+(end-x)*.2f,y,2);pointerV15(down,MotionEvent.ACTION_UP,end,y);if(gap>0)Thread.sleep(gap);
            }
            settleV15();int expected=start+(older?4:-4);
            uiAssert(()->{if(!dates[0].get(expected).equals(ownField(frame,"currentDate"))||ownField(frame,"nextPage")!=null||ownField(frame,"turn")!=null||((GestureSpring)ownField(frame,"spring")).isRunning()||ownField(frame,"tracker")!=null||((Number)ownField(frame,"pointer")).intValue()!=-1||Boolean.TRUE.equals(ownField(frame,"gestureCancelled"))||((ViewGroup)frame).getChildCount()!=1)throw new Exception("Four continuous book turns did not land/clean up: mode="+mode+", gap="+gap+", older="+older);assertRenderPure(screen,prefs,before);});
        }
        uiAssert(()->((JSONObject)field(screen,"profile")).put("bookTransitionMode","flip"));
        notebookChildCancelV27(screen);
    }
    private static int rootTabV28(int index){return index==2?3:index;}
    /** Only direct, nonanimated fixture placement is excluded from the live sampler. */
    private boolean rootFixtureSetupV28;
    private String rootIndicatorDiagnosticsV28(MainActivity screen)throws Exception {
        View indicator=(View)field(screen,"navIndicator"),nav=(View)field(screen,"navBar"),content=(View)field(screen,"content");
        return ", indicatorVisibility="+indicator.getVisibility()+", pendingIndicatorLayout="+(field(screen,"pendingNavIndicatorLayout")!=null)+", indicatorLayoutRequested="+indicator.isLayoutRequested()+", navLayoutRequested="+nav.isLayoutRequested()+", contentLayoutRequested="+content.isLayoutRequested()+", fixtureSetup="+rootFixtureSetupV28;
    }
    private void traceRootV28(MainActivity screen,RootPagePager pager,StringBuilder trace,long stamp,float wantedX,float wantedWidth,boolean setup)throws Exception {
        View indicator=(View)field(screen,"navIndicator"),nav=(View)field(screen,"navBar"),content=(View)field(screen,"content");
        trace.append(stamp).append(',').append(pager.position()).append(',').append(pager.target()).append(',').append(pager.page(0).getTranslationX()).append(',').append(pager.page(1).getTranslationX()).append(',').append(pager.page(2).getTranslationX()).append(',').append(indicator.getTranslationX()).append(',').append(wantedX).append(',').append(indicator.getWidth()*indicator.getScaleX()).append(',').append(wantedWidth).append(',').append(setup).append(',').append(indicator.getVisibility()).append(',').append(field(screen,"pendingNavIndicatorLayout")!=null).append(',').append(indicator.isLayoutRequested()).append(',').append(nav.isLayoutRequested()).append(',').append(content.isLayoutRequested()).append('\n');
    }
    /** A real retained-root frame oracle, independent of the old from/to crossfade state. */
    private void rootFrameV28(MainActivity screen,RootPagePager expected,View[] pages,StringBuilder trace,long stamp)throws Exception {
        if(field(screen,"rootPager")!=expected)throw new Exception("Root switching replaced the retained pager");
        ViewGroup content=(ViewGroup)field(screen,"content");
        if(content.getChildCount()!=1||content.getChildAt(0)!=expected||expected.getChildCount()!=3)throw new Exception("Root hierarchy lost its three retained sheets");
        PageCrossfade crossfade=(PageCrossfade)field(screen,"pageCrossfade");
        if(crossfade!=null&&crossfade.active())throw new Exception("Root gesture/click used the bitmap crossfade path");
        float position=expected.position();if(!Float.isFinite(position)||position<0||position>2)throw new Exception("Invalid absolute root position: "+position);
        RootPageMotion state=(RootPageMotion)ownField(expected,"motion");
        float edge=HomeDayMotion.rubber(-state.overscroll()*Math.max(1,expected.getWidth()),Math.max(1,expected.getWidth()));
        for(int i=0;i<3;i++){
            View page=expected.page(i);float wanted=(i-position)*expected.getWidth()+edge;
            if(page!=pages[i]||page.getParent()!=expected||page.getWidth()!=expected.getWidth()||page.getHeight()!=expected.getHeight())throw new Exception("Root page "+i+" was rebuilt, detached or not pre-laid-out");
            if(Math.abs(page.getTranslationX()-wanted)>1.5f||page.getTranslationY()!=0||page.getAlpha()!=1)throw new Exception("Root sheet "+i+" is not at its continuous absolute position: actual="+page.getTranslationX()+", wanted="+wanted);
        }
        View indicator=(View)field(screen,"navIndicator");ViewGroup nav=(ViewGroup)field(screen,"navBar");
        int low=Math.min(1,(int)Math.floor(position)),high=low+1;float fraction=Math.max(0,Math.min(1,position-low));
        View a=nav.findViewWithTag(Integer.valueOf(rootTabV28(low))),b=nav.findViewWithTag(Integer.valueOf(rootTabV28(high)));
        if(a==null||b==null||a.getWidth()<=0||b.getWidth()<=0||indicator.getWidth()<=0)throw new Exception("Unmeasured bottom slots during retained root navigation"+rootIndicatorDiagnosticsV28(screen));
        float aw=Math.max(PaperComponents.dp(screen,74),a.getWidth()-PaperComponents.dp(screen,6)),bw=Math.max(PaperComponents.dp(screen,74),b.getWidth()-PaperComponents.dp(screen,6));
        float ax=a.getLeft()+(a.getWidth()-aw)*.5f,bx=b.getLeft()+(b.getWidth()-bw)*.5f;
        float wantedX=ax+(bx-ax)*fraction,wantedWidth=aw+(bw-aw)*fraction,actualWidth=indicator.getWidth()*indicator.getScaleX();
        if(Math.abs(indicator.getTranslationX()-wantedX)>2.2f||Math.abs(actualWidth-wantedWidth)>2.2f)throw new Exception("Bottom selection is detached from absolute root position: p="+position+", actual="+indicator.getTranslationX()+"/"+actualWidth+", wanted="+wantedX+"/"+wantedWidth+rootIndicatorDiagnosticsV28(screen));
        if(expected.isInteracting()&&indicator.getVisibility()!=View.VISIBLE)throw new Exception("Bottom selection vanished while root sheets were moving"+rootIndicatorDiagnosticsV28(screen));
        if(trace!=null)traceRootV28(screen,expected,trace,stamp,wantedX,wantedWidth,false);
    }
    private final class RootFramesV28 implements Choreographer.FrameCallback {
        final MainActivity screen;final RootPagePager pager;final View[] pages;final StringBuilder trace=new StringBuilder("frame,position,target,todayX,booksX,meX,indicatorX,expectedX,width,expectedWidth,fixtureSetup,indicatorVisibility,pendingIndicatorLayout,indicatorLayoutRequested,navLayoutRequested,contentLayoutRequested\n");
        boolean running=true;Throwable failure;int movingFrames,fixtureFrames;
        RootFramesV28(MainActivity screen,RootPagePager pager,View[] pages){this.screen=screen;this.pager=pager;this.pages=pages;}
        public void doFrame(long stamp){
            if(!running)return;
            try{
                if(rootFixtureSetupV28){traceRootV28(screen,pager,trace,stamp,Float.NaN,Float.NaN,true);fixtureFrames++;}
                else{rootFrameV28(screen,pager,pages,trace,stamp);if(pager.isInteracting()&&Math.abs(pager.position()-Math.round(pager.position()))>.005f)movingFrames++;}
            }catch(Throwable error){if(failure==null)failure=error;}
            if(running)Choreographer.getInstance().postFrameCallback(this);
        }
    }
    private void rootRestV28(MainActivity screen,RootPagePager pager,View[] pages,int index)throws Exception {
        rootFrameV28(screen,pager,pages,null,0);
        if(pager.isInteracting()||pager.target()!=index||Math.abs(pager.position()-index)>.001f||((Number)field(screen,"currentTab")).intValue()!=rootTabV28(index))throw new Exception("Root did not settle on requested page "+index+": p="+pager.position()+", target="+pager.target()+", tab="+field(screen,"currentTab"));
        if(field(screen,"peerTracker")!=null||((Number)field(screen,"peerPointer")).intValue()!=-1||Boolean.TRUE.equals(field(screen,"peerTracking"))||Boolean.TRUE.equals(field(screen,"peerDragging"))||Boolean.TRUE.equals(field(screen,"peerSwallow"))||Boolean.TRUE.equals(field(screen,"navPeerActive")))throw new Exception("Root settlement retained an input/indicator owner");
        for(int i=0;i<3;i++)if(pager.ownsView(pages[i])!=(i==index))throw new Exception("Retained root "+i+" has the wrong input/date owner at rest "+index);
        View day=pager.page(0).findViewWithTag("today_day_pager"),calendar=pager.page(0).findViewWithTag("home_calendar");
        if(day==null||calendar==null||Boolean.TRUE.equals(ownField(day,"rootActive"))!=(index==0)||Boolean.TRUE.equals(ownField(calendar,"rootActive"))!=(index==0))throw new Exception("Hidden Today components were not explicitly suspended");
        View indicator=(View)field(screen,"navIndicator"),host=(View)field(screen,"navHost");
        if(indicator.getVisibility()!=View.VISIBLE||indicator.getHeight()<=0||Math.abs(indicator.getScaleX()-1)>.001f||indicator.getLeft()+indicator.getTranslationX()<host.getPaddingLeft()-1||indicator.getLeft()+indicator.getTranslationX()+indicator.getWidth()>host.getWidth()-host.getPaddingRight()+1)throw new Exception("Settled bottom selection is hidden, scaled or outside its bar");
    }
    private void rootSelectV28(MainActivity screen,RootPagePager pager,View[] pages,int index)throws Exception {
        try{
            uiAssert(()->{rootFixtureSetupV28=true;pager.select(index,false);});settleV15();
            // This stays strict while setup is marked: only the asynchronous
            // sampler ignores the fixture's hidden, pending-layout interval.
            uiAssert(()->rootRestV28(screen,pager,pages,index));
        }finally{uiAssert(()->rootFixtureSetupV28=false);}
    }
    private float[] rootPointsV28(MainActivity screen,boolean forward,float amount)throws Exception {
        final float[][] result={null};uiAssert(()->{Rect area=screenRect((View)field(screen,"content"));float x=area.left+area.width()*(forward?.85f:.15f),y=area.top+PaperComponents.dp(screen,42);
            result[0]=new float[]{x,y,x+(forward?-1:1)*area.width()*amount,area.width()};});return result[0];
    }
    private void rootGestureV28(MainActivity screen,RootPagePager pager,View[] pages,boolean forward,float amount,boolean noTerminalMove,int terminal,int pauseMs)throws Exception {
        float[] point=rootPointsV28(screen,forward,amount);long down=android.os.SystemClock.uptimeMillis();pointerV15(down,MotionEvent.ACTION_DOWN,point[0],point[1]);
        final float[] origin={0};uiAssert(()->origin[0]=pager.position());int moves=noTerminalMove?2:5;
        for(int i=1;i<=moves;i++){
            float fraction=noTerminalMove?.2f*i/moves:i/(float)moves;
            pointerV15(down,MotionEvent.ACTION_MOVE,point[0]+(point[2]-point[0])*fraction,point[1]);
            uiAssert(()->rootFrameV28(screen,pager,pages,null,0));Thread.sleep(16);
        }
        if(pauseMs>0)Thread.sleep(pauseMs);
        final float expected=Math.max(0,Math.min(2,origin[0]+(forward?1:-1)*amount));
        uiAssert(()->{
            int[] offset=new int[2];screen.getWindow().getDecorView().getLocationOnScreen(offset);MotionEvent event=MotionEvent.obtain(down,android.os.SystemClock.uptimeMillis(),terminal,point[2]-offset[0],point[1]-offset[1],0);event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            try{screen.dispatchTouchEvent(event);localPointerFallbacksV20++;}finally{event.recycle();}
            if(terminal==MotionEvent.ACTION_UP&&Math.abs(pager.position()-expected)>.002f)throw new Exception("Root UP lost its final coordinate: actual="+pager.position()+", wanted="+expected+", no-terminal-MOVE="+noTerminalMove);
            rootFrameV28(screen,pager,pages,null,0);
        });
    }
    private void rootNavigationV28(MainActivity screen,RootPagePager pager,View[] pages,android.content.SharedPreferences prefs,String before,String name)throws Exception {
        for(int gap:new int[]{0,45,80})for(boolean forward:new boolean[]{true,false})for(boolean coalesced:new boolean[]{false,true}){
            rootSelectV28(screen,pager,pages,forward?0:2);
            rootGestureV28(screen,pager,pages,forward,.64f,coalesced,MotionEvent.ACTION_UP,coalesced?100:0);if(gap>0)Thread.sleep(gap);
            rootGestureV28(screen,pager,pages,forward,.64f,coalesced,MotionEvent.ACTION_UP,coalesced?100:0);
            settleV15();uiAssert(()->{rootRestV28(screen,pager,pages,forward?2:0);assertRenderPure(screen,prefs,before);});
        }
        // Reverse while the previous request is pending, then keep moving toward the third page.
        rootSelectV28(screen,pager,pages,2);rootGestureV28(screen,pager,pages,false,.64f,false,MotionEvent.ACTION_UP,0);
        rootGestureV28(screen,pager,pages,true,.64f,true,MotionEvent.ACTION_UP,0);
        rootGestureV28(screen,pager,pages,false,.64f,true,MotionEvent.ACTION_UP,0);
        rootGestureV28(screen,pager,pages,false,.64f,true,MotionEvent.ACTION_UP,0);
        settleV15();uiAssert(()->rootRestV28(screen,pager,pages,0));
        // Repeated changes must retarget the same three instances, not accumulate old pairs.
        for(int pass=0;pass<3;pass++){
            rootSelectV28(screen,pager,pages,0);
            for(boolean forward:new boolean[]{true,true,false,false,true,false,true,true})rootGestureV28(screen,pager,pages,forward,.64f,true,MotionEvent.ACTION_UP,0);
            settleV15();uiAssert(()->rootRestV28(screen,pager,pages,2));
        }
        rootSelectV28(screen,pager,pages,1);rootGestureV28(screen,pager,pages,true,.34f,false,MotionEvent.ACTION_CANCEL,0);settleV15();uiAssert(()->rootRestV28(screen,pager,pages,1));
        float[] point=rootPointsV28(screen,true,.32f);long down=android.os.SystemClock.uptimeMillis();pointerV15(down,MotionEvent.ACTION_DOWN,point[0],point[1]);movesV15(down,point[0],point[1],point[2],point[1],3);
        rootMultiV26(screen,down,MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),point[2],point[1]);rootMultiV26(screen,down,MotionEvent.ACTION_POINTER_UP|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),point[2],point[1]);pointerV15(down,MotionEvent.ACTION_UP,point[2],point[1]);
        settleV15();uiAssert(()->rootRestV28(screen,pager,pages,1));
        rootGestureV28(screen,pager,pages,true,.64f,true,MotionEvent.ACTION_UP,0);settleV15();uiAssert(()->rootRestV28(screen,pager,pages,2));
        rootGestureV28(screen,pager,pages,true,.64f,false,MotionEvent.ACTION_UP,0);settleV15();uiAssert(()->rootRestV28(screen,pager,pages,2));
        rootGestureV28(screen,pager,pages,false,.64f,true,MotionEvent.ACTION_UP,0);rootGestureV28(screen,pager,pages,false,.64f,true,MotionEvent.ACTION_UP,0);settleV15();uiAssert(()->rootRestV28(screen,pager,pages,0));
        rootGestureV28(screen,pager,pages,false,.64f,false,MotionEvent.ACTION_UP,0);settleV15();uiAssert(()->rootRestV28(screen,pager,pages,0));
        // Actual nav taps are allowed to start immediately; a new swipe must still take ownership.
        bottomTabV24(screen,3);rootGestureV28(screen,pager,pages,false,.64f,true,MotionEvent.ACTION_UP,0);rootGestureV28(screen,pager,pages,false,.64f,true,MotionEvent.ACTION_UP,0);settleV15();uiAssert(()->rootRestV28(screen,pager,pages,0));
        for(int gap:new int[]{0,16,45}){
            for(int tab:new int[]{1,3,0,3,1,0,1}){bottomTabV24(screen,tab);if(gap>0)Thread.sleep(gap);}
            settleV15();uiAssert(()->rootRestV28(screen,pager,pages,1));
        }
        rootSelectV28(screen,pager,pages,0);uiAssert(()->assertRenderPure(screen,prefs,before));screenshotNowV15(name+"-root-final");
    }
    private void hiddenTodayOwnerV28(MainActivity screen,RootPagePager pager,View[] pages,android.content.SharedPreferences prefs,String before,String today)throws Exception {
        rootSelectV28(screen,pager,pages,0);TodayDayPager day=(TodayDayPager)pager.page(0).findViewWithTag("today_day_pager");
        Calendar requested=Calendar.getInstance();requested.setTime(new SimpleDateFormat("yyyy-MM-dd",Locale.CHINA).parse(today));requested.add(Calendar.DATE,-1);String wanted=HomeCalendarDates.key(requested);
        if(!android.animation.ValueAnimator.areAnimatorsEnabled())return;
        uiAssert(()->{if(!day.animateToDate(requested))throw new Exception("Cannot start a real date transition before leaving Today");});Thread.sleep(25);bottomTabV24(screen,3);
        final String[] held={null};final float[] offset={0};uiAssert(()->{
            held[0]=(String)field(screen,"homeDateKey");offset[0]=((Number)ownField(day,"offset")).floatValue();
            if(Boolean.TRUE.equals(ownField(day,"rootActive"))||TodayDayPager.isLiveHierarchy(day)||pager.ownsView(day))throw new Exception("Today still owns date writes while a root transition is moving away");
        });Thread.sleep(250);
        uiAssert(()->{if(!held[0].equals(field(screen,"homeDateKey"))||Math.abs(((Number)ownField(day,"offset")).floatValue()-offset[0])>.01f)throw new Exception("Hidden Today date animation continued writing/presenting after root ownership changed");assertRenderPure(screen,prefs,before);});
        settleV15();uiAssert(()->{rootRestV28(screen,pager,pages,2);if(!held[0].equals(field(screen,"homeDateKey")))throw new Exception("Hidden cached Today modified its date after root settlement");if(day.animateToDate(requested))throw new Exception("Hidden Today accepted a new date command");});
        bottomTabV24(screen,0);settleV15();settleV15();uiAssert(()->{rootRestV28(screen,pager,pages,0);if(!wanted.equals(HomeCalendarDates.key(day.currentDate()))||!wanted.equals(field(screen,"homeDateKey")))throw new Exception("Returning to Today did not resume its original date target");assertRenderPure(screen,prefs,before);});
    }
    /** Stable outer boundaries are silent: no root grab, page motion or date-owner pause. */
    private void silentRootBoundariesV29(MainActivity screen,RootPagePager pager,View[] pages,android.content.SharedPreferences prefs,String before)throws Exception {
        for(int index:new int[]{0,2}){
            rootSelectV28(screen,pager,pages,index);
            final float[] pageX=new float[3],indicatorState=new float[2];final String[] date={null};final boolean[] dayActive={false};
            uiAssert(()->{
                View indicator=(View)field(screen,"navIndicator"),day=pager.page(0).findViewWithTag("today_day_pager");
                for(int i=0;i<3;i++)pageX[i]=pages[i].getTranslationX();indicatorState[0]=indicator.getTranslationX();indicatorState[1]=indicator.getScaleX();date[0]=(String)field(screen,"homeDateKey");dayActive[0]=Boolean.TRUE.equals(ownField(day,"rootActive"));
            });
            for(int attempt=0;attempt<3;attempt++){
                boolean outwardLeft=index==2;float[] point=rootPointsV28(screen,outwardLeft,.64f);long down=android.os.SystemClock.uptimeMillis();
                pointerV15(down,MotionEvent.ACTION_DOWN,point[0],point[1]);
                for(int step=1;step<=5;step++){
                    pointerV15(down,MotionEvent.ACTION_MOVE,point[0]+(point[2]-point[0])*step/5,point[1]);
                    uiAssert(()->{
                        RootPageMotion state=(RootPageMotion)ownField(pager,"motion");View indicator=(View)field(screen,"navIndicator"),day=pager.page(0).findViewWithTag("today_day_pager");
                        if(pager.isInteracting()||pager.position()!=index||pager.target()!=index||state.overscroll()!=0)throw new Exception("Blocked root boundary grabbed or moved the pager: index="+index+", p="+pager.position());
                        for(int i=0;i<3;i++)if(pages[i].getTranslationX()!=pageX[i])throw new Exception("Blocked root boundary moved sheet "+i);
                        if(indicator.getTranslationX()!=indicatorState[0]||indicator.getScaleX()!=indicatorState[1])throw new Exception("Blocked root boundary moved the bottom selection");
                        if(Boolean.TRUE.equals(ownField(day,"rootActive"))!=dayActive[0]||!date[0].equals(field(screen,"homeDateKey")))throw new Exception("Blocked root boundary paused or changed the Today owner/date");
                        rootFrameV28(screen,pager,pages,null,0);assertRenderPure(screen,prefs,before);
                    });Thread.sleep(16);
                }
                pointerV15(down,MotionEvent.ACTION_UP,point[2],point[1]);uiAssert(()->rootRestV28(screen,pager,pages,index));
            }
            // No stale swallow/edge flag may prevent the very next legal direction.
            rootGestureV28(screen,pager,pages,index==0,.64f,true,MotionEvent.ACTION_UP,0);settleV15();uiAssert(()->rootRestV28(screen,pager,pages,1));
        }
        rootSelectV28(screen,pager,pages,0);
    }
    /** Check real cover geometry at wall-clock milestones, independently of its local formula. */
    private void bookOpeningTimelineV29(MainActivity screen,android.content.SharedPreferences prefs,String before,String name)throws Exception {
        final boolean animated=android.animation.ValueAnimator.areAnimatorsEnabled();
        for(String mode:new String[]{"normal","flip"}){
            uiAssert(()->{((JSONObject)field(screen,"profile")).put("bookTransitionMode",mode);invoke(screen,"showTab",new Class[]{int.class,boolean.class},1,false);});settleV15();bookCardTapV25(screen);
            if(!animated){
                settleV15();uiAssert(()->{if(!Boolean.TRUE.equals(field(screen,"inBookDetail"))||!bookOpenLayersV25(screen).isEmpty())throw new Exception("Disabled system animations did not land directly on notebook");View frame=(View)field(screen,"bookDetailSwipeFrame"),paper=frame.findViewWithTag("book_detail_notebook_page");if(paper==null||paper.getAlpha()!=1)throw new Exception("Reduced-motion notebook retained hidden paper");assertRenderPure(screen,prefs,before);});continue;
            }
            final Object[] opening={null};final android.animation.ValueAnimator[] animator={null};final Bitmap[] pageImage={null},coverImage={null};final View[] paper={null};boolean[] ready={false};
            long limit=android.os.SystemClock.elapsedRealtime()+3000;
            do{
                uiAssert(()->{
                    Object overlay=field(screen,"bookOpeningOverlay");if(overlay==null)return;Object value=ownField(overlay,"animator");if(!(value instanceof android.animation.ValueAnimator))return;
                    opening[0]=overlay;animator[0]=(android.animation.ValueAnimator)value;animator[0].pause();animator[0].setCurrentPlayTime(0);
                    pageImage[0]=(Bitmap)ownField(overlay,"pageImage");coverImage[0]=(Bitmap)ownField(overlay,"coverImage");paper[0]=(View)ownField(overlay,"paper");
                    if(pageImage[0]==null||coverImage[0]==null||paper[0]==null||pageImage[0].isRecycled()||coverImage[0].isRecycled())throw new Exception("Opening never allocated a live paper and cover: mode="+mode);
                    if(animator[0].getDuration()!=1000||!(animator[0].getInterpolator() instanceof android.view.animation.LinearInterpolator))throw new Exception("Opening still uses a short/front-loaded timeline: mode="+mode+", duration="+animator[0].getDuration());
                    ready[0]=true;
                });
                if(ready[0])break;Thread.sleep(5);
            }while(android.os.SystemClock.elapsedRealtime()<limit);
            if(!ready[0])throw new Exception("Real cover tap did not start a cover-opening animator: mode="+mode);
            try{
                for(final int elapsed:new int[]{150,300,550,850}){
                    uiAssert(()->{
                        if(field(screen,"bookOpeningOverlay")!=opening[0]||ownField(opening[0],"pageImage")!=pageImage[0]||ownField(opening[0],"coverImage")!=coverImage[0]||pageImage[0].isRecycled()||coverImage[0].isRecycled())throw new Exception("Opening lost/replaced its textures before timeline milestone "+elapsed+": mode="+mode);
                        animator[0].setCurrentPlayTime(elapsed);float clock=((Number)ownField(opening[0],"progress")).floatValue();
                        if(Math.abs(clock-elapsed/1000f)>.02f)throw new Exception("Opening progress is front-loaded instead of wall time: elapsed="+elapsed+", progress="+clock);
                        Bitmap drawn=renderViewV26((View)opening[0]);try{saveFrameV24(drawn,name+"-"+mode+"-open-"+elapsed);}finally{drawn.recycle();}
                        RectF current=(RectF)ownField(opening[0],"current"),target=(RectF)ownField(opening[0],"target"),start=(RectF)ownField(opening[0],"start"),local=(RectF)ownField(opening[0],"localPaper");float[] destination=(float[])ownField(opening[0],"coverDestination");
                        if(elapsed==150&&(current.width()<=start.width()||current.width()>=target.width()-.5f))throw new Exception("Early opening skipped cover growth: mode="+mode+", current="+current+", target="+target);
                        if(elapsed>=300&&(Math.abs(current.left-target.left)>1.5f||Math.abs(current.top-target.top)>1.5f||Math.abs(current.width()-target.width())>1.5f||Math.abs(current.height()-target.height())>1.5f))throw new Exception("Cover growth did not reach the real paper before its visible turn: mode="+mode+", elapsed="+elapsed);
                        if(elapsed==550&&(destination[2]<=local.width()*.05f||destination[2]>=local.width()*.9f))throw new Exception("Midpoint did not preserve a visibly half-open front cover: mode="+mode+", edge="+destination[2]+", width="+local.width());
                        if(elapsed==850&&destination[2]>=-local.width()*.05f)throw new Exception("Late opening never presented the back across its spine: mode="+mode+", edge="+destination[2]);
                        if(destination[0]!=0||destination[1]!=0||destination[6]!=0||paper[0].getAlpha()!=0)throw new Exception("Opening hinge moved or hidden live paper leaked through: mode="+mode);
                        if(!mode.equals(((JSONObject)field(screen,"profile")).optString("bookTransitionMode")))throw new Exception("Opening changed the internal page-turn preference");assertRenderPure(screen,prefs,before);
                    });
                }
                uiAssert(()->animator[0].resume());settleV15();
                uiAssert(()->{if(field(screen,"bookOpeningOverlay")!=null||!bookOpenLayersV25(screen).isEmpty()||paper[0].getAlpha()!=1||!pageImage[0].isRecycled()||!coverImage[0].isRecycled())throw new Exception("Completed slow opening retained overlay, hidden paper or texture: mode="+mode);assertRenderPure(screen,prefs,before);});
            }finally{uiAssert(()->{if(animator[0]!=null&&animator[0].isPaused())animator[0].resume();});}
        }
        // Existing real return/tab/close interruption checks exercise the shared cleanup path.
        uiAssert(()->((JSONObject)field(screen,"profile")).put("bookTransitionMode","flip"));notebookOpeningV25(screen,prefs,before,name+"-cleanup");
    }
    private void setOwnV30(Object owner,String name,Object value)throws Exception {java.lang.reflect.Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);f.set(owner,value);}
    private boolean differentPixelV30(int a,int b){return Math.abs(Color.alpha(a)-Color.alpha(b))>1||Math.abs(Color.red(a)-Color.red(b))>1||Math.abs(Color.green(a)-Color.green(b))>1||Math.abs(Color.blue(a)-Color.blue(b))>1;}
    private void sameBookCornersV30(Bitmap expected,Bitmap actual,RectF rect,String where)throws Exception {
        int side=Math.max(5,Math.round(Math.min(rect.width(),rect.height())*.25f));
        for(int corner=0;corner<4;corner++){
            int left=Math.round((corner&1)==0?rect.left:rect.right-side),top=Math.round((corner&2)==0?rect.top:rect.bottom-side),differences=0;
            for(int y=Math.max(0,top);y<Math.min(actual.getHeight(),top+side);y++)for(int x=Math.max(0,left);x<Math.min(actual.getWidth(),left+side);x++)if(differentPixelV30(expected.getPixel(x,y),actual.getPixel(x,y)))differences++;
            if(differences!=0)throw new Exception("Closed cover leaked underlying paper at corner "+corner+", pixels="+differences+", "+where);
        }
    }
    private void bookCornerPixelsV30(Object overlay,android.animation.ValueAnimator animator,View paper,String name)throws Exception {
        Bitmap page=(Bitmap)ownField(overlay,"pageImage"),cover=(Bitmap)ownField(overlay,"coverImage");
        Object outgoing=ownField(overlay,"outgoingScreen"),shadow=ownField(overlay,"spineShadow");
        Bitmap mask=Bitmap.createBitmap(page.getWidth(),page.getHeight(),Bitmap.Config.ARGB_8888);paper.getBackground().draw(new Canvas(mask));
        android.graphics.Outline outline=new android.graphics.Outline();paper.getBackground().getOutline(outline);
        if(!paper.getClipToOutline()||outline.isEmpty()||outline.getRadius()<=0||outline.getAlpha()<.99f)throw new Exception("Live notebook lacks a rounded opaque outline/content clip");
        if(paper.getAlpha()!=0||paper.getElevation()!=0||paper.getTranslationZ()!=0)throw new Exception("Hidden live notebook can still cast a native shadow during opening");
        for(int y:new int[]{0,cover.getHeight()-1})for(int x:new int[]{0,cover.getWidth()-1})if(Color.alpha(cover.getPixel(x,y))<250)throw new Exception("Animation cover texture still contains pre-scaled transparent thumbnail corners");
        try{
            // Isolate the real overlay from its bookshelf. The cover-only reference
            // uses the same native artwork and mask, never a duplicate radius formula.
            setOwnV30(overlay,"outgoingScreen",null);
            for(int time:new int[]{0,100,200}){
                animator.setCurrentPlayTime(time);page.eraseColor(Color.TRANSPARENT);Bitmap reference=renderViewV26((View)overlay),withPaper=null;
                try{
                    RectF rect=new RectF((RectF)ownField(overlay,"current"));int cx=Math.max(0,Math.min(reference.getWidth()-1,Math.round(rect.centerX()))),cy=Math.max(0,Math.min(reference.getHeight()-1,Math.round(rect.centerY())));
                    if(Color.alpha(reference.getPixel(cx,cy))<245)throw new Exception("Cover-only corner oracle has no visible closed cover at "+time);
                    page.eraseColor(0xffff00ff);withPaper=renderViewV26((View)overlay);
                    saveFrameV24(reference,name+"-closed-"+time+"-cover-only");saveFrameV24(withPaper,name+"-closed-"+time+"-paper-probe");
                    sameBookCornersV30(reference,withPaper,rect,name+" time="+time);
                }finally{reference.recycle();if(withPaper!=null)withPaper.recycle();}
            }
            for(int time:new int[]{550,850}){
                animator.setCurrentPlayTime(time);page.eraseColor(0xffff00ff);setOwnV30(overlay,"spineShadow",shadow);Bitmap shaded=renderViewV26((View)overlay),unshaded=null;
                try{
                    setOwnV30(overlay,"spineShadow",new LinearGradient(0,0,page.getWidth(),0,Color.TRANSPARENT,Color.TRANSPARENT,Shader.TileMode.CLAMP));unshaded=renderViewV26((View)overlay);
                    RectF rect=(RectF)ownField(overlay,"current");int left=Math.round(rect.left),top=Math.round(rect.top),inside=0,outside=0;
                    for(int y=0;y<mask.getHeight();y+=2)for(int x=0;x<mask.getWidth();x+=2){
                        int sx=left+x,sy=top+y;if(sx<0||sy<0||sx>=shaded.getWidth()||sy>=shaded.getHeight())continue;
                        if(!differentPixelV30(shaded.getPixel(sx,sy),unshaded.getPixel(sx,sy)))continue;
                        if(Color.alpha(mask.getPixel(x,y))<5)outside++;else inside++;
                    }
                    saveFrameV24(mask,name+"-paper-alpha-oracle");saveFrameV24(shaded,name+"-shadow-"+time);saveFrameV24(unshaded,name+"-no-shadow-"+time);
                    if(outside!=0)throw new Exception("Spine shadow paints rectangular pixels beyond actual paper alpha: "+outside+", "+name+", time="+time);
                    if(time==850&&inside<20)throw new Exception("Shadow oracle never observed the exposed paper shadow: "+name);
                }finally{shaded.recycle();if(unshaded!=null)unshaded.recycle();setOwnV30(overlay,"spineShadow",shadow);}
            }
        }finally{
            setOwnV30(overlay,"outgoingScreen",outgoing);setOwnV30(overlay,"spineShadow",shadow);
            Method refresh=overlay.getClass().getDeclaredMethod("drawPaperTexture");refresh.setAccessible(true);refresh.invoke(overlay);mask.recycle();
        }
    }
    private static final class BookCloseProbeV31 {
        Object overlay;android.animation.ValueAnimator animator;View paper,destination,shelf;
        Bitmap page,cover;RectF from,start;float initial,shelfAlpha,shelfElevation,shelfZ;
    }
    private void waitBookRestV31(MainActivity screen,boolean detail)throws Exception {
        final boolean[] done={false};long limit=android.os.SystemClock.elapsedRealtime()+4500;
        do{uiAssert(()->{done[0]=field(screen,"bookOpeningOverlay")==null&&!Boolean.TRUE.equals(field(screen,"bookClosePending"))&&Boolean.TRUE.equals(field(screen,"inBookDetail"))==detail;});if(done[0])return;Thread.sleep(12);}while(android.os.SystemClock.elapsedRealtime()<limit);
        throw new Exception("Notebook failed to settle: detail="+detail);
    }
    private void tapBookCloseV31(MainActivity screen)throws Exception {
        final Rect[] at={null};uiAssert(()->{View close=((View)field(screen,"content")).findViewWithTag("book_detail_close");if(close==null||!close.getGlobalVisibleRect(at[0]=new Rect()))throw new Exception("No real visible notebook close button");});
        long down=android.os.SystemClock.uptimeMillis();pointerV15(down,MotionEvent.ACTION_DOWN,at[0].exactCenterX(),at[0].exactCenterY());pointerV15(down,MotionEvent.ACTION_UP,at[0].exactCenterX(),at[0].exactCenterY());
    }
    private BookCloseProbeV31 pauseBookCloseV31(MainActivity screen)throws Exception {
        BookCloseProbeV31 p=new BookCloseProbeV31();long limit=android.os.SystemClock.elapsedRealtime()+3500;
        do{uiAssert(()->{Object overlay=field(screen,"bookOpeningOverlay");if(overlay==null||!Boolean.TRUE.equals(ownField(overlay,"closing")))return;Object animator=ownField(overlay,"animator");if(!(animator instanceof android.animation.ValueAnimator))return;
            p.overlay=overlay;p.animator=(android.animation.ValueAnimator)animator;p.animator.pause();p.animator.setCurrentPlayTime(0);
            p.paper=(View)ownField(overlay,"paper");p.destination=(View)ownField(overlay,"closingDestination");p.shelf=(View)ownField(overlay,"shelfCover");
            p.page=(Bitmap)ownField(overlay,"pageImage");p.cover=(Bitmap)ownField(overlay,"coverImage");p.from=new RectF((RectF)ownField(overlay,"closingFrom"));p.start=new RectF((RectF)ownField(overlay,"start"));p.initial=((Number)ownField(overlay,"closingStartProgress")).floatValue();
            p.shelfAlpha=((Number)ownField(overlay,"shelfAlpha")).floatValue();p.shelfElevation=((Number)ownField(overlay,"shelfElevation")).floatValue();p.shelfZ=((Number)ownField(overlay,"shelfZ")).floatValue();
        });if(p.overlay!=null)return p;Thread.sleep(5);}while(android.os.SystemClock.elapsedRealtime()<limit);
        throw new Exception("Real Back/X never produced a closing-cover animator");
    }
    private void sameRectV31(RectF expected,RectF actual,String where)throws Exception {
        if(Math.abs(expected.left-actual.left)>1.5f||Math.abs(expected.top-actual.top)>1.5f||Math.abs(expected.right-actual.right)>1.5f||Math.abs(expected.bottom-actual.bottom)>1.5f)throw new Exception(where+": expected="+expected+", actual="+actual);
    }
    private void closeTimelineV31(MainActivity screen,BookCloseProbeV31 p,String date,String name,boolean anchor)throws Exception {
        try{
            uiAssert(()->{
                ViewGroup content=(ViewGroup)field(screen,"content");if(Boolean.TRUE.equals(field(screen,"inBookDetail"))||content.getChildCount()!=1||content.getChildAt(0)!=p.destination)throw new Exception("Close did not render and retain the actual return destination");
                if(p.page==null||p.cover==null||p.page.isRecycled()||p.cover.isRecycled()||p.paper.getAlpha()!=0||p.paper.getElevation()!=0||p.paper.getTranslationZ()!=0)throw new Exception("Closing lost its captured paper or retained a native square shadow");
                if(p.animator.getDuration()!=Math.round(800*p.initial)||!(p.animator.getInterpolator() instanceof android.view.animation.LinearInterpolator))throw new Exception("Closing is not an 800ms proportional reverse timeline");
                if(anchor&&(p.shelf==null||!date.equals(ownField(p.shelf.getBackground(),"date"))))throw new Exception("Close targets another date instead of the currently displayed notebook: "+date);
                if(!anchor&&p.shelf!=null)throw new Exception("No-anchor favorite list unexpectedly fabricated a tagged bookshelf cover");
                if(p.shelf!=null&&(p.shelf.getAlpha()!=0||p.shelf.getElevation()!=0||p.shelf.getTranslationZ()!=0))throw new Exception("Closing target remains visible or casts a duplicate native shadow");
            });
            for(float fraction:new float[]{0,.4f,.8f}){final float f=fraction;uiAssert(()->{
                p.animator.setCurrentPlayTime(Math.round(p.animator.getDuration()*f));float progress=((Number)ownField(p.overlay,"progress")).floatValue();if(Math.abs(progress-p.initial*(1-f))>.015f)throw new Exception("Close time/progress discontinuity at "+f);
                Bitmap frame=renderViewV26((View)p.overlay);try{saveFrameV24(frame,name+"-close-"+Math.round(f*100));}finally{frame.recycle();}
                RectF current=new RectF((RectF)ownField(p.overlay,"current"));if(f==0)sameRectV31(p.from,current,"First closing frame jumped away from its real paper");
                if(current.isEmpty()||current.width()>p.from.width()+2||current.height()>p.from.height()+2||current.width()<p.start.width()-2||current.height()<p.start.height()-2)throw new Exception("Closing geometry leaves its actual paper/cover bounds: "+current);
                if(ownField(p.overlay,"pageImage")!=p.page||ownField(p.overlay,"coverImage")!=p.cover||p.page.isRecycled()||p.cover.isRecycled())throw new Exception("Close reallocates or releases textures during its timeline");
                if(((ViewGroup)field(screen,"content")).getChildAt(0)!=p.destination)throw new Exception("Closing replaced its destination during animation");
            });}
            // Closed cover corners must be independent of the hidden paper texture,
            // including the very last shrinking frame. This uses actual renderer pixels.
            uiAssert(()->{Object backdrop=ownField(p.overlay,"outgoingScreen");try{setOwnV30(p.overlay,"outgoingScreen",null);p.animator.setCurrentPlayTime(Math.round(p.animator.getDuration()*.95f));p.page.eraseColor(Color.TRANSPARENT);Bitmap reference=renderViewV26((View)p.overlay),probe=null;try{RectF rect=new RectF((RectF)ownField(p.overlay,"current"));p.page.eraseColor(0xffff00ff);probe=renderViewV26((View)p.overlay);sameBookCornersV30(reference,probe,rect,name+" closing corners");}finally{reference.recycle();if(probe!=null)probe.recycle();}}finally{setOwnV30(p.overlay,"outgoingScreen",backdrop);invokeAnyV20(p.overlay,"drawPaperTexture",new Class[]{});}});
            uiAssert(()->p.animator.end());waitBookRestV31(screen,false);
            uiAssert(()->{if(!bookOpenLayersV25(screen).isEmpty()||!p.page.isRecycled()||!p.cover.isRecycled()||((ViewGroup)field(screen,"content")).getChildAt(0)!=p.destination)throw new Exception("Close end leaked an overlay/texture or rerendered its live destination");if(p.shelf!=null&&(p.shelf.getAlpha()!=p.shelfAlpha||p.shelf.getElevation()!=p.shelfElevation||p.shelf.getTranslationZ()!=p.shelfZ))throw new Exception("Close did not restore the real cover alpha/elevation/Z");});
        }finally{uiAssert(()->{if(p.animator.isPaused())p.animator.resume();});}
    }
    private void pendingBookInputV31(MainActivity screen,View frame,float x,float end,float y)throws Exception {
        Object callback=ownField(frame,"afterTurn");GestureSpring spring=(GestureSpring)ownField(frame,"spring");Object generation=ownField(spring,"generation");
        if(callback==null||!spring.isRunning())throw new Exception("Pending-close input fixture has no live settling owner");
        int[] location=new int[2];screen.getWindow().getDecorView().getLocationOnScreen(location);long down=android.os.SystemClock.uptimeMillis();
        for(int step=0;step<3;step++){int action=step==0?MotionEvent.ACTION_DOWN:step==1?MotionEvent.ACTION_MOVE:MotionEvent.ACTION_UP;float at=step==0?x:step==1?(x+end)*.5f:end;MotionEvent event=MotionEvent.obtain(down,android.os.SystemClock.uptimeMillis(),action,at-location[0],y-location[1],0);event.setSource(InputDevice.SOURCE_TOUCHSCREEN);try{screen.dispatchTouchEvent(event);}finally{event.recycle();}localPointerFallbacksV20++;
            if(!Boolean.TRUE.equals(field(screen,"bookClosePending"))||ownField(frame,"afterTurn")!=callback||!spring.isRunning()||!generation.equals(ownField(spring,"generation")))throw new Exception("New window input canceled/replaced the inner-turn pending close: action="+action+", x="+x);
            if(Boolean.TRUE.equals(field(screen,"backSwipeTracking")))throw new Exception("Pending-close input rearmed the legacy left-edge Back tracker: action="+action);
        }
    }
    /** Registered after the production listener, so its posted checks run after return work. */
    private final class BookReturnDrawProbeV33 implements android.view.ViewTreeObserver.OnDrawListener {
        final View root;final android.view.ViewTreeObserver observer;final UiAssertion assertion;
        final java.util.concurrent.CountDownLatch completed=new java.util.concurrent.CountDownLatch(1);
        final Runnable check;boolean posted;Throwable failure;
        BookReturnDrawProbeV33(View root,UiAssertion assertion){
            this.root=root;this.observer=root.getViewTreeObserver();this.assertion=assertion;
            check=()->{try{remove();this.assertion.run();}catch(Throwable error){failure=error;}finally{completed.countDown();}};
        }
        public void onDraw(){if(posted)return;posted=true;if(!root.post(check)){failure=new Exception("Cannot post after the real notebook return draw");completed.countDown();}}
        void register(){observer.addOnDrawListener(this);root.invalidate();}
        void remove(){if(observer.isAlive())observer.removeOnDrawListener(this);root.removeCallbacks(check);}
    }
    private void closeDuringBookTurnV31(MainActivity screen,String name)throws Exception {
        for(int variant=0;variant<3;variant++){
            final boolean configurationCallback=variant==2;final float drag=variant==0?.32f:.68f;
            uiAssert(()->{((JSONObject)field(screen,"profile")).put("bookTransitionMode","flip");invoke(screen,"showTab",new Class[]{int.class,boolean.class},1,false);});settleV15();bookCardTapV25(screen);waitBookRestV31(screen,true);
            View frame=(View)field(screen,"bookDetailSwipeFrame");Rect bounds=screenRect(frame);float x=bounds.left+bounds.width()*.87f,end=x-bounds.width()*drag,y=bounds.top+bounds.height()*.56f;long down=android.os.SystemClock.uptimeMillis();pointerV15(down,MotionEvent.ACTION_DOWN,x,y);movesV15(down,x,y,end,y,6);
            final String[] expected={null};final BookReturnDrawProbeV33[] probe={null};try{uiAssert(()->{
                // Coalesce the release and Back into one real window dispatch turn,
                // before the spring's first frame. Nearest visible paper is the oracle.
                int[] location=new int[2];screen.getWindow().getDecorView().getLocationOnScreen(location);MotionEvent up=MotionEvent.obtain(down,android.os.SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,end-location[0],y-location[1],0);up.setSource(InputDevice.SOURCE_TOUCHSCREEN);try{screen.dispatchTouchEvent(up);}finally{up.recycle();}localPointerFallbacksV20++;
                NotebookPageMotion motion=(NotebookPageMotion)ownField(frame,"motion");float position=motion.position();if(ownField(frame,"nextPage")==null||Math.abs(position-Math.round(position))<.05f)throw new Exception("Pending-close setup did not retain an actual fractional inner page");
                setMainField(screen,"backSwipeTracking",true);if(!Boolean.TRUE.equals(invoke(screen,"handleBackNavigation",new Class[]{}))||field(screen,"returnAfterDraw")==null||Boolean.TRUE.equals(field(screen,"bookClosePending"))||ownField(frame,"afterTurn")!=null)throw new Exception("Back did not consume and queue its real feedback draw before close preparation");
                Object queued=field(screen,"returnAfterDraw"),drawListener=field(screen,"returnDrawListener");if(!Boolean.TRUE.equals(invoke(screen,"handleBackNavigation",new Class[]{}))||field(screen,"returnAfterDraw")!=queued||field(screen,"returnDrawListener")!=drawListener||ownField(frame,"afterTurn")!=null||Boolean.TRUE.equals(field(screen,"bookClosePending")))throw new Exception("Repeated Back replaced or prematurely ran the draw-owned return");
                probe[0]=new BookReturnDrawProbeV33((View)field(screen,"root"),()->{
                // Production posts first from this draw. Its preparation has now
                // run, while the newly selected spring has not had its next frame.
                // The nearest-date oracle uses this actual callback position, not
                // the earlier release position before the feedback draw.
                NotebookPageMotion closingMotion=(NotebookPageMotion)ownField(frame,"motion");List<?> dates=(List<?>)ownField(frame,"pageDates");float closingPosition=closingMotion.position();expected[0]=(String)dates.get(Math.round(closingPosition));
                if(field(screen,"returnAfterDraw")!=null||field(screen,"returnDrawListener")!=null||Boolean.TRUE.equals(field(screen,"returnWorkExecuting"))||!Boolean.TRUE.equals(field(screen,"bookClosePending"))||Boolean.TRUE.equals(field(screen,"backSwipeTracking"))||closingMotion.target()!=Math.round(closingPosition))throw new Exception("Draw-owned Back did not wait for its nearest inner-page endpoint or clear the legacy tracker");
                Object after=ownField(frame,"afterTurn");invoke(screen,"handleBackNavigation",new Class[]{});if(ownField(frame,"afterTurn")!=after||!Boolean.TRUE.equals(field(screen,"bookClosePending")))throw new Exception("Repeated Back replaced the pending inner-page close");
                // These are synchronous real MainActivity-window input streams,
                // before the pending spring can advance to its next frame.
                pendingBookInputV31(screen,frame,bounds.exactCenterX(),bounds.exactCenterX()+2,y);
                float edge=PaperComponents.dp(screen,24)-1;pendingBookInputV31(screen,frame,edge,Math.min(bounds.right-8,edge+PaperComponents.dp(screen,120)),y);
                if(configurationCallback){
                    // Simulate only Activity's configuration callback. This does not
                    // rotate the physical/test device or claim orientation rendering.
                    android.content.res.Configuration changed=new android.content.res.Configuration(screen.getResources().getConfiguration());changed.orientation=changed.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE?android.content.res.Configuration.ORIENTATION_PORTRAIT:android.content.res.Configuration.ORIENTATION_LANDSCAPE;
                    setMainField(screen,"backSwipeTracking",true);invoke(screen,"onConfigurationChanged",new Class[]{android.content.res.Configuration.class},changed);
                    if(Boolean.TRUE.equals(field(screen,"bookClosePending"))||Boolean.TRUE.equals(field(screen,"backSwipeTracking"))||field(screen,"bookOpeningOverlay")!=null||ownField(frame,"afterTurn")!=null||((GestureSpring)ownField(frame,"spring")).isRunning()||!bookOpenLayersV25(screen).isEmpty()||Boolean.TRUE.equals(field(screen,"inBookDetail")))throw new Exception("Simulated configuration callback left the canceled pending close/tracker alive");
                }
                });probe[0].register();
            });
                if(!probe[0].completed.await(3,java.util.concurrent.TimeUnit.SECONDS))throw new Exception("Timed out waiting for the actual notebook return draw and ordered assertions");
                if(probe[0].failure!=null)throw new Exception("After-draw notebook close assertion",probe[0].failure);
            }finally{if(probe[0]!=null)uiAssert(()->probe[0].remove());}
            if(configurationCallback){
                settleV15();uiAssert(()->{if(!Boolean.TRUE.equals(invoke(screen,"rootPagesVisible",new Class[]{}))||Boolean.TRUE.equals(field(screen,"bookClosePending"))||field(screen,"bookOpeningOverlay")!=null)throw new Exception("Canceled pending close returned after the simulated configuration callback");invoke(screen,"openPreferencesPage",new Class[]{});});settleV15();sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);settleV15();
                uiAssert(()->{if(!Boolean.TRUE.equals(invoke(screen,"rootPagesVisible",new Class[]{}))||Boolean.TRUE.equals(field(screen,"inPreferencesPage"))||Boolean.TRUE.equals(field(screen,"bookClosePending"))||Boolean.TRUE.equals(field(screen,"backSwipeTracking")))throw new Exception("Stale pending-close state blocked real settings Back after simulated configuration callback");});continue;
            }
            BookCloseProbeV31 close=pauseBookCloseV31(screen);uiAssert(()->{if(!expected[0].equals(field(screen,"bookDetailDate"))||ownField(frame,"nextPage")!=null||ownField(frame,"turn")!=null||((GestureSpring)ownField(frame,"spring")).isRunning()||ownField(frame,"tracker")!=null||ownField(frame,"afterTurn")!=null)throw new Exception("Close captured a partial inner turn or wrong nearest date");});closeTimelineV31(screen,close,expected[0],name+"-pending-turn-"+Math.round(drag*100),true);
        }
    }
    private void bookClosingV31(MainActivity screen,android.content.SharedPreferences prefs,String before,String name)throws Exception {
        boolean animated=android.animation.ValueAnimator.areAnimatorsEnabled();
        for(String mode:new String[]{"normal","flip"})for(boolean back:new boolean[]{false,true}){
            uiAssert(()->{((JSONObject)field(screen,"profile")).put("bookTransitionMode",mode).put("footprintLayout","expanded");invoke(screen,"showTab",new Class[]{int.class,boolean.class},1,false);});settleV15();bookCardTapV25(screen);waitBookRestV31(screen,true);
            String date=(String)field(screen,"bookDetailDate");if(back)sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);else tapBookCloseV31(screen);
            if(animated)closeTimelineV31(screen,pauseBookCloseV31(screen),date,name+"-"+mode+"-"+(back?"back":"x"),true);else waitBookRestV31(screen,false);
            uiAssert(()->assertRenderPure(screen,prefs,before));
        }
        if(!animated)return;
        // Reverse the existing opening at a real mid-cover frame, retaining both textures.
        uiAssert(()->invoke(screen,"showTab",new Class[]{int.class,boolean.class},1,false));settleV15();bookCardTapV25(screen);
        final Object[] opening={null};final Bitmap[] page={null},cover={null};final RectF[] geometry={null};long until=android.os.SystemClock.elapsedRealtime()+3000;
        do{uiAssert(()->{Object current=field(screen,"bookOpeningOverlay");if(current==null)return;Object value=ownField(current,"animator");if(!(value instanceof android.animation.ValueAnimator))return;android.animation.ValueAnimator a=(android.animation.ValueAnimator)value;a.pause();a.setCurrentPlayTime(450);Bitmap rendered=renderViewV26((View)current);rendered.recycle();opening[0]=current;page[0]=(Bitmap)ownField(current,"pageImage");cover[0]=(Bitmap)ownField(current,"coverImage");geometry[0]=new RectF((RectF)ownField(current,"current"));});if(opening[0]!=null)break;Thread.sleep(5);}while(android.os.SystemClock.elapsedRealtime()<until);
        if(opening[0]==null)throw new Exception("Never observed the opening that X should reverse");tapBookCloseV31(screen);BookCloseProbeV31 reverse=pauseBookCloseV31(screen);
        uiAssert(()->{if(reverse.overlay!=opening[0]||reverse.page!=page[0]||reverse.cover!=cover[0]||Math.abs(reverse.initial-.45f)>.015f)throw new Exception("X finishes and recreates the opening instead of reversing it");sameRectV31(geometry[0],reverse.from,"Opening-to-close capture jumped");});
        closeTimelineV31(screen,reverse,(String)field(screen,"bookDetailDate"),name+"-mid-opening-reverse",true);
        // Turn one actual page before closing. The shelf target must follow its new date.
        uiAssert(()->invoke(screen,"showTab",new Class[]{int.class,boolean.class},1,false));settleV15();bookCardTapV25(screen);waitBookRestV31(screen,true);
        final String[] next={null};uiAssert(()->{String current=(String)field(screen,"bookDetailDate");next[0]=(String)invoke(screen,"adjacentBookDate",new Class[]{String.class,int.class},current,1);if(next[0]==null)throw new Exception("QA fixture has no internal next notebook page");Object frame=field(screen,"bookDetailSwipeFrame");invokeAnyV20(frame,"slideToDate",new Class[]{String.class,int.class},next[0],1);});settleV15();
        uiAssert(()->{if(!next[0].equals(field(screen,"bookDetailDate")))throw new Exception("Internal page never became the close date");});sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);closeTimelineV31(screen,pauseBookCloseV31(screen),next[0],name+"-latest-page-target",true);
        closeDuringBookTurnV31(screen,name);
        // Both favorite layouts have real covers and must retain their actual anchors.
        uiAssert(()->{((JSONObject)field(screen,"profile")).put("favoriteRecordsExpanded",true);invoke(screen,"renderFavoriteRecordsPage",new Class[]{});});settleV15();bookCardTapV25(screen);waitBookRestV31(screen,true);String favoriteDate=(String)field(screen,"bookDetailDate");tapBookCloseV31(screen);closeTimelineV31(screen,pauseBookCloseV31(screen),favoriteDate,name+"-favorite-anchor",true);uiAssert(()->{if(!Boolean.TRUE.equals(field(screen,"inFavoriteRecordsPage")))throw new Exception("Anchored close did not return to the favorites page");});
        uiAssert(()->{((JSONObject)field(screen,"profile")).put("favoriteRecordsExpanded",false);invoke(screen,"renderFavoriteRecordsPage",new Class[]{});});settleV15();bookCardTapV25(screen);waitBookRestV31(screen,true);String compactDate=(String)field(screen,"bookDetailDate");tapBookCloseV31(screen);closeTimelineV31(screen,pauseBookCloseV31(screen),compactDate,name+"-compact-favorite-anchor",true);uiAssert(()->{if(!Boolean.TRUE.equals(field(screen,"inFavoriteRecordsPage")))throw new Exception("Compact-cover close did not return to the actual favorites page");});
        // The oldest fixture date is deliberately not a favorite. Opening it with
        // a favorites return destination exercises the genuine missing-anchor path.
        final String[] noAnchorDate={null};uiAssert(()->{List<?> dates=(List<?>)invoke(screen,"recordedDates",new Class[]{});noAnchorDate[0]=(String)dates.get(dates.size()-1);if(((JSONObject)field(screen,"days")).getJSONObject(noAnchorDate[0]).optBoolean("favorite",false))throw new Exception("No-anchor fixture date was accidentally favorited");invoke(screen,"renderFavoriteRecordsPage",new Class[]{});setMainField(screen,"returnToFavoriteRecordsAfterBook",true);invoke(screen,"openBookDetail",new Class[]{String.class,View.class},noAnchorDate[0],null);});waitBookRestV31(screen,true);
        uiAssert(()->setMainField(screen,"returnToFavoriteRecordsAfterBook",true));sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);closeTimelineV31(screen,pauseBookCloseV31(screen),noAnchorDate[0],name+"-favorite-no-anchor",false);uiAssert(()->{if(!Boolean.TRUE.equals(field(screen,"inFavoriteRecordsPage"))||((JSONObject)field(screen,"days")).getJSONObject(noAnchorDate[0]).optBoolean("favorite",false))throw new Exception("No-anchor close changed the return route or fabricated a favorite");assertRenderPure(screen,prefs,before);});
        // A second Back must consume the current close. A later tab action cancels it
        // and cannot be overwritten by the obsolete animation's end callback.
        uiAssert(()->invoke(screen,"showTab",new Class[]{int.class,boolean.class},1,false));settleV15();bookCardTapV25(screen);waitBookRestV31(screen,true);tapBookCloseV31(screen);BookCloseProbeV31 interrupted=pauseBookCloseV31(screen);
        sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);uiAssert(()->{if(field(screen,"bookOpeningOverlay")!=interrupted.overlay||!Boolean.TRUE.equals(ownField(interrupted.overlay,"closing")))throw new Exception("Repeated Back escaped/recreated an ongoing close");invoke(screen,"showTab",new Class[]{int.class,boolean.class},0,true);});settleV15();
        uiAssert(()->{if(field(screen,"bookOpeningOverlay")!=null||Boolean.TRUE.equals(field(screen,"bookClosePending"))||!bookOpenLayersV25(screen).isEmpty()||((Integer)field(screen,"currentTab"))!=0||Boolean.TRUE.equals(field(screen,"inBookDetail")))throw new Exception("Canceled close later returned over the chosen root page");if(!interrupted.page.isRecycled()||!interrupted.cover.isRecycled())throw new Exception("Canceled close retained textures");if(interrupted.shelf!=null&&(interrupted.shelf.getAlpha()!=interrupted.shelfAlpha||interrupted.shelf.getElevation()!=interrupted.shelfElevation||interrupted.shelf.getTranslationZ()!=interrupted.shelfZ))throw new Exception("Canceled close retained hidden shelf cover");assertRenderPure(screen,prefs,before);});
    }
    private void settingsRestV31(MainActivity screen)throws Exception {
        uiAssert(()->{PageCrossfade p=(PageCrossfade)field(screen,"pageCrossfade");if(p!=null&&p.active())throw new Exception("Settings transition retained its old scene");ViewGroup content=(ViewGroup)field(screen,"content");if(content.getChildCount()!=1)throw new Exception("Settings left an unexpected content layer count: "+content.getChildCount());View page=content.getChildAt(0);if(page.getAlpha()!=1||page.getTranslationX()!=0||page.getTranslationY()!=0||page.getScaleX()!=1||page.getScaleY()!=1)throw new Exception("Settings did not restore the live root transform");if(page.getBackground()!=null&&page.getBackground().getClass().getSimpleName().equals("PaperSurface"))throw new Exception("Temporary settings background leaked beyond the animation");});
    }
    private void settingsTimelineV31(MainActivity screen,String method,String name)throws Exception {
        final PageCrossfade[] transition={null};final android.animation.ValueAnimator[] animator={null};final Object[] scene={null};final ArrayList<Object> surfaces=new ArrayList<>();final ArrayList<View> retainedOld=new ArrayList<>();final ArrayList<Float> oldOrigins=new ArrayList<>(),oldAlphas=new ArrayList<>();
        uiAssert(()->{invoke(screen,method,new Class[]{});transition[0]=(PageCrossfade)field(screen,"pageCrossfade");if(android.animation.ValueAnimator.areAnimatorsEnabled()){
            if(transition[0]==null||!transition[0].active())throw new Exception("Settings route bypassed the unified transition: "+method);
            for(Object value:(List<?>)ownField(transition[0],"incoming")){View view=(View)value;if(view.getAlpha()!=1||Math.abs(view.getTranslationX())<1)throw new Exception("New settings root lacks an opaque translated surface before its first frame: "+method);}
        }});
        if(!android.animation.ValueAnimator.areAnimatorsEnabled()){settleV15();settingsRestV31(screen);return;}
        long until=android.os.SystemClock.elapsedRealtime()+2500;do{uiAssert(()->{Object a=ownField(transition[0],"animator");if(!(a instanceof android.animation.ValueAnimator))return;animator[0]=(android.animation.ValueAnimator)a;animator[0].pause();scene[0]=ownField(transition[0],"image");surfaces.addAll((List<?>)ownField(transition[0],"incomingSurfaces"));});if(animator[0]!=null)break;Thread.sleep(5);}while(android.os.SystemClock.elapsedRealtime()<until);
        if(animator[0]==null)throw new Exception("Settings never started a real animator: "+method);
        try{
            uiAssert(()->{if(animator[0].getDuration()!=300||!(animator[0].getInterpolator() instanceof android.view.animation.LinearInterpolator)||((Integer)ownField(scene[0],"shiftDp"))!=16)throw new Exception("Settings route uses a different/abrupt motion timeline: "+method);if(ownField(scene[0],"bitmap")!=null||surfaces.isEmpty())throw new Exception("Routine settings entry uses a screenshot or lacks an incoming surface");for(Object old:(List<?>)ownField(scene[0],"outgoing"))retainedOld.add((View)old);for(Object origin:(List<?>)ownField(scene[0],"origins"))oldOrigins.add(((Number)origin).floatValue());for(Object alpha:(List<?>)ownField(scene[0],"alphas"))oldAlphas.add(((Number)alpha).floatValue());});
            for(float fraction:new float[]{0,.4f,.8f}){final float f=fraction;uiAssert(()->{animator[0].setCurrentPlayTime(Math.round(animator[0].getDuration()*f));
                for(Object value:(List<?>)ownField(transition[0],"incoming")){View view=(View)value;if(view.getAlpha()!=1||Math.abs(view.getTranslationX())>PaperComponents.dp(screen,17))throw new Exception("Settings opaque surface/translation jumps at "+f+": "+method);}
                List<?> outgoing=(List<?>)ownField(scene[0],"outgoing"),alphas=(List<?>)ownField(scene[0],"alphas");for(int i=0;i<outgoing.size();i++)if(Math.abs(((View)outgoing.get(i)).getAlpha()-((Number)alphas.get(i)).floatValue()*(1-f))>.02f)throw new Exception("Outgoing settings scene does not dissolve over its opaque incoming surface: "+method);
                for(Object surface:surfaces){View view=(View)ownField(surface,"view");if(view.getPaddingLeft()!=((Integer)ownField(surface,"left"))||view.getPaddingTop()!=((Integer)ownField(surface,"top"))||view.getPaddingRight()!=((Integer)ownField(surface,"right"))||view.getPaddingBottom()!=((Integer)ownField(surface,"bottom")))throw new Exception("Temporary settings surface changed page padding");}
                if("openNotebookSettingsPage".equals(method)){Bitmap frame=renderViewV26((View)field(screen,"content"));try{saveFrameV24(frame,name+"-settings-"+Math.round(f*100));}finally{frame.recycle();}}
            });}
            uiAssert(()->{animator[0].end();for(Object surface:surfaces){View view=(View)ownField(surface,"view");if(view.getBackground()!=ownField(surface,"original"))throw new Exception("Settings did not restore the original background identity: "+method);}for(int i=0;i<retainedOld.size();i++){View old=retainedOld.get(i);if(Math.abs(old.getTranslationX()-oldOrigins.get(i))>.001f||Math.abs(old.getAlpha()-oldAlphas.get(i))>.001f)throw new Exception("Finished non-peer route left its retained old page transformed/transparent: "+method);}});settingsRestV31(screen);
        }finally{uiAssert(()->{if(animator[0].isPaused())animator[0].resume();});}
    }
    private int blendColorV31(int top,int bottom,float alpha){return Color.rgb(Math.round(Color.red(top)*alpha+Color.red(bottom)*(1-alpha)),Math.round(Color.green(top)*alpha+Color.green(bottom)*(1-alpha)),Math.round(Color.blue(top)*alpha+Color.blue(bottom)*(1-alpha)));}
    private void nearColorV31(int expected,int actual,int tolerance,String where)throws Exception {
        if(Color.alpha(actual)<250||Math.abs(Color.red(expected)-Color.red(actual))>tolerance||Math.abs(Color.green(expected)-Color.green(actual))>tolerance||Math.abs(Color.blue(expected)-Color.blue(actual))>tolerance)throw new Exception(where+": expected="+Integer.toHexString(expected)+", actual="+Integer.toHexString(actual));
    }
    private void addTransparentMarkerV31(FrameLayout host,int color){
        host.removeAllViews();FrameLayout page=new FrameLayout(host.getContext());View marker=new View(host.getContext());marker.setBackgroundColor(color);FrameLayout.LayoutParams markerLp=new FrameLayout.LayoutParams(360,360,Gravity.CENTER);page.addView(marker,markerLp);host.addView(page,new FrameLayout.LayoutParams(-1,-1));
    }
    /** Check native disappearing-child composition, independently of View alpha fields. */
    private void settingsCompositionV31(MainActivity screen,String name)throws Exception {
        if(!android.animation.ValueAnimator.areAnimatorsEnabled())return;
        final int paper=0xfff7f1e1,oldColor=0xffea2648,newColor=0xff1e94ec,nextColor=0xff25c568;
        final FrameLayout[] host={null};final PageCrossfade[] transition={null};final android.animation.ValueAnimator[] animator={null};
        uiAssert(()->{host[0]=new FrameLayout(screen);host[0].setBackgroundColor(Color.MAGENTA);((ViewGroup)field(screen,"root")).addView(host[0],new FrameLayout.LayoutParams(600,600));addTransparentMarkerV31(host[0],oldColor);transition[0]=new PageCrossfade(host[0]);transition[0].setPaperColor(paper);});
        try{
            waitForIdleSync();uiAssert(()->{if(!host[0].isAttachedToWindow()||host[0].getWidth()!=600||host[0].getHeight()!=600)throw new Exception("Composition fixture is not a real attached/laid-out host");transition[0].run(()->addTransparentMarkerV31(host[0],newColor),1);});
            long until=android.os.SystemClock.elapsedRealtime()+2500;do{uiAssert(()->{Object value=ownField(transition[0],"animator");if(value instanceof android.animation.ValueAnimator){animator[0]=(android.animation.ValueAnimator)value;animator[0].pause();animator[0].setCurrentPlayTime(0);}});if(animator[0]!=null)break;Thread.sleep(5);}while(android.os.SystemClock.elapsedRealtime()<until);
            if(animator[0]==null)throw new Exception("Attached composition fixture never started its animator");
            for(float fraction:new float[]{0,.25f,.5f,.75f,.9f}){final float p=fraction;uiAssert(()->{
                animator[0].setCurrentPlayTime(Math.round(animator[0].getDuration()*p));Bitmap drawn=renderViewV26(host[0]),captured=(Bitmap)invokeAnyV20(transition[0],"capturePresentation",new Class[]{});
                if(captured==null){drawn.recycle();throw new Exception("Active composition capture returned no actual content");}
                try{
                    // Each transparent page root receives one opaque paper surface.
                    // The old root dissolves once over the new one; a separate faded
                    // backing must not wash either marker toward the paper color.
                    float oldAlpha=Math.round((1-p)*255)/255f;int expected=blendColorV31(oldColor,newColor,oldAlpha);
                    for(int y:new int[]{260,300,340})for(int x:new int[]{260,300,340}){
                        int actual=drawn.getPixel(x,y);nearColorV31(expected,actual,5,"Native host.draw hid/dropped a settings page at p="+p);nearColorV31(actual,captured.getPixel(x,y),3,"Interrupted capture disagrees with actual host.draw at p="+p);
                        if(p>0&&p<1&&(Math.abs(Color.red(actual)-Color.red(oldColor))<5&&Math.abs(Color.blue(actual)-Color.blue(oldColor))<5||Math.abs(Color.red(actual)-Color.red(newColor))<5&&Math.abs(Color.blue(actual)-Color.blue(newColor))<5))throw new Exception("Settings middle frame is an unmixed endpoint at p="+p);
                    }
                    // Probe transparent page areas too: neither color marker covers
                    // these points, so a magenta/transparent gap cannot pass.
                    for(int[] point:new int[][]{{80,80},{300,80},{520,80},{80,520},{300,520},{520,520}}){nearColorV31(paper,drawn.getPixel(point[0],point[1]),3,"Opaque settings paper exposed its host at p="+p);nearColorV31(drawn.getPixel(point[0],point[1]),captured.getPixel(point[0],point[1]),3,"Transparent-root capture differs at p="+p);}
                    saveFrameV24(drawn,name+"-settings-native-composition-"+Math.round(p*100));saveFrameV24(captured,name+"-settings-captured-composition-"+Math.round(p*100));
                }finally{drawn.recycle();captured.recycle();}
            });}
            // A mid-frame interruption must capture that exact two-page blend,
            // then dissolve its retained snapshot over the next opaque root.
            uiAssert(()->{animator[0].setCurrentPlayTime(150);Bitmap before=renderViewV26(host[0]);try{transition[0].run(()->addTransparentMarkerV31(host[0],nextColor),-1);Object scene=ownField(transition[0],"image");Bitmap snapshot=(Bitmap)ownField(scene,"bitmap");if(snapshot==null)throw new Exception("Mid-frame settings interruption did not retain its visible composition");for(int y:new int[]{260,300,340})for(int x:new int[]{260,300,340})nearColorV31(before.getPixel(x,y),snapshot.getPixel(x,y),3,"Retarget snapshot lost the mixed previous scene");Bitmap first=renderViewV26(host[0]);try{for(int y:new int[]{260,300,340})for(int x:new int[]{260,300,340})nearColorV31(before.getPixel(x,y),first.getPixel(x,y),3,"Retarget's first native frame jumped to another page");}finally{first.recycle();}}finally{before.recycle();}animator[0]=null;});
            until=android.os.SystemClock.elapsedRealtime()+2500;do{uiAssert(()->{Object value=ownField(transition[0],"animator");if(value instanceof android.animation.ValueAnimator){animator[0]=(android.animation.ValueAnimator)value;animator[0].pause();}});if(animator[0]!=null)break;Thread.sleep(5);}while(android.os.SystemClock.elapsedRealtime()<until);
            if(animator[0]==null)throw new Exception("Retargeted composition fixture never started its animator");
            uiAssert(()->{Object scene=ownField(transition[0],"image");Bitmap snapshot=(Bitmap)ownField(scene,"bitmap");int previous=snapshot.getPixel(300,300);for(float p:new float[]{.25f,.5f,.75f}){animator[0].setCurrentPlayTime(Math.round(animator[0].getDuration()*p));Bitmap actual=renderViewV26(host[0]),captured=(Bitmap)invokeAnyV20(transition[0],"capturePresentation",new Class[]{});try{if(captured==null)throw new Exception("Retargeted settings capture vanished");nearColorV31(blendColorV31(previous,nextColor,Math.round((1-p)*255)/255f),actual.getPixel(300,300),5,"Retargeted native composition is not visibly mixed at p="+p);nearColorV31(actual.getPixel(300,300),captured.getPixel(300,300),3,"Retargeted host/capture composition disagree at p="+p);}finally{actual.recycle();if(captured!=null)captured.recycle();}}animator[0].end();View live=host[0].getChildAt(0);if(transition[0].active()||host[0].getChildCount()!=1||live.getAlpha()!=1||live.getTranslationX()!=0||live.getBackground()!=null)throw new Exception("Composition fixture leaked a temporary surface or obsolete outgoing page");Bitmap finalFrame=renderViewV26(host[0]);try{nearColorV31(nextColor,finalFrame.getPixel(300,300),1,"Completed native transition did not reveal its real new page");}finally{finalFrame.recycle();}});
            // Cancellation before the next pre-draw must restore the retained old
            // root's source values, rather than leaving its intermediate transform.
            uiAssert(()->{View previous=host[0].getChildAt(0);float alpha=previous.getAlpha(),origin=previous.getTranslationX();transition[0].run(()->addTransparentMarkerV31(host[0],newColor),1);invokeAnyV20(transition[0],"position",new Class[]{float.class},.4f);if(Math.abs(previous.getAlpha()-alpha*.6f)>.001f||Math.abs(previous.getTranslationX()-origin)<1)throw new Exception("Non-peer cancel fixture did not enter a genuinely faded/moving intermediate state");transition[0].clear();if(Math.abs(previous.getAlpha()-alpha)>.001f||Math.abs(previous.getTranslationX()-origin)>.001f||previous.getBackground()!=null)throw new Exception("Clearing non-peer transition failed to restore old alpha/origin/background");View live=host[0].getChildAt(0);if(live.getAlpha()!=1||live.getTranslationX()!=0||live.getBackground()!=null||transition[0].active())throw new Exception("Clearing non-peer transition left its new page hidden/transformed");});
        }finally{uiAssert(()->{transition[0].clear();((ViewGroup)field(screen,"root")).removeView(host[0]);});}
    }
    private void settingsTransitionsV31(MainActivity screen,android.content.SharedPreferences prefs,String before,String name)throws Exception {
        settingsCompositionV31(screen,name);
        for(String[] route:new String[][]{{"openThemeSettingsPage","closeThemeSettingsPage"},{"openProfileSettingsPage","closeProfileSettingsPage"},{"openAboutSoftwarePage","closeAboutSoftwarePage"},{"openFontSourcesPage","closeFontSourcesPage"},{"openHintManagerPage","closeHintManagerPage"},{"openVibrationPage","closeVibrationPage"},{"openStickerSettingsPage","closeStickerSettingsPage"},{"openFootprintSettingsPage","closeFootprintSettingsPage"},{"openNotebookSettingsPage","closeNotebookSettingsPage"},{"openInterfaceLayoutPage","closeInterfaceLayoutPage"},{"openMealReminderSettingsPage","closeMoreSettingsPage"},{"openDataSecurityPage","closeDataSecurityPage"},{"openCloudSyncPage","closeCloudSyncPage"},{"openRecycleBinPage","closeRecycleBinPage"},{"openFavoriteRecordsPage","closeFavoriteRecordsPage"}}){
            uiAssert(()->invoke(screen,"showTab",new Class[]{int.class,boolean.class},3,false));settleV15();settingsTimelineV31(screen,"openPreferencesPage",name);settingsTimelineV31(screen,route[0],name);settingsTimelineV31(screen,route[1],name);uiAssert(()->assertRenderPure(screen,prefs,before));
        }
        // Interrupt before layout and during motion, then refresh in place while
        // preserving scroll. Neither removed children nor their callbacks may survive.
        uiAssert(()->{invoke(screen,"openPreferencesPage",new Class[]{});invoke(screen,"openNotebookSettingsPage",new Class[]{});invoke(screen,"closeNotebookSettingsPage",new Class[]{});invoke(screen,"openInterfaceLayoutPage",new Class[]{});invoke(screen,"closeInterfaceLayoutPage",new Class[]{});});settleV15();settingsRestV31(screen);
        final int[] y={0};uiAssert(()->{android.widget.ScrollView scroll=(android.widget.ScrollView)invoke(screen,"currentPageScroll",new Class[]{});if(scroll==null)throw new Exception("Preferences lost its real scroll root");scroll.scrollTo(0,Math.min(180,Math.max(0,scroll.getChildAt(0).getHeight()-scroll.getHeight())));y[0]=scroll.getScrollY();Runnable refresh=()->{try{invoke(screen,"renderPreferencesPage",new Class[]{});}catch(Exception failure){throw new RuntimeException(failure);}};invoke(screen,"rerenderPreservingScroll",new Class[]{Runnable.class},refresh);});settleV15();settingsRestV31(screen);
        uiAssert(()->{android.widget.ScrollView scroll=(android.widget.ScrollView)invoke(screen,"currentPageScroll",new Class[]{});if(scroll==null||Math.abs(scroll.getScrollY()-y[0])>1)throw new Exception("In-place preferences refresh reset its real scroll position");invoke(screen,"openNotebookSettingsPage",new Class[]{});Runnable refresh=()->{try{invoke(screen,"renderNotebookSettingsPage",new Class[]{});}catch(Exception failure){throw new RuntimeException(failure);}};invoke(screen,"rerenderPreservingScroll",new Class[]{Runnable.class},refresh);});settleV15();settingsRestV31(screen);
        // Theme captures are separate overlays; any subsequent navigation owns the
        // window and must remove the obsolete snapshot immediately.
        uiAssert(()->{invoke(screen,"beginThemeModeTransition",new Class[]{});View old=(View)field(screen,"themeModeTransitionOverlay");if(android.animation.ValueAnimator.areAnimatorsEnabled()&&old==null)throw new Exception("Theme interrupt setup did not create its real snapshot");invoke(screen,"openNotebookSettingsPage",new Class[]{});if(field(screen,"themeModeTransitionOverlay")!=null||old!=null&&old.getParent()!=null)throw new Exception("New settings navigation retains a stale theme snapshot");});settleV15();settingsRestV31(screen);
        uiAssert(()->{invoke(screen,"showTab",new Class[]{int.class,boolean.class},3,true);invoke(screen,"showTab",new Class[]{int.class,boolean.class},0,true);});settleV15();settingsRestV31(screen);uiAssert(()->assertRenderPure(screen,prefs,before));
    }
    private void settingsTimelineV32(MainActivity screen,String method,String name)throws Exception {
        final PageCrossfade[] transition={null};final android.animation.ValueAnimator[] animator={null};final Object[] scene={null};final ArrayList<Object> surfaces=new ArrayList<>();final ArrayList<View> retainedOld=new ArrayList<>();final ArrayList<Float> oldOrigins=new ArrayList<>(),oldAlphas=new ArrayList<>();
        uiAssert(()->{invoke(screen,method,new Class[]{});transition[0]=(PageCrossfade)field(screen,"pageCrossfade");if(android.animation.ValueAnimator.areAnimatorsEnabled()){
            if(transition[0]==null||!transition[0].active())throw new Exception("Settings route bypassed the unified transition: "+method);
            for(Object value:(List<?>)ownField(transition[0],"incoming")){View view=(View)value;if(view.getAlpha()!=1||Math.abs(view.getTranslationX())<1)throw new Exception("New settings root lacks an opaque translated surface before its first frame: "+method);}
        }});
        if(!android.animation.ValueAnimator.areAnimatorsEnabled()){settleV15();settingsRestV31(screen);return;}
        long until=android.os.SystemClock.elapsedRealtime()+2500;do{uiAssert(()->{Object a=ownField(transition[0],"animator");if(!(a instanceof android.animation.ValueAnimator))return;animator[0]=(android.animation.ValueAnimator)a;animator[0].pause();scene[0]=ownField(transition[0],"image");surfaces.addAll((List<?>)ownField(transition[0],"incomingSurfaces"));});if(animator[0]!=null)break;Thread.sleep(5);}while(android.os.SystemClock.elapsedRealtime()<until);
        if(animator[0]==null)throw new Exception("Settings never started a real animator: "+method);
        try{
            uiAssert(()->{if(animator[0].getDuration()!=480||!(animator[0].getInterpolator() instanceof android.view.animation.LinearInterpolator)||Math.abs(((Number)ownField(scene[0],"travel")).floatValue()-((View)field(screen,"content")).getWidth())>.01f)throw new Exception("Settings route uses a different/abrupt motion timeline: "+method);if(ownField(scene[0],"bitmap")!=null||surfaces.isEmpty())throw new Exception("Routine settings entry uses a screenshot or lacks an incoming surface");for(Object old:(List<?>)ownField(scene[0],"outgoing"))retainedOld.add((View)old);for(Object origin:(List<?>)ownField(scene[0],"origins"))oldOrigins.add(((Number)origin).floatValue());for(Object alpha:(List<?>)ownField(scene[0],"alphas"))oldAlphas.add(((Number)alpha).floatValue());});
            for(float fraction:new float[]{0,.4f,.8f}){final float f=fraction;uiAssert(()->{animator[0].setCurrentPlayTime(Math.round(animator[0].getDuration()*f));
                float width=((View)field(screen,"content")).getWidth(),direction=Boolean.TRUE.equals(ownField(scene[0],"forward"))?1:-1,movement=((android.animation.TimeInterpolator)ownField(scene[0],"positionEase")).getInterpolation(f);
                for(Object value:(List<?>)ownField(transition[0],"incoming")){View view=(View)value;if(view.getAlpha()!=1||Math.abs(view.getTranslationX()-direction*width*(1-movement))>1.5f)throw new Exception("Settings incoming page does not follow one full-width opaque sheet at "+f+": "+method);}
                List<?> outgoing=(List<?>)ownField(scene[0],"outgoing"),alphas=(List<?>)ownField(scene[0],"alphas"),origins=(List<?>)ownField(scene[0],"origins");for(int i=0;i<outgoing.size();i++){View old=(View)outgoing.get(i);if(Math.abs(old.getAlpha()-((Number)alphas.get(i)).floatValue())>.001f||Math.abs(old.getTranslationX()-(((Number)origins.get(i)).floatValue()-direction*width*movement))>1.5f)throw new Exception("Outgoing settings scene fades or leaves the adjoining sheet edge: "+method);}
                for(Object surface:surfaces){View view=(View)ownField(surface,"view");if(view.getPaddingLeft()!=((Integer)ownField(surface,"left"))||view.getPaddingTop()!=((Integer)ownField(surface,"top"))||view.getPaddingRight()!=((Integer)ownField(surface,"right"))||view.getPaddingBottom()!=((Integer)ownField(surface,"bottom")))throw new Exception("Temporary settings surface changed page padding");}
                if("openNotebookSettingsPage".equals(method)){Bitmap frame=renderViewV26((View)field(screen,"content"));try{saveFrameV24(frame,name+"-settings-"+Math.round(f*100));}finally{frame.recycle();}}
            });}
            uiAssert(()->{animator[0].end();for(Object surface:surfaces){View view=(View)ownField(surface,"view");if(view.getBackground()!=ownField(surface,"original"))throw new Exception("Settings did not restore the original background identity: "+method);Object original=ownField(surface,"original");if(original instanceof android.graphics.drawable.Drawable&&((android.graphics.drawable.Drawable)original).getCallback()!=ownField(surface,"originalCallback"))throw new Exception("Settings did not restore the original Drawable callback: "+method);}for(int i=0;i<retainedOld.size();i++){View old=retainedOld.get(i);if(Math.abs(old.getTranslationX()-oldOrigins.get(i))>.001f||Math.abs(old.getAlpha()-oldAlphas.get(i))>.001f)throw new Exception("Finished non-peer route left its retained old page transformed/transparent: "+method);}});settingsRestV31(screen);
        }finally{uiAssert(()->{if(animator[0].isPaused())animator[0].resume();});}
    }

    private android.animation.ValueAnimator pauseSettingsV32(PageCrossfade transition)throws Exception {
        final android.animation.ValueAnimator[] result={null};long until=android.os.SystemClock.elapsedRealtime()+2500;
        do{uiAssert(()->{Object value=ownField(transition,"animator");if(value instanceof android.animation.ValueAnimator){result[0]=(android.animation.ValueAnimator)value;result[0].pause();result[0].setCurrentPlayTime(0);}});if(result[0]!=null)return result[0];Thread.sleep(5);}while(android.os.SystemClock.elapsedRealtime()<until);
        throw new Exception("Opaque sheet fixture never started its actual animator");
    }
    private void addMarkerPageV32(FrameLayout host,int color,boolean multi){
        addTransparentMarkerV31(host,color);host.getChildAt(0).setPadding(9,9,9,9);
        if(multi){FrameLayout overlay=new FrameLayout(host.getContext());View strip=new View(host.getContext());strip.setBackgroundColor(0xffedce26);FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(500,50);lp.leftMargin=50;lp.topMargin=40;overlay.addView(strip,lp);host.addView(overlay,new FrameLayout.LayoutParams(-1,-1));}
    }
    /** A paper Drawable may fill its entire Canvas, independent of its own bounds. */
    private void surfaceBoundsV32(FrameLayout host,int paper)throws Exception {
        android.graphics.drawable.Drawable surface=host.getChildAt(0).getBackground();if(surface==null||!"PaperSurface".equals(surface.getClass().getSimpleName()))throw new Exception("Bounds fixture is not exercising the actual temporary paper surface");
        Rect previous=surface.copyBounds(),bounds=new Rect(23,29,147,181);Bitmap image=Bitmap.createBitmap(200,220,Bitmap.Config.ARGB_8888);image.eraseColor(Color.MAGENTA);
        try{surface.setBounds(bounds);surface.draw(new Canvas(image));for(int y=3;y<217;y+=11)for(int x=3;x<197;x+=13){int expected=bounds.contains(x,y)?paper:Color.MAGENTA;nearColorV31(expected,image.getPixel(x,y),1,"Temporary paper surface paints outside its local Drawable bounds at "+x+","+y);}}finally{surface.setBounds(previous);image.recycle();}
    }
    private Bitmap markerReferenceV32(int paper,int color,boolean multi){
        Bitmap bitmap=Bitmap.createBitmap(600,600,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);canvas.drawColor(paper);Paint paint=new Paint();paint.setColor(color);canvas.drawRect(120,120,480,480,paint);if(multi){paint.setColor(0xffedce26);canvas.drawRect(50,40,550,90,paint);}return bitmap;
    }
    private boolean uniformReferenceV32(Bitmap ref,int x,int y){
        if(x<3||x>=ref.getWidth()-3||y<3||y>=ref.getHeight()-3)return false;int color=ref.getPixel(x,y);return ref.getPixel(x-3,y)==color&&ref.getPixel(x+3,y)==color&&ref.getPixel(x,y-3)==color&&ref.getPixel(x,y+3)==color;
    }
    /** Spatial oracle: each output pixel comes from one opaque page, never a color blend. */
    private void assertSheetsV32(FrameLayout host,PageCrossfade transition,android.animation.ValueAnimator animator,Bitmap oldRef,Bitmap newRef,int direction,float p,String name)throws Exception {
        long playTime=Math.round(animator.getDuration()*p);animator.setCurrentPlayTime(playTime);Object scene=ownField(transition,"image");float clock=playTime/(float)animator.getDuration();if(Math.abs(((Number)ownField(scene,"progress")).floatValue()-clock)>.002f)throw new Exception("Opaque sheet clock is not linear");float width=host.getWidth(),movement=((android.animation.TimeInterpolator)ownField(scene,"positionEase")).getInterpolation(clock),shift=width*movement,seam=direction>0?width-shift:shift;
        if(animator.getDuration()!=480||!(animator.getInterpolator() instanceof android.view.animation.LinearInterpolator)||Math.abs(((Number)ownField(scene,"travel")).floatValue()-width)>.01f)throw new Exception("Opaque sheet fixture changed its 480ms/full-width linear-clock contract");
        android.animation.TimeInterpolator pageCurve=UiTokens.motionEasePage(),actualCurve=(android.animation.TimeInterpolator)ownField(scene,"positionEase");if(UiTokens.PAGE_FLOW_MS!=480)throw new Exception("Page flow token changed its explicit 480ms contract");for(float probe:new float[]{.05f,.2f,.5f,.8f,.95f})if(Math.abs(actualCurve.getInterpolation(probe)-pageCurve.getInterpolation(probe))>.0001f)throw new Exception("Opaque sheets use another curve instead of the page-flow ease");
        for(Object value:(List<?>)ownField(transition,"incoming")){View view=(View)value;if(view.getAlpha()!=1||Math.abs(view.getTranslationX()-direction*(width-shift))>1.5f)throw new Exception("Incoming sheet fades or separates from the shared edge");}
        List<?> outgoing=(List<?>)ownField(scene,"outgoing"),origins=(List<?>)ownField(scene,"origins"),alphas=(List<?>)ownField(scene,"alphas");for(int i=0;i<outgoing.size();i++){View view=(View)outgoing.get(i);if(Math.abs(view.getAlpha()-((Number)alphas.get(i)).floatValue())>.001f||Math.abs(view.getTranslationX()-(((Number)origins.get(i)).floatValue()-direction*shift))>1.5f)throw new Exception("Outgoing sheet fades or follows another displacement");}
        Bitmap actual=renderViewV26(host),captured=(Bitmap)invokeAnyV20(transition,"capturePresentation",new Class[]{});if(captured==null){actual.recycle();throw new Exception("Opaque sheet capture lost its active presentation");}
        try{int probes=0;for(int y=10;y<590;y+=17)for(int x=10;x<590;x+=13){if(Math.abs(x-seam)<4)continue;boolean old=direction>0?x<seam:x>=seam;Bitmap ref=old?oldRef:newRef;float tx=old?-direction*shift:direction*(width-shift);int local=Math.round(x-tx);if(!uniformReferenceV32(ref,local,y))continue;int expected=ref.getPixel(local,y);nearColorV31(expected,actual.getPixel(x,y),3,"Opaque sheet blended/blank/jumped at p="+p+", dir="+direction+", x="+x+", y="+y);nearColorV31(actual.getPixel(x,y),captured.getPixel(x,y),3,"Native host/capture disagree at p="+p+", x="+x+", y="+y);probes++;}if(probes<300)throw new Exception("Opaque sheet oracle did not cover enough non-edge pixels: "+probes);if(p==.1f||p==.5f)saveFrameV24(actual,name+"-opaque-sheets-"+direction+"-"+Math.round(p*100));}finally{actual.recycle();captured.recycle();}
    }
    private void finishSheetsV32(FrameLayout host,PageCrossfade transition,android.animation.ValueAnimator animator)throws Exception {
        Object scene=ownField(transition,"image");ArrayList<View> outgoing=new ArrayList<>();for(Object v:(List<?>)ownField(scene,"outgoing"))outgoing.add((View)v);ArrayList<?> origins=new ArrayList<>((List<?>)ownField(scene,"origins")),alphas=new ArrayList<>((List<?>)ownField(scene,"alphas"));ArrayList<?> surfaces=new ArrayList<>((List<?>)ownField(transition,"incomingSurfaces"));animator.end();
        if(transition.active()||host.getChildCount()!=1||host.getChildAt(0).getAlpha()!=1||host.getChildAt(0).getTranslationX()!=0)throw new Exception("Opaque sheet end left a stale scene or transformed live root");
        for(int i=0;i<outgoing.size();i++){View old=outgoing.get(i);if(old.isAttachedToWindow()||Math.abs(old.getTranslationX()-((Number)origins.get(i)).floatValue())>.001f||Math.abs(old.getAlpha()-((Number)alphas.get(i)).floatValue())>.001f)throw new Exception("Opaque sheet end retained/transformed its old root");}
        for(Object surface:surfaces){View view=(View)ownField(surface,"view");Object original=ownField(surface,"original");if(view.getBackground()!=original)throw new Exception("Opaque sheet end leaked its PaperSurface");if(original instanceof android.graphics.drawable.Drawable&&((android.graphics.drawable.Drawable)original).getCallback()!=ownField(surface,"originalCallback"))throw new Exception("Opaque sheet end changed the Drawable callback");if(view.getPaddingLeft()!=((Number)ownField(surface,"left")).intValue()||view.getPaddingTop()!=((Number)ownField(surface,"top")).intValue()||view.getPaddingRight()!=((Number)ownField(surface,"right")).intValue()||view.getPaddingBottom()!=((Number)ownField(surface,"bottom")).intValue())throw new Exception("Opaque sheet end changed root padding");}
    }
    private void settingsCompositionV32(MainActivity screen,String name)throws Exception {
        if(!android.animation.ValueAnimator.areAnimatorsEnabled())return;
        final int paper=0xfff7f1e1,red=0xffea2648,blue=0xff1e94ec,green=0xff25c568;final FrameLayout[] host={null};final PageCrossfade[] transition={null};
        uiAssert(()->{host[0]=new FrameLayout(screen);host[0].setBackgroundColor(Color.MAGENTA);((ViewGroup)field(screen,"root")).addView(host[0],new FrameLayout.LayoutParams(600,600));transition[0]=new PageCrossfade(host[0]);transition[0].setPaperColor(paper);transition[0].setPaperBackground(new android.graphics.drawable.Drawable(){@Override public void draw(Canvas canvas){canvas.drawColor(paper);}@Override public void setAlpha(int alpha){}@Override public void setColorFilter(ColorFilter filter){}@Override public int getOpacity(){return PixelFormat.OPAQUE;}});});
        try{
            for(boolean multi:new boolean[]{false,true})for(int direction:new int[]{1,-1}){
                uiAssert(()->addMarkerPageV32(host[0],red,multi));waitForIdleSync();final Bitmap oldRef=markerReferenceV32(paper,red,multi),newRef=markerReferenceV32(paper,blue,false);
                try{uiAssert(()->{if(!host[0].isAttachedToWindow()||host[0].getWidth()!=600)throw new Exception("Opaque composition host is not really attached/laid-out");transition[0].run(()->addMarkerPageV32(host[0],blue,false),direction);Object scene=ownField(transition[0],"image");if(multi!=(ownField(scene,"bitmap") instanceof Bitmap))throw new Exception("Single root must retain its real View; multiple roots must flatten exactly once");surfaceBoundsV32(host[0],paper);});android.animation.ValueAnimator animator=pauseSettingsV32(transition[0]);for(float p:new float[]{0,.04f,.1f,.16f,.25f,.4f,.5f,.75f,.9f}){final float progress=p;uiAssert(()->assertSheetsV32(host[0],transition[0],animator,oldRef,newRef,direction,progress,name+(multi?"-multi":"-single")));}uiAssert(()->finishSheetsV32(host[0],transition[0],animator));}finally{oldRef.recycle();newRef.recycle();uiAssert(()->transition[0].clear());}
            }
            // Preserve the actual visible two-sheet composition when navigation is retargeted.
            uiAssert(()->addMarkerPageV32(host[0],red,false));waitForIdleSync();uiAssert(()->transition[0].run(()->addMarkerPageV32(host[0],blue,false),1));android.animation.ValueAnimator first=pauseSettingsV32(transition[0]);final Bitmap[] interrupted={null};final Bitmap greenRef=markerReferenceV32(paper,green,false);
            try{uiAssert(()->{first.setCurrentPlayTime(144);interrupted[0]=renderViewV26(host[0]);transition[0].run(()->addMarkerPageV32(host[0],green,false),-1);Object scene=ownField(transition[0],"image");Bitmap snapshot=(Bitmap)ownField(scene,"bitmap");if(snapshot==null)throw new Exception("Rapid route did not retain the current visible presentation");Bitmap start=renderViewV26(host[0]);try{for(int y=15;y<585;y+=19)for(int x=15;x<585;x+=17){nearColorV31(interrupted[0].getPixel(x,y),snapshot.getPixel(x,y),3,"Retarget capture changed the current page");nearColorV31(interrupted[0].getPixel(x,y),start.getPixel(x,y),3,"Retarget first frame jumped");}}finally{start.recycle();}});android.animation.ValueAnimator second=pauseSettingsV32(transition[0]);for(float p:new float[]{0,.1f,.25f,.5f,.75f}){final float progress=p;uiAssert(()->assertSheetsV32(host[0],transition[0],second,interrupted[0],greenRef,-1,progress,name+"-retarget"));}uiAssert(()->finishSheetsV32(host[0],transition[0],second));}finally{if(interrupted[0]!=null)interrupted[0].recycle();greenRef.recycle();uiAssert(()->transition[0].clear());}
            // A direct ScrollView root needs its own scroll offset applied by the
            // interrupted renderer, matching ViewGroup.drawChild's normal path.
            final android.widget.ScrollView[] scroll={null};final android.graphics.drawable.Drawable[] background={null};
            uiAssert(()->{host[0].removeAllViews();scroll[0]=new android.widget.ScrollView(screen);scroll[0].setVerticalScrollBarEnabled(false);scroll[0].setOverScrollMode(View.OVER_SCROLL_NEVER);scroll[0].setBackgroundColor(paper);background[0]=scroll[0].getBackground();android.widget.LinearLayout column=new android.widget.LinearLayout(screen);column.setOrientation(android.widget.LinearLayout.VERTICAL);for(int i=0;i<8;i++){View band=new View(screen);band.setBackgroundColor(i%2==0?red:0xffedce26);column.addView(band,new android.widget.LinearLayout.LayoutParams(-1,160));}scroll[0].addView(column);host[0].addView(scroll[0],new FrameLayout.LayoutParams(-1,-1));});waitForIdleSync();final Bitmap[] scrolledRef={null};final Bitmap blueRef=markerReferenceV32(paper,blue,false);
            try{uiAssert(()->{scroll[0].scrollTo(0,240);if(scroll[0].getScrollY()!=240)throw new Exception("Direct ScrollView fixture failed to scroll");scrolledRef[0]=renderViewV26(host[0]);transition[0].run(()->addMarkerPageV32(host[0],blue,false),1);});android.animation.ValueAnimator animator=pauseSettingsV32(transition[0]);for(float p:new float[]{0,.04f,.1f,.25f,.5f}){final float progress=p;uiAssert(()->assertSheetsV32(host[0],transition[0],animator,scrolledRef[0],blueRef,1,progress,name+"-scrolled-root"));}uiAssert(()->{finishSheetsV32(host[0],transition[0],animator);if(scroll[0].getScrollY()!=240||scroll[0].getBackground()!=background[0]||background[0].getCallback()!=scroll[0])throw new Exception("Scrolled root lost its scroll/background callback on release");});}finally{if(scrolledRef[0]!=null)scrolledRef[0].recycle();blueRef.recycle();uiAssert(()->transition[0].clear());}
            // Clear before first draw must restore the real old View as well.
            uiAssert(()->{View old=host[0].getChildAt(0);float alpha=old.getAlpha(),origin=old.getTranslationX();transition[0].run(()->addMarkerPageV32(host[0],green,false),1);invokeAnyV20(transition[0],"position",new Class[]{float.class},.16f);if(old.getAlpha()!=alpha||Math.abs(old.getTranslationX()-origin)<1)throw new Exception("Cancel fixture did not enter an opaque moving state");transition[0].clear();if(old.getAlpha()!=alpha||old.getTranslationX()!=origin||old.getBackground()!=null||host[0].getChildAt(0).getAlpha()!=1||host[0].getChildAt(0).getTranslationX()!=0||transition[0].active())throw new Exception("Early cancellation retained temporary surface/transform");});
        }finally{uiAssert(()->{transition[0].clear();((ViewGroup)field(screen,"root")).removeView(host[0]);});}
    }
    private void freshCoverTextureV32(MainActivity screen,String date)throws Exception {
        uiAssert(()->{
            Class<?> coverClass=Class.forName("com.eatrecord.app.MainActivity$BookCoverDrawable"),overlayClass=Class.forName("com.eatrecord.app.MainActivity$BookOpenView");java.lang.reflect.Constructor<?> coverCtor=coverClass.getDeclaredConstructor(MainActivity.class,String.class,int[].class,String.class),overlayCtor=overlayClass.getDeclaredConstructor(MainActivity.class,Context.class,String.class);coverCtor.setAccessible(true);overlayCtor.setAccessible(true);
            int[] palette=(int[])invoke(screen,"bookPalette",new Class[]{String.class,int.class},date,((Number)field(screen,"accent")).intValue());
            for(String style:new String[]{"food","checker","nightstar","clear"}){
                View source=new FrameLayout(screen);android.graphics.drawable.Drawable cover=(android.graphics.drawable.Drawable)coverCtor.newInstance(screen,date,palette,style);source.setBackground(cover);source.measure(View.MeasureSpec.makeMeasureSpec(180,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(230,View.MeasureSpec.EXACTLY));source.layout(0,0,180,230);
                // Never draw source. Force the valid pending-first-draw state even
                // on a platform that eagerly initializes background bounds in layout.
                cover.setBounds(0,0,0,0);Rect bounds=cover.copyBounds();Object overlay=overlayCtor.newInstance(screen,screen,date);Bitmap page=Bitmap.createBitmap(360,460,Bitmap.Config.ARGB_8888),texture=Bitmap.createBitmap(360,460,Bitmap.Config.ARGB_8888);
                try{setOwnV30(overlay,"pageImage",page);setOwnV30(overlay,"coverImage",texture);setOwnV30(overlay,"sourceCover",source);invokeAnyV20(overlay,"captureCoverTexture",new Class[]{});if(!cover.getBounds().equals(bounds))throw new Exception("Fresh cover capture failed to restore its original empty background bounds");if(ownField(overlay,"pageImage")!=page||ownField(overlay,"coverImage")!=texture)throw new Exception("Fresh cover capture reallocates its real textures");for(int y:new int[]{5,40,180,420,454})for(int x:new int[]{5,40,180,320,354})if(Color.alpha(texture.getPixel(x,y))!=255)throw new Exception("Fresh not-yet-drawn "+style+" cover has a transparent base at "+x+","+y);cover.setBounds(3,5,60,90);bounds.set(cover.getBounds());setOwnV30(overlay,"sourceCover",source);invokeAnyV20(overlay,"captureCoverTexture",new Class[]{});if(!cover.getBounds().equals(bounds))throw new Exception("Cover capture overwrote nonempty original Drawable bounds");}finally{invokeAnyV20(overlay,"finish",new Class[]{});if(!page.isRecycled())page.recycle();if(!texture.isRecycled())texture.recycle();}
            }
        });
    }
    private void notebookShaderReuseV32(MainActivity screen)throws Exception {
        uiAssert(()->{
            View paper=new View(screen);paper.setBackgroundColor(0xfffaf6e9);paper.measure(View.MeasureSpec.makeMeasureSpec(240,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY));paper.layout(0,0,240,360);NotebookTurnRenderer renderer=new NotebookTurnRenderer(paper,new RectF(20,20,260,380),0xfffaf6e9,0xffeaddc4);Bitmap output=Bitmap.createBitmap(300,420,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(output);Bitmap front=(Bitmap)ownField(renderer,"front");Object[] shaders={ownField(renderer,"paperBack"),ownField(renderer,"sheetShadow"),ownField(renderer,"foldShadow"),ownField(renderer,"sheetShadowMatrix"),ownField(renderer,"foldShadowMatrix")};int generation=front.getGenerationId();
            try{for(boolean forward:new boolean[]{true,false})for(float progress:new float[]{0,.08f,.25f,.5f,.75f,.92f,1}){output.eraseColor(Color.TRANSPARENT);renderer.draw(canvas,progress,forward);if(ownField(renderer,"front")!=front||front.isRecycled()||front.getGenerationId()!=generation)throw new Exception("Turning a page reallocated or repainted its source texture during draw");String[] names={"paperBack","sheetShadow","foldShadow","sheetShadowMatrix","foldShadowMatrix"};for(int i=0;i<names.length;i++)if(ownField(renderer,names[i])!=shaders[i])throw new Exception("Turn renderer replaced a per-turn shader/matrix while drawing: "+names[i]);}paper.setBackgroundColor(0xff6b936b);if(!renderer.refresh(paper)||ownField(renderer,"front")!=front||front.getGenerationId()==generation)throw new Exception("Cached turn renderer no longer refreshes the same texture after media arrives");nearColorV31(0xff6b936b,front.getPixel(120,180),1,"Refreshed turn front is stale");if(Color.alpha(front.getPixel(0,0))!=0)throw new Exception("Refreshed turn texture lost its rounded capture clip");}finally{renderer.release();output.recycle();if(!front.isRecycled())throw new Exception("Turn release retained its source bitmap");}
        });
    }
    private boolean changedPixelV32(int actual,int expected){
        return Math.abs(Color.alpha(actual)-Color.alpha(expected))>2||Math.abs(Color.red(actual)-Color.red(expected))>2||Math.abs(Color.green(actual)-Color.green(expected))>2||Math.abs(Color.blue(actual)-Color.blue(expected))>2;
    }
    private void drawTailFrameV32(NotebookTurnRenderer renderer,Bitmap frame,Canvas canvas,float viewportLeft,float physicalTurn,boolean forward){
        frame.eraseColor(0xff173b59);int saved=canvas.save();try{canvas.clipRect(viewportLeft,0,frame.getWidth(),frame.getHeight());renderer.draw(canvas,forward?physicalTurn:1-physicalTurn,forward);}finally{canvas.restoreToCount(saved);}
    }
    /** Inspect the real folded paper's exit in viewport pixels; do not reimplement crease math. */
    private void notebookTailPixelsV32(MainActivity screen,String name)throws Exception {
        for(int[] geometry:new int[][]{{40,0},{88,0},{40,16},{88,16},{8,16},{0,16}}){
            final int left=geometry[0],viewport=geometry[1];uiAssert(()->{
                final int width=400,height=420,paperWidth=240,paperHeight=360,destination=0xff173b59;View paper=new View(screen);paper.setBackgroundColor(0xffd93655);paper.measure(View.MeasureSpec.makeMeasureSpec(paperWidth,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(paperHeight,View.MeasureSpec.EXACTLY));paper.layout(0,0,paperWidth,paperHeight);RectF bounds=new RectF(left,24,left+paperWidth,24+paperHeight);NotebookTurnRenderer renderer=new NotebookTurnRenderer(paper,bounds,0xfffaf6e9,0xffeaddc4,viewport);Bitmap frame=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(frame);int[] pixels=new int[width*height],end=new int[width*height],forwardPixels=new int[width*height],anchor=new int[width*height];Bitmap source=(Bitmap)ownField(renderer,"front");int generation=source.getGenerationId();
                try{
                    drawTailFrameV32(renderer,frame,canvas,viewport,1,true);frame.getPixels(end,0,width,0,0,width,height);for(int value:end)if(value!=destination)throw new Exception("Completed fold leaves content in the stable clipped viewport");
                    int previousArea=Integer.MAX_VALUE;for(float t:new float[]{.9f,.97f,.99f,.995f,.9999f,1}){
                        drawTailFrameV32(renderer,frame,canvas,viewport,t,true);frame.getPixels(pixels,0,width,0,0,width,height);int area=0,solid=0,changed=0,minX=width,maxX=-1;
                        for(int y=0;y<height;y++)for(int x=0;x<width;x++){int value=pixels[y*width+x];if(x<viewport&&value!=destination)throw new Exception("Fold paints beyond its parent's stable viewport clip");boolean difference=changedPixelV32(value,end[y*width+x]);if(difference){changed++;minX=Math.min(minX,x);maxX=Math.max(maxX,x);}if(x>=viewport&&x<left&&y>=27&&y<24+paperHeight-3&&difference){area++;if(Color.red(value)>=190&&Color.green(value)>=180&&Color.blue(value)>=140)solid++;}}
                        if((long)area>(long)previousArea+8L)throw new Exception("Late fold grows a colored left-gutter block instead of moving it out: left="+left+", viewport="+viewport+", t="+t+", area="+area+", previous="+previousArea);previousArea=area;
                        if(area>paperHeight*2&&solid<area/4)throw new Exception("Late fold hides its still-visible opaque paper with a fade: t="+t+", solid="+solid+", area="+area);
                        // At the last sampled visible time, at most one AA column
                        // may differ from the endpoint. A full gutter strip fails.
                        if(t>=.9999f&&(changed>paperHeight+4||maxX>=minX&&maxX-minX+1>1))throw new Exception("Last fold frame drops a visible strip at completion: left="+left+", viewport="+viewport+", changed="+changed+", width="+(maxX-minX+1));
                        System.arraycopy(pixels,0,forwardPixels,0,pixels.length);drawTailFrameV32(renderer,frame,canvas,viewport,t,false);frame.getPixels(pixels,0,width,0,0,width,height);for(int i=0;i<pixels.length;i++)if(changedPixelV32(pixels[i],forwardPixels[i]))throw new Exception("Reverse fold endpoint geometry differs from forward motion at the same physical position: t="+t);
                        if(ownField(renderer,"front")!=source||source.isRecycled()||source.getGenerationId()!=generation)throw new Exception("Tail rendering mutates/reallocates its captured page texture");if(t==.97f||t==.9999f)saveFrameV24(frame,name+"-fold-tail-left"+left+"-clip"+viewport+"-"+Math.round(t*10000));
                    }
                    // Simulate a cancellation/reversal returning to the same
                    // physical position; cached shader state must not cause a jump.
                    drawTailFrameV32(renderer,frame,canvas,viewport,.37f,true);frame.getPixels(anchor,0,width,0,0,width,height);drawTailFrameV32(renderer,frame,canvas,viewport,.9999f,true);drawTailFrameV32(renderer,frame,canvas,viewport,.12f,false);drawTailFrameV32(renderer,frame,canvas,viewport,.37f,true);frame.getPixels(pixels,0,width,0,0,width,height);for(int i=0;i<pixels.length;i++)if(changedPixelV32(pixels[i],anchor[i]))throw new Exception("Cancel/reverse leaves a jump at an identical fold position");
                    if(viewport==0){NotebookTurnRenderer defaultViewport=new NotebookTurnRenderer(paper,bounds,0xfffaf6e9,0xffeaddc4);try{drawTailFrameV32(defaultViewport,frame,canvas,0,.37f,true);frame.getPixels(pixels,0,width,0,0,width,height);for(int i=0;i<pixels.length;i++)if(changedPixelV32(pixels[i],anchor[i]))throw new Exception("Four-argument renderer no longer means viewportLeft=0");}finally{defaultViewport.release();}}
                }finally{renderer.release();frame.recycle();if(!source.isRecycled())throw new Exception("Tail renderer release retained the captured page");}
            });
        }
    }
    private void settingsTransitionsV32(MainActivity screen,android.content.SharedPreferences prefs,String before,String name)throws Exception {
        settingsCompositionV32(screen,name);
        for(String[] route:new String[][]{{"openThemeSettingsPage","closeThemeSettingsPage"},{"openProfileSettingsPage","closeProfileSettingsPage"},{"openAboutSoftwarePage","closeAboutSoftwarePage"},{"openFontSourcesPage","closeFontSourcesPage"},{"openHintManagerPage","closeHintManagerPage"},{"openVibrationPage","closeVibrationPage"},{"openStickerSettingsPage","closeStickerSettingsPage"},{"openFootprintSettingsPage","closeFootprintSettingsPage"},{"openNotebookSettingsPage","closeNotebookSettingsPage"},{"openInterfaceLayoutPage","closeInterfaceLayoutPage"},{"openMealReminderSettingsPage","closeMoreSettingsPage"},{"openDataSecurityPage","closeDataSecurityPage"},{"openCloudSyncPage","closeCloudSyncPage"},{"openRecycleBinPage","closeRecycleBinPage"},{"openFavoriteRecordsPage","closeFavoriteRecordsPage"}}){
            uiAssert(()->invoke(screen,"showTab",new Class[]{int.class,boolean.class},3,false));settleV15();settingsTimelineV32(screen,"openPreferencesPage",name);settingsTimelineV32(screen,route[0],name);settingsTimelineV32(screen,route[1],name);uiAssert(()->assertRenderPure(screen,prefs,before));
        }
        // Interrupt before layout and during motion, then refresh in place while
        // preserving scroll. Neither removed children nor their callbacks may survive.
        uiAssert(()->{invoke(screen,"openPreferencesPage",new Class[]{});invoke(screen,"openNotebookSettingsPage",new Class[]{});invoke(screen,"closeNotebookSettingsPage",new Class[]{});invoke(screen,"openInterfaceLayoutPage",new Class[]{});invoke(screen,"closeInterfaceLayoutPage",new Class[]{});});settleV15();settingsRestV31(screen);
        final int[] y={0};uiAssert(()->{android.widget.ScrollView scroll=(android.widget.ScrollView)invoke(screen,"currentPageScroll",new Class[]{});if(scroll==null)throw new Exception("Preferences lost its real scroll root");scroll.scrollTo(0,Math.min(180,Math.max(0,scroll.getChildAt(0).getHeight()-scroll.getHeight())));y[0]=scroll.getScrollY();Runnable refresh=()->{try{invoke(screen,"renderPreferencesPage",new Class[]{});}catch(Exception failure){throw new RuntimeException(failure);}};invoke(screen,"rerenderPreservingScroll",new Class[]{Runnable.class},refresh);});settleV15();settingsRestV31(screen);
        uiAssert(()->{android.widget.ScrollView scroll=(android.widget.ScrollView)invoke(screen,"currentPageScroll",new Class[]{});if(scroll==null||Math.abs(scroll.getScrollY()-y[0])>1)throw new Exception("In-place preferences refresh reset its real scroll position");invoke(screen,"openNotebookSettingsPage",new Class[]{});Runnable refresh=()->{try{invoke(screen,"renderNotebookSettingsPage",new Class[]{});}catch(Exception failure){throw new RuntimeException(failure);}};invoke(screen,"rerenderPreservingScroll",new Class[]{Runnable.class},refresh);});settleV15();settingsRestV31(screen);
        // Theme captures are separate overlays; any subsequent navigation owns the
        // window and must remove the obsolete snapshot immediately.
        uiAssert(()->{invoke(screen,"beginThemeModeTransition",new Class[]{});View old=(View)field(screen,"themeModeTransitionOverlay");if(android.animation.ValueAnimator.areAnimatorsEnabled()&&old==null)throw new Exception("Theme interrupt setup did not create its real snapshot");invoke(screen,"openNotebookSettingsPage",new Class[]{});if(field(screen,"themeModeTransitionOverlay")!=null||old!=null&&old.getParent()!=null)throw new Exception("New settings navigation retains a stale theme snapshot");});settleV15();settingsRestV31(screen);
        uiAssert(()->{invoke(screen,"showTab",new Class[]{int.class,boolean.class},3,true);invoke(screen,"showTab",new Class[]{int.class,boolean.class},0,true);});settleV15();settingsRestV31(screen);uiAssert(()->assertRenderPure(screen,prefs,before));
    }
    /** beta.33 uses current root ownership; historical peer scenes are explicitly retired. */
    private RootPagePager currentRootV33(MainActivity screen)throws Exception {
        RootPagePager pager=(RootPagePager)field(screen,"rootPager");
        if(pager==null||pager.getParent()!=field(screen,"content"))throw new Exception("No live root pager");
        return pager;
    }
    private void rootTabV33(MainActivity screen,int tab)throws Exception {
        uiAssert(()->invoke(screen,"showTab",new Class[]{int.class,boolean.class},tab,false));settleV15();
        uiAssert(()->rootAtRestV26(screen,tab));
    }
    private void waitReturnActionV33(int[] count,int expected)throws Exception {
        long deadline=android.os.SystemClock.elapsedRealtime()+3000;
        do{waitForIdleSync();if(count[0]==expected)break;Thread.sleep(20);}while(android.os.SystemClock.elapsedRealtime()<deadline);
        if(count[0]!=expected)throw new Exception("Draw-owned return action count: "+count[0]+", expected="+expected);
    }
    private void returnFeedbackV33(MainActivity screen)throws Exception {
        rootTabV33(screen,3);final int[] actions={0};boolean animated=android.animation.ValueAnimator.areAnimatorsEnabled();
        uiAssert(()->{boolean queued=(Boolean)invoke(screen,"afterReturnFeedback",new Class[]{Runnable.class},(Runnable)()->actions[0]++);
            if(animated){if(!queued||actions[0]!=0||field(screen,"returnAfterDraw")==null)throw new Exception("Return work ran in the click turn before any real draw");}
            else{if(queued)throw new Exception("Disabled animations unnecessarily queued return work");actions[0]++;}});
        waitReturnActionV33(actions,1);uiAssert(()->{if(field(screen,"returnAfterDraw")!=null||field(screen,"returnDrawListener")!=null||Boolean.TRUE.equals(field(screen,"returnWorkExecuting")))throw new Exception("Finished draw-owned return retained listener/work state");});
        if(!animated)return;
        for(String cancel:new String[]{"tab","pause","configuration"}){
            final int[] obsolete={0};uiAssert(()->{
                if(!(Boolean)invoke(screen,"afterReturnFeedback",new Class[]{Runnable.class},(Runnable)()->obsolete[0]++))throw new Exception("Return cancellation setup was not queued");
                if("tab".equals(cancel))invoke(screen,"showTab",new Class[]{int.class,boolean.class},1,false);
                else if("pause".equals(cancel)){invoke(screen,"onPause",new Class[]{});invoke(screen,"onResume",new Class[]{});}
                else invoke(screen,"onConfigurationChanged",new Class[]{android.content.res.Configuration.class},new android.content.res.Configuration(screen.getResources().getConfiguration()));
                if(field(screen,"returnAfterDraw")!=null||field(screen,"returnDrawListener")!=null)throw new Exception("Cancellation retained the return draw listener: "+cancel);
            });settleV15();uiAssert(()->{if(obsolete[0]!=0)throw new Exception("Canceled return later executed after "+cancel);});
        }
        // Configuration above invokes the production callback; it is not a physical rotation.
    }
    private void cachedReturnsV33(MainActivity screen,String today)throws Exception {
        rootTabV33(screen,3);final RootPagePager[] original={null};final View[] setting={null};
        uiAssert(()->{original[0]=currentRootV33(screen);if(Boolean.TRUE.equals(field(screen,"rootPagesDirty")))throw new Exception("Clean return fixture is already dirty");invoke(screen,"openPreferencesPage",new Class[]{});});settleV15();
        uiAssert(()->{setting[0]=((ViewGroup)field(screen,"content")).getChildAt(0);invoke(screen,"closePreferencesPage",new Class[]{});
            if(android.animation.ValueAnimator.areAnimatorsEnabled()&&(((ViewGroup)field(screen,"content")).getChildAt(0)!=setting[0]||field(screen,"returnAfterDraw")==null))throw new Exception("Back rebuilt settings before its feedback frame");});settleV15();
        uiAssert(()->{if(currentRootV33(screen)!=original[0])throw new Exception("Clean Me Back rebuilt all three root pages");});
        uiAssert(()->invoke(screen,"openPreferencesPage",new Class[]{}));settleV15();
        uiAssert(()->{setMainField(screen,"rootPagesDirty",true);invoke(screen,"closePreferencesPage",new Class[]{});});settleV15();
        uiAssert(()->{if(currentRootV33(screen)==original[0]||Boolean.TRUE.equals(field(screen,"rootPagesDirty")))throw new Exception("Dirty Me Back reused an obsolete root");});
        rootTabV33(screen,1);uiAssert(()->{original[0]=currentRootV33(screen);invoke(screen,"openBookDetail",new Class[]{String.class,View.class},today,null);});waitBookRestV31(screen,true);
        uiAssert(()->invoke(screen,"closeBookDetail",new Class[]{}));waitBookRestV31(screen,false);settleV15();
        uiAssert(()->{if(currentRootV33(screen)!=original[0])throw new Exception("Clean book return rebuilt its prepared shelf roots");});
        pendingHomeReturnV33(screen);
    }
    private void pendingHomeReturnV33(MainActivity screen)throws Exception {
        if(!android.animation.ValueAnimator.areAnimatorsEnabled())return;
        rootTabV33(screen,0);
        // Establish a settled Today before the single-turn interruption below.
        uiAssert(()->{TodayDayPager day=(TodayDayPager)currentRootV33(screen).page(0).findViewWithTag("today_day_pager");if(day==null||!day.animateToDate(Calendar.getInstance()))throw new Exception("Cannot prepare the active Today date");});settleV15();
        final RootPagePager[] previous={null};final String[] explicit={null};
        uiAssert(()->{
            previous[0]=currentRootV33(screen);TodayDayPager day=(TodayDayPager)previous[0].page(0).findViewWithTag("today_day_pager");
            Calendar yesterday=HomeCalendarDates.day(Calendar.getInstance());yesterday.add(Calendar.DATE,-1);explicit[0]=HomeCalendarDates.key(yesterday);
            if(!day.animateToDate(yesterday)||!day.hasPendingRootMotion())throw new Exception("No explicit in-flight date target to preserve");
            // Do not call showTab here: it would rebuild away the original day state.
            previous[0].select(2,false);
            if(!explicit[0].equals(HomeCalendarDates.key(day.rootDestination()))||!day.hasPendingRootMotion())throw new Exception("Pausing Today already lost the explicit calendar target");
            invoke(screen,"openPreferencesPage",new Class[]{});
            if(!explicit[0].equals(field(screen,"rootHomeRestoreDate")))throw new Exception("Leaving the visible root did not save its pending date target");
        });settleV15();
        uiAssert(()->invoke(screen,"closePreferencesPage",new Class[]{}));settleV15();
        uiAssert(()->{
            RootPagePager fresh=currentRootV33(screen);TodayDayPager day=(TodayDayPager)fresh.page(0).findViewWithTag("today_day_pager");
            if(fresh==previous[0]||day==null||!explicit[0].equals(HomeCalendarDates.key(day.rootDestination())))throw new Exception("Back reused/settled a hidden day to its nearest date instead of the explicit pending target");
            if(field(screen,"rootHomeRestoreDate")!=null)throw new Exception("Rebuilt root did not consume its explicit date restore marker");
        });
    }
    private void dialogWindowV33(MainActivity screen,AlertDialog dialog)throws Exception {
        android.view.Window window=dialog.getWindow();if(window==null)throw new Exception("Custom dialog has no window");
        android.view.WindowManager.LayoutParams attrs=window.getAttributes();
        if((attrs.flags&android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)!=0)throw new Exception("Custom dialog stacks a second system blur behind root blur");
        float dim=Boolean.TRUE.equals(invoke(screen,"nightDecoration",new Class[]{}))?.10f:.07f;
        if(Math.abs(attrs.dimAmount-dim)>.001f)throw new Exception("Custom dialog uses a later/default dim amount");
        if(android.os.Build.VERSION.SDK_INT>=31&&Boolean.TRUE.equals(invoke(screen,"platformBlurSupported",new Class[]{}))&&attrs.getBlurBehindRadius()!=0)throw new Exception("Custom dialog retains a second blur radius");
    }
    private void dialogsV33(MainActivity screen)throws Exception {
        rootTabV33(screen,0);final AlertDialog[] first={null},second={null};final View[] firstContent={null};
        try{
            uiAssert(()->{first[0]=new AlertDialog.Builder(screen).create();firstContent[0]=PaperComponents.text(screen,"QA dialog one",16,UiTokens.ink(false),false);invoke(screen,"showRoundedDialog",new Class[]{AlertDialog.class,View.class},first[0],firstContent[0]);dialogWindowV33(screen,first[0]);
                invoke(screen,"showRoundedDialog",new Class[]{AlertDialog.class,View.class},first[0],firstContent[0]);if(((Number)field(screen,"activeGlassDialogCount")).intValue()!=1)throw new Exception("Restyling one dialog acquired a duplicate blur owner");
                second[0]=new AlertDialog.Builder(screen).create();View box=PaperComponents.text(screen,"QA dialog two",16,UiTokens.ink(false),false);invoke(screen,"showRoundedDialog",new Class[]{AlertDialog.class,View.class},second[0],box);dialogWindowV33(screen,second[0]);
                if(((java.util.Map<?,?>)field(screen,"glassDialogOwners")).size()!=2)throw new Exception("Nested dialog did not keep independent owners");first[0].dismiss();});settleV15();
            uiAssert(()->{if(!second[0].isShowing()||((Number)field(screen,"activeGlassDialogCount")).intValue()!=1||!((java.util.Map<?,?>)field(screen,"glassDialogOwners")).containsKey(second[0]))throw new Exception("Old dismiss removed the replacement dialog blur owner");dialogWindowV33(screen,second[0]);second[0].dismiss();});settleV15();
            uiAssert(()->{if(((Number)field(screen,"activeGlassDialogCount")).intValue()!=0||!((java.util.Map<?,?>)field(screen,"glassDialogOwners")).isEmpty())throw new Exception("Final dialog leaked its root blur owner");});
            for(String entry:new String[]{"chooseMealForPhoto","chooseMealForText"}){
                uiAssert(()->invoke(screen,entry,new Class[]{}));settleV15();
                uiAssert(()->{java.util.Map<?,?> owners=(java.util.Map<?,?>)field(screen,"glassDialogOwners");if(owners.size()!=1)throw new Exception("Meal chooser is not a single blur owner: "+entry);AlertDialog chooser=(AlertDialog)owners.keySet().iterator().next();dialogWindowV33(screen,chooser);chooser.dismiss();});settleV15();
            }
        }finally{uiAssert(()->{if(first[0]!=null)first[0].dismiss();if(second[0]!=null)second[0].dismiss();});}
    }
    private void detailsRefreshV33(MainActivity screen,JSONObject fixture,String today)throws Exception {
        rootTabV33(screen,0);settleV15();final RootPagePager[] original={null};final int[] cacheSize={0};final AlertDialog[] details={null};
        JSONObject item=fixture.getJSONObject(today).getJSONObject("breakfast").getJSONArray("foodItems").getJSONObject(0);
        uiAssert(()->{original[0]=currentRootV33(screen);cacheSize[0]=((android.util.LruCache<?,?>)field(screen,"mediaThumbnailCache")).size();details[0]=((FoodUi)invoke(screen,"foodUi",new Class[]{})).details(today,"breakfast",item);});
        Thread.sleep(650);waitForIdleSync();
        uiAssert(()->{if(currentRootV33(screen)!=original[0]||Boolean.TRUE.equals(field(screen,"rootPagesDirty"))||((android.util.LruCache<?,?>)field(screen,"mediaThumbnailCache")).size()<cacheSize[0])throw new Exception("Opening read-only details invalidated root or evicted its media cache");details[0].dismiss();});Thread.sleep(650);waitForIdleSync();
        uiAssert(()->{if(currentRootV33(screen)!=original[0]||Boolean.TRUE.equals(field(screen,"rootPagesDirty")))throw new Exception("Details dismissal rebuilt the unchanged Today sheet after the delayed refresh window");details[0]=((FoodUi)invoke(screen,"foodUi",new Class[]{})).details(today,"breakfast",item);});settleV15();
        final View[] viewer={null};uiAssert(()->{View image=details[0].getWindow().getDecorView().findViewWithTag("food_detail_image");if(image==null||!image.performClick())throw new Exception("Food image did not request its actual original viewer");});viewer[0]=waitViewerV34(screen);Thread.sleep(650);waitForIdleSync();
        uiAssert(()->{if(field(screen,"photoOverlay")!=viewer[0]||!Boolean.TRUE.equals(field(screen,"inPhotoViewer"))||viewer[0].getParent()!=field(screen,"content"))throw new Exception("Delayed root update swallowed the original viewer");setMainField(screen,"rootPagesDirty",true);setMainField(screen,"homeFoodRefreshPending",true);((Runnable)field(screen,"refreshRootPages")).run();((Runnable)field(screen,"homeFoodRefresh")).run();if(field(screen,"photoOverlay")!=viewer[0]||field(screen,"rootPager")!=original[0]||!Boolean.TRUE.equals(field(screen,"rootPagesDirty"))||!Boolean.TRUE.equals(field(screen,"homeFoodRefreshPending")))throw new Exception("Covered refresh did not defer while preserving pending work");invoke(screen,"closePhotoViewer",new Class[]{});});settleV15();
        uiAssert(()->{if(Boolean.TRUE.equals(field(screen,"inPhotoViewer"))||field(screen,"photoOverlay")!=null||Boolean.TRUE.equals(field(screen,"rootPagesDirty"))||Boolean.TRUE.equals(field(screen,"homeFoodRefreshPending")))throw new Exception("Closing current viewer failed to resume deferred refresh");});
        final String source=item.getString("sourcePhoto");final View[] replacement={null};
        uiAssert(()->invoke(screen,"showPhotoViewer",new Class[]{String.class,String.class,String.class},today,"breakfast",source));waitViewerV34(screen);
        uiAssert(()->{invoke(screen,"closePhotoViewer",new Class[]{});invoke(screen,"showPhotoViewer",new Class[]{String.class,String.class,String.class},today,"breakfast",source);});replacement[0]=waitViewerV34(screen);Thread.sleep(350);waitForIdleSync();
        uiAssert(()->{if(field(screen,"photoOverlay")!=replacement[0]||!Boolean.TRUE.equals(field(screen,"inPhotoViewer")))throw new Exception("Old viewer close callback cleared a new viewer");invoke(screen,"closePhotoViewer",new Class[]{});});settleV15();
    }
    private void clickContentDescriptionV33(MainActivity screen,String description)throws Exception {
        List<View> views=new ArrayList<>();collectViews((View)field(screen,"content"),views);
        for(View view:views)if(view.getContentDescription()!=null&&description.contentEquals(view.getContentDescription())&&view.isShown()&&view.isClickable()&&view.performClick())return;
        throw new Exception("Missing clickable current-page control: "+description);
    }
    private void assertMeAssistantOrderV33(MainActivity screen)throws Exception {
        View content=currentRootV33(screen).page(2);String text=visibleText(content);
        if(content.findViewWithTag("me_nutritionist")!=null||text.contains("饮食助手")||setting(content,"营养师")!=null||text.contains("界面切换"))throw new Exception("Me retains the duplicate nutritionist entry or retired switching text");
        View personal=setting(content,"个性化"),assistant=content.findViewWithTag("me_smart_assistant"),other=setting(content,"其他设置"),header=content.findViewWithTag("settings_section_偏好设置");
        if(personal==null||assistant==null||other==null||header==null||personal.getParent()!=assistant.getParent()||assistant.getParent()!=other.getParent()||header.getParent()!=assistant.getParent())throw new Exception("Me preferences do not own Personalization, Smart Assistant and Other Settings together");
        ViewGroup page=(ViewGroup)assistant.getParent();int p=page.indexOfChild(personal),a=page.indexOfChild(assistant),o=page.indexOfChild(other);
        if(page.indexOfChild(header)>=p||a!=p+1||o!=a+1)throw new Exception("Me preference order must be Personalization, Smart Assistant, Other Settings");
    }
    private void assertSmartAssistantV33(MainActivity screen)throws Exception {
        View content=(View)field(screen,"content");if(content.findViewWithTag("smart_assistant_page")==null||!Boolean.TRUE.equals(field(screen,"inSmartAssistantPage")))throw new Exception("Me Smart Assistant did not open its hub");
        for(String label:new String[]{"调用 API","餐图识别","营养师"})assertSettingPresence(content,label,true);
    }
    private void settingsRoutesV33(MainActivity screen,android.content.SharedPreferences prefs)throws Exception {
        rootTabV33(screen,3);uiAssert(()->{assertMeAssistantOrderV33(screen);if(!((View)field(screen,"content")).findViewWithTag("me_smart_assistant").performClick())throw new Exception("Me Smart Assistant row is not clickable");});settleV15();
        uiAssert(()->{assertSmartAssistantV33(screen);setting((View)field(screen,"content"),"营养师").performClick();});settleV15();
        uiAssert(()->{View content=(View)field(screen,"content");if(content.findViewWithTag("nutritionist_settings_page")==null||visibleText(content).contains("“我”页的营养师入口"))throw new Exception("Nutritionist configuration was lost or still recommends its deleted Me entry");clickContentDescriptionV33(screen,"返回智能助手");});settleV15();
        uiAssert(()->{assertSmartAssistantV33(screen);setting((View)field(screen,"content"),"餐图识别").performClick();});settleV15();
        uiAssert(()->{View content=(View)field(screen,"content"),toggle=content.findViewWithTag("food_recognition_advanced_toggle"),body=content.findViewWithTag("food_recognition_advanced");if(toggle==null||body==null||body.getVisibility()!=View.GONE)throw new Exception("API compatibility options are not collapsed by default");toggle.performClick();if(body.getVisibility()!=View.VISIBLE)throw new Exception("Advanced API options cannot expand");toggle.performClick();if(body.getVisibility()!=View.GONE)throw new Exception("Advanced API options cannot collapse");clickContentDescriptionV33(screen,"返回智能助手");});settleV15();
        uiAssert(()->{assertSmartAssistantV33(screen);clickContentDescriptionV33(screen,"返回我");});settleV15();uiAssert(()->{rootAtRestV26(screen,3);assertMeAssistantOrderV33(screen);((View)field(screen,"content")).findViewWithTag("me_smart_assistant").performClick();});settleV15();
        uiAssert(()->{assertSmartAssistantV33(screen);if(!Boolean.TRUE.equals(invoke(screen,"handleBackNavigation",new Class[]{})))throw new Exception("Smart Assistant system Back was not handled");});settleV15();uiAssert(()->{rootAtRestV26(screen,3);assertMeAssistantOrderV33(screen);invoke(screen,"openPreferencesPage",new Class[]{});});settleV15();
        final String[] profileBefore={null};
        uiAssert(()->{
            View content=(View)field(screen,"content"),header=content.findViewWithTag("settings_section_恢复出厂"),reset=setting(content,"恢复出厂设置");
            if(header==null||reset==null||header.getParent()!=reset.getParent()||setting(content,"智能助手")!=null||content.findViewWithTag("me_smart_assistant")!=null||content.findViewWithTag("settings_section_智能与管理")!=null)throw new Exception("Other Settings retains Smart Assistant or lacks the renamed factory-default section");
            ViewGroup page=(ViewGroup)header.getParent();int start=page.indexOfChild(header),rows=0;for(int i=start+1;i<page.getChildCount();i++){Object tag=page.getChildAt(i).getTag();if(tag instanceof String&&((String)tag).startsWith("settings_section_"))break;if(tag instanceof String&&(((String)tag).startsWith("setting_row_")||((String)tag).startsWith("setting_toggle_"))){rows++;if(page.getChildAt(i)!=reset)throw new Exception("Factory-default section contains an unrelated preference");}}
            if(rows!=1)throw new Exception("Factory-default section must contain exactly its one reset row");profileBefore[0]=prefs.getString(NutritionStore.PROFILE,"{}");if(!reset.performClick())throw new Exception("Factory-default entry cannot show its confirmation");
        });settleV15();
        String resetText=dialogTextsV20(screen);if(!resetText.contains("不会删除吃饭记录")||!resetText.contains("取消"))throw new Exception("Factory-default confirmation lost its data-preservation explanation or cancel action");tapDialogV20(screen,"取消");settleV15();
        uiAssert(()->{if(!profileBefore[0].equals(prefs.getString(NutritionStore.PROFILE,"{}"))||((Number)field(screen,"activeGlassDialogCount")).intValue()!=0)throw new Exception("Canceling factory defaults changed saved settings or retained its dialog owner");});
        uiAssert(()->invoke(screen,"openNotebookSettingsPage",new Class[]{}));settleV15();
        uiAssert(()->{ViewGroup content=(ViewGroup)field(screen,"content");if(content.findViewWithTag("notebook_settings_page")==null||!Boolean.TRUE.equals(field(screen,"inNotebookSettingsPage")))throw new Exception("Unified notebook hub is missing");String text=visibleText(content);for(String label:new String[]{"书架布局","封面样式","纸张样式","记录本图片边框","翻页","记录本图片摆放","分享界面选项"})if(!text.contains(label))throw new Exception("Notebook hub lost an existing option: "+label);invoke(screen,"openInterfaceLayoutPage",new Class[]{});});settleV15();
        uiAssert(()->{if(!Boolean.TRUE.equals(field(screen,"inInterfaceLayoutPage"))||!visibleText((View)field(screen,"content")).contains("书架布局"))throw new Exception("Shelf layout route did not open");invoke(screen,"closeInterfaceLayoutPage",new Class[]{});});settleV15();
        uiAssert(()->{if(((View)field(screen,"content")).findViewWithTag("notebook_settings_page")==null)throw new Exception("Shelf Back escaped the notebook hub");invoke(screen,"closeNotebookSettingsPage",new Class[]{});});settleV15();
        rootTabV33(screen,1);uiAssert(()->{View shortcut=((View)field(screen,"content")).findViewWithTag("footprints_favorites");if(shortcut==null||!shortcut.performClick())throw new Exception("Books has no visible favorites entry");});settleV15();
        uiAssert(()->{if(!Boolean.TRUE.equals(field(screen,"inFavoriteRecordsPage"))||!Boolean.TRUE.equals(field(screen,"favoriteRecordsFromBooks")))throw new Exception("Favorites lost its Books return owner");invoke(screen,"closeFavoriteRecordsPage",new Class[]{});});settleV15();uiAssert(()->rootAtRestV26(screen,1));
        rootTabV33(screen,3);
    }
    private void glassCaptureV33(MainActivity screen)throws Exception {
        rootTabV33(screen,0);final FrameLayout[] source={null};final View[] bar={null};final int[] before={0};
        try{
            uiAssert(()->{ViewGroup root=(ViewGroup)field(screen,"root");source[0]=new FrameLayout(screen);source[0].setBackgroundColor(0xff284765);root.addView(source[0],new FrameLayout.LayoutParams(600,600));Class<?> type=Class.forName("com.eatrecord.app.MainActivity$LiquidGlassBarLayout");java.lang.reflect.Constructor<?> constructor=type.getDeclaredConstructor(MainActivity.class,android.content.Context.class);constructor.setAccessible(true);bar[0]=(View)constructor.newInstance(screen,screen);invokeAnyV20(bar[0],"setGlass",new Class[]{View.class,int.class,int.class,float.class},source[0],0x80f8f8f4,0xaaffffff,30f);FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(500,120);lp.leftMargin=20;lp.topMargin=300;source[0].addView(bar[0],lp);});settleV15();
            // Warm both alternating capture buffers before requiring stable ownership.
            for(int i=0;i<2;i++){Thread.sleep(70);uiAssert(()->{invokeAnyV20(bar[0],"markBackdropDirty",new Class[]{});Bitmap frame=renderViewV26(bar[0]);frame.recycle();});}settleV15();
            final Object[] buffers=new Object[2],arrays=new Object[3],shaders=new Object[2];final int[] oldPixel={0};
            uiAssert(()->{before[0]=((Number)ownField(bar[0],"captureCount")).intValue();buffers[0]=ownField(bar[0],"blurBitmap");buffers[1]=ownField(bar[0],"captureBitmap");arrays[0]=ownField(bar[0],"blurInput");arrays[1]=ownField(bar[0],"blurMid");arrays[2]=ownField(bar[0],"blurOutput");shaders[0]=ownField(bar[0],"glassGradient");shaders[1]=ownField(bar[0],"highlightGradient");if(buffers[0]==null||buffers[1]==null||arrays[0]==null)throw new Exception("Glass cache did not warm both reusable buffers");Bitmap bitmap=(Bitmap)buffers[0];oldPixel[0]=bitmap.getPixel(bitmap.getWidth()/2,bitmap.getHeight()/2);});Thread.sleep(260);waitForIdleSync();
            uiAssert(()->{if(((Number)ownField(bar[0],"captureCount")).intValue()!=before[0])throw new Exception("Static glass continues capturing without content/layout/scroll changes");source[0].setBackgroundColor(0xff694179);invokeAnyV20(bar[0],"markBackdropDirty",new Class[]{});});Thread.sleep(90);waitForIdleSync();
            uiAssert(()->{Bitmap frame=renderViewV26(bar[0]);frame.recycle();if(((Number)ownField(bar[0],"captureCount")).intValue()<=before[0])throw new Exception("Dirty glass never captured changed background");Object front=ownField(bar[0],"blurBitmap"),back=ownField(bar[0],"captureBitmap");if(!(front==buffers[0]||front==buffers[1])||!(back==buffers[0]||back==buffers[1])||front==back)throw new Exception("Warm glass reallocates its two buffers");String[] fields={"blurInput","blurMid","blurOutput"};for(int i=0;i<fields.length;i++)if(ownField(bar[0],fields[i])!=arrays[i])throw new Exception("Warm glass reallocates pixel array "+fields[i]);if(ownField(bar[0],"glassGradient")!=shaders[0]||ownField(bar[0],"highlightGradient")!=shaders[1])throw new Exception("Glass rebuilds unchanged-size/theme shaders");Bitmap bitmap=(Bitmap)front;if(bitmap.getPixel(bitmap.getWidth()/2,bitmap.getHeight()/2)==oldPixel[0])throw new Exception("Dirty capture changed count but retained stale pixels");});
            uiAssert(()->((ViewGroup)field(screen,"root")).removeView(source[0]));settleV15();
            uiAssert(()->{if(ownField(bar[0],"backdropObserver")!=null||Boolean.TRUE.equals(ownField(bar[0],"refreshPosted"))||ownField(bar[0],"blurBitmap")!=null||ownField(bar[0],"captureBitmap")!=null||ownField(bar[0],"blurInput")!=null)throw new Exception("Detached glass retained observers/tasks/large buffers");for(Object value:buffers)if(!((Bitmap)value).isRecycled())throw new Exception("Detached glass retained a bitmap");});
        }finally{uiAssert(()->{if(source[0]!=null&&source[0].getParent() instanceof ViewGroup)((ViewGroup)source[0].getParent()).removeView(source[0]);});}
    }
    private AlertDialog onlyCustomDialogV34(MainActivity screen)throws Exception {
        java.util.Map<?,?> owners=(java.util.Map<?,?>)field(screen,"glassDialogOwners");
        if(owners.size()!=1)throw new Exception("Expected one current custom dialog, got "+owners.size());
        AlertDialog dialog=(AlertDialog)owners.keySet().iterator().next();
        if(!dialog.isShowing())throw new Exception("Tracked custom dialog is not showing");
        return dialog;
    }
    private void dismissCustomDialogsV34(MainActivity screen)throws Exception {
        java.util.Map<?,?> owners=(java.util.Map<?,?>)field(screen,"glassDialogOwners");
        for(Object dialog:new ArrayList<Object>(owners.keySet()))((AlertDialog)dialog).dismiss();
    }
    private void emptyHintV34(MainActivity screen,Context c,android.content.SharedPreferences prefs,String name)throws Exception {
        rootTabV33(screen,0);
        uiAssert(()->{setMainField(screen,"days",new JSONObject());prefs.edit().putString(NutritionStore.DAYS,"{}").commit();invoke(screen,"renderCurrent",new Class[]{});});settleV15();
        final Rect[] target={null};
        uiAssert(()->{
            View root=currentRootV33(screen).page(0),instruction=root.findViewWithTag("day_empty_instruction");
            ViewGroup paper=(ViewGroup)root.findViewWithTag("today_paper"),guide=(ViewGroup)root.findViewWithTag("day_empty_guide");
            if(guide==null||paper==null||instruction==null||guide.isClickable()||guide.isLongClickable()||guide.isFocusable()||guide.performClick())throw new Exception("Empty hint still accepts the accidental add click");
            String text=visibleText(guide);if(!text.trim().equals("点击下方星标可以添加美食喔")||guide.getChildCount()!=3)throw new Exception("Empty hint has duplicate/old explanatory text");
            if(guide.getParent()!=paper||paper.indexOfChild(guide)!=1||guide.getHeight()<=0)throw new Exception("Empty guide is not the body below the actual summary");
            float center=(guide.getChildAt(0).getTop()+guide.getChildAt(guide.getChildCount()-1).getBottom())*.5f;
            if(Math.abs(center-guide.getHeight()*.5f)>PaperComponents.dp(c,3))throw new Exception("Visible empty hint group is not vertically centered inside its real remaining body");
            if(Math.abs(guide.getBottom()-(paper.getHeight()-paper.getPaddingBottom()))>PaperComponents.dp(c,2))throw new Exception("Empty guide leaves an unused strip below instead of using the viewport");
            target[0]=screenRect(guide.getChildAt(0));assertRenderPure(screen,prefs,"{}");
        });
        tapPointV21(target[0].exactCenterX(),target[0].exactCenterY());
        uiAssert(()->{if(((Number)field(screen,"activeGlassDialogCount")).intValue()!=0||field(screen,"photoOverlay")!=null||field(screen,"foodProcessing")!=null)throw new Exception("Tapping the noninteractive bowl opened an add/media window");});
        uiAssert(()->{View star=(View)field(screen,"fab");if(!star.isShown()||!star.isClickable()||!star.performClick())throw new Exception("Empty-state bottom star is no longer available");});settleV15();
        uiAssert(()->{AlertDialog chooser=onlyCustomDialogV34(screen);String labels=visibleText(chooser.getWindow().getDecorView());for(String meal:new String[]{"早餐","午餐","晚餐","夜宵"})if(!labels.contains(meal))throw new Exception("Bottom star lost meal choice "+meal);dialogWindowV33(screen,chooser);chooser.dismiss();});settleV15();
        uiAssert(()->assertRenderPure(screen,prefs,"{}"));screenshotNowV15(name+"-empty-hint");
    }
    private MainActivity recreatedWindowV35(MainActivity screen)throws Exception {
        ActivityMonitor monitor=addMonitor(MainActivity.class.getName(),null,false);
        try{
            uiAssert(()->screen.recreate());Activity next=waitForMonitorWithTimeout(monitor,10000);
            if(!(next instanceof MainActivity)||next==screen)throw new Exception("Appearance window was not replaced");
            MainActivity fresh=(MainActivity)next;gestureScreenV20=fresh;settleV15();return fresh;
        }finally{removeMonitor(monitor);}
    }
    private void prepareAppearanceOriginV35(MainActivity screen)throws Exception {
        rootTabV33(screen,0);
        uiAssert(()->{
            RootPagePager pager=currentRootV33(screen);TodayDayPager day=(TodayDayPager)pager.page(0).findViewWithTag("today_day_pager");
            HomeCalendarView calendar=(HomeCalendarView)pager.page(0).findViewWithTag("home_calendar");
            Calendar chosen=HomeCalendarDates.day(Calendar.getInstance());chosen.add(Calendar.DATE,-1);
            if(day==null||calendar==null||!day.animateToDate(chosen))throw new Exception("Cannot establish the nondefault appearance-return date");
            calendar.setExpanded(true,false);
        });settleV15();rootTabV33(screen,3);
    }
    private MainActivity toggleNightWindowV35(MainActivity screen,Context c,boolean night)throws Exception {
        final int[] scroll={0},tab={0};final String[] date={null};final boolean[] expanded={false};final Bitmap[] snapshot={null};
        uiAssert(()->{
            if(!Boolean.TRUE.equals(field(screen,"inThemeSettingsPage")))throw new Exception("Night toggle is not on personalization");
            android.widget.ScrollView page=(android.widget.ScrollView)invoke(screen,"currentPageScroll",new Class[]{});
            int range=Math.max(0,page.getChildAt(0).getHeight()-page.getHeight());page.scrollTo(0,range);scroll[0]=page.getScrollY();
            if(scroll[0]<=0)throw new Exception("Night-mode scroll fixture is empty");
            tab[0]=((Number)field(screen,"currentTab")).intValue();expanded[0]=Boolean.TRUE.equals(field(screen,"homeCalendarExpanded"));
            RootPagePager pager=(RootPagePager)field(screen,"rootPager");date[0]=HomeCalendarDates.key(((TodayDayPager)pager.page(0).findViewWithTag("today_day_pager")).rootDestination());
            View toggle=setting((View)field(screen,"content"),"夜间模式");if(toggle==null||!toggle.performClick())throw new Exception("Missing live night-mode toggle");
            if(Boolean.TRUE.equals(invoke(screen,"nightDecoration",new Class[]{}))!=night)throw new Exception("Night choice did not persist");
            View overlay=(View)field(screen,"themeModeTransitionOverlay");if(overlay instanceof android.widget.ImageView&&((android.widget.ImageView)overlay).getDrawable() instanceof android.graphics.drawable.BitmapDrawable)snapshot[0]=((android.graphics.drawable.BitmapDrawable)((android.widget.ImageView)overlay).getDrawable()).getBitmap();
        });settleV15();
        uiAssert(()->{
            android.widget.ScrollView page=(android.widget.ScrollView)invoke(screen,"currentPageScroll",new Class[]{});
            if(screen.isDestroyed()||screen.isFinishing()||!Boolean.TRUE.equals(field(screen,"inThemeSettingsPage"))||page==null||Math.abs(page.getScrollY()-scroll[0])>1||((Number)field(screen,"currentTab")).intValue()!=tab[0])throw new Exception("Night switch lost its activity/page/scroll/tab");
            RootPagePager pager=(RootPagePager)field(screen,"rootPager");TodayDayPager day=(TodayDayPager)pager.page(0).findViewWithTag("today_day_pager");
            if(!date[0].equals(HomeCalendarDates.key(day.rootDestination()))||Boolean.TRUE.equals(field(screen,"homeCalendarExpanded"))!=expanded[0])throw new Exception("Night switch reset home date/calendar");
            if(field(screen,"themeModeTransitionOverlay")!=null||snapshot[0]!=null&&!snapshot[0].isRecycled())throw new Exception("Night transition retained its snapshot");mainFocusV21(screen);
        });gestureScreenV20=screen;return screen;
    }
    private MainActivity nightHeadersV34(MainActivity initial,Context c,String name)throws Exception {
        final MainActivity[] live={initial};
        final String[] modes={"yellow","green","red","blue","night"};
        for(int i=0;i<modes.length;i++){
            final String mode=modes[i];
            final MainActivity screen=live[0];
            uiAssert(()->{JSONObject p=(JSONObject)field(screen,"profile");p.put("decoration",mode).put("nightMode",false);invoke(screen,"saveProfile",new Class[]{});invoke(screen,"applyDecoration",new Class[]{});invoke(screen,"renderThemeSettingsPage",new Class[]{});});settleV15();
            // Exercise the same real toggle callback that users use, then return
            // through the production route instead of only replacing a test paint.
            live[0]=toggleNightWindowV35(screen,c,true);final MainActivity current=live[0];
            uiAssert(()->{if(!Boolean.TRUE.equals(invoke(current,"nightDecoration",new Class[]{})))throw new Exception("Actual night-mode toggle did not take effect");invoke(current,"closeThemeSettingsPage",new Class[]{});});settleV15();rootTabV33(current,1);
            final View[] header={null};final int[] expected={0};
            uiAssert(()->{
                header[0]=currentRootV33(current).page(1).findViewWithTag("sticky_paper_header");expected[0]=UiTokens.paper(true);
                if(header[0]==null||!header[0].isShown()||header[0].getWidth()<20)throw new Exception("Trace has no laid-out night header");
                Bitmap rendered=renderViewV26(header[0]);
                try{int core=header[0].getHeight()-PaperComponents.dp(c,UiTokens.HEADER_FADE);for(int y:new int[]{3,core/2,Math.max(3,core-3)})for(int x:new int[]{3,header[0].getWidth()-4}){if(Color.alpha(rendered.getPixel(x,y))!=0)throw new Exception("Night header painted a separate background: "+mode);};if(Color.alpha(rendered.getPixel(3,rendered.getHeight()-2))>32)throw new Exception("Night header fade ends as an opaque block");}finally{rendered.recycle();}
            });
            // Also inspect actual HWUI presentation; software draw alone cannot
            // detect a stale platform layer/gray background on the connected device.
            Bitmap presented=getUiAutomation().takeScreenshot();if(presented==null)throw new Exception("No presented night screenshot");
            try{Rect area=screenRect(header[0]);int y=area.top+(header[0].getHeight()-PaperComponents.dp(c,UiTokens.HEADER_FADE))/2;for(int x:new int[]{area.left+3,area.right-4})if(x>=0&&x<presented.getWidth()&&y>=0&&y<presented.getHeight())nearColorV31(expected[0],presented.getPixel(x,y),4,"Presented Trace night header differs from paper: "+mode);saveFrameV24(presented,name+"-night-header-"+mode);}finally{presented.recycle();}
        }
        return live[0];
    }
    private void fontDialogMeasureV34(MainActivity screen,Context c,String name)throws Exception {
        for(int width:new int[]{0,500}){
            final int targetWidth=width;
            uiAssert(()->{JSONObject p=(JSONObject)field(screen,"profile");p.put("font",targetWidth==0?100:126);invoke(screen,"saveProfile",new Class[]{});invoke(screen,"renderThemeSettingsPage",new Class[]{});invoke(screen,"chooseBookFont",new Class[]{});if(targetWidth>0)onlyCustomDialogV34(screen).getWindow().setLayout(Math.min(targetWidth,screen.getResources().getDisplayMetrics().widthPixels-PaperComponents.dp(c,48)),ViewGroup.LayoutParams.WRAP_CONTENT);});settleV15();
            uiAssert(()->{
                AlertDialog dialog=onlyCustomDialogV34(screen);View decor=dialog.getWindow().getDecorView();
                android.widget.ScrollView scroll=(android.widget.ScrollView)decor.findViewWithTag("book_font_choice_scroll");ViewGroup list=(ViewGroup)decor.findViewWithTag("book_font_choice_list");
                if(scroll==null||list==null||list.getChildCount()!=5||scroll.getHeight()<=0)throw new Exception("Font dialog lost its real five-choice list");
                int cap=PaperComponents.dp(c,426);if(scroll.getHeight()>cap+2||scroll.getHeight()>list.getHeight()+2)throw new Exception("Font dialog reserves fixed blank height below its choices");
                if(list.getHeight()<=cap&&Math.abs(scroll.getHeight()-list.getHeight())>2)throw new Exception("Short font choices do not shrink the dialog to content");
                for(int i=0;i<list.getChildCount();i++){
                    ViewGroup row=(ViewGroup)list.getChildAt(i);View texts=row.getChildAt(0);
                    if(texts.getTop()<row.getPaddingTop()-1||texts.getBottom()>row.getHeight()-row.getPaddingBottom()+1)throw new Exception("Large/narrow font choice clips its name/status text: row "+i);
                }
                if(list.getHeight()>scroll.getHeight())scroll.fullScroll(View.FOCUS_DOWN);
            });settleV15();uiAssert(()->{View decor=onlyCustomDialogV34(screen).getWindow().getDecorView();android.widget.ScrollView scroll=(android.widget.ScrollView)decor.findViewWithTag("book_font_choice_scroll");View list=decor.findViewWithTag("book_font_choice_list");if(list.getHeight()>scroll.getHeight()&&(scroll.getScrollY()<=0||scroll.canScrollVertically(1)))throw new Exception("Tall font choices did not reach the last option after smooth scroll settled");});screenshotNowV15(name+"-font-choice-"+targetWidth);
            uiAssert(()->{
                View decor=onlyCustomDialogV34(screen).getWindow().getDecorView();
                android.widget.ScrollView scroll=(android.widget.ScrollView)decor.findViewWithTag("book_font_choice_scroll");
                ViewGroup list=(ViewGroup)decor.findViewWithTag("book_font_choice_list");
                // A short parent (split-screen/limited dialog area) is a harder
                // constraint than the normal 426dp cap. Exercise both bounded modes.
                int left=scroll.getLeft(),top=scroll.getTop(),widthPx=scroll.getWidth();
                scroll.setSmoothScrollingEnabled(false);
                for(int mode:new int[]{View.MeasureSpec.AT_MOST,View.MeasureSpec.EXACTLY}){
                    scroll.forceLayout();scroll.measure(View.MeasureSpec.makeMeasureSpec(widthPx,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(160,mode));
                    if(scroll.getMeasuredHeight()>160||scroll.getMeasuredHeight()<=0)throw new Exception("Font choices ignored their short parent's height constraint: mode="+mode+", height="+scroll.getMeasuredHeight());
                    scroll.layout(left,top,left+scroll.getMeasuredWidth(),top+scroll.getMeasuredHeight());
                    if(list.getHeight()<=scroll.getHeight())throw new Exception("Short-height font fixture does not overflow");
                    scroll.fullScroll(View.FOCUS_DOWN);
                    View last=list.getChildAt(list.getChildCount()-1);
                    if(scroll.getScrollY()<=0||scroll.canScrollVertically(1)||last.getBottom()-scroll.getScrollY()>scroll.getHeight()-scroll.getPaddingBottom()+1)throw new Exception("Short-height font choices cannot expose their final option: mode="+mode);
                }
            });uiAssert(()->onlyCustomDialogV34(screen).dismiss());settleV15();
        }
    }
    private void layoutSaveStaysV34(MainActivity screen,Context c)throws Exception {
        uiAssert(()->{JSONObject p=(JSONObject)field(screen,"profile");p.put("font",100).put("layoutLevel",50).put("layout","medium");invoke(screen,"saveProfile",new Class[]{});invoke(screen,"renderThemeSettingsPage",new Class[]{});});settleV15();
        final int[] before={0};
        uiAssert(()->{
            android.widget.ScrollView scroll=(android.widget.ScrollView)invoke(screen,"currentPageScroll",new Class[]{});int range=Math.max(0,scroll.getChildAt(0).getHeight()-scroll.getHeight());if(range<=0)throw new Exception("Personalization fixture cannot exercise scroll preservation");scroll.scrollTo(0,Math.min(range,PaperComponents.dp(c,160)));before[0]=scroll.getScrollY();
            View row=setting((View)field(screen,"content"),"界面疏密");if(row==null||!row.performClick())throw new Exception("Interface density control is missing");
        });settleV15();
        uiAssert(()->{List<View> views=new ArrayList<>();collectViews(onlyCustomDialogV34(screen).getWindow().getDecorView(),views);android.widget.SeekBar seek=null;for(View view:views)if(view instanceof android.widget.SeekBar)seek=(android.widget.SeekBar)view;if(seek==null)throw new Exception("Density dialog lost its adjustable preview");seek.setProgress(72);});tapDialogV20(screen,"保存");settleV15();
        uiAssert(()->{
            android.widget.ScrollView scroll=(android.widget.ScrollView)invoke(screen,"currentPageScroll",new Class[]{});
            if(!Boolean.TRUE.equals(field(screen,"inThemeSettingsPage"))||Boolean.TRUE.equals(invoke(screen,"rootPagesVisible",new Class[]{}))||scroll==null||Math.abs(scroll.getScrollY()-before[0])>1)throw new Exception("Saving interface density left personalization or reset its scroll");
            if(((JSONObject)field(screen,"profile")).optInt("layoutLevel")!=72||setting((View)field(screen,"content"),"界面疏密")==null)throw new Exception("Density choice did not persist on its original page");
        });
    }
    private MainActivity uiReliabilityV34(MainActivity initial,Context c,android.content.SharedPreferences prefs,JSONObject fixture,String before,String name)throws Exception {
        final MainActivity[] live={initial};JSONObject original=new JSONObject(((JSONObject)field(initial,"profile")).toString());
        try{
            emptyHintV34(live[0],c,prefs,name);
            uiAssert(()->{setMainField(live[0],"days",new JSONObject(before));prefs.edit().putString(NutritionStore.DAYS,before).commit();invoke(live[0],"renderCurrent",new Class[]{});});settleV15();
            prepareAppearanceOriginV35(live[0]);live[0]=nightHeadersV34(live[0],c,name);fontDialogMeasureV34(live[0],c,name);layoutSaveStaysV34(live[0],c);
        }finally{
            if(gestureScreenV20!=null&&!gestureScreenV20.isDestroyed())live[0]=gestureScreenV20;
            uiAssert(()->{dismissCustomDialogsV34(live[0]);setMainField(live[0],"profile",new JSONObject(original.toString()));invoke(live[0],"saveProfile",new Class[]{});setMainField(live[0],"days",new JSONObject(before));prefs.edit().putString(NutritionStore.DAYS,before).commit();});
            live[0]=recreatedWindowV35(live[0]);
        }
        uiAssert(()->assertRenderPure(live[0],prefs,before));return live[0];
    }
    // The parent agent fills these phase hooks after the UI-only source is frozen.
    // Until then they fail explicitly; an unimplemented phase cannot report PASS.
    private void flushViewerV34(MainActivity screen)throws Exception {
        ((java.util.concurrent.ExecutorService)field(screen,"photoViewerExecutor")).submit(()->{}).get(5,java.util.concurrent.TimeUnit.SECONDS);waitForIdleSync();
    }
    private View waitViewerV34(MainActivity screen)throws Exception {
        flushViewerV34(screen);settleV15();final View[] result={null};uiAssert(()->{result[0]=(View)field(screen,"photoOverlay");if(result[0]==null||result[0].getParent()!=field(screen,"content"))throw new Exception("Asynchronous viewer never became live");});return result[0];
    }
    private java.util.concurrent.CountDownLatch blockViewerV34(MainActivity screen)throws Exception {
        java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(1),release=new java.util.concurrent.CountDownLatch(1);
        ((java.util.concurrent.ExecutorService)field(screen,"photoViewerExecutor")).execute(()->{entered.countDown();try{release.await(5,java.util.concurrent.TimeUnit.SECONDS);}catch(InterruptedException stopped){Thread.currentThread().interrupt();}});
        if(!entered.await(3,java.util.concurrent.TimeUnit.SECONDS))throw new Exception("Viewer worker did not enter the independent queue");return release;
    }
    private void mediaReliabilityV34(MainActivity screen,Context c,android.content.SharedPreferences prefs,JSONObject fixture,String today,String name)throws Exception {
        rootTabV33(screen,0);
        final List<String> photos=(List<String>)invoke(screen,"mealPhotoNames",new Class[]{String.class,String.class},today,"breakfast");if(photos.size()<4)throw new Exception("Media fixture lacks distinct candidates");
        java.util.concurrent.CountDownLatch gate=blockViewerV34(screen);
        final AlertDialog[] detail={null};JSONObject food=fixture.getJSONObject(today).getJSONObject("breakfast").getJSONArray("foodItems").getJSONObject(0);
        try{
            uiAssert(()->{detail[0]=((FoodUi)invoke(screen,"foodUi",new Class[]{})).details(today,"breakfast",food);detail[0].getWindow().getDecorView().findViewWithTag("food_detail_image").performClick();if(!detail[0].isShowing()||field(screen,"photoOverlay")!=null)throw new Exception("Details closed before its background decode succeeded");});
            uiAssert(()->{if(!detail[0].isShowing())throw new Exception("Blocked worker froze or destroyed the active detail dialog");});
        }finally{gate.countDown();}flushViewerV34(screen);settleV15();
        uiAssert(()->{if(detail[0].isShowing()||field(screen,"photoOverlay")==null)throw new Exception("Ready asynchronous viewer did not replace the detail");invoke(screen,"closePhotoViewer",new Class[]{});});settleV15();
        uiAssert(()->invoke(screen,"showPhotoViewer",new Class[]{String.class,String.class,String.class},today,"breakfast",photos.get(0)));flushViewerV34(screen);settleV15();
        final View[] viewer={null},image={null};final Bitmap[] initial={null};final String[] initialCount={null};
        uiAssert(()->{viewer[0]=(View)field(screen,"photoOverlay");image[0]=viewer[0].findViewWithTag("photo_viewer_image");initial[0]=(Bitmap)invokeAnyV20(image[0],"currentBitmap",new Class[]{});initialCount[0]=((android.widget.TextView)viewer[0].findViewWithTag("photo_viewer_counter")).getText().toString();});
        File candidate=new File(c.getFilesDir(),photos.get(1));byte[] source=java.nio.file.Files.readAllBytes(candidate.toPath());
        try{
            java.nio.file.Files.write(candidate.toPath(),new byte[]{9,8,7});
            uiAssert(()->invokeAnyV20(ownField(image[0],"swipeHandler"),"onSwipe",new Class[]{int.class},1));flushViewerV34(screen);
            uiAssert(()->{if(invokeAnyV20(image[0],"currentBitmap",new Class[]{})!=initial[0]||!initialCount[0].equals(((android.widget.TextView)viewer[0].findViewWithTag("photo_viewer_counter")).getText().toString()))throw new Exception("Corrupt next photo changed committed image/count");});
        }finally{java.nio.file.Files.write(candidate.toPath(),source);}
        gate=blockViewerV34(screen);try{uiAssert(()->{Object handler=ownField(image[0],"swipeHandler");for(int i=0;i<3;i++)invokeAnyV20(handler,"onSwipe",new Class[]{int.class},1);if(invokeAnyV20(image[0],"currentBitmap",new Class[]{})!=initial[0])throw new Exception("Queued request changed current image before decode");});}finally{gate.countDown();}flushViewerV34(screen);settleV15();
        uiAssert(()->{if(!("4/"+photos.size()).equals(((android.widget.TextView)viewer[0].findViewWithTag("photo_viewer_counter")).getText().toString()))throw new Exception("Rapid viewer requests did not commit their latest target");});
        gate=blockViewerV34(screen);try{uiAssert(()->{invokeAnyV20(ownField(image[0],"swipeHandler"),"onSwipe",new Class[]{int.class},1);invoke(screen,"closePhotoViewer",new Class[]{});});}finally{gate.countDown();}flushViewerV34(screen);settleV15();
        uiAssert(()->{if(field(screen,"photoOverlay")!=null||viewer[0].getParent()!=null)throw new Exception("Late decode resurrected a closed viewer");});
        java.util.concurrent.ExecutorService thumbs=(java.util.concurrent.ExecutorService)field(screen,"mediaThumbnailExecutor");java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(3),release=new java.util.concurrent.CountDownLatch(1);
        for(int i=0;i<3;i++)thumbs.execute(()->{entered.countDown();try{release.await(5,java.util.concurrent.TimeUnit.SECONDS);}catch(InterruptedException stopped){Thread.currentThread().interrupt();}});
        if(!entered.await(3,java.util.concurrent.TimeUnit.SECONDS))throw new Exception("Thumbnail workers failed to block");
        final FrameLayout host=new FrameLayout(screen);final android.widget.ImageView[] targets=new android.widget.ImageView[3];
        try{
            uiAssert(()->{((android.util.LruCache<?,?>)field(screen,"mediaThumbnailCache")).evictAll();((ViewGroup)field(screen,"root")).addView(host,new ViewGroup.LayoutParams(200,200));for(int i=0;i<3;i++){targets[i]=new android.widget.ImageView(screen);host.addView(targets[i],new FrameLayout.LayoutParams(80,80));invoke(screen,"loadMediaThumbnail",new Class[]{android.widget.ImageView.class,String.class,int.class},targets[i],photos.get(0),137);}
                Map<?,?> pending=(Map<?,?>)field(screen,"mediaThumbnailInFlight");Object list=pending.get(targets[0].getTag());if(!(list instanceof List)||((List<?>)list).size()!=3)throw new Exception("Same thumbnail request did not share its subscribers");});
        }finally{release.countDown();}
        long deadline=android.os.SystemClock.elapsedRealtime()+5000;boolean ready=false;do{waitForIdleSync();ready=targets[2].getDrawable()!=null;if(!ready)Thread.sleep(20);}while(!ready&&android.os.SystemClock.elapsedRealtime()<deadline);
        uiAssert(()->{Bitmap shared=null;for(android.widget.ImageView target:targets){if(!(target.getDrawable() instanceof android.graphics.drawable.BitmapDrawable))throw new Exception("Deduplicated thumbnail never reached every subscriber");Bitmap bitmap=((android.graphics.drawable.BitmapDrawable)target.getDrawable()).getBitmap();if(shared!=null&&shared!=bitmap)throw new Exception("Shared thumbnail was decoded more than once");shared=bitmap;}((ViewGroup)field(screen,"root")).removeView(host);});
    }
    private byte[] portableV34(MainActivity screen,boolean cloud)throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();invoke(screen,"writeBackup",new Class[]{OutputStream.class,boolean.class},out,cloud);return out.toByteArray();
    }
    private void privacyReliabilityV34(MainActivity screen,Context c,android.content.SharedPreferences prefs)throws Exception {
        JSONObject original=PrivateProfileStore.read(c),configured=new JSONObject(original.toString()).put("apiKey","synthetic-v34-placeholder").put("apiEndpoint","https://invalid.test/chat/completions").put("apiEnabled",false).put("cloudToken","synthetic-v34-session").put("foodPhotoConsent",false).put("foodAutoAnalyze",false).put("autoCloudSync",false);
        try{
            if(!PrivateProfileStore.write(c,configured))throw new Exception("Cannot write isolated private fixture");
            if(ProfilePrivacy.hasLocalOnly(NutritionStore.read(prefs,NutritionStore.PROFILE)))throw new Exception("Public profile still contains local authority");
            for(boolean cloud:new boolean[]{false,true}){
                byte[] archive=portableV34(screen,cloud);boolean found=false;try(java.util.zip.ZipInputStream zip=new java.util.zip.ZipInputStream(new ByteArrayInputStream(archive))){java.util.zip.ZipEntry entry;while((entry=zip.getNextEntry())!=null)if("profile.json".equals(entry.getName())){ByteArrayOutputStream text=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;while((n=zip.read(buffer))!=-1)text.write(buffer,0,n);JSONObject publicProfile=new JSONObject(text.toString("UTF-8"));if(ProfilePrivacy.hasLocalOnly(publicProfile)||text.toString("UTF-8").contains("synthetic-v34"))throw new Exception("Export leaked isolated local configuration");found=true;}}
                if(!found)throw new Exception("Export lost its profile document");
            }
            configured.remove("apiKey");configured.remove("cloudToken");if(!PrivateProfileStore.write(c,configured))throw new Exception("Cannot persist synthetic deletion");
            prefs.edit().putString(NutritionStore.PROFILE,new JSONObject(configured.toString()).put("apiKey","stale-v34-placeholder").put("cloudToken","foreign-v34-session").put("foodPhotoConsent",true).toString()).putString(NutritionStore.RESTORE_MARKER,"foreign-journal").commit();
            PrivateProfileStore.finishSystemRestore(c);PrivateProfileStore.prepareForBackup(c);
            JSONObject read=PrivateProfileStore.read(c);if(read.has("apiKey")||read.has("cloudToken")||read.optBoolean("foodPhotoConsent")||prefs.contains(NutritionStore.RESTORE_MARKER)||ProfilePrivacy.hasLocalOnly(NutritionStore.read(prefs,NutritionStore.PROFILE)))throw new Exception("Old restored settings revived deleted credentials/authority");
        }finally{PrivateProfileStore.write(c,original);uiAssert(()->{setMainField(screen,"profile",invoke(screen,"readProfile",new Class[]{}));invoke(screen,"renderCurrent",new Class[]{});});settleV15();}
    }
    private void restoreReliabilityV34(MainActivity screen,Context c,android.content.SharedPreferences prefs)throws Exception {
        byte[] baseline=portableV34(screen,false);String beforeDays=prefs.getString(NutritionStore.DAYS,"{}"),beforeProfile=PrivateProfileStore.read(c).toString();
        File live=new File(c.getFilesDir(),"qa_v34_transaction.png");byte[] old={1,4,7},replacement={2,5,8};java.nio.file.Files.write(live.toPath(),old);
        uiAssert(()->{setMainField(screen,"rootStopped",true);((View)field(screen,"root")).removeCallbacks((Runnable)field(screen,"refreshRootPages"));});
        try{
            JSONObject incomingDays=new JSONObject().put("2001-02-03",new JSONObject().put("lunch",new JSONObject().put("text","isolated restore").put("photos",new JSONArray().put(live.getName()))));
            JSONObject incomingProfile=new JSONObject().put("name","isolated restore").put("apiKey","foreign-test-placeholder").put("foodPhotoConsent",true);
            File sourceDir=new File(c.getCacheDir(),"qa-v34-export-"+java.util.UUID.randomUUID());sourceDir.mkdir();File source=new File(sourceDir,live.getName());java.nio.file.Files.write(source.toPath(),replacement);ByteArrayOutputStream bytes=new ByteArrayOutputStream();BackupArchive.write(bytes,incomingDays.toString(),incomingProfile.toString(),Collections.singletonList(source));
            invoke(screen,"restoreBackupFromStream",new Class[]{InputStream.class,boolean.class},new ByteArrayInputStream(bytes.toByteArray()),false);
            boolean mediaCommitted=Arrays.equals(replacement,java.nio.file.Files.readAllBytes(live.toPath())),dayCommitted=NutritionStore.read(prefs,NutritionStore.DAYS).has("2001-02-03");JSONObject localAfter=PrivateProfileStore.read(c);
            if(!mediaCommitted||!dayCommitted||!localAfter.optString("apiKey").isEmpty()||localAfter.optBoolean("foodPhotoConsent")||prefs.contains(NutritionStore.RESTORE_MARKER))throw new Exception("Real restore state failed: media="+mediaCommitted+", day="+dayCommitted+", keyPresent="+!localAfter.optString("apiKey").isEmpty()+", consent="+localAfter.optBoolean("foodPhotoConsent")+", marker="+prefs.contains(NutritionStore.RESTORE_MARKER));
            String committed=prefs.getString(NutritionStore.DAYS,"{}");byte[] corrupt={0,1,2};boolean failed=false;try{invoke(screen,"restoreBackupFromStream",new Class[]{InputStream.class,boolean.class},new ByteArrayInputStream(corrupt),false);}catch(Exception expected){failed=true;}
            if(!failed||!committed.equals(prefs.getString(NutritionStore.DAYS,"{}"))||!Arrays.equals(replacement,java.nio.file.Files.readAllBytes(live.toPath())))throw new Exception("Invalid archive changed live restored data");
            for(boolean failMarker:new boolean[]{true,false}){
                final boolean[] fired={false};android.content.SharedPreferences uncertain=(android.content.SharedPreferences)java.lang.reflect.Proxy.newProxyInstance(android.content.SharedPreferences.class.getClassLoader(),new Class[]{android.content.SharedPreferences.class},(proxy,method,args)->{
                    if(!"edit".equals(method.getName()))return method.invoke(prefs,args);
                    android.content.SharedPreferences.Editor editor=prefs.edit();Set<String> written=new HashSet<>();final Object[] forwarding={null};
                    forwarding[0]=java.lang.reflect.Proxy.newProxyInstance(android.content.SharedPreferences.Editor.class.getClassLoader(),new Class[]{android.content.SharedPreferences.Editor.class},(p,m,a)->{
                        if("putString".equals(m.getName()))written.add((String)a[0]);Object result=m.invoke(editor,a);
                        if("commit".equals(m.getName())&&!fired[0]&&(failMarker?written.contains(NutritionStore.RESTORE_MARKER):written.contains(NutritionStore.DAYS))){fired[0]=true;return false;}
                        return result instanceof android.content.SharedPreferences.Editor?forwarding[0]:result;
                    });return forwarding[0];
                });
                uiAssert(()->setMainField(screen,"prefs",uncertain));boolean rejected=false;
                try{invoke(screen,"restoreBackupFromStream",new Class[]{InputStream.class,boolean.class},new ByteArrayInputStream(baseline),false);}catch(Exception expected){rejected=true;}finally{uiAssert(()->setMainField(screen,"prefs",prefs));}
                if(!fired[0]||!rejected||!committed.equals(prefs.getString(NutritionStore.DAYS,"{}"))||!Arrays.equals(replacement,java.nio.file.Files.readAllBytes(live.toPath()))||prefs.contains(NutritionStore.RESTORE_MARKER))throw new Exception("Uncertain Android preference commit failed to roll back: marker="+failMarker);
            }
            // Simulate process death after file installation but before the JSON commit.
            File journal=new File(c.getNoBackupFilesDir(),BackupRestoreRecovery.PREFIX+java.util.UUID.randomUUID());new File(journal,"incoming").mkdirs();java.nio.file.Files.write(new File(journal,"incoming/"+live.getName()).toPath(),old);
            BackupFileTransaction tx=new BackupFileTransaction(c.getFilesDir(),journal,Collections.singletonList(live.getName()),Collections.emptyList());BackupFileTransaction.saveState(journal,committed,NutritionStore.read(prefs,NutritionStore.PROFILE).toString());prefs.edit().putString(NutritionStore.RESTORE_MARKER,journal.getName()).commit();tx.install();BackupRestoreRecovery.recover(c);BackupRestoreRecovery.recover(c);
            if(!Arrays.equals(replacement,java.nio.file.Files.readAllBytes(live.toPath()))||!committed.equals(prefs.getString(NutritionStore.DAYS,"{}"))||prefs.contains(NutritionStore.RESTORE_MARKER)||journal.exists())throw new Exception("Startup-style recovery lost media/JSON or was not idempotent");
            BackupArchive.deleteOwnedTree(sourceDir);
        }finally{
            invoke(screen,"restoreBackupFromStream",new Class[]{InputStream.class,boolean.class},new ByteArrayInputStream(baseline),false);PrivateProfileStore.write(c,new JSONObject(beforeProfile));live.delete();
            uiAssert(()->{setMainField(screen,"rootStopped",false);setMainField(screen,"days",NutritionStore.read(prefs,NutritionStore.DAYS));setMainField(screen,"profile",invoke(screen,"readProfile",new Class[]{}));invoke(screen,"renderCurrent",new Class[]{});});settleV15();
        }
        if(!beforeDays.equals(prefs.getString(NutritionStore.DAYS,"{}")))throw new Exception("Restore regression did not put back the initial diary");
        ActivityMonitor blocked=addMonitor(RestoreRecoveryActivity.class.getName(),null,false),resumed=null;
        try{
            prefs.edit().putString(NutritionStore.RESTORE_MARKER,"invalid-isolated-journal").commit();uiAssert(()->screen.recreate());
            Activity recovery=waitForMonitorWithTimeout(blocked,5000);if(!(recovery instanceof RestoreRecoveryActivity)||!prefs.contains(NutritionStore.RESTORE_MARKER)||!beforeDays.equals(prefs.getString(NutritionStore.DAYS,"{}")))throw new Exception("Interrupted restore did not enter a non-destructive recovery screen");
            prefs.edit().remove(NutritionStore.RESTORE_MARKER).commit();resumed=addMonitor(MainActivity.class.getName(),null,false);
            uiAssert(()->{View retry=recovery.findViewById(android.R.id.content).findViewWithTag("restore_recovery_retry");if(retry==null||!retry.performClick())throw new Exception("Missing recovery retry action");});
            Activity restored=waitForMonitorWithTimeout(resumed,5000);if(!(restored instanceof MainActivity))throw new Exception("Recovery retry did not reopen the diary");gestureScreenV20=(MainActivity)restored;settleV15();
        }finally{removeMonitor(blocked);if(resumed!=null)removeMonitor(resumed);prefs.edit().remove(NutritionStore.RESTORE_MARKER).commit();}
    }
    private static final class HeaderSampleV35 {
        final int[] pixels;final int headerContrast,bodyContrast;
        HeaderSampleV35(int[] pixels,int headerContrast,int bodyContrast){this.pixels=pixels;this.headerContrast=headerContrast;this.bodyContrast=bodyContrast;}
    }
    private int luminanceV35(int color){return (Color.red(color)*54+Color.green(color)*183+Color.blue(color)*19)/256;}
    private int contrastV35(Bitmap bitmap,Rect area)throws Exception {
        Rect clipped=new Rect(area);if(!clipped.intersect(0,0,bitmap.getWidth(),bitmap.getHeight())||clipped.width()<8||clipped.height()<8)throw new Exception("Invalid header/body contrast viewport");
        int low=255,high=0;for(int y=clipped.top;y<clipped.bottom;y+=3)for(int x=clipped.left;x<clipped.right;x+=3){int value=luminanceV35(bitmap.getPixel(x,y));low=Math.min(low,value);high=Math.max(high,value);}return high-low;
    }
    private HeaderSampleV35 headerFrameV35(MainActivity screen,Context c,String route,String name)throws Exception {
        final View[] header={null};final Rect[] area={null},body={null};final int[] paper={0},cardPoint={-1,-1};
        uiAssert(()->{
            View content=(View)field(screen,"content"),source=Boolean.TRUE.equals(invoke(screen,"rootPagesVisible",new Class[]{}))?currentRootV33(screen).selectedPage():content;
            header[0]=source.findViewWithTag("sticky_paper_header");paper[0]=UiTokens.paper(Boolean.TRUE.equals(invoke(screen,"nightDecoration",new Class[]{})));
            if(header[0]==null||!header[0].isShown())throw new Exception("No live shared header: "+route);
            area[0]=screenRect(header[0]);int core=header[0].getHeight()-PaperComponents.dp(c,UiTokens.HEADER_FADE);if(core<8)throw new Exception("Header has no opaque core");
            Bitmap local=renderViewV26(header[0]);try{for(int x:new int[]{3,local.getWidth()-4})for(int y:new int[]{3,core/2,core-3}){if(Boolean.TRUE.equals(invoke(screen,"nightDecoration",new Class[]{}))){if(Color.alpha(local.getPixel(x,y))!=0)throw new Exception("Transparent night header paints a core: "+route);}else nearColorV31(paper[0],local.getPixel(x,y),1,"Local header paper mismatch: "+route);};}finally{local.recycle();}
            View root=(View)field(screen,"root");Rect rootRect=screenRect(root);body[0]=new Rect(area[0].left+3,area[0].bottom+PaperComponents.dp(c,8),area[0].right-3,rootRect.bottom-PaperComponents.dp(c,124));
            Bitmap full=renderViewV26(root);try{
                int localX=area[0].left-rootRect.left+3,localY=area[0].top-rootRect.top+core/2;
                nearColorV31(paper[0],full.getPixel(localX,localY),1,"Attached full-root draw changed header paper: "+route);
                Rect localBody=new Rect(body[0]);localBody.offset(-rootRect.left,-rootRect.top);
                if(contrastV35(full,localBody)<40)throw new Exception("Header draw occluded the attached page body: "+route);
                int sampleY=Math.min(full.getHeight()-1,localBody.top+2);if(Color.alpha(full.getPixel(localX,sampleY))<254)throw new Exception("Fade layer cleared pixels outside its strip: "+route);
            }finally{full.recycle();}
            List<View> all=new ArrayList<>();collectViews(source,all);for(View view:all){Object tag=view.getTag();if(tag instanceof String&&((String)tag).startsWith("setting_row_")&&view.isShown()){
                Rect row=screenRect(view);int x=Math.round(row.exactCenterX()),y=row.top+Math.min(PaperComponents.dp(c,8),row.height()/4);if(body[0].contains(x,y)){cardPoint[0]=x;cardPoint[1]=y;break;}
            }}
        });
        Bitmap presented=getUiAutomation().takeScreenshot();if(presented==null)throw new Exception("No HWUI header screenshot");
        try{
            int core=header[0].getHeight()-PaperComponents.dp(c,UiTokens.HEADER_FADE),y=area[0].top+core/2,left=area[0].left+3,right=area[0].right-4;
            nearColorV31(paper[0],presented.getPixel(left,y),4,"Presented left header lifted away from page paper: "+route);
            nearColorV31(paper[0],presented.getPixel(right,y),4,"Presented right header lifted away from page paper: "+route);
            int bodyY=body[0].top+2;nearColorV31(paper[0],presented.getPixel(left,bodyY),5,"Header/body paper boundary differs: "+route);
            Rect headerCore=new Rect(area[0].left,area[0].top,area[0].right,area[0].top+core);int hc=contrastV35(presented,headerCore),bc=contrastV35(presented,body[0]);
            if(hc<40||bc<40)throw new Exception("Header/body text contrast collapsed: "+route+", header="+hc+", body="+bc);
            int[] samples=cardPoint[0]<0?new int[]{presented.getPixel(left,y),presented.getPixel(right,y),presented.getPixel(left,bodyY)}:new int[]{presented.getPixel(left,y),presented.getPixel(right,y),presented.getPixel(left,bodyY),presented.getPixel(cardPoint[0],cardPoint[1])};
            saveFrameV24(presented,name+"-"+route);return new HeaderSampleV35(samples,hc,bc);
        }finally{presented.recycle();}
    }
    private void stableHeaderV35(HeaderSampleV35 first,HeaderSampleV35 next,String route)throws Exception {
        if(first.pixels.length!=next.pixels.length)throw new Exception("Header route changed its stable landmarks: "+route);
        for(int i=0;i<first.pixels.length;i++)nearColorV31(first.pixels[i],next.pixels[i],4,"Header/card color changed after return to stable frame: "+route+", landmark="+i);
        if(Math.abs(first.headerContrast-next.headerContrast)>8||Math.abs(first.bodyContrast-next.bodyContrast)>8)throw new Exception("Header/body contrast changed after modal/navigation cleanup: "+route);
    }
    private void scrollRestoreDetachV35(MainActivity screen)throws Exception {
        uiAssert(()->{
            android.widget.ScrollView scroll=new android.widget.ScrollView(screen);View child=new View(screen);scroll.addView(child,new android.widget.ScrollView.LayoutParams(200,1200));
            ViewGroup root=(ViewGroup)field(screen,"root");Class<?> type=Class.forName("com.eatrecord.app.MainActivity$ScrollPositionRestore");java.lang.reflect.Constructor<?> ctor=type.getDeclaredConstructor(android.widget.ScrollView.class,int.class);ctor.setAccessible(true);
            for(boolean attachFirst:new boolean[]{false,true}){
                Object restore=ctor.newInstance(scroll,150);
                try{
                    if(attachFirst)root.addView(scroll,new ViewGroup.LayoutParams(200,200));
                    invokeAnyV20(restore,"start",new Class[]{});
                    if(!attachFirst)root.addView(scroll,new ViewGroup.LayoutParams(200,200));
                    if(ownField(restore,"observer")==null)throw new Exception("Scroll restoration did not observe its attached owner");
                    root.removeView(scroll);
                    if(!Boolean.TRUE.equals(ownField(restore,"disposed"))||ownField(restore,"observer")!=null)throw new Exception("Removed page retained its preDraw observer");
                    invokeAnyV20(restore,"onPreDraw",new Class[]{});
                    if(scroll.getScrollY()!=0)throw new Exception("Canceled preDraw moved its removed page");
                }finally{if(scroll.getParent()==root)root.removeView(scroll);}
            }
        });
    }
    private void scrollBelowHeaderV35(MainActivity screen)throws Exception {
        final android.widget.ScrollView[] scroll={null};final int[] original={0};final Bitmap[] before={null};final int[] core={0};
        uiAssert(()->{
            scroll[0]=(android.widget.ScrollView)invoke(screen,"currentPageScroll",new Class[]{});if(scroll[0]==null)return;
            original[0]=scroll[0].getScrollY();View source=Boolean.TRUE.equals(invoke(screen,"rootPagesVisible",new Class[]{}))?currentRootV33(screen).selectedPage():(View)field(screen,"content");
            View header=source.findViewWithTag("sticky_paper_header");core[0]=header.getHeight()-PaperComponents.dp(screen,UiTokens.HEADER_FADE);
            if(!scroll[0].getClipToPadding()||scroll[0].getPaddingTop()<core[0])throw new Exception("Night content is not kept below its transparent header");
            before[0]=renderViewV26((View)field(screen,"root"));int range=Math.max(0,scroll[0].getChildAt(0).getHeight()-scroll[0].getHeight()+scroll[0].getPaddingTop()+scroll[0].getPaddingBottom());scroll[0].scrollTo(0,range);
        });settleV15();
        try{uiAssert(()->{
            if(before[0]==null)return;Bitmap after=renderViewV26((View)field(screen,"root"));
            try{int width=after.getWidth();int[] a=new int[width*core[0]],b=new int[a.length];before[0].getPixels(a,0,width,0,0,width,core[0]);after.getPixels(b,0,width,0,0,width,core[0]);for(int i=0;i<a.length;i++)if(changedPixelV32(a[i],b[i]))throw new Exception("Scrolled body text/media painted behind the fixed title");}
            finally{after.recycle();}
        });}finally{uiAssert(()->{if(scroll[0]!=null)scroll[0].scrollTo(0,original[0]);if(before[0]!=null)before[0].recycle();});settleV15();}
    }
    private void openHeaderRouteV35(MainActivity screen,String route,boolean interrupt)throws Exception {
        rootTabV33(screen,"trace".equals(route)?1:3);
        if("trace".equals(route)){
            if(interrupt){
                // Settings are entered from Me in the real UI. Retarget from
                // that owner, rather than manufacturing a child under Trace.
                rootTabV33(screen,3);uiAssert(()->{invoke(screen,"openThemeSettingsPage",new Class[]{});invoke(screen,"openPreferencesPage",new Class[]{});invoke(screen,"showTab",new Class[]{int.class,boolean.class},1,true);});settleV15();
                uiAssert(()->rootAtRestV26(screen,1));
            }return;
        }
        final String method="personalization".equals(route)?"openThemeSettingsPage":"other".equals(route)?"openPreferencesPage":"notebook".equals(route)?"openNotebookSettingsPage":"openDataSecurityPage";
        uiAssert(()->{
            if(interrupt){invoke(screen,"openThemeSettingsPage",new Class[]{});invoke(screen,"openPreferencesPage",new Class[]{});}
            invoke(screen,method,new Class[]{});
        });settleV15();
    }
    private MainActivity sharedHeadersV35(MainActivity initial,Context c,android.content.SharedPreferences prefs,String before,String name)throws Exception {
        scrollRestoreDetachV35(initial);
        final MainActivity[] live={initial};JSONObject original=new JSONObject(((JSONObject)field(initial,"profile")).toString());
        try{
            uiAssert(()->{((JSONObject)field(live[0],"profile")).put("decoration","yellow");invoke(live[0],"saveProfile",new Class[]{});invoke(live[0],"applyDecoration",new Class[]{});invoke(live[0],"applyNightModeOverride",new Class[]{});invoke(live[0],"refreshCurrentThemeSurface",new Class[]{});invoke(live[0],"renderCurrent",new Class[]{});nearColorV31(0xfffaf7f0,UiTokens.paper(false),0,"Header fixture lost the normal yellow day palette");});settleV15();prepareAppearanceOriginV35(live[0]);
            int cycle=0;
            for(boolean night:new boolean[]{false,true,false,true}){
                final int pass=cycle++;
                if(Boolean.TRUE.equals(invoke(live[0],"nightDecoration",new Class[]{}))!=night){
                    openHeaderRouteV35(live[0],"personalization",false);live[0]=toggleNightWindowV35(live[0],c,night);
                }
                final MainActivity screen=live[0];
                if(Boolean.TRUE.equals(invoke(screen,"nightDecoration",new Class[]{}))!=night)throw new Exception("Night toggle did not reach its requested state");
                uiAssert(()->{android.util.TypedValue themeFlag=new android.util.TypedValue();if(!screen.getTheme().resolveAttribute(android.R.attr.isLightTheme,themeFlag,true)||(themeFlag.data!=0)==night)throw new Exception("Native theme classification disagrees with the visible day/night mode");android.util.TypedValue decorFlag=new android.util.TypedValue();if(!screen.getWindow().getDecorView().getContext().getTheme().resolveAttribute(android.R.attr.isLightTheme,decorFlag,true)||(decorFlag.data!=0)==night)throw new Exception("Native DecorContext theme retained the previous day/night classification");});
                for(String route:new String[]{"trace","personalization","other","notebook","security"}){
                    String label="cycle"+pass+"-"+(night?"night":"day")+"-"+route;openHeaderRouteV35(screen,route,false);HeaderSampleV35 baseline=headerFrameV35(screen,c,label,name+"-normal");
                    if(night)scrollBelowHeaderV35(screen);
                    final AlertDialog[] modal={null};uiAssert(()->{modal[0]=new AlertDialog.Builder(screen).create();invoke(screen,"showRoundedDialog",new Class[]{AlertDialog.class,View.class},modal[0],PaperComponents.text(screen,"QA header modal",16,UiTokens.ink(night),false));dialogWindowV33(screen,modal[0]);});settleV15();uiAssert(()->modal[0].dismiss());settleV15();
                    uiAssert(()->{if(((Number)field(screen,"activeGlassDialogCount")).intValue()!=0)throw new Exception("Modal retained a background-effect owner");});stableHeaderV35(baseline,headerFrameV35(screen,c,label,name+"-modal-closed"),label);
                    openHeaderRouteV35(screen,route,true);stableHeaderV35(baseline,headerFrameV35(screen,c,label,name+"-interrupted"),label);
                }
            }
        }finally{
            if(gestureScreenV20!=null&&!gestureScreenV20.isDestroyed())live[0]=gestureScreenV20;
            uiAssert(()->{dismissCustomDialogsV34(live[0]);setMainField(live[0],"profile",new JSONObject(original.toString()));invoke(live[0],"saveProfile",new Class[]{});});live[0]=recreatedWindowV35(live[0]);
        }
        uiAssert(()->assertRenderPure(live[0],prefs,before));return live[0];
    }
    private android.widget.EditText releaseEditorInputV060(AlertDialog dialog)throws Exception {
        View view=dialog.getWindow().getDecorView().findViewWithTag("meal_record_text");
        if(!(view instanceof android.widget.EditText))throw new Exception("Release meal editor is not the original inline input");
        return (android.widget.EditText)view;
    }
    private void editorCenterV060(android.widget.EditText input,boolean hint)throws Exception {
        android.text.Layout layout=input.getLayout();
        if(layout==null||input.getWidth()<=0||input.getHeight()<=0)throw new Exception("Meal editor has no measured text layout");
        if(!hint){
            if(layout.getLineLeft(0)>PaperComponents.dp(input.getContext(),1))throw new Exception("Meal text changed from left alignment to horizontal centering");
            int available=input.getHeight()-input.getCompoundPaddingTop()-input.getCompoundPaddingBottom();
            if(layout.getHeight()<=available){
                float top=input.getTotalPaddingTop()-input.getScrollY()+layout.getLineTop(0),bottom=input.getTotalPaddingTop()-input.getScrollY()+layout.getLineBottom(layout.getLineCount()-1);
                if(Math.abs((top+bottom)*.5f-input.getHeight()*.5f)>PaperComponents.dp(input.getContext(),2))throw new Exception("Visible meal text block is not vertically centered: block="+top+".."+bottom+", field="+input.getHeight());
            }else if(input.getScrollY()==0&&Math.abs(input.getTotalPaddingTop()-input.getCompoundPaddingTop())>PaperComponents.dp(input.getContext(),2))throw new Exception("Overflowing meal text starts outside its scrollable viewport");
        }else{
            Bitmap pixels=Bitmap.createBitmap(input.getWidth(),input.getHeight(),Bitmap.Config.ARGB_8888);input.draw(new Canvas(pixels));
            try{
                int top=input.getHeight(),bottom=-1;
                for(int y=0;y<input.getHeight();y++)for(int x=0;x<input.getWidth()-PaperComponents.dp(input.getContext(),5);x++)if(Color.alpha(pixels.getPixel(x,y))>30){top=Math.min(top,y);bottom=Math.max(bottom,y);}
                if(bottom<top)throw new Exception("Empty editor hint is not visible");
                float tolerance=Math.max(PaperComponents.dp(input.getContext(),3),input.getTextSize()*.28f);
                if(Math.abs((top+bottom+1)*.5f-input.getHeight()*.5f)>tolerance)throw new Exception("Empty editor hint ink is not vertically centered: ink="+top+".."+bottom+", field="+input.getHeight());
            }finally{pixels.recycle();}
        }
    }
    private void editorLayoutV060(MainActivity screen,Context c,android.content.SharedPreferences prefs,String today,String before,String name)throws Exception {
        JSONObject originalProfile=new JSONObject(((JSONObject)field(screen,"profile")).toString());
        try{
            for(int width:new int[]{0,500}){
                final int targetWidth=width;final AlertDialog[] dialog={null};final android.widget.EditText[] input={null};
                uiAssert(()->{
                    ((JSONObject)field(screen,"profile")).put("font",targetWidth==0?100:126);invoke(screen,"saveProfile",new Class[]{});
                    dialog[0]=(AlertDialog)invoke(screen,"showMealRecordEditor",new Class[]{String.class,String.class},today,"lunch");input[0]=releaseEditorInputV060(dialog[0]);input[0].setCursorVisible(false);
                    if(targetWidth>0)dialog[0].getWindow().setLayout(Math.min(targetWidth,c.getResources().getDisplayMetrics().widthPixels-PaperComponents.dp(c,48)),ViewGroup.LayoutParams.WRAP_CONTENT);
                });settleV15();
                try{
                    StringBuilder longText=new StringBuilder();for(int i=1;i<=16;i++){if(i>1)longText.append('\n');longText.append("午餐记录第").append(i).append("行：米饭和青菜");}
                    for(String sample:new String[]{"老乡鸡","老乡鸡\n米饭青菜",longText.toString()}){
                        uiAssert(()->{input[0].setText(sample);input[0].setSelection(0);input[0].scrollTo(0,0);});settleV15();
                        uiAssert(()->editorCenterV060(input[0],false));
                        if(sample.equals(longText.toString())){
                            uiAssert(()->{if(input[0].getLineCount()<=6)throw new Exception("Long meal fixture did not exercise input overflow");input[0].setSelection(input[0].length());input[0].bringPointIntoView(input[0].length());});settleV15();
                            uiAssert(()->{android.text.Layout layout=input[0].getLayout();int bottom=layout.getLineBottom(layout.getLineCount()-1)-input[0].getScrollY();int viewport=input[0].getHeight()-input[0].getCompoundPaddingTop()-input[0].getCompoundPaddingBottom();if(input[0].getScrollY()<=0||bottom>viewport+PaperComponents.dp(c,4))throw new Exception("Long meal input cannot expose its final line");});
                        }
                    }
                    uiAssert(()->{input[0].setText("");input[0].setHint("午餐");input[0].scrollTo(0,0);});settleV15();uiAssert(()->editorCenterV060(input[0],true));
                    screenshotNowV15(name+"-editor-center-"+targetWidth);
                    uiAssert(()->{input[0].setText("老乡鸡");input[0].setCursorVisible(true);input[0].requestFocus();input[0].performClick();((android.view.inputmethod.InputMethodManager)c.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(input[0],android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);});settleV15();
                    uiAssert(()->{int editors=0;for(View root:android.view.inspector.WindowInspector.getGlobalWindowViews())if(root.isShown()&&root.findViewWithTag("meal_record_body")!=null)editors++;if(!dialog[0].isShowing()||!input[0].hasFocus()||editors!=1||dialog[0].getWindow().getDecorView().findViewWithTag("manual_food_text")!=null)throw new Exception("Keyboard focus launched a nested editor or lost the inline owner");((android.view.inputmethod.InputMethodManager)c.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input[0].getWindowToken(),0);});settleV15();
                }finally{uiAssert(()->{if(dialog[0]!=null&&dialog[0].isShowing()){input[0].setText("老乡鸡");dialog[0].dismiss();}});settleV15();}
                if(!before.equals(prefs.getString(NutritionStore.DAYS,"{}")))throw new Exception("Layout-only editor probes changed saved diary data");
            }
        }finally{uiAssert(()->{setMainField(screen,"profile",originalProfile);invoke(screen,"saveProfile",new Class[]{});});}
    }
    private void preserveMealFieldsV060(JSONObject expected,JSONObject actual)throws Exception {
        if(actual==null)throw new Exception("Editor removed the saved meal");
        for(String key:new String[]{"photos","stickers","foodItems","calories","workerMarker"})if(!String.valueOf(expected.opt(key)).equals(String.valueOf(actual.opt(key))))throw new Exception("Meal text save overwrote fresh field "+key);
    }
    private void editorSaveV060(MainActivity screen,Context c,android.content.SharedPreferences prefs,JSONObject fixture,String today)throws Exception {
        final AlertDialog[] dialog={null};final android.widget.EditText[] input={null};JSONObject fresh=new JSONObject(fixture.getJSONObject(today).getJSONObject("breakfast").toString()).put("workerMarker",601).put("calories",111);
        fresh.getJSONArray("foodItems").getJSONObject(0).put("nutrients",FoodAnalysisResult.parse(RESPONSE).value.getJSONObject("nutrients"));
        uiAssert(()->{dialog[0]=(AlertDialog)invoke(screen,"showMealRecordEditor",new Class[]{String.class,String.class},today,"lunch");input[0]=releaseEditorInputV060(dialog[0]);input[0].setText("午餐新文字\n米饭青菜");JSONObject latest=NutritionStore.read(prefs,NutritionStore.DAYS);latest.getJSONObject(today).put("lunch",new JSONObject(fresh.toString()).put("text","后台旧文字"));prefs.edit().putString(NutritionStore.DAYS,latest.toString()).commit();dialog[0].getWindow().getDecorView().findViewWithTag("meal_record_done").performClick();});settleV15();
        JSONObject saved=NutritionStore.meal(NutritionStore.read(prefs,NutritionStore.DAYS),today,"lunch");preserveMealFieldsV060(fresh,saved);if(!"午餐新文字\n米饭青菜".equals(saved.optString("text")))throw new Exception("Done did not save inline meal text");
        uiAssert(()->{dialog[0]=(AlertDialog)invoke(screen,"showMealRecordEditor",new Class[]{String.class,String.class},today,"lunch");input[0]=releaseEditorInputV060(dialog[0]);input[0].setText("返回也保存的午餐");((android.view.inputmethod.InputMethodManager)c.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input[0].getWindowToken(),0);});settleV15();sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);settleV15();
        uiAssert(()->{if(dialog[0].isShowing())throw new Exception("Real Back did not dismiss the meal editor");});saved=NutritionStore.meal(NutritionStore.read(prefs,NutritionStore.DAYS),today,"lunch");preserveMealFieldsV060(fresh,saved);if(!"返回也保存的午餐".equals(saved.optString("text")))throw new Exception("Real Back lost changed meal text");
        uiAssert(()->{dialog[0]=(AlertDialog)invoke(screen,"showMealRecordEditor",new Class[]{String.class,String.class},today,"lunch");input[0]=releaseEditorInputV060(dialog[0]);input[0].setText("");dialog[0].getWindow().getDecorView().findViewWithTag("meal_record_done").performClick();});settleV15();saved=NutritionStore.meal(NutritionStore.read(prefs,NutritionStore.DAYS),today,"lunch");preserveMealFieldsV060(fresh,saved);if(saved.has("text"))throw new Exception("Clearing meal text did not clear only the text");
        String unchanged=prefs.getString(NutritionStore.DAYS,"{}");uiAssert(()->{dialog[0]=(AlertDialog)invoke(screen,"showMealRecordEditor",new Class[]{String.class,String.class},"1998-01-02","lunch");dialog[0].dismiss();});settleV15();if(!unchanged.equals(prefs.getString(NutritionStore.DAYS,"{}")))throw new Exception("Empty open/close created a diary entry");
    }
    private void viewerPauseV060(MainActivity screen,Context c,android.content.SharedPreferences prefs,String today)throws Exception {
        String before=prefs.getString(NutritionStore.DAYS,"{}");rootTabV33(screen,0);final List<String> photos=(List<String>)invoke(screen,"mealPhotoNames",new Class[]{String.class,String.class},today,"breakfast");if(photos.size()<4)throw new Exception("Viewer pause fixture lacks candidates");
        uiAssert(()->invoke(screen,"showPhotoViewer",new Class[]{String.class,String.class,String.class},today,"breakfast",photos.get(0)));View viewer=waitViewerV34(screen),image=viewer.findViewWithTag("photo_viewer_image");Runnable oldReset=(Runnable)field(screen,"resetPhotoViewerTarget");java.util.concurrent.CountDownLatch gate=blockViewerV34(screen);
        try{uiAssert(()->{invokeAnyV20(ownField(image,"swipeHandler"),"onSwipe",new Class[]{int.class},1);invoke(screen,"onPause",new Class[]{});invoke(screen,"onResume",new Class[]{});});}finally{gate.countDown();}flushViewerV34(screen);settleV15();
        uiAssert(()->invokeAnyV20(ownField(image,"swipeHandler"),"onSwipe",new Class[]{int.class},1));flushViewerV34(screen);settleV15();
        final Bitmap expected=BitmapFactory.decodeFile(new File(c.getFilesDir(),photos.get(1)).getAbsolutePath());
        try{uiAssert(()->{String counter=((android.widget.TextView)viewer.findViewWithTag("photo_viewer_counter")).getText().toString();Bitmap actual=(Bitmap)invokeAnyV20(image,"currentBitmap",new Class[]{});if(!("2/"+photos.size()).equals(counter)||actual==null||expected==null||actual.getPixel(actual.getWidth()/2,actual.getHeight()/2)!=expected.getPixel(expected.getWidth()/2,expected.getHeight()/2))throw new Exception("First swipe after pause skipped the canceled photo");invoke(screen,"showPhotoViewer",new Class[]{String.class,String.class,String.class},today,"breakfast",photos.get(0));});}finally{if(expected!=null)expected.recycle();}
        View replacement=waitViewerV34(screen),nextImage=replacement.findViewWithTag("photo_viewer_image");Runnable nextReset=(Runnable)field(screen,"resetPhotoViewerTarget");gate=blockViewerV34(screen);
        try{uiAssert(()->{invokeAnyV20(ownField(nextImage,"swipeHandler"),"onSwipe",new Class[]{int.class},1);if(oldReset!=null)oldReset.run();if(field(screen,"resetPhotoViewerTarget")!=nextReset)throw new Exception("Old viewer reset removed its replacement owner");});}finally{gate.countDown();}flushViewerV34(screen);settleV15();
        uiAssert(()->{if(!("2/"+photos.size()).equals(((android.widget.TextView)replacement.findViewWithTag("photo_viewer_counter")).getText().toString()))throw new Exception("Old viewer reset changed a new pending target");invoke(screen,"closePhotoViewer",new Class[]{});});settleV15();if(!before.equals(prefs.getString(NutritionStore.DAYS,"{}")))throw new Exception("Viewer pause changed diary data");
    }
    private void releaseV060(Context c,android.content.SharedPreferences prefs)throws Exception {
        String phase=args.getString("phase","all"),name=args.getString("name","release060");if(!Arrays.asList("all","editor","media").contains(phase))throw new Exception("Unknown release-v060 phase: "+phase);
        FoodAnalysisJobService.cancelAll(c);FoodDraftStore.discard(c,FoodDraftStore.pendingSources(c));java.util.concurrent.atomic.AtomicInteger requests=denyNetworkV22();String today=HomeCalendarDates.key(Calendar.getInstance());JSONObject fixture=mediaFixtureV26(c,today);fixture.getJSONObject(today).put("lunch",new JSONObject().put("text","老乡鸡"));String before=fixture.toString();
        JSONObject profile=new JSONObject().put("decoration","yellow").put("nightMode",false).put("bookFont","xiaolai").put("font",100).put("layoutLevel",50).put("foodRecognitionOnboardingSeen",true).put("seenAnnouncementVersion",c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName).put("apiEnabled",false).put("foodAutoAnalyze",false).put("foodPhotoConsent",false).put("autoCloudSync",false).put("aiAnalysisEnabled",false);
        if(!PrivateProfileStore.write(c,profile)||!prefs.edit().putString(NutritionStore.DAYS,before).remove("food_processing_preview").commit())throw new Exception("Cannot seed isolated release fixture");MainActivity screen=(MainActivity)startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));gestureScreenV20=screen;settleV15();uiAssert(()->mainFocusV21(screen));
        if("all".equals(phase)||"editor".equals(phase)){editorLayoutV060(screen,c,prefs,today,before,name);editorSaveV060(screen,c,prefs,fixture,today);}
        if("all".equals(phase)||"media".equals(phase))viewerPauseV060(screen,c,prefs,today);
        if(requests.get()!=0)throw new Exception("Release editor/media QA attempted HTTP(S)");Bundle result=new Bundle();result.putString("stream","PASS 0.6.0: phase="+phase+"; actual inline text/hint vertical layout and left origin, normal/narrow-large text, scrollable long meal, focused single editor, Done/real Back/clear merge and empty no-write; and/or paused viewer target/owner regression as selected. QA-only data, HTTP(S)=0; no all-device frame-rate or nutrition claim.\n");finish(Activity.RESULT_OK,result);
    }
    private void revisionV35(Context c,android.content.SharedPreferences prefs)throws Exception {
        String phase=args.getString("phase","all"),name=args.getString("name","beta35");if(!Arrays.asList("all","headers").contains(phase))throw new Exception("Unknown beta.35 phase: "+phase);
        FoodAnalysisJobService.cancelAll(c);FoodDraftStore.discard(c,FoodDraftStore.pendingSources(c));java.util.concurrent.atomic.AtomicInteger requests=denyNetworkV22();String today=HomeCalendarDates.key(Calendar.getInstance());JSONObject fixture=mediaFixtureV26(c,today);Calendar older=Calendar.getInstance();for(int i=0;i<2;i++){older.add(Calendar.DATE,-1);fixture.put(HomeCalendarDates.key(older),new JSONObject(fixture.getJSONObject(today).toString()));}String before=fixture.toString();
        JSONObject profile=new JSONObject().put("decoration","yellow").put("nightMode",false).put("bookFont","xiaolai").put("font",100).put("layoutLevel",50).put("layout","medium").put("notebookPageStyle","fresh").put("bookTransitionMode","flip").put("footprintLayout","expanded").put("foodRecognitionSource","api").put("foodRecognitionOnboardingSeen",true).put("seenAnnouncementVersion",c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName).put("apiEnabled",false).put("foodAutoAnalyze",false).put("foodPhotoConsent",false).put("autoCloudSync",false).put("aiAnalysisEnabled",false);
        prefs.edit().putString(NutritionStore.PROFILE,profile.toString()).putString(NutritionStore.DAYS,before).remove("food_processing_preview").commit();final MainActivity[] live={(MainActivity)startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))};gestureScreenV20=live[0];settleV15();uiAssert(()->mainFocusV21(live[0]));
        if("all".equals(phase))live[0]=uiReliabilityV34(live[0],c,prefs,fixture,before,name+"-ui");live[0]=sharedHeadersV35(live[0],c,prefs,before,name);
        if(requests.get()!=0)throw new Exception("beta.35 header QA attempted HTTP(S)");Bundle result=new Bundle();result.putString("stream","PASS beta.35: phase="+phase+"; repeated real day/night toggles; Trace, personalization, Other, Notebook and Data Security headers; local/full-root/HWUI paper and body visibility/contrast; normal/modal-close/interrupted-page color stability; "+("all".equals(phase)?"beta.34 UI and bounded-font regression; ":"")+"diary unchanged, HTTP(S)="+requests.get()+". Isolated QA; no all-device frame-rate claim.\n");finish(Activity.RESULT_OK,result);
    }
    private void revisionV34(Context c,android.content.SharedPreferences prefs)throws Exception {
        String phase=args.getString("phase","all"),name=args.getString("name","beta34");if(!Arrays.asList("all","ui","media","privacy","restore").contains(phase))throw new Exception("Unknown beta.34 phase: "+phase);
        FoodAnalysisJobService.cancelAll(c);FoodDraftStore.discard(c,FoodDraftStore.pendingSources(c));java.util.concurrent.atomic.AtomicInteger requests=denyNetworkV22();String today=HomeCalendarDates.key(Calendar.getInstance());JSONObject fixture=mediaFixtureV26(c,today);String before=fixture.toString();
        JSONObject profile=new JSONObject().put("decoration","yellow").put("nightMode",false).put("bookFont","xiaolai").put("font",100).put("layoutLevel",50).put("layout","medium").put("notebookPageStyle","fresh").put("bookTransitionMode","flip").put("footprintLayout","expanded").put("foodRecognitionSource","api").put("foodRecognitionOnboardingSeen",true).put("seenAnnouncementVersion",c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName).put("apiEnabled",false).put("foodAutoAnalyze",false).put("foodPhotoConsent",false).put("autoCloudSync",false).put("aiAnalysisEnabled",false);
        prefs.edit().putString(NutritionStore.PROFILE,profile.toString()).putString(NutritionStore.DAYS,before).remove("food_processing_preview").commit();final MainActivity[] live={(MainActivity)startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))};gestureScreenV20=live[0];settleV15();uiAssert(()->mainFocusV21(live[0]));
        if("all".equals(phase)||"ui".equals(phase))live[0]=uiReliabilityV34(live[0],c,prefs,fixture,before,name);
        if("all".equals(phase)||"media".equals(phase))mediaReliabilityV34(live[0],c,prefs,fixture,today,name);
        if("all".equals(phase)||"privacy".equals(phase))privacyReliabilityV34(live[0],c,prefs);
        if("all".equals(phase)||"restore".equals(phase))restoreReliabilityV34(live[0],c,prefs);
        if(requests.get()!=0)throw new Exception("beta.34 reliability QA attempted HTTP(S)");
        Bundle result=new Bundle();result.putString("stream","PASS beta.34: phase="+phase+"; only selected phase hooks executed, actual UI/window rendering or scoped media/privacy/restore checks as selected; HTTP(S)="+requests.get()+". QA-only data; no all-device frame-rate or real provider claim.\n");finish(Activity.RESULT_OK,result);
    }
    private void revisionV33(Context c,android.content.SharedPreferences prefs)throws Exception {
        String phase=args.getString("phase","all"),name=args.getString("name","beta33");if(!Arrays.asList("all","dialogs","returns","settings","refresh","glass").contains(phase))throw new Exception("Unknown beta.33 phase: "+phase);
        FoodAnalysisJobService.cancelAll(c);FoodDraftStore.discard(c,FoodDraftStore.pendingSources(c));java.util.concurrent.atomic.AtomicInteger requests=denyNetworkV22();String today=HomeCalendarDates.key(Calendar.getInstance());JSONObject fixture=mediaFixtureV26(c,today);fixture.getJSONObject(today).put("favorite",true);Calendar older=Calendar.getInstance();for(int i=1;i<=3;i++){older.add(Calendar.DATE,-1);fixture.put(HomeCalendarDates.key(older),new JSONObject(fixture.getJSONObject(today).toString()));}String before=fixture.toString();
        JSONObject profile=new JSONObject().put("decoration","yellow").put("nightMode","true".equals(args.getString("night"))).put("bookFont","xiaolai").put("notebookPageStyle","fresh").put("bookTransitionMode","normal").put("footprintLayout","expanded").put("favoriteRecordsExpanded",true).put("foodRecognitionSource","api").put("foodRecognitionOnboardingSeen",true).put("seenAnnouncementVersion",c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName).put("apiEnabled",false).put("foodAutoAnalyze",false).put("foodPhotoConsent",false).put("autoCloudSync",false).put("aiAnalysisEnabled",false);
        prefs.edit().putString(NutritionStore.PROFILE,profile.toString()).putString(NutritionStore.DAYS,before).remove("food_processing_preview").commit();MainActivity screen=(MainActivity)startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));gestureScreenV20=screen;settleV15();uiAssert(()->mainFocusV21(screen));
        if("all".equals(phase)||"refresh".equals(phase))detailsRefreshV33(screen,fixture,today);
        if("all".equals(phase)||"dialogs".equals(phase))dialogsV33(screen);
        if("all".equals(phase)||"returns".equals(phase)){returnFeedbackV33(screen);cachedReturnsV33(screen,today);}
        if("all".equals(phase)||"settings".equals(phase)){settingsCompositionV32(screen,name+"-spatial");settingsRoutesV33(screen,prefs);}
        if("all".equals(phase)||"glass".equals(phase))glassCaptureV33(screen);
        uiAssert(()->assertRenderPure(screen,prefs,before));if(requests.get()!=0)throw new Exception("UI-only beta.33 QA attempted HTTP(S)");Bundle result=new Bundle();result.putString("stream","PASS beta.33: phase="+phase+"; delayed details dismissal/root cache, single blur-owner windows, draw-owned/canceled returns and clean/dirty root reuse, consolidated settings routes or dirty-only glass checks as selected. Lifecycle configuration test simulates callback, not physical rotation. Diary unchanged; HTTP(S)="+requests.get()+". No brand-device frame-rate claim.\n");finish(Activity.RESULT_OK,result);
    }

    private void revisionV32(Context c,android.content.SharedPreferences prefs)throws Exception {
        String oldPhase=args.getString("phase","all");if("all".equals(oldPhase)||"settings".equals(oldPhase))throw new Exception("revision-v32 settings routes changed in beta.33; use revision-v33 settings/all. books remains available.");
        FoodAnalysisJobService.cancelAll(c);FoodDraftStore.discard(c,FoodDraftStore.pendingSources(c));java.util.concurrent.atomic.AtomicInteger requests=denyNetworkV22();
        String phase=args.getString("phase","all"),name=args.getString("name","beta32"),today=HomeCalendarDates.key(Calendar.getInstance());if(!"all".equals(phase)&&!"books".equals(phase)&&!"settings".equals(phase))throw new Exception("Unknown beta.32 phase: "+phase);
        JSONObject fixture=unifiedDays(c,today);fixture.getJSONObject(today).put("favorite",true);Calendar prior=Calendar.getInstance();for(int i=1;i<=5;i++){prior.add(Calendar.DATE,-1);fixture.put(HomeCalendarDates.key(prior),new JSONObject(fixture.getJSONObject(today).toString()).put("favorite",i<5));}String before=fixture.toString();
        JSONObject profile=new JSONObject().put("decoration","yellow").put("nightMode","true".equals(args.getString("night"))).put("bookFont","xiaolai").put("notebookPageStyle","fresh").put("bookTransitionMode","normal").put("footprintLayout","expanded").put("favoriteRecordsExpanded",false).put("foodRecognitionOnboardingSeen",true).put("seenAnnouncementVersion",c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName).put("apiEnabled",false).put("foodAutoAnalyze",false).put("foodPhotoConsent",false).put("autoCloudSync",false);
        prefs.edit().putString(NutritionStore.PROFILE,profile.toString()).putString(NutritionStore.DAYS,before).remove("food_processing_preview").commit();MainActivity screen=(MainActivity)startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));gestureScreenV20=screen;settleV15();uiAssert(()->mainFocusV21(screen));
        if("all".equals(phase)||"books".equals(phase)){freshCoverTextureV32(screen,today);notebookShaderReuseV32(screen);notebookTailPixelsV32(screen,name);bookClosingV31(screen,prefs,before,name);}
        if("all".equals(phase)||"settings".equals(phase))settingsTransitionsV32(screen,prefs,before,name);
        uiAssert(()->assertRenderPure(screen,prefs,before));if(requests.get()!=0)throw new Exception("beta.32 QA attempted HTTP(S)");Bundle result=new Bundle();result.putString("stream","PASS beta.32: phase="+phase+"; invoked opaque adjoining settings sheet geometry/pixel/capture/retarget/scroll/restore assertions, drawColor paper-art local-bounds sentinel checks, and/or actual notebook X/Back closing regressions, fresh before-first-draw cover texture bounds cached turn-shader ownership and stable viewport-clipped late-fold gutter pixels/reverse symmetry; diary unchanged, HTTP(S)="+requests.get()+". Animations="+android.animation.ValueAnimator.areAnimatorsEnabled()+" (disabled: animated timeline checks skipped). QA-only fixtures; no hardware/brand-device frame-rate or appearance claim.\n");finish(Activity.RESULT_OK,result);
    }
    private void revisionV31(Context c,android.content.SharedPreferences prefs)throws Exception {
        String oldPhase=args.getString("phase","all");if("all".equals(oldPhase)||"settings".equals(oldPhase))throw new Exception("revision-v31 settings dissolve/routes are retired; use revision-v33 settings/all. closing remains available.");
        FoodAnalysisJobService.cancelAll(c);FoodDraftStore.discard(c,FoodDraftStore.pendingSources(c));java.util.concurrent.atomic.AtomicInteger requests=denyNetworkV22();
        String phase=args.getString("phase","all"),name=args.getString("name","beta31"),today=HomeCalendarDates.key(Calendar.getInstance());if(!"all".equals(phase)&&!"closing".equals(phase)&&!"settings".equals(phase))throw new Exception("Unknown beta.31 phase: "+phase);
        JSONObject fixture=unifiedDays(c,today);fixture.getJSONObject(today).put("favorite",true);Calendar prior=Calendar.getInstance();for(int i=1;i<=5;i++){prior.add(Calendar.DATE,-1);fixture.put(HomeCalendarDates.key(prior),new JSONObject(fixture.getJSONObject(today).toString()).put("favorite",i<5));}String before=fixture.toString();
        JSONObject profile=new JSONObject().put("decoration","yellow").put("nightMode","true".equals(args.getString("night"))).put("bookFont","xiaolai").put("notebookPageStyle","fresh").put("bookTransitionMode","normal").put("footprintLayout","expanded").put("favoriteRecordsExpanded",false).put("foodRecognitionOnboardingSeen",true).put("seenAnnouncementVersion",c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName).put("apiEnabled",false).put("foodAutoAnalyze",false).put("foodPhotoConsent",false).put("autoCloudSync",false);
        prefs.edit().putString(NutritionStore.PROFILE,profile.toString()).putString(NutritionStore.DAYS,before).remove("food_processing_preview").commit();MainActivity screen=(MainActivity)startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));gestureScreenV20=screen;settleV15();uiAssert(()->mainFocusV21(screen));
        if("all".equals(phase)||"closing".equals(phase))bookClosingV31(screen,prefs,before,name);
        if("all".equals(phase)||"settings".equals(phase))settingsTransitionsV31(screen,prefs,before,name);
        uiAssert(()->assertRenderPure(screen,prefs,before));if(requests.get()!=0)throw new Exception("beta.31 QA attempted HTTP(S)");Bundle result=new Bundle();result.putString("stream","PASS beta.31: phase="+phase+"; executed real notebook X/Back paths and/or shared settings transition assertions; paused timeline, geometry, texture ownership, destination/background restoration, interrupt cleanup; closing phase includes synchronous input during pending inner-page settle and a simulated configuration callback (no device rotation); settings phase includes native attached-host.draw vs interrupted-capture composition pixels across multiple progress values and retargeting; opaque incoming roots, dissolving old scenes; diary unchanged, HTTP(S)="+requests.get()+". Animations="+android.animation.ValueAnimator.areAnimatorsEnabled()+" (disabled: animated timeline checks skipped). No hardware/brand-device frame-rate or appearance claim.\n");finish(Activity.RESULT_OK,result);
    }
    private void revisionV30(Context c,android.content.SharedPreferences prefs)throws Exception {
        FoodAnalysisJobService.cancelAll(c);FoodDraftStore.discard(c,FoodDraftStore.pendingSources(c));java.util.concurrent.atomic.AtomicInteger requests=denyNetworkV22();
        String phase=args.getString("phase","all"),name=args.getString("name","beta30"),today=HomeCalendarDates.key(Calendar.getInstance());
        if(!"all".equals(phase)&&!"corners".equals(phase))throw new Exception("Unknown beta.30 phase: "+phase);
        JSONObject fixture=unifiedDays(c,today);Calendar prior=Calendar.getInstance();for(int i=1;i<=3;i++){prior.add(Calendar.DATE,-1);fixture.put(HomeCalendarDates.key(prior),new JSONObject(fixture.getJSONObject(today).toString()));}String before=fixture.toString();
        JSONObject profile=new JSONObject().put("decoration","yellow").put("nightMode","true".equals(args.getString("night"))).put("bookFont","xiaolai").put("notebookPageStyle","fresh").put("bookTransitionMode","normal").put("footprintLayout","expanded")
            .put("foodRecognitionOnboardingSeen",true).put("seenAnnouncementVersion",c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName).put("apiEnabled",false).put("foodAutoAnalyze",false).put("foodPhotoConsent",false).put("autoCloudSync",false);
        prefs.edit().putString(NutritionStore.PROFILE,profile.toString()).putString(NutritionStore.DAYS,before).remove("food_processing_preview").commit();
        MainActivity screen=(MainActivity)startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));gestureScreenV20=screen;settleV15();uiAssert(()->mainFocusV21(screen));
        boolean animated=android.animation.ValueAnimator.areAnimatorsEnabled();
        for(String mode:new String[]{"normal","flip"})for(String style:new String[]{"food","checker","nightstar","clear"}){
            uiAssert(()->{((JSONObject)field(screen,"profile")).put("bookTransitionMode",mode).put("bookCoverStyle",style);invoke(screen,"showTab",new Class[]{int.class,boolean.class},1,false);});settleV15();
            uiAssert(()->{View cover=tagged(screen.getWindow().getDecorView(),"footprint_plain_cover").get(0);android.graphics.Outline outline=new android.graphics.Outline();cover.getBackground().getOutline(outline);if(outline.isEmpty()||outline.getRadius()<=0||outline.getAlpha()<.99f)throw new Exception("Source cover shadow still uses an unrounded/transparent outline");});
            bookCardTapV25(screen);
            if(!animated){settleV15();uiAssert(()->{View frame=(View)field(screen,"bookDetailSwipeFrame"),paper=frame.findViewWithTag("book_detail_notebook_page");if(field(screen,"bookOpeningOverlay")!=null||paper.getAlpha()!=1||!paper.getClipToOutline())throw new Exception("No-animation path lost its rounded live paper");assertRenderPure(screen,prefs,before);});continue;}
            final Object[] overlay={null};final android.animation.ValueAnimator[] animator={null};final View[] paper={null};final Bitmap[] page={null},cover={null};final float[] elevation={0},z={0};
            long limit=android.os.SystemClock.elapsedRealtime()+3000;do{uiAssert(()->{Object current=field(screen,"bookOpeningOverlay");if(current==null)return;Object value=ownField(current,"animator");if(!(value instanceof android.animation.ValueAnimator))return;overlay[0]=current;animator[0]=(android.animation.ValueAnimator)value;animator[0].pause();animator[0].setCurrentPlayTime(0);paper[0]=(View)ownField(current,"paper");page[0]=(Bitmap)ownField(current,"pageImage");cover[0]=(Bitmap)ownField(current,"coverImage");elevation[0]=((Number)ownField(current,"paperElevation")).floatValue();z[0]=((Number)ownField(current,"paperTranslationZ")).floatValue();});if(overlay[0]!=null)break;Thread.sleep(5);}while(android.os.SystemClock.elapsedRealtime()<limit);
            if(overlay[0]==null)throw new Exception("Corner QA never observed a real cover animation: "+style+"/"+mode);
            try{
                uiAssert(()->{bookCornerPixelsV30(overlay[0],animator[0],paper[0],name+"-"+style+"-"+mode);assertRenderPure(screen,prefs,before);});
                uiAssert(()->animator[0].resume());settleV15();
                uiAssert(()->{if(field(screen,"bookOpeningOverlay")!=null||paper[0].getAlpha()!=1||paper[0].getElevation()!=elevation[0]||paper[0].getTranslationZ()!=z[0]||!page[0].isRecycled()||!cover[0].isRecycled())throw new Exception("Opening did not restore rounded live paper/shadow or release textures");assertRenderPure(screen,prefs,before);});
            }finally{uiAssert(()->{if(animator[0]!=null&&animator[0].isPaused())animator[0].resume();});}
        }
        if("all".equals(phase))bookOpeningTimelineV29(screen,prefs,before,name+"-timing-cleanup");
        uiAssert(()->assertRenderPure(screen,prefs,before));if(requests.get()!=0)throw new Exception("Corner-only QA attempted HTTP(S)");
        Bundle result=new Bundle();result.putString("stream","PASS beta.30: phase="+phase+"; actual isolated Canvas overlay, cover-only vs high-contrast-paper four-corner oracle at 0/100/200ms; real-paper alpha mask vs shadow toggle at 550/850ms; four cover styles x normal/flip, rounded native outlines, hidden Z suppression/live restoration, same mutable textures and diary unchanged. HTTP(S)="+requests.get()+". Animations="+animated+" (disabled: isolated animated pixels skipped); no hardware/brand-device appearance claim.\n");finish(Activity.RESULT_OK,result);
    }
    private void revisionV29(Context c,android.content.SharedPreferences prefs)throws Exception {
        FoodAnalysisJobService.cancelAll(c);FoodDraftStore.discard(c,FoodDraftStore.pendingSources(c));java.util.concurrent.atomic.AtomicInteger requests=denyNetworkV22();
        String phase=args.getString("phase","all"),name=args.getString("name","beta29"),today=HomeCalendarDates.key(Calendar.getInstance());
        if(!"all".equals(phase)&&!"navigation".equals(phase)&&!"opening".equals(phase))throw new Exception("Unknown beta.29 phase: "+phase);
        JSONObject fixture=unifiedDays(c,today);Calendar previous=Calendar.getInstance();for(int i=1;i<=89;i++){previous.add(Calendar.DATE,-1);fixture.put(HomeCalendarDates.key(previous),new JSONObject(fixture.getJSONObject(today).toString()));}String before=fixture.toString();
        JSONObject profile=new JSONObject().put("decoration","yellow").put("nightMode","true".equals(args.getString("night"))).put("bookFont","xiaolai").put("notebookPageStyle","fresh").put("bookTransitionMode","normal").put("footprintLayout","expanded")
            .put("foodRecognitionOnboardingSeen",true).put("seenAnnouncementVersion",c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName).put("apiEnabled",false).put("foodAutoAnalyze",false).put("foodPhotoConsent",false).put("autoCloudSync",false);
        prefs.edit().putString(NutritionStore.PROFILE,profile.toString()).putString(NutritionStore.DAYS,before).remove("food_processing_preview").commit();
        MainActivity screen=(MainActivity)startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));gestureScreenV20=screen;settleV15();
        final RootPagePager[] captured={null};final View[] pages=new View[3];uiAssert(()->{mainFocusV21(screen);captured[0]=(RootPagePager)field(screen,"rootPager");if(captured[0]==null)throw new Exception("No retained root pager at cold start");for(int i=0;i<3;i++)pages[i]=captured[0].page(i);rootRestV28(screen,captured[0],pages,0);});RootPagePager pager=captured[0];
        if(!"opening".equals(phase)){
            RootFramesV28 frames=new RootFramesV28(screen,pager,pages);uiAssert(()->Choreographer.getInstance().postFrameCallback(frames));
            try{silentRootBoundariesV29(screen,pager,pages,prefs,before);rootNavigationV28(screen,pager,pages,prefs,before,name);if("all".equals(phase))hiddenTodayOwnerV28(screen,pager,pages,prefs,before,today);}
            finally{uiAssert(()->{frames.running=false;Choreographer.getInstance().removeFrameCallback(frames);});File dir=new File(getTargetContext().getExternalFilesDir(null),"qa");dir.mkdirs();try(OutputStream out=new FileOutputStream(new File(dir,name+"-root-silent-progress.csv"))){out.write(frames.trace.toString().getBytes("UTF-8"));}}
            if(frames.failure!=null)throw new Exception("Silent-boundary root frame sampler failed",frames.failure);
            if(android.animation.ValueAnimator.areAnimatorsEnabled()&&frames.movingFrames<10)throw new Exception("Too few actual legal root-transition frames: "+frames.movingFrames);
        }
        // Opening intentionally replaces the root hierarchy; its frames must not be sampled as cache identity failures.
        if("all".equals(phase)||"opening".equals(phase))bookOpeningTimelineV29(screen,prefs,before,name);
        uiAssert(()->assertRenderPure(screen,prefs,before));if(requests.get()!=0)throw new Exception("Beta.29 UI QA attempted HTTP(S)");
        Bundle result=new Bundle();String coverage="navigation".equals(phase)?"silent Today-right/Me-left boundaries plus retained-root navigation (opening phase skipped)":"opening".equals(phase)?"real normal/flip cover taps, 1000ms wall timeline, 150/300/550/850ms growth/front/back geometry, same textures and finish/tab/close cleanup (root navigation phase skipped)":"silent stationary boundaries, retained-root navigation and hidden Today owner; real normal/flip 1000ms cover growth/front/back milestones and cleanup";
        result.putString("stream","PASS beta.29: phase="+phase+"; "+coverage+"; diary unchanged, HTTP(S)="+requests.get()+", window pointer events="+localPointerFallbacksV20+". Animations="+android.animation.ValueAnimator.areAnimatorsEnabled()+" (disabled: intermediate cover milestones skipped); no device frame-rate/brand-device fluidity claim.\n");finish(Activity.RESULT_OK,result);
    }
    private void revisionV28(Context c,android.content.SharedPreferences prefs)throws Exception {
        FoodAnalysisJobService.cancelAll(c);FoodDraftStore.discard(c,FoodDraftStore.pendingSources(c));java.util.concurrent.atomic.AtomicInteger requests=denyNetworkV22();
        String phase=args.getString("phase","all"),name=args.getString("name","beta28"),today=HomeCalendarDates.key(Calendar.getInstance());
        if(!"all".equals(phase)&&!"navigation".equals(phase))throw new Exception("Unknown beta.28 phase: "+phase);
        JSONObject fixture=unifiedDays(c,today);Calendar previous=Calendar.getInstance();for(int i=1;i<=89;i++){previous.add(Calendar.DATE,-1);fixture.put(HomeCalendarDates.key(previous),new JSONObject(fixture.getJSONObject(today).toString()));}String before=fixture.toString();
        JSONObject profile=new JSONObject().put("decoration","yellow").put("nightMode","true".equals(args.getString("night"))).put("bookFont","xiaolai").put("notebookPageStyle","fresh").put("bookTransitionMode","flip").put("footprintLayout","expanded")
            .put("foodRecognitionOnboardingSeen",true).put("seenAnnouncementVersion",c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName).put("apiEnabled",false).put("foodAutoAnalyze",false).put("foodPhotoConsent",false).put("autoCloudSync",false);
        prefs.edit().putString(NutritionStore.PROFILE,profile.toString()).putString(NutritionStore.DAYS,before).remove("food_processing_preview").commit();
        MainActivity screen=(MainActivity)startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));gestureScreenV20=screen;settleV15();
        final RootPagePager[] captured={null};final View[] pages=new View[3];uiAssert(()->{mainFocusV21(screen);captured[0]=(RootPagePager)field(screen,"rootPager");if(captured[0]==null)throw new Exception("No retained root pager at cold start");for(int i=0;i<3;i++)pages[i]=captured[0].page(i);rootRestV28(screen,captured[0],pages,0);});
        RootPagePager pager=captured[0];RootFramesV28 frames=new RootFramesV28(screen,pager,pages);uiAssert(()->Choreographer.getInstance().postFrameCallback(frames));
        try{rootNavigationV28(screen,pager,pages,prefs,before,name);if("all".equals(phase))hiddenTodayOwnerV28(screen,pager,pages,prefs,before,today);}
        finally{uiAssert(()->{frames.running=false;Choreographer.getInstance().removeFrameCallback(frames);});File dir=new File(getTargetContext().getExternalFilesDir(null),"qa");dir.mkdirs();try(OutputStream out=new FileOutputStream(new File(dir,name+"-root-retained-progress.csv"))){out.write(frames.trace.toString().getBytes("UTF-8"));}}
        if(frames.failure!=null)throw new Exception("Retained root frame sampler failed",frames.failure);
        if(android.animation.ValueAnimator.areAnimatorsEnabled()&&frames.movingFrames<10)throw new Exception("Too few real intermediate retained-root frames: "+frames.movingFrames);
        uiAssert(()->assertRenderPure(screen,prefs,before));if(requests.get()!=0)throw new Exception("Root-only QA attempted HTTP(S)");
        Bundle result=new Bundle();result.putString("stream","PASS beta.28: phase="+phase+"; real MainActivity-window root gestures 0/45/80ms both directions, rollback/reversal/alternation/coalesced UP/cancel/multipointer/boundaries/tap-to-swipe and rapid nav taps; same three prebuilt page identities; frame-synced root/indicator transforms, no root PageCrossfade; "+("all".equals(phase)?"hidden Today date owner pause/resume; ":"hidden-owner phase skipped; ")+"90-day local fixture, diary unchanged, HTTP(S)="+requests.get()+", window pointer events="+localPointerFallbacksV20+". Animations="+android.animation.ValueAnimator.areAnimatorsEnabled()+"; no device frame-rate or brand-device fluidity claim.\n");finish(Activity.RESULT_OK,result);
    }
    private void revisionV26(Context c,android.content.SharedPreferences prefs)throws Exception {
        String oldPhase=args.getString("phase","all");if("all".equals(oldPhase)||"navigation".equals(oldPhase))throw new Exception("revision-v26/v27 navigation is retired; use revision-v28 navigation. startup/media/books remain available.");
        FoodAnalysisJobService.cancelAll(c);FoodDraftStore.discard(c,FoodDraftStore.pendingSources(c));java.util.concurrent.atomic.AtomicInteger requests=denyNetworkV22();
        String revision="revision-v27".equals(args.getString("scenario"))?"beta.27":"beta.26";
        String phase=args.getString("phase","all"),name=args.getString("name",revision),today=HomeCalendarDates.key(Calendar.getInstance());if(!"all".equals(phase)&&!"navigation".equals(phase)&&!"media".equals(phase)&&!"startup".equals(phase)&&!"books".equals(phase))throw new Exception("Unknown "+revision+" QA phase: "+phase);
        JSONObject fixture=mediaFixtureV26(c,today);Calendar day=Calendar.getInstance();for(int i=1;i<=7;i++){day.add(Calendar.DATE,-1);fixture.put(HomeCalendarDates.key(day),new JSONObject(fixture.getJSONObject(today).toString()));}String before=fixture.toString();
        JSONObject profile=new JSONObject().put("decoration","yellow").put("nightMode","true".equals(args.getString("night"))).put("bookFont","xiaolai").put("notebookPageStyle","fresh").put("bookStoryLayout","summary").put("bookStickerLayout","straight").put("bookTransitionMode","flip").put("footprintLayout","expanded")
            .put("foodRecognitionOnboardingSeen",true).put("seenAnnouncementVersion",c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName).put("apiEnabled",false).put("foodAutoAnalyze",false).put("foodPhotoConsent",false).put("autoCloudSync",false);
        prefs.edit().putString(NutritionStore.PROFILE,profile.toString()).putString(NutritionStore.DAYS,before).remove("food_processing_preview").commit();MainActivity screen=(MainActivity)startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));gestureScreenV20=screen;settleV15();uiAssert(()->mainFocusV21(screen));
        // Validate the actual cold-start presentation BEFORE showTab or any gesture repairs it.
        uiAssert(()->rootAtRestV26(screen,0));
        if("all".equals(phase)||"navigation".equals(phase))navigationV26(screen,prefs,before,name);
        if("all".equals(phase)||"media".equals(phase))notebookMediaV26(screen,prefs,before,name);
        if("all".equals(phase)||"books".equals(phase))continuousBooksV27(screen,prefs,before);
        uiAssert(()->assertRenderPure(screen,prefs,before));if(requests.get()!=0)throw new Exception("UI-only beta.26 QA attempted HTTP(S)");Bundle result=new Bundle();
        String coverage="all".equals(phase)?"continuous two-page root gestures 0/45/80ms both directions; frame-by-frame selection/sheet progress; grab/reverse/cancel/multipointer/boundaries/rapid clicks; real cold-delayed and warm six-subject notebook opening, same texture repaint and HWUI presented pixels, incoming curl late-media repaint/cancel, settled live drawables/cleanup":"navigation".equals(phase)?"continuous two-page root gestures 0/45/80ms both directions, frame-by-frame selection/sheet progress, grab/reverse/cancel/multipointer/boundaries/rapid clicks (media phase skipped)":"real cold-delayed and warm six-subject notebook opening, same texture repaint and HWUI presented pixels, incoming curl late-media repaint/cancel, settled live drawables/cleanup (navigation phase skipped)";
        if("startup".equals(phase))coverage="cold-start root and visible measured bottom selection without prior navigation";
        if("books".equals(phase))coverage="four consecutive notebook turns in both directions, flip/slide and 0/45ms gaps, coalesced UP endpoints and resource/input cleanup";
        else if("revision-v27".equals(args.getString("scenario"))&&"all".equals(phase))coverage+="; four consecutive notebook turns both directions in flip/slide modes";
        result.putString("stream","PASS "+revision+": phase="+phase+"; cold-start bottom selection; "+coverage+". System animations="+android.animation.ValueAnimator.areAnimatorsEnabled()+" (disabled: intermediate frames/overlay checks skipped); diary unchanged, HTTP(S)="+requests.get()+", MainActivity-window pointer events="+localPointerFallbacksV20+". Colored local QA media, no model/brand-device fluidity claim.\n");finish(Activity.RESULT_OK,result);
    }
    private void revisionV25(Context c,android.content.SharedPreferences prefs)throws Exception {
        String oldPhase=args.getString("phase","all");if("all".equals(oldPhase)||"navigation".equals(oldPhase))throw new Exception("revision-v25 navigation is retired; use revision-v28 navigation. books remains available.");
        FoodAnalysisJobService.cancelAll(c);FoodDraftStore.discard(c,FoodDraftStore.pendingSources(c));java.util.concurrent.atomic.AtomicInteger requests=denyNetworkV22();
        String today=HomeCalendarDates.key(Calendar.getInstance()),name=args.getString("name","beta25"),phase=args.getString("phase","all");JSONObject fixture=unifiedDays(c,today);Calendar day=Calendar.getInstance();
        if(!"all".equals(phase)&&!"navigation".equals(phase)&&!"books".equals(phase))throw new Exception("Unknown beta.25 QA phase: "+phase);
        for(int i=1;i<=3;i++){day.add(Calendar.DATE,-1);fixture.put(HomeCalendarDates.key(day),new JSONObject(fixture.getJSONObject(today).toString()));}
        String before=fixture.toString();JSONObject profile=new JSONObject().put("decoration","yellow").put("nightMode","true".equals(args.getString("night"))).put("bookFont","xiaolai").put("notebookPageStyle","fresh").put("bookTransitionMode","flip").put("footprintLayout","expanded")
            .put("foodRecognitionOnboardingSeen",true).put("seenAnnouncementVersion",c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName).put("apiEnabled",false).put("foodAutoAnalyze",false).put("foodPhotoConsent",false).put("autoCloudSync",false);
        prefs.edit().putString(NutritionStore.PROFILE,profile.toString()).putString(NutritionStore.DAYS,before).remove("food_processing_preview").commit();
        MainActivity screen=(MainActivity)startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));gestureScreenV20=screen;settleV15();uiAssert(()->mainFocusV21(screen));
        if("all".equals(phase)||"navigation".equals(phase)){
            rapidNavigationV24(screen);
            for(int i=0;i<5;i++){bottomTabV24(screen,1);Thread.sleep(45);bottomTabV24(screen,0);Thread.sleep(45);nonBlankContentV24(screen,"v25-footprints-today-"+i);}
            settleV15();uiAssert(()->{assertRenderPure(screen,prefs,before);PageCrossfade crossfade=(PageCrossfade)field(screen,"pageCrossfade");if(((Integer)field(screen,"currentTab"))!=0||crossfade!=null&&crossfade.active())throw new Exception("Footprints to today did not settle");});
        }
        if("all".equals(phase)||"books".equals(phase)){
            notebookEdgeGeometryV25(screen);notebookOpeningV25(screen,prefs,before,name);
            uiAssert(()->invoke(screen,"renderBookDetail",new Class[]{String.class,boolean.class},today,false));settleV15();notebookGesturesV20(screen,c,prefs,before,name);
        }
        uiAssert(()->assertRenderPure(screen,prefs,before));if(requests.get()!=0)throw new Exception("Motion QA attempted HTTP(S)");Bundle result=new Bundle();
        String coverage="all".equals(phase)?"full-width fast tab/reversal coverage, repeated footprints-to-today, six paper-back edge extents, real cover taps/early-middle-rest frames/open interruption/close cleanup, prior notebook cancel/reverse/grab/multipointer lifecycle":"navigation".equals(phase)?"full-width fast tab/reversal coverage, repeated footprints-to-today (book phases skipped)":"six paper-back edge extents, real cover taps/early-middle-rest frames/open interruption/close cleanup, prior notebook cancel/reverse/grab/multipointer lifecycle (navigation phase skipped)";
        result.putString("stream","PASS beta.25: phase="+phase+"; "+coverage+", unchanged diary. Pointer events="+localPointerFallbacksV20+", HTTP(S)="+requests.get()+". No brand-device frame-rate claim.\n");finish(Activity.RESULT_OK,result);
    }
    private void revisionV24(Context c,android.content.SharedPreferences prefs)throws Exception{
        String oldPhase=args.getString("phase","all");if(!Arrays.asList("all","navigation","dates","text").contains(oldPhase))throw new Exception("Unknown beta.24 phase: "+oldPhase);if("all".equals(oldPhase)||"navigation".equals(oldPhase))throw new Exception("revision-v24 navigation is retired; use revision-v28 navigation. dates/text phases remain available.");
        FoodAnalysisJobService.cancelAll(c);FoodDraftStore.discard(c,FoodDraftStore.pendingSources(c));java.util.concurrent.atomic.AtomicInteger requests=denyNetworkV22();
        String today=HomeCalendarDates.key(Calendar.getInstance());JSONObject days=unifiedDays(c,today);days.put("1999-11-03",new JSONObject().put("breakfast",new JSONObject().put("text","远日早餐文字").put("manualTextEntry",true)));
        JSONObject profile=new JSONObject().put("decoration","yellow").put("nightMode","true".equals(args.getString("night"))).put("foodRecognitionOnboardingSeen",true)
            .put("seenAnnouncementVersion",c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName).put("apiEnabled",false).put("foodAutoAnalyze",false).put("foodPhotoConsent",false).put("autoCloudSync",false).put("footprintLayout","expanded");
        prefs.edit().putString(NutritionStore.PROFILE,profile.toString()).putString(NutritionStore.DAYS,days.toString()).remove("food_processing_preview").commit();
        MainActivity screen=(MainActivity)startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));gestureScreenV20=screen;settleV15();uiAssert(()->mainFocusV21(screen));
        String phase=args.getString("phase","all");
        if("all".equals(phase)||"navigation".equals(phase))rapidNavigationV24(screen);
        if("all".equals(phase)||"dates".equals(phase))continuousDatesV24(screen,prefs);
        if("all".equals(phase)||"text".equals(phase))textDeleteV24(screen,c,prefs,today);
        if(requests.get()!=0)throw new Exception("UI-only revisions attempted HTTP(S)");Bundle result=new Bundle();
        String coverage="all".equals(phase)?"rapid 0/16/40/100ms bottom tab sequences with nonblank content, stale preDraw generation guard and failed-capture fallback, gallery gentle transition; continuous three/four dates, immediate reversal/cancel, far-tap to swipes and future recovery; centered text selection/delete/cancel/stale ownership/fresh media and nutrients preserved":"targeted phase="+phase+" (other phases skipped)";
        result.putString("stream","PASS beta.24: "+coverage+". Pointer events="+localPointerFallbacksV20+", HTTP(S)="+requests.get()+".\n");finish(Activity.RESULT_OK,result);
    }
    private void importCutoutV24(Context c,android.content.SharedPreferences prefs)throws Exception{
        FoodAnalysisJobService.cancelAll(c);FoodDraftStore.discard(c,FoodDraftStore.pendingSources(c));java.util.concurrent.atomic.AtomicInteger requests=denyNetworkV22();
        String today=HomeCalendarDates.key(Calendar.getInstance());JSONObject profile=new JSONObject().put("foodRecognitionOnboardingSeen",true).put("seenAnnouncementVersion",c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName)
            .put("apiEnabled",false).put("foodAutoAnalyze",false).put("foodPhotoConsent",false).put("autoCloudSync",false);
        prefs.edit().putString(NutritionStore.PROFILE,profile.toString()).putString(NutritionStore.DAYS,"{}").remove("food_processing_preview").commit();
        MainActivity screen=(MainActivity)startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));gestureScreenV20=screen;settleV15();
        android.net.Uri input=MealCameraProvider.createOutputUri(c);try(InputStream original=c.getAssets().open("qa/food.jpg");OutputStream output=c.getContentResolver().openOutputStream(input,"w")){byte[] buffer=new byte[16384];int count;while((count=original.read(buffer))!=-1)output.write(buffer,0,count);}
        long begin=android.os.SystemClock.elapsedRealtime();uiAssert(()->{setMainField(screen,"pendingPhotoDate",today);setMainField(screen,"pendingPhotoMeal","lunch");invoke(screen,"importSelectedMealMediaAsync",new Class[]{List.class,boolean.class},Collections.singletonList(input),false);});
        String source="";JSONObject item=null;long deadline=android.os.SystemClock.elapsedRealtime()+120000;
        while(android.os.SystemClock.elapsedRealtime()<deadline){List<String> sources=FoodDraftStore.pendingSources(c);if(!sources.isEmpty()){source=sources.get(0);item=FoodDraftStore.item(c,source);if(item!=null&&!item.optString("cutoutFile").isEmpty()&&"awaiting_consent".equals(item.optString("analysisStatus")))break;}Thread.sleep(250);}
        if(item==null||!item.optString("cutoutModel").contains("主体聚焦"))throw new Exception("Real import/worker did not produce focused cutout: "+(item==null?"no draft":item.optString("cutoutModel")+" "+item.optString("analysisStatus")));
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(new File(c.getFilesDir(),source).getPath(),bounds);
        if(bounds.outWidth!=1199||bounds.outHeight!=1600)throw new Exception("Actual imported JPEG still lost half its pixels: "+bounds.outWidth+"x"+bounds.outHeight);
        Bitmap cutout=BitmapFactory.decodeFile(new File(c.getFilesDir(),item.optString("cutoutFile")).getPath());JSONArray box=item.getJSONArray("cutoutRect");Rect rect=new Rect(box.getInt(0),box.getInt(1),box.getInt(2),box.getInt(3));int w=Math.round(bounds.outWidth*1024f/bounds.outHeight),h=1024;
        try{for(float[] point:new float[][]{{.5f,.43f},{.5f,.65f},{.5f,.295f},{.24f,.47f},{.5f,.704f},{.13f,.51f}})if(cutoutAlphaV23(cutout,rect,w,h,point[0],point[1])<180)throw new Exception("Import pipeline lost bowl/food/spoon at "+Arrays.toString(point));
            for(float[] point:new float[][]{{.125f,.39f},{.30f,.95f},{.5f,.1f}})if(cutoutAlphaV23(cutout,rect,w,h,point[0],point[1])>15)throw new Exception("Import pipeline kept tray/person/table at "+Arrays.toString(point));
            File dir=new File(c.getExternalFilesDir(null),"qa");dir.mkdirs();try(OutputStream out=new FileOutputStream(new File(dir,"beta24-import-cutout.png"))){cutout.compress(Bitmap.CompressFormat.PNG,100,out);}
            java.nio.file.Files.copy(new File(c.getFilesDir(),source).toPath(),new File(dir,"beta24-import-source.jpg").toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }finally{cutout.recycle();}
        if(!"{}".equals(prefs.getString(NutritionStore.DAYS,"{}"))||requests.get()!=0)throw new Exception("Draft import wrote diary or made HTTP(S) without consent");
        final String accepted=source;uiAssert(()->{Object dialog=field(screen,"foodProcessing");if(dialog instanceof FoodProcessingDialog)((FoodProcessingDialog)dialog).dismiss();});FoodDraftStore.discard(c,Collections.singletonList(accepted));FoodAnalysisJobService.cancelAll(c);
        Bundle result=new Bundle();result.putString("stream","PASS: actual controlled URI -> Main import decoder -> 1199x1600 JPEG92 -> persistent draft/job worker -> real JNI focused cutout; six subject/three background alpha landmarks; no diary commit or HTTP(S), elapsed="+(android.os.SystemClock.elapsedRealtime()-begin)+"ms on ARM bridge, not phone speed/accuracy.\n");finish(Activity.RESULT_OK,result);
    }
    private int cutoutAlphaV23(Bitmap cutout,Rect bounds,int width,int height,float x,float y){
        int px=Math.round(width*x)-bounds.left,py=Math.round(height*y)-bounds.top;
        return px<0||py<0||px>=cutout.getWidth()||py>=cutout.getHeight()?0:Color.alpha(cutout.getPixel(px,py));
    }
    private void cutoutV23(Context c)throws Exception {
        java.util.concurrent.atomic.AtomicInteger requests=denyNetworkV22();
        Bitmap source;try(InputStream in=c.getAssets().open("qa/food.jpg")){source=BitmapFactory.decodeStream(in);}
        if(source==null)throw new Exception("Missing real noodle photo QA fixture");
        long begin=android.os.SystemClock.elapsedRealtime();Bitmap output=FoodCutoutEngine.create(c,source);long elapsed=android.os.SystemClock.elapsedRealtime()-begin;
        try{
            Rect bounds=FoodCutoutEngine.lastBounds();if(bounds==null||!FoodCutoutEngine.lastModel().contains("主体聚焦"))throw new Exception("Real JNI did not exercise focused subject refinement: "+FoodCutoutEngine.lastModel());
            float ratio=Math.min(1,1024f/Math.max(source.getWidth(),source.getHeight()));int width=Math.round(source.getWidth()*ratio),height=Math.round(source.getHeight()*ratio);
            float[][] subject={{.50f,.43f},{.50f,.65f},{.50f,.295f},{.24f,.47f},{.50f,.704f},{.13f,.51f}};
            for(float[] point:subject)if(cutoutAlphaV23(output,bounds,width,height,point[0],point[1])<180)throw new Exception("Food/bowl/spoon lost at "+Arrays.toString(point));
            for(float[] point:new float[][]{{.125f,.39f},{.30f,.95f},{.5f,.1f}})if(cutoutAlphaV23(output,bounds,width,height,point[0],point[1])>15)throw new Exception("Tray/person/background remained at "+Arrays.toString(point));
            File dir=new File(c.getExternalFilesDir(null),"qa");dir.mkdirs();try(OutputStream out=new FileOutputStream(new File(dir,"beta23-noodle-cutout.png"))){output.compress(Bitmap.CompressFormat.PNG,100,out);}
            if(requests.get()!=0)throw new Exception("Local cutout attempted network");Bundle result=new Bundle();result.putString("stream","PASS: actual JNI on user noodle photo, focused refinement active, six food/bowl/spoon alpha landmarks retained, tray/person/table landmarks transparent. Elapsed="+elapsed+"ms on ARM-bridge emulator, not phone speed or general accuracy; HTTP(S)="+requests.get()+".\n");finish(Activity.RESULT_OK,result);
        }finally{output.recycle();source.recycle();}
    }
    private String hierarchyV23(View view){
        StringBuilder result=new StringBuilder();View child=view;
        while(child!=null){ViewParent parent=child.getParent();result.append(child.getClass().getSimpleName()).append(':').append(child.isAttachedToWindow()).append('/').append(parent instanceof ViewGroup?((ViewGroup)parent).indexOfChild(child):-2).append(" -> ");child=parent instanceof View?(View)parent:null;}
        return result.toString();
    }
    private void revisionV23(Context c,android.content.SharedPreferences prefs)throws Exception {
        FoodAnalysisJobService.cancelAll(c);FoodDraftStore.discard(c,FoodDraftStore.pendingSources(c));
        java.util.concurrent.atomic.AtomicInteger requests=denyNetworkV22();
        String today=HomeCalendarDates.key(Calendar.getInstance()),name=args.getString("name","beta23");
        JSONObject profile=new JSONObject().put("decoration","yellow").put("nightMode","true".equals(args.getString("night")))
            .put("foodRecognitionOnboardingSeen",true).put("seenAnnouncementVersion",c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName)
            .put("autoCloudSync",false).put("apiEnabled",false).put("foodAutoAnalyze",false).put("foodPhotoConsent",false).put("footprintLayout","expanded");
        JSONObject fixture=unifiedDays(c,today);
        prefs.edit().putString(NutritionStore.PROFILE,profile.toString()).putString(NutritionStore.DAYS,fixture.toString()).remove("food_processing_preview").commit();
        MainActivity screen=(MainActivity)startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));gestureScreenV20=screen;settleV15();

        // Real calendar click callbacks must keep the existing paper/calendar instances alive.
        final TodayDayPager[] paper={null};final HomeCalendarView[] calendar={null};
        uiAssert(()->{setMainField(screen,"homeDateKey","2000-03-08");setMainField(screen,"homeCalendarExpanded",false);invoke(screen,"showTab",new Class[]{int.class,boolean.class},0,false);});settleV15();
        String before=prefs.getString(NutritionStore.DAYS,"{}");
        uiAssert(()->{paper[0]=pagerV15(screen);calendar[0]=(HomeCalendarView)screen.getWindow().getDecorView().findViewWithTag("home_calendar");if(!TodayDayPager.isLiveHierarchy(paper[0]))throw new Exception("Current paper hierarchy rejected: "+hierarchyV23(paper[0]));View cell=calendar[0].findViewWithTag("today_week_day_2000-03-07");if(cell==null||!cell.performClick())throw new Exception("Week date is not clickable");});
        Thread.sleep(85);
        uiAssert(()->{if(paper[0]!=pagerV15(screen)||calendar[0]!=screen.getWindow().getDecorView().findViewWithTag("home_calendar"))throw new Exception("Date click rebuilt the whole screen");
            if(android.animation.ValueAnimator.areAnimatorsEnabled()){
                float moved=offsetV15(paper[0]);if(moved<=PaperComponents.dp(c,6)||moved>=paper[0].getWidth()+PaperComponents.dp(c,24))throw new Exception("Week click has no intermediate paper displacement: offset="+moved+",selection="+(ownField(paper[0],"selection")!=null)+",live="+TodayDayPager.isLiveHierarchy(paper[0])+",interacting="+paper[0].isInteracting()+",tree="+hierarchyV23(paper[0]));
                if(((Number)ownField(calendar[0],"linkedProgress")).floatValue()<=0)throw new Exception("Date marker did not follow the clicked paper");
            }
        });screenshotNowV15(name+"-date-click-mid");settleV15();
        uiAssert(()->{if(!"2000-03-07".equals(HomeCalendarDates.key(paper[0].currentDate())))throw new Exception("Week click did not commit target");calendar[0].setExpanded(true,false);});settleV15();
        uiAssert(()->{View cell=calendar[0].findViewWithTag("home_calendar_day_2000-03-20");if(cell==null||!cell.performClick())throw new Exception("Month target is not clickable");});Thread.sleep(70);
        uiAssert(()->{if(paper[0]!=pagerV15(screen))throw new Exception("Far date click rebuilt the pager");View cell=calendar[0].findViewWithTag("home_calendar_day_2000-03-04");if(cell==null||!cell.performClick())throw new Exception("Rapid reverse target is not clickable");});settleV15();
        uiAssert(()->{if(!"2000-03-04".equals(HomeCalendarDates.key(paper[0].currentDate())))throw new Exception("Rapid date selection committed an old target");
            paper[0].animateToDate(HomeCalendarDates.day(new GregorianCalendar(1999,Calendar.NOVEMBER,3)));});Thread.sleep(75);
        uiAssert(()->{
            ArrayList<View> visible=new ArrayList<>();ArrayList<Float> positions=new ArrayList<>();
            for(int i=0;i<paper[0].getChildCount();i++){View child=paper[0].getChildAt(i);if(child.getTranslationX()<paper[0].getWidth()&&child.getTranslationX()+paper[0].getWidth()>0){visible.add(child);positions.add(child.getTranslationX());}}
            invoke(screen,"retainHomeFoodDate",new Class[]{String.class},"1999-11-03");
            for(int i=0;i<visible.size();i++)if(visible.get(i).getParent()!=paper[0]||Math.abs(visible.get(i).getTranslationX()-positions.get(i))>1)throw new Exception("Tapping a far-date food replaced the still-visible source paper");
            if(!"1999-11-03".equals(invoke(screen,"homeDate",new Class[]{})))throw new Exception("Far-date food tap lost its date ownership");
            paper[0].animateToDate(HomeCalendarDates.day(new GregorianCalendar(2001,Calendar.JANUARY,12)));
        });settleV15();
        uiAssert(()->{if(!"2001-01-12".equals(HomeCalendarDates.key(paper[0].currentDate())))throw new Exception("Far-date retarget ended on intermediate date");Calendar future=Calendar.getInstance();future.add(Calendar.DATE,1);
            if(!"2001年1月".contentEquals(((android.widget.TextView)ownField(calendar[0],"monthLabel")).getText()))throw new Exception("Settled month header retained a prior date");
            if(paper[0].animateToDate(future)||!"2001-01-12".equals(HomeCalendarDates.key(paper[0].currentDate())))throw new Exception("Future date bypassed the boundary");assertRenderPure(screen,prefs,before);
            setMainField(screen,"homeDateKey",today);setMainField(screen,"homeCalendarExpanded",false);invoke(screen,"showTab",new Class[]{int.class,boolean.class},0,false);
        });settleV15();

        // Removed transition children can remain attached; they must stop owning the date.
        final String[] dateOwner={null};
        uiAssert(()->{paper[0]=pagerV15(screen);Calendar past=Calendar.getInstance();past.add(Calendar.DATE,-10);paper[0].animateToDate(past);});Thread.sleep(65);
        uiAssert(()->{invoke(screen,"openPreferencesPage",new Class[]{});dateOwner[0]=(String)invoke(screen,"homeDate",new Class[]{});
            if(TodayDayPager.isLiveHierarchy(paper[0]))throw new Exception("Disappearing old paper still owns active date callbacks");});settleV15();
        uiAssert(()->{if(!dateOwner[0].equals(invoke(screen,"homeDate",new Class[]{})))throw new Exception("Old page changed date after navigation");if(paper[0].isAttachedToWindow())throw new Exception("Finished transition leaked the old paper");invoke(screen,"closePreferencesPage",new Class[]{});});settleV15();

        // Short flicks, cancellation and the book-carousel edge use the actual window dispatcher.
        rootSwipeV21(screen,1,true,false,name+"-today-to-footprints");
        ViewGroup host=(ViewGroup)field(screen,"content");Rect area=screenRect(host);float x=area.left+area.width()*.7f,y=area.top+area.height()*.58f,end=x-area.width()*.2f;
        final float flickStart=x,flickEnd=end,flickY=y;
        // Hardware timestamps survive a busy UI thread. Dispatch a queued 120ms
        // finger stream rather than stretching its velocity by host/bridge waits.
        uiAssert(()->{long time=android.os.SystemClock.uptimeMillis()-120;int[] location=new int[2];screen.getWindow().getDecorView().getLocationOnScreen(location);
            for(int i=0;i<=6;i++){int action=i==0?MotionEvent.ACTION_DOWN:i==6?MotionEvent.ACTION_UP:MotionEvent.ACTION_MOVE;
                MotionEvent event=MotionEvent.obtain(time,time+i*20,action,flickStart+(flickEnd-flickStart)*i/6-location[0],flickY-location[1],0);event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
                try{screen.dispatchTouchEvent(event);}finally{event.recycle();}localPointerFallbacksV20++;
            }
        });settleV15();
        uiAssert(()->{if(((Integer)field(screen,"currentTab"))!=3)throw new Exception("A short forward flick still requires dragging half the screen");});
        rootSwipeV21(screen,1,false,false,name+"-me-to-footprints");rootSwipeV21(screen,1,true,true,name+"-root-reverse-cancel");
        uiAssert(()->{JSONObject p=(JSONObject)field(screen,"profile");p.put("footprintLayout","original");invoke(screen,"saveProfile",new Class[]{});invoke(screen,"showTab",new Class[]{int.class,boolean.class},1,false);});settleV15();
        final android.widget.HorizontalScrollView[] carousel={null};uiAssert(()->{carousel[0]=(android.widget.HorizontalScrollView)screen.getWindow().getDecorView().findViewWithTag("footprints_book_carousel");if(carousel[0]==null)throw new Exception("Legacy carousel not available");carousel[0].scrollTo(0,0);});
        Rect shelf=screenRect(carousel[0]);x=shelf.left+shelf.width()*.2f;y=shelf.exactCenterY();end=x+shelf.width()*.55f;long down=android.os.SystemClock.uptimeMillis();
        pointerV15(down,MotionEvent.ACTION_DOWN,x,y);movesV15(down,x,y,end,y,8);pointerV15(down,MotionEvent.ACTION_UP,end,y);settleV15();
        uiAssert(()->{if(((Integer)field(screen,"currentTab"))!=0)throw new Exception("Book carousel at its edge blocked root navigation");assertRenderPure(screen,prefs,before);});

        // The shared navigation path must animate all these pages and clean up on rapid return.
        for(String[] route:new String[][]{{"openAiAnalysisPage","closeAiAnalysisPage"},{"openStickerGalleryPage","closeStickerGalleryPage"},{"openPreferencesPage","closePreferencesPage"},{"openThemeSettingsPage","closeThemeSettingsPage"},{"openProfileSettingsPage","closeProfileSettingsPage"},{"openDataSecurityPage","closeDataSecurityPage"},{"openAboutSoftwarePage","closeAboutSoftwarePage"}}){
            final ArrayList<View> outgoing=new ArrayList<>();
            uiAssert(()->invoke(screen,route[0],new Class[]{}));Thread.sleep(45);
            uiAssert(()->{PageCrossfade transition=(PageCrossfade)field(screen,"pageCrossfade");if(android.animation.ValueAnimator.areAnimatorsEnabled()&&(transition==null||!transition.active()))throw new Exception("No intermediate transition for "+route[0]);
                ViewGroup content=(ViewGroup)field(screen,"content");for(int i=0;i<content.getChildCount();i++)if(Math.abs(content.getChildAt(i).getTranslationX())>PaperComponents.dp(c,49))throw new Exception("Hierarchy transition still throws a full screen width: "+route[0]);
                if(transition!=null&&transition.active()){
                    Object layer=ownField(transition,"image");if(ownField(layer,"bitmap")!=null)throw new Exception("Routine navigation still allocates a full-screen screenshot");
                    for(Object old:(java.util.List<?>)ownField(layer,"outgoing"))outgoing.add((View)old);
                }
                invoke(screen,route[1],new Class[]{});
            });settleV15();
            uiAssert(()->{PageCrossfade transition=(PageCrossfade)field(screen,"pageCrossfade");if(transition!=null&&transition.active())throw new Exception("Interrupted route retained its old page: "+route[0]);for(View old:outgoing)if(old.isAttachedToWindow())throw new Exception("Transition leaked an attached old page: "+route[0]);});
        }
        screenshot(name+"-navigation-final");

        // Inline input stays in the original dialog and merges into the freshest stored meal.
        final AlertDialog[] editor={null};final android.widget.EditText[] input={null};
        uiAssert(()->editor[0]=(AlertDialog)invoke(screen,"showMealRecordEditor",new Class[]{String.class,String.class},today,"snack"));settleV15();
        uiAssert(()->{View decor=editor[0].getWindow().getDecorView();View text=decor.findViewWithTag("meal_record_text");if(!(text instanceof android.widget.EditText))throw new Exception("Meal blank area is not an inline input");input[0]=(android.widget.EditText)text;
            if(input[0].length()!=0||input[0].getHint()==null)throw new Exception("Prompt was saved as real meal text");input[0].requestFocus();input[0].performClick();
        });waitForIdleSync();
        uiAssert(()->{if(!editor[0].isShowing()||editor[0].getWindow().getDecorView().findViewWithTag("manual_food_text")!=null)throw new Exception("Typing launched a second editor");
            input[0].setText("热牛奶\n小点心");JSONObject latest=NutritionStore.read(prefs,NutritionStore.DAYS),day=latest.getJSONObject(today);day.put("snack",new JSONObject().put("workerMarker",23).put("calories",140));prefs.edit().putString(NutritionStore.DAYS,latest.toString()).commit();
        });Thread.sleep(90);screenshotNowV15(name+"-inline-edit");
        uiAssert(()->editor[0].getWindow().getDecorView().findViewWithTag("meal_record_done").performClick());settleV15();
        JSONObject saved=NutritionStore.meal(NutritionStore.read(prefs,NutritionStore.DAYS),today,"snack");
        if(saved==null||!"热牛奶\n小点心".equals(saved.optString("text"))||saved.optInt("workerMarker")!=23||saved.optInt("calories")!=140)throw new Exception("Inline save lost text or fresh worker fields");
        uiAssert(()->{editor[0]=(AlertDialog)invoke(screen,"showMealRecordEditor",new Class[]{String.class,String.class},today,"snack");input[0]=(android.widget.EditText)editor[0].getWindow().getDecorView().findViewWithTag("meal_record_text");input[0].setText("返回也保留的夜宵");editor[0].cancel();});settleV15();
        if(!"返回也保留的夜宵".equals(NutritionStore.meal(NutritionStore.read(prefs,NutritionStore.DAYS),today,"snack").optString("text")))throw new Exception("Dismissal lost inline text");
        uiAssert(()->{editor[0]=(AlertDialog)invoke(screen,"showMealRecordEditor",new Class[]{String.class,String.class},today,"snack");input[0]=(android.widget.EditText)editor[0].getWindow().getDecorView().findViewWithTag("meal_record_text");input[0].setText("");editor[0].getWindow().getDecorView().findViewWithTag("meal_record_done").performClick();});settleV15();
        saved=NutritionStore.meal(NutritionStore.read(prefs,NutritionStore.DAYS),today,"snack");if(saved.has("text")||saved.optInt("workerMarker")!=23||saved.optInt("calories")!=140)throw new Exception("Clearing inline text removed other meal fields");
        String noChange=prefs.getString(NutritionStore.DAYS,"{}");uiAssert(()->{editor[0]=(AlertDialog)invoke(screen,"showMealRecordEditor",new Class[]{String.class,String.class},"1998-01-01","breakfast");editor[0].dismiss();});settleV15();
        if(!noChange.equals(prefs.getString(NutritionStore.DAYS,"{}")))throw new Exception("Opening/closing an empty inline editor created a diary");
        if(requests.get()!=0)throw new Exception("UI revisions attempted HTTP(S)");
        Bundle result=new Bundle();result.putString("stream","PASS beta.23: actual week/month date click animation and same pager/calendar, rapid reverse/far date retarget/future guard, short root flicks/reversal/cancel/carousel edge handoff, seven shared page routes and interrupted return cleanup, original-dialog inline typing/newest field merge/dismiss save/clear/no blank diary. Window pointer events="+localPointerFallbacksV20+", HTTP(S)="+requests.get()+".\n");finish(Activity.RESULT_OK,result);
    }
    @Override public void onStart(){
        Bundle result=new Bundle();
        try{
            Context c=getTargetContext();android.content.SharedPreferences p=c.getSharedPreferences(NutritionStore.PREFS,0);
            // This runner only ships in the isolated QA APK. Each scenario seeds
            // its own profile; an earlier scenario's private credentials must not
            // override that new fixture or permit a real service request.
            if(!c.getSharedPreferences(PrivateProfileStore.PRIVATE_PREFS,0).edit().clear().commit())throw new Exception("Cannot reset isolated QA private settings fixture");
            if(args!=null&&Arrays.asList("offline-v22","prepare-offline-v22","download-online-v22").contains(args.getString("scenario")))throw new IllegalArgumentException("Retired recognition QA scenario");
            if(args!=null&&"release-v060".equals(args.getString("scenario"))){releaseV060(c,p);return;}
            if(args!=null&&"revision-v35".equals(args.getString("scenario"))){revisionV35(c,p);return;}
            if(args!=null&&"revision-v34".equals(args.getString("scenario"))){revisionV34(c,p);return;}
            if(args!=null&&"revision-v33".equals(args.getString("scenario"))){revisionV33(c,p);return;}
            if(args!=null&&"revision-v32".equals(args.getString("scenario"))){revisionV32(c,p);return;}
            if(args!=null&&"revision-v31".equals(args.getString("scenario"))){revisionV31(c,p);return;}
            if(args!=null&&"revision-v30".equals(args.getString("scenario"))){revisionV30(c,p);return;}
            if(args!=null&&"revision-v29".equals(args.getString("scenario"))){revisionV29(c,p);return;}
            if(args!=null&&"revision-v28".equals(args.getString("scenario"))){revisionV28(c,p);return;}
            if(args!=null&&"revision-v27".equals(args.getString("scenario"))){revisionV26(c,p);return;}
            if(args!=null&&"revision-v26".equals(args.getString("scenario"))){revisionV26(c,p);return;}
            if(args!=null&&"revision-v25".equals(args.getString("scenario"))){revisionV25(c,p);return;}
            if(args!=null&&"revision-v24".equals(args.getString("scenario"))){revisionV24(c,p);return;}
            if(args!=null&&"import-cutout-v24".equals(args.getString("scenario"))){importCutoutV24(c,p);return;}
            if(args!=null&&"cutout-v23".equals(args.getString("scenario"))){cutoutV23(c);return;}
            if(args!=null&&"revision-v23".equals(args.getString("scenario"))){revisionV23(c,p);return;}
            if(args!=null&&"revision-v22".equals(args.getString("scenario"))){revisionV22(c,p);return;}
            if(args!=null&&"onboarding-v22".equals(args.getString("scenario"))){onboardingV22(c,p);return;}
            if(args!=null&&"revision-v21".equals(args.getString("scenario"))){revisionV21(c,p);return;}
            if(args!=null&&"reprocess-v20".equals(args.getString("scenario"))){reprocessV20(c,p);return;}
            if(args!=null&&"supplement-v20".equals(args.getString("scenario"))){supplementV20(c,p);return;}
            if(args!=null&&"revision-v20".equals(args.getString("scenario"))){revisionV20(c,p);return;}
            if(args!=null&&"revision-v19".equals(args.getString("scenario"))){revisionV19(c,p);return;}
            if(args!=null&&"downloads-v19".equals(args.getString("scenario"))){liveDownloadsV19(c,p);return;}
            if(args!=null&&"deepseek-v18".equals(args.getString("scenario"))){deepSeekV18(c,p);return;}
            if(args!=null&&"queue-priority-v17".equals(args.getString("scenario"))){queuePriorityV17(c,p);return;}
            if(args!=null&&"revision-v17".equals(args.getString("scenario"))){revisionV17(c,p);return;}
            if(args!=null&&"modern-only-v16".equals(args.getString("scenario"))){modernOnlyV16(c,p);return;}
            if(args!=null&&"dialog-policy-v15".equals(args.getString("scenario"))){dialogPolicyV15(c,p);return;}
            if(args!=null&&"revision-v15".equals(args.getString("scenario"))){revisionV15(c,p);return;}
            if(args!=null&&"camera-wide-v14".equals(args.getString("scenario"))){cameraWideV14(c,p);return;}
            if(args!=null&&"revision-v13".equals(args.getString("scenario"))){revisionV13(c,p);return;}
            if(args!=null&&"settings-v12".equals(args.getString("scenario"))){settingsV12(c,p);return;}
            if(args!=null&&"rename-scan".equals(args.getString("scenario"))){renameScan(c,p);return;}
            if(args!=null&&"progress-v13".equals(args.getString("scenario"))){progressV13(c,p);return;}
            if(args!=null&&"minimal-dialogs-v11".equals(args.getString("scenario"))){minimalDialogs(c,p);return;}
            if(args!=null&&"refinements-v9".equals(args.getString("scenario"))){refinementsV9(c,p);return;}
            if(args!=null&&"cutout-quality".equals(args.getString("scenario"))){cutoutQuality(c);return;}
            if(args!=null&&"capture-live".equals(args.getString("scenario"))){internalCaptureFlow(c,p,false);return;}
            if(args!=null&&"capture-gallery".equals(args.getString("scenario"))){internalCaptureFlow(c,p,true);return;}
            if(args!=null&&"polish-v7".equals(args.getString("scenario"))){polishScenario(c,p);return;}
            if(args!=null&&"sticker-controls".equals(args.getString("scenario"))){stickerControlsScenario(c,p);return;}
            if(args!=null&&"settings-flow".equals(args.getString("scenario"))){settingsScenario(c,p);return;}
            if(args!=null&&"single-preview".equals(args.getString("scenario"))){
                FoodAnalysisJobService.cancelAll(c);
                String date=new SimpleDateFormat("yyyy-MM-dd",Locale.CHINA).format(new Date());JSONObject sample=unifiedDays(c,date);
                JSONObject day=sample.getJSONObject(date);day.remove("breakfast");day.remove("dinner");
                JSONObject profile=new JSONObject().put("new_ui_v2",true).put("decoration","yellow").put("seenAnnouncementVersion",c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName);
                p.edit().putString(NutritionStore.PROFILE,profile.toString()).putString(NutritionStore.DAYS,sample.toString()).commit();
                MainActivity screen=(MainActivity)startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                Thread.sleep(800);screenshot("beta5-single-home");
                if(tagged(screen.getWindow().getDecorView(),"day_food_").size()!=1)throw new Exception("Single-food preview duplicated entries");
                result.putString("stream","PASS: one-food unified home screenshot; nutrition/name are labelled QA fixture\n");finish(Activity.RESULT_OK,result);return;
            }
            if(args!=null&&"edit-draft".equals(args.getString("scenario"))){editDraft(c,p);return;}
            if(args!=null&&"add-flow".equals(args.getString("scenario"))){internalCaptureFlow(c,p,false);return;}
            if(args!=null&&"weekly-empty".equals(args.getString("scenario"))){
                p.edit().putString(NutritionStore.DAYS,"{}").commit();
                Activity report=startActivitySync(new Intent(c,FoodWeeklyActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                screenshot("beta3-weekly-empty");
                if(report.getWindow().getDecorView().findViewWithTag("weekly_empty")==null)throw new Exception("Missing weekly empty state");
                result.putString("stream","PASS: independent report empty state\n");finish(Activity.RESULT_OK,result);return;
            }
            if(args!=null&&("layout".equals(args.getString("scenario"))||"unified-layout".equals(args.getString("scenario"))||"unified-empty".equals(args.getString("scenario")))){layoutScenario(c,p);return;}
            if (args!=null && "render".equals(args.getString("scenario"))) {
                JSONObject existing=NutritionStore.read(p,NutritionStore.PROFILE);
                existing.put("new_ui_v2",!"true".equals(args.getString("legacy")));
                existing.put("decoration","true".equals(args.getString("night"))?"night":"yellow");
                p.edit().putString(NutritionStore.PROFILE,existing.toString()).commit();
                MainActivity screen=(MainActivity)startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                for(final int tab:new int[]{0,1,2,3}) {
                    runOnMainSync(()->{try{invoke(screen,"showTab",new Class[]{int.class,boolean.class},tab,false);}catch(Exception e){throw new RuntimeException(e);}});
                    screenshot((args.getString("name","render"))+"-"+tab);
                }
                runOnMainSync(()->{try{invoke(screen,"renderFoodRecognitionSettingsPage",new Class[]{});}catch(Exception e){throw new RuntimeException(e);}});
                screenshot(args.getString("name","render")+"-dialog");
                sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
                result.putString("stream","PASS: four-page screenshot capture\n");finish(Activity.RESULT_OK,result);return;
            }
            String requestedScenario=args==null?null:args.getString("scenario");
            if(requestedScenario!=null&&requestedScenario.startsWith("revision-"))throw new Exception("Unknown revision QA scenario: "+requestedScenario);
            String date=new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.CHINA).format(new Date());
            stickerBorder();
            installTransport();
            FoodAnalysisJobService.cancelAll(c);
            FoodDraftStore.discard(c,FoodDraftStore.pendingSources(c));
            JSONObject profile=new JSONObject().put("new_ui_v2",true).put("foodAutoAnalyze",false).put("foodPhotoConsent",false)
                .put("seenAnnouncementVersion",c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName).put("apiEnabled",false);
            JSONObject day=new JSONObject().put("breakfast",new JSONObject().put("text","旧版早餐记录").put("manualCalories",300))
                .put("manualCaloriesOverride",true).put("manualCaloriesTotal",300);
            p.edit().putString(NutritionStore.PROFILE,profile.toString()).putString(NutritionStore.DAYS,new JSONObject().put(date,day).toString()).commit();
            MainActivity activity=(MainActivity)startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync();
            for(final int tab:new int[]{0,1,2,3,0}){
                final Throwable[] error={null};runOnMainSync(()->{try{invoke(activity,"showTab",new Class[]{int.class,boolean.class},tab,false);}catch(Throwable t){error[0]=t;}});
                waitForIdleSync();if(error[0]!=null)throw new Exception("tab "+tab,error[0]);
            }
            Bitmap source;
            try(InputStream in=c.getAssets().open("qa/food.jpg")){source=BitmapFactory.decodeStream(in);}
            if(source==null)throw new Exception("Missing QA fixture");
            // Exercise the JNI directly so a native failure has a useful stack trace in QA output.
            Bitmap nativeCheck=FoodCutoutEngine.create(c,source);
            if(nativeCheck==null)throw new Exception("Native returned no image");nativeCheck.recycle();
            final Bitmap bitmap=source;
            final Throwable[] error={null};
            runOnMainSync(()->{try{
                Object saved=invoke(activity,"saveMealBitmap",new Class[]{String.class,String.class,Bitmap.class,boolean.class,boolean.class},date,"lunch",bitmap,true,false);
                if(!Boolean.TRUE.equals(saved))throw new Exception("photo save rejected");
            }catch(Throwable t){error[0]=t;}});
            source.recycle();if(error[0]!=null)throw new Exception("save photo",error[0]);
            String photo=draftSource(c,date,"lunch");
            assertNoMealPhoto(p,date,"lunch",photo);
            runOnMainSync(()->{try{invoke(activity,"showFoodProcessing",new Class[]{String.class,String.class,List.class},date,"lunch",Collections.singletonList(photo));}catch(Exception e){throw new RuntimeException(e);}});
            screenshot("beta4-processing-original");
            if(!FoodAnalysisJobService.enqueueDraft(c,date,"lunch",photo,false)||FoodDraftStore.pendingSources(c).size()!=1)throw new Exception("duplicate draft enqueue");
            long start=android.os.SystemClock.elapsedRealtime();
            JSONObject item=waitDraft(c,photo,"awaiting_consent",120000);
            if(item.optString("cutoutFile").isEmpty())throw new Exception("JNI cutout failed: "+item);
            if(item.has("nutrients"))throw new Exception("invalid API must not save nutrients");
            if(!"awaiting_consent".equals(item.optString("analysisStatus"))||apiCalls.get()!=0)throw new Exception("Upload before consent");
            assertNoMealPhoto(p,date,"lunch",photo);
            Thread.sleep(900);screenshot("beta4-processing-cutout");
            Bitmap cutout=BitmapFactory.decodeFile(new File(c.getFilesDir(),item.getString("cutoutFile")).getAbsolutePath());
            if(cutout==null||!cutout.hasAlpha())throw new Exception("missing alpha PNG");
            cutout.recycle();
            day=NutritionStore.read(p,NutritionStore.DAYS).getJSONObject(date);
            if(FoodUi.displayCalories(day)!=300)throw new Exception("manual override lost");
            // Recreate the Activity while the durable draft is waiting for consent.
            ActivityMonitor recreation=new ActivityMonitor(MainActivity.class.getName(),null,false);addMonitor(recreation);
            runOnMainSync(()->activity.recreate());
            final MainActivity resumed=(MainActivity)recreation.waitForActivityWithTimeout(15000);removeMonitor(recreation);
            if(resumed==null||FoodDraftStore.item(c,photo)==null)throw new Exception("Activity recreation lost draft");
            waitForIdleSync();Thread.sleep(500);
            if(descriptionNode("确定")==null)throw new Exception("Activity recreation did not restore draft preview");
            screenshot("beta4-processing-recreated");
            // Resume the same durable task after consent/configuration, without re-cutting.
            String cutoutName=item.getString("cutoutFile");long cutoutModified=new File(c.getFilesDir(),cutoutName).lastModified();
            profile.put("foodAutoAnalyze",true).put("foodPhotoConsent",true).put("apiEnabled",true)
                .put("apiEndpoint","https://food.qa.invalid/v1/chat/completions").put("apiModel","qa-vision").put("apiKey","qa-not-a-real-key");
            p.edit().putString(NutritionStore.PROFILE,profile.toString()).commit();FoodAnalysisJobService.schedule(c,false,0);
            item=waitDraftName(c,photo,20000);
            if(item.has("nutrients")||!"recognizing".equals(item.optString("analysisStatus")))throw new Exception("Preliminary name fabricated or prematurely saved nutrients");
            assertNoMealPhoto(p,date,"lunch",photo);
            Thread.sleep(500);screenshot("beta4-processing-name-first");
            streamName=false;nutritionReleased.countDown();
            item=waitDraft(c,photo,"completed",90000);
            if(!"qa-returned-vision".equals(item.optString("providerModel")))throw new Exception("Returned model identity was replaced by requested alias");
            if(item.getJSONObject("nutrients").getInt("caloriesKcal")!=676)throw new Exception("Automatic draft nutrition failed");
            if(apiCalls.get()!=1||!cutoutName.equals(item.optString("cutoutFile"))||new File(c.getFilesDir(),cutoutName).lastModified()!=cutoutModified)throw new Exception("Resume duplicated request/cutout: calls="+apiCalls.get()+", same alpha="+cutoutName.equals(item.optString("cutoutFile"))+", old mtime="+cutoutModified+", new mtime="+new File(c.getFilesDir(),cutoutName).lastModified());
            assertNoMealPhoto(p,date,"lunch",photo);
            Thread.sleep(1300);screenshot("beta4-processing-nutrition");
            clickDescription("修改");
            Thread.sleep(300);screenshot("beta4-processing-edit");
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);waitForIdleSync();
            if(descriptionNode("确定")==null)throw new Exception("Closing draft editor lost preview");
            clickDescription("确定");
            JSONObject confirmed=NutritionStore.meal(NutritionStore.read(p,NutritionStore.DAYS),date,"lunch");
            if(!NutritionStore.containsPhoto(confirmed,photo)||NutritionStore.forSource(confirmed,photo).length()!=1||FoodDraftStore.item(c,photo)!=null)throw new Exception("Confirm did not commit draft exactly once");
            FoodDraftStore.confirm(c,Collections.singletonList(photo));
            confirmed=NutritionStore.meal(NutritionStore.read(p,NutritionStore.DAYS),date,"lunch");
            if(confirmed.getJSONArray("photos").length()!=1||confirmed.getJSONArray("foodItems").length()!=1)throw new Exception("Repeated confirm duplicated photo/nutrition");
            if(FoodUi.displayCalories(NutritionStore.read(p,NutritionStore.DAYS).getJSONObject(date))!=300)throw new Exception("Confirmed nutrition replaced manual calories");
            final Throwable[] backupError={null};
            runOnMainSync(()->{try{
                // Refresh first so the live Activity sees worker writes before backup/restore.
                java.lang.reflect.Field days=MainActivity.class.getDeclaredField("days");days.setAccessible(true);days.set(resumed,NutritionStore.read(p,NutritionStore.DAYS));
                byte[] backup=(byte[])invoke(resumed,"buildBackupBytes",new Class[]{boolean.class},false);
                invoke(resumed,"restoreBackupFromStream",new Class[]{InputStream.class,boolean.class},new ByteArrayInputStream(backup),false);
                invoke(resumed,"renderCurrent",new Class[]{});
            }catch(Throwable t){backupError[0]=t;}});
            if(backupError[0]!=null)throw new Exception("backup restore",backupError[0]);
            JSONObject restored=NutritionStore.read(p,NutritionStore.DAYS).getJSONObject(date);
            if(!"旧版早餐记录".equals(restored.getJSONObject("breakfast").getString("text")))throw new Exception("legacy text lost");
            if(restored.getJSONObject("lunch").getJSONArray("foodItems").length()!=1)throw new Exception("food lost after restore");
            JSONObject restoredProfile=NutritionStore.read(p,NutritionStore.PROFILE);
            if(restoredProfile.optBoolean("foodPhotoConsent")||!restoredProfile.optString("apiKey").isEmpty())throw new Exception("Portable backup restored private API configuration/consent");
            final Throwable[] recycleError={null};
            runOnMainSync(()->{try{
                invoke(resumed,"deleteMealPhoto",new Class[]{String.class,String.class,String.class},date,"lunch",photo);
                JSONObject recycledProfile=NutritionStore.read(p,NutritionStore.PROFILE);
                int index=recycledProfile.getJSONArray("recycleBin").length()-1;
                if(recycledProfile.getJSONArray("recycleBin").getJSONObject(index).getJSONArray("foodItems").length()!=1)throw new Exception("food bundle missing");
                invoke(resumed,"restoreRecycleItem",new Class[]{int.class},index);
            }catch(Throwable t){recycleError[0]=t;}});
            if(recycleError[0]!=null)throw new Exception("recycle restore",recycleError[0]);
            JSONObject finalMeal=NutritionStore.meal(NutritionStore.read(p,NutritionStore.DAYS),date,"lunch");
            if(finalMeal.getJSONArray("foodItems").length()!=1)throw new Exception("food not restored with source");
            if(!"[]".equals(p.getString("food_analysis_jobs","[]")))throw new Exception("restore left stale jobs");
            cancellationInFlight(c,p,date);
            runOnMainSync(()->{try{((FoodUi)invoke(resumed,"foodUi",new Class[]{})).weekly(date);}catch(Exception e){throw new RuntimeException(e);}});
            Thread.sleep(500);screenshot("beta4-weekly-data");sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            result.putString("stream","PASS: tabs, staged photo/dedupe, local JNI without consent/key, preview reveal, no upload before consent, recreation retention, streamed dish name before validated nutrients, QA transport nutrition in draft only, resume without duplicate/cutout, edit controls, explicit idempotent confirm, manual precedence, backup/recycle restore, cancellation during in-flight API, independent report\n");
            result.putLong("cutout_pipeline_ms",android.os.SystemClock.elapsedRealtime()-start);
            finish(Activity.RESULT_OK,result);
        }catch(Throwable e){nutritionReleased.countDown();responseReleased.countDown();result.putString("stream","FAIL: "+android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,result);}
    }
}
