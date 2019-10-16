/*
 * Copyright (c) 2014-2015 pci-suntektech Technologies, Inc.  All Rights Reserved.
 * pci-suntektech Technologies Proprietary and Confidential.
 */

package com.suntek.mway.rcs.nativeui.utils;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Groups;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.vcard.VCardParser;
import com.android.vcard.VCardParser_V21;
import com.android.vcard.exception.VCardException;

import com.suntek.mway.rcs.client.aidl.plugin.entity.profile.Avatar;
import com.suntek.mway.rcs.client.aidl.plugin.entity.profile.Avatar.IMAGE_TYPE;
import com.suntek.mway.rcs.client.aidl.plugin.entity.profile.Profile;
import com.suntek.mway.rcs.client.aidl.plugin.entity.profile.QRCardImg;
import com.suntek.mway.rcs.client.aidl.plugin.entity.profile.QRCardInfo;
import com.suntek.mway.rcs.client.aidl.plugin.entity.profile.TelephoneModel;
import com.suntek.mway.rcs.client.aidl.service.entity.GroupChat;
import com.suntek.mway.rcs.client.aidl.service.entity.GroupChatMember;
import com.suntek.mway.rcs.client.api.exception.ServiceDisconnectedException;
import com.suntek.mway.rcs.client.api.publicaccount.PublicAccountApi;
import com.suntek.mway.rcs.client.api.publicaccount.PublicAccountCallback;
import com.suntek.mway.rcs.client.api.groupchat.GroupChatApi;
import com.suntek.mway.rcs.nativeui.R;
import com.suntek.rcs.ui.common.PropertyNode;
import com.suntek.rcs.ui.common.VNode;
import com.suntek.rcs.ui.common.VNodeBuilder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.ArrayList;

public class RcsContactUtils {
    private static final String LOG_TAG = "RCS_UI";

    public static final String NOTIFY_CONTACT_PHOTO_CHANGE =
            "com.suntek.mway.rcs.NOTIFY_CONTACT_PHOTO_CHANGE";
    public static final String LOCAL_PHOTO_SETTED = "local_photo_setted";

    public static final String TAG = "NativeUI_RcsContactUtils";

    private static volatile boolean rcsConnection = false;

    public static final String PREF_FOLLOW_STATE_CHANGED = "pref_follow_state";
    // User requst to update enhance screen
    public static final String UPDATE_ENHANCE_SCREEN_PHONE_EVENT = "933 10 12000";

    private static int DEFAULT_NUMBER_LENGTH = 11;

    public static String PUB_ACCOUNT_PA_UUID = "pa_uuid";

    public static String PUB_ACCOUNT_NAME = "name";

    public static String PUB_ACCOUNT_LOGO = "logo";

    public static String PUB_ACCOUNT_RECOMMEND_LEVEL = "recommend_level";

    public static String PUB_ACCOUNT_SIP_URI = "sip_uri";

    public static String PUB_ACCOUNT_FOLLOWED = "followed";

    public static String PUB_ACCOUNT_ACCEPT = "accept";

    public static String PUB_ACCOUNT_COMPANY = "company";

    public static String PUB_ACCOUNT_INTRO = "intro";

    public static String PUB_ACCOUNT_TYPE = "type";

    public static String PUB_ACCOUNT_UPDATE_TIME = "update_time";

    public static String PUB_ACCOUNT_MENU_TYPE = "menu_type";

    public static String PUB_ACCOUNT_MENU_TIMESTAMP = "menu_timestamp";

    public static String PUB_ACCOUNT_ACTIVE_STATUS = "active_status";

    public static String PUB_ACCOUNT_TEL = "tel";

    public static String PUB_ACCOUNT_EMAIL = "email";

    public static String PUB_ACCOUNT_ZIP = "zip";

    public static String PUB_ACCOUNT_ADDR = "addr";

    public static String PUB_ACCOUNT_FIELD = "field";

