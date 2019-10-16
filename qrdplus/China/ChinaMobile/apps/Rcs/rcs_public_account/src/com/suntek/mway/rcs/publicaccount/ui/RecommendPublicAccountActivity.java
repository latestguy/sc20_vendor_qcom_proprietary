/*
 * Copyright (c) 2015 pci-suntektech Technologies, Inc.  All Rights Reserved.
 * pci-suntektech Technologies Proprietary and Confidential.
 */

package com.suntek.mway.rcs.publicaccount.ui;

import java.util.ArrayList;
import java.util.List;
import android.app.ActionBar;
import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;

import com.suntek.mway.rcs.client.aidl.plugin.entity.pubacct.MenuInfoMode;
import com.suntek.mway.rcs.client.aidl.plugin.entity.pubacct.MsgContent;
import com.suntek.mway.rcs.client.aidl.plugin.entity.pubacct.PublicAccountConstant;
import com.suntek.mway.rcs.client.aidl.plugin.entity.pubacct.PublicAccounts;
import com.suntek.mway.rcs.client.aidl.plugin.entity.pubacct.PublicAccountsDetail;
import com.suntek.mway.rcs.client.api.exception.ServiceDisconnectedException;
import com.suntek.mway.rcs.client.api.publicaccount.PublicAccountApi;
import com.suntek.mway.rcs.client.api.publicaccount.PublicAccountCallback;
import com.suntek.mway.rcs.publicaccount.R;
import com.suntek.mway.rcs.publicaccount.ui.adapter.RecommendListAdapter;
import com.suntek.mway.rcs.nativeui.utils.RcsContactUtils;

public class RecommendPublicAccountActivity extends Activity implements OnItemClickListener {
    public static final String TAG = "RecommendPublicAccountActivity";

    private ListView mListView;
    private View mEmptyView;
    private List<PublicAccounts> mList = new ArrayList<PublicAccounts>();
    private RecommendListAdapter mAdapter;
    private static final int PAGE_SIZE = 10;

    private PublicAccountCallback mCallback = new PublicAccountCallback() {

        @Override
        public void respGetPreMessage(boolean result, List<MsgContent> msgContent)
                throws RemoteException {
        }

        @Override
        public void respGetPublicMenuInfo(boolean result, MenuInfoMode menuInfoMode)
                throws RemoteException {
        }

        @Override
        public void respGetUserSubscribePublicList(boolean result, List<PublicAccounts> pubAcctList)
                throws RemoteException {
        }

        @Override
        public void respGetPublicDetail(boolean result, PublicAccountsDetail accountDetail)
                throws RemoteException {
        }

        @Override
        public void respGetPublicList(boolean result, List<PublicAccounts> accountList)
                throws RemoteException {
        }

        @Override
        public void respAddSubscribeAccount(boolean result, PublicAccounts account)
                throws RemoteException {
        }

        @Override
        public void respCancelSubscribeAccount(boolean result, PublicAccounts account)
                throws RemoteException {
        }

        @Override
        public void respComplainPublicAccount(boolean result, PublicAccounts account)
                throws RemoteException {
        }

        @Override
        public void respGetPublicRecommend(boolean result, List<PublicAccounts> accountList)
                throws RemoteException {
        }

        @Override
        public void respSetAcceptStatus(boolean result, String uuid) throws RemoteException {
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_publicaccount_recommend_list);
        initActionBar();
        initView();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        RcsContactUtils.unregisterPublicAccountCallback(mCallback);
    }

    private void initActionBar() {
        ActionBar actionBar = getActionBar();
        actionBar.setDisplayUseLogoEnabled(false);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayShowHomeEnabled(false);
        Drawable myDrawable = getResources().getDrawable(
                R.color.public_account_bar_background_color);
        actionBar.setBackgroundDrawable(myDrawable);
    }

    private void initView() {
        mListView = (ListView) findViewById(R.id.recom_list);
        mEmptyView = findViewById(R.id.empty);
        mAdapter = new RecommendListAdapter(this, mList);
        mListView.setAdapter(mAdapter);
        mListView.setOnItemClickListener(this);
    }

    private void getRecommendPublicAccounts(int page) {
        try {
            PublicAccountApi.getInstance().getRecommendPublic(1, PAGE_SIZE, page,null);
        } catch (ServiceDisconnectedException e) {
            e.printStackTrace();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case android.R.id.home:
            finish();
            return true;
        }
        return false;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position,
            long id) {
    }
}
