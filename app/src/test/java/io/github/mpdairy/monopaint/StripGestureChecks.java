package io.github.mpdairy.monopaint;

/**
 * The page bar's previews (docs/archive/superpowers/specs/2026-10-20-tracing-paper-design.md, Stage 1): what a touch is (tap,
 * riffle, lift, options) by its time and distance, the same for pen and finger, a second pointer calling it off; the
 * tiles, the window round the page drawn on, where a lifted page lands; moving a page under the lock and a timed page's
 * clock; 100 previews made from pictures in time.
 */
public final class StripGestureChecks {
    public static void main(String[] args) throws Exception {
        gestures();
        edge();
        tiles();
        lock();
        timing();
        System.out.println("PASS: strip gestures: a tap, a riffle (8 dp before 400 ms), a lift (a hold then a move), options (a hold released, or the pen's button), the same for pen and finger, a second pointer cancels; tiles and the window round the page; a lifted page lands on pages only; a page moves under Locked and Sealed, its clock with it; 100 previews from pictures in time");
    }

    /** A finger from the screen's top band (the Supernote's tray): a pull down is the system's, decided before a riffle; a tap or a riffle along the bar is the bar's. */
    static void edge() {
        float slop = 15; // 8 dp on the Manta
        check(StripGesture.edgePull(3, 10, slop) == 0, "Under 8 dp: undecided, a tap still goes");
        check(StripGesture.edgePull(2, 15, slop) == 1 && StripGesture.edgePull(-10, 12, slop) == 1, "Down, or down and a little aside: the tray's");
        check(StripGesture.edgePull(15, 2, slop) == -1 && StripGesture.edgePull(-12, 10, slop) == -1 && StripGesture.edgePull(0, -15, slop) == -1, "Along the bar, or up: the bar's riffle");
        StripGesture g = new StripGesture(slop);
        g.down(0, 100, 5, false);
        check(g.move(50, 102, 14) == StripGesture.Kind.PRESSED, "The pull is decided on the move a riffle would start on, not before");
        check(StripGesture.SYSTEM_EDGE_DP >= 37, "The band covers Android's 69 px (37 dp) on the Manta");
    }

    static void gestures() {
        float move = 15; // 8 dp on the Manta
        StripGesture g = new StripGesture(move);
        g.down(0, 100, 10, false);
        check(g.move(100, 107, 10) == StripGesture.Kind.PRESSED && g.up(200, 107, 10) == StripGesture.Kind.TAP, "A touch that moves under 8 dp and lifts before the hold is a tap");
        g.down(0, 100, 10, false);
        check(g.move(399, 115, 10) == StripGesture.Kind.RIFFLE && g.dragging(), "8 dp before 400 ms riffles");
        check(g.move(900, 300, 10) == StripGesture.Kind.RIFFLE && g.up(1000, 300, 10) == StripGesture.Kind.RIFFLE, "A riffle stays a riffle, however long, and ends one");
        g.down(0, 100, 10, false);
        check(g.tick(399) == StripGesture.Kind.PRESSED && g.tick(400) == StripGesture.Kind.HELD, "The hold is 400 ms");
        check(g.move(500, 114, 10) == StripGesture.Kind.HELD, "Held, a move under 8 dp doesn't lift");
        check(g.move(600, 115, 10) == StripGesture.Kind.LIFT && g.dragging() && g.up(700, 200, 10) == StripGesture.Kind.LIFT, "Held, then 8 dp: the page lifts, and drops where it lifts");
        g.down(0, 100, 10, false);
        check(g.up(450, 103, 12) == StripGesture.Kind.OPTIONS, "A hold released where it began opens the options");
        g.down(0, 100, 10, false);
        check(g.move(450, 130, 10) == StripGesture.Kind.LIFT, "A move after the hold's time lifts even before the hold's tick is seen");
        g.down(0, 100, 10, true);
        check(g.kind() == StripGesture.Kind.OPTIONS && g.up(50, 100, 10) == StripGesture.Kind.OPTIONS, "The pen's side button on landing opens the options at once");
        g.down(0, 100, 10, false); g.move(50, 130, 10);
        check(g.cancel() == StripGesture.Kind.CANCELLED && !g.dragging() && g.up(100, 130, 10) == StripGesture.Kind.CANCELLED, "A second pointer calls a riffle off");
        g.down(0, 100, 10, false); g.cancel();
        check(g.up(50, 100, 10) == StripGesture.Kind.CANCELLED, "A cancelled tap goes nowhere");
        check(StripGesture.HOLD_MS == 400 && StripGesture.MOVE_DP == 8, "The prototype's 400 ms and 8 dp to start with, for pen and finger alike");
    }

