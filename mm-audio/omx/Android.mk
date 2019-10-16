AUDIO_OMX := $(call my-dir)
ifneq ($(TARGET_SUPPORTS_WEARABLES),true)
include $(call all-subdir-makefiles)
endif
