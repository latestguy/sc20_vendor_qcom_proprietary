/*!
  @file
  sendcmd.cpp

  @brief
  Places a Remote Procedure Call (RPC) to Android's AtCmdFwd Service

*/

/*===========================================================================

Copyright (c) 2015, Qualcomm Technologies, Inc.
All Rights Reserved.
Confidential and Proprietary - Qualcomm Technologies, Inc.

===========================================================================*/

/*===========================================================================

                        EDIT HISTORY FOR MODULE

This section contains comments describing changes made to the module.
Notice that changes are listed in reverse chronological order.


when       who     what, where, why
--------   ---     ----------------------------------------------------------
04/11/11   jaimel   First cut.


===========================================================================*/


/*===========================================================================

                           INCLUDE FILES

===========================================================================*/

#define LOG_NDEBUG 0
#define LOG_NIDEBUG 0
#define LOG_NDDEBUG 0
#define LOG_TAG "Atfwd_Sendcmd"
#include <utils/Log.h>
#include "common_log.h"
#include <cutils/properties.h>
#include "IAtCmdFwdService.h"
#include <binder/BpBinder.h>
#include <binder/IServiceManager.h>
#include <stdio.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <time.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <dirent.h>
#include "sendcmd.h"
#include "Test.h"
#define MAX_KEYS 57

#include <binder/IPCThreadState.h>
#include <binder/ProcessState.h>