    public static String PUB_ACCOUNT_QR_CODE = "qr_code";

    public static String PUB_ACCOUNT_LOGO_TYPE = "logo_type";

    public static String PUB_ACCOUNT_MENU_STRING = "menu_string";

    public static String PUB_ACCOUNT_NUMBER = "number";

    public static String PUB_ACCOUNT_FOLLOWED_TIME = "followed_time";

    public static int TRUNCATE_COUNTRY_CODE_CN = 4;

    public static String PUB_ACCOUNT_URI = "content://com.suntek.mway.rcs.app.service.public_account";

    public static final Uri PROFILE_URI = Uri.parse("content://com.android.contacts/profile");

    public static boolean isRcsConnection() {
        return rcsConnection;
    }

    public static void setRcsConnectionState(boolean flag) {
        rcsConnection = flag;
    }

    public static void sleep(long time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void unregisterPublicAccountCallback(PublicAccountCallback callback) {
        try {
            PublicAccountApi.getInstance().unregisterCallback(callback);
        } catch (ServiceDisconnectedException e) {
            e.printStackTrace();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public static String replaceNumberSpace(String number) {
        if (TextUtils.isEmpty(number))
            return null;
        return number.replaceAll("[- ]", "");
    }

    public static Bitmap getPhotoByNumber(Context context, String number) {
        Bitmap photo = null;

        long rawContactId = getRawContactIdByNumber(context, number);
        Log.d("RCS_UI", "rawContactId=" + rawContactId);
        if (rawContactId > 0) {
            long contactId = getContactIdByRawContactId(context, rawContactId);
            Log.d("RCS_UI", "contactId=" + contactId);
            if (contactId > 0) {
                photo = getPhotoByContactId(context, contactId);
                Log.d("RCS_UI", "photo=" + photo);
            } else {
                photo = getPhotoByContactId(context, rawContactId);
            }
        }

        return photo;
    }

    public static long getContactIdByRawContactId(Context context, long rawContactId) {
        long contactId = -1;

        Cursor cursor = context.getContentResolver().query(RawContacts.CONTENT_URI, new String[] {
            RawContacts.CONTACT_ID
        }, RawContacts._ID + "=?", new String[] {
            String.valueOf(rawContactId)
        }, null);

        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    contactId = cursor.getLong(0);
                }
            } finally {
                cursor.close();
            }
        }

        return contactId;
    }

