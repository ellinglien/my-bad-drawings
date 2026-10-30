package io.github.mpdairy.monopaint;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * The Tools page's state (spec docs/archive/superpowers/specs/2026-10-15-tools-page-design.md): the ten slots (the owner,
 * 2026-10-19: "a big modal with the slots"), one of them selected ({@link #select}; tap a slot, then a card: "click a
 * slot and then click an option from library"), each slot's tool as it will go back (a card changes the selected one
 * here, not yet in the slot; the test area draws with it, {@link #look}), what Undo takes back (any slot's change,
 * selecting that slot again, as Brush Lab's),
 * what each slot held when the page opened ({@link #start}: Back to it, {@link #restore}), the unsaved brush a change
 * lets go kept in Recent ({@link ToolSlots#keep}, its note {@link #note}), and one grid of cards, paged: the open {@link Tab}
 * (Tools, Recent, Saved or Kits) through the {@link Chip} chosen (the owner, 2026-10-28: "instead of dropdown show chips
 * for the categories"; a Style button's menu of the fifteen styles from 2026-10-24); under All the Tools tab's cards go
 * chip by chip, each group from a new row ({@link #layout(int, int, float)}). The tab and the chip are kept between
 * visits; the grid opens on the page holding the selected slot's tool. A search ({@link #search}) looks through Tools,
 * Recent and Saved at once. What the drawing screen gets on Back is {@link #results}. Pure, checked on the desktop.
 */
final class ToolsPage {
    /**
     * The grid's sources: the built-in tools ({@link ToolPresets}); the brushes put in slots lately ({@link
     * ToolSlots#recent}); the library's brushes that aren't Basic tools; the tool kits
     * ({@link ToolKits}), a card each, which the chips don't filter.
     */
    enum Tab {
        BASIC("Tools"), RECENT("Recent"), SAVED("Saved"), KITS(ToolKits.TAB);
        final String label;
        Tab(String label) { this.label = label; }
    }
    /**
     * The library's chips (the owner's mockup, 2026-10-28): All, then kinds of tool, each a few styles ({@link
     * ModularStyle#name}s; Odd Ones the wild ones). Every style is in one chip (checked).
     */
    enum Chip {
        ALL("All"), PENS("Pens", "Fineliner", "Dip pen"), MARKERS("Markers", "Marker"), BRUSHES("Brushes", "Brush pen", "Dry brush", "Hake"),
        WASHES("Washes", "Watercolor", "Ink wash"), PAINT("Paint", "Gouache", "Oils"), DRY_MEDIA("Dry Media", "Pencil", "Charcoal", "Pastels"),
        PRINT("Print", "Print"), ODD_ONES("Odd Ones");
        final String label;
        private final List<String> styles;
        Chip(String label, String... styles) { this.label = label; this.styles = java.util.Arrays.asList(styles); }
        /** Whether a brush of {@code style} shows under it. */
        boolean has(ModularStyle style) { return this == ALL || (this == ODD_ONES ? style.wild : styles.contains(style.name)); }
        /** The chip {@code style} is in (Odd Ones for one no chip names). */
        static Chip of(ModularStyle style) {
            for (Chip c : values()) if (c != ALL && c != ODD_ONES && c.has(style)) return c;
            return ODD_ONES;
        }
        /** A kept chip: its name, or (kept before the chips, 2026-10-28) a style's name, its chip; All for nothing known. */
        static Chip kept(String stored) {
            for (Chip c : values()) if (c.name().equals(stored)) return c;
            ModularStyle style = stored == null ? null : ModularStyle.named(stored);
            return style == null ? ALL : of(style);
        }
    }

    /** A card: the brush it puts in the slot, its name and its one-line description; or, under Kits, a kit (its first brush shown). */
    static final class Card {
        final Brush brush;
        final String name, line;
        /** The kit the card stands for (the Kits tab), else null. */
        final ToolKits.Kit kit;
        Card(Brush brush, String name, String line) { this(brush, name, line, null); }
        Card(Brush brush, String name, String line, ToolKits.Kit kit) { this.brush = brush; this.name = name; this.line = line; this.kit = kit; }
    }

    /** Where the open tab and the chip are kept (the chip's name, empty for All; a style's name before 2026-10-28). */
    static final String TAB = "tools_tab", STYLE = "tools_style";

    final ToolSlots slots;
    /** The slot selected: the one a card changes. */
    private int slot;
    private final BrushLibrary.Store store;
    /** Each slot's tool when the page opened, and as it is now. */
    private final Brush[] opened = new Brush[ToolSlots.COUNT], current = new Brush[ToolSlots.COUNT];
    /** What each slot held at the start: when the page opened, or when the Tools page before Brush Lab did. */
    final ToolSlots.Start start;
    /**
     * A change on the page: the slot selected before it, the slots it changed and their tools before it; a kit's also the
     * bar's count and the eraser's kind before it ({@link #loadKit}).
     */
    private static final class Step {
        final int slot;
        final int[] slots;
        final Brush[] before;
        final boolean kit;
        final int bar, eraser;
        Step(int slot, int[] slots, Brush[] before, boolean kit, int bar, int eraser) {
            this.slot = slot; this.slots = slots; this.before = before; this.kit = kit; this.bar = bar; this.eraser = eraser;
        }
    }
    /** What the latest change says once (the brush it kept in Recent), or null. */
    private String note;
    /** The changes on the page, latest last, for Undo. */
    private final Deque<Step> before = new ArrayDeque<>();
    private Tab tab;
    /** The chip chosen. */
    private Chip filter;
    /**
     * How many slots the bar shows ({@link NotebookOptions#slotsShown}), whether the notebook can't change that (locked),
     * as the page opened; the count a kit made it (the same: unchanged), and the eraser's kind a kit set (-1: untouched).
     */
    private int barOpened = ToolSlots.COUNT, bar = ToolSlots.COUNT, eraser = -1;
    /** The grid's page, how many cells a page holds, and how many across (a group starts a row). */
    private int page, perPage = 1, columns = 1;
    /** How wide a card's description may be, dp ({@link ToolsLayout#lineRoom}), at 13 sp. */
    private float lineRoom = LabText.width("x", LabText.CAPTION_SP, 0) * BrushCaptions.LINE;
    /** The open tab's cards, read once a tab (the library and the recent brushes don't change while the page is open). */
    private List<Card> cached;
    /** The grid's cells under the chip, a group's last row filled out with nulls ({@link #laid}); null when the tab, the chip or the cards changed. */
    private List<Card> laidOut;
    /** What the search looks for, trimmed; empty for none (the open tab under its chip). Not kept. */
    private String search = "";

    /**
     * Opens on slot {@code slot}, and on the tab and chip last chosen ({@code store}); the first time, on the Tools tab
     * and that slot brush's chip (All for Anything goes).
     */
    ToolsPage(ToolSlots slots, BrushLibrary.Store store, int slot) { this(slots, store, slot, null); }
    /** As above, the slots' start {@code start} ({@link ToolSlots.Start#write}'s lines; null: as they are now). */
    ToolsPage(ToolSlots slots, BrushLibrary.Store store, int slot, String[] start) {
        this.slots = slots; this.store = store; this.slot = slot;
        this.start = ToolSlots.Start.read(start, slots);
        for (int i = 0; i < ToolSlots.COUNT; i++) { opened[i] = slots.brush(i); current[i] = opened[i]; }
        tab = Tab.BASIC;
        for (Tab t : Tab.values()) if (t.name().equals(store.get(TAB))) tab = t;
        String style = store.get(STYLE);
        if (style == null) filter = opened[slot].parts.style.wild ? Chip.ALL : Chip.of(opened[slot].parts.style);
        else filter = Chip.kept(style);
    }
    /** The bar shows {@code shown} slots (1 to 8), which a kit may raise unless {@code locked} (a locked notebook's rules). */
    void bar(int shown) { barOpened = bar = Math.max(1, Math.min(ToolSlots.COUNT, shown)); }
    /** How many slots the bar shows now. */
    int bar() { return bar; }
    /** The slots the bar shows now, if a kit changed it; else -1. */
    int barResult() { return bar == barOpened ? -1 : bar; }
    /** The eraser's kind a kit set ({@link EraseKind#LABELS} index), else -1. */
    int eraser() { return eraser; }
    /** Whether a kit's load stands among the page's changes: the drawing screen takes them all back with one Undo. */
    boolean together() {
        for (Step s : before) if (s.kit) return true;
        return false;
    }

    /** The slot selected. */
    int slot() { return slot; }
    /** The page now changes slot {@code i}; the grid turns to the page holding its tool. Not a change Undo takes back. */
    void select(int i) {
        if (i < 0 || i >= ToolSlots.COUNT || i == slot) return;
        slot = i;
        show(current[i]);
    }
    /** The selected slot's tool as it stands on the page. */
    Brush current() { return current[slot]; }
    /** Slot {@code i}'s tool as it stands on the page. */
    Brush current(int i) { return current[i]; }
    /** What the drawing screen puts in the selected slot: its tool as it stands, or null when it is the one the slot has. */
    Brush result() { return result(slot); }
    private Brush result(int i) { return current[i].sameAs(opened[i]) ? null : current[i]; }
    /** What the drawing screen puts in each slot: its tool as it stands, or null when it is the one the slot has. */
    Brush[] results() {
        Brush[] results = new Brush[ToolSlots.COUNT];
        for (int i = 0; i < results.length; i++) results[i] = result(i);
        return results;
    }
    /** What the test area draws the selected slot's tool at: the slot's own size and grey, as Brush Lab's does. */
    ToolSwatches.Look look() { return look(slot); }
    /** As {@link #look()}, for slot {@code i} (its tile among the ten). */
    ToolSwatches.Look look(int i) { return slots.swatches.look(ToolSlots.key(i), current[i]); }

    /** Lays the grid out: {@code perPage} cards a page, one across, descriptions up to {@code lineRoom} dp wide. */
    void layout(int perPage, float lineRoom) { layout(perPage, 1, lineRoom); }
    /** As above, {@code columns} across ({@code perPage} whole rows of them); opens on the page holding the slot's tool. */
    void layout(int perPage, int columns, float lineRoom) {
        this.columns = Math.max(1, columns);
        this.perPage = Math.max(1, perPage / this.columns) * this.columns;
        this.lineRoom = lineRoom;
        cached = null; laidOut = null;
        show(current[slot]);
    }

    // Tabs and chips

    Tab tab() { return tab; }
    /** Opens {@code t} from its first page (the page holding the tool, when it is there), and keeps it. */
    void tab(Tab t) {
        if (t == tab) return;
        tab = t;
        store.put(TAB, t.name());
        cached = null; laidOut = null;
        page = 0;
        show(current[slot]);
    }
    /** The chips the open tab shows: All, the chips it has brushes under, and the chosen one even when it has none. */
    List<Chip> chipsShown() {
        // Kits are whole ways of working, not a kind of tool: no chips.
        if (tab == Tab.KITS) return new ArrayList<>();
        List<Card> all = unfiltered();
        List<Chip> shown = new ArrayList<>();
        for (Chip chip : Chip.values()) {
            boolean any = chip == Chip.ALL || chip == filter;
            for (int i = 0; i < all.size() && !any; i++) any = chip.has(all.get(i).brush.parts.style);
            if (any) shown.add(chip);
        }
        return shown;
    }
    Chip filter() { return filter; }
    /** The open tab's brushes under {@code chip}, from the first page; kept. */
    void filter(Chip chip) {
        if (chip == filter) return;
        filter = chip;
        store.put(STYLE, chip == Chip.ALL ? "" : chip.name());
        laidOut = null;
        page = 0;
    }

    // The search (the owner, 2026-10-28: "it's difficult to find things in the tools library")

    String search() { return search; }
    /** Whether a search is on: the grid shows what it finds, not the open tab. */
    boolean searching() { return !search.isEmpty(); }
    /**
     * Searches Tools, Recent and Saved together for {@code words} ({@link #matches}), from the first page; empty (or
     * null) shows the open tab again, on the page holding the tool.
     */
    void search(String words) {
        String now = words == null ? "" : words.trim();
        if (now.equals(search)) return;
        boolean was = searching();
        search = now;
        // The cards come from every tab while searching: read again only when a search starts or ends.
        if (was != searching()) cached = null;
        laidOut = null;
        page = 0;
        if (!searching()) show(current[slot]);
    }
    /** Whether every word of {@code words} is in {@code card}'s name, its style's, its chip's or its line, any case, any order. */
    static boolean matches(Card card, String words) {
        ModularStyle style = card.brush.parts.style;
        String in = (card.name + " " + style.name + " " + style.label + " " + Chip.of(style).label + " " + card.line).toLowerCase(java.util.Locale.ROOT);
        for (String word : words.trim().toLowerCase(java.util.Locale.ROOT).split("\\s+")) if (!in.contains(word)) return false;
        return true;
    }

    // The grid

    int page() { return page; }
    int pages() { return LabPages.pages(laid().size(), perPage); }
    /** Turns the grid by {@code delta} pages, kept inside the pages there are. */
    void turn(int delta) { page = LabPages.clamp(page + delta, laid().size(), perPage); }
    /** The grid's page's cells, row by row: a card, or null where a group's last row ends early. */
    List<Card> cards() {
        List<Card> all = laid();
        page = LabPages.clamp(page, all.size(), perPage);
        return new ArrayList<>(all.subList(page * perPage, Math.min(all.size(), page * perPage + perPage)));
    }
    /**
     * Every card of the open tab under the chip; under All the Tools tab's chip by chip (Recent and Saved keep their
     * order, latest first).
     */
    List<Card> all() {
        if (tab == Tab.KITS && !searching()) return new ArrayList<>(unfiltered());
        List<Card> cards = new ArrayList<>();
        for (Card c : unfiltered()) if (searching() ? matches(c, search) : filter.has(c.brush.parts.style)) cards.add(c);
        if (grouped()) cards.sort(java.util.Comparator.comparingInt(c -> Chip.of(c.brush.parts.style).ordinal()));
        return cards;
    }
    /** Whether the grid goes in groups, chip by chip: the Tools tab under All. */
    private boolean grouped() { return tab == Tab.BASIC && filter == Chip.ALL && !searching(); }
    /** {@link #all}'s cards as the grid lays them: each group's last row filled out with nulls, so the next starts a row. */
    private List<Card> laid() {
        if (laidOut != null) return laidOut;
        List<Card> cells = new ArrayList<>();
        Chip group = null;
        for (Card c : all()) {
            Chip chip = Chip.of(c.brush.parts.style);
            if (grouped() && group != null && chip != group) while (cells.size() % columns != 0) cells.add(null);
            group = chip;
            cells.add(c);
        }
        return laidOut = cells;
    }
    /** Whether cell {@code i} of a page's {@code cells} ({@link #cards}) starts a group after another: its row a little apart. */
    boolean opens(List<Card> cells, int i) {
        if (!grouped() || i <= 0 || i >= cells.size() || cells.get(i) == null) return false;
        Card before = null;
        for (int k = i - 1; k >= 0 && before == null; k--) before = cells.get(k);
        return before != null && Chip.of(before.brush.parts.style) != Chip.of(cells.get(i).brush.parts.style);
    }
    /**
     * The open tab's cards: the Basic tools, style by style; Recent, latest first; Saved, the library's brushes that
     * aren't Basic tools, newest first; the kits. Searching: Tools, Recent and Saved, each brush once.
     */
    private List<Card> unfiltered() {
        if (cached != null) return cached;
        List<Card> cards = cached = new ArrayList<>();
        if (searching()) {
            List<Card> every = new ArrayList<>();
            for (ToolPresets.Preset p : ToolPresets.all()) every.add(card(p));
            for (Brush b : slots.recent()) every.add(card(b, lineRoom));
            every.addAll(saved());
            for (Card c : every) {
                boolean twice = false;
                for (int i = 0; i < cards.size() && !twice; i++) twice = cards.get(i).brush.sameAs(c.brush);
                if (!twice) cards.add(c);
            }
            return cards;
        }
        switch (tab) {
            case BASIC:
                for (ToolPresets.Preset p : ToolPresets.all()) cards.add(card(p));
                break;
            case RECENT:
                for (Brush b : slots.recent()) cards.add(card(b, lineRoom));
                break;
            case SAVED:
                cards.addAll(saved());
                break;
            default:
                for (ToolKits.Kit k : ToolKits.all()) { Brush[] tools = k.tools(); if (tools.length > 0 && tools[0] != null) cards.add(new Card(tools[0], k.name, kitLine(k), k)); }
        }
        return cards;
    }
    /** The library's brushes that aren't Basic tools, newest first. */
    private List<Card> saved() {
        List<Card> cards = new ArrayList<>();
        for (BrushLibrary.Entry e : slots.library.entries()) if (ToolPresets.find(e.brush) == null) cards.add(card(e.brush));
        return cards;
    }
    private static Card card(ToolPresets.Preset p) { return new Card(p.brush(), p.name, p.line); }
    private Card card(Brush brush) { return captioned(brush, lineRoom); }
    /** {@code brush}'s card: a Basic tool's own name and line; any other's name and caption, one line {@code lineRoom} dp wide (the smudge tool's chooser too). */
    static Card card(Brush brush, float lineRoom) {
        ToolPresets.Preset p = ToolPresets.find(brush);
        return p != null ? card(p) : captioned(brush, lineRoom);
    }
    /** {@code brush}'s card's name alone ({@link #card(Brush, float)}'s, without its line): a Basic tool's own, else the brush's. */
    static String name(Brush brush) {
        ToolPresets.Preset p = ToolPresets.find(brush);
        return p != null ? p.name : brush.name();
    }
    private static Card captioned(Brush brush, float lineRoom) {
        return new Card(brush, brush.name(), BrushCaptions.oneLine(ModularWords.caption(brush.parts), LabText.face(LabText.CAPTION_SP, 0), lineRoom));
    }
    /** What the grid says when the open tab has nothing under the chip: "No Dry Media in Saved", "Nothing in Recent yet". */
    String empty() { return searching() ? "Nothing found" : tab == Tab.KITS ? "No kits" : filter == Chip.ALL ? "Nothing in " + tab.label + " yet" : "No " + filter.label + " in " + tab.label; }
    /** Whether {@code card} (a cell: null for none) holds the slot's tool as it stands (it is underlined). */
    boolean now(Card card) { return card != null && card.kit == null && card.brush.sameAs(current[slot]); }
    /** A kit's card words: how many brushes it puts on the bar ("3 brushes"). */
    static String kitLine(ToolKits.Kit k) { return k.size() == 1 ? "1 brush" : k.size() + " brushes"; }
    /** Turns the grid to the page holding {@code brush}, where it is in it. */
    private void show(Brush brush) {
        List<Card> all = laid();
        for (int i = 0; i < all.size(); i++) if (all.get(i) != null && all.get(i).brush.sameAs(brush)) { page = LabPages.of(i, perPage); return; }
    }

    // Changes

    /**
     * A card's brush is the selected slot's tool now (the page stays, to try it); the unsaved brush it replaces is kept in
     * Recent. The tool already: nothing, no Undo step.
     */
    void put(Brush brush) {
        if (brush.sameAs(current[slot])) return;
        step(new int[]{slot}, false);
        replace(slot, brush);
        current[slot] = brush;
    }
    /** An Undo step for {@code which} slots as they are now (a kit's: {@code kit}). */
    private void step(int[] which, boolean kit) {
        Brush[] was = new Brush[which.length];
        for (int i = 0; i < which.length; i++) was[i] = current[which[i]];
        before.addLast(new Step(slot, which, was, kit, bar, eraser));
    }
    /** Slot {@code i} lets its tool go for {@code next}: an unsaved one is kept in Recent, and the note says so. */
    private boolean replace(int i, Brush next) {
        if (current[i].sameAs(next) || !slots.keep(current[i])) return false;
        note = ToolSlots.keptNote(current[i]);
        if (tab == Tab.RECENT || searching()) { cached = null; laidOut = null; }
        return true;
    }
    /** What the latest change said (the brush kept in Recent), once; else null. */
    String note() { String said = note; note = null; return said; }

    // Back to the start

    /** Whether slot {@code i} holds something else than at the start (the dot under it). */
    boolean changed(int i) { return start.changed(i, current[i]); }
    /** The selected slot's tool at the start ("Back to Fineliner"). */
    Brush started() { return start.brushes[slot]; }
    /**
     * Back to the start: the selected slot holds what it did when the page opened (or the Tools page before Brush Lab);
     * an Undo step, the unsaved brush it replaces kept in Recent. Whether it changed anything.
     */
    boolean restore() {
        if (!changed(slot)) return false;
        step(new int[]{slot}, false);
        replace(slot, start.brushes[slot]);
        current[slot] = start.brushes[slot];
        show(current[slot]);
        return true;
    }

    // Deleting a saved brush

    /** Whether {@code card} is a saved brush that can be deleted (a library brush, not a Basic tool or a kit). */
    boolean deletable(Card card) { return card.kit == null && ToolPresets.find(card.brush) == null && slots.library.contains(card.brush); }
    /** Deletes {@code card}'s brush from the library (a long press, confirmed); false when it isn't deletable. */
    boolean delete(Card card) {
        if (!deletable(card)) return false;
        slots.library.delete(slots.library.find(card.brush).id);
        cached = null; laidOut = null;
        return true;
    }
    /** Whether Undo has a change on the page to take back. */
    boolean canUndo() { return !before.isEmpty(); }
    /**
     * Takes back the latest change, in any slot: the tools before it are those slots' tools again, and the slot it
     * changed (a kit's: the one selected before it) is selected; a kit's bar count and eraser kind go back too.
     */
    void undo() {
        Step step = before.pollLast();
        if (step == null) return;
        boolean other = step.slot != slot;
        for (int i = 0; i < step.slots.length; i++) current[step.slots[i]] = step.before[i];
        slot = step.slot;
        if (step.kit) { bar = step.bar; eraser = step.eraser; }
        if (other || step.kit) show(current[slot]);
    }

    // Kits

    /** The kit the selected slot's tool is in (as the Basic tool is), or null: This Brush offers it ({@link ToolKits#of}). */
    ToolKits.Kit kit() { return ToolKits.of(current[slot]); }
    /** Whether loading {@code kit} raises the bar's count (Brushes on Bar) to show it all. */
    boolean raises(ToolKits.Kit kit) { return kit.size() > bar; }
    /**
     * Loads {@code kit}: its brushes fill the bar's first slots in order (the bar shows the first slots whatever its count,
     * so a kit there is always seen, and its numbers read along the bar), the first selected; the bar shows at least
     * that many; its eraser step sets the eraser's kind. One Undo takes it all back.
     * Whether anything changed.
     */
    boolean loadKit(ToolKits.Kit kit) {
        if (kit == null || kit.size() > ToolSlots.COUNT) return false;
        Brush[] tools = kit.tools();
        int newBar = Math.max(bar, kit.size()), newEraser = kit.eraser != null ? kit.eraser.eraser : eraser;
        boolean changes = newBar != bar || newEraser != eraser;
        int[] which = new int[tools.length];
        for (int i = 0; i < tools.length; i++) {
            if (tools[i] == null) return false;
            which[i] = i;
            changes |= !tools[i].sameAs(current[i]);
        }
        if (!changes) { select(0); return false; }
        step(which, true);
        int kept = 0;
        for (int i = 0; i < tools.length; i++) {
            if (replace(i, tools[i]) && ++kept > 1) note = "Kept " + kept + " brushes in Recent";
            current[i] = tools[i];
        }
        bar = newBar; eraser = newEraser;
        slot = 0;
        show(current[0]);
        return true;
    }
}
