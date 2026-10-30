package io.github.mpdairy.monopaint;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import java.util.List;
import java.util.Random;

/**
 * Brush Lab, where brushes are made, tested, named, saved and shared (docs/archive/superpowers/specs/2026-10-15-lab-screen-design.md;
 * the design pass, docs/archive/design-pass-2026-10-23.md, "Brush Lab"). It works on the slot that was selected when it
 * opened (the owner, 2026-10-29: "whatever toolbar slot was selected at the beginning should flow through"; no slots
 * strip), on a copy of the toolbar's ten, and Back returns them. The bar: <b>Back</b> ("‹ Tools" when the Tools page
 * opened it, and it goes back there), "Brush Lab", Undo at the right. Then a band (paper in a fine ink frame, no grey:
 * e-ink): <b>About This Brush</b> (its header: the brush's actions, {@link #ACTIONS}: New, Open, Save, the one ink
 * button, Save as New, Back to the slot's first brush, Cut Stamp for a Stamp; its paper block: the loop, the name with
 * its pencil, the Edited mark and a suggested name, the words, the drawing screen's picker box lying across ({@link
 * PickerBox}), a note's line), then <b>Adjust</b> ({@link LabSettings}, always what Advanced was) beside <b>Scratch
 * Pad</b> (Clear, the wet drop, the test page that gets the room), and <b>Randomize</b> across the foot. Open's sheet
 * lies under the header: the library's brushes, or a pasted code; Cancel or Open again closes it. No status line: what
 * the lab did shows where it happened. Every size is {@link LabLayout}'s, from the screen, so nothing scrolls on the
 * Manta or the Nomad. Scribbler Style ({@link Theme}). Everything it does is {@link BrushLab}'s; this shows it and calls
 * it, drawing again only what changed. Portrait only, no animations (e-ink).
 */
public final class BrushLabActivity extends Activity {
    /**
     * Extras: the slot the lab opens on, and the one selected when it returns; the ten slots' brushes it returns,
     * each as {@link Brush#write} writes it; and, opened from the Tools page, the slots' start there ({@link
     * ToolSlots.Start#write}), returned so the Tools page opens again with it.
     */
    static final String SLOT = "slot", BRUSHES = "brushes", START = "start";
    /** The brush's actions in About This Brush's header, in order (the owner's New, Open and Save, 2026-10-29: kept in one list, so their order is one line). */
    enum Action { NEW, OPEN, SAVE, SAVE_AS_NEW, BACK_TO, CUT }
    static final Action[] ACTIONS = {Action.NEW, Action.OPEN, Action.SAVE, Action.SAVE_AS_NEW, Action.BACK_TO, Action.CUT};

    private BrushLab lab;
    /** Opened from the Tools page: its Back goes there ("‹ Tools"). */
    private boolean fromTools;
    /** The test area ({@link TestArea}, shared with the Tools page) and its page. */
    private TestArea test;
    private BrushTestPad pad;
    private SampleQueue samples;
    /** The slot's size and grey: the drawing screen's picker box, lying across About This Brush's right, and its readout beside it. */
    private PickerBox picker;
    private SwatchReadout readout;
    private FrameLayout root;
    /** Cleans what a closed menu or popup covered on the panel ({@link SelectionFeedback#cleanAfterClose}). */
    private final SelectionFeedback feedback = new SelectionFeedback();
    private LabLayout layout;
    /** The name (and its pencil, or the field while renaming), About This Brush's buttons, the Open sheet, its library grid. */
    private LinearLayout nameBox, actions, sheet, shelf;
    /** The name's size when it takes two lines (its heading size, 20 sp, on one). */
    static final float TWO_LINE_SP = 16;
    private TextView name, words, said, code, note, edited, suggested;
    private EditText paste;
    private LabButton undo;
    private boolean renaming, sheetOpen, building;
    private LabSettings settings;
    /**
     * The part whose choices are open in the swap panel beside its module (a popup, the owner's "modal"), or null; the
     * part open in the column, which stays open when the panel closes.
     */
    private BrushPart swapping, opened;
    private SwapStrip strip;
    private PopupWindow swapPopup;
    /** A choice dial's menu, open beside its button (LabSettings.Beside), or null. */
    private PopupWindow menuPopup;
    private View columnBox;
    /** The Open sheet's page of library brushes. */
    private int shelfPage;
    /** What the name, the buttons, the mark, the words, the column, the picker and the shelf show, to draw them again only when it changes. */
    private String nameShown, actionsShown, markShown, wordsShown, columnShown, pickerShown, shelfShown;

