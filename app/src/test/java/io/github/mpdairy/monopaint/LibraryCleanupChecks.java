package io.github.mpdairy.monopaint;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/** The owner's library cleanup ({@link LibraryCleanup}): a rename with its own size and grey, a removal, a brush no row names, once. */
public final class LibraryCleanupChecks {
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
    static Brush row(int i) { return Brush.read(LibraryCleanup.ROWS[i][0] + "\t" + LibraryCleanup.ROWS[i][1]); }
    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        Map<String, String> prefs = new HashMap<>();
        BrushLibrary.Store store = new BrushLibrary.Store() {
            public String get(String key) { return prefs.get(key); }
            public void put(String key, String value) { if (value == null) prefs.remove(key); else prefs.put(key, value); }
        };
        ToolSlots slots = new ToolSlots(store, new Random(1));
        check(store.get(LibraryCleanup.DONE) != null && slots.library.entries().size() == ToolSlots.COUNT, "a first launch's library, the first brushes, is left as it is");
        // What the first brushes alone give (one a row removes, one already under a row's new name gets its look).
        store.put(LibraryCleanup.DONE, null);
        int[] base = LibraryCleanup.run(slots.library, slots.swatches, store);
        store.put(LibraryCleanup.DONE, null);
        Brush fineliner = row(0), noodly = row(4), other = ToolSlots.first(0);
        check(fineliner != null && noodly != null && LibraryCleanup.ROWS[0][3].equals("Classic fineliner") && LibraryCleanup.ROWS[4][2].equals("remove"), "the rows read");
        slots.library.save(fineliner);
        slots.library.save(noodly);
        long id = slots.library.find(fineliner).id;
        int before = slots.library.entries().size();
        int[] counts = LibraryCleanup.run(slots.library, slots.swatches, store);
        check(counts[0] == base[0] + 1 && counts[1] == base[1] + 1 && counts[2] == base[2] + 1, "one renamed with a look, one removed: " + java.util.Arrays.toString(counts));
        check(!slots.library.contains(fineliner) && !slots.library.contains(noodly) && slots.library.contains(other), "rename, remove, no match kept");
        check(slots.library.entries().size() == before - 1, "one entry fewer");
        BrushLibrary.Entry renamed = null;
        for (BrushLibrary.Entry e : slots.library.entries()) if (e.brush.name().equals("Classic fineliner")) renamed = e;
        check(renamed != null && renamed.id == id && renamed.brush.parts.key().equals(fineliner.parts.key())
                && ModularCode.encode(renamed.brush.parts).startsWith("classic-fineliner.M"), "renamed in place, same settings, the slug follows");
        // A slot it is put in takes its own size and grey; one without keeps the slot's.
        String key = ToolSlots.key(3);
        check(slots.use(3, renamed.brush), "picked");
        check(slots.swatches.look(key, renamed.brush).px == 3 && slots.swatches.look(key, renamed.brush).darkness == 1, "its own 3 px, black");
        slots.swatches.useSize(key, 50);
        slots.swatches.useGrey(key, .4);
        check(slots.use(3, other), "another picked");
        check(slots.swatches.look(key, other).px == 50 && slots.swatches.look(key, other).darkness == .4, "a brush without its own keeps the slot's");
        // Once: a brush the table removes, saved again, stays.
        slots.library.save(noodly);
        check(LibraryCleanup.run(slots.library, slots.swatches, store) == null && slots.library.contains(noodly), "runs once");
        // A reinstall's library, already cleaned, gets its looks back.
        prefs.remove(ToolSwatches.LOOK + ModularCode.encode(renamed.brush.parts));
        store.put(LibraryCleanup.DONE, null);
        counts = LibraryCleanup.run(slots.library, slots.swatches, store);
        check(counts[2] == base[2] + 1 && store.get(ToolSwatches.LOOK + ModularCode.encode(renamed.brush.parts)).equals("3,1"), "a cleaned library gets its looks again");
        System.out.println("PASS: library cleanup");
    }
}
