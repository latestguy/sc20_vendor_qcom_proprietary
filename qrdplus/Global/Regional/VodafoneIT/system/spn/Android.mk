LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := spn-conf.xml
LOCAL_SRC_FILES := $(LOCAL_MODULE)
LOCAL_MODULE_CLASS := VodafoneIT
LOCAL_MODULE_PATH := $(TARGET_OUT)/vendor/VodafoneIT/system/etc
include $(BUILD_PREBUILT)
