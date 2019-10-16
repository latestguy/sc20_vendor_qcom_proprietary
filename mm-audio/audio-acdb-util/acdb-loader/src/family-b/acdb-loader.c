/*
 *
 * This library contains the API to load the audio calibration
 * data from database and push to the DSP
 *
 * Copyright (c) 2012-2017 QUALCOMM Technologies, Inc.
 * All Rights Reserved.
 * Confidential and Proprietary - Qualcomm Technologies, Inc.
 *
 */

#include <sys/stat.h>
#include <stdbool.h>
#include <stdint.h>
#include <errno.h>
#include <fcntl.h>
#include <unistd.h>
#include <pthread.h>
#include <string.h>
#include <dirent.h>
#include <linux/msm_ion.h>
#include <linux/msm_audio.h>
#include <linux/msm_audio_calibration.h>
#include <linux/mfd/wcd9xxx/wcd9320_registers.h>

#define u32 uint32_t
#define u16 uint16_t
#define u8 uint8_t
#define CODEC_PACKED_REG_SIZE sizeof(u32)

#include <linux/mfd/msm-adie-codec.h>
#include <sys/mman.h>

#ifdef _ANDROID_
#include <cutils/properties.h>
/* definitions for Android logging */
#include <utils/Log.h>
#include "common_log.h"
#else /* _ANDROID_ */
#define LOGI(...)      fprintf(stdout,__VA_ARGS__)
#define LOGE(...)      fprintf(stderr,__VA_ARGS__)
#define LOGV(...)      fprintf(stderr,__VA_ARGS__)
#define LOGD(...)      fprintf(stderr,__VA_ARGS__)
#endif /* _ANDROID_ */

#include "acdb.h"
#include "acph.h"
#include "acdb-rtac.h"
#include "adie-rtac.h"
#ifdef _ANDROID_
#include "acdb-fts.h"
#include "acdb-mcs.h"
#else /* _ANDROID_ */
#include "sound/msmcal-hwdep.h"
#endif /* _ANDROID_ */
#include "acdb-loader.h"
#include "acdb-anc-general.h"
#include "acdb-anc-taiko.h"
#include "acdb-id-mapper.h"
#include "vbat_map_api.h"
#undef LOG_NDDEBUG
#undef LOG_TAG
#define LOG_NDDEBUG 0
#define LOG_TAG "ACDB-LOADER"

#define INVALID_DATA	-1

#define APR_DOMAIN_ADSP     4
#define ADSP_ASM_SERVICE    0x07
#define ADSP_ADM_SERVICE    0x08
#define MAX_ACTIVE_COPP     8

#define TEMP_CAL_BUFSZ 1024 /* 1K should be plenty */
#define MAX_ACDB_FILES		20

/* Number of ANC device configurations */
#define NUM_OF_ANC_TUNING_CONFIG 2
#define NUM_OF_ANC_RX_CONFIG 6
#define NUM_OF_AANC_RX_CONFIG 1

#define EC_REF_RX_DEVS (sizeof(uint32_t) * 20)

#define round(val) ((val > 0) ? (val + 0.5) : (val - 0.5))

#ifdef AUDIO_USE_SYSTEM_HEAP_ID
#define HEAP_MASK	ION_HEAP(ION_SYSTEM_HEAP_ID)
#else
#define HEAP_MASK	ION_HEAP(ION_AUDIO_HEAP_ID)
#endif

#define MIN_BUFF_SIZE 4096 /* Incremental memory to accomodate header size for tables */

#define STUB_VBAT 1

#ifndef _ANDROID_
#define MAX_CAL_NAME 20

char cal_name_info[WCD9XXX_MAX_CAL][MAX_CAL_NAME] = {
	[WCD9XXX_ANC_CAL] = "anc_cal",
	[WCD9XXX_MBHC_CAL] = "mbhc_cal",
	[WCD9XXX_VBAT_CAL] = "vbat_cal",
};
#endif

static vbat_adc_data_t vbat_adc_data;

enum {
	DEFAULT_MAX_CAL_TABLE_SIZE = 4096,
	ANC_MAX_CODEC_CAL_TABLE_SIZE = 16384,
};

enum {
	BUFF_IDX_0 = 0,
};

enum {
	NUM_VOICE_CAL_TABLES = 9,
};

enum {
        ERR_AUD_CAL_NOCOPP = 100,
        ERR_AUD_CAL_NODEVICE,
};

enum {
        AUDIO_DEVICE_CAL_TYPE = 0,
        AUDIO_STREAM_CAL_TYPE,
};

typedef struct acdb_audio_cal_cfg {
        uint32_t persist;
        uint32_t snd_dev_id;
        uint32_t dev_id;
        int32_t acdb_dev_id;
        uint32_t app_type;
        uint32_t topo_id;
        uint32_t sampling_rate;
        uint32_t cal_type;
        uint32_t module_id;
        uint32_t param_id;
} acdb_audio_cal_cfg_t;

struct param_data {
        int     use_case;
        int     acdb_id;
        int     get_size;
        int     buff_size;
        int     data_size;
        void    *buff;
};

struct cal_block {
	int		size;
	bool		is_mapped;
	int		*vaddr;
	int		map_handle;
	int		alloc_handle;
	int		version;
};

struct cal_node {
	int			buffer_number;
	struct cal_node		*next;
	struct cal_block	*cal_block;
};

struct cal_list {
	struct cal_node		*next;
	int			num_static_buffs;
};

#ifdef STUB_VBAT
struct vbat_cal_t {
	uint16_t	version;
	uint16_t	Auto_Cal_Enable;
	uint16_t	D_Slope;
	int16_t		D_Bias;
	uint16_t	monitor_version;
	uint16_t	Vbat_Mode;
	uint16_t	Vbat_Alpha_Select;
	uint16_t	Vbat_Rx_Peak_Block_Select;
	int16_t		Vbat_Rx_Signal_Threshold;
	uint16_t	Vbat_RF_Vbat_Down;
	uint16_t	Vbat_RF_Vbat_Trip_Point;
	uint16_t	Vbat_Trip_Point;
	uint16_t	Vbat_Max_Attenuation;
	int16_t		Vbat_Max_Limiter_Level;
	uint16_t	Vbat_Slope_Select;
	uint16_t	Vbat_Attack_Time;
	uint16_t	Vbat_Release_Time;
	uint16_t	Vbat_dBStep;
};
struct vbat_cal_t vbat_cal = {0x0,0x0,0x100,0x0,0xF,0x01,0x0,
	0x14,0x28,0x66,0x380,0x34D,0x18,0x18,0x02,0x10,0x7D00,0x01};

#endif

struct cal_list		cal_data[MAX_CAL_TYPES] = {
	{0, 1},			/* CVP_VOC_RX_TOPOLOGY_CAL_TYPE */
	{0, 1},			/* CVP_VOC_TX_TOPOLOGY_CAL_TYPE */
	{0, 1},			/* CVP_VOCPROC_STATIC_CAL_TYPE */
	{0, 1},			/* CVP_VOCPROC_DYNAMIC_CAL_TYPE */
	{0, 1},			/* CVS_VOCSTRM_STATIC_CAL_TYPE */
	{0, 1},			/* CVP_VOCDEV_CFG_CAL_TYPE */
	{0, 1},			/* CVP_VOCPROC_STATIC_COL_CAL_TYPE */
	{0, 1},			/* CVP_VOCPROC_DYNAMIC_COL_CAL_TYPE */
	{0, 1},			/* CVS_VOCSTRM_STATIC_COL_CAL_TYPE */
	{0, 2},			/* ADM_TOPOLOGY_CAL_TYPE */
	{0, 1},			/* ADM_CUST_TOPOLOGY_CAL_TYPE */
	{0, 2},			/* ADM_AUDPROC_CAL_TYPE */
	{0, 2},			/* ADM_AUDVOL_CAL_TYPE */
	{0, 1},			/* ASM_TOPOLOGY_CAL_TYPE */
	{0, 1},			/* ASM_CUST_TOPOLOGY_CAL_TYPE */
	{0, 1},			/* ASM_AUDSTRM_CAL_TYPE */
	{0, 1},			/* AFE_COMMON_RX_CAL_TYPE */
	{0, 1},			/* AFE_COMMON_TX_CAL_TYPE */
	{0, 1},			/* AFE_ANC_CAL_TYPE */
	{0, 1},			/* AFE_AANC_CAL_TYPE */
	{0, 1},			/* AFE_FB_SPKR_PROT_CAL_TYPE */
	{0, 2},			/* AFE_HW_DELAY_CAL_TYPE */
	{0, 1},			/* AFE_SIDETONE_CAL_TYPE */
	{0, 1},			/* LSM_CUST_TOPOLOGY_CAL_TYPE */
	{0, 1},			/* LSM_TOPOLOGY_CAL_TYPE */
	{0, 1},			/* LSM_CAL_TYPE */
	{0, 1},			/* ADM_RTAC_INFO_CAL_TYPE */
	{0, 1},			/* VOICE_RTAC_INFO_CAL_TYPE */
	{0, 1},			/* ADM_RTAC_APR_CAL_TYPE */
	{0, 1},			/* ASM_RTAC_APR_CAL_TYPE */
	{0, 1},			/* VOICE_RTAC_APR_CAL_TYPE */
	{0, 1},			/* MAD_CAL_TYPE */
	{0, 1},			/* ULP_AFE_CAL_TYPE */
	{0, 1},			/* ULP_LSM_CAL_TYPE */
	{0, 1},			/* DTS_EAGLE_CAL_TYPE */
	{0, 1},			/* AUDIO_CORE_METAINFO_CAL_TYPE */
        {0, 1},                 /* SRS_TRUMEDIA_CAL_TYPE */
	{0, 1}			/* CORE_CUSTOM_TOPOLOGIES_CAL_TYPE */
};

struct cal_block	mem_data[MAX_CAL_TYPES] = {
	{0, false, 0, -1, 0, VERSION_0_0},		/* CVP_VOC_RX_TOPOLOGY_CAL_TYPE */
	{0, false, 0, -1, 0, VERSION_0_0},		/* CVP_VOC_TX_TOPOLOGY_CAL_TYPE */
	{245760, true, 0, -1, 0, VERSION_0_0},		/* CVP_VOCPROC_STATIC_CAL_TYPE */
	{61440, true, 0, -1, 0, VERSION_0_0},		/* CVP_VOCPROC_DYNAMIC_CAL_TYPE */
	{61440, true, 0, -1, 0, VERSION_0_0},		/* CVS_VOCSTRM_STATIC_CAL_TYPE */
	{4096, true, 0, -1, 0, VERSION_0_0},		/* CVP_VOCDEV_CFG_CAL_TYPE */
	{0, false, 0, -1, 0, VERSION_0_0},		/* CVP_VOCPROC_STATIC_COL_CAL_TYPE */
	{0, false, 0, -1, 0, VERSION_0_0},		/* CVP_VOCPROC_DYNAMIC_COL_CAL_TYPE */
	{0, false, 0, -1, 0, VERSION_0_0},		/* CVS_VOCSTRM_STATIC_COL_CAL_TYPE */
	{0, false, 0, -1, 0, VERSION_0_0},		/* ADM_TOPOLOGY_CAL_TYPE */
	{4096, true, 0, -1, 0, VERSION_0_0},		/* ADM_CUST_TOPOLOGY_CAL_TYPE */
	{16384, true, 0, -1, 0, VERSION_0_0},		/* ADM_AUDPROC_CAL_TYPE */
	{4096, true, 0, -1, 0, VERSION_0_0},		/* ADM_AUDVOL_CAL_TYPE */
	{0, false, 0, -1, 0, VERSION_0_0},		/* ASM_TOPOLOGY_CAL_TYPE */
	{4096, true, 0, -1, 0, VERSION_0_0},		/* ASM_CUST_TOPOLOGY_CAL_TYPE */
	{4096, true, 0, -1, 0, VERSION_0_0},		/* ASM_AUDSTRM_CAL_TYPE */
	{4096, true, 0, -1, 0, VERSION_0_0},		/* AFE_COMMON_RX_CAL_TYPE */
	{4096, true, 0, -1, 0, VERSION_0_0},		/* AFE_COMMON_TX_CAL_TYPE */
	{4096, false, 0, -1, 0, VERSION_0_0},		/* AFE_ANC_CAL_TYPE */
	{4096, true, 0, -1, 0, VERSION_0_0},		/* AFE_AANC_CAL_TYPE */
	{0, false, 0, -1, 0, VERSION_0_0},		/* AFE_FB_SPKR_PROT_CAL_TYPE */
	{0, false, 0, -1, 0, VERSION_0_0},		/* AFE_HW_DELAY_CAL_TYPE */
	{0, false, 0, -1, 0, VERSION_0_0},		/* AFE_SIDETONE_CAL_TYPE */
	{4096, true, 0, -1, 0, VERSION_0_0},		/* LSM_CUST_TOPOLOGY_CAL_TYPE */
	{0, false, 0, -1, 0, VERSION_0_0},		/* LSM_TOPOLOGY_CAL_TYPE */
	{4096, true, 0, -1, 0, VERSION_0_0},		/* LSM_CAL_TYPE */
	{0, false, 0, -1, 0, VERSION_0_0},		/* ADM_RTAC_INFO_CAL_TYPE */
	{0, false, 0, -1, 0, VERSION_0_0},		/* VOICE_RTAC_INFO_CAL_TYPE */
	{0, false, 0, -1, 0, VERSION_0_0},		/* ADM_RTAC_APR_CAL_TYPE */
	{0, false, 0, -1, 0, VERSION_0_0},		/* ASM_RTAC_APR_CAL_TYPE */
	{0, false, 0, -1, 0, VERSION_0_0},		/* VOICE_RTAC_APR_CAL_TYPE */
	{4096, false, 0, -1, 0, VERSION_0_0},		/* MAD_CAL_TYPE */
	{4096, true, 0, -1, 0, VERSION_0_0},		/* ULP_AFE_CAL_TYPE */
	{4096, true, 0, -1, 0, VERSION_0_0},		/* ULP_LSM_CAL_TYPE */
	{0, false, 0, -1, 0,VERSION_0_0},		/* DTS_EAGLE_CAL_TYPE */
	{0, false, 0, -1, 0,VERSION_0_0},		/* AUDIO_CORE_METAINFO_CAL_TYPE */
	{0, false, 0, -1, 0,VERSION_0_0},		/* SRS_TRUMEDIA_CAL_TYPE */
	{4096, true, 0, -1, 0,VERSION_0_0}		/* CORE_CUSTOM_TOPOLOGIES_CAL_TYPE */
};

enum {
	PER_VOCODER_VERSION = 0,
	MAX_CVD_VERSION
};

double cvd_version_table[] = {
	2.1
};

static int voice_cal_types[NUM_VOICE_CAL_TABLES] = {
	CVP_VOC_RX_TOPOLOGY_CAL_TYPE,
	CVP_VOC_TX_TOPOLOGY_CAL_TYPE,
	CVP_VOCPROC_STATIC_CAL_TYPE,
	CVP_VOCPROC_DYNAMIC_CAL_TYPE,
	CVS_VOCSTRM_STATIC_CAL_TYPE,
	CVP_VOCPROC_STATIC_COL_CAL_TYPE,
	CVP_VOCPROC_DYNAMIC_COL_CAL_TYPE,
	CVS_VOCSTRM_STATIC_COL_CAL_TYPE,
	CVP_VOCDEV_CFG_CAL_TYPE
};

static int		cal_driver_handle;
static int		ion_handle;
static bool		is_initialized;
static uint32_t		current_feature_set;
static uint32_t		current_voice_tx_acdb_id;
static uint32_t		current_voice_rx_acdb_id;

static unsigned int acdb_init_ref_cnt = 0;
static int			mMetaInfoKey = 0;
static char			mSnd_card_name[100];
static char			mCvd_version[20];

pthread_mutex_t loader_mutex = PTHREAD_MUTEX_INITIALIZER;

static int allocate_cal_block(int cal_type,
				int buffer_number,
				struct audio_cal_alloc *audio_alloc,
				struct ion_allocation_data *alloc_data,
				struct cal_block *cal_block);
static int get_voctable_size(int rxacdb_id, int txacdb_id, int rxacdb_id_sample_rate, int txacdb_id_sample_rate);
static int get_vocvoltable_size(int rxacdb_id, int txacdb_id, int feature_set);
static int get_vocstrmtable_size();
static int get_audtable_size(int acdb_id, int app_id, int sample_rate);
int send_wcd9xxx_anc_cal(struct param_data *params);
int send_meta_info(int metaInfoKey);


static bool cvd_version_supported(char *cvd_version, int version_id)
{
	bool ret = false;
	double cvd_version_num = 0;

	if (cvd_version == NULL) {
		goto done;
	} else if ((version_id >= MAX_CVD_VERSION) || (version_id < 0)) {
		LOGE("ACDB -> %s: CVD version %d not supported!", __func__, version_id);
		goto done;
	}

	/* format of version should be #.# */
	if (strstr(cvd_version, ".") == NULL)
		goto done;

	cvd_version_num = atof(cvd_version);
	if (cvd_version_num >= cvd_version_table[version_id])
		ret = true;
done:
	return ret;
}

static struct cal_node *create_cal_node(int cal_type, int buffer_number)
{
	struct cal_node		*cal_head = cal_data[cal_type].next;
	struct cal_node		*cal_node = NULL;

	cal_node = malloc(sizeof(*cal_node));
	if (cal_node == NULL) {
		LOGE("ACDB -> %s: Could not allocated cal_node for cal type %d!",
			__func__, cal_type);
		goto done;
	}

	cal_node->cal_block = malloc(sizeof(*cal_node->cal_block));
	if (cal_node->cal_block == NULL) {
		LOGE("ACDB -> %s: Could not allocated cal_block for cal type %d!",
			__func__, cal_type);
		goto done;
	}

	cal_node->buffer_number = buffer_number;
	*cal_node->cal_block = mem_data[cal_type];
	cal_node->next = NULL;

	if (cal_head == NULL) {
		cal_data[cal_type].next = cal_node;
	} else {
		while (cal_head->next != NULL) {
			cal_head = cal_head->next;
			LOGE("ACDB -> %s: New Node!", __func__);
		}
		cal_head->next = cal_node;
	}
done:
	return cal_node;
}

static void delete_cal_node(int cal_type, int buffer_number)
{
	struct cal_node		*cal_node = NULL;
	struct cal_node		*cal_head = cal_data[cal_type].next;

	while (cal_head != NULL) {
		if (cal_head->buffer_number == buffer_number) {
			cal_node = cal_head;
			cal_head = cal_head->next;
			break;
		}
		cal_head = cal_head->next;
	}
	if (cal_node == NULL)
		goto done;

	free(cal_node->cal_block);
	cal_node->cal_block = NULL;
	free(cal_node);
	cal_node = NULL;
done:
	return;
}

static void delete_cal_list(int cal_type)
{
	struct cal_node		*cal_node = NULL;
	struct cal_node		*cal_head = cal_data[cal_type].next;

	while (cal_head != NULL) {
		cal_node = cal_head;
		cal_head = cal_head->next;
		free(cal_node->cal_block);
		cal_node->cal_block = NULL;
		free(cal_node);
		cal_node = NULL;
	}
	cal_data[cal_type].next = NULL;
}

static struct cal_node *get_cal_node(int cal_type, int buffer_number)
{
	struct cal_node		*cal_node = cal_data[cal_type].next;

	while (cal_node != NULL) {
		if (cal_node->buffer_number == buffer_number)
			break;
		cal_node = cal_node->next;
	}
done:
	return cal_node;
}

static struct cal_node *create_node_and_alloc(int cal_type, int buffer_number)
{
	int ret;
	struct cal_node			*cal_node = NULL;
	struct ion_allocation_data	alloc_data;
	struct audio_cal_alloc		audio_alloc;

	cal_node = create_cal_node(cal_type, buffer_number);
	if (cal_node == NULL)
		goto done;

	audio_alloc.hdr.data_size = sizeof(audio_alloc);
	audio_alloc.hdr.version = VERSION_0_0;
	audio_alloc.hdr.cal_type_size = sizeof(audio_alloc.cal_type);
	audio_alloc.cal_type.cal_hdr.version = VERSION_0_0;

	alloc_data.align = 0x1000;
	alloc_data.heap_id_mask = HEAP_MASK;
	alloc_data.flags = 0;

	ret = allocate_cal_block(cal_type,
		cal_node->buffer_number,
		&audio_alloc,
		&alloc_data, cal_node->cal_block);
	if (ret < 0)
		LOGE("ACDB -> allocate_cal_block failed!\n");
done:
	return cal_node;
}


static struct cal_block *get_cal_block(int cal_type, int buffer_number)
{
	struct cal_node		*cal_node = NULL;
	struct cal_block	*cal_block = NULL;

	cal_node = get_cal_node(cal_type, buffer_number);
	if (cal_node == NULL)
		cal_node = create_node_and_alloc(cal_type, buffer_number);
	if (cal_node != NULL)
		cal_block = cal_node->cal_block;

	return cal_block;
}

