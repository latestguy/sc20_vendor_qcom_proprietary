/*
 * Copyright (c) 2015-2016 Qualcomm Atheros, Inc.
 *
 * All Rights Reserved.
 * Qualcomm Atheros Confidential and Proprietary.
 */

/*
 * hostapd / VLAN initialization
 * Copyright 2003, Instant802 Networks, Inc.
 * Copyright 2005-2006, Devicescape Software, Inc.
 * Copyright (c) 2009, Jouni Malinen <j@w1.fi>
 *
 * This software may be distributed under the terms of the BSD license.
 * See README for more details.
 */

/*
 * Copyright (c) 2013-2014 The Linux Foundation. All rights reserved.
 *
 * Previously licensed under the ISC license by Qualcomm Atheros, Inc.
 *
 *
 * Permission to use, copy, modify, and/or distribute this software for
 * any purpose with or without fee is hereby granted, provided that the
 * above copyright notice and this permission notice appear in all
 * copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL
 * WARRANTIES WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE
 * AUTHOR BE LIABLE FOR ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL
 * DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR
 * PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER
 * TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR
 * PERFORMANCE OF THIS SOFTWARE.
 */

/*
 * This file was originally distributed by Qualcomm Atheros, Inc.
 * under proprietary terms before Copyright ownership was assigned
 * to the Linux Foundation.
 */



#include <stdlib.h>
#include <stdio.h>
#include <signal.h>
#include <string.h>
#include <unistd.h>
#include <errno.h>
#include <time.h>
#include <getopt.h>
#include <limits.h>
#include <asm/types.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <sys/ioctl.h>
#include <sys/capability.h>
#include <sys/prctl.h>
#include <sys/statvfs.h>
#include <dirent.h>
#include <linux/prctl.h>
#include <pwd.h>
#ifdef ANDROID
#include <private/android_filesystem_config.h>
#endif
#include <sys/socket.h>
#include <linux/netlink.h>
#include <linux/rtnetlink.h>
#include <linux/wireless.h>
#include "event.h"
#include "msg.h"

#include "diag_lsm.h"
#include "diagcmd.h"
#include "diag.h"
#include "cld-diag-parser.h"

#define CNSS_INTF "wlan0"
#define DELAY_IN_S 3
#define FREE_MEMORY_THRESHOLD 100


const char options[] =
"Options:\n\
-f, --logfile(Currently file path is fixed)\n\
-c, --console (prints the logs in the console)\n\
-s, --silent (No print will come when logging)\n\
-q, --qxdm  (prints the logs in the qxdm)\n\
-x, --qxdm_sync (QXDM log packet format)\n\
-l, --qxdm_sync_log_file (QXDM log packet format QMDL2 file)\n\
-d, --debug  (more prints in logcat, check logcat \n\
-s ROME_DEBUG, example to use: -q -d or -c -d)\n\
-b --buffer_size ( example to use : -b 64(in KBs)\n\
-m --cnss_diag_config_file_loc ( example to use : -m /data/misc/cnss_diag.conf)\n\
The options can also be given in the abbreviated form --option=x or -o x. \
The options can be given in any order";

char *log_file_name_prefix[LOG_FILE_MAX] = {
	[HOST_LOG_FILE] = "host_driver_logs_",
	[FW_LOG_FILE] = "cnss_fw_logs_",
	[HOST_QXDM_LOG_FILE] = "host_qxdm_driver_logs_",
	[FW_QXDM_LOG_FILE] = "cnss_fw_qxdm_logs_"};
char *log_file_name_extn[LOG_FILE_MAX] = {
	[HOST_LOG_FILE] = "txt",
	[FW_LOG_FILE] = "txt",
	[HOST_QXDM_LOG_FILE] = "qmdl2",
	[FW_QXDM_LOG_FILE] = "qmdl2"};

struct sockaddr_nl src_addr, dest_addr;
struct nlmsghdr *nlh = NULL;
struct iovec iov;
static int sock_fd = -1;
struct msghdr msg;

const char *progname;
int32_t optionflag = 0;
int log_path_flag = WRITE_TO_INTERNAL_SDCARD;
int delayed_count = 0;

int avail_space = 0;
int max_file_size = MAX_FILE_SIZE;
int max_archives = MAX_FILE_INDEX;

unsigned int configured_buffer_size = 0;
int free_mem_threshold = FREE_MEMORY_THRESHOLD;
char wlan_log_dir_path[MAX_FILENAME_SIZE];

