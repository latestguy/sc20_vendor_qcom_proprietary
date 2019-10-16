/*
 *Copyright (c) 2016 Qualcomm Technologies, Inc.
 *All Rights Reserved.
 *Confidential and Proprietary - Qualcomm Technologies, Inc.
 */
package com.qualcomm.qti.vdfsmsprovisioning;

import android.widget.Button;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

/* An activity that prompts for user confirmation to accept or reject the provisioning
 * message sent by the operator.
 */
public class ConfirmationActivity extends Activity {
    byte[] pushData = null;

    private class ReceivePushTask extends AsyncTask<byte[], Void, Void> {

        public ReceivePushTask(Context context) {
        }

        @Override
        protected Void doInBackground(byte[]... params) {
            InputStream inputStream = new ByteArrayInputStream(params[0]);
            parse(inputStream);
            return null;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle extras = getIntent().getExtras();
        pushData = extras.getByteArray("data");

        final Window win = getWindow();
        win.requestFeature(Window.FEATURE_NO_TITLE);
        win.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);

        setContentView(R.layout.confirmation_dialog_layout);

        findViewById(R.id.cancel_button_id).setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        findViewById(R.id.ok_button_id).setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                new ReceivePushTask(ConfirmationActivity.this).execute(pushData);
                finish();
            }
        });
    }

    private void parse(InputStream is) {
        SmsProvisioningDocContentHandler contentHandler = new SmsProvisioningDocContentHandler();
        WbxmlSaxParser parser = new WbxmlSaxParser();
        parser.parseXml(is, contentHandler);
    }
}
