package io.github.mpdairy.monopaint;

/**
 * Pen samples collected from input events until the stroke takes them all at once. On the Manta each pen event
 * carries one sample (480 a second, never batched), and drawing, composing and showing after each one costs a big
 * brush more than the 2 ms the next allows, so events queue up behind it. Collecting them instead and drawing what has
 * gathered once the queue is empty lets a slow draw be followed by a bigger one: the stroke keeps up with the pen at
 * the cost of fewer, larger screen updates. The stroke gets the same samples in the same order. A quick stroke hands
 * its samples to its render thread in one and keeps them there. Pure.
 */
final class PenBatch {
    private float[] x = new float[256], y = new float[256], pressure = new float[256], tiltX = new float[256], tiltY = new float[256];
    private long[] time = new long[256];
    private int count;

    /** Adds one sample (page position, pressure, tilt degrees, the input's clock in ms). */
    void add(float px, float py, float p, float tx, float ty, long t) {
        if (count == x.length) {
            int n = 2 * count;
            x = java.util.Arrays.copyOf(x, n); y = java.util.Arrays.copyOf(y, n); pressure = java.util.Arrays.copyOf(pressure, n);
            tiltX = java.util.Arrays.copyOf(tiltX, n); tiltY = java.util.Arrays.copyOf(tiltY, n); time = java.util.Arrays.copyOf(time, n);
        }
        x[count] = px; y[count] = py; pressure[count] = p; tiltX[count] = tx; tiltY[count] = ty; time[count] = t;
        count++;
    }
    boolean isEmpty() { return count == 0; }
    /** Sample {@code i}'s page position, pressure, tilt and time: a mirrored copy's samples ({@link MirrorStroke}). */
    float x(int i) { return x[i]; }
    float y(int i) { return y[i]; }
    float pressure(int i) { return pressure[i]; }
    float tiltX(int i) { return tiltX[i]; }
    float tiltY(int i) { return tiltY[i]; }
    long time(int i) { return time[i]; }
    int size() { return count; }
    /** The input clock of the oldest sample waiting, ms (the batch's longest wait starts there). */
    long oldest() { return count == 0 ? 0 : time[0]; }
    /** Feeds every sample to {@code stroke}, oldest first, and empties the batch; returns how many. */
    int feed(DrawingStroke stroke) {
        int n = count;
        for (int i = 0; i < n; i++) stroke.sample(x[i], y[i], pressure[i], tiltX[i], tiltY[i], time[i]);
        count = 0;
        return n;
    }
    /** Feeds sample {@code i} to {@code stroke}, keeping it: a quick stroke's samples, rendered on its own thread ({@link QuickStroke}). */
    void feed(DrawingStroke stroke, int i) { stroke.sample(x[i], y[i], pressure[i], tiltX[i], tiltY[i], time[i]); }
    /** Adds every sample here to the end of {@code into} and empties this batch: handing samples over to another thread. */
    void moveTo(PenBatch into) {
        for (int i = 0; i < count; i++) into.add(x[i], y[i], pressure[i], tiltX[i], tiltY[i], time[i]);
        count = 0;
    }
    void clear() { count = 0; }
}