struct cnss_log_file_data log_file[LOG_FILE_MAX];
uint8_t gwlan_dev = CNSS_DIAG_WLAN_DEV_UNDEF;
char *cnss_diag_wlan_dev_name[CNSS_DIAG_WLAN_DEV_MAX] = {
	[CNSS_DIAG_WLAN_DEV_UNDEF] = "X_X",
	[CNSS_DIAG_WLAN_ROM_DEV] = "QCA6174",
	[CNSS_DIAG_WLAN_TUF_DEV] = "QCA93",
	[CNSS_DIAG_WLAN_HEL_DEV] = "WCN3990",
	[CNSS_DIAG_WLAN_NAP_DEV] = "XXX_XXX"};

char *cnssdiag_config_file = "/data/misc/wifi/cnss_diag.conf";
char log_capture_loc[MAX_SIZE] = "/sdcard/wlan_logs/";



boolean isDriverLoaded = FALSE;

char *line_trim(char *);

static void
usage(void)
{
	fprintf(stderr, "Usage:\n%s options\n", progname);
	fprintf(stderr, "%s\n", options);
	exit(-1);
}
/* function to find whether file exists or not */
static  int doesFileExist(const char *filename) {
	struct stat st;
	int result = stat(filename, &st);
	return result == 0;
}

static uint32_t get_le32(const uint8_t *pos)
{
	return pos[0] | (pos[1] << 8) | (pos[2] << 16) | (pos[3] << 24);
}


/* Opens a directory wlan_logs and searches the same for the presence of
 * host and firmware log files. Sets the index of the file which is used
 * to store the logs before the reboot.
 */


void readDir(const char *dirName, enum fileType type) {
	struct DIR *fdir;
	struct dirent *dirent;
	int *files = NULL;
	char file_name[32];
	int i = 0, found = 0;

	files = (int *)malloc(sizeof(int) * max_archives);
	if (NULL == files) {
	    debug_printf("%s: failed to allocate memory to host_files\n", __func__);
	    return;
	}

	memset(files, 0, (sizeof(int) * max_archives));
	fdir = opendir(dirName);
	if (NULL == fdir) {
	    debug_printf("%s: fdir is NULL\n", __func__);
	    free(files);
	    return;
	}
	chdir(dirName);
	while ((dirent = readdir(fdir)) != NULL) {
		found = 0;
		for (i = 0; i < max_archives; i++) {
			snprintf(file_name, sizeof(file_name), "%s%03d.%s",
					log_file_name_prefix[type], i, log_file_name_extn[type]);

			if ((0 == (strcmp(dirent->d_name, file_name)))) {
				files[i] = 1;
				found = 1;
			}
			if (found)
				break;
		}
	}
/*
 * value 0 at index 'i' indicates, host log file current.txt will be renamed
 * with the filename at 'i'th index.
  */
	i = 0;
	while (i < max_archives) {
		if (!files[i]) {
			log_file[type].index = i;
			break;
		}
		i++;
	}
	debug_printf("%s: File Index: HOST_LOG_FILE: %d, HOST_QXDM_LOG_FILE: %d, FW_LOG_FILE: %d\n",
		 __func__, log_file[HOST_LOG_FILE].index, log_file[HOST_QXDM_LOG_FILE].index, log_file[FW_LOG_FILE].index);
	free(files);
	closedir(fdir);
}

/*
 * rename host/firmware current.txt logfile with the corresponding
 * host/firmware log file with proper index and delete its next
 * index file to identify the last file name used to store the logs
 * after a reboot.
 */


void backup_file(enum fileType type)
{
	char newFileName[100];
	char delFileName[100];
	int ret =0;

	if (type >= LOG_FILE_MAX)
		return;

	snprintf(newFileName, sizeof(newFileName), "%s%s%03d.%s",
			wlan_log_dir_path, log_file_name_prefix[type],
			log_file[type].index, log_file_name_extn[type]);
	errno = 0;
	ret = rename(log_file[type].name, newFileName);
	log_file[type].fp = NULL;
	log_file[type].index++;
	if (max_archives == log_file[type].index)
		log_file[type].index = 0;
	snprintf(delFileName, sizeof(delFileName), "%s%s%03d.%s",
			wlan_log_dir_path, log_file_name_prefix[type],
			log_file[type].index, log_file_name_extn[type]);
	unlink(delFileName);
}

