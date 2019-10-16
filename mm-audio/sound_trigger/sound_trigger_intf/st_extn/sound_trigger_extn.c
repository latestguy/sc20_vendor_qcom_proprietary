/* Copyright (c) 2013-2014, The Linux Foundation. All rights reserved.
 *
 * Copyright (c) 2015 Qualcomm Technologies, Inc.
 * All Rights Reserved.
 * Confidential and Proprietary - Qualcomm Technologies, Inc.
 *
 */

#define LOG_TAG "sound_trigger_extn"

#include <errno.h>
#include <stdbool.h>
#include <stdlib.h>
#include <dlfcn.h>
#include <string.h>
#include <cutils/list.h>
#include "log.h"
#include "sound_trigger_prop_intf.h"

#define XSTR(x) STR(x)
#define STR(x) #x

struct sound_trigger_info  {
    struct sound_trigger_session_info st_ses;
    bool lab_stopped;
    struct listnode list;
};

struct sound_trigger_extn_device {
    void *lib_handle;
    sound_trigger_hw_call_back_t st_callback;
    struct listnode st_ses_list;
    pthread_mutex_t lock;
};

static struct sound_trigger_extn_device *st_dev;

static struct sound_trigger_info *
get_sound_trigger_info(int capture_handle)
{
    struct sound_trigger_info  *st_ses_info = NULL;
    struct listnode *node;
    ALOGV("%s: list empty %d capture_handle %d", __func__,
           list_empty(&st_dev->st_ses_list), capture_handle);
    list_for_each(node, &st_dev->st_ses_list) {
        st_ses_info = node_to_item(node, struct sound_trigger_info , list);
        if (st_ses_info->st_ses.capture_handle == capture_handle)
            return st_ses_info;
    }
    return NULL;
}

int sound_trigger_extn_call_back(sound_trigger_event_type_t event,
                       sound_trigger_event_info_t* config)
{
    int status = 0;
    struct sound_trigger_info  *st_ses_info;

    if (!st_dev)
       return -EINVAL;

    pthread_mutex_lock(&st_dev->lock);
    switch (event) {
    case ST_EVENT_SESSION_REGISTER:
        if (!config) {
            ALOGE("%s: NULL config", __func__);
            status = -EINVAL;
            break;
        }
        st_ses_info= calloc(1, sizeof(struct sound_trigger_info ));
        if (!st_ses_info) {
            ALOGE("%s: st_ses_info alloc failed", __func__);
            status = -ENOMEM;
            break;
        }
        memcpy(&st_ses_info->st_ses, &config->st_ses, sizeof (config->st_ses));
        ALOGV("%s: add capture_handle %d pcm %p", __func__,
              st_ses_info->st_ses.capture_handle, st_ses_info->st_ses.pcm);
        list_add_tail(&st_dev->st_ses_list, &st_ses_info->list);
        break;

    case ST_EVENT_SESSION_DEREGISTER:
        if (!config) {
            ALOGE("%s: NULL config", __func__);
            status = -EINVAL;
            break;
        }
        st_ses_info = get_sound_trigger_info(config->st_ses.capture_handle);
        if (!st_ses_info) {
            ALOGE("%s: pcm %p not in the list!", __func__, config->st_ses.pcm);
            status = -EINVAL;
            break;
        }
        ALOGV("%s: remove capture_handle %d pcm %p", __func__,
              st_ses_info->st_ses.capture_handle, st_ses_info->st_ses.pcm);
        list_remove(&st_ses_info->list);
        free(st_ses_info);
        break;
    default:
        ALOGW("%s: Unknown event %d", __func__, event);
        break;
    }
    pthread_mutex_unlock(&st_dev->lock);
    return status;
}

int sound_trigger_extn_read(int capture_handle, void *buffer,
                       size_t bytes)
{
    int ret = -1;
    struct sound_trigger_info  *st_info = NULL;
    audio_event_info_t event;

    if (!st_dev)
       return ret;

    pthread_mutex_lock(&st_dev->lock);
    st_info = get_sound_trigger_info(capture_handle);
    pthread_mutex_unlock(&st_dev->lock);
    if (st_info) {
        event.u.aud_info.ses_info = &st_info->st_ses;
        event.u.aud_info.buf = buffer;
        event.u.aud_info.num_bytes = bytes;
        ret = st_dev->st_callback(AUDIO_EVENT_READ_SAMPLES, &event);
    }

    if (ret) {
        memset(buffer, 0, bytes);
        ALOGV("%s: read failed status %d - sleep", __func__, ret);
        usleep((bytes * 1000000) / (2 * 16000));
    }
    return ret;
}

void sound_trigger_extn_stop_lab(int capture_handle)
{
    int status = 0;
    struct sound_trigger_info  *st_ses_info = NULL;
    audio_event_info_t event;

    if (!st_dev)
       return;

    pthread_mutex_lock(&st_dev->lock);
    st_ses_info = get_sound_trigger_info(capture_handle);
    pthread_mutex_unlock(&st_dev->lock);
    if (st_ses_info) {
        event.u.ses_info = st_ses_info->st_ses;
        ALOGV("%s: AUDIO_EVENT_STOP_LAB pcm %p", __func__, st_ses_info->st_ses.pcm);
        st_dev->st_callback(AUDIO_EVENT_STOP_LAB, &event);
    }
}

int sound_trigger_extn_init()
{
    int status = 0;
    char sound_trigger_lib[100];
    void *lib_handle;

    ALOGI("%s: Enter", __func__);

    st_dev = (struct sound_trigger_extn_device*)
                        calloc(1, sizeof(struct sound_trigger_extn_device));
    if (!st_dev) {
        ALOGE("%s: ERROR. sound trigger alloc failed", __func__);
        return -ENOMEM;
    }

    snprintf(sound_trigger_lib, sizeof(sound_trigger_lib),
             "libsoundtrigger.so.1");

    st_dev->lib_handle = dlopen(sound_trigger_lib, RTLD_NOW);

    if (st_dev->lib_handle == NULL) {
        ALOGE("%s: DLOPEN failed for %s. error = %s", __func__, sound_trigger_lib,
                dlerror());
        status = -EINVAL;
        goto cleanup;
    }
    ALOGI("%s: DLOPEN successful for %s", __func__, sound_trigger_lib);

    st_dev->st_callback = (sound_trigger_hw_call_back_t)
              dlsym(st_dev->lib_handle, "sound_trigger_hw_call_back");

    if (st_dev->st_callback == NULL) {
       ALOGE("%s: ERROR. dlsym Error:%s sound_trigger_hw_call_back", __func__,
               dlerror());
       goto cleanup;
    }

    list_init(&st_dev->st_ses_list);

    return 0;

cleanup:
    if (st_dev->lib_handle)
        dlclose(st_dev->lib_handle);
    free(st_dev);
    st_dev = NULL;
    return status;

}

void sound_trigger_extn_deinit()
{
    ALOGI("%s: Enter", __func__);
    if (st_dev) {
        if (st_dev->lib_handle)
            dlclose(st_dev->lib_handle);
        free(st_dev);
        st_dev = NULL;
    }
}
