/*
 * Copyright (c) 2015 pci-suntektech Technologies, Inc.  All Rights Reserved.
 * pci-suntektech Technologies Proprietary and Confidential.
 */
package com.suntek.mway.rcs.publicaccount.data;

import com.suntek.mway.rcs.client.aidl.constant.Constants;
import com.suntek.mway.rcs.client.aidl.plugin.entity.pubacct.PublicMessage;
import com.suntek.mway.rcs.client.aidl.plugin.entity.pubacct.PublicTextMessage;
import com.suntek.mway.rcs.client.api.message.MessageApi;
import com.suntek.mway.rcs.publicaccount.ui.PASendMessageUtil;
import com.suntek.rcs.ui.common.RcsLog;

public class PublicWorkingMessage {

    private String mRcsTextMessage;
    private String mRcsFilePath;
    private String mPublicAccountUuid;
    private long mRcsThreadId;
    private int mRcsRecordTime;
    private boolean mRcsIsRecord;
    private int mRcsMessageType;
    private int mRcsImageQuality;
    private double mLat;
    private double mLon;
    private String mMapInfo;

    public PublicWorkingMessage(){
    }

    public static PublicWorkingMessage getWorkingMessageFromPublicMessageItem(
            PublicMessageItem messageItem) {
        PublicWorkingMessage workingMessage = new PublicWorkingMessage();
        int msgType = messageItem.getRcsMessageType();
        workingMessage.setRcsMessageType(msgType);
        if (msgType == Constants.MessageConstants.CONST_MESSAGE_TEXT) {
            PublicMessage paMsg = null;
            try {
                paMsg = MessageApi.getInstance().parsePublicMessage(msgType,
                        messageItem.getMessageBody());
            } catch (Exception e) {
                RcsLog.w(e);
            }
            PublicTextMessage tMsg = (PublicTextMessage)paMsg;
            String text = (tMsg != null) ? tMsg.getContent() : "";
            workingMessage.setRcsTextMessage(text);
        } else if (msgType == Constants.MessageConstants.CONST_MESSAGE_IMAGE){
            workingMessage.setRcsFilePath(messageItem.getMessageFilePath());
            workingMessage.setRcsImageQuality(PASendMessageUtil.RCS_IMAGE_QUALITY);
            workingMessage.setRcsIsRecord(false);
        } else if(msgType == Constants.MessageConstants.CONST_MESSAGE_AUDIO ||
                msgType == Constants.MessageConstants.CONST_MESSAGE_VIDEO) {
            workingMessage.setRcsFilePath(messageItem.getMessageFilePath());
            workingMessage.setRcsIsRecord(false);
            workingMessage.setRcsRecordTime(messageItem.getMessagePlayTime());
        } else if (msgType == Constants.MessageConstants.CONST_MESSAGE_CONTACT) {
            workingMessage.setRcsFilePath(messageItem.getMessageFilePath());
        } else if(msgType == Constants.MessageConstants.CONST_MESSAGE_MAP){
            //TODO how to save mapinfo
        }
        return workingMessage;
    }


    public double getLat() {
        return mLat;
    }

    public void setLat(double lat) {
        this.mLat = lat;
    }

    public double getLon() {
        return mLon;
    }

    public void setLon(double lon) {
        this.mLon = lon;
    }

    public String getMapInfo() {
        return mMapInfo;
    }

    public void setMapInfo(String info) {
        this.mMapInfo = info;
    }

    public int getRcsImageQuality() {
        int RcsRealImageQuality = PASendMessageUtil.RCS_IMAGE_QUALITY;
        if (mRcsImageQuality > 0) {
            RcsRealImageQuality = mRcsImageQuality;
            mRcsImageQuality = 0;
        }
        return RcsRealImageQuality;
    }

    public void setRcsImageQuality(int mRcsImageQuality) {
        this.mRcsImageQuality = mRcsImageQuality;
    }

    public int getRcsMessageType() {
        return mRcsMessageType;
    }

    public void setRcsMessageType(int rcsMessageType) {
        this.mRcsMessageType = rcsMessageType;
    }

    public String getRcsTextMessage() {
        return mRcsTextMessage;
    }

    public void setRcsTextMessage(String rcsTextMessage) {
        this.mRcsTextMessage = rcsTextMessage;
    }

    public String getRcsFilePath() {
        return mRcsFilePath;
    }

    public void setRcsFilePath(String rcsFilePath) {
        this.mRcsFilePath = rcsFilePath;
    }

    public String getPublicAccountUuid() {
        return mPublicAccountUuid;
    }

    public void setPublicAccountUuid(String publicAccountUuid) {
        this.mPublicAccountUuid = publicAccountUuid;
    }

    public long getRcsThreadId() {
        return mRcsThreadId;
    }

    public void setRcsThreadId(long rcsThreadId) {
        this.mRcsThreadId = rcsThreadId;
    }

    public int getRcsRecordTime() {
        return mRcsRecordTime;
    }

    public void setRcsRecordTime(int rcsRecordTime) {
        this.mRcsRecordTime = rcsRecordTime;
    }

    public boolean isRcsIsRecord() {
        return mRcsIsRecord;
    }

    public void setRcsIsRecord(boolean rcsIsRecord) {
        this.mRcsIsRecord = rcsIsRecord;
    }
}
