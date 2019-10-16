/*
 * Copyright (c) 2014-2016, Qualcomm Technologies, Inc.
 * All Rights Reserved.
 * Confidential and Proprietary - Qualcomm Technologies, Inc.
 */
#include "common.h"
#include "func_map.h"
#include "mmi.h"
#include "utils.h"
#include "module.h"
#include "lang.h"
#include "ate_rtest.h"

static sem_t g_sem_confirm;
static sem_t g_sem_mod_complete;
static bool is_ok = true;
static reboot_opt_t reboot_opt = REBOOT_NONE;

void switch_module(void *m) {

    module_info *mod = (module_info *) m;
    layout *lay = NULL;

    if(mod == NULL || mod->config_list[KEY_LAYOUT].empty()) {
        MMI_ALOGE("Invalid parameter");
        return;
    }
    lay = get_layout(mod);
    if(lay == NULL) {
        MMI_ALOGE("Not find layout for module:[%s]", mod->module);
        return;
    }
    MMI_ALOGI("get cur layout(%s) for module:[%s]", mod->config_list[KEY_LAYOUT].c_str(), mod->module);

    lay = switch_cur_layout_locked(lay, mod);

    if(!strcmp(MAIN_MODULE, mod->module)) {
        update_main_status();
#ifdef ANDROID
    } else {
        initial_screen(mod);
        module_exec_ui(mod);
#endif
    }
    invalidate();
}

static void do_page_up(void *m) {
		MMI_ALOGI("[%s]:Page up Ready to Switch Tester,status is %d(1 is factory)",__FUNCTION__,is_factory_tab);
    if(is_factory_tab){
    	if(reconfig("/cache/FTM_AP/mmi.cfg") <= 0){
            MMI_ALOGI("[%s]:From Factory to Tester Failed",__FUNCTION__);
        }else{
	    	is_factory_tab = false;
            MMI_ALOGI("[%s]:From Factory to Tester Success",__FUNCTION__);
		}
    }else{
        ALOGI("[%s]:In Tester Status,Needn't Switch",__FUNCTION__);
    }
    layout *curlay = acquire_cur_layout();

    if(curlay->m_listview != NULL) {
        curlay->m_listview->page_up();
    }
    invalidate();
    release_cur_layout();
}

static void do_page_down(void *m) {
    layout *curlay = acquire_cur_layout();

    if(curlay->m_listview != NULL) {
        curlay->m_listview->page_down();
    }
    invalidate();
    release_cur_layout();
}

static void do_show_fail(void *m) {
    layout *curlay = acquire_cur_layout();
    button *btn = NULL;
    button *btn_run_all = NULL;
    button *btn_failed = NULL;
    update_main_status();

    if(curlay != NULL && curlay->m_listview != NULL) {
        MMI_ALOGI("show only fail cases");
        curlay->m_listview->set_show_fail(1);
	res_tab = true;

        btn = curlay->find_button_by_name(KEY_MAIN_ALL);
        if(btn != NULL)
            btn->set_color(0x000000ff);

        btn_run_all = curlay->find_button_by_name(KEY_MAIN_RUNALL);
	    btn_failed = curlay->find_button_by_name(KEY_MAIN_FAILED);
        if(btn_run_all != NULL) {
           if(btn_run_all->get_disabled()) {
	      if(btn_failed != NULL){
	         btn_failed ->set_color(0x0000ffff);
	         btn_failed ->set_text("TESTING......");
	      }
           } else {             
	        /**if one module test is FAILED,btn_failed show FAILED in the label
                 ***if all moduel test is SUCCESS,btn_failed show SUCCESS in the label
                 */
	      if(btn_failed != NULL){
	         list < item_t * >*items = curlay->m_listview->get_items();
		 list < item_t * >::iterator iter;
		 bool test_result = false;
		 for(iter = items->begin(); iter != items->end(); iter++) {
		     item_t *obj = (item_t *) (*iter);
		     if(obj != NULL && obj->mod != NULL && (obj->mod->result == FAILED)) {
		         test_result = true;       	   
		         break;
		     }
		 }
		 if(test_result) {
		     btn_failed->set_color(0xff0000ff);
		     btn_failed->set_text("TEST FAILED");
		 } else {
		     btn_failed->set_color(0x00ff00ff);
		     btn_failed->set_text("TEST PASS");
		 }
				    
		 if(btn_failed->get_disabled()) {
		     btn_failed->set_disabled(true);
		 }
	     }
           }//not test end
        }//run all btn is not NULL
    } else {
        MMI_ALOGE("Not find layout or not find listview in layout");
    }
    invalidate();
    release_cur_layout();
}

