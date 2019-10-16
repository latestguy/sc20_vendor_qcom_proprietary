LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := spn-conf.xml
LOCAL_SRC_FILES := $(LOCAL_MODULE)
LOCAL_MODULE_CLASS := LanixTelcelMexico
LOCAL_MODULE_PATH := $(TARGET_OUT)/vendor/LanixTelcelMexico/system/etc
include $(BUILD_PREBUILT)
