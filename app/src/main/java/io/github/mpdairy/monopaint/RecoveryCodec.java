package io.github.mpdairy.monopaint;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.function.Function;

/**
 * Atomically keep the working drawing, its save destination and whether it has unsaved changes together. Since
 * 2026-10-18 (TSR3, Inkwash's notebooks) it holds only the pages its notebook's own file doesn't: each other page is
 * named by its length and CRC-32 and read from that file when recovered, so a recovery stays small however big the
 * notebook. A save that changed the file in between keeps the same bytes for the same pages, so they are still found.
 */
final class RecoveryCodec {
    // TSR2 adds the unsaved flag; TSR1 and older recoveries count as unsaved. TSR3 keeps its notebook's saved pages by reference.
    private static final int MAGIC3 = 0x54535233, MAGIC = 0x54535232, MAGIC_TSR1 = 0x54535231;
    private static final int INLINE = 0, IN_FILE = 1;

    static void write(OutputStream output, DrawingBook.Snapshot book, String path) throws IOException {
        if (!path.isEmpty() && !DrawingFiles.validPath(path)) throw new IOException("Invalid recovery destination");
        DataOutputStream out = new DataOutputStream(output);
        out.writeInt(MAGIC3);
        out.writeUTF(path);
        out.writeBoolean(book.unsaved);
        java.util.List<PackedPage> pages = book.packed();
        out.writeInt(book.width); out.writeInt(book.height); out.writeInt(pages.size()); out.writeInt(book.fileIndex()); out.writeInt(Math.max(0, book.cover));
        for (int i = 0; i < pages.size(); i++) {
            out.writeByte(book.canvasChoices.get(i));
            page(out, pages.get(i), path);
        }
        for (int i = 0; i < pages.size(); i++) picture(out, book.pictures.get(i));
        // Inkwash: the covers (a section older readers never reach): the cover picture's page, then each cover's page and picture.
        PackedPage[] covers = book.packedCovers();
        out.writeInt(COVERS); out.writeInt(book.cover);
        for (int s = 0; s < 2; s++) {
            if (covers[s] == null) { out.writeByte(NONE); continue; }
            page(out, covers[s], path);
            picture(out, book.coverPictures[s]);
        }
        // The covers' design, when it isn't the default (after the covers: older readers stop before it).
        if (book.design != CoverDesign.DEFAULT) { out.writeInt(DESIGN); out.writeUTF(book.design.key); }
        // The notebook's other options (template, the cut Guides in PDF's place, tools), when any isn't the default: three byte arrays, empty for a default.
        if (!book.options.isDefault()) {
            out.writeInt(OPTIONS);
            byte[][] kept = {book.options.template == NotebookTemplate.BLANK ? new byte[0] : book.options.templateBytes(), new byte[]{1}, book.options.ownsTools() ? book.options.toolsBytes() : new byte[0]};
            for (byte[] bytes : kept) { out.writeInt(bytes.length); out.write(bytes); }
        }
        // The rules (No Erasing, Brushes, Timed Pages), when any is on: four byte arrays, empty when off, the fourth the cut
        // Lock's, always empty now. Readers before them stop here.
        if (book.options.hasRules()) {
            out.writeInt(RULES);
            NotebookOptions o = book.options;
            byte[][] kept = {o.noErasing ? o.noErasingBytes() : new byte[0], o.brushes > 0 ? o.brushesBytes() : new byte[0], o.seconds > 0 ? o.secondsBytes() : new byte[0], new byte[0]};
            for (byte[] bytes : kept) { out.writeInt(bytes.length); out.write(bytes); }
        }
        // Tracing paper (the Paper, the ghost, the steps), when any isn't the default: three byte arrays, empty when default. Readers before it stop here.
        if (!book.tracing.isDefault()) {
            out.writeInt(PAPER);
            TracingPaper t = book.tracing;
            byte[][] kept = {t.paper != TracingPaper.PLAIN ? t.paperBytes() : new byte[0], t.ghost >= 0 ? t.ghostBytes() : new byte[0], t.stepped() ? t.stepBytes() : new byte[0]};
            for (byte[] bytes : kept) { out.writeInt(bytes.length); out.write(bytes); }
        }
        // Tracing paper's default strength, when Faint: one byte. Readers before it stop here and show Half.
        if (book.tracing.strength != TracingPaper.HALF) { out.writeInt(STRENGTH); out.writeByte(book.tracing.strength); }
        // Each page's own template (a Random notebook's), when any page has one. Readers before it stop here.
        byte[] templates = BookOptions.pageTemplateBytes(book.pageTemplates);
        if (templates != null) { out.writeInt(TEMPLATES); out.writeInt(templates.length); out.write(templates); }
        // The day a page was last added to (the library card's), when known. Readers before it stop here.
        if (book.lastDay >= 0) { out.writeInt(LAST); out.writeInt((int) book.lastDay); }
        // The stacks (a layer is a page stacked on the one below), when a page is. Readers before it stop here: each layer a page.
        if (book.tracing.stacked()) { byte[] stacks = book.tracing.stackBytes(); out.writeInt(STACKS); out.writeInt(stacks.length); out.write(stacks); }
        // The template's guides on the screen (Half, Faint, Hidden), when not Full: one byte. Readers before it stop here: Full.
        if (book.options.guidesShown != NotebookOptions.FULL) { out.writeInt(GUIDES_SHOWN); out.writeByte(book.options.guidesShown); }
        // Page Colour, when not white: one byte. Readers before it stop here: new pages white.
        if (book.options.paper != NotebookOptions.WHITE) { out.writeInt(PAGE_COLOUR); out.writeByte(book.options.paper); }
        // Each page's own rules (Page Settings), when a page has any. Readers before it stop here: the notebook's rules on every page.
        byte[] rules = BookOptions.pageRuleBytes(book.pageRules);
        if (rules != null) { out.writeInt(PAGE_RULES); out.writeInt(rules.length); out.write(rules); }
        // Each page's own time (Page Settings' slider), when a page has one. Readers before it stop here: those pages take the notebook's time, or 5 min.
        byte[] seconds = BookOptions.pageSecondBytes(book.pageSeconds);
        if (seconds != null) { out.writeInt(PAGE_SECONDS); out.writeInt(seconds.length); out.write(seconds); }
        // Lock Pages, when on: no more. Readers before it stop here: off.
        if (book.options.lockPages) out.writeInt(LOCK_PAGES);
        // Nib Preview, when off: no more. Readers before it stop here: on.
        if (!book.options.nibPreview) out.writeInt(NIB_PREVIEW_OFF);
        // Self-Portrait Prompt, when on: no more. Readers before it stop here: off.
        if (book.options.selfPortrait) out.writeInt(SELF_PORTRAIT);
        out.flush();
    }
    /** A page: in the notebook's file (by its length and CRC) or its bytes. */
    private static void page(DataOutputStream out, PackedPage page, String path) throws IOException {
        // A page in a file is in the notebook's: a book is opened from its own file, and saved to it.
        boolean stored = !page.inMemory() && !path.isEmpty();
        out.writeByte(stored ? IN_FILE : INLINE);
        out.writeInt(page.length); out.writeInt((int) page.crc);
        if (!stored) page.copyTo(out);
    }
    private static void picture(DataOutputStream out, byte[] picture) throws IOException {
        out.writeInt(picture == null ? 0 : picture.length);
        if (picture != null) out.write(picture);
    }
    /** The covers' section, a cover with nothing drawn on it, and the covers' design's section. */
    private static final int COVERS = 0x54535243, NONE = 2, DESIGN = 0x54535244, OPTIONS = 0x5453524f, RULES = 0x54535255, PAPER = 0x54535250, STRENGTH = 0x54535253, TEMPLATES = 0x54535254, LAST = 0x5453524c, STACKS = 0x5453524b, GUIDES_SHOWN = 0x54535247, PAGE_COLOUR = 0x54535242, PAGE_RULES = 0x54535252, PAGE_SECONDS = 0x5453524d, LOCK_PAGES = 0x54535258, NIB_PREVIEW_OFF = 0x5453524e, SELF_PORTRAIT = 0x54535251; // TSRU, TSRP, TSRS, TSRT, TSRL, TSRK, TSRG, TSRB, TSRR, TSRM, TSRX, TSRN, TSRQ

