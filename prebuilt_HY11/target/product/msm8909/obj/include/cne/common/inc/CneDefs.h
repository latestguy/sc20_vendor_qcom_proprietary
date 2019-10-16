#ifndef CNE_DEFS_H
#define CNE_DEFS_H

/**----------------------------------------------------------------------------
  @file CNE_Defs.h

  This file holds various definations that get used across, different CNE
  modules.
-----------------------------------------------------------------------------*/


/*============================================================================
        Copyright (c) 2009-2016 Qualcomm Technologies, Inc.
        All Rights Reserved.
        Confidential and Proprietary - Qualcomm Technologies, Inc.
============================================================================*/


/*============================================================================
  EDIT HISTORY FOR MODULE

  $Header: //depot/asic/sandbox/projects/cne/common/core/inc/CneDefs.h#7 $
  $DateTime: 2009/11/20 17:36:15 $
  $Author: chinht $
  $Change: 1092637 $

  when        who   what, where, why
  ----------  ---   -------------------------------------------------------
  2009-07-15  ysk   First revision.
  2011-07-27  jnb   Added more definitions
  2011-10-28  tej   Updated max application macro and CneAppInfoMsgDataType
  2012-03-09  mtony Added include file, stdint.h, which is directly needed
============================================================================*/
#include <string>
#include <set>
#include <android/multinetwork.h>
#include "NativeIF.pb.h"

/*
 * WARNING WARNING WARNING WARNING WARNING
 * set to CFLAG PROTOMSG_TEST in Android.mk for libcne and cneapiclient
 * for testing protocol buf msg
 * Make sure TEST is also set to true in CneMsg.java
 * Both places need to be set to true to test
 * Otherwise, behavior is undefined.
 * WARNING WARNING WARNING WARNING WARNING
 */

/**
 * Possible return codes
 *
 * New values should be added to CneUtils::init()
 */
typedef enum
{
  /* ADD other new error codes here */
  CNE_RET_SERVICE_NOT_AVAIL = -13,
  CNE_RET_ASYNC_RESPONSE = -12,
  CNE_RET_ERR_READING_FILE_STAT = -11,
  CNE_RET_PARSER_NO_MATCH = -10,
  CNE_RET_PARSER_VALIDATION_FAIL = -9,
  CNE_RET_PARSER_TRAVERSE_FAIL = -8,
  CNE_RET_PARSER_PARSE_FAIL = -7,
  CNE_RET_ERR_OPENING_FILE = -6,
  CNE_RET_INVALID_DATA = -5,
  CNE_RET_OUT_OF_MEM = -4,
  CNE_RET_ALREADY_EXISTS = -3,
  CNE_RET_NOT_ALLOWED_NOW = -2,
  CNE_RET_ERROR = -1,

  CNE_RET_OK = 1,
  CNE_RET_PARSER_MATCH = 2
} CneRetType;

#ifndef MAX
   #define  MAX( x, y ) ( ((x) > (y)) ? (x) : (y) )
#endif /* MAX */

#ifndef MIN
   #define  MIN( x, y ) ( ((x) < (y)) ? (x) : (y) )
#endif /* MIN */


