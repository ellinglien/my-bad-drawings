package io.github.mpdairy.monopaint;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.util.Log;
import android.view.View;

/**
 * The panel's fastest update for a pen stroke while the pen is down, on the bench only ({@link #wants}). The page's
 * pixels are black or white dots already, so a stroke can go to the panel through an update that drives only black and
 * white; when the stroke ends, the page's grey session ({@code DrawingPad}'s, Brush Lab's) shows everything the stroke
 * touched through {@link DirectEink#endFast}, which has the driver drive again every pixel the fast update changed (it
 * passes by a pixel asked for the value it records, and a plain grey update would ask that of every pixel the final
 * keeps as the preview showed it).
 *
 * <p>Which update is {@link Device#fastStrokeMode}'s. Measured on the Manta, firmware 2606141001 (engine spec, "Quick then render", panel findings): the e-ink controller
 * drives a frame about every 15 ms while an update runs; a grey update (mode 7) or the pen request MonoPaint uses for
 * its controls and previews (mode 9) keeps it going 18–19 frames, about 270 ms, whatever its size; driver mode 5 (flags
 * 0, the main plane) 8 frames, about 145 ms, and a stroke's run of overlapping updates is done 87 ms after the last one
 * rather than 200–220. Elsewhere (the Nomad, firmware 3.30.45 on) MonoPaint's own mode 9 path, where it has one, as its
 * shapes' previews use it; mode 5 is unmeasured there. Firmware modes not listed here are not tried: one of them (15)
 * restarted the Manta.
 */
final class StrokePanel {
    private StrokePanel() { }
    /**
     * The bench's switch: "quick" puts quick strokes on the fast path, "all" every pen stroke but the eraser's, "screen"
     * every screened stroke (Screen or Tone: the mischief modules spec's speed experiment, pure black and white, each
     * pixel inked once as the darkness only grows, nothing settling after); null or "gray", as the app draws, none.
     */
    static volatile String bench;
    /** The bench: false ends a fast session the old way, one forced gray update ({@link DirectEink#endFast}). */
    static volatile boolean redrive = true;
    /** The bench: the end asks again at a level next to the final rather than the page before the stroke (untried on the panel). */
    static volatile boolean endNear;

    /**
     * A session showing {@code bitmap} through the fast update ({@code toView} as {@link DirectEink#forView}), or null
     * where this tablet has none, or it can't open.
     */
    static DirectEink open(View owner, Bitmap bitmap, Matrix toView) {
        if ("gray".equals(bench) || DirectEink.androidDrawing() || DirectEink.layout()[0] == 0) return null;
        int mode = Device.current(owner.getContext()).fastStrokeMode();
        try {
            if (mode >= 0) return DirectEink.forView(owner, bitmap, toView, 0, mode);
            if (DirectEink.fastBinaryControls()) return DirectEink.forView(owner, bitmap, toView, 1, 9);
        } catch (RuntimeException | LinkageError error) {
            Log.w(ProbeActivity.TAG, "Strokes use gray display", error);
        }
        return null;
    }
    /**
     * Whether a stroke shows through the fast update while the pen is down: Cut Stamp's lasso, and on the bench others. A live stroke changes
     * the pixels under its nib again and again, and the fast update leaves them part-driven (a big Slow wash stayed
     * ghosted in rings, 2026-10-16). A quick stroke's preview writes each pixel once, yet its run of fast updates leaves
     * pixels part-driven too, and where the final keeps what the preview showed (a flat opaque paint: 99% of a Gouache
     * stroke) the driver passes them by at lift: the owner saw quick strokes ghosted until a full refresh. Ending the fast
     * session so the driver drives them again ({@link DirectEink#endFast}) works, but the panel runs it as two updates
     * one after the other and the stroke blinks after each lift; the gray update under the pen costs its preview about
     * 0.12 s. Engine spec, "Lift after the fast update".
     */
    static boolean wants(DrawingStroke stroke) {
        if (stroke == null || stroke instanceof EraseBothStroke) return false;
        // Cut Stamp's lasso: marching ants in ink over the page's dots, black and white only, gone at the lift.
        if (stroke instanceof MirrorShow.Place && ((MirrorShow.Place) stroke).lasso) return true;
        return "all".equals(bench) || "quick".equals(bench) && stroke instanceof QuickStroke
                || "screen".equals(bench) && stroke instanceof LiftingStroke && ((LiftingStroke) stroke).screened();
    }
}
