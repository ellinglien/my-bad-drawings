package io.github.mpdairy.monopaint;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class BrushLibraryChecks {
    static Map<String, String> prefs = new HashMap<>();
    static BrushLibrary.Store store() {
        return new BrushLibrary.Store() {
            public String get(String key) { return prefs.get(key); }
            public void put(String key, String value) { if (value == null) prefs.remove(key); else prefs.put(key, value); }
        };
    }
    static Brush pen(long seed, String name) { ModularBrush b = ModularStyle.named("Fineliner").roll(new Random(seed)); b.name = name; return Brush.read(Brush.of(b).write()); }

    public static void main(String[] args) throws Exception {
        Assets.install(Assets.folder(Assets.FOLDER));
        library(); start(); old(); kept(); sameNames();
        System.out.println("PASS: the library starts with the first brushes, newest first, every one deletable and none brought back; saving the same brush twice does nothing; entries used or saved last come first for the recent tiles; it survives a restart and drops what it can't read or holds twice; built-ins and old Wild brushes stored before the six-part engine are written back as M codes once, names, places and stamps kept; an edited copy of a saved brush saves beside it under a name of its own; the library is kept in a file too and read back after a reinstall; once, of brushes under one name (case aside) only the newest stays, a tie the later line, their sizes and greys untouched; since then no save adds a second brush of a name");
    }
    /** Saving never writes over a saved brush, and saved brushes outlive the app's own storage. */
    static void kept() throws Exception {
        prefs = new HashMap<>();
        BrushLibrary library = new BrushLibrary(store());
        Brush original = pen(1, "brisk-bean");
        library.save(original);
        Brush edited = pen(2, "brisk-bean");
        check(library.freeName(original).equals("brisk-bean"), "A saved brush keeps its name");
        check(library.freeName(edited).equals("brisk-bean 2"), "An edited copy gets the next free number");
        library.save(pen(3, "brisk-bean 2"));
        check(library.freeName(edited).equals("brisk-bean 3") && library.freeName(pen(4, "brisk-bean 2")).equals("brisk-bean 3"), "Numbers count on, not up from the copy's own");
        check(library.freeName(pen(5, "fresh-fig")).equals("fresh-fig"), "A name no one has stays");
        String longName = "abcdefghijklmnopqrstuvwxyzabcdef";
        library.save(pen(6, longName));
        String free = library.freeName(pen(7, longName));
        check(free.length() <= Brush.NAME_MAX && free.endsWith(" 2"), "A long name is shortened to fit its number: " + free);

        java.io.File folder = java.nio.file.Files.createTempDirectory("brushes").toFile();
        java.io.File file = new java.io.File(new java.io.File(folder, "Document/Inkwash"), BrushBackup.NAME);
        prefs = new HashMap<>();
        BrushLibrary first = new BrushLibrary(BrushBackup.backed(store(), file));
        first.save(pen(1, "alpha")); first.save(pen(2, "bravo"));
        check(file.isFile() && BrushBackup.read(file).equals(prefs.get(BrushLibrary.LIBRARY)), "Each save is written to the file too");
        prefs = new HashMap<>();
        BrushLibrary reinstalled = new BrushLibrary(BrushBackup.backed(store(), file));
        reinstalled.start(java.util.Collections.singletonList(pen(9, "built-in")));
        check(reinstalled.entries().size() == 2 && reinstalled.contains(pen(1, "alpha")) && !reinstalled.contains(pen(9, "built-in")),
                "A reinstalled app reads its saved brushes back instead of starting over");
        prefs = new HashMap<>();
        prefs.put(BrushLibrary.LIBRARY, "1\t1\t" + pen(3, "charlie").write());
        file.delete();
        BrushBackup.backed(store(), file);
        check(BrushBackup.read(file) != null && BrushBackup.read(file).contains("charlie"), "A library from before the file is written out at once");
        check(BrushBackup.backed(store(), null) != null, "No file: the store as it was");

        prefs = new HashMap<>();
        BrushLibrary into = new BrushLibrary(store());
        java.io.File in = new java.io.File(folder, BrushImport.NAME);
        java.nio.file.Files.write(in.toPath(), ("# from Brush Studio\n\nnot a brush\n" + pen(11, "kiwi-free").write().split("\t", 2)[1] + "\n"
                + "zealous-shallot.M67gzlduui0_1zfmq009t0500ee_0_000_1jz_8rx0000000000000000nz0.vvrysb\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        check(BrushImport.run(into, in) == 2 && into.entries().size() == 2, "Codes in the import file join the library, lines that don't read are skipped");
        check(!in.exists() && new java.io.File(folder, BrushImport.DONE).isFile(), "The import file is renamed, so each code comes in once");
        check(BrushImport.run(into, in) == 0, "No file: nothing to add");
        java.io.File f2 = new java.io.File(folder, "Friend.INKWASH.txt");
        java.nio.file.Files.write(f2.toPath(), (pen(12, "plum-free").write().split("\t", 2)[1] + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        check(BrushImport.runAll(into, folder) == 1 && !f2.exists() && new java.io.File(folder, "Friend (added).txt").isFile(), "Any .inkwash.txt in the folder is imported and renamed to (added).txt");
        check(BrushImport.runAll(into, folder) == 0 && !BrushImport.isImport("Friend (added).txt") && !BrushImport.isImport(BrushImport.DONE), "Renamed files are not imported again");
    }

    /** The owner's "duplicated names" (2026-10-29): once, the newest of a name stays; a save never adds a second. */
    static void sameNames() {
        prefs = new HashMap<>();
        BrushLibrary.Store store = store();
        // id, stamp, then the brush: "Ink" three times (one in other case and spaces), "Wash" twice at one stamp, "Pen" once.
        prefs.put(BrushLibrary.LIBRARY, String.join("\n",
                "10\t30\t" + pen(1, "Ink").write(), "11\t50\t" + pen(2, "ink").write(), "12\t40\t" + pen(3, "INK").write(),
                "13\t20\t" + pen(4, "Wash").write(), "14\t20\t" + pen(5, "Wash").write(), "15\t10\t" + pen(6, "Pen").write()));
        prefs.put(ToolSwatches.LOOK + "kept", "3,1");
        BrushLibrary library = new BrushLibrary(store);
        check(LibraryCleanup.sameNames(library, store) == 3, "Three older namesakes go");
        List<BrushLibrary.Entry> left = library.entries();
        check(left.size() == 3 && library.contains(pen(2, "ink")) && library.contains(pen(5, "Wash")) && library.contains(pen(6, "Pen"))
                && library.find(pen(2, "ink")).id == 11 && library.find(pen(2, "ink")).used == 50, "The newest of each stays in its place, the tie the later line: " + prefs.get(BrushLibrary.LIBRARY));
        check("3,1".equals(prefs.get(ToolSwatches.LOOK + "kept")), "Sizes and greys untouched");
        library.save(pen(1, "Ink"));
        check(LibraryCleanup.sameNames(library, store) == null && library.entries().size() == 4, "Once");
        // Going forward: a save under a name another brush has (case aside) is numbered, an import's too.
        check(library.has("WASH") && library.has(" pen") && !library.has("Pens"), "A name is had case and spaces aside");
        check(library.find(pen(1, "Ink")) == null && library.find(pen(1, "Ink 2")) != null, "Saved beside ink, it is Ink 2");
        check(library.save(pen(7, "pen")) == BrushLibrary.Saved.ADDED && library.contains(pen(7, "pen 2")), "pen beside Pen: pen 2");
        check(library.save(pen(6, "Pen")) == BrushLibrary.Saved.ALREADY && library.entries().size() == 5, "The same brush again is already there, no number");
        java.util.Set<String> names = new java.util.HashSet<>();
        for (BrushLibrary.Entry e : library.entries()) check(names.add(e.brush.name().toLowerCase(java.util.Locale.ROOT)), "One brush a name: " + e.brush.name());
        // A library cleaned before the numbered rule (2026-10-29: "yes remove the numbered ones too"): "Wash", "Wash 2" and
        // "wash 3" are one name, the newest staying; a number alone ("Ink 2", no Ink) stays; it runs once.
        prefs = new HashMap<>();
        store = store();
        prefs.put(LibraryCleanup.NAMES_DONE, "1");
        prefs.put(BrushLibrary.LIBRARY, String.join("\n",
                "20\t30\t" + pen(1, "Wash").write(), "21\t60\t" + pen(2, "Wash 2").write(), "22\t40\t" + pen(3, "wash 3").write(),
                "23\t10\t" + pen(4, "Ink 2").write(), "24\t10\t" + pen(5, "Mistakes Volume Two").write()));
        library = new BrushLibrary(store);
        check(LibraryCleanup.sameNames(library, store) == 2 && library.entries().size() == 3 && library.contains(pen(2, "Wash 2"))
                && library.contains(pen(4, "Ink 2")) && library.contains(pen(5, "Mistakes Volume Two")), "The numbered namesakes go, the newest staying: " + prefs.get(BrushLibrary.LIBRARY));
        check(LibraryCleanup.sameNames(library, store) == null, "Numbered once");
        // A first launch's library (the first brushes) loses nothing.
        prefs = new HashMap<>();
        ToolSlots fresh = new ToolSlots(store(), new java.util.Random(1));
        check(prefs.get(LibraryCleanup.NAMES_DONE) != null && fresh.library.entries().size() == ToolSlots.COUNT, "A first launch's library is left whole");
    }

    static void library() {
        prefs = new HashMap<>();
        BrushLibrary library = new BrushLibrary(store());
        check(library.entries().isEmpty() && library.recent(4).isEmpty(), "A library never started is empty");
        Brush a = pen(1, "alpha"), b = pen(2, "bravo"), c = pen(3, "charlie");
        check(library.save(a) == BrushLibrary.Saved.ADDED && library.save(b) == BrushLibrary.Saved.ADDED && library.save(c) == BrushLibrary.Saved.ADDED, "Saving adds");
        List<BrushLibrary.Entry> saved = library.entries();
        check(saved.size() == 3 && saved.get(0).brush.name().equals("charlie") && saved.get(2).brush.name().equals("alpha"), "Newest first");
        check(library.save(pen(1, "alpha")) == BrushLibrary.Saved.ALREADY && library.entries().size() == 3, "The same brush saved again is already there");
        check(library.save(pen(1, "another name")) == BrushLibrary.Saved.ADDED, "Another name is another brush");
        check(library.contains(b) && !library.contains(pen(9, "nine")) && library.find(b).brush.sameAs(b), "Finding a brush");

        library.used(b);
        List<BrushLibrary.Entry> recent = library.recent(4);
        check(recent.get(0).brush.sameAs(b) && recent.get(1).brush.name().equals("another name") && recent.get(2).brush.name().equals("alpha"),
                "Recently used: the one just used, then the newest saved, then alpha (saved again just before)");
        library.used(pen(9, "nine"));
        check(library.recent(1).get(0).brush.sameAs(b), "A brush not in the library changes nothing");

        long id = library.find(c).id;
        check(library.delete(id) && !library.contains(c) && library.entries().size() == 3, "Deleting a brush");
        check(!library.delete(id), "Deleting it twice does nothing");

        BrushLibrary again = new BrushLibrary(store());
        check(again.entries().size() == 3 && again.recent(1).get(0).brush.sameAs(b), "The library survives a restart");
        for (int i = 0; i < 50; i++) again.save(pen(100 + i, "many " + i));
        check(again.entries().size() == 53, "No limit");

        prefs.put("brush_library", "junk\n77\t5\tnot a brush\n78\t6\tx\tbad-code\n79\t7\t@PENCIL\nx\ty\t" + pen(4, "delta").write());
        check(new BrushLibrary(store()).entries().isEmpty(), "Unreadable entries are dropped");
        String pen = pen(5, "echo").write();
        prefs.put("brush_library", "50\t3\t" + pen + "\n51\t7\t" + pen + "\n52\t2\t" + pen(5, "foxtrot").write());
        BrushLibrary doubled = new BrushLibrary(store());
        check(doubled.entries().size() == 2 && doubled.recent(1).get(0).brush.name().equals("echo") && doubled.find(pen(5, "echo")).id == 50,
                "A brush stored twice is listed once, keeping its first place and its latest use");
        doubled.delete(50);
        check(!doubled.contains(pen(5, "echo")) && doubled.contains(pen(5, "foxtrot")), "Deleting it deletes it whole");
    }
    /** A first launch starts the library with the first brushes, the first listed newest; a library already there isn't touched. */
    static void start() {
        prefs = new HashMap<>();
        BrushLibrary library = new BrushLibrary(store());
        List<Brush> first = Arrays.asList(pen(1, "one"), pen(2, "two"), pen(3, "three"));
        library.start(first);
        List<BrushLibrary.Entry> entries = library.entries();
        check(entries.size() == 3 && entries.get(0).brush.sameAs(first.get(0)) && entries.get(2).brush.sameAs(first.get(2)), "The first brushes, in order, the first newest");
        check(library.recent(1).get(0).brush.sameAs(first.get(0)), "and most recently used");
        String before = prefs.toString();
        library.start(first);
        check(prefs.toString().equals(before), "Starting again changes nothing");
        library.delete(entries.get(0).id);
        library.start(first);
        check(library.entries().size() == 2 && !library.contains(first.get(0)), "A deleted first brush isn't brought back");
        prefs.put("brush_library", "");
        library.start(first);
        check(library.entries().isEmpty(), "nor is an emptied library refilled");
    }
    /** A Stage-1/2 library: the built-ins (as they were written), old Wild brushes and six-part brushes, converted once. */
    static void old() {
        prefs = new HashMap<>();
        String m = pen(6, "golf").write();
        prefs.put("brush_library", "1\t0\t@INK\n2\t0\t@FUDE\n3\t12\t@WASH\n4\t0\t@WATERCOLOR\n9\t9\tmy pen\tdapper-fig.I610gavij3f7q16o0000006m00.e1\n"
                + "10\t11\tbouncy moose\tbouncy-moose.P610hh46000000000000wp.25\n11\t10\t" + m + "\n12\t13\tjunk\tdapper-fig.I610gavij3f7q16o0000006m0.e1");
        prefs.put("brush_stamp", "13");
        BrushLibrary library = new BrushLibrary(store());
        library.start(Arrays.asList(pen(1, "never")));
        String text = prefs.get("brush_library");
        check(!text.contains("@") && !text.contains(".I") && !text.contains(".P") && !text.contains("junk"), "Written back as M codes, the unreadable line dropped: " + text);
        List<BrushLibrary.Entry> entries = library.entries();
        check(entries.size() == 7 && !library.contains(pen(1, "never")), "Seven brushes, no first-launch brushes added: " + entries.size());
        check(entries.get(0).brush.sameAs(Brush.read(m)) && entries.get(0).id == 11 && entries.get(1).brush.name().equals("bouncy moose") && entries.get(1).id == 10
                && entries.get(2).brush.name().equals("my pen") && entries.get(2).brush.inks(), "Places kept, newest first; names kept");
        check(entries.get(3).brush.name().equals("Watercolour") && entries.get(4).brush.name().equals("Ink wash") && entries.get(5).brush.name().equals("Brush pen")
                && entries.get(6).brush.name().equals("Fineliner") && entries.get(6).id == 1, "The built-ins, under their new names, in their old places");
        check(library.recent(2).get(0).brush.name().equals("Ink wash") && library.recent(2).get(1).brush.name().equals("bouncy moose"), "Stamps kept: Wash was used last");
        check(library.save(pen(7, "hotel")) == BrushLibrary.Saved.ADDED && library.find(pen(7, "hotel")).id == 14, "New ids follow the stored stamp");
        String after = prefs.get("brush_library");
        library.start(Arrays.asList(pen(1, "never")));
        check(prefs.get("brush_library").equals(after), "Converted once: a second start writes nothing");
    }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
