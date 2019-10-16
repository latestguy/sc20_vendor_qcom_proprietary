LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

#################################################
PRE_LOAD_SPEC := .preloadspec
$(shell mkdir -p $(TARGET_OUT)/vendor/RJIL)
$(shell cp -r $(LOCAL_PATH)/$(PRE_LOAD_SPEC) $(TARGET_OUT)/vendor/RJIL/$(PRE_LOAD_SPEC))

#################################################
SPEC_PROP := vendor.prop
$(shell mkdir -p $(TARGET_OUT)/vendor/RJIL/system/vendor)
$(shell cp -r $(LOCAL_PATH)/$(SPEC_PROP) $(TARGET_OUT)/vendor/RJIL/system/vendor/$(SPEC_PROP))

#################################################
FS_PUBLIC_PARAM := fs_public_param.txt
$(shell mkdir -p $(TARGET_OUT)/vendor/RJIL/data/shared)
$(shell cp -r $(LOCAL_PATH)/$(FS_PUBLIC_PARAM) $(TARGET_OUT)/vendor/RJIL/data/shared/$(FS_PUBLIC_PARAM))