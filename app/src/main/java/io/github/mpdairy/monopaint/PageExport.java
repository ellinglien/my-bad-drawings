package io.github.mpdairy.monopaint;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * One page's copy, asked for on its Page Settings (Export Page; the owner, 2026-10-28: "each page should have a way to
 * save page in various ways.. pdf png, and possibly layers for photoshop? ie. the diff media layers"): a PDF, a PNG or
 * Layers, a Photoshop file ({@link PsdWriter}), named for the notebook and the page ("Harbour p5.psd") beside the
 * notebooks' copies ({@code AutoExport}). The PNG and the PDF are the notebook's copies' own for that page ({@link
 * NotebookExport#png}, {@link NotebookPdf#write}, the PDF numbered as the page). Layers holds, bottom first: Paper (the
 * page's colour), Template (the guides as the copies draw them, where they show), then for each of the page's layers its
 * marks kind by kind as the page stacks them ({@link ToneDocument#stacked}: Old Paint, the base, under Dry Media,
 * Washes, Pen and Paint, opaque paint), "Layer 2 Pen" where the page has more than one, then Magnets; empty ones left
 * out. Each in the PNG's smooth greys, Normal: the page composites every mark over what is under it (no wash
 * multiplies), so Normal is exact where a mark covers fully; where it covers partly, its coverage is the one that shows
 * on the paper as the PNG shows it there (the greys' curve, {@link DotPattern#exportGray}, comes after the composite),
 * so the layers come within a few greys of the merged image, the PNG's own, only where partial marks overlap.
 * Left out: the masking fluid (never part of the artwork, nor of the PNG), Show Kinds' steps (the copies show every
 * kind whole) and a hidden layer. Pure Java, checked on the desktop ({@code PageExportChecks}); run on {@code AutoExport}'s worker.
 */
final class PageExport {
    /** The choices' words (docs/TEXT-STYLE.md) in the menu Export Page opens. */
    static final String PDF = "PDF", PNG = "PNG", LAYERS = "Layers";
    static final String[] CHOICES = {PDF, PNG, LAYERS};
    /** A page's layers' kinds as the Layers copy names them, in {@link ToneDocument#stacked}'s order: the base, then each plane ({@link ToneDocument#PLANE_DRY} …). */
    static final String[] KINDS = {"Old Paint", "Dry Media", "Washes", "Pen", "Paint"};
    static final String PAPER = "Paper", TEMPLATE = "Template", MAGNETS = "Magnets";

    /** {@code choice}'s file ending. */
    static String extension(String choice) { return choice.equals(LAYERS) ? "psd" : choice.toLowerCase(Locale.ROOT); }
    /** Page {@code place}'s (a stack's) copy's file: "p5.psd". */
    static String page(int place, String choice) { return "p" + (place + 1) + "." + extension(choice); }
    /** Its file's name in {@code title}'s notebook: "Harbour p5.psd". */
    static String name(String title, int place, String choice) { return NotebookPdf.base(title) + " " + page(place, choice); }

    /**
     * Writes page {@code place}'s (a stack's) {@code choice} copy into {@code folder} as {@link #name}, from the
     * notebook's {@code pages} (packed), its {@code options}, each page's template key ({@code pageTemplates}) and its
     * stacks ({@code tracing}), its magnets drawn by {@code tiles} (null: none), as {@link NotebookExport#update} draws
     * that page; the file written.
     */
    static File write(File folder, String title, int place, String choice, List<PackedPage> pages, NotebookOptions options, List<String> pageTemplates, TracingPaper tracing, Magnets.Tiles tiles) throws IOException {
        PageStacks stacks = tracing != null && tracing.count() == pages.size() ? tracing.stacks() : new PageStacks(new boolean[pages.size()]);
        int bottom = stacks.first(place);
        NotebookTemplate template = options.pageTemplate(pageTemplates != null && bottom < pageTemplates.size() ? pageTemplates.get(bottom) : null);
        byte[] bytes;
        synchronized (DocumentCodec.ONE_PAGE) {
            List<ToneDocument> layers = NotebookExport.layers(pages, stacks, place, tracing, template);
            ToneDocument.Snapshot page = NotebookExport.flatten(layers, NotebookExport.hidden(stacks, place, tracing));
            boolean guides = template.shared(page.width, page.height) != null;
            if (choice.equals(LAYERS)) bytes = psd(page, tiles, guides);
            else {
                long key = NotebookExport.pageKey(NotebookExport.stackKey(pages, stacks, place, tracing), template);
                byte[] png = NotebookExport.png(page, key, tiles, guides);
                if (choice.equals(PNG)) bytes = png;
                else {
                    long today = LocalDate.now().toEpochDay();
                    NotebookPdf.Sheet sheet = NotebookExport.said(key, template.equals(NotebookTemplate.BLANK) ? "" : template.label, layers).days(today, today);
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    NotebookPdf.write(out, title, title, options.template.equals(NotebookTemplate.BLANK) ? "" : options.template.label,
                            Collections.singletonList(sheet), k -> NotebookExport.read(png), place + 1);
                    bytes = out.toByteArray();
                }
            }
        }
        if (!folder.isDirectory() && !folder.mkdirs()) throw new IOException("Could not make " + folder);
        File target = new File(folder, name(title, place, choice));
        NotebookExport.write(target, bytes);
        return target;
    }

