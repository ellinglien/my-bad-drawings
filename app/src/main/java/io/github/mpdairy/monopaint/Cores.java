package io.github.mpdairy.monopaint;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * The engine's helper threads: work cut into independent pieces (bands of rows that share no tile, columns of a blur)
 * is shared out over the Manta's four cores, the caller taking pieces too, and {@link #each} returns once all are done.
 * A piece's result never depends on which thread ran it or in what order, so the page is the same with or without
 * helpers. A piece must not itself share work out (one from a helper runs in place). Pure.
 */
final class Cores {
    /** Whether work is shared out at all; off in the checks that show the result is the same. */
    static boolean shared = true;
    /** Threads besides the caller's: the Manta has four cores. */
    static final int HELPERS = Math.max(0, Math.min(3, Runtime.getRuntime().availableProcessors() - 1));
    /** Workers a piece may be run by, the caller's 0 and each helper's own number; for per-worker scratch. */
    static final int WORKERS = HELPERS + 1;

    /** One piece of work: {@code piece} of the pieces, run by worker {@code worker} (0 to {@link #WORKERS} − 1). */
    interface Piece { void run(int piece, int worker); }

    private static java.util.concurrent.ExecutorService helpers, quiet;
    private static synchronized java.util.concurrent.ExecutorService helpers() {
        if (helpers == null) helpers = pool("Inkwash core ", Thread.NORM_PRIORITY);
        return helpers;
    }
    /** The quiet helpers ({@link #quiet}): their own threads, at the lowest priority. */
    private static synchronized java.util.concurrent.ExecutorService quietHelpers() {
        if (quiet == null) quiet = pool("Inkwash quiet core ", Thread.MIN_PRIORITY);
        return quiet;
    }
    private static java.util.concurrent.ExecutorService pool(String name, int priority) {
        AtomicInteger made = new AtomicInteger();
        return java.util.concurrent.Executors.newFixedThreadPool(HELPERS, r -> {
            Thread t = new Thread(r, name + made.incrementAndGet()); t.setDaemon(true); t.setPriority(priority); return t;
        });
    }
    /** Whether this thread shares its work with the quiet helpers ({@link #quiet}). */
    private static final ThreadLocal<boolean[]> QUIET = ThreadLocal.withInitial(() -> new boolean[1]);
    /**
     * From now on this thread shares its work with helpers of its own at the lowest priority ({@code on}), or with the
     * usual ones again: a background render (a quick stroke's, {@link QuickStroke}) then takes only what the pen's thread
     * leaves of the cores, and never holds the usual helpers the pen's thread waits on.
     */
    static void quiet(boolean on) { QUIET.get()[0] = on; }
    /** The worker the current thread is, while it runs a piece for {@link #each}; 0 outside. */
    private static final ThreadLocal<int[]> WORKER = ThreadLocal.withInitial(() -> new int[]{-1});

    /** Runs pieces 0 to {@code pieces} − 1, on the helpers and this thread, and returns when every one has run. */
    static void each(int pieces, Piece work) {
        int[] me = WORKER.get();
        int helping = shared && me[0] < 0 ? Math.min(HELPERS, pieces - 1) : 0;
        if (helping <= 0) {
            // In place: one piece, no helpers, or called from a piece.
            int worker = Math.max(0, me[0]);
            for (int k = 0; k < pieces; k++) work.run(k, worker);
            return;
        }
        AtomicInteger next = new AtomicInteger();
        java.util.concurrent.Future<?>[] done = new java.util.concurrent.Future<?>[helping];
        java.util.concurrent.ExecutorService pool = QUIET.get()[0] ? quietHelpers() : helpers();
        for (int i = 0; i < helping; i++) {
            int worker = i + 1;
            done[i] = pool.submit(() -> take(next, pieces, work, worker));
        }
        take(next, pieces, work, 0);
        try { for (java.util.concurrent.Future<?> f : done) f.get(); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
        catch (java.util.concurrent.ExecutionException e) { throw rethrow(e.getCause()); }
    }
    /**
     * What another thread's work failed with, to throw on this one: an error as it is (thrown here), an unchecked
     * exception as it is, anything else wrapped. Use as {@code throw Cores.rethrow(cause)}.
     */
    static RuntimeException rethrow(Throwable cause) {
        if (cause instanceof Error) throw (Error)cause;
        return cause instanceof RuntimeException ? (RuntimeException)cause : new IllegalStateException(cause);
    }
    /** Runs pieces until none is left, as {@code worker}. */
    private static void take(AtomicInteger next, int pieces, Piece work, int worker) {
        int[] me = WORKER.get();
        int was = me[0];
        me[0] = worker;
        try { for (int k; (k = next.getAndIncrement()) < pieces; ) work.run(k, worker); }
        finally { me[0] = was; }
    }

    /** Work on rows {@code top..bottom} (inclusive), by {@code worker}. */
    interface Rows { void run(int top, int bottom, int worker); }
    /** Bands of work covering fewer pixels than this run in turn on the caller's thread: handing them out costs more. */
    static final long SHARE_PX = 32 * 1024;
    /**
     * Rows {@code y0..y1} in {@link #bands} of {@code side}, so no two share a tile row: on several cores when there are
     * two or more and they cover {@code area} pixels or more ({@link #SHARE_PX}), else in turn on this thread.
     */
    static void inBands(int y0, int y1, int side, long area, Rows work) {
        int[] tops = bands(y0, y1, side);
        int n = tops.length - 1;
        if (n > 1 && area >= SHARE_PX) each(n, (k, worker) -> work.run(tops[k], tops[k + 1] - 1, worker));
        else for (int k = 0; k < n; k++) work.run(tops[k], tops[k + 1] - 1, 0);
    }

    /**
     * Rows {@code y0..y1} (inclusive) cut where a multiple of {@code side} begins, so no two pieces share a tile row:
     * the first row of each piece, then {@code y1 + 1}.
     */
    static int[] bands(int y0, int y1, int side) {
        int n = y1 < y0 ? 0 : y1 / side - y0 / side + 1;
        int[] tops = new int[n + 1];
        for (int k = 0; k < n; k++) tops[k] = k == 0 ? y0 : (y0 / side + k) * side;
        tops[n] = y1 + 1;
        return tops;
    }

    private Cores() {}
}
