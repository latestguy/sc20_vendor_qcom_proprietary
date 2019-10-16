LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

#################################################
LOCAL_MODULE := .preloadspec
LOCAL_SRC_FILES := $(LOCAL_MODULE)
LOCAL_MODULE_CLASS := LatamTelefonicaUruguay
LOCAL_MODULE_PATH := $(TARGET_OUT)/vendor/LatamTelefonicaUruguay
include $(BUILD_PREBUILT)

#################################################

include $(CLEAR_VARS)

#################################################
LOCAL_MODULE := vendor.prop
LOCAL_SRC_FILES := $(LOCAL_MODULE)
LOCAL_MODULE_CLASS := LatamTelefonicaUruguay
LOCAL_MODULE_PATH := $(TARGET_OUT)/vendor/LatamTelefonicaUruguay/system/vendor
include $(BUILD_PREBUILT)

#################################################
