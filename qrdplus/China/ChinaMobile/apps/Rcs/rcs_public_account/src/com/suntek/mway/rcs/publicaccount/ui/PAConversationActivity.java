/*
 * Copyright (c) 2014-2015 pci-suntektech Technologies, Inc.  All Rights Reserved.
 * pci-suntektech Technologies Proprietary and Confidential.
 */

package com.suntek.mway.rcs.publicaccount.ui;


import android.app.ActionBar;
import android.app.ActionBar.LayoutParams;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.RingtoneManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.provider.ContactsContract.Contacts;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewStub;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.AbsListView.MultiChoiceModeListener;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.suntek.mway.rcs.client.aidl.constant.Actions;
import com.suntek.mway.rcs.client.aidl.constant.Constants;
import com.suntek.mway.rcs.client.aidl.plugin.entity.emoticon.EmoticonBO;
import com.suntek.mway.rcs.client.aidl.plugin.entity.pubacct.PublicTopicMessage.PublicTopicContent;
import com.suntek.mway.rcs.client.aidl.plugin.entity.pubacct.PublicMediaMessage;
import com.suntek.mway.rcs.client.aidl.plugin.entity.pubacct.PublicMessage;
import com.suntek.mway.rcs.client.aidl.plugin.entity.pubacct.PublicTextMessage;
import com.suntek.mway.rcs.client.aidl.plugin.entity.pubacct.PublicTopicMessage;
import com.suntek.mway.rcs.client.aidl.plugin.entity.pubacct.MenuInfo;
import com.suntek.mway.rcs.client.aidl.plugin.entity.pubacct.MenuInfoMode;
import com.suntek.mway.rcs.client.aidl.plugin.entity.pubacct.PublicAccountConstant;
import com.suntek.mway.rcs.client.aidl.plugin.entity.pubacct.PublicAccounts;
import com.suntek.mway.rcs.client.aidl.plugin.entity.pubacct.PublicAccountsDetail;
import com.suntek.mway.rcs.client.aidl.service.entity.GroupChat;
import com.suntek.mway.rcs.client.api.basic.BasicApi;
import com.suntek.mway.rcs.client.api.emoticon.EmoticonApi;
import com.suntek.mway.rcs.client.api.exception.ServiceDisconnectedException;
import com.suntek.mway.rcs.client.api.message.MessageApi;
import com.suntek.mway.rcs.client.api.publicaccount.PublicAccountApi;
import com.suntek.mway.rcs.client.api.publicaccount.PublicAccountCallback;
import com.suntek.mway.rcs.client.api.support.SupportApi;
import com.suntek.mway.rcs.nativeui.RcsNativeUIApp;
import com.suntek.mway.rcs.publicaccount.R;
import com.suntek.mway.rcs.publicaccount.data.PublicConversation;
import com.suntek.mway.rcs.publicaccount.data.PublicMessageItem;
import com.suntek.mway.rcs.publicaccount.data.PublicWorkingMessage;
import com.suntek.mway.rcs.publicaccount.receiver.RcsNotifyManager;
import com.suntek.mway.rcs.publicaccount.ui.adapter.ConversationAdapter;
import com.suntek.mway.rcs.publicaccount.ui.adapter.SelectorAdapter;
import com.suntek.mway.rcs.publicaccount.ui.menubar.MessageMenuBar;
import com.suntek.mway.rcs.publicaccount.ui.menubar.MessageMenuBar.onMenuBarClickListener;
import com.suntek.mway.rcs.publicaccount.ui.widget.HistoryMsgListView;
import com.suntek.mway.rcs.publicaccount.ui.widget.HistoryMsgListView.OnPARefreshListener;
import com.suntek.mway.rcs.publicaccount.ui.widget.HistoryMsgListView.PAListScrollListener;
import com.suntek.mway.rcs.publicaccount.ui.widget.MultiPopupList;
import com.suntek.mway.rcs.publicaccount.ui.widget.MultiSelectionMenu;
import com.suntek.mway.rcs.publicaccount.util.AsynImageLoader;
import com.suntek.mway.rcs.publicaccount.util.AsynImageLoader.ImageCallback;
import com.suntek.mway.rcs.publicaccount.util.AsynImageLoader.LoaderImageTask;
import com.suntek.mway.rcs.publicaccount.util.CommonUtil;
import com.suntek.mway.rcs.publicaccount.util.PublicAccountUtils;
import com.suntek.rcs.ui.common.RcsEmojiInitialize;
import com.suntek.rcs.ui.common.RcsEmojiInitialize.EmojiResources;
import com.suntek.rcs.ui.common.RcsEmojiInitialize.ViewOnClickListener;






