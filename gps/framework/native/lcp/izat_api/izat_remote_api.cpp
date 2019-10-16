/*=============================================================================
  Copyright (c) 2016 Qualcomm Technologies, Inc.
  All Rights Reserved.
  Confidential and Proprietary - Qualcomm Technologies, Inc.
  =============================================================================*/

#include <izat_remote_api.h>
#include <IzatRemoteApi.h>

using namespace izat_remote_api;

class LocationUpdaterWrapper : public LocationUpdater {
    const locationUpdateCb mLocationCb;
    const void* mClientData;
public:
    inline LocationUpdaterWrapper(locationUpdateCb locCb, void* clientData) :
        LocationUpdater(), mLocationCb(locCb), mClientData(clientData) {
    }
    inline virtual void locationUpdate(UlpLocation& location, GpsLocationExtended& locExtended) override {
        mLocationCb(&location, &locExtended, (void*)mClientData);
    }
};


void* registerLocationUpdater(locationUpdateCb locationCb, void* clientData) {
    return new LocationUpdaterWrapper(locationCb, clientData);
}

void unregisterLocationUpdater(void* locationUpdaterHandle) {
    delete (LocationUpdaterWrapper*)locationUpdaterHandle;
}
