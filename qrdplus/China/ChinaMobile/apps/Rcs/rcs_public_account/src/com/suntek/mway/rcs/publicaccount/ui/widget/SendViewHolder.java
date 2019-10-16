/*
 * Copyright (c) 2014-2015 pci-suntektech Technologies, Inc.  All Rights Reserved.
 * pci-suntektech Technologies Proprietary and Confidential.
 */

package com.suntek.mway.rcs.publicaccount.ui.widget;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.RemoteException;
import android.text.TextUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.HashMap;

import com.suntek.mway.rcs.client.aidl.constant.Constants;
import com.suntek.mway.rcs.client.aidl.plugin.entity.emoticon.EmoticonBO;
import com.suntek.mway.rcs.client.aidl.plugin.entity.emoticon.EmoticonConstant;
import com.suntek.mway.rcs.client.aidl.plugin.entity.pubacct.PublicMediaMessage;
import com.suntek.mway.rcs.client.aidl.plugin.entity.pubacct.PublicMessage;
import com.suntek.mway.rcs.client.aidl.plugin.entity.pubacct.PublicTextMessage;
import com.suntek.mway.rcs.client.aidl.plugin.entity.pubacct.PublicTopicMessage;
import com.suntek.mway.rcs.client.api.emoticon.EmoticonApi;
import com.suntek.mway.rcs.client.api.exception.ServiceDisconnectedException;
import com.suntek.mway.rcs.client.api.message.MessageApi;
import com.suntek.mway.rcs.publicaccount.R;
import com.suntek.mway.rcs.publicaccount.data.PublicMessageItem;
import com.suntek.mway.rcs.publicaccount.data.RcsGeoLocation;
import com.suntek.mway.rcs.publicaccount.ui.PAMessageUtil;
import com.suntek.mway.rcs.publicaccount.ui.PASendMessageUtil;
import com.suntek.mway.rcs.publicaccount.util.AsynImageLoader;
import com.suntek.mway.rcs.publicaccount.util.CommonUtil;
import com.suntek.rcs.ui.common.RcsEmojiStoreUtil;

public class SendViewHolder {

    private ImageView mHeadView_Send, mImageView_Send, mAudioView_Send, mVideoView_Send,
            mVcradView_Send, mMapView_Send, mResendButton, mSimIndicatorView;

    private TextView mTimeView_Send, mTextView_Send, mSendState,mSendMapBody;

    private RelativeLayout mSendPlay;

    private AsynImageLoader mAsynImageLoader;

    private Context mContext;

    private HashMap<Long, Long> mFileTrasnfer;

    private PublicMessageItem mChatMessage;

    public SendViewHolder(Context context, View convertView, HashMap<Long, Long> fileTrasnfer,
            AsynImageLoader asynImageLoader) {
        this.mContext = context;
        this.mAsynImageLoader = asynImageLoader;
        this.mFileTrasnfer = fileTrasnfer;
        this.mHeadView_Send = (ImageView)convertView.findViewById(R.id.send_head_icon);
        this.mAudioView_Send = (ImageView)convertView.findViewById(R.id.send_audio_content);
        this.mImageView_Send = (ImageView)convertView.findViewById(R.id.send_image_content);
        this.mMapView_Send = (ImageView)convertView.findViewById(R.id.send_map_content);
        this.mTextView_Send = (TextView)convertView.findViewById(R.id.send_text_content);
        this.mTimeView_Send = (TextView)convertView.findViewById(R.id.send_time);
        this.mVcradView_Send = (ImageView)convertView.findViewById(R.id.send_vcrad_content);
        this.mVideoView_Send = (ImageView)convertView.findViewById(R.id.send_video_content);
        this.mResendButton = (ImageView)convertView.findViewById(R.id.resend_button);
        this.mSendState = (TextView)convertView.findViewById(R.id.send_state);
        this.mSendMapBody = (TextView) convertView.findViewById(R.id.send_map_content_body);
        this.mSendPlay = (RelativeLayout)convertView.findViewById(R.id.send_video_play);
        this.mSimIndicatorView = (ImageView)convertView.findViewById(R.id.send_sim_indicator_icon);
    }