static void do_show_all(void *m) {
    layout *curlay = acquire_cur_layout();
    button *btn = NULL;

    if(curlay->m_listview != NULL) {
        MMI_ALOGI("show all cases");
        curlay->m_listview->set_show_fail(0);
        res_tab = false;
        btn = curlay->find_button_by_name(KEY_MAIN_ALL);
        if(btn != NULL) {
            btn->set_color(0x666666ff);
            if(btn->get_disabled()) {
               btn->set_disabled(true);
            }
        }
        btn = curlay->find_button_by_name(KEY_MAIN_FAILED);
        if(btn != NULL) {
            btn->set_color(0x000000ff);
            btn->set_text("TEST RESULT");
        }
    } else {
        MMI_ALOGE("Not find listview in layout");
    }
    invalidate();
    release_cur_layout();
}

void process_exit(void *m) {

    if(m == NULL)
        return;

    module_info *mod = (module_info *) m;
    mod->running_state = MODULE_IDLE;
    flush_result();
    module_cleanup(mod);
    ALOGI("[%s]  Test finished with result =%d ", mod->module, mod->result);
    launch_main();
    usleep(100);
    sem_post(&g_sem_mod_complete);
}

void process_exit(void *m, int result) {

    if(m == NULL) {
        MMI_ALOGE("Invalid parameter");
        return;
    }

    module_info *mod = (module_info *) m;

    time(&mod->last_time);
    mod->duration = difftime(mod->last_time, mod->start_time);
    mod->result = result;
    MMI_ALOGI("[%s] Test finished with result=%s, test duration=%f seconds",
              mod->module, MMI_TEST_RESULT(result), mod->duration);
    process_exit(m);
}
static void do_pass(void *m) {
    process_exit(m, SUCCESS);
}

static void do_fail(void *m) {
    sem_post(&g_sem_confirm);
    process_exit(m, FAILED);
}

static void do_exit(void *m) {
    sem_post(&g_sem_confirm);
    launch_main();
}

static bool pop_confirm(void *m, char *lay_name, const char *note) {

    if(m == NULL || lay_name == NULL) {
        MMI_ALOGE("Invalid parammeter");
        return false;
    }
    sem_init(&g_sem_confirm, 0, 0);
    module_info *mod = (module_info *) m;

    layout *lay = get_layout(lay_name);

    if(lay == NULL) {
        MMI_ALOGE("Not find layout(%s) for module:[%s]", lay_name, mod->module);
        return false;
    }

    //restore reboot.xml btn color,the color may be changed by last operation
    if(!strcmp(lay_name, LAYOUT_REBOOT)) {
       int color_org = 0x7D7D7Dff;
       button *btn_poweroff = lay->find_button_by_name(KEY_REBOOT_POWEROFF);
       button *btn_ffbm = lay->find_button_by_name(KEY_REBOOT_FFBM);
       button *btn_android = lay->find_button_by_name(KEY_REBOOT_ANDROID);

       if(btn_android != NULL) {
          btn_android->set_color(color_org);
       }       
       if(btn_poweroff != NULL) {
          btn_poweroff->set_color(color_org);
       }
       if(btn_ffbm != NULL) {
          btn_ffbm->set_color(color_org);
       }
    }

    switch_cur_layout_locked(lay, mod);
    button *display = lay->find_button_by_name(KEY_DISPLAY);

    if(display != NULL && note != NULL) {
        display->set_text(note);
    } else {
        MMI_ALOGW("Not find button(%s) int the layout(%s)", KEY_DISPLAY, lay->get_layout_path());
    }
    invalidate();

    /**waiting for confirm from user*/
    sem_wait(&g_sem_confirm);

    /**switch back to the previous screen*/
    lay = get_layout(mod);
    if(lay == NULL) {
        MMI_ALOGE("Not find layout for module:[%s]", mod->module);
        return false;
    }
    switch_cur_layout_locked(lay, mod);
    invalidate();

    return is_ok;
}

