/* sound_trigger_platform.c
 *
 * Copyright (c) 2013-2015 Qualcomm Technologies, Inc.
 * All Rights Reserved.
 * Confidential and Proprietary - Qualcomm Technologies, Inc.
 */

#define LOG_TAG "sound_trigger_platform"
/* #define LOG_NDEBUG 0 */
#define LOG_NDDEBUG 0

#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <sys/ioctl.h>
#include "sound_trigger_platform.h"
#include <sound/msmcal-hwdep.h>

/* macros used to verify all param id tags defined for vendor uuid
   in platform file */
#define PARAM_LOAD_SOUND_MODEL_BIT (1 << 0)
#define PARAM_UNLOAD_SOUND_MODEL_BIT (1 << 1)
#define PARAM_CONFIDENCE_LEVELS_BIT (1 << 2)
#define PARAM_OPERATION_MODE_BIT (1 << 3)
#define PARAM_POLLING_ENABLE_BIT (1 << 4)
#define PARAM_ID_MANDATORY_BITS \
    (PARAM_LOAD_SOUND_MODEL_BIT | PARAM_UNLOAD_SOUND_MODEL_BIT | \
     PARAM_OPERATION_MODE_BIT)
#define UINT_MAX	(~0U)
#define strlcpy g_strlcpy

typedef int  (*acdb_loader_init_v2_t)(const char *, const char *, int);
typedef void (*acdb_loader_deallocate_t)(void);
typedef void (*acdb_loader_send_listen_afe_cal_t)(int, int);
typedef int  (*acdb_loader_send_listen_lsm_cal_t)(int, int, int, int);
typedef int  (*acdb_loader_get_calibration_t)(char *, int, void *);

static const char * const
st_device_table[ST_DEVICE_EXEC_MODE_MAX][ST_DEVICE_MAX] = {
    {
        /* ADSP SVA devices */
        [ST_DEVICE_NONE] = "none",
        [ST_DEVICE_HANDSET_MIC] = "listen-ape-handset-mic",
        [ST_DEVICE_HEADSET_MIC] = "listen-ape-headset-mic",
    },
    {
        /* CPE SVA devices */
        [ST_DEVICE_NONE] = "none",
        [ST_DEVICE_HANDSET_MIC] = "listen-cpe-handset-mic",
        [ST_DEVICE_HEADSET_MIC] = "listen-cpe-headset-mic",
    },
};


/* Qualcomm vendorUuid for SVA soundmodel */
static const sound_trigger_uuid_t qc_uuid =
    { 0x68ab2d40, 0xe860, 0x11e3, 0x95ef, { 0x00, 0x02, 0xa5, 0xd5, 0xc5, 0x1b } };

struct platform_data {
    int hwdep_fd;
    int prev_acdb_id;
    sound_trigger_device_t *stdev;
    void *acdb_handle;
    acdb_loader_send_listen_afe_cal_t acdb_send_afe_cal;
    acdb_loader_send_listen_lsm_cal_t acdb_send_lsm_cal;
    acdb_loader_get_calibration_t acdb_get_cal;
    acdb_loader_deallocate_t acdb_deinit;
    struct st_vendor_info *vendor_uuid_info;
    int param_tag_tracker;
};

static void platform_stdev_set_default_config(struct platform_data *platform)
{
    sound_trigger_device_t *stdev = platform->stdev;

    stdev->max_ape_sessions = 1;
    stdev->avail_ape_phrases = 5;
    stdev->avail_ape_users = 5;
    stdev->max_cpe_sessions = 1;
    stdev->avail_cpe_phrases  = 6;
    stdev->avail_cpe_users = 3;
    stdev->rx_conc_max_st_ses = UINT_MAX;
    stdev->support_dev_switch = false;
    stdev->sw_mad = false;
    stdev->support_lec = false;
}

