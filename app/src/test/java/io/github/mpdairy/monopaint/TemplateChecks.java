package io.github.mpdairy.monopaint;

import java.io.*;
import java.nio.file.Files;
import java.util.*;

/**
 * The page templates ({@link NotebookTemplate}): every built-in drawn at each page size as thin guides in the panel's
 * greys, the same twice; the owner's own templates from MyStyle read by name, turned to the panel's greys, Blank (no
 * crash) when the file is gone; Random's set kept in its key; a Random notebook's pages each given a template once, the
 * same after moves, copies, deletes, a save, a reopen and the recovery, and the copies drawing each page's own.
 */
public final class TemplateChecks {
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    public static void main(String[] args) throws Exception {
        Assets.install(Assets.folder(Assets.FOLDER));
        drawn();
        own();
        random();
        System.out.println("PASS: " + (NotebookTemplate.values().length - 2) + " drawn templates thin, in the panel's greys, at the Manta's, the Nomad's and thumbnail sizes; own templates read by name in the panel's greys, Blank when gone; Random's set in its key; a different template a page, never the page before's; a Random notebook's pages keep their templates through moves, copies, deletes, a save, a reopen, the recovery and the copies");
    }

    static void drawn() {
        int[][] sizes = {{1920, 2560}, {1404, 1872}, {300, 400}, {96, 128}};
        Set<Integer> greys = new HashSet<>();
        for (int g : GrayPalette.VALUES) greys.add(g);
        for (NotebookTemplate t : NotebookTemplate.values()) {
            check(NotebookTemplate.of(t.key) == t, t.label + " is found by its key");
            if (t == NotebookTemplate.BLANK || t.random()) { check(t.guides(300, 400) == null && t.shared(300, 400) == null, t.label + " has no guides of its own"); continue; }
            for (int[] s : sizes) {
                byte[] g = t.guides(s[0], s[1]);
                check(g != null && g.length == s[0] * s[1], t.label + " is a tone a pixel at " + s[0]);
                int lines = 0;
                for (byte b : g) { int v = b & 255; check(v == NotebookTemplate.NONE || v == NotebookTemplate.RULE || v == NotebookTemplate.PANEL, t.label + ": only the panel's greys, " + v); if (v != NotebookTemplate.NONE) lines++; }
                double share = lines / (double) g.length;
                check(share > .001 && share < (s[0] < 200 ? .6 : s[0] <= 300 ? .3 : .12), t.label + " at " + s[0] + " is thin: " + share);
            }
            check(Arrays.equals(t.guides(300, 400), t.guides(300, 400)) && t.shared(300, 400) == t.shared(300, 400), t.label + " is the same twice, and shared");
        }
        check(NotebookTemplate.LINED.label.equals("Lined") && NotebookTemplate.DOTS.label.equals("Dot Grid") && NotebookTemplate.WIDE.label.equals("Wide Lined"), "Labels in Title Case");
        // Wide Lined has fewer, wider rules than Lined; Grid crosses the whole page.
        check(runs(NotebookTemplate.WIDE.guides(1920, 2560), 1920, 1000) < runs(NotebookTemplate.LINED.guides(1920, 2560), 1920, 1000), "Wide Lined is wider than Lined");
        byte[] grid = NotebookTemplate.GRID.guides(1920, 2560);
        check(runs(grid, 1920, 5) > 30 && (grid[20 * 1920 + 1] & 255) != NotebookTemplate.NONE && (grid[20 * 1920 + 1918] & 255) != NotebookTemplate.NONE, "Grid runs edge to edge");
        // Music: five lines a staff.
        check(runs(NotebookTemplate.MUSIC.guides(1920, 2560), 1920, 960) % 5 == 0, "Music's staves have five lines");
    }
    /** How many separate guides a column {@code x} of a page {@code w} wide crosses. */
    static int runs(byte[] g, int w, int x) {
        int n = 0;
        for (int y = 0, was = 255; y < g.length / w; y++) { int v = g[y * w + x] & 255; if (v != 255 && was == 255) n++; was = v; }
        return n;
    }

