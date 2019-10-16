/* sound_trigger_hw.h
 *
 * This library contains the API to load sound models with
 * DSP and start/stop detection of associated key phrases.
 *
 * Copyright (c) 2013-2017 Qualcomm Technologies, Inc.
 * All Rights Reserved.
 * Confidential and Proprietary - Qualcomm Technologies, Inc.
 *
 */

#ifndef SOUND_TRIGGER_HW_H
#define SOUND_TRIGGER_HW_H

#include <cutils/list.h>
#include <pthread.h>
#include <errno.h>
#include <hardware/sound_trigger.h>
#include <tinyalsa/asoundlib.h>
#include <audio_route/audio_route.h>

#include <sound/lsm_params.h>
#include "sound_trigger_prop_intf.h"

#define PCM_DEVICE_FILE "/proc/asound/pcm"

#define SOUND_TRIGGER_SAMPLING_RATE (16000)
#define SOUND_TRIGGER_CHANNEL_MODE  (1)

#define SOUND_TRIGGER_MAX_EVNT_PAYLOAD_SIZE (256)

#define SOUND_TRIGGER_PCM_MAX_RETRY (10)
#define SOUND_TRIGGER_PCM_SLEEP_WAIT (1000)

#define SOUND_TRIGGER_CPE_BUFFER_DURATION_MS (1000)
#define SOUND_TRIGGER_CPE_PERIOD_COUNT (8)
#define SOUND_TRIGGER_CPE_PERIOD_SIZE \
    ((((SOUND_TRIGGER_SAMPLING_RATE * SOUND_TRIGGER_CPE_BUFFER_DURATION_MS) \
       /(SOUND_TRIGGER_CPE_PERIOD_COUNT * 1000)) + 0x1f) & ~0x1f)

#define SOUND_TRIGGER_APE_BUFFER_DURATION_MS (1000)
#define SOUND_TRIGGER_APE_PERIOD_COUNT (8)
#define SOUND_TRIGGER_APE_PERIOD_SIZE \
    ((((SOUND_TRIGGER_SAMPLING_RATE * SOUND_TRIGGER_APE_BUFFER_DURATION_MS) \
       /(SOUND_TRIGGER_APE_PERIOD_COUNT * 1000)) + 0x1f) & ~0x1f)

#define SET_BIT(a, b) (a |= b)
#define CLEAR_BIT(a, b) (a &= ~(b))
#define CHECK_BIT(a, b) ((a) & (b))

#define SET_STATE(a, b) SET_BIT(a, b)
#define CLEAR_STATE(a, b) CLEAR_BIT(a, b)
#define CHECK_STATE(a, b) CHECK_BIT(a, b)

#define USECASE_STRING_SIZE (50)
#define SOUND_TRIGGER_BIT_WIDTH  (16)

typedef enum {
    ST_DEVICE_HW_NONE,
    ST_DEVICE_HW_APE,
    ST_DEVICE_HW_CPE
}st_hw_type_t;

typedef enum {
    ST_DEVICE_EXEC_MODE_NONE = -1,
    ST_DEVICE_EXEC_MODE_APE,
    ST_DEVICE_EXEC_MODE_CPE,
    ST_DEVICE_EXEC_MODE_MAX
}st_exec_mode_t;

typedef enum {
    ST_DEVICE_SSR_APE = 0x1,
    ST_DEVICE_SSR_CPE = 0x2
}st_ssr_state_t;

/* guideline for locks needed to guard ses states:
 * 1. SES_STARTED  must be under stdev lock, ses lock optional.
 * 2. SES_BUFFERING and SES_TRANSIT_STARTED must have transit lock
 *    since BUFFERING and TRANSIT_START can be updated from other ses.
 * 3. All other states must have ses lock.
 */
enum sound_trigger_states {
    SES_CREATED = 0x00,
    SES_STARTED = 0x01,
    SES_EVENT_RECEIVED = 0x02,
    SES_BUFFERING = 0x04,
    SES_INVALIDATED = 0x08,
    SES_TRANSIT_LOADING = 0x10,
    SES_TRANSIT_LOADED = 0x20,
    SES_TRANSIT_STARTED = 0x40,
    SES_TRANSIT_SUSPENDED = 0x80,
    SES_TRANSIT_INVALIDATED = 0x100
};

