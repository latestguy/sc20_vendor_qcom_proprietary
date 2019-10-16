/*
 * Copyright (c) 2014-2015 pci-suntektech Technologies, Inc.  All Rights Reserved.
 * pci-suntektech Technologies Proprietary and Confidential.
 */

package com.suntek.mway.rcs.nativeui.model;

public class OnReferErrorEvent extends GroupChatNotifyEvent {

    public static final String ON_REFER_ERROR_ACTION = "ON_REFER_ERROR_ACTION";

    private long groupId;
    private long threadId;
    private int referErrorAction;
    private int errorCode;
    private String subject;
    private String reason;

    public OnReferErrorEvent(long groupId, int actionType,
            String subject, long threadId, int errorCode, String reason) {
        this.referErrorAction = actionType;
        this.groupId = groupId;
        this.threadId = threadId;
        this.subject = subject;
        this.errorCode = errorCode;
        this.reason = reason;
    }

    @Override
    public String getActionType() {
        return ON_REFER_ERROR_ACTION;
    }

    @Override
    public long getGroupId() {
        return super.getGroupId();
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getSubject() {
        return this.subject;
    }

    public void setThreadId(long threadId) {
        this.threadId = threadId;
    }

    public long getThreadId() {
        return this.threadId;
    }

    public void setErrorCode(int errorCode) {
        this.errorCode = errorCode;
    }

    public int getErrorCode() {
        return this.errorCode;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getReason() {
        return this.reason;
    }

    public int getReferErrorAction() {
        return referErrorAction;
    }

}
