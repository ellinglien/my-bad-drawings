package io.github.mpdairy.monopaint;

/**
 * Pictures brought onto the page (the owner, 2026-10-22: Import Image and Import PDF in the file menu;
 * docs/archive/superpowers/specs/2026-10-22-import-design.md; one Import since 2026-10-29, the owner: "can we merge the
 * pdf and image import flow? ... so it'd be just one import thing"): what both share, and the numbers each decides by.
 * <ul>
 * <li><b>Greys</b> ({@link #greys}): a picture's colours as the page holds tone, smooth (the panel's dots and the copies'
 * greys are made from it later, as from any mark), each pixel its brightness over white; paper-white is left clear, as an
 * own template's near-white is ({@link NotebookTemplate#tones}), so the page and the layers under it show through.
 * {@link #lay} puts them on the layer drawn on as ink is laid on it (transparent, darkening), one Undo step, never
 * lighter than what the layer holds there.</li>
 * <li><b>Placing an image</b> ({@link Frame}): fitted inside the page as shown and centred at first ({@link #fit}), then moved,
 * resized from its corner or pinched, its shape kept, until Place or Cancel.</li>
 * <li><b>A PDF's notebook</b>: a page a stack of two layers ({@link PageStacks}), the PDF's page at the bottom, locked
 * ({@link #pdfPage}: its clock locked, as a timed page's at zero, so "Page locked" refuses marks, Undo and Merge Down
 * there), and a clear layer on it, drawn on ({@link #stacks}); so at most {@link #MOST_PDF_PAGES} of its pages, and
 * {@link #fits} keeps them inside the notebook's 200 MB.</li>
 * </ul>
 * Pure, checked on the desktop ({@code PictureImportChecks}).
 */
final class PictureImport {
    private PictureImport() {}

    /** The file menu's row and the notes (docs/TEXT-STYLE.md: two words a button, about three a note). */
    static final String IMPORT = "Import", EXPORT_NOW = "Export Now";
    static final String PLACE = "Place", CANCEL = "Cancel";
    /** A PDF picked: its pages as a new notebook, or the page shown placed on the page drawn on, as a picture. */
    static final String NEW_NOTEBOOK = "New Notebook", THIS_PAGE = "This Page";
    /** Notes: a picture or PDF that doesn't read, a PDF without pages. */
    static final String UNREADABLE = "Can't read it", NO_PAGES = "No pages";
    /** The PDF's import: its progress ("Importing 3 / 12") and what it says once done when it left pages out ("First 124 pages"). */
    static String importing(int done, int of) { return "Importing " + done + " / " + of; }
    static String firstPages(int n) { return "First " + n + " pages"; }

    // The picker (the owner, 2026-10-26: "there's no way to cancel"): the app's own ({@code ImportPicker}), with Cancel