#ifdef _ANDROID_
static int get_files_from_properties(AcdbInitCmdType *acdb_init_cmd)
{
	int i = 0;
	int prop_len;
	char prop_name[24];

	for (i=0; i < MAX_ACDB_FILES; i++) {
		if (snprintf(prop_name, sizeof(prop_name), "persist.audio.calfile%d", i) < 0)
			goto done;

		prop_len = property_get(prop_name, acdb_init_cmd->acdbFiles[i].fileName, NULL);
		if (prop_len <= 0)
			goto done;

		acdb_init_cmd->acdbFiles[i].fileNameLen = strlen(acdb_init_cmd->acdbFiles[i].fileName);
		LOGD("ACDB -> Prop Load file: %s\n", acdb_init_cmd->acdbFiles[i].fileName);
	}
done:
	acdb_init_cmd->nNoOfFiles = i;
	return i;
}
#else
static int get_files_from_properties(AcdbInitCmdType *acdb_init_cmd) {return -ENODEV;}
#endif

static int send_common_custom_topology(void);
static int send_adm_custom_topology(void);
static int send_asm_custom_topology(void);
static int send_lsm_custom_topology(void);

static int get_acdb_files_in_directory(AcdbInitCmdType *acdb_init_cmd, char *dir_path)
{
	int result = 0;
	int i = 0;
	DIR *dir_fp = NULL;
	struct dirent *dentry;

	dir_fp = opendir(dir_path);
	if (dir_fp == NULL)
		goto done;

	/* search directory for .acdb files */
	while ((dentry = readdir(dir_fp)) != NULL) {
		if ((strstr(dentry->d_name, ".acdb") != NULL) ||
		    (strstr(dentry->d_name, ".qwsp") != NULL)) {
			if (acdb_init_cmd->nNoOfFiles >= MAX_ACDB_FILES) {
				LOGD("ACDB -> Maximum number of ACDB files hit, %d!\n", i);
				break;
			}

			result = snprintf(acdb_init_cmd->acdbFiles[acdb_init_cmd->nNoOfFiles].fileName,
					sizeof(acdb_init_cmd->acdbFiles[acdb_init_cmd->nNoOfFiles].fileName),
					"%s/%s", dir_path, dentry->d_name);
			if (result < 0) {
				LOGD("ACDB -> Error: snprintf load file failed: %s/%s, err %d\n",
					dir_path, acdb_init_cmd->acdbFiles[acdb_init_cmd->nNoOfFiles].fileName, result);
				continue;
			}

			acdb_init_cmd->acdbFiles[acdb_init_cmd->nNoOfFiles].fileNameLen =
				strlen(acdb_init_cmd->acdbFiles[acdb_init_cmd->nNoOfFiles].fileName);
			LOGD("ACDB -> Load file: %s\n", acdb_init_cmd->acdbFiles[acdb_init_cmd->nNoOfFiles].fileName);
			i++;
			acdb_init_cmd->nNoOfFiles++;
		}
	}

	if (i == 0)
		LOGD("ACDB -> No .acdb files found in %s!\n", dir_path);


	closedir(dir_fp);
done:
	return i;
}

static int get_files_from_device_tree(AcdbInitCmdType *acdb_init_cmd, char *snd_card_name)
{
	int result = 0;
	char dir_path[300];
	char board_type[64] = DEFAULT_BOARD;
	FILE *fp = NULL;

	/* Get Board type */
	fp = fopen("/sys/devices/soc0/hw_platform","r");
	if (fp == NULL)
		fp = fopen("/sys/devices/system/soc/soc0/hw_platform","r");
	if (fp == NULL)
		LOGE("ACDB -> Error: Couldn't open hw_platform\n");
	else if (fgets(board_type, sizeof(board_type), fp) == NULL)
		LOGE("ACDB -> Error: Couldn't get board type\n");
	else if (board_type[(strlen(board_type) - 1)] == '\n')
		board_type[(strlen(board_type) - 1)] = '\0';
	if (fp != NULL)
		fclose(fp);

	/* Try board directory with soundcard name */
	if (snd_card_name != NULL) {
		result = snprintf(dir_path, sizeof(dir_path), "%s%s/%s", ACDB_BIN_PATH, board_type, snd_card_name);
		if (result < 0) {
			LOGE("ACDB -> Error: snprintf failed for snd card %s, error: %d\n", snd_card_name, result);
			result = -ENODEV;
			goto done;
		}
		if (get_acdb_files_in_directory(acdb_init_cmd, dir_path) > 0)
			goto done;
	}

	/* Try board type directory */
	result = snprintf(dir_path, sizeof(dir_path), "%s%s", ACDB_BIN_PATH, board_type);
	if (result < 0) {
		LOGE("ACDB -> Error: snprintf failed for board type dir, error: %d\n", result);
		result = -ENODEV;
		goto done;
	}
	if (get_acdb_files_in_directory(acdb_init_cmd, dir_path) > 0)
		goto done;

	/* Try default directory with soundcard name */
	if ((snd_card_name != NULL) && (strcmp(DEFAULT_BOARD, board_type) != 0)) {
		result = snprintf(dir_path, sizeof(dir_path), "%s%s/%s", ACDB_BIN_PATH, DEFAULT_BOARD, snd_card_name);
		if (result < 0) {
			LOGE("ACDB -> Error: snprintf failed for snd_card default dir, error: %d\n", result);
			result = -ENODEV;
			goto done;
		}
		if (get_acdb_files_in_directory(acdb_init_cmd, dir_path) > 0)
			goto done;
	}

	/* Try default directory */
	if (strcmp(DEFAULT_BOARD, board_type) != 0) {
		result = snprintf(dir_path, sizeof(dir_path), "%s%s", ACDB_BIN_PATH, DEFAULT_BOARD);
		if (result < 0) {
			LOGE("ACDB -> Error: snprintf failed for default dir, error: %d\n", result);
			result = -ENODEV;
			goto done;
		}
		if (get_acdb_files_in_directory(acdb_init_cmd, dir_path) > 0)
			goto done;
	}

	/* Try etc directory */
	result = snprintf(dir_path, sizeof(dir_path), "%s", ETC_ROOT_PATH);
	if (result < 0) {
		LOGE("ACDB -> Error: snprintf failed for board type dir, error: %d\n", result);
		result = -ENODEV;
		goto done;
	}
	if (get_acdb_files_in_directory(acdb_init_cmd, dir_path) > 0)
			goto done;

	LOGE("ACDB -> Error: Could not open any directory! Failed for %s\n", dir_path);
	result = -ENODEV;

done:
	return result;
}

static int acdb_load_files(AcdbInitCmdType *acdb_init_cmd, char * snd_card_name)
{
	int result = 0;

	result = get_files_from_properties(acdb_init_cmd);
	if (result > 0)
		goto done;

	result = get_files_from_device_tree(acdb_init_cmd, snd_card_name);
done:
	return result;
}

static void deallocate_cal_block(int cal_type,
				int buffer_number,
				struct audio_cal_dealloc *audio_dealloc,
				struct cal_block *cal_block)
{
	int				ret = 0;

	if (!cal_block->is_mapped) {
		if (cal_block->size != 0)
			free(cal_block->vaddr);
		goto done;
	} else if (cal_block->map_handle < 0)
		goto done;

	audio_dealloc->hdr.cal_type = cal_type;
	audio_dealloc->cal_type.cal_hdr.buffer_number = buffer_number;
	audio_dealloc->cal_type.cal_data.mem_handle = cal_block->map_handle;
	ret = ioctl(cal_driver_handle, AUDIO_DEALLOCATE_CALIBRATION,
		audio_dealloc);
	if (ret) {
		LOGE("ACDB -> Error: Sending AUDIO_DEALLOCATE_CALIBRATION, result = %d",
			ret);
	}

	munmap(cal_block->vaddr, cal_block->size);
	close(cal_block->map_handle);
	ret = ioctl(ion_handle, ION_IOC_FREE,
		&cal_block->alloc_handle);
	if (ret)
		LOGE("ACDB -> %s: ION_IOC_FREE, cal type %d, errno: %d",
			__func__, cal_type, ret);
done:
	return;
}

static void deallocate_memory(void)
{
	int				i;
	struct cal_node			*cal_node = NULL;
	struct audio_cal_dealloc	audio_dealloc;

	audio_dealloc.hdr.data_size = sizeof(audio_dealloc);
	audio_dealloc.hdr.version = VERSION_0_0;
	audio_dealloc.hdr.cal_type_size = sizeof(audio_dealloc.cal_type);
	audio_dealloc.cal_type.cal_hdr.version = VERSION_0_0;

	for (i=0; i<MAX_CAL_TYPES; i++) {
		cal_node = get_cal_node(i, BUFF_IDX_0);
		audio_dealloc.cal_type.cal_hdr.version = mem_data[i].version;
		while (cal_node != NULL) {
			deallocate_cal_block(i,
				cal_node->buffer_number,
				&audio_dealloc,
				cal_node->cal_block);
			cal_node = cal_node->next;
		}
		delete_cal_list(i);
	}
	close(ion_handle);
}

static void deallocate_cal_type(int cal_type_idx, int buffer_number)
{
        struct cal_node                 *cal_node = NULL;
        struct audio_cal_dealloc        audio_dealloc;

        audio_dealloc.hdr.data_size = sizeof(audio_dealloc);
        audio_dealloc.hdr.version = VERSION_0_0;
        audio_dealloc.hdr.cal_type_size = sizeof(audio_dealloc.cal_type);
        audio_dealloc.cal_type.cal_hdr.version = VERSION_0_0;

        cal_node = get_cal_node(cal_type_idx, buffer_number);
        audio_dealloc.cal_type.cal_hdr.version = mem_data[cal_type_idx].version;

        /* Deallocate cal block */
        deallocate_cal_block(cal_type_idx, cal_node->buffer_number,
                              &audio_dealloc, cal_node-> cal_block);
}

static int allocate_cal_block(int cal_type,
				int buffer_number,
				struct audio_cal_alloc *audio_alloc,
				struct ion_allocation_data *alloc_data,
				struct cal_block *cal_block)
{
	int				ret = 0;
	struct ion_fd_data		fd_data;

	if (!cal_block->is_mapped) {
		if (cal_block->size > 0) {
			cal_block->vaddr = malloc(cal_block->size);
			if (cal_block->vaddr == NULL) {
			LOGE("ACDB -> %s: malloc failed, cal type %d",
				__func__, cal_type);
				ret = -ENOMEM;
				goto done;
			}
		}
		goto done;
	} else if (cal_block->size <= 0) {
		LOGE("ACDB -> %s: Allocation request for cal type %d, but size is %d!",
			__func__, cal_type, cal_block->size);
			ret = -ENOMEM;
			goto done;
	}

	if (cal_block->map_handle > 0) {
		LOGE("ACDB -> %s: mmap", __func__);
		cal_block->vaddr = (int *)mmap(0, cal_block->size,
			PROT_READ | PROT_WRITE, MAP_SHARED,
			cal_block->map_handle, 0);
		if (cal_block->vaddr != MAP_FAILED)
			goto done;
	}

	alloc_data->len = cal_block->size;
	ret = ioctl(ion_handle, ION_IOC_ALLOC, alloc_data);
	if (ret) {
		LOGE("ACDB -> %s: ION_ALLOC, cal type %d, errno: %d\n",
			__func__, cal_type, ret);
		ret = -ENOMEM;
		goto done;
	}

	fd_data.handle = alloc_data->handle;
	cal_block->alloc_handle = (int)alloc_data->handle;
	ret = ioctl(ion_handle, ION_IOC_SHARE, &fd_data);
	if (ret) {
		LOGE("ACDB -> %s: ION_IOC_SHARE, cal type %d, errno: %d\n",
			__func__, cal_type, ret);
		ret = -ENOMEM;
		goto done;
	}

	cal_block->map_handle = fd_data.fd;
	cal_block->vaddr = (int *)mmap(0, cal_block->size,
		PROT_READ | PROT_WRITE, MAP_SHARED,
		cal_block->map_handle, 0);
	if (cal_block->vaddr == MAP_FAILED) {
		LOGE("ACDB -> %s: Cannot allocate ION, cal type %d",
			__func__, cal_type);
		ret = -ENOMEM;
		goto done;
	}

	audio_alloc->hdr.cal_type = cal_type;
	audio_alloc->cal_type.cal_hdr.buffer_number = buffer_number;
	audio_alloc->cal_type.cal_data.mem_handle = cal_block->map_handle;
	ret = ioctl(cal_driver_handle, AUDIO_ALLOCATE_CALIBRATION,
		audio_alloc);
	if (ret) {
		LOGE("ACDB -> Error: Sending AUDIO_ALLOCATE_CALIBRATION, result = %d\n",
			ret);
		goto done;
	}

done:
	return ret;
}

static void add_initial_nodes(int cal_type)
{
	int			i;
	struct cal_node		*cal_node = NULL;

	for (i=0; i < cal_data[cal_type].num_static_buffs; i++) {
		if (create_cal_node(cal_type, i) == NULL) {
			LOGE("ACDB -> Error: Could not allocate cal node %d for cal type %d\n",
				i, cal_type);
			goto done;
		}
	}
done:
	return;
}

static int allocate_memory(void)
{
	int				ret = 0;
	int				i;
	struct cal_node			*cal_node = NULL;
	struct ion_allocation_data	alloc_data;
	struct audio_cal_alloc		audio_alloc;

	ion_handle = open("/dev/ion", O_RDONLY | O_DSYNC);
	if (ion_handle < 0) {
		LOGE("Cannot open /dev/ion errno: %d\n", ion_handle);
		ret = -ENODEV;
		goto done;
	}

	audio_alloc.hdr.data_size = sizeof(audio_alloc);
	audio_alloc.hdr.version = VERSION_0_0;
	audio_alloc.hdr.cal_type_size = sizeof(audio_alloc.cal_type);
	audio_alloc.cal_type.cal_hdr.version = VERSION_0_0;

	alloc_data.align = 0x1000;
	alloc_data.heap_id_mask = HEAP_MASK;
	alloc_data.flags = 0;

	for (i=0; i<MAX_CAL_TYPES; i++) {
		add_initial_nodes(i);
		cal_node = cal_data[i].next;
		audio_alloc.cal_type.cal_hdr.version = mem_data[i].version;
		while (cal_node != NULL) {
			ret = allocate_cal_block(i,
				cal_node->buffer_number,
				&audio_alloc,
				&alloc_data, cal_node->cal_block);
			if (ret < 0) {
				LOGE("ACDB -> allocate_cal_block failed!\n");
				goto err;
			}
			cal_node = cal_node->next;
		}
	}
done:
	return ret;
err:
	deallocate_memory();
	return ret;
}

static int allocate_cal_type(int cal_type_idx, int size, int buffer_number)
{
        int                             ret = 0;
        struct cal_node                 *cal_node = NULL;
        struct ion_allocation_data      alloc_data;
        struct audio_cal_alloc          audio_alloc;

        audio_alloc.hdr.data_size = sizeof(audio_alloc);
        audio_alloc.hdr.version = VERSION_0_0;
        audio_alloc.hdr.cal_type_size = sizeof(audio_alloc.cal_type);
        audio_alloc.cal_type.cal_hdr.version = VERSION_0_0;

        alloc_data.align = 0x1000;
        alloc_data.heap_id_mask = HEAP_MASK;
        alloc_data.flags = 0;

        cal_node = get_cal_node(cal_type_idx, buffer_number);
        cal_node->cal_block->size = size;
        audio_alloc.cal_type.cal_hdr.version = mem_data[cal_type_idx].version;

        /* Allocate cal block */
        ret = allocate_cal_block(cal_type_idx, cal_node->buffer_number, &audio_alloc,
                                  &alloc_data, cal_node->cal_block);
        if (ret < 0)
            LOGE("Error: ACDB -> allocate_cal_block failed!\n");

        return ret;
}

int acdb_loader_init_v2(char *snd_card_name, char *cvd_version, int metaInfoKey)
{
	int				ret = 0;
	int				i;
	int				result = 0;
	AcdbVocProcGainDepVolTblSizeV2CmdType	vocvoltablesize;
	AcdbSizeResponseType		response;
	AcdbInitCmdType			acdb_init_cmd;
	AcdbGetMetaInfoSizeCmdType metaInfoSizeCmd;
	AcdbSizeResponseType metaInfoSize;

        if (snd_card_name != NULL)
		memcpy(mSnd_card_name, snd_card_name, strlen(snd_card_name));
	if (cvd_version != NULL)
		memcpy(mCvd_version, cvd_version, strlen(cvd_version));
	mMetaInfoKey = metaInfoKey;

	LOGD("ACDB_CMD_INITIALIZE_V2 snd_card = %s, cvd_ver = %s, key =%d\n",
		snd_card_name, cvd_version, metaInfoKey);

        pthread_mutex_lock(&loader_mutex);
        if (acdb_init_ref_cnt != 0) {
            acdb_init_ref_cnt++;
            LOGD("ACDB -> already initialized, exit");
            goto done;
        }

	if (acdb_load_files(&acdb_init_cmd, snd_card_name) <= 0) {
		LOGE("ACDB -> Could not load .acdb files!\n");
		ret = -ENODEV;
		goto done;
	}

	ret = acdb_ioctl(ACDB_CMD_INITIALIZE_V2,
		(const uint8_t *)&acdb_init_cmd, sizeof(acdb_init_cmd), NULL, 0);
	if (ret) {
		LOGE("Error initializing ACDB returned = %d\n", ret);
		ret = -ENODEV;
		goto done;
	}

	LOGD("ACDB -> ACPH INIT\n");
	ret = acph_init();
	if (ret) {
		LOGE("Error initializing ACPH returned = %d\n", ret);
		ret = -ENODEV;
		goto done;
	}

	cal_driver_handle = open("/dev/msm_audio_cal", O_RDWR);
	if (cal_driver_handle < 0) {
		LOGE("ACDB -> Cannot open /dev/msm_audio_cal errno: %d\n", errno);
		ret = -ENODEV;
		goto done;
	}

	LOGD("ACDB -> RTAC INIT\n");
	acdb_rtac_init();

#ifdef _ANDROID_
	LOGD("ACDB -> MCS, FTS INIT\n");
	acdb_mcs_init();
	acdb_fts_init();
#endif /* _ANDROID_ */

	LOGD("ACDB -> ADIE RTAC INIT\n");
	adie_rtac_init();

	if (cvd_version_supported(cvd_version, PER_VOCODER_VERSION)) {
		vocvoltablesize.nTxDeviceId = DEVICE_HANDSET_TX_ACDB_ID;
		vocvoltablesize.nRxDeviceId = DEVICE_HANDSET_RX_ACDB_ID;
		vocvoltablesize.nFeatureId = ACDB_VOCVOL_FID_DEFAULT;

		LOGD("ACDB -> ACDB_CMD_GET_VOC_PROC_DYNAMIC_TABLE_SIZE\n");
		result = acdb_ioctl(ACDB_CMD_GET_VOC_PROC_DYNAMIC_TABLE_SIZE,
				(const uint8_t *)&vocvoltablesize,
				sizeof(vocvoltablesize),(uint8_t *)&response,
				sizeof(response));
		if (result == 0)
			for (i=0; i<NUM_VOICE_CAL_TABLES; i++)
				mem_data[voice_cal_types[i]].version |= PER_VOCODER_CAL_BIT_MASK;
	}
/*meta info*/
	metaInfoSizeCmd.nKey = metaInfoKey;
	LOGD("ACDB -> send_meta_info, lic %x\n", metaInfoSizeCmd.nKey);

	result = acdb_ioctl( ACDB_CMD_GET_META_INFO_SIZE,
		(const uint8_t *) &metaInfoSizeCmd, sizeof(metaInfoSizeCmd),
		(uint8_t *) &metaInfoSize, sizeof(metaInfoSize));

	if (!result) {
		LOGD("ACDB -> get metainfo size :%d\n", metaInfoSize.nSize);
		mem_data[AUDIO_CORE_METAINFO_CAL_TYPE].size = metaInfoSize.nSize;
		mem_data[AUDIO_CORE_METAINFO_CAL_TYPE].is_mapped = true;
	}

	if (allocate_memory() < 0) {
		LOGE("ACDB -> Cannot allocate memory!");
		ret = -ENOMEM;
		acdb_ioctl(ACDB_CMD_RESET, NULL, 0, NULL, 0);
		goto done;
	}

	acdb_loader_send_common_custom_topology();
        if (send_meta_info(metaInfoKey) < 0) {
		LOGD("ACDB ->send_meta_info failed\n");
	}

	current_feature_set = ACDB_VOCVOL_FID_DEFAULT;
	is_initialized = true;
	LOGD("ACDB -> init done!\n");
        acdb_init_ref_cnt++;

done:
        pthread_mutex_unlock(&loader_mutex);
	return ret;
}

int acdb_loader_init_v1(char *snd_card_name)
{
	memcpy(mSnd_card_name, snd_card_name, strlen(snd_card_name));
	return acdb_loader_init_v2(mSnd_card_name, mCvd_version, mMetaInfoKey);
}

int acdb_loader_init_ACDB(void)
{
	return acdb_loader_init_v2(mSnd_card_name, mCvd_version, mMetaInfoKey);
}

int acdb_loader_get_default_app_type(void)
{
	return ACDB_APPTYPE_GENERAL_PLAYBACK;
}

