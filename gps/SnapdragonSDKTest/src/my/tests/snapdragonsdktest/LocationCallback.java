/*====*====*====*====*====*====*====*====*====*====*====*====*====*====*====*
  Copyright (c) 2015 Qualcomm Technologies, Inc.
  All Rights Reserved.
  Confidential and Proprietary - Qualcomm Technologies, Inc.
=============================================================================*/

package my.tests.snapdragonsdktest;

import android.util.Log;
import android.location.Location;
import com.qti.location.sdk.IZatFlpService;

public class LocationCallback implements IZatFlpService.IFlpLocationCallback {
    private static String TAG = "LocationCallbackInApp";
    public void onLocationAvailable(Location[] locations) {
        Log.v(TAG, "got batched fixes");
    }
}