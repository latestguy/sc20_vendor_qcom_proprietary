/*
 *Copyright (c) 2016 Qualcomm Technologies, Inc.
 *All Rights Reserved.
 *Confidential and Proprietary - Qualcomm Technologies, Inc.
 */
package com.qualcomm.qti.vdfsmsprovisioning;
import java.util.Map;

public interface ContentHandler {

    void startDocument();

    void startElement(String elementName, Map<String, String> attributes);

    void endElement(String elementName);

    void endDocument();

}

