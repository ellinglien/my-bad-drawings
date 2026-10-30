package io.github.mpdairy.monopaint;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Wet paint's work on a thread of its own ({@link SettleWorker}, {@link WatercolorSession#tick}): a Slow wash, a gravity
 * wash on a tilted page and a drifting Float bath end on the page they end on when every tick runs on the page's
 * thread, one Undo for all; laid now (a new stroke, the page put away) while a tick runs there, a wash is laid
 * as it shows, without waiting for the tick, to the pixels laying it after as many ticks here gives; a wash gravity
 * has moved is laid after the tick running there; a page changed meanwhile drops the tick unwritten; paced, it ends
 * the same. The wet indicator's tap ({@link WatercolorSession#hurry}) runs what is wet on to the page it would end on
 * left alone, shown once.
 */
public final class SettleWorkerChecks {
    static final long MS = 1_000_000;

    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        sameEnd(); layWhileTicking(); masked(); neverWaits(); pageChanged(); floats(); paced(); hurried();
        System.out.println("PASS: wet paint's ticks run on a thread of their own and end on the same page (Slow washes, gravity tilted, a drifting bath, one Undo); laid now while a tick runs there it is laid as it shows without waiting for the tick, to the pixels laying it after as many ticks gives (a wash gravity moved after the running tick); a page changed meanwhile drops the tick unwritten; paced, it ends the same; "
                + "the wet indicator's tap dries a Slow wash (gravity tilted too), a bath (drifting or level), halos and wet paint to the very page left alone to dry gives, there or here, shown once, one Undo");
    }

    /** A wet case: its page with a Slow wash (or bath) just lifted, in {@code s}. */
    interface Case { ToneDocument draw(WatercolorSession s); }
    static final int W = 900, H = 600;
    /** {@code style}'s brush as the bench rolls it, Slow (Drying, Settle .5). */
    static Case wash(String style, double size) { return wash(slow(style), size); }
    static ModularBrush slow(String style) {
        ModularStyle st = ModularStyle.named(style);
        ModularBrush b = st.roll(new java.util.Random(100 + st.index));
        ModularBrush.Part p = new ModularBrush.Part(PartChoice.DRYING);
        p.set(PartDial.SETTLE, .5);
        b.set(BrushPart.LIFE, p);
        return b;
    }
    static Case wash(ModularBrush b, double size) { return s -> wash(InkWashLayers.blank(W, H), b, size, s); }
    /** A wave of {@code b} at {@code size} px across {@code d}, in {@code s}. */
    static ToneDocument wash(ToneDocument d, ModularBrush b, double size, WatercolorSession s) {
        ModularStroke st = ModularStroke.start(d, b, size, 1, s);
        for (int i = 0; i <= 240; i++) { double u = i / 240.0; st.sample((float)(120 + 660 * u), (float)(300 + 160 * Math.sin(2 * Math.PI * u)), (float)(.3 + .5 * Math.sin(Math.PI * u)), Float.NaN, Float.NaN, 10_000 + i * 3L); if (i % 8 == 7) st.present(); }
        st.finish();
        return d;
    }
    static ModularBrush preset(String name) { return FloatChecks.preset(name); }
    static Case bath() {
        return s -> {
            ToneDocument d = InkWashLayers.blank(W, H);
            FloatChecks.rings(d, s, 300, 300, 3);
            FloatChecks.rings(d, s, 560, 280, 2);
            return d;
        };
    }
    static WatercolorSession session(boolean worker, boolean tilted) {
        WatercolorSession s = new WatercolorSession();
        if (worker) s.worker = new SettleWorker();
        if (tilted) s.tilt = () -> PageTilt.of(.15, .8);
        return s;
    }
    /** Ticks {@code s} as the drying ticker does, until it has settled or {@code ticks} ticks are written; the ticks written. */
    static int run(WatercolorSession s, ToneDocument d, int ticks) {
        int[] written = {0};
        while (written[0] < ticks) {
            long next = s.tick(d, () -> { written[0]++; d.clearDirty(); });
            if (next < 0) break;
            if (next == SettleWorker.POLL_MS) pause(1);
        }
        return written[0];
    }
    /** Ticks it until a tick is running there (true), or it has settled. */
    static boolean startTick(WatercolorSession s, ToneDocument d) {
        while (!s.ticking()) {
            long next = s.tick(d, d::clearDirty);
            if (next < 0) return false;
            if (next == SettleWorker.POLL_MS && !s.ticking()) pause(1);
        }
        return true;
    }
    /** The page here: {@code ticks} ticks on this thread, then laid now. */
    static ToneDocument here(Case c, boolean tilted, int ticks) {
        WatercolorSession s = session(false, tilted);
        ToneDocument d = c.draw(s);
        for (int i = 0; i < ticks && s.tickDrying(d); i++) { }
        s.settleNow(d);
        return d;
    }
    static String crc(ToneDocument d) { return SettleHashesCrc.of(d); }
    static String undone(ToneDocument d) { int n = 0; while (d.undo()) n++; String c = crc(d) + " in " + n; for (int i = 0; i < n; i++) d.redo(); return c; }

    static final Object[][] CASES = {
            {"Watercolor 60", wash("Watercolor", 60), false}, {"Ink wash 200", wash("Ink wash", 200), false},
            {"Slow bloom 120", wash(preset("Slow bloom"), 120), false}, {"Drips 120 tilted", wash(preset("Drips"), 120), true},
            {"Pooling wash 60 tilted", wash(preset("Pooling wash"), 60), true}};

    /** Every tick there ends on the page every tick here does, one Undo for all. */
    static void sameEnd() {
        for (Object[] k : CASES) {
            Case c = (Case)k[1]; boolean tilted = (Boolean)k[2];
            ToneDocument expected = here(c, tilted, 1000);
            WatercolorSession s = session(true, tilted);
            ToneDocument d = c.draw(s);
            int ticks = run(s, d, 100_000);
            check(!s.settling() && ticks > 5, k[0] + ": it settles there (" + ticks + " ticks)");
            check(crc(d).equals(crc(expected)), k[0] + ": ticked there, it ends on the page ticked here does");
            check(undone(d).equals(undone(expected)), k[0] + ": and one Undo takes it all back, as here");
        }
    }
    /** Laid while a tick runs there: as the page shows (a wash gravity moved: after that tick). */
    static void layWhileTicking() {
        int asShown = 0, after = 0;
        for (Object[] k : CASES) for (int ticks : new int[]{0, 1, 2, 5, 9}) {
            Case c = (Case)k[1]; boolean tilted = (Boolean)k[2];
            WatercolorSession s = session(true, tilted);
            ToneDocument d = c.draw(s);
            if (ticks == 0) {
                // Just after the lift: the lift's work ahead is running or done.
                s.settleNow(d);
                check(crc(d).equals(crc(here(c, tilted, 0))), k[0] + ": laid at once after the lift, as here");
                continue;
            }
            int written = run(s, d, ticks);
            boolean moved = s.drying() != null && !s.drying().finishesAhead();
            if (!startTick(s, d)) continue;
            s.settleNow(d);
            check(!s.settling(), k[0] + ": laid, nothing settles");
            int expected = moved ? written + 1 : written;
            if (moved) after++; else asShown++;
            check(crc(d).equals(crc(here(c, tilted, expected))), k[0] + ": laid while tick " + (written + 1) + " runs there, the page is as laid here after " + expected + (moved ? " (gravity has moved it: the tick first)" : " (as it shows)"));
            check(undone(d).equals(undone(here(c, tilted, expected))), k[0] + ": one Undo for all");
        }
        check(asShown > 10 && after > 3, "Laid as it shows " + asShown + " times, after the tick running there " + after + " times");
    }
    /** Under masking fluid (whose shield gives a written pixel less than asked), laid while a tick runs there, as here. */
    static void masked() {
        Case c = s -> {
            ToneDocument d = InkWashLayers.blank(W, H);
            d.paintMaskingFluid(true);
            d.begin();
            for (int y = 250; y < 330; y++) for (int x = 300; x < 600; x++) d.setTone(x, y, 60);
            d.finish();
            d.paintMaskingFluid(false);
            return wash(d, slow("Watercolor"), 120, s);
        };
        for (int ticks : new int[]{1, 4}) {
            WatercolorSession s = session(true, false);
            ToneDocument d = c.draw(s);
            int written = run(s, d, ticks);
            check(startTick(s, d), "a tick runs there");
            s.settleNow(d);
            check(crc(d).equals(crc(here(c, false, written))), "Under masking fluid, laid while a tick runs there, as laid here after " + written + " ticks");
        }
    }
    /** Laid while a tick waits there behind other work: it doesn't wait for it. */
    static void neverWaits() {
        Case c = wash("Watercolor", 120);
        WatercolorSession s = session(true, false);
        ToneDocument d = c.draw(s);
        run(s, d, 3);
        CountDownLatch release = new CountDownLatch(1);
        // The worker held up after the work ahead started at the third tick: the tick that starts next can't run until released.
        s.worker.run(() -> release.await(5, TimeUnit.SECONDS));
        check(startTick(s, d), "a tick waits there");
        long t0 = System.nanoTime();
        s.settleNow(d);
        long took = System.nanoTime() - t0;
        release.countDown();
        Timing.limit(took < 1000 * MS, "Laid now, it doesn't wait for the tick (" + took / MS + " ms)");
        check(crc(d).equals(crc(here(c, false, 3))), "and lays the wash as it shows, as here after as many ticks");
    }
    /** A page changed while a tick runs there (Undo): the tick is dropped unwritten. */
    static void pageChanged() {
        Case c = wash("Watercolor", 120);
        WatercolorSession s = session(true, false);
        ToneDocument d = c.draw(s);
        run(s, d, 2);
        check(startTick(s, d), "a tick runs there");
        d.undo();
        String undone = crc(d);
        run(s, d, 100_000);
        check(!s.settling() && crc(d).equals(undone), "A page changed meanwhile (Undo) drops the tick unwritten, and the wash");
    }
    /** A Float bath drifting there: printed on time, printed now while a tick runs, and joined by a Float stroke. */
    static void floats() {
        Case c = bath();
        ToneDocument expected = here(c, true, 1000);
        WatercolorSession s = session(true, true);
        ToneDocument d = c.draw(s);
        int ticks = run(s, d, 100_000);
        check(!s.settling() && crc(d).equals(crc(expected)), "A bath drifting there prints on time on the page it does here (" + ticks + " ticks)");
        check(undone(d).equals(undone(expected)), "one Undo after the print, as here");
        for (int k : new int[]{1, 4}) {
            s = session(true, true);
            d = c.draw(s);
            int written = run(s, d, k);
            check(startTick(s, d), "a drift runs there");
            s.settleNow(d);
            check(crc(d).equals(crc(here(c, true, written))), "Printed while a drift runs there, as it shows: as printed here after " + written + " ticks");
        }
        // A Float stroke joining the bath while a drift runs there: after it, as here.
        s = session(true, true);
        d = c.draw(s);
        int written = run(s, d, 3);
        check(startTick(s, d), "a drift runs there");
        FloatChecks.stroke(d, s, FloatChecks.clear(), 8, FloatChecks.tap(420, 300, 200), false);
        s.settleNow(d);
        WatercolorSession h = session(false, true);
        ToneDocument e = c.draw(h);
        for (int i = 0; i < written + 1; i++) h.tickDrying(e);
        FloatChecks.stroke(e, h, FloatChecks.clear(), 8, FloatChecks.tap(420, 300, 200), false);
        h.settleNow(e);
        check(crc(d).equals(crc(e)), "A Float stroke joining while a drift runs there joins the bath after it, as here");
    }
    /** Paced (backing off, showing at most every 120 ms), the work there ends on the page unpaced work here does. */
    static void paced() {
        Case c = wash("Ink wash", 160);
        ToneDocument expected = here(c, false, 1000);
        long[] clock = {0};
        WatercolorSession s = session(true, false);
        s.pace = new SettlePacer(() -> clock[0]);
        ToneDocument d = c.draw(s);
        int calls = 0;
        long next;
        do {
            if (calls % 7 == 3) s.input(0, 40);
            next = s.tick(d, () -> { d.clearDirty(); clock[0] += 30 * MS; });
            if (next == SettleWorker.POLL_MS) pause(1);
            if (next > 0) clock[0] += next * MS;
        } while (next >= 0 && ++calls < 100_000);
        check(!s.settling() && s.pace.backOffs > 0, "Paced there, it settles, backing off (" + s.pace.backOffs + " times)");
        check(crc(d).equals(crc(expected)), "and ends on the page unpaced ticks here do");
    }

    /**
     * The wet indicator's tap a few ticks in ({@link WatercolorSession#hurry}, then ticked as the drying ticker ticks it,
     * paced): the page every tick left alone gives, then dry (wet paint dried), shown once at the end, one Undo for all.
     * Wet paint moves in slices sized by the clock ({@link WetCanvas#step}), so it ends within a tone of the whole frames
     * left alone here, as the drawing screen's own ticks do.
     */
    static void hurried() {
        java.util.List<Object[]> cases = new java.util.ArrayList<>(java.util.Arrays.asList(CASES));
        cases.add(new Object[]{"bath tilted", bath(), true});
        cases.add(new Object[]{"bath level", bath(), false});
        cases.add(new Object[]{"halos", (Case)s -> { ToneDocument d = HaloChecks.bars(); HaloChecks.draw(d, s, HaloChecks.INK, 12, HaloChecks.line(120)); HaloChecks.draw(d, s, HaloChecks.INK, 12, HaloChecks.line(220)); return d; }, false});
        cases.add(new Object[]{"wet paint", (Case)s -> { ToneDocument d = WetPaintChecks.page(); WetPaintChecks.stroke(d, s, WetPaintChecks.gouache(), 60, .9, WetPaintChecks.line(40, 160, 440, 160)); return d; }, false});
        for (Object[] k : cases) for (boolean there : new boolean[]{false, true}) {
            Case c = (Case)k[1]; boolean tilted = (Boolean)k[2];
            ToneDocument expected = here(c, tilted, 100_000);
            WatercolorSession s = session(there, tilted);
            ToneDocument d = c.draw(s);
            // Two ticks left alone (shown each), then paced as the app paces them.
            run(s, d, 2);
            String where = k[0] + (there ? " there" : " here");
            check(s.settling(), where + ": still moving at the tap");
            long[] clock = {0};
            s.pace = new SettlePacer(() -> clock[0]);
            int[] shown = {0};
            if (s.hurry(d)) for (long next; (next = s.tick(d, () -> { shown[0]++; d.clearDirty(); })) >= 0; ) { if (next > 0) pause(next); }
            else shown[0]++;
            check(!s.wet() && !s.hurrying(), where + ": the tap leaves nothing wet");
            check(shown[0] == 1, where + ": shown once, at the end (" + shown[0] + ")");
            if (s.canvas() == null && k[0].equals("wet paint")) {
                int most = 0, off = 0;
                for (int y = 0; y < d.height; y++) for (int x = 0; x < d.width; x++) { int e = Math.abs(d.compositeTone(x, y) - expected.compositeTone(x, y)); most = Math.max(most, e); if (e > 0) off++; }
                check(most <= 2 && off * 100 < d.width * d.height, where + ": the tap ends within a tone or two of the page left to dry (" + off + " px off, by " + most + " at most)");
                continue;
            }
            check(crc(d).equals(crc(expected)), where + ": the tap ends on the page left to dry gives");
            check(undone(d).equals(undone(expected)), where + ": one Undo for all, as left to dry");
        }
    }

    static void pause(long ms) { try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    /** A page's layers as one CRC. */
    static final class SettleHashesCrc {
        static String of(ToneDocument d) {
            java.util.zip.CRC32 c = new java.util.zip.CRC32();
            ToneDocument.Snapshot snap = d.layerSnapshot();
            for (int i = 0; i < snap.layers.size(); i++) { ToneDocument.Layer l = snap.layers.get(i).flattened(); c.update(l.tones); c.update(l.alpha); }
            return String.format("%08x", c.getValue());
        }
    }
}
