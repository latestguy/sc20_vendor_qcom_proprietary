/*
 * Copyright (c) 2014-2015 pci-suntektech Technologies, Inc.  All Rights Reserved.
 * pci-suntektech Technologies Proprietary and Confidential.
 */

package com.suntek.mway.rcs.publicaccount.ui;

import com.suntek.mway.rcs.publicaccount.R;
import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;

import com.suntek.mway.rcs.client.aidl.plugin.entity.pubacct.PublicAccounts;
import com.suntek.mway.rcs.publicaccount.data.PublicConversation;
import com.suntek.mway.rcs.publicaccount.ui.adapter.ConversationListAdapter;
import com.suntek.mway.rcs.publicaccount.util.AsynImageLoader;
import com.suntek.mway.rcs.publicaccount.util.PublicAccountUtils;
import com.suntek.mway.rcs.publicaccount.receiver.RcsNotifyManager;
import com.suntek.rcs.ui.common.utils.ImageLoader;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ConversationListActivity extends Activity implements OnItemClickListener {

    private ListView mListView;

    private ConversationListAdapter mConversationListAdapter;

    private ArrayList<PublicConversation> mSessionList = new ArrayList<PublicConversation>();

    private ImageLoader mPublicImageLoader;

    private View mEmptyView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.conversation_list_view);
        mPublicImageLoader = new ImageLoader(this);
        initActionBar();
        initView();
        listenNotify();
        getDatas();
    }

    private void initActionBar(){
        ActionBar mActionBar = getActionBar();
        mActionBar.setDisplayHomeAsUpEnabled(true);
        mActionBar.setTitle(R.string.public_account_conversation_list);
        mActionBar.setIcon(R.drawable.logo_icon);
    }

    private void initView() {
        this.mListView = (ListView)findViewById(R.id.conv_list);
        this.mEmptyView = findViewById(R.id.empty);
        this.mConversationListAdapter = new ConversationListAdapter(this, mPublicImageLoader);
        this.mListView.setAdapter(mConversationListAdapter);
        this.mListView.setOnItemClickListener(this);
    }

    private void listenNotify() {
        registerTimeTickRecevier();
    }

    private void registerTimeTickRecevier() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_TIME_TICK);
        registerReceiver(timeTickReceiver, filter);
    }

    private BroadcastReceiver timeTickReceiver = new BroadcastReceiver() {
        @SuppressLint("SimpleDateFormat")
        @Override
        public void onReceive(Context context, Intent intent) {
            if (mConversationListAdapter != null) {
                String[] day = mConversationListAdapter.getToday();
                Date date = new Date();
                SimpleDateFormat dateFormat = new SimpleDateFormat(
                        context.getString(R.string.message_list_adapter_yyyy_mm_dd_));
                String[] newDay = dateFormat.format(date).split(" ");
                if (!day[0].equals(newDay[0]) || !day[1].equals(newDay[1])) {
                    mConversationListAdapter.setToday(newDay);
                }
            }
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        getDatas();
        RcsNotifyManager.getInstance().cancelAllMessageNotif();
        mEmptyView.setVisibility(View.GONE);
    };

    private void getDatas(){
        new LoadSessionTask().execute();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    class LoadSessionTask extends AsyncTask<Void, Void, List<PublicConversation>> {

        @Override
        protected List<PublicConversation> doInBackground(Void... params) {

            return PublicAccountUtils.getAllPublicCon(ConversationListActivity.this);
        }

        @Override
        protected void onPostExecute(List<PublicConversation> result) {
            super.onPostExecute(result);
            if (result != null && result.size() > 0) {
                mSessionList.clear();
                mSessionList.addAll(result);
                mConversationListAdapter.setDatas(mSessionList);
            } else {
                mEmptyView.setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(timeTickReceiver);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        PublicConversation sessionModel = mConversationListAdapter.getItem(position);
        if (sessionModel == null)
            return;
        PublicAccounts publicAccounts = sessionModel.getPublicAccount();
        if (publicAccounts == null)
            return;
        Intent intent = new Intent(ConversationListActivity.this, PAConversationActivity.class);
        intent.putExtra("PublicAccountUuid", publicAccounts.getPaUuid());
        intent.putExtra("PublicAccountSipUri", publicAccounts.getSipUri());
        intent.putExtra("publicCOnversation", sessionModel);
        startActivity(intent);
    }

}
