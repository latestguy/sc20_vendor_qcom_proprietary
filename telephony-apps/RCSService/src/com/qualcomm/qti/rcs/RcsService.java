/**
 * Copyright (c) 2016 Qualcomm Technologies, Inc.
 * All Rights Reserved.
 * Confidential and Proprietary - Qualcomm Technologies, Inc.
 **/

package com.qualcomm.qti.rcs;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.ServiceManager;
import android.os.SystemProperties;

import org.codeaurora.rcscommon.RcsManager;

/**
 * RcsService is a service which will be used to communicate between the jio lib
 * and the rest of the application in the device(like, dialer, incall, contacts,
 * telecom)
 */
public class RcsService extends Service {

    public static final boolean DBG = (SystemProperties.getInt("ro.debuggable", 0) == 1);
    static final String TAG = "RcsService";

    public static final String INTERNAL_BROADCAST_ACTION
            = "com.qualcomm.qti.rcs.PERMISSION_RESULT_ACTION";
    public static final String ACCESS_PERMISSION
            = "com.qualcomm.qti.internal.permission.ACCESS_ENRICHED_CALL";
    public static final String PERMISSION_RESULT = "PERMISSION_RESULT";

    private RcsServiceImpl mServiceImpl;

    @Override
    public void onCreate() {
        log("onCreate");
        mServiceImpl = new RcsServiceImpl(this);
        ServiceManager.addService(RcsManager.RCSSERVICE_PUB_NAME,
                mServiceImpl.getBinder());
        sendBroadcast(new Intent(RcsManager.RCS_APP_START_ACTION));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        log("onStartCommand");
        mServiceImpl.initializeRCS();
        return Service.START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        log("onBind");
        return mServiceImpl.getBinder();
    }

    private static void log(String msg) {
        if (DBG) {
            android.util.Log.d(TAG, msg);
        }
    }

}
