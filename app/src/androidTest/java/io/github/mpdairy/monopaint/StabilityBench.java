package io.github.mpdairy.monopaint;

import java.util.*;

/**
 * Heap under load (docs/archive/stability-2026-10-22.md), pure Java so it runs on this computer (with -Xmx192m, the Manta's
 * heap) and on the Manta through the bench build (scripts/bench_device.sh -e stability wash,session). Each action
 * reports the heap kept after it (after a GC), the most seen while it ran (sampled every 20 ms) and its time; an
 * OutOfMemoryError is reported, not thrown.
 * <ul>
 * <li>wash: Large strokes of {@code -e presets} (default Soft wash, Wet gouache, Sumi, Mold ink) across most of a page.</li>
 * <li>wet: {@code -e actions} Medium strokes of one wet paint preset (default Soft wash) side by side, never dried between.</li>
 * <li>session: {@code -e actions} (default 120) strokes of heavy presets at Large, fills and Undo runs on a 20-page notebook, turning pages.</li>
 * <li>fill: a held whole-page fill of a blank page and fills of a big box, a small one and a big one with a gap, drawn in
 * pen ({@link #SCENES}), with {@code -e presets} (default Soft wash, Wet gouache, Fountain pen, Sumi), its area found then
 * laid, each timed, and each one's phases (fill.ink … fill.refine, of which fill.flood holds ink, bands and gap; then
 * fill.lay, fill.settle, fill.compose).</li>
 * </ul>
 */
final class StabilityBench {
    static final int W = 1872, H = 2480;
    static final Runtime RT = Runtime.getRuntime();
    static volatile long peak;
    static long used() { return RT.totalMemory() - RT.freeMemory(); }
    static long kept() { System.gc(); System.runFinalization(); System.gc(); return used(); }

    public static void main(String[] a) throws Exception {
        Assets.install(Assets.folder(Assets.FOLDER));
        StringBuilder out = new StringBuilder();
        run(a.length > 0 ? a[0] : "wash,session", a.length > 1 ? a[1] : null, a.length > 2 ? Integer.parseInt(a[2]) : 120, out, () -> { System.out.print(out); out.setLength(0); });
    }

    static void run(String what, String presets, int actions, StringBuilder out, Runnable flush) throws Exception {
        Thread sampler = new Thread(() -> { while (true) { peak = Math.max(peak, used()); try { Thread.sleep(20); } catch (InterruptedException e) { return; } } }, "heap sampler");
        sampler.setDaemon(true); sampler.start();
        out.append(String.format(Locale.ROOT, "heap limit %.0f MB%n", RT.maxMemory() / 1e6));
        try {
            for (String w : what.split(",")) {
                if (w.equals("wash")) wash(presets == null ? new String[]{"Soft wash", "Wet gouache", "Sumi", "Mold ink"} : presets.split(","), out, flush);
                if (w.equals("session")) session(actions, out, flush);
                if (w.equals("fill")) fill(presets == null ? new String[]{"Soft wash", "Wet gouache", "Fountain pen", "Sumi"} : presets.split(","), out, flush);
                if (w.equals("wet")) wet(presets == null ? "Soft wash" : presets.split(",")[0], actions, out, flush);
            }
        } finally { sampler.interrupt(); }
        flush.run();
    }

    static ModularBrush brush(String name) { for (ToolPresets.Preset p : ToolPresets.all()) if (p.name.equals(name)) return p.brush().parts; throw new IllegalArgumentException(name); }

    /** One action: its time, the heap kept after and the most seen during it; false on OutOfMemoryError. */
    interface Action { void run() throws Exception; }
    static boolean time(String name, Action action, StringBuilder out) {
        long before = kept(); peak = used();
        long t = System.nanoTime();
        String result = "ok";
        try { action.run(); }
        catch (OutOfMemoryError e) { result = "OUT OF MEMORY"; }
        catch (Exception e) { result = "failed: " + e; }
        double ms = (System.nanoTime() - t) / 1e6;
        long after = kept();
        out.append(String.format(Locale.ROOT, "%-34s %8.0f ms  heap kept %5.1f -> %5.1f MB, most %5.1f MB  %s%n", name, ms, before / 1e6, after / 1e6, peak / 1e6, result));
        return result.equals("ok");
    }

