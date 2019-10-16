LOCAL_PATH := $(call my-dir)

# ---------------------------------------
# Build ConfigurationClientDemo application
#
# Output: ConfigurationClientDemo.apk
# ----------------------------------------

include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := \
        $(call all-java-files-under, src)

LOCAL_PACKAGE_NAME := ConfigurationClientDemo

LOCAL_PROGUARD_ENABLED := disabled

LOCAL_DEX_PREOPT := false

LOCAL_MODULE_TAGS := eng development

include $(BUILD_PACKAGE)

