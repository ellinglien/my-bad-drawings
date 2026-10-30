package io.github.mpdairy.monopaint;

import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

/**
 * A notebook's front and back covers (docs/archive/superpowers/specs/2026-10-19-notebook-options-design.md, "Covers"; the
 * approved prototype docs/archive/brush-lab/prototypes/cover-designs.html): their paper, made from the notebook's name and its
 * design each time it is shown, never kept in its file. Both are its design's printed sheet with the cloth tape down the
 * spine ({@link CoverPattern}: the left of the front, the right of the back); the front has a composition book's label,
 * white with slightly rounded corners and a double rule, saying COMPOSITION BOOK, then NAME (the notebook's name written
 * on its line), SUBJECT and DATE (the day the notebook's stored name starts with); and, when a page is its cover picture,
 * a smaller copy of that page glued on under the label, framed in a fine ink rule. Whatever is drawn on a cover lies
 * over its paper ({@link ToneDocument#paperPixels}). Made at any size: the page's for drawing on and the PDF, a shelf
 * tile's from the pictures the file keeps; at a tile's size the label's small words and the date are left out, as they
 * would only be smudges. The words' letters come from the app's {@link #lettering} (iA Writer Duo on the device), laid
 * out here by the face's measure ({@link LabText#advance}). Pure, checked and timed on the desktop ({@code CoverChecks},
 * {@code CoverDesignChecks}).
 */
final class NotebookCovers {
    /** What the page number says on the front and back covers (docs/TEXT-STYLE.md). */
    static final String FRONT_WORD = "Cover", BACK_WORD = "Back";
    /** The menu's words for no cover picture. */
    static final String NO_PICTURE = "No Cover Picture";
    /** The notebook view's buttons: a page as the cover picture, and none; and the note once a page is the picture. */
    static final String USE_BUTTON = "Use on Cover", NO_PICTURE_BUTTON = "No Picture", ON_COVER = "On the cover";
    /** The menus' row and the chooser's title: the notebook's design ({@link CoverDesign}). */
    static final String DESIGN = "Cover Design";

    // The label, in parts of the cover's width (its top in parts of the height): the prototype's measures

    /** The label's width, height and top, and its corners' radius. */
    static final double LABEL_WIDTH = .52, LABEL_HEIGHT = .33, LABEL_TOP = .14, LABEL_ROUND = .02;
    /** The double rule: its inset from the label's edge, a thin line, a gap, a hairline; each at least a pixel. */
    static final double RULE_INSET = .009, RULE = .0024, RULE_GAP = .0034, HAIRLINE = .0012;
    /** The room inside the rule; COMPOSITION BOOK's size and letter spacing (em); NAME, SUBJECT and DATE's, and the room after them. */
    static final double PAD = .036, TITLE = .026, TITLE_SPACING = .14, SMALL = .0155, SMALL_SPACING = .1, AFTER_WORDS = .012;
    /** The three lines' baselines, in parts of the label's height. */
    static final double[] ROWS = {.5, .69, .86};
    /** The name's largest and least size (then cut with an ellipsis), and the date's. */
    static final double NAME_MOST = .052, NAME_LEAST = .026, DATE_SIZE = .022;
    /** The label's words (a printed composition book's, in caps as printed). */
    static final String TITLE_WORDS = "COMPOSITION BOOK";
    static final String[] LINE_WORDS = {"NAME", "SUBJECT", "DATE"};
    /** Under this width (a shelf tile) the small words and the date are left out. */
    static final int TINY = 600;
    /** The cover picture's width, the room above it (under the label) and below it. */
    static final double PICTURE_WIDTH = .58, PICTURE_GAP = .05, PICTURE_FOOT = .06;
    /** The picture's rule: a cover this many pixels wide has one pixel of it (at least one). */
    static final int PICTURE_RULE = 320;

    /** Words on the label: ink letters from {@code x} along {@code baseline}, {@code size} px, in the face's {@code weight} (400 or 700). */
    static final class Words {
        final String text;
        final float x, baseline, size;
        final int weight;
        Words(String text, float x, float baseline, float size, int weight) { this.text = text; this.x = x; this.baseline = baseline; this.size = size; this.weight = weight; }
    }
    /** Draws a label's {@link Label#words}: ink letters on its white, each from its place along its baseline. */
    interface Lettering { void letter(byte[] tones, int width, int height, Label label); }
    /** The app's letters (set once, at start); null: a label without words (the desktop's checks, unless they set one). */
    static volatile Lettering lettering;

