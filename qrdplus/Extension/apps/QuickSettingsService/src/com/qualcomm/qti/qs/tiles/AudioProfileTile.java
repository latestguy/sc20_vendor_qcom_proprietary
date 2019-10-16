/*
    Copyright (c) 2017 Qualcomm Technologies, Inc.
    All Rights Reserved.
    Confidential and Proprietary - Qualcomm Technologies, Inc.
*/

package com.qualcomm.qti.qs.tiles;

import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.util.Log;

import com.qualcomm.qti.qs.misc.AudioProfilesDialog;
import com.qualcomm.qti.qs.R;
import com.qualcomm.qti.qs.tiles.QSTile;

public class AudioProfileTile extends QSTile{

    private final static String TAG = "AudioProfileTile";
    private final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);
    private AudioManager mAudioManager;
    private int mRingerMode;
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG,"onReceive-intent="+intent);
            handleUpdateState();
        }
    };

    public AudioProfileTile(Context context, String action) {
        super(context, action);
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        handleUpdateState();
        IntentFilter filter = new IntentFilter();
        filter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
        filter.addAction(AudioManager.VOLUME_CHANGED_ACTION);
        context.registerReceiver(mReceiver,filter);
    }

    @Override
    public void handleClick() {
        AudioProfilesDialog dialog = new AudioProfilesDialog(mContext, mRingerMode);
        dialog.show();
    }

    private int getRingerMode() {
        int current = mAudioManager.getStreamVolume(AudioManager.STREAM_RING);
        int maxRingVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_RING);
        int ringerMode = mAudioManager.getRingerMode();
        if (DEBUG) Log.d(TAG,"getRingerMode ringerMode=" + ringerMode +
             " current="+current+" maxRingVolume="+maxRingVolume);
        if (ringerMode == AudioManager.RINGER_MODE_NORMAL){
            if (current == maxRingVolume){
                mRingerMode = AudioProfilesDialog.RINGER_MODE_OUTDOOR;
            } else {
                mRingerMode = AudioProfilesDialog.RINGER_MODE_GENERAL;
            }
        } else if (ringerMode == AudioManager.RINGER_MODE_SILENT) {
            mRingerMode = AudioProfilesDialog.RINGER_MODE_SILENT;
        } else if (ringerMode == AudioManager.RINGER_MODE_VIBRATE) {
            mRingerMode = AudioProfilesDialog.RINGER_MODE_MEETING;
        }
        if (DEBUG) Log.d(TAG,"getRingerMode mRingerMode = "+ mRingerMode);
        return mRingerMode;
    }

    @Override
    public void handleUpdateState() {
        mRingerMode = getRingerMode();
        Intent intent = new Intent(mAction);
        intent.putExtra(EXTRA_ICON_PACKAGE, PACKAGE_NAME);
        intent.putExtra(EXTRA_ON_CLICK_PENDING_INTENT, mOnClickIntent);
        intent.putExtra(EXTRA_VISIBLE, true);
        intent.putExtra(EXTRA_LABEL, getString(mRingerMode));
        intent.putExtra(EXTRA_ICON_ID, getIcon(mRingerMode));
        intent.putExtra(EXTRA_CONTENT_DESCRIPTION, getString(mRingerMode));

        mContext.sendBroadcast(intent);
    }

    private String getString(int ringerMode) {
        switch (ringerMode) {
        case AudioProfilesDialog.RINGER_MODE_GENERAL:
            return mContext.getString(R.string.quick_settings_audio_general);
        case AudioProfilesDialog.RINGER_MODE_SILENT:
            return mContext.getString(R.string.quick_settings_audio_silent);
        case AudioProfilesDialog.RINGER_MODE_MEETING:
            return mContext.getString(R.string.quick_settings_audio_meeting);
        case AudioProfilesDialog.RINGER_MODE_OUTDOOR:
            return mContext.getString(R.string.quick_settings_audio_outdoor);
        default:
            return mContext.getString(R.string.quick_settings_audio_title);
        }
    }

    private int getIcon(int ringerMode) {
        switch (ringerMode) {
        case AudioProfilesDialog.RINGER_MODE_GENERAL:
            return R.drawable.ic_qs_general;
        case AudioProfilesDialog.RINGER_MODE_SILENT:
            return R.drawable.ic_qs_silent;
        case AudioProfilesDialog.RINGER_MODE_MEETING:
            return R.drawable.ic_qs_meeting;
        case AudioProfilesDialog.RINGER_MODE_OUTDOOR:
            return R.drawable.ic_qs_outdoor;
        default:
            return R.drawable.ic_qs_disable;
        }
    }
}
