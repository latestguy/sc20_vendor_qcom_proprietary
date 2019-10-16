/*
 * Copyright (c) 2014-2015 pci-suntektech Technologies, Inc.  All Rights Reserved.
 * pci-suntektech Technologies Proprietary and Confidential.
 */

package com.suntek.mway.rcs.publicaccount.ui;

import com.android.vcard.VCardParser;
import com.android.vcard.VCardParser_V21;
import com.android.vcard.exception.VCardException;
import com.suntek.mway.rcs.publicaccount.util.CommonUtil;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnClickListener;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.RemoteException;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import android.telephony.SubscriptionManager;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.suntek.mway.rcs.client.aidl.constant.Constants;
import com.suntek.mway.rcs.client.aidl.plugin.entity.pubacct.PublicMessage;
import com.suntek.mway.rcs.client.aidl.plugin.entity.pubacct.PublicAccountConstant;
import com.suntek.mway.rcs.client.aidl.plugin.entity.profile.TelephoneModel;
import com.suntek.mway.rcs.client.api.message.MessageApi;
import com.suntek.mway.rcs.client.api.exception.ServiceDisconnectedException;
import com.suntek.mway.rcs.client.api.basic.BasicApi;
import com.suntek.mway.rcs.nativeui.RcsNativeUIApp;
import com.suntek.mway.rcs.publicaccount.R;
import com.suntek.mway.rcs.publicaccount.data.PublicConversation;
import com.suntek.mway.rcs.publicaccount.data.PublicMessageItem;
import com.suntek.mway.rcs.publicaccount.data.RcsGeoLocation;
import com.suntek.mway.rcs.publicaccount.data.RcsGeoLocationParser;
import com.suntek.mway.rcs.publicaccount.data.RcsPropertyNode;
import com.suntek.mway.rcs.publicaccount.data.RcsVcardNode;
import com.suntek.mway.rcs.publicaccount.data.RcsVcardNodeBuilder;


public class PAMessageUtil {

    private static final String EXIT_AFTER_RECORD = "exit_after_record";

    public static final Uri SCRAP_CONTENT_URI = Uri.parse("content://mms_temp_file/scrapSpace");

    public static final String RCS_MMS_VCARD_PATH = "sdcard/rcs/" + "mms.vcf";

    public static final String MIMETYPE_RCS = "vnd.android.cursor.item/rcs";

    public static final String RCS_MSG_IMAGE_TYPE_GIF = "image/gif";

    public static final int SUB1 = 0;  // for DSDS product of slot one
    public static final int SUB2 = 1;  // for DSDS product of slot two

    private static final int RCS_SELECT_SYSTEM = 0;

    private static final int RCS_SELECT_EXTERNAL = 1;

    private static final int RCS_SELECT_LOCAL = 2;

    public static String getLastMessageStr(Context context, PublicConversation conversation) {
        int msgType = conversation.getRcsMessageType();
        String message = "";
        switch (msgType) {
            case Constants.MessageConstants.CONST_MESSAGE_AUDIO:
                message = context.getString(R.string.msg_type_audio);
                break;
            case Constants.MessageConstants.CONST_MESSAGE_CONTACT:
                message = context.getString(R.string.msg_type_contact);
                break;
            case Constants.MessageConstants.CONST_MESSAGE_IMAGE:
                message = context.getString(R.string.msg_type_image);
                break;
            case Constants.MessageConstants.CONST_MESSAGE_VIDEO:
                message = context.getString(R.string.msg_type_video);
                break;
            case Constants.MessageConstants.CONST_MESSAGE_MAP:
                message = context.getString(R.string.msg_type_location);
                break;
            case Constants.MessageConstants.CONST_MESSAGE_PUBLIC_ACCOUNT_ARTICLE:
                message = context.getString(R.string.msg_type_topic);
                break;
            case Constants.MessageConstants.CONST_MESSAGE_TEXT:
                message = conversation.getLastMessage();
                break;
            default:
                break;
        }
        return message;
    }

    public static void selectPAImage(Context context, int requestCode) {
        selectPAMediaByType(context, requestCode, "image/*", false);
    }

