package io.github.mpdairy.monopaint;

/**
 * The size modal's, the grey flyout's and the Sizes and Greys window's geometry, dp ({@link SizeModal} builds them
 * with these): one layout on the Manta and the Nomad, either way up, the window over the middle of the screen with no
 * scrolling ({@link #fits}). The grey editor is the chosen grey big beside the grey bar ({@link GreyBar}), as long as
 * the window's inside leaves it. Pure, checked on the desktop.
 */
final class SwatchSetLayout {
    /** The modal's width; the size modal's true-size view's height; the whole set's, shorter, so it fits the Nomad turned. */
    static final int WIDTH = 300, VIEW = 120, SET_VIEW = 80;
    /** A face in a row of four (a flyout grey's width once), and the gap before the next. */
    static final int FACE = 44, FACE_GAP = 4;
    /** Under a face: the gap and the marker tab's room ({@link SwatchFace}). */
    static final int TAB_ROOM = 9;
    /** The frame: its edge's room (3 dp, {@code Theme.RULE}, of which the fine ink edge draws 1) and 12 dp inside it; the window's least margin to the screen's sides. */
    static final int FRAME = 3 + 12, MARGIN = 16;
    /** The head row (its heading and Done); a value's line (px and mm, a grey's words); the slider; a stop's least side, and the gap between stops. */
    static final int HEAD = 44, VALUE = 24, SLIDER = 44, STOP = 44, STOP_GAP = 6;
    /** The grey editor: the chosen grey's face, its width ({@link #BIG} − the pad, square); the gap to the bar. */
    static final int BIG = 62, BAR_GAP = 8;
    /** The grey bar's length: the rest of the window's inside; it is as tall as the big face's view. */
    static final int BAR = WIDTH - 2 * FRAME - BIG - BAR_GAP;
    /** The gaps over a row (dp): a face row, a value under its faces, the true-size view, the slider, the stops, the grey editor; the rule's, around it. */
    static final int ROW_GAP = 6, VALUE_GAP = 2, RULE_GAP = 12, RULE = 2;

    private SwatchSetLayout() { }

    /** How tall a face {@code width} dp wide is, dp: its square, the padding over it and the marker's room under it. */
    static int faceTall(int width) { return width - DrawLayout.FACE_PAD + TAB_ROOM; }
    /** The grey editor's height: the big face's view, the bar as tall. */
    static int editorTall() { return faceTall(BIG); }
    /** The window's width on a screen {@code screenWidth} wide. */
    static int width(int screenWidth) { return Math.min(WIDTH, screenWidth - 2 * MARGIN); }
    /** The inside of a modal {@code width} wide. */
    static int inside(int width) { return width - 2 * FRAME; }

    /** How many rows the size stops wrap onto in {@code room}, each its px in a chip at least {@link #STOP} wide. */
    static int stopRows(int room) {
        int rows = 1, x = 0;
        for (double stop : ToolSwatches.STOPS) {
            int w = Math.max(STOP, (int) Math.ceil(LabText.width(ToolSwatches.pxText(stop), TypeStyle.CAPTION.sp, 0)) + 2 * 8);
            if (x > 0 && x + w > room) { rows++; x = 0; }
            x += w + STOP_GAP;
        }
        return rows;
    }
    /** The size editor under its faces, from its value line: the true-size view {@code view} tall, the slider, Fine and Broad, the stops. */
    static int sizeEditor(int view, int room) {
        int stops = stopRows(room);
        return VALUE_GAP + VALUE + ROW_GAP + view + ROW_GAP + SLIDER + (int) Math.ceil(LabText.line(TypeStyle.CAPTION.sp))
                + ROW_GAP + stops * STOP + (stops - 1) * STOP_GAP;
    }
    /** The grey editor under its faces, from its value line. */
    static int greyEditor() { return VALUE_GAP + VALUE + ROW_GAP + editorTall(); }
    /**
     * The Sizes and Greys window's height on a screen {@code screenWidth} wide, {@code greys} or sizes only: the frame,
     * the head, the size faces and their editor; with greys, the rule, the grey faces and theirs.
     */
    static int set(int screenWidth, boolean greys) {
        int room = inside(width(screenWidth));
        int h = 2 * FRAME + HEAD + ROW_GAP + faceTall(FACE) + sizeEditor(SET_VIEW, room);
        if (greys) h += RULE_GAP + RULE + RULE_GAP + faceTall(FACE) + greyEditor();
        return h;
    }
    /** The four faces of a row, side by side. */
    static int faceRow() { return ToolSwatches.COUNT * FACE + (ToolSwatches.COUNT - 1) * FACE_GAP; }
    /** The grey flyout's width and height: the frame round the grey editor. */
    static int flyoutWidth() { return 2 * FRAME + BIG + BAR_GAP + BAR; }
    static int flyoutHeight() { return 2 * FRAME + VALUE + ROW_GAP + editorTall(); }
    /** Whether the window, with greys, fits a screen {@code width} × {@code height} dp whole, no scrolling, and the flyout too. */
    static boolean fits(int width, int height) {
        return set(width, true) <= height && width(width) <= width && faceRow() <= inside(width(width))
                && BIG + BAR_GAP + BAR <= inside(width(width)) && flyoutWidth() <= width && flyoutHeight() <= height;
    }
}
