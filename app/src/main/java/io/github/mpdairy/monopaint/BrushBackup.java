package io.github.mpdairy.monopaint;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Saved brushes kept for good: the library ({@link BrushLibrary}) is also written, whole, to a text file beside the
 * drawings in shared storage ({@link DrawingStorage#shared}), which outlives uninstalling or reinstalling the app. An
 * app whose preferences hold no library (a fresh install) reads it back from there, so nothing saved is lost. Only the
 * library goes to the file; the slots and their sizes stay in the preferences. A Stamp's nibs ({@link StampNib}), which
 * brush codes name, go beside it too, a file each in {@link #NIBS}, and are read back from there the same way. Pure but
 * for the files; checked on the desktop.
 */
final class BrushBackup {
    static final String SUFFIX = " brushes.txt", NAME = AppName.NAME + SUFFIX;
    /** The folder beside it that holds Stamp nibs, a file each, named for its store key. */
    static final String NIBS_SUFFIX = " stamps", NIBS = AppName.NAME + NIBS_SUFFIX;

    /** {@code store} with its library also kept in {@code file}; {@code file} null keeps {@code store} as it is. */
    static BrushLibrary.Store backed(BrushLibrary.Store store, File file) {
        if (file == null) return store;
        // A library saved before there was a file (or since it went) is written out now, not at its next change.
        String library = store.get(BrushLibrary.LIBRARY);
        if (library != null && !library.equals(read(file))) write(file, library);
        return new BrushLibrary.Store() {
            @Override public String get(String key) {
                String value = store.get(key);
                if (value != null || !kept(key)) return value;
                String kept = read(fileOf(file, key));
                if (kept != null) store.put(key, kept);
                return kept;
            }
            @Override public void put(String key, String value) {
                store.put(key, value);
                if (kept(key) && value != null) write(fileOf(file, key), value);
            }
        };
    }
    /** Whether {@code key} is kept for good: the library, or a Stamp's nib. */
    private static boolean kept(String key) { return BrushLibrary.LIBRARY.equals(key) || key.startsWith(StampNib.KEY); }
    /** Where {@code key} is kept beside the library's {@code file}: the file itself, or a nib's in {@link #NIBS}. */
    static File fileOf(File file, String key) { return BrushLibrary.LIBRARY.equals(key) ? file : new File(new File(file.getParentFile(), NIBS), key + ".txt"); }
    /** The file's text, or null if there is none or it won't read. */
    static String read(File file) {
        try { return file.isFile() ? new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8) : null; }
        catch (IOException e) { return null; }
    }
    /** Writes {@code text} to {@code file} whole or not at all: a temporary file beside it, then renamed over it. */
    static boolean write(File file, String text) {
        File folder = file.getParentFile();
        if (folder != null && !folder.isDirectory() && !folder.mkdirs()) return false;
        File temporary = new File(folder, "." + file.getName() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(temporary)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        } catch (IOException e) { temporary.delete(); return false; }
        return temporary.renameTo(file) || (file.delete() && temporary.renameTo(file));
    }

    private BrushBackup() { }
}
