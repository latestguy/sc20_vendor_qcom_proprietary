/*
 * Copyright (c) 2015 Qualcomm Technologies, Inc.
 * All Rights Reserved.
 * Confidential and Proprietary - Qualcomm Technologies, Inc.
 */

package com.qti.extwifi;

import java.util.List;

import android.content.Context;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

public class ExtWifiPrioritySettingsActivity extends PreferenceActivity implements
        OnPreferenceChangeListener {

    private static final String TAG = "WifiPrioritySettings";

    private WifiManager mWifiManager;
    PreferenceCategory mConfiguredAps;
    private int[] mPriorityOrder;
    private List<WifiConfiguration> mWifiConfigurationList;
    private int configuredApCount;
    private static final int PRIORITY_BASE = 0;
    private static final int PRIORITY_INVALID = -1;
    private static final String SSID_NULL = "";
    private static final String COLON = ": ";
    private static final String CARRIER_SSID = "CMCC";
    private static final String CARRIER_AUTO_SSID = "CMCC-AUTO";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.wifi_priority_settings);
        mConfiguredAps = (PreferenceCategory) findPreference("configured_ap_list");
        mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        initPage();
    }

    /**
     * init page, give each access point a right priority order
     */
    public void initPage() {
        if (mWifiManager == null) {
            Log.d(TAG, "Failed to get WifiManager service");
            return;
        }

        mWifiConfigurationList = mWifiManager.getConfiguredNetworks();
        if (mWifiConfigurationList != null && mConfiguredAps != null) {
            mConfiguredAps.removeAll();

            configuredApCount = mWifiConfigurationList.size();
            String[] priorityEntries = new String[configuredApCount];
            for (int i = 0; i < configuredApCount; i++) {
                priorityEntries[i] = String.valueOf(i + 1);
            }

            for (int i = 0; i < configuredApCount; i++) {
                Log.e(TAG, "Before sorting: priority array="
                        + mWifiConfigurationList.get(i).priority);
            }
            // get the correct priority order for each ap
            mPriorityOrder = calculateInitPriority(mWifiConfigurationList);

            String summaryPreStr = getResources().getString(R.string.wifi_priority_label) + COLON;
            // initiate page with list preference
            for (int i = 0; i < configuredApCount; i++) {
                Log.e(TAG, "After sorting: priority array=" + mPriorityOrder[i]);
                WifiConfiguration config = mWifiConfigurationList.get(i);
                if (config.priority != configuredApCount - mPriorityOrder[i] + 1) {
                    config.priority = configuredApCount - mPriorityOrder[i] + 1;
                    updateConfig(config);
                }

                // add a list Preference to this page
                String ssidStr = removeDoubleQuotes(config.SSID);
                ListPreference pref = new ListPreference(this);
                pref.setOnPreferenceChangeListener(this);
                pref.setTitle(ssidStr);
                pref.setDialogTitle(ssidStr);
                pref.setSummary(summaryPreStr + mPriorityOrder[i]);
                pref.setEntries(priorityEntries);
                pref.setEntryValues(priorityEntries);
                pref.setValueIndex(mPriorityOrder[i] - 1);
                mConfiguredAps.addPreference(pref);
            }
            mWifiManager.saveConfiguration();
        }
    }

    /**
     * calculate priority order of input ap list, each ap's right order is stored in a int array
     * 
     * @param configs
     * @return
     */
    public int[] calculateInitPriority(List<WifiConfiguration> configs) {
        if (configs == null) {
            return null;
        }
        for (WifiConfiguration config : configs) {
            if (config == null) {
                config = new WifiConfiguration();
                config.SSID = "ERROR";
                config.priority = PRIORITY_BASE;
            } else if (config.priority < 0) {
                config.priority = PRIORITY_BASE;
            }
        }

        int totalSize = configs.size();
        int[] result = new int[totalSize];
        for (int i = 0; i < totalSize; i++) {
            int biggestPoz = 0;
            for (int j = 1; j < totalSize; j++) {
                if (!formerHasHigherPriority(configs.get(biggestPoz), configs.get(j))) {
                    biggestPoz = j;
                }
            }
            // this is the [i+1] biggest one, so give such order to it
            result[biggestPoz] = i + 1;
            // don't count this one in any more
            configs.get(biggestPoz).priority = PRIORITY_INVALID;
        }
        return result;
    }

    /**
     * compare priority of two AP
     * 
     * @param former
     * @param backer
     * @return true if former one has higher priority, otherwise return false
     */
    private boolean formerHasHigherPriority(WifiConfiguration former,
            WifiConfiguration backer) {
        if (former == null) {
            return false;
        } else if (backer == null) {
            return true;
        } else {
            if (former.priority > backer.priority) {
                return true;
            } else if (former.priority < backer.priority) {
                return false;
            } else {// have the same priority, so default trusted AP go first
                String formerSSID = (former.SSID == null ? SSID_NULL :
                        removeDoubleQuotes(former.SSID));
                String backerSSID = (backer.SSID == null ? SSID_NULL :
                        removeDoubleQuotes(backer.SSID));
                if (!CARRIER_SSID.equals(backerSSID)) {
                    return true;
                } else {
                    if (!CARRIER_SSID.equals(formerSSID)) {
                        return false;
                    } else {
                        return formerSSID.compareTo(backerSSID) <= 0;
                    }
                }
            }
        }
    }

    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference instanceof ListPreference) {
            ListPreference pref = (ListPreference) preference;
            int oldOrder = 0;
            int newOrder = 0;
            try {
                oldOrder = Integer.parseInt(pref.getValue());
                newOrder = Integer.parseInt((String) newValue);
            } catch (NumberFormatException e) {
                Log.e(TAG, "Error happens when modify priority manually");
                e.printStackTrace();
            }
            Log.e(TAG, "Priority old value=" + oldOrder + ", new value=" + newOrder);
            Toast.makeText(this,
                    getResources().getString(R.string.wifi_priority_changed, pref.getValue(),
                            (String) newValue), Toast.LENGTH_SHORT).show();

            // this is priority order, bigger order, smaller priority
            if (oldOrder != newOrder && mPriorityOrder != null) {
                if (oldOrder > newOrder) {
                    // selected AP will have a higher priority, but smaller order
                    for (int i = 0; i < mPriorityOrder.length; i++) {
                        WifiConfiguration config = mWifiConfigurationList.get(i);
                        if (mPriorityOrder[i] >= newOrder && mPriorityOrder[i] < oldOrder) {
                            mPriorityOrder[i]++;
                            config.priority = configuredApCount - mPriorityOrder[i] + 1;
                            updateConfig(config);
                        } else if (mPriorityOrder[i] == oldOrder) {
                            mPriorityOrder[i] = newOrder;
                            config.priority = configuredApCount - newOrder + 1;
                            updateConfig(config);
                        }
                    }
                } else {
                    // selected AP will have a lower priority, but bigger order
                    for (int i = 0; i < mPriorityOrder.length; i++) {
                        WifiConfiguration config = mWifiConfigurationList.get(i);
                        if (mPriorityOrder[i] <= newOrder && mPriorityOrder[i] > oldOrder) {
                            mPriorityOrder[i]--;
                            config.priority = configuredApCount - mPriorityOrder[i] + 1;
                            updateConfig(config);
                        } else if (mPriorityOrder[i] == oldOrder) {
                            mPriorityOrder[i] = newOrder;
                            config.priority = configuredApCount - newOrder + 1;
                            updateConfig(config);
                        }
                    }
                }
                mWifiManager.saveConfiguration();
                updateUI();
            }
        }
        return true;
    }

    /**
     * update each list view according to configure order array
     */
    public void updateUI() {
        for (int i = 0; i < mPriorityOrder.length; i++) {
            Preference pref = mConfiguredAps.getPreference(i);
            if (pref != null) {
                String summaryPreStr = getResources().getString(R.string.wifi_priority_label)
                        + COLON;
                pref.setSummary(summaryPreStr + mPriorityOrder[i]);
            }
            if (pref instanceof ListPreference) {
                ((ListPreference) pref).setValue(String.valueOf(mPriorityOrder[i]));
            }

        }
    }

    private void updateConfig(WifiConfiguration config) {
        Log.d(TAG, "updateConfig()");
        if (config == null) {
            return;
        }
        WifiConfiguration newConfig = new WifiConfiguration();
        newConfig.networkId = config.networkId;
        newConfig.priority = config.priority;
        mWifiManager.updateNetwork(newConfig);
    }

    private String removeDoubleQuotes(String string) {
        if (TextUtils.isEmpty(string)) {
            return "";
        }
        int length = string.length();
        if ((length > 1) && (string.charAt(0) == '"')
                && (string.charAt(length - 1) == '"')) {
            return string.substring(1, length - 1);
        }
        return string;
    }

}
