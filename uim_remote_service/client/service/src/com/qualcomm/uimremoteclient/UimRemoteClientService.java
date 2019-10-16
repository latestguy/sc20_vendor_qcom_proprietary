/******************************************************************************
  @file    UimRemoteClientService.java

  ---------------------------------------------------------------------------
  Copyright (c) 2014,2015 Qualcomm Technologies, Inc.  All Rights Reserved.
  Qualcomm Technologies Proprietary and Confidential.
  ---------------------------------------------------------------------------
******************************************************************************/

package com.qualcomm.uimremoteclient;

import com.qualcomm.uimremoteclient.UimRemoteClientSocket;
import com.google.protobuf.micro.ByteStringMicro;
import com.qualcomm.uimremoteclient.UimRemoteClientMsgPacking;

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

public class UimRemoteClientService extends Service {
    private final String LOG_TAG = "UimRemoteClientService";

    public static final int EVENT_REQ = 1;
    public static final int EVENT_RESP = 2;

    public static class UimRemoteError {

        public static final int UIM_REMOTE_SUCCESS = 0;

        public static final int UIM_REMOTE_ERROR = 1;
    }

    private Context context;

    private int mToken = 0;

    private int simSlots = TelephonyManager.getDefault().getSimCount();

    private UimRemoteClientSocket[] mSocket = new UimRemoteClientSocket[simSlots];
    IUimRemoteClientServiceCallback mCb = null;

    private static class Application {
        public String name;
        public String key;
        public boolean parsingFail;
    }

    private Map UimRemoteClientWhiteList;

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
                UimRemoteClient.MessageTag tag = UimRemoteClient.MessageTag.parseFrom(bytes);