int acdb_loader_send_common_custom_topology(void)
{
	int ret = -EINVAL;

	ret = send_common_custom_topology();
	if (ret < 0) {
		LOGD("ACDB -> %s: Common custom topology not in use",
			__func__);
		send_adm_custom_topology();
		send_asm_custom_topology();
		send_lsm_custom_topology();
	} else {
		LOGD("ACDB -> %s: Common custom topology in use",
			__func__);
	}

	return ret;
}

static int get_audcal_path(uint32_t capability)
{
	int path;

	if (capability & MSM_SNDDEV_CAP_RX)
		path = RX_DEVICE;
	else if (capability & MSM_SNDDEV_CAP_TX)
		path = TX_DEVICE;
	else
		path = INVALID_DATA;

	return path;
}

int acdb_loader_set_codec_data(void *data, char *attr)
{
	int ret = 0;
	vbat_adc_data_t *param;

	if (strcmp("vbat_cal", attr) == 0) {
		param = (vbat_adc_data_t *) data;
		vbat_adc_data.dcp1 = param->dcp1;
		vbat_adc_data.dcp2 = param->dcp2;
	}

	return ret;
}

static void acdb_loader_get_codec_data(void **data, char *attr)
{
	if (strcmp("vbat_cal", attr) == 0) {
#ifdef _ANDROID_
		*data = &vbat_adc_data;
#else
		vbat_adc_data.dcp1 = 342;
		vbat_adc_data.dcp2 = 838;
		*data = &vbat_adc_data;
#endif
	}
	return;
}

static uint32_t get_samplerate(int  acdb_id)
{
	uint32_t sample_rate = 48000;

	if (((uint32_t)acdb_id == DEVICE_BT_SCO_RX_ACDB_ID) ||
		((uint32_t)acdb_id == DEVICE_BT_SCO_TX_ACDB_ID) ||
		((uint32_t)acdb_id == DEVICE_BT_SCO_TX_NREC_ACDB_ID)) {
		sample_rate = 8000;
	} else if (((uint32_t)acdb_id == DEVICE_BT_SCO_RX_WB_ACDB_ID) ||
		((uint32_t)acdb_id == DEVICE_BT_SCO_TX_WB_ACDB_ID) ||
		((uint32_t)acdb_id == DEVICE_BT_SCO_TX_WB_NREC_ACDB_ID)) {
		sample_rate = 16000;
                /*To change to 16000HZ*/
        }

	return sample_rate;
}

static int get_adm_topology(struct audio_cal_adm_top *adm_top)
{
	int				result = 0;
	AcdbGetAudProcTopIdCmdType	acdb_get_top;
	AcdbGetTopologyIdRspType	audio_top;

	acdb_get_top.nDeviceId = adm_top->cal_type.cal_info.acdb_id;
	acdb_get_top.nApplicationType = adm_top->cal_type.cal_info.app_type;

	LOGD("ACDB -> ACDB_CMD_GET_AUDPROC_COMMON_TOPOLOGY_ID\n");

	result = acdb_ioctl(ACDB_CMD_GET_AUDPROC_COMMON_TOPOLOGY_ID,
		(const uint8_t *)&acdb_get_top, sizeof(acdb_get_top),
		(uint8_t *)&audio_top, sizeof(audio_top));
	if (result) {
		LOGE("Error: ACDB get adm topology for acdb id = %d, returned = %d\n",
		     adm_top->cal_type.cal_info.acdb_id, result);
		adm_top->cal_type.cal_info.topology = 0;
		goto done;
	}
	adm_top->cal_type.cal_info.topology = audio_top.nTopologyId;
done:
	return result;
}

static int get_asm_topology(struct audio_cal_asm_top *audstrm_top)
{
	int					result = 0;
	AcdbGetAudProcStrmTopIdCmdType		acdb_get_top;
	AcdbGetTopologyIdRspType		audio_top;

	acdb_get_top.nApplicationType = audstrm_top->cal_type.cal_info.app_type;

	LOGD("ACDB -> ACDB_CMD_GET_AUDPROC_STREAM_TOPOLOGY_ID\n");

	result = acdb_ioctl(ACDB_CMD_GET_AUDPROC_STREAM_TOPOLOGY_ID,
		(const uint8_t *)&acdb_get_top, sizeof(acdb_get_top),
		(uint8_t *)&audio_top, sizeof(audio_top));
	if (result) {
		LOGE("Error: ACDB get asm topology returned = %d\n",
		     result);
		audstrm_top->cal_type.cal_info.topology = 0;
		goto done;
	}
	audstrm_top->cal_type.cal_info.topology = audio_top.nTopologyId;
done:
	return 0;
}

static int get_common_custom_topology(struct cal_block *block,
				struct audio_cal_basic *common_top)
{
	int				result = 0;
	AcdbQueryCmdType		acdb_top;
	AcdbQueryResponseType		response;

	acdb_top.nBufferLength = block->size;
	acdb_top.pBufferPointer = (uint8_t *)block->vaddr;

	LOGD("ACDB -> ACDB_CMD_GET_AVCS_CUSTOM_TOPO_INFO\n");

	result = acdb_ioctl(ACDB_CMD_GET_AVCS_CUSTOM_TOPO_INFO,
		(const uint8_t *)&acdb_top, sizeof(acdb_top),
		(uint8_t *)&response, sizeof(response));
	if (result) {
		LOGE("Error: ACDB get common custom topologies returned = %d\n",
		     result);
		common_top->cal_type.cal_data.cal_size = 0;
		goto done;
	}
	common_top->cal_type.cal_data.cal_size = response.nBytesUsedInBuffer;
done:
	LOGD("ACDB -> ACDB_CMD_GET_AVCS_CUSTOM_TOPO_INFO: size:0x%x ret=%d \n",
		common_top->cal_type.cal_data.cal_size, result);
	return result;
}

static int get_adm_custom_topology(struct cal_block *block,
				struct audio_cal_basic *adm_top)
{
	int				result = 0;
	AcdbQueryCmdType		acdb_top;
	AcdbQueryResponseType		response;

	acdb_top.nBufferLength = block->size;
	acdb_top.pBufferPointer = (uint8_t *)block->vaddr;

	LOGD("ACDB -> ACDB_CMD_GET_AUDIO_COPP_TOPOLOGIES\n");

	result = acdb_ioctl(ACDB_CMD_GET_AUDIO_COPP_TOPOLOGIES,
		(const uint8_t *)&acdb_top, sizeof(acdb_top),
		(uint8_t *)&response, sizeof(response));
	if (result) {
		LOGE("Error: ACDB get adm topologies returned = %d\n",
		     result);
		adm_top->cal_type.cal_data.cal_size = 0;
		goto done;
	}
	adm_top->cal_type.cal_data.cal_size = response.nBytesUsedInBuffer;
done:
	return result;
}

static int get_asm_custom_topology(struct cal_block *block,
				struct audio_cal_basic *asm_top)
{
	int				result = 0;
	AcdbQueryCmdType		acdb_top;
	AcdbQueryResponseType		response;

	acdb_top.nBufferLength = block->size;
	acdb_top.pBufferPointer = (uint8_t *)block->vaddr;

	LOGD("ACDB -> ACDB_CMD_GET_AUDIO_POPP_TOPOLOGIES\n");

	result = acdb_ioctl(ACDB_CMD_GET_AUDIO_POPP_TOPOLOGIES,
		(const uint8_t *)&acdb_top, sizeof(acdb_top),
		(uint8_t *)&response, sizeof(response));
	if (result) {
		LOGE("Error: ACDB get asm topologies returned = %d\n",
		     result);
		asm_top->cal_type.cal_data.cal_size = 0;
		goto done;
	}
	asm_top->cal_type.cal_data.cal_size = response.nBytesUsedInBuffer;
done:
	return result;
}


static int get_audtable(struct cal_block *block,
			struct audio_cal_audproc *audproc_cal)
{
	int				result = 0;
	AcdbAudProcTableCmdType		audtable;
	AcdbQueryResponseType		response;

	audtable.nDeviceId = audproc_cal->cal_type.cal_info.acdb_id;
	audtable.nDeviceSampleRateId = audproc_cal->cal_type.cal_info.sample_rate;
	audtable.nApplicationType = audproc_cal->cal_type.cal_info.app_type;
	audtable.nBufferLength = block->size;
	audtable.nBufferPointer = (uint8_t *)block->vaddr;

	LOGD("ACDB -> ACDB_CMD_GET_AUDPROC_COMMON_TABLE\n");

	result = acdb_ioctl(ACDB_CMD_GET_AUDPROC_COMMON_TABLE,
		(const uint8_t *)&audtable, sizeof(audtable),
		(uint8_t *)&response, sizeof(response));
	if (result) {
		LOGE("Error: ACDB audproc returned = %d\n", result);
		audproc_cal->cal_type.cal_data.cal_size = 0;
		goto done;
	}
	audproc_cal->cal_type.cal_data.cal_size = response.nBytesUsedInBuffer;
done:
	return result;
}

static int get_audvoltable(struct cal_block *block,
			struct audio_cal_audvol *audvol_cal)
{
	int					result = 0;
	AcdbAudProcGainDepVolTblStepCmdType	audvoltable;
	AcdbQueryResponseType			response;

	audvoltable.nDeviceId = audvol_cal->cal_type.cal_info.acdb_id;
	audvoltable.nApplicationType = audvol_cal->cal_type.cal_info.app_type;
	audvoltable.nVolumeIndex = audvol_cal->cal_type.cal_info.vol_index;
	audvoltable.nBufferLength = block->size;
	audvoltable.nBufferPointer = (uint8_t *)block->vaddr;

	LOGD("ACDB -> ACDB_CMD_GET_AUDPROC_GAIN_DEP_STEP_TABLE\n");

	result = acdb_ioctl(ACDB_CMD_GET_AUDPROC_GAIN_DEP_STEP_TABLE,
		(const uint8_t *)&audvoltable, sizeof(audvoltable),
		(uint8_t *)&response, sizeof(response));
	if (result) {
		LOGE("Error: ACDB AudProc vol returned = %d\n", result);
		audvol_cal->cal_type.cal_data.cal_size = 0;
		goto done;
	}
	audvol_cal->cal_type.cal_data.cal_size = response.nBytesUsedInBuffer;
done:
	return result;
}

static int get_hw_delay(struct cal_block *block,
			struct audio_cal_hw_delay *hw_delay_cal)
{
	int32_t			result = 0;
	AcdbQueryResponseType	response;
	AcdbDevPropCmdType	avsyncdelay;
	struct hw_delay		*delay;

	avsyncdelay.nDeviceId = hw_delay_cal->cal_type.cal_info.acdb_id;
	avsyncdelay.nPropID = hw_delay_cal->cal_type.cal_info.property_type;
	avsyncdelay.nBufferLength = block->size;
	avsyncdelay.pBufferPointer = (uint8_t *)block->vaddr;

	LOGD("ACDB -> ACDB_AVSYNC_INFO: ACDB_CMD_GET_DEVICE_PROPERTY\n");

	result = acdb_ioctl(ACDB_CMD_GET_DEVICE_PROPERTY,
		(const uint8_t *)&avsyncdelay, sizeof(avsyncdelay),
		(uint8_t *)&response, sizeof(response));
	if (result) {
		LOGE("Error: ACDB ACDB_CMD_GET_DEVICE_PROPERTY error = %d\n",
		     result);
		hw_delay_cal->cal_type.cal_data.cal_size = 0;
		goto done;
	}
	hw_delay_cal->cal_type.cal_data.cal_size = response.nBytesUsedInBuffer;
done:
	return result;
}

static int get_afetable(struct cal_block *block,
			struct audio_cal_afe *afe_cal)
{
	int					result = 0;
	AcdbAfeCommonTableCmdType		afetable;
	AcdbQueryResponseType			response;

	afetable.nDeviceId = afe_cal->cal_type.cal_info.acdb_id;
	/* Does not accept ACDB sample rate bit mask */
	afetable.nSampleRateId = afe_cal->cal_type.cal_info.sample_rate;
	afetable.nBufferLength = block->size;
	afetable.nBufferPointer = (uint8_t *)block->vaddr;

	LOGD("ACDB -> ACDB_CMD_GET_AFE_COMMON_TABLE\n");

	result = acdb_ioctl(ACDB_CMD_GET_AFE_COMMON_TABLE,
		(const uint8_t *)&afetable, sizeof(afetable),
		(uint8_t *)&response, sizeof(response));
	if (result) {
		LOGE("Error: ACDB AFE returned = %d\n", result);
		afe_cal->cal_type.cal_data.cal_size = 0;
		goto done;
	}
	afe_cal->cal_type.cal_data.cal_size = response.nBytesUsedInBuffer;
done:
	return result;
}

static int get_lsm_custom_topology(struct cal_block *block,
				struct audio_cal_basic *lsm_top)
{
	int				result = 0;
	AcdbQueryCmdType		acdb_top;
	AcdbQueryResponseType		response;

	acdb_top.nBufferLength = block->size;
	acdb_top.pBufferPointer = (uint8_t *)block->vaddr;

	LOGD("ACDB -> ACDB_CMD_GET_LSM_TOPOLOGIES\n");

	result = acdb_ioctl(ACDB_CMD_GET_LSM_TOPOLOGIES,
		(const uint8_t *)&acdb_top, sizeof(acdb_top),
		(uint8_t *)&response, sizeof(response));
	if (result) {
		LOGE("Error: ACDB get lsm custom topologies returned = %d\n",
		     result);
		lsm_top->cal_type.cal_data.cal_size = 0;
		goto done;
	}
	lsm_top->cal_type.cal_data.cal_size = response.nBytesUsedInBuffer;
done:
	return result;
}

static int get_lsm_topology(struct audio_cal_lsm_top *lsm_top)
{
	int					result = 0;
	AcdbGetLsmTopIdCmdType			acdb_get_top;
	AcdbGetTopologyIdRspType		audio_top;

	acdb_get_top.nDeviceId = lsm_top->cal_type.cal_info.acdb_id;
	acdb_get_top.nLsmApplicationType = lsm_top->cal_type.cal_info.app_type;

	LOGD("ACDB -> ACDB_CMD_GET_LSM_TOPOLOGY_ID \n");

	result = acdb_ioctl(ACDB_CMD_GET_LSM_TOPOLOGY_ID,
		(const uint8_t *)&acdb_get_top, sizeof(acdb_get_top),
		(uint8_t *)&audio_top, sizeof(audio_top));
	if (result) {
		LOGE("Error: ACDB get lsm topology returned = %d\n",
		     result);
		lsm_top->cal_type.cal_info.topology = 0;
		result = -ENODATA;
		goto done;
	}
	lsm_top->cal_type.cal_info.topology = audio_top.nTopologyId;
done:
	return result;
}

static int get_lsm_table(struct cal_block *block,
				struct audio_cal_lsm *lsm_cal)
{
	int					result = 0;
	AcdbLsmTableCmdType			lsm_table;
	AcdbQueryResponseType			response;

	lsm_table.nDeviceId = lsm_cal->cal_type.cal_info.acdb_id;
	lsm_table.nMadApplicationType = lsm_cal->cal_type.cal_info.app_type;
	lsm_table.nBufferLength = block->size;
	lsm_table.nBufferPointer = (uint8_t *)block->vaddr;

	LOGD("ACDB -> ACDB_CMD_GET_LSM_TABLE\n");

	result = acdb_ioctl(ACDB_CMD_GET_LSM_TABLE,
		(const uint8_t *)&lsm_table, sizeof(lsm_table),
		(uint8_t *)&response, sizeof(response));
	if (result) {
		LOGE("Error: ACDB LSM returned = %d\n", result);
		lsm_cal->cal_type.cal_data.cal_size = 0;
		goto done;
	}
	lsm_cal->cal_type.cal_data.cal_size = response.nBytesUsedInBuffer;
done:
	return result;
}

int acdb_loader_get_ecrx_device(int acdb_id)
{
	int32_t result = INVALID_DATA;
	uint32_t *pRxDevs;
	AcdbAudioRecRxListCmdType acdb_cmd;
	AcdbAudioRecRxListRspType acdb_cmd_response;

	acdb_cmd.nTxDeviceId = acdb_id;
	pRxDevs = malloc(EC_REF_RX_DEVS);
	if (pRxDevs == NULL) {
		LOGE("Error: %s Malloc Failed", __func__);
		return result;
	}
	acdb_cmd_response.pRxDevs = pRxDevs;
	result = acdb_ioctl(ACDB_CMD_GET_AUDIO_RECORD_RX_DEVICE_LIST,
				(const uint8_t *)&acdb_cmd, sizeof(acdb_cmd),
			(uint8_t *)&acdb_cmd_response, sizeof(acdb_cmd_response));
	if (result) {
		LOGE("Error: ACDB EC_REF_RX returned = %d\n", result);
		goto done;
	}

	if (acdb_cmd_response.nNoOfRxDevs) {
		result = acdb_cmd_response.pRxDevs[0];
	}

	done:
	free(pRxDevs);
	return result;
}

static int get_aanctable(struct cal_block *block,
				struct audio_cal_aanc *aanc_cal)
{
	int					result = 0;
	AcdbAANCConfigTableCmdType		aanctable;
	AcdbQueryResponseType			response;

	aanctable.nTxDeviceId = aanc_cal->cal_type.cal_info.acdb_id;
	aanctable.nBufferLength = block->size;
	aanctable.nBufferPointer = (uint8_t *)block->vaddr;

	LOGD("ACDB -> ACDB_CMD_GET_ADAPTIVE_ANC_CONFIG_TABLE\n");

	result = acdb_ioctl(ACDB_CMD_GET_ADAPTIVE_ANC_CONFIG_TABLE,
		(const uint8_t *)&aanctable, sizeof(aanctable),
		(uint8_t *)&response, sizeof(response));
	LOGD("ACDB_CMD_GET_ADAPTIVE_ANC_CONFIG_TABLE result %d\n",
		result);

	if (result) {
		LOGE("Error: ACDB AANC returned = %d\n", result);
		aanc_cal->cal_type.cal_data.cal_size = 0;
		goto done;
	}
	aanc_cal->cal_type.cal_data.cal_size = response.nBytesUsedInBuffer;
done:
	return result;
}

int send_meta_info(int metaInfoKey)
{
	AcdbGetMetaInfoCmdType metaInfoCmd;
	AcdbQueryResponseType response;
	int32_t		result = 0;
	uint8_t		*buf;
	struct audio_core_metainfo metaInfo;
	struct cal_block		*block;

	LOGD("ACDB -> send_meta_info Enter\n");

	block = get_cal_block(AUDIO_CORE_METAINFO_CAL_TYPE, BUFF_IDX_0);
	if (block == NULL) {
		LOGE("%s: Error: Could not get cal block!\n", __func__);
		goto done;
	}

	metaInfoCmd.nBufferPointer = block->vaddr;
	metaInfoCmd.nKey = metaInfoKey ;
	metaInfoCmd.nBufferLength = block->size;

	if (metaInfoCmd.nBufferPointer == NULL) {
		LOGE("Fail to allocate memory for metainfo\n");
		result = -ENODEV;
		goto done;
	}

	result = acdb_ioctl( ACDB_CMD_GET_META_INFO,
		(const uint8_t *) &metaInfoCmd, sizeof(metaInfoCmd),
		(uint8_t *) &response, sizeof(response));

	if (result) {
		LOGE("GetInfo returned error (%d), err:\n", result);
		goto done;
	}

	metaInfo.hdr.data_size = sizeof(metaInfo);
	metaInfo.hdr.version = VERSION_0_0;
	metaInfo.hdr.cal_type = AUDIO_CORE_METAINFO_CAL_TYPE;
	metaInfo.hdr.cal_type_size = sizeof(metaInfo.cal_type);
	metaInfo.cal_type.cal_hdr.version = VERSION_0_0;
	metaInfo.cal_type.cal_hdr.buffer_number = BUFF_IDX_0;
	metaInfo.cal_type.cal_data.cal_size = block->size;
	metaInfo.cal_type.cal_data.mem_handle = block->map_handle;
	metaInfo.cal_type.cal_info.nKey = metaInfoKey;

	result = ioctl(cal_driver_handle, AUDIO_SET_CALIBRATION,
			&metaInfo);
	if (result) {
		LOGE("Error: Sending ACDB Meta Info result = %d\n", result);
		goto done;
	}

	LOGD("ACDB -> send_meta_info Exit\n");
done:
	return result;
}

static int send_adm_topology(int acdb_id, int path, int app_id)
{
	int				result = 0;
	int				buff_num;
	struct cal_block		*block;
	struct audio_cal_adm_top	adm_top;

	buff_num = path + MAX_PATH_TYPE * BUFF_IDX_0;

	block = get_cal_block(ADM_TOPOLOGY_CAL_TYPE, buff_num);
	if (block == NULL) {
		LOGE("%s: Error: Could not get cal block!\n", __func__);
		goto done;
	}
	LOGD("ACDB -> send_adm_topology\n");
	adm_top.hdr.data_size = sizeof(adm_top);
	adm_top.hdr.version = VERSION_0_0;
	adm_top.hdr.cal_type = ADM_TOPOLOGY_CAL_TYPE;
	adm_top.hdr.cal_type_size = sizeof(adm_top.cal_type);
	adm_top.cal_type.cal_hdr.buffer_number = buff_num;
	adm_top.cal_type.cal_hdr.version = block->version;
	adm_top.cal_type.cal_info.acdb_id = acdb_id;
	adm_top.cal_type.cal_info.path = path;
	adm_top.cal_type.cal_info.app_type = app_id;
	adm_top.cal_type.cal_data.mem_handle = block->map_handle;

	result = get_adm_topology(&adm_top);
	if (result < 0)
		goto done;

	result = ioctl(cal_driver_handle, AUDIO_SET_CALIBRATION,
		&adm_top);
	if (result) {
		LOGE("Error: Sending ACDB adm topology result = %d\n", result);
		goto done;
	}
done:
	return result;
}

