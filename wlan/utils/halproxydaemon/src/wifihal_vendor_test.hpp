/*
 * Copyright (c) 2017 Qualcomm Technologies, Inc.
 * All Rights Reserved.
 * Confidential and Proprietary - Qualcomm Technologies, Inc.
 */

#ifndef __WIFI_HAL_NUDSTATS_TEST_HPP__
#define __WIFI_HAL_NUDSTATS_TEST_HPP__

#include "wifi_hal.h"
#include <getopt.h>
#include <sys/socket.h>
#include <arpa/inet.h>
#include "common.hpp"

namespace NUDStats
{
    class NUDStatsTestSuite
    {
    public:

        /* CLI cmd strings */
        static const char *NUD_CMD;
        static const char *NUD_SET;
        static const char *NUD_GET;
        static const char *NUD_CLEAR;
        struct in_addr setip;
        NUDStatsTestSuite(wifi_handle handle);
        /* process the command line args */
        wifi_error processCmd(int argc, char **argv);

        void Usage();
    private:
        wifi_handle wifiHandle_;
        wifi_interface_handle ifaceHandle;
    };
}
#endif
