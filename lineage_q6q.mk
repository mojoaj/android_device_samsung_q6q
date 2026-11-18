#
# Copyright (C) 2023-2026 The LineageOS Project
# Copyright (C) 2026 Anthony Mckeever
#
# SPDX-License-Identifier: Apache-2.0
#

# Inherit from those products. Most specific first.
$(call inherit-product, $(SRC_TARGET_DIR)/product/core_64_bit_only.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/full_base_telephony.mk)

# Inherit from the device configuration.
$(call inherit-product, device/samsung/q6q/device.mk)

# Inherit from the Lineage configuration.
$(call inherit-product, vendor/lineage/config/common_full_phone.mk)

PRODUCT_NAME := lineage_q6q
PRODUCT_DEVICE := q6q
PRODUCT_BRAND := samsung
PRODUCT_MODEL := SM-F956B
PRODUCT_MANUFACTURER := Samsung

PRODUCT_GMS_CLIENTID_BASE := android-samsung

# KeyMint ID attestation uses the stock region product name (q6qxeea,
# q6qxxx, ...); init.q6q.attestation.rc sets it from ro.boot.carrierid.
# The manufacturer was provisioned lowercase (stock ro.product.manufacturer).
PRODUCT_MANUFACTURER_FOR_ATTESTATION := samsung