    /** The picker's top folders, under the storage root: the Supernote file manager's folders, then Pictures and Download. */
    static final String[] FOLDERS = {"Document", "EXPORT", "MyStyle", "Note", "SCREENSHOT", "INBOX", "Pictures", "Download"};
    /** The files the picker lists, by their ending (any case): pictures and PDFs together. */
    static final String[] IMPORTABLE = {".png", ".jpg", ".jpeg", ".webp", ".gif", ".bmp", ".pdf"};
    /** Whether a picked file is a PDF, by its type ({@code null} when unknown) or else its name's ending. */
    static boolean pdf(String type, String name) {
        return "application/pdf".equals(type) || name != null && name.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf");
    }
    /** The picker's top folder's name (the storage root, its folders {@link #FOLDERS}), and its note in a folder with nothing to import. */
    static final String STORAGE = "Storage", NOTHING_FOUND = "Nothing found";
    /**
     * What the picker shows in {@code folder} (the owner, 2026-10-29: "more like supernote file explorer"): its folders A
     * to Z, then its pictures and PDFs newest first, hidden ones skipped; at {@code root}, only {@link #FOLDERS} that are there.
     */
    static java.util.List<java.io.File> entries(java.io.File root, java.io.File folder) {
        java.util.List<java.io.File> folders = new java.util.ArrayList<>(), files = new java.util.ArrayList<>();
        if (folder.equals(root)) {
            for (String name : FOLDERS) { java.io.File top = new java.io.File(root, name); if (top.isDirectory()) folders.add(top); }
            return folders;
        }
        java.io.File[] all = folder.listFiles();
        if (all == null) return folders;
        for (java.io.File file : all) {
            String name = file.getName().toLowerCase(java.util.Locale.ROOT);
            if (name.startsWith(".")) continue;
            if (file.isDirectory()) { folders.add(file); continue; }
            for (String ending : IMPORTABLE) if (name.endsWith(ending)) { files.add(file); break; }
        }
        folders.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        // Each file's time read once: a file written while sorting can't upset the sort.
        java.util.Map<java.io.File, Long> changed = new java.util.HashMap<>();
        for (java.io.File file : files) changed.put(file, file.lastModified());
        files.sort((a, b) -> { int by = Long.compare(changed.get(b), changed.get(a)); return by != 0 ? by : a.getName().compareTo(b.getName()); });
        folders.addAll(files);
        return folders;
    }
    /** {@code folder} from {@code root}, "Document/Scribulor" ("" for the root): what the picker remembers, its crumbs. */
    static String path(java.io.File root, java.io.File folder) {
        return root.toPath().relativize(folder.toPath()).toString().replace(java.io.File.separatorChar, '/');
    }
    /** Where the picker opens: {@code last} ({@link #path}), or the nearest folder above it still there; the root for none, or one outside it. */
    static java.io.File start(java.io.File root, String last) {
        if (last == null || last.isEmpty() || last.startsWith("/") || java.util.Arrays.asList(last.split("/")).contains("..")) return root;
        java.io.File at = new java.io.File(root, last);
        while (at != null && !at.equals(root) && !at.isDirectory()) at = at.getParentFile();
        return at == null ? root : at;
    }

    // Greys

    /** Brightness this light and over is paper: left clear ({@link NotebookTemplate}'s own templates' near-white). */
    static final int PAPER = 248;
    /** Pixel {@code argb}'s brightness over white paper, 0 (black) to 255: its luma, its see-through share white. */
    static int grey(int argb) {
        int a = argb >>> 24;
        int luma = (((argb >> 16) & 255) * 299 + ((argb >> 8) & 255) * 587 + (argb & 255) * 114) / 1000;
        return 255 - (255 - luma) * a / 255;
    }
    /** {@code argb} as the greys {@link #lay} lays, in place: each pixel its {@link #grey}, −1 (clear) where it is paper. */
    static int[] greys(int[] argb) {
        for (int i = 0; i < argb.length; i++) { int g = grey(argb[i]); argb[i] = g >= PAPER ? -1 : g; }
        return argb;
    }
    /**
     * Lays {@code greys} ({@link #greys}: {@code w} a row, −1 clear) at {@code x, y} on the layer of {@code page} drawn on,
     * as one Undo step: a pixel takes its grey as ink does ({@link ToneDocument#setTones}), only where that is darker than
     * the layer there, so on a layer with marks the picture darkens them and never lightens them. Throws as a stroke would
     * on a hidden layer.
     */
    static void lay(ToneDocument page, int x, int y, int w, int h, int[] greys) {
        for (int row = 0; row < h; row++) for (int col = 0; col < w; col++) {
            int i = row * w + col, px = x + col, py = y + row;
            if (greys[i] < 0) continue;
            if (px < 0 || py < 0 || px >= page.width || py >= page.height || greys[i] >= page.layerTone(px, py)) greys[i] = -1;
        }
        page.begin();
        try { page.setTones(x, y, w, h, greys); }
        finally { page.finish(); }
    }

    /**
     * Place: the picture {@link #lay laid} on the layer drawn on, as any mark, so the eraser takes it and what was drawn
     * there before alike, and Undo takes it away (the owner, 2026-10-26; before, it went on a new layer above, drawn on,
     * which left the marks under it out of the eraser's reach).
     */
    static void place(DrawingBook book, int x, int y, int w, int h, int[] greys) { lay(book.current(), x, y, w, h, greys); }

    // Placing an image

