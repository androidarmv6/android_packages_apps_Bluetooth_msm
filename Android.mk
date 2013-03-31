ifeq ($(BOARD_HAVE_BLUETOOTH_BLUEZ),true)

LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_PACKAGE_NAME := Bluetooth

LOCAL_JAVA_LIBRARIES := javax.btobex
LOCAL_JAVA_LIBRARIES += mms-common
LOCAL_JAVA_LIBRARIES += telephony-common
LOCAL_STATIC_JAVA_LIBRARIES := com.android.vcard
LOCAL_REQUIRED_MODULES := javax.btobex mms-common telephony-common
LOCAL_CERTIFICATE := platform

LOCAL_PROGUARD_FLAG_FILES := proguard.flags

include $(BUILD_PACKAGE)

include $(call all-makefiles-under,$(LOCAL_PATH))
endif
