ifneq ($(strip $(USES_REGIONALIZATION_PARTITIONS)),)
ifneq ($(strip $(USES_REGIONALIZATION_PARTITIONS)),system)
ifneq ($(strip $(USES_REGIONALIZATION_PARTITIONS)),vendor)
$(warning "Start to backup Android.mk  ")

REGIONAL_PATH := $(ANDROID_BUILD_TOP)/vendor/qcom/proprietary/qrdplus/Global/Regional

$(shell mv $(REGIONAL_PATH)/OrangeBelgium/res/Frameworks/Android.mk $(REGIONAL_PATH)/OrangeBelgium/res/Frameworks/Android.mk.bak)
$(shell mv $(REGIONAL_PATH)/OrangeMoldavia/res/Frameworks/Android.mk $(REGIONAL_PATH)/OrangeMoldavia/res/Frameworks/Android.mk.bak)
$(shell mv $(REGIONAL_PATH)/TelecomItaliaMobile/res/Frameworks/Android.mk $(REGIONAL_PATH)/TelecomItaliaMobile/res/Frameworks/Android.mk.bak)
$(shell mv $(REGIONAL_PATH)/LatamAMX/res/Frameworks/Android.mk $(REGIONAL_PATH)/LatamAMX/res/Frameworks/Android.mk.bak)
$(shell mv $(REGIONAL_PATH)/LatamTelcelMexico/res/Frameworks/Android.mk $(REGIONAL_PATH)/LatamTelcelMexico/res/Frameworks/Android.mk.bak)
$(shell mv $(REGIONAL_PATH)/CherryPhilippines/res/Frameworks/Android.mk $(REGIONAL_PATH)/CherryPhilippines/res/Frameworks/Android.mk.bak)
$(shell mv $(REGIONAL_PATH)/CherryPhilippines/res/Browser/Android.mk $(REGIONAL_PATH)/CherryPhilippines/res/Browser/Android.mk.bak)
$(shell mv $(REGIONAL_PATH)/CherryThailand/res/Frameworks/Android.mk $(REGIONAL_PATH)/CherryThailand/res/Frameworks/Android.mk.bak)
$(shell mv $(REGIONAL_PATH)/CherryThailand/res/Browser/Android.mk $(REGIONAL_PATH)/CherryThailand/res/Browser/Android.mk.bak)
$(shell mv $(REGIONAL_PATH)/CherryMyanmar/res/Frameworks/Android.mk $(REGIONAL_PATH)/CherryMyanmar/res/Frameworks/Android.mk.bak)
$(shell mv $(REGIONAL_PATH)/CherryMyanmar/res/Browser/Android.mk $(REGIONAL_PATH)/CherryMyanmar/res/Browser/Android.mk.bak)
$(shell mv $(REGIONAL_PATH)/CherryLaos/res/Frameworks/Android.mk $(REGIONAL_PATH)/CherryLaos/res/Frameworks/Android.mk.bak)
$(shell mv $(REGIONAL_PATH)/CherryLaos/res/Browser/Android.mk $(REGIONAL_PATH)/CherryLaos/res/Browser/Android.mk.bak)
$(shell mv $(REGIONAL_PATH)/CherryCambodia/res/Frameworks/Android.mk $(REGIONAL_PATH)/CherryCambodia/res/Frameworks/Android.mk.bak)
$(shell mv $(REGIONAL_PATH)/CherryCambodia/res/Browser/Android.mk $(REGIONAL_PATH)/CherryCambodia/res/Browser/Android.mk.bak)
$(shell mv $(REGIONAL_PATH)/CherryCommon/res/WallpaperPicker/Android.mk $(REGIONAL_PATH)/CherryCommon/res/WallpaperPicker/Android.mk.bak)
$(shell mv $(REGIONAL_PATH)/LatamClaroBrazil/res/Frameworks/Android.mk $(REGIONAL_PATH)/LatamClaroBrazil/res/Frameworks/Android.mk.bak)
$(shell mv $(REGIONAL_PATH)/LatamClaroChile/res/Frameworks/Android.mk $(REGIONAL_PATH)/LatamClaroChile/res/Frameworks/Android.mk.bak)
$(shell mv $(REGIONAL_PATH)/LatamClaroPeru/res/Frameworks/Android.mk $(REGIONAL_PATH)/LatamClaroPeru/res/Frameworks/Android.mk.bak)
$(shell mv $(REGIONAL_PATH)/LatamClaroColombia/res/Frameworks/Android.mk $(REGIONAL_PATH)/LatamClaroColombia/res/Frameworks/Android.mk.bak)

