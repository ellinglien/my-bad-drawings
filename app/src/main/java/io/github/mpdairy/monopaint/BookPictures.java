package io.github.mpdairy.monopaint;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.List;

/**
 * A book's notebook block: which page is its cover and a small picture of each page ({@link PagePicture}), kept in the
 * book's file between its last page and the ZIP's central directory, where readers that stream the ZIP (MonoPaint's and
 * every {@link BookCodec} before it) stop, as "INB1" isn't a local header, and the central directory lists only the
 * manifest and the pages as before. Layout: "INB1", version, cover, count, then for each page its entry's CRC-32 and its
 * picture's length (0: none), the pictures, and a trailer: the block's length before it and "INB1". A picture is used
 * only while its CRC matches its page's. Reading one is lenient: a block that doesn't read is no block. Pure: checked on
 * the desktop (docs/archive/superpowers/specs/2026-10-18-notebooks-design.md).
 */
final class BookPictures {
    static final int MAGIC = 0x494e4231; // INB1
    private static final int VERSION = 1;
    /** The block's own bytes besides its pictures, for {@code count} pages. */
    static int overhead(int count) { return 16 + 8 * count + 8; }

    /**
     * The block for pages whose entries have {@code crcs}, with {@code pictures} (null where a page has none), as many
     * as {@code room} bytes hold, the cover's first, then in page order; the rest are left out.
     */
    static byte[] block(int cover, long[] crcs, List<byte[]> pictures, long room) {
        int count = crcs.length;
        boolean[] kept = new boolean[count];
        long left = room - overhead(count);
        for (int pass = 0; pass < 2; pass++)
            for (int i = 0; i < count; i++) {
                if ((pass == 0) != (i == cover) || kept[i]) continue;
                byte[] picture = i < pictures.size() ? pictures.get(i) : null;
                if (picture != null && picture.length <= left) { kept[i] = true; left -= picture.length; }
            }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(MAGIC); out.writeInt(VERSION); out.writeInt(cover); out.writeInt(count);
            for (int i = 0; i < count; i++) { out.writeInt((int) crcs[i]); out.writeInt(kept[i] ? pictures.get(i).length : 0); }
            for (int i = 0; i < count; i++) if (kept[i]) out.write(pictures.get(i));
            int length = out.size();
            out.writeInt(length); out.writeInt(MAGIC);
            return bytes.toByteArray();
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }

    /** Whether {@code archive} (a book's file) ends its pages with a notebook block: a notebook, not an old drawing. */
    static boolean hasBlock(byte[] archive) {
        int end = archive.length - 22;
        if (end < 8 || readInt(archive, end) != 0x504b0506) return false;
        long directory = readLong32(archive, end + 16);
        return directory >= 8 && directory <= end && readInt(archive, (int) directory - 4) == MAGIC;
    }

    /** As {@link #hasBlock(byte[])}, read from {@code file}'s end. */
    static boolean hasBlock(File file) throws IOException {
        try (RandomAccessFile in = new RandomAccessFile(file, "r")) {
            long size = in.length();
            if (size < 30) return false;
            byte[] eocd = new byte[22]; in.seek(size - 22); in.readFully(eocd);
            if (readInt(eocd, 0) != 0x504b0506) return false;
            long directory = readLong32(eocd, 16);
            if (directory < 8 || directory > size - 22) return false;
            byte[] magic = new byte[4]; in.seek(directory - 4); in.readFully(magic);
            return readInt(magic, 0) == MAGIC;
        }
    }

    /** What a block holds: the cover, and each page's picture where its CRC matched (else null). */
    static final class Found {
        final int cover;
        final byte[][] pictures;
        Found(int cover, byte[][] pictures) { this.cover = cover; this.pictures = pictures; }
    }

    /**
     * The block ending at {@code end} of {@code archive} (the central directory's offset), for pages whose entries have
     * {@code crcs}; null when there is none or it doesn't read.
     */
    static Found parse(byte[] archive, long end, long[] crcs) {
        if (end < 8 || end > archive.length) return null;
        int at = (int) end;
        if (readInt(archive, at - 4) != MAGIC) return null;
        long length = readInt(archive, at - 8) & 0xffffffffL;
        long start = at - 8 - length;
        if (start < 0 || length < 16) return null;
        return parse(archive, (int) start, (int) length, crcs);
    }

    /** Where the block ending at {@code end} of {@code archive} starts, or {@code end} when there is none there (what may lie before it: {@link BookOptions}). */
    static long start(byte[] archive, long end) {
        if (end < 8 || end > archive.length || readInt(archive, (int) end - 4) != MAGIC) return end;
        long length = readInt(archive, (int) end - 8) & 0xffffffffL, start = end - 8 - length;
        return start < 0 || length < 16 ? end : start;
    }

