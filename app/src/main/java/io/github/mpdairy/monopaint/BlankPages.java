package io.github.mpdairy.monopaint;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Blank pages in a notebook (the owner, 2026-10-19; docs/archive/superpowers/specs/2026-10-18-notebooks-design.md). A page is
 * blank with no marks, no magnets, no masking fluid, on the notebook's Page Colour (white unless set, {@link
 * NotebookOptions#paper}). A notebook's pages are the pages drawn or added, the page bar's + adding one (the owner,
 * 2026-10-24: "instead of always a one white page, add a + icon on the top toolbar"); the blank page notebooks kept at
 * their end until then goes when one is opened ({@link #opened}). Deleting a blank page doesn't ask; the PDF and PNG
 * copies leave out blank pages at the end. Pure Java, checked on the desktop ({@code BlankPagesChecks}).
 */
final class BlankPages {
    private BlankPages() {}

    /** Whether {@code page} is blank: nothing on it ({@link ToneDocument#unmarked}) and white paper. */
    static boolean blank(ToneDocument page) { return blank(page, NotebookOptions.WHITE); }
    /** As above, in a notebook whose new pages start on paper {@code tone} (its Page Colour): nothing on it, on that paper. */
    static boolean blank(ToneDocument page, int tone) { return page.unmarked() && page.paperTone() == tone; }

    // A blank page's bytes, made once a size

    /** A blank page {@code width × height} packed as a notebook's file holds pages, and its picture. */
    static final class Made {
        final byte[] bytes, picture;
        final long crc;
        /** The paper tone it was made on. */
        final int tone;
        private Made(byte[] bytes, byte[] picture, int tone) {
            this.bytes = bytes; this.picture = picture; this.tone = tone;
            java.util.zip.CRC32 c = new java.util.zip.CRC32(); c.update(bytes); crc = c.getValue();
        }
    }
    private static final Map<Long, Made> MADE = new HashMap<>();
    /**
     * A blank page of this size, made the first time (about 35 ms on this Mac for 1920 × 2400, 9 KB), packed at the
     * files' level, so a saved blank page is the same bytes and is told blank by them ({@link #madeTone}).
     */
    static Made made(int width, int height) throws IOException { return made(width, height, NotebookOptions.WHITE); }
    /** As above, on paper {@code tone} (a notebook's Page Colour, {@link ToneDocument#paperTone}). */
    static synchronized Made made(int width, int height, int tone) throws IOException {
        long key = ((long) width << 32 | height) * 256 + tone;
        Made made = MADE.get(key);
        if (made == null) {
            ToneDocument page = new ToneDocument(width, height);
            page.placePaper(tone);
            MADE.put(key, made = new Made(DrawingBook.encode(page), PagePicture.of(page.layerSnapshot()).encode(), tone));
        }
        return made;
    }
    private static final java.util.concurrent.ExecutorService AHEAD = java.util.concurrent.Executors.newSingleThreadExecutor(work -> {
        Thread thread = new Thread(work, "blank page"); thread.setDaemon(true); thread.setPriority(Thread.MIN_PRIORITY); return thread;
    });
    /** {@link #made} for this size and paper in the background, so a notebook's blank pages are told by their bytes without waiting for it. */
    static void prepare(int width, int height, int tone) {
        AHEAD.execute(() -> { try { made(width, height, tone); } catch (IOException | RuntimeException | OutOfMemoryError ignored) { } });
    }
    /** The paper tone of the blank page {@code packed} is, when it is bytes {@link #made} made; else −1. */
    static synchronized int madeTone(PackedPage packed) {
        if (packed == null) return -1;
        for (Made made : MADE.values()) if (made.crc == packed.crc && made.bytes.length == packed.length) return made.tone;
        return -1;
    }
    /** Pages found blank (their paper tone) or not (−1) by decoding them, by their bytes' CRC-32 (so the copies don't decode a page twice). */
    private static final Map<Long, Integer> SEEN = new LinkedHashMap<Long, Integer>(64, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Long, Integer> eldest) { return size() > 256; }
    };

    /** Whether {@code packed} holds a blank page; one that can't be read isn't. */
    static boolean blank(PackedPage packed) { return blank(packed, NotebookOptions.WHITE); }
    /** As above, on paper {@code tone} (the notebook's Page Colour). */
    static boolean blank(PackedPage packed, int tone) {
        if (packed == null) return false;
        int made = madeTone(packed);
        if (made >= 0) return made == tone;
        Integer seen;
        synchronized (SEEN) { seen = SEEN.get(packed.crc); }
        if (seen == null) {
            try { ToneDocument page = DocumentCodec.read(new ByteArrayInputStream(packed.bytes())); seen = page.unmarked() ? page.paperTone() : -1; }
            catch (IOException | RuntimeException e) { return false; }
            synchronized (SEEN) { SEEN.put(packed.crc, seen); }
        }
        return seen == tone;
    }

    /**
     * Whether page {@code i} of {@code book} is blank: from the page if it is decoded; else a picture with any grey says
     * no (it shows the visible layers, the magnets and the paper); else from its bytes, decoded only when they aren't a
     * blank page's.
     */
    static boolean blank(DrawingBook book, int i) {
        if (i < 0 || i >= book.count()) return false;
        int tone = book.options().paper;
        ToneDocument page = book.cachedPage(i);
        if (page != null) return blank(page, tone);
        byte[] picture = book.picture(i);
        if (picture != null && tone == NotebookOptions.WHITE) {
            try { if (!PagePicture.decode(picture).white()) return false; }
            catch (IOException | RuntimeException ignored) { }
        }
        return blank(book.packed(i), tone);
    }
    /** Whether every page of {@code book} is blank, and nothing is drawn on its covers. */
    static boolean empty(DrawingBook book) {
        if (book.coverDrawn(DrawingBook.FRONT) || book.coverDrawn(DrawingBook.BACK)) return false;
        for (int i = 0; i < book.count(); i++) if (!blank(book, i)) return false;
        return true;
    }
    /** Whether deleting page {@code i} asks first: it isn't blank. */
    static boolean deleteAsks(DrawingBook book, int i) { return !blank(book, i); }

    // An old notebook's blank page at its end

    /**
     * A notebook is opened. Notebooks kept a blank page at their end until 2026-10-24 (the owner: "instead of always a one
     * white page, add a + icon on the top toolbar"; the page bar's + adds one now, {@link DrawingBook#insertPage}), so
     * that page goes, not as a change: a save leaves it out with what else changed, and opening never rewrites the file.
     * Only a page alone (no layers), not the page drawn on and not a notebook's only page, blank on the notebook's Page
     * Colour, with nothing of its own (template, rules, time, see-through step, ghost pin, cover picture). Whether it went.
     */
    static boolean opened(DrawingBook book) throws IOException {
        int last = book.count() - 1;
        if (last < 1 || last == book.index() || book.linked(last) || book.cover() == last || book.ownTemplate(last)
                || book.pageRules(last) != 0 || book.pageSeconds(last) != 0) return false;
        TracingPaper tracing = book.tracing();
        if (tracing.ghost == last || tracing.stepBits(last) != 0 || !blank(book, last)) return false;
        book.dropUnchanged(last);
        return true;
    }

    // The copies

    /** {@code pages} up to the last that isn't blank, the first at least: what the PDF and PNG copies hold. */
    static List<PackedPage> drawn(List<PackedPage> pages) { return drawn(pages, NotebookOptions.WHITE); }
    /** As above, in a notebook whose new pages start on paper {@code tone}. */
    static List<PackedPage> drawn(List<PackedPage> pages, int tone) {
        int n = pages.size();
        while (n > 1 && blank(pages.get(n - 1), tone)) n--;
        return n == pages.size() ? pages : new ArrayList<>(pages.subList(0, n));
    }
}
