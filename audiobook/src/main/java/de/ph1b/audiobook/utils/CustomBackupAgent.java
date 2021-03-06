package de.ph1b.audiobook.utils;

import android.app.backup.BackupAgentHelper;
import android.app.backup.SharedPreferencesBackupHelper;

import de.ph1b.audiobook.R;


public class CustomBackupAgent extends BackupAgentHelper {

    private static final String BACKUP_KEY = "BACKUP_KEY";

    @Override
    public void onCreate() {

        String resumeOnReplug = getString(R.string.pref_key_resume_on_replug);
        String coverOnInternet = getString(R.string.pref_key_cover_on_internet);
        String seekTime = getString(R.string.pref_key_seek_time);
        String sleepTime = getString(R.string.pref_key_sleep_time);
        String trackToEnd = getString(R.string.pref_key_track_to_end);

        SharedPreferencesBackupHelper helper = new SharedPreferencesBackupHelper(this,
                resumeOnReplug, coverOnInternet, seekTime, sleepTime, trackToEnd);
        addHelper(BACKUP_KEY, helper);
    }
}
