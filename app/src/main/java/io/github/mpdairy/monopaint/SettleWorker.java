package io.github.mpdairy.monopaint;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * The thread a session's wet paint does its work on after lift ({@link WatercolorSession}): a Slow wash's settling
 * ticks ({@link DryingWater#work}, its gravity flow's too), a Float bath's drift and print ({@link FloatWater#work}),
 * and what laying the wash or printing the bath now would write, worked out ahead. The page's thread only writes what
 * the work laid, so a pen-down never waits for a tick. One job at a time, in turn; the session reads the page there
 * only while nothing else changes it, and drops work whose page changed. At the lowest priority, sharing work with
 * {@link Cores}' quiet helpers: anything on the page's thread, and a stroke's own work, comes first. Pure.
 */
final class SettleWorker {
    /** How often the session looks in on a job still running, ms (as {@link QuickLift} does on a render). */
    static final long POLL_MS = 5;

    /** One thread, made when work comes and gone a few seconds after the last (a page left alone holds none). */
    private final ThreadPoolExecutor thread = new ThreadPoolExecutor(0, 1, 5, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), r -> {
        Thread t = new Thread(() -> { Cores.quiet(true); r.run(); }, "Inkwash settle");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    /** One piece of work there: its result once done, and how long it ran there. */
    static final class Job<T> extends FutureTask<T> {
        /** How long the work ran, ns (0 until done). */
        volatile long nanos;
        private Job(Callable<T> work) { this(new long[1], work); }
        private Job(long[] took, Callable<T> work) {
            super(() -> {
                long t0 = System.nanoTime();
                try { return work.call(); } finally { took[0] = System.nanoTime() - t0; }
            });
            this.took = took;
        }
        private final long[] took;
        @Override protected void done() { nanos = took[0]; }
        /**
         * Its result, waiting for it if it is still running; what the work threw, thrown again here. Only for work still
         * wanted: work let go ({@link WatercolorSession}) may fail on a page that changed under it, and is never asked.
         */
        T result() {
            boolean interrupted = false;
            try {
                while (true) {
                    try { return get(); }
                    catch (InterruptedException e) { interrupted = true; }
                }
            } catch (ExecutionException e) {
                throw Cores.rethrow(e.getCause());
            } finally {
                if (interrupted) Thread.currentThread().interrupt();
            }
        }
    }

    /** Runs {@code work} there, after any job before it. */
    <T> Job<T> run(Callable<T> work) {
        Job<T> job = new Job<>(work);
        thread.execute(job);
        return job;
    }
}