                Log.d( LOG_TAG, "handleMessage() - token: " + tag.getToken() +
                       ", type: " + tag.getType() +
                       ", Id: " + tag.getId() +
                       ", error: " + tag.getError() +
                       ", slot id: " + slotId );
                if (tag.getType() == UimRemoteClient.UIM_REMOTE_MSG_RESPONSE) {
                    switch (tag.getId()) {
                    case UimRemoteClient.UIM_REMOTE_EVENT:
                        UimRemoteClient.UimRemoteEventResp event_rsp;
                        event_rsp = UimRemoteClient.UimRemoteEventResp.parseFrom(tag.getPayload().toByteArray());
                        mCb.uimRemoteEventResponse(slotId, event_rsp.getResponse());
                        break;
                    case UimRemoteClient.UIM_REMOTE_APDU:
                        UimRemoteClient.UimRemoteApduResp adpu_rsp;
                        adpu_rsp = UimRemoteClient.UimRemoteApduResp.parseFrom(tag.getPayload().toByteArray());
                        mCb.uimRemoteApduResponse(slotId, adpu_rsp.getStatus());
                        break;
                    default:
                        Log.e(LOG_TAG, "unexpected msg id");
                    }
                } else if (tag.getType() == UimRemoteClient.UIM_REMOTE_MSG_INDICATION) {
                    switch (tag.getId()) {
                    case UimRemoteClient.UIM_REMOTE_APDU:
                        UimRemoteClient.UimRemoteApduInd adpu_ind;
                        adpu_ind = UimRemoteClient.UimRemoteApduInd.parseFrom(tag.getPayload().toByteArray());
                        mCb.uimRemoteApduIndication(slotId, adpu_ind.getApduCommand().toByteArray());
                        break;
                    case UimRemoteClient.UIM_REMOTE_CONNECT:
                        mCb.uimRemoteConnectIndication(slotId);
                        break;
                    case UimRemoteClient.UIM_REMOTE_DISCONNECT:
                        mCb.uimRemoteDisconnectIndication(slotId);
                        break;
                    case UimRemoteClient.UIM_REMOTE_POWER_UP:
                        mCb.uimRemotePowerUpIndication(slotId);
                        break;
                    case UimRemoteClient.UIM_REMOTE_POWER_DOWN:
                        mCb.uimRemotePowerDownIndication(slotId);
                        break;
                    case UimRemoteClient.UIM_REMOTE_RESET:
                        mCb.uimRemoteResetIndication(slotId);
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
            mSocket[i]= new UimRemoteClientSocket(mRespHdlr, i);
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

    private final IUimRemoteClientService.Stub mBinder = new IUimRemoteClientService.Stub() {
        public int registerCallback(IUimRemoteClientServiceCallback cb) {
            if(!verifyAuthenticity(mBinder.getCallingUid())) {
                Log.d(LOG_TAG, "Cannot perform! returning failure");
                return UimRemoteError.UIM_REMOTE_ERROR;
            }
            UimRemoteClientService.this.mCb = cb;
            if (cb == null) {
                Log.d(LOG_TAG, "registerCallback() - null cb");
            }
            return UimRemoteError.UIM_REMOTE_SUCCESS;
        }

        public int deregisterCallback(IUimRemoteClientServiceCallback cb) {
            if(!verifyAuthenticity(mBinder.getCallingUid())) {
                Log.d(LOG_TAG, "Cannot perform! returning failure");
                return UimRemoteError.UIM_REMOTE_ERROR;
            }
            UimRemoteClientService.this.mCb = null;
            return UimRemoteError.UIM_REMOTE_SUCCESS;
        }

        public int uimRemoteEvent(int slot, int event, byte[] atr, int errCode, boolean has_transport, int transport, boolean has_usage, int usage, boolean has_apdu_timeout, int apdu_timeout, boolean has_disable_all_polling, int disable_all_polling, boolean has_poll_timer, int poll_timer) {
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

            Log.d(LOG_TAG, "uimRemoteEvent() - slot: " + slot + "; event: " + event);

            // get tag;
            UimRemoteClient.MessageTag tag = new UimRemoteClient.MessageTag();
            tag.setToken(mToken++);
            tag.setType(UimRemoteClient.UIM_REMOTE_MSG_REQUEST);
            tag.setId(UimRemoteClient.UIM_REMOTE_EVENT);
            tag.setError(UimRemoteClient.UIM_REMOTE_ERR_SUCCESS);

            // get request
            UimRemoteClient.UimRemoteEventReq req = new UimRemoteClient.UimRemoteEventReq();
            req.setEvent(event);
            req.setAtr(ByteStringMicro.copyFrom(atr));
            req.setErrorCode(errCode);
            if(has_transport) {
                req.setTransport(transport);
            }
            if(has_usage) {
                req.setUsage(usage);
            }
            if(has_apdu_timeout) {
                req.setApduTimeout(apdu_timeout);
            }
            if(has_disable_all_polling) {
                req.setDisableAllPolling(disable_all_polling);
            }
            if(has_poll_timer) {
                req.setPollTimer(poll_timer);
            }

            tag.setPayload(ByteStringMicro.copyFrom(
                                UimRemoteClientMsgPacking.packMsg( UimRemoteClient.UIM_REMOTE_EVENT,
                                                             UimRemoteClient.UIM_REMOTE_MSG_REQUEST,
                                                             req) ));

            Message msg = mSocket[slot].obtainMessage(EVENT_REQ, UimRemoteClientMsgPacking.packTag(tag));
            msg.sendToTarget();
            return UimRemoteError.UIM_REMOTE_SUCCESS;
        }

        public int uimRemoteApdu(int slot, int apduStatus, byte[] apduResp) {
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

            Log.d(LOG_TAG, "uimRemoteApdu() - slot: " + slot + "; adpuStatus: " + apduStatus);

            // get tag;
            UimRemoteClient.MessageTag tag = new UimRemoteClient.MessageTag();
            tag.setToken(mToken++);
            tag.setType(UimRemoteClient.UIM_REMOTE_MSG_REQUEST);
            tag.setId(UimRemoteClient.UIM_REMOTE_APDU);
            tag.setError(UimRemoteClient.UIM_REMOTE_ERR_SUCCESS);

            // get request
            UimRemoteClient.UimRemoteApduReq req = new UimRemoteClient.UimRemoteApduReq();
            req.setStatus(apduStatus);
            req.setApduResponse(ByteStringMicro.copyFrom(apduResp));

            tag.setPayload(ByteStringMicro.copyFrom(
                                UimRemoteClientMsgPacking.packMsg( UimRemoteClient.UIM_REMOTE_APDU,
                                                             UimRemoteClient.UIM_REMOTE_MSG_REQUEST,
                                                             req) ));

            Message msg = mSocket[slot].obtainMessage(EVENT_REQ, UimRemoteClientMsgPacking.packTag(tag));
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
                    if(app.parsingFail){
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
                 UimRemoteClientWhiteList = Collections.unmodifiableMap(table);
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

        if(UimRemoteClientWhiteList == null) {
            return ret;
        }
        String[] packageNames = context.getPackageManager().getPackagesForUid(uid);
        for(String packageName : packageNames){
            if(UimRemoteClientWhiteList.containsKey(packageName)){
                String hash = (String)UimRemoteClientWhiteList.get(packageName);
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