public class PAConversationActivity extends Activity implements PAListScrollListener,
        OnItemClickListener {

    private static final int MENU_ADD_ATTACHMENT = 0;

    private static final int MENU_PA_DETAILS = 1;

    private static final int MENU_DEL_MSG = 2;

    private static final int MENU_FORWARD_MSG = 3;

    private static final int MENU_REPORT_MSG = 4;

    private static final int MENU_REPORT_PUBLIC_ACCOUT = 5;

    private static final int MENU_SAVE_ATTACHMENT = 6;

    private static final int MENU_SELECTED_ALL = 7;

    private static final int MENU_CANCEL_SELECTED_ALL = 8;

    private static final int REQUEST_CODE_ATTACH_IMAGE = 120;

    private static final int REQUEST_CODE_TAKE_PICTURE = 121;

    private static final int REQUEST_CODE_ATTACH_VIDEO = 122;

    private static final int REQUEST_CODE_RECORD_VIDEO = 123;

    private static final int REQUEST_CODE_ATTACH_SOUND = 124;

    private static final int REQUEST_CODE_RECORD_SOUND = 125;

    private static final int REQUEST_CODE_CONTACT_VCARD = 126;

    private static final int REQUEST_CODE_ATTACH_MAP = 127;

    private static final int REQUEST_CODE_VCARD_GROUP = 128;

    public static final int REQUEST_SELECT_LOCAL_AUDIO    = 129;

    private static final int TYPE_REPORT_PUBLIC_ACCOUNT = 1;

    private static final int TYPE_REPORT_MSG = 2;

    private View mMultiSelectActionBarView;

    private void errorParam() {
        Toast.makeText(this, R.string.message_uuid_empty, Toast.LENGTH_SHORT).show();
        finish();
    }

    private String mPublicAccountUuid;
    private String mPublicAccountSipUri;

    private long mServiceThreadId;
    private PublicConversation mPublicConversation;

    private Context mContext;

    private ViewStub mPhizViewStub;

    private ActionBar mActionBar;

    private EditText mEditText;

    private HistoryMsgListView mMessageListView;

    private MessageMenuBar mMessageMenuBar;

    private ImageButton mOpenPhizBtn;

    private ConversationAdapter mConversationAdapter;

    public BroadcastReceiver receiver;

    private MultiSelectionMenu mSelectionMenu;

    public static final int REQUEST_CODE_RCS_PICK = 115;

    public static final int REQUEST_SELECT_CONV = 116;

    public static final int REQUEST_SELECT_GROUP = 117;

    public static final int REQUEST_SELECT_PUBLIC_ACCOUNT = 118;

    private static final int REQUEST_CODE_START_DETAIL_ACTIVITY = 133;

    private static final String ACTION_LUNCHER_RCS_PUBLIC_ACCPUNT =
           "com.suntek.mway.rcs.nativeui.ACTION_LUNHCER_PUBLIC_DETAIL";

    private boolean mUnfollow;

    private View mButtomLayout;

    private ImageButton btn_send;

    private PublicWorkingMessage mWorkingMessage;

    private static final int SELECT_CONTACT_VCARD = 0;
    private static final int SELECT_GROUP_VCARD = 1;
    private static final int SELECT_SELF_VCARD = 2;

    private static final int SELECT_CUT_IMAGE = 0;
    private static final int SELECT_ZOOM_IMAGE = 1;
    private static final int SELECT_NOT_CHANGED_IMAGE = 2;
    private static final int SELECT_CANCEL = 3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = this;
        mPublicAccountUuid = getIntent().getStringExtra("PublicAccountUuid");
        if (TextUtils.isEmpty(mPublicAccountUuid)) {
            errorParam();
        }
        mServiceThreadId = getIntent().getLongExtra("ThreadId", -1);
        mPublicConversation = getIntent().getParcelableExtra("publicCOnversation");
        if (mServiceThreadId == -1) {
            mPublicAccountSipUri = getIntent().getStringExtra("PublicAccountSipUri");
            mServiceThreadId = PublicAccountUtils.getTheadIdByNumber(mContext,
                    mPublicAccountSipUri);
        }

        RcsNativeUIApp.getApplication().setNowThreadId(mServiceThreadId);
        setContentView(R.layout.conversation_chat_view);
        RcsNotifyManager.getInstance().cancelNewMessageNotif(String.valueOf(mServiceThreadId));
        mWorkingMessage = new PublicWorkingMessage();
        initActionBar();
        initView();
        listenNotify();
        removeUnreadMessage(mServiceThreadId);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mPublicAccountUuid != null) {
            long threadId = getThreadIdById(mPublicAccountUuid);
            if (threadId != -1) {
                mServiceThreadId = threadId;
                RcsNativeUIApp.getApplication().setNowThreadId(mServiceThreadId);
                RcsNotifyManager.getInstance().cancelNewMessageNotif(
                        String.valueOf(mServiceThreadId));
                removeUnreadMessage(mServiceThreadId);
            }
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

    private void initActionBar() {
        mActionBar = getActionBar();
        mActionBar.setDisplayHomeAsUpEnabled(true);
        mActionBar.setDisplayUseLogoEnabled(false);
        mActionBar.setIcon(R.drawable.public_account);
        mActionBar.setTitle(R.string.public_account_conversation_view);
    }

    private void initView() {
        mButtomLayout = findViewById(R.id.layout_Bottom);
        mMessageMenuBar = new MessageMenuBar(this, mButtomLayout,
                menuBarListener);
        btn_send = (ImageButton)findViewById(R.id.btn_Send);
        findViewById(R.id.btnSwitchToMenu).setOnClickListener(mClickListener);
        findViewById(R.id.btnSwitchToMsg).setOnClickListener(mClickListener);
        btn_send.setOnClickListener(mClickListener);
        mOpenPhizBtn = (ImageButton)findViewById(R.id.btn_phiz);
        mOpenPhizBtn.setOnClickListener(mClickListener);
        mPhizViewStub = (ViewStub)findViewById(R.id.view_stub);
        mEditText = (EditText)findViewById(R.id.edit_Text);
        mEditText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mRcsEmojiInitialize != null)
                    mRcsEmojiInitialize.closeViewAndKB();
            }
        });
        mMessageListView = (HistoryMsgListView)findViewById(R.id.list);
        mMessageListView.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        if (mRcsEmojiInitialize != null)
                            mRcsEmojiInitialize.closeViewAndKB();
                        CommonUtil.closeKB(PAConversationActivity.this);
                        break;
                }
                return false;
            }
        });
        mMessageListView.setPARefreshListener(new MessagePullDownRefresh());
        mMessageListView.setOnItemClickListener(PAConversationActivity.this);
        mMessageListView.setPAListScrollListener(this);
        mMessageListView.setMultiChoiceModeListener(new ModeCallback());
        mMessageListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
        mImageLoader = new AsynImageLoader();
        mConversationAdapter = new ConversationAdapter(PAConversationActivity.this, mImageLoader);
        mMessageListView.setAdapter(mConversationAdapter);
        loadMenuBar(mPublicAccountUuid);
        loadPublicAccountDetail(mPublicAccountUuid);
        refreshMessage(true);
        if (!PublicAccountUtils.isFollowByCard(mContext,mPublicAccountUuid)) {
            hideBottomLayout();
        }
    }

    private class MessagePullDownRefresh implements OnPARefreshListener {
        private long prevChatId = -1;
        @Override
        public void onPARefresh() {
            mRefreshChatId = mConversationAdapter.getChatMessageList().get(0).getMessageId();
            if (prevChatId != mRefreshChatId) {
                //TODO  what is the constants
                new PullDownRefreshTask().execute(mPublicAccountUuid);
                prevChatId = mRefreshChatId;
            } else {
                completeRefresh();
            }
        }
    }

    private void completeRefresh() {
        if (mMessageListView != null) {
            mMessageListView.onPARefreshComplete();
        }
    }

    private class PullDownRefreshTask extends AsyncTask<String, Void, List<PublicMessageItem>> {

        @Override
        protected List<PublicMessageItem> doInBackground(String... params) {
            String uuid = params[0];
            String address =  uuid.substring(0, uuid.indexOf("@"));
            return PublicAccountUtils.getAllMessage(PAConversationActivity.this, address);
        }

        @Override
        protected void onPostExecute(List<PublicMessageItem> result) {
            super.onPostExecute(result);
            completeRefresh();
            if (result != null) {
                //Collections.sort(result);
                mConversationAdapter.addChatMessageList(result);
            }
        }
    }

    private boolean isRcsOnline(){
        boolean isRcsOnline = false;
        try {
            isRcsOnline = BasicApi.getInstance().isOnline();
        } catch (ServiceDisconnectedException e) {
            e.printStackTrace();
            isRcsOnline = false;
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return isRcsOnline;
    }

    private class ModeCallback implements MultiChoiceModeListener {
        private int mCheckedCount;

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            switch (item.getItemId()) {
                case MENU_DEL_MSG:
                    confirmDeleteDialog();
                    mode.finish();
                    break;
                case MENU_FORWARD_MSG:
                    final PublicMessageItem message = mConversationAdapter
                            .getSelectedList().get(0);
                    if (message.getRcsMessageType() ==
                            Constants.MessageConstants.CONST_MESSAGE_PAID_EMOTICON) {
                        boolean isCanSend = emotItemCheck(message);
                        if (isCanSend) {
                            PASendMessageUtil.rcsForwardMessage(PAConversationActivity.this,
                                    message);
                        } else {
                            toast(R.string.forward_message_not_support);
                        }
                    } else {
                        PASendMessageUtil.rcsForwardMessage(PAConversationActivity.this, message);
                    }
                    mode.finish();
                    break;
                case MENU_REPORT_MSG:
                    showComplainDialog(TYPE_REPORT_MSG);
                    mode.finish();
                    break;
                case MENU_SAVE_ATTACHMENT:
                    saveMsg();
                    mode.finish();
                    break;
                default:
                    break;
            }
            return true;
        }

        private void addMenuItem(Menu menu, boolean isShowForwardMenu){
            menu.clear();
            menu.add(1, MENU_DEL_MSG, Menu.NONE, R.string.menu_del_msg).setShowAsAction(
                    MenuItem.SHOW_AS_ACTION_NEVER);
            if(isShowForwardMenu){
                menu.add(1, MENU_FORWARD_MSG, Menu.NONE, R.string.menu_forward_msg).setShowAsAction(
                        MenuItem.SHOW_AS_ACTION_NEVER);
            }
            menu.add(1, MENU_REPORT_MSG, Menu.NONE, R.string.menu_report_msg).setShowAsAction(
                    MenuItem.SHOW_AS_ACTION_NEVER);
            menu.add(1, MENU_SAVE_ATTACHMENT, Menu.NONE, R.string.menu_save_msg)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        }

        /*
         * (non-Javadoc)
         * @see
         * android.view.ActionMode.Callback#onCreateActionMode(android.view.
         * ActionMode, android.view.Menu)
         */
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mConversationAdapter.clearMultiData();
            addMenuItem(menu, true);
            if (mMultiSelectActionBarView == null) {
                mMultiSelectActionBarView = LayoutInflater.from(PAConversationActivity.this)
                        .inflate(R.layout.multi_action_mode, null);
            }
            mode.setCustomView(mMultiSelectActionBarView);
            mSelectionMenu = new MultiSelectionMenu(PAConversationActivity.this,
                    (Button)(mMultiSelectActionBarView.findViewById(R.id.selection_menu)),
                    new MultiPopupList.OnPopupItemClickListener() {
                        @Override
                        public boolean onPopupItemClick(int itemId) {
                            if (itemId == MultiSelectionMenu.SELECT_OR_DESELECT) {
                                boolean selectAll = mMessageListView.getCheckedItemCount() < mMessageListView
                                        .getCount() -1 ? true : false;
                                checkAll(selectAll);
                                mSelectionMenu.updateSelectAllMode(selectAll);
                            }
                            return true;
                        }
                    });
            return true;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            mSelectionMenu.dismiss();
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            boolean isShowForwardMenu = false;
            List<PublicMessageItem> selectedList = mConversationAdapter.getSelectedList();
            if (selectedList.size() == 1 && selectedList.get(0).getRcsMessageType() !=
                    Constants.MessageConstants.CONST_MESSAGE_PUBLIC_ACCOUNT_ARTICLE) {
                isShowForwardMenu = true;
            }
            addMenuItem(menu, isShowForwardMenu);
            return true;
        }

        @Override
        public void onItemCheckedStateChanged(ActionMode mode, int position, long id,
                boolean checked) {
            mCheckedCount = mMessageListView.getCheckedItemCount();
            mSelectionMenu.setTitle(getResources().getString(R.string.selected_count,
                    mCheckedCount));
            mSelectionMenu.updateSelectAllMode(mMessageListView.getCount() -1 == mCheckedCount);
            mConversationAdapter.itemCheck(position - 1, checked);
            mConversationAdapter.notifyDataSetChanged();
        }

        private void checkAll(boolean isCheck) {
            for (int i = 1; i < mMessageListView.getCount(); i++) {
                mMessageListView.setItemChecked(i, isCheck);
            }
        }

        private void saveMsg() {
            List<PublicMessageItem> list = mConversationAdapter.getSelectedList();
            final PublicMessageItem chatMessage = list.get(0);
            if (chatMessage.getRcsMessageType() ==
                    Constants.MessageConstants.CONST_MESSAGE_PAID_EMOTICON) {
                boolean isCanSend = emotItemCheck(chatMessage);
                if (isCanSend) {
                    rcsSaveMessage(chatMessage);
                } else {
                    toast(R.string.save_message_not_support);
                }
            } else {
                rcsSaveMessage(chatMessage);
            }
        }

        private boolean emotItemCheck(PublicMessageItem chatMessage) {
            try {
                //TODO this will be EmoticonId,But i don't know where it saved.
                boolean isCanSend = EmoticonApi.getInstance().isCanSend(chatMessage.getMessageBody());
            } catch (ServiceDisconnectedException e) {
                e.printStackTrace();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            return false;
        }

        private void rcsSaveMessage(PublicMessageItem chatMessage) {
            if (isRcsOnline()) {
                if (PASendMessageUtil.saveMessage(PAConversationActivity.this, chatMessage)) {
                    toast(R.string.copy_to_sdcard_success);
                }else {
                    toast(R.string.copy_to_sdcard_fail);
                }
            }else {
                toast(R.string.not_online_message_too_big);
            }
        }
    }
    private void listenNotify() {

        IntentFilter statusFilter = new IntentFilter();
        statusFilter.addAction(Actions.MessageAction.ACTION_MESSAGE_STATUS_CHANGED);
        statusFilter.addAction(PublicAccountUtils.UI_NEED_FRESH);
        registerReceiver(statusReceiver, statusFilter);

        IntentFilter fileFilter = new IntentFilter();
        fileFilter.addAction(Actions.MessageAction.ACTION_MESSAGE_FILE_TRANSFER_PROGRESS);
        registerReceiver(fileProgressReceiver, fileFilter);

        IntentFilter networkFilter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(networkReceiver, networkFilter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (PublicAccountUtils.hasUnReadMessage(mContext,mServiceThreadId)) {
            removeUnreadMessage(mServiceThreadId);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopNotify();
        RcsNativeUIApp.getApplication().setNowThreadId(-1);
    }

    private void stopNotify() {
        try {
            if (receiver != null) {
                unregisterReceiver(receiver);
            }
            if (statusReceiver != null) {
                unregisterReceiver(statusReceiver);
            }
            if (fileProgressReceiver != null) {
                unregisterReceiver(fileProgressReceiver);
            }
            if (networkReceiver != null) {
                unregisterReceiver(networkReceiver);
            }
        } catch (Exception e) {
        }
    }

    private BroadcastReceiver fileProgressReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            //TODO changed these string to constant
            long messageId = intent.getLongExtra("messageId", -1);
            long sessionId = intent.getLongExtra("sessionId", -1);
            long start = intent.getLongExtra("start", -1);
            long end = intent.getLongExtra("end", -1);
            long total = intent.getLongExtra("total", -1);
            onFileProgressChanged(messageId, sessionId, start, end, total);
        }
    };

    private void onFileProgressChanged(long messageId, long sessionId, long start, long end,
            long total) {
        if (messageId == -1) {
            return;
        }
        HashMap<Long, Long> fileProgressHashMap = mConversationAdapter.getHashMap();
        if (fileProgressHashMap != null && total != 0) {
            Long lastProgress = fileProgressHashMap.get(messageId);
            long temp = start * 100 / total;
            if (temp == 100) {
                fileProgressHashMap.remove(messageId);
                return;
            }
            if (lastProgress == null || temp - lastProgress >= 1) {
                lastProgress = temp;
                fileProgressHashMap.put(messageId, Long.valueOf(temp));
                mConversationAdapter.notifyDataSetChanged();
            }
        }
    }

    private BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Actions.MessageAction.ACTION_MESSAGE_STATUS_CHANGED.equals(action)) {
                String idStr = intent.getStringExtra("id");
                if (TextUtils.isEmpty(idStr)) {
                    return;
                }
                boolean refresh = true;
                int id = Integer.parseInt(idStr);
                List<PublicMessageItem> list = mConversationAdapter.getChatMessageList();
                int size = list.size();
                for (int i = size - 1; i >= 0; i--) {
                    if (id == list.get(i).getMessageId()) {
                        // ChatMessage newChatMsg =
                        // RcsApiManager.getMessageApi().getMessageById(idStr);
                        if (list.get(i) != null) {
                            setSendFilePath(list.get(i));
                            // list.set(i, newChatMsg);
                            if (mConversationAdapter != null) {
                                mConversationAdapter.notifyDataSetChanged();
                            }
                        }
                        refresh = false;
                        break;
                    }
                }
            } else if (PublicAccountUtils.UI_NEED_FRESH.equals(action)) {
                refreshMessage(true);
            }
        }
    };

    private void setSendFilePath(PublicMessageItem chatMsg) {
            String filePath = chatMsg.getMessageFilePath();
    }

    private BroadcastReceiver networkReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                ConnectivityManager connectivityManager = (ConnectivityManager)context
                        .getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo info = connectivityManager.getActiveNetworkInfo();
                if (info == null && mConversationAdapter != null) {
                    mConversationAdapter.getHashMap().clear();
                    mConversationAdapter.notifyDataSetChanged();
                }
            }
        }
    };

    private void loadMenuBar(String uuid) {
        try {
            PublicAccountApi.getInstance().getPublicMenuInfo(uuid, callback);
        } catch (ServiceDisconnectedException e) {
            e.printStackTrace();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private void loadPublicAccountDetail(String uuid) {
        try {
            PublicAccountApi.getInstance().getPublicDetail(uuid, detailCallback);
        } catch (ServiceDisconnectedException e) {
            e.printStackTrace();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private PublicAccountCallback detailCallback = new PublicAccountCallback() {

        @Override
        public void respSetAcceptStatus(boolean arg0, String arg1) throws RemoteException {
        }

        @Override
        public void respGetPublicDetail(boolean arg0, final PublicAccountsDetail arg1)
                throws RemoteException {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    loadImage(arg1);
                }
            });
        }
    };

    private PublicAccountsDetail mPublicAccountsDetail;

    private Bitmap mBitmap;
    private AsynImageLoader mImageLoader;

    private void loadImage(PublicAccountsDetail arg1) {
        boolean isSubscribe = false;
        if (arg1 != null) {
            mPublicAccountsDetail = arg1;
            isSubscribe = arg1.getSubscribeStatus() == 1;
            mActionBar.setTitle(arg1.getName());
            if (mConversationAdapter != null)
                mConversationAdapter.setPublicAccountDetail(arg1);
            if (arg1.getLogoUrl() == null) {
                return;
            }
            mUnfollow = arg1.getSubscribeStatus() == 0;
            LoaderImageTask loaderImageTask = new LoaderImageTask(arg1.getLogoUrl(), false, false,
                    false, false);
            mImageLoader.loadImageAsynByUrl(loaderImageTask, new ImageCallback() {
                @Override
                public void loadImageCallback(Bitmap bitmap) {
                    if (bitmap != null) {
                        mBitmap = bitmap;
                        Matrix matrix = new Matrix();
                        matrix.postScale(2, 2);
                        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(),
                                bitmap.getHeight(), matrix, true);
                        @SuppressWarnings("deprecation")
                        Drawable drawable = new BitmapDrawable(bitmap);
                        mActionBar.setIcon(drawable);
                    }
                }
            });
        }
        if (!isSubscribe) {
            findViewById(R.id.layout_Bottom).setVisibility(View.GONE);
        }
    }

    PublicAccountCallback callback = new PublicAccountCallback() {

        @Override
        public void respSetAcceptStatus(boolean arg0, String arg1) throws RemoteException {}

        @Override
        public void respComplainPublicAccount(final boolean result, PublicAccounts arg1)
                throws RemoteException {
            super.respComplainPublicAccount(result, arg1);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (result) {
                        toast(R.string.complain_success);
                    } else {
                        toast(R.string.complain_fail);
                    }
                }
            });
        }

        @Override
        public void respGetPublicMenuInfo(final boolean result, final MenuInfoMode menuInfoMode)
                throws RemoteException {
            super.respGetPublicMenuInfo(result, menuInfoMode);
            runOnUiThread(new Runnable() {
                public void run() {
                    if (result) {
                        CommonUtil.closeKB(PAConversationActivity.this);
                        if (menuInfoMode != null) {
                            if (menuInfoMode.getMenuInfoList().size() > 0) {
                                initMenuBar(menuInfoMode.getMenuInfoList());
                            }
                        } else {
                            onMenuNull();
                        }
                    } else {
                        onMenuNull();
                        Toast.makeText(PAConversationActivity.this, R.string.fail_and_try_again,
                                Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
    };

    private void initMenuBar(List<MenuInfo> menulist) {
        mMessageMenuBar.initMenuBar(menulist);
    }

    private void onMenuNull() {
        mMessageMenuBar.onMenuNull();
    }

    private boolean loadingMore = false;

    private long lastChatId = 0;

    private long mRefreshChatId = 0;

    public int PAGE_COUNT = 20;

    public synchronized void refreshMessage(boolean fromTop) {
        if (loadingMore)
            return;
        if (fromTop) {
            lastChatId = 0;
        }
        new LoadMessageTask().execute(mPublicAccountUuid);
    }

    class LoadMessageTask extends AsyncTask<String, Void, List<PublicMessageItem>> {

        public LoadMessageTask() {
            super();
            loadingMore = true;
        }

        @Override
        protected List<PublicMessageItem> doInBackground(String... params) {
            String uuid = params[0];
            String address = uuid.substring(0, uuid.indexOf("@"));
            return PublicAccountUtils.getAllMessage(PAConversationActivity.this, address);
        }

        @Override
        protected void onPostExecute(List<PublicMessageItem> result) {
            super.onPostExecute(result);
            loadingMore = false;
            if (result != null) {
                //Collections.sort(result);
                if (result.size() > 1)
                    lastChatId = result.get(result.size() - 1).getMessageId();
                else
                    lastChatId = 0;
                mConversationAdapter.getChatMessageList().clear();
                mConversationAdapter.addChatMessageList(result);
                mMessageListView.setSelection(result.size());
            }
        }
    }

    private void removeUnreadMessage(long threadId) {
        PublicAccountUtils.removeUnReadMessage(mContext, threadId);
    }

    private View.OnClickListener mClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.btnSwitchToMenu:
                    findViewById(R.id.input_ayout).setVisibility(View.GONE);
                    findViewById(R.id.pub_account_menus).setVisibility(View.VISIBLE);
                    break;
                case R.id.btnSwitchToMsg:
                    findViewById(R.id.pub_account_menus).setVisibility(View.GONE);
                    findViewById(R.id.input_ayout).setVisibility(View.VISIBLE);
                    break;
                case R.id.btn_phiz:
                    showPhizView();
                    break;
                case R.id.btn_Send:
                    if (!isRcsOnline()) {
                        Toast.makeText(PAConversationActivity.this, R.string.message_unreg,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String content = ((EditText)findViewById(R.id.edit_Text)).getText().toString();
                    if (!TextUtils.isEmpty(content)) {
                        mWorkingMessage.setPublicAccountUuid(mPublicAccountUuid);
                        mWorkingMessage
                                .setRcsMessageType(Constants.MessageConstants.CONST_MESSAGE_TEXT);
                        mWorkingMessage.setRcsThreadId(mServiceThreadId);
                        mWorkingMessage.setRcsTextMessage(content);
                        PASendMessageUtil.sendPublicMessage(mContext, mWorkingMessage);
                    } else {
                        Toast.makeText(mContext, R.string.message_please_input_content,
                                Toast.LENGTH_SHORT).show();
                    }
                    ((EditText)findViewById(R.id.edit_Text)).setText("");
                    break;
                default:
                    break;
            }
        }
    };

    // show jony phiz <begin>
    private RcsEmojiInitialize mRcsEmojiInitialize = null;
    private void showPhizView() {
        if (mRcsEmojiInitialize == null) {
            EmojiResources resources = EmojiResources.create(
                    R.id.title,
                    R.id.icon,
                    R.id.text_face,
                    R.id.item,
                    R.drawable.rcs_public_account_btn_bg,
                    R.layout.rcs_emoji_grid_view_item,
                    R.id.delete_emoji_btn,
                    R.id.add_emoji_btn,
                    R.id.emoji_grid_view,
                    R.id.content_linear_layout,
                    R.drawable.rcs_emoji_popup_bg);
            mRcsEmojiInitialize = new RcsEmojiInitialize(this, mPhizViewStub,
                    mViewOnClickListener, resources);
        }
        mRcsEmojiInitialize.closeOrOpenView();
    }

    private ViewOnClickListener mViewOnClickListener = new ViewOnClickListener() {
        @Override
        public void emojiSelectListener(EmoticonBO emoticonBO) {
            //TODO SendPaidEmoToPub  not define
//            try {
//                MessageApi.getInstance().sendPaidEmo(mServiceThreadId, -1, mPublicAccountUuid,
//                        emoticonBO.getEmoticonId(), emoticonBO.getEmoticonName());
//            } catch (ServiceDisconnectedException e) {
//                e.printStackTrace();
//            }
        }

        @Override
        public void faceTextSelectListener(String faceText) {
            CharSequence text = mEditText.getText() + faceText;
            mEditText.setText(text);
            mEditText.setSelection(text.length());
        }

        @Override
        public void onEmojiDeleteListener() {
            new Thread() {
                public void run() {
                    try {
                        Instrumentation inst = new Instrumentation();
                        inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DEL);
                    } catch (Exception e) {
                        Log.e("Exception when sendKeyDownUpSync", e.toString());
                    }
                };
            }.start();
        }

        @Override
        public void addEmojiPackageListener() {
            CommonUtil.startEmojiStore(PAConversationActivity.this);
        }

        @Override
        public void viewOpenOrCloseListener(boolean isOpen) {
            if (isOpen) {
                mOpenPhizBtn.setImageResource(R.drawable.rcs_emotion_true);
            } else {
                mOpenPhizBtn.setImageResource(R.drawable.rcs_emotion_false);
            }
        }
    };

    private PublicAccountsDetail mAccount;

    // show jony phiz <end>

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case PHOTO_CROP:
                if (data == null)
                    return;
                Uri cropData = data.getData();
                String rcsCropPath = PAMessageUtil.getPARealPathFromURI(mContext, cropData);
                mWorkingMessage.setRcsFilePath(rcsCropPath);
                mWorkingMessage.setPublicAccountUuid(mPublicAccountUuid);
                mWorkingMessage.setRcsThreadId(mServiceThreadId);
                mWorkingMessage.setRcsMessageType(Constants.MessageConstants.CONST_MESSAGE_IMAGE);
                PASendMessageUtil.sendPublicMessage(mContext, mWorkingMessage);
                break;
            case REQUEST_CODE_ATTACH_IMAGE:
                if (data == null)
                    return;
                Uri uriData = data.getData();
                if (uriData == null) {
                    return;
                }
                String rcsPath = PAMessageUtil.getPAContentUriPath(this, uriData);
                if (!TextUtils.isEmpty(rcsPath) && rcsPath.endsWith("gif")
                        || !TextUtils.isEmpty(rcsPath) && rcsPath.endsWith("GIF")) {
                    mWorkingMessage.setRcsFilePath(rcsPath);
                    mWorkingMessage.setPublicAccountUuid(mPublicAccountUuid);
                    mWorkingMessage.setRcsThreadId(mServiceThreadId);
                    mWorkingMessage.setRcsMessageType(Constants.MessageConstants.CONST_MESSAGE_IMAGE);
                    PASendMessageUtil.sendPublicMessage(mContext, mWorkingMessage);
                } else {
                    if (!TextUtils.isEmpty(rcsPath)) {
                        ImageDispose(rcsPath);
                    }
                }
                break;
            case REQUEST_CODE_TAKE_PICTURE:
                if (resultCode == RESULT_OK) {
                    mWorkingMessage.setRcsFilePath(mPicturePath);
                    mWorkingMessage.setPublicAccountUuid(mPublicAccountUuid);
                    mWorkingMessage.setRcsThreadId(mServiceThreadId);
                    mWorkingMessage.setRcsMessageType(Constants.MessageConstants.CONST_MESSAGE_IMAGE);
                    PASendMessageUtil.sendPublicMessage(mContext, mWorkingMessage);
                }
                break;
            case REQUEST_CODE_ATTACH_VIDEO:
                if (data == null)
                    return;
                Uri videoUri = data.getData();
                if (videoUri == null) {
                    return;
                }
                String mRcsVideoPath = PAMessageUtil.getPAContentUriPath(this, videoUri);
                int videoDuration = PAMessageUtil.getDuration(mRcsVideoPath);
                if (new File(mRcsVideoPath).length() > PAMessageUtil.getPAVideoFtMaxSize() * 1024) {
                    toast(R.string.file_size_over);
                    return;
                }
                mWorkingMessage.setRcsMessageType(Constants.MessageConstants.CONST_MESSAGE_VIDEO);
                mWorkingMessage.setPublicAccountUuid(mPublicAccountUuid);
                mWorkingMessage.setRcsFilePath(mRcsVideoPath);
                mWorkingMessage.setRcsThreadId(mServiceThreadId);
                mWorkingMessage.setRcsRecordTime(videoDuration);
                mWorkingMessage.setRcsIsRecord(false);
                PASendMessageUtil.sendPublicMessage(mContext, mWorkingMessage);

                break;
            case REQUEST_CODE_RECORD_VIDEO:
                if (data == null)
                    return;
                videoUri = data.getData();
                if (videoUri == null)
                    return;
                String videoPath = videoUri.getPath();
                videoDuration = PAMessageUtil.getDuration(videoPath);
                if (videoDuration < 1) {
                    toast(R.string.cannot_send_video);
                    return;
                }
                if (videoDuration > PAMessageUtil.getPAVideoMaxTime()) {
                    toast(getString(R.string.video_record_out_time, PAMessageUtil.getPAVideoMaxTime()));
                    return;
                }
//                String videoPath = PAMessageUtil.getPath(this, videoUri);
                if (new File(videoPath).length() > PAMessageUtil.getPAVideoFtMaxSize() * 1024) {
                    toast(R.string.file_size_over);
                    return;
                }
                mWorkingMessage.setRcsMessageType(Constants.MessageConstants.CONST_MESSAGE_VIDEO);
                mWorkingMessage.setPublicAccountUuid(mPublicAccountUuid);
                mWorkingMessage.setRcsFilePath(videoPath);
                mWorkingMessage.setRcsThreadId(mServiceThreadId);
                mWorkingMessage.setRcsRecordTime(videoDuration);
                mWorkingMessage.setRcsIsRecord(true);
                PASendMessageUtil.sendPublicMessage(mContext, mWorkingMessage);

                break;
            case REQUEST_CODE_ATTACH_SOUND:
                if (data == null)
                    return;
                Uri uri = (Uri)data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
                if (uri == null) {
                    uri = data.getData();
                } else if (Settings.System.DEFAULT_RINGTONE_URI.equals(uri)) {
                    break;
                }
                String audioPath = PAMessageUtil.getPAContentUriPath(mContext, uri);
                int audioDuration = PAMessageUtil.getDuration(audioPath);
                audioDuration = PAMessageUtil.getDuration(audioPath);
                mWorkingMessage.setRcsMessageType(
                        Constants.MessageConstants.CONST_MESSAGE_AUDIO);
                mWorkingMessage.setRcsFilePath(audioPath);
                mWorkingMessage.setPublicAccountUuid(mPublicAccountUuid);
                mWorkingMessage.setRcsThreadId(mServiceThreadId);
                mWorkingMessage.setRcsRecordTime(audioDuration);
                mWorkingMessage.setRcsIsRecord(false);
                PASendMessageUtil.sendPublicMessage(mContext, mWorkingMessage);

                break;
            case REQUEST_SELECT_LOCAL_AUDIO:
                if (data != null) {
                    Uri selectUri = data.getData();
                    String path = PAMessageUtil.getPAContentUriPath(this, selectUri);
                    if(!TextUtils.isEmpty(path) && path.contains(".")){
                        String endsWith = path.substring(path.lastIndexOf("."), path.length()).toLowerCase();
                        if (endsWith.equals(".3gp") || endsWith.equals(".mp3") || endsWith.equals(".amr")
                                        || endsWith.equals(".aac") || endsWith.equals(".m4a")) {
                            audioDuration = PAMessageUtil.getDuration(path);
                            mWorkingMessage.setRcsMessageType(
                                    Constants.MessageConstants.CONST_MESSAGE_AUDIO);
                            mWorkingMessage.setRcsFilePath(path);
                            mWorkingMessage.setPublicAccountUuid(mPublicAccountUuid);
                            mWorkingMessage.setRcsThreadId(mServiceThreadId);
                            mWorkingMessage.setRcsRecordTime(audioDuration);
                            mWorkingMessage.setRcsIsRecord(false);
                            PASendMessageUtil.sendPublicMessage(mContext, mWorkingMessage);

                        } else {
                            toast(R.string.audio_file_error);
                        }
                    }
                }
                break;
            case REQUEST_CODE_RECORD_SOUND:
                if (data == null)
                    return;
                Uri audioUri = data.getData();
                String audioPath2 = PAMessageUtil.getPAContentUriPath(mContext, audioUri);
                int audioDuration2 = PAMessageUtil.getDuration(audioPath2);
                if (audioDuration2 < 1) {
                    toast(R.string.cannot_send_audio);
                    return;
                }
                if (audioDuration2 > PAMessageUtil.getPAAudioMaxTime()) {
                    toast(getString(R.string.audio_record_out_time, PAMessageUtil.getPAAudioMaxTime()));
                    return;
                }
                mWorkingMessage.setRcsMessageType(
                        Constants.MessageConstants.CONST_MESSAGE_AUDIO);
                mWorkingMessage.setRcsFilePath(audioPath2);
                mWorkingMessage.setPublicAccountUuid(mPublicAccountUuid);
                mWorkingMessage.setRcsThreadId(mServiceThreadId);
                mWorkingMessage.setRcsRecordTime(audioDuration2);
                mWorkingMessage.setRcsIsRecord(true);
                PASendMessageUtil.sendPublicMessage(mContext, mWorkingMessage);

                break;
            case REQUEST_CODE_VCARD_GROUP:
                if (data == null) {
                    return;
                }
                ArrayList<String> list = data.getStringArrayListExtra("recipients");
                StringBuffer buffer = new StringBuffer();
                for (String string : list) {
                    Uri contactUri = ContentUris.withAppendedId(Contacts.CONTENT_URI,
                            Long.parseLong(string));
                    String lookup = Uri.encode(Contacts
                            .getLookupUri(mContext.getContentResolver(), contactUri)
                            .getPathSegments().get(2));
                    buffer.append(lookup + ":");
                }
                String buffer2 = buffer.substring(0, buffer.lastIndexOf(":"));
                Uri uri2 = Uri.withAppendedPath(PAMessageUtil.getPAVcardUri(),
                        Uri.encode(buffer2));
                PAMessageUtil.setPAVcard(mContext, uri2);

                mWorkingMessage.setPublicAccountUuid(mPublicAccountUuid);
                mWorkingMessage.setRcsThreadId(mServiceThreadId);
                mWorkingMessage.setRcsMessageType(
                        Constants.MessageConstants.CONST_MESSAGE_CONTACT);
                PASendMessageUtil.sendPublicMessage(mContext, mWorkingMessage);
                break;
            case REQUEST_CODE_CONTACT_VCARD:
                if (data == null) {
                    return;
                }
                String extraVCard = data.getStringExtra("vcard");
                if (extraVCard != null) {
                    Uri vcard = Uri.parse(extraVCard);
                    PAMessageUtil.setPAVcard(mContext, vcard);
                }
                mWorkingMessage.setPublicAccountUuid(mPublicAccountUuid);
                mWorkingMessage.setRcsThreadId(mServiceThreadId);
                mWorkingMessage.setRcsMessageType(
                        Constants.MessageConstants.CONST_MESSAGE_CONTACT);
                PASendMessageUtil.sendPublicMessage(mContext, mWorkingMessage);
                break;
            case REQUEST_CODE_ATTACH_MAP:
                if (data == null)
                    return;
                double latitude = data.getDoubleExtra("latitude", 39.90865);
                double longitude = data.getDoubleExtra("longitude", 116.39751);
                String info = data.getStringExtra("address");
                mWorkingMessage.setRcsMessageType(Constants.MessageConstants.CONST_MESSAGE_MAP);
                mWorkingMessage.setLat(latitude);
                mWorkingMessage.setLon(longitude);
                mWorkingMessage.setMapInfo(info);
                mWorkingMessage.setPublicAccountUuid(mPublicAccountUuid);
                mWorkingMessage.setRcsThreadId(mServiceThreadId);
                PASendMessageUtil.sendPublicMessage(mContext, mWorkingMessage);
                break;
            case REQUEST_SELECT_GROUP:
            case REQUEST_CODE_RCS_PICK:
                if (data != null) {
                    ArrayList<String> numbers = data
                            .getStringArrayListExtra("recipients");
                    PASendMessageUtil.forwardRcsMessageToNumber(this, mConversationAdapter
                            .getSelectedList().get(0), numbers);
                }
                break;
            case REQUEST_SELECT_CONV:
                if (data != null) {
                    PASendMessageUtil.forwardRcsMessageToConversation(this, data,
                            mConversationAdapter.getSelectedList().get(0));
                }
                break;
            case REQUEST_SELECT_PUBLIC_ACCOUNT:
                if (data != null) {
                    String uuid = data.getStringExtra("selectPublicId");
                    PASendMessageUtil.forwardRcsMessageToPublicAccount(mContext, uuid,
                            mConversationAdapter.getSelectedList().get(0));
                }
                break;
            case REQUEST_CODE_START_DETAIL_ACTIVITY:
                if (resultCode == RESULT_OK) {
                    mAccount = data.getParcelableExtra("publicaccountdetail");
                    if (mAccount.getSubscribeStatus() == 0) {
                        mUnfollow = true;
                        hideBottomLayout();
                    }else {
                        mUnfollow = false;
                        showBottomLayout();
                    }
                    invalidateOptionsMenu();
                }
                break;
            default:
                break;
        }
    }

    private void hideBottomLayout(){
        if (mButtomLayout.getVisibility() == View.VISIBLE) {
            mButtomLayout.setVisibility(View.GONE);
        }
    }

    private void showBottomLayout(){
        if (mButtomLayout.getVisibility() != View.VISIBLE) {
            mButtomLayout.setVisibility(View.VISIBLE);
        }
    }

    private void ImageDispose(final String photoPath) {
        String[] imageItems = getResources().getStringArray(R.array.del_image_mode);
        new AlertDialog.Builder(PAConversationActivity.this).setTitle(R.string.del_image_action)
                .setItems(imageItems, new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case SELECT_CUT_IMAGE:
                                File mCurrentPhotoFile = new File(photoPath);
                                doCropPhoto(mCurrentPhotoFile);
                                dialog.dismiss();
                                break;
                            case SELECT_ZOOM_IMAGE:
                                showQualityDialog(photoPath);
                                break;
                            case SELECT_NOT_CHANGED_IMAGE:
                                mWorkingMessage.setRcsFilePath(photoPath);
                                mWorkingMessage.setPublicAccountUuid(mPublicAccountUuid);
                                mWorkingMessage.setRcsThreadId(mServiceThreadId);
                                mWorkingMessage.setRcsMessageType(
                                        Constants.MessageConstants.CONST_MESSAGE_IMAGE);
                                PASendMessageUtil.sendPublicMessage(mContext, mWorkingMessage);
                                dialog.dismiss();
                                break;
                            case SELECT_CANCEL:
                                dialog.dismiss();
                                break;
                            default:
                                break;
                        }
                    }
                }).create().show();
    }

    private static final int PHOTO_CROP = 10000;

    private void doCropPhoto(File file) {
        try {
            Intent intent = new Intent("com.android.camera.action.CROP");
            intent.setDataAndType(Uri.fromFile(file), "image/*");
            intent.putExtra("crop", true);
            startActivityForResult(intent, PHOTO_CROP);
        } catch (Exception e) {
            Toast.makeText(PAConversationActivity.this, R.string.not_intent, Toast.LENGTH_SHORT)
                    .show();
        }
    }

    private void showQualityDialog(final String photoPath) {
        final EditText editText = new EditText(PAConversationActivity.this);
        editText.setInputType(InputType.TYPE_CLASS_NUMBER);
        editText.setHint(R.string.please_input_1_100_int);
        editText.addTextChangedListener(new TextWatcher() {

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable s) {

                Pattern pattern = Pattern.compile("^(?:[0-9]?\\d|100|00[1-9])$");
                Matcher matcher = pattern.matcher(s);
                if (!matcher.find()) {
                    s.clear();
                    Toast.makeText(PAConversationActivity.this, R.string.input_no_fit,
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
        AlertDialog.Builder builder = new AlertDialog.Builder(PAConversationActivity.this);
        builder.setTitle(R.string.input_quality);
        builder.setView(editText);
        builder.setPositiveButton(R.string.send_comfirm_ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String quality = editText.getText().toString().trim();
                if (TextUtils.isEmpty(quality) || Long.parseLong(quality) > Integer.MAX_VALUE
                        || Integer.parseInt(quality) == 0 || Integer.parseInt(quality) > 100) {
                    Toast.makeText(PAConversationActivity.this, R.string.input_no_fit,
                            Toast.LENGTH_SHORT).show();
                } else {
                    mWorkingMessage.setRcsFilePath(photoPath);
                    mWorkingMessage.setPublicAccountUuid(mPublicAccountUuid);
                    mWorkingMessage.setRcsThreadId(mServiceThreadId);
                    mWorkingMessage.setRcsMessageType(
                            Constants.MessageConstants.CONST_MESSAGE_IMAGE);
                    mWorkingMessage.setRcsImageQuality(Integer.parseInt(quality));
                    PASendMessageUtil.sendPublicMessage(mContext, mWorkingMessage);

                }
            }
        });
        builder.setNegativeButton(R.string.send_comfirm_cancel, null);
        builder.create().show();
    }

    private onMenuBarClickListener menuBarListener = new onMenuBarClickListener() {
        @Override
        public void onActionClick(MenuInfo menuInfo) {
            switch (menuInfo.getType()) {
                case MessageMenuBar.TYPE_URL:
                    WebViewActivity.start(PAConversationActivity.this, menuInfo.getTitle(),
                            menuInfo.getCommandId());
                    break;
                case MessageMenuBar.TYPE_MSG:
                    try {
                        MessageApi.getInstance().sendCommandToPublicAccount(mPublicAccountUuid,
                                mServiceThreadId, menuInfo.getCommandId());
                    } catch (ServiceDisconnectedException e) {
                        e.printStackTrace();
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                    break;
                case MessageMenuBar.TYPE_DEVICE_API:
                    break;
                case MessageMenuBar.TYPE_APP:
                    break;
                default:
                    break;
            }
        }
    };

    // ActionBar menu <begin>
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.clear();
        if (PublicAccountUtils.isFollowByCard(mContext,mPublicAccountUuid)) {
            menu.add(0, MENU_ADD_ATTACHMENT, 0, null).setIcon(R.drawable.add_send_accessory)
            .setTitle(R.string.add_send_accessory)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            // menu.add(0, MENU_MORE, Menu.NONE, null).setIcon(R.drawable.more_icon)
            // .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            menu.add(0, MENU_PA_DETAILS, Menu.NONE, R.string.public_account_details);
            menu.add(0, MENU_REPORT_PUBLIC_ACCOUT, Menu.NONE, R.string.menu_report_public_account);
        } else {
            menu.add(0, MENU_PA_DETAILS, Menu.NONE, R.string.public_account_details);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                if (mUnfollow) {
                    toUnfollowPublicDetailActivity();
                }
                finish();
                return true;
            case MENU_PA_DETAILS:
                Intent intent = new Intent(ACTION_LUNCHER_RCS_PUBLIC_ACCPUNT);
                intent.putExtra("id", mPublicAccountUuid);
                intent.putExtra("avatar_url", mPublicAccountsDetail != null ? mPublicAccountsDetail.getLogoUrl():"");
                intent.putExtra("publicaccount", true);
                startActivityForResult(intent, REQUEST_CODE_START_DETAIL_ACTIVITY);
                return true;
            case MENU_ADD_ATTACHMENT:
                CommonUtil.closeKB((Activity)mContext);
                showAddAttachmentDialog(PAConversationActivity.this);
                return true;
            case MENU_REPORT_PUBLIC_ACCOUT:
                showComplainDialog(TYPE_REPORT_PUBLIC_ACCOUNT);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void showAddAttachmentDialog(Activity activity) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setIcon(R.drawable.add_send_accessory);
        builder.setTitle(R.string.add_send_accessory);
        final SelectorAdapter mAttachmentTypeSelectorAdapter = new SelectorAdapter(this);
        builder.setAdapter(mAttachmentTypeSelectorAdapter, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                addAttachment(mAttachmentTypeSelectorAdapter.buttonToCommand(which));
                dialog.dismiss();
            }
        });

        builder.show();
    }

    private void addAttachment(int type) {
        switch (type) {
            case SelectorAdapter.PA_ADD_IMAGE:
                PAMessageUtil.selectPAImage(this, REQUEST_CODE_ATTACH_IMAGE);
                break;

            case SelectorAdapter.PA_TAKE_PICTURE: {
//                PAMessageUtil.capturePicture(this, REQUEST_CODE_TAKE_PICTURE);
                capturePic(this, REQUEST_CODE_TAKE_PICTURE);
                break;
            }

            case SelectorAdapter.PA_ADD_VIDEO:
                PAMessageUtil.selectPAVideo(this, REQUEST_CODE_ATTACH_VIDEO);
                break;

            case SelectorAdapter.PA_RECORD_VIDEO:
                PAMessageUtil.recordPAVideo(this, REQUEST_CODE_RECORD_VIDEO);
                break;

            case SelectorAdapter.PA_ADD_SOUND:
                PAMessageUtil.selectPAAudio(this, REQUEST_CODE_ATTACH_SOUND);
                break;

            case SelectorAdapter.PA_RECORD_SOUND:
                PAMessageUtil.recordPASound(this, REQUEST_CODE_RECORD_SOUND);
                break;

            case SelectorAdapter.PA_ADD_CONTACT_VCARD:
                vcardContactOrGroup(new sendVcardClickListener());
                break;

            case SelectorAdapter.PA_ADD_MAP:
                try {
                    Intent intent = new Intent();
                    intent.setAction("com.suntek.mway.rcs.MAP_POSITION_SELECT");
                    startActivityForResult(intent, REQUEST_CODE_ATTACH_MAP);
                } catch (ActivityNotFoundException e) {
                    e.printStackTrace();
                    Toast.makeText(this, getString(R.string.please_install_rcs_map),
                            Toast.LENGTH_LONG).show();
                }
                break;
            default:
                break;
        }
    }

    private void vcardContactOrGroup(OnClickListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(PAConversationActivity.this);
        builder.setCancelable(true);
        builder.setTitle(R.string.select_contact_group);
        builder.setItems(
                new String[] {
                        mContext.getString(R.string.forward_contact),
                        mContext.getString(R.string.forward_contact_group),
                        mContext.getString(R.string.my_vcard)
                }, listener);
        builder.show();
    }

    private int MODE_VCARD = 2;
    public static final int MODE_DEFAULT = 0;

    private String mPicturePath;

    private class sendVcardClickListener implements OnClickListener {
        public void onClick(DialogInterface dialog, int whichButton) {
            switch (whichButton) {
                case SELECT_CONTACT_VCARD:
                    pickPAContacts(MODE_VCARD, REQUEST_CODE_CONTACT_VCARD);
                    break;
                case SELECT_GROUP_VCARD:
                    pickPAContactGroup(MODE_DEFAULT, REQUEST_CODE_VCARD_GROUP);
                    break;
                case SELECT_SELF_VCARD:
                    String rawContactId = PAMessageUtil.getMyRcsRawContactId(mContext);
                    if (TextUtils.isEmpty(rawContactId)) {
                        Toast.makeText(mContext, R.string.please_set_my_profile, 0).show();
                        return;
                    }
                    Uri contactUri = ContentUris.withAppendedId(Contacts.CONTENT_URI,
                            Long.parseLong(rawContactId));
                    String lookup = Uri.encode(Contacts.getLookupUri(mContext.getContentResolver(),
                            contactUri).getPathSegments().get(2));
                    Uri uri = Uri.withAppendedPath(Contacts.CONTENT_VCARD_URI, lookup);
                    PAMessageUtil.setPAVcard(mContext, uri);
                    mWorkingMessage.setPublicAccountUuid(mPublicAccountUuid);
                    mWorkingMessage.setRcsThreadId(mServiceThreadId);
                    mWorkingMessage.setRcsMessageType(
                            Constants.MessageConstants.CONST_MESSAGE_CONTACT);
                    PASendMessageUtil.sendPublicMessage(mContext, mWorkingMessage);
                    break;
                default:
                    break;
            }
        }
    }

    private void pickPAContacts(int mode, int requestCode) {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT) {
            Intent intent = new Intent("com.android.mms.intent.action.ACTION_MULTI_PICK");
            intent.putExtra("mode", mode);
            startActivityForResult(intent, requestCode);
        } else {
            Intent intent = new Intent("com.android.mms.ui.SelectRecipientsList");
            intent.putExtra("mode", mode);
            startActivityForResult(intent, requestCode);
        }
    }

    private void pickPAContactGroup(int mode, int requestCode) {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT) {
            Intent intent = new Intent("com.android.mms.MultiPickContactGroups");
            startActivityForResult(intent, requestCode);
        } else {
            Intent intent = new Intent("com.android.mms.ui.SelectRecipientsList");
            intent.putExtra("mode", mode);
            startActivityForResult(intent, requestCode);
        }
    }

    // ActionBar menu <end>

    private void toast(int resId) {
        Toast.makeText(this, resId, Toast.LENGTH_LONG).show();
    }

    private void toast(String string) {
        Toast.makeText(this, string, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onPAScrollStateChanged(AbsListView view, int scrollState) {
        if (scrollState == OnScrollListener.SCROLL_STATE_IDLE) {
//            if (mMessageListView.getFirstVisiblePosition() == 1) {
//                refreshMessage(false);
//            }
        }
    }

    @Override
    public void onPAScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
            int totalItemCount) {
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
//        if (mConversationAdapter.getItemViewType(position - 1) == ConversationAdapter.VIEW_TYPE_RECEIVE_TOPIC) {
//            return;
//        }
        showMessageDetails(mConversationAdapter.getChatMessageList().get(position -1));
    }

    private void showMessageDetails(PublicMessageItem chatMsg) {
        if (mPublicAccountsDetail == null) {
            return;
        }
        String msgDetails = PAMessageUtil.getPAMsgDetails(this, mPublicAccountsDetail.getName(),
                chatMsg);
        new AlertDialog.Builder(PAConversationActivity.this)
                .setTitle(R.string.dialog_message_details_title).setMessage(msgDetails)
                .setCancelable(true).show();
    }

    private void showComplainDialog(final int complainType) {
        LinearLayout layout = new LinearLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        layout.setLayoutParams(lp);
        layout.setOrientation(1);
        layout.setPadding(10, 0, 10, 0);
        final EditText edit_description = new EditText(this);
        edit_description.setHint(R.string.report_description);
        final String[] reason_types = getResources().getStringArray(R.array.report_type);
        boolean[] bool_reasons = new boolean[reason_types.length];
        final String[] check_types = new String[reason_types.length];
        final StringBuilder str_types = new StringBuilder();
        layout.addView(edit_description,LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT);
        AlertDialog alertDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_title_complain)
                .setView(layout)
                .setPositiveButton(R.string.confirm, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface arg0, int arg1) {
                        if (str_types.length() == 0) {
                            toast(R.string.please_select_report_type);
                            return;
                        }
                        String descriptionStr = edit_description.getText().toString();
                        try {
                            PublicAccountApi.getInstance().complainPublic(mPublicAccountUuid,
                                    str_types.toString(), descriptionStr, complainType, "", callback);
                        } catch (ServiceDisconnectedException e) {
                            e.printStackTrace();
                        } catch (RemoteException e){
                            e.printStackTrace();
                        }
                    }
                }).setNegativeButton(R.string.cancel, null)
                .setMultiChoiceItems(reason_types, bool_reasons, new OnMultiChoiceClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                        if (isChecked) {
                            check_types[which] = reason_types[which];
                        } else {
                            check_types[which] = null;
                        }

                        for (String str : check_types) {
                            if (str != null) {
                                str_types.append(str + ",");
                            }
                        }
                        if (str_types.length() != 0) {
                            str_types.deleteCharAt(str_types.length() - 1);
                        }
                    }
                }).create();
        alertDialog.show();
    }

    private void confirmDeleteDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(true);
        builder.setMessage(R.string.confirm_delete_message);
        builder.setPositiveButton(R.string.delete, new OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (deleteMessages()) {
                   mConversationAdapter.notifyDataSetChanged();
                }
            }
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }

    private boolean deleteMessages() {
        long chatId = lastChatId;
        try {
            boolean success = false;
            int count = 0;
            List<PublicMessageItem> list = mConversationAdapter.getSelectedList();
            int size = list.size();
            long msgId = 0;
            PublicMessageItem chatMessage = null;
            for (int i = size - 1; i >= 0; i--) {
                chatMessage = list.get(i);
                msgId = chatMessage.getId();
                MessageApi.getInstance().deleteMessage(msgId);
                if (msgId == lastChatId) {
                    lastChatId = mConversationAdapter.getTheFrontChatId(msgId);
                }
                mConversationAdapter.removeMsg(chatMessage);
                count ++;
            }
            return count == size;
        } catch (Exception e) {
            e.printStackTrace();
            toast(R.string.delete_fail);
            lastChatId = chatId;
            return false;
        }
    }

    private void capturePic(Activity activity, int requestCode) {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(
                MediaStore.EXTRA_OUTPUT,
                CommonUtil.getOutputVideoFileUri(getPicturePath()));
        activity.startActivityForResult(intent, requestCode);
    }

    private String getPicturePath() {
        if (mPicturePath != null) {
            File file = new File(mPicturePath);
            if (file.exists()) {
                file.delete();
            }
        }
        mPicturePath = PAMessageUtil.getPAScrapPath(this, "picture/" + System.currentTimeMillis()
                + ".jpg");
        return mPicturePath;
    }

    @Override
    public void onBackPressed() {
        if (mUnfollow) {
            toUnfollowPublicDetailActivity();
        }
        super.onBackPressed();
    }

    private void toUnfollowPublicDetailActivity(){
        Intent intent = new Intent();
        intent.putExtra("unfollow", true);
        intent.putExtra("publicaccountdetail", mAccount);
        setResult(RESULT_OK, intent);
    }
}
