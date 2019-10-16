/*
 * Copyright (c) 2013 Qualcomm Technologies, Inc.  All Rights Reserved.
 * Qualcomm Technologies Proprietary and Confidential.
 *
 * Not a Contribution, Apache license notifications and license are retained
 * for attribution purposes only.
 */

/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.backup;

import android.app.ExpandableListActivity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Window;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnClickListener;
import android.content.ServiceConnection;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AbsListView.LayoutParams;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.app.ProgressDialog;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.os.SystemService;
import android.net.Uri;
import android.content.ContentUris;
import android.text.format.DateFormat;
import java.io.File;
import android.os.FileUtils;
import java.io.IOException;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import android.view.KeyEvent;
import android.view.View.MeasureSpec;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.util.Log;
import android.os.SystemProperties;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;

import com.android.backup.BackupUtils;
import com.android.backup.PinnedHeaderExpandableListView;
import com.android.backup.PinnedHeaderExpandableListView.UpdateHeadViewListener;

import static com.android.backup.BackupUtils.BACKUP_TYPE_ALL;
import static com.android.backup.BackupUtils.BACKUP_TYPE_APP;
import static com.android.backup.BackupUtils.BACKUP_TYPE_CALENDAR;
import static com.android.backup.BackupUtils.BACKUP_TYPE_CONTACTS;
import static com.android.backup.BackupUtils.BACKUP_TYPE_MMS;
import static com.android.backup.BackupUtils.BACKUP_TYPE_PICTURE;
import static com.android.backup.BackupUtils.BACKUP_TYPE_SMS;
import static com.android.backup.BackupUtils.EVENT_BACKUP_RESULT;
import static com.android.backup.BackupUtils.EVENT_FILE_CREATE_ERR;
import static com.android.backup.BackupUtils.EVENT_INIT_PROGRESS_TITLE;
import static com.android.backup.BackupUtils.EVENT_RESUME_BACKUP_THREAD;
import static com.android.backup.BackupUtils.EVENT_SDCARD_NO_SPACE;
import static com.android.backup.BackupUtils.EVENT_SET_PROGRESS_VALUE;
import static com.android.backup.BackupUtils.EVENT_STOP_BACKUP;
import static com.android.backup.BackupUtils.EVENT_SDCARD_FULL;
import static com.android.backup.BackupUtils.SLOT_ALL;
import static com.android.backup.BackupUtils.SLOT_1;
import static com.android.backup.BackupUtils.SLOT_2;
import static com.android.backup.HelpActivity.HELP_TYPE_KEY;
import static com.android.backup.HelpActivity.TYPE_SETTING;

import static com.android.backup.HelpActivity.*;
import android.os.Parcelable;
import android.os.Parcel;

