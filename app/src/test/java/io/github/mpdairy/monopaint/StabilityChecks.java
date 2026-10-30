package io.github.mpdairy.monopaint;

import java.io.*;
import java.nio.file.Files;
import java.util.*;

/**
 * Overload fixes (docs/archive/stability-2026-10-22.md) write what they wrote before: a notebook's PDF made from its PNGs read
 * one at a time is the PDF made from them all held at once; a page whose kind plane is all base writes the same bytes
 * streamed as from a page-sized array of zeros. Undo at the Manta's budget keeps a Clear page.
 */
public final class StabilityChecks {
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    public static void main(String[] args) throws Exception {
        Assets.install(Assets.folder(Assets.FOLDER));
        pdfStreamed();
        zeroKinds();
        wetBudget();
        wetCeiling();
        undoBudget();
        System.out.println("PASS: stability: the copies' PDF reads its PNGs one at a time and is byte for byte the PDF of them all held at once; an all-base kind plane streams the same bytes as an array of zeros; wet paint under its share of the heap stays wet, past it the next stroke dries its oldest tiles and the rest stays wet; "
                + "one huge wet stroke keeps its own wet tiles under its ceiling, and past the share during a stroke the older strokes' tiles dry first, their paint left on the page; "
                + "Undo on the Manta's heap keeps 24 MB of strokes, a Clear page still undoable after more strokes, and counts a cleared layer's print plane");
    }

