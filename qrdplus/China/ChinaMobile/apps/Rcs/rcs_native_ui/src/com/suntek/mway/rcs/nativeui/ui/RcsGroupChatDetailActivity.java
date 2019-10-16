/*
 * Copyright (c) 2014-2015 pci-suntektech Technologies, Inc.  All Rights Reserved.
 * pci-suntektech Technologies Proprietary and Confidential.
 */

package com.suntek.mway.rcs.nativeui.ui;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.IntentFilter;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.RawContacts;
import android.provider.Settings;
import android.provider.Telephony.Sms;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.MotionEvent;
import android.view.View.OnTouchListener;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.QuickContactBadge;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.suntek.mway.rcs.client.aidl.constant.Constants.GroupChatConstants;
import com.suntek.mway.rcs.client.aidl.plugin.entity.profile.QRCardImg;
import com.suntek.mway.rcs.client.aidl.plugin.entity.profile.QRCardInfo;
import com.suntek.mway.rcs.client.aidl.plugin.entity.profile.Profile;
import com.suntek.mway.rcs.client.aidl.plugin.entity.profile.Avatar;
import com.suntek.mway.rcs.client.aidl.service.entity.GroupChat;
import com.suntek.mway.rcs.client.aidl.service.entity.GroupChatMember;
import com.suntek.mway.rcs.client.api.groupchat.GroupChatCallback;
import com.suntek.mway.rcs.client.api.message.MessageApi;
import com.suntek.mway.rcs.client.api.profile.ProfileListener;
import com.suntek.mway.rcs.client.api.profile.ProfileApi;
import com.suntek.mway.rcs.client.api.richscreen.RichScreenApi;
import com.suntek.mway.rcs.client.api.groupchat.GroupChatApi;
import com.suntek.mway.rcs.client.api.basic.BasicApi;
import com.suntek.mway.rcs.client.api.exception.ServiceDisconnectedException;
import com.suntek.mway.rcs.nativeui.R;
import com.suntek.mway.rcs.nativeui.model.GroupChatNotifyEvent;
import com.suntek.mway.rcs.nativeui.model.OnAliasUpdateEvent;
import com.suntek.mway.rcs.nativeui.model.OnMemberQuitEvent;
import com.suntek.mway.rcs.nativeui.model.OnReferErrorEvent;
import com.suntek.mway.rcs.nativeui.model.OnRemarkChangeEvent;
import com.suntek.mway.rcs.nativeui.model.OnSubjectChangeEvent;
import com.suntek.mway.rcs.nativeui.service.RcsConferenceListener;
import com.suntek.mway.rcs.nativeui.service.RcsConferenceListener.OnConferenceChangeListener;
import com.suntek.mway.rcs.nativeui.utils.EditTextInputFilter;
import com.suntek.mway.rcs.nativeui.utils.RcsContactUtils;
import com.suntek.rcs.ui.common.mms.GroupMemberPhotoCache;
import com.suntek.rcs.ui.common.RcsLog;

public class RcsGroupChatDetailActivity extends Activity implements OnConferenceChangeListener {

    private static final int ADD_CONTACT_TO_GROUP_CHAT = 1;
    private static final int ADD_GROUP_CHAT_BY_NUMBER = 0;
    private static final int ADD_GROUP_CHAT_BY_CONTACT = 1;
    private static final int LEAVE_GROUP_WITH_NOT_ASSIGN_CHARMEN = 0;
    private static final int LEAVE_GROUP_WITH_ASSIGN_CHARMEN = 1;
    private static final int RCS_NOT_AVAILABLE = 3;
    private static final int NO_GROUP_CHAT = 4;
    private static final int GROUP_CHAT_HAS_DISBAND = 5;
    private static final int GROUP_CHAT_INIT_DONE = 6;
    private static final int UPDATE_GROUP_MEMBER_AVATAR = 7;
    private static final int IMAGE_PIXEL = 120;
    private static final String INTENT_MULTI_PICK = "com.android.contacts.action.MULTI_PICK";
    private static final String FROM_PUBLICACCOUNT = "from_publicAccount";
    private static final String ENHANCE_SCREEN_APK_NAME = "com.cmdm.rcs";
    private ProgressDialog mProgressDialog;

    private TextView mUserCount;
    private TextView mSubjectView;
    private TextView mGroupIdView;
    private TextView mGroupCapasityView;
    private TextView mMyAliasView;
    private TextView mGroupRemarkView;
    private TextView mSMSGroupSends;
    private View mUpdateSubjectView;
    private View mChangeGroupChairmanView;
    private View mEnhanceScreenView;
    private View mSaveGroupToContacts;
    private View mSetMyAliasView;
    private View mSetGroupRemarkView;
    private View clearChatHistory;
    private Button mLeaveGroupChatButton;
    private TextView notifySettings;

    private GridView mUserListView;
    private GroupChat mGroupChat;
    private List<GroupChatMember> mGroupChatMemeberList;
    private String mProfilePhoneNumber = "";
    private RcsGroupChatMemberListAdapter mAdapter;

    private MessageApi mMessageApi;
    private RichScreenApi mRichScreenApi;
    private Resources mResources;
    private Drawable mChairmanDrawable;
    private long mGroupId;

    private Handler handler = new Handler(){
        public void handleMessage(android.os.Message msg){
            switch (msg.what) {
            case RCS_NOT_AVAILABLE:
                toast(R.string.rcs_service_is_not_available);
                break;
            case NO_GROUP_CHAT:
                toast(R.string.no_group_chat);
                break;
            case GROUP_CHAT_HAS_DISBAND:
                toast(R.string.group_chat_has_disband);
                break;
            case GROUP_CHAT_INIT_DONE:
                new UpdateGroupChatMemberAvatarTask(RcsGroupChatDetailActivity.this,
                        mGroupId, mGroupChatMemeberList, mAdapter).execute();
                break;
            case UPDATE_GROUP_MEMBER_AVATAR:
                RcsLog.d("call update function UPDATE_GROUP_MEMBER_AVATAR");
                HashMap<String, SoftReference<Bitmap>> imageCache =
                        (HashMap<String, SoftReference<Bitmap>>)msg.obj;
                if (imageCache != null && imageCache.size() > 0) {
                    Iterator<String> iter = imageCache.keySet().iterator();
                    while (iter.hasNext()) {
                        String key = iter.next();
                        SoftReference<Bitmap> rf = imageCache.get(key);
                        Bitmap bitmap = rf.get();
                        if (bitmap != null) {
                            RcsLog.d("UPDATE_GROUP_MEMBER_AVATAR key:"
                                    + key + "bitmap:" + bitmap.toString());
                            GroupMemberPhotoCache.getInstance().addBitmapCache(key, bitmap);
                        }
                    }
                }
                if (mAdapter != null) {
                    mAdapter.notifyDataSetChanged();
                }
                break;
            default:
                break;
            }
        }
    };

    public static void startByGroupId(Context context, long groupId) {
        Intent intent = new Intent(context, RcsGroupChatDetailActivity.class);
        intent.putExtra("groupId", groupId);
        context.startActivity(intent);
    }