    @Override protected void attachBaseContext(Context base) { super.attachBaseContext(Ui.largerText(base)); }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Assets.install(getApplicationContext().getAssets()::open);
        Theme.fonts(this);
        SharedPreferences stored = getSharedPreferences(PaintPreferences.FILE, MODE_PRIVATE);
        int slot = Math.max(0, Math.min(ToolSlots.COUNT - 1, getIntent().getIntExtra(SLOT, 0)));
        String[] start = getIntent().getStringArrayExtra(START);
        fromTools = start != null;
        lab = new BrushLab(new ToolSlots(InkWash.store(stored), new Random()), slot, new Random(), start);
        samples = new SampleQueue();
        test = new TestArea(this, samples, new TestArea.Host() {
            @Override public Brush brush() { return lab.brush(); }
            @Override public ToolSwatches.Look look() { return BrushLabActivity.this.look(); }
            @Override public ToolSwatches swatches() { return lab.slots.swatches; }
        });
        pad = test.pad;
        root = new FrameLayout(this);
        root.setBackgroundColor(Theme.PAPER);
        // Every size follows the screen: the lab is laid out once it is known, and again if it changes.
        root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (!building && (layout == null || layout.width != dpOf(r - l) || layout.height != dpOf(b - t))) { building = true; root.post(this::build); }
        });
        setContentView(root);
    }

    /**
     * Lays the lab out anew for the screen: the bar, then the band (About This Brush, the column beside the scratch pad,
     * Randomize). The test page keeps what is drawn on it.
     */
    private void build() {
        building = false;
        int width = dpOf(root.getWidth()), height = dpOf(root.getHeight());
        if (width <= 0 || height <= 0 || isFinishing()) return;
        layout = new LabLayout(width, height);
        pad.disconnect();
        root.removeAllViews();
        LinearLayout frame = LabUi.column(this);
        frame.addView(bar(), new LinearLayout.LayoutParams(-1, dp(LabLayout.BAR)));
        FrameLayout main = new FrameLayout(this);
        LinearLayout rest = band(layout.top, layout.bottom);
        rest.addView(aboutHeader(), new LinearLayout.LayoutParams(-1, dp(layout.aboutHead)));
        rest.addView(about(), new LinearLayout.LayoutParams(-1, dp(layout.about)));
        LinearLayout row = LabUi.row(this);
        row.setGravity(Gravity.TOP);
        // The column: a frame, so the flyouts can lie over it.
        FrameLayout columnBox = new FrameLayout(this);
        row.addView(columnBox, new LinearLayout.LayoutParams(dp(layout.column), -1));
        this.columnBox = columnBox;
        LinearLayout right = LabUi.column(this);
        right.addView(testHeader(), new LinearLayout.LayoutParams(-1, dp(layout.headRow)));
        LinearLayout.LayoutParams testPlace = new LinearLayout.LayoutParams(-1, 0, 1); testPlace.topMargin = dp(layout.gap);
        right.addView(test.frame(layout.testWidth), testPlace);
        dropSwap();
        strip = new SwapStrip(this, lab, samples, layout, pad::finishStroke, this::closeSwap);
        LinearLayout.LayoutParams rightPlace = new LinearLayout.LayoutParams(0, -1, 1); rightPlace.leftMargin = dp(layout.split);
        row.addView(right, rightPlace);
        // A line between About This Brush and the rest, in the gap there was.
        LabUi.line(rest, dp(layout.side), dp(layout.gap));
        rest.addView(row, new LinearLayout.LayoutParams(-1, 0, 1));
        closeMenu();
        settings = new LabSettings(this, lab, columnBox, layout, samples, test, this::render, this::tapped, new LabSettings.Beside() {
            @Override public void open(View anchor, View panel) { showMenu(anchor, panel); }
            @Override public void close() { closeMenu(); }
        });
        LinearLayout.LayoutParams randomizePlace = new LinearLayout.LayoutParams(-1, dp(layout.randomize)); randomizePlace.topMargin = dp(layout.gap);
        rest.addView(settings.randomize(), randomizePlace);
        main.addView(rest, new FrameLayout.LayoutParams(-1, -1));
        // Open's sheet under the header, so Open stays in reach and closes it again (the owner, 2026-10-29: "once you
        // click load, you can't shrink that form without entering something").
        sheet = sheet();
        FrameLayout.LayoutParams sheetPlace = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP); sheetPlace.topMargin = dp(layout.top + layout.aboutHead);
        main.addView(sheet, sheetPlace);
        frame.addView(main, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(frame, new FrameLayout.LayoutParams(-1, -1));
        nameShown = null; actionsShown = null; markShown = null; wordsShown = null; columnShown = null; pickerShown = null; shelfShown = null;
        showSheet(sheetOpen);
        // The pad may keep its size and place, so nothing else reconnects it after the disconnect above.
        pad.post(pad::connect);
    }
    /**
     * A band: paper in a {@link Theme#FINE} dp ink frame, the menus' edge, where grey-200 was (the owner, 2026-10-23:
     * "eink grey doesn't quite work the same way"); the lab's sides inside it, {@code top} and {@code bottom} dp over and under.
     */
    private LinearLayout band(int top, int bottom) {
        LinearLayout band = LabUi.column(this);
        band.setBackground(Theme.menu(this));
        band.setPadding(dp(layout.side), dp(top), dp(layout.side), dp(bottom));
        return band;
    }

    /** Shows the lab as it now stands, after any change: only what changed is drawn again (e-ink). */
    private void render() {
        if (layout == null) return;
        if (lab.parts() == null) { opened = null; dropSwap(); }
        // Cut Stamp waits only while the brush is a Stamp.
        if (pad.cutting() && (lab.parts() == null || !lab.parts().stamps())) pad.cutNext(null);
        // The Open sheet, the rename keyboard, the swap panel or a dial's menu lies over the test page: presenting it straight to the panel would paint over them.
        pad.setCovered(renaming || sheetOpen || swapping != null || menuPopup != null);
        String nameNow = lab.brush().name() + "\n" + renaming;
        if (!nameNow.equals(nameShown)) { nameShown = nameNow; renderName(); }
        BrushLibrary.Entry entry = lab.savedEntry();
        String actionsNow = lab.saved() + "\n" + sheetOpen + "\n" + lab.parts().stamps() + "\n" + pad.cutting() + "\n" + cutSaid + "\n" + saveSaid
                + "\n" + lab.changed(lab.selected()) + "\n" + lab.started().name() + "\n" + (entry == null ? "" : entry.brush.name());
        if (!actionsNow.equals(actionsShown)) { actionsShown = actionsNow; renderActions(entry); }
        String suggestion = lab.suggestion(), markNow = lab.edited() + "\n" + suggestion;
        if (!markNow.equals(markShown)) { markShown = markNow; renderMark(suggestion); }
        String about = BrushVoice.about(lab.parts());
        if (!about.equals(wordsShown)) {
            wordsShown = about;
            words.setText(ToolsLayout.fitWords(about, layout.wordsWidth, layout.wordsLines));
            words.setContentDescription(about);
        }
        String kept = lab.note();
        if (kept != null) sayNote(kept);
        if (undo.isEnabled() != lab.canUndo()) undo.setEnabled(lab.canUndo());
        showPicker();
        test.showLoop();
        String column = lab.selected() + "\n" + lab.brush().identity() + "\n" + opened;
        if (!column.equals(columnShown)) { columnShown = column; settings.render(opened); }
        showSwap();
        if (sheetOpen) {
            if (!lab.code().contentEquals(code.getText())) code.setText(lab.code());
            renderShelf(entry);
        }
    }

    /**
     * A module tapped: its choices open beside it, and the part opens in the column too, staying open when the panel
     * closes; its module tapped again opens the panel again, and the open card's ✕ ({@code null}) closes the part.
     */
    private void tapped(BrushPart part) {
        opened = part;
        swapping = part;
        render();
    }
    /** The swap panel closed (a pick, ✕, a tap outside it): the lab as it now stands. */
    private void closeSwap() {
        boolean was = swapping != null;
        dropSwap();
        if (was) render();
    }
    /** The swap panel closed, nothing drawn again (the lab being laid out anew, or left). */
    private void dropSwap() {
        PopupWindow popup = swapPopup;
        swapPopup = null;
        swapping = null;
        if (popup != null) popup.dismiss();
    }
    /**
     * A choice dial's menu ({@link LabSettings.Beside}): {@code panel} in a popup beside the dial's button, as a part's
     * swap panel opens beside its module ({@link #besideColumn}): just right of the column, level with the button, as wide
     * as the column. A pick, a tap outside it or Back closes it; the test page is covered while it's open.
     */
    private void showMenu(View anchor, View panel) {
        closeMenu();
        dropSwap();
        PopupWindow popup = Popups.create(panel, 0, true, Theme.PAPER);
        int width = dp(layout.column);
        RectF at = besideColumn(anchor, width, Popups.measuredHeight(panel, width, 0));
        popup.setOnDismissListener(() -> { feedback.cleanAfterClose(root, at); if (menuPopup == popup) { menuPopup = null; render(); } });
        menuPopup = popup;
        pad.setCovered(true);
        Popups.show(popup, root, at);
    }
    /** A dial's menu closed (a pick, which redraws the lab; the lab laid out anew, or left): nothing drawn again here. */
    private void closeMenu() { PopupWindow popup = menuPopup; menuPopup = null; if (popup != null) popup.dismiss(); }
    /**
     * Where a panel {@code width × height} px opens beside {@code anchor} in the column (a module, a dial's button): just
     * right of the column, level with it, inside the screen under the bar.
     */
    private RectF besideColumn(View anchor, int width, int height) {
        RectF at = Popups.bounds(root, anchor), column = Popups.bounds(root, columnBox);
        RectF area = new RectF(0, dp(LabLayout.BAR), root.getWidth() - dp(layout.side), root.getHeight() - dp(layout.bottom));
        return Popups.besideAnchor(new RectF(at.left, at.top, column.right, at.bottom), area, width, height, false, dp(layout.split));
    }
    /**
     * The swap panel, while a part's choices are open: its tiles shown in place, and once the column is laid out (a
     * part opening rebuilds it), the popup beside the part's module: just right of the column, level with the module, as
     * tall as its tiles and inside the screen.
     */
    private void showSwap() {
        if (swapping == null) return;
        strip.render(swapping);
        if (swapPopup != null) return;
        // The last popup's frame may still hold it.
        Ui.detach(strip);
        PopupWindow popup = Popups.create(strip, 0, true, Theme.PAPER);
        // The pen on another part's module opens that part's choices in one step; on its own, or anywhere else, it closes them.
        popup.setTouchInterceptor((v, e) -> {
            if (Ui.restingOutside(v, e, false)) return true;  // Brush Lab: only the pen closes it, as before
            int action = e.getActionMasked();
            if (action != MotionEvent.ACTION_DOWN && action != MotionEvent.ACTION_OUTSIDE || !Ui.outside(v, e)) return false;
            BrushPart next = BrushLab.swapTo(swapping, partAt(e));
            if (next == null) return false;
            switchSwap(next);
            return true;
        });
        RectF at = new RectF();
        popup.setOnDismissListener(() -> { feedback.cleanAfterClose(root, at); if (swapPopup == popup) closeSwap(); });
        swapPopup = popup;
        BrushPart part = swapping;
        // Posted: it runs after the layout the render asked for, so the module's place is its new one.
        root.post(() -> {
            View card = settings.card(part);
            if (swapPopup != popup || card == null || isFinishing()) return;
            int width = dp(layout.right);
            at.set(besideColumn(card, width, Popups.measuredHeight(strip, width, 0)));
            Popups.show(popup, root, at);
        });
    }

    /** The part whose module lies under {@code event} (the screen's), or null. */
    private BrushPart partAt(MotionEvent event) {
        for (BrushPart part : BrushPart.SHOWN) {
            View card = settings.card(part);
            if (card != null && card.isShown() && !Ui.outside(card, event)) return part;
        }
        return null;
    }
    /**
     * {@code part}'s choices in place of the open part's ({@link BrushLab#swapTo}): the open panel closes, its patch
     * cleaned ({@link SelectionFeedback#cleanAfterClose}), and only once that clean has run does the lab show with
     * {@code part}'s panel, so the new panel never lies over the patch being cleaned and no ghost stays.
     */
    private void switchSwap(BrushPart part) {
        PopupWindow popup = swapPopup;
        // Dismissed here, it doesn't close the swap: its listener only cleans.
        swapPopup = null;
        if (popup != null) popup.dismiss();
        opened = part;
        swapping = part;
        root.postDelayed(this::render, DrawingPad.CLOSED_MS);
    }

    // The bar

    /** Back ("‹ Tools" when the Tools page opened the lab), the title, and Undo at the right (plain words, greyed with nothing to undo; its arrow alone on the Nomad). */
    private View bar() {
        LinearLayout bar = LabUi.row(this);
        bar.setPadding(dp(layout.side), 0, dp(layout.side), 0);
        // Back, labelled, always first at the left (the owner: "back button should be clearly visible").
        LabButton back = LabButton.back(this, fromTools ? ToolsLayout.TITLE : "Back");
        back.setOnClickListener(v -> back());
        bar.addView(back, new LinearLayout.LayoutParams(-2, dp(44)));
        TextView title = LabUi.text(this, LabLayout.TITLE, TypeStyle.HEADING);
        title.setSingleLine(true);
        title.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(title, LabUi.after(this, -2, 44, 10));
        LabUi.spring(bar);
        undo = new LabButton(this, R.drawable.ic_ph_undo, layout.compact ? null : "Undo", false);
        undo.setContentDescription("Undo");
        undo.setEnabled(lab.canUndo());
        undo.setOnClickListener(v -> { pad.finishStroke(); lab.undo(); render(); });
        bar.addView(undo, LabUi.after(this, layout.compact ? dp(44) : -2, 44, 12));
        return bar;
    }

    // About This Brush

    /**
     * About This Brush's header: the brush's actions ({@link #ACTIONS}) from its left, no label (the owner, 2026-10-29: "you
     * can remove the text ABOUT THIS BRUSH and move the menu buttons to the left there").
     */
    private View aboutHeader() { return actions = LabUi.row(this); }
    /**
     * About This Brush's block: the brush's loop at the left; the name (a heading, its pencil after it; while renaming a
     * field and Done), the Edited mark and a suggested name, then the words; at the right the picker box lying across,
     * and under it a note's line.
     */
    private View about() {
        LinearLayout block = LabUi.row(this);
        block.setGravity(Gravity.TOP);
        int pad = dp(layout.pad);
        block.setPadding(pad, pad, pad, pad);
        block.addView(test.blockLoop(new LinearLayout.LayoutParams(dp(layout.loopWidth), -1)));
        LinearLayout named = LabUi.column(this);
        nameBox = LabUi.row(this);
        named.addView(nameBox, new LinearLayout.LayoutParams(-1, dp(layout.nameHeight())));
        named.addView(mark(), new LinearLayout.LayoutParams(-1, dp(layout.mark)));
        words = LabUi.text(this, "", layout.wordsSp, 400);
        words.setTextColor(Theme.GREY_800);
        LinearLayout.LayoutParams under = new LinearLayout.LayoutParams(-1, -2); under.topMargin = dp(LabLayout.NAME_GAP);
        named.addView(words, under);
        LinearLayout.LayoutParams namedPlace = new LinearLayout.LayoutParams(0, -1, 1); namedPlace.leftMargin = dp(LabLayout.LOOP_GAP);
        block.addView(named, namedPlace);
        LinearLayout swatches = LabUi.column(this);
        LinearLayout across = LabUi.row(this);
        across.addView(picker(), new LinearLayout.LayoutParams(dp(layout.picker), dp(layout.pickerThick)));
        readout = new SwatchReadout(this);
        across.addView(readout, LabUi.after(this, dp(layout.readout), layout.readout, layout.gap));
        swatches.addView(across, new LinearLayout.LayoutParams(-1, dp(layout.pickerThick)));
        said = LabUi.text(this, "", TypeStyle.CAPTION);
        said.setTextColor(Theme.GREY_800);
        said.setSingleLine(true); said.setEllipsize(TextUtils.TruncateAt.END);
        said.setGravity(Gravity.END);
        swatches.addView(said, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams swatchesPlace = new LinearLayout.LayoutParams(dp(layout.picker + layout.gap + layout.readout), -1); swatchesPlace.leftMargin = dp(layout.gap);
        block.addView(swatches, swatchesPlace);
        return block;
    }
    /** The name (one heading line, a long one two lines at {@link #TWO_LINE_SP} on the Manta) and its pencil; while renaming, a field and Done. */
    private void renderName() {
        nameBox.removeAllViews();
        name = null;
        if (!renaming) {
            String text = lab.brush().name();
            name = LabUi.text(this, text, TypeStyle.HEADING);
            boolean two = !layout.compact && LabText.width(text, TypeStyle.HEADING.sp, 0) > layout.nameWidth;
            if (two) name.setTextSize(TWO_LINE_SP);
            name.setSingleLine(false); name.setMaxLines(two ? 2 : 1); name.setEllipsize(TextUtils.TruncateAt.END); name.setLineSpacing(0, .95f);
            name.setGravity(Gravity.CENTER_VERTICAL);
            name.setMaxWidth(dp(layout.nameWidth));
            nameBox.addView(name, new LinearLayout.LayoutParams(-2, -1));
            LabButton pencil = new LabButton(this, R.drawable.ic_ph_pencil_simple, null, false);
            pencil.setContentDescription("Rename");
            pencil.setOnClickListener(v -> { renaming = true; render(); });
            nameBox.addView(pencil, new LinearLayout.LayoutParams(dp(40), -1));
            return;
        }
        EditText field = new EditText(this);
        Theme.style(field, TypeStyle.HEADING);
        // Edited, the field offers the suggested name to take or type over (the owner, 2026-10-29: "maybe suggest a new name?").
        String suggestion = lab.suggestion();
        field.setText(suggestion != null ? suggestion : lab.brush().name()); field.setSingleLine(true);
        field.setBackground(Theme.menu(this)); field.setPadding(dp(8), dp(4), dp(8), dp(4));
        field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(Brush.NAME_MAX)});
        field.setImeOptions(EditorInfo.IME_ACTION_DONE);
        field.setOnEditorActionListener((v, action, event) -> { finishRename(field); return true; });
        nameBox.addView(field, new LinearLayout.LayoutParams(0, -2, 1));
        LabButton done = new LabButton(this, 0, "Done", false);
        done.setOnClickListener(v -> finishRename(field));
        nameBox.addView(done, LabUi.after(this, -2, 44, 6));
        field.requestFocus(); field.selectAll();
        InputMethodManager keyboard = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
        if (keyboard != null) keyboard.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT);
    }
    /** The line under the name: "Edited" once the brush changed since it was opened or saved, and a suggested name to tap. */
    private View mark() {
        LinearLayout row = LabUi.row(this);
        edited = LabUi.text(this, "", TypeStyle.CAPTION);
        edited.setTextColor(Theme.INK); edited.setSingleLine(true);
        edited.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(edited, new LinearLayout.LayoutParams(-2, -1));
        suggested = LabUi.text(this, "", TypeStyle.CAPTION);
        suggested.setTextColor(Theme.INK); suggested.setSingleLine(true); suggested.setEllipsize(TextUtils.TruncateAt.END);
        suggested.setGravity(Gravity.CENTER_VERTICAL);
        suggested.setPaintFlags(suggested.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        suggested.setOnClickListener(v -> { pad.finishStroke(); if (lab.takeSuggestion()) render(); });
        LinearLayout.LayoutParams after = new LinearLayout.LayoutParams(0, -1, 1); after.leftMargin = dp(12);
        row.addView(suggested, after);
        return row;
    }
    /** Edited, or nothing (its room kept: nothing moves); the {@code suggestion} to tap, or nothing. */
    private void renderMark(String suggestion) {
        edited.setText(lab.edited() ? LabLayout.EDITED : "");
        suggested.setText(suggestion == null ? "" : LabLayout.SUGGESTED + suggestion);
        suggested.setContentDescription(suggestion == null ? null : "Use the name " + suggestion);
        suggested.setClickable(suggestion != null);
    }
    /**
     * About This Brush's buttons, in {@link #ACTIONS}' order: New, Open (on while its sheet is open), Save (ink; "Saved"
     * with its check while the brush is saved as it is; for a moment after a tap what Save did; said "Save over" the
     * entry it writes over), Save as New, Back to the slot's first brush (its name cut to fit), and a Stamp's Cut Stamp
     * (on while it waits). {@code entry}: the library entry Save writes over, or null.
     */
    private void renderActions(BrushLibrary.Entry entry) {
        actions.removeAllViews();
        int barPad = dp(LabLayout.barPad(layout.compact));
        boolean first = true;
        for (Action action : ACTIONS) {
            LabButton button;
            switch (action) {
                case NEW:
                    button = new LabButton(this, 0, LabLayout.NEW, false);
                    button.setContentDescription("New brush");
                    button.setOnClickListener(v -> { pad.finishStroke(); lab.newBrush(); render(); });
                    break;
                case OPEN:
                    button = new LabButton(this, 0, LabLayout.OPEN, false);
                    button.setOn(sheetOpen);
                    button.setContentDescription(sheetOpen ? "Close" : "Open a brush");
                    button.setOnClickListener(v -> showSheet(!sheetOpen));
                    break;
                case SAVE: {
                    // A brush not from the library has only Save as New, the one way it can go there (the owner, 2026-10-29:
                    // "move to save as new if it's a new brush (instead of staying on save)").
                    if (entry == null) continue;
                    // Saved reads unlike Save at a glance on e-ink: plain words with a check, where Save is solid ink with a disk.
                    // Just tapped, it says for a moment what Save did (the owner, 2026-10-23: "Save on Brush Lab doesn't give a confirmation").
                    boolean saved = lab.saved();
                    String label = saveSaid != null ? saveSaid : saved ? "Saved" : LabLayout.SAVE;
                    button = new LabButton(this, 0, label, !saved);
                    button.setIcon(saved ? R.drawable.ic_ph_saved : R.drawable.ic_ph_save, 18);
                    button.setContentDescription(saveSaid != null ? saveSaid : saved ? "Saved" : entry != null ? "Save over " + entry.brush.name() : "Save to library");
                    button.setOnClickListener(v -> { lab.save(); saySaved(lab.status); });
                    break;
                }
                case SAVE_AS_NEW:
                    // Solid ink with the disk where it is the only save; plain beside Save for a library brush.
                    button = new LabButton(this, 0, LabLayout.SAVE_AS_NEW, entry == null);
                    if (entry == null) button.setIcon(R.drawable.ic_ph_save, 18);
                    button.setOnClickListener(v -> { lab.saveAsNew(); saySaved(lab.status); });
                    break;
                case BACK_TO:
                    // Back to the slot's first brush (the owner: "an easy way"), taking the row's room.
                    button = new LabButton(this, R.drawable.ic_ph_arrow_counter_clockwise, ToolsLayout.BACK_TO + lab.started().name(), false);
                    button.setEllipsize(TextUtils.TruncateAt.END);
                    button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                    button.setOnClickListener(v -> { pad.finishStroke(); lab.restore(); render(); });
                    button.setVisibility(lab.changed(lab.selected()) ? View.VISIBLE : View.INVISIBLE);
                    break;
                default:
                    // A Stamp's Cut Stamp: the test page's next pen stroke is a lasso; on while it waits, a tap again stops it.
                    if (!lab.parts().stamps()) continue;
                    button = new LabButton(this, 0, cutSaid != null ? cutSaid : StampNib.CUT, false);
                    button.setOn(this.pad.cutting());
                    button.setOnClickListener(v -> cutStamp());
            }
            button.setPadding(barPad, 0, barPad, 0);
            LinearLayout.LayoutParams place = action == Action.BACK_TO ? new LinearLayout.LayoutParams(0, -1, 1) : new LinearLayout.LayoutParams(-2, -1);
            if (!first) place.leftMargin = dp(LabLayout.ACTION_GAP);
            actions.addView(button, place);
            first = false;
        }
    }
    private final Runnable noteGone = () -> said.setText("");
    /** {@code text} under the picker for {@link DrawLayout#NOTE_MS} ("Kept Fineliner in Recent"); no animation. */
    private void sayNote(String text) {
        said.setText(text);
        root.removeCallbacks(noteGone);
        root.postDelayed(noteGone, DrawLayout.NOTE_MS);
        root.announceForAccessibility(text);
    }
    /** What Save's button says for a moment after a tap ("Saved to library", "Saved Fineliner"), or null. */
    private String saveSaid;
    private final Runnable saveSaidGone = () -> { saveSaid = null; render(); };
    /** Save's button says {@code text} for {@link DrawLayout#NOTE_MS}, as the drawing screen's notes stay; no animation. */
    private void saySaved(String text) {
        saveSaid = text;
        root.removeCallbacks(saveSaidGone);
        root.postDelayed(saveSaidGone, DrawLayout.NOTE_MS);
        render();
        root.announceForAccessibility(text);
    }
    /** What Cut Stamp's button says for a moment instead of its name ("Nothing to cut"), or null. */
    private String cutSaid;
    /**
     * Cut Stamp (a Stamp's): the test page's next pen stroke is a lasso, in marching ants; the ink inside it, trimmed,
     * becomes the Stamp's nib ({@link BrushLab#cutNib}, kept in the nib store), or the button says "Nothing to cut" for a
     * moment. Tapped while it waits, it stops waiting.
     */
    private void cutStamp() {
        if (pad.cutting()) { pad.cutNext(null); render(); return; }
        pad.finishStroke();
        cutSaid = null;
        pad.cutNext(nib -> {
            if (nib != null) lab.cutNib(StampNib.keep(nib));
            else {
                cutSaid = StampNib.NOTHING;
                root.postDelayed(() -> { cutSaid = null; render(); }, StampNib.NOTHING_MS);
            }
            render();
        });
        render();
    }
    private void finishRename(EditText field) {
        hideKeyboard(field);
        renaming = false;
        lab.rename(field.getText().toString());
        render();
    }

    // The scratch pad

    /** The scratch pad's head row, as tall as the column's, so the test page and the modules start level: its label, Clear and the wet indicator at its right. */
    private View testHeader() {
        LinearLayout head = LabUi.header(this, "Scratch Pad");
        LabUi.spring(head);
        LabButton clear = test.clear(layout.clearPad);
        clear.setMinHeight(0); clear.setMinimumHeight(0);
        head.addView(clear, new LinearLayout.LayoutParams(-2, -1));
        // Beside Clear, the droplet while the test page's Slow wash is still settling; a tap lays it now.
        head.addView(test.wet(), new LinearLayout.LayoutParams(dp(layout.wet), -1));
        return head;
    }
    /**
     * The slot's size and grey: the drawing screen's picker box ({@link PickerBox}; the owner, 2026-10-29: "we can use the
     * same horizontal picker from the toolbar in place of the pickers there"), turned a quarter so it lies across: the
     * greys black at the left to white at the right, their tab above; the sizes fine at the left to broad at the right,
     * their tab under. Set on the lift, stored at once, as the drawing screen's; a Wet ink brush is dipped in a grey picked.
     */
    private View picker() {
        picker = new PickerBox(this, feedback, new SizeSlider.Listener() {
            @Override public boolean ready() { pad.finishStroke(); return true; }
            @Override public void set(double t) { lab.slots.swatches.useSize(key(), ToolSwatches.sizeAt(t)); pickerChanged(); }
        }, darkness -> {
            pad.finishStroke();
            lab.slots.swatches.useGrey(key(), darkness);
            test.session.held.dip(HeldPaint.ANY);
            pickerChanged();
        }, null, null);
        QuarterTurnLayout turned = new QuarterTurnLayout(this);
        turned.setTurn(90);
        turned.addView(picker);
        return turned;
    }
    /** The size or grey picked: the picker's tabs and the loop show it. */
    private void pickerChanged() { showPicker(); test.showLoop(); }
    /** What the selected slot draws at: its own size and grey ({@link ToolSwatches}), stored at once, as the drawing screen's. */
    private ToolSwatches.Look look() { return lab.slots.swatches.look(key(), lab.brush()); }
    private String key() { return ToolSlots.key(lab.selected()); }
    /** The picker's tabs and the readout's dot at the slot's size and grey, in the brush's nib, drawn again only when they change. */
    private void showPicker() {
        ToolSwatches.Look look = look();
        String now = key() + " " + lab.brush().identity() + " " + look.px + " " + look.darkness;
        if (now.equals(pickerShown)) return;
        pickerShown = now;
        picker.show(look, lab.brush(), true);
        readout.show(look, lab.brush(), true);
        picker.strip.invalidate(); picker.slider.invalidate(); readout.invalidate();
    }

    // The Open sheet

    /**
     * Under About This Brush's header, a sheet in a menu's look (paper, a fine ink edge), its fields framed the same:
     * open a pasted code (or Cancel, which closes it, nothing changed), copy this brush's, or open one of the library's
     * brushes, a page of them at a time; a line under them says when a code didn't read, or was copied.
     */
    private LinearLayout sheet() {
        LinearLayout sheet = LabUi.column(this);
        sheet.setBackground(Theme.menu(this));
        sheet.setPadding(dp(16), dp(12), dp(16), dp(12));
        // Taps on it stay on it, never reaching the page under it.
        sheet.setClickable(true);
        int labelWidth = dp(layout.compact ? 120 : 140);
        LinearLayout loadRow = LabUi.row(this);
        loadRow.addView(LabUi.heading(this, "Paste code"), new LinearLayout.LayoutParams(labelWidth, -2));
        paste = new EditText(this);
        Theme.style(paste, TypeStyle.BODY);
        paste.setHint("brave-onion.M…"); paste.setHintTextColor(Theme.GREY_800); paste.setSingleLine(true);
        paste.setBackground(Theme.menu(this)); paste.setPadding(dp(8), dp(8), dp(8), dp(8));
        paste.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        paste.setImeOptions(EditorInfo.IME_ACTION_DONE);
        paste.setOnEditorActionListener((v, action, event) -> { loadCode(); return true; });
        loadRow.addView(paste, new LinearLayout.LayoutParams(0, dp(44), 1));
        LabButton load = new LabButton(this, 0, LabLayout.OPEN, false);
        load.setContentDescription("Open the code");
        load.setOnClickListener(v -> loadCode());
        loadRow.addView(load, LabUi.after(this, -2, 44, 8));
        LabButton cancel = new LabButton(this, 0, "Cancel", false);
        cancel.setOnClickListener(v -> showSheet(false));
        loadRow.addView(cancel, LabUi.after(this, -2, 44, 8));
        sheet.addView(loadRow, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout copyRow = LabUi.row(this);
        copyRow.addView(LabUi.heading(this, "This brush"), new LinearLayout.LayoutParams(labelWidth, -2));
        code = LabUi.text(this, "", TypeStyle.CAPTION);
        code.setTypeface(Theme.face(400)); code.setGravity(Gravity.CENTER_VERTICAL);
        code.setSingleLine(true); code.setEllipsize(TextUtils.TruncateAt.MIDDLE); code.setTextIsSelectable(true);
        code.setBackground(Theme.menu(this)); code.setPadding(dp(8), 0, dp(8), 0);
        copyRow.addView(code, new LinearLayout.LayoutParams(0, dp(44), 1));
        LabButton copy = new LabButton(this, R.drawable.ic_ph_copy, "Copy", false);
        copy.setOnClickListener(v -> copyCode());
        copyRow.addView(copy, LabUi.after(this, -2, 44, 8));
        LinearLayout.LayoutParams below = new LinearLayout.LayoutParams(-1, -2); below.topMargin = dp(10);
        sheet.addView(copyRow, below);
        shelf = LabUi.column(this);
        LinearLayout.LayoutParams shelfPlace = new LinearLayout.LayoutParams(-1, dp(LabLayout.SECTION_BAR + layout.shelfRows * layout.cardHeight)); shelfPlace.topMargin = dp(10);
        sheet.addView(shelf, shelfPlace);
        note = LabUi.text(this, "", TypeStyle.CAPTION);
        note.setTextColor(Theme.INK);
        LinearLayout.LayoutParams under = new LinearLayout.LayoutParams(-1, -2); under.topMargin = dp(10);
        sheet.addView(note, under);
        return sheet;
    }
    /**
     * The library's brushes on the Open sheet, a page at a time (newest first, {@link BrushLab#library}): each its tray
     * stroke and name; the one Save writes over ({@code entry}) with its tab; a tap opens it ({@link BrushLab#open}).
     * Drawn again only when the page or the library changes.
     */
    private void renderShelf(BrushLibrary.Entry entry) {
        List<BrushLab.Card> cards = lab.library();
        int columns = layout.shelfColumns, perPage = columns * layout.shelfRows, pages = LabPages.pages(cards.size(), perPage);
        shelfPage = LabPages.clamp(shelfPage, cards.size(), perPage);
        StringBuilder now = new StringBuilder().append(shelfPage).append(entry == null ? "" : entry.id);
        for (BrushLab.Card card : cards) now.append('\n').append(card.entry.id).append(card.brush.identity());
        if (now.toString().equals(shelfShown)) return;
        shelfShown = now.toString();
        shelf.removeAllViews();
        LinearLayout head = LabUi.bar(this);
        head.setPadding(0, 0, 0, 0);
        head.addView(LabUi.barText(this, "Library"));
        LabUi.spring(head);
        if (pages > 1) {
            LabButton previous = LabButton.headIcon(this, R.drawable.ic_ph_caret_left, "Previous page");
            previous.setEnabled(shelfPage > 0);
            previous.setOnClickListener(v -> { shelfPage--; render(); });
            head.addView(previous, LabUi.after(this, dp(layout.pageButton), 28, 8));
            TextView count = LabUi.barText(this, (shelfPage + 1) + "/" + pages);
            count.setPadding(dp(4), 0, dp(4), 0);
            head.addView(count);
            LabButton next = LabButton.headIcon(this, R.drawable.ic_ph_caret_right, "Next page");
            next.setEnabled(shelfPage < pages - 1);
            next.setOnClickListener(v -> { shelfPage++; render(); });
            head.addView(next, LabUi.after(this, dp(layout.pageButton), 28, 0));
        }
        shelf.addView(head, new LinearLayout.LayoutParams(-1, dp(LabLayout.SECTION_BAR)));
        LinearLayout grid = LabUi.grid(this);
        for (int r = 0; r < layout.shelfRows; r++) {
            LinearLayout row = LabUi.row(this);
            for (int c = 0; c < columns; c++) {
                int index = shelfPage * perPage + r * columns + c;
                View cell;
                if (index < cards.size()) {
                    BrushLab.Card card = cards.get(index);
                    LoopCard tile = new LoopCard(this, samples, java.util.Collections.emptyList(), card.brush.name());
                    tile.ruled = true;
                    tile.setSample(card.brush.identity() + " tray", LabLibrary.traySampler(card.brush, null));
                    tile.setNow(entry != null && card.entry.id == entry.id);
                    tile.setContentDescription(LabLayout.OPEN + " " + card.brush.name());
                    tile.setOnClickListener(v -> { pad.finishStroke(); lab.open(card.entry); showSheet(false); });
                    cell = tile;
                } else { cell = new View(this); cell.setBackgroundColor(Theme.PAPER); }
                row.addView(cell, LabUi.cell(this, c, columns));
            }
            grid.addView(row, LabUi.gridRow(this, r, layout.shelfRows));
        }
        shelf.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));
    }
    /** Opens or closes the Open sheet; while it is open the test page is drawn under it, not presented over it ({@link #render}). */
    private void showSheet(boolean open) {
        sheetOpen = open;
        sheet.setVisibility(open ? View.VISIBLE : View.GONE);
        if (open) { code.setText(lab.code()); note.setVisibility(View.GONE); shelfShown = null; }
        else hideKeyboard(paste);
        render();
    }
    private void copyCode() {
        ClipboardManager clipboard = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("Brush code", lab.code()));
        say(clipboard != null ? "Copied" : "Select the code to copy");
    }
    /** Puts the pasted code's brush in the selected slot and closes the sheet; a code that doesn't read says so in it, as the lab words it. */
    private void loadCode() {
        hideKeyboard(paste);
        pad.finishStroke();
        if (lab.load(paste.getText().toString())) {
            paste.setText("");
            showSheet(false);
            return;
        }
        say(lab.status);
    }
    /** {@code text} on the Open sheet's last line. */
    private void say(String text) {
        note.setText(text);
        note.setVisibility(View.VISIBLE);
        render();
    }
    private void hideKeyboard(View field) {
        InputMethodManager keyboard = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
        if (keyboard != null && field != null) keyboard.hideSoftInputFromWindow(field.getWindowToken(), 0);
    }

    // Leaving

    /**
     * Back to the drawing (and on to the Tools page when it opened the lab), with the ten slots' brushes and the
     * selected slot: the slot the lab opened on holds whatever it ended with, saved or not (the owner, 2026-10-23: no trap).
     */
    private void back() {
        pad.finishStroke();
        Brush[] results = lab.results();
        String[] written = new String[results.length];
        for (int i = 0; i < results.length; i++) written[i] = results[i].write();
        setResult(RESULT_OK, new Intent().putExtra(SLOT, lab.selected()).putExtra(BRUSHES, written).putExtra(START, fromTools ? lab.start.write() : null));
        finish();
        overridePendingTransition(0, 0);
    }
    @Override public void onBackPressed() {
        if (renaming) { renaming = false; render(); return; }
        if (sheetOpen) { showSheet(false); return; }
        back();
    }
    @Override protected void onResume() { super.onResume(); pad.opened(); pad.post(pad::connect); }
    @Override protected void onPause() { pad.disconnect(); super.onPause(); }
    @Override protected void onDestroy() { dropSwap(); closeMenu(); samples.close(); pad.close(); super.onDestroy(); }

    private int dp(float value) { return Ui.dp(this, value); }
    private int dpOf(int pixels) { return Ui.dpOf(this, pixels); }
}
