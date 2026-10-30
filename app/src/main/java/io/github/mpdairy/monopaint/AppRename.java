package io.github.mpdairy.monopaint;

import java.io.File;

/**
 * Carries the owner's files over when the app's name changes ({@link AppName}): each former name's folders
 * (Document/old, EXPORT/old, Pictures/old) are moved into the current name's, and the editable files named for the app
 * ("Old brushes.txt", "Old prompts.txt", "Old import.txt", "Old stamps") take the current name. Safe by construction:
 * only renames, never a copy-then-delete and never an overwrite (on a clash both are kept, the incoming one as
 * "name (2).ext"); a folder is removed only once empty; whatever won't move stays where it was, and running again
 * finishes the job (idempotent). Nothing happens while {@link AppName#FORMER} is empty. The device side
 * ({@link DrawingStorage#carryOver}) runs it once, recorded in the preferences. Pure; checked on the desktop.
 */
final class AppRename {
    /** The files beside the drawings that are named for the app, after the name (a folder last). */
    static final String[] OWN = {BrushBackup.SUFFIX, DailyPrompt.SUFFIX, BrushImport.SUFFIX, BrushImport.DONE_SUFFIX, BrushBackup.NIBS_SUFFIX, ToolBackup.SUFFIX};

    /** {@code folder} (a library folder named {@code name}, maybe with a suffix: "Inkwash bench") under {@code former}. */
    static String formerFolder(String folder, String name, String former) {
        return folder.startsWith(name) ? former + folder.substring(name.length()) : null;
    }

    /**
     * Carries every former name's {@code folder} under each of {@code parents} into the current one, then renames the
     * app's own files in {@code drawings}; true if nothing was left behind.
     */
    static boolean carryOver(File[] parents, File drawings, String folder, String name, String[] former) {
        boolean clean = true;
        for (String old : former) {
            String from = formerFolder(folder, name, old);
            if (from == null || from.equals(folder)) continue;
            for (File parent : parents) if (parent != null) clean &= merge(new File(parent, from), new File(parent, folder));
        }
        if (drawings != null) clean &= adopt(drawings, name, former);
        return clean;
    }

    /**
     * Moves everything in folder {@code from} into folder {@code to}: the whole folder at once when {@code to} isn't
     * there, else entry by entry, merging folders and keeping both on a name clash; removes {@code from} once empty.
     * True if {@code from} is gone (or never was).
     */
    static boolean merge(File from, File to) {
        if (!from.exists()) return true;
        if (!from.isDirectory()) return false;
        // Only the case differs ("Inkwash" to "InkWash"): on a case-blind file system both are one folder, so go by a
        // temporary name rather than merge a folder into itself.
        if (from.getName().equalsIgnoreCase(to.getName()) && same(from.getParentFile(), to.getParentFile())) {
            File step = free(from.getParentFile(), from.getName() + ".renaming");
            if (!from.renameTo(step)) return false;
            if (step.renameTo(to) || merge(step, to)) return true;
            step.renameTo(from); // what didn't move goes back under the former name, to be tried again
            return false;
        }
        File parent = to.getParentFile();
        if (!to.exists() && (parent == null || parent.isDirectory() || parent.mkdirs()) && from.renameTo(to)) return true;
        if (!to.isDirectory()) return false; // a file in the folder's place: leave everything as it is
        File[] entries = from.listFiles();
        if (entries == null) return false;
        java.util.Arrays.sort(entries);
        boolean all = true;
        for (File entry : entries) {
            File target = new File(to, entry.getName());
            if (entry.isDirectory() && target.isDirectory()) all &= merge(entry, target);
            else all &= entry.renameTo(target.exists() ? free(to, entry.getName()) : target);
        }
        return all && from.delete(); // a folder deletes only when empty
    }

    /** Renames the app's own files in {@code folder} from each former name to {@code name}'s, where that one isn't there yet. */
    static boolean adopt(File folder, String name, String[] former) {
        boolean clean = true;
        for (String old : former) {
            if (old.equals(name)) continue;
            for (String suffix : OWN) {
                File from = new File(folder, old + suffix), to = new File(folder, name + suffix);
                if (!from.exists()) continue;
                if (from.isDirectory()) clean &= merge(from, to);
                else if (!to.exists() || old.equalsIgnoreCase(name) && same(from, to)) clean &= from.renameTo(to);
                // else both are there: the current one is in use; the former stays, untouched, beside it.
            }
        }
        return clean;
    }

    /** {@code name} in {@code folder}, or "name (2).ext", "name (3).ext"... the first that isn't taken. */
    static File free(File folder, String name) {
        File plain = new File(folder, name);
        if (!plain.exists()) return plain;
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name, ext = dot > 0 ? name.substring(dot) : "";
        for (int n = 2; ; n++) {
            File f = new File(folder, stem + " (" + n + ")" + ext);
            if (!f.exists()) return f;
        }
    }

    private static boolean same(File a, File b) {
        if (a == null || b == null) return a == b;
        try { return java.nio.file.Files.isSameFile(a.toPath(), b.toPath()); } catch (java.io.IOException e) { return false; }
    }

    private AppRename() { }
}
