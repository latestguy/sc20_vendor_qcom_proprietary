/*Copyright (c) 2016 Qualcomm Technologies, Inc.
 *All Rights Reserved.
 *Confidential and Proprietary - Qualcomm Technologies, Inc.
*/

package com.qualcomm.qti.mobiledatapromptdisplay.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import com.qualcomm.qti.mobiledatapromptdisplay.MobileDataPromptDisplayApplication;

import java.util.List;

public class SimStateChangeReceiver extends BroadcastReceiver {

    private Context mContext;
    private SharedPreferences mPref;
    private TelephonyManager mTelephonyManager;
    public static final String MOBILE_DATA_CONFIRMATION_PREF = "mobileDataConfirmationPref";
    public static final String HAS_DATA_PROMPT_SHOWN = "hasDataPromptShown";
    private String mLaunchingComponentName =
            "com.qualcomm.qti.mobiledatapromptdisplay.MobileDataPromptDisplayActivity";

    @Override
    public void onReceive(Context context, Intent intent) {

        mContext = context;
        mPref = mContext.getSharedPreferences(
                MOBILE_DATA_CONFIRMATION_PREF, mContext.MODE_PRIVATE);
        mTelephonyManager = (TelephonyManager) context.
                getSystemService(Context.TELEPHONY_SERVICE);
        checkIfDataPromptShown();
    }

    private void checkIfDataPromptShown() {
        boolean isDataPromptShown = false;
        if (mPref != null) {
            isDataPromptShown = mPref.getBoolean(HAS_DATA_PROMPT_SHOWN, false);
        }
        // case 1: if mobile data dialog has not been shown yet,
        if (!isDataPromptShown) {
            // sub Case 1: If SIM counts > 0 and SIM data is in disable mode
            // then call activity and show mobile data dialog
            if ((getSubInfoRecords() != null)) {
                if (!isDataEnabledForSim()) {
                    Intent intent = new Intent();
                    intent.setClassName(mContext, mLaunchingComponentName);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    mContext.startActivity(intent);
                }
                // sub Case 2: If SIM data is in enable mode in build itself
                // then don't show mobile data dialog to user, store boolean in Shared pref
                // so next time don't show  prompt to user
                else {
                    persistDataPromptShown();
                }
            }
        }
    }

    private List<SubscriptionInfo> getSubInfoRecords() {
        List<SubscriptionInfo> subInfoRecords = SubscriptionManager.from(mContext)
                .getActiveSubscriptionInfoList();
        return subInfoRecords;
    }

    private boolean isDataEnabledForSim() {
        boolean isDataEnabledForSim = false;
        List<SubscriptionInfo> subInfoRecords = getSubInfoRecords();
        if (subInfoRecords.size() > 0) {
            for (int i = 0; i < subInfoRecords.size(); i++) {
                SubscriptionInfo subInfo = subInfoRecords.get(i);
                int subId = subInfo.getSubscriptionId();
                // 1st check for SIM to be ready or not ?
                if (isSimReady(subId)) {
                    isDataEnabledForSim = mTelephonyManager.getDataEnabled(subId);
                    // if SIM is ready then check if data is enabled or not for that SIM
                    if (isDataEnabledForSim) {
                        break;
                    }
                }
            }
        }
        return isDataEnabledForSim;
    }

    public boolean isSimReady(int subId) {
        final int slotId = SubscriptionManager.getSlotId(subId);
        final boolean isReady = (mTelephonyManager.getSimState(slotId)
                == TelephonyManager.SIM_STATE_READY);
        return isReady;
    }

    public void persistDataPromptShown() {
        if (mPref != null) {
            SharedPreferences.Editor edit = mPref.edit();
            edit.putBoolean(HAS_DATA_PROMPT_SHOWN, true);
            edit.commit();
            MobileDataPromptDisplayApplication application =
                    (MobileDataPromptDisplayApplication)mContext.getApplicationContext();
            application.unregisterSimStateChangeReceiver();
        }
    }
}
