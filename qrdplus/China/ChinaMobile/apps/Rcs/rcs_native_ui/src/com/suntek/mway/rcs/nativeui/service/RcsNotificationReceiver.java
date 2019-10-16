/*
 * Copyright (c) 2014-2015 pci-suntektech Technologies, Inc.  All Rights Reserved.
 * pci-suntektech Technologies Proprietary and Confidential.
 */

package com.suntek.mway.rcs.nativeui.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;
import com.suntek.mway.rcs.nativeui.R;

public class RcsNotificationReceiver extends BroadcastReceiver {
    public static String ACTION_DEFAULT_MMS_APPLICATION_CHANGED =
            "com.android.telephony.intent.action.DEFAULT_MMS_APPLICATION_CHANGED";

    public static String MMS_APP_SET = "mms_app";
    @Override
    public void onReceive(Context context, Intent intent) {
        // Make sure the RcsNotificationsService is running.
        String action = intent.getAction();
        if (action.equals(ACTION_DEFAULT_MMS_APPLICATION_CHANGED)) {
            String mmsapp = intent.getStringExtra(MMS_APP_SET);
            if (!mmsapp.equals("com.android.mms")) {
                Toast.makeText(context, R.string.switch_to_third_party_mms, Toast.LENGTH_SHORT)
                        .show();
                return;
            }
        }

        context.startService(new Intent(context, RcsNotificationsService.class));
    }
}
