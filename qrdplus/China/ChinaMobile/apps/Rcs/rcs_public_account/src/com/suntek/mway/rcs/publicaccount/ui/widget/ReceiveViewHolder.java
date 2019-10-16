/*
 * Copyright (c) 2014-2015 pci-suntektech Technologies, Inc.  All Rights Reserved.
 * pci-suntektech Technologies Proprietary and Confidential.
 */

package com.suntek.mway.rcs.publicaccount.ui.widget;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler.Callback;
import android.os.Message;
import android.os.RemoteException;
import android.text.Layout;
import android.text.Spannable;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.suntek.mway.rcs.client.aidl.constant.Constants;
import com.suntek.mway.rcs.client.aidl.plugin.entity.emoticon.EmoticonBO;
import com.suntek.mway.rcs.client.aidl.plugin.entity.emoticon.EmoticonConstant;
import com.suntek.mway.rcs.client.aidl.plugin.entity.pubacct.PublicMediaMessage;
import com.suntek.mway.rcs.client.aidl.plugin.entity.pubacct.PublicMessage;
import com.suntek.mway.rcs.client.aidl.plugin.entity.pubacct.PublicTextMessage;
import com.suntek.mway.rcs.client.aidl.plugin.entity.pubacct.PublicTopicMessage;
import com.suntek.mway.rcs.client.aidl.plugin.entity.pubacct.PublicMediaMessage;
import com.suntek.mway.rcs.client.aidl.plugin.entity.pubacct.PublicMediaMessage.PublicMediaContent;
import com.suntek.mway.rcs.client.api.emoticon.EmoticonApi;
import com.suntek.mway.rcs.client.api.exception.ServiceDisconnectedException;
import com.suntek.mway.rcs.client.api.message.MessageApi;
import com.suntek.mway.rcs.publicaccount.R;
import com.suntek.mway.rcs.publicaccount.data.PublicMessageItem;
import com.suntek.mway.rcs.publicaccount.data.RcsPropertyNode;
import com.suntek.mway.rcs.publicaccount.data.RcsGeoLocation;
import com.suntek.mway.rcs.publicaccount.data.RcsVcardNode;
import com.suntek.mway.rcs.publicaccount.data.RcsVcardNodeBuilder;
import com.suntek.mway.rcs.publicaccount.http.service.CommonHttpRequest;
import com.suntek.mway.rcs.publicaccount.http.service.PAHttpService.Response;
import com.suntek.mway.rcs.publicaccount.ui.PAMessageUtil;
import com.suntek.mway.rcs.publicaccount.ui.PASendMessageUtil;
import com.suntek.mway.rcs.publicaccount.util.AsynImageLoader;
import com.suntek.mway.rcs.publicaccount.util.MD5Util;
import com.suntek.mway.rcs.publicaccount.util.AsynImageLoader.ImageCallback;
import com.suntek.mway.rcs.publicaccount.util.AsynImageLoader.LoaderImageTask;
import com.suntek.mway.rcs.publicaccount.util.CommonUtil;
import com.suntek.rcs.ui.common.RcsEmojiStoreUtil;

@SuppressLint("DefaultLocale")
public class ReceiveViewHolder {

    private ImageView mHeadView_Receive, mImageView_Receive, mAudioView_Receive,
            mVideoView_Receive, mMapView_Receive, mVcardView_Receive, mRedownload,
                    mSimIndicatorView;

    private TextView mTimeView_Receive, mTextView_Receive, mReceiveState, mDownloadState,mReceiveMapBody;

    private RelativeLayout mReceivePlay;

    private AsynImageLoader mAsynImageLoader;

    private Context mContext;

    private HashMap<Long, Long> mFileTrasnfer;

    private PublicMessageItem mChatMessage;

