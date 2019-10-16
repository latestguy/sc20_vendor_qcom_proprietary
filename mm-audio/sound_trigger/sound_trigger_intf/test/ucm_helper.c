/*
 * Copyright (c) 2015 Qualcomm Technologies, Inc.
 * All Rights Reserved.
 * Confidential and Proprietary - Qualcomm Technologies, Inc.
 *
 */

#include <stdio.h>
#include <fcntl.h>
#include <string.h>
#include <errno.h>
#include <stdlib.h>
#include <stdint.h>
#include <stdbool.h>

#include <alsa/use-case.h>
#include "log.h"

#define ST_DEVICE_EXEC_MODE_MAX 2
#define ST_DEVICE_MAX 2
#define SND_USE_CASE_VERB_SVA    "SVA"
#define SND_USE_CASE_VERB_SVA_ADSP    "SVA_ADSP"
#define SND_USE_CASE_VERB_SVA_ADSP_LEC_MONO    "SVA_ADSP_LEC_MONO"
#define SND_USE_CASE_VERB_SVA_ADSP_LEC_STEREO    "SVA_ADSP_LEC_STEREO"
#define SND_USE_CASE_DEV_CPE_HANDSET        "HandsetMic"
#define SND_USE_CASE_DEV_ADSP_HANDSET       "HandsetMic"

static snd_use_case_mgr_t *mUcMgr;

static const char *stdev_table[ST_DEVICE_EXEC_MODE_MAX][ST_DEVICE_MAX]= {
    {
        SND_USE_CASE_DEV_NONE,
        SND_USE_CASE_DEV_ADSP_HANDSET
    },
    {
        SND_USE_CASE_DEV_NONE,
        SND_USE_CASE_DEV_CPE_HANDSET
    },
};

static const char *stdev_usecase[ST_DEVICE_EXEC_MODE_MAX] = {
    SND_USE_CASE_VERB_SVA_ADSP,
    SND_USE_CASE_VERB_SVA
};

static const char *stdev_usecase_lec[ST_DEVICE_EXEC_MODE_MAX][2] = {
    {
        SND_USE_CASE_VERB_SVA_ADSP_LEC_MONO,
        SND_USE_CASE_VERB_SVA_ADSP_LEC_STEREO
    },
    {
        SND_USE_CASE_VERB_SVA,
        SND_USE_CASE_VERB_SVA
    },
};

int ucm_open(char *identifier)
{
    int err = 0;

    err = snd_use_case_mgr_open(&mUcMgr, identifier);
    if (err < 0) {
        fprintf(stderr, "failed to open sound card %s: %d\n", identifier, err);
    }

    return err;
}

void ucm_close(void )
{
    int err = 0;

    if (mUcMgr) {
        err = snd_use_case_mgr_close(mUcMgr);
        mUcMgr = NULL;
    }
}

void stdev_set_device(
    int devId,
    int exec_mode,
    bool enable)
{
    if (enable) {
        snd_use_case_set(mUcMgr, "_enadev", stdev_table[exec_mode][devId]);
    } else {
        snd_use_case_set(mUcMgr, "_disdev", stdev_table[exec_mode][devId]);
    }
}

void stdev_set_usecase(int exec_mode, bool enable,
                       bool lec_mode, int num_channels)
{
    if ((num_channels <= 0) || (num_channels > 2)) {
        ALOGE("%s: Invalid num channels = %d",  __func__, num_channels);
        return;
    }

    if (enable) {
        if (lec_mode) {
            snd_use_case_set(mUcMgr, "_verb",
                             stdev_usecase_lec[exec_mode][num_channels - 1]);
        } else
            snd_use_case_set(mUcMgr, "_verb", stdev_usecase[exec_mode]);
    } else {
        snd_use_case_set(mUcMgr, "_verb", SND_USE_CASE_VERB_INACTIVE);
    }
}
