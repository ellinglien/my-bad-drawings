package io.github.mpdairy.monopaint;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.LinearLayout;

/**
 * The page bar: a thin strip the page's width (the owner, 2026-10-19: "use the supernote style and put it on a thin
 * bottom bar, same style as the document reader"), no rule between it and the page (the owner, 2026-10-20; {@link
 * DrawLayout#PAGE_RULE} stays as the gap). On the drawing screen it runs along the page's top (the owner, 2026-10-23:
 * "moving the pages bar to the top"), inside a notebook along its foot. On the drawing screen (docs/archive/superpowers/specs/2026-10-20-tracing-paper-design.md, "The bottom strip"), from the end
 * away from the column (clear of the drawing hand): Zoom; the page and its
 * count ("3 / 24", a cover's word), with its padlock and its Timed-page blink, between ‹ and ›; and a strip of tiny live previews of the
 * front cover, the pages and the back cover, a window round the page drawn on ({@link Strip}). On a preview a tap goes
 * to it, a quick drag riffles, a hold opens its Page Settings at once, before the pen lifts (the owner, 2026-10-24:
 * "have the page menu load right away, don't wait for pen lift"; a page moves from there, its Move row, since the hold
 * no longer lifts it to drag) ({@link StripGesture}). Inside a notebook ({@link NotebookScreens}) the same bar has ‹ and › either side of the number instead,
 * ‹ from the first page turning to the front cover and › from the last to the back cover. Its sizes are {@link
 * DrawLayout}'s. Pages turn through the drawing screen ({@code PaintActivity.changePage}: {@link PageTurnDisplay}); a
 * finger on the page stays the finger tool, and nothing riffles there. A stack of layers ({@link PageStacks}) is one page
 * here: one preview (its layers over each other), one number, a turn or a move takes it whole (the spec's "Stage 3c").
 * Its own class, so the bar's other changes stay apart from it.
 *
 * <p>Tracing paper (the spec's stages 2, 3 and 3b): what shows through ({@link TracingPaper}) is the library card's
 * (the owner, 2026-10-21: no Paper button on the bar). A page's Page Settings ({@link LibraryCard}) set its step and,
 * with Page Before, pin it as the ghost; a page showing through has a grey tab under its preview, one whose layers are all Hidden is dimmed. A layer's
 * step, set in the side column's layer menu until the refocus (2026-10-24), is kept as it was ({@link #STEP_ICONS}). Zoom ({@link ZoomButton}) is
 * first, before the number.
 */
final class PageTurner {
    /** What the bar does. */
    interface Host {
        void previous();
        /** The next page; past the last, the back cover. */
        void next();
    }

    final LinearLayout view;
    private final PaintActivity app;
    /** ‹ and › (by the number, on both bars); (the Notebook button left the drawing screen's bar, 2026-10-27). */
    private final ToolButton previous, next;
    /** Zoom and its lock on the drawing screen (else null). */
    final ZoomButton zoom;
    private final Strip strip;
    private final Number number;
    private int index, count = 1;
    /** The page is locked (its time ran out): a padlock by its number. */
    private boolean locked;
    /** The number is hidden for now: the slow blink of the last seconds of a timed page. */
    private boolean hidden;

    /** The + tile's words, said to a screen reader (docs/TEXT-STYLE.md, "The page bar"). */
    static final String NEW_PAGE = "New page";

    /** The bar for {@code host}; {@code notebook}: the drawing screen's, with Zoom and the previews. */
    PageTurner(PaintActivity app, Host host, boolean notebook) {
        this.app = app;
        view = LabUi.row(app);
        view.setBackgroundColor(Theme.PAPER);
        // The paper gap on the page's side: under the drawing screen's bar, over a notebook's.
        int gap = app.dp(DrawLayout.PAGE_RULE);
        view.setPadding(0, notebook ? 0 : gap, 0, notebook ? gap : 0);
        previous = app.iconButton("Previous page", R.drawable.ic_ph_caret_left, host::previous).slow();
        next = app.iconButton("Next page", R.drawable.ic_ph_caret_right, host::next).slow();
        strip = notebook ? new Strip() : null;
        zoom = notebook ? new ZoomButton(app) : null;
        number = new Number();
        show(0, 1);
    }

