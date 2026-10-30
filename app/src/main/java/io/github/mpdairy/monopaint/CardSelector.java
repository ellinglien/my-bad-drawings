package io.github.mpdairy.monopaint;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;

/**
 * A settings screen over the notebook screen (the owner, 2026-10-21; opened from the settings list, {@link LibraryCard},
 * or a hold in the strip): Back (‹ Settings from the list) and Done in a bar, then the screen {@link CardSheet} lays out,
 * drawn as {@link CardArt} draws it: its title and what it does, its choices as grey tiles (each its own view, said to a
 * screen reader), the one in use with the ink tab. A tap on a choice sets it and goes back. Template's tiles show each
 * template's guides (Random a dice, shown while the notebook is Random; a long press on it, or More's Random Template
 * row, opens <b>Random From</b>, the
 * templates it draws from, tabbed by taps and set by Done); Cover Design's each design's front cover. Pictures are made in
 * the background, a blank page until each is ready. A slider (Timed Pages; Brushes under Tools' choices) sets the stop
 * slid to on Back, or before a choice.
 */
final class CardSelector {
    private final PaintActivity app;
    private final FrameLayout over;
    private final NotebookLayout layout;
    private final Executor worker;
    private final Runnable closed;
    /** Back's word: the place it goes back to. */
    private final String back;
    /** The screen shown, null once closed (pictures arriving after are dropped). */
    private View shown;
    /** A slider's stop, set as its screen closes; null for none. */
    private Runnable pending;

    /** A selector over {@code over} (the notebook screen), sized by {@code layout}, its pictures made on {@code worker}; {@code closed} runs once it goes; Back says {@code back}. */
    CardSelector(PaintActivity app, FrameLayout over, NotebookLayout layout, Executor worker, Runnable closed, String back) {
        this.app = app; this.over = over; this.layout = layout; this.worker = worker; this.closed = closed; this.back = back;
    }

    /** The screen opened and its page ({@link #open}), to open again when the screen turns. */
    CardSheet.Screen screen;
    int page;

