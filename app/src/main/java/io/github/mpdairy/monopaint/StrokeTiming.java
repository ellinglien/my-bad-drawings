package io.github.mpdairy.monopaint;

import java.util.Locale;

/**
 * Where one stroke's time goes, pen-down to finish, for finding what makes input fall behind.
 * Accumulates primitives only (no allocation per event) and prints one line at the end.
 * An "event" is one touch event handled: its samples (history included), then the render of
 * the changed area and the flush that presents it. An Inkwash pen stroke gathers its events' samples and draws them in
 * batches ({@link PenBatch}): each batch is timed too, with its lag, the oldest sample's wait from the pen to being
 * drawn and presented (what the pen-to-screen delay is, before the panel's own update).
 */
final class StrokeTiming {
    private boolean active;
    private String what;
    private long startNanos, createNanos;
    private int events, samples, presents, busy, deferred;
    // Per event: running sums, folded into totals and maxima by event().
    private long eventSample, eventRender, eventFlush, eventArea;
    private long sampleTotal, sampleMax, renderTotal, renderMax, areaTotal, areaMax, flushTotal, flushMax;
    private long presentTotal, presentMax, ageTotal, ageMax, ageLast;
    private int batches, batchMax;
    /** The presents' parts, ns ({@link DirectEink#presentParts}): turning, finding a change, copying, the driver call, and its longest. */
    private long turnTotal, compareTotal, copyTotal, ioctlTotal, ioctlMax;
    private long lagTotal, lagMax;
    /** Each batch's lag, ms (the first {@link #LAGS}), for its median and 95th percentile. */
    private final int[] lags = new int[LAGS];
    private static final int LAGS = 4096;
    /** The pen's down (input clock, uptime ms) and its first present after it, ms after it (-1 before one). */
    private long downAt, firstMs, beganAt, firstPresentNanos;

    boolean active() { return active; }

    /** Starts timing a stroke; {@code what} describes the tool and size, or the finger. */
    void begin(String what, long nowNanos) {
        active = true; this.what = what; startNanos = nowNanos; createNanos = 0;
        events = samples = presents = busy = deferred = 0;
        eventSample = eventRender = eventFlush = eventArea = 0;
        sampleTotal = sampleMax = renderTotal = renderMax = areaTotal = areaMax = flushTotal = flushMax = 0;
        presentTotal = presentMax = ageTotal = ageMax = ageLast = 0;
        batches = batchMax = 0; lagTotal = lagMax = 0;
        turnTotal = compareTotal = copyTotal = ioctlTotal = ioctlMax = 0;
        downAt = 0; firstMs = -1; beganAt = android.os.SystemClock.uptimeMillis(); firstPresentNanos = 0;
    }
    /** The pen's down event's time (uptime ms), for the first present's wait ({@link #present}). */
    void down(long uptimeMillis) { downAt = uptimeMillis; }
    void created(long nanos) { createNanos = nanos; }
    void cancel() { active = false; }

    void sample(long nanos) { samples++; eventSample += nanos; }
    /** {@code n} samples taken at once, in {@code nanos}. */
    void samples(int n, long nanos) { samples += n; eventSample += nanos; }
    void render(long nanos, long area) { eventRender += nanos; eventArea += area; }
    void flush(long nanos) { eventFlush += nanos; }
    /** One call into the panel driver; {@code result} below zero means it was busy. */
    void present(long nanos, int result) {
        presents++; presentTotal += nanos; presentMax = Math.max(presentMax, nanos);
        if (result < 0) busy++;
        else if (firstMs < 0 && downAt > 0) { firstMs = android.os.SystemClock.uptimeMillis() - downAt; firstPresentNanos = nanos; }
    }
    /** One present's parts, ns: turning, finding a change, copying into the plane, the driver call. */
    void presentParts(long[] parts) {
        turnTotal += parts[0]; compareTotal += parts[1]; copyTotal += parts[2]; ioctlTotal += parts[3]; ioctlMax = Math.max(ioctlMax, parts[3]);
    }
    /** A flush that was put off to coalesce with the next event. */
    void deferred() { deferred++; }

