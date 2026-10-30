package io.github.mpdairy.monopaint;

/**
 * What the eraser takes (the owner, 2026-10-20: "an eraser that targets a kind of mark"): a long press on its tile offers
 * these four, Everything first: every mark, whole, permanent ink too (the owner, 2026-10-22). A kind is a {@link ToneDocument#KIND_PEN} bit and the like; each
 * Medium lays one ({@link ResolvedBrush#kind}). Pure Java; docs/archive/superpowers/specs/2026-10-20-erase-kind-design.md.
 */
final class EraseKind {
    /** The buttons' words (docs/TEXT-STYLE.md: Title Case, two words at most), and what each takes. */
    static final String[] LABELS = {"Everything", "Pen", "Paint", "Dry Media"};
    static final int[] KINDS = {0, ToneDocument.KIND_PEN, ToneDocument.KIND_PAINT, ToneDocument.KIND_PENCIL};
    /** The result in two words, said when a choice is made. */
    private static final String[] NOTES = {"Erasing everything", "Erasing pen", "Erasing paint", "Erasing dry media"};

    /** The Eraser tile's mark for each choice (none for Everything). */
    static final String[] MARKS = {"", "Pe", "Pa", "Dr"};
    static String note(int choice) { return NOTES[choice]; }
    /** The choice that takes {@code kinds}, 0 (Everything) for any other. */
    static int choiceOf(int kinds) { for (int i = 1; i < KINDS.length; i++) if (KINDS[i] == kinds) return i; return 0; }

    /**
     * What an eraser set to {@code kinds} (0: Everything) takes while Show Kinds hides {@code hidden}
     * ({@link NotebookOptions#hiddenKinds}): never a hidden kind, which can't be seen. With none hidden, {@code kinds} as
     * set; else 0 means nothing is left to take, and the stroke is refused.
     */
    static int takes(int kinds, int hidden) { return hidden == 0 ? kinds : (kinds == 0 ? ToneDocument.KIND_ALL : kinds) & ~hidden; }

    /**
     * Show Kinds (the owner, 2026-10-28): each kind's step on a page's screen ({@link NotebookOptions#GUIDE_STEPS}, Full to
     * Hidden), two bits a kind, Pen lowest, then Paint, then Dry Media ({@link #LABELS}' order); 0 all Full
     * ({@link ToneDocument#kindsShown}).
     */
    static final int STEPS = 63;
    /** The main menu's row (docs/TEXT-STYLE.md: two words, Title Case). */
    static final String SHOW_KINDS = "Show Kinds";
    /** The step kind {@code choice} (1 Pen, 2 Paint, 3 Dry Media) shows at in {@code shown}. */
    static int step(int shown, int choice) { return choice < 1 ? NotebookOptions.FULL : shown >> 2 * (choice - 1) & 3; }
    /** {@code shown} with kind {@code choice} at {@code step}. */
    static int withStep(int shown, int choice, int step) { int shift = 2 * (choice - 1); return shown & ~(3 << shift) | (step & 3) << shift; }
    /** The step marks of {@code kinds} ({@link ToneDocument#KIND_PEN} bits; a plane's, a brush's medium) show at: the faintest of them. */
    static int stepOf(int shown, int kinds) {
        int step = NotebookOptions.FULL;
        for (int c = 1; c < KINDS.length; c++) if ((kinds & KINDS[c]) != 0) step = Math.max(step, step(shown, c));
        return step;
    }
    /** The kinds ({@link ToneDocument#KIND_PEN} bits) {@code shown} hides: no stroke of them, no eraser taking them. */
    static int hidden(int shown) {
        int hidden = 0;
        for (int c = 1; c < KINDS.length; c++) if (step(shown, c) == NotebookOptions.HIDDEN) hidden |= KINDS[c];
        return hidden;
    }
    /** Why a stroke laying {@code kinds} is refused: the first of them hidden, with where to show it ("Pen is hidden, see Show Kinds"); null when none is. */
    static String refusal(int shown, int kinds) {
        for (int c = 1; c < KINDS.length; c++) if ((kinds & hidden(shown) & KINDS[c]) != 0) return said(c, NotebookOptions.HIDDEN) + ", see " + SHOW_KINDS;
        return null;
    }
    /** What a brush of a kind not Full says when taken up (docs/TEXT-STYLE.md, sentence case): "Pen is hidden", "Dry media is faint". */
    static String said(int choice, int step) { return sentence(choice) + " is " + NotebookOptions.GUIDE_STEPS[step].toLowerCase(java.util.Locale.ROOT); }
    /** The note a step picked gives, as Template Visibility's ("Template half"): "Pen half", "Dry media hidden". */
    static String stepNote(int choice, int step) { return sentence(choice) + " " + NotebookOptions.GUIDE_STEPS[step].toLowerCase(java.util.Locale.ROOT); }
    /** Kind {@code choice}'s label in sentence case: "Dry media". */
    private static String sentence(int choice) { String l = LABELS[choice]; return l.charAt(0) + l.substring(1).toLowerCase(java.util.Locale.ROOT); }

    private EraseKind() {}
}