    private static Found parse(byte[] block, int start, int length, long[] crcs) {
        int at = start, end = start + length;
        if (readInt(block, at) != MAGIC || readInt(block, at + 4) != VERSION) return null;
        int cover = readInt(block, at + 8), count = readInt(block, at + 12);
        if (count != crcs.length || count < 1 || count > DrawingBook.MAX_PAGES || length < overhead(count) - 8) return null;
        if (cover < 0 || cover >= count) cover = 0;
        at += 16;
        int data = at + 8 * count;
        byte[][] pictures = new byte[count][];
        for (int i = 0; i < count; i++, at += 8) {
            long crc = readInt(block, at) & 0xffffffffL;
            int size = readInt(block, at + 4);
            if (size < 0 || data + (long) size > end) return null;
            if (size > 0 && crc == crcs[i]) pictures[i] = java.util.Arrays.copyOfRange(block, data, data + size);
            data += size;
        }
        if (data != end) return null;
        return new Found(cover, pictures);
    }


    /**
     * What the shelf shows of a book's file without reading a page: its page count, its pages' size, the cover picture's
     * page (-1: none) and that page's picture, and its front cover (Inkwash's covers, {@link BookOptions}).
     */
    static final class Cover {
        final int pages, cover;
        /** The cover picture's page's picture, or null when the file has none for it (made from the page instead). */
        final byte[] picture;
        /** The pages' size, from the manifest (0: not known, an older one-page file). */
        final int width, height;
        /** Whether something is drawn on the front cover, and its picture of its marks alone (null: none kept). */
        final boolean frontDrawn;
        final byte[] front;
        /** The covers' design (the default without an options block, {@link BookOptions#design}). */
        final CoverDesign design;
        /** The notebook's other options (template, tools), the defaults without a record. */
        final NotebookOptions options;
        /** Inkwash: the pages the notebook says it has: its stacks, a layer a page stacked on the one below ({@link PageStacks}). */
        final int stacks;
        Cover(int pages, int cover, byte[] picture, int width, int height, boolean frontDrawn, byte[] front, CoverDesign design) { this(pages, cover, picture, width, height, frontDrawn, front, design, NotebookOptions.DEFAULT, pages); }
        Cover(int pages, int cover, byte[] picture, int width, int height, boolean frontDrawn, byte[] front, CoverDesign design, NotebookOptions options, int stacks) {
            this.stacks = stacks;
            this.pages = pages; this.cover = cover; this.picture = picture; this.width = width; this.height = height; this.frontDrawn = frontDrawn; this.front = front;
            this.design = design; this.options = options;
        }
    }