    /** One batch of {@code samples} drawn and presented, {@code lagMillis} after its oldest sample's pen time. */
    void batch(int samples, long lagMillis) {
        if (batches < LAGS) lags[batches] = (int) Math.min(Integer.MAX_VALUE, lagMillis);
        batches++; batchMax = Math.max(batchMax, samples);
        lagTotal += lagMillis; lagMax = Math.max(lagMax, lagMillis);
        renderTotal += eventRender; renderMax = Math.max(renderMax, eventRender);
        areaTotal += eventArea; areaMax = Math.max(areaMax, eventArea);
        flushTotal += eventFlush; flushMax = Math.max(flushMax, eventFlush);
        sampleTotal += eventSample; sampleMax = Math.max(sampleMax, eventSample);
        eventSample = eventRender = eventFlush = eventArea = 0;
    }

    /** Closes one event; {@code ageMillis} is how long it had waited when handling began. */
    void event(long ageMillis) {
        events++;
        sampleTotal += eventSample; sampleMax = Math.max(sampleMax, eventSample);
        renderTotal += eventRender; renderMax = Math.max(renderMax, eventRender);
        areaTotal += eventArea; areaMax = Math.max(areaMax, eventArea);
        flushTotal += eventFlush; flushMax = Math.max(flushMax, eventFlush);
        ageTotal += ageMillis; ageMax = Math.max(ageMax, ageMillis); ageLast = ageMillis;
        eventSample = eventRender = eventFlush = eventArea = 0;
    }

    /**
     * Ends the stroke and returns its summary line.
     * @param finishNanos time in {@code stroke.finish()}
     * @param finalRenderNanos time in the last renderDirty
     * @param finalFlushNanos time in the last flush
     */
    String end(long nowNanos, long finishNanos, long finalRenderNanos, long finalFlushNanos) {
        active = false;
        return String.format(Locale.ROOT,
                "stroke timing %s events=%d samples=%d sample_ms=%s/%s render_ms=%s/%s render_px2=%d/%d flush_ms=%s/%s"
                + " present_ms=%s/%s presents=%d busy=%d deferred=%d age_ms=%.1f/%d last_age_ms=%d"
                + " create_ms=%s finish_ms=%s final_render_ms=%s final_flush_ms=%s duration_ms=%s"
                + " batches=%d batch_samples=%.1f/%d lag_ms=%.1f/%d lag_p50_p95=%d/%d first_ms=%d first_parts=%d/%s/%s present_parts_ms=turn %s compare %s copy %s driver %s/%s",
                what, events, samples, ms(sampleTotal), ms(sampleMax), ms(renderTotal), ms(renderMax), areaTotal, areaMax,
                ms(flushTotal), ms(flushMax), ms(presentTotal), ms(presentMax), presents, busy, deferred,
                events == 0 ? 0f : ageTotal / (float)events, ageMax, ageLast,
                ms(createNanos), ms(finishNanos), ms(finalRenderNanos), ms(finalFlushNanos), ms(nowNanos - startNanos),
                batches, batches == 0 ? 0f : samples / (float)batches, batchMax, batches == 0 ? 0f : lagTotal / (float)batches, lagMax, lagAt(.5), lagAt(.95), firstMs, beganAt - downAt, ms(createNanos), ms(firstPresentNanos),
                ms(turnTotal), ms(compareTotal), ms(copyTotal), ms(ioctlTotal), ms(ioctlMax));
    }
    /** The batches' lag at quantile {@code q}, ms. */
    private int lagAt(double q) {
        int n = Math.min(batches, LAGS);
        if (n == 0) return 0;
        int[] a = java.util.Arrays.copyOf(lags, n);
        java.util.Arrays.sort(a);
        return a[Math.min(n - 1, (int) (q * n))];
    }
    private static String ms(long nanos) { return String.format(Locale.ROOT, "%.1f", nanos / 1e6); }
}
