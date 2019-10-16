/*
 * Copyright (c) 2015 pci-suntektech Technologies, Inc.  All Rights Reserved.
 * pci-suntektech Technologies Proprietary and Confidential.
 */
package com.suntek.mway.rcs.nativeui.ui.backup;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.suntek.mway.rcs.client.api.exception.ServiceDisconnectedException;
import com.suntek.mway.rcs.client.api.message.MessageApi;
import com.suntek.mway.rcs.nativeui.R;
import com.suntek.rcs.ui.common.RcsLog;

public class RcsBackupRestoreAllMessageActivity extends Activity implements View.OnClickListener {
    private TextView mBackUpProgressText, mRestoreProgressText;

    private TextView mBackUpState, mRestoreState;

    private ProgressBar mBackUpProgeress, mRestoreProgress;

    private ImageView mCancelBackUp, mCancelRestore, mBackupIcon, mRestoreIcon;

    private String BACK_UP_MESSAGE_ACTION =
            "com.suntek.mway.rcs.app.service.ACTION_MESSAGE_BACKUP";

    private String RESTORE_MESSAGE_ACTION =
            "com.suntek.mway.rcs.app.service.ACTION_MESSAGE_RESTORE";

    private static final int STATUS_FAIL = -1;

    private static final int STATUS_START = 0;

    private static final int STATUS_PROGRESS = 1;

    private static final int STATUS_SUCCESS = 2;