static int send_asm_topology(int app_id)
{
	int				result = 0;
	struct cal_block		*block;
	struct audio_cal_asm_top	asm_top;

	block = get_cal_block(ASM_TOPOLOGY_CAL_TYPE, BUFF_IDX_0);
	if (block == NULL) {
		LOGE("%s: Error: Could not get cal block!\n", __func__);
		goto done;
	}
	LOGD("ACDB -> send_asm_topology\n");
	asm_top.hdr.data_size = sizeof(asm_top);
	asm_top.hdr.version = VERSION_0_0;
	asm_top.hdr.cal_type = ASM_TOPOLOGY_CAL_TYPE;
	asm_top.hdr.cal_type_size = sizeof(asm_top.cal_type);
	asm_top.cal_type.cal_hdr.buffer_number = BUFF_IDX_0;
	asm_top.cal_type.cal_hdr.version = block->version;
	asm_top.cal_type.cal_info.app_type = app_id;
	asm_top.cal_type.cal_data.mem_handle = block->map_handle;

	result = get_asm_topology(&asm_top);
	if (result < 0)
		goto done;

	result = ioctl(cal_driver_handle, AUDIO_SET_CALIBRATION,
		&asm_top);
	if (result) {
		LOGE("Error: Sending ACDB asm topology result = %d\n", result);
		goto done;
	}
done:
	return result;
}

static int send_common_custom_topology(void)
{
	int				result = 0;
	int				result2 = 0;
	struct cal_block		*block;
	struct audio_cal_basic		common_top;
	LOGD("ACDB -> %s\n", __func__);

	block = get_cal_block(CORE_CUSTOM_TOPOLOGIES_CAL_TYPE, BUFF_IDX_0);
	if (block == NULL) {
		LOGE("%s: no cal block CORE_CUSTOM_TOPOLOGIES!\n", __func__);
		result = -ENODEV;
		goto done;
	}
	common_top.hdr.data_size = sizeof(common_top);
	common_top.hdr.version = VERSION_0_0;
	common_top.hdr.cal_type = CORE_CUSTOM_TOPOLOGIES_CAL_TYPE;
	common_top.hdr.cal_type_size = sizeof(common_top.cal_type);
	common_top.cal_type.cal_hdr.buffer_number = BUFF_IDX_0;
	common_top.cal_type.cal_hdr.version = block->version;
	common_top.cal_type.cal_data.mem_handle = block->map_handle;

	result2 = get_common_custom_topology(block, &common_top);
	if (result2 < 0)
		result = result2;

	LOGD("ACDB -> CORE_CUSTOM_TOPOLOGIES\n");

	result2 = ioctl(cal_driver_handle, AUDIO_SET_CALIBRATION,
		&common_top);
	if (result2) {
		LOGE("AUDIO_SET_CALIBRATION, Error = %d\n", result);
		result = -EINVAL;
		goto done;
	}
done:
	return result;
}

static int send_adm_custom_topology(void)
{
	int				result = 0;
	struct cal_block		*block;
	struct audio_cal_basic		adm_top;
	LOGD("ACDB -> send_adm_custom_topology\n");

	block = get_cal_block(ADM_CUST_TOPOLOGY_CAL_TYPE, BUFF_IDX_0);
	if (block == NULL) {
		LOGE("%s: Error: Could not get cal block!\n", __func__);
		goto done;
	}
	adm_top.hdr.data_size = sizeof(adm_top);
	adm_top.hdr.version = VERSION_0_0;
	adm_top.hdr.cal_type = ADM_CUST_TOPOLOGY_CAL_TYPE;
	adm_top.hdr.cal_type_size = sizeof(adm_top.cal_type);
	adm_top.cal_type.cal_hdr.buffer_number = BUFF_IDX_0;
	adm_top.cal_type.cal_hdr.version = block->version;
	adm_top.cal_type.cal_data.mem_handle = block->map_handle;

	get_adm_custom_topology(block, &adm_top);

	LOGD("ACDB -> AUDIO_SET_ADM_CUSTOM_TOPOLOGY\n");

	result = ioctl(cal_driver_handle, AUDIO_SET_CALIBRATION,
		&adm_top);
	if (result) {
		LOGE("Error: Sending ACDB ADM topology result = %d\n", result);
		goto done;
	}
done:
	return result;
}

static int send_asm_custom_topology(void)
{
	int				result = 0;
	struct cal_block		*block;
	struct audio_cal_basic		asm_top;
	LOGD("ACDB -> send_asm_custom_topology\n");

	block = get_cal_block(ASM_CUST_TOPOLOGY_CAL_TYPE, BUFF_IDX_0);
	if (block == NULL) {
		LOGE("%s: Error: Could not get cal block!\n", __func__);
		goto done;
	}
	asm_top.hdr.data_size = sizeof(asm_top);
	asm_top.hdr.version = VERSION_0_0;
	asm_top.hdr.cal_type = ASM_CUST_TOPOLOGY_CAL_TYPE;
	asm_top.hdr.cal_type_size = sizeof(asm_top.cal_type);
	asm_top.cal_type.cal_hdr.buffer_number = BUFF_IDX_0;
	asm_top.cal_type.cal_hdr.version = block->version;
	asm_top.cal_type.cal_data.mem_handle = block->map_handle;

	get_asm_custom_topology(block, &asm_top);

	LOGD("ACDB -> AUDIO_SET_ASM_CUSTOM_TOPOLOGY\n");

	result = ioctl(cal_driver_handle, AUDIO_SET_CALIBRATION,
		&asm_top);
	if (result) {
		LOGE("Error: Sending ACDB ASM topology result = %d\n", result);
		goto done;
	}
done:
	return result;
}

static int send_audtable(int acdb_id, int path, int app_id, int sample_rate)
{
	int				result = 0;
	int				audtable_size = 0;
	int				buff_num;
	struct cal_block		*block;
	struct audio_cal_audproc	audproc_cal;
	LOGD("ACDB -> send_audtable\n");

	buff_num = path + MAX_PATH_TYPE * BUFF_IDX_0;

	block = get_cal_block(ADM_AUDPROC_CAL_TYPE, buff_num);
	if (block == NULL) {
		LOGE("%s: Error: Could not get cal block!\n", __func__);
		goto done;
	}

	/* Get audtable size from s/w and reallocate cal type in case of insufficient memory */
	audtable_size = get_audtable_size(acdb_id, app_id, sample_rate);
	if (audtable_size > block->size)
	{
		LOGD("Reallocate memory for AudProc Table with size: %d\n", (audtable_size + MIN_BUFF_SIZE));
		deallocate_cal_type(ADM_AUDPROC_CAL_TYPE, buff_num);
		allocate_cal_type(ADM_AUDPROC_CAL_TYPE, (audtable_size + MIN_BUFF_SIZE), buff_num);
	}

	audproc_cal.hdr.data_size = sizeof(audproc_cal);
	audproc_cal.hdr.version = VERSION_0_0;
	audproc_cal.hdr.cal_type = ADM_AUDPROC_CAL_TYPE;
	audproc_cal.hdr.cal_type_size = sizeof(audproc_cal.cal_type);
	audproc_cal.cal_type.cal_hdr.buffer_number = buff_num;
	audproc_cal.cal_type.cal_hdr.version = block->version;
	audproc_cal.cal_type.cal_info.acdb_id = acdb_id;
	audproc_cal.cal_type.cal_info.path = path;
	audproc_cal.cal_type.cal_info.app_type = app_id;
	audproc_cal.cal_type.cal_info.sample_rate = sample_rate;
	audproc_cal.cal_type.cal_data.mem_handle = block->map_handle;

	get_audtable(block, &audproc_cal);

	LOGD("ACDB -> AUDIO_SET_AUDPROC_CAL\n");

	result = ioctl(cal_driver_handle, AUDIO_SET_CALIBRATION,
		&audproc_cal);
	if (result) {
		LOGE("Error: Sending ACDB audproc result = %d\n", result);
		goto done;
	}
done:
	return result;
}

static int send_audvoltable(int acdb_id, int path, int app_id)
{
	int				result = 0;
	int				buff_num;
	struct cal_block		*block;
	struct audio_cal_audvol		audvol_cal;
	LOGD("ACDB -> send_audvoltable\n");

	buff_num = path + MAX_PATH_TYPE * BUFF_IDX_0;

	block = get_cal_block(ADM_AUDVOL_CAL_TYPE, buff_num);
	if (block == NULL) {
		LOGE("%s: Error: Could not get cal block!\n", __func__);
		goto done;
	}
	audvol_cal.hdr.data_size = sizeof(audvol_cal);
	audvol_cal.hdr.version = VERSION_0_0;
	audvol_cal.hdr.cal_type = ADM_AUDVOL_CAL_TYPE;
	audvol_cal.hdr.cal_type_size = sizeof(audvol_cal.cal_type);
	audvol_cal.cal_type.cal_hdr.buffer_number = buff_num;
	audvol_cal.cal_type.cal_hdr.version = block->version;
	audvol_cal.cal_type.cal_info.acdb_id = acdb_id;
	audvol_cal.cal_type.cal_info.path = path;
	audvol_cal.cal_type.cal_info.app_type = app_id;
	/* 0 is max volume which is default Q6 COPP volume */
	audvol_cal.cal_type.cal_info.vol_index = 0;
	audvol_cal.cal_type.cal_data.mem_handle = block->map_handle;
	audvol_cal.cal_type.cal_data.cal_size = 0;

	get_audvoltable(block, &audvol_cal);

	LOGD("ACDB -> AUDIO_SET_AUDPROC_VOL_CAL\n");
	result = ioctl(cal_driver_handle, AUDIO_SET_CALIBRATION,
		&audvol_cal);
	if (result) {
		LOGE("Error: Sending ACDB audproc vol result = %d\n", result);
		goto done;
	}
done:
	return result;
}

static int send_aanctable(int acdb_id)
{
	int				result = 0;
	struct cal_block		*block;
	struct audio_cal_aanc		aanc_cal;
	LOGD("ACDB -> send_aanctable\n");

	block = get_cal_block(AFE_AANC_CAL_TYPE, BUFF_IDX_0);
	if (block == NULL) {
		LOGE("%s: Error: Could not get cal block!\n", __func__);
		goto done;
	}
	aanc_cal.hdr.data_size = sizeof(aanc_cal);
	aanc_cal.hdr.version = VERSION_0_0;
	aanc_cal.hdr.cal_type = AFE_AANC_CAL_TYPE;
	aanc_cal.hdr.cal_type_size = sizeof(aanc_cal.cal_type);
	aanc_cal.cal_type.cal_hdr.buffer_number = BUFF_IDX_0;
	aanc_cal.cal_type.cal_hdr.version = block->version;
	aanc_cal.cal_type.cal_info.acdb_id = acdb_id;
	aanc_cal.cal_type.cal_data.mem_handle = block->map_handle;

	get_aanctable(block, &aanc_cal);

	LOGD("ACDB -> AUDIO_SET_AANC_TABLE\n");
	result = ioctl(cal_driver_handle, AUDIO_SET_CALIBRATION,
		&aanc_cal);
	if (result) {
		LOGE("Error: Sending ACDB AANC result = %d\n", result);
		goto done;
	}
done:
	return result;
}

static int send_afe_cal(int acdb_id, int path, int sample_rate, int cal_type)
{
	int				result = 0;
	struct cal_block		*block;
	struct audio_cal_afe		afe_cal;
	LOGD("ACDB -> send_afe_cal\n");

	block = get_cal_block(cal_type, BUFF_IDX_0);
	if (block == NULL) {
		LOGE("%s: Error: Could not get cal block!\n", __func__);
		goto done;
	}
	afe_cal.hdr.data_size = sizeof(afe_cal);
	afe_cal.hdr.version = VERSION_0_0;
	afe_cal.hdr.cal_type = cal_type;
	afe_cal.hdr.cal_type_size = sizeof(afe_cal.cal_type);
	afe_cal.cal_type.cal_hdr.buffer_number = BUFF_IDX_0;
	afe_cal.cal_type.cal_hdr.version = block->version;
	afe_cal.cal_type.cal_info.acdb_id = acdb_id;
	afe_cal.cal_type.cal_info.path = path;
	afe_cal.cal_type.cal_info.sample_rate = sample_rate;
	afe_cal.cal_type.cal_data.mem_handle = block->map_handle;
	afe_cal.cal_type.cal_data.cal_size = 0;

	get_afetable(block, &afe_cal);

	LOGD("ACDB -> AUDIO_SET_AFE_CAL\n");
	result = ioctl(cal_driver_handle, AUDIO_SET_CALIBRATION,
		&afe_cal);
	if (result) {
		LOGE("Error: Sending ACDB AFE result = %d\n", result);
		goto done;
	}
done:
	return result;
}

static int send_hw_delay(int acdb_id, int path)
{
	int32_t				result = 0;
	struct cal_block		*block;
	struct audio_cal_hw_delay	hw_delay_cal;
	int				buff_num;

	LOGD("ACDB -> send_hw_delay : acdb_id = %d path = %d\n",
	     acdb_id, path);

	buff_num = path + MAX_PATH_TYPE * BUFF_IDX_0;

	block = get_cal_block(AFE_HW_DELAY_CAL_TYPE, buff_num);
	if (block == NULL) {
		LOGE("%s: Error: Could not get cal block!\n", __func__);
		goto done;
	}

	hw_delay_cal.hdr.data_size = sizeof(hw_delay_cal);
	hw_delay_cal.hdr.version = VERSION_0_0;
	hw_delay_cal.hdr.cal_type = AFE_HW_DELAY_CAL_TYPE;
	hw_delay_cal.hdr.cal_type_size = sizeof(hw_delay_cal.cal_type);
	hw_delay_cal.cal_type.cal_hdr.buffer_number = buff_num;
	hw_delay_cal.cal_type.cal_hdr.version = block->version;
	hw_delay_cal.cal_type.cal_info.acdb_id = acdb_id;
	hw_delay_cal.cal_type.cal_info.path = path;
	hw_delay_cal.cal_type.cal_info.property_type = ACDB_AVSYNC_INFO;
	hw_delay_cal.cal_type.cal_data.mem_handle = block->map_handle;
	block->size = sizeof(hw_delay_cal.cal_type.cal_info.data);
	block->vaddr = (void *)&hw_delay_cal.cal_type.cal_info.data;

	get_hw_delay(block, &hw_delay_cal);

	if (hw_delay_cal.cal_type.cal_data.cal_size != 0) {
		hw_delay_cal.hdr.data_size = sizeof(hw_delay_cal) -
			sizeof(hw_delay_cal.cal_type.cal_info.data) +
			hw_delay_cal.cal_type.cal_data.cal_size;
		hw_delay_cal.hdr.cal_type_size = sizeof(hw_delay_cal.cal_type) -
			sizeof(hw_delay_cal.cal_type.cal_info.data) +
			hw_delay_cal.cal_type.cal_data.cal_size;
	}

	result = ioctl(cal_driver_handle, AUDIO_SET_CALIBRATION,
				   &hw_delay_cal);
	if (result)
		LOGE("Error: Sending ACDB AFE result = %d errno=%d\n",
		      result, errno);
done:
	return result;
}

static int send_lsm_custom_topology(void)
{
	int				result = 0;
	struct cal_block		*block;
	struct audio_cal_basic		lsm_top;
	LOGD("ACDB -> send_lsm_custom_topology\n");

	block = get_cal_block(LSM_CUST_TOPOLOGY_CAL_TYPE, BUFF_IDX_0);
	if (block == NULL) {
		LOGE("%s: Error: Could not get cal block!\n", __func__);
		goto done;
	}
	lsm_top.hdr.data_size = sizeof(lsm_top);
	lsm_top.hdr.version = VERSION_0_0;
	lsm_top.hdr.cal_type = LSM_CUST_TOPOLOGY_CAL_TYPE;
	lsm_top.hdr.cal_type_size = sizeof(lsm_top.cal_type);
	lsm_top.cal_type.cal_hdr.buffer_number = BUFF_IDX_0;
	lsm_top.cal_type.cal_hdr.version = block->version;
	lsm_top.cal_type.cal_data.mem_handle = block->map_handle;

	get_lsm_custom_topology(block, &lsm_top);

	LOGD("ACDB -> AUDIO_SET_LSM_CUSTOM_TOPOLOGY\n");

	result = ioctl(cal_driver_handle, AUDIO_SET_CALIBRATION,
		&lsm_top);
	if (result) {
		LOGE("Error: Sending ACDB LSM topology result = %d\n", result);
		goto done;
	}
done:
	return result;
}

static int send_lsm_topology(int acdb_id, int app_id, int mode)
{
	int				result = 0;
	int				result2 = 0;
	struct cal_block		*block;
	struct audio_cal_lsm_top	lsm_top;

	block = get_cal_block(LSM_TOPOLOGY_CAL_TYPE, BUFF_IDX_0);
	if (block == NULL) {
		LOGE("%s: Error: Could not get cal block!\n", __func__);
		goto done;
	}
	LOGD("ACDB -> send_lsm_topology\n");
	lsm_top.hdr.data_size = sizeof(lsm_top);
	lsm_top.hdr.version = VERSION_0_0;
	lsm_top.hdr.cal_type = LSM_TOPOLOGY_CAL_TYPE;
	lsm_top.hdr.cal_type_size = sizeof(lsm_top.cal_type);
	lsm_top.cal_type.cal_hdr.buffer_number = BUFF_IDX_0;
	lsm_top.cal_type.cal_hdr.version = block->version;
	lsm_top.cal_type.cal_info.acdb_id = acdb_id;
	lsm_top.cal_type.cal_info.app_type = app_id;
	lsm_top.cal_type.cal_data.mem_handle = block->map_handle;

	if (mode == LISTEN_MODE_NO_TOPOLOGY) {
                /* topology value 0 is not a valid topology. Driver can
                fallback to non-topology interface based on this value. */
		lsm_top.cal_type.cal_info.topology = 0;
	} else {
		result2 = get_lsm_topology(&lsm_top);
		if (result2 < 0) {
			/* return only failure code of get LSM topology to STHAL */
			/* so it knows to fallback to non-topology interface */
			result = result2;
			goto done;
		}
	}

	result2 = ioctl(cal_driver_handle, AUDIO_SET_CALIBRATION,
		&lsm_top);
	if (result2) {
		LOGE("Error: Sending ACDB LSM topology result = %d\n", result2);
		goto done;
	}
done:
	return result;
}

static int send_lsm_cal(int acdb_id, int app_id, int cal_type)
{
	int				result = 0;
	struct cal_block		*block;
	struct audio_cal_lsm		lsm_cal;
	LOGD("ACDB -> get_lsm_table\n");

	block = get_cal_block(cal_type, BUFF_IDX_0);
	if (block == NULL) {
		LOGE("%s: Error: Could not get cal block!\n", __func__);
		goto done;
	}
	lsm_cal.hdr.data_size = sizeof(lsm_cal);
	lsm_cal.hdr.version = VERSION_0_0;
	lsm_cal.hdr.cal_type = cal_type;
	lsm_cal.hdr.cal_type_size = sizeof(lsm_cal.cal_type);
	lsm_cal.cal_type.cal_hdr.buffer_number = BUFF_IDX_0;
	lsm_cal.cal_type.cal_hdr.version = block->version;
	lsm_cal.cal_type.cal_info.acdb_id = acdb_id;
	lsm_cal.cal_type.cal_info.app_type = app_id;
	lsm_cal.cal_type.cal_data.mem_handle = block->map_handle;

	get_lsm_table(block, &lsm_cal);

	LOGD("ACDB -> AUDIO_SET_LSM_CAL\n");
	result = ioctl(cal_driver_handle, AUDIO_SET_CALIBRATION,
		&lsm_cal);
	if (result) {
		LOGE("Error: Sending ACDB LSM result = %d\n", result);
		goto done;
	}
done:
	return result;
}

int send_codec_cal(struct param_data *params)
{
	int				result = 0;
	AcdbCodecCalDataCmdType		codec_table;
	AcdbQueryResponseType		response;

	LOGE("ACDB -> send_codec_cal\n");
	codec_table.nDeviceID = params->acdb_id;
	codec_table.nCodecFeatureType = ACDB_WCD9320_MAD;
	codec_table.nBufferLength = params->buff_size;
	codec_table.pBufferPointer = (uint8_t *)params->buff;

	LOGE("ACDB -> ACDB_CMD_GET_CODEC_CAL_DATA\n");

	result = acdb_ioctl(ACDB_CMD_GET_CODEC_CAL_DATA,
		(const uint8_t *)&codec_table, sizeof(codec_table),
		(uint8_t *)&response, sizeof(response));
	if (result) {
		LOGE("Error: ACDB CODEC CAL returned = %d\n", result);
	}
	params->data_size = (int)response.nBytesUsedInBuffer;
done:
	return result;
}

