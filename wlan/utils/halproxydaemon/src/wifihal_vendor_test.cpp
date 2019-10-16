/*
 * Copyright (c) 2017 Qualcomm Technologies, Inc.
 * All Rights Reserved.
 * Confidential and Proprietary - Qualcomm Technologies, Inc.
 */

#include <unistd.h>
#include <string.h>
#include <errno.h>
#include <stdio.h>
#include <getopt.h>
#include <stdlib.h>
#include "nud_stats.h"
#include "wifihal_vendor_test.hpp"

#define MAC_ADDR_ARRAY(a) (a)[0], (a)[1], (a)[2], (a)[3], (a)[4], (a)[5]
#define MAC_ADDR_STR "%02x:%02x:%02x:%02x:%02x:%02x"
wifi_interface_handle wifi_get_iface_handle(wifi_handle handle, char *name);
namespace NUDStats
{
    /* CLI cmd strings */
    const char *NUDStatsTestSuite::NUD_CMD = "nud_stats";
    const char *NUDStatsTestSuite::NUD_SET = "set";
    const char *NUDStatsTestSuite::NUD_GET = "get";
    const char *NUDStatsTestSuite::NUD_CLEAR = "clear";

    /* Constructor */
    NUDStatsTestSuite::NUDStatsTestSuite(wifi_handle handle)
        :wifiHandle_(handle)
    {

    }

    /* Constructor */
    void NUDStatsTestSuite::Usage()
    {
        fprintf(stderr, "Usage:\n");
        fprintf(stderr, "$hal_proxy_daemon nud_stats iface_name set"
                " <ipaddress> \n");
        fprintf(stderr, "Ex: $hal_proxy_daemon nud_stats wlan0 set 192.168.1.1\n\n");
        fprintf(stderr, "$hal_proxy_daemon nud_stats iface_name get\n");
        fprintf(stderr, "Ex: $hal_proxy_daemon nud_stats wlan0 get\n\n");
        fprintf(stderr, "$hal_proxy_daemon nud_stats iface_name clear\n");
        fprintf(stderr, "Ex: $hal_proxy_daemon nud_stats wlan0 clear\n\n");
    }

    /* process the command line args */
    wifi_error NUDStatsTestSuite::processCmd(int argc, char **argv)
    {
        if (argc < 4) {
            Usage();
            fprintf(stderr, "%s: insufficient NUD Stats args\n", argv[0]);
            return WIFI_ERROR_INVALID_ARGS;
        }

        if (strcasecmp(argv[3], NUD_SET) == 0) {
            if (argc < 5) {
                fprintf(stderr, "%s: insufficient args for NUD Stats set\n",
                        __func__);
                fprintf(stderr, "Usage : hal_proxy_daemon NUDStats iface_name"
                        " set <IPv4 address of Default Gateway>\n");
                return WIFI_ERROR_INVALID_ARGS;
            }

            //TODO : Take the Interface name as an argument
            ifaceHandle = wifi_get_iface_handle(wifiHandle_, argv[2]);
            if(!ifaceHandle)
            {
                fprintf(stderr, "Interface %s is not up, exiting.\n", argv[2]);
                return WIFI_ERROR_INVALID_ARGS;
            }

            if (inet_aton(argv[4], &setip) == 0) {
                fprintf(stderr, "Invalid gw_addr :%s\n", argv[4]);
                return WIFI_ERROR_INVALID_ARGS;
            }
            return  wifi_set_nud_stats(ifaceHandle, setip.s_addr);
        }

        if (strcasecmp(argv[3], NUD_GET) == 0) {
            nud_stats stats;
            wifi_error ret;

            //TODO : Take the Interface name as an argument
            ifaceHandle = wifi_get_iface_handle(wifiHandle_, argv[2]);
            if(!ifaceHandle)
            {
                fprintf(stderr, "Interface %s is not up, exiting.\n", argv[2]);
                return WIFI_ERROR_INVALID_ARGS;
            }
            ret = wifi_get_nud_stats(ifaceHandle, &stats);

            if (ret < 0) {
                fprintf(stderr, "Failed to get stats\n");
                return ret;
            }
            //print stats
            fprintf(stderr, "NUD stats\n");
            fprintf(stderr, "arp_req_count_from_netdev: %d\n",
                    stats.arp_req_count_from_netdev);
            fprintf(stderr, "arp_req_count_to_lower_mac: %d\n",
                    stats.arp_req_count_to_lower_mac);
            fprintf(stderr, "arp_req_rx_count_by_lower_mac: %d\n",
                    stats.arp_req_rx_count_by_lower_mac);
            fprintf(stderr, "arp_req_count_tx_success: %d\n",
                    stats.arp_req_count_tx_success);
            fprintf(stderr, "arp_rsp_rx_count_by_lower_mac: %d\n",
                    stats.arp_rsp_rx_count_by_lower_mac);
            fprintf(stderr, "arp_rsp_rx_count_by_upper_mac: %d\n",
                    stats.arp_rsp_rx_count_by_upper_mac);
            fprintf(stderr, "arp_rsp_count_to_netdev: %d\n",
                    stats.arp_rsp_count_to_netdev);
            fprintf(stderr, "arp_rsp_count_out_of_order_drop: %d\n",
                    stats.arp_rsp_count_out_of_order_drop);
            fprintf(stderr, "ap_link_active: %s\n",
                    stats.ap_link_active?"Yes": "No");
            fprintf(stderr, "is_duplicate_addr_detection: %s\n",
                    stats.is_duplicate_addr_detection?"Yes": "No");
	    return ret;
        }

        if (strcasecmp(argv[3], NUD_CLEAR) == 0) {

            ifaceHandle = wifi_get_iface_handle(wifiHandle_, argv[2]);
            if(!ifaceHandle)
            {
                fprintf(stderr, "Interface %s is not up, exiting.\n", argv[2]);
                return WIFI_ERROR_INVALID_ARGS;
            }

            return wifi_clear_nud_stats(ifaceHandle);
        }
        fprintf(stderr, "%s: unknown cmd %s\n", argv[0], argv[3]);
        Usage();
        return WIFI_ERROR_NOT_SUPPORTED;
    }

}
