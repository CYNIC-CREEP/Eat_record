package com.eatrecord.app;

import java.io.*;
import java.net.*;
import org.json.JSONObject;

/** App-owned, read-only distribution access. The session token stays in memory. */
final class AppDownloadClient {
    static final String BASE="https://pan.freefeng.icu";
    static final String ROOT="/eat record";
    static final String GUEST_ROOT="/24.Personal/2_EatRecord";
    private static final String GUEST_MOUNT="/baidu/03.工具/24.Personal/2_EatRecord";
    private String token="";

    static String path(String requested)throws IOException{
        String value=requested==null?"":requested.trim();
        String previous="/lanzou/Myapp/eat record";
        if(value.equals(previous)||value.startsWith(previous+"/"))value=ROOT+value.substring(previous.length());
        if(value.equals(GUEST_MOUNT)||value.startsWith(GUEST_MOUNT+"/"))value=GUEST_ROOT+value.substring(GUEST_MOUNT.length());
        if(!(within(value,ROOT)||within(value,GUEST_ROOT))||value.contains("\\")||value.contains("\u0000"))throw new IOException("下载目录无效");
        for(String part:value.split("/"))if(part.equals("..")||part.equals("."))throw new IOException("下载目录无效");
        return value;
    }

    private static boolean within(String value,String root){
        return value.equals(root)||value.startsWith(root+"/");
    }

    private synchronized String session()throws Exception{
        if(!token.isEmpty())return token;
        // This intentionally passwordless distribution account was provided for embedding.
        JSONObject response=post("/api/auth/login",new JSONObject().put("username","myapp").put("password",""),"");
        JSONObject data=response.optJSONObject("data");
        if(response.optInt("code")!=200||data==null||data.optString("token").isEmpty())throw new IOException("应用下载账号登录失败");
        token=data.getString("token");return token;
    }
    private synchronized void invalidate(String previous){if(token.equals(previous))token="";}

    JSONObject read(String operation,String requested)throws Exception{
        if(!operation.equals("get")&&!operation.equals("list"))throw new IOException("只允许读取下载目录");
        String resolved=path(requested);
        JSONObject body=new JSONObject().put("path",resolved);
        if(operation.equals("list"))body.put("page",1).put("per_page",200).put("refresh",false);
        if(within(resolved,GUEST_ROOT)){
            return requireData(post("/api/fs/"+operation,body,""));
        }
        for(int attempt=0;attempt<2;attempt++){
            String current=session();JSONObject response=post("/api/fs/"+operation,body,current);
            int code=response.optInt("code");
            if((code==401||code==403)&&attempt==0){invalidate(current);continue;}
            return requireData(response);
        }
        throw new IOException("下载登录已失效，请稍后重试");
    }
    private JSONObject requireData(JSONObject response)throws IOException{
        if(response.optInt("code")!=200){
            throw new IOException("下载目录读取失败（"+response.optInt("code")+"）");
        }
        JSONObject result=response.optJSONObject("data");
        if(result==null)throw new IOException("下载目录返回无效");
        return result;
    }
    String fontUrl(String fileName)throws Exception{
        if(fileName==null||fileName.isEmpty()||fileName.contains("/")||fileName.contains("\\")){
            throw new IOException("字体文件名无效");
        }
        try{
            return rawUrl(GUEST_ROOT+"/fonts/"+fileName);
        }catch(Exception unavailable){
            return rawUrl(ROOT+"/fonts/"+fileName);
        }
    }
    String rawUrl(String requested)throws Exception{
        String raw=read("get",requested).optString("raw_url");
        URL url=new URL(new URL(BASE),raw);
        if(raw.isEmpty()||url.getUserInfo()!=null||(!url.getProtocol().equals("https")&&!url.getProtocol().equals("http")))throw new IOException("下载链接无效");
        String result=url.toExternalForm();
        if(url.getPath().toLowerCase(java.util.Locale.ROOT).endsWith(".md")){
            String fragment=url.getRef();
            if(fragment!=null)result=result.substring(0,result.length()-fragment.length()-1);
            result+=(url.getQuery()==null?"?":"&")+"raw=true";
            if(fragment!=null)result+="#"+fragment;
        }
        return result;
    }
    private JSONObject post(String endpoint,JSONObject body,String authorization)throws Exception{
        HttpURLConnection connection=(HttpURLConnection)new URL(BASE+endpoint).openConnection();
        try{
            connection.setRequestMethod("POST");connection.setDoOutput(true);connection.setUseCaches(false);
            connection.setConnectTimeout(12000);connection.setReadTimeout(15000);connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Content-Type","application/json; charset=UTF-8");
            if(!authorization.isEmpty())connection.setRequestProperty("Authorization",authorization);
            byte[] bytes=body.toString().getBytes("UTF-8");connection.setFixedLengthStreamingMode(bytes.length);
            try(OutputStream out=connection.getOutputStream()){out.write(bytes);}
            int code=connection.getResponseCode();
            if(code==401||code==403)return new JSONObject().put("code",code);
            if(code<200||code>=300)throw new IOException("下载服务连接失败（HTTP "+code+"）");
            try(InputStream in=connection.getInputStream()){
                ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int count;
                while((count=in.read(buffer))!=-1){if(out.size()+count>1048576)throw new IOException("下载服务返回过大");out.write(buffer,0,count);}
                return new JSONObject(new String(out.toByteArray(),"UTF-8"));
            }
        }finally{connection.disconnect();}
    }
}
