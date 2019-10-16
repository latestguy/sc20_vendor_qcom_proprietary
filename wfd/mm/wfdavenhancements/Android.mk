LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

#-----------------------------------------------------------------
# Define
#-----------------------------------------------------------------
LOCAL_CFLAGS := -D_ANDROID_

#----------------------------------------------------------------
# SRC CODE
#----------------------------------------------------------------
LOCAL_SRC_FILES := src/WFDSurfaceMediaSource.cpp

#----------------------------------------------------------------
# INCLUDE PATH
#----------------------------------------------------------------
LOCAL_C_INCLUDES := $(LOCAL_PATH)/inc
LOCAL_C_INCLUDES += $(TOP)/frameworks/native/include/media/openmax
LOCAL_C_INCLUDES += $(TOP)/frameworks/native/include/media/hardware
LOCAL_C_INCLUDES += $(TOP)/frameworks/av/include/media
ifeq ($(TARGET_USES_QCOM_BSP), true)
    LOCAL_C_INCLUDES += hardware/qcom/display/libgralloc
    LOCAL_CFLAGS += -DQTI_BSP
endif
LOCAL_SHARED_LIBRARIES := liblog
LOCAL_SHARED_LIBRARIES += libbinder
LOCAL_SHARED_LIBRARIES += libmediautils
LOCAL_SHARED_LIBRARIES += libutils
LOCAL_SHARED_LIBRARIES += libmedia
LOCAL_SHARED_LIBRARIES += libstagefright
LOCAL_SHARED_LIBRARIES += libgui
LOCAL_SHARED_LIBRARIES += libui
LOCAL_SHARED_LIBRARIES += libstagefright_foundation

LOCAL_MODULE:= libwfdavenhancements

LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_OWNER := qti
LOCAL_PROPRIETARY_MODULE := true

include $(BUILD_SHARED_LIBRARY)
