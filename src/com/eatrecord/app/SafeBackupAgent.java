package com.eatrecord.app;

import android.app.backup.BackupAgent;
import android.app.backup.FullBackupDataOutput;
import android.app.backup.BackupDataInput;
import android.app.backup.BackupDataOutput;
import android.os.ParcelFileDescriptor;
import java.io.IOException;

/** Final migration barrier before Android's full backup applies the XML exclusions. */
public final class SafeBackupAgent extends BackupAgent {
    @Override public void onFullBackup(FullBackupDataOutput output) throws IOException {
        synchronized (NutritionStore.LOCK) {
            BackupRestoreRecovery.recover(this);
            PrivateProfileStore.prepareForBackup(this);
            super.onFullBackup(output);
        }
    }

    @Override public void onRestoreFinished() {
        super.onRestoreFinished();
        try { PrivateProfileStore.finishSystemRestore(this); }
        catch (IOException failed) { throw new IllegalStateException("Restored settings could not be secured", failed); }
    }

    // The app uses full-file backup. Never emit a second key/value path containing a merged profile.
    @Override public void onBackup(ParcelFileDescriptor oldState, BackupDataOutput data,
                                   ParcelFileDescriptor newState) throws IOException {
        BackupRestoreRecovery.recover(this);
        PrivateProfileStore.prepareForBackup(this);
    }

    @Override public void onRestore(BackupDataInput data, int version,
                                    ParcelFileDescriptor newState) throws IOException {
        PrivateProfileStore.finishSystemRestore(this);
    }
}