    /** A sketch: pen lines and a medium wash, as a page in use. */
    static ToneDocument page(WatercolorSession session) {
        ToneDocument d = InkWashLayers.blank(W, H);
        Random r = new Random(1);
        for (int k = 0; k < 6; k++) stroke(d, session, brush("Fountain pen"), 8, 1, scribble(150, W - 150, 250 + k * 380, 60, .8, r));
        stroke(d, session, brush("Soft wash"), 80, .6, scribble(300, 1200, 1300, 200, .8, r));
        session.settleNow(d);
        return d;
    }

    static void wash(String[] presets, StringBuilder out, Runnable flush) {
        for (String name : presets) {
            WatercolorSession session = new WatercolorSession();
            ToneDocument[] d = {page(session)};
            ModularBrush b = brush(name);
            double size = ModularSizes.size(b, InkWashSizes.Preset.LARGE);
            // Across most of the page, back and forth: a big wash.
            long[] wet = {0};
            time("Large " + name + " over the page", () -> {
                stroke(d[0], session, b, size, .9, scribble(100, W - 100, H / 2.0, 900, .85, new Random(2)));
                if (session.canvas() != null) wet[0] = session.canvas().wet.heldBytes();
                session.settleNow(d[0]);
            }, out);
            out.append(String.format(Locale.ROOT, "  its wet tiles %.1f MB%n", wet[0] / 1e6));
            d[0] = null;
            flush.run();
        }
    }

    /** The fill's scenes: a held blank page, a big box, a small box and a big box with a 6 px gap at a corner, all in pen. */
    static final String[] SCENES = {"page", "box", "small", "gap"};
    static double[][] scene(String s) {
        int cx = W / 2, cy = H / 2;
        switch (s) {
            case "box": return new double[][]{{200, 200, .8, 0}, {W - 200, 200, .8, 400}, {W - 200, H - 200, .8, 900}, {200, H - 200, .8, 1300}, {200, 190, .8, 1800}};
            case "small": return new double[][]{{cx - 150, cy - 150, .8, 0}, {cx + 150, cy - 150, .8, 400}, {cx + 150, cy + 150, .8, 800}, {cx - 150, cy + 150, .8, 1200}, {cx - 150, cy - 160, .8, 1600}};
            // The pen's 8 px ends 14 px apart: a 6 px gap the area holds at.
            case "gap": return new double[][]{{200, 200, .8, 0}, {W - 200, 200, .8, 400}, {W - 200, H - 200, .8, 900}, {200, H - 200, .8, 1300}, {200, 214, .8, 1800}};
            default: return null;
        }
    }
    static void fill(String[] presets, StringBuilder out, Runnable flush) {
        for (String name : presets) {
            ModularBrush b = brush(name);
            double size = ModularSizes.size(b, InkWashSizes.Preset.LARGE);
            for (String scene : SCENES) {
                WatercolorSession session = new WatercolorSession();
                ToneDocument[] d = {InkWashLayers.blank(W, H)};
                double[][] lines = scene(scene);
                if (lines != null) stroke(d[0], session, brush("Fountain pen"), 8, 1, lines);
                session.settleNow(d[0]);
                FillRegion.Room[] room = new FillRegion.Room[1];
                String what = scene + " " + name + " (" + b.choice(BrushPart.LIFE) + ")";
                // Each phase's time (Prof's fill sections): the area's, then the laying's.
                Prof.on = true; Prof.reset();
                time("find " + what, () -> room[0] = FillTool.region(d[0], W / 2, H / 2), out);
                phases(out, Prof.F_INK, Prof.F_REFINE);
                Prof.reset();
                time("lay " + what + " " + room[0].area / 1000 + "k px", () -> {
                    ModularStroke st = FillTool.start(d[0], b, size, .7, session, room[0]);
                    st.present(); st.finish();
                    for (int k = 0; k < 600 && session.tickDrying(d[0]); k++) { }
                    session.settleNow(d[0]);
                }, out);
                phases(out, Prof.F_LAY, Prof.F_COMPOSE);
                Prof.on = false;
                d[0] = null; room[0] = null;
                flush.run();
            }
        }
    }
    /** Prof's sections {@code from..to}, ms. */
    static void phases(StringBuilder out, int from, int to) {
        out.append("   ");
        for (int k = from; k <= to; k++) out.append(String.format(Locale.ROOT, " %s %.0f", Prof.NAMES[k], Prof.t[k] / 1e6));
        out.append(" ms\n");
    }

