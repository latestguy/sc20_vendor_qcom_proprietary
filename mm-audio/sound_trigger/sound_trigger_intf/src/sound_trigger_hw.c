/*
 *
 * This library contains the API to load sound models with
 * DSP and start/stop detection of associated key phrases.
 *
 * Copyright (c) 2013-2015 Qualcomm Technologies, Inc.
 * All Rights Reserved.
 * Confidential and Proprietary - Qualcomm Technologies, Inc.
 *
 */

#define LOG_TAG "sound_trigger_hw"
/* #define ST_DEBUG_DUMP */

#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <dlfcn.h>
#include <fcntl.h>
#include <sys/resource.h>
#include <sys/prctl.h>
#include <sys/ioctl.h>
#include <pthread.h>
#include <string.h>

#include "sound_trigger_hw.h"
#include "sound_trigger_platform.h"

#define XSTR(x) STR(x)
#define STR(x) #x

/* Routing layer functions to route to either ADSP or CPE */
static int route_reg_sm_ape(sound_trigger_session_t *p_ses);
static int route_reg_sm_params_ape(sound_trigger_session_t* p_ses);
static int route_dereg_sm_ape(sound_trigger_session_t* p_ses);
static int route_dereg_sm_params_ape(sound_trigger_session_t* p_ses);
static int route_start_ape(sound_trigger_session_t* p_ses);
static int route_stop_ape(sound_trigger_session_t* p_ses);
static int route_stop_buffering_ape(sound_trigger_session_t* p_ses);
static int route_read_pcm_ape(sound_trigger_session_t *p_ses,
                              unsigned char *buf,
                              unsigned int bytes);
static void route_ftrt_capture_ape(sound_trigger_session_t* p_ses);

static int route_reg_sm_cpe(sound_trigger_session_t *p_ses);
static int route_reg_sm_params_cpe(sound_trigger_session_t* p_ses);
static int route_dereg_sm_cpe(sound_trigger_session_t* p_ses);
static int route_dereg_sm_params_cpe(sound_trigger_session_t* p_ses);
static int route_start_cpe(sound_trigger_session_t* p_ses);
static int route_stop_cpe(sound_trigger_session_t* p_ses);
static int route_stop_buffering_cpe(sound_trigger_session_t* p_ses);
static int route_read_pcm_cpe(sound_trigger_session_t *p_ses,
                              unsigned char *buf,
                              unsigned int bytes);
static void route_ftrt_capture_cpe(sound_trigger_session_t* p_ses);

/* count of sound trigger hal clients */
static unsigned int stdev_ref_cnt = 0;
static pthread_mutex_t stdev_init_lock;
static struct sound_trigger_device *stdev = NULL;

/* default properties which will later be updated based on platform configuration */
static struct sound_trigger_properties hw_properties = {
        "QUALCOMM Technologies, Inc", // implementor
        "Sound Trigger HAL", // description
        1, // version
        { 0x68ab2d40, 0xe860, 0x11e3, 0x95ef, { 0x00, 0x02, 0xa5, 0xd5, 0xc5, 0x1b } }, // uuid
        1, // max_sound_models
        1, // max_key_phrases
        1, // max_users
        RECOGNITION_MODE_VOICE_TRIGGER, // recognition_modes
        true, // capture_transition
        0, // max_capture_ms
        false, // concurrent_capture
        false, //trigger_in_event
        0, // power_consumption_mw
        false, //sw_mad
        false //support_lec
};

static struct pcm_config stdev_ape_pcm_config = {
    .channels = SOUND_TRIGGER_CHANNEL_MODE,
    .rate = SOUND_TRIGGER_SAMPLING_RATE,
    .period_size = SOUND_TRIGGER_APE_PERIOD_SIZE,
    .period_count = SOUND_TRIGGER_APE_PERIOD_COUNT,
    .format = PCM_FORMAT_S16_LE,
};

static struct pcm_config stdev_cpe_pcm_config = {
    .channels = SOUND_TRIGGER_CHANNEL_MODE,
    .rate = SOUND_TRIGGER_SAMPLING_RATE,
    .period_size = SOUND_TRIGGER_CPE_PERIOD_SIZE,
    .period_count = SOUND_TRIGGER_CPE_PERIOD_COUNT,
    .format = PCM_FORMAT_S16_LE,
};

struct sound_trigger_session_fptrs ape_fptrs = {
    .reg_sm = route_reg_sm_ape ,
    .reg_sm_params = route_reg_sm_params_ape,
    .dereg_sm = route_dereg_sm_ape ,
    .dereg_sm_params = route_dereg_sm_params_ape,
    .start = route_start_ape,
    .stop = route_stop_ape,
    .stop_buffering = route_stop_buffering_ape,
    .read_pcm = route_read_pcm_ape,
    .process_ftrt_capture = route_ftrt_capture_ape,
};

struct sound_trigger_session_fptrs cpe_fptrs = {
    .reg_sm = route_reg_sm_cpe ,
    .reg_sm_params = route_reg_sm_params_cpe,
    .dereg_sm = route_dereg_sm_cpe  ,
    .dereg_sm_params = route_dereg_sm_params_cpe,
    .start = route_start_cpe,
    .stop = route_stop_cpe,
    .stop_buffering = route_stop_buffering_cpe,
    .read_pcm = route_read_pcm_cpe,
    .process_ftrt_capture = route_ftrt_capture_cpe,
};

static int stdev_get_hw_type(void)
{
    int dev_id;
    unsigned int i = 0, j = 0;
    char buf[100], comp_string[USECASE_STRING_SIZE];
    FILE *fp;

    fp = fopen(PCM_DEVICE_FILE, "r");

    if (!fp) {
        ALOGE("%s: ERROR. %s file open failed", __func__, PCM_DEVICE_FILE);
        return -ENODEV;
    }
    snprintf(comp_string, USECASE_STRING_SIZE, "Listen %d Audio Service", i+1);

    while(1) {
        fgets(buf, 100, fp);
        if (feof(fp))
            break;
        sscanf(&buf[3],"%d", &dev_id);
        if((strstr(buf, comp_string)) &&
           (i < stdev->max_ape_sessions)) {

            stdev->hw_type |= ST_DEVICE_HW_APE;
            snprintf(stdev->ape_pcm_use_cases[i].use_case,
                     USECASE_STRING_SIZE, "listen-voice-wakeup-%d", i+1);
            stdev->ape_pcm_use_cases[i++].pcm_id = dev_id;
            snprintf(comp_string, USECASE_STRING_SIZE, "Listen %d Audio Service", i+1);
        }
        else if(strstr(buf, "CPE Listen") &&
                (j < stdev->max_cpe_sessions)) {

            stdev->hw_type |= ST_DEVICE_HW_CPE;
            stdev->cpe_pcm_use_cases[j++].pcm_id = dev_id;
        }
    }
    fclose(fp);

    if (!CHECK_BIT(stdev->hw_type, ST_DEVICE_HW_APE|ST_DEVICE_HW_CPE)) {
        ALOGE("%s: ERROR. No Listen device present", __func__);
        return -ENODEV;
    }

    if (stdev->max_ape_sessions > i)
        stdev->max_ape_sessions = i;

    if (stdev->max_cpe_sessions > j)
        stdev->max_cpe_sessions = j;

    return 0;
}

void update_hw_mad_exec_mode(st_exec_mode_t mode)
{
    if (stdev->exec_mode != mode) {
/*        platform_stdev_connect_mad(stdev->platform,
                                   (mode==ST_DEVICE_EXEC_MODE_APE)); */
        stdev->exec_mode = mode;
        ALOGD("%s: new exec_mode 0x%x", __func__, mode);
    }
}

unsigned int get_num_sessions_in_state(enum sound_trigger_states state)
{
    struct listnode *ses_node;
    sound_trigger_session_t *p_ses;
    unsigned int num_sessions = 0;

    if ((list_head(&stdev->sound_model_list) == NULL) ||
                  (list_tail(&stdev->sound_model_list) == NULL)) {
        ALOGE("%s: sound model list is yet to be initialized", __func__);
        return num_sessions;
    }

    list_for_each(ses_node, &stdev->sound_model_list) {
        p_ses = node_to_item(ses_node, sound_trigger_session_t, list_node);
        if (state == SES_CREATED || CHECK_STATE(p_ses->state, state))
            num_sessions++;
    }

    return num_sessions;
}

static void handle_ssr_online(enum ssr_event_status event)
{
    struct listnode *p_ses_node;
    sound_trigger_session_t *p_ses;
    int status = 0;

    ALOGD("%s: Enter", __func__);
    pthread_mutex_lock(&stdev->lock);

    /* Events allowed: SND_CARD ONLINE or CPE ONLINE
     * 1. Clear state if event is applicable to configured platform(ape/cpe).
     * 2. CPE ONLINE for transitions is handled separately by call to func
     *    transit_handle_cpe_ssr_event and complete tear-down is not required.
     */
    if (event == SND_CARD_STATUS_ONLINE && stdev->run_on_ape) {
        CLEAR_STATE(stdev->ssr_state, ST_DEVICE_SSR_APE);
    } else if (event == CPE_STATUS_ONLINE && !stdev->run_on_ape) {
        CLEAR_STATE(stdev->ssr_state, ST_DEVICE_SSR_CPE);
    } else if (event == CPE_STATUS_ONLINE && transit_enabled(stdev) &&
               !CHECK_STATE(stdev->ssr_state, ST_DEVICE_SSR_APE)) {
        CLEAR_STATE(stdev->ssr_state, ST_DEVICE_SSR_CPE);
        transit_handle_cpe_ssr_event(stdev, event);
        pthread_cond_signal(&stdev->cond);
        goto ssr_exit;
    } else {
        ALOGV("%s: nothing to do!!!", __func__);
        goto ssr_exit;
    }

    /* reload and reactive each previously active session */
    list_for_each(p_ses_node, &stdev->sound_model_list) {
        p_ses = node_to_item(p_ses_node, sound_trigger_session_t, list_node);

        pthread_mutex_lock(&p_ses->lock);
        status = p_ses->fptrs->reg_sm(p_ses);

        if (!status && !stdev->audio_concurrency_active &&
            CHECK_STATE(p_ses->state, SES_STARTED)) {
            p_ses->fptrs->reg_sm_params(p_ses);
            p_ses->fptrs->start(p_ses);
            //LISTEN_EVENT_STARTED
        } else if(!status && stdev->audio_concurrency_active &&
            CHECK_STATE(p_ses->state, SES_STARTED) &&
            !stdev->sw_mad) {
            p_ses->fptrs->reg_sm_params(p_ses);
        }
        pthread_mutex_unlock(&p_ses->lock);
    }
    pthread_cond_broadcast(&stdev->cond);

ssr_exit:
    ALOGD("%s: Exit event %d, ssr_state %d", __func__, event, stdev->ssr_state);
    pthread_mutex_unlock(&stdev->lock);
}

static void handle_ssr_offline(enum ssr_event_status event)
{
    struct listnode *p_ses_node;
    sound_trigger_session_t *p_ses;

    ALOGD("%s: Enter", __func__);
    pthread_mutex_lock(&stdev->lock);

    /* Events allowed: SND_CARD OFFLINE or CPE OFFLINE
     * 1. Set state if event is applicable to configured platform(ape/cpe).
     * 2. CPE offline has to follow SND_CARD offline if platform supports both
     *    ape and cpe, i.e transition enabled.
     *    Hence set CPE flag as well on SND_CARD offline event if transition
     *    is enabled.
     * 3. CPE OFFLINE for transitions is handled separately by call to func
     *    transit_handle_cpe_ssr_event and further restoration is not required.
     */
    if (event == SND_CARD_STATUS_OFFLINE && stdev->run_on_ape) {
        SET_STATE(stdev->ssr_state, ST_DEVICE_SSR_APE);
        if (transit_enabled(stdev))
            SET_STATE(stdev->ssr_state, ST_DEVICE_SSR_CPE);
    } else if (event == CPE_STATUS_OFFLINE && !stdev->run_on_ape) {
        SET_STATE(stdev->ssr_state, ST_DEVICE_SSR_CPE);
    } else if (event == CPE_STATUS_OFFLINE && transit_enabled(stdev) &&
               !CHECK_STATE(stdev->ssr_state, ST_DEVICE_SSR_CPE)) {
        SET_STATE(stdev->ssr_state, ST_DEVICE_SSR_CPE);
        transit_handle_cpe_ssr_event(stdev, event);
        goto ssr_exit;
    } else {
        ALOGV("%s: nothing to do!!!", __func__);
        goto ssr_exit;
    }

    //LISTEN_EVENT_STOPPED
    /* teardown each session */
    list_for_each(p_ses_node, &stdev->sound_model_list) {
        p_ses = node_to_item(p_ses_node, sound_trigger_session_t, list_node);

        pthread_mutex_lock(&p_ses->lock);
        if (!stdev->audio_concurrency_active &&
            CHECK_STATE(p_ses->state, SES_STARTED)) {
            p_ses->fptrs->stop_buffering(p_ses);
            p_ses->fptrs->stop(p_ses);
            p_ses->fptrs->dereg_sm_params(p_ses);
        } else if(stdev->audio_concurrency_active &&
            CHECK_STATE(p_ses->state, SES_STARTED) &&
            !stdev->sw_mad) {
            p_ses->fptrs->dereg_sm_params(p_ses);
        }
        p_ses->fptrs->dereg_sm(p_ses);
        pthread_mutex_unlock(&p_ses->lock);
    }

ssr_exit:
    ALOGD("%s: Exit event %d, ssr_state %d", __func__, event, stdev->ssr_state);
    pthread_mutex_unlock(&stdev->lock);
}

static void handle_audio_concurrency(audio_event_type_t event_type)
{
    struct listnode *p_ses_node;
    sound_trigger_session_t *p_ses;
    bool session_allowed;
    unsigned int num_sessions;
    int status = 0;
    bool dev_switch_pending = false;
    ALOGV("%s: Enter, event type = %d", __func__, event_type);

    /*
    UC1:
        1. start_recognition
        2. audio record_active
        3. audio_record_inactive
        4. stop_recognition
    UC1:
        1. start_recognition
        2. audio record_active
        3. stop_recognition
        4. audio_record_inactive
    UC2:
        1. audio_record_active
        2. start_recognition
        3. stop_recogntion
        4. audio_record_inactive
    UC3:
        1. audio_record_active
        2. start_recognition
        3. audio_record_inactive
        4. stop_recogntion
    */
    pthread_mutex_lock(&stdev->lock);
    num_sessions = get_num_sessions_in_state(SES_CREATED);
    session_allowed = platform_stdev_check_and_update_concurrency(stdev->platform,
                                                event_type, num_sessions);

    if(!num_sessions) {
       pthread_mutex_unlock(&stdev->lock);
       return;
    }
    if(session_allowed && stdev->audio_concurrency_active) {
        list_for_each(p_ses_node, &stdev->sound_model_list) {
            p_ses = node_to_item(p_ses_node, sound_trigger_session_t, list_node);

            pthread_mutex_lock(&p_ses->lock);
            if (stdev->sw_mad)
                status = p_ses->fptrs->reg_sm(p_ses);

            if (!status && CHECK_STATE(p_ses->state, SES_STARTED)) {
                if (stdev->sw_mad)
                    status = p_ses->fptrs->reg_sm_params(p_ses);
                if (!status)
                    p_ses->fptrs->start(p_ses);
                    //LISTEN_EVENT_STARTED
            }
            pthread_mutex_unlock(&p_ses->lock);
        }
    }
    else if (!session_allowed && !stdev->audio_concurrency_active) {
        stdev->audio_concurrency_active = true;
        list_for_each(p_ses_node, &stdev->sound_model_list) {
            p_ses = node_to_item(p_ses_node, sound_trigger_session_t, list_node);

            pthread_mutex_lock(&p_ses->lock);
            if(CHECK_STATE(p_ses->state, SES_STARTED)) {
                //LISTEN_EVENT_STOPPED
                p_ses->fptrs->stop_buffering(p_ses);
                p_ses->fptrs->stop(p_ses);
                if (stdev->sw_mad)
                    p_ses->fptrs->dereg_sm_params(p_ses);
            }
            if (stdev->sw_mad)
                p_ses->fptrs->dereg_sm(p_ses);
            pthread_mutex_unlock(&p_ses->lock);
        }
    }

    pthread_mutex_unlock(&stdev->lock);
    ALOGV("%s: Exit", __func__);
}