    /** Lays the bar inside a notebook out: ‹, the page and its count, ›, in the middle, its buttons {@code button} dp wide. */
    void place(int button, boolean columnRight) {
        view.removeAllViews();
        int wide = app.dp(button);
        previous.setBackground(null); next.setBackground(null);  // plain, as on the drawing screen's bar
        LabUi.spring(view);
        view.addView(previous, new LinearLayout.LayoutParams(wide, -1));
        view.addView(number, new LinearLayout.LayoutParams(app.dp(DrawLayout.pageNumber()), -1));
        view.addView(next, new LinearLayout.LayoutParams(wide, -1));
        LabUi.spring(view);
    }
    /**
     * Lays the drawing screen's bar out at {@code at}'s sizes ({@link DrawLayout#stripPlaces}): Zoom, the
     * number and the previews from the end away from the column, which is on the page's right when {@code columnRight}.
     */
    void place(DrawLayout at, boolean columnRight) {
        view.removeAllViews();
        strip.at = at; strip.columnRight = columnRight;
        // ‹ the number › as one part, left to right whichever side the column is.
        LinearLayout turn = LabUi.row(app);
        Ui.detach(previous); Ui.detach(number); Ui.detach(next);
        previous.setBackground(null); next.setBackground(null);  // plain, like the column's icons
        // Smaller carets in the same 32 dp targets (the owner, 2026-10-27: "reduce size of < and > carets on top menu").
        previous.iconHalf = next.iconHalf = DrawLayout.CARET_HALF;
        int arrow = app.dp(DrawLayout.PAGE_ARROW);
        turn.addView(previous, new LinearLayout.LayoutParams(arrow, -1));
        turn.addView(number, new LinearLayout.LayoutParams(0, -1, 1));
        turn.addView(next, new LinearLayout.LayoutParams(arrow, -1));
        View[] parts = {zoom.button, turn, new View(app), strip};
        int[] fixed = DrawLayout.barParts(), widths = new int[parts.length];
        for (int k = 0; k < fixed.length; k++) widths[k] = app.dp(fixed[k]);
        for (int k = 0; k < parts.length; k++) {
            int i = columnRight ? k : parts.length - 1 - k;
            view.addView(parts[i], new LinearLayout.LayoutParams(widths[i], -1, i == parts.length - 1 ? 1 : 0));
        }
        strip.requestLayout();
    }
    /** The drawing screen's sizes as last laid out (null before), and whether the column is on the page's right. */
    DrawLayout layout() { return strip == null ? null : strip.at; }
    boolean columnRight() { return strip == null || strip.columnRight; }

    /**
     * Page {@code index} of {@code count} is shown, or a cover ({@link DrawingBook#FRONT}, {@link DrawingBook#BACK}): the
     * numbers or the cover's word; inside a notebook ‹ greyed on the front cover and › on the back (the notebook's order:
     * the front cover, the pages, the back cover); on the drawing screen the previews round it.
     */
    void show(int index, int count) { show(index, count, false); }
    /**
     * As {@link #show(int, int)}; {@code turned}: another page of the same pages turned to. Then, while the page turned
     * to is in the previews' window, only what the turn changes goes to the panel: the frames and the underline, the
     * number, an arrow (the owner, 2026-10-25: "avoid a refresh of the top bar as well unless there is a new page added,
     * just move the underline"), each changed patch put on the panel as the side bars' are ({@link
     * SelectionFeedback#updateParts}). Pages added, deleted or moved, or another notebook: all of it again.
     */
    void show(int index, int count, boolean turned) {
        if (turned && strip != null && Math.max(1, count) == this.count && strip.keeps()) {
            app.selectionFeedback.updateParts(view, () -> {
                // Captured as they show now, then compared once the turn is in them.
                for (View part : new View[]{previous, number, next, strip}) app.selectionFeedback.update(part, () -> { });
                set(index, count);
                strip.marked();
            });
            return;
        }
        set(index, count);
        if (strip != null) strip.shown();
    }
    private void set(int index, int count) {
        this.index = index; this.count = Math.max(1, count);
        if (strip != null) {
            // The drawing screen's › past the last page turns to the back cover, so only ‹ on the front cover greys.
            dim(previous, R.drawable.ic_ph_caret_left, index != DrawingBook.FRONT);
            dim(next, R.drawable.ic_ph_caret_right, index != DrawingBook.BACK);
        } else {
            dim(previous, R.drawable.ic_ph_caret_left, index != DrawingBook.FRONT);
            previous.setContentDescription(index == 0 ? "Front cover" : "Previous page");
            next.setContentDescription(index == this.count - 1 ? "Back cover" : "Next page");
            dim(next, R.drawable.ic_ph_caret_right, index != DrawingBook.BACK);
        }
        number.setContentDescription(DrawLayout.pageSaid(index, this.count));
        number.invalidate();
    }
    /** Tracing paper changed (the Paper, a step, the ghost): the previews' marks show it, in the strip's fast update. */
    void tracingChanged() {
        if (strip == null) return;
        strip.update(() -> { for (int k = 0; k < strip.getChildCount(); k++) ((Strip.Preview) strip.getChildAt(k)).describe(); });
    }
    private TracingPaper tracing() { return app.book == null ? TracingPaper.NONE : app.book.tracing(); }
    /**
     * Each see-through step's eye, by {@link TracingPaper#STEPS} (the owner, 2026-10-21; Phosphor): Solid {@code eye}
     * Fill, Half {@code eye} Duotone, Faint {@code eye} Light, Hidden {@code eye-closed} Bold.
     */
    static final int[] STEP_ICONS = {0, R.drawable.ic_ph_eye_fill, R.drawable.ic_ph_eye_duotone, R.drawable.ic_ph_eye_light, R.drawable.ic_ph_eye_closed};
    /** The page drawn on changed: its preview is drawn again once it has been still a moment ({@link InkWashBar#THUMBNAILS_MS}), never under the pen. */
    void pageChanged() { if (strip != null) strip.pageChanged(); }
    /** A picture the previews or a riffle wait for is ready ({@link NotebookScreens}). */
    void picturesArrived() { if (strip != null) strip.picturesArrived(); }
    /**
     * The timed page's state ({@link TimedPages}): whether it is {@code locked} (a padlock by its number) and whether its
     * number is {@code hidden} now (blinking). A change is drawn over the number's own square only, through the panel's
     * fast update ({@link SelectionFeedback#update}), never the page's.
     */
    boolean numberHidden() { return hidden; }
    void timer(boolean locked, boolean hidden) {
        if (locked == this.locked && hidden == this.hidden) return;
        Runnable change = () -> { this.locked = locked; this.hidden = hidden; number.invalidate(); };
        if (number.isAttachedToWindow() && number.getWidth() > 0) app.selectionFeedback.update(number, change); else change.run();
        number.setContentDescription(DrawLayout.pageSaid(index, count) + (locked ? ". Locked" : ""));
    }
    /** {@code button} showing {@code icon}, enabled, or greyed (grey-400, no transparency: e-ink). */
    private void dim(ToolButton button, int icon, boolean enabled) {
        button.setEnabled(enabled);
        if (enabled) button.iconOnly(icon); else button.iconOnly(Theme.tinted(app, icon, Theme.GREY_400), null);
    }

