# Disable include all subdir makefiles, If any one module can normal compile, please include module makefile in here.
#include $(call all-subdir-makefiles)

BASE_PATH := $(call my-dir)
include $(BASE_PATH)/10086cn/Android.mk
include $(BASE_PATH)/CmccCustomerService/Android.mk
include $(BASE_PATH)/Backup/Android.mk
include $(BASE_PATH)/BackupReceiver/Android.mk
include $(BASE_PATH)/ExtWifi/Android.mk
