package io.github.mpdairy.monopaint;

import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;

/**
 * Replay page (Record and Replay, {@link StrokeLog}): the page's recorded strokes drawn again on a blank copy of the
 * page, a chunk of pen time at a time, so e-ink shows steps rather than a smear. The page itself is never touched: when
 * the strokes are done (after a pause to look), or when the pen or a finger lands ({@link #ends}), it is shown again as
 * it was. The copy shows under a veil of paper wherever the page shows no mark ({@link GhostPlane#offMarks}), so a
 * stroke since erased or moved (neither logged) doesn't come back as a blob. Pen strokes with a brush are all there is
 * (no eraser, smudge, fill, shape, finger, mirror copies, or a pen held still).
 */
final class PageReplay {
    /** Wall time between steps, ms; and the pen time each step draws at the first speed, ms (about 5 times as fast as drawn). */
    static final int STEP_MS = 600, PEN_MS = 3000;
    /** The speeds: 1×, 2× and 4× of {@link #PEN_MS}; their menu's width, dp. */
    static final int[] SPEEDS = {1, 2, 4};
    static final int MENU_DP = 120;
    /** How long the finished replay stays, ms. */
    static final long HOLD_MS = 3000;
    static final String NOTHING = "Nothing to replay";
    /** The drawing screen menu's row for it. */
    static final String REPLAY_PAGE = "Replay Page";

    private final PaintActivity app;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ToneDocument original, copy;
    private StrokeLog.Player player;
    private DrawingStroke stroke;
    /** Over the copy while it plays: the paper wherever the page shows no mark ({@link GhostPlane#offMarks}). */
    private GhostPlane.Planes veil;

    PageReplay(PaintActivity app) { this.app = app; }

    boolean running() { return original != null; }
    /** The plane over {@code page} as it shows ({@link Ghosts#plane}): the veil over the replay's copy, else null. */
    GhostPlane.Planes veil(ToneDocument page) { return page != null && page == copy ? veil : null; }
    /** Whether the current page has strokes to replay. */
    boolean available() { return app.pad.document != null && !app.pad.document.replay().isEmpty(); }

    private int speed = 1;
    /**
     * Replay page: the speeds in a menu where the menu it was picked from stood, beside {@code anchor}, as Play's Mirrors
     * opens (the owner, 2026-10-25, of the chips beside the eraser: "why would it appear beside the eraser way far away
     * from the menu where you clicked?"); a tap on one starts the replay.
     */
    void choose(View anchor) {
        if (running() || app.busy()) return;
        if (!available()) { app.message(NOTHING); return; }
        String[] texts = new String[SPEEDS.length];
        Runnable[] actions = new Runnable[SPEEDS.length];
        for (int i = 0; i < texts.length; i++) { int speed = SPEEDS[i]; texts[i] = speed + "×"; actions[i] = () -> start(speed); }
        app.menu(anchor, "replay menu", texts, new int[texts.length], actions, MENU_DP);
    }
    /**
     * Whether {@code event} on the page ends a replay ({@link StrokeLog#endsReplay}): a pen or a finger landing, not a hand
     * resting (2026-10-25: a palm's touches, already down as the replay began, ended it at once, the page put back before
     * e-ink showed the blank copy, so the owner "havent been able to get replay to work").
     */
    static boolean ends(MotionEvent event) {
        int action = event.getActionMasked(), index = event.getActionIndex();
        return StrokeLog.endsReplay(action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN, DrawingPad.isPen(event, index),
                event.getTouchMajor(index) / android.content.res.Resources.getSystem().getDisplayMetrics().density);
    }

    void start(int speed) {
        if (running() || app.busy()) return;
        this.speed = speed;
        app.pad.finishStroke(); app.pad.dryWet(); app.pad.settleDrying();
        ToneDocument page = app.pad.document;
        java.util.List<StrokeLog.Entry> strokes = page.replay();
        if (strokes.isEmpty()) { app.message(NOTHING); return; }
        ToneDocument.Snapshot shot = page.layerSnapshot();
        ToneDocument blank = new ToneDocument(shot);
        blank.clearPage();
        original = page; copy = blank; veil = GhostPlane.offMarks(shot); player = new StrokeLog.Player(strokes);
        app.pad.showPage(copy);
        handler.postDelayed(this::step, STEP_MS);
    }

    private final StrokeLog.Sink sink = new StrokeLog.Sink() {
        @Override public void begin(StrokeLog.Entry e) { stroke = app.inkWash.replay(copy, e.code, e.px, e.darkness); }
        @Override public void sample(StrokeLog.Entry e, int i, long time) {
            if (stroke != null) stroke.sample(e.px(i), e.py(i), e.pressure(i), e.tiltX(i), e.tiltY(i), time);
        }
        @Override public void end(StrokeLog.Entry e) { if (stroke != null) { stroke.finish(); stroke = null; } }
    };

    private void step() {
        if (!running()) return;
        // The page was changed from under it (a page turn, a new notebook): nothing to put back.
        if (app.pad.document != copy) { forget(); return; }
        player.step((long) PEN_MS * speed, sink);
        if (stroke != null) stroke.present();
        app.pad.replayShow();
        handler.postDelayed(player.done() ? this::stop : this::step, player.done() ? HOLD_MS : STEP_MS);
    }

    /** Ends the replay and shows the page as it was. */
    void stop() {
        if (!running()) return;
        handler.removeCallbacksAndMessages(null);
        if (stroke != null) { stroke.finish(); stroke = null; }
        app.pad.dryWet(); app.pad.settleDrying();
        ToneDocument page = original;
        forget();
        app.pad.showPage(page);
    }
    private void forget() { handler.removeCallbacksAndMessages(null); original = copy = null; player = null; stroke = null; veil = null; }
}
