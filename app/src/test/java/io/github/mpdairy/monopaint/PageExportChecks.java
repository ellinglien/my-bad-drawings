package io.github.mpdairy.monopaint;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Export Page (2026-10-28, {@link PageExport}): a page's PNG is byte for byte the notebook's copy of it, its PDF that PNG
 * numbered as the page, and its Layers copy a Photoshop file read back here by the format's rules: Grayscale at 300 dpi,
 * its layers named and in the page's order, every channel's PackBits decoding to the page's size, its merged image the
 * PNG's greys, and its layers laid Normal over one another within a few greys of it. With {@code sample <file>}, writes
 * a sample page's Layers copy (pen, washes, pencil, opaque paint, lined, a magnet) there for a desktop reader.
 */
public final class PageExportChecks {
    static int checks;
    static void check(boolean ok, String what) { checks++; if (!ok) throw new AssertionError(what); }

    public static void main(String[] args) throws Exception {
        if (args.length == 2 && args[0].equals("sample")) {
            ToneDocument page = sample(1200, 1600, 7);
            page.placeGuides(NotebookTemplate.LINED.shared(page.width, page.height));
            Files.write(new File(args[1]).toPath(), PageExport.psd(page.layerSnapshot(), tiles(), true));
            System.out.println("Wrote " + args[1]);
            return;
        }
        File folder = Files.createTempDirectory("page-export").toFile();
        try {
            List<PackedPage> pages = new ArrayList<>();
            ToneDocument first = sample(240, 320, 3);
            pages.add(PackedPage.of(DrawingBook.encode(first)));
            pages.add(PackedPage.of(DrawingBook.encode(old(240, 320))));
            NotebookOptions lined = NotebookOptions.DEFAULT.withTemplate(NotebookTemplate.LINED);
            // The PNG: the notebook's copy of that page, byte for byte.
            File png = PageExport.write(folder, "Harbour", 0, PageExport.PNG, pages, lined, null, null, tiles());
            File notebook = new File(folder, "notebook");
            NotebookExport.update(notebook, "Harbour", pages, tiles(), null, lined);
            byte[] pngBytes = Files.readAllBytes(png.toPath());
            check(png.getName().equals("Harbour p1.png") && Arrays.equals(pngBytes, Files.readAllBytes(new File(notebook, "Harbour/001.png").toPath())), "The page's PNG is the notebook's copy of it");
            check(Arrays.equals(Files.readAllBytes(PageExport.write(folder, "Harbour", 1, PageExport.PNG, pages, lined, null, null, tiles()).toPath()),
                    Files.readAllBytes(new File(notebook, "Harbour/002.png").toPath())), "Page 2's too");
            // The PDF: one page, numbered as the page, its image the PNG's data.
            File pdf = PageExport.write(folder, "Harbour", 1, PageExport.PDF, pages, lined, null, null, tiles());
            String text = new String(Files.readAllBytes(pdf.toPath()), StandardCharsets.ISO_8859_1);
            check(pdf.getName().equals("Harbour p2.pdf") && text.startsWith("%PDF-1.4") && text.endsWith("%%EOF\n") && text.contains("/Count 1 ") && text.contains("/St 2") && text.contains("Harbour, page 2")
                    &&text.contains("/MediaBox [0 0 57.60 76.80]"), "A PDF of the one page at 300 dpi, labelled page 2");
            // Layers.
            File psd = PageExport.write(folder, "Harbour", 0, PageExport.LAYERS, pages, lined, null, null, tiles());
            check(psd.getName().equals("Harbour p1.psd"), "Named for the notebook and the page");
            int[] greys = greys(pngBytes);
            Psd read = read(Files.readAllBytes(psd.toPath()));
            check(read.names.equals(Arrays.asList("Paper", "Template", "Dry Media", "Washes", "Pen", "Paint", "Magnets")), "The layers, bottom first: " + read.names);
            compare(read, greys, "page 1");
            Psd two = read(Files.readAllBytes(PageExport.write(folder, "Harbour", 1, PageExport.LAYERS, pages, lined, null, null, tiles()).toPath()));
            check(two.names.equals(Arrays.asList("Paper", "Template", "Layer 1 Old Paint", "Layer 2 Pen")), "An old page's layers named by number, its paint of no kind Old Paint: " + two.names);
            compare(two, greys(Files.readAllBytes(new File(notebook, "Harbour/002.png").toPath())), "page 2");
            check(PageExport.name("Harbour", 4, PageExport.LAYERS).equals("Harbour p5.psd") && PageExport.page(4, PageExport.PDF).equals("p5.pdf"), "Its names");
            byte[] row = new byte[300];
            for (int i = 0; i < row.length; i++) row[i] = (byte) (i < 140 ? 7 : i % 3 == 0 ? i : 200);
            java.io.ByteArrayOutputStream packed = new java.io.ByteArrayOutputStream();
            PsdWriter.packBits(row, packed);
            byte[] unpacked = new byte[row.length];
            check(unpack(ByteBuffer.wrap(packed.toByteArray()), unpacked) == packed.size() && Arrays.equals(row, unpacked), "PackBits round trip");
        } finally { NotebookMigrationChecks.delete(folder); }
        System.out.println("PASS: Export Page (" + checks + " checks): a page's PNG is the notebook's copy byte for byte, its PDF that page numbered as it is; its Layers copy reads back as Grayscale PSD layers, Paper, Template, then each kind in the page's order, Magnets on top, its merged image the PNG's greys and its layers laid Normal within " + worst + " greys of them (" + String.format("%.3f", meanDiff) + " on average)");
    }

