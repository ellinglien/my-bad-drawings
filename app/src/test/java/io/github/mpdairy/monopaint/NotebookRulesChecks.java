package io.github.mpdairy.monopaint;

import java.io.*;
import java.nio.file.Files;
import java.util.*;

/**
 * A notebook's rules and lock (docs/archive/superpowers/specs/2026-10-19-notebook-options-design.md, "Rules" and "Lock";
 * {@link NotebookRules}, {@link TimedPages}, {@link PageClock}, {@link NotebookOptions}, {@link BookOptions}):
 * <ul>
 * <li>No Erasing refuses the eraser (a tool, the pen's end), a smudge that lifts (Lift, Clear Water) and not one that moves paint,
 * and leaves every other tool alone (Undo: NoErasingChecks); the eraser tile and the clear commands follow {@code rules.noErasing};</li>
 * <li>Brushes shows the first slots and refuses the rest and the smudge tool; an older One Brush reads as Brushes 1; Each Page rolls a page's own toolbar, the same each time;</li>
 * <li>Timed Pages: the timer starts at the first stroke, pauses while the page or the app isn't showing and counts on from where
 * it was (never the wall clock), blinks in the last 30 s (10 s for a minute), locks at zero, lets a stroke in progress finish,
 * leaves no Undo or Redo, is in the page's record and survives a save and a reopen, and a locked page isn't blank;</li>
 * <li>Lock Settings, cut (2026-10-24): a notebook locked or sealed before opens unlocked, its rules as they were, and
 * saved again keeps no lock;</li>
 * <li>the records: tagged, only when not the default, round-tripped through the file, the shelf and the recovery, an
 * old notebook byte for byte as before; a page without a clock writes as before.</li>
 * </ul>
 */
public final class NotebookRulesChecks {
    static final int W = 300, H = 400;

    public static void main(String[] args) throws Exception {
        Assets.install(Assets.folder(Assets.FOLDER));
        noErasing();
        oneBrush();
        blink();
        timer();
        lockedPage();
        persistence();
        lock();
        records();
        lockCut();
        pageRules();
        pageSeconds();
        System.out.println("PASS: a page's own time (Page Settings' slider): the notebook's Timed Pages stops, used while the page is timed On alone and run by its clock, kept with the page and its stack's bottom, as PTIM in the file and TSRM in the recovery only when a page has one, a notebook without one (PRUL or not) byte for byte as before, a record for another page count or over an hour left out");
        System.out.println("PASS: a page's own rules (Page Settings): No Erasing and Timed Pages each the notebook's, Off or On for that page alone, the eraser refused and the timer run on that page only, kept with the page through a move, a stack's bottom going and a delete put back, as PRUL in the file and TSRR in the recovery only when a page has one, an old notebook byte for byte as before, a record for another page count or with a byte it doesn't write left out");
        System.out.println("PASS: No Erasing refuses the eraser, the pen's eraser end and the smudges that lift (Lift, Clear Water) and nothing else; Brushes keeps to its first slots, an older One Brush read as Brushes 1, Each Page a roll a page; a page's timer starts at its first stroke, pauses off the page and counts on without the wall clock, blinks in the last 30 s (10 s for a minute), locks at zero after a stroke in progress, leaves no Undo or Redo, is kept in the page's record across a save and a reopen, and a locked page isn't blank; the rules as tagged records, only when set, round-tripped, an old notebook and a page without a clock byte for byte as before; Lock Settings cut: a notebook locked or sealed before (its file or its recovery) opens unlocked with its rules, and saved again keeps no lock; Guides in PDF cut: one that left its guides out prints them");
    }

    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    // ---- No Erasing ----

    static Brush lift() {
        ModularBrush b = WetSmudgeChecks.waterBrush().copy();
        b.set(BrushPart.CARRY, new ModularBrush.Part(PartChoice.LIFT));
        return Brush.of(b);
    }
    static Brush smear() { return Brush.of(FloatChecks.preset("Blend stump")); }
    static Brush water() { return Brush.of(WetSmudgeChecks.waterBrush()); }
    static Brush pen() { return ToolSlotsChecks.old(0); }

    static void noErasing() {
        NotebookOptions off = NotebookOptions.DEFAULT, on = off.withNoErasing(true);
        check(!off.noErasing && on.noErasing && off.isDefault() && !on.isDefault() && on.hasRules() && !off.hasRules(), "No Erasing is off by default");
        check(NotebookRules.erases(lift()) && NotebookRules.erases(water()) && !NotebookRules.erases(smear()) && !NotebookRules.erases(pen()), "Lift and Clear Water erase; Smear and a pen don't");
        check(NotebookRules.erases(null), "(the eraser's brush is none)");
        for (int slot = 0; slot < ToolSlots.COUNT; slot++) check(!NotebookRules.refuses(on, slot, ToolSlotsChecks.old(slot)), "A pen, a pencil or a paint isn't refused (slot " + slot + ")");
        check(NotebookRules.refuses(on, ToolSlots.ERASER, null), "The eraser is refused (the bar's, the pen's end and the finger's all start tool " + ToolSlots.ERASER + ")");
        check(!NotebookRules.refuses(off, ToolSlots.ERASER, null) && !NotebookRules.refuses(null, ToolSlots.ERASER, null), "and allowed with it off");
        check(NotebookRules.refuses(on, ToolSlots.SMUDGE, lift()) && NotebookRules.refuses(on, ToolSlots.SMUDGE, water()), "A smudge tool that lifts, Clear Water's lift too, is refused");
        check(!NotebookRules.refuses(on, ToolSlots.SMUDGE, smear()), "one that only moves paint isn't");
        check(NotebookRules.refuses(on, 0, lift()), "and a slot holding a lifting brush is refused as well");
        check(!NotebookRules.refuses(off, ToolSlots.SMUDGE, lift()), "Nothing is refused with the rule off");
        check(NotebookRules.eraserOff(on) && !NotebookRules.eraserOff(off) && NotebookRules.smudgeOff(on, water()) && !NotebookRules.smudgeOff(on, smear()), "The eraser tile (and a lifting smudge tool's) shows disabled");
        // Undo is not erasing: a page undoes.
        ToneDocument page = marked(1);
        check(page.canUndo() && page.undo() && page.unmarked(), "Undo still works on a page");
        check(NotebookRules.ERASING_OFF.equals("Erasing is off") && NotebookRules.NO_ERASING.equals("No Erasing"), "Words in Title Case");
    }

    // ---- Brushes (One Brush before it) ----