typedef enum {
    ST_PROFILE_TYPE_NONE, /* Connect LSM to AFE directly */
    ST_PROFILE_TYPE_DEFAULT, /* Connect LSM to ADM with default config */
    ST_PROFILE_TYPE_UNPROCESSED, /* Connect LSM to ADM with Unprocessed config */
    ST_PROFILE_TYPE_LEC, /* Connect LSM to ADM with LEC config */
    ST_PROFILE_TYPE_FLUENCE /* Connect LSM to ADM with Fluence config */
}st_profile_type_t;

#ifdef ST_TRANSITION_ENABLED
enum sound_trigger_transition_cmds {
    ST_TRANSIT_CMD_NONE,
    ST_TRANSIT_CMD_LOAD_AND_START,
    ST_TRANSIT_CMD_EXIT
};

struct sound_trigger_transition_cmd {
    enum sound_trigger_transition_cmds cmd;
    sound_model_handle_t sm_handle;
};
#endif

struct use_case_info {
    bool active;
    int pcm_id;
    char use_case[USECASE_STRING_SIZE];
};

struct sound_trigger_session {
    struct listnode list_node;
    struct sound_trigger_hw_device *device;
    struct sound_trigger_session_fptrs *fptrs;
    st_exec_mode_t exec_mode;

    sound_model_handle_t sm_handle;
    sound_trigger_sound_model_type_t sm_type;

    struct sound_trigger_phrase_sound_model *sm_data;
    struct sound_trigger_recognition_config *rc_config;

    recognition_callback_t callback;
    void *cookie;
    pthread_t callback_thread;
    bool exit_callback_thread;
    audio_io_handle_t capture_handle;
    bool capture_requested;
    unsigned int recognition_mode;

    struct pcm *pcm;
    struct pcm_config  config;
    int pcm_id;

    enum sound_trigger_states state;
    unsigned int use_case_idx;
    unsigned int num_phrases;
    unsigned int num_users;
    unsigned int num_conf_levels;
    unsigned char *conf_levels;

    pthread_mutex_t lock;
    struct sound_trigger_event_info event_info;
    struct st_vendor_info *vendor_uuid_info;
    bool use_topology_intf;

    struct sound_trigger_session *transit_ses;
    st_profile_type_t profile_type;
    int st_device;
    char *st_device_name;
};
typedef struct sound_trigger_session sound_trigger_session_t;

/* soundmodel library wrapper functions */
typedef int (*smlib_generate_sound_trigger_phrase_recognition_event_t)
(
    const struct sound_trigger_phrase_sound_model *sm,
    const struct sound_trigger_recognition_config *config,
    const void *payload,
    unsigned int payload_size,
    struct sound_trigger_phrase_recognition_event **r_event
);

typedef int (*smlib_generate_sound_trigger_recognition_config_payload_t)
(
   const struct sound_trigger_phrase_sound_model *sm,
   const struct sound_trigger_recognition_config *config,
   unsigned char **out_payload,
   unsigned int *out_payload_size
);

/* Function pointers to routing layers */
typedef int (*sound_trigger_reg_sm_t)(sound_trigger_session_t *);
typedef int (*sound_trigger_reg_sm_params_t)(sound_trigger_session_t *);
typedef int (*sound_trigger_dereg_sm_t)(sound_trigger_session_t *);
typedef int (*sound_trigger_dereg_sm_params_t)(sound_trigger_session_t *);
typedef int (*sound_trigger_start_t)(sound_trigger_session_t *);
typedef int (*sound_trigger_stop_t)(sound_trigger_session_t *);
typedef int (*sound_trigger_stop_buffering_t)(sound_trigger_session_t *);
typedef int (*sound_trigger_set_device_t)(sound_trigger_session_t *,
                                          bool, audio_devices_t);
typedef int (*sound_trigger_pre_dev_switch_t)(sound_trigger_session_t *);
typedef int (*sound_trigger_post_dev_switch_t)(sound_trigger_session_t *);
typedef int (*sound_trigger_set_port_t)(sound_trigger_session_t *);

struct sound_trigger_session_fptrs {
    sound_trigger_reg_sm_t reg_sm;
    sound_trigger_reg_sm_params_t reg_sm_params;
    sound_trigger_dereg_sm_t dereg_sm;
    sound_trigger_dereg_sm_params_t dereg_sm_params;
    sound_trigger_start_t start;
    sound_trigger_stop_t stop;
    sound_trigger_stop_buffering_t stop_buffering;
    sound_trigger_set_device_t set_device;
    sound_trigger_pre_dev_switch_t pre_dev_switch;
    sound_trigger_post_dev_switch_t post_dev_switch;
    sound_trigger_set_port_t set_port;
};