static void invalidate_button(reboot_opt_t opt) {

    int color_org = 0x7D7D7Dff;
    int color = 0x007D7Dff;

    layout *lay = get_layout(LAYOUT_REBOOT);

    if(lay == NULL) {
        MMI_ALOGE("Not find layout: '%s'", LAYOUT_REBOOT);
        return;
    }

    button *btn_poweroff = lay->find_button_by_name(KEY_REBOOT_POWEROFF);
    button *btn_ffbm = lay->find_button_by_name(KEY_REBOOT_FFBM);
    button *btn_android = lay->find_button_by_name(KEY_REBOOT_ANDROID);

    if(btn_android == NULL || btn_poweroff == NULL || btn_ffbm == NULL) {
        if(btn_android == NULL)
            MMI_ALOGE("Not find button(%s) in the layout(%s)", KEY_REBOOT_POWEROFF, LAYOUT_REBOOT);
        if(btn_poweroff == NULL)
            MMI_ALOGE("Not find button(%s) in the layout(%s)", KEY_REBOOT_FFBM, LAYOUT_REBOOT);
        if(btn_ffbm == NULL)
            MMI_ALOGE("Not find button(%s) in the layout(%s)", KEY_REBOOT_ANDROID, LAYOUT_REBOOT);
        return;
    }

    if(opt == REBOOT_POWEROFF) {
        btn_poweroff->set_color(color);
        btn_ffbm->set_color(color_org);
        btn_android->set_color(color_org);
        MMI_ALOGI("button(%s) be pressed", KEY_REBOOT_POWEROFF);
    } else if(opt == REBOOT_FFBM) {
        btn_poweroff->set_color(color_org);
        btn_ffbm->set_color(color);
        btn_android->set_color(color_org);
        MMI_ALOGI("button(%s) be pressed", KEY_REBOOT_FFBM);
    } else if(opt == REBOOT_ANDROID) {
        btn_poweroff->set_color(color_org);
        btn_ffbm->set_color(color_org);
        btn_android->set_color(color);
        MMI_ALOGI("button(%s) be pressed", KEY_REBOOT_ANDROID);
    }

    if(btn_android->get_disabled()) {
        btn_android->set_disabled(true);
    }
    if(btn_poweroff->get_disabled()) {
        btn_poweroff->set_disabled(true);
    }
    if(btn_ffbm->get_disabled()) {
        btn_ffbm->set_disabled(true);
    }

    invalidate();
}
static void onchange_reboot_android(void *m) {

    reboot_opt = REBOOT_ANDROID;
    invalidate_button(REBOOT_ANDROID);
}


static void onchange_reboot_ffbm(void *m) {

    reboot_opt = REBOOT_FFBM;
    invalidate_button(REBOOT_FFBM);
}

static void onchange_poweroff(void *m) {

    reboot_opt = REBOOT_POWEROFF;
    invalidate_button(REBOOT_POWEROFF);
}


static void do_ok(void *m) {
    MMI_ALOGI("OK be selected");
    res_tab = false;
    is_ok = true;
    sem_post(&g_sem_confirm);
}

static void do_cancel(void *m) {
    MMI_ALOGI("CANCLE be selected");
    is_ok = false;
    sem_post(&g_sem_confirm);
}