static int ape_get_pcm_device_id(sound_trigger_session_t *p_ses)
{
    unsigned int i;
    int ret = -1;

    for (i = 0; i < stdev->max_ape_sessions; i++) {
        if(!stdev->ape_pcm_use_cases[i].active) {
            stdev->ape_pcm_use_cases[i].active = true;
            ret = stdev->ape_pcm_use_cases[i].pcm_id;
            p_ses->use_case_idx = i;
            break;
        }
    }
    if (ret < 0)
        ALOGE("%s: ERROR. no free pcm device available", __func__);

    return ret;
}

void reg_hal_event_session(sound_trigger_session_t *p_ses)
{
    /* Pass the pcm information to audio hal for capturing LAB */
    if (p_ses->capture_requested && stdev->st_extn_cb) {
        ALOGD("%s: ST_EVENT_SESSION_REGISTER capture_handle %d pcm %p",
              __func__, p_ses->capture_handle, p_ses->pcm);
        p_ses->event_info.st_ses.capture_handle = p_ses->capture_handle;
        p_ses->event_info.st_ses.pcm = p_ses->pcm;
        p_ses->event_info.st_ses.config = p_ses->config;
        stdev->st_extn_cb(ST_EVENT_SESSION_REGISTER, &p_ses->event_info);
    }
}

void dereg_hal_event_session(sound_trigger_session_t *p_ses)
{
    /* Indicate to audio hal that buffering is stopped to stop reading LAB data */
    if (p_ses->capture_requested && stdev->st_extn_cb) {
        ALOGD("%s: ST_EVENT_SESSION_DEREGISTER capture_handle %d pcm %p", __func__,
              p_ses->event_info.st_ses.capture_handle, p_ses->event_info.st_ses.pcm);
        stdev->st_extn_cb(ST_EVENT_SESSION_DEREGISTER, &p_ses->event_info);
    }
}

static void ape_free_pcm_device_id(sound_trigger_session_t *p_ses)
{
    unsigned int i;

    for (i = 0; i < stdev->max_ape_sessions; i++) {
        if(stdev->ape_pcm_use_cases[i].pcm_id == p_ses->pcm_id) {
            stdev->ape_pcm_use_cases[i].active = false;
            break;
        }
    }
}

static int cpe_get_pcm_device_id(sound_trigger_session_t *p_ses)
{
    unsigned int i;
    int ret = -1;

    for (i = 0; i < stdev->max_cpe_sessions; i++) {
        if(!stdev->cpe_pcm_use_cases[i].active) {
            stdev->cpe_pcm_use_cases[i].active = true;
            ret = stdev->cpe_pcm_use_cases[i].pcm_id;
            p_ses->use_case_idx = i;
            break;
        }
    }
    if (ret < 0)
        ALOGE("%s: ERROR. no free pcm device available", __func__);

    return ret;
}

static void cpe_free_pcm_device_id(sound_trigger_session_t *p_ses)
{
    unsigned int i;

    for (i = 0; i < stdev->max_cpe_sessions; i++) {
        if(stdev->cpe_pcm_use_cases[i].pcm_id == p_ses->pcm_id) {
            stdev->cpe_pcm_use_cases[i].active = false;
            break;
        }
    }
}

static int read_pcm_data(sound_trigger_session_t *p_ses,
                     unsigned char *buf,
                     unsigned int bytes)
{
    unsigned int copy_bytes, read_bytes;
    unsigned char *updated_rd_ptr;

    ALOGVV("%s: bytes=%d, unread_bytes=%d size=%d", __func__,
            bytes, p_ses->pcm_out_buf_unread_bytes, p_ses->pcm_out_buf_size);

    while (bytes > 0) {
        if (!p_ses->pcm_out_buf_unread_bytes) {
            ALOGVV("%s: waiting on rd_cond, bytes=%d", __func__, bytes);
            /* wait till space is avaialable to write */
            pthread_cond_wait(&p_ses->pcm_out_buf_rd_cond, &p_ses->lock);
            if (!CHECK_STATE(p_ses->state, SES_BUFFERING)) {
                ALOGVV("%s: buffering stopped while waiting on rd_cond, exiting", __func__);
                return -EIO;
            }
        }
        read_bytes = MIN(bytes, p_ses->pcm_out_buf_unread_bytes);

        if (p_ses->pcm_out_buf_end_ptr < (p_ses->pcm_out_buf_rd_ptr + read_bytes)) {
            copy_bytes = p_ses->pcm_out_buf_end_ptr - p_ses->pcm_out_buf_rd_ptr;
            memcpy(buf, p_ses->pcm_out_buf_rd_ptr, copy_bytes);
            memcpy(buf + copy_bytes, p_ses->pcm_out_buf_start_ptr, read_bytes - copy_bytes);
            updated_rd_ptr = p_ses->pcm_out_buf_start_ptr + (read_bytes - copy_bytes);
            p_ses->pcm_out_buf_rd_ptr = updated_rd_ptr;
            p_ses->pcm_out_buf_unread_bytes -= read_bytes;
        } else {
            memcpy(buf, p_ses->pcm_out_buf_rd_ptr, read_bytes);
            p_ses->pcm_out_buf_rd_ptr += read_bytes;
            p_ses->pcm_out_buf_unread_bytes -= read_bytes;
        }
        pthread_cond_signal(&p_ses->pcm_out_buf_wr_cond);
        bytes -= read_bytes;
        buf += read_bytes;
    }
    return 0;
}

static int write_pcm_data(sound_trigger_session_t *p_ses,
                     unsigned char *buf,
                     unsigned int bytes)
{
    unsigned int free_bytes, write_bytes;
    unsigned int copy_bytes;
    unsigned char *updated_wr_ptr;

    //TOD-V: NULL check
    ALOGVV("%s: bytes=%d, unread_bytes=%d size=%d", __func__,
            bytes, p_ses->pcm_out_buf_unread_bytes, p_ses->pcm_out_buf_size);

    while (bytes > 0) {
        free_bytes = p_ses->pcm_out_buf_size - p_ses->pcm_out_buf_unread_bytes;
        if (!free_bytes) {
            ALOGVV("%s: waiting on wr_cond, bytes=%d", __func__, bytes);
            /* wait till space is avaialable to write */
            pthread_cond_wait(&p_ses->pcm_out_buf_wr_cond, &p_ses->lock);
            if (!CHECK_STATE(p_ses->state, SES_BUFFERING)) {
                ALOGVV("%s: buffering stopped while waiting on wr_cond, exiting", __func__);
                return -EIO;
            }
        }
        write_bytes = MIN(bytes, free_bytes);

        if (p_ses->pcm_out_buf_end_ptr < (p_ses->pcm_out_buf_wr_ptr + write_bytes)) {
            copy_bytes = p_ses->pcm_out_buf_end_ptr - p_ses->pcm_out_buf_wr_ptr;
            memcpy(p_ses->pcm_out_buf_wr_ptr, buf, copy_bytes);
            memcpy(p_ses->pcm_out_buf_start_ptr,
                   buf + copy_bytes, write_bytes - copy_bytes);
            updated_wr_ptr = p_ses->pcm_out_buf_start_ptr + (write_bytes - copy_bytes);
            p_ses->pcm_out_buf_wr_ptr = updated_wr_ptr;
            p_ses->pcm_out_buf_unread_bytes += write_bytes;
        } else {
            memcpy(p_ses->pcm_out_buf_wr_ptr, buf, write_bytes);
            p_ses->pcm_out_buf_wr_ptr += write_bytes;
            p_ses->pcm_out_buf_unread_bytes += write_bytes;
        }
        pthread_cond_signal(&p_ses->pcm_out_buf_rd_cond);
        bytes -= write_bytes;
        buf += write_bytes;
    }
    return 0;
}

static int read_capture_bytes(struct pcm *pcm, unsigned char *buf, size_t bytes)
{
    struct listnode *p_ses_node;
    sound_trigger_session_t *p_ses;
    unsigned int copy_bytes;
    bool ses_found = false;
    int ret = 0;

    if (!pcm || !buf || !bytes) {
        ALOGE("%s: Invalid params", __func__);
        return -EINVAL;
    }

    pthread_mutex_lock(&stdev->lock);
    if ((!stdev->run_on_ape && CHECK_STATE(stdev->ssr_state, ST_DEVICE_SSR_CPE))
        || CHECK_STATE(stdev->ssr_state, ST_DEVICE_SSR_APE)) {
        ALOGE("%s: SSR in progress", __func__);
        pthread_mutex_unlock(&stdev->lock);
        return -EIO;
    }
    list_for_each(p_ses_node, &stdev->sound_model_list) {
        p_ses = node_to_item(p_ses_node, sound_trigger_session_t, list_node);
        if (p_ses->pcm == pcm) {
            ses_found = true;
            break;
        }
    }
    if (!ses_found) {
        ALOGE("%s: Session not active", __func__);
        pthread_mutex_unlock(&stdev->lock);
        return -EIO;
    }
    pthread_mutex_unlock(&stdev->lock);

    pthread_mutex_lock(&p_ses->lock);
    ret = p_ses->fptrs->read_pcm(p_ses, buf, bytes);
    pthread_mutex_unlock(&p_ses->lock);

    ALOGVV("%s: Exit ret=%d", __func__, ret);
    return ret;
}

static bool search_for_syncword(sound_trigger_session_t *p_ses,
                                unsigned char *in_buf,
                                unsigned int in_size,
                                unsigned int *pos)
{

    unsigned char *buf_ptr = in_buf;
    unsigned int inc_bytes = 2;

    /* S16_LE is sampled at 16bit boundary, 24_LE and 32_LE will be
       sampled at 4byte boundary */
    if ((p_ses->ftrt_config.format == PCM_FORMAT_S32_LE) ||
        (p_ses->ftrt_config.format == PCM_FORMAT_S24_LE)) {
        inc_bytes += 2;
    }
    *pos = 0;

    while (buf_ptr < (in_buf + in_size)) {
        if (*buf_ptr == CPE_PACKET_SYNC_WORD) {
            *pos = buf_ptr - in_buf;
            ALOGVV("%s: first syncword found at idx %d for format %d",
                   __func__, *pos, p_ses->ftrt_config.format);
            return true;
        }
        buf_ptr += inc_bytes;
    }
    return false;
}

static void get_ftrt_processing_data(sound_trigger_session_t *p_ses,
                              unsigned char *in_buf,
                              unsigned int in_size,
                              unsigned char **out_buf,
                              unsigned int *out_size)
{
    unsigned char *src, *dst;

    if (p_ses->ftrt_config.format == PCM_FORMAT_S24_LE) {
        /* Construct ftrt packed buffer without gaps by skipping MSB byte */
        src = dst = in_buf;
        while (src < (in_buf + in_size)) {
            memmove(dst, src, 3);
            src += 4;
            dst += 3;
        }
        *out_buf = in_buf;
        *out_size = (dst - *out_buf);
    } else {
        *out_buf = in_buf;
        *out_size = in_size;
    }
}

