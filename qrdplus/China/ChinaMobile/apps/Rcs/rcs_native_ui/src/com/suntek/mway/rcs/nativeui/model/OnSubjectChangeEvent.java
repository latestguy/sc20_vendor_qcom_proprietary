/*
 * Copyright (c) 2014-2015 pci-suntektech Technologies, Inc.  All Rights Reserved.
 * pci-suntektech Technologies Proprietary and Confidential.
 */

package com.suntek.mway.rcs.nativeui.model;


public class OnSubjectChangeEvent extends GroupChatNotifyEvent {

    private String newSubject;

    public OnSubjectChangeEvent(long groupId, String newSubject) {
        this.groupId = groupId;
        this.newSubject = newSubject;
    }

    @Override
    public String getActionType() {
        //TODO change this string to constants
        return "updateSubject";
    }

    public String getNewSubject() {
        return newSubject;
    }

}
