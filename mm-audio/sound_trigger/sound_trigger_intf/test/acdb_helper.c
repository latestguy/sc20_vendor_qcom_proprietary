/*
 * Copyright (c) 2015 Qualcomm Technologies, Inc.
 * All Rights Reserved.
 * Confidential and Proprietary - Qualcomm Technologies, Inc.
 *
 */

#include <dirent.h>
#include <fcntl.h>
#include <string.h>
#include <stdio.h>
#include <errno.h>
#include <stdlib.h>
#include <stdint.h>
#include <sys/ioctl.h>

#include "acdb-loader.h"
#include "sound_trigger_platform.h"
#include <sound/msmcal-hwdep.h>
#include "log.h"

#define DEFAULT_TX_ACDB_ID 4

struct hwdep_cal_param_data {
    int use_case;
    int acdb_id;
    int get_size;
    int buff_size;
    int data_size;
    void *buff;
};

/* ACDB IDs for each device for both CDSP and ADSP */
static int acdb_device_table[ST_DEVICE_EXEC_MODE_MAX][ST_DEVICE_MAX] = {
    {
      [ST_DEVICE_NONE] = -1,
      [ST_DEVICE_HANDSET_MIC] = DEVICE_HANDSET_APE_ACDB_ID,
      [ST_DEVICE_HEADSET_MIC] = DEVICE_HEADSET_APE_ACDB_ID,
    },
    {
      [ST_DEVICE_NONE] = -1,
      [ST_DEVICE_HANDSET_MIC] = DEVICE_HANDSET_CPE_ACDB_ID,
      [ST_DEVICE_HEADSET_MIC] = DEVICE_HEADSET_CPE_ACDB_ID,
    }
};

int hwdep_fd, prev_acdb_id;

void acdb_helper_deinit(void )
{
    acdb_loader_deallocate_ACDB();
}

int acdb_helper_init(char *snd_card_name)
{
    int ret = 0;
    char dev_name[256];
    if ((ret = acdb_loader_init_v2(snd_card_name, NULL, 0)) < 0) {
        fprintf(stderr, "Failed to initialize ACDB\n");
        return ret;
    }
    snprintf(dev_name, sizeof(dev_name), "/dev/snd/hwC%uD%u",
             SOUND_CARD, WCD9XXX_CODEC_HWDEP_NODE);
    hwdep_fd = open(dev_name, O_WRONLY);
    if (hwdep_fd < 0) {
        ALOGE("%s: cannot open device '%s'", __func__, dev_name);
        acdb_helper_deinit();
    }

    prev_acdb_id = -1;


    return ret;
}

void acdb_helper_set_acdb_id
(
    st_exec_mode_t mode,
    st_device_t st_device,
    unsigned int speaker_count
)
{
    unsigned int acdb_id = DEVICE_HANDSET_APE_LEC_REF_END_MONO_ACDB_ID;

    if ((st_device < ST_DEVICE_MIN) || (st_device >= ST_DEVICE_MAX)) {
        ALOGE("%s: Invalid st_device = %d",  __func__, st_device);
        return;
    }

    if ((!speaker_count) || (speaker_count > 2)) {
        ALOGE("%s: Invalid speaker count = %d",  __func__, speaker_count);
        return;
    }

    if ((mode <= ST_DEVICE_EXEC_MODE_NONE) ||
            (mode >= ST_DEVICE_EXEC_MODE_MAX)) {
        ALOGE("%s: Invalid mode = %d",  __func__, mode);
        return;
    }

    if (speaker_count == 2)
        acdb_id = DEVICE_HANDSET_APE_LEC_REF_END_STEREO_ACDB_ID;

    acdb_device_table[mode][st_device] = acdb_id;
}