    private static void selectPAMediaByType(Context context, int requestCode, String contentType,
            boolean localFilesOnly) {
        if (context instanceof Activity) {
            Intent it = new Intent(Intent.ACTION_GET_CONTENT);
            it.setType(contentType);
            if (localFilesOnly) {
                it.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
            }
            Intent wrapperIntent = Intent.createChooser(it, null);
            ((Activity)context).startActivityForResult(wrapperIntent, requestCode);
        }
    }

    public static void capturePAPicture(Activity activity, int requestCode) {
        Intent it = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        it.putExtra(MediaStore.EXTRA_OUTPUT, SCRAP_CONTENT_URI);
        activity.startActivityForResult(it, requestCode);
    }

    public static void selectPAAudio(final Activity activity, final int requestCode) {
        String[] items = null;
        if(isRcsOnline()){
            items = new String[3];
            items[RCS_SELECT_LOCAL] = activity.getString(R.string.local_audio_item);
        }else{
            items = new String[2];
        }
        items[RCS_SELECT_EXTERNAL] = activity.getString(R.string.rcs_external_audio_item);
        items[RCS_SELECT_SYSTEM] = activity.getString(R.string.rcs_system_audio_item);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(activity,
                android.R.layout.simple_list_item_1, android.R.id.text1, items);
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        AlertDialog dialog = builder.setTitle(activity.getString(R.string.rcs_select_audio))
                .setAdapter(adapter, new OnClickListener() {
                    @SuppressWarnings("deprecation")
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent selectAudioIntent = null;
                        switch (which) {
                            case RCS_SELECT_SYSTEM:
                                selectAudioIntent = new Intent(RingtoneManager
                                        .ACTION_RINGTONE_PICKER);
                                selectAudioIntent.putExtra(RingtoneManager
                                        .EXTRA_RINGTONE_SHOW_DEFAULT, false);
                                selectAudioIntent.putExtra(RingtoneManager
                                        .EXTRA_RINGTONE_INCLUDE_DRM, false);
                                selectAudioIntent.putExtra(RingtoneManager
                                        .EXTRA_RINGTONE_SHOW_SILENT, false);
                                selectAudioIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE,
                                        activity.getString(R.string.rcs_select_audio));
                                activity.startActivityForResult(selectAudioIntent, requestCode);
                                break;
                            case RCS_SELECT_LOCAL:
                                selectAudioIntent = new Intent();
                                selectAudioIntent.setAction(Intent.ACTION_GET_CONTENT);
                                selectAudioIntent.setType("audio/*");
                                activity.startActivityForResult(selectAudioIntent,
                                        PAConversationActivity.REQUEST_SELECT_LOCAL_AUDIO);
                                break;
                            case RCS_SELECT_EXTERNAL:
                                selectAudioIntent = new Intent();
                                selectAudioIntent.setAction(Intent.ACTION_PICK);
                                selectAudioIntent.setData(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI);
                                activity.startActivityForResult(selectAudioIntent, requestCode);
                                break;
                        }
                    }
                }).create();
        dialog.show();
    }

    public static String getMyRcsRawContactId(Context context) {
        Uri uri = Uri.parse("content://com.android.contacts/profile/data/");
        String rawContactId = null;
        Cursor cursor = context.getContentResolver().query(uri, new String[] {
            "raw_contact_id"
        }, null, null, null);
        if (cursor != null) {
            if (cursor.moveToNext()) {
                rawContactId = cursor.getString(0);
                cursor.close();
                cursor = null;
            }
        }
        return rawContactId;
    }

    public static String getPAScrapPath(Context context, String fileName) {
        return context.getExternalCacheDir().getAbsolutePath() + "/" + fileName;
    }

    public static int getDuration(String path) {
        MediaPlayer player = new MediaPlayer();
        int duration = 0;
        try {
            player.setDataSource(path);
            player.prepare();
            duration = player.getDuration() / 1000;
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
        return duration;
    }

    public static String getPARealPathFromURI(Context context, Uri contentUri) {
        String[] proj = {
            MediaStore.Images.Media.DATA
        };
        Cursor cursor = context.getContentResolver().query(contentUri, proj, null, null, null);
        String path = null;
        if (cursor != null && cursor.moveToFirst()) {
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            path = cursor.getString(column_index);
        }
        if (cursor != null) {
            cursor.close();
        }
        return path;
    }

    public static long getPAAudioMaxTime() {
        try {
            return MessageApi.getInstance().getAudioMaxDuration();
        } catch (ServiceDisconnectedException exception) {
            exception.printStackTrace();
            return 0;
        } catch (RemoteException e) {
            e.printStackTrace();
            return 0;
        }
    }

    public static boolean isPADownloadsDoc(Uri docUri) {
        return "com.android.providers.downloads.documents".equals(docUri.getAuthority());
    }

    public static long getPAVideoMaxTime() {
        try {
            return MessageApi.getInstance().getVideoMaxDuration();
        } catch (ServiceDisconnectedException exception) {
            exception.printStackTrace();
            return 0;
        } catch (RemoteException e) {
            e.printStackTrace();
            return 0;
        }
    }

    public static String getPAScrapPath(Context context) {
        return getPAScrapPath(context, ".temp.jpg");
    }

    public static long getPAVideoFtMaxSize() {
        try {
            return MessageApi.getInstance().getVideoMaxSize();
        } catch (ServiceDisconnectedException exception) {
            exception.printStackTrace();
            return 0;
        } catch (RemoteException e) {
            e.printStackTrace();
            return 0;
        }
    }

    public static ArrayList<String> getPARawContactIdsByIds(Context context, Set<String> keySet) {
        ArrayList<String> list = new ArrayList<String>();
        Iterator<String> it = keySet.iterator();
        while (it.hasNext()) {
            String contactId = it.next();
            String rawContactId = "";
            Cursor cursor = context.getContentResolver().query(RawContacts.CONTENT_URI,
                    new String[] {
                        RawContacts._ID
                    }, RawContacts.CONTACT_ID + "=?", new String[] {
                        contactId
                    }, null);
            if (null != cursor) {
                if (cursor.moveToNext())
                    rawContactId = cursor.getString(0);
                cursor.close();
                cursor = null;
            }
            if (!TextUtils.isEmpty(rawContactId)) {
                list.add(rawContactId);
            }
        }
        return list;
    }

    @SuppressWarnings("finally")
    public static boolean setPAVcard(final Context context, Uri uri) {
        InputStream instream = null;
        FileOutputStream fout = null;
        try {
            AssetFileDescriptor fd = context.getContentResolver().openAssetFileDescriptor(uri, "r");
            instream = fd.createInputStream();
            File file = new File(RCS_MMS_VCARD_PATH);
            fout = new FileOutputStream(file);
            byte[] buffer = new byte[8000];
            int size = 0;
            while ((size = instream.read(buffer)) != -1) {
                fout.write(buffer, 0, size);
            }
            context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri
                    .fromFile(file)));
        } catch (IOException e) {
        } finally {
            if (null != instream) {
                try {
                    instream.close();
                } catch (IOException e) {
                    return false;
                }
            }
            if (null != fout) {
                try {
                    fout.close();
                } catch (IOException e) {
                    return false;
                }
            }
            return true;
        }
    }

    @SuppressLint("SimpleDateFormat")
    public static String getTimeStr(long time) {
        if (Locale.getDefault().getLanguage().equals(Locale.US)) {
            Date date = new Date(time);
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm");
            return dateFormat.format(date);
        }
        Date date = new Date(time);
        SimpleDateFormat dateFormat = new SimpleDateFormat(RcsNativeUIApp.getApplication()
                .getApplicationContext().getString(R.string.string_util_yyyy_mm_dd_hh_mm));
        return dateFormat.format(date);
    }

    public static void recordPAVideo(Activity activity, int requestCode) {
        Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 10.0);
        intent.putExtra(
                MediaStore.EXTRA_OUTPUT,
                CommonUtil.getOutputVideoFileUri(getPAScrapPath(activity,
                        "video/" + System.currentTimeMillis() + ".3gp")));
        activity.startActivityForResult(intent, requestCode);
    }

    public static Uri[] buildPAUris(Set<String> keySet, int newPickRecipientsCount) {
        Uri[] newUris = new Uri[newPickRecipientsCount];
        Iterator<String> it = keySet.iterator();
        int i = 0;
        while (it.hasNext()) {
            String id = it.next();
            newUris[i++] = ContentUris.withAppendedId(Phone.CONTENT_URI, Integer.parseInt(id));
            if (i == newPickRecipientsCount) {
                break;
            }
        }
        return newUris;
    }

    public static boolean isPAMediaDoc(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

    public static Uri getPAVcardUri() {
        Uri AUTHORITY_URI = Uri.parse("content://com.android.contacts");
        Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, "contacts");
        Uri CONTENT_MULTI_VCARD_URI = Uri.withAppendedPath(CONTENT_URI, "as_multi_vcard");
        return CONTENT_MULTI_VCARD_URI;
    }

    public static String getMessageContentType(PublicMessageItem chatMessage) {
        String contentType = "";
        switch (chatMessage.getRcsMessageType()) {
            case Constants.MessageConstants.CONST_MESSAGE_IMAGE:
                contentType = "image/*";
                break;
            case Constants.MessageConstants.CONST_MESSAGE_AUDIO:
                contentType = "audio/*";
                break;
            case Constants.MessageConstants.CONST_MESSAGE_VIDEO:
                contentType = "video/*";
                break;
            case Constants.MessageConstants.CONST_MESSAGE_MAP:
                contentType = "map/*";
                break;
            case Constants.MessageConstants.CONST_MESSAGE_CONTACT:
                contentType = "text/x-vCard";
                break;
            default:
            case Constants.MessageConstants.CONST_MESSAGE_TEXT:
                break;
        }
        return contentType;
    }

    public static boolean isPAFileDownload(String filePath, long fileSize) {
        if (TextUtils.isEmpty(filePath)) {
            return false;
        }
        boolean isDownload = false;
        File file = new File(filePath);
        if (file.exists() && file.length() >= fileSize) {
            isDownload = true;
        }
        return isDownload;
    }

    public static String getPAMsgDetails(Context context, String paName, PublicMessageItem chatMsg) {
        StringBuilder details = new StringBuilder();
        int sendType = chatMsg.getSendReceive();
        details.append(
                context.getResources().getString(R.string.dialog_message_details_body_type,
                        getMsgTypeStr(context, chatMsg.getRcsMessageType()))).append('\n');
        if (sendType != Constants.MessageConstants.CONST_DIRECTION_SEND) {
            details.append(
                    context.getResources().getString(R.string.dialog_message_details_body_sender,
                            paName))
                    .append('\n')
                    .append(context.getResources().getString(
                            R.string.dialog_message_details_body_receive_time,
                            PAMessageUtil.getTimeStr(chatMsg.getSendDate())));
        } else {
            details.append(
                    context.getResources().getString(R.string.dialog_message_details_body_receiver,
                            paName))
                    .append('\n')
                    .append(context.getResources().getString(
                            R.string.dialog_message_details_body_send_time,
                            PAMessageUtil.getTimeStr(chatMsg.getSendDate())));

        }
        return details.toString();
    }

    public static String getPAContentDataColumn(Context ctx, Uri uri, String sel, String[] selArgs) {

        final String column = "_data";
        final String[] proj = {
            column
        };
        Cursor c = null;

        try {
            c = ctx.getContentResolver().query(uri, proj, sel, selArgs, null);
            if (c != null && c.moveToFirst()) {
                final int columnIndex = c.getColumnIndexOrThrow(column);
                return c.getString(columnIndex);
            }
        } finally {
            if (c != null)
                c.close();
        }
        return null;
    }

    public static String getMsgTypeStr(Context context, int msgType) {
        String message = "";
        switch (msgType) {
            case Constants.MessageConstants.CONST_MESSAGE_AUDIO:
                message = context.getString(R.string.msg_type_audio);
                break;
            case Constants.MessageConstants.CONST_MESSAGE_CONTACT:
                message = context.getString(R.string.msg_type_contact);
                break;
            case Constants.MessageConstants.CONST_MESSAGE_IMAGE:
                message = context.getString(R.string.msg_type_image);
                break;
            case Constants.MessageConstants.CONST_MESSAGE_VIDEO:
                message = context.getString(R.string.msg_type_video);
                break;
            case Constants.MessageConstants.CONST_MESSAGE_MAP:
                message = context.getString(R.string.msg_type_location);
                break;
            case Constants.MessageConstants.CONST_MESSAGE_PUBLIC_ACCOUNT_ARTICLE:
                message = context.getString(R.string.msg_type_topic);
                break;
            case Constants.MessageConstants.CONST_MESSAGE_TEXT:
                message = context.getString(R.string.msg_type_text);
                break;
            default:
                break;
        }
        return message;
    }

    public static boolean isPAExternalStorageDoc(Uri docUri) {
        return "com.android.externalstorage.documents".equals(docUri.getAuthority());
    }

    public static void setHttpText(TextView tv, String htmlText) {
        // CharSequence c = Html.fromHtml(htmlText);
        tv.setText(htmlText);
        tv.setAutoLinkMask(Linkify.ALL);
        tv.setMovementMethod(LinkMovementMethod.getInstance());
    }

    public static boolean isRcsOnline() {
        try {
            return BasicApi.getInstance().isOnline();
        } catch (ServiceDisconnectedException e) {
            e.printStackTrace();
            return false;
        } catch (RemoteException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static String getPAContentUriPath(final Context context, final Uri uri) {

        final boolean isKk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;

        // File
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }
        // DocumentProvider
        else if (isKk && DocumentsContract.isDocumentUri(context, uri)) {
            // DownloadsProvider
            if (isPADownloadsDoc(uri)) {

                final String documentId = DocumentsContract.getDocumentId(uri);
                final Uri contentUri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"), Long.valueOf(documentId));

                return getPAContentDataColumn(context, contentUri, null, null);
            }
            // MediaProvider
            else if (isPAMediaDoc(uri)) {
                final String documentId = DocumentsContract.getDocumentId(uri);
                final String[] split = documentId.split(":");
                final String type = split[0];

                Uri contentPaUri = null;
                if ("video".equals(type)) {
                    contentPaUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    contentPaUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                } else if ("image".equals(type)) {
                    contentPaUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                }

                final String selection = "_id=?";
                final String[] selArgs = new String[] {
                    split[1]
                };
                return getPAContentDataColumn(context, contentPaUri, selection, selArgs);
            }
            // ExternalStorageProvider
            else if (isPAExternalStorageDoc(uri)) {
                final String documentId = DocumentsContract.getDocumentId(uri);
                final String[] split = documentId.split(":");
                final String type = split[0];

                if ("primary".equalsIgnoreCase(type)) {
                    return Environment.getExternalStorageDirectory() + "/" + split[1];
                }

                // TODO handle non-primary volumes
            }
        }
        // MediaStore (and general)
        else if ("content".equalsIgnoreCase(uri.getScheme())) {
            return getPAContentDataColumn(context, uri, null, null);
        }

        return null;
    }

    public static void recordPASound(Activity activity, int requestCode) {
        Intent it = new Intent(Intent.ACTION_GET_CONTENT);
        it.setType("audio/amr");
        it.setClassName("com.android.soundrecorder", "com.android.soundrecorder.SoundRecorder");
        it.putExtra(EXIT_AFTER_RECORD, true);
        activity.startActivityForResult(it, requestCode);
    }

    public static void selectPAVideo(Context context, int requestCode) {
        selectPAMediaByType(context, requestCode, "video/*", true);
    }

    public static ArrayList<RcsPropertyNode> openRcsVcardDetail(Context context, String filePath) {
        if (TextUtils.isEmpty(filePath)) {
            return null;
        }
        try {
            File file = new File(filePath);
            FileInputStream fis = new FileInputStream(file);

            RcsVcardNodeBuilder builder = new RcsVcardNodeBuilder();
            VCardParser parser = new VCardParser_V21();
            parser.addInterpreter(builder);
            parser.parse(fis);
            List<RcsVcardNode> vNodeList = builder.getVcardNodeList();
            ArrayList<RcsPropertyNode> propList = vNodeList.get(0).propNodeList;
            return propList;
        } catch (Exception e) {
            return null;
        }
    }

    public static void showDetailVcard(Context context, ArrayList<RcsPropertyNode> propList) {
        AlertDialog.Builder builder = new Builder(context);
        LayoutInflater inflater = LayoutInflater.from(context);
        View vcardView = inflater.inflate(R.layout.rcs_vcard_detail, null);

        ImageView photoView = (ImageView)vcardView.findViewById(R.id.vcard_photo);
        TextView nameView, priNumber, addrText, comName, positionText;
        nameView = (TextView)vcardView.findViewById(R.id.vcard_name);
        priNumber = (TextView)vcardView.findViewById(R.id.vcard_number);
        addrText = (TextView)vcardView.findViewById(R.id.vcard_addre);
        positionText = (TextView)vcardView.findViewById(R.id.vcard_position);
        comName = (TextView)vcardView.findViewById(R.id.vcard_com_name);

        ArrayList<String> numberList = new ArrayList<String>();
        for (RcsPropertyNode propertyNode : propList) {
            if ("FN".equals(propertyNode.name)) {
                if (!TextUtils.isEmpty(propertyNode.value)) {
                    nameView.setText(context.getString(R.string.vcard_name) + propertyNode.value);
                }
            } else if ("TEL".equals(propertyNode.name)) {
                if (!TextUtils.isEmpty(propertyNode.value)) {
                    String numberTypeStr = getPhoneNumberTypeStr(context, propertyNode);
                    if (!TextUtils.isEmpty(numberTypeStr)) {
                        numberList.add(numberTypeStr);
                    }
                }
            } else if ("ADR".equals(propertyNode.name)) {
                if (!TextUtils.isEmpty(propertyNode.value)) {
                    String address = propertyNode.value;
                    address = address.replaceAll(";", "");
                    addrText.setText(context.getString(R.string.vcard_compony_addre) + ":"
                            + address);
                }
            } else if ("ORG".equals(propertyNode.name)) {
                if (!TextUtils.isEmpty(propertyNode.value)) {
                    comName.setText(context.getString(R.string.vcard_compony_name) + ":"
                            + propertyNode.value);
                }
            } else if ("TITLE".equals(propertyNode.name)) {
                if (!TextUtils.isEmpty(propertyNode.value)) {
                    positionText.setText(context.getString(R.string.vcard_compony_position) + ":"
                            + propertyNode.value);
                }
            } else if ("PHOTO".equals(propertyNode.name)) {
                if (propertyNode.valueBytes != null) {
                    byte[] bytes = propertyNode.valueBytes;
                    final Bitmap vcardBitmap = BitmapFactory
                            .decodeByteArray(bytes, 0, bytes.length);
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
            vcardView.findViewById(R.id.vcard_middle).setVisibility(View.VISIBLE);
            LinearLayout linearLayout = (LinearLayout)vcardView
                    .findViewById(R.id.other_number_layout);
            addNumberTextView(context, numberList, linearLayout);
        }
        builder.setTitle(R.string.vcard_detail_info);
        builder.setView(vcardView);
        builder.create();
        builder.show();
    }

    public static String getPhoneNumberTypeStr(Context context, RcsPropertyNode propertyNode) {
        String numberTypeStr = "";
        if (null == propertyNode.paramMapTYPE || propertyNode.paramMapTYPE.size() == 0) {
            return numberTypeStr;
        }
        String number = propertyNode.value;
        if (propertyNode.paramMapTYPE.size() == 2) {
            if (propertyNode.paramMapTYPE.contains("FAX")
                    && propertyNode.paramMapTYPE.contains("HOME")) {
                numberTypeStr = context.getString(R.string.vcard_number_fax_home) + number;
            } else if (propertyNode.paramMapTYPE.contains("FAX")
                    && propertyNode.paramMapTYPE.contains("WORK")) {
                numberTypeStr = context.getString(R.string.vcard_number_fax_work) + number;
            } else if (propertyNode.paramMapTYPE.contains("PREF")
                    && propertyNode.paramMapTYPE.contains("WORK")) {
                numberTypeStr = context.getString(R.string.vcard_number_pref_work) + number;
            } else if (propertyNode.paramMapTYPE.contains("CELL")
                    && propertyNode.paramMapTYPE.contains("WORK")) {
                numberTypeStr = context.getString(R.string.vcard_number_call_work) + number;
            } else if (propertyNode.paramMapTYPE.contains("WORK")
                    && propertyNode.paramMapTYPE.contains("PAGER")) {
                numberTypeStr = context.getString(R.string.vcard_number_work_pager) + number;
            } else {
                numberTypeStr = context.getString(R.string.vcard_number_other) + number;
            }
        } else {
            if (propertyNode.paramMapTYPE.contains("CELL")) {
                numberTypeStr = context.getString(R.string.vcard_number) + number;
            } else if (propertyNode.paramMapTYPE.contains("HOME")) {
                numberTypeStr = context.getString(R.string.vcard_number_home) + number;
            } else if (propertyNode.paramMapTYPE.contains("WORK")) {
                numberTypeStr = context.getString(R.string.vcard_number_work) + number;
            } else if (propertyNode.paramMapTYPE.contains("PAGER")) {
                numberTypeStr = context.getString(R.string.vcard_number_pager) + number;
            } else if (propertyNode.paramMapTYPE.contains("VOICE")) {
                numberTypeStr = context.getString(R.string.vcard_number_other) + number;
            } else if (propertyNode.paramMapTYPE.contains("CAR")) {
                numberTypeStr = context.getString(R.string.vcard_number_car) + number;
            } else if (propertyNode.paramMapTYPE.contains("ISDN")) {
                numberTypeStr = context.getString(R.string.vcard_number_isdn) + number;
            } else if (propertyNode.paramMapTYPE.contains("PREF")) {
                numberTypeStr = context.getString(R.string.vcard_number_pref) + number;
            } else if (propertyNode.paramMapTYPE.contains("FAX")) {
                numberTypeStr = context.getString(R.string.vcard_number_fax) + number;
            } else if (propertyNode.paramMapTYPE.contains("TLX")) {
                numberTypeStr = context.getString(R.string.vcard_number_tlx) + number;
            } else if (propertyNode.paramMapTYPE.contains("MSG")) {
                numberTypeStr = context.getString(R.string.vcard_number_msg) + number;
            } else {
                numberTypeStr = context.getString(R.string.vcard_number_other) + number;
            }
        }
        return numberTypeStr;
    }

    private static void addNumberTextView(Context context, ArrayList<String> numberList,
            LinearLayout linearLayout) {
        for (int i = 0; i < numberList.size(); i++) {
            TextView textView = new TextView(context);
            textView.setText(numberList.get(i));
            linearLayout.addView(textView);
        }
    }

    public static boolean isMsimIccCardActive() {
        if (isMultiSimEnabledMms()) {
            if (isIccCardActivated(PAMessageUtil.SUB1) && isIccCardActivated(PAMessageUtil.SUB2)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Decide whether the current product  is DSDS in MMS
     */
    public static boolean isMultiSimEnabledMms() {
        return TelephonyManager.getDefault().isMultiSimEnabled();
    }

    /**
     * Return whether the card is activated according to Subscription
     * used for DSDS
     */
    public static boolean isIccCardActivated(int subscription) {
        TelephonyManager tm = TelephonyManager.getDefault();
        final int simState = tm.getSimState(subscription);
        return (simState != TelephonyManager.SIM_STATE_ABSENT)
                    && (simState != TelephonyManager.SIM_STATE_UNKNOWN);
    }

    public static Drawable getMultiSimIcon(Context context, int subscription) {
        if (context == null) {
            // If the context is null, return 0 as no resource found.
            return null;
        }

        int subId[] = SubscriptionManager.getSubId(subscription);
        final TelecomManager telecomManager = (TelecomManager) context
                .getSystemService(Context.TELECOM_SERVICE);
        List<PhoneAccountHandle> pHandles = telecomManager.getCallCapablePhoneAccounts();
        PhoneAccountHandle phoneAccountHandle = null;
        for (PhoneAccountHandle itorator : pHandles) {
            if (String.valueOf(subId[0]).equals(itorator.getId())) {
                phoneAccountHandle = itorator;
            }
        }

        if (phoneAccountHandle == null) {
            return null;
        }
        final PhoneAccount account = telecomManager
                .getPhoneAccount(phoneAccountHandle);
        // return account.createIconDrawable(context);
        return null;
    }
}
