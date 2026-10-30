package io.github.mpdairy.monopaint;

/**
 * The drawing screen's chrome, in dp: one narrow column down the drawing hand's side ({@link InkWashBar}) from the top
 * down to Zen Mode's button, which sits under it in the corner ({@link #zenCell}); the thin page bar along the top of
 * the page, the page's width ({@link PageTurner}, {@link #pageBar}; the owner, 2026-10-23: "moving the pages bar to the
 * top"); the brush line along the screen's foot from just past Zen Mode's button ({@link #BRUSH_LINE}, {@link
 * #lineStart}); the page the rest
 * (docs/archive/superpowers/specs/2026-10-15-tools-page-design.md, "One side toolbar"). The column, top to bottom ({@link
 * #COLUMN}; the owner's mockup, 2026-10-26, "layout v3"): Menu alone, level with the page bar's previews ({@link
 * #menuRow}); the tray (the owner's "B Pen tray", 2026-10-19): the ten slots (eight until 2026-10-27, six until the owner's "two more brush
 * slots", 2026-10-19), each a short row showing its brush as a stroke lying across it, no number; a gap, then the
 * smudge tool and the eraser, each a tray row showing the owner's drawing of it (the spudger, the block eraser),
 * centred; the size and the grey in use in one dot, the size slider beside the grey strip under it ({@link SwatchBar};
 * the owner, 2026-10-24: "change the four size options to a quick size slider... grey swatches can go", then "put the
 * size selected and the current shade below"); the icon block, two to a row: the Dropper and the Lasso, Refresh
 * (under the pickers since layout v4, 2026-10-26); then small icon rows, Show Kinds ({@link EraseKind#SHOW_KINDS}) and
 * the sand timer ({@link SandTimer}), Shapes ({@link #SHAPES}) and Play ({@link #PLAY}; back in the column, the owner,
 * 2026-10-30: "move shapes button to below visibility and timer, and move fun button beside it"), the wet indicator; the countdown's row
 * ({@link #TIMER}) under them while a time runs. The refocus, 2026-10-24: the stamp tool, Magnetic Poetry, Mirrors and
 * the layers left the column, the toys into Play's menu, the layers a page's Page Settings. More room (the owner,
 * 2026-10-28): undo and redo, Shapes and Play left it (two and three fingers undo and redo; Play a row of the menu,
 * Shapes in it), Show Kinds came in from the menu; Refresh went to the page bar, then came back (the owner, 2026-10-28:
 * "move the refresh ghost to a spot on the toolbar, set apart so a finger can press it easily") alone in the Trash's
 * row when the Lasso took the Trash in (its bar over the piece, {@link #LASSO_CUT}).
 * Nothing in it says Edit (the owner, 2026-10-19: "instead of edit buttons, we use long press as a way to
 * customize"): a long press on a tray row opens the tool picker on its slot ({@link #PICKER}). The page bar ({@link #pageBar}): Zoom at the end away from
 * the column (its padlock beside its percentage, {@link PinchPan}; the owner, 2026-10-21: out of the
 * column), the page and its count ("3 / 24", or "Cover" or "Back"), then tiny previews of the pages toward the column
 * ({@link #stripPlaces}); the page is that much shorter, as the column makes it narrower ({@link #pageHeight}). The column's items
 * share its height: each at its preferred height when there is room (the Manta's are the roomier), otherwise all of
 * them shorter together by one fraction, never under their least; an empty stretch at the foot takes what
 * is left (on the Manta, a good deal of white space). What a tap on a tool does is here too. Pure,
 * checked on the desktop.
 */