    static void oneBrush() {
        ToolSlotsChecks.prefs = new HashMap<>();
        ToolSlots slots = new ToolSlots(ToolSlotsChecks.store(), new Random(3));
        Brush chosen = slots.brush(2);
        String line = slots.toolbar().lines().get(2);
        // An older notebook's One Brush record: its brush fills a Library toolbar of its own, Brushes 1, the same on every page.
        NotebookOptions one = NotebookOptions.DEFAULT.readOneBrush(line.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        check(one.brushes == 1 && one.tools == NotebookOptions.Tools.LIBRARY && one.ownsTools() && one.hasRules() && !one.toolbarFrozen(), "One Brush reads as Brushes 1 on a Library toolbar of its own");
        ToolSlots.Toolbar toolbar = ToolSlots.of(one);
        check(toolbar != null, "and gives the toolbar");
        for (int i = 0; i < ToolSlots.COUNT; i++) check(toolbar.brushes[i].sameAs(chosen) && toolbar.px[i] == toolbar.px[0] && toolbar.darkness[i] == toolbar.darkness[0], "Every slot holds the one brush at its size and grey");
        check(NotebookOptions.DEFAULT.readOneBrush("nope".getBytes(java.nio.charset.StandardCharsets.UTF_8)).equals(NotebookOptions.DEFAULT) && NotebookOptions.DEFAULT.readOneBrush(null).equals(NotebookOptions.DEFAULT), "A line that doesn't read is off");
        // Brushes: All, or 1 to 8 slots shown; the rest, and the smudge tool, refused.
        check(NotebookOptions.DEFAULT.slotsShown() == ToolSlots.COUNT && NotebookOptions.MOST_BRUSHES == ToolSlots.COUNT, "All: every slot");
        for (int n = 1; n <= 8; n++) {
            NotebookOptions o = NotebookOptions.DEFAULT.withBrushes(n);
            check(o.brushes == n && o.slotsShown() == n && o.hasRules() && NotebookOptions.DEFAULT.readBrushes(o.brushesBytes()).equals(o), n + " brushes, kept and read back");
            check(!NotebookRules.refuses(o, n - 1, chosen) && NotebookRules.refuses(o, ToolSlots.SMUDGE, null) && (n == ToolSlots.COUNT || NotebookRules.refuses(o, n, chosen)), "Slot " + n + " and on, and the smudge tool, refused");
        }
        for (int bad : new int[]{0, ToolSlots.COUNT + 1, -1, 100}) check(NotebookOptions.DEFAULT.withBrushes(bad).brushes == 0 && NotebookOptions.DEFAULT.readBrushes(new byte[]{(byte) bad}).brushes == 0, "Out of range is All: " + bad);
        check(!NotebookRules.refuses(NotebookOptions.DEFAULT, ToolSlots.SMUDGE, null) && !NotebookRules.refuses(NotebookOptions.DEFAULT, 7, chosen), "All refuses nothing");
        check(one.withTools(NotebookOptions.Tools.SURPRISE, toolbarLines(2)).brushes == 1, "Brushes stays as the tools change");
        // Each Page: the tools change with the page, so the toolbar is frozen, and its slots read back don't change what is kept.
        NotebookOptions each = NotebookOptions.DEFAULT.withTools(NotebookOptions.Tools.EACH_PAGE, toolbarLines(2));
        check(each.toolbarFrozen() && each.withToolbar(toolbarLines(5)).equals(each) && !NotebookOptions.DEFAULT.toolbarFrozen(), "Each Page is frozen and keeps its first page's roll");
        // The Tools page says why it takes no pick, with one tap to the setting that frees it (the owner, 2026-10-22).
        check(NotebookRules.toolsFixed(NotebookOptions.DEFAULT) == null && NotebookRules.toolsSettings(NotebookOptions.DEFAULT) == null && NotebookRules.toolsFixed(null) == null, "A free toolbar: no note");
        check(NotebookRules.TOOLS_EACH_PAGE.equals(NotebookRules.toolsFixed(each)) && NotebookRules.toolsSettings(each) == CardSheet.Screen.TOOLS, "Each Page: its note, then the Tools screen");
        NotebookOptions libraryOwn = NotebookOptions.DEFAULT.withTools(NotebookOptions.Tools.LIBRARY, toolbarLines(2));
        check(!libraryOwn.toolbarFrozen() && NotebookRules.toolsFixed(libraryOwn) == null, "A notebook's own Library toolbar is free (no lock since 2026-10-24)");
        check(NotebookRules.TOOLS_EACH_PAGE.length() <= 30 && CardSheet.TOOL_NOTES[0].length() <= 30 && CardSheet.TOOL_NOTES[3].length() <= 30, "Notes about 30 characters");
        check(NotebookOptions.DEFAULT.readTools(each.toolsBytes()).equals(each), "Each Page is kept and read back");
        ToolSlots.Toolbar first = slots.forPage(each, 0), third = slots.forPage(each, 2);
        check(first.sameAs(ToolSlots.of(each)) && third.sameAs(slots.forPage(each, 2)) && !third.sameAs(slots.forPage(each, 3)) && !third.sameAs(first), "Each Page: the first page's toolbar, then a roll of a page's own, the same each time");
        check(slots.forPage(one, 3).sameAs(ToolSlots.of(one)), "Other tools are the same on every page");
        // Frozen slots don't change by rolling or picking; entering the notebook's toolbar still loads it.
        Brush before = slots.brush(1);
        slots.frozen = true;
        check(slots.roll(1).sameAs(before) && slots.brush(1).sameAs(before), "A frozen slot isn't rolled");
        check(!slots.use(1, slots.brush(3)) && slots.brush(1).sameAs(before) && !slots.canUndo(), "or picked, and no Undo step is made");
        check(slots.enter(toolbar) && slots.brush(5).sameAs(chosen), "but the notebook's toolbar is loaded into them");
        check(slots.leave(), "and the app's put back");
        slots.frozen = false;
        check(!slots.roll(1).sameAs(before) || slots.brush(1).sameAs(before), "Unfrozen, they roll as before");
        check(NotebookRules.refuses(one, ToolSlots.SMUDGE, smear()) && NotebookRules.smudgeOff(one, smear()), "The smudge tool, a second brush, is off");
        check(NotebookRules.refuses(one, ToolSlots.STAMP, ToolSlots.firstStamp()) && NotebookRules.smudgeOff(one, ToolSlots.firstStamp())
                && !NotebookRules.refuses(NotebookOptions.DEFAULT, ToolSlots.STAMP, ToolSlots.firstStamp()), "So is the stamp tool; All refuses it nothing");
        check(!NotebookRules.refuses(one, 0, chosen) && !NotebookRules.refuses(one, ToolSlots.ERASER, null), "and the one brush and the eraser are not (No Erasing is a rule of its own)");
    }
    static List<String> toolbarLines(long seed) { return NotebookOptionsChecks.toolbarLines(seed); }

    // ---- the blink ----

    static void blink() {
        check(NotebookRules.blinkWindow(180) == 30_000 && NotebookRules.blinkWindow(300) == 30_000 && NotebookRules.blinkWindow(600) == 30_000 && NotebookRules.blinkWindow(60) == 10_000 && NotebookRules.blinkWindow(30) == 10_000, "The last 30 s blink, the last 10 s for a minute or less");
        check(!NotebookRules.blinks(30_001, 180) && NotebookRules.blinks(30_000, 180) && NotebookRules.blinks(1, 180) && !NotebookRules.blinks(0, 180) && !NotebookRules.blinks(5_000, 0), "Blinking from the window's start to zero, never when Off");
        check(!NotebookRules.blinks(10_001, 60) && NotebookRules.blinks(10_000, 60), "(a minute: 10 s)");
        // Slowly: shown a second, hidden a second.
        int changes = 0; boolean was = false;
        for (long left = 30_000; left > 0; left -= 100) { boolean hidden = NotebookRules.numberHidden(left, 180); if (hidden != was) changes++; was = hidden; }
        check(changes >= 28 && changes <= 30, "It changes once a second, " + changes + " times in 30 s");
        check(!NotebookRules.numberHidden(30_000, 180) && !NotebookRules.numberHidden(29_500, 180) && NotebookRules.numberHidden(28_900, 180) && !NotebookRules.numberHidden(27_900, 180), "starting shown");
        check(!NotebookRules.numberHidden(60_000, 180), "and never hidden before the window");
        check(NotebookRules.nextBlink(100_000, 180) == 70_000 && NotebookRules.nextBlink(29_500, 180) == 500 && NotebookRules.nextBlink(30_000, 180) == 1000, "The next look is at the next change, so the panel is updated only then");
        check(NotebookRules.timeLabel(0).equals("Off") && NotebookRules.timeLabel(600).equals("10 min") && NotebookRules.timeLabel(90).equals("90 s") && NotebookRules.timeLabel(30).equals("30 s"), "Labels");
        // The side bar's countdown: m:ss, in tens of seconds over a minute (a change every 10 s), then every second.
        check(NotebookRules.countdown(300_000).equals("5:00") && NotebookRules.countdown(295_000).equals("5:00") && NotebookRules.countdown(290_000).equals("4:50")
                && NotebookRules.countdown(65_000).equals("1:10") && NotebookRules.countdown(60_000).equals("1:00") && NotebookRules.countdown(59_001).equals("1:00")
                && NotebookRules.countdown(9_500).equals("0:10") && NotebookRules.countdown(0).equals("0:00") && NotebookRules.countdown(3_600_000).equals("60:00"), "The countdown");
        check(NotebookRules.nextCountdown(65_000) == 5_000 && NotebookRules.nextCountdown(70_000) == 10_000 && NotebookRules.nextCountdown(59_500) == 500 && NotebookRules.nextCountdown(0) == Long.MAX_VALUE, "changes only when its words do");
        int looks = 0;
        for (long left = 300_000; left > 0; left -= NotebookRules.nextCountdown(left)) looks++;
        check(looks == 24 + 60, "a 5 min page's countdown is drawn 84 times, not 300: " + looks);
    }

    // ---- the timer ----

    static void timer() throws Exception {
        ToneDocument page = new ToneDocument(W, H);
        TimedPages t = new TimedPages();
        t.configure(180, 0);
        t.sync(page, true, 0);
        check(t.tick(1000, false) == TimedPages.State.IDLE && t.remaining(1000) == 180_000 && !page.clock().started && !t.running(), "A page's timer doesn't start until its first stroke");
        check(!page.clock().kept(), "and its record keeps nothing");
        long start = 5_000;
        t.strokeBegins(start);
        check(page.clock().started && t.running() && !page.locked() && page.clock().kept(), "The first stroke starts it");
        check(t.remaining(start + 60_000) == 120_000 && t.tick(start + 60_000, false) == TimedPages.State.RUNNING, "Two minutes left after one");
        t.strokeBegins(start + 70_000);
        check(t.remaining(start + 80_000) == 100_000, "A second stroke doesn't start it again");
        // Blink window.
        check(t.tick(start + 149_000, false) == TimedPages.State.RUNNING && !t.numberHidden(start + 149_000), "31 s left: running");
        check(t.tick(start + 150_500, false) == TimedPages.State.BLINKING && !t.numberHidden(start + 150_500), "29.5 s left: in the blink window, number shown");
        check(t.numberHidden(start + 151_500), "and hidden the next second");
        check(t.nextLook(start + 150_500) == 500 && t.nextLook(start + 20_000) == 10_000 && t.nextLook(start + 25_000) == 5_000, "The next look is when the blink or the countdown changes");
        // Pausing: the page or the app isn't showing.
        t.sync(page, false, start + 100_000);
        check(!t.running() && page.clock().spent == 100_000, "Paused when the page isn't showing: the time so far is the page's");
        check(t.remaining(start + 100_000 + 86_400_000L) == 80_000 && t.tick(start + 86_400_000L, false) == TimedPages.State.IDLE, "and a day away costs nothing");
        t.sync(page, true, start + 500_000_000L);
        check(t.running() && t.remaining(start + 500_000_000L + 30_000) == 50_000, "Counting on from where it was");
        // Another page has its own time; this one keeps its.
        ToneDocument other = new ToneDocument(W, H);
        t.sync(other, true, start + 500_030_000L);
        check(page.clock().spent == 130_000 && !t.running() && other.clock().spent == 0 && t.remaining(start + 500_030_000L) == 180_000, "Another page has its own time, the first keeps what it spent");
        t.sync(page, true, start + 600_000_000L);
        check(t.remaining(start + 600_000_000L) == 50_000, "and turned back to, the first counts on");
        // The time is given to the page before a save.
        t.commit(start + 600_000_000L + 10_000);
        check(page.clock().spent == 140_000 && t.running(), "A commit gives the time so far and counts on");
        long revision = page.revision();
        t.commit(start + 600_000_000L + 10_000);
        check(page.revision() == revision, "(a commit with no time passed changes nothing)");
        // A stroke at zero finishes first.
        long zero = start + 600_000_000L + 10_000 + 40_000;
        check(t.remaining(zero) == 0, "No time left");
        check(t.tick(zero, true) == TimedPages.State.BLINKING && !page.locked() && t.nextLook(zero) == 250, "A stroke in progress at zero finishes first");
        check(t.tick(zero + 300, false) == TimedPages.State.TIME_UP && page.clock().locked && !t.running(), "then Time's up, its clock run out");
        check(!page.locked(), "Lock Pages off (the default): the page stays editable");
        check(page.clock().spent == 180_000, "with all its time spent");
        check(t.tick(zero + 600, false) == TimedPages.State.IDLE, "and it is said once");
        t.strokeBegins(zero + 1000);
        check(!t.running() && t.tick(zero + 1000, false) == TimedPages.State.IDLE, "A page run out never counts again, nor says Time's up again");
        t.sync(page, false, zero + 2000); t.sync(page, true, zero + 3000);
        check(!t.running() && t.remaining(zero + 3000) == 0, "turned back to either");
        page.timerLocks(true);
        check(page.locked(), "Lock Pages on: the page run out is locked");
        page.timerLocks(false);
        check(!page.locked(), "and off again, editable");
        page.timerLocks(true);
        // Timed Pages Off, or a new time, leaves a page's lock.
        t.configure(0, zero + 4000);
        check(page.locked() && t.remaining(zero + 4000) == 0, "Off doesn't unlock a locked page: locked means locked");
        // Off: nothing starts.
        ToneDocument plain = new ToneDocument(W, H);
        TimedPages off = new TimedPages();
        off.sync(plain, true, 0); off.strokeBegins(10);
        check(!plain.clock().kept() && !off.running(), "With Timed Pages off, a stroke starts no timer");
        // A shorter time locks a page that has spent it.
        ToneDocument late = new ToneDocument(W, H);
        TimedPages three = new TimedPages();
        three.configure(600, 0); three.sync(late, true, 0); three.strokeBegins(0); three.pause(200_000);
        three.configure(180, 300_000); three.sync(late, true, 300_000);
        late.timerLocks(true);
        check(three.tick(300_000, false) == TimedPages.State.TIME_UP && late.locked(), "Three minutes where a page has spent more runs out at the next look (locked under Lock Pages)");
        // A one-minute page.
        ToneDocument minute = new ToneDocument(W, H);
        TimedPages one = new TimedPages();
        one.configure(60, 0); one.sync(minute, true, 0); one.strokeBegins(0);
        check(one.tick(49_000, false) == TimedPages.State.RUNNING && one.tick(51_000, false) == TimedPages.State.BLINKING && one.tick(60_000, false) == TimedPages.State.TIME_UP, "A minute: running to 50 s, blinking the last 10, Time's up at 60");
    }

    // ---- a locked page ----

    static void lockedPage() throws Exception {
        ToneDocument page = marked(2);
        check(page.canUndo() && !page.locked(), "A marked page can undo");
        page.undo();
        check(page.canRedo(), "and redo");
        page.redo();
        page.timerLocks(true);
        check(page.setClock(PageClock.NONE.start().lock(60_000)) && page.locked(), "Locked");
        check(!page.canUndo() && !page.canRedo() && !page.undo() && !page.redo(), "A locked page has no Undo and no Redo, though it has the steps");
        check(!page.setClock(page.clock()), "(setting the same clock changes nothing)");
        ToneDocument blank = new ToneDocument(W, H);
        check(blank.unmarked(), "A blank page is blank");
        blank.setClock(PageClock.NONE.start().lock(60_000));
        check(!blank.unmarked() && !blank.layerSnapshot().unmarked(), "but not once locked (its first stroke was undone: the lock stays, the blank page at the end stays after it)");
        ToneDocument copy = new ToneDocument(page.layerSnapshot());
        copy.timerLocks(true);
        check(copy.locked() && copy.clock().equals(page.clock()), "A copy of a locked page is locked (a snapshot holds the clock)");
        // A locked page isn't taken for the blank page at the end.
        DrawingBook book = new DrawingBook(blankLocked());
        check(!book.current().locked() && !book.current().unmarked(), "The notebook keeps it, and sees it as marked, editable under Lock Pages off");
        book.setOptions(book.options().withLockPages(true));
        check(book.current().locked(), "and locked under Lock Pages on");
        // A PDF's page is locked whatever Lock Pages says: nothing spent tells it from a timer's.
        ToneDocument pdf = PictureImport.pdfPage(W, H, new int[W * H]);
        check(pdf.locked() && !pdf.canUndo(), "A PDF's page is locked with Lock Pages off");
    }
    static ToneDocument blankLocked() { ToneDocument page = new ToneDocument(W, H); page.setClock(PageClock.NONE.start().lock(1000)); return page; }

    // ---- persistence ----

    static void persistence() throws Exception {
        PageClock clock = PageClock.NONE.start().spentTo(61_234);
        check(PageClock.decode(clock.encode(), PageClock.BYTES).equals(clock) && PageClock.decode(clock.lock(180_000).encode(), PageClock.BYTES).locked, "A clock reads back from its bytes");
        check(PageClock.decode(new byte[3], 3) == null && PageClock.decode(new byte[]{(byte) 255, 0, 0, 0, 1}, 5) == null && PageClock.decode(new byte[]{0, 0, 0, 0, 0}, 5) == PageClock.NONE, "and one that doesn't is none");
        ToneDocument page = marked(3);
        byte[] plain = DrawingBook.encode(page);
        ToneDocument same = DocumentCodec.read(new ByteArrayInputStream(plain));
        check(Arrays.equals(DrawingBook.encode(same), plain) && !same.clock().kept(), "A page without a clock writes as it did (byte for byte)");
        page.setClock(PageClock.NONE.start().spentTo(90_000));
        byte[] timed = DrawingBook.encode(page);
        check(timed.length > plain.length, "A started clock is a section in the page's record");
        ToneDocument back = DocumentCodec.read(new ByteArrayInputStream(timed));
        check(back.clock().equals(page.clock()) && !back.locked(), "and reads back, running");
        page.setClock(page.clock().lock(180_000));
        ToneDocument locked = DocumentCodec.read(new ByteArrayInputStream(DrawingBook.encode(page)));
        check(locked.clock().locked && !locked.locked() && locked.clock().spent == 180_000, "A page run out reads back run out, editable on its own");
        locked.timerLocks(true);
        check(locked.locked() && !locked.canUndo(), "and locked, with no Undo, under Lock Pages");
        // Through a notebook's file: save and reopen.
        File dir = Files.createTempDirectory("rules").toFile();
        try {
            DrawingBook book = new DrawingBook(marked(4));
            book.addPage(); BlankPagesChecks.mark(book.current());
            book.select(0);
            NotebookOptions rules = NotebookOptions.DEFAULT.withSeconds(180);
            book.setOptions(rules);
            // Written before Lock Pages (an older notebook): no TLCK record, so it reads back off.
            byte[] older = NotebookOptionsChecks.bytes(book);
            check(book.current().setClock(PageClock.NONE.start().lock(180_000)), "Page 1 locks");
            book.select(1);
            book.current().setClock(PageClock.NONE.start().spentTo(42_000));
            book.select(0);
            check(book.unsaved(), "A clock is a change to save");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            BookCodec.write(out, book.snapshot());
            File file = new File(dir, "timed.mpaint"); Files.write(file.toPath(), out.toByteArray());
            for (DrawingBook b : new DrawingBook[]{BookCodec.open(file), BookCodec.read(new ByteArrayInputStream(out.toByteArray()))}) {
                check(b.options().seconds == 180 && !b.options().lockPages && b.current().clock().locked && !b.current().locked(), "Reopened: page 1 run out, editable, Lock Pages off");
                b.select(1);
                check(!b.current().locked() && b.current().clock().spent == 42_000 && b.current().clock().started, "page 2 has the time it spent");
            }
            // The timer on a reopened page counts on from what it spent, not from the wall clock.
            DrawingBook reopened = BookCodec.open(file);
            reopened.select(1);
            TimedPages t = new TimedPages();
            t.configure(180, 0); t.sync(reopened.current(), true, 7_000_000_000L);
            check(t.running() && t.remaining(7_000_000_000L + 8_000) == 130_000, "A reopened page's timer counts on from its time spent");
            // The recovery keeps it too.
            ByteArrayOutputStream recovery = new ByteArrayOutputStream();
            RecoveryCodec.write(recovery, book.snapshot(), "");
            DrawingBook recovered = RecoveryCodec.read(new ByteArrayInputStream(recovery.toByteArray())).book;
            check(recovered.options().seconds == 180, "The recovery keeps Timed Pages");
            check(recovered.index() == 0 && recovered.current().clock().locked && !recovered.current().locked(), "and the page run out: index " + recovered.index());
            // Lock Pages on: kept in the notebook's file and the recovery, the page run out locked on opening.
            book.setOptions(rules.withLockPages(true));
            ByteArrayOutputStream lockedOut = new ByteArrayOutputStream();
            BookCodec.write(lockedOut, book.snapshot());
            ByteArrayOutputStream lockedRecovery = new ByteArrayOutputStream();
            RecoveryCodec.write(lockedRecovery, book.snapshot(), "");
            for (DrawingBook b : new DrawingBook[]{BookCodec.read(new ByteArrayInputStream(lockedOut.toByteArray())), RecoveryCodec.read(new ByteArrayInputStream(lockedRecovery.toByteArray())).book}) {
                b.select(0);
                check(b.options().lockPages && b.current().locked() && !b.current().canUndo(), "Lock Pages on reads back on, page 1 locked");
            }
            check(!BookCodec.read(new ByteArrayInputStream(older)).options().lockPages, "An older notebook (no Lock Pages record) reads back with it off");
        } finally { delete(dir); }
    }

    // ---- A page's own rules (Page Settings) ----

    static void pageRules() throws Exception {
        int erasingOn = NotebookRules.withOwn(0, NotebookRules.ERASING_BITS, NotebookRules.OWN_ON), erasingOff = NotebookRules.withOwn(0, NotebookRules.ERASING_BITS, NotebookRules.OWN_OFF);
        int timedOn = NotebookRules.withOwn(0, NotebookRules.TIMED_BITS, NotebookRules.OWN_ON), timedOff = NotebookRules.withOwn(0, NotebookRules.TIMED_BITS, NotebookRules.OWN_OFF);
        NotebookOptions d = NotebookOptions.DEFAULT;
        check(NotebookRules.forPage(d, 0).equals(d) && NotebookRules.forPage(d.withNoErasing(true).withSeconds(180), 0).equals(d.withNoErasing(true).withSeconds(180)), "No rules of its own: the notebook's");
        check(NotebookRules.forPage(d, erasingOn).noErasing && !NotebookRules.forPage(d.withNoErasing(true), erasingOff).noErasing, "No Erasing on or off for the page alone");
        check(NotebookRules.forPage(d, timedOn).seconds == NotebookRules.PAGE_SECONDS && NotebookRules.forPage(d.withSeconds(180), timedOn).seconds == 180
                && NotebookRules.forPage(d.withSeconds(180), timedOff).seconds == 0, "Timed on its own: the notebook's time, else 5 min; off: none");
        check(NotebookRules.validOwn(erasingOn | timedOff) && !NotebookRules.validOwn(3) && !NotebookRules.validOwn(16), "A page's rules as written; anything else isn't");

        DrawingBook book = NotebookOptionsChecks.book();
        book.addPage(); BlankPagesChecks.mark(book.current()); book.select(0);
        byte[] before = NotebookOptionsChecks.bytes(book);
        TracingPaper tracing = book.tracing();
        book.setPageRules(1, erasingOn);
        book.setPageRules(2, timedOn);
        check(book.unsaved() && book.tracing().equals(tracing), "A page's rule is a change to save, never tracing paper's");
        // The eraser refused on page 2 alone; page 3 timed alone; a cover never.
        check(!NotebookRules.refuses(book.rules(0), ToolSlots.ERASER, null) && NotebookRules.refuses(book.rules(1), ToolSlots.ERASER, null) && !NotebookRules.refuses(book.rules(2), ToolSlots.ERASER, null),
                "The eraser refused on the page with No Erasing on, and only there");
        check(NotebookRules.undoOff(book.rules(1)) && !NotebookRules.undoOff(book.rules(0)) && NotebookRules.keeps(book.rules(1), true) && !NotebookRules.keeps(book.rules(2), true), "and Undo off and its marks kept there alone");
        check(NotebookRules.timedSeconds(book.rules(2), 2, false) == NotebookRules.PAGE_SECONDS && NotebookRules.timedSeconds(book.rules(0), 0, false) == 0 && NotebookRules.timedSeconds(book.rules(1), 1, false) == 0
                && book.rules(DrawingBook.FRONT).equals(book.options()), "Timed on page 3 alone; a cover keeps the notebook's");
        TimedPages timer = new TimedPages();
        timer.configure(NotebookRules.timedSeconds(book.rules(2), 2, false), 0);
        check(timer.seconds() == 300, "The clock takes the page's time");
        // The notebook timed, one page off.
        book.setOptions(d.withSeconds(180));
        check(book.rules(0).seconds == 180 && book.rules(2).seconds == 180, "Timed on its own takes the notebook's time");
        book.setPageRules(0, timedOff);
        check(book.rules(0).seconds == 0 && book.rules(1).seconds == 180 && book.options().seconds == 180, "Off for one page: the others and the notebook keep theirs");
        // Kept in the file (both readers), the shelf's reading and the recovery; only PRUL added.
        byte[] saved = NotebookOptionsChecks.bytes(book);
        File dir = Files.createTempDirectory("pagerules").toFile();
        try {
            File file = new File(dir, "rules.mpaint"); Files.write(file.toPath(), saved);
            for (DrawingBook b : new DrawingBook[]{BookCodec.read(new ByteArrayInputStream(saved)), BookCodec.open(file)})
                check(b.pageRules(0) == timedOff && b.pageRules(1) == erasingOn && b.pageRules(2) == timedOn && !b.unsaved() && b.tracing().equals(tracing), "The pages' rules read back, not a change");
            check(Arrays.equals(BookCodec.openPages(file).covers.pageRules, new int[]{timedOff, erasingOn, timedOn}), "as the file's end records them");
            ByteArrayOutputStream recovery = new ByteArrayOutputStream();
            RecoveryCodec.write(recovery, book.snapshot(), "");
            DrawingBook recovered = RecoveryCodec.read(new ByteArrayInputStream(recovery.toByteArray())).book;
            check(Arrays.equals(recovered.pageRules(), book.pageRules()), "The recovery keeps them");
        } finally { delete(dir); }
        // A page's rules go with it: moved, its stack's bottom deleted (the layer on it keeps them), deleted and put back.
        book.move(1, 2);
        check(book.pageRules(2) == erasingOn && book.pageRules(1) == timedOn, "Moved with the page");
        book.move(2, 1);
        DrawingBook.Taken taken = book.take(1);
        book.deletePage(1);
        check(book.pageRules(1) == timedOn, "Deleted: gone with it");
        book.restore(taken);
        check(book.pageRules(1) == erasingOn, "Put back: back with it");
        book.select(1);
        check(book.addLayer(true) && book.stacks().count() == 3 && book.pageRules(2) == erasingOn && book.rules(2).noErasing, "A layer on the page has its stack's rules");
        book.deletePage(1);
        check(book.pageRules(1) == erasingOn && book.stacks().count() == 3, "The stack's bottom deleted: the layer on it keeps them");
        // An old notebook (no page with rules of its own) writes byte for byte as before; set back, the file is as it was.
        DrawingBook old = NotebookOptionsChecks.book();
        old.addPage(); BlankPagesChecks.mark(old.current()); old.select(0);
        check(Arrays.equals(NotebookOptionsChecks.bytes(old), before) && Arrays.equals(NotebookOptionsChecks.bytes(BookCodec.read(new ByteArrayInputStream(before))), before), "A notebook without them: byte for byte as before");
        old.setPageRules(1, erasingOn);
        check(!Arrays.equals(NotebookOptionsChecks.bytes(old), before), "A page's rule changes the file");
        old.setPageRules(1, 0);
        check(Arrays.equals(NotebookOptionsChecks.bytes(old), before), "Put back to the notebook's: the file as it was, byte for byte");
        // The record: tagged, a byte a page; one for another page count, or with a byte it doesn't write, left out.
        BookOptions.Covers covers = new BookOptions.Covers(); covers.pageRules = new int[]{0, erasingOn, 0};
        byte[] block = BookOptions.bytes(covers);
        BookOptions.Found found = BookOptions.find(BookOptions.of(block), 0, block.length);
        check(BookOptions.tag("PRUL") == BookOptions.PAGE_RULES && found.records.size() == 1 && found.records.get(0).tag == BookOptions.PAGE_RULES
                && Arrays.equals(BookOptions.covers(found, BookOptions.of(block), null, 3).pageRules, covers.pageRules), "PRUL alone, read back");
        check(BookOptions.covers(found, BookOptions.of(block), null, 4).pageRules == null, "For another page count: left out");
        byte[] bad = BookOptions.pageRuleBytes(new int[]{0, erasingOn, 0}); bad[5] = 3;
        check(BookOptions.pageRules(bad, 3) == null && BookOptions.pageRuleBytes(new int[3]) == null && BookOptions.pageRuleBytes(null) == null, "A byte it doesn't write: left out; none set: no record");
    }

    // ---- A page's own time (Page Settings' slider) ----

    static void pageSeconds() throws Exception {
        int timedOn = NotebookRules.withOwn(0, NotebookRules.TIMED_BITS, NotebookRules.OWN_ON), timedOff = NotebookRules.withOwn(0, NotebookRules.TIMED_BITS, NotebookRules.OWN_OFF);
        NotebookOptions d = NotebookOptions.DEFAULT;
        check(NotebookRules.forPage(d, timedOn, 720).seconds == 720 && NotebookRules.forPage(d.withSeconds(180), timedOn, 720).seconds == 720
                && NotebookRules.forPage(d.withSeconds(180), timedOn, 0).seconds == 180 && NotebookRules.forPage(d, timedOn, 0).seconds == NotebookRules.PAGE_SECONDS
                && NotebookRules.forPage(d.withSeconds(180), 0, 720).seconds == 180 && NotebookRules.forPage(d.withSeconds(180), timedOff, 720).seconds == 0,
                "Its time while timed On alone (none: the notebook's, else 5 min); the notebook's or Off otherwise");

        // A notebook with a page's own rule (PRUL), none with a time: its bytes, to stay as they are.
        DrawingBook book = NotebookOptionsChecks.book();
        book.addPage(); BlankPagesChecks.mark(book.current()); book.addPage(); book.select(0);
        book.setPageRules(1, timedOn);
        byte[] before = NotebookOptionsChecks.bytes(book);
        check(Arrays.equals(NotebookOptionsChecks.bytes(BookCodec.read(new ByteArrayInputStream(before))), before), "Without a page's time: read and written byte for byte as before");
        book.setPageSeconds(1, 720);
        check(book.unsaved() && book.pageSeconds(1) == 720 && book.pageSeconds(0) == 0 && book.rules(1).seconds == 720 && book.rules(0).seconds == 0, "A page's time is a change to save, its own");
        book.setPageSeconds(2, 100);
        check(book.pageSeconds(2) == 90 && book.rules(2).seconds == 0, "The nearest stop; unused while the page isn't timed On alone");
        // The clock runs the page's time.
        TimedPages timer = new TimedPages();
        timer.configure(NotebookRules.timedSeconds(book.rules(1), 1, false), 0);
        check(timer.seconds() == 720 && timer.limit() == 720_000, "The clock takes the page's own time: " + timer.seconds());
        // Kept in the file (both readers, the shelf's reading) and the recovery.
        byte[] saved = NotebookOptionsChecks.bytes(book);
        File dir = Files.createTempDirectory("pageseconds").toFile();
        try {
            File file = new File(dir, "seconds.mpaint"); Files.write(file.toPath(), saved);
            for (DrawingBook b : new DrawingBook[]{BookCodec.read(new ByteArrayInputStream(saved)), BookCodec.open(file)})
                check(Arrays.equals(b.pageSeconds(), new int[]{0, 720, 90, 0}) && b.pageRules(1) == timedOn && b.rules(1).seconds == 720 && !b.unsaved(), "The pages' times read back, not a change: " + Arrays.toString(b.pageSeconds()) + " " + b.pageRules(1) + " " + b.rules(1).seconds + " " + b.unsaved());
            check(Arrays.equals(BookCodec.openPages(file).covers.pageSeconds, new int[]{0, 720, 90, 0}), "as the file's end records them");
            check(Arrays.equals(NotebookOptionsChecks.bytes(BookCodec.read(new ByteArrayInputStream(saved))), saved), "and written again byte for byte");
            ByteArrayOutputStream recovery = new ByteArrayOutputStream();
            RecoveryCodec.write(recovery, book.snapshot(), "");
            DrawingBook recovered = RecoveryCodec.read(new ByteArrayInputStream(recovery.toByteArray())).book;
            check(Arrays.equals(recovered.pageSeconds(), book.pageSeconds()) && recovered.rules(1).seconds == 720, "The recovery keeps them");
        } finally { delete(dir); }
        // With the page: moved, and its stack's bottom deleted (the layer on it keeps it).
        book.move(1, 2);
        check(book.pageSeconds(2) == 720 && book.pageSeconds(1) == 90, "Moved with the page");
        book.move(2, 1);
        book.select(1);
        check(book.addLayer(true) && book.pageSeconds(2) == 720 && book.rules(2).seconds == 720, "A layer on the page has its stack's time");
        book.deletePage(1);
        check(book.pageSeconds(1) == 720 && book.pageRules(1) == timedOn, "The stack's bottom deleted: the layer on it keeps it");
        // Set back to none: the file as it was.
        DrawingBook again = BookCodec.read(new ByteArrayInputStream(before));
        again.setPageSeconds(1, 720);
        check(!Arrays.equals(NotebookOptionsChecks.bytes(again), before), "A page's time changes the file");
        again.setPageSeconds(1, 0);
        check(Arrays.equals(NotebookOptionsChecks.bytes(again), before), "Set back to none: byte for byte as before");
        // The record: tagged, two bytes a page; one for another page count, or over an hour, left out; none: no record.
        BookOptions.Covers covers = new BookOptions.Covers(); covers.pageSeconds = new int[]{0, 720, 0};
        byte[] block = BookOptions.bytes(covers);
        BookOptions.Found found = BookOptions.find(BookOptions.of(block), 0, block.length);
        check(BookOptions.tag("PTIM") == BookOptions.PAGE_SECONDS && found.records.size() == 1 && found.records.get(0).tag == BookOptions.PAGE_SECONDS
                && Arrays.equals(BookOptions.covers(found, BookOptions.of(block), null, 3).pageSeconds, covers.pageSeconds), "PTIM alone, read back");
        check(BookOptions.covers(found, BookOptions.of(block), null, 4).pageSeconds == null, "For another page count: left out");
        check(BookOptions.pageSeconds(BookOptions.pageSecondBytes(new int[]{0, 3601, 0}), 3) == null && BookOptions.pageSecondBytes(new int[3]) == null && BookOptions.pageSecondBytes(null) == null,
                "Over an hour: left out; none set: no record");
    }

    // ---- Lock ----

    static void lock() {
        NotebookOptions open = NotebookOptions.DEFAULT;
        NotebookOptions own = open.withTools(NotebookOptions.Tools.SURPRISE, toolbarLines(4));
        check(!own.toolbarFrozen(), "A notebook's own Surprise Me toolbar is free");
        check(open.withSeconds(420).seconds == 420 && open.withSeconds(95).seconds == 90 && open.withSeconds(10).seconds == 0 && open.withSeconds(-5).seconds == 0 && open.withSeconds(9999).seconds == 3600
                && open.withSeconds(60).limitMs() == 60_000 && open.withSeconds(1800).limitMs() == 1_800_000 && NotebookRules.timeLabel(1800).equals("30 min"), "Timed Pages: Off, 30 s to an hour, snapped to the slider's stops");
        check(NotebookOptions.SECONDS.length == 66 && NotebookOptions.SECONDS[0] == 0 && NotebookOptions.SECONDS[1] == 30 && NotebookOptions.SECONDS[10] == 300 && NotebookOptions.SECONDS[11] == 360
                && NotebookOptions.SECONDS[65] == 3600 && NotebookOptions.SECONDS[65] * 1000L <= PageClock.MOST_MS, "Off, 30 s steps to 5 min, then minutes to an hour, which a page's clock holds");
        // The library card's screens for them.
        check(Arrays.equals(CardSheet.names(CardSheet.Screen.TRANSPARENCY, null), new String[]{"Off", "Page Before", "Page Before, Faint", "Nearby Pages", "Nearby Pages, Faint"}), "Page Transparency: Off, then each at Half and Faint");
        String[] timed = new String[CardSheet.stops(CardSheet.Screen.TIMED)];
        for (int i = 0; i < timed.length; i++) timed[i] = CardSheet.sliderWord(CardSheet.Screen.TIMED, i);
        check(timed.length == 66 && Arrays.asList(timed).subList(0, 5).equals(Arrays.asList("Off", "30 s", "1 min", "90 s", "2 min")) && timed[65].equals("60 min")
                && CardSheet.slides(CardSheet.Screen.TIMED) && CardSheet.slides(CardSheet.Screen.TOOLS) && !CardSheet.slides(CardSheet.Screen.TEMPLATE) && !CardSheet.slides(CardSheet.Screen.PAGE_TEMPLATE), "Timed Pages' screen is a slider, Brushes one under Tools', none on Template's (Page Colour is the settings list's row)");
        String[] brushes = new String[CardSheet.BRUSH_STOPS];
        for (int i = 0; i < brushes.length; i++) brushes[i] = CardSheet.sliderWord(CardSheet.Screen.TOOLS, i);
        check(Arrays.equals(brushes, new String[]{"1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "All"}) && CardSheet.brushesAt(CardSheet.brushesStop(0)) == 0 && CardSheet.brushesAt(CardSheet.brushesStop(3)) == 3, "Brushes: 1 to 10, then All");
        check(NotebookOptions.DEFAULT.readMinutes(new byte[]{30}).seconds == 1800 && NotebookOptions.DEFAULT.withSeconds(1800).minutesBytes()[0] == 30 && NotebookRules.blinks(30_000, 1800) && !NotebookRules.blinks(31_000, 1800), "30 min is saved and read, and blinks the last 30 s");
    }

    // ---- records ----

    /** Timed Pages in seconds: TSEC read back over TIMD; TIMD the nearest minutes the builds before offered; a file with TIMD alone (theirs) reads its minutes. */
    static void seconds() throws Exception {
        for (int s : new int[]{30, 90, 270, 300, 420, 3600}) {
            BookOptions.Covers covers = new BookOptions.Covers(); covers.options = NotebookOptions.DEFAULT.withSeconds(s);
            byte[] block = BookOptions.bytes(covers);
            check(BookOptions.options(BookOptions.find(BookOptions.of(block), 0, block.length), BookOptions.of(block)).seconds == s, s + " s reads back");
            int old = covers.options.minutesBytes()[0];
            check(old == 1 || old == 3 || old == 5 || old == 10 || old == 30, s + " s is " + old + " min for the builds before seconds");
            // As the builds before seconds wrote it: TSEC gone (here renamed to a tag no one knows, which is passed by).
            byte[] older = block.clone();
            for (int i = 0; i + 4 <= older.length; i++) if (BookPictures.readInt(older, i) == BookOptions.TIMED_SECONDS) { older[i] = 'X'; break; }
            check(BookOptions.options(BookOptions.find(BookOptions.of(older), 0, older.length), BookOptions.of(older)).seconds == old * 60, "and a file with TIMD alone reads its minutes: " + old);
        }
        check(NotebookOptions.DEFAULT.withSeconds(90).secondsBytes().length == 2 && NotebookOptions.DEFAULT.readSeconds(NotebookOptions.DEFAULT.withSeconds(3600).secondsBytes()).seconds == 3600, "Two bytes hold an hour");
        DrawingBook book = NotebookOptionsChecks.book();
        book.setOptions(NotebookOptions.DEFAULT.withSeconds(90));
        ByteArrayOutputStream recovery = new ByteArrayOutputStream();
        RecoveryCodec.write(recovery, book.snapshot(), "");
        check(RecoveryCodec.read(new ByteArrayInputStream(recovery.toByteArray())).book.options().seconds == 90, "The recovery keeps 90 s");
        System.out.println("PASS: Timed Pages in seconds (30 s to an hour) are saved as TSEC and read back, TIMD written as the nearest old minutes for the builds before, a file with TIMD alone reads its minutes, the recovery keeps them");
    }

    static void records() throws Exception {
        File dir = Files.createTempDirectory("rulesrec").toFile();
        try {
            DrawingBook book = NotebookOptionsChecks.book();
            byte[] before = NotebookOptionsChecks.bytes(book);
            ToolSlotsChecks.prefs = new HashMap<>();
            ToolSlots slots = new ToolSlots(ToolSlotsChecks.store(), new Random(1));
            NotebookOptions[] alone = {NotebookOptions.DEFAULT.withNoErasing(true), NotebookOptions.DEFAULT.withBrushes(3), NotebookOptions.DEFAULT.withSeconds(300)};
            int[] tags = {BookOptions.NO_ERASING, BookOptions.BRUSHES, BookOptions.TIMED};
            // Timed Pages is two records: TIMD (minutes, for the builds before seconds), then TSEC.
            for (int i = 0; i < alone.length; i++) {
                BookOptions.Covers covers = new BookOptions.Covers(); covers.options = alone[i];
                byte[] block = BookOptions.bytes(covers);
                BookOptions.Found found = BookOptions.find(BookOptions.of(block), 0, block.length);
                check(found != null && found.records.size() == (i == 2 ? 2 : 1) && found.records.get(0).tag == tags[i] && (i != 2 || found.records.get(1).tag == BookOptions.TIMED_SECONDS), "Rule " + i + " alone is its record");
                check(BookOptions.options(found, BookOptions.of(block)).equals(alone[i]), "and reads back");
                check(BookOptions.length(covers) == block.length, "and its length is as written");
            }
            check(BookOptions.tag("NERS") == BookOptions.NO_ERASING && BookOptions.tag("ONEB") == BookOptions.ONE_BRUSH && BookOptions.tag("BRSH") == BookOptions.BRUSHES && BookOptions.tag("TIMD") == BookOptions.TIMED && BookOptions.tag("LOCK") == BookOptions.LOCK, "Tags");
            NotebookOptions full = NotebookOptions.DEFAULT.withTemplate(NotebookTemplate.DIARY).withNoErasing(true).withBrushes(1).withSeconds(180);
            check(full.hasRules() && full.brushes == 1 && !full.ownsTools(), "Everything on");
            book.setOptions(full);
            byte[] after = NotebookOptionsChecks.bytes(book);
            check(!Arrays.equals(before, after), "Rules change the file");
            File file = new File(dir, "rules.mpaint"); Files.write(file.toPath(), after);
            DrawingBook whole = BookCodec.read(new ByteArrayInputStream(after)), opened = BookCodec.open(file);
            for (DrawingBook b : new DrawingBook[]{whole, opened}) check(b.options().equals(full) && !b.unsaved(), "The rules read back from the file, not a change");
            check(BookCodec.openPages(file).covers.options.equals(full), "and are what the file's end records (the shelf's reading)");
            ByteArrayOutputStream recovery = new ByteArrayOutputStream();
            RecoveryCodec.write(recovery, book.snapshot(), "");
            check(RecoveryCodec.read(new ByteArrayInputStream(recovery.toByteArray())).book.options().equals(full), "The recovery keeps them");
            List<PackedPage> a = BookCodec.openPages(file).pages;
            File base = new File(dir, "base.mpaint"); Files.write(base.toPath(), before);
            List<PackedPage> b = BookCodec.openPages(base).pages;
            check(a.get(0).crc == b.get(0).crc && a.get(1).crc == b.get(1).crc, "The pages are byte for byte as without rules");
            book.setOptions(NotebookOptions.DEFAULT);
            check(Arrays.equals(NotebookOptionsChecks.bytes(book), before), "Rules put back to the default: the file as it was, byte for byte");
            check(Arrays.equals(NotebookOptionsChecks.bytes(BookCodec.read(new ByteArrayInputStream(before))), before), "and a notebook from before them writes itself unchanged");
            // Records that don't read.
            check(NotebookOptions.DEFAULT.readMinutes(new byte[]{7}).seconds == 420 && NotebookOptions.DEFAULT.readMinutes(new byte[]{3}).seconds == 180 && NotebookOptions.DEFAULT.readMinutes(new byte[0]).seconds == 0
                    && NotebookOptions.DEFAULT.readMinutes(new byte[]{61}).seconds == 0 && NotebookOptions.DEFAULT.readSeconds(new byte[]{1}).seconds == 0, "Minutes past an hour, or a damaged record, are Off");
            seconds();
            check(NotebookOptions.DEFAULT.readOneBrush("garbage".getBytes("UTF-8")).brushes == 0 && NotebookOptions.DEFAULT.readNoErasing(new byte[]{1, 1}).equals(NotebookOptions.DEFAULT), "and a damaged One Brush or No Erasing is off");
        } finally { delete(dir); }
    }

    // ---- Lock Settings and Guides in PDF, cut (the refocus, 2026-10-24) ----

    /** {@code bytes} with the first record tagged {@code from} retagged {@code to} (its bytes as they were): a record as an older version wrote it. */
    static byte[] retag(byte[] bytes, int from, int to) {
        byte[] out = bytes.clone();
        for (int i = 0; i + 4 <= out.length; i++) if (BookPictures.readInt(out, i) == from) {
            for (int k = 0; k < 4; k++) out[i + k] = (byte) (to >>> (24 - 8 * k));
            return out;
        }
        throw new AssertionError("no record to retag");
    }
    /**
     * A notebook Locked or Sealed before the cut opens unlocked, its rules (No Erasing here) as they were, not a change;
     * saved again its file keeps no lock (a version before the cut opens it Open). The same for its recovery. One that left
     * its guides out of the PDF (Guides in PDF: Leave Out) opens with its template, which its copies now print.
     */
    static void lockCut() throws Exception {
        for (int key : new int[]{1, 2}) {
            DrawingBook book = NotebookOptionsChecks.book();
            // No Erasing and Brushes 1/2: the Brushes record (one byte, 1 or 2) retagged LOCK is Locked or Sealed as the file kept it.
            book.setOptions(NotebookOptions.DEFAULT.withNoErasing(true).withBrushes(key));
            byte[] older = retag(NotebookOptionsChecks.bytes(book), BookOptions.BRUSHES, BookOptions.LOCK);
            DrawingBook read = BookCodec.read(new ByteArrayInputStream(older));
            String what = key == 1 ? "Locked" : "Sealed";
            check(read.options().equals(NotebookOptions.DEFAULT.withNoErasing(true)) && !read.unsaved(), "A notebook " + what + " before opens unlocked, No Erasing still on");
            check(!read.options().toolbarFrozen() && read.count() == book.count(), "its toolbar free, its pages all there");
            byte[] again = NotebookOptionsChecks.bytes(read);
            BookOptions.Covers c = new BookOptions.Covers(); c.options = read.options();
            byte[] block = BookOptions.bytes(c);
            BookOptions.Found found = BookOptions.find(BookOptions.of(block), 0, block.length);
            boolean lockKept = false;
            for (BookOptions.Record r : found.records) lockKept |= r.tag == BookOptions.LOCK;
            check(!lockKept && found.records.size() == 1 && again.length > 0, "Saved again: no lock record, only No Erasing's");
        }
        // The recovery as the version before wrote it for a Locked notebook: its rules' fourth array the lock's key.
        DrawingBook book = NotebookOptionsChecks.book();
        book.setOptions(NotebookOptions.DEFAULT.withNoErasing(true));
        ByteArrayOutputStream recovery = new ByteArrayOutputStream();
        RecoveryCodec.write(recovery, book.snapshot(), "");
        byte[] now = recovery.toByteArray(), older = null;
        for (int i = 0; i + 4 <= now.length && older == null; i++) if (BookPictures.readInt(now, i) == 0x54535255) { // TSRU, the rules
            int at = i + 4;
            for (int k = 0; k < 3; k++) at += 4 + BookPictures.readInt(now, at);
            check(BookPictures.readInt(now, at) == 0, "The lock's array is written empty");
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            o.write(now, 0, at); o.write(new byte[]{0, 0, 0, 1, 1}); o.write(now, at + 4, now.length - at - 4);
            older = o.toByteArray();
        }
        check(older != null, "The recovery has the rules");
        check(RecoveryCodec.read(new ByteArrayInputStream(older)).book.options().equals(NotebookOptions.DEFAULT.withNoErasing(true)), "A Locked notebook's recovery opens unlocked, No Erasing on");
        // Guides in PDF: Leave Out, as the file kept it (one byte, 0): Page Colour Black's record (one byte, 0) retagged GPDF.
        DrawingBook lined = NotebookOptionsChecks.book();
        lined.setOptions(NotebookOptions.DEFAULT.withTemplate(NotebookTemplate.LINED).withPaper(0));
        DrawingBook left = BookCodec.read(new ByteArrayInputStream(retag(NotebookOptionsChecks.bytes(lined), BookOptions.PAGE_COLOUR, BookOptions.GUIDES)));
        check(left.options().equals(NotebookOptions.DEFAULT.withTemplate(NotebookTemplate.LINED)) && left.snapshot().printedTemplate(0).equals(NotebookTemplate.LINED),
                "A notebook that left its guides out of the PDF opens with its template, which the copies print");
        System.out.println("PASS: Lock Settings and Guides in PDF cut: locked, sealed and Leave Out notebooks open as said, their rules kept, no lock written again");
    }

    // ---- helpers ----

    static ToneDocument marked(int seed) {
        ToneDocument page = new ToneDocument(W, H);
        page.begin();
        Random r = new Random(seed);
        for (int i = 0; i < 200; i++) page.setTone(r.nextInt(W), r.nextInt(H), r.nextInt(128));
        page.finish();
        return page;
    }
    static void delete(File file) {
        File[] kids = file.listFiles();
        if (kids != null) for (File k : kids) delete(k);
        file.delete();
    }
}
