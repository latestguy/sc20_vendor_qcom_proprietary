LOCAL_PATH := $(call my-dir)

$(shell mkdir -p $(TARGET_OUT)/vendor/LatamClaroChile/system/etc)
$(shell cp -r $(LOCAL_PATH)/regional-apns-conf.xml $(TARGET_OUT)/vendor/LatamClaroChile/system/etc)
