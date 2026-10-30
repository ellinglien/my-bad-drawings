package io.github.mpdairy.monopaint;

import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/**
 * The toolbar kept for good, beside the brushes' file ({@link BrushBackup}): "Scribulor toolbar.txt" in shared storage
 * lists the ten slots (number, brush name, full share code, its size and grey: each slot's own since 2026-10-27),
 * then the eraser's size and the selected tool, tab-separated. A backup only: the app never reads it. Written when the text changed, off the
 * main thread, and an IO failure is let go. The text is pure, checked on the desktop.
 */
final class ToolBackup {
    static final String SUFFIX = " toolbar.txt", NAME = AppName.NAME + SUFFIX;
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "toolbar-backup"); t.setDaemon(true); return t; });
    private static String last;

    /** The file's text for {@code slots} with tool {@code selected} (a slot, {@link ToolSlots#ERASER}, SMUDGE or STAMP). */
    static String text(ToolSlots slots, int selected) {
        StringBuilder s = new StringBuilder("# " + AppName.NAME + " toolbar (a backup, never read back)\n# slot\tbrush\tshare code\tsize\tgrey (darkness, 0 white to 1 black)\n");
        for (int i = 0; i < ToolSlots.COUNT; i++) {
            Brush b = slots.brush(i);
            ToolSwatches.Look look = slots.swatches.look(ToolSlots.key(i), b);
            s.append(i + 1).append('\t').append(b.name()).append('\t').append(ModularCode.encode(b.parts))
                    .append('\t').append(ToolSwatches.pxText(look.px)).append(" px\t").append(Math.round(1000 * look.darkness) / 1000.0).append('\n');
        }
        s.append("eraser\t").append(ToolSwatches.pxText(slots.swatches.look(ToolSwatches.ERASER_KEY, null).px)).append(" px\n");
        s.append("selected\t").append(selected < ToolSlots.COUNT ? "slot " + (selected + 1)
                : selected == ToolSlots.ERASER ? "eraser" : selected == ToolSlots.SMUDGE ? "smudge" : "stamp").append('\n');
        return s.toString();
    }

    /** Writes the toolbar to {@code file} if its text differs from the last written; never throws. */
    static void save(File file, ToolSlots slots, int selected) {
        if (file == null) return;
        try {
            String text = text(slots, selected);
            synchronized (ToolBackup.class) { if (text.equals(last)) return; last = text; }
            WRITER.execute(() -> { try { if (!text.equals(BrushBackup.read(file))) BrushBackup.write(file, text); } catch (RuntimeException e) { /* a backup only */ } });
        } catch (RuntimeException e) { /* a backup only */ }
    }

    private ToolBackup() { }
}
