/*
 * Copyright (c) 2014-2015 pci-suntektech Technologies, Inc.  All Rights Reserved.
 * pci-suntektech Technologies Proprietary and Confidential.
 */

package com.suntek.mway.rcs.publicaccount.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.Telephony.MmsSms;
import android.text.TextUtils;

import com.suntek.mway.rcs.client.aidl.plugin.entity.pubacct.PublicAccountsDetail;
import com.suntek.mway.rcs.client.api.exception.ServiceDisconnectedException;
import com.suntek.mway.rcs.client.api.message.MessageApi;
import com.suntek.mway.rcs.client.api.publicaccount.PublicAccountCallback;
import com.suntek.mway.rcs.client.api.publicaccount.PublicAccountApi;
import com.suntek.mway.rcs.nativeui.RcsNativeUIApp;

public class RcsMessageReceiver extends BroadcastReceiver {

    private static final String RCS_MESSAGE_NOTIFY_ACTION = "com.suntek.mway.rcs.ACTION_UI_SHOW_MESSAGE_NOTIFY";
    private static final Uri THREAD_URI = MmsSms.CONTENT_CONVERSATIONS_URI;
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null) {
            if (RCS_MESSAGE_NOTIFY_ACTION.equals(intent.getAction())) {
                long threadId = intent.getLongExtra("threadId", 0);
                if (RcsNativeUIApp.getApplication().getNowThreadId() != threadId) {
                    String uuid = intent.getStringExtra("contact");
                    loadPublicAccountDetail(uuid, threadId);
                } else {
                    try {
                        MessageApi.getInstance().markMessageAsReaded(threadId);
                    } catch (ServiceDisconnectedException e) {
                        e.printStackTrace();
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                    RcsNativeUIApp.getApplication().vibrator(300);
                }
            }
        }
    }

    private void loadPublicAccountDetail(String uuid, final long threadId) {
        try {
            //TODO call back not have???
            PublicAccountApi.getInstance().getPublicDetail(uuid, null);
        } catch (ServiceDisconnectedException e) {
            e.printStackTrace();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private class RcsPublicAccountCallback extends PublicAccountCallback{
        private long mThreadId;

        public RcsPublicAccountCallback(long threadId){
            mThreadId = threadId;
        }

        @Override
        public void respGetPublicDetail(boolean arg0, final PublicAccountsDetail arg1)
                throws RemoteException {
            try {
                PublicAccountApi.getInstance().unregisterCallback(RcsPublicAccountCallback.this);
            } catch (ServiceDisconnectedException e) {
                e.printStackTrace();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            if (arg1 != null) {
                RcsNotifyManager.getInstance().showNewMessageNotif(arg1, mThreadId + "",
                        true);
            }
        }
    }

}
