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
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.suntek.mway.rcs.publicaccount.R;
import com.suntek.mway.rcs.publicaccount.ui.adapter.AccountListAdapter;
import com.suntek.mway.rcs.nativeui.utils.ImageUtils;
import com.suntek.mway.rcs.nativeui.utils.RcsContactUtils;
import com.suntek.mway.rcs.client.aidl.plugin.entity.pubacct.MenuInfo;
import com.suntek.mway.rcs.client.aidl.plugin.entity.pubacct.MenuInfoMode;
import com.suntek.mway.rcs.client.aidl.plugin.entity.pubacct.MsgContent;
import com.suntek.mway.rcs.client.aidl.plugin.entity.pubacct.PublicAccountConstant;
import com.suntek.mway.rcs.client.aidl.plugin.entity.pubacct.PublicAccounts;
import com.suntek.mway.rcs.client.aidl.plugin.entity.pubacct.PublicAccountsDetail;
import com.suntek.mway.rcs.client.api.exception.ServiceDisconnectedException;
import com.suntek.mway.rcs.client.api.message.MessageApi;
import com.suntek.mway.rcs.client.api.publicaccount.PublicAccountApi;
import com.suntek.mway.rcs.client.api.publicaccount.PublicAccountCallback;
import com.suntek.rcs.ui.common.utils.ImageLoader;

public class PublicAccountActivity extends Activity {
    public static final String TAG = "PublicAccountActivity";
    private static final int REQUEST_PUBLIC_ACCOUNT_DETAIL = 1;
    private ListView mListView;
    private TextView mTextEmpty;
    private boolean mFollowStateChanged = false;
    private OnItemClickListener mItemListener;
    private boolean mForward;

    public static final int REQUEST_CODE_START_CONVERSATION_ACTIVITY = 0;

    public static final String CONVERSATION_ACTIVITY_ACTION
               = "com.suntek.mway.rcs.publicaccount.ACTION_LUNCHER_RCS_PULBIC_ACCOUT_CONVERSATION";

