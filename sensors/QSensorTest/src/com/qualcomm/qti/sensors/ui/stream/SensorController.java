/*============================================================================
@file QSensorController.java

@brief
Handler for all user input.

Copyright (c) 2013, 2015 Qualcomm Technologies, Inc.
All Rights Reserved.
Confidential and Proprietary - Qualcomm Technologies, Inc.
============================================================================*/
package com.qualcomm.qti.sensors.ui.stream;

import android.annotation.SuppressLint;
import android.hardware.SensorManager;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.EditText;
import android.widget.Toast;
import android.content.Context;
import android.util.Log;

import com.qualcomm.qti.sensors.core.stream.SensorAdapter;
import com.qualcomm.qti.sensors.qsensortest.R;
import com.qualcomm.qti.sensors.ui.qsensortest.TabControl;
import com.qualcomm.qti.sensors.ui.stream.WearStreamingActivity;

public class SensorController implements OnClickListener, OnLongClickListener {
  protected SensorManager sensorManager;
  protected SensorAdapter sensorAdapter;
  protected SensorDialog activeDialog;  // The dialog that is now active
  public Context mContext;

  public SensorController(SensorManager sensorManager, SensorAdapter sensorAdapter) {
    this.sensorAdapter = sensorAdapter;
    this.sensorManager = sensorManager;
    this.activeDialog = null;
    mContext = TabControl.getContext();
    if (TabControl.EnableWearQSTP) {
        mContext = WearStreamingActivity.getContext();
    }
  }

  @SuppressLint("NewApi")
  @Override
  public boolean onLongClick(View view) {

  mContext = TabControl.getContext();
    if (TabControl.EnableWearQSTP) {
          mContext = WearStreamingActivity.getContext();
    }

    if(R.id.stream_data_column1 == view.getId() || R.id.stream_data_column2 == view.getId() ||
       R.id.stream_sensor_title == view.getId() || R.id.stream_sensor_rate == view.getId()) {
      boolean batchSupport = this.sensorAdapter.sensor().getFifoMaxEventCount() > 0;
      this.activeDialog = new SensorDialog(this, this.sensorAdapter.streamRate(),
          this.sensorAdapter.reportRate(), batchSupport,mContext);
      this.activeDialog.show();
    }

    return true;
  }

  @SuppressLint("NewApi")
  @Override
  public void onClick(View view) {
    if(R.id.stream_data_column1 == view.getId() || R.id.stream_data_column2 == view.getId() ||
       R.id.stream_sensor_title == view.getId() || R.id.stream_sensor_rate == view.getId()) {
      this.sensorAdapter.streamRateIs(SensorManager.SENSOR_DELAY_NORMAL, -1, true);
    }
    else if(R.id.stream_button_cancel == view.getId()){
      this.sensorAdapter.streamRateIs(-1, -1, false);
    }
    else if(R.id.delay_button_normal == view.getId()){
      this.sensorAdapter.streamRateIs(SensorManager.SENSOR_DELAY_NORMAL, -1, true);
      activeDialog.cancel();
    }
    else if(R.id.delay_button_ui == view.getId()){
      this.sensorAdapter.streamRateIs(SensorManager.SENSOR_DELAY_UI, -1, true);
      activeDialog.cancel();
    }
    else if(R.id.delay_button_game == view.getId()){
      this.sensorAdapter.streamRateIs(SensorManager.SENSOR_DELAY_GAME, -1, true);
      activeDialog.cancel();
    }
    else if(R.id.delay_button_fastest == view.getId()){
      this.sensorAdapter.streamRateIs(SensorManager.SENSOR_DELAY_FASTEST, -1, true);
      activeDialog.cancel();
    }
    else if(R.id.delay_button_submit == view.getId()){
      EditText sampleField = (EditText) activeDialog.findViewById(R.id.delay_field_sample);
      EditText reportField = (EditText) activeDialog.findViewById(R.id.delay_field_report);
      int batchRate;

      try{
        batchRate = Integer.parseInt(reportField.getText().toString());
      } catch(NumberFormatException e){
        batchRate = -1;
      }

      try{
        int rate = Integer.parseInt(sampleField.getText().toString());
        this.sensorAdapter.streamRateIs(rate, batchRate, true);
      } catch(NumberFormatException e){
        Toast.makeText(TabControl.getContext(), "Invalid number entry", Toast.LENGTH_LONG).show();
      }
      activeDialog.cancel();
    }
    else if(R.id.delay_button_cancel == view.getId()){
      this.activeDialog.cancel();
    }
    else if(R.id.delay_button_flush == view.getId()){
      this.sensorAdapter.flush(this.sensorManager);
      this.activeDialog.cancel();
    }
    else if(R.id.wear_delay_button_ui == view.getId()){
      this.sensorAdapter.streamRateIs(SensorManager.SENSOR_DELAY_UI, -1, true);
      activeDialog.cancel();
    }
    else if(R.id.wear_delay_button_fastest == view.getId()){
      this.sensorAdapter.streamRateIs(SensorManager.SENSOR_DELAY_FASTEST, -1, true);
      activeDialog.cancel();
    }
    else if(R.id.wear_delay_button_game == view.getId()){
      this.sensorAdapter.streamRateIs(SensorManager.SENSOR_DELAY_GAME, -1, true);
      activeDialog.cancel();
    }
    else if(R.id.wear_delay_button_normal == view.getId()){
      this.sensorAdapter.streamRateIs(SensorManager.SENSOR_DELAY_NORMAL, -1, true);
      activeDialog.cancel();
    }
    else if(R.id.wear_delay_button_cancel == view.getId()){
      this.activeDialog.cancel();
    }
  }
}
