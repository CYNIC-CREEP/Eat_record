package com.eatrecord.app;

import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.io.IOException;
import java.util.Arrays;

public final class RetiredRecognitionCacheTest {
    private static int checks;
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    private static void clean(Path path)throws IOException{
        Files.walkFileTree(path,new SimpleFileVisitor<Path>(){
            @Override public FileVisitResult visitFile(Path p,BasicFileAttributes attrs)throws IOException{Files.delete(p);return FileVisitResult.CONTINUE;}
            @Override public FileVisitResult postVisitDirectory(Path p,IOException e)throws IOException{if(e!=null)throw e;Files.delete(p);return FileVisitResult.CONTINUE;}
        });
    }
    public static void main(String[] args)throws Exception {
        Path base=Files.createTempDirectory("retired-recognition-test-"),files=Files.createDirectory(base.resolve("files")),outside=Files.createDirectory(base.resolve("outside"));
        byte[] saved={1,2,3,4};Path diary=files.resolve("days_json"),photo=files.resolve("photo.jpg"),other=outside.resolve("keep.jpg");Files.write(diary,saved);Files.write(photo,saved);Files.write(other,saved);
        try{
            RetiredRecognitionCache.remove(files.toFile());check(Files.exists(diary)&&Files.exists(photo),"Absent cache leaves app files intact");
            Path old=Files.createDirectories(files.resolve("offline_food/qwen35_2b"));Files.write(old.resolve("config.json"),saved);Files.write(old.resolve("weight.partial"),saved);
            RetiredRecognitionCache.remove(files.toFile());check(!Files.exists(files.resolve("offline_food")),"Only retired cache subtree is removed");
            check(Arrays.equals(Files.readAllBytes(diary),saved)&&Arrays.equals(Files.readAllBytes(photo),saved),"Diary and media bytes are preserved");
            RetiredRecognitionCache.remove(files.toFile());check(Files.exists(other),"Repeated cleanup is harmless");
            boolean links=true;
            try{old=Files.createDirectories(files.resolve("offline_food"));Files.createSymbolicLink(old.resolve("external"),outside);}
            catch(UnsupportedOperationException|IOException|SecurityException unavailable){links=false;}
            if(links){
                RetiredRecognitionCache.remove(files.toFile());check(Files.isDirectory(outside)&&Arrays.equals(Files.readAllBytes(other),saved),"Nested symlink target is not traversed");
                Files.createSymbolicLink(files.resolve("offline_food"),outside);RetiredRecognitionCache.remove(files.toFile());
                check(!Files.exists(files.resolve("offline_food"),LinkOption.NOFOLLOW_LINKS)&&Files.isDirectory(outside)&&Files.exists(other),"Root symlink is unlinked without deleting target");
            }
            System.out.println("PASS: "+checks+" scoped retired-cache cleanup checks (symlinks="+links+")");
        }finally{clean(base);}
    }
}