void acdb_loader_send_audio_cal_v2(int acdb_id, int capability, int app_id, int sample_rate)
{
	int		path;
        int             cal_type;

	if (!is_initialized) {
		LOGE("ACDB -> Not correctly initialized!\n");
		goto done;
	}

	path = get_audcal_path((uint32_t)capability);
	if (path == INVALID_DATA) {
		LOGE("ACDB -> Device is not RX or TX!"
			"acdb_id = %d\n", acdb_id);
		goto done;
	}

	LOGD("ACDB -> send_audio_cal, acdb_id = %d, path =  %d\n",
		acdb_id, path);

	send_asm_topology(app_id);
	send_adm_topology(acdb_id, path, app_id);
	send_audtable(acdb_id, path, app_id, sample_rate);
	send_audvoltable(acdb_id, path, app_id);
        cal_type = (path == TX_DEVICE) ?
                    AFE_COMMON_TX_CAL_TYPE : AFE_COMMON_RX_CAL_TYPE;
	send_afe_cal(acdb_id, path, sample_rate, cal_type);
	send_hw_delay(acdb_id, path);
done:
	return;
}

void acdb_loader_send_audio_cal(int acdb_id, int capability)
{
	acdb_loader_send_audio_cal_v2(acdb_id, capability, acdb_loader_get_default_app_type(), get_samplerate(acdb_id));
	return;
}

void acdb_loader_send_listen_device_cal(int acdb_id, int type, int app_id, int sample_rate)
{
	int cal_type;

        /* send adm cal only for ADSP type */
        if (type != LISTEN_HW_TYPE_CPE) {
            send_adm_topology(acdb_id, TX_DEVICE, app_id);
            send_audtable(acdb_id, TX_DEVICE, app_id, sample_rate);
        }

        cal_type = (type == LISTEN_HW_TYPE_CPE) ?
                        ULP_AFE_CAL_TYPE : AFE_COMMON_TX_CAL_TYPE;
	send_afe_cal(acdb_id, TX_DEVICE, get_samplerate(acdb_id), cal_type);
}

int acdb_loader_send_listen_lsm_cal(int acdb_id, int app_id, int mode, int type)
{
	int result = 0;
	int cal_type;
	
        /* Only return Get LSM topology error code */
	/* so STHAL knows when to fallback to non-topology interface */
	result = send_lsm_topology(acdb_id, app_id, mode);

        cal_type = (type == LISTEN_HW_TYPE_CPE) ?
                        ULP_LSM_CAL_TYPE : LSM_CAL_TYPE;
	send_lsm_cal(acdb_id, app_id, cal_type);

	return result;
}

static int get_anc_table(int acdb_id, struct cal_block *block)
{
	int result = -EINVAL;
	AcdbCodecANCSettingCmdType acdb_cmd;
	AcdbQueryResponseType response;

	uint32_t acdb_command_id = ACDB_CMD_GET_CODEC_ANC_SETTING;
	acdb_cmd.nRxDeviceId = acdb_id;
	acdb_cmd.nParamId = ACDB_PID_CODEC_ANC_DATA_WCD9320;
	acdb_cmd.nBufferLength = block->size;
	acdb_cmd.nBufferPointer = (uint8_t *)block->vaddr;

	LOGD("ACDB -> ACDB_CMD_GET_ANC_SETTING\n");

	result = acdb_ioctl(acdb_command_id,
		(const uint8_t *)&acdb_cmd, sizeof(acdb_cmd),
		(uint8_t *)&response, sizeof(response));
	if (result) {
		LOGE("Error: ACDB ANC returned = %d\n", result);
		goto done;
	}
done:
	return result;
}

#define ABS(x) (((x) < 0) ? (-1*(x)) : (x))
int32_t FP_mult(int32_t val1, int32_t val2)
{
	int32_t prod = 0;
	if ((val1 > 0 && val2 > 0) || (val1 < 0 && val2 < 0)) {
		if (ABS(val1) > (int32_t) (MAX_INT/ABS(val2)))
			prod = MAX_INT;
	}
	else if ((val1 > 0 && val2 < 0) || (val1 < 0 && val2 > 0)) {
		if (ABS(val1) > (int32_t) (MAX_INT/ABS(val2)))
			prod = -(int32_t) MAX_INT;
	}
	if (0 == prod)
		prod = val1 * val2;

	return prod;
}
int32_t FP_shift(int32_t val, int32_t shift)
{
	int32_t rnd = 1 << (ABS(shift)-1);
	int32_t val_s = val;
	/* underflow -> rounding errors */
	if (shift < 0) {
		val_s = ABS(val_s) + rnd;
		val_s = val_s >> ABS(shift);
		val_s = (val > 0) ? val_s : -val_s;
	}
	/* overflow -> saturation */
	else if (shift > 0) {
		if (ABS(val) > (int32_t) ((MAX_INT >> ABS(shift)))) {
			if (val < 0)
				val_s = -(int32_t) MAX_INT;
			else
				val_s = (int32_t) MAX_INT;
		} else
			val_s = val << ABS(shift);
	}
	return val_s;
}

uint16_t twosComp(int16_t val, int16_t bits)
{
	uint16_t res = 0;
	uint32_t width = bits + 1;
	if (val >= 0)
		res = (uint16_t) val;
	else
		res = -((-val) - (1 << width));

	return res;
}

int32_t FP_format(int32_t val, int32_t intb, int32_t fracb, int32_t max_val)
{
	val = FP_shift(val, -(ANC_COEFF_FRAC_BITS - fracb));
	/* Check for saturation */
	if (val > max_val)
		val = max_val;
	else if (val < -max_val)
		val = -max_val;
	/* convert to 2s compl */
	val = twosComp((uint16_t) val, (uint16_t) (intb + fracb));
	return val;
}

void send_mbhc_data(struct param_data *params)
{
	int result;
	AcdbGblTblCmdType global_cmd;
	AcdbQueryResponseType   response;

	LOGD("send mbhc data\n");
	params->data_size = 0;

	global_cmd.nModuleId = ACDB_MID_MBHC;
	global_cmd.nParamId = ACDB_PID_GENERAL_CONFIG;
	global_cmd.nBufferLength = params->buff_size;
	global_cmd.nBufferPointer = (uint8_t *)params->buff;

	LOGD("ACDB -> MBHC ACDB_PID_GENERAL_CONFIG\n");
	result = acdb_ioctl(ACDB_CMD_GET_GLBTBL_DATA,
		(const uint8_t *) &global_cmd, sizeof(global_cmd),
		(uint8_t *) &response, sizeof(response));

	if (result)
	{
		LOGE("Error reading MBHC general config returned = %x\n", result);
		goto acdb_error;
	}

	params->data_size += response.nBytesUsedInBuffer;
	global_cmd.nBufferLength = params->buff_size - params->data_size;
	global_cmd.nBufferPointer = (uint8_t *)params->buff + params->data_size;
	global_cmd.nParamId = ACDB_PID_PLUG_REMOVAL_DETECTION;

	LOGD("ACDB -> MBHC ACDB_PID_PLUG_REMOVAL_DETECTION\n");
	result = acdb_ioctl(ACDB_CMD_GET_GLBTBL_DATA,
		(const uint8_t *) &global_cmd, sizeof(global_cmd),
		(uint8_t *) &response, sizeof(response));

	if (result)
	{
		LOGE("Error reading MBHC removal config returned = %x\n", result);
		goto acdb_error;
	}

	params->data_size += response.nBytesUsedInBuffer;
	global_cmd.nBufferLength = params->buff_size - params->data_size;
	global_cmd.nBufferPointer = (uint8_t *)params->buff + params->data_size;
	global_cmd.nParamId = ACDB_PID_PLUG_TYPE_DETECTION;

	LOGD("ACDB -> MBHC ACDB_PID_PLUG_TYPE_DETECTION\n");
	result = acdb_ioctl(ACDB_CMD_GET_GLBTBL_DATA,
		(const uint8_t *) &global_cmd, sizeof(global_cmd),
		(uint8_t *) &response, sizeof(response));

	if (result)
	{
		LOGE("Error reading MBHC plug type config returned = %x\n", result);
		goto acdb_error;
	}

	params->data_size += response.nBytesUsedInBuffer;
	global_cmd.nBufferLength = params->buff_size - params->data_size;
	global_cmd.nBufferPointer = (uint8_t *)params->buff + params->data_size;
	global_cmd.nParamId = ACDB_PID_BUTTON_PRESS_DETECTION;

	LOGD("ACDB -> MBHC ACDB_PID_BUTTON_PRESS_DETECTION\n");
	result = acdb_ioctl(ACDB_CMD_GET_GLBTBL_DATA,
		(const uint8_t *) &global_cmd, sizeof(global_cmd),
		(uint8_t *) &response, sizeof(response));

	if (result)
	{
		LOGE("Error reading MBHC button press config returned = %x\n", result);
		goto acdb_error;
	}

	params->data_size += response.nBytesUsedInBuffer;
	global_cmd.nBufferLength = params->buff_size - params->data_size;
	global_cmd.nBufferPointer = (uint8_t *)params->buff + params->data_size;
	global_cmd.nParamId = ACDB_PID_IMPEDANCE_DETECTION;

	LOGD("ACDB -> MBHC ACDB_PID_IMPEDANCE_DETECTION\n");
	result = acdb_ioctl(ACDB_CMD_GET_GLBTBL_DATA,
		(const uint8_t *) &global_cmd, sizeof(global_cmd),
		(uint8_t *) &response, sizeof(response));

	if (result)
	{
		LOGE("Error reading MBHC impedance config returned = %x\n", result);
		goto acdb_error;
	}

	params->data_size += response.nBytesUsedInBuffer;

acdb_error:
	return;
}


void send_wcd9xxx_anc_data(struct param_data *params)
{
	uint32_t anc_configurations = NUM_OF_ANC_RX_CONFIG +
					NUM_OF_AANC_RX_CONFIG +
					NUM_OF_ANC_TUNING_CONFIG;
	uint32_t anc_base_configuration = 26;
	uint32_t anc_reserved[3] = {0, 0, 0};
	int i;

	params->data_size = 0;

	memcpy(((uint8_t *)params->buff + params->data_size), anc_reserved, sizeof(uint32_t) * 3);
	params->data_size += sizeof(uint32_t) * 3;

	memcpy(((uint8_t *)params->buff + params->data_size), &anc_configurations, sizeof(uint32_t));
	params->data_size += sizeof(uint32_t);

	for (i = 0; i < NUM_OF_ANC_RX_CONFIG; i++) {
		params->acdb_id = i + anc_base_configuration;
		send_wcd9xxx_anc_cal(params);
	}

	/* Add the AANC Rx device */
	params->acdb_id = DEVICE_HANDSET_RX_AANC_ACDB_ID;
	send_wcd9xxx_anc_cal(params);

	/* Add AANC tuning Rx devices */
	params->acdb_id = DEVICE_ANC_TEST_S_PATH_HANDSET_SPKR_ANC_MONO_ACDB_ID;
	send_wcd9xxx_anc_cal(params);

	params->acdb_id = DEVICE_ANC_TEST_E_PATH_HANDSET_SPKR_ANC_MONO_ACDB_ID;
	send_wcd9xxx_anc_cal(params);
done:
	return;
}

static int convert_vbat_data_to_wcd9xxx(int *pVbatacdbCfg, struct param_data *params)
{
	vbat_monitor_reg_t vbat_reg_config;
	int result;
	void *Vbatadc_data;

	acdb_loader_get_codec_data(&Vbatadc_data, "vbat_cal");

	result = vbat_conversion(&vbat_reg_config, (int8_t *) pVbatacdbCfg, (vbat_adc_data_t *) Vbatadc_data);

	memcpy(((uint8_t *)params->buff + params->data_size), &vbat_reg_config.size, sizeof(vbat_reg_config.size));
	params->data_size += sizeof(vbat_reg_config.size);

	memcpy(((uint8_t *)params->buff + params->data_size), vbat_reg_config.writes, (vbat_reg_config.size * CODEC_PACKED_REG_SIZE));
	params->data_size += vbat_reg_config.size * CODEC_PACKED_REG_SIZE;

	return result;
}

static void send_vbat_data(struct param_data *params)
{

	int result;
	AcdbGblTblCmdType global_cmd;
	AcdbQueryResponseType   response;

	LOGD("send vbat data\n");
	params->data_size = 0;

#ifndef STUB_VBAT
	global_cmd.nModuleId = ACDB_MID_VBAT_MONITOR;
	global_cmd.nParamId = ACDB_PID_ADC_CAL ;
	global_cmd.nBufferLength = params->buff_size;
	global_cmd.nBufferPointer = (uint8_t *)params->buff;

	LOGD("ACDB -> VBAT ACDB_PID_ADC_CAL\n");
	result = acdb_ioctl(ACDB_CMD_GET_GLBTBL_DATA,
		(const uint8_t *) &global_cmd, sizeof(global_cmd),
		(uint8_t *) &response, sizeof(response));

	if (result)
	{
		LOGE("Error reading VBAT ADC CAL returned = %x\n", result);
		goto acdb_error;
	}

	params->data_size += response.nBytesUsedInBuffer;
	global_cmd.nBufferLength = params->buff_size - params->data_size;
	global_cmd.nBufferPointer = (uint8_t *)params->buff + params->data_size;
	global_cmd.nParamId = ACDB_PID_GAIN_PROC;

	LOGD("ACDB -> VBAT ACDB_PID_GAIN_PROC\n");
	result = acdb_ioctl(ACDB_CMD_GET_GLBTBL_DATA,
		(const uint8_t *) &global_cmd, sizeof(global_cmd),
		(uint8_t *) &response, sizeof(response));

	if (result)
	{
		LOGE("Error reading VBAT GAIN PROC returned = %x\n", result);
		goto acdb_error;
	}

	params->data_size += response.nBytesUsedInBuffer;

acdb_error:
	return;
#else
	LOGD("send vbat data, copying vbat default data\n");
	result = convert_vbat_data_to_wcd9xxx(&vbat_cal, params);
	if (result)
	{
		LOGE("Error converting vbat data to wcd9xxx data = %x\n", result);
	}

	LOGD("copied vbat cal size =%d\n",params->data_size);
return;
#endif
}

int Setwcd9xxxANC_IIRCoeffs(uint32_t *anc_config, uint32_t *anc_index,
	struct adie_codec_taiko_db_anc_cfg *pANCCfg, uint32_t ancCh)
{
	int res = 0;
	int32_t coeff = 0;
	uint32_t u_coeff = 0;
	uint8_t valMSBs = 0;
	uint8_t valLSBs = 0;
	uint32_t iter = 0;
	uint32_t offset = ancCh * 128;
	uint8_t iir_index=0;
	double cal_gain = 0;
	int32_t temp_int = 0;

	/* Divide by 2^13 */
	cal_gain = ((double)pANCCfg[ancCh].anc_gain)/8192;
	/* Write FF coeffs */
	for (iter = 0; iter < TAIKO_ANC_NUM_IIR_FF_A_COEFFS + TAIKO_ANC_NUM_IIR_FF_B_COEFFS; iter++) {
		coeff = pANCCfg[ancCh].anc_ff_coeff[iter];
		if (iter < TAIKO_ANC_NUM_IIR_FF_A_COEFFS) {
			temp_int = (int32_t)round((double)(coeff)/16);
			u_coeff = (uint32_t)temp_int;
		}
		else {
			temp_int = (int32_t)round((((double)coeff * cal_gain) / 16));
			u_coeff = (uint32_t)temp_int;
		}
		valMSBs = (uint8_t) (0x0F & (u_coeff >> REGISTER_DEPTH));
		valLSBs = (uint8_t) (0xFF & u_coeff);
		anc_config[(*anc_index)++] = TAIKO_CODEC_PACK_ENTRY((TAIKO_A_CDC_ANC1_IIR_B2_CTL + offset), 0xFF, iir_index++);
                anc_config[(*anc_index)++] = TAIKO_CODEC_PACK_ENTRY((TAIKO_A_CDC_ANC1_IIR_B3_CTL + offset), 0xFF, valLSBs);
		anc_config[(*anc_index)++] = TAIKO_CODEC_PACK_ENTRY((TAIKO_A_CDC_ANC1_IIR_B2_CTL + offset), 0xFF, iir_index++);
		anc_config[(*anc_index)++] = TAIKO_CODEC_PACK_ENTRY((TAIKO_A_CDC_ANC1_IIR_B3_CTL + offset), 0xFF, valMSBs);
	}
	/* Write FB coeff */
	for (iter = 0; iter < TAIKO_ANC_NUM_IIR_FB_A_COEFFS + TAIKO_ANC_NUM_IIR_FB_B_COEFFS; iter++) {
		coeff = pANCCfg[ancCh].anc_fb_coeff[iter];
		temp_int = (int32_t)round((double)(coeff)/16);
		u_coeff = (uint32_t)temp_int;
		valMSBs = (uint8_t) (0x0F & (u_coeff >> REGISTER_DEPTH));
		valLSBs = (uint8_t) (0xFF & u_coeff);
		anc_config[(*anc_index)++] = TAIKO_CODEC_PACK_ENTRY((TAIKO_A_CDC_ANC1_IIR_B2_CTL + offset), 0xFF, iir_index++);
		anc_config[(*anc_index)++] = TAIKO_CODEC_PACK_ENTRY((TAIKO_A_CDC_ANC1_IIR_B3_CTL + offset), 0xFF, valLSBs);
		anc_config[(*anc_index)++] = TAIKO_CODEC_PACK_ENTRY((TAIKO_A_CDC_ANC1_IIR_B2_CTL + offset), 0xFF, iir_index++);
		anc_config[(*anc_index)++] = TAIKO_CODEC_PACK_ENTRY((TAIKO_A_CDC_ANC1_IIR_B3_CTL + offset), 0xFF, valMSBs);
	}
	return res;
}


int Setwcd9xxxANC_LPFShift(uint32_t *anc_config, uint32_t *anc_index,
	struct adie_codec_taiko_db_anc_cfg *pANCCfg, uint32_t ancCh)
{
	int res = 0;
	int32_t coeff = 0;
	uint32_t u_coeff = 0;
	uint8_t valMSBs = 0;
	uint8_t valLSBs = 0;
	uint32_t iter = 0;
	uint32_t offset = ancCh * 128;
	uint8_t value = 0;

	/* FF */
	value |= pANCCfg[ancCh].anc_ff_lpf_shift[0];
	value |= pANCCfg[ancCh].anc_ff_lpf_shift[1] << 4;
	anc_config[(*anc_index)++] =  TAIKO_CODEC_PACK_ENTRY((TAIKO_A_CDC_ANC1_LPF_B1_CTL + offset), 0xFF, value);

	/* FB */
	value = 0;
	value |= pANCCfg[ancCh].anc_fb_lpf_shift[0];
        value |= pANCCfg[ancCh].anc_fb_lpf_shift[1] << 4;
	anc_config[(*anc_index)++] =  TAIKO_CODEC_PACK_ENTRY((TAIKO_A_CDC_ANC1_LPF_B2_CTL + offset), 0xFF, value);

	return res;
}

int convert_anc_data_to_wcd9xxx(struct adie_codec_taiko_db_anc_cfg *pANCCfg,
				struct param_data *params)
{
	uint32_t index;
	uint32_t reg, mask, val;
	uint32_t temp_ctl_reg_val;
	uint32_t anc_index = 0;
	int j;
	uint32_t offset;
	uint32_t ancCh;
	bool ancDMICselect;
	struct storage_adie_codec_anc_data anc_config;

