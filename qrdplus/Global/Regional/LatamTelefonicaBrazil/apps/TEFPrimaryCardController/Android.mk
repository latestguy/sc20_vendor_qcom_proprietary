LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE_TAGS := optional

LOCAL_PROGUARD_ENABLED := disabled

LOCAL_SRC_FILES := $(call all-subdir-java-files)

LOCAL_JAVA_LIBRARIES := telephony-common

LOCAL_PACKAGE_NAME := TEFPrimaryCardController

LOCAL_MODULE_PATH := $(TARGET_OUT)/vendor/LatamTelefonicaBrazil/system/app

LOCAL_CERTIFICATE := platform

include $(BUILD_PACKAGE)