    /** The page and its count in one line, in labels (14 sp, 700, caps, tracked), centred; between them the owner's drawn slash, the digits' height (2026-10-26). */
    @SuppressLint("ViewConstructor")
    private final class Number extends View {
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Rect digit = new android.graphics.Rect();
        /** The owner's slash at the digits' height, ink and clear ({@link Theme#shape}). */
        private android.graphics.Bitmap slash;
        Number() {
            super(app);
            text.setColor(Theme.INK);
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawColor(Theme.PAPER);
            TypeStyle type = TypeStyle.LABEL;
            text.setTypeface(Theme.face(type.weight));
            text.setTextSize(getResources().getDisplayMetrics().density * type.sp * LabText.SCALE);
            text.setLetterSpacing(type.tracking);
            String words = DrawLayout.pageWords(index, count).toUpperCase(java.util.Locale.ROOT);
            // "44 / 45": the words either side of the "/", the drawn slash between them (a cover's word has none).
            int cut = words.indexOf('/');
            String before = cut < 0 ? words : words.substring(0, cut), after = cut < 0 ? "" : words.substring(cut + 1);
            // The slash a digit's height, keeping its drawing's 72 × 142 px shape.
            text.getTextBounds("0", 0, 1, digit);
            int tall = digit.height(), wide = cut < 0 ? 0 : Math.max(1, Math.round(tall * 72f / 142));
            if (cut >= 0 && (slash == null || slash.getHeight() != tall || slash.getWidth() != wide)) {
                if (slash != null) slash.recycle();
                slash = Theme.shape(app, R.drawable.shape_page_slash, wide, tall);
            }
            float width = text.measureText(before) + wide + text.measureText(after), x = (getWidth() - width) / 2f;
            float baseline = getHeight() / 2f - (text.ascent() + text.descent()) / 2;
            if (!hidden) {
                canvas.drawText(before, x, baseline, text);
                if (cut >= 0) {
                    canvas.drawBitmap(slash, x + text.measureText(before), baseline - tall, null);
                    canvas.drawText(after, x + text.measureText(before) + wide, baseline, text);
                }
            }
            if (locked) padlock(canvas, x - app.dp(4), getHeight() / 2f);
        }
        /** A small closed padlock, ink, its right edge at {@code right}, centred on {@code middle}: a body and a shackle, 2 dp strokes as the bar's icons. */
        private void padlock(Canvas canvas, float right, float middle) {
            float u = app.dp(1), w = 10 * u, h = 7 * u, body = middle - h / 2 + 2 * u;
            text.setStyle(Paint.Style.FILL);
            canvas.drawRect(right - w, body, right, body + h, text);
            text.setStyle(Paint.Style.STROKE); text.setStrokeWidth(1.5f * u);
            android.graphics.RectF shackle = new android.graphics.RectF(right - w + 2 * u, body - 5 * u, right - 2 * u, body + 3 * u);
            canvas.drawArc(shackle, 180, 180, false, text);
            canvas.drawLine(right - w + 2 * u, body - 1 * u, right - w + 2 * u, body, text);
            canvas.drawLine(right - 2 * u, body - 1 * u, right - 2 * u, body, text);
            text.setStyle(Paint.Style.FILL); text.setStrokeWidth(0);
        }
    }