	for(ancCh = 0; ancCh < NUM_ANC_COMPONENTS; ancCh++) {
		if (!pANCCfg[ancCh].input_device) {
			continue;
		}
		offset = ancCh * 128;

		anc_config.writes[anc_index++] = TAIKO_CODEC_PACK_ENTRY(TAIKO_A_CDC_CLK_ANC_RESET_CTL, ancCh ? 0xC : 0x3, ancCh ? 0xC : 0x3);
		temp_ctl_reg_val = 0;
		if (pANCCfg[ancCh].ff_out_enable)
			temp_ctl_reg_val |= 0x1;
		if ((pANCCfg[ancCh].input_device & 0xF) >= ADIE_CODEC_DMIC1)
			temp_ctl_reg_val |= 0x2;
		if (pANCCfg[ancCh].anc_lr_mix_enable)
			temp_ctl_reg_val |= 0x4;
		if (pANCCfg[ancCh].hybrid_enable)
			temp_ctl_reg_val |= 0x8;
		if (pANCCfg[ancCh].ff_in_enable)
			temp_ctl_reg_val |= 0x10;
		if (pANCCfg[ancCh].dcflt_enable)
			temp_ctl_reg_val |= 0x20;
		if (pANCCfg[ancCh].smlpf_enable)
			temp_ctl_reg_val |= 0x40;
		if (pANCCfg[ancCh].adaptive_gain_enable)
			temp_ctl_reg_val |= 0x80;

		anc_config.writes[anc_index++] = TAIKO_CODEC_PACK_ENTRY((TAIKO_A_CDC_ANC1_B1_CTL + offset), 0xFF, temp_ctl_reg_val);
		anc_config.writes[anc_index++] = TAIKO_CODEC_PACK_ENTRY((TAIKO_A_CDC_ANC1_SHIFT + offset), 0xFF, ((pANCCfg[ancCh].anc_ff_shift << 4) | pANCCfg[ancCh].anc_fb_shift));
		/* IIR COEFFS */
		anc_config.writes[anc_index++] = TAIKO_CODEC_PACK_ENTRY((TAIKO_A_CDC_ANC1_IIR_B1_CTL + offset), 0xFF, 0x00);

		Setwcd9xxxANC_IIRCoeffs((uint32_t *)anc_config.writes, &anc_index, pANCCfg, ancCh);

		/* LPF COEFFS */
		Setwcd9xxxANC_LPFShift((uint32_t *)anc_config.writes, &anc_index, pANCCfg, ancCh);

		/* ANC SMLPF CTL */
		anc_config.writes[anc_index++] = TAIKO_CODEC_PACK_ENTRY((TAIKO_A_CDC_ANC1_SMLPF_CTL + offset), 0xFF, pANCCfg[ancCh].smlpf_shift);
		/* ANC DCFLT CTL */
		anc_config.writes[anc_index++] = TAIKO_CODEC_PACK_ENTRY((TAIKO_A_CDC_ANC1_DCFLT_CTL + offset), 0xFF, pANCCfg[ancCh].dcflt_shift);
		/* ANC Adaptive gain */
		anc_config.writes[anc_index++] = TAIKO_CODEC_PACK_ENTRY((TAIKO_A_CDC_ANC1_GAIN_CTL + offset), 0xFF, pANCCfg[ancCh].adaptive_gain);
		anc_config.writes[anc_index++] = TAIKO_CODEC_PACK_ENTRY(TAIKO_A_CDC_CLK_ANC_CLK_EN_CTL, ancCh ? 0xC : 0x3, (1 | (1 << pANCCfg[ancCh].anc_feedback_enable)) << (ancCh*2));
		anc_config.writes[anc_index++] = TAIKO_CODEC_PACK_ENTRY(TAIKO_A_CDC_CLK_ANC_RESET_CTL, ancCh ? 0xC : 0x3, ~((1 | (1 << pANCCfg[ancCh].anc_feedback_enable)) << (ancCh*2)));
	}
	anc_config.size = anc_index;

	memcpy(((uint8_t *)params->buff + params->data_size), &anc_config.size, sizeof(anc_config.size));
	params->data_size += sizeof(anc_config.size);

	memcpy(((uint8_t *)params->buff + params->data_size), anc_config.writes, (anc_config.size * TAIKO_PACKED_REG_SIZE));
	params->data_size += anc_config.size * TAIKO_PACKED_REG_SIZE;


	return anc_index;
}

int send_wcd9xxx_anc_cal(struct param_data *params)
{
	int					result;
	struct cal_block			*block;
	struct adie_codec_taiko_db_anc_cfg	*ancCfg;

	block = get_cal_block(AFE_ANC_CAL_TYPE, BUFF_IDX_0);
	if (block == NULL) {
		LOGE("%s: Error: Could not get cal block!\n", __func__);
		goto done;
	}

	result = get_anc_table(params->acdb_id, block);
	if (result) {
		return result;
	}
	ancCfg = (struct adie_codec_taiko_db_anc_cfg *)(block->vaddr);

	convert_anc_data_to_wcd9xxx(ancCfg, params);
done:
	return 0;
}

static uint32_t get_voice_topology(struct audio_cal_voc_top *voc_top)
{
	int				result = 0;
	AcdbGetVocProcTopIdCmdType	acdb_get_top;
	AcdbGetTopologyIdRspType	audio_top;

	acdb_get_top.nDeviceId = voc_top->cal_type.cal_info.acdb_id;

	LOGD("ACDB -> ACDB_CMD_GET_VOCPROC_COMMON_TOPOLOGY_ID\n");

	result = acdb_ioctl(ACDB_CMD_GET_VOCPROC_COMMON_TOPOLOGY_ID,
		(const uint8_t *)&acdb_get_top, sizeof(acdb_get_top),
		(uint8_t *)&audio_top, sizeof(audio_top));
	if (result) {
		LOGE("Error: ACDB get voice rx topology for acdb id = %d, returned = %d\n",
		     voc_top->cal_type.cal_info.acdb_id, result);
		voc_top->cal_type.cal_info.topology = 0;
		goto err;
	}
	voc_top->cal_type.cal_info.topology = audio_top.nTopologyId;
err:
	return result;
}

static int get_sidetone(struct audio_cal_sidetone *sidetone_cal)
{
	int				result = 0;
	AcdbAfeDataCmdType		sidetone;
	AcdbQueryResponseType		response;

	sidetone.nTxDeviceId = sidetone_cal->cal_type.cal_info.tx_acdb_id;
	sidetone.nRxDeviceId = sidetone_cal->cal_type.cal_info.rx_acdb_id;
	sidetone.nModuleId = sidetone_cal->cal_type.cal_info.mid;
	sidetone.nParamId = sidetone_cal->cal_type.cal_info.pid;
	sidetone.nBufferLength = sizeof(sidetone_cal->cal_type.cal_info.enable) +
					sizeof(sidetone_cal->cal_type.cal_info.gain);
	sidetone.nBufferPointer = (uint8_t *)&sidetone_cal->cal_type.cal_info;

	LOGD("ACDB -> ACDB_CMD_GET_AFE_DATA\n");

	result = acdb_ioctl(ACDB_CMD_GET_AFE_DATA,
		(const uint8_t *)&sidetone, sizeof(sidetone),
		(uint8_t *)&response, sizeof(response));
	if (result) {
		LOGE("Error: ACDB AFE DATA Returned = %d\n", result);
		sidetone_cal->cal_type.cal_data.cal_size = 0;
		goto done;
	}
	sidetone_cal->cal_type.cal_data.cal_size = response.nBytesUsedInBuffer;
done:
	return result;
}

static int get_voice_columns(struct cal_block *block,
				struct audio_cal_voc_col *voc_col_cal)
{
	int				result = 0;
	AcdbVocColumnsInfoCmdType	voc_col;
	AcdbVocColumnsInfoCmdType_v2	voc_col_v2;
	AcdbQueryResponseType		response;

	if (block->version & PER_VOCODER_CAL_BIT_MASK) {
		voc_col_v2.nTableId = voc_col_cal->cal_type.cal_info.table_id;
		voc_col_v2.nTxDeviceId = voc_col_cal->cal_type.cal_info.tx_acdb_id;
		voc_col_v2.nRxDeviceId = voc_col_cal->cal_type.cal_info.rx_acdb_id;
		voc_col_v2.nBufferLength = block->size;
		voc_col_v2.pBuff = (uint8_t *)block->vaddr;

		LOGD("ACDB -> ACDB_CMD_GET_VOC_COLUMNS_INFO_V2\n");

		result = acdb_ioctl(ACDB_CMD_GET_VOC_COLUMNS_INFO_V2,
			(const uint8_t *)&voc_col_v2, sizeof(voc_col_v2),
			(uint8_t *)&response, sizeof(response));
		if (result) {
			LOGE("Error: ACDB VOC COL V2 Returned = %d\n", result);
			voc_col_cal->cal_type.cal_data.cal_size = 0;
			goto done;
		}
	} else {
		voc_col.nTableId = voc_col_cal->cal_type.cal_info.table_id;
		voc_col.nBufferLength = block->size;
		voc_col.pBuff = (uint8_t *)block->vaddr;

		LOGD("ACDB -> ACDB_CMD_GET_VOC_COLUMNS_INFO\n");

		result = acdb_ioctl(ACDB_CMD_GET_VOC_COLUMNS_INFO,
			(const uint8_t *)&voc_col, sizeof(voc_col),
			(uint8_t *)&response, sizeof(response));
		if (result) {
			LOGE("Error: ACDB VOC COL Returned = %d\n", result);
			voc_col_cal->cal_type.cal_data.cal_size = 0;
			goto done;
		}
	}

	voc_col_cal->cal_type.cal_data.cal_size = response.nBytesUsedInBuffer;
done:
	return result;
}

static int get_col_cal_type(uint32_t table_id)
{
	int	table = CVP_VOCPROC_STATIC_COL_CAL_TYPE;

	switch (table_id) {
	case ACDB_VOC_PROC_TABLE_V2:
	case ACDB_VOC_PROC_STAT_TABLE_V2:
		table = CVP_VOCPROC_STATIC_COL_CAL_TYPE;
		break;
	case ACDB_VOC_PROC_VOL_TABLE_V2:
	case ACDB_VOC_PROC_DYN_TABLE_V2:
		table = CVP_VOCPROC_DYNAMIC_COL_CAL_TYPE;
		break;
	case ACDB_VOC_STREAM_TABLE_V2:
	case ACDB_VOC_STREAM2_TABLE_V2:
		table = CVS_VOCSTRM_STATIC_COL_CAL_TYPE;
		break;
	default:
		LOGE("%s: Error: %d not a valid table ID!\n",
			__func__, table_id);
	}
	return table;
}

static int send_voice_columns(uint32_t rxacdb_id,
	   uint32_t txacdb_id, uint32_t table_id)
{
	int		result = 0;
	struct cal_block		*block;
	struct audio_cal_voc_col	voc_col;
	LOGD("ACDB -> send_voice_columns, rxacdb_id %d, txacdb_id %d, table %d\n",
		   rxacdb_id, txacdb_id, table_id);

	block = get_cal_block(get_col_cal_type(table_id), BUFF_IDX_0);
	if (block == NULL) {
		LOGE("%s: Error: Could not get cal block!\n", __func__);
		goto done;
	}

	voc_col.hdr.data_size = sizeof(voc_col);
	voc_col.hdr.version = VERSION_0_0;
	voc_col.hdr.cal_type = get_col_cal_type(table_id);
	voc_col.hdr.cal_type_size = sizeof(voc_col.cal_type);
	voc_col.cal_type.cal_hdr.buffer_number = BUFF_IDX_0;
	voc_col.cal_type.cal_hdr.version = block->version;
	voc_col.cal_type.cal_info.table_id = table_id;
	voc_col.cal_type.cal_info.rx_acdb_id = rxacdb_id;
	voc_col.cal_type.cal_info.tx_acdb_id = txacdb_id;
	voc_col.cal_type.cal_data.mem_handle = block->map_handle;
	block->size = sizeof(voc_col.cal_type.cal_info.data);
	block->vaddr = (void *)&voc_col.cal_type.cal_info.data;

	get_voice_columns(block, &voc_col);

	if (voc_col.cal_type.cal_data.cal_size != 0) {
		voc_col.hdr.data_size = sizeof(voc_col) -
			sizeof(voc_col.cal_type.cal_info.data) +
			voc_col.cal_type.cal_data.cal_size;
		voc_col.hdr.cal_type_size = sizeof(voc_col.cal_type) -
			sizeof(voc_col.cal_type.cal_info.data) +
			voc_col.cal_type.cal_data.cal_size;
	}
	/* subtract 1 to map acdb.h defines to indexes */
	result = ioctl(cal_driver_handle, AUDIO_SET_CALIBRATION,
		&voc_col);
	if (result) {
		LOGE("Error: Sending ACDB voice columns result = %d\n", result);
		goto done;
	}
done:
	return result;
}

static int get_vocproc_dev_cfg(struct cal_block *block,
				struct audio_cal_vocdev_cfg *vocdev_cal)
{
	int				result = 0;
	AcdbVocProcDevCfgCmdType	vocdevtable;
	AcdbQueryResponseType		response;

	vocdevtable.nTxDeviceId = vocdev_cal->cal_type.cal_info.tx_acdb_id;
	vocdevtable.nRxDeviceId = vocdev_cal->cal_type.cal_info.rx_acdb_id;
	vocdevtable.nBufferLength = block->size;
	vocdevtable.pBuff = (uint8_t *)block->vaddr;

	LOGD("ACDB -> ACDB_CMD_GET_VOC_PROC_DEVICE_CFG\n");

	result = acdb_ioctl(ACDB_CMD_GET_VOC_PROC_DEVICE_CFG,
		(const uint8_t *)&vocdevtable, sizeof(vocdevtable),
		(uint8_t *)&response, sizeof(response));
	if (result) {
		LOGE("Error: ACDB VocProc Dev Cfg Returned = %d\n",
			result);
		vocdev_cal->cal_type.cal_data.cal_size = 0;
		goto done;
	}

	vocdev_cal->cal_type.cal_data.cal_size = response.nBytesUsedInBuffer;
done:
	return result;
}
static int send_vocproc_dev_cfg(int rxacdb_id, int txacdb_id)
{
	int				result = 0;
	struct cal_block		*block;
	struct audio_cal_vocdev_cfg	vocdev_cal;

	block = get_cal_block(CVP_VOCDEV_CFG_CAL_TYPE, BUFF_IDX_0);
	if (block == NULL) {
		LOGE("%s: Error: Could not get cal block!\n", __func__);
		goto done;
	}

	vocdev_cal.hdr.data_size = sizeof(vocdev_cal);
	vocdev_cal.hdr.version = VERSION_0_0;
	vocdev_cal.hdr.cal_type = CVP_VOCDEV_CFG_CAL_TYPE;
	vocdev_cal.hdr.cal_type_size = sizeof(vocdev_cal.cal_type);
	vocdev_cal.cal_type.cal_hdr.buffer_number = BUFF_IDX_0;
	vocdev_cal.cal_type.cal_hdr.version = block->version;
	vocdev_cal.cal_type.cal_info.tx_acdb_id = txacdb_id;
	vocdev_cal.cal_type.cal_info.rx_acdb_id = rxacdb_id;
	vocdev_cal.cal_type.cal_data.mem_handle = block->map_handle;

	get_vocproc_dev_cfg(block, &vocdev_cal);

	LOGD("ACDB -> AUDIO_SET_VOCPROC_DEV_CFG_CAL\n");
	result = ioctl(cal_driver_handle, AUDIO_SET_CALIBRATION, &vocdev_cal);
	if (result) {
		LOGE("Error: Sending ACDB VocProc Dev Cfg data result = %d\n", result);
		goto done;
	}
done:
	return result;
}


static int get_voctable(struct cal_block *block,
				struct audio_cal_vocproc *vocproc_cal)
{
	int				result = 0;
	AcdbVocProcCmnTblCmdType	voctable;
	AcdbQueryResponseType		response;
	int	acdb_cmd[2] = {ACDB_CMD_GET_VOC_PROC_COMMON_TABLE,
				ACDB_CMD_GET_VOC_PROC_STATIC_TABLE};
	bool	per_vocoder_enabled = false;

	voctable.nTxDeviceId = vocproc_cal->cal_type.cal_info.tx_acdb_id;
	voctable.nRxDeviceId = vocproc_cal->cal_type.cal_info.rx_acdb_id;
	voctable.nTxDeviceSampleRateId = vocproc_cal->cal_type.cal_info.tx_sample_rate;
	voctable.nRxDeviceSampleRateId = vocproc_cal->cal_type.cal_info.rx_sample_rate;
	voctable.nBufferLength = block->size;
	voctable.nBufferPointer = (uint8_t *)block->vaddr;

	per_vocoder_enabled = !!(block->version & PER_VOCODER_CAL_BIT_MASK);

	LOGD("ACDB -> %s\n", per_vocoder_enabled ?
			"ACDB_CMD_GET_VOC_PROC_STATIC_TABLE":
			"ACDB_CMD_GET_VOC_PROC_COMMON_TABLE");
	result = acdb_ioctl(acdb_cmd[per_vocoder_enabled],
			(const uint8_t *)&voctable, sizeof(voctable),
			(uint8_t *)&response, sizeof(response));

	if (result) {
		LOGE("Error: %s Returned = %d\n", per_vocoder_enabled ?
			"ACDB_CMD_GET_VOC_PROC_STATIC_TABLE":
			"ACDB_CMD_GET_VOC_PROC_COMMON_TABLE",
			result);
		vocproc_cal->cal_type.cal_data.cal_size = 0;
		goto done;
	}
	vocproc_cal->cal_type.cal_data.cal_size = response.nBytesUsedInBuffer;
done:
	return result;
}

static int get_vocstrmtable(struct cal_block *block,
				struct audio_cal_basic *vocstrm_cal)
{
	int				result = 0;
	AcdbQueryCmdType		vocstrmtable;
	AcdbQueryResponseType		response;
	int	acdb_cmd[2] = {ACDB_CMD_GET_VOC_STREAM_COMMON_TABLE,
				ACDB_CMD_GET_VOC_STREAM_STATIC_TABLE};
	bool	per_vocoder_enabled = false;

	vocstrmtable.nBufferLength = block->size;
	vocstrmtable.pBufferPointer = (uint8_t *)block->vaddr;

	per_vocoder_enabled = !!(block->version & PER_VOCODER_CAL_BIT_MASK);

	LOGD("ACDB -> %s\n", per_vocoder_enabled ?
			"ACDB_CMD_GET_VOC_STREAM_STATIC_TABLE":
			"ACDB_CMD_GET_VOC_STREAM_COMMON_TABLE");
	result = acdb_ioctl(acdb_cmd[per_vocoder_enabled],
			(const uint8_t *)&vocstrmtable, sizeof(vocstrmtable),
			(uint8_t *)&response, sizeof(response));

	if (result) {
		LOGE("Error: %s Returned = %d\n", per_vocoder_enabled ?
			"ACDB_CMD_GET_VOC_STREAM_STATIC_TABLE":
			"ACDB_CMD_GET_VOC_STREAM_COMMON_TABLE",
			result);
		vocstrm_cal->cal_type.cal_data.cal_size = 0;
		goto done;
	}
	vocstrm_cal->cal_type.cal_data.cal_size = response.nBytesUsedInBuffer;
done:
	return result;
}

static int get_vocvoltable(struct cal_block *block,
				struct audio_cal_vocvol *vocvol_cal)
{
	AcdbVocProcGainDepVolTblV2CmdType	vocvoltable;
	int					result = 0;
	AcdbQueryResponseType			response;
	int	acdb_cmd[2] = {ACDB_CMD_GET_VOC_PROC_GAIN_DEP_VOLTBL_V2,
				ACDB_CMD_GET_VOC_PROC_DYNAMIC_TABLE};
	bool	per_vocoder_enabled = false;

	vocvoltable.nTxDeviceId = vocvol_cal->cal_type.cal_info.tx_acdb_id;
	vocvoltable.nRxDeviceId = vocvol_cal->cal_type.cal_info.rx_acdb_id;
	vocvoltable.nFeatureId = vocvol_cal->cal_type.cal_info.feature_set;
	vocvoltable.nBufferLength = block->size;
	vocvoltable.nBufferPointer = (uint8_t *)block->vaddr;

	per_vocoder_enabled = !!(block->version & PER_VOCODER_CAL_BIT_MASK);

	LOGD("ACDB -> %s\n", per_vocoder_enabled ?
			"ACDB_CMD_GET_VOC_PROC_DYNAMIC_TABLE":
			"ACDB_CMD_GET_VOC_PROC_GAIN_DEP_VOLTBL_V2");
	result = acdb_ioctl(acdb_cmd[per_vocoder_enabled],
			(const uint8_t *)&vocvoltable, sizeof(vocvoltable),
			(uint8_t *)&response, sizeof(response));

	if (result) {
		LOGE("Error: %s Returned = %d\n", per_vocoder_enabled ?
			"ACDB_CMD_GET_VOC_PROC_DYNAMIC_TABLE":
			"ACDB_CMD_GET_VOC_PROC_GAIN_DEP_VOLTBL_V2",
			result);
		vocvol_cal->cal_type.cal_data.cal_size = 0;
		goto done;
	}
	vocvol_cal->cal_type.cal_data.cal_size = response.nBytesUsedInBuffer;
done:
	return result;
}

static int send_voice_rx_topology(int acdb_id)
{
	int				result = 0;
	struct cal_block		*block;
	struct audio_cal_voc_top	voc_top;
	LOGD("ACDB -> send_voice_rx_topology\n");

	block = get_cal_block(CVP_VOC_RX_TOPOLOGY_CAL_TYPE, BUFF_IDX_0);
	if (block == NULL) {
		LOGE("%s: Error: Could not get cal block!\n", __func__);
		goto done;
	}
	voc_top.hdr.data_size = sizeof(voc_top);
	voc_top.hdr.version = VERSION_0_0;
	voc_top.hdr.cal_type = CVP_VOC_RX_TOPOLOGY_CAL_TYPE;
	voc_top.hdr.cal_type_size = sizeof(voc_top.cal_type);
	voc_top.cal_type.cal_hdr.buffer_number = BUFF_IDX_0;
	voc_top.cal_type.cal_hdr.version = block->version;
	voc_top.cal_type.cal_info.acdb_id = acdb_id;
	voc_top.cal_type.cal_data.mem_handle = block->map_handle;

	result = get_voice_topology(&voc_top);
	if (result < 0)
		goto done;

	result = ioctl(cal_driver_handle, AUDIO_SET_CALIBRATION,
		&voc_top);
	if (result) {
		LOGE("Error: Sending ACDB voice rx topology result = %d\n", result);
		goto done;
	}
done:
	return result;
}

