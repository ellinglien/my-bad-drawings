package io.github.mpdairy.monopaint;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Synchronous control-only presentation, with Android redraw only as a fallback. */
final class SelectionFeedback {
    boolean enabled = true;
    int submitted;
    int lastDisplayMode;
    /** The last submitted patch, in driver buffer coordinates ({@link DirectEink#bufferRegion}). */
    Rect lastBufferRegion;
    private boolean loggedFailure;
    /** Panel area this session drew through the Nomad's fast pen plane (mode 9). */
    private final android.graphics.RectF fastInk = new android.graphics.RectF();
    private final Map<View, Rect> pending = new WeakHashMap<>();
    /**
     * The last control patch's session, kept for its next update: the droplet ripples and the
     * color marker drags present the same patch many times a second. Android redraws the
     * patch in between, so a reused session always refreshes rather than skipping pixels it
     * believes unchanged.
     */
    private DirectEink session;
    private View sessionOwner;
    private final Rect sessionArea = new Rect();
    private final android.graphics.Matrix sessionPanel = new android.graphics.Matrix();
    private int sessionMode;
    private ViewTreeObserver observer;
    // Refresh cached Android drawing commands when another UI action needs a frame.
    // Registering this listener does not request a frame or delay a display write.
    private final ViewTreeObserver.OnPreDrawListener syncBeforeDraw = () -> {
        for (Map.Entry<View, Rect> entry : pending.entrySet())
            invalidateWithin(entry.getKey(), entry.getValue());
        pending.clear();
        return true;
    };

