/**
 * Copyright (c) 2016 Qualcomm Technologies, Inc.
 * All Rights Reserved.
 * Confidential and Proprietary - Qualcomm Technologies, Inc.
 **/
package com.qualcomm.qti.rcs;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;

import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;

import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.SmsApplication;

import org.codeaurora.rcscommon.RcsManager;

/**
 * This class receives ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED broadcast and
 * set the Target app as default messaging app.
 *
 * Jio messaging application will be set as default app when jio sim is inserted.
 * It will also set previously user set messaging app or native messaging app as
 * default messaging app when Jio sim is removed.
 */
public class DefaultSmsControllerReceiver extends BroadcastReceiver {
    private static final String TAG = "DefaultSmsControllerReceiver";
    private static final String JIO_PACKAGE_NAME = "com.jio.join";
    private static final String PREV_DEFAULT_SMS_APP = "previousDefaultSmsApp";
    private static final String PREV_DEFAULT_SMS_PREF = "DefaultSmsApp";
    private static final String DEFAULT_SMS_ON_ICCID = "defaultSmsOnIccID";
    private static final boolean DBG = RcsService.DBG;
    private static final String DEVICE_DEFAULT_SMS_PACKAGE_NAME = "deviceDefaultSmsPackageName";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (TelephonyIntents.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED.equals(intent.getAction())) {
            log("ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED received");

            int subId = intent.getIntExtra(PhoneConstants.SUBSCRIPTION_KEY,
                    SubscriptionManager.INVALID_SUBSCRIPTION_ID);
            log("data subscription changed on sub = " + subId);
            if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                log("invalid data subscription = " + subId);
                return;
            }

            String defaultSmsAppPkgName = getDefaultSmsAppPkgName(context);
            if (TextUtils.isEmpty(defaultSmsAppPkgName)) {
                log("default sms is not present, no need to continue");
                return;
            }

