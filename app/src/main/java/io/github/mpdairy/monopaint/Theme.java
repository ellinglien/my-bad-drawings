package io.github.mpdairy.monopaint;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Layout;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

/**
 * Inkwash's one look: the owner's Inkwash Design Style (docs/DESIGN.md; once "Daily Puzzle Style",
 * docs/archive/superpowers/plans/2026-10-15-design-pass.md), its tokens converted from 300 ppi template pixels at 1.875 px a
 * dp. Every colour, stroke, type style and the typeface Inkwash's screens use come from here; nothing else names one.
 * The typeface is the app theme's font too (res/font/ukulele_family.xml, res/values/styles.xml), so what MonoPaint and
 * the system build (a menu, a dialog, a toast) draws in it as well; where Inkwash shows one of MonoPaint's dialogs or
 * menus, {@link #restyle} also gives it Inkwash's weights and ink once it is built (docs/INTEGRATION.md).
 */
final class Theme {
    private Theme() { }

    /**
     * The six greys, and nothing else: they sit on the e-ink panel's 16-step grid, so they never dither. Paper; grey-200,
     * the faintest tint; grey-400, lines between cards and slots, and what is disabled (2 dp at least); grey-600, a mid
     * fill; grey-800, secondary text; ink: text, outlines, the selected fill. On ink, white.
     */
    static final int PAPER = 0xffffffff, GREY_200 = 0xffdddddd, GREY_400 = 0xffbbbbbb, GREY_600 = 0xff888888, GREY_800 = 0xff555555,
            INK = 0xff000000, ON_INK = PAPER;
    /**
     * Strokes, dp: fine (2 px, the thinnest, ink only), base (3 px: buttons, borders, rules, any grey line), a bar's or a
     * module head's rule, heavy (6 px: the one box that matters most, the selected slot).
     */
    static final float FINE = 1, BASE = 2, RULE = 3, HEAVY = 4;

