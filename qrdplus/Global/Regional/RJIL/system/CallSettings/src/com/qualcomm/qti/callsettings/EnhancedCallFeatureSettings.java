/**
 * Copyright (c) 2016-2017 Qualcomm Technologies, Inc.
 * All Rights Reserved.
 * Confidential and Proprietary - Qualcomm Technologies, Inc.
 */

package com.qualcomm.qti.callsettings;

import android.app.ActionBar;
import android.app.Activity;
import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.CarrierConfigManager;
import android.telephony.PhoneStateListener;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;

import com.android.ims.ImsConfig;
import com.android.ims.ImsManager;
import com.android.internal.telephony.CallForwardInfo;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;

import java.lang.String;
import java.util.ArrayList;
import java.util.List;


public class EnhancedCallFeatureSettings extends PreferenceActivity implements
        Preference.OnPreferenceChangeListener {

    private final String LOG_TAG = "EnhancedCallFeatureSettings";
    private final boolean DBG = true;

    public static final String SETTINGS_SHOW_CALL_DURATION = "show_call_duration";
    public static final String SETTINGS_PROXIMITY_SENSOR = "proximity_sensor";
    public static final String SETTINGS_VIBRATE_WHEN_ACCEPTED = "vibrate_on_accepted";
    /*
     * Declaring KEYS for preferences.
     */
    private final String KEY_WFC_SETTINGS = "key_wifi_call_settings";
    private final String KEY_SIM_SETTINGS = "key_sim_settings";
    private final String KEY_TTY_MODE_SETTINGS = "key_tty_mode_settings";
    private final String KEY_DMFT_TONES_SETTINGS = "key_dmft_tones_settings";
    private final String KEY_DISPLAY_DURATION_SETTINGS = "key_display_duration_settings";
    private final String KEY_SPEED_DIAL_SETTINGS = "key_speed_dial_settings";
    private final String KEY_SMART_FORWARD_SETTINGS = "key_smart_forward_settings";
    private final String KEY_PROXIMITY_SETTINGS = "key_proximity_settings";
    private final String KEY_VIBRATION_SETTINGS = "key_vibration_settings";

    private Phone mPhone;
    private TelecomManager mTelecomManager;

    private CheckBoxPreference mDisplayDurationCheckBoxPref;
    private Preference mWifiCallingPref;
    private QtiTtyModeListPref mTtyModeListPref;
    private Preference mSmartForwardPref;
    private CheckBoxPreference mProximityCheckboxPref;
    private CheckBoxPreference mVibrateCheckboxPref;

    private final PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
        /**
         * Disable the TTY setting when in/out of a call (and if carrier doesn't
         * support VoLTE with TTY).
         * @see android.telephony.PhoneStateListener#onCallStateChanged(int,
         * java.lang.String)
         */
        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            Preference pref = getPreferenceScreen().findPreference(KEY_TTY_MODE_SETTINGS);
            if (pref != null) {
                final boolean isVolteTtySupported = ImsManager.isVolteEnabledByPlatform(
                        EnhancedCallFeatureSettings.this) && isVolteTtySupported();
                pref.setEnabled((isVolteTtySupported && !isVideoCallInProgress()) ||
                        (state == TelephonyManager.CALL_STATE_IDLE));
            }
        }
    };

    /**
     * Implemented to support onPreferenceChangeListener to look for preference
     * changes./
     *
     * @param preference is the preference to be changed
     * @param objValue should be the value of the selection, NOT its localized
     * display value.
     */
    @Override
    public boolean onPreferenceChange(Preference preference, Object objValue) {
       if (preference == mDisplayDurationCheckBoxPref) {
            boolean checked = (Boolean) objValue;
            Settings.System.putInt(mPhone.getContext().getContentResolver(),
                    SETTINGS_SHOW_CALL_DURATION, checked ? 1 : 0);
            mDisplayDurationCheckBoxPref.setSummary(checked ? R.string.duration_enable_summary
                    : R.string.duration_disable_summary);
            return true;
       } else if (preference == mProximityCheckboxPref) {
            boolean isChecked = (Boolean) objValue;
            Settings.System.putInt(mPhone.getContext().getContentResolver(),
                    SETTINGS_PROXIMITY_SENSOR, isChecked ? 1 : 0);
            mProximityCheckboxPref.setSummary(isChecked ? R.string.proximity_on_summary
                    : R.string.proximity_off_summary);
            return true;
        } else if (preference == mVibrateCheckboxPref) {
            boolean shouldVibrate = (Boolean) objValue;
            Settings.System.putInt(mPhone.getContext().getContentResolver(),
                    SETTINGS_VIBRATE_WHEN_ACCEPTED, shouldVibrate ? 1 : 0);
            return true;
        }
       return false;
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
       if (preference == mTtyModeListPref) {
            return true;
       }
       return false;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        if (DBG) log("onCreate: Intent is " + getIntent());

        // To make sure we are running as an admin user.
        if (!UserManager.get(this).isAdminUser()) {
            Toast.makeText(this, R.string.call_settings_admin_user_only,
                    Toast.LENGTH_SHORT).show();
            Log.e(LOG_TAG,"Non Admin users not allowed to access");
            finish();
            return;
        }

        mTelecomManager = TelecomManager.from(this);
        mPhone = PhoneFactory.getDefaultPhone();
    }

    @Override
    protected void onResume() {
        super.onResume();
        TelephonyManager tm =
                (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        tm.listen(mPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);

        PreferenceScreen preferenceScreen = getPreferenceScreen();
        if (preferenceScreen != null) {
            preferenceScreen.removeAll();
        }
        addPreferencesFromResource(R.xml.qti_call_settings);
        mWifiCallingPref = findPreference(KEY_WFC_SETTINGS);
        mTtyModeListPref = (QtiTtyModeListPref) findPreference(KEY_TTY_MODE_SETTINGS);
        mDisplayDurationCheckBoxPref =
                (CheckBoxPreference) findPreference(KEY_DISPLAY_DURATION_SETTINGS);
        mProximityCheckboxPref =
                (CheckBoxPreference) findPreference(KEY_PROXIMITY_SETTINGS);
        mVibrateCheckboxPref =
                (CheckBoxPreference) findPreference(KEY_VIBRATION_SETTINGS);
        mSmartForwardPref = findPreference(KEY_SMART_FORWARD_SETTINGS);
        int resId = com.android.internal.R.string.wifi_calling_off_summary;
        if (ImsManager.isWfcEnabledByUser(mPhone.getContext())) {
            resId = com.android.internal.R.string.wfc_mode_wifi_preferred_summary;
        }
        mWifiCallingPref.setSummary(resId);

        //adding TTY Select option
        addTTYSettings();
        addDisplayDurationSettings();
        addSmartDivertSettings();
        addProximitySettings();
        addVibrationSettings();
    }

    @Override
    public void onPause() {
        super.onPause();
        TelephonyManager tm =
                (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        tm.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
    }

    private void addTTYSettings() {
            mTtyModeListPref.init();
    }

    private void addSmartDivertSettings() {
        if (TelephonyManager.getDefault().getMultiSimConfiguration() !=
                 TelephonyManager.MultiSimVariants.DSDS) {
           if (mSmartForwardPref != null) {
               getPreferenceScreen().removePreference(mSmartForwardPref);
           }
        }
    }

    private boolean isVolteTtySupported() {
        CarrierConfigManager configManager =
                (CarrierConfigManager) getSystemService(Context.CARRIER_CONFIG_SERVICE);
        return configManager.getConfig().getBoolean(
                CarrierConfigManager.KEY_CARRIER_VOLTE_TTY_SUPPORTED_BOOL);
    }

    private boolean isVideoCallInProgress() {
        final Phone[] phones = PhoneFactory.getPhones();
        if (phones == null) {
            if (DBG) log("isVideoCallInProgress: Phones bot available . Returning false");
            return false;
        }
        for (Phone phone : phones) {
            if (phone.isImsVideoCallOrConferencePresent()) {
                return true;
            }
        }
        return false;
    }

    private void addDisplayDurationSettings() {
        if (mDisplayDurationCheckBoxPref != null) {
            mDisplayDurationCheckBoxPref.setOnPreferenceChangeListener(this);
            boolean checked = Settings.System.getInt(getContentResolver(),
                    SETTINGS_SHOW_CALL_DURATION, 1) == 1;
                    mDisplayDurationCheckBoxPref.setChecked(checked);
                    mDisplayDurationCheckBoxPref.setSummary(checked
                            ? R.string.duration_enable_summary
                            : R.string.duration_disable_summary);
        }
    }

    private void addProximitySettings() {
        if (mProximityCheckboxPref != null) {
            mProximityCheckboxPref.setOnPreferenceChangeListener(this);
            boolean isChecked = Settings.System.getInt(getContentResolver(),
                    SETTINGS_PROXIMITY_SENSOR, 1) == 1;
            mProximityCheckboxPref.setChecked(isChecked);
            mProximityCheckboxPref.setSummary(isChecked ? R.string.proximity_on_summary
                    : R.string.proximity_off_summary);
        }
    }

    private void addVibrationSettings() {
        if (mVibrateCheckboxPref != null) {
            mVibrateCheckboxPref.setOnPreferenceChangeListener(this);
            boolean isChecked = Settings.System.getInt(getContentResolver(),
                    SETTINGS_VIBRATE_WHEN_ACCEPTED, 1) == 1;
            mVibrateCheckboxPref.setChecked(isChecked);
        }
    }

    private void log(String msg) {
        Log.d(LOG_TAG, msg);
    }
}
