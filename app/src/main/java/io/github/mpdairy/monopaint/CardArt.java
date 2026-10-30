package io.github.mpdairy.monopaint;

import java.util.List;

/**
 * The look of a notebook settings' screens ({@link CardSheet}; the list itself is {@link LibraryCard}), in Scribbler
 * Style (the design pass, 2026-10-23: "shade before line"): each choice a grey-200 tile, a picture of a page on paper in
 * it or its icon, name and note, no frame, the one in use marked by a {@link #TAB} dp ink tab at its foot alone (no fill,
 * no ✓). Words in iA Writer Duo, a name wrapping to two lines rather than cut.
 *
 * <p>Everything is drawn through a {@link Pen} in dp, so the same drawing goes to the screen ({@code CanvasPen}) and,
 * for a look on the desktop, anywhere else. Text is measured here, as {@link LabText} measures the face, so a layout
 * is pure and checked on the desktop ({@code LibraryCardChecks}).
 */
final class CardArt {
    /** Theme's greys ({@code Theme}): paper, the faintest tint, disabled lines, a mid grey, secondary text, ink. */
    static final int PAPER = 0xffffffff, GREY_200 = 0xffdddddd, GREY_400 = 0xffbbbbbb, GREY_600 = 0xff888888, GREY_800 = 0xff555555, INK = 0xff000000;
    /** Strokes, dp, as Theme's: fine, base, rule, heavy. */
    static final float FINE = 1, BASE = 2, RULE = 3, HEAVY = 4;
    /** The least text anywhere on the settings screens, sp. */
    static final float LEAST_SP = 12;

    /** The faces: iA Writer Duo, regular and bold, and a head (bold, tracked caps). */
    enum Face {
        DUO(false, 0), DUO_BOLD(true, 0), LABEL(true, .08f);
        final boolean bold; final float tracking;
        Face(boolean bold, float tracking) { this.bold = bold; this.tracking = tracking; }
    }
    /** The ink tab under the choice in use, dp tall, and its inset from the tile's sides: a swatch's ({@code SwatchFace}). */
    static final float TAB = 5, TAB_INSET = 3;
    /** The icons drawn (Phosphor, Bold as the app's {@code ic_ph_*}, Fill and Light where a step's eye is). */
    enum Icon { DICE, EYE, EYE_CLOSED, EYE_HALF, EYE_FAINT }

    /** Where the drawing goes, in dp. Colours are ARGB. */
    interface Pen {
        void fill(float l, float t, float r, float b, int color);
        /** {@code text} from {@code x} along {@code baseline}, {@code sp} high. */
        void text(String text, float x, float baseline, float sp, Face face, int color);
        void icon(Icon icon, float l, float t, float size, int color);
        /** A picture made for this pen ({@code null}: not yet) fitted into the box. */
        void picture(Object picture, float l, float t, float r, float b);
    }

    // Measures

    /**
     * A face's width at {@code sp}, dp (duospace: an m or a w one and a half, LabText's), with Ukulele's own room
     * ({@link LabText#UKULELE}), so a word set flush to an edge stays inside it (the owner, 2026-10-27: "text is cut off").
     */
    static float width(String text, float sp, Face face) {
        return LabText.width(face == Face.LABEL ? text.toUpperCase(java.util.Locale.ROOT) : text, sp, face.tracking + LabText.UKULELE);
    }
    /** {@code sp} text's height on screen and its line, dp. */
    static float em(float sp) { return LabText.em(sp); }
    static float line(float sp) { return LabText.line(sp); }
    /** The baseline that sets {@code sp} text in the middle of a line from {@code top} to {@code bottom}. */
    static float middle(float top, float bottom, float sp) { return (top + bottom) / 2 + em(sp) * .35f; }
    /** {@code text} in one line of {@code room}, cut with "…" where it would pass it. */
    static String fit(String text, float room, float sp, Face face) { return LabText.oneLine(text, room, t -> width(t, sp, face)); }
    /**
     * {@code text} wrapped to {@code room}, at most {@code lines} lines, the last cut with "…". A word wider than the room
     * with nowhere to break (an imported template's file name, 20201031191227_isometric) is broken where the line ends,
     * so it takes two lines rather than being cut at once.
     */
    static List<String> wrap(String text, float room, float sp, Face face, int lines) {
        List<String> wrapped = new java.util.ArrayList<>();
        for (String line : LabText.wrap(text, room, t -> width(t, sp, face))) {
            while (line.length() > 1 && width(line, sp, face) > room) {
                int n = line.length() - 1;
                while (n > 1 && width(line.substring(0, n), sp, face) > room) n--;
                wrapped.add(line.substring(0, n));
                line = line.substring(n);
            }
            wrapped.add(line);
        }
        if (wrapped.size() <= lines) return wrapped;
        List<String> kept = new java.util.ArrayList<>(wrapped.subList(0, lines));
        StringBuilder rest = new StringBuilder();
        for (int i = lines - 1; i < wrapped.size(); i++) rest.append(i > lines - 1 ? " " : "").append(wrapped.get(i));
        kept.set(lines - 1, fit(rest.toString(), room, sp, face));
        return kept;
    }
    /** {@code text} as drawn: a printed head in caps. */
    static String shown(String text, Face face) { return face == Face.LABEL ? text.toUpperCase(java.util.Locale.ROOT) : text; }