public class BackupActivity extends ExpandableListActivity
        implements View.OnClickListener, UpdateHeadViewListener {
    private static boolean DBG = true;
    private static final String TAG = "BackupActivity";
    private static final boolean LOCAL_DEBUG = true;

    private static final String BACKUP_EXTRA_DIR = "backup_dir";
    private static final String BACKUP_EXTRA_DATA_CHK = "data_checked";
    private static final String BACKUP_EXTRA_DATA_CNT = "data_cnt";
    private static final String BACKUP_EXTRA_MSG_SLOT = "msg_slot";
    private static final String BACKUP_EXTRA_APP_SIZE = "app_size";
    private static final String BACKUP_EXTRA_APP_CHK = "app_checked";
    private static final String BACKUP_EXTRA_APP_INFO = "app_info";
    private static final String BACKUP_EXTRA_PATH = "backup_path";
    private static final String BACKUP_DATA = "/backup/Data";
    private static final String BACKUP_FOLDER = "/backup";
    private static final String BACKUP_APP = "/backup/App";

    private static final int MENU_MULTISEL_OK = 1;
    private static final int BACKUP_GROUP_SYSTEM_DATA = 0;
    private static final int BACKUP_GROUP_APPS = 1;

    private static final int CONTACTS_INDEX_ID = 0;
    private static final int SMS_INDEX_ID = 1;
    private static final int MMS_INDEX_ID = 2;
    private static final int CALENDAR_INDEX_ID = 3;

    private static final int SIM1_SMS_INDEX_ID = 1;
    private static final int SIM2_SMS_INDEX_ID = 2;
    private static final int SIM1_MMS_INDEX_ID = 3;
    private static final int SIM2_MMS_INDEX_ID = 4;
    private static final int CALENDAR_MSIM_INDEX_ID = 5;
    private static final int OUT_PUT_BUFFER_SIZE = 1024 * 4;

    private static final String KEY_SYSTEM_DATA_GROUP = "system_data_group";
    private static final String KEY_APP_GROUP = "app_group";
    private static final String KEY_GROUP_EXPAND = "group_expaned";
    private static final String KEY_BACKUP_CONTACTS = "bak_contact";
    private static final String KEY_BACKUP_SMS1 = "bak_sim1_sms";
    private static final String KEY_BACKUP_MMS1 = "bak_sim1_mms";
    private static final String KEY_BACKUP_SMS2 = "bak_sim2_sms";
    private static final String KEY_BACKUP_MMS2 = "bak_sim2_mms";
    private static final String KEY_BACKUP_CALENDAR = "bak_calendar";

    private static final String MAP_KEY_KEY = "key";
    private static final String MAP_KEY_NAME = "Name";
    private static final String MAP_KEY_CHECKED = "Checked";
    private static final String MAP_KEY_PERCENT = "Percent";

    public static final int INVALID_ARGUMENT = 0;
    private static final int BACKUP_RESULT_FAIL = 0;
    private static final int BACKUP_RESULT_OK = 1;
    private static final int FALLBACK_BACKUP = 0;
    private static final int PATH_INTERNAL = 0;
    private static final int PATH_EXTERNAL = 1;

    private String mBackupPath;
    private String mBackupDataDir;
    private String mBackupRootDir;
    private String mBackupAppDir;

    private final Object mBackupLock = new Object();

    private static String mCurrentDir;
    private Showconnectprogress mSendProgressDialog = null;
    private OperateThread mOperateThread = null;
    private Handler mHandler;
    private boolean mCancelBackupFlag = false;

    private static PowerManager.WakeLock sWakeLock;
    private static final Object mWakeLockSync = new Object();
    private boolean isBackupData = false;
    private ArrayList<String> dirArray = new ArrayList<String>();

    private ArrayList<AppInfo> mAppList = null;
    private ArrayList<AppInfo> mDataList = null;

    private PinnedHeaderExpandableListView mListView;
    private BackupAdapter mListAdaptor;
    private CheckBox mCheckBox;
    private Button mButtonBackup;

    private static final String PREFERENCE_BACKUP_ACTIVITY = "Preference_Backup_Activity";
    private static final String PREFERENCE_BACKUP_NO_SDCARD_DIALOG_KEY = "No_Sdcard_Dialog_Key";
    private static final String PREFERENCE_BACKUP_NOT_SUPPORT_DIALOG_KEY = "Not_Support_Dialog_Key";

    private boolean imageStatus[] = {false, false};
    private List<Map<String, String>> mGroupData;
    private List<List<Map<String, String>>> mChildData;
    private HashMap<String, Object> mStoreStatusMap = null;
    private Bundle mRestoreBundle = null;

    private class BackupHandler extends Handler {

        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_STOP_BACKUP: {
                    Log.d(TAG, "EVENT_STOP_BACKUP");
                    break;
                }

                case EVENT_SDCARD_NO_SPACE: {
                    Log.d(TAG, "EVENT_SDCARD_NO_SPACE");
                    if (mSendProgressDialog != null) {
                        mSendProgressDialog.dismiss();
                        mSendProgressDialog = null;
                    }
                    Toast.makeText(BackupActivity.this, R.string.sdcard_no_space,
                            Toast.LENGTH_SHORT).show();
                    finish();
                    break;
                }
                case EVENT_FILE_CREATE_ERR: {
                    Log.d(TAG, "EVENT_FILE_CREATE_ERR");
                    if (mSendProgressDialog != null) {
                        mSendProgressDialog.dismiss();
                        mSendProgressDialog = null;
                    }
                    Toast.makeText(BackupActivity.this, R.string.create_file_error,
                            Toast.LENGTH_SHORT).show();
                    finish();
                    break;
                }
                case EVENT_INIT_PROGRESS_TITLE: {
                    Log.d(TAG, "EVENT_INIT_PROGRESS_TITLE");
                    int type = msg.arg1;
                    Log.d(TAG, "Msg.arg1 = " + msg.arg1);
                    String title = null;
                    switch (type) {
                        case BACKUP_TYPE_CONTACTS:
                            title = getString(R.string.backup_contact_title);
                            break;
                        case BACKUP_TYPE_MMS:
                            title = getString(R.string.backup_mms_title);
                            break;
                        case BACKUP_TYPE_SMS:
                            title = getString(R.string.backup_sms_title);
                            break;
                        case BACKUP_TYPE_CALENDAR:
                            title = getString(R.string.backup_calendar_title);
                            break;
                        case BACKUP_TYPE_PICTURE:
                            title = getString(R.string.backup_picture_title);
                            break;
                        case BACKUP_TYPE_APP:
                            title = getString(R.string.backup_app_title);
                            break;
                        default:
                            title = "Init Progress Title Error";
                            break;
                    }
                    updateProgressDialog(title, msg.arg2);
                    break;
                }
                case EVENT_SET_PROGRESS_VALUE: {
                    Log.d(TAG, "EVENT_SET_PROGRESS_VALUE msg.arg1:" + msg.arg1);
                    if (msg.arg1 != INVALID_ARGUMENT) {
                        updateProgressValue(msg.arg1);
                    } else {
                        updateProgressIncrementValue(msg.arg2);
                    }
                    break;
                }
                case EVENT_RESUME_BACKUP_THREAD: {
                    Log.d(TAG, "EVENT_RESUME_BACKUP_THREAD");
                    notifyBackupThread();
                    break;
                }
                case EVENT_SDCARD_FULL: {
                    Log.d(TAG, "EVENT_SDCARD_FULL");
                    Toast.makeText(BackupActivity.this,
                            R.string.sms_full_cmcc, Toast.LENGTH_LONG).show();
                    break;
                }
                case EVENT_BACKUP_RESULT: {
                    Log.d(TAG, "EVENT_BACKUP_RESULT msg.arg1:" + msg.arg1
                            + " msg.arg: " + msg.arg2);
                    int result = msg.arg2;
                    String title = null;
                    String strResult = getString(result == BACKUP_RESULT_OK ? R.string.result_ok
                            : R.string.result_failed);
                    int type = msg.arg1;
                    switch (type) {
                        case BACKUP_TYPE_CONTACTS:
                        case BACKUP_TYPE_MMS:
                        case BACKUP_TYPE_SMS:
                        case BACKUP_TYPE_CALENDAR:
                            if (mSendProgressDialog != null) {
                                mSendProgressDialog.dismiss();
                                mSendProgressDialog = null;
                            }
                            if(result == BACKUP_RESULT_OK) {
                                updateBackupButton(true);
                                title = getString(R.string.backup_result_content,
                                            mBackupDataDir);
                            } else {
                                title = getString(R.string.result_failed);
                            }
                            break;
                        case BACKUP_TYPE_APP:
                            if (mSendProgressDialog != null) {
                                mSendProgressDialog.dismiss();
                                mSendProgressDialog = null;
                            }
                            if(result == BACKUP_RESULT_OK) {
                                updateBackupButton(true);
                                title = getString(R.string.backup_result_content,
                                            mBackupAppDir);
                            } else {
                                title = getString(R.string.result_failed);
                            }
                            break;
                        case BACKUP_TYPE_ALL:
                            if (mSendProgressDialog != null) {
                                mSendProgressDialog.dismiss();
                                mSendProgressDialog = null;
                            }
                            try {
                                if (BackupUtils.isInternalPath(mBackupPath)) {
                                    if (BackupUtils.isInternalFull()) {
                                        title = getString(R.string.no_space_warning);
                                    } else {
                                        if (result == BACKUP_RESULT_FAIL) {
                                            title = getString(R.string.result_failed);
                                        } else {
                                            updateBackupButton(true);
                                            title = getString(R.string.backup_result_content,
                                                        mBackupPath);
                                        }
                                    }
                                } else {
                                    if (result == BACKUP_RESULT_FAIL) {
                                        title = getString(R.string.result_failed);
                                    } else {
                                        updateBackupButton(true);
                                        title = getString(R.string.backup_result_content,
                                                    mBackupPath);
                                    }
                                }
                            } catch (IllegalArgumentException e) {
                                Log.v(TAG, "BackupActivity: " + e.getMessage());
                                Toast.makeText(BackupActivity.this, R.string.sdcard_unmounted,
                                        Toast.LENGTH_SHORT).show();
                                return;
                            }
                            break;
                    }
                    Log.d(TAG, "EVENT_BACKUP_RESULT title: " + title);
                    if (!mCancelBackupFlag) {
                        Toast.makeText(BackupActivity.this, title, Toast.LENGTH_SHORT)
                                .show();
                        /** Give user tips before backup, don't need prompt after backup
                        if(BackupUtils.isInternalPath(mBackupPath)){
                            final SharedPreferences sharedPreferences =
                                    getSharedPreferences(PREFERENCE_BACKUP_ACTIVITY, Context.MODE_PRIVATE);
                            if (sharedPreferences.getBoolean(PREFERENCE_BACKUP_NOT_SUPPORT_DIALOG_KEY, true)) {
                                LayoutInflater layoutInflater = LayoutInflater.from(BackupActivity.this);
                                View dialogView = layoutInflater.inflate(R.layout.dialog_style, null);
                                mCheckBox = (CheckBox) dialogView.findViewById(R.id.checkbox);
                                new AlertDialog.Builder(BackupActivity.this,
                                        android.R.style.Theme_DeviceDefault_Dialog_NoActionBar)
                                    .setMessage(R.string.not_support_sdcard_backup_abstract)
                                    .setView(dialogView)
                                    .setNegativeButton(R.string.button_cancel, new DialogInterface.OnClickListener() {

                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {

                                            if (mCheckBox.isChecked()) {
                                                SharedPreferences.Editor editor = sharedPreferences.edit();
                                                editor.putBoolean(PREFERENCE_BACKUP_NOT_SUPPORT_DIALOG_KEY, false);
                                                editor.commit();
                                            }
                                        }
                                    })
                                    .setPositiveButton(R.string.button_help, new DialogInterface.OnClickListener() {

                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {

                                            Intent intent = new Intent();
                                            intent.setClass(BackupActivity.this, HelpActivity.class);
                                            intent.putExtra(HELP_TYPE_KEY, TYPE_BACKUP);
                                            startActivity(intent);
                                        }
                                    }).create().show();
                            }
                        }**/
                    } else {
                        mCancelBackupFlag = false;
                        if (mRemoteBackupService != null) {
                            try {
                                mRemoteBackupService.setCancelBackupFlag(mCancelBackupFlag);
                            } catch (RemoteException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    break;
                }
                default:
                    Log.i(TAG, "unknow message: " + msg.what);
                    return;

            }
        }
    };

    private class OperateThread extends Thread {

        public OperateThread() {
            super("OperateThread");
        }

        public void run() {
            File rootdir = null;
            int[] ret = mListAdaptor.getChildCheckedStatus(0);
            if (ret[0] > 0)
                isBackupData = true;
            File rootDir1 = null;
            rootDir1 = new File(mBackupRootDir);

            if (!prepareBackupDir(rootDir1)) {
                mHandler.sendMessage(mHandler
                        .obtainMessage(EVENT_FILE_CREATE_ERR));
                return;
            }

            rootdir = new File(mBackupDataDir);
            if (!prepareBackupDir(rootdir)) {
                mHandler.sendMessage(mHandler
                        .obtainMessage(EVENT_FILE_CREATE_ERR));
                return;
            }

            long backupTime = System.currentTimeMillis();

            mCurrentDir = "/" + DateFormat.format("yyyyMMddkkmmss", backupTime).toString();
            if (DBG) {
                Log.d(TAG, "OperateThread directory: " + mCurrentDir);
            }
            File currentdir = new File(mBackupDataDir + mCurrentDir);
            if (isBackupData) {
                if (!prepareBackupDir(currentdir)) {
                    mHandler.sendMessage(mHandler.obtainMessage(EVENT_FILE_CREATE_ERR));
                    return;
                }
            }
            if (DBG) {
                Log.d(TAG, "System.Property = "
                        + SystemProperties.get("persist.sys.shflag", "0"));
            }

            Intent intent = new Intent(BackupActivity.this, BackupService.class);
            // put backup path
            intent.putExtra(BACKUP_EXTRA_DIR, mCurrentDir);
            // get system data item checked status
            boolean[] dataChecked = new boolean[ret[1]];
            dataChecked[0] = mListAdaptor.isChildChecked(BACKUP_GROUP_SYSTEM_DATA, CONTACTS_INDEX_ID);
            dataChecked[1] = isMmsChildChecked();
            dataChecked[2] = isSmsChildChecked();
            dataChecked[3] = isCalendarChildChecked();
            intent.putExtra(BACKUP_EXTRA_DATA_CHK, dataChecked);
            // get mms and sms slot number
            int[] msgSlot = new int[2];
            msgSlot[0] = getBackUpMmsSlot();
            msgSlot[1] = getBackUpSmsSlot();
            intent.putExtra(BACKUP_EXTRA_MSG_SLOT, msgSlot);

            // get installed applications checked status
            int[] status = mListAdaptor.getChildCheckedStatus(1);
            int size = status[0];
            intent.putExtra(BACKUP_EXTRA_APP_SIZE, size);
            if (size > 0) {
                boolean[] checkedArray = mListAdaptor.getChildCheckedList(BACKUP_GROUP_APPS, status[1]);
                intent.putExtra(BACKUP_EXTRA_APP_CHK, checkedArray);
            }

            int dataCnt = 0;
            for(int i = 0; i < 4; ++i) {
                if( mListAdaptor.isChildChecked(0, 0) )
                    dataCnt += 1;
            }
            intent.putExtra(BACKUP_EXTRA_DATA_CNT, dataCnt);
            intent.putParcelableArrayListExtra(BACKUP_EXTRA_APP_INFO, mAppList);
            intent.putExtra(BACKUP_EXTRA_PATH, mBackupPath);
            // start backup service
            startService(intent);
        }

        private int getBackUpMmsSlot() {
            if (BackupUtils.isMultiSimEnabled()) {
                if (mListAdaptor.isChildChecked(BACKUP_GROUP_SYSTEM_DATA, SIM1_MMS_INDEX_ID) &&
                        mListAdaptor.isChildChecked(BACKUP_GROUP_SYSTEM_DATA, SIM2_MMS_INDEX_ID)) {
                    return SLOT_ALL;
                } else {
                    if (mListAdaptor.isChildChecked(BACKUP_GROUP_SYSTEM_DATA, SIM1_MMS_INDEX_ID)) {
                        return SLOT_1;
                    } else {
                        return SLOT_2;
                    }
                }
            } else {
                return SLOT_ALL;
            }
        }

        private int getBackUpSmsSlot() {
            if (BackupUtils.isMultiSimEnabled()) {
                if (mListAdaptor.isChildChecked(BACKUP_GROUP_SYSTEM_DATA, SIM1_SMS_INDEX_ID) &&
                        mListAdaptor.isChildChecked(BACKUP_GROUP_SYSTEM_DATA, SIM2_SMS_INDEX_ID)) {
                    return SLOT_ALL;
                } else {
                    if (mListAdaptor.isChildChecked(BACKUP_GROUP_SYSTEM_DATA, SIM1_SMS_INDEX_ID)) {
                        return SLOT_1;
                    } else {
                        return SLOT_2;
                    }
                }
            } else {
                return SLOT_ALL;
            }
        }

        private boolean isSmsChildChecked() {
            if (BackupUtils.isMultiSimEnabled()) {
                return mListAdaptor.isChildChecked(BACKUP_GROUP_SYSTEM_DATA, SIM1_SMS_INDEX_ID) ||
                        mListAdaptor.isChildChecked(BACKUP_GROUP_SYSTEM_DATA, SIM2_SMS_INDEX_ID);
            } else {
                return mListAdaptor.isChildChecked(BACKUP_GROUP_SYSTEM_DATA, SMS_INDEX_ID);
            }
        }

        private boolean isMmsChildChecked() {
            if (BackupUtils.isMultiSimEnabled()) {
                return mListAdaptor.isChildChecked(BACKUP_GROUP_SYSTEM_DATA, SIM1_MMS_INDEX_ID) ||
                        mListAdaptor.isChildChecked(BACKUP_GROUP_SYSTEM_DATA, SIM2_MMS_INDEX_ID);
            } else {
                return mListAdaptor.isChildChecked(BACKUP_GROUP_SYSTEM_DATA, MMS_INDEX_ID);
            }
        }

        private boolean isCalendarChildChecked() {
            if (BackupUtils.isMultiSimEnabled()) {
                return mListAdaptor.isChildChecked(BACKUP_GROUP_SYSTEM_DATA, CALENDAR_MSIM_INDEX_ID);
            } else {
                return mListAdaptor.isChildChecked(BACKUP_GROUP_SYSTEM_DATA, CALENDAR_INDEX_ID);
            }
        }

    }

    private void notifyBackupThread() {
        synchronized (mBackupLock) {
            try {
                mBackupLock.notifyAll();
            } catch (Exception e) {
                Log.d(TAG, e.toString());
            }
        }
    }

    private boolean prepareBackupDir(File dir) {
        if (!dir.exists()) {
            Log.d(TAG, "prepareBackupDir backup dir is not exist");
            if (!dir.mkdir()) {
                if (LOCAL_DEBUG)
                    Log.d(TAG,
                            "prepareBackupDir backup stop - can't create base directory "
                                    + dir.getPath());
                Message message = mHandler.obtainMessage(EVENT_FILE_CREATE_ERR);
                mHandler.sendMessage(message);
                return false;
            } else {
                Log.d(TAG, "prepareBackupDir change file permission mode");
                FileUtils.setPermissions(mBackupRootDir,
                        FileUtils.S_IRWXO | FileUtils.S_IRWXU
                                | FileUtils.S_IRWXG, -1, -1);
                return true;
            }
        }
        return true;
    }

    private ArrayList<AppInfo> getSystemDataInfo() {
        ArrayList<AppInfo> appList = new ArrayList<AppInfo>();
        AppInfo dataContact = new AppInfo();
        dataContact.type = KEY_BACKUP_CONTACTS;
        dataContact.appName = getResources().getString(R.string.backup_contact);
        appList.add(dataContact);
        if (BackupUtils.isMultiSimEnabled()) {
            String smsName = getResources().getString(R.string.backup_sms);
            AppInfo dataSim1SMS = new AppInfo();
            dataSim1SMS.type = KEY_BACKUP_SMS1;
            dataSim1SMS.appName = BackupUtils.getMultiSimName(this, SLOT_1) +" " + smsName;
            appList.add(dataSim1SMS);
            AppInfo dataSim2SMS = new AppInfo();
            dataSim2SMS.type = KEY_BACKUP_SMS2;
            dataSim2SMS.appName = BackupUtils.getMultiSimName(this, SLOT_2) +" " + smsName;
            appList.add(dataSim2SMS);
            String mmsName = getResources().getString(R.string.backup_mms);
            AppInfo dataSim1MMS = new AppInfo();
            dataSim1MMS.type = KEY_BACKUP_MMS1;
            dataSim1MMS.appName = BackupUtils.getMultiSimName(this, SLOT_1) +" " + mmsName;
            appList.add(dataSim1MMS);
            AppInfo dataSim2MMS = new AppInfo();
            dataSim2MMS.type = KEY_BACKUP_MMS2;
            dataSim2MMS.appName = BackupUtils.getMultiSimName(this, SLOT_2) +" " + mmsName;
            appList.add(dataSim2MMS);
        } else {
            AppInfo dataSMS = new AppInfo();
            dataSMS.type = KEY_BACKUP_SMS1;
            dataSMS.appName = getResources().getString(R.string.backup_sms);
            appList.add(dataSMS);
            AppInfo dataMMS = new AppInfo();
            dataMMS.type = KEY_BACKUP_MMS1;
            dataMMS.appName = getResources().getString(R.string.backup_mms);
            appList.add(dataMMS);
        }
        AppInfo dataCalendar = new AppInfo();
        dataCalendar.type = KEY_BACKUP_CALENDAR;
        dataCalendar.appName = getResources().getString(R.string.backup_calendar);
        appList.add(dataCalendar);
        return appList;
    }

    private ArrayList<AppInfo> getInstalledAppInfo() {
        ArrayList<AppInfo> appList = new ArrayList<AppInfo>();
        List<PackageInfo> packages = getPackageManager()
                .getInstalledPackages(0);
        for (int i = 0; i < packages.size(); i++) {
            PackageInfo packageInfo = packages.get(i);
            AppInfo tmpInfo = new AppInfo();
            tmpInfo.appName = packageInfo.applicationInfo.loadLabel(
                    getPackageManager()).toString();
            tmpInfo.packageName = packageInfo.packageName;
            tmpInfo.versionName = packageInfo.versionName;
            tmpInfo.versionCode = packageInfo.versionCode;
            tmpInfo.sourcedir = packageInfo.applicationInfo.sourceDir;
            tmpInfo.publicsourcedir = packageInfo.applicationInfo.publicSourceDir;
            // Only display the non-system app info
            if ((packageInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                if (DBG) {
                    Log.d(TAG, "This is a installed app" + tmpInfo.appName);
                    Log.d(TAG, " getInstalledAppInfo packageInfo: sourceDir: "
                            + packageInfo.applicationInfo.sourceDir
                            + "  publicSourceDir: "
                            + packageInfo.applicationInfo.publicSourceDir);
                }
                appList.add(tmpInfo);
            } else {
                Log.d(TAG, "This is a system app" + tmpInfo.appName);
            }
        }

        Collections.sort(appList, new Comparator<AppInfo>(){
            @Override
            public int compare(AppInfo b1, AppInfo b2) {
                return b1.appName.compareTo(b2.appName);
            }

        });

        for (int i = 0; i < appList.size(); i++) {
            appList.get(i).print();
        }

        return appList;
    }

    private void BackupPictureRecursion(String srcDir) {
        //
        if (srcDir.contains("lost+found") || srcDir.contains(".thumbnails")) {
            return;
        }
        File srcFile = new File(srcDir);
        if (!srcFile.isDirectory()) {
            // if not a directory ,return
            return;
        }
        File[] listFiles = srcFile.listFiles();
        if (listFiles.length > 0) {
            for (File mFile : listFiles) {
                if (mFile.isDirectory()) {
                    BackupPictureRecursion(mFile.getPath());
                } else {
                    if (isPictureFile(mFile)) {
                        dirArray.add(mFile.getPath());
                    }
                }
            }
        } else {
            // do nothing
        }
    }

    private boolean copyPictureFile(String srcdir, String disdir) {
        Log.d(TAG, "srcdir = " + srcdir + " disdir=" + disdir);
        FileInputStream fileInputStream;
        FileOutputStream fileOutputStream;
        BufferedInputStream bis;
        BufferedOutputStream bos;
        boolean sIsAborted = false;
        int position = 0;
        int readLength = 0;

        File disfileinfo = null;
        File fileinfo = new File(srcdir);
        if (!fileinfo.exists()) {
            Log.e(TAG, "copyPictureFile file not exist");
            return false;
        }

        byte[] buffer = new byte[OUT_PUT_BUFFER_SIZE];

        try {
            fileInputStream = new FileInputStream(fileinfo);
        } catch (IOException e) {
            Log.e(TAG, "copyPictureFile open stream " + e.toString());
            return false;
        }
        bis = new BufferedInputStream(fileInputStream, 0x4000);

        if ((disfileinfo = createDisDir(srcdir, disdir)) == null) {
            if (DBG)
                Log.e(TAG, "copyPictureFile create file err");
            return false;
        }
        try {
            fileOutputStream = new FileOutputStream(disfileinfo);
        } catch (IOException e) {
            Log.e(TAG, "copyPictureFile open stream " + e.toString());
            return false;
        }
        bos = new BufferedOutputStream(fileOutputStream, 0x10000);

        try {
            while ((position != fileinfo.length())) {
                if (mCancelBackupFlag) {
                    sIsAborted = true;
                    break;
                }
                if (position != fileinfo.length()) {
                    readLength = bis.read(buffer, 0, OUT_PUT_BUFFER_SIZE);
                }
                bos.write(buffer, 0, readLength);
                position += readLength;
            }
        } catch (IOException e) {
            Log.e(TAG, "Write aborted " + e.toString());
            if (LOCAL_DEBUG)
                Log.d(TAG, "Write Abort Received");
            return false;
        }

        if (bis != null) {
            try {
                bis.close();
            } catch (IOException e) {
                Log.e(TAG, "input stream close" + e.toString());
                if (LOCAL_DEBUG)
                    Log.d(TAG, "Error when closing stream after send");
                return false;
            }
        }

        if (bos != null) {
            try {
                bos.close();
            } catch (IOException e) {
                Log.e(TAG, "onPut close stream " + e.toString());
                if (LOCAL_DEBUG)
                    Log.d(TAG, "Error when closing stream after send");
                return false;
            }
        }
        if (LOCAL_DEBUG)
            Log.d(TAG, "sendFile - position = " + position);
        if (position == fileinfo.length() && sIsAborted == false) {
            if (LOCAL_DEBUG)
                Log.d(TAG, "sendFile - return ok ");
            return true;
        } else {
            if (LOCAL_DEBUG)
                Log.d(TAG, "sendFile - return false ");
            return false;
        }
    }

    private File createDisDir(String src, String dir) {
        File retdir = null;
        String fullname = dir;

        if (LOCAL_DEBUG)
            Log.d(TAG, "createDisDir START ");
        String[] dirs = src.split("/");
        String myDirs = "";
        for (int i = 3; i < dirs.length; i++) {
            if (i != dirs.length - 1) {
                myDirs = myDirs + "/" + dirs[i];
            } else {
                myDirs = myDirs + "/" + dirs[i];
            }
        }
        if (DBG)
            Log.d(TAG, "myDirs = " + myDirs);
        int i = 0;
        int end = dirs.length - 1;
        String[] mDirs = myDirs.split("/");
        for (String str : mDirs) {
            if (DBG)
                Log.d(TAG, "str =" + str);
        }
        int end1 = mDirs.length - 1;
        for (String subdir : mDirs) {
            if (i < end1) {
                fullname += "/";
                fullname += subdir;
                if (DBG)
                    Log.d(TAG, "createDisDir fullname: " + fullname);
                retdir = new File(fullname);
                if (!retdir.exists()) {
                    if (!retdir.mkdir()) {
                        if (LOCAL_DEBUG)
                            Log.d(TAG,
                                    "Send vcard Files aborted - can't create base directory "
                                            + retdir.getPath());
                        return null;
                    } else {
                        Log.d(TAG, "OperateThread change file permission mode");
                    }
                }
            } else if (i == end1) {
                if (DBG)
                    Log.d(TAG, "createDisDir create the new file");
                retdir = new File(fullname + "/" + mDirs[i]);
            }
            i++;
        }
        return retdir;
    }

    private String getFileExtendName(String filename) {
        int index = filename.lastIndexOf('.');
        return index == -1 ? null : filename.substring(index + 1);
    }

    private boolean isPictureFile(File file) {
        String str = getFileExtendName(file.getPath());
        if (str == null) {
            return false;
        }
        boolean b = str.equalsIgnoreCase("jpg") || str.equalsIgnoreCase("jpeg")
                || str.equalsIgnoreCase("bmp") || str.equalsIgnoreCase("wbmp")
                || str.equalsIgnoreCase("gif") || str.equalsIgnoreCase("png");
        return b;
    }

    private class Showconnectprogress extends ProgressDialog {
        private Context mContext;
        private boolean LockedView = false;

        public Showconnectprogress(Context context) {
            this(context, com.android.internal.R.style.Theme_Holo_Dialog_Alert);
            mContext = context;
        }

        public Showconnectprogress(Context context, int theme) {
            super(context, theme);
            mContext = context;
        }

        public void dismiss() {
            if (!LockedView)
                super.dismiss();
            else
                LockedView = false;
        }

        @Override
        public boolean onKeyDown(int keyCode, KeyEvent event) {

            if (LOCAL_DEBUG)
                Log.v(TAG, "Showconnectprogress keyCode " + keyCode);
            if ((mSendProgressDialog != null) && (keyCode == KeyEvent.KEYCODE_BACK)) {

                // show dialog to confirm stop backup
                new AlertDialog.Builder(BackupActivity.this,
                        android.R.style.Theme_DeviceDefault_Dialog_NoActionBar)
                        .setMessage(R.string.confirm_stop_backup)
                        .setNegativeButton(R.string.button_cancel, null)
                        .setPositiveButton(R.string.button_ok,
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog,
                                            int whichButton) {
                                        File rootdir = null;
                                        rootdir = new File(mBackupDataDir + mCurrentDir);
                                        mCancelBackupFlag = true;
                                        if (mRemoteBackupService != null) {
                                            try {
                                                mRemoteBackupService
                                                        .setCancelBackupFlag(mCancelBackupFlag);
                                            } catch (RemoteException e) {
                                                e.printStackTrace();
                                            }
                                        }
                                        if (mSendProgressDialog != null) {
                                            mSendProgressDialog.dismiss();
                                            mSendProgressDialog = null;
                                        }
                                        Toast.makeText(BackupActivity.this,
                                                R.string.stop_backup_info, Toast.LENGTH_SHORT)
                                                .show();
                                        deleteDir(rootdir);
                                        updateBackupButton(true);
                                    }
                                }).show();
            }
            if ((mSendProgressDialog != null)
                    && (keyCode == KeyEvent.KEYCODE_SEARCH)) {
                return true;
            }
            return false;
        }
    }

    private class BackupAdapter extends SimpleExpandableListAdapter {

        private BackupActivity mContext;
        private List<Map<String, String>> mGroupData;
        private List<List<Map<String, String>>> mChildData;
        private Handler mHandler;

        public BackupAdapter(BackupActivity context, List<Map<String, String>> groupData,
                int expandedGroupLayout, int collapsedGroupLayout, String[] groupFrom,
                int[] groupTo, List<List<Map<String, String>>> childData,
                int childLayout, int lastChildLayout, String[] childFrom, int[] childTo) {
            super(context, groupData, expandedGroupLayout, collapsedGroupLayout, groupFrom,
                    groupTo, childData,
                    childLayout, lastChildLayout, childFrom, childTo);
            mContext = context;
            mGroupData = groupData;
            mChildData = childData;
            mHandler = new Handler() {

                @Override
                public void handleMessage(Message msg) {
                    notifyDataSetChanged();
                    super.handleMessage(msg);
                }
            };
        }

        public View getGroupView(int groupPosition, boolean isExpanded,
                View convertView, ViewGroup parent) {

            View v = super.getGroupView(groupPosition, isExpanded, convertView, parent);
            CheckBox box = (CheckBox) (v.findViewById(R.id.module_check));
            if (mGroupData.get(groupPosition).get(MAP_KEY_CHECKED) == "true")
                box.setChecked(true);
            else
                box.setChecked(false);
            box.setOnClickListener(mContext);
            box.setContentDescription("" + groupPosition + ":-1");
            TextView name = (TextView) (v.findViewById(R.id.module_name));
            name.setOnClickListener(mContext);
            TextView percent = (TextView) (v.findViewById(R.id.module_percent));
            int[] status = getChildCheckedStatus(groupPosition);
            percent.setText("" + status[0] + "/" + status[1]);
            percent.setOnClickListener(mContext);
            v.setContentDescription("" + groupPosition);
            ImageView image = (ImageView) (v.findViewById(R.id.module_image));
            image.setOnClickListener(mContext);
            if(isExpanded) {
                image.setImageResource(R.drawable.expander_close);
                imageStatus[groupPosition] = true;
            }
            else {
                image.setImageResource(R.drawable.expander_open);
                imageStatus[groupPosition] = false;
            }
            image.setContentDescription("" + groupPosition + ":-1");
            return v;
        }

        public View getChildView(int groupPosition, int childPosition,
                boolean isLastChild, View convertView, ViewGroup parent) {
            View v = super.getChildView(groupPosition, childPosition,
                    isLastChild, convertView, parent);
            CheckBox box = (CheckBox) (v.findViewById(R.id.module_item_check));
            if (isChildChecked(groupPosition, childPosition))
                box.setChecked(true);
            else
                box.setChecked(false);
            box.setOnClickListener(mContext);
            box.setContentDescription("" + groupPosition + ":" + childPosition);
            TextView name = (TextView) (v.findViewById(R.id.module_item_name));
            name.setOnClickListener(mContext);
            v.setContentDescription("" + groupPosition + ":" + childPosition);
            return v;
        }

        public boolean isChildChecked(int groupPosition, int childPosition) {
            if (groupPosition < mChildData.size()) {
                List<Map<String, String>> list = mChildData.get(groupPosition);
                if (list != null && childPosition < list.size()) {
                    Map<String, String> item = list.get(childPosition);
                    if (item != null) {
                        Object key = item.get(MAP_KEY_CHECKED);
                        if (key != null && (key instanceof String)
                                && ((String) key).compareTo("true") == 0)
                            return true;
                    }

                }
            }
            return false;
        }

        public boolean[] getChildCheckedList(int groupPosition, int size) {
            boolean[] checkedArray = new boolean[size];
            if (groupPosition < mChildData.size()) {
                List<Map<String, String>> list = mChildData.get(groupPosition);
                for (int i = 0; i < list.size(); i++) {
                    Map<String, String> item = list.get(i);
                    if (item != null) {
                        Object key = item.get(MAP_KEY_CHECKED);
                        if (key != null && (key instanceof String)
                                && ((String) key).compareTo("true") == 0) {
                            checkedArray[i] = true;
                        } else {
                            checkedArray[i] = false;
                        }
                    }
                }
            }

            return checkedArray;
        }

        public void setChildChecked(int groupPosition, int childPosition, boolean checked) {
            if (groupPosition < mChildData.size()) {
                List<Map<String, String>> list = mChildData.get(groupPosition);
                if (list != null && childPosition < list.size()) {
                    Map<String, String> item = list.get(childPosition);
                    if (item != null) {
                        if (checked)
                            item.put(MAP_KEY_CHECKED, "true");
                        else
                            item.put(MAP_KEY_CHECKED, "false");
                    }
                }
            }
        }

        public boolean isGroupChecked(int groupPosition) {
            if (groupPosition < mGroupData.size()) {
                Map<String, String> item = mGroupData.get(groupPosition);
                if (item != null) {
                    Object key = item.get(MAP_KEY_CHECKED);
                    if (key != null && (key instanceof String)
                            && ((String) key).compareTo("true") == 0)
                        return true;
                }
            }
            return false;
        }

        public void setGroupChecked(int groupPosition, boolean checked) {
            if (groupPosition < mGroupData.size()) {
                Map<String, String> item = mGroupData.get(groupPosition);
                if (item != null) {
                    if (checked)
                        item.put(MAP_KEY_CHECKED, "true");
                    else
                        item.put(MAP_KEY_CHECKED, "false");
                }
            }
        }

        public void setChildrenChecked(int groupPosition, boolean checked) {
            if (groupPosition < mChildData.size()) {
                List<Map<String, String>> list = mChildData.get(groupPosition);
                if (list != null) {
                    for (Map<String, String> item : list) {
                        if (item != null) {
                            if (checked)
                                item.put(MAP_KEY_CHECKED, "true");
                            else
                                item.put(MAP_KEY_CHECKED, "false");
                        }
                    }
                }
            }
        }

        public int[] getChildCheckedStatus(int groupPosition) {
            int[] ret = new int[2];
            ret[0] = 0;
            ret[1] = 0;
            if (groupPosition < mChildData.size()) {
                List<Map<String, String>> list = mChildData.get(groupPosition);
                for (Map<String, String> item : list) {
                    ret[1] += 1;
                    if (item != null) {
                        Object key = item.get(MAP_KEY_CHECKED);
                        if (key != null && (key instanceof String)
                                && ((String) key).compareTo("true") == 0)
                            ret[0] += 1;
                    }
                }
            }
            return ret;
        }

        public void reflesh(int groupPosition) {
            mHandler.sendMessage(new Message());
        }
    }

    public void initListView() {
        mListView = (PinnedHeaderExpandableListView) getExpandableListView();
        mListView.setItemsCanFocus(true);
        mListView.setClickable(false);
        mListView.setEnabled(true);

        // get data
        mDataList = getSystemDataInfo();
        mAppList = getInstalledAppInfo();
        // create group content
        mGroupData = new ArrayList<Map<String, String>>();
        Map<String, String> dataMap = new HashMap<String, String>();
        dataMap.put(MAP_KEY_KEY, KEY_SYSTEM_DATA_GROUP);
        dataMap.put(MAP_KEY_NAME, getResources().getString(R.string.backup_systemdata));
        dataMap.put(MAP_KEY_CHECKED, "true");
        dataMap.put(MAP_KEY_PERCENT, "0/" + mDataList.size());
        Map<String, String> appMap = new HashMap<String, String>();
        appMap.put(MAP_KEY_KEY, KEY_APP_GROUP);
        appMap.put(MAP_KEY_NAME, getResources().getString(R.string.backup_application));
        appMap.put(MAP_KEY_CHECKED, "false");
        appMap.put(MAP_KEY_PERCENT, "0/" + mAppList.size());
        mGroupData.add(dataMap);
        mGroupData.add(appMap);

        // create child content
        mChildData = new ArrayList<List<Map<String, String>>>();
        List<Map<String, String>> dataList = new ArrayList<Map<String, String>>();

        for (AppInfo info : mDataList) {
            Map<String, String> data = new HashMap<String, String>();
            data.put(MAP_KEY_KEY, info.type);
            data.put(MAP_KEY_NAME, info.appName);
            data.put(MAP_KEY_CHECKED, "true");
            dataList.add(data);
        }

        List<Map<String, String>> appList = new ArrayList<Map<String, String>>();

        for (AppInfo info : mAppList) {
            Map<String, String> app = new HashMap<String, String>();
            app.put(MAP_KEY_KEY, info.packageName);
            app.put(MAP_KEY_NAME, info.appName);
            app.put(MAP_KEY_CHECKED, "false");
            appList.add(app);
        }

        //SortAppList(appList); don't need any more, already sort in getInstalledAppInfo()

        /** just for log, nonsense
        for(AppInfo info: mAppList) {
            Log.d(TAG, "App info in mAppList: " + info.appName);
        }
        for (int i = 0; i < appList.size(); i++) {
            Log.d(TAG, "App in List: " + appList.get(i).get(MAP_KEY_NAME) + ": checked - " + appList.get(i).get(MAP_KEY_CHECKED));
        }**/

        mChildData.add(dataList);
        mChildData.add(appList);

        boolean needSetGroupChecked = false;
        boolean[] expandStatus = null;
        if ( mRestoreBundle != null ) {
            Log.d(TAG, "initListView go Restore process: the activity may re-create");
            HashMap map = (HashMap)mRestoreBundle.getSerializable("storeData");
            restoreCheckedStatus(map);
            expandStatus = (boolean[])map.get(KEY_GROUP_EXPAND);
            mRestoreBundle = null;
            needSetGroupChecked = true;
        } else if ( mStoreStatusMap != null ) {
            Log.d(TAG, "initListView go store process: the activity may re-launcher");
            restoreCheckedStatus(mStoreStatusMap);
            expandStatus = (boolean[])(mStoreStatusMap.get(KEY_GROUP_EXPAND));
            mStoreStatusMap = null;
            needSetGroupChecked = true;
        }

        mListAdaptor = new BackupAdapter(this, mGroupData,
                R.layout.backup_list_group_item, R.layout.backup_list_group_item,
                new String[] {
                        MAP_KEY_NAME, MAP_KEY_PERCENT
                }, new int[] {
                        R.id.module_name, R.id.module_percent
                },
                mChildData, R.layout.backup_list_child_item, R.layout.backup_list_child_item,
                new String[] {
                    MAP_KEY_NAME
                }, new int[] {
                    R.id.module_item_name
                });
        mListView.setAdapter(mListAdaptor);
        mListView.setOnHeaderUpdateListener(this);
        //mListView.expandGroup(0);

        //set the group checkbox checked status according to child checkbox status.
        if (needSetGroupChecked) {
            for ( int j = 0; j < mGroupData.size(); j++ ){
                setGroupChecked(j);
            }
            //restore the group item's expand status
            for (int k = 0 ; k < expandStatus.length; k++){
                handleGroupExpanded(k, !expandStatus[k]);
            }
        }

    }

    private void SortAppList(List<Map<String, String>> appList) {
        for (int i = 0; i < appList.size(); i++) {
            Map<String, String> src_name = appList.get(i);
            String src_str_name = (String) src_name.get(MAP_KEY_NAME);

            Map<String, String> temp_name = null;
            for (int j = i; j < appList.size(); j++) {
                Map<String, String> dest_name = appList.get(j);
                String dest_str_name = (String) dest_name.get(MAP_KEY_NAME);

                if (src_str_name.compareTo(dest_str_name) > 0) {
                    temp_name = src_name;
                    src_name = appList.get(j);
                    src_str_name = (String) src_name.get(MAP_KEY_NAME);
                    appList.set(i, appList.get(j));
                    appList.set(j, temp_name);
                }
            }
        }
    }

    @Override
    public void onClick(View v) {
        try {
            if (v.getId() == R.id.module_image) {
                ImageView imageView = (ImageView) v;
                try {
                    String desc = imageView.getContentDescription().toString();
                    String[] info = desc.split(":");
                    int groupPosition = Integer.parseInt(info[0]);
                    if (mListView.isGroupExpanded(groupPosition)) {
                        mListView.collapseGroup(groupPosition);
                        imageView.setImageResource(R.drawable.expander_open);
                        imageStatus[groupPosition] = true;
                    } else {
                        mListView.expandGroup(groupPosition);
                        imageView.setImageResource(R.drawable.expander_close);
                        imageStatus[groupPosition] = false;
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            } else if (v instanceof CheckBox) {
                Log.d(TAG, "v is CheckBox");
                CheckBox buttonView = (CheckBox) v;
                try {
                    String desc = buttonView.getContentDescription().toString();
                    String[] info = desc.split(":");
                    int groupPosition = Integer.parseInt(info[0]);
                    int childPosition = Integer.parseInt(info[1]);
                    if (childPosition == -1) {
                        boolean checked = !mListAdaptor.isGroupChecked(groupPosition);
                        mListAdaptor.setGroupChecked(groupPosition, checked);
                        mListAdaptor.setChildrenChecked(groupPosition, checked);
                    } else {
                        mListAdaptor.setChildChecked(groupPosition, childPosition,
                                !mListAdaptor.isChildChecked(groupPosition, childPosition));
                        setGroupChecked(groupPosition);
                    }
                    mListAdaptor.reflesh(groupPosition);
                    updateBackupButton(isChecked());
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            } else if (v instanceof Button) {
                if(isChecked()) {
                    String backupPath = BackupUtils.getBackupPath(this);
                    //check if backup path exists,If no, toast warning and stop backup.
                    if(BackupUtils.isDirAvalible(backupPath)){
                        //check if backup path is internal
                        if(!BackupUtils.isInternalPath(backupPath)){
                            //backup path is in SD card
                            operateBackup();
                        }else{
                            //backup path is in internal storage
                            if(BackupUtils.isInternalFull()) {
                                Toast.makeText(BackupActivity.this,
                                    R.string.no_space_warning, Toast.LENGTH_SHORT).show();
                                return;
                            } else {
                                final SharedPreferences sharedPreferences =
                                    getSharedPreferences(PREFERENCE_BACKUP_ACTIVITY, Context.MODE_PRIVATE);
                                //check if user select the "don't warn" checkbox.
                                if (sharedPreferences.getBoolean(PREFERENCE_BACKUP_NO_SDCARD_DIALOG_KEY, true)) {
                                    LayoutInflater layoutInflater = LayoutInflater.from(BackupActivity.this);
                                    View dialogView = layoutInflater.inflate(R.layout.dialog_style, null);
                                    mCheckBox = (CheckBox) dialogView.findViewById(R.id.checkbox);
                                    new AlertDialog.Builder(BackupActivity.this,
                                        android.R.style.Theme_DeviceDefault_Dialog_NoActionBar)
                                        .setMessage(R.string.no_sdcard_backup_abstract)
                                        .setView(dialogView)
                                        .setNeutralButton(R.string.button_help, new DialogInterface.OnClickListener() {

                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {

                                                Intent intent = new Intent();
                                                intent.setClass(BackupActivity.this, HelpActivity.class);
                                                intent.putExtra(HELP_TYPE_KEY, TYPE_BACKUP);
                                                startActivity(intent);
                                            }
                                        })
                                        .setNegativeButton(R.string.button_cancel, null)
                                        .setPositiveButton(R.string.button_continue, new DialogInterface.OnClickListener() {

                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {

                                                if (mCheckBox.isChecked()) {
                                                    SharedPreferences.Editor editor = sharedPreferences.edit();
                                                    editor.putBoolean(PREFERENCE_BACKUP_NO_SDCARD_DIALOG_KEY, false);
                                                    editor.commit();
                                                }
                                                operateBackup();
                                            }
                                        }).create().show();
                                } else {
                                    operateBackup();
                                }
                            }
                        }
                    }else{
                        Toast.makeText(BackupActivity.this,
                                    getString(R.string.backup_path_dont_exist,
                                        backupPath)
                                    , Toast.LENGTH_SHORT).show();
                        return;
                    }
                } else {
                    Log.d(TAG, "on MENU_MULTISEL_OK no Item Checked");
                    Toast.makeText(getApplicationContext(),
                            R.string.stop_no_item_selected, 2).show();
                }
            } else {
                LinearLayout item = (LinearLayout) (v.getParent().getParent());
                String desc = item.getContentDescription().toString();
                if(desc.contains(":")) {
                    try {
                        String[] info = desc.split(":");
                        int groupPosition = Integer.parseInt(info[0]);
                        int childPosition = Integer.parseInt(info[1]);
                        mListAdaptor.setChildChecked(groupPosition, childPosition,
                                !mListAdaptor.isChildChecked(groupPosition, childPosition));
                        setGroupChecked(groupPosition);
                        mListAdaptor.reflesh(groupPosition);
                        updateBackupButton(isChecked());
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                } else {
                    int groupPosition = Integer.parseInt(item.getContentDescription().toString());
                    if (mListView.isGroupExpanded(groupPosition)) {
                        mListView.collapseGroup(groupPosition);
                    } else {
                        mListView.expandGroup(groupPosition);
                    }
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void handleGroupExpanded(int groupPosition, boolean isExpanded){
        if (isExpanded) {
            mListView.collapseGroup(groupPosition);
            //imageView.setImageResource(R.drawable.expander_open);
            imageStatus[groupPosition] = true;
        } else {
            mListView.expandGroup(groupPosition);
            //imageView.setImageResource(R.drawable.expander_close);
            imageStatus[groupPosition] = false;
        }
    }

    private void setGroupChecked(int groupPosition){
        Log.d(TAG, "setGroupChecked groupPosition: " + groupPosition);
        int[] status = mListAdaptor.getChildCheckedStatus(groupPosition);
        if (status[0] == status[1]){
            Log.d(TAG, "setGroupChecked true ");
            mListAdaptor.setGroupChecked(groupPosition, true);
        } else {
            Log.d(TAG, "setGroupChecked false ");
            mListAdaptor.setGroupChecked(groupPosition, false);
        }
    }

    private IRemoteBackupService mRemoteBackupService;

    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mRemoteBackupService = null;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mRemoteBackupService = IRemoteBackupService.Stub.asInterface(service);
            if (mRemoteBackupService != null) {
                try {
                    mRemoteBackupService.registerCallback(null, mBackupCallback);
                    mRemoteBackupService.init(BackupActivity.this.getPackageName());
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
    };

    private IBackupCallback.Stub mBackupCallback = new IBackupCallback.Stub() {

        @Override
        public void handleBackupMsg(int what, int type, int result)
                throws RemoteException {
            Log.d(TAG, "service: type = " + type + " result: " + result);
            switch (what) {
            case EVENT_SDCARD_NO_SPACE: {
                Log.d(TAG, "service: EVENT_SDCARD_NO_SPACE");
                mHandler.sendEmptyMessage(EVENT_SDCARD_NO_SPACE);
                break;
            }
            case EVENT_FILE_CREATE_ERR: {
                Log.d(TAG, "service: EVENT_FILE_CREATE_ERR");
                mHandler.sendEmptyMessage(EVENT_FILE_CREATE_ERR);
                break;
            }
            case EVENT_INIT_PROGRESS_TITLE: {
                Log.d(TAG, "service: EVENT_INIT_PROGRESS_TITLE");
                mHandler.sendMessage(mHandler.obtainMessage(
                        EVENT_INIT_PROGRESS_TITLE, type, result));
                break;
            }
            case EVENT_SET_PROGRESS_VALUE: {
                Log.d(TAG, "service: EVENT_SET_PROGRESS_VALUE");
                mHandler.sendMessage(mHandler.obtainMessage(
                        EVENT_SET_PROGRESS_VALUE, type, result));
                break;
            }
            case EVENT_BACKUP_RESULT: {
                Log.d(TAG, "service: EVENT_BACKUP_RESULT");
                mHandler.sendMessage(mHandler.obtainMessage(
                        EVENT_BACKUP_RESULT, type, result));
                break;
            }
            default:
                Log.i(TAG, "service: unknow message: " + what);
                return;
            }
        }
    };

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        mHandler = new BackupHandler();

        setContentView(R.layout.backup_main);

        mButtonBackup = (Button) findViewById(R.id.backup_btn);
        mButtonBackup.setOnClickListener(this);

        mRestoreBundle = icicle;

        mCancelBackupFlag = false;
        Intent intent = new Intent(this, BackupService.class);
        boolean ret = getApplicationContext().bindService(intent,
                mConnection, Context.BIND_AUTO_CREATE);
        Log.d(TAG, "bind backup service status: " + ret);

    }

    protected void onResume() {
        super.onResume();
        initListView();
        updateBackupButton(isChecked());
        synchronized (mWakeLockSync) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            sWakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK
                    | PowerManager.ACQUIRE_CAUSES_WAKEUP
                    | PowerManager.ON_AFTER_RELEASE, TAG);
            sWakeLock.acquire();
        }
    }

    private void restoreCheckedStatus(HashMap<String,Object> map) {

        //restore child checkbox checked status
        for ( int i = 0; i < mChildData.size(); i++ ){
            List list = mChildData.get(i);
            for ( int k = 0; k < list.size(); k++ ){
                HashMap childMap = (HashMap)list.get(k);
                String key = (String)childMap.get(MAP_KEY_KEY);
                if (!map.containsKey(key)) continue;
                String isChecked = (String)map.get(key);
                Log.d(TAG, "childData: restore key: " + key + " checked: " + isChecked);
                mChildData.get(i).get(k).put(MAP_KEY_CHECKED,isChecked);
            }
        }

    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        Log.d(TAG, "onSaveInstanceState");
        outState.putSerializable("storeData",getStoreData());
        super.onSaveInstanceState(outState);
    }

    private HashMap getStoreData() {
        HashMap<String,Object> retMap = new HashMap<String,Object>();

        for ( int i = 0; i < mChildData.size(); i++ ){
            for (Map map: mChildData.get(i)) {
                retMap.put((String)map.get(MAP_KEY_KEY),(String)map.get(MAP_KEY_CHECKED));
                Log.d("storeData", "key = " + (String)map.get(MAP_KEY_KEY) + " value = " + (String)map.get(MAP_KEY_CHECKED));
            }
        }

        boolean[] expandStatus = new boolean[mGroupData.size()];
        for (int k = 0 ; k < mGroupData.size(); k++){
            expandStatus[k] = mListView.isGroupExpanded(k);
        }

        retMap.put(KEY_GROUP_EXPAND, expandStatus);
        return retMap;
    }

    protected void onPause() {
        super.onPause();
        mStoreStatusMap = getStoreData();
        synchronized (mWakeLockSync) {
            if (sWakeLock != null) {
                sWakeLock.release();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
//
//    public boolean onOptionsItemSelected(MenuItem item) {
//        switch (item.getItemId()) {
//            case MENU_MULTISEL_OK:
//                if (isChecked()) {
//                    operateBackup();
//                } else {
//                    Log.d(TAG, "on MENU_MULTISEL_OK no Item Checked");
//                    Toast.makeText(getApplicationContext(),
//                            R.string.stop_no_item_selected, 2).show();
//                }
//                return true;
//            default:
//                return true;
//        }
//    }

    private void backupDealer() {
        updateBackupButton(false);
        mSendProgressDialog = new Showconnectprogress(this);
        mSendProgressDialog.setTitle(R.string.prepare_backup);
        mSendProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mSendProgressDialog.setCanceledOnTouchOutside(false);
        mSendProgressDialog.setButton(getResources().getString(R.string.button_stop),
                new ProgressDialog.OnClickListener() {
                    public void onClick(DialogInterface dialog,
                            int whichButton) {
                        mSendProgressDialog.LockedView = true;
                        new AlertDialog.Builder(BackupActivity.this,
                                android.R.style.Theme_DeviceDefault_Dialog_NoActionBar)
                                .setMessage(R.string.confirm_stop_backup)
                                .setNegativeButton(R.string.button_cancel, null)
                                .setPositiveButton(R.string.button_ok,
                                        new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface dialog,
                                                    int whichButton) {
                                                File rootdir = null;
                                                rootdir = new File(mBackupDataDir
                                                        + mCurrentDir);
                                                mCancelBackupFlag = true;
                                                if (mRemoteBackupService != null) {
                                                    try {
                                                        mRemoteBackupService.setCancelBackupFlag(
                                                                mCancelBackupFlag);
                                                    } catch (RemoteException e) {
                                                        e.printStackTrace();
                                                    }
                                                }
                                                if (mSendProgressDialog != null) {
                                                    mSendProgressDialog.dismiss();
                                                    mSendProgressDialog = null;
                                                }
                                                Toast.makeText(BackupActivity.this,
                                                        R.string.stop_backup_info,
                                                        Toast.LENGTH_SHORT)
                                                        .show();
                                                deleteDir(rootdir);
                                                updateBackupButton(true);
                                            }
                                        }).show();
                    }
                });
        mSendProgressDialog.setCancelable(true);
        mSendProgressDialog.show();
        if (LOCAL_DEBUG)
            Log.v(TAG, "Show the progress bar");
        mOperateThread = new OperateThread();
        Thread thread = new Thread(mOperateThread);
        thread.start();
    }

    private void operateBackup() {
        Log.d(TAG, "operateBackup start");

        mBackupPath = BackupUtils.getBackupPath(this);
        mBackupDataDir = mBackupPath + BACKUP_DATA;
        mBackupRootDir = mBackupPath + BACKUP_FOLDER;
        mBackupAppDir = mBackupPath + BACKUP_APP;

        checkDupAndHandleBackUp();
        /**
        if (BackupUtils.isInternalPath(mBackupPath)) {
            final SharedPreferences sharedPreferences =
            getSharedPreferences(PREFERENCE_BACKUP_ACTIVITY, Context.MODE_PRIVATE);
            if (sharedPreferences.getBoolean(PREFERENCE_BACKUP_NO_SDCARD_DIALOG_KEY, true)) {
                LayoutInflater layoutInflater = LayoutInflater.from(BackupActivity.this);
                View dialogView = layoutInflater.inflate(R.layout.dialog_style, null);
                mCheckBox = (CheckBox) dialogView.findViewById(R.id.checkbox);
                    new AlertDialog.Builder(BackupActivity.this,
                        android.R.style.Theme_DeviceDefault_Dialog_NoActionBar)
                        .setMessage(R.string.no_sdcard_backup_abstract)
                        .setView(dialogView)
                        .setNeutralButton(R.string.button_help,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,
                                    int whichButton) {
                                    Intent intent = new Intent();
                                    intent.setClass(BackupActivity.this, HelpActivity.class);
                                    intent.putExtra(HELP_TYPE_KEY, TYPE_BACKUP);
                                    startActivity(intent);
                                }
                        })
                        .setNegativeButton(R.string.button_cancel, null)
                        .setPositiveButton(R.string.button_continue,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,
                                    int whichButton) {
                                    checkDupAndHandleBackUp();
                                    if (mCheckBox.isChecked()) {
                                        SharedPreferences.Editor editor = sharedPreferences.edit();
                                        editor.putBoolean(PREFERENCE_BACKUP_NO_SDCARD_DIALOG_KEY, false);
                                    editor.commit();
                                }
                    }
                }).show();
            } else {
                checkDupAndHandleBackUp();
            }

            if (BackupUtils.isInternalFull()) {
                Toast.makeText(BackupActivity.this,
                        R.string.no_space_warning, Toast.LENGTH_SHORT).show();
                return;
            }
        }*/
    }

    private void checkDupAndHandleBackUp(){
        if (TestBackupDuplicate()) {
            new AlertDialog.Builder(BackupActivity.this,
                    android.R.style.Theme_DeviceDefault_Dialog_NoActionBar)
                    .setMessage(R.string.confirm_backup_duplicate_title)
                    .setNegativeButton(R.string.button_cancel, null)
                    .setPositiveButton(R.string.button_ok,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,
                                int whichButton) {
                                    backupDealer();
                                }
                            }).show();
        } else {
            backupDealer();
        }
    }

    private boolean TestBackupDuplicate() {
        for (int i = 0; i < mAppList.size(); i++) {
            if (mListAdaptor.isChildChecked(BACKUP_GROUP_APPS, i)) {
                AppInfo appin = mAppList.get(i);
                String path = mBackupAppDir + "/" + appin.packageName + ".apk";
                if (new File(path).exists())
                    return true;
                path = mBackupAppDir + "/" + appin.packageName + ".tar";
                if (new File(path).exists())
                    return true;
            }
        }
        return false;
    }

    private void updateProgressDialog(String title, int max) {
        if (mSendProgressDialog != null) {
            mSendProgressDialog.setTitle(title);
            if (max > 0) {
                mSendProgressDialog.setMax(max);
            }
            mSendProgressDialog.setProgress(0);
        }
    }

    private void updateProgressValue(int val) {
        Log.d(TAG, "mSendProgressDialog = " + mSendProgressDialog);
        if (mSendProgressDialog != null) {
            mSendProgressDialog.setProgress(val);
        } else {
            Log.d(TAG, "updateProgressValue mSendProgressDialog NULL");
        }
    }
    private void updateProgressIncrementValue(int val) {
        if (mSendProgressDialog != null) {
            mSendProgressDialog.incrementProgressBy(val);
        }
    }

    private void deleteDir(File dir) {
        if (dir == null || !dir.exists()) {
            return;
        }
        if (!dir.isDirectory()) {
            dir.delete();
            return;
        }
        for (File files : dir.listFiles()) {
            if (files.isFile()) {
                files.delete();
            } else if (files.isDirectory()) {
                deleteDir(files);
            }
        }
        dir.delete();
        Log.d(TAG, "dirToBeDeleted" + dir.getAbsolutePath());
    }

    public boolean isChecked() {
        int i;
        int[] ret = mListAdaptor.getChildCheckedStatus(BACKUP_GROUP_SYSTEM_DATA);
        boolean flag = (ret[0] > 0);

        if (flag == false) {
            Log.d(TAG, "isChecked() mAppList.size(): " + mAppList.size());
            ret = mListAdaptor.getChildCheckedStatus(BACKUP_GROUP_APPS);
            flag = (ret[0] > 0);
        }

        return flag;
    }

    @Override
    public View getHeader() {
        View headerView = (ViewGroup) getLayoutInflater().inflate(
                R.layout.backup_list_group_item, null);
        headerView.setLayoutParams(new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        return headerView;
    }

    @Override
    public void updateHeader(View headerView, int firstVisibleGroupPos) {
        CheckBox check = (CheckBox) headerView.findViewById(R.id.module_check);
        if (mListAdaptor.mGroupData.get(firstVisibleGroupPos).get(MAP_KEY_CHECKED) == "true")
            check.setChecked(true);
        else
            check.setChecked(false);
        check.setClickable(true);
        check.setOnClickListener(BackupActivity.this);
        check.setContentDescription("" + firstVisibleGroupPos + ":-1");
        TextView name = (TextView) (headerView.findViewById(R.id.module_name));
        name.setClickable(false);
        name.setText((firstVisibleGroupPos == 1)?
                R.string.backup_application : R.string.backup_systemdata);
        TextView percent = (TextView) (headerView.findViewById(R.id.module_percent));
        int[] status = mListAdaptor.getChildCheckedStatus(firstVisibleGroupPos);
        percent.setText("" + status[0] + "/" + status[1]);
        percent.setClickable(false);
        ImageView image = (ImageView) (headerView.findViewById(R.id.module_image));
        image.setImageResource(imageStatus[firstVisibleGroupPos]?
                R.drawable.expander_close : R.drawable.expander_open);
        headerView.measure(MeasureSpec.EXACTLY + headerView.getWidth(),
                MeasureSpec.EXACTLY + headerView.getHeight());
    }

    private void updateBackupButton(boolean flag) {
        mButtonBackup.setEnabled(flag);
    }

}
