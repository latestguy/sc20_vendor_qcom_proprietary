/*
    Copyright (c) 2017 Qualcomm Technologies, Inc.
    All Rights Reserved.
    Confidential and Proprietary - Qualcomm Technologies, Inc.
*/

package com.qualcomm.qti.qs.misc;

import android.app.AlertDialog;
import android.content.Context;
import android.media.AudioManager;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.KeyEvent;
import android.widget.ImageView;
import com.qualcomm.qti.qs.R;

public class AudioProfilesDialog extends AlertDialog implements
        android.view.View.OnClickListener {

    public static final int RINGER_MODE_SILENT = 0;
    public static final int RINGER_MODE_MEETING = 1;
    public static final int RINGER_MODE_GENERAL = 2;
    public static final int RINGER_MODE_OUTDOOR = 3;

    private Context mContext;
    private static int mRingerMode;
    private ImageView mImgGeneral, mImgSilent, mImgMetting, mImgOutdoor;
    private AudioManager mAudioManager;
    private int maxRingVolume;

    public AudioProfilesDialog(Context context, int ringerMode) {
        super(context);
        mContext = context;
        mRingerMode = ringerMode;
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        maxRingVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_RING);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        init();
    }

    public void init() {
        View view = LayoutInflater.from(mContext).inflate(
                R.layout.quick_settings_audioprofiles_dialog, null);
        setContentView(view);

        mImgGeneral = (ImageView) view.findViewById(R.id.img_general);
        mImgSilent = (ImageView) view.findViewById(R.id.img_silent);
        mImgMetting = (ImageView) view.findViewById(R.id.img_metting);
        mImgOutdoor = (ImageView) view.findViewById(R.id.img_outdoor);
        mImgGeneral.setOnClickListener(this);
        mImgSilent.setOnClickListener(this);
        mImgMetting.setOnClickListener(this);
        mImgOutdoor.setOnClickListener(this);

        updateCurrentModeIcon();

        Window dialogWindow = getWindow();
        dialogWindow.setType(WindowManager.LayoutParams.TYPE_STATUS_BAR_PANEL);
        dialogWindow.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        dialogWindow.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams lp = dialogWindow.getAttributes();
        DisplayMetrics d = mContext.getResources().getDisplayMetrics();
        lp.width = (int) (d.widthPixels * 0.9);
        lp.privateFlags |=
        WindowManager.LayoutParams.PRIVATE_FLAG_SHOW_FOR_ALL_USERS;
        dialogWindow.setAttributes(lp);
    }

    private void updateCurrentModeIcon() {
        setDefaultIcon();
        switch (mRingerMode) {
        case RINGER_MODE_GENERAL:
            mImgGeneral
                    .setImageResource(R.drawable.ic_audio_profile_general_focused);
            break;
        case RINGER_MODE_SILENT:
            mImgSilent
                    .setImageResource(R.drawable.ic_audio_profile_silent_focused);
            break;
        case RINGER_MODE_MEETING:
            mImgMetting
                    .setImageResource(R.drawable.ic_audio_profile_meeting_focused);
            break;
        case RINGER_MODE_OUTDOOR:
            mImgOutdoor
                    .setImageResource(R.drawable.ic_audio_profile_outdoor_focused);
            break;
        default:
            break;
        }
    }

    private void setDefaultIcon() {
        mImgGeneral
                .setImageResource(R.drawable.ic_audio_profile_general_normal);
        mImgSilent.setImageResource(R.drawable.ic_audio_profile_silent_normal);
        mImgMetting
                .setImageResource(R.drawable.ic_audio_profile_meeting_normal);
        mImgOutdoor
                .setImageResource(R.drawable.ic_audio_profile_outdoor_normal);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
        case R.id.img_general:
            mRingerMode = RINGER_MODE_GENERAL;
            updateCurrentModeIcon();
            setAudioProFile(AudioManager.RINGER_MODE_NORMAL, 5);
            break;
        case R.id.img_silent:
            mRingerMode = RINGER_MODE_SILENT;
            updateCurrentModeIcon();
            setAudioProFile(AudioManager.RINGER_MODE_SILENT, 0);
            break;
        case R.id.img_metting:
            mRingerMode = RINGER_MODE_MEETING;
            updateCurrentModeIcon();
            setAudioProFile(AudioManager.RINGER_MODE_VIBRATE, 0);
            break;
        case R.id.img_outdoor:
            mRingerMode = RINGER_MODE_OUTDOOR;
            updateCurrentModeIcon();
            setAudioProFile(AudioManager.RINGER_MODE_NORMAL, maxRingVolume);
            break;
        default:
            break;
        }
        this.dismiss();
    }

    private void setAudioProFile(int ringerMode, int volume) {
        mAudioManager.setStreamVolume(AudioManager.STREAM_RING, volume, 0);
        mAudioManager.setRingerMode(ringerMode);
    }
}
