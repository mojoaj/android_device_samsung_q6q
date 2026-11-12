#
# Copyright (C) 2024-2026 The LineageOS Project
#
# SPDX-License-Identifier: Apache-2.0
#

# AAPT
PRODUCT_AAPT_CONFIG := normal
PRODUCT_AAPT_PREF_CONFIG := xxhdpi

# BRING-UP ONLY (remove before any release): pre-authorise the build PC's adb key and
# enable adb by default, so logs can be read even if the first boot never reaches the UI.
PRODUCT_ADB_KEYS := $(LOCAL_PATH)/adb_keys
PRODUCT_SYSTEM_DEFAULT_PROPERTIES += persist.sys.usb.config=mtp,adb

# Boot animation (cover screen size; multi-display below)
TARGET_SCREEN_HEIGHT := 2376
TARGET_SCREEN_WIDTH := 968

# Multi Display Bootanimation
$(call soong_config_set_bool,bootanimation,multidisplay,true)

# Init
PRODUCT_PACKAGES += \
    init.q6q.rc

# Namespaces
PRODUCT_SOONG_NAMESPACES += \
    $(LOCAL_PATH)

# Overlays
PRODUCT_PACKAGES += \
    FrameworksResQ6q \
    LineageResQ6q \
    SystemUIResQ6q \
    WifiResTargetQ6q

# Power
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/configs/powerhint.json:$(TARGET_COPY_OUT_VENDOR)/etc/powerhint.json

# Touch features (two touchscreens + S Pen)
PRODUCT_PACKAGES += \
    vendor.lineage.touch-service.samsung-foldable

# WiFi firmware symlinks
PRODUCT_PACKAGES += \
    firmware_wlanmdsp.otaupdate_symlink \
    firmware_wlan_mac.bin_symlink \
    firmware_WCNSS_qcom_cfg.ini_symlink

# Inherit from the common OEM chipset makefile.
$(call inherit-product, device/samsung/sm8650-common/common.mk)

# Inherit from the proprietary files makefile.
$(call inherit-product, vendor/samsung/q6q/q6q-vendor.mk)
