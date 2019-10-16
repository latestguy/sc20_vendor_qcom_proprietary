/* sound_trigger_platform.c
 *
 * Copyright (c) 2013-2017 Qualcomm Technologies, Inc.
 * All Rights Reserved.
 * Confidential and Proprietary - Qualcomm Technologies, Inc.
 */

#define LOG_TAG "sound_trigger_platform"
/*#define LOG_NDEBUG 0 */
#define LOG_NDDEBUG 0

#include <cutils/log.h>
#include <dlfcn.h>
#include <expat.h>
#include "sound_trigger_platform.h"
#include <errno.h>
#include <cutils/str_parms.h>

/* macros used to verify all param id tags defined for vendor uuid
   in platform file */
#define PARAM_LOAD_SOUND_MODEL_BIT (1 << 0)
#define PARAM_UNLOAD_SOUND_MODEL_BIT (1 << 1)
#define PARAM_CONFIDENCE_LEVELS_BIT (1 << 2)
#define PARAM_OPERATION_MODE_BIT (1 << 3)
#define PARAM_ID_BITS \
    (PARAM_LOAD_SOUND_MODEL_BIT | PARAM_UNLOAD_SOUND_MODEL_BIT | \
     PARAM_CONFIDENCE_LEVELS_BIT | PARAM_OPERATION_MODE_BIT)
#define ARRAY_SIZE(a) (sizeof(a) / sizeof((a)[0]))

#define ST_PARAM_KEY_SM_VENDOR_UUID "vendor_uuid"
#define ST_PARAM_KEY_EXECUTION_TYPE "execution_type"
#define ST_PARAM_KEY_MAX_CPE_SESSIONS "max_cpe_sessions"
#define ST_PARAM_KEY_MAX_APE_SESSIONS "max_ape_sessions"
#define ST_PARAM_KEY_LIBRARY "library"
#define ST_PARAM_KEY_MAX_CPE_PHRASES "max_cpe_phrases"
#define ST_PARAM_KEY_MAX_APE_USERS "max_ape_users"
#define ST_PARAM_KEY_MAX_APE_PHRASES "max_ape_phrases"
#define ST_PARAM_KEY_MAX_CPE_USERS "max_cpe_users"
#define ST_PARAM_KEY_LOAD_SOUND_MODEL_IDS "load_sound_model_ids"
#define ST_PARAM_KEY_UNLOAD_SOUND_MODEL_IDS "unload_sound_model_ids"
#define ST_PARAM_KEY_CONFIDENCE_LEVELS_IDS "confidence_levels_ids"
#define ST_PARAM_KEY_OPERATION_MODE_IDS "operation_mode_ids"

#define ST_PARAM_KEY_ENABLE_FAILURE_DETECTION "enable_failure_detection"
#define ST_PARAM_KEY_ADM_CFG_PROFILE "adm_cfg_profile"
#define ST_PARAM_KEY_APP_TYPE "app_type"
#define ST_PARAM_KEY_SAMPLE_RATE "sample_rate"
#define ST_PARAM_KEY_BIT_WIDTH "bit_width"
#define ST_PARAM_KEY_FLUENCE_TYPE "fluence_type"
#define ST_PARAM_KEY_BACKEND_PORT_NAME "backend_port_name"
#define ST_PARAM_KEY_BACKEND_DAI_NAME "backend_dai_name"
#define ST_PARAM_KEY_CHANNEL_COUNT "channel_count"

#define ST_PARAM_KEY_DEVICE_HANDSET_CPE "DEVICE_HANDSET_MIC_CPE"
#define ST_PARAM_KEY_DEVICE_HANDSET_DMIC_APE "DEVICE_HANDSET_DMIC_APE"
#define ST_PARAM_KEY_DEVICE_HANDSET_QMIC_APE "DEVICE_HANDSET_QMIC_APE"
#define ST_PARAM_KEY_DEVICE_HANDSET_APE "DEVICE_HANDSET_MIC_APE"
#define ST_PARAM_KEY_DEVICE_HANDSET_APE_PP "DEVICE_HANDSET_MIC_PP_APE"

#define ST_BACKEND_PORT_NAME_MAX_SIZE 25
#define SOUND_TRIGGER_SAMPLING_RATE_48000 (48000)
#define SOUND_TRIGGER_CHANNEL_MODE_MONO (1)
#define SOUND_TRIGGER_CHANNEL_MODE_STEREO (2)
#define SOUND_TRIGGER_CHANNEL_MODE_QUAD (4)
#define ST_MAX_LENGTH_MIXER_CONTROL 128

typedef int  (*acdb_loader_init_v1_t)(const char *);
typedef void (*acdb_loader_deallocate_t)(void);
typedef void (*acdb_loader_send_listen_device_cal_t)(int, int, int, int);
typedef int  (*acdb_loader_send_listen_lsm_cal_t)(int, int, int, int);


typedef enum {
    TAG_ROOT,
    TAG_COMMON,
    TAG_ACDB,
    TAG_SOUND_MODEL,
    TAG_ADM_APP_TYPE_CFG
} st_xml_tags_t;

typedef void (*st_xml_process_fn)(void *platform, const XML_Char **attr);
static void platform_stdev_process_kv_params(void *platform, const XML_Char **attr);

/* function pointers for xml tag info parsing */
static st_xml_process_fn process_table[] = {
    [TAG_ROOT] = NULL,
    [TAG_COMMON] = platform_stdev_process_kv_params,
    [TAG_ACDB] = platform_stdev_process_kv_params,
    [TAG_SOUND_MODEL] = platform_stdev_process_kv_params,
    [TAG_ADM_APP_TYPE_CFG] = platform_stdev_process_kv_params
};

struct st_device_index
st_device_name_idx[ST_DEVICE_EXEC_MODE_MAX][ST_DEVICE_MAX] = {
   {
       {"DEVICE_HANDSET_APE_ACDB_ID", ST_DEVICE_HANDSET_MIC},
       {"DEVICE_HANDSET_DMIC_APE", ST_DEVICE_HANDSET_DMIC},
       {"DEVICE_HANDSET_QMIC_APE", ST_DEVICE_HANDSET_QMIC},
       {"DEVICE_HANDSET_MIC_PP_APE", ST_DEVICE_HANDSET_MIC_PP},
   },
   {
       {"DEVICE_HANDSET_CPE_ACDB_ID", ST_DEVICE_HANDSET_MIC},
   },
};

static const char * const
st_device_table[ST_DEVICE_EXEC_MODE_MAX][ST_DEVICE_MAX] = {
    {
        /* ADSP SVA devices */
        [ST_DEVICE_NONE] = "none",
        [ST_DEVICE_HANDSET_MIC] = "listen-ape-handset-mic",
        [ST_DEVICE_HANDSET_DMIC] = "listen-ape-handset-dmic",
        [ST_DEVICE_HANDSET_QMIC] = "listen-ape-handset-qmic",
        [ST_DEVICE_HANDSET_MIC_PP] = "listen-ape-handset-mic-preproc",
    },
    {
        /* CPE SVA devices */
        [ST_DEVICE_NONE] = "none",
        [ST_DEVICE_HANDSET_MIC] = "listen-cpe-handset-mic",
    },
};

/* ACDB IDs for each device for both CDSP and ADSP */
static int acdb_device_table[ST_DEVICE_EXEC_MODE_MAX][ST_DEVICE_MAX] = {
    {
      [ST_DEVICE_NONE] = -1,
      [ST_DEVICE_HANDSET_MIC] = DEVICE_HANDSET_APE_ACDB_ID,
      [ST_DEVICE_HANDSET_DMIC] = DEVICE_HANDSET_DMIC_APE,
      [ST_DEVICE_HANDSET_QMIC] = DEVICE_HANDSET_QMIC_APE,
      [ST_DEVICE_HANDSET_MIC_PP] = DEVICE_HANDSET_MIC_PP_APE,
    },
    {
      [ST_DEVICE_NONE] = -1,
      [ST_DEVICE_HANDSET_MIC] = DEVICE_HANDSET_CPE_ACDB_ID,
    }
};

/* Qualcomm vendorUuid for SVA soundmodel */
static const sound_trigger_uuid_t qc_uuid =
    { 0x68ab2d40, 0xe860, 0x11e3, 0x95ef, { 0x00, 0x02, 0xa5, 0xd5, 0xc5, 0x1b } };

struct platform_data {
    sound_trigger_device_t *stdev;
    void *acdb_handle;
    acdb_loader_send_listen_device_cal_t acdb_send_device_cal;
    acdb_loader_send_listen_lsm_cal_t acdb_send_lsm_cal;
    acdb_loader_deallocate_t acdb_deinit;
    struct st_vendor_info *vendor_uuid_info;
    int param_tag_tracker;

