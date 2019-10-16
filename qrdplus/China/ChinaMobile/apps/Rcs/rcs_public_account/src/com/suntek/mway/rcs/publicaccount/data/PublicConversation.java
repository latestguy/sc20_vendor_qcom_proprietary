/*
 * Copyright (c) 2015 pci-suntektech Technologies, Inc.  All Rights Reserved.
 * pci-suntektech Technologies Proprietary and Confidential.
 */
package com.suntek.mway.rcs.publicaccount.data;

import android.content.Context;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.Telephony.Threads;

import com.suntek.mway.rcs.client.aidl.common.RcsColumns;
import com.suntek.mway.rcs.client.aidl.plugin.entity.pubacct.PublicAccounts;



public class PublicConversation implements Parcelable {

    public static final Uri THREAD_URI = Uri.parse("content://mms-sms/conversations");

    public static final String[] THREADS_PROJECTION = {
        Threads._ID, Threads.DATE, Threads.MESSAGE_COUNT, Threads.RECIPIENT_IDS,
        Threads.SNIPPET, Threads.SNIPPET_CHARSET, Threads.READ, "rcs_msg_type",
    };

    public static final int ID             = 0;
    public static final int DATE           = 1;
    public static final int MESSAGE_COUNT  = 2;
    public static final int RECIPIENT_IDS  = 3;
    public static final int SNIPPET        = 4;
    public static final int SNIPPET_CS     = 5;
    public static final int READ           = 6;
    public static final int RCS_MSG_TYPE   = 7;

    private long mPublicAccountThreadId;
    private int mPublicAccountTotalMessageCount;
    private String mLastMessage;
    private long mLastMessageTime;
    private int mHasRead;
    private int mUnRead;
    private int mRcsMessageType;
    private PublicAccounts mPublicAccount;

    @Override
    public String toString() {
        return "PublicConversation [mPublicAccountThreadId=" + mPublicAccountThreadId
                + ", mPublicAccountTotalMessageCount=" + mPublicAccountTotalMessageCount
                + ", mLastMessage=" + mLastMessage + ", mLastMessageTime=" + mLastMessageTime
                + ", mHasRead=" + mHasRead + ", mRcsMessageType=" + mRcsMessageType
                + ", mRecipients=" + mRecipients + "]";
    }

    private String mRecipients;


    public String getRecipients() {
        return mRecipients;
    }

    public void setRecipients(String recipients) {
        this.mRecipients = recipients;
    }

    public PublicAccounts getPublicAccount() {
        return mPublicAccount;
    }

    public void setPublicAccount(PublicAccounts publicAccount) {
        this.mPublicAccount = publicAccount;
    }

    public int getRcsMessageType() {
        return mRcsMessageType;
    }

    public void setRcsMessageType(int rcsMessageType) {
        this.mRcsMessageType = rcsMessageType;
    }

    public int getHasRead() {
        return mHasRead;
    }

    public void setHasRead(int hasRead) {
        this.mHasRead = hasRead;
    }

    public int getUnRead() {
        return mUnRead;
    }

    public void setUnRead(int unRead) {
        this.mUnRead = unRead;
    }

    public PublicConversation(){

    }

    public PublicConversation(Parcel in) {
        readFromParcel(in);
    }

    public long getPublicAccountThreadId() {
        return mPublicAccountThreadId;
    }


    public void setPublicAccountThreadId(long publicAccountThreadId) {
        this.mPublicAccountThreadId = publicAccountThreadId;
    }


    public int getPublicAccountTotalMessageCount() {
        return mPublicAccountTotalMessageCount;
    }


    public void setPublicAccountTotalMessageCount(int publicAccountTotalMessageCount) {
        this.mPublicAccountTotalMessageCount = publicAccountTotalMessageCount;
    }


    public String getLastMessage() {
        return mLastMessage;
    }


    public void setLastMessage(String lastMessage) {
        this.mLastMessage = lastMessage;
    }


    public long getLastMessageTime() {
        return mLastMessageTime;
    }


    public void setLastMessageTime(long lastMessageTime) {
        this.mLastMessageTime = lastMessageTime;
    }

    /** The parcel creator. */
    public static final Parcelable.Creator<PublicConversation> CREATOR = new Parcelable.Creator<PublicConversation>() {
        @Override
        public PublicConversation createFromParcel(Parcel source) {
            return new PublicConversation(source);
        }

        @Override
        public PublicConversation[] newArray(int size) {
            return new PublicConversation[size];
        }
    };
    @Override
    public int describeContents() {
        // TODO Auto-generated method stub
        return 0;
    }


    private void readFromParcel(Parcel source) {
        mPublicAccountThreadId = source.readLong();
        mPublicAccountTotalMessageCount = source.readInt();
        mLastMessage = source.readString();
        mLastMessageTime = source.readLong();
        mHasRead = source.readInt();
        mUnRead = source.readInt();
        mRcsMessageType = source.readInt();
        mPublicAccount = (PublicAccounts) source.readValue(this.getClass().getClassLoader());
        mRecipients = source.readString();

    }

    @Override
    public void writeToParcel(Parcel source, int arg1) {
        source.writeLong(mPublicAccountThreadId);
        source.writeInt(mPublicAccountTotalMessageCount);
        source.writeString(mLastMessage);
        source.writeLong(mLastMessageTime);
        source.writeInt(mHasRead);
        source.writeInt(mUnRead);
        source.writeInt(mRcsMessageType);
        source.writeValue(mPublicAccount);
        source.writeString(mRecipients);

    }

}
