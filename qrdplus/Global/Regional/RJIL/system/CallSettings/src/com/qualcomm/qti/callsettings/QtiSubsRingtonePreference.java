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
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.preference.RingtonePreference;
import android.util.AttributeSet;
import android.widget.Toast;
import android.util.Log;

public class QtiSubsRingtonePreference extends RingtonePreference {

    public QtiSubsRingtonePreference(Context ctx, AttributeSet attr) {
        super(ctx, attr);
    }

    public QtiSubsRingtonePreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context,attrs,defStyleAttr);
    }

    public QtiSubsRingtonePreference(Context context, AttributeSet attrs,
                                 int defStyleAttr, int defStyleRes) {
        super(context,attrs,defStyleAttr,defStyleRes);
    }

    public QtiSubsRingtonePreference(Context context) {
        super(context);
    }

    @Override
    protected Uri onRestoreRingtone() {
        return RingtoneManager.getActualDefaultRingtoneUri(getContext(), getRingtoneType());
    }

    @Override
    protected void onPrepareRingtonePickerIntent(Intent ringtonePickerIntent) {
        super.onPrepareRingtonePickerIntent(ringtonePickerIntent);
        ringtonePickerIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false);
    }


    @Override
    protected void onSaveRingtone(Uri ringtoneUri) {
        RingtoneManager.setActualDefaultRingtoneUri(getContext(), getRingtoneType(), ringtoneUri);
    }
}
