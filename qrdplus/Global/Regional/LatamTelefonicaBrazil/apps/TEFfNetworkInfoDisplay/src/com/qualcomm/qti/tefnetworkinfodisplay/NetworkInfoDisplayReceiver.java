/*Copyright (c) 2015 Qualcomm Technologies, Inc.
 *All Rights Reserved.
 *Confidential and Proprietary - Qualcomm Technologies, Inc.
*/
package com.qualcomm.qti.tefnetworkinfodisplay;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.sax.StartElementListener;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import java.util.ArrayList;
import java.util.List;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.TelephonyIntents;

public class NetworkInfoDisplayReceiver extends BroadcastReceiver {

    private Context mContext = null;

    @Override
    public void onReceive(Context context, Intent intent) {
        mContext = context;

        final TelephonyManager telephonyManager = (TelephonyManager) context
                .getSystemService(Context.TELEPHONY_SERVICE);
        String mSimStatus = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
        List<SubscriptionInfo> mSubscriptionInfos = new ArrayList<SubscriptionInfo>();
        mSubscriptionInfos = SubscriptionManager.from(mContext).getActiveSubscriptionInfoList();

     if (mContext.getResources().getBoolean(R.bool.regulatoryinfo_display_enabled)) {
            if (!(IccCardConstants.INTENT_VALUE_ICC_READY.equals(mSimStatus)
                    || IccCardConstants.INTENT_VALUE_ICC_LOADED.equals(mSimStatus)
                    || IccCardConstants.INTENT_VALUE_ICC_IMSI.equals(mSimStatus))) {
                return;
            }
            if (mSubscriptionInfos == null) {
                return;
            }
            else {
                startService();
            }
        }
    }

    private void startService() {
        Intent serviceIntent = new Intent();
        serviceIntent.setClass(mContext, MSimNetworkInforDisplayService.class);
        mContext.startService(serviceIntent);

    }
}
