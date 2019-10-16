/*
 * Copyright (c) 2014-2015 pci-suntektech Technologies, Inc.  All Rights Reserved.
 * pci-suntektech Technologies Proprietary and Confidential.
 */

package com.suntek.mway.rcs.nativeui.service;

import java.util.ArrayList;
import java.util.List;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;

import com.suntek.mway.rcs.client.aidl.constant.Actions;
import com.suntek.mway.rcs.client.aidl.constant.Actions.GroupChatAction;
import com.suntek.mway.rcs.client.aidl.constant.Constants.GroupChatConstants;
import com.suntek.mway.rcs.client.aidl.constant.Parameter;
import com.suntek.mway.rcs.client.aidl.service.entity.GroupChatMember;
import com.suntek.mway.rcs.client.api.exception.ServiceDisconnectedException;
import com.suntek.mway.rcs.client.api.basic.BasicApi;
import com.suntek.mway.rcs.client.aidl.service.entity.GroupChat;
import com.suntek.mway.rcs.client.api.groupchat.GroupChatApi;
import com.suntek.mway.rcs.nativeui.R;
import com.suntek.mway.rcs.nativeui.model.GroupChatNotifyEvent;
import com.suntek.mway.rcs.nativeui.model.OnAliasUpdateEvent;
import com.suntek.mway.rcs.nativeui.model.OnChairmanChangeEvent;
import com.suntek.mway.rcs.nativeui.model.OnDisbandEvent;
import com.suntek.mway.rcs.nativeui.model.OnGroupDeletedEvent;
import com.suntek.mway.rcs.nativeui.model.OnMemberJoinEvent;
import com.suntek.mway.rcs.nativeui.model.OnMemberKickedEvent;
import com.suntek.mway.rcs.nativeui.model.OnMemberQuitEvent;
import com.suntek.mway.rcs.nativeui.model.OnOverMaxCountEvent;
import com.suntek.mway.rcs.nativeui.model.OnReferErrorEvent;
import com.suntek.mway.rcs.nativeui.model.OnRemarkChangeEvent;
import com.suntek.mway.rcs.nativeui.model.OnSubjectChangeEvent;
import com.suntek.mway.rcs.nativeui.ui.RcsGroupChatAssignAsChairmanNotification;
import com.suntek.mway.rcs.nativeui.ui.RcsGroupChatInviteNotification;
import com.suntek.mway.rcs.nativeui.utils.RcsContactUtils;

public class RcsNotificationsService extends Service {
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        IntentFilter filter = new IntentFilter();
        filter.addAction(GroupChatAction.ACTION_GROUP_CHAT_MANAGE_NOTIFY);
        filter.addAction(GroupChatAction.ACTION_GROUP_CHAT_INVITE);
        filter.addAction(GroupChatAction.ACTION_GROUP_CHAT_MANAGE_FAILED);
        filter.addAction("com.suntek.mway.rcs.ACTION_UI_JOIN_GROUP_INVITE_TIMEOUT");
        filter.addAction("com.suntek.mway.rcs.ACTION_UI_SHOW_GROUP_REFER_ERROR");
        registerReceiver(mRcsServiceCallbackReceiver, filter);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    private boolean isChairman(List<GroupChatMember> list) throws ServiceDisconnectedException,
            RemoteException{
        // Get my phone number.
        String myPhoneNumber = BasicApi.getInstance().getAccount();
        if (TextUtils.isEmpty(myPhoneNumber)) {
            return false;
        }
        // Find 'me' in the group.
        boolean isChairman = false;
        for (GroupChatMember user : list) {
            if (myPhoneNumber.endsWith(user.getNumber())) {
                if (GroupChatMember.CHAIRMAN == user.getRole()) {
                    isChairman = true;
                }
                break;
            }
        }
        return isChairman;
    }

