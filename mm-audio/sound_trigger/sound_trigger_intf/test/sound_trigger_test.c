/*
 * Copyright (c) 2014-2015 Qualcomm Technologies, Inc.
 * All Rights Reserved.
 * Confidential and Proprietary - Qualcomm Technologies, Inc.
 *
 */

#include <sys/types.h>
#include <sys/stat.h>
#include <sys/time.h>
#include <dirent.h>
#include <unistd.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <pthread.h>

#include "sound_trigger_platform.h"
#include "sound_trigger_extn.h"

#define OK 0

#define SOUNDTRIGGER_TEST_USAGE \
    "sound_trigger_test usage\n"  \
    "sound_trigger_test -sm <soundmodel path> -nk <number of keywords>\n" \
    "Optional Params, can be given in any order\n" \
    "-user <user verification> -nu <number of users per keyword>\n" \
    "-mode <ape=0/cpe=1>\n" \
    "-lab <LookAheadBuffering enable/disable> -duration <LAB duration>\n" \
    "-kwcnf <keyword confidence levels> -usrcnf <user confidence levels>\n" \
    "-snd_card_name <snd_card_name>\n" \
    "-lec <LEC enable>\n" \
    "\n"

#define strlcpy g_strlcpy

#define THERMAL_SYSFS "/sys/class/thermal"
#define TZ_TYPE "/sys/class/thermal/thermal_zone%d/type"
#define MAX_PATH 128

static int counter = 0;
static struct cookie {
    int duration;
    struct sound_trigger_device *stdev;
    sound_model_handle_t sm_handle;
    struct sound_trigger_recognition_config *rc_config;
};

static void *event_handler_thread(void *);

int read_line_from_file(const char *path, char *buf, size_t count)
{
    char * fgets_ret;
    FILE * fd;
    int rv;

    fd = fopen(path, "r");
    if (fd == NULL)
        return -1;

    fgets_ret = fgets(buf, (int)count, fd);
    if (NULL != fgets_ret) {
        rv = (int)strlen(buf);
    } else {
        rv = ferror(fd);
    }
    fclose(fd);

   return rv;
}

static int get_speaker_count(void )
{
    DIR *tdir = NULL;
    struct dirent *tdirent = NULL;
    int tzn = 0;
    char name[MAX_PATH] = {0};
    char cwd[MAX_PATH] = {0};
    char file[10] = "wsa";
    bool found = false;
    int wsa_count = 0;

    if (!getcwd(cwd, sizeof(cwd)))
        return false;

    chdir(THERMAL_SYSFS); /* Change dir to read the entries. Doesnt work
                             otherwise */
    tdir = opendir(THERMAL_SYSFS);
    if (!tdir) {
        printf("Unable to open %s\n", THERMAL_SYSFS);
        return false;
    }

    while ((tdirent = readdir(tdir))) {
        char buf[50];
        struct dirent *tzdirent;
        DIR *tzdir = NULL;

        tzdir = opendir(tdirent->d_name);
        if (!tzdir)
            continue;
        while ((tzdirent = readdir(tzdir))) {
            if (strcmp(tzdirent->d_name, "type"))
                continue;
            snprintf(name, MAX_PATH, TZ_TYPE, tzn);
            read_line_from_file(name, buf, sizeof(buf));
            if (strstr(buf, file)) {
                wsa_count++;
                /*We support max only two WSA speakers*/
                if (wsa_count == 2)
                    break;
            }
            tzn++;
        }
        closedir(tzdir);
    }
    if (wsa_count > 0)
        printf("Found %d WSA present on the platform", wsa_count);

    closedir(tdir);
    chdir(cwd); /* Restore current working dir */
    return wsa_count;
}

static void eventCallback(struct sound_trigger_recognition_event *event, void *sessionHndl)
{
    printf("Callback event received: %d\n", event->status);
    int rc;
    pthread_attr_t attr;
    pthread_t callback_thread;
    struct cookie *cookie = (struct cookie *) sessionHndl;

    rc = pthread_attr_init(&attr);
    if (rc != 0) {
        printf("pthread attr init failed %d\n",rc);
        return;
    }
    pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_DETACHED);
    rc = pthread_create(&callback_thread, &attr,
                        event_handler_thread, cookie);
    if (rc != 0)
        printf("pthread create failed %d\n",rc);
    pthread_attr_destroy(&attr);
}

