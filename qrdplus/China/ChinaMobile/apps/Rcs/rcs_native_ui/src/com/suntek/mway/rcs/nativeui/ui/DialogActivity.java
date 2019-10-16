/*
 * Copyright (c) 2014-2015 pci-suntektech Technologies, Inc.  All Rights Reserved.
 * pci-suntektech Technologies Proprietary and Confidential.
 */

package com.suntek.mway.rcs.nativeui.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.RemoteException;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.view.KeyEvent;
import android.widget.Toast;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.suntek.mway.rcs.client.aidl.constant.Constants;
import com.suntek.mway.rcs.client.api.basic.BasicApi;
import com.suntek.mway.rcs.client.api.exception.ServiceDisconnectedException;
import com.suntek.mway.rcs.nativeui.R;
import com.suntek.mway.rcs.nativeui.RcsNativeUIApp;
import com.suntek.mway.rcs.nativeui.receiver.DmsReceiver;

public class DialogActivity extends Activity {

    private static final int OPEN_ACCOUNT = 1;
    private static final int OPEN_PS = 2;
    private static final int RE_GET_DMS_CONFIG = 3;
    private static final int NEED_OPEN_PS = 4;

    private static final String TYPE = "type";
    private static final String TITLE = "title";
    private static final String MESSAGE = "message";
    private static final String ACCEPT_BTN = "Accept_btn";
    private static final String REJECTBTN = "rejectBtn";

