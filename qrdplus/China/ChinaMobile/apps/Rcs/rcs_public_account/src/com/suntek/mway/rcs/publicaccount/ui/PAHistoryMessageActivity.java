/*
 * Copyright (c) 2014-2015 pci-suntektech Technologies, Inc.  All Rights Reserved.
 * pci-suntektech Technologies Proprietary and Confidential.
 */

package com.suntek.mway.rcs.publicaccount.ui;

import android.app.ActionBar;
import android.app.Activity;
import android.os.Bundle;
import android.os.RemoteException;
import android.text.TextUtils;
import android.view.MenuItem;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import com.suntek.mway.rcs.client.aidl.plugin.entity.pubacct.MsgContent;
import com.suntek.mway.rcs.client.api.exception.ServiceDisconnectedException;
import com.suntek.mway.rcs.client.api.publicaccount.PublicAccountApi;
import com.suntek.mway.rcs.client.api.publicaccount.PublicAccountCallback;
import com.suntek.mway.rcs.publicaccount.R;
import com.suntek.mway.rcs.publicaccount.ui.adapter.ConversationHistoryMsgAdapter;
import com.suntek.mway.rcs.publicaccount.ui.widget.HistoryLoadListView;
import com.suntek.mway.rcs.publicaccount.ui.widget.HistoryLoadListView.OnLoadListener;
import com.suntek.mway.rcs.publicaccount.util.CommonUtil;
import com.suntek.mway.rcs.publicaccount.util.PublicAccountUtils;

public class PAHistoryMessageActivity extends Activity implements OnLoadListener {

    private ActionBar mActionBar;
    private HistoryLoadListView mListView;
    private ConversationHistoryMsgAdapter mAdapter;
    private String mPublicAccountUuid;
    private long mServiceThreadId;
    private List<MsgContent> mList = new ArrayList<MsgContent>();
    private static final int PAGE_SIZE = 10;
    private int mPage = 1;
    private int mPrePage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getIntentExtras();
        setContentView(R.layout.conversation_chat_history_view);
        initActionBar();
        initView();
        getHistoryMsgs(mPage = 1);
    }

    private void getIntentExtras() {
        mPublicAccountUuid = getIntent().getStringExtra("PublicAccountUuid");
        if (TextUtils.isEmpty(mPublicAccountUuid)) {
            errorParam();
        }
        mServiceThreadId = getIntent().getLongExtra("ThreadId", -1);
        if (mServiceThreadId == -1) {
            getServiceThreadId();
        }
    }

    private void getHistoryMsgs(final int page) {
        new Thread() {
            public void run() {
                try {
                    if (page == 1) {
                        Thread.sleep(500);
                    }
                    mPrePage = mPage;
                    PublicAccountApi.getInstance().getPreMessage(mPublicAccountUuid,
                            CommonUtil.getTimeStamp(System.currentTimeMillis()), 1, PAGE_SIZE, page, callback);
                } catch (ServiceDisconnectedException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            };
        }.start();
    }

    private void initActionBar() {
        mActionBar = getActionBar();
        mActionBar.setDisplayHomeAsUpEnabled(true);
        mActionBar.setDisplayUseLogoEnabled(false);
        mActionBar.setIcon(R.drawable.public_account);
        mActionBar.setTitle(R.string.public_account_history_message_view);
    }

    private void initView() {
        mListView = (HistoryLoadListView)findViewById(R.id.list_history_msg);
        mAdapter = new ConversationHistoryMsgAdapter(this, mList);
        mAdapter.setPaUuid(mPublicAccountUuid);
        mListView.setAdapter(mAdapter);
        mListView.setOnLoadListener(this);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
    private void errorParam() {
        Toast.makeText(this, R.string.message_uuid_empty, Toast.LENGTH_SHORT).show();
        finish();
    }

    private void getServiceThreadId() {
        mServiceThreadId = PublicAccountUtils.getTheadIdByNumber(PAHistoryMessageActivity.this,
                mPublicAccountUuid);
    }

    private PublicAccountCallback callback = new PublicAccountCallback() {

        @Override
        public void respSetAcceptStatus(boolean arg0, String arg1) throws RemoteException {}

        @Override
        public void respGetPreMessage(boolean arg0, List<MsgContent> arg1) throws RemoteException {
            final List<MsgContent> tmpList = arg1;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    completeLoad();
                    if (tmpList != null) {
                        mAdapter.addList(tmpList);
                        if (tmpList.size() == PAGE_SIZE) {
                            mPage ++;
                        } else {
                            fullLoad();
                        }
                    }
                }
            });

        }
    };

    @Override
    public void onLoad() {
        if (mPrePage != mPage) {
            getHistoryMsgs(mPage);
        }else {
            completeLoad();
            fullLoad();
        }
    }

    private void completeLoad() {
        if (mListView != null) {
            mListView.onLoadComplete();
        }
    }

    private void fullLoad() {
        if (mListView != null) {
            mListView.onLoadFull();
        }
    }
}
