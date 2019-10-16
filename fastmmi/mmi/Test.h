#ifndef __TEST_H
#define __TEST_H
#include <stdio.h>
#include <time.h>
#include <binder/IInterface.h>
#include <binder/Parcel.h>
#include <binder/IBinder.h>
#include <binder/Binder.h>
#include <binder/ProcessState.h>
#include <binder/IPCThreadState.h>
#include <binder/IServiceManager.h>
#include <android/log.h>
#include <inttypes.h>
#include <stdlib.h>

using namespace android;
//#define res_path "/cache/FTM_AP/mmi.cfg"
//#define res_path "/etc/mmi/mmi.cfg"

typedef struct{
    char *name; // item name
    int result; // -1: fail  0: none  1: success
}fct_item_type;

class ModuleCtrl{
    private:
        static ModuleCtrl* sInstance;
        ModuleCtrl();
        char *_version;
        char *res_path;
    public:
        static ModuleCtrl *getInstance(){
            if (sInstance == NULL){
                sInstance = new ModuleCtrl();
            }
            return sInstance;
        }
        ~ModuleCtrl();
        int item_num;
        bool check_file_exist(const char *path);
        int check_res();
        int set_info();
        int set_info(char *cmd,char *buf);
        bool is_ssss(void);
        int _is_quectel(void);
        int isValidVersion(char *buff);
        int update();
        int _wce_main(void);
        int _android_version();
        int parse_res(void *g_modules_map);
        int get_info(char *_info);
        int parse_module(const char *line, char *module, int module_len);
        int parse_value(const char *line, char indicator, char *name, int name_len, char *value, int value_len);
        char* trim(char *str);
        int read_file(const char *filepath, char *buf, int size);
        bool check_lcds(void);
};

namespace android{
    #define QUEC_DEBUG_ON 0

    #define LOGI(fmt,...)  do{\
                                if(QUEC_DEBUG_ON)\
                                    __android_log_print(ANDROID_LOG_INFO, "QuecS","[%s-%u]:" fmt,__FUNCTION__,__LINE__,##__VA_ARGS__);\
                            }while(0)
    #define LOGW(fmt,...) __android_log_print(ANDROID_LOG_WARN, "QuecS","[%s-%u]:" fmt,__FUNCTION__,__LINE__,##__VA_ARGS__)
    #define LOGE(fmt,...) __android_log_print(ANDROID_LOG_ERROR, "QuecS","[%s-%u]:" fmt,__FUNCTION__,__LINE__,##__VA_ARGS__)
    #define SIZE_1K 1024

    class IQuecService : public IInterface{
        public:
            DECLARE_META_INTERFACE(QuecService); // declare macro
            virtual void test()=0;
            virtual int _get_version(char *_info)=0;
            virtual int _parse_xml()=0;
            virtual void _parse_cfg()=0;
            virtual void _reparse_cfg()=0;
            virtual int _parse_sim()=0;
            virtual int _is_quectel()=0;
            virtual void _compile_date(char *compile_date, int len)=0;
            virtual int _parse_res(char *res) = 0;
            virtual int _check_res() = 0;
            virtual int _check_lcd() = 0;
            virtual int _android_version() = 0;
            virtual int _at_mode(const char *at_cmd, char *buf) = 0;
    };

    enum{
        TEST = IBinder::FIRST_CALL_TRANSACTION + 0,
        _GET_VER = IBinder::FIRST_CALL_TRANSACTION + 1,
        _PARSE_XML = IBinder::FIRST_CALL_TRANSACTION + 2,
        _PARSE_CFG = IBinder::FIRST_CALL_TRANSACTION + 3,
        _REPARSE_CFG = IBinder::FIRST_CALL_TRANSACTION + 4,
        _PARSE_SIM = IBinder::FIRST_CALL_TRANSACTION + 5,
        _COMPILE_DATE = IBinder::FIRST_CALL_TRANSACTION + 6,
        _PARSE_RES = IBinder::FIRST_CALL_TRANSACTION + 7,
        _IS_QUEC = IBinder::FIRST_CALL_TRANSACTION + 8,
        _TWO_LCDS = IBinder::FIRST_CALL_TRANSACTION + 9,
        _ANDROID_VER = IBinder::FIRST_CALL_TRANSACTION + 10,
        _CHECK_RES = IBinder::FIRST_CALL_TRANSACTION + 11,
        _AT_MODE = IBinder::FIRST_CALL_TRANSACTION + 12,
    };


    class BnQuecService: public BnInterface<IQuecService> {
        public:
            virtual status_t onTransact(uint32_t code, const Parcel& data, Parcel* reply,uint32_t flags = 0);

    };

    class BnQuecServiceProxy: public BnQuecService {
        public:
            static BnQuecServiceProxy* getInstance() {
                if (bInstance == NULL) {
                    bInstance = new BnQuecServiceProxy();
                }
                return bInstance;
            }
            ModuleCtrl *mc;
            virtual void test();
            virtual int _get_version(char *_info);
            virtual int _parse_xml();
            virtual void _parse_cfg();
            virtual void _reparse_cfg();
            virtual int _parse_sim();
            virtual int _is_quectel();
            virtual void _compile_date(char *compile_date,int len);
            virtual int _parse_res(char *res);
            virtual int _check_res();
            virtual int _check_lcd();
            virtual int _android_version();
            virtual int _at_mode(const char *at_cmd, char *buf);
        private:
            BnQuecServiceProxy();
            virtual ~BnQuecServiceProxy();
            static BnQuecServiceProxy* bInstance;
    };

    class BpQuecService: public BpInterface<IQuecService> {
        public:
        	BpQuecService(const sp<IBinder>& impl);
        	virtual void test();
            virtual int _get_version(char *_info);
            virtual int _parse_xml();
            virtual void _parse_cfg();
            virtual void _reparse_cfg();
            /**
             * is_w_ce_sim-check current module is w,ce with one simcard,or ce with two simcards
             * return:0 is w
             *        1 is ce with one simcard
             *        2 is ce with two simcards
             */
            virtual int _parse_sim();
            virtual int _is_quectel();
            virtual void _compile_date(char *compile_date, int len);
            virtual int _parse_res(char *res);
            virtual int _check_res();
            virtual int _check_lcd();
            virtual int _android_version();
            virtual int _at_mode(const char *at_cmd, char *buf);
            ~BpQuecService();
    };
}
#endif

