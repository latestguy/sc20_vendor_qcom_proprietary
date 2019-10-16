/**
 * Copyright (c) 2014, Qualcomm Technologies, Inc. All Rights Reserved.
 * Qualcomm Technologies Proprietary and Confidential.
 */

package com.qualcomm.qti.service;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.text.SimpleDateFormat;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.uicc.IccCardStatus.CardState;
import com.android.internal.telephony.uicc.IccConstants;
import com.android.internal.telephony.uicc.IccFileHandler;
import com.android.internal.telephony.uicc.UiccCard;
import com.android.internal.telephony.uicc.UiccCardApplication;
import com.android.internal.telephony.uicc.UiccController;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppType;
import com.qualcomm.qti.autoregistration.RegistrationService;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncResult;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Parcelable;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.StatFs;
import android.os.Environment;
import android.os.SystemProperties;
import android.preference.PreferenceManager;
import android.telephony.CellLocation;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.cdma.CdmaCellLocation;
import android.telephony.gsm.GsmCellLocation;
import android.text.TextUtils;
import android.util.Log;
import org.codeaurora.internal.IExtTelephony;
import java.util.List;
import android.telephony.CellIdentityLte;
import android.telephony.CellInfo;
import android.telephony.CellInfoCdma;
import android.telephony.CellInfoLte;


public class DeviceInfoPool {

    public static class DeviceInfoReq {
        String key;
        Message callback;

        public DeviceInfoReq(String key, Message callback) {
            this.key = key;
            this.callback = callback;
        }

    }

    private static final String TAG = "DeviceInfoPool"; //DeviceInfoPool.class.getSimpleName();
    private static final String BASE_PARAM_REGVER = "5.0";
    private static final String MAN_PARAM_UETYPE = "1";
    private static final String SIM_TYPE_ICC = "1";
    private static final String SIM_TYPE_UICC = "2";
    private static final String DEFAULT_VALUE = "SC20CEPBR06A02";
    private static final boolean DBG = true;
    private static final String RESULT = "result";
    private static final String DEFAULT_HW_VERSION = "PVT2.0";
    private static final String FILENAME_META_VERSION = "/firmware/verinfo/ver_info.txt";
    private static final String PREF_WIFI_MAC = "wifi_macid";
    private static final String PREF_MEID = "pref_meid";

    private TelephonyManager mTelephonyMgr = null;
    private final Context mContext;
    private static DeviceInfoPool mDeviceInfoPool;
    private int mCtPhoneId = 0;
    // As per CT 6 modes requirement, SIM on dds sub is marked as primary SIM that reports as SIM1
    private int mPrimarySimPhoneId = 0;
    private SharedPreferences mSharedPreferences;

    public enum NodeParm {

        REGVER, MEID, MODEL, SWVER, SIM1CDMAIMSI, SIM2CDMAIMSI,

        UETYPE, SIM1ICCID, SIM2ICCID, SIM1LTEIMSI, SIM2LTEIMSI, SIM1TYPE,
        SID, NID, MACID, SIM1CELLID, SIM2CELLID,

        MANUFACTURE, OSVER, HWVER, PNAME, COLOR, MLPLVER, MSPLVER, CELLID, MMEID,
        ACCESSTYPE, REGDATE, IMEI1, IMEI2, BASEID, SIM2IMSI,

        NOVALUE;

        public static NodeParm toNodeParam(String str) {
            try {
                if (DBG) {
                    Log.d(TAG, "toNodeParam:" + str);
                }
                return valueOf(str);
            } catch (Exception ex) {
                return NOVALUE;
            }
        }
    }

    private static final int EVENT_GET_DEVICE_INFO = 1;

