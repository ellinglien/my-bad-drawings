package io.github.mpdairy.monopaint;

import java.util.ArrayList;
import java.util.List;

/**
 * A notebook settings' screen (the owner, 2026-10-21; opened from the settings list, {@link LibraryCard}, by {@link
 * CardSelector}): under the bar's Back, its title and a line saying what it does (no rule under them: the one header
 * style, 2026-10-23), then its choices as grey tiles ({@link CardArt#choice}): each its name and what it does (Template's
 * and Cover Design's a picture instead), the one in use with the ink tab at its foot. An ordered choice is a slider with
 * its value big over it (Timed Pages; Brushes under Tools' choices). Everything at once, nothing scrolling, but a
 * Template grid too long for the screen. Pure: its words, its choices and where they go, checked on the desktop for both
 * tablets in both turns ({@code LibraryCardChecks}).
 */
final class CardSheet {
    /** The screens: the settings list's, a page's own template (a hold on it in the strip), the front cover's (a hold on it), Random's set. */
    enum Screen { TEMPLATE, PAGE_TEMPLATE, TRANSPARENCY, TOOLS, TIMED, COVER, RANDOM_FROM }

    /** Each screen's title and what it does (docs/TEXT-STYLE.md: Title Case, then a sentence-case line). */
    static String title(Screen s) {
        switch (s) {
            case TEMPLATE: case PAGE_TEMPLATE: return NotebookLayout.TEMPLATE;
            case TRANSPARENCY: return TracingPaper.TRANSPARENCY;
            case TOOLS: return NotebookLayout.TOOLS;
            case TIMED: return NotebookRules.TIMED_PAGES;
            case COVER: return NotebookCovers.DESIGN;
            default: return NotebookLayout.RANDOM_FROM;
        }
    }
    static String about(Screen s, int page) {
        switch (s) {
            case TEMPLATE: return "The guides on every page";
            case PAGE_TEMPLATE: return "The guides on page " + (page + 1) + " alone";
            case TRANSPARENCY: return "Other pages seen through the one drawn on";
            case TOOLS: return "Where the bar's brushes come from";
            case TIMED: return "A countdown on every page";
            case COVER: return "The pattern on both covers";
            default: return "The templates Random picks from";
        }
    }
    /** Whether a screen's choices are pictures (a page's shape): templates and covers. */
    static boolean pictured(Screen s) { return s == Screen.TEMPLATE || s == Screen.PAGE_TEMPLATE || s == Screen.COVER || s == Screen.RANDOM_FROM; }
    /** Whether a screen has a slider: Timed Pages alone, Tools' Brushes under its choices. */
    static boolean slides(Screen s) { return s == Screen.TIMED || headed(s); }
    /** Whether a screen's slider has a head, beside its choices: Tools' Brushes. */
    static boolean headed(Screen s) { return s == Screen.TOOLS; }

    // Choices

    /** What each Tools choice does (in {@link NotebookOptions.Tools}' order). */
    // The owner, 2026-10-22: "it's not clear how to disable the constraint .. do i select my toolbar? library?": My Toolbar
    // says it is the free one, and Each Page that its brushes can't be changed.
    static final String[] TOOL_NOTES = {"The app's toolbar, free choice", "Eight brushes by chance", "Picked from the library", "New every page, can't change"},
            TRANSPARENCY_NOTES = {"Only the page drawn on", "The page before, at half", "The page before, faintly", "Four nearest pages, at half", "Four nearest pages, faintly"};
    /** Template Visibility (the owner, 2026-10-23: with the template, not a row of its own): a row of chips on the Template screen, Full, Half, Faint, Hidden. */
    static final String GUIDES_TITLE = "Template Visibility";
    /** Whether the Template screen has the visibility chips: a template to see (Blank has none). */
    static boolean chips(Screen s, NotebookOptions o) { return s == Screen.TEMPLATE && LibraryCard.guidesShown(o); }
    /** Surprise Me's note while it is in use: a tap rolls again. */
    static final String ROLL_AGAIN = "Tap again to roll new ones";
    /** The Brushes slider's head: how many of the bar's slots show (All: every one, and the smudge tool), not where they come from. */
    static final String BRUSHES = "Brushes on Bar";
    /** A headed slider's head, or null: Brushes on Bar. */
    static String sliderHead(Screen s) { return s == Screen.TOOLS ? BRUSHES : null; }

