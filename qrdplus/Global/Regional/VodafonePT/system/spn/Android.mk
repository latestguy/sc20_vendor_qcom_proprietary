LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := spn-conf.xml
LOCAL_SRC_FILES := $(LOCAL_MODULE)
LOCAL_MODULE_CLASS := VodafonePT
LOCAL_MODULE_PATH := $(TARGET_OUT)/vendor/VodafonePT/system/etc
include $(BUILD_PREBUILT)