void process_ftrt_capture_bytes(sound_trigger_session_t *p_ses)
{
    struct cpe_packet_hdr *prev_packet = NULL;
    bool first_sync_word_found = false;
    bool read_next_dr_buf = false;
    int sync_max_retry_cnt = SOUND_TRIGGER_SYNC_WORD_MAX_RETRY_CNT;
    int status = 0, dec_status = 0, out_samples = 0;
    unsigned int rt_period_bytes, copy_bytes, dec_bytes, tmp_size;
    unsigned char *driver_rd_buf;
    unsigned int driver_rd_bytes, dr_buf_idx = 0;
    unsigned char *ftrt_buf_ptr;
    unsigned int ftrt_buf_size, ftrt_rem_bytes, ftrt_buf_idx = 0;
    unsigned char *frame_buf_ptr;
    unsigned int frame_buf_idx = 0;
    unsigned char *dec_out_buf;
    unsigned char packet_hdr[sizeof(struct cpe_packet_hdr)];
    unsigned int hdr_size = sizeof(struct cpe_packet_hdr);
    unsigned int hdr_buf_idx = 0;

#ifdef ST_DEBUG_DUMP
    FILE *fptr = NULL;
    FILE *fptr_dp = NULL;
    FILE *fptr_pcm = NULL;
    FILE *fptr_ftrt_packed = NULL;
    char fptr_fn[100], fptr_dp_fn[100], fptr_pcm_fn[100], fptr_ftrt_packed_fn[100];
    int ret_bytes = 0;
    static int temp_cnt = 0;
#endif


    /* Initialize pcm output buffer pointers */
    p_ses->pcm_out_buf_start_ptr = p_ses->pcm_out_buf;
    p_ses->pcm_out_buf_end_ptr = p_ses->pcm_out_buf + p_ses->pcm_out_buf_size;
    p_ses->pcm_out_buf_wr_ptr = p_ses->pcm_out_buf;
    p_ses->pcm_out_buf_rd_ptr = p_ses->pcm_out_buf;
    p_ses->pcm_out_buf_unread_bytes = 0;

    driver_rd_buf = p_ses->ftrt_buf;
    driver_rd_bytes = p_ses->ftrt_buf_size;

#ifdef ST_DEBUG_DUMP
    snprintf(fptr_fn, sizeof(fptr_fn),
             "/data/misc/audio/ftrt_driver_data_%d.bin", temp_cnt);
    snprintf(fptr_dp_fn, sizeof(fptr_dp_fn),
             "/data/misc/audio/ftrt_driver_depacketized_%d.bin", temp_cnt);
    snprintf(fptr_pcm_fn, sizeof(fptr_pcm_fn),
             "/data/misc/audio/ftrt_pcm_out_%d.bin", temp_cnt);
    snprintf(fptr_ftrt_packed_fn, sizeof(fptr_ftrt_packed_fn),
             "/data/misc/audio/ftrt_driver_packed_%d.bin", temp_cnt++);


    fptr = fopen(fptr_fn, "wb");
    if (!fptr) {
        ALOGE("%s: FILE OPEN ERROR driver %s", __func__, strerror(errno));
    }
    fptr_dp = fopen(fptr_dp_fn, "wb");
    if (!fptr_dp) {
        ALOGE("%s: FILE OPEN ERROR depacketized %s", __func__, strerror(errno));
    }
    fptr_pcm = fopen(fptr_pcm_fn, "wb");
    if (!fptr_pcm) {
        ALOGE("%s: FILE OPEN ERROR pcm out %s", __func__, strerror(errno));
    }
    fptr_ftrt_packed = fopen(fptr_ftrt_packed_fn, "wb");
    if (!fptr_ftrt_packed) {
        ALOGE("%s: FILE OPEN ERROR ftrt packed %s", __func__, strerror(errno));
    }
#endif

    while (CHECK_STATE(p_ses->state, SES_BUFFERING)) {

        ALOGVV("%s: pcm_read reading bytes=%d", __func__, driver_rd_bytes);
        status = pcm_read(p_ses->pcm, driver_rd_buf, driver_rd_bytes);
        ALOGVV("%s: pcm_read done", __func__);

    #ifdef ST_DEBUG_DUMP
        if (fptr) {
            ret_bytes = fwrite(driver_rd_buf, 1, driver_rd_bytes, fptr);
            if (ret_bytes != (int)driver_rd_bytes) {
                ALOGE("%s: driver buf fwrite %d < %d", __func__, ret_bytes,
                      driver_rd_bytes);
            } else {
                ALOGVV("%s: dumped driver buf bytes %d",
                       __func__, driver_rd_bytes);
            }
            fflush(fptr);
        }
    #endif
        if (status) {
            ALOGE("%s: pcm read failed status %d - %s", __func__, status,
                  pcm_get_error(p_ses->pcm));
            break;
        }
        if (!first_sync_word_found) {
            first_sync_word_found = search_for_syncword(p_ses, driver_rd_buf,
                                                        driver_rd_bytes, &dr_buf_idx);
            if (!first_sync_word_found) {
                if(--sync_max_retry_cnt <= 0) {
                    ALOGE("%s: max tries for finding sync word reached", __func__);
                    goto exit;
                } else {
                    /* Read next buffer from driver */
                    dr_buf_idx = 0;
                    continue;
                }
            }
        }
        get_ftrt_processing_data(p_ses, &driver_rd_buf[dr_buf_idx],
                                 driver_rd_bytes - dr_buf_idx,
                                 &ftrt_buf_ptr, &ftrt_buf_size);
        #ifdef ST_DEBUG_DUMP_PACKED
            if (fptr_ftrt_packed) {
                ret_bytes = fwrite(ftrt_buf_ptr, 1, ftrt_buf_size, fptr_ftrt_packed);
                if (ret_bytes != (int)ftrt_buf_size) {
                    ALOGE("%s: ftrt packed buf fwrite %d < %d", __func__, ret_bytes,
                          ftrt_buf_size);
                }
                fflush(fptr_ftrt_packed);
            }
        #endif

        while (CHECK_STATE(p_ses->state, SES_BUFFERING)) {
            if (!prev_packet) {
                /* construct new packet */
                tmp_size = MIN((hdr_size - hdr_buf_idx), (ftrt_buf_size - ftrt_buf_idx));
                memcpy(&packet_hdr[hdr_buf_idx], &ftrt_buf_ptr[ftrt_buf_idx], tmp_size);
                hdr_buf_idx += tmp_size;
                ftrt_buf_idx += tmp_size;
                if (hdr_buf_idx == hdr_size) {
                    /* We have a new packet now, parse it */
                    prev_packet = (struct cpe_packet_hdr *)packet_hdr;
                    hdr_buf_idx = 0;
                    /* sync word must be present at packet boundary */
                    if (prev_packet->sync_word != CPE_PACKET_SYNC_WORD) {
                        ALOGE("%s: ERROR. sync word not present", __func__);
                        goto exit;
                    }
                    if (prev_packet->format == CPE_PACKET_FORMAT_EOS) {
                        ALOGVV("%s: EOS ??", __func__);
                        goto exit; /* TODO-V: Not expected now. FTRT to RT switch */
                    }
                }
            }

            if (prev_packet) {
                if (prev_packet->format == CPE_PACKET_FORMAT_NULL) {
                    /* Skip the NULL packets */
                    tmp_size = MIN(prev_packet->size, (ftrt_buf_size - ftrt_buf_idx));
                    ALOGVV("%s: null packet skip: size=%d ftrt_buf_idx=%d ftrt_buf_size=%d prev_packet->size=%d",
                          __func__, tmp_size, ftrt_buf_idx, ftrt_buf_size, prev_packet->size);
                    prev_packet->size -= tmp_size;
                    ftrt_buf_idx += tmp_size;
                } else if (prev_packet->format == CPE_PACKET_FORMAT_PCM) {
                    copy_bytes = MIN(prev_packet->size, (ftrt_buf_size - ftrt_buf_idx));
                    status = write_pcm_data(p_ses, &ftrt_buf_ptr[ftrt_buf_idx], copy_bytes);
                    if (status)
                        goto exit;
                    #ifdef ST_DEBUG_DUMP
                        if (fptr_pcm) {
                            ret_bytes = fwrite(&ftrt_buf_ptr[ftrt_buf_idx], 1, copy_bytes, fptr_pcm);
                            if (ret_bytes != (int)copy_bytes) {
                                ALOGE("%s: fwrite pcm out %d < %d", __func__, ret_bytes, copy_bytes);
                            }
                            fflush(fptr_pcm);
                        }
                    #endif
                    ftrt_buf_idx += copy_bytes;
                    prev_packet->size -= copy_bytes;
                } else if (prev_packet->format == CPE_PACKET_FORMAT_ADPCM) {
                    dec_out_buf = p_ses->dec_buf + ADPCM_MAX_IN_FRAME_SIZE;

                    while (CHECK_STATE(p_ses->state, SES_BUFFERING) &&
                           (ftrt_buf_idx < ftrt_buf_size) &&
                           (prev_packet->size > 0)) {

                        dec_bytes = MIN(prev_packet->size, ADPCM_MAX_IN_FRAME_SIZE);
                        ftrt_rem_bytes = ftrt_buf_size - ftrt_buf_idx;

                        if (!frame_buf_idx && (ftrt_rem_bytes >= dec_bytes)) {
                            /* There are no cached partial frame samples in frame buf.
                               We have full frame in ftrt buf for decoding. Use ftrt
                               buf directly as decoder input */
                            frame_buf_ptr = &ftrt_buf_ptr[ftrt_buf_idx];
                        } else {
                            /* Compute least of all buffer sizes to not to overflow */
                            dec_bytes = MIN(MIN(dec_bytes, ftrt_rem_bytes), (ADPCM_MAX_IN_FRAME_SIZE - frame_buf_idx));
                            memcpy(&p_ses->dec_buf[frame_buf_idx], &ftrt_buf_ptr[ftrt_buf_idx], dec_bytes);
                            frame_buf_ptr = p_ses->dec_buf;
                        }
                        frame_buf_idx += dec_bytes;
                        ftrt_buf_idx += dec_bytes;
                        prev_packet->size -= dec_bytes;

                        if (!prev_packet->size || (frame_buf_idx == ADPCM_MAX_IN_FRAME_SIZE)) {
                            /* if packet size is zero, we may have partial frame to be decoded */
                        #ifdef ST_DEBUG_DUMP
                            ALOGVV("%s: enter for decode- frame_buf_idx=%d ftrt_buf_idx=%d prev_packet->size=%d",
                                   __func__, frame_buf_idx, ftrt_buf_idx, prev_packet->size);
                            if (fptr_dp) {
                                ret_bytes = fwrite(frame_buf_ptr, 1, frame_buf_idx, fptr_dp);
                                fflush(fptr_dp);
                                if (ret_bytes != (int)frame_buf_idx) {
                                    ALOGE("%s: frame buf fwrite %d < %d", __func__, ret_bytes, frame_buf_idx);
                                }
                            }
                        #endif
                            /* Decode ADPCM frame and copy the data to pcm out buffer */
                            dec_status = stdev->adpcm_dec_process((short *)frame_buf_ptr,
                                                                  (short *)dec_out_buf,
                                                                  frame_buf_idx,
                                                                  &out_samples,
                                                                  p_ses->adpcm_dec_state);
                            if (dec_status) {
                                ALOGE("%s: ERROR. adpcm dec failed status %d", __func__, dec_status);
                                goto exit;
                            }
                            ALOGVV("%s: adpcm_dec_process done. frame_buf_idx=%d out_samples=%d",
                                   __func__, frame_buf_idx, out_samples);
                            if (out_samples) {
                            #ifdef ST_DEBUG_DUMP
                                if (fptr_pcm) {
                                    ret_bytes = fwrite(dec_out_buf, 1, out_samples * 2, fptr_pcm);
                                    if (ret_bytes != (int)out_samples * 2) {
                                        ALOGE("%s: fwrite dec out %d < %d", __func__, ret_bytes, out_samples * 2);
                                    }
                                    fflush(fptr_pcm);
                                }
                            #endif
                                status = write_pcm_data(p_ses, dec_out_buf, out_samples << 1);
                                if (status)
                                    goto exit;
                            }
                            frame_buf_idx = 0;
                        }
                    }
                } else {
                    ALOGE("%s: Uknown packet format %d ", __func__, prev_packet->format);
                    goto exit;
                }

                if (!prev_packet->size) {
                    /* packet is processed, remove its reference */
                    prev_packet = NULL;
                }
            }

            if (ftrt_buf_idx == ftrt_buf_size) {
                ALOGVV("%s: ftrt buffer exhausted, read next buffer ftrt_buf_idx=%d",
                       __func__, ftrt_buf_idx);
                ftrt_buf_idx = dr_buf_idx = 0;
                break;
            }
        }
    }

exit:
#ifdef ST_DEBUG_DUMP
    if(fptr)
        fclose(fptr);
    if(fptr_dp)
        fclose(fptr_dp);
    if(fptr_pcm)
        fclose(fptr_pcm);
    if(fptr_ftrt_packed)
        fclose(fptr_ftrt_packed);
#endif
    p_ses->fptrs->stop_buffering(p_ses);
    ALOGVV("%s: Exit status=%d", __func__, status);
}

static void *callback_thread_loop(void *context)
{
    struct sound_trigger_session *st_session =
                                 (struct sound_trigger_session *)context;
    struct sound_trigger_session *p_session = st_session;
    struct sound_trigger_device *stdev = NULL;
    struct snd_lsm_event_status *params;
    struct sound_trigger_phrase_recognition_event *event;
    unsigned int payload_alloc_size = SOUND_TRIGGER_MAX_EVNT_PAYLOAD_SIZE;
    int status = 0;
    int event_status;
    unsigned int i, j;

    if (st_session == NULL) {
        ALOGE("%s: ERROR. null context.. exiting", __func__);
        return NULL;
    }
    ALOGD("%s:[%d] Enter", __func__, st_session->sm_handle);

    transit_retrieve_st_session(&st_session);

    stdev = (struct sound_trigger_device *)st_session->device;
    prctl(PR_SET_NAME, (unsigned long)"sound trigger callback", 0, 0, 0);

    pthread_mutex_lock(&st_session->lock);
    params = calloc(1, sizeof(*params) + payload_alloc_size);
    if (params == NULL) {
        ALOGE("%s: ERROR. insufficient memory for payload", __func__);
        goto exit;
    }

    while (!p_session->exit_callback_thread) {
        params->payload_size = payload_alloc_size;
        ALOGI("%s:[%d] Waiting for SNDRV_LSM_EVENT_STATUS",
                            __func__, st_session->sm_handle);
        pthread_mutex_unlock(&st_session->lock);
        status = pcm_ioctl(p_session->pcm, SNDRV_LSM_EVENT_STATUS, params);
        pthread_mutex_lock(&st_session->lock);
        ALOGI("%s: Received SNDRV_LSM_EVENT_STATUS status=%d", __func__, status);
        if (status < 0) {
            if (errno == ENOMEM) {
                payload_alloc_size = payload_alloc_size << 1;
                params = realloc(params, sizeof(*params) + payload_alloc_size);

                if (params == NULL) {
                    ALOGE("%s: ERROR. Not enough memory for payload",
                          __func__);
                    p_session->exit_callback_thread = true;
                    break;
                }
                continue;
            } else {
                ALOGE("%s: ERROR. SNDRV_LSM_EVENT_STATUS  failed status %d",__func__, status);
                p_session->exit_callback_thread = true;
                break;
            }
        }
        if (p_session->exit_callback_thread)
            break;
        if (!st_session->callback)
            continue;

        ALOGD("%s: params status %d", __func__, params->status);
        switch (params->status) {
        case LSM_VOICE_WAKEUP_STATUS_RUNNING:
            continue;
        case LSM_VOICE_WAKEUP_STATUS_DETECTED:
            event_status = RECOGNITION_STATUS_SUCCESS;
            if (st_session->capture_requested)
                SET_STATE(st_session->state, SES_BUFFERING);

            if (CHECK_STATE(st_session->state, SES_EVENT_RECEIVED)) {
                ALOGD("%s: previous event under process, ignore event", __func__);
                st_session->fptrs->stop_buffering(st_session);
                continue;
            }
            break;
        case LSM_VOICE_WAKEUP_STATUS_END_SPEECH:
        case LSM_VOICE_WAKEUP_STATUS_REJECTED:
            event_status = RECOGNITION_STATUS_FAILURE;
            break;
        default:
            ALOGW("%s: Unknown status %d", __func__, params->status);
            continue;
        }

        event = NULL;
        if (st_session->vendor_uuid_info &&
            st_session->vendor_uuid_info->smlib_handle) {
            /* if smlib is present, get the event from it else send the
               DSP recieved payload as it is to App */
            status = st_session->vendor_uuid_info->
                        generate_st_phrase_recognition_event(
                           st_session->sm_data,
                           st_session->rc_config,
                           params->payload,
                           params->payload_size, &event);

            if (status || !event) {
                ALOGW("%s: smlib fill recognition event failed, status %d",
                      __func__, status);
                if (event)
                    free(event);
                /* Stop buffering if this is not a successful detection
                   and LAB is triggered in lsm driver */
                st_session->fptrs->stop_buffering(st_session);
                continue;
            }
        } else if (!st_session->vendor_uuid_info && stdev->smlib_handle) {
            /* This is SVA non topology solution */
            status = stdev->generate_st_phrase_recognition_event(
                                           st_session->sm_data,
                                           st_session->rc_config,
                                           params->payload,
                                           params->payload_size, &event);
            if (status || !event) {
                ALOGW("%s: SVA smlib fill recognition event failed, status %d",
                      __func__, status);
                if (event)
                    free(event);
                /* Stop buffering if this is not a successful detection and
                   LAB is triggered in lsm driver */
                st_session->fptrs->stop_buffering(st_session);
                continue;
            }
        } else {
            event = calloc(1, sizeof(*event) + params->payload_size);
            if (!event) {
                ALOGE("%s: event allocation failed, size %d", __func__,
                      params->payload_size);
                /* Stop buffering if this is not a successful detection
                   and LAB is triggered in lsm driver */
                st_session->fptrs->stop_buffering(st_session);
                continue;
            }

            memcpy(event->phrase_extras, st_session->rc_config->phrases,
                   st_session->rc_config->num_phrases *
                   sizeof(struct sound_trigger_phrase_recognition_extra));

            event->num_phrases = st_session->rc_config->num_phrases;
            event->common.data_offset = sizeof(*event);
            event->common.data_size = params->payload_size;
            memcpy((char *)event + event->common.data_offset,
                   params->payload, params->payload_size);
        }

        /* fill the remaining recognition event parameters not specific
           to soundmodel lib */
        event->common.status = event_status;
        event->common.type = st_session->sm_data->common.type;
        event->common.model = st_session->sm_handle;
        event->common.capture_available = st_session->capture_requested;
        event->common.capture_delay_ms = 0;
        event->common.capture_preamble_ms = 0;
        event->common.audio_config.sample_rate = SOUND_TRIGGER_SAMPLING_RATE;
        event->common.audio_config.channel_mask = AUDIO_CHANNEL_IN_MONO;
        event->common.audio_config.format = AUDIO_FORMAT_PCM_16_BIT;

        for (i = 0; i < event->num_phrases; i++) {
            ALOGV("%s: [%d] kw_id %d level %d", __func__,i,
                  event->phrase_extras[i].id,
                  event->phrase_extras[i].confidence_level);
            for (j = 0; j < event->phrase_extras[i].num_levels; j++) {
                ALOGV("%s: [%d] user_id %d level %d ", __func__, i,
                      event->phrase_extras[i].levels[j].user_id,
                      event->phrase_extras[i].levels[j].level);
            }
        }

        SET_STATE(st_session->state, SES_EVENT_RECEIVED);
        ALOGI("%s:[%d] send recognition event %d", __func__,
                         st_session->sm_handle, event_status);

        ALOGVV("%s:[%d] status=%d, type=%d, model=%d, capture_avaiable=%d, "
               "num_phrases=%d id=%d", __func__, st_session->sm_handle,
               event->common.status, event->common.type, event->common.model,
               event->common.capture_available, event->num_phrases,
               event->phrase_extras[0].id);

        st_session->callback(&event->common, st_session->cookie);

        if (st_session->capture_requested && st_session->vendor_uuid_info &&
            (st_session->vendor_uuid_info->kw_transfer_mode ==
             FTRT_TRANSFER_MODE) &&
            (event_status == RECOGNITION_STATUS_SUCCESS)) {
            /* Start processing data captured in FTRT mode */
            st_session->fptrs->process_ftrt_capture(st_session);
        }
        free(event);
    }

    if (params)
        free(params);

exit:
    pthread_mutex_unlock(&st_session->lock);
    ALOGD("%s:[%d] Exit", __func__, st_session->sm_handle);
    return NULL;
}

