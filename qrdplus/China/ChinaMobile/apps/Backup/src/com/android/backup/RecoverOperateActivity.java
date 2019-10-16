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

import com.android.backup.R;

import android.os.Environment;
import android.os.storage.StorageEventListener;
import android.os.storage.StorageManager;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.app.ActionBar;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.TabActivity;
import android.os.Bundle;
import android.view.Window;
import android.app.AlertDialog;
import android.content.Context;
import android.widget.AbsListView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SpinnerAdapter;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.Toast;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.app.ProgressDialog;
import android.os.Handler;
import android.os.Message;
import android.os.SystemService;
import android.os.Process;
import android.net.Uri;
import android.content.ContentUris;
import android.text.format.DateFormat;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.io.File;
import java.io.FileWriter;
import android.os.FileUtils;
import java.io.IOException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.FileNotFoundException;
import java.io.FileInputStream;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.ByteArrayInputStream;

import android.view.KeyEvent;
import android.view.ViewGroup.LayoutParams;
import android.content.Intent;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;

import android.content.ContentResolver;
import android.database.Cursor;
import android.database.DataSetObserver;

import com.android.backup.vmsg.ShortMessage;
import com.android.backup.vmsg.MessageParsers;
import com.android.backup.vcalendar.VCalendarManager;
import com.android.backup.vcalendar.VcalendarImporter;

import com.android.backup.vcalendar.VcalendarImporter.OnVcsInsertListener;
import android.provider.CalendarContract.Calendars;

import static com.android.backup.BackupUtils.BACKUP_TYPE_ALL;
import static com.android.backup.BackupUtils.BACKUP_TYPE_APP;
import static com.android.backup.BackupUtils.BACKUP_TYPE_CALENDAR;
import static com.android.backup.BackupUtils.BACKUP_TYPE_CONTACTS;
import static com.android.backup.BackupUtils.BACKUP_TYPE_EMAIL;
import static com.android.backup.BackupUtils.BACKUP_TYPE_MMS;
import static com.android.backup.BackupUtils.BACKUP_TYPE_PICTURE;
import static com.android.backup.BackupUtils.BACKUP_TYPE_SMS;
import static com.android.backup.BackupUtils.EVENT_FILE_CREATE_ERR;
import static com.android.backup.BackupUtils.EVENT_INIT_PROGRESS_TITLE;
import static com.android.backup.BackupUtils.EVENT_RESTORE_RESULT;
import static com.android.backup.BackupUtils.EVENT_SET_PROGRESS_VALUE;
import static com.android.backup.BackupUtils.EVENT_STOP_BACKUP;
import static com.android.backup.vcalendar.VcalendarImporter.CALENDAR_MEMO_FULL;
import static com.android.backup.vcalendar.VcalendarImporter.CALENDAR_APPOINTMENT_FULL;
import static com.android.backup.vcalendar.VcalendarImporter.CALENDAR_ANNIVERSARY_FULL;
import static com.android.backup.vcalendar.VcalendarImporter.CALENDAR_TASK_FULL;

import com.android.backup.vcard.VCardConfig;
import com.android.backup.vcard.VCardEntryCommitter;
import com.android.backup.vcard.VCardEntryConstructor;
import com.android.backup.vcard.VCardEntryCounter;
import com.android.backup.vcard.VCardInterpreter;
import com.android.backup.vcard.VCardInterpreterCollection;
import com.android.backup.vcard.VCardParser;
import com.android.backup.vcard.VCardParser_V21;
import com.android.backup.vcard.VCardParser_V30;
import com.android.backup.vcard.VCardSourceDetector;
import com.android.backup.vcard.exception.VCardException;
import com.android.backup.vcard.exception.VCardNestedException;
import com.android.backup.vcard.exception.VCardNotSupportedException;
import com.android.backup.vcard.exception.VCardVersionException;
import android.accounts.Account;

import android.provider.BaseColumns;
import android.os.Looper;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;
import android.util.Log;
import com.android.backup.ContactsContract;
import android.os.SystemProperties;
import android.os.PowerManager;
import android.provider.Settings;
import java.util.Locale;
import com.google.android.mms.util.SqliteWrapper;
import android.content.ContentValues;
import java.util.HashMap;

