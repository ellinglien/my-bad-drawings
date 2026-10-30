package io.github.mpdairy.monopaint;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.zip.CRC32;

/**
 * A page's packed bytes ({@link DocumentCodec}): held in memory (a page made or changed since its book was saved), or a
 * stored entry of its book's file, read when needed and checked against its CRC-32 every time, so a book needn't hold
 * its pages in memory (docs/archive/superpowers/specs/2026-10-18-notebooks-design.md, "Pages read from the file"). A save
 * copies a stored page from the old file into the new one as it is, its CRC with it, and then {@link #storedIn} points
 * every page it wrote into the new file, in-memory ones too, freeing their memory. One lock over all of them
 * ({@link #LOCK}): a save holds it from renaming the new file into place until every page points into it, so no read
 * meets the old file once it is gone. Pure Java, safe from any thread.
 */
final class PackedPage {
    /** Held while a page is read, copied or moved to another file. */
    static final Object LOCK = new Object();
    /** The bytes' length and their CRC-32, as the ZIP's entry records them. */
    final int length;
    final long crc;
    /**
     * Inkwash: packed quickly ({@link DocumentCodec#QUICK}) for memory and the recovery file only, while the page is
     * worked on: a book's file holds it packed at the default level, as every page always was ({@code DrawingBook.Snapshot#filed}).
     */
    final boolean quick;
    private byte[] bytes;
    private Source file;
    private long offset;

    private PackedPage(byte[] bytes, Source file, long offset, int length, long crc, boolean quick) {
        this.bytes = bytes; this.file = file; this.offset = offset; this.length = length; this.crc = crc; this.quick = quick;
    }
    /** A page held in memory. */
    static PackedPage of(byte[] bytes) { return of(bytes, false); }
    /** A page held in memory, {@link #quick} or not. */
    static PackedPage of(byte[] bytes, boolean quick) {
        CRC32 c = new CRC32(); c.update(bytes);
        return new PackedPage(bytes, null, 0, bytes.length, c.getValue(), quick);
    }
    /** A page stored in {@code file}: {@code length} bytes at {@code offset} whose CRC-32 is {@code crc}. */
    static PackedPage in(Source file, long offset, int length, long crc) { return new PackedPage(null, file, offset, length, crc, false); }

    /** The bytes: from memory, or read from the file and checked ("Page damaged" when they don't match their CRC). */
    byte[] bytes() throws IOException {
        synchronized (LOCK) {
            if (bytes != null) return bytes;
            byte[] read = file.read(offset, length);
            CRC32 c = new CRC32(); c.update(read);
            if (c.getValue() != crc) throw new IOException("Page damaged");
            return read;
        }
    }
    /** Writes the bytes to {@code out} as they are (a stored page's straight from its file, its CRC unchecked: a damaged one stays as damaged, never re-checksummed). */
    void copyTo(OutputStream out) throws IOException {
        synchronized (LOCK) {
            if (bytes != null) { out.write(bytes, 0, bytes.length); return; }
            file.copy(offset, length, out);
        }
    }
    /** Whether the bytes are held in memory. */
    boolean inMemory() { synchronized (LOCK) { return bytes != null; } }
    /** Where in its file it is stored. */
    long offset() { synchronized (LOCK) { return offset; } }
    /** The file it is stored in, or null. */
    Source file() { synchronized (LOCK) { return file; } }
    /** A save wrote these bytes into {@code file} at {@code offset}: read from there from now on, the memory freed. Call holding {@link #LOCK}. */
    void storedIn(Source file, long offset) { this.file = file; this.offset = offset; bytes = null; }

    /** A book's file, open for reading its pages. */
    static final class Source {
        final File path;
        private final RandomAccessFile in;
        private Source(File path) throws IOException { this.path = path; in = new RandomAccessFile(path, "r"); }
        static Source open(File path) throws IOException { return new Source(path); }
        long length() throws IOException { synchronized (LOCK) { return in.length(); } }
        byte[] read(long offset, int length) throws IOException {
            synchronized (LOCK) {
                if (offset < 0 || length < 0 || offset + length > in.length()) throw new IOException("Page damaged");
                byte[] read = new byte[length];
                in.seek(offset); in.readFully(read);
                return read;
            }
        }
        void copy(long offset, int length, OutputStream out) throws IOException {
            synchronized (LOCK) {
                if (offset < 0 || offset + length > in.length()) throw new IOException("Page damaged");
                byte[] buffer = new byte[1 << 16];
                in.seek(offset);
                for (int left = length; left > 0; ) {
                    int n = Math.min(left, buffer.length);
                    in.readFully(buffer, 0, n);
                    out.write(buffer, 0, n);
                    left -= n;
                }
            }
        }
        void close() { try { in.close(); } catch (IOException ignored) { } }
    }
}
