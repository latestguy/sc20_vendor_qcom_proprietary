/*
 * Copyright (c) 2014-2015 pci-suntektech Technologies, Inc.  All Rights Reserved.
 * pci-suntektech Technologies Proprietary and Confidential.
 */

package com.suntek.mway.rcs.nativeui.ui;

import android.app.Activity;
import android.content.AsyncQueryHandler;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.ColorDrawable;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.os.Message;
import android.provider.Telephony.Sms;
import android.provider.Telephony.MmsSms;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;
import android.text.method.ScrollingMovementMethod;

import com.suntek.mway.rcs.nativeui.R;
import com.suntek.mway.rcs.nativeui.utils.ImageUtils;
import com.suntek.mway.rcs.nativeui.utils.RcsContactUtils;
import com.suntek.mway.rcs.client.aidl.constant.Constants;
import com.suntek.mway.rcs.client.aidl.constant.Actions;
import com.suntek.mway.rcs.client.aidl.constant.Parameter;
import com.suntek.mway.rcs.client.aidl.common.RcsColumns;
import com.suntek.mway.rcs.client.api.exception.ServiceDisconnectedException;
import com.suntek.mway.rcs.client.api.message.MessageApi;
import com.suntek.rcs.ui.common.RcsEmojiGifView;

import java.io.File;
import java.util.HashMap;


public class BurnFlagMessageActivity extends Activity {

    public static final String ALARM_ALERT_ACTION = "com.android.deskclock.ALARM_ALERT";

    public static final String MESSAGE_STATE_CHANGED =
            Actions.MessageAction.ACTION_MESSAGE_STATUS_CHANGED;

    public static String ACTION_SIM_STATE_CHANGED = "android.intent.action.SIM_STATE_CHANGED";

    private static final String EXTRA_STATUS = "status";
    private static final String EXTRA_MESSAGE_ID = "_id";

    private static final int BURN_TIME_REFRESH = 1;
    private static final int AUDIO_TIME_REFRESH = 2;
    private static final int VIDEO_TIME_REFRESH = 3;

    private static final int QUERY_TOKEN = 1;

    public static final String VIDEO_HEAD = "[video]";

    public static final String SPLIT = "-";

    private static final int REFRESH_PERIOD = 1000;

    private static final long DEFAUL_VALUE = -1;

    private ImageView mImage;

    private VideoView mVideo;

    private TextView mAudio;

    private TextView mText;

    private TextView mTime;

    private TextView mVideoLen;

    private TextView mProgressText;

    private ImageView mAudioIcon;

    private RelativeLayout mRootLayout;

    private long mTempType;

    private MediaPlayer mMediaPlayer;

    private String mFileName;

    private int mFileSize;

    private int mType;

    private int mRcsMsgType;

    private String mData;

    private String mRcsMsgName;

    private String mRcsMineType;

    private int mLen = 0;

    private long mLastProgress = 0;

    private TelephonyManager mTelManager;

    private long mMessageId;

    private Cursor mCursor;

