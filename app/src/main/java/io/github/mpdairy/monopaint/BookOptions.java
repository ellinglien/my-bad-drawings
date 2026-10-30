package io.github.mpdairy.monopaint;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

/**
 * A notebook's options block (docs/archive/superpowers/specs/2026-10-19-notebook-options-design.md, "Storage"): "INO1", kept in
 * the book's file between its last page and its notebook block ({@link BookPictures}), only when there is something to
 * keep; an old notebook has none, which means all defaults. It is a list of records, each a tag, a length and its bytes,
 * so a later version's records (template, tools, rules, lock) are skipped by this one and this one's by any later.
 * Layout: "INO1", version, record count; the records; a trailer: the block's length before it and "INO1".
 *
 * <p>Stage 1 keeps the covers: a cover something is drawn on is a page's bytes, as a page entry holds them ({@code FRNT},
 * {@code BACK}: their CRC-32, then the bytes), its picture of its marks alone ({@code FPIC}, {@code BPIC}: the CRC of
 * the cover it shows, then the picture), and the page glued on the front ({@code CPIC}: its index). A cover with nothing
 * drawn has no record. Readers before covers never look here: those that stream the ZIP stop at the last page as before
 * ("INO1" isn't a local header), and those that read the notebook block from the directory find it where they always
 * did, just before the directory.
 *
 * <p>The covers' design ({@code DSGN}: its key, {@link CoverDesign#key}, in UTF-8) when it isn't the default, Classic
 * Black Marble: a notebook without one, or one this version doesn't know, has the default. Versions before designs skip
 * the record. Pure: checked on the desktop ({@code CoverChecks}, {@code CoverDesignChecks}).
 *
 * <p>Stages 2 and 3 add the notebook's {@link NotebookOptions}: the template ({@code TMPL}: its key), Guides in PDF ({@code GPDF}:
 * one byte, 0 for Leave Out; cut 2026-10-24, passed by now) and its own toolbar ({@code TOOL}: the choice's key, then a line a slot, eight before 2026-10-27), each written only when it
 * isn't the default and passed by, like the covers' records, by versions before them. Checked in {@code NotebookOptionsChecks}.
 *
 * <p>Stages 4 and 5 add the rules and the lock: {@code NERS} (No Erasing: one byte, 1 for on), {@code ONEB} (One Brush: its
 * toolbar line), {@code TIMD} (Timed Pages: the minutes, one byte) and {@code LOCK} (one byte: 1 Locked, 2 Sealed; cut 2026-10-24,
 * passed by now), each only when on, skipped by versions before them. A timed page's own clock is in the page's record ({@link PageClock}).
 * Checked in {@code NotebookRulesChecks}.
 *
 * <p>Tracing paper (docs/archive/superpowers/specs/2026-10-20-tracing-paper-design.md, "In the file") adds {@code PAPM} (the Paper:
 * one byte, 1 Tracing, 2 Glass), {@code GHST} (the pinned ghost page, an int) and {@code STEP} (the page count, then each
 * page's step and its layers' steps, a byte each), each only when it isn't the default: a view of the pages, so none of it
 * is in a page's bytes. Versions before them skip the records and open the notebook Plain. Checked in {@code TracingChecks}.
 * Layers are pages (the owner, 2026-10-21) adds {@code PSTR} (the default strength: one byte, 3 Faint), only when Faint;
 * versions before it skip it and show Half.
 *
 * <p>Brushes and per-page templates (2026-10-21) add {@code BRSH} (Brushes: the count, one byte; it replaces {@code ONEB},
 * which is still read: its brush fills a Library toolbar with Brushes 1, and never written) and {@code PTPL} (each page's
 * own template key, a line a page, empty for none: a Random notebook's pages keep what they were given). Versions before
 * them skip both: a Random notebook's template there is Blank, its toolbar all its slots.
 *
 * <p>Timed Pages in seconds (2026-10-21) adds {@code TSEC} (the seconds, two bytes), read over {@code TIMD}, which is still
 * written (the nearest minutes the builds before offered) so those keep a timer; a file with TIMD alone reads its minutes.
 *
 * <p>The library card's days (2026-10-21) add {@code LAST} (the day a page was last added to or changed, an int of epoch
 * days), written once a page has changed in this version; versions before it skip it. A page's own template (a hold on it
 * in the strip) is a {@code PTPL} line of {@code "="} and the template's key ({@link NotebookOptions#OWN}).
 *
 * <p>Layers are stacked pages (2026-10-21, the tracing paper spec's "Stage 3c") add {@code STAK} (the page count, then a
 * byte a page, 1 where it is stacked on the page below), only when a page is: versions before it skip it and show each
 * layer as a page of its own.
 *
 * <p>The template's guides on the screen (the owner, 2026-10-22) add {@code GSHN} (one byte: 1 Half, 2 Faint, 3 Hidden),
 * only when not Full: versions before it skip it and show the guides in full.
 *
 * <p>Page Colour (the owner, 2026-10-23) adds {@code PCOL} (one byte, the paper tone new pages start on), only when not
 * white: versions before it skip it and add white pages. The pages keep their own tone, so they show the same in any version.
 *
 * <p>Page Settings (the owner, 2026-10-23) adds {@code PRUL} (the page count, then a byte a page: its own No Erasing and
 * Timed Pages, {@link NotebookRules#own}), only when a page has a rule of its own: versions before it skip it and keep the
 * notebook's rules on every page.
 *
 * <p>A page's own time (Page Settings' slider, the owner, 2026-10-23) adds {@code PTIM} (the page count, then two bytes a
 * page: its seconds, 0 for none), only when a page has one: versions before it skip it, and a page timed on its own takes
 * the notebook's time, or 5 min.
 *
 * <p>Lock Pages (the owner, 2026-10-27) adds {@code TLCK} (one byte, 1 for on), only when on. A notebook without it, any
 * older one, has it off: a page its timer ran out on opens editable. Versions before it skip it and keep such a page locked.
 *
 * <p>Nib Preview (the owner, 2026-10-29) adds {@code NIBP} (one byte, 1 for on), only when off: absent, any older notebook,
 * is on. Versions before it skip it and show the hover ring.
 *
 * <p>Self-Portrait Prompt (the owner, 2026-10-30) adds {@code SELF} (one byte, 1 for on), only when on: absent, any older
 * notebook, is off. Versions before it skip it and deal no cards.
 */
