LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := spn-conf.xml
LOCAL_SRC_FILES := $(LOCAL_MODULE)
LOCAL_MODULE_CLASS := VodafoneHungary
LOCAL_MODULE_PATH := $(TARGET_OUT)/vendor/VodafoneHungary/system/etc
include $(BUILD_PREBUILT)
