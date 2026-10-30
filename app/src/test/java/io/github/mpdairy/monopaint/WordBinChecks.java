package io.github.mpdairy.monopaint;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * The word bin ({@link WordBinLayout}) on both tablets in either turn: every page of tiles fits its rows with nothing
 * clipped or scrolled, about 35 to 48 tiles a page (Paintmageddon's ~46), the header's words and buttons fit across it, and
 * the bin leaves at least three quarters of the page to the drawing.
 */
public final class WordBinChecks {
    /** The tablets' screens, dp, and their pixels a dp. */
    static final int[][] SCREENS = DrawLayoutChecks.SCREENS;
    static final float DENSITY = 1.875f;

    public static void main(String[] args) throws IOException {
        Assets.install(Assets.folder(Assets.FOLDER));
        List<String> kit = WordPacks.kit(Assets.lines(WordPacks.BUNDLED));
        StringBuilder report = new StringBuilder();
        for (int[] s : SCREENS) {
            DrawLayout chrome = new DrawLayout(s[0], s[1]);
            boolean landscape = s[0] > s[1];
            int width = Math.round(chrome.pageWidth() * DENSITY), screen = Math.round(s[1] * DENSITY);
            WordBinLayout bin = new WordBinLayout(width, screen, DENSITY, landscape);
            String at = s[0] + "x" + s[1];
            check(bin.height <= .25f * screen, "The bin leaves three quarters of the page (at most 25% of its height) at " + at + " (" + bin.height / DENSITY + " dp of " + s[1] + ")");
            check(WordBinLayout.headerLeast() <= chrome.pageWidth(), "The header fits across the bin at " + at + " (" + WordBinLayout.headerLeast() + " dp)");
            check(bin.header >= Math.round(44 * DENSITY), "Header buttons at least 44 dp tall at " + at);
            int least = Integer.MAX_VALUE, most = 0, pages = 0, total = 0;
            for (int seed = 0; seed < 20; seed++) {
                List<List<String>> book = bin.pages(WordPacks.shuffled(kit, new Random(seed)));
                int count = 0;
                for (int p = 0; p < book.size(); p++) {
                    List<String> page = book.get(p);
                    count += page.size();
                    fits(bin, page, at);
                    if (p < book.size() - 1) { least = Math.min(least, page.size()); most = Math.max(most, page.size()); pages++; total += page.size(); }
                }
                check(count == kit.size(), "Every tile is on some page at " + at);
            }
            double mean = total / (double) pages;
            check(mean >= 35 && mean <= WordBinLayout.MOST && least >= 30 && most <= WordBinLayout.MOST, "About 35 to " + WordBinLayout.MOST + " tiles a page at " + at + " (" + least + " to " + most + ", " + mean + " on average)");
            report.append(String.format("%s %d rows, %.0f dp tall (%.0f%%), %.1f tiles a page (%d to %d); ", at, bin.rows, bin.height / DENSITY, 100f * bin.height / screen, mean, least, most));
            // An imported pack's longest words fit too.
            List<String> longest = new ArrayList<>(Arrays.asList("abcdefghijklmnopqrstuvwx", "mmmmmmmmmmmmmmmmmmmmmmmm", "s", "wwwwwwwwwwww"));
            for (List<String> page : bin.pages(longest)) fits(bin, page, at);
        }
        System.out.println("PASS: the word bin fits both tablets in either turn, nothing clipped or scrolled: " + report + "its header (WORDS, the pack and page, More words, Done) fits across it");
    }
    static void fits(WordBinLayout bin, List<String> page, String at) {
        check(!page.isEmpty() && page.size() <= WordBinLayout.MOST, "A page holds 1 to " + WordBinLayout.MOST + " tiles at " + at);
        int[][] places = bin.places(page);
        for (int i = 0; i < places.length; i++) {
            int[] r = places[i];
            check(r[0] >= bin.pad && r[2] <= bin.width - bin.pad && r[1] >= bin.tilesTop() && r[3] <= bin.height - bin.pad,
                    "\"" + page.get(i) + "\" inside the bin at " + at + ": " + Arrays.toString(r));
            check(r[3] - r[1] >= Magnets.HEIGHT && r[2] - r[0] >= Math.min(Magnets.width(page.get(i)), bin.room() - Magnets.SHADOW), "Tiles at their full size in the bin");
            for (int j = 0; j < i; j++) {
                int[] o = places[j];
                check(r[0] >= o[2] || r[2] <= o[0] || r[1] >= o[3] || r[3] <= o[1], "Tiles never overlap in the bin at " + at);
            }
        }
    }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
