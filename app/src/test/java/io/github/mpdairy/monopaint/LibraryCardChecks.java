package io.github.mpdairy.monopaint;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A notebook's settings list ({@link LibraryCard}) and its screens ({@link CardSheet}, drawn by {@link CardArt}): the
 * list's rows and words, fitting both tablets in both turns with nothing scrolling (its rows 40 dp or more, the name 2
 * lines at most, each row's key and value side by side), the lock fixing the tools and the rules; every screen fitting
 * with nothing scrolling (but a Template grid too long), every word 12 sp or more inside its box, its choice in use a grey
 * tile with the ink tab (no fill, no ✓), a long name wrapped to two lines; the last day added to still kept in the file
 * and the recovery ({@code LAST}); a page's own template.
 */
public final class LibraryCardChecks {
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    /** A pen that only checks: every word at 12 sp or more and inside {@code w × h} (translations and turns aside). */
    static final class Checking implements CardArt.Pen {
        final float w, h; final String where;
        final List<int[]> fills = new ArrayList<>();
        final List<CardArt.Icon> icons = new ArrayList<>();
        final List<float[]> fillBoxes = new ArrayList<>(), pictures = new ArrayList<>();
        final List<String> words = new ArrayList<>();
        int texts;
        Checking(float w, float h, String where) { this.w = w; this.h = h; this.where = where; }
        @Override public void fill(float l, float t, float r, float b, int color) { fillBoxes.add(new float[]{l, t, r, b, color}); }
        @Override public void text(String text, float x, float baseline, float sp, CardArt.Face face, int color) {
            texts++; words.add(text);
            check(sp >= CardArt.LEAST_SP, "Text at 12 sp or more: " + text + " at " + sp + ", " + where);
            float right = x + CardArt.width(text, sp, face);
            check(x >= -.5f && right <= w + .5f && baseline - CardArt.em(sp) >= -.5f && baseline <= h + .5f, "Text inside its box: \"" + text + "\" " + x + "–" + right + " of " + w + ", baseline " + baseline + " of " + h + ", " + where);
        }
        @Override public void icon(CardArt.Icon icon, float l, float t, float size, int color) { icons.add(icon); }
        @Override public void picture(Object picture, float l, float t, float r, float b) { if (picture != null) pictures.add(new float[]{l, t, r, b}); }
    }

    public static void main(String[] args) throws Exception {
        Assets.install(Assets.folder(Assets.FOLDER));
        list();
        screens();
        days();
        pageTemplate();
        pageSettings();
        System.out.println("PASS: the settings list and its screens fit both tablets in both turns, its rows 40 dp or more and their words side by side, the tools and rules fixed once locked, every word 12 sp or more inside its box, "
                + "the choice in use a grey tile with the ink tab, a long name in two lines, the last day added to kept in the file and the recovery, a page's own template; "
                + "Page Settings' rows, words, chips and actions, fitting both tablets in both turns");
    }

