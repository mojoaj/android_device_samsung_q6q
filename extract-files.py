#!/usr/bin/env -S PYTHONPATH=../../../tools/extract-utils python3
#
# SPDX-FileCopyrightText: 2024-2026 The LineageOS Project
# SPDX-FileCopyrightText: 2026 Anthony Mckeever
# SPDX-License-Identifier: Apache-2.0
#

from extract_utils.fixups_lib import (
    lib_fixups,
)
from extract_utils.main import (
    ExtractUtils,
    ExtractUtilsModule,
)

namespace_imports = [
    'vendor/samsung/sm8650-common',
]

module = ExtractUtilsModule(
    'q6q',
    'samsung',
    namespace_imports=namespace_imports,
    lib_fixups=lib_fixups,
)

if __name__ == '__main__':
    utils = ExtractUtils.device_with_common(module, 'sm8650-common', module.vendor)
    utils.run()