    private BroadcastReceiver updatePhotoReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            ((RcsGroupChatMemberListAdapter)mUserListView.getAdapter()).notifyDataSetChanged();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.rcs_group_chat_detail_activity);

        registerReceiver(updatePhotoReceiver, new IntentFilter(
                RcsContactUtils.NOTIFY_CONTACT_PHOTO_CHANGE));
        Intent intent = getIntent();
        mGroupId = intent.getLongExtra("groupId", -1);

        if (mGroupId == -1) {
            toast(R.string.no_group_chat);
            finish();
            return;
        }
        mResources = getResources();
        mChairmanDrawable = getResources().getDrawable(R.drawable.chairman);
        ActionBar actionBar = getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        mUserCount = (TextView) findViewById(R.id.userCount);
        mSubjectView = (TextView) findViewById(R.id.subject);
        mGroupIdView = (TextView) findViewById(R.id.groupId);
        mGroupCapasityView = (TextView) findViewById(R.id.groupCapasity);
        mMyAliasView = (TextView) findViewById(R.id.my_alias);
        mUpdateSubjectView = findViewById(R.id.updateSubject);
        mSMSGroupSends = (TextView) findViewById(R.id.groupMemberSendSMS);
        mSetMyAliasView = findViewById(R.id.setMyAlias);
        mChangeGroupChairmanView = findViewById(R.id.changeGroupChairman);
        mLeaveGroupChatButton = (Button) findViewById(R.id.leaveGroupChat);
        mSetGroupRemarkView = findViewById(R.id.setGroupRemark);
        mGroupRemarkView = (TextView) findViewById(R.id.group_remark);
        clearChatHistory = findViewById(R.id.clear_chat_history);
        notifySettings = (TextView) findViewById(R.id.notify_settings);
        mUserListView = (GridView) findViewById(R.id.member_list_view);
        mEnhanceScreenView = findViewById(R.id.ehancedScreen);
        mSaveGroupToContacts = findViewById(R.id.add_group_to_contacts);
        if (!isEnhanceScreenInstalled()){
            mEnhanceScreenView.setVisibility(View.GONE);
        }
        RcsConferenceListener.getInstance().addListener(mGroupId, this);
        mAdapter = new RcsGroupChatMemberListAdapter(this, mGroupId);
        mUserListView.setAdapter(mAdapter);

        new Thread(new Runnable() {
            @Override
            public void run() {
                loadGroupChatDetail();
            }
        }).start();
    }

    private void loadGroupChatDetail() {
        mMessageApi = MessageApi.getInstance();
        mRichScreenApi = RichScreenApi.getInstance();
        Message msg = new Message();

        try {
            mGroupChat = GroupChatApi.getInstance().getGroupChatById(mGroupId);
            String userInfo = BasicApi.getInstance().getAccount();
            if (userInfo != null){
                mProfilePhoneNumber = userInfo;
            } else {
                msg.what = RCS_NOT_AVAILABLE;
                handler.sendMessage(msg);
            }
        } catch (ServiceDisconnectedException e) {
            msg.what = RCS_NOT_AVAILABLE;
            handler.sendMessage(msg);
            finish();
            return;
        } catch (RemoteException e) {
            e.printStackTrace();
            return;
        }
        if (mGroupChat == null) {
            msg.what = NO_GROUP_CHAT;
            handler.sendMessage(msg);
            finish();
            return;
        }
        if (mGroupChat.getStatus() == GroupChat.STATUS_TERMINATED) {
            msg.what = GROUP_CHAT_HAS_DISBAND;
            handler.sendMessage(msg);
            finish();
            return;
        }
        List<GroupChatMember> memberList = null;
        try {
            memberList = GroupChatApi.getInstance().getMembers(mGroupId);
        } catch (Exception e) {
            e.printStackTrace();
        }
        mGroupChatMemeberList = memberList;
        mAdapter.isAddBtnShow(true);
        mAdapter.inittUserPhoneNumber();
        boolean isDelBtnShow = isChairman(memberList);
        mAdapter.isDelBtnShow(isDelBtnShow);

        mAdapter.bind(memberList);
        initialize();
        msg.what = GROUP_CHAT_INIT_DONE;
        handler.sendMessage(msg);
    }


    @Override
    protected void onResume() {
        super.onResume();
        // when activity onresume update display view
        if (null != mAdapter) {
            try {
                mGroupChatMemeberList = GroupChatApi.getInstance().getMembers(mGroupId);
            } catch (ServiceDisconnectedException e) {
                e.printStackTrace();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            mAdapter.bind(mGroupChatMemeberList);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mGroupChat != null) {
            RcsConferenceListener.getInstance().removeListener(String.valueOf(mGroupChat.getId()),
                    this);
        }
        unregisterReceiver(updatePhotoReceiver);
    }

    private boolean isEnhanceScreenInstalled() {
        boolean installed = false;
        try {
            ApplicationInfo info = getPackageManager().getApplicationInfo(
                ENHANCE_SCREEN_APK_NAME, PackageManager.GET_PROVIDERS);
            installed = (info != null);
        } catch (NameNotFoundException e) {
        }
        RcsLog.i("Is Enhance Screen installed ? " + installed);
        return installed;
    }

    private void initialize() {
        mUserCount.setText(getString(R.string.group_user_count, mGroupChatMemeberList.size()));
        mSubjectView.setText(mGroupChat.getSubject());
        mGroupIdView.setText(String.valueOf(mGroupChat.getId()));
        mGroupCapasityView.setText(String.valueOf(mGroupChat.getMaxCount()));
        mMyAliasView.setText(getMyAlias());
        mGroupRemarkView.setText(mGroupChat.getRemark());

        int msgNotifyType = mGroupChat.getPolicy();
        notifySettings.setText(getRemindPolicyStr(msgNotifyType));

        mUpdateSubjectView.setOnClickListener(onClickListener);
        mSMSGroupSends.setOnClickListener(onClickListener);
        mSetMyAliasView.setOnClickListener(onClickListener);
        mChangeGroupChairmanView.setOnClickListener(onClickListener);
        mEnhanceScreenView.setOnClickListener(onClickListener);
        mSaveGroupToContacts.setOnClickListener(onClickListener);
        mLeaveGroupChatButton.setOnClickListener(onClickListener);
        mSetGroupRemarkView.setOnClickListener(onClickListener);
        clearChatHistory.setOnClickListener(onClickListener);
        findViewById(R.id.setGroupChatNotify).setOnClickListener(onClickListener);
    }

    private boolean isChairman(List<GroupChatMember> list) {
        if (TextUtils.isEmpty(mProfilePhoneNumber)) {
            return false;
        }

        // Find 'me' in the group.
        boolean isChairman = false;
        for (GroupChatMember user : list) {
            if (mProfilePhoneNumber.endsWith(user.getNumber())) {
                if (GroupChatMember.CHAIRMAN == user.getRole()) {
                    isChairman = true;
                }

                break;
            }
        }

        return isChairman;
    }

    private void toast(int resId) {
        Toast.makeText(this, resId, Toast.LENGTH_LONG).show();
    }

    private void showUpdateSubjectDialog() {
        if (!isChairman(mAdapter.getUserList())) {
            toast(R.string.only_chairman_can_edit_subject);
            return;
        }
        final EditText input = new EditText(RcsGroupChatDetailActivity.this);
        String inp = mGroupChat.getSubject();
        input.setText(inp);
        input.setSelection(inp.length());
        InputFilter[] filters = { new EditTextInputFilter(30) };
        input.setFilters(filters);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        input.setLayoutParams(lp);
        input.addTextChangedListener(new TextWatcher() {
            private CharSequence temp;
            private int editStart;
            private int editEnd;
            @Override
            public void onTextChanged(CharSequence s, int start, int count, int after) {
            // TODO Auto-generated method stub
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int before,
            int arg3) {
            // TODO Auto-generated method stub
             temp = s;
            }

            @Override
            public void afterTextChanged(Editable s) {
            // TODO Auto-generated method stub
                 editStart = input.getSelectionStart();
                 editEnd = input.getSelectionEnd();
                 if (temp.toString().getBytes().length > 30) {
                     Toast.makeText(RcsGroupChatDetailActivity.this,
                             R.string.group_chat_subject__no_more_number,
                             Toast.LENGTH_SHORT).show();
                     s.delete(editStart - 1, editEnd);
                     int tempSelection = editStart;
                     input.setText(s);
                     input.setSelection(tempSelection);
                 }
            }
        });
        AlertDialog.Builder builder = new AlertDialog.Builder(RcsGroupChatDetailActivity.this);
        builder.setTitle(R.string.update_subject);
        builder.setView(input);
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String newSubject = input.getText().toString().trim();
                if (TextUtils.isEmpty(newSubject)) {
                    toast(R.string.group_chat_subject_cannot_empty);
                     return;
                }
                if(poupAirplainMode()){
                    return;
                }
                try {
                    int result = GroupChatApi.getInstance()
                            .setSubject(mGroupChat.getId(), newSubject);
                    handleGroupChatOperationError(RcsGroupChatDetailActivity.this, result);
                } catch (ServiceDisconnectedException e) {
                    e.printStackTrace();
                    RcsLog.w(e);
                } catch (RemoteException e) {
                    RcsLog.w(e);
                }
            }
        });
        builder.setNegativeButton(android.R.string.cancel, null);
        AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
    }

    private void showSetGroupRemarkDialog() {
        String remark = mGroupChat.getRemark();
        if (remark == null) {
            remark = "";
        }

        final EditText input = new EditText(RcsGroupChatDetailActivity.this);
        input.setText(remark);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        input.setLayoutParams(lp);
        input.setSelection(remark.length());
        AlertDialog.Builder builder = new AlertDialog.Builder(RcsGroupChatDetailActivity.this);
        builder.setTitle(R.string.group_remark);
        builder.setView(input);
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if(poupAirplainMode()){
                    return;
                }
                String newRemark = input.getText().toString().trim();
                if (TextUtils.isEmpty(newRemark))
                    newRemark = "";
                try {
                    int result = GroupChatApi.getInstance()
                            .setRemarks(mGroupChat.getId(), newRemark);
                    handleGroupChatOperationError(RcsGroupChatDetailActivity.this, result);
                } catch (ServiceDisconnectedException e) {
                    RcsLog.w(e);
                } catch (RemoteException e) {
                    RcsLog.w(e);
                }
            }
        });
        builder.setNegativeButton(android.R.string.cancel, null);
        AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
    }

    private void showSetMyAliasDialog() {
        final EditText input = new EditText(RcsGroupChatDetailActivity.this);
        input.setText(getMyAlias());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        input.setLayoutParams(lp);
        if (!TextUtils.isEmpty(getMyAlias())) {
            input.setSelection(getMyAlias().length());
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(RcsGroupChatDetailActivity.this);
        builder.setTitle(R.string.set_my_alias);
        builder.setView(input);
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if(poupAirplainMode()){
                    return;
                }
                String newAlias = input.getText().toString();
                if (TextUtils.isEmpty(newAlias))
                    newAlias = "";
                try {
                    int result = GroupChatApi.getInstance()
                            .setMyAlias(mGroupChat.getId(), newAlias);
                    handleGroupChatOperationError(RcsGroupChatDetailActivity.this, result);
                } catch (ServiceDisconnectedException e) {
                    RcsLog.w(e);
                } catch (RemoteException e) {
                    RcsLog.w(e);
                }
            }
        });
        builder.setNegativeButton(android.R.string.cancel, null);
        AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
    }

    private void showSelectLeaveGroupDialog() {
        AlertDialog.Builder builder = new AlertDialog
                .Builder(RcsGroupChatDetailActivity.this);
        builder.setTitle(R.string.leave_group_chat);

        String[] items = new String[2];
        items[0] = getResources().getString(
                R.string.rcs_leave_group_with_not_assign_charman);
        items[1] = getResources().getString(
                R.string.rcs_leave_group_with_assign_charman);
        builder.setItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
               switch (which) {
                case LEAVE_GROUP_WITH_NOT_ASSIGN_CHARMEN:
                    showLeaveGroupChatDialog();
                    break;
                case LEAVE_GROUP_WITH_ASSIGN_CHARMEN:
                    showChangeGroupChairmanDialog(LEAVE_GROUP_WITH_ASSIGN_CHARMEN);
                    break;
                default:
                    break;
            }
            }
        });
        builder.setNegativeButton(android.R.string.cancel, null);
        AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
    }

    private void showLeaveGroupChatDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(
                RcsGroupChatDetailActivity.this);
        builder.setTitle(R.string.leave_group_chat);
        builder.setPositiveButton(android.R.string.ok,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            dialog.dismiss();
                            if (BasicApi.getInstance().isOnline()) {
                                mProgressDialog = ProgressDialog.show(
                                        RcsGroupChatDetailActivity.this,
                                        getString(R.string.quit_group),
                                        getString(R.string.please_wait));
                                mProgressDialog.setCancelable(false);
                                mProgressDialog
                                        .setOnCancelListener(new OnCancelListener() {
                                            @Override
                                            public void onCancel(
                                                    DialogInterface arg0) {
                                                toast(R.string.quit_group_fail);
                                            }
                                        });
                                int result = -1;
                                if (isChairman(mAdapter.getUserList())) {
                                    result = GroupChatApi.getInstance().disband(mGroupChat.getId());
                                } else {
                                    result = GroupChatApi.getInstance().quit(mGroupChat.getId());
                                }
                                if(GroupChatConstants.CONST_OFFLINE == result ||
                                        GroupChatConstants.CONST_OTHRE_ERROR == result){
                                    toast(R.string.quit_group_fail);
                                    mProgressDialog.dismiss();
                                    mProgressDialog = null;
                                }
                            } else {
                                toast(R.string.rcs_service_is_not_available);
                            }
                        } catch (ServiceDisconnectedException e) {
                            RcsLog.w(e);
                            mProgressDialog.dismiss();
                            toast(R.string.rcs_service_is_not_available);
                        } catch (RemoteException e) {
                            RcsLog.w(e);
                        }
                    }
                });
        builder.setNegativeButton(android.R.string.cancel, null);

        AlertDialog dialog = builder.create();
        dialog.setOnDismissListener(new OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                mLeaveGroupChatButton.setEnabled(true);
                mLeaveGroupChatButton.setTextColor(Color.BLACK);
            }
        });
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
    }

    private void setEnhancedScreenNumber() {
        if (poupAirplainMode()) {
           return;
        }
        try {
            List<GroupChatMember> GroupChatMember = mAdapter.getUserList();
            ArrayList<String> groupChatMemberNumberList = new ArrayList<String>();
            String numbers = "";
            if(!TextUtils.isEmpty(mProfilePhoneNumber)){
                for (GroupChatMember chatUser : GroupChatMember) {
                    if(null != chatUser.getNumber() && !mProfilePhoneNumber.endsWith(
                            chatUser.getNumber())) {
                        groupChatMemberNumberList.add(chatUser.getNumber());
                    }
                }
            }
            mRichScreenApi.startRichScreenApp(groupChatMemberNumberList);
        } catch (ServiceDisconnectedException e1) {
            toast(R.string.rcs_service_is_not_available);
            return;
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private void showChangeGroupChairmanDialog(final int flag) {
        if (!isChairman(mGroupChatMemeberList)) {
            toast(R.string.only_chairman_can_change_chairman);
            return;
        }
        if (poupAirplainMode()) {
           return;
        }

        final List<GroupChatMember> userList = new ArrayList<GroupChatMember>();
        try {
            List<GroupChatMember> canBeAsign = GroupChatApi.getInstance().getMembersAllowChairman(
                    mGroupChat.getId());
            boolean ischairman = false;
            GroupChatMember gc = null;
            for (GroupChatMember user : canBeAsign) {
                if (mProfilePhoneNumber.endsWith(user.getNumber())) {
                    if (GroupChatMember.CHAIRMAN == user.getRole()) {
                        ischairman = true;
                        gc = user;
                        break;
                    }
                }
            }
            if (ischairman) {
                canBeAsign.remove(gc);
            }
            if (canBeAsign != null) {
                userList.addAll(canBeAsign);
            }
        } catch (ServiceDisconnectedException e) {
            RcsLog.w(e);
        } catch (RemoteException e) {
            RcsLog.w(e);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(RcsGroupChatDetailActivity.this);
        builder.setTitle(R.string.change_group_chairman);
        if (userList.size() == 0) {
            builder.setMessage(R.string.no_group_chat_user_can_be_chairman);
        } else {
            String[] items = new String[userList.size()];
            for (int i = 0, size = userList.size(); i < size; i++) {
                final String name = RcsContactUtils.getGroupChatMemberDisplayName(
                        RcsGroupChatDetailActivity.this, mGroupId,
                        userList.get(i).getNumber(), null);
                items[i] = name;
            }
            builder.setItems(items, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    try {
                        int result = GroupChatApi.getInstance().assignChairman(
                                mGroupChat.getId(), userList.get(which).getNumber());
                        handleGroupChatOperationError(RcsGroupChatDetailActivity.this, result);
                        if (flag == LEAVE_GROUP_WITH_ASSIGN_CHARMEN && result ==
                                GroupChatConstants.CONST_SUCCESS) {
                            int results = GroupChatApi.getInstance().quit(mGroupChat.getId());
                            if(GroupChatConstants.CONST_OFFLINE == results ||
                                    GroupChatConstants.CONST_OTHRE_ERROR == results){
                                toast(R.string.quit_group_fail);
                            }
                        }
                    } catch (ServiceDisconnectedException e) {
                        e.printStackTrace();
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            });
        }
        builder.setNegativeButton(android.R.string.cancel, null);
        AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
    }

    private void showAddGroupMemberDialog() {
        AlertDialog.Builder builder = new AlertDialog
                .Builder(RcsGroupChatDetailActivity.this);
        builder.setTitle(R.string.add_groupchat_member);

        String[] items = new String[2];
        items[0] = getResources().getString(
                R.string.add_groupchat_member_by_number);
        items[1] = getResources().getString(
                R.string.add_groupchat_member_by_contacts);
        builder.setItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == ADD_GROUP_CHAT_BY_NUMBER) {
                    showAddNumberDialog();
                } else {
                    try {
                        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT
                                || Build.VERSION.SDK_INT == Build.VERSION_CODES.M) {
                            Intent intent = new Intent(INTENT_MULTI_PICK, Contacts.CONTENT_URI);
                            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.M) {
                                intent.putExtra(FROM_PUBLICACCOUNT, true);
                            }
                            startActivityForResult(intent, ADD_CONTACT_TO_GROUP_CHAT);
                        } else {
                            Intent intent = new Intent();
                            intent.setAction("com.android.mms.ui.SelectRecipientsList");
                            intent.putExtra("mode", 0);
                            startActivityForResult(intent, ADD_CONTACT_TO_GROUP_CHAT);
                        }
                    } catch (ActivityNotFoundException ex) {
                        toast(R.string.contact_app_not_found);
                    }
                }
            }
        });

        builder.setNegativeButton(android.R.string.cancel, null);
        AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
    }

    private void showAddNumberDialog() {
        final EditText editText = new EditText(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        editText.setLayoutParams(lp);
        new AlertDialog.Builder(this)
        .setTitle(R.string.add_groupchat_member_by_number)
        .setView(editText)
        .setPositiveButton(android.R.string.ok,  new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                String input = editText.getText().toString();
                if (TextUtils.isEmpty(input)) {
                    toast(R.string.input_is_empty);
                } else {
                    processInputResult(input);
                }
            }
        }).setNegativeButton(android.R.string.cancel, null)
        .show();

    }

    private void processInputResult(final String number) {
        List<String> referUsers = new ArrayList<String>();
        StringBuffer sb = new StringBuffer();
        //TODO NOT DEFINE
        GroupChatMember user = null;
       // GroupChatMember user = mGroupChat.getUserByNumber(number);
        if (user == null) {
            referUsers.add(number);
        } else {
            sb.append(number);
        }
        String toastStr = sb.toString();
        if (!TextUtils.isEmpty(toastStr)) {
            Toast.makeText(this, getString(R.string.number_in_group, toastStr),
                    Toast.LENGTH_LONG).show();
        }
        if(referUsers.size() > 0){
            try {
                int result = GroupChatApi.getInstance().invite(mGroupChat.getId(), referUsers);
                handleGroupChatOperationError(RcsGroupChatDetailActivity.this, result);
            } catch (ServiceDisconnectedException e) {
                e.printStackTrace();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

    }

    private OnClickListener onClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            try {
                if (!BasicApi.getInstance().isOnline()) {
                    Toast.makeText(RcsGroupChatDetailActivity.this,
                            R.string.rcs_service_is_not_online, Toast.LENGTH_SHORT).show();
                    return;
                }
            } catch (Exception e) {
                Toast.makeText(RcsGroupChatDetailActivity.this, R.string.rcs_service_is_not_online,
                        Toast.LENGTH_SHORT).show();
                return;
            }
            switch (v.getId()) {
                case R.id.updateSubject: {
                    showUpdateSubjectDialog();
                    break;
                }
                case R.id.setMyAlias: {
                    try {
                        mGroupChatMemeberList = GroupChatApi.getInstance().getMembers(mGroupId);
                    } catch (ServiceDisconnectedException e) {
                        e.printStackTrace();
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                    showSetMyAliasDialog();
                    break;
                }
                case R.id.ehancedScreen: {
                    setEnhancedScreenNumber();
                    break;
                }
                case R.id.add_group_to_contacts: {
                    saveGroupToContacts();
                    break;
                }
                case R.id.changeGroupChairman: {
                    showChangeGroupChairmanDialog(LEAVE_GROUP_WITH_NOT_ASSIGN_CHARMEN);
                    break;
                }
                case R.id.leaveGroupChat: {
                    if (isChairman(mAdapter.getUserList())) {
                        showSelectLeaveGroupDialog();
                    } else {
                        showLeaveGroupChatDialog();
                    }
                    break;
                }
                case R.id.setGroupRemark: {
                    showSetGroupRemarkDialog();
                    break;
                }
                case R.id.groupMemberSendSMS: {
                    groupSendsSMS();
                    break;
                }
                case R.id.setGroupChatNotify: {
                    showRemindPolicyDialog();
                    break;
                }
                case R.id.clear_chat_history: {
                    clearChatHistory();
                    break;
                }
            }
        }
    };

    private void saveGroupToContacts() {
        if (mGroupChat != null) {
            if (!RcsContactUtils.isGroupExistContacts(this, mGroupId)) {
                String groupTitle = TextUtils.isEmpty(mGroupChat
                        .getRemark()) ? mGroupChat.getSubject()
                        : mGroupChat.getRemark();
                RcsContactUtils.insertGroupChat(this, mGroupId, groupTitle);
            } else {
                toast(R.string.group_has_exist_contacts);
            }
        }
    }

    private void clearChatHistory() {
        AlertDialog.Builder mBuilder = new Builder(
                RcsGroupChatDetailActivity.this);
        mBuilder.setTitle(R.string.clear_chat_history);
        mBuilder.setMessage(R.string.sure_clear_chat_history);
        mBuilder.setNeutralButton(R.string.confirm,
                new DialogInterface.OnClickListener() {
                    @Override
            public void onClick(DialogInterface dialog, int which) {
                boolean isRemoveOK = deleteConversationByThreadId(RcsGroupChatDetailActivity.this,
                        mGroupChat.getThreadId());
                if (isRemoveOK) {
                    Toast.makeText(RcsGroupChatDetailActivity.this, R.string.clear_all_succeed,
                            Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(RcsGroupChatDetailActivity.this, R.string.clear_all_fail,
                            Toast.LENGTH_SHORT).show();
                }
            }
                });
        mBuilder.setNegativeButton(R.string.cancel, null);
        mBuilder.create().show();
    }

    private boolean deleteConversationByThreadId(Context context, long threadId) {
        int delete = context.getContentResolver().delete(Sms.CONTENT_URI, Sms.THREAD_ID, new String[] {
            String.valueOf(threadId)
        });
        if (delete != 0) {
            return true;
        }
        return false;
    }

    private void showRemindPolicyDialog() {
        int msgNotifyType = mGroupChat.getPolicy();
        String[] items = this.getResources().getStringArray(R.array.group_chat_remind_policy);
        new AlertDialog.Builder(RcsGroupChatDetailActivity.this)
        .setTitle(R.string.set_group_chat_notify)
        .setIcon(null)
        .setSingleChoiceItems(items, msgNotifyType,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            GroupChatApi.getInstance()
                                    .setGroupChatRemindPolicy(mGroupChat.getId(), which);
                            notifySettings.setText(getRemindPolicyStr(which));
                        } catch (ServiceDisconnectedException e) {
                            e.printStackTrace();
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                        dialog.dismiss();
                    }
                }).show();
    }

    private String getRemindPolicyStr(int policyType) {
        if (policyType == 0) {
            return getString(R.string.receive_and_remind);
        } else if (policyType == 1) {
            return getString(R.string.receive_not_remind);
        } else if (policyType == 2) {
            return getString(R.string.not_receive);
        } else {
            return getString(R.string.receive_and_remind);
        }
    }

    private void groupSendsSMS() {
        List<GroupChatMember> GroupChatMember = mAdapter.getUserList();
        if (!isChairman(GroupChatMember)) {
            toast(R.string.only_chairman_can_SMS_group_sends);
            return;
        } else {
            String numbers = "";
            if(!TextUtils.isEmpty(mProfilePhoneNumber)){
                for (GroupChatMember chatUser : GroupChatMember) {
                    if(!mProfilePhoneNumber.endsWith(chatUser.getNumber())){
                        numbers = numbers + chatUser.getNumber() + ";";
                    }
                }
            }
            Uri uri = Uri.parse("smsto:" + numbers);
            Intent sendIntent = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(sendIntent);
        }
    }

    private String getMyAlias() {
        List<GroupChatMember> userList = mGroupChatMemeberList;
        for (GroupChatMember user : userList) {
            String number = user.getNumber();
            if (mProfilePhoneNumber != null && mProfilePhoneNumber.endsWith(number)) {
                String alias = user.getAlias();
                if (!TextUtils.isEmpty(alias)) {
                    return alias;
                }
            }
        }
        return "";
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case ADD_CONTACT_TO_GROUP_CHAT:
                if (resultCode == RESULT_OK) {
                    processPickResult(data);
                }
                break;

            default:
                break;
        }
    }

    private void processPickResult(final Intent data) {
        // The EXTRA_PHONE_URIS stores the phone's urls that were selected by
        // user in the
        // multiple phone picker.
        List<String> referUsers = new ArrayList<String>();
        if (data != null) {
            referUsers = data.getStringArrayListExtra("recipients");
        }else{
            return;
        }
        if (referUsers.size() > 0) {
            try {
                int result = GroupChatApi.getInstance().invite(mGroupChat.getId(), referUsers);
                handleGroupChatOperationError(RcsGroupChatDetailActivity.this, result);
            } catch (ServiceDisconnectedException e) {
                e.printStackTrace();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

    }

    private void kickoutMember(String number) {
        if (mGroupChat == null) {
            toast(R.string.kick_out_member_fail);
            return;
        }
        mProgressDialog = ProgressDialog.show(this, getString(R.string.kick_out_member_ing),
                getString(R.string.please_wait));
        mProgressDialog.setCancelable(true);
        mProgressDialog.setOnCancelListener(new OnCancelListener() {

            @Override
            public void onCancel(DialogInterface arg0) {
                toast(R.string.kick_out_member_fail);
            }
        });
        try {
            int result = GroupChatApi.getInstance().kickOut(mGroupChat.getId(), number);
            handleGroupChatOperationError(RcsGroupChatDetailActivity.this, result);
        } catch (ServiceDisconnectedException e) {
            e.printStackTrace();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        if (mProfilePhoneNumber.equals(number)) {
            RcsContactUtils.deleteGroupChat(this, mGroupChat.getId());
        }
    }

    private boolean poupAirplainMode(){
        int isAirplainOn = Settings.System.getInt(RcsGroupChatDetailActivity.this.getContentResolver(),
            Settings.System.AIRPLANE_MODE_ON, 0) ;
        if(isAirplainOn == 1){
            toast(R.string.airplain_mode);
            return true;
        }
        return false;
    }

    class RcsGroupChatMemberListAdapter extends BaseAdapter {
        ArrayList<GroupChatMember> mGroupChatMembers = new ArrayList<GroupChatMember>();

        Context mContext;

        LayoutInflater mFactory;

        boolean isAddBtnShow;

        boolean isDelBtnShow;

        int groupChatCount;

        String myPhoneNumber;

        boolean delModel;

        boolean isChaiman;

        long groupId;

        public List<GroupChatMember> getUserList() {
            return mGroupChatMembers;
        }

        public RcsGroupChatMemberListAdapter(Context context, long groupId) {
            mContext = context;
            mFactory = LayoutInflater.from(context);
            this.groupId = groupId;

        }

        public void inittUserPhoneNumber() {
            try {
                myPhoneNumber = BasicApi.getInstance().getAccount();
            } catch (ServiceDisconnectedException e) {
                e.printStackTrace();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        public String getAccountNumber() {
            if (TextUtils.isEmpty(myPhoneNumber)) {
                inittUserPhoneNumber();
            }
            return myPhoneNumber;
        }

        public void isAddBtnShow(boolean isAddBtnShow) {
            this.isAddBtnShow = isAddBtnShow;
        }

        public void isDelBtnShow(boolean isDelBtnShow) {
            this.isDelBtnShow = isDelBtnShow;
        }

        public void setDelModel(boolean delModel) {
            this.delModel = delModel;

        }

        public void bind(List<GroupChatMember> GroupChatMembers) {
            if (GroupChatMembers == null) {
                return;
            } else {
                isChaiman = isChairman(GroupChatMembers);
            }
            mGroupChatMembers.clear();
            mGroupChatMembers.addAll(GroupChatMembers);
            groupChatCount = GroupChatMembers.size();
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            if (mGroupChatMembers == null)
                return 0;
            int count = mGroupChatMembers.size();
            if (isDelBtnShow && groupChatCount == 1) {
                isDelBtnShow = false;
            } else if (groupChatCount != 1 && isChaiman) {
                isDelBtnShow = true;
            }
            if (isAddBtnShow) {
                count++;
            }
            if (isDelBtnShow) {
                count++;
            }
            return count;
        }

        @Override
        public GroupChatMember getItem(int position) {
            if (position >= groupChatCount) {
                return null;
            }
            return mGroupChatMembers.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final ViewHolder holder;
            if (convertView == null) {
                convertView = mFactory.inflate(R.layout.rcs_group_chat_member_item, parent, false);
                holder = new ViewHolder(convertView);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder)convertView.getTag();
            }
            if (position < groupChatCount) {
                holder.mName.setVisibility(View.VISIBLE);
                final GroupChatMember groupChatMember = getItem(position);
                final String number = groupChatMember.getNumber();
                holder.mPhoto.setTag(number);
                holder.mName.setTag(holder.mPhoto);
                if (myPhoneNumber != null && myPhoneNumber.endsWith(number)) {
                    holder.mPhoto.assignContactUri(RcsContactUtils.PROFILE_URI);
                    holder.mPhoto.setClickable(true);
                } else {
                    holder.mPhoto.assignContactFromPhone(number, false);
                }
                holder.mPhoto.setImageToDefault();
                final String name = RcsContactUtils.getGroupChatMemberDisplayName(mContext,
                        groupId, number, myPhoneNumber);
                holder.mName.setText(name);

                if (GroupChatMember.CHAIRMAN == groupChatMember.getRole()) {
                    mChairmanDrawable.setBounds(0, 0, mChairmanDrawable.getMinimumWidth(),
                            mChairmanDrawable.getMinimumHeight());
                    holder.mName.setCompoundDrawables(mChairmanDrawable, null, null, null);
                } else {
                    holder.mName.setCompoundDrawables(null, null, null, null);
                }

                if (delModel && groupChatCount != 1) {
                    holder.mPhoto.setClickable(!delModel);
                    if (myPhoneNumber != null && !myPhoneNumber.endsWith(number)) {
                        holder.delBtn.setVisibility(View.VISIBLE);
                        holder.mAvatarLayout.setOnClickListener(new OnClickListener() {
                            @Override
                            public void onClick(View arg0) {
                                try {
                                    if (BasicApi.getInstance().isOnline()) {
                                        kickoutMember(groupChatMember.getNumber());
                                    } else {
                                        toast(R.string.rcs_network_unavailable);
                                        return;
                                    }
                                } catch (ServiceDisconnectedException e) {
                                    RcsLog.w(e);
                                    toast(R.string.rcs_service_is_not_available);
                                } catch (RemoteException e) {
                                    RcsLog.w(e);
                                }
                            }
                        });
                    } else {
                        holder.mPhoto.setClickable(!delModel);
                        holder.delBtn.setVisibility(View.INVISIBLE);
                    }
                } else {
                    holder.mPhoto.setClickable(!delModel);
                    holder.delBtn.setVisibility(View.INVISIBLE);
                }
                if (holder.mPhoto.getTag() != null) {
                    final Bitmap photo;
                    if (myPhoneNumber != null && myPhoneNumber.endsWith(number)) {
                        photo = RcsContactUtils.getMyProfilePhotoOnData(mContext);
                    } else {
                        photo = RcsContactUtils.getPhotoByNumber(mContext, number);
                    }
                    if (number != null && number.equals(holder.mPhoto.getTag())) {
                        if (photo != null) {
                            holder.mPhoto.setImageBitmap(photo);
                        } else {
                            Bitmap defaultImage = BitmapFactory.decodeResource(mResources,
                                    R.drawable.ic_contact_picture);
                            holder.mPhoto.setImageToDefault();
                            Drawable defaultDrawable = new BitmapDrawable(defaultImage);
                            GroupMemberPhotoCache.getInstance().loadGroupMemberPhoto(groupId,
                                    number, (ImageView)holder.mPhoto, defaultDrawable);
                        }
                    } else {
                        holder.mPhoto.setImageToDefault();
                    }
                }

            } else if (isAddBtnShow && (position == groupChatCount)) {
                holder.mPhoto.setImageResource(R.drawable.groupchat_add);
                holder.mName.setVisibility(View.INVISIBLE);
                holder.delBtn.setVisibility(View.INVISIBLE);
                holder.mName.setTag(null);
                holder.mPhoto.setTag(null);
                holder.mName.setCompoundDrawables(null, null, null, null);
                holder.mPhoto.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View arg0) {
                        try {
                            if (poupAirplainMode()) {
                                return;
                            }
                            if (BasicApi.getInstance().isOnline()) {
                                showAddGroupMemberDialog();
                            } else {
                                toast(R.string.rcs_network_unavailable);
                                return;
                            }
                        } catch (ServiceDisconnectedException e) {
                            RcsLog.w(e);
                            toast(R.string.rcs_service_is_not_available);
                        } catch (RemoteException e) {
                            RcsLog.w(e);
                        }
                    }
                });
            } else if (isDelBtnShow && (position == (groupChatCount + 1))) {
                holder.mPhoto.setImageResource(R.drawable.groupchat_delete);
                holder.mName.setVisibility(View.INVISIBLE);
                holder.delBtn.setVisibility(View.INVISIBLE);
                holder.mName.setTag(null);
                holder.mPhoto.setTag(null);
                holder.mName.setCompoundDrawables(null, null, null, null);
                holder.mPhoto.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View arg0) {
                        if (poupAirplainMode()) {
                            return;
                        }
                        delModel = !delModel;
                        RcsGroupChatMemberListAdapter.this.notifyDataSetChanged();
                    }
                });
            }
            return convertView;
        }

        class ViewHolder {
            QuickContactBadge mPhoto;

            TextView mName;

            ImageView delBtn;

            RelativeLayout mAvatarLayout;

            public ViewHolder(View convertView) {
                mAvatarLayout = (RelativeLayout)convertView.findViewById(R.id.avatar_layout);
                mPhoto = (QuickContactBadge)convertView.findViewById(R.id.avatar);
                mName = (TextView)convertView.findViewById(R.id.name);
                delBtn = (ImageView)convertView.findViewById(R.id.delBtn);
            }
        }
    }

    private void toastReferError(OnReferErrorEvent event) {
        StringBuilder str = new StringBuilder();
        switch (event.getReferErrorAction()) {
            case GroupChatConstants.CONST_OPERATION_CREATE: {
                str.append(getResources().getString(R.string.groupchat_name));
                str.append("(");
                str.append(event.getSubject());
                str.append(")");
                str.append(getResources().getString(R.string.create_fail));
                Toast.makeText(this, str.toString(), Toast.LENGTH_SHORT).show();
                break;
            }
            case GroupChatConstants.CONST_OPERATION_ACCEPT_TO_JOIN: {
                str.append(getResources().getString(R.string.accept));
                str.append(getResources().getString(R.string.groupchat_name));
                str.append("(");
                str.append(event.getSubject());
                str.append(")");
                str.append(getResources().getString(R.string.invite_error));
                Toast.makeText(this, str.toString(), Toast.LENGTH_SHORT).show();
                break;
            }

            case GroupChatConstants.CONST_OPERATION_REFUSE_TO_JOIN: {
                str.append(getResources().getString(R.string.decline));
                str.append(getResources().getString(R.string.groupchat_name));
                str.append("(");
                str.append(event.getSubject());
                str.append(")");
                str.append(getResources().getString(R.string.invite_error));
                Toast.makeText(this, str.toString(), Toast.LENGTH_SHORT).show();
                break;
            }
            case GroupChatConstants.CONST_OPERATION_INVITE: {
                toast(R.string.invite_error);
                break;
            }
            case GroupChatConstants.CONST_OPERATION_SET_ALIAS: {
                toast(R.string.alias_error);
                break;
            }
            case GroupChatConstants.CONST_OPERATION_QUIT: {
                toast(R.string.quit_error);
                break;
            }
            case GroupChatConstants.CONST_OPERATION_SET_SUBJECT: {
                toast(R.string.subject_error);
                break;
            }
            case GroupChatConstants.CONST_OPERATION_SET_CHAIRMAN: {
                toast(R.string.chairman_error);
                break;
            }
            case GroupChatConstants.CONST_OPERATION_KICK_OUT: {
                toast(R.string.kickout_error);
                break;
            }
            case GroupChatConstants.CONST_OPERATION_DISBAND: {
                toast(R.string.groupchat_disband_fail);
                break;
            }
            case GroupChatConstants.CONST_OPERATION_SEND_INVITATION: {
                toast(R.string.groupchat_sent_invite_fail);
                break;
            }
            default:
                break;
        }
    }

    private void handleGroupChatOperationError(Context context, int result) {
        RcsLog.d("handleGroupChatOperationError:" + result);
        int resId;
        switch (result) {
            case GroupChatConstants.CONST_SUCCESS: {
                resId = R.string.operation_successful;
                break;
            }
            case GroupChatConstants.CONST_OFFLINE: {
                resId = R.string.terminal_offline_operate_fail;
                break;
            }
            case GroupChatConstants.CONST_INVITE_TIMEOUT: {
                resId = R.string.invite_has_timeout;
                break;
            }
            case GroupChatConstants.CONST_NOT_EXIST: {
                resId = R.string.group_chat_not_exist;
                break;
            }
            case GroupChatConstants.CONST_NOT_CHAIRMAN: {
                resId = R.string.not_chairman_operation_fail;
                break;
            }
            case GroupChatConstants.CONST_MEMBERS_REACHED_MAX_COUNT: {
                resId = R.string.group_member_has_reached_max_count;
                break;
            }
            case GroupChatConstants.CONST_NOT_ACTIVE: {
                resId = R.string.groupchat_create_fail;
                break;
            }
            case GroupChatConstants.CONST_INTERNAL_ERROR: {
                resId = R.string.groupchat_inner_error;
                break;
            }
            case GroupChatConstants.CONST_OTHRE_ERROR: {
                resId = R.string.groupchat_unknown_error;
                break;
            }
            default:
                resId = R.string.group_chat_has_invite_please_wait;
                break;
        }
        Toast.makeText(context, resId, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onChanged(long groupId, GroupChatNotifyEvent event) {
        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }
        try {
            mGroupChat = GroupChatApi.getInstance().getGroupChatById(groupId);
            if(mGroupChat == null){
                toast(R.string.rcs_service_is_not_available);
                finish();
            }
        } catch (ServiceDisconnectedException e) {
            toast(R.string.rcs_service_is_not_available);
            finish();
            return;
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        String actionType = event.getActionType();
        RcsLog.d("actionType= "+actionType);
        if (OnReferErrorEvent.ON_REFER_ERROR_ACTION.equals(actionType)) {
            OnReferErrorEvent onReferError = (OnReferErrorEvent) event;
            toastReferError(onReferError);
            return;
        } else if ("deleted".equals(actionType)
                || "quit".equals(actionType)) {
            mLeaveGroupChatButton.setEnabled(true);
            mLeaveGroupChatButton.setTextColor(Color.BLACK);
            if (groupId > 0 && groupId == event.getGroupId()) {
                toast(R.string.group_chat_deleted);
                finish();
            }
        } else if ("departed".equals(actionType)) {
            OnMemberQuitEvent onMemeberQuitEvent = (OnMemberQuitEvent)event;
            String memberNumber = onMemeberQuitEvent.getMemberNumber();
            try {
                List<GroupChatMember> userList = GroupChatApi.getInstance().getMembers(
                        groupId);
                if (userList != null) {
                    mAdapter.bind(userList);
                    mUserCount.setText(getString(R.string.group_user_count, userList.size()));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (groupId > 0 && groupId == event.getGroupId()
                    && mProfilePhoneNumber.endsWith(memberNumber)) {
                finish();
            }
        } else if ("updateSubject".equals(actionType)) {
            OnSubjectChangeEvent onSubjectEvent = (OnSubjectChangeEvent) event;
            mSubjectView.setText(onSubjectEvent.getNewSubject());
            RcsContactUtils.UpdateGroupChatSubject(this,event.getGroupId(),
                    onSubjectEvent.getNewSubject());
        } else if ("updateAlias".equals(actionType)) {
            String myPhoneNumber = mAdapter.getAccountNumber();
            OnAliasUpdateEvent onAliasEvent = (OnAliasUpdateEvent) event;
            String alias = onAliasEvent.getAlias();
            String number = onAliasEvent.getPhoneNumber();
            if (mMyAliasView != null && myPhoneNumber.equals(number)) {
                mMyAliasView.setText(alias);
            }
            if (!TextUtils.isEmpty(number)) {
                QuickContactBadge tagView = (QuickContactBadge)
                        mUserListView.findViewWithTag(number);
                TextView nameTextView = (TextView) mUserListView.findViewWithTag(tagView);
                if (nameTextView != null) {
                    if (TextUtils.isEmpty(alias)) {
                        String name = RcsContactUtils.getGroupChatMemberDisplayName(
                                RcsGroupChatDetailActivity.this, groupId, number, myPhoneNumber);
                        nameTextView.setText(name);
                    } else {
                        nameTextView.setText(alias);
                    }
                }
            }
            if (TextUtils.isEmpty(mGroupChat.getSubject()) &&
                    TextUtils.isEmpty(mGroupChat.getRemark())) {
                RcsContactUtils.UpdateGroupChatSubject(this, event.getGroupId(), alias);
            }
        } else if ("updateRemark".equals(actionType)) {
            OnRemarkChangeEvent onRemarkEvent = (OnRemarkChangeEvent) event;
            String remark = onRemarkEvent.getRemark();
            mGroupChat.setRemark(remark);
            mGroupRemarkView.setText(remark);
        } else if ("overMaxCount".equals(actionType)) {
            if (groupId > 0 && event != null && groupId == event.getGroupId()) {
                toast(R.string.group_is_full);
            }
        } else if ("updateChairman".equals(actionType)) {
            try {
               List<GroupChatMember> userList = GroupChatApi.getInstance().getMembers(
                       groupId);
                if (userList != null) {
                    boolean isDelBtnShow = isChairman(userList);
                    mAdapter.isDelBtnShow(isDelBtnShow);
                    mAdapter.setDelModel(false);
                    mAdapter.bind(userList);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            if (mAdapter != null) {
                try {
                    List<GroupChatMember> userList = GroupChatApi.getInstance().getMembers(
                            groupId);
                    if (userList != null) {
                        mAdapter.bind(userList);
                        mUserCount.setText(getString(R.string.group_user_count, userList.size()));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
    private class UpdateGroupChatMemberAvatarTask extends AsyncTask<Void, Void, Void> {
        private Context mTaskContext;
        private RcsGroupChatMemberListAdapter mTaskAdapter;
        private List<GroupChatMember> mTaskGroupChatMembers;
        private long mTaskGroupId;
        private HashMap<String, SoftReference<Bitmap>> mImageCache;
        private int mCallUpdateFunctionTime = 0;

        public UpdateGroupChatMemberAvatarTask(Context context, long groupId,
                List<GroupChatMember> groupChatMembers, RcsGroupChatMemberListAdapter adapter) {
            mTaskAdapter = adapter;
            mTaskContext = context;
            mTaskGroupId = groupId;
            mTaskGroupChatMembers = groupChatMembers;
            mImageCache = new HashMap<String, SoftReference<Bitmap>>();
        }

        @Override
        protected Void doInBackground(Void... params) {
            if (mTaskGroupChatMembers == null) {
                return null;
            }
            for (GroupChatMember groupChatMember : mTaskGroupChatMembers) {
                if (groupChatMember != null) {
                    final String number = groupChatMember.getNumber();
                    long contactId = RcsContactUtils.getContactIdByNumber(mTaskContext, number);
                    RcsLog.d("mCallUpdateFunctionTime:" + mCallUpdateFunctionTime);
                    mCallUpdateFunctionTime++;
                    if (contactId != -1) {
                        try {
                            ProfileApi.getInstance().getHeadPicByContact(contactId,
                                    new ProfileListener() {
                                @Override
                                public void onAvatarGet(final Avatar photo,
                                        final int resultCode, final String resultDesc)
                                        throws RemoteException {
                                    RcsLog.d("get avatar:resultCode = "
                                            + resultCode + " resultDesc:" + resultDesc);
                                    mCallUpdateFunctionTime--;
                                    if (resultCode != 0 || photo == null) {
                                        return;
                                    }
                                    String account = photo.getAccount();
                                    String imageString = photo.getImgBase64Str();
                                    if (TextUtils.isEmpty(account)
                                            || TextUtils.isEmpty(imageString)) {
                                        return;
                                    }
                                    long accountContactId = RcsContactUtils.getContactIdByNumber(
                                            mTaskContext, account);
                                    ContentResolver resolver= mTaskContext.getContentResolver();
                                    Cursor c = resolver.query(RawContacts.CONTENT_URI, new String[] {
                                            RawContacts._ID
                                            }, RawContacts.CONTACT_ID + "=" +
                                            String.valueOf(accountContactId), null, null);
                                    long rawContactId = -1;
                                    if (c != null) {
                                        try {
                                            if (c.moveToFirst()) {
                                                if (!RcsContactUtils.hasLocalSetted(resolver,
                                                        c.getLong(0))) {
                                                    rawContactId = c.getLong(0);
                                                }
                                            }
                                        } finally {
                                            c.close();
                                        }
                                    }
                                    if (rawContactId != -1) {
                                        byte[] contactPhoto = Base64.decode(
                                                photo.getImgBase64Str(),
                                                android.util.Base64.DEFAULT);
                                        final Uri outputUri = Uri.withAppendedPath(
                                                ContentUris.withAppendedId(
                                                RawContacts.CONTENT_URI, rawContactId),
                                                RawContacts.DisplayPhoto.CONTENT_DIRECTORY);
                                        RcsContactUtils.setContactPhoto(
                                                mTaskContext, contactPhoto, outputUri);
                                        //notify mms list
                                        mTaskContext.sendBroadcast(new Intent(RcsContactUtils.
                                                NOTIFY_CONTACT_PHOTO_CHANGE));
                                        Bitmap bitmap = BitmapFactory.decodeByteArray(
                                            contactPhoto, 0, contactPhoto.length);
                                        if (!TextUtils.isEmpty(account)) {
                                            mImageCache.put(account,
                                                    new SoftReference<Bitmap>(bitmap));
                                        }
                                    }
                                    if (mCallUpdateFunctionTime == 0) {
                                        Message msg = handler.obtainMessage();
                                        msg.what = UPDATE_GROUP_MEMBER_AVATAR;
                                        msg.obj = mImageCache;
                                        handler.sendMessage(msg);
                                    }
                                }
                                @Override
                                public void onAvatarUpdated(int arg0, String arg1)
                                        throws RemoteException {
                                }

                                @Override
                                public void onProfileGet(Profile arg0, int arg1, String arg2)
                                        throws RemoteException {
                                }

                                @Override
                                public void onProfileUpdated(int arg0, String arg1)
                                        throws RemoteException {
                                }

                                @Override
                                public void onQRImgDecode(QRCardInfo imgObj, int resultCode,
                                        String arg2) throws RemoteException {
                                }

                            });
                        } catch (Exception e) {
                            RcsLog.w(e);
                        }
                    } else {
                        try {
                            GroupChatApi.getInstance().getMemberAvatarFromServer(mTaskGroupId,
                                    number, IMAGE_PIXEL, new GroupChatCallback()  {
                                @Override
                                public void onUpdateAvatar(Avatar avatar, int resultCode,
                                        String resultDesc) throws RemoteException {
                                    mCallUpdateFunctionTime--;
                                    if (avatar != null) {
                                        String str = avatar.getImgBase64Str();
                                        if (str != null) {
                                            byte[] imageByte = Base64.decode(str, Base64.DEFAULT);
                                            Bitmap bitmap = BitmapFactory.decodeByteArray(
                                                    imageByte, 0, imageByte.length);
                                            String account = avatar.getAccount();
                                            if (!TextUtils.isEmpty(account)) {
                                                mImageCache.put(account, new SoftReference<Bitmap>(bitmap));
                                            }
                                        }
                                    }
                                    if (mCallUpdateFunctionTime == 0 && mImageCache.size() > 0) {
                                      //notify mms list
                                        mTaskContext.sendBroadcast(new Intent(RcsContactUtils.
                                                NOTIFY_CONTACT_PHOTO_CHANGE)
                                                .putExtra("CHANGE_NUMBER", avatar.getAccount()));
                                        Message msg = handler.obtainMessage();
                                        msg.what = UPDATE_GROUP_MEMBER_AVATAR;
                                        msg.obj = mImageCache;
                                        handler.sendMessage(msg);

                                    }
                                }
                            });
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
            return null;
        }

        protected void onPostExecute(Void result) {

        }
    }
}
