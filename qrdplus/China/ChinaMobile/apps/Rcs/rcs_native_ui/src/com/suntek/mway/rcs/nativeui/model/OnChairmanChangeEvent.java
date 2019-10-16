/*
 * Copyright (c) 2014-2015 pci-suntektech Technologies, Inc.  All Rights Reserved.
 * pci-suntektech Technologies Proprietary and Confidential.
 */

package com.suntek.mway.rcs.nativeui.model;


public class OnChairmanChangeEvent extends GroupChatNotifyEvent {

    private String chairmanNumber;

    public OnChairmanChangeEvent(long groupId, String chaimanNumber) {
        this.chairmanNumber = chaimanNumber;
    }
    @Override
    public String getActionType() {
        //TODO change this string to constants
        return "updateChairman";
    }

    public String getChairmanNumber() {
        return chairmanNumber;
    }


}
