LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := mcfg_sw.mbn
LOCAL_SRC_FILES := $(LOCAL_MODULE)
LOCAL_MODULE_CLASS := LatamTelefonica
LOCAL_MODULE_PATH := $(TARGET_OUT)/vendor/LatamTelefonica/data/modem_config/
include $(BUILD_PREBUILT)
####################################################################################################
include $(CLEAR_VARS)
MODEM_CONFIG_FILE := mcfg_sw.mbn
MODEM_CONFIG_FOLDER := $(TARGET_OUT)/vendor/LatamTelefonicaArgentina/data/modem_config
$(shell mkdir -p $(MODEM_CONFIG_FOLDER))
$(shell cp -r $(LOCAL_PATH)/$(MODEM_CONFIG_FILE) $(MODEM_CONFIG_FOLDER)/$(MODEM_CONFIG_FILE))
####################################################################################################
include $(CLEAR_VARS)
MODEM_CONFIG_FILE := mcfg_sw.mbn
MODEM_CONFIG_FOLDER := $(TARGET_OUT)/vendor/LatamTelefonicaBrazil/data/modem_config
$(shell mkdir -p $(MODEM_CONFIG_FOLDER))
$(shell cp -r $(LOCAL_PATH)/$(MODEM_CONFIG_FILE) $(MODEM_CONFIG_FOLDER)/$(MODEM_CONFIG_FILE))
####################################################################################################
include $(CLEAR_VARS)
MODEM_CONFIG_FILE := mcfg_sw.mbn
MODEM_CONFIG_FOLDER := $(TARGET_OUT)/vendor/LatamTelefonicaChile/data/modem_config
$(shell mkdir -p $(MODEM_CONFIG_FOLDER))
$(shell cp -r $(LOCAL_PATH)/$(MODEM_CONFIG_FILE) $(MODEM_CONFIG_FOLDER)/$(MODEM_CONFIG_FILE))
####################################################################################################
include $(CLEAR_VARS)
MODEM_CONFIG_FILE := mcfg_sw.mbn
MODEM_CONFIG_FOLDER := $(TARGET_OUT)/vendor/LatamTelefonicaColombia/data/modem_config
$(shell mkdir -p $(MODEM_CONFIG_FOLDER))
$(shell cp -r $(LOCAL_PATH)/$(MODEM_CONFIG_FILE) $(MODEM_CONFIG_FOLDER)/$(MODEM_CONFIG_FILE))
####################################################################################################
include $(CLEAR_VARS)
MODEM_CONFIG_FILE := mcfg_sw.mbn
MODEM_CONFIG_FOLDER := $(TARGET_OUT)/vendor/LatamTelefonicaCostaRica/data/modem_config
$(shell mkdir -p $(MODEM_CONFIG_FOLDER))
$(shell cp -r $(LOCAL_PATH)/$(MODEM_CONFIG_FILE) $(MODEM_CONFIG_FOLDER)/$(MODEM_CONFIG_FILE))
####################################################################################################
include $(CLEAR_VARS)
MODEM_CONFIG_FILE := mcfg_sw.mbn
MODEM_CONFIG_FOLDER := $(TARGET_OUT)/vendor/LatamTelefonicaEcuador/data/modem_config
$(shell mkdir -p $(MODEM_CONFIG_FOLDER))
$(shell cp -r $(LOCAL_PATH)/$(MODEM_CONFIG_FILE) $(MODEM_CONFIG_FOLDER)/$(MODEM_CONFIG_FILE))
####################################################################################################
include $(CLEAR_VARS)
MODEM_CONFIG_FILE := mcfg_sw.mbn
MODEM_CONFIG_FOLDER := $(TARGET_OUT)/vendor/LatamTelefonicaElSalvador/data/modem_config
$(shell mkdir -p $(MODEM_CONFIG_FOLDER))
$(shell cp -r $(LOCAL_PATH)/$(MODEM_CONFIG_FILE) $(MODEM_CONFIG_FOLDER)/$(MODEM_CONFIG_FILE))
####################################################################################################
include $(CLEAR_VARS)
MODEM_CONFIG_FILE := mcfg_sw.mbn
MODEM_CONFIG_FOLDER := $(TARGET_OUT)/vendor/LatamTelefonicaGuatemala/data/modem_config
$(shell mkdir -p $(MODEM_CONFIG_FOLDER))
$(shell cp -r $(LOCAL_PATH)/$(MODEM_CONFIG_FILE) $(MODEM_CONFIG_FOLDER)/$(MODEM_CONFIG_FILE))
####################################################################################################
include $(CLEAR_VARS)
MODEM_CONFIG_FILE := mcfg_sw.mbn
MODEM_CONFIG_FOLDER := $(TARGET_OUT)/vendor/LatamTelefonicaMexico/data/modem_config
$(shell mkdir -p $(MODEM_CONFIG_FOLDER))
$(shell cp -r $(LOCAL_PATH)/$(MODEM_CONFIG_FILE) $(MODEM_CONFIG_FOLDER)/$(MODEM_CONFIG_FILE))
####################################################################################################
include $(CLEAR_VARS)
MODEM_CONFIG_FILE := mcfg_sw.mbn
MODEM_CONFIG_FOLDER := $(TARGET_OUT)/vendor/LatamTelefonicaNicaragua/data/modem_config
$(shell mkdir -p $(MODEM_CONFIG_FOLDER))
$(shell cp -r $(LOCAL_PATH)/$(MODEM_CONFIG_FILE) $(MODEM_CONFIG_FOLDER)/$(MODEM_CONFIG_FILE))
####################################################################################################
include $(CLEAR_VARS)
MODEM_CONFIG_FILE := mcfg_sw.mbn
MODEM_CONFIG_FOLDER := $(TARGET_OUT)/vendor/LatamTelefonicaPanama/data/modem_config
$(shell mkdir -p $(MODEM_CONFIG_FOLDER))
$(shell cp -r $(LOCAL_PATH)/$(MODEM_CONFIG_FILE) $(MODEM_CONFIG_FOLDER)/$(MODEM_CONFIG_FILE))
####################################################################################################
include $(CLEAR_VARS)
MODEM_CONFIG_FILE := mcfg_sw.mbn
MODEM_CONFIG_FOLDER := $(TARGET_OUT)/vendor/LatamTelefonicaPeru/data/modem_config
$(shell mkdir -p $(MODEM_CONFIG_FOLDER))
$(shell cp -r $(LOCAL_PATH)/$(MODEM_CONFIG_FILE) $(MODEM_CONFIG_FOLDER)/$(MODEM_CONFIG_FILE))
####################################################################################################
include $(CLEAR_VARS)
MODEM_CONFIG_FILE := mcfg_sw.mbn
MODEM_CONFIG_FOLDER := $(TARGET_OUT)/vendor/LatamTelefonicaUruguay/data/modem_config
$(shell mkdir -p $(MODEM_CONFIG_FOLDER))
$(shell cp -r $(LOCAL_PATH)/$(MODEM_CONFIG_FILE) $(MODEM_CONFIG_FOLDER)/$(MODEM_CONFIG_FILE))
####################################################################################################
include $(CLEAR_VARS)
MODEM_CONFIG_FILE := mcfg_sw.mbn
MODEM_CONFIG_FOLDER := $(TARGET_OUT)/vendor/LatamTelefonicaVenezuela/data/modem_config
$(shell mkdir -p $(MODEM_CONFIG_FOLDER))
$(shell cp -r $(LOCAL_PATH)/$(MODEM_CONFIG_FILE) $(MODEM_CONFIG_FOLDER)/$(MODEM_CONFIG_FILE))
####################################################################################################