    static void own() throws IOException {
        File dir = Files.createTempDirectory("mystyle").toFile();
        File was = NotebookTemplate.folder; NotebookTemplate.Images reader = NotebookTemplate.images;
        try {
            Files.write(new File(dir, "Notes.webp").toPath(), new byte[]{1});
            Files.write(new File(dir, "3D Regular.png").toPath(), new byte[]{1});
            Files.write(new File(dir, "readme.txt").toPath(), new byte[]{1});
            NotebookTemplate.folder = dir;
            // A picture: black lines on white, a grey square, a see-through corner.
            NotebookTemplate.images = (file, w, h) -> {
                int[] px = new int[w * h];
                for (int i = 0; i < px.length; i++) { int x = i % w, y = i / w; px[i] = x < 4 && y < 4 ? 0 : y % 20 == 0 ? 0xff000000 : x > w / 2 && y > h / 2 ? 0xff808080 : 0xffffffff; }
                return px;
            };
            List<NotebookTemplate> mine = NotebookTemplate.mine();
            check(mine.size() == 2 && mine.get(0).label.equals("3D Regular") && mine.get(1).key.equals("my:Notes.webp") && mine.get(1).own(), "MyStyle's pictures, by name, sorted: " + mine);
            check(NotebookTemplate.choices().size() == NotebookTemplate.values().length + 2 && NotebookTemplate.choices().get(NotebookTemplate.choices().size() - 1) == NotebookTemplate.RANDOM, "offered after the built-ins, Random last");
            NotebookTemplate notes = NotebookTemplate.of("my:Notes.webp");
            check(notes.equals(mine.get(1)) && notes.label.equals("Notes"), "found again by its key");
            for (int[] s : new int[][]{{1920, 2560}, {300, 400}}) {
                byte[] g = notes.guides(s[0], s[1]);
                check(g != null && g.length == s[0] * s[1], "scaled to the page at " + s[0]);
                check((g[0] & 255) == NotebookTemplate.NONE && (g[s[0] * 20 + 10] & 255) == 0 && (g[(s[1] - 3) * s[0] + s[0] - 3] & 255) == 128 && (g[s[0] * 21 + 10] & 255) == NotebookTemplate.NONE, "see-through and white are paper, the rest the panel's greys");
            }
            // A notebook with one; then the file goes: Blank, no crash.
            DrawingBook book = new DrawingBook(new ToneDocument(300, 400));
            book.setOptions(NotebookOptions.DEFAULT.withTemplate(notes));
            check(book.current().guides() != null && book.template(0).equals(notes), "a page shows it");
            byte[] file = NotebookOptionsChecks.bytes(book);
            new File(dir, "Notes.webp").delete();
            DrawingBook read = BookCodec.read(new ByteArrayInputStream(file));
            check(read.options().template.equals(notes) && read.current().guides() == null, "gone: kept by name, the page Blank");
            check(NotebookTemplate.of("my:") == NotebookTemplate.BLANK && NotebookTemplate.of("my:a/b.png") == NotebookTemplate.BLANK, "a name that can't be a file is Blank");
            NotebookTemplate.images = (f, w, h) -> { throw new RuntimeException("unreadable"); };
            check(NotebookTemplate.of("my:3D Regular.png").guides(30, 40) == null, "unreadable: Blank");
            NotebookTemplate.folder = null;
            check(NotebookTemplate.mine().isEmpty() && notes.guides(30, 40) == null, "no folder: none");
        } finally {
            NotebookTemplate.folder = was; NotebookTemplate.images = reader;
            for (File f : dir.listFiles()) f.delete();
            dir.delete();
        }
    }

