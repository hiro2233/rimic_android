#
# Copyright (C) 2013 Andrew Comminos
# Copyright (C) 2020 Hiroshi Takey F. (hiro2233) <htakey@gmail.com>
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

ROOT := $(call my-dir)

include $(CLEAR_VARS)

ifeq ($(TARGET_ARCH),arm)
LOCAL_ARM_MODE := arm
endif
ifeq ($(TARGET_ARCH_ABI),arm64-v8a)
LOCAL_ARM_NEON := true
endif
ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
#LOCAL_ARM_NEON := true
endif
LOCAL_PATH          := $(ROOT)/speex/libspeex
LOCAL_MODULE        := jnispeex
LOCAL_C_INCLUDES    := $(ROOT)/speex/include/
LOCAL_SRC_FILES     := cb_search.c      exc_10_32_table.c   exc_8_128_table.c   filters.c \
                       gain_table.c     hexc_table.c        high_lsp_tables.c   lsp.c \
                       ltp.c            speex.c             stereo.c            vbr.c \
                       vq.c bits.c      exc_10_16_table.c   exc_20_32_table.c   exc_5_256_table.c \
                       exc_5_64_table.c gain_table_lbr.c    hexc_10_32_table.c  lpc.c \
                       lsp_tables_nb.c  modes.c             modes_wb.c          nb_celp.c \
                       quant_lsp.c      sb_celp.c           speex_callbacks.c   speex_header.c \
                       window.c
ifeq ($(TARGET_ARCH_ABI),armeabi)
LOCAL_CFLAGS           := -ffunction-sections -fdata-sections -s -D__EMX__ -DUSE_KISS_FFT -DFIXED_POINT -DEXPORT=''
endif
ifeq ($(TARGET_ARCH_ABI),arm64-v8a)
LOCAL_CFLAGS           := -ffunction-sections -fdata-sections -s -D__EMX__ -DUSE_SMALLFT -DFLOATING_POINT -DEXPORT='' -DUSE_NEON
endif
ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
LOCAL_CFLAGS           := -ffunction-sections -fdata-sections -s -mfpu=neon-vfpv4 -D__EMX__ -DUSE_SMALLFT -DFLOATING_POINT -DEXPORT='' -DUSE_NEON
endif
LOCAL_CPP_FEATURES := exceptions
LOCAL_LDLIBS := -llog -latomic
LOCAL_LD_FLAGS := -Wl,--gc-sections
include $(BUILD_STATIC_LIBRARY)

include $(CLEAR_VARS)

ifeq ($(TARGET_ARCH),arm)
LOCAL_ARM_MODE := arm
endif
ifeq ($(TARGET_ARCH_ABI),arm64-v8a)
LOCAL_ARM_NEON := true
endif
ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
#LOCAL_ARM_NEON := true
endif
LOCAL_MODULE        := jnispeexdsp
LOCAL_PATH          := $(ROOT)/speexdsp/libspeexdsp
LOCAL_C_INCLUDES    := $(ROOT)/speexdsp/include/
LOCAL_SRC_FILES     := smallft.c		buffer.c			resample.c          jitter.c            preprocess.c \
                       mdf.c            kiss_fft.c          kiss_fftr.c         fftwrap.c \
                       filterbank.c     scal.c \
                       $(ROOT)/jnispeexdsp.cpp
ifeq ($(TARGET_ARCH_ABI),armeabi)
LOCAL_CFLAGS           := -ffunction-sections -fdata-sections -s -D__EMX__ -DUSE_KISS_FFT -DFIXED_POINT -DEXPORT=''
endif
ifeq ($(TARGET_ARCH_ABI),arm64-v8a)
LOCAL_CFLAGS           := -ffunction-sections -fdata-sections -s -D__EMX__ -DUSE_SMALLFT -DFLOATING_POINT -DEXPORT='' -DUSE_NEON
endif
ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
LOCAL_CFLAGS           := -ffunction-sections -fdata-sections -s -mfpu=neon-vfpv4 -D__EMX__ -DUSE_SMALLFT -DFLOATING_POINT -DEXPORT='' -DUSE_NEON
endif
LOCAL_CPP_FEATURES := exceptions
LOCAL_LDLIBS := -llog -latomic
LOCAL_STATIC_LIBRARIES := jnispeex
LOCAL_LD_FLAGS := -Wl,--gc-sections
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
ifeq ($(TARGET_ARCH),arm)
LOCAL_ARM_MODE := arm
endif
ifeq ($(TARGET_ARCH_ABI),arm64-v8a)
LOCAL_ARM_NEON := true
endif
ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
#LOCAL_ARM_NEON := true
endif
LOCAL_PATH          := $(ROOT)/celt-0.11.0-src/libcelt
LOCAL_MODULE        := jnicelt11
LOCAL_SRC_FILES     := bands.c celt.c cwrs.c entcode.c entdec.c entenc.c header.c kiss_fft.c \
                       laplace.c mathops.c mdct.c modes.c pitch.c plc.c quant_bands.c rate.c vq.c \
                       $(ROOT)/jnicelt11.cpp
