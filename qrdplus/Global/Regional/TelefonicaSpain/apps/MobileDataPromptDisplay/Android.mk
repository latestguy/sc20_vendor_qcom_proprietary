LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_PROGUARD_ENABLED:= disabled

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_PACKAGE_NAME := MobileDataPromptDisplay

LOCAL_MODULE_PATH := $(TARGET_OUT)/vendor/TelefonicaSpain/system/app

LOCAL_CERTIFICATE := platform

include $(BUILD_PACKAGE)
