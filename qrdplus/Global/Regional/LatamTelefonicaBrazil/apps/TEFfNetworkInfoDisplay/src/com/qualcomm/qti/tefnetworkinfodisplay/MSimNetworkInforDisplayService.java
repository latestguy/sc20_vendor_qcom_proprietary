/*Copyright (c) 2015 Qualcomm Technologies, Inc.
 *All Rights Reserved.
 *Confidential and Proprietary - Qualcomm Technologies, Inc.
*/
package com.qualcomm.qti.tefnetworkinfodisplay;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.IBinder;
import android.os.UserHandle;
import android.preference.Preference;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import android.util.Log;
import android.widget.RemoteViews;
import android.telephony.CellBroadcastMessage;
import android.telephony.CellInfo;
import android.telephony.CellInfoLte;
import android.telephony.CellIdentityLte;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.PhoneStateIntentReceiver;

public class MSimNetworkInforDisplayService extends Service {

    private TelephonyManager mTelephonyManager;
    private HashMap<String, String> mAreaInfo = new HashMap<String, String>();
    private List<SubscriptionInfo> mSubscriptionInfos = new ArrayList<SubscriptionInfo>();

    private PhoneStateIntentReceiver mPhoneStateReceiver;
    private PhoneStateListener[] mPhoneStateListener;
    private boolean mShowLatestAreaInfo = false;
    private int mNumPhones = 0;
    private static final int SINGLE_SIM = 1;
    public final int NOTIFICATION_ID = 0;

    static final String CB_AREA_INFO_RECEIVED_ACTION = "android.cellbroadcastreceiver"
            + ".CB_AREA_INFO_RECEIVED";

    static final String GET_LATEST_CB_AREA_INFO_ACTION = "android.cellbroadcastreceiver"
            + ".GET_LATEST_CB_AREA_INFO";

    static final String CB_AREA_INFO_SENDER_PERMISSION = "android.permission"
            + ".RECEIVE_EMERGENCY_BROADCAST";

    private ServiceState[] mMSimServiceState;

    private String[] mLatestAreaInfoSummary;

    RemoteViews mNotificationView;

    int mActivePhones;

