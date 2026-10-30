package io.github.mpdairy.monopaint;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A notebook's options (docs/archive/superpowers/specs/2026-10-19-notebook-options-design.md, "The options"), immutable: its
 * {@link NotebookTemplate template} (its guides always printed in the PDF and PNG copies, as on real paper: Guides in PDF
 * was cut, 2026-10-24) and its {@link Tools}. A
 * notebook with its own toolbar (Surprise Me, Pick from Library) keeps it as {@link ToolSlots.Toolbar} lines. Everything
 * defaults to off ({@link #DEFAULT}: no guides, the app-wide toolbar); {@link BookOptions}
 * keeps only what isn't the default, so an old notebook, which has none, is read as the default and written byte for byte
 * as before. The guides' step on the screen ({@link #guidesShown}, 2026-10-22) and Page Colour ({@link #paper}, 2026-10-23) are kept the same way. Stages 4 and 5 add the Rules (No Erasing, Brushes, Timed Pages;
 * Lock Pages, 2026-10-27; Nib Preview, 2026-10-29, on by default; Self-Portrait Prompt, 2026-10-30), each with a record of its own beside these. Lock Settings (Open, Locked, Sealed) was cut (the refocus, 2026-10-24): a
 * notebook locked before opens unlocked, its rules as they were ({@link BookOptions#LOCK} is passed by). Pure, checked on the desktop ({@code NotebookOptionsChecks}, {@code NotebookRulesChecks}).
 */
final class NotebookOptions {
    /** Where a notebook's tools come from. The key is the file's (never respelled), the label what the library card shows. */
    enum Tools {
        MINE("mine", "My Toolbar"), SURPRISE("surprise", "Surprise Me"), LIBRARY("library", "Library"),
        /** Surprise Me rolled again for every page ({@link ToolSlots#forPage}): the toolbar kept is the first page's. */
        EACH_PAGE("eachpage", "Each Page");
        final String key, label;
        Tools(String key, String label) { this.key = key; this.label = label; }
        static Tools of(String key) { for (Tools t : values()) if (t.key.equals(key)) return t; return MINE; }
    }

    /** Brushes' choices: 0 is All, else how many slots the bar shows, at most all of them ({@link #SLOTS}). */
    static final int MOST_BRUSHES = NotebookOptions.SLOTS;
    /** Timed Pages' choices, seconds, its slider's stops: Off, 30 s steps to 5 min, then minutes to an hour. */
    static final int[] SECONDS = new int[1 + 10 + 55];
    static { for (int i = 1; i < SECONDS.length; i++) SECONDS[i] = i <= 10 ? 30 * i : 60 * (i - 5); }
    /**
     * Timed Pages' steps, minutes: what builds before seconds offered (the TIMD record says the nearest of them, so those
     * keep a timer), and the sand timer's choices after Off ({@link SandTimer#SECONDS}).
     */
    static final int[] MINUTES = {1, 3, 5, 10, 30};

    /**
     * How many slots a notebook's own toolbar has: {@link ToolSlots#COUNT} (checked), kept here so the file's classes stand
     * without the tools; and the fewest it is read with: one kept with eight (before 2026-10-27) keeps them, the slots after
     * them as a first launch has them ({@link ToolSlots.Toolbar#parse}).
     */
    static final int SLOTS = 10, FEWEST_SLOTS = 8;

    /** The template's guides' steps on the screen ({@link #guidesShown}). */
    static final int FULL = 0, HALF = 1, FAINT = 2, HIDDEN = 3;
    /** Page Colour's default ({@link #paper}): white paper. */
    static final int WHITE = 255;

    static final NotebookOptions DEFAULT = new NotebookOptions(NotebookTemplate.BLANK, Tools.MINE, Collections.<String>emptyList(), false, 0, 0, FULL, WHITE, false, true, false);

    final NotebookTemplate template;
    final Tools tools;
    /** The notebook's own toolbar, {@link ToolSlots#COUNT} lines ({@link ToolSlots.Toolbar#lines}, which the caller reads back with {@link ToolSlots.Toolbar#parse}); empty for My Toolbar. */
    final List<String> toolbar;
    /** No Erasing: the eraser (the pen's end and the finger's too), Clear Page, any erasing smudge, Undo and Redo, and deleting a page with marks are off in this notebook. */
    final boolean noErasing;
    /** Brushes: how many of the toolbar's slots the bar shows (the first ones; the smudge tool is off too), 0 for All. Replaces One Brush (count 1). */
    final int brushes;
    /** Timed Pages: seconds a page has from its first stroke (one of {@link #SECONDS}); 0 for off. */
    final int seconds;
    /**
     * Lock Pages (the owner, 2026-10-27: "could the lock be a setting in the notebook? like, timers lock pages?"): a page
     * whose time ran out takes no more marks, and a Diary Page's finished section is fenced ({@link PageClock#locks},
     * {@link TimedSections#locked}). Off by default: Time's up is a guide and the page stays editable.
     */
    final boolean lockPages;
    /**
     * Nib Preview (the owner, 2026-10-29: "for the dot preview.. have it enabled by default but able to be disabled in the
     * notebook settings?"): the hover ring's dots where the nib would land ({@code DrawingPad.hoverOutline}). On by default,
     * the one option that is: a notebook without its record, any older one, shows them.
     */
    final boolean nibPreview;
    /**
     * Self-Portrait Prompt (the owner, 2026-10-30: "put it in notebook settings as an optional thing, new for every new
     * page"): each page added to the notebook is dealt a card ({@link DailyPrompt}; {@code PaintActivity.addPage}). Off by
     * default; pages there already are untouched.
     */
    final boolean selfPortrait;

    /**
     * How the template's guides show on the screen (the owner, 2026-10-22: "A way to hide/show/adjust transparency of
     * template"): {@link #FULL}, {@link #HALF}, {@link #FAINT} or {@link #HIDDEN}, Page Transparency's steps and eyes. A view
     * only: never in a page, its file or its copies (which always print the guides).
     */
    final int guidesShown;
    /** The steps' words (docs/TEXT-STYLE.md), in order; a tap on the file menu's Template row goes to the next. */
    static final String[] GUIDE_STEPS = {"Full", "Half", "Faint", "Hidden"};
    /** The note a tap on the file menu's Template row gives: the result in two words (docs/TEXT-STYLE.md). */
    static String guidesNote(int step) { return "Template " + GUIDE_STEPS[step].toLowerCase(java.util.Locale.ROOT); }
    /** Each step's strength, %: Page Transparency's ({@link TracingPaper#STRENGTH}), so Half and Faint guides look as Half and Faint ghosts. */
    static final int[] GUIDE_STRENGTH = {100, TracingPaper.STRENGTH[TracingPaper.HALF], TracingPaper.STRENGTH[TracingPaper.FAINT], 0};

    /**
     * Page Colour (the owner, 2026-10-23: "a notebook setting: default page color? that'd be cool"): the paper tone a page
     * added to the notebook starts on ({@link ToneDocument#paperTone}: 255 white, the default, to 0 black). Pages already
     * there keep their own, but blank ones, which take the new shade ({@code DrawingBook#setOptions}).
     */
    final int paper;
    /**
     * Page Colour's six swatches (the owner, 2026-10-23: "six swatches is enough", in the settings list's row): Scribbler
     * Style's six greys as paper tones, paper, grey-200, grey-400, grey-600, grey-800, ink. They only set new values: an
     * older notebook's grey from the 12-step slider (5% to 90%) is kept as it is and matches none ({@link #paperSwatch}).
     */
    static final int[] PAPERS = {255, 0xdd, 0xbb, 0x88, 0x55, 0};
    /** Which of {@link #PAPERS} this notebook's {@link #paper} is, or −1 (an older grey: no swatch chosen). */
    int paperSwatch() {
        for (int i = 0; i < PAPERS.length; i++) if (PAPERS[i] == paper) return i;
        return -1;
    }
    /** Page Colour's words for paper tone {@code tone} (docs/TEXT-STYLE.md): White, Black, else "47% grey". */
    static String paperWord(int tone) { return tone >= WHITE ? "White" : tone <= 0 ? "Black" : Math.round((WHITE - tone) * 100f / WHITE) + "% grey"; }

    private NotebookOptions(NotebookTemplate template, Tools tools, List<String> toolbar, boolean noErasing, int brushes, int seconds, int guidesShown, int paper, boolean lockPages, boolean nibPreview, boolean selfPortrait) {
        this.template = template; this.tools = tools; this.toolbar = toolbar; this.lockPages = lockPages; this.nibPreview = nibPreview; this.selfPortrait = selfPortrait;
        this.noErasing = noErasing; this.brushes = brushes; this.seconds = seconds;
        this.guidesShown = guidesShown >= FULL && guidesShown <= HIDDEN ? guidesShown : FULL;
        this.paper = Math.max(0, Math.min(WHITE, paper));
    }

    NotebookOptions withTemplate(NotebookTemplate template) { return new NotebookOptions(template, tools, toolbar, noErasing, brushes, seconds, guidesShown, paper, lockPages, nibPreview, selfPortrait); }
    NotebookOptions withGuidesShown(int step) { return new NotebookOptions(template, tools, toolbar, noErasing, brushes, seconds, step, paper, lockPages, nibPreview, selfPortrait); }
    /** The next of {@link #GUIDE_STEPS} after this one's, Hidden going back to Full. */
    NotebookOptions withNextGuidesStep() { return withGuidesShown((guidesShown + 1) % GUIDE_STEPS.length); }
    NotebookOptions withLockPages(boolean on) { return new NotebookOptions(template, tools, toolbar, noErasing, brushes, seconds, guidesShown, paper, on, nibPreview, selfPortrait); }
    NotebookOptions withNibPreview(boolean on) { return new NotebookOptions(template, tools, toolbar, noErasing, brushes, seconds, guidesShown, paper, lockPages, on, selfPortrait); }
    NotebookOptions withSelfPortrait(boolean on) { return new NotebookOptions(template, tools, toolbar, noErasing, brushes, seconds, guidesShown, paper, lockPages, nibPreview, on); }
    NotebookOptions withNoErasing(boolean on) { return new NotebookOptions(template, tools, toolbar, on, brushes, seconds, guidesShown, paper, lockPages, nibPreview, selfPortrait); }
    /** Timed Pages {@code seconds}, the nearest of {@link #SECONDS} (none or less: off). */
    NotebookOptions withSeconds(int seconds) {
        return new NotebookOptions(template, tools, toolbar, noErasing, brushes, SECONDS[nearest(SECONDS, seconds)], guidesShown, paper, lockPages, nibPreview, selfPortrait);
    }
    /** Which of {@code choices} (ascending) is nearest {@code value}. */
    static int nearest(int[] choices, int value) {
        int best = 0;
        for (int i = 1; i < choices.length; i++) if (Math.abs(choices[i] - value) < Math.abs(choices[best] - value)) best = i;
        return best;
    }
    /** Page Colour {@code tone} (0 to 255; anything past them the nearest). */
    NotebookOptions withPaper(int tone) { return new NotebookOptions(template, tools, toolbar, noErasing, brushes, seconds, guidesShown, tone, lockPages, nibPreview, selfPortrait); }
    /** Brushes {@code count}: 1 to {@link #MOST_BRUSHES}; anything else is All. */
    NotebookOptions withBrushes(int count) {
        return new NotebookOptions(template, tools, toolbar, noErasing, count >= 1 && count <= MOST_BRUSHES ? count : 0, seconds, guidesShown, paper, lockPages, nibPreview, selfPortrait);
    }
    /** My Toolbar (no lines), or {@code tools} with its own {@code toolbar} lines ({@link #FEWEST_SLOTS} to {@link #SLOTS} of them; anything else is My Toolbar). */
    NotebookOptions withTools(Tools tools, List<String> toolbar) {
        if (tools == Tools.MINE || !slotCount(toolbar)) return new NotebookOptions(template, Tools.MINE, Collections.<String>emptyList(), noErasing, brushes, seconds, guidesShown, paper, lockPages, nibPreview, selfPortrait);
        return new NotebookOptions(template, tools, Collections.unmodifiableList(new ArrayList<>(toolbar)), noErasing, brushes, seconds, guidesShown, paper, lockPages, nibPreview, selfPortrait);
    }
    /** The same options with the toolbar lines changed (the notebook's brushes, sizes or greys changed); My Toolbar keeps none, Each Page the first page's roll. */
    NotebookOptions withToolbar(List<String> toolbar) { return tools == Tools.MINE || tools == Tools.EACH_PAGE ? this : withTools(tools, toolbar); }

    /** Whether {@code toolbar} has a toolbar's count of lines: {@link #FEWEST_SLOTS} to {@link #SLOTS}. */
    private static boolean slotCount(List<String> toolbar) { return toolbar != null && toolbar.size() >= FEWEST_SLOTS && toolbar.size() <= SLOTS; }
    /** Whether {@code line} is a toolbar line ({@link ToolSlots.Toolbar#lines}): a brush's two fields, then its size and grey. The brush itself is read where it is used. */
    private static boolean readsAsLine(String line) {
        if (line == null || line.isEmpty()) return false;
        String[] part = line.split("\t", -1);
        if (part.length != 4 || part[0].isEmpty()) return false;
        try {
            double px = Double.parseDouble(part[2]), darkness = Double.parseDouble(part[3]);
            return !Double.isNaN(px) && !Double.isInfinite(px) && !Double.isNaN(darkness) && !Double.isInfinite(darkness);
        } catch (NumberFormatException e) { return false; }
    }
    /** Whether the notebook has a toolbar of its own that it keeps as lines (Surprise Me's, Each Page's or the library's). */
    boolean ownsTools() { return tools != Tools.MINE; }
    /** Whether any rule is on (No Erasing, Brushes, Timed Pages). */
    boolean hasRules() { return noErasing || brushes > 0 || seconds > 0; }
    /** Whether the toolbar can't be changed while drawing: Each Page's (each page's own roll). */
    boolean toolbarFrozen() { return tools == Tools.EACH_PAGE; }
    /** How many slots the bar shows: Brushes' count, else all. */
    int slotsShown() { return brushes > 0 ? brushes : SLOTS; }
    /** A page's time, ms; 0 when Timed Pages is off. */
    long limitMs() { return seconds * 1000L; }
    /**
     * A page's own template key, set by a hold on the page in the strip (the owner, 2026-10-21): the template's key after
     * {@link #OWN}, so it is told from what Random gave the page. Builds before it read such a key as Blank under Random,
     * and pass it by otherwise.
     */
    static final String OWN = "=";
    static String ownKey(NotebookTemplate t) { return OWN + t.key; }
    /** The template of a page that keeps {@code pageKey} (null: none): its own, else the notebook's, or under Random what Random gave it (Blank until it has one). */
    NotebookTemplate pageTemplate(String pageKey) {
        if (pageKey != null && pageKey.startsWith(OWN)) return NotebookTemplate.of(pageKey.substring(OWN.length()));
        return template.random() ? NotebookTemplate.of(pageKey) : template;
    }
    /** Whether every option is the default: then the file keeps nothing for them. */
    boolean isDefault() { return equals(DEFAULT); }

    // As the file keeps them (BookOptions): a record each, written only when it isn't the default.

    /** The template record's bytes: its key. */
    byte[] templateBytes() { return template.key.getBytes(StandardCharsets.UTF_8); }
    /** The tools record's bytes: the choice's key, then the toolbar's lines, one a line. */
    byte[] toolsBytes() { return (tools.key + "\n" + String.join("\n", toolbar)).getBytes(StandardCharsets.UTF_8); }

    /** The guides' step's record's bytes ({@code GSHN}): one byte, {@link #guidesShown}. */
    byte[] guidesShownBytes() { return new byte[]{(byte) guidesShown}; }
    /** These options with the guides' step's record read; anything but a step is Full. */
    NotebookOptions readGuidesShown(byte[] bytes) { return bytes == null || bytes.length != 1 ? this : withGuidesShown(bytes[0]); }
    /** The Page Colour record's bytes ({@code PCOL}): one byte, {@link #paper}. */
    byte[] paperBytes() { return new byte[]{(byte) paper}; }
    /** These options with the Page Colour record read ({@code null}: none, white). */
    NotebookOptions readPaper(byte[] bytes) { return bytes == null || bytes.length != 1 ? this : withPaper(bytes[0] & 255); }
    /** The Lock Pages record's bytes ({@code TLCK}): one byte, 1 for on. */
    byte[] lockPagesBytes() { return new byte[]{(byte) (lockPages ? 1 : 0)}; }
    /** These options with the Lock Pages record read ({@code null}: none, off). */
    NotebookOptions readLockPages(byte[] bytes) { return bytes == null || bytes.length != 1 ? this : withLockPages(bytes[0] != 0); }
    /** The Nib Preview record's bytes ({@code NIBP}): one byte, 1 for on. */
    byte[] nibPreviewBytes() { return new byte[]{(byte) (nibPreview ? 1 : 0)}; }
    /** These options with the Nib Preview record read ({@code null}: none, on). */
    NotebookOptions readNibPreview(byte[] bytes) { return bytes == null || bytes.length != 1 ? this : withNibPreview(bytes[0] != 0); }
    /** The Self-Portrait Prompt record's bytes ({@code SELF}): one byte, 1 for on. */
    byte[] selfPortraitBytes() { return new byte[]{(byte) (selfPortrait ? 1 : 0)}; }
    /** These options with the Self-Portrait Prompt record read ({@code null}: none, off). */
    NotebookOptions readSelfPortrait(byte[] bytes) { return bytes == null || bytes.length != 1 ? this : withSelfPortrait(bytes[0] != 0); }
    /** The No Erasing record's bytes: one byte, 1 for on. */
    byte[] noErasingBytes() { return new byte[]{(byte) (noErasing ? 1 : 0)}; }
    /** The Brushes record's bytes: the count, one byte. */
    byte[] brushesBytes() { return new byte[]{(byte) brushes}; }
    /** The old Timed Pages record's bytes (TIMD, for builds before seconds): the nearest minutes they offered, one byte. */
    byte[] minutesBytes() { return new byte[]{(byte) MINUTES[nearest(MINUTES, Math.round(seconds / 60f))]}; }
    /** The Timed Pages record's bytes: the seconds, two bytes. */
    byte[] secondsBytes() { return new byte[]{(byte) (seconds >> 8), (byte) seconds}; }

    /** These options with the No Erasing record's bytes read ({@code null}: none). */
    NotebookOptions readNoErasing(byte[] bytes) { return bytes == null || bytes.length != 1 ? this : withNoErasing(bytes[0] != 0); }
    /** These options with the Brushes record's bytes read; anything but 1 to {@link #MOST_BRUSHES} is All. */
    NotebookOptions readBrushes(byte[] bytes) { return bytes == null || bytes.length != 1 ? this : withBrushes(bytes[0]); }
    /**
     * These options with an older version's One Brush record read: its toolbar line fills a Library toolbar of its own,
     * with Brushes 1 (the same tools on every page). A line that doesn't read leaves them as they are.
     */
    NotebookOptions readOneBrush(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > 1 << 16) return this;
        String line = new String(bytes, StandardCharsets.UTF_8);
        return readsAsLine(line) ? withTools(Tools.LIBRARY, Collections.nCopies(SLOTS, line)).withBrushes(1) : this;
    }
    /** These options with the old Timed Pages record's bytes read: minutes, 1 to 60 (anything else is off). */
    NotebookOptions readMinutes(byte[] bytes) { return bytes == null || bytes.length != 1 ? this : withSeconds(bytes[0] >= 1 && bytes[0] <= 60 ? bytes[0] * 60 : 0); }
    /** These options with the Timed Pages record's bytes read: seconds, the nearest choice. */
    NotebookOptions readSeconds(byte[] bytes) { return bytes == null || bytes.length != 2 ? this : withSeconds((bytes[0] & 255) << 8 | (bytes[1] & 255)); }

    /** These options with the template record's bytes read ({@code null}: none). */
    NotebookOptions readTemplate(byte[] bytes) { return bytes == null || bytes.length == 0 || bytes.length > 4096 ? this : withTemplate(NotebookTemplate.of(new String(bytes, StandardCharsets.UTF_8))); }
    /** These options with the tools record's bytes read; a record that doesn't read leaves My Toolbar. */
    NotebookOptions readTools(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > 1 << 20) return this;
        String[] lines = new String(bytes, StandardCharsets.UTF_8).split("\n", -1);
        Tools tools = Tools.of(lines[0]);
        List<String> toolbar = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) toolbar.add(lines[i]);
        if (tools == Tools.MINE || !slotCount(toolbar)) return this;
        return withTools(tools, toolbar);
    }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof NotebookOptions)) return false;
        NotebookOptions o = (NotebookOptions) other;
        return template.equals(o.template) && tools == o.tools && toolbar.equals(o.toolbar)
                && noErasing == o.noErasing && brushes == o.brushes && seconds == o.seconds && guidesShown == o.guidesShown && paper == o.paper && lockPages == o.lockPages && nibPreview == o.nibPreview && selfPortrait == o.selfPortrait;
    }
    @Override public int hashCode() {
        return template.hashCode() * 31 + 7 * tools.hashCode() + 13 * toolbar.hashCode()
                + (noErasing ? 17 : 0) + 19 * brushes + 23 * seconds + 37 * guidesShown + 41 * paper + (lockPages ? 43 : 0) + (nibPreview ? 0 : 47) + (selfPortrait ? 53 : 0);
    }
}
