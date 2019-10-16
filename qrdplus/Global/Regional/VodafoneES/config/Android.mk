LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

#################################################
LOCAL_MODULE := .preloadspec
LOCAL_SRC_FILES := $(LOCAL_MODULE)
LOCAL_MODULE_CLASS := VodafoneES
LOCAL_MODULE_PATH := $(TARGET_OUT)/vendor/VodafoneES
include $(BUILD_PREBUILT)

#################################################

include $(CLEAR_VARS)

#################################################
LOCAL_MODULE := vendor.prop
LOCAL_SRC_FILES := $(LOCAL_MODULE)
LOCAL_MODULE_CLASS := VodafoneES
LOCAL_MODULE_PATH := $(TARGET_OUT)/vendor/VodafoneES/system/vendor
include $(BUILD_PREBUILT)

################################################
