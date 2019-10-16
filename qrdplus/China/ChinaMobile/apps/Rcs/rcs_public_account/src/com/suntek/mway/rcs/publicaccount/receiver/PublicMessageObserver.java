/*
 * Copyright (c) 2015 pci-suntektech Technologies, Inc.  All Rights Reserved.
 * pci-suntektech Technologies Proprietary and Confidential.
 */
package com.suntek.mway.rcs.publicaccount.receiver;

import com.suntek.mway.rcs.nativeui.RcsNativeUIApp;
import com.suntek.mway.rcs.publicaccount.util.PublicAccountUtils;

import android.content.Intent;
import android.database.ContentObserver;
import android.os.Handler;
import android.util.Log;

public class PublicMessageObserver extends ContentObserver {

    public PublicMessageObserver(Handler handler) {
        super(handler);
    }

    @Override
    public void onChange(boolean selfChange) {
       Intent intent  = new Intent();
       intent.setAction(PublicAccountUtils.UI_NEED_FRESH);
       RcsNativeUIApp.getApplication().sendBroadcast(intent);
    }
}
