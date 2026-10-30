package io.github.mpdairy.monopaint;

import java.util.Arrays;
import java.util.Locale;
import java.util.Random;

/**
 * The six-part engine's timing bench: replays a realistic pen stroke through a six-part brush on a full Manta page,
 * the way DrawingPad does it, in real time. The pen reports {@link #HZ} samples a second; the screen batches whatever
 * arrived by each 60 Hz frame into one event (history included); the event's samples go to stroke.sample one by one,
 * then the page's dirty rectangle is rendered (the {@link Render} hook: nothing on the desktop, the native dot
 * compose into a page-sized bitmap on the device). When handling falls behind, the next event takes every sample
 * that arrived meanwhile, as Android's input batching does. Reports per-sample engine cost, per-event render cost,
 * and the lag behind the pen at lift. Pen to screen: each sample's wait from the pen reporting it until its event's render
 * is done. Paced as the app is ({@link #PACED}): the session's {@link SettlePacer} on the replay's clock, told of each
 * event's handling time and age as DrawingPad tells it, so a Fast-wet re-settle's box shows when the app's would.
 */
final class ModularPerfBench {
    interface Render { /** Renders the page's dirty rectangle {l, t, r, b} (r, b exclusive); returns nanos spent. */ long render(ToneDocument d, int[] dirty); }

    static final int W = 1920, H = 2560, HZ = 480;
    static final double FRAME_MS = 1000.0 / 60;
    /** Whether the replay paces settling as the app does (InkWash's session has a {@link SettlePacer}); false: unpaced, every re-settle shown at once. */
    static boolean PACED = !"false".equals(System.getProperty("paced"));
    /**
     * How input reaches the stroke. SYNC: as the app on the Manta sees it, one sample an event, each sampled, composed
     * and rendered before the next is handled (they queue meanwhile). BATCH: each event only collects its sample and the
     * stroke takes every sample collected once the queue is empty ({@link PenBatch}), so a slow batch is followed by
     * a bigger one. VSYNC: Android's batching, whatever arrived by each 60 Hz frame in one event.
     */
    enum Input { SYNC, BATCH, VSYNC }
    static Input INPUT = Input.valueOf(System.getProperty("input", "BATCH").toUpperCase(Locale.ROOT));
    /**
     * Which strokes are drawn quick, then rendered at lift ({@link QuickStroke}): "off", every stroke live (as before the
     * quick strokes, and {@link #draw}'s pages); "rule", as the app decides ({@link QuickStroke#quick}); "on", every stroke.
     * A quick stroke's true stroke renders on its own thread as the replay goes; its lift waits for it and composes.
     */
    static String QUICK = System.getProperty("quick", "off");
    /** A stroke as {@link #QUICK} says. */
    static DrawingStroke start(ToneDocument d, ModularBrush b, double size, WatercolorSession session) {
        // Wet (the wet paint spec): always its own stroke, laid into the wet paint, whatever QUICK says.
        if (ResolvedBrush.of(b, size, 1).wet != null) return QuickStroke.start(d, b, size, 1, session);
        if (QUICK.equals("on")) return QuickStroke.quickly(d, b, size, 1, session);
        if (QUICK.equals("rule")) return QuickStroke.start(d, b, size, 1, session);
        return ModularStroke.start(d, b, size, 1, session);
    }
    /** Every style, in the lab's order. */
    static String[] styles() { java.util.List<ModularStyle> all = ModularStyle.all(); String[] s = new String[all.size()]; for (int i = 0; i < s.length; i++) s[i] = all.get(i).name; return s; }

    /** The stroke: an S-curve across the page, {@code ms} long, about 1 px/ms in the middle (8.5 cm/s), pressure rising and falling. */
    static float[][] path(double ms) {
        int n = (int)Math.round(ms * HZ / 1000);
        float[][] s = new float[n][6];
        for (int i = 0; i < n; i++) {
            double u = (double)i / (n - 1), env = Math.sin(Math.PI * u);
            s[i][0] = (float)(260 + 1400 * u);
            s[i][1] = (float)(1280 + 600 * Math.sin(2 * Math.PI * u) + 300 * (u - .5));
            s[i][2] = (float)(.2 + .6 * Math.pow(env, .6));
            s[i][3] = (float)(25 * Math.cos(3 * u));
            s[i][4] = 30;
            s[i][5] = (float)(i * 1000.0 / HZ);
        }
        return s;
    }

    /** Whether {@code style} names one brush as it is (a preset, "@Name", or an M code), drawn once with its own Life. */
    static boolean asIs(String style) { return style.startsWith("@") || style.contains(".M"); }
    /** Erasable for every brush the bench draws (1: every brush as before Erasable), or null: each brush's own. */
    static Double ERASABLE = System.getProperty("erasable") == null ? null : Double.valueOf(System.getProperty("erasable"));
    static ModularBrush brush(String style, PartChoice life) {
        ModularBrush b = brushAsIs(style, life);
        if (ERASABLE != null) b.part(BrushPart.MEDIUM).set(PartDial.ERASABLE, ERASABLE);
        return b;
    }
    private static ModularBrush brushAsIs(String style, PartChoice life) {
        // An M code: that brush as it is, its own Life.
        if (style.contains(".M")) { ModularBrush b = ModularCode.decode(style); if (b == null) throw new IllegalArgumentException("No brush " + style); return b; }
        // "@Name": a Tools page preset as it is, its own Life.
        if (style.startsWith("@")) {
            for (ToolPresets.Preset p : ToolPresets.withRetired()) if (p.name.equals(style.substring(1))) return ModularCode.decode(p.code);
            throw new IllegalArgumentException("No preset " + style);
        }
        ModularStyle st = ModularStyle.named(style);
        ModularBrush b = st.roll(new Random(100 + st.index));
        ModularBrush.Part p = new ModularBrush.Part(life);
        if (life == PartChoice.DRYING) p.set(PartDial.SETTLE, .5);
        b.set(BrushPart.LIFE, p);
        return b;
    }

