package com.eatrecord.app;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Real temporary files and injected rename-stage failures; never touches an Android user's records. */
public final class BackupReliabilityTest {
    private static int checks;
    private static void expect(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static byte[] zip(String... entries)throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(ZipOutputStream zip=new ZipOutputStream(bytes)){
            for(int i=0;i<entries.length;i+=2){zip.putNextEntry(new ZipEntry(entries[i]));zip.write(entries[i+1].getBytes(StandardCharsets.UTF_8));zip.closeEntry();}
        }return bytes.toByteArray();
    }
    private static void bad(File root,byte[] bytes,String label)throws Exception {
        File stage=new File(root,"bad-"+UUID.randomUUID());boolean failed=false;
        try{BackupArchive.extract(new ByteArrayInputStream(bytes),stage);}catch(Exception expected){failed=true;}finally{BackupArchive.deleteOwnedTree(stage);}
        expect(failed,label+" was accepted");
    }
    private static void put(File dir,String name,String text)throws Exception{Files.write(new File(dir,name).toPath(),text.getBytes(StandardCharsets.UTF_8));}
    private static String read(File dir,String name)throws Exception{return new String(Files.readAllBytes(new File(dir,name).toPath()),StandardCharsets.UTF_8);}
    public static void main(String[] args)throws Exception {
        File root=Files.createTempDirectory("eat-backup-test-").toFile();
        try{
            byte[] valid=zip("days.json","{\"2026-10-28\":{\"lunch\":{\"text\":\"meal\"}}}","profile.json","{\"nickname\":\"test\"}","files/photo.jpg","new");
            File stage=new File(root,"valid");BackupArchive.Staged result=BackupArchive.extract(new ByteArrayInputStream(valid),stage);
            expect(result.days.has("2026-10-28")&&result.profile.has("nickname")&&"new".equals(read(result.files,"photo.jpg")),"Valid legacy ZIP failed");
            bad(root,zip("days.json","{}"),"Missing JSON");
            bad(root,zip("days.json","[]","profile.json","{}"),"Array days");
            bad(root,zip("days.json","{} trailing","profile.json","{}"),"Trailing JSON data");
            bad(root,zip("days.json","{\"day\":1}","profile.json","{}"),"Wrong day shape");
            bad(root,zip("days.json","{\"day\":{\"lunch\":3}}","profile.json","{}"),"Wrong meal shape");
            for(String path:new String[]{"files/../outside","files/sub/image","files/..\\outside","/absolute","files/.hidden","files/x:y","files/..","unknown.json"})bad(root,zip("days.json","{}","profile.json","{}",path,"x"),"Unsafe path "+path);
            byte[] duplicate=zip("days.json","{}","profile.json","{}","next.json","{}");
            String binary=new String(duplicate,StandardCharsets.ISO_8859_1).replace("next.json","days.json");bad(root,binary.getBytes(StandardCharsets.ISO_8859_1),"Duplicate JSON");
            bad(root,Arrays.copyOf(valid,70),"Truncated member");
            byte[] declaredLarge=valid.clone();declaredLarge[6]=(byte)(declaredLarge[6]&~8);long tooLarge=BackupArchive.MAX_JSON+1;for(int i=0;i<4;i++)declaredLarge[22+i]=(byte)(tooLarge>>>(8*i));bad(root,declaredLarge,"Oversize JSON header");
            File low=new File(root,"space");boolean spaceFailed=false;try{BackupArchive.extract(new ByteArrayInputStream(valid),low,at->1);}catch(IOException expected){spaceFailed=true;}expect(spaceFailed,"Full disk accepted");BackupArchive.deleteOwnedTree(low);
            File source=new File(root,"source");source.mkdir();put(source,"photo.jpg","media");ByteArrayOutputStream output=new ByteArrayOutputStream();BackupArchive.write(output,"{}","{}",Collections.singletonList(new File(source,"photo.jpg")));
            File roundtrip=new File(root,"roundtrip");expect("media".equals(read(BackupArchive.extract(new ByteArrayInputStream(output.toByteArray()),roundtrip).files,"photo.jpg")),"Streaming roundtrip failed");
            // Every possible failure point, including an original removed-only file, must roll back.
            for(String failStep:new String[]{"before","preserved","installed"})for(String failName:new String[]{"old.jpg","same.jpg","new.jpg"}){
                File live=new File(root,"live-"+UUID.randomUUID()),journal=new File(root,"journal-"+UUID.randomUUID());live.mkdir();journal.mkdir();new File(journal,"incoming").mkdir();
                put(live,"old.jpg","old-only");put(live,"same.jpg","old-same");put(live,"untouched.jpg","untouched");put(new File(journal,"incoming"),"same.jpg","new-same");put(new File(journal,"incoming"),"new.jpg","new-only");
                BackupFileTransaction.saveState(journal,"old days","old profile");
                BackupFileTransaction tx=new BackupFileTransaction(live,journal,Arrays.asList("same.jpg","new.jpg"),Arrays.asList("old.jpg","same.jpg"),(step,name)->{if(step.equals(failStep)&&name.equals(failName))throw new IOException("injected");});
                boolean failed=false;try{tx.install();}catch(IOException expected){failed=true;}expect(failed,"Fault injection did not fire");
                // A fresh instance simulates recovery after the installing process died.
                BackupFileTransaction.rollback(live,journal);BackupFileTransaction.rollback(live,journal);
                expect("old-only".equals(read(live,"old.jpg"))&&"old-same".equals(read(live,"same.jpg"))&&!new File(live,"new.jpg").exists()&&"untouched".equals(read(live,"untouched.jpg")),"Rollback lost existing media");
                String[] state=BackupFileTransaction.readState(journal);expect("old days".equals(state[0])&&"old profile".equals(state[1]),"Recovery state changed");
            }
            File live=new File(root,"success-live"),journal=new File(root,"success-journal");live.mkdir();journal.mkdir();new File(journal,"incoming").mkdir();put(live,"same.jpg","old");put(new File(journal,"incoming"),"same.jpg","new");
            BackupFileTransaction tx=new BackupFileTransaction(live,journal,Collections.singletonList("same.jpg"),Collections.emptyList());tx.install();expect("new".equals(read(live,"same.jpg")),"Successful install failed");
            tx.rollback();expect("old".equals(read(live,"same.jpg")),"Preference commit failure rollback failed");
            // Opaque file contents are copied in fixed-size chunks, not retained by the archive writer.
            File large=new File(source,"large.bin");try(RandomAccessFile file=new RandomAccessFile(large,"rw")){file.setLength(32L*1024*1024);}
            final long[] sink={0};BackupArchive.write(new OutputStream(){public void write(int value){sink[0]++;}public void write(byte[] bytes,int start,int count){sink[0]+=count;}},"{}","{}",Collections.singletonList(large));expect(sink[0]>0,"Streaming writer did not produce output");
            System.out.println("PASS: "+checks+" bounded archive/path/shape/space/streaming and journal rollback/recovery checks");
        }finally{BackupArchive.deleteOwnedTree(root);}
    }
}
