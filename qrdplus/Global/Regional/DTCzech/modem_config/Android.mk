LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

MODEM_CONFIG_FILE := mcfg_sw.mbn
MODEM_CONFIG_FOLDER := $(TARGET_OUT)/vendor/DTCzech/data/modem_config
$(shell mkdir -p $(MODEM_CONFIG_FOLDER))
$(shell cp -r $(LOCAL_PATH)/$(MODEM_CONFIG_FILE) $(MODEM_CONFIG_FOLDER)/$(MODEM_CONFIG_FILE))