final class BookOptions {
    static final int MAGIC = 0x494e4f31; // INO1
    private static final int VERSION = 1;
    static final int FRONT = tag("FRNT"), BACK = tag("BACK"), FRONT_PICTURE = tag("FPIC"), BACK_PICTURE = tag("BPIC"), COVER_PICTURE = tag("CPIC"), DESIGN = tag("DSGN"),
            TEMPLATE = tag("TMPL"), GUIDES = tag("GPDF"), TOOLS = tag("TOOL"),
            NO_ERASING = tag("NERS"), ONE_BRUSH = tag("ONEB"), BRUSHES = tag("BRSH"), PAGE_TEMPLATES = tag("PTPL"), TIMED = tag("TIMD"), TIMED_SECONDS = tag("TSEC"), LOCK = tag("LOCK"),
            PAPER = tag("PAPM"), GHOST = tag("GHST"), STEPS = tag("STEP"), STRENGTH = tag("PSTR"), STACKS = tag("STAK"),
            LAST = tag("LAST"), GUIDES_SHOWN = tag("GSHN"), PAGE_COLOUR = tag("PCOL"), PAGE_RULES = tag("PRUL"), PAGE_SECONDS = tag("PTIM"), LOCK_PAGES = tag("TLCK"), NIB_PREVIEW = tag("NIBP"), SELF_PORTRAIT = tag("SELF");
    static int tag(String name) { byte[] b = name.getBytes(java.nio.charset.StandardCharsets.US_ASCII); return (b[0] & 255) << 24 | (b[1] & 255) << 16 | (b[2] & 255) << 8 | (b[3] & 255); }

