LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_JAVA_LIBRARIES := telephony-common

LOCAL_MODULE_TAGS := optional

LOCAL_PROGUARD_ENABLED:= disabled

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/res

LOCAL_PACKAGE_NAME := TEFNetworkInfoDisplay

LOCAL_MODULE_PATH := $(TARGET_OUT)/vendor/LatamTelefonicaBrazil/system/app

LOCAL_CERTIFICATE := platform

include $(BUILD_PACKAGE)
