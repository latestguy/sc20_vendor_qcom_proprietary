/*
 * Copyright (c) 2015 Qualcomm Technologies, Inc.
 * All Rights Reserved.
 * Confidential and Proprietary - Qualcomm Technologies, Inc.
 */

package com.qualcomm.qti.tefprimarycardcontroller;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.Rlog;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.view.WindowManager;

import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.SubscriptionController;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.uicc.IccCardStatus.CardState;
import com.android.internal.telephony.uicc.UiccCard;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Responsible for overall functionality
 *
 * 1> Monitors for card state, sim state and radio capability changes
 * 2> In case of SIM change on bootup or hotswap. Checks if
 *    newly inserted SIM is any of preferred SIM's
 *    defined in config file. If preferred SIM, then sets
 *    primary slot, default data and voice subscription
 *    to preferred SIM slot. If operations are successful
 *    user will be notified of change.
 * 3> Above operations are performed only if radio capability
 *    is available and if SIM's are loaded [notified by SIM state
 *    change].
 * 4> In case preferred/non-preferred SIM's are available in both
 *    slots. No operations will be performed.
 * 5> Monitors for DDS change and primary slot changes by user.
 *    In case of DDS change, primary slot will be set to current
 *    DDS slot. In case of Primary slot change via network mode
 *    set DDS to current Primary slot.
 */
