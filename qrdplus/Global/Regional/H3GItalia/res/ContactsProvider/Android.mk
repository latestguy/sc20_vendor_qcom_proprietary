#Copyright (c) 2016 Qualcomm Technologies, Inc.
#All Rights Reserved.
#Confidential and Proprietary - Qualcomm Technologies, Inc.

LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional
LOCAL_SRC_FILES := $(call all-subdir-java-files)
LOCAL_SDK_VERSION := current
LOCAL_PACKAGE_NAME := H3GItaliaContactsProviderRes
LOCAL_MODULE_PATH := $(TARGET_OUT)/vendor/H3GItalia/system/vendor/overlay
LOCAL_CERTIFICATE := shared

include $(BUILD_PACKAGE)
