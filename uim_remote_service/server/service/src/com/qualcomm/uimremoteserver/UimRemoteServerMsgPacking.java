/******************************************************************************
  @file    UimRemoteServerMsgPacking.java

  ---------------------------------------------------------------------------
  Copyright (c) 2014 Qualcomm Technologies, Inc.  All Rights Reserved.
  Qualcomm Technologies Proprietary and Confidential.
  ---------------------------------------------------------------------------
******************************************************************************/

package com.qualcomm.uimremoteserver;

import android.util.Log;

public class UimRemoteServerMsgPacking {
    private static final String LOG_TAG = "UimRemoteServerMsgPacking";

    public static byte[] packMsg (int msgId, int msgType, Object msg) {
        byte[] bytes = null;
        Log.i(LOG_TAG, "packMsg() - msgId: " + msgId + ", msgType: " + msgType);

        try {
            if (msgType == SapApi.REQUEST) {
                switch (msgId) {
                case SapApi.RIL_SIM_SAP_CONNECT:
                    bytes = ((SapApi.RIL_SIM_SAP_CONNECT_REQ)msg).toByteArray();
                    break;
                case SapApi.RIL_SIM_SAP_DISCONNECT:
                    bytes = ((SapApi.RIL_SIM_SAP_DISCONNECT_REQ)msg).toByteArray();
                    break;
                case SapApi.RIL_SIM_SAP_APDU:
                    bytes = ((SapApi.RIL_SIM_SAP_APDU_REQ)msg).toByteArray();
                    break;
                case SapApi.RIL_SIM_SAP_TRANSFER_ATR:
                    bytes = ((SapApi.RIL_SIM_SAP_TRANSFER_ATR_REQ)msg).toByteArray();
                    break;
                case SapApi.RIL_SIM_SAP_POWER:
                    bytes = ((SapApi.RIL_SIM_SAP_POWER_REQ)msg).toByteArray();
                    break;
                case SapApi.RIL_SIM_SAP_RESET_SIM:
                    bytes = ((SapApi.RIL_SIM_SAP_RESET_SIM_REQ)msg).toByteArray();
                    break;
                case SapApi.RIL_SIM_SAP_TRANSFER_CARD_READER_STATUS:
                    bytes = ((SapApi.RIL_SIM_SAP_TRANSFER_CARD_READER_STATUS_REQ)msg).toByteArray();
                    break;
                case SapApi.RIL_SIM_SAP_SET_TRANSFER_PROTOCOL:
                    bytes = ((SapApi.RIL_SIM_SAP_SET_TRANSFER_PROTOCOL_REQ)msg).toByteArray();
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
            if (msgType == SapApi.RESPONSE) {
                switch (msgId) {
                case SapApi.RIL_SIM_SAP_CONNECT:
                    msg = SapApi.RIL_SIM_SAP_CONNECT_RSP.parseFrom(bytes);
                    break;
                case SapApi.RIL_SIM_SAP_DISCONNECT:
                    msg = SapApi.RIL_SIM_SAP_DISCONNECT_RSP.parseFrom(bytes);
                    break;
                case SapApi.RIL_SIM_SAP_APDU:
                    msg = SapApi.RIL_SIM_SAP_APDU_RSP.parseFrom(bytes);
                    break;
                case SapApi.RIL_SIM_SAP_TRANSFER_ATR:
                    msg = SapApi.RIL_SIM_SAP_TRANSFER_ATR_RSP.parseFrom(bytes);
                    break;
                case SapApi.RIL_SIM_SAP_POWER:
                    msg = SapApi.RIL_SIM_SAP_POWER_RSP.parseFrom(bytes);
                    break;
                case SapApi.RIL_SIM_SAP_RESET_SIM:
                    msg = SapApi.RIL_SIM_SAP_RESET_SIM_RSP.parseFrom(bytes);
                    break;
                case SapApi.RIL_SIM_SAP_TRANSFER_CARD_READER_STATUS:
                    msg = SapApi.RIL_SIM_SAP_TRANSFER_CARD_READER_STATUS_RSP.parseFrom(bytes);
                    break;
                case SapApi.RIL_SIM_SAP_SET_TRANSFER_PROTOCOL:
                    msg = SapApi.RIL_SIM_SAP_SET_TRANSFER_PROTOCOL_RSP.parseFrom(bytes);
                    break;
                default:
                    Log.e(LOG_TAG, "unexpected msgId");
                };
            } else if (msgType == SapApi.UNSOL_RESPONSE) {
                switch (msgId) {
                case SapApi.RIL_SIM_SAP_DISCONNECT:
                    msg = SapApi.RIL_SIM_SAP_DISCONNECT_IND.parseFrom(bytes);
                    break;
                case SapApi.RIL_SIM_SAP_STATUS:
                    msg = SapApi.RIL_SIM_SAP_STATUS_IND.parseFrom(bytes);
                    break;
                case SapApi.RIL_SIM_SAP_ERROR_RESP:
                    msg = SapApi.RIL_SIM_SAP_ERROR_RSP.parseFrom(bytes);
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

    public static byte[] packMsgHeader(SapApi.MsgHeader header) {
        Log.i(LOG_TAG, "packMsgHeader()");
        byte[] bytes = null;
        try {
            bytes = header.toByteArray();
        } catch (Exception e) {
            Log.e(LOG_TAG, "Exception in msg protobuf encoding");
        }

        return bytes;
    }

    public static SapApi.MsgHeader unpackMsgHeader(byte[] bytes) {
        Log.i(LOG_TAG, "unpackMsgHeader()");
        SapApi.MsgHeader header = null;
        try {
            header = SapApi.MsgHeader.parseFrom(bytes);
        } catch (Exception e) {
            Log.e(LOG_TAG, "Exception in tag protobuf decoding");
        }

        return header;
    }
}