    boolean[] mShowSpn;
    boolean[] mShowPlmn;
    String[] mSpn;
    String[] mPlmn;
    String[] mMSimNetworkName;
    String[] mMSimNetworkDetails;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {

        mTelephonyManager = (TelephonyManager) getApplicationContext()
                .getSystemService(TELEPHONY_SERVICE);

        mSubscriptionInfos = SubscriptionManager.from(getApplicationContext())
                .getActiveSubscriptionInfoList();

        if (mSubscriptionInfos != null) {
            mActivePhones = mSubscriptionInfos.size();
        }

        if( mActivePhones <=0) {
            this.stopSelf();
            return;
        }

        mNumPhones = mTelephonyManager.getPhoneCount();
        mShowSpn = new boolean[mNumPhones];
        mShowPlmn = new boolean[mNumPhones];
        mSpn = new String[mNumPhones];
        mPlmn = new String[mNumPhones];

        if(mSubscriptionInfos !=null) {
            for(SubscriptionInfo subInfo: mSubscriptionInfos) {
                mSpn[getPhoneId(subInfo.getSubscriptionId())] =
                    mTelephonyManager.getSimOperatorName
                        (getPhoneId(subInfo.getSubscriptionId()));
                mPlmn[getPhoneId(subInfo.getSubscriptionId())] =
                    mTelephonyManager.getNetworkOperatorName(subInfo.getSubscriptionId());
                if(mSpn[getPhoneId(subInfo.getSubscriptionId())].
                    equals(mPlmn[getPhoneId(subInfo.getSubscriptionId())])) {
                        mShowSpn[getPhoneId((subInfo.getSubscriptionId()))] = true;
                }
            }
        }

        mPhoneStateListener = new PhoneStateListener[mNumPhones];
        mLatestAreaInfoSummary = new String[mNumPhones];
        mMSimServiceState = new ServiceState[mNumPhones];

        mMSimNetworkName = new String[mNumPhones];
        mMSimNetworkDetails = new String[mNumPhones];

        if (mActivePhones == 1) {
            mNotificationView = new RemoteViews(getApplicationContext().getPackageName(),
                    R.layout.notification_info_view_singlesim);
        } else if(mActivePhones == 2){
            mNotificationView = new RemoteViews(getApplicationContext().getPackageName(),
                    R.layout.notification_info_view_dualsim);
        } else {
           this.stopSelf();
           return;
        }

        for (int i = 0; i < mSubscriptionInfos.size(); i++) {
            int mSlotIndex = mSubscriptionInfos.get(i).getSimSlotIndex();
            mPhoneStateListener[i] = getPhoneStateListener(mSlotIndex);
        }
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        registerReceiver(mAreaInfoReceiver, new IntentFilter(CB_AREA_INFO_RECEIVED_ACTION),
                CB_AREA_INFO_SENDER_PERMISSION, null);

        registerReceiver(mSpninfoReceiver, new IntentFilter(
                TelephonyIntents.SPN_STRINGS_UPDATED_ACTION));

        for (int i = 0; i < mNumPhones; i++) {
            mTelephonyManager.listen(mPhoneStateListener[i], PhoneStateListener.LISTEN_SERVICE_STATE
                    | PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
            Intent getLatestIntent = new Intent(GET_LATEST_CB_AREA_INFO_ACTION);
            getLatestIntent.putExtra(PhoneConstants.PHONE_KEY, i);
            sendBroadcastAsUser(getLatestIntent, UserHandle.ALL, CB_AREA_INFO_SENDER_PERMISSION);

            PopulateAreacodeInfo();
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        for (int i = 0; i < mNumPhones; i++) {
            mTelephonyManager.listen(mPhoneStateListener[i], PhoneStateListener.LISTEN_NONE);
        }
        unregisterReceiver(mAreaInfoReceiver);
        unregisterReceiver(mSpninfoReceiver);
    }

    private BroadcastReceiver mAreaInfoReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (CB_AREA_INFO_RECEIVED_ACTION.equals(action)) {
                Bundle extras = intent.getExtras();
                if (extras == null) {
                    return;
                }
                CellBroadcastMessage cbMessage = (CellBroadcastMessage) extras.get("message");
                if (cbMessage != null && cbMessage.getServiceCategory() == 50) {
                    int subId = cbMessage.getSubId();
                    int phoneId = SubscriptionManager.getSlotId(subId);
                    String latestAreaInfo = cbMessage.getMessageBody();
                    updateAreaInfo(latestAreaInfo, phoneId);
                }
            }
        }
    };

    private BroadcastReceiver mSpninfoReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            final int subId = intent.getIntExtra(PhoneConstants.SUBSCRIPTION_KEY, 0);
            int phoneId = getPhoneId(subId);

