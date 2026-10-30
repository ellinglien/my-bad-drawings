package io.github.mpdairy.monopaint;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;

/** Relative paths within a drawing library folder (see {@link DrawingStorage}). */
final class DrawingFiles {
    static final String EXTENSION = ".mpaint";
    /** Drawings were .tsm files before 0.96; {@link #upgradeLegacyNames} renames them. */
    static final String LEGACY_EXTENSION = ".tsm";
    /** What {@link #validName} accepts, as shown wherever a name is refused. */
    static final String NAME_RULES = nameRules(DrawingFiles.NAME_MOST);
    /** The most letters a file name holds. */
    static final int NAME_MOST = 64;
    /** {@link #NAME_RULES} for a name that may be {@code most} letters long (fewer where the app adds to what is typed). */
    static String nameRules(int most) { return "Use 1–" + most + " letters, numbers, spaces, - or _; _recovery is reserved"; }
    private final File root;

    DrawingFiles(File root) { this.root = root; }

    static boolean validName(String name) {
        return name != null && name.length() > 0 && name.length() <= NAME_MOST
                && name.equals(name.trim()) && name.matches("[\\p{L}\\p{N} _-]+")
                && !name.equals("_recovery");
    }

    static boolean validPath(String path) {
        if (path == null || path.isEmpty() || path.length() > 1024) return false;
        for (String part : path.split("/", -1)) if (!validName(part)) return false;
        return true;
    }

    static String child(String folder, String name) { return folder.isEmpty() ? name : folder + "/" + name; }
    static String parent(String path) { int slash = path.lastIndexOf('/'); return slash < 0 ? "" : path.substring(0, slash); }
    static String name(String path) { return path.substring(path.lastIndexOf('/') + 1); }
    /** The ISO date a file name starts with, then a space ("2026-10-18 brisk-bean notebook") or _ (a title's, {@link DrawingNames#titled}), or null. */
    static java.time.LocalDate isoDate(String name) {
        if (!name.matches("\\d{4}-\\d{2}-\\d{2}[ _].+")) return null;
        try { return java.time.LocalDate.parse(name.substring(0, 10)); } catch (java.time.DateTimeException e) { return null; }
    }

    private File contained(File file) throws IOException {
        if (!file.getCanonicalPath().startsWith(root.getCanonicalPath() + File.separator))
            throw new IOException("Invalid painting location");
        return file;
    }

    File folder(String path) throws IOException {
        if (!root.isDirectory() && !root.mkdirs()) throw new IOException("Cannot create painting folder");
        if (path.isEmpty()) return root;
        if (!validPath(path)) throw new IOException("Invalid folder name");
        File folder = contained(new File(root, path));
        if (!folder.isDirectory()) throw new IOException("Folder no longer exists");
        return folder;
    }

    File drawing(String path) throws IOException {
        if (!validPath(path)) throw new IOException("Invalid painting name");
        return contained(new File(folder(parent(path)), name(path) + EXTENSION));
    }

    /** Where the recovery file lived before 0.97; {@link DocumentStore} now keeps it in private storage. */
    File recovery() throws IOException { return new File(folder(""), "_recovery" + EXTENSION); }

    void createFolder(String parent, String name) throws IOException {
        if (!validName(name) || !validPath(child(parent, name))) throw new IOException("Invalid folder name");
        if (!contained(new File(folder(parent), name)).mkdir())
            throw new IOException("Folder already exists or could not be created");
    }

    /**
     * Renames a drawing within its folder and returns its new path, following {@code AtomicFile}:
     * a {@code .bak} exists only after an interrupted write and is then the last good copy, so it
     * becomes the new base file and the stale base and {@code .new} are dropped. A drawing not
     * saved yet has no file; the name is still checked so its first save cannot replace another.
     */
    String rename(String path, String name) throws IOException {
        if (!validName(name)) throw new IOException(NAME_RULES);
        String target = child(parent(path), name);
        if (target.equals(path)) return path;
        File from = drawing(path), to = drawing(target), backup = sibling(from, ".bak");
        if (!target.equalsIgnoreCase(path) && exists(to)) throw new IOException("A drawing with that name already exists");
        if (backup.exists()) {
            if (!backup.renameTo(to)) throw new IOException("Could not rename " + name(path));
            // A case-only rename on a case-insensitive disk makes the old base the new file.
            if (from.exists() && !Files.isSameFile(from.toPath(), to.toPath())) from.delete();
            sibling(from, ".new").delete();
        } else if (exists(from)) {
            if (!from.renameTo(to)) throw new IOException("Could not rename " + name(path));
            sibling(from, ".new").delete();
        }
        return target;
    }

    /** Deletes a drawing and any atomic-save backup or partial write; a drawing never saved is already gone. */
    void delete(String path) throws IOException {
        File file = drawing(path);
        File[] parts = {file, sibling(file, ".bak"), sibling(file, ".new")};
        for (File part : parts) part.delete();
        for (File part : parts)
            if (part.exists()) throw new IOException("Could not delete " + name(path));
    }

    private static File sibling(File file, String suffix) { return new File(file.getPath() + suffix); }
    /** When drawing {@code path} was last written, ms (0: never). */
    long modified(String path) throws IOException { return modified(drawing(path)); }
    /** When a drawing was last written, counting an interrupted write's backup. */
    private static long modified(File file) { return Math.max(file.lastModified(), sibling(file, ".bak").lastModified()); }

