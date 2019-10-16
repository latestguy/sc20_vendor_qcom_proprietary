/*
 * Copyright (c) 2015 pci-suntektech Technologies, Inc.  All Rights Reserved.
 * pci-suntektech Technologies Proprietary and Confidential.
 */

package com.suntek.mway.rcs.publicaccount.ui;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import android.app.ActionBar;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.SearchView.OnQueryTextListener;
import android.widget.Toast;

import com.suntek.mway.rcs.client.aidl.plugin.entity.pubacct.MenuInfo;
import com.suntek.mway.rcs.client.aidl.plugin.entity.pubacct.MenuInfoMode;
import com.suntek.mway.rcs.client.aidl.plugin.entity.pubacct.MsgContent;
import com.suntek.mway.rcs.client.aidl.plugin.entity.pubacct.PublicAccounts;
import com.suntek.mway.rcs.client.aidl.plugin.entity.pubacct.PublicAccountsDetail;
import com.suntek.mway.rcs.client.api.basic.BasicApi;
import com.suntek.mway.rcs.client.api.exception.ServiceDisconnectedException;
import com.suntek.mway.rcs.client.api.publicaccount.PublicAccountApi;
import com.suntek.mway.rcs.client.api.publicaccount.PublicAccountCallback;
import com.suntek.mway.rcs.publicaccount.R;
import com.suntek.mway.rcs.publicaccount.ui.adapter.AccountListAdapter;
import com.suntek.mway.rcs.publicaccount.ui.widget.PublicAccountListView;
import com.suntek.mway.rcs.publicaccount.ui.widget.PublicAccountListView.OnLoadListener;
import com.suntek.mway.rcs.nativeui.utils.ImageUtils;
import com.suntek.mway.rcs.nativeui.utils.RcsContactUtils;

public class SearchAccountActivity extends Activity implements OnLoadListener {
    public static final String TAG = "SearchAccountActivity";

    private PublicAccountListView mListView;

    private ProgressDialog mDlg;

    private AccountListAdapter mAdapter;

    private List<PublicAccounts> mList = new ArrayList<PublicAccounts>();

    private OnItemClickListener mItemListener;

    private static final int PA_PAGE_SIZE = 20;

    private int mPage = 1;

    private int mPrePage;

