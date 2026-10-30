package io.github.mpdairy.monopaint;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.LinearLayout;
import android.widget.PopupWindow;

/**
 * Import Image's placing (the owner, 2026-10-22; docs/archive/superpowers/specs/2026-10-22-import-design.md): the picture shows
 * over the page, fitted inside it and centred ({@link PictureImport.Frame}), with a square handle at its lower right, and
 * a bar along the page's foot with <b>Cancel</b> and <b>Place</b>. Until one is tapped every touch on the page is the
 * picture's, the pen's or a finger's alike: a drag moves it, a drag from the handle resizes it, two fingers pinch it, its
 * shape always kept. Place lays it ({@link PictureImport#lay}, the caller's), Cancel leaves the page as it was; leaving
 * the page or the screen cancels it too. Nothing is in the page until Place.
 *
 * <p>E-ink, as a magnet moves ({@link WordsMode}): while a finger or the pen moves it only its outline and handle follow,
 * drawn over the page's raster ({@link RasterOverlay}) through the panel's fast black-and-white update where it has one;
 * at lift the picture shows in its new place, in the panel's dots and darkening what it lies over, as {@link
 * PictureImport#lay} will lay it, and the area it crossed gets one clean refresh.
 */
final class PicturePlacer {
    /** What Place does with the picture where it lies. */
    interface Place { void at(Bitmap picture, PictureImport.Frame frame); }

    private final PaintActivity app;
    private final WordsMode.Host host;
    private final RasterOverlay overlay = new RasterOverlay();
    private final Paint ink = new Paint(), paper = new Paint();
    /** The picture, decoded no larger than it needs for the page; null while nothing is being placed. */
    private Bitmap picture;
    private PictureImport.Frame frame;
    /** The page it is being placed on: another page shown cancels it. */
    private ToneDocument page;
    private Place place;
    private PopupWindow bar;
    /** What a touch does: nothing yet, moves it, resizes it from the handle, pinches it; or waits for every pointer to lift. */
    private enum Gesture { NONE, MOVE, CORNER, PINCH, LIFTING }
    private Gesture gesture = Gesture.NONE;
    private int first = -1, second = -1;
    /** The frame as the gesture found it, and where its pointers landed (page px): the first's, the handle's offset, the pinch's middle and spread. */
    private PictureImport.Frame start;
    private float downX, downY, midX, midY, spread;
    /** Where the outline has been this gesture, page px: what the lift refreshes. */
    private final Rect travelled = new Rect();
    private boolean framePosted;
    private long lastFrame;
    private final Runnable frameStep = this::frame;
    private Canvas canvas;
    private Bitmap canvasOf;
    /** Rows a band of the picture's preview, so it never needs a whole page of pixels at once. */
    private static final int BAND = 128;

    PicturePlacer(PaintActivity app, WordsMode.Host host) {
        this.app = app; this.host = host;
        ink.setColor(Theme.INK); paper.setColor(Theme.PAPER);
    }

    /** Whether a picture is being placed. */
    boolean active() { return picture != null; }
    /** Whether a pointer is moving it. */
    boolean dragging() { return gesture != Gesture.NONE && gesture != Gesture.LIFTING; }

    /** Starts placing {@code picture} on the page shown, fitted inside it and centred; Place runs {@code place}. */
    void begin(Bitmap picture, Place place) {
        cancel();
        page = host.page();
        if (page == null || host.raster() == null) { picture.recycle(); return; }
        this.picture = picture; this.place = place;
        frame = PictureImport.Frame.fitted(picture.getWidth(), picture.getHeight(), shown(), page.width, page.height);
        showBar();
        drawPicture(); host.present();
        host.refresh("picture shown", rect(frame.drawn()));
    }
    /** What the canvas shows of the page, page px {left, top, right, bottom}: its corners' page points. */
    private int[] shown() {
        float[] xs = new float[4], ys = new float[4];
        int w = app.pad.getWidth(), h = app.pad.getHeight();
        for (int k = 0; k < 4; k++) { float[] p = host.toPage(k % 2 == 0 ? 0 : w, k < 2 ? 0 : h); xs[k] = p[0]; ys[k] = p[1]; }
        float l = Math.min(Math.min(xs[0], xs[1]), Math.min(xs[2], xs[3])), r = Math.max(Math.max(xs[0], xs[1]), Math.max(xs[2], xs[3]));
        float t = Math.min(Math.min(ys[0], ys[1]), Math.min(ys[2], ys[3])), b = Math.max(Math.max(ys[0], ys[1]), Math.max(ys[2], ys[3]));
        return new int[]{Math.round(l), Math.round(t), Math.round(r), Math.round(b)};
    }
    /** Cancel, or the page or screen left: the page as it was, nothing laid. */
    void cancel() { end(false); }

