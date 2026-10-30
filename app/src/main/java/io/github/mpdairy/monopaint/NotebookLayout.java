package io.github.mpdairy.monopaint;

/**
 * The shelf of notebooks and the inside of one, in dp (docs/archive/superpowers/specs/2026-10-18-notebooks-design.md, "The
 * screens"). Nothing scrolls: the shelf's covers come a page at a time, as many as fit, and the notebook's strip shows a
 * window of its pages. Pure, checked on the desktop for both tablets in both turns ({@code NotebookLayoutChecks}).
 *
 * <p>The shelf: a bar (the heading "Notebooks"), the grid of tiles (a cover with the page's proportions, then the name in
 * up to {@link #NAME_LINES} lines ({@link #COMPACT_NAME_LINES} on the Nomad) and "24 pages · Oct 17"), and a row of page buttons. Inside a notebook
 * (the owner, 2026-10-19: "consolidate these to one bar ... use the same page turn bar on the bottom as the drawing
 * page"): one bar, ‹ Notebooks, the name (up to {@link #TITLE_LINES} lines, a tap renames it), Open, Use on Cover and
 * Delete Page (the last two their icons alone where the bar can't hold their words, the Nomad in portrait,
 * {@link #labelled}); the page as big as fits under it; the strip of pages (the covers first and last); and the
 * drawing screen's page bar along the foot ({@link PageTurner}, without its Notebook button: ‹, "3 / 24", ›).
 */
final class NotebookLayout {
    /** A row of buttons or a bar's height; a button's least width. */
    static final int BAR = 56, BUTTON = 48;
    /** The lines a tile's name may take (the Nomad's narrower tiles take one more); the notebook's name over its page. */
    static final int NAME_LINES = 3, COMPACT_NAME_LINES = 4, TITLE_LINES = 2;
    /** The lines a tile's count and date may take ("100 pages · Oct 18 2025" wraps in a narrow tile). */
    static final int COUNT_LINES = 2;
    /** Room between a cover and its name; the current page's tab under the strip, and its gap. */
    static final int UNDER_COVER = 8, TAB = 5, TAB_GAP = 4;
    /**
     * The bar's room between its parts; an action's padding either side of its words (as {@link LabButton}'s); the least
     * room its name has with the actions' words, else they are their icons alone.
     */
    static final int BAR_GAP = 8, ACTION_PAD = 12, TITLE_LEAST = 240;
    /** The bar's labels: back to the shelf, and the three actions (docs/TEXT-STYLE.md: Title Case, two words at most). */
    static final String BACK = "Notebooks", OPEN = "Open", DELETE = "Delete Page";
    /** The drawing screen menu's row to the shelf, to open another notebook. */
    static final String OPEN_NOTEBOOK = "Open Notebook";

    final int width, height;
    final boolean compact;
    /** A page's height over its width, for covers, the page and the strip. */
    final double aspect;
    final int margin, gap, nameLines;

    // The shelf
    final int columns, rows, tileWidth, coverHeight, tileHeight, columnGap, rowGap;
    // Inside a notebook
    final int pageWidth, pageHeight, spreadHeight, thumbWidth, thumbHeight, stripGap, visible;
    /** Whether the bar's actions show their words (else their icons alone, 48 dp each); the name's room between them. */
    final boolean labelled;
    final int titleRoom;
    /** The page bar along the foot, as the drawing screen's ({@link DrawLayout#pageBar}), and its buttons' width. */
    final int pageBar, pageButton;

    NotebookLayout(int width, int height, double aspect) {
        this.width = width; this.height = height; this.aspect = aspect;
        compact = Math.min(width, height) < DrawLayout.WIDE;
        margin = compact ? 24 : 40; gap = compact ? 12 : 16; nameLines = compact ? COMPACT_NAME_LINES : NAME_LINES;
        columnGap = compact ? 16 : 32; rowGap = compact ? 16 : 24;
        int gridWidth = width - 2 * margin, gridHeight = height - 2 * margin - 2 * BAR - 2 * gap;
        int least = compact ? 150 : 180, bestColumns = 2, bestRows = 0;
        for (int c = 2; c <= 6; c++) {
            int w = (gridWidth - (c - 1) * columnGap) / c;
            if (w < least) break;
            int r = Math.max(0, (gridHeight + rowGap) / (tile(w) + rowGap));
            if (c * r > bestColumns * bestRows) { bestColumns = c; bestRows = r; }
        }
        columns = bestColumns; rows = Math.max(1, bestRows);
        tileWidth = (gridWidth - (columns - 1) * columnGap) / columns;
        coverHeight = (int) Math.floor(tileWidth * aspect);
        tileHeight = tile(tileWidth);

        int across = width - 2 * margin;
        labelled = titleRoom(across, true) >= TITLE_LEAST;
        titleRoom = titleRoom(across, labelled);
        pageBar = DrawLayout.pageBar(compact); pageButton = DrawLayout.pageButton(compact);
        boolean wide = width > height;
        thumbHeight = compact ? (wide ? 72 : 96) : (wide ? 104 : 140);
        thumbWidth = Math.max(24, (int) Math.floor(thumbHeight / aspect));
        stripGap = 8;
        visible = Math.max(2, Math.min(12, (width - 2 * margin + stripGap) / (thumbWidth + stripGap)));
        spreadHeight = height - margin - BAR - thumbHeight - TAB - TAB_GAP - 3 * gap - pageBar;
        int h = Math.min(spreadHeight, (int) Math.floor(across * aspect));
        pageHeight = h; pageWidth = (int) Math.floor(h / aspect);
    }

