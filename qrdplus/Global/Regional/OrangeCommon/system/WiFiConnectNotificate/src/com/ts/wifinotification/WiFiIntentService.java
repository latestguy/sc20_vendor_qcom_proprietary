/*
 * Copyright (c) 2015 Qualcomm Technologies, Inc.
 * All Rights Reserved.
 * Confidential and Proprietary - Qualcomm Technologies, Inc.
 */

package com.ts.wifinotification;

import android.annotation.SuppressLint;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

public class WiFiIntentService extends IntentService {

    private static final String TAG = "WiFiIntentService";
    private static final String APPLICATION_PACKAGE_NAME = "com.ts.wifinotification";

    public static final String ACTION_CANCAL = "wifi_notification_action_cancel";
    public static final String ACTION_START = "wifi_notification_action_start";

    public static final String WIFI_NAME = "wifi_connection_name";

    private static final String NOTIFICATION_TAG = "WiFi_authentication_problem";

    private static final int NOTIFICATION_ID = 100;

    private NotificationManager mNotificationManager;
    private String message;
    private NotificationCompat.Builder builder;

    public WiFiIntentService() {
        super(APPLICATION_PACKAGE_NAME);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        String action = "";
        if (intent != null) {
            action = intent.getAction();
            if (action.equals(ACTION_START)) {
                createNotification(intent);
            } else if (action.equals(ACTION_CANCAL)) {
                cancelNotificate();
            }
        }
    }

    @SuppressLint("InlinedApi")
    private Intent getIntent() {
        Intent mIntent = new Intent();
        ComponentName mComponentName = new ComponentName(
                "com.android.settings",
                "com.android.settings.wifi.WifiSettings");
        mIntent.setComponent(mComponentName);
        mIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        return mIntent;
    }

    private void createNotification(Intent intent) {

        Intent dissmissItent = new Intent(this, WiFiIntentService.class);
        dissmissItent.setAction(ACTION_CANCAL);
        PendingIntent disPendingIntent = PendingIntent.getService(this, 0,
                dissmissItent, 0);

        Intent okintent = getIntent();
        PendingIntent okPendingIntent = PendingIntent.getActivity(this, 0,
                okintent, PendingIntent.FLAG_UPDATE_CURRENT);

        Intent intents = getIntent();
        builder = new NotificationCompat.Builder(this)
            .setSmallIcon(R.drawable.wifi_auth_fail)
            .setContentTitle(getResources().getString(R.string.wifinoti_authproblem_title))
            .setContentText(getResources().getString(R.string.wifinoti_authproblem_summary))
            .setDefaults(Notification.DEFAULT_ALL)
            .setOngoing(true);
        builder.setContentIntent(PendingIntent.getActivity(getApplicationContext(), NOTIFICATION_ID,
               intents, PendingIntent.FLAG_UPDATE_CURRENT));

        startNotificate(builder);

    }

    private void startNotificate(NotificationCompat.Builder builder) {
        if (mNotificationManager == null) {
            mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            mNotificationManager.notify(NOTIFICATION_TAG, NOTIFICATION_ID,
                    builder.build());
        }
    }

    private void cancelNotificate() {
        if (mNotificationManager == null) {
            mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            mNotificationManager.cancel(NOTIFICATION_TAG, NOTIFICATION_ID);
        }
    }

}
