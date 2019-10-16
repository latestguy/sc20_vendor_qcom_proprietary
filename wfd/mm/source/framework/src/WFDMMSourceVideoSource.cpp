/* =======================================================================
                              WFDMMSourceVideoSource.cpp
DESCRIPTION

This module is for WFD source implementation. Takes care of interacting
with Encoder (which in turn interacts with capture module).

Copyright (c) 2011 - 2015 Qualcomm Technologies, Inc.
All Rights Reserved.
Confidential and Proprietary - Qualcomm Technologies, Inc.
========================================================================== */


/* =======================================================================
                             Edit History
   When            Who           Why
-----------------  ------------  -----------------------------------------------
12/27/2013                       InitialDraft
========================================================================== */

/*========================================================================
 *                             Include Files
 *==========================================================================*/
#ifdef LOG_TAG
#undef LOG_TAG
#endif
#define LOG_TAG "WFDMMSourceVideoSource"

#include "WFDMMSourceSignalQueue.h"
#include "WFDMMSourceVideoSource.h"
#include "MMDebugMsg.h"
#include "WFDMMLogs.h"
#include <fcntl.h>
#include "QOMX_VideoExtensions.h"
#include "WFD_HdcpCP.h"
#include "wfd_cfg_parser.h"
#include "MMMalloc.h"
#include "MMMemory.h"
#include <cutils/properties.h>
#include "WFDUtils.h"

#ifndef WFD_ICS
#include "common_log.h"
#endif
#include <threads.h>
#define MM_MSG_PRIO_MEDIUM MM_MSG_PRIO_ERROR

/*========================================================================
 *                          Defines/ Macro
 *==========================================================================*/
#define VIDEO_PVTDATA_TYPE_FILLERNALU           0
#define VIDEO_PVTDATA_TYPE_FILLERNALU_ENCRYPTED 1
#define PES_PVT_DATA_LEN 16
#define BUFFER_EXTRA_DATA 1024

#define WFD_H264_PROFILE_CONSTRAINED_BASE 1
#define WFD_H264_PROFILE_CONSTRAINED_HIGH 2
#define FRAME_SKIP_FPS_VARIANCE 20
#define WFDMM_VIDEO_SRC_ION_MEM_LIMIT 3145728
// Frame skipping delay
uint32 video_frame_skipping_start_delay = 0;

/**!
 * @brief Helper macro to set private/internal flag
 */
#define FLAG_SET(_pCtx_, _f_) (_pCtx_)->nFlags  |= (_f_)

/**!
 * @brief Helper macro to check if a flag is set or not
 */
#define FLAG_ISSET(_pCtx_, _f_) (((_pCtx_)->nFlags & (_f_)) ? OMX_TRUE : OMX_FALSE)

/**!
 * @brief Helper macro to clear a private/internal flag
 */
#define FLAG_CLEAR(_pCtx_, _f_) (_pCtx_)->

#ifndef OMX_SPEC_VERSION
#define OMX_SPEC_VERSION 0x00000101
#endif

struct output_metabuffer {
    OMX_U32 type;
    native_handle_t* nh;
};

enum
{
    CBP_VGA_BLANK_FRAME_SIZE = 1248,
    CHP_VGA_BLANK_FRAME_SIZE = 1250,
    MAX_BLANKING_PERIOD = 200,//in ms
};

OMX_U8 VideoSource::sFillerNALU[FILLER_NALU_SIZE] =
{0x00, 0x00, 0x00, 0x01, 0x0c, 0xff, 0xff, 0x80};

static const OMX_U8 sVGA_CBP_BkFm[CBP_VGA_BLANK_FRAME_SIZE]=
{
    0x00,0x00,0x00,0x01,0x09,0x10,0x00,0x00,0x00,0x01,0x67,0x42,0x80,0x1F,0xDA,0x02,
    0x80,0xF6,0x80,0x6D,0x0A,0x13,0x50,0x00,0x00,0x00,0x01,0x68,0xCE,0x06,0xE2,0x00,
    0x00,0x00,0x01,0x65,0xB8,0x41,0x5F,0xFF,0xF8,0x7A,0x28,0x00,0x08,0x18,0xFB,0x55,
    0x6B,0x6B,0x6B,0x6B,0x6B,0x6B,0x6B,0x6B,0x6B,0x6B,0x6B,0x6B,0x6B,0x6B,0x6B,0x6B,
    0x6B,0x6B,0x6B,0x6B,0x6B,0x6B,0x6B,0x6B,0x6B,0x6B,0x6B,0x6B,0x6B,0x6B,0x6B,0x6B,
    0x6B,0x6B,0x6B,0x6B,0x6B,0x6B,0x4D,0xAA,0x7A,0x7A,0x7A,0x7A,0x7A,0x7A,0x7A,0x7A,
    0x7A,0x7A,0x7A,0x7A,0x7A,0x7A,0x7A,0x7A,0x7A,0x7A,0x7A,0x7A,0x7A,0x7A,0x7A,0x7A,
    0x7A,0x7A,0x7A,0x7A,0x7A,0x7A,0x7A,0x7A,0x7A,0x7A,0x7A,0x7A,0x7A,0x7A,0x7A,0x6E,
    0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,
    0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,
    0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9B,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,
    0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,
    0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,
    0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,
    0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,
    0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,
    0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,
    0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,
    0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,
    0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,
    0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,
    0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,
    0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,
    0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,
    0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,
    0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,
    0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,
    0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,
    0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,
    0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,
    0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,
    0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,
    0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,
    0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,
    0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,
    0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,
    0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,
    0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,
    0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,
    0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,
    0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,
    0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,
    0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,
    0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,
    0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,
    0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,
    0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,
    0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,
    0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,
    0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,
    0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,
    0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,
    0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,
    0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,
    0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,
    0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,
    0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,
    0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,
    0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,
    0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,
    0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,
    0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,
    0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,
    0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,
    0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,
    0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,
    0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,
    0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,
    0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,
    0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,
    0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,
    0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,
    0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,
    0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,
    0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,
    0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,
    0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,
    0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xA7,0xC0
};

static const OMX_U8 sVGA_CHP_BkFm[CHP_VGA_BLANK_FRAME_SIZE] =
{
    0x00,0x00,0x00,0x01,0x09,0x10,0x00,0x00,0x00,0x01,0x67,0x64,0x00,0x1F,0xAC,0xD2,
    0x02,0x80,0xF6,0x80,0x6D,0x0A,0x13,0x50,0x00,0x00,0x00,0x01,0x68,0xCE,0x06,0xE2,
    0xC0,0x00,0x00,0x00,0x01,0x65,0xB8,0x40,0x0A,0xBF,0xFF,0xE1,0xE8,0xA0,0x00,0x20,
    0x63,0xED,0x55,0xAD,0xAD,0xAD,0xAD,0xAD,0xAD,0xAD,0xAD,0xAD,0xAD,0xAD,0xAD,0xAD,
    0xAD,0xAD,0xAD,0xAD,0xAD,0xAD,0xAD,0xAD,0xAD,0xAD,0xAD,0xAD,0xAD,0xAD,0xAD,0xAD,
    0xAD,0xAD,0xAD,0xAD,0xAD,0xAD,0xAD,0xAD,0xAD,0x36,0xA9,0xE9,0xE9,0xE9,0xE9,0xE9,
    0xE9,0xE9,0xE9,0xE9,0xE9,0xE9,0xE9,0xE9,0xE9,0xE9,0xE9,0xE9,0xE9,0xE9,0xE9,0xE9,
    0xE9,0xE9,0xE9,0xE9,0xE9,0xE9,0xE9,0xE9,0xE9,0xE9,0xE9,0xE9,0xE9,0xE9,0xE9,0xE9,
    0xE9,0xE9,0xBA,0x7A,0x7A,0x7A,0x7A,0x7A,0x7A,0x7A,0x7A,0x7A,0x7A,0x7A,0x7A,0x7A,
    0x7A,0x7A,0x7A,0x7A,0x7A,0x7A,0x7A,0x7A,0x7A,0x7A,0x7A,0x7A,0x7A,0x7A,0x7A,0x7A,
    0x7A,0x7A,0x7A,0x7A,0x7A,0x7A,0x7A,0x7A,0x7A,0x7A,0x6E,0x9E,0x9E,0x9E,0x9E,0x9E,
    0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,
    0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,
    0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,
    0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,
    0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,
    0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,
    0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,
    0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,
    0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,
    0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,
    0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,
    0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,
    0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,
    0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,
    0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,
    0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,
    0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,
    0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,
    0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,
    0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,
    0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,
    0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,
    0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,
    0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,
    0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,
    0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,
    0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,
    0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,
    0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,
    0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,
    0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,
    0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,
    0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,
    0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,
    0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,
    0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,
    0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,
    0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,
    0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,
    0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,
    0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,
    0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,
    0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,
    0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,
    0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,
    0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,
    0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,
    0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,
    0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,
    0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,
    0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,
    0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,
    0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,
    0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,
    0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,
    0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,
    0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,
    0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,
    0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,
    0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,
    0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,
    0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,
    0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,
    0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,
    0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,
    0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,
    0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,0x9E,
    0x9E,0x9F
};

//This macro provides a single point exit from function on
//encountering any error
#define WFD_OMX_ERROR_CHECK(result,error) ({ \
    if(result!= OMX_ErrorNone) \
    {\
        WFDMMLOGE(error);\
        WFDMMLOGE1("due to %x", result);\
        goto EXIT;\
    }\
})

/*!*************************************************************************
 * @brief     CTOR
 *
 * @param[in] NONE
 *
 * @return    NONE
 *
 * @note
 **************************************************************************/