    static void pdfStreamed() throws IOException {
        int w = 120, h = 160, n = 9;
        Random random = new Random(3);
        List<PackedPage> pages = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            byte[] tones = new byte[w * h];
            for (int k = 0; k < tones.length; k++) tones[k] = (byte) (255 - (random.nextInt(4) == 0 ? random.nextInt(256) : 0));
            pages.add(PackedPage.of(DrawingBook.encode(w, h, tones)));
        }
        File dir = Files.createTempDirectory("stability").toFile();
        try {
            NotebookExport.Done done = NotebookExport.update(dir, "nb", pages, null,
                    new NotebookExport.Covers("nb", CoverDesign.DEFAULT, w, h, null, null, 2), NotebookOptions.DEFAULT, null, null);
            check(done.drawn == n + 2, "every page and both covers drawn: " + done.drawn);
            // The same PNGs, all held, through the PDF writer as it was.
            List<NotebookExport.Png> held = new ArrayList<>();
            held.add(NotebookExport.read(Files.readAllBytes(new File(dir, "nb/" + NotebookExport.FRONT_PNG).toPath())));
            for (int i = 0; i < n; i++) held.add(NotebookExport.read(Files.readAllBytes(new File(dir, "nb/" + NotebookExport.pageName(i)).toPath())));
            ByteArrayOutputStream all = new ByteArrayOutputStream();
            NotebookPdf.State said = NotebookPdf.State.read(new File(dir, "nb/" + NotebookPdf.STATE));
            NotebookPdf.write(all, "nb", said.title, said.subject, said.sheets, held::get);
            check(Arrays.equals(all.toByteArray(), Files.readAllBytes(done.pdf.toPath())), "the PDF read page by page is the PDF of the PNGs held at once");
            // Again with every PNG kept: read from the folder, the same PDF.
            byte[] first = Files.readAllBytes(done.pdf.toPath());
            NotebookExport.Done again = NotebookExport.update(dir, "nb", pages, null,
                    new NotebookExport.Covers("nb", CoverDesign.DEFAULT, w, h, null, null, 2), NotebookOptions.DEFAULT, null, null);
            check(again.drawn == 0 && Arrays.equals(first, Files.readAllBytes(again.pdf.toPath())), "kept PNGs make the same PDF");
        } finally {
            File[] inside = new File(dir, "nb").listFiles();
            if (inside != null) for (File f : inside) f.delete();
            new File(dir, "nb").delete();
            File[] files = dir.listFiles();
            if (files != null) for (File f : files) f.delete();
            dir.delete();
        }
    }

    /** Wet paint under its share of the heap stays wet; past it, the next stroke dries the tiles painted longest ago, not all of it. */
    static void wetBudget() {
        long was = WatercolorSession.WET_BYTES;
        try {
            ModularBrush wet = null;
            for (ToolPresets.Preset p : ToolPresets.all()) if (p.name.equals("Wet ink")) wet = p.brush().parts;
            WatercolorSession session = new WatercolorSession();
            ToneDocument d = InkWashLayers.blank(400, 300);
            double[][] line = {{40, 150, .8, 0}, {200, 150, .8, 40}, {360, 150, .8, 80}};
            stroke(d, session, wet, line);
            WetCanvas first = session.canvas();
            check(first != null && first.wet.heldBytes() > 0, "a Wet stroke leaves wet paint");
            stroke(d, session, wet, line);
            check(session.canvas() == first, "under the budget it stays wet across strokes");
            long held = first.wet.heldBytes();
            WatercolorSession.WET_BYTES = held - 1;
            stroke(d, session, wet, new double[][]{{40, 60, .8, 0}, {200, 60, .8, 40}, {360, 60, .8, 80}});
            check(session.canvas() == first && first.wet.heldBytes() <= held + held / 4, "past it, the next stroke dries the oldest tiles and the wet paint stays: " + first.wet.heldBytes() / 1000 + " KB of " + held / 1000);
        } finally { WatercolorSession.WET_BYTES = was; }
    }
    /** Undo at the Manta's budget (24 MB): strokes past it drop the oldest; a clear of a full page stays (to 32 MB); a cleared layer's print plane counts. */
    static void undoBudget() {
        int was = ToneDocument.HISTORY_BYTES;
        try {
            ToneDocument.HISTORY_BYTES = 24 << 20;
            int w = 1920, h = 2560, most = 0;
            ToneDocument d = InkWashLayers.blank(w, h);
            for (int s = 0; s < 16; s++) {
                d.begin(); for (int y = 0; y < 640; y++) for (int x = 0; x < w; x++) d.setTone(x, y, (x ^ y) + s & 255); d.finish();
                most = Math.max(most, d.historyBytes());
            }
            check(d.undoSteps() < 16 && most <= (24 << 20) + 4 * w * 640, "strokes past 24 MB drop the oldest: " + d.undoSteps() + " steps, " + most);
            // PageClearChecks' page: both layers holding shares, some 30 MB, over 24.
            ToneDocument c = InkWashLayers.blank(w, h);
            for (int layer = 0; layer < 2; layer++) { c.selectLayer(layer); c.begin(.4); for (int x = 0; x < w; x += 3) c.setTone(x, 100 + layer, 40); c.finish(); }
            c.selectLayer(InkWashLayers.INK);
            check(c.clearPage(), "clears the full page");
            for (int s = 0; s < 8; s++) { c.begin(); for (int x = 0; x < 600; x++) c.setTone(x, 400 + s * 50, 0); c.finish(); }
            for (int s = 0; s < 8; s++) check(c.undo(), "undoes stroke " + s);
            check(c.unmarked() && c.undo() && !c.unmarked(), "the clear is still undoable after eight strokes");
        } finally { ToneDocument.HISTORY_BYTES = was; }
        // The same clear but for a print plane on one layer holds a plane more.
        int w = 200, h = 160, plain = 0, printed = 0;
        for (boolean print : new boolean[]{false, true}) {
            ToneDocument d = InkWashLayers.blank(w, h);
            d.begin(.4); if (print) d.printing(); d.setTone(5, 5, 40); d.finish();
            int before = d.historyBytes();
            check(d.clearPage(), "clears");
            if (print) printed = d.historyBytes() - before; else plain = d.historyBytes() - before;
        }
        check(printed - plain == w * h, "a cleared layer's print plane counts: " + plain + " and " + printed);
    }
    /**
     * One huge wet stroke (a Large Soft wash back and forth over a 1600 × 1200 page) keeps its own tiles under {@link
     * WetWatercolor#STROKE_BYTES}, its start still painted; and past {@link WatercolorSession#WET_BYTES} during a stroke the
     * older strokes' tiles dry first, the wet paint staying wet and its paint on the page.
     */
    static void wetCeiling() {
        long was = WatercolorSession.WET_BYTES;
        try {
            ModularBrush soft = null, wet = null;
            for (ToolPresets.Preset p : ToolPresets.all()) { if (p.name.equals("Soft wash")) soft = p.brush().parts; if (p.name.equals("Wet ink")) wet = p.brush().parts; }
            WatercolorSession session = new WatercolorSession();
            ToneDocument d = InkWashLayers.blank(1600, 1200);
            List<double[]> path = new ArrayList<>();
            for (int row = 0, t = 0; row < 9; row++) for (int k = 0; k <= 375; k++) path.add(new double[]{row % 2 == 0 ? 50 + 4 * k : 1550 - 4 * k, 100 + 125 * row, .9, t++ * 3});
            session.settleBefore(d, soft);
            DrawingStroke s = QuickStroke.start(d, soft, ModularSizes.size(soft, InkWashSizes.Preset.LARGE), .9, session);
            for (int i = 0; i < path.size(); i++) { double[] p = path.get(i); s.sample((float) p[0], (float) p[1], ToolSettings.pressure((float) p[2]), 0, 0, (long) p[3]); if (i % 4 == 3) s.present(); }
            s.finish();
            long held = session.canvas().wet.heldBytes();
            check(held > WetWatercolor.STROKE_BYTES * 3 / 4 && held <= WetWatercolor.STROKE_BYTES * 9 / 8, "a huge wet stroke keeps its wet tiles under its ceiling: " + held / 1000000 + " MB");
            check(d.tone(60, 100) < 250, "where it began, dried, its paint stays on the page");

            session = new WatercolorSession();
            d = InkWashLayers.blank(400, 300);
            stroke(d, session, wet, new double[][]{{40, 60, .8, 0}, {360, 60, .8, 80}});
            WetCanvas canvas = session.canvas();
            long first = canvas.wet.heldBytes();
            WatercolorSession.WET_BYTES = first + 12 * 1024;
            stroke(d, session, wet, new double[][]{{40, 240, .8, 0}, {360, 240, .8, 80}});
            check(session.canvas() == canvas, "under the share before the stroke, the wet paint stays wet");
            check(canvas.wet.heldBytes() < 2 * first && canvas.wet.heldBytes() <= WatercolorSession.WET_BYTES + first / 4, "past it during the stroke, the older stroke's tiles dry");
            check(d.tone(200, 60) < 250, "the older stroke, dried, stays on the page");
        } finally { WatercolorSession.WET_BYTES = was; }
    }
    static void stroke(ToneDocument d, WatercolorSession session, ModularBrush b, double[][] path) {
        session.settleBefore(d, b);
        DrawingStroke s = QuickStroke.start(d, b, 12, .9, session);
        for (double[] p : path) { s.sample((float) p[0], (float) p[1], ToolSettings.pressure((float) p[2]), 0, 0, (long) p[3]); s.present(); }
        s.finish();
        for (int k = 0; k < 200 && session.tickDrying(d); k++) { }
    }

    static void zeroKinds() throws IOException {
        int w = 300, h = 200;
        ToneDocument page = NotebookFixture.sketch(w, h, 1);
        ToneDocument.Snapshot streamed = page.layerSnapshot(), arrayed = page.layerSnapshot();
        // Two kinds on the layer, its plane all base: written streamed (no plane) and from an array of zeros.
        for (ToneDocument.Snapshot s : new ToneDocument.Snapshot[]{streamed, arrayed}) { ToneDocument.Layer l = s.layers.get(s.active); l.kinds = 3; l.kindBase = 1; }
        arrayed.layers.get(arrayed.active).kind = new byte[w * h];
        ByteArrayOutputStream a = new ByteArrayOutputStream(), b = new ByteArrayOutputStream();
        DocumentCodec.write(a, streamed); DocumentCodec.write(b, arrayed);
        check(a.size() > 0 && Arrays.equals(a.toByteArray(), b.toByteArray()), "an all-base kind plane streams the same bytes");
        ToneDocument read = DocumentCodec.read(new ByteArrayInputStream(a.toByteArray()));
        check(read.width == w && read.height == h, "and reads back");
        streamed.release(); arrayed.release();
    }
}
