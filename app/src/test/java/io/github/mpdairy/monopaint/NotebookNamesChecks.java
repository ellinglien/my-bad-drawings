package io.github.mpdairy.monopaint;

import java.time.LocalDate;
import java.util.*;

/**
 * The name prompt's names ({@link NotebookNames}; docs/archive/superpowers/specs/2026-10-18-notebooks-design.md, "Names"): a new
 * notebook's field holds the name it would get as the shelf shows it, its day at the end, no others offered;
 * a rename's holds its name; typed with the shown date, the ISO date kept; names refused as
 * the rename always refused them.
 */
public final class NotebookNamesChecks {
    static final LocalDate TODAY = LocalDate.of(2026, 10, 19);

    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        creating();
        renaming();
        typed();
        refused();
        tagged();
        System.out.println("PASS: notebook names: a new notebook's prompt holds the name it would get as shown, its day at the end (26-10-19) (\"Oct 19 brisk-bean notebook\", stored with its ISO date), the one name, no others offered; "
                + "titles (\"Probably Fine\", their date kept unseen), kept at the first save whatever the template; a rename's holds its name; typed starting with the shown date the ISO date is kept, else as typed; a name with signs a file can't have, too long for a shelf, or another notebook's is refused, a rename may change its own case; "
                + "options differing from the default are said in a generated name (template word, no erasing, one brush, N min), the default leaves it as it was, a long list is trimmed to a valid name that fits, a name typed by hand is left alone");
    }
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    static void creating() {
        Set<String> taken = new HashSet<>();
        for (int seed = 0; seed < 50; seed++) {
            NotebookNames names = NotebookNames.creating(TODAY, new Random(seed), taken::contains);
            check(!names.renaming && DrawingNames.titled(names.base) && names.base.startsWith("2026-10-19_") && !taken.contains(names.base), "The title a new notebook gets, another drawn when taken: " + names.base);
            check(names.base.equals(DrawingNames.notebook(TODAY, new Random(seed), taken::contains)), "The same name New notebook gave before the prompt");
            check(names.field().equals(names.base.substring(11)), "The field holds it as the shelf shows it, no ISO date: " + names.field());
            check(names.base.endsWith(" 26-10-19") && !names.base.matches(".* \\d+"), "It ends with its day, yy-MM-dd (the owner, 2026-10-28): " + names.base);
            check(names.refused(names.stored(names.field()), taken::contains) == null, "OK takes it, nothing to fix: " + names.base);
            taken.add(names.base);
        }
        // Every title taken: a number, never a name taken.
        NotebookNames crowded = NotebookNames.creating(TODAY, new Random(1), name -> !name.matches(".* \\d+"));
        check(crowded.base.matches("2026-10-19_.* 26-10-19 \\d+"), "Titles run out: a number added: " + crowded.base);
        try {
            check(!source("NamePrompt").contains("suggestion") && !source("NotebookNames").contains("SUGGESTIONS"), "Just the one name: no others offered under the field (the owner, 2026-10-28)");
        } catch (java.io.IOException e) { throw new AssertionError("the sources: " + e); }
    }
    static String source(String name) throws java.io.IOException {
        return new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("app/src/main/java/io/github/mpdairy/monopaint/" + name + ".java")), java.nio.charset.StandardCharsets.UTF_8);
    }

    static void renaming() {
        NotebookNames names = NotebookNames.renaming("2025-03-02 dapper-fig notebook", TODAY);
        check(names.renaming && names.field().equals("Mar 2 2025 dapper-fig notebook"), "A rename's field holds its name as shown: " + names.field());
        NotebookNames plain = NotebookNames.renaming("Harbour", TODAY);
        check(plain.field().equals("Harbour"), "A name with no date: the field as it is");
        NotebookNames titled = NotebookNames.renaming("2026-10-01_Several Opinions About Soup", TODAY);
        check(titled.field().equals("Several Opinions About Soup") && titled.stored("Harbour").equals("2026-10-01_Harbour"), "A title's rename: no date shown, its date kept");
    }

    static void typed() {
        NotebookNames names = NotebookNames.creating(TODAY, new Random(2), name -> false);
        check(names.stored(names.field()).equals(names.base), "OK with the field as it was: the name it would get, with its ISO date");
        check(names.stored("  Harbour ").equals("2026-10-19_Harbour"), "Typed over: as typed, trimmed, its date kept unseen");
        check(NotebookNames.named("2026-10-19_Folio of Ink 26-10-19", true, NotebookOptions.DEFAULT.withNoErasing(true)).equals("2026-10-19_Folio of Ink 26-10-19 - no erasing"), "Its day stays where it was shown, the tags after it");
        NotebookNames old = NotebookNames.renaming("2025-03-02 dapper-fig notebook", TODAY);
        check(old.stored("Mar 2 2025 sketches").equals("2025-03-02 sketches"), "A rename typed with its shown date keeps its ISO date");
    }

    static void refused() {
        NotebookNames names = NotebookNames.creating(TODAY, new Random(3), name -> false);
        check(names.refused("Harbour", name -> false) == null, "Any plain name");
        // A new notebook's typed name has its hidden date's room fewer letters, and says so: "Use 1–53 letters…".
        String rules = DrawingFiles.nameRules(names.most());
        check(names.most() == DrawingFiles.NAME_MOST - "2026-10-19_".length() && rules.startsWith("Use 1–53 letters"), "Its letters, said honestly: " + rules);
        check(rules.equals(names.refused(names.stored("Bad/name"), name -> false)) && rules.equals(names.refused(names.stored(""), name -> false)), "Signs a file can't have, or nothing");
        String most = "a".repeat(names.most());
        check(DrawingFiles.validName(names.stored(most)) && rules.equals(names.refused(names.stored(most + "a"), name -> false)), "As many letters as it says, one more refused");
        NotebookNames plain = NotebookNames.renaming("Harbour", TODAY);
        check(plain.most() == DrawingFiles.NAME_MOST && DrawingFiles.NAME_RULES.equals(plain.refused("Bad/name", name -> false)) && DrawingFiles.NAME_RULES.startsWith("Use 1–64 letters"), "A name with nothing added: all 64");
        String longName = "A notebook of very many long words that never ends anywhere soon at all";
        check(NotebookNames.TOO_LONG.equals(names.refused(longName.substring(0, 64), name -> false)), "Too long for a shelf tile");
        check(NotebookNames.TAKEN.equals(names.refused("Harbour", "Harbour"::equals)), "Another notebook's");
        NotebookNames own = NotebookNames.renaming("Harbour", TODAY);
        check(own.refused("harbour", "harbour"::equalsIgnoreCase) == null, "A rename may change its own name's case");
        check(NotebookNames.TAKEN.equals(own.refused("Bay", "Bay"::equals)), "but not take another's");
    }

    /** A title the app gave keeps its words at the first save, whatever the template, the rules said after it; a name typed is never changed. */
    static void titles() {
        NotebookOptions d = NotebookOptions.DEFAULT;
        // A name typed (or a suggestion picked) is never changed, even one shaped as a title: only the one the app gave.
        NotebookNames prompt = null; // a short title: its day and a tag fit
        for (int seed = 2; prompt == null || prompt.base.length() > 34; seed++) prompt = NotebookNames.creating(TODAY, new Random(seed), name -> false);
        String typed = prompt.stored("My Tome");
        NotebookOptions typedOptions = d.withTemplate(NotebookTemplate.LINED).withNoErasing(true);
        check(typed.equals("2026-10-19_My Tome") && NotebookNames.named(typed, false, typedOptions).equals(typed), "A typed \"My Tome\" stays My Tome");
        check(NotebookNames.named(prompt.base, false, typedOptions).equals(prompt.base), "Its own title, not given (typed again, or picked): kept");
        check(!NotebookNames.named(prompt.base, true, typedOptions).equals(prompt.base), "The title it was given takes its options: " + NotebookNames.named(prompt.base, true, typedOptions));
        check(NotebookNames.named("2026-10-19 brave-yam notebook", false, typedOptions).equals("2026-10-19 brave-yam notebook"), "An older name typed: kept");
        String t = "2026-10-19_Probably Fine 26-10-19";
        check(NotebookNames.named(t, true, d).equals(t), "Defaults: as it was");
        // Its template isn't said (the owner, 2026-10-29: "not necessary"): Lined, Music and the rest keep the title.
        for (NotebookTemplate template : new NotebookTemplate[]{NotebookTemplate.LINED, NotebookTemplate.MUSIC, NotebookTemplate.STORYBOARD})
            check(NotebookNames.named(t, true, d.withTemplate(template)).equals(t), template.label + ": the title as it was");
        check(NotebookNames.named("2026-10-19_Tome of the Damp Wanderer 2", true, d.withTemplate(NotebookTemplate.LINED)).equals("2026-10-19_Tome of the Damp Wanderer 2"), "An older title: kept, its number too");
        check(NotebookNames.named("2026-10-19_Mistakes Volume Three", true, d.withNoErasing(true).withTemplate(NotebookTemplate.LINED)).equals("2026-10-19_Mistakes Volume Three - no erasing"), "The tags said after it");
        check(NotebookNames.named("2026-10-19_Harbour", false, d.withTemplate(NotebookTemplate.LINED).withNoErasing(true)).equals("2026-10-19_Harbour"), "A name typed at New notebook: as typed");
        check(NotebookNames.named("Tome of Mine", false, d.withTemplate(NotebookTemplate.LINED)).equals("Tome of Mine"), "Undated: not generated, as typed");
        String rolled = NotebookNames.named("2026-10-19_A Brief Inventory of Pebbles 26-10-19", true, d.withTemplate(NotebookTemplate.STORYBOARD).withNoErasing(true).withSeconds(300));
        check(DrawingNames.fits(rolled) && rolled.startsWith("2026-10-19_A Brief Inventory of Pebbles 26-10-19"), "A long one still fits, tags dropped first: " + rolled);
    }

    static void tagged() {
        NotebookNames names = NotebookNames.creating(TODAY, new Random(5), name -> false);
        String base = "2026-10-19 brave-yam notebook", stem = "2026-10-19 brave-yam"; // short words: the tags fit
        NotebookOptions d = NotebookOptions.DEFAULT;
        check(NotebookNames.named(base, true, d).equals(base), "Defaults: names as before");
        check(NotebookNames.named(base, true, d.withTemplate(NotebookTemplate.DIARY)).equals(stem + " diary"), "Template word");
        check(NotebookNames.named(base, true, d.withTemplate(NotebookTemplate.COMICS)).equals(stem + " comics"), "Template word: comics");
        check(NotebookNames.named(base, true, d.withTemplate(NotebookTemplate.DIARY_PAGE)).equals(stem + " diary page"), "Template word: diary page");
        check(NotebookNames.named(base, true, d.withNoErasing(true)).equals(base + " - no erasing"), "No erasing");
        check(NotebookNames.named(base, true, d.withSeconds(300)).equals(base + " - 5 min"), "Minutes");
        check(NotebookNames.named(base, true, d.withBrushes(1)).equals(base + " - one brush") && NotebookNames.named(base, true, d.withBrushes(3)).equals(base + " - 3 brushes"), "One brush, 3 brushes");
        // A name typed at New notebook isn't generated, even ending in "notebook": its first save keeps it.
        check(NotebookNames.named("Sketches notebook", false, d.withNoErasing(true).withTemplate(NotebookTemplate.LINED)).equals("Sketches notebook"), "A typed name stays");
        check(NotebookNames.named("Oct 19 cats notebook", false, d.withNoErasing(true)).equals("Oct 19 cats notebook"), "A typed name stays: words");
        check(NotebookNames.named(base, true, d.withTools(NotebookOptions.Tools.SURPRISE, java.util.Collections.nCopies(NotebookOptions.SLOTS, "x"))).equals(base), "Tools aren't said");
        NotebookOptions some = d.withTemplate(NotebookTemplate.DIARY).withNoErasing(true).withSeconds(300);
        check(NotebookNames.named(base, true, some.withSeconds(0)).equals(stem + " diary - no erasing"), "Template, then rules");
        // A shelf tile has room for about 30 characters: the third tag is the one dropped.
        check(NotebookNames.named(base, true, some).equals(stem + " diary - no erasing"), "Too long: the last tag goes");
        check(NotebookNames.named("2026-10-19 odd-yam notebook", true, some).equals("2026-10-19 odd-yam diary - no erasing - 5 min"), "A short name keeps all three");
        check(NotebookNames.named(base + " 2", true, d.withTemplate(NotebookTemplate.DIARY).withSeconds(180)).equals(stem + " diary 2 - 3 min"), "A number stays after the word");
        // Blank gives up "notebook" before a tag when they don't fit together; the other templates keep their word.
        String wide = "2026-10-19 bold-pepper notebook";
        check(NotebookNames.named(wide, true, d.withNoErasing(true)).equals("2026-10-19 bold-pepper - no erasing"), "Blank, long: the tag without the word");
        // Everything on: valid, fits a shelf, tags dropped from the end if not.
        NotebookOptions all = d.withTemplate(NotebookTemplate.DIARY_PAGE).withNoErasing(true).withBrushes(1).withSeconds(1800);
        for (int seed = 0; seed < 100; seed++) {
            String n = NotebookNames.creating(TODAY, new Random(seed), name -> false).base;
            String got = NotebookNames.named(n, true, all);
            check(DrawingFiles.validName(got) && NotebookLayout.fits(DrawingNames.shown(got, java.time.LocalDate.now())), "Valid and fits: " + got);
            check(got.startsWith(n), "Still its title, the tags that fit after it: " + got);
            String old = NotebookNames.named("2026-10-19 brave-yam notebook", true, all);
            check(old.startsWith("2026-10-19 brave-yam diary page"), "An older name as before: " + old);
        }
        titles();
        check(NotebookNames.named("Harbour", false, all).equals("Harbour"), "Not a generated name: as it was");
    }
}
