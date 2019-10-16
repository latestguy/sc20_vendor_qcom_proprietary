LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
MODEM_ROW_CONFIG_FILE := mcfg_sw.mbn
MODEM_ROW_CONFIG_FOLDER := $(TARGET_OUT)/vendor/OrangeCommon/data/modem_row_config
$(shell mkdir -p $(MODEM_ROW_CONFIG_FOLDER))
$(shell cp -r $(LOCAL_PATH)/$(MODEM_ROW_CONFIG_FILE) $(MODEM_ROW_CONFIG_FOLDER)/$(MODEM_ROW_CONFIG_FILE))

ifeq (0,1)
#########################################################################################################
include $(CLEAR_VARS)
MODEM_ROW_CONFIG_FILE := mcfg_sw.mbn
MODEM_ROW_CONFIG_FOLDER := $(TARGET_OUT)/vendor/OrangeFrance/data/modem_row_config
$(shell mkdir -p $(MODEM_ROW_CONFIG_FOLDER))
$(shell cp -r $(LOCAL_PATH)/$(MODEM_ROW_CONFIG_FILE) $(MODEM_ROW_CONFIG_FOLDER)/$(MODEM_ROW_CONFIG_FILE))
#########################################################################################################
include $(CLEAR_VARS)
MODEM_ROW_CONFIG_FILE := mcfg_sw.mbn
MODEM_ROW_CONFIG_FOLDER := $(TARGET_OUT)/vendor/OrangePoland/data/modem_row_config
$(shell mkdir -p $(MODEM_ROW_CONFIG_FOLDER))
$(shell cp -r $(LOCAL_PATH)/$(MODEM_ROW_CONFIG_FILE) $(MODEM_ROW_CONFIG_FOLDER)/$(MODEM_ROW_CONFIG_FILE))
#########################################################################################################
include $(CLEAR_VARS)
MODEM_ROW_CONFIG_FILE := mcfg_sw.mbn
MODEM_ROW_CONFIG_FOLDER := $(TARGET_OUT)/vendor/OrangeRomania/data/modem_row_config
$(shell mkdir -p $(MODEM_ROW_CONFIG_FOLDER))
$(shell cp -r $(LOCAL_PATH)/$(MODEM_ROW_CONFIG_FILE) $(MODEM_ROW_CONFIG_FOLDER)/$(MODEM_ROW_CONFIG_FILE))
#########################################################################################################
include $(CLEAR_VARS)
MODEM_ROW_CONFIG_FILE := mcfg_sw.mbn
MODEM_ROW_CONFIG_FOLDER := $(TARGET_OUT)/vendor/OrangeSlovakia/data/modem_row_config
$(shell mkdir -p $(MODEM_ROW_CONFIG_FOLDER))
$(shell cp -r $(LOCAL_PATH)/$(MODEM_ROW_CONFIG_FILE) $(MODEM_ROW_CONFIG_FOLDER)/$(MODEM_ROW_CONFIG_FILE))
#########################################################################################################
include $(CLEAR_VARS)
MODEM_ROW_CONFIG_FILE := mcfg_sw.mbn
MODEM_ROW_CONFIG_FOLDER := $(TARGET_OUT)/vendor/OrangeSpain/data/modem_row_config
$(shell mkdir -p $(MODEM_ROW_CONFIG_FOLDER))
$(shell cp -r $(LOCAL_PATH)/$(MODEM_ROW_CONFIG_FILE) $(MODEM_ROW_CONFIG_FOLDER)/$(MODEM_ROW_CONFIG_FILE))
#########################################################################################################
include $(CLEAR_VARS)
MODEM_ROW_CONFIG_FILE := mcfg_sw.mbn
MODEM_ROW_CONFIG_FOLDER := $(TARGET_OUT)/vendor/OrangeBelgium/data/modem_row_config
$(shell mkdir -p $(MODEM_ROW_CONFIG_FOLDER))
$(shell cp -r $(LOCAL_PATH)/$(MODEM_ROW_CONFIG_FILE) $(MODEM_ROW_CONFIG_FOLDER)/$(MODEM_ROW_CONFIG_FILE))
#########################################################################################################
include $(CLEAR_VARS)
MODEM_ROW_CONFIG_FILE := mcfg_sw.mbn
MODEM_ROW_CONFIG_FOLDER := $(TARGET_OUT)/vendor/OrangeMoldavia/data/modem_row_config
$(shell mkdir -p $(MODEM_ROW_CONFIG_FOLDER))
$(shell cp -r $(LOCAL_PATH)/$(MODEM_ROW_CONFIG_FILE) $(MODEM_ROW_CONFIG_FOLDER)/$(MODEM_ROW_CONFIG_FILE))
#########################################################################################################
endif