    /** Page Transparency's choices, in order: Off, then each paper at Half and at Faint. */
    static TracingPaper transparency(int choice) {
        return TracingPaper.NONE.withPaper((choice + 1) / 2).withStrength(choice > 0 && choice % 2 == 0 ? TracingPaper.FAINT : TracingPaper.HALF);
    }
    static final int TRANSPARENCY_CHOICES = 2 * TracingPaper.PAPERS.length - 1;
    /** Which of {@link #transparency}'s choices {@code tracing} is. */
    static int transparencyChoice(TracingPaper tracing) { return tracing.paper == TracingPaper.PLAIN ? 0 : 2 * tracing.paper - 1 + (tracing.strength == TracingPaper.FAINT ? 1 : 0); }

    /** The names of a screen's choices ({@code templates}: the templates offered, {@link NotebookTemplate#choices}, or Random From's). */
    static String[] names(Screen s, List<NotebookTemplate> templates) {
        String[] words;
        switch (s) {
            case TEMPLATE: case PAGE_TEMPLATE: case RANDOM_FROM:
                words = new String[templates.size()]; for (int i = 0; i < words.length; i++) words[i] = templates.get(i).label; return words;
            case TRANSPARENCY:
                words = new String[TRANSPARENCY_CHOICES]; for (int i = 0; i < words.length; i++) words[i] = String.join(", ", LibraryCard.transparency(transparency(i))); return words;
            case TOOLS: words = new String[NotebookOptions.Tools.values().length]; for (int i = 0; i < words.length; i++) words[i] = NotebookOptions.Tools.values()[i].label; return words;
            case COVER: words = new String[CoverDesign.values().length]; for (int i = 0; i < words.length; i++) words[i] = CoverDesign.values()[i].label; return words;
            default: return new String[0];
        }
    }
    /**
     * As {@link #names(Screen, List)}, for a notebook with {@code o}: on a page's own template's screen the notebook's
     * template (Random, under Random) is "Notebook's (Lined)", the choice that puts the page back to the notebook's.
     */
    static String[] names(Screen s, List<NotebookTemplate> templates, NotebookOptions o) {
        String[] words = names(s, templates);
        if (s == Screen.PAGE_TEMPLATE) for (int i = 0; i < words.length; i++) {
            NotebookTemplate t = templates.get(i);
            if (o.template.random() ? t.random() : t.equals(o.template)) words[i] = LibraryCard.notebooks(words[i]);
        }
        return words;
    }
    /** Choice {@code i}'s note ({@code inUse}: it is the one in use), or null. */
    static String note(Screen s, int i, boolean inUse) {
        switch (s) {
            case TOOLS: return inUse && i == NotebookOptions.Tools.SURPRISE.ordinal() ? ROLL_AGAIN : TOOL_NOTES[i];
            case TRANSPARENCY: return TRANSPARENCY_NOTES[i];
            default: return null;
        }
    }
    /** Choice {@code i}'s icon, or null. */
    static CardArt.Icon icon(Screen s, int i, List<NotebookTemplate> templates) {
        switch (s) {
            case TRANSPARENCY: return LibraryCard.eye(transparency(i));
            case TEMPLATE: case PAGE_TEMPLATE: return templates.get(i).random() ? CardArt.Icon.DICE : null;
            default: return null;
        }
    }
    /** Which choice is in use (−1: none, e.g. a template whose file is gone); {@code page}'s own for {@link Screen#PAGE_TEMPLATE}. */
    static int chosen(Screen s, NotebookOptions o, TracingPaper tracing, CoverDesign design, List<NotebookTemplate> templates, NotebookTemplate page) {
        switch (s) {
            case TEMPLATE: for (int i = 0; i < templates.size(); i++) if (templates.get(i).equals(o.template) || templates.get(i).random() && o.template.random()) return i; return -1;
            case PAGE_TEMPLATE: return templates.indexOf(page);
            case TRANSPARENCY: return transparencyChoice(tracing);
            case TOOLS: return o.tools.ordinal();
            case COVER: return design.ordinal();
            default: return -1;
        }
    }