    static void wet(String name, int strokes, StringBuilder out, Runnable flush) {
        WatercolorSession session = new WatercolorSession();
        ToneDocument d = InkWashLayers.blank(W, H);
        ModularBrush b = brush(name);
        double size = ModularSizes.size(b, InkWashSizes.Preset.MEDIUM);
        for (int k = 0; k < strokes; k++) {
            int row = k / 3 % 5, col = k % 3, seed = k;
            if (!time("wet " + name + " " + (k + 1), () -> stroke(d, session, b, size, .8, scribble(80 + col * 600, 80 + col * 600 + 520, 200 + row * 420, 150, .8, new Random(seed))), out)) break;
            flush.run();
        }
        time("droplet tap (dried)", () -> session.settleNow(d), out);
    }

    static void session(int actions, StringBuilder out, Runnable flush) throws Exception {
        WatercolorSession session = new WatercolorSession();
        DrawingBook book = new DrawingBook(page(session));
        byte[] packed = DrawingBook.encode(book.current());
        for (int i = 1; i < 20; i++) book.appendUnchanged(PackedPage.of(packed), null);
        String[] heavy = {"Soft wash", "Wet gouache", "Flat wash", "Sumi", "Mold ink", "Charcoal dust", "Stump", "Fountain pen", "Bleed marker", "Pooling wash"};
        Random r = new Random(5);
        int failed = 0;
        for (int n = 0; n < actions && failed < 3; n++) {
            double x = r.nextDouble();
            String name; Action action;
            if (x < .6) {
                ModularBrush b = brush(heavy[r.nextInt(heavy.length)]);
                double size = ModularSizes.size(b, InkWashSizes.Preset.LARGE), x0 = W * (.05 + .4 * r.nextDouble()), y = H * (.1 + .8 * r.nextDouble()), len = W * (.2 + .3 * r.nextDouble()), amp = 40 + 200 * r.nextDouble();
                long seed = r.nextLong();
                name = "stroke " + b.choice(BrushPart.LIFE);
                action = () -> stroke(book.current(), session, b, size, .9, scribble(x0, x0 + len, y, amp, .8, new Random(seed)));
            } else if (x < .7) {
                ModularBrush b = brush(heavy[r.nextInt(heavy.length)]);
                int tx = r.nextInt(W), ty = r.nextInt(H);
                name = "fill";
                action = () -> {
                    ToneDocument d = book.current();
                    session.settleNow(d);
                    FillRegion.Room room = FillTool.region(d, tx, ty);
                    if (room == null) return;
                    ModularStroke st = FillTool.start(d, b, ModularSizes.size(b, InkWashSizes.Preset.LARGE), .7, session, room);
                    st.present(); st.finish();
                    for (int k = 0; k < 600 && session.tickDrying(d); k++) { }
                };
            } else if (x < .85) {
                int k = 1 + r.nextInt(10);
                name = "undo/redo";
                action = () -> { ToneDocument d = book.current(); session.settleNow(d); for (int i = 0; i < k; i++) d.undo(); for (int i = 0; i < k / 2; i++) d.redo(); };
            } else {
                int to = r.nextInt(book.count());
                name = "page turn";
                action = () -> { session.settleNow(book.current()); book.select(to); };
            }
            if (!time(n + " " + name, action, out)) failed++;
            if (n % 10 == 9) flush.run();
        }
    }

    /** A stroke as the drawing screen lays one, the wet paint run for at most 600 ticks after. */
    static void stroke(ToneDocument d, WatercolorSession session, ModularBrush b, double size, double darkness, double[][] path) {
        boolean wet = ResolvedBrush.of(b, size, darkness).wet != null;
        if (wet) session.settleBefore(d, b); else session.settleNow(d);
        DrawingStroke s = QuickStroke.start(d, b, size, darkness, session);
        try { for (double[] p : path) { s.sample((float) p[0], (float) p[1], ToolSettings.pressure((float) p[2]), 0, 0, (long) p[3]); s.present(); } }
        finally { s.finish(); }
        for (int k = 0; k < 600 && session.tickDrying(d); k++) { }
    }
    /** Loops across, like handwriting, at pressure {@code p} with a little wander (PresetPictures.scribble's). */
    static double[][] scribble(double x0, double x1, double yc, double amp, double p, Random r) {
        int n = (int) Math.ceil((x1 - x0) * 1.6);
        double[][] o = new double[n + 1][];
        double drift = r.nextDouble();
        for (int i = 0; i <= n; i++) {
            double u = (double) i / n, t = u * Math.PI * 2 * ((x1 - x0) / 40) + drift;
            o[i] = new double[]{x0 + (x1 - x0) * u + amp * .5 * Math.cos(t), yc + amp * Math.sin(t), p + .2 * Math.sin(t * .37), i * 3};
        }
        return o;
    }
}
