/*
 * Copyright (c) 2014-2015 pci-suntektech Technologies, Inc.  All Rights Reserved.
 * pci-suntektech Technologies Proprietary and Confidential.
 */

package com.suntek.mway.rcs.nativeui.ui;

import android.app.ActionBar;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;


import com.suntek.mway.rcs.client.aidl.constant.Actions;
import com.suntek.mway.rcs.nativeui.R;
import com.suntek.mway.rcs.nativeui.service.RcsNotificationList;
import com.suntek.mway.rcs.nativeui.utils.RcsNotifyUtil;

public class RcsNotificationListActivity extends Activity {

    private ListView mNotifyListView;
    private RcsNotificationListAdapter mAdapter;
    private RcsNotificationList mNotifications;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.rcs_notification_list_activity);

        initViews();

        ActionBar actionBar = getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);

        IntentFilter filter = new IntentFilter();
        filter.addAction(Actions.GroupChatAction.ACTION_GROUP_CHAT_MANAGE_NOTIFY);
        filter.addAction(Actions.GroupChatAction.ACTION_GROUP_CHAT_INVITE);
        filter.addAction("com.suntek.mway.rcs.ACTION_UI_JOIN_GROUP_INVITE_TIMEOUT");
        registerReceiver(mRcsServiceCallbackReceiver, filter);

        mNotifications.addListener(onChangeListener);
        RcsNotifyUtil.cancelNotif();
    }

    @Override
    protected void onResume() {
        super.onPause();
        RcsNotifyUtil.cancelNotif();
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(mRcsServiceCallbackReceiver);
        mNotifications.removeListener(onChangeListener);
        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Do findViewById, etc.
     */
    private void initViews() {
        mNotifyListView = (ListView) findViewById(android.R.id.list);
        mNotifyListView.setEmptyView(findViewById(R.id.empty));
        mNotifications = RcsNotificationList.getInstance();
        mAdapter = new RcsNotificationListAdapter(this, mNotifications);
        mNotifyListView.setAdapter(mAdapter);

        ((TextView) mNotifyListView.getEmptyView()).setText(R.string.no_notifications);
    }

    private BroadcastReceiver mRcsServiceCallbackReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if ("com.suntek.mway.rcs.ACTION_UI_JOIN_GROUP_INVITE_TIMEOUT".equals(action)) {
                Toast.makeText(RcsNotificationListActivity.this,
                        R.string.group_chat_invite_timeout, Toast.LENGTH_LONG).show();
            }
        }
    };

    private RcsNotificationList.OnChangedListener onChangeListener =
            new RcsNotificationList.OnChangedListener() {
        @Override
        public void onChanged() {
            mAdapter.notifyDataSetChanged();
            RcsNotifyUtil.cancelNotif();
        }
    };
}
