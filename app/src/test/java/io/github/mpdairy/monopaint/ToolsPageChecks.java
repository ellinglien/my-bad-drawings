package io.github.mpdairy.monopaint;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public final class ToolsPageChecks {
    static Map<String, String> prefs = new HashMap<>();
    static BrushLibrary.Store store() {
        return new BrushLibrary.Store() {
            public String get(String key) { return prefs.get(key); }
            public void put(String key, String value) { if (value == null) prefs.remove(key); else prefs.put(key, value); }
        };
    }
    static final ModularStyle PENCIL = ModularStyle.named("Pencil"), WATERCOLOR = ModularStyle.named("Watercolor"), WILD = ModularStyle.named(ModularStyle.ANYTHING_GOES);

    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        chips(); refocus(); opens(); remembers(); tabs(); recent(); filters(); paging(); groups(); changes(); six(); tries(); backToStart(); keeps(); deletes(); lines(); layout(); table(); search();
        System.out.println("PASS: the Tools page: the ten slots, a tap choosing the one a card goes to, Undo taking back any slot's change and selecting it again, Back taking each slot its pick; one grid: Tools (the Basic tools), Recent (put in a slot lately, latest first, each once), Saved (the library but the Basic tools) and Kits, each under its chips (All, Pens, Markers, Brushes, Washes, Paint, Dry Media, Print, Odd Ones: every style in one; only those a tab has, the chosen one always; a kept style's name opens on its chip), paged, the Tools tab under All chip by chip, each group from a new row; Brush Lab in the bar, no New Brush, Brush Lab's Anything Goes a smaller plain button beside Randomize; Back to puts the slot's first brush back (an Undo step; the start carried through Brush Lab), a slot changed on this visit marked; an unsaved brush a pick or a kit lets go is kept in Recent, said once; a saved card deleted by a long press; opening on the tab and chip last chosen and the page holding the slot's tool; a card changes the tool on the page, not the slot, the page staying to try it on the scratch pad at the one size and grey, Undo taking each back, and Back returns it; every drawing cut at its handle and one width; every budget fitting both tablets in either turn");
    }

    static ToolSlots fresh() { prefs = new HashMap<>(); return ToolSlotsChecks.old(store(), new Random(1)); }
    static ToolsPage open(ToolSlots slots, int slot) { return new ToolsPage(slots, store(), slot); }
    static Brush rolled(ModularStyle style, int seed) { return Brush.of(style.roll(new Random(seed))); }

    /** The chips (the owner, 2026-10-28: "instead of dropdown show chips for the categories"): every style in exactly one. */
    static void chips() {
        ToolsPage.Chip[] chips = ToolsPage.Chip.values();
        check(chips[0] == ToolsPage.Chip.ALL && String.join(",", ToolsLayout.chipLabels()).equals("All,Pens,Markers,Brushes,Washes,Paint,Dry Media,Print,Odd Ones"),
                "The chips: All, then the kinds, Odd Ones last: " + String.join(",", ToolsLayout.chipLabels()));
        String[][] kinds = {{"Fineliner", "PENS"}, {"Dip pen", "PENS"}, {"Marker", "MARKERS"}, {"Brush pen", "BRUSHES"}, {"Dry brush", "BRUSHES"}, {"Hake", "BRUSHES"},
                {"Watercolor", "WASHES"}, {"Ink wash", "WASHES"}, {"Gouache", "PAINT"}, {"Oils", "PAINT"}, {"Pencil", "DRY_MEDIA"}, {"Charcoal", "DRY_MEDIA"},
                {"Pastels", "DRY_MEDIA"}, {"Print", "PRINT"}, {ModularStyle.ANYTHING_GOES, "ODD_ONES"}};
        for (String[] k : kinds) check(ToolsPage.Chip.of(ModularStyle.named(k[0])).name().equals(k[1]), k[0] + " under " + k[1]);
        check(kinds.length == ModularStyle.shown().size(), "Every style has its chip");
        for (ModularStyle style : ModularStyle.shown()) {
            int in = 0;
            for (ToolsPage.Chip c : chips) if (c != ToolsPage.Chip.ALL && c.has(style)) in++;
            check(in == 1 && ToolsPage.Chip.ALL.has(style), style.name + " in exactly one chip, and under All");
        }
        // Kept: the chip's name; a style's name, kept before the chips, its chip; anything else All.
        check(ToolsPage.Chip.kept("PENS") == ToolsPage.Chip.PENS && ToolsPage.Chip.kept("Pencil") == ToolsPage.Chip.DRY_MEDIA && ToolsPage.Chip.kept("Print") == ToolsPage.Chip.PRINT
                && ToolsPage.Chip.kept("Wild") == ToolsPage.Chip.ODD_ONES && ToolsPage.Chip.kept("") == ToolsPage.Chip.ALL && ToolsPage.Chip.kept("Nonsense") == ToolsPage.Chip.ALL,
                "A kept chip, or a kept style's chip");
    }
    /** The chips on the page in place of the Style button's menu (2026-10-28); no New Brush; Brush Lab's Anything Goes secondary. */
    static void refocus() {
        try {
            String tools = DrawLayoutChecks.source("ToolsActivity"), lab = DrawLayoutChecks.source("LabSettings"), card = DrawLayoutChecks.source("ToolCard");
            // Opening fast (the owner, 2026-10-28: "can it load faster?"): built before the first frame, the art boxes' pictures
            // kept across openings in a bounded cache and drawn in the background, the cards' screen-reader words made when asked.
            check(tools.contains("addOnPreDrawListener(") && !tools.contains("addOnLayoutChangeListener(") && tools.contains("\"Tools page shown in \""),
                    "The page laid out before its first frame, its time logged");
            // The owner, 2026-10-28: "make the non-selected tools a little grey": a solid grey, picture and name; the selected one black.
            check(card.contains("static final int GREY = Theme.GREY_600;") && card.contains("int ink = grey ? GREY : Theme.INK;") && tools.contains("card.setChosen(i == selected);")
                    && !card.contains("setAlpha("), "The slots not selected drawn in solid grey-600, the selected one in ink");
            check(card.contains("new LruCache<String, Bitmap>(4 << 20)") && card.contains("samples.request(this, () -> mask(") && !card.contains("picture.recycle()")
                    && !card.contains("drawing.draw(canvas)") && tools.contains("view.describe(() ->"), "Pictures kept (at most 4 MB) and drawn off the main thread; words made when asked");
            // The first opening too (the owner: slow "just on the first load"): drawn ahead once the drawing screen settles, by
            // the page's own tiles (showSlot, showCard: the same keys), never while the pen is down; let go when it leaves.
            String app = DrawLayoutChecks.source("PaintActivity");
            check(tools.contains("showSlot(new ToolCard(context, samples), layout, page, i)") && tools.contains("showSlot(card, layout, page, i)")
                    && tools.contains("showCard(new ToolCard(context, samples), layout, card)") && tools.contains("showCard(view, layout, card)")
                    && app.contains("if (busy() || pad.stroking()) { pad.postDelayed(this.warmTools, WARM_TOOLS_MS); return; }")
                    && app.contains("if (toolPictures != null) { toolPictures.close(); toolPictures = null; }"), "The page's first pictures drawn ahead, as the page shows them, never under the pen");
            check(!tools.contains("ToolsLayout.NEW") && !tools.contains("openStyles") && !tools.contains("PopupWindow"), "No New Brush, no Style menu");
            check(tools.contains("chips = new LabUi.Wrap(") && tools.contains("Theme.rounded(this, on ? Theme.INK : Theme.PAPER, Theme.FINE, Theme.INK"), "The chips on the page, wrapping, rounded, the chosen one filled");
            check(lab.contains("new LabButton(context, R.drawable.ic_ph_shuffle, \"Anything Goes\", false)") && lab.contains("inkButton(\"Randomize\"") && !lab.contains("inkButton(\"Anything Goes\""),
                    "Randomize the big ink button, Anything Goes a smaller plain one beside it");
        } catch (java.io.IOException e) { throw new AssertionError(e); }
    }
    static void opens() {
        ToolSlots slots = fresh();
        ToolsPage page = open(slots, 7);
        check(page.tab() == ToolsPage.Tab.BASIC && page.filter() == ToolsPage.Chip.WASHES, "The first time: Tools, the slot brush's chip");
        slots.use(2, rolled(WILD, 3));
        check(open(slots, 2).filter() == ToolsPage.Chip.ALL, "An Anything goes brush opens on All");
        for (int i = 0; i < 40; i++) slots.library.save(rolled(PENCIL, 100 + i));
        ToolsPage library = open(slots, 0);
        library.tab(ToolsPage.Tab.SAVED);
        library.filter(ToolsPage.Chip.ALL);
        library.layout(15, 14);
        check(library.page() == 2, "The grid opens on the page holding the slot's brush (Fineliner, after the 40 newer)");
        int now = 0;
        for (ToolsPage.Card c : library.cards()) if (library.now(c)) now++;
        check(now == 1, "Its card is outlined");
        slots.use(3, ToolPresets.of(ModularStyle.named("Ink wash")).get(3).brush());
        ToolsPage basic = open(slots, 3);
        basic.tab(ToolsPage.Tab.BASIC);
        basic.filter(ToolsPage.Chip.WASHES);
        basic.layout(2, 14);
        int at = 0;
        while (!basic.all().get(at).brush.sameAs(slots.brush(3))) at++;
        check(basic.page() == at / 2 && basic.now(basic.cards().get(at % 2)), "The Tools tab opens on the page holding the one the slot has");
    }
    static void remembers() {
        ToolSlots slots = fresh();
        ToolsPage page = open(slots, 0);
        page.tab(ToolsPage.Tab.SAVED);
        page.filter(ToolsPage.Chip.DRY_MEDIA);
        ToolsPage again = open(slots, 4);
        check(again.tab() == ToolsPage.Tab.SAVED && again.filter() == ToolsPage.Chip.DRY_MEDIA && "DRY_MEDIA".equals(prefs.get(ToolsPage.STYLE)), "The tab and the chip last chosen, on any slot");
        again.filter(ToolsPage.Chip.ALL);
        again.tab(ToolsPage.Tab.KITS);
        ToolsPage third = open(slots, 4);
        check(third.tab() == ToolsPage.Tab.KITS && third.filter() == ToolsPage.Chip.ALL, "All is remembered too (not the slot's chip again)");
        check("KITS".equals(prefs.get(ToolsPage.TAB)) && "".equals(prefs.get(ToolsPage.STYLE)), "Kept in the preferences");
        // The All tab is cut (the refocus, 2026-10-24): one kept from before opens on Basic tools.
        prefs.put(ToolsPage.TAB, "ALL");
        // A style kept by the Style menu (before 2026-10-28) opens on its chip.
        prefs.put(ToolsPage.STYLE, "Charcoal");
        check(open(slots, 4).filter() == ToolsPage.Chip.DRY_MEDIA, "A kept style opens on its chip");
        check(open(slots, 4).tab() == ToolsPage.Tab.BASIC, "A kept All tab opens on Basic tools");
        check(java.util.Arrays.toString(ToolsPage.Tab.values()).equals("[BASIC, RECENT, SAVED, KITS]") && ToolsPage.Tab.BASIC.label.equals("Tools"), "The tabs: Tools, Recent, Saved, Kits");
    }
    static void tabs() {
        ToolSlots slots = fresh();
        ToolsPage page = open(slots, 0);
        page.filter(ToolsPage.Chip.ALL);
        page.layout(1000, 16);
        check(page.all().size() == ToolPresets.all().size(), "Basic tools: every preset (" + page.all().size() + ")");
        List<ToolsPage.Card> grouped = page.all();
        for (int i = 0; i < grouped.size(); i++) check(grouped.get(i).name.equals(groupedPresets().get(i).name), "The Tools tab under All chip by chip, each in its order: " + grouped.get(i).name);
        page.tab(ToolsPage.Tab.SAVED);
        check(page.all().size() == firstSaved() && firstSaved() == 6, "Saved: the library's own but the Basic tools (a first launch's six: two of the owner's eight are Basic tools as they are)");
        // A Basic tool saved to the library is still a Basic tool: not in Saved, once in All.
        Brush preset = ToolPresets.of(WATERCOLOR).get(1).brush();
        slots.library.save(preset);
        Brush mine = rolled(PENCIL, 9);
        slots.library.save(mine);
        page = open(slots, 0);
        page.layout(1000, 16);
        check(page.tab() == ToolsPage.Tab.SAVED && page.all().size() == firstSaved() + 1 && page.all().get(0).brush.sameAs(mine), "Saved: newest first, the saved Basic tool not among them");
        for (ToolsPage.Card c : page.all()) check(ToolPresets.find(c.brush) == null, c.name + " in Saved is no Basic tool");
    }
    /** The Basic tools chip by chip (Pens, Markers, ...), in their order within each. */
    static List<ToolPresets.Preset> groupedPresets() {
        List<ToolPresets.Preset> all = new java.util.ArrayList<>();
        for (ToolsPage.Chip c : ToolsPage.Chip.values()) for (ToolPresets.Preset p : ToolPresets.all()) if (c != ToolsPage.Chip.ALL && ToolsPage.Chip.of(p.style) == c) all.add(p);
        return all;
    }
