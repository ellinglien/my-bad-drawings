package io.github.mpdairy.monopaint;

/**
 * Every dp budget of Brush Lab for a root of width × height dp (the design pass, docs/archive/design-pass-2026-10-23.md, "Brush
 * Lab"): the bar; then a band in a 1 dp ink frame: <b>About This Brush</b> (a paper block: the loop, the name, the words,
 * the picker box lying across, the brush's buttons), then the column (<b>Adjust</b>: the part modules) beside the right
 * side (<b>Scratch Pad</b>: the test page), both under a {@link #HEAD_ROW} so they start level, and <b>Randomize</b>
 * across the foot. No toolbar band (the owner, 2026-10-29: "whatever toolbar slot was selected at the beginning should
 * flow through. that will give the brush lab some room"), no Basic (always what Advanced was), no Tweak It. Nothing in
 * the frame scrolls, and no text in the column or on a card is cut or smaller than 12 sp: the budgets leave room for the
 * longest words wrapped ({@link LabText}; LabLayoutChecks tries them all). Pure, checked on the desktop.
 */
final class LabLayout {
    /** The bar (no rule under it); a head row; a lock or die; below WIDE dp wide, the Nomad's sizes; a section's label row. */
    static final int BAR = 56, HEAD_ROW = 44, LOCK = 28, WIDE = 900;
    /**
     * A part module (owner's B5e design): the room its border had (2 dp); the room the rule under its head had (3 dp); the gap between the
     * name and the description. (The pattern strip's room went to the words with the design pass, 2026-10-23.)
     */
    static final int MODULE_RULE = 2, HEAD_RULE = 3, NAME_GAP = 3;
    /** Its type: the name (bold caps, tracked .08), the description and end words (caption), the choice (16 sp, down to 13). */
    static final float NAME_SP = 14, SMALL_SP = 13, CHOICE_SP = 16;
    /**
     * The widest choice words each part's module shows ({@link ModularWords#choiceWords}), in em (duospace: an "m" or
     * a "w" 0.9, {@link LabText}): Medium's "Messy charcoal", Drying's "Slow 10.2 s", Carry's "Smear"; Control's ("tilt→turns nib, press→tines open", which may break after an arrow)
     * always take three lines, its one strongest response two on the Nomad ({@link #handResponses}). LabLayoutChecks
     * proves no brush's words are wider.
     */
    static double choiceEm(BrushPart part) {
        switch (part) {
            case MEDIUM: return 9;
            case LIFE: return 7.8;
            case HAND: return Double.POSITIVE_INFINITY;
            case PAPER: return 6;
            case TIP: return 5.4;
            default: return 3.3;
        }
    }
    /**
     * Advanced's text, sp: dial labels, values and segments, the slim modules, the open card's head and the Control
     * grid. Never smaller (the owner: 12 sp at least; words wrap rather than shrink or cut; LabLayoutChecks).
     */
    static final float DENSE_SP = 12;
    /**
     * The open card's head row (its glyph, name, lock, die and ✕; the choice shows in its slim module above and its
     * swap strip beside, so the card repeats neither); a dial row's rule under it; a segment's height and its padding
     * either side of its word; the widest value (six characters, two of them wide: "9.8 mm"; LabLayoutChecks tries
     * them all).
     */
    static final int OPEN_HEAD = 30, ROW_RULE = 2, SEGMENT = 28, SEGMENT_PAD = 3;
    static final String VALUE_WIDEST = "9.8 mm";
    /**
     * "This brush": its line spacing; the longest first two sentences and the longest description, in characters
     * ({@link BrushVoice#about}; checked).
     */
    static final float WORDS_SPACING = 1;
    static final int TWO_SENTENCES = 150, DESCRIPTION = BrushVoice.ABOUT_MOST;
    /** A section's header row with buttons at its right (a part's choices): a 14 sp label, 7 dp over and under. */
    static final int SECTION_BAR = 36;
    final int width, height, main;
    /** {@code compact}: the Nomad's smaller sizes. */
    final boolean compact;
    /** Whether a module's head shows what the part holds under its name (not on the Nomad: no room at 13 sp). */
    final boolean described;
    /** The band's padding at its top, sides and bottom; the gap between the column and the right side; the gap between blocks. */
    final int top, side, bottom, split, gap;
    final int column, right;
    /**
     * The column's and the right side's head rows (Adjust and Start from ▾; Scratch Pad, Clear and the wet drop), one
     * height, so the modules and the test page start level.
     */
    final int headRow;
    /** About This Brush's header: its buttons, no label (the owner, 2026-10-29); 36 dp on the Nomad, whose column has no more room. */
    final int aboutHead;
    /**
     * About This Brush: its header ({@link #HEAD_ROW}: the brush's buttons from its left, no label) and its paper block, {@code about} tall: inside its padding ({@link #pad}) the loop ({@code loopWidth} wide),
     * the name and the words ({@code wordsWidth} wide, {@code words} tall for {@code wordsLines} lines, under the name's
     * {@link #nameHeight}), the picker box at its right ({@link #picker} long, {@link #pickerThick} tall), a note's line under it.
     */
    final int about, loopWidth, wordsWidth, words, wordsLines, nameWidth;
    /** The line under the name: "Edited" and a suggested name, a caption's line. */
    final int mark;
    /** The Open sheet's library: its cards across and down a page, each {@link #cardHeight} tall. */
    final int shelfColumns, shelfRows;
    /** The gap between the loop and the name and words. */
    static final int LOOP_GAP = 8;
    /** The name's room: on the Manta two lines of {@link BrushLabActivity#TWO_LINE_SP} for a long one (the owner: "because i love the names"), on the Nomad a heading's line. */
    int nameHeight() { return (int) Math.ceil(compact ? LabText.line(TypeStyle.HEADING.sp) : Math.max(LabText.line(TypeStyle.HEADING.sp), 2 * LabText.line(16))); }
    /** Randomize, across the foot: big (the owner's), plain words, its dice. */
    final int randomize;
    /**
     * The column: the modules, two to a row, {@code moduleGap} apart; a part open, the slim modules and the
     * open card under them; {@code pad}, an open card's reach either side and a block's padding. {@code spare} is the
     * blank under the modules.
     */
    final int pad, moduleGap, spare;
    /** "This brush"'s words' size, sp. */
    final float wordsSp;
    /** A module's width; its body inside its padding. */
    final int inner, moduleWidth, modulePad, body;
    /**
     * A module: its white head (the name and the glyph at its right in a row, the fixed description across under them),
     * the tallest head and the most lines a description takes ({@link #rowHead}: a row's heads are as tall as its longer
     * description needs, and its choice's box as its wider words need, one line or two: {@link #rowChoice}; the sample
     * gets the rest); the slider and the end words under it; the sample's least and most, as the tallest head and
     * choice leave it.
     */
    final int glyph, glyphGap, descLines, head, choiceHeight, rowGap, sliderHeight, macro, sampleMin, sampleMax, module;
    /** The tallest head, choice and slider with its end words of any row together: what leaves the least sample. */
    final int rowMost;
    /** A module's sample, dp. */
    final int sampleWidth, sampleHeight;
    /**
     * A part open: a slim module (name, lock, its choice in {@code slimLines} lines of {@link #DENSE_SP}), the
     * open card's width and height.
     */
    final int slim, slimLines, slimChoice, openWidth, open;
    /** A swap tile's least height's measure (a card's, as the library's were). */
    final int cardHeight;
    /** A page button's width (a swap strip's pages). */
    final int pageButton;
    /**
     * The test area and the test page in it (inside its 2 dp band); the room a part's swap panel has ({@code swap}: what
     * the shelf under the test page had with Tweak It, so its tiles keep their size).
     */
    final int testWidth, testHeight, padHeight, swap;
    /**
     * Clear's padding either side; the wet indicator's width beside Clear, its droplet's blank sides (the head row's
     * height square, or 24 dp on the Nomad, where the row has no more room).
     */
    final int clearPad, wet;
    /**
     * The drawing screen's picker box ({@link PickerBox}) lying across About This Brush's right, where the four sizes and
     * the grey bar were: {@code picker} long (their width), {@code pickerThick} across, two cells of {@code pickerCell}
     * (a 28 dp square, 24 on the Nomad, and its tab's room).
     */
    final int picker, pickerCell, pickerThick;
    /**
     * The drawing screen's readout ({@link SwatchReadout}) beside the picker box, at its right (the side bar's over it,
     * turned as the box is; the owner, 2026-10-29: "beside the picker show the preview size / shape as in the toolbar"):
     * a square as thick as the box.
     */
    final int readout;
    /** The scratch pad's head row: its label, Clear and the wet indicator. */
    int testHead() { return (int) Math.ceil(labelWidth("SCRATCH PAD") + 8 + labelWidth("Clear") + 2 * clearPad) + wet; }
    /** The column's head row at the least: its label, then Start from ▾ (the style's name between them cut with … where the row has no room for it). */
    int adjustHead() { return (int) Math.ceil(labelWidth("ADJUST") + 8 + labelWidth((START_FROM + "… ▾").toUpperCase(java.util.Locale.ROOT)) + 2 * 12); }
    /** Start from's words before the style ("Start from Watercolour ▾"): a new brush in a style ({@link BrushLab#pickStyle}). */
    static final String START_FROM = "Start from ";
    /** A dial row: its label, value and slider; the Control grid's row names. */
    final int label, value, slider, handName;

