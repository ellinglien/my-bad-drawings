package io.github.mpdairy.monopaint;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.PopupWindow;
import java.util.Random;

/**
 * Fridge magnets on the drawing screen (the owner's, 2026-10-18; mockup screen 3). The magnets always lie over the
 * drawing ({@link #draw}, after the page's paint and masking fluid), never in it. The pen landing on a magnet picks it
 * up, whether the bin is open or not, and it follows the pen; lifted over the bin or off the page it comes off. Anywhere
 * else the pen draws, under the magnets. The bar's Words button opens the bin along the foot of the page
 * ({@link WordBin}): the pen carries a word out of it onto the page, or a word tapped in the bin goes where the pen
 * next lands; while it is open the pen draws nothing and fingers keep their taps (undo, redo) and draw nothing either.
 * Placing, moving and taking off are each one Undo step ({@link ToneDocument#setMagnets}). Only the pen moves magnets:
 * a finger would fight the finger tool and a resting palm would carry them off.
 *
 * <p>E-ink: while a magnet moves it stays where it was and only its outline follows the pen ({@link MagnetDrag#outline}),
 * an overlay on the page's raster ({@link RasterOverlay}): each move puts back the few pixels the outline left and draws
 * it at its new place, through the panel's fast black-and-white update where it has one ({@link Host#fastPreview}). At
 * lift the magnet is laid, and the area the outline crossed gets one clean refresh. The canvas never presents under the
 * bin ({@link Host#cover}), which Android draws over it.
 *
 * <p>Why moving never worked before (2026-10-19): only the open bin gave the pen to the magnets, so once Done (or a
 * tool) closed it the pen drew under them; and the bin is a window of its own, so a pen landing on a word there sent
 * the whole stroke to it, and it only ever read the lift, as a tap.
 */
final class WordsMode {
    /** What the drawing screen's canvas lends ({@link DrawingPad}), to the magnets and to a picture being placed ({@link PicturePlacer}). */
    interface Host {
        ToneDocument page();
        /** The page's raster, page pixels. */
        Bitmap raster();
        /** View point {@code x, y} on the page. */
        float[] toPage(float x, float y);
        /** Queues page pixels already drawn into the raster for the panel. */
        void show(Rect pageArea);
        /** Presents what is queued. */
        void present();
        /** Starts (true) or ends the panel's fast black-and-white update, where it has one, for the moving outline. */
        void fastPreview(boolean on);
        /** One clean refresh of {@code pageArea} of the page, where nothing covers it. */
        void refresh(String reason, Rect pageArea);
        /** The panel area the canvas must not present over, or null: the bin. */
        void cover(RectF panelArea);
        /** Whether the raster is in the panel's dots (else plain greys, for a zoomed view to scale): what a picture placed is drawn in ({@link PicturePlacer}). */
        boolean dots();
    }
    /** The least time between a move's frames, ms. */
    static final long FRAME_MS = 16;
    /** The pointer of a word carried out of the bin: its events reach the bin, never the canvas. */
    private static final int CARRIED = -2;
    private final PaintActivity app;
    private final Host host;
    private final MagnetTiles tiles = new MagnetTiles();
    private final RasterOverlay overlay = new RasterOverlay();
    private final Paint ink = new Paint(), paper = new Paint();
    private final Random random = new Random();
    private PopupWindow popup;
    private WordBin bin;
    /** The bin, in the root's app coordinates, and on the panel. */
    private final RectF binBounds = new RectF(), binPanel = new RectF();
    private MagnetDrag drag;
    private int pointer = -1;
    /** The pen on the page, page pixels, and on the canvas, view pixels. */
    private float penX, penY, viewX, viewY;
    /** Where the outline has been this drag, page pixels: what the lift refreshes. */
    private final Rect travelled = new Rect();
    private Canvas canvas;
    private Bitmap canvasOf;
    private boolean framePosted;
    private long lastFrame;
    private final Runnable frame = this::frame;
    /** A drag's timing, for the log: frames, their drawing and their present, ns; pixels shown. */
    private int frames;
    private long drawNanos, presentNanos, shownPx;

