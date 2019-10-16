/*
 * Copyright (c) 2015 Qualcomm Technologies, Inc.  All Rights Reserved.
 * Qualcomm Technologies Proprietary and Confidential.
 *
 * Not a Contribution, Apache license notifications and license are retained
 * for attribution purposes only.
 */

package com.android.backup;

import java.io.File;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.storage.StorageEventListener;
import android.os.storage.StorageManager;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.preference.Preference.OnPreferenceClickListener;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Toast;

import javax.sip.InvalidArgumentException;

import static com.android.backup.HelpActivity.*;
import static com.android.backup.BackupUtils.*;

public class SettingsActivity extends PreferenceActivity implements
        Preference.OnPreferenceClickListener {

    private static final int REQUEST_BACKUP_PATH = 101;
    private static final int REQUEST_RESTORE_PATH = 102;
    private static String TAG = "SettingsActivity";
    private static final String KEY_BACKUP = "backup_location";
    private static final String KEY_RESTORE = "restore_location";
    private static final String KEY_HELP = "settings_help";
    private static final String BACKUP_TAG_PATH = "/backup/App/backuptag";
    private static final String RESTORE_TAG_PATH = "/backup/App/restoretag";

    /** If there is no setting in the provider, use this. */
    private static final int FALLBACK_BACKUP = 0;
    private static final int FALLBACK_RESTORE = 0;

    private static final int PATH_INTERNAL = 0;
    private static final int PATH_EXTERNAL = 1;

    private Preference mBackupPreference;
    private Preference mRestorePreference;
    private Preference mHelpPreference;

    private SharedPreferences sharedPreferences;
    private StorageEventListener mStorageListener;
    private StorageManager mStorageManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final ContentResolver resolver = this.getContentResolver();
        addPreferencesFromResource(R.xml.settings_preference);
        sharedPreferences = getSharedPreferences(BackupUtils.LOCATION_PREFERENCE_NAME,
              Context.MODE_PRIVATE);

        mBackupPreference = (Preference) findPreference(KEY_BACKUP);
        mRestorePreference = (Preference) findPreference(KEY_RESTORE);
        updateSummary();
        checkPathAndWarn();
        mHelpPreference = (Preference) findPreference(KEY_HELP);

        mBackupPreference.setOnPreferenceClickListener(this);
        mRestorePreference.setOnPreferenceClickListener(this);
        mHelpPreference.setOnPreferenceClickListener(this);

        mStorageListener = new StorageEventListener() {
            @Override
            public void onStorageStateChanged(String path, String oldState, String newState) {
                //update backup and restore status for the pathes in settings
                Log.d(TAG, "onStorageStateChanged(), path: " + path +
                        ", oldState: " + oldState + ", newState: " + newState);
                if (!(path != null && path.equals(BackupUtils.getSDPath(getApplicationContext())))) {
                    return;
                }
                if (newState.equalsIgnoreCase(Environment.MEDIA_MOUNTED) ||
                        newState.equals(Environment.MEDIA_BAD_REMOVAL) ||
                        newState.equals(Environment.MEDIA_UNMOUNTED)) {
                    updateSummary();
                }
            }
        };
        mStorageManager = StorageManager.from(SettingsActivity.this);
        mStorageManager.registerListener(mStorageListener);
    }

    private void updateSummary() {
        String backUpPath = BackupUtils.getBackupPath(this);
        mBackupPreference.setSummary(backUpPath);

        String restorePath = BackupUtils.getRestorePath(this);
        mRestorePreference.setSummary(restorePath);
    }

    private void checkPathAndWarn(){
        String backUpPath = BackupUtils.getBackupPath(this);
        String restorePath = BackupUtils.getRestorePath(this);
        if (!BackupUtils.isDirAvalible(backUpPath)){
            Toast.makeText(SettingsActivity.this,
                getString(R.string.backup_path_dont_exist,
                    backUpPath)
                , Toast.LENGTH_SHORT).show();
        }
        if (!BackupUtils.isDirAvalible(restorePath)){
            Toast.makeText(SettingsActivity.this,
                getString(R.string.restore_path_dont_exist,
                    restorePath)
                , Toast.LENGTH_SHORT).show();

        }
    }

    @Override
    public void onDestroy() {
        if (mStorageManager != null && mStorageListener != null) {
            mStorageManager.unregisterListener(mStorageListener);
        }
        super.onDestroy();
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        if (preference.getKey().equals(KEY_BACKUP)) {
            startPickFolderActivity(BackupUtils.getBackupPath(this), REQUEST_BACKUP_PATH);
            return true;
        } else if (preference.getKey().equals(KEY_RESTORE)) {
            startPickFolderActivity(BackupUtils.getRestorePath(this), REQUEST_RESTORE_PATH);
            return true;
        } else if (preference.getKey().equals(KEY_HELP)) {
            Intent intent = new Intent();
            intent.setClass(SettingsActivity.this, HelpActivity.class);
            intent.putExtra(HELP_TYPE_KEY, TYPE_SETTING);
            startActivity(intent);
            return true;
        }
        return false;
    }

    private void startPickFolderActivity(String defaultData, int requestCode) {
        Intent i = new Intent();
        i.setAction(Intent.ACTION_PICK);
        File defaultDir = new File(defaultData);
        Uri data =  Uri.fromFile(defaultDir);
        data = data.buildUpon().scheme("directory").build();
        i.setData(data);
        try {
            startActivityForResult(i, requestCode);
        } catch (ActivityNotFoundException ex) {
            Log.e(TAG, "There is not application can resolve the pick directory intent");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_BACKUP_PATH:
                if (resultCode == RESULT_OK) {
                    String path = data.getData().getPath();
                    /**
                    if (path.startsWith("/storage/emulated/0")) {
                        path = path.replace("/storage/emulated/0", BackupUtils.strInternalPath);
                    }**/
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putString(BackupUtils.KEY_BACKUP_LOCATION, path);
                    editor.commit();
                    mBackupPreference.setSummary(path);
                }
                break;
            case REQUEST_RESTORE_PATH:
                if (resultCode == RESULT_OK) {
                    String path = data.getData().getPath();
                    /**
                    if (path.startsWith("/storage/emulated/0")) {
                        path = path.replace("/storage/emulated/0", BackupUtils.strInternalPath);
                    }**/
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putString(BackupUtils.KEY_RESTORE_LOCATION, path);
                    editor.commit();
                    mRestorePreference.setSummary(path);
                }
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
    }
}
