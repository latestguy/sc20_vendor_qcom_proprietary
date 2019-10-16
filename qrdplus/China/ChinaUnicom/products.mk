# Add ChinaUnicom Apps
PRODUCT_PACKAGES += \
    CuBrowserRes \
    CuDialerRes \
    CuStkRes \
    CuSettingsRes \
    CuSettingsProviderRes \
    CuSystemUIRes \
    CuGallery2Res \
    CuBrowserQuick \
    CuKeyguardRes \
    CuSimContactsRes \
    CuCalLocalAccountRes \
    WoRead

# CSVT
ifeq ($(TARGET_USES_CSVT),true)
PRODUCT_PACKAGES += \
    com.qti.videocall.permissions.xml \
    vtremoteservice

endif

LOCAL_PATH := vendor/qcom/proprietary/qrdplus/China/ChinaUnicom

# include the BootAnimation's products
-include $(LOCAL_PATH)/apps/BootAnimation/products.mk