static void *process_runall(void *m) {
	int nread = -1;
    int _gpio_fd = -1;
    if(m == NULL) {
        MMI_ALOGE("Invalid parameter");
        return NULL;
    }
    /* wait for click ok or cancel */
 //   if(!pop_confirm(m, LAYOUT_CONFIRM, get_string(KEY_RUN_ALL_NOTICE))) {
        /* cancle is clicked */
 //       return NULL;
 //   }

    module_info *mod = (module_info *) m;

    sem_init(&g_sem_mod_complete, 0, 0);
    layout *lay = get_layout(mod);

    if(lay == NULL) {
        MMI_ALOGE("Not find layout for module:[%s]", mod->module);
        return NULL;
    }

    button *btn = lay->find_button_by_name(KEY_MAIN_RUNALL);

    if(btn != NULL) {
        if(btn->get_disabled()) {
            MMI_ALOGI("The previous RUN ALL action is not finished, please wait...");
            return NULL;
        } else {
            btn->set_disabled(true);
        }
    } else {
        MMI_ALOGW("Not find button(%s) in the layout(%s)", KEY_MAIN_RUNALL, lay->get_layout_path());
    }
    /**step one(ellis):reset last test result*/
//    if(lay != NULL && lay->m_listview != NULL) {
//        lay->m_listview->reset_result();
//        flush_result();
//    }

    MMI_ALOGI("start test manually case");
  /**start manually test cases*/
    if(!btn->get_smart_disabled()){
	    /**step one(ellis):reset last test result*/
	    if(lay != NULL && lay->m_listview != NULL) {
	        lay->m_listview->reset_result();
	        flush_result();
	    }
	    if(lay != NULL && lay->m_listview != NULL && lay->m_listview->get_items() != NULL) {

        list < item_t * >*items = lay->m_listview->get_items();
        list < item_t * >::iterator iter;
        for(iter = items->begin(); iter != items->end(); iter++) {
            item_t *item = (item_t *) (*iter);
            module_info *tmod = item->mod;

            if(tmod != NULL && !tmod->config_list[KEY_AUTOMATION].compare("0")) {
                usleep(100);
                switch_module(tmod);
                MMI_ALOGI("start module:[%s] testing", tmod->module);
                sem_wait(&g_sem_mod_complete);
                MMI_ALOGI("module:[%s] testing finished, result=%s", tmod->module, MMI_RESULT(tmod->result));
	            }
            }
        }
    }
    usleep(1000 * 500);
   /**start all automation cases*/
    MMI_ALOGI("start test all automation cases");
    start_all(true);
   
    MMI_ALOGI("%s,start manual test.", __FUNCTION__);
    /*check test is complete or not*/
    list < item_t * >::iterator iter;
    list < item_t * >*items = lay->m_listview->get_items();
    int ret = 0;
    button *btn_res = NULL;
    char buf[64];
    bool test_res = false;
    btn_res = lay->find_button_by_name(KEY_MAIN_FAILED);
    for(iter = items->begin(); iter != items->end(); iter++) {
        item_t *obj = (item_t *) (*iter);
	// ALOGI("%s[%s](ellis):testing...", __FUNCTION__, obj->mod->module); 
	do_show_fail(m); 
	usleep(1000 * 500); 
	if(obj != NULL && obj->mod != NULL) {
	    while(obj->mod->result == ERR_UNKNOW){
	        if(btn_res != NULL)
	            btn_res->set_color(0x0000ffff);
		continue;
	    }	  
	}		   
     }
    if(btn->get_smart_disabled()){
        //send result to smart-fct   
        MMI_ALOGI("%s:send result to smart-fct...uart_fd is %d", __FUNCTION__, uart_fd);
        if(uart_fd < 0){
            MMI_ALOGE("[%s]ate test thread error,maybe set uart port error,uart_fd is %d", __FUNCTION__, uart_fd);
        }else{
            //write(uart_fd, "QFCT:Send Result\r\n", strlen("QFCT:Send Result\r\n"));
            MMI_ALOGI("[%s]open uart success",__FUNCTION__);
            MMI_ALOGI("[%s] QFCT Result", __FUNCTION__);
                       char res[SIZE_1K] = {0,};
                       char *p = res; 
                       char *result_pc = NULL;
                       snprintf(p, sizeof(res), "QFCTResult:");//send result to pc test,start begin with 'QFCTResult:'
                       p = p+strlen("QFCTResult:");
            if(lay != NULL && lay->m_listview != NULL){
		           list < item_t * >*items = lay->m_listview->get_items();
		           list < item_t * >::iterator iter;
		           for(iter = items->begin(); iter != items->end(); iter++){
		       	       item_t *obj = (item_t *) (*iter);
                               if(obj != NULL && obj->mod != NULL){
                                   if(!strcmp(obj->mod->module,"SIMCARD1") || !strcmp(obj->mod->module,"SIMCARD2") || !strcmp(obj->mod->module,"WIFI") || !strcmp(obj->mod->module,"BLUETOOTH") || !strcmp(obj->mod->module,"GPS") || !strcmp(obj->mod->module,"SDCARD") || !strcmp(obj->mod->module,"EMMC") || !strcmp(obj->mod->module,"VIBRATOR")){
                                       if(obj->mod->result == FAILED){
                                           result_pc = "0";//0 test fail
                                       }
                                       if(obj->mod->result == SUCCESS){
                                           result_pc = "1";//1 test success
                                       }
                                       if(obj->mod->result == ERR_UNKNOW){
                                           result_pc = "2";//2 is not tested
                                       }
                                       
                                       snprintf(p, sizeof(res), "%s:%s;", obj->mod->module, result_pc);
                                       MMI_ALOGI("[%s] write result %s",res);
                                       p = res + strlen(res);
                                   } 
                               }
                           }
		       }
                       int res_len = strlen(res); 
                       res[res_len] = '\r';
                       res[res_len+1] = '\n';
                       res[res_len+2] = '\0';
                       nread=write(uart_fd, res, strlen(res));
        }
 
    }
    btn->set_smart_fct(false);
	MMI_ALOGE("[%s][%d] set smart fct false", __FUNCTION__, __LINE__);


    if(btn != NULL)
        btn->set_disabled(false);
    flush_result();
    do_show_fail(m);
    return NULL;
}

