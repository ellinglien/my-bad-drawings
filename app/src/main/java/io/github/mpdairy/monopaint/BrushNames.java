package io.github.mpdairy.monopaint;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Names for new brushes and drawings, from plain text files Elling can edit (assets/names, one name a line):
 * an adjective and a fruit or vegetable ("dapper-fig") for a drawing; a new brush is named for what it is instead,
 * a fun word, a describer and its part ("Sloppy Inky Drag", "Polite Smear", {@link #described}), from names/traits.txt, as is a new
 * notebook's title ("Several Opinions About Soup", {@link #titles}). Brushes and notebooks store their names, so editing the lists renames nothing.
 * Pure, checked on the desktop.
 */
final class BrushNames {
    /** What naming falls back on when a list is missing, or holds nothing it can use. */
    static final String[] ADJECTIVES_DEFAULT = {"bold", "brave", "brisk", "dapper", "mellow", "nimble", "regal", "swift"},
            PRODUCE_DEFAULT = {"plum", "pear", "fig", "lime", "leek", "kale", "yam", "beet"};
    /** traits.txt's sections, in a name's order: its fun word, its describer, its part ("sloppy-inky-drag"). */
    static final String FUN = "fun", DESCRIBER = "describer", PART = "part";
    private static List<String> adjectives, produce;
    private static Map<String, Map<String, List<String>>> traits;

    private BrushNames() { }

    static synchronized List<String> adjectives() { if (adjectives == null) adjectives = load("names/adjectives.txt", ADJECTIVES_DEFAULT); return adjectives; }
    static synchronized List<String> produce() { if (produce == null) produce = load("names/produce.txt", PRODUCE_DEFAULT); return produce; }
    /** names/traits.txt's sections ({@link #traits(List)}); none when it is missing, so names stay adjective-produce. */
    static synchronized Map<String, Map<String, List<String>>> traits() { if (traits == null) traits = traits(lines("names/traits.txt")); return traits; }

    /** An adjective and a fruit or vegetable, two draws from {@code random}: a new drawing's name, and a new brush's before {@link #described}. */
    static String fresh(Random random) {
        return pick(adjectives(), random) + "-" + pick(produce(), random);
    }
    /**
     * A variation's name, drawing an adjective from {@code random} first as it always did, so every variation rolls as
     * before. A capitalized name led by a word of [deadpan], [fun], [describer] or [part] ("Polite Smear", "Dusty Aunt Charcoal")
     * keeps the rest under another word of the same line, from {@code seed} (the variation's): "Nervous Smear". A name led
     * by a fun word (not a fruit or vegetable's) keeps the rest, its describer and part, under another word of the same
     * fun line: "sloppy-inky-drag" to "loose-inky-drag". A fruit or vegetable's name led by anything but an adjective (an
     * older "drip-plum") keeps its first word under a new one from {@code seed}; any other keeps its last word under the
     * new adjective, so "Bobson Dugnutt" becomes adjective-family too. A name with no last word ("-", "") gets a fresh name.
     */
    static String kin(String name, long seed, Random random) {
        String family = family(name);
        if (family.isEmpty()) return fresh(random);
        String adjective = pick(adjectives(), random);
        String[] words = name.trim().toLowerCase(Locale.ROOT).split("[- ]+");
        if (words.length >= 2 && Character.isUpperCase(name.trim().charAt(0)))
            for (String section : new String[]{DEADPAN, FUN, DESCRIBER, PART}) {
                Map<String, List<String>> lines = traits().get(section);
                if (lines != null) for (List<String> line : lines.values())
                    if (line.contains(words[0])) return cap(other(line, words[0], seed)) + name.trim().substring(words[0].length());
            }
        Map<String, List<String>> fun = traits().get(FUN);
        if (fun != null && words.length >= 2 && !produce().contains(family)) for (List<String> line : fun.values())
            if (line.contains(words[0])) return other(line, words[0], seed) + name.trim().toLowerCase(Locale.ROOT).substring(words[0].length());
        if (words.length == 2 && !words[0].isEmpty() && !adjectives().contains(words[0]) && produce().contains(family)) return words[0] + "-" + other(produce(), family, seed);
        return adjective + "-" + family;
    }
    /** A name's family part: its last word, lower case; empty when it has none. */
    static String family(String name) {
        return name.substring(Math.max(name.lastIndexOf('-'), name.lastIndexOf(' ')) + 1).trim().toLowerCase(Locale.ROOT);
    }
    static String pick(List<String> list, Random random) { return list.get(random.nextInt(list.size())); }

    // ---- What it is ----

    /** Whether new brushes are named for what they are; the checks turn it off to see every roll is as before. */
    static boolean traitNames = true;
    /** parts.js TRAIT_SALT and KIN_SALT: the seed's own hashes for a line's word and a variation's. */
    private static final int TRAIT_SALT = 0x7a175, KIN_SALT = 0x4b1e5;
    /** A strong paper grain: Tooth past its default (.5; most styles keep it under .2). */
    static final double STRONG_TOOTH = .6;
    /** Blend Mode's options as the trait keys name them ({@code blend.white}), in option order: keys, not its display words. */
    private static final String[] BLEND_KEYS = {"darken", "white", "behind", "invert"};

    /**
     * A new brush's name said by what it is ({@link #words}), "dapper-plum" to "sloppy-inky-drag"; {@code name} as it is
     * when it has no last word or traits.txt names nothing. From the brush and its seed, drawing nothing from any
     * Random, so a roll's every part and seed come out as before.
     */
    static String described(ModularBrush b, String name) {
        if (!traitNames || family(name).isEmpty()) return name;
        String named = name(b);
        return named == null ? name : named;
    }
    /**
     * {@code b}'s name, deadpan (the owner, 2026-10-29: "mostly deadpan"; "have the brush names hint at what the brush
     * does for sure"): every name has its part, the word for what makes it ({@link #words}). About {@link #PLAIN} its words
     * capitalized ("Sloppy Inky Drag"; Title Case, the owner, 2026-10-29), the rest a {@link #deadpan} form ("Polite
     * Smear"), the next form where one won't wrap to two lines of a card, then its words, its plain words, its describer
     * and part. Null with no words. parts.js brushNameOf.
     */
    static String name(ModularBrush b) { return name(b, n -> false); }
    /**
     * {@link #name}, passing by the names {@code taken} (a library's, {@link BrushLibrary#has}) for the next one it
     * would give; all taken, the first, which a save numbers ({@link BrushLibrary#freeName}).
     */
    static String name(ModularBrush b, java.util.function.Predicate<String> taken) {
        List<String> w = words(b), plain = plainWords(b), tries = new ArrayList<>();
        if (w.isEmpty()) return null;
        int s = (int) b.seed ^ TRAIT_SALT;
        if (w.size() == 3 && StrokeMath.hash01(s, 7) >= PLAIN) tries.addAll(deadpan(w, plain, s));
        tries.add(titled(w));
        tries.add(titled(plain));
        String first = null;
        for (String name : tries) if (name.length() <= Brush.NAME_MAX && twoLines(name)) {
            if (!taken.test(name)) return name;
            if (first == null) first = name;
        }
        String last = titled(plain.size() < 3 ? plain : plain.subList(1, 3));
        return first == null || !taken.test(last) ? last : first;
    }
    /** About one name in five is the plain words. */
    static final double PLAIN = .2;
    /** {@code words} with spaces, each capitalized: names are Title Case (the owner, 2026-10-29). */
    private static String titled(List<String> words) {
        List<String> out = new ArrayList<>();
        for (String w : words) out.add(cap(w));
        return String.join(" ", out);
    }
    /**
     * The deadpan forms for {@code w} (fun, describer, part; {@code plain} before {@link #nonsense}), from traits.txt's
     * [deadpan], the one hash {@code s} picks first, then the next ones round: "Polite Smear", "Regrettable Inky Hake",
     * "Dusty Aunt Charcoal", "Inky Pen Junior", "Smear of Mild Regret", "Wash for Beginners", "Inky Pen Situation",
     * "Budget Dusty Charcoal". Each says the part; a made-up describer leaves out the forms without it. None missing a
     * word. parts.js deadpanForms.
     */
    static List<String> deadpan(List<String> w, List<String> plain, int s) {
        List<String> out = new ArrayList<>();
        Map<String, List<String>> d = traits().get(DEADPAN);
        if (d == null) return out;
        String describer = w.get(1), part = w.get(2), manner = word(d.get("manner"), s, 13);
        boolean madeUp = !describer.equals(plain.get(1));
        String[] forms = {
                madeUp ? null : join(manner, " ", part),
                join(manner, " ", describer, " ", part),
                join(describer, " ", word(d.get("relative"), s, 14), " ", part),
                join(describer, " ", part, " ", word(d.get("edition"), s, 17)),
                madeUp ? null : join(part, " of ", word(d.get("mild"), s, 15), " ", word(d.get("concern"), s, 16)),
                madeUp ? null : join(part, " for Beginners"),
                join(describer, " ", part, " Situation"),
                join("Budget ", describer, " ", part)};
        int at = (int) (StrokeMath.hash01(s, 12) * forms.length);
        for (int i = 0; i < forms.length; i++) {
            int k = (at + i) % forms.length;
            // The two short forms only when picked: else they'd stand in for every long name that won't fit.
            if (forms[k] != null && (i == 0 || k != 0 && k != 5)) out.add(forms[k]);
        }
        return out;
    }
    /** The letters a line of the narrowest card holds (Tools page and library cards, 142 dp at 13 sp). */
    static final int LINE_LETTERS = 12;
    /**
     * Whether {@code name} wraps to at most two lines of {@link #LINE_LETTERS} letters as a card wraps it
     * ({@link LabText#wrap}: at spaces, and after a hyphen inside a word too long), no piece too long. parts.js twoLines.
     */
    static boolean twoLines(String name) {
        List<String> pieces = new ArrayList<>();
        for (String word : name.trim().split(" +")) {
            while (word.length() > LINE_LETTERS) {
                int cut = -1;
                for (int i = word.indexOf('-', 1); i > 0 && i < word.length() - 1; i = word.indexOf('-', i + 1))
                    if (cut < 0 || i + 1 <= LINE_LETTERS) cut = i;
                if (cut < 0) break;
                pieces.add(word.substring(0, cut + 1));
                word = word.substring(cut + 1);
            }
            pieces.add(word);
        }
        int lines = 0, line = -1;
        for (String p : pieces) {
            if (p.length() > LINE_LETTERS) return false;
            if (line >= 0 && line + 1 + p.length() <= LINE_LETTERS) { line += 1 + p.length(); continue; }
            lines++;
            line = p.length();
        }
        return lines <= 2;
    }
    /**
     * Its name's words: from each of traits.txt's sections in name order (fun, describer, part) the first line {@code b}
     * has the key of ({@link #traitKeys}; most telling first, as {@link BrushVoice}'s opener goes) and one of that
     * line's words picked by the seed, maybe one made up ({@link #nonsense}). The part first; no word echoes another (the next of the line's words, or the
     * next line). A section with nothing for it is left out.
     */
    static List<String> words(ModularBrush b) {
        List<String> w = plainWords(b);
        return w.size() == 3 ? nonsense(w, (int) b.seed) : w;
    }
    /** {@link #words} before any {@link #nonsense}. */
    static List<String> plainWords(ModularBrush b) {
        Set<String> has = traitKeys(b);
        String part = pick(PART, has, b.seed, 0), describer = pick(DESCRIBER, has, b.seed, 1, part), fun = pick(FUN, has, b.seed, 2, part, describer);
        List<String> out = new ArrayList<>();
        for (String w : new String[]{fun, describer, part}) if (w != null) out.add(w);
        return out;
    }
    /**
     * From traits.txt's {@code section}, the first line {@code has} the key of, one of its words picked by {@code seed}
     * (hash {@code n}), the next where it {@link #echoes} one of {@code not}; null if none.
     */
    private static String pick(String section, Set<String> has, long seed, int n, String... not) {
        List<String> all = picks(section, has, seed, n, not);
        return all.isEmpty() ? null : all.get(0);
    }
    /** {@link #pick}'s word and the rest of its line after it, in the seed's order: the next to try where it won't fit. */
    private static List<String> picks(String section, Set<String> has, long seed, int n, String... not) {
        List<String> out = new ArrayList<>();
        Map<String, List<String>> lines = traits().get(section);
        if (lines == null) return out;
        for (Map.Entry<String, List<String>> e : lines.entrySet()) {
            if (!has.contains(e.getKey())) continue;
            List<String> line = e.getValue();
            int at = (int) (StrokeMath.hash01((int) seed ^ TRAIT_SALT, n) * line.size());
            for (int i = 0; i < line.size(); i++) {
                String w = line.get((at + i) % line.size());
                boolean echo = false;
                for (String t : not) echo |= echoes(w, t);
                if (!echo) out.add(w);
            }
            if (!out.isEmpty()) return out;
        }
        return out;
    }
    /** Whether two words start alike (their first four letters, or all of the shorter), as "washy wash" would. */
    static boolean echoes(String a, String b) {
        if (a == null || b == null) return false;
        int k = Math.min(4, Math.min(a.length(), b.length()));
        return a.regionMatches(0, b, 0, k);
    }

    // ---- Nonsense ----

    /** About one name in six has a made-up word (the owner, 2026-10-29: mostly deadpan, now and then an Edward Lear word). */
    static final double NONSENSE = 1 / 6.0;
    /** A made-up word's most letters, so it stays readable and fits a card's line. */
    static final int NONSENSE_MOST = 12;
    /**
     * {@code words} (fun, describer, part), maybe one made up, from {@code seed}: about {@link #NONSENSE} of names. A -y or
     * -ish describer's {@link #stem} takes an ending from [deadpan]'s nonsense line ("wobbly" to "wobblefuzz", "inky" to
     * "inkery"), else the part does ("smudge" to "smudgekins"), so it still says what the brush does; none past
     * {@link #NONSENSE_MOST} letters (the next ending, else the words as they are). parts.js nonsenseName.
     */
    static List<String> nonsense(List<String> words, int seed) {
        int s = seed ^ TRAIT_SALT;
        Map<String, List<String>> d = traits().get(DEADPAN);
        List<String> ends = d == null ? null : d.get("nonsense");
        if (ends == null || StrokeMath.hash01(s, 3) >= NONSENSE) return words;
        String describer = words.get(1), part = words.get(2), stem = stem(describer);
        int at = (int) (StrokeMath.hash01(s, 6) * ends.size());
        String base = stem != null ? stem : part;
        for (int i = 0; i < ends.size(); i++) {
            String end = ends.get((at + i) % ends.size()), w = suffixed(base, end);
            // Not the word twice ("fuzzfuzz").
            if (w.length() > NONSENSE_MOST || echoes(base, end)) continue;
            return stem != null ? Arrays.asList(words.get(0), w, part) : Arrays.asList(words.get(0), describer, w);
        }
        return words;
    }
    private static boolean vowel(char c) { return "aeiou".indexOf(c) >= 0; }
    /**
     * A -y or -ish word's stem, readable: "dusty" dust, "inkish" ink, "wobbly" wobble, "drippy" drip, "shaky" shake,
     * "ghostly" ghost; null for any other word or a stem under three letters.
     */
    static String stem(String w) {
        String s = w.endsWith("ish") ? w.substring(0, w.length() - 3) : w.endsWith("y") ? w.substring(0, w.length() - 1) : null;
        if (s == null || s.length() < 3) return null;
        int n = s.length();
        char last = s.charAt(n - 1), before = s.charAt(n - 2);
        if (last == before && "bdgmnpt".indexOf(last) >= 0) return s.substring(0, n - 1);
        if (last == 'l' && !vowel(before) && before != 'l') return s.endsWith("stl") ? s.substring(0, n - 1) : s + "e";
        if (last == 'v' || last == 'k' && vowel(before) && n >= 3 && !vowel(s.charAt(n - 3))) return s + "e";
        return s;
    }
    /** {@code w} with {@code suffix}: a final e goes before a vowel ("smudgery"), a short word's last consonant doubles before one ("draggy", "drippery"). */
    static String suffixed(String w, String suffix) {
        char first = suffix.charAt(0);
        boolean soft = vowel(first) || first == 'y';
        int n = w.length();
        if (soft && w.endsWith("e")) return w.substring(0, n - 1) + suffix;
        if (soft && n <= 4 && n >= 3 && !vowel(w.charAt(n - 1)) && "wxy".indexOf(w.charAt(n - 1)) < 0 && vowel(w.charAt(n - 2)) && !vowel(w.charAt(n - 3)))
            return w + w.charAt(n - 1) + suffix;
        return w + suffix;
    }
    /**
     * What {@code b} has that a name can say, as traits.txt keys it: {@code part.choice} by the codes' keys, or a part's
     * dial; {@code any} always. Its medium and any tip but Round always; a smudge or a drying only when it isn't the
     * style's usual ({@link ModularWords#usual}: what most of its rolls have, so Oils' Drag says nothing), a paper only
     * with a strong grain; a Smear at Soften .5 and over as {@code carry.stump} or {@code carry.comb} (as its caption
     * says), Resist as {@code medium.wax} under .5, Crayon's Scratch as {@code medium.scratch}; Blend
     * ({@code blend.white}), gravity's dials past .15 ({@code life.drips}), a taper ({@code tip.taper}), and the marks
     * "This brush" says at the strengths it says them ({@code medium.bloom}, {@code tip.flick}).
     */
    static Set<String> traitKeys(ModularBrush b) {
        Set<String> keys = new HashSet<>();
        keys.add("any");
        ModularStyle style = b.style;
        ModularBrush.Part t = b.part(BrushPart.TIP), m = b.part(BrushPart.MEDIUM), paper = b.part(BrushPart.PAPER), life = b.part(BrushPart.LIFE);
        PartChoice carry = b.choice(BrushPart.CARRY);
        if (t.choice != PartChoice.ROUND) keys.add("tip." + t.choice.key);
        if (t.get(PartDial.TAPER_IN, 0) + t.get(PartDial.TAPER_OUT, 0) >= .9) keys.add("tip.taper");
        keys.add("medium." + m.choice.key);
        if (m.choice == PartChoice.RESIST && ModularWords.wax(b)) keys.add("medium.wax");
        if (m.choice == PartChoice.CRAYON && (int) m.get(PartDial.SCRATCH, 0) == 1) keys.add("medium.scratch");
        if (m.choice != PartChoice.RESIST) {
            int blend = (int) m.get(PartDial.M_BLEND, 0);
            if (blend > 0) keys.add("blend." + BLEND_KEYS[blend]);
        }
        if (m.get(PartDial.DUST, 0) > .15) keys.add("medium.dust");
        if ((int) m.get(PartDial.SCREEN, 0) > 0) keys.add("medium.screen");
        for (Object[] mark : MARKS) {
            PartDial d = (PartDial) mark[0];
            if (b.part(d.part).get(d, 0) >= (Double) mark[1]) keys.add(d.part.name().toLowerCase(Locale.ROOT) + "." + d.key);
        }
        if (ModularWords.stump(b)) keys.add(ModularWords.combs(b) ? "carry.comb" : "carry.stump");
        else if (carry != PartChoice.OFF && carry != ModularWords.usual(style, BrushPart.CARRY)) keys.add("carry." + carry.key);
        if (ModularWords.runsOut(b)) keys.add("carry.pload");
        if (paper.choice != PartChoice.PLAIN && paper.choice != ModularWords.usual(style, BrushPart.PAPER) && paper.get(PartDial.TOOTH) >= STRONG_TOOTH)
            keys.add("paper." + paper.choice.key);
        if (life.choice != ModularWords.usual(style, BrushPart.LIFE)) keys.add("life." + life.choice.key);
        for (PartDial d : PartDial.GRAVITY) if (life.get(d, 0) > .15) keys.add("life." + d.key);
        return keys;
    }
    /** The marks a name can say and from what strength: "This brush"'s ({@link ModularWords#plainWords}); parts.js TRAIT_MARKS. */
    private static final Object[][] MARKS = {
            {PartDial.BLOOM, .3}, {PartDial.BACKRUN, .3}, {PartDial.BLEED, .35}, {PartDial.SPLATTER, .3}, {PartDial.IMPASTO, .3},
            {PartDial.POOL, .35}, {PartDial.FLING, .05}, {PartDial.FLICK, .05}, {PartDial.DRY, .3}, {PartDial.WOBBLE, .25}};
    /** Every word traits.txt gives a brush (its notebook titles' sections left out). */
    static Set<String> traitWords() {
        Set<String> words = new HashSet<>();
        for (Map.Entry<String, Map<String, List<String>>> section : traits().entrySet())
            if (!TITLE_SECTIONS.contains(section.getKey())) for (List<String> w : section.getValue().values()) words.addAll(w);
        return words;
    }
    /** A word of {@code list} from {@code seed}, other than {@code not} when the list has another. */
    private static String other(List<String> list, String not, long seed) {
        int i = (int) (StrokeMath.hash01((int) seed ^ KIN_SALT, 0) * list.size());
        return list.get(list.get(i).equals(not) ? (i + 1) % list.size() : i);
    }

    // ---- Notebook titles ----

    /** traits.txt's section of words not keyed by what a brush has ({@link #deadpan}, {@link #nonsense}), and a new notebook's title's. */
    static final String DEADPAN = "deadpan", TITLE = "title";
    static final List<String> TITLE_SECTIONS = Collections.singletonList(TITLE);
    private static final int TITLE_SALT = 0x7b0c5;
    /**
     * A new notebook's titles from {@code seed}, deadpan (the owner, 2026-10-29: "mostly deadpan"; "not too bad, the name of
     * the app is tongue in cheek"), from traits.txt's [title]: "Several Opinions About Soup", "A Brief Inventory of Lint",
     * "Notes Toward a Better Spoon", "The Pamphlet of Mild Concerns", "Doodles for a Goose", "Mistakes Volume Three",
     * "Probably Fine", "The Lukewarm Pamphlet", "Spoons I Have Known", "In Defence of Fog", "Advice from a Puffin",
     * "Minutes of the Pigeon Committee", "Field Notes on Scones", "Letters to a Walrus"; about {@link #NONSENSE} one with a made-up word ({@link #lear}: "The Flimwhistle
     * Papers", "Doodles for a Pobblewick", "Wobblefuzz and Other Concerns"). The seed's form first, then the plain forms
     * shortest first, for the caller to take the first that fits; a form missing a word is left out (none without [title]).
     */
    static List<String> titles(long seed) {
        Map<String, List<String>> t = traits().get(TITLE);
        List<String> out = new ArrayList<>();
        if (t == null) return out;
        int s = (int) (seed ^ (seed >>> 32)) ^ TITLE_SALT;
        String things = word(t.get("things"), s, 1), creature = word(t.get("creature"), s, 3), adj = word(t.get("adj"), s, 4), book = word(t.get("book"), s, 5), lear = lear(t, s);
        String[] plain = {
                join("Several Opinions About ", things),
                join("A Brief Inventory of ", things),
                join("Notes Toward a Better ", word(t.get("thing"), s, 2)),
                join("The ", book, " of ", adj, " Concerns"),
                join("Doodles for a ", creature),
                join("Mistakes Volume ", word(t.get("number"), s, 6)),
                join("Probably ", word(t.get("verdict"), s, 7)),
                join("The ", adj, " ", book),
                join(things, " I Have Known"),
                join("In Defence of ", things),
                join("Advice from a ", creature),
                join("Minutes of the ", creature, " Committee"),
                join("Field Notes on ", things),
                join("Letters to a ", creature)};
        String[] odd = {join("The ", lear, " Papers"), join("Doodles for a ", lear), join(lear, " and Other Concerns")};
        String[] forms = StrokeMath.hash01(s, 8) < NONSENSE ? odd : plain;
        String first = forms[(int) (StrokeMath.hash01(s, 9) * forms.length)];
        if (first != null) out.add(first);
        List<String> rest = new ArrayList<>();
        for (String f : plain) if (f != null && !f.equals(first)) rest.add(f);
        rest.sort((a, b) -> a.length() - b.length());
        out.addAll(rest);
        return out;
    }
    /** A word of {@code line} from hash {@code n}; null without the line. */
    private static String word(List<String> line, int s, int n) { return line == null ? null : line.get((int) (StrokeMath.hash01(s, n) * line.size())); }
    /** Its words run together, each capitalized (but " of " and its like); null when one is missing. */
    private static String join(String... words) {
        StringBuilder b = new StringBuilder();
        for (String w : words) { if (w == null) return null; b.append(w.startsWith(" ") ? w : cap(w)); }
        return b.toString();
    }
    private static String cap(String w) { return Character.toUpperCase(w.charAt(0)) + w.substring(1); }
    /** A made-up word, Edward Lear's way: a [title] lear word with a [deadpan] nonsense ending ("flimwhistle", "pobblewick"); null without either. */
    private static String lear(Map<String, List<String>> t, int s) {
        Map<String, List<String>> d = traits().get(DEADPAN);
        String front = word(t.get("lear"), s, 10), end = d == null ? null : word(d.get("nonsense"), s, 11);
        return front == null || end == null ? null : suffixed(front, end);
    }

    // ---- The list files ----

    /** A list file's names (see {@link #valid}); {@code defaults} if it is missing. */
    static List<String> load(String path, String[] defaults) { return valid(lines(path), defaults); }
    /** A list file's lines; none if it is missing. */
    private static List<String> lines(String path) {
        try { return Assets.lines(path); } catch (IOException e) { return Collections.emptyList(); }
    }
    /** The names in {@code lines} naming can use; {@code defaults} if none is left. */
    static List<String> valid(List<String> lines, String[] defaults) {
        List<String> names = new ArrayList<>();
        for (String line : lines) {
            String name = line.trim();
            if (!name.isEmpty()) names.add(name);
        }
        return Collections.unmodifiableList(names.isEmpty() ? Arrays.asList(defaults) : names);
    }
    /**
     * A traits list's sections, each its lines in the file's order (most telling first), a key and its words: a key, a
     * colon and lowercase words split by spaces ("paper.cold press: grainy toothy"). A line "[fun]" starts a section
     * (lines before any go in [part]); lines starting with # are notes; a word that isn't lowercase letters, a line
     * left with none and a key said twice in a section (the first stands) are left out, as are empty sections.
     */
    static Map<String, Map<String, List<String>>> traits(List<String> lines) {
        Map<String, Map<String, List<String>>> out = new LinkedHashMap<>();
        String section = PART;
        for (String raw : lines) {
            String line = raw.replace('\t', ' ').trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (line.matches("\\[[a-z]+\\]")) { section = line.substring(1, line.length() - 1); continue; }
            int cut = line.indexOf(':');
            if (cut <= 0) continue;
            String key = line.substring(0, cut).trim().replaceAll("\\s+", " ");
            List<String> words = new ArrayList<>();
            for (String w : line.substring(cut + 1).trim().split("\\s+")) if (w.matches("[a-z]+") && !words.contains(w)) words.add(w);
            Map<String, List<String>> s = out.get(section);
            if (s == null) out.put(section, s = new LinkedHashMap<>());
            if (!key.isEmpty() && !words.isEmpty() && !s.containsKey(key)) s.put(key, Collections.unmodifiableList(words));
        }
        Map<String, Map<String, List<String>>> done = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, List<String>>> e : out.entrySet()) if (!e.getValue().isEmpty()) done.put(e.getKey(), Collections.unmodifiableMap(e.getValue()));
        return Collections.unmodifiableMap(done);
    }
}
