 /*
 *Copyright (c) 2015, Qualcomm Technologies, Inc.
 *All Rights Reserved.
 *Qualcomm Technologies Proprietary and Confidential.
 */
package com.qualcomm.qti.telephony.carrierpack;

import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.IccCardConstants.State;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.TelephonyIntents;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.net.Uri;
import android.provider.Settings;
import android.util.Log;

public class CarrierNewOutgoingCallReceiver extends BroadcastReceiver {

    private static final String TAG = "lanix_OCR";
    private static boolean DBG = Log.isLoggable(TAG, Log.DEBUG);

    private static final String ACTION_OEM_DEVICEINFO =
             "org.codeaurora.carrier.ACTION_OEM_DEVICEINFO";

    private static final String INTERNAL_ACTION_OEM_DEVICEINFO =
            "org.codeaurora.carrier.INTERNAL_ACTION_OEM_DEVICEINFO";

    private Context mContext = null;

    @Override
    public void onReceive(Context context, Intent intent) {
        mContext = context;
        if (intent != null
                && intent.getAction().equals(Intent.ACTION_NEW_OUTGOING_CALL)
                && intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER)
                .equals(context.getString(R.string.np_lock_code))) {
            abortBroadcast();
            this.setResultData("");
            clearAbortBroadcast();
            if (CarrierpackApplication.getInstance().isPersoLocked()
                    && !CarrierpackApplication.getInstance().isDePersonalizationEnabled()) {
                Intent npLockIntent = new Intent("org.codeaurora.carrier.ACTION_DEPERSO_PANEL");
                npLockIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(npLockIntent);
            } else {
                log("phone is not personalized or depersonalization is not disabled in phone app");
            }
        } else if (intent != null
            && intent.getAction().equals(context.getString(R.string.oem_key_code_action))) {
            if (intent.getStringExtra(context.getString(R.string.oem_code))
                    .equals(context.getString(R.string.phone_software_code))) {
                // launch external software version activity
                log("launch external device info settings");
                launchExternalDeviceInfo();
            } else if(intent.getStringExtra(context.getString(R.string.oem_code))
                    .equals(context.getString(R.string.phone_software_internal_code))) {
                // launch internal software version activity
                log("launch internal device info settings");
                launchInternalDeviceInfo();
            } else if (intent.getStringExtra(context.getString(R.string.oem_code))
                    .equals(context.getString(R.string.field_test_mode_code))) {
                log("launch FTM");
                // launch field test mode activity
                launchFtm();
            } else if (intent.getStringExtra(context.getString(R.string.oem_code))
                    .equals(context.getString(R.string.factory_mode_code))) {
                // initiate WipeUserData trigger
                log("start WipeUserData");
                Intent masterClearConfirmIntent = new Intent(context,
                                                    MasterClearConfirmationActivity.class);
                masterClearConfirmIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(masterClearConfirmIntent);
            } else {
                // No code to handle, just return.
                return;
            }
            abortBroadcast();
            this.setResultData("");
            clearAbortBroadcast();

        }
    }

    private void launchFtm() {
        Intent intent = new Intent(TelephonyIntents.SECRET_CODE_ACTION,
        Uri.parse("android_secret_code://3878"));
        mContext.sendBroadcast(intent);
    }

    private void launchExternalDeviceInfo() {
        Intent oemDeviceInfointent = new Intent(ACTION_OEM_DEVICEINFO);
        oemDeviceInfointent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(oemDeviceInfointent);
    }

    private void launchInternalDeviceInfo() {
        Intent oemInternalDeviceInfointent = new Intent(INTERNAL_ACTION_OEM_DEVICEINFO);
        oemInternalDeviceInfointent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(oemInternalDeviceInfointent);
    }

    private void log(String logMsg) {
        if (DBG) {
            Log.d(TAG, logMsg);
        }
    }

}

