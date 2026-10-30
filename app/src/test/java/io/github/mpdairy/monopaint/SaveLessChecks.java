package io.github.mpdairy.monopaint;

import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.zip.CRC32;

/**
 * Saving less while drawing (docs/archive/notes/2026-10-19-audit.md, findings 6 and 7): the recovery save waits for a pause
 * ({@link RecoverySchedule}) and never runs during a stroke; a crash at any moment restores the page as it was after a
 * finished stroke, losing at most the schedule's window; a page is packed quickly for memory and the recovery file and
 * at the default level for its book's file, byte for byte as before; a snapshot released after its save lets the page's
 * next write skip the whole-layer copy.
 */
public final class SaveLessChecks {
    public static void main(String[] args) throws Exception {
        schedule();
        crashes();
        quickPages();
        layerCopies();
    }

    // The schedule

    private static void schedule() {
        RecoverySchedule s = new RecoverySchedule();
        check(s.ready(0, false, false) == -1, "Nothing to save before a change");
        check(s.changed(0) == RecoverySchedule.QUIET_MS, "A change is saved 2.5 s after it");
        check(s.ready(1000, false, false) == 1500, "Not before");
        check(s.changed(1000) == RecoverySchedule.QUIET_MS, "Another change starts the wait again");
        check(s.changed(2000) == RecoverySchedule.QUIET_MS && s.ready(4499, false, false) == 1, "… and again");
        check(s.changed(3000) == 2000 && s.changed(4000) == 1000, "While changes keep coming, the oldest waits 5 s at most");
        check(s.ready(5000, true, false) == RecoverySchedule.RETRY_MS, "Due while a stroke is down: it looks again in 250 ms, never saving then");
        check(s.ready(5000, false, false) == 0, "Due with the pen up: it saves");
        s.saved();
        check(!s.waiting() && s.ready(9000, false, false) == -1, "Saved: nothing waits");
        s.changed(10_000);
        check(s.ready(12_500, false, true) == 2500, "Wet paint after the quiet wait: it waits until 5 s are up");
        check(s.ready(15_000, false, true) == 0, "… then saves, wet or not");
        System.out.println("PASS: the recovery save waits for 2.5 s without a change, starting again at each, 5 s at most while changes keep coming, never while a stroke is down; wet paint waits until 5 s are up");
    }

    // Crashes, simulated

    /** A stroke: the pen down at {@code down}, the page final (lift, and any finishing render after it) at {@code end}. */
    private static final class Stroke { final int down, end; Stroke(int down, int end) { this.down = down; this.end = end; } }

