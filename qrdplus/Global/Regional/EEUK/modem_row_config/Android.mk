LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
MODEM_ROW_CONFIG_FILE := mcfg_sw.mbn
MODEM_ROW_CONFIG_FOLDER := $(TARGET_OUT)/vendor/EEUK/data/modem_row_config
$(shell mkdir -p $(MODEM_ROW_CONFIG_FOLDER))
$(shell cp -r $(LOCAL_PATH)/$(MODEM_ROW_CONFIG_FILE) $(MODEM_ROW_CONFIG_FOLDER)/$(MODEM_ROW_CONFIG_FILE))
#########################################################################################################
