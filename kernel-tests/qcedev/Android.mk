ifneq (,$(filter arm aarch64 arm64, $(TARGET_ARCH)))

LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_C_INCLUDES := $(TARGET_OUT_INTERMEDIATES)/KERNEL_OBJ/usr/include
LOCAL_ADDITIONAL_DEPENDENCIES := $(TARGET_OUT_INTERMEDIATES)/KERNEL_OBJ/usr

LOCAL_SHARED_LIBRARIES := \
        libc \
        libcutils \
        libutils

LOCAL_MODULE := qcedev_test
LOCAL_SRC_FILES := qcedev_test.c
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_PATH := $(TARGET_OUT_DATA)/kernel-tests
LOCAL_CFLAGS := $(QCEDEV_CFLAGS)
LOCAL_PRELINK_MODULE := false
LOCAL_MODULE_OWNER := qcom
include $(BUILD_EXECUTABLE)

include $(CLEAR_VARS)
LOCAL_MODULE := qcedev_test.sh
LOCAL_MODULE_CLASS := EXECUTABLES
LOCAL_SRC_FILES := qcedev_test.sh
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_PATH := $(TARGET_OUT_DATA)/kernel-tests
LOCAL_MODULE_OWNER := qcom
include $(BUILD_PREBUILT)
endif
