LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

#################################################
PRE_LOAD_SPEC := .preloadspec
$(shell mkdir -p $(TARGET_OUT)/vendor/OrangeCommon)
$(shell cp -r $(LOCAL_PATH)/$(PRE_LOAD_SPEC) $(TARGET_OUT)/vendor/OrangeCommon/$(PRE_LOAD_SPEC))

#################################################
SPEC_PROP := vendor.prop
$(shell mkdir -p $(TARGET_OUT)/vendor/OrangeCommon/system/vendor/)
$(shell cp -r $(LOCAL_PATH)/$(SPEC_PROP) $(TARGET_OUT)/vendor/OrangeCommon/system/vendor/$(SPEC_PROP))
