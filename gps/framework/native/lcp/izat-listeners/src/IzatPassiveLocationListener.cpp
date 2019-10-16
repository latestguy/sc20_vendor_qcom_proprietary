/*====*====*====*====*====*====*====*====*====*====*====*====*====*====*====*
  Copyright (c) 2015 - 2016 Qualcomm Technologies, Inc.
  All Rights Reserved.
  Confidential and Proprietary - Qualcomm Technologies, Inc.
=============================================================================*/

#include <IzatDefines.h>
#include <IzatManager.h>
#include <IzatPassiveLocationListener.h>
#include <Utils.h>

#define LOG_NDDEBUG 0
#define LOG_TAG "IzatSvc_PassiveLocListener"

using namespace izat_manager;
using namespace std;



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


IzatPassiveLocationListener::IzatListenerMapType IzatPassiveLocationListener::sObjects;

void IzatPassiveLocationListener :: startPassiveLocationListener(
    const std::string clientName,
    const IzatListenerMask listensTo,
    bool intermediateFixAccepted,
    IIzatManager *izatManager,
    IPCMessagingProxy *iPCMessagingProxy) {
    int result = -1;
    do {
        if (NULL == sObjects[clientName]) {
            sObjects[clientName] =
                new(nothrow) IzatPassiveLocationListener(clientName, listensTo, intermediateFixAccepted,
                                                         izatManager, iPCMessagingProxy);
            BREAK_IF_ZERO(1, sObjects[clientName]);
            result = 0;
        }
    } while (0);
    EXIT_LOG_WITH_ERROR("%d", result);
}

void IzatPassiveLocationListener::stopAllPassiveLocationListers(IIzatManager *izatManager) {
    for (IzatListenerMapType::iterator it = sObjects.begin(); it != sObjects.end(); it++) {
        if(0 != izatManager->unsubscribeListener(it->second)) {
            LOC_LOGI("Failed to unsubcribe location listener for %s", it->first.c_str());
        }
        delete it->second;
    }
}


// ctor
IzatPassiveLocationListener :: IzatPassiveLocationListener(const std::string clientName,
                                                           IzatListenerMask listensTo,
                                                           bool intermediateFixAccepted,
                                                           IIzatManager *izatManager,
                                                           IPCMessagingProxy *iPCMessagingProxy)
    : mClientName(clientName)
    , mIpcName(clientName + "-LOC-LISTENER")
    , mIntermediateFixAccepted(intermediateFixAccepted)
    , mLocationListenerMask(listensTo)
    , mIPCMessagingProxy(iPCMessagingProxy) {
    int result = -1;

    do {
        BREAK_IF_ZERO(1, izatManager);
        izatManager->subscribeListener(this);
        result = 0;
    } while(0);

    EXIT_LOG_WITH_ERROR("%d", result);
}

// dtor
IzatPassiveLocationListener :: ~IzatPassiveLocationListener() { }

//overrides
IzatListenerMask IzatPassiveLocationListener :: listensTo() const {
    return mLocationListenerMask;
}

