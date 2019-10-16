/*
 *Copyright (c) 2016 Qualcomm Technologies, Inc.
 *All Rights Reserved.
 *Confidential and Proprietary - Qualcomm Technologies, Inc.
 */
package com.qualcomm.qti.vdfsmsprovisioning;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class SmsProvisioningParserReceiver extends BroadcastReceiver {

    private static final String WAP_PUSH_TYPE_VOICEWIFI = "application/vnd.wap.connectivity-wbxml";

    @Override
    public void onReceive(Context context, Intent intent) {
        if ((context.getResources().getBoolean(R.bool.config_enable_vowifi_sms_provisioning)) &&
                intent.getAction().equals("android.provider.Telephony.WAP_PUSH_RECEIVED")
                && (WAP_PUSH_TYPE_VOICEWIFI.equals(intent.getType()))) {
            byte[] pushData = intent.getByteArrayExtra("data");
            Intent actvityIntent = new Intent(context, ConfirmationActivity.class);
            actvityIntent.putExtra("data", pushData);
            context.startActivity(actvityIntent);
            return;
        }
    }
}