static int get_acdb_id
(
    st_exec_mode_t mode,
    st_device_t st_device
)
{
    unsigned int acdb_id;

    if ((st_device < ST_DEVICE_MIN) || (st_device >= ST_DEVICE_MAX)) {
        ALOGE("%s: Invalid st_device = %d",  __func__, st_device);
        return;
    }

    if ((mode <= ST_DEVICE_EXEC_MODE_NONE) ||
            (mode >= ST_DEVICE_EXEC_MODE_MAX)) {
        ALOGE("%s: Invalid mode = %d",  __func__, mode);
        return;
    }

    acdb_id = acdb_device_table[mode][st_device];
    return acdb_id;

}


static int send_hwmad_cal
(
    int acdb_id,
    bool sw_mad
)
{
    int ret = 0;
    struct wcdcal_ioctl_buffer codec_buffer;
    struct hwdep_cal_param_data calib;

    if ((prev_acdb_id == acdb_id) || sw_mad) {
        ALOGD("%s: previous acdb_id %d new acdb_id %d, sw_mad %d return",
              __func__, prev_acdb_id, acdb_id, sw_mad);
        return 0;
    }

    calib.acdb_id = acdb_id;
    calib.get_size = 1;
    ret = acdb_loader_get_calibration("mad_cal", sizeof(struct hwdep_cal_param_data),
                                                            &calib);
    if (ret < 0) {
        ALOGE("%s: get_calibration to get size failed", __func__);
        return ret;
    }

    calib.get_size = 0;
    calib.buff = malloc(calib.buff_size);
    if (!calib.buff) {
        ALOGE("%s: malloc calib of buff size %d failed",
                  __func__, calib.buff_size);
        return -ENOMEM;
    }

    ret = acdb_loader_get_calibration("mad_cal", sizeof(struct hwdep_cal_param_data),
                                                            &calib);
    if (ret < 0) {
        ALOGE("%s: get_calibration to get size failed", __func__);
        free(calib.buff);
        return ret;
    }

    codec_buffer.buffer = calib.buff;
    codec_buffer.size = calib.data_size;
    codec_buffer.cal_type = WCD9XXX_MAD_CAL;
    ret = ioctl(hwdep_fd, SNDRV_CTL_IOCTL_HWDEP_CAL_TYPE, &codec_buffer);
    if (ret < 0) {
        ALOGE("%s: failed to call ioctl err=%d",__func__, errno);
    } else {
        prev_acdb_id = acdb_id;
        ALOGD("%s hwmad cal sent for acdb_id (%d)", __func__, acdb_id);
    }

    free(calib.buff);
    return ret;
}

int acdb_helper_send_calibration
(
    int st_device,
    int app_id,
    bool use_topology,
    st_exec_mode_t exec_mode,
    bool sw_mad,
    bool lec_enabled
)
{
    int status = 0;
    int hw_type, acdb_id, send_adm_cal;

    acdb_id = get_acdb_id(exec_mode, st_device);

    if (acdb_id < 0) {
        ALOGE("%s: invalid acdb id %d",
              __func__, acdb_id);
        return -EINVAL;
    }

    if (send_hwmad_cal(acdb_id, sw_mad) < 0) {
        ALOGE("%s: error sending hwmad cal for acdb id (%d)",
                                      __func__, acdb_id);
        return -EINVAL;
    }

    send_adm_cal = lec_enabled ? 1 : 0;
    ALOGD("%s: sending listen calibration for acdb_id(%d)",
                                             __func__, acdb_id);
    acdb_loader_send_listen_cal(acdb_id, app_id, (use_topology == true),
                                send_adm_cal);
    return 0;
}

void acdb_helper_send_default_calibration
(
    int app_id,
    bool use_topology,
    bool lec_enabled
)
{
    int send_adm_cal = lec_enabled ? 1 : 0;

    /* send default acdb id TX cal to reset cal data after close of SVA.
     * This is required as acdb supports only two types of devices - RX and TX.
     * In case recording is started after SVA without sending any acdb cal,
     * it will try to use SVA acdb cal and would lead to failures.
     * We currently need this if only adm cal is sent.
     */
    if (!lec_enabled)
        return;
    acdb_loader_send_listen_cal(DEFAULT_TX_ACDB_ID, app_id,
                                (use_topology == true), send_adm_cal);
}