    /**
     * Random From's Done: Random drawing from {@code ticked} of {@code offered}; every one ticked is any built-in, unless the
     * owner's own templates are among them (any built-in would leave them out).
     */
    static NotebookTemplate randomFrom(List<NotebookTemplate> ticked, List<NotebookTemplate> offered) {
        boolean every = ticked.size() == offered.size();
        for (NotebookTemplate t : ticked) if (t.own()) every = false;
        return NotebookTemplate.random(every ? null : ticked);
    }

    // Sliders

    /** Brushes' stops, left to right: 1 to 8, then All (0). */
    static int brushesAt(int stop) { return stop >= NotebookOptions.MOST_BRUSHES ? 0 : stop + 1; }
    static int brushesStop(int brushes) { return brushes == 0 ? NotebookOptions.MOST_BRUSHES : brushes - 1; }
    static final int BRUSH_STOPS = NotebookOptions.MOST_BRUSHES + 1;
    /** A slider's words at stop {@code i}: Timed Pages' time ("5 min"), Brushes' count or All. */
    static String sliderWord(Screen s, int i) {
        return s == Screen.TIMED ? NotebookRules.timeLabel(NotebookOptions.SECONDS[i]) : brushesAt(i) == 0 ? NotebookLayout.ALL : String.valueOf(brushesAt(i));
    }
    static int stops(Screen s) { return s == Screen.TIMED ? NotebookOptions.SECONDS.length : BRUSH_STOPS; }
    /** The stops a slider names under its track: Timed's Off, 5, 30 and 60 min; every one of Brushes'. */
    static int[] marks(Screen s) {
        if (s == Screen.TIMED) return new int[]{0, 10, 35, NotebookOptions.SECONDS.length - 1};
        int[] all = new int[BRUSH_STOPS]; for (int i = 0; i < all.length; i++) all[i] = i; return all;
    }
    static String[] markWords(Screen s) { int[] m = marks(s); String[] w = new String[m.length]; for (int i = 0; i < m.length; i++) w[i] = sliderWord(s, m[i]); return w; }

    // Where it goes

    /** A screen's sizes on a tablet {@code compact} or not. */
    static float titleSp(boolean compact) { return compact ? 26 : 32; }
    /** The line under the title: a caption ({@link TypeStyle#CAPTION}). */
    static final float ABOUT_SP = 13, GAP_TALL = 16;
    /** A choice's look; a picture's name in two lines, wrapped, never cut at once (2026-10-23). */
    static CardArt.Choice look(boolean compact, boolean pictured) {
        return pictured ? (compact ? new CardArt.Choice(8, 13, 2, 13, 0, 28) : new CardArt.Choice(10, 14, 2, 13, 0, 32)) : new CardArt.Choice(compact ? 14 : 18, compact ? 16 : 18, 1, 13, 2, compact ? 28 : 32);
    }
    static CardArt.Slider slider(boolean compact) { return new CardArt.Slider(compact ? 36 : 44, 13); }
    /** The least width of a choice that isn't a picture (its note's two lines), of a picture's, and its least picture, dp. */
    static final float LEAST_WIDE = 260, LEAST_NARROW = 120, LEAST_PICTURE = 64;

    /** Where a screen's parts go in its room {@code w × h} dp (under the bar, inside the margin). */
    static final class Layout {
        final float w, h, titleSp, headHeight;
        final List<String> about;
        /** The choices' boxes, {l, t, r, b}; the slider's (null: none) and its head's baseline. */
        final List<float[]> choices = new ArrayList<>();
        final float[] slider;
        /** The visibility chips' box (null: none). */
        final float[] chips;
        /** The choices' grid: columns, and whether they pass the room (a Template grid too long: it scrolls). */
        final int columns;
        final boolean scrolls;
        /** All of it, dp tall (more than {@link #h} when it scrolls). */
        final float height;
        final CardArt.Choice look;
        final CardArt.Slider sliderLook;
        final boolean compact;

