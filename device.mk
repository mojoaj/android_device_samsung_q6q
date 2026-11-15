#
# Copyright (C) 2024-2026 The LineageOS Project
# Copyright (C) 2026 Anthony Mckeever
#
# SPDX-License-Identifier: Apache-2.0
#

# AAPT
PRODUCT_AAPT_CONFIG := normal
PRODUCT_AAPT_PREF_CONFIG := xxhdpi

# OTA updates (Updater app): JSON published at github.com/mojoaj/lineage_q6q_ota
# Large-screen Settings (two-pane on the inner screen), as on Pixel tablets/foldables
PRODUCT_PRODUCT_PROPERTIES += \
    persist.settings.large_screen_opt.enabled=true

PRODUCT_SYSTEM_PROPERTIES += \
    lineage.updater.uri=https://raw.githubusercontent.com/mojoaj/lineage_q6q_ota/main/q6q.json

# Audio (OSS Qualcomm audio stack: stock F956B mixer paths and resource manager)
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/configs/audio/mixer_paths_pineapple.xml:$(TARGET_COPY_OUT_VENDOR)/etc/audio/sku_pineapple/mixer_paths_pineapple_mtp.xml \
    $(LOCAL_PATH)/configs/audio/resourcemanager_pineapple.xml:$(TARGET_COPY_OUT_VENDOR)/etc/audio/sku_pineapple/resourcemanager_pineapple_mtp.xml

# Boot animation (cover screen size; multi-display below)
TARGET_SCREEN_HEIGHT := 2376
TARGET_SCREEN_WIDTH := 968

# Multi Display Bootanimation
$(call soong_config_set_bool,bootanimation,multidisplay,true)

# Init
PRODUCT_PACKAGES += \
    init.q6q.rc \
    init.recovery.q6q.rc

# Namespaces
PRODUCT_SOONG_NAMESPACES += \
    $(LOCAL_PATH)

# Overlays
PRODUCT_PACKAGES += \
    FrameworksResQ6q \
    LineageResQ6q \
    SettingsResQ6q \
    SystemUIResQ6q \
    UpdaterResQ6q \
    WifiResTargetQ6q

# Fold: device state policy (AOSP BookStyle, system server extension)
PRODUCT_PACKAGES += \
    q6q-devicestate
PRODUCT_SYSTEM_SERVER_JARS += \
    system_ext:q6q-devicestate

# Fold: display layout per device state
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/configs/display_layout_configuration.xml:$(TARGET_COPY_OUT_VENDOR)/etc/displayconfig/display_layout_configuration.xml \
    $(LOCAL_PATH)/configs/display_id_4630946165277524611.xml:$(TARGET_COPY_OUT_VENDOR)/etc/displayconfig/display_id_4630946165277524611.xml \
    $(LOCAL_PATH)/configs/display_id_4630947194243491972.xml:$(TARGET_COPY_OUT_VENDOR)/etc/displayconfig/display_id_4630947194243491972.xml

# Fold hall calibration
PRODUCT_PACKAGES += \
    init.q6q.dhall.sh

# Display: 120 Hz composer default + panel LFD policy
PRODUCT_PACKAGES += \
    Q6qDisplayPolicy

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
