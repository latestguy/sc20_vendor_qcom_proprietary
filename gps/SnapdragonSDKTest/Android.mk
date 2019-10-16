#/******************************************************************************
#*@file Android.mk
#*brief Rules for compiling ODLTApp Application
#*  ******************************************************************************/

LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

#this tag means this package will be available for all eng builds
#note if the OEM decides to include this permission and group assignment in the final build, the
#tag should be changed to 'user'
LOCAL_MODULE_TAGS := debug

LOCAL_JAVA_LIBRARIES = com.qti.location.sdk

LOCAL_SRC_FILES := $(call all-subdir-java-files)

LOCAL_PACKAGE_NAME := my.tests.snapdragonsdktest

#note this is necessary for the test app to acquire the permission to access LocAPI in etc/oncrpc
LOCAL_CERTIFICATE := platform

LOCAL_PROGUARD_ENABLED := disabled

include $(BUILD_PACKAGE)

include $(CLEAR_VARS)

include $(BUILD_MULTI_PREBUILT)

include $(call all-makefiles-under,$(LOCAL_PATH))