    private void end(boolean placed) {
        if (picture == null) return;
        app.pad.removeCallbacks(frameStep); framePosted = false;
        Rect area = overlay.isEmpty() ? null : rect(frame.drawn());
        overlayOff();
        host.fastPreview(false);
        gesture = Gesture.NONE; first = second = -1;
        Bitmap laid = picture; PictureImport.Frame at = frame; Place then = place;
        picture = null; place = null; page = null;
        PopupWindow shown = bar; bar = null;
        if (shown != null) shown.dismiss();
        if (placed) then.at(laid, at);
        laid.recycle();
        host.present();
        if (area != null && !placed) host.refresh("picture cancelled", area);
    }

    // The bar

    private void showBar() {
        RectF pad = Popups.bounds(app.root, app.pad);
        int height = app.dp(64), gap = app.dp(WordBinLayout.BUTTON_GAP);
        LinearLayout row = new LinearLayout(app);
        row.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        row.setPadding(gap, 0, gap, 0);
        row.setBackground(Ui.outline(Theme.PAPER, app.dp(Theme.BASE), Theme.INK, 0));
        LabButton cancel = new LabButton(app, 0, PictureImport.CANCEL, false), placed = new LabButton(app, 0, PictureImport.PLACE, true);
        cancel.setOnClickListener(v -> cancel());
        placed.setOnClickListener(v -> end(true));
        for (LabButton b : new LabButton[]{cancel, placed}) {
            LinearLayout.LayoutParams at = new LinearLayout.LayoutParams(-2, app.dp(44));
            at.leftMargin = gap;
            row.addView(b, at);
        }
        RectF bounds = new RectF(pad.left, pad.bottom - height, pad.right, pad.bottom);
        bar = Popups.create(row, app.appTurn(), false, Theme.PAPER);
        PopupWindow shown = bar;
        RectF panel = new RectF(bounds); PanelCoordinates.fromView(app.root).mapRect(panel);
        bar.setOnDismissListener(() -> {
            host.cover(null);
            app.pad.uncovered("picture", panel);
            // Dismissed by Android (the screen left): the picture goes too.
            if (bar == shown) cancel();
        });
        host.cover(panel);
        Popups.show(bar, app.root, bounds);
    }

    // The canvas's hooks

    /** Takes what it drew off the raster before the page under it is composed again. */
    void overlayOff() { if (!overlay.isEmpty()) overlay.restore(host.raster(), host::show); }
    /** And puts it back after. */
    void overlayOn() { if (picture != null && overlay.isEmpty()) { if (dragging()) drawOutline(); else drawPicture(); } }
    /** The whole raster was composed again: on another page the picture is cancelled, else it shows again. */
    void drawAll() {
        overlay.forget();
        if (picture == null) return;
        if (host.page() != page) { cancel(); return; }
        if (dragging()) drawOutline(); else drawPicture();
    }

    // Touches

