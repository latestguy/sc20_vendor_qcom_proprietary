/**
 * Copyright (c) 2016 Qualcomm Technologies, Inc.
 * All Rights Reserved.
 * Confidential and Proprietary - Qualcomm Technologies, Inc.
 */

package com.qualcomm.qti.mobiledatapromptdisplay;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.provider.Settings;
import android.util.Log;

import com.android.internal.telephony.TelephonyIntents;
import com.qualcomm.qti.mobiledatapromptdisplay.receiver.SimStateChangeReceiver;

public class MobileDataPromptDisplayApplication extends Application {

    SimStateChangeReceiver mSimStateChangeReceiver = new SimStateChangeReceiver();

    @Override
    public void onCreate() {
        super.onCreate();
        IntentFilter filter = new IntentFilter();
        filter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        filter.setPriority(1000);
        registerReceiver(mSimStateChangeReceiver, filter);
    }

    public void unregisterSimStateChangeReceiver() {
        if(mSimStateChangeReceiver != null) {
            unregisterReceiver(mSimStateChangeReceiver);
        }
    }
}