    LabLayout(int width, int height) {
        this.width = width; this.height = height;
        compact = width < WIDE;
        main = height - BAR;
        top = compact ? 6 : 8; side = compact ? 10 : 12; bottom = compact ? 6 : 8; split = compact ? 12 : 16; gap = compact ? 6 : 10;
        headRow = compact ? 28 : 36;
        aboutHead = compact ? 36 : HEAD_ROW;
        column = compact ? 354 : 404;
        right = width - 2 * side - column - split;
        pad = compact ? 6 : 8;
        inner = column;
        // About This Brush: the picker box at the right; the loop and the name and words beside it; at least the longest
        // first sentence (two sentences where they fit, ToolsLayout#fitWords).
        clearPad = compact ? 10 : 14;
        pickerCell = (compact ? 24 : DrawLayout.FACE_SQUARE) + DrawLayout.FACE_GAP / 2 + DrawLayout.sliderMarkRoom();
        pickerThick = 2 * pickerCell;
        readout = pickerThick;
        // On the Nomad the readout's room comes from the picker (264 until 2026-10-29), so the words keep theirs.
        picker = compact ? 264 - gap - readout : 344;
        wet = compact ? 24 : HEAD_ROW;
        wordsSp = compact ? DENSE_SP : TypeStyle.CAPTION.sp;
        loopWidth = compact ? 48 : 90;
        wordsWidth = width - 2 * side - 2 * pad - loopWidth - LOOP_GAP - gap - picker - gap - readout;
        nameWidth = wordsWidth - 40;
        wordsLines = 2;
        words = wordsHeight(wordsLines);
        mark = (int) Math.ceil(LabText.line(TypeStyle.CAPTION.sp));
        about = pad + Math.max(nameHeight() + mark + NAME_GAP + words, pickerThick + mark) + pad;
        shelfColumns = compact ? 4 : 6;
        shelfRows = 2;
        randomize = compact ? 40 : 48;
        // A module: the white head and its rule, the choice, the sample, the slider over its end words.
        moduleGap = 6;
        modulePad = compact ? 4 : 6;
        described = !compact;
        moduleWidth = (inner - moduleGap) / 2;
        body = moduleWidth - 2 * MODULE_RULE - 2 * modulePad;
        // The glyph is as tall as the name's line.
        glyph = (int) Math.ceil(LabText.line(NAME_SP));
        glyphGap = compact ? 6 : 8;
        int lines = 0;
        for (BrushPart part : BrushPart.values()) lines = Math.max(lines, descLines(part));
        descLines = lines;
        head = headOf(descLines);
        int lines2 = 1;
        for (BrushPart part : BrushPart.values()) lines2 = Math.max(lines2, choiceLines(part));
        choiceHeight = choiceHeight(lines2);
        rowGap = 4;
        sliderHeight = LOCK;
        macro = sliderHeight + (int) Math.ceil(LabText.line(SMALL_SP));
        sampleMin = compact ? 24 : 32;
        sampleMax = compact ? 80 : 120;
        int most = 0;
        for (int row = 0; row < 3; row++) most = Math.max(most, rowHead(row) + rowChoice(row) + rowMacro(row));
        rowMost = most;
        int fixed = MODULE_RULE + rowMost + HEAD_RULE + rowGap + rowGap + rowGap + modulePad + MODULE_RULE;
        // Control's words name its one strongest response on a slim module ({@link PartCard}): two lines.
        slimLines = 2;
        slimChoice = (int) Math.ceil(slimLines * LabText.line(DENSE_SP));
        slim = MODULE_RULE + 4 + LOCK + 2 + slimChoice + 4 + MODULE_RULE;
        openWidth = inner + 2 * pad;
        // The column: under the bar, the band: About This Brush's header and block, the head row (Adjust, Start from), the
        // modules, Randomize, its padding.
        int rest = main - top - aboutHead - about - gap - headRow - gap - gap - randomize - bottom;
        // A part open: the six as slim modules, then the open card, across the column's padding too.
        int restOpen = rest - (3 * slim + 2 * moduleGap) - pad;
        module = clamp((rest - 2 * moduleGap) / 3, fixed + sampleMin, fixed + sampleMax);
        spare = rest - 3 * module - 2 * moduleGap;
        open = restOpen;
        sampleWidth = body;
        sampleHeight = module - fixed;
        cardHeight = compact ? 128 : 148;
        pageButton = 44;
        // What the shelf under the test page had with Tweak It's six under it (gone 2026-10-29), so the tiles keep their size.
        swap = compact ? 312 : 289;
        testWidth = right;
        testHeight = rest;
        padHeight = testHeight - 2 * MODULE_RULE;
        label = compact ? 119 : 114;
        value = (int) Math.ceil(LabText.width(VALUE_WIDEST, DENSE_SP, 0));
        slider = openWidth - 16 - label - value - 8 - 2 * LOCK - 12;
        float names = 0;
        for (String name : BrushLab.HAND_ROWS) names = Math.max(names, LabText.width(name.toUpperCase(java.util.Locale.ROOT), DENSE_SP, LABEL_TRACKING));
        handName = (int) Math.ceil(names);
    }

