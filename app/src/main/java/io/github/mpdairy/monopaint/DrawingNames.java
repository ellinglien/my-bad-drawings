package io.github.mpdairy.monopaint;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.function.Predicate;

/**
 * Names a new drawing at once, e.g. "2026-10-14 dapper-fig", so saving never asks for one; at its first save the name
 * says what's on it, e.g. "2026-10-14 ink-wash fig" ({@link #described}). Pure, checked on the desktop.
 */
final class DrawingNames {
    /** A name for {@code date} that {@code taken} rejects for no drawing; a number is added if needed. */
    static String next(LocalDate date, Random random, Predicate<String> taken) {
        // Deliberately silly, an adjective and a fruit or vegetable from the brushes' lists: the words that say what's
        // drawn replace the adjective at the first save, once there is something to describe.
        return unused(date + " " + BrushNames.fresh(random), taken);
    }

    /**
     * A new notebook's name on disk, its date then "_" and a title from a seed {@code random} draws
     * ({@link BrushNames#titles}), e.g. "2026-10-23_Several Opinions About Soup 26-10-23" (it sorts by date in Files; its cover's date
     * line and its settings' "Made Oct 23, 2026" say the date), shown as "Several Opinions About Soup 26-10-23" ({@link #shown}); its day
     * at the end (the owner, 2026-10-28): the first of the seed's forms that {@link #fits} with it and a number, else the
     * first without it; adjective-produce ("2026-10-18 brisk-bean notebook", as
     * names were before 2026-10-23) when traits.txt has no titles. Another drawn where {@code taken} rejects it, a number
     * added only when they run out. Never changed by what's drawn in it.
     */
    static String notebook(LocalDate date, Random random, Predicate<String> taken) {
        for (int tries = 0; tries < 40; tries++) {
            String name = notebookWords(date, random);
            if (!taken.test(name)) return name;
        }
        return unused(notebookWords(date, random), taken);
    }
    private static String notebookWords(LocalDate date, Random random) {
        List<String> titles = BrushNames.titles(random.nextLong());
        // Ends with its day as "26-10-28" (the owner, 2026-10-28): a shorter title first, no day only when none fits.
        for (String day : new String[]{" " + date.format(java.time.format.DateTimeFormatter.ofPattern("yy-MM-dd")), ""})
            for (String title : titles) {
                String name = date + "_" + title + day;
                if (fits(name + " 99")) return name;
            }
        return date + " " + BrushNames.pick(BrushNames.adjectives(), random) + "-" + BrushNames.pick(BrushNames.produce(), random) + " notebook";
    }
    /** Whether a notebook may be named {@code name}: a valid file name that fits every shelf tile and title ({@link NotebookLayout#fits}). */
    static boolean fits(String name) { return DrawingFiles.validName(name) && NotebookLayout.fits(shown(name, LocalDate.now())); }
    /** Whether {@code name} is a title's (a date then "_", {@link #notebook}): the shelf shows it without its date. */
    static boolean titled(String name) { return isoDate(name) != null && name.charAt(10) == '_'; }

    private static final String[] MONTHS = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

    /** A date as the shelf says it: "Oct 18", with the year when it isn't {@code today}'s ("Oct 18 2025"). */
    static String day(LocalDate date, LocalDate today) {
        String day = MONTHS[date.getMonthValue() - 1] + " " + date.getDayOfMonth();
        return date.getYear() == today.getYear() ? day : day + " " + date.getYear();
    }

    /** A file name as the shelf shows it: one that starts with an ISO date shows the date short ("Oct 18 brisk-bean notebook"), a title's not at all ("Probably Fine"). */
    static String shown(String name, LocalDate today) {
        LocalDate date = isoDate(name);
        return date == null ? name : titled(name) ? name.substring(11) : day(date, today) + name.substring(10);
    }