    // Pieces

    /**
     * A choice on a settings screen ({@link CardSheet}), a grey-200 tile: its icon (null: none) and name, its note under
     * them in up to {@code noteLines} lines; or, {@code pictured}, its picture on paper on top (a page's shape, its guides
     * or design; {@code icon} over a blank one) and its name under it in up to two lines. In use: the {@link #TAB} dp ink
     * tab at its foot, that one mark alone (the owner, 2026-10-23: no fill and ✓ both). Not {@code enabled}: its words grey.
     */
    static void choice(Pen pen, float w, float h, String name, String note, Icon icon, boolean pictured, Object picture, double aspect,
                       boolean on, boolean enabled, Choice look) {
        int words = enabled ? INK : GREY_600, quiet = enabled ? GREY_800 : GREY_600;
        pen.fill(0, 0, w, h, GREY_200);
        if (on) pen.fill(TAB_INSET, h - TAB, w - TAB_INSET, h, INK);
        float pad = look.pad;
        if (pictured) {
            float[] box = pictureBox(w, h, aspect, look);
            float pl = box[0], pt = box[1], pw = box[2] - box[0], ph = box[3] - box[1];
            pen.fill(pl, pt, pl + pw, pt + ph, PAPER);
            pen.picture(picture, pl, pt, pl + pw, pt + ph);
            if (icon != null) { float s = Math.min(48, Math.min(pw, ph) / 2); pen.icon(icon, pl + (pw - s) / 2, pt + (ph - s) / 2, s, words); }
            List<String> lines = wrap(name, w - 2 * pad, look.nameSp, Face.DUO_BOLD, look.nameLines);
            float y = pt + ph + pad / 2;
            for (String s : lines) { pen.text(s, (w - width(s, look.nameSp, Face.DUO_BOLD)) / 2, middle(y, y + line(look.nameSp), look.nameSp), look.nameSp, Face.DUO_BOLD, words); y += line(look.nameSp); }
            return;
        }
        float x = pad;
        float iconSize = look.icon;
        float textRoom = w - 2 * pad;
        if (icon != null) {
            pen.icon(icon, x, (h - iconSize) / 2, iconSize, words);
            x += iconSize + pad;
            textRoom -= iconSize + pad;
        }
        List<String> noteLines = note == null ? java.util.Collections.<String>emptyList() : wrap(note, textRoom, look.noteSp, Face.DUO, look.noteLines);
        float block = line(look.nameSp) + noteLines.size() * line(look.noteSp), y = (h - block) / 2;
        pen.text(fit(name, textRoom, look.nameSp, Face.DUO_BOLD), x, middle(y, y + line(look.nameSp), look.nameSp), look.nameSp, Face.DUO_BOLD, words);
        y += line(look.nameSp);
        for (String s : noteLines) { pen.text(s, x, middle(y, y + line(look.noteSp), look.noteSp), look.noteSp, Face.DUO, quiet); y += line(look.noteSp); }
    }
    /** A pictured choice's picture box in its {@code w × h}: a page's shape, as tall as its name's lines leave room for. */
    static float[] pictureBox(float w, float h, double aspect, Choice look) {
        float ph = Math.max(0, h - look.picturedWords()), pw = (float) (ph / aspect);
        if (pw > w - 2 * look.pad) { pw = w - 2 * look.pad; ph = (float) (pw * aspect); }
        float pl = (w - pw) / 2;
        return new float[]{pl, look.pad, pl + pw, look.pad + ph};
    }
    /** A choice's sizes: its padding, its name's size and lines, its note's size and lines, its icon's size. */
    static final class Choice {
        final float pad, nameSp, noteSp, icon;
        final int nameLines, noteLines;
        Choice(float pad, float nameSp, int nameLines, float noteSp, int noteLines, float icon) {
            this.pad = pad; this.nameSp = nameSp; this.nameLines = nameLines; this.noteSp = noteSp; this.noteLines = noteLines; this.icon = icon;
        }
        /** A choice's least height for its name and {@code noteLines} lines of note (pictured: under a picture of {@code picture} dp). */
        float height(int noteLines) { return 2 * pad + line(nameSp) + noteLines * line(noteSp); }
        float picturedWords() { return 2 * pad + nameLines * line(nameSp) + pad / 2; }
    }

