package io.github.mpdairy.monopaint;

import android.os.SystemClock;
import java.io.FileInputStream;

/**
 * The e-ink controller's frames, counted from its interrupt (Manta/Nomad: {@code fdec0000.ebc} in /proc/interrupts): the
 * panel drives one frame about every 11 ms while any update is still running, so when the frames stop the panel shows
 * everything it was asked to. The bench's measure of the panel's own lag, which the app's timing can't see (a present
 * returns long before the panel has finished it). -1 where the count can't be read.
 */
final class PanelFrames {
    private static final byte[] buf = new byte[16384];

    /** Frames the controller has driven since boot, or -1. */
    static long count() {
        try (FileInputStream in = new FileInputStream("/proc/interrupts")) {
            int n = 0, r;
            while (n < buf.length && (r = in.read(buf, n, buf.length - n)) > 0) n += r;
            String s = new String(buf, 0, n, "US-ASCII");
            int at = s.indexOf("fdec0000.ebc");
            if (at < 0) return -1;
            int line = s.lastIndexOf('\n', at) + 1;
            String[] f = s.substring(line, at).trim().split("\\s+");
            long sum = 0;
            // "18:  cpu0 cpu1 cpu2 cpu3  GICv3 49 Level": the per-CPU counts follow the number.
            for (int i = 1; i < f.length; i++) { if (!f[i].matches("\\d+")) break; sum += Long.parseLong(f[i]); }
            return sum;
        } catch (Exception e) { return -1; }
    }

    /**
     * Waits until the controller has driven no frame for {@code quietMs} (at most {@code maxMs}); returns {when the last
     * frame came (uptime ms), frames since {@code from}}, or null if frames can't be read.
     */
    static long[] settle(long from, long quietMs, long maxMs) {
        long last = count();
        if (last < 0 || from < 0) return null;
        long lastAt = SystemClock.uptimeMillis(), start = lastAt;
        while (SystemClock.uptimeMillis() - lastAt < quietMs && SystemClock.uptimeMillis() - start < maxMs) {
            SystemClock.sleep(2);
            long c = count();
            if (c != last) { last = c; lastAt = SystemClock.uptimeMillis(); }
        }
        return new long[]{lastAt, last - from};
    }
}
