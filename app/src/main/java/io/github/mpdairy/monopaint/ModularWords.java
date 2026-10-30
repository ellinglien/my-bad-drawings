package io.github.mpdairy.monopaint;

import static io.github.mpdairy.monopaint.PartDial.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * A modular brush in words (parts.js 463–537, reworded to docs/TEXT-STYLE.md): choice words, the Control summary,
 * caption items and "This brush". Painter's words, named rather than explained; only what is unusual for the brush.
 * "This brush" leaves out what the engine doesn't draw yet ({@link ModularFeatures}). Pure, checked on the desktop.
 */
final class ModularWords {
    private ModularWords() { }
    /** "This brush" at most, characters ({@link #plainWords}): what Brush Lab's column budgets. */
    static final int MOST = 260;
    /** A Stamp in "This brush": what its nib is, and, with Ink Fade on, that it runs dry. */
    static final String STAMPS = "Stamps a shape cut from the page.", FADES = "Fades as it prints.";
    /** A Crayon in "This brush", after what it is: its wax builds up and sheds washes (with Wax off, only builds up); its Scratch scrapes. */
    static final String BUILDS = "Builds up, resists washes.", BUILDS_ONLY = "Builds up.", SCRAPES = "Scrapes back to white.";
    /** A Spiro in "This brush": along the line, or round its ring. */
    /** Hatching's sentence: how its strokes build. */
    static final String HATCHES = "Crosses where gone over or held.";
    static final String SPIRO_LINE = "Loops along the line.", SPIRO_RING = "Loops round a ring pulled out from the start.",
            SPIRO_OVAL = "Loops round an oval drawn first.", SPIRO_BAR = "Loops round a bar drawn first.";

    /** A mark and how strongly it shows, for "This brush". */
    private static final class Mark {
        final double strength;
        final String words;
        Mark(double strength, String words) { this.strength = strength; this.words = words; }
    }
    /** A Control cell in words: what drives it ("pressure") and what it does ("widens"). */
    private static final class Response {
        final double strength;
        final String cause, effect;
        Response(double strength, String cause, String effect) { this.strength = strength; this.cause = cause; this.effect = effect; }
    }

    /**
     * parts.js handWords (466–472): the {@code n} strongest of the Control cells in use (past .15, after the
     * response), strongest first, as "pressure widens"; one cause doing two things, "tilt widens and lightens"; two
     * causes doing one thing, "pressure and tilt widen". "steady" when none is. A screened brush ({@link #dots}) prints its
     * darkness as dots: "pressure grows dots", "tilt shrinks dots".
     */
    static String handWords(ModularBrush b, int n) {
        ModularBrush.Part hand = b.part(BrushPart.HAND);
        PartChoice tip = b.choice(BrushPart.TIP);
        if (tip == PartChoice.SPIRO) return driveWords(b, n);
        boolean dots = dots(b);
        String darkens = dots ? "grows dots" : "darkens", lightens = dots ? "shrinks dots" : "lightens";
        String[][] words = {{"pressure", "widens"}, {"pressure", darkens},
                {"pressure", tip == PartChoice.SPLIT_NIB ? "opens tines" : tip == PartChoice.MULTI_LINE ? "spreads lines" : "widens nib"},
                {"tilt", (int) hand.get(TILT_RANGE) == 1 ? "broadens" : "widens"}, {"tilt", lightens}, {"tilt", "turns nib"},
                {"speed", (int) hand.get(DIR) != 0 ? "thickens" : "thins"}, {"speed", lightens}, {"speed", "turns nib"},
                {"downhill", "widens"}, {"uphill", lightens}, {"downhill", "opens nib"}};
        List<Response> on = new ArrayList<>();
        for (int i = 0; i < GRID_CELLS.length; i++) {
            double v = b.handCell(GRID_CELLS[i]);
            if (v > .15) on.add(new Response(v, words[i][0], words[i][1]));
        }
        if (on.isEmpty()) return "steady";
        Collections.sort(on, new Comparator<Response>() {
            @Override public int compare(Response x, Response y) { return Double.compare(y.strength, x.strength); }
        });
        Response a = on.get(0);
        if (n < 2 || on.size() < 2) return a.cause + " " + a.effect;
        Response c = on.get(1);
        if (a.cause.equals(c.cause))
            return verb(a.effect).equals(verb(c.effect)) ? a.cause + " " + a.effect : a.cause + " " + a.effect + " and " + c.effect;
        if (a.effect.equals(c.effect)) return a.cause + " and " + c.cause + " " + plural(a.effect);
        return a.cause + " " + a.effect + ", " + c.cause + " " + c.effect;
    }
    /**
     * A Spiro's Control in words (newmods.js driveWords): what pressure, speed and tilt set of its shape, the first
     * {@code n} that do something for its curve, "pressure sets size, tilt sets hole"; two setting one thing, "pressure
     * and tilt set size". "steady" when none does.
     */
    static String driveWords(ModularBrush b, int n) {
        ModularBrush.Part hand = b.part(BrushPart.HAND), tip = b.part(BrushPart.TIP);
        String[] causes = {"pressure", "speed", "tilt"};
        List<String[]> on = new ArrayList<>();
        for (int i = 0; i < DRIVES.length; i++) {
            int what = (int) hand.get(DRIVES[i]);
            if (SpiroStamper.acts(tip, what)) on.add(new String[]{causes[i], lower(DRIVES[i].choices[what])});
        }
        if (on.isEmpty()) return "steady";
        String[] a = on.get(0);
        if (n < 2 || on.size() < 2) return a[0] + " sets " + a[1];
        String[] c = on.get(1);
        if (a[1].equals(c[1])) return a[0] + " and " + c[0] + " set " + a[1];
        return a[0] + " sets " + a[1] + ", " + c[0] + " sets " + c[1];
    }
    /**
     * Whether {@code b}'s mark reaches the page as dots ({@link PrintScreen}: Print shading, or Screentone), so Control's
     * darkness cells (the grid's Dots column, {@link #darkHead}) size its dots; not on water, where a Print shading does nothing.
     */
    static boolean dots(ModularBrush b) {
        ModularBrush.Part m = b.part(BrushPart.MEDIUM);
        return m.choice == PartChoice.TONE || (int) m.get(SCREEN, 0) > 0 && b.choice(BrushPart.LIFE) != PartChoice.FLOAT;
    }
    /** The Control grid's darkness column head: "Dots" for a brush that prints dots ({@link #dots}), else "Dark". */
    static String darkHead(ModularBrush b) { return dots(b) ? "Dots" : "Dark"; }
    private static String verb(String effect) { int space = effect.indexOf(' '); return space < 0 ? effect : effect.substring(0, space); }
    /** "widens" → "widen", "opens tines" → "open tines": for two causes. */
    private static String plural(String effect) { String v = verb(effect); return v.substring(0, v.length() - 1) + effect.substring(v.length()); }

