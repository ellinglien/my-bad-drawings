package io.github.mpdairy.monopaint;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Inflater;

/**
 * A notebook's PDF in Documents ({@link NotebookPdf}): it parses (every object where its table says, read back by a
 * small reader here), a page each the page's size, the front cover first and labelled Cover, then 1, 2, …; an outline
 * of the cover and a day at a time; the document's info; invisible words where they belong (title, page, days,
 * template, card, each magnet's word on its tile); greys exactly as the PNG copy has them, 1, 2 or 4 bits a pixel when a
 * page has 16 greys or fewer; only changed pages drawn again and an unchanged PDF not written; handwriting kept (a
 * marked PDF rewritten only while pages keep their numbers, else a new "T 2.pdf"); never a file the app didn't write
 * replaced; renames and deletes followed; 250 pages in 40 MB of heap.
 */
public final class NotebookPdfChecks {
    static final int W = 300, H = 400;
    static final LocalDate DAY1 = LocalDate.of(2026, 10, 23), DAY2 = LocalDate.of(2026, 10, 24);

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("big")) { big(); return; }
        greys();
        File dir = Files.createTempDirectory("pdfchecks").toFile();
        try {
            book(dir);
            handwriting(dir);
            notOurs(dir);
            renameDelete(dir);
            bounded();
        } finally { NotebookMigrationChecks.delete(dir); }
        System.out.println("PASS: notebook PDF: it parses, every object where its table says; a page each at the page's size, the cover first labelled Cover then 1, 2, 3; "
                + "an outline of the cover then a day at a time, its pages under it; Title, Author, Subject, Keywords and days in its info; invisible Courier words: title and page, "
                + "made and changed days, template, the card's words, each magnet's word at its tile's baseline; greys as the PNG copy has them at 1, 2 and 4 bits under 17 greys, 8 above; "
                + "only a changed page drawn again, an unchanged PDF not written again; with handwriting beside it, pages changed in place or added rewrite it, a page gone makes \"T 2.pdf\" and leaves it; "
                + "another's file and a lone mark are passed over; a rename moves the PDF and its handwriting, a delete keeps a PDF with handwriting; the old PDF beside the PNGs goes; 250 pages in 40 MB");
    }

    // Pages

    /** A page whose ink holds {@code tones} at random, on {@code template}'s guides. */
    static ToneDocument page(int seed, int[] tones) {
        ToneDocument page = InkWashLayers.blank(W, H);
        page.selectLayer(InkWashLayers.INK); page.begin();
        Random r = new Random(seed);
        for (int i = 0; i < 3000; i++) page.setTone(r.nextInt(W), r.nextInt(H), tones[r.nextInt(tones.length)]);
        page.finish();
        return page;
    }
    static PackedPage packed(ToneDocument page) throws IOException { return PackedPage.of(DrawingBook.encode(page)); }
    static final int[] PEN = {0, 128};

    /** Greys through the PDF as the PNG copy has them: 1 bit for two, 4 for sixteen, 8 for more. */
    static void greys() throws Exception {
        int[] noisy = new int[40];
        for (int i = 0; i < noisy.length; i++) noisy[i] = i * 6;
        int[][] kinds = {{0}, {0, 128, 200}, GrayPalette.VALUES, noisy};
        int[] bits = {1, 2, 4, 8};
        for (int k = 0; k < kinds.length; k++) {
            ToneDocument page = page(k, kinds[k]);
            NotebookExport.Png png = NotebookExport.read(NotebookExport.png(page.layerSnapshot(), 7));
            check(png.bits == bits[k], "Page " + k + " at " + bits[k] + " bits: " + png.bits);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            NotebookPdf.write(out, "Greys", "Greys", "", Collections.singletonList(new NotebookPdf.Sheet(7, 1, 1, false, "", "", Collections.emptyList())), i -> png);
            Pdf pdf = new Pdf(out.toByteArray());
            int[] shown = pdf.image(pdf.pages().get(0));
            for (int y = 0; y < H; y++) for (int x = 0; x < W; x++)
                check(shown[y * W + x] == DotPattern.exportGray(page.compositeTone(x, y)), "Greys at " + bits[k] + " bits as the PNG copy's at " + x + "," + y);
            check(javax.imageio.ImageIO.read(new ByteArrayInputStream(NotebookExport.png(page.layerSnapshot(), 7))).getRGB(5, 5) != 0, "The PNG reads in javax.imageio");
        }
    }

    /** The whole book: pages, labels, outline, info, words; only changes drawn; days follow pages. */
    static void book(File dir) throws Exception {
        File export = new File(dir, "EXPORT"), docs = new File(dir, "Document");
        String name = "2026-10-23_Tome of Checks";
        List<PackedPage> pages = new ArrayList<>();
        ToneDocument first = page(1, PEN);
        first.placePrompt("1 min | Eyes closed: a giraffe.");
        first.placeMagnets(Magnets.of(Arrays.asList(new Magnets.Magnet("moon", 120, 200, 0, 0), new Magnets.Magnet("tide", 150, 300, 25, 1))));
        pages.add(packed(first));
        pages.add(packed(page(2, PEN)));
        // An old copy beside the PNGs, from before Documents.
        export.mkdirs();
        Files.write(NotebookExport.pdf(export, name).toPath(), "%PDF-1.4 old".getBytes(StandardCharsets.ISO_8859_1));
        NotebookOptions lined = NotebookOptions.DEFAULT.withTemplate(NotebookTemplate.LINED);
        NotebookExport.Done done = update(export, docs, name, "Tome of Checks", pages, lined, DAY1);
        check(done.pdf.equals(new File(docs, "Tome of Checks.pdf")) && done.drawn == 4, "The PDF in Documents named for the title: " + done.pdf + ", " + done.drawn);
        check(!NotebookExport.pdf(export, name).exists(), "The old PDF beside the PNGs is gone");
        Pdf pdf = new Pdf(Files.readAllBytes(done.pdf.toPath()));
        List<Integer> kids = pdf.pages();
        check(kids.size() == 3, "The front cover and two pages, no back cover: " + kids.size());
        check(pdf.dict(kids.get(1)).contains("/MediaBox [0 0 72.00 96.00]"), "A page at its size at 300 ppi");
        check(pdf.dict(1).contains("/PageLabels << /Nums [0 << /P (Cover) >> 1 << /S /D >>] >>"), "Labelled Cover, 1, 2");
        check(pdf.outline().equals(Arrays.asList("Cover", "Oct 23, 2026", "  Page 1: Eyes closed: a giraffe.", "  Page 2")), "The outline: " + pdf.outline());
        String info = pdf.dict(5);
        check(info.contains("/Title " + NotebookPdf.hex("Tome of Checks")) && info.contains("/Author " + NotebookPdf.hex(AppName.NAME)) && info.contains("/Subject " + NotebookPdf.hex("Lined"))
                && info.contains("/Keywords " + NotebookPdf.hex(AppName.NAME + ", notebook, Lined")) && info.contains("/CreationDate (D:20261023120000)") && info.contains("/ModDate (D:20261023120000)"), "Its info: " + info);
        Map<String, double[]> words = pdf.words(kids.get(1));
        check(words.containsKey("Tome of Checks, page 1") && words.containsKey("Oct 23, 2026") && words.containsKey("Lined") && words.containsKey("Eyes closed: a giraffe."), "The page's words: " + words.keySet());
        check(pdf.content(kids.get(1)).startsWith("q 72.00 0 0 96.00 0 0 cm /Page Do Q\nBT 3 Tr\n"), "Under the picture, invisible");
        // "moon" untilted: its baseline 12 px under the tile's centre, starting half its 4 × 0.6 em to the left.
        double[] moon = words.get("moon");
        check(moon != null && near(moon[4], (120 - 4 * .6 * Magnets.EM / 2) * .24) && near(moon[5], 96 - (200 + 12) * .24) && near(moon[0], 1) && near(moon[1], 0), "moon where its tile is: " + Arrays.toString(moon));
        // "tide" turned a quarter and 2.5° more: its baseline runs down the page.
        double[] tide = words.get("tide");
        double a = Math.toRadians(92.5);
        check(tide != null && near(tide[0], Math.cos(a)) && near(tide[1], -Math.sin(a)), "tide turned with its tile: " + Arrays.toString(tide));
        check(pdf.words(kids.get(0)).containsKey("Tome of Checks"), "The cover says the title");
        // Nothing changed: nothing drawn, the PDF not written again.
        done.pdf.setLastModified(1000_000L);
        done = update(export, docs, name, "Tome of Checks", pages, lined, DAY2);
        check(done.drawn == 0 && done.pdf.lastModified() == 1000_000L, "Nothing changed, nothing drawn or written");
        // Page 2 changed on Oct 24 and a third page: one drawn each, page 2 made Oct 23 changed Oct 24, page 3 under Oct 24.
        pages.set(1, packed(page(22, PEN)));
        pages.add(packed(page(3, PEN)));
        File p1 = new File(export, name + "/001.png");
        long p1Time = p1.lastModified();
        done = update(export, docs, name, "Tome of Checks", pages, lined, DAY2);
        check(done.drawn == 2 && p1.lastModified() == p1Time, "Only the changed page and the new one drawn: " + done.drawn);
        pdf = new Pdf(Files.readAllBytes(done.pdf.toPath()));
        check(pdf.outline().equals(Arrays.asList("Cover", "Oct 23, 2026", "  Page 1: Eyes closed: a giraffe.", "  Page 2", "Oct 24, 2026", "  Page 3")), "Days follow pages: " + pdf.outline());
        check(pdf.words(pdf.pages().get(2)).containsKey("Oct 23, 2026, changed Oct 24, 2026"), "Page 2 made Oct 23, changed Oct 24");
        // Its PNGs already there (a copy from before the PDF said its words): read again for them, not drawn.
        new File(export, name + "/" + NotebookPdf.STATE).delete();
        done = update(export, docs, name, "Tome of Checks", pages, lined, DAY2);
        check(done.drawn == 0 && new Pdf(Files.readAllBytes(done.pdf.toPath())).words(pdf.pages().get(1)).containsKey("moon"), "Words read from kept pages' notebook pages");
    }

    /** Handwriting beside the PDF: rewritten while pages keep their numbers; a page gone, a new file, the marked one left as it is. */
    static void handwriting(File dir) throws Exception {
        File export = new File(dir, "EXPORT2"), docs = new File(dir, "Document2");
        List<PackedPage> pages = new ArrayList<>();
        for (int i = 0; i < 3; i++) pages.add(packed(page(30 + i, PEN)));
        File pdf = update(export, docs, "T", "T", pages, NotebookOptions.DEFAULT, DAY1).pdf;
        File mark = NotebookPdf.mark(pdf);
        Files.write(mark.toPath(), new byte[]{1, 2, 3});
        pages.set(1, packed(page(41, PEN)));
        check(update(export, docs, "T", "T", pages, NotebookOptions.DEFAULT, DAY1).pdf.equals(pdf), "A page changed where it is: the marked PDF rewritten");
        pages.add(packed(page(42, PEN)));
        check(update(export, docs, "T", "T", pages, NotebookOptions.DEFAULT, DAY1).pdf.equals(pdf), "A page added at the end: rewritten");
        byte[] marked = Files.readAllBytes(pdf.toPath());
        pages.remove(0);
        File next = update(export, docs, "T", "T", pages, NotebookOptions.DEFAULT, DAY1).pdf;
        check(next.getName().equals("T 2.pdf") && Arrays.equals(marked, Files.readAllBytes(pdf.toPath())) && mark.isFile(), "A page gone: T 2.pdf, the marked one left as it was");
        check(NotebookPdf.keepsNumbers(Arrays.asList(1L, 2L, 7L, 7L, 5L), Arrays.asList(1L, 2L, 9L, 7L, 5L, 6L)) && !NotebookPdf.keepsNumbers(Arrays.asList(1L, 2L, 3L), Arrays.asList(1L, 3L, 2L))
                && !NotebookPdf.keepsNumbers(Arrays.asList(1L, 2L, 3L), Arrays.asList(1L, 9L, 2L, 3L)), "Drawing on one of two blank pages keeps numbers; a swap or an insert doesn't");
        // No handwriting: a page gone rewrites it in place.
        pages.add(0, packed(page(50, PEN)));
        File again = update(export, docs, "T", "T", pages, NotebookOptions.DEFAULT, DAY1).pdf;
        pages.remove(0);
        check(update(export, docs, "T", "T", pages, NotebookOptions.DEFAULT, DAY1).pdf.equals(again), "Without handwriting, rewritten in place");
    }

    /** A file the app didn't write, or a mark alone, is passed over. */
    static void notOurs(File dir) throws Exception {
        File export = new File(dir, "EXPORT3"), docs = new File(dir, "Document3");
        docs.mkdirs();
        byte[] theirs = "someone's".getBytes(StandardCharsets.ISO_8859_1);
        Files.write(new File(docs, "Harbour.pdf").toPath(), theirs);
        Files.write(new File(docs, "Quay.pdf.mark").toPath(), theirs);
        List<PackedPage> pages = Collections.singletonList(packed(page(60, PEN)));
        check(update(export, docs, "Harbour", "Harbour", pages, NotebookOptions.DEFAULT, DAY1).pdf.getName().equals("Harbour 2.pdf")
                && Arrays.equals(theirs, Files.readAllBytes(new File(docs, "Harbour.pdf").toPath())), "Another's Harbour.pdf is left: Harbour 2.pdf");
        check(update(export, docs, "Quay", "Quay", pages, NotebookOptions.DEFAULT, DAY1).pdf.getName().equals("Quay 2.pdf"), "A mark alone keeps its name");
        // The app's own PDF changed by something else (another length): not ours any more.
        File own = new File(docs, "Harbour 2.pdf");
        Files.write(own.toPath(), theirs);
        check(update(export, docs, "Harbour", "Harbour", pages, NotebookOptions.DEFAULT, DAY1).pdf.getName().equals("Harbour 3.pdf") && Arrays.equals(theirs, Files.readAllBytes(own.toPath())), "Changed by another: left");
    }

    static void renameDelete(File dir) throws Exception {
        File export = new File(dir, "EXPORT4"), docs = new File(dir, "Document4");
        List<PackedPage> pages = Collections.singletonList(packed(page(70, PEN)));
        update(export, docs, "2026-10-23_Old", "Old", pages, NotebookOptions.DEFAULT, DAY1);
        Files.write(new File(docs, "Old.pdf.mark").toPath(), new byte[]{9});
        NotebookExport.rename(export, docs, "2026-10-23_Old", "2026-10-23_New", "New");
        check(new File(docs, "New.pdf").isFile() && new File(docs, "New.pdf.mark").isFile() && !new File(docs, "Old.pdf").exists() && !new File(docs, "Old.pdf.mark").exists(), "Renamed: the PDF and its handwriting follow");
        File pdf = update(export, docs, "2026-10-23_New", "New", pages, NotebookOptions.DEFAULT, DAY1).pdf;
        check(pdf.getName().equals("New.pdf") && new Pdf(Files.readAllBytes(pdf.toPath())).dict(5).contains(NotebookPdf.hex("New")), "Its title follows at the next copy, in place");
        NotebookExport.delete(export, docs, "2026-10-23_New");
        check(pdf.isFile() && !new File(export, "2026-10-23_New").exists(), "Deleted: its PNGs go, the PDF with handwriting stays");
        update(export, docs, "2026-10-23_Gone", "Gone", pages, NotebookOptions.DEFAULT, DAY1);
        NotebookExport.delete(export, docs, "2026-10-23_Gone");
        check(!new File(docs, "Gone.pdf").exists(), "Deleted without handwriting: the PDF goes");
    }

    /** 250 pages in a JVM of 40 MB: every page's PNG is read one at a time. */
    static void bounded() throws Exception {
        Process p = new ProcessBuilder(new File(System.getProperty("java.home"), "bin/java").getPath(), "-Xmx40m", "-cp", System.getProperty("java.class.path"),
                NotebookPdfChecks.class.getName(), "big").redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        check(p.waitFor() == 0 && out.contains("BIG OK"), "250 pages in 40 MB: " + out);
    }
    static void big() throws Exception {
        File dir = Files.createTempDirectory("pdfbig").toFile();
        try {
            int w = 500, h = 700;
            // Ten noisy pages, each over and over: 250 PNGs of 8-bit noise, more than the heap holds.
            List<PackedPage> distinct = new ArrayList<>(), pages = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                ToneDocument page = InkWashLayers.blank(w, h);
                page.selectLayer(InkWashLayers.INK); page.begin();
                Random r = new Random(i);
                for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) page.setTone(x, y, r.nextInt(256));
                page.finish();
                distinct.add(packed(page));
            }
            for (int i = 0; i < 250; i++) pages.add(distinct.get(i % 10));
            File export = new File(dir, "EXPORT"), docs = new File(dir, "Document");
            NotebookExport.Done done = update(export, docs, "Big", "Big", pages, NotebookOptions.DEFAULT, DAY1);
            long size = 0;
            for (File f : new File(export, "Big").listFiles()) size += f.length();
            if (size < 60L << 20) throw new AssertionError("The PNGs together outweigh the heap: " + size);
            pages.set(100, distinct.get(3) == pages.get(100) ? distinct.get(4) : distinct.get(3));
            NotebookExport.Done again = update(export, docs, "Big", "Big", pages, NotebookOptions.DEFAULT, DAY1);
            if (done.drawn != 252 || again.drawn != 1) throw new AssertionError("drawn " + done.drawn + ", then " + again.drawn);
            // The PDF itself outweighs the heap: its start and end read alone.
            try (RandomAccessFile f = new RandomAccessFile(again.pdf, "r")) {
                byte[] head = new byte[8192], tail = new byte[64];
                f.readFully(head); f.seek(f.length() - tail.length); f.readFully(tail);
                if (f.length() < 60L << 20 || !new String(head, StandardCharsets.ISO_8859_1).contains("/Type /Pages /Count 251 ") || !new String(tail, StandardCharsets.ISO_8859_1).endsWith("%%EOF\n"))
                    throw new AssertionError("The cover and 250 pages: " + f.length());
            }
            System.out.println("BIG OK");
        } finally { NotebookMigrationChecks.delete(dir); }
    }

    static NotebookExport.Done update(File export, File docs, String name, String title, List<PackedPage> pages, NotebookOptions options, LocalDate today) throws IOException {
        return NotebookExport.update(export, name, pages, null, new NotebookExport.Covers(name, CoverDesign.DEFAULT, W, H, null, null, -1), options, null, null, new NotebookExport.Documents(docs, title, today));
    }

    // A small PDF reader: the table, objects, pages, outline, content and images.

    static final class Pdf {
        final byte[] bytes; final String text; final Map<Integer, Integer> at = new HashMap<>();
        Pdf(byte[] bytes) {
            this.bytes = bytes; text = new String(bytes, StandardCharsets.ISO_8859_1);
            check(text.startsWith("%PDF-1.4\n") && text.endsWith("%%EOF\n"), "A PDF");
            int xref = Integer.parseInt(text.substring(text.lastIndexOf("startxref\n") + 10, text.lastIndexOf("\n%%EOF")).trim());
            check(text.startsWith("xref\n0 ", xref), "startxref points at the table");
            String[] rows = text.substring(xref).split("\n");
            int count = Integer.parseInt(rows[1].split(" ")[1]);
            for (int n = 1; n < count; n++) {
                int offset = Integer.parseInt(rows[2 + n].substring(0, 10));
                check(text.startsWith(n + " 0 obj\n", offset), "Object " + n + " is where the table says");
                at.put(n, offset);
            }
            String trailer = text.substring(text.indexOf("trailer", xref));
            check(trailer.contains("/Size " + count + " ") && trailer.contains("/Root 1 0 R") && trailer.contains("/Info 5 0 R"), "The trailer");
        }
        String dict(int n) { int o = at.get(n), s = text.indexOf("stream\n", o), e = text.indexOf("endobj", o); return text.substring(o, s >= 0 && s < e ? s : e); }
        int ref(String dict, String key) { Matcher m = Pattern.compile(Pattern.quote(key) + " (\\d+) 0 R").matcher(dict); check(m.find(), key + " in " + dict); return Integer.parseInt(m.group(1)); }
        byte[] stream(int n) {
            String d = dict(n);
            Matcher m = Pattern.compile("/Length (\\d+) >>\\s*$").matcher(d);
            check(m.find(), "A stream's length");
            int start = at.get(n) + d.length() + 7, length = Integer.parseInt(m.group(1));
            check(text.startsWith("\nendstream", start + length) || text.startsWith("endstream", start + length), "Its length ends at endstream");
            return Arrays.copyOfRange(bytes, start, start + length);
        }
        List<Integer> pages() {
            String kids = dict(ref(dict(1), "/Pages"));
            List<Integer> list = new ArrayList<>();
            Matcher m = Pattern.compile("(\\d+) 0 R").matcher(kids.substring(kids.indexOf("/Kids")));
            while (m.find()) list.add(Integer.parseInt(m.group(1)));
            check(kids.contains("/Count " + list.size() + " "), "Pages counted");
            return list;
        }
        String content(int page) { return new String(stream(ref(dict(page), "/Contents")), StandardCharsets.ISO_8859_1); }
        /** Each word's Tm {a, b, c, d, x, y}. */
        Map<String, double[]> words(int page) {
            Map<String, double[]> words = new LinkedHashMap<>();
            Matcher m = Pattern.compile("(\\S+) (\\S+) (\\S+) (\\S+) (\\S+) (\\S+) Tm \\(((?:[^()\\\\]|\\\\.)*)\\) Tj").matcher(content(page));
            while (m.find()) {
                double[] tm = new double[6];
                for (int k = 0; k < 6; k++) tm[k] = Double.parseDouble(m.group(k + 1));
                words.put(m.group(7).replace("\\(", "(").replace("\\)", ")"), tm);
            }
            return words;
        }
        List<String> outline() {
            List<String> titles = new ArrayList<>();
            String root = dict(ref(dict(1), "/Outlines"));
            if (root.contains("/First")) walk(ref(root, "/First"), "", titles);
            return titles;
        }
        private void walk(int item, String indent, List<String> titles) {
            for (Integer n = item; n != null; ) {
                String d = dict(n);
                Matcher t = Pattern.compile("/Title <FEFF([0-9A-F]*)>").matcher(d);
                check(t.find(), "An outline title");
                StringBuilder s = new StringBuilder();
                for (int k = 0; k < t.group(1).length(); k += 4) s.append((char) Integer.parseInt(t.group(1).substring(k, k + 4), 16));
                titles.add(indent + s);
                check(pages().contains(Integer.valueOf(d.replaceAll("(?s).*/Dest \\[(\\d+) 0 R /Fit\\].*", "$1"))), "Its destination is a page");
                if (d.contains("/First")) walk(ref(d, "/First"), indent + "  ", titles);
                n = d.contains("/Next") ? ref(d, "/Next") : null;
            }
        }
        /** The page's picture's greys, decoded as a reader would: inflate, PNG predictors, bits, palette. */
        int[] image(int page) throws Exception {
            int n = ref(dict(page), "/Page");
            String d = dict(n);
            int w = number(d, "/Width"), h = number(d, "/Height"), bits = number(d, "/BitsPerComponent");
            int[] palette = null;
            Matcher p = Pattern.compile("\\[/Indexed /DeviceGray (\\d+) <([0-9a-f]*)>\\]").matcher(d);
            if (p.find()) {
                palette = new int[Integer.parseInt(p.group(1)) + 1];
                for (int k = 0; k < palette.length; k++) palette[k] = Integer.parseInt(p.group(2).substring(2 * k, 2 * k + 2), 16);
            } else check(d.contains("/ColorSpace /DeviceGray") && bits == 8, "8-bit grey");
            int stride = (w * bits + 7) / 8;
            byte[] raw = new byte[(stride + 1) * h];
            Inflater inflater = new Inflater(); inflater.setInput(stream(n));
            int got = inflater.inflate(raw); inflater.end();
            check(got == raw.length, "Its image inflates to its rows");
            int[] greys = new int[w * h];
            byte[] previous = new byte[stride], row = new byte[stride];
            for (int y = 0; y < h; y++) {
                int type = raw[y * (stride + 1)];
                for (int x = 0; x < stride; x++) {
                    int v = raw[y * (stride + 1) + 1 + x] & 255, left = x > 0 ? row[x - 1] & 255 : 0, up = previous[x] & 255, corner = x > 0 ? previous[x - 1] & 255 : 0;
                    if (type == 1) v += left; else if (type == 2) v += up; else if (type == 3) v += (left + up) / 2;
                    else if (type == 4) { int e = left + up - corner, a = Math.abs(e - left), b = Math.abs(e - up), c = Math.abs(e - corner); v += a <= b && a <= c ? left : b <= c ? up : corner; }
                    row[x] = (byte) v;
                }
                for (int x = 0; x < w; x++) {
                    int per = 8 / bits, value = (row[x / per] & 255) >> (8 - bits - x % per * bits) & ((1 << bits) - 1);
                    greys[y * w + x] = palette == null ? value : palette[value];
                }
                byte[] t = previous; previous = row; row = t;
            }
            return greys;
        }
        private static int number(String d, String key) { Matcher m = Pattern.compile(Pattern.quote(key) + " (\\d+)").matcher(d); check(m.find(), key); return Integer.parseInt(m.group(1)); }
    }

    static boolean near(double a, double b) { return Math.abs(a - b) < .02; }
    static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
