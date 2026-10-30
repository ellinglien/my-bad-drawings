package io.github.mpdairy.monopaint;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * A notebook's settings (the design pass, 2026-10-23, the owner: "replace it with the list"; once the library card, an
 * old library book's card with stamps): a plain list, the one place a notebook's settings are set, in the strip's tile
 * after the front cover ("Settings"). The bar: ‹ Notebooks. Then the notebook's
 * name (TITLE; a tap renames it) with Rename under it; one grey-200 row a setting, its key in caps (LABEL, grey-800) at the
 * left, what it is set to at the right in one line, "›" where it opens a screen ({@link CardSheet}), in three groups
 * ({@link #GROUPS}) with a grey-400 line between them (no More since 2026-10-27, the owner: "instead of a more button,
 * just include everything on that page"). The page: Template (its thumbnail and name; Template Visibility is on the
 * Template screen), Random Template (Off, or what Random draws from; its screen Random From), Page Colour (six swatches
 * in the row), See-Through Pages (Off and On in the row; a long press opens Page Transparency's five), Nib Preview (Off and
 * On in the row: the hover ring's dots, on by default), Self-Portrait Prompt (Off and On in the row, 2026-10-30: each new
 * page dealt a card, {@link DailyPrompt}; off by default). The rules: No
 * Erasing (Off and On), Timed Pages, Lock Pages (Off and On, greyed while nothing is timed, {@link #locksApply}), Tools (the choice and the bar's marks, Brushes on Bar on its screen). The cover:
 * Cover, Cover Picture (Use on Cover for the page drawn on, or No Picture). The foot: "Made Oct 23, 2026 · 2 pages".
 * Where the rows can't fit (the Nomad turned) the list scrolls. Not a page: never counted, exported or moved. Pure, checked on the
 * desktop ({@code LibraryCardChecks}); {@code NotebookScreens} builds it.
 *
 * <p><b>Page Settings</b> (the owner, 2026-10-23: "long press on a page will bring up a panel to adjust the settings for
 * that page"; a hold on a page in either strip or the Pages grid) is the same list for one page ({@link #pageRows}): the
 * page's number for a name with the notebook's under it, then Template (its own, or "Notebook's (Lined)"), Page Colour
 * (Notebook's and the six swatches), Show Through (its step, and Ghost under Page Before; only while See-Through Pages
 * is on and the page has no layers, whose steps are the side column's), No Erasing and Timed Pages (each the notebook's,
 * or Off or On for the page alone, {@link NotebookRules#forPage}; Timed Pages On with its time's slider under it), and
 * Export Page and Delete in plain words at the foot. Paper rows with 1 dp ink lines between them (the owner: e-ink greys render poorly).
 */
final class LibraryCard {
    /** A row, or the name above them. */
    enum Part { NAME, TEMPLATE, RANDOM_FROM, PAGE_COLOUR, TRANSPARENCY, NIB_PREVIEW, SELF_PORTRAIT, TOOLS, NO_ERASING, TIMED, LOCK_PAGES, COVER, COVER_PICTURE, SHOW_THROUGH, LAYERS, MOVE }
    /** The strip's tile word (and the menus' row that opens it); the name's button. */
    static final String SETTINGS = "Settings", RENAME = "Rename";
    /** The rows' keys that aren't a screen's title (docs/TEXT-STYLE.md: Title Case, shown in caps). */
    static final String PAGE_COLOUR = "Page Colour", SEE_THROUGH = "See-Through Pages", COVER = "Cover",
            RANDOM_TEMPLATE = "Random Template", COVER_PICTURE = "Cover Picture", NIB_PREVIEW = "Nib Preview";

    /** The rows a notebook shows, in groups, a grey-400 line between them: the page, the rules, the cover. */
    static final List<List<Part>> GROUPS = java.util.Arrays.asList(
            java.util.Arrays.asList(Part.TEMPLATE, Part.RANDOM_FROM, Part.PAGE_COLOUR, Part.TRANSPARENCY, Part.NIB_PREVIEW, Part.SELF_PORTRAIT),
            java.util.Arrays.asList(Part.NO_ERASING, Part.TIMED, Part.LOCK_PAGES, Part.TOOLS),
            java.util.Arrays.asList(Part.COVER, Part.COVER_PICTURE));
    /**
     * The notebook's own actions at the settings' foot, after a group's line (the owner, 2026-10-28; the drawing screen's
     * menu's rows until then): Export Now ({@link PictureImport#EXPORT_NOW}), then Delete Notebook last. Rename is the
     * name's button at the top.
     */
    static final String DELETE_NOTEBOOK = "Delete Notebook";
    /** The settings list's layout {@code across × tall} dp for the notebook {@code name}: its rows, then the actions' row after one more group's line. */
    static Layout settingsLayout(float across, float tall, String name) { return new Layout(across, tall, rows().size() + ACTION_ROWS, GROUPS.size(), name); }
    /** Every row, in order. */
    static List<Part> rows() {
        List<Part> rows = new ArrayList<>();
        for (List<Part> g : GROUPS) rows.addAll(g);
        return rows;
    }
    /** A row's key. */
    static String key(Part p) {
        switch (p) {
            case TEMPLATE: return NotebookLayout.TEMPLATE;
            case RANDOM_FROM: return RANDOM_TEMPLATE;
            case PAGE_COLOUR: return PAGE_COLOUR;
            case TRANSPARENCY: return SEE_THROUGH;
            case NIB_PREVIEW: return NIB_PREVIEW;
            case SELF_PORTRAIT: return DailyPrompt.TITLE;
            case TOOLS: return NotebookLayout.TOOLS;
            case NO_ERASING: return NotebookRules.NO_ERASING;
            case TIMED: return NotebookRules.TIMED_PAGES;
            case LOCK_PAGES: return NotebookRules.LOCK_PAGES;
            case COVER: return COVER;
            case COVER_PICTURE: return COVER_PICTURE;
            case SHOW_THROUGH: return SHOW_THROUGH;
            case LAYERS: return LAYERS;
            case MOVE: return MOVE;
            default: return SETTINGS;
        }
    }
    /** What a row says it is set to, in words; null for a row of buttons (Page Colour, See-Through Pages, Nib Preview, Self-Portrait Prompt, No Erasing, Lock Pages). */
    static String value(Part p, NotebookOptions o, CoverDesign design) {
        switch (p) {
            case TEMPLATE: return guidesShown(o) && o.guidesShown != NotebookOptions.FULL ? o.template.label + " · " + NotebookOptions.GUIDE_STEPS[o.guidesShown] : o.template.label;
            case RANDOM_FROM: {
                if (!o.template.random()) return NotebookRules.OFF;
                List<NotebookTemplate> set = o.template.set();
                return set.isEmpty() ? NotebookLayout.ALL : set.size() == 1 ? set.get(0).label : set.size() + " templates";
            }
            case TOOLS: return o.tools.label;
            case TIMED: return NotebookRules.timeLabel(o.seconds);
            case COVER: return design.label;
            default: return null;
        }
    }
    /** Whether Lock Pages applies, so its chips can be set (else greyed): a page is timed, by Timed Pages or a timed template's sections. */
    static boolean locksApply(NotebookOptions o) { return o.seconds > 0 || o.template.timed(); }
    /** Whether a row opens a screen ("›"); the rest are set in the row. */
    static boolean opens(Part p) { return p != Part.PAGE_COLOUR && p != Part.TRANSPARENCY && p != Part.NIB_PREVIEW && p != Part.SELF_PORTRAIT && p != Part.NO_ERASING && p != Part.LOCK_PAGES && p != Part.COVER_PICTURE; }
    /** Cover Picture's words: the page on the front cover ("Page 3"), or none. */
    static String coverPicture(int stack) { return stack < 0 ? NotebookCovers.NO_PICTURE_BUTTON : pageTitle(stack); }
    /**
     * The foot: "Made Oct 23, 2026 · 2 pages" (the day from its name; the pages alone when it isn't known), or from then
     * to its last edit, "Oct 23 – 30, 2026 · 2 pages" (the owner, 2026-10-30: "include the range of dates since it was
     * started to when it was last edited").
     */
    static String foot(LocalDate made, LocalDate edited, int pages) {
        String count = pages == 1 ? "1 page" : pages + " pages";
        if (made == null) return count;
        if (edited == null || !edited.isAfter(made)) return "Made " + day(made) + " · " + count;
        boolean year = made.getYear() == edited.getYear(), month = year && made.getMonth() == edited.getMonth();
        return (year ? day(made, "MMM d") : day(made)) + " – " + (month ? day(edited, "d, yyyy") : day(edited)) + " · " + count;
    }
    /** "Oct 21, 2026": a day as the foot says it. */
    static String day(LocalDate day) { return day(day, "MMM d, yyyy"); }
    private static String day(LocalDate day, String pattern) { return java.time.format.DateTimeFormatter.ofPattern(pattern, java.util.Locale.ENGLISH).format(day); }

    /** Page Transparency as words: the paper, and Faint after it when it is. */
    static String[] transparency(TracingPaper t) {
        String paper = TracingPaper.PAPERS[t.paper];
        return t.paper != TracingPaper.PLAIN && t.strength == TracingPaper.FAINT ? new String[]{paper, TracingPaper.STEPS[TracingPaper.FAINT]} : new String[]{paper};
    }
    /** Page Transparency's eye: closed when off, else its strength's ({@link PageTurner#STEP_ICONS}'s). */
    static CardArt.Icon eye(TracingPaper t) {
        return t.paper == TracingPaper.PLAIN ? CardArt.Icon.EYE_CLOSED : t.strength == TracingPaper.FAINT ? CardArt.Icon.EYE_FAINT : CardArt.Icon.EYE_HALF;
    }
    /** Whether Template Visibility shows: there is a template (Blank has no guides to show). */
    static boolean guidesShown(NotebookOptions o) { return !o.template.equals(NotebookTemplate.BLANK); }
    /** See-Through Pages' On: Page Before, at the strength the notebook had; Off: none. A notebook's Nearby Pages stays On. */
    static TracingPaper seeThrough(TracingPaper t, boolean on) {
        return on ? (t.paper == TracingPaper.PLAIN ? t.withPaper(TracingPaper.TRACING) : t) : t.withPaper(TracingPaper.PLAIN);
    }

    // Page Settings

    static final String PAGE_SETTINGS = "Page Settings", SHOW_THROUGH = "Show Through", NOTEBOOKS = "Notebook's", GHOST = "Ghost";
    /**
     * The foot, a row of the list's room: Export Page (2026-10-28: the page's PDF, PNG or Layers, {@link PageExport}),
     * then Delete (the owner, 2026-10-23: "remove some of the other stuff on the bottom.. duplicate move up etc."; the
     * drawing screen's menu has no Delete Page, so it stays here).
     */
    static final String EXPORT_PAGE = "Export Page", DELETE = "Delete";
    static final int ACTION_ROWS = 1;
    /** Delete's height in rows {@code rowHeight} dp tall: a button's 44 dp, less in a shorter row. */
    static float actionHeight(float rowHeight) { return Math.min(44, rowHeight - 2); }
    /** Page {@code place}'s heading. */
    static String pageTitle(int place) { return "Page " + (place + 1); }
    /** A setting a page takes from its notebook, said with what that is: "Notebook's (Lined)". */
    static String notebooks(String value) { return NOTEBOOKS + " (" + value + ")"; }
    /**
     * A page's rows: Show Through only while {@code tracing} shows other pages through and the page is {@code alone} (no
     * layers); then Layers and Move (the refocus, 2026-10-24: the layers left the side column, and a hold on a page opens
     * this at once, so it no longer lifts the page to drag it).
     */
    static List<Part> pageRows(TracingPaper tracing, boolean alone) {
        List<Part> rows = new ArrayList<>();
        rows.add(Part.TEMPLATE); rows.add(Part.PAGE_COLOUR);
        if (tracing.paper != TracingPaper.PLAIN && alone) rows.add(Part.SHOW_THROUGH);
        rows.add(Part.NO_ERASING); rows.add(Part.TIMED);
        rows.add(Part.LAYERS); rows.add(Part.MOVE);
        return rows;
    }
    /**
     * Layers' row (the refocus, 2026-10-24; the side column's group until then): a chip a layer, "1" the bottom, the one
     * drawn on ink-filled (a tap draws on it), then Add (a new layer on top, drawn on); Merge Old, a page drawn with the
     * old in-page layers', when {@code old}.
     */
    static final String LAYERS = "Layers", ADD_LAYER = "Add", MERGE_OLD = "Merge Old", MOVE = "Move", EARLIER = "Earlier", LATER = "Later";
    static String[] layerChips(int layers, boolean full, boolean old) {
        List<String> chips = new ArrayList<>();
        for (int i = 1; i <= layers && layers > 1; i++) chips.add(String.valueOf(i));
        if (!full) chips.add(ADD_LAYER);
        if (old) chips.add(MERGE_OLD);
        return chips.toArray(new String[0]);
    }
    /** What Layers' row says: "Layer 2 of 3" (the one drawn on), "3 layers", or "One". */
    static String layersValue(int layers, int drawnOn) { return layers <= 1 ? "One" : drawnOn >= 0 ? "Layer " + (drawnOn + 1) + " of " + layers : layers + " layers"; }
    /** Move's row: the page one place earlier or later (the page bar's hold-and-drag until 2026-10-24). */
    static final String[] MOVE_CHIPS = {EARLIER, LATER};
    /** Which of a page's own rules a row sets ({@link NotebookRules#ERASING_BITS}, {@link NotebookRules#TIMED_BITS}). */
    static int ruleBits(Part p) { return p == Part.TIMED ? NotebookRules.TIMED_BITS : NotebookRules.ERASING_BITS; }
    /** A rule row's chips, in {@link NotebookRules#own}'s order: the notebook's (with what it is), Off, On (Timed Pages' time on its slider, {@link #timeSlider}). */
    static String[] ruleChoices(Part p, NotebookOptions o) {
        return new String[]{notebooks(p == Part.TIMED ? NotebookRules.timeLabel(o.seconds) : o.noErasing ? NotebookRules.ON : NotebookRules.OFF), NotebookRules.OFF, NotebookRules.ON};
    }
    /** Whether Timed Pages' row has its time's slider under it, a row of its own: the page timed On alone. */
    static boolean timeSlider(int rules) { return NotebookRules.own(rules, NotebookRules.TIMED_BITS) == NotebookRules.OWN_ON; }
    /** Show Through's chips: the steps as words (a step never set shows Solid). */
    static final String[] SHOW_STEPS = {TracingPaper.STEPS[TracingPaper.SOLID], TracingPaper.STEPS[TracingPaper.HALF], TracingPaper.STEPS[TracingPaper.FAINT], TracingPaper.STEPS[TracingPaper.HIDDEN]};
    /** Which of {@link #SHOW_STEPS} a page's step is; and the step chip {@code i} sets (Solid: never set, as a new page). */
    static int stepChip(int step) { return step == TracingPaper.UNSET ? 0 : step - TracingPaper.SOLID; }
    static int chipStep(int i) { return i == 0 ? TracingPaper.UNSET : TracingPaper.SOLID + i; }
    /**
     * What a page's row says it is set to: Template's own ({@code own}) or the notebook's, Page Colour's tone ({@code tone};
     * the notebook's when it is the notebook's), Show Through's step and Ghost, a rule's choice in {@code rules}.
     */
    static String pageValue(Part p, NotebookOptions o, NotebookTemplate template, boolean own, int tone, int rules, int step, boolean ghost) {
        switch (p) {
            case TEMPLATE: return own ? template.label : notebooks(template.label);
            case PAGE_COLOUR: return tone == o.paper ? notebooks(NotebookOptions.paperWord(tone)) : NotebookOptions.paperWord(tone);
            case SHOW_THROUGH: return SHOW_STEPS[stepChip(step)] + (ghost ? ", " + GHOST : "");
            case NO_ERASING: case TIMED: return ruleChoices(p, o)[NotebookRules.own(rules, ruleBits(p))];
            default: return null;
        }
    }
    /** Whether a page's row opens a screen: Template alone; the rest are set in the row. */
    static boolean pageOpens(Part p) { return p == Part.TEMPLATE; }
    // What a tap does

    /** What a tap on a row does: renames, toggles No Erasing, opens a screen. */
    enum Tap { RENAME, TOGGLE, CHOOSE, NOTHING }
    static Tap tap(Part part, NotebookOptions options) {
        if (part == null || part == Part.PAGE_COLOUR || part == Part.COVER_PICTURE || part == Part.LOCK_PAGES || part == Part.NIB_PREVIEW || part == Part.SELF_PORTRAIT) return Tap.NOTHING;
        return part == Part.NAME ? Tap.RENAME : part == Part.NO_ERASING ? Tap.TOGGLE : Tap.CHOOSE;
    }
    /** The screen a row opens (See-Through Pages' by a long press). */
    static CardSheet.Screen screen(Part part) {
        switch (part) {
            case TEMPLATE: return CardSheet.Screen.TEMPLATE;
            case RANDOM_FROM: return CardSheet.Screen.RANDOM_FROM;
            case TRANSPARENCY: return CardSheet.Screen.TRANSPARENCY;
            case TOOLS: return CardSheet.Screen.TOOLS;
            case TIMED: return CardSheet.Screen.TIMED;
            case COVER: return CardSheet.Screen.COVER;
            default: return null;
        }
    }

    // Layout

    /**
     * The list's sizes in the page's room, {@code across × tall} dp, for {@code rows} rows and the name {@code name}: a
     * column at most {@link #MOST_WIDE} wide; the name as large as leaves each row {@link #LEAST_ROW} or more (TITLE,
     * else HEADING, else BODY) in two lines at most; the rows {@link #ROW} tall, less where the room is short (the Nomad
     * turned), {@code breaks} of the gaps between them a group's {@link #BREAK}; it scrolls ({@link #scrolls}) only where
     * rows {@link #LEAST_ROW} tall can't fit.
     */
    static final class Layout {
        static final float MOST_WIDE = 720, ROW = 56, LEAST_ROW = 40, ROW_GAP = 2, BREAK = 12, GAP = 12, RENAME = 44, PAD = 14;
        /** Page Colour's swatches, dp square, apart; the thumbnail's and a mark's widths. */
        static final float SWATCH = 28, SWATCH_GAP = 6, THUMB = 24, MARK = 20, MARK_GAP = 3;
        static final int NAME_LINES = 2;
        final float width, titleSp, rowHeight, height;
        final int titleLines;
        final boolean scrolls;

        Layout(float across, float tall, int rows, int breaks, String name) {
            width = width(across);
            float sp = TypeStyle.BODY.sp, row = LEAST_ROW;
            int lines = NAME_LINES;
            for (TypeStyle t : new TypeStyle[]{TypeStyle.TITLE, TypeStyle.HEADING, TypeStyle.BODY}) {
                sp = t.sp;
                lines = Math.min(NAME_LINES, LabText.wrap(name, width, LabText.face(sp, 0)).size());
                row = (tall - fixed(sp, lines) - gaps(rows, breaks)) / Math.max(1, rows);
                if (row >= LEAST_ROW && LabText.fits(name, width, LabText.face(sp, 0), NAME_LINES)) break;
            }
            titleSp = sp; titleLines = lines;
            rowHeight = Math.max(LEAST_ROW, Math.min(ROW, row));
            height = fixed(sp, lines) + rows * rowHeight + gaps(rows, breaks);
            scrolls = height > tall + .5f;
        }
        /** The gaps between {@code rows} rows, {@code breaks} of them a group's line. */
        private static float gaps(int rows, int breaks) { return (rows - 1 - breaks) * ROW_GAP + breaks * BREAK; }
        /** A key's width in its row: LABEL caps, in the face's own width ({@link LabText#UKULELE}). */
        static float keyWidth(Part p) { return LabText.width(key(p).toUpperCase(java.util.Locale.ROOT), TypeStyle.LABEL.sp, TypeStyle.LABEL.tracking + LabText.UKULELE); }
        /** What a row says it is set to, one line in BODY bold, in the face's own width. */
        static float valueWidth(String value) { return LabText.width(value, TypeStyle.BODY.sp, LabText.UKULELE); }
        /**
         * A value's room in a row of a list {@code width} dp wide: what its key leaves, less Template's thumbnail, the
         * Tools row's marks, the gap before the value and "›" (its gap and 18 dp).
         */
        static float valueRoom(Part p, float width) {
            float room = width - 2 * PAD - keyWidth(p) - 8 - (opens(p) ? 8 + 18 : 0);
            if (p == Part.TEMPLATE) room -= THUMB;
            if (p == Part.TOOLS) room -= 8 + 8 * MARK + 7 * MARK_GAP;
            return room;
        }
        /** The list's width in a room {@code across} dp wide. */
        static float width(float across) { return Math.min(across, MOST_WIDE); }
        /** All but the rows: the name's lines, Rename, the gaps either side of the rows, the foot. */
        private static float fixed(float sp, int lines) { return lines * LabText.line(sp) + RENAME + 2 * GAP + LabText.line(TypeStyle.CAPTION.sp); }
    }

    private LibraryCard() {}
}
