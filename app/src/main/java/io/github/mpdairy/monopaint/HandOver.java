package io.github.mpdairy.monopaint;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * The move to a new application id (2026-10-30: {@link #OLD_ID} became ing.ell.mybaddrawings, a separate app on the
 * tablet that can't read the old one's private files). The old id's last build ({@code -PlegacyId}) leaves what it keeps
 * privately in {@link #FOLDER} beside the notebooks in shared storage, at launch and whenever it is left: its
 * preferences ({@link #PREFERENCES}: the brush library, slots, recent brushes, notebook options and every other
 * setting) and its private drawings folder ({@link #DRAWINGS}: the working notebook's recovery, and drawings made before
 * shared storage was allowed). The new app takes them once, the first time it may read shared storage and before it
 * reads its own preferences, then leaves {@link #IMPORTED} there, which also stops the old one writing. The old app's
 * own data is never touched. Notebooks are in shared storage already and need nothing. Pure but for the files; checked
 * on the desktop (HandOverChecks); the Android side is in {@link DrawingStorage}.
 */
final class HandOver {
    static final String OLD_ID = "app.helpyy.inkwash";
    static final String FOLDER = ".handover", PREFERENCES = "preferences.properties", DRAWINGS = "drawings", IMPORTED = "imported";

    /**
     * Preferences as text: each key behind a letter for its value's type (s, b, i, l, f). Sets of strings are left out
     * (the app keeps none).
     */
    static String encode(Map<String, ?> prefs) {
        Properties p = new Properties();
        for (Map.Entry<String, ?> e : prefs.entrySet()) {
            Object v = e.getValue();
            String type = v instanceof String ? "s" : v instanceof Boolean ? "b" : v instanceof Integer ? "i"
                    : v instanceof Long ? "l" : v instanceof Float ? "f" : null;
            if (type != null) p.setProperty(type + ":" + e.getKey(), String.valueOf(v));
        }
        StringWriter out = new StringWriter();
        try { p.store(out, AppName.NAME + " handover: the old app's preferences, read once by the new one"); }
        catch (IOException impossible) { throw new IllegalStateException(impossible); }
        return out.toString();
    }

    /**
     * {@link #encode}'s preferences back, typed; null if {@code text} is null or won't read. A content:// value (a folder
     * Android let the old app use) is left out: the new app has no such permission.
     */
    static Map<String, Object> decode(String text) {
        if (text == null) return null;
        Properties p = new Properties();
        Map<String, Object> prefs = new LinkedHashMap<>();
        try {
            p.load(new StringReader(text));
            for (String name : p.stringPropertyNames()) {
                String v = p.getProperty(name);
                if (name.length() < 3 || name.charAt(1) != ':' || v.startsWith("content://")) continue;
                String key = name.substring(2);
                switch (name.charAt(0)) {
                    case 's': prefs.put(key, v); break;
                    case 'b': prefs.put(key, Boolean.parseBoolean(v)); break;
                    case 'i': prefs.put(key, Integer.parseInt(v)); break;
                    case 'l': prefs.put(key, Long.parseLong(v)); break;
                    case 'f': prefs.put(key, Float.parseFloat(v)); break;
                    default: break;
                }
            }
        } catch (IOException | IllegalArgumentException e) { return null; }
        return prefs;
    }

    /** Whether the handover in {@code folder} is there and not yet taken. */
    static boolean waiting(File folder) { return new File(folder, PREFERENCES).isFile() && !new File(folder, IMPORTED).exists(); }

    /**
     * The old app's side: writes {@code prefs} and a copy of {@code drawings} (its private folder) into {@code folder},
     * each file whole or not at all; nothing once the new app took it. False if something didn't write.
     */
    static synchronized boolean leave(File folder, Map<String, ?> prefs, File drawings) {
        if (new File(folder, IMPORTED).exists()) return true;
        boolean ok = copy(drawings, new File(folder, DRAWINGS), true);
        return BrushBackup.write(new File(folder, PREFERENCES), encode(prefs)) && ok;
    }

    /**
     * The new app's side: the preferences waiting in {@code folder} (null if none), with its drawings copied into
     * {@code drawings} where it has none of that name. The caller saves the preferences, then calls {@link #taken}.
     */
    static Map<String, Object> take(File folder, File drawings) {
        if (!waiting(folder)) return null;
        Map<String, Object> prefs = decode(BrushBackup.read(new File(folder, PREFERENCES)));
        if (prefs != null) copy(new File(folder, DRAWINGS), drawings, false);
        return prefs;
    }
    /** Marks the handover in {@code folder} taken: never read again, and the old app stops writing it. */
    static boolean taken(File folder) {
        try { return new File(folder, IMPORTED).createNewFile() || new File(folder, IMPORTED).exists(); }
        catch (IOException e) { return false; }
    }

    /** Copies {@code from}'s files and folders into {@code to}, each through a temporary file; {@code replace} or keeps what is there. */
    private static boolean copy(File from, File to, boolean replace) {
        File[] files = from.listFiles();
        if (files == null) return true;
        if (!to.isDirectory() && !to.mkdirs()) return false;
        boolean ok = true;
        for (File f : files) {
            File target = new File(to, f.getName());
            if (f.isDirectory()) { ok &= copy(f, target, replace); continue; }
            if (target.exists() && !replace) continue;
            File temporary = new File(to, "." + f.getName() + ".tmp");
            try {
                Files.copy(f.toPath(), temporary.toPath(), StandardCopyOption.REPLACE_EXISTING);
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) { temporary.delete(); ok = false; }
        }
        return ok;
    }

    private HandOver() { }
}