final class DrawLayout {
    /** A screen whose shorter side is at least this, dp, takes the Manta's sizes; a smaller one (the Nomad) the compact ones. */
    static final int WIDE = 900;
    /** The column's room at its top and foot, and the 2 dp lines between a chooser's cards; no rule beside the page (the owner asked for none). */
    static final int PAD = 4, RULE = 2, BAR_RULE = 0;
    /**
     * The column's width with its ink rule, dp: the Manta's, the Nomad's. The Nomad's 62 (58 until 2026-10-19) holds a
     * pair of 26 dp faces with each one's marker bar at its right ({@link #FACE_MARK}).
     */
    static final int ROOMY_SIDE = 72, COMPACT_SIDE = 62;
    /**
     * The column's one inner margin each side, dp (the Manta's, the Nomad's; the design pass, 2026-10-23: "no common
     * axis"): every row lies inside it, on the column's centre axis, a slot in use's ink fill too, so nothing reaches the
     * page. The tray's rows (the slots, the smudge tool, the eraser) reach over it to the screen's edge, so their drawings
     * touch it (the owner, 2026-10-27: "move tools slightly left, touching edge"; mirrored with the column on the right).
     * Items two to a row ({@link #half}) share that width in two equal cells.
     */
    static final int ROOMY_EDGE = 4, COMPACT_EDGE = 2;
    /**
     * The tray's rows: the slots, {@link ToolSlots#COUNT}, the one number to change for more (the owner, 2026-10-24: "the
     * toolbar may become 10 slots"; ten since 2026-10-27, "we're at 8 now might as well have 10"; the layout fits ten on
     * every screen, {@code DrawLayoutChecks.tenSlots}, the Nomad in landscape with its tray rows at their least).
     */
    static final int TRAY_ROWS = ToolSlots.COUNT;
    /**
     * The Nomad's tray rows: the first eight slots (the owner, 2026-10-28: "eight slots for the nomad it's fine. Just cut
     * off the bottom two"). The other two stay stored and come back on the Manta; the room goes to the rows.
     */
    static final int COMPACT_TRAYS = 8;
    /** The slots the column shows on the Nomad ({@code compact}) or the Manta; the Tools page's Toolbar shows as many. */
    static int trays(boolean compact) { return compact ? COMPACT_TRAYS : TRAY_ROWS; }
    /** Whether {@code tool} is a slot the column doesn't show, {@code shown} slots shown: selected, it gives way to the first. */
    static boolean slotHidden(int tool, int shown) { return tool < ToolSlots.COUNT && tool >= shown; }
    /**
     * The picker box's length in rows of faces ({@link SwatchBar}: the grey strip, {@link GreyStrip}, and the size slider
     * over it): at the least as long as the four sizes' squares were, {@link #FACE_ROWS}; preferred half as long again,
     * {@link #BOX_ROWS} (the owner, 2026-10-29: "stretch the shade selector and size selector a bit to allow for more fine
     * control? maybe 1.5x the height"), an item of its own ({@link Item#BOX}) fitted as the rows of faces, which grows
     * only into the room the column has to spare, before the gaps do, so no other row gives way for it (the owner: "grow
     * only into spare room"). The readout over it, {@link #READOUT_ROWS} rows (the Dropper's room until it went beside
     * the wet indicator, 2026-10-24).
     */
    static final int FACE_ROWS = ToolSwatches.COUNT, BOX_ROWS = FACE_ROWS * 3 / 2, READOUT_ROWS = 2;
    /**
     * The rows past the tray (the owner's mockup, 2026-10-26): the smudge tool's and the eraser's, each a tray row tall
     * ({@link Part#TOOLS}; the owner's drawings, res/drawable/tool_smudge and tool_eraser (traced, sized in the Manta's
     * pixels), trimmed); the icon block's, an icon button tall, two cells to a row, half the
     * column each: the Dropper and the Lasso, Refresh alone, the row its target ({@link Part#BLOCK}); the small icon rows under it: Show
     * Shapes and the sand timer, Show Kinds and Play (2026-10-30), the wet indicator ({@link Part#PLAY}; the owner, 2026-10-28).
     */
    static final int TOOL_ROWS = 2, BLOCK_ROWS = 2, PLAY_ROWS = 3, GRID_COLUMNS = 2;
    /**
     * The column's parts, top to bottom ({@link #COLUMN}, which {@link InkWashBar} adds in its order). MENU Menu alone,
     * no rule under it (the owner, 2026-10-22: "the menu on top should be phosphor list and not have a line
     * underneath"); TRAY the ten slots' rows; TOOLS the smudge tool's and the eraser's rows, their drawings; BLOCK the
     * Dropper and the Lasso, Refresh; FACES the readout ({@link #READOUT_ROWS}) over the grey strip and the size slider
     * ({@link #BOX_ROWS}); PLAY Shapes and the sand timer, Show Kinds and Play, the wet indicator, small rows;
     * TIMER the countdown; STRETCH the empty stretch.
     * The page turner is not in it: it is the page bar over the page ({@link #pageBar}).
     */
    enum Part { TIMER, MENU, GAP, TRAY, TOOLS, BLOCK, FACES, PLAY, STRETCH }
    /**
     * The column, top to bottom, the one list of everything in it. The owner (2026-10-19): the smudge tool and the
     * eraser under the swatches and sizes, then Fill beside the smudge tool, four tools stacked two by two, with Words
     * (the Fun button since 2026-10-19) and the icon buttons under them; no Edit buttons ("instead of edit buttons, we
     * use long press"), no page turner at the foot (the page bar's now); no Brush Lab and no Zoom (2026-10-21: Brush Lab
     * from the Tools page, Zoom on the page bar). The owner (2026-10-22): MonoPaint's four swatches under the sizes and the
     * grey strip, and the wet indicator under them ("i want users familiar with the monopaint interface to feel like they
     * know what everything does in ours"). The owner (2026-10-24): the size slider in the sizes' and swatches' room, the
     * swatches gone, the Dropper under the grey strip. The refocus (2026-10-24): no layers. The owner (2026-10-26): the
     * smudge tool and the eraser straight under the tray, the wet indicator straight under the Dropper. The owner's
     * mockup (2026-10-26, layout v3): Menu alone; the tray; the smudge tool and the eraser as drawings; undo, redo, the
     * Dropper, the Lasso and the Trash in one block; the dot, the slider and the strip; the wet indicator, Finger,
     * Template Visibility and Play; the countdown under them. Layout v4 (the owner, 2026-10-26: "move undo redo eye drop
     * lasso and recycle to below picker"): the dot, the slider and the strip straight under the eraser, the icon block
     * under them, over the wet indicator. The owner (2026-10-27): Refresh on its own row under the wet indicator; Finger back
     * in the main menu, a row, not in the column. The owner (2026-10-28): undo, redo, Shapes, Play and Refresh out, the sand
     * timer beside the wet indicator, Show Kinds beside Template Visibility; later that day the Trash out (the Lasso's bar
     * deletes) and Refresh in its row. The owner (2026-10-29): Refresh at the foot, "just above the zen mode button, with
     * about 1cm space" ({@link #ghostMargin}). The owner (2026-10-30): Shapes and Play back, a small row under Show Kinds and the sand timer; later that day Shapes and Show Kinds swapped ("the fun button looks a bit like the shapes button").
     */
    static final Part[] COLUMN = {Part.MENU, Part.GAP, Part.TRAY, Part.GAP, Part.TOOLS, Part.GAP, Part.FACES, Part.GAP,
            Part.BLOCK, Part.GAP, Part.TIMER, Part.STRETCH, Part.PLAY};
    /** About a centimetre, dp (160 a inch): Refresh's icon to Zen Mode's. */
    static final int ZEN_ROOM = 63;
    /**
     * Refresh's row's margin under it, dp, so its icon sits {@link #ZEN_ROOM} over Zen Mode's (both {@link #ZEN_ICON},
     * each centred in its cell: Refresh's row {@link #icon} tall, Zen Mode's cell {@link #zenHeight}), at most the room
     * the column has to spare.
     */
    int ghostMargin() { return Math.max(0, Math.min(spare, ZEN_ROOM - (icon - ZEN_ICON) / 2 - (zenHeight() - ZEN_ICON) / 2)); }
    /**
     * The gaps between the column's groups (Menu, the tray, the smudge tool and the eraser, the icon block, the size and
     * greys, the small rows): the one spacing
     * between groups, nothing between the rows inside one. Where the column has room to spare they share it, up to
     * {@link #GAP_MOST} times their preferred height each, so the groups breathe evenly; the rest stays at the foot.
     */
    static final int GAPS = 5, GAP_MOST = 4;
    /**
     * The countdown's row under the small icon rows, dp (the owner, 2026-10-21; at the column's top until Menu went level
     * with the page bar, 2026-10-23, then under undo and redo until layout v3, 2026-10-26): the page's time left, m:ss in labels, shown only
     * with Timed Pages on or on a page of timed sections, two lines tall for a section's name over its time (24 dp until Timed
     * Sections), or the sand timer's countdown (2026-10-27); the sand timer's icon beside the wet indicator (2026-10-28; in
     * this row until then). Its room is kept on every screen, so the column's sums are as they were.
     */
    static final int TIMER = 40;
    /** The widest countdown, in labels. */
    static final String WIDEST_COUNTDOWN = "60:00";
    /** The column's items, in the order of the height tables below. */
    enum Item { ICON, GAP, SLOT, FACE, WET, BOX }
    /**
     * Preferred heights on the Manta and the Nomad, and the least anywhere: an icon block row (undo and redo's, the
     * Dropper's and the Lasso's, Refresh's; Menu's is {@link #menuRow}; 56 dp on the Manta and 40 the least until
     * layout v3, 2026-10-26, whose mockup's rows are about 40), the gaps between groups, a tray row (a slot's, and the
     * smudge tool's and the eraser's: its stroke or drawing, {@link #TRAY_TOP} over and under it; 4 dp more than with the
     * underline, the owner, 2026-10-23: "a tad more vertical spacing between the brushes"), a row of faces (its squares
     * and the {@link #FACE_GAP} to the next; the readout's), a small icon row (the wet indicator, beside Refresh where the column is short, Template Visibility and
     * Play, and Refresh alone), the picker box ({@link #FACE_ROWS} rows of faces; up to {@link #BOX_ROWS} from the room to spare). A face 26 dp square (23 on the Nomad, whose column holds no more with each one's side bar).
     * A tray row's least is 24 dp (30 until the ten slots, 2026-10-27): ten fit the Nomad in landscape only with the tray's
     * rows a little shorter, everything else as it was there. The Manta's icon rows 52 dp and small rows 44 since three rows
     * left the column (2026-10-28; 44 and 36 until then): they breathe, the rest still white space at the foot.
     */
    static final int[] ROOMY = {52, 10, 34, 30, 44, FACE_ROWS * 30}, COMPACT = {40, 8, 32, 27, 34, FACE_ROWS * 27}, LEAST = {30, 0, 24, 27, 28, FACE_ROWS * 27};
    /**
     * Template Visibility (the owner, 2026-10-23: "if there is a template, a way to adjust its visibility quickly on the
     * toolbar"), beside Play under the wet indicator (layout v3, 2026-10-26; beside Menu that morning): the page's guides' step ({@link NotebookOptions#guidesShown}),
     * its eye the step's ({@link PageTurner#STEP_ICONS}), shown only while the page drawn on has guides. A tap steps Full,
     * Half, Faint, Hidden, then Full again ({@link NotebookOptions#withNextGuidesStep}). Since 2026-10-28 the Template
     * eye in Show Kinds' popup, first while the page has guides (the owner: "that makes sense to merge them").
     */
    static final String TEMPLATE = "Template Visibility";
    /** What Template Visibility says to a screen reader at {@code step}: "Template Visibility: Faint". */
    static String templateSaid(int step) { return TEMPLATE + ": " + NotebookOptions.GUIDE_STEPS[step]; }
    /**
     * The grey a dirty brush's held paint shows in on the bar (its slot's dot, the smudge tool's brush head), a tone
     * from its paint's {@code darkness} (0 to 1): the nearest of the six greys ({@link NotebookOptions#PAPERS}), never
     * paper, which would read as clean.
     */
    static int heldGrey(float darkness) {
        int tone = Math.round(255 * (1 - darkness)), best = NotebookOptions.PAPERS[1];
        for (int i = 2; i < NotebookOptions.PAPERS.length; i++)
            if (Math.abs(NotebookOptions.PAPERS[i] - tone) < Math.abs(best - tone)) best = NotebookOptions.PAPERS[i];
        return best;
    }
    /** The long press's choices on a slot whose brush holds paint: Clean Brush, and the tool picker ({@link #PICKER}) as any slot's long press opens it. */
    static final String CLEAN = "Clean Brush", TOOLS = "Tools";
    /**
     * The tool picker (the owner, 2026-10-25: "change the library to a smaller set of tools (the ones with drawings
     * mostly) and add an advanced button which pulls up the library we have now. like, have it be a tiny modal like i
     * suggested for the confetti menu"; "long press on any slot to open. single tap selects the tool"), opened beside a
     * slot by its long press ({@code PaintActivity.toolPicker}): "just whatever i drew", each Basic tool's drawing over its
     * name. Grouped by kind, no headers (the owner, 2026-10-27: "sort by category instead of alphabetical (but dont show
     * category headers or anything) just to group similar tools"): pens and nibs, markers, brushes, water and washes, dry
     * media, oils, then the odd ones. The owner's pens and Cover hake joined it, Sumi and Comb left it (2026-10-28).
     * Each row: the Basic tool's name ({@link ToolPresets}), then its drawing in res/drawable (traced vectors; Springy brush
     * a PNG in res/drawable-nodpi for its grey), or null for one not drawn yet, shown by its stroke; change either here.
     */
    static final String[][] PICKER = {
            {"Steady line", "tip_steady_line"}, {"Ballpoint", "tip_ballpoint"}, {"Cheap ballpoint", null}, {"Gel pen", null},
            {"Fountain pen", "tip_fountain_pen"}, {"Smooth fountain", null},
            {"Split nib", "tip_split_nib"}, {"Fude pen", "tip_broad_nib"}, {"Stipple pen", "tip_stipple_pen"},
            {"Engraving pen", "tip_engraving_pen"},
            {"Felt marker", "tip_felt_marker"}, {"Chisel marker", "tip_chisel_marker"}, {"Permanent marker", null},
            {"Acrylic paint marker", null}, {"Ms. Sketch", null}, {"Highlighter", "tip_highlighter"},
            {"Springy brush", "tip_springy_brush"}, {"Swell brush", "tip_swell_brush"}, {"Ink wash brush", null},
            {"Flat hake", "tip_flat_hake"}, {"Cover hake", null},
            // The owner, 2026-10-26: "wet blooms, pooling wash bring to the quicker menu"; the wash brush drawn until they have their own.
            {"Water brush", "tip_water_brush"}, {"Soft wash", "tip_soft_wash"}, {"Wet blooms", "tip_soft_wash"},
            {"Pooling wash", "tip_soft_wash"}, {"Masking fluid", "tip_masking_fluid"}, {"Wax resist", "tip_wax_resist"},
            {"HB pencil", "tip_hb_pencil"}, {"Soft charcoal", "tip_soft_charcoal"}, {"Soft pastel", "tip_soft_pastel"},
            {"Wax crayon", "tip_wax_crayon"},
            {"Loaded oils", "tip_loaded_oils"},
            {"Spirograph", "tip_spirograph"}, {"Stamp", "tip_stamp"}, {"Fill in", "tip_fill_in"}, {"Filled shape", "tip_filled_shape"},
            {"Dot tone", "tip_dot_tone"}, {"Ben-Day brush", "tip_ben_day"}, {"Lemon juice", "tip_lemon_juice"},
            {"Vanishing ink", "tip_vanishing_ink"}};
    /** The owner's drawings of Basic tools off the picker (2026-10-28), still shown on their slots and the Tools page: as {@link #PICKER}'s rows. */
    static final String[][] DRAWN = {{"Sumi", "tip_sumi"}, {"Comb", "tip_comb"}};
    /**
     * The picker's drawing of {@code brush} ({@link #PICKER}, its file's name), or null, its tray row keeping its stroke. The
     * owner, 2026-10-26: "you can add the brush drawings now". The drawing of the Basic tool it came from ({@link
     * ToolPresets#origin}: "Wax crayon 3" Wax crayon's); else, for a tool not drawn, its style's one drawing (Sketch pencil
     * HB pencil's); none for a brush of its own (Brush Lab's), nor a tool whose style has none or
     * several (Mold ink; Print's Halftone brush since Ben-Day brush and Engraving pen were drawn, 2026-10-26).
     */
    static String drawing(Brush brush) {
        ToolPresets.Preset preset = brush == null ? null : ToolPresets.origin(brush);
        if (preset == null) return null;
        String kin = null;
        int drawn = 0;
        for (String[][] tools : new String[][][]{PICKER, DRAWN}) for (String[] tool : tools) {
            ToolPresets.Preset p = ToolKits.preset(tool[0]);
            if (p == preset) return tool[1];
            if (p != null && tool[1] != null && p.style == preset.style) { kin = tool[1]; drawn++; }
        }
        return drawn == 1 ? kin : null;
    }
    /** The share of a drawing's width, at its handle's end, a tray row's edge cuts off: the tip comes out of the edge (the owner's mockup, 2026-10-26). */
    static final float TIP_TRIM = .1f;
    /**
     * Where a tray row's drawings end, of the row's width: each is traced to its ink, so trimmed and fitted to the row's
     * width (most of them on the Manta and the Nomad) its tip ends .986 across, measured (WildSampleChecks.tray). A slot's stroke ends there too
     * ({@link WildSample#tray}; the owner, 2026-10-27: "continue to where the brush images end").
     */
    static final float TIP_END = .986f;
    /**
     * Shapes (the owner, 2026-10-26: "yes shapes, lets bring them back"): MonoPaint's Shapes tool, a small row's left
     * cell beside Play (2026-10-30; a row of Play's menu from 2026-10-28, a button beside the Trash until then), its side
     * bar while on, in place of the slot's; its tap a menu of the five shapes ({@link ToolSettings.Shape}) and Filled, marked while on; a pick takes Shapes up on
     * the slot in use (the first with no slot in use), whose
     * grey it lays and whose size is its outline's width, until a tool is chosen. Pen down, drag, lift: Line end to end, Rectangle,
     * Square and Oval corner to corner, Circle out from its centre.
     */
    static final String SHAPES = "Shapes", FILLED = "Filled", REFRESH = "Refresh";
    /** What a pick of {@code shapes} says beside the slot: "Rectangle", "Filled oval" (a line is never filled). */
    static String shapeWords(ToolSettings shapes) {
        boolean filled = shapes.filled && shapes.shape != ToolSettings.Shape.LINE;
        return filled ? FILLED + " " + shapes.shape.label.toLowerCase(java.util.Locale.ROOT) : shapes.shape.label;
    }
    /** {@code shapes} with its outline a slot's size, {@code px}, in the whole pixels it takes (1 to 128). */
    static ToolSettings shapeOutline(ToolSettings shapes, double px) { return shapes.outlineWidth((int) Math.round(StrokeMath.clamp(px, 1, 128))); }
    /**
     * The picker's last row: Brush Lab on the slot's brush ("brush studio could be opened directly from the tiny popup"), and
     * the Tools page on the slot, said Library (the owner, 2026-10-28: "instead of advanced say library, which is more like
     * what it actually is"; Advanced until then).
     */
    static final String BRUSH_LAB = "Brush Lab", ADVANCED = "Library";
    /**
     * The picker's foot row, a switch, and the Tools page's beside its Toolbar label (the owner, 2026-10-28: "an advanced
     * 'switch' on the pop-up menu ... so that the advanced menu comes up immediately when you long press a tool"; "a way
     * to disable that on the tool page"): while on, a slot's long press opens the Tools page, not the picker
     * ({@link PaintPreferences#toolsFirst}).
     */
    static final String TOOLS_FIRST = "Tools First";
    /**
     * The picker's top row (the owner, 2026-10-27: "if brush has been edited in brush lab from one of the base saved
     * brushes, add a 'reset to default'"; 2026-10-28: "put reset to default at the top of the menu"): shown only for a
     * brush whose Basic tool ({@link #edited}) it no longer is.
     */
    static final String RESET = "Reset to Default";
    /**
     * The picker's row over its last for a slot holding a Stamp (the owner, 2026-10-28: "just create a tool that is a
     * stamp"): the stamp menu beside the slot ({@code InkWashBar.stampMenu}: its card, Cut Stamp, Turns, Spacing, Ink Fade).
     */
    static final String STAMP_MENU = "Stamp ▾";
    /**
     * The Basic tool {@code brush} came from ({@link ToolPresets#origin}) when its settings no longer are that tool's; else
     * null. One of {@link ToolSlots#FIRST}'s brushes (by its name's slug: any case, a copy's number) came from FIRST's own
     * version (the owner, 2026-10-28: Verve felt and others reset to another Basic tool sharing their seed).
     */
    static ToolPresets.Preset edited(Brush brush) {
        ToolPresets.Preset preset = brush == null ? null : ToolPresets.origin(brush);
        if (brush != null) for (String[] row : ToolSlots.FIRST)
            if (row[2].startsWith(BrushCode.slug(brush.name()) + ".M")) preset = new ToolPresets.Preset(LegacyBrushes.named(row).style, row[1], row[2], "");
        return preset != null && !preset.brush().parts.key().equals(brush.parts.key()) ? preset : null;
    }
    /** A picker cell's drawing's height, the room round it and its name, and its side bar's room (a menu row's: Theme.HEAVY, Theme.BASE in), dp. */
    static final int PICKER_TIP = 32, PICKER_PAD = 6, PICKER_MARK = 6;
    /**
     * Room a picker name is counted with, em a character: Ukulele (the face since 2026-10-26, scripts/ukulele_font.py) runs
     * wider than LabText's duospace count, Ben-Day brush 8.44 em to its 8.1.
     */
    static final float PICKER_SPACING = .04f;
    /** The longest name a picker cell holds whole, as before the owner's pens joined (2026-10-28): four columns still fit beside the Nomad's column. */
    static final String PICKER_NAME_MOST = "Masking fluid";
    /**
     * A picker cell's width, dp: its longest name in captions on one line, but no wider than {@link #PICKER_NAME_MOST}'s
     * (a longer one, the owner's pens', cut with …, as a Tools page card cuts it), the room round it and the side bar's.
     */
    static int pickerCellWidth() {
        float longest = 0;
        for (String[] tool : PICKER) longest = Math.max(longest, LabText.width(tool[0], TypeStyle.CAPTION.sp, PICKER_SPACING));
        longest = Math.min(longest, LabText.width(PICKER_NAME_MOST, TypeStyle.CAPTION.sp, PICKER_SPACING));
        return (int) Math.ceil(longest) + 2 * PICKER_PAD + PICKER_MARK;
    }
    /** A picker cell's height, dp: the drawing over its name's line, the room round them. */
    static int pickerCellHeight() { return PICKER_TIP + (int) Math.ceil(LabText.line(TypeStyle.CAPTION.sp)) + 3 * PICKER_PAD; }
    /** The picker's height in {@code columns} columns, dp: its cells' rows, Reset to Default's room, the Stamp row's, Brush Lab's row, Tools First's and its fine edge. */
    static int pickerHeight(int columns) { return (PICKER.length + columns - 1) / columns * pickerCellHeight() + 4 * MENU_ROW + 2; }
    /** The picker's columns on a screen {@code height} dp tall: three, more where three would run off its foot (the Nomad turned). */
    static int pickerColumns(int height) {
        int columns = 3;
        while (columns < PICKER.length && pickerHeight(columns) > height - 2 * PAD) columns++;
        return columns;
    }
    /**
     * Play (the refocus, 2026-10-24; Phosphor confetti; the column's button again since 2026-10-30, beside Shapes, a row
     * of the menu from 2026-10-28): the toys' menu beside it ({@code PaintActivity.playMenu}): Magnetic Poetry (the fridge
     * magnets' bin, {@link WordsMode}), Mirrors (the page's mirrors' list, {@link MirrorsMenu}), then Replay Page. No
     * Shapes (its own button since 2026-10-30), Self-Portrait Prompt (a notebook setting since 2026-10-30, {@link
     * NotebookOptions#selfPortrait}), Stamp (a Basic tool since 2026-10-28, {@link #STAMP_MENU}), Surprise Me (Notebook
     * Settings' Tools) or Fling (the owner, 2026-10-28: "remove fling and surprise me"). Its side bar while a toy is on ({@link #playOn}).
     */
    static final String PLAY = "Play", POETRY = "Magnetic Poetry", MIRRORS = "Mirrors", LASSO = "Lasso";
    /**
     * The shade strip's long press (the owner, 2026-10-27: "add the long press for transparency to the shade picker"):
     * Opacity, the Opaque medium's own dial, thin at the strip's top to opaque at its foot; on any other brush a note, as
     * there it would only repeat the grey.
     */
    static final String OPACITY = PartDial.COVER.label, OPACITY_ONLY = "Opaque paint only";
    /** Whether {@code brush} (null: the eraser) has the shade strip's Opacity: its Medium is Opaque. */
    static boolean opacity(Brush brush) { return brush != null && brush.parts.choice(BrushPart.MEDIUM) == PartChoice.COVER; }
    /**
     * {@code brush} with its Opacity dial at {@code value} (0 thin to 1 opaque), as Fling's {@link #flung}. Kept as the
     * slot's brush's own dial, not a per-tool key beside size and grey: each slot holds its own brush, so the memory is
     * per tool already, a brush starts at its saved Opacity, and every stroke, Brush Lab and Undo read it as they are.
     */
    static Brush opacity(Brush brush, double value) {
        ModularBrush b = brush.parts.copy();
        b.part(BrushPart.MEDIUM).set(PartDial.COVER, StrokeMath.clamp(value));
        return Brush.of(b.normalize());
    }
    /** The Opacity strip's screen-reader text at {@code value}, as the grey strip's: "Opacity, thin to opaque: 40%". */
    static String opacitySaid(double value) {
        return OPACITY + ", " + PartChoice.COVER.words[0] + " to " + PartChoice.COVER.words[1] + ": " + Math.round(100 * StrokeMath.clamp(value)) + "%";
    }
    /** Whether Play has its side bar: a toy is on (the magnets' bin open, mirrors on the page). */
    static boolean playOn(boolean words, boolean mirrors) { return words || mirrors; }
    /**
     * The Dropper (the owner, 2026-10-24: "add a dropper tool so people can quickly match color"; Phosphor eyedropper),
     * in the icon block's left cell, the Lasso beside it (the owner, 2026-10-24: "lasso can move beside picker";
     * beside the wet indicator before, under the strip before that): a tap takes it up, its side bar on the column's edge side while it is, the slot's given up
     * meanwhile, as the Lasso's. The pen on the page then picks the grey the page shows under its tip (every layer, no
     * guides, {@link ToneDocument#shownTone}), a ring of it round the tip while it hovers or presses, a hole in the ring's
     * middle so the page there shows (the owner, 2026-10-24: "dropper is confusing.. the selector is offset"; it sat
     * {@code 40} dp over the pen until then); the lift
     * sets it as the tool in use's grey ({@link ToolSwatches#drop}) and puts the Dropper down, the tool drawing again.
     * A tap on it again, or another tool, puts it down with nothing picked. Shown with the grey strip.
     */
    static final String DROPPER = "Dropper";
    /** The ring of the grey the Dropper picks, round the pen's tip: its outer radius and its hole's, dp. */
    static final int DROPPER_RING = 14, DROPPER_HOLE = 5;
    /**
     * The size slider ({@link SizeSlider}; the owner, 2026-10-24: "a slider with a preview would be ideal", then "the
     * redraw is blinky and slow", then "size picker is still blinking. make it an outline shape instead?" with a sketch,
     * "DO IT THIS WAY"): the right cell beside the strip (the owner, 2026-10-27: "move the size selector to the right
     * side; shade to the left"; the left cell until then), {@link #BOX_ROWS} rows of faces long, its track an ink outline
     * of a triangle pointing down, the square's width ({@link #faceSide}) at the top (broad) to a point at the foot (fine;
     * the owner, 2026-10-24: "flip the triangle for the size so it goes down"), end to end with the strip; the size in use
     * the strip's ink tab ({@link #FACE_MARK} x {@link #STRIP_MARK}) right of it, on its cell's outer side. The triangle
     * never draws again: a drag moves the tab alone, its strip of the cell {@link #sliderMarkRoom} wide put on the panel
     * through its binary update, all ink and paper (a grey-600 right triangle, its tab's strip a grey update a move, until
     * then). The whole cell takes the pen.
     */
    static String sizeSaid(double px) { return "Size: " + ToolSwatches.mm(px); }
    /** The size slider's track, dp from its cell's top: the tab's middle at the broadest, and at the finest, in a cell {@code tall} dp; the strip's ends. */
    static float trackTop() { return PAIR_PAD; }
    static float trackFoot(float tall) { return tall - FACE_GAP / 2f; }
    /** The slider's position, 0 at the finest (the foot) to 1 at the broadest (the top), at {@code y} dp in a cell {@code tall} dp; past its ends, the end's. */
    static double sliderAt(float y, float tall) { return StrokeMath.clamp((trackFoot(tall) - y) / (trackFoot(tall) - trackTop())); }
    /** The slider's cell's room for its tab, dp: the tab and its gap right of the triangle, and the gap to the axis left of it. */
    static int sliderMarkRoom() { return faceMarkRoom() + FACE_GAP / 2; }
    /** Where the tab's middle is, dp, at position {@code t}. */
    static float sliderY(double t, float tall) { return trackFoot(tall) - (float) StrokeMath.clamp(t) * (trackFoot(tall) - trackTop()); }
    /** The standing grey strip's darkness (0–1) at {@code y}, its top at {@code top}, {@code length} long: white at the top, black at the foot, past its ends the end's ({@link GreyStrip}). */
    static double shadeAt(float y, float top, int length, int endpoint) { return ToolSwatches.darkness(ShadeStrip.toneAt(length - (y - top), length, endpoint)); }
    /**
     * The picker box (the owner's drawing, 2026-10-27: "i'd like to try using it to replace the shade picker and size
     * select"; {@link SwatchBar}): whether a pen landing at ({@code x}, {@code y}) drives the shade, the lower-left
     * triangle of the box from ({@code left}, {@code top}) to ({@code right}, {@code foot}), cut by its diagonal from the
     * top left to the foot right; else the size, the upper-right one. On the diagonal's gap the nearer side's; outside
     * the box, the side it is on (each tab's room too). Unmirrored: the caller turns x for a column on the right.
     */
    static boolean boxShade(float x, float y, float left, float top, float right, float foot) {
        return (y - top) * (right - left) >= (x - left) * (foot - top);
    }
    /**
     * Zen Mode (the owner, 2026-10-22: "a zen mode without toolbars would be nice"): the column, the brush line and the
     * page bar go, the page has the whole screen, the tool in use still draws. Its button is always there and never
     * moves (the owner, 2026-10-22: "the zen mode button.. it should be in the same spot it is when it's active"): in
     * the screen's foot corner under the column, centred on the column's icon axis, its icon as far from the screen's
     * foot as from its side edge, (side - {@link #ZEN_ICON}) / 2 (the owner, 2026-10-23: "keep the zen in the bottom
     * left (align a bit better pls)"; "the left side and right side of the screen have equal space"), one button over the page in both states ({@link #zenCell}), so the way out is where the way in
     * was. Its icon Phosphor circle while off, circle fill while on (the owner, 2026-10-24: "change dotted circle for zen:
     * circle unfilled and filled for zen mode"; circle-dashed while off from 2026-10-23), Menu's size ({@link #ZEN_ICON}); its cell as wide as Menu's ({@link
     * #half}) and {@link #zenHeight} (the column's {@code side}) tall. A tap turns it on or off; the menu has no row for it. A session's: a new launch
     * has the bars.
     */
    static final String ZEN = "Zen Mode", ZEN_LEAVE = "Leave Zen Mode";
    /**
     * The menu's row for the drawing hand ({@code PaintPreferences.toolboxRight}, MonoPaint's setting, its Settings window
     * unreachable here), naming the other hand (the owner, 2026-10-27: "it should be relative"): Left-Handed puts the column
     * on the page's right, and with it the page bar, the brush line, Zen Mode's corner and the menus beside the column, all
     * mirrored; then the row says Right-Handed, its hand mirrored. Applied at once.
     */
    static final String LEFT_HANDED = "Left-Handed", RIGHT_HANDED = "Right-Handed";
    /** The menu's row for the notebook's settings (the owner, 2026-10-27: "change settings in dropdown to 'notebook settings'"). */
    static final String NOTEBOOK_SETTINGS = "Notebook Settings";
    /**
     * Every icon in the column and its corners, dp: one square (the owner, 2026-10-30: "the icon buttons seem a bit out of
     * whack with each other, make sure to size them and position them carefully so they look like they all belong
     * there"): Menu, the Dropper and the Lasso, Shapes and the sand timer, Show Kinds and Play, the wet indicator,
     * Refresh and Zen Mode. A half-row's cell narrower than it (the Nomad's) takes its icon smaller. 28 for Menu, Zen
     * Mode, Refresh and the drop, and 20 to 24 for the half-row cells, until then.
     */
    static final int ICON_SIDE = 24;
    /**
     * The icons whose drawings fill their whole box top to bottom (the hourglass, the drop, Zen Mode's circle), dp: a
     * step under {@link #ICON_SIDE}, so they read the same size as the rest (about 36 px tall on the Manta, as the others).
     */
    static final int ICON_FULL = 22;
    /** Zen Mode's icon, dp ({@link #ICON_FULL}). */
    static final int ZEN_ICON = ICON_FULL;
    /**
     * Zen Mode's button's cell on a {@code width × height} screen, the column {@code side} wide on its right or not, the
     * cell {@code cellWidth × cellHeight} (all px, or all dp): its left and top, in the foot corner under the column, in
     * the middle of the column's width. The same cell with the bars and in Zen Mode.
     */
    static int[] zenCell(int width, int height, int side, int cellWidth, int cellHeight, boolean right) {
        int in = (side - cellWidth) / 2;  // from the column's outer edge, mirrored for a column on the right
        return new int[]{right ? width - in - cellWidth : in, height - cellHeight};
    }
    /** Zen Mode's cell's height, dp: the column's width, so its icon's gap to the foot is its gap to the side edge. */
    int zenHeight() { return side; }
    /**
     * The room the foot row takes under the canvas, dp: from the brush line's top down to the screen's foot, so the line
     * is centred on Zen Mode's icon (the cell's middle) and the canvas ends above the line's words.
     */
    int footHeight() { return side - side / 2 + BRUSH_LINE / 2; }
    /**
     * The room between Zen Mode's cell and the brush line, dp (the owner, 2026-10-23: "move the active tool info over a
     * bit, so it's separate from the zen circle").
     */
    static final int LINE_GAP = 14;
    /** Where the brush line starts, dp from the column's outer edge: {@link #LINE_GAP} past Zen Mode's cell. */
    int lineStart() { return (side - half()) / 2 + half() + LINE_GAP; }
    /**
     * The Mirrors list's rows, top to bottom: on or off; their dashed guides shown or not; the two styles; place one with
     * the next stroke; three through the centre; Real Mirror's other side kept, and what it shows laid into the page as
     * the mirrors go (both shown only while there is one); the mirrors removed, the drawing kept as it is (with Real
     * Mirror, the page as it really is again). Under them a caption says how many ({@link Mirrors#caption}). The ones on
     * are marked by the menu's bar at their right.
     */
    enum MirrorRow {
        MIRRORS("Mirrors"), GUIDES("Show Guides"), COPY("Copy Strokes"), REAL("Real Mirror"), PLACE("Place Mirror"), KALEIDOSCOPE("Kaleidoscope"), FLIP("Flip Side"),
        KEEP("Keep Reflection"), CLEAR("Remove Mirrors");
        final String label;
        MirrorRow(String label) { this.label = label; }
    }
    /** A list's row on the drawing screen (the menu's, {@code PaintActivity.menu}), dp, and its caption under the rows. */
    static final int MENU_ROW = 52, MENU_NOTE = 40;
    /**
     * How many of each the column holds but Menu ({@link #menuRow}): the icon block's rows; the gaps between groups; the
     * tray's rows, the smudge tool's and the eraser's; the readout's rows; the small icon rows; the picker box. No
     * rules: groups are set apart by space (Scribbler Style; the design pass, 2026-10-23).
     */
    private static final int[] COUNT = {BLOCK_ROWS, GAPS, TRAY_ROWS + TOOL_ROWS, READOUT_ROWS, PLAY_ROWS, 1};
    /**
     * The page bar's Zoom button ({@code ZoomButton}): its padlock's side, the gap between it and the percentage beside
     * it, and its room either side, dp; its width holds the widest percentage, 800%, in captions ({@link #zoomButton}).
     * Its room about the percentage's gap to the screen's top, so the corner reads even (the owner, 2026-10-30: the zoom %
     * "has a similar issue" to Menu's; 4 dp until then, half its gap to the top).
     */
    static final int ZOOM_LOCK = 16, ZOOM_GAP = 3, ZOOM_PAD = 10;
    static int zoomButton() { return ZOOM_LOCK + ZOOM_GAP + (int) Math.ceil(LabText.width(PinchPan.caption(800), TypeStyle.CAPTION.sp, 0)) + 2 * ZOOM_PAD; }
    /**
     * A tray row (and a tool button under the faces): its room over and under its stroke or icon, dp, so the strokes
     * stand {@code 2 * TRAY_TOP} apart.
     */
    static final int TRAY_TOP = 4;
    /**
     * dp: the side column's one "in use" mark (the owner, 2026-10-23: "it should be a side line, not an underline. too
     * hard to differentiate a solid line from a stroke"): an ink bar {@link #SIDE_BAR} wide down the side of the slot or
     * tool in use or on ({@link SlotTile}), {@link #BAR_GAP} clear of its stroke or icon. A full row's (a slot's) is on
     * its page side, the screen's edge side being its drawing's (its right, its left with the column on the right), the
     * row's height less {@link #BAR_INSET} at its top and foot (the owner, 2026-10-27: "tool selection indicator bar,
     * make it slightly shorter"; the full height until then); a cell of two's is on the column's edge side of the cell,
     * so it never lies between two icons.
     */
    static final int SIDE_BAR = 5, BAR_GAP = 2, BAR_INSET = 3;
    /** A row's room for its side bar, dp. */
    static int barRoom() { return SIDE_BAR + BAR_GAP; }
    /**
     * dp: the underline under a slot in use on the Tools page and in Brush Lab ({@code LoopCard}; the drawing screen's
     * column has the side bar, {@link #SIDE_BAR}): {@link #MARK_GAP} under its picture, {@link #UNDERLINE} thick (a tab's,
     * {@code SwatchFace.TAB}), {@link #MARK_CLEAR} clear of its card's foot; {@link #MARK} in all.
     */
    static final int MARK_GAP = 1, UNDERLINE = 5, MARK_CLEAR = 3, MARK = MARK_GAP + UNDERLINE + MARK_CLEAR;
    /**
     * A row of faces (the slider and the grey strip, {@link SwatchBar}; a size beside the strip, or two swatches, until 2026-10-24): each square in its
     * cell ({@link #half}) {@link #FACE_GAP} from the other's, the pair centred on the column's axis, {@link #PAIR_PAD}
     * over it, so the gaps across and down are the same (the column pass, 2026-10-23: "an exact 2×2 grid"); the one in
     * use marked by a {@link #FACE_MARK} dp ink bar down its side, {@link #FACE_MARK_GAP} clear, its square's height, on
     * the column's edge side of its cell (a size's at its left, the owner, 2026-10-23: "instead of underline indicator for
     * size, use left side line like before"; a right cell's swatch's at its right); the squares' most. The grey strip
     * spans the four sizes' squares, top to foot, exactly a square wide, so the swatches under it line up with it (the
     * owner, 2026-10-23: "make the shade boxes the same width and aligned with the grey picker"), a {@link #FACE_MARK} ×
     * {@link #STRIP_MARK} ink tab right of it at the grey in use, in the room the swatches' bars have.
     */
    static final int FACE_GAP = 4, PAIR_PAD = FACE_GAP / 2, FACE_MARK = 3, FACE_MARK_GAP = 1, FACE_SQUARE = 28, STRIP_MARK = 10;
    /** The room round a face elsewhere (Brush Lab's, the Tools page's tiles), dp. */
    static final int FACE_PAD = 3;

