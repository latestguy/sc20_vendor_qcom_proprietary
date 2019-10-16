/**
 * Copyright (c) 2015 Qualcomm Technologies, Inc.
 * All Rights Reserved.
 * Confidential and Proprietary - Qualcomm Technologies, Inc
 */

package com.oma.drm.server;

import android.app.DownloadManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.drm.DrmErrorEvent;
import android.drm.DrmEvent;
import android.drm.DrmInfoEvent;
import android.drm.DrmManagerClient;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Downloads;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;


public class DrmManagerClientServer extends Service implements
        DrmManagerClient.OnErrorListener, DrmManagerClient.OnEventListener,
        DrmManagerClient.OnInfoListener {

    private static final String TAG = "OmaDrm:Server";
    private static final boolean DEBUG = true;
    private static final String ASSET_FILE_PATH = "com.oma.drm/temp.dm";
    private static final String CACHE_FILE_NAME = "__com.oma.drm_omadrmengine_temp.dm";

    public static final String KEY_CONTENT_ID = "CONTENT_ID";
    public static final String KEY_FILE_PATH = "FILE_PATH";
    public static final String KEY_FILE_DATA = "FILE_DATA";
    public static final String KEY_DM_SERVICE = "SERVICE";

    public static final String KEY_EVENT_MESSAGE = "info_event_message";
    public static final String KEY_EVENT_TYPE = "info_event_type";

    private static final String DRM_MIMETYPE_RIGHTS_XML_STRING = "application/vnd.oma.drm.rights+xml";

    private static final String DRM_MIMETYPE_RIGHTS_WBXML_STRING = "application/vnd.oma.drm.rights+wbxml";

    DrmManagerClient mDrmManagerClient;

    private DownloadManager mDownloadManager = null;
    private Cursor mMonitorCursor = null;
    ContentMonitorObserver mDownloadObserver = new ContentMonitorObserver(
            Downloads.Impl.CONTENT_URI);

    final String[] PROJECTION = new String[]{Downloads.Impl._DATA,
            Downloads.Impl.COLUMN_STATUS, Downloads.Impl.COLUMN_MIME_TYPE,
            Downloads.Impl._ID,};


    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        try {
            mDrmManagerClient = new DrmManagerClient(this);
            Log.i(TAG, "Creating new DrmManagerClientServer");

            mDrmManagerClient.setOnErrorListener(this);

            mDrmManagerClient.setOnEventListener(this);

            mDrmManagerClient.setOnInfoListener(this);

            // This is a fake request is to capture info listener client id
            // in OMA DRM Engine
            String filePath = getCacheFilePath();
            if (!TextUtils.isEmpty(filePath)) {
                String mime = mDrmManagerClient.getOriginalMimeType(filePath);
                if (!TextUtils.isEmpty(mime)) {
                    Log.i(TAG, "Successfully Registered OmaDrm Info Client");
                }else{
                    Log.w(TAG, "Fail to Register OmaDrm Info Client");
                }
            }

            // Register observer for DRM right file downloaded
            mDownloadManager = (DownloadManager) getSystemService(
                    Context.DOWNLOAD_SERVICE);
            DownloadManager.Query baseQuery = new DownloadManager.Query()
                    .setOnlyIncludeVisibleInDownloadsUi(true);

            if (mDownloadManager != null && baseQuery != null){
                mMonitorCursor = mDownloadManager.query(baseQuery);
                if (mMonitorCursor != null) {
                    mMonitorCursor.registerContentObserver(mDownloadObserver);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error while start com.oma.drm.server : " + e);
        }

        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (DEBUG) {
            Log.d(TAG, "DrmManagerClientServer service started");
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        mDrmManagerClient.release();
        if (mMonitorCursor != null){
            mMonitorCursor.unregisterContentObserver(mDownloadObserver);
        }
        super.onDestroy();
    }

    @Override
    public void onInfo(DrmManagerClient client, DrmInfoEvent event) {
        if (DEBUG) {
            Log.d(TAG, "onInfo : " + event.getType());
        }
        if (event.getType() == DrmInfoEvent.TYPE_RIGHTS_REMOVED) {
            showRightsExpiredDialog(event);
        }

    }

    @Override
    public void onEvent(DrmManagerClient client, DrmEvent event) {
        if (DEBUG) {
            Log.d(TAG, "onEvent : " + event.getType());
        }

    }

    @Override
    public void onError(DrmManagerClient client, DrmErrorEvent event) {
        if (DEBUG) {
            Log.d(TAG, "onError : " + event.getType());
        }
    }

    private void showRightsExpiredDialog(final DrmInfoEvent event) {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName("com.oma.drm", "com.oma.drm.ui.DrmLicenseExpireDialogActivity"));
        intent.putExtra(KEY_EVENT_TYPE, event.getType());
        intent.putExtra(KEY_EVENT_MESSAGE, event.getMessage());
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private class ContentMonitorObserver extends ContentObserver {
        public ContentMonitorObserver(Uri uri) {
            super(new Handler());
        }

        @Override
        public void onChange(boolean selfChange) {
            Log.d(TAG, "DownloadMonitorService onChange");
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            Cursor cursor = getContentResolver().query(
                    Downloads.Impl.ALL_DOWNLOADS_CONTENT_URI, PROJECTION, null,
                    null, null);
            try {
                if (cursor != null && cursor.moveToLast()) {
                    final int filenameIndex = cursor
                            .getColumnIndexOrThrow(Downloads.Impl._DATA);
                    final int statusIndex = cursor.getColumnIndexOrThrow(
                            Downloads.Impl.COLUMN_STATUS);
                    final int mimeIndex = cursor.getColumnIndexOrThrow(
                            Downloads.Impl.COLUMN_MIME_TYPE);
                    String filename = cursor.getString(filenameIndex);
                    String mimeType = cursor.getString(mimeIndex);
                    int status = cursor.getInt(statusIndex);

                    final int idIndex = cursor
                            .getColumnIndexOrThrow(Downloads.Impl._ID);
                    long id = cursor.getLong(idIndex);
                    //Log.d(TAG, "id: " + id + " : " + idIndex);
                    if (DRM_MIMETYPE_RIGHTS_XML_STRING.equals(mimeType)
                            || DRM_MIMETYPE_RIGHTS_WBXML_STRING
                            .equals(mimeType)) {
                        if (Downloads.Impl.isStatusCompleted(status)
                                && Downloads.Impl.isStatusSuccess(status)) {
                            Intent drmIntent = new Intent();
                            drmIntent.setComponent(new ComponentName("com.oma.drm", "com.oma.drm.ui.DrmRightsInstallerActivity"));
                            drmIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            drmIntent.setAction(Intent.ACTION_VIEW);
                            drmIntent.setType(mimeType);
                            drmIntent.putExtra(KEY_CONTENT_ID, id);
                            drmIntent.putExtra(KEY_FILE_PATH, filename);
                            drmIntent.putExtra(KEY_DM_SERVICE, true);
                            getApplicationContext().startActivity(drmIntent);
                        }
                    }
                }
            } catch (Exception ee) {
                Log.d(TAG, "Exception ee " + ee.getMessage());
            } finally {
                cursor.close();
            }
        }
    }

    String getCacheFilePath() {
        try {
            DrmManagerClient mDrmManagerClient = null;
            AssetManager am = getAssets();
            InputStream inputStream = am.open(ASSET_FILE_PATH);

            File file = new File(getCacheDir(), CACHE_FILE_NAME);
            if (file.exists()) {
                file.delete();
            }
            OutputStream outputStream = new FileOutputStream(file);
            byte buffer[] = new byte[1024];
            int length = 0;

            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
            outputStream.close();
            inputStream.close();
            return file.getAbsolutePath();
        } catch (Exception e) {
            Log.e(TAG, "Exception : " + e);
            return null;
        }
    }
}

