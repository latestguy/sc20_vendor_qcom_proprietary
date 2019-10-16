/* sound_trigger_platform.h
 *
 * Copyright (c) 2013-2016 Qualcomm Technologies, Inc.
 * All Rights Reserved.
 * Confidential and Proprietary - Qualcomm Technologies, Inc.
 */

#ifndef SOUND_TRIGGER_PLATFORM_H
#define SOUND_TRIGGER_PLATFORM_H

#include "sound_trigger_hw.h"

#define PLATFORM_PATH_XML "/system/etc/sound_trigger_platform_info.xml"
#define MIXER_PATH_XML "system/etc/sound_trigger_mixer_paths.xml"
#define MIXER_PATH_XML_WCD9306 "system/etc/sound_trigger_mixer_paths_wcd9306.xml"
#define MIXER_PATH_XML_WCD9326 "system/etc/sound_trigger_mixer_paths_wcd9326.xml"
#define LIB_ACDB_LOADER "libacdbloader.so"
#define LIB_SM_WRAPPER "libsmwrapper.so"
#define BUF_SIZE 1024

#define SOUND_CARD 0
#define MIXER_OPEN_MAX_NUM_RETRY 10
#define RETRY_US 500000

#ifdef PLATFORM_DEFAULT
  #define DEVICE_HANDSET_APE_ACDB_ID   (130)
  #define DEVICE_HANDSET_DMIC_APE      (149)
  #define DEVICE_HANDSET_QMIC_APE      (150)
  #define DEVICE_HANDSET_CPE_ACDB_ID   (128)
  #define DEVICE_HANDSET_MIC_PP_APE   (151)
#else
  #define DEVICE_HANDSET_APE_ACDB_ID   (100)
  #define DEVICE_HANDSET_CPE_ACDB_ID   (128)
#endif

#define DEVICE_NAME_MAX_SIZE 128

/* ACDB app type for LSM non topology */
#define ACDB_LSM_APP_TYPE_NO_TOPOLOGY (1)
#define ST_BE_DAI_NAME_MAX_LENGTH 24
#define SOUND_TRIGGER_DEVICE_DEFAULT_APP_TYPE (69938)

enum {
    ST_DEVICE_NONE = 0,
    ST_DEVICE_MIN,
    ST_DEVICE_HANDSET_MIC = ST_DEVICE_MIN,
    ST_DEVICE_HANDSET_DMIC,
    ST_DEVICE_HANDSET_QMIC,
    ST_DEVICE_HANDSET_MIC_PP,
    ST_DEVICE_MAX,
};

typedef int st_device_t; //TODO

typedef enum {
    ST_SESSION_CAL, /* lsm cal */
    ST_DEVICE_CAL,  /* hwmad, afe cal */
} st_cal_type;

struct st_device_index {
    char name[100];
    unsigned int index;
};

enum st_param_id_type {
    LOAD_SOUND_MODEL,
    UNLOAD_SOUND_MODEL,
    CONFIDENCE_LEVELS,
    OPERATION_MODE,
    MAX_PARAM_IDS
};

struct st_module_param_info {
    unsigned int module_id;
    unsigned int param_id;
};

typedef enum {
    ST_FLUENCE_TYPE_NONE,
    ST_FLUENCE_TYPE_MONO,
    ST_FLUENCE_TYPE_DMIC,
    ST_FLUENCE_TYPE_QMIC
} st_fluence_type_t;

struct st_vendor_info {
    struct listnode list_node;
    sound_trigger_uuid_t uuid;
    int app_type;
    int sample_rate;
    int format;
    int channel_count;
    st_profile_type_t profile_type;
    unsigned int avail_cpe_phrases;
    unsigned int avail_cpe_users;
    unsigned int avail_ape_phrases;
    unsigned int avail_ape_users;
    st_fluence_type_t fluence_type;

    bool is_qc_uuid;
    struct st_module_param_info params[MAX_PARAM_IDS];
    void *smlib_handle;
    smlib_generate_sound_trigger_recognition_config_payload_t
                                    generate_st_recognition_config_payload;
    smlib_generate_sound_trigger_phrase_recognition_event_t
                                    generate_st_phrase_recognition_event;
};

typedef struct st_codec_backend_cfg {
    int sample_rate;
    int format;
    int channel_count;
    char *samplerate_mixer_ctl;
    char *format_mixer_ctl;
    char *channelcount_mixer_ctl;
} st_codec_backend_cfg_t;

struct adm_app_type_cfg_info {
    struct listnode list_node;
    st_profile_type_t profile_type;
    int app_type;
    int sample_rate;
    int bit_width;
};

void *platform_stdev_init(sound_trigger_device_t *stdev);

void platform_stdev_deinit(void *platform);

int platform_stdev_get_device
(
   void *platform,
   struct st_vendor_info* v_info,
   audio_devices_t device
);

int platform_stdev_get_device_name
(
   void *platform,
   st_exec_mode_t exec_mode,
   st_device_t st_device,
   char *device_name
);

int platform_stdev_send_calibration
(
    void *platform,
    audio_devices_t device,
    sound_trigger_session_t *p_ses,
    int app_id,
    bool use_topology,
    st_cal_type type
);

bool platform_stdev_check_and_update_concurrency
(
   void *platform,
   audio_event_type_t event_type,
   unsigned int num_sessions
);

int platform_stdev_check_and_set_codec_backend_cfg
(
   void *platform,
   struct st_vendor_info *v_info,
   bool *backend_cfg_change
);

int platform_stdev_send_stream_app_type_cfg
(
   void *platform,
   sound_trigger_session_t *p_ses,
   audio_devices_t device
);

int platform_stdev_get_device_app_type
(
   void *platform,
   st_profile_type_t profile_type
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

int platform_stdev_send_lec_ref_cfg
(
   void *platform,
   bool enable
);

void platform_stdev_check_and_append_usecase(
   void *platform,
   char *use_case,
   st_profile_type_t profile_type
);

#endif /* SOUND_TRIGGER_PLATFORM_H */
