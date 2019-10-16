/*==============================================================================
*       WFDMMUtils.cpp
*
*  DESCRIPTION:
*       Source file for WFDUtils
*
*
*  Copyright (c) 2014 - 2015 Qualcomm Technologies, Inc. All Rights Reserved.
*  Qualcomm Technologies Proprietary and Confidential.
*===============================================================================
*/
/*==============================================================================
                             Edit History
================================================================================
   When            Who           Why
-----------------  ------------  -----------------------------------------------
02/19/2014                    InitialDraft
================================================================================
*/

/*==============================================================================
**               Includes and Public Data Declarations
**==============================================================================
*/

/* =============================================================================

                     INCLUDE FILES FOR MODULE

================================================================================
*/

#include "WFDUtils.h"
#include "WFDMMLogs.h"
#include <time.h>
#include <errno.h>
#include <threads.h>
#include <pthread.h>
#include <cutils/sched_policy.h>
#include <sys/prctl.h>
#ifdef LOG_TAG
#undef LOG_TAG
#endif

#define THREAD_NAME_SIZE 16

#define LOG_TAG "WFD_Utils"

#ifdef __cplusplus
extern "C" {
#endif

/*=============================================================================

         FUNCTION:          GetCurTime

         DESCRIPTION:
*//**       @brief          Helper method to query current system time in micro
                            sec precision

*//**
@par     DEPENDENCIES:
                            None

*//*
         PARAMETERS:
*//**       @param[out]     lTime  Variable which will hold the current sys time

*//*     RETURN VALUE:
*//**       @return
                            None

@par     SIFE EFFECTS:
                            None
*//*=========================================================================*/

void GetCurTime(OMX_TICKS& lTime)
{
    static const OMX_S32 WFD_TIME_NSEC_IN_MSEC = 1000000;
    static const OMX_S32 WFD_TIME_NSEC_IN_USEC = 1000;
    struct timespec tempTime;
    clock_gettime(CLOCK_MONOTONIC, &tempTime);
    lTime =(OMX_TICKS)(((unsigned long long)tempTime.tv_sec *
                                            WFD_TIME_NSEC_IN_MSEC)
                     + ((unsigned long long)tempTime.tv_nsec /
                                             WFD_TIME_NSEC_IN_USEC));
}
int SleepUs(int timeMs)
{
    useconds_t t = timeMs*1000;
    return usleep(t);
}

int setThreadName(const char* name)
{
    if(name)
    {
        if(pthread_setname_np(pthread_self(),name))
        {
            WFDMMLOGE2("Failed to set thread name %s due to %s",
                name, strerror(errno));
            return errno;
        }
    }
    return 0;
}

void getThreadName(char* nameBuff, int len)
{
    //It's OK if we get a null name
    if(nameBuff && len > THREAD_NAME_SIZE)//atleast 16 bytes is required by PR_GET_NAME
    {
        prctl(PR_GET_NAME, (unsigned long)nameBuff);
        nameBuff[THREAD_NAME_SIZE] = '\0';
    }
}

void setThreadPriority(int prio)
{
    int tid = gettid();
    char thread_name[THREAD_NAME_SIZE + 1] = {0};
    getThreadName(thread_name,sizeof(thread_name));
    WFDMMLOGE2("%s thread priority before %d ",thread_name,
        androidGetThreadPriority(tid));
    androidSetThreadPriority(0, prio);
    WFDMMLOGE2("%s thread priority after %d ",thread_name,
        androidGetThreadPriority(tid));
}

const char* getSchedPolicyName()
{
    int tid = gettid();
    SchedPolicy policy;
    get_sched_policy(tid, & policy);
    return get_sched_policy_name(policy);
}

int setSchedPolicy(int policy)
{
    char thread_name[THREAD_NAME_SIZE + 1] = {0};
    getThreadName(thread_name,sizeof(thread_name));
    SchedPolicy after = static_cast<SchedPolicy>(policy);
    WFDMMLOGE2("Prior scheduling policy  for %s is %s", thread_name,
        getSchedPolicyName());
    int ret = set_sched_policy(0,after);
    WFDMMLOGE2("After scheduling policy  for %s is %s", thread_name,
        getSchedPolicyName());
    return ret;
}

unsigned long getTimerSlackValue()
{
    return prctl(PR_GET_TIMERSLACK);
}

unsigned long setTimerSlackValue(unsigned long slackVal)
{
    char thread_name[THREAD_NAME_SIZE + 1] = {0};
    getThreadName(thread_name,sizeof(thread_name));
    if(slackVal == 0)
    {
        slackVal = 50000;//Default value for init is 50us
    }
    int ret = prctl(PR_SET_TIMERSLACK,slackVal);
    if(ret)
    {
        WFDMMLOGE2("Failed to set timer slack value due to ret = %d --> %s",
            ret,strerror(errno));
    }
    unsigned long newSlack = getTimerSlackValue();
    WFDMMLOGE2("Timer slack value for %s is %lu", thread_name, newSlack);
    return newSlack;
}

#ifdef __cplusplus
}
#endif
