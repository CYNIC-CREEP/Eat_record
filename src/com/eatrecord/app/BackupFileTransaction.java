package com.eatrecord.app;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Durable same-filesystem media replacement. The caller owns the JSON commit and recovery marker. */
final class BackupFileTransaction {
    interface Fault { void at(String step,String name)throws IOException; }
    static final Fault NONE=(step,name)->{};
    final File live,journal,incoming,rollback;
    private final LinkedHashMap<String,Boolean> original=new LinkedHashMap<>();
    private final Set<String> imported=new LinkedHashSet<>();
    private final Fault fault;
    BackupFileTransaction(File live,File journal,Collection<String> install,Collection<String> remove)throws IOException{this(live,journal,install,remove,NONE);}
    BackupFileTransaction(File live,File journal,Collection<String> install,Collection<String> remove,Fault fault)throws IOException {
        this.live=live;this.journal=journal;this.incoming=new File(journal,"incoming");this.rollback=new File(journal,"rollback");this.fault=fault;
        for(String name:install){File file=BackupArchive.child(incoming,name);if(!file.isFile())throw new IOException("Staged media missing");imported.add(name);}
        LinkedHashSet<String> touched=new LinkedHashSet<>(remove);touched.addAll(imported);
        for(String name:touched){File file=BackupArchive.child(live,name);if(file.exists()&&!file.isFile())throw new IOException("Live media is not a file");original.put(name,file.isFile());}
        if(!rollback.mkdir())throw new IOException("Cannot prepare rollback");
        try(FileOutputStream file=new FileOutputStream(new File(journal,"plan"));DataOutputStream out=new DataOutputStream(file)){
            out.writeInt(1);out.writeInt(original.size());for(Map.Entry<String,Boolean> entry:original.entrySet()){out.writeUTF(entry.getKey());out.writeBoolean(entry.getValue());}out.flush();file.getFD().sync();
        }
    }
    private BackupFileTransaction(File live,File journal)throws IOException {
        this.live=live;this.journal=journal;incoming=new File(journal,"incoming");rollback=new File(journal,"rollback");fault=NONE;
        try(DataInputStream in=new DataInputStream(new FileInputStream(new File(journal,"plan")))){
            if(in.readInt()!=1)throw new IOException("Invalid restore journal");int count=in.readInt();if(count<0||count>BackupArchive.MAX_ENTRIES)throw new IOException("Invalid restore journal count");
            for(int i=0;i<count;i++){String name=in.readUTF();BackupArchive.child(live,name);if(original.put(name,in.readBoolean())!=null)throw new IOException("Duplicate journal item");}
        }
    }
    void install()throws IOException {
        for(Map.Entry<String,Boolean> entry:original.entrySet()){
            String name=entry.getKey();File destination=BackupArchive.child(live,name);
            fault.at("before",name);
            if(entry.getValue()&&!destination.renameTo(BackupArchive.child(rollback,name)))throw new IOException("Cannot preserve old media");
            fault.at("preserved",name);
            if(imported.contains(name)&&!BackupArchive.child(incoming,name).renameTo(destination))throw new IOException("Cannot install restored media");
            fault.at("installed",name);
        }
    }
    void rollback()throws IOException {
        IOException failure=null;
        for(Map.Entry<String,Boolean> entry:original.entrySet())try{
            File destination=BackupArchive.child(live,entry.getKey()),old=BackupArchive.child(rollback,entry.getKey());
            if(old.exists()){
                if(destination.exists()&&!destination.delete())throw new IOException("Cannot remove uncommitted media");
                if(!old.renameTo(destination))throw new IOException("Cannot restore old media");
            }else if(!entry.getValue()){
                if(destination.exists()&&!destination.delete())throw new IOException("Cannot remove uncommitted media");
            }else if(!destination.isFile())throw new IOException("Old media missing from journal");
        }catch(IOException error){if(failure==null)failure=error;else failure.addSuppressed(error);}
        if(failure!=null)throw failure;
    }
    static void rollback(File live,File journal)throws IOException{new BackupFileTransaction(live,journal).rollback();}
    static void saveState(File journal,String days,String profile)throws IOException {
        try(FileOutputStream file=new FileOutputStream(new File(journal,"state"));DataOutputStream out=new DataOutputStream(file)){
            writeText(out,days);writeText(out,profile);out.flush();file.getFD().sync();
        }
    }
    static String[] readState(File journal)throws IOException {
        try(DataInputStream in=new DataInputStream(new FileInputStream(new File(journal,"state")))){return new String[]{readText(in),readText(in)};}
    }
    private static void writeText(DataOutputStream out,String text)throws IOException{byte[] bytes=text.getBytes(StandardCharsets.UTF_8);if(bytes.length>BackupArchive.MAX_JSON)throw new IOException("State exceeds restore limit");out.writeInt(bytes.length);out.write(bytes);}
    private static String readText(DataInputStream in)throws IOException{int length=in.readInt();if(length<0||length>BackupArchive.MAX_JSON)throw new IOException("Invalid restore state length");byte[] bytes=new byte[length];in.readFully(bytes);return new String(bytes,StandardCharsets.UTF_8);}
}
