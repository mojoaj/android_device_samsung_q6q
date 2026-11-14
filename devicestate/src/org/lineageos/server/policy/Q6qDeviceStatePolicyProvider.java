/*
 * SPDX-FileCopyrightText: 2026 Anthony Mckeever
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.server.policy;

import android.annotation.NonNull;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.provider.Settings;
import android.util.Slog;

import com.android.server.devicestate.DeviceStatePolicy;
import com.android.server.policy.BookStyleDeviceStatePolicy;

/**
 * Galaxy Z Fold6 (q6q) device state policy: AOSP's book-style foldable policy
 * (CLOSED / HALF_OPENED / OPENED with tent and wedge mode), selected by
 * config_deviceSpecificDeviceStatePolicyProvider.
 *
 * Sensors (measured with tools/senslog FoldLog, logs/foldlog-1/2.txt):
 * - The AOSP hinge_angle sensor (type 36) only reports 0/90/180, too coarse for
 *   BookStyle. Samsung "Folding Angle" (com.samsung.sensor.folding_angle) reports
 *   whole degrees 0..180 but is non-wakeup and sends no initial value.
 * - "lid_angle_fusion" (com.samsung.sensor.folding_state) is wakeup, sends an initial
 *   event and fires at fold-state changes with the precise angle in values[1]. Its
 *   events are forwarded to the policy as Folding Angle readings, so the policy has a
 *   starting angle and sees an unfold that happens while the AP is suspended.
 * - Accelerometers: main "lsm6dsv" is in the inner-only half, "LSM6DSVW ... Sub" is in
 *   the cover-screen half (closed and lying cover up: sub z=-9.8, main z=+9.8).
 * - Closed-state hysteresis: BookStyle treats any angle above exactly 0 as "opening" and
 *   moves to the inner screen. Folding Angle steps through 1-4 degrees when the folded
 *   phone is just held or squeezed, which turned the inner screen on and then locked the
 *   phone when it read 0 again. All angle events therefore pass through
 *   {@link ClosedHysteresis}: after a 0 reading, angles below OPEN_ANGLE_DEGREES are
 *   reported as 0.
 */
public class Q6qDeviceStatePolicyProvider implements DeviceStatePolicy.Provider {
    private static final String TAG = "Q6qDeviceStatePolicy";

    private static final String TYPE_FOLDING_ANGLE = "com.samsung.sensor.folding_angle";
    private static final String TYPE_FOLDING_STATE = "com.samsung.sensor.folding_state";
    private static final String TYPE_ACCELEROMETER_SUB = "com.samsung.sensor.accelerometer_sub";
    private static final String NAME_ACCELEROMETER_MAIN = "lsm6dsv LSM6DSV Accelerometer";

    // Settings.Global.LID_BEHAVIOR (hidden). Builds before b46 seeded it with 1 (sleep)
    // from sm8650-common's config_lidControlsSleep, so closing the phone fully always
    // slept and ignored "Continue using apps on fold". Reset it to 0 (none) once.
    private static final String LID_BEHAVIOR = "lid_behavior";
    private static final String LID_MIGRATED = "q6q_lid_behavior_migrated";

    @Override
    public DeviceStatePolicy instantiate(@NonNull Context context) {
        migrateLidBehaviorAfterBoot(context);

        final SensorManager sm = context.getSystemService(SensorManager.class);

        Sensor foldingAngle = null;
        Sensor foldingState = null;
        Sensor accelMain = null;
        Sensor accelSub = null;
        for (Sensor s : sm.getSensorList(Sensor.TYPE_ALL)) {
            final String type = s.getStringType();
            if (foldingAngle == null && TYPE_FOLDING_ANGLE.equals(type)) {
                foldingAngle = s;
            } else if (foldingState == null && TYPE_FOLDING_STATE.equals(type)
                    && s.isWakeUpSensor()) {
                foldingState = s;
            } else if (accelSub == null && TYPE_ACCELEROMETER_SUB.equals(type)) {
                accelSub = s;
            } else if (accelMain == null && s.getType() == Sensor.TYPE_ACCELEROMETER
                    && s.getName().startsWith(NAME_ACCELEROMETER_MAIN)) {
                accelMain = s;
            }
        }

        final Sensor hinge = foldingAngle != null ? foldingAngle
                : sm.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE, /* wakeUp= */ true);
        if (hinge == null) {
            throw new IllegalStateException("No hinge angle sensor");
        }
        if (accelMain == null) {
            accelMain = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }

        // BookStyle "left" = cover-screen half (reverse wedge: lying on the cover screen),
        // "right" = other half (wedge/tent: that half flat on a table).
        final Sensor left = accelSub;
        final Sensor right = accelMain;

