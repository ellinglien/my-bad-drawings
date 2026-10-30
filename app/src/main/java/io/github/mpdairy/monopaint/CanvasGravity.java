package io.github.mpdairy.monopaint;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.Display;
import android.view.WindowManager;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

/**
 * Which way is down on the page, for wet paint that runs downhill. Android stays in
 * portrait, but the drawing screen's page turns with the app (Rotate): page axes are the
 * device's own (x right, y down) turned by the display's rotation and the app's. A tablet
 * lying flat reports no pull ({@link PageTilt#FLAT}).
 */
final class CanvasGravity {
    private static final float SMOOTHING = .2f;
    private final SensorManager sensors;
    /** Whether the pull is wanted at all (MonoPaint: the "Watercolor uses gravity" setting). */
    private final BooleanSupplier enabled;
    private final Display display;
    /** The app's own turn, as Surface counts it: 0 where the page never turns (Brush Lab's pad). */
    private IntSupplier turn = () -> 0;
    private boolean listening, seeded;
    private float x, y;
    private final SensorEventListener listener = new SensorEventListener() {
        @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }
        @Override public void onSensorChanged(SensorEvent event) {
            // The accelerometer reads the push against gravity on axes with y up, the device's own.
            double[] g = PageTilt.fromDevice(event.values[0], event.values[1], (display == null ? 0 : display.getRotation()) + turn.getAsInt());
            float gx = (float)g[0], gy = (float)g[1];
            // The first reading is used as is, so newly wet paint never starts out level.
            if (!seeded) { x = gx; y = gy; seeded = true; }
            else { x += (gx - x) * SMOOTHING; y += (gy - y) * SMOOTHING; }
        }
    };

    CanvasGravity(PaintActivity app) { this(app, app.prefs::wetGravity); turn = () -> app.appRotation; }
    /** For {@code context}'s screen, listening only while {@code enabled}. */
    CanvasGravity(Context context, BooleanSupplier enabled) {
        sensors = (SensorManager)context.getSystemService(Context.SENSOR_SERVICE);
        WindowManager windows = (WindowManager)context.getSystemService(Context.WINDOW_SERVICE);
        display = windows == null ? null : windows.getDefaultDisplay();
        this.enabled = enabled;
    }

    /** The pad listens only while wet paint is still spreading, and only with the setting on. */
    void listen(boolean spreading) {
        boolean wanted = spreading && enabled.getAsBoolean() && sensors != null;
        if (wanted == listening) return;
        Sensor sensor = sensors == null ? null : sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (wanted && sensor != null) listening = sensors.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI);
        else if (!wanted) { sensors.unregisterListener(listener); listening = seeded = false; x = y = 0; }
    }

    /** The current downhill direction on the page; strength grows with tilt, at most 1; level until a reading arrives. */
    PageTilt read() { return seeded ? PageTilt.pull(x, y) : PageTilt.LEVEL; }
    /** Applies the current downhill direction to wet paint; strength grows with tilt, at most 1. */
    void apply(WetWatercolor wet) {
        PageTilt t = read();
        wet.setGravity((float)t.x, (float)t.y);
    }
}