static void cleanup(void)
{
	int i;
	if (sock_fd)
		close(sock_fd);
	for (i = HOST_LOG_FILE; i < LOG_FILE_MAX; i++) {
		if (i == FW_QXDM_LOG_FILE)
			buffer_fw_logs_log_pkt("", TRUE);
		if(log_file[i].fp) {
	                fwrite(log_file[i].buf, sizeof(char), (log_file[i].buf_ptr - log_file[i].buf), log_file[i].fp);
			fflush(log_file[i].fp);
			fclose(log_file[i].fp);
		}
		if (log_file[i].buf) {
			free(log_file[i].buf);
			log_file[i].buf = NULL;
		}
	}
}

static void stop(int32_t signum)
{
	UNUSED(signum);
	if(optionflag & LOGFILE_FLAG){
		printf("Recording stopped\n");
		cleanup();
	}
	exit(0);
}


void process_cnss_log_file(uint8_t *dbgbuf)
{
	uint16_t length = 0;
	uint32_t dropped = 0;
	uint32_t timestamp = 0;
	uint32_t res =0;
	struct dbglog_slot *slot = (struct dbglog_slot *)dbgbuf;
	if (NULL != log_file[FW_LOG_FILE].fp)
		fseek(log_file[FW_LOG_FILE].fp, ftell(log_file[FW_LOG_FILE].fp), SEEK_SET);
	timestamp = get_le32((uint8_t *)&slot->timestamp);
	length = get_le32((uint8_t *)&slot->length);
	dropped = get_le32((uint8_t *)&slot->dropped);
	if (!((optionflag & SILENT_FLAG) == SILENT_FLAG)) {
		/* don't like this have to fix it */
		printf("Read bytes %ld timestamp=%u length=%u fw dropped=%u\n",
		    (log_file[FW_LOG_FILE].fp != NULL )? ftell(log_file[FW_LOG_FILE].fp) : 0, timestamp, length, dropped);
	}
	if (NULL != log_file[FW_LOG_FILE].fp) {
		if ((res = fwrite(dbgbuf, RECLEN, 1, log_file[FW_LOG_FILE].fp)) != 1) {
			perror("fwrite");
			return;
		}
		fflush(log_file[FW_LOG_FILE].fp);
	}
}

/*
 * This function trims any leading and trailing white spaces
 */
char *line_trim(char *str)
{
	char *ptr;

	if(*str == '\0') return str;

	/* Find the first non white-space */
	for (ptr = str; i_isspace(*ptr); ptr++);
	if (*ptr == '\0')
	    return str;

	/* This is the new start of the string*/
	str = ptr;

	/* Find the last non white-space and null terminate the string */
	ptr += strlen(ptr) - 1;
	for (; ptr != str && i_isspace(*ptr); ptr--);
	ptr[1] = '\0';

	return str;
}

