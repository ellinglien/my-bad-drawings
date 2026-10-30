package io.github.mpdairy.monopaint;

/**
 * Speed, like physical media: smoothed over about 0.1 s, changing a tool's width only
 * gradually, each tool's factor within its range, and no effect at all without times.
 */
public final class PenSpeedChecks {
    public static void main(String[] args) {
        untimed(); smoothing(); sameMillisecond(); rateLimit(); ranges(); monotonic(); strokes();
        System.out.println("PASS: pen speed smoothed with a 100 ms time constant, fastness eased in from .3 to 3 px/ms and changing no faster than a full swing in "
                + Math.round(PenSpeed.FULL_SWING_MS) + " ms, so a sudden stop does not snap the width;"
                + " pen eraser .3 slow to 1 fast, finger 12 px slow to its contact size fast; no times, no effect");
    }

    /** Samples moving along x at {@code speed} px/ms every {@code stepMs} for {@code ms}, from {@code x} at {@code time}; returns the last x. */
    private static float move(PenSpeed pen, float x, long time, float speed, int stepMs, int ms) {
        for (int t = stepMs; t <= ms; t += stepMs) pen.sample(x + speed * t, 0, time + t);
        return x + speed * ms;
    }

    private static void untimed() {
        PenSpeed pen = new PenSpeed();
        for (int i = 0; i < 100; i++) pen.sample(i * 50, 0, PenSpeed.UNTIMED);
        for (PenSpeed.Effect effect : PenSpeed.Effect.values())
            check(pen.factor(effect) == 1, "Without times, speed changes nothing: " + effect);
        check(pen.speed() == 0, "Without times there is no speed");
    }

    private static void smoothing() {
        for (int step : new int[]{1, 2, 4}) {
            PenSpeed pen = new PenSpeed();
            pen.sample(0, 0, 1000);
            move(pen, 0, 1000, 2f, step, Math.round(PenSpeed.SMOOTHING_MS));
            float expected = 2f * (1 - (float)Math.exp(-1));
            check(Math.abs(pen.speed() - expected) < .01f, "After one time constant, speed is 63% of the way there, whatever the sample rate: " + pen.speed() + " at " + step + " ms");
        }
        PenSpeed pen = new PenSpeed();
        pen.sample(0, 0, 0);
        move(pen, 0, 0, 2f, 2, 1000);
        check(Math.abs(pen.speed() - 2f) < .01f, "A steady speed is reached");
    }

    /** At 480 samples a second, millisecond times repeat: the distance waits for the clock instead of dividing by zero. */
    private static void sameMillisecond() {
        PenSpeed pen = new PenSpeed();
        pen.sample(0, 0, 0);
        float x = 0;
        for (int i = 1; i <= 960; i++) { x += 1.04f; pen.sample(x, 0, i * 25 / 12); }
        check(Float.isFinite(pen.speed()) && Math.abs(pen.speed() - .5f) < .03f, "Repeated times average out: " + pen.speed());
    }

    private static void rateLimit() {
        PenSpeed pen = new PenSpeed();
        pen.sample(0, 0, 0);
        // From rest, a sudden flick: the width still changes gradually.
        float x = 0; long t = 0;
        float last = pen.fastness();
        for (int i = 0; i < 400; i++) {
            t += 2; x += 2 * 6f; pen.sample(x, 0, t);
            check(pen.fastness() - last <= 2 / PenSpeed.FULL_SWING_MS + 1e-5f, "Fastness grows no faster than its limit");
            last = pen.fastness();
        }
        check(pen.fastness() == 1, "A long flick is fully fast");
        // A sudden stop: the width does not snap back.
        long stop = t;
        for (int i = 0; i < 200; i++) {
            t += 2; pen.sample(x, 0, t);
            float change = last - pen.fastness();
            check(change >= 0 && change <= 2 / PenSpeed.FULL_SWING_MS + 1e-5f, "Fastness falls no faster than its limit");
            for (PenSpeed.Effect effect : PenSpeed.Effect.values()) {
                float range = Math.abs(effect.fast - effect.slow);
                check(Math.abs(effect.at(last) - effect.at(pen.fastness())) <= range * 2 / PenSpeed.FULL_SWING_MS + 1e-5f, "Each factor changes gradually: " + effect);
            }
            last = pen.fastness();
            if (t - stop == 20) check(pen.factor(PenSpeed.Effect.PEN_ERASER) > .85f, "20 ms after a sudden stop the eraser is still nearly full size");
        }
        check(pen.fastness() == 0, "Stopped long enough, it is slow again");
    }

