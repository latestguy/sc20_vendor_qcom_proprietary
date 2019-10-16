/*
 * Copyright (c) 2014-2015 pci-suntektech Technologies, Inc.  All Rights Reserved.
 * pci-suntektech Technologies Proprietary and Confidential.
 */

package com.suntek.mway.rcs.nativeui.model;

public class OnMemberQuitEvent extends GroupChatNotifyEvent {

    private String memberNumber;

    public OnMemberQuitEvent(long groupId, String memberNumber){
        this.groupId =groupId;
        this.memberNumber = memberNumber;
    }
    @Override
    public String getActionType() {
        //TODO change this string to constant
        return "departed";
    }

    public String getMemberNumber() {
        return memberNumber;
    }


}