# For Clean
CleanPollution: InstallCarrier
	@echo "#### Start to clean backup Android.mk! ####"
	@if [ -f "$(REGIONAL_PATH)/OrangeBelgium/res/Frameworks/Android.mk.bak" ] ; then \
        mv $(REGIONAL_PATH)/OrangeBelgium/res/Frameworks/Android.mk.bak $(REGIONAL_PATH)/OrangeBelgium/res/Frameworks/Android.mk ;\
    fi
	@if [ -f "$(REGIONAL_PATH)/OrangeMoldavia/res/Frameworks/Android.mk.bak" ] ; then \
        mv $(REGIONAL_PATH)/OrangeMoldavia/res/Frameworks/Android.mk.bak $(REGIONAL_PATH)/OrangeMoldavia/res/Frameworks/Android.mk ;\
    fi
	@if [ -f "$(REGIONAL_PATH)/TelecomItaliaMobile/res/Frameworks/Android.mk.bak" ] ; then \
        mv $(REGIONAL_PATH)/TelecomItaliaMobile/res/Frameworks/Android.mk.bak $(REGIONAL_PATH)/TelecomItaliaMobile/res/Frameworks/Android.mk ;\
    fi
	@if [ -f "$(REGIONAL_PATH)/CherryPhilippines/res/Frameworks/Android.mk.bak" ] ; then \
        mv $(REGIONAL_PATH)/CherryPhilippines/res/Frameworks/Android.mk.bak $(REGIONAL_PATH)/CherryPhilippines/res/Frameworks/Android.mk ;\
    fi
	@if [ -f "$(REGIONAL_PATH)/CherryPhilippines/res/Browser/Android.mk.bak" ] ; then \
        mv $(REGIONAL_PATH)/CherryPhilippines/res/Browser/Android.mk.bak $(REGIONAL_PATH)/CherryPhilippines/res/Browser/Android.mk ;\
    fi
	@if [ -f "$(REGIONAL_PATH)/CherryThailand/res/Frameworks/Android.mk.bak" ] ; then \
        mv $(REGIONAL_PATH)/CherryThailand/res/Frameworks/Android.mk.bak $(REGIONAL_PATH)/CherryThailand/res/Frameworks/Android.mk ;\
    fi
	@if [ -f "$(REGIONAL_PATH)/CherryThailand/res/Browser/Android.mk.bak" ] ; then \
        mv $(REGIONAL_PATH)/CherryThailand/res/Browser/Android.mk.bak $(REGIONAL_PATH)/CherryThailand/res/Browser/Android.mk ;\
    fi
	@if [ -f "$(REGIONAL_PATH)/CherryMyanmar/res/Frameworks/Android.mk.bak" ] ; then \
        mv $(REGIONAL_PATH)/CherryMyanmar/res/Frameworks/Android.mk.bak $(REGIONAL_PATH)/CherryMyanmar/res/Frameworks/Android.mk ;\
    fi
	@if [ -f "$(REGIONAL_PATH)/CherryMyanmar/res/Browser/Android.mk.bak" ] ; then \
        mv $(REGIONAL_PATH)/CherryMyanmar/res/Browser/Android.mk.bak $(REGIONAL_PATH)/CherryMyanmar/res/Browser/Android.mk ;\
    fi
	@if [ -f "$(REGIONAL_PATH)/CherryLaos/res/Frameworks/Android.mk.bak" ] ; then \
        mv $(REGIONAL_PATH)/CherryLaos/res/Frameworks/Android.mk.bak $(REGIONAL_PATH)/CherryLaos/res/Frameworks/Android.mk ;\
    fi
	@if [ -f "$(REGIONAL_PATH)/CherryLaos/res/Browser/Android.mk.bak" ] ; then \
        mv $(REGIONAL_PATH)/CherryLaos/res/Browser/Android.mk.bak $(REGIONAL_PATH)/CherryLaos/res/Browser/Android.mk ;\
    fi
	@if [ -f "$(REGIONAL_PATH)/CherryCambodia/res/Frameworks/Android.mk.bak" ] ; then \
        mv $(REGIONAL_PATH)/CherryCambodia/res/Frameworks/Android.mk.bak $(REGIONAL_PATH)/CherryCambodia/res/Frameworks/Android.mk ;\
    fi
	@if [ -f "$(REGIONAL_PATH)/CherryCambodia/res/Browser/Android.mk.bak" ] ; then \
        mv $(REGIONAL_PATH)/CherryCambodia/res/Browser/Android.mk.bak $(REGIONAL_PATH)/CherryCambodia/res/Browser/Android.mk ;\
    fi
	@if [ -f "$(REGIONAL_PATH)/CherryCommon/res/WallpaperPicker/Android.mk.bak" ] ; then \
        mv $(REGIONAL_PATH)/CherryCommon/res/WallpaperPicker/Android.mk.bak $(REGIONAL_PATH)/CherryCommon/res/WallpaperPicker/Android.mk ;\
    fi