static int send_voice_tx_topology(int acdb_id)
{
	int				result = 0;
	struct cal_block		*block;
	struct audio_cal_voc_top	voc_top;
	LOGD("ACDB -> send_voice_tx_topology\n");

	block = get_cal_block(CVP_VOC_TX_TOPOLOGY_CAL_TYPE, BUFF_IDX_0);
	if (block == NULL) {
		LOGE("%s: Error: Could not get cal block!\n", __func__);
		goto done;
	}
	voc_top.hdr.data_size = sizeof(voc_top);
	voc_top.hdr.version = VERSION_0_0;
	voc_top.hdr.cal_type = CVP_VOC_TX_TOPOLOGY_CAL_TYPE;
	voc_top.hdr.cal_type_size = sizeof(voc_top.cal_type);
	voc_top.cal_type.cal_hdr.buffer_number = BUFF_IDX_0;
	voc_top.cal_type.cal_hdr.version = block->version;
	voc_top.cal_type.cal_info.acdb_id = acdb_id;
	voc_top.cal_type.cal_data.mem_handle = block->map_handle;

	result = get_voice_topology(&voc_top);
	if (result < 0)
		goto done;

	result = ioctl(cal_driver_handle, AUDIO_SET_CALIBRATION,
		&voc_top);
	if (result) {
		LOGE("Error: Sending ACDB voice tx topology result = %d\n", result);
		goto done;
	}
done:
	return result;
}

static int send_sidetone(int rxacdb_id, int txacdb_id)
{
	int				result = 0;
	struct cal_block		*block;
	struct audio_cal_sidetone	sidetone_cal;

	block = get_cal_block(AFE_SIDETONE_CAL_TYPE, BUFF_IDX_0);
	if (block == NULL) {
		LOGE("%s: Error: Could not get cal block!\n", __func__);
		goto done;
	}

	sidetone_cal.hdr.data_size = sizeof(sidetone_cal);
	sidetone_cal.hdr.version = VERSION_0_0;
	sidetone_cal.hdr.cal_type = AFE_SIDETONE_CAL_TYPE;
	sidetone_cal.hdr.cal_type_size = sizeof(sidetone_cal.cal_type);
	sidetone_cal.cal_type.cal_hdr.buffer_number = BUFF_IDX_0;
	sidetone_cal.cal_type.cal_hdr.version = block->version;
	sidetone_cal.cal_type.cal_info.tx_acdb_id = txacdb_id;
	sidetone_cal.cal_type.cal_info.rx_acdb_id = rxacdb_id;
	sidetone_cal.cal_type.cal_info.mid = ACDB_MID_SIDETONE;
	sidetone_cal.cal_type.cal_info.pid = ACDB_PID_SIDETONE;
	sidetone_cal.cal_type.cal_data.mem_handle = block->map_handle;

	get_sidetone(&sidetone_cal);

	LOGD("ACDB -> AUDIO_SET_SIDETONE_CAL\n");
	result = ioctl(cal_driver_handle, AUDIO_SET_CALIBRATION,
			&sidetone_cal);
	if (result) {
		LOGE("Error: Sending ACDB sidetone data result = %d\n", result);
		goto done;
	}
done:
	return result;
}

static int send_voctable(int rxacdb_id, int txacdb_id)
{
	int				result = 0;
	int				voctable_size = 0;
	struct cal_block		*block;
	struct audio_cal_vocproc	vocproc_cal;

	block = get_cal_block(CVP_VOCPROC_STATIC_CAL_TYPE, BUFF_IDX_0);
	if (block == NULL) {
		LOGE("%s: Error: Could not get cal block!\n", __func__);
		goto done;
	}

	/* Get voctable size from s/w and reallocate cal type in case of insufficient memory */
	voctable_size = get_voctable_size(rxacdb_id, txacdb_id, get_samplerate(rxacdb_id), get_samplerate(txacdb_id));
	if (voctable_size > block->size)
	{
		LOGD("Reallocate memory for VocProc Table with size: %d\n", (voctable_size + MIN_BUFF_SIZE));
		deallocate_cal_type(CVP_VOCPROC_STATIC_CAL_TYPE, BUFF_IDX_0);
		allocate_cal_type(CVP_VOCPROC_STATIC_CAL_TYPE, (voctable_size + MIN_BUFF_SIZE), BUFF_IDX_0);
	}

	vocproc_cal.hdr.data_size = sizeof(vocproc_cal);
	vocproc_cal.hdr.version = VERSION_0_0;
	vocproc_cal.hdr.cal_type = CVP_VOCPROC_STATIC_CAL_TYPE;
	vocproc_cal.hdr.cal_type_size = sizeof(vocproc_cal.cal_type);
	vocproc_cal.cal_type.cal_hdr.buffer_number = BUFF_IDX_0;
	vocproc_cal.cal_type.cal_hdr.version = block->version;
	vocproc_cal.cal_type.cal_info.tx_acdb_id = txacdb_id;
	vocproc_cal.cal_type.cal_info.rx_acdb_id = rxacdb_id;
	vocproc_cal.cal_type.cal_info.tx_sample_rate = get_samplerate(txacdb_id);
	vocproc_cal.cal_type.cal_info.rx_sample_rate = get_samplerate(rxacdb_id);
	vocproc_cal.cal_type.cal_data.mem_handle = block->map_handle;

	get_voctable(block, &vocproc_cal);

	LOGD("ACDB -> AUDIO_SET_VOCPROC_CAL\n");
	result = ioctl(cal_driver_handle, AUDIO_SET_CALIBRATION, &vocproc_cal);
	if (result) {
		LOGE("Error: Sending ACDB VocProc data result = %d\n", result);
		goto done;
	}
done:
	return result;
}

static int send_vocstrmtable(void) {
	int				result = 0;
	int				vocstrmtable_size = 0;
	struct cal_block		*block;
	struct audio_cal_basic		vocstrm_cal;

	block = get_cal_block(CVS_VOCSTRM_STATIC_CAL_TYPE, BUFF_IDX_0);
	if (block == NULL) {
		LOGE("%s: Error: Could not get cal block!\n", __func__);
		goto done;
	}

	/* Get vocstrmtable size from s/w and reallocate cal type in case of insufficient memory */
	vocstrmtable_size = get_vocstrmtable_size();
	if (vocstrmtable_size > block->size)
	{
		LOGD("Reallocate memory for VocStrm Table with size: %d\n", (vocstrmtable_size + MIN_BUFF_SIZE));
		deallocate_cal_type(CVS_VOCSTRM_STATIC_CAL_TYPE, BUFF_IDX_0);
		allocate_cal_type(CVS_VOCSTRM_STATIC_CAL_TYPE, (vocstrmtable_size + MIN_BUFF_SIZE), BUFF_IDX_0);
	}

	vocstrm_cal.hdr.data_size = sizeof(vocstrm_cal);
	vocstrm_cal.hdr.version = VERSION_0_0;
	vocstrm_cal.hdr.cal_type = CVS_VOCSTRM_STATIC_CAL_TYPE;
	vocstrm_cal.hdr.cal_type_size = sizeof(vocstrm_cal.cal_type);
	vocstrm_cal.cal_type.cal_hdr.buffer_number = BUFF_IDX_0;
	vocstrm_cal.cal_type.cal_hdr.version = block->version;
	vocstrm_cal.cal_type.cal_data.mem_handle = block->map_handle;

	get_vocstrmtable(block, &vocstrm_cal);

	LOGD("ACDB -> AUDIO_SET_VOCPROC_STREAM_CAL\n");
	result = ioctl(cal_driver_handle, AUDIO_SET_CALIBRATION,
		&vocstrm_cal);
	if (result < 0) {
		LOGE("Error: Sending ACDB VOCPROC STREAM fail result %d\n",
			result);
		goto done;
	}
done:
	return result;
}

static int send_vocvoltable(int rxacdb_id, int txacdb_id, int feature_set)
{
	int				result = 0;
	int				vocvoltable_size = 0;
	struct cal_block		*block;
	struct audio_cal_vocvol		vocvol_cal;

	block = get_cal_block(CVP_VOCPROC_DYNAMIC_CAL_TYPE, BUFF_IDX_0);
	if (block == NULL) {
		LOGE("%s: Error: Could not get cal block!\n", __func__);
		goto done;
	}

	/* Get vocvoltable size from s/w and reallocate cal type in case of insufficient memory */
	vocvoltable_size = get_vocvoltable_size(rxacdb_id, txacdb_id, feature_set);
	if (vocvoltable_size > block->size)
	{
		LOGD("Reallocate memory for VocVol Table with size: %d\n", (vocvoltable_size + MIN_BUFF_SIZE));
		deallocate_cal_type(CVP_VOCPROC_DYNAMIC_CAL_TYPE, BUFF_IDX_0);
		allocate_cal_type(CVP_VOCPROC_DYNAMIC_CAL_TYPE, (vocvoltable_size + MIN_BUFF_SIZE), BUFF_IDX_0);
	}

	vocvol_cal.hdr.data_size = sizeof(vocvol_cal);
	vocvol_cal.hdr.version = VERSION_0_0;
	vocvol_cal.hdr.cal_type = CVP_VOCPROC_DYNAMIC_CAL_TYPE;
	vocvol_cal.hdr.cal_type_size = sizeof(vocvol_cal.cal_type);
	vocvol_cal.cal_type.cal_hdr.buffer_number = BUFF_IDX_0;
	vocvol_cal.cal_type.cal_hdr.version = block->version;
	vocvol_cal.cal_type.cal_info.tx_acdb_id = txacdb_id;
	vocvol_cal.cal_type.cal_info.rx_acdb_id = rxacdb_id;
	vocvol_cal.cal_type.cal_info.feature_set = feature_set;
	vocvol_cal.cal_type.cal_data.mem_handle = block->map_handle;

	result = get_vocvoltable(block, &vocvol_cal);
	if (result < 0)
		LOGE("Error: getting VocVol Table = %d\n", result);

	LOGD("ACDB -> AUDIO_SET_VOCPROC_VOL_CAL\n");
	result = ioctl(cal_driver_handle, AUDIO_SET_CALIBRATION,
			&vocvol_cal);
	if (result) {
		LOGE("Error: Sending ACDB VocProc data result = %d\n", result);
		goto done;
	}
done:
	return result;
}

static int validate_voc_cal_dev_pair(int rxacdb_id, int txacdb_id)
{
	int result = 0;
	AcdbDevicePairType dev_pair;
	AcdbDevicePairingResponseType response;

	dev_pair.nTxDeviceId = txacdb_id;
	dev_pair.nRxDeviceId = rxacdb_id;

        result = acdb_ioctl(ACDB_CMD_IS_DEVICE_PAIRED,
			(const uint8_t *)&dev_pair, sizeof(dev_pair),
			(uint8_t *)&response, sizeof(response));

	if (result < 0) {
		LOGE("Error: failure to vaildate the device pair = %d\n",
			result);
		goto done;
	}

	result = (int)response.ulIsDevicePairValid;
done:
	return result;
}

static void send_voice_cal(int rxacdb_id, int txacdb_id, int feature_set)
{
	int	acdb_table_id[2][3] = {
				{ACDB_VOC_PROC_TABLE_V2,
				ACDB_VOC_PROC_VOL_TABLE_V2,
				ACDB_VOC_STREAM_TABLE_V2},
				{ACDB_VOC_PROC_STAT_TABLE_V2,
				ACDB_VOC_PROC_DYN_TABLE_V2,
				ACDB_VOC_STREAM2_TABLE_V2}
				};
	bool	per_vocoder_enabled = false;

	per_vocoder_enabled = !!(mem_data[voice_cal_types[0]].version &
					PER_VOCODER_CAL_BIT_MASK);

	LOGD("ACDB -> send_voice_cal, acdb_rx = %d, acdb_tx = %d, feature_set = %d\n",
		rxacdb_id, txacdb_id, feature_set);

	if (!is_initialized) {
		LOGE("ACDB -> Not correctly initialized!\n");
		goto done;
	}

	/* check if it is valid RX/TX device pair */
	if (validate_voc_cal_dev_pair(rxacdb_id, txacdb_id) != 1) {
		LOGE("ACDB -> Error: invalid device pair!");
		goto done;
	}

	current_voice_tx_acdb_id = txacdb_id;
	current_voice_rx_acdb_id = rxacdb_id;

	send_voice_rx_topology(rxacdb_id);
	send_voice_tx_topology(txacdb_id);
	send_sidetone(rxacdb_id, txacdb_id);

	send_voice_columns(rxacdb_id, txacdb_id, acdb_table_id[per_vocoder_enabled][0]);
	send_voctable(rxacdb_id, txacdb_id);
	send_vocproc_dev_cfg(rxacdb_id, txacdb_id);
	send_voice_columns(rxacdb_id, txacdb_id, acdb_table_id[per_vocoder_enabled][1]);
	if((send_vocvoltable(rxacdb_id, txacdb_id, feature_set) < 0) &&
			(feature_set != ACDB_VOCVOL_FID_DEFAULT)) {
		LOGD("ACDB -> feature set %d failed, using default feature set\n", feature_set);
		if (send_vocvoltable(rxacdb_id, txacdb_id, ACDB_VOCVOL_FID_DEFAULT) < 0)
			LOGE("ACDB -> Resend default vocvol unsuccessful!\n");
	}
	send_voice_columns(rxacdb_id, txacdb_id, acdb_table_id[per_vocoder_enabled][2]);
	send_vocstrmtable();
	send_afe_cal(txacdb_id, TX_DEVICE, get_samplerate(txacdb_id), AFE_COMMON_TX_CAL_TYPE);
	send_afe_cal(rxacdb_id, RX_DEVICE, get_samplerate(rxacdb_id), AFE_COMMON_RX_CAL_TYPE);
	send_aanctable(txacdb_id);
	send_hw_delay(txacdb_id, TX_DEVICE);
	send_hw_delay(rxacdb_id, RX_DEVICE);

	LOGD("ACDB -> Sent VocProc Cal!\n");
done:
	return;
}

void acdb_loader_send_voice_cal_v2(int rxacdb_id, int txacdb_id, int feature_set)
{
	pthread_mutex_lock(&loader_mutex);
	send_voice_cal(rxacdb_id, txacdb_id, feature_set);
	pthread_mutex_unlock(&loader_mutex);
}

void acdb_loader_send_voice_cal(int rxacdb_id, int txacdb_id)
{
	pthread_mutex_lock(&loader_mutex);
	send_voice_cal(rxacdb_id, txacdb_id, current_feature_set);
	pthread_mutex_unlock(&loader_mutex);
}

#ifndef _ANDROID_
void acdb_loader_send_vbat_cal()
{
	struct param_data calib;
	struct wcdcal_ioctl_buffer codec_buffer;
	int result, fd;
	int ret = 0;
	fd = open("/dev/snd/hwC0D1000", O_WRONLY);

	calib.get_size = 1;
	ret = acdb_loader_get_calibration("vbat_cal", sizeof(struct param_data),
                                                                &calib);
	if (ret < 0) {
            LOGE("%s get_calibration failed\n", __func__);
            return ret;
	}
	calib.get_size = 0;
	calib.buff = malloc(calib.buff_size);
	ret = acdb_loader_get_calibration(cal_name_info[WCD9XXX_VBAT_CAL],
                           sizeof(struct param_data), &calib);
	if (ret < 0) {
            ALOGE("%s get_calibration failed\n", __func__);
            free(calib.buff);
	    return ret;
	}
	codec_buffer.buffer = calib.buff;
	codec_buffer.size = calib.data_size;
	codec_buffer.cal_type = WCD9XXX_VBAT_CAL;

	if (ioctl(fd, SNDRV_CTL_IOCTL_HWDEP_CAL_TYPE, &codec_buffer) < 0)
            LOGE("Failed to call ioctl  for %s err=%d",
                                  cal_name_info[WCD9XXX_VBAT_CAL], errno);

	LOGD("%s: cal sent for %s", __func__, cal_name_info[WCD9XXX_VBAT_CAL]);
}
#endif

static int get_vocvoltable_size(int rxacdb_id, int txacdb_id, int feature_set)
{
	int					result = 0;
	AcdbVocProcGainDepVolTblSizeV2CmdType	vocvoltablesize;
	AcdbSizeResponseType			response;
	int	acdb_cmd[2] = {ACDB_CMD_GET_VOC_PROC_GAIN_DEP_VOLTBL_SIZE_V2,
				ACDB_CMD_GET_VOC_PROC_DYNAMIC_TABLE_SIZE};
	bool	per_vocoder_enabled = false;

	vocvoltablesize.nTxDeviceId = txacdb_id;
	vocvoltablesize.nRxDeviceId = rxacdb_id;
	vocvoltablesize.nFeatureId = feature_set;

	per_vocoder_enabled = !!(mem_data[voice_cal_types[0]].version &
					PER_VOCODER_CAL_BIT_MASK);

	LOGD("ACDB -> %s\n", per_vocoder_enabled ?
			"ACDB_CMD_GET_VOC_PROC_DYNAMIC_TABLE_SIZE":
			"ACDB_CMD_GET_VOC_PROC_GAIN_DEP_VOLTBL_SIZE_V2");
	result = acdb_ioctl(acdb_cmd[per_vocoder_enabled],
			(const uint8_t *)&vocvoltablesize, sizeof(vocvoltablesize),
			(uint8_t *)&response, sizeof(response));

	if (result < 0) {
		LOGE("Error: %s Returned = %d\n", per_vocoder_enabled ?
			"ACDB_CMD_GET_VOC_PROC_DYNAMIC_TABLE_SIZE":
			"ACDB_CMD_GET_VOC_PROC_GAIN_DEP_VOLTBL_SIZE_V2",
			result);
		goto done;
	}

	result = response.nSize;
	if (response.nSize == 0) {
		LOGE("Error: %s returned %d bytes\n", per_vocoder_enabled ?
			"ACDB_CMD_GET_VOC_PROC_DYNAMIC_TABLE_SIZE":
			"ACDB_CMD_GET_VOC_PROC_GAIN_DEP_VOLTBL_SIZE_V2",
			response.nSize);
		goto done;
	}

done:
	return result;
}

static int get_voctable_size(int rxacdb_id, int txacdb_id, int rxacdb_id_sample_rate, int txacdb_id_sample_rate)
{
        int                                     result = 0;
        AcdbVocProcCmnTblSizeCmdType            voctablesize;
        AcdbSizeResponseType                    response;
        int     acdb_cmd[2] = {ACDB_CMD_GET_VOC_PROC_COMMON_TABLE_SIZE,
                                ACDB_CMD_GET_VOC_PROC_STATIC_TABLE_SIZE};
        bool    per_vocoder_enabled = false;

        voctablesize.nTxDeviceId = txacdb_id;
        voctablesize.nRxDeviceId = rxacdb_id;
        voctablesize.nTxDeviceSampleRateId = txacdb_id_sample_rate;
        voctablesize.nRxDeviceSampleRateId = rxacdb_id_sample_rate;

        per_vocoder_enabled = !!(mem_data[voice_cal_types[0]].version &
                                        PER_VOCODER_CAL_BIT_MASK);

        LOGD("ACDB -> %s\n", per_vocoder_enabled ?
                        "ACDB_CMD_GET_VOC_PROC_STATIC_TABLE_SIZE":
                        "ACDB_CMD_GET_VOC_PROC_COMMON_TABLE_SIZE");
        result = acdb_ioctl(acdb_cmd[per_vocoder_enabled],
                        (const uint8_t *)&voctablesize, sizeof(voctablesize),
                        (uint8_t *)&response, sizeof(response));

        if (result < 0) {
                LOGE("Error: %s Returned = %d\n", per_vocoder_enabled ?
                        "ACDB_CMD_GET_VOC_PROC_STATIC_TABLE_SIZE":
                        "ACDB_CMD_GET_VOC_PROC_COMMON_TABLE_SIZE",
                        result);
                goto done;
        }

        result = response.nSize;
        if (response.nSize == 0) {
                LOGE("Error: %s returned %d bytes\n", per_vocoder_enabled ?
                        "ACDB_CMD_GET_VOC_PROC_STATIC_TABLE_SIZE":
                        "ACDB_CMD_GET_VOC_PROC_COMMON_TABLE_SIZE",
                        response.nSize);
                goto done;
        }

done:
        return result;
}

static int get_vocstrmtable_size()
{
        int                                     result = 0;
        AcdbSizeResponseType                    response;
        int     acdb_cmd[2] = {ACDB_CMD_GET_VOC_STREAM_COMMON_TABLE_SIZE,
                                ACDB_CMD_GET_VOC_STREAM_STATIC_TABLE_SIZE};
        bool    per_vocoder_enabled = false;

        per_vocoder_enabled = !!(mem_data[voice_cal_types[0]].version &
                                        PER_VOCODER_CAL_BIT_MASK);

        LOGD("ACDB -> %s\n", per_vocoder_enabled ?
                        "ACDB_CMD_GET_VOC_STREAM_STATIC_TABLE_SIZE":
                        "ACDB_CMD_GET_VOC_STREAM_COMMON_TABLE_SIZE");
        result = acdb_ioctl(acdb_cmd[per_vocoder_enabled], NULL, 0,
                             (uint8_t *)&response, sizeof(response));

        if (result < 0) {
                LOGE("Error: %s Returned = %d\n", per_vocoder_enabled ?
                        "ACDB_CMD_GET_VOC_STREAM_STATIC_TABLE_SIZE":
                        "ACDB_CMD_GET_VOC_STREAM_COMMON_TABLE_SIZE",
                        result);
                goto done;
        }

        result = response.nSize;
        if (response.nSize == 0) {
                LOGE("Error: %s returned %d bytes\n", per_vocoder_enabled ?
                        "ACDB_CMD_GET_VOC_STREAM_STATIC_TABLE_SIZE":
                        "ACDB_CMD_GET_VOC_STREAM_COMMON_TABLE_SIZE",
                        response.nSize);
                goto done;
        }

done:
        return result;
}

