package io.github.mpdairy.monopaint;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A notebook's pages read from its file (docs/archive/superpowers/specs/2026-10-18-notebooks-design.md, "Pages read from the
 * file"), on a 60-page notebook of about 1 MB pages, far past the 16 MB pages were once limited to: opening holds no page
 * but the shown one, turning holds two decoded, a save streams the pages it didn't change from the old file and then
 * reads every page from the new one, the changed pages' memory freed; a damaged page is refused when read and kept as
 * it is by a save; a cut file doesn't open; a recovery holds only the changed pages; an interrupted save leaves the old
 * file as it was and readable.
 */
public final class NotebookPagesChecks {
    static final int PAGES = 60, SIDE = 1000;

    public static void main(String[] args) throws Exception {
        File dir = Files.createTempDirectory("pages").toFile();
        try {
            File file = new File(dir, "big.mpaint");
            List<byte[]> distinct = new ArrayList<>();
            for (int i = 0; i < 4; i++) distinct.add(DrawingBook.encode(noise(i)));
            List<PackedPage> pages = new ArrayList<>();
            for (int i = 0; i < PAGES; i++) pages.add(PackedPage.of(distinct.get(i % distinct.size())));
            try (OutputStream out = new BufferedOutputStream(new FileOutputStream(file))) {
                BookCodec.write(out, SIDE, SIDE, pages, 0, new int[PAGES], 0, new ArrayList<>());
            }
            distinct = null; pages = null;
            check(file.length() > 50L << 20, "A notebook of " + (file.length() >> 20) + " MB, far past the old 16 MB");
            long before = used();
            DrawingBook book = BookCodec.open(file);
            long opened = used() - before;
            check(book.count() == PAGES && opened < 24L << 20, "Opening holds only the shown page decoded (" + (opened >> 20) + " MB)");
            for (int i = 1; i < PAGES; i += 7) book.select(i);
            long turned = used() - before;
            check(turned < 40L << 20, "Turning holds two pages decoded at most (" + (turned >> 20) + " MB)");
            // An edited page stays in memory until saved.
            book.select(10); mark(book.current());
            book.select(11);
            check(book.packed(10).inMemory() && !book.packed(12).inMemory(), "The changed page is held in memory, the others read from the file");
            save(book, file, dir);
            check(!book.packed(10).inMemory(), "Once saved, the changed page too is read from the new file, its memory freed");
            book.select(10);
            check(book.current().compositeTone(5, 5) == 0, "and reads back as drawn");
            long saved = used() - before;
            check(saved < 48L << 20, "Saving streamed the pages it didn't change (" + (saved >> 20) + " MB held)");
            DrawingBook again = BookCodec.open(file);
            again.select(10);
            check(again.current().compositeTone(5, 5) == 0 && again.count() == PAGES, "The saved file reopens with every page");
            damage(file, dir);
            recovery(dir);
            interrupted(file, dir);
        } finally { NotebookMigrationChecks.delete(dir); }
        System.out.println("PASS: notebook pages read from their file: a 60-page notebook of 58 MB opens holding only its shown page, turns holding two, keeps a changed page in memory until a save, which streams the others from the old file and then reads every page from the new one; a damaged page is refused when read (\"Page damaged\") and kept as it is by a save, a cut file doesn't open; a recovery holds only the changed pages and finds the rest in the notebook's file; an interrupted save leaves the old file whole and readable");
    }

