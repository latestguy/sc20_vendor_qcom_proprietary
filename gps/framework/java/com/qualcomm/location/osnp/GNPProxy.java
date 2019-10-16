/*====*====*====*====*====*====*====*====*====*====*====*====*====*====*====*

GENERAL DESCRIPTION
  GNP Proxy

  Copyright (c) 2015-2016 Qualcomm Technologies, Inc.
  All Rights Reserved.
  Confidential and Proprietary - Qualcomm Technologies, Inc.
=============================================================================*/

package com.qualcomm.location.osnp;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Properties;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import android.util.Log;
import android.util.Slog;

import android.os.IBinder;
import android.os.WorkSource;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.content.pm.PackageManager.NameNotFoundException;

import com.android.internal.location.ILocationProvider;
import com.android.internal.location.ProviderRequest;

import com.android.internal.content.PackageMonitor;

import android.location.LocationListener;
import android.location.LocationManager;
import android.location.Location;
import android.location.LocationRequest;

// Wraps GNP service
public class GNPProxy implements Handler.Callback {
    private Properties mProperties;
    private static final String PROPERTIES_FILE = "/etc/izat.conf";

    static {
        nativeOnClassLoad ();
    }

    public static GNPProxy createInstance (Context context) {
        if (mInstance == null) {
            mInstance = new GNPProxy (context);
            if (mInstance == null) {
                if (DEBUG) Log.d (TAG, "GNPProxy creation failure");
            }
        }
        return mInstance;
    }

    public static void screenLocations(boolean screenOn)
    {
        if (DEBUG) Log.d(TAG, "Screen location status: " + screenOn);
        mLocationScreenOn.set(screenOn);
    }

    // One can use this method to optionally set the worksource.
    public void setWorkSource (WorkSource workSource) {
        mHandler.obtainMessage
        (
            MSG_SET_WORKSOURCE,
            workSource
        ).sendToTarget ();
    }

    private GNPProxy (Context context) {
        mContext = context;
        mThread = new HandlerThread (TAG);
        mThread.start ();
        mHandler = new Handler (mThread.getLooper (), this);
        mCurrServiceState = mServiceUnknown;
        mLocationManager =
        (LocationManager) mContext.getSystemService (Context.LOCATION_SERVICE);

        nativeOnInstanceInit ();
    }

    // Methods Called by native code
    private boolean onInit () {
        //Read OSNLP package and action from izat.conf
        mProperties = new Properties();
        try {
            File file = new File(PROPERTIES_FILE);
            FileInputStream stream = new FileInputStream(file);
            mProperties.load(stream);
            stream.close();
        } catch (IOException e) {
            Log.e(TAG, "Could not open IZAT configuration file " + PROPERTIES_FILE);
        }

        String osnlpPackageName;
        osnlpPackageName = mProperties.getProperty("OSNLP_PACKAGE");
        if (osnlpPackageName != null) {
            osnlpPackageName = osnlpPackageName.trim();
            if(DEBUG) Log.d(TAG, "OSNLP_PACKAGE is " + osnlpPackageName);
            SERVICE_PACKAGE_NAMES_IN_CHINA[0] = osnlpPackageName;
        } else {
            Log.e(TAG, "OSNLP_PACKAGE is not defined in " + PROPERTIES_FILE);
        }


        String osnlpActionName;
        osnlpActionName = mProperties.getProperty("OSNLP_ACTION");
        if (osnlpActionName != null) {
            osnlpActionName = osnlpActionName.trim();
            if(DEBUG) Log.d(TAG, "OSNLP_ACTION is " + osnlpActionName);
            SERVICE_ACTION_NAMES[0] = osnlpActionName;
        } else {
            Log.e(TAG, "OSNLP_ACTION is not defined in " + PROPERTIES_FILE);
        }

        boolean hasChinaNlp = checkPackageExists(SERVICE_PACKAGE_NAMES_IN_CHINA);
        boolean hasGnp = checkPackageExists(SERVICE_PACKAGE_NAMES);
        boolean isBound = false;
        if (DEBUG)
            Log.d(TAG, "hasChinaNlp = " + hasChinaNlp + ",hasGnp = " + hasGnp);
        if (hasChinaNlp || hasGnp) {
            isBound = bindToService (
                hasChinaNlp ? SERVICE_PACKAGE_NAMES_IN_CHINA : SERVICE_PACKAGE_NAMES,
                SERVICE_ACTION_NAMES, mServiceConnection);
            if (hasChinaNlp && hasGnp)
                installCountryChangeReceiver();
        }

        if (!isBound) {
            Log.e (TAG, "Binding failed on GNPProxy Initialization");
            return isBound;
        }

        mHandler.sendEmptyMessage (MSG_SERVICE_BOUND);
        // Register package monitor
        mPackageMonitor.register (mContext, null, UserHandle.ALL, true);

        // Regsiter for user -change action
        installUserSwitchActionReceiver();

        try {
            LocationRequest request =
                LocationRequest.createFromDeprecatedProvider
                (
                    LocationManager.PASSIVE_PROVIDER,
                    0,
                    0,
                    false
                );
            request.setHideFromAppOps (true);
            mLocationManager.requestLocationUpdates
            (
                request,
                mLocationListener,
                mHandler.getLooper ()
            );
        } catch (RuntimeException e) {
            Log.e (TAG, "Cannot request for passive location updates");
        }
        return true;
    }

