/*====*====*====*====*====*====*====*====*====*====*====*====*====*====*====*
  Copyright (c) 2016 Qualcomm Technologies, Inc.
  All Rights Reserved.
  Confidential and Proprietary - Qualcomm Technologies, Inc.
=============================================================================*/

#include <stdint.h>
#include <IzatRemoteApi.h>
#include <mq_client/IPCMessagingProxy.h>
#include <gps_extended_c.h>
#include <IzatTypes.h>
#include <algorithm>
#include <vector>

using namespace std;
using namespace qc_loc_fw;
using namespace izat_manager;

#define POS_LOC_FLAGS                    "GPS-LOC-FLAGS"
#define POS_LATITUDE                     "LATITUDE"
#define POS_LONGITUDE                    "LONGITUDE"
#define POS_HORIZONTAL_ACCURACY          "HORIZONTAL-ACCURACY"
#define POS_ALTITUDE                     "ALTITUDE"
#define POS_UTC_TIME                     "UTC-TIME-STAMP"
#define POS_VERT_UNCERT                  "VERT-UNCERT"
#define POS_BEARING                      "BEARING"
#define POS_SPEED                        "SPEED"
#define POS_SOURCE                       "POSITION-SOURCE"

#define POS_LOC_EXT_FLAGS                "GPS-LOC-EXT-FLAGS"
#define POS_ALT_MEAN_SEA_LEVEL           "ALT-MEAN-SEA-LEVEL"
#define POS_PDOP                         "PDOP"
#define POS_HDOP                         "HDOP"
#define POS_VDOP                         "VDOP"
#define POS_MAGNETIC_DEVIATION           "MAGNETIC-DEVIATION"
#define POS_VERT_UNCERT                  "VERT-UNCERT"
#define POS_SPEED_UNCERT                 "SPEED-UNCERT"
#define POS_BEARING_UNCERT               "BEARING-UNCERT"
#define POS_HOR_RELIABILITY              "HOR-RELIABILITY"
#define POS_VERT_RELIABILITY             "VERT-RELIABILITY"
#define POS_HOR_ELIP_UNC_MAJOR           "HOR-ELIP-UNC-MAJOR"
#define POS_HOR_ELIP_UNC_MINOR           "HOR-ELIP-UNC-MINOR"
#define POS_HOR_ELIP_UNC_AZIMUTH         "HOR-ELIP-UNC-AZIMUTH"


namespace izat_remote_api {

class IzatNotifierProxy : public IIPCMessagingResponse {
private:
    const char* const mName;
    IPCMessagingProxy* const mIPCProxy;
    vector<IzatNotifier*> mNotifiers;
    inline IzatNotifierProxy(const char* const name, IPCMessagingProxy* ipcProxy) :
        mName(name), mIPCProxy(ipcProxy), mNotifiers() {
    }
    inline ~IzatNotifierProxy() {}
public:
    static IzatNotifierProxy* get(const char* const name) {
        IPCMessagingProxy* ipcProxy = IPCMessagingProxy::getInstance();
        IzatNotifierProxy* notifierProxy = (IzatNotifierProxy*)
                                           ipcProxy->getResponseObj(name);
        if (notifierProxy == nullptr) {
            notifierProxy = new IzatNotifierProxy(name, ipcProxy);
            ipcProxy->registerResponseObj(name, notifierProxy);
        }
        return notifierProxy;
    }
    static void drop(IzatNotifierProxy* notifierProxy) {
        if (notifierProxy->mNotifiers.size() == 0) {
            notifierProxy->mIPCProxy->unregisterResponseObj(notifierProxy->mName);
            delete notifierProxy;
        }
    }
    inline void addNotifier(IzatNotifier* notifier, OutPostcard* const subCard) {
        mNotifiers.push_back(notifier);
        if (mNotifiers.size() == 1) {
            mIPCProxy->sendMsg(subCard, "IZAT-MANAGER");
        }
    }
    inline void removeNotifier(IzatNotifier* notifier) {
        vector<IzatNotifier*>::iterator it =
            find(mNotifiers.begin(), mNotifiers.end(), notifier);
        if (it != mNotifiers.end()) {
            mNotifiers.erase(it);
        }
    }
    inline virtual void handleMsg(InPostcard * const in_card) final {
        for (auto const& notifier : mNotifiers) {
            notifier->handleMsg(in_card);
        }
    }
};