        Slog.i(TAG, "hinge=" + hinge.getName() + " wake=" + (foldingState != null
                ? foldingState.getName() : null)
                + " left=" + (left != null ? left.getName() : null)
                + " right=" + (right != null ? right.getName() : null));

        final BookStyleDeviceStatePolicy policy = new BookStyleDeviceStatePolicy(
                context, hinge, left, right,
                /* closeAngleDegrees= */ null);

        if (!(policy.getDeviceStateProvider() instanceof SensorEventListener)) {
            Slog.w(TAG, "Provider is not a SensorEventListener, hinge hysteresis disabled");
            return policy;
        }
        final SensorEventListener target = (SensorEventListener) policy.getDeviceStateProvider();
        final ClosedHysteresis filter = new ClosedHysteresis(target, hinge);

        if (hinge == foldingAngle) {
            // Take the raw Folding Angle stream away from the provider and feed it filtered.
            sm.unregisterListener(target, foldingAngle);
            sm.registerListener(new SensorEventListener() {
                @Override
                public void onSensorChanged(SensorEvent event) {
                    filter.onAngle(event.values[0], event.accuracy, event.timestamp);
                }

                @Override
                public void onAccuracyChanged(Sensor sensor, int accuracy) {}
            }, foldingAngle, SensorManager.SENSOR_DELAY_FASTEST);
        }

        if (foldingState != null && hinge != foldingState) {
            sm.registerListener(new SensorEventListener() {
                @Override
                public void onSensorChanged(SensorEvent event) {
                    if (event.values.length < 2) return;
                    filter.onAngle(event.values[1], event.accuracy, event.timestamp);
                }

                @Override
                public void onAccuracyChanged(Sensor sensor, int accuracy) {}
            }, foldingState, SensorManager.SENSOR_DELAY_FASTEST);
        }

        return policy;
    }

    private static void migrateLidBehaviorAfterBoot(Context context) {
        // Never let this take system_server down: worst case the setting stays as it was.
        try {
            registerLidMigration(context);
        } catch (RuntimeException e) {
            Slog.w(TAG, "lid_behavior migration not registered", e);
        }
    }

    private static void registerLidMigration(Context context) {
        context.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                ctx.unregisterReceiver(this);
                try {
                    final ContentResolver cr = ctx.getContentResolver();
                    if (Settings.Global.getInt(cr, LID_MIGRATED, 0) != 0) return;
                    if (Settings.Global.getInt(cr, LID_BEHAVIOR, 0) == 1) {
                        Settings.Global.putInt(cr, LID_BEHAVIOR, 0);
                        Slog.i(TAG, "lid_behavior: sleep -> none (fold setting decides)");
                    }
                    Settings.Global.putInt(cr, LID_MIGRATED, 1);
                } catch (RuntimeException e) {
                    Slog.w(TAG, "lid_behavior migration failed", e);
                }
            }
        }, new IntentFilter(Intent.ACTION_LOCKED_BOOT_COMPLETED));
    }

    /**
     * Holds the hinge at 0 degrees after the phone has been fully closed until the angle
     * reaches OPEN_ANGLE_DEGREES (BookStyle's own end of the closed band). A real unfold
     * passes 1-5 degrees within ~150 ms (logs/foldlog-2.txt), so this adds no visible delay.
     */
    private static final class ClosedHysteresis {
        private static final float OPEN_ANGLE_DEGREES = 5f;

        private final SensorEventListener mTarget;
        private final Sensor mHinge;
        private boolean mClosedLatched = false;
        private float mMaxSuppressed = 0f;

        ClosedHysteresis(SensorEventListener target, Sensor hinge) {
            mTarget = target;
            mHinge = hinge;
        }

        synchronized void onAngle(float angle, int accuracy, long timestamp) {
            float reported = angle;
            if (angle <= 0f) {
                if (!mClosedLatched) {
                    mClosedLatched = true;
                    mMaxSuppressed = 0f;
                }
            } else if (mClosedLatched) {
                if (angle < OPEN_ANGLE_DEGREES) {
                    if (angle > mMaxSuppressed) {
                        mMaxSuppressed = angle;
                        Slog.i(TAG, "Closed: ignoring hinge bounce to " + angle + " deg");
                    }
                    reported = 0f;
                } else {
                    mClosedLatched = false;
                    Slog.i(TAG, "Opened: hinge " + angle + " deg (max ignored bounce "
                            + mMaxSuppressed + ")");
                }
            }
            mTarget.onSensorChanged(new SensorEvent(mHinge, accuracy, timestamp,
                    new float[] { reported }));
        }
    }
}