    /**
     * The file name for {@code typed}, a notebook's new name, renaming {@code was}: typed starting with the date {@code was}
     * shows ("Oct 18 harbour"), it keeps the ISO date on disk ("2026-10-18 harbour"), so it still sorts by date. A
     * title's hidden date ({@link #titled}) stays under whatever is typed ("2026-10-23_Harbour"), so its cover and "Made" keep it.
     */
    static String stored(String typed, String was, LocalDate today) {
        LocalDate date = isoDate(was);
        if (date == null) return typed;
        if (titled(was)) return typed.isEmpty() ? typed : was.substring(0, 11) + typed;
        String day = day(date, today) + " ";
        return typed.startsWith(day) && typed.length() > day.length() ? date + " " + typed.substring(day.length()) : typed;
    }

    /** The ISO date {@code name} starts with, then a space, or null ({@link DrawingFiles#isoDate}). */
    static LocalDate isoDate(String name) { return DrawingFiles.isoDate(name); }

    /** The longest a described name gets before its second word is left out; a number may still follow. */
    private static final int DESCRIBED_MAX = 58;

    /**
     * {@code name} (a drawing's file name, no folder) said by what's on it: its date, then {@code words} (at most two,
     * the brushes used most), then its fruit or vegetable, e.g. "2026-10-14 charcoal-hatch ink-wash fig". Null when
     * {@code name} isn't a name {@link #next} gave or there are no words: an owner's own name is never changed.
     */
    static String described(String name, List<String> words, Predicate<String> taken) {
        if (!auto(name) || words.isEmpty()) return null;
        String date = name.substring(0, 10), produce = name.substring(name.indexOf('-', 11) + 1).split(" ")[0];
        String base = date + " " + words.get(0) + (words.size() > 1 ? " " + words.get(1) : "") + " " + produce;
        if (base.length() > DESCRIBED_MAX) base = date + " " + words.get(0) + " " + produce;
        return unused(base, taken);
    }

    /** Whether {@code name} is one {@link #next} gives: a date, an adjective and a fruit or vegetable, maybe a number. */
    static boolean auto(String name) {
        if (!name.matches("\\d{4}-\\d{2}-\\d{2} [a-z]+-[a-z]+( \\d+)?")) return false;
        String[] words = name.substring(11).split(" ")[0].split("-");
        return BrushNames.adjectives().contains(words[0]) && BrushNames.produce().contains(words[1]);
    }

    private static String unused(String base, Predicate<String> taken) {
        String name = base;
        for (int n = 2; taken.test(name); n++) name = base + " " + n;
        return name;
    }

    /** The word a brush adds to a name: its style ("ink-wash"), Anything goes' medium, then "stipple" or "hatch". */
    static String word(ModularBrush brush) {
        String word = brush.style.wild ? brush.choice(BrushPart.MEDIUM).label : brush.style.label;
        PartChoice tip = brush.choice(BrushPart.TIP);
        if (tip == PartChoice.STIPPLE || tip == PartChoice.HATCH) word += " " + tip.key;
        else if (tip == PartChoice.HAND_HATCH) word += " " + PartChoice.HATCH.key;
        return word.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "-");
    }

    /** The strokes drawn on the open drawing, by {@link #word}: which brushes it was drawn with most. */
    static final class Tally {
        private final Map<String, Integer> strokes = new LinkedHashMap<>();

        void add(Brush brush) { if (brush != null) strokes.merge(word(brush.parts), 1, Integer::sum); }
        void clear() { strokes.clear(); }

        /** The most used word, then the next if it drew at least a third as many strokes; first used first on a tie. */
        List<String> top() {
            List<Map.Entry<String, Integer>> ranked = new ArrayList<>(strokes.entrySet());
            // A stable sort: words drawn equally often keep the order they were first used in.
            ranked.sort((a, b) -> b.getValue() - a.getValue());
            List<String> top = new ArrayList<>();
            if (!ranked.isEmpty()) top.add(ranked.get(0).getKey());
            if (ranked.size() > 1 && ranked.get(1).getValue() * 3 >= ranked.get(0).getValue()) top.add(ranked.get(1).getKey());
            return top;
        }
    }

    private DrawingNames() {}
}
