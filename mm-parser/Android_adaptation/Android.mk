
LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

# ---------------------------------------------------------------------------------
#    If TARGET_ENABLE_QC_AV_ENHANCEMENTS is defined, then compile AAL Code
# ---------------------------------------------------------------------------------
ifeq ($(TARGET_ENABLE_QC_AV_ENHANCEMENTS),true)

LOCAL_C_FLAGS:= -D_ANDROID_

ifeq ($(call is-vendor-board-platform,QCOM),true)
ifeq ($(strip $(AUDIO_FEATURE_ENABLED_EXTN_FLAC_DECODER)),true)
    LOCAL_CFLAGS += -DQTI_FLAC_DECODER
endif
ifeq ($(strip $(AUDIO_FEATURE_ENABLED_FLAC_OFFLOAD)),true)
    LOCAL_CFLAGS += -DFLAC_OFFLOAD_ENABLED
endif
endif

PREBUILT_HY11_STATS_PATH := $(LOCAL_PATH)/../../prebuilt_HY11
PREBUILT_HY22_STATS_PATH := $(LOCAL_PATH)/../../prebuilt_HY22
PREBUILT_GREASE_STATS_PATH := $(LOCAL_PATH)/../../prebuilt_grease
HAS_PREBUILT_PATH := false
HAS_PREBUILT_PATH := $(shell if [ -d $(PREBUILT_HY11_STATS_PATH) ] ; then echo true; else echo false; fi)
ifeq ($(HAS_PREBUILT_PATH),false)
  HAS_PREBUILT_PATH := $(shell if [ -d $(PREBUILT_HY22_STATS_PATH) ] ; then echo true; else echo false; fi)
endif

ifeq ($(HAS_PREBUILT_PATH),false)
  HAS_PREBUILT_PATH := $(shell if [ -d $(PREBUILT_GREASE_STATS_PATH) ] ; then echo true; else echo false; fi)
endif

ifneq ($(HAS_PREBUILT_PATH),true)
 LOCAL_SRC_FILES:=                 \
     src/MMParserExtractor.cpp     \
     src/QComExtractorFactory.cpp  \
     src/SourcePort.cpp            \

 LOCAL_C_INCLUDES:=                                   \
     $(LOCAL_PATH)/inc                                \
     $(LOCAL_PATH)/../Api/inc                         \
     $(LOCAL_PATH)/../../mm-osal/inc                  \
     $(LOCAL_PATH)/../../common/inc                   \
     $(TARGET_OUT_HEADERS)/mm-core/omxcore            \
     $(TARGET_OUT_HEADERS)/avenhancements             \
     $(TOP)/frameworks/native/include/media/hardware  \
     $(TOP)/frameworks/av/media/libstagefright


 LOCAL_SHARED_LIBRARIES +=       \
     libstagefright              \
     libmmosal                   \
     libbinder                   \
     libutils                    \
     libcutils                   \
     libdl                       \
     libmmparser_lite            \
     libmedia                    \
     libstagefright_foundation

 LOCAL_MODULE:= libmmparser

 LOCAL_MODULE_TAGS := optional

 LOCAL_MODULE_OWNER := qti
 LOCAL_PROPRIETARY_MODULE := true

 include $(BUILD_SHARED_LIBRARY)

endif #HAS_PREBUILT_PATH
endif #TARGET_ENABLE_QC_AV_ENHANCEMENTS
