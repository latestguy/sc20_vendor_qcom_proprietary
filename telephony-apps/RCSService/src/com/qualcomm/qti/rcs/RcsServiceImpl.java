/**
 * Copyright (c) 2016 Qualcomm Technologies, Inc.
 * All Rights Reserved.
 * Confidential and Proprietary - Qualcomm Technologies, Inc.
 **/

package com.qualcomm.qti.rcs;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.Manifest;
import android.os.Binder;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;

import com.jio.join.enrichedcall.RichCallsManager;
import com.jio.join.enrichedcall.RichCallsManagerImpl;
import com.jio.join.enrichedcall.cb.EnrichedCallCallbacks;
import com.jio.join.enrichedcall.library.CallComposerData;
import com.jio.join.enrichedcall.library.EnrichedCallState;

import com.qualcomm.qti.rcs.utils.RcsUtils;
import com.qualcomm.qti.rcs.utils.StaticMapDownloader;

import java.util.concurrent.ConcurrentHashMap;

import org.codeaurora.rcscommon.EnrichedCallUpdateCallback;
import org.codeaurora.rcscommon.FetchImageCallBack;
import org.codeaurora.rcscommon.IncomingEnrichedCallCallback;
import org.codeaurora.rcscommon.INewPostCallCallback;
import org.codeaurora.rcscommon.IRCSService;
import org.codeaurora.rcscommon.NewCallComposerCallback;
import org.codeaurora.rcscommon.PostCallCapabilitiesCallback;
import org.codeaurora.rcscommon.RcsManager;
import org.codeaurora.rcscommon.RichCallCapabilitiesCallback;
import org.codeaurora.rcscommon.SessionStateUpdateCallback;

/**
 * RcsServiceImpl class is will handle the callbacks and intract with the
 * jiojoin lib.
 */
public class RcsServiceImpl {

    private static final boolean DBG = RcsService.DBG;
    private static final String TAG = "RcsServiceImpl";

    private RichCallsManagerImpl mRichCallMgrImpl = (RichCallsManagerImpl) RichCallsManager
            .getInstance();

    /*
     * mCallbackHashMap hashmap is used to maintain the register callback from clients vs
     * the RichCallsManagerImpl register callbacks.
     * Once a client register for a callback then a corresponding new RJIL callback is created
     * and registered. And saved in mCallbackHashMap hashmap. Once a client unregister the callback
     * then a corresponding RJIL hashmap will be unregistered and the callbacks will be removed from
     * this mCallbackHashMap hashmap.
     */
    private ConcurrentHashMap<Object, Object> mCallbackHashMap
           = new ConcurrentHashMap<Object, Object>();

    private Context mContext;

    /**
     * Constructor of RcsServiceImpl class
     *
     * @param Context.
     */
    public RcsServiceImpl(Context context) {
        this.mContext = context;
    }

    /**
     * get the IRCSService binder to publish system service.
     *
     * @return IRCSService binder.
     */
    public IRCSService.Stub getBinder() {
        return mRcsServiceBinder;
    }

    /**
     * Method initialize connection to remote service of RJIL
     */
    public void initializeRCS() {
        mRichCallMgrImpl.initialize(mContext);
    }

