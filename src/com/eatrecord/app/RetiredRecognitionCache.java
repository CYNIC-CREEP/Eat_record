package com.eatrecord.app;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.FileVisitResult;
import java.nio.file.attribute.BasicFileAttributes;

/** Upgrade-only scoped cleanup; symlink targets and every other app file stay intact. */
final class RetiredRecognitionCache {
    private RetiredRecognitionCache() { }
    static void remove(File filesDirectory)throws IOException {
        final File root=filesDirectory.getCanonicalFile();
        final Path target=new File(root,"offline_food").toPath();
        if(!Files.exists(target,LinkOption.NOFOLLOW_LINKS))return;
        if(Files.isSymbolicLink(target)){Files.deleteIfExists(target);return;}
        if(!target.toFile().getCanonicalFile().getParentFile().equals(root))throw new IOException("Unexpected retired cache parent");
        Files.walkFileTree(target,new SimpleFileVisitor<Path>(){
            private void owned(Path path)throws IOException{
                if(!path.toAbsolutePath().normalize().startsWith(target))throw new IOException("Retired cache escaped scope");
            }
            @Override public FileVisitResult preVisitDirectory(Path path,BasicFileAttributes attrs)throws IOException{owned(path);return FileVisitResult.CONTINUE;}
            @Override public FileVisitResult visitFile(Path path,BasicFileAttributes attrs)throws IOException{owned(path);Files.delete(path);return FileVisitResult.CONTINUE;}
            @Override public FileVisitResult postVisitDirectory(Path path,IOException error)throws IOException{owned(path);if(error!=null)throw error;Files.delete(path);return FileVisitResult.CONTINUE;}
        });
    }
}