void read_config_file(void) {

	FILE *fp = NULL;
	char line_string[256];
	char *line;
	char string[100];
	static int path_flag = 0;
	static int size_flag = 0;
	int archive_flag = 0;
	int memory_threshold_flag = 0;

	int log_storage = 0;

	fp = fopen(cnssdiag_config_file, "a+");
	if (NULL != fp) {
		fseek(fp, 0, SEEK_SET);
		while (!feof(fp)) {
			fgets(line_string, sizeof(line_string), fp);
			line = line_string;
			line = line_trim(line);
			if (*line == '#')
				continue;
			else {
				sscanf(line, "%s", string);
				if (strcmp(string, "LOG_PATH_FLAG") == 0) {
					sscanf((line + strlen("LOG_PATH_FLAG")
						+ FLAG_VALUE_OFFSET),
							"%s", string);
					log_path_flag = atoi(string);
					path_flag = 1;
					debug_printf("file_path=%d\n", log_path_flag);
				}
				else if (strcmp(string, "MAX_LOG_FILE_SIZE") == 0) {
					sscanf((line +	strlen("MAX_LOG_FILE_SIZE") +
						FLAG_VALUE_OFFSET),
							 "%s", string);
					max_file_size = (int)strtol(string, (char **)NULL, 10);
					if ((max_file_size > 0) &&
						(max_file_size <= MAX_FILE_SIZE_FROM_USER_IN_MB)) {
						max_file_size = max_file_size * (1024) * (1024);
					} else {
						max_file_size = 0;
					}
					size_flag = 1;
					debug_printf("max_file_size=%d\n", max_file_size);
				}
				else if (strcmp(string, "MAX_ARCHIVES") == 0) {
					sscanf((line +	strlen("MAX_ARCHIVES") +
						FLAG_VALUE_OFFSET),
							 "%s", string);
					max_archives = atoi(string);
					if (max_archives >= 50)
						max_archives = 50;
					archive_flag = 1;
					debug_printf("max_archives=%d\n", max_archives);
				}
				else if (strcmp(string, "AVAILABLE_MEMORY_THRESHOLD") == 0) {
					sscanf((line +	strlen("AVAILABLE_MEMORY_THRESHOLD") +
						FLAG_VALUE_OFFSET), "%s", string);
					free_mem_threshold = atoi(string);
					memory_threshold_flag = 1;
					debug_printf("free_mem_threshold=%d\n", free_mem_threshold);
				} else if (strcmp(string, "LOG_STORAGE_PATH") == 0) {
					sscanf((line +	strlen("LOG_STORAGE_PATH") +
						FLAG_VALUE_OFFSET), "%s", string);
					if (strlen(string) != 0)
						strlcpy(log_capture_loc, string, sizeof(log_capture_loc));
					android_printf("log_capture_location  = %s", log_capture_loc);
					log_storage = 1;

				} else
					continue;
				}
				if ((1 == path_flag) && (1 == size_flag)&& (archive_flag == 1)
						&& (memory_threshold_flag) && log_storage) {
					break;
				}
			}
			if (!path_flag)
				fprintf(fp, "LOG_PATH_FLAG = %d\n", log_path_flag);
			if (!size_flag)
				fprintf(fp, "MAX_LOG_FILE_SIZE = %d\n", MAX_FILE_SIZE /((1024) * (1024)));
			if (!archive_flag)
				fprintf(fp, "MAX_ARCHIVES = %d\n", MAX_FILE_INDEX);
			if (! log_storage)
				fprintf(fp, "LOG_STORAGE_PATH = %s\n", log_capture_loc);
			if (!memory_threshold_flag)
				fprintf(fp, "AVAILABLE_MEMORY_THRESHOLD = %d\n", FREE_MEMORY_THRESHOLD);
	}
	else {
		debug_printf("%s(%s): Configuration file not present "
				"set defualt log file path to internal "
				"sdcard\n", __func__, strerror(errno));
	}
	if (fp)
		fclose(fp);
}



void cnss_open_log_file(int max_size_reached, enum fileType type)
{
	struct stat st;
	int ret;

	if (log_path_flag == WRITE_TO_FILE_DISABLED) {
		optionflag &= ~(LOGFILE_FLAG);
		debug_printf("%s: write to file flag is disabled\n", __func__);
	}

	do {
		if (!max_size_reached)
			log_file[type].index = 0;

		if(stat(wlan_log_dir_path, &st) == 0 &&
			S_ISDIR(st.st_mode)) {
			android_printf("%s: directory %s created",
					__func__, wlan_log_dir_path);
		}
		else {
			ret = mkdir(wlan_log_dir_path, 755);
			android_printf("%s: create directory %s "
					"ret = %d errno= %d", __func__,
					wlan_log_dir_path, ret, errno);
		}
		readDir(wlan_log_dir_path, type);

		if (NULL == log_file[type].fp) {
			if (max_size_reached) {
				log_file[type].fp = fopen(log_file[type].name, "w");
			} else {
				log_file[type].fp = fopen(log_file[type].name, "a+");
				if ((log_file[type].fp != NULL) &&
						(ftell(log_file[type].fp) >=
							max_file_size)) {
					if ((avail_space  < free_mem_threshold) &&
							(log_path_flag ==
							WRITE_TO_INTERNAL_SDCARD)) {
						android_printf("Device free memory is insufficient");
						break;
					}
					fflush(log_file[type].fp);
					fclose(log_file[type].fp);
					backup_file(type);
					log_file[type].fp = fopen(log_file[type].name, "w");
				} else {
					debug_printf("failed to open file a+ mode or file"
						" size %ld is less than max_file_size %d\n",
						(log_file[type].fp != NULL)?
						ftell(log_file[type].fp) : 0,
						max_file_size);
				}
			}
			if (NULL == log_file[type].fp) {
				debug_printf("Failed to open file %s: %d\n",
						log_file[type].name, errno);
			}
		}

		if (NULL == log_file[type].fp) {
			if (MAX_RETRY_COUNT != delayed_count) {
				debug_printf("%s: Sleep and poll again for %s "
						" sdcard\n", __func__,
						(log_path_flag == 1) ? "internal" : "external");
				sleep(DELAY_IN_S);
				delayed_count++;
			}
			else {
				delayed_count = 0;
				if (log_path_flag == WRITE_TO_EXTERNAL_SDCARD) {
					log_path_flag = WRITE_TO_INTERNAL_SDCARD;
					debug_printf("%s: External sdcard not mounted try for"
							" internal sdcard ", __func__);
					continue;
				}
				else {
					debug_printf("%s: Internal sdcard not yet mounted"
						" Disable writing logs to a file\n", __func__);
					log_path_flag = WRITE_TO_FILE_DISABLED;
					break;
				}
			}
		} else
			break;
	} while(1);
	return;
}
/*
 * Process FW debug, FW event and FW log messages
 * Read the payload and process accordingly.
 *
 */
