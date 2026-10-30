package io.github.mpdairy.monopaint;

import java.util.ArrayList;
import java.util.List;

/**
 * The word bin's sizes (the owner's mockup, screen 3: a panel along the foot of the page, docs/books mockup): a 3 dp
 * ink rule along its top; a header row (WORDS, the pack and page, More words, Done); under it a page of tiles, as big
 * as on the page ({@link Magnets}: 2.7 mm words, 4.7 mm tiles), flowed into rows (four in portrait, three in
 * landscape: about 35 to 48 tiles a page), never more than {@link #MOST} a page, so a page
 * always fits and nothing scrolls. In panel pixels, the
 * chrome given in dp at {@code density} pixels a dp. Pure, checked on the desktop ({@code WordBinChecks}).
 */
final class WordBinLayout {
    /** The rule along the bin's top, the room round its rows, the gap between tiles, and the header row's height, dp. */
    static final int RULE = 3, PAD = 12, GAP = 5, HEADER = 48;
    /** The most tiles a page shows (Paintmageddon's ~46). */
    static final int MOST = 48;
    /** Rows of tiles in portrait and in landscape. */
    static final int ROWS = 4, LANDSCAPE_ROWS = 3;
    /** The header's words, for the fit: its label, its buttons, and the room the pack's name and page get at least. */
    static final String LABEL = "WORDS", MORE = "MORE WORDS", DONE = "DONE";
    static final int CAPTION_LEAST = 120, BUTTON_PAD = 12, BUTTON_GAP = 8;

    /** The bin's width (the page's) and height, px; its rows; the rule, padding, gap and header, px; a tile's slot height (its shadow in). */
    final int width, height, rows, rule, pad, gap, header, slot;

    /** The bin for a page {@code width} px wide on a screen {@code screenHeight} px tall, {@code landscape} or not. */
    WordBinLayout(int width, int screenHeight, float density, boolean landscape) {
        this.width = width;
        rows = landscape ? LANDSCAPE_ROWS : ROWS;
        rule = Math.round(RULE * density); pad = Math.round(PAD * density); gap = Math.round(GAP * density);
        header = Math.round(HEADER * density);
        slot = Magnets.HEIGHT + Magnets.SHADOW;
        height = rule + pad + header + gap + rows * slot + (rows - 1) * gap + pad;
    }
    /** A tile's slot width in the bin: the tile and its shadow. */
    static int slotWidth(String word) { return Magnets.width(word) + Magnets.SHADOW; }
    /** The room across a row. */
    int room() { return width - 2 * pad; }
    /** Where the tiles start, from the bin's top. */
    int tilesTop() { return rule + pad + header + gap; }
    /** {@code tiles} in pages, in order: each page as many as its rows hold, at most {@link #MOST}. */
    List<List<String>> pages(List<String> tiles) {
        List<List<String>> pages = new ArrayList<>();
        List<String> page = new ArrayList<>();
        int row = 0, x = 0;
        for (String tile : tiles) {
            int w = Math.min(slotWidth(tile), room());
            if (x > 0 && x + w > room()) { row++; x = 0; }
            if (row >= rows || page.size() >= MOST) { pages.add(page); page = new ArrayList<>(); row = 0; x = 0; }
            page.add(tile);
            x += w + gap;
        }
        if (!page.isEmpty()) pages.add(page);
        return pages;
    }
    /** Where each tile of {@code page} goes: {left, top, right, bottom} of its slot, from the bin's top left, px. */
    int[][] places(List<String> page) {
        int[][] at = new int[page.size()][];
        int row = 0, x = 0;
        for (int i = 0; i < page.size(); i++) {
            int w = Math.min(slotWidth(page.get(i)), room());
            if (x > 0 && x + w > room()) { row++; x = 0; }
            int left = pad + x, top = tilesTop() + row * (slot + gap);
            at[i] = new int[]{left, top, left + w, top + slot};
            x += w + gap;
        }
        return at;
    }
    /** The header's least width, dp: its label, the pack's name and page at their least, and its two buttons, with gaps. */
    static float headerLeast() {
        TypeStyle label = TypeStyle.LABEL;
        return PAD + LabText.width(LABEL, label.sp, label.tracking) + BUTTON_GAP + CAPTION_LEAST
                + 2 * (2 * BUTTON_PAD + BUTTON_GAP) + LabText.width(MORE, label.sp, label.tracking) + LabText.width(DONE, label.sp, label.tracking) + PAD;
    }
}