void do_run_all(void *m) {
    int ret = -1;
    pthread_t pid;

    ret = pthread_create(&pid, NULL, process_runall, m);
    if(ret < 0) {
        MMI_ALOGE("create thread fail, error=%s\n", strerror(errno));
    }
    MMI_ALOGD("create thread(thread id=%lu) for run all test case\n", pid);
}

static void *process_reboot(void *m) {

    if(pop_confirm(m, LAYOUT_REBOOT, get_string(KEY_REBOOT_NOTICE))) {
        if(reboot_opt == REBOOT_POWEROFF) {
            system("reboot -p");
            MMI_ALOGI("system power off");
        } else if(reboot_opt == REBOOT_FFBM) {
            set_boot_mode(BOOT_MODE_UI);
            system("reboot");
            MMI_ALOGI("system reboot to FFBM");
        } else if(reboot_opt == REBOOT_ANDROID) {
            set_boot_mode(BOOT_MODE_NORMAL);
            system("reboot");
            MMI_ALOGI("system reboot to android");
        } else {
            MMI_ALOGE("reboot_opt value(%d) is invalid", reboot_opt);
        }
    } else {
        MMI_ALOGI("cancell be selected, return to last menu");
    }
    return NULL;
}

static void do_reboot(void *m) {
    int ret = -1;
    pthread_t pid;

    ret = pthread_create(&pid, NULL, process_reboot, m);
    if(ret < 0) {
        MMI_ALOGE("create thread fail, error=%s\n", strerror(errno));
    }
    MMI_ALOGD("create thread(thread id=%lu) for reboot\n", pid);
}

static void *process_reset(void *m) {

    if(m == NULL) {
        MMI_ALOGE("Invalid parameter");
        return NULL;
    }

    module_info *mod = (module_info *) m;
    layout *lay = get_layout(mod);

    if(lay == NULL) {
        MMI_ALOGE("Not find layout for module:[%s]", mod->module);
        return NULL;
    }

    if(pop_confirm(m, LAYOUT_CONFIRM, get_string(KEY_RESET_NOTICE))) {
        MMI_ALOGI("confirm be selected, reset test result");
        if(lay != NULL && lay->m_listview != NULL) {
            lay->m_listview->reset_result();
            res_tab = false;
        } else {
            MMI_ALOGE("Not find listview in the layout(%s)", !lay ? "" : lay->get_layout_path());
        }
        // clear result file
        if(check_file_exist(g_res_file)) {
            clear_file(g_res_file);
        }
        update_main_status();//update main status button in main.xml
        do_show_all(m);//show main ui
    } else {
        MMI_ALOGI("cancell be selected, return to last menu");
    }
    return NULL;
}

static void *process_report(void *m) {
    rect_t rect;
    int w = get_fb_width();
    int h = get_fb_height();
    int temp_x = 0;
    int temp_y = 57 * h / 100;
    module_info *mod = NULL;
    layout *lay = NULL;

    list < item_t * >*item_list = NULL;
    item_t *obj = NULL;

    if(m == NULL) {
        MMI_ALOGE("Invalid parameter");
        return NULL;
    }

    mod = (module_info *) m;

    lay = get_layout(mod);
    if(lay == NULL) {
        MMI_ALOGE("Not find layout for module:[%s]", mod->module);
        return NULL;
    }

    item_list = lay->m_listview->get_items();
    if(item_list == NULL) {
        MMI_ALOGE("Cannot get item list");
        return NULL;
    }

    button *btn[lay->m_listview->get_item_num()];

    sem_init(&g_sem_confirm, 0, 0);

    lay = get_layout(LAYOUT_REPORT);
    if(lay == NULL) {
        MMI_ALOGE("Not find layout: %s", LAYOUT_REPORT);
        return NULL;
    }

    switch_cur_layout_locked(lay, mod);

    list < item_t * >::iterator iter;
    for(iter = item_list->begin(); iter != item_list->end(); iter++) {
        obj = (item_t *) (*iter);

    /**if the module already tested*/
        if(obj != NULL && obj->mod != NULL && obj->mod->result == FAILED) {
            rect.x = temp_x;
            rect.y = temp_y;

            if(rect.x > w) {
                rect.x = 0;
                rect.y = temp_y + (7 * h / 100);
            }
            rect.w = 22 * w / 100;
            rect.h = 5 * h / 100;

            button *btn = new button(obj->mod->module, rect, "", NULL);

            btn->set_text(obj->mod->module);
            btn->set_color(COLOR_RED);
            lay->add_button_locked(btn);

            temp_x = rect.x + (26 * w / 100);
            temp_y = rect.y;

            MMI_ALOGI("module:[%s] test fail", obj->mod->module);
        }
    }

    invalidate();

    /**waiting for confirm from user*/
    sem_wait(&g_sem_confirm);

    int ret = 0;

    lay = acquire_cur_layout();
    for(iter = item_list->begin(); iter != item_list->end(); iter++) {
        obj = (item_t *) (*iter);
        if(lay != NULL && obj != NULL && obj->mod != NULL && obj->mod->result == FAILED) {
            ret = lay->delete_btn_by_name(obj->mod->module);
            if(!ret) {
                MMI_ALOGE("Fail to delete button: %s", obj->mod->module);
            } else {
                MMI_ALOGD("button:%s be deleted", obj->mod->module);
            }
        }
    }
    release_cur_layout();

    /**switch back to the previous screen*/
    lay = get_layout(mod);
    if(lay == NULL) {
        MMI_ALOGE("Not find layout for module:[%s]", mod->module);
        return NULL;
    }
    switch_cur_layout_locked(lay, mod);
    invalidate();

    return NULL;
}