    private void onDestroy () {
        synchronized (GNPProxy.this.mLock) {
            mContext.unbindService(mServiceConnection);
        }
        mHandler.sendEmptyMessage(MSG_SERVICE_UNBOUND);
        mCurrServiceState = mServiceUnknown;
        mRequestWrappers.clear();
        mPackageMonitor.unregister();
        mThread.quitSafely();
        nativeOnInstanceDeinit();
        mInstance = null;
    }

    private void onSetRequest
    (
        String provider,
        long interval,
        float smallestDisplacement,
        int quality,
        int numUpdates
    ) {
        LocationRequest locationRequest =
            LocationRequest.createFromDeprecatedProvider
            (
                provider,
                interval,
                smallestDisplacement,
                numUpdates == 1
            );
        ProviderRequest providerRequest = new ProviderRequest ();
        providerRequest.interval = interval;
        providerRequest.reportLocation = true;
        providerRequest.locationRequests.add (locationRequest);

        if (mWorkSource == null) {
            mWorkSource = new WorkSource ();
        }

        mHandler.obtainMessage
        (
            MSG_SET_REQUEST,
            new RequestWrapper (providerRequest, mWorkSource)
        ).sendToTarget ();
    }

    private void onStopRequest () {
        ProviderRequest providerRequest = new ProviderRequest ();

        if (mWorkSource == null) {
            mWorkSource = new WorkSource ();
        }

        mHandler.obtainMessage
        (
            MSG_STOP_REQUEST,
            new RequestWrapper (providerRequest, mWorkSource)
        ).sendToTarget ();
    }

    private static class RequestWrapper {
        public ProviderRequest request;
        public WorkSource source;
        public RequestWrapper
        (
            ProviderRequest request,
            WorkSource source
        ) {
            this.request = request;
            this.source = source;
        }
    }

    // Helpers
    private void installUserSwitchActionReceiver()
    {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_USER_SWITCHED);

        // use sticky broadcast receiver first to get the current user.
        Intent userIntent =
            mContext.registerReceiverAsUser(null, UserHandle.ALL, intentFilter, null, null);
        if (userIntent != null) {
            mCurrentUserId
                = userIntent.getIntExtra(Intent.EXTRA_USER_HANDLE, UserHandle.USER_CURRENT);
        }

        // register Receiver for ACTION_USER_SWITCHED
        mContext.registerReceiverAsUser(mUserChangeReceiver, UserHandle.ALL,
            intentFilter, null, null);

