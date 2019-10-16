#ifndef CNE_PARCEL_H
#define CNE_PARCEL_H

/*==============================================================================
  FILE:         CneParcel.h

  OVERVIEW:     Parcel CNE messages

  DEPENDENCIES: android::parcel

                Copyright (c) 2011-2016 Qualcomm Technologies, Inc.
                All Rights Reserved.
                Confidential and Proprietary - Qualcomm Technologies, Inc.
==============================================================================*/

#include "CneDefs.h"
#include "CneCliCas.h"
/*------------------------------------------------------------------------------
 * CLASS         CneParcel
 *
 * DESCRIPTION   Parcel and unparcel data for CnE
 *----------------------------------------------------------------------------*/
class CneParcel {

public:
/*
 * Function: bool unparcel(void *pt, uint32_t dataSz, ProtoBuf &srcObject, T &dstObject)
 * Description: The unparcel functions decode a protocol buffer message, at address
 * pointed by pt with length dataSz, into dstObject. The bytes that pt points to
 * must be previously encoded from srcObject.
 * Parameters: void *pt - points to the bytes of protocol buffer message.
 *             uint32_t dataSz - the length of the bytes
 *             srcObject - the type of protocol buffer message that previously
 *                         encoded the bytes.
 *             dstObject - the object that is decoded from the bytes pt point to.
 * Return: true if the bytes pointed by pt are successfully decoded to dstObject.
 * false if decode exception occurred or at least one of the fields defined in
 * srcObject is missing in the bytes pointed by pt.
 */
    static bool unparcel(void *pt, uint32_t dataSz, CneState &pb, CneStateType &screenState );
    static bool unparcel(void *pt, uint32_t dataSz, WwanSubtypeInfo &pb, CneRatSubType &subType );
    static bool unparcel(void *pt, uint32_t dataSz, QuotaInfo &pb, CneQuotaInfo &qt );
    static bool unparcel(void *pt, uint32_t dataSz, FeatureInfo &pb, CneFeatureInfoType &featureInfoType);
    static bool unparcel(void *pt, uint32_t dataSz, DefaultNetwork &pb, CneDefaultNetworkType &dfnw);
    static bool unparcel(void *pt, uint32_t dataSz, RatStatus &pb, CneRatStatusType &ratStatusType);
    static bool unparcel(void *pt, uint32_t dataSz, PbMobileDataState &pb, MobileDataState &mbdata);
    static bool unparcel(void *pt, uint32_t dataSz, WlanFamType &pb, CneWlanFamType &wfam);
    static bool unparcel(void *pt, uint32_t dataSz, WifiApInfo &pb, CneWifiApInfoType &wap);
    static bool unparcel(void *pt, uint32_t dataSz, WifiP2pInfo &pb, CneWifiP2pInfoType &p2p);
    static bool unparcel(void *pt, uint32_t dataSz, WlanInfo &pb, CneWlanInfoType &wlanInfoType);
    static bool unparcel(void *pt, uint32_t dataSz, WwanInfo &pb, CneWwanInfoType &wwanInfoType);
    static bool unparcel(void *pt, uint32_t dataSz, NatKeepAliveResult &pb, CneNatKeepAliveResultInfo &nkaResult);
    static bool unparcel(void *pt, uint32_t dataSz, IcdHttpResult &pb, CneIcdHttpResultCmdType &icdHttpResultCmdType);
    static bool unparcel(void *pt, uint32_t dataSz, IcdResult &pb, CneIcdResultCmdType &icdResultType);
    static bool unparcel(void *pt, uint32_t dataSz, JrttResult &pb, CneJrttResultCmdType &jrtt);
    static bool unparcel(void *pt, uint32_t dataSz, RatInfo &pb, CneRatInfoType &ratInfoType);
    static bool unparcel(void *pt, uint32_t dataSz, ProfileOverride &pb, IMSProfileOverrideSetting &imsProfileOverride);
    static bool unparcel(void *pt, uint32_t dataSz, ProfileInfo &pb, CliProfileInfo &profile, int fd);
    static bool unparcel(void *pt, uint32_t dataSz, NetRequestInfo &pb, CliNetRequestInfo &netType, int fd);

/*
 * Function: int parcel(T const &srcObject, void *pt)
 * Description: the functions encode srcObject into a protocol buffer message and
 * stores the bytes into the address pointed by pt. The buffer pointed by pt must
 * be previously allocated before calling this function, and must be large enough
 * to hold the entire encoded message from srcObject.
 * Parameters: srcObject - the object that needs to be encoded
 *             void *pt - points to the address where the encoded message shall be
 *             saved.
 * Return: the size of the encoded message if encoding of srcObject is successful
 * -1 if the encoding fails.
 */
    static int parcel(CneFeatureInfoType const& data, void *pt);
    static int parcel(CneFeatureRespType const& data, void *pt);
    static int parcel(CneNatKeepAliveRequestInfo const& data, void *pt);
    static int parcel(cne_rat_type const& data, void *pt);
    static int parcel(CnePolicyUpdateRespType const& data, void *pt);
    static int parcel(CneBQEActiveProbeMsgType const& data, void *pt);
    static int parcel(CneBQEPostParamsMsgType const& data, void *pt);
    static int parcel(cne_set_default_route_req_data_type const& data, void *pt);
    static int parcel(CneIcdStartMsgType const& data, void *pt);
    static int parcel(CneDisallowedAPType const& data, void *pt);
    static int parcel(CasWlanNetConfigType const& data, bool notify, void *pt);
    static int parcel(CasWwanNetConfigType const& data, bool notify, void *pt);
    static int parcel(CnoNetConfigType const& data, bool notify, void *pt);
    static int parcel(CasFeatureInfoType const &data, bool notify, void *pt);
    static int parcel(CneRatSlotType const &data, void *pt);
};

#endif /* define CNE_PARCEL_H */