    int xml_version;
    st_xml_tags_t st_xml_tag;
    struct str_parms *kvpairs;
    st_codec_backend_cfg_t codec_backend_cfg;
    char backend_port[ST_BACKEND_PORT_NAME_MAX_SIZE];
    char backend_dai_name[ST_BE_DAI_NAME_MAX_LENGTH];
};

static int string_to_profile_type(const char *str, st_profile_type_t *type)
{
    int ret = 0;

    if (!strncmp(str, "NONE", sizeof("NONE"))) {
        *type = ST_PROFILE_TYPE_NONE;
    } else if (!strncmp(str, "DEFAULT", sizeof("DEFAULT"))) {
        *type = ST_PROFILE_TYPE_DEFAULT;
    } else if (!strncmp(str, "UNPROCESSED", sizeof("UNPROCESSED"))) {
        *type = ST_PROFILE_TYPE_UNPROCESSED;
    } else if (!strncmp(str, "FLUENCE", sizeof("FLUENCE"))) {
        *type = ST_PROFILE_TYPE_FLUENCE;
    } else {
        ALOGE("unknown profile string %s", str);
        ret = -EINVAL;
    }

    return ret;
}

static int string_to_fluence_type(const char *str, st_fluence_type_t *type)
{
    int ret = 0;

    if (!strncmp(str, "NONE", sizeof("NONE"))) {
        *type = ST_FLUENCE_TYPE_NONE;
    } else if (!strncmp(str, "FLUENCE", sizeof("FLUENCE"))) {
        *type = ST_FLUENCE_TYPE_MONO;
    } else if (!strncmp(str, "FLUENCE_DMIC", sizeof("FLUENCE_DMIC"))) {
        *type = ST_FLUENCE_TYPE_DMIC;
    } else if (!strncmp(str, "FLUENCE_QMIC", sizeof("FLUENCE_QMIC"))) {
        *type = ST_FLUENCE_TYPE_QMIC;
    } else {
        ALOGE("unknown fluence string %s", str);
        ret = -EINVAL;
    }
    return ret;
}

static int get_channel_cnt_from_fluence_type(st_fluence_type_t type)
{
    int channel_cnt = 0;

    switch (type) {
    case ST_FLUENCE_TYPE_NONE:
    case ST_FLUENCE_TYPE_MONO:
        channel_cnt = 1;
        break;
    case ST_FLUENCE_TYPE_DMIC:
        channel_cnt = 2;
        break;
    case ST_FLUENCE_TYPE_QMIC:
        channel_cnt = 4;
        break;
    default:
        ALOGE("%s: Invalid fluence type", __func__);
    }

    return channel_cnt;
}

int platform_stdev_set_acdb_id(void *userdata __unused, const char* device, int acdb_id)
{
    int i, j;
    int ret = 0;
    int dev_idx = ST_DEVICE_NONE;

    if (device == NULL) {
       ALOGE("%s: device name is NULL", __func__);
       ret = -ENODEV;
       goto done;
    }

    for (i = 0; i < ST_DEVICE_EXEC_MODE_MAX; i++) {
        for (j = 0; j < ST_DEVICE_MAX; i++) {
           if(strcmp(st_device_name_idx[i][j].name, device) == 0)
               dev_idx = st_device_name_idx[i][j].index;
               break;
        }
        if (dev_idx != ST_DEVICE_NONE)
            break;
    }
    if (dev_idx == ST_DEVICE_NONE) {
       ALOGE("%s: Could not find index for device name = %s",
               __func__, device);
       ret = -ENODEV;
       goto done;
    }

    acdb_device_table[i][dev_idx] = acdb_id;

done:
    return ret;

}

void platform_stdev_set_default_config(struct platform_data *platform)
{
    sound_trigger_device_t *stdev = platform->stdev;

    stdev->run_on_ape =  true;
    stdev->max_ape_sessions = 1;
    stdev->avail_ape_phrases = 1;
    stdev->avail_ape_users = 1;
    stdev->max_cpe_sessions = 1;
    stdev->avail_cpe_phrases  = 1;
    stdev->avail_cpe_users = 1;
    stdev->rx_conc_max_st_ses = UINT_MAX;
    platform->codec_backend_cfg.sample_rate = SOUND_TRIGGER_SAMPLING_RATE_48000;
    platform->codec_backend_cfg.format = PCM_FORMAT_S16_LE;
    platform->codec_backend_cfg.channel_count = SOUND_TRIGGER_CHANNEL_MODE_MONO;
}

void platform_stdev_set_config(void *userdata, const char* param, const char* value)
{
    struct platform_data *platform = userdata;
    sound_trigger_device_t *stdev;

    if (!platform) {
        ALOGE("%s: platform data NULL", __func__);
        return;
    }
    if (!platform->stdev) {
        ALOGE("%s: platform stdev data NULL", __func__);
        return;
    }
    stdev = platform->stdev;
    if (!strcmp(param, "execution_type")) {
        if(!strcmp(value, "CPE"))
            stdev->run_on_ape =  false;
        else if(!strcmp(value, "CPE_AND_APE"))
            stdev->transition_enabled = true;
    }
    else if (!strcmp(param, "max_ape_sessions")) {
        stdev->max_ape_sessions = atoi(value);
    }
    else if (!strcmp(param, "max_cpe_sessions")) {
        stdev->max_cpe_sessions = atoi(value);
    }
    else if (!strcmp(param, "max_cpe_phrases")) {
        stdev->avail_cpe_phrases = atoi(value);
    }
    else if (!strcmp(param, "max_cpe_users")) {
        stdev->avail_cpe_users = atoi(value);
    }
    else if (!strcmp(param, "max_ape_phrases")) {
        stdev->avail_ape_phrases = atoi(value);
    }
    else if (!strcmp(param, "max_ape_users")) {
        stdev->avail_ape_users = atoi(value);
    }
    else if (!strcmp(param, "rx_concurrency_disabled")) {
        stdev->rx_concurrency_disabled =
           (0 == strncasecmp(value, "true", 4))? true:false;
        ALOGD("%s:rx_concurrency_disabled = %d",
                 __func__, stdev->rx_concurrency_disabled);
    }
    else if (!strcmp(param, "rx_conc_max_st_ses")) {
        stdev->rx_conc_max_st_ses = atoi(value);
        ALOGD("%s:rx_conc_max_st_ses = %d",
                 __func__, stdev->rx_conc_max_st_ses);
    }
    else if (!strcmp(param, "enable_failure_detection")) {
        stdev->detect_failure =
           (0 == strncasecmp(value, "true", 4))? true:false;
    }
    else if (!strcmp(param, "sw_mad")) {
        stdev->sw_mad =
           (0 == strncasecmp(value, "true", 4))? true:false;
    }
    else if (!strcmp(param, "support_lec")) {
        stdev->support_lec =
           (0 == strncasecmp(value, "true", 4))? true:false;
    } else
        ALOGD("%s: unknown config param, ignoring..", __func__);
}

static void init_codec_backend_cfg_mixer_ctl(struct platform_data *my_data)
{
    char mixer_ctl[128];

    if (strcmp(my_data->backend_port, "")) {
        snprintf(mixer_ctl, sizeof(mixer_ctl),
                "%s SampleRate", my_data->backend_port);
        my_data->codec_backend_cfg.samplerate_mixer_ctl = strdup(mixer_ctl);

        snprintf(mixer_ctl, sizeof(mixer_ctl),
                "%s Format", my_data->backend_port);
        my_data->codec_backend_cfg.format_mixer_ctl = strdup(mixer_ctl);

        snprintf(mixer_ctl, sizeof(mixer_ctl),
                "%s Channels", my_data->backend_port);
        my_data->codec_backend_cfg.channelcount_mixer_ctl = strdup(mixer_ctl);
    }
}

