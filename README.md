# Device tree for the Samsung Galaxy Z Fold6 (SM-F956B, q6q)

Unofficial LineageOS 24.0 device tree for the Snapdragon 8 Gen 3 Galaxy Z Fold6.

Maintained by [mojoaj](https://github.com/mojoaj).

| | |
|---|---|
| Model | SM-F956B (global/EU, unlockable bootloader) |
| Codename | q6q |
| SoC | Qualcomm SM8650 (Snapdragon 8 Gen 3 for Galaxy) |
| Inner display | 7.6" 1856x2160, 120 Hz, under-display camera |
| Cover display | 6.3" 968x2376, 120 Hz |
| Required firmware | F956BXXS2BYGB (One UI 7, bootloader revision 2) |

> **Never** flash Samsung firmware with bootloader revision 3 or higher (5th character from the end of the build number). It removes OEM unlocking permanently.

## Building

Use the manifest at <https://github.com/mojoaj/android_local_manifests_q6q> on top of LineageOS `lineage-24.0`, then:

```sh
source build/envsetup.sh
lunch lineage_q6q-cp2a-userdebug
m bacon
```

## Related repositories

| Path | Repository |
|---|---|
| device/samsung/q6q | this repository |
| device/samsung/sm8650-common | mojoaj/android_device_samsung_sm8650-common (branch `q6q`) |
| vendor/samsung/q6q | mojoaj/proprietary_vendor_samsung_q6q |
| vendor/samsung/sm8650-common | mojoaj/proprietary_vendor_samsung_sm8650-common (branch `q6q`) |
| kernel/samsung/sm8650{,-modules,-devicetrees} | mojoaj/android_kernel_samsung_sm8650* (branch `q6q`) |
| hardware/samsung | mojoaj/android_hardware_samsung (branch `q6q`) |
| frameworks/base, lineage-sdk, device/qcom/sepolicy_vndr/sm8650 | mojoaj forks (branch `q6q`) |

## Credits

- [LineageOS](https://lineageos.org) for the OS, and the authors of the official q5q (Z Fold5) tree, whose foldable touch HAL and overlays this tree builds on.
- [Exynoobs](https://github.com/Exynoobs) for the sm8650-common device, vendor and kernel trees (Galaxy S24 series).
- Samsung Open Source Release Center for the SM-F956B kernel source.

## License

Apache License 2.0 (see `LICENSE`), except where a file states otherwise. Files derived from other LineageOS/Exynoobs trees keep their original copyright notices.