    /** {@code page} (a stack flattened) as a Photoshop file of its layers ({@link PageExport}), its guides drawn when {@code guides}. */
    static byte[] psd(ToneDocument.Snapshot page, Magnets.Tiles tiles, boolean guides) throws IOException {
        int w = page.width, h = page.height;
        int[] grey = new int[256];
        for (int g = 0; g < 256; g++) grey[g] = DotPattern.exportGray(g);
        int[] row = new int[w], cover = new int[w];
        List<PsdWriter.Layer> layers = new ArrayList<>();
        int paper = grey[page.paper];
        layers.add(PsdWriter.layer(PAPER, w, h, (y, g, a) -> { java.util.Arrays.fill(g, (byte) paper); java.util.Arrays.fill(a, (byte) 255); }));
        // The guides on the paper, as the copies draw them ({@link ToneDocument#guided}, the darker of the two): opaque where they show.
        if (guides) {
            PagePicture.Composite ground = PagePicture.Composite.printed(page, 0, 0);
            layers.add(PsdWriter.layer(TEMPLATE, w, h, (y, g, a) -> {
                ground.row(y, row);
                for (int x = 0; x < w; x++) if (row[x] != page.paper) { g[x] = (byte) grey[row[x]]; a[x] = (byte) 255; }
            }));
        }
        // Each pair (a layer of the page, InkWashLayers) kind by kind: its two halves' entries of a kind, side by side in the stack.
        int pairs = (page.layers.size() + 1) / 2, kinds = 1 + ToneDocument.PLANES;
        for (int p = 0; p < pairs; p++) for (int kind = 0; kind < kinds; kind++) {
            int from = p * 2 * kinds + 2 * kind;
            PagePicture.Composite marks = PagePicture.Composite.printed(page, from, from + 2);
            layers.add(PsdWriter.layer((pairs > 1 ? "Layer " + (p + 1) + " " : "") + KINDS[kind], w, h, (y, g, a) -> {
                marks.marks(y, row, cover);
                for (int x = 0; x < w; x++) {
                    int c = cover[x];
                    if (c == 0) continue;
                    // Its own grey, at the coverage that shows on the paper as the PNG shows it there (the smooth greys'
                    // curve is steep in the darks: half black shows far darker than half way); over other marks, near.
                    int tone = Math.min(255, (row[x] * 255 + c / 2) / c), shown = grey[(tone * c + page.paper * (255 - c) + 127) / 255];
                    g[x] = (byte) grey[tone];
                    a[x] = (byte) (grey[tone] == paper ? c : Math.max(0, Math.min(255, Math.round((paper - shown) * 255f / (paper - grey[tone])))));
                }
            }));
        }
        if (tiles != null && page.magnets != null && !page.magnets.isEmpty()) {
            int[] stamped = new int[w * h];
            Magnets.stamp(stamped, w, h, page.magnets, tiles);
            layers.add(PsdWriter.layer(MAGNETS, w, h, (y, g, a) -> {
                for (int x = 0, i = y * w; x < w; x++, i++) { g[x] = (byte) (stamped[i] >> 8); a[x] = (byte) (stamped[i] >>> 24); }
            }));
        }
        layers.removeIf(l -> !l.shows);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PsdWriter.write(out, w, h, NotebookExport.DPI, layers, NotebookExport.greys(page, tiles, guides));
        return out.toByteArray();
    }

    private PageExport() {}
}
