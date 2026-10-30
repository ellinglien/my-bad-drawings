package io.github.mpdairy.monopaint;

/**
 * Inkwash's type scale (the owner's Inkwash Design Style, converted from 300 ppi template pixels): size in sp, before
 * the app's 1.2× text scale ({@link Ui#largerText}); iA Writer Duo weight, 400 or 700 (the family's only two; no
 * italics); tracking in em. Hierarchy is size, weight and case: headings sentence case and bold, labels caps, tracked
 * and bold, the rest 400. {@link Theme#style} applies one; {@link LabText} measures with them. Pure.
 */
enum TypeStyle {
    /**
     * Every menu's and popup's rows over the page, one size for all of them (the owner, 2026-10-23: "12px font for menus
     * is better"), the smallest step: 400, the chosen row 700 ({@link Theme#menuRow}).
     */
    MENU(12, 400, 0),
    /** The smallest text anywhere else: end words, captions, a description. */
    CAPTION(13, 400, 0),
    /** All caps, tracked: buttons, bars, labels. */
    LABEL(14, 700, .06f),
    BODY(15, 400, 0),
    HEADING(20, 700, 0),
    TITLE(32, 700, 0);
    final float sp, tracking;
    final int weight;
    TypeStyle(float sp, int weight, float tracking) { this.sp = sp; this.weight = weight; this.tracking = tracking; }
    boolean caps() { return this == LABEL; }
}