    /** The front cover's label for a cover {@code width × height} px: its card, its rule, its lines and its words. */
    static final class Label {
        /** The card. */
        final int x, y, width, height;
        final double radius;
        /** The double rule: its inset, the thin line, the gap and the hairline, px. */
        final int inset, rule, gap, hairline;
        /** The writing lines: from {@code lineStart} to {@code lineEnd}, {@code line} px thick, their tops. */
        final int lineStart, lineEnd, line;
        final int[] lines;
        /** The name as written on its line (cut with an ellipsis if it doesn't fit) and its size; every word on the label. */
        final String name;
        final float nameSize;
        final List<Words> words;
        Label(int x, int y, int width, int height, double radius, int inset, int rule, int gap, int hairline, int lineStart, int lineEnd, int line, int[] lines,
                String name, float nameSize, List<Words> words) {
            this.x = x; this.y = y; this.width = width; this.height = height; this.radius = radius;
            this.inset = inset; this.rule = rule; this.gap = gap; this.hairline = hairline;
            this.lineStart = lineStart; this.lineEnd = lineEnd; this.line = line; this.lines = lines;
            this.name = name; this.nameSize = nameSize; this.words = words;
        }
        int bottom() { return y + height; }
    }

    /** What the label's name line says for a notebook named {@code name} (its file's): the name after its date, or all of it. */
    static String written(String name) { return DrawingFiles.isoDate(name) != null ? name.substring(11) : name; }
    /** What its date line says: the day its name starts with ("19 Oct 2026"), or nothing. */
    static String dated(String name) {
        java.time.LocalDate date = DrawingFiles.isoDate(name);
        return date == null ? "" : date.getDayOfMonth() + " " + date.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH) + " " + date.getYear();
    }
    /** {@code text}'s width at {@code size} px, {@code spacing} px between its letters. */
    static float width(String text, float size, float spacing) {
        float w = 0;
        for (int i = 0; i < text.length(); i++) w += LabText.advance(text.charAt(i)) * size + spacing;
        return text.isEmpty() ? 0 : w - spacing;
    }
    private static int px(int width, double share) { return Math.max(1, (int) Math.round(width * share)); }
    /** {@code text}'s letters one by one from {@code x}, {@code spacing} px apart, so every device spaces them the same. */
    private static void spaced(List<Words> words, String text, float x, float baseline, float size, float spacing) {
        for (int i = 0; i < text.length(); i++) {
            String letter = text.substring(i, i + 1);
            words.add(new Words(letter, x, baseline, size, 700));
            x += width(letter, size, 0) + spacing;
        }
    }

    /** The label on a cover {@code width × height} px for the notebook named {@code name} (its file's name). */
    static Label label(String name, int width, int height) {
        int spine = CoverPattern.spine(width);
        double cx = spine + (width - spine) / 2.0;
        int lw = (int) Math.round(width * LABEL_WIDTH), lh = (int) Math.round(width * LABEL_HEIGHT);
        int x = (int) Math.round(cx - lw / 2.0), y = (int) Math.round(height * LABEL_TOP);
        int inset = px(width, RULE_INSET), rule = px(width, RULE), gap = px(width, RULE_GAP), hairline = px(width, HAIRLINE);
        List<Words> words = new ArrayList<>();
        float pad = (float) (width * PAD), left = x + pad, right = x + lw - pad;
        float title = (float) (width * TITLE);
        spaced(words, TITLE_WORDS, (float) (cx - width(TITLE_WORDS, title, title * (float) TITLE_SPACING) / 2), y + pad + title * .82f, title, title * (float) TITLE_SPACING);
        float small = (float) (width * SMALL), smallSpacing = small * (float) SMALL_SPACING;
        float widest = 0;
        for (String word : LINE_WORDS) widest = Math.max(widest, width(word, small, smallSpacing));
        float lineStart = left + widest + (float) (width * AFTER_WORDS);
        boolean tiny = width < TINY;
        int[] lines = new int[ROWS.length];
        float[] baselines = new float[ROWS.length];
        for (int i = 0; i < ROWS.length; i++) {
            baselines[i] = (float) (y + lh * ROWS[i]);
            if (!tiny) spaced(words, LINE_WORDS[i], left, baselines[i], small, smallSpacing);
            lines[i] = Math.round(baselines[i] + small * .3f);
        }
        // The name on its line, as large as fits (down to a least size, then cut with an ellipsis).
        String written = written(name);
        float room = right - lineStart - (float) (width * .01), size = (float) (width * NAME_MOST), least = (float) (width * NAME_LEAST);
        while (width(written, size, 0) > room && size > least) size *= .94f;
        String shown = written;
        if (width(shown, size, 0) > room) {
            int cut = written.length();
            while (cut > 0 && width(written.substring(0, cut) + "…", size, 0) > room) cut = written.offsetByCodePoints(cut, -1);
            shown = written.substring(0, cut).replaceAll("\\s+$", "") + "…";
        }
        if (!written.isEmpty()) words.add(new Words(shown, lineStart + (float) (width * .006), baselines[0] + small * .3f - (float) (width * .006), size, 700));
        String date = dated(name);
        if (!tiny && !date.isEmpty()) words.add(new Words(date, lineStart + (float) (width * .006), baselines[2] + small * .3f - (float) (width * .005), (float) (width * DATE_SIZE), 400));
        return new Label(x, y, lw, lh, width * LABEL_ROUND, inset, rule, gap, hairline, Math.round(lineStart), Math.round(lineStart) + Math.round(right - lineStart), hairline, lines,
                shown, size, words);
    }

    /** Draws {@code label} on {@code tones} ({@code width × height}): its card and double rule, its lines, its words, at the panel's greys. */
    static void draw(byte[] tones, int width, int height, Label l) {
        int x0 = Math.max(0, l.x), y0 = Math.max(0, l.y), x1 = Math.min(width, l.x + l.width), y1 = Math.min(height, l.y + l.height);
        if (x1 <= x0 || y1 <= y0) return;
        int w = x1 - x0, h = y1 - y0;
        double[] card = new double[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) card[y * w + x] = (tones[(y0 + y) * width + x0 + x] & 255) / 255.0;
        // The card, then the double rule: a thin line, a gap, a hairline, each a rounded box in the last.
        double r = l.radius;
        int m = l.inset, i2 = l.inset + l.rule + l.gap;
        round(card, w, h, l.x - x0, l.y - y0, l.width, l.height, r, 1);
        round(card, w, h, l.x - x0 + m, l.y - y0 + m, l.width - 2 * m, l.height - 2 * m, Math.max(0, r - m), 0);
        round(card, w, h, l.x - x0 + m + l.rule, l.y - y0 + m + l.rule, l.width - 2 * (m + l.rule), l.height - 2 * (m + l.rule), Math.max(0, r - m - l.rule), 1);
        round(card, w, h, l.x - x0 + i2, l.y - y0 + i2, l.width - 2 * i2, l.height - 2 * i2, Math.max(0, r - i2), 0);
        round(card, w, h, l.x - x0 + i2 + l.hairline, l.y - y0 + i2 + l.hairline, l.width - 2 * (i2 + l.hairline), l.height - 2 * (i2 + l.hairline), Math.max(0, r - i2 - l.hairline), 1);
        // The writing lines.
        for (int top : l.lines)
            for (int y = Math.max(0, top - y0); y < Math.min(h, top - y0 + l.line); y++)
                for (int x = Math.max(0, l.lineStart - x0); x < Math.min(w, l.lineEnd - x0); x++) card[y * w + x] = 0;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) tones[(y0 + y) * width + x0 + x] = (byte) Math.round(card[y * w + x] * 255);
        Lettering letters = lettering;
        if (letters != null && !l.words.isEmpty()) letters.letter(tones, width, height, l);
        for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) tones[y * width + x] = (byte) CoverPattern.LEVELS[tones[y * width + x] & 255];
    }
    /** A box {@code w × h} at ({@code x}, {@code y}) with corners rounded {@code r}, of {@code tone} (0 ink, 1 paper), its edge smoothed over a pixel. */
    private static void round(double[] card, int cw, int ch, int x, int y, int w, int h, double r, double tone) {
        if (w <= 0 || h <= 0) return;
        double hx = w / 2.0, hy = h / 2.0, mx = x + hx, my = y + hy;
        r = Math.min(r, Math.min(hx, hy));
        for (int py = Math.max(0, y); py < Math.min(ch, y + h); py++)
            for (int qx = Math.max(0, x); qx < Math.min(cw, x + w); qx++) {
                // The pixel's centre's distance outside the box (below 0 inside).
                double dx = Math.abs(qx + .5 - mx) - (hx - r), dy = Math.abs(py + .5 - my) - (hy - r);
                double ox = Math.max(dx, 0), oy = Math.max(dy, 0);
                double out = (ox > 0 || oy > 0 ? Math.sqrt(ox * ox + oy * oy) : 0) + Math.min(Math.max(dx, dy), 0) - r;
                double cover = Math.max(0, Math.min(1, .5 - out));
                if (cover <= 0) continue;
                int o = py * cw + qx;
                card[o] += (tone - card[o]) * cover;
            }
    }

    /**
     * Where the cover picture goes on a cover {@code width × height} px, for a page {@code pageWidth × pageHeight}: x, y,
     * width and height, its proportions kept, {@link #PICTURE_WIDTH} of the cover wide (less if it wouldn't fit), centred
     * over the printed part (right of the tape) and in the room under the label.
     */
    static int[] pictureBox(int width, int height, int pageWidth, int pageHeight) {
        double top = Math.round(height * LABEL_TOP) + Math.round(width * LABEL_HEIGHT) + width * PICTURE_GAP, bottom = height * (1 - PICTURE_FOOT);
        double w = width * PICTURE_WIDTH, h = w * pageHeight / pageWidth;
        if (h > bottom - top) { h = Math.max(1, bottom - top); w = h * pageWidth / pageHeight; }
        int pw = Math.max(1, (int) Math.round(w)), ph = Math.max(1, (int) Math.round(h)), spine = CoverPattern.spine(width);
        return new int[]{(int) Math.round(spine + (width - spine - pw) / 2.0), (int) Math.round(top + (bottom - top - ph) / 2), pw, ph};
    }

    /** The page glued on: its tones at any size. */
    interface Picture {
        int width();
        int height();
        /** Its tones {@code w × h}, row by row: each pixel its footprint's average. */
        byte[] tones(int w, int h);
    }
    /** A page's small picture ({@link PagePicture}) as the cover picture: for a shelf tile. */
    static Picture of(PagePicture picture) {
        return new Picture() {
            @Override public int width() { return picture.width; }
            @Override public int height() { return picture.height; }
            @Override public byte[] tones(int w, int h) { return resampled(picture.width, picture.height, (x, y) -> picture.tone(x, y), w, h); }
        };
    }
    /** A page itself as the cover picture, {@code pageWidth} wide: for the cover being drawn on and the PDF. */
    static Picture of(ToneDocument.Snapshot page) {
        return new Picture() {
            @Override public int width() { return page.width; }
            @Override public int height() { return page.height; }
            @Override public byte[] tones(int w, int h) {
                PagePicture.Composite composite = new PagePicture.Composite(page);
                int[] row = new int[page.width];
                long[] sums = new long[w];
                int[] counts = new int[w], column = new int[page.width];
                for (int x = 0; x < page.width; x++) column[x] = Math.min(w - 1, (int) ((long) x * w / page.width));
                byte[] out = new byte[w * h];
                for (int oy = 0; oy < h; oy++) {
                    int y0 = (int) ((long) oy * page.height / h), y1 = Math.max(y0 + 1, (int) ((long) (oy + 1) * page.height / h));
                    java.util.Arrays.fill(sums, 0); java.util.Arrays.fill(counts, 0);
                    for (int y = y0; y < Math.min(y1, page.height); y++) {
                        composite.row(y, row);
                        for (int x = 0; x < page.width; x++) { sums[column[x]] += row[x]; counts[column[x]]++; }
                    }
                    for (int ox = 0; ox < w; ox++) out[oy * w + ox] = (byte) (counts[ox] == 0 ? 255 : (sums[ox] + counts[ox] / 2) / counts[ox]);
                }
                return out;
            }
        };
    }
    /**
     * {@code picture} (null: none) read once: its tones kept at the size first asked and the page let go, so a cover can
     * make its paper with its design rubbed off later ({@link CoverWear}) without holding the page.
     */
    static Picture once(Picture picture) {
        if (picture == null) return null;
        int pw = picture.width(), ph = picture.height();
        return new Picture() {
            private Picture source = picture;
            private byte[] kept;
            private int kw, kh;
            @Override public int width() { return pw; }
            @Override public int height() { return ph; }
            @Override public synchronized byte[] tones(int w, int h) {
                if (kept == null) { kept = source.tones(w, h); kw = w; kh = h; source = null; }
                if (w == kw && h == kh) return kept;
                byte[] from = kept;
                return resampled(kw, kh, (x, y) -> from[y * kw + x] & 255, w, h);
            }
        };
    }
    /** A source of tones {@code sw × sh} at {@code w × h}: each pixel its footprint's average when smaller, between the nearest four when larger. */
    interface Tones { int at(int x, int y); }
    static byte[] resampled(int sw, int sh, Tones source, int w, int h) {
        byte[] out = new byte[w * h];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int tone;
                if (w <= sw && h <= sh) {
                    int x0 = (int) ((long) x * sw / w), x1 = Math.max(x0 + 1, (int) ((long) (x + 1) * sw / w));
                    int y0 = (int) ((long) y * sh / h), y1 = Math.max(y0 + 1, (int) ((long) (y + 1) * sh / h));
                    int sum = 0, count = 0;
                    for (int sy = y0; sy < y1; sy++) for (int sx = x0; sx < x1; sx++) { sum += source.at(sx, sy); count++; }
                    tone = (sum + count / 2) / count;
                } else {
                    float fx = Math.max(0, (x + .5f) * sw / w - .5f), fy = Math.max(0, (y + .5f) * sh / h - .5f);
                    int ax = Math.min(sw - 1, (int) fx), ay = Math.min(sh - 1, (int) fy), bx = Math.min(sw - 1, ax + 1), by = Math.min(sh - 1, ay + 1);
                    float tx = fx - ax, ty = fy - ay;
                    float top = source.at(ax, ay) * (1 - tx) + source.at(bx, ay) * tx, bottom = source.at(ax, by) * (1 - tx) + source.at(bx, by) * tx;
                    tone = Math.round(top * (1 - ty) + bottom * ty);
                }
                out[y * w + x] = (byte) tone;
            }
        return out;
    }

    /**
     * A cover's paper {@code width × height} px, a tone a pixel, for a notebook named {@code name} (its file's name: the
     * pattern's seed and the label's words) in {@code design}: the back's its sheet alone (shared: don't change it), the
     * front's with, unless {@code picture} is null, the cover picture glued on, then its label.
     */
    static byte[] paper(boolean front, CoverDesign design, String name, int width, int height, Picture picture) { return paper(front, design, name, width, height, picture, false); }
    /**
     * As above, {@code bare}: with the design rubbed off ({@link CoverWear}), white paper under the tape, the picture and
     * the label, which the eraser can't take.
     */
    static byte[] paper(boolean front, CoverDesign design, String name, int width, int height, Picture picture, boolean bare) {
        byte[] sheet;
        if (bare) {
            sheet = new byte[width * height];
            java.util.Arrays.fill(sheet, (byte) CoverPattern.LEVELS[255]);
            CoverPattern.tape(sheet, width, height, !front, width >= CoverPattern.SMALL);
        } else sheet = CoverPattern.cached(design, name, width, height, !front);
        if (!front) return sheet;
        byte[] out = bare ? sheet : sheet.clone();
        if (picture != null) {
            int[] box = pictureBox(width, height, picture.width(), picture.height());
            byte[] tones = picture.tones(box[2], box[3]);
            for (int y = 0; y < box[3]; y++) {
                int to = box[1] + y;
                if (to < 0 || to >= height) continue;
                System.arraycopy(tones, y * box[2], out, to * width + box[0], Math.min(box[2], width - box[0]));
            }
            int rule = Math.max(1, Math.round(width / (float) PICTURE_RULE));
            frame(out, width, height, box[0] - rule, box[1] - rule, box[2] + 2 * rule, box[3] + 2 * rule, rule);
        }
        draw(out, width, height, label(name, width, height));
        return out;
    }
    private static void fill(byte[] tones, int width, int height, int x, int y, int w, int h, int tone) {
        for (int row = Math.max(0, y); row < Math.min(height, y + h); row++)
            java.util.Arrays.fill(tones, row * width + Math.max(0, x), row * width + Math.min(width, x + w), (byte) tone);
    }
    /** An ink rule {@code rule} px thick just inside the box. */
    private static void frame(byte[] tones, int width, int height, int x, int y, int w, int h, int rule) {
        fill(tones, width, height, x, y, w, rule, 0); fill(tones, width, height, x, y + h - rule, w, rule, 0);
        fill(tones, width, height, x, y, rule, h, 0); fill(tones, width, height, x + w - rule, y, rule, h, 0);
    }

    /**
     * A cover as a shelf tile or a thumbnail shows it, {@code width × height} px: its paper made at that size (the cover
     * picture from {@code pictured}'s picture, or none) with {@code marks}, the cover's picture of its marks alone
     * ({@link PagePicture#marksAlone}), laid over it (none for null), its design rubbed off where {@code marks} says.
     */
    static byte[] shown(boolean front, CoverDesign design, String name, int width, int height, PagePicture pictured, PagePicture marks) {
        byte[] paper = paper(front, design, name, width, height, pictured == null ? null : of(pictured));
        if (marks != null && marks.wear() != null)
            paper = CoverWear.apply(paper, paper(front, design, name, width, height, pictured == null ? null : of(pictured), true), marks.wear().scaled(width, height));
        if (marks == null || marks.white()) return paper;
        byte[] out = new byte[width * height];
        int sw = marks.width, sh = marks.height;
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++) {
                int o = y * width + x, ground = paper[o] & 255, tone;
                if (width <= sw && height <= sh) {
                    int x0 = (int) ((long) x * sw / width), x1 = Math.max(x0 + 1, (int) ((long) (x + 1) * sw / width));
                    int y0 = (int) ((long) y * sh / height), y1 = Math.max(y0 + 1, (int) ((long) (y + 1) * sh / height));
                    int sum = 0, count = 0;
                    for (int sy = y0; sy < y1; sy++) for (int sx = x0; sx < x1; sx++) { sum += marks.toneOn(sx, sy, ground); count++; }
                    tone = (sum + count / 2) / count;
                } else {
                    float fx = Math.max(0, (x + .5f) * sw / width - .5f), fy = Math.max(0, (y + .5f) * sh / height - .5f);
                    int ax = Math.min(sw - 1, (int) fx), ay = Math.min(sh - 1, (int) fy), bx = Math.min(sw - 1, ax + 1), by = Math.min(sh - 1, ay + 1);
                    float tx = fx - ax, ty = fy - ay;
                    float top = marks.toneOn(ax, ay, ground) * (1 - tx) + marks.toneOn(bx, ay, ground) * tx;
                    float bottom = marks.toneOn(ax, by, ground) * (1 - tx) + marks.toneOn(bx, by, ground) * tx;
                    tone = Math.round(top * (1 - ty) + bottom * ty);
                }
                out[o] = (byte) tone;
            }
        return out;
    }

    /**
     * What {@link #shown} makes is made from, as a key for the shelf's covers kept as shown: the side, design, size and
     * name, and the pictures by what they hold ({@link #print}), so the same covers read again from their files (new
     * arrays, the same bytes) are found, not made again.
     */
    static String shownKey(boolean front, CoverDesign design, String name, int width, int height, byte[] pictured, byte[] marks) {
        return (front ? "front " : "back ") + design.key + " " + width + "x" + height + " " + Long.toHexString(print(pictured)) + " " + Long.toHexString(print(marks)) + "\n" + name;
    }
    /** Pictures' prints, by the array (a byte array's hash and equality are its identity), let go with it. */
    private static final java.util.Map<byte[], Long> PRINTS = new java.util.WeakHashMap<>();
    /** What {@code bytes} hold, as a number (its length and CRC-32; 0 for none), worked out once an array. */
    static long print(byte[] bytes) {
        if (bytes == null) return 0;
        synchronized (PRINTS) {
            Long known = PRINTS.get(bytes);
            if (known != null) return known;
            CRC32 c = new CRC32(); c.update(bytes);
            long print = (long) bytes.length << 32 | c.getValue();
            PRINTS.put(bytes, print);
            return print;
        }
    }

    /**
     * What a cover's PNG copy is drawn from, as a number its PNG carries in place of a page's CRC: the pattern's version,
     * the design, the name, the size, the cover's own page (its CRC, 0 when nothing is drawn) and the cover picture's
     * page (its CRC, 0 for none). Any change, and the PNG is drawn again.
     */
    static long key(boolean front, CoverDesign design, String name, int width, int height, long coverCrc, long pictureCrc) {
        CRC32 c = new CRC32();
        String all = (front ? "front" : "back") + "\n" + CoverPattern.VERSION + "\n" + design.key + "\n" + name + "\n" + width + "x" + height
                + "\n" + Long.toHexString(coverCrc) + "\n" + (front ? Long.toHexString(pictureCrc) : "");
        c.update(all.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return c.getValue();
    }

    private NotebookCovers() {}
}