    /** Opens screen {@code screen} for the open notebook ({@code page}: the page whose own template {@link CardSheet.Screen#PAGE_TEMPLATE} sets). */
    void open(CardSheet.Screen screen, int page) {
        DrawingBook book = app.book;
        if (book == null) return;
        this.screen = screen; this.page = page;
        if (screen == CardSheet.Screen.RANDOM_FROM) { randomFrom(); return; }
        NotebookOptions o = book.options();
        List<NotebookTemplate> templates = NotebookTemplate.choices();
        // Random only where the notebook is Random already (a page's own: choosing it gives the page a new pick); it is
        // set from Settings' More, Random Template (the refocus, 2026-10-24).
        if (!o.template.random()) templates.removeIf(NotebookTemplate::random);
        String[] names = CardSheet.names(screen, templates, o);
        int chosen = CardSheet.chosen(screen, o, book.tracing(), book.design(), templates, screen == CardSheet.Screen.PAGE_TEMPLATE ? book.template(page) : null);
        CardSheet.Layout sheet = sheet(screen, names.length, page);
        String name = DrawingFiles.name(app.drawingName);
        List<View> cards = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            int choice = i;
            BiFunction<Integer, Integer, byte[]> picture = null;
            if (screen == CardSheet.Screen.COVER) { CoverDesign design = CoverDesign.values()[i]; picture = (w, h) -> NotebookCovers.paper(true, design, name, w, h, null); }
            else if (CardSheet.pictured(screen)) picture = templates.get(i)::shared;
            View card = card(sheet, screen, names[i], CardSheet.note(screen, i, i == chosen), CardSheet.icon(screen, i, templates), i == chosen, true, picture);
            card.setOnClickListener(v -> choose(screen, choice, page, templates));
            if ((screen == CardSheet.Screen.TEMPLATE) && templates.get(i).random()) card.setOnLongClickListener(v -> { randomFrom(); return true; });
            cards.add(card);
        }
        View slider = null;
        if (CardSheet.slides(screen)) {
            int at = screen == CardSheet.Screen.TIMED ? Math.max(0, java.util.Arrays.binarySearch(NotebookOptions.SECONDS, o.seconds)) : CardSheet.brushesStop(o.brushes);
            Slider s = new Slider(screen, sheet.sliderLook, at, CardSheet.sliderHead(screen));
            pending = () -> { if (s.at != at) set(screen, s.at); };
            slider = s;
        }
        View chips = null;
        if (sheet.chips != null) chips = new Chips(o.guidesShown);
        show(sheet, CardSheet.title(screen), cards, slider, chips, null);
    }

    /** The screen's layout in the notebook screen's room under the bar. */
    private CardSheet.Layout sheet(CardSheet.Screen screen, int count, int page) {
        return new CardSheet.Layout(layout.width - 2 * layout.margin, layout.height - 2 * layout.margin - NotebookLayout.BAR - layout.gap, layout.compact, screen, count, layout.aspect, app.book == null || page < 0 || page >= app.book.count() ? page : app.book.stacks().of(page), app.book != null && CardSheet.chips(screen, app.book.options()));
    }

    /** Choice {@code choice} of {@code screen} set for the open notebook, then back to the card (Sealed asks first). */
    private void choose(CardSheet.Screen screen, int choice, int page, List<NotebookTemplate> templates) {
        DrawingBook book = app.book;
        if (book == null || app.busy()) return;
        // Brushes slid to on the Tools screen go in first.
        Runnable slid = pending; pending = null;
        if (slid != null) slid.run();
        NotebookOptions o = book.options();
        switch (screen) {
            case TEMPLATE: {
                NotebookTemplate t = templates.get(choice);
                // Random keeps the set it had.
                if (!(t.random() && o.template.random())) app.setNotebookOptions(o.withTemplate(t));
                break;
            }
            case PAGE_TEMPLATE: app.setPageTemplate(page, templates.get(choice)); break;
            case TRANSPARENCY: {
                TracingPaper t = CardSheet.transparency(choice);
                app.setTracing(book.tracing().withPaper(t.paper).withStrength(t.strength));
                break;
            }
            case TOOLS: close(); app.chooseTools(NotebookOptions.Tools.values()[choice]); return;
            case COVER: app.setCoverDesign(CoverDesign.values()[choice]); break;
            default: break;
        }
        close();
    }
    /** A slider's stop {@code at} set: Timed Pages' time, or Brushes' count. */
    private void set(CardSheet.Screen screen, int at) {
        DrawingBook book = app.book;
        if (book == null || app.busy()) return;
        NotebookOptions o = book.options();
        app.setNotebookOptions(screen == CardSheet.Screen.TIMED ? o.withSeconds(NotebookOptions.SECONDS[at]) : o.withBrushes(CardSheet.brushesAt(at)));
    }

    /** Random From: every template but Blank and Random, ticked while Random draws from it (none ticked: any built-in); Done sets them. */
    private void randomFrom() {
        DrawingBook book = app.book;
        if (book == null) return;
        List<NotebookTemplate> all = new ArrayList<>();
        for (NotebookTemplate t : NotebookTemplate.choices()) if (t != NotebookTemplate.BLANK && !t.random()) all.add(t);
        List<NotebookTemplate> set = book.options().template.set();
        CardSheet.Layout sheet = sheet(CardSheet.Screen.RANDOM_FROM, all.size(), 0);
        String[] names = CardSheet.names(CardSheet.Screen.RANDOM_FROM, all);
        boolean[] ticked = new boolean[all.size()];
        List<View> cards = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            int at = i;
            ticked[i] = set.contains(all.get(i));
            Card card = card(sheet, CardSheet.Screen.RANDOM_FROM, names[i], null, null, ticked[i], true, all.get(i)::shared);
            card.setOnClickListener(v -> { ticked[at] = !ticked[at]; card.on = ticked[at]; card.describe(); card.invalidate(); });
            cards.add(card);
        }
        pending = null;
        show(sheet, CardSheet.title(CardSheet.Screen.RANDOM_FROM), cards, null, null, () -> {
            List<NotebookTemplate> chosen = new ArrayList<>();
            for (int i = 0; i < ticked.length; i++) if (ticked[i]) chosen.add(all.get(i));
            if (app.book == book && !app.busy()) app.setNotebookOptions(book.options().withTemplate(CardSheet.randomFrom(chosen, all)));
            close();
        });
    }

    /** The screen: Back (and Done, when {@code done} is given) in the bar, then the sheet: its head, {@code cards} and {@code slider} where {@code sheet} puts them. */
    private void show(CardSheet.Layout sheet, String title, List<View> cards, View slider, View chips, Runnable done) {
        if (shown != null) over.removeView(shown);
        LinearLayout column = LabUi.column(app);
        column.setBackgroundColor(Theme.PAPER);
        column.setClickable(true);
        column.setPadding(app.dp(layout.margin), app.dp(layout.margin), app.dp(layout.margin), app.dp(layout.margin));
        LinearLayout bar = LabUi.row(app);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        LabButton back = new LabButton(app, R.drawable.ic_ph_caret_left, this.back, false);
        back.setOnClickListener(v -> close());
        bar.addView(back, new LinearLayout.LayoutParams(-2, app.dp(NotebookLayout.BUTTON)));
        bar.addView(new View(app), new LinearLayout.LayoutParams(0, 1, 1));
        if (done != null) {
            LabButton button = new LabButton(app, 0, NotebookLayout.DONE, true);
            button.setOnClickListener(v -> done.run());
            bar.addView(button, new LinearLayout.LayoutParams(-2, app.dp(NotebookLayout.BUTTON)));
        }
        column.addView(bar, new LinearLayout.LayoutParams(-1, app.dp(NotebookLayout.BAR)));
        FrameLayout page = new FrameLayout(app);
        CanvasPen.ArtView head = new CanvasPen.ArtView(app, (pen, w, h) -> CardSheet.head(pen, sheet, title));
        head.setContentDescription(title + ". " + String.join(" ", sheet.about));
        place(page, head, new float[]{0, 0, sheet.w, sheet.headHeight});
        for (int i = 0; i < cards.size(); i++) place(page, cards.get(i), sheet.choices.get(i));
        if (slider != null) place(page, slider, sheet.slider);
        if (chips != null) place(page, chips, sheet.chips);
        LinearLayout.LayoutParams sheetPlace = new LinearLayout.LayoutParams(-1, 0, 1);
        sheetPlace.topMargin = app.dp(layout.gap);
        if (sheet.scrolls) {
            // A Template grid too long for the screen: the one thing that scrolls.
            ScrollView scroll = new ScrollView(app);
            scroll.addView(page, new FrameLayout.LayoutParams(-1, app.dp(sheet.height)));
            column.addView(scroll, sheetPlace);
        } else column.addView(page, sheetPlace);
        shown = column;
        over.addView(column, new FrameLayout.LayoutParams(-1, -1));
    }
    private void place(FrameLayout page, View view, float[] box) {
        FrameLayout.LayoutParams at = new FrameLayout.LayoutParams(app.dp(box[2] - box[0]), app.dp(box[3] - box[1]));
        at.leftMargin = app.dp(box[0]); at.topMargin = app.dp(box[1]);
        page.addView(view, at);
    }
    /** Back (a slider's stop set first). */
    void close() {
        Runnable set = pending;
        pending = null;
        if (set != null) set.run();
        if (shown == null) return;
        over.removeView(shown);
        shown = null;
        closed.run();
    }
    boolean shown() { return shown != null; }

    /** A choice's card: its picture made in the background ({@code picture}'s tones, at its box's pixels; null: none). */
    private Card card(CardSheet.Layout sheet, CardSheet.Screen screen, String name, String note, CardArt.Icon icon, boolean on, boolean enabled, BiFunction<Integer, Integer, byte[]> picture) {
        Card card = new Card(sheet, screen, name, note, icon, on, enabled);
        if (picture != null) {
            // Every choice is the same size.
            float[] cell = sheet.choices.get(0);
            float[] box = CardArt.pictureBox(cell[2] - cell[0], cell[3] - cell[1], layout.aspect, sheet.look);
            int w = Math.max(1, app.dp(box[2] - box[0])), h = Math.max(1, app.dp(box[3] - box[1]));
            worker.execute(() -> {
                Bitmap mask = null;
                try { byte[] tones = picture.apply(w, h); if (tones != null) mask = NotebookScreens.mask(tones, w, h); }
                catch (RuntimeException e) { android.util.Log.w(ProbeActivity.TAG, "Could not make a choice's picture", e); }
                Bitmap made = mask;
                app.runOnUiThread(() -> { if (shown != null) { card.picture = made; card.invalidate(); } });
            });
        }
        return card;
    }

    /** A choice drawn as {@link CardArt#choice}, said to a screen reader as its name, its note and whether it is in use (Random From's: ticked). */
    @SuppressLint("ViewConstructor")
    private final class Card extends CanvasPen.ArtView {
        final String name, note;
        boolean on;
        Object picture;
        Card(CardSheet.Layout sheet, CardSheet.Screen screen, String name, String note, CardArt.Icon icon, boolean on, boolean enabled) {
            super(app, null);
            this.name = name; this.note = note; this.on = on;
            art = (pen, w, h) -> CardArt.choice(pen, w, h, name, note, icon, CardSheet.pictured(screen), picture, layout.aspect, this.on, enabled, sheet.look);
            setEnabled(enabled);
            describe();
        }
        void describe() { setContentDescription(name + (note == null ? "" : ". " + note) + (on ? ", in use" : "")); }
    }

    /** Template Visibility's chips: a tap on one sets the step at once and stays on the screen. */
    @SuppressLint("ViewConstructor")
    private final class Chips extends CanvasPen.ArtView {
        int at;
        Chips(int at) {
            super(app, null);
            this.at = at;
            art = (pen, w, h) -> CardArt.chips(pen, w, CardSheet.GUIDES_TITLE, NotebookOptions.GUIDE_STEPS, this.at, CardSheet.ABOUT_SP);
            describe();
        }
        void describe() { setContentDescription(CardSheet.GUIDES_TITLE + ": " + NotebookOptions.GUIDE_STEPS[at]); }
        @SuppressLint("ClickableViewAccessibility")
        @Override public boolean onTouchEvent(MotionEvent event) {
            if (event.getActionMasked() != MotionEvent.ACTION_UP) return true;
            DrawingBook book = app.book;
            int step = CardArt.chipAt(pen().dp(event.getX()), pen().dp(getWidth()), pen().dp(event.getY()), CardSheet.ABOUT_SP, NotebookOptions.GUIDE_STEPS.length);
            if (book == null || app.busy() || step < 0 || step == at) return true;
            app.setNotebookOptions(book.options().withGuidesShown(step));
            at = book.options().guidesShown; describe(); invalidate();
            return true;
        }
    }

    /** A slider's view: dragged or tapped to a stop, its value big over it as it moves. */
    @SuppressLint("ViewConstructor")
    private final class Slider extends CanvasPen.ArtView {
        final CardSheet.Screen screen;
        final CardArt.Slider look;
        int at;
        Slider(CardSheet.Screen screen, CardArt.Slider look, int at, String head) {
            super(app, null);
            this.screen = screen; this.look = look; this.at = at;
            art = (pen, w, h) -> CardArt.slider(pen, w, head, CardSheet.sliderWord(screen, this.at), this.at, CardSheet.stops(screen), CardSheet.marks(screen), CardSheet.markWords(screen), look);
            describe();
        }
        void describe() { setContentDescription((screen == CardSheet.Screen.TIMED ? NotebookRules.TIMED_PAGES : CardSheet.sliderHead(screen)) + ": " + CardSheet.sliderWord(screen, at)); }
        @SuppressLint("ClickableViewAccessibility")
        @Override public boolean onTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) getParent().requestDisallowInterceptTouchEvent(true);
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP) {
                int stop = CardArt.stopAt(pen().dp(event.getX()), pen().dp(getWidth()), CardSheet.stops(screen), look);
                if (stop != at) { at = stop; describe(); invalidate(); }
            }
            return true;
        }
    }
}
