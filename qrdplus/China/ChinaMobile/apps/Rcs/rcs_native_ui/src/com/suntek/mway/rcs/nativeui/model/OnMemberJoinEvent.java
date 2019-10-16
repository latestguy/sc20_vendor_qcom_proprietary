/*
 * Copyright (c) 2014-2015 pci-suntektech Technologies, Inc.  All Rights Reserved.
 * pci-suntektech Technologies Proprietary and Confidential.
 */

package com.suntek.mway.rcs.nativeui.model;


public class OnMemberJoinEvent extends GroupChatNotifyEvent {

    private String memberNumber;

    public OnMemberJoinEvent(long groupId, String memberNumber) {
        this.groupId = groupId;
        this.memberNumber = memberNumber;
    }

    @Override
    public String getActionType() {
        //TODO changed this string to constants.
        return "connected";
    }

    public String getMemberNumber() {
        return memberNumber;
    }
}