    /** {left, top, width, height} of a {@code w × h} picture fitted inside a {@code pageW × pageH} page, centred, its shape kept. */
    static int[] fit(int w, int h, int pageW, int pageH) {
        if (w <= 0 || h <= 0) throw new IllegalArgumentException("Empty picture");
        double scale = Math.min((double) pageW / w, (double) pageH / h);
        int fw = Math.max(1, (int) Math.round(w * scale)), fh = Math.max(1, (int) Math.round(h * scale));
        return new int[]{(pageW - fw) / 2, (pageH - fh) / 2, fw, fh};
    }

    /**
     * Where a picture being placed lies on the page, page px: its top left, width and height, its shape always the
     * picture's. Immutable. It stays at least {@link #KEPT} px on the page, between {@link #SMALLEST} px across its
     * shorter side and {@link #LARGEST} times the page.
     */
    static final class Frame {
        /** The least of it left on the page, px each way (about 5 mm), so it can't be lost off the edge. */
        static final float KEPT = 64;
        /** Its shorter side's least, px (about 4 mm). */
        static final float SMALLEST = 48;
        /** Its longer side's most, times the page's longer side. */
        static final float LARGEST = 3;
        /** The corner handle's side, px, and how far from the corner a pen or finger still takes it. */
        static final float HANDLE = 56, REACH = 96;

        final float x, y, w, h;
        final int pageW, pageH;