        if (DEBUG)
            Log.d(TAG, "Registered for ACTION_USER_SWITCHED CurrentUserId = " + mCurrentUserId);
    }

    private void installCountryChangeReceiver()
    {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(COUNTRY_ACTION);
        mContext.registerReceiverAsUser(mCountryChangeReceiver,
            UserHandle.ALL, intentFilter, null, mHandler);
        if (DEBUG)
            Log.d(TAG, "Registered for CountryChangeReceiver");
    }

    private boolean checkPackageExists(String [] packageNames)
    {
        String info;
        for(int i = 0; i < packageNames.length; i++) {
            try {
                info = mContext.getPackageManager().getPackageInfo(
                    packageNames [i], 0).versionName;
                if (info != null || !info.isEmpty())
                    return true;
            } catch (NameNotFoundException e) {
            }
        }
        return false;
    }

    private boolean bindToService
    (
        String [] packageNames,
        String [] actionNames,
        ServiceConnection serviceConnection
    ) {

        boolean isBound = false;
        for (int i = 0;
             i < packageNames.length && i < actionNames.length;
             i++) {

            Intent intent = new Intent (actionNames [i]);
            intent.setPackage (packageNames [i]);

            if (DEBUG) Log.d (TAG, "binding to service in " + packageNames [i] +
                    " with action " + actionNames [i]);
            isBound =
                mContext.bindServiceAsUser
                (
                    intent,

                    serviceConnection,

                    Context.BIND_AUTO_CREATE |
                    Context.BIND_NOT_FOREGROUND |
                    Context.BIND_ALLOW_OOM_MANAGEMENT |
                    Context.BIND_NOT_VISIBLE,

                    UserHandle.CURRENT
                );
            if (isBound) {
                if (DEBUG) Log.d (TAG, "bind success");
                break;
            } else {
                if (DEBUG) Log.d (TAG, "bind failed");
            }
        }
        return isBound;
    }

    private void startOnConnect () {
        boolean clearRequests = false;
        synchronized (GNPProxy.this.mLock) {
            if (mProvider != null) {
                try {
                    for (RequestWrapper rw : mRequestWrappers) {
                        mProvider.setRequest (rw.request, rw.source);
                    }
                } catch (RemoteException e) {
                    Log.w (TAG, e);
                }
                clearRequests = true;
            }
        }

        if (clearRequests) {
            mRequestWrappers.clear ();
        }
    }

    private boolean locationIsForScreening (Location location) {
        Bundle extras = location.getExtras ();
        if (extras != null) {
            if (extras.containsKey ("com.qualcomm.location.nlp:screen")) {
                return true;
            }
        }
        return false;
    }

    private Location clearScreeningMarker(Location location) {
        Bundle extras = location.getExtras();
        if (extras != null) {
            extras.remove("com.qualcomm.location.nlp:screen");
            location.setExtras(extras);
        }
        return location;
    }


    // Message handler
    public boolean handleMessage (Message msg) {

        int msgID = msg.what;
        if (DEBUG) Log.d (TAG, "handleMessage what - " + msgID);

        switch (msgID) {
            case MSG_SERVICE_UNBOUND:
            {
                setState (mServiceUnbound);
                break;
            }
            case MSG_SERVICE_BOUND:
            {
                setState (mServiceBound);
                break;
            }
            case MSG_SERVICE_CONNECTED:
            {
                try {
                    synchronized (GNPProxy.this.mLock) {
                        mProvider.enable ();
                    }
                } catch (RemoteException e) {
                    Log.w (TAG, e);
                }

                setState (mServiceConnected);
                startOnConnect ();
                break;
            }
            case MSG_SET_REQUEST:
            case MSG_STOP_REQUEST:
            {
                RequestWrapper wrapper = (RequestWrapper) msg.obj;
                mCurrServiceState.setRequest (wrapper);
                break;
            }
            case MSG_LOCATION_CHANGED:
            {
                int networkPositionSource = -1;
                if (DEBUG) Log.d (TAG, "MSG_LOCATION_CHANGED");
                Location location = (Location)msg.obj;

                if (location != null) {

                    if (location.getExtras() != null) {
                        String strSource = location.getExtras().getString(NETWORK_LOCATION_TYPE_KEY, "");
                        if (strSource != null) {
                            if (strSource.equalsIgnoreCase("cell")) {
                                networkPositionSource = 0;
                            } else if (strSource.equalsIgnoreCase("wifi")) {
                                networkPositionSource = 1;
                            }
                        }
                    }

                    nativeOnLocationChanged
                    (
                        true,
                        location.getTime (),
                        true,
                        location.getElapsedRealtimeNanos (),
                        true,
                        location.getLatitude (),
                        true,
                        location.getLongitude (),
                        location.hasAccuracy (),
                        location.getAccuracy (),
                        location.hasAltitude (),
                        location.getAltitude (),
                        location.hasBearing (),
                        location.getBearing (),
                        location.hasSpeed (),
                        location.getSpeed (),
                        (networkPositionSource != -1 )? true : false,
                        networkPositionSource
                    );
                }
                break;
            }
            case MSG_SERVICE_DISCONNECTED:
            {
                setState (mServiceDisconnected);
                break;
            }
            case MSG_RECONNECT_GNP:
            {
                int retryNumber = MAX_GNP_RETRIES;

                synchronized (GNPProxy.this.mLock) {
                    if (mProvider == null) {
                        if (bindToService (SERVICE_PACKAGE_NAMES,
                                           SERVICE_ACTION_NAMES,
                                           mServiceConnection)) {
                            setState (mServiceBound);
                        } else {
                            retryNumber = (Integer)msg.obj;
                        }
                    }
                }

                if (retryNumber < MAX_GNP_RETRIES) {
                    Message message = Message.obtain(mHandler,
                                                     MSG_RECONNECT_GNP,
                                                     retryNumber+1);
                    mHandler.sendMessageDelayed (message, retryNumber+2000);
                } else {
                    Log.e (TAG, "Give up on GNP after " + MAX_GNP_RETRIES + " retries!");
                }
                break;
            }
            default:
            {
                Log.w (TAG, "Unhandled Message " + msg.what);
            }
        }
        return true;
    }


    // native method declarations.
    private static native void nativeOnClassLoad ();
    private native void nativeOnInstanceInit ();
    private native void nativeOnInstanceDeinit ();
    private native void nativeOnStatusChanged (int status);
    private native void nativeOnLocationChanged
    (
        boolean hasTime,
        long time,
        boolean hasElapsedRealTimeNanos,
        long elapsedRealTimeNanos,
        boolean hasLatitude,
        double latitude,
        boolean hasLongitude,
        double longitude,
        boolean hasAccuracy,
        float accuracy,
        boolean hasAltitude,
        double altitude,
        boolean hasBearing,
        float bearing,
        boolean hasSpeed,
        float speed,
        boolean hasNetworkPositionSource,
        int networkPositionSource
    );

    // Nested types
    private void setState (IServiceState state) {
        mCurrServiceState = state;
    }
    private interface IServiceState {
        public void setRequest (RequestWrapper requestWrapper);
    }

    private abstract class ServiceState implements IServiceState {
        public void setRequest (RequestWrapper requestWrapper) {
            if (DEBUG) Log.d (TAG, "setRequest : " + getClass ().getName ());
            mRequestWrappers.add (requestWrapper);
        }
    }

    private class ServiceUnknown extends ServiceState {
        public void setRequest (RequestWrapper requestWrapper) {
            super.setRequest (requestWrapper);
        }
    }

    private class ServiceUnbound extends ServiceState {
        public void setRequest (RequestWrapper requestWrapper) {
            if (DEBUG) Log.d (TAG, "setRequest : " + getClass ().getName ());
            super.setRequest (requestWrapper);
        }
    }

    private class ServiceBound extends ServiceState {
        public void setRequest (RequestWrapper requestWrapper) {
            super.setRequest (requestWrapper);
        }
    }

    private class ServiceConnected extends ServiceState {
        public void setRequest (RequestWrapper requestWrapper) {
            super.setRequest (requestWrapper);
            startOnConnect ();
        }
    }

    private class ServiceDisconnected extends ServiceState {
        public void setRequest (RequestWrapper requestWrapper) {
            if (DEBUG) Log.d (TAG, "setRequest : " + getClass ().getName ());
            super.setRequest (requestWrapper);
        }
    }


    // Constant data members
    private static final int        MSG_SERVICE_UNBOUND       = 0;
    private static final int        MSG_SERVICE_BOUND         = 1;
    private static final int        MSG_SERVICE_CONNECTED     = 2;
    private static final int        MSG_SET_REQUEST           = 3;
    private static final int        MSG_LOCATION_CHANGED      = 4;
    private static final int        MSG_STOP_REQUEST          = 5;
    private static final int        MSG_SERVICE_DISCONNECTED  = 6;
    private static final int        MSG_SET_WORKSOURCE        = 7;
    private static final int        MSG_RECONNECT_GNP         = 8;

    private static final int        MAX_GNP_RETRIES           = 10;

    private static final String     TAG   = "GNPProxy";
    private static final boolean    DEBUG = Log.isLoggable (TAG, Log.DEBUG);

    private static final String []  SERVICE_PACKAGE_NAMES =
    {
        "com.google.android.gms",
        "com.google.android.location"
    };
    private static final String []  SERVICE_PACKAGE_NAMES_IN_CHINA =
    {
        "com.baidu.map.location"
    };
    private static final String []  SERVICE_ACTION_NAMES =
    {
        "com.android.location.service.v3.NetworkLocationProvider",
        "com.android.location.service.v2.NetworkLocationProvider"
    };

    private static final String NETWORK_LOCATION_TYPE_KEY =
        "networkLocationType";

    // non constant data members
    private static GNPProxy         mInstance           = null;
    private static AtomicBoolean    mLocationScreenOn = new AtomicBoolean(false);
    private HandlerThread           mThread;
    private Handler                 mHandler;
    private Context                 mContext;
    private WorkSource              mWorkSource         = null;
    private LocationManager         mLocationManager;
    private Object                  mLock               = new Object ();
    private ILocationProvider       mProvider;
    private IServiceState           mCurrServiceState;
    private List <RequestWrapper>   mRequestWrappers    = new ArrayList <RequestWrapper> ();
    private ServiceUnknown          mServiceUnknown = new ServiceUnknown ();
    private ServiceBound            mServiceBound = new ServiceBound ();
    private ServiceUnbound          mServiceUnbound = new ServiceUnbound ();
    private ServiceConnected        mServiceConnected = new ServiceConnected ();
    private ServiceDisconnected     mServiceDisconnected = new ServiceDisconnected ();
    private int mCurrentUserId = UserHandle.USER_CURRENT;
    private static final String COUNTRY_ACTION = "com.android.location.osagent.COUNTRY_SELECT_ACTION";
    private boolean mDeviceInChina = false;


    // Implemented Callback objects
    private ServiceConnection mServiceConnection = new ServiceConnection () {

        public void onServiceConnected (ComponentName name, IBinder binder) {
            synchronized (GNPProxy.this.mLock) {
                mProvider = ILocationProvider.Stub.asInterface (binder);
            }
            mHandler.removeMessages (MSG_RECONNECT_GNP);
            mHandler.sendEmptyMessage (MSG_SERVICE_CONNECTED);

            if (DEBUG) {
                Log.d (TAG, "Service Connected");
            }
        }

        public void onServiceDisconnected (ComponentName name) {
            // We may get this call if
            // 1.   Remote process hosting GNP crashes.
            // 2.   Package in which GNP service is present gets updated.
            // In case of 1 above, according to the android API documentation, we should
            // automatically get a call to onServiceConnected when the service starts running again.
            // However it is noticed that the behavior for a remote service is not as documented and
            // we will never get a call to onServiceConnected when the service starts running again.
            Log.d (TAG, "GNP Service Disconnected");
            synchronized (GNPProxy.this.mLock) {
                mProvider = null;
            }
            mHandler.sendEmptyMessage (MSG_SERVICE_UNBOUND);
            Log.w (TAG, "Remote service GNP will be reconnected when ready");
            mHandler.sendEmptyMessage (MSG_SERVICE_DISCONNECTED);
        }
    };


    private final PackageMonitor mPackageMonitor = new PackageMonitor () {

        public void onPackageUpdateStarted (String packageName, int uid) {

            if (DEBUG)
                Log.d (TAG, "onPackageUpdateStarted " + packageName);

            synchronized (GNPProxy.this.mLock) {
                if (mProvider == null) {
                    for (String p : SERVICE_PACKAGE_NAMES) {
                        if (p.equals (packageName)) {
                            Log.w (TAG, "GNP Package Update Started " + packageName);
                            mHandler.removeMessages (MSG_RECONNECT_GNP);
                            break;
                        }
                    }
                }
            }
        }

        public void onPackageUpdateFinished (String packageName, int uid) {

            if (DEBUG)
                Log.d (TAG, "onPackageUpdateFinished " + packageName);

            synchronized (GNPProxy.this.mLock) {
                if (mProvider == null) {
                    for (String p : SERVICE_PACKAGE_NAMES) {
                        if (p.equals (packageName)) {
                            Log.w (TAG, "GNP Package update finished " + packageName);
                            // reconnect now since update has finished
                            mHandler.obtainMessage (MSG_RECONNECT_GNP, 1).sendToTarget ();
                            break;
                        }
                    }
                }
            }
        }
    };

    private LocationListener mLocationListener = new LocationListener () {

        public void onLocationChanged (Location location) {

            if (DEBUG)
                Log.d (TAG, "onLocationChanged: " + location.getProvider ());

            if (LocationManager.NETWORK_PROVIDER.equals
                ( location.getProvider ())) {

                if (mLocationScreenOn.get() == true) {
                    if (locationIsForScreening (location)) {
                        if (DEBUG) Log.d (TAG, "location is for screening");
                        location = clearScreeningMarker (location);
                        mHandler.obtainMessage (MSG_LOCATION_CHANGED, location)
                                .sendToTarget ();
                    }
                } else {
                    mHandler.obtainMessage (MSG_LOCATION_CHANGED, location)
                                .sendToTarget ();
                }
            }
        }

        public void onStatusChanged
        (
            String provider,
            int status,
            Bundle extras
        ) {
            if (DEBUG) Log.d (TAG, "status: " + status);
        }

        public void onProviderEnabled (String provider) {
            if (DEBUG) Log.d (TAG, "provider re-enabled: " + provider);
        }

        public void onProviderDisabled (String provider) {
            if (DEBUG) Log.d (TAG, "provider disabled: " + provider);
        }
    };

    private final BroadcastReceiver mUserChangeReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_USER_SWITCHED)) {
                mCurrentUserId =
                    intent.getIntExtra(Intent.EXTRA_USER_HANDLE, UserHandle.USER_CURRENT);
                if (DEBUG) Log.d(TAG, "Action user switched: " + mCurrentUserId);

                synchronized (GNPProxy.this.mLock) {
                    if (mProvider != null) {
                        mContext.unbindService (mServiceConnection);
                        mProvider = null;
                        mHandler.sendEmptyMessage (MSG_SERVICE_UNBOUND);
                        mHandler.obtainMessage (MSG_RECONNECT_GNP, 1).sendToTarget ();
                    }
                }
            }
        }
    };

    private final BroadcastReceiver mCountryChangeReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            boolean country_status = intent.getBooleanExtra("COUNTY_SELECT_STATUS", false);
            if (mDeviceInChina != country_status) {
                mDeviceInChina = country_status;
                if (DEBUG)
                    Log.d(TAG, "Receive CountryChange Message, mDeviceInChina = " + mDeviceInChina);
                if (mCurrServiceState == mServiceConnected) {
                    mContext.unbindService (mServiceConnection);
                    setState (mServiceUnbound);
                }
                bindToService (mDeviceInChina ? SERVICE_PACKAGE_NAMES_IN_CHINA : SERVICE_PACKAGE_NAMES,
                    SERVICE_ACTION_NAMES, mServiceConnection);
                setState (mServiceBound);
            }
        }
    };

}
