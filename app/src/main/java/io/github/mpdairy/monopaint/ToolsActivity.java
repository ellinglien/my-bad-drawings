package io.github.mpdairy.monopaint;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The Tools page, the full-screen page the tool picker's Advanced opens (a long press on a tray row opens the picker) (spec
 * docs/archive/superpowers/specs/2026-10-15-tools-page-design.md; the owner's mockup, 2026-10-28, "Tools page v2"), all
 * in the app's face (Ukulele, {@link Theme#face}), on paper with no frames or grid lines.
 * The bar: <b>Back</b> (the caret and its word, always at the left), "Tools", Undo and <b>Brush Lab</b>, boxed. Then
 * the <b>scratch pad</b> (Brush Lab's test page, {@link TestArea}: the pen draws with the selected slot's brush at its size
 * and grey) in a rounded box, the brush's name (bold) and its one line in its foot, and in its corner small grey words:
 * Clear and the wet drop, and while the tool has them <b>Back to</b> the slot's first brush ({@link ToolsPage#restore}),
 * <b>Load Kit</b> and <b>Cut Stamp</b>; a change's note ("Kept Fineliner in Recent") at its other corner. Under it the
 * small grey label <b>Toolbar</b> over the ten slots, two rows of five {@link ToolCard}s (each its drawing as the side bar
 * shows it over its name; the selected one underlined and bold, a slot changed on this visit a dot). Then the Library:
 * the tabs (Tools, Recent, Saved, Kits) as words, the chosen one bold and underlined, the pages ("‹ 1 / 2 ›") at their
 * right; the chips ({@link ToolsPage.Chip}, the chosen one filled) under them; and one grid of {@link ToolCard}s, under
 * All the Tools tab's in groups a little apart. Stacked in portrait; in landscape the scratch pad and the slots are a
 * column at the left. Every size is {@link ToolsLayout}'s.
 * <p>
 * Tap a slot, then a card (the owner: "click a slot and then click an option from library"): the card's brush is the
 * slot's here (its card underlined, its slot showing it, the page staying to try it), its size following it; an unsaved
 * brush it replaces is kept in Recent, said in the scratch pad's corner ({@link ToolsPage#note}). Undo takes each back,
 * in any slot; Back takes each slot its tool. A long press on a saved card deletes it, asked in the chips' place. The
 * drawing screen makes the changes ({@link #BRUSHES}: an Undo step each there), selects the slot selected here, and
 * opens Brush Lab after when asked ({@link #LAB}); Brush Lab's Back comes here again, the slots' start carried through
 * ({@link #START}). Turned as the drawing is ({@link #TURN}). Everything it does is {@link ToolsPage}'s; nothing
 * scrolls, and only what changed is drawn again (e-ink).
 */
public final class ToolsActivity extends Activity {
    /**
     * Extras: the slot it opens on, and the drawing screen's turn in degrees; back, the slot selected, the brush to put
     * in each slot (as {@link Brush#write}; null: unchanged), and whether to open Brush Lab on the selected slot, or on a
     * new brush for it, or to cut the selected slot's Stamp a nib from the page (Cut Stamp, {@link StampNib}). Both ways,
     * the slots' start ({@link ToolSlots.Start#write}; in: null when opened from the drawing screen).
     */
    static final String SLOT = "slot", TURN = "turn", BRUSHES = "brushes", LAB = "lab", CUT = "cut", START = "start";
    /** In: when the drawing screen opened it ({@link SystemClock#uptimeMillis}), for the log's "Tools page shown in N ms". */
    static final String OPENED = "opened";
    /**
     * In: the note when the notebook's toolbar can't change ({@link NotebookRules#toolsFixed}; null: it can), and the
     * library card's screen that frees it ({@link NotebookRules#toolsSettings}, a {@link CardSheet.Screen} name; null:
     * none, sealed). With a note the bar says it in place of "Tools", the library's cards are greyed and take no tap, New
     * Brush and Undo go, and <b>Notebook Settings</b> takes the place of Undo. Back, SETTINGS says to open that screen.
     */
    static final String FIXED = "fixed", SETTINGS = "settings";
    /**
     * Tool kits ({@link ToolKits}). In: how many slots the bar shows ({@link NotebookOptions#slotsShown}) and whether the
     * notebook's rules are locked (a kit can't then show more). Back: the count a kit raised it to (-1: unchanged), the
     * eraser's kind a kit set (-1: unchanged), and whether the slots' changes are one Undo step there (a kit was loaded).
     */
    static final String BAR = "bar", ERASER = "eraser", TOGETHER = "together";
    /** The note, or null, and the screen Notebook Settings opens, or null. */
    private String fixed, settings;

    private ToolsPage page;
    private ToolsLayout layout;
    private SampleQueue samples;
    private FrameLayout root;
    /** When the page was opened, until its first frame is drawn (then 0). */
    private long opened;
    /** The scratch pad, Brush Lab's test page, drawing with the tool as it stands. */
    private TestArea test;
    private LabButton undo;
    /** The ten slots. */
    private final ToolCard[] slotCards = new ToolCard[ToolSlots.COUNT];
    /** The scratch pad's words: the tool's name and line in its foot, a change's note; and what they show, to draw them again only when that changes. */
    private TextView toolName, toolLine, note;
    private String toolShown;
    /** In the scratch pad's corner: Back to (the slot's first brush), Cut Stamp (a Stamp's) and Load Kit (a kit's brush). */
    private LabButton backTo, cut, kitButton;
    /** The kit sheet over the page while open, else null. */
    private View sheet;
    // The tabs, the pages, the chips (or a delete's question in their place), the grid and its cards, and what they show.
    private final TextView[] tabs = new TextView[ToolsPage.Tab.values().length];
    private LinearLayout pager, question;
    private LabUi.Wrap chips;
    private TextView empty;
    private LinearLayout grid;
    private String chipsShown, gridShown, pagesShown;
    private List<ToolsPage.Card> shown = Collections.emptyList();
    private ToolCard[] cards = new ToolCard[0];
    /** The saved card whose delete waits for an answer in the chips' place, or null. */
    private ToolsPage.Card confirming;
    /** The search's line in the chips' place (its field and ×), and whether it is open. */
    private LinearLayout searchRow;
    private EditText searchField;
    private boolean searchOpen;
    /** The grid follows the typing once per pause, not each letter (e-ink). */
    static final int SEARCH_MS = 250;
    private final Runnable searchNow = () -> { page.search(searchField.getText().toString()); render(); };

    @Override protected void attachBaseContext(Context base) { super.attachBaseContext(Ui.largerText(base)); }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Assets.install(getApplicationContext().getAssets()::open);
        Theme.fonts(this);
        SharedPreferences stored = getSharedPreferences(PaintPreferences.FILE, MODE_PRIVATE);
        int slot = Math.max(0, Math.min(ToolSlots.COUNT - 1, getIntent().getIntExtra(SLOT, 0)));
        fixed = getIntent().getStringExtra(FIXED);
        settings = fixed == null ? null : getIntent().getStringExtra(SETTINGS);
        BrushLibrary.Store store = InkWash.store(stored);
        page = new ToolsPage(new ToolSlots(store, new Random()), store, slot, getIntent().getStringArrayExtra(START));
        page.bar(getIntent().getIntExtra(BAR, ToolSlots.COUNT));
        samples = new SampleQueue();
        test = new TestArea(this, samples, new TestArea.Host() {
            @Override public Brush brush() { return page.current(); }
            @Override public ToolSwatches.Look look() { return page.look(); }
            @Override public ToolSwatches swatches() { return page.slots.swatches; }
        });
        root = new FrameLayout(this);
        root.setBackgroundColor(Theme.PAPER);
        opened = getIntent().getLongExtra(OPENED, SystemClock.uptimeMillis());
        // Laid out for the screen before a frame is drawn: no blank page first on the e-ink panel.
        root.getViewTreeObserver().addOnPreDrawListener(() -> {
            if (layout == null || layout.width != dpOf(root.getWidth()) || layout.height != dpOf(root.getHeight())) return !build();
            if (opened > 0) { long since = opened; root.post(() -> Log.i(ProbeActivity.TAG, "Tools page shown in " + (SystemClock.uptimeMillis() - since) + " ms")); opened = 0; }
            return true;
        });
        // Turned as the drawing screen is, while Android stays in portrait.
        QuarterTurnLayout turned = new QuarterTurnLayout(this);
        turned.setTurn(getIntent().getIntExtra(TURN, 0));
        turned.addView(root);
        turned.setBackgroundColor(Theme.PAPER);
        setContentView(turned);
    }

    /**
     * Lays the page out for the screen: the bar, then the scratch pad and the slots, then the Library (portrait: stacked;
     * landscape: side by side). The scratch pad keeps what is drawn on it. Whether it did (not with no size yet).
     */
    private boolean build() {
        int width = dpOf(root.getWidth()), height = dpOf(root.getHeight());
        if (width <= 0 || height <= 0 || isFinishing()) return false;
        layout = new ToolsLayout(width, height);
        page.layout(layout.perPage(), layout.columns, layout.cardWidth);
        test.pad.disconnect();
        root.removeAllViews();
        sheet = null;
        toolShown = null; chipsShown = null; gridShown = null; pagesShown = null;
        LinearLayout column = LabUi.column(this);
        column.addView(bar(), new LinearLayout.LayoutParams(-1, dp(ToolsLayout.BAR)));
        LinearLayout main = layout.wide ? LabUi.row(this) : LabUi.column(this);
        main.setGravity(Gravity.TOP);
        int side = dp(layout.side);
        main.setPadding(side, 0, side, side);
        main.addView(tools(), layout.wide ? new LinearLayout.LayoutParams(dp(layout.top), -1) : new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams place = layout.wide ? new LinearLayout.LayoutParams(0, -1, 1) : new LinearLayout.LayoutParams(-1, 0, 1);
        if (layout.wide) place.leftMargin = dp(layout.apart); else place.topMargin = dp(layout.apart);
        main.addView(library(), place);
        column.addView(main, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(column, new FrameLayout.LayoutParams(-1, -1));
        render();
        // The pad may keep its size and place, so nothing else reconnects it after the disconnect above.
        test.pad.post(test.pad::connect);
        return true;
    }
    /** The scratch pad, then the label Toolbar over the ten slots. */
    private View tools() {
        LinearLayout box = LabUi.column(this);
        LinearLayout.LayoutParams padPlace = new LinearLayout.LayoutParams(-1, dp(layout.testHeight));
        padPlace.topMargin = dp(layout.gap);
        box.addView(pad(), padPlace);
        FrameLayout head = new FrameLayout(this);
        TextView label = small(ToolsLayout.TOOLBAR);
        label.setGravity(Gravity.BOTTOM | Gravity.START);
        label.setPadding(0, 0, 0, dp(layout.gap / 2f));
        head.addView(label, new FrameLayout.LayoutParams(-1, -1));
        // Tools First at its right, a chip filled while on (the picker's switch too): a slot's long press opens this page.
        PaintPreferences prefs = new PaintPreferences(getSharedPreferences(PaintPreferences.FILE, MODE_PRIVATE));
        TextView first = chip(DrawLayout.TOOLS_FIRST, prefs.toolsFirst());
        first.setOnClickListener(v -> {
            boolean on = !prefs.toolsFirst();
            prefs.setToolsFirst(on);
            chip(first, on);
        });
        FrameLayout.LayoutParams firstPlace = new FrameLayout.LayoutParams(-2, dp(ToolsLayout.CHIP), Gravity.BOTTOM | Gravity.END);
        firstPlace.bottomMargin = dp(layout.switchFoot());
        head.addView(first, firstPlace);
        box.addView(head, new LinearLayout.LayoutParams(-1, dp(layout.label)));
        LinearLayout slots = LabUi.column(this);
        for (int r = 0; r < ToolsLayout.SLOT_ROWS; r++) {
            LinearLayout row = LabUi.row(this);
            for (int c = 0; c < layout.slotColumns; c++) {
                int slot = r * layout.slotColumns + c;
                ToolCard card = new ToolCard(this, samples);
                card.setOnClickListener(v -> { test.pad.finishStroke(); page.select(slot); render(); });
                slotCards[slot] = card;
                LinearLayout.LayoutParams cell = new LinearLayout.LayoutParams(0, -1, 1);
                if (c > 0) cell.leftMargin = dp(layout.gap);
                row.addView(card, cell);
            }
            LinearLayout.LayoutParams rowPlace = new LinearLayout.LayoutParams(-1, dp(layout.slotCard));
            if (r > 0) rowPlace.topMargin = dp(layout.gap);
            slots.addView(row, rowPlace);
        }
        box.addView(slots, new LinearLayout.LayoutParams(-1, dp(layout.slots)));
        return box;
    }
    /** Slot {@code i}'s tile as the page stands: its picture (kept by its brush, its size and the art box) and its name. */
    static void showSlot(ToolCard card, ToolsLayout layout, ToolsPage page, int i) {
        Brush brush = page.current(i);
        ToolSwatches.Look look = page.look(i);
        card.setArt(layout.slotArt(), layout.slotArtHeight());
        card.show(brush.identity() + " tray " + look.px, brush, LabLibrary.traySampler(brush, look), ToolsPage.name(brush));
    }
    /** A Library card's tile: its picture (kept by its brush and the art box) and its name. */
    static void showCard(ToolCard view, ToolsLayout layout, ToolsPage.Card card) {
        view.setArt(layout.cardArt(), layout.cardArtHeight());
        view.show(card.brush.identity() + " tray", card.brush, LabLibrary.traySampler(card.brush, null), card.name);
    }
    /**
     * Draws ahead, on {@code samples}' thread, the pictures the page would open with on slot {@code slot} on a {@code width
     * × height} dp screen: the ten slots and the first page of the tab and chip last chosen, kept for it ({@link ToolCard});
     * those kept already cost nothing. The owner, 2026-10-28: slow "just on the first load", because of the pictures.
     */
    static void warm(Context context, SampleQueue samples, ToolSlots slots, BrushLibrary.Store store, int slot, int width, int height) {
        ToolsLayout layout = new ToolsLayout(width, height);
        ToolsPage page = new ToolsPage(slots, store, Math.max(0, Math.min(ToolSlots.COUNT - 1, slot)));
        page.layout(layout.perPage(), layout.columns, layout.cardWidth);
        for (int i = 0; i < layout.slotCount; i++) showSlot(new ToolCard(context, samples), layout, page, i);
        for (ToolsPage.Card card : page.cards()) if (card != null) showCard(new ToolCard(context, samples), layout, card);
    }
    /** Small grey words ({@link TypeStyle#CAPTION}, grey-800, one line): the Toolbar label, the pages' count, a note. */
    private TextView small(String words) {
        TextView view = LabUi.text(this, words, TypeStyle.CAPTION);
        view.setTextColor(Theme.GREY_800);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        return view;
    }

    /** Shows the page as it now stands, drawing again only what changed. */
    private void render() {
        if (layout == null) return;
        int selected = page.slot();
        for (int i = 0; i < layout.slotCount; i++) {
            Brush brush = page.current(i);
            ToolCard card = slotCards[i];
            showSlot(card, layout, page, i);
            card.setNow(i == selected);
            card.setChosen(i == selected);
            card.setChanged(page.changed(i));
            card.setContentDescription(brush.name() + (i == selected ? ", selected" : "") + (page.changed(i) ? ", changed" : ""));
        }
        renderTool();
        if (undo.isEnabled() != page.canUndo()) undo.setEnabled(page.canUndo());
        for (ToolsPage.Tab t : ToolsPage.Tab.values()) {
            // None chosen while a search shows every tab's finds.
            boolean on = t == page.tab() && !page.searching();
            tabs[t.ordinal()].setTypeface(Theme.face(on ? 700 : 400));
            tabs[t.ordinal()].setBackground(on ? Theme.underlined(this) : null);
            tabs[t.ordinal()].setContentDescription(t.label + (on ? ", chosen" : ""));
        }
        renderChips();
        renderPages();
        renderGrid();
    }

    // The bar

    /** Back (the caret and its word, first at the left), "Tools" (bold), then Undo (plain words; its icon only on the Nomad) and Brush Lab, boxed. */
    private View bar() {
        LinearLayout bar = LabUi.row(this);
        bar.setPadding(dp(layout.side), 0, dp(layout.side), 0);
        // The owner: "back button should be clearly visible.. sometimes it gets lost".
        LabButton back = LabButton.back(this);
        back.setOnClickListener(v -> leave(false, false));
        bar.addView(back, new LinearLayout.LayoutParams(-2, dp(44)));
        // A fixed toolbar: its note in the title's place, so the page never seems to take a pick it won't keep.
        TextView title = LabUi.text(this, fixed != null ? fixed : ToolsLayout.TITLE, fixed != null ? TypeStyle.BODY : TypeStyle.HEADING);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setGravity(Gravity.CENTER_VERTICAL);
        if (fixed != null) { LinearLayout.LayoutParams rest = new LinearLayout.LayoutParams(0, dp(44), 1); rest.leftMargin = dp(10); bar.addView(title, rest); }
        else { bar.addView(title, LabUi.after(this, -2, 44, 10)); LabUi.spring(bar); }
        if (settings != null) {
            LabButton open = new LabButton(this, 0, NotebookRules.NOTEBOOK_SETTINGS, false);
            open.setOnClickListener(v -> leave(false, false, true));
            bar.addView(open, LabUi.after(this, -2, 40, 10));
        }
        // Greyed with nothing to undo, as Brush Lab's.
        undo = new LabButton(this, R.drawable.ic_ph_undo, layout.compact ? null : ToolsLayout.UNDO, false);
        undo.setContentDescription("Undo");
        undo.setEnabled(page.canUndo());
        undo.setOnClickListener(v -> { test.pad.finishStroke(); closeKit(); page.undo(); render(); });
        // Nothing to undo where nothing is picked.
        if (fixed == null) bar.addView(undo, LabUi.after(this, layout.compact ? dp(44) : -2, 40, 10));
        // Brush Lab on the slot's brush, boxed (the owner's mockup; Tweak in Brush Lab, ink-filled, until 2026-10-28).
        LabButton lab = new LabButton(this, R.drawable.ic_ph_flask, ToolsLayout.LAB, false);
        lab.setBackground(Theme.rounded(this, Theme.PAPER, Theme.BASE, Theme.INK, ToolsLayout.PAD_RADIUS));
        lab.setOnClickListener(v -> leave(true, false));
        bar.addView(lab, LabUi.after(this, -2, 40, 10));
        return bar;
    }

    // The scratch pad

    /**
     * The scratch pad: the test page in a rounded {@link Theme#BASE} dp ink box; in its corner small grey words, Back to,
     * Load Kit and Cut Stamp while the tool has them, Clear, and the wet drop; a change's note at its other corner; the
     * tool's name (bold) and its line in its foot.
     */
    private View pad() {
        FrameLayout box = new FrameLayout(this);
        box.setBackground(Theme.rounded(this, Theme.PAPER, Theme.BASE, Theme.INK, ToolsLayout.PAD_RADIUS));
        // Inside the rounded corners: the page's own corners stay off the edge.
        int inset = dp(Theme.BASE + 1);
        box.setPadding(inset, inset, inset, inset);
        Ui.detach(test.pad);
        box.addView(test.pad, new FrameLayout.LayoutParams(-1, -1));
        int gap = dp(layout.gap);
        // Across the top: the note in what the corner's words leave, then the corner's words.
        LinearLayout corner = LabUi.row(this);
        note = small("");
        note.setGravity(Gravity.CENTER_VERTICAL);
        corner.addView(note, new LinearLayout.LayoutParams(0, -1, 1));
        backTo = new LabButton(this, 0, "", false).quiet();
        backTo.setEllipsize(TextUtils.TruncateAt.END);
        backTo.setMaxWidth(dp(layout.backToWidth()));
        backTo.setOnClickListener(v -> { test.pad.finishStroke(); if (page.restore()) { sayNote(); render(); } });
        kitButton = new LabButton(this, 0, ToolKits.LOAD, false).quiet();
        kitButton.setOnClickListener(v -> { ToolKits.Kit k = page.kit(); if (k != null) openKit(k); });
        cut = new LabButton(this, 0, StampNib.CUT, false).quiet();
        cut.setOnClickListener(v -> leave(false, true));
        for (View v : new View[]{backTo, kitButton, cut, test.clear(8).quiet()}) corner.addView(v, new LinearLayout.LayoutParams(-2, -1));
        corner.addView(test.wet(), new LinearLayout.LayoutParams(dp(layout.wet()), -1));
        FrameLayout.LayoutParams cornerPlace = new FrameLayout.LayoutParams(-1, dp(ToolsLayout.PAD_ROW), Gravity.TOP);
        cornerPlace.leftMargin = gap;
        cornerPlace.rightMargin = gap / 2;
        box.addView(corner, cornerPlace);
        // The tool: its name bold, then its one line, grey (the owner's mockup: "Steady line · even and crisp").
        LinearLayout words = LabUi.row(this);
        words.setGravity(Gravity.BOTTOM);
        toolName = LabUi.text(this, "", TypeStyle.CAPTION);
        toolName.setTypeface(Theme.face(700));
        toolName.setSingleLine(true);
        toolName.setEllipsize(TextUtils.TruncateAt.END);
        toolName.setMaxWidth(dp(layout.padRoom() * 2 / 3f));
        words.addView(toolName, new LinearLayout.LayoutParams(-2, -2));
        toolLine = small("");
        words.addView(toolLine, new LinearLayout.LayoutParams(0, -2, 1));
        FrameLayout.LayoutParams wordsPlace = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM | Gravity.START);
        wordsPlace.setMargins(gap, 0, gap, gap);
        box.addView(words, wordsPlace);
        return box;
    }
    private final Runnable noteGone = () -> note.setText("");
    /** The note the page's last change left, if any, for {@link DrawLayout#NOTE_MS}; no animation. */
    private void sayNote() {
        String said = page.note();
        if (said == null) return;
        note.setText(said);
        root.removeCallbacks(noteGone);
        root.postDelayed(noteGone, DrawLayout.NOTE_MS);
        root.announceForAccessibility(said);
    }
    /** The scratch pad's words: the tool's name and line, drawn again only when the tool changed; its corner's words as the brush has them. */
    private void renderTool() {
        Brush brush = page.current();
        int cuts = brush.parts.stamps() ? View.VISIBLE : View.GONE;
        if (cut.getVisibility() != cuts) cut.setVisibility(cuts);
        // The owner, 2026-10-22: "maybe sumi tools load several tools in your toolbar". Shown while the brush is in a kit.
        ToolKits.Kit kit = page.kit();
        int kits = kit != null ? View.VISIBLE : View.GONE;
        if (kitButton.getVisibility() != kits) kitButton.setVisibility(kits);
        if (kit != null) kitButton.setContentDescription("Load the " + kit.name + " kit");
        // Back to the slot's first brush, once it holds another (the owner: "an easy way").
        int back = page.changed(page.slot()) && fixed == null ? View.VISIBLE : View.GONE;
        String backSaid = ToolsLayout.BACK_TO + page.started().name();
        if (!backSaid.contentEquals(backTo.getText())) { backTo.setText(backSaid); backTo.setContentDescription(backSaid); }
        if (backTo.getVisibility() != back) backTo.setVisibility(back);
        if (brush.identity().equals(toolShown)) return;
        toolShown = brush.identity();
        ToolsPage.Card card = ToolsPage.card(brush, layout.padRoom() / 2f);
        toolName.setText(card.name);
        // Its line, unless it only says the name again (a style's own brush: "Fineliner", "Fineliner").
        boolean line = !card.line.isEmpty() && !card.line.equalsIgnoreCase(card.name);
        toolLine.setText(line ? " · " + card.line : "");
        toolName.setContentDescription(card.name + ": " + BrushVoice.about(brush.parts));
    }

    // Library

    /** The Library: the tabs with the pages at their right, the chips (or a delete's question), and the grid. */
    private View library() {
        LinearLayout box = LabUi.column(this);
        LinearLayout head = LabUi.row(this);
        for (ToolsPage.Tab t : ToolsPage.Tab.values()) {
            TextView tab = LabUi.text(this, t.label, TypeStyle.BODY);
            tab.setSingleLine(true);
            tab.setGravity(Gravity.CENTER);
            tab.setOnClickListener(v -> { test.pad.finishStroke(); confirming = null; closeSearch(); page.tab(t); render(); });
            tabs[t.ordinal()] = tab;
            head.addView(tab, LabUi.after(this, -2, ToolsLayout.TAB - 6, t.ordinal() == 0 ? 0 : layout.tabGap()));
        }
        LabUi.spring(head);
        LabButton find = LabButton.headIcon(this, R.drawable.ic_ph_magnifying_glass, "Search");
        find.setOnClickListener(v -> openSearch());
        head.addView(find, new LinearLayout.LayoutParams(dp(ToolsLayout.PAGE_BUTTON), -1));
        pager = LabUi.row(this);
        head.addView(pager, new LinearLayout.LayoutParams(-2, -1));
        box.addView(head, new LinearLayout.LayoutParams(-1, dp(ToolsLayout.TAB)));
        FrameLayout chipsBox = new FrameLayout(this);
        chips = new LabUi.Wrap(this, dp(layout.chipGap()));
        chipsBox.addView(chips, new FrameLayout.LayoutParams(-1, -2));
        question = LabUi.row(this);
        chipsBox.addView(question, new FrameLayout.LayoutParams(-1, dp(ToolsLayout.CHIP)));
        // The search: one line in a chip's rounded edge, then × to close it (the chips again).
        searchRow = LabUi.row(this);
        searchField = Theme.style(new EditText(this), TypeStyle.CAPTION);
        searchField.setSingleLine(true);
        searchField.setHint("Search");
        searchField.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        // The keyboard's Search key: the finds now, the keyboard down to see the grid.
        searchField.setOnEditorActionListener((v, action, event) -> { root.removeCallbacks(searchNow); searchNow.run(); keyboard(false); return true; });
        searchField.setPadding(dp(layout.chipPad()), 0, dp(layout.chipPad()), 0);
        searchField.setBackground(Theme.rounded(this, Theme.PAPER, Theme.FINE, Theme.INK, ToolsLayout.CHIP / 2f));
        // Kept through a new layout (the screen turned) while open; cleared on closing.
        searchField.setText(page.search());
        searchField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { root.removeCallbacks(searchNow); root.postDelayed(searchNow, SEARCH_MS); }
        });
        searchRow.addView(searchField, new LinearLayout.LayoutParams(0, -1, 1));
        LabButton close = LabButton.icon(this, R.drawable.ic_ph_x, "Close search");
        close.setOnClickListener(v -> { closeSearch(); render(); });
        searchRow.addView(close, LabUi.after(this, ToolsLayout.CHIP, ToolsLayout.CHIP, 6));
        chipsBox.addView(searchRow, new FrameLayout.LayoutParams(-1, dp(ToolsLayout.CHIP)));
        box.addView(chipsBox, new LinearLayout.LayoutParams(-1, dp(layout.chips())));
        FrameLayout gridBox = new FrameLayout(this);
        grid = LabUi.column(this);
        gridBox.addView(grid, new FrameLayout.LayoutParams(-1, -2));
        // Greyed while the toolbar is fixed: to look through, not to pick from (a kit's sheet still opens, to read).
        if (fixed != null) grid.setAlpha(.4f);
        empty = LabUi.text(this, "", TypeStyle.BODY);
        empty.setTextColor(Theme.GREY_800);
        empty.setGravity(Gravity.CENTER);
        gridBox.addView(empty, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams gridPlace = new LinearLayout.LayoutParams(-1, 0, 1);
        gridPlace.topMargin = dp(layout.gap);
        // The grid takes what is left, so a chip row more than budgeted squeezes it, never the page.
        box.addView(gridBox, gridPlace);
        return box;
    }
    /**
     * The chips the open tab has ({@link ToolsPage#chipsShown}; none under Kits), rounded, the chosen one filled; or,
     * while a saved card's delete waits, "Delete Name?", Delete and Keep in their place. Drawn again only when that changes.
     */
    private void renderChips() {
        List<ToolsPage.Chip> now = page.chipsShown();
        String key = confirming != null ? "delete " + confirming.brush.identity() : searchOpen ? "search" : page.tab() + " " + page.filter() + " " + now;
        if (key.equals(chipsShown)) return;
        chipsShown = key;
        chips.removeAllViews();
        question.removeAllViews();
        chips.setVisibility(confirming == null && !searchOpen ? View.VISIBLE : View.GONE);
        question.setVisibility(confirming == null ? View.GONE : View.VISIBLE);
        searchRow.setVisibility(confirming == null && searchOpen ? View.VISIBLE : View.GONE);
        if (searchOpen) return;
        if (confirming != null) {
            TextView asked = LabUi.text(this, "Delete " + confirming.name + "?", TypeStyle.BODY);
            asked.setTypeface(Theme.face(700));
            asked.setSingleLine(true); asked.setEllipsize(TextUtils.TruncateAt.END);
            question.addView(asked, new LinearLayout.LayoutParams(0, -2, 1));
            LabButton delete = new LabButton(this, R.drawable.ic_ph_trash, "Delete", false);
            delete.setMinHeight(0); delete.setMinimumHeight(0);
            delete.setOnClickListener(v -> { ToolsPage.Card card = confirming; confirming = null; page.delete(card); render(); });
            question.addView(delete, LabUi.after(this, -2, ToolsLayout.CHIP, 10));
            LabButton keep = new LabButton(this, 0, "Keep", false);
            keep.setMinHeight(0); keep.setMinimumHeight(0);
            keep.setOnClickListener(v -> { confirming = null; render(); });
            question.addView(keep, LabUi.after(this, -2, ToolsLayout.CHIP, 6));
            return;
        }
        for (ToolsPage.Chip chip : now) {
            boolean on = chip == page.filter();
            TextView view = chip(chip.label, on);
            view.setContentDescription(chip.label + (on ? ", chosen" : ""));
            view.setOnClickListener(v -> { test.pad.finishStroke(); confirming = null; page.filter(chip); render(); });
            chips.addView(view);
        }
    }
    /** A chip: {@code label} (13 sp) in a rounded fine ink edge, filled while {@code on} (the chips, Tools First). */
    private TextView chip(String label, boolean on) {
        TextView view = LabUi.text(this, label, TypeStyle.CAPTION);
        view.setSingleLine(true);
        view.setGravity(Gravity.CENTER);
        int pad = dp(layout.chipPad());
        view.setPadding(pad, 0, pad, 0);
        view.setHeight(dp(ToolsLayout.CHIP));
        return chip(view, on);
    }
    /** {@code view} as a chip on or off; said "on" or "off" (Tools First's, a switch). */
    private TextView chip(TextView view, boolean on) {
        view.setTextColor(on ? Theme.ON_INK : Theme.INK);
        view.setBackground(Theme.rounded(this, on ? Theme.INK : Theme.PAPER, Theme.FINE, Theme.INK, ToolsLayout.CHIP / 2f));
        view.setContentDescription(view.getText() + (on ? ", on" : ", off"));
        return view;
    }
    /** At the tabs' right: "‹ 2 / 5 ›" (nothing while there is one page), drawn again only when that changes. */
    private void renderPages() {
        int pages = page.pages(), at = page.page();
        String key = pages > 1 ? (at + 1) + " / " + pages : "";
        if (key.equals(pagesShown)) return;
        pagesShown = key;
        pager.removeAllViews();
        if (pages <= 1) return;
        LabButton previous = LabButton.headIcon(this, R.drawable.ic_ph_caret_left, "Previous page");
        previous.setEnabled(at > 0);
        previous.setOnClickListener(v -> { test.pad.finishStroke(); page.turn(-1); render(); });
        pager.addView(previous, new LinearLayout.LayoutParams(dp(ToolsLayout.PAGE_BUTTON), -1));
        TextView count = small(key);
        count.setGravity(Gravity.CENTER);
        count.setPadding(dp(4), 0, dp(4), 0);
        count.setContentDescription("Page " + (at + 1) + " of " + pages);
        pager.addView(count, new LinearLayout.LayoutParams(-2, -1));
        LabButton next = LabButton.headIcon(this, R.drawable.ic_ph_caret_right, "Next page");
        next.setEnabled(at < pages - 1);
        next.setOnClickListener(v -> { test.pad.finishStroke(); page.turn(1); render(); });
        pager.addView(next, new LinearLayout.LayoutParams(dp(ToolsLayout.PAGE_BUTTON), -1));
    }
    /** The page of cards, row by row, a group's first row a little apart; or, with none, a short line. */
    private void renderGrid() {
        List<ToolsPage.Card> now = page.cards();
        StringBuilder key = new StringBuilder();
        for (ToolsPage.Card c : now) key.append(c == null ? "-" : c.brush.identity()).append('\n');
        if (!key.toString().equals(gridShown)) {
            gridShown = key.toString();
            shown = now;
            grid.removeAllViews();
            cards = new ToolCard[shown.size()];
            boolean none = shown.isEmpty();
            grid.setVisibility(none ? View.INVISIBLE : View.VISIBLE);
            empty.setVisibility(none ? View.VISIBLE : View.GONE);
            empty.setText(none ? page.empty() : "");
            if (!none) fillGrid();
        }
        for (int c = 0; c < shown.size(); c++) if (cards[c] != null) cards[c].setNow(page.now(shown.get(c)));
    }
    private void fillGrid() {
        int columns = layout.columns;
        for (int r = 0; r * columns < shown.size(); r++) {
            LinearLayout row = LabUi.row(this);
            for (int c = 0; c < columns; c++) {
                int index = r * columns + c;
                ToolsPage.Card card = index < shown.size() ? shown.get(index) : null;
                View cell;
                if (card != null) {
                    ToolCard view = new ToolCard(this, samples);
                    showCard(view, layout, card);
                    view.describe(() -> card.kit != null ? card.kit.title() + ": " + card.kit.intro : card.name + ": " + BrushVoice.about(card.brush.parts));
                    // A kit's card opens its sheet, which says why it won't load in a fixed toolbar.
                    if (card.kit != null) view.setOnClickListener(v -> openKit(card.kit));
                    else if (fixed == null) view.setOnClickListener(v -> pick(card.brush));
                    if (fixed == null && page.deletable(card)) view.setOnLongClickListener(v -> { confirming = card; render(); return true; });
                    cards[index] = view;
                    cell = view;
                } else cell = new View(this);
                row.addView(cell, new LinearLayout.LayoutParams(0, -1, 1));
            }
            LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(-1, dp(layout.cardHeight));
            if (r > 0) place.topMargin = dp(ToolsLayout.ROW_GAP + (page.opens(shown, r * columns) ? ToolsLayout.GROUP_GAP : 0));
            grid.addView(row, place);
        }
    }

    // Kits

    /**
     * The kit's sheet over the page (the owner, 2026-10-22: "yes to kits"): its name, its intro, its steps in order
     * ("1 Drop ink", the Basic tool's name beside it), where it goes ("Fills the first 3 slots"), then Cancel and Load
     * Kit; in a fixed toolbar the page's note in Load Kit's place, and on a locked bar too small for it the bar's note.
     * A tap outside closes it. The test page stops taking the pen while it is open.
     */
    private void openKit(ToolKits.Kit kit) {
        test.pad.finishStroke();
        closeKit();
        test.pad.disconnect();
        FrameLayout scrim = new FrameLayout(this);
        scrim.setClickable(true);
        scrim.setOnClickListener(v -> closeKit());
        LinearLayout box = LabUi.column(this);
        // Over the page, so a menu's look: paper, a fine ink edge, no grey.
        box.setBackground(Theme.menu(this));
        box.setClickable(true);
        int pad = dp(ToolsLayout.SHEET_PAD);
        box.setPadding(pad, pad, pad, pad);
        TextView title = LabUi.text(this, kit.title(), TypeStyle.HEADING);
        box.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView intro = LabUi.text(this, kit.intro, TypeStyle.BODY);
        LinearLayout.LayoutParams introPlace = new LinearLayout.LayoutParams(-1, -2);
        introPlace.topMargin = dp(ToolsLayout.WORDS_GAP);
        box.addView(intro, introPlace);
        for (int i = 0; i < kit.steps(); i++) {
            LinearLayout row = LabUi.row(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView job = LabUi.text(this, kit.said(i), TypeStyle.LABEL);
            job.setSingleLine(true);
            row.addView(job, new LinearLayout.LayoutParams(0, -2, 1));
            TextView tool = LabUi.text(this, kit.tool(i), TypeStyle.CAPTION);
            tool.setTextColor(Theme.GREY_800);
            tool.setSingleLine(true);
            row.addView(tool, new LinearLayout.LayoutParams(-2, -2));
            row.setContentDescription(kit.said(i) + ", " + kit.tool(i));
            LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(-1, dp(ToolsLayout.SHEET_ROW));
            if (i == 0) place.topMargin = dp(layout.gap);
            box.addView(row, place);
        }
        String refused = fixed;
        TextView where = LabUi.text(this, refused != null ? refused : ToolsLayout.kitWhere(kit.size(), page.raises(kit)), TypeStyle.CAPTION);
        where.setTextColor(refused != null ? Theme.INK : Theme.GREY_800);
        LinearLayout.LayoutParams wherePlace = new LinearLayout.LayoutParams(-1, -2);
        wherePlace.topMargin = dp(layout.gap);
        box.addView(where, wherePlace);
        LinearLayout buttons = LabUi.row(this);
        LabUi.spring(buttons);
        LabButton cancel = new LabButton(this, 0, ToolKits.CANCEL, false);
        cancel.setOnClickListener(v -> closeKit());
        buttons.addView(cancel, LabUi.after(this, -2, 40, 0));
        if (refused == null) {
            LabButton load = new LabButton(this, 0, ToolKits.LOAD, true);
            load.setContentDescription("Load the " + kit.name + " kit");
            load.setOnClickListener(v -> { closeKit(); page.loadKit(kit); sayNote(); render(); });
            buttons.addView(load, LabUi.after(this, -2, 40, 10));
        }
        LinearLayout.LayoutParams buttonsPlace = new LinearLayout.LayoutParams(-1, -2);
        buttonsPlace.topMargin = dp(layout.gap);
        box.addView(buttons, buttonsPlace);
        scrim.addView(box, new FrameLayout.LayoutParams(dp(layout.sheetWidth()), -2, Gravity.CENTER));
        root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));
        sheet = scrim;
    }
    /** The kit sheet closed, if open; the test page takes the pen again. */
    private void closeKit() {
        if (sheet == null) return;
        root.removeView(sheet);
        sheet = null;
        test.pad.post(test.pad::connect);
    }

    // The search

    /** The search's line in the chips' place, the keyboard up for it. */
    private void openSearch() {
        test.pad.finishStroke();
        confirming = null;
        searchOpen = true;
        render();
        searchField.requestFocus();
        keyboard(true);
    }
    /** The search closed and cleared, the keyboard down: the open tab and its chips again. */
    private void closeSearch() {
        if (!searchOpen) return;
        searchOpen = false;
        keyboard(false);
        searchField.setText("");
        root.removeCallbacks(searchNow);
        page.search("");
    }
    private void keyboard(boolean up) {
        InputMethodManager keyboard = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (keyboard == null) return;
        if (up) keyboard.showSoftInput(searchField, InputMethodManager.SHOW_IMPLICIT);
        else keyboard.hideSoftInputFromWindow(searchField.getWindowToken(), 0);
    }

    // Leaving

    /** A card is the selected slot's tool now: underlined, its slot and the scratch pad showing it; the page stays. */
    private void pick(Brush brush) {
        test.pad.finishStroke();
        confirming = null;
        page.put(brush);
        sayNote();
        render();
    }
    /**
     * Back to the drawing with each slot's tool as it stands (null when unchanged) and the slot selected; then Brush Lab
     * on it if {@code lab}, or the page's next pen stroke Cut Stamp's lasso if {@code cut}.
     */
    private void leave(boolean lab, boolean cut) { leave(lab, cut, false); }
    /** As {@link #leave(boolean, boolean)}; {@code open}: to the library card's screen that frees the toolbar ({@link #SETTINGS}). */
    private void leave(boolean lab, boolean cut, boolean open) {
        test.pad.finishStroke();
        Brush[] results = page.results();
        String[] written = new String[results.length];
        for (int i = 0; i < results.length; i++) written[i] = results[i] == null ? null : results[i].write();
        setResult(RESULT_OK, new Intent().putExtra(SLOT, page.slot()).putExtra(BRUSHES, written).putExtra(LAB, lab).putExtra(CUT, cut).putExtra(START, page.start.write())
                .putExtra(SETTINGS, open ? settings : null).putExtra(BAR, page.barResult()).putExtra(ERASER, page.eraser()).putExtra(TOGETHER, page.together()));
        finish();
        overridePendingTransition(0, 0);
    }
    @Override public void onBackPressed() { if (sheet != null) closeKit(); else leave(false, false); }
    @Override protected void onResume() { super.onResume(); test.pad.opened(); if (sheet == null) test.pad.post(test.pad::connect); }
    @Override protected void onPause() { test.pad.disconnect(); super.onPause(); }
    @Override protected void onDestroy() { samples.close(); test.pad.close(); super.onDestroy(); }

    private int dp(float value) { return Ui.dp(this, value); }
    private int dpOf(int pixels) { return Ui.dpOf(this, pixels); }
}
