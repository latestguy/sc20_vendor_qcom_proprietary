/*
 * Copyright (c) 2014-2015 pci-suntektech Technologies, Inc.  All Rights Reserved.
 * pci-suntektech Technologies Proprietary and Confidential.
 */

package com.suntek.mway.rcs.nativeui.model;

public class OnOverMaxCountEvent extends GroupChatNotifyEvent {

    private long mThreadId;
    private int mMaxCount;

    public OnOverMaxCountEvent(long groupId, long threadId, int maxCount) {
        this.groupId = groupId;
        mThreadId = threadId;
        mMaxCount = maxCount;
    }

    @Override
    public String getActionType() {
        //TODO change this string to constants.
        return "overMaxCount";
    }

    public long getThreadId() {
        return mThreadId;
    }

    public int getMaxCount() {
        return mMaxCount;
    }
}
