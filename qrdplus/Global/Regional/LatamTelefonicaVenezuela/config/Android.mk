LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

#################################################
LOCAL_MODULE := .preloadspec
LOCAL_SRC_FILES := $(LOCAL_MODULE)
LOCAL_MODULE_CLASS := LatamTelefonicaVenezuela
LOCAL_MODULE_PATH := $(TARGET_OUT)/vendor/LatamTelefonicaVenezuela
include $(BUILD_PREBUILT)

#################################################

include $(CLEAR_VARS)

#################################################
LOCAL_MODULE := vendor.prop
LOCAL_SRC_FILES := $(LOCAL_MODULE)
LOCAL_MODULE_CLASS := LatamTelefonicaVenezuela
LOCAL_MODULE_PATH := $(TARGET_OUT)/vendor/LatamTelefonicaVenezuela/system/vendor
include $(BUILD_PREBUILT)

#################################################