static void platform_stdev_send_adm_app_type_cfg
(
   void *platform
)
{
    struct platform_data *my_data = (struct platform_data *)platform;
    sound_trigger_device_t *stdev = my_data->stdev;
    const char *mixer_ctl_name = "Listen App Type Config";
    struct mixer_ctl *ctl;
    struct listnode *p_node, *temp_node;
    struct adm_app_type_cfg_info *cfg_info;;
    int app_type_cfg[ST_MAX_LENGTH_MIXER_CONTROL] = {-1};
    int i, len = 0, num_app_types = 0;
    bool update;

    ctl = mixer_get_ctl_by_name(stdev->mixer, mixer_ctl_name);
    if (!ctl) {
        ALOGE("%s: ERROR. Could not get ctl for mixer cmd - %s",
        __func__, mixer_ctl_name);
        return;
    }

    app_type_cfg[len++] = num_app_types;
    list_for_each_safe(p_node, temp_node, &stdev->adm_cfg_list) {
        cfg_info = node_to_item(p_node, struct adm_app_type_cfg_info, list_node);
        update = true;
        for (i = 0; i < len; i = i+3) {
        /* avoid updating duplicate app types */
            if (app_type_cfg[i+1] == -1) {
                break;
            } else if (app_type_cfg[i+1] == cfg_info->app_type) {
                update = false;
                break;
            }
        }

        if (update && ((len + 3) <= ST_MAX_LENGTH_MIXER_CONTROL)) {
            num_app_types += 1;
            app_type_cfg[len++] = cfg_info->app_type;
            app_type_cfg[len++] = cfg_info->sample_rate;
            app_type_cfg[len++] = cfg_info->bit_width;
        }
    }

    ALOGV("%s: num_app_types: %d", __func__, num_app_types);
    if (num_app_types) {
        app_type_cfg[0] = num_app_types;
        mixer_ctl_set_array(ctl, app_type_cfg, len);
    }
}

static int string_to_uuid(const char *str, sound_trigger_uuid_t *uuid)
{
    int tmp[10];

    if (str == NULL || uuid == NULL) {
        return -EINVAL;
    }

    if (sscanf(str, "%08x-%04x-%04x-%04x-%02x%02x%02x%02x%02x%02x",
            tmp, tmp + 1, tmp + 2, tmp + 3, tmp + 4, tmp + 5, tmp + 6,
            tmp + 7, tmp+ 8, tmp+ 9) < 10) {
        return -EINVAL;
    }
    uuid->timeLow = (uint32_t)tmp[0];
    uuid->timeMid = (uint16_t)tmp[1];
    uuid->timeHiAndVersion = (uint16_t)tmp[2];
    uuid->clockSeq = (uint16_t)tmp[3];
    uuid->node[0] = (uint8_t)tmp[4];
    uuid->node[1] = (uint8_t)tmp[5];
    uuid->node[2] = (uint8_t)tmp[6];
    uuid->node[3] = (uint8_t)tmp[7];
    uuid->node[4] = (uint8_t)tmp[8];
    uuid->node[5] = (uint8_t)tmp[9];

    return 0;
}

static int platform_stdev_load_sm_lib(struct st_vendor_info *sm_info, const char *name)
{
    int ret = 0;
    const char *error;

    sm_info->smlib_handle = dlopen(name, RTLD_NOW);
    if (!sm_info->smlib_handle) {
        ALOGE("%s: dlopen failed for %s", __func__, name);
        ret = -EINVAL;
        goto error_exit;
    }

    /* clear any existing errors */
    dlerror();
    sm_info->generate_st_phrase_recognition_event =
        (smlib_generate_sound_trigger_phrase_recognition_event_t)
        dlsym(sm_info->smlib_handle,
              "generate_sound_trigger_phrase_recognition_event");

    if ((error = dlerror()) != NULL) {
        ALOGE("%s: dlsym error %s for generate_sound_trigger_phrase_recognition_event",
              __func__, error);
        ret = -EINVAL;
        goto error_exit;
    }

    dlerror();
    sm_info->generate_st_recognition_config_payload =
        (smlib_generate_sound_trigger_recognition_config_payload_t)
        dlsym(sm_info->smlib_handle,
              "generate_sound_trigger_recognition_config_payload");

    if ((error = dlerror()) != NULL) {
        ALOGE("%s: dlsym error %s for generate_sound_trigger_recognition_config_payload",
              __func__, error);
        ret = -EINVAL;
        goto error_exit;
    }

    return 0;

error_exit:
    return ret;
}

static int platform_stdev_set_module_param_ids
(
   struct st_module_param_info *mp_info,
   char *value
)
{
    char *id, *test_r;
    int ret = 0;

    id = strtok_r(value, ", ", &test_r);
    if (!id) {
        ALOGE("%s: incorrect module id", __func__);
        ret = -EINVAL;
        goto error_exit;
    }
    mp_info->module_id = strtoul(id, NULL, 16);

    id = strtok_r(NULL, ", ", &test_r);
    if (!id) {
        ALOGE("%s: incorrect param id", __func__);
        ret = -EINVAL;
        goto error_exit;
    }
    mp_info->param_id = strtoul(id, NULL, 16);
    return 0;

error_exit:
    return ret;
}

static int platform_stdev_set_sm_config_params
(
   void *platform,
   struct str_parms *parms
)
{
    struct platform_data *my_data = (struct platform_data *)platform;
    sound_trigger_device_t *stdev = my_data->stdev;
    struct st_vendor_info *sm_info;
    char str_value[128];
    char *kv_pairs = str_parms_to_str(parms);
    int ret = 0, err, value;

    ALOGV("%s: enter: %s", __func__, kv_pairs);
    if (kv_pairs == NULL) {
        ALOGE("%s: key-value pair is NULL", __func__);
        return -EINVAL;
    }

    /* Allocate the vendor sound model config.
       Set the platform configured params.
       Push this sound model config to list.
       */
    sm_info = calloc(1, sizeof(*sm_info));
    if (!sm_info) {
        ALOGE("%s: sm_info allcoation failed", __func__);
        return -ENOMEM;
    }
    /* initialize to deault config */
    sm_info->sample_rate = SOUND_TRIGGER_SAMPLING_RATE;
    sm_info->format = PCM_FORMAT_S16_LE;
    sm_info->channel_count = SOUND_TRIGGER_CHANNEL_MODE_MONO;
    sm_info->profile_type = ST_PROFILE_TYPE_NONE;
    sm_info->fluence_type = ST_FLUENCE_TYPE_MONO;

    /* Set the platform configured params */
    err = str_parms_get_str(parms, ST_PARAM_KEY_SM_VENDOR_UUID,
                            str_value, sizeof(str_value));
    if (err >= 0) {
        str_parms_del(parms, ST_PARAM_KEY_SM_VENDOR_UUID);
        if (string_to_uuid(str_value, &sm_info->uuid) < 0) {
           ALOGE("%s: string_to_uuid failed", __func__);
           ret = -EINVAL;
           goto error_exit;
        }
    }

    err = str_parms_get_str(parms, ST_PARAM_KEY_APP_TYPE,
                            str_value, sizeof(str_value));
    if (err >= 0) {
        str_parms_del(parms, ST_PARAM_KEY_APP_TYPE);
        sm_info->app_type = strtoul(str_value, NULL, 16);
    }

    err = str_parms_get_str(parms, ST_PARAM_KEY_LIBRARY,
                            str_value, sizeof(str_value));
    if (err >= 0) {
        str_parms_del(parms, ST_PARAM_KEY_LIBRARY);
        /* if soundmodel library for ISV vendor uuid is mentioned, use it. If not
           ignore and continue sending the opaque data from HAL to DSP */
        if (strcmp(str_value, "none"))
            platform_stdev_load_sm_lib(sm_info, str_value);
    }

    err = str_parms_get_int(parms, ST_PARAM_KEY_MAX_CPE_PHRASES, &value);
    if (err >= 0) {
        str_parms_del(parms, ST_PARAM_KEY_MAX_CPE_PHRASES);
        sm_info->avail_cpe_phrases = value;
    }

    err = str_parms_get_int(parms, ST_PARAM_KEY_MAX_CPE_USERS, &value);
    if (err >= 0) {
        str_parms_del(parms, ST_PARAM_KEY_MAX_CPE_USERS);
        sm_info->avail_cpe_users = value;
    }

    err = str_parms_get_int(parms, ST_PARAM_KEY_MAX_APE_PHRASES, &value);
    if (err >= 0) {
        str_parms_del(parms, ST_PARAM_KEY_MAX_APE_PHRASES);
        sm_info->avail_ape_phrases = value;
    }

    err = str_parms_get_int(parms, ST_PARAM_KEY_MAX_APE_USERS, &value);
    if (err >= 0) {
        str_parms_del(parms, ST_PARAM_KEY_MAX_APE_USERS);
        sm_info->avail_ape_users = value;
    }

    err = str_parms_get_int(parms, ST_PARAM_KEY_SAMPLE_RATE, &value);
    if (err >= 0) {
        str_parms_del(parms, ST_PARAM_KEY_SAMPLE_RATE);
        sm_info->sample_rate = value;
    }

    err = str_parms_get_int(parms, ST_PARAM_KEY_BIT_WIDTH, &value);
    if (err >= 0) {
        str_parms_del(parms, ST_PARAM_KEY_BIT_WIDTH);
        if (value == 16) {
            sm_info->format = PCM_FORMAT_S16_LE;
        } else if (value == 24) {
            sm_info->format = PCM_FORMAT_S24_LE;
        } else {
            ALOGE("%s: invalid bit width for profile", __func__);
            ret = -EINVAL;
            goto error_exit;
        }
    }

    err = str_parms_get_int(parms, ST_PARAM_KEY_CHANNEL_COUNT, &value);
    if (err >= 0) {
        str_parms_del(parms, ST_PARAM_KEY_CHANNEL_COUNT);
        sm_info->channel_count = value;
    }

    err = str_parms_get_str(parms, ST_PARAM_KEY_ADM_CFG_PROFILE,
                            str_value, sizeof(str_value));
    if (err >= 0) {
        str_parms_del(parms, ST_PARAM_KEY_ADM_CFG_PROFILE);
        if (string_to_profile_type(str_value, &sm_info->profile_type) < 0) {
            ALOGE("%s: string_to_profile_type failed", __func__);
            ret = -EINVAL;
            goto error_exit;
        }
    }

    err = str_parms_get_str(parms, ST_PARAM_KEY_FLUENCE_TYPE,
                            str_value, sizeof(str_value));
    if (err >= 0) {
        str_parms_del(parms, ST_PARAM_KEY_FLUENCE_TYPE);
        if (string_to_fluence_type(str_value, &sm_info->fluence_type) < 0) {
            ALOGE("%s: string_to_fluence_type failed", __func__);
            ret = -EINVAL;
            goto error_exit;
        }
    }

    err = str_parms_get_str(parms, ST_PARAM_KEY_LOAD_SOUND_MODEL_IDS,
                            str_value, sizeof(str_value));
    if (err >= 0) {
        str_parms_del(parms, ST_PARAM_KEY_LOAD_SOUND_MODEL_IDS);
        platform_stdev_set_module_param_ids(&sm_info->params[LOAD_SOUND_MODEL], str_value);
        my_data->param_tag_tracker |= PARAM_LOAD_SOUND_MODEL_BIT;
    }

    err = str_parms_get_str(parms, ST_PARAM_KEY_UNLOAD_SOUND_MODEL_IDS,
                            str_value, sizeof(str_value));
    if (err >= 0) {
        str_parms_del(parms, ST_PARAM_KEY_UNLOAD_SOUND_MODEL_IDS);
        ret = platform_stdev_set_module_param_ids(&sm_info->params[UNLOAD_SOUND_MODEL], str_value);
        if (ret)
            goto error_exit;
        my_data->param_tag_tracker |= PARAM_UNLOAD_SOUND_MODEL_BIT;
    }

    err = str_parms_get_str(parms, ST_PARAM_KEY_CONFIDENCE_LEVELS_IDS,
                            str_value, sizeof(str_value));
    if (err >= 0) {
        str_parms_del(parms, ST_PARAM_KEY_CONFIDENCE_LEVELS_IDS);
        platform_stdev_set_module_param_ids(&sm_info->params[CONFIDENCE_LEVELS], str_value);
        my_data->param_tag_tracker |= PARAM_CONFIDENCE_LEVELS_BIT;
    }

    err = str_parms_get_str(parms, ST_PARAM_KEY_OPERATION_MODE_IDS,
                            str_value, sizeof(str_value));
    if (err >= 0) {
        str_parms_del(parms, ST_PARAM_KEY_OPERATION_MODE_IDS);
        ret = platform_stdev_set_module_param_ids(&sm_info->params[OPERATION_MODE], str_value);
        if (ret)
            goto error_exit;
        my_data->param_tag_tracker |= PARAM_OPERATION_MODE_BIT;
    }

    /* store combined keyphrases/users of all engines */
    stdev->avail_ape_phrases += sm_info->avail_ape_phrases;
    stdev->avail_ape_users += sm_info->avail_ape_users;
    stdev->avail_cpe_phrases += sm_info->avail_cpe_phrases;
    stdev->avail_cpe_users += sm_info->avail_cpe_users;

    list_add_tail(&my_data->stdev->vendor_uuid_list,
                      &sm_info->list_node);
    return 0;

error_exit:
    if (sm_info)
        free(sm_info);
    return ret;
}