static void capture_lab_data(int capture_handle, int duration)
{
    int ret = 0;
    void *buffer;
    size_t bytes, written;
    char lab_capture_file[128] = "";
    size_t cur_bytes_read = 0;
    size_t total_bytes_toRead = 0;
    FILE *fp;
    int n;

    //read one period buffer each time
    bytes = SOUND_TRIGGER_APE_PERIOD_SIZE * 2;
    /* n = multiplier for bytes to obtain 1sec worth of data
     * for 16khz, mono, pcm_16
     * TODO - obtain these from recognition event
     */
    n = CEIL(1000, ((bytes * 1000) / (16000 * 2)));
    total_bytes_toRead = bytes * n * duration;
    buffer = calloc(1, bytes);
    if (buffer == NULL) {
        printf("Could not allocate memory for capture buffer\n");
        return;
    }

    snprintf(lab_capture_file, sizeof(lab_capture_file),
             "/sdcard/SVA/lab_capture_file_%d",capture_handle);
    fp = fopen(lab_capture_file, "wb");
    if (fp == NULL) {
        printf("Could not open lab capture file : %s\n", lab_capture_file);
        return;
    }

    while (cur_bytes_read < total_bytes_toRead) {
        ret = sound_trigger_extn_read(capture_handle, buffer, bytes);
        written = fwrite(buffer, 1, bytes, fp);
        if (written != bytes) {
            printf("written %d, bytes %d\n", written, bytes);
            if (ferror(fp)) {
                printf("Error writing lab capture data into file %s\n",strerror(errno));
                break;
            }
        }
        cur_bytes_read += bytes;
        memset(buffer, 0, bytes);
    }
    printf("bytes to read %d, bytes read %d\n", total_bytes_toRead, cur_bytes_read);
    sound_trigger_extn_stop_lab(capture_handle);
    fclose(fp);
}

static void *event_handler_thread(void *context)
{
    struct cookie *cookie = (struct cookie *) context;
    if (!cookie) {
        printf("Error: cookie is null\n");
        return NULL;
    }
    struct sound_trigger_device *stdev = cookie->stdev;
    struct sound_trigger_recognition_config *rc_config = cookie->rc_config;
    sound_model_handle_t sm_handle = cookie->sm_handle;
    int duration = cookie->duration;

    printf("cookie params %p, %p, %d, %d\n",stdev,
    rc_config, sm_handle, duration);
    //capture_requested is part of recognition event as well
    if (rc_config && rc_config->capture_requested) {
        capture_lab_data(rc_config->capture_handle, duration);
    }
    /* ignore error */
    stdev_start_recognition(stdev, sm_handle,
                    rc_config, eventCallback, cookie);
    counter++;
    printf("callback event processed, detection counter %d\n", counter);
    printf("proceed with utterance or command \n");
    printf("- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -\n\n");
    return NULL;
}