    /** A touch on the page; true while a picture is being placed (every touch is its). */
    boolean touch(MotionEvent event) {
        if (picture == null) return false;
        if (host.page() != page) { cancel(); return false; }
        int action = event.getActionMasked(), index = event.getActionIndex(), id = event.getPointerId(index);
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                first = id; second = -1;
                float[] p = host.toPage(event.getX(index), event.getY(index));
                start = frame; downX = p[0]; downY = p[1];
                gesture = frame.onHandle(p[0], p[1]) ? Gesture.CORNER : Gesture.MOVE;
                if (gesture == Gesture.CORNER) { downX -= frame.x + frame.w; downY -= frame.y + frame.h; }
                travelled.set(rect(frame.drawn()));
                overlayOff(); host.fastPreview(true); drawOutline(); host.present();
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                if (gesture != Gesture.MOVE && gesture != Gesture.CORNER || second >= 0) return true;
                second = id; start = frame; gesture = Gesture.PINCH;
                float[] a = pointer(event, first), b = pointer(event, second);
                if (a == null || b == null) { gesture = Gesture.LIFTING; return true; }
                midX = (a[0] + b[0]) / 2; midY = (a[1] + b[1]) / 2; spread = Math.max(1, (float) Math.hypot(a[0] - b[0], a[1] - b[1]));
                return true;
            case MotionEvent.ACTION_MOVE:
                moved(event);
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                // One finger of a pinch lifts: it is done, the other waits to lift.
                if (gesture == Gesture.PINCH && (id == first || id == second)) { moved(event); lift(); gesture = Gesture.LIFTING; }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragging()) { moved(event); lift(); }
                gesture = Gesture.NONE; first = second = -1;
                return true;
            default:
                return true;
        }
    }
    /** Pointer {@code id}'s page point, or null when it isn't in {@code event}. */
    private float[] pointer(MotionEvent event, int id) {
        int i = event.findPointerIndex(id);
        return i < 0 ? null : host.toPage(event.getX(i), event.getY(i));
    }
    /** The pointers moved: the frame follows, its outline a frame at most every {@link WordsMode#FRAME_MS}. */
    private void moved(MotionEvent event) {
        PictureImport.Frame next = frame;
        float[] a = pointer(event, first);
        if (gesture == Gesture.MOVE && a != null) next = start.moved(a[0] - downX, a[1] - downY);
        else if (gesture == Gesture.CORNER && a != null) next = start.cornerTo(a[0] + downX, a[1] + downY);
        else if (gesture == Gesture.PINCH) {
            float[] b = pointer(event, second);
            if (a == null || b == null) return;
            float mx = (a[0] + b[0]) / 2, my = (a[1] + b[1]) / 2, d = (float) Math.hypot(a[0] - b[0], a[1] - b[1]);
            next = start.scaled(d / spread, midX, midY, mx - midX, my - midY);
        }
        if (next == frame) return;
        frame = next;
        if (!framePosted) {
            framePosted = true;
            app.pad.postDelayed(frameStep, Math.max(0, WordsMode.FRAME_MS - (SystemClock.uptimeMillis() - lastFrame)));
        }
    }
    /** A move's frame: the outline taken off where it was and drawn where it is. */
    private void frame() {
        framePosted = false; lastFrame = SystemClock.uptimeMillis();
        if (!dragging()) return;
        overlayOff(); drawOutline(); host.present();
    }
    /** The pointers lift: the picture shows where it now lies, and the area the outline crossed gets one clean refresh. */
    private void lift() {
        app.pad.removeCallbacks(frameStep); framePosted = false;
        overlayOff();
        host.fastPreview(false);
        travelled.union(rect(frame.drawn()));
        drawPicture(); host.present();
        host.refresh("picture moved", new Rect(travelled));
    }

    // Drawing over the raster

    /** Its outline and handle where it is now, over the raster, what they cover saved, and queued. */
    private void drawOutline() {
        Bitmap raster = host.raster();
        if (raster == null) return;
        int[] r = frame.rect(), h = handle();
        int t = MagnetDrag.OUTLINE;
        for (int[] band : new int[][]{{r[0] - t, r[1] - t, r[2] + t, r[1]}, {r[0] - t, r[3], r[2] + t, r[3] + t}, {r[0] - t, r[1], r[0], r[3]}, {r[2], r[1], r[2] + t, r[3]}, h})
            overlay.add(rect(band), raster);
        if (overlay.isEmpty()) return;
        overlay.save(raster);
        Canvas c = canvas(raster);
        // As a magnet's: ink outside, a line of paper inside it, so it shows on paper and on ink alike.
        ring(c, r[0] - t, r[1] - t, r[2] + t, r[3] + t, t - 1, ink); ring(c, r[0] - 1, r[1] - 1, r[2] + 1, r[3] + 1, 1, paper);
        drawHandle(c, h);
        overlay.queue(host::show);
        travelled.union(r[0] - t, r[1] - t, r[2] + t, r[3] + t);
    }
    /**
     * The picture where it is now, over the raster: each pixel darkened to it as {@link PictureImport#lay} lays it (in the
     * panel's dots, or in greys on a zoomed raster), a thin outline round it and its handle; what they cover saved, and queued.
     */
    private void drawPicture() {
        Bitmap raster = host.raster();
        if (raster == null || picture == null) return;
        overlay.add(rect(frame.drawn()), raster);
        if (overlay.isEmpty()) return;
        overlay.save(raster);
        Rect shown = rect(frame.rect());
        if (shown.intersect(0, 0, raster.getWidth(), raster.getHeight())) {
            boolean dots = host.dots();
            int w = shown.width();
            int[] under = new int[w * BAND], greys = new int[w * BAND];
            for (int top = shown.top; top < shown.bottom; top += BAND) {
                int rows = Math.min(BAND, shown.bottom - top);
                Rect band = new Rect(shown.left, top, shown.right, top + rows);
                region(picture, frame, band, greys);
                PictureImport.greys(greys);
                raster.getPixels(under, 0, w, band.left, band.top, w, rows);
                for (int y = 0; y < rows; y++) for (int x = 0; x < w; x++) {
                    int i = y * w + x, g = greys[i];
                    if (g < 0) continue;
                    int over = dots ? DotPattern.pixel(g, band.left + x, band.top + y) : 0xff000000 | g << 16 | g << 8 | g;
                    under[i] = darker(under[i], over);
                }
                raster.setPixels(under, 0, w, band.left, band.top, w, rows);
            }
        }
        Canvas c = canvas(raster);
        int[] r = frame.rect();
        ring(c, r[0] - 1, r[1] - 1, r[2] + 1, r[3] + 1, 1, ink);
        drawHandle(c, handle());
        overlay.queue(host::show);
    }
    /** The handle's square, centred on the frame's lower right. */
    private int[] handle() {
        int[] r = frame.rect();
        int half = Math.round(PictureImport.Frame.HANDLE / 2);
        return new int[]{r[2] - half, r[3] - half, r[2] + half, r[3] + half};
    }
    /** A square of ink with a paper edge, so it shows on paper and on ink alike. */
    private void drawHandle(Canvas c, int[] h) {
        c.drawRect(h[0], h[1], h[2], h[3], paper);
        c.drawRect(h[0] + 3, h[1] + 3, h[2] - 3, h[3] - 3, ink);
    }
    /** A ring {@code width} px wide just inside {@code l, t, r, b}, as four rectangles. */
    private static void ring(Canvas c, int l, int t, int r, int b, int width, Paint paint) {
        c.drawRect(l, t, r, t + width, paint); c.drawRect(l, b - width, r, b, paint);
        c.drawRect(l, t + width, l + width, b - width, paint); c.drawRect(r - width, t + width, r, b - width, paint);
    }
    /** Each channel the darker of {@code a} and {@code b}'s, opaque. */
    private static int darker(int a, int b) {
        return 0xff000000 | Math.min(a & 0xff0000, b & 0xff0000) | Math.min(a & 0xff00, b & 0xff00) | Math.min(a & 0xff, b & 0xff);
    }
    private Canvas canvas(Bitmap raster) {
        if (canvasOf != raster) { canvas = new Canvas(raster); canvasOf = raster; }
        return canvas;
    }
    private static Rect rect(int[] r) { return new Rect(r[0], r[1], r[2], r[3]); }

    /**
     * {@code area} of the page (page px) as {@code picture} covers it lying at {@code frame}: its pixels, ARGB, {@code
     * area}'s width a row, into {@code out}; clear (see-through) where the picture isn't. Also what Place lays.
     */
    /**
     * The picture at {@code uri}, decoded no larger than fits a {@code width × height} page (the most a page shows of it
     * fitted; it is laid from this), upright as its camera took it, in pixels a canvas can read. Off the main thread.
     */
    static Bitmap read(android.content.ContentResolver files, android.net.Uri uri, int width, int height) throws java.io.IOException {
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            return android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(files, uri), (decoder, info, source) -> {
                decoder.setAllocator(android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE);
                int w = info.getSize().getWidth(), h = info.getSize().getHeight();
                int[] fit = PictureImport.fit(w, h, width, height);
                if (fit[2] < w) decoder.setTargetSize(fit[2], fit[3]);
            });
        }
        android.graphics.BitmapFactory.Options size = new android.graphics.BitmapFactory.Options();
        size.inJustDecodeBounds = true;
        try (java.io.InputStream in = files.openInputStream(uri)) { android.graphics.BitmapFactory.decodeStream(in, null, size); }
        if (size.outWidth <= 0 || size.outHeight <= 0) throw new java.io.IOException(PictureImport.UNREADABLE);
        android.graphics.BitmapFactory.Options read = new android.graphics.BitmapFactory.Options();
        // Halved while it still covers its fitted size (Android 8.1, before ImageDecoder: not turned upright).
        int fitted = PictureImport.fit(size.outWidth, size.outHeight, width, height)[2];
        read.inSampleSize = 1;
        while (size.outWidth / (read.inSampleSize * 2) >= fitted) read.inSampleSize *= 2;
        try (java.io.InputStream in = files.openInputStream(uri)) {
            Bitmap picture = android.graphics.BitmapFactory.decodeStream(in, null, read);
            if (picture == null) throw new java.io.IOException(PictureImport.UNREADABLE);
            return picture;
        }
    }
    /** {@link #region} of {@code area}, a band of rows at a time: what Place lays ({@link PictureImport#greys}, then {@link PictureImport#lay}). */
    static int[] pixels(Bitmap picture, PictureImport.Frame frame, Rect area) {
        int w = area.width();
        int[] all = new int[w * area.height()], band = new int[w * BAND];
        for (int top = area.top; top < area.bottom; top += BAND) {
            int rows = Math.min(BAND, area.bottom - top);
            region(picture, frame, new Rect(area.left, top, area.right, top + rows), band);
            System.arraycopy(band, 0, all, (top - area.top) * w, w * rows);
        }
        return all;
    }
    static void region(Bitmap picture, PictureImport.Frame frame, Rect area, int[] out) {
        Bitmap band = Bitmap.createBitmap(area.width(), area.height(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(band);
        c.translate(-area.left, -area.top);
        c.drawBitmap(picture, null, new RectF(frame.x, frame.y, frame.x + frame.w, frame.y + frame.h), new Paint(Paint.FILTER_BITMAP_FLAG));
        band.getPixels(out, 0, area.width(), 0, 0, area.width(), area.height());
        band.recycle();
    }
}
