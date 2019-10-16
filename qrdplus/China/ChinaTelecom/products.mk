# Add ChinaTelecom Apps
PRODUCT_PACKAGES += \
    Mail189 \
    CtBrowserQuick \
    CtBrowserRes \
    CtDialerRes \
    CtStkRes \
    CtEmailRes \
    CtFrameworksRes \
    CtTeleServiceRes \
    CtMmsRes \
    CtSettingsRes \
    CtSettingsProviderRes \
    CtSystemUIRes \
    CtPhoneFeaturesRes \
    CtKeyguardRes \
    CtRoamingSettings \
    AutoRegistration \
    CtSimContactsRes \
    CtCalLocalAccountRes \
    CustomerService

# Marked as couldn't build sucess now.
#PRODUCT_PACKAGES += \
#    ApnSettings \

LOCAL_PATH := vendor/qcom/proprietary/qrdplus/China/ChinaTelecom

# include the BootAnimation's products
-include $(LOCAL_PATH)/apps/BootAnimation/products.mk