void process_cnss_diag_msg(tAniNlHdr *wnl)
{
	uint8_t *dbgbuf;
	uint8_t *eventbuf = ((uint8_t *)NLMSG_DATA(wnl) + sizeof(wnl->radio));
	uint16_t diag_type = 0;
	uint32_t event_id = 0;
	uint16_t length = 0;
	struct dbglog_slot *slot;
	uint32_t dropped = 0;

	dbgbuf = eventbuf;
	diag_type = *(uint16_t *)eventbuf;
	eventbuf += sizeof(uint16_t);

	length = *(uint16_t *)eventbuf;
	eventbuf += sizeof(uint16_t);

	if (wnl->nlh.nlmsg_type == WLAN_NL_MSG_CNSS_HOST_MSG) {
		if ((wnl->wmsg.type == ANI_NL_MSG_LOG_HOST_MSG_TYPE) ||
			(wnl->wmsg.type == ANI_NL_MSG_LOG_MGMT_MSG_TYPE)) {
			if ((optionflag & LOGFILE_FLAG) && (!doesFileExist(log_file[HOST_LOG_FILE].name))
				&& (log_path_flag == WRITE_TO_INTERNAL_SDCARD)&& log_file[HOST_LOG_FILE].fp) {
				if (fclose(log_file[HOST_LOG_FILE].fp) == EOF)
					perror("Failed to close host file ");
				log_file[HOST_LOG_FILE].index = 0;
				log_file[HOST_LOG_FILE].fp = fopen(log_file[HOST_LOG_FILE].name, "w");
				if (log_file[HOST_LOG_FILE].fp == NULL) {
					debug_printf("Failed to create a new file");
				}
			}
			process_cnss_host_message(wnl, optionflag);
		}
		else if (wnl->wmsg.type == ANI_NL_MSG_LOG_FW_MSG_TYPE) {
			if ((optionflag & LOGFILE_FLAG) && (!doesFileExist(log_file[FW_LOG_FILE].name))
				&&(log_path_flag == WRITE_TO_INTERNAL_SDCARD)&& log_file[FW_LOG_FILE].fp) {
				if (fclose(log_file[FW_LOG_FILE].fp) == EOF)
					perror("Failed to close fw file ");
				log_file[FW_LOG_FILE].index = 0;
				log_file[FW_LOG_FILE].fp = fopen(log_file[FW_LOG_FILE].name, "w");
				if(log_file[FW_LOG_FILE].fp == NULL) {
					debug_printf("Failed to create a new file");
				}
			}
			process_pronto_firmware_logs(wnl, optionflag);
		}
	} else if (wnl->nlh.nlmsg_type == WLAN_NL_MSG_CNSS_HOST_EVENT_LOG &&
		   (wnl->wmsg.type == ANI_NL_MSG_LOG_HOST_EVENT_LOG_TYPE)) {
		process_cnss_host_diag_events_log(
		    (char *)((char *)&wnl->wmsg.length +
			      sizeof(wnl->wmsg.length)),
		    optionflag);
	} else {
		if (diag_type == DIAG_TYPE_FW_EVENT) {
			eventbuf += sizeof(uint32_t);
			event_id = *(uint32_t *)eventbuf;
			eventbuf += sizeof(uint32_t);
			if (optionflag & QXDM_FLAG) {
				if (length)
					event_report_payload(event_id, length,
							     eventbuf);
				else
					event_report(event_id);
			}
		} else if (diag_type == DIAG_TYPE_FW_LOG) {
			/* Do nothing for now */
		} else if (diag_type == DIAG_TYPE_FW_DEBUG_MSG) {
			slot =(struct dbglog_slot *)dbgbuf;
			length = get_le32((uint8_t *)&slot->length);
			dropped = get_le32((uint8_t *)&slot->dropped);
			dbglog_parse_debug_logs(&slot->payload[0],
				    length, dropped, wnl->radio);
		} else if (diag_type == DIAG_TYPE_FW_MSG) {
			uint32_t version = 0;
			slot = (struct dbglog_slot *)dbgbuf;
			length = get_32((uint8_t *)&slot->length);
			version = get_le32((uint8_t *)&slot->dropped);
			if ((optionflag & LOGFILE_FLAG) && (!doesFileExist(log_file[FW_LOG_FILE].name))
				&&(log_path_flag == WRITE_TO_INTERNAL_SDCARD)&& log_file[FW_LOG_FILE].fp) {
				if (fclose(log_file[FW_LOG_FILE].fp) == EOF)
					perror("Failed to close fw file ");
				log_file[FW_LOG_FILE].index = 0;
				log_file[FW_LOG_FILE].fp = fopen(log_file[FW_LOG_FILE].name, "w");
				if(log_file[FW_LOG_FILE].fp == NULL) {
					debug_printf("Failed to create a new file");
				}
			}
			process_diagfw_msg(&slot->payload[0], length,
			    optionflag, version, sock_fd, wnl->radio);
		} else if (diag_type == DIAG_TYPE_HOST_MSG) {
			slot = (struct dbglog_slot *)dbgbuf;
			length = get_32((uint8_t *)&slot->length);
			process_diaghost_msg(slot->payload, length);
		} else {
			/* Do nothing for now */
		}
	}
}

