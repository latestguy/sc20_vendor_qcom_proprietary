# Disable include all subdir makefiles, If any one module can normal compile, please include module makefile in here.
#include $(call all-subdir-makefiles)

BASE_PATH := $(call my-dir)
include $(BASE_PATH)/ExtSettings/Android.mk
include $(BASE_PATH)/TimerSwitchSettings/Android.mk
include $(BASE_PATH)/BatterySaver/Android.mk
include $(BASE_PATH)/CalendarWidget/Android.mk
include $(BASE_PATH)/LunarInfoProvider/Android.mk
include $(BASE_PATH)/NetworkControl/Android.mk
include $(BASE_PATH)/NotePad/Android.mk
include $(BASE_PATH)/PhoneFeatures/Android.mk
include $(BASE_PATH)/QuickSettingsService/Android.mk
include $(BASE_PATH)/StorageCleaner/Android.mk
include $(BASE_PATH)/WapPush/Android.mk
include $(BASE_PATH)/LauncherUnreadService/Android.mk
include $(BASE_PATH)/DeviceInfo/Android.mk
include $(BASE_PATH)/SmartSearch/Android.mk
include $(BASE_PATH)/PowerOnAlert/Android.mk
include $(BASE_PATH)/CarrierCacheService/Android.mk
include $(BASE_PATH)/CarrierConfigure/Android.mk
include $(BASE_PATH)/CarrierLoadService/Android.mk
include $(BASE_PATH)/ZeroBalanceHelper/Android.mk
include $(BASE_PATH)/libdatactrl/Android.mk
include $(BASE_PATH)/SetupWizard/Android.mk
include $(BASE_PATH)/ConfigurationClient/Android.mk
include $(BASE_PATH)/OmaDownload/Android.mk
include $(BASE_PATH)/OmaDrmEngine/Android.mk