        Frame(float x, float y, float w, float h, int pageW, int pageH) { this.x = x; this.y = y; this.w = w; this.h = h; this.pageW = pageW; this.pageH = pageH; }
        /** A {@code w × h} picture fitted inside the page, centred ({@link #fit}). */
        static Frame fitted(int w, int h, int pageW, int pageH) { return fitted(w, h, new int[]{0, 0, pageW, pageH}, pageW, pageH); }
        /**
         * A {@code w × h} picture fitted inside the part of the page {@code shown} ({left, top, right, bottom}: the page
         * may be larger than the screen at 100%, under the bars), {@link #HANDLE} in from its edges so the handle shows,
         * centred there; the whole page when none of it is shown.
         */
        static Frame fitted(int w, int h, int[] shown, int pageW, int pageH) {
            int inset = Math.round(HANDLE);
            int l = Math.max(0, shown[0]) + inset, t = Math.max(0, shown[1]) + inset, r = Math.min(pageW, shown[2]) - inset, b = Math.min(pageH, shown[3]) - inset;
            if (r - l < SMALLEST || b - t < SMALLEST) { l = 0; t = 0; r = pageW; b = pageH; }
            int[] f = fit(w, h, r - l, b - t);
            return new Frame(l + f[0], t + f[1], f[2], f[3], pageW, pageH);
        }
        /** Moved by {@code dx, dy}, kept {@link #KEPT} on the page. */
        Frame moved(float dx, float dy) { return new Frame(x + dx, y + dy, w, h, pageW, pageH).kept(); }
        /** Its corner dragged to {@code cornerX, cornerY}, its top left staying: as wide as reaches the corner either way, its shape kept. */
        Frame cornerTo(float cornerX, float cornerY) {
            float across = Math.max(cornerX - x, (cornerY - y) * w / h);
            return sized(across).kept();
        }
        /** Scaled by {@code factor} about page point {@code cx, cy} (a pinch: the point between the fingers stays put), then moved by {@code dx, dy}. */
        Frame scaled(float factor, float cx, float cy, float dx, float dy) {
            Frame s = sized(w * factor);
            float f = s.w / w;
            return new Frame(cx - (cx - x) * f + dx, cy - (cy - y) * f + dy, s.w, s.h, pageW, pageH).kept();
        }
        /** As wide as {@code across} (height following), held between {@link #SMALLEST} and {@link #LARGEST}; its top left staying. */
        private Frame sized(float across) {
            float aspect = w / h;
            float least = aspect >= 1 ? SMALLEST * aspect : SMALLEST, most = Math.max(pageW, pageH) * LARGEST * Math.min(1, aspect);
            float nw = Math.max(least, Math.min(most, across));
            return new Frame(x, y, nw, nw / aspect, pageW, pageH);
        }
        /** Moved, if it must be, to leave {@link #KEPT} px of it on the page each way (all of it, when it is smaller). */
        private Frame kept() {
            float kx = Math.min(KEPT, w), ky = Math.min(KEPT, h);
            float nx = Math.max(kx - w, Math.min(pageW - kx, x)), ny = Math.max(ky - h, Math.min(pageH - ky, y));
            return nx == x && ny == y ? this : new Frame(nx, ny, w, h, pageW, pageH);
        }
        /** Whether page point {@code px, py} takes the corner handle (its lower right). */
        boolean onHandle(float px, float py) { return Math.hypot(px - (x + w), py - (y + h)) <= REACH; }
        /** {left, top, right, bottom} rounded, the whole of it (perhaps past the page). */
        int[] rect() { return new int[]{Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h)}; }
        /** {@link #rect} with the handle and its outline, so a move puts back all it drew. */
        int[] drawn() {
            int[] r = rect();
            int out = Math.round(HANDLE / 2) + 4;
            return new int[]{r[0] - 4, r[1] - 4, r[2] + out, r[3] + out};
        }
    }

    // A PDF's notebook

    /** The most PDF pages a notebook holds: two layers a page in its {@link DrawingBook#MAX_PAGES} (124 of 250), one to spare for a page the + adds. */
    static final int MOST_PDF_PAGES = (DrawingBook.MAX_PAGES - 1) / 2;
    /** How many of a PDF's {@code count} pages are imported. */
    static int pdfPages(int count) { return Math.max(0, Math.min(count, MOST_PDF_PAGES)); }
    /** Room left in the 200 MB ({@link DrawingBook#MAX_PACKED_BYTES}) for the files' own records and a page the + adds. */
    static final long SPARE = 1 << 20;
    /** Whether a page pair of {@code pair} packed bytes fits after {@code packed} bytes already taken. */
    static boolean fits(long packed, long pair) { return packed + pair + SPARE <= DrawingBook.MAX_PACKED_BYTES; }
    /** Tracing paper for {@code pdfPages} pages of two layers each: every other page stacked on the one below ({@link TracingPaper#LINKED}). */
    static TracingPaper stacks(int pdfPages) {
        int[] steps = new int[2 * pdfPages];
        for (int i = 1; i < steps.length; i += 2) steps[i] = TracingPaper.LINKED;
        return new TracingPaper(TracingPaper.PLAIN, -1, steps);
    }
    /** The page a PDF's notebook opens on: its first page's clear layer. */
    static final int OPENS_ON = 1;
    /**
     * A PDF's page as its notebook's bottom layer: {@code greys} ({@link #greys}, {@code width} a row) laid on a blank
     * page and locked, with no Undo history: no marks, no erasing, no Undo there.
     */
    static ToneDocument pdfPage(int width, int height, int[] greys) {
        ToneDocument drawn = new ToneDocument(width, height);
        lay(drawn, 0, 0, width, height, greys);
        ToneDocument.Snapshot laid = drawn.layerSnapshot();
        ToneDocument page = new ToneDocument(laid);
        laid.release();
        page.placeClock(PageClock.NONE.lock(0));
        return page;
    }
    /**
     * Writes a PDF's notebook to {@code out}, as a notebook's file ({@link BookCodec}): {@code pdf}, its PDF pages packed
     * ({@link #pdfPage}), each with {@code clear} (a blank page packed, {@link BlankPages#made}) stacked on it
     * ({@link #stacks}), opening on the first clear layer, in covers of {@code design}, made {@code day} (an epoch day).
     * The pages may lie in a file ({@link PackedPage#in}): they are copied from it as they are.
     */
    static void writeBook(java.io.OutputStream out, int width, int height, java.util.List<PackedPage> pdf, PackedPage clear, CoverDesign design, long day) throws java.io.IOException {
        if (pdf.isEmpty() || pdf.size() > MOST_PDF_PAGES) throw new java.io.IOException("Invalid page list");
        java.util.List<PackedPage> pages = new java.util.ArrayList<>();
        for (PackedPage p : pdf) { pages.add(p); pages.add(clear); }
        BookOptions.Covers covers = new BookOptions.Covers();
        covers.design = design; covers.tracing = stacks(pdf.size()); covers.lastDay = day;
        java.util.List<byte[]> pictures = new java.util.ArrayList<>();
        for (int i = 0; i < pages.size(); i++) pictures.add(null);
        BookCodec.write(out, width, height, pages, OPENS_ON, new int[pages.size()], -1, pictures, covers, new long[2]);
    }
}
