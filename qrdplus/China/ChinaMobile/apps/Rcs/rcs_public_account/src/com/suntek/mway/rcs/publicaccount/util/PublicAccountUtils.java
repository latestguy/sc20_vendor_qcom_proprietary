/*
 * Copyright (c) 2015 pci-suntektech Technologies, Inc.  All Rights Reserved.
 * pci-suntektech Technologies Proprietary and Confidential.
 */

package com.suntek.mway.rcs.publicaccount.util;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SqliteWrapper;
import android.provider.BaseColumns;
import android.provider.Telephony.Sms;
import android.util.Log;

import com.suntek.mway.rcs.client.aidl.common.RcsColumns;
import com.suntek.mway.rcs.client.aidl.constant.Constants;
import com.suntek.mway.rcs.client.aidl.plugin.entity.pubacct.PublicAccounts;
import com.suntek.mway.rcs.client.aidl.plugin.entity.pubacct.PublicMessage;
import com.suntek.mway.rcs.client.api.message.MessageApi;
import com.suntek.mway.rcs.publicaccount.data.PublicConversation;
import com.suntek.mway.rcs.publicaccount.data.PublicMessageItem;
import com.suntek.rcs.ui.common.RcsLog;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PublicAccountUtils {

    public static final int RCS_MESSAGE_READ = 1;
    public static final int RCS_MESSAGE_SEEN = 1;

    public static final String UI_NEED_FRESH = "com.suntek.mway.rcs.ACTION_UI_NEED_FRESH";

    public static final String PA_MESSAGE_READ = "read";

    private static final String[] THREADS_PROJECTION = new String[] {
        Constants.ThreadProvider.Thread.DATE,
        Constants.ThreadProvider.Thread.NUMBER,
        PA_MESSAGE_READ,
    };

    public static List<PublicConversation> getAllPublicCon(Context context){
        ContentResolver resolver = context.getContentResolver();
        String selector = "(" + RcsColumns.ThreadColumns.RCS_CHAT_TYPE + " = "
                + Constants.MessageConstants.CONST_CHAT_PUBLIC_ACCOUNT + ")";
        Cursor cursor = SqliteWrapper.query(context, resolver,
                Constants.ThreadProvider.CONST_THREAD_URI, THREADS_PROJECTION, selector, null, null);
        ArrayList<PublicConversation> publicConversationList = new ArrayList<PublicConversation>();
        try {
            while(cursor != null && cursor.moveToNext()) {
                long date = cursor.getLong(cursor.getColumnIndex(
                        Constants.ThreadProvider.Thread.DATE));
                String number = cursor.getString(cursor.getColumnIndex(
                        Constants.ThreadProvider.Thread.NUMBER));
                int read = cursor.getInt(cursor.getColumnIndex(PA_MESSAGE_READ));
                int unReadCount = 0;
                if (read == 0) {
                    unReadCount = getUnReadCount(context, number);
                }

                String sel = "(" + Constants.PublicAccountHisProvider.PublicAccountHis
                        .ACCOUNT_SIP_URI + " = " + cursor.getString(cursor.getColumnIndex(
                                Constants.ThreadProvider.Thread.NUMBER)) + ")";
                Cursor c = SqliteWrapper.query(context, resolver,
                        Constants.PublicAccountHisProvider.CONST_PUBLIC_ACCOUNT_HIS_URI,
                        null, sel, null, null);

                try {
                    if (c != null && c.moveToFirst()) {
                        PublicConversation pubCon = new PublicConversation();
                        PublicAccounts account  =  new PublicAccounts();
                        String paUuid = c.getString(c.getColumnIndex(
                                Constants.PublicAccountHisProvider.PublicAccountHis.ACCOUNT_ID));
                        String logoUrl = c.getString(c.getColumnIndex(
                                Constants.PublicAccountHisProvider.PublicAccountHis.ACCOUNT_LOGO));
                        String name = c.getString(c.getColumnIndex(
                                Constants.PublicAccountHisProvider.PublicAccountHis.ACCOUNT_NAME));
                        String sipUri = c.getString(c.getColumnIndex(Constants
                                .PublicAccountHisProvider.PublicAccountHis.ACCOUNT_SIP_URI));
                        account.setPaUuid(paUuid);
                        account.setLogo(logoUrl);
                        account.setName(name);
                        account.setSipUri(sipUri);
                        pubCon.setPublicAccount(account);
                        pubCon.setLastMessageTime(date);
                        pubCon.setUnRead(unReadCount);
                        publicConversationList.add(pubCon);
                    }
                } catch (Exception e) {
                    Log.w("RCS_UI",e);
                } finally {
                    c.close();
                }
            }
        } catch (Exception e) {
            Log.w("RCS_UI",e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return publicConversationList;
    }

    public static void dumpCursor(Cursor cursor) {
        if (cursor == null) {
            return;
        }
        int position = cursor.getPosition();

        try {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    int count = cursor.getColumnCount();
                    RcsLog.i( "------ dump cursor row ------");
                    for (int i = 0; i < count; i++) {
                        RcsLog.i(cursor.getColumnName(i) + "=" + cursor.getString(i));
                    }
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            RcsLog.e(e.toString());
        } finally {
            cursor.moveToPosition(position);
        }
    }

    public static List<PublicMessageItem> getAllMessage(Context context, String address) {
        ContentResolver resolver = context.getContentResolver();
        String selection = RcsColumns.SmsRcsColumns.RCS_CHAT_TYPE + " = ?"
                +" and " + Sms.ADDRESS + "= ?";
        Cursor cursor = SqliteWrapper.query(context, resolver, PublicMessageItem.PUBLIC_MESSAGE_URI,
                PublicMessageItem.PUBLIC_MESSAGE_PROJECTION,
                selection, new String[] {
                String.valueOf(Constants.MessageConstants.CONST_CHAT_PUBLIC_ACCOUNT), address
            }, "_id ASC");
        dumpCursor(cursor);
        ArrayList<PublicMessageItem> publicMessageList = new ArrayList<PublicMessageItem>();
        try {
            while (cursor != null && cursor.moveToNext()) {
                PublicMessageItem messageItem = new PublicMessageItem();
                long id = cursor.getLong(cursor.getColumnIndex(BaseColumns._ID));
                messageItem.setId(id);
                int msgType = cursor.getInt(cursor.getColumnIndex(
                        RcsColumns.SmsRcsColumns.RCS_MSG_TYPE));
                messageItem.setRcsMessageType(msgType);
                String msgBody = cursor.getString(cursor.getColumnIndex(Sms.BODY));
                messageItem.setMessageBody(msgBody);
                messageItem.setSendReceive(cursor.getInt(cursor.getColumnIndex(Sms.TYPE)));
                messageItem.setSendDate(cursor.getLong(cursor.getColumnIndex(Sms.DATE)));
                messageItem.setMessageFilePath(cursor.getString(
                        cursor.getColumnIndex(RcsColumns.SmsRcsColumns.RCS_FILENAME)));
                messageItem.setThumbMessageFilePath(cursor.getString(
                        cursor.getColumnIndex(RcsColumns.SmsRcsColumns.RCS_THUMB_PATH)));
                messageItem.setMessageSendState(cursor.getInt(
                        cursor.getColumnIndex(RcsColumns.SmsRcsColumns.RCS_MSG_STATE)));
                PublicMessage publicMessage = MessageApi.getInstance().
                        parsePublicMessage(msgType, msgBody);
                messageItem.setPublicMessage(publicMessage);
                messageItem.setMessagePhoneId(cursor.getInt(
                        cursor.getColumnIndex(RcsColumns.SmsRcsColumns.PHONE_ID)));
                messageItem.setMessageMimeType(cursor.getString(
                        cursor.getColumnIndex(RcsColumns.SmsRcsColumns.RCS_MIME_TYPE)));
                publicMessageList.add(messageItem);
            }
        } catch (Exception e) {
            RcsLog.e(e.toString());
        } finally {
            cursor.close();
        }
        return publicMessageList;
    }

    public static boolean hasUnReadMessage(Context context, long threadId) {
        boolean hasUnRead = false;
        ContentResolver resolver = context.getContentResolver();
        Cursor cursor = resolver.query(PublicMessageItem.PUBLIC_MESSAGE_URI, new String[]{
            "_id" }, "read = 0 AND thread_id = ?", new String[]{ String.valueOf(threadId) }, null);
        try {
            if (cursor != null && cursor.getCount() > 0) {
                hasUnRead = true;
            }
        } finally {
            cursor.close();
        }
        return hasUnRead;
    }

    public static void removeUnReadMessage(Context context, long threadId) {
        ContentValues values = new ContentValues();
        values.put("read", RCS_MESSAGE_READ);
        values.put("seen", RCS_MESSAGE_SEEN);
        ContentResolver resolver = context.getContentResolver();
        resolver.update(PublicMessageItem.PUBLIC_MESSAGE_URI, values,
                "thread_id = ?", new String[]{String.valueOf(threadId)});
    }

    public static int getUnReadCount(Context context, String number) {
        ContentResolver resolver = context.getContentResolver();
        Cursor cursor = resolver.query(PublicMessageItem.PUBLIC_MESSAGE_URI, new String[]{
                "_id" }, "address = ? AND read = 0", new String[]{ number }, null);
        try {
            while (cursor != null && cursor.moveToNext()) {
                return cursor.getCount();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            cursor.close();
        }
        return 0;
    }

    public static long getTheadIdByNumber(Context context, String number) {
        ContentResolver resolver = context.getContentResolver();
        Cursor cursor = resolver.query(PublicMessageItem.PUBLIC_MESSAGE_URI, new String[]{
                "thread_id" }, "address = ?", new String[]{ number }, null);
        try {
            while (cursor != null && cursor.moveToNext()) {
                return cursor.getLong(0);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            cursor.close();
        }
        return 0;
    }

    public static boolean isFollowByCard(Context context, String publicAccountUuid) {
        ContentResolver resolver = context.getContentResolver();
        Cursor cursor = SqliteWrapper.query(context, resolver,
                Constants.PublicAccountProvider.CONST_PUBLIC_ACCOUNT_URI, null,
                        Constants.PublicAccountProvider.PublicAccount.ACCOUNT_ID + " = ?",
                                new String[]{ publicAccountUuid }, null);
        if (cursor != null && cursor.moveToFirst()) {
            return true;
        } else {
            return false;
        }
    }

    public static String replaceBlank(String str) {
        String dest = "";
        if (str != null) {
            Pattern p = Pattern.compile("\\s*|\t|\r|\n");
            Matcher m = p.matcher(str);
            dest = m.replaceAll("");
        }
        return dest;
    }
}
