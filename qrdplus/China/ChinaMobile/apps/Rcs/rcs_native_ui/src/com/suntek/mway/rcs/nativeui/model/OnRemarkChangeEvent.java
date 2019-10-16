/*
 * Copyright (c) 2014-2015 pci-suntektech Technologies, Inc.  All Rights Reserved.
 * pci-suntektech Technologies Proprietary and Confidential.
 */

package com.suntek.mway.rcs.nativeui.model;

public class OnRemarkChangeEvent extends GroupChatNotifyEvent {

    private String remark;

    public OnRemarkChangeEvent(long groupId, String remark) {
        this.remark = remark;
    }
    @Override
    public String getActionType() {
        //TODO change this string to constant
        return "updateRemark";
    }

    public String getRemark() {
        return remark;
    }




}
