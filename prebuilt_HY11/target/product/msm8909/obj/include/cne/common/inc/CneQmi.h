
#ifndef CNE_QMI_H
#define CNE_QMI_H
/**
 * @file CneQmi.h
 *
 *
 * ============================================================================
 *             Copyright (c) 2011-2016 Qualcomm Technologies,
 *             Inc. All Rights Reserved.
 *             Qualcomm Technologies Confidential and Proprietary
 * ============================================================================
 */

/*----------------------------------------------------------------------------
 * Include Files
 * -------------------------------------------------------------------------*/
#include <stdlib.h>
#include <CneSrmDefs.h>
#include <CneSrm.h>
#include <string>
#include <pthread.h>
#include <queue.h>
//#include <ext/hash_map>
#include<unordered_map>
#include "CneCom.h"
#include "CneDefs.h"
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wunused-variable"
#include "qmi_cci_target_ext.h"
#ifdef __cplusplus
extern "C" {
#endif
#include "qmi_client.h"
//#include "application_traffic_pairing_v01.h"
#include "data_system_determination_v01.h"
#include "data_port_mapper_v01.h"
#ifdef __cplusplus
}
#endif
#pragma GCC diagnostic pop

//WARNING This should never be defined as 1 when committing
#define CNE_QMI_SANITY 0


class WmsInterface;

/*----------------------------------------------------------------------------
 * Preprocessor Definitions and Constants
 * -------------------------------------------------------------------------*/
#ifndef NELEM
#define NELEM(x) (sizeof(x)/sizeof*(x))
#endif

#ifndef IFNAMSIZ
#define IFNAMSIZ 16
#endif

#ifndef CNE_QMI_CLIENT_INIT_TIMEOUT
#define CNE_QMI_CLIENT_INIT_TIMEOUT 1000
#endif

#ifndef QMI_DPM_TIMEOUT
#define QMI_DPM_TIMEOUT 10000
#endif

static const int64_t WAKELOCK_TIMER = 1000; //millisec
static const int MAX_WAKELOCK_NAME_LEN = 32;
static const char* DSD_IND_WAKELOCK = "cne_dsd_ind_handler_wl";
using namespace std;

/*----------------------------------------------------------------------------
 * Type Declarations
 * -------------------------------------------------------------------------*/
/**
 * @brief
 * CneQmi events type.
 * TODO: handle event registrations for each QMI port separately and
 * dispatch events as appropriate
 */
typedef enum {
  QMI_EVENT_MIN = 0,
  QMI_EVENT_DSD_UP,
  QMI_EVENT_MAX
} CneQmiEventType;

typedef struct CneQmiDsdIndInfo_s
{
  qmi_client_type  userHandle;
  unsigned long    msgId;
  unsigned char    *indBuf;
  int              indBufLen;
  void             *indCbData;
} CneQmiDsdIndInfo_t;

/*----------------------------------------------------------------------------
 * Class Definitions
 * -------------------------------------------------------------------------*/

class CneQmi: public EventDispatcher<CneQmiEventType> {
  public:
      /**
       * @brief Constructor
       */
    CneQmi (CneSrm &setSrm, CneCom &setCneCom, CneTimer &setTimer);
      /**
       * @brief Destructor
       */
      ~CneQmi (void);
      /**
       *  @brief Init QMI service for CNE.
       *  @return None
       */
      void init (void);

      void sendWifiAvailableStatus(dsd_wlan_available_req_msg_v01 &status);

      void sendWqeProfileStatus(dsd_set_wqe_profile_quality_status_req_msg_v01 &status);

      void sendWifiMeasurementReport(dsd_wifi_meas_report_req_msg_v01 &report);

      void stopWifiMeasurement(uint32_t meas_id);

      void sendNotifyDataSettings(dsd_notify_data_settings_req_msg_v01 &dataSettingsReq);

      CneSrm& getSrm();

      CneTimer& getTimer();

      bool setLoActivity(dsd_wwan_activity_enum_type_v01 loActivity);

/**
 * @brief Private class to track each QMI port wds connection
 */

  private:
      /**private copy constructor* - no copies allowed */
      CneQmi (const CneQmi &);

