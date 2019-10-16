LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

#################################################
LOCAL_MODULE := .preloadspec
LOCAL_SRC_FILES := $(LOCAL_MODULE)
LOCAL_MODULE_CLASS := VodafonePT
LOCAL_MODULE_PATH := $(TARGET_OUT)/vendor/VodafonePT
include $(BUILD_PREBUILT)

#################################################

include $(CLEAR_VARS)

#################################################
LOCAL_MODULE := vendor.prop
LOCAL_SRC_FILES := $(LOCAL_MODULE)
LOCAL_MODULE_CLASS := VodafonePT
LOCAL_MODULE_PATH := $(TARGET_OUT)/vendor/VodafonePT/system/vendor
include $(BUILD_PREBUILT)

################################################
