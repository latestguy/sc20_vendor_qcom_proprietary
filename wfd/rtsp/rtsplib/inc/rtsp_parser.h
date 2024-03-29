#ifndef _RTSP_PARSER_H
#define _RTSP_PARSER_H
/***************************************************************************
 *                             rtsp_parser.h
 * DESCRIPTION
 *  RTSP message parser declaration for RTSP_LIB module
 *
 * Copyright (c)  2011 - 2012, 2015 Qualcomm Technologies, Inc.
 * All Rights Reserved
 * Confidential and Proprietary - Qualcomm Technologies, Inc.
 ***************************************************************************/

/***************************************************************************
                              Edit History
  $Header: //source/qcom/qct/multimedia2/Video/wfd/rtsp/main/latest/rtsplib/inc/rtsp_parser.h#1 $
  $DateTime: 2011/12/14 03:28:24 $
  $Change: 2096652 $
 ***************************************************************************/

#include "rtsp_helper.h"

enum rtspFields {
    getSequence,
    getSession,
    getRtpPorts,
    getWfdMethod
};

void parseRecv(char **, size_t, rtspParams *);
void recvCmd(std::string, rtspParams *);
void createWordTable(std::string, char **, unsigned &);
bool getRtspParam(char **, unsigned, rtspFields, std::string &,const char *input = NULL, bitset<WFD_MAX_SIZE> wfdMethods = 0);

#endif /*_RTSP_PARSER_H*/