    // The previews (the drawing screen's)

    /** A screen reader's custom actions on a page's preview (spec, "Accessibility"); "up" is toward the top of the stack, the last page. */
    private static final int MOVE_UP = View.generateViewId(), MOVE_DOWN = View.generateViewId(), OPTIONS = View.generateViewId();
    private static final String MOVE_UP_WORDS = "Move Page Up", MOVE_DOWN_WORDS = "Move Page Down", OPTIONS_WORDS = LibraryCard.PAGE_SETTINGS;
    /** How often the window slides one page while a riffle or a lift is held at its end, ms. */
    static final long SLIDE_MS = 150;

    /**
     * The previews: one wide target ({@link DrawLayout#previewAt}), each preview a child that draws it and speaks for it.
     * Every update is a fast one of the strip alone ({@link SelectionFeedback#update}), in steps, never animated.
     */
    @SuppressLint("ViewConstructor")
    private final class Strip extends ViewGroup {
        DrawLayout at;
        boolean columnRight = true;
        private final StripGesture gesture;
        /** The first tile while a riffle or a lift holds the window (it slides at the ends), else -1: round the page drawn on. */
        private int held = -1;
        /** The place pressed, and the place a riffle shows. */
        private int pressed = DrawingBook.NONE, riffled = DrawingBook.NONE;
        private float fingerX;
        /** The touch began on the + tile. */
        private boolean plusDown;
        /** The riffle's picture over the page, while one is open. */
        private Riffle riffle;
        /**
         * The drawing page's preview, sampled from it ({@link LayerThumbnail#renderPage}), what it shows, and the place and
         * layer it is of: still shown for the page just left until that page's picture is ready, so a turn blanks no preview.
         */
        private final LayerThumbnail sampler = new LayerThumbnail();
        private Bitmap live;
        private ToneDocument liveOf;
        private long liveRevision;
        private int livePlace = DrawingBook.NONE, liveLayer = DrawingBook.NONE;
        /** The page (layer) the marks show as drawn on, of {@code drawnBook}: the book's own, but between a turn and its {@link #marked}. */
        private int drawnOn = DrawingBook.NONE;
        private DrawingBook drawnBook;
        /** The window's first tile as shown, kept while the page drawn on is in it, so a turn moves the marks alone (-1: none yet). */
        private int first = -1;
        private final Paint ink = new Paint(), frame = new Paint(), ghostTab = new Paint(), dimmed = new Paint(), multiply = new Paint();
        private final Runnable redrawLive = this::redrawLiveWhenIdle;
        private final Runnable hold = this::held;
        private final Runnable slide = this::slide;