systemimage: CleanPollution
endif
endif
endif

ifeq ($(strip $(TARGET_USES_QTIC_REGIONAL)),true)
REGIONAL_PATH := $(call my-dir)
$(warning $(shell $(REGIONAL_PATH)/carrier_spec_config_parser.py -nl))
#include $(call all-subdir-makefiles)
include $(REGIONAL_PATH)/EUCommon/Android.mk
include $(REGIONAL_PATH)/TelefonicaGermany/Android.mk
include $(REGIONAL_PATH)/IndiaCommon/Android.mk
include $(REGIONAL_PATH)/RJIL/Android.mk
include $(REGIONAL_PATH)/H3GUK/Android.mk
include $(REGIONAL_PATH)/H3GItalia/Android.mk
include $(REGIONAL_PATH)/OrangeCommon/Android.mk
include $(REGIONAL_PATH)/OrangeMoldavia/Android.mk
include $(REGIONAL_PATH)/OrangeBelgium/Android.mk
include $(REGIONAL_PATH)/OrangeFrance/Android.mk
include $(REGIONAL_PATH)/OrangeRomania/Android.mk
include $(REGIONAL_PATH)/OrangeSpain/Android.mk
include $(REGIONAL_PATH)/OrangePoland/Android.mk
include $(REGIONAL_PATH)/OrangeSlovakia/Android.mk
include $(REGIONAL_PATH)/TelecomItaliaMobile/Android.mk
include $(REGIONAL_PATH)/IndonesiaOpenmarket/Android.mk
include $(REGIONAL_PATH)/ThailandOpenMarket/Android.mk
include $(REGIONAL_PATH)/NorthAmerica/Android.mk
include $(REGIONAL_PATH)/VodafoneGroup/Android.mk
include $(REGIONAL_PATH)/VodafoneGermany/Android.mk
include $(REGIONAL_PATH)/VodafoneUK/Android.mk
include $(REGIONAL_PATH)/VodafoneGreece/Android.mk
include $(REGIONAL_PATH)/VodafoneTurkey/Android.mk
include $(REGIONAL_PATH)/VodafoneCzech/Android.mk
include $(REGIONAL_PATH)/VodafoneHungary/Android.mk
include $(REGIONAL_PATH)/VodafoneIreland/Android.mk
include $(REGIONAL_PATH)/VodafoneNetherlands/Android.mk
include $(REGIONAL_PATH)/VodafoneIT/Android.mk
include $(REGIONAL_PATH)/VodafoneSouthAfrica/Android.mk
include $(REGIONAL_PATH)/VodafoneES/Android.mk
include $(REGIONAL_PATH)/VodafonePT/Android.mk
include $(REGIONAL_PATH)/LatamAMX/Android.mk
include $(REGIONAL_PATH)/LatamTelcelMexico/Android.mk
include $(REGIONAL_PATH)/LatamClaroPeru/Android.mk
include $(REGIONAL_PATH)/LatamClaroBrazil/Android.mk
include $(REGIONAL_PATH)/LatamClaroColombia/Android.mk
include $(REGIONAL_PATH)/LatamClaroChile/Android.mk
include $(REGIONAL_PATH)/LatamTelefonica/Android.mk
include $(REGIONAL_PATH)/LatamTelefonicaArgentina/Android.mk
include $(REGIONAL_PATH)/LatamTelefonicaBrazil/Android.mk
include $(REGIONAL_PATH)/LatamTelefonicaChile/Android.mk
include $(REGIONAL_PATH)/LatamTelefonicaColombia/Android.mk
include $(REGIONAL_PATH)/LatamTelefonicaCostaRica/Android.mk
include $(REGIONAL_PATH)/LatamTelefonicaEcuador/Android.mk
include $(REGIONAL_PATH)/LatamTelefonicaElSalvador/Android.mk
include $(REGIONAL_PATH)/LatamTelefonicaGuatemala/Android.mk
include $(REGIONAL_PATH)/LatamTelefonicaMexico/Android.mk
include $(REGIONAL_PATH)/LatamTelefonicaNicaragua/Android.mk
include $(REGIONAL_PATH)/LatamTelefonicaPanama/Android.mk
include $(REGIONAL_PATH)/LatamTelefonicaPeru/Android.mk
include $(REGIONAL_PATH)/LatamTelefonicaUruguay/Android.mk
include $(REGIONAL_PATH)/LatamTelefonicaVenezuela/Android.mk
include $(REGIONAL_PATH)/TelefonicaSpain/Android.mk
include $(REGIONAL_PATH)/CherryCambodia/Android.mk
include $(REGIONAL_PATH)/CherryCommon/Android.mk
include $(REGIONAL_PATH)/CherryLaos/Android.mk
include $(REGIONAL_PATH)/CherryMyanmar/Android.mk
include $(REGIONAL_PATH)/CherryPhilippines/Android.mk
include $(REGIONAL_PATH)/CherryThailand/Android.mk
include $(REGIONAL_PATH)/Cambodia/Android.mk
include $(REGIONAL_PATH)/Laos/Android.mk
include $(REGIONAL_PATH)/MalaysiaOpenMarket/Android.mk
include $(REGIONAL_PATH)/PhilippinesOpenMarket/Android.mk
include $(REGIONAL_PATH)/VietnamOpenMarket/Android.mk
include $(REGIONAL_PATH)/DTGermany/Android.mk
include $(REGIONAL_PATH)/EEEU/Android.mk
include $(REGIONAL_PATH)/LatamBrazil/Android.mk
endif