            mShowSpn[phoneId] = intent.getBooleanExtra(TelephonyIntents.EXTRA_SHOW_SPN, false);
            mSpn[phoneId] = intent.getStringExtra(TelephonyIntents.EXTRA_SPN);
            mShowPlmn[phoneId] = intent.getBooleanExtra(TelephonyIntents.EXTRA_SHOW_PLMN, false);
            mPlmn[phoneId] = intent.getStringExtra(TelephonyIntents.EXTRA_PLMN);
        }
    };

    private PhoneStateListener getPhoneStateListener(final int phoneId) {
        int subId = SubscriptionManager.getSubId(phoneId)[0];
        PhoneStateListener phoneStateListener = new PhoneStateListener(subId) {

            @Override
            public void onServiceStateChanged(ServiceState state) {
                mMSimServiceState[phoneId] = state;
                updateNetworkName(mShowSpn[phoneId], mSpn[phoneId], mShowPlmn[phoneId],
                    mPlmn[phoneId], phoneId);
            }
        };
        return phoneStateListener;
    }

    private void updateAreaInfo(String areaInfo, int phoneId) {
        if (areaInfo != null) {
            mMSimNetworkName[phoneId] = areaInfo;
            mMSimNetworkDetails[phoneId] = "";
        }
    }

    private void updateUI(int phoneId) {
        Notification notification = new Notification.Builder(getApplicationContext())
                .setSmallIcon(android.R.color.transparent)
                .setWhen(0).setOngoing(true).setDefaults(0)
                .setPriority(Notification.PRIORITY_MIN)
                .setContentTitle("").setContentText("")
                .setContent(mNotificationView).build();

        if (mActivePhones == SINGLE_SIM) {
            mNotificationView.setTextViewText(R.id.single_sim_spn, mMSimNetworkName[phoneId]);

            mNotificationView.setTextViewText(R.id.single_sim_plmn, mMSimNetworkDetails[phoneId]);
        } else {
            mNotificationView.setTextViewText(R.id.dualsim_spn_one, mMSimNetworkName[0]);

            mNotificationView.setTextViewText(R.id.dualsim_plmn_one, mMSimNetworkDetails[0]);

            mNotificationView.setTextViewText(R.id.dualsim_spn_two, mMSimNetworkName[1]);

            mNotificationView.setTextViewText(R.id.dualsim_plmn_two, mMSimNetworkDetails[1]);
        }

        NotificationManager notificationmanager = (NotificationManager) getApplicationContext()
                .getSystemService(NOTIFICATION_SERVICE);
        notificationmanager.notify(0, notification);
    }

    private void PopulateAreacodeInfo() {
        int index = 0;

        String[] areacodeArray = getResources().getStringArray(R.array.area_code);
        String[] areainfoArray = getResources().getStringArray(R.array.area_info);
        mAreaInfo.clear();

        if (areacodeArray.length != areainfoArray.length) {
            stopSelf();
        } else {
            for (index = 0; index < areacodeArray.length; index++) {
                mAreaInfo.put(areacodeArray[index], areainfoArray[index]);
            }
        }
    }

    private String updateLocationAreaCode(int phoneId) {
        String search_key = "";
        String location_info = "";
        if (mTelephonyManager.getPhoneType() == TelephonyManager.PHONE_TYPE_GSM) {
            final GsmCellLocation location = (GsmCellLocation) mTelephonyManager.getCellLocation();
            if (location != null && mMSimServiceState[phoneId].getState()
                        == ServiceState.STATE_IN_SERVICE) {
                search_key = Integer.toString(location.getLac() % 100);
                if(mAreaInfo != null && mAreaInfo.containsKey(search_key)) {
                    location_info = new StringBuilder().append(mAreaInfo.get(search_key))
                            .append(" ").append(search_key).toString();
                }
            } else {
                location_info = "";
            }
        }
        return location_info;
    }

    private void updateNetworkName(boolean showSpn, String spn, boolean showPlmn,
            String plmn, int phoneId) {
        StringBuilder str = new StringBuilder();
        boolean something = false;
        boolean isSamePlmnSpn = false;

        mMSimNetworkDetails[phoneId] = "";
        mMSimNetworkName[phoneId] ="";

        if (plmn != null && spn != null) {
            isSamePlmnSpn = plmn.equals(spn);
        }

        if(isAirplaneMode(phoneId)) {
            NotificationManager notificationmanager = (NotificationManager) getApplicationContext()
                    .getSystemService(Context.NOTIFICATION_SERVICE);
            notificationmanager.cancel(NOTIFICATION_ID);
            return;
        }

        if (!hasService(phoneId)) {
            if (!TextUtils.isEmpty(plmn)) {
                str.append(plmn);
                mMSimNetworkName[phoneId] = str.toString().toUpperCase();
                mMSimNetworkDetails[phoneId] = "";
                something = true;
            }
        }

        if (isSamePlmnSpn && showSpn && !something) {
            if (mMSimServiceState[phoneId] != null) {
                spn = appendLocationInfoToNetworkName(spn, mMSimServiceState[phoneId], phoneId);
            }
            str.append(spn);
            mMSimNetworkName[phoneId] = str.toString().toUpperCase();
            mMSimNetworkDetails[phoneId] = "";
        }

        if (!isSamePlmnSpn && plmn != null && spn != null && !something) {
            if (mMSimServiceState[phoneId] != null) {
                spn = appendLocationInfoToNetworkName(spn, mMSimServiceState[phoneId], phoneId);
            }
            str.append(spn);
            mMSimNetworkName[phoneId] = plmn.toUpperCase();;
            mMSimNetworkDetails[phoneId] = str.toString().toUpperCase();
        }
            updateUI(phoneId);
    }

    public String appendLocationInfoToNetworkName(String operator, ServiceState state,
           int phoneId) {
        String opeartorName = "";

        if (state.getState() == ServiceState.STATE_IN_SERVICE) {
            int networkType = state.getNetworkType();

            if (networkType != TelephonyManager.NETWORK_TYPE_UNKNOWN) {
                int networkClass = TelephonyManager.getNetworkClass(networkType);
                switch (networkClass) {
                case TelephonyManager.NETWORK_CLASS_2_G:
                    opeartorName = new StringBuilder().append(operator).append(" ")
                            .append(updateLocationAreaCode(phoneId)).toString();
                    break;
                case TelephonyManager.NETWORK_CLASS_3_G:
                    opeartorName = new StringBuilder().append(operator).append(" ")
                            .append(updateLocationAreaCode(phoneId)).toString();
                    break;
                case TelephonyManager.NETWORK_CLASS_4_G:
                    opeartorName = new StringBuilder().append(operator).append(" ")
                            .append(updateTrackingAreaCode(phoneId)).toString();
                default:
                    break;
                }
            }
        } else {
            opeartorName = operator;
        }
        return opeartorName;
    }

    private String updateTrackingAreaCode(int phoneId) {
        String search_key = "";
        String location_info = "";
        if (mTelephonyManager.getPhoneType() == TelephonyManager.PHONE_TYPE_GSM) {
            final GsmCellLocation location = (GsmCellLocation) mTelephonyManager.getCellLocation();
            if (location != null) {
                List<CellInfo> info = mTelephonyManager.getAllCellInfo();

                for (CellInfo ci : info) {
                    CellInfoLte cellInfoLte = null;
                    try {
                        cellInfoLte = (CellInfoLte) ci;
                        CellIdentityLte cellIdentityLte = cellInfoLte.getCellIdentity();

                        search_key = Integer.toString(cellIdentityLte.getTac() % 100);
                        if(mAreaInfo != null && mAreaInfo.containsKey(search_key)) {
                            location_info = new StringBuilder().append(mAreaInfo.get(search_key))
                                    .append(" ").append(search_key).toString();
                        }
                        return location_info;

                    } catch (ClassCastException e) {
                    }
                }
            }
        }
        return location_info;
    }

    private int getPhoneId(int subId) {
        int phoneId;
        phoneId = SubscriptionManager.getPhoneId(subId);
        return phoneId;
    }

    private boolean isAirplaneMode(int phoneId) {
        ServiceState ss = mMSimServiceState[phoneId];
        if (ss != null) {
            switch (ss.getState()) {
            case ServiceState.STATE_POWER_OFF:
                return true;
            default:
                return false;
            }
        } else {
            return false;
        }
    }

    private boolean hasService(int phoneId) {
        ServiceState ss = mMSimServiceState[phoneId];
        if (ss != null) {
            switch (ss.getState()) {
                case ServiceState.STATE_OUT_OF_SERVICE:
                    return false;
                default:
                    return true;
            }
        } else {
            return false;
        }
    }

}
