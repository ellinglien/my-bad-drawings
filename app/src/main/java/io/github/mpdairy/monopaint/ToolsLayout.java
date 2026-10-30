package io.github.mpdairy.monopaint;

import java.util.List;
import java.util.Locale;

/**
 * Every size on the Tools page, in dp, from the screen (the owner's mockup, 2026-10-28, "Tools page v2"): the bar (Back,
 * labelled, always at its left; "Tools"; Undo; Brush Lab, boxed), then on paper with no frames: the <b>scratch pad</b>
 * (a rounded box, the tool's name and line in its foot, Clear in its corner), the small grey label <b>Toolbar</b> over the
 * ten slots in two rows of five (each its drawing, as the side bar shows it, over its name), then the Library: its tabs
 * as words with the search's magnifier and the pages at their right, the chips under them ({@link ToolsPage.Chip}; a
 * row, or more where the width runs out; while searching, the search's line in their place) and its grid, {@link #COLUMNS} across at most, each card its drawing over its name, both at its left.
 * Every drawing is cut at its handle end ({@link DrawLayout#TIP_TRIM}) and scaled to one width, in an art box of one
 * height ({@link #art}): a slot's {@link #slotArt} × {@link #slotArtHeight}, a card's {@link #cardArt} × {@link
 * #cardArtHeight}; a brush with no drawing shows its stroke at that size. In portrait it is all stacked, the rows the
 * Library gets first and the scratch pad the rest (at least {@link #testLeast}); in landscape the scratch pad and the
 * slots are a column at the left, the Library beside it. Nothing scrolls: the grid is paged. Measures text as {@link
 * LabText} does. Pure, checked on the desktop.
 */
final class ToolsLayout {
    /** The bar; a card's least width (the finger's chooser's cards too). */
    static final int BAR = 56, CARD_LEAST = 142;
    /** The Library's most columns (the owner's mockup: four). */
    static final int COLUMNS = 4;
    /** A line's width ({@link Theme#FINE}): a chip's edge. */
    static final int LINE = 1;
    /** The tabs' row; the pages' buttons' width ("‹", "›") at its right. */
    static final int TAB = 40, PAGE_BUTTON = 32;
    /** A chip's height. */
    static final int CHIP = 30;
    /** An art box's height for its width: the drawings (116 px tall, trimmed about 270 wide) fill it. */
    static final float ART_TALL = .42f;
    /** A slot's art of its width, and a card's (the mockup's 300 px of 456). */
    static final float SLOT_ART = .8f, CARD_ART = .66f;
    /** The blank between a drawing and its name; between the grid's rows, and more where a group starts ({@link ToolsPage#opens}). */
    static final int NAME_GAP = 4, ROW_GAP = 4, GROUP_GAP = 8;
    /** The blank a name keeps at its card's right, so it never runs into the next card's. */
    static final int NAME_END = 8;
    /** The scratch pad's corners' radius and its words' row in it (Clear, and Back to, Load Kit or Cut Stamp when the tool has them). */
    static final int PAD_RADIUS = 5, PAD_ROW = 32;
    /** The brush's name (heading, 20 sp) and its words (caption, 13 sp, grey), and the gap between them: Brush Lab's and the kit sheet's. */
    static final float NAME_SP = TypeStyle.HEADING.sp, WORDS_SP = TypeStyle.CAPTION.sp;
    static final int WORDS_GAP = 4;
    /** The longest first sentence of a brush's words, in characters ({@link BrushVoice#about}; checked): Brush Lab shows it whole. */
    static final int FIRST_SENTENCE = 80;
    /**
     * The labels: the bar's title and buttons, the slots' label, and Back to ("Back to
     * Fineliner", the slot's first brush's name). Tweak in Brush Lab until 2026-10-28.
     */
    static final String LAB = "Brush Lab", UNDO = "Undo", TITLE = "Tools", BACK_TO = "Back to ", TOOLBAR = "Toolbar";