static int platform_stdev_set_adm_app_type_cfg_params
(
   void *platform,
   struct str_parms *parms
)
{
    struct platform_data *my_data = (struct platform_data *)platform;
    struct adm_app_type_cfg_info *cfg_info;
    char str_value[128];
    char *kv_pairs = str_parms_to_str(parms);
    int ret = 0, err, value;

    ALOGV("%s: enter: %s", __func__, kv_pairs);
    if (kv_pairs == NULL) {
        ALOGE("%s: key-value pair is NULL", __func__);
        return -EINVAL;
    }

    /* Allocate the app type profile info
       Set the platform configured profile params.
       Push this profile to platform data.
       */
    cfg_info = calloc(1, sizeof(*cfg_info));
    if (!cfg_info) {
        ALOGE("%s: cfg info allcoation failed", __func__);
        return -ENOMEM;
    }

    /* initialize to deault config */
    cfg_info->profile_type = ST_PROFILE_TYPE_DEFAULT;
    cfg_info->app_type = SOUND_TRIGGER_DEVICE_DEFAULT_APP_TYPE;
    cfg_info->sample_rate = SOUND_TRIGGER_SAMPLING_RATE;
    cfg_info->bit_width = SOUND_TRIGGER_BIT_WIDTH;

    /* Set the platform configured params */
    err = str_parms_get_str(parms, ST_PARAM_KEY_ADM_CFG_PROFILE,
                            str_value, sizeof(str_value));
    if (err >= 0) {
        str_parms_del(parms, ST_PARAM_KEY_ADM_CFG_PROFILE);
        if (string_to_profile_type(str_value, &cfg_info->profile_type) < 0) {
            ALOGE("%s: string_to_profile_type failed", __func__);
            ret = -EINVAL;
            goto error_exit;
        }
    }

    err = str_parms_get_int(parms, ST_PARAM_KEY_APP_TYPE, &value);
    if (err >= 0) {
        str_parms_del(parms, ST_PARAM_KEY_APP_TYPE);
        cfg_info->app_type = value;
    }

    err = str_parms_get_int(parms, ST_PARAM_KEY_SAMPLE_RATE, &value);
    if (err >= 0) {
        str_parms_del(parms, ST_PARAM_KEY_SAMPLE_RATE);
        cfg_info->sample_rate = value;
    }

    err = str_parms_get_int(parms, ST_PARAM_KEY_BIT_WIDTH, &value);
    if (err >= 0) {
        str_parms_del(parms, ST_PARAM_KEY_BIT_WIDTH);
        cfg_info->bit_width = value;
    }

    list_add_tail(&my_data->stdev->adm_cfg_list,
                  &cfg_info->list_node);
    free(kv_pairs);
    return 0;

error_exit:
    if (cfg_info)
        free(cfg_info);
    return ret;
}

int platform_stdev_get_device_app_type
(
   void *platform,
   st_profile_type_t profile_type
)
{
    struct platform_data *my_data = (struct platform_data *)platform;
    sound_trigger_device_t *stdev = my_data->stdev;
    struct listnode *p_node, *temp_node;
    struct adm_app_type_cfg_info *cfg_info;;
    int app_type = 0;

    list_for_each_safe(p_node, temp_node, &stdev->adm_cfg_list) {
        cfg_info = node_to_item(p_node, struct adm_app_type_cfg_info, list_node);
        if (cfg_info->profile_type == profile_type) {
            app_type = cfg_info->app_type;
            break;
        }
    }

    ALOGV("%s: app type %d for profile %d", __func__, app_type, profile_type);
    return app_type;
}