    /** Files' covers read lately, by path, with the length and time they were read at; the most kept. */
    private static final class Read {
        final long length, time; final Cover cover;
        Read(long length, long time, Cover cover) { this.length = length; this.time = time; this.cover = cover; }
    }
    private static final java.util.Map<String, Read> READ = new java.util.LinkedHashMap<String, Read>(16, .75f, true) {
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<String, Read> eldest) { return size() > 64; }
    };
    /** How many covers {@link #cover} has read from their files (the checks'). */
    static final java.util.concurrent.atomic.AtomicInteger READS = new java.util.concurrent.atomic.AtomicInteger();
    /**
     * {@link #readCover}, read again only when the file changed (its length or time) since it was last read: the shelf's,
     * each time it shows. The same file unchanged gives the same {@link Cover}, its pictures the same arrays.
     */
    static Cover cover(File file) throws IOException {
        String path = file.getPath();
        long length = file.length(), time = file.lastModified();
        synchronized (READ) {
            Read kept = READ.get(path);
            if (kept != null && kept.length == length && kept.time == time) return kept.cover;
        }
        Cover read = readCover(file);
        READS.incrementAndGet();
        synchronized (READ) { READ.put(path, new Read(length, time, read)); }
        return read;
    }

    /**
     * {@code file}'s page count, page size, cover picture and front cover, read from its end and its start: the ZIP's end
     * record, the central directory, the notebook block and the options block before it, and the manifest's first bytes.
     * Without an options block (a notebook from before covers) the cover picture is the notebook block's cover unless it
     * is the first page (everyone's then). An older single-page file (TSM, not a ZIP) has one page and no picture.
     */
    static Cover readCover(File file) throws IOException {
        try (RandomAccessFile in = new RandomAccessFile(file, "r")) {
            long size = in.length();
            if (size < 4) throw new IOException("Invalid painting file");
            byte[] magic = new byte[4]; in.readFully(magic);
            if (magic[0] == 'T' && magic[1] == 'S' && magic[2] == 'M') return new Cover(1, -1, null, 0, 0, false, null, CoverDesign.DEFAULT);
            if (size < 22) throw new IOException("Incomplete painting archive");
            byte[] eocd = new byte[22]; in.seek(size - 22); in.readFully(eocd);
            if (readInt(eocd, 0) != 0x504b0506) throw new IOException("Incomplete painting archive");
            int entries = readShort(eocd, 10);
            long directorySize = readLong32(eocd, 12), directory = readLong32(eocd, 16);
            int pages = entries - 1;
            if (pages < 1 || pages > DrawingBook.MAX_PAGES || directory + directorySize > size - 22 || directorySize > 65536)
                throw new IOException("Invalid page directory");
            byte[] central = new byte[(int) directorySize]; in.seek(directory); in.readFully(central);
            long[] crcs = new long[pages];
            for (int at = 0, n = 0; at + 46 <= central.length && n < entries; n++) {
                if (readInt(central, at) != 0x504b0102) throw new IOException("Invalid page directory");
                int nameLength = readShort(central, at + 28), extra = readShort(central, at + 30), comment = readShort(central, at + 32);
                if (at + 46 + nameLength > central.length) throw new IOException("Invalid page directory");
                String name = new String(central, at + 46, nameLength, java.nio.charset.StandardCharsets.UTF_8);
                if (name.startsWith("pages/") && name.endsWith(".tsm")) {
                    try {
                        int page = Integer.parseInt(name.substring(6, name.length() - 4)) - 1;
                        if (page >= 0 && page < pages) crcs[page] = readLong32(central, at + 16);
                    } catch (NumberFormatException ignored) { }
                }
                at += 46 + nameLength + extra + comment;
            }
            // The pages' size: the manifest is the first entry (a local header of 30, "manifest", then its magic, width, height).
            int width = 0, height = 0;
            byte[] head = new byte[30 + 8 + 12]; in.seek(0); in.readFully(head);
            if (readInt(head, 0) == 0x504b0304 && readShort(head, 26) == 8) { width = readInt(head, 42); height = readInt(head, 46); }
            if (!ToneDocument.validSize(width, height)) { width = 0; height = 0; }
            // The notebook block: its cover and its table of pictures.
            long blockStart = directory, tableAt = 0;
            int blockCover = 0;
            byte[] table = null;
            if (directory >= 8) {
                byte[] trailer = new byte[8]; in.seek(directory - 8); in.readFully(trailer);
                long length = readInt(trailer, 0) & 0xffffffffL;
                if (readInt(trailer, 4) == MAGIC && length >= 16 && length <= directory - 8) {
                    blockStart = directory - 8 - length;
                    byte[] top = new byte[16]; in.seek(blockStart); in.readFully(top);
                    if (readInt(top, 0) == MAGIC && readInt(top, 4) == VERSION && readInt(top, 12) == pages && length >= overhead(pages) - 8) {
                        blockCover = readInt(top, 8);
                        if (blockCover < 0 || blockCover >= pages) blockCover = 0;
                        table = new byte[8 * pages]; in.readFully(table);
                        tableAt = blockStart + 16;
                    }
                }
            }
            // Inkwash: the options block before it: the cover picture's page and the front cover.
            BookOptions.Found options = BookOptions.find(BookOptions.of(in), 0, blockStart);
            CoverDesign design = BookOptions.design(options, BookOptions.of(in));
            NotebookOptions notebookOptions = BookOptions.options(options, BookOptions.of(in));
            int cover = blockCover > 0 ? blockCover : -1;
            boolean frontDrawn = false;
            byte[] front = null;
            if (options != null) {
                cover = -1;
                BookOptions.Record page = options.get(BookOptions.FRONT), marks = options.get(BookOptions.FRONT_PICTURE), pictured = options.get(BookOptions.COVER_PICTURE);
                frontDrawn = page != null;
                if (page != null && marks != null && page.length >= 4 && marks.length > 4) {
                    byte[] crc = new byte[4], shows = new byte[4];
                    in.seek(page.offset); in.readFully(crc); in.seek(marks.offset); in.readFully(shows);
                    if (readInt(crc, 0) == readInt(shows, 0)) { front = new byte[marks.length - 4]; in.readFully(front); }
                }
                if (pictured != null && pictured.length == 4) {
                    byte[] p = new byte[4]; in.seek(pictured.offset); in.readFully(p);
                    cover = readInt(p, 0);
                    if (cover < 0 || cover >= pages) cover = -1;
                }
            }
            byte[] picture = null;
            if (cover >= 0 && table != null) {
                long data = tableAt + table.length;
                for (int i = 0; i < cover; i++) data += readInt(table, 8 * i + 4) & 0xffffffffL;
                int pictureSize = readInt(table, 8 * cover + 4);
                long crc = readInt(table, 8 * cover) & 0xffffffffL;
                if (pictureSize > 0 && crc == crcs[cover] && data + pictureSize <= directory - 8) { picture = new byte[pictureSize]; in.seek(data); in.readFully(picture); }
            }
            return new Cover(pages, cover, picture, width, height, frontDrawn, front, design, notebookOptions, BookOptions.stacks(options, BookOptions.of(in), pages));
        }
    }

    static int readInt(byte[] b, int at) {
        if (at < 0 || at + 4 > b.length) return 0;
        return (b[at] & 255) << 24 | (b[at + 1] & 255) << 16 | (b[at + 2] & 255) << 8 | (b[at + 3] & 255);
    }
    /** A ZIP's little-endian 16-bit field. */
    static int readShort(byte[] b, int at) { return (b[at] & 255) | (b[at + 1] & 255) << 8; }
    /** A ZIP's little-endian 32-bit field, unsigned. */
    static long readLong32(byte[] b, int at) { return (readShort(b, at) | (long) readShort(b, at + 2) << 16) & 0xffffffffL; }

    private BookPictures() {}
}
