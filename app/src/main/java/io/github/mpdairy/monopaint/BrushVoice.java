package io.github.mpdairy.monopaint;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A brush in friendly words (the owner, 2026-10-22: "explain what makes the brush unique, what to expect", "complete
 * sentences", "human sounding", "don't use semicolons"). {@link ModularWords#plainWords} keeps the facts, short and
 * checked ("Ink line. Dries at once. Speed thins, pressure widens."); {@link #say} says them as a person would, in
 * complete sentences, what matters most first ("It dries as soon as you lift the pen."), at most {@link #MOST_SENTENCES}.
 * A Basic tool also has its own personality sentence, hand-written in assets/brushes/personalities.txt
 * ({@link #personality}), said before them ({@link #about}). The web lab's voice.js says the same with the same
 * {@link #RULES} (VoiceChecks). Pure, checked on the desktop.
 */
final class BrushVoice {
    private BrushVoice() { }

    /** The most sentences {@link #say} gives; and the most characters it gives. */
    static final int MOST_SENTENCES = 4, MOST = 230;
    /** A personality's most sentences, and a sentence's most characters (the Tools page shows a whole first sentence). */
    static final int PERSONALITY_SENTENCES = 2, SENTENCE_MOST = ToolsLayout.FIRST_SENTENCE;
    /** An opener's most characters: with the longest behaviour sentence after it, Brush Lab's two first sentences fit ({@link LabLayout#TWO_SENTENCES}). */
    static final int OPENER_MOST = 70;
    /** {@link #about}'s most characters: Brush Lab's THIS BRUSH box budgets this ({@link LabLayout#DESCRIPTION}). */
    static final int ABOUT_MOST = 260;
    /** The owner's file of personality sentences, one Basic tool a line: "Wet gouache: Thick paint that stays wet…". */
    static final String PERSONALITIES = "brushes/personalities.txt";

    /** What each rule's words come first by: what makes it unique, wet, smudge, hand, marks, the rest (lower first). */
    static final int NOUN = 0;

    /**
     * The facts' sentences (whole, or each comma-part) in friendly words: rank (what comes first, lower first), kind (S
     * a whole sentence, P what "It" does, joined with another P from the same fact: "It tapers to a fine point and
     * catches the paper's grain."), the fact as a pattern (case-blind on its first letter), the words ($1 its first
     * group; empty: left unsaid), and a tag so two rules saying the same thing say it once. The web lab's voice.js
     * carries these rows as they are (VoiceChecks).
     */
    static final String[][] RULES = {
            // What makes it unique: its tip, medium or drying's own trick.
            {"1", "S", "Builds up where it lingers", "Dots pile up where you linger.", ""},
            {"1", "S", "Stamps a shape cut from the page", "It stamps a shape you cut from the page.", ""},
            {"1", "S", "Fades as it prints", "Each print comes out a little fainter.", ""},
            {"1", "S", "Builds up, resists washes", "The wax builds up in layers and pushes washes away.", ""},
            {"1", "S", "Builds up", "The wax builds up in layers.", ""},
            {"1", "S", "Scrapes back to white", "It scrapes back through to white paper.", ""},
            {"1", "S", "Loops along the line", "It draws toy loops along your line.", ""},
            {"1", "S", "Loops round a ring pulled out from the start", "It loops around a ring you pull out from where you start.", ""},
            {"1", "S", "Crosses where gone over or held", "Go back over a spot, or hold still, and the hatching crosses.", ""},
            {"1", "S", "Greys shift each stroke at random", "Each stroke picks a new grey at random.", ""},
            {"1", "S", "Greys shift each stroke, shuffled", "Each stroke takes the next grey from a shuffled set.", ""},
            {"1", "S", "Greys shift each stroke, there and back", "Each stroke steps to the next grey, then back again.", ""},
            {"1", "S", "Greys shift each stroke, in order", "Each stroke steps to the next grey in order.", ""},
            {"1", "S", "Fades to paper over (.+) cm", "It fades out to bare paper over $1 cm.", ""},
            {"1", "S", "Shifts to (.+) over (.+) cm", "It shifts to $1 over $2 cm.", ""},
            {"1", "S", "Cycles through its greys every (.+) cm", "It cycles through its greys every $1 cm.", ""},
            {"1", "S", "Flicks on a quick lift, flings drops on a hard stop", "Lift quickly and it flicks. Stop hard and it flings drops.", ""},
            {"1", "S", "Flicks on a quick lift", "Lift quickly and it flicks.", ""},
            {"1", "S", "Flings drops on a hard stop", "Stop hard and it flings drops.", ""},
            {"1", "S", "Nothing takes on it", "Nothing painted on top will stick to it.", ""},
            {"1", "S", "Nothing takes on it until rubbed off", "Nothing sticks to it until you rub it off with the eraser.", ""},
            {"1", "S", "Scrapes off in streaks", "The eraser scrapes it off in streaks.", ""},
            {"1", "S", "Goes behind marks", "It slips behind the marks already there.", ""},
            {"1", "S", "Inverts what's under it", "It flips the greys under it, dark to light.", ""},
            {"1", "S", "Taps drop clear rings, dragging combs them", "Tap to drop clear rings, then drag to comb them.", ""},
            {"1", "S", "Taps drop clear rings, dragging combs them a little", "Tap to drop clear rings, then drag to comb them a little.", ""},
            {"1", "S", "Floats (.+) s", "It floats on the water for $s.", ""},
            {"1", "S", "Taps drop rings, dragging combs them", "Tap to drop rings of ink, then drag to comb them.", ""},
            {"1", "S", "Taps drop rings", "Tap to drop rings of ink.", ""},
            {"1", "S", "Veins bridge nearby marks for (.+) s", "Mold veins grow out to bridge nearby marks for $s.", ""},
            {"1", "S", "Fades away in (.+) s", "It fades away after $s.", ""},
            {"1", "S", "Darkens in as it dries", "It goes on faint and darkens as it dries.", ""},
            {"1", "S", "Holds ink between strokes", "It holds its ink between strokes.", ""},
            {"1", "S", "Tap a grey to dip", "Tap a grey to dip it in fresh ink.", ""},
            {"1", "S", "Runs out of paint, then only smudges", "It runs out of paint as you go, then only smudges.", ""},
            {"1", "S", "Rub a grey to load", "Rub a grey to load it up again.", ""},
            {"1", "S", "Stays inside ink lines", "It stays inside your ink lines.", ""},
            {"1", "S", "Floods inside ink lines", "It floods up to your ink lines.", ""},
            {"1", "S", "Fills the shape it starts in", "It fills the whole shape it starts in.", ""},
            {"1", "S", "Fills the shape it outlines", "When you lift, it closes the outline and fills it in.", ""},
            // Wet: how long, how it mixes, what the page's tilt does.
            {"2", "S", "Stays wet until dried", "It stays wet until you tap the drop to dry it.", ""},
            {"2", "S", "Merges while wet, blooms when damp", "Wet strokes mix together and bloom as they dry.", "bloom"},
            {"2", "S", "Merges while wet", "Wet strokes mix together.", ""},
            {"2", "S", "Wet for (.+) s", "It stays wet for $s.", ""},
            {"2", "S", "(runs|drips|pools|granulates|blends when rocked)( and (runs|drips|pools|granulates|blends when rocked))? when tilted", "@tilt", "tilt"},
            // The smudge: what it does to what is there.
            {"2", "S", "Lifts paint, lets it run", "It lifts paint and lets it run.", ""},
            {"2", "S", "Lifts paint", "It lifts paint off the page.", ""},
            {"2", "S", "Drags wet paint", "It drags wet paint along with it.", ""},
            {"2", "S", "Smears", "It smears whatever it passes over.", ""},
            {"2", "S", "Softens", "It softens marks without adding any paint.", ""},
            {"2", "S", "Combs", "It combs through wet paint.", ""},
            {"2", "S", "Keeps paint for the next stroke", "It keeps some paint for the next stroke.", ""},
            // Its marks, the two strongest, and a print's newsprint.
            {"4", "P", "tapers to a point", "tapers to a fine point", ""},
            {"4", "P", "dry-brush texture", "has a scratchy, dry-brush texture", ""},
            {"4", "P", "dry-brush streaks", "leaves streaky brush marks", ""},
            {"4", "P", "slight wobble", "wobbles a little", ""},
            {"4", "P", "dark edges", "dries with dark edges", ""},
            {"4", "P", "pools when slow", "pools where you slow down", ""},
            {"4", "P", "spatters on flicks", "spatters when you flick", ""},
            {"4", "P", "blooms", "blooms as it dries", "bloom"},
            {"4", "P", "backruns", "leaves pale backruns", ""},
            {"4", "P", "bleeds", "bleeds softly into the paper", ""},
            {"4", "P", "raised ridges", "builds up raised ridges", ""},
            {"4", "P", "dusty broken edge", "has a dusty, broken edge", ""},
            {"4", "P", "soft speckled edge", "has a soft, speckled edge", ""},
            {"4", "P", "line shifted", "lands a little off the line", ""},
            {"4", "P", "broken line", "breaks up here and there", ""},
            {"4", "P", "opens into two lines", "opens into two lines", ""},
            {"4", "P", "splits into strands", "splits into strands", ""},
            {"4", "P", "runs dry quickly", "runs dry quickly", ""},
            {"4", "P", "runs dry on long strokes", "runs dry on long strokes", ""},
            {"4", "P", "picks up the texture", "catches the paper's grain", ""},
            {"4", "S", "sheds dust when tilted", "Tilt the page and dust falls off it.", ""},
            {"4", "P", "doubled", "prints a little doubled", ""},
            {"4", "P", "dots swell", "prints fat, swollen dots", ""},
            {"4", "P", "patchy ink", "prints with patchy ink", ""},
            // The rest: drying at once, and an Erasable that isn't the medium's own (fully erasable goes unsaid).
            {"5", "S", "Dries at once", "It dries as soon as you lift the pen.", ""},
            {"5", "S", "Permanent", "The eraser can't touch it.", ""},
            {"5", "S", "Part erasable", "The eraser only lightens it.", ""},
            {"5", "S", "Erasable", "", ""},
    };
    /** How the hand answers: rank 3, between the smudge and the marks. */
    static final int HAND = 3;
    /** A brush with nothing to say past what it is: Control steady, nothing unusual. */
    static final String STEADY = "It stays the same however hard you press or how fast you go.";

    private static final Pattern[] PATTERNS = new Pattern[RULES.length];
    static {
        for (int i = 0; i < RULES.length; i++) PATTERNS[i] = Pattern.compile("^" + RULES[i][2] + "$");
    }

    /** One thing to say: its rank, the order it came in, and its words (a P without "It", or whole sentences). */
    private static final class Said {
        final int rank, order;
        final String words, tag;
        /** What "It" does, and each one's tag, for words said as "It … and …." once the tags said before are left out. */
        final List<String> does, tags;
        Said(int rank, int order, String words, String tag) { this(rank, order, words, tag, null, null); }
        Said(int rank, int order, String words, String tag, List<String> does, List<String> tags) {
            this.rank = rank; this.order = order; this.words = words; this.tag = tag; this.does = does; this.tags = tags;
        }
    }

    /** Facts {@link #say} found no rule for (each once), for the checks: every fact a brush can have has words. */
    static final Set<String> MISSED = Collections.synchronizedSet(new HashSet<>());

    /**
     * {@code facts} ({@link ModularWords#plainWords}) in friendly sentences, what matters most first, at most
     * {@link #MOST_SENTENCES} and {@link #MOST} characters (always the first); {@code noun}: start with what it is
     * ("It's an ink line with a chisel tip."), as a brush without a personality does.
     */
    static String say(String facts, boolean noun) {
        List<String> sentences = ModularWords.sentences(facts);
        List<Said> all = new ArrayList<>();
        if (noun && !sentences.isEmpty()) all.add(new Said(NOUN, 0, nounSentence(strip(sentences.get(0))), ""));
        for (int i = 1; i < sentences.size(); i++) {
            String s = strip(sentences.get(i));
            if (HAND_START.matcher(s).find()) { all.add(new Said(HAND, all.size(), hand(s), "")); continue; }
            // A whole fact first ("Runs out of paint, then only smudges"), then its comma-parts.
            String[] whole = s.contains(", ") ? rule(s) : null;
            if (whole != null) { if (!whole[1].isEmpty()) all.add(new Said(Integer.parseInt(whole[0]), all.size(), whole[1], whole[3])); continue; }
            // What "It" does, two joined in one sentence ("It has a soft, speckled edge and catches the paper's grain.").
            List<String> does = new ArrayList<>(), doTags = new ArrayList<>();
            int rank = 9;
            for (String part : s.split(", ")) {
                String[] r = rule(part);
                if (r == null) { MISSED.add(part); r = new String[]{"4", "It " + lower1(part) + ".", "S", ""}; }
                if (r[1].isEmpty()) continue;
                if (r[2].equals("P")) { does.add(r[1]); doTags.add(r[3]); rank = Math.min(rank, Integer.parseInt(r[0])); }
                else all.add(new Said(Integer.parseInt(r[0]), all.size(), r[1], r[3]));
            }
            if (!does.isEmpty()) all.add(new Said(rank, all.size(), null, "", does, doTags));
        }
        // Nothing past what it is: Control is steady, the drying Quick, nothing unusual. Say so.
        if (all.size() == (noun && !sentences.isEmpty() ? 1 : 0)) all.add(new Said(HAND, all.size(), STEADY, ""));
        // What matters most first, a thing said once (by what says it first); then while sentences and characters allow.
        List<Said> order = new ArrayList<>(all);
        Collections.sort(order, (a, b) -> a.rank != b.rank ? Integer.compare(a.rank, b.rank) : Integer.compare(a.order, b.order));
        Set<String> tags = new HashSet<>();
        StringBuilder out = new StringBuilder();
        int count = 0;
        for (Said s : order) {
            if (!s.tag.isEmpty() && !tags.add(s.tag)) continue;
            String words = s.words;
            if (s.does != null) {
                List<String> left = new ArrayList<>();
                for (int k = 0; k < s.does.size(); k++) if (s.tags.get(k).isEmpty() || tags.add(s.tags.get(k))) left.add(s.does.get(k));
                if (left.isEmpty()) continue;
                words = "It " + String.join(" and ", left) + ".";
            }
            int n = ModularWords.sentences(words).size();
            if (count > 0 && (count + n > MOST_SENTENCES || out.length() + 1 + words.length() > MOST)) continue;
            out.append(out.length() > 0 ? " " : "").append(words);
            count += n;
        }
        return out.toString();
    }
    /** The rule {@code fact} meets, as {rank, its words, kind, tag}; null for none. */
    private static String[] rule(String fact) {
        String f = lower1(fact);
        for (int i = 0; i < RULES.length; i++) {
            Matcher m = PATTERNS[i].matcher(f);
            if (!m.matches()) m = PATTERNS[i].matcher(fact);
            if (!m.matches()) continue;
            String words = RULES[i][3];
            if (words.equals("@tilt")) words = tilt(m.group(1), m.group(3));
            else if (words.contains("$s")) words = words.replace("$s", seconds(m.group(1)));
            else for (int g = m.groupCount(); g >= 1; g--) words = words.replace("$" + g, m.group(g) == null ? "" : m.group(g));
            return new String[]{RULES[i][0], words, RULES[i][1], RULES[i][4]};
        }
        return null;
    }
    /** "about 6 seconds", "1 second": a time the facts give in seconds ("5.6"), to the nearest whole one. */
    static String seconds(String s) {
        long n = Math.max(1, Math.round(Double.parseDouble(s)));
        return (n < 10 ? "about " : "") + n + (n == 1 ? " second" : " seconds");
    }
    /** What the page's tilt does to it ("runs and pools when tilted"): "Tilt the page and it runs and pools downhill." */
    private static String tilt(String first, String second) {
        String a = TILT_WORDS.get(first), b = second == null ? null : TILT_WORDS.get(second);
        String last = second == null ? first : second;
        boolean downhill = last.equals("runs") || last.equals("drips") || last.equals("pools");
        return "Tilt the page and it " + a + (b != null ? " and " + b : "") + (downhill ? " downhill" : "") + ".";
    }
    private static final Map<String, String> TILT_WORDS = new HashMap<>();
    static {
        TILT_WORDS.put("runs", "runs"); TILT_WORDS.put("drips", "drips"); TILT_WORDS.put("pools", "pools");
        TILT_WORDS.put("granulates", "settles into grain"); TILT_WORDS.put("blends when rocked", "blends as you rock it");
    }

    // What it is

    /** The tips as the facts name them, and as a sentence does. */
    private static final Pattern NOUN_PARTS = Pattern.compile("^(.+?)(?:, (chisel tip|flat nib|hake|split nib|\\d+-line rake|bristle brush|filbert|round tip))?,?(?: on (.+))?$");
    /** Nouns that take "a" or "an" (the rest are stuff: graphite, gouache, water, screentone). */
    private static final Pattern COUNTED = Pattern.compile("(line|wash|stamp|spirograph|scratch|pencil|pastel|crayon)$|^stick of");
    /** What the facts' noun says it is, friendlier: "Vine charcoal" a stick of it, "HB graphite" an HB pencil; with "a" or "an" where it takes one. */
    static String nounPhrase(String noun) {
        String n = noun.replaceAll(" stipple$", " stippling").replaceAll(" spiro$", " spirograph")
                .replaceAll("^(Vine|Compressed) charcoal$", "stick of $1 charcoal");
        // A pencil by its grade, in capitals ("Mezzotint B pencil", whatever case the facts give it).
        Matcher grade = Pattern.compile("^(.*?)(\\S+) graphite$").matcher(n);
        if (grade.matches()) n = grade.group(1) + (grade.group(2).matches("(?i)\\d*[bhf]+") ? grade.group(2).toUpperCase(Locale.ROOT) : grade.group(2)) + " pencil";
        n = lowerNoun(n);
        boolean counted = COUNTED.matcher(n).find() && !n.endsWith(" and wash");
        boolean an = "aeiou8".indexOf(n.charAt(0)) >= 0 || n.matches("^[FHLMNRSX][A-Z0-9 ].*");
        return (!counted ? "" : an ? "an " : "a ") + n.replace("stick of Vine", "stick of vine").replace("stick of Compressed", "stick of compressed");
    }
    /**
     * The facts' first sentence ("Ink line, chisel tip, on felt paper") as a sentence: "It's an ink line with a chisel
     * tip, on felt paper."
     */
    static String nounSentence(String fact) {
        Matcher m = NOUN_PARTS.matcher(fact);
        if (!m.matches()) { MISSED.add(fact); return "It's " + lower1(fact) + "."; }
        String tip = m.group(2) == null ? "" : " with a " + m.group(2);
        String on = m.group(3) == null ? "" : (tip.isEmpty() ? "" : ",") + " on " + m.group(3);
        return "It's " + nounPhrase(m.group(1)) + tip + on + ".";
    }
    /** A noun as a sentence's middle has it: "Wet wash" → "wet wash"; "HB graphite", "2B graphite", "Ben-Day ink wash" as they are. */
    private static String lowerNoun(String noun) {
        String first = noun.split(" ")[0];
        if (first.equals("Ben-Day") || first.equals(first.toUpperCase(Locale.ROOT))) return noun;
        return lower1(noun);
    }

    // The opener

    /**
     * What stands out about a brush, for its opener, most telling first (the owner, 2026-10-22: an automatic opener for
     * every brush, "in a human voice"): the fact as a pattern (as {@link #RULES}), then what it does after "that" and
     * after a comma ("that leaves dust in its wake", ", leaving dust in its wake"). The web lab's voice.js carries these
     * rows as they are.
     */
    static final String[][] OPENERS = {
            {"Stamps a shape cut from the page", "stamps a shape you cut from the page", "stamping a shape you cut from the page"},
            {"Loops along the line", "spins out toy loops as you draw", "spinning out toy loops as you draw"},
            {"Loops round a ring pulled out from the start", "spins loops around a ring you pull out", "spinning loops around a ring you pull out"},
            {"Crosses where gone over or held", "crosshatches wherever you go back", "crosshatching wherever you go back"},
            {"Builds up, resists washes", "lays down waxy layers", "laying down waxy layers"},
            {"Builds up", "lays down waxy layers", "laying down waxy layers"},
            {"Scrapes back to white", "scratches back to white", "scratching back to white"},
            {"Builds up where it lingers", "piles up dots where you linger", "piling up dots where you linger"},
            {"Nothing takes on it(?: until rubbed off)?", "keeps washes off the paper", "keeping washes off the paper"},
            {"Goes behind marks", "slides under your lines", "sliding under your lines"},
            {"Inverts what's under it", "flips the greys under it", "flipping the greys under it"},
            {"Taps drop clear rings, dragging combs them(?: a little)?", "pushes floating ink into rings", "pushing floating ink into rings"},
            {"Floats (.+) s", "floats in rings", "floating in rings"},
            {"Veins bridge nearby marks for (.+) s", "grows little mold veins", "growing little mold veins"},
            {"Fades away in (.+) s", "slowly fades away", "slowly fading away"},
            {"Darkens in as it dries", "shows up as it dries", "showing up as it dries"},
            {"Holds ink between strokes", "keeps its ink from stroke to stroke", "keeping its ink from stroke to stroke"},
            {"Runs out of paint, then only smudges", "runs out of paint as you go", "running out of paint as you go"},
            {"Stays inside ink lines", "stays inside your lines", "staying inside your lines"},
            {"Floods inside ink lines", "floods up to your lines", "flooding up to your lines"},
            {"Fills the shape it starts in", "fills whole shapes", "filling whole shapes"},
            {"Fills the shape it outlines", "fills in the shapes you draw", "filling in the shapes you draw"},
            {"Flicks on a quick lift, flings drops on a hard stop", "flicks and flings when you're quick", "flicking and flinging when you're quick"},
            {"Flicks on a quick lift", "flicks when you lift fast", "flicking when you lift fast"},
            {"Flings drops on a hard stop", "flings drops when you stop hard", "flinging drops when you stop hard"},
            {"Greys shift each stroke.*", "changes grey every stroke", "changing grey every stroke"},
            {"(?:Fades to paper|Shifts to .+|Cycles through its greys) (?:over|every) .+ cm", "changes grey along the stroke", "changing grey along the stroke"},
            {"Stays wet until dried", "stays wet until you dry it", "staying wet until you dry it"},
            {"Wet for (.+) s", "stays wet for a while", "staying wet for a while"},
            {"sheds dust when tilted", "leaves dust in its wake", "leaving dust in its wake"},
            {"blooms", "blooms as it dries", "blooming as it dries"},
            {"backruns", "leaves pale backruns", "leaving pale backruns"},
            {"bleeds", "bleeds into the paper", "bleeding into the paper"},
            {"splits into strands", "splits into hairy strands", "splitting into hairy strands"},
            {"runs dry (?:quickly|on long strokes)", "runs dry into streaks", "running dry into streaks"},
            {"opens into two lines", "opens into two lines", "opening into two lines"},
            {"(?:runs|drips|pools)(?: and \\w+)? when tilted", "runs when you tilt the page", "running when you tilt the page"},
            {"(?:granulates|blends when rocked)(?: and .+)? when tilted", "moves when you tilt the page", "moving when you tilt the page"},
            {"Merges while wet(?:, blooms when damp)?", "mixes while it's wet", "mixing while it's wet"},
            {"Lifts paint, lets it run", "lifts paint and lets it run", "lifting paint and letting it run"},
            {"Lifts paint", "lifts paint off the page", "lifting paint off the page"},
            {"Drags wet paint", "drags wet paint along", "dragging wet paint along"},
            {"Softens", "softens what's there", "softening what's there"},
            {"Combs", "combs through wet paint", "combing through wet paint"},
            {"Smears", "smears what's there", "smearing what's there"},
            {"Keeps paint for the next stroke", "never quite comes clean", "never quite coming clean"},
            {"dots swell|doubled|patchy ink", "prints like old newsprint", "printing like old newsprint"},
            {"spatters on flicks", "spatters when flicked", "spattering when flicked"},
            {"pools when slow", "pools when you slow down", "pooling when you slow down"},
            {"raised ridges", "builds up raised ridges", "building up raised ridges"},
            {"dusty broken edge", "has a dusty, broken edge", "showing a dusty, broken edge"},
            {"tapers to a point", "tapers to a fine point", "tapering to a fine point"},
            {"dry-brush streaks", "leaves streaky marks", "leaving streaky marks"},
            {"dry-brush texture", "skips and scratches", "skipping and scratching"},
            {"picks up the texture", "catches the paper's grain", "catching the paper's grain"},
            {"soft speckled edge", "has a soft, speckled edge", "showing a soft, speckled edge"},
            {"dark edges", "dries with dark edges", "drying with dark edges"},
            {"slight wobble", "wobbles a little", "wobbling a little"},
            {"line shifted|broken line", "breaks up here and there", "breaking up here and there"},
    };
    private static final Pattern[] OPENER_PATTERNS = new Pattern[OPENERS.length];
    static {
        for (int i = 0; i < OPENERS.length; i++) OPENER_PATTERNS[i] = Pattern.compile("^(?:" + OPENERS[i][0] + ")$");
    }
    /** FNV-1a over {@code s}'s chars: a brush's own pick of the opener's shape, the same in voice.js. */
    static int hash(String s) {
        int h = 0x811c9dc5;
        for (int i = 0; i < s.length(); i++) { h ^= s.charAt(i); h *= 16777619; }
        return h;
    }
    /** The facts' units the opener may use: each sentence past the first but Control's, whole where an opener names it whole, else its comma-parts. */
    private static List<String> units(List<String> sentences) {
        List<String> out = new ArrayList<>();
        for (int i = 1; i < sentences.size(); i++) {
            String s = strip(sentences.get(i));
            if (HAND_START.matcher(s).find()) continue;
            if (s.contains(", ") && opens(s) >= 0) { out.add(s); continue; }
            Collections.addAll(out, s.split(", "));
        }
        return out;
    }
    /** The {@link #OPENERS} row {@code unit} meets, or -1. */
    private static int opens(String unit) {
        for (int i = 0; i < OPENERS.length; i++)
            if (OPENER_PATTERNS[i].matcher(lower1(unit)).matches() || OPENER_PATTERNS[i].matcher(unit).matches()) return i;
        return -1;
    }
    /**
     * The opener: one sentence of what the brush is and the one or two things that stand out most, in one of four
     * shapes picked by its facts' {@link #hash} ("A stick of vine charcoal with a chisel tip that leaves dust in its
     * wake.", "Meet gouache, staying wet until you dry it."), at most {@link #OPENER_MOST} characters (the second
     * thing, the paper, the tip and the shape's lead word left out to fit). The units it says go in {@code used}.
     */
    static String opener(String facts, List<String> used) { return opener(facts, used, OPENER_MOST); }
    /** {@link #opener} at most {@code most} characters. */
    static String opener(String facts, List<String> used, int most) {
        List<String> sentences = ModularWords.sentences(facts);
        if (sentences.isEmpty()) return "";
        Matcher m = NOUN_PARTS.matcher(strip(sentences.get(0)));
        String noun = m.matches() ? nounPhrase(m.group(1)) : lower1(strip(sentences.get(0)));
        String tip = m.matches() && m.group(2) != null ? " with a " + m.group(2) : "", on = m.matches() && m.group(3) != null ? " on " + m.group(3) : "";
        // The one or two that stand out most, by OPENERS' order, each row once.
        List<String> units = units(sentences);
        List<String> picked = new ArrayList<>();
        List<Integer> rows = new ArrayList<>();
        for (int i = 0; i < OPENERS.length && rows.size() < 2; i++)
            for (String u : units)
                if (!picked.contains(u) && (OPENER_PATTERNS[i].matcher(lower1(u)).matches() || OPENER_PATTERNS[i].matcher(u).matches())) {
                    picked.add(u); rows.add(i); break;
                }
        int shape = Integer.remainderUnsigned(hash(facts), 4);
        String[][] tries = {{"2", on, tip}, {"1", on, tip}, {"1", "", tip}, {"1", "", ""}, {"0", on, tip}, {"0", "", tip}, {"0", "", ""}};
        for (int lead = 0; lead < 2; lead++)
            for (String[] t : tries) {
                int n = Math.min(Integer.parseInt(t[0]), rows.size());
                if (n == 0 && !rows.isEmpty() && t[0].equals("0") && lead == 0) continue;
                String s = shaped(lead == 0 ? shape : 0, noun + t[2] + t[1], rows.subList(0, n));
                if (s.length() <= most) { used.addAll(picked.subList(0, n)); return s; }
            }
        return cap1(noun) + ".";
    }
    /** {@code what} in shape {@code shape} (0 plain, 1 "Here's", 2 "This is", 3 "Meet"), saying {@code rows}. */
    private static String shaped(int shape, String what, List<Integer> rows) {
        if (rows.isEmpty()) return shape == 0 ? cap1(what) + ", plain and simple." : shape == 1 ? "Here's " + what + ", nothing fancy."
                : shape == 2 ? "This is " + what + ", no surprises." : "Meet " + what + ".";
        String rel = OPENERS[rows.get(0)][1] + (rows.size() > 1 ? " and " + OPENERS[rows.get(1)][1] : "");
        String ing = OPENERS[rows.get(0)][2] + (rows.size() > 1 ? " and " + OPENERS[rows.get(1)][2] : "");
        return shape == 0 ? cap1(what) + " that " + rel + "." : shape == 1 ? "Here's " + what + " that " + rel + "."
                : shape == 2 ? "This is " + what + ", " + ing + "." : "Meet " + what + ", " + ing + ".";
    }
    /** {@code facts} without the units {@code used} (the opener said them). */
    static String without(String facts, List<String> used) {
        // An opener's bloom says the wet wash's too.
        for (String u : new ArrayList<>(used)) if (lower1(u).equals("blooms")) used.add("blooms when damp");
        List<String> sentences = ModularWords.sentences(facts);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < sentences.size(); i++) {
            String s = strip(sentences.get(i));
            if (i > 0 && !HAND_START.matcher(s).find()) {
                if (used.contains(s)) continue;
                List<String> keep = new ArrayList<>();
                for (String part : s.split(", ")) if (!used.contains(part)) keep.add(part);
                if (keep.isEmpty()) continue;
                s = cap1(String.join(", ", keep));
            }
            out.append(out.length() > 0 ? " " : "").append(s).append('.');
        }
        return out.toString();
    }

    // How the hand answers

    private static final Pattern HAND_START = Pattern.compile("^(Pressure|Tilt|Speed|Downhill|Uphill)\\b");
    /** Each cause as a sentence starts with it. */
    private static final Map<String, String> CAUSE = new HashMap<>();
    /** Each effect (and its plural, two causes doing it) as what the cause makes: "it wider", "the dots bigger". */
    private static final Map<String, String> MAKES = new HashMap<>();
    /** A Spiro's: what the cause changes. */
    private static final Map<String, String> SETS = new HashMap<>();
    static {
        CAUSE.put("pressure", "Pressing harder"); CAUSE.put("tilt", "Tilting the pen"); CAUSE.put("speed", "Moving faster");
        CAUSE.put("downhill", "Tilting the page"); CAUSE.put("uphill", "Tilting the page");
        String[][] makes = {{"widens", "widen", "it wider"}, {"darkens", "darken", "it darker"}, {"lightens", "lighten", "it lighter"},
                {"thins", "thin", "it thinner"}, {"thickens", "thicken", "it thicker"}, {"broadens", "broaden", "it broader"},
                {"grows dots", "grow dots", "the dots bigger"}, {"shrinks dots", "shrink dots", "the dots smaller"},
                {"widens nib", "widen nib", "the nib wider"}, {"opens nib", "open nib", "the nib open"},
                {"opens tines", "open tines", "the tines spread"}, {"spreads lines", "spread lines", "the lines spread"},
                {"turns nib", "turn nib", "the nib turn"}};
        for (String[] m : makes) { MAKES.put(m[0], m[2]); MAKES.put(m[1], m[2]); }
        SETS.put("size", "the loops' size"); SETS.put("loops", "how many loops there are"); SETS.put("wheel", "the wheel");
        SETS.put("hole", "the hole in the middle"); SETS.put("petals", "the petals"); SETS.put("nothing", "nothing");
    }
    /**
     * Control's facts ({@link ModularWords#handWords}: "Pressure widens and darkens", "Pressure and tilt widen", "Pressure
     * widens, speed thins", a Spiro's "Pressure sets size, tilt sets hole") as a sentence: "Pressing harder makes it
     * wider and darker.", "Pressing harder and tilting the pen both make it wider.", "Pressing harder makes it wider,
     * and moving faster makes it thinner."
     */
    static String hand(String fact) {
        String f = lower1(fact);
        String[] pair = f.split(", ");
        if (pair.length == 2) return cap1(handClause(pair[0])) + ", and " + handClause(pair[1]) + ".";
        return cap1(handClause(f)) + ".";
    }
    private static final Pattern TWO_CAUSES = Pattern.compile("^(\\w+) and (\\w+) (.+)$");
    private static String handClause(String f) {
        Matcher two = TWO_CAUSES.matcher(f);
        if (two.matches() && CAUSE.containsKey(two.group(1)) && CAUSE.containsKey(two.group(2))) {
            String both = lower1(CAUSE.get(two.group(1))) + " and " + lower1(CAUSE.get(two.group(2)));
            if (two.group(1).equals("downhill") || two.group(2).equals("downhill") || CAUSE.get(two.group(1)).equals(CAUSE.get(two.group(2))))
                both = lower1(CAUSE.get(two.group(1)));
            String what = two.group(3);
            if (what.startsWith("set ")) return both + " both change " + sets(what.substring(4));
            return both + " both make " + makes(what, two.group(1));
        }
        int space = f.indexOf(' ');
        String cause = f.substring(0, space), what = f.substring(space + 1);
        String who = CAUSE.containsKey(cause) ? lower1(CAUSE.get(cause)) : cause;
        if (what.startsWith("sets ")) return who + " changes " + sets(what.substring(5));
        String[] effects = what.split(" and ");
        String made = makes(effects[0], cause);
        if (effects.length > 1) {
            String second = makes(effects[1], cause);
            made = made.startsWith("it ") && second.startsWith("it ") ? made + " and " + second.substring(3)
                    : made.replaceFirst("^it ", "the line ") + " and " + second.replaceFirst("^it ", "the line ");
        }
        return who + " makes " + made;
    }
    private static String makes(String effect, String cause) {
        String made = MAKES.get(effect);
        if (made == null) { MISSED.add(effect); made = "it " + effect; }
        return made + (cause.equals("downhill") ? " downhill" : cause.equals("uphill") ? " uphill" : "");
    }
    private static String sets(String what) {
        String s = SETS.get(what);
        if (s == null) { MISSED.add(what); s = "the " + what; }
        return s;
    }

    // A Basic tool's own words

    private static Map<String, String> personalities;
    /**
     * Each Basic tool's personality, by its name, from {@link #PERSONALITIES}: a line "Name: words", # lines notes; none
     * when the file is missing.
     */
    static synchronized Map<String, String> personalities() {
        if (personalities == null) {
            List<String> lines;
            try { lines = Assets.lines(PERSONALITIES); } catch (IOException e) { lines = Collections.emptyList(); }
            personalities = personalities(lines);
        }
        return personalities;
    }
    /** {@code lines} of a personalities file, by name: the words after the first ": ". */
    static Map<String, String> personalities(List<String> lines) {
        Map<String, String> map = new HashMap<>();
        for (String line : lines) {
            if (line.startsWith("#")) continue;
            int colon = line.indexOf(": ");
            if (colon > 0) map.put(line.substring(0, colon).trim(), line.substring(colon + 2).trim());
        }
        return map;
    }
    /** {@code parts}'s personality when it is a Basic tool as it comes ({@link ToolPresets#find}); else empty. */
    static String personality(ModularBrush parts) {
        ToolPresets.Preset p = ToolPresets.find(Brush.of(parts));
        if (p == null) return "";
        String words = personalities().get(p.name);
        return words == null ? "" : words;
    }
    /**
     * The brush in words, wherever "This brush" is shown and for the screen reader: a Basic tool's personality, then
     * how it behaves; any other brush, what it is and how it behaves.
     */
    static String about(ModularBrush parts) { return about(ModularWords.plainWords(parts), personality(parts)); }
    /**
     * {@code facts} in words: {@code mine} (a special tool's own sentences) or the generated {@link #opener}, then how it
     * behaves, leaving out what the opener said, while it fits {@link #ABOUT_MOST}.
     */
    static String about(String facts, String mine) {
        List<String> used = new ArrayList<>();
        String lead = mine.isEmpty() ? opener(facts, used) : mine;
        String behaves = say(without(facts, used), false);
        // The opener and the first of the rest within Brush Lab's two first sentences: a shorter opener where they aren't.
        List<String> first = ModularWords.sentences(behaves);
        if (mine.isEmpty() && !first.isEmpty() && lead.length() + 1 + first.get(0).length() > LabLayout.TWO_SENTENCES) {
            used.clear();
            lead = opener(facts, used, LabLayout.TWO_SENTENCES - 1 - first.get(0).length());
            behaves = say(without(facts, used), false);
        }
        StringBuilder out = new StringBuilder(lead);
        for (String s : ModularWords.sentences(behaves)) {
            if (out.length() + 1 + s.length() > ABOUT_MOST) break;
            out.append(' ').append(s);
        }
        return out.toString();
    }

    private static String strip(String sentence) { return sentence.endsWith(".") ? sentence.substring(0, sentence.length() - 1) : sentence; }
    private static String lower1(String s) { return s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1); }
    private static String cap1(String s) { return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1); }
}