    public ReceiveViewHolder(Context context, View convertView, HashMap<Long, Long> fileTrasnfer,
            AsynImageLoader asynImageLoader) {
        this.mContext = context;
        this.mAsynImageLoader = asynImageLoader;
        this.mFileTrasnfer = fileTrasnfer;
        this.mHeadView_Receive = (ImageView)convertView.findViewById(R.id.receive_head_icon);
        this.mAudioView_Receive = (ImageView)convertView.findViewById(R.id.receive_audio_content);
        this.mImageView_Receive = (ImageView)convertView.findViewById(R.id.receive_image_content);
        this.mTextView_Receive = (TextView)convertView.findViewById(R.id.receive_text_content);
        this.mTimeView_Receive = (TextView)convertView.findViewById(R.id.receive_time);
        this.mVideoView_Receive = (ImageView)convertView.findViewById(R.id.receive_video_content);
        this.mMapView_Receive = (ImageView)convertView.findViewById(R.id.receive_map_content);
        this.mVcardView_Receive = (ImageView)convertView.findViewById(R.id.receive_vcrad_content);
        this.mReceiveState = (TextView)convertView.findViewById(R.id.receive_state);
        this.mRedownload = (ImageView)convertView.findViewById(R.id.redownload_button);
        this.mDownloadState = (TextView)convertView.findViewById(R.id.download_state);
        this.mReceiveMapBody = (TextView)convertView.findViewById(R.id.receive_map_content_body);
        this.mReceivePlay = (RelativeLayout)convertView.findViewById(R.id.receive_video_play);
        this.mSimIndicatorView = (ImageView)convertView.findViewById(R.id.rec_sim_indicator_icon);
    }

