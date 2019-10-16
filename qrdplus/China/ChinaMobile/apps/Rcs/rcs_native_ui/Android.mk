LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

public_account_dir := ../rcs_public_account

src_dirs := src $(public_account_dir)/src
res_dirs := res $(public_account_dir)/res

LOCAL_SRC_FILES := $(call all-java-files-under, $(src_dirs))
LOCAL_RESOURCE_DIR := $(addprefix $(LOCAL_PATH)/, $(res_dirs))

LOCAL_AAPT_FLAGS := \
    --auto-add-overlay \
    --extra-packages com.suntek.mway.rcs.publicaccount \

LOCAL_PACKAGE_NAME := NativeUI
LOCAL_CERTIFICATE := platform

LOCAL_MODULE_PATH := $(TARGET_OUT)/vendor/ChinaMobile/system/app

LOCAL_JAVA_LIBRARIES := rcs_service_api
LOCAL_JAVA_LIBRARIES += vcard
LOCAL_JAVA_LIBRARIES += telephony-common

LOCAL_PROGUARD_FLAG_FILES := proguard.flags
include $(BUILD_PACKAGE)