    public static void startOpenAccountDialog(Context context, String title, String message,
            int acceptButton, int rejectButton) {
        Intent intent = new Intent(context, DialogActivity.class);
        intent.putExtra(TYPE, OPEN_ACCOUNT);
        intent.putExtra(TITLE, title);
        intent.putExtra(MESSAGE, message);
        intent.putExtra(ACCEPT_BTN, acceptButton);
        intent.putExtra(REJECTBTN, rejectButton);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    public static void startOpenPSDialog(Context context) {
        Intent intent = new Intent(context, DialogActivity.class);
        intent.putExtra(TYPE, OPEN_PS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    public static void startReGetDmsConfig(Context context){
        Intent intent = new Intent(context, DialogActivity.class);
        intent.putExtra(TYPE, RE_GET_DMS_CONFIG);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    public static void startCloseWifiOpenPs(Context context) {
        Intent intent = new Intent(context, DialogActivity.class);
        intent.putExtra(TYPE, NEED_OPEN_PS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setFinishOnTouchOutside(false);
        int type = getIntent().getIntExtra(TYPE, 0);
        switch (type) {
            case OPEN_ACCOUNT:
                showOpenAccountDialog();
                break;
            case OPEN_PS:
                showOpenPSDialog();
                break;
            case RE_GET_DMS_CONFIG:
                showRegetDmsDialog();
                break;
            case NEED_OPEN_PS:
                showNeedCloseWifiOpenPsDialog();
                break;
            default:
                break;
        }
    }

    private void showNeedCloseWifiOpenPsDialog() {
        String title = this.getString(R.string.re_get_dms_config_title);
        String message = this.getString(R.string.open_account_need_ps);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setPositiveButton(getString(R.string.btn_confirm), new OnClickListener() {
            @Override
            public void onClick(DialogInterface arg0, int arg1) {
                Intent intent = new Intent();
                intent.setAction(Settings.ACTION_SETTINGS );
                startActivity(intent);
                finish();
            }
        });
        builder.setNegativeButton(getString(R.string.btn_cancel), new OnClickListener() {
            @Override
            public void onClick(DialogInterface arg0, int arg1) {
                finish();
            }
        });
        AlertDialog alertDialog = builder.create();
        alertDialog.setCanceledOnTouchOutside(false);
        alertDialog.setCancelable(false);
        alertDialog.show();
    }

    private void showRegetDmsDialog() {
        String title = this.getString(R.string.re_get_dms_config_title);
        String message = this.getString(R.string.re_get_dms_config_message);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setPositiveButton(getString(R.string.btn_confirm), new OnClickListener() {
            @Override
            public void onClick(DialogInterface arg0, int arg1) {
                try {
                    BasicApi.getInstance().openAccount();
                } catch (ServiceDisconnectedException e) {
                    e.printStackTrace();
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
                arg0.dismiss();
                finish();
            }
        });
        builder.setNegativeButton(getString(R.string.btn_cancel), new OnClickListener() {
            @Override
            public void onClick(DialogInterface arg0, int arg1) {
                arg0.dismiss();
                finish();
            }
        });
        AlertDialog alertDialog = builder.create();
        alertDialog.setCanceledOnTouchOutside(false);
        alertDialog.setCancelable(false);
        alertDialog.show();
    }

    private void showOpenPSDialog() {
        String title = this.getString(R.string.please_open_ps);
        String message = this.getString(R.string.please_open_ps_message);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setPositiveButton(getString(R.string.btn_confirm), new OnClickListener() {
            @Override
            public void onClick(DialogInterface arg0, int arg1) {
                DmsReceiver.closeWifi(DialogActivity.this);
                if (!getMobileData(DialogActivity.this)) {
                    RcsNativeUIApp.setNeedClosePs(true);
                    DmsReceiver.setMobileData(DialogActivity.this, true);
                }
                try {
                    BasicApi.getInstance().getConfiguration();
                } catch (ServiceDisconnectedException e) {
                    e.printStackTrace();
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
                arg0.dismiss();
                finish();
            }
        });
        builder.setNegativeButton(getString(R.string.btn_cancel), new OnClickListener() {
            @Override
            public void onClick(DialogInterface arg0, int arg1) {
                try {
                    BasicApi.getInstance().rejectOpenAccount();
                } catch (ServiceDisconnectedException e) {
                    e.printStackTrace();
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
                arg0.dismiss();
                finish();
            }
        });
        AlertDialog alertDialog = builder.create();
        alertDialog.setCanceledOnTouchOutside(false);
        alertDialog.setCancelable(false);
        alertDialog.show();
    }

    private void showOpenAccountDialog() {
        String title = getIntent().getStringExtra(TITLE);
        String message = getIntent().getStringExtra(MESSAGE);
        int acceptButton = getIntent().getIntExtra(ACCEPT_BTN,
                Constants.DMSConstants.CONST_BUTTON_DISPLAY);
        int rejectButton = getIntent().getIntExtra(REJECTBTN,
                Constants.DMSConstants.CONST_BUTTON_DISPLAY);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        builder.setMessage(message);
        if (acceptButton == Constants.DMSConstants.CONST_BUTTON_DISPLAY) {
            builder.setPositiveButton(getString(R.string.open_account_accpet),
                    new OnClickListener() {
                        @Override
                        public void onClick(DialogInterface arg0, int arg1) {
                            try {
                                BasicApi.getInstance().openAccount();
                            } catch (ServiceDisconnectedException e) {
                                e.printStackTrace();
                            } catch (RemoteException e) {
                                e.printStackTrace();
                            }
                            arg0.dismiss();
                            finish();
                        }
                    });
        }
        if (rejectButton == Constants.DMSConstants.CONST_BUTTON_DISPLAY) {
            builder.setNegativeButton(getString(R.string.open_account_reject),
                    new OnClickListener() {
                        @Override
                        public void onClick(DialogInterface arg0, int arg1) {
                            arg0.dismiss();
                            try {
                                BasicApi.getInstance().rejectOpenAccount();
                            } catch (ServiceDisconnectedException e) {
                                e.printStackTrace();
                            } catch (RemoteException e) {
                                e.printStackTrace();
                            }
                            if (RcsNativeUIApp.isNeedClosePs()) {
                                DmsReceiver.setMobileData(DialogActivity.this, false);
                                RcsNativeUIApp.setNeedClosePs(false);
                            }
                            if (RcsNativeUIApp.isNeedOpenWifi()) {
                                DmsReceiver.openWifi(DialogActivity.this);
                                RcsNativeUIApp.setNeedOpenWifi(false);
                            }
                            finish();
                        }
                    });
        }
        AlertDialog alertDialog = builder.create();
        alertDialog.setCanceledOnTouchOutside(false);
        alertDialog.setCancelable(false);
        alertDialog.show();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_BACK:
                finish();
                return true;

            default:
                break;
        }
        return super.onKeyDown(keyCode, event);
    }

    public static Boolean getMobileData(Context context) {
        boolean enable = false;
        try {
            ConnectivityManager mConnectivityManager = (ConnectivityManager)context
                    .getSystemService(Context.CONNECTIVITY_SERVICE);
            Class<? extends ConnectivityManager> ownerClass = mConnectivityManager.getClass();
            Method method = ownerClass.getMethod("getMobileDataEnabled");
            enable = (Boolean)method.invoke(mConnectivityManager);
        } catch (Exception e) {
            TelephonyManager telephonyManager = (TelephonyManager)context
                    .getSystemService(Context.TELEPHONY_SERVICE);
            Class<? extends TelephonyManager> ownerClass = telephonyManager.getClass();
            try {
                Method method = ownerClass.getMethod("getDataEnabled");
                enable = (Boolean)method.invoke(telephonyManager);
            } catch (NoSuchMethodException e1) {
                e1.printStackTrace();
            } catch (IllegalAccessException e1) {
                e1.printStackTrace();
            } catch (IllegalArgumentException e1) {
                e1.printStackTrace();
            } catch (InvocationTargetException e1) {
                e1.printStackTrace();
            }
            e.printStackTrace();
        }
        return enable;
    }

}
