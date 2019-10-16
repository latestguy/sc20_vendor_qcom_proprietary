/*
 * Copyright (c) 2015 pci-suntektech Technologies, Inc.  All Rights Reserved.
 * pci-suntektech Technologies Proprietary and Confidential.
 */
package com.suntek.mway.rcs.publicaccount.data;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.BaseColumns;
import android.provider.Telephony.Sms;
import android.provider.Telephony.Sms.Conversations;

import com.suntek.mway.rcs.client.aidl.common.RcsColumns.SmsRcsColumns;
import com.suntek.mway.rcs.client.aidl.common.RcsColumns;
import com.suntek.mway.rcs.client.aidl.constant.Constants;
import com.suntek.mway.rcs.client.aidl.plugin.entity.pubacct.PublicAccountConstant;
import com.suntek.mway.rcs.client.aidl.plugin.entity.pubacct.PublicMediaMessage;
import com.suntek.mway.rcs.client.aidl.plugin.entity.pubacct.PublicMessage;
import com.suntek.mway.rcs.client.aidl.plugin.entity.pubacct.PublicTextMessage;
import com.suntek.mway.rcs.client.aidl.plugin.entity.pubacct.PublicTopicMessage;


public class PublicMessageItem implements Parcelable{
    public static final Uri PUBLIC_MESSAGE_URI = Sms.CONTENT_URI;

    public static final String[] PUBLIC_MESSAGE_PROJECTION = new String[] {
            BaseColumns._ID, Conversations.THREAD_ID, Sms.DATE,
            RcsColumns.SmsRcsColumns.RCS_MSG_TYPE, Sms.BODY, RcsColumns.SmsRcsColumns.RCS_PATH,
            RcsColumns.SmsRcsColumns.RCS_THUMB_PATH, Sms.TYPE,
            RcsColumns.SmsRcsColumns.RCS_MIME_TYPE, Sms.STATUS,
            RcsColumns.SmsRcsColumns.RCS_CHAT_TYPE, RcsColumns.SmsRcsColumns.RCS_FILENAME,
            RcsColumns.SmsRcsColumns.RCS_MSG_STATE, RcsColumns.SmsRcsColumns.PHONE_ID
    };
    public static int mPubMessageId       = 0;
    public static int mPubThreadId        = 1;
    public static int mPubMessageTime     = 2;
    public static int mPubMessageType     = 3;
    public static int mPubMessageBody     = 4;
    public static int mPubMessagePath     = 5;
    public static int mPubThumbPath       = 6;
    public static int mPubSendReceive     = 7;
    public static int mPubMessageMimeType = 8;
    public static int mPubMessageStatus   = 9;

    private static final int RECEIVE_NUMBERS = 1;

    private long mId;
    private long mMessageId;
    private long mMessageThreadId;
    private long mSendDate;
    private int mRcsMessageType;
    private String mMessageBody;
    private String mMessageFilePath;
    private String mThumbMessageFilePath;
    private int mSendReceive;
    private String mMessageMimeType;
    private int mMessageSendState;
    private int mMessagePlayTime;
    private long mMessageFileSize;
    private int mMessagePhoneId;

    /**
     * Instantiates a new public topic content.
     */
    public PublicMessageItem() {

    }

    /**
     * Instantiates a new public topic content.
     *
     * @param source the source
     */
    public PublicMessageItem(Parcel source) {
        readFromParcel(source);
    }

    /*
     * (non-Javadoc)
     * @see android.os.Parcelable#describeContents()
     */
    @Override
    public int describeContents() {
        return 0;
    }

