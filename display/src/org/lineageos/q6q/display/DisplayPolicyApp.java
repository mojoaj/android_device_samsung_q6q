/*
 * SPDX-FileCopyrightText: 2026 Anthony Mckeever
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.q6q.display;

import android.app.Application;
import android.content.ContentResolver;
import android.provider.Settings;
import android.util.Log;

/**
 * Persistent system app (android.uid.system): started by the framework at boot.
 *
 * 1. Keeps SurfaceFlinger in the 120 Hz panel mode by default. The Samsung panels do
 *    their own low-frequency drive (LFD, 10-120 Hz), so a static screen still drops
 *    to 10 Hz inside the panel while a new frame goes out on the next 120 Hz TE.
 *    Letting SurfaceFlinger idle in its 10 Hz mode instead makes the first touch
 *    after a pause wait for a mode switch on a 100 ms vsync (feels like touch lag).
 * 2. Runs the panel LFD policy One UI applies (see LfdController).
 */
public class DisplayPolicyApp extends Application {
    private static final String TAG = "Q6qDisplayPolicy";

    @Override
    public void onCreate() {
        super.onCreate();
        defaultMinRefreshRate();
        new LfdController(this).start();
    }

    /**
     * min_refresh_rate unset or 0 means "no config": the Minimum refresh rate list in
     * Settings > Display never writes 0 (its lowest entry is the lowest panel mode), but
     * Developer options > Force peak refresh rate does when developer options are turned
     * off. Any value picked in Settings is left alone.
     */
    private void defaultMinRefreshRate() {
        final ContentResolver cr = getContentResolver();
        final float current = Settings.System.getFloat(cr, Settings.System.MIN_REFRESH_RATE, 0f);
        if (current > 0f) {
            return;
        }
        // Infinity = highest rate the active display supports (DisplayModeDirector).
        if (Settings.System.putFloat(cr, Settings.System.MIN_REFRESH_RATE,
                Float.POSITIVE_INFINITY)) {
            Log.i(TAG, "Defaulted min_refresh_rate to display max (panel LFD handles idle)");
        } else {
            Log.w(TAG, "Failed to set min_refresh_rate");
        }
    }
}
