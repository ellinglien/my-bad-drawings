package io.github.mpdairy.monopaint;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.Inflater;

/**
 * A notebook's PDF and PNG copies (docs/archive/superpowers/specs/2026-10-18-notebooks-design.md, "PDF and PNG copies"): grey
 * PNGs any reader opens, at 300 dpi, carrying their page's CRC; a PDF whose every object is where its table says, each
 * page the PNG's image at its real size; only changed pages drawn again, a deleted page's followers renumbered without
 * drawing; renames and deletes followed.
 */
public final class NotebookExportChecks {
    public static void main(String[] args) throws Exception {
        png();
        pdf();
        update();
        magnets();
        exportOnlyNow();
        System.out.println("PASS: notebook copies: written by Export Now alone, never on leaving a notebook or the app (the refocus, 2026-10-24), its PDF to Documents and its PNGs to EXPORT; a page's fridge magnets drawn over its PNG (so its PDF) and into its picture; a page's grey PNG reads in javax.imageio with the PNG copy's smooth greys at 300 dpi and its page's CRC; the PDF's objects are where its table says, a page each at the page's size at 300 dpi showing its PNG's pixels; only pages that changed are drawn again, a deleted page's followers keep their PNGs renumbered, PNGs past the last page go; a renamed notebook's copies follow, a deleted one's go");
    }

    static ToneDocument page(int w, int h, int seed) {
        ToneDocument page = InkWashLayers.blank(w, h);
        page.selectLayer(InkWashLayers.INK); page.begin();
        Random r = new Random(seed);
        for (int i = 0; i < 40; i++) page.setTone(r.nextInt(w), r.nextInt(h), r.nextInt(256));
        page.finish();
        page.selectLayer(InkWashLayers.WASH); page.begin();
        for (int x = 0; x < w / 2; x++) page.setTone(x, h / 2, 128);
        page.finish();
        return page;
    }

    static void png() throws Exception {
        ToneDocument page = page(37, 23, 1);
        byte[] png = NotebookExport.png(page.layerSnapshot(), 0x89abcdefL);
        java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(new ByteArrayInputStream(png));
        check(image != null && image.getWidth() == 37 && image.getHeight() == 23, "A reader opens the PNG");
        for (int y = 0; y < 23; y++) for (int x = 0; x < 37; x++)
            check((image.getRaster().getSample(x, y, 0)) == DotPattern.exportGray(page.compositeTone(x, y)), "Smooth greys, as the PNG copy: " + x + "," + y);
        NotebookExport.Png read = NotebookExport.read(png);
        check(read.crc == 0x89abcdefL && read.width == 37, "It carries its page's CRC");
        String text = new String(png, StandardCharsets.ISO_8859_1);
        check(text.contains("pHYs"), "Its size is 300 dpi");
        byte[] bad = png.clone(); bad[40] ^= 1;
        check(NotebookExport.read(bad) == null && NotebookExport.read(new byte[]{1, 2}) == null, "A damaged PNG isn't read");
    }

    /** The PDF's pages' pixels, read back by following its xref table. */
    static void pdf() throws Exception {
        List<NotebookExport.Png> pngs = new ArrayList<>();
        List<ToneDocument> pages = new ArrayList<>();
        for (int i = 0; i < 3; i++) { ToneDocument p = page(40 + i, 30, i); pages.add(p); pngs.add(NotebookExport.read(NotebookExport.png(p.layerSnapshot(), i))); }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<NotebookPdf.Sheet> sheets = new ArrayList<>();
        for (int i = 0; i < 3; i++) sheets.add(new NotebookPdf.Sheet(i, 20750, 20750, false, "", "", Collections.emptyList()));
        NotebookPdf.write(out, "T", "T", "", sheets, pngs::get);
        byte[] pdf = out.toByteArray();
        String text = new String(pdf, StandardCharsets.ISO_8859_1);
        check(text.startsWith("%PDF-1.4") && text.endsWith("%%EOF\n"), "A PDF");
        int xref = Integer.parseInt(text.substring(text.lastIndexOf("startxref\n") + 10, text.lastIndexOf("\n%%EOF")).trim());
        check(text.startsWith("xref\n", xref), "startxref points at the table");
        String[] rows = text.substring(xref).split("\n");
        int count = Integer.parseInt(rows[1].split(" ")[1]);
        check(count == 5 + 3 * 3 + 4 + 1, "Every object in the table: the book's five, three a page, a day and its three pages in the outline");
        for (int n = 1; n < count; n++) {
            int offset = Integer.parseInt(rows[2 + n].substring(0, 10));
            check(text.startsWith(n + " 0 obj", offset), "Object " + n + " is where the table says");
        }
        check(text.contains("/MediaBox [0 0 9.60 7.20]"), "A page at its size at 300 dpi (40 × 30 px is 9.6 × 7.2 pt)");
        int at = 0;
        for (int i = 0; i < 3; i++) {
            at = text.indexOf("/Subtype /Image", at);
            int length = Integer.parseInt(text.substring(text.indexOf("/Length ", at) + 8, text.indexOf(" >>\nstream\n", at)));
            int start = text.indexOf(">>\nstream\n", at) + 10;
            byte[] data = Arrays.copyOfRange(pdf, start, start + length);
            int w = 40 + i, h = 30;
            byte[] raw = new byte[(w + 1) * h];
            Inflater inflater = new Inflater(); inflater.setInput(data); int got = inflater.inflate(raw); inflater.end();
            check(got == raw.length, "Its image inflates to its rows");
            byte[] previous = new byte[w];
            for (int y = 0; y < h; y++) {
                int type = raw[y * (w + 1)];
                byte[] row = new byte[w];
                for (int x = 0; x < w; x++) {
                    int v = raw[y * (w + 1) + 1 + x] & 255;
                    if (type == 1) v += x > 0 ? row[x - 1] & 255 : 0;
                    else if (type == 2) v += previous[x] & 255;
                    row[x] = (byte) v;
                    check((row[x] & 255) == DotPattern.exportGray(pages.get(i).compositeTone(x, y)), "PDF page " + (i + 1) + " shows its page's greys");
                }
                previous = row;
            }
            at = start + length;
        }
    }