    private BroadcastReceiver mRcsServiceCallbackReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.i("RCS_UI", "onReceive(): action=" + action);
            if (GroupChatAction.ACTION_GROUP_CHAT_MANAGE_NOTIFY.equals(action)
                    || GroupChatAction.ACTION_GROUP_CHAT_MANAGE_FAILED.equals(action)) {
                int actionType = intent.getIntExtra(Parameter.EXTRA_TYPE, -1);
                if (GroupChatConstants.CONST_CREATE == actionType) {
                    long groupId = intent.getLongExtra(Parameter.EXTRA_GROUP_CHAT_ID, -1);
                    long threadId = intent.getLongExtra(Parameter.EXTRA_THREAD_ID, -1);
                    RcsNotificationList.getInstance().removeInviteNotificationByChatUri(groupId);
                    try {
                        GroupChat model = GroupChatApi.getInstance()
                                .getGroupChatByThreadId(threadId);

                        if (model != null) {
                            String groupTitle = TextUtils.isEmpty(model
                                    .getRemark()) ? model.getSubject()
                                    : model.getRemark();
                            RcsContactUtils.insertGroupChat(context, groupId, groupTitle);
                        }
                    } catch (ServiceDisconnectedException e) {
                        Log.i("RCS_UI", "GroupChatMessage" + e);
                    } catch (RemoteException e) {
                        Log.i("RCS_UI", "GroupChatMessage" + e);
                    }
                } else if (GroupChatConstants.CONST_SET_CHAIRMAN == actionType) {
                    long groupId = intent.getLongExtra(Parameter.EXTRA_GROUP_CHAT_ID, -1);
                    long threadId = intent.getLongExtra(Parameter.EXTRA_THREAD_ID, -1);
                    try {
                        GroupChat model = GroupChatApi.getInstance()
                                .getGroupChatByThreadId(threadId);
                        if (model != null) {
                            RcsGroupChatInviteNotification notification =
                                    new RcsGroupChatInviteNotification();
                            notification.setGroupId(groupId);
                            notification.setInviteTime(System.currentTimeMillis());
                            notification.setSubject(model.getSubject());
                            notification.setIsChairmanChange(true);
                            boolean isChairman = isChairman(GroupChatApi.getInstance()
                                    .getMembers(threadId));
                            if (isChairman) {
                                notification.setNumberData(context.getString(
                                        R.string.chairman_change_to_me,
                                        model.getSubject()));
                            } else {
                                notification.setNumberData(context.getString(
                                        R.string.chairman_change_notification,
                                        model.getSubject()));
                            }
                            RcsNotificationList.getInstance().add(notification);
                        }
                    } catch (ServiceDisconnectedException e) {
                        e.printStackTrace();
                    } catch (RemoteException e){
                        e.printStackTrace();
                    }
                }
                onConferenceManage(context, intent, actionType);
            } else if (Actions.GroupChatAction.ACTION_GROUP_CHAT_INVITE.equals(action)) {
                long groupId = intent.getLongExtra(Parameter.EXTRA_GROUP_CHAT_ID, -1);
                long threadId = intent.getLongExtra(Parameter.EXTRA_THREAD_ID, -1);
                String subject = intent.getStringExtra(Parameter.EXTRA_SUBJECT);
                String inviteNumber = intent.getStringExtra(Parameter.EXTRA_INVITE_NUMBER);
                RcsGroupChatInviteNotification notification = new RcsGroupChatInviteNotification();
                notification.setGroupId(groupId);
                notification.setInviteTime(System.currentTimeMillis());
                notification.setSubject(subject);
                notification.setInviteNumber(inviteNumber);
                RcsNotificationList.getInstance().add(notification);
            }
        }

        private void onConferenceManage(Context context, Intent intent, int actionType) {
            long groupId = intent.getLongExtra(Parameter.EXTRA_GROUP_CHAT_ID, -1);
            GroupChatNotifyEvent event = null;
            if (GroupChatAction.ACTION_GROUP_CHAT_MANAGE_FAILED.equals(intent.getAction())) {
                int type = intent.getIntExtra(Parameter.EXTRA_TYPE, -1);
                long threadId = intent.getLongExtra(Parameter.EXTRA_THREAD_ID, -1);
                long groupid = intent.getLongExtra(Parameter.EXTRA_GROUP_CHAT_ID, -1);
                int errorCode = intent.getIntExtra(Parameter.EXTRA_CODE, -1);
                String subject = intent.getStringExtra(Parameter.EXTRA_SUBJECT);
                String reason = intent.getStringExtra(Parameter.EXTRA_DESC);
                event = new OnReferErrorEvent(groupid, type,
                        subject, threadId, errorCode, reason);

            } else {
                if (GroupChatConstants.CONST_SET_SUBJECT == actionType) {

                    String newSubject = intent.getStringExtra(Parameter.EXTRA_SUBJECT);
                    event = new OnSubjectChangeEvent(groupId, newSubject);

                } else if (GroupChatConstants.CONST_SET_REMARK == actionType) {

                    String remark = intent.getStringExtra(Parameter.EXTRA_REMARK);
                    event = new OnRemarkChangeEvent(groupId, remark);
                    if (!TextUtils.isEmpty(remark)) {
                        RcsContactUtils.UpdateGroupChatSubject(context, groupId, remark);
                    }
                } else if (GroupChatConstants.CONST_SET_ALIAS == actionType) {
                    String phoneNumber = intent.getStringExtra(Parameter.EXTRA_NUMBER);
                    String alias = intent.getStringExtra(Parameter.EXTRA_ALIAS);
                    event = new OnAliasUpdateEvent(groupId, phoneNumber, alias);
                } else if (GroupChatConstants.CONST_SET_CHAIRMAN == actionType) {
                    String number = intent.getStringExtra(Parameter.EXTRA_NUMBER);
                    event = new OnChairmanChangeEvent(groupId, number);
                } else if (GroupChatConstants.CONST_DISBAND == actionType) {
                    event = new OnDisbandEvent(groupId);
                    if (groupId > 0) {
                        RcsContactUtils.deleteGroupChat(context, groupId);
                    }
                } else if (GroupChatConstants.CONST_QUIT == actionType) {
                    String member = intent.getStringExtra(Parameter.EXTRA_NUMBER);
                    event = new OnMemberQuitEvent(groupId, member);
                    if (groupId > 0) {
                        RcsContactUtils.deleteGroupChat(context, groupId);
                    }
                } else if (GroupChatConstants.CONST_BOOTED == actionType) {
                    String member = intent.getStringExtra(Parameter.EXTRA_NUMBER);
                    event = new OnMemberKickedEvent(groupId, member);
                    try {
                        String myPhoneNumber = BasicApi.getInstance().getAccount();
                        if (!TextUtils.isEmpty(myPhoneNumber)
                                && !TextUtils.isEmpty(member)) {
                            if (myPhoneNumber.endsWith(member) && groupId > 0) {
                                RcsContactUtils.deleteGroupChat(context, groupId);
                            }
                        }
                    } catch(ServiceDisconnectedException e) {
                        Log.i("RCS_UI", "OnMemberKickedEvent" + e);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                } else if (GroupChatConstants.CONST_JOIN == actionType) {
                    String member = intent.getStringExtra(Parameter.EXTRA_NUMBER);
                    event = new OnMemberJoinEvent(groupId, member);
                } else if (GroupChatConstants.CONST_QUIT == actionType) {
                    event = new OnGroupDeletedEvent(groupId);
                    if (groupId > 0) {
                        RcsContactUtils.deleteGroupChat(context, groupId);
                    }
                } else if (GroupChatConstants.CONST_REACHED_MAX_COUNT == actionType) {

                    // TODO:should popup to remaind user reached max count.


                }  else if (GroupChatConstants.CONST_SET_MAX_COUNT == actionType) {

                    long threadId = intent.getLongExtra(Parameter.EXTRA_THREAD_ID, -1);
                    int maxCount = intent.getIntExtra(Parameter.EXTRA_MAX_COUNT, 0);
                    event = new OnOverMaxCountEvent(groupId, threadId, maxCount);
                }
            }
            RcsConferenceListener.getInstance().notifyChanged(groupId, event);
        }
    };
}