static int get_audtable_size(int acdb_id, int app_id, int sample_rate)
{
	int					result = 0;
	AcdbAudProcTableSizeCmdType		audtablesize;
	AcdbSizeResponseType			response;

	audtablesize.nDeviceId = acdb_id;
	audtablesize.nApplicationType = app_id;
	audtablesize.nDeviceSampleRateId = sample_rate;

	LOGD("ACDB -> ACDB_CMD_GET_AUDPROC_COMMON_TABLE_SIZE\n");
	result = acdb_ioctl(ACDB_CMD_GET_AUDPROC_COMMON_TABLE_SIZE,
			(const uint8_t *)&audtablesize, sizeof(audtablesize),
			(uint8_t *)&response, sizeof(response));

	if (result < 0) {
		LOGE("Error: ACDB_CMD_GET_AUDPROC_COMMON_TABLE_SIZE Returned = %d\n",
			result);
		goto done;
	}

	result = response.nSize;
	if (response.nSize == 0) {
		LOGE("Error: ACDB_CMD_GET_AUDPROC_COMMON_TABLE_SIZE returned %d bytes\n",
			response.nSize);
		goto done;
	}
done:
	return result;
}

int deregister_vocvoltable(void)
{
	int result = 0;
	struct audio_cal_basic		basic_cal;

	basic_cal.hdr.data_size = sizeof(basic_cal);
	basic_cal.hdr.version = VERSION_0_0;
	basic_cal.hdr.cal_type = CVP_VOCPROC_DYNAMIC_CAL_TYPE;
	basic_cal.hdr.cal_type_size = sizeof(basic_cal.cal_type);
	basic_cal.cal_type.cal_hdr.version = VERSION_0_0;
	basic_cal.cal_type.cal_hdr.buffer_number = BUFF_IDX_0;

	LOGD("ACDB -> AUDIO_DEREGISTER_VOCPROC_VOL_TABLE\n");
	result = ioctl(cal_driver_handle, AUDIO_PREPARE_CALIBRATION, &basic_cal);
	if (result < 0) {
		LOGE("Error: Deregister vocproc vol returned = %d\n",
			result);
		goto done;
	}
done:
	return result;
}

int register_vocvoltable(void)
{
	int result = 0;
	struct audio_cal_basic		basic_cal;

	basic_cal.hdr.data_size = sizeof(basic_cal);
	basic_cal.hdr.version = VERSION_0_0;
	basic_cal.hdr.cal_type = CVP_VOCPROC_DYNAMIC_CAL_TYPE;
	basic_cal.hdr.cal_type_size = sizeof(basic_cal.cal_type);
	basic_cal.cal_type.cal_hdr.version = VERSION_0_0;
	basic_cal.cal_type.cal_hdr.buffer_number = BUFF_IDX_0;

	LOGD("ACDB -> AUDIO_REGISTER_VOCPROC_VOL_TABLE\n");
	result = ioctl(cal_driver_handle, AUDIO_POST_CALIBRATION, &basic_cal);
	if (result < 0) {
		LOGE("Error: Register vocproc vol returned = %d\n",
			result);
		goto done;
	}
done:
	return result;
}

int acdb_loader_reload_vocvoltable(int feature_set)
{
	int result = 0;
	uint32_t txacdb_id;
	uint32_t rxacdb_id;

	pthread_mutex_lock(&loader_mutex);
	txacdb_id = current_voice_tx_acdb_id;
	rxacdb_id = current_voice_rx_acdb_id;
	current_feature_set = feature_set;

	LOGD("ACDB -> acdb_loader_reload_vocvoltable, acdb_rx = %d, acdb_tx = %d, feature_set = %d\n",
		rxacdb_id, txacdb_id, feature_set);

	result = get_vocvoltable_size(rxacdb_id, txacdb_id, feature_set);
	if (result < 0) {
		LOGE("ACDB -> No vocvol table to reload!\n");
		goto done;
	}

	result = deregister_vocvoltable();
	if (result < 0) {
		LOGE("ACDB -> Deregister vocvol table unsuccessful!\n");
		goto done;
	}

	result = send_vocvoltable(rxacdb_id, txacdb_id, feature_set);
	if (result < 0) {
		LOGE("ACDB -> Deregister vocvol table unsuccessful!\n");

		if (feature_set != ACDB_VOCVOL_FID_DEFAULT) {

			LOGE("ACDB -> Resend default vocvol table!\n");
			if (send_vocvoltable(rxacdb_id, txacdb_id, ACDB_VOCVOL_FID_DEFAULT) < 0)
				LOGE("ACDB -> Resend default vocvol unsuccessful!\n");
		}

		/* Even if second attempt to send vol table fails */
		/* Try to re-register. Memory should still contain */
		/* a previous valid volume table */
		LOGE("ACDB -> Reregister default vocvol table!\n");
		if(register_vocvoltable() < 0) {
			LOGE("ACDB -> Reregister default volume unsuccessful!\n");
			goto done;
		}
		LOGE("ACDB -> Resend default volume successful!\n");
		goto done;
	}

	result = register_vocvoltable();
	if (result < 0) {
		LOGE("ACDB -> Register vocvol table unsuccessful!\n");
		goto done;
	}

done:
	pthread_mutex_unlock(&loader_mutex);
	return result;
}

void acdb_loader_deallocate_ACDB(void)
{
	int	result;

        pthread_mutex_lock(&loader_mutex);
        if (--acdb_init_ref_cnt != 0) {
            goto exit;
        }

	is_initialized = false;

	LOGD("ACDB -> deallocate_ADIE\n");
	adie_rtac_exit();
	LOGD("ACDB -> deallocate_ACDB\n");
	acdb_rtac_exit();
	acph_deinit();
	deallocate_memory();
	close(cal_driver_handle);
	LOGD("ACDB -> deallocate_ACDB done!\n");

exit:
        pthread_mutex_unlock(&loader_mutex);
}

int acdb_loader_get_remote_acdb_id(unsigned int native_acdb_id)
{
	int				result;
	AcdbGetRmtCompDevIdCmdType	cmd;
	AcdbGetRmtCompDevIdRspType	response;

	LOGD("ACDB -> acdb_loader_get_remote_acdb_id, acdb_id = %d\n",
		native_acdb_id);

	if (!is_initialized) {
		LOGE("ACDB -> Not correctly initialized!\n");
		result = INVALID_DATA;
		goto done;
	}

	cmd.nNativeDeviceId = native_acdb_id;

	result = acdb_ioctl(ACDB_CMD_GET_COMPATIBLE_REMOTE_DEVICE_ID,
			(const uint8_t *)&cmd, sizeof(cmd),
			(uint8_t *)&response, sizeof(response));
	if (result < 0) {
		LOGE("Error: Remote ACDB ID lookup failed = %d\n",
			result);
		goto done;
	}

	result = response.nRmtDeviceId;
done:
	return result;
}

int process_attribute(char *attr, struct param_data *params)
{
	int ret = 0;

	if (params->get_size) {
		if (strcmp("anc_cal", attr) == 0)
			params->buff_size = ANC_MAX_CODEC_CAL_TABLE_SIZE;
		else
			params->buff_size = DEFAULT_MAX_CAL_TABLE_SIZE;
		goto done;
	}

	if (strcmp("mad_cal", attr) == 0) {
		ret = send_codec_cal(params);
	} else if (strcmp("mbhc_cal", attr) == 0) {
		send_mbhc_data(params);
	} else if (strcmp("anc_cal", attr) == 0) {
		send_wcd9xxx_anc_data(params);
	} else if (strcmp("vbat_cal", attr) == 0) {
		send_vbat_data(params);
	}
done:
	return ret;
}

int acdb_loader_get_calibration(char *attr, int size, void *data)
{
	int ret = 0;
	struct param_data *params = data;

	if (attr == NULL) {
		LOGE("%s: Error: attr is NULL!\n", __func__);
		ret = -EINVAL;
		goto done;
	} else if (size != sizeof(*params)) {
		LOGE("%s: Error: Invalid size %d, expected %d\n",
			__func__, size, sizeof(*params));
		ret = -EINVAL;
		goto done;
	} else if (data == NULL) {
		LOGE("%s: Error: data is NULL!\n", __func__);
		ret = -EINVAL;
		goto done;
	}

	process_attribute(attr, params);
done:
	return ret;
}

int acdb_loader_store_set_audio_cal(acdb_audio_cal_cfg_t *cal, void *data, uint32_t datalen)
{
    int result = 0;
    if(data == NULL || cal == NULL) {
        LOGE("[%s] received null pointer", __func__);
        return -EINVAL;
    }

    if(cal->cal_type == AUDIO_DEVICE_CAL_TYPE) {
        if(cal->module_id && cal->param_id) {
            AcdbAudProcCmdType cmd;
            cmd.nDeviceId = cal->acdb_dev_id;
            cmd.nDeviceSampleRateId = cal->sampling_rate;
            cmd.nApplicationType = cal->app_type;
            cmd.nModuleId = cal->module_id;
            cmd.nParamId = cal->param_id;
            cmd.nBufferLength = datalen;
            cmd.nBufferPointer = (uint8_t *)data;
            result = acdb_ioctl(ACDB_CMD_SET_AUDPROC_COMMON_DATA,
                                (const uint8_t *)&cmd, sizeof(cmd),
                                 NULL,0);
        } else {
            AcdbAudProcTableCmdType cmd;
            cmd.nDeviceId = cal->acdb_dev_id;
            cmd.nDeviceSampleRateId = cal->sampling_rate;
            cmd.nApplicationType = cal->app_type;
            cmd.nBufferLength = datalen;
            cmd.nBufferPointer = (uint8_t *)data;
            result = acdb_ioctl(ACDB_CMD_SET_AUDPROC_COMMON_TABLE,
                                (const uint8_t *)&cmd, sizeof(cmd),
                                 NULL,0);
        }
    } else if(cal->cal_type == AUDIO_STREAM_CAL_TYPE){
        if(cal->module_id && cal->param_id) {
            AcdbAudStrmV2CmdType scmd;
            scmd.nApplicationTypeId = cal->app_type;
            scmd.nModuleId = cal->module_id;
            scmd.nParamId = cal->param_id;
            scmd.nBufferLength = datalen;
            scmd.nBufferPointer = (uint8_t *)data;
            result = acdb_ioctl(ACDB_CMD_SET_AUDPROC_STREAM_DATA_V2,
                                (const uint8_t *)&scmd, sizeof(scmd),
                                 NULL,0);
        } else {
            AcdbAudStrmTableV2CmdType scmd;
            scmd.nApplicationTypeId = cal->app_type;
            scmd.nBufferLength = datalen;
            scmd.nBufferPointer = (uint8_t *)data;
            result = acdb_ioctl(ACDB_CMD_SET_AUDPROC_STREAM_TABLE_V2,
                                (const uint8_t *)&scmd, sizeof(scmd),
                                 NULL,0);
        }
    } else {
        LOGE("[%s] unsupported caltype %d", __func__, cal->cal_type);
        result = -EINVAL;
    }
    if(result != ACDB_SUCCESS) {
        LOGE("[%s] ACDB settting parameters failed %d\n", __func__, result);
    }
    return result;
}

int acdb_loader_adsp_set_audio_cal(acdb_audio_cal_cfg_t *cal, void *data, uint32_t datalen)
{
    ACPH_CMD_RTC_SET_CAL_DATA_req* req;
    uint32_t copp_id[MAX_ACTIVE_COPP+1]={0};
    uint8_t* tptr;
    uint32_t i, req_len, resp_buf_filled;
    uint32_t service_id;
    uint32_t resp_buf_len=64;
    uint8_t resp_buf[resp_buf_len];
    int result = 0;

    if(data == NULL || cal == NULL) {
        LOGE("[%s] received null pointer", __func__);
        return -EINVAL;
    }

    if(cal->cal_type == AUDIO_DEVICE_CAL_TYPE) {
           result = get_audio_copp_id(cal->topo_id, cal->app_type,
                                       cal->acdb_dev_id, copp_id);
           service_id = ADSP_ADM_SERVICE;
    } else if(cal->cal_type == AUDIO_STREAM_CAL_TYPE) {
           result = get_audio_popp_id(cal->topo_id, cal->app_type,
                                       cal->acdb_dev_id, copp_id);
           service_id = ADSP_ASM_SERVICE;
    } else {
           result = -EINVAL;
           LOGE("[%s] unsupported caltype %d", __func__, cal->cal_type);
           goto done;
    }

    if(result < 0) {
           result = -ERR_AUD_CAL_NOCOPP;
           LOGE("[%s] active device/stream not found (result=%d) for topology 0x%x and apptype 0x%x\n",
                   __func__, result, cal->topo_id, cal->app_type);
           goto done;
    }

    req_len = sizeof(ACPH_CMD_RTC_SET_CAL_DATA_req) + datalen;
    req = (ACPH_CMD_RTC_SET_CAL_DATA_req*)malloc(req_len);
    if(NULL == req) {
        LOGE("[%s] failed to allocate memory for length %d", __func__, req_len);
        result = -ENOMEM;
        goto done;
    }
    req->domain_id = APR_DOMAIN_ADSP;
    req->service_id = service_id;
    if(cal->module_id && cal->param_id) {
        req->module_id = cal->module_id;
        req->parameter_id = cal->param_id;
        req->length = datalen;
        req->reserved = 0;
        tptr = (uint8_t*)req;
        tptr += sizeof(ACPH_CMD_RTC_SET_CAL_DATA_req);
    } else {
        tptr = (uint8_t*)&(req->module_id);
    }

    memcpy(tptr, data, datalen);
    for(i=1; i<=copp_id[0]; i++) {
	req->pp_id = copp_id[i];
	result = acdb_rtac_callback(ACPH_CMD_RTC_SET_CAL_DATA, (uint8_t*) req, req_len,
                                 resp_buf, resp_buf_len, &resp_buf_filled);
    }
done:
    if(result < 0) {
            LOGE("[%s] set parameters failed with status %d", __func__, result);
    }
    if(req)
      free(req);
    return result;
}

int acdb_loader_set_audio_cal_v2(void *cfg, void *data, uint32_t datalen)
{
        acdb_audio_cal_cfg_t *cal = (acdb_audio_cal_cfg_t*) cfg;
        int result=0;
        if(cal == NULL) {
                result = -EINVAL;
                LOGE("[%s] null pointer received", __func__);
                goto done;
        }
        if(cal->persist) {
                result = acdb_loader_store_set_audio_cal(cal, data, datalen);
        } else {
                result = acdb_loader_adsp_set_audio_cal(cal, data, datalen);
        }
done:
        ALOGV("[%s] Exiting %d", __func__, result);
        return result;
}

int acdb_loader_store_get_audio_cal(acdb_audio_cal_cfg_t *cal, void *data, uint32_t *datalen)
{
    int result=-1,i;
    AcdbQueryResponseType resp={0};

    if(datalen == NULL || data == NULL || cal == NULL) {
        LOGE("[%s] received null pointer", __func__);
        return -EINVAL;
    }

    if(cal->cal_type == AUDIO_DEVICE_CAL_TYPE) {
        AcdbAudProcCmdType data_cmd;
        AcdbAudProcTableCmdType table_cmd;
        if(cal->module_id && cal->param_id) {
            data_cmd.nDeviceId = cal->acdb_dev_id;
            data_cmd.nDeviceSampleRateId = cal->sampling_rate;
            data_cmd.nApplicationType = cal->app_type;
            data_cmd.nModuleId = cal->module_id;
            data_cmd.nParamId = cal->param_id;
            data_cmd.nBufferLength = *datalen;
            data_cmd.nBufferPointer = (uint8_t*)data;
            result = acdb_ioctl(ACDB_CMD_GET_AUDPROC_COMMON_DATA,
                                (const uint8_t *)&data_cmd, sizeof(data_cmd),
                                (uint8_t*)&resp,sizeof(resp));
        } else {
            table_cmd.nDeviceId = cal->acdb_dev_id;
            table_cmd.nDeviceSampleRateId = cal->sampling_rate;
            table_cmd.nApplicationType = cal->app_type;
            table_cmd.nBufferLength = *datalen;
            table_cmd.nBufferPointer = (uint8_t*)data;
            result = acdb_ioctl(ACDB_CMD_GET_AUDPROC_COMMON_TABLE,
                                (const uint8_t *)&table_cmd, sizeof(table_cmd),
                                (uint8_t*)&resp,sizeof(resp));
        }
    } else if(cal->cal_type == AUDIO_STREAM_CAL_TYPE){
        AcdbAudStrmV2CmdType sdata_cmd;
        AcdbAudStrmTableV2CmdType stable_cmd;

        if(cal->module_id && cal->param_id) {
            sdata_cmd.nApplicationTypeId = cal->app_type;
            sdata_cmd.nModuleId = cal->module_id;
            sdata_cmd.nParamId = cal->param_id;
            sdata_cmd.nBufferLength = *datalen;
            sdata_cmd.nBufferPointer = (uint8_t*)data;
            result = acdb_ioctl(ACDB_CMD_GET_AUDPROC_STREAM_DATA_V2,
                                (const uint8_t *)&sdata_cmd, sizeof(sdata_cmd),
                                (uint8_t*)&resp,sizeof(resp));
        } else {
            stable_cmd.nApplicationTypeId = cal->app_type;
            stable_cmd.nBufferLength = *datalen;
            stable_cmd.nBufferPointer = (uint8_t*)data;
            result = acdb_ioctl(ACDB_CMD_GET_AUDPROC_STREAM_TABLE_V2,
                                (const uint8_t *)&stable_cmd, sizeof(stable_cmd),
                                (uint8_t*)&resp,sizeof(resp));
        }
    } else {
        result = -EINVAL;
    }

    if(ACDB_SUCCESS == result) {
        *datalen = resp.nBytesUsedInBuffer;
    } else {
        *datalen = 0;
        LOGE("[%s] Error: ACDB get returned = %d length %d\n",
               __func__, result, resp.nBytesUsedInBuffer);
        goto done;
    }

done:
    return result;
}

int acdb_loader_adsp_get_audio_cal(acdb_audio_cal_cfg_t *cal, void *data, unsigned int *datalen)
{
        int result=0,i;
        ACPH_CMD_RTC_GET_CAL_DATA_req req_buf;
	uint32_t copp_id[MAX_ACTIVE_COPP+1]={0};
        uint32_t req_buf_len;
        uint32_t resp_buf_len;
        uint16_t *tptr;
        uint32_t resp_buf_filled=0;
        uint8_t *resp_buf;
        uint32_t service_id;

        if(cal == NULL || data == NULL || datalen == NULL) {
                result = -EINVAL;
                LOGE("[%s] null pointer received", __func__);
                goto done;
        }

        resp_buf = (uint8_t*)data;
        resp_buf_len = *datalen;
        if(cal->cal_type == AUDIO_DEVICE_CAL_TYPE) {
                result = get_audio_copp_id(cal->topo_id, cal->app_type,
                                            cal->acdb_dev_id, copp_id);
                service_id = ADSP_ADM_SERVICE;
        } else if(cal->cal_type == AUDIO_STREAM_CAL_TYPE) {
                result = get_audio_popp_id(cal->topo_id, cal->app_type,
                                            cal->acdb_dev_id, copp_id);
                service_id = ADSP_ASM_SERVICE;
        } else {
                result = -EINVAL;
                LOGE("[%s] unsupported caltype %d", __func__, cal->cal_type);
                goto done;
        }

        if(result < 0) {
                result = -ERR_AUD_CAL_NOCOPP;
                LOGE("[%s] active device/stream not found (result=%d) for topology 0x%x and apptype 0x%x\n",
                      __func__, result, cal->topo_id, cal->app_type);
                goto done;
        }

        req_buf.domain_id = APR_DOMAIN_ADSP;
        req_buf.pp_id = copp_id[1];
        req_buf.service_id = service_id;
        if(cal->module_id && cal->param_id) {
                req_buf.module_id = cal->module_id;
                req_buf.parameter_id = cal->param_id;
                result = acdb_rtac_callback(ACPH_CMD_RTC_GET_CAL_DATA, (uint8_t*)&req_buf, req_buf_len,
                                            resp_buf, resp_buf_len, &resp_buf_filled);

                *datalen = resp_buf_filled;
        } else {
                *datalen = 0;
                result = -EINVAL;
                LOGE("[%s] module id (0x%x) and param id(0x%x) invalid",
                       __func__, cal->module_id, cal->param_id);
        }

done:
        return result;
}

int acdb_loader_get_audio_cal_v2(void *cfg, void *data, unsigned int *datalen)
{
        int result=0;
        acdb_audio_cal_cfg_t *cal = (acdb_audio_cal_cfg_t*)cfg;

        if(cal == NULL) {
                LOGE("[%s] received null pointer", __func__);
                return -EINVAL;
        }
        if(cal->persist) {
                result = acdb_loader_store_get_audio_cal(cal, data, datalen);
        } else {
                result = acdb_loader_adsp_get_audio_cal(cal, data, datalen);
        }
        ALOGV("[%s] Exiting %d", __func__, result);
        return result;
}
