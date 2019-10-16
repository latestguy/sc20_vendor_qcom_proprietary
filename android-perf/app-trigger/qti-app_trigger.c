/******************************************************************************
 @file    qti-app_trigger.c
 @brief   Android app trigger library
 DESCRIPTION

  Copyright (c) 2015 Qualcomm Technologies, Inc. All Rights Reserved.
  Qualcomm Technologies Proprietary and Confidential.
  ---------------------------------------------------------------------------
******************************************************************************/

#include <stdlib.h>
#include <string.h>
#include <libgen.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>

#define LOG_TAG		"ANDR-PERF-LOGS"
#include <cutils/log.h>
#include <cutils/properties.h>

#if QC_DEBUG
#  define QLOGE(...)	ALOGE(__VA_ARGS__)
#  define QLOGD(...)	ALOGD(__VA_ARGS__)
#  define QLOGW(...)	ALOGW(__VA_ARGS__)
#  define QLOGI(...)	ALOGI(__VA_ARGS__)
#  define QLOGV(...)	ALOGV(__VA_ARGS__)
#else
#  define QLOGE(...)
#  define QLOGD(...)
#  define QLOGW(...)
#  define QLOGI(...)
#  define QLOGV(...)
#endif

void activity_trigger_init(void);
void activity_trigger_start(const char *, int *);
void activity_trigger_resume(const char *);

static int find_name(const char *list[], int listlen, const char *name);

#define APP_HW_ACCEL_TRIGGER_PROPERTY "sys.apps.hwaccel.enable"

#define FLAG_HARDWARE_ACCELERATED 0x0200

static const char *hw_accel_apps[] = {
    "com.sina.weibo",
    "com.taobao.taobao",
    "com.tmall.wireless",
    "com.qzone",
    "com.Qunar",
    "com.letv.android.client",
    "com.pplive.androidphone",
    "com.jingdong.app.mall",
    "com.shuqi.controller",
    "com.youdao.dict",
    "com.edog",
    "com.jiayuan",
    "com.youdao.x86.dict",
    "com.lingdong.client.android",
    "com.mobi.common.main.wmfzlbizhi1111",
    "com.autonavi.xmgd.navigator",
    "com.tencent.mobileqq"
};

void activity_trigger_init(void)
{
    QLOGE("App trigger library initialized successfully");
}


void activity_trigger_start(const char *name, int *flags) {
    int found = 0;
    int accelapp = -1;
    int len = 0;
    char buf[PROPERTY_VALUE_MAX];

    QLOGE("App trigger starting '%s'", (name) ? name : "<null>");

    if (!name || (NULL == flags)) {
        return;
    }

    /* Check for perf applications and apply hw accel */
    len = sizeof (hw_accel_apps) / sizeof (*hw_accel_apps);
    accelapp = find_name(hw_accel_apps, len, name);
    if (accelapp) {
        property_get(APP_HW_ACCEL_TRIGGER_PROPERTY, buf, "1");
        if (atoi(buf) == 1) {
            QLOGE("Setting layout params setting to vendor specific");
            *flags |= FLAG_HARDWARE_ACCELERATED;
        }
        return;
    }
}

void activity_trigger_resume(const char *name) {
    /* Do the same as activity start */
    int flags = 0;
    activity_trigger_start(name, &flags);
}

void activity_trigger_deinit(void)
{
    int handle = 0;
}

int find_name(const char *list[], int listlen, const char *name) {
    int i;
    unsigned int applen;
    int ret = 0;

    /* Check for perf applications */
    for (i = 0; i < listlen; i++) {
        applen = strlen(list[i]);
        if (!applen)
            continue;
        if (strncmp(list[i], name, applen) == 0) {
           QLOGE("App trigger found '%s'", name);
           ret  = 1;
           break;
        }
    }

    return ret;
}

