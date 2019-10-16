/* sound_trigger_platform.h
 *
 * Copyright (c) 2013-2015 Qualcomm Technologies, Inc.
 * All Rights Reserved.
 * Confidential and Proprietary - Qualcomm Technologies, Inc.
 *
 */

#ifndef SOUND_TRIGGER_PLATFORM_H
#define SOUND_TRIGGER_PLATFORM_H

#include "sound_trigger_hw.h"

#define LIB_SM_WRAPPER "libsmwrapper.so.1"
#define LIB_ADPCM_DECODER "libadpcmdec.so"
#define BUF_SIZE 1024
#define SOUND_CARD 0

#define DEVICE_HANDSET_APE_ACDB_ID   (130)
#define DEVICE_HANDSET_CPE_ACDB_ID   (128)
#define DEVICE_HEADSET_APE_ACDB_ID   (138)
#define DEVICE_HEADSET_CPE_ACDB_ID   (139)
#define DEVICE_HANDSET_APE_LEC_REF_END_MONO_ACDB_ID (136)
#define DEVICE_HANDSET_APE_LEC_REF_END_STEREO_ACDB_ID (137)

#define DEVICE_NAME_MAX_SIZE 128

/* ACDB app type for LSM non topology */
#define ACDB_LSM_APP_TYPE_NO_TOPOLOGY (1)

/* Maximum firmware image name length */
#define CPE_IMAGE_FNAME_SIZE_MAX (64)

enum {
    ST_DEVICE_NONE = 0,
    ST_DEVICE_MIN,
    ST_DEVICE_HANDSET_MIC = ST_DEVICE_MIN,
    ST_DEVICE_HEADSET_MIC,
    ST_DEVICE_MAX,
};

typedef int st_device_t;

typedef enum {
    ST_SESSION_CAL, /* lsm cal */
    ST_DEVICE_CAL,  /* hwmad, afe cal */
} st_cal_type;


typedef enum {
    ADPCM_CUSTOM_PACKET = 0x01,
    ADPCM_RAW = 0x02,
    PCM_CUSTOM_PACKET = 0x04,
    PCM_RAW = 0x08,
    ADPCM = (ADPCM_CUSTOM_PACKET | ADPCM_RAW),
    PCM = (PCM_CUSTOM_PACKET | PCM_RAW),
} st_capture_format_t;

typedef enum {
    RT_TRANSFER_MODE,
    FTRT_TRANSFER_MODE,
} st_capture_mode_t;

enum st_param_id_type {
    LOAD_SOUND_MODEL,
    UNLOAD_SOUND_MODEL,
    CONFIDENCE_LEVELS,
    OPERATION_MODE,
    POLLING_ENABLE,
    MAX_PARAM_IDS
};

struct st_module_param_info {
    unsigned int module_id;
    unsigned int param_id;
};

struct st_vendor_info {
    struct listnode list_node;
    sound_trigger_uuid_t uuid;
    int app_type;
    bool is_qc_uuid;

    char cpe_firmware_image[CPE_IMAGE_FNAME_SIZE_MAX];
    st_capture_format_t kw_capture_format;
    st_capture_mode_t kw_transfer_mode;
    unsigned int avail_cpe_phrases;
    unsigned int avail_cpe_users;
    unsigned int avail_ape_phrases;
    unsigned int avail_ape_users;

    struct st_module_param_info params[MAX_PARAM_IDS];
    void *smlib_handle;
    smlib_generate_sound_trigger_recognition_config_payload_t
                                    generate_st_recognition_config_payload;
    smlib_generate_sound_trigger_phrase_recognition_event_t
                                    generate_st_phrase_recognition_event;
};

void *platform_stdev_init(sound_trigger_device_t *stdev);

void platform_stdev_deinit(void *platform);

int platform_stdev_get_device
(
   void *platform,
   audio_devices_t device
);

bool platform_stdev_check_and_update_concurrency
(
   void *platform,
   audio_event_type_t event_type,
   unsigned int num_sessions
);

bool platform_stdev_is_session_allowed
(
   void *platform,
   unsigned int num_sessions,
   bool sound_model_loaded
);

int platform_stdev_connect_mad
(
    void *platform,
    bool is_ape
);

struct st_vendor_info* platform_stdev_get_vendor_info
(
   void *platform,
   sound_trigger_uuid_t *uuid,
   bool *qc_uuid
);

#endif /* SOUND_TRIGGER_PLATFORM_H */