void *platform_stdev_init(sound_trigger_device_t *stdev)
{
    int ret = 0, retry_num = 0;
    struct platform_data *my_data = NULL;
    const char *snd_card_name = NULL;
    acdb_loader_init_v2_t acdb_init;
    char mixer_path_xml[100];
    struct listnode *v_node;
    struct st_vendor_info* v_info;
    char dev_name[256];

    ALOGI("%s: Enter", __func__);
    my_data = calloc(1, sizeof(struct platform_data));

    if (!my_data || !stdev) {
        ALOGE("%s: ERROR. NULL param", __func__);
        if(my_data)
            free(my_data);
        return NULL;
    }
    my_data->stdev = stdev;
    list_init(&stdev->vendor_uuid_list);
    platform_stdev_set_default_config(my_data);

    /* Using non topology solution still need QC smlib wrapper APIs */
    stdev->smlib_handle = dlopen(LIB_SM_WRAPPER, RTLD_NOW);
    if (stdev->smlib_handle) {
        stdev->generate_st_phrase_recognition_event =
            (smlib_generate_sound_trigger_phrase_recognition_event_t)dlsym(stdev->smlib_handle,
                                                  "generate_sound_trigger_phrase_recognition_event");
        if (!stdev->generate_st_phrase_recognition_event) {
            ALOGE("%s: dlsym error %s for generate_sound_trigger_phrase_recognition_event", __func__,
                  dlerror());
            goto cleanup;
        }

        stdev->generate_st_recognition_config_payload =
            (smlib_generate_sound_trigger_recognition_config_payload_t)dlsym(stdev->smlib_handle,
                                                  "generate_sound_trigger_recognition_config_payload");
        if (!stdev->generate_st_recognition_config_payload) {
            ALOGE("%s: dlsym error %s for generate_sound_trigger_recognition_config_payload",
                  __func__, dlerror());
            goto cleanup;
        }
    } else {
        ALOGW("%s: dlopen failed for %s, error %s", __func__, LIB_SM_WRAPPER, dlerror());
    }

    /* Check if ISV vendor_uuid is present and force disable transitions */
    list_for_each(v_node, &stdev->vendor_uuid_list) {
        v_info = node_to_item(v_node, struct st_vendor_info, list_node);
        if(!memcmp(&v_info->uuid, &qc_uuid, sizeof(sound_trigger_uuid_t))) {
            v_info->is_qc_uuid = true;
        } else {
            ALOGV("%s: ISV uuid present, force disable transitions",
              __func__);
            stdev->transition_enabled = false;
        }
        if (!stdev->adpcm_dec_lib_handle &&
             (v_info->kw_capture_format & ADPCM)) {
            /* Load ADPCM decoder library */
            stdev->adpcm_dec_lib_handle = dlopen(LIB_ADPCM_DECODER, RTLD_NOW);
            if (!stdev->adpcm_dec_lib_handle) {
                ALOGE("%s: ERROR. dlopen failed for %s", __func__, LIB_ADPCM_DECODER);
                goto cleanup;
            }
            stdev->adpcm_dec_init =
                (g722_init_decoder_t)dlsym(stdev->adpcm_dec_lib_handle,
                                                      "g722_init_decoder");
            if (!stdev->adpcm_dec_init) {
                ALOGE("%s: dlsym error %s for g722_init_decoder", __func__,
                      dlerror());
                goto cleanup;
            }

            stdev->adpcm_dec_get_scratch_size =
                (g722_dec_get_total_byte_size_t)dlsym(stdev->adpcm_dec_lib_handle,
                                                      "g722_dec_get_total_byte_size");
            if (!stdev->adpcm_dec_get_scratch_size) {
                ALOGE("%s: dlsym error %s for g722_dec_get_total_byte_size", __func__,
                      dlerror());
                goto cleanup;
            }

            stdev->adpcm_dec_process =
                (g722_dec_process_t)dlsym(stdev->adpcm_dec_lib_handle,
                                                      "g722_dec_process");
            if (!stdev->adpcm_dec_process) {
                ALOGE("%s: dlsym error %s for g722_dec_process", __func__,
                      dlerror());
                goto cleanup;
            }
        }
        ALOGV("%s: vendor config: kcf=%d, ktm=%d, ckw=%d, cu=%d, akw=%d, au=%d",
              __func__, v_info->kw_capture_format, v_info->kw_transfer_mode,
              v_info->avail_cpe_phrases, v_info->avail_cpe_users,
              v_info->avail_ape_phrases, v_info->avail_ape_users);
    }
    return my_data;
cleanup:
    if(stdev->adpcm_dec_lib_handle)
        dlclose(stdev->adpcm_dec_lib_handle);

    if (stdev->smlib_handle)
        dlclose(stdev->smlib_handle);

    list_for_each(v_node, &stdev->vendor_uuid_list) {
        list_remove(v_node);
        v_info = node_to_item(v_node, struct st_vendor_info, list_node);
        if (v_info->smlib_handle)
            dlclose(v_info->smlib_handle);
        free(v_info);
    }
    free(my_data);
    return NULL;
}

