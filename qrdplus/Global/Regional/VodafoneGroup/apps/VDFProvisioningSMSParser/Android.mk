LOCAL_PATH:= $(call my-dir)

# ---------------------------------------
# Build VDFProvisioningSMSParser application
#
# Output: VDFProvisioningSMSParser.apk
# ----------------------------------------

include $(CLEAR_VARS)

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_RESOURCE_DIR = $(LOCAL_PATH)/res

LOCAL_PACKAGE_NAME := VDFProvisioningSMSParser

LOCAL_CERTIFICATE := platform

LOCAL_MODULE_PATH := $(TARGET_OUT)/vendor/VodafoneGroup/system/app

LOCAL_MODULE_TAGS := optional

include $(BUILD_PACKAGE)
