/*
 * Copyright (c) 2015 Qualcomm Technologies, Inc.
 * All Rights Reserved.
 * Confidential and Proprietary - Qualcomm Technologies, Inc.
 */

package com.qualcomm.qti.tefprimarycardcontroller;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.provider.Settings;
import android.telephony.Rlog;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.uicc.IccCardStatus.CardState;
import com.android.internal.telephony.uicc.UiccCard;
import com.android.internal.telephony.uicc.UiccController;

/**
 * Monitors ICC change events. In case, if all cards are available , notifies
 * to registered class.
 */
public class CardStateMonitor extends Handler {

    private static final String TAG = "CardStateMonitor";
    private static final int EVENT_ICC_CHANGED = 1;
    private static boolean mIsShutDownInProgress;
    private RegistrantList mAllCardsInfoAvailableRegistrants = new RegistrantList();
    private CardInfo[] mCards = new CardInfo[PrimarySubSelectionController.PHONE_COUNT];
    private Context mContext;
    private BroadcastReceiver receiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SHUTDOWN.equals(intent.getAction())
                    &&
                    !intent.getBooleanExtra(
                            Intent.EXTRA_SHUTDOWN_USERSPACE_ONLY,
                            false)) {
                logd("ACTION_SHUTDOWN Received");
                mIsShutDownInProgress = true;
            }
        }
    };

    public CardStateMonitor(Context context) {
        mContext = context;
        for (int index = 0; index < PrimarySubSelectionController.PHONE_COUNT; index++) {
            mCards[index] = new CardInfo();
        }
        UiccController.getInstance().registerForIccChanged(this,
                EVENT_ICC_CHANGED, null);
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SHUTDOWN);
        mContext.registerReceiver(receiver, filter);
    }

    public static UiccCard getUiccCard(int phoneId) {
        UiccCard uiccCard = null;
        Phone[] phones = PhoneFactory.getPhones();
        Phone phone = phones[phoneId];
        if (mIsShutDownInProgress
                || Settings.Global.getInt(
                        phone.getContext().getContentResolver(),
                        Settings.Global.AIRPLANE_MODE_ON,
                        0) == 1) {
            return null;
        }
        if (phone.mCi.getRadioState().isOn()) {
            uiccCard = UiccController.getInstance()
                    .getUiccCard(phoneId);
        }
        return uiccCard;
    }

    static void logd(String msg) {
        if (PrimarySubSelectionController.DEBUG) {
            Rlog.d(TAG, msg);
        }
    }

    public void dispose() {
        mContext.unregisterReceiver(receiver);
        UiccController.getInstance()
                .unregisterForIccChanged(this);
    }

    public void registerAllCardsInfoAvailable(Handler handler,
            int what, Object obj) {
        Registrant r = new Registrant(handler, what, obj);
        synchronized (mAllCardsInfoAvailableRegistrants) {
            mAllCardsInfoAvailableRegistrants.add(r);
            for (int index = 0; index < PrimarySubSelectionController.PHONE_COUNT; index++) {
                if (!mCards[index].isCardInfoAvailable()) {
                    return;
                }
            }
            r.notifyRegistrant();
        }
    }

    public void unregisterAllCardsInfoAvailable(
            Handler handler) {
        synchronized (mAllCardsInfoAvailableRegistrants) {
            mAllCardsInfoAvailableRegistrants.remove(handler);
        }
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case EVENT_ICC_CHANGED:
                logd("on EVENT_ICC_CHANGED");
                onIccChanged((AsyncResult) msg.obj);
                break;
        }
    }

    private void onIccChanged(AsyncResult iccChangedResult) {
        if (iccChangedResult == null
                || iccChangedResult.result == null) {
            for (int index = 0; index < PrimarySubSelectionController.PHONE_COUNT; index++) {
                updateCardState(index);
            }
        } else {
            updateCardState((Integer) iccChangedResult.result);
        }
    }

    private void updateCardState(int phoneId) {
        UiccCard uiccCard = getUiccCard(phoneId);
        logd("ICC changed on " + phoneId + ", state is "
                + (uiccCard == null ? "NULL"
                        : uiccCard.getCardState()));
        notifyCardAvailableIfNeed(phoneId, uiccCard);
    }

    private void notifyCardAvailableIfNeed(int phoneId,
            UiccCard uiccCard) {
        if (uiccCard != null) {

            if (!mCards[phoneId].isCardStateEquals(
                    uiccCard.getCardState().toString())) {
                mCards[phoneId].mCardState = uiccCard
                        .getCardState()
                        .toString();
                notifyAllCardsAvailableIfNeed();
            }
        } else {
            // card is null, means card info is unavailable or the device is in
            // APM, need to reset all card info, otherwise no change will be
            // detected when card info is available again!
            mCards[phoneId].reset();
        }
    }

    private void notifyAllCardsAvailableIfNeed() {
        for (int index = 0; index < PrimarySubSelectionController.PHONE_COUNT; index++) {
            if (!mCards[index].isCardInfoAvailable()) {
                return;
            }
        }
        mAllCardsInfoAvailableRegistrants.notifyRegistrants();
    }

    static class CardInfo {
        String mCardState;

        boolean isCardStateEquals(String cardState) {
            return TextUtils.equals(mCardState, cardState);
        }

        boolean isCardInfoAvailable() {
            return !isCardStateEquals(null)
                    && isCardStateEquals(
                            CardState.CARDSTATE_PRESENT
                                    .toString());

        }

        boolean isCardPresent() {
            return !isCardStateEquals(null) && isCardStateEquals(
                    CardState.CARDSTATE_PRESENT.toString());
        }

        private void reset() {
            mCardState = null;
        }
    }
}