public class RecoverOperateActivity extends PreferenceActivity implements OnVcsInsertListener,
        View.OnClickListener {
    private static final boolean DBG = true;
    private static final String TAG = "RecoverOperateActivity";
    private static final boolean LOCAL_DEBUG = false;
    public static final int VCARD_TYPE = VCardConfig.VCARD_TYPE_DEFAULT;
    public static final String CURRENT_DIR_STR = "android.com.backup.directory";

    private static final String BACKUP_DATA_DIR = "/backup/Data/";
    private static final String DEFAULT_BACKUP_CONTACTS_DIR = "/Contact/";
    private static final String DEFAULT_BACKUP_MMS_SMS_DIR = "/Mms/";
    private static final String DEFAULT_BACKUP_SMS_DIR = "/Sms/";
    private static final String DEFAULT_BACKUP_EMAIL_DIR = "/email/";
    private static final String DEFAULT_BACKUP_CALENDAR_DIR = "/Calendar/";
    private static final String RESTORE_PATH = "/backup/restorepath";
    private static final String RESTORE_FILE = "/backup/restoreapppath.txt";
    private static final String DEFAULT_BACKUP_PICTURE_DIR = "/Image/";
    private static final String DEFAULT_BACKUP_APP_DIR = "/apps";
    private static final String RESTORE_PICTURE_ROOT_DIR = "/mnt/sdcard2/";
    private String mCurrentDir;
    private String myCurrentDir;

    private static final String KEY_BACKUP_CONTACTS = "bak_contact";
    private static final String KEY_BACKUP_SMS1 = "bak_sim1_sms";
    private static final String KEY_BACKUP_MMS1 = "bak_sim1_mms";
    private static final String KEY_BACKUP_SMS2 = "bak_sim2_sms";
    private static final String KEY_BACKUP_MMS2 = "bak_sim2_mms";
    private static final String KEY_BACKUP_EMAIL = "bak_email";
    private static final String KEY_BACKUP_CALENDAR = "bak_calendar";
    private static final String KEY_BACKUP_SPINNER_DATA = "bak_spinner_data";

    private int mSpinnerData = Integer.MIN_VALUE;

    private static final String ITEM_RECORD = "record";
    private static final String ITEM_CATEGORY = "category";
    private static final String PACKAGENAME = "packagename";
    private static final String APPNAME = "label";
    private static final String APKNAME = "name";

    private static final int BACKUP_TYPE_CALENDAR_ERROR = 9;
    private static final int MENU_MULTISEL_OK = 1;
    private static final int MENU_MULTISEL_ALL = 2;
    private static final int MENU_MULTISEL_CANCEL = 3;

    private static final int BACKUP_CONTACTS_ID = 0;
    private static final int BACKUP_SMS_ID = 1;
    private static final int BACKUP_MMS_ID = 2;
    private static final int BACKUP_CALENDAR_ID = 3;

    private static final int BACKUP_SIM1_SMS_ID = 1;
    private static final int BACKUP_SIM2_SMS_ID = 2;
    private static final int BACKUP_SIM1_MMS_ID = 3;
    private static final int BACKUP_SIM2_MMS_ID = 4;
    private static final int BACKUP_MSIM_CALENDAR_ID = 5;

    private static final int RESTORE_RESULT_FAIL = 0;
    private static final int RESTORE_RESULT_FAIL_NO_CAL_ACCOUNT = -1;
    private static final int RESTORE_RESULT_OK = 1;
    private static final int FALLBACK_RESTORE = 0;
    private static final int PATH_INTERNAL = 0;
    private static final int PATH_EXTERNAL = 1;

    private boolean mCancelRestoreFlag = false;

    private static Uri MMS_URI = Uri.parse("content://mms-sms/mailbox/");
    private static final int OUT_PUT_BUFFER_SIZE = 1024 * 4;

    private String mRecoverPath;
    private String mBackupRootDir;
    private String mBackupEmailDirFile;
    private String mBackupAppDirFile;
    private CheckBoxPreference[] mBackupSource;

    private Showconnectprogress mSendProgressDialog = null;
    private OperateThread mOperateThread = null;
    private Handler mHandler;
    private ContentResolver mResolver;
    private VCardParser mVCardParser;

    private VcalendarImporter mVcalImporter = null;

    private BackupMmsOperation mBackupMessageOper = null;
    private ApplicationCategory mAppList = null;

    private static PowerManager.WakeLock sWakeLock;
    private static final Object mWakeLockSync = new Object();

    private ProgressDialog mProgDlg = null;
    private Button mBtn;

    private SelectAdapter mAdapter;
    private Bundle mRestoreBundle = null;
    private HashMap<String,Object> mStoreStatusMap = null;

    private StorageManager mStorageManager;
    private StorageEventListener mStorageListener = new StorageEventListener() {
        @Override
        public void onStorageStateChanged(String path, String oldState, String newState) {
            Log.d(TAG, "onStorageStateChanged(), path: " + path +
                    ", oldState: " + oldState + ", newState: " + newState);
            if (!(path != null && path.equals(BackupUtils.getSDPath(getApplicationContext())))) {
                return;
            }
            if (newState.equals(Environment.MEDIA_BAD_REMOVAL) ||
                    newState.equals(Environment.MEDIA_MOUNTED) ||
                    newState.equals(Environment.MEDIA_UNMOUNTED)) {
                finish();
            }
        }
    };

    private class BackupHandler extends Handler {

        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_STOP_BACKUP: {
                    Log.d(TAG, "EVENT_STOP_BACKUP");
                    break;
                }
                case EVENT_FILE_CREATE_ERR: {
                    Log.d(TAG, "EVENT_FILE_CREATE_ERR");
                    break;
                }
                case EVENT_INIT_PROGRESS_TITLE: {
                    Log.d(TAG, "EVENT_INIT_PROGRESS_TITLE");
                    int type = msg.arg1;
                    String title = null;
                    switch (type) {
                        case BACKUP_TYPE_CONTACTS:
                            title = getString(R.string.restore_contact_title);
                            break;
                        case BACKUP_TYPE_MMS:
                            title = getString(R.string.restore_mms_title);
                            break;
                        case BACKUP_TYPE_SMS:
                            title = getString(R.string.restore_sms_title);
                            break;
                        case BACKUP_TYPE_EMAIL:
                            title = getString(R.string.restore_email_title);
                            break;
                        case BACKUP_TYPE_CALENDAR:
                            title = getString(R.string.restore_calendar_title);
                            break;
                        case BACKUP_TYPE_PICTURE:
                            title = getString(R.string.restore_picture_title);
                            break;
                        case BACKUP_TYPE_APP:
                            title = getString(R.string.restore_app_title);
                            break;
                    }
                    updateProgressDialog(title, msg.arg2);
                    break;
                }
                case EVENT_SET_PROGRESS_VALUE: {
                    Log.d(TAG, "EVENT_SET_PROGRESS_VALUE msg.arg1: " + msg.arg1
                            + " msg.arg2: " + msg.arg2);
                    if (mSendProgressDialog != null) {
                        if (msg.arg1 != BackupActivity.INVALID_ARGUMENT) {
                            mSendProgressDialog.setProgress(msg.arg1);
                        } else if (msg.arg2 != BackupActivity.INVALID_ARGUMENT) {
                            mSendProgressDialog.incrementProgressBy(msg.arg2);
                        }
                    }
                    break;
                }
                case EVENT_RESTORE_RESULT: {
                    Log.d(TAG, "EVENT_RESTORE_RESULT msg.arg1:" + msg.arg1 + " msg.arg: "
                            + msg.arg2);
                    int result = msg.arg2;
                    String title = null;
                    String strResult;
                    strResult = getString(result == RESTORE_RESULT_OK ? R.string.restore_ok
                            : R.string.restore_failed);
                    int type = msg.arg1;
                    switch (type) {
                        case BACKUP_TYPE_CONTACTS:
                        case BACKUP_TYPE_MMS:
                        case BACKUP_TYPE_SMS:
                        case BACKUP_TYPE_CALENDAR:
                            if (result == RESTORE_RESULT_OK) {
                                updateRestoreButton(true);
                                title = getString(R.string.restore_ok,
                                            mBackupRootDir);
                            }
                            else if(result == RESTORE_RESULT_FAIL_NO_CAL_ACCOUNT)
                                title = getString(R.string.restore_failed_no_cal_account);
                            else
                                title = getString(R.string.restore_failed);
                            break;
                        case BACKUP_TYPE_ALL:
                            if (mSendProgressDialog != null) {
                                Log.d(TAG, " dialog dismiss");
                                mSendProgressDialog.dismiss();
                                mSendProgressDialog = null;
                            }
                            if (result == RESTORE_RESULT_OK) {
                                updateRestoreButton(true);
                                title = getString(R.string.restore_ok,
                                            mRecoverPath);
                            }
                            else
                                title = getString(R.string.restore_failed);
                            break;
                    }
                    Log.d(TAG, "EVENT_RESTORE_RESULT title: " + title);
                    if (mSendProgressDialog != null) {
                        mSendProgressDialog.dismiss();
                        mSendProgressDialog = null;
                    }
                    if (!mCancelRestoreFlag)
                        Toast.makeText(RecoverOperateActivity.this, title, Toast.LENGTH_SHORT)
                                .show();
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
            Log.d(TAG, " run:mSendProgressDialog--1"
                    + ((mSendProgressDialog != null) ? "not null" : "null"));
            int failCnt = 0;
            // restore contacts
            if (mBackupSource[BACKUP_CONTACTS_ID].isChecked() && !mCancelRestoreFlag) {
                Log.d(TAG, "prepare restore contacts!");
                String contact_dir_file = mBackupRootDir + mCurrentDir
                        + DEFAULT_BACKUP_CONTACTS_DIR;
                Log.d(TAG, "contact_dir_file = " + contact_dir_file);
                File contacts_dir = new File(contact_dir_file);
                if (contacts_dir.exists()) {
                    File[] fileLists = contacts_dir.listFiles();
                    int num = fileLists.length;
                    for (int i = 0; i < num && (!mCancelRestoreFlag); i++) {
                        if (fileLists[i].exists()) {
                            restoreContacts(fileLists[i].toString());
                            Log.d(TAG, "restore contacts finish!");
                        } else {
                            Log.d(TAG, "OperateThread: contacts_dir is not exist: " + contacts_dir);
                            failCnt++;
                            continue;
                        }
                    }
                } else {
                    Log.d(TAG, "contacts_dir dont exist");
                    failCnt++;
                }
            }
            Log.d(TAG, " run:mSendProgressDialog--2"
                    + ((mSendProgressDialog != null) ? "not null" : "null"));

            boolean restoredMMS = false;
            // restore MMS
            if (isMmsChecked() && !mCancelRestoreFlag) {
                Log.d(TAG, "prepare restore MMS!");
                String filename = mBackupRootDir + mCurrentDir
                        + DEFAULT_BACKUP_MMS_SMS_DIR;
                File mms_dir = new File(filename);
                File[] mmsLists = mms_dir.listFiles();// mms-resume-judge
                if (mms_dir.exists() && (!(mmsLists.length == 1 && mmsLists[0].isDirectory()))) {
                    restoreMMS(filename, getMmsSlot());
                    restoredMMS = true;
                    Log.d(TAG, "restore MMS finish!");
                } else {
                    Log.d(TAG, "OperateThread: mms_dir is not exist: " + mms_dir);
                    if (!mms_dir.exists())
                        failCnt++;
                }
            }

            boolean restoredSMS = false;
            // restore SMS
            if (isSmsChecked() && !mCancelRestoreFlag) {
                Log.d(TAG, "prepare restore SMS!");
                String filename = mBackupRootDir + mCurrentDir + DEFAULT_BACKUP_SMS_DIR;
                File sms_dir = new File(filename);
                if (sms_dir.exists()) {
                    restoreSMS(filename, getSmsSlot());
                    restoredSMS = true;
                    Log.d(TAG, "restore SMS finish!");
                } else {
                    Log.d(TAG, "OperateThread: sms_dir is not exist:" + sms_dir);
                    failCnt++;
                }
            }

            if (restoredSMS || restoredMMS) {
                updateMMSThreadDate();
            }
            // restore calendar
            if (isCalendarChecked()&& !mCancelRestoreFlag) {
                Log.d(TAG, "prepare restore calendar!");
                File calendar_dir = new File(mBackupRootDir + mCurrentDir
                        + DEFAULT_BACKUP_CALENDAR_DIR);
                if (!checkForAccounts(RecoverOperateActivity.this)) {
                    mHandler.sendMessage(mHandler.obtainMessage(EVENT_RESTORE_RESULT,
                            BACKUP_TYPE_CALENDAR, RESTORE_RESULT_FAIL_NO_CAL_ACCOUNT));
                    Log.d(TAG, "OperateThread: calendar account do not exist");
                    failCnt++;
                } else if (calendar_dir.exists()) {
                    restoreCalendar(calendar_dir);
                    Log.d(TAG, "restore calendar finish!");
                } else {
                    Log.d(TAG, "OperateThread: calendar_dir is not exist:" + calendar_dir);
                    failCnt++;
                }
            }

            if (!mCancelRestoreFlag) {
                if (failCnt == 0)
                    mHandler.sendMessage(mHandler.obtainMessage(EVENT_RESTORE_RESULT,
                            BACKUP_TYPE_ALL, RESTORE_RESULT_OK));
                else
                    mHandler.sendMessage(mHandler.obtainMessage(EVENT_RESTORE_RESULT,
                            BACKUP_TYPE_ALL, RESTORE_RESULT_FAIL));
            } else {
                mHandler.sendMessage(mHandler.obtainMessage(EVENT_RESTORE_RESULT,
                        BACKUP_TYPE_ALL, RESTORE_RESULT_FAIL));
                mCancelRestoreFlag = false;
            }
        }

        private int getMmsSlot() {
            if (BackupUtils.isMultiSimEnabled()) {
                if (mBackupSource[BACKUP_SIM1_MMS_ID].isChecked()
                        && mBackupSource[BACKUP_SIM2_MMS_ID].isChecked()) {
                    return BackupUtils.SLOT_ALL;
                } else {
                    if (mBackupSource[BACKUP_SIM1_MMS_ID].isChecked()) {
                        return BackupUtils.SLOT_1;
                    } else {
                        return BackupUtils.SLOT_2;
                    }
                }
            } else {
                return BackupUtils.SLOT_ALL;
            }
        }

        private int getSmsSlot() {
            if (BackupUtils.isMultiSimEnabled()) {
                if (mBackupSource[BACKUP_SIM1_SMS_ID].isChecked()
                        && mBackupSource[BACKUP_SIM2_SMS_ID].isChecked()) {
                    return BackupUtils.SLOT_ALL;
                } else {
                    if (mBackupSource[BACKUP_SMS_ID].isChecked()) {
                        return BackupUtils.SLOT_1;
                    } else {
                        return BackupUtils.SLOT_2;
                    }
                }
            } else {
                return BackupUtils.SLOT_ALL;
            }
        }

        private boolean isMmsChecked() {
            if (BackupUtils.isMultiSimEnabled()) {
                return mBackupSource[BACKUP_SIM1_MMS_ID].isChecked()
                        || mBackupSource[BACKUP_SIM2_MMS_ID].isChecked();
            } else {
                return mBackupSource[BACKUP_MMS_ID].isChecked();
            }
        }

        private boolean isSmsChecked() {
            if (BackupUtils.isMultiSimEnabled()) {
                return mBackupSource[BACKUP_SIM1_SMS_ID].isChecked()
                        || mBackupSource[BACKUP_SIM2_SMS_ID].isChecked();
            } else {
                return mBackupSource[BACKUP_SMS_ID].isChecked();
            }
        }

        private boolean isCalendarChecked() {
            if (BackupUtils.isMultiSimEnabled()) {
                return mBackupSource[BACKUP_MSIM_CALENDAR_ID].isChecked();
            } else {
                return mBackupSource[BACKUP_CALENDAR_ID].isChecked();
            }
        }
    }

    private void deleteDir(String str) {
        File dir;
        try {
            dir = new File(str);
        } catch (Exception e) {
            return;
        }
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
                deleteDir(files.toString());
            }
        }
        dir.delete();
        if (DBG)
            Log.d(TAG, "dirToBeDeleted" + dir.getAbsolutePath());
    }

    private boolean restoreContacts(String filename) {
        if (DBG)
            Log.d(TAG, " restoreContacts:mSendProgressDialog"
                    + ((mSendProgressDialog != null) ? "not null" : "null"));
        final Uri uri = Uri.parse("file://" + filename);
        Log.v(TAG, "restoreContacts path = " + uri);

        // Update the progress bar title

        mResolver = RecoverOperateActivity.this.getContentResolver();

        VCardEntryCounter counter = new VCardEntryCounter();
        VCardSourceDetector detector = new VCardSourceDetector();
        VCardInterpreterCollection builderCollection = new VCardInterpreterCollection(
                Arrays.asList(counter, detector));
        boolean result;
        try {
            result = readOneVCardFile(uri,
                    VCardConfig.DEFAULT_CHARSET, builderCollection, null, true, null, true);
        } catch (VCardNestedException e) {
            try {
                // Assume that VCardSourceDetector was able to detect the
                // source.
                // Try again with the detector.
                result = readOneVCardFile(uri,
                        VCardConfig.DEFAULT_CHARSET, counter, detector, false, null, true);
            } catch (VCardNestedException e2) {
                result = false;
                Log.e(TAG, "Must not reach here. " + e2);
            }
        }
        Log.d(TAG, "counter = " + counter.getCount());
        mHandler.sendMessage(mHandler.obtainMessage(EVENT_INIT_PROGRESS_TITLE,
                BACKUP_TYPE_CONTACTS, counter.getCount()));
        String charset = detector.getEstimatedCharset();
        doActuallyReadOneVCard(uri, null,
                charset, false, detector, null);

        return result;
    }

    private Uri doActuallyReadOneVCard(Uri uri, Account account,
            String charset, boolean showEntryParseProgress,
            VCardSourceDetector detector, List<String> errorFileNameList) {
        if (DBG)
            Log.d(TAG, " doActuallyReadOneVCard:mSendProgressDialog"
                    + ((mSendProgressDialog != null) ? "not null" : "null"));
        final Context context = RecoverOperateActivity.this;
        mResolver = context.getContentResolver();
        VCardEntryConstructor builder;

        int vcardType = VCARD_TYPE;
        if (charset != null) {
            builder = new VCardEntryConstructor(charset, charset, false, vcardType, null);
        } else {
            charset = VCardConfig.DEFAULT_CHARSET;
            builder = new VCardEntryConstructor(null, null, false, vcardType, null);
        }

        VCardEntryCommitter committer = new VCardEntryCommitter(mResolver);
        builder.addEntryHandler(committer);

        try {
            if (!readOneVCardFile(uri, charset, builder, detector, false, null, false)) {
                return null;
            }
        } catch (VCardNestedException e) {
            Log.e(TAG, "Never reach here.");
        }
        final ArrayList<Uri> createdUris = committer.getCreatedUris();
        return (createdUris == null || createdUris.size() != 1) ? null : createdUris.get(0);
    }

    private boolean readOneVCardFile(Uri uri, String charset,
            VCardInterpreter builder, VCardSourceDetector detector,
            boolean throwNestedException, List<String> errorFileNameList, boolean isFirst)
            throws VCardNestedException {
        if (DBG)
            Log.d(TAG, " readOneVCardFile:mSendProgressDialog is "
                    + ((mSendProgressDialog != null) ? "not null" : "null"));
        InputStream is;
        try {
            is = mResolver.openInputStream(uri);
            if (!isFirst)
                mVCardParser = new VCardParser_V21(detector, mHandler);
            else
                mVCardParser = new VCardParser_V21(detector);

            try {
                ContactsContract.enableTransactionLock(getPackageName(), getContentResolver());
                Log.d(TAG, "readOneVCardFile cancel flag: " + mCancelRestoreFlag);
                mVCardParser.parse(is, charset, builder, mCancelRestoreFlag);
            } catch (VCardVersionException e1) {
                try {
                    is.close();
                } catch (IOException e) {
                }
                if (builder instanceof VCardEntryConstructor) {
                    // Let the object clean up internal temporal objects,
                    ((VCardEntryConstructor) builder).clear();
                }
                is = mResolver.openInputStream(uri);

                try {
                    mVCardParser = new VCardParser_V30();
                    mVCardParser.parse(is, charset, builder, mCancelRestoreFlag);
                } catch (VCardVersionException e2) {
                    throw new VCardException("vCard with unspported version.");
                }
            } finally {
                ContactsContract.disableTransactionLock(getPackageName(), getContentResolver());
                if (is != null) {
                    try {
                        is.close();
                    } catch (IOException e) {
                    }
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "IOException was emitted: " + e.getMessage());

            return false;
        } catch (VCardNotSupportedException e) {
            Log.e(TAG, "VCardNotSupportedException was emitted: " + e.getMessage());
            return false;
        } catch (VCardException e) {
            Log.e(TAG, "VCardException was emitted: " + e.getMessage());
            return false;
        }
        return true;
    }

    private boolean restoreMMS(String dir, int slot) {
        Log.d(TAG, "restoreMMS() dir " + dir);
        mBackupMessageOper = new BackupMmsOperation(this, mHandler, dir, slot, mRecoverPath);
        mBackupMessageOper.recoverMms(dir);
        mBackupMessageOper = null;
        return true;
    }

    private boolean restoreSMS(String dir, int slot) {
        mBackupMessageOper = new BackupMmsOperation(this, mHandler, dir, slot, mRecoverPath);
        mBackupMessageOper.recoverSmsMessagesWithVmsg(dir);
        mBackupMessageOper = null;
        return true;
    }

    private void updateMMSThreadDate() {
        final Uri sUpdateUri = Uri.parse("content://mms-sms/update-date");
        SqliteWrapper.update(RecoverOperateActivity.this,
               RecoverOperateActivity.this.getContentResolver(), sUpdateUri,
               new ContentValues(), null, null);
    }

    public void importProgressSet(int progress) {
    }

    private boolean checkForAccounts(Context context) {
        ContentResolver mContentResolver = context.getContentResolver();
        Cursor mCursor = mContentResolver.query(Calendars.CONTENT_URI, null, null, null, null);
        if (mCursor != null) {
            int count = mCursor.getCount();
            if (count != 0) {
                return true;
            } else {
                return false;
            }
        }
        return false;
    }

    private boolean restoreCalendar(File vcsfile) {
        String myFileDir = vcsfile.toString();
        File[] vcsList = vcsfile.listFiles();
        int length = vcsList.length;
        int success = 1;
        for (int i = 0; i < length; i++) {
            Log.d(TAG, "mCancelRestoreFlag = " + mCancelRestoreFlag);
            if(mCancelRestoreFlag) break;
            mVcalImporter = new VcalendarImporter(RecoverOperateActivity.this,
                    RecoverOperateActivity.this, mHandler);
            if (!vcsList[i].toString().endsWith("vcs")) {
                continue;
            }
            if (DBG)
                Log.d(TAG, "vcsList = " + vcsList[i].toString());
            success = mVcalImporter.insertFromFile(vcsList[i]);
            if (DBG)
                Log.d(TAG, "restoreCalendar _insertFromFile() return " + success);
        }
        // release
        mVcalImporter = null;

        return true;
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
            if (!LockedView && mContext != null
                    && !RecoverOperateActivity.this.isFinishing())
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
                new AlertDialog.Builder(RecoverOperateActivity.this,
                        android.R.style.Theme_DeviceDefault_Dialog_NoActionBar)
                        .setMessage(R.string.confirm_stop_restore)
                        .setNegativeButton(R.string.button_cancel, null)
                        .setPositiveButton(R.string.button_ok,
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int whichButton)
                                    {
                                        setCancelRestoreFlag(true);
                                        stopRestore();
                                        if (mSendProgressDialog != null) {
                                            mSendProgressDialog.dismiss();
                                            mSendProgressDialog = null;
                                        }
                                        Toast.makeText(RecoverOperateActivity.this,
                                                R.string.stop_restore_info, Toast.LENGTH_SHORT)
                                                .show();
                                        finish();
                                        updateRestoreButton(true);
                                    }
                                })
                        .show();
            }
            if ((mSendProgressDialog != null) && (keyCode == KeyEvent.KEYCODE_SEARCH)) {
                return true;
            }
            return false;
        }
    }

    private void stopRestore(){
        // Stop restore MMS/SMS
        if (mBackupMessageOper != null) {
            mBackupMessageOper.cancel();
        }
        // Stop restore Calendar
        if (mVcalImporter != null) {
            mVcalImporter.cancel();
        }
        // Stop restore Contacts
        if (mVCardParser != null) {
            mVCardParser.cancel();
        }
    }

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.recovery_main);
        addPreferencesFromResource(R.xml.system_software1);

        mHandler = new BackupHandler();
        mCancelRestoreFlag = false;
        ActionBar actionBar = getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
        mAdapter = new SelectAdapter();
        actionBar.setListNavigationCallbacks(mAdapter, mAdapter);

        setTitle("");
        mBtn = (Button) findViewById(R.id.recovery_btn);
        mBtn.setOnClickListener(this);

        mRestoreBundle = icicle;

        mStorageManager = StorageManager.from(this);
        mStorageManager.registerListener(mStorageListener);
    }

    private void initData(){

        mRecoverPath = BackupUtils.getRestorePath(this);
        mBackupRootDir = mRecoverPath + BACKUP_DATA_DIR;

        Intent intent = getIntent();
        mCurrentDir = intent.getStringExtra(CURRENT_DIR_STR);
        if (BackupUtils.isMultiSimEnabled()) {
            mBackupSource = new CheckBoxPreference[6];
            mBackupSource[BACKUP_CONTACTS_ID] =
                    (CheckBoxPreference) findPreference(KEY_BACKUP_CONTACTS);
            mBackupSource[BACKUP_SIM1_MMS_ID] =
                    (CheckBoxPreference) findPreference(KEY_BACKUP_MMS1);
            mBackupSource[BACKUP_SIM2_MMS_ID] =
                    (CheckBoxPreference) findPreference(KEY_BACKUP_MMS2);
            mBackupSource[BACKUP_SIM1_SMS_ID] =
                    (CheckBoxPreference) findPreference(KEY_BACKUP_SMS1);
            mBackupSource[BACKUP_SIM2_SMS_ID] =
                    (CheckBoxPreference) findPreference(KEY_BACKUP_SMS2);
            mBackupSource[BACKUP_MSIM_CALENDAR_ID] =
                    (CheckBoxPreference) findPreference(KEY_BACKUP_CALENDAR);
            String smsName = getResources().getString(R.string.backup_sms);
            mBackupSource[BACKUP_SIM1_SMS_ID].setTitle(
                    BackupUtils.getMultiSimName(this, BackupUtils.SLOT_1) + " " + smsName);
            mBackupSource[BACKUP_SIM2_SMS_ID].setTitle(
                    BackupUtils.getMultiSimName(this, BackupUtils.SLOT_2) + " " + smsName);
            String mmsName = getResources().getString(R.string.backup_mms);
            mBackupSource[BACKUP_SIM1_MMS_ID].setTitle(
                    BackupUtils.getMultiSimName(this, BackupUtils.SLOT_1) + " " + mmsName);
            mBackupSource[BACKUP_SIM2_MMS_ID].setTitle(
                    BackupUtils.getMultiSimName(this, BackupUtils.SLOT_2) + " " + mmsName);
        } else {
            mBackupSource = new CheckBoxPreference[4];
            mBackupSource[BACKUP_CONTACTS_ID] =
                    (CheckBoxPreference) findPreference(KEY_BACKUP_CONTACTS);
            mBackupSource[BACKUP_MMS_ID] = (CheckBoxPreference) findPreference(KEY_BACKUP_MMS1);
            mBackupSource[BACKUP_SMS_ID] = (CheckBoxPreference) findPreference(KEY_BACKUP_SMS1);
            mBackupSource[BACKUP_CALENDAR_ID] =
                    (CheckBoxPreference) findPreference(KEY_BACKUP_CALENDAR);
            getPreferenceScreen().removePreference(findPreference(KEY_BACKUP_MMS2));
            getPreferenceScreen().removePreference(findPreference(KEY_BACKUP_SMS2));
        }

        for (CheckBoxPreference p : mBackupSource) {
            p.setOnPreferenceClickListener(mAdapter);
            p.setEnabled(false);
        }

        containsWhat(mBackupRootDir + mCurrentDir);

        mAdapter.updateTitle();
    }

    class SelectAdapter implements SpinnerAdapter, ActionBar.OnNavigationListener,
            Preference.OnPreferenceClickListener
    {
        private static final int INIT_DATA = -1;
        private static final int SELECTED_DATA = 0;
        private static final int DEFAULT_DATA = 2;

        private int mPreState = (mSpinnerData == Integer.MIN_VALUE)? INIT_DATA:mSpinnerData;

        private TextView mTitleTextView;
        private View mTitleView;
        private View mDropView;
        private TextView mTextView;

        public void updateTitle() {
            PrepareObj();
            mTitleTextView.setText(getTitle());
            mTitleTextView.invalidate();
        }

        @Override
        public boolean onNavigationItemSelected(int itemPosition, long itemId) {
            if (mPreState == INIT_DATA) {
                mPreState = itemPosition;
            } else if (mPreState == DEFAULT_DATA && itemPosition == SELECTED_DATA) {
                RecoverOperateActivity.this.getActionBar()
                        .setSelectedNavigationItem(SELECTED_DATA);
                mPreState = INIT_DATA;
            }
            return true;
        }

        @Override
        public int getCount() {
            return 1;
        }

        @Override
        public Object getItem(int position) {
            return getTitle();
        }

        private int getSelectCnt()
        {
            int checkCnt = 0;
            for (CheckBoxPreference p : mBackupSource) {
                if (p.isChecked()) {
                    checkCnt++;
                }
            }
            return checkCnt;
        }

        private String getTitle()
        {
            String title = RecoverOperateActivity.this.getString(R.string.menu_delete_sel);
            return "     " + getSelectCnt() + " " + title;
        }

        private void PrepareObj()
        {
            if (mTitleView == null) {
                mTitleView = new LinearLayout(RecoverOperateActivity.this);
                ((LinearLayout) mTitleView).setOrientation(LinearLayout.VERTICAL);
                mTitleTextView = new TextView(RecoverOperateActivity.this);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT);
                //params.topMargin = 20;
                mTitleTextView.setLayoutParams(params);
                ((LinearLayout) mTitleView).addView(mTitleTextView);
                mTitleView.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT,
                        LayoutParams.FILL_PARENT));
            }
        }

        @Override
        public long getItemId(int position) {
            return (long) position;
        }

        @Override
        public int getItemViewType(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            PrepareObj();
            mTitleTextView.setText(getTitle());
            return mTitleView;
        }

        @Override
        public int getViewTypeCount() {
            return 1;
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public void registerDataSetObserver(DataSetObserver observer) {
            // Set the default selected navigation item in list, 0 for the first item
            int data = (mSpinnerData == Integer.MIN_VALUE)? DEFAULT_DATA:mSpinnerData;
            RecoverOperateActivity.this.getActionBar().setSelectedNavigationItem(data);
        }

        @Override
        public void unregisterDataSetObserver(DataSetObserver observer) {
            int itemIndex = RecoverOperateActivity.this.getActionBar()
                    .getSelectedNavigationIndex();
            if (mPreState == DEFAULT_DATA && mPreState == itemIndex) {
                RecoverOperateActivity.this.getActionBar()
                        .setSelectedNavigationItem(SELECTED_DATA);
            } else if (itemIndex == SELECTED_DATA) {
                if (isAllSelected()) {
                    for (CheckBoxPreference p : mBackupSource) {
                        p.setChecked(false);
                    }
                    mBtn.setEnabled(false);
                } else {
                    for (CheckBoxPreference p : mBackupSource) {
                        if (p.isEnabled()) {
                            p.setChecked(true);
                        }
                    }
                    mBtn.setEnabled(true);
                }
                mTitleTextView.setText(getTitle());
                mTitleTextView.invalidate();
            }
        }

        public boolean isAllSelected()
        {
            for (CheckBoxPreference p : mBackupSource) {
                if (p.isEnabled() && !p.isChecked()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            if (mDropView == null) {
                mDropView = new LinearLayout(RecoverOperateActivity.this);
                ((LinearLayout) mDropView).setOrientation(LinearLayout.HORIZONTAL);
                mTextView = new TextView(RecoverOperateActivity.this);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT);
                params.setMargins(20, 0, 0, 0);
                params.gravity = Gravity.CENTER_VERTICAL;
                mTextView.setLayoutParams(params);
                ((LinearLayout) mDropView).addView(mTextView);
                mDropView.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                        LayoutParams.MATCH_PARENT));
            }
            if (mTitleView != null)
                mDropView.setLayoutParams(new AbsListView.LayoutParams(
                        mTitleView.getWidth() * 5 / 4,
                        mTitleView.getHeight() * 5 / 4));
            if (isAllSelected())
                mTextView.setText(R.string.menu_deselect_all);
            else
                mTextView.setText(R.string.menu_select_all);
            return mDropView;
        }

        @Override
        public boolean onPreferenceClick(Preference preference) {
            PrepareObj();
            mTitleTextView.setText(getTitle());
            mTitleTextView.invalidate();
            if(getSelectCnt() > 0) {
                mBtn.setEnabled(true);
            } else {
                mBtn.setEnabled(false);
            }
            return true;
        }

    }

    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        initData();
        synchronized (mWakeLockSync) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            sWakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK |
                    PowerManager.ACQUIRE_CAUSES_WAKEUP |
                    PowerManager.ON_AFTER_RELEASE,
                    TAG);
            sWakeLock.acquire();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        Log.d(TAG, "onSaveInstanceState");
        outState.putSerializable("checkedMap",getStoreData());
        super.onSaveInstanceState(outState);
    }

    private HashMap getStoreData() {
        HashMap<String,Object> map = new HashMap<String,Object>();

        for (CheckBoxPreference p : mBackupSource) {
            Log.d(TAG, p.getKey() + " --- " + p.isChecked());
            map.put(p.getKey(),p.isChecked());
        }

        map.put(KEY_BACKUP_SPINNER_DATA, mAdapter.mPreState);

        return map;
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
        if (mStorageManager != null && mStorageListener != null) {
            mStorageManager.unregisterListener(mStorageListener);
        }
    }

    protected void onStop() {
        super.onStop();
    }

    public boolean onOptionsItemSelected(MenuItem item)
    {
        Log.d(TAG, "onOptionsItemSelected item" + item);

        switch (item.getItemId())
        {
            case MENU_MULTISEL_OK:
                Log.d(TAG, "onOptionsItemSelected MENU_MULTISEL_OK" + mBackupSource.length);
                if (isChecked()) {
                    operateRestore();
                }
                else {
                    Log.d(TAG, "on MENU_MULTISEL_OK no Item Checked");
                    Toast.makeText(getApplicationContext(), R.string.stop_no_item_selected, 2)
                            .show();
                }
                return true;

            case MENU_MULTISEL_ALL:
                allSelect(true);
                return true;

            case MENU_MULTISEL_CANCEL:
                allSelect(false);
                return true;

            default:
                return super.onOptionsItemSelected(item);

        }
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        Log.d(TAG, "onPreferenceTreeClick ");
        if (preference instanceof ApplicationPreference) {
            ApplicationPreference appPreference = (ApplicationPreference) preference;
            AppInfo app = appPreference.getCachedAppInfo();
            app.print();
            appPreference.setChecked();
            return true;
        }

        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    private void allSelect(boolean enable) {
        Log.d(TAG, "allSelect enable" + enable);

        for (int i = 0; i < mBackupSource.length; i++) {
            if (mBackupSource[i].isEnabled()) {
                mBackupSource[i].setChecked(enable);
            }
        }
    }

    private void operateRestore() {
        Log.d(TAG, "operateRestore start");
        updateRestoreButton(false);
        mSendProgressDialog = new Showconnectprogress(this);
        mSendProgressDialog.setTitle(R.string.restore_title);
        mSendProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mSendProgressDialog.setCanceledOnTouchOutside(false);
        mSendProgressDialog.setCancelable(true);
        mSendProgressDialog.setButton(ProgressDialog.BUTTON_NEGATIVE,
                getString(R.string.button_stop), new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mSendProgressDialog.LockedView = true;
                        new AlertDialog.Builder(RecoverOperateActivity.this,
                                android.R.style.Theme_DeviceDefault_Dialog_NoActionBar)
                                .setMessage(R.string.confirm_stop_restore)
                                .setNegativeButton(R.string.button_cancel, null)
                                .setPositiveButton(R.string.button_ok,
                                        new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface dialog,
                                                    int whichButton) {
                                                setCancelRestoreFlag(true);
                                                stopRestore();

                                                if (mSendProgressDialog != null) {
                                                    mSendProgressDialog.dismiss();
                                                    mSendProgressDialog = null;
                                                }

                                                Toast.makeText(RecoverOperateActivity.this,
                                                    R.string.stop_restore_info, Toast.LENGTH_SHORT).show();
                                                updateRestoreButton(true);
                                            }
                                        }).show();
                    }
                });
        mSendProgressDialog.show();
        if (LOCAL_DEBUG)
            Log.v(TAG, "Show the progress bar");
        mOperateThread = new OperateThread();
        Thread thread = new Thread(mOperateThread);
        thread.start();
    }

    private void updateProgressDialog(String title, int max) {
        if (mSendProgressDialog != null) {
            mSendProgressDialog.setTitle(R.string.restore_title);
            if (max > 0) {
                mSendProgressDialog.setMax(max);
            }
            mSendProgressDialog.setProgress(0);
        }
    }

    public boolean getCancelRestoreFlag() {
        return mCancelRestoreFlag;
    }

    public void setCancelRestoreFlag(boolean flag) {
        mCancelRestoreFlag = flag;
    }

    public boolean isChecked() {
        int i;
        boolean flag = false;
        for (i = 0; i < mBackupSource.length; i++) {
            if (mBackupSource[i].isChecked()) {
                flag = true;
            }
            if (i == mBackupSource.length) {
                flag = false;
            }
        }
        return flag;
    }

    private boolean containsWhat(String dir) {
        File contains = new File(dir);
        File[] list = contains.listFiles();
        for (int i = 0; i < mBackupSource.length; i++) {
            // first ,we should set all of the mBackupSources false
            mBackupSource[i].setChecked(false);
        }
        if (list == null) {
            Log.d(TAG, "containswhat list = null");
            return false;
        }

        String fileSep = File.separator;
        for (int i = 0; i < list.length; i++) {
            Log.d(TAG, "list[i].toString()=" + list[i].toString());
            String[] folders = list[i].toString().split(fileSep);
            String type = folders[folders.length-1];
            Log.d(TAG, "folder name : " + type);
            switch(type) {
                case "Contact":
                    mBackupSource[BACKUP_CONTACTS_ID].setEnabled(true);
                    break;
                case "Mms":
                    checkMms();
                    break;
                case "Sms":
                    checkSms();
                    break;
                case "Calendar":
                    checkCalendar();
                    break;
            }
        }

        if ( mRestoreBundle != null ) {
            restoreCheckedStatus((HashMap)mRestoreBundle.getSerializable("checkedMap"));
            mRestoreBundle = null;
        } else if ( mStoreStatusMap != null ) {
            restoreCheckedStatus(mStoreStatusMap);
            mStoreStatusMap = null;
        } else {
            for (int i = 0; i < mBackupSource.length; i++) {
                if (mBackupSource[i].isEnabled()) {
                    mBackupSource[i].setChecked(true);
                } else {
                    mBackupSource[i].setChecked(false);
                }
            }
        }

        return true;
    }

    private void checkCalendar() {
        if (BackupUtils.isMultiSimEnabled()) {
            mBackupSource[BACKUP_MSIM_CALENDAR_ID].setEnabled(true);
        } else {
            mBackupSource[BACKUP_CALENDAR_ID].setEnabled(true);
        }
    }

    private void checkMms() {
        if (BackupUtils.isMultiSimEnabled()) {
            int slot = BackupUtils.getBackUpMmsSmsSlot(this, "/" + mCurrentDir + "_Mms");
            if (BackupUtils.SLOT_1 == slot) {
                mBackupSource[BACKUP_SIM1_MMS_ID].setEnabled(true);
            } else if (BackupUtils.SLOT_2 == slot) {
                mBackupSource[BACKUP_SIM2_MMS_ID].setEnabled(true);
            } else {
                mBackupSource[BACKUP_SIM1_MMS_ID].setEnabled(true);
                mBackupSource[BACKUP_SIM2_MMS_ID].setEnabled(true);
            }
        } else {
            mBackupSource[BACKUP_MMS_ID].setEnabled(true);
        }
    }

    private void checkSms() {
        if (BackupUtils.isMultiSimEnabled()) {
            int slot = BackupUtils.getBackUpMmsSmsSlot(this, "/" + mCurrentDir + "_Sms");
            if (BackupUtils.SLOT_1 == slot) {
                mBackupSource[BACKUP_SIM1_SMS_ID].setEnabled(true);
            } else if (BackupUtils.SLOT_2 == slot) {
                mBackupSource[BACKUP_SIM2_SMS_ID].setEnabled(true);
            } else {
                mBackupSource[BACKUP_SIM1_SMS_ID].setEnabled(true);
                mBackupSource[BACKUP_SIM2_SMS_ID].setEnabled(true);
            }
        } else {
            mBackupSource[BACKUP_SMS_ID].setEnabled(true);
        }
    }

    @Override
    public void onClick(View v) {
        if (!mCancelRestoreFlag && isChecked()) {
            operateRestore();
        } else if (mCancelRestoreFlag) {
            Toast.makeText(getApplicationContext(), R.string.stop_restore_stopping, 2)
                    .show();
        } else {
            Log.d(TAG, "on MENU_MULTISEL_OK no Item Checked");
            Toast.makeText(getApplicationContext(), R.string.stop_no_item_selected, 2)
                    .show();
        }
    }

    private void restoreCheckedStatus(HashMap<String,Object> map){
        Log.d(TAG, "restoreCheckedStatus");

        for (CheckBoxPreference p : mBackupSource) {
            if (!map.containsKey(p.getKey())) continue;
            Log.d(TAG, p.getKey() + " isChecked: " + isChecked());
            if (p.isEnabled()){
                p.setChecked((boolean)map.get(p.getKey()));
            }
        }

        if(mAdapter.getSelectCnt() > 0) {
            mBtn.setEnabled(true);
        } else {
            mBtn.setEnabled(false);
        }

        mSpinnerData = (int)map.get(KEY_BACKUP_SPINNER_DATA);
    }

    private void updateRestoreButton(boolean flag) {
        mBtn.setEnabled(flag);
    }
}