    /** How many lines {@code part}'s description takes in a module's head. */
    int descLines(BrushPart part) { return LabText.wrap(ModularWords.inside(part), body, LabText.face(SMALL_SP, 0)).size(); }
    /** A module's head with {@code lines} lines of description: its padding, the name's row, the gap, the lines (none on the Nomad). */
    private int headOf(int lines) { return modulePad + glyph + (described ? NAME_GAP + (int) Math.ceil(lines * LabText.line(SMALL_SP)) : 0) + modulePad; }
    /** The heads of the modules in row {@code row} (0–2) of {@link BrushPart#SHOWN}: as tall as the longer description needs. */
    int rowHead(int row) { return headOf(Math.max(descLines(BrushPart.SHOWN.get(2 * row)), descLines(BrushPart.SHOWN.get(2 * row + 1)))); }
    /** How many lines {@code part}'s choice words take at the least, 13 sp: one, two, or Control's three. */
    int choiceLines(BrushPart part) {
        double em = choiceEm(part);
        return Double.isInfinite(em) ? handResponses() + 1 : em * LabText.em(SMALL_SP) <= body ? 1 : 2;
    }
    /** How many of the hand's strongest responses Control's module names ({@link ModularWords#handWords}): one on the Nomad. */
    int handResponses() { return compact ? 1 : 2; }
    /** The choice's box for {@code lines} lines: one line of 16 sp, or more of 13. */
    static int choiceHeight(int lines) { return (int) Math.ceil(lines == 1 ? LabText.line(CHOICE_SP) : lines * LabText.line(SMALL_SP)); }
    /**
     * How many lines a slider's end words take under it in {@code part}'s module: one, both at its ends, 6 dp apart at
     * least; or two (the Nomad's Control: "steady", then "responsive" at the right), never smaller.
     */
    int endLines(BrushPart part) {
        for (PartChoice c : PartChoice.values())
            if (c.part == part && c.words != null && LabText.width(c.words[0], SMALL_SP, 0) + 6 + LabText.width(c.words[1], SMALL_SP, 0) > body) return 2;
        return 1;
    }
    /** The slider (or the lock and die) and its end words under it, in row {@code row}. */
    int rowMacro(int row) {
        int lines = Math.max(endLines(BrushPart.SHOWN.get(2 * row)), endLines(BrushPart.SHOWN.get(2 * row + 1)));
        return sliderHeight + (int) Math.ceil(lines * LabText.line(SMALL_SP));
    }
    /** The choice's box in row {@code row}: as tall as the wider words of its two parts need. */
    int rowChoice(int row) { return choiceHeight(Math.max(choiceLines(BrushPart.SHOWN.get(2 * row)), choiceLines(BrushPart.SHOWN.get(2 * row + 1)))); }

