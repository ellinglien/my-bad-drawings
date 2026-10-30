package io.github.mpdairy.monopaint;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Collections;
import java.util.Locale;

/**
 * One part of a six-part brush in Brush Lab's column, as a module of the owner's B5e design in Scribbler Style (the
 * owner, 2026-10-23): a paper block in a {@link Theme#FINE} dp ink frame, where grey-200 was (the owner: "eink grey
 * doesn't quite work the same way"), no pattern strip; a head as tall as its row's
 * longer description needs (the part's name in bold caps, its glyph in ink at the right, as tall as the name, and under
 * them, across the head, what the part holds, {@link ModularWords#inside}; the name alone on the Nomad); under it the
 * choice, bold, centred, 16 sp down to 13, in as many lines as its row budgets ({@link
 * LabLayout#rowChoice}); a sample of what the part adds ({@link WildSample#part}, drawn in the background) taking the
 * height the head leaves; and one slider over the choice's macro dial with its two end words in grey-800 under it. A
 * choice without a macro dial (Drying's Instant, Smudge's Off) says what it means in the sample's and slider's place
 * (Filled Shape in the slider's, under its sample).
 * The slider row has the part's lock and die. A slim module (while a part is
 * open) shows its glyph, name, lock and choice only. Picked (its swap strip or its open card showing), it has the tiles'
 * one mark, a {@link SwatchFace#TAB} dp ink tab at its foot. A tap anywhere but the slider and the buttons calls {@code pick}; the slider is an Undo step, as the
 * lab's others. {@link #show} updates it in place; while the slider moves nothing else is drawn.
 */
@SuppressLint("ViewConstructor")
final class PartCard extends FrameLayout {
    /** Each part's glyph (ui.js GLYPH). */
    static int glyph(BrushPart part) {
        switch (part) {
            case TIP: return R.drawable.ic_part_tip;
            case MEDIUM: return R.drawable.ic_part_medium;
            case CARRY: return R.drawable.ic_part_carry;
            case PAPER: return R.drawable.ic_part_paper;
            case LIFE: return R.drawable.ic_part_life;
            default: return R.drawable.ic_part_hand;
        }
    }
    /** The glyph, 16 dp, then the part's label in bold caps at {@link LabLayout#DENSE_SP}: a slim module's head and the open card's. */
    static LinearLayout head(Context context, BrushPart part, String label) {
        LinearLayout head = LabUi.row(context);
        ImageView glyph = new ImageView(context);
        glyph.setImageResource(glyph(part));
        glyph.setColorFilter(Theme.INK);
        head.addView(glyph, new LinearLayout.LayoutParams(Ui.dp(context, 16), Ui.dp(context, 16)));
        TextView text = LabUi.text(context, label.toUpperCase(Locale.ROOT), LabLayout.DENSE_SP, 700);
        text.setLetterSpacing(.07f); text.setSingleLine(true);
        LinearLayout.LayoutParams after = new LinearLayout.LayoutParams(-2, -2); after.leftMargin = Ui.dp(context, 6);
        head.addView(text, after);
        return head;
    }
    /**
     * {@code part}'s choice in words as its module says them: Control's strongest responses, as many as the layout names
     * (a slim module's, {@code slim}, its one strongest); a break allowed after an arrow.
     */
    static String words(ModularBrush b, BrushPart part, LabLayout layout, boolean slim) {
        String words = part == BrushPart.HAND ? ModularWords.handWords(b, slim ? 1 : layout.handResponses()) : ModularWords.choiceWords(b, part);
        return words.replace("→", "→​");
    }

    private final BrushLab lab;
    private final BrushPart part;
    private final LabLayout layout;
    private final Runnable changed;
    private final boolean slim;
    private final int row;
    private final TextView choice;
    /** The lock and die (a slim module has no die). */
    private final LabButton lock, die;
    private final LoopCard sample;
    private final View sampleBox;
    private final LinearLayout macro;
    private final Paint ink = new Paint();
    private BandSlider slider;
    private PartChoice macroShown;
    private boolean picked;