    static void random() throws Exception {
        // Its set, kept in its key; an older version reads the key as Blank.
        NotebookTemplate any = NotebookTemplate.RANDOM, some = NotebookTemplate.random(Arrays.asList(NotebookTemplate.GRID, NotebookTemplate.of("my:Notes.webp")));
        check(any.random() && any.set().isEmpty() && some.random() && some.key.equals("random/grid/my:Notes.webp") && some.set().size() == 2 && NotebookTemplate.of(some.key).equals(some), "Random's set in its key");
        check(NotebookTemplate.random(null) == any && NotebookTemplate.random(Collections.<NotebookTemplate>emptyList()) == any && some.label.equals("Random"), "none: any");
        // Random From with every template ticked: any built-in, unless the owner's own are among them (any would leave them out).
        List<NotebookTemplate> builtIn = new ArrayList<>();
        for (NotebookTemplate t : NotebookTemplate.values()) if (t != NotebookTemplate.BLANK && !t.random()) builtIn.add(t);
        check(CardSheet.randomFrom(builtIn, builtIn) == any, "every built-in ticked: any");
        List<NotebookTemplate> withMine = new ArrayList<>(builtIn); withMine.add(NotebookTemplate.of("my:Notes.webp"));
        check(CardSheet.randomFrom(withMine, withMine).set().contains(NotebookTemplate.of("my:Notes.webp")), "every one ticked with an own template: it stays in the set");
        check(CardSheet.randomFrom(Arrays.asList(NotebookTemplate.GRID), builtIn).set().equals(Arrays.asList(NotebookTemplate.GRID)), "some ticked: those");
        check(NotebookOptions.DEFAULT.readTemplate(NotebookOptions.DEFAULT.withTemplate(some).templateBytes()).template.equals(some), "kept in the file's template record");
        Random dice = new Random(5);
        Set<NotebookTemplate> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) { NotebookTemplate t = any.pick(dice); check(t != NotebookTemplate.BLANK && !t.random(), "any built-in but Blank"); seen.add(t); check(some.set().contains(some.pick(dice)), "or one of its set"); }
        check(seen.size() == NotebookTemplate.values().length - 2, "every built-in in time");
        check(NotebookTemplate.GRID.pick(dice) == NotebookTemplate.GRID, "another template gives itself");
        // A different template a page (the owner, 2026-10-21): never the page before's while the set has another, for
        // pages filled when Random is chosen and pages added after; a set of one gives it to every page.
        for (NotebookTemplate random : new NotebookTemplate[]{any, some}) {
            DrawingBook alike = new DrawingBook(new ToneDocument(300, 400));
            alike.dice = new Random(3);
            for (int i = 0; i < 9; i++) alike.addPage();
            alike.setOptions(NotebookOptions.DEFAULT.withTemplate(random));
            for (int i = 0; i < 20; i++) alike.addPage();
            List<String> keys = alike.pageTemplates();
            for (int i = 1; i < keys.size(); i++) check(!keys.get(i).equals(keys.get(i - 1)), "page " + (i + 1) + " differs from the page before: " + keys);
        }
        check(NotebookTemplate.random(Arrays.asList(NotebookTemplate.GRID)).pick(dice, NotebookTemplate.GRID) == NotebookTemplate.GRID, "a set of one: every page has it");
        // A notebook: each page given one, for good.
        DrawingBook book = new DrawingBook(new ToneDocument(300, 400));
        book.dice = new Random(11);
        book.addPage(); book.addPage();
        book.setOptions(NotebookOptions.DEFAULT.withTemplate(any));
        List<String> given = book.pageTemplates();
        for (String k : given) check(k != null && !NotebookTemplate.of(k).random() && NotebookTemplate.of(k) != NotebookTemplate.BLANK, "every page given one: " + k);
        check(book.current().guides() == NotebookTemplate.of(given.get(2)).shared(300, 400), "the page drawn on shows its own");
        book.addPage(); book.addPage(); book.addPage();
        List<String> more = book.pageTemplates();
        check(more.subList(0, 3).equals(given) && more.get(5) != null && book.current().guides() == NotebookTemplate.of(more.get(5)).shared(300, 400), "a new page gets one; the others keep theirs");
        book.move(5, 0);
        check(book.pageTemplates().get(0).equals(more.get(5)) && book.pageTemplates().get(1).equals(more.get(0)), "it moves with its page");
        book.select(1); book.duplicatePage();
        check(book.pageTemplates().get(2).equals(book.pageTemplates().get(1)), "a copy keeps its original's");
        List<String> now = book.pageTemplates();
        book.deletePage(4);
        List<String> after = new ArrayList<>(now); after.remove(4);
        check(book.pageTemplates().equals(after), "a delete takes only its page's");
        book.setOptions(NotebookOptions.DEFAULT.withTemplate(NotebookTemplate.LINED));
        check(book.template(0) == NotebookTemplate.LINED && book.current().guides() == NotebookTemplate.LINED.shared(300, 400), "another template: every page has it");
        book.setOptions(NotebookOptions.DEFAULT.withTemplate(some));
        check(book.pageTemplates().equals(after) && book.template(0).equals(NotebookTemplate.of(after.get(0))), "Random again (another set): the pages keep what they were given");
        // Marks on every page, so the copies draw them all (a blank last page has none).
        for (int i = 0; i < book.count(); i++) { book.select(i); BlankPagesChecks.mark(book.current()); }
        // Kept: the file, a reopen, the recovery; an all-Blank notebook keeps no record.
        byte[] file = NotebookOptionsChecks.bytes(book);
        DrawingBook read = BookCodec.read(new ByteArrayInputStream(file));
        check(read.pageTemplates().equals(after) && read.options().template.equals(some) && !read.unsaved(), "read back from the file, not a change");
        File dir = Files.createTempDirectory("random").toFile();
        try {
            File f = new File(dir, "r.mpaint"); Files.write(f.toPath(), file);
            check(BookCodec.open(f).pageTemplates().equals(after) && BookCodec.openPages(f).covers.pageTemplates.equals(after), "and from a file opened, as the shelf reads it");
        } finally { for (File x : dir.listFiles()) x.delete(); dir.delete(); }
        ByteArrayOutputStream recovery = new ByteArrayOutputStream();
        RecoveryCodec.write(recovery, book.snapshot(), "");
        check(RecoveryCodec.read(new ByteArrayInputStream(recovery.toByteArray())).book.pageTemplates().equals(after), "the recovery keeps them");
        check(BookOptions.pageTemplateBytes(Arrays.asList(null, null)) == null && BookOptions.pageTemplates("a\nb".getBytes("UTF-8"), 3) == null, "none set keeps nothing; a record for another page count is passed by");
        // The copies: each page under its own.
        DrawingBook.Snapshot snap = book.snapshot();
        for (int i = 0; i < book.count(); i++) check(snap.printedTemplate(i).equals(NotebookTemplate.of(after.get(i))) && snap.printed(i).guides() == NotebookTemplate.of(after.get(i)).shared(300, 400), "page " + i + "'s copy has its own guides");
        File out = Files.createTempDirectory("randomcopies").toFile();
        try {
            NotebookExport.update(out, "nb", snap.filed(), null, null, snap.options, snap.pageTemplates);
            for (int i = 0; i < book.count(); i++) {
                NotebookExport.Png png = NotebookExport.read(Files.readAllBytes(new File(new File(out, "nb"), NotebookExport.pageName(i)).toPath()));
                check(png.crc == NotebookExport.pageKey(snap.filed().get(i).crc, NotebookTemplate.of(after.get(i))), "page " + i + "'s PNG keyed by its own template");
            }
        } finally { delete(out); }
    }
    static void delete(File f) { File[] in = f.listFiles(); if (in != null) for (File x : in) delete(x); f.delete(); }
}
