LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_RESOURCE_DIR := vendor/qcom/proprietary/qrdplus/Global/Regional/OrangeCommon/system/WiFiConnectNotificate/res

ifeq ($(TARGET_BUILD_APPS),)
LOCAL_RESOURCE_DIR += frameworks/support/v7/appcompat/res
LOCAL_RESOURCE_DIR += frameworks/support/v7/gridlayout/res
else
LOCAL_RESOURCE_DIR += prebuilts/sdk/current/support/v7/appcompat/res
LOCAL_RESOURCE_DIR += prebuilts/sdk/current/support/v7/gridlayout/res
endif

LOCAL_MODULE_TAGS := optional
LOCAL_DEX_PREOPT := false
LOCAL_CERTIFICATE := platform
LOCAL_PACKAGE_NAME := WiFiConnectNotificate
LOCAL_MODULE_PATH := $(TARGET_OUT)/vendor/OrangeCommon/system/app

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_STATIC_JAVA_LIBRARIES += android-support-v13
LOCAL_STATIC_JAVA_LIBRARIES += android-support-v7-appcompat
LOCAL_STATIC_JAVA_LIBRARIES += android-support-v7-gridlayout

LOCAL_AAPT_FLAGS := --auto-add-overlay
LOCAL_AAPT_FLAGS += --extra-packages android.support.v7.appcompat
LOCAL_AAPT_FLAGS += --extra-packages android.support.v7.gridlayout

include $(BUILD_PACKAGE)