        Strip() {
            super(app);
            gesture = new StripGesture(app.dp(StripGesture.MOVE_DP));
            setWillNotDraw(false);
            ink.setColor(Theme.INK);
            frame.setStyle(Paint.Style.STROKE);
            ghostTab.setColor(Theme.GREY_600);
            dimmed.setColor(Theme.PAPER); dimmed.setAlpha(Math.round(255 * (1 - LayerThumbnail.DIMMED)));
            // The page drawn on's live picture over its stack's other layers, as their marks lie over each other.
            multiply.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.MULTIPLY));
        }
        private DrawingBook book() { return app.book; }
        /** The stacks the strip's places are: a stack of layers is one page here. */
        private PageStacks stacks() { return book().stacks(); }
        /** The page (layer) drawn on as the marks show it, and its place: its stack, or a cover. */
        private int drawn() { return drawnBook == book() && book().valid(drawnOn) ? drawnOn : book().index(); }
        private int here() { return stacks().of(drawn()); }
        /** How many pages (stacks) there are. */
        private int count() { return stacks().count(); }
        /** Whether stack {@code place} shows through the page drawn on now (another stack). */
        private boolean ghosting(int place) {
            if (DrawingBook.isCover(place) || place == here()) return false;
            PageStacks s = stacks();
            for (int[] g : tracing().ghosts(drawn())) if (s.of(g[0]) == place) return true;
            return false;
        }
        /** Whether every layer of stack {@code place} is Hidden (a cover never is). */
        private boolean hidden(int place) {
            if (DrawingBook.isCover(place) || place >= count()) return false;
            PageStacks s = stacks();
            for (int p = s.first(place); p <= s.last(place); p++) if (tracing().step(p) != TracingPaper.HIDDEN) return false;
            return true;
        }
        private int tiles() { return book() == null ? 0 : count() + 3; }
        /** How many previews there is room for, and how many show. */
        private int room() { return at == null || view.getWidth() == 0 ? 0 : at.previews(Math.round(view.getWidth() / getResources().getDisplayMetrics().density)); }
        /** How many slots show. */
        private int shownCount() { return Math.min(getChildCount(), tiles()); }
        /**
         * The + tile's slot: its own tile, right after the last page and before the back cover, where the window has it; else
         * pinned in the window's last slot (it takes that tile's place), so a page can always be added.
         */
        private int plusSlot() { int k = count() + 1 - start(); return k >= 0 && k < shownCount() ? k : shownCount() - 1; }
        private int start() { return held >= 0 ? held : round(); }
        /** The first tile of the window: as it was while the page drawn on shows in it, else round that page. */
        private int round() {
            if (book() == null) return 0;
            int tile = StripGesture.tileOf(here(), count());
            if (!StripGesture.shows(first, tile, count(), shownCount())) first = NotebookLayout.window(tile, tiles(), shownCount());
            return first;
        }
        /** Whether the page the book draws on now shows in the window as it is, so a turn to it needs only the marks moved. */
        boolean keeps() {
            return book() != null && drawnBook == book() && held < 0 && getChildCount() > 0 && !isLayoutRequested()
                    && StripGesture.shows(first, StripGesture.tileOf(stacks().of(book().index()), count()), count(), shownCount());
        }
        /** The first preview's left, px: the previews sit against the number. */
        private int firstLeft() {
            int n = shownCount(), used = n == 0 ? 0 : app.dp(n * (at.previewWidth() + at.previewGap()) - at.previewGap());
            return columnRight ? 0 : getWidth() - used;
        }
        /** The place under {@code x}: the nearest preview's. */
        private int placeAt(float x) {
            float dp = (x - firstLeft()) / getResources().getDisplayMetrics().density;
            int slot = at.previewAt(dp, shownCount());
            return slot == plusSlot() ? DrawingBook.NONE : StripGesture.placeAt(start() + slot, count());
        }

        /** Whether {@code x} is over the + tile. */
        private boolean overPlus(float x) {
            float dp = (x - firstLeft()) / getResources().getDisplayMetrics().density;
            return at.previewAt(dp, shownCount()) == plusSlot();
        }

        @Override protected void onMeasure(int width, int height) {
            setMeasuredDimension(MeasureSpec.getSize(width), MeasureSpec.getSize(height));
            int room = room();
            if (room != getChildCount() && room > 0) post(this::build);
            for (int k = 0; k < getChildCount(); k++)
                getChildAt(k).measure(MeasureSpec.makeMeasureSpec(app.dp(at.previewWidth()), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.EXACTLY));
        }
        @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) { post(this::build); }
        /** The strip is one target: the previews never take a touch themselves. */
        @Override public boolean onInterceptTouchEvent(MotionEvent event) { return true; }
        /** As many previews as there is room for. */
        private void build() {
            int room = room();
            if (room == getChildCount() || room <= 0) return;
            removeAllViews();
            for (int k = 0; k < room; k++) addView(new Preview(k));
            requestLayout(); shown();
        }
        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int left = firstLeft(), step = app.dp(at.previewWidth() + at.previewGap());
            for (int k = 0; k < getChildCount(); k++) {
                View child = getChildAt(k);
                child.layout(left + k * step, 0, left + k * step + child.getMeasuredWidth(), b - t);
            }
        }
        /** Another page drawn on, or another count: the window round it, and what each preview says. */
        void shown() {
            if (book() == null) return;
            if (drawnBook != book()) first = -1;
            // The places moved: the sample stays only with the page drawn on.
            if (app.pad != null && liveOf == app.pad.document) { livePlace = stacks().of(book().index()); liveLayer = book().index(); } else livePlace = liveLayer = DrawingBook.NONE;
            marked();
            requestLayout();
            invalidateAll();
        }
        /** The page drawn on now is the one the marks show; what each preview says follows. */
        void marked() {
            drawnOn = book().index(); drawnBook = book();
            for (int k = 0; k < getChildCount(); k++) ((Preview) getChildAt(k)).describe();
        }
        private void invalidateAll() { invalidate(); for (int k = 0; k < getChildCount(); k++) getChildAt(k).invalidate(); }
        /** {@code change} to the strip, shown at once through the fast update of the strip alone. */
        private void update(Runnable change) {
            if (isAttachedToWindow() && getWidth() > 0) app.selectionFeedback.update(this, () -> { change.run(); invalidateAll(); });
            else { change.run(); invalidateAll(); }
        }

        // The drawing page's live preview

        void pageChanged() { removeCallbacks(redrawLive); postDelayed(redrawLive, InkWashBar.THUMBNAILS_MS); }
        private void redrawLiveWhenIdle() {
            // Never under the pen: wait for the stroke to end.
            if (app.pad == null || app.pad.document == null || app.pad.stroking() || app.busy()) { pageChanged(); return; }
            ToneDocument page = app.pad.document;
            if (page == liveOf && page.revision() == liveRevision) return;
            // Turned to and unchanged since its picture: the picture shows it.
            if (page != liveOf && book() != null && book().picture(book().index()) != null) return;
            Preview shown = previewOf(book() == null ? DrawingBook.NONE : here());
            if (shown == null || shown.getWidth() == 0) return;
            RectF to = shown.picture();
            int w = Math.max(1, Math.round(to.width())), h = Math.max(1, Math.round(to.height()));
            int[] pixels = new int[w * h];
            sampler.renderPage(page, w, h, pixels);
            Bitmap made = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888);
            int place = here(), layer = book().index();
            // The sample moving off another page: that page's preview goes back to its picture in the same update.
            app.selectionFeedback.update(place == livePlace ? shown : this, () -> { live = made; liveOf = page; liveRevision = page.revision(); livePlace = place; liveLayer = layer; shown.invalidate(); });
        }
        private Preview previewOf(int place) {
            if (book() == null || place == DrawingBook.NONE) return null;
            int k = StripGesture.tileOf(place, count()) - start();
            return k >= 0 && k < shownCount() && k != plusSlot() ? (Preview) getChildAt(k) : null;
        }
        void picturesArrived() {
            if (riffle != null) app.updatePanel(riffle::invalidate);
            if (isAttachedToWindow() && getWidth() > 0) update(() -> { });
        }

        // Gestures

        @SuppressLint("ClickableViewAccessibility")
        @Override public boolean onTouchEvent(MotionEvent event) {
            DrawingBook book = book();
            if (book == null || at == null) return true;
            long time = event.getEventTime();
            float x = event.getX(), y = event.getY();
            fingerX = x;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (app.busy() || app.pad.stroking()) { gesture.cancel(); return true; }
                    held = start(); plusDown = overPlus(x); pressed = plusDown ? DrawingBook.NONE : placeAt(x);
                    boolean side = (event.getButtonState() & MotionEvent.BUTTON_STYLUS_PRIMARY) != 0;
                    if (gesture.down(time, x, y, side) == StripGesture.Kind.OPTIONS) { int place = pressed; end(); options(place); return true; }
                    if (!plusDown) postDelayed(hold, StripGesture.HOLD_MS);  // the + has no long press
                    return true;
                case MotionEvent.ACTION_POINTER_DOWN:
                case MotionEvent.ACTION_CANCEL:
                    if (gesture.kind() != StripGesture.Kind.CANCELLED) { gesture.cancel(); end(); if (riffle != null) closeRiffle(DrawingBook.NONE); }
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (gesture.kind() == StripGesture.Kind.CANCELLED || gesture.kind() == StripGesture.Kind.OPTIONS) return true;
                    // A hold opened Page Settings already (held), so a move here is a riffle or nothing.
                    gesture.move(time, x, y);
                    if (!plusDown) follow();
                    return true;
                case MotionEvent.ACTION_UP:
                    if (gesture.kind() == StripGesture.Kind.CANCELLED || gesture.kind() == StripGesture.Kind.OPTIONS) { end(); return true; }
                    StripGesture.Kind done = gesture.up(time, x, y);
                    int riffledTo = riffled, tapped = pressed;
                    boolean plus = plusDown;
                    end();
                    if (plus) { if (done == StripGesture.Kind.TAP && !app.busy()) app.newPage(); }
                    else if (done == StripGesture.Kind.TAP) go(tapped);
                    else if (done == StripGesture.Kind.OPTIONS) options(tapped);
                    else if (done == StripGesture.Kind.RIFFLE) closeRiffle(riffledTo);
                    return true;
                default: return true;
            }
        }
        /** A riffle shows the place under the finger; at an end the window slides. */
        private void follow() {
            if (gesture.kind() != StripGesture.Kind.RIFFLE) return;
            int place = placeAt(fingerX);
            if (place != DrawingBook.NONE) riffle(place);  // over the + the riffle stays as it was
            int k = at.previewAt((fingerX - firstLeft()) / getResources().getDisplayMetrics().density, shownCount());
            boolean atEnd = k == 0 && start() > 0 || k == shownCount() - 1 && start() + shownCount() < tiles();
            removeCallbacks(slide);
            if (atEnd) postDelayed(slide, SLIDE_MS);
        }
        private void slide() {
            if (gesture.kind() != StripGesture.Kind.RIFFLE) return;
            int k = at.previewAt((fingerX - firstLeft()) / getResources().getDisplayMetrics().density, shownCount());
            int by = k == 0 ? -1 : k == shownCount() - 1 ? 1 : 0;
            int next = Math.max(0, Math.min(tiles() - shownCount(), held + by));
            if (next == held) return;
            update(() -> held = next);
            follow();
        }
        /** The hold's time is up: the page pressed opens its Page Settings now, the pen still down; the rest of the touch does nothing. */
        private void held() {
            if (gesture.tick(SystemClock.uptimeMillis()) != StripGesture.Kind.HELD) return;
            int place = pressed;
            gesture.cancel();
            end();
            options(place);
        }
        /** The gesture is over: the window round the page drawn on again. */
        private void end() {
            removeCallbacks(hold); removeCallbacks(slide);
            if (held >= 0 && held != round()) update(this::reset); else reset();
        }
        private void reset() { held = -1; pressed = DrawingBook.NONE; plusDown = false; }
        private void go(int place) {
            if (book() == null || !DrawingBook.isCover(place) && (place < 0 || place >= count())) return;
            app.drawOnStack(place);
        }

        // The riffle: the picture of the page under the finger, over the page (nothing decoded)

        private void riffle(int place) {
            if (place == riffled) return;
            if (riffle == null) {
                if (app.busy()) return;
                app.dismissPanels();
                app.pad.suspend();
                riffle = new Riffle();
                riffled = place;
                app.showPanelAt(riffle, Popups.bounds(app.root, app.pad));
                return;
            }
            app.updatePanel(() -> { riffled = place; riffle.invalidate(); });
        }
        /** The riffle lifts: the page under the finger turned to, or the page drawn on shown again. */
        private void closeRiffle(int place) {
            riffle = null; riffled = DrawingBook.NONE;
            DrawingBook book = book();
            if (book != null && (DrawingBook.isCover(place) || place >= 0 && place < count()) && place != here() && !app.busy()) { app.closePagePanel(false); app.drawOnStack(place); }
            else app.closePagePanel();
        }

        /** A hold on a page (or its screen-reader action): its Page Settings ({@link LibraryCard}), a screen of their own. */
        private void options(int place) {
            if (book() == null || DrawingBook.isCover(place) || place < 0 || place >= count() || app.notebooks == null) return;
            app.notebooks.showPageSettings(place, true);
        }

        @Override protected void dispatchDraw(Canvas canvas) {
            canvas.drawColor(Theme.PAPER);
            super.dispatchDraw(canvas);
        }

        /** One preview: the place at its tile, framed; the page drawn on in ink with a tab under it. */
        @SuppressLint("ViewConstructor")
        private final class Preview extends View {
            private final int slot;
            Preview(int slot) { super(app); this.slot = slot; setFocusable(true); }
            /** The place it shows, or {@link DrawingBook#NONE} past the last. */
            int place() { return book() == null || slot >= shownCount() || slot == plusSlot() ? DrawingBook.NONE : StripGesture.placeAt(start() + slot, count()); }
            /** Where its picture goes inside its frame, px. */
            RectF picture() {
                float stroke = 1, top = app.dp(DrawLayout.STRIP_EDGE);
                return NotebookScreens.fitted(book(), new RectF(stroke, top + stroke, getWidth() - edge(), top + app.dp(at.previewHeight()) - edge()));
            }
            /** Whether it is the + tile ({@link #plusSlot}). */
            boolean plus() { return book() != null && slot < shownCount() && slot == plusSlot(); }
            void describe() {
                if (plus()) { setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES); setContentDescription(NEW_PAGE); return; }
                int place = place();
                setImportantForAccessibility(place == DrawingBook.NONE ? IMPORTANT_FOR_ACCESSIBILITY_NO : IMPORTANT_FOR_ACCESSIBILITY_YES);
                if (place == DrawingBook.NONE) return;
                setContentDescription(DrawLayout.pageSaid(place, count()) + (place == here() ? ", drawing" : ghosting(place) ? ", ghost" : hidden(place) ? ", hidden" : ""));
            }
            @Override protected void onDraw(Canvas canvas) {
                int place = place();
                DrawingBook book = book();
                if (plus()) { drawPlus(canvas); return; }
                if (place == DrawingBook.NONE) return;
                boolean current = place == here();
                // Every edge one hairline, a pixel (the owner, 2026-10-24: "use same stroke size for page outlines on top", then
                // "strokes around pages on top toolbar should be hairline"; 2 dp until then): the page drawn on is told by its
                // ink edges and its tab under it.
                float stroke = 1, top = app.dp(DrawLayout.STRIP_EDGE);
                RectF box = new RectF(0, top, getWidth(), top + app.dp(at.previewHeight()));
                RectF inside = new RectF(box.left + stroke, box.top + stroke, box.right - edge(), box.bottom - edge());
                // Its layers' pictures over each other; the page sampled last (the page drawn on, or the one just left) from
                // its sample over its stack's other layers. A page turned to with no sample shows its picture, as it did, so a
                // turn changes only the marks; so does the page left, if its sample missed its last strokes.
                boolean cover = DrawingBook.isCover(place);
                int layer = cover ? DrawingBook.NONE : current ? drawn() : place == livePlace ? liveLayer : DrawingBook.NONE;
                boolean sampled = live != null && layer != DrawingBook.NONE && place == livePlace && layer == liveLayer;
                boolean fresh = sampled && (current || liveOf.revision() == liveRevision);
                boolean whole = !fresh && layer != DrawingBook.NONE && book.picture(layer) != null && app.notebooks != null
                        && app.notebooks.drawStackPreview(canvas, book, place, inside, DrawingBook.NONE);
                if (!whole && app.notebooks != null) app.notebooks.drawStackPreview(canvas, book, place, inside, cover && current ? place : layer);
                if (!whole && sampled) canvas.drawBitmap(live, null, NotebookScreens.fitted(book, inside), multiply);
                edges(canvas, box, current ? Theme.INK : Theme.GREY_400);
                float tab = box.bottom + app.dp(DrawLayout.STRIP_TAB_GAP);
                // Hidden: the preview at LayerThumbnail.DIMMED, paper laid over it (spec, "The bottom strip").
                if (hidden(place)) canvas.drawRect(inside, dimmed);
                if (current) canvas.drawRect(box.left, tab, box.right, tab + app.dp(DrawLayout.STRIP_TAB), ink);
                else if (ghosting(place)) canvas.drawRect(box.left, tab, box.right, tab + app.dp(DrawLayout.GHOST_TAB), ghostTab);
                if (current && !cover && !whole && !sampled) Strip.this.pageChanged();
            }
            /**
             * A preview's frame: a line ({@link #edge}) along its right side and its foot only, a page's edge (the owner, 2026-10-26:
             * "instead of full stroke for each page preview on the top bar, let's just have a line on the right side and the bottom").
             */
            private void edges(Canvas canvas, RectF box, int color) {
                float w = edge();
                frame.setStrokeWidth(w); frame.setColor(color);
                canvas.drawLine(box.right - w / 2, box.top, box.right - w / 2, box.bottom, frame);
                canvas.drawLine(box.left, box.bottom - w / 2, box.right, box.bottom - w / 2, frame);
            }
            /** The right and foot lines' width, px: a pixel and 1 dp (the owner, 2026-10-27: "slightly thicker lines on right and bottom of preview pages at top"; 3 px on the Manta, as a pixel alone is too faint on e-ink). */
            private float edge() { return 1 + app.dp(1); }
            /** The + tile: a preview's size and hairline edges, the Phosphor plus centred in it. */
            private void drawPlus(Canvas canvas) {
                float top = app.dp(DrawLayout.STRIP_EDGE), h = app.dp(at.previewHeight());
                edges(canvas, new RectF(0, top, getWidth(), top + h), Theme.GREY_400);
                Drawable icon = app.getDrawable(R.drawable.ic_ph_plus).mutate();
                icon.setTint(Theme.INK);
                int size = Math.min(getWidth(), Math.round(h)) * 2 / 3, cx = getWidth() / 2, cy = Math.round(top + h / 2);
                icon.setBounds(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2);
                icon.draw(canvas);
            }
            // Without dragging (spec, "Accessibility"): go, move the page either way, its options.
            @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(info);
                int place = place();
                if (place == DrawingBook.NONE && !plus()) return;
                info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK);
                if (plus()) return;
                if (DrawingBook.isCover(place)) return;
                if (place < count() - 1) info.addAction(new AccessibilityNodeInfo.AccessibilityAction(MOVE_UP, MOVE_UP_WORDS));
                if (place > 0) info.addAction(new AccessibilityNodeInfo.AccessibilityAction(MOVE_DOWN, MOVE_DOWN_WORDS));
                info.addAction(new AccessibilityNodeInfo.AccessibilityAction(OPTIONS, OPTIONS_WORDS));
            }
            @Override public boolean performAccessibilityAction(int action, Bundle arguments) {
                if (plus()) {
                    if (action != AccessibilityNodeInfo.ACTION_CLICK) return super.performAccessibilityAction(action, arguments);
                    if (!app.busy()) app.newPage();
                    return true;
                }
                int place = place();
                if (place == DrawingBook.NONE) return super.performAccessibilityAction(action, arguments);
                if (action == AccessibilityNodeInfo.ACTION_CLICK) { go(place); return true; }
                if (action == MOVE_UP) { app.moveStack(place, place + 1); return true; }
                if (action == MOVE_DOWN) { app.moveStack(place, place - 1); return true; }
                if (action == OPTIONS) { options(place); return true; }
                return super.performAccessibilityAction(action, arguments);
            }
        }

        /** The riffle's picture over the page area: the place under the finger, on paper. */
        @SuppressLint("ViewConstructor")
        private final class Riffle extends View {
            Riffle() { super(app); }
            @Override protected void onDraw(Canvas canvas) {
                canvas.drawColor(Theme.PAPER);
                DrawingBook book = book();
                if (book == null || app.notebooks == null) return;
                RectF all = new RectF(0, 0, getWidth(), getHeight());
                // The page drawn on is never copied for a picture: without one, its preview, larger, over its stack's other layers.
                boolean drawing = riffled == here() && !DrawingBook.isCover(riffled);
                app.notebooks.drawPlace(canvas, book, riffled, all, !drawing);
                if (drawing && book.picture(book.index()) == null && live != null && liveOf == app.pad.document) canvas.drawBitmap(live, null, NotebookScreens.fitted(book, all), multiply);
            }
        }
    }
}
