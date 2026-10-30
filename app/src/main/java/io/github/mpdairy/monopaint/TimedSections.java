package io.github.mpdairy.monopaint;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Timed Sections (the owner, 2026-10-21; Lynda Barry's diary): a template's boxes timed one after another, as the Diary Page's
 * Did 2:30, Saw 2:30, Heard 0:30, Picture 0:30 ({@link NotebookTemplate#sections}; the times editable in the prompts file,
 * {@link DailyPrompt#sectionTimes}). Immutable and kept in the page's own record ({@link DocumentCodec}'s {@code SECT} section,
 * only once started): each section's length and the wall-clock time it ends, so a page reopened, or the app restarted, carries
 * on where the clock is. The time runs on whether the page shows or not, as a kitchen timer does. A skip ends the section
 * now and the ones after it follow on in full. Under the notebook's Lock Pages a section ended a day ago ({@link #LOCK_MS})
 * is locked: its box takes no more marks ({@link ToneDocument#fence}); otherwise it stays open (the owner, 2026-10-27). What the page shows of them (the bold outline, the faint dim, the flash) is
 * drawn over the page alone ({@link #draw}), never in it or its copies. Pure, checked on the desktop ({@code TimedSectionsChecks}).
 */
final class TimedSections {
    static final TimedSections NONE = new TimedSections(new int[0], new long[0]);
    /** How long a finished section stays open, ms: a day. */
    static final long LOCK_MS = 24L * 3600_000L;
    /** The most sections a record keeps, and the longest a section may be, s (as a Daily Prompt card). */
    static final int MOST = 16, MOST_SECONDS = DailyPrompt.MOST_SECONDS;
    /** The notes (docs/TEXT-STYLE.md): the next section's, the end's, a locked box's. */
    static final String NEXT = "Next: ", DONE = "Done", LOCKED = "Section locked";

    /** Each section's length, s, and the wall-clock time it ends, ms (none before the start). */
    private final int[] seconds;
    private final long[] ends;
    private TimedSections(int[] seconds, long[] ends) { this.seconds = seconds; this.ends = ends; }

    /** Sections of {@code seconds} started at {@code now}: each ends when the one before it has, plus its own time. */
    static TimedSections start(int[] seconds, long now) {
        if (seconds == null || seconds.length == 0 || seconds.length > MOST) return NONE;
        long[] ends = new long[seconds.length];
        for (int i = 0; i < seconds.length; i++) ends[i] = (i == 0 ? now : ends[i - 1]) + seconds[i] * 1000L;
        return new TimedSections(seconds.clone(), ends);
    }
    /** Whether they have started (a file keeps nothing otherwise). */
    boolean started() { return ends.length > 0; }
    int count() { return ends.length; }
    /** The section running at {@code now}: −1 before the start, {@link #count} once all have ended. */
    int active(long now) {
        if (!started()) return -1;
        int i = 0;
        while (i < ends.length && now >= ends[i]) i++;
        return i;
    }
    /** The time the running section has left, ms (0 when none runs). */
    long remaining(long now) {
        int i = active(now);
        return i < 0 || i >= ends.length ? 0 : ends[i] - now;
    }
    /** The section running ends {@code now}; the ones after it follow on in full. Itself when none runs. */
    TimedSections skip(long now) {
        int k = active(now);
        if (k < 0 || k >= ends.length) return this;
        long[] next = ends.clone();
        next[k] = now;
        for (int i = k + 1; i < next.length; i++) next[i] = next[i - 1] + seconds[i] * 1000L;
        return new TimedSections(seconds, next);
    }
    /** Whether section {@code i} ended a day or more before {@code now}: its box takes no marks. */
    boolean locked(int i, long now) { return started() && i < ends.length && now - ends[i] >= LOCK_MS; }
    /** How long until a section ends or locks, ms; −1 when nothing is left to happen. */
    long nextChange(long now) {
        long next = Long.MAX_VALUE;
        for (long end : ends) {
            if (end > now) next = Math.min(next, end - now);
            else if (end + LOCK_MS > now) next = Math.min(next, end + LOCK_MS - now);
        }
        return next == Long.MAX_VALUE ? -1 : next;
    }

    /** The countdown in the side bar: the section's name over its time ("Saw" "1:42", shown in caps), or {@link #DONE}. */
    static String countdown(String name, long remaining) { return name == null ? DONE : name + "\n" + NotebookRules.countdown(remaining); }

    /** The record: a byte for the count, then each section's seconds (an int) and end (a long). A later version may add more after it. */
    byte[] encode() {
        ByteBuffer out = ByteBuffer.allocate(1 + 12 * ends.length);
        out.put((byte) ends.length);
        for (int i = 0; i < ends.length; i++) out.putInt(seconds[i]).putLong(ends[i]);
        return out.array();
    }
    /** The sections {@code bytes} keep ({@code length} of them); null when they don't read. */
    static TimedSections decode(byte[] bytes, int length) {
        if (length < 1 || bytes.length < length) return null;
        int n = bytes[0] & 255;
        if (n < 1 || n > MOST || length < 1 + 12 * n) return null;
        ByteBuffer in = ByteBuffer.wrap(bytes, 1, 12 * n);
        int[] seconds = new int[n]; long[] ends = new long[n];
        for (int i = 0; i < n; i++) {
            seconds[i] = in.getInt(); ends[i] = in.getLong();
            if (seconds[i] < 0 || seconds[i] > MOST_SECONDS || i > 0 && ends[i] < ends[i - 1]) return null;
        }
        return new TimedSections(seconds, ends);
    }

    /** The bold outline's width round the running section, px, for a page {@code width} wide: 6 at the Manta's 1920. */
    static int band(int width) { return Math.max(2, Math.round(width * 6 / 1920f)); }
    /** The faint dim over the other sections: a light grey of the panel's. */
    static final int DIM = GrayPalette.VALUES[14];
    /**
     * Draws over {@code pixels} ({@code w x h} at page {@code x, y}, ARGB; {@link DotPattern}'s dots when {@code dots}, else
     * plain greys) what the page shows of its sections: the running one's box {@code rects[active]} outlined bold in ink, the
     * others dimmed faintly, and {@code flash} (a box, or null) its outline inverted, three bands wide. Whether it changed any.
     */
    static boolean draw(int[] pixels, int x, int y, int w, int h, boolean dots, int[][] rects, int active, int[] flash, int band) {
        boolean changed = false;
        for (int r = 0; rects != null && active >= 0 && active < rects.length && r < rects.length; r++) {
            int[] b = rects[r];
            int x0 = Math.max(x, b[0]), y0 = Math.max(y, b[1]), x1 = Math.min(x + w, b[2]), y1 = Math.min(y + h, b[3]);
            for (int py = y0; py < y1; py++) for (int px = x0; px < x1; px++) {
                int i = (py - y) * w + px - x, p = pixels[i];
                if (r == active) {
                    if (!edge(b, px, py, band) || p == 0xff000000) continue;
                    pixels[i] = 0xff000000;
                } else if (dots) {
                    if (p == 0xff000000 || DotPattern.pixel(DIM, px, py) != 0xff000000) continue;
                    pixels[i] = 0xff000000;
                } else {
                    int g = p & 255;
                    if (g <= DIM) continue;
                    pixels[i] = 0xff000000 | DIM * 0x010101;
                }
                changed = true;
            }
        }
        if (flash != null) {
            int x0 = Math.max(x, flash[0]), y0 = Math.max(y, flash[1]), x1 = Math.min(x + w, flash[2]), y1 = Math.min(y + h, flash[3]);
            for (int py = y0; py < y1; py++) for (int px = x0; px < x1; px++) {
                if (!edge(flash, px, py, 3 * band)) continue;
                int i = (py - y) * w + px - x;
                pixels[i] = 0xff000000 | (255 - (pixels[i] & 255)) * 0x010101;
                changed = true;
            }
        }
        return changed;
    }
    /** Whether {@code px, py} lies in box {@code b}'s outline, {@code band} px in from its edges. */
    private static boolean edge(int[] b, int px, int py, int band) { return px < b[0] + band || py < b[1] + band || px >= b[2] - band || py >= b[3] - band; }
    /** The box round all of {@code rects}, {x0, y0, x1, y1}; null for none. */
    static int[] around(int[][] rects) {
        if (rects == null || rects.length == 0) return null;
        int[] a = rects[0].clone();
        for (int[] r : rects) { a[0] = Math.min(a[0], r[0]); a[1] = Math.min(a[1], r[1]); a[2] = Math.max(a[2], r[2]); a[3] = Math.max(a[3], r[3]); }
        return a;
    }

    @Override public boolean equals(Object other) {
        return other instanceof TimedSections && Arrays.equals(seconds, ((TimedSections) other).seconds) && Arrays.equals(ends, ((TimedSections) other).ends);
    }
    @Override public int hashCode() { return Arrays.hashCode(ends); }
}
