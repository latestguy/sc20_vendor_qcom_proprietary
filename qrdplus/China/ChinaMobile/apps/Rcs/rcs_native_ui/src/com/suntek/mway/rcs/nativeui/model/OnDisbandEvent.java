/*
 * Copyright (c) 2014-2015 pci-suntektech Technologies, Inc.  All Rights Reserved.
 * pci-suntektech Technologies Proprietary and Confidential.
 */

package com.suntek.mway.rcs.nativeui.model;


public class OnDisbandEvent extends GroupChatNotifyEvent {

    public OnDisbandEvent(long groupId) {
        this.groupId = groupId;
    }
    @Override
    public String getActionType() {
        //TODO change this string to constants
        return "deleted";
    }


}
