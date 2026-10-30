package io.github.mpdairy.monopaint;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import java.util.Collections;

/**
 * Brush Lab's test area, shared with the Tools page: the test page ({@link BrushTestPad}, in its own
 * {@link WatercolorSession} paced as the drawing screen's, its settling on a {@link SettleWorker}) in a 2 dp ink frame,
 * nothing over it; for the head row over it Clear and the wet indicator, through which the tilt feed listens (so
 * Gravity runs on the test page); and the brush's loop in the look's grey, which Brush Lab puts right beside the
 * brush's name ({@link #blockLoop}), so the name and what it draws always show together. The host says which brush and
 * look the strokes use; they are the drawing screen's strokes ({@link InkWash#stroke}).
 */
final class TestArea {
    interface Host {
        /** The brush the pen draws with. */
        Brush brush();
        /** Its size and grey. */
        ToolSwatches.Look look();
        /** The toolbar's swatches, whose greys a brush with Grey Shift picks from ({@link SwatchPicker}); null for their first. */
        default ToolSwatches swatches() { return null; }
    }

    final WatercolorSession session = new WatercolorSession();
    final BrushTestPad pad;
    /**
     * The brush's loop, About This Brush's in Brush Lab ({@link LabLayout#loopWidth}), directly left of its name (the
     * Tools page shows a tray stroke instead). No frame; drawn in the background ({@link #showLoop}).
     */
    final LoopCard blockLoop;
    private final Context context;
    private final Host host;
    /** Below this share of its grey's ink a Wet brush on the test page counts as run dry. */
    private static final float DRY = .25f;
    /** The last brush the test page drew with ({@link Brush#identity}), or null. */
    private String lastBrush;

    TestArea(Context context, SampleQueue samples, Host host) {
        this.context = context; this.host = host;
        // The name beside it says what it is.
        blockLoop = new LoopCard(context, samples, Collections.emptyList(), null);
        blockLoop.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        session.pace = new SettlePacer();
        session.worker = new SettleWorker();
        // A brush with Grey Shift picks its grey at each touch, as on the drawing screen.
        pad = new BrushTestPad(context, Device.current(context).landscapeTilt, session, (page, eraserEnd) -> {
            Brush brush = eraserEnd ? null : host.brush();
            // Another brush on the test page starts dipped (a Wet brush in its grey), as a brush never dipped does.
            String identity = brush == null ? null : brush.identity();
            if (identity != null && !identity.equals(lastBrush)) { session.held.dip(session.holder()); lastBrush = identity; }
            // A Wet brush run nearly dry dips itself again: the Tools page has no greys to dip it in, and a dry brush on a
            // fresh test page lays nothing (Brush Lab's greys dip it too).
            ToolSwatches.Look look = host.look();
            if (session.held.wetInk(session.holder()) < DRY * look.darkness) session.held.dip(session.holder());
            // And Gradient's Cycle steps through the toolbar's greys.
            session.swatches = host.swatches();
            return InkWash.stroke(page, brush, SwatchPicker.look(brush, look, host.swatches(), session.held, session.holder()), session);
        });
    }

    /** Clear, plain words, {@code padding} dp either side of its word: clears the test page. */
    LabButton clear(int padding) {
        LabButton clear = new LabButton(context, 0, "Clear", false);
        clear.setPadding(dp(padding), 0, dp(padding), 0);
        clear.setOnClickListener(v -> pad.clear());
        return clear;
    }
    /**
     * The droplet beside Clear while the test page's Slow wash is still settling; a tap lays it now. The tilt feed
     * listens through it: the test page tilts with the tablet (the accelerometer, only while something reads it).
     */
    WetIndicator wet() {
        WetIndicator wet = new WetIndicator(context, new WetIndicator.Host() {
            @Override public boolean wet() { return session.wet(); }
            @Override public boolean moving() { return session.moving(); }
            @Override public boolean stroking() { return pad.stroking(); }
            @Override public boolean onlyRendering() { return session.onlyRendering(); }
            @Override public void dry() { pad.dry(); }
            @Override public boolean hurrying() { return session.hurrying(); }
            @Override public void update(View indicator, Runnable change) { change.run(); indicator.invalidate(); }
        });
        session.wetChanged = wet::changed;
        wet.changed();
        new TiltFeed(wet, session);
        return wet;
    }
    /** The test page in a {@link Theme#FINE} dp ink frame, {@code width} dp wide, frame included (no grey: e-ink); nothing lies over the page. */
    View frame(int width) {
        LinearLayout test = LabUi.column(context);
        test.setBackgroundColor(Theme.INK);
        int rule = dp(Theme.FINE);
        test.setPadding(rule, rule, rule, rule);
        Ui.detach(pad);
        test.addView(pad, new LinearLayout.LayoutParams(-1, 0, 1));
        return test;
    }
    /** {@link #blockLoop}, taken from where it was, in {@code place}. */
    View blockLoop(LinearLayout.LayoutParams place) {
        Ui.detach(blockLoop);
        blockLoop.setLayoutParams(place);
        return blockLoop;
    }
    /**
     * The brush's loop at its own full darkness, whatever the current grey, at the size that fits the tile (as a library
     * card's), drawn in the background when the brush changed.
     */
    void showLoop() {
        Brush brush = host.brush();
        blockLoop.setSample(brush.identity(), LabLibrary.sampler(brush));
    }

    private int dp(float value) { return Ui.dp(context, value); }
}
