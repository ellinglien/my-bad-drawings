package io.github.mpdairy.monopaint;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Samples drawn on one background thread, so the screen never waits on them: Brush Lab's (20–35 at once) and the
 * bar's slot tray strokes. Newest request first, a newer request for the same owner replacing the older, each result
 * handed to its {@code done} on the main thread unless a newer request or {@link #cancel} came since. Results are
 * handed back together, in one main-thread turn, when the queue runs dry (or once the first has waited {@link #HOLD_MS}),
 * so the views they change are drawn in one frame and the e-ink panel updates once, not once a sample. The work must
 * only read what nothing else changes: a copy of the brush, a page of its own ({@link WildSample}'s cache is synchronized).
 */
final class SampleQueue {
    private final HandlerThread thread = new HandlerThread("Samples", Process.THREAD_PRIORITY_BACKGROUND);
    private final Handler worker, main = new Handler(Looper.getMainLooper());
    /** Waiting work, newest first, and each owner's latest request; both guarded by {@code jobs}. */
    private final ArrayDeque<Job<?>> jobs = new ArrayDeque<>();
    private final Map<Object, Job<?>> latest = new HashMap<>();
    /** Results waiting to be handed back together, and when the first of them was ready; guarded by {@code jobs}. */
    private final List<Runnable> ready = new ArrayList<>();
    private long readySince;
    /** The longest a ready result waits for the others, ms (it waits at most one more sample past this). */
    static final long HOLD_MS = 400;
    private volatile boolean closed;

    SampleQueue() {
        thread.start();
        worker = new Handler(thread.getLooper());
    }

    /** Runs {@code work} in the background, then {@code done} with its result on the main thread, if still wanted. */
    <T> void request(Object owner, Supplier<T> work, Consumer<T> done) { request(owner, work, done, null); }
    /** As above; a result no longer wanted (a newer request, {@link #cancel}, {@link #close}) goes to {@code dropped}, e.g. to recycle it. */
    <T> void request(Object owner, Supplier<T> work, Consumer<T> done, Consumer<T> dropped) {
        if (closed) return;
        Job<T> job = new Job<>(owner, work, done, dropped);
        synchronized (jobs) {
            Job<?> old = latest.put(owner, job);
            if (old != null) jobs.remove(old);
            jobs.addFirst(job);
        }
        worker.post(this::next);
    }
    /** Forgets {@code owner}'s request: its work is dropped if waiting, its result if running. */
    void cancel(Object owner) {
        synchronized (jobs) {
            Job<?> old = latest.remove(owner);
            if (old != null) jobs.remove(old);
        }
    }
    /** Stops the thread once the sample in hand is done; nothing more is handed back. */
    void close() {
        closed = true;
        synchronized (jobs) { jobs.clear(); latest.clear(); }
        thread.quit();
    }

    private void next() {
        Job<?> job;
        synchronized (jobs) { job = jobs.pollFirst(); }
        // Nothing left (the rest replaced or cancelled): what is ready goes back now.
        if (job == null) handBack();
        else if (!closed) job.run();
    }
    /** Holds {@code delivery} with the others ready, handing them all back once the queue is dry or the first has waited long enough. */
    private void ready(Runnable delivery) {
        boolean now;
        synchronized (jobs) {
            long time = SystemClock.uptimeMillis();
            if (ready.isEmpty()) readySince = time;
            ready.add(delivery);
            now = jobs.isEmpty() || time - readySince >= HOLD_MS;
        }
        if (now) handBack();
    }
    /** Hands back every ready result in one main-thread turn. */
    private void handBack() {
        List<Runnable> batch;
        synchronized (jobs) {
            if (ready.isEmpty()) return;
            batch = new ArrayList<>(ready);
            ready.clear();
        }
        main.post(() -> { for (Runnable delivery : batch) delivery.run(); });
    }

    private final class Job<T> {
        final Object owner;
        final Supplier<T> work;
        final Consumer<T> done, dropped;
        Job(Object owner, Supplier<T> work, Consumer<T> done, Consumer<T> dropped) { this.owner = owner; this.work = work; this.done = done; this.dropped = dropped; }

        void run() {
            T result;
            try { result = work.get(); }
            catch (RuntimeException | OutOfMemoryError error) {
                // A sample that can't be drawn stays white; the screen goes on.
                Log.w(ProbeActivity.TAG, "Sample failed", error);
                result = null;
            }
            T made = result;
            ready(() -> {
                boolean wanted;
                synchronized (jobs) {
                    wanted = latest.get(owner) == this;
                    if (wanted) latest.remove(owner);
                }
                if (made == null) return;
                if (wanted && !closed) done.accept(made);
                else if (dropped != null) dropped.accept(made);
            });
        }
    }
}
