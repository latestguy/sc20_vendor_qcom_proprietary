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
 * Copyright (C) 2014 The Android Open Source Project
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
import android.os.UserHandle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.provider.Settings;
import android.telecom.TelecomManager;
import android.util.AttributeSet;
import android.util.Log;

public class QtiTtyModeListPref extends ListPreference
        implements Preference.OnPreferenceChangeListener {

    private static final String LOG_TAG = "QtiTtyModeListPref";
    private static final boolean DBG = true;

    public QtiTtyModeListPref(Context ctx, AttributeSet attr) {
        super(ctx, attr);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object objValue) {
        if (preference == this) {
            int btnTtyMode = Integer.parseInt((String) objValue);
            int settingsTtyMode = android.provider.Settings.Secure.getInt(
                    getContext().getContentResolver(),
                    Settings.Secure.PREFERRED_TTY_MODE,
                    TelecomManager.TTY_MODE_OFF);
            if (DBG) log("setting TTY mode enable to" +
                    Integer.toString(btnTtyMode));

            if (btnTtyMode != settingsTtyMode) {
                switch(btnTtyMode) {
                    case TelecomManager.TTY_MODE_OFF:
                    case TelecomManager.TTY_MODE_FULL:
                    case TelecomManager.TTY_MODE_HCO:
                    case TelecomManager.TTY_MODE_VCO:
                        Settings.Secure.putInt(
                                getContext().getContentResolver(),
                                Settings.Secure.PREFERRED_TTY_MODE,
                                btnTtyMode);
                        break;
                    default:
                        btnTtyMode = TelecomManager.TTY_MODE_OFF;
                }

                setValue(Integer.toString(btnTtyMode));
                updatePreferredTtyModeSummary(btnTtyMode);
                Intent ttyModeChanged =
                        new Intent(TelecomManager.ACTION_TTY_PREFERRED_MODE_CHANGED);
                ttyModeChanged.putExtra(TelecomManager.EXTRA_TTY_PREFERRED_MODE, btnTtyMode);
                getContext().sendBroadcastAsUser(ttyModeChanged, UserHandle.ALL);
            }
        }
        return true;
    }

    private void updatePreferredTtyModeSummary(int TtyMode) {
        String [] txts = getContext().getResources()
                              .getStringArray(R.array.tty_mode_entries);
        switch(TtyMode) {
            case TelecomManager.TTY_MODE_OFF:
            case TelecomManager.TTY_MODE_HCO:
            case TelecomManager.TTY_MODE_VCO:
            case TelecomManager.TTY_MODE_FULL:
                setSummary(txts[TtyMode]);
                break;
            default:
                setEnabled(false);
                setSummary(txts[TelecomManager.TTY_MODE_OFF]);
                break;
        }
    }

    public void init() {
        setOnPreferenceChangeListener(this);
        int settingsTtyMode = Settings.Secure.getInt(getContext().getContentResolver(),
                Settings.Secure.PREFERRED_TTY_MODE,
                TelecomManager.TTY_MODE_OFF);
        setValue(Integer.toString(settingsTtyMode));
        updatePreferredTtyModeSummary(settingsTtyMode);
    }


    private static void log(String msg) {
        Log.d(LOG_TAG, msg);
    }
}