    final int width, height;
    /** {@code compact}: the Nomad's sizes; {@code wide}: landscape, the scratch pad and the slots a column at the left. */
    final boolean compact, wide;
    /** The page's margin at its sides and foot; the blank between things; the label's row over the slots; the blank before the Library (portrait) or beside it (landscape). */
    final int side, gap, label, apart;
    /** The left column's width (the scratch pad, the slots), and the Library's; the same in portrait. */
    final int top, sections;
    /** The scratch pad. */
    final int testWidth, testHeight;
    /** A slot's width and card height (art, name, underline's room); the ten slots' height, two rows of five. */
    final int slotWidth, slotCard, slots;
    /** The slots shown, as many as the side column shows ({@link DrawLayout#trays}), and how many across. */
    final int slotCount, slotColumns;
    /** The chips' rows (all of them shown, the most a tab has). */
    final int chipRows;
    /** The grid: columns, a card's width and height, rows, and the room left under it. */
    final int columns, cardWidth, cardHeight, rows, spare;

    ToolsLayout(int width, int height) {
        this.width = width; this.height = height;
        compact = Math.min(width, height) < DrawLayout.WIDE;
        wide = width > height;
        side = compact ? 12 : 24;
        gap = compact ? 10 : 14;
        label = compact ? 32 : 48;
        apart = compact ? 16 : 24;
        if (wide) {
            // Side by side, the paper between: on the Nomad the Library three cards across at their least.
            sections = compact ? 3 * CARD_LEAST + 4 : (width - 2 * side - apart) / 2;
            top = width - 2 * side - apart - sections;
        } else {
            top = width - 2 * side;
            sections = top;
        }
        slotCount = DrawLayout.trays(compact);
        slotColumns = (slotCount + SLOT_ROWS - 1) / SLOT_ROWS;
        slotWidth = (top - (slotColumns - 1) * gap) / slotColumns;
        slotCard = slotArtHeight() + NAME_GAP + nameLine() + DrawLayout.MARK;
        slots = SLOT_ROWS * slotCard + (SLOT_ROWS - 1) * gap;
        testWidth = top;
        columns = LabLayout.clamp(sections / CARD_LEAST, 3, COLUMNS);
        cardWidth = sections / columns;
        cardHeight = cardArtHeight() + NAME_GAP + nameLine() + DrawLayout.MARK;
        chipRows = chipRows(chipLabels(), sections, compact);
        int main = height - BAR;
        if (wide) {
            // Right: the Library's rows, as many as fit. Left: the scratch pad takes what the slots leave.
            int rest = main - libraryFixed();
            rows = Math.max(1, (rest + rowGaps()) / (cardHeight + rowGaps()));
            spare = rest - grid(rows);
            testHeight = main - gap - label - slots - side;
        } else {
            // Stacked: the scratch pad at least testLeast; the rows get the rest, and the scratch pad what they leave.
            int rest = main - gap - label - slots - apart - libraryFixed();
            rows = Math.max(1, (rest - testLeast() + rowGaps()) / (cardHeight + rowGaps()));
            spare = 0;
            testHeight = rest - grid(rows);
        }
    }
    /** The slots: two rows, of five on the Manta (the owner's "two more brush slots", 2026-10-19; ten from 2026-10-27), of four on the Nomad's eight ({@link DrawLayout#trays}). */
    static final int SLOT_ROWS = 2;
    /** The scratch pad's least height: 220 dp on the Manta (about a sixth of it), {@link #TEST_LEAST_COMPACT} on the Nomad. */
    int testLeast() { return compact ? TEST_LEAST_COMPACT : 220; }
    static final int TEST_LEAST_COMPACT = 120;
    /** A name's line (13 sp). */
    static int nameLine() { return (int) Math.ceil(LabText.line(TypeStyle.CAPTION.sp)); }
    /** A slot's art box, and a card's: a share of its width, {@link #ART_TALL} of that tall. */
    int slotArt() { return Math.round(slotWidth * SLOT_ART); }
    int slotArtHeight() { return Math.round(slotArt() * ART_TALL); }
    int cardArt() { return Math.round(cardWidth * CARD_ART); }
    int cardArtHeight() { return Math.round(cardArt() * ART_TALL); }
    /** The blank between two rows of the grid at most (a group starting). */
    static int rowGaps() { return ROW_GAP + GROUP_GAP; }
    /** The grid's height for {@code rows} rows, every row a group's first (the most it takes). */
    int grid(int rows) { return rows * cardHeight + (rows - 1) * rowGaps(); }
    /** The Library but its grid: the tabs' row, the chips' rows, the blank over the grid, the page's foot. */
    int libraryFixed() { return TAB + chips() + gap + side; }
    /** The chips' rows' height. */
    int chips() { return chipRows * CHIP + (chipRows - 1) * chipGap(); }
    /** How many cards a page holds. */
    int perPage() { return columns * rows; }