            SharedPreferences pref = context.getSharedPreferences(PREV_DEFAULT_SMS_PREF,
                    Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = pref.edit();
            String prefDeviceDefaultSms = pref.getString(DEVICE_DEFAULT_SMS_PACKAGE_NAME, null);
            if (null == prefDeviceDefaultSms) {
                /* first time device bootup, store the default sms app */
                log("device default sms pkg = " + defaultSmsAppPkgName);
                editor.putString(DEVICE_DEFAULT_SMS_PACKAGE_NAME, defaultSmsAppPkgName);
                editor.commit();
            }

            if (RcsManager.getInstance(context).isRcsConfigEnabledonSub(subId)) {
                handleRcsSimSubscriptionChange(context, pref);
            } else {
                handleNonRcsSimSubscriptionChange(context, pref);
            }

        }
    }

    /**
     * handleRcsSimSubscriptionChange handles RCS SIM subscription change
     *
     * @param : Context, SharedPreferences
     */
    private void handleRcsSimSubscriptionChange(Context context,
            SharedPreferences pref) {
        if (!isPackageInstalled(JIO_PACKAGE_NAME, context)) {
            log("join app is not present");
            return;
        }

        String iccId = getIccId(context);
        String prevIccId = pref.getString(DEFAULT_SMS_ON_ICCID, null);
        if (TextUtils.isEmpty(iccId)) {
            /* SIM removal case - hot swap */
            handleEmptyIccId(context, pref, true);
        } else if (!isEquals(iccId, prevIccId)) {
            String defaultSmsAppPkgName = getDefaultSmsAppPkgName(context);
            if (JIO_PACKAGE_NAME.equals(defaultSmsAppPkgName)) {
                log("join app is already set as default");
            } else {
                log("Set default SMS app = " + JIO_PACKAGE_NAME);
                SmsApplication.setDefaultApplication(JIO_PACKAGE_NAME, context);
                SharedPreferences.Editor editor = pref.edit();
                editor.putString(PREV_DEFAULT_SMS_APP, defaultSmsAppPkgName);
                editor.putString(DEFAULT_SMS_ON_ICCID, iccId);
                editor.commit();
            }
        } else {
            log("Case of device reboot");
        }
    }

    /**
     * handleNonRcsSimSubscriptionChange handles non RCS and no SIM cases
     *
     * @param : Context, SharedPreferences
     */
    private void handleNonRcsSimSubscriptionChange(Context context,
            SharedPreferences pref) {
        String defaultSmsAppPkgName = getDefaultSmsAppPkgName(context);
        if (!isEquals(defaultSmsAppPkgName, JIO_PACKAGE_NAME)) {
            log("non jio sms app is set as default");
            return;
        }

        String iccId = getIccId(context);
        String prevIccId = pref.getString(DEFAULT_SMS_ON_ICCID, null);

        if (TextUtils.isEmpty(iccId)) {
            /* NO SIM case */
            handleEmptyIccId(context, pref, false);
        } else if (!isEquals(iccId, prevIccId)) {
            String prevDefault = pref.getString(PREV_DEFAULT_SMS_APP, null);
            setPrevDefaultMessagingApp(prevDefault, context, pref);
            SharedPreferences.Editor editor = pref.edit();
            editor.putString(DEFAULT_SMS_ON_ICCID, iccId);
            editor.commit();
        } else {
            log("Case of device reboot");
        }
    }

    /**
     * handleEmptyIccId handles invalid/No SIM usecases
     *
     * @param : Context, SharedPreferences and boolean IsRCSSim
     */
    private void handleEmptyIccId(Context context, SharedPreferences pref, boolean isRCSSim) {
        String prevDefault = pref.getString(PREV_DEFAULT_SMS_APP, null);
        String defaultSmsAppPkgName = getDefaultSmsAppPkgName(context);
        if (isRCSSim) {
            if (isEquals(defaultSmsAppPkgName, JIO_PACKAGE_NAME)) {
                setPrevDefaultMessagingApp(prevDefault, context, pref);
            } else {
                log("user preferred sms app is already set");
            }
        } else {
            if (isEquals(prevDefault, defaultSmsAppPkgName)) {
                log("default sms app is already set");
            } else {
                setPrevDefaultMessagingApp(prevDefault, context, pref);
            }
        }
        SharedPreferences.Editor editor = pref.edit();
        resetDefaultSmsPrefs(editor);
    }

    /**
     * getDefaultSmsAppPkgName get the default sms appplication
     *
     * @param : context to read the sms applicaiton package name
     * @return String of default sms application package name.
     */
    private String getDefaultSmsAppPkgName(Context context) {
        log("getDefaultSmsAppPkgName");
        ComponentName appName = SmsApplication.getDefaultSmsApplication(context, true);
        if (appName != null) {
            return appName.getPackageName();
        }
        return null;
    }

    /**
     * isPackageInstalled checks if package is present in device.
     *
     * @param : string package name and context.
     * @return boolean, true if package is installed else false.
     */
    private boolean isPackageInstalled(String packagename, Context context) {
        log("isPackageInstalled");
        PackageManager pm = context.getPackageManager();
        try {
            pm.getPackageInfo(packagename, PackageManager.GET_ACTIVITIES);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /**
     * getIccId get iccid of SIM
     *
     * @param : context
     * @return string of iccid
     */
    private String getIccId(Context context) {
        log("getIccId");
        String iccId = null;
        final SubscriptionInfo sir = SubscriptionManager.from(context)
                .getDefaultDataSubscriptionInfo();
        if (sir != null) {
            iccId = sir.getIccId();
            log("getIccId SubscriptionInfo simslotindex: " + sir.getSimSlotIndex() + " subId: "
                    + sir.getSubscriptionId() + " iccid:" + iccId);
        } else {
            log("Null iccId : no sim or invalid sim case");
        }
        return iccId;
    }

    /**
     * @param : previous default app pkg name, context, sharedpref
     * @return void
     */
    private void setPrevDefaultMessagingApp(String prevDefault, Context context,
            SharedPreferences pref) {
        log("setPrevDefaultMessagingApp");
        if ((null != prevDefault) && isPackageInstalled(prevDefault, context)) {
            log("Set default SMS app = " + prevDefault);
            SmsApplication.setDefaultApplication(prevDefault, context);
        } else {
            String deviceDefaultSmsPackage = pref.getString(DEVICE_DEFAULT_SMS_PACKAGE_NAME, null);
            if (null != deviceDefaultSmsPackage) {
                log("Set default SMS app = " + deviceDefaultSmsPackage);
                SmsApplication.setDefaultApplication(deviceDefaultSmsPackage, context);
            } else {
                log("This case should never occur!!!");
            }
        }
    }

    /**
     * isEquals compares both the string.
     *
     * @param 2 string which need to be compared
     * @return true if both the strings are equal else false.
     */
    private boolean isEquals(String first, String second) {
        return (!TextUtils.isEmpty(first)) && (!TextUtils.isEmpty(second))
                && (first.equals(second));
    }

    /**
     * resetDefaultSmsPrefs reset the appliaction sharedpreference to default
     * values.
     *
     * @param SharedPreferences
     *            .Editor to edit shared preference.
     */
    private void resetDefaultSmsPrefs(SharedPreferences.Editor editor) {
        log("resetDefaultSmsPrefs");
        editor.putString(DEFAULT_SMS_ON_ICCID, "");
        editor.putString(PREV_DEFAULT_SMS_APP, "");
        editor.commit();
    }

    private void log(String msg) {
        if (DBG) {
            android.util.Log.d(TAG, msg);
        }
    }
}