VideoSource::VideoSource():
    m_nNumBuffers(0),
    m_pHdcpOutBuffers(NULL),
    m_pVideoSourceOutputBuffers(NULL),
    m_pHDCPOutputBufQ(NULL),
    m_nMuxBufferCount(0),
    m_bStarted(OMX_FALSE),
    m_bPause(OMX_FALSE),
    m_pFrameDeliverFn(NULL),
    ionfd(-1),
    m_secureSession(OMX_FALSE),
    m_nFillerInFd(-1),
    m_nFillerOutFd(-1),
    m_hFillerInHandle(0),
    m_hFillerOutHandle(0),
    m_pFillerDataInPtr(NULL),
    m_pFillerDataOutPtr(NULL),
    m_bFillerNaluEnabled(OMX_TRUE),
    m_pVencoder(NULL),
    m_bDropVideoFrame(OMX_FALSE),
    m_bBlankFrameSupport(OMX_TRUE),
    m_pBlankFrame(NULL),
    m_nBlankFrameSize(0),
    m_pBlankFrameQ(NULL),
    m_bHandleStandby(OMX_FALSE),
    m_nMaxBlankFrames(0),
    m_nBlankFramesDelivered(0),
    m_nBlankFrameInFd(-1),
    m_hBlankFrameInHandle(0),
    m_pBlankFrameDataInPtr(NULL),
    m_nLastFrameTS(0)
{
    WFDMMLOGE("Creating VideoSource...");
    m_eVideoSrcState    = WFDMM_VideoSource_STATE_INIT;
    m_pHdcpHandle       = NULL;
    m_bHdcpSessionValid = OMX_FALSE;
    m_pHdcpOutBuffers   = NULL;
    /**---------------------------------------------------------------------
         Decision to encrypt non secure content or not is made by application
         or user based on the WFD config file
        ------------------------------------------------------------------------
       */
    int nVal;
    nVal = 0;
    // CHeck if Filler NALU is disabled
    getCfgItem(DISABLE_NALU_FILLER_KEY,&nVal);
    if(nVal == 1)
    {
        m_bFillerNaluEnabled = OMX_FALSE;
    }
#ifdef ENABLE_WFD_STATS
    memset(&wfdStats,0,sizeof(wfd_stats_struct));
    m_pStatTimer = NULL;
    wfdStats.bEnableWfdStat = OMX_TRUE;
    m_nDuration = 5000;

    if(0 != MM_Timer_Create((int) m_nDuration, 1, readStatTimerHandler, (void *)(this), &m_pStatTimer))
    {
        WFDMMLOGE("Creation of timer failed");
    }
#endif

    m_bEnableProfiling = WFDMMSourceStatistics::isProfilingEnabled();

    if(m_bEnableProfiling)
    {
        m_pWFDMMSrcStats = WFDMMSourceStatistics::getInstance();
    }

    m_pVencoder = MM_New(WFDMMSourceVideoEncode);

    if(!m_pVencoder)
    {
        WFDMMLOGE("Failed to create Video Encoder");
        return;
    }

    nVal = 1;
    getCfgItem(BLANK_FRAME_SUPPORT_KEY,&nVal);
    if(0 == nVal)
    {
        m_bBlankFrameSupport = OMX_FALSE;
        WFDMMLOGH("No support for blank frame");
    }

    memset(&m_sConfig,0,sizeof(m_sConfig));
}

/*!*************************************************************************
 * @brief     DTOR
 *
 * @param[in] NONE
 *
 * @return    NONE
 *
 * @note
 **************************************************************************/
VideoSource::~VideoSource()
{
    #ifdef ENABLE_WFD_STATS
    // WFD:STATISTICS -- start
    if(wfdStats.bEnableWfdStat && 0 != wfdStats.nStatCount)
    {
       MM_MSG_PRIO2(MM_STATISTICS, MM_PRIO_MEDIUM,
                   "WFD:STATISTICS: Average Roundtrip time of buffer \
                   in Userspace is =%llu ms Max time is =%llu ms",
                   wfdStats.nCumulativeStatTime/wfdStats.nStatCount,
                   wfdStats.nMaxStatTime);
    }
    if(m_pStatTimer != NULL)
    {
       MM_Timer_Release(m_pStatTimer);
    }
    // WFD:STATISTICS -- end
    #endif /* ENABLE_WFD_STATS */
    OMX_ERRORTYPE result = OMX_ErrorNone;
    int timeoutCnt=1000;/*timeout counter*/
    MM_MSG_PRIO(MM_GENERAL, MM_PRIO_MEDIUM, "WFDMMSourceVideoSource::~VideoSource()");

    if(m_pVencoder)
    {
        MM_Delete(m_pVencoder);
    }

    if(m_eVideoSrcState!=  WFDMM_VideoSource_STATE_IDLE)
    {
      while((m_eVideoSrcState !=  WFDMM_VideoSource_STATE_IDLE)&&timeoutCnt)
      {
        MM_Timer_Sleep(5);
        timeoutCnt--;
      }
    }

    ReleaseResources();

    if(m_pWFDMMSrcStats)
    {
        WFDMMSourceStatistics::deleteInstance();
        m_pWFDMMSrcStats = NULL;
    }

    WFDMMLOGH("~WFDMMSourceVideoSource completed");
}

/*!*************************************************************************
 * @brief     Configures the source
 *
 * @param[in] nFrames The number of frames to to play.
 * @param[in] nBuffers The number of buffers allocated for the session.
 * @param[in] pFrameDeliverFn Frame delivery callback.
 * @param[in] bFrameSkipEnabled frame skipping enabled
 * @param[in] nFrameSkipLimitInterval frame skipping time interval
 *
 * @return    OMX_ERRORTYPE
 *
 * @note
 **************************************************************************/
OMX_ERRORTYPE VideoSource::Configure(
    VideoEncStaticConfigType* pConfig,
    OMX_S32 nBuffers,
    FrameDeliveryFnType pFrameDeliverFn,
    eventHandlerType pEventHandlerFn,
    OMX_BOOL bFrameSkipEnabled,
    OMX_U64 nFrameSkipLimitInterval,
    OMX_U32 nModuleId,
    void *appData)
{
    (void)nBuffers;
    (void)bFrameSkipEnabled;
    (void)nFrameSkipLimitInterval;
    OMX_ERRORTYPE result = OMX_ErrorNone;
    if(!pConfig)
    {
      WFDMMLOGE("WFDMMSourceVideoSource::bad params");
      return OMX_ErrorBadParameter;
    }// if pConfig
    memcpy(&m_sConfig, pConfig, sizeof(m_sConfig));
    m_pEventHandlerFn = pEventHandlerFn;
    m_pFrameDeliverFn = pFrameDeliverFn;
    m_nModuleId       = nModuleId;
    m_appData         = appData;
    if(m_pHdcpHandle &&
      (m_pHdcpHandle->m_eHdcpSessionStatus == HDCP_STATUS_SUCCESS))
    {
        m_bHdcpSessionValid = OMX_TRUE;
    }
    if(m_eVideoSrcState==  WFDMM_VideoSource_STATE_INIT)
    {
        result = m_pVencoder->configure(pConfig,
                                        &VideoSourceEventHandlerCb,
                                        &VideoSourceFrameDelivery,
                                        m_bHdcpSessionValid,
                                        WFDMM_VENC_MODULE_ID,
                                        this);

        if(result!= OMX_ErrorNone)
        {
            WFDMMLOGE("Failed to configure encoder");
            return result;
        }

        result = CreateResources();

        if(result!= OMX_ErrorNone)
        {
            WFDMMLOGE("Failed to create Resources");
            return result;
        }

        if(!m_bHdcpSessionValid && m_bFillerNaluEnabled)
        {
            /*---------------------------------------------------------------------
             For non HDCP session
            -----------------------------------------------------------------------
            */
            m_pFillerDataOutPtr = (unsigned char*)sFillerNALU;
        }

        WFDMMLOGH("WFDMMSourceVideoSource::Allocated all resources");
        m_eVideoSrcState = WFDMM_VideoSource_STATE_IDLE;
    }
    return result;
}

/*!*************************************************************************
 * @brief     Timer handler for reading statistics flag from command line
 *
 * @param[in] ptr Reference to the current instance
 *
 * @return    NONE
 *
 * @note
 **************************************************************************/
#ifdef ENABLE_WFD_STATS
// TODO: Needs to be moved to appropriate location
void VideoSource::readStatTimerHandler(void* ptr)
{
    if(!ptr)
    {
        return;
    }
    VideoSource* pMe= (VideoSource*)ptr;
    char szTemp[PROPERTY_VALUE_MAX];
    if(property_get("persist.debug.enable_wfd_stats",szTemp,NULL)<0)
    {
        WFDMMLOGE("Failed to read persist.debug.enable_wfd_stats");
        return;
    }
    if(strcmp(szTemp,"false")==0)
    {
        memset(&(pMe->wfdStats),0, sizeof(wfdStats));
        pMe->wfdStats.bEnableWfdStat = OMX_FALSE;
    }
    else
    {
        pMe->wfdStats.bEnableWfdStat = OMX_TRUE;
    }
}
#endif

/*=============================================================================

         FUNCTION:          CreateResources

         DESCRIPTION:
*//**       @brief          responisble for resource allocation
                            for VideoSource
*//**
@par     DEPENDENCIES:
                            Should be called once configure has been called.
*//*
         PARAMETERS:
*//**       @param          None

*//*     RETURN VALUE:
*//**       @return
                            OMX_ErrorNone or other Error
@par     SIDE EFFECTS:
                            None
*//*=========================================================================*/
OMX_ERRORTYPE VideoSource::CreateResources()
{
    OMX_ERRORTYPE result = OMX_ErrorNone;

    m_nNumBuffers = m_pVencoder->GetNumOutBuf();

    if(m_bBlankFrameSupport)
    {

        m_nMaxBlankFrames = MAX_BLANKING_PERIOD/(1000/m_sConfig.nFramerate);

        /*---------------------------------------------------------------------
         Select the blank frame to be sent based on negotiated profile to not
         irk rigid decoders
        -----------------------------------------------------------------------
        */

        if(!m_pBlankFrame)
        {

            if(m_sConfig.nProfile == WFD_H264_PROFILE_CONSTRAINED_BASE)
            {
                m_pBlankFrame = const_cast<OMX_U8*>(sVGA_CBP_BkFm);
                m_nBlankFrameSize = static_cast<OMX_U32>(sizeof(sVGA_CBP_BkFm));
            }
            else if(m_sConfig.nProfile == WFD_H264_PROFILE_CONSTRAINED_HIGH)
            {
                m_pBlankFrame = const_cast<OMX_U8*>(sVGA_CHP_BkFm);
                m_nBlankFrameSize = static_cast<OMX_U32>(sizeof(sVGA_CHP_BkFm));
            }

            WFDMMLOGH2("Done with blank frame of size %ld for profile %ld",
                m_nBlankFrameSize,m_sConfig.nProfile);
        }

        /*-------------------------------------------------------------
         Maintain a queue for supplying blank frames to MUX
        ---------------------------------------------------------------
        */
        if(!m_pBlankFrameQ)
        {
            m_pBlankFrameQ = MM_New_Args(SignalQueue,
                        (m_nMaxBlankFrames, sizeof(OMX_BUFFERHEADERTYPE*)));

            if(!m_pBlankFrameQ)
            {
                WFDMMLOGE("Failed to allocate blank frame Q!");
                return OMX_ErrorInsufficientResources;
            }

            for(OMX_S32 i = 0; i< m_nMaxBlankFrames; i++)
            {
                OMX_BUFFERHEADERTYPE* pBufferHdr = NULL;
                buff_hdr_extra_info* pExtraInfo = NULL;

                pBufferHdr = static_cast<OMX_BUFFERHEADERTYPE*>
                             (MM_Malloc(sizeof(*pBufferHdr)));

                if(!pBufferHdr)
                {
                    WFDMMLOGE("Failed to allocate blank frame buffers!");
                    return OMX_ErrorInsufficientResources;
                }

                memset(pBufferHdr,0,sizeof(*pBufferHdr));

                pExtraInfo = static_cast<buff_hdr_extra_info*>
                             (MM_Malloc(sizeof(*pExtraInfo)));

                if(!pExtraInfo)
                {
                    MM_Free(pBufferHdr);
                    WFDMMLOGE("Failed to allocate extaInfo!");
                    return OMX_ErrorInsufficientResources;
                }

                pExtraInfo->bBlackFrame         = OMX_FALSE;

                pBufferHdr->pPlatformPrivate    = static_cast<OMX_PTR>
                                                        (pExtraInfo);

                /*-------------------------------------------------------------
                 Fixed data from our end using the reference blank frames
                ---------------------------------------------------------------
                */
                pBufferHdr->nSize               = sizeof(*pBufferHdr);
                pBufferHdr->pBuffer             = m_pBlankFrame;
                pBufferHdr->nInputPortIndex     = 0;
                pBufferHdr->nOffset             = 0;
                pBufferHdr->nOutputPortIndex    = 1;
                pBufferHdr->nVersion.nVersion   = static_cast<OMX_U32>
                                                (OMX_SPEC_VERSION);
                pBufferHdr->nAllocLen           = m_nBlankFrameSize;

                /*-------------------------------------------------------------
                 Done with this OMX_BUFFERHEADER, add it to the Blank Frame Q
                ---------------------------------------------------------------
                */
                WFDMMLOGH1("Pushing %p to Blank Frame Q",pBufferHdr);

                m_pBlankFrameQ->Push(&pBufferHdr,sizeof(pBufferHdr));
            }
        }

    }

    if(m_bHdcpSessionValid)
    {
        /*---------------------------------------------------------------------
         HDCP handle is valid which implies HDCP connection has gone through.Now
         that encoder module has been configured, go ahead and allocate the
         resources required for HDCP.
        -----------------------------------------------------------------------
        */
        result = AllocateHDCPResources();
        if(result != OMX_ErrorNone)
        {
            WFDMMLOGE("Failed to allocate HDCP resources");
            return result;
        }
    }

    return result;
}

