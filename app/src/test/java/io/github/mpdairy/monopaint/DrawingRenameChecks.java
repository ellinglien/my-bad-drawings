package io.github.mpdairy.monopaint;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public final class DrawingRenameChecks {
    public static void main(String[] args) throws Exception {
        File root = Files.createTempDirectory("inkwash-files").toFile();
        DrawingFiles files = new DrawingFiles(root);
        write(new File(root, "a.mpaint")); write(new File(root, "a.mpaint.bak")); write(new File(root, "b.mpaint"));
        check(files.rename("a", "c").equals("c"), "Rename returns the new path");
        check(!new File(root, "a.mpaint").exists() && new File(root, "c.mpaint").exists()
                && !new File(root, "a.mpaint.bak").exists() && !new File(root, "c.mpaint.bak").exists(), "Rename keeps the backup as the new base file");
        check(fails(() -> files.rename("c", "b")), "Rename never replaces another drawing");
        check(fails(() -> files.rename("c", "bad/name")), "Rename refuses invalid names");
        files.rename("never-saved", "fresh");
        check(!new File(root, "fresh.mpaint").exists(), "Renaming a drawing not yet saved only checks the name is free");
        check(fails(() -> files.rename("never-saved", "b")), "Even an unsaved drawing cannot take a used name");
        check(files.rename("b", "b").equals("b") && new File(root, "b.mpaint").exists(), "Renaming to the same name changes nothing");

        write(new File(root, "d.mpaint.bak"));
        check(files.rename("d", "e").equals("e"), "Rename of a backup-only drawing returns the new path");
        check(new File(root, "e.mpaint").exists() && !new File(root, "d.mpaint.bak").exists() && !new File(root, "e.mpaint.bak").exists(),
                "A lone backup becomes the new base file");
        boolean listed = false;
        for (DrawingFiles.Entry entry : files.list("")) listed |= entry.name.equals("e") && !entry.folder;
        check(listed, "The list shows the renamed backup-only drawing");

        Files.write(new File(root, "f.mpaint").toPath(), new byte[]{1}); Files.write(new File(root, "f.mpaint.bak").toPath(), new byte[]{2});
        write(new File(root, "f.mpaint.new"));
        files.rename("f", "g");
        check(Files.readAllBytes(new File(root, "g.mpaint").toPath())[0] == 2, "The backup wins over the base file");
        check(!new File(root, "f.mpaint").exists() && !new File(root, "f.mpaint.bak").exists() && !new File(root, "f.mpaint.new").exists()
                && !new File(root, "g.mpaint.bak").exists() && !new File(root, "g.mpaint.new").exists(), "The old files are gone");

        write(new File(root, "c.mpaint.new"));
        files.delete("c");
        check(!new File(root, "c.mpaint").exists() && !new File(root, "c.mpaint.bak").exists() && !new File(root, "c.mpaint.new").exists(),
                "Delete removes the drawing, its backup and its partial write");
        files.delete("c");
        for (File file : root.listFiles()) file.delete();
        check(root.delete(), "The temp folder is removed");
        System.out.println("PASS: rename with backups, no replacing, name validation, unsaved rename, delete");
    }
    private interface Action { void run() throws IOException; }
    private static boolean fails(Action action) { try { action.run(); return false; } catch (IOException expected) { return true; } }
    private static void write(File file) throws IOException { Files.write(file.toPath(), new byte[]{1}); }
    private static void check(boolean valid, String message) { if (!valid) throw new AssertionError(message); }
}
