# Copyright (C) 2010 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

LOCAL_PATH := $(call my-dir)
ROOT_PATH := $(LOCAL_PATH)

include $(call all-subdir-makefiles)
include $(CLEAR_VARS)

LOCAL_PATH = $(ROOT_PATH)
FILE_LIST := $(wildcard $(LOCAL_PATH)/*.c)
FILE_LIST += $(wildcard $(LOCAL_PATH)/effects/*.c)
FILE_LIST += $(wildcard $(LOCAL_PATH)/generators/*.c)
LOCAL_CFLAGS := -Wall -Wextra
#LOCAL_C_INCLUDES := $(LOCAL_PATH)/track.h

LOCAL_MODULE := nativeaudio
LOCAL_LDLIBS := -L$(SYSROOT)/usr/lib -llog -landroid -lOpenSLES 
LOCAL_STATIC_LIBRARIES := fftw3
LOCAL_SRC_FILES := $(FILE_LIST:$(LOCAL_PATH)/%=%)
#LOCAL_SRC_FILES += nativeaudio.c

include $(BUILD_SHARED_LIBRARY)