    static void list() {
        String longName = "Oct 20 bashful-cherry diary - no erasing - 5 min - 3 brushes";
        NotebookOptions d = NotebookOptions.DEFAULT, random = d.withTemplate(NotebookTemplate.random(Arrays.asList(NotebookTemplate.GRID, NotebookTemplate.LINED)));
        // The rows, all on the one page (no More since 2026-10-27, the owner: "just include everything on that page"), in
        // three groups: the page, the rules, the cover.
        check(LibraryCard.GROUPS.equals(Arrays.asList(
                Arrays.asList(LibraryCard.Part.TEMPLATE, LibraryCard.Part.RANDOM_FROM, LibraryCard.Part.PAGE_COLOUR, LibraryCard.Part.TRANSPARENCY, LibraryCard.Part.NIB_PREVIEW, LibraryCard.Part.SELF_PORTRAIT),
                Arrays.asList(LibraryCard.Part.NO_ERASING, LibraryCard.Part.TIMED, LibraryCard.Part.LOCK_PAGES, LibraryCard.Part.TOOLS),
                Arrays.asList(LibraryCard.Part.COVER, LibraryCard.Part.COVER_PICTURE))) && LibraryCard.rows().size() == 12, "The twelve rows in three groups " + LibraryCard.GROUPS);
        // Lock Pages (2026-10-27): Off and On in the row, greyed while nothing is timed.
        check(!LibraryCard.opens(LibraryCard.Part.LOCK_PAGES) && LibraryCard.value(LibraryCard.Part.LOCK_PAGES, d, CoverDesign.DEFAULT) == null
                && LibraryCard.tap(LibraryCard.Part.LOCK_PAGES, d) == LibraryCard.Tap.NOTHING && LibraryCard.key(LibraryCard.Part.LOCK_PAGES).equals("Lock Pages"), "Lock Pages is set in its row");
        // Nib Preview (2026-10-29): Off and On in the row, on by default.
        check(!LibraryCard.opens(LibraryCard.Part.NIB_PREVIEW) && LibraryCard.value(LibraryCard.Part.NIB_PREVIEW, d, CoverDesign.DEFAULT) == null && d.nibPreview
                && LibraryCard.tap(LibraryCard.Part.NIB_PREVIEW, d) == LibraryCard.Tap.NOTHING && LibraryCard.key(LibraryCard.Part.NIB_PREVIEW).equals("Nib Preview"), "Nib Preview is set in its row, on by default");
        // Self-Portrait Prompt (2026-10-30): Off and On in the row, off by default.
        check(!LibraryCard.opens(LibraryCard.Part.SELF_PORTRAIT) && LibraryCard.value(LibraryCard.Part.SELF_PORTRAIT, d, CoverDesign.DEFAULT) == null && !d.selfPortrait
                && LibraryCard.tap(LibraryCard.Part.SELF_PORTRAIT, d) == LibraryCard.Tap.NOTHING && LibraryCard.key(LibraryCard.Part.SELF_PORTRAIT).equals("Self-Portrait Prompt"), "Self-Portrait Prompt is set in its row, off by default");
        check(!LibraryCard.locksApply(d) && LibraryCard.locksApply(d.withSeconds(180)) && LibraryCard.locksApply(d.withTemplate(NotebookTemplate.DIARY_PAGE)), "and applies while a page is timed: Timed Pages or a timed template");
        check(!LibraryCard.opens(LibraryCard.Part.COVER_PICTURE)
                && LibraryCard.key(LibraryCard.Part.RANDOM_FROM).equals("Random Template") && LibraryCard.key(LibraryCard.Part.COVER_PICTURE).equals("Cover Picture")
                && LibraryCard.value(LibraryCard.Part.RANDOM_FROM, d, null).equals("Off") && LibraryCard.coverPicture(-1).equals("No Picture") && LibraryCard.coverPicture(2).equals("Page 3"),
                "Random Template says Off or its set; Cover Picture its page");
        try {
            String selector = DrawLayoutChecks.source("CardSelector");
            check(DrawLayoutChecks.source("DrawingPad").contains("lassoOn || !app.rules().nibPreview || stroke != null) { hideOutline(); return; }"), "Nib Preview off: the hover ring hides (the Dropper's and the eraser's rings stay)");
            check(selector.contains("if (!o.template.random()) templates.removeIf(NotebookTemplate::random);"), "The Template screen offers Random only to a notebook that is Random (Random Template sets it)");
        } catch (java.io.IOException e) { throw new AssertionError(e); }
        check(CardSheet.GUIDES_TITLE.equals("Template Visibility") && CardSheet.chips(CardSheet.Screen.TEMPLATE, d.withTemplate(NotebookTemplate.LINED)) && !CardSheet.chips(CardSheet.Screen.TEMPLATE, d)
                && !CardSheet.chips(CardSheet.Screen.PAGE_TEMPLATE, d.withTemplate(NotebookTemplate.LINED)), "The chips are on the Template screen, with a template");
        // What each row says.
        check(LibraryCard.value(LibraryCard.Part.TEMPLATE, d.withTemplate(NotebookTemplate.GRID), CoverDesign.SWIRL).equals("Grid") && LibraryCard.value(LibraryCard.Part.COVER, d, CoverDesign.SWIRL).equals(CoverDesign.SWIRL.label)
                && LibraryCard.value(LibraryCard.Part.TIMED, d.withSeconds(300), null).equals("5 min") && LibraryCard.value(LibraryCard.Part.TIMED, d, null).equals("Off")
                && LibraryCard.value(LibraryCard.Part.TOOLS, d, null).equals("My Toolbar") && LibraryCard.value(LibraryCard.Part.TEMPLATE, d.withTemplate(NotebookTemplate.LINED).withGuidesShown(NotebookOptions.FAINT), null).equals("Lined · Faint")
                && LibraryCard.value(LibraryCard.Part.TEMPLATE, d.withGuidesShown(NotebookOptions.FAINT), null).equals("Blank")
                && LibraryCard.value(LibraryCard.Part.RANDOM_FROM, random, null).equals("2 templates") && LibraryCard.value(LibraryCard.Part.RANDOM_FROM, d.withTemplate(NotebookTemplate.random(null)), null).equals("All")
                && LibraryCard.value(LibraryCard.Part.PAGE_COLOUR, d, null) == null, "Each row's value");
        check(LibraryCard.key(LibraryCard.Part.TRANSPARENCY).equals("See-Through Pages") && LibraryCard.key(LibraryCard.Part.NO_ERASING).equals("No Erasing") && LibraryCard.key(LibraryCard.Part.TIMED).equals("Timed Pages")
                && LibraryCard.key(LibraryCard.Part.PAGE_COLOUR).equals("Page Colour"), "The keys, Title Case, shown in caps");
        check(LibraryCard.opens(LibraryCard.Part.TEMPLATE) && LibraryCard.opens(LibraryCard.Part.COVER) && !LibraryCard.opens(LibraryCard.Part.PAGE_COLOUR) && !LibraryCard.opens(LibraryCard.Part.NO_ERASING) && !LibraryCard.opens(LibraryCard.Part.TRANSPARENCY), "› where a row opens a screen");
        LocalDate made = LocalDate.of(2026, 10, 23);
        check(LibraryCard.foot(made, null, 2).equals("Made Oct 23, 2026 · 2 pages") && LibraryCard.foot(made, made, 2).equals("Made Oct 23, 2026 · 2 pages")
                && LibraryCard.foot(null, made, 1).equals("1 page"), "The foot: " + LibraryCard.foot(made, null, 2));
        check(LibraryCard.foot(made, LocalDate.of(2026, 10, 30), 6).equals("Oct 23 – 30, 2026 · 6 pages") && LibraryCard.foot(made, LocalDate.of(2026, 11, 2), 6).equals("Oct 23 – Nov 2, 2026 · 6 pages")
                && LibraryCard.foot(made, LocalDate.of(2027, 1, 4), 6).equals("Oct 23, 2026 – Jan 4, 2027 · 6 pages"), "The foot's range, made to last edit: " + LibraryCard.foot(made, LocalDate.of(2026, 10, 30), 6));
        // See-Through Pages' Off and On: On is Page Before at the strength it had; Nearby Pages stays as it is; Off is none.
        TracingPaper faint = TracingPaper.NONE.withStrength(TracingPaper.FAINT), nearby = TracingPaper.NONE.withPaper(TracingPaper.GLASS);
        check(LibraryCard.seeThrough(faint, true).paper == TracingPaper.TRACING && LibraryCard.seeThrough(faint, true).strength == TracingPaper.FAINT
                && LibraryCard.seeThrough(nearby, true) == nearby && LibraryCard.seeThrough(nearby, false).paper == TracingPaper.PLAIN, "See-Through Pages' Off and On");
        // Every value a row can say in words, built in (an imported template's long file name is cut with …).
        java.util.Map<LibraryCard.Part, List<String>> values = new java.util.EnumMap<>(LibraryCard.Part.class);
        List<String> templateValues = new ArrayList<>(), randomValues = new ArrayList<>(Arrays.asList("Off", "All", NotebookTemplate.choices().size() + " templates")), timed = new ArrayList<>(), covers = new ArrayList<>(), tools = new ArrayList<>();
        for (NotebookTemplate t : NotebookTemplate.choices()) {
            randomValues.add(t.label);
            for (int step = 0; step < NotebookOptions.GUIDE_STEPS.length; step++) templateValues.add(LibraryCard.value(LibraryCard.Part.TEMPLATE, d.withTemplate(t).withGuidesShown(step), null));
        }
        templateValues.add(LibraryCard.value(LibraryCard.Part.TEMPLATE, random.withGuidesShown(NotebookOptions.HIDDEN), null));
        for (int sec : NotebookOptions.SECONDS) timed.add(LibraryCard.value(LibraryCard.Part.TIMED, d.withSeconds(sec), null));
        for (CoverDesign c : CoverDesign.values()) covers.add(LibraryCard.value(LibraryCard.Part.COVER, d, c));
        for (NotebookOptions.Tools t : NotebookOptions.Tools.values()) tools.add(t.label);
        values.put(LibraryCard.Part.TEMPLATE, templateValues); values.put(LibraryCard.Part.RANDOM_FROM, randomValues);
        values.put(LibraryCard.Part.TIMED, timed); values.put(LibraryCard.Part.COVER, covers); values.put(LibraryCard.Part.TOOLS, tools);
        check(templateValues.contains("Dot Grid · Faint") && timed.contains("Off") && covers.contains("Crackle"), "The values the owner saw broken are among them");
        // On both tablets in both turns, every row and the longest name: rows 40 dp or more, the name in two lines at most,
        // nothing scrolling but on the Nomad turned; each row's key beside its widest value in one line (the owner,
        // 2026-10-27: "some text is cut off": "Of / f", "Crackl / e"), measured in Ukulele's own width, or its buttons.
        for (int[] s : NotebookLayout.SCREENS) {
            NotebookLayout nl = new NotebookLayout(s[0], s[1], NotebookLayout.aspectOf(s));
            String at = s[0] + "x" + s[1];
            for (String name : new String[]{"Oct 20 notebook", longName}) {
                LibraryCard.Layout l = LibraryCard.settingsLayout(nl.width - 2 * nl.margin, nl.spreadHeight, name);
                check(l.scrolls == (l.height > nl.spreadHeight + .5f) && (!l.scrolls || nl.compact && s[0] > s[1]) && l.rowHeight >= LibraryCard.Layout.LEAST_ROW && l.titleLines <= 2 && l.titleSp >= TypeStyle.BODY.sp
                        && LabText.fits(name, l.width, LabText.face(l.titleSp, 0), 2), "The list fits at " + at + ", or scrolls on the Nomad turned: " + l.height + " of " + nl.spreadHeight + ", rows " + l.rowHeight + ", name " + l.titleSp + " sp");
                float inner = l.width - 2 * LibraryCard.Layout.PAD, chips = 2 * (2 * 8 + LabText.width("Off", TypeStyle.CAPTION.sp, LabText.UKULELE));
                for (LibraryCard.Part p : LibraryCard.rows()) {
                    if (values.containsKey(p)) {
                        for (String v : values.get(p)) check(LibraryCard.Layout.valueWidth(v) <= LibraryCard.Layout.valueRoom(p, l.width),
                                p + "'s value \"" + v + "\" in one line beside its key at " + at + ": " + LibraryCard.Layout.valueWidth(v) + " of " + LibraryCard.Layout.valueRoom(p, l.width));
                        continue;
                    }
                    float value = p == LibraryCard.Part.PAGE_COLOUR ? 6 * LibraryCard.Layout.SWATCH + 6 * LibraryCard.Layout.SWATCH_GAP : chips;
                    check(LibraryCard.Layout.keyWidth(p) + 8 + value <= inner, p + "'s key and buttons side by side at " + at + ": " + (LibraryCard.Layout.keyWidth(p) + 8 + value) + " of " + inner);
                }
                check(LibraryCard.Layout.SWATCH + CardArt.TAB + 3 <= l.rowHeight, "A swatch and its tab in the row at " + at);
            }
        }
        // A tap: the name renames, No Erasing toggles, the rest open their screens (Lock Settings cut, 2026-10-24: nothing is fixed).
        // Random Template opens Random From whatever the template (it is how Random is set, from More).
        check(LibraryCard.tap(LibraryCard.Part.RANDOM_FROM, d) == LibraryCard.Tap.CHOOSE
                && LibraryCard.tap(LibraryCard.Part.PAGE_COLOUR, d) == LibraryCard.Tap.NOTHING && LibraryCard.tap(LibraryCard.Part.COVER_PICTURE, d) == LibraryCard.Tap.NOTHING,
                "Random Template opens its screen; Page Colour and Cover Picture are set in their rows");
        d = random;
        for (LibraryCard.Part p : LibraryCard.Part.values()) {
            // Show Through, Layers and Move are Page Settings' alone; Page Colour, Cover Picture, Lock Pages and Nib Preview set in their rows.
            if (p == LibraryCard.Part.LOCK_PAGES || p == LibraryCard.Part.NIB_PREVIEW || p == LibraryCard.Part.SELF_PORTRAIT || p == LibraryCard.Part.PAGE_COLOUR || p == LibraryCard.Part.SHOW_THROUGH || p == LibraryCard.Part.COVER_PICTURE || p == LibraryCard.Part.LAYERS || p == LibraryCard.Part.MOVE) continue;
            LibraryCard.Tap tap = LibraryCard.tap(p, d);
            check(p == LibraryCard.Part.NAME ? tap == LibraryCard.Tap.RENAME : p == LibraryCard.Part.NO_ERASING ? tap == LibraryCard.Tap.TOGGLE
                    : tap == LibraryCard.Tap.CHOOSE && LibraryCard.screen(p) != null, p + ": what a tap does");
        }
        check(LibraryCard.tap(null, d) == LibraryCard.Tap.NOTHING, "A tap on nothing does nothing");
        check(LibraryCard.eye(TracingPaper.NONE) == CardArt.Icon.EYE_CLOSED && LibraryCard.eye(TracingPaper.NONE.withPaper(TracingPaper.TRACING)) == CardArt.Icon.EYE_HALF, "Page Transparency's eye: closed when off, else its strength's");
        check(TracingPaper.TRANSPARENCY.equals("See-Through Pages"), "Renamed: See-Through Pages");
    }
    static float[] mid(float[] b) { return new float[]{(b[0] + b[2]) / 2, (b[1] + b[3]) / 2}; }

