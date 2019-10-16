/*
 * Copyright (c) 2015 pci-suntektech Technologies, Inc.  All Rights Reserved.
 * pci-suntektech Technologies Proprietary and Confidential.
 */

package com.suntek.mway.rcs.nativeui.receiver;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.RemoteException;
import android.widget.Toast;

import com.suntek.mway.rcs.client.aidl.constant.Actions;
import com.suntek.mway.rcs.client.aidl.constant.Parameter;
import com.suntek.mway.rcs.client.aidl.constant.Constants;
import com.suntek.mway.rcs.client.api.basic.BasicApi;
import com.suntek.mway.rcs.client.api.exception.ServiceDisconnectedException;
import com.suntek.mway.rcs.nativeui.R;
import com.suntek.mway.rcs.nativeui.RcsNativeUIApp;
import com.suntek.mway.rcs.nativeui.ui.DialogActivity;
import com.suntek.mway.rcs.nativeui.ui.InputSmsVerifyCodeActivity;
import com.suntek.rcs.ui.common.RcsLog;

public class DmsReceiver extends BroadcastReceiver {

    public static final String REQUEST_DMS_CONFIG_VERSION = "-2";
    public static final String ACTION_DMS_OPEN_BUSS = Actions.DmsAction.ACTION_DMS_OPEN_ACCOUNT;

    public static final String ACTION_OPEN_PS = Actions.DmsAction.ACTION_DMS_OPEN_PS;

    public static final String ACTION_DMS_OPEN_BUSS_RESULT =
            Actions.DmsAction.ACTION_DMS_OPEN_ACCOUNT_RESULT;

    public static final String ACTION_CONFIRM_USE_NEW_IMSI =
            Actions.DmsAction.ACTION_DMS_CONFIRM_USE_NEW_IMSI;

    public static final String ACTION_DMS_USER_STATUS_CHANGED =
            Actions.DmsAction.ACTION_DMS_USER_STATUS_CHANGED;

    public static final String ACTION_FETCH_CONFIG_FINISH =
            Actions.DmsAction.ACTION_DMS_FETCH_CONFIG_FINISH;

    public static final String ACTION_DMS_INPUT_SMS_VERIFY_CODE =
            Actions.DmsAction.ACTION_DMS_INPUT_SMS_VERIFY_CODE;

    public static final String ACTION_SET_MOBILE_DATA =
            "com.suntek.mway.rcs.ACTION_SET_MOBILE_DATA";

    public static final String ACTION_GET_MOBILE_DATA =
            "com.suntek.mway.rcs.ACTION_GET_MOBILE_DATA";
    public static final String ACTION_GET_MOBILE_DATA_RESPONSE =
            "com.suntek.mway.rcs.ACTION_GET_MOBILE_DATA_RESPONSE";
    public static final String EXTRA_MOBILE_DATA_ENABLE =
            "extra_mobile_data_enable";

    public static final String ACTION_FETCH_CONFIG_ERROR =
            Actions.DmsAction.ACTION_DMS_FETCH_CONFIG_ERROR;
    public static final String EXTRA_OPER_RESULTCODE = "resultCode";

    public static final String ACTION_UI_SIM_NOT_BELONG_CMCC =
            Actions.DmsAction.ACTION_UI_SIM_NOT_BELONG_CMCC;
    private int mNotificationId;