static int reset_ftrt_configuration(sound_trigger_session_t* p_ses)
{
    ALOGVV("%s:[%d] Enter", __func__, p_ses->sm_handle);
    if (p_ses->adpcm_dec_state) {
        free(p_ses->adpcm_dec_state);
        p_ses->adpcm_dec_state = NULL;
    }
    if (p_ses->dec_buf) {
        free(p_ses->dec_buf);
        p_ses->dec_buf = NULL;
    }
    if (p_ses->pcm_out_buf) {
        free(p_ses->pcm_out_buf);
        p_ses->pcm_out_buf = NULL;
    }
    if (p_ses->ftrt_buf) {
        free(p_ses->ftrt_buf);
        p_ses->ftrt_buf = NULL;
    }
    pthread_cond_destroy(&p_ses->pcm_out_buf_rd_cond);
    pthread_cond_destroy(&p_ses->pcm_out_buf_wr_cond);

    return 0;
}

static int initialize_and_configure_ftrt(sound_trigger_session_t* p_ses)
{
    int status = 0;
    unsigned int tmp_bytes;
    int adpcm_dec_scratch_size, sample_size;
    int dec_status;
    struct snd_lsm_output_format_cfg media_cfg;

    ALOGVV("%s:[%d] Enter", __func__, p_ses->sm_handle);

    if (!p_ses->ftrt_buf) {
        p_ses->ftrt_buf_size = pcm_frames_to_bytes(p_ses->pcm, p_ses->ftrt_config.period_size);
        p_ses->ftrt_buf = (unsigned char *) calloc(1, p_ses->ftrt_buf_size);
        if (!p_ses->ftrt_buf) {
            ALOGE("%s: ERROR. Can not allcoate FTRT buffer size %d", __func__, p_ses->ftrt_buf_size);
            status = -ENOMEM;
            goto error_exit;
        }
        ALOGVV("%s: Allocated ftrt buffer size=%d", __func__, p_ses->ftrt_buf_size);
    }
    if (!p_ses->pcm_out_buf) {
        sample_size = CALCULATE_PERIOD_SIZE(PCM_CIRC_BUF_DURATION_MS, p_ses->config.rate, 1, 32);
        p_ses->pcm_out_buf_size = pcm_frames_to_bytes(p_ses->pcm, sample_size);
        p_ses->pcm_out_buf = (unsigned char *) calloc(1, p_ses->pcm_out_buf_size);
        if (!p_ses->pcm_out_buf) {
            ALOGE("%s: ERROR. Can not allcoate out buffer size %d",
                  __func__, p_ses->pcm_out_buf_size);
            status = -ENOMEM;
            goto error_exit;
        }
        ALOGVV("%s: Allocated out buffer size=%d", __func__, p_ses->pcm_out_buf_size);
    }

    media_cfg.format = LSM_OUT_FORMAT_PCM;
    if (CHECK_BIT(p_ses->vendor_uuid_info->kw_capture_format, ADPCM)) {
        if (!p_ses->dec_buf) {
            p_ses->dec_buf = (unsigned char *) calloc(1, ADPCM_MAX_IN_FRAME_SIZE +
                                                      ADPCM_MAX_OUT_FRAME_SIZE);
            if (!p_ses->dec_buf) {
                ALOGE("%s: ERROR. Can not allcoate decoder buffer size %d",
                      __func__, ADPCM_MAX_IN_FRAME_SIZE + ADPCM_MAX_OUT_FRAME_SIZE);
                status = -ENOMEM;
                goto error_exit;
            }
            ALOGVV("%s: Allocated decoder buffers size=%d", __func__,
                   ADPCM_MAX_IN_FRAME_SIZE + ADPCM_MAX_OUT_FRAME_SIZE);
        }

        /* Instantiate the decoder */
        status = stdev->adpcm_dec_get_scratch_size(&adpcm_dec_scratch_size);
        if (status) {
            ALOGE("%s: ERROR. Can not get adpcm dec scratch", __func__);
            status = -EINVAL;
            goto error_exit;
        }

        p_ses->adpcm_dec_state = (void *)calloc(1, adpcm_dec_scratch_size);
        if(!p_ses->adpcm_dec_state)
        {
            ALOGE("%s: ERROR. Can not allcoate decoder scratch", __func__);
            status = -ENOMEM;
            goto error_exit;
        }
        ALOGVV("%s: Allocated adpcm_dec_state size=%d", __func__,
              adpcm_dec_scratch_size);
        dec_status = stdev->adpcm_dec_init(p_ses->adpcm_dec_state);
        if (dec_status) {
            ALOGE("%s: ERROR. ADPCM decoder init failed ret=0x%x", __func__, dec_status);
            status = -ENODEV;
            goto error_exit;
        }
        media_cfg.format = LSM_OUT_FORMAT_ADPCM;
    }

    media_cfg.packing = LSM_OUT_DATA_PACKED;
    media_cfg.events = LSM_OUT_DATA_EVENTS_DISABLED;
    media_cfg.mode = LSM_OUT_TRANSFER_MODE_FTRT;

    ALOGV("%s: media_cfg format=%d, packing=%d, events=%d, mode=%d", __func__,
          media_cfg.format, media_cfg.packing, media_cfg.events, media_cfg.mode);
    status = pcm_ioctl(p_ses->pcm, SNDRV_LSM_OUT_FORMAT_CFG, &media_cfg);
    if (status) {
        ALOGE("%s: ERROR. SNDRV_LSM_OUT_FORMAT_CFG, status=%d", __func__, status);
        goto error_exit;
    }
    pthread_cond_init(&p_ses->pcm_out_buf_rd_cond, (const  pthread_condattr_t *) NULL);
    pthread_cond_init(&p_ses->pcm_out_buf_wr_cond, (const  pthread_condattr_t *) NULL);

    return 0;

error_exit:
    reset_ftrt_configuration(p_ses);
    return status;
}

int ape_reg_sm(sound_trigger_session_t *p_ses)
{
    int status = 0;
    struct snd_lsm_sound_model_v2 lsm_sm_v2 = {0,};
    struct st_vendor_info *v_info = p_ses->vendor_uuid_info;
    struct snd_lsm_session_data ses_data;
    struct snd_lsm_module_params lsm_params;
    struct lsm_params_info param_info;
    pthread_attr_t attr;
    int app_type = ACDB_LSM_APP_TYPE_NO_TOPOLOGY;
    bool use_topology = false;

    ALOGD("%s:[%d] Enter", __func__, p_ses->sm_handle);

    p_ses->pcm_id = ape_get_pcm_device_id(p_ses);
    if(p_ses->pcm_id < 0)
        return -ENODEV;

    if(v_info) {
        app_type = v_info->app_type;
        use_topology = true;
    }
#if 0
    status = platform_stdev_send_calibration(stdev->platform,
                                              AUDIO_DEVICE_NONE,
                                              p_ses->exec_mode,
                                              app_type, use_topology,
                                              ST_SESSION_CAL);

    ALOGD("%s: app_type=%d use_topo=%d cal status=%d",
          __func__, app_type, use_topology, status);

    /* Backward compatibility checks
       1. If no vendor present, use regular SVA non-topology solution.
       2. If vendor present is QC SVA and acdb doesn't have topology id,
        use regular QC non-topology solution.
       3. If vendor present is ISV and acdb doesn't have topology id,
          return error */
    if (use_topology && (status == -ENODATA) && v_info->is_qc_uuid) {
        /* In case of old acdb data files, calibration hadn't been
           sent to driver due to app_type we requested is for static
           topology. Resend calibration with non topology app_type */
        app_type = ACDB_LSM_APP_TYPE_NO_TOPOLOGY;
        use_topology = false;
        status = platform_stdev_send_calibration(stdev->platform,
                                                  AUDIO_DEVICE_NONE,
                                                  p_ses->exec_mode,
                                                  app_type, use_topology,
                                                  ST_SESSION_CAL);
    }
    if (status) {
        ALOGE("%s: ERROR. sending calibration failed status %d topo %d",
              __func__, status, use_topology);
        goto sm_error;
    }
#endif
    p_ses->use_topology_intf = use_topology;

    p_ses->config = stdev_ape_pcm_config;
    ALOGD("%s: opening pcm device=%d topo %d", __func__, p_ses->pcm_id,
          p_ses->use_topology_intf);
    ALOGV("%s: config: channels=%d rate=%d, period_size=%d, period_cnt=%d, format=%d",
          __func__, p_ses->config.channels, p_ses->config.rate,
          p_ses->config.period_size, p_ses->config.period_count,
          p_ses->config.format);
    p_ses->pcm = pcm_open(SOUND_CARD, p_ses->pcm_id,
                          PCM_IN, &p_ses->config);
    if (!p_ses->pcm) {
        ALOGE("%s: ERROR. pcm_open failed", __func__);
        status = -ENODEV;
        goto sm_error;
    }
    if (!pcm_is_ready(p_ses->pcm)) {
        ALOGE("%s: ERROR. pcm_is_ready failed err=%s", __func__,
              pcm_get_error(p_ses->pcm));
        status = -ENODEV;
        goto sm_error;
    }

    /* Although app_id is valid only for non-topology solution. The driver
       opens DSP LSM session with this ioctl cmd */
    ses_data.app_id = LSM_VOICE_WAKEUP_APP_ID_V2;
    status = pcm_ioctl(p_ses->pcm, SNDRV_LSM_SET_SESSION_DATA, &ses_data);
    if (status) {
        ALOGE("%s: ERROR. SNDRV_LSM_SET_SESSION_DATA failed status %d",
              __func__, status);
        goto sm_error;
    }

    if(p_ses->use_topology_intf) {
        param_info.param_type = LSM_REG_SND_MODEL;
        param_info.module_id = v_info->params[LOAD_SOUND_MODEL].module_id;
        param_info.param_id = v_info->params[LOAD_SOUND_MODEL].param_id;
        param_info.param_size = p_ses->sm_data->common.data_size;
        param_info.param_data =
            (unsigned char*)p_ses->sm_data + p_ses->sm_data->common.data_offset;

        lsm_params.num_params = 1;
        lsm_params.params = (unsigned char*)&param_info;
        lsm_params.data_size =
            lsm_params.num_params * sizeof(struct lsm_params_info);

        status = pcm_ioctl(p_ses->pcm, SNDRV_LSM_SET_MODULE_PARAMS, &lsm_params);
        if (status) {
            ALOGE("%s: ERROR. SNDRV_LSM_SET_MODULE_PARAMS->reg status=%d",
                  __func__, status);
            goto sm_error;
        }
    } else {
        lsm_sm_v2.data = (unsigned char*)p_ses->sm_data + p_ses->sm_data->common.data_offset;
        lsm_sm_v2.data_size = p_ses->sm_data->common.data_size;
        ALOGD("%s: data=%p, data_size=%d, conf_level=%p, num_conf_level=%d",
               __func__, lsm_sm_v2.data, lsm_sm_v2.data_size,
              lsm_sm_v2.confidence_level, lsm_sm_v2.num_confidence_levels);
        status = pcm_ioctl(p_ses->pcm, SNDRV_LSM_REG_SND_MODEL_V2, &lsm_sm_v2);
        if (status) {
            ALOGE("%s: ERROR. SNDRV_LSM_REG_SND_MODEL_V2 failed, status=%d",
                  __func__, status);
            goto sm_error;
        }
    }

    /* Callback thread to wait on event detection */
    p_ses->exit_callback_thread = false;
    pthread_attr_init(&attr);
    pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_JOINABLE);
    pthread_create(&p_ses->callback_thread, &attr,
                        callback_thread_loop, p_ses);

    ALOGD("%s:[%d] Exit, status=%d", __func__, p_ses->sm_handle, status);
    return 0;

sm_error:
    ape_free_pcm_device_id(p_ses);
    if (p_ses->pcm) {
        pcm_close(p_ses->pcm);
        p_ses->pcm = NULL;
    }

    ALOGD("%s:[%d] Exit, status=%d", __func__, p_ses->sm_handle, status);
    return status;
}

int ape_dereg_sm(sound_trigger_session_t *p_ses)
{
    int status = 0, buf_en;
    struct st_vendor_info *v_info = p_ses->vendor_uuid_info;
    struct snd_lsm_module_params lsm_params;
    struct lsm_params_info param_info;

    ALOGD("%s:[%d] Enter", __func__, p_ses->sm_handle);

    if (!p_ses->pcm) {
        ALOGV("%s: pcm NULL", __func__);
        return status;
    }

    /* Exit the callback thread waiting on event detection */
    p_ses->exit_callback_thread = true;
    status = pcm_ioctl(p_ses->pcm, SNDRV_LSM_ABORT_EVENT);
    if (status)
        ALOGE("%s: ERROR. SNDRV_LSM_ABORT_EVENT failed, status=%d", __func__,
              status);

    pthread_mutex_unlock(&p_ses->lock);
    pthread_join(p_ses->callback_thread, (void **) NULL);
    pthread_mutex_lock(&p_ses->lock);

    if (p_ses->capture_requested) {
        buf_en = 0;
        status = pcm_ioctl(p_ses->pcm, SNDRV_LSM_LAB_CONTROL, &buf_en);
        if (status)
            ALOGE("%s: ERROR. SNDRV_LSM_LAB_CONTROL failed, status=%d", __func__, status);
    }

    if (p_ses->use_topology_intf) {
        /* Topology based */
        param_info.param_type = LSM_DEREG_SND_MODEL;
        param_info.module_id = v_info->params[UNLOAD_SOUND_MODEL].module_id;
        param_info.param_id = v_info->params[UNLOAD_SOUND_MODEL].param_id;
        param_info.param_size = 0;

        lsm_params.num_params = 1;
        lsm_params.params = (unsigned char*)&param_info;
        lsm_params.data_size =
            lsm_params.num_params * sizeof(struct lsm_params_info);

        status = pcm_ioctl(p_ses->pcm, SNDRV_LSM_SET_MODULE_PARAMS, &lsm_params);
        if (status)
            ALOGE("%s: ERROR. SNDRV_LSM_SET_MODULE_PARAMS->dereg status=%d",
                  __func__, status);
    } else {
        /* Non topology based */
        status = pcm_ioctl(p_ses->pcm, SNDRV_LSM_DEREG_SND_MODEL);
        if (status)
            ALOGE("%s: ERROR. SNDRV_LSM_DEREG_SND_MODEL failed", __func__);
    }

    pcm_close(p_ses->pcm);
    p_ses->pcm = NULL;
    ape_free_pcm_device_id(p_ses);

    ALOGD("%s:[%d] Exit, status=%d", __func__, p_ses->sm_handle, status);
    return status;
}

