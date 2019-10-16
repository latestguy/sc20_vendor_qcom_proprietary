/*====*====*====*====*====*====*====*====*====*====*====*====*====*====*====*
  Copyright (c) 2015 Qualcomm Technologies, Inc.
  All Rights Reserved.
  Confidential and Proprietary - Qualcomm Technologies, Inc.
=============================================================================*/

package my.tests.snapdragonsdktest;

import java.util.ArrayList;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;

import com.qti.location.sdk.IZatManager;
import com.qti.location.sdk.IZatFlpService;
import com.qti.location.sdk.IZatGeofenceService;

public class FullscreenActivity extends Activity {

    private static String TAG = "SnapdragonSDKTest";
    private IZatGeofenceService.IZatGeofenceHandle mHandle = null;
    private boolean isStarted = false;
    private boolean isPaused = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fullscreen);
        boolean permissionGranted = false;

        if (Build.VERSION.SDK_INT >= 23) {
            // if SDK version > 23, check for required runtime permission.
            String[] operationPermissionNames = getOperationPermissionName();

            if (operationPermissionNames == null ||
                        checkOperationPermission(operationPermissionNames)) {
                permissionGranted = true;
            }
            else {
                // if permission not granted, don't continue
                permissionGranted = false;
            }
        }
        else {
            permissionGranted = true;
        }

        if (permissionGranted) {
            final View controlsView = findViewById(R.id.fullscreen_content_controls);
            final Button addRemoveGeofence = (Button) findViewById(R.id.eula);
            final Button pauseGeofence = (Button) findViewById(R.id.radio);
            final Button setDwellGeofence = (Button) findViewById(R.id.buildinfo);
            final TextView tv = (TextView) findViewById(R.id.result);
            final IZatManager qcLM = IZatManager.getInstance(getApplicationContext());
            final IZatGeofenceService gfSevice = qcLM.connectGeofenceService();

            final BreachCallback breachCb = new BreachCallback();

            String version = qcLM.getVersion();
            Log.v(TAG, "SDK and Service Version:" + version);

            addRemoveGeofence.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    if (!isStarted) {
                        tv.setText("Add a geofence...");

                        // register callback
                        gfSevice.registerForGeofenceCallbacks(breachCb);
                        //37.375220, -121.983573
                        IZatGeofenceService.IzatGeofence geofence =
                            new IZatGeofenceService.IzatGeofence(37.375507,
                                                                 -121.983643,
                                                                 5000);

                        IZatGeofenceService.IzatDwellNotify dwellNotify =
                            new IZatGeofenceService.IzatDwellNotify (3,
                            IZatGeofenceService.IzatDwellNotify.DWELL_TYPE_INSIDE_MASK |
                            IZatGeofenceService.IzatDwellNotify.DWELL_TYPE_OUTSIDE_MASK);

                        geofence.setTransitionTypes(
                            IZatGeofenceService.IzatGeofenceTransitionTypes.ENTERED_AND_EXITED);
                        geofence.setNotifyResponsiveness(10000);
                        geofence.setConfidence(IZatGeofenceService.IzatGeofenceConfidence.MEDIUM);
                        geofence.setDwellNotify(dwellNotify);
                        String name = "geofence";
                        mHandle = gfSevice.addGeofence(name, geofence);
                        isStarted = true;
                    } else {
                        tv.setText("Remove a geofence...");
                        gfSevice.removeGeofence(mHandle);
                        // un-register callback
                        gfSevice.deregisterForGeofenceCallbacks(breachCb);
                        isStarted = false;
                    }
                }
            });

            pauseGeofence.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                        if (mHandle != null) {
                            if (!isPaused) {
                                tv.setText("Pause a geofence..");
                                mHandle.pause();
                                isPaused = true;
                            } else {
                                tv.setText("Resume a geofence..");
                                mHandle.resume(
                                    IZatGeofenceService.IzatGeofenceTransitionTypes.ENTERED_ONLY);
                                isPaused = false;
                            }
                        } else {
                            tv.setText("no geofence added...");
                        }
                    }
            });

            setDwellGeofence.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    if (mHandle != null) {
                        tv.setText("updating the geofence");
                        mHandle.update(
                            IZatGeofenceService.IzatGeofenceTransitionTypes.EXITED_ONLY, 897600);
                    } else {
                        tv.setText("no FLP session...");
                    }
                }
            });
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
    }

    private boolean checkOperationPermission(String[] permissionName) {
        ArrayList<String> needRequestPermission = new ArrayList<String>();
        for (String tmp : permissionName) {
            if (!(PackageManager.PERMISSION_GRANTED == checkSelfPermission(tmp))) {
                needRequestPermission.add(tmp);
            }
        }

        if (needRequestPermission.size() == 0) {
            return true;
        } else {
            String[] needRequestPermissionArray = new String[needRequestPermission.size()];
            needRequestPermission.toArray(needRequestPermissionArray);
            requestPermissions(needRequestPermissionArray, 0);
            return false;
        }
    }

    private String[] getOperationPermissionName() {
        return new String[]{ Manifest.permission.ACCESS_FINE_LOCATION};
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (permissions == null || grantResults == null ||
            permissions.length == 0 || grantResults.length == 0) {
            return;
        }

        boolean bPass = true;
        for (int i = 0 ; i < permissions.length ; i++) {
            Log.d(TAG, "PermissionsResult: " + permissions[i] + ":" + grantResults[i]);
            if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                bPass = false;
            }
        }
        if(!bPass) {
            finish();
        }
    }

}