    /**
     * Ukulele, the one family (the owner's, CC BY 3.0; docs/DESIGN.md), in its two weights, 400 and 700 (no italics): a
     * weight up to 500 draws 400, from 600 up 700. Bundled in res/font, made by scripts/ukulele_font.py.
     */
    private static Typeface regular, bold;
    private static Resources resources;
    /** Where the font is read from: each activity calls it before building a view. */
    static void fonts(Context context) { resources = context.getApplicationContext().getResources(); }
    /** Ukulele at {@code weight} (400 or 700; 500 draws 400, 600 700); the system's monospace if the font can't be read. */
    static synchronized Typeface face(int weight) {
        boolean heavy = weight >= 600;
        Typeface face = heavy ? bold : regular;
        if (face != null) return face;
        try {
            // iA Writer Duo draws what Ukulele lacks (% & * = ...), then the system's (▾ ✕); before Android 10, the system's alone.
            if (resources != null && android.os.Build.VERSION.SDK_INT >= 29) {
                android.graphics.fonts.Font ukulele = new android.graphics.fonts.Font.Builder(resources, heavy ? R.font.ukulele_bold : R.font.ukulele).build(),
                        duo = new android.graphics.fonts.Font.Builder(resources, heavy ? R.font.ia_writer_duo_bold : R.font.ia_writer_duo_regular).build();
                face = new Typeface.CustomFallbackBuilder(new android.graphics.fonts.FontFamily.Builder(ukulele).build())
                        .addCustomFallback(new android.graphics.fonts.FontFamily.Builder(duo).build())
                        .setStyle(new android.graphics.fonts.FontStyle(heavy ? 700 : 400, android.graphics.fonts.FontStyle.FONT_SLANT_UPRIGHT)).build();
            } else if (resources != null) face = Typeface.create(resources.getFont(R.font.ukulele_family), heavy ? Typeface.BOLD : Typeface.NORMAL);
        } catch (RuntimeException | java.io.IOException e) { face = null; }
        if (face == null) face = Typeface.create(Typeface.MONOSPACE, heavy ? Typeface.BOLD : Typeface.NORMAL);
        if (heavy) bold = face; else regular = face;
        return face;
    }
    private static Typeface magnet;
    /**
     * The fridge magnets' words, and nothing else: Roboto Mono Bold, Paintmageddon's tile face (res/font, SIL OFL 1.1;
     * {@link MagnetTiles}); the system's bold monospace if the font can't be read.
     */
    static synchronized Typeface magnetFace() {
        if (magnet != null) return magnet;
        try { if (resources != null) magnet = resources.getFont(R.font.roboto_mono_bold); }
        catch (RuntimeException e) { magnet = null; }
        if (magnet == null) magnet = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD);
        return magnet;
    }
    /**
     * {@code view} in {@code type}: its size, weight and tracking, caps for a label, in ink; no font padding, and wrapping
     * at spaces only, never hyphenated (as {@link LabText#wrap} counts it).
     */
    static <T extends TextView> T style(T view, TypeStyle type) {
        view.setTextSize(type.sp); view.setTypeface(face(type.weight)); view.setLetterSpacing(type.tracking);
        view.setAllCaps(type.caps()); view.setTextColor(INK); view.setIncludeFontPadding(false);
        view.setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE); view.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE);
        return view;
    }

    /** A square {@code fill} box with a {@code stroke}-dp outline in {@code line} (0: none). */
    static GradientDrawable box(Context context, int fill, float stroke, int line) {
        GradientDrawable box = new GradientDrawable();
        box.setColor(fill);
        if (stroke > 0) box.setStroke(Ui.dp(context, stroke), line);
        return box;
    }
    /**
     * A group's box, a grey step and no line (Scribbler Style's "shade before line", the owner, 2026-10-23): grey-200,
     * for a sheet, a mode switch, a field on paper, a note; paper blocks sit on it.
     */
    static GradientDrawable box(Context context) { return box(context, GREY_200, 0, 0); }

    /**
     * A menu's or popup's panel over the page (the owner's option A, 2026-10-23: "i am worried they will leave e-ink
     * ghosts"): paper with a {@link #FINE} dp ink edge, no grey anywhere in it.
     */
    static GradientDrawable menu(Context context) { return box(context, PAPER, FINE, INK); }
    /**
     * {@code row} as a menu's row: its words in {@link TypeStyle#MENU}, in ink; {@code chosen}, bold, with the bar's
     * {@link #HEAVY} dp ink bar down its right side, {@link #BASE} dp in from its edge, as the side bar marks the tool in
     * use. Its background is the mark's, so a row goes on the menu's paper.
     */
    static <T extends TextView> T menuRow(T row, boolean chosen) {
        style(row, TypeStyle.MENU);
        if (chosen) row.setTypeface(face(700));
        row.setBackground(chosen ? marked(row.getContext()) : null);
        return row;
    }
    /** A chosen menu row's or cell's background: paper, the bar's {@link #HEAVY} dp ink bar down its right side, {@link #BASE} dp in. */
    static Drawable marked(Context context) {
        android.graphics.drawable.LayerDrawable mark = new android.graphics.drawable.LayerDrawable(new Drawable[]{
                new android.graphics.drawable.ColorDrawable(PAPER), new android.graphics.drawable.ColorDrawable(INK)});
        mark.setLayerGravity(1, android.view.Gravity.END | android.view.Gravity.FILL_VERTICAL);
        mark.setLayerWidth(1, Ui.dp(context, HEAVY));
        mark.setLayerInsetEnd(1, Ui.dp(context, BASE));
        return mark;
    }

    /** A chosen tab's background (the Tools page's Library, 2026-10-28): paper, a {@link #RULE} dp ink line along its foot. */
    static Drawable underlined(Context context) {
        android.graphics.drawable.LayerDrawable mark = new android.graphics.drawable.LayerDrawable(new Drawable[]{
                new android.graphics.drawable.ColorDrawable(PAPER), new android.graphics.drawable.ColorDrawable(INK)});
        mark.setLayerGravity(1, android.view.Gravity.BOTTOM | android.view.Gravity.FILL_HORIZONTAL);
        mark.setLayerHeight(1, Ui.dp(context, RULE));
        return mark;
    }
    /** A rounded box: {@code fill}, a {@code stroke}-dp {@code line} edge (0: none), {@code radius} dp corners (the Tools page's scratch pad, Brush Lab button and chips). */
    static GradientDrawable rounded(Context context, int fill, float stroke, int line, float radius) {
        GradientDrawable box = box(context, fill, stroke, line);
        box.setCornerRadius(Ui.dp(context, radius));
        return box;
    }

    /** {@code icon} in {@code color}: an icon on ink, or a disabled one in grey-400 (no transparency: e-ink). */
    static Drawable tinted(Context context, int icon, int color) {
        Drawable drawable = context.getDrawable(icon).mutate();
        drawable.setTint(color);
        return drawable;
    }

    /**
     * The owner's drawn shape {@code shape} (a traced vector, 2026-10-26) stretched to {@code width × height} px, ink and
     * clear only: the vector's anti-aliased edge cut at half, so it changes in the panel's binary update, no grey.
     */
    static android.graphics.Bitmap shape(Context context, int shape, int width, int height) {
        android.graphics.Bitmap mask = android.graphics.Bitmap.createBitmap(Math.max(1, width), Math.max(1, height), android.graphics.Bitmap.Config.ARGB_8888);
        Drawable art = context.getDrawable(shape);
        art.setBounds(0, 0, mask.getWidth(), mask.getHeight());
        art.draw(new android.graphics.Canvas(mask));
        int[] px = new int[mask.getWidth() * mask.getHeight()];
        mask.getPixels(px, 0, mask.getWidth(), 0, 0, mask.getWidth(), mask.getHeight());
        for (int k = 0; k < px.length; k++) px[k] = px[k] >>> 24 >= 128 ? INK : 0;
        mask.setPixels(px, 0, mask.getWidth(), 0, 0, mask.getWidth(), mask.getHeight());
        return mask;
    }

    /**
     * {@code dialog} (a MonoPaint dialog Inkwash shows) in Inkwash's look: a square grey-200 box with no outline, its
     * text {@link #restyle}d; its title then 700 and its message 400 whatever MonoPaint made them, so every dialog has
     * the one hierarchy; its act (the positive button) ink-filled, Cancel and the rest plain words.
     */
    static void dialog(android.app.AlertDialog dialog) {
        android.view.Window window = dialog.getWindow();
        if (window == null) return;
        window.setBackgroundDrawable(box(dialog.getContext()));
        View decor = window.getDecorView();
        restyle(decor);
        View title = decor.findViewById(decor.getResources().getIdentifier("alertTitle", "id", "android"));
        if (title instanceof TextView) ((TextView) title).setTypeface(face(700));
        View message = decor.findViewById(android.R.id.message);
        if (message instanceof TextView) ((TextView) message).setTypeface(face(400));
        for (int which : new int[]{android.app.AlertDialog.BUTTON_POSITIVE, android.app.AlertDialog.BUTTON_NEGATIVE, android.app.AlertDialog.BUTTON_NEUTRAL}) {
            android.widget.Button button = dialog.getButton(which);
            if (button == null) continue;
            boolean act = which == android.app.AlertDialog.BUTTON_POSITIVE;
            // No ripple or tint: e-ink.
            button.setBackground(act ? box(dialog.getContext(), INK, 0, 0) : null);
            button.setTextColor(act ? ON_INK : INK);
            int pad = Ui.dp(dialog.getContext(), 12);
            button.setPadding(pad, 0, pad, 0);
        }
    }
    /**
     * A tree MonoPaint built (a dialog, a menu), in Inkwash's look: every text in iA Writer Duo, ink; an edit field
     * 400, a button a label (caps, 700), the rest 700 if it was bold, else 400; a dialog's grey-200 box is the
     * caller's. Sizes stay MonoPaint's (16–18 sp, all over 13).
     */
    static void restyle(View view) {
        if (view instanceof LabButton) return; // styles itself: its chosen chip is on-ink, which this would paint ink again
        if (view instanceof TextView) {
            TextView text = (TextView)view;
            boolean button = text instanceof android.widget.Button, bold = text.getTypeface() != null && text.getTypeface().isBold();
            text.setTypeface(face(text instanceof EditText ? 400 : button || bold ? 700 : 400));
            text.setTextColor(INK);
            if (button) { text.setAllCaps(true); text.setLetterSpacing(TypeStyle.LABEL.tracking); }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup)view;
            for (int i = 0; i < group.getChildCount(); i++) restyle(group.getChildAt(i));
        }
    }
}
