/*
 * Copyright (c) 2016 Qualcomm Technologies, Inc.
 * All Rights Reserved.
 * Confidential and Proprietary - Qualcomm Technologies, Inc.
 */

package com.qualcomm.qti.telephony.carrierpack;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;

public class MasterClearConfirmationActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(
                R.string.master_clear_desc)
                .setTitle(
                        R.string.master_clear_title)
                .setPositiveButton(
                        R.string.master_clear_positive_button,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,
                                    int whichButton) {
                                Intent intent = new Intent(
                                        Intent.ACTION_MASTER_CLEAR);
                                intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
                                intent.putExtra(Intent.EXTRA_REASON,
                                        "MasterClearConfirm");
                                sendBroadcast(intent);
                                finish();
                            }
                        })
                .setNegativeButton(
                        R.string.master_clear_negative_button,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,
                                    int whichButton) {
                                finish();
                            }
                        })
                .setCancelable(false);
        AlertDialog masterClearConfirm = builder.create();
        masterClearConfirm.show();
    }

}