    private static void ranges() {
        PenSpeed pen = new PenSpeed();
        pen.sample(0, 0, 0);
        move(pen, 0, 0, .1f, 2, 1000);
        check(pen.fastness() == 0, "A careful, slow move is slow");
        check(near(pen.factor(PenSpeed.Effect.PEN_ERASER), .3f) && pen.factor(PenSpeed.Effect.FINGER_ERASER) == 0, "A slow eraser is small");
        move(pen, 100, 1000, 4f, 2, 1000);
        check(pen.fastness() == 1, "A quick flick is fast");
        check(pen.factor(PenSpeed.Effect.PEN_ERASER) == 1 && pen.factor(PenSpeed.Effect.FINGER_ERASER) == 1, "A fast eraser is full size");
        float narrow = PenSpeed.fingerDiameter(60, 0), broad = PenSpeed.fingerDiameter(60, 1);
        check(narrow == InkWashSizes.FINGER_NARROWEST && broad == 60, "A finger narrows to 12 px slow, its contact size fast");
        check(PenSpeed.fingerDiameter(10, 0) == 10, "A contact already finer stays as it is");
    }

    private static void monotonic() {
        check(PenSpeed.fastnessAt(0) == 0 && PenSpeed.fastnessAt(PenSpeed.SLOW) == 0, "Up to a slow move, no speed effect");
        check(PenSpeed.fastnessAt(PenSpeed.FAST) == 1 && PenSpeed.fastnessAt(50) == 1, "From a quick flick on, the full effect");
        float last = 0;
        for (float speed = 0; speed <= 6; speed += .01f) {
            float f = PenSpeed.fastnessAt(speed);
            check(f >= last && f >= 0 && f <= 1, "Fastness never falls as speed grows"); last = f;
        }
        for (PenSpeed.Effect effect : PenSpeed.Effect.values()) {
            float previous = effect.at(0);
            for (float f = 0; f <= 1; f += .01f) {
                float value = effect.at(f);
                check(effect.fast < effect.slow ? value <= previous : value >= previous, "Each factor moves one way from slow to fast: " + effect);
                previous = value;
            }
        }
        check(PenSpeed.fastnessAt(Float.NaN) == 0, "No reading, no effect");
    }

    /** The eraser takes its times: slow erasing is fine and fast is broad; untimed, as before. */
    private static void strokes() {
        int slow = erasedRows(.1f, 0), fast = erasedRows(4f, 0), untimed = erasedRows(0, 0);
        int full = Math.round(InkWashSizes.diameter(InkWashSizes.Preset.MEDIUM, .45f));
        check(Math.abs(untimed - full) <= 2, "Untimed, the pen eraser is its pressure size: " + untimed + " vs " + full);
        check(Math.abs(slow - full * .3f) <= 2, "Slow, the pen eraser is about 30% of it: " + slow);
        check(Math.abs(fast - full) <= 2, "Fast, the pen eraser is its full size: " + fast);
        int fingerSlow = erasedRows(.1f, 60), fingerFast = erasedRows(4f, 60);
        check(Math.abs(fingerSlow - InkWashSizes.FINGER_NARROWEST) <= 2 && Math.abs(fingerFast - 60) <= 2, "A finger erases 12 px slow and its contact size fast: " + fingerSlow + ", " + fingerFast);
    }
    /** How many rows of a long ink line an eraser crossing it at {@code speed} px/ms erases; a finger at {@code finger} px, or the pen at M. */
    private static int erasedRows(float speed, float finger) {
        ToneDocument doc = InkWashLayers.blank(1400, 140);
        doc.selectLayer(InkWashLayers.INK); doc.begin();
        for (int y = 0; y < doc.height; y++) doc.setTone(1300, y, 0);
        doc.finish();
        EraseBothStroke stroke = finger > 0 ? new EraseBothStroke(doc, finger) : new EraseBothStroke(doc, InkWashSizes.Preset.MEDIUM);
        for (int t = 0; ; t += 2) {
            float x = Math.min(1360, 10 + (speed > 0 ? speed : 2) * t);
            if (speed > 0) stroke.sample(x, 70, .45f, 0, 0, t); else stroke.sample(x, 70, .45f, 0, 0);
            if (x >= 1360) break;
        }
        stroke.finish();
        int rows = 0;
        for (int y = 0; y < doc.height; y++) if (doc.compositeTone(1300, y) == 255) rows++;
        return rows;
    }
    private static boolean near(float a, float b) { return Math.abs(a - b) < 1e-4f; }
    private static void check(boolean valid, String message) { if (!valid) throw new AssertionError(message); }
}