    private PublicAccountCallback mCallback = new PublicAccountCallback() {

        @Override
        public void respGetUserSubscribePublicList(final boolean result,
                final List<PublicAccounts> publicAccountList) throws RemoteException {
            runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    if (publicAccountList != null && publicAccountList.size() > 0) {
                        mListView.setAdapter(new AccountListAdapter(PublicAccountActivity.this,
                                publicAccountList));
                        mListView.setOnItemClickListener(mItemListener);
                        Toast.makeText(
                                PublicAccountActivity.this,
                                PublicAccountActivity.this.getResources().getString(
                                        R.string.sync_success), Toast.LENGTH_SHORT).show();
                    } else {
                        boolean empty = (mListView.getAdapter() == null)
                                || (mListView.getAdapter() != null && mListView.getAdapter()
                                        .getCount() == 0);
                        if (result) {
                            if (empty) {
                                Toast.makeText(
                                        PublicAccountActivity.this,
                                        PublicAccountActivity.this.getResources().getString(
                                                R.string.success_but_empty), Toast.LENGTH_SHORT)
                                        .show();
                            } else {
                                Toast.makeText(
                                        PublicAccountActivity.this,
                                        PublicAccountActivity.this.getResources().getString(
                                                R.string.sync_success), Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            if (empty) {
                                Toast.makeText(PublicAccountActivity.this,
                                        R.string.failure_and_empty, Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                }
            });

        }

        @Override
        public void respGetPublicList(boolean arg0, List<PublicAccounts> arg1)
                throws RemoteException {
        }

        @Override
        public void respGetPublicDetail(boolean arg0, PublicAccountsDetail arg1)
                throws RemoteException {

        }

        @Override
        public void respComplainPublicAccount(boolean arg0, PublicAccounts arg1)
                throws RemoteException {

        }

        @Override
        public void respSetAcceptStatus(boolean arg0, String arg1) throws RemoteException {

        }

        @Override
        public void respGetPreMessage(boolean result, List<MsgContent> msgContent)
                throws RemoteException {
        }

        @Override
        public void respGetPublicMenuInfo(boolean result, MenuInfoMode menuInfoMode)
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
        public void respGetPublicRecommend(boolean result, List<PublicAccounts> accountList)
                throws RemoteException {
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_main);
        ActionBar actionBar = getActionBar();
        actionBar.setDisplayUseLogoEnabled(false);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayShowHomeEnabled(false);
        getIntentExtra();
        Drawable myDrawable = getResources().getDrawable(
                R.color.public_account_bar_background_color);
        actionBar.setBackgroundDrawable(myDrawable);
        mTextEmpty = (TextView)findViewById(R.id.text_empty);
        mListView = (ListView)findViewById(R.id.list_accounts);
        mItemListener = new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                AccountListAdapter adapter = (AccountListAdapter)mListView.getAdapter();
                PublicAccounts account = adapter.getItem(position);
                Intent intent = new Intent(CONVERSATION_ACTIVITY_ACTION);
                if (account != null && !mForward) {
                    intent.putExtra("PublicAccountUuid", account.getPaUuid());
                    intent.putExtra("ThreadId", getThreadIdById(account.getPaUuid()));
                    startActivityForResult(intent, REQUEST_CODE_START_CONVERSATION_ACTIVITY);
                }
                if (mForward) {
                    Intent it = new Intent();
                    it.putExtra("selectPublicId", account.getPaUuid());
                    setResult(RESULT_OK, it);
                    finish();
                }
            }
        };
        mTextEmpty.setVisibility(View.GONE);
        getUserSubscribePublicListCache();
        if (mListView != null) {
            mListView.setAdapter(null);
            mListView.setOnItemClickListener(null);
        }
        getUserSubscribePublicList();
    }

    private void getIntentExtra(){
        if (getIntent().hasExtra("forward")) {
            mForward = getIntent().getBooleanExtra("forward", false);
        }
    }

    private long getThreadIdById(String id) {
        long threadId = -1;
        try {
            threadId = MessageApi.getInstance().getThreadId(Arrays.asList(new String[] {
                id.substring(0, id.indexOf("@"))
            }));
        } catch (ServiceDisconnectedException e) {
            e.printStackTrace();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return threadId;
    }

    private void getUserSubscribePublicListCache() {
        final Handler handler = new Handler();
        Thread t = new Thread() {
            @Override
            public void run() {
                if (!RcsContactUtils.isRcsConnection()) {
                    RcsContactUtils.sleep(500);
                    RcsContactUtils.setRcsConnectionState(true);
                }
                Cursor c = getContentResolver().query(
                        Uri.parse(RcsContactUtils.PUB_ACCOUNT_URI),
                        new String[] {
                                RcsContactUtils.PUB_ACCOUNT_PA_UUID,
                                RcsContactUtils.PUB_ACCOUNT_NAME,
                                RcsContactUtils.PUB_ACCOUNT_RECOMMEND_LEVEL,
                                RcsContactUtils.PUB_ACCOUNT_LOGO,
                                RcsContactUtils.PUB_ACCOUNT_SIP_URI,
                        }, RcsContactUtils.PUB_ACCOUNT_FOLLOWED + " = 1 ", null,
                        RcsContactUtils.PUB_ACCOUNT_FOLLOWED_TIME + " DESC");
                final List<PublicAccounts> publicAccountList = new ArrayList<PublicAccounts>();
                try {
                    if (c != null && c.moveToFirst()) {
                        do {
                            String paUuid = c.getString(0);
                            String name = c.getString(1);
                            int recommendLevel = c.getInt(2);
                            String logo = c.getString(3);
                            String sipUri = c.getString(4);
                            PublicAccounts publicAccount = new PublicAccounts();
                            publicAccount.setLogo(logo);
                            publicAccount.setName(name);
                            publicAccount.setPaUuid(paUuid);
                            publicAccount.setRecommendLevel(recommendLevel);
                            publicAccount.setSipUri(sipUri);
                            publicAccount.setSubscribestatus(1);
                            publicAccountList.add(publicAccount);
                        } while (c.moveToNext());
                    }
                } finally {
                    if (null != c) {
                        c.close();
                    }
                }
                if (publicAccountList != null && publicAccountList.size() > 0) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            mListView.setAdapter(new AccountListAdapter(PublicAccountActivity.this,
                                    publicAccountList));
                            mListView.setOnItemClickListener(mItemListener);
                        }
                    });
                } else {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            mListView.setAdapter(null);
                            mListView.setOnItemClickListener(null);
                        }
                    });
                }
            }
        };
        t.start();
    }

    private void getUserSubscribePublicList() {
        try {
            PublicAccountApi.getInstance().getUserSubscribePublicList(mCallback);
        } catch (ServiceDisconnectedException e) {
            Toast.makeText(this,
                    this.getResources().getString(R.string.rcs_service_is_not_available),
                    Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        RcsContactUtils.unregisterPublicAccountCallback(mCallback);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case android.R.id.home:
            finish();
            return true;
        case R.id.action_search:
            startActivityForResult(
                    new Intent(this, SearchAccountActivity.class), 0);
            return true;
        case R.id.action_refresh:
            if (mListView != null) {
                mListView.setAdapter(null);
                mListView.setOnItemClickListener(null);
            }
            getUserSubscribePublicList();
            return true;
        }
        return false;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.account_list, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (prefs.getBoolean(RcsContactUtils.PREF_FOLLOW_STATE_CHANGED, false)) {
            getUserSubscribePublicListCache();
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean(RcsContactUtils.PREF_FOLLOW_STATE_CHANGED, false);
            editor.apply();
        }
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
            if (mAdapter != null) {
                Bitmap bitmap = mAdapter.getBitmapViaUrl(imageUrl);
                if (bitmap == null) {
                    try {
                        URL myFileUrl = new URL(imageUrl);
                        HttpURLConnection httpURLconnection = (HttpURLConnection)myFileUrl
                                .openConnection();
                        httpURLconnection.setRequestMethod("GET");
                        httpURLconnection.setReadTimeout(6 * 1000);
                        InputStream is = null;
                        if (httpURLconnection.getResponseCode() == 200) {
                            is = httpURLconnection.getInputStream();
                            bitmap = BitmapFactory.decodeStream(is);
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
        }
    }

}
