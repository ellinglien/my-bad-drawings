package io.github.mpdairy.monopaint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public final class LabLayoutChecks {
    static final int[] MANTA = {1024, 1365}, NOMAD = {749, 998};

    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        Texts texts = texts();
        for (int[] screen : new int[][]{MANTA, NOMAD}) {
            LabLayout l = new LabLayout(screen[0], screen[1]);
            fits(l);
            reads(l, texts);
        }
        wraps();
        table();
        System.out.println("PASS: Brush Lab fits the Manta and the Nomad with nothing scrolling: every region adds up to the screen (About This Brush under the bar, no toolbar band, the column beside the scratch pad, Randomize across the foot), the modules' top and the test area's line up, the six part modules sit two to a row, every part's dials fit its open card, sliders are at least 80 dp, the picker box lies across where the sizes and greys were with its 28 dp squares (24 on the Nomad), the Edited line under the name, a part's swap panel fits beside the column, the Open sheet's library fits, and the test area keeps the most room, more than with the toolbar band and Tweak It; no text is under 12 sp or cut: part names, their descriptions (Manta), the widest choice words, end words, swap notes, captions and the brush's first sentence fit their budgeted lines at the lab's 1.2× text in iA Writer Duo (duospace: m, w and a few more one and a half wide); About This Brush's buttons (New, Open, Save, Save as New) and the bar fit across; slim modules, dial labels (two lines), values, segments (under their label when they don't fit beside it) and the Control grid");
    }

    /** Whether {@code choice}'s choice dial {@code d} stacks its segments under its label in {@code l} (LabSettings.stacked). */
    static boolean stacks(LabLayout l, PartChoice choice, PartDial d) {
        String[] options = choice.optionsOf(d);
        return d != PartDial.DIR && d.choice() && !l.segmentsBeside(options) && !l.menu(options);
    }
    static void fits(LabLayout l) {
        String at = at(l);
        check(l.main == l.height - LabLayout.BAR, at + "main is the screen less the bar");
        check(2 * l.side + l.column + l.split + l.right == l.width, at + "the column, the gap and the right side add up to the width");
        // The band: About This Brush's header and block, the head row, the modules (or the test area), Randomize, the band's foot.
        int above = l.top + l.aboutHead + l.about + l.gap + l.headRow + l.gap, bottom = l.gap + l.randomize + l.bottom;
        check(above + 3 * l.module + 2 * l.moduleGap + bottom + l.spare == l.main && l.spare >= 0, at + "the column adds up to main, " + l.spare + " spare");
        check(above + l.testHeight + bottom == l.main, at + "the head row and the test area add up to main");
        check(l.about >= 2 * l.pad + l.nameHeight() + l.mark + LabLayout.NAME_GAP + l.words && l.about >= 2 * l.pad + l.pickerThick + l.mark && l.mark >= LabText.line(TypeStyle.CAPTION.sp),
                at + "About This Brush holds the name, the Edited line, the words, the picker box and a note's line: " + l.about);
        check(l.wordsWidth + l.loopWidth + LabLayout.LOOP_GAP + l.gap + l.picker + l.gap + l.readout + 2 * l.pad + 2 * l.side == l.width && l.loopWidth >= 48 && l.readout == l.pickerThick, at + "the loop, the words, the picker and its readout (a square as thick as the box) add up to the width");
        // The picker box turned a quarter: two cells across, each a square (28 dp, 24 on the Nomad) and its tab's room, as
        // SizeSlider and GreyStrip size them; as long as the sizes and greys were.
        int square = l.pickerCell - DrawLayout.FACE_GAP / 2 - DrawLayout.sliderMarkRoom();
        check(square == (l.compact ? 24 : DrawLayout.FACE_SQUARE) && l.pickerThick == 2 * l.pickerCell && l.picker >= 4 * (square + DrawLayout.FACE_GAP), at + "the picker box: squares " + square + ", " + l.picker + " x " + l.pickerThick);
        // The Open sheet under the header: its rows, the library's head and two rows of cards, above Randomize.
        int sheet = 12 + 44 + 10 + 44 + 10 + LabLayout.SECTION_BAR + l.shelfRows * l.cardHeight + 10 + (int) Math.ceil(LabText.line(TypeStyle.CAPTION.sp)) + 12;
        check(l.top + l.aboutHead + sheet <= l.main - l.randomize - l.bottom && (l.width - 2 * l.side - 32) / l.shelfColumns >= LabLayout.NARROWEST_TILE + 30, at + "the Open sheet fits: " + sheet);
        check(l.aboutButtons() <= l.width - 2 * l.side || l.backToWidth() >= 100, at + "About This Brush's header holds its buttons from its left, no label, Back to cut to " + l.backToWidth());
        check(l.adjustHead() <= l.column && l.testHead() <= l.right, at + "the head rows hold Start from ▾ and Clear: " + l.adjustHead() + " / " + l.testHead());
        check(LabLayout.bar(l.compact) <= l.width - 2 * l.side, at + "the bar's Back, title, switch and Undo fit: " + LabLayout.bar(l.compact));
        check(l.sampleHeight >= l.sampleMin && l.sampleHeight <= l.sampleMax, at + "a module's sample is " + l.sampleWidth + " x " + l.sampleHeight);
        for (int row = 0; row < 3; row++)
            check(l.rowHead(row) + l.rowChoice(row) + l.rowMacro(row) <= l.rowMost, at + "row " + row + "'s head and choice leave its sample at least the least");
        check(l.sampleWidth >= 100 && l.moduleWidth >= 150, at + "modules " + l.moduleWidth + " wide, samples " + l.sampleWidth);
        check(l.described == !l.compact, at + "descriptions on the Manta's modules only");
        {
            check(above + 3 * l.slim + 2 * l.moduleGap + l.pad + l.open + bottom == l.main, at + "the slim modules and the open card add up to main");
            int head = LabLayout.OPEN_HEAD + 10;
            for (BrushPart part : BrushPart.values()) {
                if (part == BrushPart.HAND) {
                    check(head + LabLayout.handHead() + BrushLab.HAND_ROWS.length * l.handCell() + 2 * 44 <= l.open && l.handCell() >= LabLayout.rowLeast(1),
                            at + "the Control grid's four rows fit its open card at " + l.handCell());
                    // Its rows under the grid: a Spiro's three drives, Grey Shift's own while it is on (a Pattern's
                    // Order too), each row as tall as its label needs, a choice dial's options beside it or a menu.
                    // Then Gradient's: To Grey's End Grey and Length, a Cycle's Greys and set.
                    for (PartChoice tip : new PartChoice[]{PartChoice.ROUND, PartChoice.SPIRO}) for (int shift = 0; shift < 4; shift++) for (int grad : new int[]{GreyGradient.OFF, GreyGradient.TO_GREY, GreyGradient.CYCLE}) {
                        ModularBrush b = SwatchChecks.brush(PartDial.SHIFT, shift, PartDial.GRADIENT, grad);
                        b.set(BrushPart.TIP, new ModularBrush.Part(tip));
                        List<PartDial> under = l.handRows(b);
                        int rows = under.size(), row = l.handRow(rows);
                        String which = at + tip + " with Grey Shift " + PartDial.SHIFT.choices[shift] + ", Gradient " + PartDial.GRADIENT.choices[grad] + ": ";
                        check(head + LabLayout.handHead() + BrushLab.HAND_ROWS.length * l.handCell(rows) + rows * row <= l.open && l.handCell(rows) >= LabLayout.rowLeast(1) && l.handRow(2) == 44,
                                which + "the Control card fits: cells " + l.handCell(rows) + ", rows " + row);
                        for (PartDial d : under) {
                            if (d == null || d == PartDial.DIR) continue;
                            check(row >= LabLayout.rowLeast(l.labelLines(d.label)), which + d.label + "'s row " + row + " holds its " + l.labelLines(d.label) + " lines");
                            check(!l.stacks(d, d.choices == null ? new String[0] : d.choices), which + d.label + "'s options lie beside it or in a menu");
                        }
                        // Everything on the Manta but with a Spiro's drives and Grey Shift on both; else where there's no
                        // room Gradient's own rows go first (its row stays), then its row, then Grey Shift's.
                        List<PartDial> all = BrushLab.handRows(b), none = BrushLab.handRows(b, false), neither = new java.util.ArrayList<>(none);
                        neither.removeIf(d -> d == null || Arrays.asList(PartDial.SHIFTS).contains(d));
                        boolean less = under.equals(all.subList(0, all.indexOf(PartDial.GRADIENT) + 1)) || under.equals(none) || under.equals(neither);
                        check(under.equals(all) || less && (l.compact || tip == PartChoice.SPIRO && shift > 0), which + "its rows " + under);
                        check(l.compact || tip == PartChoice.SPIRO || under.equals(all), which + "the Manta shows them all");
                    }
                    continue;
                }
                for (PartChoice choice : part.choices()) {
                    if (!ModularFeatures.offered(choice)) continue;
                    int dials = 0, stacked = 0;
                    // A choice dial that doesn't fit beside its label stacks under it, or on the Nomad is a menu button in its row.
                    // A Spiro shows its curve's dials (SpiroStamper.shows): the toy's, its default, the most.
                    ModularBrush.Part part0 = new ModularBrush.Part(choice);
                    for (PartDial d : choice.dials) if (l.shows(choice, d) && SpiroStamper.shows(part0, d)) { dials++; if (stacks(l, choice, d)) stacked++; }
                    if (dials == 0) continue;
                    int row = l.dialRow(dials, stacked);
                    check(head + stacked * LabLayout.stackedRow() + (dials - stacked) * row <= l.open, at + choice + "'s " + dials + " dials fit at " + row + "");
                    // Each row it shows is as tall as its label's lines need, at 12 sp (the Nomad hides Print Shading's own dials on most cards).
                    for (PartDial d : choice.dials)
                        if (l.shows(choice, d) && SpiroStamper.shows(part0, d) && !stacks(l, choice, d))
                            check(row >= LabLayout.rowLeast(l.labelLines(LabLayout.rowLabel(choice, d))), at + choice + "'s " + LabLayout.rowLabel(choice, d) + " row " + row + " holds its " + l.labelLines(LabLayout.rowLabel(choice, d)) + " lines");
                }
            }
            check(l.slider >= 80, at + "a dial's slider is " + l.slider);
        }
        check(l.testWidth >= 360 && l.testHeight >= 700, at + "test area " + l.testWidth + " x " + l.testHeight);
        // The name, beside its loop and its pencil: every Basic tool's and every style's whole on one line (a longer name
        // of one's own takes two lines at 16 sp on the Manta, or ends in "…").
        float longest = 0;
        for (ToolPresets.Preset p : ToolPresets.all()) longest = Math.max(longest, LabText.width(p.name, TypeStyle.HEADING.sp, 0));
        for (ModularStyle style : ModularStyle.all()) longest = Math.max(longest, LabText.width(style.label, TypeStyle.HEADING.sp, 0));
        check(l.nameWidth >= longest, at + "the name gets " + l.nameWidth + " for " + longest);
        // A swap strip holds 2 (Carry) to 9 (Hand) tiles in the under area. The tiles fill it, so a short, wide strip
        // can't always reach 1.35 : 1.
        int pw = l.right - 4, ph = l.swapRoom();
        for (int n = 2; n <= 9; n++) {
            int cols = l.swapColumns(n), rows = (n + cols - 1) / cols;
            double aspect = ((double) pw / cols) / ((double) ph / rows);
            check(aspect >= .6 && aspect <= 2.65 && rows * cols - n < cols, at + n + " swap tiles in " + cols + " columns are " + aspect + " : 1");
            // The swap panel, a modal beside the part's module: right of the column, inside the screen, about as tall as
            // the room under the test page (its lines between rows added), so it fits level with a module (moved up at the foot).
            int[] panel = l.swapPanel(n);
            check(panel[0] >= l.side + l.column && panel[0] + panel[1] <= l.width - l.side && panel[2] <= l.swap + 2 * rows
                    && panel[2] <= l.main - l.top - l.bottom && panel[2] >= LabLayout.SECTION_BAR + l.swapCell(n)[1],
                    at + n + " swap tiles: the panel " + panel[1] + " x " + panel[2] + " at " + panel[0] + " fits beside the column");
        }
        // Past thirteen tiles (the fourteen papers) a strip turns in pages of as many each: its head then holds the glyph,
        // the part's name, the library's page buttons round "2/2" and ✕.
        check(LabLayout.swapPerPage(13) == 13 && LabLayout.swapPerPage(14) == 7 && LabLayout.swapPerPage(BrushPart.PAPER.choices().size()) == 7, "Fourteen swap tiles: two pages of seven");
        float pagedHead = 2 * 10 + 20 + 8 + LabLayout.labelWidth(BrushPart.CARRY.label.toUpperCase(java.util.Locale.ROOT)) + 8 + 2 * l.pageButton + 2 * 4 + LabLayout.labelWidth("2/2") + 44;
        check(pagedHead <= l.right, at + "a paged swap strip's head fits: " + pagedHead + " in " + l.right);
    }

    /** Every text a lab shows, gathered from many rolled brushes in every style. */
    static final class Texts {
        final Map<BrushPart, Set<String>> choices = new HashMap<>();
        /** Each part's swap strips: one entry per strip, each tile's caption. */
        final Map<BrushPart, List<List<List<String>>>> swaps = new HashMap<>();
        final Set<String> descriptions = new LinkedHashSet<>(), captions = new LinkedHashSet<>();

        final Set<String> labels = new LinkedHashSet<>(), values = new LinkedHashSet<>();
        final Set<PartDial> segmented = new LinkedHashSet<>();
        /** Control's one strongest response, as the Nomad's module names it. */
        final Set<String> handOne = new LinkedHashSet<>();
    }
    static Texts texts() {
        Texts t = new Texts();
        for (BrushPart part : BrushPart.values()) { t.choices.put(part, new LinkedHashSet<>()); t.swaps.put(part, new ArrayList<>()); }
        Map<String, String> store = new HashMap<>();
        BrushLibrary.Store s = new BrushLibrary.Store() { public String get(String k) { return store.get(k); } public void put(String k, String v) { if (v == null) store.remove(k); else store.put(k, v); } };
        BrushLab lab = new BrushLab(new ToolSlots(s, new Random(1)), 2, new Random(7));
        Random random = new Random(8);
        // Every Basic tool's words too: its personality, then how it behaves (BrushVoice.about).
        for (ToolPresets.Preset p : ToolPresets.all()) t.descriptions.add(BrushVoice.about(p.brush().parts));
        for (int i = 0; i < ToolSlots.COUNT; i++) t.captions.addAll(BrushCaptions.describe(ToolSlots.first(i)));
        for (ModularStyle style : ModularStyle.all()) {
            lab.pickStyle(style);
            for (int i = 0; i < 400; i++) {
                lab.randomize();
                ModularBrush b = lab.parts();
                t.descriptions.add(BrushVoice.about(b));
                t.captions.addAll(BrushCaptions.describe(lab.brush()));
                for (BrushPart part : BrushPart.values()) {
                    t.choices.get(part).add(ModularWords.choiceWords(b, part));
                    if (part == BrushPart.HAND) t.handOne.add(ModularWords.handWords(b, 1));
                    if (i % 20 != 0) continue;
                    List<List<String>> strip = new ArrayList<>();
                    for (BrushLab.Swap swap : lab.swaps(part)) {
                        String note = ModularWords.swapNote(swap.brush.parts, part);
                        strip.add(note.isEmpty() ? Arrays.asList(ModularWords.tileLabel(swap.choice)) : Arrays.asList(ModularWords.tileLabel(swap.choice), note));
                    }
                    t.swaps.get(part).add(strip);
                }

            }
        }
        for (PartChoice c : PartChoice.values()) for (PartDial d : c.dials) {
            if (!ModularFeatures.shown(d)) continue;
            t.labels.add(c.labelOf(d));
            if (d.choice()) { t.segmented.add(d); continue; }
            double[] range = c.range(d);
            for (int k = 0; k <= 40; k++) t.values.add(d.shown(range[0] + (range[1] - range[0]) * k / 40));
        }
        return t;
    }

    /** No text in the column or on a card is cut, and none in Basic is under 13 sp, at {@code l}'s budgets. */
    static void reads(LabLayout l, Texts t) {
        String at = at(l);
        float small = LabLayout.SMALL_SP;
        // A module's head: its name left of the glyph, then (Manta) its fixed description across the head in its budgeted lines.
        LabText.Measure desc = LabText.face(small, 0);
        for (BrushPart part : BrushPart.values()) {
            String name = part.label.toUpperCase(Locale.ROOT);
            check(LabText.width(name, LabLayout.NAME_SP, .08f) <= l.nameRoom(), at + part + "'s name " + name + " in " + l.nameRoom());
            if (l.described) check(LabText.fits(ModularWords.inside(part), l.body, desc, l.descLines), at + part + "'s description in " + l.descLines + " lines of " + l.body);
            check(LabText.width(name, LabLayout.DENSE_SP, .07f) <= l.slimRoom(), at + part + "'s name on a slim module");
        }
        // Its choice, bold, centred, 16 sp down to 13: in as many lines as its row budgets; a slim module's at 12 sp.
        float dense = LabLayout.DENSE_SP;
        LabText.Measure choice = LabText.face(small, 0), slim = LabText.face(dense, 0);
        int slimBody = l.moduleWidth - 2 * LabLayout.MODULE_RULE - 12, openBody = l.openWidth - 16;
        // A slim module names Control's one strongest response (PartCard.words).
        check(l.slimLines * LabText.line(dense) <= l.slimChoice, at + "a slim module's choice holds " + l.slimLines + " lines");
        for (BrushPart part : BrushPart.values())
            for (String said : part == BrushPart.HAND && l.handResponses() == 1 ? t.handOne : t.choices.get(part)) {
                // A module may break after an arrow, as a dial's label (PartCard: a zero-width space after it).
                String words = said.replace("→", "→ ");
                check(Double.isInfinite(LabLayout.choiceEm(part)) || LabText.width(words, small, 0) <= LabLayout.choiceEm(part) * LabText.em(small) + .01, part + "'s widest choice words are budgeted: \"" + words + "\"");
                check(LabText.fits(words, l.body, choice, l.choiceLines(part)), at + part + " module: \"" + words + "\" in " + l.choiceLines(part) + " lines of " + l.body);
                if (part == BrushPart.HAND) continue;
                check(LabText.fits(words, slimBody, slim, l.slimLines), at + part + " slim: \"" + words + "\" in " + l.slimLines + " lines of " + slimBody);
            }
        for (String said : t.handOne)
            check(LabText.fits(said.replace("→", "→ "), slimBody, slim, l.slimLines), at + "Control slim: \"" + said + "\" in " + l.slimLines + " lines of " + slimBody);
        // The slider over its two end words; or what a choice without one means, in the sample's place too.
        for (PartChoice c : PartChoice.values()) {
            if (!ModularFeatures.offered(c)) continue;
            if (c.words != null) check((l.endLines(c.part) == 2 ? Math.max(LabText.width(c.words[0], small, 0), LabText.width(c.words[1], small, 0)) : LabText.width(c.words[0], small, 0) + 6 + LabText.width(c.words[1], small, 0)) <= l.body && l.macroRoom() >= 40, at + c + "'s end words and slider");
            else {
                float sp = l.macroSp();
                int lines = (int) ((l.sampleHeight + l.rowGap + l.macro) / LabText.line(sp));
                check(LabText.fits(ModularWords.noMacro(c), l.macroRoom(), LabText.face(sp, 0), lines), at + c + ": \"" + ModularWords.noMacro(c) + "\" in " + lines + " lines of " + l.macroRoom());
            }
        }
        check(l.wordsSp >= 12 && LabText.line(TypeStyle.LABEL.sp) <= l.headRow, at + "About This Brush's words at " + l.wordsSp + " sp, the labels in their rows");
        // About This Brush: whole sentences that fit its lines, at least the first (ToolsLayout.fitWords), never cut.
        LabText.Measure plain = LabText.face(l.wordsSp, 0);
        for (String text : t.descriptions) {
            check(text.length() <= LabLayout.DESCRIPTION && ModularWords.firstSentences(text, 2).length() <= LabLayout.TWO_SENTENCES
                    && ModularWords.sentences(text).get(0).length() <= ToolsLayout.FIRST_SENTENCE, "a description's length is budgeted (the Tools page's first sentence too): " + text);
            check(LabText.wrap(ModularWords.sentences(text).get(0), l.wordsWidth, plain).size() <= l.wordsLines, at + "About This Brush's " + l.wordsLines + " lines hold \"" + ModularWords.sentences(text).get(0) + "\"");
        }
        // The dial rows, all at 12 sp: the label in two lines (a break after an arrow), the value on one.
        {
            LabText.Measure label = LabText.face(dense, LabLayout.LABEL_TRACKING);
            // Every dial's label the open card shows (the Nomad's leaves Screen's own out: LabLayout.shows).
            Set<String> shownLabels = new LinkedHashSet<>();
            for (PartChoice c : PartChoice.values()) for (PartDial d : c.dials) if (l.shows(c, d)) shownLabels.add(c.labelOf(d));
            check(t.labels.containsAll(shownLabels), at + "every label shown is gathered");
            for (String name : shownLabels) {
                String shown = LabLayout.dialLabel(name).replace("\u200b", " ");
                check(LabText.fits(shown, l.label, label, 2), at + "the dial label " + name + " in two lines of " + l.label);
                // A label over its segments has the row's width, on one line.
                check(LabText.fits(shown, openBody, label, 1), at + "the dial label " + name + " on one line over its segments");
            }
            for (String value : t.values) check(LabText.width(value, dense, 0) <= l.value, at + "the value " + value + " in " + l.value);
            // A choice dial's segments: beside the label, in the slider's and the value's room; else under it, across the
            // row but the lock and die. Each as wide as its word is long, at 12 sp.
            // A menu button (the Nomad's Blend) shows its option and ▾ in the slider's and the value's room.
            for (PartDial d : t.segmented) for (PartChoice c : PartChoice.values()) {
                if (d == PartDial.DIR || !c.has(d)) continue;
                String[] options = c.optionsOf(d);
                if (l.menu(options)) {
                    for (String option : options) check(LabText.width(option + " ▾", dense, 0) + 2 * LabLayout.SEGMENT_PAD <= l.slider + 4 + l.value, at + c + "'s " + d + " menu " + option);
                    continue;
                }
                int total = 0;
                for (String option : options) total += option.length();
                float room = l.segmentsBeside(options) ? l.slider + 4 + l.value : openBody - 8 - 2 * LabLayout.LOCK;
                for (String option : options)
                    check(room * option.length() / (float) total - 2 >= LabText.width(option, dense, 0) + 2 * LabLayout.SEGMENT_PAD, at + c + "'s " + d + " segment " + option + " in " + room);
            }
            // The Control grid: its row names on one line, its heads (HandGrid.HEADS, caps) and levels ("+FULL") in a cell.
            float cell = (openBody - (l.handName + 4) - 8 - 2 * LabLayout.LOCK) / 3f;
            for (String row : BrushLab.HAND_ROWS) check(LabText.width(row.toUpperCase(Locale.ROOT), dense, LabLayout.LABEL_TRACKING) <= l.handName, at + "the Control row " + row);
            for (String head : new String[]{"SIZE", "DARK", "NIB"})
                check(LabText.width(head, dense, 0) + 2 <= cell, at + "the Control head " + head + " over a cell " + cell + " wide");
            // A level on its 3 dp patch either side (HandGrid.Cell).
            for (String level : new String[]{"+FULL", "−FULL", "−SOME", "+SOME", "+MAX", "−MAX"})
                check(LabText.width(level, dense, 0) + 6 <= cell, at + "the level " + level + " in a Control cell " + cell + " wide");
            check(LabLayout.handHead() >= 2 * LabText.line(dense) && l.handCell() >= LabText.line(dense) + 4, at + "the Control grid's heads and cells hold their 12 sp");
        }
        // The Edited line: "Edited" and the longest suggestion a name may be, cut with … past the room (never under 13 sp).
        check(LabText.width(LabLayout.EDITED, TypeStyle.CAPTION.sp, 0) + 12 + LabText.width(LabLayout.SUGGESTED + "Regrettable Inky", TypeStyle.CAPTION.sp, 0) <= l.wordsWidth, at + "the Edited line holds a suggestion");
        for (BrushPart part : BrushPart.values())
            for (List<List<String>> strip : t.swaps.get(part)) {
                // Paged as SwapStrip pages it: every page's grid laid out for a page's tiles.
                int[] cell = l.swapCell(LabLayout.swapPerPage(strip.size()));
                for (List<String> caption : strip) {
                    LabText.Caption fitted = LabText.caption(caption, null, cell[0] - 2, cell[1] - 2, l.swapLeast(), l.swapInset());
                    check(fitted.whole, at + part + "'s swap tile " + caption + " on " + (cell[0] - 2) + " x " + (cell[1] - 2));
                }
            }
    }

    /** Wrapping breaks at spaces only, keeps every word whole, and a caption shrinks before it drops the name. */
    static void wraps() {
        LabText.Measure m = LabText.face(10, 0);
        float x = LabText.width("x", 10, 0);
        check(LabText.wrap("press→tines open, fast→nib follows", 18.5f * x, m).equals(Arrays.asList("press→tines open,", "fast→nib follows")), "wraps at spaces: " + LabText.wrap("press→tines open, fast→nib follows", 18.5f * x, m));
        check(LabText.oneLine("2026-10-14 dapper-fig", 15 * x, m).equals("2026-10-14…"), "a name is cut at a word: " + LabText.oneLine("2026-10-14 dapper-fig", 15 * x, m));
        // Screentone's swap tile: whole where it fits, else the owner's short form, "Screen" / "Tone".
        String tone = ModularWords.tileLabel(PartChoice.TONE);
        check(LabText.wrap(tone, 12 * x, m).equals(Arrays.asList("Screentone")) && LabText.wrap(tone, 8 * x, m).equals(Arrays.asList("Screen", "Tone")),
                "Screentone breaks into Screen / Tone: " + LabText.wrap(tone, 8 * x, m));
        LabText.Caption c = LabText.caption(Arrays.asList("Brush pen, chisel", "ink, wet"), "brave-onion", 200, 150);
        check(c.whole && c.sp == 13 && c.name != null && c.lines.size() == 2, "a caption keeps its lines and the name at 13 sp: " + c.lines);
        LabText.Caption small = LabText.caption(Arrays.asList("Brush pen, chisel", "ink, wet"), "brave-onion", 200, 100);
        check(small.whole && small.sp == 13 && small.name == null, "and leaves the name out, never shrinking, when even a smaller sample leaves no room");
        // A long name wraps to two lines, cut with … only past them (the owner, 2026-10-22).
        LabText.Caption two = LabText.caption(Arrays.asList("Brush pen, chisel"), "Smoldering Stick of Embers", 170, 200);
        check(two.name != null && two.name2 != null && (two.name + " " + two.name2).equals("Smoldering Stick of Embers") && two.count() == 3, "a long name takes two lines: " + two.name + " / " + two.name2);
        List<String> cut = LabText.nameLines("Smoldering Stick of Embers and Ashes", 60, m, 2);
        check(cut.size() == 2 && cut.get(1).endsWith("…"), "and is cut with … past two: " + cut);
    }

    /** The spec's Budgets table, as built (2026-10-29: no toolbar band, no Basic, no Tweak It; the picker box across About This Brush). */
    static void table() {
        LabLayout ma = new LabLayout(MANTA[0], MANTA[1]), na = new LabLayout(NOMAD[0], NOMAD[1]);
        // Was, Advanced with the toolbar band and Tweak It: 580 x 704, 363 x 409.
        test(ma, 580, TABLE[0]); test(na, 363, TABLE[1]);
        check(ma.about == TABLE[2] && na.about == TABLE[3], "About This Brush " + ma.about + "/" + na.about);
        check(ma.moduleWidth == 199 && na.moduleWidth == 174 && ma.module == TABLE[4] && na.module == TABLE[5], "Modules: " + ma.moduleWidth + "x" + ma.module + "/" + na.moduleWidth + "x" + na.module);
        check(ma.sampleHeight == TABLE[6] && na.sampleHeight == TABLE[7], "Samples " + ma.sampleHeight + "/" + na.sampleHeight);
        check(ma.open == TABLE[8] && na.open == TABLE[9] && ma.slim == 80 && na.slim == 80, "Open card: Manta " + ma.open + ", Nomad " + na.open + "; slim modules " + ma.slim + "/" + na.slim);
        check(ma.slider == 153 && na.slider == 94, "Sliders: Manta " + ma.slider + ", Nomad " + na.slider);
        check(ma.picker == 344 && na.picker == 194 && ma.pickerThick == 72 && na.pickerThick == 64 && ma.readout == 72 && na.readout == 64, "The picker box and its readout (the Nomad's box 264 until the readout, 2026-10-29): " + ma.picker + " x " + ma.pickerThick + ", Nomad " + na.picker + " x " + na.pickerThick);
        check(ma.column == 404 && na.column == 354, "Columns: " + ma.column + "/" + na.column);
        check(ma.swapColumns(9) == 5 && na.swapColumns(9) == 3, "Nine swap tiles: 5 across on the Manta, 3 × 3 on the Nomad");
        check(ma.rowHead(0) == 98 && ma.rowHead(1) == 58 && ma.rowHead(2) == 78 && na.rowHead(0) == 30 && na.rowHead(1) == 30, "Heads as tall as their row's description: " + ma.rowHead(0) + "/" + ma.rowHead(1) + "/" + ma.rowHead(2) + ", Nomad " + na.rowHead(0) + "/" + na.rowHead(1));
    }
    /** The table's numbers: test heights (Manta, Nomad), About This Brush, modules, samples, open cards. */
    static final int[] TABLE = {1004, 702, 131, 106, 330, 223, 117, 80, 744, 444};
    static void test(LabLayout l, int width, int height) {
        check(l.testWidth == width && l.testHeight == height, l.width + " test area " + l.testWidth + " x " + l.testHeight + ", want " + width + " x " + height);
    }
    static String at(LabLayout l) { return l.width + "x" + l.height + ": "; }


    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
