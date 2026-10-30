package io.github.mpdairy.monopaint;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * A notebook's page template (docs/archive/superpowers/specs/2026-10-19-notebook-options-design.md, "Template"): faint guides
 * printed under the drawing on every page but the covers. Blank (none, the default), the Supernote-style papers (Lined,
 * Wide Lined, Grid, Dot Grid, Isometric, Hex, Music, Cornell, Storyboard), Diary, Diary Page and Comics, all drawn
 * procedurally at the page's size; one of the owner's own Supernote templates (a PNG, JPG or WEBP in {@link #folder},
 * /sdcard/MyStyle, kept by its file name, key "my:" + name, its picture in the panel's greys, Blank when the file is gone);
 * or Random, which gives each new page one of its set (key "random", then "/" and each key it draws from; none: any built-in)
 * and the page keeps it ({@code DrawingBook}). The guides are paper, not marks ({@link ToneDocument#placeGuides}): the
 * eraser never takes them and a page with only guides is blank ({@link BlankPages}). They are kept as the template's key in
 * the options block ({@link BookOptions}), not in any page's file; a key an older version doesn't know is Blank there.
 * Drawn in the app's style: thin mid-grey rules, hairline panels, in the panel's own greys ({@link GrayPalette}), 2 px at
 * the Manta's 1920 and 1 px on smaller pages and thumbnails. Pure but for {@link #images}, checked on the desktop
 * ({@code NotebookOptionsChecks}, {@code TemplateChecks}).
 */
final class NotebookTemplate {
    static final NotebookTemplate BLANK = new NotebookTemplate("blank", "Blank"), LINED = new NotebookTemplate("lined", "Lined"),
            WIDE = new NotebookTemplate("wide", "Wide Lined"), GRID = new NotebookTemplate("grid", "Grid"), DOTS = new NotebookTemplate("dots", "Dot Grid"),
            ISOMETRIC = new NotebookTemplate("isometric", "Isometric"), HEX = new NotebookTemplate("hex", "Hex"), MUSIC = new NotebookTemplate("music", "Music"),
            CORNELL = new NotebookTemplate("cornell", "Cornell"), STORYBOARD = new NotebookTemplate("storyboard", "Storyboard"),
            DIARY = new NotebookTemplate("diary", "Diary"), COMICS = new NotebookTemplate("comics", "Comics"),
            /**
             * Three boxes down the left (Did, Saw, Heard) and a big one for the drawing: the Daily Prompt's diary cards ({@link
             * DailyPrompt}). Its boxes are timed, in order ({@link TimedSections}): Did 2:30, Saw 2:30, Heard 0:30, Picture 0:30.
             */
            DIARY_PAGE = new NotebookTemplate("diarypage", "Diary Page", new String[]{"Did", "Saw", "Heard", "Picture"}, new int[]{150, 150, 30, 30}),
            RANDOM = new NotebookTemplate("random", "Random");
    /** The templates drawn here, in the order they are offered. */
    private static final NotebookTemplate[] BUILT_IN = {BLANK, LINED, WIDE, GRID, DOTS, ISOMETRIC, HEX, MUSIC, CORNELL, STORYBOARD, DIARY, DIARY_PAGE, COMICS};
    /** The prefix of an own template's key, and what Random's key puts between the keys it draws from. */
    static final String MINE = "my:", SET = "/";

    /** The key kept in the file (never respelled) and the label shown (Title Case: docs/TEXT-STYLE.md). */
    final String key, label;
    /**
     * Timed Sections ({@link TimedSections}): its boxes' names (Title Case, shown in the side bar's countdown) and their times,
     * s, in order, as {@link #sectionRects} places them; none for a template without them. The prompts file may change the
     * times ({@link DailyPrompt#sectionTimes}).
     */
    final String[] sections;
    final int[] sectionSeconds;
    private NotebookTemplate(String key, String label) { this(key, label, new String[0], new int[0]); }
    private NotebookTemplate(String key, String label, String[] sections, int[] seconds) { this.key = key; this.label = label; this.sections = sections; this.sectionSeconds = seconds; }
    /** Whether its boxes are timed. */
    boolean timed() { return sections.length > 0; }
    /** Its timed boxes on a {@code width x height} page, {x0, y0, x1, y1} exclusive, in {@link #sections}' order; none for a template without them. */
    int[][] sectionRects(int width, int height) {
        if (!equals(DIARY_PAGE)) return new int[0][];
        int side = Math.round(width * .08f), top = Math.round(height * .06f), bottom = height - top, right = width - side;
        // Three boxes (Did, Saw, Heard) in a left column of 36% of the width, the drawing box beside them.
        int gap = Math.round(width * .02f), column = side + Math.round((right - side) * .36f), box = (bottom - top - 2 * gap) / 3;
        int[][] rects = new int[4][];
        for (int i = 0; i < 3; i++) rects[i] = new int[]{side, top + i * (box + gap), column, top + i * (box + gap) + box};
        rects[3] = new int[]{column + gap, top, right, bottom};
        return rects;
    }

    /** Every built-in template, then Random: what a tap on the library card steps through. */
    static NotebookTemplate[] values() {
        NotebookTemplate[] all = Arrays.copyOf(BUILT_IN, BUILT_IN.length + 1);
        all[BUILT_IN.length] = RANDOM;
        return all;
    }
    /** Where the owner's own Supernote templates are (Supernote's MyStyle folder; null: none), and what reads one ({@link Images}); set by the app. */
    static volatile File folder;
    static volatile Images images;
    /** Reads a picture as ARGB pixels scaled to {@code width x height}, or null when it can't. */
    interface Images { int[] pixels(File file, int width, int height); }

    /** The owner's own templates now in {@link #folder}, by name. */
    static List<NotebookTemplate> mine() {
        List<NotebookTemplate> mine = new ArrayList<>();
        File[] files = folder == null ? null : folder.listFiles();
        if (files == null) return mine;
        Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (File f : files) if (f.isFile() && f.getName().toLowerCase(java.util.Locale.ROOT).matches(".+\\.(png|jpe?g|webp)")) mine.add(of(MINE + f.getName()));
        return mine;
    }
    /** Every template to choose from: the built-ins, the owner's own, then Random. */
    static List<NotebookTemplate> choices() {
        List<NotebookTemplate> all = new ArrayList<>(Arrays.asList(BUILT_IN));
        all.addAll(mine());
        all.add(RANDOM);
        return all;
    }

    /** The template {@code key} names; Blank for none or one this version doesn't know. */
    static NotebookTemplate of(String key) {
        if (key == null) return BLANK;
        for (NotebookTemplate t : values()) if (t.key.equals(key)) return t;
        if (key.startsWith(MINE) && key.length() > MINE.length() && !key.contains("/")) {
            String name = key.substring(MINE.length()), dot = name.contains(".") ? name.substring(0, name.lastIndexOf('.')) : name;
            return new NotebookTemplate(key, dot.isEmpty() ? name : dot);
        }
        if (key.startsWith(RANDOM.key + SET)) return new NotebookTemplate(key, RANDOM.label);
        return BLANK;
    }
    /** Whether this is Random (with or without a set). */
    boolean random() { return key.equals(RANDOM.key) || key.startsWith(RANDOM.key + SET); }
    /** Whether this is one of the owner's own templates. */
    boolean own() { return key.startsWith(MINE); }
    /** Random's set: the templates it draws from (empty: any built-in but Blank). */
    List<NotebookTemplate> set() {
        List<NotebookTemplate> set = new ArrayList<>();
        if (!random()) return set;
        for (String k : key.split(SET)) { NotebookTemplate t = of(k); if (t != BLANK && !t.random() && !set.contains(t)) set.add(t); }
        return set;
    }
    /** Random drawing from {@code set} (empty or null: any). */
    static NotebookTemplate random(List<NotebookTemplate> set) {
        if (set == null || set.isEmpty()) return RANDOM;
        StringBuilder key = new StringBuilder(RANDOM.key);
        for (NotebookTemplate t : set) if (t != BLANK && !t.random()) key.append(SET).append(t.key);
        return of(key.toString());
    }
    /**
     * What Random gives a page: one of its set, or of the built-ins but Blank, never one of {@code beside} (its neighbours'
     * templates, null for none) while another is left to give. Any other template gives itself.
     */
    NotebookTemplate pick(Random dice, NotebookTemplate... beside) {
        if (!random()) return this;
        List<NotebookTemplate> from = set();
        if (from.isEmpty()) from = new ArrayList<>(Arrays.asList(BUILT_IN).subList(1, BUILT_IN.length));
        for (NotebookTemplate t : beside) if (from.size() > 1) from.remove(t);
        return from.get(dice.nextInt(from.size()));
    }

    @Override public boolean equals(Object o) { return o instanceof NotebookTemplate && ((NotebookTemplate) o).key.equals(key); }
    @Override public int hashCode() { return key.hashCode(); }
    @Override public String toString() { return key; }

    /** A rule's tone and a panel's hairline's: two of the panel's greys. */
    static final int RULE = GrayPalette.VALUES[11], PANEL = GrayPalette.VALUES[8];
    /** A tone for no guide: paper. */
    static final int NONE = 255;
    /** A line's width at a page 1920 wide, px; narrower pages and thumbnails scale it down, never under one pixel. */
    private static final double LINE = 2;

    /** The guides on a {@code width x height} page: a tone a pixel, row by row ({@link #NONE}: none there); null for Blank, Random and an own template that can't be read. */
    byte[] guides(int width, int height) {
        if (this == BLANK || random()) return null;
        if (own()) return picture(width, height);
        byte[] tones = new byte[width * height];
        Arrays.fill(tones, (byte) NONE);
        int line = Math.max(1, (int) Math.round(LINE * width / 1920.0));
        int side = Math.round(width * .08f), top = Math.round(height * .06f), bottom = height - top, right = width - side;
        if (equals(DIARY)) {
            // A big panel above, then rules every 4.5% of the width down to the foot.
            int panelBottom = top + Math.round((bottom - top) * .46f);
            frame(tones, width, side, top, right, panelBottom, line, PANEL);
            int step = Math.max(2 * line + 2, Math.round(width * .045f));
            for (int y = panelBottom + step + step / 2; y <= bottom; y += step) fill(tones, width, side, y, right, y + line, RULE);
        } else if (equals(DIARY_PAGE)) {
            // Its boxes (sectionRects), the first three labelled.
            int[][] boxes = sectionRects(width, height);
            for (int[] b : boxes) frame(tones, width, b[0], b[1], b[2], b[3], line, PANEL);
            int cell = Math.max(1, Math.round(width * .006f));
            for (int i = 0; i < 3; i++) label(tones, width, sections[i].toUpperCase(java.util.Locale.ROOT), boxes[i][0] + 3 * line + cell, boxes[i][1] + 3 * line + cell, cell);
        } else if (equals(COMICS) || equals(STORYBOARD)) {
            // Comics: two columns of three panels. Storyboard: the same, each frame 16:9 with two lines for notes under it.
            int gutter = Math.round(width * .03f), columns = 2, rows = 3;
            int panelWidth = (right - side - (columns - 1) * gutter) / columns, cell = (bottom - top - (rows - 1) * gutter) / rows;
            int panelHeight = equals(STORYBOARD) ? Math.min(cell * 2 / 3, panelWidth * 9 / 16) : cell;
            for (int r = 0; r < rows; r++)
                for (int c = 0; c < columns; c++) {
                    int x = side + c * (panelWidth + gutter), y = top + r * (cell + gutter);
                    frame(tones, width, x, y, x + panelWidth, y + panelHeight, line, PANEL);
                    if (equals(STORYBOARD)) for (int k = 1; k <= 2; k++) { int ry = y + panelHeight + k * (cell - panelHeight) * 2 / 5; fill(tones, width, x, ry, x + panelWidth, ry + line, RULE); }
                }
        } else if (equals(LINED) || equals(WIDE)) {
            // College rule (about 7 mm on the Manta) or wide (about 9.5 mm), under a heading line.
            int step = Math.max(2 * line + 2, Math.round(width * (equals(WIDE) ? .058f : .043f)));
            for (int y = top + step; y <= bottom; y += step) fill(tones, width, side, y, right, y + line, RULE);
        } else if (equals(GRID) || equals(DOTS)) {
            // 5 mm squares, edge to edge across the page (Supernote's), or a dot at each crossing.
            int step = Math.max(3, Math.round(width * .031f)), x0 = (width % step) / 2, y0 = (height % step) / 2;
            if (equals(GRID)) {
                for (int y = y0; y < height; y += step) fill(tones, width, 0, y, width, y + line, RULE);
                for (int x = x0; x < width; x += step) fill(tones, width, x, 0, x + line, height, RULE);
            } else {
                int dot = Math.max(1, 2 * line);
                for (int y = y0; y < height; y += step) for (int x = x0; x < width; x += step) fill(tones, width, x, y, x + dot, y + dot, PANEL);
            }
        } else if (equals(ISOMETRIC)) {
            // Equilateral triangles: upright lines `step` apart and two families at 30 degrees, crossing on the uprights.
            int step = Math.max(3, Math.round(width * .04f));
            double rise = step / Math.sqrt(3), reach = width / Math.sqrt(3);
            for (int x = 0; x < width; x += step) fill(tones, width, x, 0, x + line, height, RULE);
            for (int n = (int) Math.floor(-reach / (2 * rise)) - 1; n * 2 * rise <= height + reach; n++) {
                double y = n * 2 * rise;
                segment(tones, width, 0, y, width, y + reach, line, RULE);
                segment(tones, width, 0, y, width, y - reach, line, RULE);
            }
        } else if (equals(HEX)) {
            // Flat-topped hexagons of radius 3.5% of the width, edge to edge.
            double r = Math.max(3, width * .035), h = r * Math.sqrt(3) / 2;
            for (int c = 0; c * 1.5 * r < width + r; c++)
                for (int k = -1; k * 2 * h < height + h; k++) {
                    double cx = c * 1.5 * r, cy = k * 2 * h + (c % 2 == 1 ? h : 0);
                    // Three of the six sides each: neighbours draw the rest.
                    segment(tones, width, cx - r, cy, cx - r / 2, cy - h, line, RULE);
                    segment(tones, width, cx - r / 2, cy - h, cx + r / 2, cy - h, line, RULE);
                    segment(tones, width, cx + r / 2, cy - h, cx + r, cy, line, RULE);
                }
        } else if (equals(MUSIC)) {
            // Staves of five lines, a gap of 1.2% of the width between lines, 8.5% between staves.
            int gap = Math.max(2, Math.round(width * .012f)), staff = Math.round(width * .085f);
            for (int y = top; y + 4 * gap <= bottom; y += staff) for (int l = 0; l < 5; l++) fill(tones, width, side, y + l * gap, right, y + l * gap + line, PANEL);
        } else if (equals(CORNELL)) {
            // A heading line, a cue column at the left, ruled notes beside it, a summary box at the foot.
            int heading = top + Math.round(height * .06f), summary = bottom - Math.round(height * .2f), cue = side + Math.round((right - side) * .3f);
            fill(tones, width, side, heading, right, heading + line, PANEL);
            fill(tones, width, side, summary, right, summary + line, PANEL);
            fill(tones, width, cue, heading, cue + line, summary, PANEL);
            int step = Math.max(2 * line + 2, Math.round(width * .043f));
            for (int y = heading + step; y < summary - line; y += step) fill(tones, width, cue + 2 * line, y, right, y + line, RULE);
        }
        return tones;
    }
    /** An own template's picture at {@code width x height}, in the panel's greys; null when the file is gone or can't be read. */
    private byte[] picture(int width, int height) {
        Images reader = images;
        File dir = folder;
        if (reader == null || dir == null) return null;
        File file = new File(dir, key.substring(MINE.length()));
        if (!file.isFile()) return null;
        try {
            int[] argb = reader.pixels(file, width, height);
            return argb == null || argb.length != width * height ? null : tones(argb);
        } catch (RuntimeException | OutOfMemoryError unreadable) { return null; }
    }
    /** ARGB pixels as guides: near-white and see-through are paper, the rest the nearest of the panel's greys. */
    static byte[] tones(int[] argb) {
        byte[] tones = new byte[argb.length];
        for (int i = 0; i < argb.length; i++) {
            // Its brightness over white, as an imported picture's (PictureImport, the owner's Import Image, 2026-10-22).
            int grey = PictureImport.grey(argb[i]);
            if (grey >= PictureImport.PAPER) { tones[i] = (byte) NONE; continue; }
            int best = 0;
            for (int g = 1; g < GrayPalette.VALUES.length; g++) if (Math.abs(GrayPalette.VALUES[g] - grey) < Math.abs(GrayPalette.VALUES[best] - grey)) best = g;
            tones[i] = (byte) GrayPalette.VALUES[best];
        }
        return tones;
    }

    /** 3 x 5 letters of the Diary Page labels, a row a string. */
    private static final java.util.Map<Character, String[]> LETTERS = new java.util.HashMap<>();
    static {
        String[] all = {"D 110 101 101 101 110", "I 111 010 010 010 111", "S 011 100 010 001 110", "A 010 101 111 101 101",
            "W 101 101 101 111 101", "H 101 101 111 101 101", "E 111 100 110 100 111", "R 110 101 110 101 101"};
        for (String l : all) LETTERS.put(l.charAt(0), l.substring(2).split(" "));
    }
    /** {@code text} in {@link #PANEL} from (x, y), each dot of a letter {@code cell} px square. */
    private static void label(byte[] tones, int width, String text, int x, int y, int cell) {
        for (char c : text.toCharArray()) {
            String[] rows = LETTERS.get(c);
            for (int r = 0; r < 5; r++) for (int k = 0; k < 3; k++)
                if (rows[r].charAt(k) == '1') fill(tones, width, x + k * cell, y + r * cell, x + (k + 1) * cell, y + (r + 1) * cell, PANEL);
            x += 4 * cell;
        }
    }

    /** A hairline frame from (x0, y0) to (x1, y1), exclusive, {@code line} thick inside. */
    private static void frame(byte[] tones, int width, int x0, int y0, int x1, int y1, int line, int tone) {
        fill(tones, width, x0, y0, x1, y0 + line, tone); fill(tones, width, x0, y1 - line, x1, y1, tone);
        fill(tones, width, x0, y0, x0 + line, y1, tone); fill(tones, width, x1 - line, y0, x1, y1, tone);
    }
    private static void fill(byte[] tones, int width, int x0, int y0, int x1, int y1, int tone) {
        int height = tones.length / width;
        // Clipped to the row both sides: a segment past the right edge on the last row once ran off the array (a small picture's hexagons).
        int a = Math.min(width, Math.max(0, x0)), b = Math.max(a, Math.min(width, x1));
        for (int y = Math.max(0, y0); y < Math.min(height, y1); y++) if (b > a) Arrays.fill(tones, y * width + a, y * width + b, (byte) tone);
    }
    /** A straight line {@code line} px thick from (x0, y0) to (x1, y1), clipped to the page. */
    private static void segment(byte[] tones, int width, double x0, double y0, double x1, double y1, int line, int tone) {
        int steps = (int) Math.ceil(Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)));
        for (int s = 0; s <= steps; s++) {
            int x = (int) Math.round(x0 + (x1 - x0) * s / Math.max(1, steps)), y = (int) Math.round(y0 + (y1 - y0) * s / Math.max(1, steps));
            fill(tones, width, x, y, x + line, y + line, tone);
        }
    }

    /** The guides made lately, by template and size, for the next page that wants them: never changed once made, so pages share them. */
    private static final java.util.LinkedHashMap<String, byte[]> MADE = new java.util.LinkedHashMap<String, byte[]>(8, .75f, true) {
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<String, byte[]> eldest) { return size() > 4; }
    };
    /**
     * The guides as the screen shows them at step {@code shown} ({@link NotebookOptions#guidesShown}; the owner, 2026-10-22):
     * {@link #shared} in full, none Hidden, else each guide lightened toward paper as a ghost of that step is
     * ({@link NotebookOptions#GUIDE_STRENGTH}: g at s shows as 255 − (255 − g)·s), so the page's own dots draw Half and
     * Faint (e-ink has no transparency). Kept as {@link #shared} is: made once a template, size and step. Never the copies'.
     */
    byte[] shown(int width, int height, int shown) {
        if (shown == NotebookOptions.HIDDEN) return null;
        byte[] full = shared(width, height);
        if (full == null || shown <= NotebookOptions.FULL || shown > NotebookOptions.HIDDEN) return full;
        String at = key + "@" + width + "x" + height + "#" + shown;
        synchronized (MADE) { byte[] made = MADE.get(at); if (made != null) return made; }
        int s = NotebookOptions.GUIDE_STRENGTH[shown];
        byte[] made = new byte[full.length];
        for (int i = 0; i < made.length; i++) made[i] = (byte) (255 - ((255 - (full[i] & 255)) * s + 50) / 100);
        synchronized (MADE) { byte[] raced = MADE.get(at); if (raced != null) return raced; MADE.put(at, made); }
        return made;
    }
    /** {@link #guides} kept for the next call with the same template and size (a Random notebook's pages take turns). Shared: never write to it. */
    byte[] shared(int width, int height) {
        if (this == BLANK || random()) return null;
        // An own template whose file went is Blank from then on, kept or not.
        if (own() && (folder == null || !new File(folder, key.substring(MINE.length())).isFile())) return null;
        String at = key + "@" + width + "x" + height;
        synchronized (MADE) { byte[] made = MADE.get(at); if (made != null) return made; }
        byte[] made = guides(width, height);
        // An own template not read (gone, unreadable) is not kept: it may be there next time.
        if (made != null) synchronized (MADE) { byte[] raced = MADE.get(at); if (raced != null) return raced; MADE.put(at, made); }
        return made;
    }
}