public class PrimarySubSelectionController extends Handler
        implements OnClickListener,
        OnDismissListener {
    public static final String CONFIG_LTE_SUB_SELECT_MODE = "config_lte_sub_select_mode";
    public static final String CONFIG_PRIMARY_SUB_SETABLE = "config_primary_sub_setable";
    public static final String CONFIG_CURRENT_PRIMARY_SUB = "config_current_primary_sub";
    public static final boolean DEBUG = true;
    static final String PRIMARY_CARD_PROPERTY_NAME = "persist.radio.primarycard.tef";
    static final int PHONE_COUNT = TelephonyManager.getDefault()
            .getPhoneCount();
    private static final int PRIMARY_STACK_MODEMID = 0;
    private static final int INVALID = -1;
    private static final int MSG_ALL_CARDS_AVAILABLE = 1;
    private static final int MSG_CONFIG_LTE_DONE = 2;
    private static final int MSG_RADIO_CAPS_READY = 3;
    private static PrimarySubSelectionController sInstance;
    // These are the list of  possible values that
    // IExtTelephony.getCurrentUiccCardProvisioningStatus() can return
    private final int NOT_PROVISIONED = 0;
    private final int PROVISIONED = 1;
    private final String ACTION_RADIO_CAPABILITY_UPDATED =
            "org.codeaurora.intent.action.ACTION_RADIO_CAPABILITY_UPDATED";
    private final String ACTION_UICC_MANUAL_PROVISION_STATUS_CHANGED =
            "org.codeaurora.intent.action.ACTION_UICC_MANUAL_PROVISION_STATUS_CHANGED";
    private final String EXTRA_NEW_PROVISION_STATE = "newProvisionState";
    private final Context mContext;
    Phone[] mPhones;
    CardStateMonitor mCardStateMonitor;
    private String TAG = "PrimarySubSelectionController";
    private boolean mAllCardsAbsent = true;
    private boolean mCardChanged = false;
    private boolean mNeedHandleModemReadyEvent = false;
    private boolean mSetPrimarySubOnSimLoad = false;
    private boolean mRestoreDdsToPrimarySub = false;
    private boolean mRadioCapabilityAvailable = false;
    private boolean mSetPreferredNetworkOnRadioCapability = false;
    private boolean[] mIccLoaded;
    private boolean[] mSimProvisioned;
    private AlertDialog mSIMChangedDialog = null;
    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            logd("Recieved intent " + action);
            if (TelephonyIntents.ACTION_SIM_STATE_CHANGED
                    .equals(action)) {
                final int slot = intent
                        .getIntExtra(PhoneConstants.SLOT_KEY, 0);
                final String stateExtra = intent
                        .getStringExtra(
                                IccCardConstants.INTENT_KEY_ICC_STATE);
                mIccLoaded[slot] = false;
                if (IccCardConstants.INTENT_VALUE_ICC_LOADED
                        .equals(stateExtra)) {
                    mIccLoaded[slot] = true;
                    int primarySlot = getPrimarySlot();
                    int currentDds = SubscriptionManager
                            .getDefaultDataSubscriptionId();
                    int currentDdsSlotId = SubscriptionManager
                            .getSlotId(currentDds);
                    int currentPrimaryStackPhoneId = getPrimaryStackPhoneId();
                    logd("ACTION_SIM_STATE_CHANGED current default dds :"
                            + currentDds
                            + ", primary slot :" + primarySlot
                            + ", currentDdsSlotId :" +
                            currentDdsSlotId
                            + ", currentPrimaryStackPhoneId :" +
                            currentPrimaryStackPhoneId);
                    if (currentDdsSlotId == primarySlot) {
                        mRestoreDdsToPrimarySub = false;
                    }

                    logd("ACTION_SIM_STATE_CHANGED mRestoreDdsToPrimarySub: "
                            + mRestoreDdsToPrimarySub);
                    if (mRestoreDdsToPrimarySub) {
                        if (slot == primarySlot) {
                            int subId = SubscriptionManager
                                    .getSubId(slot)[0];
                            SubscriptionManager.from(context)
                                    .setDefaultDataSubId(subId);
                            setOutgoingPhoneAccount(primarySlot);
                            mRestoreDdsToPrimarySub = false;
                            logd("restore dds to primary card, dds["
                                    + slot + "] = " + subId);
                        }
                    } else if ((currentPrimaryStackPhoneId != currentDdsSlotId)
                            &&
                            (currentPrimaryStackPhoneId == slot)) {
                        logd("Current primary slot and data are different. "
                                +
                                "Setting data slot  :" + slot);
                        int subId = SubscriptionManager
                                .getSubId(slot)[0];
                        SubscriptionManager.from(context)
                                .setDefaultDataSubId(subId);
                    } else if (mSetPrimarySubOnSimLoad && isMultiSimLoaded()) {
                        // If primary SIM slot setup is pending on activation
                        // due to SIM not loaded. Set it up over here.
                        logd("All SIM's loaded and activated set primary sub");
                        setPrimarySub();
                        mSetPrimarySubOnSimLoad = false;
                    }
                }
                logd("ACTION_SIM_STATE_CHANGED intent received SIM STATE is "
                        + stateExtra
                        + ", mIccLoaded[" + slot + "] = "
                        + mIccLoaded[slot]);
            } else if (Intent.ACTION_LOCALE_CHANGED
                    .equals(action)) {
                logd("Recieved EVENT ACTION_LOCALE_CHANGED");
                if (mSIMChangedDialog != null
                        && mSIMChangedDialog.isShowing()) {
                    logd("Update SIMChanged dialog");
                    mSIMChangedDialog.dismiss();
                    alertSIMChangedOnPrimaryConfig();
                }
            } else if (ACTION_RADIO_CAPABILITY_UPDATED
                    .equals(action)) {
                sendMessage(obtainMessage(MSG_RADIO_CAPS_READY));

            } else if (TelephonyIntents.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED
                    .equals(action)) {
                logd("Received ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED");
                int subId = intent.getIntExtra(
                        PhoneConstants.SUBSCRIPTION_KEY,
                        INVALID);
                verifyAndSetPreferredNetworkOnDDSChange(subId);
            } else if (ACTION_UICC_MANUAL_PROVISION_STATUS_CHANGED
                    .equals(action)) {
                logd("Received ACTION_UICC_MANUAL_PROVISION_STATUS_CHANGED");

                // Handle SIM slot deactivation & activation usecase.
                int slotId = intent.getIntExtra(
                        PhoneConstants.PHONE_KEY,
                        SubscriptionManager.INVALID_SUBSCRIPTION_ID);
                int newProvisionedState = intent.getIntExtra(
                        EXTRA_NEW_PROVISION_STATE,
                        NOT_PROVISIONED);
                logd("Received ACTION_UICC_MANUAL_PROVISION_STATUS_CHANGED on slotId: "
                        + slotId + " new sub state "
                        + newProvisionedState);
                if (slotId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {

                    if (newProvisionedState == PROVISIONED) {
                        mSimProvisioned[slotId] = true;
                    } else {
                        mSimProvisioned[slotId] = false;

                        // In case of SIM deactivation clear up previously
                        // saved mccmnc value for slot. This is needed for
                        // setting up primary sub next time when slot is
                        // activated
                        PreferenceManager
                                .getDefaultSharedPreferences(
                                        mContext)
                                .edit()
                                .remove(PhoneConstants.SUBSCRIPTION_KEY
                                        + slotId)
                                .commit();
                    }

                    if (isMultiSimActivated()
                            && isMultiSimLoaded()) {
                        logd("All SIM's activated and loaded, set primary sub");
                        setPrimarySub();
                    } else {
                        logd("set primary sub on SIM load");
                        // Defer primary SIM slot setup until SIM is loaded.
                        mSetPrimarySubOnSimLoad = true;
                    }

                }

            }
        }
    };

    private PrimarySubSelectionController(Context context) {
        mContext = context.getApplicationContext();
        mPhones = PhoneFactory.getPhones();

        Rlog.d(TAG, " in constructor, context =  " + mContext);

        mCardStateMonitor = new CardStateMonitor(mContext);
        mCardStateMonitor.registerAllCardsInfoAvailable(this,
                MSG_ALL_CARDS_AVAILABLE, null);

        mIccLoaded = new boolean[PHONE_COUNT];
        for (int i = 0; i < PHONE_COUNT; i++) {
            mIccLoaded[i] = false;
        }

        mSimProvisioned = new boolean[PHONE_COUNT];
        for (int i = 0; i < PHONE_COUNT; i++) {
            // SIM's are assumed to be provisioned
            // and activated by default.
            mSimProvisioned[i] = true;
        }

        IntentFilter intentFilter = new IntentFilter(
                TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        intentFilter.addAction(
                TelephonyIntents.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED);
        intentFilter.addAction(Intent.ACTION_LOCALE_CHANGED);
        intentFilter.addAction(ACTION_RADIO_CAPABILITY_UPDATED);
        intentFilter.addAction(ACTION_UICC_MANUAL_PROVISION_STATUS_CHANGED);
        mContext.registerReceiver(mReceiver, intentFilter);
    }

    public static void init(Context context) {
        synchronized (PrimarySubSelectionController.class) {
            if (sInstance == null
                    && isPrimaryCardFeatureEnabled()) {
                sInstance = new PrimarySubSelectionController(
                        context);
            }
        }
    }

    public static PrimarySubSelectionController getInstance() {
        synchronized (PrimarySubSelectionController.class) {
            if (sInstance == null) {
                throw new RuntimeException(
                        "PrimarySubSelectionController was not initialize!");
            }
            return sInstance;
        }
    }

    // Primary card feature would be enabled when system property
    // PRIMARY_CARD_PROPERTY_NAME set to true on MSIM devices.
    public static boolean isPrimaryCardFeatureEnabled() {
        return SystemProperties
                .getBoolean(PRIMARY_CARD_PROPERTY_NAME, false)
                && (PHONE_COUNT > 1);
    }

    private int getPrimaryStackPhoneId() {
        String modemUuId = null;
        Phone phone = null;
        int primayStackPhoneId = INVALID;

        for (int i = 0; i < TelephonyManager.getDefault()
                .getPhoneCount(); i++) {

            phone = PhoneFactory.getPhone(i);
            if (phone == null)
                continue;

            logd("Logical Modem id: " + phone.getModemUuId()
                    + " phoneId: " + i);
            modemUuId = phone.getModemUuId();
            if ((modemUuId == null) || (modemUuId.length() <= 0)
                    ||
                    modemUuId.isEmpty()) {
                continue;
            }
            // Select the phone id based on modemUuid
            // if modemUuid is 0 for any phone instance, primary stack is mapped
            // to it so return the phone id as the primary stack phone id.
            if (Integer.parseInt(
                    modemUuId) == PRIMARY_STACK_MODEMID) {
                primayStackPhoneId = i;
                logd("Primary Stack phone id: "
                        + primayStackPhoneId + " selected");
                break;
            }
        }

        // never return INVALID
        if (primayStackPhoneId == INVALID) {
            logd("Returning default phone id");
            primayStackPhoneId = 0;
        }

        return primayStackPhoneId;
    }

    private boolean isMultiSimLoaded() {
        for (int i = 0; i < PHONE_COUNT; i++) {
            if (mIccLoaded[i] == false) {
                logd("ICC not loaded for slot :" + i);
                return false;
            }
        }
        logd("ICC loaded for all SIM's");
        return true;
    }

    private boolean isMultiSimActivated() {
        for (int i = 0; i < PHONE_COUNT; i++) {
            if (mSimProvisioned[i] == false) {
                logd("SIM not provisioned for slot :" + i);
                return false;
            }
        }
        logd("All SIM's provisioned");
        return true;
    }

    protected boolean isCardsInfoChanged(int phoneId) {
        int mccmnc = getMccMncForId(phoneId);
        int mccmncInSP = PreferenceManager
                .getDefaultSharedPreferences(mContext).getInt(
                        PhoneConstants.SUBSCRIPTION_KEY
                                + phoneId,
                        -1);
        logd(" phoneId " + phoneId + " icc id = " + mccmnc
                + ", icc id in sp=" + mccmncInSP);
        return (mccmnc > 0) && (mccmnc != mccmncInSP);
    }

    private void loadStates() {
        mCardChanged = false;
        for (int i = 0; i < PHONE_COUNT; i++) {
            if (isCardsInfoChanged(i)) {
                mCardChanged = true;
            }
        }
        mAllCardsAbsent = isAllCardsAbsent();
    }

    private void saveSubscriptions() {
        for (int i = 0; i < PHONE_COUNT; i++) {
            int mccmnc = getMccMncForId(i);
            if (mccmnc > 0) {
                logd("save subscription on sub " + i
                        + " , mccmnc :" + mccmnc);
                PreferenceManager
                        .getDefaultSharedPreferences(mContext)
                        .edit()
                        .putInt(PhoneConstants.SUBSCRIPTION_KEY
                                + i, mccmnc)
                        .commit();
            }
        }
    }

    public void onClick(DialogInterface dialog, int which) {
        switch (which) {
            case DialogInterface.BUTTON_NEGATIVE:
                // do nothing.
                break;
            case DialogInterface.BUTTON_POSITIVE:
                // call dual settings;
                Intent intent = new Intent(
                        "com.android.settings.sim.SIM_SUB_INFO_SETTINGS");
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                try {
                    mContext.startActivity(intent);
                } catch (ActivityNotFoundException e) {
                    logd("can not start activity " + intent);
                }
                break;
        }
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        if (mSIMChangedDialog == (AlertDialog) dialog) {
            mSIMChangedDialog = null;
        }
    }

    protected void alertSIMChangedOnPrimaryConfig() {

        if (mSIMChangedDialog != null
                && mSIMChangedDialog.isShowing()) {
            mSIMChangedDialog.dismiss();
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(
                mContext)
                        .setMessage(
                                R.string.auto_config_complete_msg)
                        .setNegativeButton(R.string.close, this);

        mSIMChangedDialog = builder.create();
        mSIMChangedDialog.getWindow().setType(
                WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG);
        mSIMChangedDialog.setOnDismissListener(this);
        mSIMChangedDialog.show();
    }

    private void configPrimaryLteSub() {
        int slotIndex = SubscriptionManager.INVALID_SIM_SLOT_INDEX;
        if (!isPrimarySetable()) {
            logd("primary is not setable in any sub!");
            return;
        } else {
            int prefPrimarySlot = getPrefPrimarySlot();
            int primarySlot = getPrimarySlot();
            logd("pref primary slotIndex = " + prefPrimarySlot
                    + " curr primary slot = " +
                    primarySlot + " mCardChanged = "
                    + mCardChanged);
            if (prefPrimarySlot == SubscriptionManager.INVALID_SIM_SLOT_INDEX
                    &&
                    (mCardChanged
                            || primarySlot == SubscriptionManager.INVALID_SIM_SLOT_INDEX)) {
                // No change. Currently both cards have same priority
                return;
            } else if (prefPrimarySlot != SubscriptionManager.INVALID_SIM_SLOT_INDEX
                    &&
                    (mCardChanged
                            || primarySlot != prefPrimarySlot)) {
                slotIndex = prefPrimarySlot;
            }
            if (slotIndex != SubscriptionManager.INVALID_SIM_SLOT_INDEX
                    &&
                    primarySlot == slotIndex && !mCardChanged) {
                logd("primary sub and network mode are all correct, just notify");
                obtainMessage(MSG_CONFIG_LTE_DONE, primarySlot,
                        -1).sendToTarget();
                return;
            } else if (slotIndex == SubscriptionManager.INVALID_SIM_SLOT_INDEX) {
                logd("card not changed and primary sub is correct, do nothing");
                return;
            }
        }
        setPreferredNetwork(slotIndex, obtainMessage(
                MSG_CONFIG_LTE_DONE, slotIndex, -1));
    }

    private boolean trySetDdsToPrimarySub() {
        boolean set = false;
        int primarySlot = getPrimarySlot();

        if (primarySlot != SubscriptionManager.INVALID_SIM_SLOT_INDEX) {
            int currentDds = SubscriptionManager
                    .getDefaultDataSubscriptionId();
            int currentDdsSlotId = SubscriptionManager
                    .getSlotId(currentDds);
            int mccmnc = getMccMncForId(primarySlot);

            logd("trySetDdsToPrimarySub primary Slot "
                    + primarySlot + ", currentDds = "
                    + currentDds
                    + ", mIccLoaded[" + primarySlot
                    + "] =" + mIccLoaded[primarySlot]
                    + "Icc Id = " + mccmnc);
            if ((mIccLoaded[primarySlot] || (mccmnc > 0))
                    && currentDdsSlotId != primarySlot) {
                int subId = SubscriptionManager
                        .getSubId(primarySlot)[0];
                SubscriptionManager.from(mContext)
                        .setDefaultDataSubId(subId);
                setOutgoingPhoneAccount(primarySlot);
                mRestoreDdsToPrimarySub = false;
                set = true;
            }
        }
        return set;
    }

    private void setOutgoingPhoneAccount(int phoneId) {
        final TelecomManager telecomManager = TelecomManager
                .from(mContext);
        final List<PhoneAccountHandle> phoneAccountsList = telecomManager
                .getCallCapablePhoneAccounts();
        telecomManager.setUserSelectedOutgoingPhoneAccount(
                phoneAccountsList.get(phoneId));
    }

    protected void onConfigLteDone(Message msg) {
        boolean isManualConfigMode = isManualConfigMode();
        int slotId = msg.arg1;
        if (slotId != SubscriptionManager.INVALID_SIM_SLOT_INDEX) {
            logd("onConfigLteDone, n/w mode success, primary card = "
                    + slotId);
            savePrimarySlot(slotId);
        }
        if (!trySetDdsToPrimarySub()) {
            mRestoreDdsToPrimarySub = true;
        }
        logd("onConfigLteDone isManualConfigMode "
                + isManualConfigMode);
        if (isAutoConfigMode()) {
            alertSIMChangedOnPrimaryConfig();
        }
    }

    private void setPrimarySub() {
        if (!mRadioCapabilityAvailable) {
            logd("Radio capability not available, do not set primary sub.");
            mNeedHandleModemReadyEvent = true;
            return;
        }

        // reset states and load again by new card info
        loadStates();
        if (isPrimaryCardFeatureEnabled()) {
            logd("primary sub config feature is enabled!");
            configPrimaryLteSub();
        }
        saveSubscriptions();
        saveLteSubSelectMode();
        savePrimarySetable();
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case MSG_RADIO_CAPS_READY:
                logd("on EVENT MSG_RADIO_CAPS_READY "
                        + mNeedHandleModemReadyEvent);
                mRadioCapabilityAvailable = true;
                if (mNeedHandleModemReadyEvent) {
                    setPrimarySub();
                    mNeedHandleModemReadyEvent = false;
                } else if (mSetPreferredNetworkOnRadioCapability) {
                    int currentDds = SubscriptionManager
                            .getDefaultDataSubscriptionId();
                    verifyAndSetPreferredNetworkOnDDSChange(
                            currentDds);
                    mSetPreferredNetworkOnRadioCapability = false;
                }
                break;
            case MSG_ALL_CARDS_AVAILABLE:
                logd("on EVENT MSG_ALL_CARDS_AVAILABLE");
                setPrimarySub();
                break;
            case MSG_CONFIG_LTE_DONE:
                logd("on EVENT MSG_CONFIG_LTE_DONE");
                onConfigLteDone(msg);
                break;
        }
    }

    public void setPreferredNetwork(int slotIndex, Message msg) {
        int network = -1;

        if (slotIndex >= 0 && slotIndex < PHONE_COUNT) {
            int mccmnc = getMccMncForId(slotIndex);
            UiccCard uiccCard = CardStateMonitor
                    .getUiccCard(slotIndex);

            network = PrimaryCardConfigList.getInstance(mContext)
                    .getPrimaryCardPrefNetwork(mccmnc, uiccCard);
            if (network == -1) {
                logd("network mode is -1 , can not set primary card for "
                        + slotIndex);
                return;
            }
        }

        logd("set primary card for slot = " + slotIndex
                + ", network=" + network);
        new PrefNetworkRequest(mContext, slotIndex, network, msg)
                .loop();
    }

    /**
     * On DDS change, verifies if primary slot is mapped to DDS. If different, primary slot will be
     * set to current DDS slot.
     */
    public void verifyAndSetPreferredNetworkOnDDSChange(
            int subId) {
        if (subId < 0) {
            logd("SubscriptionId is invalid");
            return;
        }

        int currentDdsSlotId = SubscriptionManager
                .getSlotId(subId);
        int currentPrimaryStackPhoneId = getPrimaryStackPhoneId();

        if (currentDdsSlotId == currentPrimaryStackPhoneId) {
            logd("Data subscription maps to primary slot");
            return;
        } else {

            if (mRadioCapabilityAvailable) {
                int network = mContext.getResources().getInteger(
                        R.integer.config_default_preferred_network);
                if (network < 0) {
                    return;
                }
                logd("set primary for slot = " + currentDdsSlotId
                        + ", network=" + network);
                new PrefNetworkRequest(mContext,
                        currentDdsSlotId, network, null).loop();
            } else {
                mSetPreferredNetworkOnRadioCapability = true;
            }
        }
    }

    // Retrieve priorities of current inserted SIM cards based on MCCMNC.
    private Map<Integer, Integer> retrieveCurrentCardsPriorities() {
        Map<Integer, Integer> priorities = new HashMap<Integer, Integer>();
        for (int index = 0; index < PHONE_COUNT; index++) {
            int mccmnc = getMccMncForId(index);
            UiccCard uiccCard = CardStateMonitor
                    .getUiccCard(index);
            priorities.put(index,
                    PrimaryCardConfigList.getInstance(mContext)
                            .getPrimaryCardPriority(mccmnc,
                                    uiccCard));
        }
        return priorities;
    }

    private int getPriority(Map<Integer, Integer> priorities,
            Integer higherPriority) {
        int count = getCount(priorities, higherPriority);
        if (count == 1) {
            return getKey(priorities, higherPriority);
        } else if (count > 1) {
            return PrimaryCardConfigList.INVALID_PRIMARY_CARD_PRIORITY;
        } else if (higherPriority > 0) {
            return getPriority(priorities, --higherPriority);
        } else {
            return PrimaryCardConfigList.INVALID_PRIMARY_CARD_PRIORITY;
        }
    }

    private int getCount(Map<Integer, Integer> priorities,
            int priority) {
        int count = 0;
        for (Integer key : priorities.keySet()) {
            if (priorities.get(key) == priority) {
                count++;
            }
        }
        return count;
    }

    private Integer getKey(Map<Integer, Integer> map,
            int priority) {
        for (Integer key : map.keySet()) {
            if (map.get(key) == priority) {
                return key;
            }
        }
        return null;
    }

    // This method return preferred primary slot as
    // 1. SlotId in which the highest priority Card inserted.
    // 2. '-1' if two SIM cards from both slots has same priority.
    public int getPrefPrimarySlot() {
        return getPriority(retrieveCurrentCardsPriorities(),
                PrimaryCardConfigList.getInstance(mContext)
                        .getHighestPriority());
    }

    public int getMccMncForId(int cardIndex) {

        String mccmnc = TelephonyManager.getDefault()
                .getSimOperatorNumericForPhone(cardIndex);

        if (TextUtils.isEmpty(mccmnc)) {
            logd("MCCNNC is invalid for cardIndex :"
                    + cardIndex);
            return -1;
        } else {
            return Integer.parseInt(mccmnc);
        }

    }

    public boolean isPrimarySetable() {
        Map<Integer, Integer> priorities = retrieveCurrentCardsPriorities();
        int unsetableCount = getCount(priorities,
                PrimaryCardConfigList.INVALID_PRIMARY_CARD_PRIORITY);
        return unsetableCount < priorities.size();
    }

    private boolean isAllCardsAbsent() {
        for (int i = 0; i < PHONE_COUNT; i++) {
            UiccCard uiccCard = CardStateMonitor.getUiccCard(i);
            if (uiccCard == null || uiccCard
                    .getCardState() != CardState.CARDSTATE_ABSENT) {
                logd("card state on sub" + i + " not absent");
                return false;
            }
        }
        logd("all cards absent");
        return true;
    }

    private void saveLteSubSelectMode() {
        Settings.Global.putInt(
                mContext.getContentResolver(),
                CONFIG_LTE_SUB_SELECT_MODE,
                isManualConfigMode() ? 0 : 1);
    }

    private void savePrimarySetable() {
        Settings.Global.putInt(mContext.getContentResolver(),
                CONFIG_PRIMARY_SUB_SETABLE,
                isPrimarySetable() ? 1 : 0);
    }

    // This method returns current configured primary slot
    public int getPrimarySlot() {
        int slotId = Settings.Global.getInt(
                mContext.getContentResolver(),
                CONFIG_CURRENT_PRIMARY_SUB,
                SubscriptionManager.INVALID_SIM_SLOT_INDEX);

        if (SubscriptionManager.isValidSlotId(slotId)) {
            int nwMode = getPreferredNetworkFromDb(slotId);
        }
        return slotId;
    }

    void savePrimarySlot(int slotId) {
        Settings.Global.putInt(mContext.getContentResolver(),
                CONFIG_CURRENT_PRIMARY_SUB, slotId);
    }

    private boolean isManualConfigMode() {
        return isPrimarySetable()
                && getPrefPrimarySlot() == SubscriptionManager.INVALID_SIM_SLOT_INDEX;
    }

    private boolean isAutoConfigMode() {
        return isPrimarySetable()
                && getPrefPrimarySlot() != SubscriptionManager.INVALID_SIM_SLOT_INDEX;
    }

    private int getPreferredNetworkFromDb(int slotId) {
        SubscriptionController subContrl = SubscriptionController
                .getInstance();
        int[] subId = subContrl.getSubId(slotId);
        int nwMode = -1;

        if (subContrl.isActiveSubId(subId[0])) {
            nwMode = Settings.Global.getInt(
                    mContext.getContentResolver(),
                    Settings.Global.PREFERRED_NETWORK_MODE
                            + subId[0],
                    nwMode);
        }
        return nwMode;
    }

    private void logd(String message) {
        if (DEBUG) {
            Rlog.d(TAG, message);
        }
    }
}