    /** A label's width on a button (caps, tracked), dp. */
    private static int words(String label) { return (int) Math.ceil(LabLayout.labelWidth(label.toUpperCase(java.util.Locale.ROOT))); }
    /** ‹ Notebooks: its caret, the gap, its word and its padding (no outline). */
    static int backWidth() { return 22 + 8 + words(BACK) + 2 * ACTION_PAD; }
    /** An action's width: its words, padding and 2 dp outline; or, not {@code labelled}, its icon alone ({@link #BUTTON}). */
    static int actionWidth(String label, boolean labelled) { return labelled ? words(label) + 2 * ACTION_PAD + 2 * DrawLayout.RULE : BUTTON; }
    /** The cover picture's action at its widest: Use on Cover, or No Picture on the front cover. */
    static int coverWidth(boolean labelled) { return Math.max(actionWidth(NotebookCovers.USE_BUTTON, labelled), actionWidth(NotebookCovers.NO_PICTURE_BUTTON, labelled)); }
    /** The name's room in a bar {@code across} dp wide: what ‹ Notebooks and the actions leave, a gap between each. Draw always says its word. */
    static int titleRoom(int across, boolean labelled) {
        return across - backWidth() - actionWidth(OPEN, true) - coverWidth(labelled) - actionWidth(DELETE, labelled) - 4 * BAR_GAP;
    }

    /** A tile's height {@code w} wide: its cover, the gap, the name's lines and the count's. */
    private int tile(int w) { return (int) Math.floor(w * aspect) + UNDER_COVER + textHeight(); }
    int textHeight() { return (int) Math.ceil(nameLines * LabText.line(TypeStyle.BODY.sp) + COUNT_LINES * LabText.line(TypeStyle.CAPTION.sp)); }

    /** Tiles on a shelf page; the first page's first is New notebook. */
    int perPage() { return columns * rows; }
    /** The shelf's pages for {@code notebooks} notebooks, with New notebook. */
    int shelfPages(int notebooks) { return Math.max(1, (notebooks + 1 + perPage() - 1) / perPage()); }
    /** The notebooks on shelf page {@code page}: the first's index and how many (page 0 starts after New notebook). */
    int[] shelfPage(int page, int notebooks) {
        int first = page == 0 ? 0 : page * perPage() - 1, count = page == 0 ? perPage() - 1 : perPage();
        return new int[]{Math.min(first, notebooks), Math.max(0, Math.min(count, notebooks - first))};
    }
    /** The shelf page notebook {@code index} is on. */
    int shelfPageOf(int index) { return (index + 1) / perPage(); }

    /** The first page the strip shows, for page {@code current} of {@code count}: a window round the current, the + tile after the last. */
    int stripStart(int current, int count) { return window(current, count + 1, visible); }
    /** The first of {@code tiles} a strip of {@code visible} shows: a window round {@code current} (the drawing screen's page bar too, {@link PageTurner}). */
    static int window(int current, int tiles, int visible) {
        if (tiles <= visible) return 0;
        return Math.max(0, Math.min(current - visible / 2, tiles - visible));
    }

    /** The shelf's whole height, its parts added up: the margins, the bar, the grid, the page buttons. */
    int shelfUsed() { return 2 * margin + 2 * BAR + 2 * gap + rows * tileHeight + (rows - 1) * rowGap; }
    /** The notebook screen's whole height, its parts added up: the margin, the bar, the page, the strip, the gaps between them and over the page bar, the page bar. */
    int notebookUsed() { return margin + BAR + spreadHeight + thumbHeight + TAB + TAB_GAP + 3 * gap + pageBar; }

    /** A name's lines in a tile {@code width} dp wide. */
    static int nameLines(String name, int width) { return LabText.wrap(name, width, LabText.face(TypeStyle.BODY.sp, 0)).size(); }
    /** A name's lines in the bar, {@code width} dp wide (body, bold: as wide as regular). */
    static int titleLines(String name, int width) { return LabText.wrap(name, width, LabText.face(TypeStyle.BODY.sp, 0)).size(); }

    /** The words on and over the library card's screens (Title Case: docs/TEXT-STYLE.md). */
    static final String TEMPLATE = "Template", TOOLS = "Tools",
            BRUSHES = "Brushes", ALL = "All", RANDOM_FROM = "Random From", DONE = "Done";

    /** The four screens a name must fit, dp, and their pages' proportions. */
    static final int[][] SCREENS = {{1024, 1365}, {1365, 1024}, {749, 998}, {998, 749}};
    static double aspectOf(int[] screen) {
        DrawLayout bar = new DrawLayout(Math.min(screen[0], screen[1]), Math.max(screen[0], screen[1]));
        return bar.pageHeight() / (double) bar.pageWidth();
    }
    /**
     * Whether {@code shown} (a notebook's name as shown) fits: in its lines in every screen's tile and in
     * {@link #TITLE_LINES} of every screen's bar inside a notebook. A rename that doesn't is refused ("Name too long"),
     * so no name is cut.
     */
    static boolean fits(String shown) {
        for (int[] s : SCREENS) {
            NotebookLayout l = new NotebookLayout(s[0], s[1], aspectOf(s));
            if (!LabText.fits(shown, l.tileWidth, LabText.face(TypeStyle.BODY.sp, 0), l.nameLines)
                    || !LabText.fits(shown, l.titleRoom, LabText.face(TypeStyle.BODY.sp, 0), TITLE_LINES)) return false;
        }
        return true;
    }
}
