# Android.mk

LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE    := MyJniLib
LOCAL_SRC_FILES := native_bt.cpp
LOCAL_C_INCLUDES := $(LOCAL_PATH)/../include
LOCAL_LDLIBS := -llog
include $(BUILD_SHARED_LIBRARY)
