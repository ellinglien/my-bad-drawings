package io.github.mpdairy.monopaint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Every brush that can go in a slot: saved brushes, newest first, without limit, starting with the first launch's
 * ({@link #start}). Each entry is stamped when saved or put in a slot, so the four most recently used fill a slot's
 * panel. Kept between launches in a tiny key/value store; read each time, so two screens sharing the store always
 * agree. Entries stored before the six-part engine (the built-ins, old Wild brushes) read converted, and are written
 * back converted once ({@link #start}). Pure, checked on the desktop.
 */
final class BrushLibrary {
    /** Where the library is kept: SharedPreferences on the device, a map in the checks. A null value removes the key. */
    interface Store { String get(String key); void put(String key, String value); }
    enum Saved { ADDED, ALREADY }
    /** A library brush: its place ({@link #id}s rise with each save), when it was last saved or used, and the brush. */
    static final class Entry {
        final long id, used;
        final Brush brush;
        Entry(long id, long used, Brush brush) { this.id = id; this.used = used; this.brush = brush; }
    }

    /** The entries, one a line: id, stamp and the stored brush, tab-separated. The last id or stamp given out. */
    static final String LIBRARY = "brush_library";
    private static final String STAMP = "brush_stamp";
    private final Store store;

    BrushLibrary(Store store) { this.store = store; }

    /**
     * At every launch: a store with no library yet starts with {@code first}, the first listed newest; one holding
     * entries stored before the six-part engine is written back with them converted (names, places and stamps kept).
     */
    void start(List<Brush> first) {
        String text = store.get(LIBRARY);
        if (text == null) {
            for (int i = first.size() - 1; i >= 0; i--) save(first.get(i));
            return;
        }
        for (String line : text.split("\n")) {
            String[] parts = line.split("\t", 3);
            if (parts.length == 3 && LegacyBrushes.read(parts[2]) != null) { write(read()); return; }
        }
    }
    /** Every brush, newest first. */
    List<Entry> entries() { return newestFirst(read()); }
    /** The {@code count} entries saved or used last, latest first. */
    List<Entry> recent(int count) {
        List<Entry> all = read();
        Collections.sort(all, (a, b) -> a.used != b.used ? Long.compare(b.used, a.used) : Long.compare(b.id, a.id));
        return new ArrayList<>(all.subList(0, Math.min(count, all.size())));
    }
    /** The entry holding {@code brush}, or null. */
    Entry find(Brush brush) { return find(read(), brush); }
    boolean contains(Brush brush) { return find(brush) != null; }
    /**
     * The name {@code brush} is saved under: its own, unless another brush here has it already (an edited copy of a
     * saved brush keeps its name until saved), then its name with the first free number after it ("brisk-bean 2").
     */
    String freeName(Brush brush) {
        List<Entry> all = read();
        String name = brush.name();
        if (!taken(all, name, brush)) return name;
        String base = name.replaceAll(" \\d+$", "");
        for (int n = 2; ; n++) {
            String suffix = " " + n;
            String candidate = Brush.clean(base.substring(0, Math.min(base.length(), Brush.NAME_MAX - suffix.length())).trim() + suffix);
            if (!taken(all, candidate, brush)) return candidate;
        }
    }
    private static boolean taken(List<Entry> all, String name, Brush brush) {
        for (Entry e : all) if (sameName(e.brush.name(), name) && !e.brush.sameAs(brush)) return true;
        return false;
    }
    /** Whether a brush here is named {@code name}, case aside: a suggested name passes it by ({@link BrushNames#name}). */
    boolean has(String name) { return taken(read(), name, null); }
    /** One name to the library: the same letters, case and spaces round them aside ("Polite smear", "polite Smear "). */
    static boolean sameName(String a, String b) { return nameKey(a).equals(nameKey(b)); }
    private static String nameKey(String name) { return name.trim().toLowerCase(java.util.Locale.ROOT); }
    /** {@link #nameKey} less a number at its end ("Wash 2" is Wash's). */
    private static String baseKey(String name) { return nameKey(name).replaceFirst("\\s+\\d+$", ""); }
    /**
     * Adds {@code brush}, newest, under a name no other brush here has ({@link #freeName}: an imported code's, a slot's);
     * one already here (a built-in, or the same name and settings) is stamped as used instead.
     */
    Saved save(Brush brush) {
        List<Entry> all = read();
        long stamp = next();
        Entry found = find(all, brush);
        if (found != null) { all.set(all.indexOf(found), new Entry(found.id, stamp, found.brush)); write(all); return Saved.ALREADY; }
        String free = freeName(brush);
        if (!free.equals(brush.name())) brush = Brush.read(free + "\t" + ModularCode.encode(brush.parts));
        all.add(new Entry(stamp, stamp, brush));
        write(all);
        return Saved.ADDED;
    }
    /**
     * Brush Lab's Save over a brush opened from here: entry {@code id} now holds {@code brush}, in its place (its id),
     * stamped as used now; the other entries as they were. False, changing nothing, for an unknown id.
     */
    boolean update(long id, Brush brush) {
        List<Entry> all = read();
        for (int i = 0; i < all.size(); i++)
            if (all.get(i).id == id) { all.set(i, new Entry(id, next(), brush)); write(all); return true; }
        return false;
    }
    /** {@code brush} was put in a slot: if it is in the library, it is now the most recently used. */
    void used(Brush brush) {
        List<Entry> all = read();
        Entry found = find(all, brush);
        if (found == null) return;
        all.set(all.indexOf(found), new Entry(found.id, next(), found.brush));
        write(all);
    }
    /** Deletes entry {@code id}; an unknown id changes nothing. */
    boolean delete(long id) {
        List<Entry> all = read();
        for (Entry e : all) if (e.id == id) { all.remove(e); write(all); return true; }
        return false;
    }
    /**
     * Every entry at once, the library written once: {@code edit} gives each brush back as it is, or another (a rename,
     * in the same place and stamp), or null to delete it ({@link LibraryCleanup}).
     */
    void edit(java.util.function.UnaryOperator<Brush> edit) {
        List<Entry> all = new ArrayList<>();
        for (Entry e : read()) {
            Brush b = edit.apply(e.brush);
            if (b != null) all.add(new Entry(e.id, e.used, b));
        }
        write(all);
    }

    /**
     * Of the brushes under one name ({@link #sameName}), only the newest stays: the latest stamp, a tie the later line
     * ({@link LibraryCleanup#sameNames}). Their own sizes and greys are left as they are. How many went; the library is
     * written only when one did.
     */
    int keepNewestNames() { return keepNewestNames(false); }
    /** As {@link #keepNewestNames()}, a name's numbered ones ("Wash 2", "wash 3") with it when {@code numbered}. */
    int keepNewestNames(boolean numbered) {
        List<Entry> all = read();
        Map<String, Entry> newest = new HashMap<>();
        for (Entry e : all) {
            String key = numbered ? baseKey(e.brush.name()) : nameKey(e.brush.name());
            Entry was = newest.get(key);
            if (was == null || e.used >= was.used) newest.put(key, e);
        }
        if (newest.size() == all.size()) return 0;
        List<Entry> kept = new ArrayList<>();
        for (Entry e : all) if (newest.get(numbered ? baseKey(e.brush.name()) : nameKey(e.brush.name())) == e) kept.add(e);
        write(kept);
        return all.size() - kept.size();
    }

    private static List<Entry> newestFirst(List<Entry> all) {
        List<Entry> sorted = new ArrayList<>(all);
        Collections.sort(sorted, (a, b) -> Long.compare(b.id, a.id));
        return sorted;
    }
    private static Entry find(List<Entry> all, Brush brush) {
        for (Entry e : all) if (e.brush.sameAs(brush)) return e;
        return null;
    }
    /**
     * The stored entries; lines that don't read are dropped, a brush stored twice (the same name and settings) is one
     * entry, in its first place, used when it was last used.
     */
    private List<Entry> read() {
        List<Entry> all = new ArrayList<>();
        Map<String, Integer> at = new HashMap<>();
        String text = store.get(LIBRARY);
        if (text != null) for (String line : text.split("\n")) {
            String[] parts = line.split("\t", 3);
            if (parts.length < 3) continue;
            Brush brush = Brush.read(parts[2]);
            if (brush == null) continue;
            long id, used;
            try { id = Long.parseLong(parts[0]); used = Long.parseLong(parts[1]); } catch (NumberFormatException e) { continue; }
            Integer twice = at.get(brush.identity());
            if (twice == null) { at.put(brush.identity(), all.size()); all.add(new Entry(id, used, brush)); }
            else if (used > all.get(twice).used) all.set(twice, new Entry(all.get(twice).id, used, all.get(twice).brush));
        }
        return all;
    }
    private void write(List<Entry> all) {
        StringBuilder text = new StringBuilder();
        for (Entry e : all) {
            if (text.length() > 0) text.append('\n');
            text.append(e.id).append('\t').append(e.used).append('\t').append(e.brush.write());
        }
        store.put(LIBRARY, text.toString());
    }
    /** The next id or stamp, after the old built-ins' 1 to 4 (which stores from before the six-part engine hold). */
    private long next() {
        long last = 4;
        try { String stored = store.get(STAMP); if (stored != null) last = Math.max(last, Long.parseLong(stored)); } catch (NumberFormatException ignored) { }
        store.put(STAMP, Long.toString(last + 1));
        return last + 1;
    }
}