    IzatNotifier::IzatNotifier(const char* const name, OutPostcard* const subCard) :
    mNotifierProxy(IzatNotifierProxy::get(name)) {
    mNotifierProxy->addNotifier(this, subCard);
}

IzatNotifier::~IzatNotifier() {
    mNotifierProxy->removeNotifier(this);
    IzatNotifierProxy::drop(mNotifierProxy);
}

// a static method outside LocationUpdater class.
static OutPostcard* getLocationUpdaterSubscriptionCard() {
    OutPostcard* card = OutPostcard::createInstance();
    if (nullptr != card) {
        card->init();
        card->addString("TO", "IZAT-MANAGER");
        card->addString("FROM", LocationUpdater::sName);
        card->addString("REQ", "PASSIVE-LOCATION");
        card->addUInt16("LISTENS-TO", IZAT_STREAM_ALL);
        card->finalize();
    }
    return card;
}

const char* const LocationUpdater::sName = "LOCATION-UPDATE";
const char* const LocationUpdater::sInfoTag = "INFO";
const char* const LocationUpdater::sLatTag = "LATITUDE";
const char* const LocationUpdater::sLonTag = "LONGITUDE";
const char* const LocationUpdater::sAccuracyTag = "HORIZONTAL-ACCURACY";
OutPostcard* const LocationUpdater::sSubscriptionCard =
    getLocationUpdaterSubscriptionCard();

void LocationUpdater::handleMsg(InPostcard * const in_msg) {
    const char* info = nullptr;
    in_msg->getString(sInfoTag, &info);
    if (0 == strncmp(sName, info, sizeof(sInfoTag)-1)) {
        UlpLocation ulpLoc;
        GpsLocationExtended locExtended;
        memset(&ulpLoc, 0, sizeof(ulpLoc));
        memset(&locExtended, 0, sizeof(locExtended));

        in_msg->getUInt16(POS_LOC_FLAGS, ulpLoc.gpsLocation.flags);
        in_msg->getDouble(POS_LATITUDE, ulpLoc.gpsLocation.latitude);
        in_msg->getDouble(POS_LONGITUDE, ulpLoc.gpsLocation.longitude);
        in_msg->getFloat(POS_HORIZONTAL_ACCURACY, ulpLoc.gpsLocation.accuracy);
        in_msg->getDouble(POS_ALTITUDE, ulpLoc.gpsLocation.altitude);
        in_msg->getInt64(POS_UTC_TIME, (long long&)ulpLoc.gpsLocation.timestamp);

        in_msg->getFloat(POS_BEARING, ulpLoc.gpsLocation.bearing);
        in_msg->getFloat(POS_SPEED, ulpLoc.gpsLocation.speed);
        in_msg->getUInt16(POS_SOURCE, ulpLoc.position_source);

        in_msg->getUInt16(POS_LOC_EXT_FLAGS, locExtended.flags);
        in_msg->getFloat(POS_ALT_MEAN_SEA_LEVEL, locExtended.altitudeMeanSeaLevel);
        in_msg->getFloat(POS_PDOP, locExtended.pdop);
        in_msg->getFloat(POS_HDOP, locExtended.hdop);
        in_msg->getFloat(POS_VDOP, locExtended.vdop);
        in_msg->getFloat(POS_MAGNETIC_DEVIATION, locExtended.magneticDeviation);
        in_msg->getFloat(POS_VERT_UNCERT, locExtended.vert_unc);
        in_msg->getFloat(POS_SPEED_UNCERT, locExtended.speed_unc);
        in_msg->getFloat(POS_BEARING_UNCERT, locExtended.bearing_unc);
        in_msg->getUInt8(POS_HOR_RELIABILITY, (uint8_t&)locExtended.horizontal_reliability);
        in_msg->getUInt8(POS_VERT_RELIABILITY, (uint8_t&)locExtended.vertical_reliability);
        in_msg->getFloat(POS_HOR_ELIP_UNC_MAJOR, locExtended.horUncEllipseSemiMajor);
        in_msg->getFloat(POS_HOR_ELIP_UNC_MINOR, locExtended.horUncEllipseSemiMinor);
        in_msg->getFloat(POS_HOR_ELIP_UNC_AZIMUTH, locExtended.horUncEllipseOrientAzimuth);


        locationUpdate(ulpLoc, locExtended);
    }
}

}


