package io.github.mpdairy.monopaint;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.Inflater;

/**
 * A notebook's options (docs/archive/superpowers/specs/2026-10-19-notebook-options-design.md; {@link NotebookOptions},
 * {@link NotebookTemplate}, {@link SurpriseTools}, {@link BookOptions}): the templates' guides thin, faint and in the
 * panel's greys at every size, a hairline panel or a rule two pixels at the Manta's width; the options kept as tagged
 * records, only what isn't the default, round-tripped through the file, the shelf's reading and the recovery, an
 * all-default notebook byte for byte as before; the guides paper, not marks (the eraser never takes them, a page with only
 * guides is blank, covers have none, a page's file and picture hold none); the copies with and without guides, a PNG
 * drawn again only when the template or Guides in PDF changes it; Surprise Me balanced over many seeds, its re-rolls too;
 * a notebook's own toolbar loaded and the app's put back. {@code write <file>} writes a notebook with options for the
 * version before them ({@code OlderOptionsCheck}, 233ba6b); {@code older <file>} opens what it wrote.
 */
public final class NotebookOptionsChecks {
    static final int W = 300, H = 400;

    public static void main(String[] args) throws Exception {
        Assets.install(Assets.folder(Assets.FOLDER));
        if (args.length == 2 && args[0].equals("write")) { write(new File(args[1])); return; }
        if (args.length == 2 && args[0].equals("older")) { older(new File(args[1])); return; }
        templates();
        records();
        guidesShown();
        guidesUnderPaint();
        guidesAreNotMarks();
        copies();
        surprise();
        toolbar();
        System.out.println("PASS: Opaque paint covers the guides on the page and in the copies, at any grey, while a wash lets them show by its coverage; Template's step on the screen (Full, Half, Faint, Hidden) lightens the guides as ghosts are, a GSHN record only when not Full, kept by the file and the recovery, never in the copies; Blank, Diary and Comic Panels as thin guides in the panel's greys (a hairline panel, a rule two pixels at 1920 wide and one on smaller pages), shared by every page; the options kept as tagged records only when they aren't the default, read back from the file, the shelf and the recovery, the pages byte for byte and an all-default notebook as before; the guides paper and not marks (the eraser never takes them, a page with only guides is blank and saves, pictures and counts as one, covers have none); the copies with and without guides, a page's PNG drawn again only when the template or Guides in PDF changes it; Surprise Me balanced over many seeds and libraries, each re-roll keeping its place; a notebook's own toolbar loaded, kept as brush codes (their sizes and greys written, not drawn at), and the app's brushes put back");
    }

    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    // ---- templates ----

    static void templates() {
        check(NotebookTemplate.BLANK.guides(W, H) == null && NotebookTemplate.BLANK.shared(W, H) == null, "Blank has no guides");
        check(NotebookTemplate.of("nope") == NotebookTemplate.BLANK && NotebookTemplate.of(null) == NotebookTemplate.BLANK, "A template this version doesn't know is Blank");
        for (NotebookTemplate t : NotebookTemplate.values()) check(NotebookTemplate.of(t.key) == t, t.label + " is found by its key");
        check(NotebookTemplate.DIARY.label.equals("Diary") && NotebookTemplate.COMICS.label.equals("Comics"), "Labels in Title Case");
        int[][] sizes = {{1920, 2560}, {1404, 1872}, {300, 400}, {96, 128}};
        for (NotebookTemplate t : new NotebookTemplate[]{NotebookTemplate.DIARY, NotebookTemplate.COMICS}) {
            for (int[] s : sizes) {
                byte[] g = t.guides(s[0], s[1]);
                check(g.length == s[0] * s[1], t.label + " is a tone a pixel");
                int lines = 0;
                for (byte b : g) {
                    int v = b & 255;
                    check(v == NotebookTemplate.NONE || v == NotebookTemplate.RULE || v == NotebookTemplate.PANEL, "Only the panel's greys: " + v);
                    if (v != NotebookTemplate.NONE) lines++;
                }
                double share = lines / (double) g.length;
                check(share > .001 && share < (s[0] < 200 ? .15 : .08), t.label + " at " + s[0] + " is thin: " + share);
                check(Arrays.equals(g, t.guides(s[0], s[1])), "and the same twice");
            }
            check(t.shared(W, H) == t.shared(W, H), "Every page of a size shares one array");
            check(t.shared(W, H) != t.shared(W + 2, H), "A new size is made anew");
        }
        check(NotebookTemplate.RULE > NotebookTemplate.PANEL && NotebookTemplate.RULE < 255, "Rules are fainter than the panels' hairlines, both mid greys");
        // Line widths: two pixels at the Manta's width, one on smaller pages.
        byte[] diary = NotebookTemplate.DIARY.guides(1920, 2560);
        check(run(diary, 1920, 2560, 1000, true, NotebookTemplate.PANEL) == 2 && run(diary, 1920, 2560, 1000, true, NotebookTemplate.RULE) == 2, "A hairline panel and the rules are 2 px at 1920");
        byte[] small = NotebookTemplate.DIARY.guides(1404, 1872);
        check(run(small, 1404, 1872, 700, true, NotebookTemplate.RULE) == 1, "and 1 px at the Nomad's width");
        // Diary: a big panel above (frame), then rules every 4.5% of the width.
        int panels = runs(diary, 1920, 2560, 1000, NotebookTemplate.PANEL), rules = runs(diary, 1920, 2560, 1000, NotebookTemplate.RULE);
        check(panels == 2 && rules >= 12, "Diary: a panel's top and foot, then " + rules + " rules");
        int topOfRules = 0;
        for (int y = 0; y < 2560 && topOfRules == 0; y++) if ((diary[y * 1920 + 1000] & 255) == NotebookTemplate.RULE) topOfRules = y;
        check(topOfRules > 2560 * .5, "the rules lie below the panel, from past the middle");
        // Comic Panels: two columns of three.
        byte[] comics = NotebookTemplate.COMICS.guides(1920, 2560);
        check(runs(comics, 1920, 2560, 480, NotebookTemplate.PANEL) == 6, "Comic Panels: three panels down the left column");
        int across = 0;
        for (int x = 0, was = 255; x < 1920; x++) { int v = comics[600 * 1920 + x] & 255; if (v == NotebookTemplate.PANEL && was != v) across++; was = v; }
        check(across == 4, "and two across (a side each)");
        // A shaded paper darkens the guides' ground, never lightens it.
        byte[] g = NotebookTemplate.DIARY.guides(W, H);
        byte[] white = ToneDocument.guided(g, 255), grey = ToneDocument.guided(g, 100);
        for (int i = 0; i < g.length; i++) {
            check((white[i] & 255) == (g[i] & 255), "Guides on white paper are the guides");
            check((grey[i] & 255) == Math.min(g[i] & 255, 100), "On a shaded paper, the darker of guide and shade");
        }
        check(ToneDocument.guided(null, 255) == null, "No guides, no pixels");
    }
    /** The length of the run of {@code tone} through ({@code at}, y) walking down column {@code at}, the first found; {@code vertical}. */
    static int run(byte[] g, int w, int h, int at, boolean vertical, int tone) {
        for (int y = 0; y < h; y++) {
            if ((g[y * w + at] & 255) != tone) continue;
            int n = 0;
            while (y + n < h && (g[(y + n) * w + at] & 255) == tone) n++;
            return n;
        }
        return 0;
    }
    /** How many runs of {@code tone} there are down column {@code at}. */
    static int runs(byte[] g, int w, int h, int at, int tone) {
        int n = 0, was = -1;
        for (int y = 0; y < h; y++) { int v = g[y * w + at] & 255; if (v == tone && was != tone) n++; was = v; }
        return n;
    }

