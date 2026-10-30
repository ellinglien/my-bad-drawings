package io.github.mpdairy.monopaint;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Tool kits (docs/archive/superpowers/specs/2026-10-22-tool-kits-design.md): the file, loading and Undo, the notebook's rules, the sheet's layout, marbling's notes. */
public final class ToolKitsChecks {
    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        data(); parsing(); tab(); loads(); bar(); fixed(); drawingUndo(); said(); notes(); layout();
        System.out.println("PASS: tool kits: " + ToolKits.all().size() + " kits from " + ToolKits.FILE + " (Marbling first: Sumi, Clear water, Comb), every brush a Basic tool, at most ten, words in the text style; a kit fills the first slots, the first selected, and one Undo on the Tools page (and on the drawing screen) puts every slot back; a bar too small shows more (Lock Settings cut); a fixed toolbar takes nothing; a slot picked says its step; clear water with nothing floating and a brush that prints the rings get their notes; the sheet and Load Kit fit both tablets in either turn");
    }

    static ToolKits.Kit kit(String name) {
        for (ToolKits.Kit k : ToolKits.all()) if (k.name.equals(name)) return k;
        throw new AssertionError("No kit " + name);
    }

    static void data() {
        List<ToolKits.Kit> kits = ToolKits.all();
        check(kits.size() >= 3 && kits.get(0).name.equals("Marbling"), "Marbling first, and the starter kits: " + kits.size());
        ToolKits.Kit marbling = kit("Marbling");
        check(marbling.brushes.size() == 3 && marbling.brushes.get(0).preset.equals("Sumi") && marbling.brushes.get(1).preset.equals("Clear water")
                && marbling.brushes.get(2).preset.equals("Comb") && marbling.eraser == null, "Marbling: Sumi, Clear water, Comb, in that order");
        check(kit("Sketch and Ink").eraser != null && EraseKind.LABELS[kit("Sketch and Ink").eraser.eraser].equals("Dry Media"), "Sketch and Ink sets the eraser to Dry Media");
        check(kit("Watercolour with Masking").brushes.get(0).preset.equals("Masking fluid"), "Watercolour with Masking starts with the mask");
        Set<String> names = new HashSet<>();
        for (ToolKits.Kit k : kits) {
            check(names.add(k.name), "Kits have names of their own: " + k.name);
            check(k.size() >= 1 && k.size() <= ToolSlots.COUNT, k.name + " fills one to ten slots: " + k.size());
            for (Brush b : k.tools()) check(b != null, k.name + ": every brush is a Basic tool on the Tools page: " + k.brushes);
            for (ToolKits.Role r : k.brushes) check(ToolPresets.find(ToolKits.preset(r.preset).brush()) != null, k.name + ": " + r.preset + " is offered, not retired");
            // This Brush's header has room for Cut Stamp or Load Kit, not both.
            for (Brush b : k.tools()) check(!b.parts.stamps(), k.name + ": no Stamp in a kit");
            VoiceChecks.readable(k.intro, k.name + "'s intro");
            check(ModularWords.sentences(k.intro).size() == 1 && k.intro.length() <= 100, k.name + "'s intro is one sentence: " + k.intro);
            check(!k.name.contains(";") && Character.isUpperCase(k.name.charAt(0)), k.name + ": a label");
            for (int i = 0; i < k.steps(); i++) {
                String said = k.said(i), job = said.substring(said.indexOf(' ') + 1);
                check(said.startsWith((i + 1) + " "), k.name + "'s steps are numbered in order: " + said);
                // A bar's words: three at most (docs/TEXT-STYLE.md rule 9), sentence case, no symbols.
                check(job.split(" ").length <= 3 && Character.isUpperCase(job.charAt(0)) && job.substring(1).equals(job.substring(1).toLowerCase()) && !job.matches(".*[;:→=].*"),
                        k.name + "'s step is a bar's words in sentence case: " + said);
            }
        }
        check(ToolKits.of(ToolKits.preset("Clear water").brush()) == marbling, "A kit's brush offers its kit");
        check(ToolKits.of(ToolKits.preset("Gel pen").brush()) == null, "A brush in no kit offers none");
        ModularBrush renamed = ToolKits.preset("Sumi").brush().parts.copy();
        renamed.name = "My sumi";
        check(ToolKits.of(Brush.of(renamed)) == null, "A Basic tool changed is no longer the kit's");
    }

    static void parsing() {
        List<ToolKits.Kit> some = ToolKits.parse(Arrays.asList("# Kit: Notes", "stray", "Kit: A", "Intro: One.", "Sumi: Drop ink", "Eraser Pen: Erase pen",
                "Kit: Empty", "Intro: Nothing in it.", "Kit: B", "Comb: Comb rings", "Kit: C", "Intro: Two.", "Eraser Nonsense: x", "Comb: Comb"));
        check(some.size() == 2 && some.get(0).name.equals("A") && some.get(1).name.equals("C"), "Kits with an intro and a brush, notes and strays left out: " + some.size());
        check(some.get(0).eraser != null && some.get(0).eraser.eraser == 1 && some.get(0).steps() == 2 && some.get(0).said(1).equals("2 Erase pen")
                && some.get(0).tool(1).equals("Eraser, Pen"), "An eraser step: its kind, last");
        check(some.get(1).eraser == null, "An eraser kind not known is left out");
    }

    /** The library's Kits tab: a card a kit, in the file's order, its first brush's stroke, "3 brushes"; no chips, whatever chip was chosen. */
    static void tab() {
        ToolsPage page = ToolsPageChecks.open(ToolsPageChecks.fresh(), 0);
        page.layout(1000, 16);
        page.filter(ToolsPage.Chip.DRY_MEDIA);
        page.tab(ToolsPage.Tab.KITS);
        List<ToolsPage.Card> cards = page.all();
        check(cards.size() == ToolKits.all().size() && page.chipsShown().isEmpty() && page.pages() == 1, "Kits: a card a kit, no chips: " + cards.size());
        for (int i = 0; i < cards.size(); i++) {
            ToolKits.Kit k = ToolKits.all().get(i);
            check(cards.get(i).kit == k && cards.get(i).name.equals(k.name) && cards.get(i).brush.sameAs(k.tools()[0]) && cards.get(i).line.equals(k.size() + " brushes") && !page.now(cards.get(i)),
                    "A kit's card: its name, its first brush, how many it puts on the bar: " + cards.get(i).line);
        }
        page.tab(ToolsPage.Tab.BASIC);
        check(page.filter() == ToolsPage.Chip.DRY_MEDIA, "The chip chosen waits for the other tabs");
        // The tabs, Kits among them, and the pages share their row.
        for (int[] s : DrawLayoutChecks.SCREENS) {
            ToolsLayout t = new ToolsLayout(s[0], s[1]);
            check(t.tabsWidth() + t.tabGap() + ToolsLayout.pagerWidth() <= t.sections, "The tabs, Kits among them, fit at " + s[0] + "x" + s[1]);
        }
    }

    static void loads() {
        ToolSlots slots = ToolsPageChecks.fresh();
        ToolsPage page = ToolsPageChecks.open(slots, 5);
        Brush[] opened = new Brush[ToolSlots.COUNT];
        for (int i = 0; i < opened.length; i++) opened[i] = page.current(i);
        ToolKits.Kit marbling = kit("Marbling");
        check(page.kit() == null, "The slot's brush (Wet gouache) is in no kit");
        page.put(ToolKits.preset("Comb").brush());
        check(page.kit() == marbling, "Comb picked: This Brush offers the Marbling kit");
        check(page.loadKit(marbling), "The kit loads");
        Brush[] tools = marbling.tools();
        for (int i = 0; i < tools.length; i++) check(page.current(i).sameAs(tools[i]), "The kit fills the first slots in order: slot " + i);
        for (int i = tools.length; i < ToolSlots.COUNT; i++) check(page.current(i).sameAs(i == 5 ? ToolKits.preset("Comb").brush() : opened[i]), "The other slots keep theirs: slot " + i);
        check(page.slot() == 0 && page.together() && page.canUndo() && page.bar() == ToolSlots.COUNT && page.barResult() == -1 && page.eraser() == -1, "Its first slot selected, one step, the bar as it was");
        check(!page.loadKit(marbling), "Loaded again: nothing changes, no step");
        page.undo();
        for (int i = 0; i < ToolSlots.COUNT; i++) check(page.current(i).sameAs(i == 5 ? ToolKits.preset("Comb").brush() : opened[i]), "One Undo puts every slot back: slot " + i);
        check(page.slot() == 5 && !page.together(), "and selects the slot selected before");
        page.undo();
        check(page.current(5).sameAs(opened[5]) && !page.canUndo(), "Then the pick before it");
        ToolKits.Kit sketch = kit("Sketch and Ink");
        page.loadKit(sketch);
        check(page.eraser() == 3, "A kit's eraser step sets the eraser to its kind");
        Brush[] results = page.results();
        check(results[0] != null && results[1] != null && results[2] == null, "Back takes the kit's slots their brushes, the rest unchanged");
        page.undo();
        check(page.eraser() == -1 && page.results()[0] == null, "Undo takes the eraser's kind back too");
    }

    static void bar() {
        ToolKits.Kit marbling = kit("Marbling");
        ToolsPage open = ToolsPageChecks.open(ToolsPageChecks.fresh(), 0);
        open.bar(2);
        check(open.raises(marbling), "A notebook's bar of two: the kit shows its third slot");
        open.loadKit(marbling);
        check(open.bar() == 3 && open.barResult() == 3, "Brushes on Bar goes to three");
        open.undo();
        check(open.bar() == 2 && open.barResult() == -1, "Undo puts the count back");
        // Lock Settings is cut (2026-10-24): no bar refuses a kit, it raises the count.
        ToolsPage three = ToolsPageChecks.open(ToolsPageChecks.fresh(), 0);
        three.bar(3);
        check(!three.raises(marbling) && three.loadKit(marbling), "A bar of three takes it as it is");
    }

    /** A fixed toolbar (Each Page's): the page says why and loads nothing; the slots take nothing either. */
    static void fixed() {
        NotebookOptions each = NotebookOptions.DEFAULT.withTools(NotebookOptions.Tools.EACH_PAGE, ToolsPageChecks.fresh().toolbar().lines());
        check(NotebookRules.toolsFixed(each) != null, "Each Page: the Tools page's note, as for a single pick: " + NotebookRules.toolsFixed(each));
        ToolSlots slots = ToolsPageChecks.fresh();
        slots.frozen = true;
        Brush before = slots.brush(0);
        boolean[] used = slots.useAll(kit("Marbling").tools());
        check(!used[0] && !used[1] && !used[2] && slots.brush(0).sameAs(before) && !slots.canUndo(), "A frozen toolbar takes no kit");
    }

    static void drawingUndo() {
        ToolSlots slots = ToolsPageChecks.fresh();
        slots.use(6, ToolKits.preset("Gel pen").brush());
        Brush[] was = new Brush[ToolSlots.COUNT];
        for (int i = 0; i < was.length; i++) was[i] = slots.brush(i);
        Brush[] kit = Arrays.copyOf(kit("Watercolour with Masking").tools(), ToolSlots.COUNT);
        boolean[] used = slots.useAll(kit);
        check(used[0] && used[1] && used[2] && !used[3], "The kit's slots change on the drawing screen");
        check(slots.undo() == 0 && slots.undone().equals(Arrays.asList(0, 1, 2)), "One Undo there takes back the whole kit: " + slots.undone());
        for (int i = 0; i < ToolSlots.COUNT; i++) check(slots.brush(i).sameAs(was[i]), "Every slot as before: " + i);
        check(slots.undo() == 6 && slots.undone().equals(Arrays.asList(6)) && !slots.canUndo(), "Then the pick before it, alone");
        // Past the Undo limit the kit is still one step.
        ToolSlots full = ToolsPageChecks.fresh();
        for (int i = 0; i < ToolSlots.UNDOS + 4; i++) full.use(7, i % 2 == 0 ? ToolKits.preset("Gel pen").brush() : ToolKits.preset("Ballpoint").brush());
        full.useAll(kit);
        check(full.undo() == 0 && full.undone().size() == 3, "A kit after many picks is one step too");
    }

    static void said() {
        ToolSlots slots = ToolsPageChecks.fresh();
        slots.useAll(Arrays.copyOf(kit("Marbling").tools(), ToolSlots.COUNT));
        Brush[] bar = new Brush[ToolSlots.COUNT];
        for (int i = 0; i < bar.length; i++) bar[i] = slots.brush(i);
        check("1 Drop ink".equals(ToolKits.said(bar, 0)) && "3 Comb rings".equals(ToolKits.said(bar, 2)), "A slot of a loaded kit says its step: " + ToolKits.said(bar, 2));
        check(ToolKits.said(bar, 4) == null, "A slot after the kit says its name");
        bar[1] = ToolKits.preset("Gel pen").brush();
        check(ToolKits.said(bar, 0) == null, "A kit no longer whole says names");
    }

    static void notes() {
        ModularBrush sumi = ToolKits.preset("Sumi").brush().parts, water = ToolKits.preset("Clear water").brush().parts, comb = ToolKits.preset("Comb").brush().parts;
        ModularBrush pen = ToolKits.preset("Gel pen").brush().parts, waterBrush = ToolKits.preset("Water brush").brush().parts;
        check(ToolKits.NOTHING_FLOATING.equals(ToolKits.floatNote(water, water, false)) && ToolKits.NOTHING_FLOATING.equals(ToolKits.floatNote(comb, comb, false)), "Clear water or the comb with nothing floating: its note");
        check(ToolKits.floatNote(sumi, sumi, false) == null && ToolKits.floatNote(water, water, true) == null && ToolKits.floatNote(comb, comb, true) == null, "Sumi starts the bath; water and comb on it say nothing");
        check(ToolKits.PRINTED.equals(ToolKits.floatNote(pen, pen, true)) && ToolKits.PRINTED.equals(ToolKits.floatNote(null, null, true)), "Another brush, or the eraser, prints the rings: its note");
        check(ToolKits.floatNote(waterBrush, ClearWater.afloat(waterBrush, true), true) == null, "Clear Water's smudge works on the water: no note");
        check(ToolKits.floatNote(pen, pen, false) == null, "Nothing floating, another brush: nothing to say");
        for (String n : new String[]{ToolKits.NOTHING_FLOATING, ToolKits.PRINTED}) check(n.split(" ").length <= 3 && n.length() <= 30, "A note in about three words: " + n);
    }

    static void layout() {
        for (int[] s : DrawLayoutChecks.SCREENS) {
            ToolsLayout t = new ToolsLayout(s[0], s[1]);
            String at = s[0] + "x" + s[1];
            check(t.sheetWidth() <= s[0] - 2 * t.side, "The kit sheet inside the page's sides at " + at);
            for (ToolKits.Kit k : ToolKits.all()) {
                check(t.sheetHeight(k) <= s[1] - 2 * t.gap, k.name + "'s sheet fits the height at " + at + ": " + t.sheetHeight(k));
                for (int i = 0; i < k.steps(); i++) check(ToolsLayout.stepFits(k, i, t.sheetRoom()), k.name + ": step " + k.said(i) + " and " + k.tool(i) + " share a row at " + at);
                String where = ToolsLayout.kitWhere(k.size(), true);
                check(LabText.width(where, ToolsLayout.WORDS_SP, 0) <= t.sheetRoom(), "Where it goes, one line at " + at + ": " + where);
            }
            check(ToolsLayout.buttonWidth(ToolKits.CANCEL) + 10 + ToolsLayout.buttonWidth(ToolKits.LOAD) <= t.sheetRoom(), "Cancel and Load Kit side by side at " + at);
        }
        check(ToolsLayout.kitWhere(3, false).equals("Fills the first 3 slots") && ToolsLayout.kitWhere(1, false).equals("Fills the first slot")
                && ToolsLayout.kitWhere(3, true).equals("Fills and shows the first 3 slots"), "Where it goes, in words");
    }

    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
