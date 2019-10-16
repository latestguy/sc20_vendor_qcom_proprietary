/******************************************************************************
  @file    UimRemoteServerService.java

  ---------------------------------------------------------------------------
  Copyright (c) 2014,2015 Qualcomm Technologies, Inc.  All Rights Reserved.
  Qualcomm Technologies Proprietary and Confidential.
  ---------------------------------------------------------------------------
******************************************************************************/

package com.qualcomm.uimremoteserver;

import com.qualcomm.uimremoteserver.UimRemoteServerSocket;
import com.google.protobuf.micro.ByteStringMicro;
import com.qualcomm.uimremoteserver.UimRemoteServerMsgPacking;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;

import android.content.Context;
import android.telephony.TelephonyManager;
import android.content.pm.Signature;
import android.content.pm.PackageManager;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import java.io.IOException;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import android.content.res.XmlResourceParser;

import java.io.*;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.MessageDigest;

public class UimRemoteServerService extends Service {
    private final String LOG_TAG = "UimRemoteServerService";

    public static final int EVENT_REQ = 1;
    public static final int EVENT_RESP = 2;

    public static class UimRemoteError {

        public static final int UIM_REMOTE_SUCCESS = 0;

        public static final int UIM_REMOTE_ERROR = 1;
    }

    private static class Application {
        public String name;
        public String key;
        public boolean parsingFail;
    }

    private Map UimRemoteServerWhiteList;

    private Context context;

    private int mToken = 0;

    private int simSlots = TelephonyManager.getDefault().getSimCount();

    private UimRemoteServerSocket[] mSocket = new UimRemoteServerSocket[simSlots];
    IUimRemoteServerServiceCallback mCb = null;