/** The first launch's brushes that aren't Basic tools: six of the owner's eight (2026-10-24); the other two are Basic tools as they are. */
    static int firstSaved() {
        int n = 0;
        for (int i = 0; i < ToolSlots.COUNT; i++) if (ToolPresets.find(ToolSlotsChecks.old(i)) == null) n++;
        return n;
    }
    static void recent() {
        ToolSlots slots = fresh();
        ToolsPage page = open(slots, 0);
        page.tab(ToolsPage.Tab.RECENT);
        page.filter(ToolsPage.Chip.ALL);
        page.layout(1000, 16);
        check(page.all().size() == ToolSlots.COUNT, "Recent before anything is kept: the library, most recently used first");
        Brush wash = ToolPresets.of(WATERCOLOR).get(0).brush(), pencil = ToolPresets.of(PENCIL).get(2).brush(), roll = rolled(WILD, 4);
        slots.use(1, wash); slots.use(2, pencil); slots.use(3, roll); slots.use(4, wash);
        page = open(slots, 0);
        page.layout(1000, 16);
        List<ToolsPage.Card> recent = page.all();
        check(recent.size() == 3 + ToolSlots.COUNT && recent.get(0).brush.sameAs(wash) && recent.get(1).brush.sameAs(roll) && recent.get(2).brush.sameAs(pencil),
                "Recent: what went in a slot, latest first, each once: Basic tools and rolls too; then the library's, as before");
        check(recent.get(0).name.equals(ToolPresets.of(WATERCOLOR).get(0).name) && recent.get(0).line.equals(ToolPresets.of(WATERCOLOR).get(0).line), "a Basic tool with its own description");
        for (int i = 0; i < 60; i++) slots.use(5, rolled(PENCIL, 300 + i));
        check(slots.recent().size() == ToolSlots.RECENT_MOST, "At most " + ToolSlots.RECENT_MOST + " kept");
    }
    static void filters() {
        ToolSlots slots = fresh();
        slots.library.save(rolled(PENCIL, 11));
        slots.library.save(rolled(WILD, 12));
        slots.use(1, ToolPresets.of(PENCIL).get(0).brush());
        ToolsPage page = open(slots, 0);
        page.layout(1000, 16);
        page.filter(ToolsPage.Chip.DRY_MEDIA);
        for (ToolsPage.Tab t : ToolsPage.Tab.values()) {
            // Kits aren't filtered by chip (ToolKitsChecks).
            if (t == ToolsPage.Tab.KITS) continue;
            page.tab(t);
            check(!page.all().isEmpty(), t.label + " has Dry Media brushes");
            for (ToolsPage.Card c : page.all()) check(ToolsPage.Chip.DRY_MEDIA.has(c.brush.parts.style), "The chip filters " + t.label + ": " + c.name);
            check(page.filter() == ToolsPage.Chip.DRY_MEDIA, "The chip stays across tabs");
        }
        page.tab(ToolsPage.Tab.BASIC);
        check(!page.chipsShown().contains(ToolsPage.Chip.ODD_ONES) && page.chipsShown().contains(ToolsPage.Chip.PRINT), "Tools: no Odd Ones chip (no Basic tool is Anything goes)");
        page.tab(ToolsPage.Tab.SAVED);
        List<ToolsPage.Chip> saved = page.chipsShown();
        check(saved.get(0) == ToolsPage.Chip.ALL && saved.contains(ToolsPage.Chip.ODD_ONES) && saved.contains(ToolsPage.Chip.DRY_MEDIA), "Saved: only the chips it has (" + saved + ")");
        ToolsPage.Chip none = null;
        for (ToolsPage.Chip c : ToolsPage.Chip.values()) if (none == null && !saved.contains(c)) none = c;
        check(none != null, "Saved lacks a chip: " + saved);
        page.filter(none);
        check(page.all().isEmpty() && page.cards().isEmpty() && page.pages() == 1 && page.chipsShown().contains(none), "A chip with nothing here: it still shows, chosen, and the grid is empty");
        check(page.empty().equals("No " + none.label + " in Saved"), "and says so: " + page.empty());
        page.filter(ToolsPage.Chip.ALL);
        for (ToolsPage.Tab t : ToolsPage.Tab.values()) {
            if (t == ToolsPage.Tab.KITS) continue; // no chips (ToolKitsChecks)
            page.tab(t);
            List<ToolsPage.Chip> want = new java.util.ArrayList<>();
            want.add(ToolsPage.Chip.ALL);
            for (ToolsPage.Chip chip : ToolsPage.Chip.values()) if (chip != ToolsPage.Chip.ALL) for (ToolsPage.Card c : page.all()) if (chip.has(c.brush.parts.style)) { want.add(chip); break; }
            check(page.chipsShown().equals(want), t.label + "'s chips: All and exactly the ones it has");
        }
    }
    static void paging() {
        ToolSlots slots = fresh();
        ToolsPage page = open(slots, 0);
        page.filter(ToolsPage.Chip.ALL);
        page.layout(30, 16);
        check(page.pages() == 5 && page.cards().size() == 30 && page.page() == 0, "Basic tools, All: " + ToolPresets.all().size() + " in pages of 30, from the first");
        page.turn(1);
        check(page.page() == 1 && page.cards().get(0).name.equals(groupedPresets().get(30).name), "Turning shows the next");
        page.turn(99);
        check(page.page() == 4 && page.cards().size() == ToolPresets.all().size() - 120, "never past the last page");
        page.turn(-99);
        check(page.page() == 0, "nor before the first");
        page.turn(2);
        page.filter(ToolsPage.Chip.MARKERS);
        check(page.page() == 0 && page.cards().size() == Math.min(30, page.all().size()), "A chip: its first page");
        page.filter(ToolsPage.Chip.ALL);
        page.turn(2);
        for (int i = 0; i < 40; i++) slots.library.save(rolled(PENCIL, 100 + i));
        page.tab(ToolsPage.Tab.SAVED);
        check(page.page() == 1, "Another tab: the page holding the slot's tool (Fineliner, after 40 newer)");
        page.tab(ToolsPage.Tab.BASIC);
        check(page.page() == 0, "in Basic tools, Fineliner's: the first page");
    }
    /**
     * The Tools tab under All laid out four across (the owner's mockup, 2026-10-28): each chip's group from a new row, its
     * last row filled out with blanks, and that row a little apart ({@link ToolsPage#opens}); the cards themselves all
     * there, in order. Other tabs and chips: no blanks.
     */
    static void groups() {
        ToolSlots slots = fresh();
        ToolsPage page = open(slots, 0);
        page.filter(ToolsPage.Chip.ALL);
        page.layout(12, 4, 16);
        List<ToolsPage.Card> cells = new java.util.ArrayList<>(), cards = new java.util.ArrayList<>();
        for (int p = 0; p < page.pages(); p++) {
            while (page.page() < p) page.turn(1);
            List<ToolsPage.Card> on = page.cards();
            check(on.size() <= 12 && (p == page.pages() - 1 || on.size() == 12), "Pages of three rows of four");
            for (int i = 0; i < on.size(); i++) {
                ToolsPage.Card c = on.get(i);
                boolean starts = c != null && (cells.isEmpty() ? false : lastChip(cells) != ToolsPage.Chip.of(c.brush.parts.style));
                check(!starts || (cells.size() % 4 == 0 && (i % 4 != 0 || i == 0 || page.opens(on, i))), "A group starts a row: " + (c == null ? "" : c.name));
                check(c != null || cells.size() % 4 != 0, "Blanks only end a row");
                cells.add(c);
                if (c != null) cards.add(c);
            }
        }
        page.turn(-99);
        check(cards.size() == page.all().size() && cells.contains(null), "Every card, in groups with blanks between");
        for (int i = 0; i < cards.size(); i++) check(cards.get(i) == page.all().get(i), "in order");
        page.filter(ToolsPage.Chip.PENS);
        check(!page.cards().contains(null) && !page.opens(page.cards(), 4), "One chip: no blanks, no gaps");
        page.filter(ToolsPage.Chip.ALL);
        page.tab(ToolsPage.Tab.SAVED);
        check(!page.cards().contains(null), "Saved under All keeps its order, newest first: no groups");
    }
    static ToolsPage.Chip lastChip(List<ToolsPage.Card> cells) {
        for (int i = cells.size() - 1; i >= 0; i--) if (cells.get(i) != null) return ToolsPage.Chip.of(cells.get(i).brush.parts.style);
        return null;
    }
    static void changes() {
        ToolSlots slots = fresh();
        String stored = prefs.get("tool_slots");
        ToolsPage page = open(slots, 4);
        page.layout(30, 16);
        check(page.result() == null, "Nothing changed: nothing goes back");
        Brush other = rolled(PENCIL, 21);
        page.put(other);
        check(page.current().sameAs(other) && page.result() != null && page.result().sameAs(other), "A brush put in the slot here goes back on Back");
        check(prefs.get("tool_slots").equals(stored) && !slots.canUndo(), "The page never writes the slot: the drawing screen does, one Undo step");
        ToolsPage.Card card = page.cards().get(2);
        page.put(card.brush);
        check(page.result().sameAs(card.brush) && page.now(card), "A card's brush is the tool now");
        page.put(slots.brush(4));
        check(page.result() == null, "The slot's own brush again: nothing goes back");
    }
    /** The eight slots on the page (the owner, 2026-10-19): "click a slot and then click an option from library". */
    static void six() {
        ToolSlots slots = fresh();
        String stored = prefs.get("tool_slots");
        ToolsPage page = open(slots, 1);
        page.layout(30, 16);
        check(page.slot() == 1 && page.current().sameAs(slots.brush(1)), "Opens on the slot tapped, holding its tool");
        for (Brush b : page.results()) check(b == null, "Nothing changed: nothing goes back for any slot");
        Brush wash = rolled(WATERCOLOR, 4), pencil = rolled(PENCIL, 5);
        page.put(wash);
        page.select(3);
        check(page.slot() == 3 && page.current().sameAs(slots.brush(3)) && page.current(1).sameAs(wash), "Another slot: the page changes it now; the first keeps its pick");
        page.put(pencil);
        Brush[] back = page.results();
        for (int i = 0; i < ToolSlots.COUNT; i++)
            check(i == 1 ? back[i].sameAs(wash) : i == 3 ? back[i].sameAs(pencil) : back[i] == null, "Back takes each slot's pick to it, and only those (slot " + i + ")");
        check(page.look(1).px == slots.swatches.look(ToolSlots.key(1), slots.brush(1)).px, "Each slot's tile at the one size, its pick or not");
        page.select(0);
        page.undo();
        check(page.slot() == 3 && page.current().sameAs(slots.brush(3)), "Undo takes back the latest change in any slot, selecting that slot again");
        page.undo();
        check(page.slot() == 1 && page.current().sameAs(slots.brush(1)) && !page.canUndo(), "and the one before, in its slot");
        check(prefs.get("tool_slots").equals(stored), "The page never writes a slot: the drawing screen does");
    }
    static void tries() {
        ToolSlots slots = fresh();
        ToolsPage page = open(slots, 0);
        page.layout(12, 16);
        Brush opened = page.current();
        check(!page.canUndo(), "Nothing to undo when the page opens");
        Brush wash = ToolPresets.of(WATERCOLOR).get(0).brush(), pencil = ToolPresets.of(PENCIL).get(1).brush();
        page.put(wash);
        page.put(wash);
        page.put(pencil);
        check(page.current().sameAs(pencil) && page.canUndo(), "Two picks: the second is the tool");
        page.undo();
        check(page.current().sameAs(wash), "Undo: the pick before (the same card twice is one pick)");
        Brush saved = rolled(PENCIL, 22);
        page.put(saved);
        page.undo();
        check(page.current().sameAs(wash) && !saved.sameAs(wash), "Undo takes back any brush put in, not only a card's");
        page.undo();
        check(page.current().sameAs(opened) && !page.canUndo() && page.result() == null, "and back to the slot's own tool: nothing goes back");
        page.undo();
        check(page.current().sameAs(opened), "Undo with nothing to undo changes nothing");
        // The scratch pad draws at what the slot will draw the tool at: the one size and grey; the slot itself is written
        // only by the drawing screen.
        ToolSwatches.Look line = slots.swatches.look(ToolSlots.key(0), opened);
        check(page.look().px == line.px, "The one size");
        String slotsBefore = prefs.get("tool_slots");
        page.put(wash);
        String stored = slotsAndSwatches();
        ToolSwatches.Look look = page.look();
        check(slotsAndSwatches().equals(stored) && prefs.get("tool_slots").equals(slotsBefore), "Trying a tool writes no slot");
        // The one grey (2026-10-24): the Watercolor draws at the grey the Fineliner did, the first launch's .216.
        check(look.darkness == line.darkness && line.darkness == .216, "The one grey");
        slots.use(0, page.result());
        ToolSwatches.Look after = slots.swatches.look(ToolSlots.key(0), slots.brush(0));
        check(look.px == after.px && look.px == line.px && look.preset == after.preset && look.darkness == after.darkness,
                "and the one size, the slot's with it (a Watercolor in the Fineliner's slot: " + look.px + " px, as the slot: " + after.px + ")");
    }
    static String slotsAndSwatches() {
        Map<String, String> some = new java.util.TreeMap<>(prefs);
        some.remove(ToolsPage.TAB); some.remove(ToolsPage.STYLE);
        return some.toString();
    }
    static void table() {
        ToolsLayout mp = new ToolsLayout(1024, 1365), ml = new ToolsLayout(1365, 1024), np = new ToolsLayout(749, 998), nl = new ToolsLayout(998, 749);
        ToolsLayout[] all = {mp, ml, np, nl};
        StringBuilder said = new StringBuilder();
        for (ToolsLayout l : all) said.append(l.width).append('x').append(l.height).append(": pad ").append(l.testWidth).append('x').append(l.testHeight)
                .append(", slots ").append(l.slotWidth).append('x').append(l.slotCard).append(", cards ").append(l.cardWidth).append('x').append(l.cardHeight)
                .append(" in ").append(l.columns).append('x').append(l.rows).append(", chips ").append(l.chipRows).append(" row(s); ");
        // The owner's mockup (2026-10-28). Before it: scratch pads 1000 x 297 / 635 x 270, 729 x 163 / 504 x 123; cards a page 12 / 32 / 5 / 15.
        check(mp.testWidth == TABLE[0] && mp.testHeight == TABLE[1] && ml.testWidth == TABLE[2] && ml.testHeight == TABLE[3]
                && np.testWidth == TABLE[4] && np.testHeight == TABLE[5] && nl.testWidth == TABLE[6] && nl.testHeight == TABLE[7], "Scratch pads: " + said);
        check(mp.perPage() == TABLE[8] && ml.perPage() == TABLE[9] && np.perPage() == TABLE[10] && nl.perPage() == TABLE[11], "Cards a page: " + said);
        check(mp.chipRows == 1, "The chips one row on the Manta upright, as the mockup");
    }
    /** The scratch pads (Manta portrait, landscape, Nomad portrait, landscape, width and height), then the cards a page, as laid out. */
    static final int[] TABLE = {976, 237, 647, 722, 725, 191, 528, 477, 24, 36, 16, 18};
    static void lines() {
        ToolSlots slots = fresh();
        ToolsPage page = open(slots, 0);
        page.tab(ToolsPage.Tab.SAVED);
        page.filter(ToolsPage.Chip.ALL);
        float room = LabText.width("x", LabText.CAPTION_SP, 0) * 16;
        page.layout(30, room);
        for (ToolsPage.Card c : page.cards()) {
            check(LabText.width(c.line, LabText.CAPTION_SP, 0) <= room && c.line.startsWith(c.brush.parts.style.label), c.name + ": one line of its caption, style first (" + c.line + ")");
            check(c.name.equals(c.brush.name()), "named as the brush");
            check(ToolsPage.name(c.brush).equals(c.name), "A slot's name alone is its card's: " + c.name);
        }
        for (ToolPresets.Preset p : ToolPresets.all()) check(ToolsPage.name(p.brush()).equals(ToolsPage.card(p.brush(), room).name), "A Basic tool's slot name is its card's: " + p.name);
        check(BrushCaptions.oneLine(java.util.Arrays.asList("Watercolor", "wash", "wet", "cold press"), String::length, 16).equals("Watercolor, wash"), "Items while they fit");
        check(BrushCaptions.oneLine(java.util.Arrays.asList("Charcoal and more", "x"), String::length, 10).equals("Charcoal and more"), "the first always");
    }
    static void layout() {
        for (int[] s : DrawLayoutChecks.SCREENS) {
            ToolsLayout t = new ToolsLayout(s[0], s[1]);
            String at = s[0] + "x" + s[1];
            int main = s[1] - ToolsLayout.BAR;
            check(t.wide == (s[0] > s[1]) && t.spare >= 0 && t.rows >= 2, "The page fits " + at + " (cards " + t.cardWidth + "x" + t.cardHeight + ", rows " + t.rows + ")");
            // Tools First's chip in the Toolbar label's row, beside the label, no taller than the row.
            check(t.switchFoot() + ToolsLayout.CHIP <= t.label && LabText.width(ToolsLayout.TOOLBAR, TypeStyle.CAPTION.sp, 0) + t.gap + t.switchWidth() <= t.top,
                    "Tools First fits the Toolbar label's row at " + at + " (" + t.switchWidth() + " wide, foot " + t.switchFoot() + " of " + t.label + ")");
            int left = t.gap + t.testHeight + t.label + t.slots;
            int library = t.libraryFixed() + t.grid(t.rows) + t.spare;
            if (t.wide) {
                check(left + t.side == main && library == main, "Each column adds up to the height at " + at);
                check(2 * t.side + t.top + t.apart + t.sections == s[0], "The two columns and the paper add up to the width at " + at);
            } else {
                check(left + t.apart + library == main && t.top == s[0] - 2 * t.side && t.sections == t.top, "Everything stacked adds up at " + at);
                check(t.testHeight >= t.testLeast() && t.testHeight < t.testLeast() + t.cardHeight + ToolsLayout.rowGaps(), "The scratch pad at least its least, the rows the rest, at " + at + ": " + t.testHeight);
            }
            check(t.testWidth == t.top && t.testWidth >= 330, "The scratch pad " + t.testWidth + " wide at " + at);
            // The Library: four across at most (the mockup), three at least, a card at least CARD_LEAST wide.
            check(t.columns >= 3 && t.columns <= ToolsLayout.COLUMNS && t.cardWidth >= ToolsLayout.CARD_LEAST && t.columns * t.cardWidth <= t.sections, "The cards across at " + at);
            check(t.slots == 2 * t.slotCard + t.gap && t.slotCount == (t.compact ? 8 : ToolSlots.COUNT) && t.slotColumns * 2 == t.slotCount
                    && t.slotColumns * t.slotWidth + (t.slotColumns - 1) * t.gap <= t.top, "The side column's slots, two rows of five (four on the Nomad), at " + at);
            check(t.slotCard == t.slotArtHeight() + ToolsLayout.NAME_GAP + ToolsLayout.nameLine() + DrawLayout.MARK && t.cardHeight == t.cardArtHeight() + ToolsLayout.NAME_GAP + ToolsLayout.nameLine() + DrawLayout.MARK,
                    "A slot and a card: art, a line for the name, the underline's room at " + at);
            check(t.slotArt() < t.slotWidth && t.cardArt() + ToolsLayout.NAME_END <= t.cardWidth && t.slotArtHeight() >= 30 && t.cardArtHeight() >= 30, "The art inside its card at " + at);
            // The tabs and the pages share a row; the chips wrap within the Library's width, each whole.
            check(t.tabsWidth() + t.tabGap() + ToolsLayout.pagerWidth() <= t.sections, "The tabs and the pages across at " + at + ": " + (t.tabsWidth() + ToolsLayout.pagerWidth()) + " of " + t.sections);
            for (String label : ToolsLayout.chipLabels()) check(ToolsLayout.chipWidth(label, t.chipPad()) <= t.sections, label + "'s chip fits at " + at);
            check(t.chipRows >= 1 && t.chipRows <= 3, "The chips in " + t.chipRows + " row(s) at " + at);
            check(LabText.width(ToolsLayout.TITLE, TypeStyle.HEADING.sp, 0) <= t.titleRoom(), "The title fits beside the bar's buttons at " + at + ": " + t.titleRoom());
            // The scratch pad's top: a change's note in what is left (a hundred dp at least), then Back to, Load Kit or Cut
            // Stamp (never both: a kit's brush is never a Stamp), Clear and the drop.
            float words = 100 + t.backToWidth() + Math.max(quiet(ToolKits.LOAD), quiet(StampNib.CUT)) + quiet("Clear") + t.wet();
            check(words <= t.padRoom(), "The scratch pad's corner words fit at " + at + ": " + words + " of " + t.padRoom());
            check(t.backToWidth() >= quiet(ToolsLayout.BACK_TO + "Pencil"), "Back to names a short brush whole at " + at);
        }
        check(new ToolsLayout(1024, 1365).columns == 4 && new ToolsLayout(749, 998).columns == 4 && new ToolsLayout(1365, 1024).columns == 4 && new ToolsLayout(998, 749).columns == 3,
                "4 columns, 3 on the Nomad in landscape beside the scratch pad");
        // Every drawing cut at its handle end and scaled to the box's width: they all end one length, whatever their canvas.
        for (String[] tool : DrawLayout.PICKER) {
            if (tool[1] == null) continue;
            float[] size = drawingSize(tool[1]), art = ToolsLayout.art(size[0], size[1], 160, 67);
            check(Math.abs(art[0] + art[2] - 160) < 1e-3 && Math.abs(-art[0] - DrawLayout.TIP_TRIM * art[2]) < 1e-3 && Math.abs(2 * art[1] + art[3] - 67) < 1e-3, tool[0] + ": trimmed, the box's width, centred");
        }
        float[] wide = ToolsLayout.art(352, 116, 160, 67), narrow = ToolsLayout.art(240, 116, 160, 67);
        check(Math.abs(wide[0] + wide[2] - (narrow[0] + narrow[2])) < 1e-3 && wide[3] < narrow[3], "A wider canvas ends at the same length, drawn smaller");
    }
    /**
     * The search (the owner, 2026-10-28: "it'd be good to have a search function for the library"): every word in the
     * name, the style, the chip or the line, any case, any order; Tools, Recent and Saved at once, each brush once.
     */
    static void search() {
        ToolPresets.Preset pencil = null;
        for (ToolPresets.Preset p : ToolPresets.all()) if (pencil == null && p.brush().parts.style == PENCIL) pencil = p;
        check(pencil != null, "A Basic pencil");
        ToolsPage.Card card = new ToolsPage.Card(pencil.brush(), pencil.name, pencil.line);
        String[] lineWords = pencil.line.split("[\\s,·]+");
        String word = lineWords[lineWords.length - 1];
        for (String q : new String[]{pencil.name, pencil.name.toUpperCase(), "pencil", "PENCIL", "dry media", "Media DRY", "dry " + pencil.name.toLowerCase(), word, " " + word + "  pencil ", ""})
            check(ToolsPage.matches(card, q), "\"" + q + "\" finds " + pencil.name + " (" + pencil.line + ")");
        for (String q : new String[]{"fineliner", "pencil fineliner", "washes", "zzz"}) check(!ToolsPage.matches(card, q), "\"" + q + "\" doesn't find " + pencil.name);
        ToolsPage.Card wash = new ToolsPage.Card(rolled(WATERCOLOR, 2), "Pond", "x");
        check(ToolsPage.matches(wash, "watercolour pond") && ToolsPage.matches(wash, "Watercolor") && ToolsPage.matches(wash, "washes"), "The style's name and its label, the chip's");

        ToolSlots slots = fresh();
        Brush mine = rolled(PENCIL, 77);
        slots.library.save(mine);
        ToolsPage page = open(slots, 0);
        page.tab(ToolsPage.Tab.KITS);
        page.layout(8, 4, 140);
        int kits = page.all().size();
        check(!page.searching() && page.search().isEmpty() && kits > 0, "No search at first");
        page.search("  " + mine.name().toLowerCase() + " ");
        check(page.searching() && page.tab() == ToolsPage.Tab.KITS && page.page() == 0, "Searching from the first page, the tab kept");
        boolean found = false;
        for (ToolsPage.Card c : page.all()) found |= c.brush.sameAs(mine);
        check(found, "A saved brush found from the Kits tab");
        page.search("pencil");
        List<ToolsPage.Card> finds = page.all();
        boolean basic = false;
        for (ToolsPage.Card c : finds) { check(c.brush.parts.style == PENCIL || ToolsPage.matches(c, "pencil"), c.name + " matches"); basic |= ToolPresets.find(c.brush) != null; }
        check(basic && finds.size() >= 2, "Tools and Saved together: " + finds.size());
        for (int i = 0; i < finds.size(); i++) for (int k = i + 1; k < finds.size(); k++) check(!finds.get(i).brush.sameAs(finds.get(k).brush), "Each brush once");
        page.turn(1);
        page.search("pencil b");
        check(page.page() == 0, "Back to the first page as the words change");
        page.search("zzzz");
        check(page.all().isEmpty() && page.empty().equals("Nothing found"), "Nothing found");
        page.search("");
        check(!page.searching() && page.all().size() == kits && page.all().get(0).kit != null, "An empty search: the open tab again");
    }
    /** The owner's drawing {@code name}'s size, px: a vector's (res/drawable) or a PNG's (res/drawable-nodpi). */
    static float[] drawingSize(String name) {
        try {
            java.io.File vector = new java.io.File("app/src/main/res/drawable/" + name + ".xml");
            if (vector.isFile()) {
                String xml = new String(java.nio.file.Files.readAllBytes(vector.toPath()));
                java.util.regex.Matcher w = java.util.regex.Pattern.compile("android:width=\"(\\d+)px").matcher(xml), h = java.util.regex.Pattern.compile("android:height=\"(\\d+)px").matcher(xml);
                check(w.find() && h.find(), name + ": its size in px");
                return new float[]{Float.parseFloat(w.group(1)), Float.parseFloat(h.group(1))};
            }
            java.awt.image.BufferedImage png = javax.imageio.ImageIO.read(new java.io.File("app/src/main/res/drawable-nodpi/" + name + ".png"));
            return new float[]{png.getWidth(), png.getHeight()};
        } catch (java.io.IOException e) { throw new AssertionError(name + ": " + e); }
    }
    /** A word in the scratch pad's corner: 13 sp and its 8 dp either side ({@link LabButton#quiet}). */
    static float quiet(String words) { return LabText.width(words, TypeStyle.CAPTION.sp, 0) + 16; }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    /** Back to where it started (docs/archive/design-pass-2026-10-23.md, "Getting back to where you started"). */
    static void backToStart() {
        ToolSlots slots = fresh();
        ToolsPage page = open(slots, 4);
        page.layout(30, 16);
        Brush watercolour = slots.brush(4);
        check(!page.restore() && !page.changed(4), "Nothing changed: no dot, nothing to go back to");
        page.put(ToolPresets.of(PENCIL).get(0).brush());
        slots.swatches.chooseSize(ToolSlots.key(4), 0);
        page.select(2);
        page.put(rolled(PENCIL, 7));
        page.select(4);
        check(page.changed(4) && page.changed(2) && !page.changed(1) && page.started().sameAs(watercolour), "Changed slots are marked; Back to names the slot's first brush");
        check(page.restore() && page.current().sameAs(watercolour) && !page.changed(4) && page.look().px == slots.swatches.size(0) && page.result() == null,
                "Back to: the slot's first brush again, at the one size as set, so nothing goes back for it");
        page.undo();
        check(page.current().sameAs(ToolPresets.of(PENCIL).get(0).brush()) && page.changed(4), "Undo takes Back to back");
        // Tools page → Brush Lab → Tools page: the start is the one from before either.
        String[] start = page.start.write();
        slots.use(4, page.current());
        ToolsPage again = new ToolsPage(slots, store(), 4, start);
        check(again.changed(4) && again.started().sameAs(watercolour) && again.result() == null, "The start carried through Brush Lab: Back to the brush from before either");
        check(again.restore() && again.result().sameAs(watercolour), "and Back to puts it back for the drawing screen to take");
    }
    /** Nothing unsaved is lost when a slot is replaced: kept in Recent (docs/archive/design-pass-2026-10-23.md). */
    static void keeps() {
        ToolSlots slots = fresh();
        Brush mine = rolled(PENCIL, 31);
        slots.use(5, mine);
        // Taken out of Recent, as an unsaved brush made in Brush Lab is until the slot lets it go.
        prefs.remove(ToolSlots.RECENT);
        ToolsPage page = open(slots, 5);
        page.layout(30, 16);
        page.put(ToolPresets.of(PENCIL).get(0).brush());
        check(("Kept " + mine.name() + " in Recent").equals(page.note()) && page.note() == null, "A pick lets an unsaved brush go: kept in Recent, said once");
        check(slots.recent().get(0).sameAs(mine), "first in Recent");
        page.put(ToolPresets.of(PENCIL).get(1).brush());
        check(page.note() == null, "A Basic tool let go isn't kept: it's never lost");
        page.put(mine);
        page.put(ToolPresets.of(PENCIL).get(0).brush());
        check(page.note() == null, "A brush in Recent already isn't kept twice");
        page.select(1);
        page.put(rolled(WATERCOLOR, 2));
        check(page.note() == null, "A saved brush (a first launch's Brush pen) isn't kept: it's in the library");
        for (int i = 0; i < 40; i++) { slots.keep(rolled(PENCIL, 500 + i)); }
        check(slots.recent().size() == ToolSlots.RECENT_MOST && ToolSlots.RECENT_MOST == 30, "Recent keeps the latest 30, the oldest dropped");
        Set<String> seen = new HashSet<>();
        for (Brush b : slots.recent()) check(seen.add(b.identity()), "Recent holds each brush once");
    }
    /** A saved card's long press deletes it (Brush Lab's library went, 2026-10-23: the Tools page keeps its delete). */
    static void deletes() {
        ToolSlots slots = fresh();
        Brush mine = rolled(PENCIL, 41);
        slots.library.save(mine);
        ToolsPage page = open(slots, 0);
        page.tab(ToolsPage.Tab.SAVED);
        page.filter(ToolsPage.Chip.ALL);
        page.layout(30, 16);
        ToolsPage.Card card = page.cards().get(0);
        check(card.brush.sameAs(mine) && page.deletable(card), "A saved brush's card can be deleted");
        check(page.delete(card) && !slots.library.contains(mine) && !page.cards().get(0).brush.sameAs(mine), "deleted, the grid without it");
        page.tab(ToolsPage.Tab.BASIC);
        check(!page.deletable(page.cards().get(0)) && !page.delete(page.cards().get(0)), "A Basic tool can't be");
    }
}
