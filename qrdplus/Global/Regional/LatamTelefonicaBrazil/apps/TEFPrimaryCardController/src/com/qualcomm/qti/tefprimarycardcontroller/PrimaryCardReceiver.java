/*
 * Copyright (c) 2015 Qualcomm Technologies, Inc.
 * All Rights Reserved.
 * Confidential and Proprietary - Qualcomm Technologies, Inc.
 */

package com.qualcomm.qti.tefprimarycardcontroller;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.Rlog;
import android.telephony.TelephonyManager;

/**
 * Initiates Primary slot selection operation's on bootup
 */
public class PrimaryCardReceiver extends BroadcastReceiver {
    private final String LOG_TAG = "PrimaryCardReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {

        // Enable only in case of Multisim
        if ((TelephonyManager.getDefault().getPhoneCount() > 1)
                &&
                Intent.ACTION_BOOT_COMPLETED
                        .equals(intent.getAction())) {
            Rlog.d(LOG_TAG,
                    " invoking PrimarySubSelectionController init");
            PrimarySubSelectionController.init(context);
        }
    }
}
