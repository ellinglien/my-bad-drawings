package io.github.mpdairy.monopaint;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

/** The move from the old application id ({@link HandOver}): preferences round trip typed, drawings copied, taken once. */
public final class HandOverChecks {
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
    static void write(File f, String text) throws Exception { f.getParentFile().mkdirs(); Files.write(f.toPath(), text.getBytes(StandardCharsets.UTF_8)); }
    static String read(File f) { return BrushBackup.read(f); }

    public static void main(String[] args) throws Exception {
        check(HandOver.OLD_ID.equals("app.helpyy.inkwash"), "the old id, as the owner's installed app has it");

        Map<String, Object> prefs = new LinkedHashMap<>();
        prefs.put("brush_library", "zealous-shallot.M67gz=:\\#!\n\ttwo lines, é");
        prefs.put("toolbox_right", true);
        prefs.put("slot_size", 31);
        prefs.put("made_at", 1761800000000L);
        prefs.put("grey", 0.375f);
        prefs.put("empty", "");
        prefs.put("export_folder", "content://com.android.externalstorage.documents/tree/primary%3AEXPORT");
        prefs.put("a set", new java.util.HashSet<>(java.util.Arrays.asList("x")));
        Map<String, Object> back = HandOver.decode(HandOver.encode(prefs));
        check(back != null && back.size() == 6, "every typed value back, the set and the folder permission left out: " + back);
        for (String key : new String[]{"brush_library", "toolbox_right", "slot_size", "made_at", "grey", "empty"})
            check(prefs.get(key).equals(back.get(key)) && prefs.get(key).getClass() == back.get(key).getClass(), key + " kept, with its type");
        check(HandOver.decode(null) == null && HandOver.decode("i\\:n=many") == null, "unreadable text: nothing");
        check(HandOver.decode("x=1\nq\\:y=2\n").isEmpty(), "untyped lines skipped");

        File root = Files.createTempDirectory("handover").toFile();
        File folder = new File(root, HandOver.FOLDER), oldPrivate = new File(root, "old"), newPrivate = new File(root, "new");
        write(new File(oldPrivate, "_recovery.mpaint"), "working");
        write(new File(oldPrivate, "Sketches/Cat.mpaint"), "cat");
        check(!HandOver.waiting(folder) && HandOver.take(folder, newPrivate) == null, "no handover: nothing to take");

        check(HandOver.leave(folder, prefs, oldPrivate) && HandOver.waiting(folder), "the old app leaves it");
        write(new File(oldPrivate, "_recovery.mpaint"), "working, later");
        check(HandOver.leave(folder, prefs, oldPrivate) && "working, later".equals(read(new File(folder, "drawings/_recovery.mpaint"))), "left again: the newer copy");

        write(new File(newPrivate, "Sketches/Cat.mpaint"), "the new app's own");
        Map<String, Object> taken = HandOver.take(folder, newPrivate);
        check(taken != null && taken.equals(back), "the new app takes the preferences");
        check("working, later".equals(read(new File(newPrivate, "_recovery.mpaint"))), "the recovery copied");
        check("the new app's own".equals(read(new File(newPrivate, "Sketches/Cat.mpaint"))), "a drawing already there kept");
        check(HandOver.taken(folder) && !HandOver.waiting(folder) && HandOver.take(folder, newPrivate) == null, "taken once");

        write(new File(oldPrivate, "_recovery.mpaint"), "after");
        check(HandOver.leave(folder, prefs, oldPrivate) && "working, later".equals(read(new File(folder, "drawings/_recovery.mpaint"))), "once taken, the old app writes no more");
        check(new File(oldPrivate, "Sketches/Cat.mpaint").isFile(), "the old app's files untouched");
        System.out.println("PASS: handover from the old application id");
    }
}