    static void screens() {
        List<NotebookTemplate> templates = NotebookTemplate.choices();
        List<NotebookTemplate> many = new ArrayList<>(templates);
        for (int i = 0; i < 8; i++) many.add(many.size() - 1, NotebookTemplate.of("my:Template " + i + ".png"));
        List<NotebookTemplate> lots = new ArrayList<>(templates);
        for (int i = 0; i < 60; i++) lots.add(lots.size() - 1, NotebookTemplate.of("my:T" + i + ".png"));
        for (int i = 0; i < CardSheet.TRANSPARENCY_CHOICES; i++) check(CardSheet.transparencyChoice(CardSheet.transparency(i)) == i, "Page Transparency choice " + i + " round trips");
        NotebookOptions d = NotebookOptions.DEFAULT;
        check(CardSheet.chosen(CardSheet.Screen.TEMPLATE, d.withTemplate(NotebookTemplate.random(Arrays.asList(NotebookTemplate.GRID))), null, null, templates, null) == templates.size() - 1
                && CardSheet.chosen(CardSheet.Screen.COVER, d, null, CoverDesign.SWIRL, templates, null) == CoverDesign.SWIRL.ordinal()
                && CardSheet.chosen(CardSheet.Screen.PAGE_TEMPLATE, d, null, null, templates, NotebookTemplate.MUSIC) == templates.indexOf(NotebookTemplate.MUSIC), "The one in use is marked: Random with a set, a cover, a page's own");
        for (int[] s : NotebookLayout.SCREENS) {
            NotebookLayout nl = new NotebookLayout(s[0], s[1], NotebookLayout.aspectOf(s));
            float w = nl.width - 2 * nl.margin, h = nl.height - 2 * nl.margin - NotebookLayout.BAR - nl.gap;
            for (CardSheet.Screen screen : CardSheet.Screen.values()) {
                for (List<NotebookTemplate> offered : screen == CardSheet.Screen.TEMPLATE ? Arrays.asList(templates, many, lots) : Arrays.asList(templates)) {
                    String[] names = CardSheet.names(screen, offered);
                    CardSheet.Layout l = new CardSheet.Layout(w, h, nl.compact, screen, names.length, nl.aspect, 99, screen == CardSheet.Screen.TEMPLATE);
                    String at = screen + " (" + names.length + ") at " + s[0] + "x" + s[1];
                    // Two lines for a picture's name (2026-10-23) leave less room: 22 templates may scroll too on the Nomad turned (998 x 749).
                    check((l.scrolls == (offered == lots) || offered == many && l.scrolls) && (l.scrolls || l.height <= h + .5f), "Fits, nothing scrolling but a long Template grid: " + at + ", " + l.height + " of " + h);
                    Checking head = new Checking(w, l.headHeight, "head of " + at);
                    CardSheet.head(head, l, CardSheet.title(screen));
                    for (int i = 0; i < names.length; i++) {
                        float[] b = l.choices.get(i);
                        check(b[0] >= 0 && b[2] <= w + .5f && b[1] >= l.headHeight - .5f, "Choice " + i + " in the room: " + at);
                        if (screen == CardSheet.Screen.TEMPLATE && names[i].startsWith("T")) continue;
                        if (i == 0 && CardSheet.pictured(screen)) longName(l.look, b[2] - b[0] - 2 * l.look.pad);
                        for (boolean on : new boolean[]{false, true}) {
                            Checking pen = new Checking(b[2] - b[0], b[3] - b[1], names[i] + ", " + at);
                            CardArt.choice(pen, b[2] - b[0], b[3] - b[1], names[i], CardSheet.note(screen, i, on), CardSheet.icon(screen, i, offered), CardSheet.pictured(screen), null, nl.aspect, on, true, l.look);
                            // A grey-200 tile, never ink-filled; in use, the ink tab at its foot alone (one mark).
                            float cw = b[2] - b[0], ch = b[3] - b[1];
                            boolean grey = false, filled = false, tab = false;
                            for (float[] f : pen.fillBoxes) {
                                boolean whole = f[0] <= 0 && f[1] <= 0 && f[2] >= cw && f[3] >= ch;
                                grey |= whole && (int) f[4] == CardArt.GREY_200; filled |= whole && (int) f[4] == CardArt.INK;
                                tab |= (int) f[4] == CardArt.INK && f[3] >= ch - .5f && f[3] - f[1] == CardArt.TAB && f[2] - f[0] >= cw / 2;
                            }
                            check(grey && !filled && tab == on, "A grey tile, the ink tab only in use: " + names[i] + ", " + at);
                            if (CardSheet.note(screen, i, on) != null) check(LabText.fits(CardSheet.note(screen, i, on), b[2] - b[0] - 2 * l.look.pad - l.look.icon - l.look.pad, t -> CardArt.width(t, l.look.noteSp, CardArt.Face.DUO), l.look.noteLines), "A note in its lines: " + CardSheet.note(screen, i, on) + ", " + at);
                        }
                    }
                    if (l.chips != null) {
                        check(l.chips[0] >= 0 && l.chips[2] <= w + .5f && l.chips[1] >= l.headHeight - .5f && l.chips[3] <= l.choices.get(0)[1] + .5f, "The chips between the head and the choices: " + at);
                        for (int step = 0; step < NotebookOptions.GUIDE_STEPS.length; step++) {
                            Checking pen = new Checking(l.chips[2] - l.chips[0], l.chips[3] - l.chips[1], "chips of " + at);
                            CardArt.chips(pen, l.chips[2] - l.chips[0], CardSheet.GUIDES_TITLE, NotebookOptions.GUIDE_STEPS, step, CardSheet.ABOUT_SP);
                            float cw = (l.chips[2] - l.chips[0] - 3 * CardArt.CHIP_GAP) / 4, mid = CardArt.chipsHeight(CardSheet.ABOUT_SP) - CardArt.CHIP_HEIGHT / 2;
                            check(CardArt.chipAt(step * (cw + CardArt.CHIP_GAP) + cw / 2, l.chips[2] - l.chips[0], mid, CardSheet.ABOUT_SP, 4) == step && CardArt.chipAt(1, l.chips[2] - l.chips[0], 1, CardSheet.ABOUT_SP, 4) == -1, "A touch on a chip finds it: " + step + ", " + at);
                        }
                    }
                    if (l.slider != null) {
                        check(l.slider[0] >= 0 && l.slider[2] <= w + .5f && l.slider[3] <= h + .5f, "The slider in the room: " + at);
                        int stops = CardSheet.stops(screen);
                        for (int stop : new int[]{0, stops / 2, stops - 1}) {
                            Checking pen = new Checking(l.slider[2] - l.slider[0], l.slider[3] - l.slider[1], "slider of " + at);
                            CardArt.slider(pen, l.slider[2] - l.slider[0], CardSheet.sliderHead(screen), CardSheet.sliderWord(screen, stop), stop, stops, CardSheet.marks(screen), CardSheet.markWords(screen), l.sliderLook);
                            check(CardArt.stopAt(CardArt.x(stop, stops, l.sliderLook.thumb / 2, l.slider[2] - l.slider[0] - l.sliderLook.thumb / 2), l.slider[2] - l.slider[0], stops, l.sliderLook) == stop, "A touch on a stop finds it: " + stop + ", " + at);
                        }
                    }
                }
            }
        }
    }