    private void updateSimIndicatorView(int subscription) {
        if (PAMessageUtil.isMsimIccCardActive() && subscription >= 0) {
            Drawable mSimIndicatorIcon = PAMessageUtil.getMultiSimIcon(mContext,
                    subscription);
            mSimIndicatorView.setImageDrawable(mSimIndicatorIcon);
            mSimIndicatorView.setVisibility(View.VISIBLE);
        }
    }

    public void setViewDataAndPhoto(PublicMessageItem chatMessage, BitmapDrawable bitmapDrawable) {
        mChatMessage = chatMessage;
        mHeadView_Send.setImageDrawable(bitmapDrawable);
        updateSendState();
        showViewData();
    }

    private void showViewData() {
        updateSimIndicatorView(mChatMessage.getMessagePhoneId());
        mTimeView_Send.setText(PAMessageUtil.getTimeStr(mChatMessage.getSendDate()));
        PublicMessage paMsg = mChatMessage.getPublicMessage();
        int msgType = mChatMessage.getRcsMessageType();
        Bitmap bitmap = null;

        switch (msgType) {
            case Constants.MessageConstants.CONST_MESSAGE_IMAGE:
                String thImagePath = mChatMessage.getThumbMessageFilePath();
                String imagePath = mChatMessage.getMessageFilePath();
                mAudioView_Send.setVisibility(View.GONE);
                mImageView_Send.setVisibility(View.VISIBLE);
                mMapView_Send.setVisibility(View.GONE);
                mTextView_Send.setVisibility(View.GONE);
                mVcradView_Send.setVisibility(View.GONE);
                mVideoView_Send.setVisibility(View.GONE);
                mSendMapBody.setVisibility(View.GONE);
                mSendPlay.setVisibility(View.GONE);
                mImageView_Send.setTag(mChatMessage);
                if (!TextUtils.isEmpty(thImagePath))
                    bitmap = mAsynImageLoader.loadImageAsynByLocalPath(thImagePath);
                else if (!TextUtils.isEmpty(imagePath))
                    bitmap = mAsynImageLoader.loadImageAsynByLocalPath(imagePath);

                if (bitmap != null) {
                    @SuppressWarnings("deprecation")
                    Drawable drawable = new BitmapDrawable(bitmap);
                    mImageView_Send.setBackground(drawable);
                }
                mImageView_Send.setOnClickListener(mSendClickListener);
                break;
            case Constants.MessageConstants.CONST_MESSAGE_AUDIO:
                mAudioView_Send.setVisibility(View.VISIBLE);
                mImageView_Send.setVisibility(View.GONE);
                mMapView_Send.setVisibility(View.GONE);
                mTextView_Send.setVisibility(View.GONE);
                mVcradView_Send.setVisibility(View.GONE);
                mVideoView_Send.setVisibility(View.GONE);
                mSendMapBody.setVisibility(View.GONE);
                mSendPlay.setVisibility(View.GONE);
                mAudioView_Send.setTag(mChatMessage);
                mAudioView_Send.setOnClickListener(mSendClickListener);
                break;

            case Constants.MessageConstants.CONST_MESSAGE_VIDEO:
                mAudioView_Send.setVisibility(View.GONE);
                mImageView_Send.setVisibility(View.GONE);
                mMapView_Send.setVisibility(View.GONE);
                mTextView_Send.setVisibility(View.GONE);
                mVcradView_Send.setVisibility(View.GONE);
                mVideoView_Send.setVisibility(View.VISIBLE);
                mSendMapBody.setVisibility(View.GONE);
                mSendPlay.setVisibility(View.VISIBLE);
                mVideoView_Send.setTag(mChatMessage);
                mVideoView_Send.setOnClickListener(mSendClickListener);

                String thumbFilepath = mChatMessage.getThumbMessageFilePath();
                if (!TextUtils.isEmpty(thumbFilepath)) {
                    bitmap = mAsynImageLoader.loadImageAsynByLocalPath(thumbFilepath);
                }
                if (bitmap != null) {
                    @SuppressWarnings("deprecation")
                    Drawable drawable = new BitmapDrawable(bitmap);
                    mVideoView_Send.setBackground(drawable);
                }

                break;

            case Constants.MessageConstants.CONST_MESSAGE_MAP:
                mAudioView_Send.setVisibility(View.GONE);
                mImageView_Send.setVisibility(View.GONE);
                mMapView_Send.setVisibility(View.VISIBLE);
                mTextView_Send.setVisibility(View.GONE);
                mVcradView_Send.setVisibility(View.GONE);
                mVideoView_Send.setVisibility(View.GONE);
                mSendMapBody.setVisibility(View.VISIBLE);
                mSendPlay.setVisibility(View.GONE);
                mMapView_Send.setTag(mChatMessage);
                mMapView_Send.setOnClickListener(mSendClickListener);
                mSendMapBody.setText(mChatMessage.getMessageBody());
                break;
            case Constants.MessageConstants.CONST_MESSAGE_CONTACT:
                mAudioView_Send.setVisibility(View.GONE);
                mImageView_Send.setVisibility(View.GONE);
                mMapView_Send.setVisibility(View.GONE);
                mTextView_Send.setVisibility(View.GONE);
                mVcradView_Send.setVisibility(View.VISIBLE);
                mVideoView_Send.setVisibility(View.GONE);
                mSendMapBody.setVisibility(View.GONE);
                mSendPlay.setVisibility(View.GONE);
                mVcradView_Send.setTag(mChatMessage);
                mVcradView_Send.setOnClickListener(mSendClickListener);
                break;

            case Constants.MessageConstants.CONST_MESSAGE_PAID_EMOTICON:
                mImageView_Send.setBackground(null);
                mAudioView_Send.setVisibility(View.GONE);
                mImageView_Send.setVisibility(View.VISIBLE);
                mMapView_Send.setVisibility(View.GONE);
                mTextView_Send.setVisibility(View.GONE);
                mVcradView_Send.setVisibility(View.GONE);
                mVideoView_Send.setVisibility(View.GONE);
                mSendMapBody.setVisibility(View.GONE);
                mSendPlay.setVisibility(View.GONE);
                mImageView_Send.setTag(mChatMessage);
                mImageView_Send.setOnClickListener(mSendClickListener);
                RcsEmojiStoreUtil.getInstance().loadImageAsynById(mImageView_Send,
                        mChatMessage.getMessageBody(), RcsEmojiStoreUtil.EMO_STATIC_FILE);
                break;
            default:
            case Constants.MessageConstants.CONST_MESSAGE_TEXT:
                mAudioView_Send.setVisibility(View.GONE);
                mImageView_Send.setVisibility(View.GONE);
                mMapView_Send.setVisibility(View.GONE);
                mTextView_Send.setVisibility(View.VISIBLE);
                mVcradView_Send.setVisibility(View.GONE);
                mVideoView_Send.setVisibility(View.GONE);
                mSendMapBody.setVisibility(View.GONE);
                mSendPlay.setVisibility(View.GONE);
                mTextView_Send.setTag(mChatMessage);
                mTextView_Send.setText(mChatMessage.getMessageBody());
                break;
        }
    }