#PresetPackList := Default ChinaUnicom ChinaTelecom ChinaMobile CmccPower CTA Cambodia DTAustria DTCommon DTCroazia DTCzech DTGermany DTGreece DTHungary DTMacedonia DTMontenegro DTNetherlands DTPoland DTRomania DTSlovakia EEUK EUCommon IndonesiaOpenmarket Laos LatamBrazil LatamTelefonica LatamTelefonicaArgentina LatamTelefonicaBrazil LatamTelefonicaChile LatamTelefonicaColombia LatamTelefonicaCostaRica LatamTelefonicaEcuador LatamTelefonicaElSalvador LatamTelefonicaGuatemala LatamTelefonicaMexico LatamTelefonicaNicaragua LatamTelefonicaPanama LatamTelefonicaPeru LatamTelefonicaUruguay LatamTelefonicaVenezuela MalaysiaOpenMarket NorthAmerica PhilippinesOpenMarket RussiaOpen TelefonicaGermany TelefonicaSpain ThailandOpenMarket TurkeyOpen VodafoneGermany VodafoneUK

ifeq ($(call is-board-platform-in-list, msm8909),true)
PresetPackList := Default ChinaUnicom ChinaTelecom ChinaMobile EUCommon TelefonicaGermany VodafoneGermany VodafoneUK H3GUK H3GItalia RJIL ThailandOpenMarket NorthAmerica
else
PresetPackList := Default ChinaUnicom ChinaTelecom ChinaMobile EUCommon TelefonicaGermany VodafoneGermany VodafoneUK H3GUK H3GItalia IndonesiaOpenmarket ThailandOpenMarket NorthAmerica
endif

#Preset Regional packs for perf build
PresetPacksToPerf: $(INTERNAL_BOOTIMAGE_FILES)
	@for path in `find $(PRODUCT_OUT)/system/vendor -name ".preloadspec"` ; do \
       tmp="$${path%\/.preloadspec}" ;\
       pack="$${tmp##*\/}" ;\
       if [ "$$pack" != "" ] ; then \
         flag="del" ;\
         for presetpack in $(PresetPackList) ; do \
           if [ "$$presetpack" == "$$pack" ] ; then \
             echo "Keep $(PRODUCT_OUT)/system/vendor/$$pack for perf ..." ;\
             flag="keep" ;\
             break ;\
           fi ;\
         done ;\
         if [ "$$flag" != "keep" ] ; then \
           echo "Remove $(PRODUCT_OUT)/system/vendor/$$pack for perf ..." ;\
           rm -rvf $(PRODUCT_OUT)/system/vendor/$$pack ;\
        fi ;\
       fi ;\
     done

ifneq ($(strip $(filter %perf_defconfig,$(KERNEL_DEFCONFIG))),)
bootimage: PresetPacksToPerf
endif