void platform_stdev_deinit(void *platform)
{
    struct platform_data *my_data = (struct platform_data *)platform;
    struct listnode *v_node;
    struct st_vendor_info* v_info;

    ALOGI("%s: Enter", __func__);
    if (my_data) {
        list_for_each(v_node, &my_data->stdev->vendor_uuid_list) {
            list_remove(v_node);
            v_info = node_to_item(v_node, struct st_vendor_info, list_node);
            if (v_info->smlib_handle)
                dlclose(v_info->smlib_handle);
            free(v_info);
        }
        if (my_data->stdev->smlib_handle)
            dlclose(my_data->stdev->smlib_handle);
        if (my_data->stdev->adpcm_dec_lib_handle)
            dlclose(my_data->stdev->adpcm_dec_lib_handle);
        free(my_data);
    }
}

struct st_vendor_info* platform_stdev_get_vendor_info
(
   void *platform,
   sound_trigger_uuid_t *uuid,
   bool *is_qc_uuid
)
{
    struct listnode *v_node;
    struct st_vendor_info* v_info;
    struct platform_data *my_data;
    sound_trigger_device_t *stdev;

    if (!platform || !is_qc_uuid || !uuid) {
        ALOGE("%s: NULL inputs", __func__);
        return NULL;
    }
    my_data = (struct platform_data *)platform;
    if (!my_data->stdev) {
        ALOGE("%s: platform stdev data is NULL", __func__);
        return NULL;
    }
    stdev = my_data->stdev;
    *is_qc_uuid = false;

    list_for_each(v_node, &stdev->vendor_uuid_list) {
        v_info = node_to_item(v_node, struct st_vendor_info, list_node);
        if(!memcmp(&v_info->uuid, uuid, sizeof(sound_trigger_uuid_t))) {
            ALOGV("%s: Matched uuid", __func__);
            return v_info;
        }
    }

    if (!memcmp(&qc_uuid, uuid, sizeof(sound_trigger_uuid_t)))
        *is_qc_uuid = true;

    return NULL;
}

int platform_stdev_get_device
(
   void *platform,
   audio_devices_t device
)
{
    struct platform_data *my_data = (struct platform_data *)platform;
    st_device_t st_device;

    switch (device) {
    case AUDIO_DEVICE_IN_WIRED_HEADSET:
        st_device = ST_DEVICE_HEADSET_MIC;
        break;
    case AUDIO_DEVICE_IN_BUILTIN_MIC:
        st_device = ST_DEVICE_HANDSET_MIC;
        break;
    default:
        st_device = ST_DEVICE_NONE;
        break;
    }
    return st_device;
}