    // ---- records ----

    static ToneDocument marked(int seed) {
        ToneDocument page = new ToneDocument(W, H);
        page.begin();
        Random r = new Random(seed);
        for (int i = 0; i < 200; i++) page.setTone(r.nextInt(W), r.nextInt(H), r.nextInt(128));
        page.finish();
        return page;
    }
    static DrawingBook book() throws IOException {
        DrawingBook book = new DrawingBook(marked(1));
        book.addPage(); BlankPagesChecks.mark(book.current());
        book.select(0);
        return book;
    }
    static byte[] bytes(DrawingBook book) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DrawingBook.Snapshot snapshot = book.snapshot();
        BookCodec.write(out, snapshot);
        return out.toByteArray();
    }
    static List<String> toolbarLines(long seed) {
        ToolSlotsChecks.prefs = new HashMap<>();
        ToolSlots slots = new ToolSlots(ToolSlotsChecks.store(), new Random(seed));
        List<SurpriseTools.Pick> picks = SurpriseTools.roll(Collections.<Brush>emptyList(), slots.swatches, new Random(seed));
        return SurpriseTools.toolbar(picks).lines();
    }

    static void records() throws Exception {
        check(NotebookOptions.SLOTS == ToolSlots.COUNT, "A notebook's toolbar has the app's slots");
        check(NotebookOptions.DEFAULT.isDefault() && NotebookOptions.DEFAULT.template == NotebookTemplate.BLANK
                && !NotebookOptions.DEFAULT.ownsTools(), "The default: no template, My Toolbar");
        check(NotebookOptions.Tools.MINE.label.equals("My Toolbar") && NotebookOptions.Tools.SURPRISE.label.equals("Surprise Me") && NotebookOptions.Tools.LIBRARY.label.equals("Library"), "Labels in Title Case");
        File dir = Files.createTempDirectory("options").toFile();
        try {
            DrawingBook book = book();
            byte[] before = bytes(book);
            List<String> lines = toolbarLines(5);
            NotebookOptions full = NotebookOptions.DEFAULT.withTemplate(NotebookTemplate.DIARY).withTools(NotebookOptions.Tools.SURPRISE, lines).withNibPreview(false).withSelfPortrait(true);
            check(full.ownsTools() && !full.isDefault() && full.toolbar.equals(lines), "Options with a template and a toolbar of its own");
            book.setOptions(full);
            check(book.unsaved(), "Options are a change to save");
            byte[] after = bytes(book);
            check(!Arrays.equals(before, after), "and change the file");
            // Each option alone makes its own record, none for the default.
            NotebookOptions[] alone = {NotebookOptions.DEFAULT.withTemplate(NotebookTemplate.COMICS), NotebookOptions.DEFAULT.withTools(NotebookOptions.Tools.LIBRARY, lines), NotebookOptions.DEFAULT.withNibPreview(false), NotebookOptions.DEFAULT.withSelfPortrait(true)};
            int[] tags = {BookOptions.TEMPLATE, BookOptions.TOOLS, BookOptions.NIB_PREVIEW, BookOptions.SELF_PORTRAIT};
            for (int i = 0; i < alone.length; i++) {
                BookOptions.Covers covers = new BookOptions.Covers(); covers.options = alone[i];
                byte[] block = BookOptions.bytes(covers);
                BookOptions.Found found = BookOptions.find(BookOptions.of(block), 0, block.length);
                check(found != null && found.records.size() == 1 && found.records.get(0).tag == tags[i], "Option " + i + " alone is one record");
                check(BookOptions.options(found, BookOptions.of(block)).equals(alone[i]), "and reads back");
                check(BookOptions.length(covers) == block.length, "and its length is as written");
            }
            check(BookOptions.bytes(new BookOptions.Covers()).length == 0, "The defaults write no block");
            // The file, whole and from disk, the shelf, the recovery.
            DrawingBook whole = BookCodec.read(new ByteArrayInputStream(after));
            File file = new File(dir, "nb.mpaint"); Files.write(file.toPath(), after);
            DrawingBook opened = BookCodec.open(file);
            for (DrawingBook b : new DrawingBook[]{whole, opened}) {
                check(b.options().equals(full) && !b.unsaved(), "The options read back from the file, not a change");
                check(b.current().guides() != null && b.count() == 2, "and the pages have the guides");
            }
            check(BookCodec.openPages(file).covers.options.equals(full), "and are what the file's pages record");
            BookPictures.Cover shelf = BookPictures.readCover(file);
            check(shelf.pages == 2 && shelf.design == CoverDesign.DEFAULT, "The shelf reads the notebook as before");
            ByteArrayOutputStream recovery = new ByteArrayOutputStream();
            RecoveryCodec.write(recovery, book.snapshot(), "");
            check(RecoveryCodec.read(new ByteArrayInputStream(recovery.toByteArray())).book.options().equals(full), "The recovery keeps the options");
            ByteArrayOutputStream plain = new ByteArrayOutputStream();
            DrawingBook fresh = book(); RecoveryCodec.write(plain, fresh.snapshot(), "");
            check(RecoveryCodec.read(new ByteArrayInputStream(plain.toByteArray())).book.options().isDefault(), "and none for the default");
            // The pages are as they were: the same bytes, the same pictures.
            List<PackedPage> a = BookCodec.openPages(file).pages;
            DrawingBook plainBook = BookCodec.read(new ByteArrayInputStream(before));
            File base = new File(dir, "base.mpaint"); Files.write(base.toPath(), before);
            List<PackedPage> b = BookCodec.openPages(base).pages;
            check(a.size() == b.size() && a.get(0).crc == b.get(0).crc && a.get(1).crc == b.get(1).crc, "The pages are byte for byte as without options");
            check(plainBook.options().isDefault() && plainBook.current().guides() == null, "A notebook without options is the default, Blank");
            // Nib Preview (2026-10-29) is on by default: a notebook without its record, any older one, shows the hover ring.
            check(plainBook.options().nibPreview && NotebookOptions.DEFAULT.readNibPreview(null).nibPreview && !NotebookOptions.DEFAULT.readNibPreview(new byte[]{0}).nibPreview
                    && !NotebookOptions.DEFAULT.withNibPreview(false).equals(NotebookOptions.DEFAULT), "Nib Preview: absent is On, its record Off, a change");
            // Self-Portrait Prompt (2026-10-30) is off by default: a notebook without its record, any older one, deals no cards.
            check(!plainBook.options().selfPortrait && !NotebookOptions.DEFAULT.readSelfPortrait(null).selfPortrait && NotebookOptions.DEFAULT.readSelfPortrait(new byte[]{1}).selfPortrait
                    && !NotebookOptions.DEFAULT.withSelfPortrait(true).equals(NotebookOptions.DEFAULT), "Self-Portrait Prompt: absent is Off, its record On, a change");
            // Back to the defaults: the file as it was, byte for byte.
            book.setOptions(NotebookOptions.DEFAULT);
            check(Arrays.equals(bytes(book), before), "Options put back to the default: the file is byte for byte as before them");
            check(Arrays.equals(bytes(BookCodec.read(new ByteArrayInputStream(before))), before), "and a notebook from before them writes itself unchanged");
            // A template record this version doesn't know is Blank; a damaged tools record is My Toolbar.
            check(NotebookOptions.DEFAULT.readTemplate("lined-ish".getBytes("UTF-8")).template == NotebookTemplate.BLANK, "An unknown template is Blank");
            check(!NotebookOptions.DEFAULT.readTools("surprise\nnot a brush".getBytes("UTF-8")).ownsTools() && !NotebookOptions.DEFAULT.readTools(new byte[0]).ownsTools(), "A toolbar that doesn't read is My Toolbar");
            check(!NotebookOptions.DEFAULT.withTools(NotebookOptions.Tools.SURPRISE, lines.subList(0, 3)).ownsTools(), "and so is one with too few slots");
            check(ToolSlots.Toolbar.parse(lines).lines().equals(lines), "A toolbar's lines read back to themselves");
            check(full.withToolbar(lines).equals(full) && !full.withToolbar(toolbarLines(6)).equals(full) && !NotebookOptions.DEFAULT.withToolbar(lines).ownsTools(), "A toolbar changed in a notebook that has its own; none for one that hasn't");
        } finally { delete(dir); }
    }
    /**
     * The template's guides on the screen (the owner, 2026-10-22: "A way to hide/show/adjust transparency of template"):
     * Full as made, Half and Faint lightened as a ghost of that step, Hidden none; a record only when not Full; the page
     * drawn on shows them so, its copies print them in full (Guides in PDF's rule alone).
     */
    static void guidesShown() throws Exception {
        NotebookOptions o = NotebookOptions.DEFAULT.withTemplate(NotebookTemplate.GRID);
        check(o.guidesShown == NotebookOptions.FULL && o.withNextGuidesStep().withNextGuidesStep().withNextGuidesStep().guidesShown == NotebookOptions.HIDDEN
                && o.withGuidesShown(NotebookOptions.HIDDEN).withNextGuidesStep().guidesShown == NotebookOptions.FULL, "A tap goes Full, Half, Faint, Hidden, Full");
        check(String.join(" ", NotebookOptions.GUIDE_STEPS).equals("Full Half Faint Hidden") && NotebookOptions.guidesNote(NotebookOptions.FAINT).equals("Template faint"), "Its words");
        byte[] full = NotebookTemplate.GRID.shared(W, H);
        check(NotebookTemplate.GRID.shown(W, H, NotebookOptions.FULL) == full && NotebookTemplate.GRID.shown(W, H, NotebookOptions.HIDDEN) == null && NotebookTemplate.BLANK.shown(W, H, NotebookOptions.HALF) == null, "Full is the guides as made, Hidden none");
        byte[] half = NotebookTemplate.GRID.shown(W, H, NotebookOptions.HALF), faint = NotebookTemplate.GRID.shown(W, H, NotebookOptions.FAINT);
        check(half == NotebookTemplate.GRID.shown(W, H, NotebookOptions.HALF), "made once, shared");
        int lines = 0;
        for (int i = 0; i < full.length; i++) {
            int f = full[i] & 255, h = half[i] & 255, a = faint[i] & 255;
            if (f == 255) { check(h == 255 && a == 255, "paper stays paper"); continue; }
            lines++;
            check(h == 255 - Math.round((255 - f) * .44f) || Math.abs(h - (255 - (255 - f) * .44)) <= 1, "Half at a ghost's 44%: " + f + " -> " + h);
            check(f < h && h < a && a < 255, "Half lighter than Full, Faint lighter still: " + f + " " + h + " " + a);
        }
        check(lines > 100, "the grid has lines: " + lines);
        // The record: alone, read back; none for Full.
        for (int step = NotebookOptions.HALF; step <= NotebookOptions.HIDDEN; step++) {
            BookOptions.Covers covers = new BookOptions.Covers(); covers.options = NotebookOptions.DEFAULT.withGuidesShown(step);
            byte[] block = BookOptions.bytes(covers);
            BookOptions.Found found = BookOptions.find(BookOptions.of(block), 0, block.length);
            check(found != null && found.records.size() == 1 && found.records.get(0).tag == BookOptions.GUIDES_SHOWN && found.records.get(0).length == 1
                    && BookOptions.options(found, BookOptions.of(block)).equals(covers.options), "Step " + step + " alone is one GSHN record, read back");
        }
        check(NotebookOptions.DEFAULT.readGuidesShown(new byte[]{9}).guidesShown == NotebookOptions.FULL, "A step this version doesn't know is Full");
        // A notebook: the page drawn on shows the step, the copies the guides in full; the file and the recovery keep it.
        DrawingBook book = book();
        book.setOptions(o.withGuidesShown(NotebookOptions.FAINT));
        check(book.current().guides() == faint, "The page drawn on shows the guides faint");
        check(book.snapshot().printed(0).guides() == full, "Its copies print them in full");
        DrawingBook read = BookCodec.read(new ByteArrayInputStream(bytes(book)));
        check(read.options().guidesShown == NotebookOptions.FAINT && read.current().guides() == faint, "The file keeps the step");
        ByteArrayOutputStream recovery = new ByteArrayOutputStream();
        RecoveryCodec.write(recovery, book.snapshot(), "");
        check(RecoveryCodec.read(new ByteArrayInputStream(recovery.toByteArray())).book.options().guidesShown == NotebookOptions.FAINT, "and the recovery");
        book.setOptions(o.withGuidesShown(NotebookOptions.HIDDEN));
        check(book.current().guides() == null && book.snapshot().printed(0).guides() == full, "Hidden: none on the page drawn on, the copies as Guides in PDF says");
        book.setOptions(o);
        check(book.current().guides() == full, "Full again: the guides as made");
    }
    /**
     * The owner, 2026-10-22: "opaque media should cover template". The guides are the paper under every layer, so opaque
     * paint (Medium Opaque, full Cover) hides them on the page drawn on and in the copies, light or dark, while a wash
     * shows them through as much as it is transparent. Measured: the share of a grid line's contrast left under the mark.
     */
    static void guidesUnderPaint() {
        int w = 480, h = 360;
        byte[] g = NotebookTemplate.GRID.shared(w, h);
        for (String name : new String[]{"Opaque round", "Flat gouache", "Impasto", "Soft wash"})
            for (double dark : new double[]{1, .5, .15, 0}) {
                ToneDocument d = InkWashLayers.blank(w, h);
                d.placeGuides(g);
                CarrySmudgeChecks.live(d, GravityChecks.preset(name), 60, dark, null, CarrySmudgeChecks.across(40, 440, 180));
                PagePicture.Composite printed = PagePicture.Composite.printed(d.layerSnapshot());
                int[][] rows = new int[h][];
                double page = 0, copy = 0; int n = 0;
                for (int y = 172; y < 188; y++) {
                    rows[y] = new int[w]; printed.row(y, rows[y]);
                    for (int x = 100; x < 380; x++) {
                        int i = y * w + x, gi = g[i] & 255;
                        if (gi >= 200 || (g[i + 3] & 255) != 255 || (g[i - 3] & 255) != 255) continue;
                        n++;
                        page += Math.max(0, (d.compositeTone(x + 3, y) + d.compositeTone(x - 3, y)) / 2 - d.compositeTone(x, y)) / (double) (255 - gi);
                        copy += Math.max(0, (rows[y][x + 3] + rows[y][x - 3]) / 2 - rows[y][x]) / (double) (255 - gi);
                    }
                }
                page /= n; copy /= n;
                boolean opaque = !name.equals("Soft wash");
                check(n > 100 && (opaque ? page < .01 && copy < .01 : dark <= .5 ? page > .3 : true),
                        name + " at " + dark + ": the guides " + (opaque ? "covered" : "showing through") + " (page " + page + ", copy " + copy + ")");
            }
    }
    static void delete(File f) { File[] all = f.listFiles(); if (all != null) for (File c : all) delete(c); f.delete(); }

    // ---- the guides are paper ----

    static void guidesAreNotMarks() throws Exception {
        DrawingBook book = new DrawingBook(new ToneDocument(W, H));
        book.addPage(); book.select(0);
        book.setOptions(NotebookOptions.DEFAULT.withTemplate(NotebookTemplate.DIARY));
        ToneDocument page = book.current();
        byte[] g = NotebookTemplate.DIARY.shared(W, H);
        check(page.guides() == g, "A page of a notebook with a template holds the guides");
        check(book.picture(0) == null, "(no picture yet)");
        int guide = -1, none = -1;
        for (int i = 0; i < g.length && (guide < 0 || none < 0); i++) { if ((g[i] & 255) != 255 && guide < 0) guide = i; if ((g[i] & 255) == 255 && none < 0) none = i; }
        int gx = guide % W, gy = guide / W, nx = none % W, ny = none / W;
        check(page.compositeTone(gx, gy) == (g[guide] & 255) && page.compositeTone(nx, ny) == 255, "The page shows the guide, and paper elsewhere");
        check(page.unmarked() && BlankPages.blank(page) && BlankPages.blank(book, 0) && BlankPages.empty(book), "A page with only guides counts as blank, and so does a notebook of them");
        check(Arrays.equals(DrawingBook.encode(page), BlankPages.made(W, H).bytes), "Its file holds no guides: the blank page's own bytes");
        check(PagePicture.of(page.layerSnapshot()).white(), "and its picture none");
        long revision = page.revision();
        book.setOptions(NotebookOptions.DEFAULT.withTemplate(NotebookTemplate.COMICS));
        check(page.revision() == revision && page.guides() == NotebookTemplate.COMICS.shared(W, H), "A template changed doesn't change the page, only its guides");
        book.setOptions(NotebookOptions.DEFAULT.withTemplate(NotebookTemplate.DIARY));
        g = NotebookTemplate.DIARY.shared(W, H);
        // The eraser never takes them.
        page = book.current();
        page.begin(); page.eraseTone(gx, gy, 1); page.finish();
        check(page.compositeTone(gx, gy) == (g[guide] & 255), "The eraser never takes a guide");
        page.begin(); page.setTone(gx, gy, 0); page.finish();
        check(page.compositeTone(gx, gy) == 0 && !page.unmarked() && !BlankPages.blank(page), "A mark over a guide shows, and the page isn't blank");
        page.begin(); page.eraseTone(gx, gy, 1); page.finish();
        check(page.compositeTone(gx, gy) == (g[guide] & 255) && page.unmarked(), "Erased, the guide is back and the page blank again");
        // Under a shade the darker of the two shows.
        page.shadePaper(120);
        check(page.compositeTone(nx, ny) == 120 && page.compositeTone(gx, gy) == Math.min(120, g[guide] & 255), "A shaded page shows the darker of guide and shade");
        byte[][] tones = new byte[ToneDocument.STACK + 1][], alpha = new byte[ToneDocument.STACK + 1][];
        int count = page.visibleLayers(tones, alpha, new int[ToneDocument.STACK + 1]);
        check(count == page.layerCount() + 1 && tones[0] == ToneDocument.guided(g, 120), "and the screen composes the guides as the layer under them");
        page.shadePaper(255);
        check(new ToneDocument(page.layerSnapshot()).guides() == g, "A snapshot keeps the guides");
        // Covers have none; pages added or copied have them; Blank takes them off.
        book.select(DrawingBook.FRONT);
        check(book.current().guides() == null, "The front cover has no guides");
        book.select(DrawingBook.BACK);
        check(book.current().guides() == null, "nor the back");
        book.select(1);
        check(book.current().guides() == g, "Another page has them");
        book.addPage();
        check(book.current().guides() == g, "A page added has them");
        BlankPagesChecks.mark(book.current());
        book.duplicatePage();
        check(book.current().guides() == g, "A copy of a page has them");
        book.setOptions(NotebookOptions.DEFAULT);
        check(book.current().guides() == null, "Blank takes them off every page decoded");
        book.select(0);
        check(book.current().guides() == null, "and the others when decoded");
        // Opened from a file with a template, the pages have them from the first.
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        book.setOptions(NotebookOptions.DEFAULT.withTemplate(NotebookTemplate.COMICS));
        BookCodec.write(out, book.snapshot());
        DrawingBook read = BookCodec.read(new ByteArrayInputStream(out.toByteArray()));
        check(read.current().guides() == NotebookTemplate.COMICS.shared(W, H), "A notebook read with a template shows it at once");
        read.select(DrawingBook.FRONT); read.select(read.count() - 1);
        check(read.current().guides() != null, "and on the page it turns to");
        // A notebook of guides alone is empty and its pages blank at the end.
        check(BlankPages.empty(new DrawingBook(new ToneDocument(W, H))), "(and a plain one)");
    }

    // ---- the copies ----

    /** A grey PNG's pixels. */
    static int[] tones(byte[] file) throws Exception {
        NotebookExport.Png png = NotebookExport.read(file);
        Inflater inflater = new Inflater(); inflater.setInput(png.data);
        byte[] raw = new byte[(png.width + 1) * png.height];
        int got = 0;
        while (got < raw.length) { int n = inflater.inflate(raw, got, raw.length - got); if (n == 0) break; got += n; }
        int[] out = new int[png.width * png.height];
        for (int y = 0; y < png.height; y++) {
            int type = raw[y * (png.width + 1)];
            for (int x = 0; x < png.width; x++) {
                int v = raw[y * (png.width + 1) + 1 + x] & 255;
                int left = x > 0 ? out[y * png.width + x - 1] : 0, up = y > 0 ? out[(y - 1) * png.width + x] : 0;
                out[y * png.width + x] = (type == 1 ? v + left : type == 2 ? v + up : v) & 255;
            }
        }
        return out;
    }

    static void copies() throws Exception {
        File dir = Files.createTempDirectory("copies").toFile();
        try {
            List<PackedPage> pages = new ArrayList<>();
            ToneDocument first = marked(1), second = marked(2);
            pages.add(BlankPagesChecks.packed(first)); pages.add(BlankPagesChecks.packed(second));
            byte[] g = NotebookTemplate.DIARY.shared(W, H);
            int guide = -1;
            for (int i = 0; i < g.length && guide < 0; i++) if ((g[i] & 255) != 255) guide = i;
            // A guide pixel the marks leave alone, in the first page.
            ToneDocument probe = DocumentCodec.read(new ByteArrayInputStream(pages.get(0).bytes()));
            for (int i = guide; i < g.length; i++) if ((g[i] & 255) != 255 && probe.compositeTone(i % W, i / W) == 255) { guide = i; break; }
            int expectGuide = DotPattern.exportGray(g[guide] & 255), expectPaper = DotPattern.exportGray(255);

            File plain = new File(dir, "plain"), printed = new File(dir, "printed");
            plain.mkdirs(); printed.mkdirs();
            NotebookExport.Done a = NotebookExport.update(plain, "nb", pages, null, null);
            byte[] plainPng = Files.readAllBytes(new File(plain, "nb/001.png").toPath());
            check(a.drawn == 2 && NotebookExport.read(plainPng).crc == pages.get(0).crc, "The copies without options: a PNG a page carrying its page's CRC, as before");
            NotebookExport.Done b = NotebookExport.update(printed, "nb", pages, null, null, NotebookOptions.DEFAULT);
            check(Arrays.equals(Files.readAllBytes(new File(printed, "nb/001.png").toPath()), plainPng) && b.drawn == 2
                    && Arrays.equals(Files.readAllBytes(NotebookExport.pdf(printed, "nb").toPath()), Files.readAllBytes(NotebookExport.pdf(plain, "nb").toPath())),
                    "Default options: the PNG and the PDF byte for byte as without");
            // A template, printed.
            NotebookOptions diary = NotebookOptions.DEFAULT.withTemplate(NotebookTemplate.DIARY);
            NotebookExport.Done c = NotebookExport.update(printed, "nb", pages, null, null, diary);
            byte[] guidePng = Files.readAllBytes(new File(printed, "nb/001.png").toPath());
            check(c.drawn == 2 && c.kept == 0, "A template added draws every page again");
            check(NotebookExport.read(guidePng).crc == NotebookExport.pageKey(pages.get(0).crc, NotebookTemplate.DIARY) && NotebookExport.read(guidePng).crc != pages.get(0).crc, "and its PNG carries the page's CRC with the template's");
            int[] shown = tones(guidePng), without = tones(plainPng);
            check(shown[guide] == expectGuide && without[guide] == expectPaper, "The PNG has the guides under the marks (" + shown[guide] + " against " + without[guide] + ")");
            int differ = 0;
            for (int i = 0; i < shown.length; i++) if (shown[i] != without[i]) differ++;
            check(differ > 100, "and only them differ");
            check(Files.size(NotebookExport.pdf(printed, "nb").toPath()) != Files.size(NotebookExport.pdf(plain, "nb").toPath()), "so does the PDF");
            NotebookExport.Done d = NotebookExport.update(printed, "nb", pages, null, null, diary);
            check(d.drawn == 0 && d.kept == 2, "Nothing changed: nothing drawn again");
            // Guides in PDF (Leave Out) is cut (2026-10-24): the guides always print, as on paper (NotebookRulesChecks.lockCut).
            // Covers have none.
            NotebookExport.Covers covers = new NotebookExport.Covers("nb", CoverDesign.DEFAULT, W, H, null, null, -1);
            File one = new File(dir, "one"), two = new File(dir, "two"); one.mkdirs(); two.mkdirs();
            NotebookExport.update(one, "nb", pages, null, covers, NotebookOptions.DEFAULT);
            NotebookExport.update(two, "nb", pages, null, covers, diary);
            check(Arrays.equals(Files.readAllBytes(new File(one, "nb/" + NotebookExport.FRONT_PNG).toPath()), Files.readAllBytes(new File(two, "nb/" + NotebookExport.FRONT_PNG).toPath()))
                    && Arrays.equals(Files.readAllBytes(new File(one, "nb/" + NotebookExport.BACK_PNG).toPath()), Files.readAllBytes(new File(two, "nb/" + NotebookExport.BACK_PNG).toPath())), "The covers are the same with a template: they have no guides");
            // The manual PNG export's page, from a snapshot.
            DrawingBook book = book(); book.setOptions(diary);
            DrawingBook.Snapshot snapshot = book.snapshot();
            check(snapshot.printed(1).guides() == g && snapshot.printed(DrawingBook.FRONT).guides() == null, "A snapshot's page for the copies has the guides, a cover none");
            check(snapshot.printed(1).exportPixels(true)[guide] == (0xff000000 | expectGuide * 0x010101), "and the guides export as the PNG has them");
        } finally { delete(dir); }
    }

    // ---- Surprise Me ----

    static List<Brush> library(long seed, boolean rich) {
        List<Brush> all = new ArrayList<>();
        Random r = new Random(seed);
        if (!rich) { for (int i = 0; i < ToolSlots.COUNT; i++) all.add(ToolSlotsChecks.old(i)); return all; }
        for (ModularStyle style : ModularStyle.all()) for (int k = 0; k < 4; k++) all.add(Brush.of(style.roll(r)));
        for (int k = 0; k < 8; k++) all.add(SurpriseTools.fresh(SurpriseTools.Role.FANCY, Collections.<Brush>emptyList(), r));
        for (int k = 0; k < 6; k++) all.add(SurpriseTools.fresh(SurpriseTools.Role.PEN, Collections.<Brush>emptyList(), r));
        return all;
    }
    static boolean in(List<Brush> list, Brush b) { for (Brush x : list) if (x.sameAs(b)) return true; return false; }

    static void surprise() {
        ToolSlotsChecks.prefs = new HashMap<>();
        ToolSwatches swatches = new ToolSwatches(ToolSlotsChecks.store());
        List<Brush> none = Collections.emptyList(), first = library(0, false), rich = library(1, true);
        // The roles' rules on known brushes.
        check(SurpriseTools.plain(ToolSlotsChecks.old(0)) && SurpriseTools.fast(ToolSlotsChecks.old(0)) && SurpriseTools.simple(ToolSlotsChecks.old(0)), "The Fineliner is a plain fast pen");
        check(SurpriseTools.kind(ToolSlotsChecks.old(6)) == SurpriseTools.Kind.WASH && SurpriseTools.kind(ToolSlotsChecks.old(2)) == SurpriseTools.Kind.DRY
                && SurpriseTools.kind(ToolSlotsChecks.old(1)) == SurpriseTools.Kind.LINE, "Oils is a wash, Dither pencil dry, Halftone brush a line");
        check(SurpriseTools.kind(ToolPresets.of(ModularStyle.named("Fineliner")).stream().filter(p -> p.name.equals("Hand hatch")).findFirst().get().brush()) == SurpriseTools.Kind.DRY, "Hand hatch is dry, as Hatch is");
        Brush flick = Brush.of(ModularStyle.named("Brush pen").roll(new Random(3)));
        flick.parts.part(BrushPart.TIP).set(PartDial.FLICK, .5);
        check(SurpriseTools.fancy(flick) && !SurpriseTools.simple(flick) && !SurpriseTools.plain(flick), "A flick is fancy");
        long start = System.nanoTime();
        int rolls = 0, fromLibrary = 0, richRolls = 0;
        Set<String> seen = new HashSet<>();
        for (long seed = 0; seed < 300; seed++) {
            for (int which = 0; which < 3; which++) {
                List<Brush> library = which == 0 ? none : which == 1 ? first : rich;
                Random dice = new Random(seed * 7 + which);
                List<SurpriseTools.Pick> picks = SurpriseTools.roll(library, swatches, dice);
                rolls++;
                String problem = SurpriseTools.problem(picks);
                check(problem == null, "Seed " + seed + " library " + which + ": " + problem);
                int fromLib = 0;
                for (SurpriseTools.Pick p : picks) {
                    check(p.px >= ModularSizes.MIN && p.px <= ModularSizes.MAX && p.darkness > 0 && p.darkness <= 1, "Each at a size and grey");
                    if (p.library) { fromLib++; check(in(library, p.brush), "A library pick is the owner's"); }
                    seen.add(p.brush.identity());
                }
                check(fromLib <= SurpriseTools.FROM_LIBRARY, "No more than half from the library");
                if (which == 0) check(fromLib == 0, "No library: all fresh");
                if (which == 2) { check(fromLib >= 4 && fromLib <= 6, "About half from a rich library: " + fromLib); fromLibrary += fromLib; richRolls++; }
                // Each slot re-rolled alone: the others untouched, the mix still balanced, the role and source kept.
                for (int slot = 0; slot < ToolSlots.COUNT; slot++) {
                    if ((seed + slot) % 5 != 0) continue;
                    List<SurpriseTools.Pick> next = SurpriseTools.reroll(picks, slot, library, swatches, dice);
                    check(next.size() == picks.size(), "Every slot again");
                    for (int i = 0; i < picks.size(); i++) if (i != slot) check(next.get(i) == picks.get(i), "The other seven stay");
                    check(next.get(slot).role == picks.get(slot).role, "The slot keeps its role");
                    check(SurpriseTools.problem(next) == null, "Seed " + seed + " slot " + slot + " re-rolled: " + SurpriseTools.problem(next));
                    if (slot == 0) check(SurpriseTools.plain(next.get(0).brush), "The first slot stays a plain pen");
                    if (next.get(slot).library) check(in(library, next.get(slot).brush), "A re-rolled library pick is the owner's");
                }
            }
        }
        double mean = fromLibrary / (double) richRolls;
        check(mean > 4.6 && mean < 5.4, "Half from a rich library on the whole: " + mean);
        check(seen.size() > 600, "A great variety: " + seen.size() + " different brushes in " + rolls + " rolls");
        double ms = (System.nanoTime() - start) / 1e6 / rolls;
        Timing.limit(ms < 150, "A roll takes " + ms + " ms");
        System.out.println(String.format(Locale.ROOT, "Surprise Me: %d rolls over %d brushes, %.1f ms a roll with its re-rolls, %.2f of ten from a rich library", rolls, seen.size(), ms, mean));
    }

    // ---- the toolbar ----

    static void toolbar() throws Exception {
        ToolSlotsChecks.prefs = new HashMap<>();
        ToolSlots slots = new ToolSlots(ToolSlotsChecks.store(), new Random(1));
        ToolSlots.Toolbar app = slots.toolbar();
        // The app's own, picked out of the ordinary a little.
        slots.use(2, ToolSlotsChecks.style("Dip pen", 4));
        slots.swatches.chooseSize(ToolSlots.key(2), 3);
        ToolSlots.Toolbar mine = slots.toolbar();
        check(!mine.sameAs(app), "(the app's toolbar changed)");
        List<BrushLibrary.Entry> libraryBefore = slots.library.entries();
        List<Brush> recentBefore = slots.recent();
        List<SurpriseTools.Pick> picks = SurpriseTools.roll(library(2, true), slots.swatches, new Random(8));
        ToolSlots.Toolbar own = SurpriseTools.toolbar(picks);
        NotebookOptions options = NotebookOptions.DEFAULT.withTools(NotebookOptions.Tools.SURPRISE, own.lines());
        check(!slots.inNotebook(), "(no notebook yet)");
        check(!slots.leave(), "Nothing to put back before a notebook's");
        check(slots.enter(ToolSlots.of(options)) && slots.inNotebook(), "Opening a notebook with its own toolbar loads it");
        for (int i = 0; i < ToolSlots.COUNT; i++) {
            check(slots.brush(i).sameAs(own.brushes[i]), "Slot " + i + " holds the notebook's brush");
            ToolSwatches.Look look = slots.swatches.look(ToolSlots.key(i), slots.brush(i));
            check(look.px == own.px[i] && look.darkness == own.darkness[i], "at the notebook's size and grey for it: " + look.px + " " + look.darkness);
        }
        check(slots.toolbar().sameAs(own), "The slots are the notebook's");
        check(!slots.canUndo(), "(a roll or pick before is forgotten)");
        check(slots.library.entries().size() == libraryBefore.size() && slots.recent().equals(recentBefore) || slots.recent().size() == recentBefore.size(), "The library and the recent brushes are the app's, untouched");
        // Changed while the notebook is open, then read back as its toolbar.
        slots.use(4, ToolSlotsChecks.style("Marker", 9));
        slots.swatches.chooseSize(ToolSlots.key(4), 0);
        ToolSlots.Toolbar changed = slots.toolbar();
        check(!changed.sameAs(own), "(the notebook's toolbar changed)");
        NotebookOptions kept = options.withToolbar(changed.lines());
        check(ToolSlots.Toolbar.parse(kept.toolbar).sameAs(changed), "and its lines keep the change");
        // Leaving puts the app's brushes back, at their sizes and greys (each tool's own, 2026-10-27).
        check(slots.leave() && !slots.inNotebook(), "Leaving puts the app's toolbar back");
        check(slots.toolbar().sameAs(mine), "as it was");
        check(slots.swatches.sizeInUse(ToolSlots.key(2)) == 3 && slots.swatches.sizeInUse(ToolSlots.key(4)) != 0, "each slot's size as the app's toolbar had it");
        check(!slots.leave(), "and again changes nothing");
        // Two notebooks in turn: the app's kept once, through both.
        ToolSlots.Toolbar other = SurpriseTools.toolbar(SurpriseTools.roll(none(), slots.swatches, new Random(9)));
        slots.enter(own); slots.enter(other);
        check(slots.toolbar().sameAs(other), "Another notebook's toolbar loads over the first");
        slots.leave();
        check(slots.toolbar().sameAs(mine), "and leaving still gives the app's");
        // A notebook with My Toolbar, or lines that don't read, has none of its own.
        check(ToolSlots.of(NotebookOptions.DEFAULT) == null && ToolSlots.of(null) == null, "My Toolbar: none of its own");
        // A launch cut short in a notebook: the app's toolbar still waits, and the next leave gives it back.
        slots.enter(own);
        ToolSlots again = new ToolSlots(ToolSlotsChecks.store(), new Random(2));
        check(again.inNotebook() && again.toolbar().sameAs(own), "A relaunch finds the notebook's toolbar loaded and the app's waiting");
        again.leave();
        check(again.toolbar().sameAs(mine), "and puts the app's back");
        // Through a notebook's file.
        DrawingBook book = book();
        book.setOptions(options);
        DrawingBook read = BookCodec.read(new ByteArrayInputStream(bytes(book)));
        check(ToolSlots.of(read.options()).sameAs(own) && read.options().tools == NotebookOptions.Tools.SURPRISE, "A notebook's file keeps its toolbar as brush codes with their sizes and greys");
    }
    static List<Brush> none() { return Collections.emptyList(); }

    // ---- an older version's reading ----

    static NotebookOptions oldOptions() {
        return NotebookOptions.DEFAULT.withTemplate(NotebookTemplate.COMICS).withTools(NotebookOptions.Tools.LIBRARY, toolbarLines(11))
                .withNoErasing(true).withSeconds(180);
    }
    /** A notebook with every option, two marked pages, for the version before options. */
    static void write(File file) throws Exception {
        DrawingBook book = book();
        book.setOptions(oldOptions());
        // The first page timed out and locked: its record holds a clock the older version doesn't know.
        book.current().setClock(PageClock.NONE.start().lock(180_000));
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(file))) { BookCodec.write(out, book.snapshot()); }
        System.out.println("PASS: wrote a notebook with a template, a toolbar of its own, the rules and a locked page");
    }
    /** What the older version saved again opens here with all the defaults and its pages as they were. */
    static void older(File file) throws Exception {
        DrawingBook read = BookCodec.open(file);
        check(read.options().isDefault() && read.count() == 2, "A notebook the version before options saved again has the default options and its pages");
        check(read.current().guides() == null, "so no guides");
        check(read.current().clock().locked && !read.current().locked(), "while the page's run-out clock, in the page's own record, went through the older version's save untouched: it opens run out, editable (Lock Pages off)");
        BlankPagesChecks.check(read.current().compositeTone(0, 0) >= 0, "and reads");
        System.out.println("PASS: the version before options saved the notebook again without its options (the rules too); it opens here with the defaults and its pages, a page run out still so");
    }
}
