LOCAL_PATH := $(call my-dir)

ifeq ($(TARGET_BOARD_PLATFORM),msm8937)

include $(CLEAR_VARS)
MODEM_CONFIG_FILE := mcfg_sw.mbn
MODEM_CONFIG_FOLDER := $(TARGET_OUT)/vendor/ThailandOpenMarket/data/modem_config/msm8917
$(shell mkdir -p $(MODEM_CONFIG_FOLDER))
$(shell cp -r $(LOCAL_PATH)/msm8917/$(MODEM_CONFIG_FILE) $(MODEM_CONFIG_FOLDER)/$(MODEM_CONFIG_FILE))

include $(CLEAR_VARS)
MODEM_CONFIG_FILE := mcfg_sw.mbn
MODEM_CONFIG_FOLDER := $(TARGET_OUT)/vendor/ThailandOpenMarket/data/modem_config/msm8917_3
$(shell mkdir -p $(MODEM_CONFIG_FOLDER))
$(shell cp -r $(LOCAL_PATH)/msm8917_3/$(MODEM_CONFIG_FILE) $(MODEM_CONFIG_FOLDER)/$(MODEM_CONFIG_FILE))

include $(CLEAR_VARS)
MODEM_CONFIG_FILE := mcfg_sw.mbn
MODEM_CONFIG_FOLDER := $(TARGET_OUT)/vendor/ThailandOpenMarket/data/modem_config/msm8920
$(shell mkdir -p $(MODEM_CONFIG_FOLDER))
$(shell cp -r $(LOCAL_PATH)/msm8920/$(MODEM_CONFIG_FILE) $(MODEM_CONFIG_FOLDER)/$(MODEM_CONFIG_FILE))

include $(CLEAR_VARS)
MODEM_CONFIG_FILE := mcfg_sw.mbn
MODEM_CONFIG_FOLDER := $(TARGET_OUT)/vendor/ThailandOpenMarket/data/modem_config/msm8937
$(shell mkdir -p $(MODEM_CONFIG_FOLDER))
$(shell cp -r $(LOCAL_PATH)/msm8937/$(MODEM_CONFIG_FILE) $(MODEM_CONFIG_FOLDER)/$(MODEM_CONFIG_FILE))

include $(CLEAR_VARS)
MODEM_CONFIG_FILE := mcfg_sw.mbn
MODEM_CONFIG_FOLDER := $(TARGET_OUT)/vendor/ThailandOpenMarket/data/modem_config/msm8937_3
$(shell mkdir -p $(MODEM_CONFIG_FOLDER))
$(shell cp -r $(LOCAL_PATH)/msm8937_3/$(MODEM_CONFIG_FILE) $(MODEM_CONFIG_FOLDER)/$(MODEM_CONFIG_FILE))

include $(CLEAR_VARS)
MODEM_CONFIG_FILE := mcfg_sw.mbn
MODEM_CONFIG_FOLDER := $(TARGET_OUT)/vendor/ThailandOpenMarket/data/modem_config/msm8940
$(shell mkdir -p $(MODEM_CONFIG_FOLDER))
$(shell cp -r $(LOCAL_PATH)/msm8940/$(MODEM_CONFIG_FILE) $(MODEM_CONFIG_FOLDER)/$(MODEM_CONFIG_FILE))

else ifeq ($(TARGET_BOARD_PLATFORM),msm8909)

include $(CLEAR_VARS)
MODEM_CONFIG_FILE := mcfg_sw.mbn
MODEM_CONFIG_FOLDER := $(TARGET_OUT)/vendor/ThailandOpenMarket/data/modem_config/msm8909
$(shell mkdir -p $(MODEM_CONFIG_FOLDER))
$(shell cp -r $(LOCAL_PATH)/msm8909/$(MODEM_CONFIG_FILE) $(MODEM_CONFIG_FOLDER)/$(MODEM_CONFIG_FILE))

include $(CLEAR_VARS)
MODEM_CONFIG_FILE := mcfg_sw.mbn
MODEM_CONFIG_FOLDER := $(TARGET_OUT)/vendor/ThailandOpenMarket/data/modem_config/msm8909_3
$(shell mkdir -p $(MODEM_CONFIG_FOLDER))
$(shell cp -r $(LOCAL_PATH)/msm8909_3/$(MODEM_CONFIG_FILE) $(MODEM_CONFIG_FOLDER)/$(MODEM_CONFIG_FILE))

else ifeq ($(TARGET_BOARD_PLATFORM),msm8953)

include $(CLEAR_VARS)
MODEM_CONFIG_FILE := mcfg_sw.mbn
MODEM_CONFIG_FOLDER := $(TARGET_OUT)/vendor/ThailandOpenMarket/data/modem_config/msm8953
$(shell mkdir -p $(MODEM_CONFIG_FOLDER))
$(shell cp -r $(LOCAL_PATH)/msm8953/$(MODEM_CONFIG_FILE) $(MODEM_CONFIG_FOLDER)/$(MODEM_CONFIG_FILE))

endif

