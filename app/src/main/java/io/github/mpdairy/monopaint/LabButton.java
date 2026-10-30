package io.github.mpdairy.monopaint;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.widget.TextView;

/**
 * A Brush Lab control, as the mockup's .btn and .chip: an optional icon and an optional label ({@link TypeStyle#LABEL}),
 * square. Scribbler Style's two kinds (the owner, 2026-10-23, "shade before line"): the primary, {@code solid}, ink-filled
 * with on-ink words (Save, a dialog's act); every other one plain ink words or an icon, no frame and no fill, and ink-filled
 * only while turned on (a chosen chip, a lock, the mode). Grey-400 when disabled (on-ink on grey-400 when also on). No
 * ripple: e-ink.
 */
@SuppressLint({"ViewConstructor", "AppCompatCustomView"})
final class LabButton extends TextView {
    private final boolean solid;
    private boolean on, quiet;

    LabButton(Context context, int icon, String label, boolean solid) {
        super(context);
        this.solid = solid;
        Theme.style(this, TypeStyle.LABEL);
        setGravity(Gravity.CENTER); setSingleLine(true);
        setPadding(dp(12), 0, dp(12), 0); setMinHeight(dp(44)); setMinWidth(dp(44)); setMinimumHeight(dp(44)); setMinimumWidth(dp(44));
        setText(label == null ? "" : label);
        if (label != null) setContentDescription(label);
        if (icon != 0) setIcon(icon, label == null ? 24 : 22);
        refresh();
    }
    /** A chip: a small choice, plain words, ink-filled while chosen, its label as written ({@link TypeStyle#CAPTION}, 700). */
    static LabButton chip(Context context, String label) {
        LabButton chip = new LabButton(context, 0, label, false);
        chip.setAllCaps(false); chip.setLetterSpacing(0); chip.setTextSize(TypeStyle.CAPTION.sp); chip.setTypeface(Theme.face(700));
        chip.setPadding(chip.dp(8), 0, chip.dp(8), 0);
        chip.setMinHeight(chip.dp(32)); chip.setMinimumHeight(chip.dp(32)); chip.setMinWidth(0); chip.setMinimumWidth(0);
        return chip;
    }
    /**
     * Back, at a bar's left (Brush Lab's, the Tools page's, the card's choosers): the caret and "Back", never an icon
     * alone, plain as the notebook screen's ‹ Notebooks (the owner, 2026-10-21: "‹ Label" everywhere, no box), 44 dp tall
     * ({@link LabLayout#backWidth}).
     */
    static LabButton back(Context context) { return back(context, "Back"); }
    /** Back, its word the place it goes ("‹ Tools": Brush Lab opened from the Tools page; the audit's rule). */
    static LabButton back(Context context, String where) {
        return new LabButton(context, R.drawable.ic_ph_caret_left, where, false);
    }
    /** A section header's icon button, 18 dp (the mockup's .pg: a page's ‹ and ›, ✕). */
    static LabButton headIcon(Context context, int icon, String description) {
        LabButton button = new LabButton(context, icon, null, false);
        button.setContentDescription(description);
        button.setIcon(icon, 18);
        button.setMinHeight(0); button.setMinimumHeight(0);
        return button;
    }
    /** A small frameless icon button (CSS .icn): a lock, a die, ✕. Inverted while on. */
    static LabButton icon(Context context, int icon, String description) {
        LabButton button = new LabButton(context, 0, null, false);
        button.setIcon(icon, 16);
        button.setPadding(0, 0, 0, 0);
        button.setMinHeight(0); button.setMinimumHeight(0); button.setMinWidth(0); button.setMinimumWidth(0);
        button.setContentDescription(description);
        return button;
    }
    /** {@code name}'s lock, closed and inverted while {@code locked}; {@code toggle} null shows it without letting it change. */
    static LabButton lock(Context context, String name, boolean locked, Runnable toggle) {
        LabButton lock = icon(context, R.drawable.ic_ph_lock_open, null);
        showLock(lock, name, locked);
        lock.setEnabled(toggle != null);
        if (toggle != null) lock.setOnClickListener(v -> toggle.run());
        return lock;
    }
    /** A lock made by {@link #lock}, showing {@code locked}. */
    static void showLock(LabButton lock, String name, boolean locked) {
        lock.setIcon(locked ? R.drawable.ic_ph_lock : R.drawable.ic_ph_lock_open, 16);
        lock.setOn(locked);
        lock.setContentDescription((locked ? "Unlock " : "Lock ") + name);
    }
    /** {@code name}'s die; it can't roll what is locked. */
    static LabButton die(Context context, String name, boolean locked, Runnable roll) {
        LabButton die = icon(context, R.drawable.ic_ph_dice, "Roll " + name);
        die.setEnabled(!locked);
        die.setOnClickListener(v -> roll.run());
        return die;
    }
    /**
     * A chip showing an icon alone, {@code sideDp} square, ink-filled while chosen: its word ({@code label}) is what a
     * screen reader says and what a hold shows (the owner, 2026-10-20: "icons instead of text", a long press to discover).
     */
    static LabButton iconChip(Context context, Drawable icon, String label, int sideDp) {
        LabButton chip = new LabButton(context, 0, null, false);
        chip.setPadding(0, 0, 0, 0);
        chip.setMinWidth(chip.dp(sideDp)); chip.setMinimumWidth(chip.dp(sideDp)); chip.setMinHeight(chip.dp(sideDp)); chip.setMinimumHeight(chip.dp(sideDp));
        chip.setIcon(icon, Math.min(22, sideDp - 14));
        chip.setContentDescription(label); chip.setTooltipText(label);
        return chip;
    }
    static LabButton iconChip(Context context, int icon, String label, int sideDp) { return iconChip(context, context.getDrawable(icon), label, sideDp); }
    /** As a menu's words ({@link TypeStyle#MENU}, as written, not caps), every menu's one size; its fill and ink stay. */
    LabButton menu() {
        setTextSize(TypeStyle.MENU.sp); setTypeface(Theme.face(TypeStyle.MENU.weight)); setLetterSpacing(TypeStyle.MENU.tracking); setAllCaps(false);
        return this;
    }
    /**
     * As small grey words ({@link TypeStyle#CAPTION} as written, grey-800, no frame): the Tools page's scratch pad's
     * Clear and the words beside it (the owner's mockup, 2026-10-28).
     */
    LabButton quiet() {
        quiet = true;
        setTextSize(TypeStyle.CAPTION.sp); setTypeface(Theme.face(TypeStyle.CAPTION.weight)); setLetterSpacing(0); setAllCaps(false);
        setPadding(dp(8), 0, dp(8), 0);
        setMinHeight(0); setMinimumHeight(0); setMinWidth(0); setMinimumWidth(0);
        refresh();
        return this;
    }
    /** The icon, {@code sizeDp} square, before the label. */
    void setIcon(int icon, int sizeDp) { setIcon(getContext().getDrawable(icon), sizeDp); }
    private void setIcon(Drawable icon, int sizeDp) {
        Drawable drawable = icon.mutate();
        drawable.setBounds(0, 0, dp(sizeDp), dp(sizeDp));
        setCompoundDrawables(drawable, null, null, null);
        setCompoundDrawablePadding(getText().length() == 0 ? 0 : dp(sizeDp > 30 ? 14 : 8));
        refresh();
    }
    /**
     * An icon alone (no label) centred across the button. TextView draws a left drawable at the left padding, not in the
     * middle, so with the 12 dp padding an icon-only button wider than the icon and its padding had its icon off to the
     * left: the drawing screen's Brush Lab button (60 dp wide on the Manta, its flask 6 dp left of centre), the page
     * buttons. The background is drawn before {@code onDraw}, so only the icon moves.
     */
    @Override protected void onDraw(android.graphics.Canvas canvas) {
        Drawable icon = getCompoundDrawables()[0];
        if (icon == null || getText().length() != 0) { super.onDraw(canvas); return; }
        int shift = (getWidth() - icon.getBounds().width()) / 2 - getPaddingLeft();
        canvas.save();
        canvas.translate(shift, 0);
        super.onDraw(canvas);
        canvas.restore();
    }
    /** Turned on or off; drawn again only when that changes (e-ink). */
    void setOn(boolean on) { if (on != this.on) { this.on = on; refresh(); } }
    boolean on() { return on; }
    @Override public void setEnabled(boolean enabled) { if (enabled != isEnabled()) { super.setEnabled(enabled); refresh(); } }

    private void refresh() {
        // Disabled, a chosen chip or a lock that is on stays filled, in grey, so a locked part still shows its choice.
        boolean inverted = solid || on;
        int ink = inverted ? Theme.ON_INK : !isEnabled() ? Theme.GREY_400 : quiet ? Theme.GREY_800 : Theme.INK;
        setTextColor(ink);
        for (Drawable d : getCompoundDrawables()) if (d != null) d.setTint(ink);
        setBackground(inverted ? Theme.box(getContext(), isEnabled() ? Theme.INK : Theme.GREY_400, 0, 0) : null);
    }
    private int dp(float value) { return Ui.dp(getContext(), value); }
}
