/*Copyright (c) 2016 Qualcomm Technologies, Inc.
 *All Rights Reserved.
 *Confidential and Proprietary - Qualcomm Technologies, Inc.
*/

package com.qualcomm.qti.mobiledatapromptdisplay;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.view.View;
import android.view.Window;
import android.widget.Button;

import com.qualcomm.qti.mobiledatapromptdisplay.receiver.SimStateChangeReceiver;

public class MobileDataPromptDisplayActivity extends Activity implements View.OnClickListener {

    private Button mOkButton;
    private Button mCancelButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        this.setFinishOnTouchOutside(false);
        setContentView(R.layout.dialog);
        mOkButton = (Button) findViewById(R.id.ok_button_id);
        mCancelButton = (Button) findViewById(R.id.cancel_button_id);
        addListener();
    }

    private void addListener() {
        mOkButton.setOnClickListener(this);
        mCancelButton.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.cancel_button_id:
                persistDataPromptShown();
                finish();
                break;
            case R.id.ok_button_id:
                // enable mobile data for Preferred SIM
                int subId = SubscriptionManager.getDefaultDataSubscriptionId();
                TelephonyManager telephonyManager = (TelephonyManager) this.
                        getSystemService(this.TELEPHONY_SERVICE);
                telephonyManager.setDataEnabled(subId, true);
                persistDataPromptShown();
                finish();
                break;
        }
    }

    public void persistDataPromptShown() {
        SharedPreferences pref = this.getSharedPreferences(
                SimStateChangeReceiver.MOBILE_DATA_CONFIRMATION_PREF,
                this.MODE_PRIVATE);
        if (pref != null) {
            SharedPreferences.Editor edit = pref.edit();
            edit.putBoolean(SimStateChangeReceiver.HAS_DATA_PROMPT_SHOWN,
                    true);
            edit.commit();
            MobileDataPromptDisplayApplication application =
                    (MobileDataPromptDisplayApplication)getApplication();
            application.unregisterSimStateChangeReceiver();
        }
    }

    // to prevent dialog from dismiss on press of device back button
    @Override
    public void onBackPressed() {
    }
}