/*=============================================================================

         FUNCTION:          ReleaseResources

         DESCRIPTION:
*//**       @brief          Responisble for resource deallocation for VideoSource
*//**
@par     DEPENDENCIES:
                            Should be called once Encoder module is deleted.
*//*
         PARAMETERS:
*//**       @param          None

*//*     RETURN VALUE:
*//**       @return
                            OMX_ErrorNone or other Error
@par     SIDE EFFECTS:
                            None
*//*=========================================================================*/
OMX_ERRORTYPE VideoSource::ReleaseResources()
{
    OMX_ERRORTYPE result = OMX_ErrorNone;

    if(m_bHdcpSessionValid)
    {
        DeallocateHDCPResources();
    }

    if(m_pBlankFrameQ)
    {

        /*---------------------------------------------------------------------
          Wait for all blank frames to return from MUX, if any
        -----------------------------------------------------------------------
        */
        while(m_pBlankFrameQ->GetSize() != m_nMaxBlankFrames)
        {
            WFDMMLOGH1("Waiting for %ld blank frames from MUX",
                      m_nNumBuffers - m_pBlankFrameQ->GetSize());
            MM_Timer_Sleep(2);
        }

        while(m_pBlankFrameQ->GetSize())
        {
            OMX_BUFFERHEADERTYPE* pBuffHdr = NULL;
            m_pBlankFrameQ->Pop(&pBuffHdr, sizeof(pBuffHdr),100);

            if(pBuffHdr)
            {
                if(pBuffHdr->pPlatformPrivate)
                {
                    MM_Free(pBuffHdr->pPlatformPrivate);
                }
                WFDMMLOGH1("Deleted %p from Blank Frame Q",pBuffHdr);
                MM_Free(pBuffHdr);
            }
        }

        WFDMMLOGH("Done with buffers for Blank Frame Q");

        MM_Delete(m_pBlankFrameQ);
        m_pBlankFrameQ = NULL;
    }

    if(m_pBlankFrame)
    {
        m_pBlankFrame = NULL;
    }

    return result;
}


/*=============================================================================

         FUNCTION:          AllocateHDCPResources

         DESCRIPTION:
*//**       @brief          Responisble for HDCP specific resource allocation
                            for VideoSource
*//**
@par     DEPENDENCIES:
                            Should be called once Encoder module is configured.
*//*
         PARAMETERS:
*//**       @param          None

*//*     RETURN VALUE:
*//**       @return
                            OMX_ErrorNone or other Error
@par     SIDE EFFECTS:
                            None
*//*=========================================================================*/

OMX_ERRORTYPE VideoSource::AllocateHDCPResources()
{
    OMX_ERRORTYPE result = OMX_ErrorNone;
    WFDMMLOGE("AllocateHDCPResources");
    if(!m_bHdcpSessionValid)
    {
        WFDMMLOGE("HDCP session is not established");
        return OMX_ErrorInvalidState;
    }

    if(!m_pVencoder)
    {
        WFDMMLOGE("Invalid encoder component");
        return OMX_ErrorBadParameter;
    }

    /*-------------------------------------------------------------------------
     Both output buffers and ION buffers for HDCP should be same in number
    ---------------------------------------------------------------------------
    */

    /*--------------------------------------------------------------------------
     Create Q for holding OMX BufferHeaders in case of HDCP session which will
     be delivered to MUX
    ---------------------------------------------------------------------------
    */

    OMX_S32 buffer_size = WFDMM_VIDEO_SRC_ION_MEM_LIMIT;
    OMX_BUFFERHEADERTYPE** outputBuffers = GetBuffers();

    output_metabuffer *meta_buf = (output_metabuffer *)((outputBuffers[0])->pBuffer);
    native_handle_t* handle = meta_buf->nh;

    if(!handle || !(handle->numInts >= 3 && handle->numFds >= 1))
    {
         WFDMMLOGE("Invalid parameters from encoder");
         if(handle)
         {
             WFDMMLOGE2("Handle has %d fds and %d ints",
              handle->numFds,handle->numInts);
         }
         else
         {
             WFDMMLOGE("Native handle is null!!!");
         }
    }
    else
    {
         //Access the native handle to extract the alloc size
         buffer_size = handle->data[3];
    }

    WFDMMLOGE1("buffer_size = %d",buffer_size);

    m_pHDCPOutputBufQ = MM_New_Args(SignalQueue,(10, sizeof(OMX_BUFFERHEADERTYPE*)));
    if(!m_pHDCPOutputBufQ)
    {
        result = OMX_ErrorInsufficientResources;
        WFD_OMX_ERROR_CHECK(result,
            "Could not create HDCPOutputBufQ");
    }

    /*-----------------------------------------------------------------
     Create the array of OMX BufferHeaders in case of HDCP session which
     will be delivered to MUX.
    -------------------------------------------------------------------
    */

    m_pVideoSourceOutputBuffers = MM_New_Array(OMX_BUFFERHEADERTYPE*,m_nNumBuffers);
    if(!m_pVideoSourceOutputBuffers)
    {
        result = OMX_ErrorInsufficientResources;
        WFD_OMX_ERROR_CHECK(result,
            "Could not allocate VideoSourceOutputBufferHeaders");
    }

    /*-----------------------------------------------------------------
     Allocate ION buffers for HDCP output buffers
    -------------------------------------------------------------------
    */

    m_pHdcpOutBuffers = (struct buffer*)calloc(m_nNumBuffers,sizeof(buffer));
    if(!m_pHdcpOutBuffers)
    {
        result =  OMX_ErrorInsufficientResources;
        WFD_OMX_ERROR_CHECK(result,"Failed to allocate HDCP output buffers");
    }

    ionfd = open("/dev/ion",  O_RDONLY);
    if (ionfd < 0)
    {
        result = OMX_ErrorInsufficientResources;
        WFD_OMX_ERROR_CHECK(result,"Failed to open ion device");
    }

    WFDMMLOGE1("Opened ion device = %d\n", ionfd);

    for (int i = 0; i < m_nNumBuffers; i++)
    {

        buff_hdr_extra_info* pExtraInfo = NULL;
        /*-----------------------------------------------------------------
         Allocate OMX BufferHeaders
        -----------------------------------------------------------
        */
        m_pVideoSourceOutputBuffers[i] = (OMX_BUFFERHEADERTYPE* )\
                            MM_Malloc(sizeof(OMX_BUFFERHEADERTYPE));

        if(!m_pVideoSourceOutputBuffers[i])
        {
            result = OMX_ErrorInsufficientResources;
            WFD_OMX_ERROR_CHECK(result,
                            "Could not allocate VideoSourceOutputBuffers");
        }


        pExtraInfo = static_cast<buff_hdr_extra_info*>
                            (MM_Malloc(sizeof(*pExtraInfo)));

        if(!pExtraInfo)
        {
            m_pVideoSourceOutputBuffers[i]->pPlatformPrivate = NULL;
            result = OMX_ErrorInsufficientResources;
            WFD_OMX_ERROR_CHECK(result,
                               "Could not allocate ExtraInfo");
        }

        /*-----------------------------------------------------------------
         Populate PlatformPrivate with extra info to be populated later
         ------------------------------------------------------------------
        */

        m_pVideoSourceOutputBuffers[i]->pPlatformPrivate = pExtraInfo;

        /*-----------------------------------------------------------------
         Nullify appPrivate because this will be populated with ION buffer
         ------------------------------------------------------------------
        */
        m_pVideoSourceOutputBuffers[i]->pAppPrivate = NULL;

        /*-----------------------------------------------------------------
         Nullify outputPortPrivate because this will be populated with OMX
         BufferHeader received from encoder/from the Blank Frame Q
         ------------------------------------------------------------------
        */
        m_pVideoSourceOutputBuffers[i]->pOutputPortPrivate = NULL;

        /*-----------------------------------------------------------------
         Allocate ION memory for the HDCP output buffers
         ------------------------------------------------------------------
        */

        int memfd = -1;
        if(buffer_size > WFDMM_VIDEO_SRC_ION_MEM_LIMIT)
        {
            buffer_size = WFDMM_VIDEO_SRC_ION_MEM_LIMIT;
        }
        memfd = allocate_ion_mem((unsigned int)buffer_size, &(m_pHdcpOutBuffers[i].handle), ionfd,
                    ION_QSECOM_HEAP_ID,(OMX_BOOL) OMX_FALSE);
        if(memfd < 0)
        {
            WFDMMLOGE("Failed to allocate ion memory for HDCP buffers");
            return OMX_ErrorInsufficientResources;
        }

        WFDMMLOGE1("memfd = %d ", memfd);

        m_pHdcpOutBuffers[i].start = (unsigned char *)
        mmap(NULL, buffer_size, PROT_READ | PROT_WRITE, MAP_SHARED, memfd, 0);
        m_pHdcpOutBuffers[i].length = buffer_size;
        m_pHdcpOutBuffers[i].fd = memfd;
        m_pHdcpOutBuffers[i].offset = 0;
        m_pHdcpOutBuffers[i].index = i;
        WFDMMLOGH3("allocated buffer(%p) of size = %ld, fd = %d",
                m_pHdcpOutBuffers[i].start, buffer_size, memfd);
        if (m_pHdcpOutBuffers[i].start == MAP_FAILED)
        {
            WFDMMLOGE("Could not allocate ION buffers");
            return OMX_ErrorInsufficientResources;
        }

        m_pVideoSourceOutputBuffers[i]->nSize             =
                        sizeof(*(m_pVideoSourceOutputBuffers[i]));

        m_pVideoSourceOutputBuffers[i]->nVersion.nVersion =
                        static_cast<OMX_U32>(OMX_SPEC_VERSION);

        m_pVideoSourceOutputBuffers[i]->pBuffer           =
                        reinterpret_cast<OMX_U8*>(m_pHdcpOutBuffers[i].start);

        m_pVideoSourceOutputBuffers[i]->nAllocLen         = buffer_size;

        m_pVideoSourceOutputBuffers[i]->nInputPortIndex   = 0;

        m_pVideoSourceOutputBuffers[i]->nOutputPortIndex  = 1;

        m_pVideoSourceOutputBuffers[i]->pAppPrivate       =
                        reinterpret_cast<OMX_U8*>(&m_pHdcpOutBuffers[i]);

        /*-----------------------------------------------------------------
         Push the allocated buffer to the Q now that all its fields are
         properly polulated
        -----------------------------------------------------------------
        */

        m_pHDCPOutputBufQ->Push(&(m_pVideoSourceOutputBuffers[i]),
                                sizeof(m_pVideoSourceOutputBuffers[i]));
    }//for loop allocate buffer on ion memory

    if(m_bFillerNaluEnabled)
    {
      WFDMMLOGE("Allocate filler Nalu buffers");

      m_nFillerInFd = allocate_ion_mem(FILLER_ION_BUF_SIZE, &m_hFillerInHandle,
                                       ionfd, ION_QSECOM_HEAP_ID, OMX_FALSE);

      if(m_nFillerInFd <= 0)
      {
          WFDMMLOGE("Failed to allocate In FillerNalu ION buffer");
      }
      else
      {
          m_pFillerDataInPtr = (unsigned char*)
                         mmap(NULL, FILLER_ION_BUF_SIZE, PROT_READ | PROT_WRITE,
                              MAP_SHARED, m_nFillerInFd, 0);
          if(m_pFillerDataInPtr == NULL)
          {
            WFDMMLOGE("Failed to allocate In FillerNalu buffer mmap");
          }
          else
          {
            //Initialize the input buffer with fixed Filler NALU
            memcpy(m_pFillerDataInPtr, sFillerNALU, sizeof(sFillerNALU));
          }
      }

      m_nFillerOutFd = allocate_ion_mem(FILLER_ION_BUF_SIZE,
        &m_hFillerOutHandle, ionfd, ION_QSECOM_HEAP_ID, OMX_FALSE);

      if(m_nFillerOutFd <= 0)
      {
          WFDMMLOGE("Failed to allocate Out FillerNalu ION buffer");
      }
      else
      {
          m_pFillerDataOutPtr = (unsigned char*)
                       mmap(NULL, FILLER_ION_BUF_SIZE, PROT_READ | PROT_WRITE,
                                                MAP_SHARED, m_nFillerOutFd, 0);
          if(m_pFillerDataOutPtr == NULL)
          {
            WFDMMLOGE("Failed to allocate In FillerNalu buffer mmap");
          }
      }
    }

    if(m_bBlankFrameSupport)
    {
        WFDMMLOGH("Allocate Blank frame i/p ION handle");

        m_nBlankFrameInFd = allocate_ion_mem(FILLER_ION_BUF_SIZE,
            &m_hBlankFrameInHandle,ionfd, ION_QSECOM_HEAP_ID, OMX_FALSE);

        if(m_nBlankFrameInFd <= 0)
        {
            result = OMX_ErrorInsufficientResources;
            WFD_OMX_ERROR_CHECK(result,
                "Failed to allocate I/P BlankFrame ION buffer");
        }
        else
        {
            m_pBlankFrameDataInPtr = (unsigned char*)
                    mmap(NULL, FILLER_ION_BUF_SIZE, PROT_READ | PROT_WRITE,
                        MAP_SHARED, m_nBlankFrameInFd, 0);
            if(m_pBlankFrameDataInPtr == NULL)
            {
                result = OMX_ErrorInsufficientResources;
                WFD_OMX_ERROR_CHECK(result,
                    "Failed to allocate I/P BlankFrame buffer mmap");
            }
            else
            {
                //Initialize the input buffer with fixed Blank frame data
                memcpy(m_pBlankFrameDataInPtr, m_pBlankFrame,
                    static_cast<size_t>(m_nBlankFrameSize));
            }
        }

    }

EXIT:
    return result;
}