    WordsMode(PaintActivity app, Host host) {
        this.app = app; this.host = host;
        ink.setColor(Theme.INK); paper.setColor(Theme.PAPER);
    }

    boolean open() { return popup != null; }
    /** Whether the pen is moving a magnet. */
    boolean dragging() { return drag != null; }
    /** The bar's Words button. */
    void toggle() { if (open()) close(); else show(); }

    private void show() {
        if (app.busy() || app.pad == null) return;
        cancel();
        app.pad.finishStroke();
        java.io.File folder = DrawingStorage.sharedAllowed(app) ? DrawingStorage.shared() : null;
        RectF page = Popups.bounds(app.root, app.pad);
        float density = app.getResources().getDisplayMetrics().density;
        WordBinLayout layout = new WordBinLayout(Math.round(page.width()), app.root.getHeight(), density, app.landscape);
        binBounds.set(page.left, page.bottom - layout.height, page.right, page.bottom);
        bin = new WordBin(app, layout, WordPacks.session(folder), tiles, new WordBin.Host() {
            @Override public void picked(String word) { }
            @Override public void done() { close(); }
            @Override public void update(android.view.View view, Rect area, Runnable change) { app.selectionFeedback.update(view, area, change, true); }
            @Override public void carry(String word, int action, float panelX, float panelY) { WordsMode.this.carry(word, action, panelX, panelY); }
        });
        popup = Popups.create(bin, app.appTurn(), false, Theme.PAPER);
        PopupWindow shown = popup;
        binPanel.set(binBounds); PanelCoordinates.fromView(app.root).mapRect(binPanel);
        RectF covered = new RectF(binPanel);
        popup.setOnDismissListener(() -> {
            if (popup == shown) { popup = null; bin = null; }
            host.cover(null);
            app.pad.uncovered("words", covered);
            if (app.inkWashBar != null) app.inkWashBar.modesChanged();
        });
        host.cover(binPanel);
        Popups.show(popup, app.root, binBounds);
        if (app.inkWashBar != null) app.inkWashBar.modesChanged();
    }
    /** Closes the bin (Done, Words again, a menu, leaving the screen), dropping a magnet the pen still holds. */
    void close() {
        cancel();
        if (popup != null) popup.dismiss();
    }

    // The canvas's hooks

    /** Draws the page's magnets into {@code area} of {@code raster}, freshly composed: over the paint. */
    void draw(Bitmap raster, Rect area) {
        Magnets magnets = host.page() == null ? Magnets.NONE : host.page().magnets();
        if (magnets.isEmpty()) return;
        tiles.draw(canvas(raster), area, magnets, -1);
    }
    /** The whole raster was composed again (another page, the viewport): a magnet held is dropped back where it was. */
    void drawAll(Bitmap raster) {
        overlay.forget(); drag = null; pointer = -1;
        draw(raster, new Rect(0, 0, raster.getWidth(), raster.getHeight()));
    }
    /** Takes the outline off the raster before the page under it is composed again. */
    void overlayOff() { if (!overlay.isEmpty()) overlay.restore(host.raster(), host::show); }
    /** And puts it back over it after. */
    void overlayOn() { if (drag != null && overlay.isEmpty()) drawOutline(); }
    /** The canvas is suspended (a panel, a pause): a magnet held stays where it was. */
    void cancel() {
        if (drag == null) return;
        app.pad.removeCallbacks(frame); framePosted = false;
        overlayOff();
        drag = null; pointer = -1;
        host.fastPreview(false);
        host.present();
    }

