LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

#################################################
LOCAL_MODULE := .preloadspec
LOCAL_SRC_FILES := $(LOCAL_MODULE)
LOCAL_MODULE_CLASS := VodafoneGreece
LOCAL_MODULE_PATH := $(TARGET_OUT)/vendor/VodafoneGreece
include $(BUILD_PREBUILT)

#################################################

include $(CLEAR_VARS)

#################################################
LOCAL_MODULE := vendor.prop
LOCAL_SRC_FILES := $(LOCAL_MODULE)
LOCAL_MODULE_CLASS := VodafoneGreece
LOCAL_MODULE_PATH := $(TARGET_OUT)/vendor/VodafoneGreece/system/vendor
include $(BUILD_PREBUILT)

################################################