    @Override
    public void onReceive(final Context context, Intent intent) {
        String action = intent.getAction();
        RcsLog.i("DmsReceiver receive action=" + action);
        if (ACTION_DMS_OPEN_BUSS.equals(action)) {
            String title = intent.getStringExtra(Parameter.EXTRA_DISPLAY_TITLE);
            String message = intent.getStringExtra(Parameter.EXTRA_DISPLAY_MESSAGE);
            int accept_btn = intent.getIntExtra(Parameter.EXTRA_DISPLAY_ACCEPT_BUTTON, -1);
            int rejectBtn = intent.getIntExtra(Parameter.EXTRA_DISPLAY_REJECT_BUTTON, -1);
            DialogActivity.startOpenAccountDialog(context, title, message, accept_btn, rejectBtn);
        } else if (ACTION_OPEN_PS.equals(action)) {
            int isAuto = intent.getIntExtra(Parameter.EXTRA_OPEN_PS_IS_AUTO, 0);
            RcsLog.i("DmsReceiver open ps isAuto=" + isAuto);
            if (isAuto == Constants.DMSConstants.CONST_OPEN_PS_PROMPT) {
                DialogActivity.startCloseWifiOpenPs(context);
            } else {
                DialogActivity.startOpenPSDialog(context);
            }
        } else if (ACTION_DMS_OPEN_BUSS_RESULT.equals(action)) {
            int resutlCode = intent.getIntExtra(Parameter.EXTRA_CODE, -1);
            String msisdn = intent.getStringExtra(Parameter.EXTRA_MSISDN);
            String resutl = intent.getStringExtra(Parameter.EXTRA_DESC);
            if (resutlCode == Constants.DMSConstants.CONST_OPEN_ACCOUNT_SUCCESS) {
                Toast.makeText(context, context.getString(R.string.auto_open_buss_succeed),
                        Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(context, context.getString(R.string.auto_open_buss_fail) + resutl,
                        Toast.LENGTH_LONG).show();
            }
            if (RcsNativeUIApp.isNeedClosePs()) {
                setMobileData(context, false);
                RcsNativeUIApp.setNeedClosePs(false);
            }
            if (RcsNativeUIApp.isNeedOpenWifi()) {
                openWifi(context);
                RcsNativeUIApp.setNeedOpenWifi(false);
            }

        } else if (ACTION_CONFIRM_USE_NEW_IMSI.equals(action)) {
        } else if (ACTION_DMS_USER_STATUS_CHANGED.equals(action)) {
            int state = intent.getIntExtra("status", 0);
            String message = intent.getStringExtra(Parameter.EXTRA_DISPLAY_MESSAGE);
            Toast.makeText(context, message, Toast.LENGTH_LONG).show();
        } else if (ACTION_FETCH_CONFIG_FINISH.equals(action)
                || ACTION_FETCH_CONFIG_ERROR.equals(action)) {
            if (RcsNativeUIApp.isNeedClosePs()) {
                setMobileData(context, false);
                RcsNativeUIApp.setNeedClosePs(false);
            }
            if (RcsNativeUIApp.isNeedOpenWifi()) {
                openWifi(context);
                RcsNativeUIApp.setNeedOpenWifi(false);
            }
        } else if (ACTION_GET_MOBILE_DATA_RESPONSE.equals(action)) {
            boolean state = intent.getBooleanExtra(EXTRA_MOBILE_DATA_ENABLE, false);
            RcsLog.i("Dms Receiver ACTION_GET_MOBILE_DATA_RESPONSE state=" + state);
            RcsNativeUIApp.setNeedClosePs(!state);
            closeWifi(context);
            setMobileData(context, true);
        } else if (ACTION_DMS_INPUT_SMS_VERIFY_CODE.equals(action)) {
            Intent intentCode = new Intent(context, InputSmsVerifyCodeActivity.class);
            intentCode.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intentCode);
        } else if (ACTION_UI_SIM_NOT_BELONG_CMCC.equals(action)) {
            notifySimNotBelongToCMCC(context);
        }
    }

    private void notifySimNotBelongToCMCC(Context context) {
        Notification.Builder mBuilder =
                new Notification.Builder(context)
                .setSmallIcon(R.drawable.rcs_native_action_refresh)
                .setDefaults(Notification.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentTitle(context.getString(R.string.rcs_service_is_not_available))
                .setContentText(context.getString(R.string.not_cmcc_sim));

        Intent resultIntent = new Intent();
        PendingIntent resultPendingIntent = PendingIntent.getActivity(context, mNotificationId,
                resultIntent, 0, null);
        mBuilder.setContentIntent(resultPendingIntent);

        NotificationManager mNotificationManager =
            (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        // mNotificationId allows you to update the notification later on.
        mNotificationManager.notify(mNotificationId, mBuilder.build());
    }

    public static void closeWifi(Context context){
        RcsLog.i("Receive auto open ps ,close wifi");
        WifiManager mWifiManager = (WifiManager) context
                .getSystemService(Context.WIFI_SERVICE);
        if (mWifiManager.isWifiEnabled()){
            RcsNativeUIApp.setNeedOpenWifi(true);
            WifiInfo wifiInfo = mWifiManager.getConnectionInfo();
            if (wifiInfo != null) {
                RcsNativeUIApp.setWifiNetworkId(wifiInfo.getNetworkId());
            }
            mWifiManager.setWifiEnabled(false);
        }
    }

    public static void openWifi(Context context){
        WifiManager mWifiManager = (WifiManager) context
                .getSystemService(Context.WIFI_SERVICE);
        mWifiManager.setWifiEnabled(true);
        int networkId = RcsNativeUIApp.getWifiNetworkId();
        if(networkId != -1) {
             boolean bRet = mWifiManager.enableNetwork(networkId, true);
        }
    }

    public static void setMobileData(Context context, boolean pBoolean) {
        RcsLog.i("DmsReceiver setMobileData open =" + pBoolean);
        Intent intent = new Intent(ACTION_SET_MOBILE_DATA);
        intent.putExtra("isOpen", pBoolean);
        context.sendBroadcast(intent);
    }
}
