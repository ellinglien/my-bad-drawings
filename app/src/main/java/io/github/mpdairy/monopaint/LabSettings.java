package io.github.mpdairy.monopaint;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * Brush Lab's settings column, <b>Adjust</b>, which never scrolls (the design pass, docs/archive/design-pass-2026-10-23.md):
 * its head row, as tall as the scratch pad's so the two start level (ADJUST, the one header style, and at its right
 * Start from ▾: the styles in a menu over the column, a pick a new brush in that style), then the brush's {@link
 * PartCard} modules, two to a row in {@link BrushPart#SHOWN}'s order, one slider each (their end words kept: fine and
 * broad, dry and wet), with their locks and dice (always what Advanced showed, 2026-10-29: "let's just have it advanced");
 * tapping a module opens that part (the part {@code picked}, whose swap panel opens beside it): the six become slim
 * modules, and the open card, as wide as the column, lies under them. The open card has its head (the choice shows in
 * the slim module and in the swap panel, which picks it; the slim module tapped opens the panel again, ✕ closes the
 * part), then every dial as a row ({@link #row}): name, slider or segments, value, lock, die; Control its {@link
 * HandGrid}, Response and the Fast switch. The text is {@link LabLayout#DENSE_SP}, never smaller. Randomize
 * ({@link #randomize}) lies across the lab's foot. Every size is {@link LabLayout}'s.
 * A change to the brush calls {@code changed}, which redraws only what it changed (e-ink: a module only when its
 * part changed, {@link BrushLab#redraw}; rows rebuilt only when more than their sliders changed); tapping a module
 * calls {@code pick} with its part; a lock only redraws the column.
 */
final class LabSettings {
    /** The Hand's Response and Fast rows; the room under the open card's head (LabLayout.dialRow's budget). */
    private static final int UNDER_HEAD = 10;
    private final Context context;
    private final BrushLab lab;
    private final FrameLayout box;
    private final LabLayout layout;
    private final SampleQueue samples;
    private final TestArea test;
    private final Runnable changed;
    private final Consumer<BrushPart> pick;
    private final Beside beside;
    /**
     * Where a choice dial's menu opens ({@link #menuButton}): beside the button tapped, as a part's swap panel opens
     * beside its module ({@link BrushLabActivity}); {@link #close} closes it.
     */
    interface Beside {
        void open(View anchor, View panel);
        void close();
    }
    private LinearLayout column;
    private BrushPart picked;
    // The views, built once per kind of brush (and per open part) and then updated in place (e-ink), and what they show.
    private String built, styleShown;
    private LabButton styleButton;
    private View flyout;
    private final Map<BrushPart, PartCard> cards = new EnumMap<>(BrushPart.class);
    /** The six-part brush the modules last showed (a copy: the lab changes its own in place), to draw again only the parts that changed. */
    private ModularBrush partsShown;
    /**
     * The open card's dials: built anew only when what they show but their sliders' values changes ({@link #openKey});
     * a slide or a die only moves the sliders and their values ({@link #live}).
     */
    private LinearLayout openBody;
    private String rowsShown;
    private final List<Live> live = new ArrayList<>();
    /** A row's slider and its value, and what they show now. */
    private static final class Live {
        final BandSlider slider;
        final TextView shown;
        final DoubleSupplier value;
        final Supplier<String> text;
        Live(BandSlider slider, TextView shown, DoubleSupplier value, Supplier<String> text) { this.slider = slider; this.shown = shown; this.value = value; this.text = text; }
    }

    LabSettings(Context context, BrushLab lab, FrameLayout box, LabLayout layout, SampleQueue samples, TestArea test, Runnable changed, Consumer<BrushPart> pick, Beside beside) {
        this.context = context; this.lab = lab; this.box = box; this.layout = layout; this.samples = samples; this.test = test; this.changed = changed; this.pick = pick;
        this.beside = beside;
    }

    /** Shows the lab's brush, {@code picked} the open part, whose swap panel may be open too (or null). */

    void render(BrushPart picked) {
        this.picked = picked;
        ModularBrush parts = lab.parts();
        String kind = picked != null ? "open " + picked : "parts";
        if (!kind.equals(built)) { built = kind; build(parts); }
        show(parts);
    }
    /** {@code part}'s module as now shown (the swap panel opens beside it), or null. */
    View card(BrushPart part) { return cards.get(part); }

    /**
     * The head row (ADJUST, Start from ▾), then the six modules; or, a part open, the six slim and the open card. Each as
     * tall as the layout says.
     */
    private void build(ModularBrush parts) {
        box.removeAllViews();
        cards.clear();
        styleButton = null; flyout = null; styleShown = null;
        openBody = null; partsShown = null; rowsShown = null; live.clear();
        column = LabUi.column(context);
        box.addView(column, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout head = LabUi.header(context, "Adjust");
        LinearLayout.LayoutParams stylePlace = new LinearLayout.LayoutParams(0, -1, 1); stylePlace.leftMargin = dp(8);
        head.addView(styleButton(), stylePlace);
        column.addView(head, new LinearLayout.LayoutParams(-1, dp(layout.headRow)));
        // ui.js 324: a part open, the six slim and the open card under them, across the column's padding.
        boolean open = picked != null;
        for (int row = 0; row < 3; row++) {
            LinearLayout pair = LabUi.row(context);
            for (int i = 0; i < 2; i++) {
                LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(0, -1, 1);
                if (i > 0) place.leftMargin = dp(layout.moduleGap);
                pair.addView(card(BrushPart.SHOWN.get(row * 2 + i), open, row), place);
            }
            LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(-1, dp(open ? layout.slim : layout.module));
            place.topMargin = dp(row == 0 ? layout.gap : layout.moduleGap);
            column.addView(pair, place);
        }
        if (!open) return;
        LinearLayout.LayoutParams openPlace = new LinearLayout.LayoutParams(-1, dp(layout.open));
        openPlace.setMargins(-dp(layout.pad), dp(layout.pad), -dp(layout.pad), 0);
        column.addView(openCard(), openPlace);
    }
    private PartCard card(BrushPart part, boolean slim, int row) {
        PartCard card = new PartCard(context, lab, samples, layout, part, slim, row, () -> pick.accept(part), changed, () -> render(picked));
        cards.put(part, card);
        return card;
    }
    private void show(ModularBrush parts) {
        ModularStyle style = parts.style;
        String styleNow = style.name;
        if (!styleNow.equals(styleShown)) {
            styleShown = styleNow;
            if (styleButton != null) { styleButton.setText(LabLayout.START_FROM + style.label + " ▾"); styleButton.setContentDescription(LabLayout.START_FROM + style.label); }
        }
        BrushLab.Redraw redraw = BrushLab.redraw(partsShown, parts);
        if (!cards.isEmpty()) partsShown = parts.copy();
        for (Map.Entry<BrushPart, PartCard> card : cards.entrySet()) card.getValue().show(parts, card.getKey() == picked, redraw);
        if (openBody != null) showOpen(parts);
    }

    // Start from and Randomize

    /** "Start from Watercolour ▾" in the head row: it opens the styles over the column; a pick makes a new brush in it. */
    private View styleButton() {
        styleButton = new LabButton(context, 0, LabLayout.START_FROM, false);
        styleButton.setMinHeight(0); styleButton.setMinimumHeight(0);
        styleButton.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        styleButton.setEllipsize(TextUtils.TruncateAt.END);
        styleButton.setOnClickListener(v -> showFlyout(flyout == null));
        return styleButton;
    }
    /** The 15 style chips in a menu's panel over the column, under its head row (ui.js 314); a pick closes it. */
    private void showFlyout(boolean open) {
        if (flyout != null) { box.removeView(flyout); flyout = null; }
        if (!open) return;
        LabUi.Wrap chips = new LabUi.Wrap(context, dp(6));
        ModularBrush parts = lab.parts();
        for (ModularStyle style : ModularStyle.shown()) {
            LabButton chip = styleChip(LabButton.chip(context, style.label), style);
            chip.setMinHeight(dp(44)); chip.setMinimumHeight(dp(44));
            chip.setOn(style == parts.style);
            chips.addView(chip);
        }
        sheet("Start From", chips);
    }
    /**
     * A choice dial as a menu button ({@link LabLayout#menu}: its options don't fit its row, and the card has no room to
     * stack them; Print shading's, Blend's on the Nomad): its option and ▾; a tap opens its options as chips in a panel
     * beside the button ({@link Beside}, the owner's "flyout panel"); a pick sets it and closes it.
     */
    private LabButton menuButton(PartDial dial, String name, String[] options, int current, boolean locked) {
        LabButton button = new LabButton(context, 0, options[current] + " ▾", false);
        button.setTextSize(LabLayout.DENSE_SP); button.setLetterSpacing(0); button.setAllCaps(false);
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        button.setPadding(dp(LabLayout.SEGMENT_PAD), 0, dp(LabLayout.SEGMENT_PAD), 0);
        button.setMinHeight(0); button.setMinimumHeight(0);
        button.setEnabled(!locked);
        button.setOnClickListener(v -> {
            showFlyout(false);
            LabUi.Wrap chips = new LabUi.Wrap(context, dp(6));
            for (int option = 0; option < options.length; option++) {
                int chosen = option;
                LabButton chip = LabButton.chip(context, options[option]);
                chip.setMinHeight(dp(44)); chip.setMinimumHeight(dp(44));
                chip.setOn(option == current);
                chip.setOnClickListener(c -> { beside.close(); if (chosen != current) lab.choose(dial, chosen); changed.run(); });
                chips.addView(chip);
            }
            beside.open(button, panel(name, chips));
        });
        return button;
    }
    /** The flyout: {@code title} and {@code chips} in a 2 dp ink frame over the column, under its head row. */
    private void sheet(String title, View chips) {
        if (flyout != null) { box.removeView(flyout); flyout = null; }
        View sheet = panel(title, chips);
        FrameLayout.LayoutParams place = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        place.setMargins(0, dp(layout.headRow), 0, 0);
        flyout = sheet;
        box.addView(sheet, place);
    }
    /** {@code title} and {@code chips} in a menu's panel ({@link Theme#menu}): the style flyout's, and a menu button's. */
    private View panel(String title, View chips) {
        LinearLayout sheet = LabUi.column(context);
        sheet.setBackground(Theme.menu(context));
        sheet.setPadding(dp(12), dp(10), dp(12), dp(12));
        sheet.setClickable(true);
        sheet.addView(heading(title), new LinearLayout.LayoutParams(-1, dp(28)));
        LinearLayout.LayoutParams under = new LinearLayout.LayoutParams(-1, -2); under.topMargin = dp(6);
        sheet.addView(chips, under);
        return sheet;
    }
    /** {@code chip} for {@code style}: a tap makes a new six-part brush in it (nothing for the brush's own style). */
    private LabButton styleChip(LabButton chip, ModularStyle style) {
        chip.setOnClickListener(v -> { showFlyout(false); lab.pickStyle(style); changed.run(); });
        return chip;
    }
    /**
     * Randomize, big and ink-filled across the lab's foot (the owner, 2026-10-23: "make RANDOMIZE a black button, align dice
     * with text"), its dice beside its word, centred with it as one; Anything Goes beside it, a smaller secondary button,
     * plain words and its shuffle (the owner, 2026-10-24; two equal ink halves until then). Randomize rolls a new brush in
     * the brush's style, Anything Goes in any style (the owner, 2026-10-23: "the way to generate anything goes with any
     * parameters randomly is missing"); both keep what's locked.
     */
    View randomize() {
        LinearLayout row = LabUi.row(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(inkButton("Randomize", R.drawable.ic_ph_dice, v -> lab.randomize()), new LinearLayout.LayoutParams(0, -1, 1));
        LabButton anything = new LabButton(context, R.drawable.ic_ph_shuffle, "Anything Goes", false);
        anything.setContentDescription("Anything Goes, keeping what's locked");
        anything.setOnClickListener(v -> { lab.anythingGoes(); changed.run(); });
        row.addView(anything, LabUi.after(context, -2, layout.randomize, layout.gap));
        return row;
    }
    private View inkButton(String label, int icon, View.OnClickListener roll) {
        LabButton words = new LabButton(context, 0, label, true);
        words.setIcon(icon, Math.min(28, layout.randomize - 18));
        words.setTextSize(16); words.setTypeface(Theme.face(700));
        words.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        // A TextView draws a left icon at its padding, not beside centred words: the button wraps them, centred in an ink bar.
        FrameLayout bar = new FrameLayout(context);
        bar.setBackgroundColor(Theme.INK);
        bar.addView(words, new FrameLayout.LayoutParams(-2, -1, Gravity.CENTER));
        bar.setContentDescription(label + ", keeping what's locked");

        bar.setOnClickListener(v -> { roll.onClick(v); changed.run(); });
        return bar;
    }

    // The open card

    /** The open card (ui.js cardOpen, 289–311): its body under a {@link Theme#FINE} dp ink line (no grey: e-ink), in the rule's room. */
    private View openCard() {
        LinearLayout card = LabUi.column(context);
        LabUi.rule(card, Theme.FINE, Theme.INK);
        LabUi.rule(card, Theme.RULE - Theme.FINE, Theme.PAPER);
        LinearLayout inside = LabUi.row(context);
        inside.setGravity(Gravity.TOP);
        openBody = LabUi.column(context);
        openBody.setPadding(dp(8), 0, dp(8), 0);
        inside.addView(openBody, new LinearLayout.LayoutParams(0, -1, 1));
        card.addView(inside, new LinearLayout.LayoutParams(-1, 0, 1));
        return card;
    }
    /**
     * The open part: its head (glyph, label, lock, die, ✕), then each dial as a row sharing the card's height; the Hand
     * its grid, Response and the Fast switch. Its choices are the swap panel's, beside its module. Takes ink and Plume stay
     * hidden ({@link BrushLab#dials}).
     */
    private void showOpen(ModularBrush parts) {
        BrushPart part = picked;
        String key = openKey(parts, part);
        if (key.equals(rowsShown)) { showLive(); return; }
        rowsShown = key;
        live.clear();
        boolean locked = lab.locked(part);
        openBody.removeAllViews();
        LinearLayout head = PartCard.head(context, part, part.label);
        LabUi.spring(head);
        head.addView(LabButton.lock(context, part.label, locked, () -> { lab.toggleLock(part); render(picked); }), square());
        head.addView(LabButton.die(context, part.label, locked, () -> { lab.rollPart(part); changed.run(); }), square());
        LabButton close = LabButton.icon(context, R.drawable.ic_ph_x, "Close");
        close.setOnClickListener(v -> pick.accept(null));
        head.addView(close, square());
        openBody.addView(head, new LinearLayout.LayoutParams(-1, dp(LabLayout.OPEN_HEAD)));
        openBody.addView(new View(context), new LinearLayout.LayoutParams(1, dp(UNDER_HEAD)));
        if (part == BrushPart.HAND) {
            // Under Response and Fast is: a Spiro's drives (what pressure, speed and tilt set of its shape), then
            // Grey Shift, with its own rows and its set while it is on.
            List<PartDial> under = layout.handRows(parts);
            int rows = under.size(), row = layout.handRow(rows);
            openBody.addView(new HandGrid(context, lab, layout.handName, layout.handCell(rows), changed, () -> render(picked)), new LinearLayout.LayoutParams(-1, -2));
            for (PartDial d : under) openBody.addView(d != null ? dialRow(d) : greySet(parts), new LinearLayout.LayoutParams(-1, dp(row)));
            return;
        }
        List<PartDial> dials = new java.util.ArrayList<>(lab.dials(part));
        dials.removeIf(d -> !layout.shows(parts.choice(part), d));
        if (dials.isEmpty()) {
            // A choice with no dials (Drying's Instant, Smudge's Off) says what it means, as its module does.
            TextView means = LabUi.text(context, ModularWords.noMacro(parts.choice(part)), TypeStyle.CAPTION);
            means.setTypeface(Theme.face(400)); means.setTextColor(Theme.INK);
            openBody.addView(means, new LinearLayout.LayoutParams(-1, -2));
            return;
        }
        int stacked = 0;
        for (PartDial dial : dials) if (stacked(dial)) stacked++;
        int height = layout.dialRow(dials.size(), stacked);
        for (PartDial dial : dials) openBody.addView(dialRow(dial), new LinearLayout.LayoutParams(-1, dp(stacked(dial) ? LabLayout.stackedRow() : height)));
    }
    /** Grey Shift's set: the brush's greys as it lays them, each taken in or left out by a tap ({@link GreySet}). */
    private View greySet(ModularBrush parts) {
        int n = SwatchPicker.size(SwatchPicker.palette(parts));
        int[] tones = new int[n];
        double[] darkness = new double[n];
        boolean[] in = new boolean[n];
        for (int k = 0; k < n; k++) {
            darkness[k] = SwatchPicker.darkness(parts, lab.slots.swatches, k);
            tones[k] = ModularSizes.swatch(parts, darkness[k]);
            in[k] = SwatchPicker.in(parts, k);
        }
        View set = new GreySet(context, tones, darkness, in, !lab.locked(PartDial.SHIFT), k -> { if (lab.toggleGrey(k)) changed.run(); });
        return row(null, set, null, PartDial.SHIFT_GREYS.label, false, null, null);
    }
    /** Whether {@code dial}'s segments lie under its label, not beside it (LabLayout.segmentsBeside). */
    private boolean stacked(PartDial dial) { return layout.stacks(dial, options(dial)); }
    /** {@code dial}'s options as the brush's choice shows them (Opaque's Blend: cover first). */
    private String[] options(PartDial dial) { return lab.parts().part(dial.part).choice.optionsOf(dial); }
    /**
     * What the open card shows for {@code part} of {@code parts} but its sliders' values and the choice's words: the
     * part, its style (the bands), choice and chips, every lock, and each dial's state, and the value of each that
     * isn't a slider (a choice dial's segments, Fast is, a hand cell).
     */
    private String openKey(ModularBrush parts, BrushPart part) {
        ModularBrush.Part p = parts.part(part);
        StringBuilder key = new StringBuilder().append(part).append(' ').append(parts.style.name).append(' ').append(p.choice)
                .append(' ').append(lab.chips(part)).append(' ').append(lab.partLocks.parts).append(' ').append(lab.partLocks.dials);
        for (PartDial d : p.choice.dials) {
            key.append(' ').append(d).append(lab.active(d) ? '+' : '-');
            if (d == PartDial.DIR || d.choice() || part == BrushPart.HAND && d != PartDial.GAIN) key.append(p.get(d));
        }
        return key.toString();
    }
    /** The rows' sliders and values as the brush now has them, each drawn again only if it changed. */
    private void showLive() {
        for (Live l : live) {
            double v = l.value.getAsDouble();
            if (l.slider.value != v) { l.slider.value = v; l.slider.invalidate(); }
            String text = l.text.get();
            if (!text.contentEquals(l.shown.getText())) l.shown.setText(text);
        }
    }
    /**
     * One dial of the open part: Fast is as its switch; a choice dial (Head, Line placement) as segments; else its
     * slider over the choice's range, the style's band hatched. Greyed when it changes nothing now; under a locked
     * part it is locked too, and its own lock can't change.
     */
    private LinearLayout dialRow(PartDial dial) {
        ModularBrush b = lab.parts();
        ModularBrush.Part p = b.part(dial.part);
        boolean locked = lab.locked(dial), active = lab.active(dial);
        String name = p.choice.labelOf(dial);
        double value = p.get(dial);
        Runnable lock = lab.locked(dial.part) ? null : () -> lab.toggleLock(dial), roll = () -> lab.roll(dial);
        // Erasable moves only by hand (no roll changes it, ModularStyle.keepErasable): no die, so no lock either. Resist's is wax or fluid, and rolls.
        if (dial == PartDial.ERASABLE && p.choice != PartChoice.RESIST) { lock = null; roll = null; }
        if (dial == PartDial.DIR) return row(null, fastSwitch((int) value == 1, locked, lab::flipFast), null, name, locked, lock, roll);
        if (dial.choice() && dial != PartDial.DIR && layout.menu(dial, options(dial)))
            return row(label(LabLayout.rowLabel(p.choice, dial), active, locked), menuButton(dial, name, options(dial), (int) value, locked), null, name, locked, lock, roll);
        if (dial.choice() && stacked(dial)) {
            // Its label on a line of its own, the segments under it with the lock and die: they don't fit beside it at 12 sp.
            LinearLayout both = LabUi.column(context);
            TextView label = label(name, active, locked);
            label.setMaxLines(1);
            both.addView(label, new LinearLayout.LayoutParams(-1, 0, 1));
            both.addView(row(null, segments(p.choice.optionsOf(dial), (int) value, locked, option -> lab.choose(dial, option)), null, name, locked, lock, roll),
                    new LinearLayout.LayoutParams(-1, dp(LabLayout.SEGMENT + LabLayout.ROW_RULE)));
            return both;
        }
        if (dial.choice()) return row(label(name, active, locked), segments(p.choice.optionsOf(dial), (int) value, locked, option -> lab.choose(dial, option)), null, name, locked, lock, roll);
        double[] range = p.choice.range(dial);
        TextView shown = value(dial.shown(value, lab.parts().part(dial.part)), active);
        BandSlider slider = slider(new BandSlider(context, range[0], range[1], value, active ? b.style.band(p.choice, dial) : null), locked, shown,
                v -> { lab.slide(dial, v); ModularBrush.Part q = lab.parts().part(dial.part); return dial.shown(q.get(dial), q); }, () -> lab.endSlide(dial));
        slider.id = dial;
        DoubleSupplier now = () -> lab.parts().part(dial.part).get(dial);
        live.add(new Live(slider, shown, now, () -> dial.shown(now.getAsDouble(), lab.parts().part(dial.part))));
        return row(label(name, active, locked), slider, shown, name, locked, lock, roll);
    }

    // Rows

    /**
     * A row (CSS .drow, with the lock moved right): {@code label} ({@link LabLayout#label} wide, or null for {@code
     * middle} to take its place), {@code middle}, {@code value} ({@link LabLayout#value} wide, or null for {@code
     * middle} to take its place), 8 dp, then {@code name}'s lock and die (28 dp each), a 1 dp rule under it. A lock
     * changes no brush, so it only redraws the column; {@code lock} null shows it without letting it change;
     * {@code roll} null leaves both out (a row that shares the next row's).
     */
    private LinearLayout row(TextView label, View middle, TextView value, String name, boolean locked, Runnable lock, Runnable roll) {
        LinearLayout row = LabUi.row(context);
        LayerDrawable ruled = new LayerDrawable(new Drawable[]{new ColorDrawable(Theme.INK), new ColorDrawable(Theme.PAPER)});
        ruled.setLayerInset(1, 0, 0, 0, dp(Theme.FINE));
        row.setBackground(ruled);
        if (label != null) row.addView(label, new LinearLayout.LayoutParams(dp(layout.label), -1));
        LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(0, -2, 1);
        if (label != null) place.leftMargin = dp(4);
        row.addView(middle, place);
        if (value != null) {
            // A fixed height, so a new value never lays the column out again (e-ink).
            LinearLayout.LayoutParams after = new LinearLayout.LayoutParams(dp(layout.value), dp(LabLayout.LOCK)); after.leftMargin = dp(4);
            row.addView(value, after);
        }
        if (roll == null) { row.addView(new View(context), new LinearLayout.LayoutParams(dp(8 + 2 * LabLayout.LOCK), 1)); return row; }
        LinearLayout.LayoutParams gap = square(); gap.leftMargin = dp(8);
        row.addView(LabButton.lock(context, name, locked, lock == null ? null : () -> { lock.run(); render(picked); }), gap);
        row.addView(LabButton.die(context, name, locked, () -> { roll.run(); changed.run(); }), square());
        return row;
    }
    private LinearLayout.LayoutParams square() { return new LinearLayout.LayoutParams(dp(LabLayout.LOCK), dp(LabLayout.LOCK)); }
    /**
     * A row's name: {@link LabLayout#DENSE_SP}, 700, caps, wrapping to two lines, never smaller, never ellipsized
     * (LabLayoutChecks measures them all); a break allowed after an arrow, so "Hold (vine↔compressed)" wraps at the
     * arrow. Grey-800 when locked; grey-400 when it changes nothing now.
     */
    private TextView label(String name, boolean active, boolean locked) {
        TextView label = LabUi.text(context, LabLayout.dialLabel(name), LabLayout.DENSE_SP, 700);
        label.setLetterSpacing(LabLayout.LABEL_TRACKING);
        label.setGravity(Gravity.CENTER_VERTICAL);
        label.setMaxLines(2);
        label.setTextColor(!active ? Theme.GREY_400 : locked ? Theme.GREY_800 : Theme.INK);
        return label;
    }
    /** A row's value: grey, right-aligned, one line, as wide as the widest (LabLayout.VALUE_WIDEST). */
    private TextView value(String shown, boolean active) {
        TextView value = LabUi.text(context, shown, LabLayout.DENSE_SP, 400);
        value.setTextColor(active ? Theme.GREY_800 : Theme.GREY_400);
        value.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        value.setSingleLine(true);
        return value;
    }
    /**
     * {@code slider} wired: pressing it calls {@link BrushLab#startSlide}; moving it, {@code move}, which returns the
     * value {@code shown} says; letting go, {@code stop}, then the lab redraws. Returns it.
     */
    private BandSlider slider(BandSlider slider, boolean locked, TextView shown, DoubleFunction<String> move, Runnable stop) {
        slider.setEnabled(!locked);
        slider.listener = new BandSlider.Listener() {
            @Override public void started() { lab.startSlide(); }
            @Override public void moved(double v) { shown.setText(move.apply(v)); }
            @Override public void stopped() { stop.run(); changed.run(); }
            @Override public void reset() { if (slider.id instanceof PartDial) { lab.resetDial((PartDial) slider.id); changed.run(); } }
        };
        return slider;
    }
    /**
     * A choice dial's options as butted segments (CSS .pseg), the current one inverted, at {@link LabLayout#DENSE_SP}.
     * Each is as wide as its word is long; the row gives them the value column's room too ({@link #row}, no value), or
     * the whole row under the label when that is too narrow ({@link #stacked}).
     */
    private LinearLayout segments(String[] options, int current, boolean locked, IntConsumer pick) {
        LinearLayout segments = LabUi.row(context);
        for (int option = 0; option < options.length; option++) {
            int chosen = option;
            LabButton segment = new LabButton(context, 0, options[option], false);
            segment.setTextSize(LabLayout.DENSE_SP); segment.setLetterSpacing(0); segment.setAllCaps(false);
            segment.setPadding(dp(LabLayout.SEGMENT_PAD), 0, dp(LabLayout.SEGMENT_PAD), 0);
            segment.setMinHeight(0); segment.setMinimumHeight(0); segment.setMinWidth(0); segment.setMinimumWidth(0);
            segment.setOn(option == current);
            segment.setEnabled(!locked);
            segment.setOnClickListener(v -> { if (chosen != current) { pick.accept(chosen); changed.run(); } });
            LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(0, dp(LabLayout.SEGMENT - 2), options[option].length());
            if (option > 0) place.leftMargin = -dp(2);
            segments.addView(segment, place);
        }
        return segments;
    }
    /**
     * The Fast switch (the Hand's Fast is): the switch and what it does, "fast =
     * thinner" (off), like a nib running dry, or "fast = thicker" (on), like the eraser. No arrows. A tap anywhere on it flips it.
     */
    private LinearLayout fastSwitch(boolean thicker, boolean locked, Runnable flip) {
        LinearLayout row = LabUi.row(context);
        String says = "Fast strokes " + (thicker ? "thicker" : "thinner");
        SquareSwitch toggle = new SquareSwitch(context, thicker);
        toggle.setEnabled(!locked);
        row.addView(toggle);
        TextView text = LabUi.text(context, says, TypeStyle.CAPTION);
        text.setPadding(dp(10), 0, 0, 0);
        if (locked) text.setTextColor(Theme.GREY_800);
        row.addView(text);
        row.setContentDescription(says);
        row.setEnabled(!locked);
        row.setOnClickListener(v -> { flip.run(); changed.run(); });
        return row;
    }
    /** A panel's label, in its head. */
    private TextView heading(String text) {
        TextView heading = LabUi.heading(context, text);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        return heading;
    }
    private int dp(float value) { return Ui.dp(context, value); }
}