    public static long getRawContactIdByNumber(Context context, String number) {
        long rawContactId = -1;
        String contactName = number;
        String numberW86;
        if (!number.startsWith("+86")) {
            numberW86 = "+86" + number;
        } else {
            numberW86 = number;
            number = number.substring(3);
        }
        String formatNumber = getAndroidFormatNumber(number);
        String formatNumber2 = formatNumber.substring(TRUNCATE_COUNTRY_CODE_CN);
        ContentResolver cr = context.getContentResolver();
        Cursor pCur = cr.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI, new String[] {
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID
                },
                ContactsContract.CommonDataKinds.Phone.NUMBER + " = ? OR "
                        + ContactsContract.CommonDataKinds.Phone.NUMBER + " = ? OR "
                        + ContactsContract.CommonDataKinds.Phone.NUMBER + " = ? OR "
                        + ContactsContract.CommonDataKinds.Phone.NUMBER + " = ? ",
                new String[] {
                        number, numberW86, formatNumber, formatNumber2
                }, null);
        try {
            if (pCur != null && pCur.moveToFirst()) {
                rawContactId = pCur.getLong(pCur.getColumnIndex(
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID));
            }
        } finally {
            if (pCur != null){
                pCur.close();
            }
        }
        return rawContactId;
    }

    public static Bitmap getPhotoByContactId(Context context, long contactId) {
        ContentResolver cr = context.getContentResolver();
        Uri uri = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId);
        Log.d("RCS_UI", "contact uri=" + uri);
        InputStream input = Contacts.openContactPhotoInputStream(cr, uri);
        Log.d("RCS_UI", "input=" + input);
        Bitmap contactPhoto = BitmapFactory.decodeStream(input);
        return contactPhoto;
    }

    public static String getGroupChatMemberDisplayName(Context context, long groupId,
            String number, String myPhoneNumber) {
        GroupChat model = null;
        List<GroupChatMember> list = null;
        try {
            model = GroupChatApi.getInstance().getGroupChatById(groupId);
            list = GroupChatApi.getInstance().getMembers(groupId);
        } catch (ServiceDisconnectedException e) {
            e.printStackTrace();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        if (model == null)
            return number;

        if (list == null || list.size() == 0)
            return number;
        for (GroupChatMember GroupChatMember : list) {
            if (GroupChatMember.getNumber().equals(number)) {
                if (!TextUtils.isEmpty(GroupChatMember.getAlias())) {
                    return GroupChatMember.getAlias();
                } else {
                    return getContactNameFromPhoneBook(context, number, myPhoneNumber);
                }
            }
        }
        return number;
    }

    public static String getContactNameFromPhoneBook(Context context, String phoneNum,
            String myPhoneNumber) {
        Uri qureyUri = null;
        if (myPhoneNumber != null && myPhoneNumber.endsWith(phoneNum)) {
            qureyUri = Uri.withAppendedPath(ContactsContract.Profile.CONTENT_URI,
                    "data");
        } else {
            qureyUri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
        }

        String contactName = phoneNum;
        String numberW86;
        if (!phoneNum.startsWith("+86")) {
            numberW86 = "+86" + phoneNum;
        } else {
            numberW86 = phoneNum;
            phoneNum = phoneNum.substring(3);
        }
        String formatNumber = getAndroidFormatNumber(phoneNum);
        String formatNumber2 = formatNumber.substring(TRUNCATE_COUNTRY_CODE_CN);

        ContentResolver cr = context.getContentResolver();
        Cursor pCur = cr.query(qureyUri, new String[] {
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                },
                ContactsContract.CommonDataKinds.Phone.NUMBER + " = ? OR "
                        + ContactsContract.CommonDataKinds.Phone.NUMBER + " = ? OR "
                        + ContactsContract.CommonDataKinds.Phone.NUMBER + " = ? OR "
                        + ContactsContract.CommonDataKinds.Phone.NUMBER + " = ? ",
                new String[] {
                        phoneNum, numberW86, formatNumber, formatNumber2
                }, null);
        try {
            if (pCur != null && pCur.moveToFirst()) {
                contactName = pCur.getString(pCur.getColumnIndex(
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
            }
        } finally {
            if (pCur != null)
                pCur.close();
        }
        return contactName;
    }

    public static String getAndroidFormatNumber(String number) {
        if (TextUtils.isEmpty(number)) {
            return number;
        }

        number = number.replaceAll(" ", "");

        if (number.startsWith("+86")) {
            number = number.substring(3);
        }

        if (number.length() != 11) {
            return number;
        }

        StringBuilder builder = new StringBuilder();
        builder.append("+86 ");
        builder.append(number.substring(0, 3));
        builder.append(" ");
        builder.append(number.substring(3, 7));
        builder.append(" ");
        builder.append(number.substring(7));
        return builder.toString();
    }

    public static boolean isGroupExistContacts(Context context, long groupId) {
        boolean isExist = false;
        if(context == null) {
            return isExist;
        }
        try {
            Cursor groupCount =  context.getContentResolver().query(Groups.CONTENT_URI, null,
                    Groups.SYSTEM_ID + " = " + groupId, null, null);
            if (null != groupCount) {
                if (groupCount.getCount() > 0) {
                    isExist = true;
                }
                groupCount.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return isExist;
    }

    public static void insertGroupChat(Context context, long groupId, String groupSubject) {
        if(context == null) return;
        if (isGroupExistContacts(context, groupId)) {
            return;
        }
        ContentResolver resolver = context.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(Groups.TITLE, groupSubject);
        values.put(Groups.SYSTEM_ID,groupId);
        values.put(Groups.SOURCE_ID,"RCS");
        try{
            Log.d(TAG," create group: title= "+groupSubject+" id= "+groupId);
            resolver.insert(Groups.CONTENT_URI, values);
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public static void UpdateGroupChatSubject(Context context, long groupId,String groupSubject) {
        if(context == null) return;
        ContentResolver resolver = context.getContentResolver();
        StringBuilder where = new StringBuilder();
        where.append(Groups.SYSTEM_ID);
        where.append("="+groupId);
        ContentValues values = new ContentValues();
        values.put(Groups.TITLE, groupSubject);
        values.put(Groups.SYSTEM_ID,groupId);

        try{
            Log.d(TAG," update group: title= "+groupSubject+" id= "+groupId);
            resolver.update(Groups.CONTENT_URI, values, where.toString(), null);
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public static void deleteGroupChat(Context context, long groupId) {
        if(context == null) return;
        ContentResolver resolver = context.getContentResolver();
        StringBuilder where = new StringBuilder();
        where.append(Groups.SYSTEM_ID);
        where.append("="+groupId);

        try{
            Log.d(TAG," disband group:  id= "+groupId);
            resolver.delete(Groups.CONTENT_URI, where.toString(), null);
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public static Bitmap getMyProfilePhotoOnData(Context context) {
        Bitmap bitmap = null;
        byte [] data = getMyProfilePhotoByteOnData(context);
        if (data != null) {
            bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
        }
        return bitmap;
    }

    public static byte[] getMyProfilePhotoByteOnData(Context context) {
        byte [] data = null;
        InputStream avatarDataStream = Contacts.openContactPhotoInputStream(
                context.getContentResolver(), PROFILE_URI);
        try {
            if (avatarDataStream != null) {
                data = new byte[avatarDataStream.available()];
                avatarDataStream.read(data, 0, data.length);
            }
        } catch (IOException ex) {

        } finally {
            try {
                if (avatarDataStream != null) {
                    avatarDataStream.close();
                }
            } catch (IOException e) {
            }
        }
        return data;
    }

    public static void setContactPhoto(Context context, byte[] input,
            Uri outputUri) {
        FileOutputStream outputStream = null;

        try {
            outputStream = context.getContentResolver().openAssetFileDescriptor(outputUri, "rw")
                    .createOutputStream();
            outputStream.write(input);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try{
                outputStream.close();
            }catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static boolean hasLocalSetted(ContentResolver resolver, long rawContactId) {
        Cursor c = resolver.query(ContactsContract.RawContacts.CONTENT_URI, new String[] {
                LOCAL_PHOTO_SETTED
        }, RawContacts._ID + " = ? ", new String[] {
                String.valueOf(rawContactId)
        }, null);
        long localSetted = 0;
        try {
            if (c != null && c.moveToFirst()) {
                localSetted = c.getLong(0);
            }
        } finally {
            c.close();
        }
        return (localSetted == 1) ? true : false;
    }

    public static long getContactIdByNumber(Context context, String number) {
        if (TextUtils.isEmpty(number)) {
            return -1;
        }
        String numberW86 = number;
        if (!number.startsWith("+86")) {
            numberW86 = "+86" + number;
        } else {
            numberW86 = number.substring(3);
        }
        Cursor cursor = context.getContentResolver().query(Phone.CONTENT_URI, new String[] {
                Phone.CONTACT_ID
        }, Phone.NUMBER + "=? OR " + Phone.NUMBER + "=?", new String[] {
                number, numberW86
        }, null);
        if (cursor != null) {
            try{
                if (cursor.moveToFirst()) {
                    return cursor.getInt(0);
                }
            } finally {
                cursor.close();
            }
        }
        return -1;
    }

    public static String getFormatNumber(String number){
        if(null == number){
            return "";
        }
        number = number.replaceAll("-", "");
        number = number.replaceAll(" ", "");
        number = number.replaceAll(",", "");
        int numberLen = number.length();
        if(numberLen > DEFAULT_NUMBER_LENGTH){
            number = number.substring(numberLen - DEFAULT_NUMBER_LENGTH, numberLen);
        }
        return number;
    }

    public static byte[] getBytesFromFile(File f) {
        if (f == null) {
            return null;
        }
        try {
            FileInputStream stream = new FileInputStream(f);
            ByteArrayOutputStream out = new ByteArrayOutputStream(1000);
            byte[] b = new byte[1000];
            int n;
            while ((n = stream.read(b)) != -1) {
                out.write(b, 0, n);
            }
            stream.close();
            out.close();
            return out.toByteArray();
        } catch (IOException e) {
        }
        return null;
    }

    public static boolean isFileDownload(String filePath, long fileSize) {
        if (TextUtils.isEmpty(filePath)) {
            return false;
        }
        if (fileSize == 0) {
            return false;
        }
        boolean isDownload = false;
        File file = new File(filePath);
        if (file != null) {
            Log.i(LOG_TAG,"filePath = " + filePath + " ; thisFileSize = "
                    + file.length() + " ; fileSize = " + fileSize);
            if (file.exists() && file.length() >= fileSize) {
                isDownload = true;
            }
        }
        return isDownload;
    }

    public static List<VNode> rcsVcardContactList(Context context,String filePath) {
        if (TextUtils.isEmpty(filePath)) {
            return null;
        }
        try {
            File file = new File(filePath);
            FileInputStream fis = new FileInputStream(file);

            VNodeBuilder builder = new VNodeBuilder();
            VCardParser parser = new VCardParser_V21();
            parser.addInterpreter(builder);
            parser.parse(fis);
            List<VNode> vNodeList = builder.getVNodeList();
            return vNodeList;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static ArrayList<PropertyNode> openRcsVcardDetail(Context context,String filePath){
        if (TextUtils.isEmpty(filePath)){
            return null;
        }
        try {
            File file = new File(filePath);
            FileInputStream fis = new FileInputStream(file);

            VNodeBuilder builder = new VNodeBuilder();
            VCardParser parser = new VCardParser_V21();
            parser.addInterpreter(builder);
            parser.parse(fis);
            List<VNode> vNodeList = builder.getVNodeList();
            ArrayList<PropertyNode> propList = vNodeList.get(0).propList;
            return propList;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static Bitmap decodeInSampleSizeBitmap(String imageFilePath) {
        Bitmap bitmap;

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        bitmap = BitmapFactory.decodeFile(imageFilePath, options);
        options.inJustDecodeBounds = false;

        int inSampleSize = (int) (options.outHeight / (float) 200);
        if (inSampleSize <= 0)
            inSampleSize = 1;
        options.inSampleSize = inSampleSize;

        bitmap = BitmapFactory.decodeFile(imageFilePath, options);

        return bitmap;
    }

    public static Bitmap decodeInSampleSizeBitmap(Bitmap bitmap) {
        int inSampleSize = 200/bitmap.getHeight();
        if (inSampleSize >= 0) {
            return bitmap;
        } else {
            Matrix matrix = new Matrix();
            matrix.postScale(inSampleSize,inSampleSize);
            Bitmap resizeBmp = Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),
                    bitmap.getHeight(),matrix,true);
            return resizeBmp;
        }
    }

    public static String getPhoneNumberTypeStr(Context context, PropertyNode propertyNode) {
        String numberTypeStr = "";
        if (null == propertyNode.paramMap_TYPE
                || propertyNode.paramMap_TYPE.size() == 0) {
            return numberTypeStr;
        }
        String number = propertyNode.propValue;
        if (propertyNode.paramMap_TYPE.size() == 2) {
            if (propertyNode.paramMap_TYPE.contains("FAX")
                    && propertyNode.paramMap_TYPE.contains("HOME")) {
                numberTypeStr = context
                        .getString(R.string.vcard_number_fax_home) + number;
            } else if (propertyNode.paramMap_TYPE.contains("FAX")
                    && propertyNode.paramMap_TYPE.contains("WORK")) {
                numberTypeStr = context
                        .getString(R.string.vcard_number_fax_work) + number;
            } else if (propertyNode.paramMap_TYPE.contains("PREF")
                    && propertyNode.paramMap_TYPE.contains("WORK")) {
                numberTypeStr = context
                        .getString(R.string.vcard_number_pref_work) + number;
            } else if (propertyNode.paramMap_TYPE.contains("CELL")
                    && propertyNode.paramMap_TYPE.contains("WORK")) {
                numberTypeStr = context
                        .getString(R.string.vcard_number_call_work) + number;
            } else if (propertyNode.paramMap_TYPE.contains("WORK")
                    && propertyNode.paramMap_TYPE.contains("PAGER")) {
                numberTypeStr = context
                        .getString(R.string.vcard_number_work_pager) + number;
            } else {
                numberTypeStr = context.getString(R.string.vcard_number_other)
                        + number;
            }
        } else {
            if (propertyNode.paramMap_TYPE.contains("CELL")) {
                numberTypeStr = context.getString(R.string.vcard_number)
                        + number;
            } else if (propertyNode.paramMap_TYPE.contains("HOME")) {
                numberTypeStr = context.getString(R.string.vcard_number_home)
                        + number;
            } else if (propertyNode.paramMap_TYPE.contains("WORK")) {
                numberTypeStr = context.getString(R.string.vcard_number_work)
                        + number;
            } else if (propertyNode.paramMap_TYPE.contains("PAGER")) {
                numberTypeStr = context.getString(R.string.vcard_number_pager)
                        + number;
            } else if (propertyNode.paramMap_TYPE.contains("VOICE")) {
                numberTypeStr = context.getString(R.string.vcard_number_other)
                        + number;
            } else if (propertyNode.paramMap_TYPE.contains("CAR")) {
                numberTypeStr = context.getString(R.string.vcard_number_car)
                        + number;
            } else if (propertyNode.paramMap_TYPE.contains("ISDN")) {
                numberTypeStr = context.getString(R.string.vcard_number_isdn)
                        + number;
            } else if (propertyNode.paramMap_TYPE.contains("PREF")) {
                numberTypeStr = context.getString(R.string.vcard_number_pref)
                        + number;
            } else if (propertyNode.paramMap_TYPE.contains("FAX")) {
                numberTypeStr = context.getString(R.string.vcard_number_fax)
                        + number;
            } else if (propertyNode.paramMap_TYPE.contains("TLX")) {
                numberTypeStr = context.getString(R.string.vcard_number_tlx)
                        + number;
            } else if (propertyNode.paramMap_TYPE.contains("MSG")) {
                numberTypeStr = context.getString(R.string.vcard_number_msg)
                        + number;
            } else {
                numberTypeStr = context.getString(R.string.vcard_number_other)
                        + number;
            }
        }
        return numberTypeStr;
    }

    public static void showDetailVcard(Context context,
            ArrayList<PropertyNode> propList) {
        AlertDialog.Builder builder = new Builder(context);
        LayoutInflater inflater = LayoutInflater.from(context);
        View vcardView = inflater.inflate(R.layout.rcs_vcard_detail, null);

        ImageView photoView = (ImageView) vcardView
                .findViewById(R.id.vcard_photo);
        TextView nameView, priNumber, addrText, comName, positionText;
        nameView = (TextView) vcardView.findViewById(R.id.vcard_name);
        priNumber = (TextView) vcardView.findViewById(R.id.vcard_number);
        addrText = (TextView) vcardView.findViewById(R.id.vcard_addre);
        positionText = (TextView) vcardView.findViewById(R.id.vcard_position);
        comName = (TextView) vcardView.findViewById(R.id.vcard_com_name);

        ArrayList<String> numberList = new ArrayList<String>();
        for (PropertyNode propertyNode : propList) {
            if ("FN".equals(propertyNode.propName)) {
                if (!TextUtils.isEmpty(propertyNode.propValue)) {
                    nameView.setText(context.getString(R.string.vcard_name)
                            + propertyNode.propValue);
                }
            } else if ("TEL".equals(propertyNode.propName)) {
                if (!TextUtils.isEmpty(propertyNode.propValue)) {
                    String numberTypeStr = getPhoneNumberTypeStr(context, propertyNode);
                    if(!TextUtils.isEmpty(numberTypeStr)){
                        numberList.add(numberTypeStr);
                    }
                }
            } else if ("ADR".equals(propertyNode.propName)) {
                if (!TextUtils.isEmpty(propertyNode.propValue)) {
                    String address = propertyNode.propValue;
                    address = address.replaceAll(";", "");
                    addrText.setText(context
                            .getString(R.string.vcard_compony_addre)
                            + ":"
                            + address);
                }
            } else if ("ORG".equals(propertyNode.propName)) {
                if (!TextUtils.isEmpty(propertyNode.propValue)) {
                    comName.setText(context
                            .getString(R.string.vcard_compony_name)
                            + ":"
                            + propertyNode.propValue);
                }
            } else if ("TITLE".equals(propertyNode.propName)) {
                if (!TextUtils.isEmpty(propertyNode.propValue)) {
                    positionText.setText(context
                            .getString(R.string.vcard_compony_position)
                            + ":"
                            + propertyNode.propValue);
                }
            } else if ("PHOTO".equals(propertyNode.propName)) {
                if (propertyNode.propValue_bytes != null) {
                    byte[] bytes = propertyNode.propValue_bytes;
                    final Bitmap vcardBitmap = BitmapFactory.decodeByteArray(
                            bytes, 0, bytes.length);
                    photoView.setImageBitmap(vcardBitmap);
                }
            }
        }
        vcardView.findViewById(R.id.vcard_middle).setVisibility(View.GONE);
        if (numberList.size() > 0) {
            priNumber.setText(numberList.get(0));
            numberList.remove(0);
        }
        if (numberList.size() > 0) {
            vcardView.findViewById(R.id.vcard_middle).setVisibility(
                    View.VISIBLE);
            LinearLayout linearLayout = (LinearLayout) vcardView.
                    findViewById(R.id.other_number_layout);
            addNumberTextView(context, numberList, linearLayout);
        }
        builder.setTitle(R.string.vcard_detail_info);
        builder.setView(vcardView);
        builder.create();
        builder.show();
    }

    private static void addNumberTextView(Context context,
            ArrayList<String> numberList, LinearLayout linearLayout) {
        for (int i = 0; i < numberList.size(); i++) {
            TextView textView = new TextView(context);
            textView.setText(numberList.get(i));
            linearLayout.addView(textView);
        }
    }

    public static Bitmap toCornerRadius(Bitmap bitmap, int rate) {
        int color = 0xffFFFFFF;
        float roundRate = rate;
        Paint pt = new Paint();
        Bitmap outBitmap = Bitmap.createBitmap(bitmap.getWidth(),
                bitmap.getHeight(), Config.ARGB_8888);
        Canvas can = new Canvas(outBitmap);
        Rect rt = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
        RectF rf = new RectF(rt);
        pt.setAntiAlias(true);
        can.drawARGB(0, 0, 0, 0);
        pt.setColor(color);
        can.drawRoundRect(rf, roundRate, roundRate, pt);
        pt.setXfermode(new PorterDuffXfermode(Mode.SRC_IN));
        can.drawBitmap(bitmap, rt, rt, pt);
        return outBitmap;
    }

}
