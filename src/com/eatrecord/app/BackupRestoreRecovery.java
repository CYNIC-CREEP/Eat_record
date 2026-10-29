package com.eatrecord.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.File;
import java.io.IOException;

/** A pending marker contains only the journal basename. Sensitive state stays in noBackupFilesDir. */
final class BackupRestoreRecovery {
    static final String MARKER=NutritionStore.RESTORE_MARKER;
    static final String PREFIX="restore-journal-";
    static void recover(Context context){
        synchronized(NutritionStore.LOCK){
            SharedPreferences prefs=context.getSharedPreferences(NutritionStore.PREFS,0);
            String name=prefs.getString(MARKER,"");
            if(name.isEmpty())return;
            try{
                if(!name.startsWith(PREFIX)||!BackupArchive.safeName(name))throw new IOException("Invalid restore marker");
                File journal=BackupArchive.child(context.getNoBackupFilesDir(),name);
                String[] state=BackupFileTransaction.readState(journal);
                BackupFileTransaction.rollback(context.getFilesDir(),journal);
                if(!prefs.edit().putString(NutritionStore.DAYS,state[0]).putString(NutritionStore.PROFILE,state[1]).remove(MARKER).commit()){
                    // A failed disk commit may already have removed the in-memory marker.
                    prefs.edit().putString(MARKER,name).commit();
                    throw new IOException("Cannot recover restore state");
                }
                try{BackupArchive.deleteOwnedTree(journal);}catch(IOException ignored){}
            }catch(Exception error){throw new IllegalStateException("Interrupted restore requires recovery before records can be used",error);}
        }
    }
}
