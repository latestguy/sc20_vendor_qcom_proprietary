/*
 * Copyright (c) 2015 Qualcomm Technologies, Inc.
 * All Rights Reserved.
 * Confidential and Proprietary - Qualcomm Technologies, Inc.
 */

package com.qualcomm.qti.tefprimarycardcontroller;

import android.content.Context;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.telephony.Rlog;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.internal.telephony.Phone;

import java.util.ArrayList;
import java.util.List;

/**
 * Responsible for setting primary slot. Primary slot set
 * by modification of network modes.
 *
 * For secondary slot, setting network mode to "GSM only".
 * For Primary slot, it will any of higher network modes.
 *
 * For above changes to work, flexmap property "persist.radio.flexmap_type"
 * has to be set to "nw_mode"
 */
public class PrefNetworkRequest extends SyncQueue.SyncRequest {
    private final String TAG = "PrefNetworkRequest";

    private static final SyncQueue sSyncQueue = new SyncQueue();
    private static final int EVENT_SET_PREF_NETWORK_DONE = 1;
    private static final int EVENT_GET_PREF_NETWORK_DONE = 2;
    private static final int EVENT_START_REQUEST = 3;
    private final Message mCallback;
    private final List<PrefNetworkSetCommand> mCommands;
    private final Context mContext;
    private Handler mHandler = new Handler(
            Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_SET_PREF_NETWORK_DONE:
                    handleSetPreferredNetwork(msg);
                    break;
                case EVENT_GET_PREF_NETWORK_DONE:
                    handleGetPreferredNetwork(msg);
                    break;
                case EVENT_START_REQUEST:
                    request((Integer) msg.obj);
                    break;
            }
        }
    };

    public PrefNetworkRequest(Context context, int slot,
            int networkMode, Message callback) {
        super(sSyncQueue);
        mContext = context;
        mCallback = callback;
        mCommands = new ArrayList<PrefNetworkSetCommand>();
        // This request comes here to set N/W mode on primary card.
        // Send GSM N/W mode on all other phones so that framework can initiate
        // flex map to link primary stack.
        if (networkMode != Phone.NT_MODE_GSM_ONLY) {
            for (int index = 0; index < PrimarySubSelectionController.PHONE_COUNT; index++) {
                PrimarySubSelectionController subController = PrimarySubSelectionController
                        .getInstance();
                if (subController != null) {
                    if (index != slot) {
                        mCommands.add(new PrefNetworkSetCommand(
                                index, Phone.NT_MODE_GSM_ONLY));
                    }
                }
            }
        }
        if (slot >= 0
                && slot < PrimarySubSelectionController.PHONE_COUNT) {
            mCommands.add(new PrefNetworkSetCommand(slot,
                    networkMode));
        }
    }

    private void request(final int index) {
        final PrefNetworkSetCommand command = mCommands
                .get(index);
        logd("save network mode " + command.getPrefNetwork()
                + " for slot" + command.getSlot()
                + " to DB first");
        savePrefNetworkInDb(command.getSlot(),
                command.getPrefNetwork());

        logd("set " + command.getPrefNetwork() + " for slot"
                + command.getSlot());
        PrimarySubSelectionController
                .getInstance().mPhones[command.getSlot()]
                        .setPreferredNetworkType(
                                command.getPrefNetwork(),
                                mHandler.obtainMessage(
                                        EVENT_SET_PREF_NETWORK_DONE,
                                        index));
    }

    private void handleGetPreferredNetwork(Message msg) {
        int modemNetworkMode = -1;
        AsyncResult ar = (AsyncResult) msg.obj;
        int index = (Integer) ar.userObj;
        PrefNetworkSetCommand command = mCommands.get(index);
        if (ar.exception == null) {
            modemNetworkMode = ((int[]) ar.result)[0];
            savePrefNetworkInDb(command.getSlot(),
                    modemNetworkMode);
        }
        logd(" get perferred N/W mode = [" + command.getSlot()
                + "] = "
                + modemNetworkMode + " done, " + ar.exception);
        if (++index < mCommands.size()) {
            request(index);
        } else {
            response(mCallback);
            end();
        }
    }

    private void handleSetPreferredNetwork(Message msg) {
        AsyncResult ar = (AsyncResult) msg.obj;
        int index = (Integer) ar.userObj;
        PrefNetworkSetCommand command = mCommands.get(index);
        logd("set " + command.getPrefNetwork() + " for slot"
                + command.getSlot() + " done, "
                + ar.exception);
        if (ar.exception != null) {
            PrimarySubSelectionController
                    .getInstance().mPhones[command.getSlot()]
                            .getPreferredNetworkType(
                                    mHandler.obtainMessage(
                                            EVENT_GET_PREF_NETWORK_DONE,
                                            index));
            return;
        }
        if (++index < mCommands.size()) {
            request(index);
        } else {
            response(mCallback);
            end();
        }
    }

    protected void start() {
        if (mCommands.isEmpty()) {
            logd("no command sent");
            response(mCallback);
            end();
        } else {
            PrefNetworkSetCommand command = mCommands
                    .get(mCommands.size() - 1);
            logd("try to set network=" + command.getPrefNetwork()
                    + " on slot" + command.getSlot());
            mHandler.obtainMessage(EVENT_START_REQUEST, 0)
                    .sendToTarget();
        }
    }

    private void savePrefNetworkInDb(int slotId,
            int networkMode) {
        int[] subId = SubscriptionManager.getSubId(slotId);

        android.provider.Settings.Global.putInt(
                mContext.getContentResolver(),
                android.provider.Settings.Global.PREFERRED_NETWORK_MODE
                        + subId[0],
                networkMode);
        TelephonyManager.putIntAtIndex(
                mContext.getContentResolver(),
                android.provider.Settings.Global.PREFERRED_NETWORK_MODE,
                slotId, networkMode);
    }

    private void response(Message callback) {
        if (callback == null) {
            return;
        }
        if (callback.getTarget() != null) {
            callback.sendToTarget();
        } else {
            Log.w(TAG,
                    "can't response the result, replyTo and target are all null!");
        }
    }

    private void logd(String msg) {
        Rlog.d(TAG, msg);
    }

    private class PrefNetworkSetCommand {
        private final int mSlot;
        private final int mPrefNetwork;

        private PrefNetworkSetCommand(int slot,
                int prefNetwork) {
            mSlot = slot;
            mPrefNetwork = prefNetwork;
        }

        public int getSlot() {
            return mSlot;
        }

        public int getPrefNetwork() {
            return mPrefNetwork;
        }

    }
}