    private OnClickListener mSendClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            PublicMessageItem chatMessage = (PublicMessageItem)v.getTag();
            String contentType = PAMessageUtil.getMessageContentType(chatMessage);
            String filePath = chatMessage.getMessageFilePath();
            if (!CommonUtil.isFileExists(filePath)) {
                Toast.makeText(mContext, R.string.file_not_exist, Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.fromFile(new File(filePath)), contentType.toLowerCase());
            switch (chatMessage.getRcsMessageType()) {
                case Constants.MessageConstants.CONST_MESSAGE_IMAGE:
                    if (TextUtils.isEmpty(filePath))
                        return;
                    if (!chatMessage.getMessageMimeType()
                            .endsWith(PAMessageUtil.RCS_MSG_IMAGE_TYPE_GIF)) {
                        mContext.startActivity(intent);
                    } else {
                        intent.setAction("com.android.gallery3d.VIEW_GIF");
                        mContext.startActivity(intent);
                    }
                    break;

                case Constants.MessageConstants.CONST_MESSAGE_AUDIO:
                    // intent.setDataAndType(Uri.parse("file://" + filepath),
                    // "audio/*");
                    mContext.startActivity(intent);
                    break;

                case Constants.MessageConstants.CONST_MESSAGE_VIDEO:
                    mContext.startActivity(intent);
                    break;

                case Constants.MessageConstants.CONST_MESSAGE_MAP:
                    String body = chatMessage.getMessageBody();
                    body = body.substring(body.lastIndexOf("/") + 1, body.length());
                    RcsGeoLocation geo = PASendMessageUtil.readPaMapXml(filePath);
                    String geourl = "geo:" + geo.getLat() + "," + geo.getLng() + "?q=" + body;
                    try {
                        Uri uri = Uri.parse(geourl);
                        Intent intent_map = new Intent(Intent.ACTION_VIEW, uri);
                        mContext.startActivity(intent_map);
                    } catch (ActivityNotFoundException e) {
                        Toast.makeText(mContext, R.string.toast_install_map, Toast.LENGTH_SHORT)
                                .show();
                    }
                    break;

                case Constants.MessageConstants.CONST_MESSAGE_CONTACT:
                    intent.putExtra("VIEW_VCARD_FROM_MMS", true);
                    mContext.startActivity(intent);
                    break;
                case Constants.MessageConstants.CONST_MESSAGE_PAID_EMOTICON:
                    try {
                        EmoticonApi emoticonApi = EmoticonApi.getInstance();
                        //TODO emoticon_id  changed to a contsant.
                        EmoticonBO bean = emoticonApi.getEmoticon("emoticon_id");
                        byte[] data = emoticonApi.decrypt2Bytes(bean.getEmoticonId(),
                                EmoticonConstant.EMO_DYNAMIC_FILE);
                        CommonUtil.openPopupWindow(mContext, v, data);
                    } catch (ServiceDisconnectedException e) {
                        e.printStackTrace();
                    } catch (RemoteException e){
                        e.printStackTrace();
                    }
                    break;
                default:
                case Constants.MessageConstants.CONST_MESSAGE_TEXT:
                    break;
            }
        }
    };

    private void updateSendState() {
        int sendState = mChatMessage.getMessageSendState();
        switch (sendState) {
            case Constants.MessageConstants.CONST_STATUS_SEND_FAIL:
                mResendButton.setVisibility(View.VISIBLE);
                mResendButton.setTag(mChatMessage);
                mResendButton.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        PublicMessageItem chatMessage = (PublicMessageItem)v.getTag();
                        if (chatMessage != null) {
                            try {
                                MessageApi.getInstance().resend(chatMessage.getId());
                            } catch (ServiceDisconnectedException e) {
                                e.printStackTrace();
                            } catch (RemoteException e){
                                e.printStackTrace();
                            }
                        }
                    }
                });
                mSendState.setText(R.string.message_send_fail);
                break;
            case Constants.MessageConstants.CONST_STATUS_SENDED:
                mResendButton.setVisibility(View.GONE);
                mSendState.setText(R.string.message_adapter_has_send);
                break;
            case Constants.MessageConstants.CONST_STATUS_SENDING:
                mResendButton.setVisibility(View.GONE);
                long messageId = 0;
                if (mChatMessage != null)
                    messageId = mChatMessage.getMessageId();

                mSendState.setText(R.string.message_adapte_sening);
                if (mChatMessage.getRcsMessageType() !=
                        Constants.MessageConstants.CONST_MESSAGE_TEXT
                        && mFileTrasnfer != null) {
                    Long percent = mFileTrasnfer.get(messageId);
                    if (percent != null) {
                        mSendState
                                .setText(String.format(
                                        mContext.getString(R.string.message_adapte_sening_percent),
                                        percent) + " %");
                    }
                }
                break;
            default:
                mResendButton.setVisibility(View.GONE);
                mSendState.setText(sendState + "");
                break;
        }
    }

    private boolean isMediaType(int type) {
        return (type == Constants.MessageConstants.CONST_MESSAGE_IMAGE
                || type == Constants.MessageConstants.CONST_MESSAGE_AUDIO
                || type == Constants.MessageConstants.CONST_MESSAGE_VIDEO);
    }
}