    /** "This brush" with {@code lines} lines of text. */
    int wordsHeight(int lines) { return (int) Math.ceil(lines * LabText.line(wordsSp) * WORDS_SPACING); }
    /** The width a module's name has in its head's first row, left of the glyph (the description has the whole body). */
    int nameRoom() { return body - glyph - glyphGap; }
    /** The width a slim module's name has, after its glyph and before its lock. */
    int slimRoom() { return moduleWidth - 2 * MODULE_RULE - 12 - 16 - 6 - 6 - LOCK; }
    /** The width a module's noMacro words have (in the sample's place too), beside the lock and die. */
    int macroRoom() { return body - 4 - 2 * LOCK; }
    /** Those words' size: {@link #DENSE_SP} beside the lock and die. */
    float macroSp() { return DENSE_SP; }
    /** The width of a {@link TypeStyle#LABEL} {@code text}, dp. */
    static float labelWidth(String text) { return LabText.width(text, TypeStyle.LABEL.sp, TypeStyle.LABEL.tracking); }
    /** An outlined label button {@code text} with {@code icon} dp of icon and its gap before it, 14 dp padding either side. */
    static int button(String text, int icon) { return button(text, icon, 14); }
    /** The same with {@code pad} dp either side. */
    static int button(String text, int icon, int pad) { return (int) Math.ceil(labelWidth(text) + icon + 2 * pad); }
    /**
     * Back, as Brush Lab's and the Tools page's bars have it ({@link LabButton#back}: the arrow and "Back", 12 dp either
     * side, in a 3 dp ink rule, 44 dp tall; the owner, 2026-10-19: "back button should be clearly visible"), always first
     * at the bar's left.
     */
    static int backWidth() { return button("Back", 22 + 8, 12); }
    /** The padding either side of a bar's or a block's button's words: 14 dp, or 10 on the Nomad. */
    static int barPad(boolean compact) { return compact ? 10 : 14; }
    /** The room between About This Brush's buttons, dp. */
    static final int ACTION_GAP = 6;
    /** About This Brush's header: the buttons ({@link BrushLabActivity#ACTIONS}) from its left, Back to with "Fineliner" whole. */
    int aboutButtons() { return firstButtons() + ACTION_GAP + button("BACK TO FINELINER", 30, barPad(compact)); }
    /** New, Open, Save (its disk; its widest words, "Already saved"), Save as New and Cut Stamp. */
    int firstButtons() {
        int pad = barPad(compact), room = 0;
        for (String words : new String[]{NEW, OPEN, SAVE_AS_NEW, StampNib.CUT})
            room += button(words.toUpperCase(java.util.Locale.ROOT), 0, pad) + ACTION_GAP;
        return room + button(ALREADY.toUpperCase(java.util.Locale.ROOT), 26, pad);
    }
    /** About This Brush's buttons' words: the owner's New, Open, Save and Save as New (2026-10-29); the line under the name. */
    static final String NEW = "New", OPEN = "Open", SAVE = "Save", SAVE_AS_NEW = "Save as New", EDITED = "Edited", SUGGESTED = "Suggested: ";
    /** What Save's button says for a moment after it adds or writes over a brush, or when the library has it as it is (BrushLab's status). */
    static final String SAVED = "Saved", ALREADY = "Already saved";
    /** Back to's widest, its name cut with … past it: what the others leave on the row. */
    int backToWidth() { return width - 2 * side - firstButtons() - ACTION_GAP; }
    /** The bar: Back ("Tools"), "Brush Lab", Undo (its arrow; icon only on the Nomad). */
    static int bar(boolean compact) {
        int undo = compact ? 44 : button("Undo", 26);
        return button("Tools", 22 + 8, 12) + 10 + (int) Math.ceil(LabText.width(TITLE, TypeStyle.HEADING.sp, 0)) + 12 + undo;
    }
    /** The bar's title. */
    static final String TITLE = "Brush Lab";