    /**
     * Draws {@code strokes} on a page, its recovery saves queued as the app queues them ({@code schedule}, or after every
     * stroke as before when null), each written {@code saveMs} after the file thread reaches it: one at a time, a save
     * still waiting replaced by a newer one ({@code DocumentStore.recoverLater}). Then, crashing every 10 ms, reads back
     * the last recovery written by then. Returns {the most ms of finished strokes lost, saves written, the most ms the last
     * stroke before a pause of 2.5 s or more waited to be safe}.
     */
    private static int[] session(String name, List<Stroke> strokes, boolean schedule, int saveMs) throws IOException {
        ToneDocument page = InkWashLayers.blank(240, 300);
        DrawingBook book = new DrawingBook(page);
        List<Long> states = new ArrayList<>();
        states.add(crc(page));
        RecoverySchedule recovery = new RecoverySchedule();
        // What was written, and when it was done; a save waiting for the file thread.
        List<byte[]> written = new ArrayList<>(); List<Integer> done = new ArrayList<>();
        byte[] waiting = null;
        int end = strokes.get(strokes.size() - 1).end + 10_000, timer = -1, busy = 0, next = 0;
        for (int t = 0; t <= end; t += 10) {
            boolean queue = false;
            while (next < strokes.size() && strokes.get(next).end == t) {
                draw(page, next); states.add(crc(page)); next++;
                if (schedule) timer = (int) (t + recovery.changed(t)); else queue = true;
            }
            if (timer >= 0 && t >= timer) {
                boolean stroking = false;
                for (Stroke s : strokes) stroking |= s.down <= t && t < s.end;
                long wait = recovery.ready(t, stroking, false);
                if (wait == 0) {
                    check(!stroking, name + ": no save while a stroke is down or finishing");
                    queue = true; recovery.saved(); timer = -1;
                } else timer = wait < 0 ? -1 : (int) (t + wait);
            }
            if (queue) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                RecoveryCodec.write(out, book.snapshot(), "");
                waiting = out.toByteArray();
            }
            if (waiting != null && t >= busy) { written.add(waiting); waiting = null; done.add(busy = t + saveMs); }
        }
        // How many strokes each recovery written holds, read back as the app reads it.
        int[] holds = new int[written.size()];
        for (int i = 0; i < holds.length; i++) {
            holds[i] = states.indexOf(crc(RecoveryCodec.read(new ByteArrayInputStream(written.get(i))).book.current()));
            check(holds[i] >= 0, name + ": a crash restores the page as it was after a finished stroke");
        }
        int lost = 0;
        for (int crash = 0; crash <= end; crash += 10) {
            int last = -1;
            for (int i = 0; i < done.size(); i++) if (done.get(i) <= crash) last = i;
            int restored = last < 0 ? 0 : holds[last];
            // The first finished stroke the recovery doesn't have: the work lost runs from its end to the crash.
            if (restored < strokes.size() && strokes.get(restored).end <= crash) lost = Math.max(lost, crash - strokes.get(restored).end);
        }
        int paused = 0;
        for (int k = 0; k < strokes.size(); k++) {
            if (k + 1 < strokes.size() && strokes.get(k + 1).down - strokes.get(k).end < RecoverySchedule.QUIET_MS) continue;
            int safe = Integer.MAX_VALUE;
            for (int i = 0; i < done.size(); i++) if (holds[i] > k) { safe = done.get(i); break; }
            paused = Math.max(paused, safe - strokes.get(k).end);
        }
        return new int[]{lost, written.size(), paused};
    }
    private static void crashes() throws IOException {
        // The Manta, 20 times slower than this Mac at most: a page packed quickly and written in about 1.4 s (71 ms
        // here, the owner's heavy pages), at the default level, as every save was before, about 4.7 s (236 ms).
        int quick = 1400, before = 4700;
        // Bursts of five strokes (0.6 s down, 0.2 s finishing, 0.4 s apart), then a 4 s look.
        List<Stroke> pauses = new ArrayList<>();
        for (int burst = 0, t = 500; burst < 6; burst++, t += 4000)
            for (int i = 0; i < 5; i++, t += 1200) pauses.add(new Stroke(t, t + 800));
        // Nonstop: 60 strokes 0.7 s long, 0.3 s apart: never 2.5 s without a change.
        List<Stroke> nonstop = new ArrayList<>();
        for (int i = 0, t = 500; i < 60; i++, t += 1000) nonstop.add(new Stroke(t, t + 700));
        int[] a = session("with pauses", pauses, true, quick), b = session("nonstop", nonstop, true, quick);
        int[] a0 = session("with pauses, as before", pauses, false, before), b0 = session("nonstop, as before", nonstop, false, before);
        int most = (int) (RecoverySchedule.MOST_MS + 700 + RecoverySchedule.RETRY_MS) + quick;
        check(a[0] <= most && b[0] <= most, "A crash loses at most 5 s of strokes, a stroke down then, and the save's own time (" + a[0] + ", " + b[0] + " ms)");
        check(a[2] <= RecoverySchedule.QUIET_MS + quick, "The last stroke before a pause is safe 2.5 s and the save's own time after it (" + a[2] + " ms)");
        check(a[1] < pauses.size() / 2 && b[1] < nonstop.size() / 3, "Far fewer saves (" + a[1] + ", " + b[1] + ")");
        System.out.printf(Locale.ROOT, "PASS: a crash every 10 ms restores the page as after a finished stroke, never mid-stroke, no save while a stroke is down; on the Manta (saves 20 times slower than here):%n"
                        + "  with pauses: %d saves for 30 strokes (were %d), at most %.1f s of strokes lost (was %.1f), the last before a pause safe %.1f s after it (was %.1f)%n"
                        + "  nonstop: %d saves for 60 strokes (were %d), at most %.1f s lost (was %.1f)%n",
                a[1], a0[1], a[0] / 1000.0, a0[0] / 1000.0, a[2] / 1000.0, a0[2] / 1000.0, b[1], b0[1], b[0] / 1000.0, b0[0] / 1000.0);
    }

    // Packed once quickly, filed at the default level

    private static void quickPages() throws IOException {
        File dir = Files.createTempDirectory("saveless").toFile();
        try {
            ToneDocument first = NotebookFixture.sketch(480, 600, 1);
            DrawingBook book = new DrawingBook(first);
            book.addPage();
            ToneDocument second = book.current();
            for (int i = 0; i < 6; i++) draw(second, i);
            byte[] secondDefault = DrawingBook.encode(second);
            // The recovery save packs the drawn page quickly, and the book keeps those bytes.
            DrawingBook.Snapshot recovery = book.snapshot();
            ByteArrayOutputStream file = new ByteArrayOutputStream();
            RecoveryCodec.write(file, recovery, "");
            PackedPage quick = recovery.activePacked();
            check(quick != null && quick.quick && quick.length > 0, "The recovery save packs the page drawn on quickly");
            book.offerPacked(recovery.activeKey, quick);
            check(book.packed(1) == quick, "The book keeps the bytes the recovery save packed");
            book.select(0);
            check(book.packed(1) == quick, "A page turn packs nothing again: the page left keeps the recovery's bytes");
            check(book.packed(0) != null && book.packed(0).quick, "A page left unpacked is packed quickly (the first page, left at addPage)");
            // A named save: every page at the default level, byte for byte as before.
            File named = new File(dir, "named.mpaint");
            DrawingBook.Snapshot save = book.snapshot();
            try (OutputStream out = new FileOutputStream(named)) { BookCodec.write(out, save); }
            BookCodec.Pages read = BookCodec.openPages(named);
            check(Arrays.equals(read.pages.get(0).bytes(), DrawingBook.encode(first)) && Arrays.equals(read.pages.get(1).bytes(), secondDefault),
                    "The file holds each page packed at the default level, as before");
            ByteArrayOutputStream before = new ByteArrayOutputStream();
            List<PackedPage> asBefore = Arrays.asList(PackedPage.of(DrawingBook.encode(first)), PackedPage.of(secondDefault));
            // With the day a page was last added to (LAST), which a page drawn on gives it.
            BookOptions.Covers kept = new BookOptions.Covers(); kept.lastDay = save.lastDay;
            BookCodec.write(before, save.width, save.height, asBefore, save.index, new int[2], save.cover, save.pictures, kept, new long[2]);
            check(Arrays.equals(Files.readAllBytes(named.toPath()), before.toByteArray()), "The whole file is byte for byte what it was before quick packing");
            // After the save, the book reads both pages from the file: no quick bytes left in memory.
            BookCodec.replaced(save, named, () -> { });
            book.savedPages(save);
            check(book.packed(0) != null && !book.packed(0).inMemory() && !book.packed(0).quick, "After the save the current page reads the file's bytes");
            check(book.packed(1) != null && !book.packed(1).inMemory() && !book.packed(1).quick, "… and so does the page left");
            // A recovery read back: its pages may be quick; a file made from it is at the default level.
            DrawingBook recovered = RecoveryCodec.read(new ByteArrayInputStream(file.toByteArray())).book;
            check(crc(recovered.current()) == crc(second), "The recovery restores the page drawn on");
            ByteArrayOutputStream again = new ByteArrayOutputStream();
            DrawingBook.Snapshot fromRecovery = recovered.snapshot();
            BookCodec.write(again, fromRecovery);
            check(Arrays.equals(fromRecovery.written.get(1).bytes(), secondDefault), "A save after a recovery files the page at the default level");
            // A page turned while its save runs: once saved, the page left reads the file's bytes, not its quick ones.
            DrawingBook turning = new DrawingBook(NotebookFixture.sketch(480, 600, 3));
            turning.addPage(); draw(turning.current(), 9);
            DrawingBook.Snapshot running = turning.snapshot();
            turning.select(0);
            check(turning.packed(1).quick, "The page turned from is packed quickly");
            File turned = new File(dir, "turned.mpaint");
            try (OutputStream out = new FileOutputStream(turned)) { BookCodec.write(out, running); }
            BookCodec.replaced(running, turned, () -> { });
            turning.savedPages(running);
            check(!turning.packed(1).inMemory() && !turning.packed(1).quick, "Turned from during its save, it reads the file's bytes once saved");
            System.out.println("PASS: a page drawn on is packed once quickly by the recovery save (or a page turn) and kept, so turning the page packs nothing again; a book's file holds every page at the default level, byte for byte as before, and after the save the book reads them from it; a recovery's pages go back to the default level at the next save");
        } finally {
            for (File f : dir.listFiles()) f.delete();
            dir.delete();
        }
    }

    // The layer copy after a snapshot

    private static void layerCopies() throws IOException {
        ToneDocument page = InkWashLayers.blank(240, 300);
        page.selectLayer(InkWashLayers.INK);
        draw(page, 0);
        byte[] held = live(page);
        ToneDocument.Snapshot s = page.layerSnapshot();
        s.release();
        draw(page, 1);
        check(live(page) == held, "A released snapshot: the next write copies no layer");
        ToneDocument.Snapshot kept = page.layerSnapshot();
        byte[] keptTones = kept.layers.get(InkWashLayers.INK).tones.clone();
        draw(page, 2);
        check(live(page) != held && Arrays.equals(kept.layers.get(InkWashLayers.INK).tones, keptTones), "A snapshot not released: the write copies the layer, the snapshot unchanged");
        held = live(page);
        kept.release();
        draw(page, 3);
        check(live(page) == held, "Released after the copy: the copy is the page's own, written in place");
        ToneDocument.Snapshot one = page.layerSnapshot(), two = page.layerSnapshot();
        one.release();
        draw(page, 4);
        check(live(page) != held, "Two snapshots, one released: the write still copies");
        held = live(page);
        two.release();
        draw(page, 5);
        check(live(page) == held, "… the other's late release changes nothing for the copy");
        // A book's snapshot, as the recovery save takes and releases it.
        DrawingBook book = new DrawingBook(page);
        DrawingBook.Snapshot saved = book.snapshot();
        saved.packed(); saved.release();
        draw(page, 6);
        check(live(page) == held, "A recovery snapshot released once written: the next stroke copies no layer");
        // A page turn packs the page left and releases its snapshot: back on it, the first write copies nothing.
        book.addPage(); book.select(0);
        draw(book.current(), 7);
        check(live(book.current()) == held, "A page packed at a turn: back on it, the first write copies no layer");
        System.out.println("PASS: a snapshot released once its save is done (or dropped) lets the page's next write skip the whole-layer copy; one not released, or another still held, keeps the copy");
    }
    /** A stroke: a 20 × 20 square of its own tone. */
    private static void draw(ToneDocument page, int stroke) {
        page.begin();
        int[] row = new int[20]; Arrays.fill(row, 30 + stroke * 7 % 200);
        int x = stroke * 23 % 220, y = stroke * 37 % 280;
        for (int r = 0; r < 20; r++) page.setTones(x, y + r, 20, 1, row);
        page.finish();
    }
    /** The bound layer's tones array as the page holds it. */
    private static byte[] live(ToneDocument page) {
        byte[][] tones = new byte[ToneDocument.STACK][], alpha = new byte[ToneDocument.STACK][];
        int[] opacity = new int[ToneDocument.STACK];
        page.visibleLayers(tones, alpha, opacity);
        return tones[page.activeLayer()];
    }
    private static long crc(ToneDocument page) {
        CRC32 c = new CRC32();
        byte[][] tones = new byte[ToneDocument.STACK][], alpha = new byte[ToneDocument.STACK][];
        int n = page.visibleLayers(tones, alpha, new int[ToneDocument.STACK]);
        for (int i = 0; i < n; i++) { c.update(tones[i]); c.update(alpha[i]); }
        return c.getValue();
    }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
    private SaveLessChecks() {}
}