    private BroadcastReceiver simStateReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == ACTION_SIM_STATE_CHANGED) {
                TelephonyManager telManager = (TelephonyManager) getSystemService(
                        TELEPHONY_SERVICE);
                int ststus = telManager.getSimState();
                if (ststus != TelephonyManager.SIM_STATE_READY) {
                    Toast.makeText(BurnFlagMessageActivity.this, R.string.burn_all_message,
                            Toast.LENGTH_SHORT).show();
                    try {
                        MessageApi.getInstance().burnAll();
                        finish();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    };

    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context arg0, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Actions.MessageAction.ACTION_MESSAGE_FILE_TRANSFER_PROGRESS)) {
                long messageId = intent.getLongExtra(Parameter.EXTRA_ID, -1);
                long currentSize = intent.getLongExtra(Parameter.EXTRA_TRANSFER_CURRENT_SIZE, -1);
                long totalSize = intent.getLongExtra(Parameter.EXTRA_TRANSFER_TOTAL_SIZE, -1);
                if (messageId > 0 && currentSize == totalSize) {
                    mProgressText.setVisibility(View.GONE);
                    if (mRcsMsgType == Constants.MessageConstants.CONST_MESSAGE_IMAGE) {
                        loadImage();
                    } else if (mRcsMsgType == Constants.MessageConstants.CONST_MESSAGE_VIDEO) {
                        loadVideo();
                    }
                }
                if (messageId > 0 && totalSize != 0) {
                    long temp = currentSize * 100 / totalSize;
                    if (messageId > 0 && currentSize < totalSize) {
                        mLastProgress = temp;
                        mProgressText.setText(String.format(getString(R.string.image_downloading),
                                mLastProgress));
                    }
                }
            } else if (action.equals(MESSAGE_STATE_CHANGED)) {
                int status = intent.getIntExtra(EXTRA_STATUS, -11);
                if (Constants.MessageConstants.CONST_STATUS_BURNED == status
                        && mType == Constants.MessageConstants.CONST_DIRECTION_SEND) {
                    Toast.makeText(getBaseContext(), R.string.message_is_burnd, Toast.LENGTH_SHORT)
                            .show();
                    finish();
                }
            } else if (ALARM_ALERT_ACTION.equals(intent.getAction())) {
                finish();
            }
        }
    };

    private Runnable refresh = new Runnable() {

        @Override
        public void run() {
            mTempType = mTempType - REFRESH_PERIOD;
            mTime.setText(mTempType / REFRESH_PERIOD + "");
            if (mTempType != 0) {
                handler.postDelayed(this, REFRESH_PERIOD);
            } else {
                Toast.makeText(getBaseContext(), R.string.message_is_burnd,
                        Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    };

    private Runnable refreshAudio = new Runnable() {

        @Override
        public void run() {
            mLen = mLen - REFRESH_PERIOD;
            mAudio.setText(getString(R.string.audio_length) + mLen / REFRESH_PERIOD + "\'");

            if (mLen != 0) {
                handler.postDelayed(this, REFRESH_PERIOD);
            } else {
                Toast.makeText(getBaseContext(), R.string.message_is_play_over,
                        Toast.LENGTH_SHORT).show();
                mAudio.setVisibility(View.GONE);
                finish();
            }
        }
    };

    private Runnable refreshvideo = new Runnable() {

        @Override
        public void run() {
            mLen = mLen - REFRESH_PERIOD;
            mVideoLen.setText(getString(R.string.video_length) + mLen / REFRESH_PERIOD
                    + "\'");
            if (mLen != 0) {
                handler.postDelayed(this, REFRESH_PERIOD);
            } else {
                Toast.makeText(getBaseContext(), R.string.message_is_play_over,
                        Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    };

    private Handler handler = new Handler() {

        @Override
        public void handleMessage(android.os.Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case BURN_TIME_REFRESH:
                    mTime.setText(mTempType / REFRESH_PERIOD + "");
                    handler.postDelayed(refresh, REFRESH_PERIOD);
                    break;
                case AUDIO_TIME_REFRESH:
                    mLen = mLen * REFRESH_PERIOD;
                    mAudio.setText(getString(R.string.audio_length) + mLen / REFRESH_PERIOD
                            + "\"");
                    handler.postDelayed(refreshAudio, REFRESH_PERIOD);
                    break;
                case VIDEO_TIME_REFRESH:
                    mLen = mLen * REFRESH_PERIOD;
                    mVideoLen.setText(getString(R.string.video_length) + mLen / REFRESH_PERIOD
                            + "\"");
                    handler.postDelayed(refreshvideo, REFRESH_PERIOD);
                    break;
                default:
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window win = getWindow();
        win.addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.burn_message_activity);
        getWindow().setLayout(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);

        WindowManager mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        mTelManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        mTelManager.listen(new phoneStateListener(),
                PhoneStateListener.LISTEN_CALL_STATE);

        IntentFilter filter = new IntentFilter();
        filter.addAction(Actions.MessageAction.ACTION_MESSAGE_FILE_TRANSFER_PROGRESS);
        filter.addAction(ALARM_ALERT_ACTION);
        filter.addAction(MESSAGE_STATE_CHANGED);
        registerReceiver(receiver, filter);

        IntentFilter simOutFilter = new IntentFilter();
        simOutFilter.addAction(ACTION_SIM_STATE_CHANGED);
        registerReceiver(simStateReceiver,simOutFilter);

        mMessageId = getIntent().getLongExtra(EXTRA_MESSAGE_ID, DEFAUL_VALUE);
        if (mMessageId == DEFAUL_VALUE) {
            finish();
            return;
        }
        findView();
        new backGroundHandler(getContentResolver()).startQuery(QUERY_TOKEN, null, Sms.CONTENT_URI,
                null, "_id = ?", new String[] {
                    String.valueOf(mMessageId)
                }, null);
    }

    public void initMsg(Cursor cursor) {
        mCursor = cursor;
        if (mCursor != null && mCursor.moveToFirst()) {
            mFileName = mCursor.getString(mCursor.getColumnIndex(
                    RcsColumns.SmsRcsColumns.RCS_FILENAME));
            mFileSize = mCursor.getInt(mCursor.getColumnIndex(
                    RcsColumns.SmsRcsColumns.RCS_FILE_SIZE));
            mType = mCursor.getInt(mCursor.getColumnIndex(
                        Sms.TYPE));
            mRcsMsgType = mCursor.getInt(mCursor.getColumnIndex(
                    RcsColumns.SmsRcsColumns.RCS_MSG_TYPE));
            mRcsMineType = mCursor.getString(mCursor.getColumnIndex(
                    RcsColumns.SmsRcsColumns.RCS_MIME_TYPE));
            mRcsMsgName = mCursor.getString(mCursor.getColumnIndex(
                    RcsColumns.SmsRcsColumns.RCS_FILENAME));
            mData = mCursor.getString(mCursor.getColumnIndex(Sms.BODY));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try{
            if (receiver != null) {
                unregisterReceiver(receiver);
            }
            if (simStateReceiver != null) {
                unregisterReceiver(simStateReceiver);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            mMediaPlayer.release();
        }
        if (mType == Constants.MessageConstants.CONST_DIRECTION_RECEIVE) {
            if (mRcsMsgType == Constants.MessageConstants.CONST_MESSAGE_IMAGE
                    || mRcsMsgType == Constants.MessageConstants.CONST_MESSAGE_AUDIO
                    || mRcsMsgType == Constants.MessageConstants.CONST_MESSAGE_VIDEO){
                if (RcsContactUtils.isFileDownload(mFileName, mFileSize)) {
                    burnMessage(mMessageId);
                }
            } else {
                burnMessage(mMessageId);
            }
            finish();
        }
    }

    private void loadVideo() {
        if (RcsContactUtils.isFileDownload(mFileName, mFileSize)) {
            mVideo.setVisibility(View.VISIBLE);
            mVideo.setOnCompletionListener(new OnCompletionListener() {

                @Override
                public void onCompletion(MediaPlayer arg0) {
                    finish();
                }
            });
            mVideoLen.setVisibility(View.VISIBLE);
            mVideoLen.setText(getString(R.string.video_length) + mLen / REFRESH_PERIOD
                    + "\"");
            mVideo.setVideoURI(Uri.parse(mFileName));
            mVideo.start();

            handler.sendEmptyMessage(VIDEO_TIME_REFRESH);
        } else {
            mVideo.setVisibility(View.GONE);
            mVideoLen.setVisibility(View.GONE);
            acceptFile();
        }
    }

    private void loadImage() {

        if (RcsContactUtils.isFileDownload(mFileName, mFileSize)) {

            if (imageIsGif()) {
                File file = new File(mFileName);
                byte[] data = RcsContactUtils.getBytesFromFile(file);
                LinearLayout.LayoutParams mGifParam = new LinearLayout.LayoutParams(
                        LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
                ColorDrawable transparent = new ColorDrawable(Color.TRANSPARENT);
                RcsEmojiGifView emojiGifView = new RcsEmojiGifView(BurnFlagMessageActivity.this);
                emojiGifView.setLayoutParams(mGifParam);
                emojiGifView.setBackground(transparent);
                emojiGifView.setMonieByteData(data);
                mRootLayout.setVisibility(View.VISIBLE);
                mRootLayout.addView(emojiGifView);
            } else {
                Bitmap imageBm = ImageUtils.getBitmap(mFileName);
                mImage.setImageBitmap(imageBm);
                if (mType == Constants.MessageConstants.CONST_DIRECTION_RECEIVE) {
                   burnMessage(mMessageId);
                }
                mProgressText.setVisibility(View.GONE);
            }

        } else {
            acceptFile();
            mProgressText.setVisibility(View.VISIBLE);
        }
    }

    private void acceptFile() {
        try {
            MessageApi.getInstance().download(mMessageId);
        } catch (RemoteException e) {
            e.printStackTrace();
            Toast.makeText(this, R.string.image_msg_load_fail_tip,
                    Toast.LENGTH_SHORT).show();
        } catch (ServiceDisconnectedException e) {
            e.printStackTrace();
        }
    }

    private void initView() {
        switch (mRcsMsgType) {
            case Constants.MessageConstants.CONST_MESSAGE_TEXT:
                mText.setVisibility(View.VISIBLE);
                mText.setText(mData);
                if (mType == Constants.MessageConstants.CONST_DIRECTION_RECEIVE) {
                    burnMessage(mMessageId);
                }
                break;
            case Constants.MessageConstants.CONST_MESSAGE_IMAGE:
                mImage.setVisibility(View.VISIBLE);
                loadImage();
                break;
            case Constants.MessageConstants.CONST_MESSAGE_AUDIO:
                mAudio.setVisibility(View.VISIBLE);
                mAudioIcon.setVisibility(View.VISIBLE);
                mAudioIcon.setBackgroundResource(R.anim.burn_message_audio_icon);
                final AnimationDrawable animaition =
                        (AnimationDrawable) mAudioIcon.getBackground();
                animaition.setOneShot(false);
                mMediaPlayer = new MediaPlayer();
                try {
                    if (mMediaPlayer != null && mMediaPlayer.isPlaying()) {
                        mMediaPlayer.stop();
                        mMediaPlayer.release();
                    }
                    mMediaPlayer.setDataSource(mFileName);
                    mMediaPlayer.prepare();
                    mMediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                        @Override
                        public void onPrepared(MediaPlayer mp) {
                        mLen = mMediaPlayer.getDuration() / REFRESH_PERIOD;
                        }
                    });
                    mMediaPlayer.setOnCompletionListener(new OnCompletionListener() {

                        @Override
                        public void onCompletion(MediaPlayer arg0) {
                            animaition.stop();
                            finish();
                        }
                    });
                } catch (Exception e) {

                    e.printStackTrace();

                    if (mMediaPlayer != null && mMediaPlayer.isPlaying()) {
                        mMediaPlayer.stop();
                    }
                    Toast.makeText(this, R.string.open_file_fail,
                            Toast.LENGTH_SHORT).show();
                    finish();
                }
                mMediaPlayer.start();
                animaition.start();
                handler.sendEmptyMessage(AUDIO_TIME_REFRESH);
                break;

            case Constants.MessageConstants.CONST_MESSAGE_VIDEO:
                mVideo.setVisibility(View.VISIBLE);
                mVideoLen.setVisibility(View.VISIBLE);
                mVideo.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                    @Override
                    public void onPrepared(MediaPlayer mp) {
                        mLen = mVideo.getDuration();
                    }
                });
                loadVideo();
                break;
            default:
                break;
        }

    }

    private void findView() {
        mProgressText = (TextView) findViewById(R.id.progress_text);
        mImage = (ImageView) findViewById(R.id.image);
        mVideo = (VideoView) findViewById(R.id.video);
        mAudio = (TextView) findViewById(R.id.audio);
        mText = (TextView) findViewById(R.id.text);
        mText.setMovementMethod(ScrollingMovementMethod.getInstance());
        mTime = (TextView) findViewById(R.id.burn_time);
        mVideoLen = (TextView) findViewById(R.id.video_len);
        mAudioIcon = (ImageView) findViewById(R.id.audio_icon);
        mRootLayout = (RelativeLayout) findViewById(R.id.gif_root_view);
    }

    public static int getVideoLength(String message) {
        if (message.startsWith(VIDEO_HEAD)) {
            return Integer.parseInt(message.substring(VIDEO_HEAD.length())
                    .split(SPLIT)[0]);
        }
        return 0;
    }

    private boolean imageIsGif(){
        if (mRcsMineType != null &&
                mRcsMineType.endsWith("image/gif")
                || mRcsMsgName != null && mRcsMsgName.endsWith("gif")) {
            return true;
        } else {
            return false;
        }
    }

    private void burnMessage(long id) {
        try {
            if (id != DEFAUL_VALUE) {
                MessageApi.getInstance().burn(id);
            }
        } catch (ServiceDisconnectedException e) {
            e.printStackTrace();
        } catch (RemoteException e){
            e.printStackTrace();
        }
    }

    class phoneStateListener extends PhoneStateListener {
        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            switch(state) {
            case TelephonyManager.CALL_STATE_RINGING:
                finish();
                break;
            }
        }
    }

    public class backGroundHandler extends AsyncQueryHandler{

        public backGroundHandler(ContentResolver cr) {
            super(cr);
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            super.onQueryComplete(token, cookie, cursor);
            switch (token) {
                case QUERY_TOKEN:
                    initMsg(cursor);
                    initView();
                    break;

                default:
                    break;
            }
        }
    }
}
