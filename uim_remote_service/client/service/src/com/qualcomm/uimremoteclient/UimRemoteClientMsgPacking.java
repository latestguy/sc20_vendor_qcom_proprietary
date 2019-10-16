/******************************************************************************
  @file    UimRemoteClientMsgPacking.java

  ---------------------------------------------------------------------------
  Copyright (c) 2014 Qualcomm Technologies, Inc.  All Rights Reserved.
  Qualcomm Technologies Proprietary and Confidential.
  ---------------------------------------------------------------------------
******************************************************************************/

package com.qualcomm.uimremoteclient;

import android.util.Log;

public class UimRemoteClientMsgPacking {
    private static final String LOG_TAG = "UimRemoteClientMsgPacking";

    public static byte[] packMsg (int msgId, int msgType, Object msg) {
        byte[] bytes = null;
        Log.i(LOG_TAG, "packMsg() - msgId: " + msgId + ", msgType: " + msgType);

        try {
            if (msgType == UimRemoteClient.UIM_REMOTE_MSG_REQUEST) {
                switch (msgId) {
                case UimRemoteClient.UIM_REMOTE_EVENT:
                    bytes = ((UimRemoteClient.UimRemoteEventReq)msg).toByteArray();
                    break;
                case UimRemoteClient.UIM_REMOTE_APDU:
                    bytes = ((UimRemoteClient.UimRemoteApduReq)msg).toByteArray();
                    break;
                default:
                    Log.e(LOG_TAG, "unexpected msgId");
                };
            } else {
                Log.e(LOG_TAG, "unexpected msgType");
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "Exception in msg protobuf encoding");
        }

        return bytes;
    }

    public static Object unpackMsg(int msgId, int msgType, byte[] bytes) {
        Object msg = null;
        Log.i(LOG_TAG, "unpackMsg() - msgId: " + msgId + ", msgType: " + msgType);

        try {
            if (msgType == UimRemoteClient.UIM_REMOTE_MSG_RESPONSE) {
                switch (msgId) {
                case UimRemoteClient.UIM_REMOTE_EVENT:
                    msg = UimRemoteClient.UimRemoteEventResp.parseFrom(bytes);
                    break;
                case UimRemoteClient.UIM_REMOTE_APDU:
                    msg = UimRemoteClient.UimRemoteApduResp.parseFrom(bytes);
                    break;
                default:
                    Log.e(LOG_TAG, "unexpected msgId");
                };
            } else if (msgType == UimRemoteClient.UIM_REMOTE_MSG_INDICATION) {
                switch (msgId) {
                case UimRemoteClient.UIM_REMOTE_APDU:
                    msg = UimRemoteClient.UimRemoteApduInd.parseFrom(bytes);
                    break;
                case UimRemoteClient.UIM_REMOTE_CONNECT:
                    break;
                case UimRemoteClient.UIM_REMOTE_DISCONNECT:
                    break;
                case UimRemoteClient.UIM_REMOTE_POWER_UP:
                    msg = UimRemoteClient.UimRemotePowerUpInd.parseFrom(bytes);
                    break;
                case UimRemoteClient.UIM_REMOTE_POWER_DOWN:
                    msg = UimRemoteClient.UimRemotePowerDownInd.parseFrom(bytes);
                    break;
                case UimRemoteClient.UIM_REMOTE_RESET:
                    break;
                default:
                    Log.e(LOG_TAG, "unexpected msgId");
                };

            } else {
                Log.e(LOG_TAG, "unexpected msgType");
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "Exception in msg protobuf decoding");
        }

        return msg;
    }

    public static byte[] packTag(UimRemoteClient.MessageTag tag) {
        Log.i(LOG_TAG, "packTag()");
        byte[] bytes = null;
        try {
            bytes = tag.toByteArray();
        } catch (Exception e) {
            Log.e(LOG_TAG, "Exception in msg protobuf encoding");
        }
        return bytes;
    }

    public static UimRemoteClient.MessageTag unpackTag(byte[] bytes) {
        Log.i(LOG_TAG, "unpackTag()");
        UimRemoteClient.MessageTag tag = null;
        try {
            tag = UimRemoteClient.MessageTag.parseFrom(bytes);
        } catch (Exception e) {
            Log.e(LOG_TAG, "Exception in tag protobuf decoding");
        }
        return tag;
    }
}
