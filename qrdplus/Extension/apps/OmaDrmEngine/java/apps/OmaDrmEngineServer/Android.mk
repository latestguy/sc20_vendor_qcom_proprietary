
LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

# ---------------------------------------
#
# Build OmaDrmEngineServer application
#
# Output: OmaDrmEngineServer.apk
# ------------------------------------------------
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := user

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_PACKAGE_NAME := OmaDrmEngineServer

LOCAL_STATIC_JAVA_LIBRARIES := \
        android-support-v4

LOCAL_CERTIFICATE := platform

LOCAL_PROGUARD_ENABLED := disabled

LOCAL_MODULE_TAGS := optional

include $(BUILD_PACKAGE)
include $(call all-makefiles-under,$(LOCAL_PATH))