    /** A dial label's caps' tracking, em. */
    static final float LABEL_TRACKING = .03f;
    /** {@code name} as a dial row shows it: caps, a break allowed after an arrow (a zero-width space; a space when measured). */
    static String dialLabel(String name) { return name.toUpperCase(java.util.Locale.ROOT).replace("↔", "↔\u200b").replace("→", "→\u200b"); }
    /**
     * The label of choice {@code c}'s dial {@code d} in its open card's row: its own ({@link PartChoice#labelOf}), but
     * Print shading's short form ({@link PrintScreen#ROW_LABEL}), which keeps one line beside Ink's seventeen dials.
     */
    static String rowLabel(PartChoice c, PartDial d) { return d == PartDial.SCREEN ? PrintScreen.ROW_LABEL : c.labelOf(d); }
    /** The lines {@code name} takes in a dial row's label column. */
    int labelLines(String name) {
        return LabText.wrap(dialLabel(name).replace("\u200b", " "), label, LabText.face(DENSE_SP, LABEL_TRACKING)).size();
    }
    /** A dial row's least height with a label of {@code lines} lines: its lock and die, or the lines, and its rule. */
    static int rowLeast(int lines) { return Math.max(LOCK, (int) Math.ceil(lines * LabText.line(DENSE_SP))) + ROW_RULE; }
    /** Whether a choice dial's {@code options} fit as segments beside its label, in the slider's and the value's room. */
    boolean segmentsBeside(String[] options) {
        // Each segment's share goes by its word's length (LabSettings.segments), so each must hold its own word.
        float room = slider + 4 + value;
        int total = 0;
        for (String option : options) total += option.length();
        for (String option : options) if (room * option.length() / total - 2 < LabText.width(option, DENSE_SP, 0) + 2 * SEGMENT_PAD) return false;
        return true;
    }
    /**
     * Whether a choice dial shows as a menu button ("darken ▾"): on the Nomad, three options or more that don't fit
     * beside its label (Blend, Line Style), so the open card needn't stack them under it (Ink's eleven dials then fit
     * under About This Brush, 2026-10-23); anywhere, five or more that don't (Screen's: stacked, the shortest would be cut).
     */
    boolean menu(String[] options) { return options.length > (compact ? 2 : 4) && !segmentsBeside(options); }
    /** {@link #menu(String[])} for dial {@code d}, and a Control row's whenever its options don't fit beside it: the Control card's rows are one height. */
    boolean menu(PartDial d, String[] options) { return menu(options) || d.part == BrushPart.HAND && d != PartDial.DIR && !segmentsBeside(options); }
    /**
     * Whether the open card has a row for {@code choice}'s dial {@code d}: every dial that draws, but on the Nomad Screen's
     * own (Cell, Angle and the newsprint three), which its card has no room for beside a medium's own; there Screen picks
     * the print alone, where its row fits (not beside Ink's eleven), then Fling where its row fits too, and Tone keeps its
     * Cell and Angle.
     */
    boolean shows(PartChoice choice, PartDial d) {
        if (!ModularFeatures.shown(d)) return false;
        if (!compact || choice == PartChoice.TONE || d != PartDial.SCREEN && d != PartDial.FLING && !d.underScreen()) return true;
        if (d.underScreen()) return false;
        // Screen's row first, then Fling's: each where the medium's own rows and those before it leave room.
        boolean fling = d == PartDial.FLING, screen = !fling || shows(choice, PartDial.SCREEN);
        int dials = 0, stacked = 0;
        for (PartDial c : choice.dials)
            if (ModularFeatures.shown(c) && !c.underScreen() && (c != PartDial.SCREEN || screen) && (c != PartDial.FLING || fling)) { dials++; if (stacks(c, choice.optionsOf(c))) stacked++; }
        return OPEN_HEAD + 10 + stacked * stackedRow() + (dials - stacked) * dialRow(dials, stacked) <= open;
    }
    /** Whether dial {@code d}, with {@code options}, lays its segments under its label: a choice dial whose segments don't fit beside it, and isn't a menu button. */
    boolean stacks(PartDial d, String[] options) { return d != PartDial.DIR && d.choice() && !segmentsBeside(options) && !menu(d, options); }
    /** A choice dial whose segments don't fit beside its label: the label on a line of its own, the segments under it, its rule. */
    static int stackedRow() { return (int) Math.ceil(LabText.line(DENSE_SP)) + 2 + SEGMENT + ROW_RULE; }
    /** The open card's dial rows' height: {@code dials} rows, {@code stacked} of them {@link #stackedRow}s (ui.js 300). */
    int dialRow(int dials, int stacked) {
        return clamp((open - OPEN_HEAD - 10 - stacked * stackedRow()) / Math.max(1, dials - stacked), rowLeast(1), 48);
    }
    /** The Control grid's column heads' room: two lines. */
    static int handHead() { return (int) Math.ceil(2 * LabText.line(DENSE_SP)); }
    /** A Control grid cell's height (ui.js 297): under the head, the grid's four rows, then Response and Fast is as rows of 44. */
    int handCell() { return handCell(2); }
    /** {@link #handCell()} with {@code rows} rows under the grid (a Spiro's drives too: five), each {@link #handRow} tall. */
    int handCell(int rows) { return clamp((open - OPEN_HEAD - 10 - handHead() - rows * handRow(rows)) / BrushLab.HAND_ROWS.length, 30, 46); }
    /** The Control card's rows under its grid, {@code rows} of them: 44, or what the grid's least cells leave each. */
    int handRow(int rows) { return Math.min(44, (open - OPEN_HEAD - 10 - handHead() - BrushLab.HAND_ROWS.length * 30) / rows); }
    /**
     * The Control card's rows under its grid for {@code b} ({@link BrushLab#handRows}; null, Grey Shift's set), when
     * they fit, each as tall as its label needs ({@link #handFits}). On the Nomad (and the Manta with a Spiro's drives and
     * Grey Shift's own) they may not: Gradient's own rows are left out first, its row kept, then its row, then Grey
     * Shift's, as other cards leave out what they have no room for ({@link #shows}).
     */
    java.util.List<PartDial> handRows(ModularBrush b) {
        java.util.List<PartDial> rows = BrushLab.handRows(b);
        if (handFits(rows)) return rows;
        int gradient = rows.indexOf(PartDial.GRADIENT);
        if (gradient >= 0 && handFits(rows.subList(0, gradient + 1))) return new java.util.ArrayList<>(rows.subList(0, gradient + 1));
        rows = BrushLab.handRows(b, false);
        if (!handFits(rows)) rows.removeIf(d -> d == null || java.util.Arrays.asList(PartDial.SHIFTS).contains(d));
        return rows;
    }
    /** Whether {@code rows} fit under the Control grid, each at least as tall as its label needs (Fast is a switch, the set swatches: one line's). */
    boolean handFits(java.util.List<PartDial> rows) {
        int least = 0;
        for (PartDial d : rows) least = Math.max(least, rowLeast(d == null || d == PartDial.DIR ? 1 : labelLines(d.label)));
        return handRow(rows.size()) >= least;
    }
    /**
     * The swap strip's columns for {@code n} tiles, so each is about 1.35 : 1 (ui.js 444–446), and no tile is much
     * shorter than a library card (7/10 of one at least), so its caption has the room (the Nomad's narrower test area
     * in Basic; its nine tiles go 3 × 3 in Advanced's shorter strip, LabLayoutChecks proving every caption whole).
     */
    int swapColumns(int n) {
        int columns = swapColumns(n, .7, 0);
        // Tiles too narrow for a tip's name ("Bristle", "Stipple": the Nomad's eleven Tip tiles in Basic, two rows of six):
        // shorter rows, three of them, each tile at least NARROWEST_TILE wide.
        return (right - 2 * MODULE_RULE) / columns >= NARROWEST_TILE ? columns : swapColumns(n, .55, NARROWEST_TILE);
    }
    /**
     * The most tiles a swap strip shows at once: Advanced's thirteen Tip tiles. More (the fourteen papers) turn in pages,
     * as the library's ({@link SwapStrip}, {@link LabPages}).
     */
    static final int SWAP_MOST = 13;
    /** A swap strip's tiles a page for {@code n} choices: all of them, else as evenly as pages of at most {@link #SWAP_MOST} hold them (the papers: two pages of seven). */
    static int swapPerPage(int n) { int pages = LabPages.pages(n, SWAP_MOST); return (n + pages - 1) / pages; }
    /** The narrowest swap tile whose caption holds every choice's name at the least size, dp ("Bristle" at 12 sp, inset 4). */
    static final int NARROWEST_TILE = 70;
    /** {@link #swapColumns(int)}'s columns, rows at least {@code least} of a card tall and tiles at least {@code narrowest} dp wide. */
    private int swapColumns(int n, double least, int narrowest) {
        double pw = right - 2 * MODULE_RULE, ph = swapRoom(), best = Double.MAX_VALUE;
        int columns = 1;
        for (int c = 1; c <= n; c++) {
            int rows = (n + c - 1) / c;
            if (rows > 1 && ph / rows < least * cardHeight || c > 1 && pw / c < narrowest) continue;
            double cost = Math.abs(Math.log((pw / c) / (ph / rows) / 1.35)) + (rows * c - n) * .08;
            if (cost < best) { best = cost; columns = c; }
        }
        return columns;
    }
    /** The room for a swap strip's tiles under its head: the strip's border, its head bar. */
    int swapRoom() { return swap - 2 * MODULE_RULE - SECTION_BAR; }
    /**
     * A swap tile's caption's least size, sp: 13, or 12 on the Nomad, whose nine Control tiles in Basic are too narrow
     * for "Custom" at 13 (61 dp of a 59 dp line, its "m" wide).
     */
    float swapLeast() { return compact ? DENSE_SP : TypeStyle.CAPTION.sp; }
    /**
     * A swap tile's caption's inset either side, dp: a card's, or 4 on the Nomad, whose nine Tip tiles in Basic are a
     * hair too narrow for "Bristle" and "Stipple" at 12 sp inside a card's 5 (60.5 dp of a 59 dp line).
     */
    float swapInset() { return compact ? 4 : LabText.CAPTION_INSET; }
    /**
     * A swap strip tile's cell for {@code n} tiles, rule included: {width, height}, never taller than wide, or than a
     * library card when it is narrower than one (its caption needs a card's lines).
     */
    int[] swapCell(int n) {
        int columns = swapColumns(n), rows = (n + columns - 1) / columns, w = (right - 2 * MODULE_RULE) / columns;
        return new int[]{w, Math.min(swapRoom() / rows, Math.max(w, cardHeight))};
    }
    /**
     * The swap panel for {@code n} tiles, dp: {x, width, height}. Beside the column (just right of it, as the test area),
     * as wide as the test area, as tall as its head and tiles ({@link SwapStrip}: the head bar, rows of {@link #swapCell}
     * with a 2 dp line between, its 2 dp border under them): the tiles fill the room the old strip had under the test page.
     */
    int[] swapPanel(int n) {
        int rows = (n + swapColumns(n) - 1) / swapColumns(n);
        return new int[]{side + column + split, right, SECTION_BAR + rows * swapCell(n)[1] + (rows - 1) * MODULE_RULE + MODULE_RULE};
    }
    static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