int ape_reg_sm_params(sound_trigger_session_t* p_ses)
{
    int status = 0, buf_en = 0, idx = 0;
    int retry_num = 0;
    struct st_vendor_info *v_info = p_ses->vendor_uuid_info;
    struct snd_lsm_detection_params params;
    struct snd_lsm_module_params lsm_params;
    struct lsm_params_info param_info[3];
    struct lsm_params_info *cfl_params;
    struct lsm_params_info *op_params;
    struct lsm_params_info *poll_en_params;
    struct lsm_params_info *cus_params;
    struct snd_lsm_detect_mode det_mode;
    struct snd_lsm_poll_enable poll_enable;

    ALOGD("%s:[%d] Enter", __func__, p_ses->sm_handle);
    if (!p_ses->pcm) {
        ALOGW("%s: pcm NULL", __func__);
        return status;
    }

    status = pcm_ioctl(p_ses->pcm, SNDRV_LSM_SET_PORT);
    if (status) {
        ALOGE("%s: ERROR. SNDRV_LSM_SET_PORT, status=%d", __func__, status);
        goto exit;
    }

    status = pcm_start(p_ses->pcm);
    while (status && (retry_num < SOUND_TRIGGER_PCM_MAX_RETRY)) {
        usleep(SOUND_TRIGGER_PCM_SLEEP_WAIT);
        retry_num++;
        ALOGI("%s: pcm_start retrying..status %d errno %d, retry cnt %d",
              __func__, status, errno, retry_num);
        status = pcm_start(p_ses->pcm);
    }
    if (status) {
        ALOGE("%s: ERROR. pcm_start failed, returned status %d",
              __func__, status);
        goto exit;
    }

    /* SVA doesn't support per keyword recogntion mode.
       Use the per soundmodel recognition mode */
    if(p_ses->recognition_mode & RECOGNITION_MODE_VOICE_TRIGGER){
        det_mode.mode= LSM_MODE_KEYWORD_ONLY_DETECTION;
        if (p_ses->recognition_mode & RECOGNITION_MODE_USER_IDENTIFICATION)
            det_mode.mode = LSM_MODE_USER_KEYWORD_DETECTION;
    } else {
        ALOGE("%s: Unknown recognition mode %d", __func__, p_ses->recognition_mode);
        goto exit;
    }


    if (p_ses->use_topology_intf) {
        /* Topology based:
           if smlib is not present, pass only the opaque data as custom params and
           ignore sending all other params */
        if (v_info->smlib_handle) {
            det_mode.detect_failure = stdev->detect_failure;
            lsm_params.num_params = 0;

            /* fill confidence level params */
            cfl_params = &param_info[idx++];
            cfl_params->param_type = LSM_MIN_CONFIDENCE_LEVELS;
            cfl_params->module_id = v_info->params[CONFIDENCE_LEVELS].module_id;
            cfl_params->param_id = v_info->params[CONFIDENCE_LEVELS].param_id;
            cfl_params->param_size = p_ses->num_conf_levels;
            cfl_params->param_data = p_ses->conf_levels;
            lsm_params.num_params++;

            /* fill operation mode params */
            op_params = &param_info[idx++];
            op_params->param_type = LSM_OPERATION_MODE;
            op_params->module_id = v_info->params[OPERATION_MODE].module_id;
            op_params->param_id = v_info->params[OPERATION_MODE].param_id;
            op_params->param_size = sizeof(det_mode);
            op_params->param_data = (unsigned char *)&det_mode;
            lsm_params.num_params++;

            {
                unsigned int i;
                ALOGV("%s: dm %d df %d ncl %d lab %d", __func__,
                      det_mode.mode, det_mode.detect_failure,
                      cfl_params->param_size, p_ses->capture_requested);
                for (i = 0; i < cfl_params->param_size; i++) {
                    ALOGV("%s: conf_levels[%d] = %d", __func__,
                                        i, cfl_params->param_data[i]);
                }
            }
        }

        if (stdev->support_lec) {
            poll_enable.poll_en = false;
            poll_en_params = &param_info[idx++];
            poll_en_params->param_type = LSM_POLLING_ENABLE;
            poll_en_params->module_id = v_info->params[POLLING_ENABLE].module_id;
            poll_en_params->param_id = v_info->params[POLLING_ENABLE].param_id;
            poll_en_params->param_size = sizeof(poll_enable);
            poll_en_params->param_data = (unsigned char *)&poll_enable;
            lsm_params.num_params++;
        }

        if (p_ses->rc_config->data_size) {
            /* fill opaque data as custom params */
            cus_params = &param_info[idx++];
            cus_params->param_type = LSM_CUSTOM_PARAMS;
            cus_params->param_size = p_ses->rc_config->data_size;
            cus_params->param_data =
                (unsigned char *)p_ses->rc_config + p_ses->rc_config->data_offset;
            lsm_params.num_params++;
        }

        if (lsm_params.num_params) {
            lsm_params.params = (unsigned char*)param_info;
            lsm_params.data_size =
                lsm_params.num_params * sizeof(struct lsm_params_info);

            status = pcm_ioctl(p_ses->pcm, SNDRV_LSM_SET_MODULE_PARAMS, &lsm_params);
            if (status) {
                ALOGE("%s: ERROR. SNDRV_LSM_SET_MODULE_PARAMS->reg_params, status=%d",
                      __func__, status);
                goto exit;
            }
        }
    } else {
        /* Non topology based */
        params.conf_level = p_ses->conf_levels;
        params.num_confidence_levels = p_ses->num_conf_levels;
        params.detect_failure = stdev->detect_failure;
        params.detect_mode = det_mode.mode;
        params.poll_enable = stdev->support_lec ? false : true;

        {
            unsigned int i;
            ALOGV("%s: dm %d df %d ncl %d lab %d", __func__,
                  params.detect_mode, params.detect_failure,
                  params.num_confidence_levels, p_ses->capture_requested);
            for (i = 0; i < params.num_confidence_levels; i++) {
                ALOGV("%s: conf_levels[%d] = %d", __func__,
                                    i, params.conf_level[i]);
            }
        }

        status = pcm_ioctl(p_ses->pcm, SNDRV_LSM_SET_PARAMS, &params);
        if (status) {
            ALOGE("%s: ERROR. SNDRV_LSM_SET_PARAMS, status=%d", __func__, status);
            goto exit;
        }
    }

    buf_en = p_ses->capture_requested ? 1: 0;
    status = pcm_ioctl(p_ses->pcm, SNDRV_LSM_LAB_CONTROL, &buf_en);
    if (status)
        ALOGE("%s: ERROR. SNDRV_LSM_LAB_CONTROL failed, status=%d", __func__, status);
exit:
    ALOGD("%s:[%d] Exit, status=%d", __func__, p_ses->sm_handle, status);
    return status;
}

int ape_dereg_sm_params(sound_trigger_session_t* p_ses)
{
    ALOGD("%s:[%d] Enter", __func__, p_ses->sm_handle);
    if (p_ses->pcm)
        pcm_stop(p_ses->pcm);
    return 0;
}

int ape_start(sound_trigger_session_t* p_ses)
{
    int status = 0;
    pthread_attr_t attr;

    ALOGD("%s:[%d] Enter", __func__, p_ses->sm_handle);
    if (!p_ses->pcm) {
        ALOGW("%s: pcm NULL", __func__);
        return status;
    }

    status = pcm_ioctl(p_ses->pcm, SNDRV_LSM_START);
    if (status) {
        ALOGE("%s: ERROR. SNDRV_LSM_START failed, status=%d", __func__, status);
    }

    ALOGD("%s:[%d] Exit, status=%d", __func__, p_ses->sm_handle, status);
    return status;
}

int ape_stop(sound_trigger_session_t* p_ses)
{
    int status = 0;

    ALOGI("%s:[%d] Enter", __func__, p_ses->sm_handle);
    if (!p_ses->pcm) {
        ALOGW("%s: pcm NULL", __func__);
        return status;
    }

    status = pcm_ioctl(p_ses->pcm, SNDRV_LSM_STOP);
    if(status)
        ALOGE("%s: ERROR. SNDDRV_LSM_STOP failed, status=%d", __func__, status);

    ALOGD("%s:[%d] Exit, status=%d", __func__, p_ses->sm_handle, status);
    return status;
}

int ape_stop_buffering(sound_trigger_session_t* p_ses)
{
    int status = 0;

    ALOGD("%s:[%d] Enter pcm %p", __func__, p_ses->sm_handle, p_ses->pcm);
    if (!p_ses->pcm) {
        ALOGW("%s: pcm NULL", __func__);
        return status;
    }

    status = pcm_ioctl(p_ses->pcm, SNDRV_LSM_STOP_LAB);
    if (status) {
        ALOGE("%s: ERROR. SNDRV_LSM_STOP_BUFFERING failed status %d", __func__, status);
    } else {
        status = pcm_ioctl(p_ses->pcm, SNDRV_PCM_IOCTL_RESET);
        if(status)
            ALOGE("%s: ERROR. SNDRV_PCM_IOCTL_RESET failed status %d", __func__, status);
    }
    ALOGD("%s:[%d] Exit, status=%d", __func__, p_ses->sm_handle, status);
    return status;
}

int cpe_reg_sm(sound_trigger_session_t *p_ses)
{
    int status = 0, fd;
    int app_type = ACDB_LSM_APP_TYPE_NO_TOPOLOGY;
    struct snd_lsm_sound_model_v2 lsm_sm_v2 = {0,};
    struct snd_lsm_module_params lsm_params;
    struct lsm_params_info param_info;
    struct st_vendor_info *v_info = p_ses->vendor_uuid_info;
    struct pcm_config  *config;
    pthread_attr_t attr;
    bool use_topology = false;

    ALOGD("%s:[%d] Enter", __func__, p_ses->sm_handle);

    p_ses->pcm_id = cpe_get_pcm_device_id(p_ses);
    if (p_ses->pcm_id < 0)
        return -ENODEV;
#if 0
    status = platform_stdev_send_calibration(stdev->platform,
                                              AUDIO_DEVICE_NONE,
                                              p_ses->exec_mode,
                                              app_type, use_topology,
                                              ST_SESSION_CAL);

    ALOGD("%s: app_type=%d use_topo=%d cal status=%d",
          __func__, app_type, use_topology, status);

    /* Backward compatibility checks
       1. If no vendor present, use regular SVA non-topology solution.
       2. If vendor present is QC SVA and acdb doesn't have topology id,
        use regular QC non-topology solution.
       3. If vendor present is ISV and acdb doesn't have topology id,
          return error */
    if (use_topology && (status == -ENODATA) && v_info->is_qc_uuid) {
        /* In case of old acdb data files, calibration hadn't been
           sent to driver due to app_type we requested is for static
           topology. Resend calibration with non topology app_type */
        app_type = ACDB_LSM_APP_TYPE_NO_TOPOLOGY;
        use_topology = false;
        status = platform_stdev_send_calibration(stdev->platform,
                                                  stdev->capture_device,
                                                  p_ses->exec_mode,
                                                  app_type, use_topology,
                                                  ST_SESSION_CAL);
    }

    if (status) {
        ALOGE("%s: ERROR. sending session calibration failed status %d",
               __func__, status);
        goto sm_error;
    }
#endif
    if (v_info && (strcmp(v_info->cpe_firmware_image,""))) {
        ALOGV("%s: cpe firmware image %s", __func__, v_info->cpe_firmware_image);
        /* Write the vendor firmware image name in sysfs entry */
        fd = open("/sys/kernel/wcd_cpe/image_name", O_WRONLY);
        if (fd >= 0) {
            write(fd, v_info->cpe_firmware_image, sizeof(v_info->cpe_firmware_image));
            close(fd);
        }
        else {
            ALOGE("%s: ERROR. sysfs open to write firmware name failed : %s",
                  __func__, strerror(errno));
            goto sm_error;
        }
    }
    p_ses->use_topology_intf = use_topology;
    p_ses->config = stdev_cpe_pcm_config;
    config = &p_ses->config;

    if (v_info && (v_info->kw_transfer_mode == FTRT_TRANSFER_MODE)) {
        /* Get the maximum supported sample rate and bit width from
           driver and open the driver with these values to capture
           keyword in FTRT mode */
        struct pcm_params *params;

        params = pcm_params_get(SOUND_CARD, p_ses->pcm_id, PCM_IN);
        if (!params) {
            ALOGE("%s: ERROR. couldn't get pcm params, device %d - %s",
                  __func__, p_ses->pcm_id, strerror(errno));
            goto sm_error;
        }
        p_ses->ftrt_config.rate = pcm_params_get_max(params, PCM_PARAM_RATE);
        if(pcm_params_format_test(params, PCM_FORMAT_S32_LE)) {
            ALOGV("%s: format found is S32_LE", __func__);
            p_ses->ftrt_config.format = PCM_FORMAT_S32_LE;
        } else if (pcm_params_format_test(params, PCM_FORMAT_S24_LE)) {
            ALOGV("%s: format found is S24_LE", __func__);
            p_ses->ftrt_config.format = PCM_FORMAT_S24_LE;
        } else if (pcm_params_format_test(params, PCM_FORMAT_S16_LE)) {
            ALOGV("%s: format found is S16_LE", __func__);
            p_ses->ftrt_config.format = PCM_FORMAT_S16_LE;
        } else {
            ALOGV("%s: No valid format found setting S16_LE", __func__);
            p_ses->ftrt_config.format = PCM_FORMAT_S16_LE;
        }

        p_ses->ftrt_config.period_size =
            CALCULATE_PERIOD_SIZE(SOUND_TRIGGER_CPE_FTRT_BUFFER_DURATION_MS,
                                  p_ses->ftrt_config.rate,
                                  SOUND_TRIGGER_CPE_FTRT_PERIOD_COUNT, 32);
        p_ses->ftrt_config.period_count = SOUND_TRIGGER_CPE_FTRT_PERIOD_COUNT;
        p_ses->ftrt_config.channels = SOUND_TRIGGER_CHANNEL_MODE;

        ALOGV("%s: ftrt rate=%d format=%d", __func__, p_ses->ftrt_config.rate,
              p_ses->ftrt_config.format);
        pcm_params_free(params);

        if(CHECK_BIT(v_info->kw_capture_format, ADPCM))
            p_ses->config.period_size = ADPCM_MAX_OUT_SAMPLES_PER_FRAME;

        config = &p_ses->ftrt_config;
    }

    ALOGD("%s: opening pcm device=%d topo %d", __func__, p_ses->pcm_id,
          p_ses->use_topology_intf);
    ALOGV("%s: config: channels=%d rate=%d, period_size=%d, "
          "period_cnt=%d, format=%d", __func__, config->channels,
          config->rate, config->period_size,
          config->period_count, config->format);

    p_ses->pcm = pcm_open(SOUND_CARD, p_ses->pcm_id,
                          PCM_IN, config);

    if (!p_ses->pcm) {
        ALOGE("%s: ERROR. pcm_open failed", __func__);
        status = -ENODEV;
        goto sm_error;
    }
    if (!pcm_is_ready(p_ses->pcm)) {
        ALOGE("%s: ERROR. pcm_is_ready failed err=%s", __func__,
              pcm_get_error(p_ses->pcm));
        status = -ENODEV;
        goto sm_error;
    }

    if (p_ses->use_topology_intf) {
        param_info.param_type = LSM_REG_SND_MODEL;
        param_info.module_id = v_info->params[LOAD_SOUND_MODEL].module_id;
        param_info.param_id = v_info->params[LOAD_SOUND_MODEL].param_id;
        param_info.param_size = p_ses->sm_data->common.data_size;
        param_info.param_data =
            (unsigned char*)p_ses->sm_data + p_ses->sm_data->common.data_offset;

        lsm_params.num_params = 1;
        lsm_params.params = (unsigned char*)&param_info;
        lsm_params.data_size = lsm_params.num_params * sizeof(struct lsm_params_info);

        status = pcm_ioctl(p_ses->pcm, SNDRV_LSM_SET_MODULE_PARAMS, &lsm_params);
        if (status) {
            ALOGE("%s: ERROR. SNDRV_LSM_SET_MODULE_PARAMS->reg status=%d",
                  __func__, status);
            goto sm_error;
        }
    } else {
        lsm_sm_v2.data = (unsigned char*)p_ses->sm_data + p_ses->sm_data->common.data_offset;
        lsm_sm_v2.data_size = p_ses->sm_data->common.data_size;
        ALOGD("%s: data=%p, data_size=%d, conf_level=%p, num_conf_level=%d",
               __func__, lsm_sm_v2.data, lsm_sm_v2.data_size,
              lsm_sm_v2.confidence_level, lsm_sm_v2.num_confidence_levels);
        status = pcm_ioctl(p_ses->pcm, SNDRV_LSM_REG_SND_MODEL_V2, &lsm_sm_v2);
        if (status) {
            ALOGE("%s: ERROR. SNDRV_LSM_REG_SND_MODEL_V2 failed, status=%d",
                  __func__, status);
            goto sm_error;
        }
    }

    /* Callback thread to wait on event detection */
    p_ses->exit_callback_thread = false;
    pthread_attr_init(&attr);
    pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_JOINABLE);
    pthread_create(&p_ses->callback_thread, &attr,
                        callback_thread_loop, p_ses);

    ALOGD("%s:[%d] Exit, status=%d", __func__, p_ses->sm_handle, status);
    return 0;

