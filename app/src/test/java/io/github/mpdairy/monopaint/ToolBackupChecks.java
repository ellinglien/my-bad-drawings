package io.github.mpdairy.monopaint;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/** The toolbar backup's text ({@link ToolBackup}): header, a tab-separated line per slot, then the size, the eraser's, grey and selection. */
public final class ToolBackupChecks {
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
    public static void main(String[] args) throws Exception {
        Assets.install(Assets.folder(Assets.FOLDER));
        Map<String, String> prefs = new HashMap<>();
        ToolSlots slots = new ToolSlots(new BrushLibrary.Store() {
            public String get(String key) { return prefs.get(key); }
            public void put(String key, String value) { if (value == null) prefs.remove(key); else prefs.put(key, value); }
        }, new Random(1));
        String text = ToolBackup.text(slots, 2);
        String[] lines = text.split("\n");
        check(lines.length == 2 + ToolSlots.COUNT + 2 && lines[0].startsWith("#") && lines[1].startsWith("#"), "header, ten slots, eraser, selected");
        for (int i = 0; i < ToolSlots.COUNT; i++) {
            String[] f = lines[2 + i].split("\t");
            Brush b = slots.brush(i);
            check(f.length == 5 && f[0].equals("" + (i + 1)) && f[1].equals(b.name()) && f[2].equals(ModularCode.encode(b.parts)) && f[3].equals(ToolSwatches.pxText(ToolSlots.firstPx(i)) + " px") && f[4].equals("" + ToolSlots.firstGrey(i)), "slot " + (i + 1) + " line, its size and grey: " + lines[2 + i]);
        }
        check(lines[2].split("\t")[1].equals("Another pencil"), "first slot is Another pencil");
        check(lines[2 + ToolSlots.COUNT].equals("eraser\t31 px") && lines[3 + ToolSlots.COUNT].equals("selected\tslot 3"), "the eraser's own 31 px, selected lines");
        check(ToolBackup.text(slots, ToolSlots.ERASER).endsWith("selected\teraser\n"), "eraser selected");
        check(ToolBackup.text(slots, 2).equals(text), "same state, same text");
        slots.swatches.useGrey(ToolSlots.key(2), 0.25);
        check(!ToolBackup.text(slots, 2).equals(text) && ToolBackup.text(slots, 2).split("\n")[4].endsWith("\t" + ToolSwatches.pxText(ToolSlots.firstPx(2)) + " px\t0.25"), "a slot's grey change changes its line");
        slots.swatches.useSize(ToolSlots.key(2), 10);
        check(ToolBackup.text(slots, 2).split("\n")[4].endsWith("\t10 px\t0.25"), "a slot's size change changes its line");
        System.out.println("PASS: toolbar backup lines");
    }
}