    /**
     * The covers as a file keeps them: each one's page (null: nothing drawn), front then back, its picture, the cover
     * picture's page (-1: none) and their design.
     */
    static final class Covers {
        final PackedPage[] pages = new PackedPage[2];
        final byte[][] pictures = new byte[2][];
        int picture = -1;
        CoverDesign design = CoverDesign.DEFAULT;
        /** The notebook's other options ({@link NotebookOptions}): the template and the tools. */
        NotebookOptions options = NotebookOptions.DEFAULT;
        /** Tracing paper ({@link TracingPaper}): the Paper, the ghost, the steps; null or the default keeps nothing. */
        TracingPaper tracing;
        /** Each page's own template key (null where none: {@code DrawingBook#pageTemplates}); null or none set keeps nothing. */
        List<String> pageTemplates;
        /** Each page's own rules ({@link NotebookRules#own}; null or all 0 keeps nothing). */
        int[] pageRules;
        /** Each page's own time, s ({@code DrawingBook#pageSeconds}; null or all 0 keeps nothing). */
        int[] pageSeconds;
        /** The day (epoch day) a page was last added to or changed; -1: not known, nothing kept. */
        long lastDay = -1;
        /** Whether a file needs the block for them. */
        boolean any() { return pages[0] != null || pages[1] != null || picture >= 0 || designed() || !options.isDefault() || !optionRecords(this).isEmpty(); }
        /** Whether the design is kept: it isn't the default. */
        boolean designed() { return design != CoverDesign.DEFAULT; }
        /** The design's record's bytes. */
        byte[] designBytes() { return design.key.getBytes(java.nio.charset.StandardCharsets.UTF_8); }
    }

    /** How many bytes {@link #write} writes for {@code covers}: 0 when there is nothing to keep. */
    static long length(Covers covers) {
        if (!covers.any()) return 0;
        long n = 12 + 8;
        for (int s = 0; s < 2; s++) if (covers.pages[s] != null) {
            n += 8 + 4 + covers.pages[s].length;
            if (covers.pictures[s] != null) n += 8 + 4 + covers.pictures[s].length;
        }
        if (covers.picture >= 0) n += 8 + 4;
        if (covers.designed()) n += 8 + covers.designBytes().length;
        for (Entry e : optionRecords(covers)) n += 8 + e.bytes.length;
        return n;
    }
    /**
     * Writes the block for {@code covers} to {@code out} at file offset {@code at} (nothing when there is nothing to keep),
     * each cover's page copied as it is ({@link PackedPage#copyTo}); returns where each cover's bytes start, front then
     * back (0 for none).
     */
    static long[] write(OutputStream out, long at, Covers covers) throws IOException {
        long[] offsets = new long[2];
        if (!covers.any()) return offsets;
        DataOutputStream data = new DataOutputStream(out);
        int records = 0;
        for (int s = 0; s < 2; s++) if (covers.pages[s] != null) records += covers.pictures[s] != null ? 2 : 1;
        if (covers.picture >= 0) records++;
        if (covers.designed()) records++;
        records += optionRecords(covers).size();
        data.writeInt(MAGIC); data.writeInt(VERSION); data.writeInt(records);
        long written = 12;
        for (int s = 0; s < 2; s++) {
            PackedPage page = covers.pages[s];
            if (page == null) continue;
            data.writeInt(s == 0 ? FRONT : BACK); data.writeInt(4 + page.length); data.writeInt((int) page.crc);
            offsets[s] = at + written + 12; data.flush();
            page.copyTo(out);
            written += 12 + page.length;
            byte[] picture = covers.pictures[s];
            if (picture == null) continue;
            data.writeInt(s == 0 ? FRONT_PICTURE : BACK_PICTURE); data.writeInt(4 + picture.length); data.writeInt((int) page.crc); data.write(picture);
            written += 12 + picture.length;
        }
        if (covers.picture >= 0) { data.writeInt(COVER_PICTURE); data.writeInt(4); data.writeInt(covers.picture); written += 12; }
        if (covers.designed()) { byte[] key = covers.designBytes(); data.writeInt(DESIGN); data.writeInt(key.length); data.write(key); written += 8 + key.length; }
        for (Entry e : optionRecords(covers)) { data.writeInt(e.tag); data.writeInt(e.bytes.length); data.write(e.bytes); written += 8 + e.bytes.length; }
        data.writeInt((int) written); data.writeInt(MAGIC); data.flush();
        return offsets;
    }