sm_error:
    cpe_free_pcm_device_id(p_ses);
    if (p_ses->pcm) {
        pcm_close(p_ses->pcm);
        p_ses->pcm = NULL;
    }

    ALOGD("%s:[%d] Exit, status=%d", __func__, p_ses->sm_handle, status);
    return status;
}

int cpe_dereg_sm(sound_trigger_session_t *p_ses)
{
    int status = 0, buf_en;
    struct st_vendor_info *v_info = p_ses->vendor_uuid_info;
    struct snd_lsm_module_params lsm_params;
    struct lsm_params_info param_info;

    ALOGD("%s:[%d] Enter", __func__, p_ses->sm_handle);

    if (!p_ses->pcm) {
        ALOGV("%s: pcm NULL", __func__);
        return status;
    }

    /* Exit the callback thread waiting on event detection */
    p_ses->exit_callback_thread = true;
    status = pcm_ioctl(p_ses->pcm, SNDRV_LSM_ABORT_EVENT);
    if (status)
        ALOGE("%s: SNDRV_LSM_ABORT_EVENT failed, status=%d", __func__, status);

    pthread_mutex_unlock(&p_ses->lock);
    pthread_join(p_ses->callback_thread, (void **) NULL);
    pthread_mutex_lock(&p_ses->lock);

    if (p_ses->capture_requested) {
        buf_en = 0;
        status = pcm_ioctl(p_ses->pcm, SNDRV_LSM_LAB_CONTROL, &buf_en);
        if (status)
            ALOGE("%s: ERROR. SNDRV_LSM_LAB_CONTROL failed, status=%d", __func__, status);
    }

    if (p_ses->use_topology_intf) {
        param_info.param_type = LSM_DEREG_SND_MODEL;
        param_info.module_id = v_info->params[UNLOAD_SOUND_MODEL].module_id;
        param_info.param_id = v_info->params[UNLOAD_SOUND_MODEL].param_id;
        param_info.param_size = 0;

        lsm_params.num_params = 1;
        lsm_params.params = (unsigned char*)&param_info;
        lsm_params.data_size =
            lsm_params.num_params * sizeof(struct lsm_params_info);

        status = pcm_ioctl(p_ses->pcm, SNDRV_LSM_SET_MODULE_PARAMS, &lsm_params);
        if (status)
            ALOGE("%s: ERROR. SNDRV_LSM_SET_MODULE_PARAMS->dereg status=%d",
                  __func__, status);
    } else {
        status = pcm_ioctl(p_ses->pcm, SNDRV_LSM_DEREG_SND_MODEL);
        if (status)
            ALOGE("%s: ERROR. SNDRV_LSM_DEREG_SND_MODEL failed %d", __func__, status);
    }

    pcm_close(p_ses->pcm);
    p_ses->pcm = NULL;
    cpe_free_pcm_device_id(p_ses);

    ALOGD("%s:[%d] Exit, status=%d", __func__, p_ses->sm_handle, status);
    return status;
}

int cpe_reg_sm_params(sound_trigger_session_t* p_ses)
{
    int status = 0, buf_en = 0, idx = 0;
    int retry_num = 0;
    unsigned char temp_conf_levels[1];
    struct st_vendor_info *v_info = p_ses->vendor_uuid_info;
    struct snd_lsm_detection_params params;
    struct snd_lsm_module_params lsm_params;
    struct lsm_params_info param_info[3];
    struct lsm_params_info *cfl_params;
    struct lsm_params_info *op_params;
    struct lsm_params_info *cus_params;
    struct snd_lsm_detect_mode det_mode;

    ALOGD("%s:[%d] Enter", __func__, p_ses->sm_handle);
    if (!p_ses->pcm) {
        ALOGW("%s: pcm NULL", __func__);
        return status;
    }

    status = pcm_start(p_ses->pcm);
    while (status && (retry_num < SOUND_TRIGGER_PCM_MAX_RETRY)) {
        usleep(SOUND_TRIGGER_PCM_SLEEP_WAIT);
        retry_num++;
        ALOGI("%s: pcm_start retrying..status %d errno %d, retry cnt %d",
              __func__, status, errno, retry_num);
        status = pcm_start(p_ses->pcm);
    }
    if (status) {
        ALOGE("%s: ERROR. pcm_start failed, returned status %d",
              __func__, status);
        return status;
    }

    status = pcm_ioctl(p_ses->pcm, SNDRV_LSM_SET_PORT);
    if (status) {
        ALOGE("%s: ERROR. SNDRV_LSM_SET_PORT, status=%d", __func__, status);
        goto err_exit_1;
    }

    /* SVA doesn't support per keyword recogntion mode.
       Use the per soundmodel recognition mode */
    if(p_ses->recognition_mode & RECOGNITION_MODE_VOICE_TRIGGER){
        det_mode.mode= LSM_MODE_KEYWORD_ONLY_DETECTION;
        if (p_ses->recognition_mode & RECOGNITION_MODE_USER_IDENTIFICATION)
            det_mode.mode = LSM_MODE_USER_KEYWORD_DETECTION;
    } else {
        ALOGE("%s: Unknown recognition mode %d", __func__, p_ses->recognition_mode);
        goto err_exit_1;
    }

    if (p_ses->use_topology_intf) {
        /* if smlib is not present, pass only the opaque data as custom params and
           ignore sending all other params */
        if (v_info->smlib_handle) {
            det_mode.detect_failure = stdev->detect_failure;
            lsm_params.num_params = 0;

            /* fill confidence level params if present */
            if (p_ses->conf_levels) {
                cfl_params = &param_info[idx++];
                cfl_params->param_type = LSM_MIN_CONFIDENCE_LEVELS;
                cfl_params->module_id = v_info->params[CONFIDENCE_LEVELS].module_id;
                cfl_params->param_id = v_info->params[CONFIDENCE_LEVELS].param_id;
                cfl_params->param_size = p_ses->num_conf_levels;
                cfl_params->param_data = p_ses->conf_levels;
                lsm_params.num_params++;

                {
                    unsigned int i;
                    ALOGV("%s: dm %d df %d ncl %d lab %d", __func__,
                          det_mode.mode, det_mode.detect_failure,
                          cfl_params->param_size, p_ses->capture_requested);
                    for (i = 0; i < cfl_params->param_size; i++) {
                        ALOGV("%s: conf_levels[%d] = %d", __func__,
                                            i, cfl_params->param_data[i]);
                    }
                }
            }

            /* fill operation mode params */
            op_params = &param_info[idx++];
            op_params->param_type = LSM_OPERATION_MODE;
            op_params->module_id = v_info->params[OPERATION_MODE].module_id;
            op_params->param_id = v_info->params[OPERATION_MODE].param_id;
            op_params->param_size = sizeof(det_mode);
            op_params->param_data = (unsigned char *)&det_mode;
            lsm_params.num_params++;
        }

        if (p_ses->rc_config->data_size) {
            /* fill opaque data as custom params */
            cus_params = &param_info[idx++];
            cus_params->param_type = LSM_CUSTOM_PARAMS;
            cus_params->param_size = p_ses->rc_config->data_size;
            cus_params->param_data =
                (unsigned char *)p_ses->rc_config + p_ses->rc_config->data_offset;
            lsm_params.num_params++;
        }

        if (lsm_params.num_params) {
            lsm_params.params = (unsigned char*)param_info;
            lsm_params.data_size =
                lsm_params.num_params * sizeof(struct lsm_params_info);

            status = pcm_ioctl(p_ses->pcm, SNDRV_LSM_SET_MODULE_PARAMS, &lsm_params);
            if (status) {
                ALOGE("%s: ERROR. SNDRV_LSM_SET_MODULE_PARAMS->reg_params %d, status=%d",
                      __func__, lsm_params.num_params, status);
                goto err_exit_1;
            }
        }
    } else {
        params.conf_level = (p_ses->conf_levels != NULL) ?
                                    p_ses->conf_levels : temp_conf_levels;
        params.num_confidence_levels = (p_ses->conf_levels != NULL) ?
                                    p_ses->num_conf_levels : 1;
        params.detect_failure = stdev->detect_failure;
        params.detect_mode = det_mode.mode;

        {
            unsigned int i;
            ALOGV("%s: dm %d df %d ncl %d lab %d", __func__,
                  params.detect_mode, params.detect_failure,
                  params.num_confidence_levels, p_ses->capture_requested);
            for (i = 0; i < params.num_confidence_levels; i++) {
                ALOGV("%s: conf_levels[%d] = %d", __func__,
                                    i, params.conf_level[i]);
            }
        }

        status = pcm_ioctl(p_ses->pcm, SNDRV_LSM_SET_PARAMS, &params);
        if (status) {
            ALOGE("%s: ERROR. SNDRV_LSM_SET_PARAMS, status=%d", __func__, status);
            goto err_exit_1;
        }
    }

    if (p_ses->capture_requested && p_ses->vendor_uuid_info &&
        (p_ses->vendor_uuid_info->kw_transfer_mode == FTRT_TRANSFER_MODE)) {
        status = initialize_and_configure_ftrt(p_ses);
        if (status)
            goto err_exit_2;
    }

    buf_en = p_ses->capture_requested ? 1 : 0;
#if 0
    status = pcm_ioctl(p_ses->pcm, SNDRV_LSM_LAB_CONTROL, &buf_en);
    if (status) {
        ALOGE("%s: ERROR. SNDRV_LSM_LAB_CONTROL failed, status=%d", __func__, status);
        goto err_exit_2;
    }
#endif
    ALOGD("%s:[%d] Exit, status=%d", __func__, p_ses->sm_handle, status);
    return 0;

err_exit_2:
    if (p_ses->capture_requested && p_ses->vendor_uuid_info &&
        (p_ses->vendor_uuid_info->kw_transfer_mode == FTRT_TRANSFER_MODE)) {
        reset_ftrt_configuration(p_ses);
    }

err_exit_1:
    pcm_stop(p_ses->pcm);

    return status;
}

int cpe_dereg_sm_params(sound_trigger_session_t* p_ses)
{
    int status = 0;
    ALOGD("%s:[%d] Enter", __func__, p_ses->sm_handle);
    if (p_ses->pcm) {
        status = pcm_stop(p_ses->pcm);
        if (status)
            ALOGE("%s: ERROR. pcm_stop failed: %s", __func__, pcm_get_error(p_ses->pcm));
    }

    if (p_ses->capture_requested && p_ses->vendor_uuid_info &&
        (p_ses->vendor_uuid_info->kw_transfer_mode == FTRT_TRANSFER_MODE)) {
        reset_ftrt_configuration(p_ses);
    }
    return status;
}

int cpe_start(sound_trigger_session_t* p_ses)
{
    int status = 0;
    pthread_attr_t attr;

    ALOGD("%s:[%d] Enter", __func__, p_ses->sm_handle);
    if (!p_ses->pcm) {
        ALOGW("%s: pcm NULL", __func__);
        return status;
    }

    status = pcm_ioctl(p_ses->pcm, SNDRV_LSM_START);
    if (status) {
        ALOGE("%s: ERROR. SNDRV_LSM_START failed, status=%d", __func__, status);
    }

    ALOGD("%s:[%d] Exit, status=%d", __func__, p_ses->sm_handle, status);
    return status;
}

int cpe_stop(sound_trigger_session_t* p_ses)
{
    int status = 0;

    ALOGI("%s:[%d] Enter", __func__, p_ses->sm_handle);
    if (!p_ses->pcm) {
        ALOGW("%s: pcm NULL", __func__);
        return status;
    }

    status = pcm_ioctl(p_ses->pcm, SNDRV_LSM_STOP);
    if(status)
        ALOGE("%s: ERROR. SNDDRV_LSM_STOP failed, status=%d", __func__, status);

    ALOGD("%s:[%d] Exit, status=%d", __func__, p_ses->sm_handle, status);
    return status;
}

int cpe_stop_buffering(sound_trigger_session_t* p_ses)
{
    int status = 0;

    ALOGD("%s:[%d] Enter pcm %p", __func__, p_ses->sm_handle, p_ses->pcm);
    if (!p_ses->pcm) {
        ALOGW("%s: pcm NULL", __func__);
        return status;
    }

    status = pcm_ioctl(p_ses->pcm, SNDRV_LSM_STOP_LAB);
    if (status) {
        ALOGE("%s: ERROR. SNDRV_LSM_STOP_BUFFERING failed status %d", __func__, status);
    } else {
        status = pcm_ioctl(p_ses->pcm, SNDRV_PCM_IOCTL_RESET);
        if(status)
            ALOGE("%s: ERROR. SNDRV_PCM_IOCTL_RESET failed status %d", __func__, status);
    }
    if (p_ses->capture_requested && p_ses->vendor_uuid_info &&
        (p_ses->vendor_uuid_info->kw_transfer_mode == FTRT_TRANSFER_MODE)) {
        pthread_cond_signal(&p_ses->pcm_out_buf_wr_cond);
        pthread_cond_signal(&p_ses->pcm_out_buf_rd_cond);
    }

    ALOGD("%s:[%d] Exit, status=%d", __func__, p_ses->sm_handle, status);
    return status;
}

static int route_reg_sm_ape(sound_trigger_session_t *p_ses)
{
    return ape_reg_sm(p_ses);
}

static int route_reg_sm_params_ape(sound_trigger_session_t* p_ses)
{
    return ape_reg_sm_params(p_ses);
}

static int route_dereg_sm_params_ape(sound_trigger_session_t* p_ses)
{
    return ape_dereg_sm_params(p_ses);
}

static int route_dereg_sm_ape(sound_trigger_session_t* p_ses)
{
    int status = 0;

    status = ape_dereg_sm(p_ses);

    return status;
}

static int route_start_ape(sound_trigger_session_t* p_ses)
{
    reg_hal_event_session(p_ses);
    return ape_start(p_ses);
}

static int route_stop_ape(sound_trigger_session_t* p_ses)
{
    dereg_hal_event_session(p_ses);
    return ape_stop(p_ses);
}

static int route_stop_buffering_ape(sound_trigger_session_t* p_ses)
{
    int status = 0;

    if (!CHECK_STATE(p_ses->state, SES_BUFFERING)) {
        return status;
    }
    status = ape_stop_buffering(p_ses);

    CLEAR_STATE(p_ses->state, SES_BUFFERING);
    return status;
}

static int route_read_pcm_ape(sound_trigger_session_t *p_ses,
                              unsigned char *buf,
                              unsigned int bytes)
{
    int status = 0;

    if (!CHECK_STATE(p_ses->state, SES_BUFFERING)) {
        ALOGE("%s: No active buffering", __func__);
        return -EIO;
    }
     /* Read PCM frames directly from driver */
     status = pcm_read(p_ses->pcm, buf, bytes);
     if (status < 0)
         status = -errno;

    return status;
}

static void route_ftrt_capture_ape(sound_trigger_session_t *p_ses)
{
    /* FTRT is not supported for ADSP */
    return;
}

static int route_reg_sm_cpe(sound_trigger_session_t *p_ses)
{
    return cpe_reg_sm(p_ses);
}

static int route_reg_sm_params_cpe(sound_trigger_session_t* p_ses)
{
    return cpe_reg_sm_params(p_ses);
}

static int route_dereg_sm_params_cpe(sound_trigger_session_t* p_ses)
{
    return cpe_dereg_sm_params(p_ses);
}

static int route_dereg_sm_cpe(sound_trigger_session_t* p_ses)
{
    return cpe_dereg_sm(p_ses);
}

static int route_start_cpe(sound_trigger_session_t* p_ses)
{
    reg_hal_event_session(p_ses);
    return cpe_start(p_ses);
}

static int route_stop_cpe(sound_trigger_session_t* p_ses)
{
    dereg_hal_event_session(p_ses);
    return cpe_stop(p_ses);
}

static int route_stop_buffering_cpe(sound_trigger_session_t* p_ses)
{
    int status = 0;

    if (!CHECK_STATE(p_ses->state, SES_BUFFERING)) {
        return status;
    }
    status = cpe_stop_buffering(p_ses);

    CLEAR_STATE(p_ses->state, SES_BUFFERING);
    return status;
}