    /**
     * A touch on the page; true: it was the magnets'. With the bin open every touch is; with it closed only the pen
     * landing on a magnet, when {@code mayPick} (the canvas isn't busy with another gesture), and what follows it.
     */
    boolean touch(MotionEvent event, boolean mayPick) {
        int action = event.getActionMasked(), index = event.getActionIndex();
        ToneDocument page = host.page();
        if (page == null) return open();
        if (drag == null) {
            if (action != MotionEvent.ACTION_DOWN && action != MotionEvent.ACTION_POINTER_DOWN) return open();
            float x = event.getX(index), y = event.getY(index);
            float[] p = host.toPage(x, y);
            boolean onPage = p[0] >= 0 && p[1] >= 0 && p[0] < page.width && p[1] < page.height && !overBin(x, y);
            Magnets magnets = page.magnets();
            int hit = onPage ? magnets.hit(p[0], p[1]) : -1;
            String word = bin == null || !onPage ? null : bin.picked();
            switch (MagnetDrag.landing(open(), DrawingPad.isPen(event, index), mayPick, hit >= 0, word != null)) {
                case DRAWING: return false;
                case NEW: begin(fresh(magnets, word, p), event.getPointerId(index), x, y, p); bin.unpick(); return true;
                case MAGNET:
                    // A stroke still working after its lift finishes first.
                    if (!open()) app.pad.finishStroke();
                    begin(new MagnetDrag(magnets, hit, magnets.get(hit), p[0], p[1]), event.getPointerId(index), x, y, p);
                    return true;
                default: return true;
            }
        }
        if (pointer == CARRIED) return open();
        int p = event.findPointerIndex(pointer);
        if (p < 0) return true;
        if (action == MotionEvent.ACTION_MOVE) { move(event.getX(p), event.getY(p)); return true; }
        if (action == MotionEvent.ACTION_CANCEL) { cancel(); return true; }
        if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) && event.getPointerId(index) == pointer) {
            move(event.getX(p), event.getY(p));
            lift();
        }
        return true;
    }
    /**
     * The pen carrying {@code word} out of the bin ({@link WordBin}), at panel point {@code panelX, panelY}: it starts
     * ({@link MotionEvent#ACTION_DOWN}), moves, lifts or is cancelled. Its events reach the bin's window, never the canvas.
     */
    void carry(String word, int action, float panelX, float panelY) {
        ToneDocument page = host.page();
        if (page == null || app.pad == null) return;
        Matrix toCanvas = new Matrix();
        if (!PanelCoordinates.fromView(app.pad).invert(toCanvas)) return;
        float[] v = {panelX, panelY};
        toCanvas.mapPoints(v);
        if (action == MotionEvent.ACTION_DOWN) {
            if (drag != null) return;
            float[] p = host.toPage(v[0], v[1]);
            begin(fresh(page.magnets(), word, p), CARRIED, v[0], v[1], p);
            return;
        }
        if (drag == null || pointer != CARRIED) return;
        if (action == MotionEvent.ACTION_CANCEL) { cancel(); return; }
        move(v[0], v[1]);
        if (action == MotionEvent.ACTION_UP) lift();
    }
    /** A new magnet of {@code word} centred at page point {@code p}, tilted a little at random, upright: the page turns with the app. */
    private MagnetDrag fresh(Magnets magnets, String word, float[] p) {
        int tilt = random.nextInt(2 * Magnets.MOST_TILT + 1) - Magnets.MOST_TILT;
        return new MagnetDrag(magnets, -1, new Magnets.Magnet(word, Math.round(p[0]), Math.round(p[1]), tilt, 0), p[0], p[1]);
    }
    /** The pen picks up {@code d} as pointer {@code id} at view point {@code x, y}, page point {@code p}: its outline shows at once. */
    private void begin(MagnetDrag d, int id, float x, float y, float[] p) {
        drag = d; pointer = id; viewX = x; viewY = y; penX = p[0]; penY = p[1];
        frames = 0; drawNanos = presentNanos = shownPx = 0;
        travelled.setEmpty();
        host.fastPreview(true);
        drawOutline(); host.present();
    }
    /** The pen at view point {@code x, y}: the outline follows it, a frame at most every {@link #FRAME_MS}. */
    private void move(float x, float y) {
        float[] at = host.toPage(x, y);
        viewX = x; viewY = y; penX = at[0]; penY = at[1];
        if (drag.moveTo(penX, penY) && !framePosted) {
            framePosted = true;
            app.pad.postDelayed(frame, Math.max(0, FRAME_MS - (SystemClock.uptimeMillis() - lastFrame)));
        }
    }
    /** Whether view point {@code x, y} (the canvas's) is over the bin. */
    private boolean overBin(float x, float y) {
        if (popup == null) return false;
        RectF pad = Popups.bounds(app.root, app.pad);
        return binBounds.contains(pad.left + x, pad.top + y);
    }
    /** A move's frame: the pixels the outline left put back, the outline drawn at its new place, both to the panel. */
    private void frame() {
        framePosted = false; lastFrame = SystemClock.uptimeMillis();
        if (drag == null) return;
        long start = System.nanoTime();
        overlayOff(); drawOutline();
        long drawn = System.nanoTime();
        host.present();
        frames++; drawNanos += drawn - start; presentNanos += System.nanoTime() - drawn;
    }
    /** The pen lifts where it last was: the magnet laid there (or taken off, or put back), and the area the outline crossed refreshed. */
    private void lift() {
        app.pad.removeCallbacks(frame); framePosted = false;
        MagnetDrag done = drag;
        boolean over = overBin(viewX, viewY);
        long start = System.nanoTime();
        overlayOff();
        drag = null; pointer = -1;
        host.fastPreview(false);
        ToneDocument page = host.page();
        Magnets next = done.lift(penX, penY, page.width, page.height, over);
        Rect area = new Rect(travelled);
        for (int[] changed : Magnets.changed(done.base, next)) area.union(changed[0], changed[1], changed[2], changed[3]);
        if (next != done.base) app.history(() -> page.setMagnets(next));
        host.present();
        long laid = System.nanoTime();
        host.refresh("magnet " + (next == done.base ? "put back" : next.size() < done.base.size() ? "taken off" : next.size() > done.base.size() ? "placed" : "moved"), area);
        Log.i(ProbeActivity.TAG, String.format(java.util.Locale.ROOT,
                "Magnet drag: %d frames, drawn in %.2f ms and presented in %.1f ms a frame, %d px a frame; lift %.1f ms, refresh %.1f ms",
                frames, frames == 0 ? 0 : drawNanos / 1e6 / frames, frames == 0 ? 0 : presentNanos / 1e6 / frames, frames == 0 ? 0 : shownPx / frames,
                (laid - start) / 1e6, (System.nanoTime() - laid) / 1e6));
    }
    /** Draws the held magnet's outline over the raster where it is now, saving what it covers, and queues it. */
    private void drawOutline() {
        Bitmap raster = host.raster();
        if (raster == null) return;
        Magnets.Magnet m = drag.at();
        for (int[] band : MagnetDrag.outline(m)) overlay.add(rect(band), raster);
        if (overlay.isEmpty()) return;
        overlay.save(raster);
        Canvas c = canvas(raster);
        int[] f = Magnets.face(m);
        int line = MagnetDrag.OUTLINE - 1;
        ring(c, f, 0, line, ink); ring(c, f, line, 1, paper);
        overlay.queue(host::show);
        travelled.union(f[0], f[1], f[2], f[3]);
        for (int[] band : MagnetDrag.outline(m)) shownPx += 2 * MagnetDrag.area(band);
    }
    /** A square ring {@code width} px wide, {@code inset} px inside {@code r}, as four rectangles that never overlap. */
    private static void ring(Canvas c, int[] r, int inset, int width, Paint paint) {
        int l = r[0] + inset, t = r[1] + inset, rt = r[2] - inset, b = r[3] - inset;
        c.drawRect(l, t, rt, t + width, paint);
        c.drawRect(l, b - width, rt, b, paint);
        c.drawRect(l, t + width, l + width, b - width, paint);
        c.drawRect(rt - width, t + width, rt, b - width, paint);
    }
    private Canvas canvas(Bitmap raster) {
        if (canvasOf != raster) { canvas = new Canvas(raster); canvasOf = raster; }
        return canvas;
    }
    private static Rect rect(int[] r) { return new Rect(r[0], r[1], r[2], r[3]); }

    /** The PNG copy's magnets: a renderer of its own, since the copy is drawn on its own thread. */
    static Magnets.Tiles exportTiles() { return new MagnetTiles(); }
}
