package io.github.mpdairy.monopaint;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The drawing screen's tracing-paper ghosts (docs/archive/superpowers/specs/2026-10-20-tracing-paper-design.md, "When the plane
 * is built"): one {@link GhostPlane}, kept by what it is made of ({@link DrawingBook#ghostKey}) and built again only when
 * that changes, on a worker of its own. A page turned to shows with its plane if it is ready within {@link #WAIT_MS},
 * else the plane follows in one more full update; a change of Paper, step, ghost or order builds it {@link #SETTLE_MS}
 * after the last, so several taps show once. Never under the pen.
 */
final class Ghosts {
    static final long WAIT_MS = 300, SETTLE_MS = 300;
    private final PaintActivity app;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "ghosts"); t.setDaemon(true); return t; });
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable rebuild = this::rebuild;
    /** The plane kept and what it is of (null: none). */
    private String key;
    private GhostPlane.Planes plane;
    /** The plane being built and what it will be of. */
    private String building;
    private Future<GhostPlane.Planes> pending;

    Ghosts(PaintActivity app) { this.app = app; }

    /** The plane for {@code page}, about to show: the one kept, else one built now within {@link #WAIT_MS}; null for none (nothing to ghost); for another page shown, Replay's veil or none. */
    GhostPlane.Planes plane(ToneDocument page) {
        DrawingBook book = app.book;
        if (book == null || book.current() != page) return app.replay.veil(page);
        String want = book.ghostKey();
        if (want == null) return null;
        if (want.equals(key)) return plane;
        try { return start(book, want).get(WAIT_MS, TimeUnit.MILLISECONDS); }
        catch (TimeoutException late) { return null; }
        catch (Exception failed) { Log.w(ProbeActivity.TAG, "Could not show the ghosts", failed); return null; }
    }
    /** The Paper, a step, the ghost or the pages changed: the plane is built again once the taps settle, if what it is of changed. */
    void changed() { handler.removeCallbacks(rebuild); handler.postDelayed(rebuild, SETTLE_MS); }

    private void rebuild() {
        DrawingBook book = app.book;
        if (book == null || app.pad == null || app.pad.document != book.current()) return;
        String want = book.ghostKey();
        if (want == null) { key = null; plane = null; app.pad.showGhost(null); }
        else if (want.equals(key)) app.pad.showGhost(plane);
        else start(book, want);
    }
    /** The plane {@code want} names, building (or still being built) on the worker; it shows itself when done. */
    private Future<GhostPlane.Planes> start(DrawingBook book, String want) {
        if (want.equals(building) && pending != null) return pending;
        if (pending != null) pending.cancel(false);
        building = want;
        java.util.List<GhostPlane.Source> sources = book.ghostSources();
        int width = book.current().width, height = book.current().height;
        return pending = worker.submit(() -> {
            GhostPlane.Planes made = null;
            // Only what the pen's thread leaves of the cores.
            Cores.quiet(true);
            try { synchronized (DocumentCodec.ONE_PAGE) { made = GhostPlane.build(width, height, sources); } }
            catch (Exception | OutOfMemoryError failed) { Log.w(ProbeActivity.TAG, "Could not make the ghosts", failed); }
            GhostPlane.Planes done = made;
            handler.post(() -> built(want, done));
            return made;
        });
    }
    /** The plane {@code want} names is {@code made}: kept, and shown if the page still wants it, once the pen is up. */
    private void built(String want, GhostPlane.Planes made) {
        if (!want.equals(building)) return;
        building = null; pending = null; key = want; plane = made;
        show(want);
    }
    private void show(String want) {
        DrawingBook book = app.book;
        if (book == null || app.pad == null || app.pad.document != book.current() || !want.equals(book.ghostKey()) || !want.equals(key)) return;
        if (app.pad.stroking() || app.busy()) { handler.postDelayed(() -> show(want), SETTLE_MS); return; }
        app.pad.showGhost(plane);
    }
}
