#ifndef _WFD_UTILS_H_
#define _WFD_UTILS_H_

/*==============================================================================
*       WFDUtils.h
*
*  DESCRIPTION:
*       Header file for WFDUtils
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

#include "OMX_Core.h"

#ifdef __cplusplus
extern "C" {
#endif

/*----------------------------------------------------------------------
 A structure for storing custom information in an OMX_BUFFERHEADERTYPE
------------------------------------------------------------------------
*/

struct buff_hdr_extra_info
{
    OMX_TICKS   nEncDelTime;
    OMX_TICKS   nEncRcvTime;
    OMX_TICKS   nEncryptTime;
    OMX_TICKS   nMuxDelTime;
    OMX_TICKS   nMuxRcvTime;
    OMX_S64     nFrameNo;
    OMX_BOOL    bPushed;
    OMX_BOOL    bBlackFrame;
};

void GetCurTime(OMX_TICKS& lTtime);

int SleepUs(int timeMs);

int setThreadName(const char* name);

void getThreadName(char* nameBuff, int len);

void setThreadPriority(int prio);

const char* getSchedPolicyName();

int setSchedPolicy(int policy);

unsigned long getTimerSlackValue();

unsigned long setTimerSlackValue(unsigned long slackVal);

#ifdef __cplusplus
}
#endif

#endif //_WFD_UTILS_H_
