package io.github.mpdairy.monopaint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Brush Lab's text, wrapped and measured: word wrapping at spaces (what the lab's TextViews do, with simple breaks and
 * no hyphens: {@link Theme#style}), and a card's caption fitted to its card ({@link #caption}, what {@link LoopCard}
 * draws). On the desktop it measures as the device draws the lab: iA Writer Duo ({@link Theme#face}) at the app's 1.2×
 * font scale ({@link Ui#largerText}). The face is duospace (its hmtx, 1000 units an em, the same in 400 and 700): a
 * character 0.6 em wide, the few {@link #WIDE_SET} ("m", "w", "M", "W", "%", "@", "—", "…" and the like) 0.9 em; a
 * sign the face lacks ("▾", "✕") a whole em, as it comes from a fallback face; a zero-width space nothing. A line is
 * 1.3 em tall (its hhea ascent and descent, 1025 + 275). Pure, checked on the desktop ({@code LabLayoutChecks}).
 */
final class LabText {
    /**
     * The app's font scale; in em, a character's width, a wide one's ({@link #WIDE_SET}), a sign's the face lacks, and
     * a line's height.
     */
    static final float SCALE = 1.2f, ADVANCE = .6f, WIDE = .9f, MISSING = 1f, LINE = 1.3f;
    /**
     * The room, em a character, Ukulele (the face since 2026-10-26, scripts/ukulele_font.py) runs past this duospace count:
     * its 0.07 em letter spacing (Ben-Day brush 8.44 em to the count's 8.1; DrawLayout.PICKER_SPACING is the same).
     */
    static final float UKULELE = .04f;
    /** The characters the face draws one and a half wide (of those past Latin-1, the ones the app might show). */
    static final String WIDE_SET = "%@MWmw©ÆæŒœŴŵǼǽ—…ψωφШЩЖЮЫМДФшщжюыф";
    /** The signs past Latin Extended-A the face has (at {@link #ADVANCE}, but those in {@link #WIDE_SET}); any other is {@link #MISSING}. */
    static final String SIGNS = "–‘’‚“”„•‹›€™←↑→↓↔−√∞≈≠≤≥✓σΔπµ⁰¹²³⁴⁵⁶⁷⁸⁹";
    /** A caption's size, sp: the smallest text anywhere ({@link TypeStyle#CAPTION}), never smaller. */
    static final float CAPTION_SP = 13;
    /**
     * Where a word too wide for its line may break into two words, the second capitalised as a label's ("Screentone":
     * "Screen" / "Tone", the owner's short form); shown as nothing elsewhere ({@link #wrap}).
     */
    static final char SOFT_BREAK = '­';
    /**
     * A card's caption, dp: its inset either side (just clear of a 4 dp selected outline); its 2 dp rule, the gap over its
     * lines and the room under them; the least of the sample above it, and the shares of the card the sample keeps: as
     * it would like, and at least.
     */
    static final float CAPTION_INSET = 5, CAPTION_CHROME = 2 + 4 + 6, SAMPLE_MIN = 28, SAMPLE_SHARE = .45f, SAMPLE_LEAST = .3f;

    private LabText() { }

    /** How wide some text is, in the units its caller works in (dp on the desktop, pixels on the device). */
    interface Measure { float width(String text); }
    /** A measure for each size, and its line height, for {@link #caption}. */
    interface Sized { Measure at(float sp); float line(float sp); }

    /** {@code sp} in dp, at the app's font scale. */
    static float em(float sp) { return sp * SCALE; }
    /** A character's width, em. */
    static float advance(char c) {
        if (c == '\u200b') return 0;
        if (WIDE_SET.indexOf(c) >= 0) return WIDE;
        return c < 0x180 || SIGNS.indexOf(c) >= 0 ? ADVANCE : MISSING;
    }
    /** The face at {@code sp} with {@code spacing} em of letter spacing, in dp. */
    static Measure face(float sp, float spacing) {
        return text -> {
            float ems = 0;
            for (int i = 0; i < text.length(); i++) ems += advance(text.charAt(i)) + spacing;
            return ems * em(sp);
        };
    }
    static float width(String text, float sp, float spacing) { return face(sp, spacing).width(text); }
    /** A line of {@code sp} text, dp. */
    static float line(float sp) { return em(sp) * LINE; }
    /** {@link #caption}'s sizes on the desktop, dp. */
    static final Sized FACE = new Sized() {
        @Override public Measure at(float sp) { return face(sp, 0); }
        @Override public float line(float sp) { return LabText.line(sp); }
    };

    /** A card's caption fitted to a card {@code width × height}, dp, as the device would draw it, at 13 sp. */
    static Caption caption(List<String> items, String name, float width, float height) { return caption(items, name, width, height, CAPTION_SP); }
    /** The same, smaller down to {@code least} sp where it must be ({@link LabLayout#swapLeast}). */
    static Caption caption(List<String> items, String name, float width, float height, float least) {
        return caption(items, name, width, height, least, CAPTION_INSET);
    }
    /** As {@link #caption(List, String, float, float, float)}, inset {@code inset} dp either side ({@link LabLayout#swapInset}). */
    static Caption caption(List<String> items, String name, float width, float height, float least, float inset) {
        return caption(items, name, width - 2 * inset, height, 1, least, FACE);
    }

    /**
     * {@code text} in lines no wider than {@code room}, broken at spaces, at a {@link #SOFT_BREAK} and after a hyphen
     * inside a word wider than {@code room} ("Multi-" / "line"); a word still wider keeps a line of its own.
     */
    static List<String> wrap(String text, float room, Measure measure) {
        List<String> lines = new ArrayList<>(), words = new ArrayList<>();
        for (String word : text.trim().split(" +")) {
            // A soft break marks where a word too wide may break into two ("Screen" / "Tone"); elsewhere it shows nothing.
            if (word.indexOf(SOFT_BREAK) >= 0) {
                String mark = String.valueOf(SOFT_BREAK), whole = word.replace(mark, "");
                int cut = -1;
                if (measure.width(whole) > room)
                    for (int i = word.indexOf(SOFT_BREAK); i > 0; i = word.indexOf(SOFT_BREAK, i + 1))
                        if (cut < 0 || measure.width(word.substring(0, i).replace(mark, "")) <= room) cut = i;
                if (cut < 0) word = whole;
                else {
                    words.add(word.substring(0, cut).replace(mark, ""));
                    String rest = word.substring(cut + 1).replace(mark, "");
                    word = rest.isEmpty() ? rest : Character.toUpperCase(rest.charAt(0)) + rest.substring(1);
                }
            }
            while (measure.width(word) > room) {
                int cut = -1;
                for (int i = word.indexOf('-', 1); i > 0 && i < word.length() - 1; i = word.indexOf('-', i + 1))
                    if (cut < 0 || measure.width(word.substring(0, i + 1)) <= room) cut = i;
                if (cut < 0) break;
                words.add(word.substring(0, cut + 1));
                word = word.substring(cut + 1);
            }
            words.add(word);
        }
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (line.length() > 0 && measure.width(line + " " + word) <= room) { line.append(' ').append(word); continue; }
            if (line.length() > 0) lines.add(line.toString());
            line.setLength(0);
            line.append(word);
        }
        if (line.length() > 0) lines.add(line.toString());
        return lines;
    }
    /**
     * A caption line ("Watercolour, slow") as a card shows it: whole if it fits; else broken between its items, the
     * comma left at the break as {@link BrushCaptions#twoLines} leaves it between lines ("Watercolour" / "slow"), the
     * items that fit a line kept together; then each wrapped at spaces ({@link #wrap}).
     */
    static List<String> wrapItem(String item, float room, Measure measure) {
        if (measure.width(item) <= room || !item.contains(", ")) return wrap(item, room, measure);
        List<String> lines = new ArrayList<>();
        String line = "";
        for (String piece : item.split(", ")) {
            if (!line.isEmpty() && measure.width(line + ", " + piece) <= room) { line += ", " + piece; continue; }
            if (!line.isEmpty()) lines.addAll(wrap(line, room, measure));
            line = piece;
        }
        if (!line.isEmpty()) lines.addAll(wrap(line, room, measure));
        return lines;
    }
    /** Whether {@code text} wraps to at most {@code lines} lines in {@code room}, no word wider than it. */
    static boolean fits(String text, float room, Measure measure, int lines) {
        List<String> wrapped = wrap(text, room, measure);
        for (String line : wrapped) if (measure.width(line) > room) return false;
        return wrapped.size() <= lines;
    }
    /** {@code text} on one line of {@code room}: whole words while they fit, then "…"; cut inside a word only when its first is too wide. */
    static String oneLine(String text, float room, Measure measure) {
        if (measure.width(text) <= room) return text;
        String cut = text;
        while (true) {
            int space = Math.max(cut.lastIndexOf(' '), cut.lastIndexOf('-'));
            if (space <= 0) break;
            cut = cut.substring(0, space);
            if (measure.width(cut + "…") <= room) return cut + "…";
        }
        for (int n = text.length() - 1; n > 0; n--) if (measure.width(text.substring(0, n) + "…") <= room) return text.substring(0, n) + "…";
        return "";
    }

    /** A card's caption as fitted: its size, the name's line or null (and its second line or null), then its lines (in grey-800). */
    static final class Caption {
        static final Caption NONE = new Caption(CAPTION_SP, Collections.emptyList(), null, true);
        final float sp;
        final List<String> lines;
        final String name, name2;
        /** Whether every item fits whole; false only when the card is too small for them even over the least sample. */
        final boolean whole;
        Caption(float sp, List<String> lines, String name, boolean whole) { this(sp, lines, name, null, whole); }
        Caption(float sp, List<String> lines, String name, String name2, boolean whole) { this.sp = sp; this.lines = lines; this.name = name; this.name2 = name2; this.whole = whole; }
        int count() { return lines.size() + (name != null ? 1 : 0) + (name2 != null ? 1 : 0); }
    }
    /** A brush's name on at most {@code most} lines of {@code room} (the owner, 2026-10-22: long names wrap to two lines), the last cut with … only past them. */
    static List<String> nameLines(String name, float room, Measure measure, int most) {
        List<String> lines = new ArrayList<>();
        for (String l : wrap(name, room, measure)) lines.add(l);
        if (lines.size() <= most) { List<String> out = new ArrayList<>(); for (String l : lines) out.add(oneLine(l, room, measure)); return out; }
        List<String> out = new ArrayList<>();
        for (int i = 0; i < most - 1; i++) out.add(oneLine(lines.get(i), room, measure));
        String rest = String.join(" ", lines.subList(most - 1, lines.size())), cut = oneLine(rest, room, measure);
        out.add(cut.endsWith("…") ? cut : oneLine(rest + "…", room, measure));
        return out;
    }
    /**
     * {@code name} (if not null; up to two lines, then one cut at a word) over {@code items} as a card {@code room} wide (inside its
     * insets) and {@code height} tall shows them, with {@code dp} units a dp: each item wrapped, never cut, at 13 sp
     * (or the largest size down to {@code least} where they fit) over a sample of at least 45% of the card, failing
     * that 30%; failing that, a whole name on two lines with no caption under it, then the name on one line, then
     * left out; failing that, only the first item (a swap tile's
     * choice without its note: the Nomad's narrow Control tiles). Only a card too small even then loses its last lines.
     */
    static Caption caption(List<String> items, String name, float room, float height, float dp, float least, Sized sized) {
        if (items.isEmpty() && name == null) return Caption.NONE;
        float most = height;
        // Tries, best first: {items kept, name lines}. A whole name over no caption beats a cut one over it (the owner, 2026-10-22: "i love the names").
        int all = items.size(), one = Math.min(1, all);
        int[][] tries = name == null ? new int[][]{{all, 0}, {one, 0}}
                : new int[][]{{all, 2}, {0, 2}, {all, 1}, {all, 0}, {one, 2}, {one, 1}, {one, 0}};
        for (int[] attempt : tries) {
            int kept = attempt[0], named = attempt[1];
            // A name over no caption only where it takes two lines and shows whole: a short one gives way to the caption as before.
            if (kept == 0) {
                List<String> two = nameLines(name, room, sized.at(least), 2);
                if (two.size() < 2 || two.get(1).endsWith("…")) continue;
            }
            for (float share : new float[]{SAMPLE_SHARE, SAMPLE_LEAST}) {
                most = height - Math.max(SAMPLE_MIN * dp, height * share) - CAPTION_CHROME * dp;
                for (float sp = CAPTION_SP; sp >= least; sp--) {
                    Measure measure = sized.at(sp);
                    List<String> lines = new ArrayList<>();
                    boolean wide = false;
                    for (String item : items.subList(0, kept)) for (String l : wrapItem(item, room, measure)) { lines.add(l); wide |= measure.width(l) > room; }
                    List<String> names = named == 0 ? Collections.<String>emptyList() : nameLines(name, room, measure, named);
                    if (!wide && (lines.size() + names.size()) * sized.line(sp) <= most)
                        return new Caption(sp, lines, names.isEmpty() ? null : names.get(0), names.size() > 1 ? names.get(1) : null, true);
                }
            }
        }
        // Too small for them all: the least size, whole lines while they fit.
        Measure measure = sized.at(least);
        List<String> fitted = new ArrayList<>();
        int fit = (int) Math.floor(most / sized.line(least));
        for (String item : items) for (String l : wrapItem(item, room, measure)) if (fitted.size() < fit) fitted.add(oneLine(l, room, measure));
        return new Caption(least, fitted, null, false);
    }
}
