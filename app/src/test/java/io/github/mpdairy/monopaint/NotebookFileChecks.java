package io.github.mpdairy.monopaint;

import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.zip.CRC32;

/**
 * Notebooks in files (docs/archive/superpowers/specs/2026-10-18-notebooks-design.md): page pictures, the notebook block written
 * and read, ignored by ZIP readers, kept within the old readers' size limit, read from the file's end for the shelf;
 * opening without inflating every page, damage still refused. {@code write <file>} writes a notebook for the older
 * reader's check ({@code OlderNotebookCheck}).
 */
public final class NotebookFileChecks {
    public static void main(String[] args) throws Exception {
        if (args.length == 2 && args[0].equals("write")) { Files.write(new File(args[1]).toPath(), sample()); return; }
        pictures();
        written();
        followsPages();
        moved();
        sizeLimit();
        cover();
        damage();
        lazy();
        recovery();
        System.out.println("PASS: notebooks in files: page pictures average their page at 16 greys, never the masking fluid, encode small and refuse garbage; the notebook block (cover and pictures) is written after the last page and read back, ZIP readers see only the manifest and pages, a picture whose CRC doesn't match its page is ignored, pictures move with their pages and go once a page changes, the cover follows its page; a moved page takes its bytes, picture, canvas choice and the cover with it through save, recovery and reopening, its PNG kept; pictures that don't fit the block's room are left out, the cover's kept first; the shelf reads a cover from the file's end; opening checks every page's bytes and header but decodes only the shown page, a page that can't decode fails alone and is kept; a damaged byte is still refused; the recovery file keeps them");
    }