void IzatPassiveLocationListener :: onLocationChanged (const IzatLocation *location,
                                                          const IzatLocationStatus status)
{
    ENTRY_LOG();
    UlpLocation ulpLoc;
    GpsLocationExtended locExtended;
    memset(&ulpLoc, 0 , sizeof(UlpLocation));
    memset(&locExtended, 0 ,sizeof(GpsLocationExtended));

    OutPostcard *card = NULL;
    int result = 0;
    do {
        if (status == IZAT_LOCATION_STATUS_INTERMEDIATE) {
          BREAK_IF_ZERO(1, mIntermediateFixAccepted);
        }

        BREAK_IF_ZERO(2, location);
        if (!location->isValid()) {
            result = 3;
            break;
        }
        ulpLocationFromIzatLocation(location, &ulpLoc, &locExtended);

        BREAK_IF_ZERO(4, mIPCMessagingProxy);
        // send out the location update
        card = OutPostcard::createInstance();
        BREAK_IF_ZERO(5, card);
        BREAK_IF_NON_ZERO(6, card->init());
        BREAK_IF_NON_ZERO(7, card->addString("TO", mClientName.c_str()));
        BREAK_IF_NON_ZERO(8, card->addString("FROM", mIpcName.c_str()));
        BREAK_IF_NON_ZERO(9, card->addString("INFO", "LOCATION-UPDATE"));
        //Basic ULP location parameters
        BREAK_IF_NON_ZERO(10, card->addUInt16(POS_LOC_FLAGS, ulpLoc.gpsLocation.flags));
        BREAK_IF_NON_ZERO(11, card->addDouble(POS_LATITUDE, ulpLoc.gpsLocation.latitude));
        BREAK_IF_NON_ZERO(13, card->addDouble(POS_LONGITUDE, ulpLoc.gpsLocation.longitude));
        BREAK_IF_NON_ZERO(15, card->addFloat(POS_HORIZONTAL_ACCURACY, ulpLoc.gpsLocation.accuracy));
        BREAK_IF_NON_ZERO(17, card->addDouble(POS_ALTITUDE, ulpLoc.gpsLocation.altitude));
        BREAK_IF_NON_ZERO(19, card->addInt64(POS_UTC_TIME, ulpLoc.gpsLocation.timestamp));
        BREAK_IF_NON_ZERO(23, card->addFloat(POS_BEARING, ulpLoc.gpsLocation.bearing));
        BREAK_IF_NON_ZERO(25, card->addFloat(POS_SPEED, ulpLoc.gpsLocation.speed));
        BREAK_IF_NON_ZERO(26, card->addUInt16(POS_SOURCE, ulpLoc.position_source));
        //Extended Position prameters
        BREAK_IF_NON_ZERO(10, card->addUInt16(POS_LOC_EXT_FLAGS, locExtended.flags));
        BREAK_IF_NON_ZERO(28, card->addFloat(POS_ALT_MEAN_SEA_LEVEL, locExtended.altitudeMeanSeaLevel));
        BREAK_IF_NON_ZERO(30, card->addFloat(POS_PDOP, locExtended.pdop));
        BREAK_IF_NON_ZERO(31, card->addFloat(POS_HDOP, locExtended.hdop));
        BREAK_IF_NON_ZERO(32, card->addFloat(POS_VDOP, locExtended.vdop));
        BREAK_IF_NON_ZERO(34, card->addFloat(POS_MAGNETIC_DEVIATION, locExtended.magneticDeviation));
        BREAK_IF_NON_ZERO(36, card->addFloat(POS_VERT_UNCERT, locExtended.vert_unc));
        BREAK_IF_NON_ZERO(38, card->addFloat(POS_SPEED_UNCERT, locExtended.speed_unc));
        BREAK_IF_NON_ZERO(40, card->addFloat(POS_BEARING_UNCERT, locExtended.bearing_unc));
        BREAK_IF_NON_ZERO(42, card->addUInt8(POS_HOR_RELIABILITY, locExtended.horizontal_reliability));
        BREAK_IF_NON_ZERO(44, card->addUInt8(POS_VERT_RELIABILITY, locExtended.vertical_reliability));
        BREAK_IF_NON_ZERO(46, card->addFloat(POS_HOR_ELIP_UNC_MAJOR, locExtended.horUncEllipseSemiMajor));
        BREAK_IF_NON_ZERO(48, card->addFloat(POS_HOR_ELIP_UNC_MINOR, locExtended.horUncEllipseSemiMinor));
        BREAK_IF_NON_ZERO(50, card->addFloat(POS_HOR_ELIP_UNC_AZIMUTH, locExtended.horUncEllipseOrientAzimuth));

        BREAK_IF_NON_ZERO(51, card->finalize());
        BREAK_IF_NON_ZERO(52, mIPCMessagingProxy->sendMsg(card, mClientName.c_str()));
    } while(0);

    delete card;

    EXIT_LOG_WITH_ERROR("%d", result);
}
void IzatPassiveLocationListener::ulpLocationFromIzatLocation(const IzatLocation *izatLoc, UlpLocation *ulpLoc, GpsLocationExtended* locExtended) {
    /*Populate  UlpLocation parameters*/
    if ((NULL != izatLoc) && (NULL != ulpLoc)) {
        ulpLoc->size = sizeof(UlpLocation);
        ulpLoc->gpsLocation.size = sizeof(GpsLocation);

        if(izatLoc->mHasLatitude && izatLoc->mHasLongitude){
            ulpLoc->gpsLocation.flags  |= GPS_LOCATION_HAS_LAT_LONG;
            ulpLoc->gpsLocation.latitude = izatLoc->mLatitude;
            ulpLoc->gpsLocation.longitude = izatLoc->mLongitude;
            ulpLoc->position_source = izatLoc->mPosition_source;
        }
        if(izatLoc->mHasAltitudeWrtEllipsoid){
            ulpLoc->gpsLocation.flags  |= GPS_LOCATION_HAS_ALTITUDE;
            ulpLoc->gpsLocation.altitude = izatLoc->mAltitudeWrtEllipsoid;
        }
        if(izatLoc->mHasHorizontalAccuracy){
            ulpLoc->gpsLocation.flags  |= GPS_LOCATION_HAS_ACCURACY;
            ulpLoc->gpsLocation.accuracy = izatLoc->mHorizontalAccuracy;
        }
        if(izatLoc->mHasSpeed){
            ulpLoc->gpsLocation.flags  |= GPS_LOCATION_HAS_SPEED;
            ulpLoc->gpsLocation.speed = izatLoc->mSpeed;
        }
        if(izatLoc->mHasBearing){
            ulpLoc->gpsLocation.flags  |= GPS_LOCATION_HAS_BEARING;
            ulpLoc->gpsLocation.bearing = izatLoc->mBearing;
        }
        if(izatLoc->mHasUtcTimestampInMsec){
            ulpLoc->gpsLocation.timestamp = izatLoc->mUtcTimestampInMsec;
        }
    }
    /*Populate  GpsLocationExtended parameters*/
    if ((NULL != izatLoc) && (NULL != locExtended)) {
        locExtended->size = sizeof(GpsLocationExtended);
        if(izatLoc->mHasAltitudeMeanSeaLevel){
            locExtended->flags |= GPS_LOCATION_EXTENDED_HAS_ALTITUDE_MEAN_SEA_LEVEL;
            locExtended->altitudeMeanSeaLevel= izatLoc->mAltitudeMeanSeaLevel;
        }
        if(izatLoc->mHasDop){
            locExtended->flags |= GPS_LOCATION_EXTENDED_HAS_DOP;
            locExtended->pdop = izatLoc->mPdop;
            locExtended->hdop = izatLoc->mHdop;
            locExtended->vdop = izatLoc->mVdop;
        }
        if(izatLoc->mHasMagneticDeviation){
            locExtended->flags |= GPS_LOCATION_EXTENDED_HAS_MAG_DEV;
            locExtended->magneticDeviation = izatLoc->mMagneticDeviation;
        }
        if(izatLoc->mHasVert_unc){
            locExtended->flags |= GPS_LOCATION_EXTENDED_HAS_VERT_UNC;
            locExtended->vert_unc = izatLoc->mVert_unc;
        }
        if(izatLoc->mHasSpeed_unc){
            locExtended->flags |= GPS_LOCATION_EXTENDED_HAS_SPEED_UNC;
            locExtended->speed_unc = izatLoc->mSpeed_unc;
        }
        if(izatLoc->mHasBearing_unc){
            locExtended->flags |= GPS_LOCATION_EXTENDED_HAS_BEARING_UNC;
            locExtended->bearing_unc = izatLoc->mBearing_unc;
        }
        if(izatLoc->mHasHorizontal_reliability){
            locExtended->flags |= GPS_LOCATION_EXTENDED_HAS_HOR_RELIABILITY;
            locExtended->horizontal_reliability = (LocReliability)izatLoc->mHorizontal_reliability;
        }
        if(izatLoc->mHasVertical_reliability){
            locExtended->flags |= GPS_LOCATION_EXTENDED_HAS_VERT_RELIABILITY;
            locExtended->vertical_reliability = (LocReliability)izatLoc->mVertical_reliability;
        }
        if(izatLoc->mHasHorUncEllipseSemiMajor){
            locExtended->flags |= GPS_LOCATION_EXTENDED_HAS_HOR_ELIP_UNC_MAJOR;
            locExtended->horUncEllipseSemiMajor = izatLoc->mHorUncEllipseSemiMajor;
        }
        if(izatLoc->mHasHorUncEllipseSemiMinor){
            locExtended->flags |= GPS_LOCATION_EXTENDED_HAS_HOR_ELIP_UNC_MINOR;
            locExtended->horUncEllipseSemiMinor = izatLoc->mHasHorUncEllipseSemiMinor;
        }
        if(izatLoc->mHasHorUncEllipseOrientAzimuth){
            locExtended->flags |= GPS_LOCATION_EXTENDED_HAS_HOR_ELIP_UNC_AZIMUTH;
            locExtended->horUncEllipseOrientAzimuth = izatLoc->mHorUncEllipseOrientAzimuth;
        }
    }
}

void IzatPassiveLocationListener :: onStatusChanged
(
    const IzatProviderStatus
)
{
    // do nothing for now
}


