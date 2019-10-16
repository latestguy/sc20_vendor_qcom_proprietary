LOCAL_PATH := $(call my-dir)
res_dirs := ../../Rowmbn/

ifeq ($(TARGET_BOARD_PLATFORM),msm8937)

include $(CLEAR_VARS)
MODEM_ROW_CONFIG_FILE := mcfg_sw.mbn
MODEM_ROW_CONFIG_FOLDER := $(TARGET_OUT)/vendor/Cambodia/data/modem_row_config/msm8917
$(shell mkdir -p $(MODEM_ROW_CONFIG_FOLDER))
$(shell cp -r $(addprefix $(LOCAL_PATH)/, $(res_dirs))/msm8917/$(MODEM_ROW_CONFIG_FILE) $(MODEM_ROW_CONFIG_FOLDER)/$(MODEM_ROW_CONFIG_FILE))

include $(CLEAR_VARS)
MODEM_ROW_CONFIG_FILE := mcfg_sw.mbn
MODEM_ROW_CONFIG_FOLDER := $(TARGET_OUT)/vendor/Cambodia/data/modem_row_config/msm8917_3
$(shell mkdir -p $(MODEM_ROW_CONFIG_FOLDER))
$(shell cp -r $(addprefix $(LOCAL_PATH)/, $(res_dirs))/msm8917_3/$(MODEM_ROW_CONFIG_FILE) $(MODEM_ROW_CONFIG_FOLDER)/$(MODEM_ROW_CONFIG_FILE))

include $(CLEAR_VARS)
MODEM_ROW_CONFIG_FILE := mcfg_sw.mbn
MODEM_ROW_CONFIG_FOLDER := $(TARGET_OUT)/vendor/Cambodia/data/modem_row_config/msm8920
$(shell mkdir -p $(MODEM_ROW_CONFIG_FOLDER))
$(shell cp -r $(addprefix $(LOCAL_PATH)/, $(res_dirs))/msm8920/$(MODEM_ROW_CONFIG_FILE) $(MODEM_ROW_CONFIG_FOLDER)/$(MODEM_ROW_CONFIG_FILE))

include $(CLEAR_VARS)
MODEM_ROW_CONFIG_FILE := mcfg_sw.mbn
MODEM_ROW_CONFIG_FOLDER := $(TARGET_OUT)/vendor/Cambodia/data/modem_row_config/msm8937
$(shell mkdir -p $(MODEM_ROW_CONFIG_FOLDER))
$(shell cp -r $(addprefix $(LOCAL_PATH)/, $(res_dirs))/msm8937/$(MODEM_ROW_CONFIG_FILE) $(MODEM_ROW_CONFIG_FOLDER)/$(MODEM_ROW_CONFIG_FILE))

include $(CLEAR_VARS)
MODEM_ROW_CONFIG_FILE := mcfg_sw.mbn
MODEM_ROW_CONFIG_FOLDER := $(TARGET_OUT)/vendor/Cambodia/data/modem_row_config/msm8937_3
$(shell mkdir -p $(MODEM_ROW_CONFIG_FOLDER))
$(shell cp -r $(addprefix $(LOCAL_PATH)/, $(res_dirs))/msm8937_3/$(MODEM_ROW_CONFIG_FILE) $(MODEM_ROW_CONFIG_FOLDER)/$(MODEM_ROW_CONFIG_FILE))

include $(CLEAR_VARS)
MODEM_ROW_CONFIG_FILE := mcfg_sw.mbn
MODEM_ROW_CONFIG_FOLDER := $(TARGET_OUT)/vendor/Cambodia/data/modem_row_config/msm8940
$(shell mkdir -p $(MODEM_ROW_CONFIG_FOLDER))
$(shell cp -r $(addprefix $(LOCAL_PATH)/, $(res_dirs))/msm8940/$(MODEM_ROW_CONFIG_FILE) $(MODEM_ROW_CONFIG_FOLDER)/$(MODEM_ROW_CONFIG_FILE))

else ifeq ($(TARGET_BOARD_PLATFORM),msm8909)

include $(CLEAR_VARS)
MODEM_ROW_CONFIG_FILE := mcfg_sw.mbn
MODEM_ROW_CONFIG_FOLDER := $(TARGET_OUT)/vendor/Cambodia/data/modem_row_config/msm8909
$(shell mkdir -p $(MODEM_ROW_CONFIG_FOLDER))
$(shell cp -r $(addprefix $(LOCAL_PATH)/, $(res_dirs))/msm8909/$(MODEM_ROW_CONFIG_FILE) $(MODEM_ROW_CONFIG_FOLDER)/$(MODEM_ROW_CONFIG_FILE))

include $(CLEAR_VARS)
MODEM_ROW_CONFIG_FILE := mcfg_sw.mbn
MODEM_ROW_CONFIG_FOLDER := $(TARGET_OUT)/vendor/Cambodia/data/modem_row_config/msm8909_3
$(shell mkdir -p $(MODEM_ROW_CONFIG_FOLDER))
$(shell cp -r $(addprefix $(LOCAL_PATH)/, $(res_dirs))/msm8909_3/$(MODEM_ROW_CONFIG_FILE) $(MODEM_ROW_CONFIG_FOLDER)/$(MODEM_ROW_CONFIG_FILE))

else ifeq ($(TARGET_BOARD_PLATFORM),msm8953)

include $(CLEAR_VARS)
MODEM_ROW_CONFIG_FILE := mcfg_sw.mbn
MODEM_ROW_CONFIG_FOLDER := $(TARGET_OUT)/vendor/Cambodia/data/modem_row_config/msm8953
$(shell mkdir -p $(MODEM_ROW_CONFIG_FOLDER))
$(shell cp -r $(addprefix $(LOCAL_PATH)/, $(res_dirs))/msm8953/$(MODEM_ROW_CONFIG_FILE) $(MODEM_ROW_CONFIG_FOLDER)/$(MODEM_ROW_CONFIG_FILE))

endif

