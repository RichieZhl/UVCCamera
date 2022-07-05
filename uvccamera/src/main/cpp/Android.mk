#include $(call all-subdir-makefiles)
PROJ_PATH := $(call my-dir)
LOCAL_PATH := $(call my-dir)

NDK_PROJECT_PATH := .

include $(CLEAR_VARS)
include $(PROJ_PATH)/UVCCamera/Android.mk
include $(PROJ_PATH)/libjpeg-turbo-1.5.0/Android.mk
include $(PROJ_PATH)/libusb/android/jni/Android.mk
include $(PROJ_PATH)/libuvc/android/jni/Android.mk