static void do_report(void *m) {
    int ret = -1;
    pthread_t pid;

    ret = pthread_create(&pid, NULL, process_report, m);
    if(ret < 0) {
        MMI_ALOGE("create thread fail, error=%s\n", strerror(errno));
    }
    MMI_ALOGD("create thread(thread id=%lu) to report for module:[%s]\n", pid, ((module_info *) m)->module);
}

static void do_reset(void *m) {
    int ret = -1;
    pthread_t pid;

    ret = pthread_create(&pid, NULL, process_reset, m);
    if(ret < 0) {
        MMI_ALOGE("create thread fail, error=%s\n", strerror(errno));
    }
    MMI_ALOGD("create thread(thread id=%lu) to reset for module:[%s]\n", pid, ((module_info *) m)->module);
}

static void *process_extra_cmd(void *m) {
    if(m == NULL) {
        MMI_ALOGE("Invalid parameter");
        return NULL;
    }

    module_info *mod = (module_info *) m;
    extra_cmd_t *cmd = &mod->extracmd;

    if(cmd->is_valid == true && cmd->size > 0) {
        MMI_ALOGI("exec extra command for module:[%s]", mod->module);
        send_run_extra(mod, (cmd->cmd_list.front()).c_str());
    } else {
        MMI_ALOGW("extra command is invalid for module:[%s]", mod->module);
    }

    return NULL;
}

static void do_extra_cmd(void *m) {
    int ret = -1;
    pthread_t pid;

    ret = pthread_create(&pid, NULL, process_extra_cmd, m);
    if(ret < 0) {
        MMI_ALOGE("create thread fail, error=%s\n", strerror(errno));
    }
    MMI_ALOGD("create thread(thread id=%lu) to exec extra command for module:[%s]\n", pid, ((module_info *) m)->module);
}

static func_map_t func_list[] = {
    {"do_cancel", do_cancel},
    {"do_extra_cmd", do_extra_cmd},
    {"do_fail", do_fail},
    {"do_ok", do_ok},
    {"do_report", do_report},
    {"do_page_down", do_page_down},
    {"do_page_up", do_page_up},
    {"do_pass", do_pass},
    {"switch_module", switch_module},
    {"do_reboot", do_reboot},
    {"do_run_all", do_run_all},
    {"do_reset", do_reset},
    {"do_show_fail", do_show_fail},
    {"do_show_all", do_show_all},
#ifdef ANDROID
    {"do_next", do_next},
#endif
    {"do_exit", do_exit},
    {"onchange_poweroff", onchange_poweroff},
    {"onchange_reboot_ffbm", onchange_reboot_ffbm},
    {"onchange_reboot_android", onchange_reboot_android},
};

static unordered_map < string, cb_t > func_map;
void create_func_map() {
    uint32_t i = 0;

    for(i = 0; i < sizeof(func_list) / sizeof(func_map_t); i++) {
        func_map[(string) func_list[i].name] = func_list[i].cb;
    }
}

cb_t get_cb(string func_name) {
    return func_map[func_name];
}
cb_t get_cb(char *func_name) {
    if(func_name == NULL)
        return NULL;
    return func_map[(string) func_name];
}