/*
 * Open the socket and bind the socket with src
 * address. Return the socket fd if sucess.
 *
 */
static int32_t create_nl_socket()
{
	int32_t ret;
	int32_t sock_fd;

	sock_fd = socket(PF_NETLINK, SOCK_RAW, NETLINK_USERSOCK);
	if (sock_fd < 0) {
		fprintf(stderr, "Socket creation failed sock_fd 0x%x \n",
		        sock_fd);
		return -1;
	}

	memset(&src_addr, 0, sizeof(src_addr));
	src_addr.nl_family = AF_NETLINK;
	src_addr.nl_groups = 0x01;
	src_addr.nl_pid = getpid(); /* self pid */

	ret = bind(sock_fd, (struct sockaddr *)&src_addr, sizeof(src_addr));
	if (ret < 0)
		{
		close(sock_fd);
		return ret;
	}
	return sock_fd;
}

static int initialize()
{
	char *mesg = "Hello";

	memset(&dest_addr, 0, sizeof(dest_addr));
	dest_addr.nl_family = AF_NETLINK;
	dest_addr.nl_pid = 0; /* For Linux Kernel */
	dest_addr.nl_groups = 0; /* unicast */

	if (nlh) {
		free(nlh);
		nlh = NULL;
	}
	nlh = (struct nlmsghdr *)malloc(NLMSG_SPACE(MAX_MSG_SIZE));
	if (nlh == NULL) {
		android_printf("%s Cannot allocate memory for nlh",
			__func__);
		return -1;
	}
	memset(nlh, 0, NLMSG_SPACE(MAX_MSG_SIZE));
	nlh->nlmsg_len = NLMSG_SPACE(MAX_MSG_SIZE);
	nlh->nlmsg_pid = getpid();
	nlh->nlmsg_type = WLAN_NL_MSG_CNSS_DIAG;
	nlh->nlmsg_flags = NLM_F_REQUEST;

	memcpy(NLMSG_DATA(nlh), mesg, strlen(mesg));

	iov.iov_base = (void *)nlh;
	iov.iov_len = nlh->nlmsg_len;
	msg.msg_name = (void *)&dest_addr;
	msg.msg_namelen = sizeof(dest_addr);
	msg.msg_iov = &iov;
	msg.msg_iovlen = 1;

	return 1;
}

