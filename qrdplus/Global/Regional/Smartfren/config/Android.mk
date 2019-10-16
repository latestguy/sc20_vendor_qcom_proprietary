LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
 
#################################################
PRE_LOAD_SPEC := .preloadspec
$(shell mkdir -p $(TARGET_OUT)/vendor/Smartfren)
$(shell cp -r $(LOCAL_PATH)/$(PRE_LOAD_SPEC) $(TARGET_OUT)/vendor/Smartfren/$(PRE_LOAD_SPEC))

#################################################
SPEC_PROP := vendor.prop
$(shell mkdir -p $(TARGET_OUT)/vendor/Smartfren/system/vendor/)
$(shell cp -r $(LOCAL_PATH)/$(SPEC_PROP) $(TARGET_OUT)/vendor/Smartfren/system/vendor/$(SPEC_PROP))

#################################################
EXCLUDE_LIST := exclude.list
$(shell mkdir -p $(TARGET_OUT)/vendor/Smartfren)
$(shell cp -r $(LOCAL_PATH)/$(EXCLUDE_LIST) $(TARGET_OUT)/vendor/Smartfren/$(EXCLUDE_LIST))

#################################################
$(shell mkdir -p $(TARGET_OUT)/vendor/Smartfren/system/etc/hostapd)
$(shell cp $(TARGET_OUT_ETC)/hostapd/hostapd_default.conf \
        $(TARGET_OUT)/vendor/Smartfren/system/etc/hostapd/hostapd_default.conf)
$(shell sed 's/max_num_sta=[0-9]*/max_num_sta=5/' -i \
        $(TARGET_OUT)/vendor/Smartfren/system/etc/hostapd/hostapd_default.conf)