bool platform_stdev_check_and_update_concurrency
(
   void *platform,
   audio_event_type_t event_type,
   unsigned int num_sessions
)
{
    struct platform_data *my_data;
    sound_trigger_device_t *stdev;
    bool concurrency_ses_allowed = true;

    if (!platform) {
        ALOGE("%s: NULL platform", __func__);
        return false;
    }
    my_data = (struct platform_data *)platform;
    if (!my_data->stdev) {
        ALOGE("%s: platform stdev data is NULL", __func__);
        return false;
    }
    stdev = my_data->stdev;

    switch (event_type) {
    case AUDIO_EVENT_CAPTURE_DEVICE_ACTIVE:
        stdev->tx_concurrency_active++;
        break;
    case AUDIO_EVENT_CAPTURE_DEVICE_INACTIVE:
        if (stdev->tx_concurrency_active > 0)
            stdev->tx_concurrency_active--;
        break;
    case AUDIO_EVENT_PLAYBACK_STREAM_ACTIVE:
            stdev->rx_concurrency_active++;
        break;
    case AUDIO_EVENT_PLAYBACK_STREAM_INACTIVE:
        if (stdev->rx_concurrency_active > 0)
            stdev->rx_concurrency_active--;
        break;
    default:
        break;
    }
    if ((stdev->tx_concurrency_active > 0) ||
        (stdev->rx_concurrency_disabled &&
          stdev->rx_concurrency_active > 0 &&
          num_sessions > stdev->rx_conc_max_st_ses)) {
        concurrency_ses_allowed = false;
    }

    ALOGD("%s: concurrency active %d, tx %d, rx %d, concurrency session_allowed %d",
          __func__, stdev->audio_concurrency_active, stdev->tx_concurrency_active,
          stdev->rx_concurrency_active, concurrency_ses_allowed);
    return concurrency_ses_allowed;
}

bool platform_stdev_is_session_allowed
(
    void *platform,
    unsigned int num_sessions,
    bool sound_model_loaded
)
{
    struct platform_data *my_data;
    sound_trigger_device_t *stdev;
    bool session_allowed = true;

    if (!platform) {
        ALOGE("%s: NULL platform", __func__);
        return false;
    }
    my_data = (struct platform_data *)platform;
    if (!my_data->stdev) {
        ALOGE("%s: platform stdev data is NULL", __func__);
        return false;
    }
    stdev = my_data->stdev;

    if (!stdev->sw_mad) {
        /* hw_mad case only applicable only when sound_model is loaded */
        if (sound_model_loaded) {
            if (stdev->tx_concurrency_active > 0)
                session_allowed = false;
            stdev->audio_concurrency_active = session_allowed ? false: true;
        }
    } else {
        /* sw_mad case applicable only before sound_model is loaded */
        /* num_sessions does not reflect current session yet */
        if (!sound_model_loaded) {
            if ((stdev->tx_concurrency_active > 0) ||
                (stdev->rx_concurrency_disabled &&
                stdev->rx_concurrency_active > 0 &&
                (num_sessions + 1) > stdev->rx_conc_max_st_ses)) {
                session_allowed = false;
            } else {
                stdev->audio_concurrency_active = false;
            }
        }
    }

    ALOGD("%s: stdev->audio_concurrency_active %d session_allowed %d", __func__,
          stdev->audio_concurrency_active, session_allowed);
    return session_allowed;
}

#if 0
int platform_stdev_connect_mad
(
   void *platform,
   bool is_ape
)
{
    int status = 0;
    /* This mixer control is only valid for CPE supported codec */
    struct platform_data *my_data = (struct platform_data *)platform;
    sound_trigger_device_t *stdev = my_data->stdev;
    int status = 0;
    struct mixer_ctl *ctl = NULL;
    const char *mixer_ctl_name = "MAD_SEL MUX";

    if (stdev->sw_mad)
        return 0;

    ctl = mixer_get_ctl_by_name(stdev->mixer, mixer_ctl_name);
    if (!ctl) {
        ALOGE("%s: ERROR. Could not get ctl for mixer cmd - %s",
        __func__, mixer_ctl_name);
        return -EINVAL;
    }
    if(is_ape)
        status = mixer_ctl_set_enum_by_string(ctl, "MSM");
    else
        status = mixer_ctl_set_enum_by_string(ctl, "SPE");

    if (status)
        ALOGE("%s: ERROR. Mixer ctl set failed", __func__);

    return status;
}
#endif
