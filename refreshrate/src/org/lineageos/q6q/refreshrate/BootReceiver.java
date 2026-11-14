/*
 * SPDX-FileCopyrightText: 2026 Anthony Mckeever
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.q6q.refreshrate;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;

/**
 * Keeps SurfaceFlinger in the 120 Hz panel mode by default.
 *
 * The Samsung panels do their own low-frequency drive (LFD, 10-120 Hz, see
 * /sys/class/lcd/panel{,1}/vrr_lfd), so a static screen already drops to 10 Hz
 * inside the panel while a new frame still goes out on the next 120 Hz TE.
 * If SurfaceFlinger is instead allowed to switch to its 10-48 Hz modes on idle,
 * the first touch has to wait for a mode switch on a 100 ms vsync, which feels
 * like touch lag. One UI never idles the composer that low.
 *
 * Only written when the user hasn't chosen a value (Settings > Display >
 * Minimum refresh rate), so it never overrides a user choice.
 */
public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "Q6qRefreshRate";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Settings.System.getString(context.getContentResolver(),
                Settings.System.MIN_REFRESH_RATE) != null) {
            return;
        }
        // Infinity = highest rate the active display supports (DisplayModeDirector).
        if (Settings.System.putFloat(context.getContentResolver(),
                Settings.System.MIN_REFRESH_RATE, Float.POSITIVE_INFINITY)) {
            Log.i(TAG, "Defaulted min_refresh_rate to display max (panel LFD handles idle)");
        }
    }
}