/*=============================================================================

         FUNCTION:          DeallocateHDCPResources

         DESCRIPTION:
*//**       @brief          Responisble for HDCP specific resource de-allocation
                            for VideoSource
*//**
@par     DEPENDENCIES:
                            Should be called once Encoder module is configured.
*//*
         PARAMETERS:
*//**       @param          None

*//*     RETURN VALUE:
*//**       @return
                            OMX_ErrorNone or other Error
@par     SIDE EFFECTS:
                            None
*//*=========================================================================*/

OMX_ERRORTYPE VideoSource::DeallocateHDCPResources()
{
    if(!m_bHdcpSessionValid)
    {
        return OMX_ErrorNone;
    }
    WFDMMLOGE("Deallocating HDCP resources");

    struct ion_handle_data handle_data;
    int ret = -1;

    if(m_pVideoSourceOutputBuffers)
    {
        for (unsigned int i = 0; i < (unsigned int)m_nNumBuffers; i++)
        {
            if(m_pVideoSourceOutputBuffers[i])
            {
                if((m_pHdcpHandle != NULL) &&(m_pHdcpOutBuffers != NULL))
                {
                    /*---------------------------------------------------------
                     Unmap the memory
                    -----------------------------------------------------------
                    */
                    if(m_pHdcpOutBuffers[i].start != NULL)
                    {
                        if (munmap(m_pHdcpOutBuffers[i].start, m_pHdcpOutBuffers[i].length) == -1)
                        {
                            WFDMMLOGE2("error in munmap at idx %d :%s",
                                i,strerror(errno));
                        }
                        m_pHdcpOutBuffers[i].start = NULL;
                        m_pHdcpOutBuffers[i].length = 0;
                    }

                    /*---------------------------------------------------------
                     Free the ION handle
                    -----------------------------------------------------------
                    */

                    if(m_pHdcpOutBuffers[i].handle != 0)
                    {
                        handle_data.handle = m_pHdcpOutBuffers[i].handle;
                        ret = ioctl(ionfd, ION_IOC_FREE, &handle_data);
                        if(ret)
                        {
                            WFDMMLOGE2("Error in freeing handle at idx %d : %d",
                                i,ret);
                        }
                        m_pHdcpOutBuffers[i].handle = 0;
                    }

                    /*---------------------------------------------------------
                     Close the fd
                    -----------------------------------------------------------
                    */

                    if(m_pHdcpOutBuffers[i].fd > 0)
                    {
                        close(m_pHdcpOutBuffers[i].fd);
                        WFDMMLOGH2("closing hdcp ion fd = %d ret type = %d",
                                    m_pHdcpOutBuffers[i].fd, ret);
                        m_pHdcpOutBuffers[i].fd = -1;
                    }
                }

                if(m_pVideoSourceOutputBuffers[i]->pPlatformPrivate)
                {
                    MM_Free(m_pVideoSourceOutputBuffers[i]->pPlatformPrivate);
                    m_pVideoSourceOutputBuffers[i]->pPlatformPrivate = NULL;
                }

                MM_Free(m_pVideoSourceOutputBuffers[i]);
                m_pVideoSourceOutputBuffers[i] = NULL;

            }
        }

        if(m_nBlankFrameInFd> 0)
        {
            if(m_pBlankFrameDataInPtr)
            {
                munmap(m_pBlankFrameDataInPtr, FILLER_ION_BUF_SIZE);
                m_pBlankFrameDataInPtr = NULL;
            }

            if(m_hBlankFrameInHandle!= 0)
            {
                handle_data.handle = m_hBlankFrameInHandle;
                ret = ioctl(ionfd, ION_IOC_FREE, &handle_data);
                m_hBlankFrameInHandle = 0;
            }

            close(m_nBlankFrameInFd);
            WFDMMLOGH2("closing blank frame ion fd = %d ret type = %d \n",
                        m_nBlankFrameInFd, ret);
            m_nBlankFrameInFd = -1;
        }

        if(m_nFillerInFd > 0)
        {
            if(m_pFillerDataInPtr)
            {
                munmap(m_pFillerDataInPtr, FILLER_ION_BUF_SIZE);
                m_pFillerDataInPtr = NULL;
            }

            if(m_hFillerInHandle != 0)
            {
                handle_data.handle = m_hFillerInHandle;
                ret = ioctl(ionfd, ION_IOC_FREE, &handle_data);
                m_hFillerInHandle = 0;
            }

            close(m_nFillerInFd);
            WFDMMLOGH2("closing hdcp filler ion fd = %d ret type = %d \n",
                        m_nFillerInFd, ret);
            m_nFillerInFd = -1;
        }

        if(m_nFillerOutFd > 0)
        {
            if(m_pFillerDataOutPtr)
            {
                munmap(m_pFillerDataOutPtr, FILLER_ION_BUF_SIZE);
                m_pFillerDataOutPtr = NULL;
            }

            if(m_hFillerOutHandle != 0)
            {
                handle_data.handle = m_hFillerOutHandle;
                ret = ioctl(ionfd, ION_IOC_FREE, &handle_data);
                m_hFillerOutHandle = 0;
            }

            close(m_nFillerOutFd);
            WFDMMLOGH2("closing hdcp filler ion fd = %d ret type = %d \n",
                 m_nFillerOutFd, ret);
            m_nFillerOutFd = -1;
        }

        if(m_pHdcpOutBuffers)
        {
            MM_Free(m_pHdcpOutBuffers);
            m_pHdcpOutBuffers = NULL;
        }

        MM_Delete_Array(m_pVideoSourceOutputBuffers);
        m_pVideoSourceOutputBuffers = NULL;
    }

    if(m_pHDCPOutputBufQ)
    {
        MM_Delete(m_pHDCPOutputBufQ);
        m_pHDCPOutputBufQ = NULL;
    }

    if(ionfd > 0)
    {
        WFDMMLOGH1("closing ion fd = %d",ionfd);
        close(ionfd);
        ionfd = -1;
    }

    return OMX_ErrorNone;
}

