/*
 * Copyright (c) 2015 Qualcomm Technologies, Inc.
 * All Rights Reserved.
 * Confidential and Proprietary - Qualcomm Technologies, Inc.
 */

package com.ts.wifinotification;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.NetworkInfo;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.preference.PreferenceManager;
import android.util.Log;

import java.util.List;
import java.io.IOException;
import java.lang.reflect.Field;

public class WifiConnectionBroadcast extends BroadcastReceiver {

    private static final String TAG = "WifiConnectionBroadcast";
    private String currentDisWifi;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null) {
            String strAction = intent.getAction();
            WifiManager wifiManager = (WifiManager) context
                    .getSystemService(Context.WIFI_SERVICE);
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            List<WifiConfiguration> configuredNetworks = wifiManager
                    .getConfiguredNetworks();
            if(strAction.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
                int wifistate=intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE,
                        WifiManager.WIFI_STATE_DISABLED);
                if(wifistate==WifiManager.WIFI_STATE_DISABLED) {
                    cancelNotification(context);
                }
            } else if (strAction.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
                NetworkInfo info = intent
                        .getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                currentDisWifi = wifiInfo.getSSID();
                if (info.getState().equals(NetworkInfo.State.DISCONNECTED)) {
                    saveWifiName(context,currentDisWifi);
                } else {
                    if(currentDisWifi!=null && currentDisWifi.equals(getWifiName(context))) {
                        cancelNotification(context);
                    }
                }
            } else if (strAction
                    .equals(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION)) {
                SupplicantState supplicantState = (SupplicantState) intent
                        .getParcelableExtra(WifiManager.EXTRA_NEW_STATE);
                if (SupplicantState.DISCONNECTED.equals(supplicantState)) {
                    for (WifiConfiguration config : configuredNetworks) {
                        WifiConfiguration.NetworkSelectionStatus networkStatus = config
                                .getNetworkSelectionStatus();
                        if (networkStatus.DISABLED_AUTHENTICATION_FAILURE ==
                                networkStatus.getNetworkSelectionDisableReason()) {
                            begainNotification(context);
                            saveWifiName(context,currentDisWifi);
                        }
                    }
                }
            }
        }
    }

    private void begainNotification(Context context) {
        Intent myIntent = new Intent(context, WiFiIntentService.class);
        myIntent.setAction(WiFiIntentService.ACTION_START);
        context.startService(myIntent);
    }

    private void cancelNotification(Context context) {
        Intent myIntent = new Intent(context, WiFiIntentService.class);
        myIntent.setAction(WiFiIntentService.ACTION_CANCAL);
        context.startService(myIntent);
    }


    private void saveWifiName(Context context, String wifiname) {
        SharedPreferences mSharedPreferences = PreferenceManager
                .getDefaultSharedPreferences(context);
        Editor mEditor = mSharedPreferences.edit();
        mEditor.putString(WiFiIntentService.WIFI_NAME, wifiname);
        mEditor.commit();
    }

    private String getWifiName(Context context) {
        SharedPreferences mSharedPreferences = PreferenceManager
                .getDefaultSharedPreferences(context);
        return mSharedPreferences
                .getString(WiFiIntentService.WIFI_NAME, "none");
    }
}