      /** reference to SRM */
      CneSrm &srm;

      CneCom &com;

      CneTimer &timer;

      qmi_idl_service_object_type dsd_svc_obj;

      qmi_client_type qmi_client_hndl;

      qmi_client_type dpm_client_hndl;

      qmi_client_os_params mQmiDsdOsParams, mQmiDpmOsParams;

      qmi_client_type mQmiDsdNotifier, mQmiDpmNotifier;

      dsd_wlan_available_req_msg_v01 wifiAvailMsg;

      static WmsInterface *wmsInst;

      bool isNatKeepAliveStarted;
      bool dsd_ind_wakelock_acquired;
      int wakelockTimerId;
      int resetWakelockTimerRequest;

      int dsdSvcUpFd;
      int dpmSvcUpFd;
      int errorFd;
      int dsdIndFd;
      pthread_mutex_t wakelockMutex;
      pthread_mutex_t queueMutex;
      std::queue<void *> indQueue;

      /**
       * @brief SRM event handler wrapper
       *
       * @return None
       */
      static void srmEventHandler
      (
        SrmEvent    event,
        const void  *event_data,
        void    *user_data
      );

      /**
       * @brief processes SRM events
       *
       * @return None
       */
      void processSrmEvent (SrmEvent event, const void *event_data);

      static void qmiEventCallback(int fd, void *arg);

      void initQmiDsd(qmi_service_info &info);
      void regQmiDsd();
      /**
       * @brief Initializes QMI Data Port Mapper Client
       *
       * @return None
       */
      void initQmiDpm(qmi_service_info &info);
      void regQmiDpm();

      void registerForDsdInd();
      static CneQmi *qmiSelf;

      void sendNatKeepAliveMsg(dsd_nat_keep_alive_info_ind_msg_v01 &natKeepAliveIndMsg);

      void sendNatKeepAliveResponse(CneNatKeepAliveResultInfo *nkaRsp);

      void handleNatKeepAliveInd(CneQmiDsdIndInfo_t *info);

      void handleStartWifiMeasInd(CneQmiDsdIndInfo_t *info);

      void handleStopWifiMeasInd(CneQmiDsdIndInfo_t *info);

      void handleSetWqeProfileInd(CneQmiDsdIndInfo_t *info);

      void handleWqeProfileInitInd(CneQmiDsdIndInfo_t *info);

      /**
       * @brief To check if modem is rev_ip_sync capable
       *
       * @return true if capable, false otherwise
       */
      bool isModemCapable();

      static void qmiDsdIndCb
      (
        qmi_client_type user_handle,
        unsigned int    msg_id,
        void            *ind_buf,
        unsigned int    ind_buf_len,
        void            *ind_cb_data
      );

      static void cneQmiErrorCb
      (
        qmi_client_type userHandle,
        qmi_client_error_type error,
        void *err_cb_data
      );

      static void cneQmiDsdNotifyCb
      (
        qmi_client_type user_handle,
        qmi_idl_service_object_type service_obj,
        qmi_client_notify_event_type service_event,
        void *notify_cb_data
      );

      static void cneQmiDpmNotifyCb
      (
        qmi_client_type user_handle,
        qmi_idl_service_object_type service_obj,
        qmi_client_notify_event_type service_event,
        void *notify_cb_data
      );

      static void handleAllInds(int fd, void *arg);
      static void handleDsdInd(CneQmi *qmiPtr, void *arg);

      static void handleQmiSSR(int fd,void *arg);

      static void handleDsdSvcUpInd(int fd, void *arg);
      static void handleDpmSvcUpInd(int fd, void *arg);
      void handleModemDownInd();

      void dumpReport(dsd_wifi_meas_report_req_msg_v01 &report);

      WmsInterface* loadWms(void);
      bool acquireWakelock(const char* wakelock);
      bool releaseWakelock(const char* wakelock);
      static int wakelockTimerExprCb( void *arg );
      static const char*getQualityStr(dsd_wqe_profile_quality_status_enum_v01 qual);
      static const char*getReasonStr(dsd_wqe_profile_quality_status_cause_code_enum_v01 reason);
};

#endif /* CNE_QMI_H */
