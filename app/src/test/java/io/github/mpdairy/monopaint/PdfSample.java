package io.github.mpdairy.monopaint;

import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * A sample notebook's copies at the Manta's size, for trying the PDF on the tablet and for its size before and after
 * (built with the app's sources and MagnetPictures, as scripts/magnet_pictures.sh builds it, run from the repo): seven Lined pages over three days drawn with the app's brushes (fineliner lines, washes),
 * a Daily Prompt card, a page of fridge magnets. {@code <out> new} writes Document/ and EXPORT/ with this version (the
 * days through {@code NotebookExport.Documents}, by reflection so the same file builds against the version before);
 * {@code <out> old} writes the copies as the version it is built with does, all today.
 */
public final class PdfSample {
    static final int W = 1920, H = 2560;
    static final String NAME = "2026-10-21_Sketchbook of Sample Tides", TITLE = "Sketchbook of Sample Tides";

    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        boolean old = args[1].equals("old");
        List<PackedPage> pages = new ArrayList<>();
        int[][] days = {{0, 3}, {3, 5}, {5, 7}};
        NotebookOptions options = NotebookOptions.DEFAULT.withTemplate(NotebookTemplate.LINED);
        Magnets.Tiles tiles = new MagnetPictures.Awt();
        for (int d = 0; d < days.length; d++) {
            for (int i = days[d][0]; i < days[d][1]; i++) pages.add(PackedPage.of(DrawingBook.encode(page(i))));
            if (old && d < days.length - 1) continue;
            NotebookExport.Covers covers = new NotebookExport.Covers(NAME, CoverDesign.DEFAULT, W, H, null, null, -1);
            File export = new File(out, "EXPORT");
            if (old) NotebookExport.update(export, NAME, pages, tiles, covers, options, null, null);
            else {
                Class<?> documents = Class.forName(NotebookExport.class.getName() + "$Documents");
                Object where = documents.getDeclaredConstructor(File.class, String.class, LocalDate.class).newInstance(new File(out, "Document"), TITLE, LocalDate.of(2026, 10, 21 + d));
                NotebookExport.class.getDeclaredMethod("update", File.class, String.class, List.class, Magnets.Tiles.class, NotebookExport.Covers.class, NotebookOptions.class, List.class, TracingPaper.class, documents)
                        .invoke(null, export, NAME, pages, tiles, covers, options, null, null, where);
            }
        }
    }

    /** Page {@code i}: lines, washes under lines, a card, magnets. */
    static ToneDocument page(int i) {
        Random random = new Random(i);
        ToneDocument page = i == 1 || i == 5 ? NotebookFixture.sketch(W, H, i) : InkWashLayers.blank(W, H);
        if (i != 1 && i != 5) {
            WatercolorSession session = new WatercolorSession();
            ModularBrush line = NotebookFixture.brush("Fineliner", "Steady line");
            page.selectLayer(InkWashLayers.INK);
            for (int k = 0; k < 24; k++) NotebookFixture.stroke(page, line, 3, 1, session, random);
            session.settleNow(page);
        }
        if (i == 2) page.placePrompt("3 min | A self-portrait as a knight, full body, boots and all.");
        if (i == 3) {
            List<Magnets.Magnet> words = new ArrayList<>();
            String[][] poem = MagnetPictures.POEM;
            for (int r = 0; r < poem.length; r++) {
                int x = 300;
                for (String w : poem[r]) { int half = Magnets.width(w) / 2; words.add(new Magnets.Magnet(w, x + half, 900 + r * 110, (r * 7 + w.length() * 3) % 41 - 20, 0)); x += 2 * half + 24; }
            }
            page.placeMagnets(Magnets.of(words));
        }
        return page;
    }

    private PdfSample() {}
}
