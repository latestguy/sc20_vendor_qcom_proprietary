################################################################################
# @file pkgs/stringl/Android.mk
# @brief Makefile for building the string library on Android.
################################################################################

LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

ifeq ($(TARGET_SUPPORTS_ANDROID_WEAR),true)
LOCAL_CFLAGS := -DTARGET_SUPPORTS_ANDROID_WEAR
endif
libdiag_includes:= \
        $(LOCAL_PATH)/../include \
	$(LOCAL_PATH)/../src \

LOCAL_C_INCLUDES:= $(libdiag_includes)
LOCAL_C_INCLUDES += $(TARGET_OUT_HEADERS)/common/inc

LOCAL_SRC_FILES:= \
	diag_mdlog.c \

commonSharedLibraries :=libdiag liblog \
	
LOCAL_MODULE:= diag_mdlog
LOCAL_MODULE_TAGS := optional debug
LOCAL_SHARED_LIBRARIES := $(commonSharedLibraries)

LOCAL_MODULE_OWNER := qcom
include $(BUILD_EXECUTABLE)