    /** A template's long file name with nowhere to break takes two lines, not cut at once (the audit's "2020103119122…"). */
    static void longName(CardArt.Choice look, float tile) {
        // A tile narrower than the name (the Nomad's, or a longer name), and the tile itself.
        float w = Math.min(tile, CardArt.width("20201031191227_isometric", look.nameSp, CardArt.Face.DUO_BOLD) * .6f);
        List<String> lines = CardArt.wrap("20201031191227_isometric", w, look.nameSp, CardArt.Face.DUO_BOLD, 2);
        check(lines.size() == 2 && lines.get(0).startsWith("2020") && !lines.get(0).endsWith("…") && CardArt.width(lines.get(0), look.nameSp, CardArt.Face.DUO_BOLD) <= w, "A long name broken onto two lines: " + lines);
        check(CardArt.wrap("Wide Lined", 400, look.nameSp, CardArt.Face.DUO_BOLD, 2).equals(Arrays.asList("Wide Lined")), "A short one whole");
    }

    /** The last day added to: a page added or changed is today's; kept in the file and the recovery; none before. */
    static void days() throws Exception {
        DrawingBook book = new DrawingBook(new ToneDocument(300, 400));
        long[] today = {20000};
        book.today = () -> today[0];
        check(book.lastAdded() == null, "A new notebook, nothing drawn: not known");
        BlankPagesChecks.mark(book.current());
        check(book.lastAdded().toEpochDay() == 20000, "Drawn on, unsaved: today");
        book.markSaved(book.snapshot());
        today[0] = 20005;
        check(book.lastAdded().toEpochDay() == 20000, "Saved, it stays the day it was added");
        book.setOptions(NotebookOptions.DEFAULT.withNoErasing(true));
        check(book.lastAdded().toEpochDay() == 20000, "A setting isn't a page added to");
        book.addPage();
        check(book.lastAdded().toEpochDay() == 20005, "A page added: today");
        byte[] file = NotebookOptionsChecks.bytes(book);
        DrawingBook read = BookCodec.read(new ByteArrayInputStream(file));
        check(read.lastAdded() != null && read.lastAdded().toEpochDay() == 20005, "Kept in the file (LAST)");
        ByteArrayOutputStream recovery = new ByteArrayOutputStream();
        RecoveryCodec.write(recovery, book.snapshot(), "");
        check(RecoveryCodec.read(new ByteArrayInputStream(recovery.toByteArray())).book.lastAdded().toEpochDay() == 20005, "and in the recovery");
        DrawingBook old = new DrawingBook(new ToneDocument(300, 400));
        byte[] plain = NotebookOptionsChecks.bytes(old);
        DrawingBook before = BookCodec.read(new ByteArrayInputStream(plain));
        check(before.lastAdded() == null, "A file from before it: not known");
        check(Arrays.equals(NotebookOptionsChecks.bytes(before), plain), "and written again byte for byte while no page changes");
    }