    /**
     * What a tap on a tool does: select it; open the smudge brushes' chooser (the selected smudge tool again); nothing
     * (the selected slot or eraser again). A tray row only ever selects (the owner, 2026-10-19: "selecting a brush opens
     * the options panel each time, that seems excessive"): its brush is changed by a long press on the row, which opens
     * the tool picker on it ({@link #PICKER}; "we use long press as a way to customize").
     */
    enum Tap { SELECT, SMUDGES, NOTHING }
    /** A tap on {@code tool} (a slot, the eraser at {@link ToolSlots#ERASER} or the smudge tool at {@link ToolSlots#SMUDGE}) while {@code selected} is selected. */
    static Tap tap(int selected, int tool) {
        if (tool != selected) return Tap.SELECT;
        return tool == ToolSlots.SMUDGE ? Tap.SMUDGES : Tap.NOTHING;
    }
    /**
     * What a tap on the Lasso does: take it up (not in use), its menu, Paste alone (in use, nothing floating, one kept to
     * paste), nothing (in use; a piece floating has its bar, Cut, Duplicate and Delete, since 2026-10-28).
     */
    enum LassoTap { TAKE_UP, MENU, NOTHING }
    static LassoTap lassoTap(boolean on, boolean floating, boolean kept) { return !on ? LassoTap.TAKE_UP : !floating && kept ? LassoTap.MENU : LassoTap.NOTHING; }
    /**
     * The Lasso's words (docs/TEXT-STYLE.md): its bar's over a floating piece (the owner, 2026-10-28: "in supernote there is
     * just one lasso tool... when the bounding box appears, above it is a toolbar offering choices for what to do with
     * it. we could merge the lasso trash and the lasso"; {@code InkWashBar.lassoBar}), its menu's Paste, its notes.
     */
    static final String LASSO_CUT = "Cut", LASSO_DUPLICATE = "Duplicate", LASSO_DELETE = "Delete", LASSO_PASTE = "Paste",
            LASSO_NOTHING = "Nothing to move", LASSO_NOTHING_DELETED = "Nothing to delete", LASSO_OTHER_SIZE = "Page size differs";
    /** The Lasso's bar's words over a floating piece, in its order. */
    static final String[] LASSO_BAR = {LASSO_CUT, LASSO_DUPLICATE, LASSO_DELETE};
    /** How far down and right Duplicate's copy floats from the piece, page px: clear of it, to be seen and dragged. */
    static final int DUPLICATE_OFFSET = 40;
    /**
     * Whether the pen's stroke is the Lasso's loop: whenever the Lasso is in use, whichever tool it gave way to, the eraser
     * too (the owner, 2026-10-24: "sometimes selecting the lasso doesnt work"; with the eraser selected the pen erased).
     * Only the pen's eraser end still erases.
     */
    static boolean lassoLoop(boolean on, boolean eraserEnd) { return on && !eraserEnd; }
    /** Whether the column shows greys (the strip; the Dropper shows always) for {@code tool}: every slot and the stamp tool do; the eraser and the smudge tool have a size only. */
    static boolean greys(int tool) { return tool < ToolSlots.COUNT || tool == ToolSlots.STAMP; }

