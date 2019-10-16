/*
 * Copyright (c) 2015 pci-suntektech Technologies, Inc.  All Rights Reserved.
 * pci-suntektech Technologies Proprietary and Confidential.
 */
package com.suntek.mway.rcs.nativeui.ui.backup;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.sqlite.SqliteWrapper;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.os.RemoteException;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.os.Build;
import android.provider.Telephony.Sms;

import java.util.ArrayList;

import com.suntek.mway.rcs.client.aidl.service.entity.SimpleMessage;
import com.suntek.mway.rcs.client.api.message.MessageApi;
import com.suntek.mway.rcs.client.api.exception.ServiceDisconnectedException;
import com.suntek.mway.rcs.nativeui.R;
import com.suntek.rcs.ui.common.RcsLog;

public class RcsManyMessageBackupActivity extends Activity implements View.OnClickListener {

    private ListView mMessageListView;

    private ImageView mBackUpIcon, mCancelBackUp;

    private TextView mBackUpStatus, mLastBackUpTime, mBackUpProgressText;

    private ProgressBar mBackUpProgress;

    private String BACK_UP_MESSAGE_ACTION =
            "com.suntek.mway.rcs.app.service.ACTION_MESSAGE_BACKUP";

    private ArrayList<SimpleMessage> mBackUpSimpleMessageList;

    private static final int STATUS_FAIL = -1;

    private static final int STATUS_START = 0;

    private static final int STATUS_PROGRESS = 1;

    private static final int STATUS_SUCCESS = 2;

    private static final String BACKUP_MESSAGE_LIST = "msgList";

    private static final String BACKUP_MESSAGE_IDS = "ids";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.rcs_backup_many_message);
        initUi();
        registerReceiver();
        new getSelectMessageCursorTask().execute(getBackUpMessageIds());
    }

    @Override
    protected void onDestroy() {
        try {
            unregisterReceiver(mBackUpRestoreMessageReceiver);
        } catch (Exception e) {
            RcsLog.w(e);
        }
        super.onDestroy();
    }

    private void registerReceiver() {
        IntentFilter intent = new IntentFilter();
        intent.addAction(BACK_UP_MESSAGE_ACTION);
        registerReceiver(mBackUpRestoreMessageReceiver, intent);
    }

    private void initUi() {
        mMessageListView = (ListView)findViewById(R.id.many_message_list);
        mBackUpIcon = (ImageView)findViewById(R.id.backup_many_message_icon);
        mBackUpIcon.setOnClickListener(this);
        mCancelBackUp = (ImageView)findViewById(R.id.cancel_backup_many_message);
        mCancelBackUp.setOnClickListener(this);
        mLastBackUpTime = (TextView)findViewById(R.id.last_backup_time);
        mBackUpStatus = (TextView)findViewById(R.id.backup_many_message_status);
        mBackUpStatus.setText(R.string.back_up_message);
        mBackUpProgressText = (TextView)findViewById(R.id.backup_many_message_text);
        mBackUpProgress = (ProgressBar)findViewById(R.id.backup_many_message_progress);
    }

    private String[] getBackUpMessageIds() {
        Bundle bundleExtra = getIntent().getBundleExtra(BACKUP_MESSAGE_LIST);
        mBackUpSimpleMessageList = (ArrayList<SimpleMessage>)bundleExtra
                .getSerializable(BACKUP_MESSAGE_IDS);
        ArrayList<Long> idList = new ArrayList<Long>();
        for (int i = 0; i < mBackUpSimpleMessageList.size(); i++) {
            idList.add(mBackUpSimpleMessageList.get(i).getMessageRowId());
        }
        String[] selectionArgs = new String[idList.size()];
        for (int i = 0; i < idList.size(); i++) {
            selectionArgs[i] = String.valueOf(idList.get(i));
        }
        return selectionArgs;
    }

    public static Cursor queryRcsBackUpMessage(Context context, String[] params) {
        String selection = null;
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < params.length; i++) {
            builder.append(Sms._ID + "=?" + " or ");
        }
        selection = builder.toString().substring(0, builder.toString().lastIndexOf("or"));
        String[] progection = new String[] {
                "_id", "address", "body", "sub_id", "type", "date", "rcs_msg_type"
        };
        Cursor cursor = null;
        try {
            ContentResolver resolver = context.getContentResolver();
            cursor = SqliteWrapper.query(context, resolver, Sms.CONTENT_URI, progection, selection,
                    params, null);
            return cursor;
        } catch (Exception e) {
            RcsLog.w(e);
        }
        return null;
    }

    class getSelectMessageCursorTask extends AsyncTask<String, Integer, Cursor> {

        @Override
        protected Cursor doInBackground(String... params) {
            Cursor cursor = queryRcsBackUpMessage(RcsManyMessageBackupActivity.this, params);
            return cursor;
        }

        @Override
        protected void onPostExecute(Cursor result) {
            super.onPostExecute(result);
            mMessageListView.setAdapter(new RcsBackUpMessageListCursorAdapter(
                    RcsManyMessageBackupActivity.this, result));
        }
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
                        mBackUpProgressText.setVisibility(View.GONE);
                        mBackUpStatus.setText(R.string.backup_message_fail);
                        mBackUpIcon.setImageResource(R.drawable.rcs_backup_restore_error);
                        mBackUpProgress.setVisibility(View.GONE);
                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                mBackUpStatus.setText(R.string.back_up_message);
                                mBackUpIcon.setImageResource(R.drawable.rcs_backup_message);
                            }
                        }, 2000);
                        break;
                    case STATUS_START:
                        mBackUpProgressText.setVisibility(View.VISIBLE);
                        mBackUpProgress.setVisibility(View.VISIBLE);
                        mBackUpStatus.setText(R.string.backup_message_start);
                        mBackUpProgress.setProgress(0);
                        mBackUpProgressText.setText("0/0");
                        break;
                    case STATUS_PROGRESS:
                        mBackUpProgressText.setVisibility(View.VISIBLE);
                        mBackUpProgress.setVisibility(View.VISIBLE);
                        mCancelBackUp.setVisibility(View.VISIBLE);
                        mBackUpStatus.setText(R.string.backup_message_progress);
                        if (progress != 0 && total != 0) {
                            mBackUpProgress.setMax(total);
                            mBackUpProgress.setProgress(progress);
                            mBackUpProgressText.setText(progress + "/" + total);
                        }
                        break;
                    case STATUS_SUCCESS:
                        mBackUpProgress.setVisibility(View.GONE);
                        mCancelBackUp.setVisibility(View.GONE);
                        mBackUpIcon.setImageResource(R.drawable.rcs_backup_restore_success);
                        mBackUpProgressText.setVisibility(View.GONE);
                        mBackUpStatus.setText(R.string.backup_message_success);
                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                mBackUpStatus.setText(R.string.back_up_message);
                                mBackUpIcon.setImageResource(R.drawable.rcs_backup_message);
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
            case R.id.backup_many_message_icon:
                new Thread(new Runnable() {
                    public void run() {
                        try {
                            MessageApi.getInstance().backup(mBackUpSimpleMessageList);
                        } catch (ServiceDisconnectedException e) {
                            RcsLog.w(e);
                        } catch (RemoteException e) {
                            RcsLog.w(e);
                        } catch (Exception e) {
                            RcsLog.w(e);
                        }
                    }
                }).start();
                break;
            case R.id.cancel_backup_many_message:
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
}