    /**
     * What each part holds, as its module in the lab says it under the part's name (owner's B5 design): fixed per part,
     * whatever the choice. One table, so LabLayoutChecks fits every line.
     */
    private static final String[][] INSIDE = new String[BrushPart.values().length][];
    static {
        // Flick and Fling (the mischief modules spec, Stage 2) are the Tip's and the Medium's too.
        INSIDE[BrushPart.TIP.ordinal()] = new String[]{"shape", "taper", "angle", "flick"};
        INSIDE[BrushPart.MEDIUM.ordinal()] = new String[]{"ink", "paint", "charcoal", "pencil", "fling"};
        // Paint Load (the oil paint spec) is the Smudge's too.
        INSIDE[BrushPart.CARRY.ordinal()] = new String[]{"lift", "drag", "smear", "load"};
        INSIDE[BrushPart.PAPER.ordinal()] = new String[]{"texture"};
        INSIDE[BrushPart.LIFE.ordinal()] = new String[]{"time", "runs", "drips"};
        INSIDE[BrushPart.HAND.ordinal()] = new String[]{"pressure", "tilt", "speed"};
    }
    /**
     * {@link #INSIDE} for {@code part}, "ink · paint · charcoal", a no-break space before each dot so a wrapped line ends
     * with its dot and never starts with one.
     */
    static String inside(BrushPart part) { return String.join(" · ", INSIDE[part.ordinal()]); }

    /** parts.js choiceWords (473–490): a part's choice as its card says it ("Wet ink", "Slow 5.6 s", "4 lines"). */
    static String choiceWords(ModularBrush b, BrushPart part) {
        String own = ownWords(b, part);
        if (part != BrushPart.MEDIUM) return own;
        // Blend names the medium by what it lays (newmods.js choiceWords), its plain noun so the module keeps one line:
        // "White ink", "Chalk"; "Wash behind"; "Invert ink".
        int m = b.choice(BrushPart.MEDIUM).index();
        String words;
        switch (blend(b)) {
            case 1: words = WHITE_WORDS[m]; break;
            case 2: words = PATTERN_BASE[m] + " behind"; break;
            case 3: words = "Invert " + lower(PATTERN_BASE[m]); break;
            default: words = screen(b) > 0 ? screenWords(screen(b), PATTERN_BASE[m]) : own;
        }
        // Fling says so after the medium, as Slow's gravity does ("Ink, fling"), where the module's line has room (the Tip's
        // has none for Flick: its part's words name it, INSIDE).
        if (!(b.part(part).get(FLING, 0) >= .05)) return words;
        String both = words + ", fling";
        return fits(both) ? both : words;
    }
    /**
     * Screen names the medium by its print and plain noun (newmods.js choiceWords), "Halftone ink", "Dithered pencil";
     * the print alone where the two would pass the Medium module's line ("Halftone" for charcoal).
     */
    private static String screenWords(int screen, String base) {
        String both = SCREEN_WORDS[screen] + " " + lower(base);
        return fits(both) ? both : SCREEN_WORDS[screen];
    }
    /** The medium's Screen ({@link PartDial#SCREEN}, shown as {@link PrintScreen#LABEL}): 0 none, 1 halftone, 2 engraving, 3 dither, 4 grain, 5 rosette, 6 Ben-Day, 7 crosshatch, 8 wavy lines, 9 mezzotint, 10 diamond dots, 11 stipple, 12 woodcut, 13 scribble, 14 hatching. */
    private static int screen(ModularBrush b) { return (int) b.part(BrushPart.MEDIUM).get(SCREEN, 0); }
    /** Each Screen as the Medium card and "This brush" name what it lays ("Halftone ink"), and its caption tag, in option order. */
    private static final String[] SCREEN_WORDS = {"", "Halftone", "Engraved", "Dithered", "Grained", "Rosette", "Ben-Day", "Crosshatch", "Wavy-line", "Mezzotint", "Diamond-dot", "Stippled", "Woodcut", "Scribbled", "Hatched"},
            SCREEN_TAGS = {"", "halftone", "engraved", "dithered", "grained", "rosette", "ben-day", "crosshatch", "wavy-line", "mezzotint", "diamond-dot", "stippled", "woodcut", "scribbled", "hatched"};
    /** Tone (shown as Screentone) as "This brush" and captions name it, by its pattern ("Dot screentone"), in {@link PartDial#PATTERN} order. */
    private static final String[] TONE_WORDS = {"Dot screentone", "Line screentone", "Sand screentone", "Gradient screentone", "Crosshatch screentone", "Wavy-line screentone", "Mezzotint screentone", "Stipple screentone", "Woodcut screentone", "Scribble screentone", "Hatched screentone"};
    private static String toneNoun(ModularBrush b) { return TONE_WORDS[(int) b.part(BrushPart.MEDIUM).get(PATTERN)]; }
    /** Its Medium card: {@link #toneNoun}, or "Screentone" alone where that would pass the module's line ("Gradient screentone"). */
    private static String toneCard(ModularBrush b) { String noun = toneNoun(b); return fits(noun) ? noun : PartChoice.TONE.label; }
    /** Whether {@code words} fit the Medium module's line. */
    private static boolean fits(String words) { return LabText.width(words, 1, 0) <= LabLayout.choiceEm(BrushPart.MEDIUM) * LabText.em(1) + 1e-4; }
    /** Each medium with Blend's White, as its card says it, in medium order. */
    private static final String[] WHITE_WORDS = {"White ink", "White wash", "White paint", "Chalk", "White pencil"};
    /** The medium's Blend option ({@link PartDial#M_BLEND}): 0 its own way, 1 White, 2 Behind, 3 Invert. */
    private static int blend(ModularBrush b) { return (int) b.part(BrushPart.MEDIUM).get(M_BLEND, 0); }
    /** Blend's caption tag, after the style ("Fineliner, white"), in option order; none for the medium's own way. */
    private static final String[] BLEND_TAGS = {"", "white", "behind", "invert"};
    /** {@link #choiceWords} as the choice lays on its own, without Blend. */
    private static String ownWords(ModularBrush b, BrushPart part) {
        ModularBrush.Part p = b.part(part);
        PartChoice c = p.choice;
        switch (c) {
            case INK: { double wet = p.get(WET); return wet < .05 ? "Ink" : wet < .6 ? "Wet ink" : "Ink wash"; }
            case WASH: return p.get(WET) < .5 ? "Flat wash" : "Wet wash";
            case COVER: return p.get(IMPASTO) > .3 ? "Thick paint" : "Opaque";
            case CHARCOAL: { double spread = p.get(SPREAD); return spread > .5 ? "Messy charcoal" : spread < .12 ? "Crisp charcoal" : "Charcoal"; }
            case GRAPHITE: return "Graphite " + grade(p.get(GRADE));
            case RESIST: return resistNoun(b);
            case TONE: return toneCard(b);
            case CRAYON: return scratch(b) ? "Scratch" : c.label;
            case DRYING: {
                // A wash that moves with the page's tilt says how ("Slow, drips"; its time is in "This brush" and on
                // the dial); the module has room for one or the other.
                String g = resolved(b).grav != null ? gravityWord(p) : null;
                return !settles(b) ? c.label : g != null ? c.label + ", " + g : c.label + " " + seconds(b) + " s";
            }
            // The wet edge's millimetres are the dial's: the module says only that it is Quick.
            case FAST_WET: return c.label;
            case FLOAT: return c.label + " " + floatSeconds(b) + " s";
            // Mold says how long it grows: "Mold 6.6 s" (newmods.js choiceWords).
            case HALO: return c.label + " " + seconds(b) + " s";
            // Fading says how soon it goes ("Fading 20 s"); Lemon Juice's time would pass the module's line.
            case FADE: return c.label + " " + fadeTime(b);
            case LEMON: return sentence(c.label);
            case MULTI_LINE: return Math.round(p.get(COUNT)) + " lines";
            // Clear Water's module has room for one word, as Smudge's others: the water it lays.
            case CLEAR_WATER: return "Water";
            // Stay in Lines' module too: what it does.
            case STAY_IN: return "Stay in";
            // Filled Shape's too: what it lays.
            case SHAPE: return "Shape";
            // A Spiro says its curve, and its ring or track ("Rose ring", "Spirograph oval").
            case SPIRO: { int mode = (int) p.get(SPIRO_MODE); return p.choice.optionsOf(CURVE)[(int) p.get(CURVE)] + (mode == 0 ? "" : " " + SPIRO_MODE.choices[mode].toLowerCase(java.util.Locale.ROOT)); }
            default: return part == BrushPart.HAND ? handWords(b, 2) : c.label;
        }
    }

