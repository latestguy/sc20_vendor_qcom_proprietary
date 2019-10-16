/*============================================================================
@file QSensorDialog.java

@brief
The dialog box used by the user to select the sensor data stream rate for either
a particular sensor, or all sensors.

Copyright (c) 2011-2013, 2015 Qualcomm Technologies, Inc.
All Rights Reserved.
Confidential and Proprietary - Qualcomm Technologies, Inc.
============================================================================*/
package com.qualcomm.qti.sensors.ui.stream;

import android.app.Dialog;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.content.Context;

import com.qualcomm.qti.sensors.qsensortest.R;
import com.qualcomm.qti.sensors.ui.qsensortest.TabControl;

public class SensorDialog extends Dialog {
  public SensorDialog(View.OnClickListener onClickListener, int currStreamRate,
      int currReportRate, boolean supportsBatching, Context mContext) {
        super(mContext);
    if (TabControl.EnableWearQSTP) {
        this.setContentView(R.layout.wear_stream_rate_dialog);
        Button delayNormalButton = (Button) this.findViewById(R.id.wear_delay_button_normal);
        delayNormalButton.setOnClickListener(onClickListener);
        Button delayUIButton = (Button) this.findViewById(R.id.wear_delay_button_ui);
        delayUIButton.setOnClickListener(onClickListener);
        Button delayGameButton = (Button) this.findViewById(R.id.wear_delay_button_game);
        delayGameButton.setOnClickListener(onClickListener);
        Button delayFastestButton = (Button) this.findViewById(R.id.wear_delay_button_fastest);
        delayFastestButton.setOnClickListener(onClickListener);
        Button dialogCancelButton = (Button) this.findViewById(R.id.wear_delay_button_cancel);
        dialogCancelButton.setOnClickListener(onClickListener);
    } else {
        this.setContentView(R.layout.stream_rate_dialog);
        this.setTitle("Sensor Stream Rate");

        String currentRate = currStreamRate == -1 ? "" : String.valueOf(currStreamRate);
        EditText streamRateField = (EditText) this.findViewById(R.id.delay_field_sample);
        streamRateField.setText(currentRate);

        currentRate = currReportRate == -1 ? "" : String.valueOf(currReportRate);
        EditText reportRateField = (EditText) this.findViewById(R.id.delay_field_report);
        reportRateField.setText(currentRate);

        Button delayNormalButton = (Button) this.findViewById(R.id.delay_button_normal);
        delayNormalButton.setOnClickListener(onClickListener);

        Button delayUIButton = (Button) this.findViewById(R.id.delay_button_ui);
        delayUIButton.setOnClickListener(onClickListener);

        Button delayGameButton = (Button) this.findViewById(R.id.delay_button_game);
        delayGameButton.setOnClickListener(onClickListener);

        Button delayFastestButton = (Button) this.findViewById(R.id.delay_button_fastest);
        delayFastestButton.setOnClickListener(onClickListener);

        Button dialogSubmitButton = (Button) this.findViewById(R.id.delay_button_submit);
        dialogSubmitButton.setOnClickListener(onClickListener);

        Button dialogCancelButton = (Button) this.findViewById(R.id.delay_button_cancel);
        dialogCancelButton.setOnClickListener(onClickListener);

        Button dialogFlushButton = (Button) this.findViewById(R.id.delay_button_flush);

        boolean sdkSupport = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT;
        if (!sdkSupport || !supportsBatching) {
           reportRateField = (EditText) this.findViewById(R.id.delay_field_report);
           reportRateField.setEnabled(false);

           TextView reportRateText = (TextView) this.findViewById(R.id.delay_field_report_text);
           reportRateText.setEnabled(false);
      }

      if (!sdkSupport) {
          dialogFlushButton.setEnabled(false);
      }

      dialogFlushButton.setOnClickListener(onClickListener);
   }
  }
}
