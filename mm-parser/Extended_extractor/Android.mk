LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)


# ---------------------------------------------------------------------------------
#    If TARGET_ENABLE_QC_AV_ENHANCEMENTS is defined, then compile AAL Code
# ---------------------------------------------------------------------------------
ifeq ($(TARGET_ENABLE_QC_AV_ENHANCEMENTS),true)

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
 LOCAL_SRC_FILES+=                                    \
     src/QCExtractor.cpp

 LOCAL_C_INCLUDES:=                                   \
     $(LOCAL_PATH)/../../common/inc                   \
     $(LOCAL_PATH)/inc                                \

 LOCAL_SHARED_LIBRARIES :=       \
         libutils                \
         libcutils               \
         libdl                   \
         libstagefright          \

 LOCAL_MODULE:= libExtendedExtractor

 LOCAL_MODULE_TAGS := optional

 LOCAL_MODULE_OWNER := qti
 LOCAL_PROPRIETARY_MODULE := true

 include $(BUILD_SHARED_LIBRARY)

endif #HAS_PREBUILT_PATH
endif #TARGET_ENABLE_QC_AV_ENHANCEMENTS