    private String mText;

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
        public void respGetPublicList(boolean arg0, final List<PublicAccounts> list)
                throws RemoteException {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    completeLoad();
                    if (mPage == 1 && (list == null || list.size() <= 0)) {
                        mListView.setAdapter(null);
                        mListView.setOnItemClickListener(null);
                        Toast.makeText(SearchAccountActivity.this, R.string.rcs_search_no_data, 0)
                                .show();
                    } else {
                        mAdapter.addList(list);
                        if (list.size() == PA_PAGE_SIZE) {
                            mPage ++;
                        } else {
                            fullLoad();
                        }
                    }
                }
            });
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
        setContentView(R.layout.activity_search_account);
        ActionBar actionBar = getActionBar();
        actionBar.setDisplayUseLogoEnabled(false);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayShowHomeEnabled(false);
        Drawable myDrawable = getResources().getDrawable(
                R.color.public_account_bar_background_color);
        actionBar.setBackgroundDrawable(myDrawable);
        mListView = (PublicAccountListView)findViewById(R.id.list_accounts);
        mAdapter = new AccountListAdapter(SearchAccountActivity.this, mList);
        mListView.setAdapter(mAdapter);
        mListView.setOnLoadListener(this);
        mListView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                PublicAccounts account = mAdapter.getItem(position);
                Intent intent = new Intent(SearchAccountActivity.this, PublicDetailActivity.class);
                if (account.getLogo() != null) {
                    intent.putExtra("avatar_url", account.getLogo());
                }
                intent.putExtra("id", account.getPaUuid());
                intent.putExtra("name", account.getName());
                startActivityForResult(intent, 0);
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        RcsContactUtils.unregisterPublicAccountCallback(mCallback);
    }

    private void searchAccount(final String text) {
        mDlg = ProgressDialog.show(this, getString(R.string.please_wait),
                getString(R.string.searching));
        mDlg.setCancelable(true);
        mPage = 1;
        mAdapter.clearList();
        againLoad();
        getPublicAccount();
    }

    private void getPublicAccount() {
        new Thread() {
            public void run() {
                try {
                    mPrePage = mPage;
                    PublicAccountApi.getInstance().getPublicList(mText, PA_PAGE_SIZE, mPage, 1, mCallback);
                } catch (ServiceDisconnectedException e) {
                    showSearchFailedToast();
                    e.printStackTrace();
                } catch (RemoteException e) {
                    showSearchFailedToast();
                    e.printStackTrace();
                }
            };
        }.start();
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
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.search_account, menu);
        MenuItem searchItem = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView)searchItem.getActionView();
        searchView.setOnQueryTextListener(new OnQueryTextListener() {

            @Override
            public boolean onQueryTextChange(String input) {
                return false;
            }

            @Override
            public boolean onQueryTextSubmit(String input) {
                if (!TextUtils.isEmpty(input)) {
                    try {
                        if (BasicApi.getInstance().isOnline()) {
                            mText = input;
                            searchAccount(input);
                        } else {
                            Toast.makeText(SearchAccountActivity.this,
                                    R.string.rcs_service_is_not_online, Toast.LENGTH_SHORT).show();
                        }
                    } catch (ServiceDisconnectedException e) {
                        e.printStackTrace();
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
                return true;
            }
        });

        return super.onCreateOptionsMenu(menu);
    }

    private void showSearchFailedToast() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(SearchAccountActivity.this, R.string.rcs_search_fail, 0).show();
            }
        });
    }

    private class UpdateAccountPhotoTask extends AsyncTask<Void, Void, Void> {
        List<PublicAccounts> mPublicAccountList;

        AccountListAdapter mAdapter = (AccountListAdapter)mListView.getAdapter();

        UpdateAccountPhotoTask(List<PublicAccounts> publicAccountList) {
            mPublicAccountList = publicAccountList;
        }

        @Override
        protected Void doInBackground(Void... params) {
            if (mAdapter == null) {
                return null;
            }
            for (PublicAccounts account : mPublicAccountList) {
                String imageUrl = account.getLogo();
                getPhoto(imageUrl);
                this.publishProgress(params);
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(Void... progresses) {
            if (mAdapter != null) {
                mAdapter.notifyDataSetChanged();
            }
        }

        @Override
        protected void onPostExecute(Void result) {
            if (mAdapter != null) {
                mAdapter.notifyDataSetChanged();
            }
        }

        private void getPhoto(String imageUrl) {
            Bitmap bitmap = null;
            try {
                URL myFileUrl = new URL(imageUrl);
                HttpURLConnection httpURLconnection=(HttpURLConnection)myFileUrl.openConnection();
                httpURLconnection.setRequestMethod("GET");
                httpURLconnection.setReadTimeout(6 * 1000);
                InputStream is = null;
                if (httpURLconnection.getResponseCode() == 200) {
                    is = httpURLconnection.getInputStream();
                    bitmap = BitmapFactory.decodeStream(is);
                    if (bitmap == null) {
                        return;
                    }
                    Bitmap roundCornerBitMap = ImageUtils.createBitmapRoundCorner(bitmap,
                            bitmap.getHeight() / 2);
                    if (mAdapter != null) {
                        mAdapter.addPhotoMap(imageUrl, roundCornerBitMap);
                    }
                    is.close();
                    Log.i(TAG, "image download finished." + imageUrl);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onLoad() {
        if (mPrePage != mPage) {
            getPublicAccount();
        }else {
            completeLoad();
            fullLoad();
        }
    }

    private void completeLoad() {
        if (mDlg != null) {
            mDlg.dismiss();
            showFooter();
        }
        if (mListView != null) {
            mListView.onLoadComplete();
        }
    }

    private void fullLoad() {
        if (mListView != null) {
            mListView.onLoadFull();
        }
    }

    private void againLoad() {
        if (mListView != null) {
            mListView.onLoadAgain();
        }
    }

    private void showFooter() {
        if (mListView != null) {
            mListView.showFooter();
        }
    }
}