    static void update() throws Exception {
        File folder = Files.createTempDirectory("export").toFile();
        try {
            List<PackedPage> pages = new ArrayList<>();
            for (int i = 0; i < 5; i++) pages.add(PackedPage.of(DrawingBook.encode(page(30, 20, 10 + i))));
            NotebookExport.Done first = NotebookExport.update(folder, "Oct notebook", pages);
            check(first.drawn == 5 && first.pdf.isFile() && new File(folder, "Oct notebook/005.png").isFile(), "Every page drawn the first time");
            byte[] page3 = Files.readAllBytes(new File(folder, "Oct notebook/003.png").toPath());
            NotebookExport.Done again = NotebookExport.update(folder, "Oct notebook", pages);
            check(again.drawn == 0 && again.kept == 5, "Nothing changed, nothing drawn");
            pages.set(1, PackedPage.of(DrawingBook.encode(page(30, 20, 99))));
            check(NotebookExport.update(folder, "Oct notebook", pages).drawn == 1, "One page changed, one drawn");
            pages.remove(0);
            NotebookExport.Done shifted = NotebookExport.update(folder, "Oct notebook", pages);
            check(shifted.drawn == 0 && Arrays.equals(page3, Files.readAllBytes(new File(folder, "Oct notebook/002.png").toPath()))
                    && !new File(folder, "Oct notebook/005.png").exists(), "A page deleted: the rest renumbered without drawing, the last PNG gone");
            for (int i = 0; i < 4; i++) {
                java.util.zip.CRC32 c = new java.util.zip.CRC32(); c.update(pages.get(i).bytes());
                check(NotebookExport.read(Files.readAllBytes(new File(folder, "Oct notebook/" + NotebookExport.pageName(i)).toPath())).crc == c.getValue(), "PNG " + (i + 1) + " is its page");
            }
            String[] left = new File(folder, "Oct notebook").list();
            check(left.length == 5, "Nothing left over but what the PDF said: " + Arrays.toString(left));
            NotebookExport.rename(folder, "Oct notebook", "Harbour");
            check(new File(folder, "Harbour/001.png").isFile() && new File(folder, "Harbour.pdf").isFile() && !new File(folder, "Oct notebook.pdf").exists(), "Renamed: its copies follow");
            NotebookExport.delete(folder, "Harbour");
            check(!new File(folder, "Harbour").exists() && !new File(folder, "Harbour.pdf").exists(), "Deleted: its copies go");
        } finally { NotebookMigrationChecks.delete(folder); }
    }

    /** A page's fridge magnets: over its PNG copy, as Magnets.stamp draws them, and averaged into its picture. */
    static void magnets() throws Exception {
        ToneDocument page = InkWashLayers.blank(400, 300);
        page.placeMagnets(Magnets.of(Collections.singletonList(new Magnets.Magnet("ink", 200, 150, 0, 0))));
        // A solid ink tile, so what it covers is plain.
        Magnets.Tiles tiles = m -> { int[] b = Magnets.bounds(m); int w = b[2] - b[0], h = b[3] - b[1]; int[] argb = new int[w * h]; Arrays.fill(argb, 0xff000000); return new Magnets.Tile(argb, w, h); };
        java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(new ByteArrayInputStream(NotebookExport.png(page.layerSnapshot(), 1, tiles)));
        check((image.getRGB(200, 150) & 255) == 0 && (image.getRGB(5, 5) & 255) == 255, "The magnet is drawn over the PNG copy");
        check(javax.imageio.ImageIO.read(new ByteArrayInputStream(NotebookExport.png(page.layerSnapshot(), 1))).getRGB(200, 150) == 0xffffffff, "Without tiles, no magnets");
        PagePicture with = PagePicture.of(page.layerSnapshot(), tiles), without = PagePicture.of(page.layerSnapshot());
        check(with.tone(200 * with.width / 400, 150 * with.height / 300) == 0 && without.tone(200 * with.width / 400, 150 * with.height / 300) == 255 && with.tone(1, 1) == 255,
                "The page's picture shows its magnets");
    }
    /**
     * Export only when asked (the owner, 2026-10-24: "export only when you need it"): nothing queues the copies but
     * Export Now, which queues them the way the automatic path did: the PNGs in EXPORT/<app folder>, the PDF beside the
     * notebooks (Documents/<app folder>), through {@link NotebookExport#update}, which {@link #update} checks writes both.
     */
    static void exportOnlyNow() throws IOException {
        String app = DrawLayoutChecks.source("PaintActivity"), copies = DrawLayoutChecks.source("AutoExport");
        int at = app.indexOf("AutoExport.notebook(");
        check(at >= 0 && at == app.lastIndexOf("AutoExport.notebook(") && !app.contains("AutoExport.notebooks("), "One place queues a notebook's copies, none queues the migrated ones");
        check(app.lastIndexOf("    void exportNow()", at) > app.lastIndexOf("\n    }\n", at), "That place is Export Now");
        check(!copies.contains("static void notebooks(") && copies.contains("NotebookExport.update(folder(), DrawingFiles.name(path), pages") && copies.contains("documents(path)));"),
                "Export Now writes the PNGs to EXPORT and the PDF to Documents, as the automatic copies did");
    }

    static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
