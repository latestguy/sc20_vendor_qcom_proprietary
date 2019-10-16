
PRODUCT_PACKAGES += \
    CmccPowerFrameworksRes \
    CmccPowerCamera2Res \
    CmccPowerSettingsProviderRes \
    CmccSettingsProviderRes \
    10086cn \
    BackupReceiver \
    CmccCustomerService \
    CmccDialerRes \
    ChinaMobileFrameworksRes \
    CmccGallery2Res \
    CmccMmsRes \
    CmccMusicRes \
    CmccBrowserRes \
    CmccSettingsRes \
    CmccCalculatorRes \
    CmccCalendarRes \
    CmccSystemUIRes \
    CmccDeskClockRes \
    CmccBluetoothRes \
    CmccEmailRes \
    CmccCamera2Res \
    CmccFM2Res \
    libdmjni \
    ExtWifi \
    Backup \
    CmccSnapdragonCameraRes \
    CmccQuickSearchBoxRes \
    CmccSimContactsRes \
    CmccCalLocalAccountRes \
    BatterySaver

ifeq (0,1)
ifeq ($(TARGET_USES_PCI_RCS),true)

#RCS in ChinaMobile folder
RCS := NativeUI
RCS += PublicAccount
#RCS in other folders
RCS += librcs_pub
RCS += librcs_sip
RCS += librcs_msrp
RCS += librcs_media
RCS += librcs_jni
RCS += rcs_plugin_aidl_libs_gson_static.jar
RCS += rcs_plugin_aidl
RCS += rcs_plugin_aidl.xml
RCS += RcsService
RCS += RcsSystemService
RCS += device_api_aidl
RCS += device_api
RCS += device_api.xml
RCS += DeviceApiService

#RCS no ship
RCS_NO_SHIP += libbinaryByFounder
RCS_NO_SHIP += libqrcodedecoder
RCS_NO_SHIP += RcsPlugin
RCS_NO_SHIP += RcsGbaProxy
#RCS_NO_SHIP += cmccsso
RCS_NO_SHIP += CaiYinRCS
RCS_NO_SHIP += BiaoQingStore4Rcs_APK
RCS_NO_SHIP += OnlineBusinessHall
RCS_NO_SHIP += RcsMap

PRODUCT_PACKAGES += $(RCS)
PRODUCT_PACKAGES += $(RCS_NO_SHIP)
endif
endif

LOCAL_PATH := vendor/qcom/proprietary/qrdplus/ChinaMobile

# include the BootAnimation's products
-include $(LOCAL_PATH)/apps/BootAnimation/products.mk
# include the modem configuration products
-include $(LOCAL_PATH)/config/modem_config/products.mk
