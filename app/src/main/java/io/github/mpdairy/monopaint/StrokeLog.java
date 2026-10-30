package io.github.mpdairy.monopaint;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Record and Replay (docs/archive/brush-lab/prototypes/mischief-lab-3.NOTES.md, section 1): the pen's strokes on a page as the pen
 * sent them, kept in the page's file ({@link DocumentCodec}'s {@code RPLY} section) so the page can draw itself again.
 * Each stroke keeps its brush code, size and darkness; each sample its position (half pixels), pressure, tilt and the
 * ms since the sample before. Replay works because the engine is deterministic: the same samples, brush and grey make
 * the same marks. What plays is what was stored, so positions are rounded when recorded.
 *
 * <p>Immutable. An entry knows the Undo step that made it ({@code serial}, {@link ToneDocument}'s), so Undo and Redo
 * show and hide strokes with their steps; a Clear page leaves a marker (no brush) that hides the strokes before it.
 * Only pen strokes with a brush are logged: no eraser, smudge, fill, shape or finger. Pure, checked on the desktop
 * ({@code StrokeLogChecks}).
 */
final class StrokeLog {
    static final StrokeLog NONE = new StrokeLog(Collections.emptyList(), 0);
    /** The most samples a page keeps (about 300 KB deflated); past it the oldest strokes are dropped, so a replay starts part-way. */
    static final int MOST_SAMPLES = 60000;
    /** The most bytes of the file's section. */
    static final int MOST_BYTES = 1 << 20;
    /** The longest pause inside a stroke that replay keeps, ms. */
    static final int MOST_PAUSE = 2000;
    private static final int VERSION = 1;

    /** One stroke, or (no {@code code}) a Clear page marker. */
    static final class Entry {
        final long serial;
        final String code;
        final float px, darkness;
        /** Per sample: half pixels, ms since the one before (0 for the first), pressure and tilt bytes ({@link #NO_TILT}: none). */
        final int[] x, y, dt;
        final byte[] pressure, tiltX, tiltY;
        Entry(long serial, String code, float px, float darkness, int[] x, int[] y, int[] dt, byte[] pressure, byte[] tiltX, byte[] tiltY) {
            this.serial = serial; this.code = code; this.px = px; this.darkness = darkness;
            this.x = x; this.y = y; this.dt = dt; this.pressure = pressure; this.tiltX = tiltX; this.tiltY = tiltY;
        }
        int size() { return x.length; }
        Entry at(long s) { return new Entry(s, code, px, darkness, x, y, dt, pressure, tiltX, tiltY); }
        static Entry marker(long serial) { return new Entry(serial, null, 0, 0, new int[0], new int[0], new int[0], new byte[0], new byte[0], new byte[0]); }
        float px(int i) { return x[i] / 2f; }
        float py(int i) { return y[i] / 2f; }
        float pressure(int i) { return (pressure[i] & 255) / 255f; }
        float tiltX(int i) { return tiltX[i] == NO_TILT ? Float.NaN : tiltX[i]; }
        float tiltY(int i) { return tiltY[i] == NO_TILT ? Float.NaN : tiltY[i]; }
    }
    static final byte NO_TILT = Byte.MIN_VALUE;

    /** Gathers one stroke's samples as the pen sends them. */
    static final class Recorder {
        private final String code;
        private final float px, darkness;
        private final List<int[]> rows = new ArrayList<>();
        private long last = -1;
        Recorder(String code, double px, double darkness) { this.code = code; this.px = (float) px; this.darkness = (float) darkness; }
        /** One sample: page px, pressure 0 to 1, tilt degrees (NaN: none), the input's clock in ms (or a negative for none). */
        void add(float x, float y, float pressure, float tiltX, float tiltY, long time) {
            int dt = last < 0 || time < 0 ? 0 : (int) Math.max(0, Math.min(MOST_PAUSE, time - last));
            if (time >= 0) last = time;
            rows.add(new int[]{Math.round(x * 2), Math.round(y * 2), dt, Math.round(Math.max(0, Math.min(1, pressure)) * 255), tilt(tiltX), tilt(tiltY)});
        }
        private static int tilt(float t) { return Float.isNaN(t) ? NO_TILT : Math.max(-90, Math.min(90, Math.round(t))); }
        int size() { return rows.size(); }
        /** The stroke as an entry made by Undo step {@code serial}; null for none. */
        Entry done(long serial) {
            int n = rows.size();
            if (n == 0) return null;
            int[] x = new int[n], y = new int[n], dt = new int[n];
            byte[] p = new byte[n], tx = new byte[n], ty = new byte[n];
            for (int i = 0; i < n; i++) {
                int[] r = rows.get(i);
                x[i] = r[0]; y[i] = r[1]; dt[i] = r[2]; p[i] = (byte) r[3]; tx[i] = (byte) r[4]; ty[i] = (byte) r[5];
            }
            return new Entry(serial, code, px, darkness, x, y, dt, p, tx, ty);
        }
    }

    private final List<Entry> entries;
    private final int samples;
    private StrokeLog(List<Entry> entries, int samples) { this.entries = entries; this.samples = samples; }

    /** The strokes since the last visible Clear page marker. */
    List<Entry> strokes() {
        int from = 0;
        for (int i = 0; i < entries.size(); i++) if (entries.get(i).code == null) from = i + 1;
        return entries.subList(from, entries.size());
    }
    boolean isEmpty() { return strokes().isEmpty(); }
    int samples() { return samples; }

    /** The log with the entries of Undo steps up to {@code top} (the rest were undone). */
    StrokeLog upTo(long top) {
        List<Entry> kept = new ArrayList<>();
        int n = 0;
        for (Entry e : entries) if (e.serial <= top) { kept.add(e); n += e.size(); }
        return kept.size() == entries.size() ? this : new StrokeLog(kept, n);
    }
    /** {@code entry} after the steps up to {@code top}; the oldest entries go while it would pass {@link #MOST_SAMPLES} (a stroke that alone does is not kept). */
    StrokeLog added(Entry entry, long top) {
        StrokeLog base = upTo(top);
        if (entry == null || entry.size() > MOST_SAMPLES) return base;
        List<Entry> next = new ArrayList<>(base.entries); next.add(entry);
        int n = base.samples + entry.size();
        while (n > MOST_SAMPLES) n -= next.remove(0).size();
        return new StrokeLog(next, n);
    }
    /** What the file keeps at step {@code top}: the visible strokes, numbered from the start of a new session. */
    StrokeLog saved(long top) {
        List<Entry> kept = new ArrayList<>();
        int n = 0;
        for (Entry e : upTo(top).strokes()) { kept.add(e.at(0)); n += e.size(); }
        return kept.isEmpty() ? NONE : new StrokeLog(kept, n);
    }

    // ---- File format: version byte, stroke count, then per stroke code, px, darkness, sample count and samples ----

    byte[] encode() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(VERSION);
        List<Entry> list = strokes();
        varint(out, list.size());
        for (Entry e : list) {
            byte[] code = e.code.getBytes(StandardCharsets.UTF_8);
            varint(out, code.length); out.write(code, 0, code.length);
            int bits = Float.floatToIntBits(e.px), dark = Float.floatToIntBits(e.darkness);
            for (int s = 24; s >= 0; s -= 8) out.write(bits >>> s);
            for (int s = 24; s >= 0; s -= 8) out.write(dark >>> s);
            varint(out, e.size());
            int px = 0, py = 0;
            for (int i = 0; i < e.size(); i++) {
                varint(out, zigzag(e.x[i] - px)); varint(out, zigzag(e.y[i] - py)); px = e.x[i]; py = e.y[i];
                varint(out, e.dt[i]); out.write(e.pressure[i]); out.write(e.tiltX[i]); out.write(e.tiltY[i]);
            }
        }
        return out.toByteArray();
    }
    /** The log {@code bytes} keep; null when they don't read. */
    static StrokeLog decode(byte[] bytes) {
        try {
            int[] at = {0};
            if (bytes.length < 2 || bytes[at[0]++] != VERSION) return null;
            int count = (int) read(bytes, at, MOST_SAMPLES), total = 0;
            List<Entry> list = new ArrayList<>();
            for (int k = 0; k < count; k++) {
                int length = (int) read(bytes, at, 4096);
                if (at[0] + length + 9 > bytes.length) return null;
                String code = new String(bytes, at[0], length, StandardCharsets.UTF_8); at[0] += length;
                float px = Float.intBitsToFloat(int32(bytes, at)), dark = Float.intBitsToFloat(int32(bytes, at));
                int n = (int) read(bytes, at, MOST_SAMPLES - total);
                if (n == 0 || !(px > 0 && px < 4096) || !(dark >= 0 && dark <= 1)) return null;
                total += n;
                int[] x = new int[n], y = new int[n], dt = new int[n];
                byte[] p = new byte[n], tx = new byte[n], ty = new byte[n];
                int cx = 0, cy = 0;
                for (int i = 0; i < n; i++) {
                    cx += unzigzag(read(bytes, at, 1 << 20)); cy += unzigzag(read(bytes, at, 1 << 20));
                    x[i] = cx; y[i] = cy; dt[i] = (int) read(bytes, at, MOST_PAUSE);
                    if (at[0] + 3 > bytes.length) return null;
                    p[i] = bytes[at[0]++]; tx[i] = bytes[at[0]++]; ty[i] = bytes[at[0]++];
                }
                list.add(new Entry(0, code, px, dark, x, y, dt, p, tx, ty));
            }
            return at[0] == bytes.length ? (list.isEmpty() ? NONE : new StrokeLog(list, total)) : null;
        } catch (IOException | RuntimeException bad) { return null; }
    }

    private static int zigzag(int v) { return (v << 1) ^ (v >> 31); }
    private static int unzigzag(long v) { return (int) (v >>> 1) ^ -(int) (v & 1); }
    private static void varint(ByteArrayOutputStream out, int v) {
        while ((v & ~0x7f) != 0) { out.write((v & 0x7f) | 0x80); v >>>= 7; }
        out.write(v);
    }
    private static long read(byte[] b, int[] at, long most) throws IOException {
        long v = 0;
        for (int shift = 0; shift < 35; shift += 7) {
            if (at[0] >= b.length) throw new IOException("short");
            int c = b[at[0]++] & 255;
            v |= (long) (c & 0x7f) << shift;
            if ((c & 0x80) == 0) { if (v > most) throw new IOException("big"); return v; }
        }
        throw new IOException("long");
    }
    private static int int32(byte[] b, int[] at) throws IOException {
        if (at[0] + 4 > b.length) throw new IOException("short");
        int v = (b[at[0]] & 255) << 24 | (b[at[0] + 1] & 255) << 16 | (b[at[0] + 2] & 255) << 8 | (b[at[0] + 3] & 255);
        at[0] += 4; return v;
    }

    // ---- Replay: the strokes fed back in chunks of pen time ----

    /** Where a {@link Player} puts what it plays. {@code time} is ms from the stroke's start, plus 1000 (never 0). */
    interface Sink {
        void begin(Entry e);
        void sample(Entry e, int i, long time);
        void end(Entry e);
    }

    /**
     * Plays strokes one after another in steps: each {@link #step} feeds the samples of the next {@code ms} of pen time
     * (a stroke's pauses count, the time between strokes doesn't), so a replay shows a chunk at a time, as e-ink can.
     */
    static final class Player {
        private final List<Entry> list;
        private int index, at;
        private long clock;
        Player(List<Entry> strokes) { list = strokes; }
        boolean done() { return index >= list.size(); }
        /** The strokes not played yet, a started one counted. */
        int left() { return list.size() - index; }
        void step(long ms, Sink sink) {
            long budget = ms;
            while (!done() && budget > 0) {
                Entry e = list.get(index);
                if (at == 0) { clock = 1000; sink.begin(e); }
                while (at < e.size() && budget > 0) { clock += e.dt[at]; budget -= e.dt[at]; sink.sample(e, at, clock); at++; }
                if (at >= e.size()) { sink.end(e); index++; at = 0; }
            }
        }
    }
    /**
     * Whether a touch on the page ends a replay: a {@code press} (a pointer landing) of the {@code pen}, or of a finger no
     * broader than a palm ({@link FingerTool#PALM_DP}; {@code contactDp} across). A hand resting on the page, its moves and
     * lifts, never does: it was there as the replay began (2026-10-25: it ended every replay at once).
     */
    static boolean endsReplay(boolean press, boolean pen, float contactDp) { return press && (pen || contactDp <= FingerTool.PALM_DP); }
}