    private Handler mMainHandler = new Handler(Looper.getMainLooper()) {

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_GET_DEVICE_INFO:
                    DeviceInfoReq req = (DeviceInfoReq) msg.obj;
                    Message message = req.callback;
                    if (DBG) {
                        Log.d(TAG, "handle key:" + req.key);
                    }
                    switch (NodeParm.toNodeParam(req.key)) {
                        // Base params
                        case REGVER:
                            response(message, getRegver());
                            break;
                        case MEID:
                            getMeid(message);
                            break;
                        case MODEL:
                            response(message, getDeviceModel());
                            break;
                        case SWVER:
                            response(message, getSwVersion());
                            break;
                        case SIM1CDMAIMSI:
                            response(message, getSimCdmaIMSI(getPrimaryPhoneId()));
                            break;
                        // Mandatory params
                        case UETYPE:
                            response(message, getUeType());
                            break;
                        case SIM1ICCID:
                            response(message, getSimIccid(getPrimaryPhoneId()));
                            break;
                        case SIM2ICCID:
                            response(message, getSimIccid(getSecondaryPhoneId()));
                            break;
                        case SIM1LTEIMSI:
                            response(message, getSimLTEIMSI(getPrimaryPhoneId()));
                            break;
                        case SIM2LTEIMSI:
                        case SIM2IMSI:
                            response(message, getSimLTEIMSI(getSecondaryPhoneId()));
                            break;
                        case SIM1TYPE:
                            response(message, getSimType(getPrimaryPhoneId()));
                            break;
                        case SIM2CDMAIMSI:
                            response(message, getSimCdmaIMSI(getSecondaryPhoneId()));
                            break;
                        case SID:
                            response(message, getSid());
                            break;
                        case NID:
                            response(message, getNid());
                            break;
                        case MACID:
                            response(message, getMacID());
                            break;
                        // Optional params
                        case MANUFACTURE:
                            response(message, getManufacture());
                            break;
                        case OSVER:
                            response(message, getOSVersion());
                            break;
                        case HWVER:
                            response(message, getHWVersion());
                            break;
                        case MLPLVER:
                            mHandler.getMlplVersion(PhoneConstants.SUB1, message);
                            break;
                        case MSPLVER:
                            mHandler.getMsplVersion(PhoneConstants.SUB1, message);
                            break;
                        case ACCESSTYPE:
                            response(message, getAccessType());
                            break;
                        case REGDATE:
                            response(message, getRegDate());
                            break;
                        case IMEI1:
                            response(message, getImei(getPrimaryPhoneId()));
                            break;
                        case IMEI2:
                            response(message, getImei(getSecondaryPhoneId()));
                            break;
                        case BASEID:
                            response(message, getBaseId());
                            break;

                        case SIM1CELLID:
                            response(message, getCellId(getPrimaryPhoneId()));
                            break;
                        case SIM2CELLID:
                            response(message, getCellId(getSecondaryPhoneId()));
                            break;

                        default:
                            response(message, null);
                    }
            }
        }

    };

    //1, If only have 1 ct card, use ct phone id
    //2, If have non ct card, use primary phone id
    //3, If have 2 ct cards, use primary phone id
    //mCtPhoneId is the first ct card, it will use wrong phone id if the primary card
    //is the second ct card without this function.
    private int getPhoneId() {
        if (mCtPhoneId >= 0) {
            String primarySimIccId = getSimIccid(mPrimarySimPhoneId);
            if (primarySimIccId != null) {
                for (String iin : RegistrationService.CT_IIN.split(",")) {
                    if (primarySimIccId.startsWith(iin)) {
                        return mPrimarySimPhoneId;
                    } else {
                        return mCtPhoneId;
                    }
                }
            } else {
                return getSecondaryPhoneId();
            }
        } else {
            return mPrimarySimPhoneId;
        }
        return SubscriptionManager.INVALID_PHONE_INDEX;
    }

    private int getPrimaryPhoneId() {
        return mPrimarySimPhoneId;
    }

    private int getSecondaryPhoneId() {
        return (mPrimarySimPhoneId + 1) % mTelephonyMgr.getPhoneCount();
    }

    private DeviceInfoPool(Context context) {
        mContext = context;
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        mTelephonyMgr = TelephonyManager.from(context);
    }

    public static DeviceInfoPool getInstance(Context context) {
        if (mDeviceInfoPool == null) {
            mDeviceInfoPool = new DeviceInfoPool(context);
        }
        return mDeviceInfoPool;
    }

    public boolean dispatchNodeOperation(String nodeParam, Message message) {
        if (NodeParm.toNodeParam(nodeParam) != NodeParm.NOVALUE) {
            if(message != null) {
                mCtPhoneId = message.arg1;
                mPrimarySimPhoneId = message.arg2;
            }
            mMainHandler.obtainMessage(EVENT_GET_DEVICE_INFO,
                    new DeviceInfoReq(nodeParam, message))
                    .sendToTarget();
            return true;
        }
        return false;
    }

    private ObtainVersionHandler mHandler = new ObtainVersionHandler();

    private class ObtainVersionHandler extends Handler {
        private static final int MESSAGE_GET_EF_MSPL = 0;
        private static final int MESSAGE_GET_EF_MLPL = 1;
        private static final int MESSAGE_GET_DEVICE_IDENTITY = 2;
        // MSPL ID is x bytes data
        private static final int NUM_BYTES_MSPL_ID = 5;
        // MLPL ID is 8 bytes data
        private static final int NUM_BYTES_MLPL_ID = 5;

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_GET_EF_MSPL:
                    handleGetEFMspl(msg);
                    break;
                case MESSAGE_GET_EF_MLPL:
                    handleGetEFMlpl(msg);
                    break;
                case MESSAGE_GET_DEVICE_IDENTITY:
                    handleGetDeviceIdentity(msg);
                    break;
            }
        }

        private void handleGetEFMspl(Message msg) {
            AsyncResult ar = (AsyncResult) msg.obj;
            String msplVersion = null;
            byte[] data = (byte[]) ar.result;
            if (ar.exception == null) {
                if (data.length > NUM_BYTES_MSPL_ID - 1) {
                    int msplId = ((data[3] & 0xFF) << 8) | (data[4] & 0xFF);
                    msplVersion = String.valueOf(msplId);
                }
            }
            response((Message) ar.userObj, msplVersion);
        }

        private void handleGetEFMlpl(Message msg) {
            AsyncResult ar = (AsyncResult) msg.obj;
            String mlplVersion = null;
            byte[] data = (byte[]) ar.result;
            if (ar.exception == null) {
                if (data.length > NUM_BYTES_MLPL_ID - 1) {
                    int mlplId = ((data[3] & 0xFF) << 8) | (data[4] & 0xFF);
                    mlplVersion = String.valueOf(mlplId);
                }
            }
            response((Message) ar.userObj, mlplVersion);
        }

        private void handleGetDeviceIdentity(Message msg) {
            AsyncResult ar = (AsyncResult) msg.obj;
            String meid = null;
            if (ar.exception == null) {
                String[] respId = (String[])ar.result;
                meid = respId[3];
                mSharedPreferences.edit().putString(PREF_MEID, meid).apply();
            }
            response((Message) ar.userObj, invalidMeid(meid) ? null : meid);
        }

        public boolean getMsplVersion(int slotId, Message message) {
            UiccController controller = UiccController.getInstance();
            if (controller != null) {
                IccFileHandler fh = controller.getIccFileHandler(slotId,
                        UiccController.APP_FAM_3GPP2);
                if (fh != null) {
                    fh.loadEFTransparent(IccConstants.EF_CSIM_MSPL, NUM_BYTES_MSPL_ID,
                            mHandler.obtainMessage(ObtainVersionHandler.MESSAGE_GET_EF_MSPL,
                                    message));
                    return true;
                }
            }
            response(message, null);
            return false;
        }

        public boolean getMlplVersion(int slotId, Message message) {
            UiccController controller = UiccController.getInstance();
            if (controller != null) {
                IccFileHandler fh = controller.getIccFileHandler(slotId,
                        UiccController.APP_FAM_3GPP2);
                if (fh != null) {
                    fh.loadEFTransparent(IccConstants.EF_CSIM_MLPL, NUM_BYTES_MLPL_ID,
                            mHandler.obtainMessage(ObtainVersionHandler.MESSAGE_GET_EF_MLPL,
                                    message));
                    return true;
                }
            }
            response(message, null);
            return false;
        }

        public void getMeid(Phone phone, Message message) {
            if (phone != null) {
                phone.mCi.getDeviceIdentity(obtainMessage(MESSAGE_GET_DEVICE_IDENTITY, message));
            }
        }
    }

    private Phone getPhoneInstance(int phoneId) {
        Phone phone = null;
        if (mTelephonyMgr.isMultiSimEnabled()) {
            phone = PhoneFactory.getPhone(phoneId);
        } else {
            phone = PhoneFactory.getDefaultPhone();
        }
        return phone;
    }

    public String getRegver() {
        return BASE_PARAM_REGVER;
    }

    public String getDeviceModel() {
        return "QWS-SC20 CE";
        //return Build.MODEL;
    }

    public void getMeid(Message message) {
        String meid = mSharedPreferences.getString(PREF_MEID, "");
        if (invalidMeid(meid)) {
            int phoneId = 0;
            IExtTelephony extTelephony = IExtTelephony.Stub.
                    asInterface(ServiceManager.getService("extphone"));
            try {
                if (extTelephony != null) {
                    phoneId = extTelephony.getPrimaryStackPhoneId();
                }
            } catch (RemoteException ex) {
                Log.w(TAG, "Failed to get primary stack id");
            }

            Log.d(TAG, "Primary stack phone id = " + phoneId);
            Phone phone = getPhoneInstance(phoneId);
            if (phone != null) {
                meid =  phone.getMeid();
                Log.d(TAG, "Meid from phone = " + meid);
                if (invalidMeid(meid)) {
                    mHandler.getMeid(phone, message);
                } else {
                    mSharedPreferences.edit().putString(PREF_MEID, meid).apply();
                    response(message, meid);
                }
            }
        } else {
            Log.d(TAG, "Meid = " + meid);
            response(message, meid);
        }
    }

    private boolean invalidMeid(String meid) {
        return TextUtils.isEmpty(meid) || meid.equals("0");
    }

    public String getSwVersion() {
        String swVersion = SystemProperties.get("ro.build.au_rev", null);
        if (TextUtils.isEmpty(swVersion)) return DEFAULT_VALUE;
        return swVersion;
    }

    public UiccCard getUiccCard(int cardIndex) {
        UiccCard uiccCard = null;
        if (!mTelephonyMgr.isMultiSimEnabled()) {
            uiccCard = UiccController.getInstance().getUiccCard(0);
        } else {
            uiccCard = UiccController.getInstance().getUiccCard(cardIndex);
        }
        return uiccCard;
    }

    private UiccCardApplication getValidApp(AppType appType, UiccCard uiccCard) {
        UiccCardApplication validApp = null;
        int numApps = uiccCard.getNumApplications();
        for (int i = 0; i < numApps; i++) {
            UiccCardApplication app = uiccCard.getApplicationIndex(i);
            if (app != null && app.getType() != AppType.APPTYPE_UNKNOWN
                    && app.getType() == appType) {
                validApp = app;
                break;
            }
        }
        return validApp;
    }

    public String getSimIMISI(int sub, AppType apptype) {
        String simIMSI = null;
        UiccCardApplication validApp = getValidApp(apptype, getUiccCard(sub));
        if (validApp != null) {
            simIMSI = validApp.getIccRecords().getIMSI();
        }
        return simIMSI;
    }

    public String getSimCdmaIMSI(int sub) {
        String ruimImsi = getSimIMISI(sub, AppType.APPTYPE_RUIM);
        String csimImsi = getSimIMISI(sub, AppType.APPTYPE_CSIM);
        return TextUtils.isEmpty(ruimImsi) ? csimImsi : ruimImsi;
    }

    public String getSimGIMSI(int sub) {
        return getSimIMISI(sub, AppType.APPTYPE_SIM);
    }

    public String getSimLTEIMSI(int sub) {
        return getSimIMISI(sub, AppType.APPTYPE_USIM);
    }

    private String getSim2IMSI(int sub) {
        String mIMSI = null;
        if (mTelephonyMgr.isMultiSimEnabled()) {
            mIMSI = mTelephonyMgr.getSubscriberId(SubscriptionManager.getSubId(sub)[0]);
        } else {
            mIMSI = mTelephonyMgr.getSubscriberId();
        }
        return mIMSI;
    }

    public boolean isUiccCard(UiccCard uiccCard) {
        return uiccCard.isApplicationOnIcc(AppType.APPTYPE_CSIM)
                || uiccCard.isApplicationOnIcc(AppType.APPTYPE_USIM)
                || uiccCard.isApplicationOnIcc(AppType.APPTYPE_ISIM);
    }

    public String getSimType(int sub) {
        UiccCard uiccCard = getUiccCard(sub);
        if (uiccCard == null) {
            return null;
        }
        if (uiccCard.getCardState() == CardState.CARDSTATE_ABSENT) {
            return null;
        }
        if (isUiccCard(uiccCard)) {
            return SIM_TYPE_UICC;
        }
        return SIM_TYPE_ICC;
    }

    public String getUeType() {
        return MAN_PARAM_UETYPE;
    }

    public String getSim1Iccid() {
        return getSimIccid(PhoneConstants.SUB1);
    }

    public String getSim2Iccid() {
        return getSimIccid(PhoneConstants.SUB2);
    }

    public String getSimIccid(int phoneId) {
        String iccid = null;
        if (mTelephonyMgr.isMultiSimEnabled()) {
            iccid = mTelephonyMgr.getSimSerialNumber(SubscriptionManager.getSubId(phoneId)[0]);
        } else {
            iccid = mTelephonyMgr.getSimSerialNumber();
        }
        return iccid;
    }

    public String getSid() {
        Phone phone = getPhoneInstance(getPhoneId());
        if (phone == null) return null;
        String sid = null;
        CellLocation cellLocation = phone.getCellLocation();
        if (cellLocation instanceof CdmaCellLocation) {
            sid = String.valueOf(((CdmaCellLocation) cellLocation).getSystemId());
        }
        return sid;
    }

    public String getNid() {
        Phone phone = getPhoneInstance(getPhoneId());
        if (phone == null) return null;
        String nid = null;
        CellLocation cellLocation = phone.getCellLocation();
        if (cellLocation instanceof CdmaCellLocation) {
            nid = String.valueOf(((CdmaCellLocation) cellLocation).getNetworkId());
        }
        return nid;
    }

    public String getBaseId() {
        String baseId = "";
        Phone phone = getPhoneInstance(getPhoneId());
        UiccCard uiccCard = getUiccCard(getPrimaryPhoneId());
        // For class a, maybe the primary sim is absent
        if (uiccCard != null && uiccCard.getCardState() == CardState.CARDSTATE_ABSENT) {
            phone = getPhoneInstance(getSecondaryPhoneId());
        }
        if (phone != null) {
            CellLocation cellLocation = phone.getCellLocation();
            if (cellLocation instanceof CdmaCellLocation) {
                baseId = String.valueOf(((CdmaCellLocation) cellLocation).getBaseStationId());
            } else if (cellLocation instanceof GsmCellLocation) {
                baseId = String.valueOf(((GsmCellLocation)cellLocation).getCid());
            }
        }
        return baseId;
    }

    public String getMacID() {
        String macAddress  =  mSharedPreferences.getString(PREF_WIFI_MAC, null);
        return macAddress;
    }

    public String getModelCTA() {
        String modelCTA = Build.MODEL;
        return modelCTA;
    }

    public String getManufacture() {
        String manufacture = Build.MANUFACTURER;
        return manufacture;
    }

    public String getOSVersion() {
        String osVersion = "android"+Build.VERSION.RELEASE;
        return osVersion;
    }

    public String getHWVersion() {
        String hwVersion = SystemProperties.get("ro.emmc_size", "16G");
        return hwVersion;
    }

    public String getImei(int slotId) {
        String imei = "";
        if (slotId < mTelephonyMgr.getPhoneCount()) {
            Phone phone = getPhoneInstance(slotId);
            imei = phone.getImei();
        }
        return imei;
    }

    public String getRegDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String time = sdf.format(new java.util.Date());
        return time;
    }

    public String getCellId(int slotId){
        String cellId = " ";
        if (slotId < mTelephonyMgr.getPhoneCount()) {
            Phone phone = getPhoneInstance(slotId);
            List<CellInfo> cellInfoList = phone.getAllCellInfo();
            Log.d(TAG, "cellInfoList = " + cellInfoList);

            if (cellInfoList != null){
                for (CellInfo cellInfo : cellInfoList) {
                    if (cellInfo instanceof CellInfoLte && cellInfo.isRegistered()) {
                        CellInfoLte cellInfoLte = (CellInfoLte) cellInfo;
                        CellIdentityLte cellIdentity = cellInfoLte.getCellIdentity();
                        int longCid = cellIdentity.getCi();
                        String cellidHex = DecToHex(longCid);
                        Log.d(TAG, "cellidHex = " + cellidHex);
                        String eNBHex = cellidHex.substring(0, cellidHex.length()-2);
                        String cellIdHex = cellidHex.substring(cellidHex.length()-2, cellidHex.length());
                        Log.d(TAG, "eNBHex = " + eNBHex + ", cellId = " + cellIdHex);
                        int eNB = HexToDec(eNBHex);
                        cellId = String.valueOf(HexToDec(cellIdHex));
                        Log.d(TAG, "eNB = " + Integer.toString(eNB) + ", cellId = " + cellId);
                    }
                }
            } else {
                Log.d(TAG, "cellId is null");
            }
        }
        return cellId;
    }


    // Decimal -> hexadecimal
    public String DecToHex(int dec){
        return String.format("%x", dec);
    }

    // hex -> decimal
    public int HexToDec(String hex){
        return Integer.parseInt(hex, 16);
    }


    public String getAccessType() {
        String accessType = null;
        ConnectivityManager connManager = (ConnectivityManager) mContext
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo[] networkInfos = connManager.getAllNetworkInfo();
        for (NetworkInfo networkInfo : networkInfos) {
            if (networkInfo.isConnected()) {
                int networkType = networkInfo.getType();
                if (networkType == ConnectivityManager.TYPE_WIFI
                        || networkType == ConnectivityManager.TYPE_MOBILE) {
                    // 1:network, 2:wifi
                    accessType = String.valueOf(networkType + 1);
                }
            }
        }
        return accessType;
    }

    private static String readLine(String filename) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(filename), 256);
        try {
            return reader.readLine();
        } finally {
            reader.close();
        }
    }

    private void response(Message callback, String result) {
        if (DBG) {
            Log.i(TAG, "response: [callback]=" + callback + " [result]=" + result);
        }
        if (callback == null) {
            return;
        }
        Bundle bundle = new Bundle();
        bundle.putString(RESULT, result);
        if (callback.obj != null && callback.obj instanceof Parcelable) {
            bundle.putParcelable("userobj", (Parcelable) callback.obj);
        }
        callback.obj = bundle;
        if (callback.replyTo != null) {
            try {
                callback.replyTo.send(callback);
            } catch (RemoteException e) {
                Log.w(TAG, "failed to response result", e);
            }
        } else if (callback.getTarget() != null) {
            callback.sendToTarget();
        } else {
            Log.w(TAG, "can't response the result, replyTo and target are all null!");
        }
    }
}
