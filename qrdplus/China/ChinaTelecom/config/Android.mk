# Disable this makefile, After verify all commands can work, please remove the ifeq condition
ifeq (1,1)
LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

#################################################
PRE_LOAD_SPEC := .preloadspec
$(shell mkdir -p $(TARGET_OUT)/vendor/ChinaTelecom)
$(shell cp -r $(LOCAL_PATH)/$(PRE_LOAD_SPEC) $(TARGET_OUT)/vendor/ChinaTelecom/$(PRE_LOAD_SPEC))

#################################################
SPEC_PROP := vendor.prop
$(shell mkdir -p $(TARGET_OUT)/vendor/ChinaTelecom/system/vendor/)
$(shell cp -r $(LOCAL_PATH)/$(SPEC_PROP) $(TARGET_OUT)/vendor/ChinaTelecom/system/vendor/$(SPEC_PROP))

#################################################
EXCLUDE_LIST := exclude.list
$(shell mkdir -p $(TARGET_OUT)/vendor/ChinaTelecom)
$(shell cp -r $(LOCAL_PATH)/$(EXCLUDE_LIST) $(TARGET_OUT)/vendor/ChinaTelecom/$(EXCLUDE_LIST))

ifeq (0,1)
#################################################
GPS_CONF := gps.conf
GPS_CONF_FILE := $(PRODUCT_OUT)/system/etc/$(GPS_CONF)
GPS_CONF_CT_FILE := $(TARGET_OUT)"/vendor/ChinaTelecom/system/etc"
InstallCarrierFileList := $(PRODUCT_OUT)/installed-files.txt

InstallCTGPSConf: $(InstallCarrierFileList)
	@mkdir -p "$(GPS_CONF_CT_FILE)"
	@cp -rf "$(GPS_CONF_FILE)" "$(GPS_CONF_CT_FILE)/$(GPS_CONF)"
	@if [ -f  "$(GPS_CONF_FILE).bakforspec" ] ; then \
	    cp -rf "$(GPS_CONF_FILE).bakforspec" "$(GPS_CONF_CT_FILE)/$(GPS_CONF)" ;\
	 fi
	@sed -i 's/^SUPL_VER/# &/;s/^SUPL_HOST/# &/;s/^SUPL_PORT/# &/;s/^SGLTE_TARGET/# &/' "$(GPS_CONF_CT_FILE)/$(GPS_CONF)"

InstallCarrier: InstallCTGPSConf
endif
endif