    static void tiles() {
        int count = 24;
        check(StripGesture.tileOf(DrawingBook.FRONT, count) == 0 && StripGesture.tileOf(0, count) == 1 && StripGesture.tileOf(DrawingBook.BACK, count) == 26 && StripGesture.placeAt(25, count) == DrawingBook.NONE, "The front cover first, the + tile before the back cover, the back cover last");
        for (int tile = 0; tile < count + 3; tile++) if (tile != count + 1) check(StripGesture.tileOf(StripGesture.placeAt(tile, count), count) == tile, "Tile " + tile + " round-trips");
        check(StripGesture.landing(DrawingBook.FRONT, count) == 0 && StripGesture.landing(DrawingBook.BACK, count) == 23 && StripGesture.landing(7, count) == 7, "A lifted page lands on pages only: over a cover, at the nearest end");
        // The window round the page drawn on: page 12's tile in the middle of 21, the ends held.
        check(NotebookLayout.window(StripGesture.tileOf(11, 100), 103, 21) == 2 && NotebookLayout.window(0, 103, 21) == 0 && NotebookLayout.window(102, 103, 21) == 82
                && NotebookLayout.window(5, 6, 21) == 0, "The window round the drawing page, held at the ends; a short notebook shows whole");
        // A turn keeps the window while the page turned to shows in it (only the marks move), not under the pinned +.
        check(StripGesture.shows(2, 3, 100, 21) && StripGesture.shows(2, 21, 100, 21) && !StripGesture.shows(2, 22, 100, 21) && !StripGesture.shows(2, 1, 100, 21),
                "Kept from tile 2 for tiles 2 to 21, the last slot the pinned +");
        check(StripGesture.shows(82, 102, 100, 21) && !StripGesture.shows(83, 90, 100, 21) && !StripGesture.shows(-1, 0, 100, 21),
                "At the end the + has its own tile; a window past the end, or none yet, is not kept");
        check(StripGesture.shows(0, 5, 3, 21) && !StripGesture.shows(1, 5, 3, 21), "A short notebook shows whole, from the first tile only");
        // Moving: each place maps as DrawingBook.move moves it.
        int[] order = {0, 1, 2, 3, 4};
        int from = 1, to = 3;
        int[] after = new int[5];
        for (int i = 0; i < 5; i++) after[DrawingBook.moved(i, from, to)] = order[i];
        check(java.util.Arrays.equals(after, new int[]{0, 2, 3, 1, 4}) && DrawingBook.moved(DrawingBook.FRONT, 1, 3) == DrawingBook.FRONT, "Page 2 moved to place 4: 1 3 4 2 5, the covers stay");
        for (int i = 0; i < 5; i++) after[DrawingBook.moved(i, 4, 0)] = order[i];
        check(java.util.Arrays.equals(after, new int[]{4, 0, 1, 2, 3}), "The last page moved first");
    }

    /** A timed page moves with its clock (spec, "Rules, the lock and timed pages"; Lock Settings cut 2026-10-24). */
    static void lock() throws Exception {
        DrawingBook book = NotebookFileChecks.book(3, 32, 40);
        book.setOptions(book.options().withNoErasing(true));
        book.current().setClock(PageClock.NONE.lock(60_000));
        ToneDocument timed = book.current();
        book.move(2, 0);
        check(book.index() == 0 && book.current() == timed && book.current().clock().locked, "A page moves under No Erasing, its run-out clock with it");
        DrawingBook again = BookCodec.read(new java.io.ByteArrayInputStream(NotebookFileChecks.bytes(book.snapshot())));
        check(again.index() == 0 && again.current().clock().locked && again.options().noErasing, "and is saved so");
    }

    /** 100 previews from their pictures well within the spec's 50 ms (the pictures already decoded, as the bar keeps them). */
    static void timing() throws Exception {
        java.util.List<PagePicture> pictures = new java.util.ArrayList<>();
        java.util.Random random = new java.util.Random(3);
        for (int i = 0; i < 100; i++) {
            ToneDocument page = InkWashLayers.blank(1920, 2560);
            page.begin(); for (int k = 0; k < 400; k++) page.setTone(random.nextInt(1920), random.nextInt(2560), random.nextInt(200)); page.finish();
            if (i < 4) pictures.add(PagePicture.decode(PagePicture.of(page.layerSnapshot()).encode()));
            else pictures.add(pictures.get(i % 4));
        }
        int[] first = pictures.get(0).dots(41, 56);
        check(first.length == 41 * 56, "A preview is 41 × 56 px, the Manta's 22 × 30 dp");
        long best = Long.MAX_VALUE;
        for (int round = 0; round < 5; round++) {
            long start = System.nanoTime();
            for (PagePicture p : pictures) p.dots(41, 56);
            best = Math.min(best, System.nanoTime() - start);
        }
        System.out.println("100 previews from pictures: " + best / 1_000_000 + " ms");
        Timing.limit(best < 50_000_000L, "100 previews from pictures under 50 ms: " + best / 1_000_000 + " ms");
        // A black picture's preview is black dots, a white one's white.
        ToneDocument black = InkWashLayers.blank(64, 80);
        black.begin(); for (int x = 0; x < 64; x++) for (int y = 0; y < 80; y++) black.setTone(x, y, 0); black.finish();
        int[] dark = PagePicture.of(black.layerSnapshot()).dots(8, 10), light = PagePicture.of(InkWashLayers.blank(64, 80).layerSnapshot()).dots(8, 10);
        for (int i = 0; i < dark.length; i++) check(dark[i] == 0xff000000 && light[i] == 0xffffffff, "Black stays black, paper white");
    }

    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
