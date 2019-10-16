/*
 * Copyright (c) 2015 pci-suntektech Technologies, Inc.  All Rights Reserved.
 * pci-suntektech Technologies Proprietary and Confidential.
 */

package com.suntek.mway.rcs.publicaccount.ui;

import com.suntek.mway.rcs.publicaccount.R;
import com.suntek.mway.rcs.nativeui.utils.RcsContactUtils;
import com.suntek.mway.rcs.client.aidl.plugin.entity.pubacct.MenuInfo;
import com.suntek.mway.rcs.client.aidl.plugin.entity.pubacct.MenuInfoMode;
import com.suntek.mway.rcs.client.aidl.plugin.entity.pubacct.MsgContent;
import com.suntek.mway.rcs.client.aidl.plugin.entity.pubacct.PublicAccountConstant;
import com.suntek.mway.rcs.client.aidl.plugin.entity.pubacct.PublicAccountReqEntity;
import com.suntek.mway.rcs.client.aidl.plugin.entity.pubacct.PublicAccounts;
import com.suntek.mway.rcs.client.aidl.plugin.entity.pubacct.PublicAccountsDetail;
import com.suntek.mway.rcs.client.api.exception.ServiceDisconnectedException;
import com.suntek.mway.rcs.client.api.message.MessageApi;
import com.suntek.mway.rcs.client.api.publicaccount.PublicAccountApi;
import com.suntek.mway.rcs.client.api.publicaccount.PublicAccountCallback;
import com.suntek.rcs.ui.common.utils.ImageLoader;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
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
import android.text.method.ScrollingMovementMethod;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class PublicDetailActivity extends Activity implements OnClickListener {

    private ImageView mImagePhoto;

    private TextView mTextName, mTextCompany, mTextUuid, mTextIntro, mTextConversation,
            mTextHistoryMsg, mTextEmptyMsg, mTextComplain, mTextStatus;

    private View mConverDivider;

    private Button mBtnFollow;

    private ProgressDialog mDlg;

    private CheckBox mCbReceiveMsg;

    private String mId;

    private PublicAccountsDetail mAccount;

    private static final HashMap<String, SoftReference<PublicAccountsDetail>> mDetailCache
            = new HashMap<String, SoftReference<PublicAccountsDetail>>();

    private int mLastReceiveStatus;

    public static final String CONVERSATION_ACTIVITY_ACTION
               = "com.suntek.mway.rcs.publicaccount.ACTION_LUNCHER_RCS_PULBIC_ACCOUT_CONVERSATION";

    private boolean mFromPublicAccount;

    public static final int REQUEST_CODE_START_CONVERSATION_ACTIVITY = 0;

    private static final String PUB_ACCOUNT_UUID = "PublicAccountUuid";

    private static final String PUB_ACCOUNT_THREADID = "ThreadId";

    private PublicAccountCallback mCallback = new PublicAccountCallback() {

        @Override
        public void respGetUserSubscribePublicList(boolean arg0, List<PublicAccounts> arg1)
                throws RemoteException {
        }

        @Override
        public void respGetPublicMenuInfo(final boolean result, final MenuInfoMode menuInfoMode)
                throws RemoteException {
        }

        @Override
        public void respGetPublicList(boolean arg0, List<PublicAccounts> arg1)
                throws RemoteException {

        }

        @Override
        public void respGetPublicDetail(final boolean result,
                final PublicAccountsDetail accountDetail) throws RemoteException {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (result && accountDetail != null) {
                        mAccount = accountDetail;
                        if (TextUtils.equals(mAccount.getPaUuid(), mId)) {
                            initViewByAccount();
                            mDetailCache
                                    .put(mId, new SoftReference<PublicAccountsDetail>(mAccount));
                        }
                    } else {
                        Toast.makeText(PublicDetailActivity.this,
                                getString(R.string.get_public_detail_fail), Toast.LENGTH_LONG)
                                .show();
                        showFailView();
                    }
                }
            });

        }

        @Override
        public void respComplainPublicAccount(final boolean arg0, PublicAccounts arg1)
                throws RemoteException {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (arg0) {
                        Toast.makeText(PublicDetailActivity.this, R.string.complain_success,
                                Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(PublicDetailActivity.this, R.string.complain_fail,
                                Toast.LENGTH_LONG).show();
                    }
                }
            });
        }

        @Override
        public void respAddSubscribeAccount(final boolean result, PublicAccounts ac)
                throws RemoteException {
            if (result) {
                mAccount.setSubscribeStatus(1);
            }
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mBtnFollow.setClickable(true);
                    if (result) {
                        mBtnFollow.setText(R.string.unfollow);
                        try {
                            PublicAccountApi.getInstance().getPublicDetail(mId, mCallback);
                        } catch (ServiceDisconnectedException e) {
                            e.printStackTrace();
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                        mTextConversation.setVisibility(View.VISIBLE);
                        mConverDivider.setVisibility(View.VISIBLE);
                        SharedPreferences prefs = PreferenceManager
                                .getDefaultSharedPreferences(PublicDetailActivity.this);
                        SharedPreferences.Editor editor = prefs.edit();
                        editor.putBoolean(RcsContactUtils.PREF_FOLLOW_STATE_CHANGED, true);
                        editor.apply();

                        Intent intent = new Intent(CONVERSATION_ACTIVITY_ACTION);
                        intent.putExtra(PUB_ACCOUNT_UUID, mId);
                        intent.putExtra(PUB_ACCOUNT_THREADID, getThreadIdById(mId));
                        startActivityForResult(intent, REQUEST_CODE_START_CONVERSATION_ACTIVITY);
                    } else {
                        Toast.makeText(PublicDetailActivity.this, R.string.fail_and_try_again,
                                Toast.LENGTH_SHORT).show();
                        mBtnFollow.setText(R.string.follow);
                    }
                    mDlg.dismiss();
                }
            });

        }

        @Override
        public void respCancelSubscribeAccount(final boolean result, PublicAccounts ac)
                throws RemoteException {
            if (result) {
                mAccount.setSubscribeStatus(0);
            }
            runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    mBtnFollow.setClickable(true);
                    if (result) {
                        mBtnFollow.setText(R.string.follow);
                        try {
                            PublicAccountApi.getInstance().getPublicDetail(mId, mCallback);
                        } catch (ServiceDisconnectedException e) {
                            e.printStackTrace();
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                        mTextConversation.setVisibility(View.GONE);
                        mConverDivider.setVisibility(View.GONE);
                        SharedPreferences prefs = PreferenceManager
                                .getDefaultSharedPreferences(PublicDetailActivity.this);
                        SharedPreferences.Editor editor = prefs.edit();
                        editor.putBoolean(RcsContactUtils.PREF_FOLLOW_STATE_CHANGED, true);
                        editor.apply();
                    } else {
                        Toast.makeText(PublicDetailActivity.this, R.string.fail_and_try_again,
                                Toast.LENGTH_SHORT).show();
                        mBtnFollow.setText(R.string.unfollow);
                    }
                    mDlg.dismiss();
                }
            });
        }

        @Override
        public void respSetAcceptStatus(boolean arg0, String arg1)
                throws RemoteException {
            if (arg0) {
                mAccount.setAcceptstatus(mLastReceiveStatus
                        == PublicAccountConstant.ACCEPT_STATUS_ACCEPT ?
                        PublicAccountConstant.ACCEPT_STATUS_NOT
                        : PublicAccountConstant.ACCEPT_STATUS_ACCEPT);
            } else {
                mAccount.setAcceptstatus(mLastReceiveStatus);
            }
        }

        @Override
        public void respGetPreMessage(boolean result, List<MsgContent> msgContent)
                throws RemoteException {
        }

        @Override
        public void respGetPublicRecommend(boolean result, List<PublicAccounts> accountList)
                throws RemoteException {
        }
    };
    private ImageLoader mImageLoader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.public_account_detail);
        ActionBar actionBar = getActionBar();
        actionBar.setDisplayUseLogoEnabled(false);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayShowHomeEnabled(false);
        Drawable myDrawable = getResources().getDrawable(
                R.color.public_account_bar_background_color);
        actionBar.setBackgroundDrawable(myDrawable);
        mId = getIntent().getStringExtra("id");
        if (TextUtils.isEmpty(mId)) {
            Toast.makeText(this, R.string.public_account_id_null, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (getIntent().hasExtra("publicaccount")) {
            mFromPublicAccount = getIntent().getBooleanExtra("publicaccount", false);
        }

        mImagePhoto = (ImageView)findViewById(R.id.img_photo);
        mBtnFollow = (Button)findViewById(R.id.btn_follow);
        mTextName = (TextView)findViewById(R.id.text_name);
        mTextCompany = (TextView)findViewById(R.id.text_company);
        mTextStatus = (TextView)findViewById(R.id.text_status);
        mTextUuid = (TextView)findViewById(R.id.text_uuid);
        mTextIntro = (TextView)findViewById(R.id.intro_content);
        mConverDivider = findViewById(R.id.conver_divider);
        mTextConversation = (TextView)findViewById(R.id.conversation);
        mTextHistoryMsg = (TextView)findViewById(R.id.history_message);
        mTextEmptyMsg = (TextView)findViewById(R.id.message_empty);
        mTextComplain = (TextView)findViewById(R.id.complain);
        mCbReceiveMsg = (CheckBox)findViewById(R.id.cb_receive_msg);
        mCbReceiveMsg.setOnClickListener(this);
        mTextIntro.setMovementMethod(ScrollingMovementMethod.getInstance());
        final Handler handler = new Handler();
        if (mDetailCache.containsKey(mId)) {
            mAccount = mDetailCache.get(mId).get();
        }
        queryDetailInfo();
    }

    private void queryDetailInfo() {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... arg0) {
                Cursor c = getContentResolver().query(
                        Uri.parse(RcsContactUtils.PUB_ACCOUNT_URI),
                        new String[] {
                                RcsContactUtils.PUB_ACCOUNT_NAME,
                                RcsContactUtils.PUB_ACCOUNT_COMPANY,
                                RcsContactUtils.PUB_ACCOUNT_INTRO,
                                RcsContactUtils.PUB_ACCOUNT_TYPE,
                                RcsContactUtils.PUB_ACCOUNT_RECOMMEND_LEVEL,
                                RcsContactUtils.PUB_ACCOUNT_UPDATE_TIME,
                                RcsContactUtils.PUB_ACCOUNT_MENU_TYPE,
                                RcsContactUtils.PUB_ACCOUNT_MENU_TIMESTAMP,
                                RcsContactUtils.PUB_ACCOUNT_FOLLOWED,
                                RcsContactUtils.PUB_ACCOUNT_ACCEPT,
                                RcsContactUtils.PUB_ACCOUNT_ACTIVE_STATUS,
                                RcsContactUtils.PUB_ACCOUNT_TEL, RcsContactUtils.PUB_ACCOUNT_EMAIL,
                                RcsContactUtils.PUB_ACCOUNT_ZIP, RcsContactUtils.PUB_ACCOUNT_ADDR,
                                RcsContactUtils.PUB_ACCOUNT_FIELD,
                                RcsContactUtils.PUB_ACCOUNT_QR_CODE,
                                RcsContactUtils.PUB_ACCOUNT_LOGO,
                                RcsContactUtils.PUB_ACCOUNT_NUMBER,
                        }, RcsContactUtils.PUB_ACCOUNT_PA_UUID + " = " + mId, null, null);
                final List<PublicAccounts> publicAccountList = new ArrayList<PublicAccounts>();
                try {
                    if (c != null && c.moveToFirst()) {
                        mAccount = new PublicAccountsDetail();
                        String name = c.getString(0);
                        mAccount.setName(name);
                        String company = c.getString(1);
                        mAccount.setCompany(company);
                        String intro = c.getString(2);
                        mAccount.setIntro(intro);
                        String type = c.getString(3);
                        mAccount.setType(type);
                        int recommendLevel = c.getInt(4);
                        mAccount.setRecommendLevel(recommendLevel);
                        String updateTime = c.getString(5);
                        mAccount.setUpdateTime(updateTime);
                        int menuType = c.getInt(6);
                        mAccount.setMenuType(menuType);
                        String menuTimeStamp = c.getString(7);
                        mAccount.setMenuTimestamp(menuTimeStamp);
                        int followed = c.getInt(8);
                        mAccount.setSubscribeStatus(followed);
                        int accept = c.getInt(9);
                        mAccount.setAcceptstatus(accept);
                        int activeStatus = c.getInt(10);
                        mAccount.setActiveStatus(activeStatus);
                        String tel = c.getString(11);
                        mAccount.setTel(tel);
                        String email = c.getString(12);
                        mAccount.setEmail(email);
                        String zip = c.getString(13);
                        mAccount.setZip(zip);
                        String addr = c.getString(14);
                        mAccount.setAddr(addr);
                        String field = c.getString(15);
                        mAccount.setField(field);
                        String qrCode = c.getString(16);
                        mAccount.setQrCode(qrCode);
                        String logo = c.getString(17);
                        mAccount.setLogoUrl(logo);
                        String number = c.getString(18);
                        mAccount.setNumber(number);
                        mAccount.setPaUuid(mId);
                    }
                } finally {
                    if (null != c) {
                        c.close();
                    }
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void result) {
                try {
                    PublicAccountApi.getInstance().getPublicDetail(mId, mCallback);
                } catch (ServiceDisconnectedException e) {
                    Toast.makeText(
                            PublicDetailActivity.this,
                            PublicDetailActivity.this.getResources().getString(
                                    R.string.rcs_service_is_not_available), Toast.LENGTH_SHORT)
                            .show();
                    e.printStackTrace();
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }.execute();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                if (mFromPublicAccount) {
                    toPaConversation();
                }
                finish();
                return true;
        }
        return false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mImageLoader != null) {
            mImageLoader.destroy();
        }
        RcsContactUtils.unregisterPublicAccountCallback(mCallback);
    }

    private void initViewByAccount() {
        mTextName.setText(mAccount.getName());
        mTextCompany.setText(mAccount.getCompany());
        setPublicAccountStatus();
        setReceiveMsgStatus();
        mTextUuid.setText(mAccount.getNumber());
        mTextIntro.setText(mAccount.getIntro());
        if (mAccount.getSubscribeStatus() == 1) {
            mBtnFollow.setText(R.string.unfollow);
            mTextConversation.setVisibility(View.VISIBLE);
            mConverDivider.setVisibility(View.VISIBLE);
        } else {
            mBtnFollow.setText(R.string.follow);
            mTextConversation.setVisibility(View.GONE);
            mConverDivider.setVisibility(View.GONE);
        }
        String avatarUrl = getIntent().getStringExtra("avatar_url");
        mImageLoader = new ImageLoader(this);
        mImageLoader.load(mImagePhoto, avatarUrl, R.drawable.public_account_default_ic,
                R.drawable.public_account_default_ic);
    }

    private void setPublicAccountStatus() {
        int resId = R.string.rcs_public_account_status_normal;
        switch (mAccount.getActiveStatus()) {
            case PublicAccountConstant.ACCOUNT_STATUS_NORMAL:
                resId = R.string.rcs_public_account_status_normal;
                break;
            case PublicAccountConstant.ACCOUNT_STATUS_PAUSE:
                resId = R.string.rcs_public_account_status_pause;
                break;
            case PublicAccountConstant.ACCOUNT_STATUS_CLOSE:
                resId = R.string.rcs_public_account_status_closed;
                break;
            default:
                break;
        }
        String status = getResources().getString(resId);
        mTextStatus.setText(this.getResources().getString(R.string.rcs_public_account_status,
                status));
    }

    private void setReceiveMsgStatus() {
        if (mAccount.getAcceptstatus() == PublicAccountConstant.ACCEPT_STATUS_ACCEPT) {
            mCbReceiveMsg.setChecked(true);
        } else if (mAccount.getAcceptstatus() == PublicAccountConstant.ACCEPT_STATUS_NOT) {
            mCbReceiveMsg.setChecked(false);
        }
    }

    private void showFailView() {
        mTextName.setText(R.string.load_detail_fail);
    }

    private void follow() {
        mBtnFollow.setClickable(false);
        try {
            PublicAccountApi.getInstance().addSubscribe(mId, mCallback);
        } catch (ServiceDisconnectedException e) {
            e.printStackTrace();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private void unfollow() {
        mBtnFollow.setClickable(false);
        PublicAccountReqEntity entity = new PublicAccountReqEntity();
        entity.setPaUuid(mId);
        try {
            PublicAccountApi.getInstance().cancelSubscribe(mId, mCallback);
        } catch (ServiceDisconnectedException e) {
            e.printStackTrace();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private void acceptMessage() {
        final Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
            }
        });
        mDlg = ProgressDialog.show(this, getString(R.string.please_wait),
                getString(R.string.loading), true, true, new OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        thread.interrupt();
                    }
                });
        thread.start();
    }

    private void rejectMessage() {
        final Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
            }
        });
        mDlg = ProgressDialog.show(this, getString(R.string.please_wait),
                getString(R.string.loading), true, true, new OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        thread.interrupt();
                    }
                });
        thread.start();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_follow:
                if (mAccount == null) {
                    return;
                }
                mDlg = ProgressDialog.show(this, getString(R.string.please_wait), mAccount
                        .getSubscribeStatus() == 1 ? getString(R.string.unfollowing)
                        : getString(R.string.following));
                mDlg.setCanceledOnTouchOutside(true);
                mDlg.setOnCancelListener(new OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface arg0) {
                        mBtnFollow.setClickable(true);
                        Toast.makeText(
                                PublicDetailActivity.this,
                                PublicDetailActivity.this.getResources().getString(
                                        R.string.cancel_option), Toast.LENGTH_SHORT).show();
                    }
                });
                if (mAccount.getSubscribeStatus() == 1) {
                    unfollow();
                } else {
                    follow();
                }
                break;
            case R.id.conversation:
                Intent intent = new Intent(CONVERSATION_ACTIVITY_ACTION);
                intent.putExtra("PublicAccountUuid", mId);
                intent.putExtra("ThreadId", getThreadIdById(mId));
                startActivityForResult(intent, REQUEST_CODE_START_CONVERSATION_ACTIVITY);
                break;
            case R.id.history_message:
                Intent it = new Intent(
                        "com.suntek.mway.rcs.publicaccount.ACTION_LUNCHER_PAHISTORY_MESSAGE");
                it.putExtra("PublicAccountUuid", mId);
                it.putExtra("ThreadId", getThreadIdById(mId));
                startActivity(it);
                break;
            case R.id.message_empty:
                emptyMsg();
                break;
            case R.id.complain:
                showComplainDialog();
                break;
            case R.id.cb_receive_msg:
                mLastReceiveStatus = mAccount.getAcceptstatus();
                boolean isChecked = mCbReceiveMsg.isChecked();
                try {
                    if (isChecked) {
                        PublicAccountApi.getInstance().setAcceptStatus(mId,
                                PublicAccountConstant.ACCEPT_STATUS_ACCEPT, mCallback);
                    } else {
                        PublicAccountApi.getInstance().setAcceptStatus(mId,
                                PublicAccountConstant.ACCEPT_STATUS_NOT, mCallback);
                    }
                } catch (ServiceDisconnectedException e) {
                    e.printStackTrace();
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
                break;
            default:
                break;
        }

    }

    private void emptyMsg() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(true);
        builder.setMessage(R.string.rcs_confirm_empty_msgs);
        builder.setPositiveButton(R.string.rcs_confirm, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                try {
                    int success = MessageApi.getInstance().deleteMessageByThreadId(
                            getThreadIdById(mId));
                    Toast.makeText(
                            PublicDetailActivity.this,
                            (success == 0) ? R.string.rcs_empty_msg_fail
                                    : R.string.rcs_empty_msg_success, Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                } catch (ServiceDisconnectedException e) {
                    e.printStackTrace();
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        });
        builder.setNegativeButton(R.string.rcs_cancel, null);
        builder.show();
    }

    private void showComplainDialog() {

        final EditText reason = new EditText(this);
        new AlertDialog.Builder(this).setTitle(R.string.dialog_title_complain).setView(reason)
                .setPositiveButton(R.string.confirm, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface arg0, int arg1) {
                        String reasonStr = reason.getText().toString();
                        if (!TextUtils.isEmpty(reasonStr)) {
                            try {
                                PublicAccountApi.getInstance().complainPublic(mId, reasonStr, "",
                                        1, "", mCallback);
                            } catch (ServiceDisconnectedException e) {
                                e.printStackTrace();
                            } catch (RemoteException e) {
                                e.printStackTrace();
                            }
                        } else {
                            Toast.makeText(PublicDetailActivity.this, R.string.reason_is_empty,
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                }).setNegativeButton(R.string.cancel, null).show();

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

    @Override
    public void onBackPressed() {
        if (mFromPublicAccount) {
            toPaConversation();
        }
        super.onBackPressed();
    }

    private void toPaConversation() {
        Intent data = new Intent();
        data.putExtra("publicaccountdetail", mAccount);
        setResult(RESULT_OK, data);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_CODE_START_CONVERSATION_ACTIVITY:
                if (resultCode == RESULT_OK) {
                    boolean unfollow = data.getBooleanExtra("unfollow", false);
                    if (unfollow) {
                        mAccount = data.getParcelableExtra("publicaccountdetail");
                        mBtnFollow.setText(R.string.follow);
                        mTextConversation.setVisibility(View.GONE);
                        mConverDivider.setVisibility(View.GONE);
                    }
                }
                break;

            default:
                break;
        }
    }
}
