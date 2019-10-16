/*
 * Copyright (c) 2015 Qualcomm Technologies, Inc.
 * All Rights Reserved.
 * Confidential and Proprietary - Qualcomm Technologies, Inc.
 */

package com.qualcomm.qti.tefprimarycardcontroller;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.telephony.Rlog;
import android.util.Log;

import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppType;
import com.android.internal.telephony.uicc.UiccCard;
import com.android.internal.util.XmlUtils;

import org.xmlpull.v1.XmlPullParser;

import java.util.ArrayList;
import java.util.Comparator;

/**
 * Parses primary card configuration files
 */
public class PrimaryCardConfigList {
    public static final int INVALID_PRIMARY_CARD_PRIORITY = -1;
    private final String TAG = "PrimaryCardConfigList";
    private static final boolean DEBUG = true;
    private static PrimaryCardConfigList sInstance;
    private final Context mContext;
    private final PriorityQueue<PrimaryCardInfo> mPrimaryCardConfigs =
            new PriorityQueue<PrimaryCardInfo>(
            new Comparator<PrimaryCardInfo>() {
                public int compare(PrimaryCardInfo c2,
                        PrimaryCardInfo c1) {
                    return c1.priority - c2.priority;
                }
            });

    private PrimaryCardConfigList(Context context) {
        mContext = context;
        loadPrimaryCardConfigs();
    }

    public static PrimaryCardConfigList getInstance(
            Context context) {
        synchronized (PrimaryCardConfigList.class) {
            if (sInstance == null) {
                sInstance = new PrimaryCardConfigList(context);
            }
            return sInstance;
        }
    }

    public int getHighestPriority() {
        return mPrimaryCardConfigs.isEmpty()
                ? INVALID_PRIMARY_CARD_PRIORITY
                : mPrimaryCardConfigs.get(0).priority;
    }

    public PrimaryCardInfo getPrimaryCardConfig(int mccmnc,
            UiccCard uiccCard) {
        if (mccmnc <= 0) {
            return null;
        }
        for (PrimaryCardInfo cardInfo : mPrimaryCardConfigs) {
            if ((cardInfo.mccmnc == mccmnc)
                    && (cardInfo.app == null ||
                            (uiccCard != null && uiccCard
                                    .isApplicationOnIcc(
                                            AppType.valueOf(
                                                    cardInfo.app))))) {
                return PrimaryCardInfo.from(cardInfo);
            }
        }
        return null;
    }

    public int getPrimaryCardPriority(int mccmnc,
            UiccCard uiccCard) {
        PrimaryCardInfo cardInfo = getPrimaryCardConfig(mccmnc,
                uiccCard);
        return cardInfo == null ? INVALID_PRIMARY_CARD_PRIORITY
                : cardInfo.priority;
    }

    public int getPrimaryCardPrefNetwork(int mccmnc,
            UiccCard uiccCard) {
        PrimaryCardInfo cardInfo = getPrimaryCardConfig(mccmnc,
                uiccCard);
        return cardInfo == null ? -1 : cardInfo.network;
    }

    private void loadPrimaryCardConfigs() {
        mPrimaryCardConfigs.clear();
        Resources r = mContext.getResources();
        XmlResourceParser parser = r
                .getXml(R.xml.primary_card_conf);
        try {
            XmlUtils.beginDocument(parser, "pccs");
            XmlUtils.nextElement(parser);
            while (parser
                    .getEventType() != XmlPullParser.END_DOCUMENT) {
                PrimaryCardInfo primaryCardInfo = new PrimaryCardInfo();
                primaryCardInfo.mccmnc = Integer.parseInt(parser
                        .getAttributeValue(null, "mccmnc"));
                primaryCardInfo.app = parser
                        .getAttributeValue(null, "app_type");
                primaryCardInfo.priority = Integer
                        .parseInt(parser.getAttributeValue(null,
                                "priority"));
                primaryCardInfo.network = Integer.parseInt(parser
                        .getAttributeValue(null, "network"));
                mPrimaryCardConfigs.add(primaryCardInfo);
                XmlUtils.nextElement(parser);
            }
        } catch (Exception e) {
            Log.w(TAG, "failed to load primary_card_conf xml",
                    e);
        } finally {
            parser.close();
        }
        logd("Primarycard configs loaded:"
                + mPrimaryCardConfigs);
    }

    private void logd(String message) {
        Rlog.d(TAG, message);
    }

    public static class PrimaryCardInfo {
        public int mccmnc;
        public String app;
        public int priority;
        public int network;

        public static PrimaryCardInfo from(
                PrimaryCardInfo primaryCardInfo) {
            if (primaryCardInfo == null) {
                return null;
            }
            PrimaryCardInfo cardInfo = new PrimaryCardInfo();
            cardInfo.mccmnc = primaryCardInfo.mccmnc;
            cardInfo.priority = primaryCardInfo.priority;
            cardInfo.network = primaryCardInfo.network;
            cardInfo.app = primaryCardInfo.app;
            return cardInfo;
        }

        @Override
        public String toString() {
            return "[mccmnc " + mccmnc + ", " + app + ", "
                    + priority + ", " + network + "]";
        }
    }

    public static class PriorityQueue<T> extends ArrayList<T> {
        private static final long serialVersionUID = 1L;
        private Comparator<T> mComparator;

        public PriorityQueue(Comparator<T> comparator) {
            mComparator = comparator;
        }

        @Override
        public boolean add(T e) {
            if (!isEmpty() && mComparator != null) {
                for (int index = 0; index < size(); index++) {
                    if (mComparator.compare(e, get(index)) < 0) {
                        super.add(index, e);
                        return true;
                    }
                }
            }
            return super.add(e);
        }

    }
}