int main(int argc, char *argv[])
{
    char sound_model_file[128] = "";
    bool exit_loop = false, device_enabled = false;
    sound_model_handle_t sm_handle = 0;
    int sm_data_size  = 0;
    int sound_model_size = 0;
    unsigned int num_kws = 0;
    unsigned int i, j;
    struct sound_trigger_phrase_sound_model *sound_model = NULL;
    struct sound_trigger_recognition_config *rc_config = NULL;
    struct cookie *sessionHndl;
    st_exec_mode_t mode = ST_DEVICE_EXEC_MODE_CPE; //CPE
    char snd_card_name[256] = "wcd9326"; //8909 + 9326
    audio_devices_t device = AUDIO_DEVICE_IN_BUILTIN_MIC; //only handset mic supported
    st_device_t st_device = ST_DEVICE_NONE;
    int capture_handle = AUDIO_IO_HANDLE_NONE;
    bool capture_requested = false;
    int duration = 5; //5sec is default duration
    bool user_verification = false;
    unsigned int num_users = 0;
    unsigned int kw_conf = 60; //default confidence level is 60
    unsigned int user_conf = 60;
    bool lec_enable = false;
    bool sw_mad = false;
    int speaker_count = 1;

    if (argc < 5) {
        printf(SOUNDTRIGGER_TEST_USAGE);
        return 0;
    }

    if (strcmp(argv[1], "-sm") == 0) {
        strlcpy(sound_model_file, argv[2],sizeof(sound_model_file));
    }
    if (strcmp(argv[3], "-nk") == 0) {
        num_kws = atoi(argv[4]);
    }

    if ((strcmp(sound_model_file, "") == 0) || (num_kws == 0)) {
        printf(SOUNDTRIGGER_TEST_USAGE);
        return 0;
    }

    for (i = 6; i < argc; i += 2) {
        if ((strncmp(argv[i-1], "-user", sizeof(argv[i-1])) == 0)) {
            user_verification =
                      (0 == strncasecmp(argv[i], "true", 4))? true:false;
        } else if ((strncmp(argv[i-1], "-nu", sizeof(argv[i-1])) == 0)) {
            num_users = atoi(argv[i]);
        } else if ((strncmp(argv[i-1], "-mode", sizeof(argv[i-1])) == 0)) {
            mode = atoi(argv[i]);
        } else if ((strncmp(argv[i-1], "-lab", sizeof(argv[i-1])) == 0)) {
            capture_requested =
                      (0 == strncasecmp(argv[i], "true", sizeof(argv[i])))? true:false;
        } else if ((strncmp(argv[i-1], "-duration", sizeof(argv[i-1])) == 0)) {
            duration = atoi(argv[i]);
        } else if ((strncmp(argv[i-1], "-kwcnf", sizeof(argv[i-1])) == 0)) {
            kw_conf = atoi(argv[i]);
        } else if ((strncmp(argv[i-1], "-usrcnf", sizeof(argv[i-1])) == 0)) {
            user_conf = atoi(argv[i]);
        } else if ((strncmp(argv[i-1], "-snd_card_name", sizeof(argv[i-1])) == 0)) {
            strlcpy(snd_card_name, argv[i],sizeof(snd_card_name));
        } else if ((strncmp(argv[i-1], "-lec", sizeof(argv[i-1])) == 0)) {
            lec_enable =
                      (0 == strncasecmp(argv[i], "true", sizeof(argv[i])))? true:false;
        }
    }

    int status = 0;
    char command[128];
    struct sound_trigger_device *stdev;

    status = stdev_open(&stdev);
    if (OK != status || NULL == stdev) {
        printf("sound_trigger_device_open() failed with %d\n",status);
        return status;
    }

    struct sound_trigger_properties properties;
    st_property_type_t prop_type = ST_PROPERTY_GLOBAL;
    stdev_get_properties(stdev, &properties, prop_type, mode);
    sw_mad = properties.sw_mad;

    status = stdev_set_properties(stdev, &properties, prop_type, mode);
    if (OK != status) {
        printf("sound_trigger_extn_init() failed with %d\n",status);
        return status;
    }

    prop_type = ST_PROPERTY_LEC_SUPPORT;
    properties.support_lec = lec_enable;
    status = stdev_set_properties(stdev, &properties, prop_type, mode);
    if (OK != status) {
        printf("sound_trigger_extn_init() failed with %d\n",status);
        return status;
    }

    status = sound_trigger_extn_init();
    if (OK != status) {
        printf("sound_trigger_extn_init() failed with %d\n",status);
        return status;
    }


    status = acdb_helper_init(snd_card_name);
    if (status < 0) {
        printf("acdb_helper_init() failed with %d\n",status);
        goto exit_acdb;
    }
    /* send calibration and enable device */
    st_device = platform_stdev_get_device(stdev->platform, device);

    if (lec_enable) {
        speaker_count =  get_speaker_count();
        if (speaker_count > 0) {
            acdb_helper_set_acdb_id(mode, st_device,
                                           speaker_count);
        }
    }

    acdb_helper_send_calibration(st_device,
                      ACDB_LSM_APP_TYPE_NO_TOPOLOGY,
                      false, mode, sw_mad, lec_enable);

    status = ucm_open(snd_card_name);
    if (status < 0) {
        printf("ucm_open() failed with %d\n",status);
        goto exit_ucm;
    }

    FILE *fp = fopen(sound_model_file, "rb");
    if (fp == NULL) {
        printf("Could not open sound mode file : %s\n", sound_model_file);
        goto exit_sm_mem;
    }

    /* Get the sound mode size i.e. file size */
    fseek( fp, 0, SEEK_END);
    sm_data_size  = ftell(fp);
    fseek( fp, 0, SEEK_SET);

    sound_model_size = sizeof(struct sound_trigger_phrase_sound_model) + sm_data_size;
    sound_model = (struct sound_trigger_phrase_sound_model *)calloc(1, sound_model_size);
    if (sound_model == NULL) {
        printf("Could not allocate memory for sound model");
        goto exit_sm_mem;
    }
    sound_model->common.type = SOUND_MODEL_TYPE_KEYPHRASE;
    sound_model->common.data_size = sm_data_size;
    sound_model->common.data_offset = sizeof(*sound_model);
    sound_model->num_phrases = num_kws;
    for (i = 0; i < num_kws; i++) {
        sound_model->phrases[i].num_users = num_users;
        if (user_verification)
            sound_model->phrases[i].recognition_mode = RECOGNITION_MODE_VOICE_TRIGGER |
                              RECOGNITION_MODE_USER_IDENTIFICATION;
        else
            sound_model->phrases[i].recognition_mode = RECOGNITION_MODE_VOICE_TRIGGER;
    }

    int bytes_read = fread((char*)sound_model+sound_model->common.data_offset , 1, sm_data_size , fp);
    printf("bytes from the file %d\n", bytes_read);
    if (bytes_read != sm_data_size) {
        printf("Something wrong while reading data from file: bytes_read %d file_size %d", bytes_read, sm_data_size);
        goto exit_sm_mem;
    }

    /* Load Sound Model */
    printf("sound model data_size %d data_offset %d\n", sm_data_size, sound_model->common.data_offset);
    status = stdev_load_sound_model(stdev, &sound_model->common, NULL, NULL, &sm_handle);
    if (OK != status) {
        printf("load_sound_model failed\n");
        goto exit_sm_mem;
    }

    rc_config = (struct sound_trigger_recognition_config *)calloc(1, sizeof(struct sound_trigger_recognition_config));
    if (rc_config == NULL) {
        printf("Could not allocate memory for recognition config");
        goto exit_rc_config_mem;
    }
    rc_config->capture_handle = capture_handle++;
    rc_config->capture_device = AUDIO_DEVICE_IN_BUILTIN_MIC;
    rc_config->capture_requested = capture_requested;
    rc_config->num_phrases = num_kws;

    int user_id = num_kws; //user_id should start from num_kws
    for (i = 0; i < num_kws; i++) {
        rc_config->phrases[i].id = i;
        rc_config->phrases[i].confidence_level = kw_conf;
        rc_config->phrases[i].num_levels = num_users;
        for (j = 0; j < num_users; j++) {
            rc_config->phrases[i].levels[j].level = user_conf;
            rc_config->phrases[i].levels[j].user_id = user_id++;
        }
        if (user_verification)
            rc_config->phrases[i].recognition_modes = RECOGNITION_MODE_VOICE_TRIGGER |
                             RECOGNITION_MODE_USER_IDENTIFICATION;
        else
            rc_config->phrases[i].recognition_modes = RECOGNITION_MODE_VOICE_TRIGGER;
    }

    sessionHndl = (struct cookie *)calloc(1, sizeof(struct cookie));
    if (sessionHndl== NULL) {
        printf("Could not allocate memory for cookie");
        goto exit_rc_config_mem;
    }

    sessionHndl->stdev = stdev;
    sessionHndl->rc_config = rc_config;
    sessionHndl->sm_handle = sm_handle;
    sessionHndl->duration = duration;
    printf("session handl params %p, %p, %d, %d\n",sessionHndl->stdev,
    sessionHndl->rc_config, sessionHndl->sm_handle, duration);

    do {
        printf("Enter command <start/stop/exit>: \n");
        fgets(command, 128, stdin);
        printf("Received the command: %s\n", command);

        if(!strncmp(command, "exit", 4)){
            printf("exiting the loop ..\n");
            exit_loop = true;
        } else if(!strncmp(command, "start", 5)) {
            if (device_enabled)
                continue;

            stdev_set_usecase(mode, true, lec_enable, speaker_count);
            printf("setting device %d for mode %d\n", st_device, mode);
            stdev_set_device(st_device, mode, true);
            device_enabled = true;

            /* Start Recognition */
            status = stdev_start_recognition(stdev, sm_handle,
                                                     rc_config, eventCallback, sessionHndl);
            if (OK != status) {
               printf("start_recognition failed\n");
               exit_loop = true;
            }
            counter = 0;
        } else if(!strncmp(command, "stop", 4)) {
            /* Stop Recognition */
            status = stdev_stop_recognition(stdev, sm_handle);
            if (OK != status) {
               printf("stop_recognition failed\n");
               exit_loop = true;
            }

            if (!device_enabled)
                continue;

            stdev_set_device(st_device, mode, false);
            stdev_set_usecase(mode, false, lec_enable, speaker_count);
            device_enabled = false;
        }
    } while(!exit_loop);

    /* if device enabled and exit loop set to true */
    if (device_enabled) {
        /* start recognition was successful before exit loop */
        if (status == OK)
            stdev_stop_recognition(stdev, sm_handle);
        stdev_set_device(st_device, mode, false);
        stdev_set_usecase(mode, false, lec_enable, speaker_count);
    }

exit_rc_config_mem:
    status = stdev_unload_sound_model(stdev, sm_handle);
    if (OK != status) {
       printf("unload_sound_model failed\n");
       return status;
    }
    if (rc_config)
       free(rc_config);
exit_sm_mem:
    if (sound_model)
       free(sound_model);
    ucm_close();
exit_ucm:
    acdb_helper_send_default_calibration(ACDB_LSM_APP_TYPE_NO_TOPOLOGY,
                      false, lec_enable);
    acdb_helper_deinit();
exit_acdb:
    sound_trigger_extn_deinit();

    status = stdev_close(stdev);
    if (OK != status) {
       printf("sound_trigger_device_close() failed, status %d\n", status);
    }
    if (fp)
        fclose(fp);
    return status;
}
