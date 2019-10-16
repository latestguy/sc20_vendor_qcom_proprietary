/*
 * Copyright (c) 2014-2015 pci-suntektech Technologies, Inc.  All Rights Reserved.
 * pci-suntektech Technologies Proprietary and Confidential.
 */

package com.suntek.mway.rcs.nativeui.model;


public class OnAliasUpdateEvent extends GroupChatNotifyEvent {

    private String phoneNumber;
    private String alias;

    public OnAliasUpdateEvent(long groupId, String phoneNumber, String alias) {
        this.phoneNumber = phoneNumber;
        this.alias = alias;
    }

    @Override
    public String getActionType() {
        //TODO change this string to constants.
        return "updateAlias";
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getAlias() {
        return alias;
    }

}
