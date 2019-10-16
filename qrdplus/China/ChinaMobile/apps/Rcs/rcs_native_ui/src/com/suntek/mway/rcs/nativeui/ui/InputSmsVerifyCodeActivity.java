/*
 * Copyright (c) 2015 pci-suntektech Technologies, Inc.  All Rights Reserved.
 * pci-suntektech Technologies Proprietary and Confidential.
 */

package com.suntek.mway.rcs.nativeui.ui;

import com.suntek.mway.rcs.client.api.basic.BasicApi;
import com.suntek.mway.rcs.client.api.exception.ServiceDisconnectedException;
import com.suntek.mway.rcs.nativeui.R;
import com.suntek.rcs.ui.common.utils.RcsUtils;

import android.annotation.TargetApi;
import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.text.TextUtils;
import android.view.Display;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.widget.EditText;
import android.widget.Toast;

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
@SuppressWarnings("deprecation")
public class InputSmsVerifyCodeActivity extends Activity implements OnClickListener {

    private EditText mEditText;
    private BasicApi mBasicApi = BasicApi.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.input_sms_verify_code);

        this.setFinishOnTouchOutside(false);

        WindowManager manage = getWindowManager();
        Display display = manage.getDefaultDisplay();
        LayoutParams params = getWindow().getAttributes();
        params.width = display.getWidth() - RcsUtils.dip2px(this, 20);
        getWindow().setAttributes(params);

        mEditText = (EditText)findViewById(R.id.input_verify_code);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.commit_verify_code:
                String code = mEditText.getText().toString();
                code = code.replace(" ", "");
                if (!TextUtils.isEmpty(code)) {
                    try {
                        mBasicApi.getConfigurationWithOtp(code);
                        finish();
                    } catch (ServiceDisconnectedException e) {
                        e.printStackTrace();
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                } else {
                    Toast.makeText(InputSmsVerifyCodeActivity.this, R.string.verify_code_is_null,
                            Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.abandon_verify_code:
                finish();
                break;
        }
    }

}
