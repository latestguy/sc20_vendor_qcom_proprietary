LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
	qti-app_trigger.c

LOCAL_SHARED_LIBRARIES := \
	libcutils \
        libdl \
        libutils

LOCAL_C_INCLUDES := \
	$(LOCAL_PATH)/../inc \
	$(LOCAL_PATH)/../libqc-opt \
    $(TARGET_OUT_HEADERS)/android-perf

LOCAL_CFLAGS += \
	-Wall \
	-DQC_DEBUG=0


LOCAL_MODULE := libqti-at
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_OWNER := qti
LOCAL_PROPRIETARY_MODULE := true

include $(BUILD_SHARED_LIBRARY)