int init_log_file()
{
	boolean enable_log_file[LOG_FILE_MAX] = {FALSE, FALSE, FALSE};
	int i;

	if (optionflag & LOGFILE_FLAG) {
		enable_log_file[HOST_LOG_FILE] = TRUE;
		enable_log_file[FW_LOG_FILE] = TRUE;
	}
	if (optionflag & LOGFILE_QXDM_FLAG) {
		enable_log_file[HOST_QXDM_LOG_FILE] = TRUE;
		enable_log_file[FW_QXDM_LOG_FILE] = TRUE;
	}
	if (log_path_flag == WRITE_TO_EXTERNAL_SDCARD) {
		snprintf(wlan_log_dir_path, sizeof(wlan_log_dir_path),
				"%s", "/mnt/media_rw/sdcard1/wlan_logs/");
	} else if (log_path_flag == WRITE_TO_INTERNAL_SDCARD) {
		snprintf(wlan_log_dir_path, sizeof(wlan_log_dir_path),
				"%s", log_capture_loc);
	}

	for (i = HOST_LOG_FILE; i < LOG_FILE_MAX; i++) {
		snprintf(log_file[i].name, sizeof(log_file[i].name),
			"%s%scurrent.%s", wlan_log_dir_path, log_file_name_prefix[i], log_file_name_extn[i]);
		if (enable_log_file[i] == FALSE)
			continue;
		if (!(optionflag & BUFFER_SIZE_FLAG))
			configured_buffer_size = DEFAULT_LOG_BUFFER_LIMIT;
		log_file[i].free_buf_mem = configured_buffer_size;

		log_file[i].buf = (char*) malloc(configured_buffer_size * sizeof(char));
		if (!log_file[i].buf) {
			android_printf("malloc failed for Host case");
			return -1;
		}
		memset(log_file[i].buf, 0x00, (configured_buffer_size * sizeof(char)));
		log_file[i].buf_ptr = log_file[i].buf;
		cnss_open_log_file(FALSE, i);
	}

	if (optionflag & LOGFILE_QXDM_FLAG) {
		struct qmdl_file_hdr file_hdr;
		file_hdr.hdr_len = sizeof(struct qmdl_file_hdr);
		file_hdr.version = 1;
		file_hdr.data_type = 0;
		file_hdr.guid_list_count = 0;
		cnss_write_buf_logs(sizeof(struct qmdl_file_hdr), (char *)&file_hdr, HOST_QXDM_LOG_FILE);
		cnss_write_buf_logs(sizeof(struct qmdl_file_hdr), (char *)&file_hdr, FW_QXDM_LOG_FILE);
	}
	return 0;
}

static int getAvailableSpace(const char* path) {
	struct statvfs stat;
	if (statvfs(path, &stat) != 0) {
		return -1;
	}
	/* the available size is f_bsize * f_bavail , return in MBs */
	return ((stat.f_bsize * stat.f_bavail) / (1024 * 1024));
}

static void cnss_diag_find_wlan_dev()
{
	int i, sk;
	char buf[512], *hw_name;
	struct iwreq rq;
#define WLAN_VERSION_IOCTL 0x8be5

	sk = socket(AF_INET, SOCK_DGRAM, 0);
	if (sk < 0)
		return;
	strlcpy(rq.ifr_name, CNSS_INTF, IFNAMSIZ);
	rq.u.data.pointer = (caddr_t) buf;
	rq.u.data.flags = 1;
	rq.u.data.length = 0;
	system("ifconfig "CNSS_INTF" up");
	if(ioctl(sk, WLAN_VERSION_IOCTL, &rq) < 0) {
		printf("Not CLD WLAN Driver\n");
		return;
	}
	buf[rq.u.data.length] = '\0';
	hw_name = strstr(buf, "HW:");
	if (!hw_name)
		return;
	hw_name += strlen("HW:");
	gwlan_dev = CNSS_DIAG_WLAN_ROM_DEV;
	for (i = CNSS_DIAG_WLAN_ROM_DEV; i < CNSS_DIAG_WLAN_DEV_MAX; i++) {
		if (strncmp(hw_name, cnss_diag_wlan_dev_name[i], strlen(cnss_diag_wlan_dev_name[i])) == 0) {
			gwlan_dev = i;
			break;
		}
	}
	close(sk);
}

