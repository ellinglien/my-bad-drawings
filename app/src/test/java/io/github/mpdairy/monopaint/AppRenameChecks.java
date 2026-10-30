package io.github.mpdairy.monopaint;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * A rename simulated in a temporary folder ({@link AppRename}): "Inkwash" becomes "Quill". The former folders move whole
 * or merge into folders already there; a clash keeps both; the app's own files take the new name, or stay beside a
 * current one; a second run changes nothing; a file that can't move stays where it was and a later run finishes. Also
 * the name's one switch ({@link AppName}) and the import files under a former name.
 */
public final class AppRenameChecks {
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    static void write(File f, String text) throws Exception {
        f.getParentFile().mkdirs();
        Files.write(f.toPath(), text.getBytes(StandardCharsets.UTF_8));
    }
    static String read(File f) throws Exception { return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8); }
    static int count(File dir) {
        int n = 0;
        File[] all = dir.listFiles();
        if (all != null) for (File f : all) n += f.isDirectory() ? count(f) : 1;
        return n;
    }

    public static void main(String[] args) throws Exception {
        // The name is My Bad Drawings (2026-10-29), formerly Scribulor, before that Inkwash; the files are named from it.
        check(AppName.NAME.equals("My Bad Drawings") && java.util.Arrays.equals(AppName.FORMER, new String[]{"Scribulor", "Inkwash"}), "the name is My Bad Drawings, formerly Scribulor and Inkwash");
        check(BrushBackup.NAME.equals("My Bad Drawings brushes.txt") && BrushBackup.NIBS.equals("My Bad Drawings stamps"), "the brushes' file and stamps' folder");
        check(DailyPrompt.FILE.equals("My Bad Drawings prompts.txt") && DailyPrompt.DEFAULTS.startsWith("# My Bad Drawings daily prompts"), "the prompts' file");
        check(BrushImport.NAME.equals("My Bad Drawings import.txt") && BrushImport.DONE.equals("My Bad Drawings import (added).txt"), "the import files");
        check(BrushImport.isImport("Friend.scribulor.txt") && BrushImport.isImport("scribulor IMPORT.txt") && !BrushImport.isImport("Friend (added).txt"), "import files recognised");
        check(BrushImport.isImport("Friend.inkwash.txt") && BrushImport.isImport("Inkwash import.txt"), "the former name's import files still recognised");
        check(AppRename.formerFolder("My Bad Drawings bench", AppName.NAME, "Scribulor").equals("Scribulor bench") && AppRename.formerFolder("My Bad Drawings bench", AppName.NAME, "Inkwash").equals("Inkwash bench"), "the bench variant's former folders");
        // app/build.gradle reads the name from AppName.java's one NAME line (the same pattern as there).
        File source = new File("app/src/main/java/io/github/mpdairy/monopaint/AppName.java");
        if (source.isFile()) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?m)^\\s*static final String NAME = \"([^\"]+)\";").matcher(read(source));
            check(m.find() && m.group(1).equals(AppName.NAME) && !m.find(), "Gradle finds the one NAME line");
        }
        // The loading screen's name, a vector made from it (scripts/java/SplashName.java), says the name.
        File splash = new File("app/src/main/res/drawable/splash_name.xml");
        if (splash.isFile()) check(read(splash).contains("<!-- \"" + AppName.NAME + "\" (AppName.NAME)"), "the loading screen's name is made again after a rename (scripts/java/SplashName.java)");
        // The file markers stay whatever the name (AppName's list).
        check(NotebookExport.KEY.equals("Inkwash page") && CoverTexture.NAME.equals("Inkwash cover sheet"), "the file markers stay");
        // A former name's import files still come in.
        String[] apps = {"Quill", "Inkwash"};
        check("".equals(BrushImport.importSuffix("Inkwash import.txt", apps)) && "".equals(BrushImport.importSuffix("quill import.txt", apps)), "either import.txt");
        check(".Inkwash.txt".equals(BrushImport.importSuffix("Friend.Inkwash.txt", apps)) && ".quill.txt".equals(BrushImport.importSuffix("Friend.quill.txt", apps)), "either *.name.txt");
        check(BrushImport.importSuffix("Friend.txt", apps) == null, "a plain txt is no import");
        check("Quill bench".equals(AppRename.formerFolder("Inkwash bench", "Inkwash", "Quill")) && AppRename.formerFolder("Other", "Inkwash", "Quill") == null, "a variant's folder");

        String[] former = {"Inkwash"};
        File root = Files.createTempDirectory("app-rename").toFile();
        File document = new File(root, "Document"), export = new File(root, "EXPORT"), pictures = new File(root, "Pictures");
        File[] parents = {document, export, pictures};
        // Before: the owner's files under the old name; EXPORT/Quill already holds a picture with a clashing name.
        write(new File(document, "Inkwash/Sketches.mpaint"), "drawing");
        write(new File(document, "Inkwash/Trip/Day 1.mpaint"), "day one");
        write(new File(document, "Inkwash/Inkwash brushes.txt"), "brushes");
        write(new File(document, "Inkwash/Inkwash prompts.txt"), "prompts");
        write(new File(document, "Inkwash/Inkwash toolbar.txt"), "toolbar");
        write(new File(document, "Inkwash/Inkwash import (added).txt"), "added");
        write(new File(document, "Inkwash/Inkwash stamps/stamp_1.txt"), "nib");
        write(new File(document, "Inkwash/Friend.inkwash.txt"), "codes");
        write(new File(export, "Inkwash/Sketches.png"), "old png");
        write(new File(export, "Inkwash/Trip/Day 1.png"), "old day");
        write(new File(export, "Quill/Sketches.png"), "new png");
        write(new File(export, "Quill/Trip/Day 2.png"), "new day");
        int before = count(root);

        File drawings = new File(document, "Quill");
        check(AppRename.carryOver(parents, drawings, "Quill", "Quill", former), "everything moves");
        check(count(root) == before, "no file lost or added: " + count(root) + " of " + before);
        check(!new File(document, "Inkwash").exists() && !new File(export, "Inkwash").exists(), "the old folders are gone");
        check(read(new File(drawings, "Sketches.mpaint")).equals("drawing") && read(new File(drawings, "Trip/Day 1.mpaint")).equals("day one"), "the drawings moved");
        check(read(new File(drawings, "Quill brushes.txt")).equals("brushes") && read(new File(drawings, "Quill prompts.txt")).equals("prompts"), "own files renamed");
        check(read(new File(drawings, "Quill toolbar.txt")).equals("toolbar"), "the toolbar's file renamed");
        check(read(new File(drawings, "Quill import (added).txt")).equals("added") && read(new File(drawings, "Quill stamps/stamp_1.txt")).equals("nib"), "import log and stamps renamed");
        check(new File(drawings, "Friend.inkwash.txt").isFile(), "a friend's import file stays as named (still read)");
        check(read(new File(export, "Quill/Sketches.png")).equals("new png") && read(new File(export, "Quill/Sketches (2).png")).equals("old png"), "a clash keeps both");
        check(read(new File(export, "Quill/Trip/Day 1.png")).equals("old day") && read(new File(export, "Quill/Trip/Day 2.png")).equals("new day"), "subfolders merge");
        check(!pictures.exists(), "a folder that never was stays absent");

        // Idempotent: a second run changes nothing.
        check(AppRename.carryOver(parents, drawings, "Quill", "Quill", former) && count(root) == before, "a second run is a no-op");

        // Both the old and the new own file: the current one is kept, the former stays beside it, untouched.
        write(new File(drawings, "Inkwash brushes.txt"), "older brushes");
        check(AppRename.adopt(drawings, "Quill", former), "adopt with both there");
        check(read(new File(drawings, "Quill brushes.txt")).equals("brushes") && read(new File(drawings, "Inkwash brushes.txt")).equals("older brushes"), "both brushes' files kept");

        // A partial failure: a folder whose files can't move out. Nothing is lost, the rest moves, and a later run finishes.
        write(new File(export, "Inkwash/Stuck/a.png"), "a");
        write(new File(export, "Inkwash/Loose.png"), "loose");
        write(new File(export, "Quill/Stuck/b.png"), "b");
        File stuck = new File(export, "Inkwash/Stuck");
        int before2 = count(root);
        check(stuck.setWritable(false), "can make a folder read-only");
        try {
            check(!AppRename.carryOver(parents, drawings, "Quill", "Quill", former), "a stuck file reports it");
            check(count(root) == before2, "nothing lost on failure");
            check(read(new File(stuck, "a.png")).equals("a") && read(new File(export, "Quill/Loose.png")).equals("loose"), "the stuck file stayed, the rest moved");
        } finally { stuck.setWritable(true); }
        check(AppRename.carryOver(parents, drawings, "Quill", "Quill", former) && count(root) == before2, "a later run finishes");
        check(read(new File(export, "Quill/Stuck/a.png")).equals("a") && !new File(export, "Inkwash").exists(), "and the old folder is gone");

        // Only the case changes ("Quill" to "QUILL"): no merge into itself, nothing doubled, files keep their contents.
        int before3 = count(root);
        check(AppRename.carryOver(parents, new File(document, "QUILL"), "QUILL", "QUILL", new String[]{"Quill"}), "a case-only rename");
        check(count(root) == before3, "nothing doubled or lost by a case-only rename");
        check(java.util.Arrays.asList(document.list()).contains("QUILL") && read(new File(document, "QUILL/QUILL brushes.txt")).equals("brushes"), "the folder and own files take the new case");

        System.out.println("AppRenameChecks passed");
    }
}