    /**
     * A module for {@code part} as {@code layout} sizes it, in row {@code row} (0–2) of the column: its head and
     * choice as tall as the row's ({@link LabLayout#rowHead}, {@link LabLayout#rowChoice}); {@code slim}, the glyph,
     * name, lock and choice only. Its lock calls {@code relocked} after it toggles.
     */
    PartCard(Context context, BrushLab lab, SampleQueue samples, LabLayout layout, BrushPart part, boolean slim, int row, Runnable pick, Runnable changed, Runnable relocked) {
        super(context);
        this.lab = lab; this.part = part; this.layout = layout; this.slim = slim; this.row = row; this.changed = changed;
        lock = LabButton.lock(context, part.label, false, () -> { lab.toggleLock(part); relocked.run(); });
        die = slim ? null : LabButton.die(context, part.label, false, () -> { lab.rollPart(part); changed.run(); });
        setWillNotDraw(false);
        setBackground(Theme.menu(context));
        setOnClickListener(v -> pick.run());
        // The border's room stays; the pattern strip's goes to the words (LabLayout still budgets without it).
        int rule = dp(LabLayout.MODULE_RULE);
        LinearLayout body = LabUi.column(context);
        LayoutParams bodyPlace = new LayoutParams(-1, -1);
        bodyPlace.setMargins(rule, rule, rule, rule);
        addView(body, bodyPlace);
        if (slim) {
            choice = LabUi.text(context, "", LabLayout.DENSE_SP, 700);
            choice.setMaxLines(layout.slimLines);
            body.setPadding(dp(6), dp(4), dp(6), dp(4));
            LinearLayout line = head(context, part, part.label);
            LabUi.spring(line);
            line.addView(lock, new LinearLayout.LayoutParams(dp(LabLayout.LOCK), dp(LabLayout.LOCK)));
            body.addView(line, new LinearLayout.LayoutParams(-1, dp(LabLayout.LOCK)));
            choice.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams under = new LinearLayout.LayoutParams(-1, dp(layout.slimChoice)); under.topMargin = dp(2);
            body.addView(choice, under);
            sample = null; sampleBox = null; macro = null;
            return;
        }
        body.addView(head(context, layout, part), new LinearLayout.LayoutParams(-1, dp(layout.rowHead(row))));
        // The head's rule went (shade before line); its room stays, so the rows keep LabLayout's heights.
        body.addView(new View(context), new LinearLayout.LayoutParams(-1, dp(LabLayout.HEAD_RULE)));
        LinearLayout lower = LabUi.column(context);
        lower.setPadding(dp(layout.modulePad), dp(layout.rowGap), dp(layout.modulePad), dp(layout.modulePad));
        body.addView(lower, new LinearLayout.LayoutParams(-1, 0, 1));
        // The choice's words wrap, never cut: as many lines as the row budgets, smaller if they must (LabLayoutChecks tries them all).
        choice = LabUi.text(context, "", LabLayout.CHOICE_SP, 700);
        int lines = layout.choiceLines(part);
        choice.setMaxLines(lines);
        LabUi.autoSize(choice, (int) LabLayout.SMALL_SP, (int) LabLayout.CHOICE_SP);
        choice.setGravity(Gravity.CENTER);
        lower.addView(choice, new LinearLayout.LayoutParams(-1, dp(layout.rowChoice(row))));
        // The sample, the module's width, and whatever height the head leaves.
        sample = new LoopCard(context, samples, Collections.emptyList(), null);
        sampleBox = sample;
        LinearLayout.LayoutParams samplePlace = new LinearLayout.LayoutParams(-1, 0, 1); samplePlace.topMargin = dp(layout.rowGap);
        lower.addView(sample, samplePlace);
        macro = LabUi.column(context);
        lower.addView(macro, macroPlace(true));
    }
    /** The slider and its end words, the row's height; or (no slider) what the choice means, in the sample's place too. */
    private LinearLayout.LayoutParams macroPlace(boolean slider) {
        LinearLayout.LayoutParams place = slider ? new LinearLayout.LayoutParams(-1, dp(layout.rowMacro(row))) : new LinearLayout.LayoutParams(-1, 0, 1);
        place.topMargin = dp(layout.rowGap);
        return place;
    }

