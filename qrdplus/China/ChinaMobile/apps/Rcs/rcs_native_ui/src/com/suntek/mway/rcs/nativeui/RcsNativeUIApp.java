/*
 * Copyright (c) 2014-2015 pci-suntektech Technologies, Inc.  All Rights Reserved.
 * pci-suntektech Technologies Proprietary and Confidential.
 */

package com.suntek.mway.rcs.nativeui;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Vibrator;
import android.provider.Telephony.Sms;

import com.suntek.mway.rcs.nativeui.service.RcsNotificationsService;
import com.suntek.mway.rcs.publicaccount.http.service.CommonHttpRequest;
import com.suntek.mway.rcs.publicaccount.receiver.PublicMessageObserver;
import com.suntek.mway.rcs.publicaccount.util.PublicAccountUtils;
import com.suntek.rcs.ui.common.RcsApiManager;

public class RcsNativeUIApp extends Application {

    private static long nowThreadId;
    private Vibrator vibrator;
    private static Context context;
    private static boolean sNeedClosePs;
    private static boolean sNeedOpenWifi;
    private static int sNetId = -1;
    private static RcsNativeUIApp mRcsNativeUIApp = null;

    public static Context getContext() {
        return context;
    }

    public static void setNeedClosePs(boolean needClosePs){
        sNeedClosePs = needClosePs;
    }

    public static boolean isNeedClosePs(){
        return sNeedClosePs;
    }

    public static void setWifiNetworkId(int netId){
        sNetId = netId;
    }
    public static int getWifiNetworkId(){
        return sNetId;
    }

    public static void setNeedOpenWifi(boolean needOpenWifi){
        sNeedOpenWifi = needOpenWifi;
    }

    public static boolean isNeedOpenWifi(){
        return sNeedOpenWifi;
    }

    synchronized public static RcsNativeUIApp getApplication() {
        return mRcsNativeUIApp;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mRcsNativeUIApp = this;
        context = getBaseContext();
        RcsApiManager.init(this);
        startService(new Intent(this, RcsNotificationsService.class));
        CommonHttpRequest.getInstance();
        registerDataObserver();
    }

    private void registerDataObserver() {
        PublicMessageObserver observer = new PublicMessageObserver(new Handler());
        getContentResolver().registerContentObserver(Sms.CONTENT_URI, true, observer);
    }

    public void vibrator(long milliseconds) {
        if (vibrator == null) {
            vibrator = (Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
        }
        vibrator.vibrate(milliseconds);
    }

    public long getNowThreadId() {
        return nowThreadId;
    }

    public void setNowThreadId(long threadId) {
        nowThreadId = threadId;
    }

}
