package io.github.mpdairy.monopaint;

import java.io.*;
import java.util.Locale;

/**
 * {@link NotebookBench}'s files timed with the document classes from before notebooks (git `580a326`,
 * scripts/bench_notebooks.sh): opening inflated every page; the Open dialog's tile decoded page 1 and averaged it down to
 * 240 px ({@code PageOverview.thumbnail}'s arithmetic, without Android).
 */
public final class NotebookBenchBefore {
    public static void main(String[] args) throws Exception {
        File dir = new File(args[0]);
        File notebook = new File(dir, "24 pages.mpaint");
        double open = best(() -> { try (InputStream in = new FileInputStream(notebook)) { BookCodec.read(in); } });
        double shelf = best(() -> {
            for (int i = 0; i < 20; i++)
                try (InputStream in = new FileInputStream(new File(dir, "shelf " + i + ".mpaint"))) { tile(BookCodec.readFirstPage(in)); }
        });
        System.out.printf(Locale.ROOT, "before: open 24 pages %.0f ms; 20 drawings' tiles in Open %.0f ms%n", open, shelf);
    }
    interface Work { void run() throws Exception; }
    /** The best of five runs, after two to warm up, ms (as {@link NotebookBench}'s). */
    static double best(Work work) throws Exception {
        for (int i = 0; i < 2; i++) work.run();
        double best = Double.MAX_VALUE;
        for (int i = 0; i < 5; i++) { long t = System.nanoTime(); work.run(); best = Math.min(best, (System.nanoTime() - t) / 1e6); }
        return best;
    }
    /** PageOverview.thumbnail's sampling: 240 px on the long side, 16 samples a pixel. */
    static int[] tile(ToneDocument document) {
        float scale = Math.min(1, 240f / Math.max(document.width, document.height));
        int width = Math.max(1, Math.round(document.width * scale)), height = Math.max(1, Math.round(document.height * scale));
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++) {
                int sum = 0;
                for (int sy = 0; sy < 4; sy++) for (int sx = 0; sx < 4; sx++) {
                    int px = Math.min(document.width - 1, (int) ((x + (sx + .5f) / 4) * document.width / width));
                    int py = Math.min(document.height - 1, (int) ((y + (sy + .5f) / 4) * document.height / height));
                    sum += document.compositeTone(px, py);
                }
                pixels[y * width + x] = (sum + 8) / 16;
            }
        return pixels;
    }
}