/*!*************************************************************************
 * @brief     Start video source
 *
 * @param[in] NONE
 *
 * @return    OMX_ERRORTYPE
 *
 * @note      NONE
 **************************************************************************/
OMX_ERRORTYPE VideoSource::Start()
{
    OMX_ERRORTYPE result = OMX_ErrorNone;
    m_bHandleStandby = OMX_FALSE;
    m_nBlankFramesDelivered = 0;
    // make sure we've been configured
    MM_MSG_PRIO(MM_GENERAL, MM_PRIO_HIGH,
                "WFDMMSourceVideoSource::SourceThread Start");
    if(!m_pVencoder)
    {
        result = OMX_ErrorUndefined;
        WFD_OMX_ERROR_CHECK(result,"Failed to start!");
    }
    if(m_eVideoSrcState == WFDMM_VideoSource_STATE_PLAY)
    {
        WFDMMLOGH("Already in Playing, Ignore");
    }
    else if(m_eVideoSrcState == WFDMM_VideoSource_STATE_PAUSE)
    {
        result = m_pVencoder->Resume();
        WFD_OMX_ERROR_CHECK(result,"Failed to start!");
    }
    else if(m_eVideoSrcState == WFDMM_VideoSource_STATE_IDLE)
    {
        result = m_pVencoder->Start();
        WFD_OMX_ERROR_CHECK(result,"Failed to start!");
        if(m_pEventHandlerFn)
        {
            m_pEventHandlerFn( m_appData, m_nModuleId,
               WFDMMSRC_VIDEO_SESSION_START, OMX_ErrorNone, 0);
        }
    }
    else
    {
        result = OMX_ErrorInvalidState;
        WFD_OMX_ERROR_CHECK(result,"Failed to start!");
    }
    m_eVideoSrcState = WFDMM_VideoSource_STATE_PLAY;

EXIT:
    return result;
}

/*!*************************************************************************
 * @brief     Pause video source
 *
 * @param[in] NONE
 *
 * @return    OMX_ERRORTYPE
 *
 * @note      NONE
 **************************************************************************/
OMX_ERRORTYPE VideoSource::Pause()
{
    OMX_ERRORTYPE result = OMX_ErrorNone;

    if(m_eVideoSrcState == WFDMM_VideoSource_STATE_PLAY)
    {
        m_eVideoSrcState = WFDMM_VideoSource_STATE_PAUSING;
        if(m_pVencoder)
        {
            result = m_pVencoder->Pause();
            WFD_OMX_ERROR_CHECK(result,"Failed to pause!");
        }
        m_eVideoSrcState = WFDMM_VideoSource_STATE_PAUSE;
    }
    else
    {
        result = OMX_ErrorInvalidState;
        WFD_OMX_ERROR_CHECK(result,"Failed to pause!");
    }
EXIT:
    return result;
}

/*!*************************************************************************
 * @brief     Finish video source
 *
 * @param[in] NONE
 *
 * @return    OMX_ERRORTYPE
 *
 * @note      NONE
 **************************************************************************/
OMX_ERRORTYPE VideoSource::Finish()
{
    WFDMMLOGH("VideoSource Finish");
    m_eVideoSrcState = WFDMM_VideoSource_STATE_STOPPING;
    if(m_pVencoder)
    {
        WFDMMLOGH("Calling Encoder Stop");
        m_pVencoder->Stop();
    }
    m_eVideoSrcState = WFDMM_VideoSource_STATE_IDLE;
    return OMX_ErrorNone;
}

/*!*************************************************************************
 * @brief     Get buffer header
 *
 * @param[in] NONE
 *
 * @return    OMX_BUFFERHEADERTYPE**
 *
 * @note      NONE
 **************************************************************************/
OMX_BUFFERHEADERTYPE** VideoSource::GetBuffers()
{
    OMX_BUFFERHEADERTYPE** ppBuffers;
    if(m_pVencoder)
    {
        ppBuffers = m_pVencoder->GetOutputBuffHdrs();
        MM_MSG_PRIO(MM_GENERAL, MM_PRIO_HIGH,
                    "WFDMMSourceVideoSource::GetBuffers success");
    }
    else
    {
        ppBuffers = NULL;
    }
    return ppBuffers;
}

/*!*************************************************************************
 * @brief     Get number of buffer
 *
 * @param[in] NONE
 * @param[out]pnBuffers
 *
 * @return    OMX_ERRORTYPE
 *
 * @note      NONE
 **************************************************************************/
OMX_ERRORTYPE VideoSource::GetOutNumBuffers(
    OMX_U32 nPortIndex, OMX_U32* pnBuffers)
{
    if(nPortIndex != 0)
    {
        return OMX_ErrorBadPortIndex;
    }
    if(!pnBuffers)
    {
        return OMX_ErrorBadParameter;
    }
    if(m_pVencoder)
    {
        *pnBuffers = m_pVencoder->GetNumOutBuf();
        MM_MSG_PRIO1(MM_GENERAL, MM_PRIO_HIGH,
                 "WFDMMSourceVideoSource::GetOutNumBuffers = %ld",
                 *pnBuffers);
    }
    return OMX_ErrorNone;
}

/*!*************************************************************************
 * @brief     Changes the frame rate
 *            The frame rate will take effect immediately.
 * @param[in] nFramerate New frame rate
 *
 * @return    OMX_ERRORTYPE
 *
 * @note
 **************************************************************************/
OMX_ERRORTYPE VideoSource::ChangeFrameRate(OMX_S32 nFramerate)
{
    OMX_ERRORTYPE result = OMX_ErrorNone;
    MM_MSG_PRIO1(MM_GENERAL, MM_PRIO_MEDIUM,
                 "WFDMMSourceVideoSource::ChangeFrameRate %ld", nFramerate);
    return result;
}

/*!*************************************************************************
 * @brief     Changes the bit rate
 *            The bit rate will take effect immediately.
 *
 * @param[in] nBitrate The new bit rate.
 *
 * @return    OMX_ERRORTYPE
 * @note      None
 **************************************************************************/
OMX_ERRORTYPE VideoSource::ChangeBitrate(OMX_S32 nBitrate)
{
    OMX_ERRORTYPE result = OMX_ErrorNone;
    MM_MSG_PRIO1(MM_GENERAL, MM_PRIO_MEDIUM,
                "WFDMMSourceVideoSource::ChangeBitrate %ld", nBitrate);
    if(nBitrate > 0)
    {
      {
        if(m_pVencoder)
        {
          m_pVencoder->ChangeBitrate(nBitrate);
        }
      }
    }// if nBitRate
    return result;
}

/*!*************************************************************************
 * @brief     Request Intra VOP
 *
 * @param[in] NONE
 *
 * @return    OMX_ERRORTYPE
 *
 * @note      NONE
 **************************************************************************/
OMX_ERRORTYPE VideoSource::RequestIntraVOP()
{
    OMX_ERRORTYPE result = OMX_ErrorNone;
    MM_MSG_PRIO(MM_GENERAL, MM_PRIO_MEDIUM,
                "WFDMMSourceVideoSource::RequestIntraVOP");
    if(m_pVencoder)
    {
        result = m_pVencoder->RequestIntraVOP();
    }
    return result;
}

/*=============================================================================

         FUNCTION:          HandleStandby

         DESCRIPTION:
*//**       @brief          Responisble for performing actions to handle
                            standby from upper layers
*//**
@par     DEPENDENCIES:
                            Should be called once Encoder module is configured.
*//*
         PARAMETERS:
*//**       @param          None

*//*     RETURN VALUE:
*//**       @return
                            OMX_ErrorNone or other Error
@par     SIDE EFFECTS:
                            None
*//*=========================================================================*/
OMX_ERRORTYPE VideoSource::HandleStandby()
{
    OMX_ERRORTYPE result = OMX_ErrorNone;
    if(OMX_TRUE == m_bBlankFrameSupport)
    {
        OMX_TICKS frameInterval = static_cast<OMX_TICKS>
            (1000000/m_sConfig.nFramerate);
        unsigned long sleepInterval = static_cast<unsigned long>
            (frameInterval/1000);//in ms

        WFDMMLOGH2("Blank Frame Interval = %lld us, Blank Frames sent = %d",
            frameInterval, m_nBlankFramesDelivered);

        m_bHandleStandby = OMX_TRUE; //Force dropping of encoder ouptut
        /*----------------------------------------------------------------------
         After setting the flag there might be chances of a context switch while
         encoder was delivering a frame to MUX and fails to read the flag and
         proceeds to deliver the frame to MUX. So allow for a context switch to
         ensure that no more frames from encoder are sent to MUX in any case,
         else it might result in a blank frame interspersed with an actual
         encoded frame being rendered on sink (a catastrophe by any standard!).
         This is to avoid unecessary synchronization between HandleStandby() and
         FrameDeliveryFn().
         ------------------------------------------------------------------
        */

        MM_Timer_Sleep(10);

        if(m_eVideoSrcState == WFDMM_VideoSource_STATE_PLAY)
        {
            for(int i = 0 ; i< m_nMaxBlankFrames &&
                m_eVideoSrcState == WFDMM_VideoSource_STATE_PLAY; i++)
            {
                OMX_ERRORTYPE res = GenerateBlankFrame
                    (m_nLastFrameTS + (i+1)*frameInterval);
                if(res != OMX_ErrorNone)
                {
                    /*---------------------------------------------------------
                     Can't really do much here if it fails except logging. Also
                     we need to bail out from here since we don't want to be
                     stuck for MUX to deliver the frames because it never will,
                     a scenarios above layers need not tolerate.
                     ----------------------------------------------------------
                    */
                    WFDMMLOGE1("GenerateBlankFrame failed due to %x", res);
                    return OMX_ErrorUndefined;
                }
                MM_Timer_Sleep(sleepInterval);
            }
            while(m_nBlankFramesDelivered < m_nMaxBlankFrames &&
                m_eVideoSrcState == WFDMM_VideoSource_STATE_PLAY)
            {
                MM_Timer_Sleep(10);
            }
            WFDMMLOGH1("Done Handling standby by sending %d blank frames",
                m_nBlankFramesDelivered);
        }
        else
        {
            WFDMMLOGE("Can't generate blank frame in non-PLAY state");
            return OMX_ErrorIncorrectStateOperation;
        }
    }
    else
    {
        WFDMMLOGH("Blank frame generation not supported");
        result = OMX_ErrorUnsupportedSetting;
    }
    return result;
}