void platform_stdev_set_vendor_config
(
   void *userdata,
   const char** attr,
   bool is_new_uuid
)
{
    struct platform_data *platform = userdata;
    sound_trigger_device_t *stdev;
    struct st_vendor_info *vendor_uuid_info;

    if (!platform || !attr) {
        ALOGE("%s: NULL attr or platform ", __func__);
        return;
    }
    if (!platform->stdev) {
        ALOGE("%s: platform stdev data NULL", __func__);
        return;
    }
    stdev = platform->stdev;

    if (is_new_uuid) {
        vendor_uuid_info = calloc(1, sizeof(struct st_vendor_info));
        if(!vendor_uuid_info) {
            ALOGE("%s: can't allocate vendor_uuid_info", __func__);
            goto cleanup;
        }
        if(string_to_uuid(attr[1], &vendor_uuid_info->uuid) < 0) {
            ALOGE("%s: string_to_uuid failed", __func__);
            goto cleanup;
        }
        vendor_uuid_info->app_type = strtoul(attr[3], NULL, 16);

        /* if soundmodel library for ISV vendor uuid is mentioned, use it. If not
           ignore and continue sending the opaque data from HAL to DSP */
        if(!strcmp(attr[4], "library") && strcmp(attr[5], "none")) {
            ALOGV("%s: vendor library present %s", __func__, attr[5]);
            vendor_uuid_info->smlib_handle = dlopen(attr[5], RTLD_NOW);
            if (!vendor_uuid_info->smlib_handle) {
                ALOGE("%s: ERROR. dlopen failed for %s", __func__, attr[5]);
                goto cleanup;
            }
            vendor_uuid_info->generate_st_phrase_recognition_event =
                (smlib_generate_sound_trigger_phrase_recognition_event_t)
                dlsym(vendor_uuid_info->smlib_handle,
                      "generate_sound_trigger_phrase_recognition_event");

            if (!vendor_uuid_info->generate_st_phrase_recognition_event) {
                ALOGE("%s: dlsym error %s for generate_sound_trigger_phrase_recognition_event",
                      __func__, dlerror());
                goto cleanup;
            }

            vendor_uuid_info->generate_st_recognition_config_payload =
                (smlib_generate_sound_trigger_recognition_config_payload_t)
                dlsym(vendor_uuid_info->smlib_handle,
                      "generate_sound_trigger_recognition_config_payload");

            if (!vendor_uuid_info->generate_st_recognition_config_payload) {
                ALOGE("%s: dlsym error %s for generate_sound_trigger_recognition_config_payload",
                      __func__, dlerror());
                goto cleanup;
            }
        }
        platform->vendor_uuid_info = vendor_uuid_info;
        return;
    }

    if(!platform->vendor_uuid_info)
        return;

    vendor_uuid_info = platform->vendor_uuid_info;

    if (!strcmp(attr[1], "load_sound_model")) {
        vendor_uuid_info->params[LOAD_SOUND_MODEL].module_id =
                                                strtoul(attr[3], NULL, 16);
        vendor_uuid_info->params[LOAD_SOUND_MODEL].param_id =
                                                strtoul(attr[5], NULL, 16);
        platform->param_tag_tracker |= PARAM_LOAD_SOUND_MODEL_BIT;
    } else if (!strcmp(attr[1], "unload_sound_model")) {
        vendor_uuid_info->params[UNLOAD_SOUND_MODEL].module_id =
                                                strtoul(attr[3], NULL, 16);
        vendor_uuid_info->params[UNLOAD_SOUND_MODEL].param_id =
                                                strtoul(attr[5], NULL, 16);
        platform->param_tag_tracker |= PARAM_UNLOAD_SOUND_MODEL_BIT;
    } else if (!strcmp(attr[1], "confidence_levels")) {
        vendor_uuid_info->params[CONFIDENCE_LEVELS].module_id =
                                                strtoul(attr[3], NULL, 16);
        vendor_uuid_info->params[CONFIDENCE_LEVELS].param_id =
                                                strtoul(attr[5], NULL, 16);
        platform->param_tag_tracker |= PARAM_CONFIDENCE_LEVELS_BIT;
    } else if (!strcmp(attr[1], "operation_mode")) {
        vendor_uuid_info->params[OPERATION_MODE].module_id =
                                                strtoul(attr[3], NULL, 16);
        vendor_uuid_info->params[OPERATION_MODE].param_id =
                                                strtoul(attr[5], NULL, 16);
        platform->param_tag_tracker |= PARAM_OPERATION_MODE_BIT;
    }
    return;

cleanup:
    if(vendor_uuid_info) {
        if(vendor_uuid_info->smlib_handle)
            dlclose(vendor_uuid_info->smlib_handle);
        free(vendor_uuid_info);
    }
}

static int platform_set_acdb_ids
(
   void *platform,
   struct str_parms *parms
)
{
    int ret = 0, value, err;

    err = str_parms_get_int(parms, ST_PARAM_KEY_DEVICE_HANDSET_CPE, &value);
    if (err >= 0) {
        str_parms_del(parms, ST_PARAM_KEY_DEVICE_HANDSET_CPE);
        ret = platform_stdev_set_acdb_id(platform, ST_PARAM_KEY_DEVICE_HANDSET_CPE, value);
        if (ret)
            goto exit;
    }

    err = str_parms_get_int(parms, ST_PARAM_KEY_DEVICE_HANDSET_APE, &value);
    if (err >= 0) {
        str_parms_del(parms, ST_PARAM_KEY_DEVICE_HANDSET_APE);
        ret = platform_stdev_set_acdb_id(platform, ST_PARAM_KEY_DEVICE_HANDSET_APE, value);
        if (ret)
            goto exit;
    }

    err = str_parms_get_int(parms, ST_PARAM_KEY_DEVICE_HANDSET_DMIC_APE, &value);
    if (err >= 0) {
        str_parms_del(parms, ST_PARAM_KEY_DEVICE_HANDSET_DMIC_APE);
        ret = platform_stdev_set_acdb_id(platform, ST_PARAM_KEY_DEVICE_HANDSET_DMIC_APE, value);
        if (ret)
            goto exit;
    }

    err = str_parms_get_int(parms, ST_PARAM_KEY_DEVICE_HANDSET_QMIC_APE, &value);
    if (err >= 0) {
        str_parms_del(parms, ST_PARAM_KEY_DEVICE_HANDSET_QMIC_APE);
        ret = platform_stdev_set_acdb_id(platform, ST_PARAM_KEY_DEVICE_HANDSET_QMIC_APE, value);
        if (ret)
            goto exit;
    }

exit:
    return ret;
}

static int platform_set_common_config
(
   void *platform,
   struct str_parms *parms
)
{
    struct platform_data *my_data = (struct platform_data *)platform;
    sound_trigger_device_t *stdev = my_data->stdev;
    char str_value[128];
    int value, err;

    err = str_parms_get_str(parms, ST_PARAM_KEY_EXECUTION_TYPE,
                            str_value, sizeof(str_value));
    if (err >= 0) {
        str_parms_del(parms, ST_PARAM_KEY_EXECUTION_TYPE);
        if (!strcmp(str_value, "CPE")) {
            stdev->run_on_ape =  false;
            /* There can be scenario where ADSP needs to run in
               SW MAD while CPE mode is supported in the codec.
               ex:apq8009.To handle such cases, overwrite sw_mad
               flag to true in cases where execution mode is set
               to CPE */
            stdev->sw_mad = false;
        }
    }

    err = str_parms_get_int(parms, ST_PARAM_KEY_MAX_CPE_SESSIONS, &value);
    if (err >= 0) {
        str_parms_del(parms, ST_PARAM_KEY_MAX_CPE_SESSIONS);
        stdev->max_cpe_sessions = value;
    }

    err = str_parms_get_int(parms, ST_PARAM_KEY_MAX_APE_SESSIONS, &value);
    if (err >= 0) {
        str_parms_del(parms, ST_PARAM_KEY_MAX_APE_SESSIONS);
        stdev->max_ape_sessions = value;
    }

    err = str_parms_get_str(parms, ST_PARAM_KEY_ENABLE_FAILURE_DETECTION,
                            str_value, sizeof(str_value));
    if (err >= 0) {
        str_parms_del(parms, ST_PARAM_KEY_ENABLE_FAILURE_DETECTION);
        stdev->detect_failure = (!strncasecmp(str_value, "true", 4))? true : false;
    }

    err = str_parms_get_str(parms, ST_PARAM_KEY_BACKEND_PORT_NAME,
                            str_value, sizeof(str_value));
    if (err >= 0) {
        str_parms_del(parms, ST_PARAM_KEY_BACKEND_PORT_NAME);
        if (my_data->stdev->run_on_ape)
            strlcpy(my_data->backend_port,
                    str_value, sizeof(my_data->backend_port));
    }

    err = str_parms_get_str(parms, ST_PARAM_KEY_BACKEND_DAI_NAME,
                            str_value, sizeof(str_value));
    if (err >= 0) {
        str_parms_del(parms, ST_PARAM_KEY_BACKEND_DAI_NAME);
        strlcpy(my_data->backend_dai_name,
                str_value, sizeof(my_data->backend_dai_name));
    }

    return 0;
}


int platform_stdev_set_parameters
(
   void *platform,
   struct str_parms *parms
)
{
    char *kv_pairs = str_parms_to_str(parms);

    ALOGV("%s: enter with key-value pair: %s", __func__, kv_pairs);
    if (!kv_pairs) {
        ALOGE("%s: key-value pair is NULL",__func__);
        return -EINVAL;
    }

    platform_set_common_config(platform, parms);
    platform_set_acdb_ids(platform, parms);
    free(kv_pairs);

    return 0;
}

static void platform_stdev_process_kv_params
(
   void *platform,
   const XML_Char **attr
)
{
    struct platform_data *my_data = (struct platform_data *)platform;

    ALOGV("%s: %s:%s ", __func__, attr[0],attr[1]);
    str_parms_add_str(my_data->kvpairs, (char*)attr[0], (char*)attr[1]);
    ALOGV("%s: exit ",__func__);
    return;
}

