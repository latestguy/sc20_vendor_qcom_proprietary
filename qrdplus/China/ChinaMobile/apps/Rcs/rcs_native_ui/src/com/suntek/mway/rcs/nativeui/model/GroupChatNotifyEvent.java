/*
 * Copyright (c) 2015 pci-suntektech Technologies, Inc.  All Rights Reserved.
 * pci-suntektech Technologies Proprietary and Confidential.
 */

package com.suntek.mway.rcs.nativeui.model;

public abstract class GroupChatNotifyEvent {
    protected long groupId;

    public abstract String getActionType();

    public long getGroupId() {
        return groupId;
    };
}
