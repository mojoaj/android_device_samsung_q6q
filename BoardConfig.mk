#
# Copyright (C) 2023-2026 The LineageOS Project
# Copyright (C) 2026 Anthony Mckeever
#
# SPDX-License-Identifier: Apache-2.0
#

DEVICE_PATH := device/samsung/q6q

# Partitions (must be set before the common BoardConfig, which uses ?=)
# super on SM-F956B: 13396992 KiB (sda33)
BOARD_SUPER_PARTITION_SIZE := 13718519808

# Include the common OEM chipset BoardConfig.
include device/samsung/sm8650-common/BoardConfigCommon.mk

# Assert
TARGET_OTA_ASSERT_DEVICE := q6q

# Display
TARGET_SCREEN_DENSITY := 420

# Kernel
TARGET_KERNEL_CONFIG := oem/q6q_defconfig

# Kernel Modules
BOARD_RECOVERY_RAMDISK_KERNEL_MODULES_LOAD := $(strip $(shell cat $(DEVICE_PATH)/modules.load.recovery))
BOARD_SYSTEM_KERNEL_MODULES_LOAD := $(strip $(shell cat $(DEVICE_PATH)/modules.load.system_dlkm))
BOARD_VENDOR_KERNEL_MODULES_BLOCKLIST_FILE := $(DEVICE_PATH)/modules.blocklist
BOARD_VENDOR_KERNEL_MODULES_LOAD := $(strip $(shell cat $(DEVICE_PATH)/modules.load))
BOARD_VENDOR_RAMDISK_KERNEL_MODULES_BLOCKLIST_FILE := $(BOARD_VENDOR_KERNEL_MODULES_BLOCKLIST_FILE)
BOARD_VENDOR_RAMDISK_KERNEL_MODULES_LOAD := $(strip $(shell cat $(DEVICE_PATH)/modules.load.vendor_boot))
BOARD_VENDOR_RAMDISK_RECOVERY_KERNEL_MODULES_LOAD := $(strip $(shell cat $(DEVICE_PATH)/modules.load.recovery))
BOOT_KERNEL_MODULES := $(BOARD_VENDOR_RAMDISK_RECOVERY_KERNEL_MODULES_LOAD)
BOARD_RECOVERY_KERNEL_MODULES_LOAD := $(strip $(shell cat $(DEVICE_PATH)/modules.load.recovery))
RECOVERY_KERNEL_MODULES := $(BOARD_RECOVERY_RAMDISK_KERNEL_MODULES_LOAD)
SYSTEM_KERNEL_MODULES := $(BOARD_SYSTEM_KERNEL_MODULES_LOAD)

TARGET_KERNEL_EXT_MODULES := \
    qcom/opensource/mmrm-driver \
    qcom/opensource/mm-drivers/hw_fence \
    qcom/opensource/mm-drivers/msm_ext_display \
    qcom/opensource/mm-drivers/sync_fence \
    qcom/opensource/securemsm-kernel \
    qcom/opensource/audio-kernel \
    qcom/opensource/synx-kernel \
    qcom/opensource/camera-kernel \
    qcom/opensource/datarmnet-ext/mem \
    qcom/opensource/dataipa/drivers/platform/msm \
    qcom/opensource/datarmnet/core \
    qcom/opensource/datarmnet-ext/aps \
    qcom/opensource/datarmnet-ext/offload \
    qcom/opensource/datarmnet-ext/shs \
    qcom/opensource/datarmnet-ext/perf \
    qcom/opensource/datarmnet-ext/perf_tether \
    qcom/opensource/datarmnet-ext/sch \
    qcom/opensource/datarmnet-ext/wlan \
    qcom/opensource/display-drivers/msm \
    qcom/opensource/dsp-kernel \
    qcom/opensource/eva-kernel \
    qcom/opensource/video-driver \
    qcom/opensource/graphics-kernel \
    qcom/opensource/wlan/platform \
    qcom/opensource/wlan/qcacld-3.0/.kiwi_v2 \
    qcom/opensource/bt-kernel \
    qcom/opensource/spu-kernel \
    qcom/opensource/mm-sys-kernel/ubwcp \
    qcom/opensource/touch-drivers \
    nxp/opensource/driver

# Recovery
# Inner panel is mounted rotated (installOrientation 3 on stock). GUESS: verify on device.
TARGET_RECOVERY_DEFAULT_ROTATION := ROTATION_LEFT
# minui does not rotate touch with the display; inner touch IC (fst2ca78y) needs matching rotation.
# Measured on device: the inner digitiser already reports in the upright (rotated) frame.
TARGET_RECOVERY_DEFAULT_TOUCH_ROTATION := ROTATION_NONE

# SEPolicy
BOARD_VENDOR_SEPOLICY_DIRS += $(DEVICE_PATH)/sepolicy/vendor

# VINTF
DEVICE_MANIFEST_FILE += $(DEVICE_PATH)/vintf/manifest_q6q.xml

# Vendor props
TARGET_VENDOR_PROP += $(DEVICE_PATH)/vendor.prop

# Include the proprietary files BoardConfig.
include vendor/samsung/q6q/BoardConfigVendor.mk