    private static final String BACKUP_RESTORE_FAVORITEMESSAGE = "isFavoriteMessage";
    private boolean mIsFavoriteMessage = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.rcs_backup_restore_all_message);
        if (getIntent().hasExtra(BACKUP_RESTORE_FAVORITEMESSAGE)) {
            mIsFavoriteMessage =
                    getIntent().getBooleanExtra(BACKUP_RESTORE_FAVORITEMESSAGE, false);
        }
        initUI();
        registerRcsReceiver();
    }

    private void registerRcsReceiver() {
        IntentFilter intent = new IntentFilter();
        intent.addAction(BACK_UP_MESSAGE_ACTION);
        intent.addAction(RESTORE_MESSAGE_ACTION);
        registerReceiver(mBackUpRestoreMessageReceiver, intent);
    }

    private void initUI() {
        mBackupIcon = (ImageView)findViewById(R.id.backup_icon);
        mBackupIcon.setOnClickListener(this);
        mRestoreIcon = (ImageView)findViewById(R.id.restore_icon);
        mRestoreIcon.setOnClickListener(this);
        mBackUpProgressText = (TextView)findViewById(R.id.backup_progress_text);
        mRestoreProgressText = (TextView)findViewById(R.id.restore_progress_text);
        mBackUpState = (TextView)findViewById(R.id.backup_status);
        mBackUpState.setText(R.string.back_up_message);

        mRestoreState = (TextView)findViewById(R.id.restore_status);
        mRestoreState.setText(R.string.rcs_restore_message);

        mCancelBackUp = (ImageView)findViewById(R.id.cancel_backup_message);
        mCancelRestore = (ImageView)findViewById(R.id.cancel_restore_message);
        mBackUpProgeress = (ProgressBar)findViewById(R.id.backup_progress);
        mRestoreProgress = (ProgressBar)findViewById(R.id.restore_progress);
    }

    BroadcastReceiver mBackUpRestoreMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int progress = intent.getIntExtra("progress", 0);
            int total = intent.getIntExtra("total", 0);
            int status = intent.getIntExtra("status", 0);
            String action = intent.getAction();
            RcsLog.i("action=" + action + ",progress=" + progress + ",total=" + total + ",status="
                    + status);
            if (BACK_UP_MESSAGE_ACTION.equals(action)) {
                switch (status) {
                    case STATUS_FAIL:
                        // TODO fail
                        mBackUpProgressText.setVisibility(View.GONE);
                        mBackUpProgeress.setVisibility(View.GONE);
                        mCancelBackUp.setVisibility(View.GONE);
                        mBackUpState.setText(R.string.backup_message_fail);
                        mBackupIcon.setImageResource(R.drawable.rcs_backup_restore_error);
                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                mBackUpState.setText(R.string.back_up_message);
                                mBackupIcon.setImageResource(R.drawable.rcs_backup_message);
                            }
                        }, 2000);
                        break;
                    case STATUS_START:
                        // TODO start
                        mBackUpProgressText.setVisibility(View.VISIBLE);
                        mBackUpProgeress.setVisibility(View.VISIBLE);
                        mBackUpState.setText(R.string.backup_message_start);
                        mBackUpProgeress.setProgress(0);
                        mBackUpProgressText.setText("0/0");
                        break;
                    case STATUS_PROGRESS:
                        // TODO progeress
                        mBackUpProgressText.setVisibility(View.VISIBLE);
                        mBackUpProgeress.setVisibility(View.VISIBLE);
                        mCancelBackUp.setVisibility(View.VISIBLE);
                        mBackUpState.setText(R.string.backup_message_progress);
                        if (progress != 0 && total != 0) {
                            mBackUpProgeress.setMax(total);
                            mBackUpProgeress.setProgress(progress);
                            mBackUpProgressText.setText(progress + "/" + total);
                        }
                        break;
                    case STATUS_SUCCESS:
                        // TODO success:
                        mBackUpProgeress.setVisibility(View.GONE);
                        mCancelBackUp.setVisibility(View.GONE);
                        mBackupIcon.setImageResource(R.drawable.rcs_backup_restore_success);
                        mBackUpProgressText.setVisibility(View.GONE);
                        mBackUpState.setText(R.string.backup_message_success);
                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                mBackUpState.setText(R.string.back_up_message);
                                mBackupIcon.setImageResource(R.drawable.rcs_backup_message);
                            }
                        }, 2000);
                        break;

                }
            } else if (RESTORE_MESSAGE_ACTION.equals(action)) {
                switch (status) {
                    case STATUS_FAIL:
                        mRestoreProgressText.setVisibility(View.GONE);
                        mRestoreProgress.setVisibility(View.GONE);
                        mCancelRestore.setVisibility(View.GONE);
                        mRestoreState.setText(R.string.restore_message_fail);
                        mRestoreIcon.setImageResource(R.drawable.rcs_backup_restore_error);
                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                mRestoreState.setText(R.string.rcs_restore_message);
                                mRestoreIcon.setImageResource(R.drawable.rcs_backup_message);
                            }
                        }, 2000);
                        break;
                    case STATUS_START:
                        mRestoreProgressText.setVisibility(View.VISIBLE);
                        mRestoreProgress.setVisibility(View.VISIBLE);
                        mRestoreState.setText(R.string.restore_message_start);
                        mRestoreProgress.setProgress(0);
                        mRestoreProgressText.setText("0/0");
                        break;
                    case STATUS_PROGRESS:
                        mRestoreProgressText.setVisibility(View.VISIBLE);
                        mRestoreProgress.setVisibility(View.VISIBLE);
                        mCancelRestore.setVisibility(View.VISIBLE);
                        mRestoreState.setText(R.string.restore_message_progress);
                        if (progress != 0 && total != 0) {
                            mRestoreProgress.setMax(total);
                            mRestoreProgress.setProgress(progress);
                            mRestoreProgressText.setText(progress + "/" + total);
                        }
                        break;
                    case STATUS_SUCCESS:
                        mRestoreProgress.setVisibility(View.GONE);
                        mCancelRestore.setVisibility(View.GONE);
                        mRestoreIcon.setImageResource(R.drawable.rcs_backup_restore_success);
                        mRestoreProgressText.setVisibility(View.GONE);
                        mRestoreState.setText(R.string.restore_message_success);
                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                mRestoreState.setText(R.string.rcs_restore_message);
                                mRestoreIcon.setImageResource(R.drawable.rcs_backup_message);
                            }
                        }, 2000);
                        break;
                }
            }
        }
    };

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.backup_icon:
                new Thread(new Runnable() {
                    public void run() {
                        try {
                            if (mIsFavoriteMessage){
                                MessageApi.getInstance().backUpFavouriteAll();
                            } else {
                                MessageApi.getInstance().backupAll();
                            }
                        } catch (ServiceDisconnectedException e) {
                            RcsLog.w(e);
                        } catch (RemoteException e) {
                            RcsLog.w(e);
                        }
                    }
                }).start();
                break;
            case R.id.restore_icon:
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            if (mIsFavoriteMessage) {
                                MessageApi.getInstance().restoreAllFavourite();
                            } else {
                                MessageApi.getInstance().restoreAll();
                            }
                        } catch (ServiceDisconnectedException e) {
                            RcsLog.w(e);
                        } catch (RemoteException e) {
                            RcsLog.w(e);
                        }

                    }
                }).start();
                break;
            case R.id.cancel_backup_message:
                try {
                    MessageApi.getInstance().cancelBackup();
                } catch (Exception e) {
                    RcsLog.w(e);
                }
                break;
            case R.id.cancel_restore_message:
                try {
                    MessageApi.getInstance().cancelBackup();
                } catch (Exception e) {
                    RcsLog.w(e);
                }
                break;

            default:
                break;
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mBackUpRestoreMessageReceiver != null) {
            unregisterReceiver(mBackUpRestoreMessageReceiver);
        }
    }

}