    /** A page drawn with the app's brushes: washes and a fineliner (NotebookFixture), a pencil, a block of opaque paint, lined, a magnet. */
    static ToneDocument sample(int w, int h, long seed) {
        ToneDocument page = NotebookFixture.sketch(w, h, seed);
        Random random = new Random(seed);
        WatercolorSession session = new WatercolorSession();
        page.selectLayer(InkWashLayers.INK);
        for (int i = 0; i < 6; i++) NotebookFixture.stroke(page, NotebookFixture.brush("Pencil", "B pencil"), Math.max(2, w / 200.0), .8, session, random);
        KindLayersChecks.block(page, InkWashLayers.WASH, ToneDocument.KIND_PAINT | ToneDocument.KIND_OPAQUE, w / 5, h / 3, w / 4, h / 10, 230);
        page.placeMagnets(Magnets.of(Collections.singletonList(new Magnets.Magnet("ink", w * 2 / 3, h * 3 / 4, 0, 0))));
        return page;
    }
    /** An old page: two pairs, its first's ink of no kind (the base), its second's pen. */
    static ToneDocument old(int w, int h) {
        ToneDocument page = InkWashLayers.blank(w, h);
        page.selectLayer(InkWashLayers.INK); page.begin();
        int[] grays = new int[40 * 40]; Arrays.fill(grays, 90);
        page.setTones(20, 20, 40, 40, grays);
        page.finish();
        InkWashLayers.add(page, true);
        KindLayersChecks.block(page, 3, ToneDocument.KIND_PEN, 40, 40, 30, 60, 0);
        return page;
    }
    /** A grey tile with a soft (half-covering) edge, so the Magnets layer has partial coverage. */
    static Magnets.Tiles tiles() {
        return m -> {
            int[] b = Magnets.bounds(m); int w = b[2] - b[0], h = b[3] - b[1];
            int[] argb = new int[w * h];
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) argb[y * w + x] = x < 3 || y < 3 || x >= w - 3 || y >= h - 3 ? 0x80404040 : 0xff202020;
            return new Magnets.Tile(argb, w, h);
        };
    }

    static int worst;
    static double meanDiff;
    /** {@code psd}'s merged image is the PNG's greys; its layers laid Normal come within a few greys of them. */
    static void compare(Psd psd, int[] greys, String what) {
        check(Arrays.equals(psd.merged, greys), "The merged image is the PNG's greys, " + what);
        int[] laid = new int[greys.length];
        long sum = 0; int most = 0;
        for (int i = 0; i < laid.length; i++) {
            int result = 0;
            for (int l = 0; l < psd.grey.size(); l++) { int a = psd.alpha.get(l)[i] & 255, g = psd.grey.get(l)[i] & 255; result = (g * a + result * (255 - a) + 127) / 255; }
            int d = Math.abs(result - greys[i]); sum += d; most = Math.max(most, d);
        }
        worst = Math.max(worst, most); meanDiff = Math.max(meanDiff, sum / (double) laid.length);
        check(most <= 12 && sum / (double) laid.length < .5, "The layers laid Normal show the PNG within a few greys, " + what + ": at most " + most + ", " + sum / (double) laid.length + " on average");
    }

    static int[] greys(byte[] png) throws Exception {
        java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(new ByteArrayInputStream(png));
        int w = image.getWidth(), h = image.getHeight();
        int[] out = new int[w * h];
        // A palette's greys by colour; 8-bit grey by its samples (getRGB would take it as linear grey).
        boolean palette = image.getColorModel() instanceof java.awt.image.IndexColorModel;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) out[y * w + x] = palette ? image.getRGB(x, y) >> 8 & 255 : image.getRaster().getSample(x, y, 0);
        return out;
    }

    /** A PSD as this reads it: its layers' names, grey and alpha channels, and its merged image. */
    static final class Psd {
        final List<String> names = new ArrayList<>();
        final List<byte[]> grey = new ArrayList<>(), alpha = new ArrayList<>();
        int[] merged;
    }
    /** Reads {@code file} by Adobe's specification, checking each part as it goes. */
    static Psd read(byte[] file) {
        ByteBuffer b = ByteBuffer.wrap(file);
        byte[] sig = new byte[4]; b.get(sig);
        check(new String(sig, StandardCharsets.US_ASCII).equals("8BPS") && b.getShort() == 1, "A PSD, version 1");
        b.position(b.position() + 6);
        int channels = b.getShort(), h = b.getInt(), w = b.getInt(), depth = b.getShort(), mode = b.getShort();
        check(channels == 1 && depth == 8 && mode == 1, "Grayscale, 8 bits, one channel");
        skip(b);
        int resources = b.getInt(), end = b.position() + resources;
        b.position(b.position() + 4);
        check(b.getShort() == 1005, "Its resolution");
        b.position(b.position() + 2 + 4);
        check(b.getInt() == NotebookExport.DPI << 16, "300 dpi");
        b.position(end);
        int section = b.getInt(), sectionEnd = b.position() + section, info = b.getInt();
        check(info % 2 == 0, "The layers' part an even length");
        int count = b.getShort();
        Psd psd = new Psd();
        int[][] ids = new int[count][];
        for (int l = 0; l < count; l++) {
            check(b.getInt() == 0 && b.getInt() == 0 && b.getInt() == h && b.getInt() == w, "A layer the page's size");
            int n = b.getShort(); ids[l] = new int[n];
            for (int c = 0; c < n; c++) { ids[l][c] = b.getShort(); b.getInt(); }
            b.get(sig); byte[] key = new byte[4]; b.get(key);
            check(new String(sig, StandardCharsets.US_ASCII).equals("8BIM") && new String(key, StandardCharsets.US_ASCII).equals("norm") && (b.get() & 255) == 255, "Normal, full opacity");
            b.position(b.position() + 3);
            int extra = b.getInt(), extraEnd = b.position() + extra;
            skip(b);
            skip(b);
            byte[] name = new byte[b.get() & 255]; b.get(name);
            psd.names.add(new String(name, StandardCharsets.ISO_8859_1));
            b.position(extraEnd);
        }
        for (int l = 0; l < count; l++) for (int id : ids[l]) {
            byte[] channel = channel(b, w, h);
            if (id == -1) psd.alpha.add(channel); else if (id == 0) psd.grey.add(channel); else check(false, "Only grey and transparency");
        }
        check(psd.grey.size() == count && psd.alpha.size() == count, "Each layer's two channels");
        b.position(sectionEnd);
        byte[] merged = channel(b, w, h);
        psd.merged = new int[merged.length];
        for (int i = 0; i < merged.length; i++) psd.merged[i] = merged[i] & 255;
        check(!b.hasRemaining(), "Nothing after the merged image");
        return psd;
    }
    /** A channel: PackBits, its rows' lengths, then its rows, each decoding to {@code w} bytes. */
    static byte[] channel(ByteBuffer b, int w, int h) {
        check(b.getShort() == 1, "PackBits");
        int[] lengths = new int[h];
        for (int y = 0; y < h; y++) lengths[y] = b.getShort() & 0xffff;
        byte[] out = new byte[w * h], row = new byte[w];
        for (int y = 0; y < h; y++) {
            int at = b.position();
            check(unpack(b, row) == lengths[y], "A row's length as said");
            b.position(at + lengths[y]);
            System.arraycopy(row, 0, out, y * w, w);
        }
        return out;
    }
    /** Past a part that starts with its length. */
    static void skip(ByteBuffer b) { int n = b.getInt(); b.position(b.position() + n); }
    /** One PackBits row into {@code row} (filled exactly); the bytes it took. */
    static int unpack(ByteBuffer b, byte[] row) {
        int start = b.position(), i = 0;
        while (i < row.length) {
            int n = b.get();
            if (n >= 0) { check(i + n + 1 <= row.length, "A literal within the row"); b.get(row, i, n + 1); i += n + 1; }
            else if (n != -128) { byte v = b.get(); check(i + 1 - n <= row.length, "A run within the row"); for (int k = 0; k < 1 - n; k++) row[i++] = v; }
        }
        return b.position() - start;
    }
}