    /** A page's own template: set by a hold, kept apart from Random's, put back by the notebook's own, kept in the file. */
    static void pageTemplate() throws Exception {
        DrawingBook book = new DrawingBook(new ToneDocument(300, 400));
        book.addPage(); book.addPage();
        book.setOptions(NotebookOptions.DEFAULT.withTemplate(NotebookTemplate.LINED));
        book.setPageTemplate(1, NotebookTemplate.MUSIC);
        check(book.template(0) == NotebookTemplate.LINED && book.template(1) == NotebookTemplate.MUSIC && book.template(2) == NotebookTemplate.LINED, "One page its own, the rest the notebook's");
        book.setOptions(NotebookOptions.DEFAULT.withTemplate(NotebookTemplate.GRID));
        check(book.template(1) == NotebookTemplate.MUSIC && book.template(0) == NotebookTemplate.GRID, "The notebook's template changed: the page keeps its own");
        book.setOptions(NotebookOptions.DEFAULT.withTemplate(NotebookTemplate.random(null)));
        check(book.template(1) == NotebookTemplate.MUSIC && !book.template(0).random() && book.template(0) != NotebookTemplate.BLANK, "Under Random too");
        book.setOptions(NotebookOptions.DEFAULT.withTemplate(NotebookTemplate.GRID));
        book.setPageTemplate(1, NotebookTemplate.GRID);
        check(book.template(1) == NotebookTemplate.GRID && book.pageTemplates().get(1) == null, "The notebook's own puts it back");
        book.setPageTemplate(2, NotebookTemplate.HEX);
        DrawingBook read = BookCodec.read(new ByteArrayInputStream(NotebookOptionsChecks.bytes(book)));
        check(read.template(2) == NotebookTemplate.HEX && read.template(0) == NotebookTemplate.GRID, "Kept in the file");
        check(NotebookOptions.DEFAULT.withTemplate(NotebookTemplate.random(null)).pageTemplate("=hex") == NotebookTemplate.HEX && NotebookTemplate.of("=hex") == NotebookTemplate.BLANK, "Its key is told from Random's (an older build reads it as Blank)");
    }

