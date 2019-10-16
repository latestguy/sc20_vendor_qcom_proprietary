LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_SRC_FILES := wpa_supplicant_test.conf
LOCAL_CFLAGS := -Wall
LOCAL_MODULE := wpa_supplicant_test.conf
LOCAL_MODULE_CLASS := ETC
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_PATH := $(TARGET_OUT_ETC)/mmi
include $(BUILD_PREBUILT)

