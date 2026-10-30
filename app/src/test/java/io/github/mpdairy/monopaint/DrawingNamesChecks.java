package io.github.mpdairy.monopaint;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.HashSet;
import java.util.Random;

public final class DrawingNamesChecks {
    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        LocalDate day = LocalDate.of(2026, 10, 13);
        HashSet<String> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String name = DrawingNames.next(day, new Random(i), taken -> false);
            check(name.matches("2026-10-13 [a-z]+-[a-z]+"), "Date and two words: " + name);
            String[] words = name.substring(11).split("-");
            check(BrushNames.adjectives().contains(words[0]) && BrushNames.produce().contains(words[1]), "From the adjective and produce lists: " + name);
            check(DrawingFiles.validName(name), "A valid drawing file name: " + name);
            seen.add(name);
        }
        check(seen.size() > 50, "Names vary");
        String first = DrawingNames.next(day, new Random(7), taken -> false);
        check(first.equals(DrawingNames.next(day, new Random(7), taken -> false)), "Same seed, same name");
        HashSet<String> taken = new HashSet<>(); taken.add(first); taken.add(first + " 2");
        check(DrawingNames.next(day, new Random(7), taken::contains).equals(first + " 3"), "A taken name gets the next free number");

        // At the first save: the date, the brushes drawn with most, the same fruit or vegetable.
        check(DrawingNames.auto(first) && DrawingNames.auto(first + " 3"), "A given name is recognized: " + first);
        String produce = first.substring(first.indexOf('-', 11) + 1);
        List<String> one = Collections.singletonList("ink-wash");
        String described = DrawingNames.described(first, one, t -> false);
        check(described.equals("2026-10-13 ink-wash " + produce) && DrawingFiles.validName(described), "Date, words, produce: " + described);
        check(DrawingNames.described(first + " 2", one, t -> false).equals(described), "A collision number is dropped with the adjective");
        check(DrawingNames.described(first, Arrays.asList("charcoal-hatch", "ink-wash"), t -> false)
                .equals("2026-10-13 charcoal-hatch ink-wash " + produce), "Two words, most used first");
        check(DrawingNames.described(first, one, described::equals).equals(described + " 2"), "A taken described name gets a number");
        check(!DrawingNames.auto(described) && DrawingNames.described(described, one, t -> false) == null, "Described once: never again");
        for (String own : new String[]{"Harbour sketch", "2026-10-13 harbour", "2026-10-13 tidy-zzz", "2026-10-13 zzz-fig"})
            check(DrawingNames.described(own, one, t -> false) == null, "An owner's own name is kept: " + own);
        check(DrawingNames.described(first, Collections.emptyList(), t -> false) == null, "Nothing drawn, no new name");
        String longest = DrawingNames.described(first, Arrays.asList("anything-goes-stipple", "brush-pen-stipple-long-word"), t -> false);
        check(longest.length() <= 64 && DrawingFiles.validName(longest), "Long words leave the second out: " + longest);

        // The words: a style, Anything goes' medium, a stipple or hatching named.
        ModularBrush wash = ModularStyle.named("Ink wash").roll(new Random(3));
        check(DrawingNames.word(wash).equals(wash.choice(BrushPart.TIP) == PartChoice.STIPPLE || wash.choice(BrushPart.TIP) == PartChoice.HATCH
                ? "ink-wash-" + wash.choice(BrushPart.TIP).key : "ink-wash"), "A style's word: " + DrawingNames.word(wash));
        ModularBrush watercolour = ModularStyle.named("Watercolor").roll(new Random(3));
        watercolour.set(BrushPart.TIP, new ModularBrush.Part(PartChoice.ROUND));
        check(DrawingNames.word(watercolour).equals("watercolour"), "Canadian spelling, as shown");
        ModularBrush wild = ModularStyle.named(ModularStyle.ANYTHING_GOES).roll(new Random(3));
        wild.set(BrushPart.MEDIUM, new ModularBrush.Part(PartChoice.CHARCOAL)); wild.set(BrushPart.TIP, new ModularBrush.Part(PartChoice.HATCH));
        check(DrawingNames.word(wild).equals("charcoal-hatch"), "Anything goes says its medium: " + DrawingNames.word(wild));
        for (ModularStyle style : ModularStyle.all())
            check(DrawingNames.word(style.roll(new Random(5))).matches("[a-z]+(-[a-z]+)*"), "Every style's word is a file-name word: " + style.name);

        DrawingNames.Tally tally = new DrawingNames.Tally();
        check(tally.top().isEmpty(), "No strokes, no words");
        Brush washBrush = Brush.of(watercolour), wildBrush = Brush.of(wild);
        tally.add(wildBrush); tally.add(washBrush); tally.add(washBrush); tally.add(null);
        check(tally.top().equals(Arrays.asList("watercolour", "charcoal-hatch")), "Most used first: " + tally.top());
        tally.add(washBrush); tally.add(washBrush);
        check(tally.top().equals(Collections.singletonList("watercolour")), "A brush barely used is left out: " + tally.top());
        tally.clear(); tally.add(wildBrush); tally.add(washBrush);
        check(tally.top().equals(Arrays.asList("charcoal-hatch", "watercolour")), "A tie keeps the first used first");
        notebooks();
        System.out.println("PASS: dated adjective-produce names from the brushes' lists, valid file names, varied, collision numbering;"
                + " described once at the first save by the brushes drawn with most, owners' names kept; new notebooks titled"
                + " \"2026-10-18_Probably Fine 26-10-18\" on disk and \"Probably Fine 26-10-18\" on the shelf (its cover and settings say the date),"
                + " valid, fitting every shelf, another drawn on a clash, never described; older names shown and renamed as before");
    }
    /** Notebooks' names (docs/archive/superpowers/specs/2026-10-18-notebooks-design.md, "Names"; titles since 2026-10-23). */
    static void notebooks() {
        LocalDate day = LocalDate.of(2026, 10, 18), today = LocalDate.of(2026, 11, 2);
        HashSet<String> seen = new HashSet<>();
        int longest = 0;
        for (int i = 0; i < 2000; i++) {
            String name = DrawingNames.notebook(day, new Random(i), taken -> false), title = name.substring(11);
            check(name.startsWith("2026-10-18_") && DrawingNames.titled(name) && title.matches("[A-Z][a-z]*( [A-Za-z][a-z]*)+ 26-10-18"), "Its date, hidden, then a title in capitals, its day at the end: " + name);
            check(BrushNames.titles(new Random(i).nextLong()).contains(title.substring(0, title.length() - 9)), "One of its seed's titles: " + name);
            check(DrawingFiles.validName(name + " 99") && NotebookLayout.fits(DrawingNames.shown(name + " 99", today)), "A valid file name that fits every shelf, with a number too: " + name);
            check(!DrawingNames.auto(name) && DrawingNames.described(name, Collections.singletonList("ink-wash"), t -> false) == null,
                    "A notebook is never renamed by what's drawn in it: " + name);
            check(DrawingNames.shown(name, today).equals(title) && NotebookCovers.written(name).equals(title) && NotebookCovers.dated(name).equals("18 Oct 2026"),
                    "The shelf shows the title alone, the cover its title and its date: " + name);
            check(LibraryCard.foot(DrawingNames.isoDate(name), null, 3).equals("Made Oct 18, 2026 · 3 pages"), "Its settings say the day it was made");
            for (String w : title.toLowerCase(java.util.Locale.ROOT).split(" ")) check(!java.util.Arrays.asList(UNKIND).contains(w), "Nothing mean or gross: " + name);
            seen.add(name); longest = Math.max(longest, name.length());
        }
        check(seen.size() > 550 && longest + 3 <= 64, "Names vary and stay short (" + seen.size() + ", " + longest + ")");
        String first = DrawingNames.notebook(day, new Random(3), taken -> false);
        check(first.equals(DrawingNames.notebook(day, new Random(3), taken -> false)), "Same seed, same title");
        String again = DrawingNames.notebook(day, new Random(3), first::equalsIgnoreCase);
        check(!again.equals(first) && !again.matches(".* \\d+") && DrawingNames.titled(again), "A taken title: another drawn, no number: " + again);
        String crowded = DrawingNames.notebook(day, new Random(3), n -> !n.matches(".* \\d+"));
        check(crowded.matches("2026-10-18_.* 2"), "Every title taken: a number added: " + crowded);
        // Names given before titles never change: shown, stored and described as they were.
        String old = "2026-10-18 brisk-bean notebook", words = old.substring(11);
        check(!DrawingNames.titled(old) && DrawingNames.shown(old, today).equals("Oct 18 " + words), "An older name shown with a short date: " + DrawingNames.shown(old, today));
        check(DrawingNames.shown(old, LocalDate.of(2027, 1, 4)).equals("Oct 18 2026 " + words), "Another year's shows the year");
        check(DrawingNames.shown("Harbour", today).equals("Harbour") && DrawingNames.shown("2026-13-40 x", today).equals("2026-13-40 x") && DrawingNames.shown("2026-13-40_x", today).equals("2026-13-40_x"),
                "A name without a date shows as it is");
        check(DrawingNames.shown("2026-01-05 dapper-fig", today).equals("Jan 5 dapper-fig") && DrawingNames.shown("2026-01-05 Harbour", today).equals("Jan 5 Harbour"), "Any dated name shows its date short");
        check(DrawingNames.stored("Oct 18 harbour", old, today).equals("2026-10-18 harbour"), "Typed with the shown date: the ISO date kept");
        check(DrawingNames.stored("Harbour", old, today).equals("Harbour") && DrawingNames.stored("Oct 18 ", old, today).equals("Oct 18 ")
                && DrawingNames.stored("Oct 18 harbour", "Harbour", today).equals("Oct 18 harbour"), "Any other name is kept as typed");
        check(DrawingNames.stored("Harbour", first, today).equals("2026-10-18_Harbour") && DrawingNames.stored(DrawingNames.shown(first, today), first, today).equals(first)
                && DrawingNames.stored("", first, today).isEmpty(), "A title's rename keeps its hidden date");
        check(DrawingNames.day(LocalDate.of(2026, 5, 1), today).equals("May 1"), "A date: month and day");
    }
    /** Words no title may have (the pools are filtered of anything mean, gross or awkward). */
    private static final String[] UNKIND = {"stupid", "ugly", "fat", "dumb", "gross", "slimy", "snot", "greasy", "lusty", "moist", "butt", "poop", "rotten", "dead", "kill", "hate"};
    private static void check(boolean valid, String message) { if (!valid) throw new AssertionError(message); }
}
