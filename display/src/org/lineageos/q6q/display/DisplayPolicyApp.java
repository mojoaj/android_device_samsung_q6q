/*
 * SPDX-FileCopyrightText: 2026 Anthony Mckeever
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.q6q.display;

import android.app.Application;
import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.util.Log;

/**
 * Persistent system app (android.uid.system): started by the framework at boot.
 *
 * 1. Sets a minimum refresh rate of 48 Hz by default. Without a minimum, SurfaceFlinger
 *    idles in its 10 Hz panel mode and the first touch after a pause waits for a mode
 *    switch on a 100 ms vsync (feels like touch lag). 48 Hz keeps that wait under 21 ms
 *    while still letting video pick a matching mode: 23.976/24 fps -> 48 Hz, 30/60 fps
 *    -> 60 Hz. The Samsung panels do their own low-frequency drive (LFD, 10-120 Hz), so
 *    a static screen still drops to 10 Hz inside the panel.
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

    private static final float DEFAULT_MIN_REFRESH_RATE = 48f;
    private static final String PREFS = "display_policy";
    private static final String KEY_MIGRATED_PEAK_LOCK = "migrated_peak_lock";

    /**
     * min_refresh_rate unset or 0 means "no config": the Minimum refresh rate list in
     * Settings > Display never writes 0 (its lowest entry is the lowest panel mode), but
     * Developer options > Force peak refresh rate does when developer options are turned
     * off. Any value picked in Settings is left alone.
     *
     * 24-b5..b8 defaulted to Infinity (always peak), which also kept video at 120 Hz. That
     * value is moved to the new default once; Force peak refresh rate (also Infinity)
     * turned on after that is respected.
     */
    private void defaultMinRefreshRate() {
        final ContentResolver cr = getContentResolver();
        final SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        final float current = Settings.System.getFloat(cr, Settings.System.MIN_REFRESH_RATE, 0f);
        final boolean migrate = Float.isInfinite(current)
                && !prefs.getBoolean(KEY_MIGRATED_PEAK_LOCK, false);
        prefs.edit().putBoolean(KEY_MIGRATED_PEAK_LOCK, true).apply();
        if (current > 0f && !migrate) {
            return;
        }
        if (Settings.System.putFloat(cr, Settings.System.MIN_REFRESH_RATE,
                DEFAULT_MIN_REFRESH_RATE)) {
            Log.i(TAG, "Defaulted min_refresh_rate to " + DEFAULT_MIN_REFRESH_RATE
                    + (migrate ? " (was peak lock)" : ""));
        } else {
            Log.w(TAG, "Failed to set min_refresh_rate");
        }
    }
}
