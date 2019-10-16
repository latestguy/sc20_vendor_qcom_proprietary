/*
 * Copyright (c) 2016 Qualcomm Technologies, Inc.
 * All Rights Reserved.
 * Confidential and Proprietary - Qualcomm Technologies, Inc.
 *
 * Not a Contribution.
 * Apache license notifications and license are retained
 * for attribution purposes only.
 *
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.qualcomm.qti.internal.telephony;

import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Message;
import android.content.Context;
import android.net.ZeroBalanceHelper;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.text.TextUtils;

import android.util.Log;
import com.android.internal.telephony.ConfigResourceUtil;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.GsmCdmaPhone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.ServiceStateTracker;

import com.qti.internal.telephony.QtiPlmnOverride;

public class QtiServiceStateTracker extends ServiceStateTracker {
    private static final String LOG_TAG = "QtiServiceStateTracker";
    private static final boolean DBG = true;
    private static final boolean VDBG = false;  // STOPSHIP if true
    private static final String ACTION_MANAGED_ROAMING_IND =
            "codeaurora.intent.action.ACTION_MANAGED_ROAMING_IND";
    private final String ACTION_RAC_CHANGED = "qualcomm.intent.action.ACTION_RAC_CHANGED";
    private final String mRatInfo = "rat";
    private final String mRacChange = "rac";
    private int mRac;
    private int mRat;
    private int mTac = -1;
    private QtiPlmnOverride mQtiPlmnOverride;
    private ConfigResourceUtil mConfigResUtil = new ConfigResourceUtil();

    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(ACTION_RAC_CHANGED)) {
                Bundle bundle = intent.getExtras();
                if (bundle != null) {
                    mRac = bundle.getInt(mRacChange);
                    mRat = bundle.getInt(mRatInfo);
                    enableBackgroundData();
                }
            }
        }
    };

    public QtiServiceStateTracker(GsmCdmaPhone phone, CommandsInterface ci) {
        super(phone,ci);
        mQtiPlmnOverride = new QtiPlmnOverride();
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_RAC_CHANGED);
        phone.getContext().registerReceiver(mIntentReceiver, filter);
    }

    private void enableBackgroundData() {
        ZeroBalanceHelper helper = new ZeroBalanceHelper();
        if (helper.getFeatureConfigValue() &&
                helper.getBgDataProperty().equals("true")) {
            Log.i("zerobalance","Enabling the background data on LAU/RAU");
            helper.setBgDataProperty("false");
        }
    }

    @Override
    protected void handlePollStateResultMessage(int what, AsyncResult ar) {
        switch (what) {
            case EVENT_POLL_STATE_REGISTRATION: {
                super.handlePollStateResultMessage(what, ar);
                String states[];
                if (mPhone.isPhoneTypeGsm()) {
                    states = (String[]) ar.result;
                    int regState = ServiceState.RIL_REG_STATE_UNKNOWN;
                    if (states.length > 0) {
                        try {
                            regState = Integer.parseInt(states[0]);
                        } catch (NumberFormatException ex) {
                            loge("error parsing RegistrationState: " + ex);
                        }
                    }

                    if ((regState == ServiceState.RIL_REG_STATE_DENIED
                            || regState == ServiceState.RIL_REG_STATE_DENIED_EMERGENCY_CALL_ENABLED)
                            && (states.length >= 14)) {
                        try {
                            int rejCode = Integer.parseInt(states[13]);
                            // Check if rejCode is "Persistent location update reject",
                            if (rejCode == 10) {
                                log(" Posting Managed roaming intent sub = "
                                        + mPhone.getSubId());
                                Intent intent =
                                        new Intent(ACTION_MANAGED_ROAMING_IND);
                                intent.putExtra(PhoneConstants.SUBSCRIPTION_KEY,
                                        mPhone.getSubId());
                                mPhone.getContext().sendBroadcast(intent);
                            }
                        } catch (NumberFormatException ex) {
                            loge("error parsing regCode: " + ex);
                        }
                    }
                }
                break;
            }
            case EVENT_POLL_STATE_OPERATOR: {
                super.handlePollStateResultMessage(what, ar);
                if (mPhone.isPhoneTypeGsm()) {
                    String opNames[] = (String[]) ar.result;

                    if (opNames != null && opNames.length >= 3) {
                        // FIXME: Giving brandOverride higher precedence, is this desired?
                        String brandOverride = mUiccController.getUiccCard(getPhoneId()) != null ?
                                mUiccController.getUiccCard(getPhoneId()).getOperatorBrandOverride()
                                : null;
                        if (brandOverride != null) {
                            log("EVENT_POLL_STATE_OPERATOR: use brandOverride=" + brandOverride);
                            mNewSS.setOperatorName(brandOverride, brandOverride, opNames[2]);
                        } else if (mQtiPlmnOverride.containsCarrier(opNames[2]) &&
                                    mConfigResUtil.getBooleanValue(mPhone.getContext(),
                                        "config_plmn_name_override_enabled")) {
                                String strOperatorLong = null;
                                log("EVENT_POLL_STATE_OPERATOR: use plmnOverride");
                                strOperatorLong = mQtiPlmnOverride.getPlmn(opNames[2]);
                                mNewSS.setOperatorName (strOperatorLong, opNames[1], opNames[2]);
                        } else {
                            mNewSS.setOperatorName(opNames[0], opNames[1], opNames[2]);
                        }
                    }
                }
                break;
            }

            default:
                super.handlePollStateResultMessage(what, ar);
        }
    }

   @Override
   protected void setRoamingType(ServiceState currentServiceState) {
        super.setRoamingType(currentServiceState);
        final boolean isVoiceInService =
                (currentServiceState.getVoiceRegState() == ServiceState.STATE_IN_SERVICE);
        if(isVoiceInService && currentServiceState.getVoiceRoaming()
                    && mPhone.isPhoneTypeGsm()) {
            setOperatorConsideredDomesticRoaming(currentServiceState);
        }
   }

   private void setOperatorConsideredDomesticRoaming(ServiceState s) {
        final int subId = mPhone.getSubId();
        String operatorNumeric = s.getOperatorNumeric();
        String[] numericArray =
                SubscriptionManager.getResourcesForSubId(mPhone.getContext(), subId).
                getStringArray(com.android.internal.
                        R.array.config_operatorConsideredDomesticRoaming);
        String[] numericExceptionsArray =
                SubscriptionManager.getResourcesForSubId(mPhone.getContext(), subId).
                getStringArray(com.android.internal.
                        R.array.config_operatorConsideredDomesticRoamingExceptions);

        if (numericArray == null || numericArray.length == 0
                || TextUtils.isEmpty(operatorNumeric)) {
            // do nothing and exit
            return;
        }

        boolean isDomestic = false;
        for (String numeric : numericArray) {
            if (operatorNumeric.startsWith(numeric)) {
                // set domestic if it is in numeric in operator Considered Domestic Roaminglist
                s.setVoiceRoamingType(ServiceState.ROAMING_TYPE_DOMESTIC);
                isDomestic = true;
                break;
            }
        }

        if (numericExceptionsArray.length != 0 && isDomestic) {
            for (String numeric : numericExceptionsArray) {
                if (operatorNumeric.startsWith(numeric)) {
                    // set international if it is in Operator Domestic Roaming Exceptions list
                    s.setVoiceRoamingType(ServiceState.ROAMING_TYPE_INTERNATIONAL);
                    break;
                }
            }
        }

        // for all other cases, set international roaming
        if (!isDomestic) {
            s.setVoiceRoamingType(ServiceState.ROAMING_TYPE_INTERNATIONAL);
        }
    }

}
