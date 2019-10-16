# Disable include all subdir makefiles, If any one module can normal compile, please include module makefile in here.
#include $(call all-subdir-makefiles)
BASE_PATH := $(call my-dir)
include $(BASE_PATH)/AutoRegistration/Android.mk
include $(BASE_PATH)/BootAnimation/Android.mk
include $(BASE_PATH)/BrowserQuick/Android.mk
include $(BASE_PATH)/CustomerService/Android.mk
include $(BASE_PATH)/RoamingSettings/Android.mk