    /** Reads bytes of a book's file: a file's (random access) or one held whole. */
    interface Bytes { byte[] read(long offset, int length) throws IOException; }
    /** A record: its tag, and where its bytes are and how many. */
    static final class Record {
        final int tag; final long offset; final int length;
        Record(int tag, long offset, int length) { this.tag = tag; this.offset = offset; this.length = length; }
    }
    /** Where a block was found: from {@code start} to {@code end} (its trailer's end), and its records. */
    static final class Found {
        final long start, end;
        final List<Record> records;
        Found(long start, long end, List<Record> records) { this.start = start; this.end = end; this.records = records; }
        Record get(int tag) { for (Record r : records) if (r.tag == tag) return r; return null; }
    }
    /**
     * The block ending at {@code end} (where the notebook block starts), not before {@code from} (the last page's end); null
     * when there is none or it doesn't read: then the notebook has its defaults.
     */
    static Found find(Bytes file, long from, long end) {
        try {
            if (end - from < 20) return null;
            byte[] trailer = file.read(end - 8, 8);
            if (BookPictures.readInt(trailer, 4) != MAGIC) return null;
            long length = BookPictures.readInt(trailer, 0) & 0xffffffffL, start = end - 8 - length;
            if (length < 12 || start < from) return null;
            byte[] head = file.read(start, 12);
            if (BookPictures.readInt(head, 0) != MAGIC || BookPictures.readInt(head, 4) != VERSION) return null;
            int count = BookPictures.readInt(head, 8);
            if (count < 0 || count > 1024) return null;
            List<Record> records = new ArrayList<>();
            long at = start + 12;
            for (int i = 0; i < count; i++) {
                if (at + 8 > end - 8) return null;
                byte[] r = file.read(at, 8);
                long n = BookPictures.readInt(r, 4) & 0xffffffffL;
                if (at + 8 + n > end - 8) return null;
                records.add(new Record(BookPictures.readInt(r, 0), at + 8, (int) n));
                at += 8 + n;
            }
            return at == end - 8 ? new Found(start, end, records) : null;
        } catch (IOException | RuntimeException damaged) { return null; }
    }
    /** How many pages a notebook of {@code pages} pages says it has: its stacks, by {@code found}'s {@code STAK} (null: every page its own). */
    static int stacks(Found found, Bytes file, int pages) throws IOException {
        if (found == null) return pages;
        return TracingPaper.read(null, null, null, null, bytes(found.get(STACKS), file), pages).stacks().count();
    }
    /**
     * The covers a found block keeps ({@code found} null: none), their pages read from {@code source} when needed (a
     * book's file) or, for null, copied out of {@code file} now and checked (a book read whole).
     */
    static Covers covers(Found found, Bytes file, PackedPage.Source source, int pages) throws IOException {
        Covers covers = new Covers();
        if (found == null) return covers;
        for (int s = 0; s < 2; s++) {
            Record page = found.get(s == 0 ? FRONT : BACK);
            if (page == null || page.length < 4 + 12) continue;
            long crc = BookPictures.readInt(file.read(page.offset, 4), 0) & 0xffffffffL;
            if (source != null) covers.pages[s] = PackedPage.in(source, page.offset + 4, page.length - 4, crc);
            else {
                PackedPage held = PackedPage.of(file.read(page.offset + 4, page.length - 4));
                if (held.crc != crc) throw new IOException("Cover damaged");
                covers.pages[s] = held;
            }
            DocumentCodec.checkHeader(file.read(page.offset + 4, 12), 0, 0);
            Record picture = found.get(s == 0 ? FRONT_PICTURE : BACK_PICTURE);
            if (picture != null && picture.length > 4 && (BookPictures.readInt(file.read(picture.offset, 4), 0) & 0xffffffffL) == crc)
                covers.pictures[s] = file.read(picture.offset + 4, picture.length - 4);
        }
        Record pictured = found.get(COVER_PICTURE);
        if (pictured != null && pictured.length == 4) {
            int p = BookPictures.readInt(file.read(pictured.offset, 4), 0);
            covers.picture = p >= 0 && p < pages ? p : -1;
        }
        covers.design = design(found, file);
        covers.options = options(found, file);
        covers.pageTemplates = pageTemplates(bytes(found.get(PAGE_TEMPLATES), file), pages);
        covers.pageRules = pageRules(bytes(found.get(PAGE_RULES), file), pages);
        covers.pageSeconds = pageSeconds(bytes(found.get(PAGE_SECONDS), file), pages);
        byte[] last = bytes(found.get(LAST), file);
        covers.lastDay = last != null && last.length == 4 ? Math.max(-1, BookPictures.readInt(last, 0)) : -1;
        covers.tracing = TracingPaper.read(bytes(found.get(PAPER), file), bytes(found.get(GHOST), file), bytes(found.get(STEPS), file), bytes(found.get(STRENGTH), file), bytes(found.get(STACKS), file), pages);
        return covers;
    }
    /** A record to write: its tag and its bytes. */
    private static final class Entry {
        final int tag; final byte[] bytes;
        Entry(int tag, byte[] bytes) { this.tag = tag; this.bytes = bytes; }
    }
    /** The records the options and tracing paper need, in the order they are written: each only when it isn't the default (none, for the defaults). */
    private static List<Entry> optionRecords(Covers covers) {
        NotebookOptions options = covers.options;
        List<Entry> records = new ArrayList<>();
        if (options.template != NotebookTemplate.BLANK) records.add(new Entry(TEMPLATE, options.templateBytes()));
        if (options.guidesShown != NotebookOptions.FULL) records.add(new Entry(GUIDES_SHOWN, options.guidesShownBytes()));
        if (options.paper != NotebookOptions.WHITE) records.add(new Entry(PAGE_COLOUR, options.paperBytes()));
        if (options.ownsTools()) records.add(new Entry(TOOLS, options.toolsBytes()));
        if (options.noErasing) records.add(new Entry(NO_ERASING, options.noErasingBytes()));
        if (options.brushes > 0) records.add(new Entry(BRUSHES, options.brushesBytes()));
        if (options.seconds > 0) { records.add(new Entry(TIMED, options.minutesBytes())); records.add(new Entry(TIMED_SECONDS, options.secondsBytes())); }
        if (options.lockPages) records.add(new Entry(LOCK_PAGES, options.lockPagesBytes()));
        if (!options.nibPreview) records.add(new Entry(NIB_PREVIEW, options.nibPreviewBytes()));
        if (options.selfPortrait) records.add(new Entry(SELF_PORTRAIT, options.selfPortraitBytes()));
        TracingPaper t = covers.tracing;
        if (t != null) {
            if (t.paper != TracingPaper.PLAIN) records.add(new Entry(PAPER, t.paperBytes()));
            if (t.ghost >= 0) records.add(new Entry(GHOST, t.ghostBytes()));
            if (t.stepped()) records.add(new Entry(STEPS, t.stepBytes()));
            if (t.strength != TracingPaper.HALF) records.add(new Entry(STRENGTH, t.strengthBytes()));
            if (t.stacked()) records.add(new Entry(STACKS, t.stackBytes()));
        }
        byte[] pages = pageTemplateBytes(covers.pageTemplates);
        if (pages != null) records.add(new Entry(PAGE_TEMPLATES, pages));
        byte[] rules = pageRuleBytes(covers.pageRules);
        if (rules != null) records.add(new Entry(PAGE_RULES, rules));
        byte[] seconds = pageSecondBytes(covers.pageSeconds);
        if (seconds != null) records.add(new Entry(PAGE_SECONDS, seconds));
        if (covers.lastDay >= 0) records.add(new Entry(LAST, java.nio.ByteBuffer.allocate(4).putInt((int) covers.lastDay).array()));
        return records;
    }
    /** The options a found block keeps ({@code found} null: the defaults); a record this version doesn't read is the default. */
    static NotebookOptions options(Found found, Bytes file) throws IOException {
        NotebookOptions options = NotebookOptions.DEFAULT;
        if (found == null) return options;
        options = options.readTemplate(bytes(found.get(TEMPLATE), file));
        // GPDF (Guides in PDF) and LOCK (Lock Settings), both cut (2026-10-24), are passed by: the guides always print, and
        // a notebook locked or sealed before opens unlocked, its rules as they were. Saved again, it writes neither.
        options = options.readGuidesShown(bytes(found.get(GUIDES_SHOWN), file)).readPaper(bytes(found.get(PAGE_COLOUR), file));
        options = options.readTools(bytes(found.get(TOOLS), file));
        options = options.readNoErasing(bytes(found.get(NO_ERASING), file));
        options = options.readOneBrush(bytes(found.get(ONE_BRUSH), file));
        options = options.readBrushes(bytes(found.get(BRUSHES), file));
        options = options.readMinutes(bytes(found.get(TIMED), file)).readSeconds(bytes(found.get(TIMED_SECONDS), file));
        options = options.readLockPages(bytes(found.get(LOCK_PAGES), file)).readNibPreview(bytes(found.get(NIB_PREVIEW), file))
                .readSelfPortrait(bytes(found.get(SELF_PORTRAIT), file));
        return options;
    }
    /** Each page's template key, a line a page (empty for none); null when none is set. */
    static byte[] pageTemplateBytes(List<String> keys) {
        if (keys == null) return null;
        boolean any = false;
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < keys.size(); i++) { String k = keys.get(i); if (i > 0) text.append('\n'); if (k != null && !k.isEmpty()) { any = true; text.append(k); } }
        return any ? text.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8) : null;
    }
    /** {@link #pageTemplateBytes} read for {@code pages} pages (null where none); null for none or a record for another page count. */
    static List<String> pageTemplates(byte[] bytes, int pages) {
        if (bytes == null) return null;
        String[] lines = new String(bytes, java.nio.charset.StandardCharsets.UTF_8).split("\n", -1);
        if (lines.length != pages) return null;
        List<String> keys = new ArrayList<>();
        for (String l : lines) keys.add(l.isEmpty() ? null : l);
        return keys;
    }
    /** Each page's own rules: the page count, then a byte a page; null when no page has any. */
    static byte[] pageRuleBytes(int[] rules) {
        if (rules == null) return null;
        boolean any = false;
        for (int r : rules) any |= r != 0;
        if (!any) return null;
        java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(4 + rules.length).putInt(rules.length);
        for (int r : rules) b.put((byte) r);
        return b.array();
    }
    /** {@link #pageRuleBytes} read for {@code pages} pages; null for none, or a record for another page count or with a byte this version doesn't write. */
    static int[] pageRules(byte[] bytes, int pages) {
        if (bytes == null || bytes.length != 4 + pages || java.nio.ByteBuffer.wrap(bytes).getInt() != pages) return null;
        int[] rules = new int[pages];
        for (int i = 0; i < pages; i++) { rules[i] = bytes[4 + i] & 255; if (!NotebookRules.validOwn(rules[i])) return null; }
        return rules;
    }
    /** Each page's own time: the page count, then two bytes a page, its seconds; null when no page has one. */
    static byte[] pageSecondBytes(int[] seconds) {
        if (seconds == null) return null;
        boolean any = false;
        for (int s : seconds) any |= s != 0;
        if (!any) return null;
        java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(4 + 2 * seconds.length).putInt(seconds.length);
        for (int s : seconds) b.putShort((short) s);
        return b.array();
    }
    /** {@link #pageSecondBytes} read for {@code pages} pages; null for none, or a record for another page count or with a time over an hour. */
    static int[] pageSeconds(byte[] bytes, int pages) {
        if (bytes == null || bytes.length != 4 + 2 * pages) return null;
        java.nio.ByteBuffer b = java.nio.ByteBuffer.wrap(bytes);
        if (b.getInt() != pages) return null;
        int[] seconds = new int[pages];
        for (int i = 0; i < pages; i++) { seconds[i] = b.getShort() & 0xffff; if (seconds[i] > NotebookOptions.SECONDS[NotebookOptions.SECONDS.length - 1]) return null; }
        return seconds;
    }
    private static byte[] bytes(Record record, Bytes file) throws IOException { return record == null || record.length < 0 || record.length > 1 << 20 ? null : file.read(record.offset, record.length); }
    /** The design a found block keeps ({@code found} null: none): the default without one, or for one this version doesn't know. */
    static CoverDesign design(Found found, Bytes file) throws IOException {
        Record design = found == null ? null : found.get(DESIGN);
        if (design == null || design.length <= 0 || design.length > 64) return CoverDesign.DEFAULT;
        return CoverDesign.of(new String(file.read(design.offset, design.length), java.nio.charset.StandardCharsets.UTF_8));
    }
    /** {@code archive}'s bytes, as {@link Bytes}. */
    static Bytes of(byte[] archive) {
        return (offset, length) -> {
            if (offset < 0 || length < 0 || offset + length > archive.length) throw new IOException("Invalid notebook block");
            return java.util.Arrays.copyOfRange(archive, (int) offset, (int) offset + length);
        };
    }
    /** A file's bytes, as {@link Bytes}. */
    static Bytes of(PackedPage.Source source) { return source::read; }
    static Bytes of(java.io.RandomAccessFile file) {
        return (offset, length) -> {
            if (offset < 0 || length < 0 || offset + length > file.length()) throw new IOException("Invalid notebook block");
            byte[] b = new byte[length]; file.seek(offset); file.readFully(b); return b;
        };
    }

    /** The CRC-32 of {@code bytes}. */
    static long crc(byte[] bytes) { CRC32 c = new CRC32(); c.update(bytes); return c.getValue(); }
    /** The block for {@code covers} held whole (for checks). */
    static byte[] bytes(Covers covers) throws IOException { ByteArrayOutputStream out = new ByteArrayOutputStream(); write(out, 0, covers); return out.toByteArray(); }

    private BookOptions() {}
}
