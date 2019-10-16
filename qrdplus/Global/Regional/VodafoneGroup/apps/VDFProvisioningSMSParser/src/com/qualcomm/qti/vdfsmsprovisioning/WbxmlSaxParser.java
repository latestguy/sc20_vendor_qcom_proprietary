 /*
  * Copyright (c) 2013,2016 Qualcomm Technologies, Inc.
  * All Rights Reserved.
  * Confidential and Proprietary - Qualcomm Technologies, Inc.
  *
  * Not a Contribution.
  * Apache license notifications and license are retained
  * for attribution purposes only.
  *
  * Copyright (C) 2007 The Android Open Source Project
  *
  * Licensed under the Apache License, Version 2.0 (the "License");
  * you may not use this file except in compliance with the License.
  * You may obtain a copy of the License at
  *
  *      http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  * See the License for the specific language governing permissions and
  * limitations under the License.
  */

package com.qualcomm.qti.vdfsmsprovisioning;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.os.SystemProperties;
import android.text.TextUtils;
import android.util.Log;

/*
 * WBXML parser for parsing the operator specific provisioning message.
 * This parser based on SAX.
 */
public class WbxmlSaxParser {

    private WbxmlParser mPullParser;
    private SmsProvisioningDocContentHandler mContentHandler;
    private final String PERSIST_KEY = "persist.sys.provisioning";
    private final String PROVISIONING_KEY = "ePDG FQDN";
    private HashMap<String, String> mHashMap;
    private String TAG = "WbxmlSaxParser";

    public WbxmlSaxParser() {
        mPullParser = new WbxmlParser();
        mPullParser.setTagTable(0, WbxmlTokenTable.TAG_TABLE_CODEPAGE_0);
        mPullParser.setAttrStartTable(0, WbxmlTokenTable.ATTRIBUTE_START_TABLE_CODEPAGE_0);
    }

    public void parseXml(InputStream bytesIn, SmsProvisioningDocContentHandler contentHandler) {
        boolean result = parse(bytesIn, contentHandler);
        if (result) {
            mHashMap = mContentHandler.getParameters();
            if (!TextUtils.isEmpty(mHashMap.get(PROVISIONING_KEY))) {
                SystemProperties.set(PERSIST_KEY, "true");
            } else {
                SystemProperties.set(PERSIST_KEY, "false");
            }
        }
    }

    private boolean parse(InputStream bytesIn, SmsProvisioningDocContentHandler contentHandler) {
        mContentHandler = contentHandler;
        try {
            mPullParser.setInput(bytesIn, null);
            contentHandler.startDocument();
            boolean endOfDocument = false;
            while (!endOfDocument) {
                int state = mPullParser.next();

                switch (state) {
                    case XmlPullParser.START_TAG: {
                        contentHandler.startElement(mPullParser.getName(), getAttributes());
                        break;
                    }
                    case XmlPullParser.END_TAG: {
                        contentHandler.endElement(mPullParser.getName());
                        break;
                    }
                    case XmlPullParser.END_DOCUMENT: {
                        contentHandler.endDocument();
                        endOfDocument = true;
                        break;
                    }
                }
            }
        } catch (XmlPullParserException e) {
            Log.v(TAG, e.toString());
            return false;
        } catch (IOException e) {
            Log.v(TAG, e.toString());
            return false;
        }
        return true;
    }

    private Map<String, String> getAttributes() {
        Map<String, String> attributes = new HashMap<String, String>();
        int attributeCount = 0;
        if ((attributeCount = mPullParser.getAttributeCount()) > 0) {
            for (int i = 0; i < attributeCount; i++) {
                attributes.put(mPullParser.getAttributeName(i), mPullParser.getAttributeValue(i));
            }
        }
        return attributes;
    }
}