    /*
     * (non-Javadoc)
     * @see android.os.Parcelable#writeToParcel(android.os.Parcel, int)
     */
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(mId);
        dest.writeLong(mMessageId);
        dest.writeLong(mMessageThreadId);
        dest.writeLong(mSendDate);
        dest.writeInt(mRcsMessageType);
        dest.writeString(mMessageBody);
        dest.writeString(mMessageFilePath);
        dest.writeString(mThumbMessageFilePath);
        dest.writeInt(mSendReceive);
        dest.writeString(mMessageMimeType);
        dest.writeInt(mMessageSendState);
        dest.writeInt(mMessagePlayTime);
        dest.writeLong(mMessageFileSize);
        dest.writeInt(mMessagePhoneId);
        dest.writeParcelable(mPublicMessage, flags);
        dest.writeParcelable(mPublicMediaMessage, flags);
        dest.writeParcelable(mPublicTextMessage, flags);
        dest.writeParcelable(mPublicTopicMessage, flags);
    }

    /**
     * Read from parcel.
     *
     * @param source the source
     */
    public void readFromParcel(Parcel source) {
        mId = source.readLong();
        mMessageId = source.readLong();
        mMessageThreadId = source.readLong();
        mSendDate = source.readLong();
        mRcsMessageType = source.readInt();
        mMessageBody = source.readString();
        mMessageFilePath = source.readString();
        mThumbMessageFilePath = source.readString();
        mSendReceive = source.readInt();
        mMessageMimeType = source.readString();
        mMessageSendState = source.readInt();
        mMessagePlayTime = source.readInt();
        mMessageFileSize = source.readLong();
        mMessagePhoneId = source.readInt();
        mPublicMessage = source.readParcelable(PublicMessage.class.getClassLoader());
        mPublicMediaMessage = source.readParcelable(PublicMediaMessage.class.getClassLoader());
        mPublicTextMessage = source.readParcelable(PublicTextMessage.class.getClassLoader());
        mPublicTopicMessage = source.readParcelable(PublicTopicMessage.class.getClassLoader());
    }

    /** The parcel creator. */
    public static final Parcelable.Creator<PublicMessageItem> CREATOR =
            new Parcelable.Creator<PublicMessageItem>() {
        @Override
        public PublicMessageItem createFromParcel(Parcel source) {
            return new PublicMessageItem(source);
        }

        @Override
        public PublicMessageItem[] newArray(int size) {
            return new PublicMessageItem[size];
        }
    };

    public long getId() {
        return mId;
    }

    public void setId(long id) {
        this.mId = id;
    }

    public long getmMessageFileSize() {
        return mMessageFileSize;
    }

    public void setmMessageFileSize(long mMessageFileSize) {
        this.mMessageFileSize = mMessageFileSize;
    }

    private PublicMessage mPublicMessage;
    private PublicMediaMessage mPublicMediaMessage;
    private PublicTextMessage mPublicTextMessage;
    private PublicTopicMessage mPublicTopicMessage;


    public PublicTopicMessage getPublicTopicMessage() {
        return mPublicTopicMessage;
    }

    public void setPublicTopicMessage(PublicTopicMessage publicTopicMessage) {
        this.mPublicTopicMessage = publicTopicMessage;
    }

    public PublicTextMessage getPublicTextMessage() {
        return mPublicTextMessage;
    }

    public void setPublicTextMessage(PublicTextMessage publicTextMessage) {
        this.mPublicTextMessage = publicTextMessage;
    }

    public PublicMediaMessage getPublicMediaMessage() {
        return mPublicMediaMessage;
    }

    public void setPublicMediaMessage(PublicMediaMessage publicMediaMessage) {
        this.mPublicMediaMessage = publicMediaMessage;
    }

    public PublicMessage getPublicMessage() {
        return mPublicMessage;
    }

    public void setPublicMessage(PublicMessage publicMessage) {
        this.mPublicMessage = publicMessage;
    }

    public int getMessagePlayTime() {
        return mMessagePlayTime;
    }

    public void setMessagePlayTime(int messagePlayTime) {
        this.mMessagePlayTime = messagePlayTime;
    }

    public int getMessageSendState() {
        return mMessageSendState;
    }

    public void setMessageSendState(int messageSendState) {
        this.mMessageSendState = messageSendState;
    }

    public String getMessageMimeType() {
        return mMessageMimeType;
    }

    public void setMessageMimeType(String messageMimeType) {
        this.mMessageMimeType = messageMimeType;
    }

    public String getThumbMessageFilePath() {
        return mThumbMessageFilePath;
    }

    public void setThumbMessageFilePath(String thumbMessageFilePath) {
        this.mThumbMessageFilePath = thumbMessageFilePath;
    }

    public int getSendReceive() {
        return mSendReceive;
    }

    public void setSendReceive(int sendReceive) {
        this.mSendReceive = sendReceive;
    }

    public long getMessageId() {
        return mMessageId;
    }

    public void setMessageId(long messageId) {
        this.mMessageId = messageId;
    }

    public long getMessageThreadId() {
        return mMessageThreadId;
    }

    public void setMessageThreadId(long messageThreadId) {
        this.mMessageThreadId = messageThreadId;
    }

    public long getSendDate() {
        return mSendDate;
    }

    public void setSendDate(long sendDate) {
        this.mSendDate = sendDate;
    }

    public int getRcsMessageType() {
        return mRcsMessageType;
    }

    public void setRcsMessageType(int rcsMessageType) {
        this.mRcsMessageType = rcsMessageType;
    }

    public String getMessageBody() {
        return mMessageBody;
    }

    public void setMessageBody(String messageBody) {
        this.mMessageBody = messageBody;
    }

    public String getMessageFilePath() {
        return mMessageFilePath;
    }

    public void setMessageFilePath(String messageFilePath) {
        this.mMessageFilePath = messageFilePath;
    }

    public int getMessagePhoneId() {
        return mMessagePhoneId;
    }

    public void setMessagePhoneId(int messagePhoneId) {
        this.mMessagePhoneId = messagePhoneId;
    }

    public static boolean isPublicMessageCanForward(PublicMessageItem publicMessageItem){
        if (publicMessageItem.getSendReceive() ==
                Constants.MessageConstants.CONST_DIRECTION_SEND) {
            return true;
        } else {
            PublicMessage publicMessage = publicMessageItem.getPublicMessage();
            if (publicMessage.getForwardable() == PublicAccountConstant.MESSAGE_FORWARD_ABLE) {
                return true;
            }
        }
        return false;
    }

    public static boolean isReceiveMessage(PublicMessageItem publicMessageItem){
        boolean isReceiveMsg = false;
        if (publicMessageItem.getSendReceive() == RECEIVE_NUMBERS){
            isReceiveMsg = true;
        } else {
            isReceiveMsg = false;
        }
        return isReceiveMsg;
    }
}