    final int width, height;
    /** The tray's rows ({@link #TRAY_ROWS}). */
    final int trays;
    /** The column's inner margin each side ({@link #ROOMY_EDGE}). */
    final int edge;
    final boolean compact;
    /** The column's width with its ink rule, if any; its items' width inside it. */
    final int side, inner;
    /** The items' heights, fitted (tray rows in whole halves); what is left over for the stretch at the foot (negative: it overflows). */
    final int icon, gap, slot, face, wet, box, spare;
    /** How far the items are from their least (0) to their preferred heights (1); under 0 only on a screen shorter than any of the four. */
    final double fit;

    /** The chrome for a {@code width × height} dp screen. */
    DrawLayout(int width, int height) { this(width, height, trays(Math.min(width, height) < WIDE)); }
    /**
     * As {@link #DrawLayout(int, int)} showing {@code trays} tray rows: laid out for {@link #TRAY_ROWS}, the rows shown
     * (and the smudge tool's and the eraser's) sharing that room, so fewer (the Nomad's eight) are taller and leave no gap.
     */
    DrawLayout(int width, int height, int trays) {
        this.trays = trays;
        this.width = width; this.height = height;
        compact = Math.min(width, height) < WIDE;
        side = compact ? COMPACT_SIDE : ROOMY_SIDE;
        edge = compact ? COMPACT_EDGE : ROOMY_EDGE;
        inner = side - BAR_RULE - 2 * edge;
        int[] preferred = preferred(), fewest = LEAST;
        int room = room();
        int least = total(fewest, TRAY_ROWS), most = total(preferred, TRAY_ROWS);
        // Shorter than any of the four screens: everything shrinks below its least alike, rather than off the end.
        double shrink = least > room ? room / (double) least : 1;
        fit = most == least ? 1 : least > room ? room / (double) least - 1 : StrokeMath.clamp((room - least) / (double) (most - least));
        int[] h = new int[preferred.length];
        for (int i = 0; i < h.length; i++)
            h[i] = (int) Math.floor(shrink < 1 ? fewest[i] * shrink : fewest[i] + fit * (preferred[i] - fewest[i]));
        // Fewer slots shown: the rows (the smudge tool's and the eraser's too) share the ten's room. A tray row is drawn in whole halves.
        h[Item.SLOT.ordinal()] = h[Item.SLOT.ordinal()] * (TRAY_ROWS + TOOL_ROWS) / (trays + TOOL_ROWS) / 2 * 2;
        // Room to spare: the picker box first, up to BOX_ROWS rows of faces; then the gaps between groups share the rest,
        // up to GAP_MOST times their preferred height.
        int left = room - total(h, trays), gapItem = Item.GAP.ordinal(), boxItem = Item.BOX.ordinal();
        int grow = Math.max(0, Math.min(left, boxMost() - h[boxItem]));
        h[boxItem] += grow; left -= grow;
        if (left > 0) h[gapItem] += Math.min(left / GAPS, (GAP_MOST - 1) * preferred[gapItem]);
        icon = h[0]; gap = h[1]; slot = h[2]; face = h[3]; wet = h[4]; box = h[5];
        spare = room - total(h, trays);
    }