    static ToneDocument noise(int seed) {
        ToneDocument page = InkWashLayers.blank(SIDE, SIDE);
        page.selectLayer(InkWashLayers.INK); page.begin();
        Random r = new Random(seed); int[] row = new int[SIDE];
        for (int y = 0; y < SIDE; y++) { for (int x = 0; x < SIDE; x++) row[x] = r.nextInt(256); page.setTones(0, y, SIDE, 1, row); }
        page.finish();
        return page;
    }
    static void mark(ToneDocument page) {
        page.selectLayer(InkWashLayers.INK); page.begin();
        for (int x = 0; x < 20; x++) for (int y = 0; y < 20; y++) page.setTone(x, y, 0);
        page.finish();
    }
    /** A save as DocumentStore makes one: a new file beside, synced, then renamed into place with the pages moved to it. */
    static void save(DrawingBook book, File file, File dir) throws IOException {
        DrawingBook.Snapshot snapshot = book.snapshot();
        File temporary = new File(dir, "save.tmp");
        try (FileOutputStream out = new FileOutputStream(temporary)) {
            BufferedOutputStream buffered = new BufferedOutputStream(out, 1 << 20);
            BookCodec.write(buffered, snapshot); buffered.flush(); out.getFD().sync();
        }
        BookCodec.replaced(snapshot, file, () -> Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING));
        // As the app takes a save back (PaintActivity.saved): pages packed quickly now read the file's.
        book.savedPages(snapshot);
        book.markSaved(snapshot);
    }
    static long used() {
        Runtime r = Runtime.getRuntime();
        for (int i = 0; i < 3; i++) System.gc();
        return r.totalMemory() - r.freeMemory();
    }

    /** A damaged byte in a page: the notebook opens, that page is refused when read, a save keeps it as it is. */
    static void damage(File file, File dir) throws IOException {
        File copy = new File(dir, "damaged.mpaint");
        Files.copy(file.toPath(), copy.toPath(), StandardCopyOption.REPLACE_EXISTING);
        BookCodec.Pages pages = BookCodec.openPages(copy);
        long crc = pages.pages.get(20).crc;
        long at = pages.pages.get(20).offset() + 2000;
        try (RandomAccessFile f = new RandomAccessFile(copy, "rw")) { f.seek(at); int b = f.read(); f.seek(at); f.write(b ^ 0x40); }
        DrawingBook book = BookCodec.open(copy);
        int shown = book.index();
        try { book.select(20); throw new AssertionError("A damaged page was shown"); }
        catch (IOException expected) { check(expected.getMessage().contains("damaged"), "Refused as damaged: " + expected.getMessage()); }
        check(book.index() == shown, "A damaged page fails alone; the notebook stays on its page");
        book.select(21);
        mark(book.current()); book.select(0);
        save(book, copy, dir);
        BookCodec.Pages saved = BookCodec.openPages(copy);
        check(saved.pages.get(20).crc == crc, "A save keeps a damaged page as it was, its CRC with it");
        try { saved.pages.get(20).bytes(); throw new AssertionError("A save laundered a damaged page"); }
        catch (IOException expected) { }
        File cut = new File(dir, "cut.mpaint");
        byte[] all = Files.readAllBytes(file.toPath());
        for (long length : new long[]{all.length - 1, all.length - 22, all.length / 2, 100}) {
            Files.write(cut.toPath(), java.util.Arrays.copyOf(all, (int) length));
            try { BookCodec.open(cut); throw new AssertionError("A cut file opened at " + length); }
            catch (IOException expected) { }
        }
    }
    /** A recovery holds only the changed pages; the rest it finds in the notebook's file by length and CRC. */
    static void recovery(File dir) throws IOException {
        File file = new File(dir, "big.mpaint");
        DrawingBook book = BookCodec.open(file);
        book.select(30); mark(book.current());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        RecoveryCodec.write(out, book.snapshot(), "big");
        check(out.size() < 8L << 20, "A recovery of a 58 MB notebook holds only its changed page (" + (out.size() >> 10) + " KB)");
        RecoveryCodec.Recovered back = RecoveryCodec.read(new ByteArrayInputStream(out.toByteArray()), path -> new File(dir, path + ".mpaint"));
        check(back.book.count() == PAGES && back.book.index() == 30 && back.book.current().compositeTone(5, 5) == 0 && back.book.unsaved(), "It reads back with its change and every page");
        back.book.select(31);
        check(back.book.current().width == SIDE, "The others from the notebook's file");
        try { RecoveryCodec.read(new ByteArrayInputStream(out.toByteArray()), path -> new File(dir, "missing.mpaint")); throw new AssertionError("Read without its file"); }
        catch (IOException expected) { }
    }

    /** A save cut short: the old file whole, the book still reading from it. */
    static void interrupted(File file, File dir) throws IOException {
        DrawingBook book = BookCodec.open(file);
        byte[] before = Files.readAllBytes(file.toPath());
        book.select(5); mark(book.current()); book.select(6);
        File temporary = new File(dir, "cut.tmp");
        try (FileOutputStream out = new FileOutputStream(temporary)) {
            OutputStream cut = new FilterOutputStream(out) {
                long written;
                @Override public void write(byte[] b, int off, int len) throws IOException { if ((written += len) > 30 << 20) throw new IOException("Power cut"); out.write(b, off, len); }
            };
            BookCodec.write(cut, book.snapshot());
            throw new AssertionError("Not cut");
        } catch (IOException expected) { temporary.delete(); }
        check(java.util.Arrays.equals(before, Files.readAllBytes(file.toPath())), "An interrupted save leaves the file as it was");
        book.select(40);
        check(book.current().width == SIDE && book.packed(5).inMemory(), "and the book still reads from it, its change still held");
    }

    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
