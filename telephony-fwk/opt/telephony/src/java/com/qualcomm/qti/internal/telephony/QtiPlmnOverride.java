/*
 * Copyright (c) 2016 Qualcomm Technologies, Inc.
 * All Rights Reserved.
 * Confidential and Proprietary - Qualcomm Technologies, Inc.
 *
 * Not a Contribution.
 * Apache license notifications and license are retained
 * for attribution purposes only.
 *
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.qti.internal.telephony;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.os.Environment;
import android.telephony.Rlog;
import android.util.Xml;

import com.android.internal.util.XmlUtils;

public class QtiPlmnOverride {
    private HashMap<String, String> mCarrierPlmnMap;

    static final String LOG_TAG = "PlmnOverride";
    static final String PARTNER_PLMN_OVERRIDE_PATH ="etc/plmn-conf.xml";

    public QtiPlmnOverride () {
        mCarrierPlmnMap = new HashMap<String, String>();
        loadPlmnOverrides();
    }

    public boolean containsCarrier(String carrier) {
        return mCarrierPlmnMap.containsKey(carrier);
    }

    public String getPlmn(String carrier) {
        return mCarrierPlmnMap.get(carrier);
    }

    private void loadPlmnOverrides() {
        FileReader plmnReader;

        final File plmnFile = new File(Environment.getRootDirectory(),
                PARTNER_PLMN_OVERRIDE_PATH);

        try {
            plmnReader = new FileReader(plmnFile);
        } catch (FileNotFoundException e) {
            Rlog.w(LOG_TAG, "Can not open " +
                    Environment.getRootDirectory() + "/" + PARTNER_PLMN_OVERRIDE_PATH);
            return;
        }

        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(plmnReader);

            XmlUtils.beginDocument(parser, "plmnOverrides");

            while (true) {
                XmlUtils.nextElement(parser);

                String name = parser.getName();
                if (!"plmnOverride".equals(name)) {
                    break;
                }

                String numeric = parser.getAttributeValue(null, "numeric");
                String data    = parser.getAttributeValue(null, "plmn");

                mCarrierPlmnMap.put(numeric, data);
            }
            plmnReader.close();
        } catch (XmlPullParserException e) {
            Rlog.w(LOG_TAG, "Exception in plmn-conf parser " + e);
        } catch (IOException e) {
            Rlog.w(LOG_TAG, "Exception in plmn-conf parser " + e);
        }
    }
}