    /** The preferred heights on this screen. */
    int[] preferred() { return compact ? COMPACT : ROOMY; }
    /** The least heights on every screen. */
    int[] least() { return LEAST; }
    /** The picker box's most, dp: {@link #BOX_ROWS} preferred rows of faces. */
    int boxMost() { return BOX_ROWS * preferred()[Item.FACE.ordinal()]; }
    /** The room for items: the column's height less the padding, Menu's row and the countdown. */
    private int room() { return columnHeight() - 2 * PAD - menuRow() - TIMER; }
    /**
     * Menu's row, dp: as tall as centres its icon on the page bar's previews (the owner, 2026-10-23: "make the menu icon
     * in the upper right in line with the page previews"), {@link #PAD} under the screen's top, so Menu and the page bar
     * read as one top row.
     */
    int menuRow() { return 2 * (STRIP_EDGE + previewHeight() / 2 - PAD); }
    /** How many of each item the column holds with {@code trays} tray rows (the smudge tool's and the eraser's besides). */
    static int[] count(int trays) {
        int[] count = COUNT.clone();
        count[Item.SLOT.ordinal()] = trays + TOOL_ROWS;
        return count;
    }
    /** The items' heights added up, each {@code heights}, with {@code trays} tray rows. */
    static int total(int[] heights, int trays) {
        int[] count = count(trays);
        int sum = 0;
        for (int i = 0; i < count.length; i++) sum += count[i] * heights[i];
        return sum;
    }
    /** The heights as fitted, in {@link Item} order. */
    int[] heights() { return new int[]{icon, gap, slot, face, wet, box}; }
    /** Whether {@code other} gives every item the same size, so nothing needs laying out again. */
    boolean same(DrawLayout other) {
        return other != null && other.side == side
                && other.pageBar() == pageBar() && java.util.Arrays.equals(other.heights(), heights());
    }

