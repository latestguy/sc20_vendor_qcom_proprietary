/*
 * Copyright (c) 2014-2015 pci-suntektech Technologies, Inc.  All Rights Reserved.
 * pci-suntektech Technologies Proprietary and Confidential.
 */

package com.suntek.mway.rcs.publicaccount.ui;


import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.RemoteException;
import android.provider.ContactsContract.Contacts;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.suntek.mway.rcs.client.aidl.constant.Constants;
import com.suntek.mway.rcs.client.aidl.plugin.entity.pubacct.PublicAccountConstant;
import com.suntek.mway.rcs.client.aidl.plugin.entity.pubacct.PublicMediaMessage;
import com.suntek.mway.rcs.client.aidl.plugin.entity.pubacct.PublicMessage;
import com.suntek.mway.rcs.client.aidl.plugin.entity.pubacct.PublicTextMessage;
import com.suntek.mway.rcs.client.aidl.plugin.entity.pubacct.PublicTopicMessage;
import com.suntek.mway.rcs.client.aidl.plugin.entity.pubacct.PublicTopicMessage.PublicTopicContent;
import com.suntek.mway.rcs.client.aidl.plugin.entity.pubacct.PublicMediaMessage;
import com.suntek.mway.rcs.client.aidl.plugin.entity.pubacct.PublicMediaMessage.PublicMediaContent;
import com.suntek.mway.rcs.client.aidl.service.entity.GroupChat;
import com.suntek.mway.rcs.client.api.basic.BasicApi;
import com.suntek.mway.rcs.client.api.message.MessageApi;
import com.suntek.mway.rcs.client.api.exception.FileSuffixException;
import com.suntek.mway.rcs.client.api.exception.FileTooLargeException;
import com.suntek.mway.rcs.client.api.exception.FileDurationException;
import com.suntek.mway.rcs.client.api.exception.FileNotExistsException;
import com.suntek.mway.rcs.client.api.exception.ServiceDisconnectedException;
import com.suntek.mway.rcs.publicaccount.data.PublicConversation;
import com.suntek.mway.rcs.publicaccount.data.PublicMessageItem;
import com.suntek.mway.rcs.publicaccount.data.PublicWorkingMessage;
import com.suntek.mway.rcs.publicaccount.data.RcsGeoLocation;
import com.suntek.mway.rcs.publicaccount.data.RcsGeoLocationParser;
import com.suntek.mway.rcs.nativeui.RcsNativeUIApp;
import com.suntek.mway.rcs.publicaccount.util.CommonUtil;
import com.suntek.mway.rcs.publicaccount.util.PublicAccountUtils;
import com.suntek.mway.rcs.publicaccount.R;

public class PASendMessageUtil {

    private static final String RCS_MMS_VCARD_PATH = "sdcard/rcs/" + "mms.vcf";
    public static final int RCS_IMAGE_QUALITY = 100;
    private static final int RCS_NOT_BURN_MESSAGE = -1;
    private static final boolean RCS_NOT_RECORD_MESSAGE = false;
    private static final int FORWARD_TO_CONTACT = 0;
    private static final int FORWARD_TO_CONVERSATION = 1;
    private static final int FORWARD_TO_GROUP = 2;
    private static final int FORWWARD_TO_PUBLIC_ACCOUNT = 3;

    private static final String LAUNCH_SELECT_CONTACT_ACTION_KITKAT =
            "com.android.contacts.action.MULTI_PICK";
    private static final String LAUNCH_SELECT_CONTACT_ACTION =
            "com.android.mms.ui.SelectRecipientsList";
    private static final String LAUNCH_SELECT_CONTACT_MODE = "mode";
    private static final String LAUNCH_SELECT_CONVERSATION_ACTION =
            "com.suntek.rcs.action.ACTION_PICK_CONVERSATION";
    private static final String LAUNCH_CONVERSATION_MODE = "select_conversation";
    private static final String LAUNCH_SELECT_GROUP_ACTION_KITKAT =
            "com.suntek.rcs.action.ACTION_PICK_CONTACTGROUPS";
    private static final String LAUNCH_SELECT_PUBLICACCOUNT_ACTION =
            "com.suntek.mway.rcs.nativeui.ui.PUBLIC_ACCOUNT_ACTIVITY";
    private static final String LAUNCH_SELECT_PUBLICACCOUNT_MODE = "forward";

