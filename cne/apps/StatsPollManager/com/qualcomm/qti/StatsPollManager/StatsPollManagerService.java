/*
 * Copyright (c) 2016 Qualcomm Technologies, Inc.
 * All Rights Reserved.
 * Confidential and Proprietary - Qualcomm Technologies, Inc.
 */

package com.qualcomm.qti.StatsPollManager;

import static android.Manifest.permission.CONNECTIVITY_INTERNAL;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.INetworkManagementEventObserver;
import android.os.IBinder;
import android.os.INetworkManagementService;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.telephony.PhoneStateListener;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import com.android.server.net.BaseNetworkObserver;

public class StatsPollManagerService extends Service {

    public static final String TAG = "StatsPollManagerService";

    private static final String LIMIT_GLOBAL_ALERT = "globalAlert";
    private static final String NETSTATS_GLOBAL_ALERT_BYTES = "netstats_global_alert_bytes";
    private static final long MB_IN_BYTES = 1 * 1024 * 1024;

    private static final long TWO_MB = 2 * MB_IN_BYTES;
    private static final long TWENTY_MB = 20 * MB_IN_BYTES;
    private static final long FIFTY_MB = 50 * MB_IN_BYTES;

    private static long currBufferValue = TWO_MB;

    /* T1 = Time taken to consume 2 MB data for low tput cases (less than CAT6 rates)
     * T2 = Time taken to consume 20 MB data for mid tput cases (CAT6 rates)
     * T3 = Time taken to consume 50 MB data for high tput cases (CAT7, CAT8 or more)
     *
     * So, for low tput, buffer values stays 2 MB for a longer time. For mid tputs,
     * 20 MB and for high tputs, buffer stays on 50 MB
     *
     *
     *           |     low        mid       high
     *    _______|___________________________________
     *           |
     *    2MB    |     T1         T1-        T1--
     *           |
     *    20MB   |     T2+        T2         T2-
     *           |
     *    50MB   |     T3++       T3+        T3
     *
     *
     *    >> For example, consider a high tput scenario, current buffer is 2MB, since
     *       time taken is less than T1, it moves to 20MB state, there the time taken
     *       is less than T2, so it moves to 50 MB and stays there at 50 MB.
     *
     *    >> Similarly, for low tput scenario, buffer value stays at 2MB
     *
     */

    private static final long T1 = 1900;
    private static final long T2 = 2300;
    private static final long T3 = 2700;
    private static final long delta = 150;

    private long time;
    private TelephonyManager mTelephonyManager;
    private INetworkManagementService mNetworkService;
    private static final boolean DBG = false;

    private INetworkManagementEventObserver mAlertObserver = new BaseNetworkObserver() {
        @Override
        public void limitReached(String limitName, String iface) {

            if (DBG) Log.d(TAG, "limitReached Alert for " + limitName);
            getApplicationContext().enforceCallingOrSelfPermission(CONNECTIVITY_INTERNAL, TAG);

            if (LIMIT_GLOBAL_ALERT.equals(limitName)) {

                long currTime = SystemClock.elapsedRealtime();
                if (DBG) Log.d(TAG, "currBuffer = " + currBufferValue +
                                    " currTime = " + currTime + " time = " + time);

                setGlobalAlert(getRecommendedGlobalAlert(currBufferValue,timeDiff(time, currTime)));
                time = currTime;
            }
        }
    };

    PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
        @Override
        public void onDataConnectionStateChanged(int state, int networkType) {

             int networkClass = mTelephonyManager.getNetworkClass(networkType);
             if (DBG) Log.d(TAG, "state = " + state + " networkType = " + networkType +
                                 " networkClass = " + networkClass);

             if (networkClass == android.telephony.TelephonyManager.NETWORK_CLASS_4_G) {
                 registerObserver();
             } else {
                 setGlobalAlert(TWO_MB);
                 unregisterObserver();
             }
        }
    };

    private static long timeDiff(long prev, long curr) {
        if (curr > prev) return (curr - prev);
        else return 0;
    }

    private static long getRecommendedGlobalAlert(long currBufferValue, long elapsedTime) {
        if ((currBufferValue == TWO_MB) && (elapsedTime < T1)) {
             return TWENTY_MB;
        } else if ((currBufferValue == TWENTY_MB) && (elapsedTime < T2 - delta)){
             return FIFTY_MB;
        } else if ((currBufferValue == TWENTY_MB) && (elapsedTime > T2 + delta)){
             return TWO_MB;
        } else if ((currBufferValue == FIFTY_MB) && (elapsedTime > T3)){
             return TWENTY_MB;
        } else {
             return currBufferValue;
        }
    }

    private void registerObserver() {
        if (DBG) Log.d(TAG, "register observer");
        try {
           if (!(mNetworkService.isBandwidthControlEnabled())) {
               Log.e(TAG, "observer is not registered since bandwidth is disabled");
               return;
           }
           mNetworkService.registerObserver(mAlertObserver);
        } catch (RemoteException ex) {
           Log.e(TAG, "Remote exception while registering observer with NMS");
        }
    }

    private void unregisterObserver() {
        if (DBG) Log.d(TAG, "unregister observer");
        try {
           setGlobalAlert(TWO_MB);
           mNetworkService.unregisterObserver(mAlertObserver);
        } catch (RemoteException ex) {
           Log.d(TAG, "Remote exception while unregistering observer with NMS");
        }
    }

    @Override
    public void onCreate(){
        if (DBG) Log.d(TAG, "onCreate()");
        super.onCreate();

        mNetworkService = INetworkManagementService.Stub.asInterface(
                ServiceManager.getService(Context.NETWORKMANAGEMENT_SERVICE));

        mTelephonyManager = (TelephonyManager) getApplicationContext()
                .getSystemService(Context.TELEPHONY_SERVICE);
        mTelephonyManager
                .listen(mPhoneStateListener, PhoneStateListener.LISTEN_DATA_CONNECTION_STATE);

        time = SystemClock.elapsedRealtime();
    }

    @Override
    public void onStart(Intent intent, int id) {
        if (DBG) Log.d(TAG, "onStart()");
        super.onStart(intent, id);
    }

    @Override
    public void onDestroy() {
        if (DBG) Log.d(TAG, "onDestroy()");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void setGlobalAlert(long bytes) {
        if (DBG) Log.d(TAG, "setGlobalAlert(" + bytes + ")");
        if (currBufferValue != bytes) {
            if (DBG) Log.d(TAG, "Setting Global Alert bytes to " + bytes);
            Settings.Global.putLong(getContentResolver(), NETSTATS_GLOBAL_ALERT_BYTES, bytes);
            currBufferValue = bytes;
        }
    }
}