    /** What a card's macro row says for a choice with no macro dial (Drying's Instant, Smudge's Off). */
    static String noMacro(PartChoice c) { return c.part == BrushPart.LIFE ? "Dries at once." : c == PartChoice.SHAPE ? "Closes and fills." : "No smudge."; }

    /**
     * parts.js captionLines (492–501), unjoined: the style's name, then what is unusual for that style, in display
     * words: floating; a tip other than the style's usual; the medium unless it is the style's usual medium as it
     * usually is (or the style's own name); a smudge; Erasable when not the medium's own ({@link #erasable}); a paper
     * other than smooth and the style's usual; slow or instant drying; dust. {@link BrushCaptions} fits them to two lines.
     */
    static List<String> caption(ModularBrush b) {
        List<String> items = new ArrayList<>();
        ModularStyle style = b.style;
        items.add(style.label);
        PartChoice life = b.choice(BrushPart.LIFE);
        if (life == PartChoice.FLOAT) items.add(clearWater(b) ? "clear water" : "float");
        PartChoice tip = b.choice(BrushPart.TIP);
        // Stipple and Hatch say so first, after the style (newmods.js captionItems).
        if (pattern(tip)) items.add(1, lower(tip.label));
        else if (tip != usual(style, BrushPart.TIP) && !lower(tip.label).equals(lower(style.name))) items.add(lower(tip.label));
        PartChoice m = b.choice(BrushPart.MEDIUM);
        // Resist and Tone are named by their own tags (below): no medium tag, and their Erasable is theirs.
        boolean resist = m == PartChoice.RESIST, tone = m == PartChoice.TONE;
        String medium = resist || tone ? "" : mediumTag(b);
        if (!medium.isEmpty() && !medium.equals(lower(style.name)) && !(m == usual(style, BrushPart.MEDIUM) && medium.equals(PLAIN_TAG[m.index()])))
            items.add(medium);
        // Tone and Crayon smear nothing (they are laid without a smudge): no smudge tag.
        // Paint Load in the smudge's place: "Oils, filbert, runs out".
        boolean crayon = m == PartChoice.CRAYON;
        if (b.choice(BrushPart.CARRY) != PartChoice.OFF && !tone && !crayon && !(clearWater(b) && b.choice(BrushPart.CARRY) == PartChoice.CLEAR_WATER)) items.add(runsOut(b) ? "runs out" : stump(b) ? (combs(b) ? "comb" : "stump") : lower(b.choice(BrushPart.CARRY).label));
        if (keepsPaint(b) && !tone && !crayon) items.add("keeps paint");
        String erasable = resist || tone ? "" : erasable(b);
        if (!erasable.isEmpty()) items.add(lower(erasable));
        PartChoice paper = b.choice(BrushPart.PAPER);
        if (paper != PartChoice.PLAIN && paper != usual(style, BrushPart.PAPER)) items.add(lower(paper.label));
        if (life == PartChoice.DRYING && settles(b)) { String g = gravityWord(b.part(BrushPart.LIFE)); items.add(g != null ? g : "slow"); }
        else if (life == PartChoice.NONE) items.add("instant");
        if (m == PartChoice.CHARCOAL && b.part(BrushPart.MEDIUM).get(DUST) > .15) items.add("dust");
        // Blend says so first, after the style, before a Stipple or Hatch (newmods.js captionItems).
        if (blend(b) > 0) items.add(1, BLEND_TAGS[blend(b)]);
        // Screen after Blend: "Marker, halftone" (newmods.js captionItems).
        if (screen(b) > 0) items.add(1 + (blend(b) > 0 ? 1 : 0), SCREEN_TAGS[screen(b)]);
        // Resist and Tone first of all, after the style: "Watercolour, resist", "Watercolour, wax", "Anything goes, dot tone".
        if (resist) items.add(1, wax(b) ? "wax" : "resist");
        if (tone) items.add(1, lower(toneNoun(b)));
        // Mold after those, before the rest: "Ink wash, mold" (newmods.js captionItems).
        // So do Fading and Lemon Juice: "Anything goes, fading" (newmods.js captionItems).
        // So does Wet: "Gouache, wet".
        if (life == PartChoice.HALO || life == PartChoice.FADE || life == PartChoice.LEMON || life == PartChoice.WET_PAINT)
            items.add(1 + (resist || tone ? 1 : 0) + (blend(b) > 0 ? 1 : 0) + (screen(b) > 0 ? 1 : 0) + (pattern(tip) ? 1 : 0), lower(life.label));
        return items;
    }
    /**
     * The Erasable dial in words, only when it isn't the medium's own value ({@link ModularStyle#erasable}): "Erasable",
     * "Permanent" or "Part erasable"; else empty (parts.js eraseLabel).
     */
    static String erasable(ModularBrush b) {
        ModularBrush.Part m = b.part(BrushPart.MEDIUM);
        double e = m.get(ERASABLE, 1);
        if (Math.abs(e - b.style.erasable(m.choice)) < .05) return "";
        return e >= .95 ? "Erasable" : e <= .05 ? "Permanent" : "Part erasable";
    }
    /** Each medium's tag as it usually is, in medium order: what a caption leaves out for the style's usual medium. */
    private static final String[] PLAIN_TAG = {"ink", "wet wash", "opaque", "charcoal", ""};
    /** The medium as a caption tag: "wet ink", "flat wash", "impasto", "messy", "4B" (with Pencil; else "4B graphite"). */
    private static String mediumTag(ModularBrush b) {
        ModularBrush.Part m = b.part(BrushPart.MEDIUM);
        switch (m.choice) {
            case INK: { double wet = m.get(WET); return wet < .05 ? "ink" : wet < .6 ? "wet ink" : "ink wash"; }
            case WASH: return m.get(WET) < .5 ? "flat wash" : "wet wash";
            case COVER: return m.get(IMPASTO) > .3 ? "thick paint" : "opaque";
            case CHARCOAL: { double spread = m.get(SPREAD); return spread > .5 ? "messy" : spread < .12 ? "crisp" : "charcoal"; }
            case CRAYON: return scratch(b) ? "scratch" : "crayon";
            default: return grade(m.get(GRADE)) + ("Pencil".equals(b.style.name) ? "" : " graphite");
        }
    }
    /** The choice {@code style} weighs most for {@code part} (the first of equals, in code order); null if it weighs none. */
    static PartChoice usual(ModularStyle style, BrushPart part) {
        PartChoice best = null; double most = 0;
        for (PartChoice c : part.choices()) if (style.weight(c) > most) { most = style.weight(c); best = c; }
        return best;
    }

