LOCAL_PATH := $(call my-dir)

# ---------------------------------------
# Build OmaDrmEngineDemo application
#
# Output: OmaDrmEngineDemo.apk
# ----------------------------------------
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := \
        $(call all-java-files-under, src)

LOCAL_PACKAGE_NAME := OmaDrmEngineDemo

LOCAL_STATIC_JAVA_LIBRARIES := \
        android-support-v4 \
        android-support-v7-appcompat \
        android-support-v7-recyclerview \
        android-support-v7-gridlayout \
        android-support-design
LOCAL_RESOURCE_DIR = \
        $(LOCAL_PATH)/res \
        frameworks/support/v7/appcompat/res \
        frameworks/support/v7/recyclerview/res \
        frameworks/support/v7/gridlayout/res \
        frameworks/support/design/res
LOCAL_AAPT_FLAGS := \
        --auto-add-overlay \
        --extra-packages android.support.v7.appcompat \
        --extra-packages android.support.v7.recyclerview \
        --extra-packages android.support.v7.gridlayout \
        --extra-packages android.support.design

LOCAL_PROGUARD_ENABLED := disabled

LOCAL_DEX_PREOPT := false

LOCAL_MODULE_TAGS := eng development

include $(BUILD_PACKAGE)

