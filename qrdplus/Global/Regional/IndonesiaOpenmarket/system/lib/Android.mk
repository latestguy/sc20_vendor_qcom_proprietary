LOCAL_PATH := $(call my-dir)

$(shell mkdir -p $(TARGET_OUT)/vendor/IndonesiaOpenmarket/system/lib)
$(shell cp -r $(LOCAL_PATH)/*.so $(TARGET_OUT)/vendor/IndonesiaOpenmarket/system/lib)