    // The tabs and the chips

    /** A chip's padding either side of its word; the blank between chips (and their rows). */
    int chipPad() { return compact ? 10 : 14; }
    int chipGap() { return compact ? 5 : 7; }
    /** Every chip's label, All first. */
    static String[] chipLabels() {
        ToolsPage.Chip[] chips = ToolsPage.Chip.values();
        String[] labels = new String[chips.length];
        for (int i = 0; i < chips.length; i++) labels[i] = chips[i].label;
        return labels;
    }
    /** A chip's width: its word (13 sp, the chosen one's 400 too: it is filled, not bolder), its padding, its edge. */
    static int chipWidth(String label, int pad) { return (int) Math.ceil(LabText.width(label, TypeStyle.CAPTION.sp, 0)) + 2 * pad + 2 * LINE; }
    /** How many rows {@code labels} take as chips across {@code room} dp, each row as many as fit (as {@link LabUi.Wrap}). */
    static int chipRows(String[] labels, int room, boolean compact) {
        int pad = compact ? 10 : 14, gap = compact ? 5 : 7, rows = 1, x = 0;
        for (String label : labels) {
            int w = chipWidth(label, pad);
            if (x > 0 && x + w > room) { rows++; x = 0; }
            x += w + gap;
        }
        return rows;
    }
    /** Tools First's chip ({@link DrawLayout#TOOLS_FIRST}) at the Toolbar label's right: its width, and its foot over the label row's, centred on the label's line. */
    int switchWidth() { return chipWidth(DrawLayout.TOOLS_FIRST, chipPad()); }
    int switchFoot() { return Math.max(0, Math.round(gap / 2f + nameLine() / 2f - CHIP / 2f)); }
    /** The blank between the tabs (the Nomad's 10, 14 before the search's magnifier: its row fits in landscape). */
    int tabGap() { return compact ? 10 : 24; }
    /** A tab's width: its word (15 sp, bold: the chosen one's). */
    static int tabWidth(String label) { return (int) Math.ceil(LabText.width(label, TypeStyle.BODY.sp, 0)); }
    /** The tabs' row: the four words and the blanks between them. */
    int tabsWidth() {
        int w = 0;
        for (ToolsPage.Tab t : ToolsPage.Tab.values()) w += tabWidth(t.label) + tabGap();
        return w - tabGap();
    }
    /** At the tabs' right: the search's magnifier, a page button's width, then the pages, "‹ 12 / 12 ›". */
    static int pagerWidth() { return 3 * PAGE_BUTTON + (int) Math.ceil(LabText.width("99 / 99", TypeStyle.CAPTION.sp, 0)) + 2 * 4; }

    // The drawings

    /**
     * Where the owner's drawing, {@code aw × ah}, goes in an art box {@code w × h} (any unit): {@link DrawLayout#TIP_TRIM}
     * of its width cut at its handle end and the rest exactly {@code w} wide, so every drawing ends one length; centred
     * top to bottom, the box clipping what runs past it. {left, top, width, height} of the whole drawing, its left past
     * the box's edge by the trim.
     */
    static float[] art(float aw, float ah, float w, float h) {
        float scale = w / (aw * (1 - DrawLayout.TIP_TRIM)), dw = aw * scale, dh = ah * scale;
        return new float[]{w - dw, (h - dh) / 2, dw, dh};
    }

