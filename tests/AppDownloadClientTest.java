package com.eatrecord.app;
import org.json.*;
import java.net.*;
import java.io.*;

public final class AppDownloadClientTest {
    private static int checks,logins,reads;
    private static boolean expire=true;
    private static void check(boolean value,String name){if(!value)throw new AssertionError(name);checks++;}
    public static void main(String[] args)throws Exception{
        if(args.length>0&&args[0].equals("live")){
            AppDownloadClient client=new AppDownloadClient();JSONObject update=client.read("get",AppDownloadClient.ROOT+"/update.json");
            String font=client.rawUrl(AppDownloadClient.ROOT+"/fonts/ZCOOLKuaiLe-Regular.ttf");
            try(InputStream in=new URL(font).openStream()){byte[] first=new byte[4];int count=in.read(first);if(count!=4||first[0]!=0||first[1]!=1)throw new IOException("Font signature invalid");}
            System.out.println("PASS: live passwordless app session reads update metadata and downloads a real TTF header; no session/signed URL logged");return;
        }
        URL.setURLStreamHandlerFactory(protocol->!protocol.equals("https")?null:new URLStreamHandler(){protected URLConnection openConnection(URL url)throws IOException{
            if(!url.getHost().equals("pan.freefeng.icu"))throw new IOException("Session escaped origin");
            return new HttpURLConnection(url){ByteArrayOutputStream sent=new ByteArrayOutputStream();String response;
                public void connect(){}public void disconnect(){}public boolean usingProxy(){return false;}public OutputStream getOutputStream(){return sent;}
                public int getResponseCode()throws IOException{try{
                    JSONObject body=new JSONObject(sent.toString("UTF-8"));
                    if(url.getPath().equals("/api/auth/login")){check(body.getString("username").equals("myapp")&&body.getString("password").isEmpty(),"embedded distribution account only");logins++;response=new JSONObject().put("code",200).put("data",new JSONObject().put("token","qa-session-"+logins)).toString();}
                    else{check(getRequestProperty("Authorization").equals("qa-session-"+logins),"OpenList header is raw session, not Bearer");check(body.getString("path").startsWith("/eat record"),"account-relative app directory");reads++;if(expire){expire=false;response="{\"code\":401}";}else response="{\"code\":200,\"data\":{\"raw_url\":\"https://cdn.test.invalid/file.ttf\",\"content\":[]}}";}
                    return 200;
                }catch(Exception e){throw new IOException(e);}}
                public InputStream getInputStream()throws IOException{return new ByteArrayInputStream(response.getBytes("UTF-8"));}
            };
        }});
        AppDownloadClient c=new AppDownloadClient();String url=c.rawUrl("/lanzou/Myapp/eat record/fonts/font.ttf");
        check(logins==2&&reads==2,"expired session refreshes once without an infinite loop");check(url.equals("https://cdn.test.invalid/file.ttf"),"signed/raw link returned without attaching session to CDN");
        c.read("list","/eat record");check(logins==2,"valid session reused in memory");
        for(String invalid:new String[]{"/other/file","/eat record/../private","/eat record2/font","https://elsewhere.invalid/file"}){boolean refused=false;try{AppDownloadClient.path(invalid);}catch(IOException e){refused=true;}check(refused,"reject outside/traversal download path");}
        System.out.println("PASS: "+checks+" authenticated distribution checks");
    }
}
