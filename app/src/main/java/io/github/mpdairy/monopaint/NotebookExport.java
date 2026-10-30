package io.github.mpdairy.monopaint;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.time.LocalDate;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * A notebook's copies for printing and sending (docs/archive/superpowers/specs/2026-10-18-notebooks-design.md, "PDF and PNG
 * copies"): a grey PNG a page in a folder named for the notebook ({@code 001.png} …), each carrying its page's CRC-32,
 * and beside it one PDF of them all, a page each at the page's real size at 300 dpi. A page whose PNG already carries its
 * CRC isn't drawn again (one that moved keeps its PNG under its new number), so the PDF is stitched from the PNGs: a PNG's
 * image data, deflated with its row filters, is a PDF image as it is ({@code FlateDecode} with the PNG predictor). Smooth
 * greys as the PNG copy always had ({@link DotPattern#exportGray}). Pure Java, timed on the desktop; run on
 * {@code AutoExport}'s worker.
 */
final class NotebookExport {
    /** The page's pixels a printed inch. */
    static final int DPI = 300;
    private static final byte[] SIGNATURE = {(byte) 137, 'P', 'N', 'G', 13, 10, 26, 10};
    /** The PNG's text key for its page's CRC-32. */
    static final String KEY = "Inkwash page";

    // PNG

    /** A page's grey PNG, carrying {@code crc} (its page's CRC-32). */
    static byte[] png(ToneDocument.Snapshot page, long crc) { return png(page, crc, null); }
    /** As {@link #png(ToneDocument.Snapshot, long)}, the page's fridge magnets drawn by {@code tiles} over it ({@link Magnets#stamp}; null: none). */
    static byte[] png(ToneDocument.Snapshot page, long crc, Magnets.Tiles tiles) { return png(page, crc, tiles, false); }
    /** As above, with the page's template guides under its marks when {@code guides} (every notebook prints them; the page must hold them). */
    static byte[] png(ToneDocument.Snapshot page, long crc, Magnets.Tiles tiles, boolean guides) {
        int w = page.width, h = page.height;
        byte[] greys = greys(page, tiles, guides);
        // At most 16 greys (pen and pencil on plain paper, mostly): a palette of exactly those, 1, 2 or 4 bits a pixel,
        // lossless and far smaller; more (washes, magnets' soft edges): 8-bit grey as always.
        boolean[] seen = new boolean[256];
        int n = 0;
        for (int i = 0; i < greys.length && n <= 16; i++) if (!seen[greys[i] & 255]) { seen[greys[i] & 255] = true; n++; }
        byte[] palette = null;
        int[] index = null;
        int bits = 8;
        if (n <= 16) {
            palette = new byte[n]; index = new int[256];
            for (int g = 0, k = 0; g < 256; g++) if (seen[g]) { index[g] = k; palette[k++] = (byte) g; }
            bits = n <= 2 ? 1 : n <= 4 ? 2 : 4;
        }
        int stride = (w * bits + 7) / 8, perByte = 8 / Math.min(8, bits);
        byte[] previous = new byte[stride], current = new byte[stride], filtered = new byte[stride + 1], best = new byte[stride + 1];
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION);
        try (DeflaterOutputStream zip = new DeflaterOutputStream(data, deflater, 65536)) {
            for (int y = 0; y < h; y++) {
                if (palette == null) System.arraycopy(greys, y * w, current, 0, w);
                else {
                    java.util.Arrays.fill(current, (byte) 0);
                    for (int x = 0; x < w; x++) current[x / perByte] |= index[greys[y * w + x] & 255] << (8 - bits - x % perByte * bits);
                }
                // Of None, Sub and Up, the row filter whose bytes sum smallest (PNG's usual guess).
                long bestSum = Long.MAX_VALUE;
                for (int type = 0; type < 3; type++) {
                    filtered[0] = (byte) type; long sum = 0;
                    for (int x = 0; x < stride; x++) {
                        int v = current[x] & 255;
                        if (type == 1) v -= x > 0 ? current[x - 1] & 255 : 0;
                        else if (type == 2) v -= previous[x] & 255;
                        filtered[x + 1] = (byte) v; sum += Math.abs((byte) v);
                    }
                    if (sum < bestSum) { bestSum = sum; System.arraycopy(filtered, 0, best, 0, stride + 1); }
                }
                zip.write(best, 0, stride + 1);
                byte[] t = previous; previous = current; current = t;
            }
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
        finally { deflater.end(); }
        try {
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            png.write(SIGNATURE);
            ByteArrayOutputStream header = new ByteArrayOutputStream();
            DataOutputStream ihdr = new DataOutputStream(header);
            ihdr.writeInt(w); ihdr.writeInt(h); ihdr.writeByte(bits); ihdr.writeByte(palette == null ? 0 : 3); ihdr.writeByte(0); ihdr.writeByte(0); ihdr.writeByte(0);
            chunk(png, "IHDR", header.toByteArray());
            if (palette != null) {
                byte[] plte = new byte[3 * n];
                for (int k = 0; k < n; k++) plte[3 * k] = plte[3 * k + 1] = plte[3 * k + 2] = palette[k];
                chunk(png, "PLTE", plte);
            }
            // 300 dpi, as pixels a metre, so it prints at its real size.
            ByteArrayOutputStream phys = new ByteArrayOutputStream();
            DataOutputStream p = new DataOutputStream(phys);
            int perMetre = (int) Math.round(DPI / .0254); p.writeInt(perMetre); p.writeInt(perMetre); p.writeByte(1);
            chunk(png, "pHYs", phys.toByteArray());
            chunk(png, "tEXt", (KEY + "\0" + String.format(Locale.ROOT, "%08x", crc)).getBytes(StandardCharsets.ISO_8859_1));
            chunk(png, "IDAT", data.toByteArray());
            chunk(png, "IEND", new byte[0]);
            return png.toByteArray();
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }
    /**
     * The page's greys as {@link #png} writes them, a byte a pixel, row by row: its composite in smooth greys, its guides
     * under its marks when {@code guides}, its magnets stamped over by {@code tiles} (null: none). Also a page's Layers
     * copy's merged image ({@link PageExport}).
     */
    static byte[] greys(ToneDocument.Snapshot page, Magnets.Tiles tiles, boolean guides) {
        int w = page.width, h = page.height;
        PagePicture.Composite composite = guides ? PagePicture.Composite.printed(page) : new PagePicture.Composite(page);
        int[] row = new int[w];
        // The page's greys, a byte a pixel; with magnets, the whole page as its PNG copy shows it first, its tiles stamped on.
        byte[] greys = new byte[w * h];
        if (tiles != null && page.magnets != null && !page.magnets.isEmpty()) {
            int[] stamped = new int[w * h];
            for (int y = 0; y < h; y++) {
                composite.row(y, row);
                for (int x = 0; x < w; x++) { int g = DotPattern.exportGray(row[x]); stamped[y * w + x] = 0xff000000 | g << 16 | g << 8 | g; }
            }
            Magnets.stamp(stamped, w, h, page.magnets, tiles);
            for (int i = 0; i < greys.length; i++) greys[i] = (byte) (stamped[i] >> 8 & 255);
        } else for (int y = 0; y < h; y++) {
            composite.row(y, row);
            for (int x = 0; x < w; x++) greys[y * w + x] = (byte) DotPattern.exportGray(row[x]);
        }
        return greys;
    }
    private static void chunk(OutputStream out, String type, byte[] data) throws IOException {
        DataOutputStream d = new DataOutputStream(out);
        byte[] name = type.getBytes(StandardCharsets.US_ASCII);
        d.writeInt(data.length); d.write(name); d.write(data);
        CRC32 crc = new CRC32(); crc.update(name); crc.update(data);
        d.writeInt((int) crc.getValue());
    }

    /** A grey PNG as {@link #png} writes it: its size, its page's CRC (-1 if none), its image data, its bits a pixel and its palette's greys (null: 8-bit grey). */
    static final class Png {
        final int width, height; final long crc; final byte[] data; final int bits; final byte[] palette;
        Png(int width, int height, long crc, byte[] data) { this(width, height, crc, data, 8, null); }
        Png(int width, int height, long crc, byte[] data, int bits, byte[] palette) { this.width = width; this.height = height; this.crc = crc; this.data = data; this.bits = bits; this.palette = palette; }
    }
    /** Reads {@link #png}'s kinds of PNG (8-bit grey, or a palette of greys at 1, 2 or 4 bits; not interlaced); null for any other file. */
    static Png read(byte[] file) {
        if (file.length < 8) return null;
        for (int i = 0; i < 8; i++) if (file[i] != SIGNATURE[i]) return null;
        java.nio.ByteBuffer b = java.nio.ByteBuffer.wrap(file);
        b.position(8);
        int width = 0, height = 0, bits = 8; long crc = -1;
        byte[] palette = null;
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        boolean header = false, indexed = false;
        while (b.remaining() >= 12) {
            int length = b.getInt();
            if (length < 0 || length > b.remaining() - 8) return null;
            byte[] type = new byte[4]; b.get(type);
            byte[] body = new byte[length]; b.get(body);
            int check = b.getInt();
            CRC32 c = new CRC32(); c.update(type); c.update(body);
            if ((int) c.getValue() != check) return null;
            String name = new String(type, StandardCharsets.US_ASCII);
            if (name.equals("IHDR")) {
                java.nio.ByteBuffer h = java.nio.ByteBuffer.wrap(body);
                width = h.getInt(); height = h.getInt(); bits = h.get(); int colour = h.get();
                indexed = colour == 3;
                if (!(bits == 8 && colour == 0 || indexed && (bits == 1 || bits == 2 || bits == 4)) || h.get() != 0 || h.get() != 0 || h.get() != 0) return null;
                header = true;
            } else if (name.equals("PLTE")) {
                if (length % 3 != 0 || length == 0 || length > 3 * 16) return null;
                palette = new byte[length / 3];
                for (int k = 0; k < palette.length; k++) {
                    if (body[3 * k] != body[3 * k + 1] || body[3 * k] != body[3 * k + 2]) return null;
                    palette[k] = body[3 * k];
                }
            } else if (name.equals("tEXt")) {
                String text = new String(body, StandardCharsets.ISO_8859_1);
                if (text.startsWith(KEY + "\0")) try { crc = Long.parseLong(text.substring(KEY.length() + 1), 16); } catch (NumberFormatException ignored) { }
            } else if (name.equals("IDAT")) data.write(body, 0, body.length);
            else if (name.equals("IEND")) return header && width > 0 && height > 0 && indexed == (palette != null) && (!indexed || palette.length <= 1 << bits) ? new Png(width, height, crc, data.toByteArray(), bits, indexed ? palette : null) : null;
        }
        return null;
    }

    // PDF

    /** A page's PNG, read when its turn comes ({@link NotebookPdf#write}). */
    interface PngAt { Png get(int page) throws IOException; }
    /** A PNG's image data as a PDF image's dictionary, up to its stream: 8-bit grey, or its palette of greys indexed. */
    static String imageDictionary(Png png) {
        StringBuilder space = new StringBuilder("/DeviceGray");
        if (png.palette != null) {
            space = new StringBuilder("[/Indexed /DeviceGray " + (png.palette.length - 1) + " <");
            for (byte g : png.palette) space.append(String.format(Locale.ROOT, "%02x", g & 255));
            space.append(">]");
        }
        return "<< /Type /XObject /Subtype /Image /Width " + png.width + " /Height " + png.height + " /ColorSpace " + space + " /BitsPerComponent " + png.bits
                + " /Filter /FlateDecode /DecodeParms << /Predictor 15 /Colors 1 /BitsPerComponent " + png.bits + " /Columns " + png.width + " >> /Length " + png.data.length + " >>\nstream\n";
    }
    /** {@code pixels} at {@link #DPI} in points (72 an inch). */
    static String points(int pixels) { return String.format(Locale.ROOT, "%.2f", pixels * 72.0 / DPI); }
    static final class Counting extends java.io.FilterOutputStream {
        long count;
        Counting(OutputStream out) { super(out); }
        @Override public void write(int b) throws IOException { out.write(b); count++; }
        @Override public void write(byte[] b, int off, int len) throws IOException { out.write(b, off, len); count += len; }
        void text(String s) throws IOException { byte[] b = s.getBytes(StandardCharsets.ISO_8859_1); write(b, 0, b.length); }
    }

    // A notebook's copies

    /** What {@link #update} did: pages drawn anew, PNGs kept as they were. */
    static final class Done {
        final int drawn, kept; final File pdf;
        Done(int drawn, int kept, File pdf) { this.drawn = drawn; this.kept = kept; this.pdf = pdf; }
    }
    /** {@code n}th page's PNG name: 001.png … */
    static String pageName(int n) { return String.format(Locale.ROOT, "%03d.png", n + 1); }

    /**
     * Brings {@code name}'s copies in {@code folder} (EXPORT/Inkwash) in step with its {@code pages} (packed): each
     * page's PNG drawn only when no PNG there carries its CRC, PNGs past the last page removed, then the PDF written
     * anew. A page that doesn't decode keeps no PNG and no PDF page. Blank pages at the end have none either
     * ({@link BlankPages#drawn}: the notebook's page ready to draw on isn't printed).
     */
    static Done update(File folder, String name, List<PackedPage> pages) throws IOException { return update(folder, name, pages, null); }
    /** As {@link #update(File, String, List)}, the pages' fridge magnets drawn by {@code tiles} (null: none). */
    static Done update(File folder, String name, List<PackedPage> pages, Magnets.Tiles tiles) throws IOException { return update(folder, name, pages, tiles, null); }

    /** The front cover's PNG and the back cover's: first and last in the folder, as in the PDF. */
    static final String FRONT_PNG = "00-cover.png", BACK_PNG = "zz-back.png";
    /**
     * A notebook's covers for its copies: its name (the covers' seed and their label's words), their design, its pages'
     * size, each cover's page (null: nothing drawn) and the cover picture's page (-1: none).
     */
    static final class Covers {
        final String name; final CoverDesign design; final int width, height; final PackedPage front, back; final int picture;
        Covers(String name, CoverDesign design, int width, int height, PackedPage front, PackedPage back, int picture) {
            this.name = name; this.design = design; this.width = width; this.height = height; this.front = front; this.back = back; this.picture = picture;
        }
    }
    /**
     * As {@link #update(File, String, List, Magnets.Tiles)}, the PDF starting with the front cover and ending with the back
     * ({@code covers}; null: none), each drawn on its paper ({@link NotebookCovers}) into its own PNG ({@link #FRONT_PNG},
     * {@link #BACK_PNG}), drawn again only when what it is made from changed ({@link NotebookCovers#key}).
     */
    static Done update(File folder, String name, List<PackedPage> pages, Magnets.Tiles tiles, Covers covers) throws IOException { return update(folder, name, pages, tiles, covers, NotebookOptions.DEFAULT); }
    /**
     * As above, the pages' template guides ({@code options}) drawn under their marks in the PNGs and the PDF when the
     * notebook prints them (the covers excepted). A PNG carries its page's CRC mixed
     * with the template's ({@link #pageKey}), so changing the template draws the pages again and nothing else does.
     */
    static Done update(File folder, String name, List<PackedPage> pages, Magnets.Tiles tiles, Covers covers, NotebookOptions options) throws IOException { return update(folder, name, pages, tiles, covers, options, null); }
    /** As above, each page under its own template under Random ({@code pageTemplates}: each page's key, {@link NotebookOptions#pageTemplate}; null: none). */
    static Done update(File folder, String name, List<PackedPage> pages, Magnets.Tiles tiles, Covers covers, NotebookOptions options, List<String> pageTemplates) throws IOException {
        return update(folder, name, pages, tiles, covers, options, pageTemplates, null);
    }
    /**
     * As above, a stack of layers ({@code tracing}'s stacks, {@link PageStacks}; null: every page its own) printed as one
     * page, its layers flattened bottom first under the bottom page's guides, a Hidden layer left out: a PNG and a PDF page
     * a stack. A page alone keeps its PNG's key (its CRC); a stack's mixes its layers'.
     */
    static Done update(File folder, String name, List<PackedPage> pages, Magnets.Tiles tiles, Covers covers, NotebookOptions options, List<String> pageTemplates, TracingPaper tracing) throws IOException {
        return update(folder, name, pages, tiles, covers, options, pageTemplates, tracing, new Documents(folder, name, LocalDate.now()));
    }
    /** Where a notebook's PDF goes ({@link NotebookPdf}): a folder (the Supernote's Documents), its title, and the day it is. */
    static final class Documents {
        final File folder; final String title; final LocalDate today;
        Documents(File folder, String title, LocalDate today) { this.folder = folder; this.title = title; this.today = today; }
    }
    /**
     * As above, the PDF in {@code documents} ({@link NotebookPdf#update}: its words, outline and days, the back cover left
     * out so pages keep their numbers), with what it says of each page kept in the PNGs' folder ({@link NotebookPdf#STATE})
     * so a kept PNG's page isn't read again; the PDF the app wrote beside the PNGs before goes once this is written,
     * unless it has handwriting.
     */
    static Done update(File folder, String name, List<PackedPage> pages, Magnets.Tiles tiles, Covers covers, NotebookOptions options, List<String> pageTemplates, TracingPaper tracing, Documents documents) throws IOException {
        List<PackedPage> all = pages;
        PageStacks stacks = tracing != null && tracing.count() == all.size() ? tracing.stacks() : new PageStacks(new boolean[all.size()]);
        int drawn = BlankPages.drawn(pages, options.paper).size(), sheets = stacks.of(drawn - 1) + 1;
        NotebookTemplate[] templates = new NotebookTemplate[sheets];
        String[] named = new String[sheets];
        for (int s = 0; s < sheets; s++) {
            int b = stacks.first(s);
            String key = pageTemplates != null && b < pageTemplates.size() ? pageTemplates.get(b) : null;
            templates[s] = options.pageTemplate(key);
            NotebookTemplate own = options.pageTemplate(key);
            named[s] = own.equals(NotebookTemplate.BLANK) ? "" : own.label;
        }
        File pngs = new File(folder, name);
        if (!pngs.isDirectory() && !pngs.mkdirs()) throw new IOException("Could not make " + pngs);
        // What is there now, by the CRC each PNG carries (pages alike, blank ones between drawn ones, keep one each).
        Map<Long, java.util.ArrayDeque<File>> found = new HashMap<>();
        File[] there = pngs.listFiles();
        if (there != null) for (File f : there) {
            if (!f.getName().matches("\\d{3}\\.png")) continue;
            Png png = read(Files.readAllBytes(f.toPath()));
            if (png != null && png.crc >= 0) found.computeIfAbsent(png.crc, k -> new java.util.ArrayDeque<>()).add(f);
        }
        long[] crcs = new long[sheets];
        for (int s = 0; s < sheets; s++) crcs[s] = pageKey(stackKey(all, stacks, s, tracing), templates[s]);
        // What the PDF said of each page last time, by key, and this time's keys in its order: the days follow a page.
        File state = new File(pngs, NotebookPdf.STATE);
        NotebookPdf.State old = NotebookPdf.State.read(state);
        Map<Long, NotebookPdf.Sheet> known = new HashMap<>();
        if (old != null) for (NotebookPdf.Sheet s : old.sheets) known.putIfAbsent(s.key, s);
        List<Long> keys = new ArrayList<>();
        long[] coverKeys = new long[2];
        PackedPage pictured = covers != null && covers.picture >= 0 && covers.picture < all.size() ? all.get(covers.picture) : null;
        if (covers != null) for (int c = 0; c < 2; c++) {
            PackedPage own = c == 0 ? covers.front : covers.back;
            coverKeys[c] = NotebookCovers.key(c == 0, covers.design, covers.name, covers.width, covers.height, own == null ? 0 : own.crc, c == 0 && pictured != null ? pictured.crc : 0);
        }
        if (covers != null) keys.add(coverKeys[0]);
        for (long crc : crcs) keys.add(crc);
        long today = documents.today.toEpochDay();
        // Kept PNGs first move aside, so renumbering never writes over one still needed.
        Map<Integer, File> aside = new HashMap<>();
        long[] keptDay = new long[crcs.length];
        for (int i = 0; i < crcs.length; i++) {
            java.util.ArrayDeque<File> alike = found.get(crcs[i]);
            File keep = alike == null ? null : alike.poll();
            if (keep == null) continue;
            keptDay[i] = day(keep.lastModified());
            File moved = new File(pngs, "." + i + ".moving");
            move(keep, moved); aside.put(i, moved);
        }
        int made = 0, kept = aside.size();
        // The PDF's pages as files, each read again only as it is written: a notebook's PNGs never all in memory at once.
        List<File> pdf = new ArrayList<>();
        List<NotebookPdf.Sheet> said = new ArrayList<>();
        if (covers != null) {
            for (boolean front : new boolean[]{true, false}) {
                PackedPage own = front ? covers.front : covers.back;
                long key = coverKeys[front ? 0 : 1];
                File target = new File(pngs, front ? FRONT_PNG : BACK_PNG);
                Png png = target.isFile() ? read(Files.readAllBytes(target.toPath())) : null;
                long keptOn = -1;
                if (png == null || png.crc != key) {
                    byte[] bytes;
                    try { synchronized (DocumentCodec.ONE_PAGE) { bytes = png(cover(front, covers, own, front ? pictured : null), key, tiles); } }
                    catch (IOException | RuntimeException e) { target.delete(); continue; }
                    write(target, bytes); made++;
                } else { kept++; keptOn = day(target.lastModified()); }
                long[] coverDays = days(key, 0, keptOn, old, known, keys, today);
                // The back cover's PNG only: in the PDF it would take a new number with every page added.
                if (front) { said.add(new NotebookPdf.Sheet(key, 0, 0, true, "", "", java.util.Collections.emptyList()).days(coverDays[0], coverDays[1])); pdf.add(target); }
            }
        }
        for (int i = 0; i < crcs.length; i++) {
            File target = new File(pngs, pageName(i));
            NotebookPdf.Sheet sheet;
            long[] days = days(crcs[i], pdf.size(), aside.containsKey(i) ? keptDay[i] : -1, old, known, keys, today);
            if (aside.containsKey(i)) {
                move(aside.get(i), target);
                NotebookPdf.Sheet was = known.get(crcs[i]);
                if (was != null) sheet = was;
                // A PNG from before the PDF said its words: the page read once for them.
                else try { synchronized (DocumentCodec.ONE_PAGE) { sheet = said(crcs[i], named[i], layers(all, stacks, i, tracing, null)); } }
                catch (IOException | RuntimeException e) { sheet = new NotebookPdf.Sheet(crcs[i], 0, 0, false, named[i], "", java.util.Collections.emptyList()); }
            } else synchronized (DocumentCodec.ONE_PAGE) {
                ToneDocument.Snapshot page;
                byte[] guides;
                try {
                    List<ToneDocument> layers = layers(all, stacks, i, tracing, templates[i]);
                    sheet = said(crcs[i], named[i], layers);
                    page = flatten(layers, hidden(stacks, i, tracing));
                    guides = templates[i].shared(page.width, page.height);
                }
                catch (IOException | RuntimeException e) { target.delete(); continue; }
                byte[] bytes = png(page, crcs[i], tiles, guides != null);
                write(target, bytes); made++;
            }
            said.add(sheet.days(days[0], days[1]));
            pdf.add(target);
        }
        // PNGs past the last page, or of pages since changed.
        File[] left = pngs.listFiles();
        if (left != null) for (File f : left) {
            String n = f.getName();
            if (n.matches("\\d{3}\\.png") && Integer.parseInt(n.substring(0, 3)) > crcs.length || n.endsWith(".moving")) f.delete();
        }
        File target = NotebookPdf.update(documents.folder, state, name, documents.title, options.template.equals(NotebookTemplate.BLANK) ? "" : options.template.label, said,
                k -> read(Files.readAllBytes(pdf.get(k).toPath())));
        // The PDF the app wrote beside the PNGs before it wrote one in Documents.
        File before = pdf(folder, name);
        if (!before.equals(target) && before.isFile() && !NotebookPdf.mark(before).exists()) before.delete();
        return new Done(made, kept, target);
    }
    /** Stack {@code s}'s pages read, bottom first, the bottom one under {@code template}'s guides (null: none placed). */
    static List<ToneDocument> layers(List<PackedPage> pages, PageStacks stacks, int s, TracingPaper tracing, NotebookTemplate template) throws IOException {
        List<ToneDocument> layers = new ArrayList<>();
        for (int p = stacks.first(s); p <= stacks.last(s); p++) {
            ToneDocument layer = DocumentCodec.read(new ByteArrayInputStream(pages.get(p).bytes()));
            // The guides are the bottom page's.
            if (layers.isEmpty() && template != null) layer.placeGuides(template.shared(layer.width, layer.height));
            layers.add(layer);
        }
        return layers;
    }
    /** Stack {@code s}'s layers' Hidden steps: a Hidden layer of a stack is left out. */
    static boolean[] hidden(PageStacks stacks, int s, TracingPaper tracing) {
        boolean[] hidden = new boolean[stacks.size(s)];
        for (int p = stacks.first(s), k = 0; p <= stacks.last(s); p++, k++) hidden[k] = tracing != null && stacks.size(s) > 1 && tracing.step(p) == TracingPaper.HIDDEN;
        return hidden;
    }
    /** A stack's {@code layers} (read, {@link #layers}) as the one page its PNG shows ({@link PageStacks#flatten}). */
    static ToneDocument.Snapshot flatten(List<ToneDocument> layers, boolean[] hidden) {
        List<ToneDocument.Snapshot> snapshots = new ArrayList<>();
        for (ToneDocument layer : layers) snapshots.add(layer.layerSnapshot());
        return PageStacks.flatten(snapshots, hidden);
    }
    /** What the PDF says of a page beyond its days: its template's name, its card and its magnets' words, from its shown layers. */
    static NotebookPdf.Sheet said(long key, String template, List<ToneDocument> layers) {
        String prompt = null;
        List<Magnets.Magnet> words = new ArrayList<>();
        for (ToneDocument layer : layers) {
            if (prompt == null) prompt = layer.prompt();
            if (layer.magnets() != null) words.addAll(layer.magnets().list());
        }
        return new NotebookPdf.Sheet(key, 0, 0, false, template, prompt, words);
    }
    /**
     * A page's days, {made, changed}: as the PDF last said them when its key is known; a PNG from before that, the day it
     * was drawn ({@code keptOn}; -1: drawn now); drawn now, changed today and made when the page that was at its place
     * ({@code at}) was, if that one is gone (changed where it is), else today.
     */
    private static long[] days(long key, int at, long keptOn, NotebookPdf.State old, Map<Long, NotebookPdf.Sheet> known, List<Long> keys, long today) {
        NotebookPdf.Sheet was = known.get(key);
        if (was != null) return new long[]{was.made, was.changed};
        if (keptOn >= 0) return new long[]{keptOn, keptOn};
        boolean replaced = old != null && at < old.sheets.size() && !keys.contains(old.sheets.get(at).key);
        return new long[]{replaced ? old.sheets.get(at).made : today, today};
    }
    private static long day(long millis) { return java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay(); }
    /** What stack {@code s}'s PNG is of: a page alone, its CRC; a stack, its layers' CRCs and their Hidden steps mixed. */
    static long stackKey(List<PackedPage> pages, PageStacks stacks, int s, TracingPaper tracing) {
        if (stacks.size(s) == 1) return pages.get(stacks.first(s)).crc;
        CRC32 c = new CRC32();
        for (int p = stacks.first(s); p <= stacks.last(s); p++) {
            long crc = pages.get(p).crc;
            c.update(new byte[]{(byte) (crc >> 24), (byte) (crc >> 16), (byte) (crc >> 8), (byte) crc, (byte) (tracing.step(p) == TracingPaper.HIDDEN ? 1 : 0)});
        }
        return c.getValue();
    }
    /** The CRC a page's PNG carries: its page's, mixed with the template's when guides are printed (Blank: the page's own, as before guides). */
    static long pageKey(long crc, NotebookTemplate template) {
        if (template.equals(NotebookTemplate.BLANK)) return crc;
        CRC32 c = new CRC32(); c.update(template.key.getBytes(StandardCharsets.UTF_8));
        return (crc ^ c.getValue()) & 0xffffffffL;
    }
    static File pdf(File folder, String name) { return new File(folder, name + ".pdf"); }
    /** A cover as its PNG shows it: its page (blank for none) on its paper, the front's picture from {@code pictured}'s page. */
    private static ToneDocument.Snapshot cover(boolean front, Covers covers, PackedPage own, PackedPage pictured) throws IOException {
        ToneDocument page = own == null ? new ToneDocument(covers.width, covers.height) : DocumentCodec.read(new ByteArrayInputStream(own.bytes()));
        NotebookCovers.Picture picture = pictured == null ? null : NotebookCovers.of(DocumentCodec.read(new ByteArrayInputStream(pictured.bytes())).layerSnapshot());
        page.placePaper(NotebookCovers.paper(front, covers.design, covers.name, page.width, page.height, picture), () -> NotebookCovers.paper(front, covers.design, covers.name, page.width, page.height, picture, true));
        return page.layerSnapshot();
    }

    /** A notebook renamed: its PNGs' folder and its PDF follow; copies already under the new name win. */
    static void rename(File folder, String from, String to) { rename(folder, folder, from, to, to); }
    /** As above, its PDF in {@code documents} taking {@code title}'s name, its handwriting with it ({@link NotebookPdf#rename}). */
    static void rename(File folder, File documents, String from, String to, String title) {
        if (from.equals(to)) return;
        boolean caseOnly = from.equalsIgnoreCase(to);
        File pngs = new File(folder, from), pdf = pdf(folder, from);
        File newPngs = new File(folder, to), newPdf = pdf(folder, to);
        if (pngs.isDirectory() && (caseOnly || !newPngs.exists()) && !pngs.renameTo(newPngs)) { /* drawn again at the next update */ }
        File state = new File(newPngs, NotebookPdf.STATE);
        if (state.isFile()) NotebookPdf.rename(documents, state, title);
        // A PDF from before the Documents one, beside the PNGs.
        else if (pdf.isFile() && (caseOnly || !newPdf.exists())) pdf.renameTo(newPdf);
    }
    /** A notebook deleted: its PNGs and PDF go. */
    static void delete(File folder, String name) { delete(folder, folder, name); }
    /** As above, its PDF in {@code documents} too, unless it has handwriting (then it stays). */
    static void delete(File folder, File documents, String name) {
        File pngs = new File(folder, name);
        NotebookPdf.delete(documents, new File(pngs, NotebookPdf.STATE));
        File[] files = pngs.listFiles();
        if (files != null) for (File f : files) if (f.getName().endsWith(".png") || f.getName().endsWith(".moving") || f.getName().equals(NotebookPdf.STATE)) f.delete();
        pngs.delete();
        File before = pdf(folder, name);
        if (!NotebookPdf.mark(before).exists()) before.delete();
    }

    static void write(File target, byte[] bytes) throws IOException {
        File temporary = new File(target.getParentFile(), "." + target.getName() + ".tmp");
        Files.write(temporary.toPath(), bytes);
        move(temporary, target);
    }
    static void move(File from, File to) throws IOException {
        Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    private NotebookExport() {}
}