int32_t main(int32_t argc, char *argv[])
{
	int res =0;
	int32_t c;
	boolean fetch_free_mem = TRUE;

	progname = argv[0];
	int32_t option_index = 0;
	static struct option long_options[] = {
		{"logfile", 0, NULL, 'f'},
		{"console", 0, NULL, 'c'},
		{"qxdm", 0, NULL, 'q'},
		{"qxdm_sync", 0, NULL, 'x'},
		{"qxdm_sync_log_file", 0, NULL, 'l'},
		{"silent", 0, NULL, 's'},
		{"debug", 0, NULL, 'd'},
		{"buffer_size",required_argument, NULL, 'b'},
		{"config_file",required_argument, NULL, 'm'},
		{ 0, 0, 0, 0}
	};


	while (1) {
		c = getopt_long (argc, argv, "fscqxldb:m:", long_options,
				 &option_index);
		if (c == -1) break;

		switch (c) {
		case 'f':
			optionflag |= LOGFILE_FLAG;
			break;
		case 'b':
			optionflag |= BUFFER_SIZE_FLAG;
			if (optarg != NULL) {
				configured_buffer_size = atoi(optarg) * 1024;
			}
			break;

		case 'c':
			optionflag |= CONSOLE_FLAG;
			break;

		case 'q':
			optionflag |= QXDM_FLAG;
			break;
		case 'x':
			optionflag |= QXDM_SYNC_FLAG;
			break;
		case 'l':
			optionflag |= LOGFILE_QXDM_FLAG;
			break;
		case 's':
			optionflag |= SILENT_FLAG;
			break;

		case 'd':
			optionflag |= DEBUG_FLAG;
			break;
		case 'm':
			if (optarg != NULL)
				cnssdiag_config_file = optarg;
			break;

		default:
			usage();
		}
	}

	if (!(optionflag & (LOGFILE_FLAG | CONSOLE_FLAG | QXDM_SYNC_FLAG |
			    QXDM_FLAG | SILENT_FLAG | DEBUG_FLAG |
			    LOGFILE_QXDM_FLAG | BUFFER_SIZE_FLAG))) {
		usage();
		return -1;
	}

	if (optionflag & QXDM_FLAG || optionflag & QXDM_SYNC_FLAG) {
		/* Intialize the fd required for diag APIs */
		if (TRUE != Diag_LSM_Init(NULL))
			{
			perror("Failed on Diag_LSM_Init\n");
			return -1;
		}
		/* Register CALLABACK for QXDM input data */
		DIAGPKT_DISPATCH_TABLE_REGISTER(DIAG_SUBSYS_WLAN,
		    cnss_wlan_tbl);
	}

	sock_fd = create_nl_socket();
	if (sock_fd < 0) {
		fprintf(stderr, "Socket creation failed sock_fd 0x%x \n",
			sock_fd);
		return -1;
	}
	if (initialize() < 0)
		return -1;
	read_config_file();

	if (optionflag & LOGFILE_FLAG || optionflag & LOGFILE_QXDM_FLAG) {
		avail_space = getAvailableSpace("/sdcard");
		if (avail_space != -1)
			fetch_free_mem = FALSE;
		if (init_log_file())
			goto end;
	}
	signal(SIGINT, stop);
	signal(SIGTERM, stop);

	parser_init();

	while ( 1 )  {
		if ((res = recvmsg(sock_fd, &msg, 0)) < 0)
			continue;
		if ((res >= (int)sizeof(struct dbglog_slot)) ||
		    (nlh->nlmsg_type == WLAN_NL_MSG_CNSS_HOST_EVENT_LOG)) {
			if (fetch_free_mem && (optionflag & LOGFILE_FLAG)) {
				avail_space = getAvailableSpace("/sdcard");
				if (avail_space != -1)
					fetch_free_mem = FALSE;
			}
			//Identify driver once on receiving NL MSG from driver
			if (gwlan_dev == CNSS_DIAG_WLAN_DEV_UNDEF) {
				// Default to Rome (compaitble with legacy device logging)
				cnss_diag_find_wlan_dev();
			}
			process_cnss_diag_msg((tAniNlHdr *)nlh);
			memset(nlh,0,NLMSG_SPACE(MAX_MSG_SIZE));
		} else {
			/* Ignore other messages that might be broadcast */
			continue;
		}
	}
end:
	/* Release the handle to Diag*/
	if (optionflag & QXDM_FLAG || optionflag & QXDM_SYNC_FLAG)
		Diag_LSM_DeInit();
	if (optionflag & LOGFILE_FLAG || optionflag & LOGFILE_QXDM_FLAG)
		cleanup();
	close(sock_fd);
	free(nlh);
	return 0;
}