/*=============================================================================

         FUNCTION:            VideoSourceFrameDelivery

         DESCRIPTION:
*//**       @brief           Function responsible for sending frames to MUX

*//**
@par     DEPENDENCIES:
                            None
*//*
         PARAMETERS:
*//**       @param[in]      pBufferHdr   buffer fhaving encoded data

     *         @param[in]      pThis          pointer to get current instance

*//*     RETURN VALUE:
*//**       @return
                            OMX_ErrorNone or other Error
@par     SIFE EFFECTS:
                            None
*//*=========================================================================*/
void VideoSource::VideoSourceFrameDelivery(
    OMX_BUFFERHEADERTYPE* pEncBufferHdr,
    void* pThis)
{
    if(!pEncBufferHdr || !pThis)
    {
        WFDMMLOGE(" Invalid parameters to VideoSourceFrameDelivery");
        return;
    }
    VideoSource* pMe = (VideoSource*)pThis;

    if(pEncBufferHdr->nFlags & OMX_BUFFERFLAG_SYNCFRAME)
    {
        pMe->m_bDropVideoFrame = OMX_FALSE;
    }

    if(pMe->m_eVideoSrcState != WFDMM_VideoSource_STATE_PLAY
            ||pMe->m_bDropVideoFrame)
    {
        WFDMMLOGH("Not delivering frame to MUX");
        pMe->m_pVencoder->SetFreeBuffer(pEncBufferHdr);
        return;
    }

    if(pMe->m_bBlankFrameSupport && pMe->m_bHandleStandby)
    {

        WFDMMLOGH("Ignoring encoder output for blank frame");
        pMe->m_pVencoder->SetFreeBuffer(pEncBufferHdr);
        return;
    }

    pMe->m_nLastFrameTS = pEncBufferHdr->nTimeStamp;

    if(pMe->m_bHdcpSessionValid)
    {
        //Adopt encryption path
        pMe->EncryptData(pEncBufferHdr);
    }
    else
    {
        if(pMe->m_bFillerNaluEnabled)
        {
            pMe->FillFillerBytesExtraData(pEncBufferHdr,NULL,1);
        }
        pMe->m_pFrameDeliverFn(pEncBufferHdr,pMe->m_appData);
    }

}

/*!*************************************************************************
 * @brief     Set Free buffer
 *
 * @param[in] pBufferHdr OMX_BUFFERHEADERTYPE passed in
 *
 * @return    OMX_ERRORTYPE
 *
 * @note      NONE
 **************************************************************************/
OMX_ERRORTYPE VideoSource::SetFreeBuffer(OMX_BUFFERHEADERTYPE* pBufferHdr)
{
    OMX_ERRORTYPE result = OMX_ErrorNone;
    OMX_BUFFERHEADERTYPE* pEncOutputBuff = NULL, *pTempBufferHeader = NULL;

    if (pBufferHdr != NULL && pBufferHdr->pBuffer != NULL)
    {

        buff_hdr_extra_info* pExtraInfo = static_cast<buff_hdr_extra_info*>
            (pBufferHdr->pPlatformPrivate);

        bool bBlankFrame = false;

        if(pExtraInfo)
        {
            if(pExtraInfo->bBlackFrame == OMX_TRUE)
            {
                bBlankFrame = true;
                /*-------------------------------------------------------------
                 NOTE: There can't be a race condition here since the same Buffer
                 Header can't be worked upon by two different threads, so its
                 safe to reset this flag here
                ---------------------------------------------------------------
                */
                pExtraInfo->bBlackFrame = OMX_FALSE;
            }
        }

        if(!bBlankFrame)
        {
            if(m_bHdcpSessionValid)
            {
                /*-------------------------------------------------------------
                 Extract the Buffer Header to be sent back to encoder from the
                 received one
                ---------------------------------------------------------------
                */
                pEncOutputBuff = reinterpret_cast<OMX_BUFFERHEADERTYPE*>
                                        (pBufferHdr->pOutputPortPrivate);

                /*-------------------------------------------------------------
                 Push back the buffer Header to the HDCP output Q
                 --------------------------------------------------------------
                */
                m_pHDCPOutputBufQ->Push(&pBufferHdr,sizeof(pBufferHdr));
            }
            else
            {
                /*-------------------------------------------------------------
                 Push back same buffer header to encoder as received back from
                 Mux
                 --------------------------------------------------------------
                */
                pEncOutputBuff = pBufferHdr;
            }
        }
        else//A few additional steps in case of a blank frame
        {
            m_nBlankFramesDelivered ++;
            WFDMMLOGH2("Received Blank frame %d. %p from MUX",
                m_nBlankFramesDelivered,pBufferHdr);
            if(m_bHdcpSessionValid)
            {
                /*-------------------------------------------------------------
                 Extract the BlankFrame Buffer Header from the received one
                 --------------------------------------------------------------
                */
                pTempBufferHeader = static_cast<OMX_BUFFERHEADERTYPE*>
                                        (pBufferHdr->pOutputPortPrivate);
                /*-------------------------------------------------------------
                 Push back the received buffer Header to the HDCP output Q
                 --------------------------------------------------------------
                */
                m_pHDCPOutputBufQ->Push(&pBufferHdr,sizeof(pBufferHdr));
            }
            else
            {
                pTempBufferHeader = pBufferHdr;
            }

            /*-----------------------------------------------------------------
             Push back the Blank frame buffer Header to Blank FrameQ
             ------------------------------------------------------------------
            */
            OMX_ERRORTYPE res = m_pBlankFrameQ->Push(
                            &pTempBufferHeader,sizeof(pTempBufferHeader));

        /*---------------------------------------------------------------------
         For blank frame case, the juggernaut stops here.
         ----------------------------------------------------------------------
        */
            return result;
        }

        if(m_bEnableProfiling)
        {
            if(m_eVideoSrcState == WFDMM_VideoSource_STATE_PLAY)
            {
                if(m_pWFDMMSrcStats)
                {
                    m_pWFDMMSrcStats->recordMuxStat(pEncOutputBuff);
                }
            }
        }

        m_pVencoder->SetFreeBuffer(pEncOutputBuff);
    }
    else
    {
        WFDMMLOGE1("Bad params passed in to %s",__FUNCTION__);
        result = OMX_ErrorBadParameter;
    }
    return result;
}

/*=============================================================================

         FUNCTION:            EncryptData

         DESCRIPTION:
*//**       @brief           Function for encrypting encoded data
*//**
@par     DEPENDENCIES:
                            None
*//*
         PARAMETERS:
*//**       @param[in]      pBufferHdr   buffer fhaving encoded data

     *         @param[in]      pThis          pointer to get current instance

*//*     RETURN VALUE:
*//**       @return
                            OMX_ErrorNone or other Error
@par     SIFE EFFECTS:
                            None
*//*=========================================================================*/

void VideoSource::EncryptData(OMX_BUFFERHEADERTYPE* pEncBufferHdr)
{
    WFDMMLOGE("Received data to encrypt");
    if(!pEncBufferHdr)
    {
        WFDMMLOGE("Invalid argument to EncryptData");
        return;
    }
    OMX_BUFFERHEADERTYPE* pBuffHdr;
    m_pHDCPOutputBufQ->Pop(&pBuffHdr, sizeof(pBuffHdr),100);
    if(!pBuffHdr)
    {
        WFDMMLOGE("Failed to POP from HDCP BufQ");
        m_pVencoder->SetFreeBuffer(pEncBufferHdr);
        return;
    }

    /*----------------------------------------------------------------
     Extract relevant details from the encoder output
     -----------------------------------------------------------------
    */
    pBuffHdr->nFlags           = pEncBufferHdr->nFlags;
    pBuffHdr->nTimeStamp       = pEncBufferHdr->nTimeStamp;

    /*-------------------------------------------------------------------------
      nFilledLen and nOffset shall be populated later based on encoder buffer
      -------------------------------------------------------------------------
    */

    buff_hdr_extra_info* tempExtra = static_cast<buff_hdr_extra_info*>
        (pEncBufferHdr->pPlatformPrivate);

    if(pEncBufferHdr->pBuffer)
    {
        uint8 ucPESPvtData[PES_PVT_DATA_LEN] = {0};
        output_metabuffer *meta_buf = (output_metabuffer *)(pEncBufferHdr->pBuffer);
        native_handle_t* handle = meta_buf->nh;
       /*----------------------------------------------------------------
         !!!SHOULD BE IN SYNC WITH COMPONENT!!!
         data[0]-> fd
         data[1]-> offset
         data[2]-> size
         data[3]-> allocLen
         Hence the below check for 1 Minimum Fd and 3 Ints at [1],[2] and [3]
         NOTE: AllocLength need not be re-populated since this is already
         taken care when the ouput buffer was allocated
         -----------------------------------------------------------------
       */
        if(!handle || !(handle->numInts >= 3 && handle->numFds >= 1))
        {
            WFDMMLOGE("Invalid parameters from encoder");
            if(handle)
            {
                WFDMMLOGE2("Handle has %d fds and %d ints",
                    handle->numFds,handle->numInts);
            }
            else
            {
                WFDMMLOGE("Native handle is null!!!");
            }
            /*-----------------------------------------------------------------
             Release back buffer to encoder, and push back buffer
             Header to Q and report a runtime error
             ------------------------------------------------------------------
            */
            m_pHDCPOutputBufQ->Push(&pBuffHdr, sizeof(pBuffHdr));
            m_pVencoder->SetFreeBuffer(pEncBufferHdr);
            if(m_pEventHandlerFn)
            {
                m_pEventHandlerFn( m_appData, m_nModuleId,WFDMMSRC_ERROR,
                  OMX_ErrorUndefined, 0);
            }
            return;
        }

        int ion_input_fd = handle->data[0];
        pBuffHdr->nOffset = handle->data[1];
        /*---------------------------------------------------------------------
         Encryption module has no way to propagate offsets since it maps fd and
         takes the entire data of nFilledLen. So encoder module should always
         provide 0 offset. And then MUX would read the data from the offset and
         there would be IV mismatch because encryption module would write data
         from the beginning, oblivious of offset.Right now adding an error log
         which should probably provide debugging hints in case encoder plays
         truant.
         -----------------------------------------------------------------
        */
        if(pBuffHdr->nOffset != 0)
        {
            WFDMMLOGE1("WARNING: Encoder provided offset %ld in secure session",
                        pBuffHdr->nOffset);
        }
        pBuffHdr->nFilledLen = handle->data[2];
        buffer* ion_buffer_out = (reinterpret_cast<buffer*>
                                    (pBuffHdr->pAppPrivate));

        //Reset in case encryption fails, to avoid spurious stats
        if(tempExtra)
        {
            tempExtra->nEncryptTime = 0;
        }

        unsigned ulStatus =
            m_pHdcpHandle->WFD_HdcpDataEncrypt(STREAM_VIDEO ,
                              (unsigned char*)ucPESPvtData,
                              (unsigned char *) (uint64)(ion_input_fd),
                              (unsigned char *) (uint64)(ion_buffer_out->fd),
                               pBuffHdr->nFilledLen);

        if( ulStatus != 0)
        {
            WFDMMLOGE1("Error in HDCP Encryption! %x", ulStatus);
            /*-----------------------------------------------------------------
             Release back buffer to encoder, and push back buffer
             Header to Q and report a runtime error
             ------------------------------------------------------------------
            */
            m_pHDCPOutputBufQ->Push(&pBuffHdr, sizeof(pBuffHdr));
            m_pVencoder->SetFreeBuffer(pEncBufferHdr);

            if(false == m_pHdcpHandle->proceedWithHDCP())
            {
                WFDMMLOGE("Cipher enablement wait timed out");
                if(m_pEventHandlerFn)
                {
                  m_pEventHandlerFn( m_appData, m_nModuleId,WFDMMSRC_ERROR,
                    OMX_ErrorUndefined, 0);
                }
            }
            else
            {
            /*-----------------------------------------------------------------
             In case a frame is dropped request an IDR from encoder to ensure
             that the sink always does receive an IDR, else we might end up in a
             scenario where an IDR frame is dropped due to CIPHER not being
             enabled and then once it's enabled, we end up sending only P frames
             until the sink explicitly requests for an IDR (not guaranteed) or
             the IDR interval expires.
             ------------------------------------------------------------------
            */
                m_pVencoder->RequestIntraVOP();
                m_bDropVideoFrame = OMX_TRUE;
            }
            return;
        }

        if(m_bEnableProfiling)
        {
            if(m_pWFDMMSrcStats)
            {
                m_pWFDMMSrcStats->recordVideoEncryptStat(pEncBufferHdr);
            }
        }

        for ( int idx = 0; idx < PES_PVT_DATA_LEN; idx++)
        {
            WFDMMLOGL3("Encrypt PayloadLen[%lu] PES_PVTData[%d]:%x",
                        pEncBufferHdr->nFilledLen,
                        idx,
                        ucPESPvtData[idx]);
        }

        /*-----------------------------------------------------------------
             Fill PESPvtData at end of the encrypted buffer, as an extra data
            -----------------------------------------------------------
            */

        FillHdcpCpExtraData( pBuffHdr, ucPESPvtData, 1);

        if(m_bFillerNaluEnabled)
        {
          memset((void*)ucPESPvtData, 0, sizeof(ucPESPvtData));
          if(m_nFillerInFd > 0 && m_nFillerOutFd > 0)
          {
             unsigned long ulStatus = m_pHdcpHandle->WFD_HdcpDataEncrypt(
                                    STREAM_VIDEO ,
                                   (unsigned char*)ucPESPvtData,
                                   (unsigned char *)(uint64) (m_nFillerInFd),
                                   (unsigned char *) (uint64)(m_nFillerOutFd),
                                    FILLER_NALU_SIZE);
             if( ulStatus != 0)
             {
                 WFDMMLOGE("Error in Filler NALU HDCP Encryption");
             }
             else
             {
                 FillFillerBytesExtraData(pBuffHdr,
                                          ucPESPvtData, 1);
                 WFDMMLOGE("Filler NALU HDCP Encryption");
             }
          }
        }

        pBuffHdr->pOutputPortPrivate = reinterpret_cast<OMX_U8*>
                                    (pEncBufferHdr);
        if(m_pFrameDeliverFn)
        {
            m_pFrameDeliverFn(pBuffHdr,m_appData);
        }
    }
    else
    {
        WFDMMLOGE("Can't extract fd from encoded buffer!");
    }
}

