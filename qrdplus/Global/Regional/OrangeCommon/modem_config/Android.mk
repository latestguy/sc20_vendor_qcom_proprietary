LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

#################################################
LOCAL_MODULE := mcfg_sw.mbn
LOCAL_SRC_FILES := $(LOCAL_MODULE)
LOCAL_MODULE_CLASS := OrangeCommon
LOCAL_MODULE_PATH := $(TARGET_OUT)/vendor/OrangeCommon/data/modem_config
include $(BUILD_PREBUILT)

####################################################################################################
