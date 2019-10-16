/*
 * Copyright (c) 2015 Qualcomm Technologies, Inc.
 * All Rights Reserved.
 * Confidential and Proprietary - Qualcomm Technologies, Inc.
 *
 * Copyright (c) 2011-2012, The Linux Foundation. All rights reserved.
 */

#define LOG_TAG "ANDR-PERF-JNI"

#include "jni.h"
#include "JNIHelp.h"
#include <android_runtime/AndroidRuntime.h>

#include <dlfcn.h>
#include <limits.h>
#include <pthread.h>
#include <string.h>

#include <cutils/properties.h>
#include <utils/Log.h>

namespace android
{

// ----------------------------------------------------------------------------
static int  (*perf_lock_acq)(int, int, int[], int)  = NULL;
static int  (*perf_lock_rel)(int)                   = NULL;
static void *dlhandle                               = NULL;
static pthread_mutex_t dl_mutex                     = PTHREAD_MUTEX_INITIALIZER;

// ----------------------------------------------------------------------------

static void
com_qualcomm_qti_performance_native_init()
{
    const char *rc;
    char buf[PROPERTY_VALUE_MAX];
    int len;

    /* Retrieve name of vendor extension library */
    if (property_get("ro.vendor.extension_library", buf, NULL) <= 0) {
        return;
    }

    /* Sanity check - ensure */
    buf[PROPERTY_VALUE_MAX-1] = '\0';
    if (strstr(buf, "/") != NULL) {
        return;
    }


    dlhandle = dlopen(buf, RTLD_NOW | RTLD_LOCAL);

    if (dlhandle == NULL) {
        return;
    }

    dlerror();

    perf_lock_acq = (int (*) (int, int, int[], int))dlsym(dlhandle, "perf_lock_acq");
    if ((rc = dlerror()) != NULL) {
        goto cleanup;
    }

    perf_lock_rel = (int (*) (int))dlsym(dlhandle, "perf_lock_rel");
    if ((rc = dlerror()) != NULL) {
        goto cleanup;
    }

    return;

cleanup:
    perf_lock_acq  = NULL;
    perf_lock_rel  = NULL;
    if (dlhandle) {
        dlclose(dlhandle);
        dlhandle = NULL;
    }
}

static void
com_qualcomm_qti_performance_native_deinit(JNIEnv *env, jobject clazz)
{
    pthread_mutex_lock(&dl_mutex);
    if (dlhandle) {
        perf_lock_acq  = NULL;
        perf_lock_rel  = NULL;

        dlclose(dlhandle);
        dlhandle       = NULL;
    }
    pthread_mutex_unlock(&dl_mutex);
}

static jint
com_qualcomm_qti_performance_native_perf_lock_acq(JNIEnv *env, jobject clazz, jint handle, jint duration, jintArray list)
{
    jint listlen = env->GetArrayLength(list);
    jint buf[listlen];
    int i=0;
    int ret = 0;
    env->GetIntArrayRegion(list, 0, listlen, buf);

    pthread_mutex_lock(&dl_mutex);
    if (perf_lock_acq == NULL) {
        com_qualcomm_qti_performance_native_init();
    }
    if (perf_lock_acq) {
        ret = (*perf_lock_acq)(handle, duration, buf, listlen);
    }
    pthread_mutex_unlock(&dl_mutex);
    return ret;
}

static jint
com_qualcomm_qti_performance_native_perf_lock_rel(JNIEnv *env, jobject clazz, jint handle)
{
    int ret = 0;
    pthread_mutex_lock(&dl_mutex);
    if (perf_lock_rel == NULL) {
        com_qualcomm_qti_performance_native_init();
    }
    if (perf_lock_rel) {
        ret = (*perf_lock_rel)(handle);
    }
    pthread_mutex_unlock(&dl_mutex);
    return ret;
}
// ----------------------------------------------------------------------------

static JNINativeMethod gMethods[] = {
    {"native_perf_lock_acq",  "(II[I)I",               (int *)com_qualcomm_qti_performance_native_perf_lock_acq},
    {"native_perf_lock_rel",  "(I)I",                  (int *)com_qualcomm_qti_performance_native_perf_lock_rel},
    {"native_deinit",         "()V",                   (void *)com_qualcomm_qti_performance_native_deinit},
};


int register_com_qualcomm_qti_Performance(JNIEnv *env)
{
    pthread_mutex_lock(&dl_mutex);
    com_qualcomm_qti_performance_native_init();
    pthread_mutex_unlock(&dl_mutex);

    return jniRegisterNativeMethods(env, "com/qualcomm/qti/Performance", gMethods, NELEM(gMethods));
}

}   // namespace android

/*
 * JNI Initialization
 */
jint JNI_OnLoad(JavaVM *jvm, void *reserved)
{
    JNIEnv *e;
    int status;

    ALOGV("com.qualcomm.qti.Performance: loading JNI\n");

    // Check JNI version
    if (jvm->GetEnv((void **)&e, JNI_VERSION_1_6)) {
        ALOGE("com.qualcomm.qti.Performance: JNI version mismatch error");
        return JNI_ERR;
    }

    if ((status = android::register_com_qualcomm_qti_Performance(e)) < 0) {
        ALOGE("com.qualcomm.qti.Performance: jni registration failure: %d", status);
        return JNI_ERR;
    }

    return JNI_VERSION_1_6;
}