    private static final String FROM_PUBLICACCOUNT = "from_publicAccount";

    public static void sendPublicMessage(Context context,
            PublicWorkingMessage publicWorkingMessage) {

        int msgType = publicWorkingMessage.getRcsMessageType();
        String uuid = publicWorkingMessage.getPublicAccountUuid();
        String message = publicWorkingMessage.getRcsTextMessage();
        long threadId = publicWorkingMessage.getRcsThreadId();
        String filePath = publicWorkingMessage.getRcsFilePath();
        int imageQuality = publicWorkingMessage.getRcsImageQuality();
        int recordTime = publicWorkingMessage.getRcsRecordTime();
        boolean isRecord = publicWorkingMessage.isRcsIsRecord();
        double latitude = publicWorkingMessage.getLat();
        double longitude = publicWorkingMessage.getLon();
        String info = publicWorkingMessage.getMapInfo();
        try {
            switch (msgType) {
                case Constants.MessageConstants.CONST_MESSAGE_TEXT:
                    MessageApi.getInstance().sendTextToPublicAccount(uuid, threadId, message);
                    break;
                case Constants.MessageConstants.CONST_MESSAGE_IMAGE:
                    if (imageQuality == RCS_IMAGE_QUALITY) {
                        MessageApi.getInstance().sendImageToPublicAccount(uuid, threadId, filePath,
                                RCS_IMAGE_QUALITY, false);
                    } else {
                        MessageApi.getInstance().sendImageToPublicAccount(uuid, threadId, filePath,
                                imageQuality, false);
                    }
                    break;
                case Constants.MessageConstants.CONST_MESSAGE_AUDIO:
                    MessageApi.getInstance().sendAudioToPublicAccount(uuid, threadId, filePath,
                            recordTime, isRecord);
                    break;
                case Constants.MessageConstants.CONST_MESSAGE_VIDEO:
                    MessageApi.getInstance().sendVideoToPublicAccount(uuid, threadId, filePath,
                            recordTime, isRecord);
                    break;
                case Constants.MessageConstants.CONST_MESSAGE_CONTACT:
                    MessageApi.getInstance().sendVcardToPublicAccount(uuid, threadId,
                            PAMessageUtil.RCS_MMS_VCARD_PATH);
                    break;
                case Constants.MessageConstants.CONST_MESSAGE_MAP:
                    MessageApi.getInstance().sendLocationToPublicAccount(uuid, threadId, latitude,
                            longitude, info);
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            disposeSendPublicMessageException(context, e, msgType);
        }
    }

    private static void disposeSendPublicMessageException(Context context, Exception exception,
            int msgType) {
        exception.printStackTrace();
    }

    public static void forwardTopicToConversation(Activity context, Intent data,
            PublicTopicContent topicContent) {
        try {
            long threadId = -1;
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT) {
                threadId = -1;
            } else {
                threadId = data.getLongExtra("selectThreadId", -1);
            }
            String[] numbers = data.getStringArrayExtra("numbers");
            GroupChat groupChat = null;
            if (data.hasExtra("GroupChat")) {
                groupChat = data.getParcelableExtra("GroupChat");
            }
            boolean suc = false;
            if (groupChat != null) {
                forwardTopicToGroupChat(context, topicContent, threadId, groupChat);
            } else {
                forwardTopicToNumber(context, topicContent, Arrays.asList(numbers));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void forwardTopicToNumber(Activity context, PublicTopicContent topicContent,
            List<String> numberList) {
        try {
            MessageApi messageApi = MessageApi.getInstance();
            long threadId = messageApi.getThreadId(numberList.get(0));
            String title = topicContent.getTitle();
            String bodyLink = PublicAccountUtils.replaceBlank(topicContent.getBodyLink());
            String textSend = title + "\n" +bodyLink;
            if (numberList != null && numberList.size() == 1) {
                messageApi.sendText(numberList.get(0), threadId, textSend, RCS_NOT_BURN_MESSAGE);
            } else {
                messageApi.sendText(numberList, threadId, textSend, RCS_NOT_BURN_MESSAGE);
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public static void forwardTopicToGroupChat(Activity context, PublicTopicContent topicContent,
            long threadId, GroupChat groupChat) {
        try {
            MessageApi messageApi = MessageApi.getInstance();
            String title = topicContent.getTitle();
            String bodyLink = PublicAccountUtils.replaceBlank(topicContent.getBodyLink());
            String textSend = title + "\n" +bodyLink;
            messageApi.sendTextToGroupChat(groupChat.getId(), threadId, textSend);
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public static void forwardRcsMessageToNumber(Activity context, PublicMessageItem message,
            List<String> numberList) {
        int sendReceiveType = message.getSendReceive();
        if (sendReceiveType == Constants.MessageConstants.CONST_DIRECTION_SEND) {
            forwardSendMessage(message, numberList);
        } else {
            forwardReceiveMessage(context, message, numberList);
        }
    }

    private static void forwardReceiveMessage(Context context, PublicMessageItem message,
            List<String> numberList) {
        int msgType = message.getRcsMessageType();
        try {
            String msgContent = message.getMessageBody();
            String filePath = "";
            int duration = 0;
            MessageApi messageApi = MessageApi.getInstance();
            PublicMessage publicMessage = messageApi.parsePublicMessage(msgType, msgContent);
            if (msgType == Constants.MessageConstants.CONST_MESSAGE_AUDIO
                    || msgType == Constants.MessageConstants.CONST_MESSAGE_VIDEO
                    || msgType == Constants.MessageConstants.CONST_MESSAGE_IMAGE) {
                PublicMediaMessage mMsg = (PublicMediaMessage)publicMessage;
                PublicMediaContent paContent = mMsg.getMedia();
                String url = paContent.getOriginalLink();
                filePath = CommonUtil.getFileCacheLocalPath(msgType, url);
                if (msgType != Constants.MessageConstants.CONST_MESSAGE_IMAGE) {
                    duration = Integer.parseInt(paContent.getDuration());
                }
            }
            if (numberList != null && numberList.size() == 1) {
                long threadId = messageApi.getThreadId(numberList.get(0));
                switch (msgType) {
                    case Constants.MessageConstants.CONST_MESSAGE_TEXT:
                        PublicTextMessage textMsg = (PublicTextMessage)publicMessage;
                        String text = textMsg.getContent();
                        messageApi.sendText(numberList.get(0), threadId, text, RCS_NOT_BURN_MESSAGE);
                        break;
                    case Constants.MessageConstants.CONST_MESSAGE_IMAGE:

                        messageApi.sendImage(numberList.get(0), threadId, filePath, RCS_IMAGE_QUALITY,
                                RCS_NOT_RECORD_MESSAGE, RCS_NOT_BURN_MESSAGE);
                        break;
                    case Constants.MessageConstants.CONST_MESSAGE_AUDIO:
                        messageApi.sendAudio(numberList.get(0), threadId, filePath, (int)duration,
                                RCS_NOT_RECORD_MESSAGE, RCS_NOT_BURN_MESSAGE);
                        break;
                    case Constants.MessageConstants.CONST_MESSAGE_VIDEO:
                        messageApi.sendVideo(numberList.get(0), threadId, filePath, (int)duration,
                                RCS_NOT_RECORD_MESSAGE, RCS_NOT_BURN_MESSAGE);
                        break;
                    case Constants.MessageConstants.CONST_MESSAGE_CONTACT:
                        PublicTextMessage tMsg = (PublicTextMessage)publicMessage;
                        String vcardFilePath = tMsg.getContent();
                        messageApi.sendVcard(numberList.get(0), threadId, vcardFilePath,
                                RCS_NOT_BURN_MESSAGE);
                        break;
                    case Constants.MessageConstants.CONST_MESSAGE_MAP:
                        PublicTextMessage mapMsg = (PublicTextMessage)publicMessage;
                        String mapPath = mapMsg.getContent();
                        RcsGeoLocation geo = PASendMessageUtil.readPaMapXml(mapPath);
                        double lat = geo.getLat();
                        double lng = geo.getLng();
                        String label = geo.getLabel();
                        messageApi.sendLocation(numberList.get(0), threadId, lat, lng, label,
                                RCS_NOT_BURN_MESSAGE);
                        break;
                    default:
                        break;
                }
            } else {
                long threadId = messageApi.getThreadId(numberList);
                switch (msgType) {
                    case Constants.MessageConstants.CONST_MESSAGE_TEXT:
                        PublicTextMessage textMsg = (PublicTextMessage)publicMessage;
                        String text = textMsg.getContent();
                        messageApi.sendText(numberList, threadId, text, RCS_NOT_BURN_MESSAGE);
                        break;
                    case Constants.MessageConstants.CONST_MESSAGE_IMAGE:

                        messageApi.sendImage(numberList, threadId, filePath, RCS_IMAGE_QUALITY,
                                RCS_NOT_RECORD_MESSAGE, RCS_NOT_BURN_MESSAGE);
                        break;
                    case Constants.MessageConstants.CONST_MESSAGE_AUDIO:
                        messageApi.sendAudio(numberList, threadId, filePath, (int)duration,
                                RCS_NOT_RECORD_MESSAGE, RCS_NOT_BURN_MESSAGE);
                        break;
                    case Constants.MessageConstants.CONST_MESSAGE_VIDEO:
                        messageApi.sendVideo(numberList, threadId, filePath, (int)duration,
                                RCS_NOT_RECORD_MESSAGE, RCS_NOT_BURN_MESSAGE);
                        break;
                    case Constants.MessageConstants.CONST_MESSAGE_CONTACT:
                        PublicTextMessage tMsg = (PublicTextMessage)publicMessage;
                        String vcardFilePath = tMsg.getContent();
                        messageApi.sendVcard(numberList, threadId, vcardFilePath,
                                RCS_NOT_BURN_MESSAGE);
                        break;
                    case Constants.MessageConstants.CONST_MESSAGE_MAP:
                        PublicTextMessage mapMsg = (PublicTextMessage)publicMessage;
                        String mapPath = mapMsg.getContent();
                        RcsGeoLocation geo = PASendMessageUtil.readPaMapXml(mapPath);
                        double lat = geo.getLat();
                        double lng = geo.getLng();
                        String label = geo.getLabel();
                        messageApi.sendLocation(numberList, threadId, lat, lng, label,
                                RCS_NOT_BURN_MESSAGE);
                        break;
                    default:
                        break;
                }
            }
        } catch (Exception e) {
            disposeSendPublicMessageException(context, e, msgType);
        }

    }
    private static void forwardSendMessage(PublicMessageItem message,
            List<String> numberList){
        try {
            MessageApi messageApi = MessageApi.getInstance();
            if (numberList != null && numberList.size() == 1) {
                long threadId = messageApi.getThreadId(numberList.get(0));
                messageApi.forward(message.getId(), threadId,
                        numberList.get(0), RCS_NOT_BURN_MESSAGE);
            } else {
                long threadId = messageApi.getThreadId(numberList);
                messageApi.forward(message.getId(), threadId,
                        numberList, RCS_NOT_BURN_MESSAGE);
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        } catch (ServiceDisconnectedException e) {
            e.printStackTrace();
        }
    }

    public static void forwardRcsMessageToPublicAccount(Context context, String uuid,
            PublicMessageItem messageItem) {
        if (TextUtils.isEmpty(uuid)) {
            return ;
        }
        long threadId = getServiceThreadId(uuid);
        PublicWorkingMessage workingMessage =
                PublicWorkingMessage.getWorkingMessageFromPublicMessageItem(messageItem);
        workingMessage.setRcsThreadId(threadId);
        workingMessage.setPublicAccountUuid(uuid);
        sendPublicMessage(context, workingMessage);
    }

    public static void forwardRcsMessageToConversation(Activity context, Intent data,
            PublicMessageItem message) {
        try {
            if (!PASendMessageUtil.checkForwardFilePathExist(context, message))
                return;
            long threadId = -1;
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT) {
                threadId = -1;
            } else {
                threadId = data.getLongExtra("selectThreadId", -1);
            }
            String[] numbers = data.getStringArrayExtra("numbers");
            GroupChat groupChat = null;
            if (data.hasExtra("GroupChat")) {
                groupChat = data.getParcelableExtra("GroupChat");
            }
            boolean suc = false;
            if (groupChat != null) {
                forwardRcsMessageToGroupChat(context, message, threadId, groupChat);
            } else {
                forwardRcsMessageToNumber(context, message, Arrays.asList(numbers));
            }
        } catch (RemoteException exception) {
            exception.printStackTrace();
        } catch (ServiceDisconnectedException e) {
            e.printStackTrace();
        }
    }

    private static void forwardRcsMessageToGroupChat(Context context, PublicMessageItem message,
            long threadId, GroupChat groupChat) throws RemoteException,
            ServiceDisconnectedException {
        if(!isReceiveMsg(message)){
            MessageApi.getInstance().forwardToGroupChat(message.getId(), threadId,
                    groupChat.getId());
        } else {
            forwardReceiverMessageToGroupChat(context, message, threadId, groupChat);
        }
    }
    private static void forwardReceiverMessageToGroupChat(Context context,
            PublicMessageItem message, long threadId, GroupChat groupChat) {
        int msgType = message.getRcsMessageType();
        long groupId = groupChat.getId();
        MessageApi messageApi = MessageApi.getInstance();

        String msgContent = message.getMessageBody();
        String filePath = "";
        int duration = 0;

        PublicMessage publicMessage = messageApi.parsePublicMessage(msgType, msgContent);
        if (msgType == Constants.MessageConstants.CONST_MESSAGE_AUDIO
                || msgType == Constants.MessageConstants.CONST_MESSAGE_VIDEO
                || msgType == Constants.MessageConstants.CONST_MESSAGE_IMAGE) {
            PublicMediaMessage mMsg = (PublicMediaMessage)publicMessage;
            PublicMediaContent paContent = mMsg.getMedia();
            String url = paContent.getOriginalLink();
            filePath = CommonUtil.getFileCacheLocalPath(msgType, url);
            if (msgType != Constants.MessageConstants.CONST_MESSAGE_IMAGE) {
                duration = Integer.parseInt(paContent.getDuration());
            }
        }
        try {
            switch (msgType) {
                case Constants.MessageConstants.CONST_MESSAGE_TEXT:
                    PublicTextMessage textMsg = (PublicTextMessage)publicMessage;
                    String text = textMsg.getContent();
                    messageApi.sendTextToGroupChat(groupId, threadId, text);
                    break;
                case Constants.MessageConstants.CONST_MESSAGE_IMAGE:
                    messageApi.sendImageToGroupChat(groupId,  threadId, filePath, RCS_IMAGE_QUALITY,
                            RCS_NOT_RECORD_MESSAGE);
                    break;
                case Constants.MessageConstants.CONST_MESSAGE_AUDIO:
                    messageApi.sendAudioToGroupChat(groupId, threadId, filePath, duration,
                            RCS_NOT_RECORD_MESSAGE);
                    break;
                case Constants.MessageConstants.CONST_MESSAGE_VIDEO:
                    messageApi.sendVideoToGroupChat(groupId, threadId, filePath, duration,
                            RCS_NOT_RECORD_MESSAGE);
                    break;
                case Constants.MessageConstants.CONST_MESSAGE_CONTACT:
                    PublicTextMessage tMsg = (PublicTextMessage)publicMessage;
                    String vcardFilePath = tMsg.getContent();
                    messageApi.sendVcardToGroupChat(groupId, threadId, vcardFilePath);
                    break;
                case Constants.MessageConstants.CONST_MESSAGE_MAP:
                    PublicTextMessage mapMsg = (PublicTextMessage)publicMessage;
                    String mapPath = mapMsg.getContent();
                    RcsGeoLocation geo = PASendMessageUtil.readPaMapXml(mapPath);
                    double lat = geo.getLat();
                    double lng = geo.getLng();
                    String label = geo.getLabel();
                    messageApi.sendLocationToGroupChat(groupId, threadId, lat, lng,
                            label);
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            disposeSendPublicMessageException(context, e, msgType);
        }
    }

    public static String getPaFilePath(PublicMessageItem chatMessage) {
        String filePath = chatMessage.getMessageFilePath();
        if (filePath != null) {
            File file = new File(filePath);
            if (isReceiveMsg(chatMessage)) {
                if (file.exists()) {
                    return filePath;
                }
            } else {
                if (file != null && file.exists()) {
                    String dir = file.getParent();
                    String newFilePath = dir + File.separator + chatMessage.getMessageBody();
                    if (!newFilePath.equals(filePath)) {
                        boolean suc = renameFile(filePath, newFilePath);
                        return suc ? newFilePath : null;
                    } else {
                        return filePath;
                    }
                } else {//may be forwarded
                    return getForwordFileName(filePath, chatMessage.getMessageBody());
                }
            }
        }
        return null;
    }

    public static String getForwordFileName(String sendFilePath, String fileName) {
        if (sendFilePath != null && sendFilePath.lastIndexOf("/") != -1) {
            sendFilePath = sendFilePath.substring(0, sendFilePath.lastIndexOf("/") + 1);
            return sendFilePath + fileName;
        } else {
            return null;
        }
    }

    public static boolean renameFile(String oldFilePath, String newFilePath) {
        if (TextUtils.isEmpty(oldFilePath) || TextUtils.isEmpty(newFilePath)) {
            return false;
        }
        File oldFile = new File(oldFilePath);
        File newFile = new File(newFilePath);
        return oldFile.renameTo(newFile);
    }

    public static RcsGeoLocation readPaMapXml(String filepath) {
        RcsGeoLocation geo = null;
        try {
            RcsGeoLocationParser handler = new RcsGeoLocationParser(new FileInputStream(filepath));
            geo = handler.getGeoLocation();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return geo;
    }

    public static boolean saveMessage(Context context, PublicMessageItem chatMessage) {
        InputStream input = null;
        FileOutputStream fout = null;
        try {
            if (chatMessage == null) {
                return false;
            }
            int msgType = chatMessage.getRcsMessageType();
            if (msgType != Constants.MessageConstants.CONST_MESSAGE_AUDIO
                    && msgType != Constants.MessageConstants.CONST_MESSAGE_VIDEO
                    && msgType != Constants.MessageConstants.CONST_MESSAGE_IMAGE) {
                return true; // we only save pictures, videos, and sounds.
            }
            String filePath = null;
            if (PublicMessageItem.isReceiveMessage(chatMessage)) {
                PublicMessage publicMessage = chatMessage.getPublicMessage();
                PublicMediaMessage mMsg = (PublicMediaMessage)publicMessage;
                PublicMediaContent paContent = mMsg.getMedia();
                String url = paContent.getOriginalLink();
                filePath = CommonUtil.getFileCacheLocalPath(msgType, url);
            } else {
                filePath = chatMessage.getMessageFilePath();
            }
            if (filePath == null) {
                Toast.makeText(context, R.string.file_not_exist, Toast.LENGTH_SHORT).show();
                return false;
            }
            if (isLoading(filePath, chatMessage.getmMessageFileSize())) {
                return false;
            }
            String saveDir = Environment.getExternalStorageDirectory() + "/"
                    + Environment.DIRECTORY_DOWNLOADS + "/";
            File dirFile = new File(saveDir);
            if (!dirFile.exists()) {
                dirFile.mkdirs();
            }
            String fileName = null;
            input = new FileInputStream(filePath);
            File file = getUniqueDes(saveDir + getFileName(filePath),
                    getFileExtensionName(filePath));
            fout = new FileOutputStream(file);

            byte[] buffer = new byte[1024];
            int size = 0;
            while ((size = input.read(buffer)) != -1) {
                fout.write(buffer, 0, size);
            }
            // Notify other applications listening to scanner events
            // that a media file has been added to the sd card
            context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri
                    .fromFile(file)));
        } catch (Exception e) {
            return false;
        } finally {
            if (null != input) {
                try {
                    input.close();
                } catch (IOException e) {
                    // Ignore
                    Log.e("RCS_UI", "IOException caught while closing stream", e);
                    return false;
                }
            }
            if (null != fout) {
                try {
                    fout.close();
                } catch (IOException e) {
                    // Ignore
                    Log.e("RCS_UI", "IOException caught while closing stream", e);
                    return false;
                }
            }
        }
        return true;
    }

    private static String getFileExtensionName(String filePath) {
        int index = filePath.lastIndexOf(".");
        return filePath.substring(index + 1);
    }

    private static String getFileName(String filePath) {
        int start = filePath.lastIndexOf("/");
        int end = filePath.lastIndexOf(".");
        return filePath.substring(start, end + 1);
    }

    private static boolean isLoading(String filePath, long fileSize) {
        if (TextUtils.isEmpty(filePath)) {
            return false;
        }
        File file = new File(filePath);
        if (file.exists() && file.length() < fileSize) {
            return true;
        } else {
            return false;
        }
    }

    private static File getUniqueDes(String base, String extension) {
        File file = new File(base + "." + extension);

        for (int i = 2; file.exists(); i++) {
            file = new File(base + "_" + i + "." + extension);
        }
        return file;
    }

    public static long getServiceThreadId(String paUuid) {
        return PublicAccountUtils.getTheadIdByNumber(RcsNativeUIApp.getApplication()
                .getApplicationContext(), paUuid);
    }

    public static boolean isReceiveMsg(PublicMessageItem cMsg) {
        if (cMsg.getSendReceive() ==
                Constants.MessageConstants.CONST_DIRECTION_SEND) {
            return false;
        } else {
            return true;
        }
    }

    public static boolean checkForwardFilePathExist(Context context, PublicMessageItem chatMessage) {
        if (chatMessage.getRcsMessageType() !=
                Constants.MessageConstants.CONST_MESSAGE_PUBLIC_ACCOUNT_ARTICLE
                && chatMessage.getRcsMessageType() !=
                Constants.MessageConstants.CONST_MESSAGE_TEXT) {
            PublicMessage publicMessage = chatMessage.getPublicMessage();
            PublicMediaMessage mMsg = (PublicMediaMessage)publicMessage;
            PublicMediaContent paContent = mMsg.getMedia();
            String url = paContent.getOriginalLink();
            String filePath = CommonUtil.getFileCacheLocalPath(chatMessage.getRcsMessageType(), url);
            if (!CommonUtil.isFileExists(filePath)) {
                Toast.makeText(context, R.string.forward_file_not_exist, Toast.LENGTH_SHORT).show();
                return false;
            }
        }
        return true;
    }

    public static void rcsForwardMessage(Activity context, PublicMessageItem message) {
        if (isRcsOnline()) {
            if (PublicMessageItem.isPublicMessageCanForward(message)) {
                showForwardSelectDialog(context);
            } else {
                toast(context, R.string.forward_message_not_support);
            }
        } else {
            toast(context, R.string.not_online_message_too_big);
        }
    }

    public static void rcsCopyMessageLink(Activity context, PublicTopicContent topicContent) {
        if (topicContent != null) {
            ClipboardManager clipboard = (ClipboardManager)
                    context.getSystemService(Context.CLIPBOARD_SERVICE);
            String bodyLink = PublicAccountUtils.replaceBlank(topicContent.getBodyLink());
            ClipData clip = ClipData.newPlainText("bodyLink", bodyLink);
            clipboard.setPrimaryClip(clip);
        }
    }

    public static void showForwardSelectDialog(final Activity context) {
        Resources res = context.getResources();
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setCancelable(true);
        builder.setTitle(R.string.select_contact_conversation);
        builder.setItems(
                new String[] {
                        res.getString(R.string.forward_contact),
                        res.getString(R.string.forward_conversation),
                        res.getString(R.string.forward_contact_group),
                        res.getString(R.string.forward_public_account)
                }, new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case FORWARD_TO_CONTACT:
                                if (Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT ||
                                        Build.VERSION.SDK_INT == Build.VERSION_CODES.M) {
                                    Intent intent = new Intent(
                                            LAUNCH_SELECT_CONTACT_ACTION_KITKAT,
                                            Contacts.CONTENT_URI);
                                    if (Build.VERSION.SDK_INT == Build.VERSION_CODES.M) {
                                        intent.putExtra(FROM_PUBLICACCOUNT, true);
                                    }
                                    context.startActivityForResult(intent,
                                            PAConversationActivity.REQUEST_CODE_RCS_PICK);
                                } else {
                                    Intent intent = new Intent(LAUNCH_SELECT_CONTACT_ACTION);
                                    intent.putExtra(LAUNCH_SELECT_CONTACT_MODE,
                                            PAConversationActivity.MODE_DEFAULT);
                                    context.startActivityForResult(intent,
                                            PAConversationActivity.REQUEST_CODE_RCS_PICK);
                                }
                                break;
                            case FORWARD_TO_CONVERSATION:
                                Intent intent = new Intent(LAUNCH_SELECT_CONVERSATION_ACTION);
                                intent.putExtra(LAUNCH_CONVERSATION_MODE, true);
                                context.startActivityForResult(intent,
                                        PAConversationActivity.REQUEST_SELECT_CONV);
                                break;
                            case FORWARD_TO_GROUP:
                                if (Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT ||
                                        Build.VERSION.SDK_INT == Build.VERSION_CODES.M) {
                                    intent = new Intent(LAUNCH_SELECT_GROUP_ACTION_KITKAT);
                                    context.startActivityForResult(intent,
                                            PAConversationActivity.REQUEST_SELECT_GROUP);
                                } else {
                                    intent = new Intent(LAUNCH_SELECT_CONTACT_ACTION);
                                    intent.putExtra(LAUNCH_SELECT_CONTACT_MODE,
                                            PAConversationActivity.MODE_DEFAULT);
                                    context.startActivityForResult(intent,
                                            PAConversationActivity.REQUEST_SELECT_GROUP);
                                }
                                break;
                            case FORWWARD_TO_PUBLIC_ACCOUNT:
                                intent = new Intent(LAUNCH_SELECT_PUBLICACCOUNT_ACTION);
                                intent.putExtra(LAUNCH_SELECT_PUBLICACCOUNT_MODE, true);
                                context.startActivityForResult(intent,
                                        PAConversationActivity.REQUEST_SELECT_PUBLIC_ACCOUNT);
                                break;
                            default:
                                break;
                        }
                    }
                });
        builder.show();
    }

    public static boolean isRcsOnline() {
        boolean isRcsOnline;
        try {
            isRcsOnline = BasicApi.getInstance().isOnline();
        } catch (ServiceDisconnectedException e) {
            e.printStackTrace();
            isRcsOnline = false;
        } catch (RemoteException e) {
            e.printStackTrace();
            isRcsOnline = false;
        }
        return isRcsOnline;
    }

    public static void toast(Context context, int resId) {
        Toast.makeText(context, resId, Toast.LENGTH_LONG).show();
    }

}
