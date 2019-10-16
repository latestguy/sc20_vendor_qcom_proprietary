/*
 * Copyright (c) 2014-2015 pci-suntektech Technologies, Inc.  All Rights Reserved.
 * pci-suntektech Technologies Proprietary and Confidential.
 */

package com.suntek.mway.rcs.nativeui.ui;

import android.content.Context;
import android.os.RemoteException;
import android.util.Log;

import com.suntek.mway.rcs.client.api.basic.BasicApi;
import com.suntek.mway.rcs.client.api.exception.ServiceDisconnectedException;
import com.suntek.mway.rcs.nativeui.R;
import com.suntek.mway.rcs.nativeui.service.RcsNotificationList;
import com.suntek.mway.rcs.nativeui.service.RcsNotificationsService;

public class RcsGroupChatAssignAsChairmanNotification extends RcsNotification {
    private String subject;
    private String contributionId;
    private String conversationId;
    private String chatUri;
    private long inviteTime;

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getContributionId() {
        return contributionId;
    }

    public void setContributionId(String contributionId) {
        this.contributionId = contributionId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getChatUri() {
        return chatUri;
    }

    public void setChatUri(String chatUri) {
        this.chatUri = chatUri;
    }

    public long getInviteTime() {
        return inviteTime;
    }

    public void setInviteTime(long inviteTime) {
        this.inviteTime = inviteTime;
    }

    @Override
    public String getText(Context context) {
        return context.getString(R.string.invite_group_chat_chairman);
    }

    @Override
    public void onPositiveButtonClicked() {
        try {
            BasicApi accountApi = BasicApi.getInstance();
            if (!accountApi.isOnline()) {
                Log.d("RCS_UI", "acceptAssignedAsChairman() aborded due to RCS offline");
                return;
            }

            RcsNotificationList.getInstance().remove(this);
        } catch (ServiceDisconnectedException e) {
            e.printStackTrace();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onNegativeButtonClicked() {
        try {
            BasicApi accountApi = BasicApi.getInstance();
            if (!accountApi.isOnline()) {
                Log.d("RCS_UI", "refuseAssigedAsChairman() aborded due to RCS offline");
                return;
            }

            RcsNotificationList.getInstance().remove(this);
        } catch (ServiceDisconnectedException e) {
            e.printStackTrace();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean getIsChairmanChange() {
        return false;
    }
}
