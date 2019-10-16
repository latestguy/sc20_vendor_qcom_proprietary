/*============================================================================
  @file sns_pm.c

  @brief Invokes the Peripheral Manager (PM) framework to bring sensor image
         out of reset.

  DEPENDENCIES: none

  Copyright (c) 2015 Qualcomm Technologies, Inc.
  All Rights Reserved.
  Confidential and Proprietary - Qualcomm Technologies, Inc.
  ============================================================================*/

#include "sns_smr_util.h"
#include "sns_main.h"
#include "sns_init.h"

#ifdef SNS_USE_PM

#include <pm-service.h>
#include <fcntl.h>

#define PM_SENSORS_DEV_NAME "slpi"
#define PM_SENSORS_CLIENT_NAME "sensors.qcom"
#define BOOT_IMG_SYSFS_PATH "/sys/kernel/boot_slpi/boot"
#define UNLOAD_IMAGE "0"

static void *pm_handle;

static void slpi_unload()
{
  int rc, fd;

  fd = open(BOOT_IMG_SYSFS_PATH, O_WRONLY);
  if (fd == -1) {
    SNS_PRINTF_STRING_ERROR_2( SNS_DBG_MOD_APPS_MAIN,
                               "Failed to open %s to write, %d",
                               BOOT_IMG_SYSFS_PATH, errno );
  }
  else {
    SNS_PRINTF_STRING_ERROR_0( SNS_DBG_MOD_APPS_MAIN,
                               "Unload SLPI images to give control to PM" );
    rc = write(fd, UNLOAD_IMAGE, 1); /* sizeof(UNLOAD_IMAGE) failing, so, using 1. */
    if(rc != 1) {
     SNS_PRINTF_STRING_ERROR_2( SNS_DBG_MOD_APPS_MAIN,
                                "Sysfs write error: %d, %d",
                                rc, errno );
    }
    close(fd);
  }
}

static void pm_event_notifier(void *pm_handle, enum pm_event event)
{
  SNS_PRINTF_STRING_ERROR_1( SNS_DBG_MOD_APPS_MAIN,
                             "PM event received: %d", event );
  pm_client_event_acknowledge(pm_handle, event);

  switch (event) {
    case EVENT_PERIPH_GOING_OFFLINE:
      SNS_PRINTF_STRING_ERROR_0( SNS_DBG_MOD_APPS_MAIN,
                                 "SSR Event Detected/SLPI going offline" );
      sns_main_exit();
      break;

    case EVENT_PERIPH_IS_OFFLINE:
      SNS_PRINTF_STRING_ERROR_0( SNS_DBG_MOD_APPS_MAIN,
                                 "SLPI is offline" );
      break;

    case EVENT_PERIPH_GOING_ONLINE:
      SNS_PRINTF_STRING_ERROR_0( SNS_DBG_MOD_APPS_MAIN,
                                 "SLPI is going online" );
      break;

    case EVENT_PERIPH_IS_ONLINE:
      SNS_PRINTF_STRING_ERROR_0( SNS_DBG_MOD_APPS_MAIN,
                                 "SLPI is online, triggerring unload SLPI" );
      /*
       * SSC Driver loads SLPI image at early boot up, before PM is up.
       * Once PM is up, voted to PM & received SLPI online event, unload
       * the image to give control to PM to load/unload image
       */
      slpi_unload();
      break;

    default:
      SNS_PRINTF_STRING_ERROR_0( SNS_DBG_MOD_APPS_MAIN,
                                 "Invalid event received from PM" );
      break;
  }
}

static int sns_pm_init_internal()
{
  enum pm_event pm_state;
  int rc;

  /* Register with Peripheral Manager */
  rc = pm_client_register(pm_event_notifier,
                          pm_handle,
                          PM_SENSORS_DEV_NAME,
                          PM_SENSORS_CLIENT_NAME,
                          &pm_state,
                          &pm_handle);
  if (rc != PM_RET_SUCCESS) {
    SNS_PRINTF_STRING_FATAL_0( SNS_DBG_MOD_APPS_MAIN,
                               "Peripheral Manager not available" );
    return -1;
  }

   /* Invoke PIL to load the sensor image */
   rc = pm_client_connect(pm_handle);
   if (rc != PM_RET_SUCCESS) {
     SNS_PRINTF_STRING_FATAL_0( SNS_DBG_MOD_APPS_MAIN,
                                "Sensor PIL Failed" );
     pm_client_unregister(pm_handle);
     return -1;
  }

  SNS_PRINTF_STRING_HIGH_0( SNS_DBG_MOD_APPS_MAIN,
                            "Sensor PIL initiated" );

  return 0;
}

/*!
  @brief Initializes the Peripheral Manager

  @return
   - SNS_SUCCESS if initialization is successful.
   - All other values indicate an error has occurred.
*/
sns_err_code_e sns_pm_init()
{
  int sns_err;

  /* Load the sensor core image if not already loaded */
  sns_err = sns_pm_init_internal();
  if (sns_err) {
    SNS_PRINTF_STRING_ERROR_1( SNS_DBG_MOD_APPS_MAIN,
                               "Exiting! PM failed with %d",
                               sns_err );
    /* SNS_ERR_FAILED */
    sleep(60); /* Prevent init process from constantly restarting us */
    exit(-1);
  }

  sns_init_done();
  return SNS_SUCCESS;
}

#else /* SNS_USE_PM */
/* Stub function for platforms that do not use Peripheral Manager */
sns_err_code_e sns_pm_init()
{
  sns_init_done();
  return SNS_SUCCESS;
}
#endif /* SNS_USE_PM */
