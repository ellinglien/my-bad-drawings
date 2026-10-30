package io.github.mpdairy.monopaint;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;

/**
 * What the name prompt holds (the owner, 2026-10-19; docs/archive/superpowers/specs/2026-10-18-notebooks-design.md, "Names"),
 * for a new notebook and for a rename: the field, filled with the name as the shelf shows it, all of it selected so
 * typing replaces it; OK and Cancel. No other names offered (the owner, 2026-10-28: "just have the one"). A new
 * notebook's field holds the name it would have been given ("Probably Fine 26-10-28", {@link
 * DrawingNames#notebook}); a rename's, its name. Pure, checked on the desktop ({@code NotebookNamesChecks}).
 */
final class NotebookNames {
    /** The prompt's titles and its button (docs/TEXT-STYLE.md). */
    static final String NEW = "New Notebook", RENAME = "Rename Notebook", OK = "OK";
    /** Why a name is refused. */
    static final String TOO_LONG = "Name too long", TAKEN = "Name taken";

    /** The name the field starts from, as stored: the new notebook's, or the one renamed. */
    final String base;
    /** Whether a notebook is renamed (else one is made). */
    final boolean renaming;
    private final LocalDate today;

    private NotebookNames(String base, boolean renaming, LocalDate today) { this.base = base; this.renaming = renaming; this.today = today; }

    /** A new notebook's: the name it takes if nothing is typed, not {@code taken}. */
    static NotebookNames creating(LocalDate today, Random random, Predicate<String> taken) {
        return new NotebookNames(DrawingNames.notebook(today, random, taken), false, today);
    }
    /** Renaming the notebook named {@code name}. */
    static NotebookNames renaming(String name, LocalDate today) { return new NotebookNames(name, true, today); }

    /** What the field holds at first, all selected. */
    String field() { return shown(base); }

    /** The tags that say what differs from the default: no erasing, one brush or N brushes, N min, (Tools is left out). */
    static List<String> tags(NotebookOptions o) {
        List<String> tags = new ArrayList<>();
        if (o.noErasing) tags.add("no erasing");
        if (o.brushes > 0) tags.add(o.brushes == 1 ? "one brush" : o.brushes + " brushes");
        if (o.seconds > 0) tags.add(NotebookRules.timeLabel(o.seconds));
        return tags;
    }
    /**
     * A generated name with the options said in it, at a new notebook's first save ({@code PaintActivity.describeName}):
     * a title as it was ("2026-10-23_Probably Fine 26-10-23"; its template not said, the owner, 2026-10-29: "not
     * necessary"), or an older name's template word for "notebook" ("2026-10-20 bashful-cherry diary"; Blank keeps it), then the
     * {@link #tags} after " - " (a file name has no room for more). Tags are dropped from the end until the name is valid
     * and fits a shelf (Blank gives up "notebook" before a tag). Only the name the app {@code given} it (the prompt's
     * field taken as it was, or New's own): a name typed or picked comes back as it was ("My Tome" stays), as do the
     * default options.
     */
    static String named(String stored, boolean given, NotebookOptions o) {
        if (!given || o.equals(NotebookOptions.DEFAULT)) return stored;
        java.util.regex.Matcher m = GENERATED.matcher(stored);
        String head, bare;
        if (m.matches()) {
            // An own template's name isn't a kind of notebook: it says "notebook", as Blank.
            String word = o.template == NotebookTemplate.BLANK || o.template.own() ? "notebook" : o.template.label.toLowerCase(java.util.Locale.ROOT);
            head = m.group(1) + " " + word + (m.group(2) == null ? "" : m.group(2));
            // Blank says no word of its own: when "notebook" and the tags don't fit, the tags alone do.
            bare = o.template == NotebookTemplate.BLANK ? m.group(1) : head;
        } else if (DrawingNames.titled(stored)) head = bare = stored;
        else return stored;
        List<String> tags = tags(o);
        for (int n = tags.size(); n >= 1; n--) {
            String say = " - " + String.join(" - ", tags.subList(0, n));
            for (String name : new String[]{head + say, bare + say}) if (DrawingNames.fits(name)) return name;
        }
        return head.equals(stored) ? stored : (DrawingNames.fits(head) ? head : stored);
    }
    private static final java.util.regex.Pattern GENERATED = java.util.regex.Pattern.compile("^(\\d{4}-\\d{2}-\\d{2} [a-z]+-[a-z]+) notebook( \\d+)?$");
    /** A name as the prompt and the shelf show it ("Oct 19 brisk-bean notebook", a title without its date). */
    String shown(String stored) { return DrawingNames.shown(stored, today); }
    /** The most letters a typed name may have: a file name's, less what {@link #stored} adds (a title's hidden date). */
    int most() { return DrawingFiles.NAME_MOST - (stored("x").length() - 1); }
    /** The name stored for what is typed: typed starting with the date the field showed, it keeps the ISO date ({@link DrawingNames#stored}). */
    String stored(String typed) { return DrawingNames.stored(typed.trim(), base, today); }
    /**
     * Why {@code name} (as stored) can't be the notebook's, or null when it can: signs a file name can't have, too long
     * for any tablet's shelf or title ({@link NotebookLayout#fits}), or another notebook's ({@code taken}; a rename may
     * change only the case of its own name).
     */
    String refused(String name, Predicate<String> taken) {
        if (!DrawingFiles.validName(name)) return DrawingFiles.nameRules(most());
        if (!NotebookLayout.fits(shown(name))) return TOO_LONG;
        if (!(renaming && name.equalsIgnoreCase(base)) && taken.test(name)) return TAKEN;
        return null;
    }
}