namespace android {

/*===========================================================================

                           Global Variables

===========================================================================*/

sp<IAtCmdFwdService> gAtCmdFwdService; //At Command forwarding sevice object
sp<DeathNotifier> mDeathNotifier;

/*===========================================================================

                          Extern functions invoked from CKPD daemon

===========================================================================*/

extern "C" int initializeAtFwdService();
extern "C" int pressit(char key, int keyPressTime, int timeBetweenKeyPresses);
extern "C" void millisecondSleep(int milliseconds);

/*===========================================================================
  FUNCTION  initializeAtFwdService
===========================================================================*/
/*!
@brief
     Initializes the connection with the Window Manager service
@return
  Returns 0 if service intialization was successful; -1 otherwise

@note
  None.
*/
/*=========================================================================*/

extern "C" int initializeAtFwdService()
{
    sp<IServiceManager> sm = defaultServiceManager();
    sp<IBinder> binder;
    int retryCnt = 1;
    if(sm == 0) {
        LOGE("Could not obtain IServiceManager \n");
        return -1;
    }

    do {
        binder = sm->getService(String16("AtCmdFwd"));
        if (binder == 0) {
            LOGW("AtCmdFwd service not published, waiting... retryCnt : %d", retryCnt);
            /*
             * Retry after (retryCnt * 5)s and yield in the cases when AtCmdFwd service is
             * is about to be published
             */
            sleep(retryCnt * ATFWD_RETRY_DELAY);
            ++retryCnt;
            continue;
        }

        break;
    } while(retryCnt <= ATFWD_MAX_RETRY_ATTEMPTS);

    if (binder == 0) {
        LOGI("AtCmdFwd service not ready - Exhausted retry attempts - :%d",retryCnt);
        //property_set("ctl.stop", "atfwd");
        return -1;
    }
    if (mDeathNotifier == NULL) {
        mDeathNotifier = new DeathNotifier();
    }
    binder->linkToDeath(mDeathNotifier);

    gAtCmdFwdService = interface_cast<IAtCmdFwdService>(binder);
    if (gAtCmdFwdService == 0)
    {
        LOGE("Could not obtain AtCmdFwd service\n");
        return -1;
    }

    // Start a Binder thread pool to receive Death notification callbacks
    sp<ProcessState> proc(ProcessState::self());
    ProcessState::self()->startThreadPool();
    return 0;
}

#define QUECTEL_QGMR_CMD
#ifdef QUECTEL_QGMR_CMD
#define ATFWD_DATA_PROP_QUEC_VER            "ro.build.quectelversion.release"
#define ATFWD_DATA_PROP_ANDROID_VER			 "ro.build.version.release"
#define QGMR_RESP_BUF_SIZE (380*2) // RESP_BUF_SIZE msut less than the QMI_ATCOP_AT_RESP_MAX_LEN in the file vendor/qcom/proprietary/qmi/inc/qmi_atcop_srvc.h,
               // Maybe you should change QMI_ATCOP_AT_RESP_MAX_LEN for your requirement

extern "C" void quec_qgmr_handle( const AtCmd *cmd ,AtCmdResponse *response)
{
	int ret=0;
	char target[PROPERTY_VALUE_MAX] = {0}; // Stores target info      
	char *resp_buf=NULL;
	char *ptr=NULL;
	if(NULL == resp_buf)
	{
		resp_buf = (char *)malloc(QGMR_RESP_BUF_SIZE);
		if(resp_buf == NULL)
		{
         LOGE("%s:%d No Memory\n", __func__, __LINE__);
         return; // error
		}
		memset(resp_buf, 0, QGMR_RESP_BUF_SIZE);
     }

	ret = property_get(ATFWD_DATA_PROP_ANDROID_VER, target, "");
	LOGI("qgmr_modem:%s",cmd->tokens[0]);
	LOGI("qgmr_andorid:%s",target);
	sprintf(resp_buf,"Quectel\nSC20\nRevision: ");
	if(cmd->ntokens==2)
	sprintf(resp_buf,"%s%s%s_Android%s",resp_buf, cmd->tokens[0],cmd->tokens[1],target);
	else
	sprintf(resp_buf,"%s%s_Android%s",resp_buf, cmd->tokens[0],target);
	memset(target,0,64);
	ret = property_get(ATFWD_DATA_PROP_QUEC_VER, target, "");
	LOGI("qgmr_quectel:%s",target);
	// remove V01
	if((ptr=strchr(target,'V'))!=NULL)
	{
		*ptr='\0';  // 'V' -> '\0'
	}
	sprintf(resp_buf,"%s.%s",resp_buf,target);
	if((response->response = resp_buf) == NULL )
	 {
		response->result = 0; // error
	    LOGE("QGMR_AT error");
	 }
     response->result = 1;
	 
	 return;
}
#endif

#if defined QUECTEL_AT_QAPSUB_FEATURE
#define QUECTEL_ANDROID_VERSION            "ro.build.quectelversion.release"
#define RESP_BUF_SIZE 390


extern "C" void quec_qapsub_handle(AtCmdResponse *response)
{
	int ret = 0;
	char target[PROPERTY_VALUE_MAX] = {0};
	char *resp_buf=NULL;
	char *ptr=NULL;
	//char apsub[8] = {0};
	if(NULL == resp_buf)
	{
		resp_buf = (char *)malloc(QGMR_RESP_BUF_SIZE);
		if(resp_buf == NULL)
		{
         LOGE("%s:%d No Memory.\n", __func__, __LINE__);
         return; // error
		}
		memset(resp_buf, 0, RESP_BUF_SIZE);
     }
	ret = property_get(QUECTEL_ANDROID_VERSION, target, ""); //sample: 01.001V01
	LOGI("andorid version:%s",target);
	if(ret<=0)
	{
		free(resp_buf);
		LOGE("%s:%d Get property error.\n", __func__, __LINE__);
		return; // get property of android version error.
	}
	// get sub version from android version, 01.001V01 -> V01
	if((ptr=strchr(target,'V'))==NULL)
	{
		free(resp_buf);
		LOGE("%s:%d Get apsubversion error.\n", __func__, __LINE__);
		return; // get property of android version error.
	}
	//strncpy(resp_buf, ptr, RESP_BUF_SIZE);
	snprintf(resp_buf, RESP_BUF_SIZE, "APSubEdition: %s",ptr);
	response->response = resp_buf;
	LOGI("resp buf:%s",resp_buf);
	response->result = 1;
	return;
}
#endif // QUECTEL_AT_QAPSUB_FEATURE


/*
*
* add by Ben, 2017.05.24
* return the FCT test results for AT Command(AT+QFCT?)
* 
*/
#define QUECTEL_FCT_TEST  
#ifdef QUECTEL_FCT_TEST

#define ARRARY_SIZE(a) (sizeof(a)/sizeof(a[0]))
#define RESP_BUF_SIZE 390
/*
typedef struct{
	char *name; // item name
	int result; // -1: fail  0: none  1: success
}fct_item_type;
*/
fct_item_type fct_items_all[]=
//fct_item_type fct_items[]=
{
//	{"SECOND LCD", 0},
	{"FLASHLIGHT",0},
	{"KEY", 0},
	{"VIBRATOR",0},
	{"HANDSET PLAY", 0},
	{"HEADSET LOOP", 0},
	{"SPEAKER LOOP", 0},
	{"CAMERA MAIN", 0},
	{"CAMERA FRONT",0},
	{"LIGHT SENSOR",0},
	{"SDCARD", 0},
	{"STORAGE", 0},
	{"SIMCARD1",0},
	{"SIMCARD2",0},
	{"WIFI", 0},
	{"BLUETOOTH", 0},
	{"GPS",0},
};

extern "C" char* set_response_buf(fct_item_type *fct_items, int num)
{
	int i,offset=0;
	int test_num=0, success_num=0;
	char *resp_buf=NULL;
	if(NULL == resp_buf)
	{
		resp_buf = (char *)malloc(RESP_BUF_SIZE);
		if(resp_buf == NULL)
		{
			LOGI("%s:%d No Memory\n", __func__, __LINE__);
			return resp_buf; // error
		}
		memset(resp_buf, 0, RESP_BUF_SIZE);
	}
	for(i=0; i<num; i++)
	{
		if(fct_items[i].result !=0 )
			test_num ++;
		if(fct_items[i].result == 1)
			success_num ++;
	}
	offset += snprintf(resp_buf+offset, (RESP_BUF_SIZE-offset), "+QFCT: %d,%d,%d",(success_num==num)?1:0, num, test_num );
	if((success_num==num) || (test_num == 0)){
		LOGI("success_num:%d test_num:%d,(test num is 0 and test num is equal to success num can see this log)",success_num,test_num);
		return resp_buf; // all fct items pass or not test
	}
	LOGI("start to show fail items in com port (success_num:%d test_num:%d)",success_num,test_num);
	offset += snprintf(resp_buf+offset, (RESP_BUF_SIZE-offset), "\r\n");
	for(i=0; i<num; i++)
	{
		offset += snprintf(resp_buf+offset, (RESP_BUF_SIZE-offset), "+QFCT: %s,", fct_items[i].name);
		switch(fct_items[i].result)
		{
			case -1:
				offset += snprintf(resp_buf+offset, (-offset), "fail");
				break;
			case 0:
				offset += snprintf(resp_buf+offset, (RESP_BUF_SIZE-offset), "null");
				break;
				
			case 1:
				offset += snprintf(resp_buf+offset, (RESP_BUF_SIZE-offset), "pass");
				break;
		}
		if(i<num-1)
		{
			offset += snprintf(resp_buf+offset, (RESP_BUF_SIZE-offset), "\r\n");
		}
		
	}
	LOGI("%s:%d: RESP_BUF_SIZE:%d offset:%d \n", __FILE__, __LINE__,RESP_BUF_SIZE, offset);
	//printf("%s:%d: RESP_BUF_SIZE:%d offset:%d \n", __FILE__, __LINE__,RESP_BUF_SIZE, offset);

	return resp_buf;
}

extern "C" void quec_qfct_handle(AtCmdResponse *response)
{
	#define FCT_RESULT_FILE	 "/cache/FTM_AP/mmi.res"
	//char *fct_items[] = {"FLASHLIGHT","KEY","HEADSET","VIBRATOR","AUDIO_LOUDSPEAKER","CAMERA_BACK","CAMERA_FRONT","LSENSOR","SDCARD","SIMCARD1","WIFI"};
	int total_items;
	char line_text[64] = {0};
	FILE *fp = NULL;
	int  i,offset=-1;
	
	char _ress[1024] = {0,};
	char *p = NULL;
	const char *delim = ",";
	int in = 0;
	int res = -1;
	int retry = 0;
	int w_ce_ds_ss = -1;
	int lcd_num = 1;
	int android_ver = 1;
	const char *_path = NULL;
	LOGI("Client Parse RES");
	
    sp < IBinder > binder = NULL;
    sp < IServiceManager > sm = defaultServiceManager();
    binder = sm->getService(String16("service.quecservice"));
    while(retry<10){
    	if (binder == NULL) {
            LOGE("getService failed retry-%d",retry);
            retry++;
            sleep(1);
            binder = sm->getService(String16("service.quecservice"));
            continue;
    	}
		LOGI("getService success");
		break;
    }

    if(retry == 10){
		LOGE("getService failed");
		res = ARRARY_SIZE(fct_items_all);
    }else{
		sp<IQuecService> cs = interface_cast < IQuecService > (binder);
		//first of all, check sim card num
		w_ce_ds_ss = cs->_parse_sim();
		LOGI("Client Step 1: Check W CE and SIM Num is %d",w_ce_ds_ss);
		if(w_ce_ds_ss < 0){
			LOGE("Client Parse SIM FAILED, Use default SIM Parametes");
			w_ce_ds_ss = 2;
		}

		lcd_num = cs->_check_lcd();
		LOGI("Client Step 2: Check Current Target LCD Num is %d",lcd_num);
		
		if(cs == NULL){
			LOGI("Client is NULL,reinit");
			cs = interface_cast < IQuecService > (binder);
		}else{
			LOGI("Client Not NULL");
		}

		android_ver = cs->_android_version();
		LOGI("Client Step 3: Check Android Version is %d",android_ver);
		res = cs->_parse_res(_ress);
		if(res <= 0){
			LOGE("Service Ready,but Client Parse RES FAILED, Use default RES Parametes");
			retry = 10;
			res = ARRARY_SIZE(fct_items_all);
		}else{
			if(android_ver == 5 || android_ver == 6){
				res = res - 0;
			} else if((android_ver == 7) && (lcd_num == 1)){
				res = res - 1;
			} else if((android_ver == 7) && (lcd_num == 2)){
				res = res - 0;
			} 
			if(w_ce_ds_ss == 0){
				if(strstr(_ress,"GPS") != NULL){
					res = res - 3;
				}else{
					res = res - 2;
				}
			}else if(w_ce_ds_ss == 1){
				res = res - w_ce_ds_ss;
			}
			LOGW("Parse RES has [%d] items:[%s]",res,_ress);
		}
		int _check_res = -1;
		_check_res = cs->_check_res();
	
		if(_check_res == 1){
			_path = "/cache/FTM_AP/mmi-auto.res";
		}else{
			_path = "/cache/FTM_AP/mmi.res";
		}
		LOGI("Check RES is [%d],res path is [%s]",_check_res, _path);
	}  
	fct_item_type fct_items[res];
	char t[res][20];
	if(retry == 10){
		LOGE("Client Parse RES FAILED, Use default RES Parametes,retry is 10");
		for(i=0; i<res; i++) {
			fct_items[i].name = fct_items_all[i].name;
			fct_items[i].result = 0;
	    }
	}else{
		p = strtok(_ress, delim);
		while(p != NULL){
			//printf("split character is :%s\n",p);
			if(!strcmp(p,"SECOND LCD")){
				if(lcd_num == 1){
					p = strtok(NULL,delim);
				}
			}

			if(w_ce_ds_ss == 0 && (!strcmp(p,"SIMCARD1")||!strcmp(p,"SIMCARD2")||!strcmp(p,"GPS"))){
				p = strtok(NULL,delim);
				continue;
			}
			if(!strcmp(p,"SIMCARD2") && w_ce_ds_ss == 1){
				p = strtok(NULL,delim);
				continue;
			}
			strlcpy(t[in],p,sizeof(t[in]));
			p = strtok(NULL,delim);
			
			fct_items[in].name = t[in];
			fct_items[in].result = 0;
			LOGW("result character is [%d]:%s",in,fct_items[in].name);
			in++;
		}
	}
		
	total_items = ARRARY_SIZE(fct_items);
	LOGI("result total_items length is [%d]",total_items);
	for(i=0; i<total_items; i++)
	{
		fct_items[i].result = 0;
	}
	if((fp=fopen(_path, "r")) == NULL)
	{
		
		if((response->response = set_response_buf(fct_items, total_items)) == NULL )
		{
			response->result = 0; // error
			LOGI("%s:%d open file %s failed!\n", __func__, __LINE__, _path);
		}
		response->result = 1;
		return;
	}
	// get line from file
	while(fgets(line_text, 64, fp)!=NULL)	
	{
		if(strchr(line_text, '[') && strchr(line_text, ']')) // [name]
		{
			offset = -1;
			for(i=0; i<total_items; i++)
			{
				if(strstr(line_text, fct_items[i].name))
				{
					offset = i;
					break;
				}
			}
			
		}
		else if(strstr(line_text, "Result"))  // item result
		{
			if(offset>=0)
			{
				if(strstr(line_text, "pass"))
					fct_items[offset].result = 1;
				else if(strstr(line_text, "fail"))
					fct_items[offset].result = -1;
			}
		}
	}
	if(fp)
		fclose(fp);
	
	if((response->response = set_response_buf(fct_items, ARRARY_SIZE(fct_items))) == NULL)
	{
		response->result = 0;
		return;
	}
	response->result = 1; // success
	return;
	
}

#endif

/*===========================================================================
  FUNCTION  sendit
===========================================================================*/
/*!
@brief
     Invokes a Remote Procedure Call (RPC) to Android's Window Manager Service
     Window Manager service returns 0 if the call is successful
@return
  Returns 1 if the key press operation was successful; 0 otherwise

@note
  None.
*/
/*=========================================================================*/

extern "C" AtCmdResponse *sendit(const AtCmd *cmd)
{

	AtCmdResponse *result = NULL;
	result = new AtCmdResponse;
	result->response = NULL;
		if(strcasecmp(cmd->name, "+QFCT")==0){
		LOGI("ATFWD AtCmdFwd QFCT");
		if(NULL != cmd->tokens) {
			LOGI("ATFWD AtCmdFwd Tokens Not NULL ntokens=%d",cmd->ntokens);
			if(cmd->ntokens == 0 || cmd->tokens[0] == NULL){
				LOGI("ATFWD AtCmdFwd Tokens[0] is NULL");
				quec_qfct_handle(result);
			}else if(0 == strncmp("wifi-kill",cmd->tokens[0],strlen("wifi-kill"))){
			//	char *args[5] = { PTT_SOCKET_BIN, "-f", "-d", "-v", NULL };
				LOGI("ATFWD AtCmdFwd:%s",cmd->tokens[0]);
				property_set("wifi.ptt_socket_app", "false");
				property_set("wifi.p_socket_app", "true");
			}else if(0 == strncmp("wifi-start",cmd->tokens[0],strlen("wifi-start"))){
				LOGI("ATFWD AtCmdFwd:%s",cmd->tokens[0]);
			//	char *args[5] = { PTT_SOCKET_BIN, "-v", "-d", "-f", NULL };
				//do_handle(result,args,true);
				property_set("wifi.ptt_socket_app", "false");
				property_set("wifi.p_socket_app", "true");
				result->result = 1; // success
			}else if(0 == strncmp("wifi-end",cmd->tokens[0],strlen("wifi-end"))){
				LOGI("ATFWD AtCmdFwd:%s",cmd->tokens[0]);
			//	char *args[5] = { PTT_SOCKET_BIN, "-f", "-d", "-v", NULL };
				//do_handle(result,args,false);
				property_set("wifi.p_socket_app", "false");
				property_set("wifi.ptt_socket_app", "true");
				result->result = 1; // success
			}else if(0 == strncmp("ble-start",cmd->tokens[0],strlen("ble-start"))){
			//	const char *args[3] = { FTMDAEMON_BIN, "-n", NULL };
				LOGE("ATFWD AtCmdFwd:%s",cmd->tokens[0]);
				property_set("bt.start", "true");
				result->result = 1; // success
			}else if(0 == strncmp("ble-end",cmd->tokens[0],strlen("ble-end"))){
			//	const char *args[3] = { FTMDAEMON_BIN, "-n", NULL };
				LOGE("ATFWD AtCmdFwd:%s",cmd->tokens[0]);
				property_set("bt.start", "false");
				result->result = 1; // success
			}else{
				LOGI("ATFWD AtCmdFwd Default Handle");
				quec_qfct_handle(result);
			}
		}else{
			LOGI("ATFWD AtCmdFwd Tokens is NULL");
			quec_qfct_handle(result);		
		}
	}else if(strcasecmp(cmd->name, "+QGMR")==0)
	{
		quec_qgmr_handle(cmd,result);
	}
#if defined QUECTEL_AT_QAPSUB_FEATURE
	else if( strcasecmp(cmd->name, "+QAPSUB")==0 )
	{
		quec_qapsub_handle(result);
	}
#endif

    return result;
}

void DeathNotifier::binderDied(const wp<IBinder>& who) {
    LOGI("AtCmdFwd : binderDied");
    initializeAtFwdService();
	if(who == NULL){
		LOGI("AtCmdFwd : binderDied NULL");
	}
}

};  /* namespace android */
