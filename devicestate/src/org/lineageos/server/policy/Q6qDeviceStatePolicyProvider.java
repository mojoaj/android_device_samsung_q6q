/*
 * SPDX-FileCopyrightText: 2026 Anthony Mckeever
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.server.policy;

import android.annotation.NonNull;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Slog;

import com.android.server.devicestate.DeviceStatePolicy;
import com.android.server.policy.BookStyleDeviceStatePolicy;
import com.android.server.policy.feature.flags.FeatureFlagsImpl;

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
 * - Switch points (stock One UI: the cover stays on until ~80 degrees when opening, the
 *   inner screen stays on until ~5-10 degrees when closing). BookStyle switches to the
 *   inner screen at anything above 0 (or 90 in tent/wedge mode) and back at exactly 0,
 *   and Folding Angle also steps through 1-4 degrees when the folded phone is held or
 *   squeezed. All angle events therefore pass through {@link FoldHysteresis}: once
 *   closed, angles below OPEN_ANGLE_DEGREES are reported as 0; once open, angles at or
 *   below CLOSE_ANGLE_DEGREES are reported as 0.
 */
public class Q6qDeviceStatePolicyProvider implements DeviceStatePolicy.Provider {
    private static final String TAG = "Q6qDeviceStatePolicy";

    private static final String TYPE_FOLDING_ANGLE = "com.samsung.sensor.folding_angle";
    private static final String TYPE_FOLDING_STATE = "com.samsung.sensor.folding_state";
    private static final String TYPE_HALL_IC = "com.samsung.sensor.hallIC";
    private static final String TYPE_ACCELEROMETER_SUB = "com.samsung.sensor.accelerometer_sub";
    private static final String NAME_ACCELEROMETER_MAIN = "lsm6dsv LSM6DSV Accelerometer";

    @Override
    public DeviceStatePolicy instantiate(@NonNull Context context) {
        final SensorManager sm = context.getSystemService(SensorManager.class);

        Sensor foldingAngle = null;
        Sensor foldingState = null;
        Sensor hall = null;
        Sensor accelMain = null;
        Sensor accelSub = null;
        for (Sensor s : sm.getSensorList(Sensor.TYPE_ALL)) {
            final String type = s.getStringType();
            if (foldingAngle == null && TYPE_FOLDING_ANGLE.equals(type)) {
                foldingAngle = s;
            } else if (foldingState == null && TYPE_FOLDING_STATE.equals(type)
                    && s.isWakeUpSensor()) {
                foldingState = s;
            } else if (hall == null && TYPE_HALL_IC.equals(type)) {
                hall = s;
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
        if (hall == null) {
            // Unused while posture-based closed state is enabled, but must be non-null.
            hall = hinge;
        }
        if (accelMain == null) {
            accelMain = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }

        // BookStyle "left" = cover-screen half (reverse wedge: lying on the cover screen),
        // "right" = other half (wedge/tent: that half flat on a table).
        final Sensor left = accelSub;
        final Sensor right = accelMain;

        Slog.i(TAG, "hinge=" + hinge.getName() + " wake=" + (foldingState != null
                ? foldingState.getName() : null) + " hall=" + hall.getName()
                + " left=" + (left != null ? left.getName() : null)
                + " right=" + (right != null ? right.getName() : null));

        final BookStyleDeviceStatePolicy policy = new BookStyleDeviceStatePolicy(
                new FeatureFlagsImpl(), context, hinge, hall, left, right,
                /* closeAngleDegrees= */ null);

        if (!(policy.getDeviceStateProvider() instanceof SensorEventListener)) {
            Slog.w(TAG, "Provider is not a SensorEventListener, hinge hysteresis disabled");
            return policy;
        }
        final SensorEventListener target = (SensorEventListener) policy.getDeviceStateProvider();
        final FoldHysteresis filter = new FoldHysteresis(target, hinge);

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

    /**
     * Folding hysteresis. After the phone reads closed, the hinge is held at 0 until it
     * opens past OPEN_ANGLE_DEGREES; after that, it reads 0 as soon as it folds to
     * CLOSE_ANGLE_DEGREES. If the AP sleeps through an unfold, the wakeup lid_angle_fusion
     * event at ~82 degrees still opens it.
     */
    private static final class FoldHysteresis {
        private static final float OPEN_ANGLE_DEGREES = 80f;
        private static final float CLOSE_ANGLE_DEGREES = 7f;

        private final SensorEventListener mTarget;
        private final Sensor mHinge;
        private boolean mClosedLatched = false;
        private float mMaxSuppressed = 0f;

        FoldHysteresis(SensorEventListener target, Sensor hinge) {
            mTarget = target;
            mHinge = hinge;
        }

        synchronized void onAngle(float angle, int accuracy, long timestamp) {
            float reported = angle;
            if (mClosedLatched) {
                if (angle < OPEN_ANGLE_DEGREES) {
                    if (angle > mMaxSuppressed) {
                        mMaxSuppressed = angle;
                    }
                    reported = 0f;
                } else {
                    mClosedLatched = false;
                    Slog.i(TAG, "Opened: hinge " + angle + " deg (held closed up to "
                            + mMaxSuppressed + ")");
                }
            } else if (angle <= CLOSE_ANGLE_DEGREES) {
                mClosedLatched = true;
                mMaxSuppressed = 0f;
                reported = 0f;
                Slog.i(TAG, "Closed: hinge " + angle + " deg");
            }
            mTarget.onSensorChanged(new SensorEvent(mHinge, accuracy, timestamp,
                    new float[] { reported }));
        }
    }
}