/*=============================================================================

         FUNCTION:          GenerateBlankFrame

         DESCRIPTION:
*//**       @brief          Function for generating blank frame
*//**
@par     DEPENDENCIES:
                            None
*//*
         PARAMETERS:
*//**       @param[in]      nTimestamp   Timestamp to be attached with frame

*//*     RETURN VALUE:
*//**       @return
                            None
@par     SIFE EFFECTS:
                            None
*//*=========================================================================*/

OMX_ERRORTYPE VideoSource::GenerateBlankFrame(OMX_TICKS nTimestamp)
{

    if(OMX_FALSE == m_bBlankFrameSupport)
    {
        WFDMMLOGE("GenerateBlankFrame not supprted!");
        return OMX_ErrorUnsupportedSetting;
    }

    /*-----------------------------------------------------------------
     Things need to be done a bit differently here

     We aren't going to work on the encoder output, instead
     use pre-populated buffers we have and send it to MUX
     ------------------------------------------------------------------
    */

    /*-----------------------------------------------------------------
     1. Get an OMX_BUFFERHEADER from the Blank Frame Q
     ------------------------------------------------------------------
    */

    OMX_BUFFERHEADERTYPE* pOPBufferHdr = NULL;

    OMX_ERRORTYPE res = m_pBlankFrameQ->Pop(&pOPBufferHdr,
                                sizeof(pOPBufferHdr),100);

    if(res != OMX_ErrorNone)
    {
        WFDMMLOGE1("Failed to POP from Blank FrameQ due to %x",res);
        return OMX_ErrorInsufficientResources;
    }

    /*-----------------------------------------------------------------
     2. Fill in relevant details
     ------------------------------------------------------------------
    */

    pOPBufferHdr->nTimeStamp       = nTimestamp;

    /*----------------------------------------------------------------------
     Hard code some values for this OMX_BUFFERHEADER.

     NOTE: QCOM encoders supply a special flag of QOMX_VIDEO_PictureTypeIDR
     with an IDR frame, but since WFD's MUX stack doesn't care for it, just
     the flag OMX_BUFFERFLAG_SYNCFRAME should suffice here
     ----------------------------------------------------------------------
    */
    pOPBufferHdr->nFlags           = 0|OMX_BUFFERFLAG_SYNCFRAME;
    pOPBufferHdr->nFilledLen       = m_nBlankFrameSize;//Fixed from our end

    /*-------------------------------------------------------------------------
     The actual I/P buffer given to MUX depends on whether the session is
     secure or not.

     A) In non secure, this same buffer will be supplied

     B) In secure case, a buffer from the HDCP Q will be supplied and this
        blank frame will be tagged in it's outputportprivate for later recovery
     --------------------------------------------------------------------------
    */

    OMX_BUFFERHEADERTYPE* pMuxIPBufferHeader = NULL;//The actual MUX i/p buffer

    if(m_bHdcpSessionValid)
    {
        m_pHDCPOutputBufQ->Pop(&pMuxIPBufferHeader,
            sizeof(pMuxIPBufferHeader),100);

        if(!pMuxIPBufferHeader)
        {
            WFDMMLOGE("Failed to POP from HDCP BufQ");

            /*-----------------------------------------------------------------

             1. The OMX_BUFFERHEADER in Blank frame Q needs to be returned

             ------------------------------------------------------------------
            */

            m_pBlankFrameQ->Push(&pOPBufferHdr,sizeof(pOPBufferHdr));
            return OMX_ErrorInsufficientResources;
        }

        /*---------------------------------------------------------------------
         Extract relevant details from the output buffer
         ----------------------------------------------------------------------
        */

        pMuxIPBufferHeader->nFilledLen       = pOPBufferHdr->nFilledLen;
        pMuxIPBufferHeader->nFlags           = pOPBufferHdr->nFlags;
        pMuxIPBufferHeader->nOffset          = pOPBufferHdr->nOffset;
        pMuxIPBufferHeader->nTimeStamp       = pOPBufferHdr->nTimeStamp;

        uint8 ucPESPvtData[PES_PVT_DATA_LEN] = {0};

        buffer* ion_buffer_out = (reinterpret_cast<buffer*>
                                (pMuxIPBufferHeader->pAppPrivate));

        unsigned long ulStatus = m_pHdcpHandle->WFD_HdcpDataEncrypt(
                            STREAM_VIDEO ,
                           (unsigned char*)ucPESPvtData,
                           (unsigned char *)(uint64) (m_nBlankFrameInFd),
                           (unsigned char *) (uint64)(ion_buffer_out->fd),
                            pMuxIPBufferHeader->nFilledLen);

        if( ulStatus != 0)
        {
            WFDMMLOGE1("Blank frame encryption failed with %x",ulStatus);

            m_pBlankFrameQ->Push(&pOPBufferHdr,sizeof(pOPBufferHdr));
            return OMX_ErrorUndefined;
        }

        for ( int idx = 0; idx < PES_PVT_DATA_LEN; idx++)
        {
            WFDMMLOGL3("Encrypt PayloadLen[%lu] PES_PVTData[%d]:%x",
                        pMuxIPBufferHeader->nFilledLen,
                        idx,
                        ucPESPvtData[idx]);
        }

        /*---------------------------------------------------------------------
         Fill PESPvtData at end of the encrypted buffer, as an extra data
         ----------------------------------------------------------------------
        */

        FillHdcpCpExtraData( pMuxIPBufferHeader, ucPESPvtData, 1);

        /*---------------------------------------------------------------------
         Stuff in the frame from the BlankFrameQ to be extracted later
         once MUX returns the buffer
         ----------------------------------------------------------------------
        */

        pMuxIPBufferHeader->pOutputPortPrivate = static_cast<OMX_PTR>
                                    (pOPBufferHdr);

    }
    else
    {
        /*---------------------------------------------------------------------
         Same buffer header from blank frame Q needs to be sent to MUX
         ----------------------------------------------------------------------
        */
        pMuxIPBufferHeader = pOPBufferHdr;
    }

    /*---------------------------------------------------------------------
     Mark this buffer header as a blank frame to handle when MUX returns the
     buffer
     ----------------------------------------------------------------------
    */

    buff_hdr_extra_info* pMuxIPBuffHdrExtraInfo =
    static_cast<buff_hdr_extra_info*>(pMuxIPBufferHeader->pPlatformPrivate);

    if(pMuxIPBuffHdrExtraInfo)
    {
        pMuxIPBuffHdrExtraInfo->bBlackFrame = OMX_TRUE;
        WFDMMLOGH1("Sending Blank frame %p to MUX",pMuxIPBufferHeader);
    }

    if(m_pFrameDeliverFn)
    {
        m_pFrameDeliverFn(pMuxIPBufferHeader,m_appData);
    }

    return OMX_ErrorNone;
}


/***********************************************************************************!
 * @brief      Fill Extra data in buffer
 * @details    Fill HDCP PES pvt extra data at end of buffer.
 * @param[in]  pBufHdr         Payload buffer header
 * @param[in]  pucPESPvtHeader PES Pvt data
 * @param[in]  nPortIndex      Port index
 * @return     RETURN 'OMX_ErrorNone' if SUCCESS
 *             OMX_ErrorBadParameter code in FAILURE
 ***********************************************************************************/