        Layout(float w, float h, boolean compact, Screen s, int count, double aspect, int page, boolean withChips) {
            this.w = w; this.h = h; this.compact = compact;
            titleSp = titleSp(compact);
            about = CardArt.wrap(about(s, page), w, ABOUT_SP, CardArt.Face.DUO, 2);
            headHeight = CardArt.line(titleSp) + about.size() * CardArt.line(ABOUT_SP) + GAP_TALL;
            boolean pictured = pictured(s);
            look = look(compact, pictured);
            sliderLook = slider(compact);
            float gap = compact ? 12 : 16;
            boolean headed = headed(s);
            float sliderBlock = slides(s) ? sliderLook.height(headed) + (headed ? gap * 2 : 0) : 0;
            float chipsHeight = withChips ? CardArt.chipsHeight(ABOUT_SP) : 0, chipsBlock = withChips ? chipsHeight + gap : 0;
            chips = withChips ? new float[]{0, headHeight, w, headHeight + chipsHeight} : null;
            float room = h - headHeight - chipsBlock - sliderBlock, top = headHeight + chipsBlock;
            int cols = 1; boolean scroll = false; float cellW = w, cellH = 0;
            if (count > 0 && pictured) {
                float bestPicture = -1;
                for (int c = 1; c <= count; c++) {
                    int rows = (count + c - 1) / c;
                    float cw = (w - (c - 1) * gap) / c, cell = (room - (rows - 1) * gap) / rows;
                    if (c > 1 && cw < LEAST_NARROW) break;
                    float picture = Math.min(cell - look.picturedWords(), (float) Math.floor((cw - 2 * look.pad) * aspect));
                    if (picture > bestPicture) { bestPicture = picture; cols = c; cellW = cw; cellH = picture + look.picturedWords(); }
                }
                if (bestPicture < LEAST_PICTURE) {
                    // Too many to show at once: as many columns as stay wide enough, the pictures a good size, scrolling.
                    cols = Math.max(1, (int) ((w + gap) / (LEAST_NARROW * 1.4f + gap)));
                    cellW = (w - (cols - 1) * gap) / cols;
                    cellH = (float) Math.min((cellW - 2 * look.pad) * aspect, 140) + look.picturedWords();
                    scroll = true;
                }
            } else if (count > 0) {
                float want = look.height(look.noteLines) + 24, least = look.height(look.noteLines);
                cols = -1;
                for (int c = 1; c <= count; c++) {
                    int rows = (count + c - 1) / c;
                    float cw = (w - (c - 1) * gap) / c, cell = (room - (rows - 1) * gap) / rows;
                    if (c > 1 && cw < LEAST_WIDE) break;
                    if (cell >= want || cols < 0 || cell > cellH) { cols = c; cellW = cw; cellH = Math.min(cell, want); }
                    if (cell >= want) break;
                }
                cols = Math.max(1, cols);
                cellH = Math.max(cellH, least);
            }
            columns = cols;
            float y = withChips && count == 0 ? chips[3] : top;
            for (int i = 0; i < count; i++) {
                int col = i % cols, row = i / cols;
                float l = col * (cellW + gap), t = top + row * (cellH + gap);
                choices.add(new float[]{l, t, l + cellW, t + cellH});
                y = t + cellH;
            }
            if (slides(s)) {
                float st = count > 0 ? y + gap * 2 : top + Math.max(0, (room - sliderLook.height(headed)) / 3);
                float sw = count > 0 ? w : Math.min(w, compact ? 640 : 760), sl = (w - sw) / 2;
                slider = new float[]{sl, st, sl + sw, st + sliderLook.height(headed)};
                y = slider[3];
            } else slider = null;
            scrolls = scroll;
            height = y;
        }
    }

    /** The screen's title and its line under it, {@code w} wide, no rule. */
    static void head(CardArt.Pen pen, Layout l, String title) {
        pen.text(title, 0, CardArt.middle(0, CardArt.line(l.titleSp), l.titleSp), l.titleSp, CardArt.Face.DUO_BOLD, CardArt.INK);
        float y = CardArt.line(l.titleSp);
        for (String s : l.about) { pen.text(s, 0, CardArt.middle(y, y + CardArt.line(ABOUT_SP), ABOUT_SP), ABOUT_SP, CardArt.Face.DUO, CardArt.GREY_800); y += CardArt.line(ABOUT_SP); }
    }
    private CardSheet() {}
}
