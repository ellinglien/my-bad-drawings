package io.github.mpdairy.monopaint;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Brushes brought in from outside the tablet: share codes (Brush Studio's, the web lab's, a friend's) written one to a
 * line in {@link #NAME} or any {@code *.inkwash.txt} (the app's name, lower case) beside the drawings are added to the library at launch and each time the drawing screen comes back, then the file is renamed
 * {@link #DONE}, so each code comes in once. Lines that don't read as a brush, and blank or {@code #} lines, are
 * skipped. Pure but for the file; checked on the desktop.
 */
final class BrushImport {
    static final String SUFFIX = " import.txt", DONE_SUFFIX = " import (added).txt", NAME = AppName.NAME + SUFFIX, DONE = AppName.NAME + DONE_SUFFIX;

    /** The brush a share code describes: an M code, or an old I or P code as its six parts; null if it doesn't read. */
    static ModularBrush decode(String code) {
        ModularBrush m = ModularCode.decode(code);
        if (m == null) { WildBrush old = BrushCode.decode(code); m = old != null ? LegacyBrushes.of(old) : null; }
        return m;
    }

    /**
     * True for "Inkwash import.txt" and any "*.inkwash.txt" (case aside), and the same under a name the app had before
     * ({@link AppName#FORMER}); the renamed "(added).txt" files match none.
     */
    static boolean isImport(String name) { return importSuffix(name, names()) != null; }

    /** The ".inkwash.txt" (one of {@code apps}' names, as written) that {@code name} ends with, or "" for {@link #NAME}; null if it's no import file. */
    static String importSuffix(String name, String[] apps) {
        String n = name.toLowerCase(java.util.Locale.ROOT);
        for (String app : apps) {
            String a = app.toLowerCase(java.util.Locale.ROOT);
            if (n.equals(a + SUFFIX)) return "";
            if (n.endsWith("." + a + ".txt")) return name.substring(name.length() - a.length() - 5);
        }
        return null;
    }
    private static String[] names() {
        String[] all = new String[AppName.FORMER.length + 1];
        all[0] = AppName.NAME;
        System.arraycopy(AppName.FORMER, 0, all, 1, AppName.FORMER.length);
        return all;
    }

    /** Imports every import file in {@code folder}; how many brushes were added. */
    static int runAll(BrushLibrary library, File folder) {
        File[] files = folder == null ? null : folder.listFiles();
        if (files == null) return 0;
        java.util.Arrays.sort(files);
        int added = 0;
        for (File f : files) if (f.isFile() && isImport(f.getName())) added += run(library, f);
        return added;
    }

    /** Adds every brush {@code file} holds to {@code library} and renames the file; how many it added. */
    static int run(BrushLibrary library, File file) {
        if (file == null || !file.isFile()) return 0;
        int added = 0;
        try {
            for (String line : new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).split("\n")) {
                String code = line.trim();
                if (code.isEmpty() || code.startsWith("#")) continue;
                ModularBrush m = decode(code);
                if (m != null && library.save(Brush.of(m)) == BrushLibrary.Saved.ADDED) added++;
            }
        } catch (IOException e) { return added; }
        String n = file.getName();
        String suffix = importSuffix(n, names());
        File done = new File(file.getParentFile(), suffix == null || suffix.isEmpty() ? DONE
                : n.substring(0, n.length() - suffix.length()) + " (added).txt");
        if (!done.exists() || done.delete()) file.renameTo(done);
        return added;
    }

    private BrushImport() { }
}
