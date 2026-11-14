/*
 * SPDX-FileCopyrightText: 2026 Anthony Mckeever
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.q6q.display;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.display.DisplayManager;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Display;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Panel low-frequency drive (LFD) policy, following One UI's
 * RefreshRateController$SeamlessPlusController (services.jar, Fold6 BYGB/CYL5):
 *
 * - The panel driver (ss_dsi_panel_sysfs.c, vrr_lfd) lets a static image refresh down to
 *   10 Hz on its own. One UI turns that off on the inner screen when low-frequency
 *   refresh would show as flicker or interference:
 *     * brightness below Samsung platform level 43 (of 255) AND ambient light below
 *       50 lux (or not known yet): persist.dm.passive.display_brightness /
 *       persist.dm.passive.ambient_brightness defaults;
 *     * wireless charging.
 *   It writes "client=disp scope=normal,lpm fix=3" (LFD pinned at 120 Hz) and
 *   "scalability=1" (LFD off), and back to 0 otherwise.
 * - The high-lux (1 Hz) rule is not configured on the Fold6 (thresholds -1), and the
 *   cover screen has no thresholds, so One UI only writes /sys/class/lcd/panel (inner).
 *
 * Platform level 43 = backlight 430 of 12750 = brightness 0.0337 here (backlight
 * 0..12750 maps linearly to brightness 0..1, 2550 = level 255), about 50 nits on the
 * inner panel. That conversion assumes One UI's level is its 0..255 platform level.
 */
class LfdController implements DisplayManager.DisplayListener, SensorEventListener {
    private static final String TAG = "Q6qLfd";

    private static final String INNER_LFD = "/sys/class/lcd/panel/vrr_lfd";
    private static final String INNER_UNIQUE_ID = "local:4630946165277524611";

    private static final float LOW_BRIGHTNESS = 430f / 12750f;
    private static final float LOW_LUX = 50f;
    // Hysteresis so the panel doesn't toggle around the lux threshold.
    private static final float LOW_LUX_EXIT = 60f;

    private static final int FIX_OFF = 0;
    private static final int FIX_LFD = 3;
    private static final int SCALABILITY_DEFAULT = 0;
    private static final int SCALABILITY_LFD_OFF = 1;

    private final Context mContext;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final DisplayManager mDisplayManager;
    private final SensorManager mSensorManager;
    private final Sensor mLightSensor;

    private boolean mSensorRegistered;
    private float mLux = -1f;
    private boolean mLowLux = true; // unknown lux counts as low (One UI: lux zone 0)
    private boolean mWireless;
    private int mApplied = -1; // -1 = never written

    LfdController(Context context) {
        mContext = context;
        mDisplayManager = context.getSystemService(DisplayManager.class);
        mSensorManager = context.getSystemService(SensorManager.class);
        mLightSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
    }

    void start() {
        mDisplayManager.registerDisplayListener(this, mHandler,
                DisplayManager.EVENT_TYPE_DISPLAY_ADDED
                        | DisplayManager.EVENT_TYPE_DISPLAY_REMOVED
                        | DisplayManager.EVENT_TYPE_DISPLAY_CHANGED
                        | DisplayManager.EVENT_TYPE_DISPLAY_STATE
                        | DisplayManager.EVENT_TYPE_DISPLAY_BRIGHTNESS);
        final Intent battery = mContext.registerReceiver(mBatteryReceiver,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED), null, mHandler);
        if (battery != null) {
            mWireless = isWireless(battery);
        }
        update();
    }

    private final BroadcastReceiver mBatteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final boolean wireless = isWireless(intent);
            if (wireless != mWireless) {
                mWireless = wireless;
                update();
            }
        }
    };

    private static boolean isWireless(Intent battery) {
        return battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
                == BatteryManager.BATTERY_PLUGGED_WIRELESS;
    }

    /** The inner panel if it is the one currently driving the default display, else null. */
    private Display innerDisplayIfActive() {
        final Display display = mDisplayManager.getDisplay(Display.DEFAULT_DISPLAY);
        if (display == null || !INNER_UNIQUE_ID.equals(display.getUniqueId())) {
            return null;
        }
        return display.getState() == Display.STATE_ON ? display : null;
    }

    private void update() {
        final Display inner = innerDisplayIfActive();
        setSensorEnabled(inner != null);
        if (inner == null) {
            // Panel is off or the cover screen is in use; the driver keeps the last request
            // and we re-evaluate as soon as the inner screen comes back on.
            return;
        }
        final float brightness = inner.getBrightnessInfo() != null
                ? inner.getBrightnessInfo().brightness : 1f;
        final boolean lfdOff = mWireless || (brightness < LOW_BRIGHTNESS && mLowLux);
        apply(lfdOff ? 1 : 0, brightness);
    }

    private void apply(int lfdOff, float brightness) {
        if (lfdOff == mApplied) {
            return;
        }
        final String req = "client=disp scope=normal,lpm fix=" + (lfdOff == 1 ? FIX_LFD : FIX_OFF)
                + " scalability=" + (lfdOff == 1 ? SCALABILITY_LFD_OFF : SCALABILITY_DEFAULT);
        try (FileOutputStream out = new FileOutputStream(INNER_LFD)) {
            out.write(req.getBytes(StandardCharsets.US_ASCII));
            mApplied = lfdOff;
            Log.i(TAG, (lfdOff == 1 ? "LFD off" : "LFD on") + " (brightness=" + brightness
                    + ", lux=" + mLux + ", wireless=" + mWireless + ")");
        } catch (IOException e) {
            Log.e(TAG, "Failed to write " + INNER_LFD, e);
        }
    }

    private void setSensorEnabled(boolean enabled) {
        if (mLightSensor == null || enabled == mSensorRegistered) {
            return;
        }
        if (enabled) {
            mSensorManager.registerListener(this, mLightSensor,
                    SensorManager.SENSOR_DELAY_NORMAL, mHandler);
        } else {
            mSensorManager.unregisterListener(this);
            mLux = -1f;
            mLowLux = true;
        }
        mSensorRegistered = enabled;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        mLux = event.values[0];
        final boolean low = mLowLux ? mLux < LOW_LUX_EXIT : mLux < LOW_LUX;
        if (low != mLowLux) {
            mLowLux = low;
            update();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    public void onDisplayAdded(int displayId) {
        update();
    }

    @Override
    public void onDisplayRemoved(int displayId) {
        update();
    }

    @Override
    public void onDisplayChanged(int displayId) {
        if (displayId == Display.DEFAULT_DISPLAY) {
            update();
        }
    }
}