    /** A 3-page notebook, pictures on pages 1 and 3, the cover page 3. */
    static byte[] sample() throws IOException {
        DrawingBook book = book(3, 64, 80);
        book.offerPicture(book.pictureKey(2), picture(book, 2));
        book.select(0);
        book.offerPicture(book.pictureKey(0), picture(book, 0));
        book.setCover(2);
        return bytes(book.snapshot());
    }
    static DrawingBook book(int pages, int width, int height) throws IOException {
        ToneDocument first = InkWashLayers.blank(width, height);
        DrawingBook book = new DrawingBook(first);
        for (int i = 0; i < pages; i++) {
            if (i > 0) book.addPage();
            mark(book.current(), i * 5, 10 + i, 30 + 40 * i);
        }
        return book;
    }
    static void mark(ToneDocument page, int x, int y, int tone) {
        page.begin(); for (int dx = 0; dx < 8; dx++) for (int dy = 0; dy < 8; dy++) page.setTone(x + dx, y + dy, tone); page.finish();
    }
    static byte[] picture(DrawingBook book, int i) { return PagePicture.of(book.current().layerSnapshot()).encode(); }
    static byte[] bytes(DrawingBook.Snapshot snapshot) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); BookCodec.write(out, snapshot); return out.toByteArray();
    }

    static void pictures() throws IOException {
        ToneDocument page = InkWashLayers.blank(1920, 2400);
        page.selectLayer(InkWashLayers.INK);
        page.begin(); for (int x = 0; x < 4; x++) for (int y = 0; y < 4; y++) page.setTone(x, y, 0); page.setTone(4, 0, 0); page.finish();
        page.beginFluid(1); for (int x = 100; x < 200; x++) page.setTone(x, 100, 0); page.finish();
        PagePicture p = PagePicture.of(page.layerSnapshot());
        check(p.width == 480 && p.height == 600, "A 1920 × 2400 page's picture is 480 × 600");
        check(p.tone(0, 0) == 0 && p.tone(1, 0) == GrayPalette.VALUES[14], "Each picture pixel is its 4 × 4 block's average, at the panel's nearest grey (" + p.tone(1, 0) + ")");
        check(p.tone(30, 25) == 255, "The masking fluid is never in the picture");
        byte[] e = p.encode();
        PagePicture q = PagePicture.decode(e);
        boolean same = q.width == p.width && q.height == p.height;
        for (int y = 0; y < p.height && same; y++) for (int x = 0; x < p.width; x++) same &= q.tone(x, y) == p.tone(x, y);
        check(same && e.length < 4000, "A picture encodes and decodes exactly, a blank one in a few KB (" + e.length + " bytes)");
        check(PagePicture.size(749, 998)[1] == 600 && PagePicture.size(300, 200)[0] == 300, "Long side 600, never larger than the page");
        int[] shown = q.pixels(120, 150), big = q.pixels(1500, 1875);
        check(shown.length == 120 * 150 && big.length == 1500 * 1875 && (shown[0] & 255) < 255, "Shown smaller (averaged) or larger (blended)");
        for (byte[] bad : new byte[][]{{}, {2, 0, 1, 0, 1}, Arrays.copyOf(e, e.length - 3), {1, 0x7f, 0x7f, 0x7f, 0x7f}}) {
            try { PagePicture.decode(bad); throw new AssertionError("Garbage read as a picture"); }
            catch (IOException expected) { }
        }
    }

    static void written() throws Exception {
        byte[] file = sample();
        DrawingBook read = BookCodec.read(new ByteArrayInputStream(file));
        check(read.count() == 3 && read.cover() == 2, "The cover is read back");
        check(read.picture(0) != null && read.picture(1) == null && read.picture(2) != null, "Pictures are read back where they were saved");
        check(PagePicture.decode(read.picture(2)).tone(12, 14) < 255, "The picture shows its page");
        // ZIP readers see only the manifest and the pages.
        ArrayList<String> streamed = new ArrayList<>();
        try (java.util.zip.ZipInputStream zip = new java.util.zip.ZipInputStream(new ByteArrayInputStream(file))) {
            for (java.util.zip.ZipEntry e; (e = zip.getNextEntry()) != null; ) streamed.add(e.getName());
        }
        File temp = File.createTempFile("notebook", ".mpaint"); temp.deleteOnExit();
        Files.write(temp.toPath(), file);
        ArrayList<String> listed = new ArrayList<>();
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(temp)) {
            for (java.util.Enumeration<? extends java.util.zip.ZipEntry> e = zip.entries(); e.hasMoreElements(); ) {
                java.util.zip.ZipEntry entry = e.nextElement(); listed.add(entry.getName());
                try (InputStream in = zip.getInputStream(entry)) { while (in.read() != -1) { } }
            }
        }
        java.util.List<String> expected = Arrays.asList("manifest", "pages/1.tsm", "pages/2.tsm", "pages/3.tsm");
        check(streamed.equals(expected) && listed.equals(expected), "ZipInputStream and ZipFile see the manifest and pages only: " + streamed + " " + listed);
        check(Arrays.equals(file, bytes(read.snapshot())), "Writing what was read gives the same bytes");
        // A stale picture (its page's CRC differs) is ignored.
        DrawingBook other = BookCodec.read(new ByteArrayInputStream(file));
        other.select(0); mark(other.current(), 40, 40, 0);
        byte[] changed = bytes(other.snapshot());
        // Put the old block (pictures for the old page 1) on the changed pages.
        byte[] spliced = splice(changed, block(file));
        DrawingBook stale = BookCodec.read(new ByteArrayInputStream(spliced));
        check(stale.picture(0) == null && stale.picture(2) != null, "A picture whose CRC doesn't match its page is ignored, the others kept");
        // An older file without the block reads with no pictures.
        check(BookCodec.read(new ByteArrayInputStream(withoutBlock(file))).picture(2) == null, "A file without a block has no pictures");
    }

    /** The notebook block in {@code file}. */
    static byte[] block(byte[] file) {
        int end = file.length - 22;
        int directory = (int) BookPictures.readLong32(file, end + 16);
        int length = BookPictures.readInt(file, directory - 8);
        return Arrays.copyOfRange(file, directory - 8 - length, directory);
    }
    /** {@code file} with its block replaced by {@code block}, the central directory's offsets moved to match. */
    static byte[] splice(byte[] file, byte[] block) {
        byte[] old = block(file);
        int end = file.length - 22, directory = (int) BookPictures.readLong32(file, end + 16), start = directory - old.length;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(file, 0, start); out.write(block, 0, block.length); out.write(file, directory, file.length - directory);
        byte[] b = out.toByteArray();
        int moved = directory - old.length + block.length;
        int e = b.length - 22;
        b[e + 16] = (byte) moved; b[e + 17] = (byte) (moved >> 8); b[e + 18] = (byte) (moved >> 16); b[e + 19] = (byte) (moved >> 24);
        return b;
    }
    static byte[] withoutBlock(byte[] file) { return splice(file, new byte[0]); }

    static void followsPages() throws Exception {
        DrawingBook book = book(4, 32, 40);
        for (int i = 0; i < 4; i++) { book.select(i); check(book.offerPicture(book.pictureKey(i), new byte[]{(byte) i}), "A picture of the page as it is is taken"); }
        book.select(1);
        DrawingBook.PictureKey before = book.pictureKey(1);
        mark(book.current(), 0, 0, 0);
        check(book.picture(1) == null, "A page's picture goes once the page changes");
        check(!book.offerPicture(before, new byte[]{9}), "A picture of how the page was isn't taken");
        book.offerPicture(book.pictureKey(1), new byte[]{11});
        book.select(3); book.select(0); book.select(2);
        check(book.picture(1)[0] == 11 && book.picture(0)[0] == 0 && book.picture(3)[0] == 3, "Pictures stay with pages turned away from and back to");
        book.select(3); mark(book.current(), 1, 1, 0); book.select(0); book.select(1);
        check(book.picture(3) == null, "A page changed and left loses its picture");
        book.setCover(2); book.select(0); book.deletePage();
        check(book.count() == 3 && book.picture(0)[0] == 11 && book.picture(1)[0] == 2 && book.cover() == 1, "A deleted page's picture goes, the rest move with their pages, the cover too");
        book.select(1); book.duplicatePage();
        check(book.count() == 4 && book.picture(2)[0] == 2 && book.cover() == 1, "A duplicated page shares its picture");
        book.select(1); book.deletePage();
        // The copy is a layer stacked on it (Stage 3c): the stack stays, its new bottom the picture.
        check(book.cover() == 1, "Deleting the bottom layer of the cover picture's page: the layer on it keeps the picture");
        book.setCover(-1);
        book.addPage();
        check(book.picture(book.index()) == null && book.unsaved(), "A new page has no picture");
        DrawingBook saved = BookCodec.read(new ByteArrayInputStream(bytes(book.snapshot())));
        saved.select(1);
        check(saved.picture(1)[0] == 2 && saved.cover() == -1, "Pictures survive a save and reopening");
        DrawingBook covered = book(2, 32, 40);
        DrawingBook.Snapshot clean = covered.snapshot(); covered.markSaved(clean);
        covered.setCover(1);
        check(covered.unsaved(), "Setting the cover is a change to save");
    }

    /**
     * A page moved (the page bar's lift, docs/archive/superpowers/specs/2026-10-20-tracing-paper-design.md): its bytes, picture,
     * canvas choice and the cover picture go with it, the page drawn on stays the one drawn on; it is a change to save,
     * and survives a save, the recovery file and reopening; its PNG copy is kept under its new number, nothing drawn.
     */
    static void moved() throws Exception {
        DrawingBook book = book(5, 32, 40);
        for (int i = 0; i < 5; i++) { book.select(i); book.setCanvasChoice(i % 3); book.offerPicture(book.pictureKey(i), new byte[]{(byte) i}); }
        book.select(1); book.setCover(3);
        java.util.List<PackedPage> before = book.snapshot().filed();
        DrawingBook.Snapshot clean = book.snapshot(); book.markSaved(clean);
        ToneDocument drawing = book.current();
        book.move(3, 0);
        int[] was = {3, 0, 1, 2, 4};
        java.util.List<PackedPage> after = book.snapshot().filed();
        for (int i = 0; i < 5; i++) {
            check(book.picture(i) != null && book.picture(i)[0] == was[i], "Page " + (i + 1) + "'s picture moved with it");
            check(after.get(i).crc == before.get(was[i]).crc, "Page " + (i + 1) + "'s bytes moved with it, unchanged");
            check(book.snapshot().canvasChoices.get(i) == was[i] % 3, "Page " + (i + 1) + "'s canvas choice moved with it");
        }
        check(book.cover() == 0 && book.index() == 2 && book.current() == drawing && book.unsaved(), "The cover picture and the page drawn on follow their pages; a change to save");
        book.move(4, 2);
        check(book.index() == 3 && book.current() == drawing && book.picture(2)[0] == 4, "Moving another page over the one drawn on shifts it");
        book.move(2, 4);
        DrawingBook reopened = BookCodec.read(new ByteArrayInputStream(bytes(book.snapshot())));
        check(reopened.count() == 5 && reopened.cover() == 0 && reopened.index() == 2, "A moved notebook saves and reopens on the page drawn on, the cover picture's page kept");
        java.util.List<PackedPage> filed = reopened.snapshot().filed();
        for (int i = 0; i < 5; i++) {
            check(filed.get(i).crc == before.get(was[i]).crc && reopened.snapshot().canvasChoices.get(i) == was[i] % 3, "Page " + (i + 1) + " reopened as moved");
            if (i != reopened.index()) check(reopened.picture(i) != null && reopened.picture(i)[0] == was[i], "Page " + (i + 1) + "'s picture reopened with it");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        RecoveryCodec.write(out, book.snapshot(), "Notebook");
        DrawingBook recovered = RecoveryCodec.read(new ByteArrayInputStream(out.toByteArray()), path -> null).book;
        java.util.List<PackedPage> kept = recovered.snapshot().filed();
        for (int i = 0; i < 5; i++) check(kept.get(i).crc == before.get(was[i]).crc, "Page " + (i + 1) + " recovered as moved");
        check(recovered.cover() == 0 && recovered.index() == 2, "The recovery file keeps the move");
        // The PNG copies: a move draws nothing, each PNG kept under its page's new number.
        File folder = Files.createTempDirectory("moved").toFile();
        try {
            check(NotebookExport.update(folder, "Moved", before).drawn == 5, "Every page drawn the first time");
            NotebookExport.Done again = NotebookExport.update(folder, "Moved", after);
            check(again.drawn == 0 && again.kept == 5, "A move draws no page again: " + again.drawn);
            for (int i = 0; i < 5; i++)
                check(NotebookExport.read(Files.readAllBytes(new File(folder, "Moved/" + NotebookExport.pageName(i)).toPath())).crc == crc(after.get(i).bytes()), "PNG " + (i + 1) + " is its page");
        } finally { NotebookMigrationChecks.delete(folder); }
        try { book.move(0, 5); throw new AssertionError("A move past the last page was taken"); } catch (IllegalArgumentException expected) { }
    }

    static void sizeLimit() throws Exception {
        // The block holds as many pictures as its room does, the cover's first.
        byte[] big = new byte[30000];
        java.util.List<byte[]> pictures = Arrays.asList(fill(5, big));
        byte[] block = BookPictures.block(3, new long[]{1, 2, 3, 4, 5}, pictures, BookPictures.overhead(5) + 2 * 30000 + 10);
        BookPictures.Found found = BookPictures.parse(block, block.length, new long[]{1, 2, 3, 4, 5});
        int kept = 0; for (byte[] p : found.pictures) if (p != null) kept++;
        check(found.pictures[3] != null && found.pictures[0] != null && kept == 2, "Pictures that don't fit are left out, the cover's kept first (" + kept + " kept)");
        check(BookCodec.MAX_ARCHIVE_BYTES >= DrawingBook.MAX_PACKED_BYTES && DrawingBook.MAX_PACKED_BYTES == 200 << 20 && DrawingBook.MAX_PAGES == 250,
                "A notebook holds 250 pages and 200 MB of them");
    }
    static byte[][] fill(int n, byte[] picture) { byte[][] all = new byte[n][]; Arrays.fill(all, picture); return all; }

    static void cover() throws Exception {
        File dir = Files.createTempDirectory("covers").toFile(); dir.deleteOnExit();
        File notebook = new File(dir, "a.mpaint"); Files.write(notebook.toPath(), sample());
        BookPictures.Cover c = BookPictures.readCover(notebook);
        DrawingBook read = BookCodec.read(new ByteArrayInputStream(sample()));
        check(c.pages == 3 && c.cover == 2 && Arrays.equals(c.picture, read.picture(2)), "The shelf reads the page count, cover and its picture from the file's end");
        File plain = new File(dir, "b.mpaint"); Files.write(plain.toPath(), withoutBlock(sample()));
        BookPictures.Cover p = BookPictures.readCover(plain);
        check(p.pages == 3 && p.cover == 2 && p.picture == null, "A file without its block of pictures: its pages and its cover picture's page (the options block's), no picture");
        File legacy = new File(dir, "c.mpaint");
        try (OutputStream out = new FileOutputStream(legacy)) { DocumentCodec.write(out, InkWashLayers.blank(20, 20).layerSnapshot()); }
        BookPictures.Cover l = BookPictures.readCover(legacy);
        check(l.pages == 1 && l.picture == null, "An older one-page file has one page and no picture");
        DrawingBook stale = BookCodec.read(new ByteArrayInputStream(sample()));
        stale.select(2); mark(stale.current(), 3, 3, 0);
        File changed = new File(dir, "d.mpaint"); Files.write(changed.toPath(), splice(bytes(stale.snapshot()), block(sample())));
        check(BookPictures.readCover(changed).picture == null, "A cover's stale picture isn't shown");
        for (File f : new File[]{notebook, plain, legacy, changed}) f.delete();
    }

    static void damage() throws Exception {
        byte[] file = sample();
        int end = file.length - 22, directory = (int) BookPictures.readLong32(file, end + 16);
        int blockStart = directory - block(file).length, optionsStart = (int) BookOptions.find(BookOptions.of(file), 0, blockStart).start;
        // A damaged byte in any page is refused, as before (the ZIP's CRC-32); the options block (the cover picture here) lies between.
        for (int at = 0; at < optionsStart; at += 97) {
            byte[] broken = file.clone(); broken[at] ^= 1;
            try { BookCodec.read(new ByteArrayInputStream(broken)); throw new AssertionError("Damage at " + at + " was read"); }
            catch (IOException expected) { }
        }
        // A damaged block never stops the notebook opening; it only loses pictures.
        for (int at = blockStart; at < directory; at += 7) {
            byte[] broken = file.clone(); broken[at] ^= 0x10;
            DrawingBook book = BookCodec.read(new ByteArrayInputStream(broken));
            check(book.count() == 3, "A damaged block still opens the notebook");
        }
        for (int length : new int[]{file.length - 1, file.length - 22, blockStart + 3}) {
            try { BookCodec.read(new ByteArrayInputStream(Arrays.copyOf(file, length))); throw new AssertionError("Cut file read"); }
            catch (IOException expected) { }
        }
    }

    static void lazy() throws Exception {
        DrawingBook book = book(3, 40, 30);
        DrawingBook.Snapshot snapshot = book.snapshot();
        byte[] file = bytes(snapshot);
        // Page 2's deflated data damaged but its CRC-32 made to match: a page only its decoding can refuse.
        byte[] bad = snapshot.written.get(1).bytes().clone();
        bad[bad.length - 9] ^= 0x55;
        ArrayList<byte[]> pages = new ArrayList<>(); for (PackedPage p : snapshot.written) pages.add(p.bytes()); pages.set(1, bad);
        DrawingBook mixed = new DrawingBook(40, 30, pages, 2);
        byte[] withBad = bytes(mixed.snapshot());
        DrawingBook opened = BookCodec.read(new ByteArrayInputStream(withBad));
        check(opened.count() == 3 && opened.index() == 2, "A notebook opens without decoding its other pages");
        try { opened.select(1); throw new AssertionError("A page that can't decode was shown"); }
        catch (IOException expected) { }
        check(opened.index() == 2 && opened.current().compositeTone(12, 13) < 255, "A page that can't decode fails alone; the notebook stays on its page");
        opened.select(0);
        check(opened.current().compositeTone(1, 11) < 255, "The other pages still turn");
        DrawingBook.Snapshot again = opened.snapshot();
        bytes(again);
        check(Arrays.equals(again.written.get(1).bytes(), bad), "The undecodable page is kept byte for byte");
        try { DocumentCodec.checkHeader(new byte[]{'X', 'S', 'M', '3', 0, 0, 0, 1, 0, 0, 0, 1}, 0, 0); throw new AssertionError("Unknown header passed"); }
        catch (IOException expected) { }
        byte[] head = Arrays.copyOf(snapshot.written.get(0).bytes(), 12);
        try { DocumentCodec.checkHeader(head, 41, 30); throw new AssertionError("Wrong size passed"); }
        catch (IOException expected) { }
        DocumentCodec.checkHeader(head, 40, 30);
        check(file.length > 0, "Headers checked as read would");
    }

    static void recovery() throws Exception {
        DrawingBook book = BookCodec.read(new ByteArrayInputStream(sample()));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        RecoveryCodec.write(out, book.snapshot(), "Notebook");
        RecoveryCodec.Recovered back = RecoveryCodec.read(new ByteArrayInputStream(out.toByteArray()), path -> null);
        check(back.book.cover() == 2 && back.book.picture(2) != null && back.path.equals("Notebook"), "The recovery file keeps the cover and pictures");
        // Pages kept by reference to the notebook's file: a file renamed away or changed elsewhere reads as FileChanged.
        java.io.File saved = java.io.File.createTempFile("notebook", ".mpaint"), other = java.io.File.createTempFile("other", ".mpaint");
        java.nio.file.Files.write(saved.toPath(), sample());
        ByteArrayOutputStream ref = new ByteArrayOutputStream();
        RecoveryCodec.write(ref, BookCodec.open(saved).snapshot(), "Notebook");
        check(RecoveryCodec.read(new ByteArrayInputStream(ref.toByteArray()), path -> saved).book.count() == book.count(), "A recovery reads its pages from the notebook's file");
        // Another notebook under the name: only the first page of the saved one.
        try (java.io.FileOutputStream changed = new java.io.FileOutputStream(other)) {
            BookCodec.write(changed, book.width, book.height, java.util.List.of(BookCodec.openPages(saved).pages.get(0)), 0, new int[]{0}, -1, Arrays.asList((byte[]) null));
        }
        for (java.io.File file : new java.io.File[]{other, new java.io.File(saved.getPath() + ".gone")}) {
            try { RecoveryCodec.read(new ByteArrayInputStream(ref.toByteArray()), path -> file); throw new AssertionError("A changed file passed"); }
            catch (RecoveryCodec.FileChanged expected) { check(expected.path.equals("Notebook"), "FileChanged names the notebook"); }
        }
        saved.delete(); other.delete();
    }

    static long crc(byte[] bytes) { CRC32 c = new CRC32(); c.update(bytes); return c.getValue(); }
    static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