    // The kit sheet (ToolsActivity#openKit)

    /** The sheet's padding, and a step's row. */
    static final int SHEET_PAD = 20, SHEET_ROW = 36;
    /** The sheet's width: 520 dp at most, the page's width inside its sides at least. */
    int sheetWidth() { return Math.min(520, width - 2 * side); }
    /** Where a kit of {@code n} brushes goes, said on its sheet: "Fills the first 3 slots", and "shows" too when it raises the bar's count. */
    static String kitWhere(int n, boolean raises) { return (raises ? "Fills and shows the first " : "Fills the first ") + (n == 1 ? "slot" : n + " slots"); }
    /** The sheet's room for words: its width inside its padding. */
    int sheetRoom() { return sheetWidth() - 2 * SHEET_PAD - 2 * LINE; }
    /** The sheet's height for {@code kit}, dp: the title, the intro wrapped, the steps, the note and the buttons, as laid out. */
    int sheetHeight(ToolKits.Kit kit) {
        float room = sheetRoom();
        int title = LabText.wrap(kit.title(), room, LabText.face(NAME_SP, 0)).size() * (int) Math.ceil(LabText.line(NAME_SP));
        int intro = LabText.wrap(kit.intro, room, LabText.face(TypeStyle.BODY.sp, 0)).size() * (int) Math.ceil(LabText.line(TypeStyle.BODY.sp));
        int note = (int) Math.ceil(LabText.line(WORDS_SP));
        return 2 * SHEET_PAD + 2 * LINE + title + WORDS_GAP + intro + gap + kit.steps() * SHEET_ROW + gap + note + gap + 40;
    }
    /** Whether step {@code i}'s words and its tool's name share its row in {@code room} dp. */
    static boolean stepFits(ToolKits.Kit kit, int i, float room) {
        float job = LabLayout.labelWidth(kit.said(i).toUpperCase(Locale.ROOT)), tool = LabText.width(kit.tool(i), WORDS_SP, 0);
        return job + 12 + tool <= room;
    }

    // The bar and the scratch pad

    /** A labelled button's width: an icon, its words (a label, caps, tracked) and its padding (as {@link LabButton}). */
    static int buttonWidth(String label) { return (int) Math.ceil(LabText.width(label.toUpperCase(Locale.ROOT), TypeStyle.LABEL.sp, TypeStyle.LABEL.tracking)) + 22 + 8 + 2 * 12 + 2 * LINE; }
    /** Undo's width: its icon alone on the Nomad. */
    int undoWidth() { return compact ? 44 : buttonWidth(UNDO); }
    /** The bar's room for its title: what Back, Undo and Brush Lab leave. */
    int titleRoom() { return width - 2 * side - LabLayout.backWidth() - 3 * 10 - undoWidth() - buttonWidth(LAB); }
    /** The wet drop's width beside Clear (Brush Lab's). */
    int wet() { return compact ? 24 : LabLayout.HEAD_ROW; }
    /** The scratch pad's words' room, inside its corners: the tool's name and line in its foot, the row in its corner. */
    int padRoom() { return testWidth - 2 * gap; }
    /** The most Back to takes in the pad's corner, its name cut with … past it. */
    int backToWidth() { return padRoom() / 3; }
    /**
     * {@code plain} (a brush's {@link BrushVoice#about}) as Brush Lab shows it: whole sentences while they fit
     * {@code lines} lines of {@code room} dp, at least the first.
     */
    static String fitWords(String plain, float room, int lines) {
        List<String> sentences = ModularWords.sentences(plain);
        String shown = "";
        for (String s : sentences) {
            String more = shown.isEmpty() ? s : shown + " " + s;
            if (!shown.isEmpty() && !LabText.fits(more, room, LabText.face(WORDS_SP, 0), lines)) break;
            shown = more;
        }
        return shown;
    }
}
