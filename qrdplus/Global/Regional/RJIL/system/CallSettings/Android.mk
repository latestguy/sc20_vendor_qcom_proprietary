LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_JAVA_LIBRARIES := telephony-common ims-common

LOCAL_SRC_FILES := $(call all-subdir-java-files)
LOCAL_PACKAGE_NAME = RJILCallSettings
LOCAL_CERTIFICATE := platform
LOCAL_MODULE_PATH := $(TARGET_OUT)/vendor/RJIL/system/vendor/overlay
LOCAL_MODULE_OWNER = qti

include $(BUILD_PACKAGE)


