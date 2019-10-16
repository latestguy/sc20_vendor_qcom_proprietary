/*
 * Copyright (c) 2015 Qualcomm Technologies, Inc.
 * All Rights Reserved.
 * Confidential and Proprietary - Qualcomm Technologies, Inc.
 *
 */

#include <stdio.h>

#define ALOGI(...)      fprintf(stdout,__VA_ARGS__); \
                        fprintf(stdout,"\n")
#define ALOGE(...)      fprintf(stderr,__VA_ARGS__); \
                        fprintf(stdout,"\n")
#define ALOGV(...)      fprintf(stdout,__VA_ARGS__); \
                        fprintf(stdout,"\n");
#define ALOGD(...)      fprintf(stdout,__VA_ARGS__); \
                        fprintf(stdout,"\n")
#define ALOGW(...)      fprintf(stderr,__VA_ARGS__); \
                        fprintf(stdout,"\n")

// #define VERY_VERBOSE_LOGGING
#ifdef VERY_VERBOSE_LOGGING
#define ALOGVV ALOGV
#else
#define ALOGVV(a...) do { } while(0)
#endif