static int route_read_pcm_cpe(sound_trigger_session_t *p_ses,
                              unsigned char *buf,
                              unsigned int bytes)
{
    int status = 0;

    if (!CHECK_STATE(p_ses->state, SES_BUFFERING)) {
        ALOGE("%s: No active buffering", __func__);
        return -EIO;
    }

    if (p_ses->vendor_uuid_info &&
        (p_ses->vendor_uuid_info->kw_transfer_mode == FTRT_TRANSFER_MODE)) {
        /* Read from internal pcm circular buffer */
         status = read_pcm_data(p_ses, buf, bytes);
    } else {
         /* Read PCM frames directly from driver */
         status = pcm_read(p_ses->pcm, buf, bytes);
         if (status < 0)
             status = -errno;
    }
    return status;
}

static void route_ftrt_capture_cpe(sound_trigger_session_t *p_ses)
{
    process_ftrt_capture_bytes(p_ses);
}

struct sound_trigger_session* get_sound_trigger_session(
                    struct sound_trigger_device *stdev,
                    sound_model_handle_t sound_model_handle)
{
    struct sound_trigger_session *st_session = NULL;
    struct listnode *node;
    list_for_each(node, &stdev->sound_model_list) {
        st_session = node_to_item(node, struct sound_trigger_session, list_node);
        if (st_session->sm_handle == sound_model_handle)
            return st_session;
    }
    return NULL;
}

static int stop_recognition_l(struct sound_trigger_session *st_session)
{
    int status = 0;

    st_session->callback = NULL;
    CLEAR_STATE(st_session->state, SES_STARTED|SES_EVENT_RECEIVED);
    /* Audio concurrency would have already stopped the recognition */
    if (stdev->audio_concurrency_active)  {
        ALOGD("%s: audio concurrency active", __func__);
        st_session->fptrs->dereg_sm_params(st_session);
        if (st_session->conf_levels) {
            free(st_session->conf_levels);
            st_session->conf_levels = NULL;
        }
        if (st_session->rc_config) {
            free(st_session->rc_config);
            st_session->rc_config = NULL;
        }
        return status;
    }

    st_session->fptrs->stop_buffering(st_session);

    status = st_session->fptrs->stop(st_session);

    st_session->fptrs->dereg_sm_params(st_session);

    if (st_session->conf_levels) {
        free(st_session->conf_levels);
        st_session->conf_levels = NULL;
    }
    if (st_session->rc_config) {
        free(st_session->rc_config);
        st_session->rc_config = NULL;
    }
    return status;
}

int stdev_get_properties(const struct sound_trigger_device *stdev,
                         struct sound_trigger_properties *properties,
                         st_property_type_t property_type, st_exec_mode_t mode)
{
    ALOGV("%s", __func__);

    if (stdev == NULL) {
        ALOGE("%s: NULL sound trigger device", __func__);
        return -EINVAL;
    }
    if (properties == NULL) {
        ALOGE("%s: NULL properties", __func__);
        return -EINVAL;
    }

    switch(property_type) {
    case ST_PROPERTY_GLOBAL:
        if (mode == ST_DEVICE_EXEC_MODE_APE) {
            hw_properties.max_sound_models = stdev->max_ape_sessions;
            hw_properties.max_key_phrases = stdev->avail_ape_phrases;
            hw_properties.max_users = stdev->avail_ape_users;
            hw_properties.max_buffer_ms = SOUND_TRIGGER_APE_BUFFER_DURATION_MS;
            hw_properties.sw_mad = true;
        }
        else {
            hw_properties.max_sound_models = stdev->max_cpe_sessions;
            hw_properties.max_key_phrases = stdev->avail_cpe_phrases ;
            hw_properties.max_users = stdev->avail_cpe_users ;
            hw_properties.max_buffer_ms = SOUND_TRIGGER_CPE_BUFFER_DURATION_MS;
            hw_properties.sw_mad = false;
        }
        break;
    default:
        ALOGW("%s: Unknown property type %d", __func__, property_type);
        return -EINVAL;
    }
    ALOGVV("%s recognition_modes=%d, capture_transition=%d", __func__,
          hw_properties.recognition_modes, hw_properties.capture_transition);
    memcpy(properties, &hw_properties, sizeof(struct sound_trigger_properties));
    return 0;
}

int stdev_set_properties(struct sound_trigger_device *stdev,
                         struct sound_trigger_properties *properties,
                         st_property_type_t property_type, st_exec_mode_t mode)
{
    switch(property_type) {
    case ST_PROPERTY_GLOBAL:
        if (mode == ST_DEVICE_EXEC_MODE_APE) {
            stdev->run_on_ape = true;
            stdev->sw_mad = properties->sw_mad;
        }
        break;
    case ST_PROPERTY_LEC_SUPPORT:
        if (mode == ST_DEVICE_EXEC_MODE_APE)
            stdev->support_lec = properties->support_lec;
        break;
    default:
        ALOGW("%s: Unknown property type %d", __func__, property_type);
        return -EINVAL;
    }
    return 0;
}

static st_exec_mode_t get_session_exec_mode
(
   sound_trigger_session_t *p_ses,
   unsigned int num_phrases,
   unsigned int num_users
)
{
    unsigned int num_sessions = 0;
    st_exec_mode_t exec_mode = ST_DEVICE_EXEC_MODE_NONE;
    struct st_vendor_info *v_info = p_ses->vendor_uuid_info;

    num_sessions = get_num_sessions_in_state(SES_CREATED);

    ALOGD("%s: Enter - KW=%u UP=%u AKW=%u AKWUP=%u CKW=%u CKWUP=%u pm=%d ns=%d",
          __func__, num_phrases, num_users,
          stdev->avail_ape_phrases, stdev->avail_ape_users,
          stdev->avail_cpe_phrases , stdev->avail_cpe_users,
          stdev->run_on_ape, num_sessions);

    if (!stdev->run_on_ape) {
        /* Platform configured for CPE */
        if (v_info &&
            (num_sessions < stdev->max_cpe_sessions) &&
            (num_phrases <= v_info->avail_cpe_phrases) &&
            (num_users <= v_info->avail_cpe_users)) {
            exec_mode = ST_DEVICE_EXEC_MODE_CPE;
        }
        else if ((num_sessions < stdev->max_cpe_sessions) &&
            (num_phrases <= stdev->avail_cpe_phrases) &&
            (num_users <= stdev->avail_cpe_users)) {
            exec_mode = ST_DEVICE_EXEC_MODE_CPE;
        }
    }
    else {
        if (v_info &&
            (num_sessions < stdev->max_ape_sessions) &&
            (num_phrases <= v_info->avail_ape_phrases) &&
            (num_users <= v_info->avail_ape_users)) {
            exec_mode = ST_DEVICE_EXEC_MODE_APE;
        }
        else if ((num_sessions < stdev->max_ape_sessions) &&
            (num_phrases <= stdev->avail_ape_phrases ) &&
            (num_users <= stdev->avail_ape_users )) {
            exec_mode = ST_DEVICE_EXEC_MODE_APE;
        }
    }

    ALOGV("%s: Exit  exec_mode=%d", __func__, exec_mode);
    return exec_mode;
}

int stdev_load_sound_model(struct sound_trigger_device *stdev,
                                  struct sound_trigger_sound_model *sound_model,
                                  sound_model_callback_t callback,
                                  void *cookie,
                                  sound_model_handle_t *handle)
{
    int status = 0;
    unsigned int i = 0, j = 0;
    unsigned int total_num_users = 0;
    st_exec_mode_t exec_mode;
    struct sound_trigger_session *st_session = NULL;
    struct sound_trigger_phrase_sound_model *phrase_sm =
            (struct sound_trigger_phrase_sound_model*)sound_model;

    ALOGD("%s", __func__);

    pthread_mutex_lock(&stdev->lock);
    /* Wait on SSR if
     * 1. SSR_CPE flag is set and platform is configured for CPE.
     * OR
     * 2. SSR_APE flag is set.
     *
     * SSR handler is suppossed to add only those flags which affects
     * configured platform, but when both APE and CPE are applicable
     * i.e. transitions enabled(run_on_ape=true), no need to wait on CPE.
     */
    if ((!stdev->run_on_ape && CHECK_STATE(stdev->ssr_state, ST_DEVICE_SSR_CPE))
        || CHECK_STATE(stdev->ssr_state, ST_DEVICE_SSR_APE)) {
        /* Wait till SSR completes */
        ALOGV("%s: waiting on SSR completion", __func__);
        pthread_cond_wait(&stdev->cond, &stdev->lock);
    }

    if (handle == NULL || sound_model == NULL) {
        status = -EINVAL;
        goto exit;
    }
    if (!platform_stdev_is_session_allowed(stdev->platform,
                         get_num_sessions_in_state(SES_CREATED), false)) {
        ALOGW("%s: session prevented by concurrency", __func__);
        status = -ENODEV;
        goto exit;
    }

    /* Current version supports only key phrase detection */
    if ((phrase_sm->common.data_size == 0) ||
        (phrase_sm->common.data_offset < sizeof(*phrase_sm)) ||
        (phrase_sm->common.type != SOUND_MODEL_TYPE_KEYPHRASE) ||
        (phrase_sm->num_phrases == 0)) {
        ALOGE("%s: Invalid sound model params data size=%d, data offset=%d, "
              "type=%d phrases=%d", __func__, phrase_sm->common.data_size,
              phrase_sm->common.data_offset, phrase_sm->common.type,
              phrase_sm->num_phrases);
        status = -EINVAL;
        goto exit;
    }

    st_session = calloc(1, sizeof(struct sound_trigger_session));
    if (!st_session) {
        status = -ENOMEM;
        goto exit;
    }

    /* CPE takes time to become online, so parse for the pcm devices
       here instead during boot time */
    if (!CHECK_BIT(stdev->hw_type, ST_DEVICE_HW_APE|ST_DEVICE_HW_CPE)) {
        status = stdev_get_hw_type();
        if (status)
            goto exit;
    }

    /* Use cases:
       1. No vendor uuids defined in platform file                                                    .
            - Use default non topology based SVA solution.
       2. vendor uuids defined in platform file and vendor uuid doesn't match                         .
            - Return error.
       3. vendor uuids defined in platform file and vendor uuid matches                               .
            - Use topology based solution.
     */

    /* TODO: Google search app doesn't pass the vendor_uuid yet.
       Use SVA uuid matching with the value in platform file */
    phrase_sm->common.vendor_uuid = hw_properties.uuid;

    bool is_qc_uuid = false;
    st_session->vendor_uuid_info = platform_stdev_get_vendor_info(
                                       stdev->platform,
                                       &phrase_sm->common.vendor_uuid,
                                       &is_qc_uuid);

    if(!st_session->vendor_uuid_info && !is_qc_uuid) {
        ALOGE("%s: no matching vendor uuid found", __func__);
        status = -EINVAL;
        goto exit;
    }

    for (i = 0; i < phrase_sm->num_phrases; i++) {
        for (j = 0; j < phrase_sm->phrases[i].num_users; j++) {
            total_num_users++;
        }
    }

    exec_mode = get_session_exec_mode(st_session, phrase_sm->num_phrases, total_num_users);
    if (exec_mode == ST_DEVICE_EXEC_MODE_NONE) {
        status = -EINVAL;
        goto exit;
    }

    st_session->device = (void *)stdev;
    st_session->exec_mode = exec_mode;
    st_session->num_users = total_num_users;

    if (ST_DEVICE_EXEC_MODE_CPE == exec_mode) {
        ALOGD("%s: CPE mode", __func__);
        st_session->fptrs = &cpe_fptrs;
    } else {
        ALOGD("%s: APE mode", __func__);
        st_session->fptrs = &ape_fptrs;
    }
    transit_reassign_fptrs(st_session);

    /* Store the sound model information for handling SSR
       and interaction with smlib */
    st_session->sm_data =
        calloc(1, sizeof(*phrase_sm) + phrase_sm->common.data_size);
    if (!st_session->sm_data) {
        status = -ENOMEM;
        goto exit;
    }
    memcpy(st_session->sm_data, (char *)phrase_sm, sizeof(*phrase_sm));
    memcpy((char *)st_session->sm_data + st_session->sm_data->common.data_offset,
           (char *)phrase_sm + phrase_sm->common.data_offset,
           phrase_sm->common.data_size);

    #ifdef ST_DEBUG_DUMP
    {
        FILE *fptr = NULL;
        int ret_bytes = 0;
        fptr = fopen("/data/misc/audio/sound_model_data.bin", "wb");
        if (!fptr) {
            ALOGE("%s: FILE OPEN ERROR soundmodel data %s", __func__, strerror(errno));
        }
        else {
            ret_bytes = fwrite((char *)phrase_sm + phrase_sm->common.data_offset,
                               1, phrase_sm->common.data_size, fptr);
            if (ret_bytes != (int)phrase_sm->common.data_size) {
                ALOGE("%s: fwrite %d < %d", __func__, ret_bytes,
                      phrase_sm->common.data_size);
            } else {
                ALOGV("%s: dumped sound model data size %d, ret_size=%d", __func__,
                      phrase_sm->common.data_size, ret_bytes);
            }
            fclose(fptr);
        }
    }
    #endif

    ALOGD("%s: sm magic number 0x%x rm %d", __func__,
          ((int *)((char *)st_session->sm_data + st_session->sm_data->common.data_offset))[0],
          phrase_sm->phrases[0].recognition_mode);

    /* TODO: SVA doesn't support per keyword recognition mode.
       So use the first phrase recognition mode. App is supposed
       to set the proper recognition mode in the first phrase */
    st_session->recognition_mode = phrase_sm->phrases[0].recognition_mode;
    pthread_mutex_init(&st_session->lock, (const pthread_mutexattr_t *) NULL);

    st_session->sm_handle = stdev->session_id++;
    *handle = st_session->sm_handle;

    /* No need for session lock as session is not pushed to list and
       the ssr/concurrencies are guarded with dev lock */
    status = st_session->fptrs->reg_sm(st_session);
    if (status)
        goto exit;

    if (ST_DEVICE_EXEC_MODE_CPE == exec_mode) {
        if (st_session->vendor_uuid_info) {
            st_session->vendor_uuid_info->avail_cpe_phrases  -= phrase_sm->num_phrases;
            st_session->vendor_uuid_info->avail_cpe_users  -= st_session->num_users;
        } else {
            stdev->avail_cpe_phrases  -= phrase_sm->num_phrases;
            stdev->avail_cpe_users  -= st_session->num_users;
        }
    } else {
        if (st_session->vendor_uuid_info) {
            st_session->vendor_uuid_info->avail_ape_phrases  -= phrase_sm->num_phrases;
            st_session->vendor_uuid_info->avail_ape_users  -= st_session->num_users;
        } else {
            stdev->avail_ape_phrases  -= phrase_sm->num_phrases;
            stdev->avail_ape_users  -= st_session->num_users;
        }
    }

    /* Add the session to the list of registered sound models list */
    list_add_tail(&stdev->sound_model_list, &st_session->list_node);
    pthread_mutex_unlock(&stdev->lock);

    ALOGD("%s: success, sound_model_handle %d", __func__, st_session->sm_handle);
    return 0;

exit:
    if (st_session != NULL) {
        if (st_session->sm_data) {
            free(st_session->sm_data);
            pthread_mutex_destroy(&st_session->lock);
        }
        free(st_session);
        *handle = -1;
    }
    pthread_mutex_unlock(&stdev->lock);
    return status;
}