    /**
     * A module's white head: the part's name and its glyph in a row, what the part holds under them (the Manta); the
     * same padding all round, so the room under the description matches the room over the name.
     */
    private static LinearLayout head(Context context, LabLayout layout, BrushPart part) {
        LinearLayout head = LabUi.column(context);
        int pad = Ui.dp(context, layout.modulePad);
        head.setPadding(pad, pad, pad, pad);
        LinearLayout top = LabUi.row(context);
        TextView name = LabUi.text(context, part.label.toUpperCase(Locale.ROOT), LabLayout.NAME_SP, 700);
        name.setLetterSpacing(.08f); name.setSingleLine(true);
        top.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        ImageView glyph = new ImageView(context);
        glyph.setImageResource(glyph(part));
        glyph.setColorFilter(Theme.INK);
        LinearLayout.LayoutParams glyphPlace = new LinearLayout.LayoutParams(Ui.dp(context, layout.glyph), Ui.dp(context, layout.glyph));
        glyphPlace.leftMargin = Ui.dp(context, layout.glyphGap);
        top.addView(glyph, glyphPlace);
        head.addView(top, new LinearLayout.LayoutParams(-1, Ui.dp(context, layout.glyph)));
        if (!layout.described) return head;
        TextView inside = LabUi.text(context, ModularWords.inside(part), TypeStyle.CAPTION);
        inside.setTypeface(Theme.face(400)); inside.setTextColor(Theme.INK);
        LinearLayout.LayoutParams under = new LinearLayout.LayoutParams(-1, -2); under.topMargin = Ui.dp(context, LabLayout.NAME_GAP);
        head.addView(inside, under);
        return head;
    }

    /**
     * Shows the part of {@code b}, picked or not, changing only what changed; its sample is drawn again only when
     * {@code redraw} says this part changed ({@link BrushLab#redraw}), as a part's sample shows the whole brush.
     */
    void show(ModularBrush b, boolean picked, BrushLab.Redraw redraw) {
        String words = words(b, part, layout, slim);
        if (!words.contentEquals(choice.getText())) choice.setText(words);
        setContentDescription(part.label + ": " + ModularWords.choiceWords(b, part));
        if (picked != this.picked) { this.picked = picked; invalidate(); }
        boolean locked = lab.locked(part);
        if (lock.on() != locked) LabButton.showLock(lock, part.label, locked);
        if (die != null && die.isEnabled() == locked) die.setEnabled(!locked);
        if (slim) return;
        if (redraw.part(part)) sample.setSample(part + " " + b.key(), () -> { ModularBrush copy = b.copy(); return (w, h) -> WildSample.part(copy, part, w, h); });
        ModularBrush.Part p = b.part(part);
        if (p.choice != macroShown) { macroShown = p.choice; renderMacro(p.choice); }
        if (slider != null && slider.value != p.get(p.choice.macro)) { slider.value = p.get(p.choice.macro); slider.invalidate(); }
        // A locked macro dial can't slide (ui.js 229).
        if (slider != null && slider.isEnabled() == lab.locked(p.choice.macro)) { slider.setEnabled(!lab.locked(p.choice.macro)); slider.invalidate(); }
    }