    private IRCSService.Stub mRcsServiceBinder = new IRCSService.Stub() {
        @Override
        public boolean isSessionRegistered(int subId) {
            enforceAccessPermission();
            logIn("isSessionRegistered");
            boolean isSessionRegistered = mRichCallMgrImpl != null ?
                    mRichCallMgrImpl.isSessionRegistered() : false;
            logOut("isSessionRegistered : " + isSessionRegistered);
            return isSessionRegistered;
        }

        @Override
        public void makePostCall(String phoneNumber, final INewPostCallCallback callback,
                int subId) {
            enforceAccessPermission();
            logIn("makePostCall : phoneNumber: " + phoneNumber + " INewPostCallCallback : "
                    + callback);
            if (mRichCallMgrImpl == null) {
                return;
            }
            mRichCallMgrImpl.makePostCall(phoneNumber,
                    new EnrichedCallCallbacks.NewPostCallCallback() {

                @Override
                public void onNewPostCall(boolean success) {
                    logOut("onNewPostCall : " + success);
                    try {
                        callback.onNewPostCall(success);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            });

        }

        @Override
        public void fetchPostCallCapabilities(String phoneNumber,
                final PostCallCapabilitiesCallback callback, int subId) {
            enforceAccessPermission();
            logIn("fetchPostCallCapabilities : " + phoneNumber);
            if (mRichCallMgrImpl == null) {
                return;
            }
            mRichCallMgrImpl.fetchPostCallCapabilities(phoneNumber,
                    new EnrichedCallCallbacks.PostCallCapabilitiesCallback() {

                        @Override
                        public void onPostCallCapabilitiesFetch(boolean isCapable) {
                            logOut("onPostCallCapabilitiesFetch : " + isCapable);
                            try {
                                callback.onPostCallCapabilitiesFetch(isCapable);
                            } catch (RemoteException e) {
                                e.printStackTrace();
                            }
                        }
                    });

        }

        @Override
        public void subscribeEnrichedCallUpdate(
                final EnrichedCallUpdateCallback callback, int subId) {
            enforceAccessPermission();
            logIn("subscribeEnrichedCallUpdate : " + callback);
            if (mRichCallMgrImpl == null) {
                return;
            }
            EnrichedCallCallbacks.EnrichedCallUpdateCallback rcsCallback
                    = new EnrichedCallCallbacks.EnrichedCallUpdateCallback() {

                @Override
                public void onEnrichedCallUpdate(String phoneNumber, EnrichedCallState state) {
                    logOut("onEnrichedCallUpdate : phoneNumber : " + phoneNumber
                            + " EnrichedCallState: " + state);
                    try {
                        callback.onEnrichedCallUpdate(phoneNumber,
                                RcsUtils.getLocalEnrichedCallState(state));
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            };
            mRichCallMgrImpl.subscribeEnrichedCallUpdate(rcsCallback);
            mCallbackHashMap.put(callback, rcsCallback);
            log("subscribeEnrichedCallUpdate : DONE");
        }

        @Override
        public void unsubscribeEnrichedCallUpdate(EnrichedCallUpdateCallback callback,
                int subId) {
            enforceAccessPermission();
            logIn("unsubscribeEnrichedCallUpdate : " + callback);
            if (mRichCallMgrImpl == null || callback == null
                    || mCallbackHashMap.get(callback) == null) {
                return;
            }
            mRichCallMgrImpl.unsubscribeEnrichedCallUpdate((EnrichedCallCallbacks
                    .EnrichedCallUpdateCallback) mCallbackHashMap.get(callback));
            mCallbackHashMap.remove(callback);
            log("unsubscribeEnrichedCallUpdate : DONE");
        }

        @Override
        public void subscribeSessionStateUpdate(
                final SessionStateUpdateCallback callback, int subId) {
            enforceAccessPermission();
            logIn("subscribeSessionStateUpdate : " + callback);
            if (mRichCallMgrImpl == null) {
                return;
            }
            EnrichedCallCallbacks.SessionStateUpdateCallback rcsCallback
                    = new EnrichedCallCallbacks.SessionStateUpdateCallback() {

                @Override
                public void onSessionStateUpdate(boolean isRegistered) {
                    logOut("onSessionStateUpdate : " + isRegistered);
                    try {
                        callback.onSessionStateUpdate(isRegistered);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            };
            mRichCallMgrImpl.subscribeSessionStateUpdate(rcsCallback);
            mCallbackHashMap.put(callback, rcsCallback);
            log("onSessionStateUpdate : DONE");
        }

        @Override
        public void unsubscribeSessionStateUpdate(SessionStateUpdateCallback callback,
                int subId) {
            enforceAccessPermission();
            logIn("unsubscribeSessionStateUpdate : " + callback);
            if (mRichCallMgrImpl == null || callback == null
                    || mCallbackHashMap.get(callback) == null) {
                return;
            }
            mRichCallMgrImpl.unsubscribeSessionStateUpdate((EnrichedCallCallbacks
                    .SessionStateUpdateCallback) mCallbackHashMap.get(callback));
            mCallbackHashMap.remove(callback);
            log("unsubscribeSessionStateUpdate : DONE");
        }

        @Override
        public void subscribeIncomingEnrichedCall(
                final IncomingEnrichedCallCallback callback, int subId) {
            enforceAccessPermission();
            logIn("subscribeIncomingEnrichedCall : " + callback);
            if (mRichCallMgrImpl == null) {
                return;
            }
            EnrichedCallCallbacks.IncomingEnrichedCallCallback rcsCallback
                    = new EnrichedCallCallbacks.IncomingEnrichedCallCallback() {

                @Override
                public void onIncomingEnrichedCall(CallComposerData data) {
                    logOut("onIncomingEnrichedCall : " + data.toString());
                    try {
                        callback.onIncomingEnrichedCall(convertlocalCallComposerData(data));
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            };
            mRichCallMgrImpl.subscribeIncomingEnrichedCall(rcsCallback);
            mCallbackHashMap.put(callback, rcsCallback);
            log("subscribeIncomingEnrichedCall : DONE");

        }

        @Override
        public void unsubscribeIncomingEnrichedCall(
                IncomingEnrichedCallCallback callback, int subId) {
            enforceAccessPermission();
            logIn("unsubscribeIncomingEnrichedCall : " + callback);
            if (mRichCallMgrImpl == null || callback == null
                    || mCallbackHashMap.get(callback) == null) {
                return;
            }
            mRichCallMgrImpl.unsubscribeIncomingEnrichedCall((EnrichedCallCallbacks
                    .IncomingEnrichedCallCallback) mCallbackHashMap.get(callback));
            mCallbackHashMap.remove(callback);
            log("unsubscribeIncomingEnrichedCall : DONE");
        }

        @Override
        public void makeEnrichedCall(String phoneNumber, final NewCallComposerCallback callback,
                int subId) {
            enforceAccessPermission();
            logIn("makeEnrichedCall : " + phoneNumber);

            if (mRichCallMgrImpl == null) {
                return;
            }
            mRichCallMgrImpl.makeEnrichedCall(phoneNumber,
                    new EnrichedCallCallbacks.NewCallComposerCallback() {
                        public void onNewCallComposer(CallComposerData data) {
                            logOut("onNewCallComposer : " + data);
                            try {
                                callback.onNewCallComposer(convertlocalCallComposerData(data));
                            } catch (RemoteException e) {
                                log("ERROR: " + e.getMessage());
                                e.printStackTrace();
                            }
                        }
                    });
        }

        @Override
        public void fetchEnrichedCallCapabilities(final String phoneNumber,
                final RichCallCapabilitiesCallback callback, int subId) {
            enforceAccessPermission();
            logIn("fetchEnrichedCallCapabilities");

            if (mRichCallMgrImpl == null) {
                return;
            }
            mRichCallMgrImpl.fetchEnrichedCallCapabilities(phoneNumber,
                    new EnrichedCallCallbacks.RichCallCapabilitiesCallback() {

                        @Override
                        public void onRichCallCapabilitiesFetch(boolean isCapable) {
                            logOut("onRichCallCapabilitiesFetch : number : " + phoneNumber
                                    + " isCapable: " + isCapable);
                            try {
                                callback.onRichCallCapabilitiesFetch(isCapable);
                            } catch (RemoteException e) {
                                log("ERROR: " + e.getMessage());
                                e.printStackTrace();
                            }
                        }
                    });
        }

        @Override
        public void fetchStaticMap(double lat, double lon, int width, int height,
                final FetchImageCallBack callback) {
            enforceAccessPermission();
            logIn("fetchStaticMap");
            StaticMapDownloader.StaticMapParam param = new StaticMapDownloader.StaticMapParam(lat,
                    lon, width, height);

            StaticMapDownloader downloader = new StaticMapDownloader(
                    new StaticMapDownloader.StaticMapDownloadListener() {
                        @Override
                        public void onStaticMapDownloaded(byte[] arr) {
                            logOut("onStaticMapDownloaded : " + arr);
                            try {
                                callback.onImageFetched(arr);
                            } catch (RemoteException e) {
                                log("ERROR: " + e.getMessage());
                                e.printStackTrace();
                            }
                        }
                    }, getMapKey());
            downloader.execute(param, null, null);
        }

        /**
         * Convert the jio CallComposerData to local
         * org.codeaurora.rcscommon.CallComposerData object.
         *
         * @param CallComposerData
         * @return converted org.codeaurora.rcscommon.CallComposerData
         */
        private org.codeaurora.rcscommon.CallComposerData convertlocalCallComposerData(
                CallComposerData data) {
            if (data == null) {
                return new org.codeaurora.rcscommon.CallComposerData(null, null, null, -1d, -1d
                        , org.codeaurora.rcscommon.CallComposerData.PRIORITY.NORMAL, null);
            }
            return new org.codeaurora.rcscommon.CallComposerData(data.getPhoneNumber()
                    , data.getSubject(), data.getImage(), (data.getLocationLatitude() == null ?
                    -1d : data.getLocationLatitude().doubleValue()),
                    (data.getLocationLongitude() == null ? -1d : data.getLocationLongitude()
                    .doubleValue()), data.getPriority() == CallComposerData.PRIORITY.NORMAL
                    ? org.codeaurora.rcscommon.CallComposerData.PRIORITY.NORMAL
                    : org.codeaurora.rcscommon.CallComposerData.PRIORITY.HIGH, null);
        }

    };

    /**
    * A Util to check if the remote caller(app) is granted with
    * the access RcsService permission.
    */
    private void enforceAccessPermission() {
        mContext.enforceCallingPermission(RcsService.ACCESS_PERMISSION,
                "Application required Access permission to access RcsService");
    }

    /**
     * get the google map static image map key from the string resource.
     *
     * @return String, mapkey string.
     */
    private String getMapKey() {
       return mContext.getString(R.string.map_key);
    }

    private static void log(String msg) {
        if (DBG) {
            android.util.Log.d(TAG, msg);
        }
    }

    private void logIn(String msg) {
        log(">>>> " + msg);
    }

    private void logOut(String msg) {
        log("<<<< " + msg);
    }
}
