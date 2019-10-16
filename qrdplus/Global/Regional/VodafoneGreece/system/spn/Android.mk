LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := spn-conf.xml
LOCAL_SRC_FILES := $(LOCAL_MODULE)
LOCAL_MODULE_CLASS := VodafoneGreece
LOCAL_MODULE_PATH := $(TARGET_OUT)/vendor/VodafoneGreece/system/etc
include $(BUILD_PREBUILT)