    /**
     * An ordered choice's slider ({@link CardSheet}): its {@code head} in tracked caps, grey-800 as every section header (null:
     * none), its choice in words, big, over a heavy track with a tick at each stop ({@code marks} longer and named under it), and a block
     * of ink for the thumb at stop {@code at} of {@code stops}.
     */
    static void slider(Pen pen, float w, String head, String value, int at, int stops, int[] marks, String[] markWords, Slider look) {
        boolean headed = head != null;
        if (headed) pen.text(shown(head, Face.LABEL), 0, em(look.markSp), look.markSp, Face.LABEL, GREY_800);
        float top = look.top(headed);
        pen.text(value, (w - width(value, look.valueSp, Face.DUO_BOLD)) / 2, top + em(look.valueSp), look.valueSp, Face.DUO_BOLD, INK);
        float ty = look.trackY(headed), l = look.thumb / 2, r = w - look.thumb / 2;
        pen.fill(l, ty - RULE / 2, r, ty + RULE / 2, INK);
        for (int i = 0; i < stops; i++) {
            boolean marked = false;
            for (int m : marks) marked |= m == i;
            float x = x(i, stops, l, r), tick = marked ? look.tick : look.tick / 2;
            pen.fill(x - FINE, ty + RULE / 2, x + FINE, ty + RULE / 2 + tick, marked ? INK : GREY_600);
        }
        for (int k = 0; k < marks.length; k++) {
            float x = x(marks[k], stops, l, r), mw = width(markWords[k], look.markSp, Face.DUO);
            float mx = Math.max(0, Math.min(w - mw, x - mw / 2));
            pen.text(markWords[k], mx, ty + look.thumbHeight / 2 + 6 + em(look.markSp), look.markSp, Face.DUO, GREY_800);
        }
        float tx = x(at, stops, l, r);
        pen.fill(tx - look.thumb / 2, ty - look.thumbHeight / 2, tx + look.thumb / 2, ty + look.thumbHeight / 2, INK);
        pen.fill(tx - FINE, ty - look.thumbHeight / 2 + 8, tx + FINE, ty + look.thumbHeight / 2 - 8, PAPER);
    }
    /** A row of chips under a tracked-caps {@code head} (Template Visibility): the one at {@code at} ink-filled, the rest grey-200, as wide as the row shares. */
    static final float CHIP_HEIGHT = 48, CHIP_GAP = 8;
    static float chipsHeight(float headSp) { return line(headSp) * 1.2f + CHIP_HEIGHT; }
    static void chips(Pen pen, float w, String head, String[] words, int at, float headSp) {
        pen.text(shown(head, Face.LABEL), 0, em(headSp), headSp, Face.LABEL, GREY_800);
        float top = line(headSp) * 1.2f, cw = (w - (words.length - 1) * CHIP_GAP) / words.length;
        for (int i = 0; i < words.length; i++) {
            float l = i * (cw + CHIP_GAP);
            pen.fill(l, top, l + cw, top + CHIP_HEIGHT, i == at ? INK : GREY_200);
            pen.text(words[i], l + (cw - width(words[i], 15, Face.DUO_BOLD)) / 2, middle(top, top + CHIP_HEIGHT, 15), 15, Face.DUO_BOLD, i == at ? PAPER : INK);
        }
    }
    /** The chip at {@code x}, {@code y} in a row {@code w} wide of {@code count} chips (−1: none, e.g. over the head). */
    static int chipAt(float x, float w, float y, float headSp, int count) {
        float cw = (w - (count - 1) * CHIP_GAP) / count, top = line(headSp) * 1.2f;
        if (y < top || y > top + CHIP_HEIGHT) return -1;
        int i = (int) (x / (cw + CHIP_GAP));
        return i >= 0 && i < count && x - i * (cw + CHIP_GAP) <= cw ? i : -1;
    }
    /** Where stop {@code i} of {@code stops} is between {@code l} and {@code r}. */
    static float x(int i, int stops, float l, float r) { return stops <= 1 ? l : l + (r - l) * i / (stops - 1); }
    /** The stop at {@code x} on a slider {@code w} wide. */
    static int stopAt(float x, float w, int stops, Slider look) {
        float l = look.thumb / 2, r = w - look.thumb / 2;
        return Math.max(0, Math.min(stops - 1, Math.round((x - l) / (r - l) * (stops - 1))));
    }
    /** A slider's sizes: its value's and its marks' (and head's), its thumb's, its ticks'. */
    static final class Slider {
        final float valueSp, markSp, thumb = 28, thumbHeight = 48, tick = 12;
        Slider(float valueSp, float markSp) { this.valueSp = valueSp; this.markSp = markSp; }
        float top(boolean headed) { return headed ? line(markSp) * 1.2f : 0; }
        float trackY(boolean headed) { return top(headed) + em(valueSp) * 1.25f + thumbHeight / 2 + 8; }
        float height(boolean headed) { return trackY(headed) + thumbHeight / 2 + 6 + line(markSp); }
    }

    private CardArt() {}
}