OMX_ERRORTYPE  VideoSource::FillHdcpCpExtraData(
                          OMX_BUFFERHEADERTYPE *pBufHdr,
                          OMX_U8* pucPESPvtHeader,
                          OMX_U32 nPortIndex)
{
  OMX_ERRORTYPE eError = OMX_ErrorNone;
  uint64 ulAddr;
  OMX_OTHER_EXTRADATATYPE *pHdcpCpExtraData;
  if( (NULL != pBufHdr ) && (NULL != pucPESPvtHeader))
  {
    MM_MSG_PRIO2(MM_GENERAL, MM_PRIO_MEDIUM,
                "WFDMMSourceVideoSource::pBufHdr->pBuffer[%p] FilledLen[%ld]",
                pBufHdr->pBuffer,
                pBufHdr->nFilledLen);
    /* Skip encoded frame payload length filled by V4L2 driver */
    ulAddr = (uint64) ( pBufHdr->pBuffer) +  pBufHdr->nFilledLen;
    MM_MSG_PRIO1(MM_GENERAL, MM_PRIO_MEDIUM, "WFDMMSourceVideoSource::ulAddr[%llu]", ulAddr);
    /* Aligned address to DWORD boundary */
    ulAddr = (ulAddr + 0x3) & (~0x3);
    MM_MSG_PRIO1(MM_GENERAL, MM_PRIO_MEDIUM, "WFDMMSourceVideoSource::Aligned ulAddr[%llu]", ulAddr);
    pHdcpCpExtraData = (OMX_OTHER_EXTRADATATYPE *)ulAddr;
    /* Update pBufHdr flag, to indicate that it carry extra data */
    MM_MSG_PRIO1(MM_GENERAL, MM_PRIO_MEDIUM, "WFDMMSourceVideoSource::pHdcpCpExtraData[%p]", pHdcpCpExtraData);

    FLAG_SET(pBufHdr,OMX_BUFFERFLAG_EXTRADATA);
    /* Extra Data size = ExtraDataType*/
    pHdcpCpExtraData->nSize = sizeof(OMX_OTHER_EXTRADATATYPE) + sizeof(OMX_U8)* PES_PVT_DATA_LEN -4;
    pHdcpCpExtraData->nVersion = pBufHdr->nVersion;
    pHdcpCpExtraData->nPortIndex = nPortIndex;
    pHdcpCpExtraData->nDataSize = PES_PVT_DATA_LEN;
    MM_MSG_PRIO3(MM_GENERAL, MM_PRIO_MEDIUM,
      "WFDMMSourceVideoSource::size[%ld] PortIndex[%ld] nDataSize[%ld]",
      pHdcpCpExtraData->nSize,
      pHdcpCpExtraData->nPortIndex,pHdcpCpExtraData->nDataSize );
    pHdcpCpExtraData->eType = (OMX_EXTRADATATYPE)QOMX_ExtraDataHDCPEncryptionInfo;
    /* Fill PES_PVT_DATA into data*/
    memcpy(pHdcpCpExtraData->data,pucPESPvtHeader, PES_PVT_DATA_LEN );
    /* Append OMX_ExtraDataNone */
    ulAddr += pHdcpCpExtraData->nSize;
    pHdcpCpExtraData = (OMX_OTHER_EXTRADATATYPE *)ulAddr;
    pHdcpCpExtraData->nSize = sizeof(OMX_OTHER_EXTRADATATYPE);
    pHdcpCpExtraData->nVersion = pBufHdr->nVersion;
    pHdcpCpExtraData->nPortIndex = nPortIndex;
    pHdcpCpExtraData->eType = OMX_ExtraDataNone;
    pHdcpCpExtraData->nDataSize = 0;
  }
  else
  {
    eError = OMX_ErrorBadParameter;
  }
  return eError;
}

/***********************************************************************************!
 * @brief      Fill FillerBytes Extra data in buffer
 * @details    Fill Fillerbytes NALU extra data at end of buffer.
 * @param[in]  pBufHdr         Payload buffer header
 * @param[in]  pucPESPvtHeader PES Pvt data
 * @param[in]  nPortIndex      Port index
 * @return     RETURN 'OMX_ErrorNone' if SUCCESS
 *             OMX_ErrorBadParameter code in FAILURE
 ***********************************************************************************/
bool  VideoSource::FillFillerBytesExtraData(
                          OMX_BUFFERHEADERTYPE *pBuffHdr,
                          OMX_U8* pucPESPvtHeader,
                          OMX_U32 nPortIndex)
{
  OMX_OTHER_EXTRADATATYPE *pExtra;

  if(NULL != pBuffHdr)
  {
    OMX_U8 *pTmp = pBuffHdr->pBuffer +
                         pBuffHdr->nOffset + pBuffHdr->nFilledLen + 3;

    pExtra = (OMX_OTHER_EXTRADATATYPE *) ((reinterpret_cast<long>(pTmp)) & ~3);

    if(pBuffHdr->nFlags & OMX_BUFFERFLAG_EXTRADATA)
    {
      //Extra Data already set. Find the end
      while(pExtra->eType != OMX_ExtraDataNone)
      {
        pExtra = (OMX_OTHER_EXTRADATATYPE *)
                      (((OMX_U8 *) pExtra) + pExtra->nSize);

        if(reinterpret_cast<long>(pExtra) + (long)sizeof(OMX_OTHER_EXTRADATATYPE) >=
          reinterpret_cast<long>(pBuffHdr->pBuffer) + (long)pBuffHdr->nAllocLen)
        {
          MM_MSG_PRIO(MM_GENERAL, MM_PRIO_ERROR,
                         "Fiiler Bytes Reached out of bounds");
          return false;
        }
      }
    }

    OMX_U32 nBytesToFill = FILLER_NALU_SIZE + 1 /*Signalling Byte*/
                                            + 1 /*Length */;

    if(pucPESPvtHeader != NULL)
    {
      /*Filler Nalu us encrypted*/
      nBytesToFill += PES_PVT_DATA_LEN + 1 /*Length */;
    }

    MM_MSG_PRIO1(MM_GENERAL, MM_PRIO_MEDIUM,
                 "VideoSource Fiiler Bytes nBytesToFill = %lu",
                  nBytesToFill);

    /** Check out of bound access in the pBuffer */
    if(reinterpret_cast<long>(pExtra) + (long)sizeof(OMX_OTHER_EXTRADATATYPE) +
                                           (long)((nBytesToFill +3) & (~3))  >
       reinterpret_cast<long>(pBuffHdr->pBuffer) + (long)pBuffHdr->nAllocLen)
    {
      /*Can't fit in filler bytes*/
      MM_MSG_PRIO(MM_GENERAL, MM_PRIO_ERROR,
                      "VideoSource Can't fit in fillerNALU");
      return false;
    }

    FLAG_SET(pBuffHdr,OMX_BUFFERFLAG_EXTRADATA);

    /* Extra Data size = ExtraDataType*/
    pExtra->nVersion = pBuffHdr->nVersion;
    pExtra->nPortIndex = nPortIndex;
    pExtra->nDataSize = nBytesToFill;
    nBytesToFill += 3;
    nBytesToFill &= (~3);
    pExtra->nSize = (OMX_U32)(sizeof(OMX_OTHER_EXTRADATATYPE) + nBytesToFill -4);

    MM_MSG_PRIO3(MM_GENERAL, MM_PRIO_MEDIUM,
      "WFDMMSourceVideoSource:: Filler size[%ld] PortIndex[%ld] nDataSize[%ld]",
      pExtra->nSize,
      pExtra->nPortIndex,pExtra->nDataSize );

    /* Using MAX to pass generic ExtraData Info using signalling byte to
       identify the type of data*/
    pExtra->eType = OMX_ExtraDataMax;

    /**------------------------------------------------------------------------
    Filler NALU format:= |SigByte|Size|PES Pvt Data|Size|Filler NALU |
    ---------------------------------------------------------------------------
    */

    /**Fill the extra data bytes */
    uint32 nOffset = 0;

    /*Signal Encrypted Payload or non-encrypted Payload*/
    pExtra->data[nOffset] = VIDEO_PVTDATA_TYPE_FILLERNALU;

    if(pucPESPvtHeader != NULL)
    {
      pExtra->data[nOffset] = VIDEO_PVTDATA_TYPE_FILLERNALU_ENCRYPTED;
    }
    nOffset++;

    /**If encypted first add the PES private data */
    if(pucPESPvtHeader != NULL)
    {
      pExtra->data[nOffset] = PES_PVT_DATA_LEN;
      nOffset++;
      /* Fill PES_PVT_DATA into data*/
      memcpy(pExtra->data + nOffset,pucPESPvtHeader, PES_PVT_DATA_LEN );
      nOffset += PES_PVT_DATA_LEN;
    }

    /** FIll the filler NALU bytes */
    pExtra->data[nOffset] = FILLER_NALU_SIZE;
    nOffset++;
    memcpy(pExtra->data + nOffset, m_pFillerDataOutPtr, FILLER_NALU_SIZE);

    /** Fill the extradataNone if there is space left. Mux will
     *  check against AllocLen to prevent access beyond limit */
    pExtra = (OMX_OTHER_EXTRADATATYPE *)
           (reinterpret_cast<OMX_U8*>(pExtra) + pExtra->nSize);
    if(reinterpret_cast<long>(pExtra) + (long)sizeof(OMX_OTHER_EXTRADATATYPE) >=
        reinterpret_cast<long>(pBuffHdr->pBuffer) + (long)pBuffHdr->nAllocLen)
    {
      MM_MSG_PRIO(MM_GENERAL, MM_PRIO_ERROR,
                     "Fiiler Bytes: XtraNone Reached out of bounds");
      return true;
    }
    pExtra->nSize = sizeof(OMX_OTHER_EXTRADATATYPE);
    pExtra->nVersion = pBuffHdr->nVersion;
    pExtra->nPortIndex = nPortIndex;
    pExtra->eType = OMX_ExtraDataNone;
    pExtra->nDataSize = 0;
  }

  return false;
}

/*==============================================================================

         FUNCTION:         eventHandler

         DESCRIPTION:
*//**       @brief         Static function that handles events from encoder
                                modules


*//**

@par     DEPENDENCIES:      None

*//*
         PARAMETERS:
*//**       @param        pThis - this pointer
                          nModuleId - Id of the module reporting event
                          nEvent - Type of event
                          nStatus - status associated with event
                          nData  - More information about event


*//*     RETURN VALUE:
*//**       @return       None


@par     SIDE EFFECTS:

*//*==========================================================================*/
void VideoSource::VideoSourceEventHandlerCb(
                        void *pThis,
                        OMX_U32 nModuleId,
                        WFDMMSourceEventType nEvent,
                        OMX_ERRORTYPE nStatus, void* nData)
{
    (void) nData;
    (void) nStatus;
    if(!pThis)
    {
        WFDMMLOGE("Invalid Me, can't handle device callback");
        return;
    }
    VideoSource* pMe= static_cast<VideoSource*>(pThis);
    WFDMMLOGE1("Received callback from module %ld",nModuleId);
    switch(nEvent)
    {
        case WFDMMSRC_ERROR:
            pMe->m_pEventHandlerFn(pMe->m_appData, pMe->m_nModuleId,
                   WFDMMSRC_ERROR, OMX_ErrorNone, 0);
            break;
        default:
            WFDMMLOGE("Simply unreachable!");
            break;
    }
    return;
}