#ifdef __cplusplus
  extern "C" {
#endif /* __cplusplus */

/*----------------------------------------------------------------------------
 * Include C Files
 * -------------------------------------------------------------------------*/
#include <sys/types.h>
#include <stdint.h>

/*----------------------------------------------------------------------------
 * Preprocessor Definitions and Constants
 * -------------------------------------------------------------------------*/
#ifndef TRUE
  /** Boolean true value. */
  #define TRUE   1
#endif /* TRUE */

#ifndef FALSE
  /** Boolean false value. */
  #define FALSE  0
#endif /* FALSE */

#ifndef NULL
/** NULL */
  #define NULL  0
#endif /* NULL */

#define CNE_IPA_IFACE_NAME_MAX 20 // Mirror with IPA_RESOURCE_NAME_MAX in msm_ipa.h

#define CNE_MAX_SSID_LEN 32
// Max BSSID size is 64 bits (EUI-64). We receive this value as a string in human readable format:
// (00:00:00:00:00:00:00:00). There are 23 chars + null termination char + 1 reserved = 25.
#define CNE_MAX_BSSID_LEN 25
#define CNE_MAX_SCANLIST_SIZE 40
#define CNE_MAX_APPLIST_SIZE 500
#define CNE_MAX_IPADDR_LEN 46
#define CNE_MAX_IFACE_NAME_LEN 16
#define CNE_MAX_TIMESTAMP_LEN 32
#define CNE_MAX_CAPABILITIES_LEN 256
#define CNE_MAX_URI_LEN 128
#define CNE_MAX_BQE_FILESIZE_LEN 10
// FIXME temp make this bigger
#define CNE_MAX_VENDOR_DATA_LEN 640
#define CNE_MAX_PROTOCOL_BUFFER_LEN 1024
#define CNE_SERVICE_DISABLED 0
#define CNE_SERVICE_ENABLED 1
#define CNE_MAX_BROWSER_APP_LIST 40
#define CNE_MAX_DNS_ADDRS 4
#define CNE_MAX_MCC_MNC_LEN 7 //6 for mccmnc number + 1 for null termination

#define CNE_APP_NAME_MAX_LEN 256
#define CNE_HASHES_MAX_LEN 256 //TODO

#define CNE_FEATURE_IWLAN_PROP "persist.sys.cnd.iwlan"
#define CNE_FEATURE_WQE_PROP "persist.sys.cnd.wqe"
#define CNE_FEATURE_WQE_CQE_TIMER_PROP "persist.cne.cqetimer"
#define BSSID_PLACEHOLDER "00:00:00:00:00:00"

#define CND_RET_CODE_OK 0
#define CND_RET_CODE_UNKNOWN_ERROR -1
#define CND_RET_CODE_INVALID_DATA -2

#define STRUCT_PACKED __attribute__ ((packed))

typedef uint32_t u32;

typedef CommandId cne_cmd_enum_type;
typedef CommandId CneEvent;
typedef MessageId cne_msg_enum_type;
typedef NetworkState cne_network_state_enum_type;
typedef BackgroundEvent cne_background_event_enum_type;
typedef FeatureStatus cne_feature_status;
typedef PolicyType cne_policy_type;
typedef RatType cne_rat_type;
typedef cne_rat_type CneRatType;
typedef RatSubtype cne_rat_subtype;
typedef cne_rat_subtype CneRatSubType;
typedef SlotType cne_slot_type;
typedef cne_slot_type CneSlotType;
typedef BringupErrorType cne_bringuperror_type;
typedef SoftApStatus cne_softApStatus_type;
typedef FeatureType cne_feature_type;
typedef WifiState cne_wifi_state_enum_type;
typedef FamType cne_fam_type;

/* cmd handlers will pass the cmd data as raw bytes.
 * the bytes specified below are for a 32 bit machine
 */
/** @note
   BooleanNote: the daemon will receive the boolean as a 4 byte integer
   cne may treat it as a 1 byte internally
 */

/**
 Request info structure sent by CNE for the request
 CNE_REQUEST_SET_DEFAULT_ROUTE_MSG
 */
typedef struct
{
  cne_rat_type rat;
} cne_set_default_route_req_data_type;

typedef struct _ratInfo {
    cne_rat_type rat;
    net_handle_t netHdl;
    int status;
    cne_slot_type slot;
    cne_bringuperror_type errorCause;
    char iface[CNE_MAX_IFACE_NAME_LEN];
    char ipV4Addr[CNE_MAX_IPADDR_LEN];
    char ipV6Addr[CNE_MAX_IPADDR_LEN];
    char timestamp[CNE_MAX_TIMESTAMP_LEN];

    _ratInfo(): rat(RAT_NONE), netHdl(0), status(-1), slot(SLOT_UNSPECIFIED),
                errorCause(INVALID) {
        bzero(iface, CNE_MAX_IFACE_NAME_LEN);
        bzero(ipV4Addr, CNE_MAX_IPADDR_LEN);
        bzero(ipV6Addr, CNE_MAX_IPADDR_LEN);
        bzero(timestamp, CNE_MAX_TIMESTAMP_LEN);
    }

    void convert (const RatInfo &rhs){
        rat = (cne_rat_type)rhs.nettype();
        netHdl = rhs.nethdl();
        status = rhs.networkstate();
        slot = (cne_slot_type)rhs.slot();
        errorCause = (cne_bringuperror_type)rhs.errorcause();
        memset(ipV4Addr, 0, CNE_MAX_IPADDR_LEN);
        memcpy(ipV4Addr, rhs.ipaddr().c_str(), strlen(rhs.ipaddr().c_str()));

        //iface
        memset(iface, 0, CNE_MAX_IFACE_NAME_LEN);
        memcpy(iface, rhs.iface().c_str(), strlen(rhs.iface().c_str()));

        //ipaddrv6
        memset(ipV6Addr, 0, CNE_MAX_IPADDR_LEN);
        memcpy(ipV6Addr, rhs.ipaddrv6().c_str(), strlen(rhs.ipaddrv6().c_str()));
        memset(timestamp, 0, CNE_MAX_TIMESTAMP_LEN);
        memcpy(timestamp, rhs.timestamp().c_str(), strlen(rhs.timestamp().c_str()));
    }

} CneRatInfoType;

// Make sure the array is in sync with the enum.
static const char* FreqBandToString[] = {"2.4GHz", "5GHz"};

typedef struct  _WlanInfo{
    int32_t type;
    int32_t status;
    int32_t rssi;
    char ssid[CNE_MAX_SSID_LEN];
    char bssid[CNE_MAX_BSSID_LEN];
    char ipAddr[CNE_MAX_IPADDR_LEN];
    char iface[CNE_MAX_IFACE_NAME_LEN];
    char ipAddrV6[CNE_MAX_IPADDR_LEN];
    char ifaceV6[CNE_MAX_IFACE_NAME_LEN];
    char timeStamp[CNE_MAX_TIMESTAMP_LEN];
    net_handle_t netHdl;
    bool isAndroidValidated;
    FreqBand freqBand;
    cne_wifi_state_enum_type wifiState;
    char dnsInfo[CNE_MAX_DNS_ADDRS][CNE_MAX_IPADDR_LEN];

    _WlanInfo(): type(-1), status(-1), rssi(-127), netHdl(0),
        isAndroidValidated(false), freqBand(_2GHz), wifiState(WIFI_STATE_UNKNOWN) {
        bzero(ssid, CNE_MAX_SSID_LEN);
        bzero(bssid, CNE_MAX_BSSID_LEN);
        bzero(ipAddr, CNE_MAX_IPADDR_LEN);
        bzero(iface, CNE_MAX_IFACE_NAME_LEN);
        bzero(ipAddrV6, CNE_MAX_IPADDR_LEN);
        bzero(ifaceV6, CNE_MAX_IFACE_NAME_LEN);
        bzero(timeStamp, CNE_MAX_TIMESTAMP_LEN);
        for (int i = 0; i < CNE_MAX_DNS_ADDRS; i++)
        {
          memset(dnsInfo[i], 0, CNE_MAX_IPADDR_LEN);
        }
    }

    //Copy constructor
    _WlanInfo(const struct _WlanInfo &src):
        type(src.type), status(src.status), rssi(src.rssi),
        netHdl(src.netHdl), isAndroidValidated(src.isAndroidValidated),
        freqBand(src.freqBand), wifiState(src.wifiState)
    {
      strlcpy(ssid, src.ssid, CNE_MAX_SSID_LEN);
      strlcpy(bssid, src.bssid, CNE_MAX_BSSID_LEN);
      strlcpy(ipAddr, src.ipAddr, CNE_MAX_IPADDR_LEN);
      strlcpy(iface, src.iface, CNE_MAX_IFACE_NAME_LEN);
      strlcpy(ipAddrV6, src.ipAddrV6, CNE_MAX_IPADDR_LEN);
      strlcpy(ifaceV6, src.ifaceV6, CNE_MAX_IFACE_NAME_LEN);
      strlcpy(timeStamp, src.timeStamp, CNE_MAX_TIMESTAMP_LEN);
      for (int i = 0; i < CNE_MAX_DNS_ADDRS; i++)
      {
          strlcpy(dnsInfo[i], src.dnsInfo[i], CNE_MAX_IPADDR_LEN);
      }
    }

    void transpose(WlanInfo &wlan){
        const RatInfo &ratInfo = wlan.ratinfo();
        type = ratInfo.nettype();
        status = ratInfo.networkstate();
        rssi = wlan.rssi();
        netHdl = ratInfo.nethdl();
        isAndroidValidated = ratInfo.isandroidvalidated();
        freqBand = wlan.freqband();
        wifiState = wlan.wifistate();
        //ssid
        memset(ssid, 0, CNE_MAX_SSID_LEN);
        memcpy(ssid, wlan.ssid().c_str(), strlen(wlan.ssid().c_str()));

        //bssid
        memset(bssid, 0, CNE_MAX_BSSID_LEN);
        memcpy(bssid, wlan.bssid().c_str(), strlen(wlan.bssid().c_str()));

        //ipaddr
        memset(ipAddr, 0, CNE_MAX_IPADDR_LEN);
        memcpy(ipAddr, ratInfo.ipaddr().c_str(), strlen(ratInfo.ipaddr().c_str()));

        //iface
        memset(iface, 0, CNE_MAX_IFACE_NAME_LEN);
        memcpy(iface, ratInfo.iface().c_str(), strlen(ratInfo.iface().c_str()));

        //ipaddrv6
        memset(ipAddrV6, 0, CNE_MAX_IPADDR_LEN);
        memcpy(ipAddrV6, ratInfo.ipaddrv6().c_str(), strlen(ratInfo.ipaddrv6().c_str()));

        //ifacev6
        memset(ifaceV6, 0, CNE_MAX_IFACE_NAME_LEN);
        memcpy(ifaceV6, ratInfo.ifacev6().c_str(), strlen(ratInfo.ifacev6().c_str()));
        //timestamp
        memset(timeStamp, 0, CNE_MAX_TIMESTAMP_LEN);
        memcpy(timeStamp, ratInfo.timestamp().c_str(), strlen(ratInfo.timestamp().c_str()) );

        for (int i = 0; i < CNE_MAX_DNS_ADDRS; i++)
        {
           memset(dnsInfo[i], 0, CNE_MAX_IPADDR_LEN);
           memcpy(dnsInfo[i], wlan.dnsinfo(i).c_str(), strlen(wlan.dnsinfo(i).c_str()));
        }

    }

} CneWlanInfoType;

typedef struct _WwanInfo  {
    int32_t type;
    int32_t status;
    int32_t rssi;
    int32_t roaming;
    CneRatSubType subrat;
    char ipAddr[CNE_MAX_IPADDR_LEN];
    char iface[CNE_MAX_IFACE_NAME_LEN];
    char ipAddrV6[CNE_MAX_IPADDR_LEN];
    char ifaceV6[CNE_MAX_IFACE_NAME_LEN];
    char timeStamp[CNE_MAX_TIMESTAMP_LEN];
    char mccMnc[CNE_MAX_MCC_MNC_LEN];
    net_handle_t netHdl;

    _WwanInfo(): type(-1), status(-1), rssi(-127),
        roaming(-1), subrat(SUBTYPE_UNKNOWN), netHdl(0) {
            bzero(ipAddr, CNE_MAX_IPADDR_LEN);
            bzero(iface, CNE_MAX_IFACE_NAME_LEN);
            bzero(ipAddrV6, CNE_MAX_IPADDR_LEN);
            bzero(ifaceV6, CNE_MAX_IFACE_NAME_LEN);
            bzero(timeStamp, CNE_MAX_TIMESTAMP_LEN);
            bzero(mccMnc, CNE_MAX_MCC_MNC_LEN);
        }

    void transpose(const WwanInfo &wwan){
        const RatInfo &ratInfo = wwan.ratinfo();
        type = ratInfo.nettype();
        status = ratInfo.networkstate();
        rssi = wwan.signalstrength();
        roaming = wwan.roaming();
        subrat = (CneRatSubType)ratInfo.subtype();
        netHdl = ratInfo.nethdl();
         //ipaddr
        memset(ipAddr, 0, CNE_MAX_IPADDR_LEN);
        memcpy(ipAddr, ratInfo.ipaddr().c_str(), strlen(ratInfo.ipaddr().c_str()));

        //iface
        memset(iface, 0, CNE_MAX_IFACE_NAME_LEN);
        memcpy(iface, ratInfo.iface().c_str(), strlen(ratInfo.iface().c_str()));

        //ipaddrv6
        memset(ipAddrV6, 0, CNE_MAX_IPADDR_LEN);
        memcpy(ipAddrV6, ratInfo.ipaddrv6().c_str(), strlen(ratInfo.ipaddrv6().c_str()));

        //ifacev6
        memset(ifaceV6, 0, CNE_MAX_IFACE_NAME_LEN);
        memcpy(ifaceV6, ratInfo.ifacev6().c_str(), strlen(ratInfo.ifacev6().c_str()));
        //timestamp
        memset(timeStamp, 0, CNE_MAX_TIMESTAMP_LEN);
        memcpy(timeStamp, ratInfo.timestamp().c_str(), strlen(ratInfo.timestamp().c_str()));

        memset(mccMnc, 0, CNE_MAX_MCC_MNC_LEN);
        memcpy(mccMnc, wwan.mccmnc().c_str(), strlen(wwan.mccmnc().c_str()));
    }

} CneWwanInfoType;

typedef struct {
    int32_t level;
    int32_t frequency;
    char ssid[CNE_MAX_SSID_LEN];
    char bssid[CNE_MAX_BSSID_LEN];
    char capabilities[CNE_MAX_CAPABILITIES_LEN];
}CneWlanScanListInfoType;

typedef struct  {
    int numItems;
    CneWlanScanListInfoType scanList[CNE_MAX_SCANLIST_SIZE];
} CneWlanScanResultsType;

typedef struct {
    cne_rat_type rat;
    cne_network_state_enum_type ratStatus;
    char ipAddr[CNE_MAX_IPADDR_LEN];
    char ipAddrV6[CNE_MAX_IPADDR_LEN];

    void transpose( const RatStatus &rhs){
        rat = rhs.rat();
        ratStatus = rhs.ratstatus();

        memset(ipAddr, 0, CNE_MAX_IPADDR_LEN);
        memcpy(ipAddr, rhs.ipaddr().c_str(), strlen(rhs.ipaddr().c_str()));

        memset(ipAddrV6, 0, CNE_MAX_IPADDR_LEN);
        memcpy(ipAddrV6, rhs.ipaddrv6().c_str(), strlen(rhs.ipaddrv6().c_str()));

    }
} CneRatStatusType;

typedef struct {
    cne_rat_type rat;
    cne_slot_type slot;
} CneRatSlotType;

typedef struct {
    uint8_t disallowed; /// bool
    uint8_t reason; /// Congested/Firewalled
    char bssid[CNE_MAX_BSSID_LEN];
} CneDisallowedAPType;

typedef struct {
    int32_t uid;
    uint8_t isBlocked;
} CneNsrmBlockedUidType;

/* data structure used to for the parent app request and result for ATP */
typedef struct {
  int cookie;
  char childAppName[CNE_APP_NAME_MAX_LEN+1];
  uid_t parentUid;
} CneAtpParentAppInfoMsg_t;

typedef struct {
  char bssid[CNE_MAX_BSSID_LEN];
  char uri[CNE_MAX_URI_LEN];
  char httpuri[CNE_MAX_URI_LEN];
  char fileSize[CNE_MAX_BQE_FILESIZE_LEN];
} CneBQEActiveProbeMsgType;

typedef struct {
    char uri[CNE_MAX_URI_LEN];
    char httpuri[CNE_MAX_URI_LEN];
    char bssid[CNE_MAX_BSSID_LEN];
    uint32_t timeout;
    uint32_t tid;
} CneIcdStartMsgType;

typedef struct {
    char bssid[CNE_MAX_BSSID_LEN];
    uint8_t result;
    uint8_t flags;
    uint32_t tid;
    uint32_t icdQuota;
    uint8_t icdProb;
    uint32_t bqeQuota;
    uint8_t bqeProb;
    uint32_t mbw;
    uint32_t tputDl;
    uint32_t tputSdev;

    void convert(const IcdResult &icdResult){
        memset(bssid, 0, CNE_MAX_BSSID_LEN);
        memcpy(bssid, icdResult.bssid().c_str(), strlen(icdResult.bssid().c_str()));

        result = icdResult.result();
        flags = icdResult.flags();
        tid = icdResult.tid();
        icdQuota = icdResult.icdquota();
        icdProb = icdResult.icdprob();
        bqeQuota = icdResult.bqequota();
        bqeProb = icdResult.bqeprob();
        mbw = icdResult.mbw();
        tputDl = icdResult.tputdl();
        tputSdev = icdResult.tputsdev();
    }
} CneIcdResultCmdType;

typedef struct {
    char bssid[CNE_MAX_BSSID_LEN];
    uint8_t result;
    uint32_t tid;
    int family;

    void convert(const IcdHttpResult &rhs){
        memset(bssid, 0, CNE_MAX_BSSID_LEN);
        memcpy(bssid, rhs.bssid().c_str(), strlen(rhs.bssid().c_str()));
        result = rhs.result();
        tid = rhs.tid();
        family = rhs.family();

    }
} CneIcdHttpResultCmdType;

typedef struct {
  int32_t type;
  int32_t state;
} CneStateType;

typedef struct {
    uint32_t result;
    uint32_t jrttMillis;
    uint32_t getTsSeconds;
    uint32_t getTsMillis;
} CneJrttResultCmdType;

typedef struct {
  char bssid[CNE_MAX_BSSID_LEN];
  char uri[CNE_MAX_URI_LEN];
  uint32_t tputKiloBitsPerSec;
  uint32_t timeStampSec;
} CneBQEPostParamsMsgType;

typedef struct {
  cne_feature_type featureId;
  cne_feature_status featureStatus;
  void convert(const FeatureInfo &rhs){
      featureId = rhs.featureid();
      featureStatus = rhs.featurestatus();
  }
} CneFeatureInfoType;

//typedef FeatureInfo CneFeatureInfoType;

typedef struct {
  cne_feature_type featureId;
  cne_feature_status featureStatus;
  int32_t result;
} CneFeatureRespType;

/**
  Response info structure returned for the event
   CNE_NOTIFY_POLICY_UPDATE_DONE.
 */
typedef struct
{
  cne_policy_type policy;
  /**< policy type andsf or nsrm */
  int32_t result;
  /**< 0 for sucess -1 for failure */
} CnePolicyUpdateRespType;

typedef struct _CneWlanFamType
{
  cne_fam_type family;
  bool isAndroidValidated;
  _CneWlanFamType(): family(FAM_NONE), isAndroidValidated(false) {}
}CneWlanFamType;

typedef struct {
    uint32_t timer;
    uint16_t srcPort;
    uint16_t destPort;
    char destIp[CNE_MAX_IPADDR_LEN];
}CneNatKeepAliveRequestInfo;

typedef struct {
    int32_t errorcode;
}CneNatKeepAliveResultInfo;

typedef struct {
    int32_t isQuotaReached;
}CneQuotaInfo;

typedef struct _MobileDataState{
    int32_t isEnabled;
    _MobileDataState():isEnabled(0){};
}MobileDataState;

typedef struct {
    int currState;
    int prevState;
} CneWifiApInfoType;

typedef struct {
    int32_t currState;
} CneWifiP2pInfoType;

typedef struct _IMSProfileOverrideSetting{
    int32_t isOverrideSet;
    _IMSProfileOverrideSetting():isOverrideSet(0){};
}IMSProfileOverrideSetting;

typedef struct _DefaultNetwork {
  int32_t network;
} CneDefaultNetworkType;
/*----------------------------------------------------------------------------
 * Function Declarations and Documentation
 * -------------------------------------------------------------------------*/

typedef struct {
    int fd;
    std::string profile;
}CliProfileInfo;

typedef struct {
    int fd;
    CneRatType rat;
    CneSlotType slot;
}CliNetRequestInfo;

#ifdef __cplusplus
  }
#endif /* __cplusplus */

#endif /* CNE_DEFS_H */