    void retainForNextDraw(View owner, Rect area) {
        ViewTreeObserver next = owner.getRootView().getViewTreeObserver();
        if (observer != next) {
            if (observer != null && observer.isAlive()) observer.removeOnPreDrawListener(syncBeforeDraw);
            observer = next; observer.addOnPreDrawListener(syncBeforeDraw);
        }
        Rect old = pending.get(owner);
        if (old == null) pending.put(owner, new Rect(area)); else old.union(area);
    }
    /** Commit a finished interaction to Android's surface as well as the panel. */
    void finishUpdate(View owner) {
        Rect area = pending.remove(owner);
        if (area != null) invalidateWithin(owner, area);
    }
    /**
     * Inkwash: {@code owner} and every view inside it over {@code area} (the owner's pixels) drawn again at Android's next
     * frame. A change shown through this class asks the views it changed for no frame (a slot's mark, the slider's tab,
     * the dot and the brush line's name, gathered into one present of the column), so invalidating the owner alone left
     * Android's recorded drawing of each of them as it was before: the next frame of the column, the panel driving what it
     * changed, put the old marks back (the owner, 2026-10-24: "won't switch to another brush, kept jumping to liftith with
     * wrong size as well": the slot and the size last drawn through Android, at launch).
     */
    static void invalidateWithin(View owner, Rect area) {
        owner.invalidate(area);
        if (!(owner instanceof android.view.ViewGroup)) return;
        android.view.ViewGroup group = (android.view.ViewGroup) owner;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            // A turned child (a popup's QuarterTurnLayout): all of it, rather than map the patch.
            if (!child.getMatrix().isIdentity()) { Ui.invalidateTree(child); continue; }
            Rect inside = new Rect(area);
            inside.offset(group.getScrollX() - child.getLeft(), group.getScrollY() - child.getTop());
            if (inside.intersect(0, 0, child.getWidth(), child.getHeight())) invalidateWithin(child, inside);
        }
    }
    /** One settled gray-mode pass clears fast-ink residue without slowing live movement. */
    void clean(View owner, Rect area) {
        if (!enabled || area.isEmpty() || !owner.hasWindowFocus() || !owner.isShown()
                || owner.getDisplay() == null || !PanelCoordinates.fullyVisible(owner, area)) return;
        Bitmap pixels = capture(owner, area);
        DirectEink display = null;
        try {
            android.graphics.Matrix bitmapToView = new android.graphics.Matrix();
            bitmapToView.setTranslate(area.left, area.top);
            display = DirectEink.forView(owner, pixels, bitmapToView, 0, 7);
            // Android's unchanged white pixels cannot describe residual physical ink.
            if (display.refresh(pixels, new Rect(0, 0, pixels.getWidth(), pixels.getHeight())) < 0)
                owner.invalidate(area);
        } catch (RuntimeException error) {
            owner.invalidate(area);
        } finally {
            if (display != null) display.close();
            pixels.recycle();
        }
    }
    /**
     * Inkwash: {@code area} of {@code owner} driven again where it is paper, gently: two grey updates back to back, its
     * paper first as the level next to white, then white, so the driver drives those pixels (it passes by any pixel asked
     * for the value it holds, forced or not, so {@link #clean} of unchanged pixels drives nothing on the Manta: {@code
     * panel_end.h}'s "near"). Clears the faint trail the binary stroke mode (mode 5) leaves where an ink mark moved off
     * paper: the size slider's tab (the owner, 2026-10-24: "artifacting of side size indicator"). Ink stays as it is.
     */
    void redrive(View owner, Rect area) {
        if (!enabled || area.isEmpty() || !owner.hasWindowFocus() || !owner.isShown()
                || owner.getDisplay() == null || !PanelCoordinates.fullyVisible(owner, area)) return;
        Bitmap pixels = capture(owner, area), near = pixels.copy(Bitmap.Config.ARGB_8888, true);
        int[] row = new int[near.getWidth()];
        for (int y = 0; y < near.getHeight(); y++) {
            near.getPixels(row, 0, row.length, 0, y, row.length, 1);
            for (int x = 0; x < row.length; x++) if (row[x] == android.graphics.Color.WHITE) row[x] = NEAR_WHITE;
            near.setPixels(row, 0, row.length, 0, y, row.length, 1);
        }
        DirectEink display = null;
        try {
            android.graphics.Matrix bitmapToView = new android.graphics.Matrix();
            bitmapToView.setTranslate(area.left, area.top);
            display = DirectEink.forView(owner, pixels, bitmapToView, 0, 7);
            Rect all = new Rect(0, 0, pixels.getWidth(), pixels.getHeight());
            // The second must follow the first, however busy the display (as panel_end's): the paper is near white until it goes.
            if (display.present(near, all) > 0)
                for (int tries = 0; tries < 200 && display.present(pixels, all) < 0; tries++) android.os.SystemClock.sleep(1);
        } catch (RuntimeException ignored) {
            // No fast display: nothing to clean.
        } finally {
            if (display != null) display.close();
            pixels.recycle(); near.recycle();
        }
    }
    /**
     * Inkwash: {@code pixels}, a popup's drawn before its window is on screen, put on the panel over {@code panel} at once
     * through a grey update. The firmware shows a new window about 350 ms after Android first draws it (the menu's first
     * frame 500 to 590 ms after the tap, its first draw 150 to 170 ms, 2026-10-25).
     */
    void presentAhead(Bitmap pixels, android.graphics.RectF panel) {
        Rect at = new Rect(); panel.round(at);
        if (!enabled || at.width() != pixels.getWidth() || at.height() != pixels.getHeight() || DirectEink.layout()[0] == 0) return;
        Bitmap under = Bitmap.createBitmap(at.width(), at.height(), Bitmap.Config.ARGB_8888);
        under.eraseColor(android.graphics.Color.WHITE);
        DirectEink display = null;
        try {
            android.graphics.Matrix toPanel = new android.graphics.Matrix();
            toPanel.setTranslate(at.left, at.top);
            display = DirectEink.forPanel(under, toPanel, 0, 7);
            if (display.refresh(pixels, new Rect(0, 0, at.width(), at.height())) > 0) { submitted++; lastDisplayMode = 7; lastBufferRegion = display.bufferRegion(new Rect(0, 0, at.width(), at.height())); }
        } catch (RuntimeException ignored) {
            // No fast display: the firmware shows it as before.
        } finally {
            if (display != null) display.close();
            under.recycle();
        }
    }
    /** Paper one level darker on the panel's sixteen (level 14 of 15). */
    private static final int NEAR_WHITE = 0xffeeeeee;
    /**
     * Inkwash: a menu or popup over {@code owner} closed over {@code area} (the owner's pixels): once Android has drawn
     * what it covered, that patch alone gets the clean pass ({@link #clean}), so the popup leaves no ghost (the owner,
     * 2026-10-23: "i am worried they will leave e-ink ghosts when they open and close").
     */
    void cleanAfterClose(View owner, android.graphics.RectF area) {
        Rect patch = new Rect(); area.roundOut(patch);
        if (!patch.intersect(0, 0, owner.getWidth(), owner.getHeight())) return;
        owner.postDelayed(() -> clean(owner, patch), DrawingPad.CLOSED_MS);
    }
    /**
     * Returns and forgets the fast ink drawn so far. Android redraws and panel refreshes
     * do not remove pen-plane pixels, so a closing popup must erase this area itself.
     */
    android.graphics.RectF takeFastInk() {
        android.graphics.RectF area = new android.graphics.RectF(fastInk); fastInk.setEmpty(); return area;
    }
    void close() {
        if (observer != null && observer.isAlive()) observer.removeOnPreDrawListener(syncBeforeDraw);
        observer = null; pending.clear();
        closeSession();
    }
    /** Releases the kept control session, e.g. while another window covers the controls. */
    void closeSession() {
        if (session != null) session.close();
        session = null; sessionOwner = null;
    }

    /** Updates the whole control. */
    void update(View owner, Runnable change) {
        update(owner,new Rect(0,0,owner.getWidth(),owner.getHeight()),change);
    }
    void update(View owner, Rect area, Runnable change) {
        update(owner,area,change,owner.hasWindowFocus());
    }
    // Non-focusable palettes share the activity's active window while keeping
    // their own feedback session and redraw observer for their popup surface.
    void update(View owner, Rect area, Runnable change, boolean windowFocused) {
        // Inkwash: inside an update of a view holding this one, the outer update's one present shows this change too.
        if (holds(gathering, owner)) { if (parts != null) part(owner, area); change.run(); return; }
        Bitmap before = direct(owner, area, windowFocused) ? capture(owner, area) : null;
        View outer = gathering;
        if (before != null) gathering = owner;
        try { change.run(); } finally { gathering = outer; }
        if (before == null) { invalidateWithin(owner, area); return; }
        present(owner, area, before, true);
    }
    /**
     * Inkwash: as {@link #update(View, Rect, Runnable)}, a patch all ink and paper put on the panel through its binary
     * update where it has one: MonoPaint's fast plane (mode 9), else the Manta's stroke mode ({@link
     * Device#fastStrokeMode}, mode 5, 8 frames and no grey flash where mode 7 runs 18-21); any grey in the patch, mode 7
     * as before. The size slider's tab and the size and grey dot (the owner, 2026-10-24: "size picker is still blinking").
     */
    void updateBinary(View owner, Rect area, Runnable change) {
        // Restored after, not cleared: one gathered inside a binary update of the view holding it (a tool tap's column) must leave that one binary.
        boolean was = binary;
        binary = true;
        try { update(owner, area, change); } finally { binary = was; }
    }
    private boolean binary;
    /**
     * Inkwash: as {@link #updateBinary} over all of {@code owner} (a tool pick's column), but only the patches of the
     * updates that run inside {@code change} are captured and compared, each before its change and after them all (a
     * slot's mark, the old slot's, the swatch bar, the Dropper), not the whole 135 × 2401 column twice. Each patch's
     * changed pixels are split into the boxes they fall in ({@link #differences}), and every box all ink and paper goes
     * out in the panel's binary update (mode 5 on this Manta firmware), the rest in one grey update over them all. A slot's
     * mark is ink on the paper of the bar's room, so a slot pick no longer goes grey for the brush strokes of the rows
     * between (the whole patch was checked until 2026-10-25).
     */
    void updateParts(View owner, Runnable change) {
        Rect whole = new Rect(0, 0, owner.getWidth(), owner.getHeight());
        if (gathering != null || !(owner instanceof android.view.ViewGroup) || !direct(owner, whole, owner.hasWindowFocus())) { updateBinary(owner, whole, change); return; }
        List<Part> mine = new ArrayList<>();
        parts = mine; gathering = owner;
        try { change.run(); } finally { gathering = null; parts = null; }
        if (mine.isEmpty()) return;
        Rect union = new Rect();
        for (Part part : mine) union.union(part.area);
        // The patches drawn into one bitmap over their union, so one session per mode takes them all; between them it is
        // white and never submitted.
        Bitmap before = Bitmap.createBitmap(union.width(), union.height(), Bitmap.Config.ARGB_8888), after = before.copy(Bitmap.Config.ARGB_8888, true);
        before.eraseColor(android.graphics.Color.WHITE); after.eraseColor(android.graphics.Color.WHITE);
        Canvas was = new Canvas(before), now = new Canvas(after);
        boolean fastPlane = DirectEink.fastBinaryControls();
        int stroke = fastPlane ? 9 : Device.current(owner.getContext()).fastStrokeMode();
        List<Rect> fast = new ArrayList<>();
        Rect grey = new Rect();
        // Back to front, so where two patches overlap the first one's before is the one kept.
        for (int i = mine.size() - 1; i >= 0; i--) was.drawBitmap(mine.get(i).before, mine.get(i).area.left - union.left, mine.get(i).area.top - union.top, null);
        for (Part part : mine) {
            Bitmap shown = capture(owner, part.area);
            for (Rect box : differences(part.before, shown)) {
                boolean ink = stroke >= 0 && opaqueBinary(shown, box);
                box.offset(part.area.left, part.area.top);
                if (ink) { box.offset(-union.left, -union.top); fast.add(box); } else grey.union(box);
            }
            now.drawBitmap(shown, part.area.left - union.left, part.area.top - union.top, null);
            shown.recycle(); part.before.recycle();
        }
        android.graphics.Matrix bitmapToView = new android.graphics.Matrix();
        bitmapToView.setTranslate(union.left, union.top);
        boolean ok = presentAll(owner, before, after, bitmapToView, fast, fastPlane ? 9 : stroke, fastPlane);
        before.recycle(); after.recycle();
        if (!grey.isEmpty()) {
            // The grey boxes in one update, as the whole column's was: the panel runs grey updates one after another. Drawn
            // again over their union (it may span patches, white between them in the bitmaps above), refreshed.
            Bitmap shown = capture(owner, grey);
            bitmapToView.setTranslate(grey.left, grey.top);
            ok &= presentAll(owner, null, shown, bitmapToView, java.util.Collections.singletonList(new Rect(0, 0, grey.width(), grey.height())), 7, false);
            shown.recycle();
            union.union(grey);
        }
        if (ok) retainForNextDraw(owner, union); else invalidateWithin(owner, union);
    }
    /** {@code boxes} of {@code after} (over {@code before}; {@code before} null: refreshed) put on the panel in {@code mode}; whether all of them went. */
    private boolean presentAll(View owner, Bitmap before, Bitmap after, android.graphics.Matrix bitmapToView, List<Rect> boxes, int mode, boolean fastPlane) {
        if (boxes.isEmpty()) return true;
        if (mode < 0) return false;
        DirectEink display = null;
        try {
            display = DirectEink.forView(owner, before == null ? after : before, bitmapToView, fastPlane ? 1 : 0, mode);
            for (Rect box : boxes) {
                if ((before == null ? display.refresh(after, box) : display.present(after, box)) <= 0) return false;
                submitted++; lastDisplayMode = mode; lastBufferRegion = display.bufferRegion(box);
                if (fastPlane) {
                    android.graphics.RectF panel = new android.graphics.RectF(box);
                    android.graphics.Matrix toPanel = new android.graphics.Matrix(bitmapToView);
                    toPanel.postConcat(PanelCoordinates.fromView(owner)); toPanel.mapRect(panel);
                    fastInk.union(panel);
                }
            }
            return true;
        } catch (RuntimeException error) {
            if (!loggedFailure) Log.w(ProbeActivity.TAG, "Selection feedback uses normal redraw", error);
            loggedFailure = true;
            return false;
        } finally {
            if (display != null) display.close();
        }
    }
    /** A patch of the gathering view, {@link #updateParts}: where (its pixels) and what it showed before. */
    private static final class Part {
        final Rect area; final Bitmap before;
        Part(Rect area, Bitmap before) { this.area = area; this.before = before; }
    }
    private List<Part> parts;
    /** {@code view}'s {@code area} as a patch of the gathering view, captured as it is now, unless one already holds it. */
    private void part(View view, Rect area) {
        Rect at = new Rect(area);
        ((android.view.ViewGroup) gathering).offsetDescendantRectToMyCoords(view, at);
        if (!at.intersect(0, 0, gathering.getWidth(), gathering.getHeight())) return;
        for (Part part : parts) if (part.area.contains(at)) return;
        parts.add(new Part(at, capture(gathering, at)));
    }
    /**
     * Inkwash: the view whose update is running, so the updates of views inside it (a slot's mark, the strip's tab, the
     * dot, as a slot is picked) gather into its one present. The panel runs its grey updates one after another, each
     * about 270 ms, so several small presents took several times as long as one over them all (2026-10-24).
     */
    private View gathering;
    private static boolean holds(View outer, View view) {
        if (outer == null) return false;
        for (Object at = view; at instanceof View; at = ((View) at).getParent()) if (at == outer) return true;
        return false;
    }
    private boolean direct(View owner, Rect area, boolean windowFocused) {
        return enabled && windowFocused && owner.isShown() && owner.getDisplay() != null && PanelCoordinates.fullyVisible(owner, area);
    }
    /** Presents the control's current pixels over {@code before}, which it recycles; through the fast pen plane only if {@code fast} allows. */
    private void present(View owner, Rect area, Bitmap before, boolean fast) {
        Bitmap after = capture(owner, area);
        boolean presented = false;
        try {
            Rect dirty = difference(before, after);
            if (dirty.isEmpty()) { presented = true; return; }
            android.graphics.Matrix bitmapToView = new android.graphics.Matrix();
            bitmapToView.setTranslate(area.left,area.top);
            // Each session owns only this control patch. The canvas is never included.
            boolean fastBinary = fast && DirectEink.fastBinaryControls() && opaqueBinary(after, dirty);
            int stroke = binary && !fastBinary && opaqueBinary(after, dirty) ? Device.current(owner.getContext()).fastStrokeMode() : -1;
            int mode = fastBinary ? 9 : stroke >= 0 ? stroke : 7;
            android.graphics.Matrix toPanel = PanelCoordinates.fromView(owner);
            boolean reused = session != null && sessionOwner == owner && sessionArea.equals(area)
                    && sessionMode == mode && sessionPanel.equals(toPanel);
            if (!reused) {
                closeSession();
                session = DirectEink.forView(owner,before,bitmapToView,fastBinary ? 1 : 0,mode);
                sessionOwner = owner; sessionArea.set(area); sessionMode = mode; sessionPanel.set(toPanel);
            }
            DirectEink display = session;
            if ((reused ? display.refresh(after, dirty) : display.present(after, dirty)) > 0) {
                presented = true;
                retainForNextDraw(owner, area);
                submitted++;
                lastDisplayMode = mode;
                lastBufferRegion = display.bufferRegion(dirty);
                if (fastBinary) {
                    android.graphics.RectF panel = new android.graphics.RectF(dirty);
                    bitmapToView.postConcat(toPanel); bitmapToView.mapRect(panel);
                    fastInk.union(panel);
                }
            }
            // Busy queues fall back to the normal redraw. Never replay stale UI pixels.
        } catch (RuntimeException error) {
            if (!loggedFailure) Log.w(ProbeActivity.TAG, "Selection feedback uses normal redraw", error);
            loggedFailure = true;
            closeSession();
        } finally {
            before.recycle();
            after.recycle();
            if (!presented) invalidateWithin(owner, area);
        }
    }

    /** Decide from the submitted patch, including its border, without flattening grays. */
    static boolean opaqueBinary(Bitmap bitmap, Rect area) {
        int[] row = new int[area.width()];
        for (int y = area.top; y < area.bottom; y++) {
            bitmap.getPixels(row, 0, row.length, area.left, y, row.length, 1);
            for (int pixel : row)
                if (pixel != android.graphics.Color.BLACK && pixel != android.graphics.Color.WHITE) return false;
        }
        return true;
    }

    private static Bitmap capture(View owner, Rect area) {
        Bitmap bitmap = Bitmap.createBitmap(area.width(), area.height(), Bitmap.Config.ARGB_8888);
        // Inkwash: on paper, as the screen shows it: the panel takes a pixel's red alone, so a control with no background
        // of its own (a plain-words button) would show black where it is clear.
        bitmap.eraseColor(android.graphics.Color.WHITE);
        // Apply the scroll offset a parent would, so scrolled text lands inside the view.
        Canvas canvas = new Canvas(bitmap); canvas.translate(-area.left-owner.getScrollX(), -area.top-owner.getScrollY());
        owner.draw(canvas); return bitmap;
    }
    /**
     * Inkwash: the changed pixels of {@code before} and {@code after} as boxes, each with the 2 px border {@link
     * #difference} keeps: split where a run of unchanged rows or columns parts them (a slot's mark and the old one's, the
     * size slider's tab and the dot), so each box is checked for ink and paper on its own.
     */
    static List<Rect> differences(Bitmap before, Bitmap after) {
        int width = before.getWidth(), height = before.getHeight();
        int[] a = new int[width*height], b = new int[a.length];
        before.getPixels(a,0,width,0,0,width,height); after.getPixels(b,0,width,0,0,width,height);
        boolean[] changed = new boolean[a.length];
        for (int i = 0; i < a.length; i++) changed[i] = a[i] != b[i];
        List<Rect> boxes = new ArrayList<>();
        cut(changed, width, height, new Rect(0, 0, width, height), boxes);
        return boxes;
    }
    /** Unchanged rows or columns that part two boxes: more than both borders. */
    private static final int GAP = 5;
    private static void cut(boolean[] changed, int width, int height, Rect within, List<Rect> boxes) {
        int left = within.right, top = within.bottom, right = within.left, bottom = within.top;
        boolean[] rows = new boolean[height], columns = new boolean[width];
        for (int y = within.top; y < within.bottom; y++) for (int x = within.left; x < within.right; x++) {
            if (!changed[y*width+x]) continue;
            rows[y] = columns[x] = true;
            left = Math.min(left, x); top = Math.min(top, y); right = Math.max(right, x+1); bottom = Math.max(bottom, y+1);
        }
        if (right <= left) return;
        Rect bounds = new Rect(left, top, right, bottom);
        List<Rect> pieces = runs(rows, bounds, true);
        if (pieces.size() == 1) pieces = runs(columns, bounds, false);
        if (pieces.size() == 1) { boxes.add(new Rect(Math.max(0,left-2),Math.max(0,top-2),Math.min(width,right+2),Math.min(height,bottom+2))); return; }
        for (Rect piece : pieces) cut(changed, width, height, piece, boxes);
    }
    /** {@code bounds} cut across at each run of more than {@link #GAP} unmarked rows ({@code across}) or columns. */
    private static List<Rect> runs(boolean[] marked, Rect bounds, boolean across) {
        List<Rect> pieces = new ArrayList<>();
        int from = across ? bounds.top : bounds.left, end = across ? bounds.bottom : bounds.right, start = from, gap = 0;
        for (int i = from; i < end; i++) {
            if (marked[i]) {
                if (gap > GAP) { pieces.add(across ? new Rect(bounds.left, start, bounds.right, i - gap) : new Rect(start, bounds.top, i - gap, bounds.bottom)); start = i; }
                gap = 0;
            } else gap++;
        }
        pieces.add(across ? new Rect(bounds.left, start, bounds.right, end) : new Rect(start, bounds.top, end, bounds.bottom));
        return pieces;
    }
    static Rect difference(Bitmap before, Bitmap after) {
        int width = before.getWidth(), height = before.getHeight();
        int[] a = new int[width*height], b = new int[a.length];
        before.getPixels(a,0,width,0,0,width,height); after.getPixels(b,0,width,0,0,width,height);
        int left=width,top=height,right=0,bottom=0;
        for (int y=0;y<height;y++) for (int x=0;x<width;x++) {
            if (a[y*width+x] == b[y*width+x]) continue;
            left=Math.min(left,x);top=Math.min(top,y);right=Math.max(right,x+1);bottom=Math.max(bottom,y+1);
        }
        if (right==0) return new Rect();
        return new Rect(Math.max(0,left-2),Math.max(0,top-2),Math.min(width,right+2),Math.min(height,bottom+2));
    }
}
