/*
 * Copyright (c) 2014-2015 pci-suntektech Technologies, Inc.  All Rights Reserved.
 * pci-suntektech Technologies Proprietary and Confidential.
 */

package com.suntek.mway.rcs.nativeui.ui;

import android.content.Context;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;

import com.suntek.mway.rcs.client.api.exception.ServiceDisconnectedException;
import com.suntek.mway.rcs.client.api.basic.BasicApi;
import com.suntek.mway.rcs.client.api.groupchat.GroupChatApi;
import com.suntek.mway.rcs.nativeui.R;
import com.suntek.mway.rcs.nativeui.service.RcsNotificationList;

public class RcsGroupChatInviteNotification extends RcsNotification {

    private long groupId;
    private String subject;
    private String contributionId;
    private String conversationId;
    private String chatUri;
    private String numberData;
    private String inviteNumber;
    private long inviteTime;
    private boolean isChairmanChange;

    public void setIsChairmanChange(boolean isChairmanChange) {
        this.isChairmanChange = isChairmanChange;
    }

    public long getGroupId() {
        return groupId;
    }

    public void setGroupId(long groupId) {
        this.groupId = groupId;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getNumberData() {
        return numberData;
    }

    public void setNumberData(String numberData) {
        this.numberData = numberData;
    }

    public long getInviteTime() {
        return inviteTime;
    }

    public void setInviteTime(long inviteTime) {
        this.inviteTime = inviteTime;
    }

    public void setInviteNumber(String number) {
        this.inviteNumber = number;
    }

    public String getInviteNumber() {
        return this.inviteNumber;
    }

    public void agreeToJoinGroup() throws ServiceDisconnectedException, RemoteException {
        //TODO need ThreadId TO replace -1
        GroupChatApi.getInstance().acceptToJoin(groupId, getInviteNumber());
    }

    public void refuseToJoinGroup() throws ServiceDisconnectedException, RemoteException {
        GroupChatApi.getInstance().rejectToJoin(groupId, getInviteNumber());
    }

    @Override
    public String getText(Context context) {
        if (isChairmanChange) {
            return getNumberData();
        } else {
            StringBuilder textString = new StringBuilder();
            textString.append(getInviteNumber());
            textString.append("\n");
            textString.append(context.getString(R.string.invite_join_group_chat));
            textString.append(getSubject());
            return textString.toString();
        }
    }

    @Override
    public boolean getIsChairmanChange(){
        return isChairmanChange;
    }

    @Override
    public void onPositiveButtonClicked() {
        try {
            BasicApi accountApi = BasicApi.getInstance();
            if (!accountApi.isOnline()) {
                Log.d("RCS_UI", "agreeToJoinGroup() aborded due to RCS offline");
                return;
            }

            agreeToJoinGroup();
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
            // Refuse to join the group chat.
            refuseToJoinGroup();

            // Delete notification.
            RcsNotificationList.getInstance().remove(this);
        } catch (ServiceDisconnectedException e) {
            e.printStackTrace();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }
}
