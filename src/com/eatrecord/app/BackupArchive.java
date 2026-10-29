package com.eatrecord.app;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import org.json.*;

/** Bounded, streaming portable archive. Extraction never writes into the live media directory. */
final class BackupArchive {
    static final long MAX_JSON=16L*1024*1024,MAX_FILE=256L*1024*1024,MAX_TOTAL=2L*1024*1024*1024;
    static final long DISK_RESERVE=8L*1024*1024;
    static final int MAX_ENTRIES=50000;
    interface Space { long usable(File at); }
    static final Space DISK=File::getUsableSpace;
    static final class Staged {
        final JSONObject days,profile;final File files;final Set<String> names;
        Staged(JSONObject days,JSONObject profile,File files,Set<String> names){this.days=days;this.profile=profile;this.files=files;this.names=names;}
    }
    static boolean safeName(String name){
        return name!=null&&!name.isEmpty()&&name.length()<=240&&!name.contains("..")&&!name.contains("/")&&!name.contains("\\")&&!name.contains(":")&&name.charAt(0)!='.'&&name.indexOf(0)<0;
    }
    static File child(File directory,String name)throws IOException {
        if(!safeName(name))throw new IOException("Invalid backup file name");
        // Android may alias /data/data and /data/user/0. Normalize the trusted
        // directory first; reject links in the untrusted child itself.
        File parent=directory.getCanonicalFile(),result=new File(parent,name);
        if(!result.getCanonicalFile().getParentFile().equals(parent)||!result.getCanonicalFile().equals(result))throw new IOException("Backup file is not a direct regular child");
        return result;
    }
    static void requireSpace(File at,long bytes,Space space)throws IOException {
        long available=space.usable(at);
        if(bytes<0||available<DISK_RESERVE||bytes>available-DISK_RESERVE)throw new IOException("Insufficient space for backup");
    }
    static void write(OutputStream output,String days,String profile,List<File> files)throws IOException {
        byte[] buffer=new byte[32768];Set<String> seen=new HashSet<>();long total=0;
        if(files.size()>MAX_ENTRIES-2)throw new IOException("Too many backup files");
        try(ZipOutputStream zip=new ZipOutputStream(new BufferedOutputStream(output))){
            text(zip,"days.json",days);text(zip,"profile.json",profile);
            for(File file:files){
                String name=file.getName();if(!safeName(name)||!seen.add(name)||!file.isFile())throw new IOException("Invalid backup media");
                file=child(file.getParentFile(),name);
                if(file.length()>MAX_FILE)throw new IOException("Backup media is too large");
                ZipEntry entry=new ZipEntry("files/"+name);entry.setTime(0);zip.putNextEntry(entry);
                try(InputStream in=new BufferedInputStream(new FileInputStream(file))){int read;long count=0;while((read=in.read(buffer))!=-1){count+=read;total+=read;if(count>MAX_FILE||total>MAX_TOTAL)throw new IOException("Backup media is too large");zip.write(buffer,0,read);}}
                zip.closeEntry();
            }
        }
    }
    private static void text(ZipOutputStream zip,String name,String value)throws IOException {
        byte[] bytes=value.getBytes(StandardCharsets.UTF_8);if(bytes.length>MAX_JSON)throw new IOException("Backup JSON is too large");
        ZipEntry entry=new ZipEntry(name);entry.setTime(0);zip.putNextEntry(entry);zip.write(bytes);zip.closeEntry();
    }
    static Staged extract(InputStream input,File directory)throws Exception{return extract(input,directory,DISK);}
    static Staged extract(InputStream input,File directory,Space space)throws Exception {
        if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Cannot stage backup");
        File files=new File(directory,"incoming");if(!files.mkdir())throw new IOException("Cannot stage media");
        Set<String> seen=new HashSet<>(),names=new LinkedHashSet<>();String days=null,profile=null;long total=0;int count=0;byte[] buffer=new byte[32768];
        try(ZipInputStream zip=new ZipInputStream(new BufferedInputStream(input))){
            ZipEntry entry;
            while((entry=zip.getNextEntry())!=null){
                String name=entry.getName();if(++count>MAX_ENTRIES||!seen.add(name))throw new IOException("Duplicate or excessive backup entries");
                if(entry.isDirectory()){if(!"files/".equals(name))throw new IOException("Invalid backup directory");zip.closeEntry();continue;}
                boolean json="days.json".equals(name)||"profile.json".equals(name);String media=name.startsWith("files/")?name.substring(6):null;
                if(!json&&(media==null||!safeName(media)))throw new IOException("Invalid backup entry");
                long limit=json?MAX_JSON:MAX_FILE;if(entry.getSize()>limit)throw new IOException("Backup entry is too large");
                ByteArrayOutputStream text=json?new ByteArrayOutputStream():null;File target=json?null:child(files,media);long size=0;
                FileOutputStream disk=json?null:new FileOutputStream(target);
                try(OutputStream out=json?text:new BufferedOutputStream(disk)){
                    int read;while((read=zip.read(buffer))!=-1){size+=read;total+=read;if(size>limit||total>MAX_TOTAL)throw new IOException("Backup extraction limit exceeded");requireSpace(directory,read,space);out.write(buffer,0,read);}
                    out.flush();if(disk!=null)disk.getFD().sync();
                }
                zip.closeEntry(); // CRC/truncation errors must be observed before any live mutation.
                if(json){String value=new String(text.toByteArray(),StandardCharsets.UTF_8);if("days.json".equals(name))days=value;else profile=value;}else names.add(media);
            }
        }
        if(days==null||profile==null)throw new IOException("Backup JSON missing");
        JSONObject restoredDays=object(days),restoredProfile=object(profile);
        Iterator<String> dates=restoredDays.keys();while(dates.hasNext()){
            String date=dates.next();if(!(restoredDays.opt(date) instanceof JSONObject))throw new IOException("Invalid day object");
            JSONObject day=restoredDays.getJSONObject(date);
            for(String meal:new String[]{"breakfast","lunch","afternoon_tea","dinner","snack"})if(day.has(meal)&&!(day.opt(meal) instanceof JSONObject))throw new IOException("Invalid meal object");
        }
        return new Staged(restoredDays,restoredProfile,files,names);
    }
    private static JSONObject object(String text)throws Exception {
        JSONTokener parser=new JSONTokener(text);Object value=parser.nextValue();
        if(!(value instanceof JSONObject)||parser.nextClean()!=0)throw new IOException("Invalid backup JSON object");
        return (JSONObject)value;
    }
    static void deleteOwnedTree(File root)throws IOException {
        if(root==null||!root.exists())return;
        root=child(root.getParentFile(),root.getName());
        if(root.isDirectory()){File[] files=root.listFiles();if(files==null)throw new IOException("Cannot list backup directory");for(File file:files)deleteOwnedTree(file);}
        if(!root.delete())throw new IOException("Cannot remove backup temporary file");
    }
}
