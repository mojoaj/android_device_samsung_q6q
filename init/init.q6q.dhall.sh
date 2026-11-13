#!/vendor/bin/sh
#
# SPDX-FileCopyrightText: 2026 Anthony Mckeever
# SPDX-License-Identifier: Apache-2.0
#
# Load this unit's factory switch thresholds into the AKM digital hall.
# Without them the chip keeps DT defaults (Y 20000/2000), its switch output
# (gpio98, "flip") never toggles and the ADSP never learns the fold state.

CAL=/efs/FactoryApp/digital_hall
THD=/sys/class/sec/digital_hall/digital_hall_thd

if [ -s "$CAL" ]; then
    cat "$CAL" > "$THD"
fi
