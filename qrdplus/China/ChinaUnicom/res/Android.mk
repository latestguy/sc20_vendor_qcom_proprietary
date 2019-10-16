#Disable include all subdir makefiles,  If any one module can normal compile, please include module makefile in here.
#include $(call all-subdir-makefiles)
BASE_PATH := $(call my-dir)
include $(BASE_PATH)/SystemUI/Android.mk
include $(BASE_PATH)/SimContacts/Android.mk
include $(BASE_PATH)/Stk/Android.mk
include $(BASE_PATH)/Settings/Android.mk
include $(BASE_PATH)/Browser/Android.mk
include $(BASE_PATH)/Dialer/Android.mk
include $(BASE_PATH)/SettingsProvider/Android.mk
