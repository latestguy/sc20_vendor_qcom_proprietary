LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

#################################################
LOCAL_MODULE := .preloadspec
LOCAL_SRC_FILES := $(LOCAL_MODULE)
LOCAL_MODULE_CLASS := VodafoneIT
LOCAL_MODULE_PATH := $(TARGET_OUT)/vendor/VodafoneIT
include $(BUILD_PREBUILT)

#################################################

include $(CLEAR_VARS)

#################################################
LOCAL_MODULE := vendor.prop
LOCAL_SRC_FILES := $(LOCAL_MODULE)
LOCAL_MODULE_CLASS := VodafoneIT
LOCAL_MODULE_PATH := $(TARGET_OUT)/vendor/VodafoneIT/system/vendor
include $(BUILD_PREBUILT)

################################################