    /** Reads a recovery whose pages it holds itself (a TSR3 naming pages in its notebook's file can't be read so). */
    static Recovered read(InputStream input) throws IOException { return read(input, path -> null); }
    /** Reads a recovery; {@code drawing} gives the file of a library path, where a TSR3's saved pages are. */
    static Recovered read(InputStream input, Function<String, File> drawing) throws IOException {
        BufferedInputStream buffered = new BufferedInputStream(input);
        buffered.mark(4);
        DataInputStream in = new DataInputStream(buffered);
        int magic = in.readInt();
        if (magic != MAGIC3 && magic != MAGIC && magic != MAGIC_TSR1) {
            buffered.reset();
            return new Recovered(BookCodec.read(buffered), "", true);
        }
        String path = in.readUTF();
        if (!path.isEmpty() && !DrawingFiles.validPath(path)) throw new IOException("Invalid recovery destination");
        boolean unsaved = magic == MAGIC_TSR1 || in.readBoolean();
        if (magic != MAGIC3) return new Recovered(BookCodec.read(buffered), path, unsaved);
        int width = in.readInt(), height = in.readInt(), count = in.readInt(), index = in.readInt(), cover = in.readInt();
        if (count < 1 || count > DrawingBook.MAX_PAGES || index < 0 || index >= count) throw new IOException("Invalid recovery");
        ArrayList<PackedPage> pages = new ArrayList<>();
        int[] choices = new int[count];
        long total = 0;
        java.util.concurrent.atomic.AtomicReference<HashMap<String, PackedPage>> file = new java.util.concurrent.atomic.AtomicReference<>();
        for (int i = 0; i < count; i++) {
            choices[i] = in.readUnsignedByte();
            if (choices[i] > 2) throw new IOException("Invalid recovery");
            PackedPage page = page(in, in.readUnsignedByte(), path, drawing, file);
            if ((total += page.length) > DrawingBook.MAX_PACKED_BYTES) throw new IOException("Invalid recovery");
            pages.add(page);
        }
        byte[][] pictures = new byte[count][];
        for (int i = 0; i < count; i++) pictures[i] = picture(in);
        // Inkwash: the covers, when the recovery has them (one from before covers: none, and the block's cover a page chosen then unless the first).
        PackedPage[] covers = new PackedPage[2];
        byte[][] coverPictures = new byte[2][];
        int pictured = cover > 0 ? cover : -1;
        int section;
        try { section = in.readInt(); } catch (java.io.EOFException none) { section = 0; }
        if (section == COVERS) {
            pictured = in.readInt();
            for (int s = 0; s < 2; s++) {
                int kind = in.readUnsignedByte();
                if (kind == NONE) continue;
                covers[s] = page(in, kind, path, drawing, file);
                if ((total += covers[s].length) > DrawingBook.MAX_PACKED_BYTES) throw new IOException("Invalid recovery");
                coverPictures[s] = picture(in);
            }
        }
        CoverDesign design = CoverDesign.DEFAULT;
        NotebookOptions options = NotebookOptions.DEFAULT;
        TracingPaper tracing = null;
        int strength = TracingPaper.HALF;
        java.util.List<String> templates = null;
        long lastDay = -1;
        byte[] stacks = null;
        int[] pageRules = null, pageSeconds = null;
        int guidesShown = NotebookOptions.FULL, paper = NotebookOptions.WHITE;
        boolean lockPages = false, nibPreview = true, selfPortrait = false;
        // The sections after the covers, each once and in any order: the design, then the other options.
        while (section == COVERS) {
            int next;
            try { next = in.readInt(); } catch (java.io.EOFException none) { next = 0; }
            if (next == DESIGN) design = CoverDesign.of(in.readUTF());
            else if (next == OPTIONS) {
                byte[][] kept = new byte[3][];
                for (int k = 0; k < kept.length; k++) {
                    int length = in.readInt();
                    if (length < 0 || length > 1 << 20) throw new IOException("Invalid recovery");
                    kept[k] = new byte[length]; in.readFully(kept[k]);
                }
                // kept[1], Guides in PDF's, is passed by (cut: the guides always print).
                options = options.readTemplate(kept[0]).readTools(kept[2]);
            } else if (next == RULES) {
                byte[][] kept = new byte[4][];
                for (int k = 0; k < kept.length; k++) {
                    int length = in.readInt();
                    if (length < 0 || length > 1 << 20) throw new IOException("Invalid recovery");
                    kept[k] = new byte[length]; in.readFully(kept[k]);
                }
                options = (kept[1].length == 1 ? options.readNoErasing(kept[0]).readBrushes(kept[1]) : options.readNoErasing(kept[0]).readOneBrush(kept[1])).readMinutes(kept[2]).readSeconds(kept[2]); // kept[3], the cut Lock's, is passed by
            } else if (next == PAPER) {
                byte[][] kept = new byte[3][];
                for (int k = 0; k < kept.length; k++) {
                    int length = in.readInt();
                    if (length < 0 || length > 1 << 20) throw new IOException("Invalid recovery");
                    kept[k] = new byte[length]; in.readFully(kept[k]);
                }
                tracing = TracingPaper.read(kept[0], kept[1], kept[2], count);
            } else if (next == STRENGTH) strength = in.readByte();
            else if (next == TEMPLATES) {
                int length = in.readInt();
                if (length < 0 || length > 1 << 20) throw new IOException("Invalid recovery");
                byte[] bytes = new byte[length]; in.readFully(bytes);
                templates = BookOptions.pageTemplates(bytes, count);
            }
            else if (next == LAST) lastDay = Math.max(-1, in.readInt());
            else if (next == STACKS) {
                int length = in.readInt();
                if (length < 0 || length > 1 << 20) throw new IOException("Invalid recovery");
                stacks = new byte[length]; in.readFully(stacks);
            }
            else if (next == GUIDES_SHOWN) guidesShown = in.readByte();
            else if (next == PAGE_COLOUR) paper = in.readUnsignedByte();
            else if (next == LOCK_PAGES) lockPages = true;
            else if (next == NIB_PREVIEW_OFF) nibPreview = false;
            else if (next == SELF_PORTRAIT) selfPortrait = true;
            else if (next == PAGE_RULES) {
                int length = in.readInt();
                if (length < 0 || length > 1 << 20) throw new IOException("Invalid recovery");
                byte[] bytes = new byte[length]; in.readFully(bytes);
                pageRules = BookOptions.pageRules(bytes, count);
            }
            else if (next == PAGE_SECONDS) {
                int length = in.readInt();
                if (length < 0 || length > 1 << 20) throw new IOException("Invalid recovery");
                byte[] bytes = new byte[length]; in.readFully(bytes);
                pageSeconds = BookOptions.pageSeconds(bytes, count);
            }
            else break;
        }
        options = options.withGuidesShown(guidesShown).withPaper(paper).withLockPages(lockPages).withNibPreview(nibPreview).withSelfPortrait(selfPortrait);
        DrawingBook book = new DrawingBook(width, height, pages, index, DocumentCodec.read(new ByteArrayInputStream(pages.get(index).bytes())));
        book.restoreCanvasChoices(choices);
        book.restorePictures(pictures, pictured);
        book.restoreCovers(covers, coverPictures);
        book.restoreDesign(design);
        book.restoreOptions(options);
        book.restoreTracing(TracingPaper.stacked(tracing == null ? TracingPaper.plain(count) : tracing, stacks).withStrength(strength));
        book.restorePageTemplates(templates);
        book.restorePageRules(pageRules);
        book.restorePageSeconds(pageSeconds);
        book.restoreLastDay(lastDay);
        return new Recovered(book, path, unsaved);
    }
    /** A page {@link #page(DataOutputStream, PackedPage, String)} wrote, of {@code kind}: its bytes, or found in the notebook's file. */
    private static PackedPage page(DataInputStream in, int kind, String path, Function<String, File> drawing, java.util.concurrent.atomic.AtomicReference<HashMap<String, PackedPage>> file) throws IOException {
        int length = in.readInt();
        long crc = in.readInt() & 0xffffffffL;
        if (length < 12 || length > DrawingBook.MAX_PACKED_BYTES) throw new IOException("Invalid recovery");
        if (kind == INLINE) {
            byte[] bytes = new byte[length]; in.readFully(bytes);
            // Perhaps packed quickly, as the app packs a page it works on: a file gets it packed at the default level.
            PackedPage page = PackedPage.of(bytes, true);
            if (page.crc != crc) throw new IOException("Recovery damaged");
            return page;
        }
        if (kind != IN_FILE) throw new IOException("Invalid recovery");
        if (file.get() == null) file.set(saved(path, drawing));
        PackedPage page = file.get().get(length + ":" + crc);
        if (page == null) throw new FileChanged(path);
        return page;
    }
    private static byte[] picture(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > 1 << 20) throw new IOException("Invalid recovery");
        if (length == 0) return null;
        byte[] picture = new byte[length]; in.readFully(picture);
        return picture;
    }
    /** The pages of {@code path}'s file, by length and CRC-32, its covers' too. */
    private static HashMap<String, PackedPage> saved(String path, Function<String, File> drawing) throws IOException {
        HashMap<String, PackedPage> pages = new HashMap<>();
        File file = path.isEmpty() ? null : drawing.apply(path);
        if (file == null || !file.isFile()) throw new FileChanged(path);
        BookCodec.Pages read = BookCodec.openPages(file);
        for (PackedPage page : read.pages) pages.putIfAbsent(page.length + ":" + page.crc, page);
        for (PackedPage page : read.covers.pages) if (page != null) pages.putIfAbsent(page.length + ":" + page.crc, page);
        return pages;
    }

    static final class Recovered {
        final DrawingBook book;
        final String path;
        Recovered(DrawingBook book, String path, boolean unsaved) {
            this.book = book; this.path = path;
            if (unsaved) book.markUnsaved();
        }
    }
    /** The notebook's file changed or went since the recovery was written (renamed or synced over elsewhere). */
    static final class FileChanged extends IOException {
        final String path;
        FileChanged(String path) { super("The notebook's file changed since it was last open"); this.path = path; }
    }
    private RecoveryCodec() {}
}