    Entry[] list(String path) throws IOException {
        File[] files = folder(path).listFiles();
        if (files == null) throw new IOException("Could not read painting folder");
        ArrayList<Entry> entries = new ArrayList<>();
        for (File file : files) {
            String name = file.getName();
            if (file.isDirectory() && validName(name)) {
                contained(file);
                entries.add(new Entry(name, true, 0));
            } else if (file.isFile() && name.endsWith(EXTENSION) && !name.equals("_recovery" + EXTENSION)) {
                String title = name.substring(0, name.length() - EXTENSION.length());
                if (validName(title)) { contained(file); entries.add(new Entry(title, false, modified(file))); }
            }
        }
        // Folders first, by name; then drawings, the last changed first (the owner's "most recent at the top").
        entries.sort(Comparator.comparing((Entry entry) -> !entry.folder)
                .thenComparing(entry -> -entry.modified)
                .thenComparing(entry -> entry.name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(entry -> entry.name));
        return entries.toArray(new Entry[0]);
    }

    /** The library's folder. */
    File root() { return root; }
    /** Where the migration to notebooks keeps the old drawings it gathered (NotebookMigration): not a folder of notebooks. */
    static final String ORIGINALS = "Originals";

    /**
     * Every notebook in the library and its folders, Originals left out (the migration's, NotebookMigration): each an
     * {@link Entry} named by its path, the last changed first.
     */
    java.util.List<Entry> notebooks() throws IOException {
        ArrayList<Entry> all = new ArrayList<>();
        if (root.isDirectory()) notebooks("", all);
        all.sort(Comparator.comparing((Entry entry) -> -entry.modified).thenComparing(entry -> entry.name, String.CASE_INSENSITIVE_ORDER));
        return all;
    }
    private void notebooks(String path, ArrayList<Entry> all) throws IOException {
        for (Entry entry : list(path)) {
            String child = child(path, entry.name);
            if (!entry.folder) all.add(new Entry(child, false, entry.modified));
            else if (!(path.isEmpty() && entry.name.equalsIgnoreCase(ORIGINALS))) notebooks(child, all);
        }
    }

    /** Named drawings in the whole library, not counting the recovery file. */
    int drawingCount() throws IOException { return root.isDirectory() ? drawingCount("") : 0; }
    private int drawingCount(String path) throws IOException {
        int count = 0;
        for (Entry entry : list(path)) count += entry.folder ? drawingCount(child(path, entry.name)) : 1;
        return count;
    }

    /**
     * Renames .tsm drawings, their atomic-save backups and the recovery file to .mpaint in place.
     * Nothing is copied or deleted: a drawing whose new name is taken gets an unused name, and
     * an older recovery file beside a current one is left as it is.
     */
    void upgradeLegacyNames() throws IOException { if (root.isDirectory()) upgradeLegacyNames(""); }
    private void upgradeLegacyNames(String path) throws IOException {
        File folder = folder(path);
        File[] files = folder.listFiles();
        if (files == null) throw new IOException("Could not read painting folder");
        java.util.LinkedHashSet<String> legacy = new java.util.LinkedHashSet<>();
        for (File file : files) {
            String name = file.getName();
            if (file.isDirectory() && validName(name)) upgradeLegacyNames(child(path, name));
            else if (name.endsWith(LEGACY_EXTENSION) || name.endsWith(LEGACY_EXTENSION + ".bak"))
                legacy.add(name.substring(0, name.lastIndexOf(LEGACY_EXTENSION)));
        }
        for (String name : legacy) {
            boolean recovery = path.isEmpty() && name.equals("_recovery");
            if (!recovery && !validName(name)) continue;
            File destination = recovery ? recovery() : drawing(child(path, name));
            if (exists(destination)) {
                if (recovery) continue;
                destination = drawing(unusedName(path, name));
            }
            rename(new File(folder, name + LEGACY_EXTENSION + ".bak"), new File(destination.getPath() + ".bak"));
            rename(new File(folder, name + LEGACY_EXTENSION), destination);
        }
    }
    private static void rename(File from, File to) throws IOException {
        if (from.exists() && !from.renameTo(to)) throw new IOException("Could not rename " + from.getName());
    }

    boolean sameLibrary(DrawingFiles other) throws IOException {
        return root.getCanonicalPath().equals(other.root.getCanonicalPath());
    }

    /** A drawing name in {@code folder} that nothing uses yet: {@code base}, then "base 2", "base 3"… */
    String unusedName(String folder, String base) throws IOException {
        for (int n = 1; ; n++) {
            String suffix = n == 1 ? "" : " " + n;
            String path = child(folder, base.substring(0, Math.min(base.length(), 64 - suffix.length())).trim() + suffix);
            if (!exists(drawing(path))) return path;
        }
    }

    /** Whether a drawing file is present, counting an atomic save's backup left by an interrupted write. */
    static boolean exists(File file) {
        return file.exists() || sibling(file, ".bak").exists();
    }

    static final class Entry {
        final String name;
        final boolean folder;
        /** A drawing's last write, ms; 0 for a folder. */
        final long modified;
        Entry(String name, boolean folder, long modified) { this.name = name; this.folder = folder; this.modified = modified; }
    }
}