    /**
     * The brush's facts, never shown as they are: {@link BrushVoice#say} says them in friendly sentences.
     * parts.js plainWords (507–537), reworded: up to five short sentences in the order a painter notices: what it is
     * and what it's on ("Line and wash, chisel tip, on felt paper."); its two strongest marks; its smudge; Erasable when not the medium's own; its drying
     * when not Quick; how it answers pressure, tilt and speed. A round tip and smooth paper go unsaid. Plumes are left
     * out until they draw.
     */
    static String plainWords(ModularBrush b) {
        ModularBrush.Part t = b.part(BrushPart.TIP), m = b.part(BrushPart.MEDIUM), life = b.part(BrushPart.LIFE);
        List<String> out = new ArrayList<>();
        // Clear water (Float, Smudge's Own paint 0) lays nothing: what it does on the water is all there is to say.
        if (clearWater(b))
            return "Clear water. Taps drop clear rings, dragging combs them" + (b.part(BrushPart.CARRY).get(PICK) > .05 ? "" : " a little") + ".";
        boolean floats = life.choice == PartChoice.FLOAT;
        // Stipple, Hatch, Stamp and Spiro are in the noun ("Ink stipple", "Ink hatching", "Ink stamp", "Ink spiro"): no tip of their own to name.
        String tip = t.choice == PartChoice.ROUND || pattern(t.choice) ? "" : ", " + tipNoun(t);
        String on = floats ? " on water" : b.choice(BrushPart.PAPER) == PartChoice.PLAIN ? "" : " on " + paperWords(b.choice(BrushPart.PAPER));
        // Clear Water lays no paint of its own: it is named by what it lays.
        String noun = floats && m.choice == PartChoice.INK ? "Sumi ink" : water(b) ? "Clear water" : noun(b);
        // White names what it lays (newmods.js plainWords): "White ink line.", "White chalk.", "White pencil."
        if (blend(b) == 1) noun = !pattern(t.choice) && m.choice == PartChoice.CHARCOAL ? "White chalk" : !pattern(t.choice) && m.choice == PartChoice.GRAPHITE ? "White pencil" : "White " + lower1(noun);
        // Screen says its print first (newmods.js plainWords): "Halftone ink line.", "Dithered white chalk."
        if (screen(b) > 0) noun = SCREEN_WORDS[screen(b)] + " " + lower1(noun);
        out.add(noun + tip + (tip.isEmpty() || on.isEmpty() ? "" : ",") + on + ".");
        // Stipple's Build-up, after what it is (newmods.js plainWords).
        if (t.choice == PartChoice.STIPPLE && (int) t.get(BUILD_UP) > 0) out.add("Builds up where it lingers.");
        // A Stamp says what its nib is, and that it runs dry when its Ink Fade is on (newmods.js plainWords).
        if (t.choice == PartChoice.STAMP) {
            out.add(STAMPS);
            if (t.get(INK_FADE) >= .005) out.add(FADES);
        }
        // A Crayon says what its wax does to what comes later (newmods.js plainWords).
        if (m.choice == PartChoice.CRAYON) out.add(scratch(b) ? SCRAPES : m.get(WAX) >= .05 ? BUILDS : BUILDS_ONLY);
        // A Spiro says where its loops go (newmods.js plainWords).
        if (t.choice == PartChoice.SPIRO) out.add(new String[]{SPIRO_LINE, SPIRO_RING, SPIRO_OVAL, SPIRO_BAR}[(int) t.get(SPIRO_MODE)]);
        // Hatching says how its strokes build.
        if (t.choice == PartChoice.HAND_HATCH) out.add(HATCHES);
        // Grey Shift says how its grey shifts (newmods.js plainWords).
        String shifts = SwatchPicker.words(b);
        if (shifts != null) out.add(shifts);
        // Gradient says how the grey changes along the stroke, and over how far (newmods.js plainWords).
        String gradient = GreyGradient.words(b);
        if (gradient != null) out.add(gradient);
        // Flick and Fling, the mischief, said plainly next (newmods.js plainWords).
        String mischief = mischiefWords(b);
        if (mischief != null) out.add(mischief);
        // Resist says what it does to what comes later; its Erasable is that (no "Permanent." below).
        boolean resist = m.choice == PartChoice.RESIST;
        if (resist) out.add(wax(b) ? "Nothing takes on it." : "Nothing takes on it until rubbed off.");
        // Tone says what the eraser does to it, its one unusual trait.
        boolean tone = m.choice == PartChoice.TONE;
        if (tone) out.add("Scrapes off in streaks.");
        // Behind and Invert say what they do to what is there.
        if (blend(b) == 2) out.add("Goes behind marks.");
        if (blend(b) == 3) out.add("Inverts what's under it.");
        // Screen's newsprint dials, the two strongest (newmods.js newsprintWords): "Doubled, dots swell."
        if (screen(b) > 0) {
            List<Mark> news = new ArrayList<>();
            add(news, m.get(MIS) / 8, .1, "doubled");
            add(news, m.get(S_GAIN), .25, "dots swell");
            add(news, m.get(UNEVEN), .25, "patchy ink");
            sortStrongest(news);
            if (!news.isEmpty()) out.add(cap1(news.get(0).words) + (news.size() > 1 ? ", " + news.get(1).words : "") + ".");
        }

        List<Mark> marks = new ArrayList<>();
        add(marks, t.get(TAPER_IN) + t.get(TAPER_OUT), .9, "tapers to a point");
        add(marks, t.get(DRY), .3, t.choice == PartChoice.HAKE ? "dry-brush streaks" : "dry-brush texture");
        add(marks, t.get(WOBBLE), .25, "slight wobble");
        add(marks, m.get(POOL), .35, m.choice == PartChoice.WASH ? "dark edges" : "pools when slow");
        add(marks, m.get(SPLATTER), .3, "spatters on flicks");
        add(marks, m.get(BLOOM), .3, "blooms");
        add(marks, m.get(BACKRUN), .3, "backruns");
        add(marks, m.get(BLEED), .35, "bleeds");
        add(marks, m.get(IMPASTO), .3, "raised ridges");
        if (m.choice == PartChoice.CHARCOAL)
            add(marks, m.get(SPREAD) + .3, .35, m.get(SPREAD) > .5 ? "dusty broken edge" : "soft speckled edge");
        if (m.choice == PartChoice.CHARCOAL) add(marks, m.get(DUST), .2, "sheds dust when tilted");
        // Quick's gravity is a mark of the stroke under the pen (Slow's is in what happens after lift).
        if (life.choice == PartChoice.FAST_WET && resolved(b).grav != null && resolved(b).body != null)
            add(marks, Math.max(life.get(RUNS), Math.max(life.get(G_POOL), life.get(SINK))), .15, gravityVerbs(life) + " when tilted");
        add(marks, m.get(OFFSET), .25, "line shifted");
        add(marks, m.get(CHAOS), .25, "broken line");
        if (t.choice == PartChoice.SPLIT_NIB) marks.add(new Mark(.5, "opens into two lines"));
        if (t.choice == PartChoice.BRISTLES && ModularFeatures.BRISTLES) {
            marks.add(new Mark(.4, "splits into strands"));
            // How soon its hairs start to run dry: streaks within a page's width, sooner the drier and the less it holds.
            double streaks = BristleSwarm.streaksAt(t.get(DRYNESS), t.get(INK_LOAD));
            if (streaks < 2000) marks.add(new Mark(.3 + .5 * (1 - streaks / 2000), streaks < 800 ? "runs dry quickly" : "runs dry on long strokes"));
        }
        double tooth = b.part(BrushPart.PAPER).get(TOOTH);
        if (tooth > .55) marks.add(new Mark(tooth - .5, "picks up the texture"));
        sortStrongest(marks);
        if (!marks.isEmpty()) out.add(cap1(marks.get(0).words) + (marks.size() > 1 ? ", " + marks.get(1).words : "") + ".");

        // On water Smudge combs what floats; the drying's sentence says so.
        String carry = ModularFeatures.CARRY && !floats && !tone && m.choice != PartChoice.CRAYON ? carryWords(b) : null;
        if (carry != null) out.add(carry);
        String erasable = resist || tone ? "" : erasable(b);
        if (!erasable.isEmpty()) out.add(erasable + ".");
        String drying = lifeWords(b);
        if (drying != null) out.add(drying);

        String hand = handWords(b, 2);
        if (!hand.equals("steady")) out.add(cap1(hand) + ".");
        // At most MOST characters: sentences past the second that don't fit are left out.
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < out.size(); i++) {
            if (i >= 2 && s.length() + 1 + out.get(i).length() > MOST) break;
            s.append(i > 0 ? " " : "").append(out.get(i));
        }
        return s.toString();
    }

    /**
     * Flick and Fling in "This brush": "Flicks on a quick lift.", "Flings drops on a hard stop.", or both in one
     * sentence; null for neither.
     */
    static String mischiefWords(ModularBrush b) {
        boolean flick = b.part(BrushPart.TIP).get(FLICK, 0) >= .05, fling = b.part(BrushPart.MEDIUM).get(FLING, 0) >= .05;
        if (flick && fling) return "Flicks on a quick lift, flings drops on a hard stop.";
        return flick ? "Flicks on a quick lift." : fling ? "Flings drops on a hard stop." : null;
    }
    /** {@code text}'s sentences, each with its full stop: it splits after a full stop followed by a space, so "5.6 s" stays whole. */
    static List<String> sentences(String text) {
        List<String> out = new ArrayList<>();
        for (String s : text.trim().split("(?<=\\.)\\s+")) if (!s.isEmpty()) out.add(s);
        return out;
    }
    /** The first {@code count} of {@code text}'s sentences, never fewer than two (ui.js fitPlain). */
    static String firstSentences(String text, int count) {
        List<String> all = sentences(text);
        return String.join(" ", all.subList(0, Math.min(all.size(), Math.max(2, count))));
    }

    /**
     * The note under a swap strip tile (ui.js renderSwap, 423, 451): a medium in words, a drying's time or how it
     * dries, Control's two strongest cells; nothing for the other parts, nor for Quick or a Slow that is done at lift.
     */
    /**
     * A choice's name on its swap tile: its label, Screentone's with a soft break ({@link LabText#SOFT_BREAK}) where
     * the Nomad's narrow Medium tiles break it into the owner's short form, "Screen" / "Tone".
     */
    static String tileLabel(PartChoice c) { return c == PartChoice.TONE ? "Screen" + LabText.SOFT_BREAK + "tone" : c.label; }
    static String swapNote(ModularBrush b, BrushPart part) {
        ModularBrush.Part p = b.part(part);
        switch (part) {
            // Screentone: its pattern (the tile already says Screentone, and "Dot screentone" wouldn't fit it).
            case MEDIUM: return p.choice == PartChoice.TONE ? lower(p.choice.optionsOf(PATTERN)[(int) p.get(PATTERN)]) : choiceWords(b, part);
            case HAND: return handWords(b, 2);
            case LIFE:
                if (p.choice == PartChoice.DRYING) return settles(b) ? "wet " + seconds(b) + " s" : "";
                if (p.choice == PartChoice.FAST_WET) return "";
                if (p.choice == PartChoice.FLOAT) return clearWater(b) ? "clear water" : "floats " + floatSeconds(b) + " s";
                if (p.choice == PartChoice.HALO) return lower(p.choice.label) + " " + seconds(b) + " s";
                if (p.choice == PartChoice.FADE) return "fades " + fadeTime(b);
                if (p.choice == PartChoice.LEMON) return "darkens " + fadeTime(b);
                if (p.choice == PartChoice.WET_PAINT && resolved(b).wet != null) return "stays wet";
                return "dries at once";
            default: return "";
        }
    }

    /** Resist with its Erasable under .5 (newmods.js): wax, which stays; else masking fluid, which rubs off. */
    static boolean wax(ModularBrush b) { return b.part(BrushPart.MEDIUM).get(ERASABLE, 1) < .5; }
    /** Crayon with its Scratch on: a blade that scrapes back to white. */
    static boolean scratch(ModularBrush b) { return b.choice(BrushPart.MEDIUM) == PartChoice.CRAYON && (int) b.part(BrushPart.MEDIUM).get(SCRATCH) == 1; }
    /** Resist as its card and "This brush" name it. */
    private static String resistNoun(ModularBrush b) { return wax(b) ? "Wax resist" : "Masking fluid"; }
    /** Stipple, Hatch, Stamp or Spiro: a tip the brush is named by ({@link #noun}). */
    private static boolean pattern(PartChoice tip) { return tip == PartChoice.STIPPLE || tip == PartChoice.HATCH || tip == PartChoice.STAMP || tip == PartChoice.SPIRO || tip == PartChoice.HAND_HATCH; }
    /** Each medium as a stipple or hatching names it, and Behind and Invert (newmods.js NM_BASE), in medium order. */
    private static final String[] PATTERN_BASE = {"Ink", "Wash", "Paint", "Charcoal", "Pencil"};

    /**
     * The medium as "This brush" names it: in its style's words (a Pastels charcoal is a pastel, an Oils cover oil
     * paint); with a Stipple, Hatch, Stamp or Spiro tip, what it lays ("Ink stipple", "Sponged wash", "Pencil hatching", "Ink stamp", "Ink spiro").
     */
    private static String noun(ModularBrush b) {
        ModularBrush.Part m = b.part(BrushPart.MEDIUM);
        PartChoice tip = b.choice(BrushPart.TIP);
        // Resist and Tone are named for themselves, whatever their tip (newmods.js plainWords).
        if (m.choice == PartChoice.RESIST) return resistNoun(b);
        if (m.choice == PartChoice.TONE) return toneNoun(b);
        if (m.choice == PartChoice.CRAYON) return scratch(b) ? "Crayon scratch" : "Wax crayon";
        if (pattern(tip)) {
            String base = PATTERN_BASE[m.choice.index()];
            if (tip == PartChoice.HATCH) return base + " hatching";
            if (tip == PartChoice.HAND_HATCH) return base + " hatched by hand";
            if (tip == PartChoice.STAMP) return base + " stamp";
            if (tip == PartChoice.SPIRO) return base + " spiro";
            return m.choice == PartChoice.WASH || m.choice == PartChoice.COVER ? "Sponged " + lower(base) : base + " stipple";
        }
        String style = b.style.name;
        switch (m.choice) {
            case INK: { double wet = m.get(WET); return wet < .05 ? "Ink line" : wet < .45 ? "Line and wash" : "Ink wash"; }
            case WASH: return m.get(WET) < .5 ? "Flat wash" : "Wet wash";
            case COVER: return m.get(IMPASTO) > .3 ? "Thick paint" : m.get(PartDial.COVER) > .8 ? ("Oils".equals(style) ? "Oil paint" : "Gouache") : "Semi-opaque paint";
            case CHARCOAL: {
                boolean hard = m.get(HOLD) > .6;
                return "Pastels".equals(style) ? (hard ? "Hard pastel" : "Soft pastel") : hard ? "Compressed charcoal" : "Vine charcoal";
            }
            default: return grade(m.get(GRADE)) + " graphite";
        }
    }
    private static String tipNoun(ModularBrush.Part t) {
        switch (t.choice) {
            case CHISEL: return "chisel tip";
            case FLAT_NIB: return "flat nib";
            case HAKE: return "hake";
            case SPLIT_NIB: return "split nib";
            case MULTI_LINE: { long count = Math.round(t.get(COUNT)); return (count != 0 ? count : 3) + "-line rake"; }
            case BRISTLES: return "bristle brush";
            case FILBERT: return "filbert";
            default: return "round tip";
        }
    }
    /** parts.js PAPER_WORDS. */
    private static String paperWords(PartChoice paper) {
        switch (paper) {
            case COLD_PRESS: return "cold-press paper";
            case LAID: return "ribbed paper";
            case FELT: return "felt paper";
            case CRUMPLED: return "crumpled paper";
            case CANVAS: return "canvas";
            case COTTON: return "cotton paper";
            case BUMPY: return "bumpy paper";
            case UNRYU: return "unryu paper";
            case LIBRARY: return "library paper";
            case KRAFT: return "kraft paper";
            case OLD_BUFF: return "vintage paper";
            case BLUE_FIBRE: return "craft paper";
            case FINE_SPECK: return "fine paper";
            default: return "smooth paper";
        }
    }
    private static String carryWords(ModularBrush b) {
        // Keeps Paint says so after the smudge (a dirty brush): "Smears. Keeps paint for the next stroke."
        String keeps = keepsPaint(b) ? " Keeps paint for the next stroke." : "";
        // Paint Load says plainly what it does, and how to load it, in the smudge's place (the oil paint spec).
        if (runsOut(b)) return "Runs out of paint, then only smudges. Rub a grey to load." + keeps;
        // The Wet brush's ink in water (the wet brush spec) says what it holds, and how to dip it, in the smudge's place.
        if (holdsInk(b)) return "Holds ink between strokes. Tap a grey to dip.";
        switch (b.choice(BrushPart.CARRY)) {
            case LIFT: return "Lifts paint.";
            case DRAG: return "Drags wet paint." + keeps;
            case SMUDGE: return (stump(b) ? (combs(b) ? "Combs." : "Softens.") : "Smears.") + keeps;
            case CLEAR_WATER: return "Lifts paint, lets it run.";
            // Stay in Lines says how far it floods inside the lines (newmods.js plainWords).
            case STAY_IN: { double f = b.part(BrushPart.CARRY).get(FLOOD); return f < .005 ? "Stays inside ink lines." : f > .995 ? "Fills the shape it starts in." : "Floods inside ink lines."; }
            // Filled Shape says what it does at the lift (FilledShape).
            case SHAPE: return "Fills the shape it outlines.";
            default: return null;
        }
    }
    /** Drag's or Smear's Keeps Paint on, a dirty brush (not all stump, which holds nothing): "keeps paint" (parts.js keepsPaint). */
    static boolean keepsPaint(ModularBrush b) {
        ModularBrush.Part c = b.part(BrushPart.CARRY);
        return c.get(KEEPS, 0) > 0 && c.get(SOFTEN, 0) < 1;
    }
    /**
     * Drag's or Smear's Paint Load on (the oil paint spec, {@link LoadedPaint}): its own paint runs out along the stroke,
     * "Runs out of paint, then only smudges.", tagged "runs out" (parts.js runsOut). Not on water, where it combs what floats.
     */
    static boolean runsOut(ModularBrush b) {
        PartChoice c = b.choice(BrushPart.CARRY);
        PartChoice m = b.choice(BrushPart.MEDIUM);
        // Resist and Screentone are laid as a crisp ink line without a smudge, Crayon without one: nothing to run out (ResolvedBrush.asInk).
        return (c == PartChoice.DRAG || c == PartChoice.SMUDGE) && b.part(BrushPart.CARRY).get(PLOAD, 0) > 0 && b.choice(BrushPart.LIFE) != PartChoice.FLOAT
                && ModularBrush.ownPaint(b.part(BrushPart.CARRY)) > 0 && m != PartChoice.RESIST && m != PartChoice.TONE && m != PartChoice.CRAYON;
    }
    /** Smudge's Clear Water off the water (on Float it is Float's clear water, {@link #clearWater}). */
    /** Wet ink in water (the wet brush spec, {@link ResolvedBrush.Wet#inks}): its brush holds its ink between strokes. */
    static boolean holdsInk(ModularBrush b) { ResolvedBrush.Wet w = b.choice(BrushPart.LIFE) == PartChoice.WET_PAINT ? resolved(b).wet : null; return w != null && w.inks; }
    static boolean water(ModularBrush b) { return b.choice(BrushPart.CARRY) == PartChoice.CLEAR_WATER && b.choice(BrushPart.LIFE) != PartChoice.FLOAT; }
    /** A Smear that is mostly MonoPaint's blending stump (Soften from .5): "Softens.", tagged "stump" (parts.js isStump). */
    static boolean stump(ModularBrush b) { return b.choice(BrushPart.CARRY) == PartChoice.SMUDGE && b.part(BrushPart.CARRY).get(SOFTEN, 0) >= .5; }
    /** A stump with tines, a Rake or a Split nib: it combs ("Combs.", tagged "comb"). */
    static boolean combs(ModularBrush b) { PartChoice t = b.choice(BrushPart.TIP); return t == PartChoice.MULTI_LINE || t == PartChoice.SPLIT_NIB; }
    /**
     * How it dries, or null when there is nothing to say (Quick, the usual; a Slow that is done at lift): Instant
     * dries at once; a Slow wash that settles is wet for its drying time, and says what it does when tilted; Float
     * floats for its time and drops rings.
     */
    private static String lifeWords(ModularBrush b) {
        ModularBrush.Part life = b.part(BrushPart.LIFE);
        if (life.choice == PartChoice.NONE) return "Dries at once.";
        if (life.choice == PartChoice.FAST_WET) return null;
        ResolvedBrush r = resolved(b);
        if (life.choice == PartChoice.FLOAT) {
            PartChoice carry = b.choice(BrushPart.CARRY);
            boolean combs = carry == PartChoice.DRAG || carry == PartChoice.SMUDGE || carry == PartChoice.CLEAR_WATER;
            return "Floats " + floatSeconds(b) + " s. Taps drop rings" + (combs ? ", dragging combs them" : "") + ".";
        }
        // Mold: what its veins do, for how long (newmods.js plainWords).
        if (life.choice == PartChoice.HALO) return "Veins bridge nearby marks for " + seconds(b) + " s.";
        // Fading and Lemon Juice, plainly (the mischief modules spec, Stage 3).
        if (life.choice == PartChoice.FADE) return "Fades away in " + fadeTime(b) + ".";
        if (life.choice == PartChoice.LEMON) return "Darkens in as it dries.";
        // Wet (the wet paint spec): it stays wet until the droplet dries it, and says when it runs.
        if (life.choice == PartChoice.WET_PAINT) {
            if (r.wet == null) return "Dries at once.";
            String stays = "Stays wet until dried" + (r.wet.runs > .15 ? ", runs when tilted" : "") + ".";
            // A wet watercolour (section 9) also says what wet into wet does, and blooms when it has them.
            return r.wet.wash == null ? stays : stays + " Merges while wet" + (r.wet.wash.bloom > 0 ? ", blooms when damp" : "") + ".";
        }
        if (!settles(b)) return null;
        // Clear Water's wet moves as a wash's body does.
        String moves = (r.body != null || water(b)) && r.grav != null ? gravityVerbs(life) : null;
        String plumes = ModularFeatures.PLUMES && (int) life.get(TAKES) != 0 ? ", pen lines plume in it" : "";
        return "Wet for " + seconds(b) + " s" + (moves != null ? ", " + moves + " when tilted" : "") + plumes + ".";
    }
    /** A Slow brush's drying time, seconds to a tenth, as its dial and module say it. */
    private static String seconds(ModularBrush b) { return fixed(b.dryingMs() / 1000.0, 1); }
    /** Float's floating time, seconds: whole from 10 s, so "Float 16 s" fits the module as "Slow 16.3 s" does. */
    private static String floatSeconds(ModularBrush b) { double sec = b.dryingMs() / 1000.0; return fixed(sec, sec >= 9.95 ? 0 : 1); }
    /** Fading's Fades In or Lemon Juice's Dries In as its dial says it: "20 s", "4.0 s". */
    private static String fadeTime(ModularBrush b) { ModularBrush.Part life = b.part(BrushPart.LIFE); PartDial d = life.choice.dials.get(0); return d.shown(life.get(d)); }
    /** The strongest gravity dial of {@code life} past .15, as its card says it ("drips"), or null (the Gravity Lab's gravWord). */
    static String gravityWord(ModularBrush.Part life) {
        String best = null; double v = .15;
        for (int i = 0; i < GRAVITY.length; i++) if (life.get(GRAVITY[i]) > v) { v = life.get(GRAVITY[i]); best = GRAVITY_WORDS[i]; }
        return best;
    }
    /** The gravity dials as cards and captions say them, in {@link PartDial#GRAVITY} order. */
    private static final String[] GRAVITY_WORDS = {"runs", "drips", "pools", "grainy", "blends"};
    /** What {@code life}'s gravity does when the page tilts, its two strongest dials, strongest first: "drips and runs"; null without any. */
    static String gravityVerbs(ModularBrush.Part life) {
        List<Mark> on = new ArrayList<>();
        String[] verbs = {"runs", "drips", "pools", "granulates", "blends when rocked"};
        for (int i = 0; i < GRAVITY.length; i++) if (life.get(GRAVITY[i]) > 0) on.add(new Mark(life.get(GRAVITY[i]), verbs[i]));
        if (on.isEmpty()) return null;
        sortStrongest(on);
        return on.get(0).words + (on.size() > 1 ? " and " + on.get(1).words : "");
    }
    /** Clear water (parts.js isClearWater): a Float brush whose Smudge drags with no paint of its own (Smudge's Clear Water too). */
    static boolean clearWater(ModularBrush b) {
        PartChoice carry = b.choice(BrushPart.CARRY);
        return b.choice(BrushPart.LIFE) == PartChoice.FLOAT && (carry == PartChoice.DRAG || carry == PartChoice.SMUDGE || carry == PartChoice.CLEAR_WATER)
                && ModularBrush.ownPaint(b.part(BrushPart.CARRY)) < .02;
    }
    /** Slow's wash keeps settling after lift, as the stroke draws it ({@link ResolvedBrush#settlesAfterLift}): transparent, with Settle. */
    static boolean settles(ModularBrush b) { return resolved(b).settlesAfterLift; }
    /** The brush as the engine draws it; any size and darkness, since neither changes what settles. */
    private static ResolvedBrush resolved(ModularBrush b) { return ResolvedBrush.of(b, 8, 1); }

    /** parts.js plainWords' add: a mark when {@code v} is past {@code threshold}, as strong as it is past it. */
    private static void add(List<Mark> marks, double v, double threshold, String words) {
        if (v > threshold) marks.add(new Mark(v - threshold, words));
    }
    /** Strongest first; a stable sort, as the prototype's, so equals keep their order. */
    private static void sortStrongest(List<Mark> marks) {
        Collections.sort(marks, new Comparator<Mark>() {
            @Override public int compare(Mark a, Mark b) { return Double.compare(b.strength, a.strength); }
        });
    }
    private static String lower(String s) { return s.toLowerCase(Locale.ROOT); }
    /** A Title Case label in a sentence or a card's words: its first letter as it is, the rest lower case ("Flat Nib" → "Flat nib"). */
    static String sentence(String label) { return label.isEmpty() ? label : label.charAt(0) + lower(label.substring(1)); }
    /** newmods.js lc1: the first letter lower case ("Wet wash" → "wet wash"). */
    private static String lower1(String s) { return s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1); }
    /** parts.js cap1. */
    private static String cap1(String s) { return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1); }
}