LOCAL_C_INCLUDES    := $(ROOT)/celt-0.11.0-src/libcelt/
LOCAL_CFLAGS        := -ffunction-sections -fdata-sections -s -I$(ROOT)/celt-0.11.0-build -DHAVE_CONFIG_H -fvisibility=hidden
LOCAL_CPP_FEATURES := exceptions
LOCAL_LDLIBS := -llog -latomic
LOCAL_LD_FLAGS := -Wl,--gc-sections
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
ifeq ($(TARGET_ARCH),arm)
LOCAL_ARM_MODE := arm
endif
ifeq ($(TARGET_ARCH_ABI),arm64-v8a)
LOCAL_ARM_NEON := true
endif
ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
#LOCAL_ARM_NEON := true
endif
LOCAL_PATH          := $(ROOT)/celt-0.7.0-src/libcelt
LOCAL_MODULE        := jnicelt7
LOCAL_SRC_FILES     := bands.c celt.c cwrs.c entcode.c entdec.c entenc.c header.c kiss_fft.c \
                       kiss_fftr.c laplace.c mdct.c modes.c pitch.c psy.c quant_bands.c rangedec.c \
                       rangeenc.c rate.c vq.c $(ROOT)/jnicelt7.cpp
LOCAL_C_INCLUDES    := $(ROOT)/celt-0.7.0-src/libcelt/
LOCAL_CFLAGS        := -ffunction-sections -fdata-sections -s -I$(ROOT)/celt-0.7.0-build -DHAVE_CONFIG_H -fvisibility=hidden
LOCAL_CPP_FEATURES := exceptions
LOCAL_LDLIBS := -llog -latomic
LOCAL_LD_FLAGS := -Wl,--gc-sections
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
ifeq ($(TARGET_ARCH),arm)
LOCAL_ARM_MODE := arm
endif
ifeq ($(TARGET_ARCH_ABI),arm64-v8a)
LOCAL_ARM_NEON := true
endif
ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
#LOCAL_ARM_NEON := true
endif
LOCAL_PATH   := $(ROOT)/opus
LOCAL_MODULE := jniopus

include $(LOCAL_PATH)/celt_sources.mk
include $(LOCAL_PATH)/silk_sources.mk
include $(LOCAL_PATH)/opus_sources.mk

ifeq ($(TARGET_ARCH),arm)
CELT_SOURCES += $(CELT_SOURCES_ARM)
SILK_SOURCES += $(SILK_SOURCES_ARM)
endif

ifeq ($(TARGET_ARCH_ABI),armeabi)
# TODO: add support for floating-point?
SILK_SOURCES += $(SILK_SOURCES_FIXED)
OPUS_SOURCES += $(OPUS_SOURCES_FLOAT)
# end fixed point
endif

ifeq ($(TARGET_ARCH_ABI),$(filter $(TARGET_ARCH_ABI),armeabi-v7a arm64-v8a))
CELT_SOURCES += $(CELT_SOURCES_ARM_NEON_INTR)
SILK_SOURCES += $(SILK_SOURCES_ARM_NEON_INTR)
SILK_SOURCES += $(SILK_SOURCES_FLOAT)
OPUS_SOURCES += $(OPUS_SOURCES_FLOAT)
endif

LOCAL_C_INCLUDES    := $(LOCAL_PATH)/include $(LOCAL_PATH)/celt $(LOCAL_PATH)/silk \
                       $(LOCAL_PATH)/silk/float $(LOCAL_PATH)/silk/fixed
LOCAL_SRC_FILES     := $(CELT_SOURCES) $(SILK_SOURCES) $(OPUS_SOURCES) $(ROOT)/jniopus.cpp
ifeq ($(TARGET_ARCH_ABI),armeabi)
LOCAL_CFLAGS        := -ffunction-sections -fdata-sections -s -DOPUS_BUILD -DVAR_ARRAYS -DFIXED_POINT
endif
ifeq ($(TARGET_ARCH_ABI),arm64-v8a)
LOCAL_CFLAGS           := -ffunction-sections -fdata-sections -s -DFLOAT_APPROX -DENABLE_HARDENING -DOPUS_BUILD -DVAR_ARRAYS -DOPUS_ARM_MAY_HAVE_NEON_INTR=1 -DOPUS_ARM_PRESUME_NEON_INTR=1
endif
ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
LOCAL_CFLAGS           := -ffunction-sections -fdata-sections -s -mfpu=neon-vfpv4 -DFLOAT_APPROX -DENABLE_HARDENING -DOPUS_BUILD -DVAR_ARRAYS -DOPUS_ARM_MAY_HAVE_NEON_INTR=1 -DOPUS_ARM_PRESUME_NEON_INTR=1
endif
LOCAL_CPP_FEATURES  := exceptions
LOCAL_LDLIBS        := -llog -latomic
LOCAL_LD_FLAGS := -Wl,--gc-sections
include $(BUILD_SHARED_LIBRARY)
