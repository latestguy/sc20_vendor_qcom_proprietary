/*
 * Copyright (c) 2016-2017 Qualcomm Technologies, Inc.
 * All Rights Reserved.
 * Confidential and Proprietary - Qualcomm Technologies, Inc.
 *
 * Not a Contribution.
 * Apache license notifications and license are retained
 * for attribution purposes only.
 */

/*
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

package com.qualcomm.qti.callsettings;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.sqlite.SQLiteException;
import android.media.RingtoneManager;
import android.media.Ringtone;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.UserManager;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.telephony.CarrierConfigManager;
import android.telephony.PhoneStateListener;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.TabHost.OnTabChangeListener;
import android.widget.TabHost.TabContentFactory;
import android.widget.TabHost.TabSpec;
import android.widget.TabHost;
import android.widget.Toast;

import com.android.ims.ImsManager;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.codeaurora.ims.QtiCallConstants;

public class QtiSubscriptionSettings extends PreferenceActivity {

    /**
     * Intent action to bring up Voicemail Provider settings
     * DO NOT RENAME. There are existing apps which use this intent value.
     */
    public static final String ACTION_ADD_VOICEMAIL =
            "com.android.phone.CallFeaturesSetting.ADD_VOICEMAIL";
    public static final String QTI_SUB_ID_EXTRA =
            "com.android.phone.settings.SubscriptionInfoHelper.SubscriptionId";
    public static final String QTI_SUB_LABEL_EXTRA =
            "com.android.phone.settings.SubscriptionInfoHelper.SubscriptionLabel";

    // Number of active Subscriptions to show tabs
    private static final int TAB_THRESHOLD = 2;

    private final String VOICEMAIL_SETTINGS_PREF_KEY = "voicemail_key";
    private final String FDN_SETTINGS_PREF_KEY = "fdn_key";
    private final String RINGTONE_SETTINGS_PREF_KEY = "ringtone_preference_key";
    private final String CF_SETTINGS_PREF_KEY = "call_forwarding_key";
    private final String ADDITIONAL_SETTINGS_PREF_KEY = "additional_settings_key";

    private PreferenceScreen mVoicemailSettingsScreen;
    private PreferenceScreen mFdnSettingScreen;
    private QtiSubsRingtonePreference mRingtonePreference;
    private PreferenceScreen mCallForwardingScreen;
    private PreferenceScreen mAdditionalSettings;
    private CarrierConfigManager mConfigManager;
    private SubscriptionManager mSubscriptionManager;

    private String tabDefaultLabel = "SIM SLOT";
    private List<SubscriptionInfo> mActiveSubInfos;
    private UserManager mUm;
    private Phone mPhone;
    private boolean mOkClicked;

    private TabHost mTabHost;
    private TelephonyManager mTelephonyManager;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        mUm = (UserManager) getSystemService(Context.USER_SERVICE);
        mSubscriptionManager = SubscriptionManager.from(this);
        mConfigManager = (CarrierConfigManager)
                getSystemService(Context.CARRIER_CONFIG_SERVICE);
        if (mUm.hasUserRestriction(UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS)) {
            Log.e("QtiSubscriberSettings","has user rerstriction, returning");
            return;
        }
         mTelephonyManager = (TelephonyManager) getSystemService(
                Context.TELEPHONY_SERVICE);

        // Initialize mActiveSubInfo
        int max = mSubscriptionManager.getActiveSubscriptionInfoCountMax();
        mActiveSubInfos = new ArrayList<SubscriptionInfo>(max);
        addPreferencesFromResource(R.xml.qti_subscription_settings);
        mVoicemailSettingsScreen =
            (PreferenceScreen) findPreference(VOICEMAIL_SETTINGS_PREF_KEY);
        mFdnSettingScreen =
            (PreferenceScreen) findPreference(FDN_SETTINGS_PREF_KEY);
        mRingtonePreference =
            (QtiSubsRingtonePreference) findPreference(RINGTONE_SETTINGS_PREF_KEY);
        mCallForwardingScreen =
            (PreferenceScreen) findPreference(CF_SETTINGS_PREF_KEY);
        mAdditionalSettings =
            (PreferenceScreen) findPreference(ADDITIONAL_SETTINGS_PREF_KEY);
        initSubscriptions();
    }

    private void initSubscriptions() {
        int currentTab = 0;
        List<SubscriptionInfo> sil = mSubscriptionManager.getActiveSubscriptionInfoList();
        int phoneCount = mTelephonyManager.getPhoneCount();
        TabState state = TabState.UPDATE;
        if (phoneCount < 2) {
            state = isUpdateTabsNeeded(sil);
        }

        // Update to the active subscription list
        mActiveSubInfos.clear();
        if (sil != null) {
            mActiveSubInfos.addAll(sil);
            // If there is only 1 sim then currenTab should represent slot no. of the sim.
            if (sil.size() == 1) {
                currentTab = sil.get(0).getSimSlotIndex();
            }
        }

        switch (state) {
            case UPDATE: {
                currentTab = mTabHost != null ? mTabHost.getCurrentTab() : 0;
                setContentView(com.android.internal.R.layout.common_tab_settings);
                mTabHost = (TabHost) findViewById(android.R.id.tabhost);
                mTabHost.setup();
                // Update the tabName. Get tab name from SubscriptionInfo,
                // if SubscriptionInfo not available get default tabName for that slot.
                for (int simSlotIndex = 0; simSlotIndex < phoneCount; simSlotIndex++) {
                    String tabName = null;
                    for (SubscriptionInfo si : mActiveSubInfos) {
                        if (si != null && si.getSimSlotIndex() == simSlotIndex) {
                            // Slot is not empty and we match
                            tabName = String.valueOf(si.getDisplayName());
                            break;
                        }
                    }
                    if (tabName == null) {
                        try {
                            Context con = createPackageContext("com.android.settings", 0);
                            int id = con.getResources().getIdentifier("sim_editor_title",
                                    "string", "com.android.settings");
                            tabName = con.getResources().getString(id, simSlotIndex + 1);
                        } catch (NameNotFoundException e) {
                            tabName = tabDefaultLabel + simSlotIndex;
                        }
                    }
                    mTabHost.addTab(buildTabSpec(String.valueOf(simSlotIndex), tabName));
                }
                mTabHost.setOnTabChangedListener(mTabListener);
                mTabHost.setCurrentTab(currentTab);
                break;
            }
            case NO_TABS: {
                if (mTabHost != null) {
                    mTabHost.clearAllTabs();
                    mTabHost = null;
                }
                setContentView(com.android.internal.R.layout.common_tab_settings);
                break;
            }
            case DO_NOTHING: {
                if (mTabHost != null) {
                    currentTab = mTabHost.getCurrentTab();
                }
                break;
            }
        }
        updatePhone(currentTab);
        updateBody(currentTab);
    }

    private enum TabState {
        NO_TABS, UPDATE, DO_NOTHING
    }

    private TabState isUpdateTabsNeeded(List<SubscriptionInfo> newSil) {
        TabState state = TabState.DO_NOTHING;
        if (newSil == null) {
            if (mActiveSubInfos.size() >= TAB_THRESHOLD) {
                state = TabState.NO_TABS;
            }
        } else if (newSil.size() < TAB_THRESHOLD && mActiveSubInfos.size() >= TAB_THRESHOLD) {
            state = TabState.NO_TABS;
        } else if (newSil.size() >= TAB_THRESHOLD && mActiveSubInfos.size() < TAB_THRESHOLD) {
            state = TabState.UPDATE;
        } else if (newSil.size() >= TAB_THRESHOLD) {
            Iterator<SubscriptionInfo> siIterator = mActiveSubInfos.iterator();
            for(SubscriptionInfo newSi : newSil) {
                SubscriptionInfo curSi = siIterator.next();
                if (!newSi.getDisplayName().equals(curSi.getDisplayName())) {
                    state = TabState.UPDATE;
                    break;
                }
            }
        }
        return state;
    }

    private OnTabChangeListener mTabListener = new OnTabChangeListener() {
        @Override
        public void onTabChanged(String tabId) {
            // The User has changed tab; update the body.
            updatePhone(Integer.parseInt(tabId));
            updateBody(Integer.parseInt(tabId));
        }
    };


    private void updatePhone(int slotId) {
        mPhone = PhoneFactory.getPhone(slotId);
        if (mPhone == null) {
            mPhone = PhoneFactory.getDefaultPhone();
        }
    }

    private void updateBody(int slotId) {
        int simState = TelephonyManager.getDefault().getSimState(mPhone.getPhoneId());
        boolean screenState = simState != TelephonyManager.SIM_STATE_ABSENT;
        mVoicemailSettingsScreen.setEnabled(screenState);
        mFdnSettingScreen.setEnabled(screenState);
        mCallForwardingScreen.setEnabled(screenState);
        mAdditionalSettings.setEnabled(screenState);

        if (!screenState) {
            Log.e("QtiSubscriptionSettings","screen state is disabled, returning");
            return;
        }

        // If its a mutlisim phone and only one SIM is placed.
        if (mActiveSubInfos.size() == slotId) {
            slotId -= 1;
        }

        SubscriptionInfo currentSi = mActiveSubInfos.get(slotId);

        Intent voicemailIntent = new Intent(ACTION_ADD_VOICEMAIL);
        voicemailIntent = addExtrasToIntent(voicemailIntent,currentSi);
        mVoicemailSettingsScreen.setIntent(voicemailIntent);

        Intent fdnIntent = new Intent();
        fdnIntent.setClassName("com.android.phone",
                      "com.android.phone.settings.fdn.FdnSetting");
        fdnIntent = addExtrasToIntent(fdnIntent,currentSi);
        mFdnSettingScreen.setIntent(fdnIntent);

        Intent callForwardingIntent = new Intent();
        callForwardingIntent.setClassName("com.android.phone",
                      "com.android.phone.CallForwardType");
        callForwardingIntent = addExtrasToIntent(callForwardingIntent,currentSi);
        mCallForwardingScreen.setIntent(callForwardingIntent);

        Intent additionalCallOptionsIntent =  new Intent();
        additionalCallOptionsIntent.setClassName("com.android.phone",
                      "com.android.phone.GsmUmtsAdditionalCallOptions");
        additionalCallOptionsIntent = addExtrasToIntent(additionalCallOptionsIntent,currentSi);
        mAdditionalSettings.setIntent(additionalCallOptionsIntent);
    }

    private Intent  addExtrasToIntent(Intent intent, SubscriptionInfo subscriptionInfo) {
        if (subscriptionInfo == null) {
            return null;
        }

        intent.putExtra(QTI_SUB_ID_EXTRA, subscriptionInfo.getSubscriptionId());
        intent.putExtra(
                QTI_SUB_LABEL_EXTRA, subscriptionInfo.getDisplayName().toString());
        return intent;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private TabContentFactory mEmptyTabContent = new TabContentFactory() {
        @Override
        public View createTabContent(String tag) {
            return new View(mTabHost.getContext());
        }
    };

    private TabSpec buildTabSpec(String tag, String title) {
        return mTabHost.newTabSpec(tag).setIndicator(title).setContent(
                mEmptyTabContent);
    }
}

