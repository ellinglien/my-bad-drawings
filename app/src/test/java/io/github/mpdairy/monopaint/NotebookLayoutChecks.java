package io.github.mpdairy.monopaint;

import java.time.LocalDate;
import java.util.Random;

/**
 * The shelf and the inside of a notebook fit both tablets in both turns (docs/archive/superpowers/specs/2026-10-18-notebooks-
 * design.md, "The screens"), as {@code LabLayoutChecks} and {@code DrawLayoutChecks} check theirs: nothing scrolls,
 * nothing is cut, every word fits, every target at least 48 dp.
 */
public final class NotebookLayoutChecks {
    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        LabText.Measure label = LabText.face(TypeStyle.LABEL.sp, TypeStyle.LABEL.tracking), caption = LabText.face(TypeStyle.CAPTION.sp, 0);
        for (int[] s : NotebookLayout.SCREENS)
            for (double aspect : new double[]{NotebookLayout.aspectOf(s), 1, 1.25, 1.8}) {
                NotebookLayout l = new NotebookLayout(s[0], s[1], aspect);
                String at = s[0] + "x" + s[1] + " at " + aspect;
                // The shelf.
                check(l.shelfUsed() <= s[1] && l.rows >= 1 && l.columns >= 2 && l.perPage() >= 4, "The shelf fits without scrolling, at least four tiles a page at " + at + " (" + l.columns + " × " + l.rows + ")");
                check(l.columns * l.tileWidth + (l.columns - 1) * l.columnGap <= s[0] - 2 * l.margin, "Its tiles fit across at " + at);
                check(l.tileWidth >= (l.compact ? 150 : 180) && l.coverHeight >= 150, "Covers big enough to know at " + at + " (" + l.tileWidth + " × " + l.coverHeight + ")");
                check(LabText.fits("100 pages · Oct 18 2025", l.tileWidth, caption, NotebookLayout.COUNT_LINES) && caption.width("24 pages · today") <= l.tileWidth,
                        "A tile's count and date fit its lines at " + at);
                // New notebook's tile: a dashed cover with a +, its name under it as a notebook's is.
                check(LabText.fits("New Notebook", l.tileWidth, LabText.face(TypeStyle.BODY.sp, 0), 1), "New notebook's name fits its tile at " + at);
                // Inside a notebook.
                check(l.notebookUsed() == s[1] && l.spreadHeight >= l.pageHeight, "The notebook screen adds up to its height at " + at);
                // The page as big as fits between the bar and the strip, at the page's shape, and bigger than before the one
                // bar (2026-10-19: a bar, the name under it, the page with ‹ › beside it, the strip, a row of actions).
                int across = s[0] - 2 * l.margin;
                check(l.pageWidth <= across && l.pageHeight == Math.min(l.spreadHeight, (int) Math.floor(across * aspect)) && l.pageHeight >= (l.compact ? 300 : 420),
                        "The page as big as fits at " + at + " (" + l.pageWidth + " × " + l.pageHeight + ")");
                int title = (int) Math.ceil(NotebookLayout.TITLE_LINES * LabText.line(TypeStyle.HEADING.sp));
                int oldSpread = s[1] - 2 * l.margin - 2 * NotebookLayout.BAR - title - l.thumbHeight - NotebookLayout.TAB - NotebookLayout.TAB_GAP - 4 * l.gap;
                int old = Math.min(oldSpread, (int) Math.floor((across - 2 * 56) * aspect));
                check(l.pageHeight > old, "The page bigger than with the name, the arrows and the actions' row (" + old + " dp tall) at " + at + ": " + l.pageHeight);
                check(l.visible * l.thumbWidth + (l.visible - 1) * l.stripGap <= across && l.visible >= 4 && l.thumbHeight >= 72, "The strip fits across at " + at + " (" + l.visible + " pages)");
                // One bar: ‹ Notebooks, the name, Draw, Use on Cover, Delete Page; the last two their icons alone on the Nomad in portrait.
                boolean nomadPortrait = s[0] == 749 && s[1] == 998;
                check(l.labelled == !nomadPortrait, "The actions' words in the bar but on the Nomad in portrait (their icons there) at " + at);
                int bar = NotebookLayout.backWidth() + NotebookLayout.actionWidth(NotebookLayout.OPEN, true) + NotebookLayout.coverWidth(l.labelled)
                        + NotebookLayout.actionWidth(NotebookLayout.DELETE, l.labelled) + 4 * NotebookLayout.BAR_GAP + l.titleRoom;
                check(bar == across && l.titleRoom >= NotebookLayout.TITLE_LEAST, "The bar's buttons and the name's " + l.titleRoom + " dp across it at " + at);
                check(NotebookLayout.TITLE_LINES * LabText.line(TypeStyle.BODY.sp) <= NotebookLayout.BAR && NotebookLayout.BUTTON >= 48 && NotebookLayout.BAR >= NotebookLayout.BUTTON,
                        "The name's two lines in the bar, its buttons 48 dp at " + at);
                check(label.width("OPEN") + 2 * NotebookLayout.ACTION_PAD + 4 <= NotebookLayout.actionWidth(NotebookLayout.OPEN, true), "Open says its word at " + at);
                // The drawing screen's page bar along the foot: ‹, the page and its count, ›.
                check(l.pageBar == DrawLayout.pageBar(l.compact) && l.pageButton == DrawLayout.pageButton(l.compact) && 2 * l.pageButton + DrawLayout.pageNumber() <= s[0]
                        && l.pageBar - DrawLayout.PAGE_RULE >= 26, "The drawing screen's page bar along the foot at " + at);
            }
        // The strip's window: the current page always in it, the + tile after the last.
        NotebookLayout manta = new NotebookLayout(1024, 1365, NotebookLayout.aspectOf(NotebookLayout.SCREENS[0]));
        for (int count = 1; count <= 100; count++)
            for (int current = 0; current < count; current++) {
                int start = manta.stripStart(current, count);
                check(start <= current && current < start + manta.visible && start + manta.visible <= Math.max(manta.visible, count + 1), "The strip shows page " + (current + 1) + " of " + count);
            }
        // The shelf's pages: every notebook on one, New notebook first.
        for (int n = 0; n < 60; n++) {
            int seen = 0;
            for (int page = 0; page < manta.shelfPages(n); page++) {
                int[] p = manta.shelfPage(page, n);
                check(p[0] == seen, "Shelf pages follow on"); seen += p[1];
                for (int i = p[0]; i < p[0] + p[1]; i++) check(manta.shelfPageOf(i) == page, "A notebook's shelf page");
            }
            check(seen == n, n + " notebooks all on the shelf");
        }
        // Names: the automatic ones, Loose pages and a long one fit; one too long is refused.
        LocalDate today = LocalDate.of(2026, 10, 18);
        for (int i = 0; i < 2000; i++) {
            String name = DrawingNames.shown(DrawingNames.notebook(today.minusDays(i % 400), new Random(i), t -> false) + " 12", today);
            check(NotebookLayout.fits(name), "A notebook's automatic name fits every tile and title: " + name);
        }
        check(NotebookLayout.fits("Loose pages 12") && NotebookLayout.fits("Harbour sketches from the ferry, foggy"), "Loose pages and a longer name fit");
        // The bar's words: Title Case (docs/TEXT-STYLE.md), a button's two words (Use on Cover's "on" aside: the owner's own words).
        for (String w : new String[]{NotebookLayout.BACK, NotebookLayout.OPEN, NotebookCovers.USE_BUTTON, NotebookCovers.NO_PICTURE_BUTTON, NotebookLayout.DELETE}) {
            int big = 0;
            for (String word : w.split(" ")) if (Character.isUpperCase(word.charAt(0))) big++; else check(word.equals("on"), w + ": Title Case");
            check(big <= 2, w + ": two words at most");
        }
        check(!NotebookLayout.fits("Mmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmm"), "A name that can't fit is refused, never cut");
        System.out.println("PASS: the shelf and the inside of a notebook fit the Manta and the Nomad in either turn at any page shape without scrolling: at least four covers a shelf page with their names in three lines and \"24 pages · Oct 17\", inside a notebook one bar (‹ Notebooks, the name in two lines, Draw, Use on Cover and Delete Page, the last two their icons on the Nomad in portrait), the page as big as fits under it and bigger than before, a strip of pages round the current one, and the drawing screen's page bar along the foot; every automatic name fits, one that can't is refused");
    }
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