    private void setTextViewTouch() {
        mTextView_Receive.setOnTouchListener(new OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                boolean ret = false;
                CharSequence text = ((TextView)v).getText();
                Spannable stext = Spannable.Factory.getInstance().newSpannable(text);
                TextView widget = (TextView)v;
                int action = event.getAction();
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_DOWN) {
                    int x = (int)event.getX();
                    int y = (int)event.getY();
                    x -= widget.getTotalPaddingLeft();
                    y -= widget.getTotalPaddingTop();
                    x += widget.getScrollX();
                    y += widget.getScrollY();
                    Layout layout = widget.getLayout();
                    int line = layout.getLineForVertical(y);
                    int off = layout.getOffsetForHorizontal(line, x);
                    ClickableSpan[] link = stext.getSpans(off, off, ClickableSpan.class);
                    if (link.length != 0) {
                        if (action == MotionEvent.ACTION_UP) {
                            link[0].onClick(widget);
                        }
                        ret = true;
                    }
                }
                return ret;
            }
        });
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
        mHeadView_Receive.setImageDrawable(bitmapDrawable);
        mTimeView_Receive.setText(PAMessageUtil.getTimeStr(mChatMessage.getSendDate()));
        updateReceiveState();
        showViewData();
    }

    private void showViewData() {
        updateSimIndicatorView(mChatMessage.getMessagePhoneId());
        int msgType = mChatMessage.getRcsMessageType();
        String data = mChatMessage.getMessageBody();
        PublicMessage paMsg = null;
        try {
            paMsg = MessageApi.getInstance().parsePublicMessage(msgType, data);
        } catch (Exception e) {
            Log.w("RCS_UI", e);
        }

        switch (msgType) {
            case Constants.MessageConstants.CONST_MESSAGE_AUDIO: {
                mAudioView_Receive.setVisibility(View.VISIBLE);
                mImageView_Receive.setVisibility(View.GONE);
                mTextView_Receive.setVisibility(View.GONE);
                mVideoView_Receive.setVisibility(View.GONE);
                mMapView_Receive.setVisibility(View.GONE);
                mVcardView_Receive.setVisibility(View.GONE);
                mReceiveMapBody.setVisibility(View.GONE);
                mReceivePlay.setVisibility(View.GONE);
                mAudioView_Receive.setTag(mChatMessage);
                mAudioView_Receive.setOnClickListener(mReceiveClickListener);
                break;
            }
            case Constants.MessageConstants.CONST_MESSAGE_VIDEO: {
                PublicMediaMessage mMsg = (PublicMediaMessage)paMsg;
                mAudioView_Receive.setVisibility(View.GONE);
                mImageView_Receive.setVisibility(View.GONE);
                mTextView_Receive.setVisibility(View.GONE);
                mVideoView_Receive.setVisibility(View.VISIBLE);
                mMapView_Receive.setVisibility(View.GONE);
                mVcardView_Receive.setVisibility(View.GONE);
                mReceiveMapBody.setVisibility(View.GONE);
                mReceivePlay.setVisibility(View.VISIBLE);
                String videoThumb = mMsg.getMedia().getThumbLink();
                if (!TextUtils.isEmpty(videoThumb)) {
                    LoaderImageTask loaderImageTask = new LoaderImageTask(videoThumb, false, false,
                            true, true);
                    mAsynImageLoader.loadImageAsynByUrl(loaderImageTask, new ImageCallback() {
                        @Override
                        public void loadImageCallback(Bitmap bitmap) {
                            if (bitmap != null) {
                                @SuppressWarnings("deprecation")
                                Drawable drawable = new BitmapDrawable(bitmap);
                                mVideoView_Receive.setBackground(drawable);
                            }
                        }
                    });
                }
                mVideoView_Receive.setTag(mChatMessage);
                mVideoView_Receive.setOnClickListener(mReceiveClickListener);
                break;
            }
            case Constants.MessageConstants.CONST_MESSAGE_IMAGE: {
                PublicMediaMessage mMsg = (PublicMediaMessage)paMsg;
                mAudioView_Receive.setVisibility(View.GONE);
                mImageView_Receive.setVisibility(View.VISIBLE);
                mTextView_Receive.setVisibility(View.GONE);
                mVideoView_Receive.setVisibility(View.GONE);
                mMapView_Receive.setVisibility(View.GONE);
                mVcardView_Receive.setVisibility(View.GONE);
                mReceiveMapBody.setVisibility(View.GONE);
                mReceivePlay.setVisibility(View.GONE);
                PublicMediaContent paContent = mMsg.getMedia();
                if (paContent != null) {
                    String imagePath = paContent.getThumbLink();
                    if (!TextUtils.isEmpty(imagePath)) {
                        LoaderImageTask loaderImageTask = new LoaderImageTask(imagePath, false,
                                false, true, true);
                        mAsynImageLoader.loadImageAsynByUrl(loaderImageTask, new ImageCallback() {
                            @Override
                            public void loadImageCallback(Bitmap bitmap) {
                                if (bitmap != null) {
                                    @SuppressWarnings("deprecation")
                                    Drawable drawable = new BitmapDrawable(bitmap);
                                    mImageView_Receive.setBackground(drawable);
                                }
                            }
                        });
                    }
                }
                mImageView_Receive.setTag(mChatMessage);
                mImageView_Receive.setOnClickListener(mReceiveClickListener);
                break;
            }
            case Constants.MessageConstants.CONST_MESSAGE_MAP:
                PublicTextMessage mapMsg = (PublicTextMessage)paMsg;
                mAudioView_Receive.setVisibility(View.GONE);
                mImageView_Receive.setVisibility(View.GONE);
                mTextView_Receive.setVisibility(View.GONE);
                mVideoView_Receive.setVisibility(View.GONE);
                mMapView_Receive.setVisibility(View.VISIBLE);
                mVcardView_Receive.setVisibility(View.GONE);
                mReceiveMapBody.setVisibility(View.VISIBLE);
                mReceivePlay.setVisibility(View.GONE);
                mMapView_Receive.setTag(mapMsg);

                String filePath = mapMsg.getContent();
                RcsGeoLocation geo = PASendMessageUtil.readPaMapXml(filePath);
                //TODO  what is this body
                String body = mChatMessage.getMessageBody();
                body = body.substring(body.lastIndexOf("/") + 1, body.length());
                String geourl = "geo:" + geo.getLat() + "," + geo.getLng() + "?q=" + body;
                Log.d("huangyf",body+"&"+geo.getLat()+"&"+geo.getLng() + "&" + geo.getLabel());
                mReceiveMapBody.setText(geo.getLabel());
                mMapView_Receive.setOnClickListener(new OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        PublicTextMessage mapMessage = (PublicTextMessage) v.getTag();
                        String filePath = mapMessage.getContent();
                        RcsGeoLocation geo = PASendMessageUtil.readPaMapXml(filePath);
                        String body = mChatMessage.getMessageBody();
                        body = body.substring(body.lastIndexOf("/") + 1, body.length());
                        String geourl = "geo:" + geo.getLat() + "," + geo.getLng() + "?q=" + body;
                        //setMapFileArgs(filePath, mChatMessage);
                        try {
                            Uri uri = Uri.parse(geourl);
                            Intent intent_map = new Intent(Intent.ACTION_VIEW, uri);
                            mContext.startActivity(intent_map);
                        } catch (Exception e) {
                            Toast.makeText(mContext, R.string.toast_install_map, Toast.LENGTH_SHORT)
                                    .show();
                        }
                    }
                });
                break;
            case Constants.MessageConstants.CONST_MESSAGE_PAID_EMOTICON:
                mAudioView_Receive.setVisibility(View.GONE);
                mImageView_Receive.setVisibility(View.VISIBLE);
                mTextView_Receive.setVisibility(View.GONE);
                mVideoView_Receive.setVisibility(View.GONE);
                mMapView_Receive.setVisibility(View.GONE);
                mVcardView_Receive.setVisibility(View.GONE);
                mReceiveMapBody.setVisibility(View.GONE);
                mReceivePlay.setVisibility(View.GONE);
                mImageView_Receive.setTag(mChatMessage);
                mImageView_Receive.setOnClickListener(mReceiveClickListener);
                //TODO we don't know the data insert the location in db. so place it "getmMessageBody"
                RcsEmojiStoreUtil.getInstance().loadImageAsynById(mImageView_Receive,
                        mChatMessage.getMessageBody(), RcsEmojiStoreUtil.EMO_STATIC_FILE);
                break;
            case Constants.MessageConstants.CONST_MESSAGE_CONTACT: {
                mAudioView_Receive.setVisibility(View.GONE);
                mImageView_Receive.setVisibility(View.GONE);
                mTextView_Receive.setVisibility(View.GONE);
                mVideoView_Receive.setVisibility(View.GONE);
                mMapView_Receive.setVisibility(View.GONE);
                mVcardView_Receive.setVisibility(View.VISIBLE);
                mReceiveMapBody.setVisibility(View.GONE);
                mReceivePlay.setVisibility(View.GONE);
                mVcardView_Receive.setTag(mChatMessage);
                mVcardView_Receive.setOnClickListener(new OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        PublicMessageItem chatMessage = (PublicMessageItem) v.getTag();
                        PublicMessage publicMessage = chatMessage.getPublicMessage();
                        PublicTextMessage tMsg = (PublicTextMessage)publicMessage;
                        String vcardFilePath = tMsg.getContent();
                        //setMapFileArgs(vcardFilePath, chatMessage);
                        ArrayList<RcsPropertyNode> propList = PAMessageUtil.openRcsVcardDetail(mContext, vcardFilePath);
                        PAMessageUtil.showDetailVcard(mContext, propList);
                    }
                });
                break;
            }
            default: {
                PublicTextMessage tMsg = (PublicTextMessage)paMsg;
                String text = "";
                if (tMsg != null)
                    text = tMsg.getContent();
                mAudioView_Receive.setVisibility(View.GONE);
                mImageView_Receive.setVisibility(View.GONE);
                mTextView_Receive.setVisibility(View.VISIBLE);
                mVideoView_Receive.setVisibility(View.GONE);
                mMapView_Receive.setVisibility(View.GONE);
                mVcardView_Receive.setVisibility(View.GONE);
                mReceiveMapBody.setVisibility(View.GONE);
                mReceivePlay.setVisibility(View.GONE);
                mTextView_Receive.setText(text);
                break;
            }
        }
    }

    private void updateReceiveState() {
        int sendState = mChatMessage.getMessageSendState();
        switch (sendState) {
            //TODO this state has not define "SuntekMessageData.MSG_STATE_DOWNLOAD_FAIL"
            case 5:
                mReceiveState.setVisibility(View.VISIBLE);
                mReceiveState.setText(R.string.message_download_fail);
                mRedownload.setVisibility(View.VISIBLE);
                mRedownload.setTag(mChatMessage);
                mRedownload.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        PublicMessageItem chatMessage = (PublicMessageItem) v.getTag();
                        if (mFileTrasnfer.containsKey(chatMessage.getMessageId())) {
                            mFileTrasnfer.remove(chatMessage.getMessageId());
                        }
                        downloadFile(chatMessage);
                    }
                });
                break;
            default:
                mReceiveState.setVisibility(View.GONE);
                mRedownload.setVisibility(View.GONE);
                break;
        }
    }

    private OnClickListener mReceiveClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            PublicMessageItem chatMessage = (PublicMessageItem) v.getTag();
            String contentType = PAMessageUtil.getMessageContentType(chatMessage);
            PublicMessage publicMessage = chatMessage.getPublicMessage();
            PublicMediaMessage mMsg = (PublicMediaMessage)publicMessage;
            PublicMediaContent paContent = mMsg.getMedia();
            String url = paContent.getOriginalLink();

            if (chatMessage.getRcsMessageType() ==
                    Constants.MessageConstants.CONST_MESSAGE_TEXT) {
                return;
            }
            String filePath = CommonUtil.getFileCacheLocalPath(chatMessage.getRcsMessageType(), url);
            boolean isDownloaded = CommonUtil.isFileExists(filePath);
            if (!isDownloaded) {
                downloadFile(chatMessage);
                return;
            }
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
                    if (!chatMessage.getMessageBody().endsWith("gif")
                            && !chatMessage.getMessageBody().endsWith("GIF")) {
                        mContext.startActivity(intent);
                    } else {
                        intent.setAction("com.android.gallery3d.VIEW_GIF");
                        mContext.startActivity(intent);
                    }
                    break;

                case Constants.MessageConstants.CONST_MESSAGE_AUDIO:
                case Constants.MessageConstants.CONST_MESSAGE_VIDEO:
                    mContext.startActivity(intent);
                    break;
                case Constants.MessageConstants.CONST_MESSAGE_PAID_EMOTICON:
                    try {
                        EmoticonApi emoticonApi = EmoticonApi.getInstance();
                        //TODO this will change to emoticon id
                        EmoticonBO bean = emoticonApi.getEmoticon("emtion id");
                        byte[] data = emoticonApi.decrypt2Bytes(bean.getEmoticonId(),
                                EmoticonConstant.EMO_DYNAMIC_FILE);
                        CommonUtil.openPopupWindow(mContext, v, data);
                    } catch (ServiceDisconnectedException e) {
                        e.printStackTrace();
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                    break;
                default:
                case Constants.MessageConstants.CONST_MESSAGE_TEXT:
                    break;
            }
        }
    };

    private void downloadFile(final PublicMessageItem chatMessage) {
        if (mFileTrasnfer.containsKey(chatMessage.getMessageId()))
            return;
        mFileTrasnfer.put(chatMessage.getMessageId(), Long.parseLong("0"));
        mDownloadState.setVisibility(View.VISIBLE);
        mReceiveState.setVisibility(View.GONE);
        mRedownload.setVisibility(View.GONE);
        PublicMessage publicMessage = chatMessage.getPublicMessage();
        PublicMediaMessage mMsg = (PublicMediaMessage)publicMessage;
        final PublicMediaContent paContent = mMsg.getMedia();
        CommonHttpRequest.getInstance().downloadFile(paContent.getOriginalLink(),
                chatMessage.getRcsMessageType(), new Callback() {
                    @Override
                    public boolean handleMessage(Message msg) {
                        if (msg != null) {
                            Response response = (Response)msg.obj;
                            if (response.state == Response.SECCESS) {
                               // setChatMessageFileArgs(chatMessage, paContent.getOriginalLink());
                                mFileTrasnfer.remove(chatMessage.getMessageId());
                                mDownloadState.setVisibility(View.GONE);
                                mReceiveState.setVisibility(View.GONE);
                                mRedownload.setVisibility(View.GONE);
                            } else if (response.state == Response.PROGRESS) {
                                mFileTrasnfer.put(chatMessage.getMessageId(),
                                        Long.parseLong(response.feedback));
                                mDownloadState.setText(mContext.getString(R.string.downloading)
                                        + response.feedback + "%");
                            } else {
                                mFileTrasnfer.remove(chatMessage.getMessageId());
                                mDownloadState.setVisibility(View.GONE);
                                mReceiveState.setVisibility(View.VISIBLE);
                                mReceiveState.setText(R.string.message_download_fail);
                                mRedownload.setVisibility(View.VISIBLE);
                                mRedownload.setTag(chatMessage);
                                mRedownload.setOnClickListener(new OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        PublicMessageItem message = (PublicMessageItem) v.getTag();
                                        if (mFileTrasnfer.containsKey(message.getMessageId())) {
                                            mFileTrasnfer.remove(message.getMessageId());
                                        }
                                        downloadFile(message);
                                    }
                                });
                            }
                        }
                        return false;
                    }
                });
    }


    private boolean isMediaType(int type) {
        return (type == Constants.MessageConstants.CONST_MESSAGE_IMAGE)
                || (type == Constants.MessageConstants.CONST_MESSAGE_AUDIO)
                || (type == Constants.MessageConstants.CONST_MESSAGE_VIDEO);
    }
}