    private Handler mRespHdlr = new Handler() {
        public void handleMessage (Message msg) {
            Log.i(LOG_TAG, "handleMessage()");
            if (mCb == null) {
                Log.d(LOG_TAG, "handleMessage() - null mCb");
                return;
            }

            try {
                byte[] bytes = (byte[])msg.obj;
                int slotId = msg.arg1;
                SapApi.MsgHeader header = UimRemoteServerMsgPacking.unpackMsgHeader(bytes);

                Log.d( LOG_TAG, "handleMessage() - token: " + header.getToken() +
                       ", type: " + header.getType() +
                       ", Id: " + header.getId() +
                       ", error: " + header.getError() +
                       ", slot id: " + slotId );

                Object msgPayload = UimRemoteServerMsgPacking.unpackMsg(header.getId(), header.getType(), header.getPayload().toByteArray());

                if (header.getType() == SapApi.RESPONSE) {
                    switch (header.getId()) {
                    case SapApi.RIL_SIM_SAP_CONNECT:
                        mCb.uimRemoteServerConnectResp(
                            slotId,
                            ((SapApi.RIL_SIM_SAP_CONNECT_RSP)msgPayload).getResponse(),
                            ((SapApi.RIL_SIM_SAP_CONNECT_RSP)msgPayload).getMaxMessageSize() );
                        break;
                    case SapApi.RIL_SIM_SAP_DISCONNECT:
                        mCb.uimRemoteServerDisconnectResp(slotId, 0); // success
                        break;
                    case SapApi.RIL_SIM_SAP_APDU:
                        mCb.uimRemoteServerApduResp(
                            slotId,
                            ((SapApi.RIL_SIM_SAP_APDU_RSP)msgPayload).getResponse(),
                            ((SapApi.RIL_SIM_SAP_APDU_RSP)msgPayload).getApduResponse().toByteArray() );
                        break;
                    case SapApi.RIL_SIM_SAP_TRANSFER_ATR:
                        mCb.uimRemoteServerTransferAtrResp(
                            slotId,
                            ((SapApi.RIL_SIM_SAP_TRANSFER_ATR_RSP)msgPayload).getResponse(),
                            ((SapApi.RIL_SIM_SAP_TRANSFER_ATR_RSP)msgPayload).getAtr().toByteArray() );
                        break;
                    case SapApi.RIL_SIM_SAP_POWER:
                        mCb.uimRemoteServerPowerResp(
                            slotId,
                            ((SapApi.RIL_SIM_SAP_POWER_RSP)msgPayload).getResponse() );
                        break;
                    case SapApi.RIL_SIM_SAP_RESET_SIM:
                        mCb.uimRemoteServerResetSimResp(
                            slotId,
                            ((SapApi.RIL_SIM_SAP_RESET_SIM_RSP)msgPayload).getResponse() );
                        break;
                    default:
                        Log.e(LOG_TAG, "unexpected msg id");
                    }
                } else if (header.getType() == SapApi.UNSOL_RESPONSE) {
                    switch (header.getId()) {
                    case SapApi.RIL_SIM_SAP_DISCONNECT:
                        mCb.uimRemoteServerDisconnectInd(
                            slotId,
                            ((SapApi.RIL_SIM_SAP_DISCONNECT_IND)msgPayload).getDisconnectType() );
                        break;
                    case SapApi.RIL_SIM_SAP_STATUS:
                        mCb.uimRemoteServerStatusInd(
                            slotId,
                            ((SapApi.RIL_SIM_SAP_STATUS_IND)msgPayload).getStatusChange());
                        break;
                    default:
                        Log.e(LOG_TAG, "unexpected msg id");
                    }
                } else {
                    Log.e(LOG_TAG, "unexpected msg type");
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "error occured when parsing the resp/ind");
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(LOG_TAG, "onCreate()");
        context = this;
        Log.i(LOG_TAG, "simCount: "+ simSlots);
        for(int i = 0; i < simSlots; i++) {
            mSocket[i] = new UimRemoteServerSocket(mRespHdlr, i);
            new Thread(mSocket[i]).start();
        }

        //initing whitelist
        getWhiteList();
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(LOG_TAG, "onBind()");
        return mBinder;
    }

    @Override
    public void onDestroy() {
        Log.i(LOG_TAG, "onDestroy()");
        for(int i = 0; i < simSlots; i++) {
            mSocket[i].toDestroy();
        }
        stopSelf();
        super.onDestroy();
    }

    private final IUimRemoteServerService.Stub mBinder = new IUimRemoteServerService.Stub() {
        public int registerCallback(IUimRemoteServerServiceCallback cb) {
            if(!verifyAuthenticity(mBinder.getCallingUid())) {
                Log.i(LOG_TAG, "Cannot perform! returning failure");
                return UimRemoteError.UIM_REMOTE_ERROR;
            }
            UimRemoteServerService.this.mCb = cb;
            if (cb == null) {
                Log.d(LOG_TAG, "registerCallback() - null cb");
            }
            return UimRemoteError.UIM_REMOTE_SUCCESS;
        }

        public int deregisterCallback(IUimRemoteServerServiceCallback cb) {
            if(!verifyAuthenticity(mBinder.getCallingUid())) {
                Log.d(LOG_TAG, "Cannot perform! returning failure");
                return UimRemoteError.UIM_REMOTE_ERROR;
            }
            UimRemoteServerService.this.mCb = null;
            return UimRemoteError.UIM_REMOTE_SUCCESS;
        }

        public int uimRemoteServerConnectReq(int slot, int maxMessageSize) {
            if(!verifyAuthenticity(mBinder.getCallingUid())) {
                Log.d(LOG_TAG, "Cannot perform! returning failure");
                return UimRemoteError.UIM_REMOTE_ERROR;
            }
            if(slot >= simSlots) {
                Log.e(LOG_TAG, "Sim Slot not supported!" + slot);
                return UimRemoteError.UIM_REMOTE_ERROR;
            }
            if (mSocket[slot] == null) {
                Log.e(LOG_TAG, "socket is not connected");
                return UimRemoteError.UIM_REMOTE_ERROR;
            }

            Log.d(LOG_TAG, "uimRemoteServerConnectReq() - maxMessageSize: " + maxMessageSize + "slot: " + slot);

            // get tag;
            SapApi.MsgHeader header = new SapApi.MsgHeader();
            header.setToken(mToken++);
            header.setType(SapApi.REQUEST);
            header.setId(SapApi.RIL_SIM_SAP_CONNECT);
            header.setError(SapApi.RIL_E_SUCCESS);

            // get request
            SapApi.RIL_SIM_SAP_CONNECT_REQ req = new SapApi.RIL_SIM_SAP_CONNECT_REQ();
            req.setMaxMessageSize(maxMessageSize);
            header.setPayload(ByteStringMicro.copyFrom(
                                UimRemoteServerMsgPacking.packMsg( SapApi.RIL_SIM_SAP_CONNECT,
                                                             SapApi.REQUEST,
                                                             req) ));

            Message msg = mSocket[slot].obtainMessage(EVENT_REQ, UimRemoteServerMsgPacking.packMsgHeader(header));
            msg.sendToTarget();
            return UimRemoteError.UIM_REMOTE_SUCCESS;
        }

        public int uimRemoteServerDisconnectReq(int slot) {
            if(!verifyAuthenticity(mBinder.getCallingUid())) {
                Log.d(LOG_TAG, "Cannot perform! returning failure");
                return UimRemoteError.UIM_REMOTE_ERROR;
            }
            if(slot >= simSlots) {
                Log.e(LOG_TAG, "Sim Slot not supported!" + slot);
                return UimRemoteError.UIM_REMOTE_ERROR;
            }
            if (mSocket[slot] == null) {
                Log.e(LOG_TAG, "socket is not connected");
                return UimRemoteError.UIM_REMOTE_ERROR;
            }

            Log.d(LOG_TAG, "uimRemoteServerDisconnectReq() slot: " + slot);

            // get tag;
            SapApi.MsgHeader header = new SapApi.MsgHeader();
            header.setToken(mToken++);
            header.setType(SapApi.REQUEST);
            header.setId(SapApi.RIL_SIM_SAP_DISCONNECT);
            header.setError(SapApi.RIL_E_SUCCESS);
            header.setPayload(ByteStringMicro.copyFrom(new byte[0]));

            Message msg = mSocket[slot].obtainMessage(EVENT_REQ, UimRemoteServerMsgPacking.packMsgHeader(header));
            msg.sendToTarget();
            return UimRemoteError.UIM_REMOTE_SUCCESS;
        }

        public int uimRemoteServerApduReq(int slot, byte[] cmd) {
            if(!verifyAuthenticity(mBinder.getCallingUid())) {
                Log.d(LOG_TAG, "Cannot perform! returning failure");
                return UimRemoteError.UIM_REMOTE_ERROR;
            }
            if(slot >= simSlots) {
                Log.e(LOG_TAG, "Sim Slot not supported!" + slot);
                return UimRemoteError.UIM_REMOTE_ERROR;
            }
            if (mSocket[slot] == null) {
                Log.e(LOG_TAG, "socket is not connected");
                return UimRemoteError.UIM_REMOTE_ERROR;
            }

            Log.d(LOG_TAG, "uimRemoteServerApduReq() - cmd length: " + cmd.length + " slot: " + slot);

            // get tag;
            SapApi.MsgHeader header = new SapApi.MsgHeader();
            header.setToken(mToken++);
            header.setType(SapApi.REQUEST);
            header.setId(SapApi.RIL_SIM_SAP_APDU);
            header.setError(SapApi.RIL_E_SUCCESS);

            // get request
            SapApi.RIL_SIM_SAP_APDU_REQ req = new SapApi.RIL_SIM_SAP_APDU_REQ();
            req.setType(1);
            req.setCommand(ByteStringMicro.copyFrom(cmd));
            header.setPayload(ByteStringMicro.copyFrom(
                                UimRemoteServerMsgPacking.packMsg( SapApi.RIL_SIM_SAP_APDU,
                                                             SapApi.REQUEST,
                                                             req) ));

            Message msg = mSocket[slot].obtainMessage(EVENT_REQ, UimRemoteServerMsgPacking.packMsgHeader(header));
            msg.sendToTarget();
            return UimRemoteError.UIM_REMOTE_SUCCESS;
        }

        public int uimRemoteServerTransferAtrReq(int slot) {
            if(!verifyAuthenticity(mBinder.getCallingUid())) {
                Log.d(LOG_TAG, "Cannot perform! returning failure");
                return UimRemoteError.UIM_REMOTE_ERROR;
            }
            if(slot >= simSlots) {
                Log.e(LOG_TAG, "Sim Slot not supported!" + slot);
                return UimRemoteError.UIM_REMOTE_ERROR;
            }
            if (mSocket[slot] == null) {
                Log.e(LOG_TAG, "socket is not connected");
                return UimRemoteError.UIM_REMOTE_ERROR;
            }

            Log.d(LOG_TAG, "uimRemoteServerTransferAtrReq() slot: " + slot);

            // get tag;
            SapApi.MsgHeader header = new SapApi.MsgHeader();
            header.setToken(mToken++);
            header.setType(SapApi.REQUEST);
            header.setId(SapApi.RIL_SIM_SAP_TRANSFER_ATR);
            header.setError(SapApi.RIL_E_SUCCESS);
            header.setPayload(ByteStringMicro.copyFrom(new byte[0]));

            Message msg = mSocket[slot].obtainMessage(EVENT_REQ, UimRemoteServerMsgPacking.packMsgHeader(header));
            msg.sendToTarget();
            return UimRemoteError.UIM_REMOTE_SUCCESS;
        }

        public int uimRemoteServerPowerReq(int slot, boolean state) {
            if(!verifyAuthenticity(mBinder.getCallingUid())) {
                Log.d(LOG_TAG, "Cannot perform! returning failure");
                return UimRemoteError.UIM_REMOTE_ERROR;
            }
            if(slot >= simSlots) {
                Log.e(LOG_TAG, "Sim Slot not supported!" + slot);
                return UimRemoteError.UIM_REMOTE_ERROR;
            }
            if (mSocket[slot] == null) {
                Log.e(LOG_TAG, "socket is not connected");
                return UimRemoteError.UIM_REMOTE_ERROR;
            }

            Log.d(LOG_TAG, "uimRemoteServerPowerReq() - state: " + state + " slot: " + slot);

            // get tag;
            SapApi.MsgHeader header = new SapApi.MsgHeader();
            header.setToken(mToken++);
            header.setType(SapApi.REQUEST);
            header.setId(SapApi.RIL_SIM_SAP_POWER);
            header.setError(SapApi.RIL_E_SUCCESS);

            // get request
            SapApi.RIL_SIM_SAP_POWER_REQ req = new SapApi.RIL_SIM_SAP_POWER_REQ();
            req.setState(state);
            header.setPayload(ByteStringMicro.copyFrom(
                                UimRemoteServerMsgPacking.packMsg( SapApi.RIL_SIM_SAP_POWER,
                                                             SapApi.REQUEST,
                                                             req) ));

            Message msg = mSocket[slot].obtainMessage(EVENT_REQ, UimRemoteServerMsgPacking.packMsgHeader(header));
            msg.sendToTarget();
            return UimRemoteError.UIM_REMOTE_SUCCESS;
        }

        public int uimRemoteServerResetSimReq(int slot) {
            if(!verifyAuthenticity(mBinder.getCallingUid())) {
                Log.d(LOG_TAG, "Cannot perform! returning failure");
                return UimRemoteError.UIM_REMOTE_ERROR;
            }
            if(slot >= simSlots) {
                Log.e(LOG_TAG, "Sim Slot not supported!" + slot);
                return UimRemoteError.UIM_REMOTE_ERROR;
            }
            if (mSocket[slot] == null) {
                Log.e(LOG_TAG, "socket is not connected");
                return UimRemoteError.UIM_REMOTE_ERROR;
            }

            Log.d(LOG_TAG, "uimRemoteServerResetSimReq() slot: " + slot);

            // get tag;
            SapApi.MsgHeader header = new SapApi.MsgHeader();
            header.setToken(mToken++);
            header.setType(SapApi.REQUEST);
            header.setId(SapApi.RIL_SIM_SAP_RESET_SIM);
            header.setError(SapApi.RIL_E_SUCCESS);
            header.setPayload(ByteStringMicro.copyFrom(new byte[0]));

            Message msg = mSocket[slot].obtainMessage(EVENT_REQ, UimRemoteServerMsgPacking.packMsgHeader(header));
            msg.sendToTarget();
            return UimRemoteError.UIM_REMOTE_SUCCESS;
        }
    };

    private Application readApplication(XmlResourceParser parser) throws XmlPullParserException, IOException {
        parser.require(XmlPullParser.START_TAG, null, "Application");
        Application app = new Application();
        int eventType = parser.next();
        while(eventType != XmlPullParser.END_TAG) {
            if(eventType != XmlPullParser.START_TAG) {
                app.parsingFail = true;
                Log.e(LOG_TAG, "parse fail");
                break;
            }
            String tagName = parser.getName();
            if(tagName.equals("PackageName")){
                eventType = parser.next();
                if(eventType == XmlPullParser.TEXT){
                    app.name = parser.getText();
                    eventType = parser.next();
                }
                if((eventType != XmlPullParser.END_TAG) || !(parser.getName().equals("PackageName"))){
                     //Invalid tag or invalid xml format
                    app.parsingFail = true;
                    Log.e(LOG_TAG, "parse fail");
                    break;
                }
            }
            else if(tagName.equals("SignatureHash")){
                eventType = parser.next();
                if(eventType == XmlPullParser.TEXT){
                    app.key = parser.getText();
                    eventType = parser.next();
                }
                if((eventType != XmlPullParser.END_TAG) || !(parser.getName().equals("SignatureHash"))){
                     //Invalid tag or invalid xml format
                    app.parsingFail = true;
                    Log.e(LOG_TAG, "parse fail");
                    break;
                }
            }
            else{
                 app.parsingFail = true;
                 Log.e(LOG_TAG, "parse fail" + tagName);
                 break;
            }
            eventType = parser.next();
        }
        if((eventType != XmlPullParser.END_TAG) || !(parser.getName().equals("Application"))){
            //End Tag that ended the loop is not Application
            app.parsingFail = true;
        }
        return app;
    }

    private void getWhiteList(){
        try {
            String name = null;
            Application app = null;
            boolean fail = false;
            int eventType;
            HashMap<String, String> table = new HashMap<String, String>();

            XmlResourceParser parser = this.getResources().getXml(R.xml.applist);
            parser.next();

            //Get the parser to point to Entries Tag.
            if(parser.getEventType() == XmlPullParser.START_DOCUMENT){
                name = parser.getName();
                Log.e(LOG_TAG, "name "+name);
                while(name == null ||
                       (name != null && !name.equals("Entries"))){
                    parser.next();
                    name = parser.getName();
                }
            }

            parser.require(XmlPullParser.START_TAG, null, "Entries");
            eventType = parser.next();

            //Loop until END_TAG is encountered
            while(eventType != XmlPullParser.END_TAG) {

                //If the TAG is not a START_TAG, break the loop
                //with Failure.
                if(eventType != XmlPullParser.START_TAG) {
                    fail = true;
                    Log.e(LOG_TAG, "parse fail");
                    break;
                }

                name = parser.getName();
                if(name.equals("Application")) {
                    app = readApplication(parser);
                    if(app.parsingFail)
                    {
                        fail = true;
                        Log.e(LOG_TAG, "parse fail");
                        break;
                    }
                    else if(app.name != null || app.key != null){
                        table.put(app.name, app.key);
                        Log.e(LOG_TAG, "appname: " +app.name+" appkey: "+app.key);
                    }
                }
                else {
                    fail = true;
                    Log.e(LOG_TAG, "parse fail" + name);
                    break;
                }
                eventType = parser.next();
            }
            if(fail || eventType != XmlPullParser.END_TAG ||
                     !(parser.getName().equals("Entries"))){
                //parsing failure
                Log.e(LOG_TAG, "FAIL");
            }
            else if(!table.isEmpty()) {
                 UimRemoteServerWhiteList = Collections.unmodifiableMap(table);
            }
        }
        catch(Exception e) {
            Log.e(LOG_TAG, "Exception: "+ e);
        }
    }

	private static String bytesToHex(byte[] inputBytes) {
        final StringBuilder sb = new StringBuilder();
        for(byte b : inputBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
    private boolean verifyAuthenticity(int uid){
        boolean ret = false;

        if(UimRemoteServerWhiteList == null) {
            return ret;
        }
        String[] packageNames = context.getPackageManager().getPackagesForUid(uid);
        for(String packageName : packageNames){
            if(UimRemoteServerWhiteList.containsKey(packageName)){
                String hash = (String)UimRemoteServerWhiteList.get(packageName);
                String compareHash = new String();
                try {
                    Signature[] sigs = context.getPackageManager().getPackageInfo(packageName, PackageManager.GET_SIGNATURES).signatures;
                    for(Signature sig: sigs) {

                        //get the raw certificate into input stream
                        final byte[] rawCert = sig.toByteArray();
                        InputStream certStream = new ByteArrayInputStream(rawCert);

                        //Read the X.509 Certificate into certBytes
                        final CertificateFactory certFactory = CertificateFactory.getInstance("X509");
                        final X509Certificate x509Cert = (X509Certificate)certFactory.generateCertificate(certStream);
                        byte[] certBytes = x509Cert.getEncoded();

                        //get the fixed SHA-1 cert
                        MessageDigest md = MessageDigest.getInstance("SHA-1");
	                    md.update(certBytes);
	                    byte[] certThumbprint = md.digest();

                        //cert in hex format
                        compareHash = bytesToHex(certThumbprint);

                        if(hash.equals(compareHash)) {
                            ret = true;
                            break;
                        }
                    }
                }
                catch(Exception e) {
                    Log.e(LOG_TAG, "Exception reading client data!" + e);
                }
            }
        }
        return ret;
    }
}