struct sound_trigger_device {
    struct sound_trigger_hw_device device;
    pthread_mutex_t lock;
    pthread_cond_t  cond;
    struct listnode sound_model_list;
    struct mixer *mixer;
    struct audio_route *audio_route;
    volatile int session_id;
    st_ssr_state_t ssr_state;
    audio_devices_t capture_device;
    int *dev_ref_cnt;

    unsigned int tx_concurrency_active;
    unsigned int rx_concurrency_active;
    bool audio_concurrency_active;
    bool rx_concurrency_disabled;
    bool sw_mad;
    bool run_on_ape;
    bool transition_enabled;
    int num_sessions_configured;
    unsigned int max_cpe_sessions;
    unsigned int avail_cpe_phrases ;
    unsigned int avail_cpe_users ;
    unsigned int max_ape_sessions;
    unsigned int avail_ape_phrases;
    unsigned int avail_ape_users;
    unsigned int rx_conc_max_st_ses;
    struct use_case_info *ape_pcm_use_cases;
    struct use_case_info *cpe_pcm_use_cases;
    bool detect_failure;
    bool support_lec;
    void *platform;
    st_hw_type_t hw_type;
    st_exec_mode_t exec_mode;

    void *audio_hal_handle;
    audio_hw_call_back_t audio_hal_cb;

    struct listnode vendor_uuid_list;
    void *smlib_handle;
    smlib_generate_sound_trigger_recognition_config_payload_t
                                    generate_st_recognition_config_payload;
    smlib_generate_sound_trigger_phrase_recognition_event_t
                                    generate_st_phrase_recognition_event;

#ifdef ST_TRANSITION_ENABLED
    pthread_mutex_t transit_lock;
    pthread_cond_t  transit_cond;
    struct sound_trigger_transition_cmd transit_cmd;
    pthread_t transition_thread;
#endif
    struct listnode adm_cfg_list;
};
typedef struct sound_trigger_device sound_trigger_device_t;

unsigned int get_num_sessions_in_state(enum sound_trigger_states state);
struct sound_trigger_session* get_sound_trigger_session
(
    struct sound_trigger_device *stdev,
    sound_model_handle_t sound_model_handle
);
void reg_hal_event_session(sound_trigger_session_t *p_ses);
void dereg_hal_event_session(sound_trigger_session_t *p_ses);
void update_hw_mad_exec_mode(st_exec_mode_t mode);


int ape_reg_sm(sound_trigger_session_t* p_ses);
int ape_reg_sm_params(sound_trigger_session_t* p_ses);
int ape_dereg_sm(sound_trigger_session_t* p_ses);
int ape_dereg_sm_params(sound_trigger_session_t* p_ses);
int ape_start(sound_trigger_session_t* p_ses);
int ape_stop(sound_trigger_session_t* p_ses);
int ape_stop_buffering(sound_trigger_session_t* p_ses);

int cpe_reg_sm(sound_trigger_session_t* p_ses);
int cpe_reg_sm_params(sound_trigger_session_t* p_ses);
int cpe_dereg_sm(sound_trigger_session_t* p_ses);
int cpe_dereg_sm_params(sound_trigger_session_t* p_ses);
int cpe_start(sound_trigger_session_t* p_ses);
int cpe_stop(sound_trigger_session_t* p_ses);
int cpe_stop_buffering(sound_trigger_session_t* p_ses);

#ifdef ST_TRANSITION_ENABLED
void transit_stdev_init(struct sound_trigger_device *stdev);
void transit_stdev_deinit(struct sound_trigger_device *stdev);
void transit_reassign_fptrs(struct sound_trigger_session *p_ses);
void transit_retrieve_st_session(struct sound_trigger_session **p_ses);
bool transit_ses_loading(struct sound_trigger_session *p_ses);
bool transit_ses_buffering(sound_trigger_session_t* p_ses, struct pcm *pcm);
bool transit_enabled(struct sound_trigger_device *stdev);
void transit_handle_cpe_ssr_event(struct sound_trigger_device *stdev,
                                  enum ssr_event_status event);
#else
#define transit_stdev_init(stdev) (0)
#define transit_stdev_deinit(stdev) (0)
#define transit_reassign_fptrs(p_ses) (0)
#define transit_retrieve_st_session(p_ses) (0)
#define transit_ses_loading(p_ses) (0)
#define transit_ses_buffering(p_ses, pcm) (0)
#define transit_enabled(stdev) (0)
#define transit_handle_cpe_ssr_event(stdev, event) (0)
#endif
#endif /* SOUND_TRIGGER_HW_H */