    static final class Result {
        double meanSample, p95Sample, maxSample, meanEvent, p95Event, maxEvent, renderMean, renderMax, lift, finish, lagAtLift, penMs, busyMs;
        /** Pen to screen: each sample's wait from the pen reporting it to its event's render done, ms (median, p95, max). */
        double latP50, latP95, latMax;
        long dirtyMean, dirtyMax; int samples, events;
        /** Drawn quick, then rendered at lift. */
        boolean quick;
        /** The page and session as the stroke left them (the wet bench moves its paint after). */
        ToneDocument page; WatercolorSession session;
        long[] prof = new long[Prof.N];
    }

    /**
     * Whether the page has marks on the stroke's own layer before it (hatching and bands): a smudge over blank paper
     * flatters (Smudge spec, "Speed on the Manta").
     */
    static boolean MARKS = Boolean.getBoolean("marks");
    /** How erasable the marks are (below 1: laid permanent in part, so the layer has a share, as the Erasable spec's bench asks). */
    static double MARKS_ERASABLE = Double.parseDouble(System.getProperty("marksErasable", "1"));
    static void marks(ToneDocument d, ModularBrush b) {
        d.selectLayer(ModularSizes.inks(b) ? InkWashLayers.INK : InkWashLayers.WASH);
        d.begin(MARKS_ERASABLE);
        int[] row = new int[W];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) row[x] = Math.floorMod(x + y / 3, 12) < 3 ? 70 : x / 100 % 3 == 1 ? 150 : -1;
            d.setTones(0, y, W, 1, row);
        }
        d.finish();
    }
    /** What is on the page before a {@link #run}'s stroke (Float's bath), or null. */
    static java.util.function.BiConsumer<ToneDocument, WatercolorSession> BEFORE;
    /** One real-time replay; {@code render} may be null. */
    static Result run(ModularBrush b, double size, Render render, double strokeMs) {
        float[][] s = path(strokeMs);
        ToneDocument d = InkWashLayers.blank(W, H);
        if (MARKS) { marks(d, b); d.clearDirty(); }
        Prof.on = true; Prof.reset();
        long base = 10_000;
        WatercolorSession session = new WatercolorSession();
        if (BEFORE != null) { BEFORE.accept(d, session); d.clearDirty(); }
        // The replay's clock, ns: the frame an event is handled at, plus the time spent handling it so far.
        long[] clock = {0, System.nanoTime()};
        if (PACED) session.pace = new SettlePacer(() -> clock[0] + (System.nanoTime() - clock[1]));
        DrawingStroke stroke = start(d, b, size, session);
        double[] per = new double[s.length]; int ns = 0;
        double[] evt = new double[s.length]; double[] rend = new double[s.length]; int ne = 0;
        long dirtySum = 0, dirtyMax = 0;
        double cursor = 0; int next = 0; double busy = 0;
        double[] lat = new double[s.length];
        while (next < s.length) {
            // The frame this event is dispatched at: the first vsync at or after both the cursor and the next sample.
            double ready = Math.max(cursor, s[next][5]);
            double frame = INPUT == Input.VSYNC ? Math.ceil(ready / FRAME_MS) * FRAME_MS : ready;
            long e0 = System.nanoTime();
            clock[0] = (long)(frame * 1e6); clock[1] = e0;
            int first = next;
            while (next < s.length && s[next][5] <= frame && (INPUT != Input.SYNC || next == first)) {
                float[] q = s[next++];
                long t0 = System.nanoTime();
                stroke.sample(q[0], q[1], q[2], q[3], q[4], base + (long)q[5]);
                per[ns++] = (System.nanoTime() - t0) / 1e6;
            }
            // As DrawingPad does once an event's samples are in; charged to the event's last sample.
            long t0 = System.nanoTime();
            stroke.present();
            if (ns > 0) per[ns - 1] += (System.nanoTime() - t0) / 1e6;
            long r = 0;
            int[] dirty = d.dirty();
            if (dirty != null) {
                long area = (long)(dirty[2] - dirty[0] + 4) * (dirty[3] - dirty[1] + 4);
                dirtySum += area; dirtyMax = Math.max(dirtyMax, area);
                if (render != null) r = render.render(d, dirty);
                d.clearDirty();
            }
            double em = (System.nanoTime() - e0) / 1e6;
            // As DrawingPad: the event's handling time and how long after its newest sample it began.
            session.input(System.nanoTime() - e0, (long)Math.max(0, frame - s[INPUT == Input.BATCH ? first : next - 1][5]));
            evt[ne] = em; rend[ne] = r / 1e6; ne++;
            busy += em;
            cursor = frame + em;
            for (int k = first; k < next; k++) lat[k] = cursor - s[k][5];
        }
        double penEnd = s[s.length - 1][5];
        clock[0] = (long)(Math.max(cursor, penEnd) * 1e6); clock[1] = System.nanoTime();
        long f0 = System.nanoTime();
        stroke.finish();
        double fin = (System.nanoTime() - f0) / 1e6;
        long r0 = System.nanoTime();
        int[] dirty = d.dirty();
        if (dirty != null && render != null) render.render(d, dirty);
        double lastRender = (System.nanoTime() - r0) / 1e6;
        Result out = new Result();
        double[] a = Arrays.copyOf(per, ns); Arrays.sort(a);
        double sum = 0; for (double v : a) sum += v;
        out.meanSample = sum / ns; out.p95Sample = a[(int)(ns * .95)]; out.maxSample = a[ns - 1]; out.samples = ns;
        double[] e = Arrays.copyOf(evt, ne); Arrays.sort(e);
        sum = 0; for (double v : e) sum += v;
        out.meanEvent = sum / ne; out.p95Event = e[(int)(ne * .95)]; out.maxEvent = e[ne - 1]; out.events = ne;
        sum = 0; double rm = 0; for (int i = 0; i < ne; i++) { sum += rend[i]; rm = Math.max(rm, rend[i]); }
        out.renderMean = sum / ne; out.renderMax = rm;
        Arrays.sort(lat);
        out.latP50 = lat[lat.length / 2]; out.latP95 = lat[(int)(lat.length * .95)]; out.latMax = lat[lat.length - 1];
        out.dirtyMean = dirtySum / ne; out.dirtyMax = dirtyMax;
        out.quick = stroke instanceof QuickStroke;
        out.finish = fin; out.lift = fin + lastRender;
        out.penMs = penEnd; out.busyMs = busy;
        // How long after the pen lifted the screen shows the finished stroke.
        out.lagAtLift = Math.max(0, cursor - penEnd) + fin + lastRender;
        out.page = d; out.session = session;
        System.arraycopy(Prof.t, 0, out.prof, 0, Prof.N);
        return out;
    }

    static String header() {
        return String.format(Locale.ROOT, "%-13s %-8s %4s | %7s %7s %7s | %7s %7s %5s | %7s %7s | %8s | %6s %6s %6s | %5s %7s %7s | %s%n",
                "style", "life", "px", "smp_ms", "p95", "max", "evt_ms", "p95", "evts", "rnd_ms", "rndMax", "dirtyKpx", "pen50", "pen95", "penMax", "lag_s", "busy%", "lift", "profile ms (per stroke)");
    }
    static String row(String style, String life, double size, Result r) {
        StringBuilder p = new StringBuilder();
        for (int k = 0; k < Prof.N; k++) {
            if (r.prof[k] == 0) continue;
            String name = Prof.NAMES[k];
            // Counts: "#…" as they are, "…Px" in thousands; the rest are times, ms.
            if (name.startsWith("#")) p.append(' ').append(name.substring(1)).append('=').append(r.prof[k]);
            else if (name.endsWith("Px")) p.append(' ').append(name).append('=').append(r.prof[k] / 1000).append('k');
            else p.append(' ').append(name).append('=').append(String.format(Locale.ROOT, "%.0f", r.prof[k] / 1e6));
        }
        return String.format(Locale.ROOT, "%-13s %-8s %4.0f%s| %7.2f %7.2f %7.1f | %7.1f %7.1f %5d | %7.1f %7.1f | %8d | %6.0f %6.0f %6.0f | %5.2f %6.0f%% %7.0f |%s%n",
                style, life, size, r.quick ? "q" : " ", r.meanSample, r.p95Sample, r.maxSample, r.meanEvent, r.p95Event, r.events, r.renderMean, r.renderMax,
                r.dirtyMean / 1000, r.latP50, r.latP95, r.latMax, r.lagAtLift / 1000, 100 * r.busyMs / r.penMs, r.lift, p);
    }

    /** Runs the matrix; {@code styles}, {@code sizes}, {@code lives} filter it (null for all). */
    static void matrix(StringBuilder out, String[] styles, double[] sizes, PartChoice[] lives, Render render, double strokeMs, Runnable flush) {
        if (styles == null) styles = styles();
        if (sizes == null) sizes = new double[]{60, 120, 240, 360};
        if (lives == null) lives = new PartChoice[]{PartChoice.NONE, PartChoice.FAST_WET, PartChoice.DRYING};
        out.append(header());
        for (String style : styles) for (PartChoice life : lives) {
            // A preset or a code draws once, with its own Life.
            if (asIs(style) && life != lives[0]) continue;
            ModularBrush b = brush(style, life);
            if (asIs(style)) life = b.part(BrushPart.LIFE).choice;
            // No sizes: the style's own Small, Medium and Large.
            double[] at = sizes;
            if (at.length == 0) { InkWashSizes.Preset[] p = InkWashSizes.Preset.values(); at = new double[p.length]; for (int i = 0; i < p.length; i++) at[i] = ModularSizes.size(b, p[i]); }
            // NaN: the biggest size (to 360 px, in 10s) still drawn live by the quick rule, the worst a live stroke gets.
            if (at.length == 1 && Double.isNaN(at[0])) { double e = 10; for (double z = 360; z >= 10; z -= 10) if (!QuickStroke.quick(ResolvedBrush.of(b, z, 1))) { e = z; break; } at = new double[]{e}; }
            out.append("# ").append(style).append(' ').append(life.name()).append(": ")
                    .append(ModularWords.choiceWords(b, BrushPart.TIP)).append(" · ").append(ModularWords.choiceWords(b, BrushPart.MEDIUM)).append('\n');
            // Warm-up: the JIT meets this brush's branches before the timed stroke.
            run(b, 120, render, 400); run(b, at[at.length - 1], render, 400);
            for (double size : at) {
                Result r = run(b, size, render, strokeMs);
                out.append(row(style, life == PartChoice.FAST_WET ? "FastWet" : life == PartChoice.DRYING ? "Drying" : "None", size, r));
                if (flush != null) flush.run();
            }
        }
    }

    // ---- Erasable (the Erasable spec, "Memory and speed on the Manta") ----

    /**
     * The eraser at its three sizes along the bench's stroke over a page of ink and wash marks, the marks all erasable
     * (as every page before Erasable) and with permanent ink and a wash at .4 (each layer a share): ms a sample and its
     * lift (the wash erased), the best of three. Then the heap a page's shares take: a blank Manta page, then a permanent
     * mark on each layer of its pair.
     */
    static void eraserBench(StringBuilder out, Runnable flush) {
        float[][] s = path(1500);
        out.append(String.format(Locale.ROOT, "%-8s %-9s | %7s %7s %7s | %7s%n", "eraser", "page", "smp_ms", "p95", "max", "lift"));
        for (int mode = 0; mode < 2; mode++) for (InkWashSizes.Preset p : InkWashSizes.Preset.values()) {
            double[] best = null;
            for (int k = 0; k < 4; k++) {
                ToneDocument d = InkWashLayers.blank(W, H);
                for (int layer = 0; layer < 2; layer++) {
                    d.selectLayer(layer);
                    d.begin(mode == 0 ? 1 : layer == InkWashLayers.INK ? 0 : .4);
                    int[] row = new int[W];
                    for (int y = 0; y < H; y++) {
                        for (int x = 0; x < W; x++) row[x] = Math.floorMod(x + y / 3 + 5 * layer, 12) < 4 ? 70 + 60 * layer : -1;
                        d.setTones(0, y, W, 1, row);
                    }
                    d.finish();
                }
                EraseBothStroke e = new EraseBothStroke(d, p);
                double[] per = new double[s.length];
                for (int i = 0; i < s.length; i++) {
                    long t0 = System.nanoTime();
                    e.sample(s[i][0], s[i][1], 1f, s[i][3], s[i][4], 10_000 + (long)s[i][5]);
                    per[i] = (System.nanoTime() - t0) / 1e6;
                }
                long t0 = System.nanoTime();
                e.finish();
                double lift = (System.nanoTime() - t0) / 1e6;
                Arrays.sort(per);
                double sum = 0; for (double v : per) sum += v;
                double[] r = {sum / per.length, per[(int)(per.length * .95)], per[per.length - 1], lift};
                if (k > 0) best = best == null ? r : new double[]{Math.min(best[0], r[0]), Math.min(best[1], r[1]), Math.min(best[2], r[2]), Math.min(best[3], r[3])};
            }
            out.append(String.format(Locale.ROOT, "%-8s %-9s | %7.3f %7.3f %7.3f | %7.1f%n", p, mode == 0 ? "erasable" : "keeps", best[0], best[1], best[2], best[3]));
            if (flush != null) flush.run();
        }
        Runtime rt = Runtime.getRuntime();
        ToneDocument d = InkWashLayers.blank(W, H);
        long blank = used(rt);
        for (int layer = 0; layer < 2; layer++) { d.selectLayer(layer); d.begin(0); d.setTones(100, 100, 1, 1, new int[]{0}); d.finish(); }
        long kept = used(rt);
        out.append(String.format(Locale.ROOT, "heap: a blank page's pair %.1f MB more with a share on each layer (%.1f MB a layer's tones and coverage)%n",
                (kept - blank) / 1048576.0, 2.0 * W * H / 1048576));
        d.selectLayer(0);
    }
    private static long used(Runtime rt) { for (int i = 0; i < 3; i++) { System.gc(); System.runFinalization(); } return rt.totalMemory() - rt.freeMemory(); }

    // ---- Float (the Float spec, "Speed on the Manta") ----

    /** A bath: Sumi and Clear water taps in turn, {@code pairs} of each at each of {@code sites} sites along the bench's stroke, at {@code size} px. */
    static void bath(ToneDocument d, WatercolorSession s, int sites, int pairs, double size) {
        ModularBrush sumi = brush("@Sumi", null), clear = brush("@Clear water", null);
        long t = 0;
        for (int k = 0; k < sites; k++) {
            double u = (k + .5) / sites, x = 260 + 1400 * u, y = 1280 + 600 * Math.sin(2 * Math.PI * u) + 300 * (u - .5);
            for (int i = 0; i < pairs; i++) for (ModularBrush b : new ModularBrush[]{sumi, clear}) {
                s.settleBefore(d, b);
                ModularStroke st = ModularStroke.start(d, b, size, 1, s);
                int hold = b == sumi ? 220 + 40 * (i % 4) : 160;
                for (int ms = 0; ms <= hold; ms += 8) st.sample((float)(x + 3 * Math.sin(i + k)), (float)(y + 3 * Math.cos(i * 1.3)), .5f, Float.NaN, Float.NaN, (t += 8));
                st.finish();
                t += 1000;
            }
        }
    }
    /** The drags timed through a bath: a round Clear water, a 5-line Clear water rake, a Sumi line, a Clear water hake. */
    static final Object[][] FLOAT_DRAGS = {{"round 14", "Clear water", PartChoice.ROUND, 14.0}, {"rake 12", "Clear water", PartChoice.MULTI_LINE, 12.0},
            {"sumi 10", "Sumi", PartChoice.ROUND, 10.0}, {"hake 90", "Clear water", PartChoice.HAKE, 90.0}};
    static ModularBrush floatDrag(Object[] drag) {
        ModularBrush b = brush("@" + drag[1], null).copy();
        if (drag[2] != PartChoice.ROUND) { b.set(BrushPart.TIP, new ModularBrush.Part((PartChoice)drag[2])); if (drag[2] == PartChoice.MULTI_LINE) b.part(BrushPart.TIP).set(PartDial.COUNT, 5); b.normalize(); }
        return b;
    }
    /**
     * Float's costs on baths of three sizes: a drop, the drags' moves per 10 px of pen (live, on this thread), a frame
     * under the pen, the full show, a lift, a drift tick, the print and an Undo; then each drag drawn as the app draws
     * it (quick) in real time over the bath: pen to screen and the wait after lift.
     */
    /** The Wet presets {@link #wetBench} draws (-e names narrows them). */
    static String[] WET_NAMES = {"Wet gouache", "Runny gouache", "Gouache water", "Wet oils", "Loaded oils", "Gouache hake", "Wet brush",
            "Soft wash", "Wet blooms", "Backrun", "Granulating", "Slow bloom", "Pooling wash", "Tilt blend", "Sinking grain",
            "Line and wash", "Wet ink", "Grey wash", "Flat wash", "Slow ink wash"};
    /**
     * Wet paint (the wet paint spec): each Wet preset's stroke in real time over marks on its layer (the table's row: the
     * pen's cost and wait, every pixel laid once and final under it), then its paint's movement after the lift as the
     * drawing screen runs it ({@link WetCanvas#step}: slices sized by WetWorkBudget's idle 5 ms, MonoPaint's frames), each
     * slice's dirty area rendered: how many slices and frames until it rests, the slice's mean, p95 and worst ms with its
     * render, and the whole movement's ms of work. A brush that runs downhill (Runny gouache, Pooling wash, Tilt blend, Sinking grain) on a page tilted half upright.
     */
    static void wetBench(StringBuilder out, double[] sizes, Render render, Runnable flush) {
        boolean marks = MARKS;
        MARKS = true;
        try {
            out.append("wet paint over marks: the pen, then the paint moving after the lift (slices with their render)\n").append(header());
            for (String name : WET_NAMES) {
                ModularBrush b = brush("@" + name, PartChoice.NONE);
                run(b, 120, render, 400);
                for (double size : sizes) {
                    Result r = run(b, size, render, 1500);
                    out.append(row(name, "Wet", size, r));
                    WetCanvas c = r.session.canvas();
                    // A brush that runs downhill (Runny gouache, Pooling wash, ...) on a page tilted half upright.
                    PageTilt tilt = b.part(BrushPart.LIFE).get(PartDial.RUNS, 0) > 0 ? PageTilt.of(0, .5) : PageTilt.LEVEL;
                    java.util.ArrayList<Double> ms = new java.util.ArrayList<>();
                    double work = 0;
                    int frames = c.wet.frame();
                    while (c.moving() && ms.size() < 20000) {
                        long t0 = System.nanoTime();
                        long next = c.step(tilt);
                        int[] dirty = r.page.dirty();
                        if (dirty != null && render != null) render.render(r.page, dirty);
                        r.page.clearDirty();
                        double took = (System.nanoTime() - t0) / 1e6;
                        ms.add(took); work += took;
                        if (next < 0) break;
                    }
                    double[] a = new double[ms.size()];
                    for (int i = 0; i < a.length; i++) a[i] = ms.get(i);
                    Arrays.sort(a);
                    double mean = a.length == 0 ? 0 : work / a.length;
                    out.append(String.format(Locale.ROOT, "  flow: %d slices, %d frames | slice ms mean %.2f p95 %.2f max %.2f | work %.0f ms%n",
                            a.length, c.wet.frame() - frames, mean, a.length == 0 ? 0 : a[(int) (a.length * .95)], a.length == 0 ? 0 : a[a.length - 1], work));
                    if (flush != null) flush.run();
                }
            }
        } finally { MARKS = marks; }
    }

    static void floatBench(StringBuilder out, Render render, Runnable flush) {
        int[][] baths = {{3, 6}, {3, 12}, {6, 16}};
        String[] names = {"small", "medium", "large"};
        out.append(String.format(Locale.ROOT, "%-7s %6s %5s %6s | %6s | %-26s | %6s %6s | %6s %6s | %6s %6s %6s%n", "bath", "points", "rings", "kpx", "drop",
                "moves ms / 10 px (drag)", "frame", "full", "lift", "write", "drift", "print", "undo"));
        for (int pass = 0; pass < 2; pass++) for (int i = 0; i < baths.length; i++) {
            StringBuilder moves = new StringBuilder();
            double frame = 0, frames = 0, full = 0, lift = 0, write = 0;
            for (Object[] drag : FLOAT_DRAGS) {
                ToneDocument d = InkWashLayers.blank(W, H);
                WatercolorSession s = new WatercolorSession();
                bath(d, s, baths[i][0], baths[i][1], 8);
                ModularStroke st = ModularStroke.start(d, floatDrag(drag), (Double)drag[3], 1, s);
                float[][] p = path(1500);
                long ns = 0;
                for (int k = 0; k < p.length; k++) {
                    long t0 = System.nanoTime();
                    st.sample(p[k][0], p[k][1], p[k][2], Float.NaN, Float.NaN, 100_000 + (long)p[k][5]);
                    ns += System.nanoTime() - t0;
                    if (k % 16 == 15) { long f0 = System.nanoTime(); st.floatFrame(); frame += (System.nanoTime() - f0) / 1e6; frames++; }
                }
                double len = 0;
                for (int k = 1; k < p.length; k++) len += Math.hypot(p[k][0] - p[k - 1][0], p[k][1] - p[k - 1][1]);
                moves.append(String.format(Locale.ROOT, "%.2f ", ns / 1e6 / (len / 10)));
                FloatWater w = s.floating();
                long f0 = System.nanoTime();
                w.prepare();
                full += (System.nanoTime() - f0) / 1e6;
                long l0 = System.nanoTime();
                st.finish();
                lift += (System.nanoTime() - l0) / 1e6;
                if (render != null) { int[] dirty = d.dirty(); if (dirty != null) render.render(d, dirty); }
            }
            ToneDocument d = InkWashLayers.blank(W, H);
            WatercolorSession s = new WatercolorSession();
            bath(d, s, baths[i][0], baths[i][1], 8);
            FloatWater w = s.floating();
            float[] box = w.bath.box();
            long kpx = (long)((box[2] - box[0]) * (box[3] - box[1]) / 1000);
            int points = w.bath.points, rings = w.bath.rings.size();
            long t0 = System.nanoTime();
            w.bath.begin();
            w.bath.apply(new FloatBath.Drop(960, 1280, 400, .8f, true, null));
            w.bath.flush();
            double drop = (System.nanoTime() - t0) / 1e6;
            t0 = System.nanoTime();
            d.begin(); w.full(false); w.commit();
            write = (System.nanoTime() - t0) / 1e6;
            s.tilt = () -> PageTilt.of(0, .8);
            t0 = System.nanoTime();
            s.tickDrying(d);
            double drift = (System.nanoTime() - t0) / 1e6;
            t0 = System.nanoTime();
            s.undo(d);
            double undo = (System.nanoTime() - t0) / 1e6;
            t0 = System.nanoTime();
            s.settleNow(d);
            double print = (System.nanoTime() - t0) / 1e6;
            if (pass == 0) continue;
            int n = FLOAT_DRAGS.length;
            out.append(String.format(Locale.ROOT, "%-7s %6d %5d %6d | %6.2f | %-26s | %6.1f %6.1f | %6.1f %6.1f | %6.1f %6.1f %6.1f%n", names[i], points, rings, kpx, drop,
                    moves.toString().trim(), frame / Math.max(1, frames), full / n, lift / n, write, drift, print, undo));
            if (flush != null) flush.run();
        }
        // In real time: the samples come as the pen makes them (the render thread works meanwhile), a frame every
        // 16 ms is presented and rendered; then lift to final, the stroke finished and its page rendered.
        out.append(String.format(Locale.ROOT, "real time (the pen's 1.5 s, then the lift):%n%-7s %-9s | %8s %8s %8s | %8s %8s%n", "bath", "drag", "present", "p95", "max", "lift ms", "render"));
        for (int i = 0; i < baths.length; i++) for (Object[] drag : FLOAT_DRAGS) {
            ToneDocument d = InkWashLayers.blank(W, H);
            WatercolorSession s = new WatercolorSession();
            bath(d, s, baths[i][0], baths[i][1], 8);
            d.clearDirty();
            ModularBrush b = floatDrag(drag);
            s.settleBefore(d, b);
            DrawingStroke st = QuickStroke.start(d, b, (Double)drag[3], 1, s);
            float[][] p = path(1500);
            long start = System.nanoTime();
            java.util.ArrayList<Double> shows = new java.util.ArrayList<>();
            int next = 0;
            while (next < p.length) {
                double now = (System.nanoTime() - start) / 1e6;
                while (next < p.length && p[next][5] <= now) { float[] q = p[next++]; st.sample(q[0], q[1], q[2], Float.NaN, Float.NaN, 100_000 + (long)q[5]); }
                long t0 = System.nanoTime();
                st.present();
                int[] dirty = d.dirty();
                if (dirty != null) { if (render != null) render.render(d, dirty); d.clearDirty(); }
                shows.add((System.nanoTime() - t0) / 1e6);
                try { Thread.sleep(16); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            long l0 = System.nanoTime();
            st.finish();
            double lift = (System.nanoTime() - l0) / 1e6;
            long r0 = System.nanoTime();
            int[] dirty = d.dirty();
            if (dirty != null && render != null) render.render(d, dirty);
            double rend = (System.nanoTime() - r0) / 1e6;
            double[] a = new double[shows.size()]; double sum = 0;
            for (int k = 0; k < a.length; k++) { a[k] = shows.get(k); sum += a[k]; }
            Arrays.sort(a);
            out.append(String.format(Locale.ROOT, "%-7s %-9s | %8.1f %8.1f %8.1f | %8.0f %8.0f%n", names[i], drag[0], sum / a.length, a[(int)(a.length * .95)], a[a.length - 1], lift, rend));
            if (flush != null) flush.run();
        }
        out.append("drags drawn as the app draws them (quick), over each bath, the bench's replay (its samples handed over at once):\n").append(header());
        String quick = QUICK;
        QUICK = "rule";
        try {
            for (int i = 0; i < baths.length; i++) for (Object[] drag : FLOAT_DRAGS) {
                int[] bt = baths[i];
                BEFORE = (d, s) -> bath(d, s, bt[0], bt[1], 8);
                Result r = run(floatDrag(drag), (Double)drag[3], render, 1500);
                out.append(row(names[i], (String)drag[0], (Double)drag[3], r));
                if (flush != null) flush.run();
            }
        } finally { BEFORE = null; QUICK = quick; }
    }

    /**
     * The page each case draws, as a CRC of its layers and one of its tones as shown: the bench's stroke fed eight samples an event, a Drying wash
     * laid settled at finish (no session). Deterministic, so two builds' lists compare: a speed-up that changes no
     * pixel lists the same.
     */
    static int PER = Integer.getInteger("per", 8);
    static String hash(ModularBrush b, double size) {
        ToneDocument d = draw(b, size);
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        ToneDocument.Snapshot snap = d.layerSnapshot();
        for (int i = 0; i < snap.layers.size(); i++) { crc.update(snap.layers.get(i).tones); crc.update(snap.layers.get(i).alpha); }
        java.util.zip.CRC32 shown = new java.util.zip.CRC32();
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) shown.update(d.compositeTone(x, y));
        return String.format(Locale.ROOT, "%08x shown %08x", crc.getValue(), shown.getValue());
    }
    /** The page {@link #hash} reads. */
    static ToneDocument draw(ModularBrush b, double size) {
        float[][] s = path(1500);
        ToneDocument d = InkWashLayers.blank(W, H);
        DrawingStroke stroke = start(d, b, size, null);
        for (int i = 0; i < s.length; i++) {
            float[] q = s[i];
            stroke.sample(q[0], q[1], q[2], q[3], q[4], 10_000 + (long)q[5]);
            if (i % PER == PER - 1) stroke.present();
        }
        stroke.finish();
        return d;
    }
    /** Writes {@link #draw}'s page as it shows (its composite tones) to {@code file}, a binary PGM. */
    static void pgm(ModularBrush b, double size, java.io.File file) throws java.io.IOException {
        ToneDocument d = draw(b, size);
        byte[] px = new byte[W * H];
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) px[y * W + x] = (byte)d.compositeTone(x, y);
        try (java.io.OutputStream out = new java.io.FileOutputStream(file)) {
            out.write(("P5\n" + W + " " + H + "\n255\n").getBytes("US-ASCII"));
            out.write(px);
        }
    }

    /**
     * Gravity: each Slow gravity preset (ToolPresets) laid as a wash across a full page at {@code size} px on a page
     * tilted towards you, then its ticks timed as the drying ticker runs them (the step and the page's write), a stretch
     * of them rocked, each tick's changed area rendered by {@code render} (may be null). Per wash: the ticks, the first
     * tick (the flow is built then), mean, p95 and max ms a tick, and the share over the 40 ms budget.
     */
    static boolean GRAVITY_LEVEL = Boolean.getBoolean("level");
    /** The gravity presets the gravity bench lays. */
    static String[] GRAVITY_NAMES = System.getProperty("names", "Drips,Pooling wash,Tilt blend,Sinking grain").split(",");
    /** A wave {@code wide} px across the page's middle at about 1 px/ms, pressure rising and falling. */
    static float[][] wave(int wide) {
        int n = Math.max(2, wide * HZ / 1000);
        float[][] s = new float[n][6];
        for (int i = 0; i < n; i++) {
            double u = (double)i / (n - 1);
            s[i][0] = (float)(W / 2 - wide / 2 + wide * u); s[i][1] = (float)(H / 2 + 80 * Math.sin(2 * Math.PI * u));
            s[i][2] = (float)(.2 + .6 * Math.pow(Math.sin(Math.PI * u), .6)); s[i][5] = (float)(i * 1000.0 / HZ);
        }
        return s;
    }
    static void gravity(StringBuilder out, double[] sizes, Render render, Runnable flush) { gravity(out, sizes, 0, render, flush); }
    /** As {@link #gravity(StringBuilder, double[], Render, Runnable)}, along a wave {@code wide} px across (0: the bench's S-curve over the page). */
    static void gravity(StringBuilder out, double[] sizes, int wide, Render render, Runnable flush) {
        out.append(String.format(Locale.ROOT, "%-14s %4s %5s | %7s %7s %7s %7s | %6s | %s | %s   (%s)%n", "gravity", "px", "ticks", "first", "mean", "p95", "max", "over40", "render mean/max", "ms a tick",
                wide > 0 ? "a " + wide + " px wave" : "the S-curve over the page"));
        for (String name : GRAVITY_NAMES) for (double size : sizes) {
            ModularBrush b = null;
            for (ToolPresets.Preset p : ToolPresets.withRetired()) if (b == null && p.name.equals(name)) b = ModularCode.decode(p.code);
            for (int pass = 0; pass < 2; pass++) {
                ToneDocument d = InkWashLayers.blank(W, H);
                WatercolorSession session = new WatercolorSession();
                int[] tick = {0};
                // GRAVITY_LEVEL: the same washes on a level page (no flow), to compare.
                session.tilt = GRAVITY_LEVEL ? () -> PageTilt.LEVEL : () -> tick[0] > 60 && tick[0] < 100 ? PageTilt.of(Math.sin(tick[0] / 4.0) * .7, .6) : PageTilt.of(0, .8);
                ModularStroke stroke = ModularStroke.start(d, b, size, 1, session);
                float[][] s = wide > 0 ? wave(wide) : path(1500);
                for (int i = 0; i < s.length; i++) { float[] q = s[i]; stroke.sample(q[0], q[1], q[2], Float.NaN, Float.NaN, 10_000 + (long)q[5]); if (i % 8 == 7) stroke.present(); }
                stroke.finish();
                d.clearDirty();
                java.util.ArrayList<Double> ms = new java.util.ArrayList<>();
                Prof.on = true; Prof.reset();
                double rsum = 0, rmax = 0;
                while (session.settling() && tick[0] < 2000) {
                    long t0 = System.nanoTime();
                    session.tickDrying(d);
                    ms.add((System.nanoTime() - t0) / 1e6);
                    int[] dirty = d.dirty();
                    if (dirty != null) { if (render != null) { double r = render.render(d, dirty) / 1e6; rsum += r; rmax = Math.max(rmax, r); } d.clearDirty(); }
                    tick[0]++;
                }
                // The first pass warms the JIT up.
                if (pass == 0) continue;
                double first = ms.get(0), sum = 0; int over = 0;
                double[] a = new double[ms.size()];
                for (int i = 0; i < a.length; i++) { a[i] = ms.get(i); sum += a[i]; if (a[i] > SettlePacer.TICK_BUDGET_MS) over++; }
                Arrays.sort(a);
                StringBuilder p = new StringBuilder();
                for (int k = Prof.G_RUN; k <= Prof.G_MAKE; k++) p.append(' ').append(Prof.NAMES[k]).append('=').append(String.format(Locale.ROOT, "%.1f", Prof.t[k] / 1e6 / a.length));
                Prof.on = false;
                out.append(String.format(Locale.ROOT, "%-14s %4.0f %5d | %7.1f %7.1f %7.1f %7.1f | %5.0f%% | %.1f / %.1f |%s%n", name, size, a.length, first, sum / a.length,
                        a[(int)(a.length * .95)], a[a.length - 1], 100.0 * over / a.length, rsum / Math.max(1, a.length), rmax, p));
                if (flush != null) flush.run();
            }
        }
    }

    /**
     * The wet indicator's tap on a Slow wash (the S-curve over the page at each size, the gravity presets on a page tilted
     * towards you), three ticks after the lift, as the drawing screen runs it (a {@link SettleWorker}, the ticker's polls
     * slept): laid where it is ({@link WatercolorSession#settleNow}, the tap until 2026-10-29) against run on to its end
     * ({@link WatercolorSession#hurry}, ticked until done), ms; beside them the wash's ticks still to go and how long
     * they take left alone (a tick each 100 ms), and their work on one thread with nothing shown or worked out ahead.
     */
    static void dryerBench(StringBuilder out, String[] names, double[] sizes, Runnable flush) {
        out.append(String.format(Locale.ROOT, "%-14s %4s %5s | %8s %8s | %8s %8s%n", "tap", "px", "ticks", "laid ms", "hurry ms", "alone s", "work ms"));
        for (String name : names) for (double size : sizes) for (int pass = 0; pass < 2; pass++) {
            long[] ms = new long[3];
            int ticks = 0;
            for (int way = 0; way < 3; way++) {
                ToneDocument d = InkWashLayers.blank(W, H);
                WatercolorSession s = new WatercolorSession();
                if (way < 2) s.worker = new SettleWorker();
                s.tilt = name.startsWith("@") ? () -> PageTilt.of(0, .8) : () -> PageTilt.LEVEL;
                ModularStroke stroke = ModularStroke.start(d, brush(name, PartChoice.DRYING), size, 1, s);
                float[][] p = path(1500);
                for (int i = 0; i < p.length; i++) { float[] q = p[i]; stroke.sample(q[0], q[1], q[2], Float.NaN, Float.NaN, 10_000 + (long)q[5]); if (i % 8 == 7) stroke.present(); }
                stroke.finish();
                int[] shown = {0};
                if (way < 2) for (long next; shown[0] < 3 && (next = s.tick(d, () -> { shown[0]++; d.clearDirty(); })) >= 0; ) sleep(next);
                else for (int i = 0; i < 3; i++) s.tickDrying(d);
                long t0 = System.nanoTime();
                if (way == 0) s.settleNow(d);
                else if (way == 1) { if (s.hurry(d)) for (long next; (next = s.tick(d, d::clearDirty)) >= 0; ) sleep(next); }
                else while (s.tickDrying(d)) ticks++;
                ms[way] = (System.nanoTime() - t0) / 1_000_000;
            }
            if (pass == 0) continue;
            out.append(String.format(Locale.ROOT, "%-14s %4.0f %5d | %8d %8d | %8.1f %8d%s%n", name, size, ticks, ms[0], ms[1], ticks / 10.0, ms[2],
                    ms[1] >= WatercolorSession.HURRY_MS ? "  (capped)" : ""));
            if (flush != null) flush.run();
        }
    }
    private static void sleep(long ms) { if (ms > 0) try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }

    /**
     * Desktop: java ... ModularPerfBench [styles,comma] [sizes,comma] [lives,comma: none,wet,dry] [strokeMs];
     * java ... ModularPerfBench gravity [sizes] times gravity's ticks;
     * java ... ModularPerfBench dryer [sizes] [names] times the wet indicator's tap (dryerBench);
     * java ... ModularPerfBench hashes [styles] [sizes] [lives] prints each case's {@link #hash};
     * java ... ModularPerfBench eraser times the eraser over pages with and without shares (Erasable);
     * java ... ModularPerfBench wet [sizes] the Wet presets' pen and their paint's movement after (wetBench).
     * -Derasable=1 draws every brush all erasable (as before Erasable); -Dmarks=true -DmarksErasable=0 lays the marks permanent;
     * -Dtrail=false shows quick strokes' previews until lift ({@link QuickStroke#trail}); -DdrawOnce=true draws Pastels and
     * Dry brush once a pixel ({@link QuickStroke#drawOnce}, the wet paint spec's experiment).
     */
    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        if (System.getProperty("lattice") != null) ModularStroke.latticePx = Double.parseDouble(System.getProperty("lattice"));
        if (System.getProperty("composeMs") != null) QuickStroke.composeMs = Long.parseLong(System.getProperty("composeMs"));
        if (System.getProperty("trail") != null) QuickStroke.trail = Boolean.parseBoolean(System.getProperty("trail"));
        if (System.getProperty("drawOnce") != null) QuickStroke.drawOnce = Boolean.parseBoolean(System.getProperty("drawOnce"));
        if (args.length > 0 && args[0].equals("gravity")) {
            String[] q = args.length > 1 ? args[1].split(",") : new String[]{"60", "120", "240"};
            double[] sz = new double[q.length];
            for (int i = 0; i < q.length; i++) sz[i] = Double.parseDouble(q[i]);
            StringBuilder out = new StringBuilder();
            gravity(out, sz, args.length > 2 ? Integer.parseInt(args[2]) : 0, null, () -> { System.out.print(out); out.setLength(0); });
            System.out.print(out);
            return;
        }
        if (args.length > 0 && args[0].equals("dryer")) {
            // dryer [sizes] [names]: the wet indicator's tap, laid where it is against run on to its end (dryerBench).
            String[] q = (args.length > 1 ? args[1] : "60,120,240").split(",");
            String[] names = (args.length > 2 ? args[2] : "Watercolor,Ink wash,@Drips").split(",");
            double[] sz = new double[q.length];
            for (int i = 0; i < q.length; i++) sz[i] = Double.parseDouble(q[i]);
            StringBuilder out = new StringBuilder();
            dryerBench(out, names, sz, () -> { System.out.print(out); out.setLength(0); });
            System.out.print(out);
            return;
        }
        if (args.length > 0 && args[0].equals("eraser")) {
            StringBuilder out = new StringBuilder();
            eraserBench(out, () -> { System.out.print(out); out.setLength(0); });
            System.out.print(out);
            return;
        }
        if (args.length > 0 && args[0].equals("wet")) {
            // wet [sizes] [names]: the Wet presets' pen and their paint's movement after (wetBench).
            String[] q = (args.length > 1 ? args[1] : "60,120,240").split(",");
            if (args.length > 2) WET_NAMES = args[2].split(",");
            double[] sz = new double[q.length];
            for (int i = 0; i < q.length; i++) sz[i] = Double.parseDouble(q[i]);
            StringBuilder out = new StringBuilder();
            wetBench(out, sz, null, () -> { System.out.print(out); out.setLength(0); });
            System.out.print(out);
            return;
        }
        if (args.length > 0 && args[0].equals("float")) {
            StringBuilder out = new StringBuilder();
            floatBench(out, null, () -> { System.out.print(out); out.setLength(0); });
            System.out.print(out);
            return;
        }
        if (args.length > 0 && (args[0].equals("hashes") || args[0].equals("pgm"))) {
            // hashes [styles] [sizes] [lives], or pgm <folder> [styles] [sizes] [lives]: each case's page as a PGM.
            boolean pgm = args[0].equals("pgm");
            int a = pgm ? 2 : 1;
            String[] st = args.length > a && !args[a].equals("all") ? args[a].split(",") : styles();
            double[] sz = {16, 60, 120, 240, 360};
            if (args.length > a + 1 && !args[a + 1].equals("all")) { String[] q = args[a + 1].split(","); sz = new double[q.length]; for (int i = 0; i < q.length; i++) sz[i] = Double.parseDouble(q[i]); }
            PartChoice[] lv = args.length > a + 2 && !args[a + 2].equals("all") ? lives(args[a + 2]) : new PartChoice[]{PartChoice.NONE, PartChoice.FAST_WET, PartChoice.DRYING};
            for (String style : st) for (PartChoice life : lv) for (double size : sz) {
                if (!pgm) { System.out.printf(Locale.ROOT, "%-13s %-9s %4.0f %s%n", style, life.name(), size, hash(brush(style, life), size)); continue; }
                java.io.File f = new java.io.File(args[1], (style + "_" + life.name() + "_" + (int)size + ".pgm").replace(' ', '-'));
                try { pgm(brush(style, life), size, f); } catch (java.io.IOException e) { throw new RuntimeException(e); }
                System.out.println(f);
            }
            return;
        }
        StringBuilder out = new StringBuilder();
        String[] st = args.length > 0 && !args[0].equals("all") ? args[0].split(",") : null;
        double[] sz = null;
        if (args.length > 1 && args[1].equals("presets")) sz = new double[0];
        else if (args.length > 1 && args[1].equals("edge")) sz = new double[]{Double.NaN};
        else if (args.length > 1 && !args[1].equals("all")) { String[] q = args[1].split(","); sz = new double[q.length]; for (int i = 0; i < q.length; i++) sz[i] = Double.parseDouble(q[i]); }
        PartChoice[] lv = args.length > 2 && !args[2].equals("all") ? lives(args[2]) : null;
        double ms = args.length > 3 ? Double.parseDouble(args[3]) : 1500;
        matrix(out, st, sz, lv, null, ms, () -> { System.out.print(out); out.setLength(0); });
        System.out.print(out);
    }
    static PartChoice[] lives(String spec) {
        String[] q = spec.split(",");
        PartChoice[] l = new PartChoice[q.length];
        for (int i = 0; i < q.length; i++) l[i] = q[i].startsWith("n") ? PartChoice.NONE : q[i].startsWith("w") ? PartChoice.FAST_WET : PartChoice.DRYING;
        return l;
    }
    private ModularPerfBench() {}
}