int stdev_unload_sound_model(struct sound_trigger_device *stdev,
                                    sound_model_handle_t handle)
{
    struct sound_trigger_session *st_session = NULL;
    int status = 0;

    ALOGD("%s: Enter sound model %d", __func__, handle);

    pthread_mutex_lock(&stdev->lock);
    if ((!stdev->run_on_ape && CHECK_STATE(stdev->ssr_state, ST_DEVICE_SSR_CPE))
        || CHECK_STATE(stdev->ssr_state, ST_DEVICE_SSR_APE)) {
        /* Wait till SSR completes */
        ALOGV("%s: waiting on SSR completion", __func__);
        pthread_cond_wait(&stdev->cond, &stdev->lock);
    }

    st_session = get_sound_trigger_session(stdev, handle);
    if (!st_session || !st_session->fptrs) {
        ALOGE("%s Could not find sound model %d", __func__, handle);
        status = -EINVAL;
        goto exit;
    }

    pthread_mutex_lock(&st_session->lock);
    if (st_session->callback)
        status = stop_recognition_l(st_session);

    SET_STATE(st_session->state, SES_INVALIDATED);
    status = st_session->fptrs->dereg_sm(st_session);

    list_remove(&st_session->list_node);

    if (st_session->exec_mode == ST_DEVICE_EXEC_MODE_CPE) {
        if (st_session->vendor_uuid_info) {
            st_session->vendor_uuid_info->avail_cpe_phrases  += st_session->sm_data->num_phrases;
            st_session->vendor_uuid_info->avail_cpe_users  += st_session->num_users;
        } else {
            stdev->avail_cpe_phrases  += st_session->sm_data->num_phrases;
            stdev->avail_cpe_users  += st_session->num_users;
        }
    } else {
        if (st_session->vendor_uuid_info) {
            st_session->vendor_uuid_info->avail_ape_phrases  += st_session->sm_data->num_phrases;
            st_session->vendor_uuid_info->avail_ape_users  += st_session->num_users;
        } else {
            stdev->avail_ape_phrases  += st_session->sm_data->num_phrases;
            stdev->avail_ape_users  += st_session->num_users;
        }
    }
    pthread_mutex_unlock(&st_session->lock);

    if(!get_num_sessions_in_state(SES_CREATED))
        stdev->exec_mode = ST_DEVICE_EXEC_MODE_NONE;

    if (transit_ses_loading(st_session))
        goto exit;

    pthread_mutex_destroy(&st_session->lock);
    free(st_session->sm_data);
    free(st_session);

exit:
    pthread_mutex_unlock(&stdev->lock);
    ALOGD("%s: Exit status %d", __func__, status);
    return status;
}

int stdev_start_recognition
(
   struct sound_trigger_device *stdev,
   sound_model_handle_t sound_model_handle,
   const struct sound_trigger_recognition_config *config,
   recognition_callback_t callback,
   void *cookie
)
{
    struct sound_trigger_session *st_session = NULL;
    int status = 0;
    unsigned int total_num_users = 0;

    ALOGD("%s:[%d] Enter", __func__, sound_model_handle);

    pthread_mutex_lock(&stdev->lock);
    if ((!stdev->run_on_ape && CHECK_STATE(stdev->ssr_state, ST_DEVICE_SSR_CPE))
        || CHECK_STATE(stdev->ssr_state, ST_DEVICE_SSR_APE)) {
        /* Wait till SSR completes */
        ALOGV("%s: waiting on SSR completion", __func__);
        pthread_cond_wait(&stdev->cond, &stdev->lock);
    }

    if (!callback || !config) {
        ALOGE("%s: ERROR. NULL params", __func__);
        status = -EINVAL;
        goto exit;
    }

    st_session = get_sound_trigger_session(stdev, sound_model_handle);
    if (!st_session) {
        ALOGE("%s: ERROR. Could not find session for sound model %d", __func__, sound_model_handle);
        status = -EINVAL;
        goto exit;
    }
	    ALOGE("st_session %p", st_session);

    pthread_mutex_lock(&st_session->lock);
    /* Client might have called this function after event is detected.
       As we haven't stopped recognition after detection, exit from here */
       ALOGE("stsesion state %d",st_session->state);
    if (CHECK_STATE(st_session->state, SES_STARTED)) {
	    ALOGE("start after event detection, exit from here");
        CLEAR_STATE(st_session->state, SES_EVENT_RECEIVED);
        goto ses_exit;
    }

    if (!st_session->pcm || !st_session->fptrs) {
        ALOGE("%s: ERROR. Could not find sound model %d", __func__, sound_model_handle);
        status = -EINVAL;
        goto ses_exit;
    }

    if(st_session->vendor_uuid_info) {
        /* Can be QC SVA or ISV vendor. Get from corresponding smlib defined
           in platform file. if soundmodel library for ISV vendor uuid is
           mentioned, use it. If not ignore and continue sending the opaque
           data from HAL to DSP */
        if(st_session->vendor_uuid_info->smlib_handle) {
            status = st_session->vendor_uuid_info->
                                 generate_st_recognition_config_payload(
                                            st_session->sm_data, config,
                                            &st_session->conf_levels,
                                            &st_session->num_conf_levels);

            if (status || !st_session->conf_levels) {
                ALOGE("%s: smlib get confidence level failed, status %d", __func__,
                      status);
                if (st_session->conf_levels) {
                    free(st_session->conf_levels);
                    st_session->conf_levels = NULL;
                }
                goto ses_exit;
            }
        } else {
            ALOGD("%s: No smlib for uuid, sending opaque data", __func__);
        }
    } else {
        /* non topology solution: get from default SVA smlib */
        if (stdev->smlib_handle) {
            status = stdev->generate_st_recognition_config_payload(
                                            st_session->sm_data, config,
                                            &st_session->conf_levels,
                                            &st_session->num_conf_levels);

            if (status || !st_session->conf_levels) {
                ALOGE("%s: Smlib get confidence level failed, status %d", __func__,
                      status);
                if (st_session->conf_levels) {
                    free(st_session->conf_levels);
                    st_session->conf_levels = NULL;
                }
                goto ses_exit;
            }
        }
    }

    /* Store the recogntion configuration for sending opaque data
       and for SSR, passing to sounmodel wrapper library */
    st_session->rc_config = calloc(1, sizeof(*config) + config->data_size);
    if (!st_session->rc_config) {
        status = -ENOMEM;
        goto exit;
    }

    memcpy(st_session->rc_config, (char *)config, sizeof(*config));
    memcpy((char *)st_session->rc_config + st_session->rc_config->data_offset,
           (char *)config + config->data_offset,
           config->data_size);

    ALOGVV("%s: num_phrases=%d, id=%d", __func__, st_session->rc_config->num_phrases,
           st_session->rc_config->phrases[0].id);

    st_session->callback = callback;
    st_session->cookie = cookie;
    /* capture_handle will be shared with AHAL for reading LAB data */
    st_session->capture_handle = config->capture_handle;
    st_session->capture_requested = config->capture_requested;

    /* If audio capture is active, start recogntion later after capture is inactive */
    if (!platform_stdev_is_session_allowed(stdev->platform,
                     get_num_sessions_in_state(SES_CREATED), true)) {
        ALOGD("%s: audio concurrency active", __func__);
        SET_STATE(st_session->state, SES_STARTED);
        if (!stdev->sw_mad)
            status = st_session->fptrs->reg_sm_params(st_session);
        goto ses_exit;
    }

    status = st_session->fptrs->reg_sm_params(st_session);
    if(status)
        goto ses_exit_1;

    status = st_session->fptrs->start(st_session);
    if (status)
        goto ses_exit_1;

    SET_STATE(st_session->state, SES_STARTED);
    pthread_mutex_unlock(&st_session->lock);
    pthread_mutex_unlock(&stdev->lock);

    ALOGD("%s:[%d] Exit", __func__, sound_model_handle);
    return 0;

ses_exit_1:
ses_exit:
    pthread_mutex_unlock(&st_session->lock);

exit:
    pthread_mutex_unlock(&stdev->lock);
    ALOGD("%s:[%d] Exit status %d", __func__, sound_model_handle, status);
    return status;
}

int stdev_stop_recognition(struct sound_trigger_device *stdev,
                                 sound_model_handle_t sound_model_handle)
{
    struct sound_trigger_session *st_session = NULL;
    int status = 0;

    ALOGD("%s:[%d] Enter", __func__, sound_model_handle);

    pthread_mutex_lock(&stdev->lock);
    if ((!stdev->run_on_ape && CHECK_STATE(stdev->ssr_state, ST_DEVICE_SSR_CPE))
        || CHECK_STATE(stdev->ssr_state, ST_DEVICE_SSR_APE)) {
        /* Wait till SSR completes */
        ALOGV("%s: waiting on SSR completion", __func__);
        pthread_cond_wait(&stdev->cond, &stdev->lock);
    }
    st_session = get_sound_trigger_session(stdev, sound_model_handle);
    if (st_session == NULL) {
        ALOGE("%s: Could not find sound model %d", __func__, sound_model_handle);
        status = -EINVAL;
        pthread_mutex_unlock(&stdev->lock);
        return status;
    }

    pthread_mutex_lock(&st_session->lock);
    status = stop_recognition_l(st_session);
    pthread_mutex_unlock(&st_session->lock);

    pthread_mutex_unlock(&stdev->lock);
    ALOGD("%s:[%d] Exit status %d", __func__, sound_model_handle, status);
    return status;
}

int stdev_close(struct sound_trigger_device *stdev)
{
    ALOGD("%s: count=%d", __func__, stdev_ref_cnt);

    pthread_mutex_lock(&stdev_init_lock);
    if (!stdev || (--stdev_ref_cnt != 0)) {
        goto exit;
    }

    pthread_mutex_lock(&stdev->lock);
    platform_stdev_deinit(stdev->platform);
    free(stdev->cpe_pcm_use_cases);
    free(stdev->ape_pcm_use_cases);
    free(stdev->dev_ref_cnt);
    transit_stdev_deinit(stdev);
    pthread_mutex_unlock(&stdev->lock);

    pthread_mutex_destroy(&stdev->lock);
    free(stdev);
    stdev = NULL;

exit:
    pthread_mutex_unlock(&stdev_init_lock);
    ALOGD("%s: Exit device=%p cnt=%d ", __func__, stdev, stdev_ref_cnt);
    return 0;
}

int stdev_open(struct sound_trigger_device** device)
{
    int status = 0;
    char st_extn_lib[100];

    ALOGD("%s: Enter", __func__);
    pthread_mutex_lock(&stdev_init_lock);
    if (stdev_ref_cnt != 0) {
        *device = stdev;
        stdev_ref_cnt++;
        ALOGD("%s: returning existing stdev instance, exit", __func__);
        pthread_mutex_unlock(&stdev_init_lock);
        return status;
    }

    stdev = calloc(1, sizeof(struct sound_trigger_device));
    if (!stdev) {
        ALOGE("%s: ERROR. stdev alloc failed", __func__);
        status = -ENOMEM;
        goto exit;
    }

    stdev->platform = platform_stdev_init(stdev);
    if (!stdev->platform) {
        ALOGE("%s: ERROR. platform init failed", __func__);
        status = -ENODEV;
        goto exit;
    }

    snprintf(st_extn_lib, sizeof(st_extn_lib),
             "libsoundtrigger_extn.so.1");
    stdev->st_extn_handle = dlopen(st_extn_lib, RTLD_NOW);
    if (!stdev->st_extn_handle){
        ALOGE("%s: ERROR. dlopen failed for %s", __func__, st_extn_lib);
        status = -ENODEV;
        goto exit_1;
    }

    stdev->st_extn_cb = (st_extn_call_back_t)dlsym(stdev->st_extn_handle,
                                              "sound_trigger_extn_call_back");
    if (!stdev->st_extn_cb){
        ALOGE("%s: ERROR. dlsym error %s for sound_trigger_extn_call_back",
                 __func__, dlerror());
        status = -ENODEV;
    }

    stdev->ape_pcm_use_cases =
        calloc(stdev->max_ape_sessions, sizeof(struct use_case_info));

    if (!stdev->ape_pcm_use_cases) {
        ALOGE("%s: ERROR. Mem alloc failed for ape use cases", __func__);
        status = -ENODEV;
        goto exit_1;
    }

    stdev->cpe_pcm_use_cases =
        calloc(stdev->max_cpe_sessions, sizeof(struct use_case_info));

    if (!stdev->cpe_pcm_use_cases) {
        ALOGE("%s: ERROR. Mem alloc failed for cpe use cases", __func__);
        status = -ENODEV;
        goto exit_1;
    }

    stdev->session_id = 1;
    stdev->exec_mode = ST_DEVICE_EXEC_MODE_NONE;
    stdev->capture_device = AUDIO_DEVICE_NONE;
    stdev->available_devices = AUDIO_DEVICE_IN_BUILTIN_MIC;

    pthread_mutex_init(&stdev->lock, (const pthread_mutexattr_t *) NULL);
    list_init(&stdev->sound_model_list);

    transit_stdev_init(stdev);

    *device = stdev;
    stdev_ref_cnt++;
    pthread_mutex_unlock(&stdev_init_lock);

    return 0;

exit_1:
    if (stdev->cpe_pcm_use_cases)
        free(stdev->cpe_pcm_use_cases);

    if (stdev->ape_pcm_use_cases)
        free(stdev->ape_pcm_use_cases);

    if(stdev->platform)
       platform_stdev_deinit(stdev->platform);

exit:

    if(stdev)
        free(stdev);
    stdev = NULL;
    *device = NULL;

    pthread_mutex_unlock(&stdev_init_lock);
    ALOGD("%s: Exit status %d", __func__, status);
    return status;
}

/* Audio hal calls this callback for notifying Subsystem restart,
   lab stop and concurrency events */
int sound_trigger_hw_call_back(audio_event_type_t event,
                               audio_event_info_t* config)
{
    struct listnode *p_ses_node;
    sound_trigger_session_t *p_ses;
    int ret = 0;

    if (!stdev)
        return -ENODEV;

    switch (event) {
    case AUDIO_EVENT_CAPTURE_DEVICE_INACTIVE:
    case AUDIO_EVENT_CAPTURE_DEVICE_ACTIVE:
    case AUDIO_EVENT_PLAYBACK_STREAM_INACTIVE:
    case AUDIO_EVENT_PLAYBACK_STREAM_ACTIVE:
        handle_audio_concurrency(event);
        break;
    case AUDIO_EVENT_STOP_LAB:
        if(!config || !config->u.ses_info.pcm) {
            ALOGE("%s: NULL params for stop lab", __func__);
            break;
        }
        pthread_mutex_lock(&stdev->lock);
        list_for_each(p_ses_node, &stdev->sound_model_list) {
            p_ses = node_to_item(p_ses_node, sound_trigger_session_t, list_node);
            if (p_ses->pcm == config->u.ses_info.pcm ||
                transit_ses_buffering(p_ses, config->u.ses_info.pcm)) {
                pthread_mutex_lock(&p_ses->lock);
                p_ses->fptrs->stop_buffering(p_ses);
                pthread_mutex_unlock(&p_ses->lock);
                break;
            }
        }
        pthread_mutex_unlock(&stdev->lock);
        break;
    case AUDIO_EVENT_SSR:
        if (!config) {
            ALOGE("%s: NULL config for SSR", __func__);
            break;
        }

        if (config->u.status == SND_CARD_STATUS_OFFLINE ||
            config->u.status == CPE_STATUS_OFFLINE)
            handle_ssr_offline(config->u.status);

        if (config->u.status == SND_CARD_STATUS_ONLINE ||
            config->u.status == CPE_STATUS_ONLINE)
            handle_ssr_online(config->u.status);

        break;
    case AUDIO_EVENT_NUM_ST_SESSIONS:
        if (!config) {
            ALOGE("%s: NULL config for AUDIO_EVENT_NUM_ST_SESSIONS", __func__);
            break;
        }
        pthread_mutex_lock(&stdev->lock);
        ALOGV("%s: num sessions configured %d", __func__, config->u.value);
        stdev->num_sessions_configured = config->u.value;
        pthread_mutex_unlock(&stdev->lock);
        break;

    case AUDIO_EVENT_READ_SAMPLES:
        if(!config) {
            ALOGE("%s: Invalid config", __func__);
            ret = -EINVAL;
            break;
        }
        ret = read_capture_bytes(config->u.aud_info.ses_info->pcm,
                                 config->u.aud_info.buf,
                                 config->u.aud_info.num_bytes);

        break;

    default:
        ALOGW("%s: Unknown event %d", __func__, event);
        break;
    }

    return ret;
}
