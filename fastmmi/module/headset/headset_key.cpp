/*
 * Copyright (c) 2014, Qualcomm Technologies, Inc. All Rights Reserved.
 * Qualcomm Technologies Proprietary and Confidential.
 */
 
 /* 修改说明 ------ 耳机插拔检测功能
 * SCx0的耳机插拔 产线测试需求与原有的高通功能几乎完全不一样，所以，重写该文件。
 * 2017-06-09 ellis.zhao
 * ps:need modify headset driver in kernel,which is not same as msm8909
 */
 
#include "mmi_module.h"

#include <string.h>
#include <dirent.h>

#define HEADSET_PATH "/sys/devices/soc/"
#define HS_PLUG 49
#define HS_OUT  48

static const mmi_module_t * mymodule = NULL;

static int headset_test()
{
	DIR *dir;
	DIR *temp_dir;
	struct dirent *ptr;
	
	char base[128];
	memset(base,'\0',sizeof(base));
	strcpy(base,HEADSET_PATH);
	char buf[10];
	memset(buf,'\0',sizeof(buf));
	
	if ((dir=opendir(base)) == NULL)
	{
		return FAILED;
	}
	
	while ((ptr=readdir(dir)) != NULL)
	{
		if(strstr(ptr->d_name ,"msm8x16_wcd_codec"))
		{
			strcat(base,ptr->d_name);
			if ((temp_dir=opendir(base)) == NULL)
			{
				closedir(dir);
				return FAILED;
			}
			
			while((ptr=readdir(temp_dir)) != NULL)
			{
				if(strstr(ptr->d_name ,"headset"))
				{
					strcat(base,"/");
					strcat(base,ptr->d_name);
					if(read_file(base, buf, 2)!= 0)
					{
						closedir(temp_dir);
						closedir(dir);
						return FAILED;
					}

					closedir(temp_dir);
					closedir(dir);
					if(buf[0] == HS_OUT)
					{
						return FAILED;
					}
					else
					{
						return HS_PLUG;
					}
				}
			}
		}
	}
	closedir(temp_dir);
	closedir(dir);
	return FAILED;
}

/**
* Defined case run in mmi mode,this mode support UI.
* @return, 0 -success; -1
*/
static int32_t module_run_mmi(const mmi_module_t * module, unordered_map < string, string > &params) {
    int ret = -1;
    char buf[256] = {0};
	
    if(module == NULL) {
        ALOGE("%s NULL point  received ", __FUNCTION__);
        return FAILED;
    }
    
	ret = headset_test();
	if(ret == HS_PLUG)
	{
		module->cb_print(NULL, SUBCMD_MMI, "Headset is plug !!", sizeof("Headset is plug !!"), PRINT_DATA);
        return SUCCESS;
	}
	else
	{
		module->cb_print(NULL, SUBCMD_MMI, "Headset is out !!", sizeof("Headset is out !!"), PRINT_DATA);
		return FAILED;
	}
}

/**
* Defined case run in PCBA mode, fully automatically.
*
*/
static int32_t module_run_pcba(const mmi_module_t * module, unordered_map < string, string > &params) {
    int ret = -1;
	
	ALOGI("%s start:%s", __FUNCTION__);
    if(module == NULL) {
        ALOGE("%s NULL point  received ", __FUNCTION__);
        return FAILED;
    }
	
	ret = headset_test();
	if(ret == HS_PLUG)
        return SUCCESS;
	else
		return FAILED;
}

static int32_t module_init(const mmi_module_t * module, unordered_map < string, string > &params) {
	mymodule = module;
	char buf[256] = {0};
	
    if(module == NULL) {
        return FAILED;
    }
	
    return SUCCESS;
}

static int32_t module_deinit(const mmi_module_t * module) {
    ALOGI("%s start.", __FUNCTION__);
    if(module == NULL) {
        ALOGE("%s NULL point  received ", __FUNCTION__);
        return FAILED;
    }
    return SUCCESS;
}

static int32_t module_stop(const mmi_module_t * module) {
    ALOGI("%s start.", __FUNCTION__);
    if(module == NULL) {
        ALOGE("%s NULL point  received ", __FUNCTION__);
        return FAILED;
    }
    return SUCCESS;
}

/**
* Before call Run function, caller should call module_init first to initialize the module.
* the "cmd" passd in MUST be defined in cmd_list ,mmi_agent will validate the cmd before run.
* 
*/
static int32_t module_run(const mmi_module_t * module, const char *cmd, unordered_map < string, string > &params) {
    ALOGI("%s start", __FUNCTION__);
    int ret = -1;

    if(!module || !cmd) {
        ALOGE("%s NULL point  received ", __FUNCTION__);
        return FAILED;
    }

    if(!strcmp(cmd, SUBCMD_MMI))
        ret = module_run_mmi(module, params);
    else if(!strcmp(cmd, SUBCMD_PCBA))
        ret = module_run_pcba(module, params);
    else {
        ALOGE("%s Invalid command: %s  received ", __FUNCTION__, cmd);
        ret = FAILED;
    }

   /** Default RUN mmi*/
    return ret;
}

/**
* Methods must be implemented by module. 
*/
static struct mmi_module_methods_t module_methods = {
    .module_init = module_init,
    .module_deinit = module_deinit,
    .module_run = module_run,
    .module_stop = module_stop,
};

/**
* Every mmi module must have a data structure named MMI_MODULE_INFO_SYM
* and the fields of this data structure must be initialize in strictly sequence as definition,
* please don't change the sequence as g++ not supported in CPP file.
*/
mmi_module_t MMI_MODULE_INFO_SYM = {
    .version_major = 1,
    .version_minor = 0,
    .name = "Headset",
    .author = "Qualcomm Technologies, Inc.",
    .methods = &module_methods,
    .module_handle = NULL,
    .supported_cmd_list = NULL,
    .supported_cmd_list_size = 0,
    .cb_print = NULL, /**it is initialized by mmi agent*/
    .run_pid = -1,
};
