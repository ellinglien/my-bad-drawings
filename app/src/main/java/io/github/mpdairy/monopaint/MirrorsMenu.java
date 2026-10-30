package io.github.mpdairy.monopaint;

import android.graphics.drawable.Drawable;
import android.view.View;

/**
 * The Mirrors button's list ({@link DrawLayout#MIRRORS}; in the Fun button's list until the owner, 2026-10-23: "move
 * magnetic poetry and mirrors to the toolbar, removing the confetti"): the mirrors' rows ({@link DrawLayout.MirrorRow})
 * with how many there are under them, the ones on marked by the menu's bar. The pen's next stroke places a mirror
 * ({@link DrawingPad#placing}), and {@link #placed} takes it; Real Mirror's side is Flip Side's, never a stroke's.
 * Magnetic Poetry's button opens or closes the magnets' bin ({@link WordsMode}) itself.
 */
final class MirrorsMenu {
    /** The notices (docs/TEXT-STYLE.md: the result in about three words). */
    static final String TOO_SHORT = "Too short";
    /** How long a notice shows, ms; the pen going down hides it. */
    static final long SAY_MS = 4000;
    private final PaintActivity app;

    MirrorsMenu(PaintActivity app) { this.app = app; }

    /** Whether the page's mirrors are on, or a mirror is being placed: Play's side bar. */
    boolean on() { return app.pad != null && app.pad.document != null && (app.pad.document.mirrors().acts() || app.pad.placing != DrawingPad.Placing.NONE); }
    /** Play's side bar follows. */
    void changed() { if (app.inkWashBar != null) app.inkWashBar.modesChanged(); }
    void say(String text) { if (app.inkWashBar != null) app.inkWashBar.sayMirrors(text, SAY_MS); }

    private int width() { return app.prefs.largeSettingsText() ? 296 : 280; }

    /** The mirrors' list beside {@code anchor}: each row, the ones on marked, how many under them. */
    void mirrors(View anchor) {
        ToneDocument page = app.pad.document;
        Mirrors m = page.mirrors();
        java.util.List<String> names = new java.util.ArrayList<>();
        java.util.List<Runnable> actions = new java.util.ArrayList<>();
        java.util.List<Boolean> on = new java.util.ArrayList<>();
        java.util.List<Drawable> icons = new java.util.ArrayList<>();
        for (DrawLayout.MirrorRow row : DrawLayout.MirrorRow.values()) {
            if ((row == DrawLayout.MirrorRow.FLIP || row == DrawLayout.MirrorRow.KEEP) && !m.reflects()) continue;
            names.add(row.label);
            icons.add(app.getDrawable(icon(row)));
            on.add(row == DrawLayout.MirrorRow.MIRRORS ? m.acts() : row == DrawLayout.MirrorRow.GUIDES ? m.guides : row == DrawLayout.MirrorRow.COPY ? m.style == Mirrors.Style.COPY
                    : row == DrawLayout.MirrorRow.REAL ? m.style == Mirrors.Style.REAL : row == DrawLayout.MirrorRow.PLACE && app.pad.placing == DrawingPad.Placing.LINE);
            actions.add(() -> act(row));
        }
        boolean[] marked = new boolean[on.size()];
        for (int i = 0; i < marked.length; i++) marked[i] = on.get(i);
        app.menu(anchor, "mirrors menu", names.toArray(new String[0]), icons.toArray(new Drawable[0]), actions.toArray(new Runnable[0]), width(), marked, m.caption());
    }
    /**
     * A row's icon (the owner, 2026-10-21; Phosphor Bold): Mirrors (on or off) the Mirrors button's own, so it reads as
     * that switch and not a heading.
     */
    private static int icon(DrawLayout.MirrorRow row) {
        switch (row) {
            case MIRRORS: return R.drawable.ic_ph_flip_horizontal;
            case GUIDES: return R.drawable.ic_ph_eye;
            case COPY: return R.drawable.ic_ph_copy;
            case REAL: return R.drawable.ic_ph_circle_half;
            case PLACE: return R.drawable.ic_ph_line_segment;
            case KALEIDOSCOPE: return R.drawable.ic_ph_snowflake;
            case FLIP: return R.drawable.ic_ph_arrows_left_right;
            case KEEP: return R.drawable.ic_ph_check;
            default: return R.drawable.ic_ph_broom;
        }
    }

    /** A row of the mirrors' list tapped. */
    private void act(DrawLayout.MirrorRow row) {
        ToneDocument page = app.pad.document;
        Mirrors m = page.mirrors();
        switch (row) {
            case MIRRORS:
                app.layerChange(() -> page.showMirrors(!m.on));
                if (!page.mirrors().on) app.pad.placing = DrawingPad.Placing.NONE;
                break;
            case GUIDES: app.layerChange(() -> page.showGuides(!m.guides)); break;
            case COPY: app.layerChange(() -> page.setMirrors(m.styled(Mirrors.Style.COPY))); break;
            case REAL: set(m.styled(Mirrors.Style.REAL)); break;
            case PLACE: app.pad.placing = DrawingPad.Placing.LINE; break;
            case KALEIDOSCOPE: set(m.kaleidoscope(page.width, page.height)); break;
            case FLIP: app.layerChange(() -> page.setMirrors(m.flipped(page.width, page.height))); break;
            case KEEP: app.layerChange(page::keepReflection); break;
            case CLEAR: app.pad.placing = DrawingPad.Placing.NONE; app.layerChange(() -> page.setMirrors(m.cleared())); break;
            default: break;
        }
        changed();
    }
    /**
     * Sets the page's mirrors to {@code next} (one Undo step). Real Mirror keeps, at first, the side with the most marks;
     * Flip Side keeps another.
     */
    private void set(Mirrors next) {
        ToneDocument page = app.pad.document;
        app.pad.placing = DrawingPad.Placing.NONE;
        if (next.style == Mirrors.Style.REAL && !next.isEmpty()) {
            double[] side = Mirrors.mostInk(next, page::compositeTone, page.width, page.height);
            next = next.keeping(side[0], side[1]);
        }
        Mirrors mirrors = next;
        app.layerChange(() -> page.setMirrors(mirrors));
        changed();
    }

    /** The pen's placing stroke ended: a mirror placed from it ({@link Mirrors#fit}), or "Too short". */
    void placed(MirrorShow.Place place) {
        ToneDocument page = app.pad.document;
        Mirrors m = page.mirrors();
        Mirrors.Line line = m.fit(place.xs(), place.ys(), place.count(), page.width, page.height);
        if (line == null) { say(TOO_SHORT); return; }
        set(m.add(line));
    }
}