static void platform_stdev_process_versioned_xml_data
(
   struct platform_data *my_data,
   const XML_Char *tag_name,
   const XML_Char **attr
)
{
    st_xml_process_fn fn_ptr;

    if (!strcmp(tag_name, "common_config")) {
        my_data->st_xml_tag = TAG_COMMON;
    } else if (!strcmp(tag_name, "acdb_ids")) {
        my_data->st_xml_tag = TAG_ACDB;
    } else if (!strcmp(tag_name, "sound_model_config")) {
        my_data->st_xml_tag = TAG_SOUND_MODEL;
    } else if (!strcmp(tag_name, "adm_config")) {
        my_data->st_xml_tag = TAG_ADM_APP_TYPE_CFG;
    } else if (!strcmp(tag_name, "param")) {
        if ((my_data->st_xml_tag != TAG_ROOT) && (my_data->st_xml_tag != TAG_COMMON) &&
            (my_data->st_xml_tag != TAG_ACDB) && (my_data->st_xml_tag != TAG_SOUND_MODEL) &&
             my_data->st_xml_tag != TAG_ADM_APP_TYPE_CFG) {
            ALOGE("%s: param under unknown tag", __func__);
            return;
        }
        fn_ptr = process_table[my_data->st_xml_tag];
        if (fn_ptr)
            fn_ptr(my_data, attr);
    }
}

static void start_tag(void *userdata, const XML_Char *tag_name,
                      const XML_Char **attr)
{
    int ret;
    struct platform_data *platform = (void *)userdata;

    if (!platform || !tag_name || !attr) {
        ALOGE("%s: NULL platform/tag_name/attr", __func__);
        return;
    }

    if ((platform->st_xml_tag == TAG_ROOT) &&
        !strcmp(tag_name, "param") && !strcmp(attr[0], "version") ) {
        /* This must be the first param for versioned XML file */
        platform->xml_version = strtoul(attr[1], NULL, 16);
    } else if (platform->xml_version) {
        platform_stdev_process_versioned_xml_data(platform, tag_name, attr);
    } else if (!strcmp(tag_name, "device")) {
        if (strcmp(attr[0], "name") || strcmp(attr[2], "value")) {
            ALOGE("%s: 'name' or 'value' not found! for device tag", __func__);
            goto done;
        }
        ret = platform_stdev_set_acdb_id(userdata, (const char *)attr[1], atoi((const char *)attr[3]));
        if (ret < 0) {
            ALOGE("%s: Device %s in platform xml not found, no ACDB ID set!",
                  __func__, attr[1]);
            goto done;
        }
    } else if (!strcmp(tag_name, "ctrl") && !platform->vendor_uuid_info) {
        if (strcmp(attr[0], "name") || strcmp(attr[2], "value")) {
            ALOGE("%s: 'name' or 'value' not found! for ctrl tag", __func__);
            goto done;
        }
        platform_stdev_set_config(userdata, (const char *)attr[1],
                                  (const char *)attr[3]);
    } else if (!strcmp(tag_name, "vendor_uuid") || platform->vendor_uuid_info) {
        platform_stdev_set_vendor_config(userdata, (const char **)attr, !platform->vendor_uuid_info);
    }

done:
    return;
}

static void end_tag(void *userdata, const XML_Char *tag_name)
{
    struct platform_data *platform = userdata;

    if (!platform || !tag_name) {
        ALOGE("%s: NULL attr or platform ", __func__);
        return;
    }

    if (platform->xml_version) {
        if (!strcmp(tag_name, "common_config") || (!strcmp(tag_name, "acdb_ids"))) {
            platform->st_xml_tag = TAG_ROOT;
            platform_stdev_set_parameters(platform, platform->kvpairs);
        } else if (!strcmp(tag_name, "sound_model_config")) {
            platform->st_xml_tag = TAG_ROOT;
            platform_stdev_set_sm_config_params(platform, platform->kvpairs);
        } else if (!strcmp(tag_name, "adm_config")) {
            platform->st_xml_tag = TAG_ROOT;
            platform_stdev_set_adm_app_type_cfg_params(platform, platform->kvpairs);
        }
        return;
    }

    if (!strcmp(tag_name, "vendor_uuid") && platform->vendor_uuid_info) {
        if ((platform->param_tag_tracker & PARAM_ID_BITS) == PARAM_ID_BITS) {
                list_add_tail(&platform->stdev->vendor_uuid_list,
                              &platform->vendor_uuid_info->list_node);
        } else {
            if(platform->vendor_uuid_info->smlib_handle)
                dlclose(platform->vendor_uuid_info->smlib_handle);
            free(platform->vendor_uuid_info);
            ALOGE("%s: param_type missing for vendor_uuid tag. Bits 0x%x",
                  __func__, platform->param_tag_tracker);
        }
        platform->param_tag_tracker = 0;
        platform->vendor_uuid_info = NULL;
    }
}

static int platform_parse_info(struct platform_data *platform, const char *filename)
{
    XML_Parser      parser;
    FILE            *file;
    int             ret = 0;
    int             bytes_read;
    void            *buf;

    file = fopen(filename, "r");
    if (!file) {
        ALOGD("%s: Failed to open %s, using defaults", __func__, filename);
        ret = -ENODEV;
        goto done;
    }

    platform->st_xml_tag = TAG_ROOT;
    platform->kvpairs = str_parms_create();

    parser = XML_ParserCreate(NULL);
    if (!parser) {
        ALOGE("%s: Failed to create XML parser!", __func__);
        ret = -ENODEV;
        goto err_close_file;
    }

    XML_SetUserData(parser, platform);

    XML_SetElementHandler(parser, start_tag, end_tag);

    while (1) {
        buf = XML_GetBuffer(parser, BUF_SIZE);
        if (buf == NULL) {
            ALOGE("%s: XML_GetBuffer failed", __func__);
            ret = -ENOMEM;
            goto err_free_parser;
        }

        bytes_read = fread(buf, 1, BUF_SIZE, file);
        if (bytes_read < 0) {
            ALOGE("%s: fread failed, bytes read = %d", __func__, bytes_read);
             ret = bytes_read;
            goto err_free_parser;
        }

        if (XML_ParseBuffer(parser, bytes_read,
                            bytes_read == 0) == XML_STATUS_ERROR) {
            ALOGE("%s: XML_ParseBuffer failed, for %s",
                __func__, filename);
            ret = -EINVAL;
            goto err_free_parser;
        }

        if (bytes_read == 0)
            break;
    }

err_free_parser:
    XML_ParserFree(parser);
err_close_file:
    fclose(file);
done:
    return ret;
}

static void query_stdev_platform(sound_trigger_device_t *stdev,
                                 const char *snd_card_name,
                                 char *mixer_path_xml)
{
    if (strstr(snd_card_name, "msm8939-tapan")) {
        strlcpy(mixer_path_xml, MIXER_PATH_XML_WCD9306,
                        sizeof(MIXER_PATH_XML_WCD9306));
    } else if (strstr(snd_card_name, "msm8x09-tasha9326")) {
        strlcpy(mixer_path_xml, MIXER_PATH_XML_WCD9326,
                        sizeof(MIXER_PATH_XML_WCD9326));
    } else {
        strlcpy(mixer_path_xml, MIXER_PATH_XML,
                         sizeof(MIXER_PATH_XML));
    }

    if ((strstr(snd_card_name, "msm8939") ||
        strstr(snd_card_name, "msm8909") ||
        strstr(snd_card_name, "msm8x16")) &&
        !strstr(snd_card_name, "msm8939-tomtom")) {
        stdev->sw_mad = true;
    }
}

