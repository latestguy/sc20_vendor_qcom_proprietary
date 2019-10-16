/*
 * Copyright (c) 2015 Qualcomm Technologies, Inc.
 * All Rights Reserved.
 * Confidential and Proprietary - Qualcomm Technologies, Inc.
 */

package com.wdstechnology.android.kryten.demo;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.app.Activity;
import android.view.View;
import android.view.View.OnClickListener;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.app.ActionBar;
import com.wdstechnology.android.kryten.demo.R;

public class LauncherActivity extends Activity {
    private String TAG = "LauncherActivity";
    public Button mViewButton;
    private static final String ACTION_CONFIGURE_MESSAGE =
            "org.codeaurora.CONFIGURE_MESSAGE";

    @Override
    public void onCreate(Bundle savedInstanceState) {
       super.onCreate(savedInstanceState);
       Log.d(TAG,"onCreate");
       setContentView(R.layout.activity_main);
       mViewButton = (Button) findViewById(R.id.view_message);
       getActionBar().setBackgroundDrawable(new ColorDrawable(getResources()
               .getColor(R.color.holo_blue)));
       mViewButton.setOnClickListener(new OnClickListener() {
        @Override
        public void onClick(View arg0) {

            try {
                Intent intent = new Intent(ACTION_CONFIGURE_MESSAGE);
                startActivity(intent);
            } catch (ActivityNotFoundException e) {
                Log.e(TAG,"Activity not found : "+e);
            }
        }
    });
    }

}