    /**
     * A face's square, dp (the strip's width and the slider's, each half the box): a {@link #FACE_ROWS}th of the box less
     * the {@link #FACE_GAP}, its cell ({@link #half}) less half the {@link #FACE_GAP} and its side bar's room ({@link
     * #faceMarkRoom}), at most {@link #FACE_SQUARE}.
     */
    int faceSide() { return Math.min(FACE_SQUARE, Math.min(box / FACE_ROWS - FACE_GAP, half() - FACE_GAP / 2 - faceMarkRoom())); }
    /** A face's room for its side bar beside its square (right of the grey strip, for its tab there), dp. */
    static int faceMarkRoom() { return FACE_MARK_GAP + FACE_MARK; }
    /** How tall a tray row's stroke may be, dp: the row less the room over and under it. */
    int trayStroke() { return slot - 2 * TRAY_TOP; }
    /** A cell's width (the Dropper, the Lasso, the wet indicator, the sand timer, Show Kinds, Shapes, Play), dp: half the column's inside each. */
    int half() { return inner / 2; }
    /**
     * The smudge tool's chooser, beside the column: its cards at most two across, each the Tools page's least card width
     * ({@link ToolsLayout#CARD_LEAST}) and its good height (Manta 132, Nomad 104), 2 dp lines between them and round them,
     * as many rows as the cards need, at most three or what the screen's height holds with a row of page buttons under
     * them (shown when there are more pages); never wider or more rows than the cards need.
     */
    static final int CHOOSER_COLUMNS = 2, CHOOSER_MOST_ROWS = 3;
    /** A chooser card's height, dp. */
    int chooserCard() { return compact ? 104 : 132; }
    /** The most rows of cards the chooser shows. */
    int chooserRows() {
        int room = height - 2 * PAD - ToolsLayout.PAGE_BUTTON - 3 * RULE;
        return Math.max(1, Math.min(CHOOSER_MOST_ROWS, (room + RULE) / (chooserCard() + RULE)));
    }
    /** The chooser's columns and rows for {@code count} cards. */
    int[] chooserGrid(int count) {
        int columns = Math.max(1, Math.min(CHOOSER_COLUMNS, count));
        return new int[]{columns, Math.max(1, Math.min(chooserRows(), (count + columns - 1) / columns))};
    }
    /** Whether {@code count} cards take more than one page. */
    boolean chooserPaged(int count) { int[] grid = chooserGrid(count); return count > grid[0] * grid[1]; }
    /** The chooser's width for {@code count} cards, dp, its outline in. */
    int chooserWidth(int count) { int columns = chooserGrid(count)[0]; return columns * ToolsLayout.CARD_LEAST + (columns + 1) * RULE; }
    /** The chooser's height for {@code count} cards, dp, its outline in, with page buttons when paged. */
    int chooserHeight(int count) {
        int rows = chooserGrid(count)[1];
        return rows * chooserCard() + (rows + 1) * RULE + (chooserPaged(count) ? ToolsLayout.PAGE_BUTTON + RULE : 0);
    }
    /**
     * The page bar along the page's top on the drawing screen (the owner, 2026-10-23: "moving the pages bar to the top";
     * {@link PageTurner}; it began as "a thin bottom bar, same style as the document reader", 2026-10-19), dp: a thin
     * strip the page's width, from just past the column, {@link #PAGE_RULE} of paper between it and the page (no rule
     * since 2026-10-20). Inside a notebook it stays along the foot, {@link #PAGE_RULE} over it. On the drawing screen it holds, from the end away from the column
     * (clear of the drawing hand), the Zoom button, the page and its count, and a strip of tiny page previews
     * (docs/archive/superpowers/specs/2026-10-20-tracing-paper-design.md, "The bottom strip"; {@link #stripPlaces}); inside a
     * notebook ‹ and › either side of the page and its count. 40 dp tall on the Manta, 36 on the Nomad; its buttons the
     * bar's height under the rule (39 and 35, over the owner's 26 dp pen targets) and {@link #pageButton} wide.
     */
    static final int ROOMY_PAGE_BAR = 40, COMPACT_PAGE_BAR = 36, PAGE_RULE = 1, ROOMY_PAGE_BUTTON = 56, COMPACT_PAGE_BUTTON = 48;
    /** The page bar's whole height, its rule in, dp. */
    int pageBar() { return pageBar(compact); }
    static int pageBar(boolean compact) { return compact ? COMPACT_PAGE_BAR : ROOMY_PAGE_BAR; }
    /** A page bar button's width, dp; its height is the bar's under its rule. */
    int pageButton() { return pageButton(compact); }
    static int pageButton(boolean compact) { return compact ? COMPACT_PAGE_BUTTON : ROOMY_PAGE_BUTTON; }
    /** The widest page and count the bar says, "250 / 250" ({@link DrawingBook#MAX_PAGES}), and its room either side, dp. */
    static final String WIDEST_PAGE = DrawingBook.MAX_PAGES + " / " + DrawingBook.MAX_PAGES;
    static final int PAGE_NUMBER_PAD = 8;
    /** The page and its count's place between ‹ and ›, dp: the widest, in labels, and its room either side. */
    static int pageNumber() { return (int) Math.ceil(LabText.width(WIDEST_PAGE, TypeStyle.LABEL.sp, TypeStyle.LABEL.tracking)) + 2 * PAGE_NUMBER_PAD; }
    /**
     * A preview in the strip, dp: 22 × 30 with a 4 dp gap on the Manta, 19 × 26 with 3 on the Nomad, {@link #STRIP_EDGE}
     * dp clear of the screen's top edge, so a riffle doesn't wake a system edge swipe; under it a 1 dp gap and the
     * drawing page's {@link #STRIP_TAB} dp ink tab along the bar's foot, {@link #PAGE_RULE} over the page; {@link
     * #STRIP_END} dp spare at the column's end.
     */
    static final int ROOMY_PREVIEW_WIDTH = 22, ROOMY_PREVIEW_HEIGHT = 30, ROOMY_PREVIEW_GAP = 4,
            COMPACT_PREVIEW_WIDTH = 19, COMPACT_PREVIEW_HEIGHT = 26, COMPACT_PREVIEW_GAP = 3,
            STRIP_TAB_GAP = 1, STRIP_TAB = 4, STRIP_EDGE = 4, STRIP_END = 4;
    /** A page showing through the page drawn on (tracing paper): a grey-600 tab under its preview, dp, thinner than the drawing page's ink one. */
    static final int GHOST_TAB = 3;
    int previewWidth() { return compact ? COMPACT_PREVIEW_WIDTH : ROOMY_PREVIEW_WIDTH; }
    int previewHeight() { return compact ? COMPACT_PREVIEW_HEIGHT : ROOMY_PREVIEW_HEIGHT; }
    int previewGap() { return compact ? COMPACT_PREVIEW_GAP : ROOMY_PREVIEW_GAP; }
    /** How many previews the strip of a bar {@code width} dp wide shows at most (the covers count as two, the + tile as one). */
    int previews(int width) {
        int room = width - STRIP_END;
        for (int part : barParts()) room -= part;
        return Math.max(1, Math.min(DrawingBook.MAX_PAGES + 3, (room + previewGap()) / (previewWidth() + previewGap())));
    }
    /**
     * The page-turn carets' icons are 18 dp (their targets stay {@link #PAGE_ARROW}; the owner, 2026-10-27: "reduce size of <
     * and > carets on top menu"), and 8 dp of space between the last preview and the caret beside it ("add a bit of space
     * to the right of the back cover page on the top bar, before the carets for page turning").
     */
    static final int CARET_HALF = 9, CARET_GAP = 8;
    /**
     * ‹ and › either side of the drawing screen's number (the owner, 2026-10-21: "add flipping arrows to the bottom bar"), dp wide each.
     */
    static final int PAGE_ARROW = 32;
    /** The number with its ‹ and ›, dp. */
    static int numberPart() { return pageNumber() + 2 * PAGE_ARROW; }
    /** The bar's parts before the previews, dp, from the end away from the column: Zoom, the number, the gap. */
    static int[] barParts() { return new int[]{zoomButton(), numberPart(), CARET_GAP}; }
    /** The strip's parts' widths across a bar {@code width} dp wide, dp: {@link #barParts}, then the previews. */
    int[] stripWidths(int width) {
        int[] parts = barParts(), w = java.util.Arrays.copyOf(parts, parts.length + 1);
        w[parts.length] = previews(width) * (previewWidth() + previewGap()) - previewGap();
        return w;
    }
    /**
     * Where the strip's parts start across a bar {@code width} dp wide, dp ({@link #stripWidths}). From the end away
     * from the column (on the page's right when {@code columnRight}): Zoom, the number, the gap, then the
     * previews toward the column, mirrored for a column on the left.
     */
    int[] stripPlaces(int width, boolean columnRight) {
        int[] w = stripWidths(width), x = new int[w.length];
        for (int k = 1; k < x.length; k++) x[k] = x[k - 1] + w[k - 1];
        if (!columnRight) for (int k = 0; k < x.length; k++) x[k] = width - x[k] - w[k];
        return x;
    }
    /** The slot of {@code visible} previews nearest {@code x} dp from the first one's left: the strip is one wide target. */
    int previewAt(float x, int visible) {
        return Math.max(0, Math.min(visible - 1, (int) Math.floor((x + previewGap() / 2f) / (previewWidth() + previewGap()))));
    }
    /** What the page bar shows of page {@code index} of {@code count}: "3 / 24", or a cover's word ({@link DrawingBook#FRONT}, {@link DrawingBook#BACK}). */
    static String pageWords(int index, int count) {
        return index == DrawingBook.FRONT ? NotebookCovers.FRONT_WORD : index == DrawingBook.BACK ? NotebookCovers.BACK_WORD : (index + 1) + " / " + count;
    }
    /** What it says of it to a screen reader: "Page 3 of 24", "Front cover". */
    static String pageSaid(int index, int count) {
        return index == DrawingBook.FRONT ? "Front cover" : index == DrawingBook.BACK ? "Back cover" : "Page " + (index + 1) + " of " + count;
    }
    /**
     * The brush line (the owner, 2026-10-22: "it'd be cool to see the description of the current brush displayed
     * somewhere, so you know what it is..?"), dp tall: along the screen's foot under the page, from just past Zen Mode's
     * button ({@link #lineStart}; the owner, 2026-10-23: "you can put the selected brush indicator to the right along the
     * bottom"), centred on Zen Mode's icon ({@link #footHeight}), its words left-aligned, toward the column, never at the far corner, where a right hand's palm rests ("i am
     * worried it will be triggered by a palm accidentally.. best to shift it to the left"). One line in captions, the tool
     * in use's name, a Basic tool's description and its card's words ({@link #brushWords}), cut with … to fit; hidden in Zen Mode. Only its
     * words take a tap, not the empty line after them. No preview of the stroke (the owner, 2026-10-27: "remove stroke
     * from bottom line brush info"; layout v3's 80 x 32 dp preview before the name until then); the line keeps that
     * preview's height, so the foot and the canvas stay where they were (18 dp, one caption line, before layout v3).
     * {@link #LINE_PAD} dp before the words, {@link #LINE_END} after the note.
     */
    static final int BRUSH_LINE = 32, LINE_PAD = 6, LINE_END = 8;
    /**
     * The brush line's room for the brush's name and words, dp, cut with … past it: fixed, so a new name needs no layout,
     * and short of the line's far end, where the palm rests. 280 and the wet indicator's 30 after it until the drop went
     * back to the column (the owner, 2026-10-26: "move wet to near the pickers"); 480 since Ukulele, a tenth wider, cut the
     * words short (the owner, 2026-10-26: "give the line more width").
     */
    static final int LINE_NAME = 480;
    /**
     * The open notebook's name (the owner, 2026-10-27: "the notebook name very small in the bottom right.. no need to have
     * it tappable"): in the foot's corner away from Zen Mode's button (bottom right with the column left, bottom left with
     * it right), beside the brush line, which ends where it starts, so it covers neither the line's words nor its note.
     * As wide as its words (the owner, 2026-10-27: "bottom right name is cut off"; 90 dp until then), at most this many
     * dp, cut with … past it: what the brush line leaves past its least ({@link #lineLeast}). In captions, not a target;
     * hidden in Zen Mode with the foot row.
     */
    int bookName() { return width - lineStart() - lineLeast(); }
    /** The brush line's least width, dp: its words' fixed room ({@link #LINE_NAME}), the note's gap and the line's ends. */
    static int lineLeast() { return LINE_PAD + LINE_NAME + NOTE_GAP + LINE_END; }
    /**
     * The light popouts beside the column (the eraser's kinds and Clear page, an offer; the owner, 2026-10-22: "long
     * press eraser menu, make it smaller and lighter so it doesn't leave ghosts on the screen after"), dp: a row's
     * height (a pen target), each word's room either side.
     */
    static final int LIGHT_ROW = 32, LIGHT_PAD = 10;
    /** Show Kinds' eyes' height, dp: finger-sized buttons under the names. */
    static final int KINDS_EYE = 44;
    /**
     * What the brush line says of a brush named {@code name}, a Basic tool's one-line description {@code line} (the Tools
     * page's and the picker's, {@link ToolPresets.Preset#line}; null for any other brush) and its card words ({@link
     * ModularWords#caption}) {@code caption}: "Name · description, style, tag", the description first so the cut leaves it
     * (the owner, 2026-10-26: "add description to brush info on bottom of screen"), anything that only says the name again
     * left out ("Wet gouache · paint stays wet").
     */
    static String brushWords(String name, String line, java.util.List<String> caption) {
        java.util.List<String> items = new java.util.ArrayList<>();
        if (line != null) items.add(line);
        items.addAll(caption);
        StringBuilder words = new StringBuilder();
        for (String item : items) if (!item.isEmpty() && !item.equalsIgnoreCase(name)) words.append(words.length() == 0 ? "" : ", ").append(item);
        return words.length() == 0 ? name : name + " · " + words;
    }
    /**
     * The app's notes ({@code PaintActivity.message}: "Inkwash hasn't been tested on this firmware yet.", an eraser's
     * kind, a guide's step) at the brush line's right, in captions as the name (the owner, 2026-10-22: "the system update
     * alerts... got cut off by the canvas. maybe put it in a dedicated spot... small stuff to the right, same height as the
     * name of the current brush"): shown {@link #NOTE_MS}, {@link #NOTE_GAP} dp clear of the name, which gives way to it;
     * a note takes at most {@link #noteWidth} of the line, cut with … past that. In Zen Mode (no line) a note is a toast.
     */
    static final long NOTE_MS = 4000;
    static final int NOTE_GAP = 12;
    /** The most of a {@code line} wide brush line a note takes, px or dp: two thirds, the name keeping the rest at least. */
    static int noteWidth(int line) { return Math.max(0, line * 2 / 3); }
    /** The label's widest (a slot's name flashed beside the column), dp: a longer name wraps to a second line. */
    static final int LABEL_MOST = 220;
    /**
     * The page's room: what the column and the page bar leave, dp, the proportion the notebook screens show a page at.
     * The canvas has the brush line less ({@link #canvasHeight}): a page of another size (made before it, or the
     * other way up) shows whole, fitted and centred in the canvas ({@link CanvasViewport#configure}).
     */
    int pageWidth() { return width - side; }
    int pageHeight() { return height - pageBar(); }
    /** The column's height, dp: from the screen's top down to Zen Mode's cell in the corner under it. */
    int columnHeight() { return height - zenHeight(); }
    /** The canvas's height, dp: the page's room less the foot row ({@link #footHeight}); the page bar over it, the line under it. */
    int canvasHeight() { return pageHeight() - footHeight(); }
}