void *platform_stdev_init(sound_trigger_device_t *stdev)
{
    int ret = 0, retry_num = 0;
    struct platform_data *my_data = NULL;
    const char *snd_card_name = NULL;
    acdb_loader_init_v1_t acdb_init;
    char mixer_path_xml[100];
    struct listnode *v_node;
    struct st_vendor_info* v_info;

    ALOGI("%s: Enter", __func__);
    my_data = calloc(1, sizeof(struct platform_data));

    if (!my_data || !stdev) {
        ALOGE("%s: ERROR. NULL param", __func__);
        if(my_data)
            free(my_data);
        return NULL;
    }
    my_data->stdev = stdev;

    list_init(&stdev->adm_cfg_list);

    stdev->mixer = mixer_open(SOUND_CARD);
    while (!stdev->mixer && retry_num < MIXER_OPEN_MAX_NUM_RETRY) {
        usleep(RETRY_US);
        stdev->mixer = mixer_open(SOUND_CARD);
        retry_num++;
    }

    if (!stdev->mixer) {
        ALOGE("%s: ERROR. Unable to open the mixer, aborting", __func__);
        goto cleanup;
    }

    snd_card_name = mixer_get_name(stdev->mixer);

    query_stdev_platform(stdev, snd_card_name, mixer_path_xml);
    stdev->audio_route = audio_route_init(SOUND_CARD, mixer_path_xml);
    if (!stdev->audio_route) {
        ALOGE("%s: ERROR. Failed to init audio route controls, aborting.",
                __func__);
        goto cleanup;
    }

    my_data->acdb_handle = dlopen(LIB_ACDB_LOADER, RTLD_NOW);
    if (my_data->acdb_handle == NULL) {
        ALOGE("%s: ERROR. dlopen failed for %s", __func__, LIB_ACDB_LOADER);
        goto cleanup;
    }

    acdb_init = (acdb_loader_init_v1_t)dlsym(my_data->acdb_handle,
                                              "acdb_loader_init_v1");
    if (acdb_init == NULL) {
        ALOGE("%s: dlsym error %s for acdb_loader_init_v1", __func__, dlerror());
        goto cleanup;
    }

    my_data->acdb_deinit = (acdb_loader_deallocate_t)dlsym(my_data->acdb_handle,
                                           "acdb_loader_deallocate_ACDB");
    if (my_data->acdb_deinit == NULL) {
        ALOGE("%s: dlsym error %s for acdb_loader_deallocate_ACDB", __func__, dlerror());
        goto cleanup;
    }

    my_data->acdb_send_device_cal = (acdb_loader_send_listen_device_cal_t)
              dlsym(my_data->acdb_handle, "acdb_loader_send_listen_device_cal");

    if (my_data->acdb_send_device_cal == NULL) {
        ALOGE("%s: ERROR. dlsym Error:%s acdb_loader_send_listen_device_cal", __func__,
               dlerror());
        goto cleanup;
    }

    my_data->acdb_send_lsm_cal = (acdb_loader_send_listen_lsm_cal_t)
              dlsym(my_data->acdb_handle, "acdb_loader_send_listen_lsm_cal");

    if (my_data->acdb_send_lsm_cal == NULL) {
        ALOGE("%s: ERROR. dlsym Error:%s acdb_loader_send_listen_lsm_cal", __func__,
               dlerror());
        goto cleanup;
    }

    ALOGI("%s: acdb_init: %s", __func__, snd_card_name);
    ret = acdb_init(snd_card_name);
    if (ret) {
        ALOGE("%s: ERROR. acdb_loader_init_v1 failed status %d", __func__, ret);
        goto cleanup;
    }

    list_init(&stdev->vendor_uuid_list);
    platform_stdev_set_default_config(my_data);

    platform_parse_info(my_data, PLATFORM_PATH_XML);

    /* Using non topology solution still need QC smlib wrapper APIs */
    stdev->smlib_handle = dlopen(LIB_SM_WRAPPER, RTLD_NOW);
    if (!stdev->smlib_handle) {
        ALOGE("%s: ERROR. dlopen failed for %s", __func__, LIB_SM_WRAPPER);
        goto cleanup;
    }
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

    /* Check if ISV vendor_uuid is present and force SVA execution to ADSP */
    list_for_each(v_node, &stdev->vendor_uuid_list) {
        v_info = node_to_item(v_node, struct st_vendor_info, list_node);
        if(!memcmp(&v_info->uuid, &qc_uuid, sizeof(sound_trigger_uuid_t))) {
            v_info->is_qc_uuid = true;
        } else {
            ALOGV("%s: ISV uuid present, force execution on APE",
              __func__);
            stdev->run_on_ape = true;
            stdev->transition_enabled = false;
        }
    }
    init_codec_backend_cfg_mixer_ctl(my_data);
    platform_stdev_send_adm_app_type_cfg(my_data);
    return my_data;

cleanup:
    if (my_data->acdb_handle)
        dlclose(my_data->acdb_handle);

    if(stdev->smlib_handle)
        dlclose(stdev->smlib_handle);

    if (stdev->audio_route)
        audio_route_free(stdev->audio_route);

    if (stdev->mixer)
        mixer_close(stdev->mixer);

    if (my_data->kvpairs)
        str_parms_destroy(my_data->kvpairs);

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
        my_data->acdb_deinit();
        dlclose(my_data->acdb_handle);
        if(my_data->stdev->smlib_handle)
            dlclose(my_data->stdev->smlib_handle);
        audio_route_free(my_data->stdev->audio_route);
        mixer_close(my_data->stdev->mixer);
        if (my_data->kvpairs)
            str_parms_destroy(my_data->kvpairs);

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

int platform_stdev_get_device_name
(
   void *platform,
   st_exec_mode_t exec_mode,
   st_device_t st_device,
   char *device_name
)
{
    struct platform_data *my_data = (struct platform_data *)platform;

    if ((st_device >= ST_DEVICE_MIN && st_device < ST_DEVICE_MAX) &&
        (exec_mode > ST_DEVICE_EXEC_MODE_NONE && exec_mode < ST_DEVICE_EXEC_MODE_MAX)) {
        strlcpy(device_name, st_device_table[exec_mode][st_device], DEVICE_NAME_MAX_SIZE);
    } else {
        strlcpy(device_name, "", DEVICE_NAME_MAX_SIZE);
        return -EINVAL;
    }
    return 0;
}

int platform_stdev_get_device
(
   void *platform,
   struct st_vendor_info* v_info,
   audio_devices_t device
)
{
    struct platform_data *my_data = (struct platform_data *)platform;
    sound_trigger_device_t *stdev = my_data->stdev;
    int channel_count;

    if (v_info && v_info->profile_type == ST_PROFILE_TYPE_FLUENCE)
        channel_count = get_channel_cnt_from_fluence_type(v_info->fluence_type);
    else
        channel_count = my_data->codec_backend_cfg.channel_count;

    st_device_t st_device = ST_DEVICE_NONE;

    if (device == AUDIO_DEVICE_NONE)
        ALOGV("%s: device none",__func__);

    if (!stdev->run_on_ape) {
            st_device = ST_DEVICE_HANDSET_MIC;
    } else {
        if (channel_count == SOUND_TRIGGER_CHANNEL_MODE_QUAD) {
             st_device = ST_DEVICE_HANDSET_QMIC;
        } else if (channel_count == SOUND_TRIGGER_CHANNEL_MODE_STEREO) {
             st_device = ST_DEVICE_HANDSET_DMIC;
        } else if (channel_count == SOUND_TRIGGER_CHANNEL_MODE_MONO) {
            if (v_info &&
                (v_info->profile_type != ST_PROFILE_TYPE_NONE))
                st_device = ST_DEVICE_HANDSET_MIC_PP;
            else
                st_device = ST_DEVICE_HANDSET_MIC;
        } else {
             ALOGE("%s: Invalid channel count %d", __func__, channel_count);
        }
    }
    ALOGV("%s: st_device %d", __func__, st_device);

    return st_device;
}

static int platform_stdev_get_device_sample_rate
(
    struct platform_data *my_data,
    sound_trigger_session_t *p_ses
)
{
    sound_trigger_device_t *stdev = my_data->stdev;
    struct listnode *p_node, *temp_node;
    struct adm_app_type_cfg_info *cfg_info;;
    /* default device sampling rate in acdb */
    int sample_rate = SOUND_TRIGGER_SAMPLING_RATE_48000;

    list_for_each_safe(p_node, temp_node, &stdev->adm_cfg_list) {
        cfg_info = node_to_item(p_node, struct adm_app_type_cfg_info, list_node);
        if (cfg_info->profile_type == p_ses->profile_type) {
            sample_rate = cfg_info->sample_rate;
            break;
        }
    }
    ALOGD("%s: sample rate %d", __func__, sample_rate);
    return sample_rate;
}

int platform_stdev_send_calibration
(
   void *platform,
   audio_devices_t device,
   sound_trigger_session_t *p_ses,
   int app_id,
   bool use_topology,
   st_cal_type type
)
{
    struct platform_data *my_data = (struct platform_data *)platform;
    sound_trigger_device_t *stdev;
    int acdb_id;
    st_device_t st_device;
    int status = 0;
    int hw_type, sample_rate;

    stdev = my_data->stdev;
    st_device = platform_stdev_get_device(platform, p_ses->vendor_uuid_info, device);
    if (st_device == ST_DEVICE_NONE) {
        ALOGE("%s: Could not find valid device",__func__);
        return -EINVAL;
    }

    acdb_id = acdb_device_table[p_ses->exec_mode][st_device];
    if (acdb_id < 0) {
        ALOGE("%s: Could not find acdb id for device(%d)",
              __func__, st_device);
        return -EINVAL;
    }

    hw_type = (p_ses->exec_mode == ST_DEVICE_EXEC_MODE_CPE) ? 0 : 1;

    switch (type) {
    case ST_SESSION_CAL:
        if (my_data->acdb_send_lsm_cal) {
            ALOGD("%s: sending lsm calibration for device(%d) acdb_id(%d)",
                                             __func__, st_device, acdb_id);
            /* ACDB modes: topology=1, non-topology=0 */
            status = my_data->acdb_send_lsm_cal(acdb_id, app_id,
                                                (use_topology == true),
                                                hw_type);
        }
        break;

    case ST_DEVICE_CAL:

        sample_rate = platform_stdev_get_device_sample_rate(my_data, p_ses);
        if (my_data->acdb_send_device_cal) {
            ALOGD("%s: sending afe calibration for device(%d) acdb_id(%d)",
                                             __func__, st_device, acdb_id);
            my_data->acdb_send_device_cal(acdb_id, hw_type, app_id, sample_rate);
        }
        break;

    default:
        ALOGE("%s: invalid cal type %d",__func__,type);
        return -EINVAL;
    }

    return status;
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

int platform_stdev_connect_mad
(
   void *platform,
   bool is_ape
)
{
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

int platform_stdev_send_lec_ref_cfg
(
   void *platform,
   bool enable
)
{
    struct platform_data *my_data = (struct platform_data *)platform;
    sound_trigger_device_t *stdev = my_data->stdev;
    int status = 0;
    struct mixer_ctl *ctl = NULL;
    const char *mixer_ctl_name = "LEC Ref Config";
    int lec_ref_cfg[4];

    ctl = mixer_get_ctl_by_name(stdev->mixer, mixer_ctl_name);
    if (!ctl) {
        ALOGE("%s: ERROR. Could not get ctl for mixer cmd - %s",
        __func__, mixer_ctl_name);
        return -EINVAL;
    }
    lec_ref_cfg[0] = enable ? 2 : 0;
    lec_ref_cfg[1] = enable ? 2 : 0;
    lec_ref_cfg[2] = enable ? 16000 : 0;
    lec_ref_cfg[3] = enable ? 16 : 0;

    status = mixer_ctl_set_array(ctl, lec_ref_cfg, ARRAY_SIZE(lec_ref_cfg));
    if (status)
        ALOGE("%s: ERROR. Mixer ctl set failed", __func__);

    return status;
}

int platform_stdev_check_and_set_codec_backend_cfg
(
   void *platform,
   struct st_vendor_info *v_info,
   bool *backend_cfg_change
)
{
    struct platform_data *my_data = (struct platform_data *)platform;
    sound_trigger_device_t *stdev = my_data->stdev;
    struct  mixer_ctl *ctl;
    int channel_count;

    if (!backend_cfg_change) {
        ALOGE("%s: NULL backend", __func__);
        return -EINVAL;
    }

    *backend_cfg_change = false;

    if (!v_info)
        return 0;

    if (v_info->format != my_data->codec_backend_cfg.format) {
        if (my_data->codec_backend_cfg.format != PCM_FORMAT_S24_LE &&
            my_data->codec_backend_cfg.format_mixer_ctl) {
            ctl = mixer_get_ctl_by_name(stdev->mixer,
                        my_data->codec_backend_cfg.format_mixer_ctl);
            if (!ctl) {
                ALOGE("%s: Could not get ctl for mixer command - %s",
                      __func__, my_data->codec_backend_cfg.format_mixer_ctl);
                return -EINVAL;
            }

            if (v_info->format == PCM_FORMAT_S24_LE) {
                mixer_ctl_set_enum_by_string(ctl, "S24_LE");
            } else if (v_info->format == PCM_FORMAT_S16_LE) {
                mixer_ctl_set_enum_by_string(ctl, "S16_LE");
            } else {
                ALOGE("%s: Invalid format %d", __func__, v_info->format);
                return -EINVAL;
            }
            *backend_cfg_change = true;
            my_data->codec_backend_cfg.format = v_info->format;
        }
    }

    /*
     * Channel count for backend is determined from configuration of
     * lsm session except in case of fluence profile.
     * In case of fluence, backend configuration is obtained from
     * fluence type set.
     */
    if (v_info->profile_type == ST_PROFILE_TYPE_FLUENCE)
        channel_count = get_channel_cnt_from_fluence_type(v_info->fluence_type);
    else
        channel_count = v_info->channel_count;

    if (channel_count != my_data->codec_backend_cfg.channel_count) {

        if (my_data->codec_backend_cfg.channel_count != SOUND_TRIGGER_CHANNEL_MODE_QUAD &&
            my_data->codec_backend_cfg.channelcount_mixer_ctl) {
            ctl = mixer_get_ctl_by_name(stdev->mixer,
                        my_data->codec_backend_cfg.channelcount_mixer_ctl);
            if (!ctl) {
                ALOGE("%s: Could not get ctl for mixer command - %s",
                      __func__, my_data->codec_backend_cfg.channelcount_mixer_ctl);
                return -EINVAL;
            }
            if (channel_count == SOUND_TRIGGER_CHANNEL_MODE_QUAD) {
                mixer_ctl_set_enum_by_string(ctl, "Four");
            } else if (channel_count == SOUND_TRIGGER_CHANNEL_MODE_STEREO) {
                mixer_ctl_set_enum_by_string(ctl, "Two");
            } else if (channel_count == SOUND_TRIGGER_CHANNEL_MODE_MONO) {
                mixer_ctl_set_enum_by_string(ctl, "One");
            } else {
                ALOGE("%s: Invalid channel count %d", __func__, channel_count);
                return -EINVAL;
            }
            *backend_cfg_change = true;
            my_data->codec_backend_cfg.channel_count = channel_count;
        }
    }
    return 0;
}

int platform_stdev_send_stream_app_type_cfg
(
   void *platform,
   sound_trigger_session_t *p_ses,
   audio_devices_t device
)
{
    struct platform_data *my_data = (struct platform_data *)platform;
    sound_trigger_device_t *stdev = my_data->stdev;
    char mixer_ctl_name[ST_MAX_LENGTH_MIXER_CONTROL];
    int app_type_cfg[ST_MAX_LENGTH_MIXER_CONTROL], len = 0;
    struct mixer_ctl *ctl;
    int pcm_device_id = 0, status = 0, acdb_id;
    struct listnode *p_node, *temp_node;
    struct adm_app_type_cfg_info *cfg_info;
    bool found_profile = false;

    if (p_ses->profile_type == ST_PROFILE_TYPE_NONE) {
        ALOGV("%s: Profile set to None, ignore sending app type cfg",__func__);
        goto exit;

    }

    st_device_t st_device = platform_stdev_get_device(platform, p_ses->vendor_uuid_info, device);
    if (st_device == ST_DEVICE_NONE) {
        ALOGE("%s: Could not find valid device",__func__);
        status = -EINVAL;
        goto exit;
    }

    acdb_id = acdb_device_table[p_ses->exec_mode][st_device];
    if (acdb_id < 0) {
        ALOGE("%s: Could not find acdb id for device(%d)",
              __func__, st_device);
        status = -EINVAL;
        goto exit;
    }

    snprintf(mixer_ctl_name, sizeof(mixer_ctl_name),
             "Listen Stream %d App Type Cfg", p_ses->pcm_id);
    ctl = mixer_get_ctl_by_name(stdev->mixer, mixer_ctl_name);
    if (!ctl) {
        ALOGE("%s: ERROR. Could not get ctl for mixer cmd - %s",
        __func__, mixer_ctl_name);
        status = -EINVAL;
        goto exit;
    }

    list_for_each_safe(p_node, temp_node, &stdev->adm_cfg_list) {
        cfg_info = node_to_item(p_node, struct adm_app_type_cfg_info, list_node);
        if (cfg_info->profile_type == p_ses->profile_type) {
            found_profile = true;
            app_type_cfg[len++] = cfg_info->app_type;
            app_type_cfg[len++] = acdb_id;
            app_type_cfg[len++] = cfg_info->sample_rate;
            break;
        }
    }

    if (found_profile) {
        status = mixer_ctl_set_array(ctl, app_type_cfg, len);
        if (status)
            ALOGE("%s: ERROR. Mixer ctl set failed", __func__);
    }

exit:
    return status;
}

void platform_stdev_check_and_append_usecase
(
   void *platform __unused,
   char *use_case,
   st_profile_type_t profile_type
)
{
    if (profile_type != ST_PROFILE_TYPE_NONE)
        strlcat(use_case, " preproc", USECASE_STRING_SIZE);

    ALOGV("%s: return usecase %s", __func__, use_case);
}
