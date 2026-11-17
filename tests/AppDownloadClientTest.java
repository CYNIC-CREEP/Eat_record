package com.eatrecord.app;
import org.json.*;
import java.net.*;
import java.io.*;

public final class AppDownloadClientTest {
    private static int checks,logins,reads,guestReads;
    private static boolean expire=true;
    private static boolean guestUnavailable;
    private static String raw="https://cdn.test.invalid/file.ttf";
    private static void check(boolean value,String name){if(!value)throw new AssertionError(name);checks++;}
    private static JSONObject liveManifest(AppDownloadClient client,String root)throws Exception{
        try(InputStream in=new URL(client.rawUrl(root+"/update.json")).openStream();
                java.util.Scanner scanner=new java.util.Scanner(in,"UTF-8").useDelimiter("\\A")){
            return new JSONObject(scanner.next());
        }
    }
    public static void main(String[] args)throws Exception{
        if(args.length>0&&args[0].equals("live")){
            AppDownloadClient client=new AppDownloadClient();
            JSONObject update=AppUpdateCheck.fetch(203,()->liveManifest(client,AppDownloadClient.ROOT),
                    ()->liveManifest(client,AppDownloadClient.GUEST_ROOT));
            check(update.optInt("versionCode")==202,"Both live distribution sources identify the published formal version");
            String font=client.fontUrl("ZCOOLKuaiLe-Regular.ttf");
            try(InputStream in=new URL(font).openStream()){byte[] first=new byte[4];int count=in.read(first);if(count!=4||first[0]!=0||first[1]!=1)throw new IOException("Font signature invalid");}
            System.out.println("PASS: live ordered formal-version checks and guest font header; no session/signed URL logged");return;
        }
        URL.setURLStreamHandlerFactory(protocol->!protocol.equals("https")?null:new URLStreamHandler(){protected URLConnection openConnection(URL url)throws IOException{
            if(!url.getHost().equals("pan.freefeng.icu"))throw new IOException("Session escaped origin");
            return new HttpURLConnection(url){ByteArrayOutputStream sent=new ByteArrayOutputStream();String response;
                public void connect(){}public void disconnect(){}public boolean usingProxy(){return false;}public OutputStream getOutputStream(){return sent;}
                public int getResponseCode()throws IOException{try{
                    JSONObject body=new JSONObject(sent.toString("UTF-8"));
                    if(url.getPath().equals("/api/auth/login")){check(body.getString("username").equals("myapp")&&body.getString("password").isEmpty(),"embedded distribution account only");logins++;response=new JSONObject().put("code",200).put("data",new JSONObject().put("token","qa-session-"+logins)).toString();}
                    else if(body.getString("path").startsWith(AppDownloadClient.GUEST_ROOT)){
                        guestReads++;
                        check(getRequestProperty("Authorization")==null,"Guest request carries no stored account session");
                        if(url.getPath().endsWith("/list"))check(!body.getBoolean("refresh"),"Guest reads use normal directory caching");
                        response=guestUnavailable?"{\"code\":403}":new JSONObject().put("code",200).put("data",new JSONObject().put("raw_url",raw).put("content",new JSONArray())).toString();
                    }
                    else{check(getRequestProperty("Authorization").equals("qa-session-"+logins),"OpenList header is raw session, not Bearer");check(body.getString("path").startsWith("/eat record"),"account-relative app directory");reads++;if(expire){expire=false;response="{\"code\":401}";}else response=new JSONObject().put("code",200).put("data",new JSONObject().put("raw_url",raw).put("content",new JSONArray())).toString();}
                    return 200;
                }catch(Exception e){throw new IOException(e);}}
                public InputStream getInputStream()throws IOException{return new ByteArrayInputStream(response.getBytes("UTF-8"));}
            };
        }});
        AppDownloadClient c=new AppDownloadClient();String url=c.rawUrl("/lanzou/Myapp/eat record/fonts/font.ttf");
        check(logins==2&&reads==2,"expired session refreshes once without an infinite loop");check(url.equals("https://cdn.test.invalid/file.ttf"),"signed/raw link returned without attaching session to CDN");
        c.read("list","/eat record");check(logins==2,"valid session reused in memory");
        c.read("list",AppDownloadClient.GUEST_ROOT);
        new AppDownloadClient().rawUrl(AppDownloadClient.GUEST_ROOT+"/update.json");
        check(logins==2&&guestReads==2,"Guest access never logs in, including a new client");
        check(AppDownloadClient.path("/baidu/03.工具/24.Personal/2_EatRecord/0.6.2/apk/app.apk")
                .equals(AppDownloadClient.GUEST_ROOT+"/0.6.2/apk/app.apk"),"Mounted archive path maps to the guest scope");
        c.fontUrl("ZCOOLKuaiLe-Regular.ttf");check(guestReads==3&&logins==2,"Fonts prefer guest downloads");
        int previousReads=reads;guestUnavailable=true;c.fontUrl("ZCOOLKuaiLe-Regular.ttf");guestUnavailable=false;
        check(reads==previousReads+1&&logins==2,"Guest font failure uses the existing account fallback");
        raw="/p/24.Personal/2_EatRecord/readme.md?sign=synthetic";
        check(c.rawUrl(AppDownloadClient.GUEST_ROOT+"/readme.md").endsWith("&raw=true"),"Markdown download requests original bytes");
        for(String invalidUrl:new String[]{"file:///tmp/data","https://user:synthetic@cdn.invalid/file"}){
            raw=invalidUrl;boolean refused=false;try{c.rawUrl(AppDownloadClient.GUEST_ROOT+"/file");}catch(IOException e){refused=true;}
            check(refused,"Reject unsupported or credential-bearing download URLs");
        }
        for(String invalid:new String[]{"/other/file","/eat record/../private","/eat record2/font","https://elsewhere.invalid/file",AppDownloadClient.GUEST_ROOT+"/../private",AppDownloadClient.GUEST_ROOT+"2/file"}){boolean refused=false;try{AppDownloadClient.path(invalid);}catch(IOException e){refused=true;}check(refused,"reject outside/traversal download path");}
        boolean refused=false;try{c.read("remove",AppDownloadClient.GUEST_ROOT);}catch(IOException e){refused=true;}
        check(refused,"Guest capability remains read only");
        System.out.println("PASS: "+checks+" authenticated distribution checks");
    }
}