    /**
     * The slider row (the lock and die after it) and the choice's two words under it, at its ends (the
     * second on a line of its own where they don't fit side by side); for a choice without a macro dial, what it
     * means in the sample's and the slider's place.
     */
    private void renderMacro(PartChoice c) {
        macro.removeAllViews();
        slider = null;
        LinearLayout row = LabUi.row(getContext());
        boolean none = c.macro == null, sampled = !none || c == PartChoice.SHAPE;
        // Filled Shape has no dial but shows its sample, a filled blob, its words in the slider's place.
        sampleBox.setVisibility(sampled ? View.VISIBLE : View.GONE);
        macro.setLayoutParams(macroPlace(sampled));
        if (none) {
            // 12 sp beside the lock and die, wrapping (LabLayoutChecks).

            TextView words = LabUi.text(getContext(), ModularWords.noMacro(c), TypeStyle.CAPTION);
            words.setTypeface(Theme.face(400)); words.setTextColor(Theme.INK); words.setGravity(Gravity.CENTER_VERTICAL);
            words.setTextSize(layout.macroSp());
            row.addView(words, new LinearLayout.LayoutParams(0, -1, 1));
            addButtons(row);
            macro.addView(row, new LinearLayout.LayoutParams(-1, -1));
            return;
        }
        double[] range = c.range(c.macro);
        slider = new BandSlider(getContext(), range[0], range[1], range[0], null);
        slider.setContentDescription(part.label + ": " + c.words[0] + " to " + c.words[1]);
        slider.listener = new BandSlider.Listener() {
            @Override public void started() { lab.startSlide(); }
            @Override public void moved(double v) { lab.slide(c.macro, v); }
            @Override public void stopped() { lab.endSlide(c.macro); changed.run(); }
        };
        row.addView(slider, new LinearLayout.LayoutParams(0, -1, 1));
        addButtons(row);
        macro.addView(row, new LinearLayout.LayoutParams(-1, dp(layout.sliderHeight)));
        if (layout.endLines(part) == 1) {
            LinearLayout ends = LabUi.row(getContext());
            ends.addView(word(c.words[0], Gravity.START));
            LabUi.spring(ends);
            ends.addView(word(c.words[1], Gravity.END));
            macro.addView(ends, new LinearLayout.LayoutParams(-1, 0, 1));
            return;
        }
        macro.addView(word(c.words[0], Gravity.START), new LinearLayout.LayoutParams(-1, 0, 1));
        macro.addView(word(c.words[1], Gravity.END), new LinearLayout.LayoutParams(-1, 0, 1));
    }
    /** The lock and die after the slider (or what a choice without one means). */
    private void addButtons(LinearLayout row) {
        for (LabButton button : new LabButton[]{lock, die}) {
            if (button.getParent() != null) ((ViewGroup) button.getParent()).removeView(button);
            LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(dp(LabLayout.LOCK), dp(LabLayout.LOCK));
            if (button == lock) place.leftMargin = dp(4);
            row.addView(button, place);
        }
    }
    /**
     * An end word, a caption in ink (the owner, 2026-10-21: Brush Lab's words legible, no grey), a little in from the
     * card's border.
     */
    private TextView word(String text, int gravity) {
        TextView word = LabUi.text(getContext(), text, TypeStyle.CAPTION);
        word.setTypeface(Theme.face(400)); word.setTextColor(Theme.INK); word.setSingleLine(true);
        word.setPadding(dp(LabText.CAPTION_INSET), 0, dp(LabText.CAPTION_INSET), 0);
        word.setGravity(gravity | Gravity.CENTER_VERTICAL);
        return word;
    }

    /** Picked: the ink tab at its foot, over everything. */
    @Override protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (!picked) return;
        int w = getWidth(), h = getHeight(), inset = dp(SwatchFace.TAB_INSET);
        ink.setColor(Theme.INK);
        canvas.drawRect(inset, h - dp(SwatchFace.TAB), w - inset, h, ink);
    }
    private int dp(float value) { return Ui.dp(getContext(), value); }
}
