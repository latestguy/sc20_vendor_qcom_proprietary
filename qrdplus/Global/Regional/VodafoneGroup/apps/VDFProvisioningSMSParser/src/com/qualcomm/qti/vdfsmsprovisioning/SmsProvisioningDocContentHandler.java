/*
 *Copyright (c) 2016 Qualcomm Technologies, Inc.
 *All Rights Reserved.
 *Confidential and Proprietary - Qualcomm Technologies, Inc.
 */
package com.qualcomm.qti.vdfsmsprovisioning;

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

import android.os.SystemProperties;

/*
 * Class that validates the incoming wbxml content by parsing elements by elements
 * construct a hashmap of parm value from the incoming wbxml content.
 */

public class SmsProvisioningDocContentHandler implements ContentHandler {

    private static final String ROOT_DOCUMENT_ELEMENT = "ROOT";
    private static final String CHARACTERISTIC_DOCUMENT_ELEMENT = "characteristic";
    private static final String PARM_DOCUMENT_ELEMENT = "parm";
    private static final String ATTRIBUTE_NAME = "ePDG FQDN";
    private Stack<String> mContext = new Stack<String>();
    public HashMap<String, String> mParameters = new HashMap<String, String>();
    private boolean mHasParsedCompleteDocument = false;

    @Override
    public void startDocument() {
        mContext.push(ROOT_DOCUMENT_ELEMENT);
    }

    @Override
    public void startElement(String elementName, Map<String, String> attributes) {
        mContext.push(createContextPath(elementName, attributes));
        if (PARM_DOCUMENT_ELEMENT.equals(elementName)) {
            addParameter(attributes);
        }
    }

    @Override
    public void endElement(String elementName) {
        mContext.pop();
    }

    @Override
    public void endDocument() {
        mHasParsedCompleteDocument = ROOT_DOCUMENT_ELEMENT.
                equals(mContext.pop()) && mContext.empty();
    }

    public boolean isDocumentWellFormed() {
        return mHasParsedCompleteDocument;
    }

    private String createContextPath(String elementName, Map<String, String> attributes) {
        String attType = attributes.get("type");
        String attName = attributes.get("name");
        String attValue = attributes.get("value");

        if (CHARACTERISTIC_DOCUMENT_ELEMENT.equals(elementName)) {
            return elementName + "[type=" + attType + "]";
        } else if (PARM_DOCUMENT_ELEMENT.equals(elementName)) {
            if ((attValue != null) && (attName != null) && (attName.equals(ATTRIBUTE_NAME))) {
                return elementName + "[[name=" + attName + "]" + " " + "[value=" + attValue + "]]";
            }
        } else {
            return elementName;
        }
        return elementName;
    }

    private void addParameter(Map<String, String> attributes) {
        if ((attributes.get("name") != null) && (attributes.get("name").equals(ATTRIBUTE_NAME))) {
            mParameters.put(attributes.get("name"), attributes.get("value"));
        }
    }

    public HashMap<String, String> getParameters() {
        return mParameters;
    }
}