    /**
     * Layers in Page Settings (the refocus, 2026-10-24: out of the side column): every page has the row; a page alone has
     * Add; a page that has layers keeps them, a chip each ("1" the bottom, the one drawn on chosen) to draw on, and Add
     * while the stack has room; Merge Old where a page has the old in-page layers. Move: Earlier, Later.
     */
    static void layers() {
        check(Arrays.equals(LibraryCard.layerChips(1, false, false), new String[]{"Add"}) && Arrays.equals(LibraryCard.layerChips(3, false, false), new String[]{"1", "2", "3", "Add"})
                && Arrays.equals(LibraryCard.layerChips(PageStacks.MOST, true, false), new String[]{"1", "2", "3", "4"}) && Arrays.equals(LibraryCard.layerChips(1, false, true), new String[]{"Add", "Merge Old"}),
                "Layers' chips: a layer each to draw on, Add while there is room, Merge Old for old in-page layers");
        check(LibraryCard.layersValue(1, 0).equals("One") && LibraryCard.layersValue(3, 1).equals("Layer 2 of 3") && LibraryCard.layersValue(3, -1).equals("3 layers"), "What the row says");
        check(Arrays.equals(LibraryCard.MOVE_CHIPS, new String[]{"Earlier", "Later"}) && LibraryCard.key(LibraryCard.Part.LAYERS).equals("Layers") && LibraryCard.key(LibraryCard.Part.MOVE).equals("Move"), "Move: Earlier and Later");
        for (String w : new String[]{LibraryCard.ADD_LAYER, LibraryCard.MERGE_OLD, LibraryCard.EARLIER, LibraryCard.LATER}) check(w.split(" ").length <= 2 && Character.isUpperCase(w.charAt(0)), w + ": two words, Title Case");
        try {
            String screens = DrawLayoutChecks.source("NotebookScreens"), turner = DrawLayoutChecks.source("PageTurner");
            check(screens.contains("app.drawOn(page);") && screens.contains("app.newLayer(true)") && screens.contains("app.moveStack(place, to)") && screens.contains("app.mergeLayers()"),
                    "A layer's chip draws on it, Add adds one on top, Move moves the page, Merge Old merges");
            check(turner.contains("if (gesture.tick(SystemClock.uptimeMillis()) != StripGesture.Kind.HELD) return;") && turner.contains("options(place);") && !turner.contains("lift("),
                    "A hold on a page's preview opens Page Settings once the hold is seen, not when the pen lifts (the owner, 2026-10-24)");
        } catch (java.io.IOException e) { throw new AssertionError(e); }
    }
    /** Page Settings (a hold on a page): its rows, what each says, its chips and actions, all fitting with nothing scrolling. */
    static void pageSettings() {
        NotebookOptions d = NotebookOptions.DEFAULT, lined = d.withTemplate(NotebookTemplate.LINED);
        TracingPaper off = TracingPaper.plain(3), on = off.withPaper(TracingPaper.TRACING);
        List<LibraryCard.Part> seven = Arrays.asList(LibraryCard.Part.TEMPLATE, LibraryCard.Part.PAGE_COLOUR, LibraryCard.Part.SHOW_THROUGH, LibraryCard.Part.NO_ERASING, LibraryCard.Part.TIMED,
                LibraryCard.Part.LAYERS, LibraryCard.Part.MOVE);
        check(LibraryCard.pageRows(on, true).equals(seven), "The rows: " + LibraryCard.pageRows(on, true));
        check(!LibraryCard.pageRows(off, true).contains(LibraryCard.Part.SHOW_THROUGH) && !LibraryCard.pageRows(on, false).contains(LibraryCard.Part.SHOW_THROUGH) && LibraryCard.pageRows(off, true).size() == 6,
                "Show Through only while See-Through Pages is on, and not for a page of layers");
        layers();
        check(LibraryCard.pageTitle(2).equals("Page 3") && LibraryCard.key(LibraryCard.Part.SHOW_THROUGH).equals("Show Through") && LibraryCard.PAGE_SETTINGS.equals("Page Settings"), "Its title and keys");
        // What each row says.
        check(LibraryCard.pageValue(LibraryCard.Part.TEMPLATE, lined, NotebookTemplate.LINED, false, 255, 0, 0, false).equals("Notebook's (Lined)")
                && LibraryCard.pageValue(LibraryCard.Part.TEMPLATE, lined, NotebookTemplate.GRID, true, 255, 0, 0, false).equals("Grid"), "Template: the notebook's, or its own");
        check(LibraryCard.pageValue(LibraryCard.Part.PAGE_COLOUR, d, null, false, 255, 0, 0, false).equals("Notebook's (White)")
                && LibraryCard.pageValue(LibraryCard.Part.PAGE_COLOUR, d, null, false, 0xbb, 0, 0, false).equals(NotebookOptions.paperWord(0xbb))
                && LibraryCard.pageValue(LibraryCard.Part.PAGE_COLOUR, d, null, false, 0xa0, 0, 0, false).equals(NotebookOptions.paperWord(0xa0)), "Page Colour: the notebook's, a swatch, an older grey kept as it is");
        check(LibraryCard.pageValue(LibraryCard.Part.SHOW_THROUGH, d, null, false, 255, 0, TracingPaper.UNSET, false).equals("Solid")
                && LibraryCard.pageValue(LibraryCard.Part.SHOW_THROUGH, d, null, false, 255, 0, TracingPaper.FAINT, true).equals("Faint, Ghost"), "Show Through: the step (Solid when never set), and Ghost");
        for (int i = 0; i < LibraryCard.SHOW_STEPS.length; i++) check(LibraryCard.stepChip(LibraryCard.chipStep(i)) == i, "Step chip " + i + " sets its step");
        check(LibraryCard.chipStep(0) == TracingPaper.UNSET && Arrays.asList(LibraryCard.SHOW_STEPS).equals(Arrays.asList("Solid", "Half", "Faint", "Hidden")), "Solid is a page as new");
        int erasingOn = NotebookRules.withOwn(0, NotebookRules.ERASING_BITS, NotebookRules.OWN_ON), timedOff = NotebookRules.withOwn(0, NotebookRules.TIMED_BITS, NotebookRules.OWN_OFF);
        check(Arrays.equals(LibraryCard.ruleChoices(LibraryCard.Part.NO_ERASING, d), new String[]{"Notebook's (Off)", "Off", "On"})
                && Arrays.equals(LibraryCard.ruleChoices(LibraryCard.Part.TIMED, d), new String[]{"Notebook's (Off)", "Off", "On"})
                && Arrays.equals(LibraryCard.ruleChoices(LibraryCard.Part.TIMED, d.withSeconds(90)), new String[]{"Notebook's (90 s)", "Off", "On"}), "The rules' chips: the notebook's, Off, On");
        int timedOn = NotebookRules.withOwn(0, NotebookRules.TIMED_BITS, NotebookRules.OWN_ON);
        check(LibraryCard.timeSlider(timedOn) && LibraryCard.timeSlider(erasingOn | timedOn) && !LibraryCard.timeSlider(timedOff) && !LibraryCard.timeSlider(erasingOn) && !LibraryCard.timeSlider(0),
                "The time's slider under Timed Pages while the page is timed On alone");
        check(LibraryCard.pageValue(LibraryCard.Part.NO_ERASING, d, null, false, 255, 0, 0, false).equals("Notebook's (Off)") && LibraryCard.pageValue(LibraryCard.Part.NO_ERASING, d, null, false, 255, erasingOn, 0, false).equals("On")
                && LibraryCard.pageValue(LibraryCard.Part.TIMED, d.withSeconds(300), null, false, 255, erasingOn | timedOff, 0, false).equals("Off"), "A rule row says the page's choice");
        check(LibraryCard.pageOpens(LibraryCard.Part.TEMPLATE) && !LibraryCard.pageOpens(LibraryCard.Part.TIMED) && !LibraryCard.pageOpens(LibraryCard.Part.NO_ERASING) && !LibraryCard.pageOpens(LibraryCard.Part.SHOW_THROUGH), "› on Template alone");
        // Its page template screen: the notebook's template is the choice that puts the page back to it.
        List<NotebookTemplate> templates = NotebookTemplate.choices();
        templates.removeIf(NotebookTemplate::random);
        String[] names = CardSheet.names(CardSheet.Screen.PAGE_TEMPLATE, templates, lined);
        check(names[templates.indexOf(NotebookTemplate.LINED)].equals("Notebook's (Lined)") && names[templates.indexOf(NotebookTemplate.GRID)].equals("Grid")
                && CardSheet.names(CardSheet.Screen.TEMPLATE, templates, lined)[templates.indexOf(NotebookTemplate.LINED)].equals("Lined"), "Notebook's (Lined) on a page's own Template screen alone");
        // The foot: Delete alone (the others are the layer menu's, the page bar's drag, the menu's and the eraser's hold).
        check(LibraryCard.DELETE.equals("Delete") && LibraryCard.ACTION_ROWS == 1, "Delete alone at the foot, a row");
        // On both tablets in both turns: the rows and the actions fit, nothing scrolls, each row's key beside its chips.
        for (int[] s : NotebookLayout.SCREENS) {
            NotebookLayout nl = new NotebookLayout(s[0], s[1], NotebookLayout.aspectOf(s));
            String at = s[0] + "x" + s[1];
            float tall = nl.height - 2 * nl.margin - NotebookLayout.BAR - nl.gap;
            LibraryCard.Layout l = new LibraryCard.Layout(nl.width - 2 * nl.margin, tall, seven.size() + 1 + LibraryCard.ACTION_ROWS, 0, "Page 24");
            check(l.height <= tall + .5f && l.rowHeight >= LibraryCard.Layout.LEAST_ROW, "Page Settings fit at " + at + ": " + l.height + " of " + tall);
            float inner = l.width - 2 * LibraryCard.Layout.PAD;
            for (LibraryCard.Part p : seven) {
                float key = LibraryCard.Layout.keyWidth(p), value = 0;
                String[] chips = p == LibraryCard.Part.PAGE_COLOUR ? new String[]{LibraryCard.NOTEBOOKS} : p == LibraryCard.Part.SHOW_THROUGH ? LibraryCard.SHOW_STEPS
                        : p == LibraryCard.Part.TEMPLATE ? new String[0] : LibraryCard.ruleChoices(p, d.withSeconds(3600));
                for (String c : chips) value += 2 * 8 + LabText.width(c, TypeStyle.CAPTION.sp, LabText.UKULELE);
                if (p == LibraryCard.Part.PAGE_COLOUR) value += 6 * (LibraryCard.Layout.SWATCH + LibraryCard.Layout.SWATCH_GAP);
                if (p == LibraryCard.Part.SHOW_THROUGH) value += 8 + 32;
                if (p == LibraryCard.Part.TEMPLATE) value = LibraryCard.Layout.THUMB + 8 + LibraryCard.Layout.valueWidth("Notebook's (Wide Lined)") + 8 + 18;
                check(key + value <= inner, "Page Settings' " + p + " fits at " + at + ": " + (key + value) + " of " + inner);
            }
            // The time's row: a slider a good thumb's travel wide beside the longest time.
            float time = LibraryCard.Layout.valueWidth(NotebookRules.timeLabel(NotebookOptions.SECONDS[NotebookOptions.SECONDS.length - 1])) + 8;
            check(inner - time >= 160, "The time's slider has room at " + at + ": " + (inner - time));
            check(LibraryCard.actionHeight(l.rowHeight) <= l.rowHeight, "Delete in its row at " + at);
        }
    }
}
