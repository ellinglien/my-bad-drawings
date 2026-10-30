package io.github.mpdairy.monopaint;

import java.util.Arrays;

/**
 * Quick strokes ({@link QuickStroke}): which strokes go quick, the plain preview while the pen is down, and the true
 * stroke rendered from the samples at lift, which must end exactly as drawing the same samples live does.
 */
public final class QuickStrokeChecks {
    static final int W = 900, H = 700;

    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        rule(); sameAsLive(); slices(); undo(); preview(false); preview(true); wetWhileRendering(); dryingSettlesAlike(); liveLiftOffThread(); tiltReplayed(); tap(); previewIsCheap(); placedPreview(); retapersWait();
        System.out.println("PASS: big strokes past what keeps up live go quick (small ones and Watercolor at any size draw live, unchanged); the preview lays a flat footprint along the pen on the stroke's layer only and is gone at lift (with the true stroke written behind the pen, kept until the render is written); the true stroke rendered from the samples on its own thread while drawing, finished after lift or at once, ends with every layer as live (Fast-wet: the tones as live, the layers as live composed once), page tilt and all, as one undo step; the page reads as wet while it renders and a Drying wash then settles as after a live stroke; a live Drying stroke's lift worked off the pen's thread ends and settles as finished at once; the preview costs a fraction of drawing live; placed lines (a split nib's) preview as their own nibs, the split's gap and all, nearly every dot as the render's; a render's re-tapers wait for its composes and it ends byte for byte as re-tapering at each sample the pen's time calls for, composing often or never, the trail on or off");
    }

    // ---- the stroke ----

    /** The checks' stroke: an S across the page, a sample every 2 ms (480 Hz), pressure and tilt varying. */
    static float[][] path() {
        int n = 420;
        float[][] s = new float[n][];
        for (int i = 0; i < n; i++) {
            double u = (double)i / (n - 1);
            s[i] = new float[]{(float)(120 + 660 * u), (float)(350 + 200 * Math.sin(2 * Math.PI * u)), (float)(.25 + .6 * Math.sin(Math.PI * u)),
                    (float)(20 * Math.cos(3 * u)), 30, 10_000 + 2 * i};
        }
        return s;
    }
    /** Batches as DrawingPad's: a few samples, then a present. */
    static final int[] BATCH = {1, 3, 8, 2, 5, 1, 13};
    /** Feeds {@code s} the path in {@link #BATCH}es, each followed by a present (none when {@code once}); between batches {@code between} runs. */
    static void draw(DrawingStroke s, float[][] p, boolean once, Runnable between) {
        for (int i = 0, b = 0; i < p.length; b++) {
            for (int k = 0; k < BATCH[b % BATCH.length] && i < p.length; k++, i++) s.sample(p[i][0], p[i][1], p[i][2], p[i][3], p[i][4], (long)p[i][5]);
            if (!once) s.present();
            if (between != null) between.run();
        }
    }
    /** {@code style}'s brush as the timing bench rolls it (ModularPerfBench.brush), with {@code life}. */
    static ModularBrush brush(String style, PartChoice life) {
        ModularStyle st = ModularStyle.named(style);
        ModularBrush b = st.roll(new java.util.Random(100 + st.index));
        ModularBrush.Part p = new ModularBrush.Part(life);
        if (life == PartChoice.DRYING) p.set(PartDial.SETTLE, .5);
        b.set(BrushPart.LIFE, p);
        return b;
    }
    static String[] styles() { java.util.List<ModularStyle> all = ModularStyle.all(); String[] s = new String[all.size()]; for (int i = 0; i < s.length; i++) s[i] = all.get(i).name; return s; }
    static ToneDocument live(ModularBrush b, double size, boolean once, WatercolorSession session) {
        ToneDocument d = InkWashLayers.blank(W, H);
        ModularStroke s = ModularStroke.start(d, b, size, 1, session);
        draw(s, path(), once, null);
        s.finish();
        return d;
    }
    static ToneDocument quick(ModularBrush b, double size, WatercolorSession session) {
        ToneDocument d = InkWashLayers.blank(W, H);
        QuickStroke s = QuickStroke.quickly(d, b, size, 1, session);
        draw(s, path(), false, null);
        s.finish();
        return d;
    }
    static byte[][] layers(ToneDocument d) { return ModularStrokeChecks.layers(d); }
    static boolean same(ToneDocument a, ToneDocument b) { return ModularStrokeChecks.same(layers(a), layers(b)); }
    static boolean sameShown(ToneDocument a, ToneDocument b) {
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) if (a.compositeTone(x, y) != b.compositeTone(x, y)) return false;
        return true;
    }

    // ---- the rule ----

    static void rule() {
        PartChoice[] lives = {PartChoice.NONE, PartChoice.FAST_WET, PartChoice.DRYING};
        for (String style : styles()) for (PartChoice life : lives) {
            ModularBrush b = brush(style, life);
            check(!QuickStroke.quick(ResolvedBrush.of(b, 16, 1)), style + " " + life + " at 16 px draws live");
            boolean was = false;
            for (int size = 10; size <= 360; size += 10) {
                boolean q = QuickStroke.quick(ResolvedBrush.of(b, size, 1));
                check(!was || q, style + " " + life + ": once quick, bigger stays quick (" + size + " px)");
                was = q;
            }
        }
        // The panel: past 3 mm every mark that changes pixels again and again under the pen goes quick, whatever it is.
        check(!QuickStroke.quick(ResolvedBrush.of(brush("Watercolor", PartChoice.DRYING), 360, 1)), "Watercolor in Slow (a flat preview written once) draws live even at 360 px");
        for (String style : new String[]{"Watercolor", "Oils", "Gouache", "Ink wash", "Hake"}) for (PartChoice life : new PartChoice[]{PartChoice.NONE, PartChoice.FAST_WET})
            check(QuickStroke.quick(ResolvedBrush.of(brush(style, life), 40, 1)) && !QuickStroke.quick(ResolvedBrush.of(brush(style, life), 30, 1)), style + " " + life + " goes quick past 36 px, not under it");
        check(QuickStroke.quick(ResolvedBrush.of(brush("Charcoal", PartChoice.NONE), 360, 1)), "Charcoal at 360 px goes quick");
        check(QuickStroke.quick(ResolvedBrush.of(brush("Pencil", PartChoice.NONE), 240, 1)), "Pencil at 240 px goes quick");
        check(QuickStroke.quick(ResolvedBrush.of(brush("Anything goes", PartChoice.FAST_WET), 120, 1)), "a Fast-wet stick that re-tapers at 120 px goes quick");
        // Pen-down starts the stroke the rule says, on the brush's layer.
        ToneDocument d = InkWashLayers.blank(W, H);
        DrawingStroke live = QuickStroke.start(d, brush("Watercolor", PartChoice.DRYING), 360, 1, null);
        check(live instanceof ModularStroke && d.activeLayer() == InkWashLayers.WASH, "A stroke under the threshold is the live stroke, unchanged");
        live.finish();
        DrawingStroke quick = QuickStroke.start(d, brush("Charcoal", PartChoice.NONE), 360, 1, null);
        check(quick instanceof QuickStroke && d.activeLayer() == InkWashLayers.INK, "one over it is quick, on its layer");
        quick.finish();
    }

    // ---- the final page ----

    /** Every style, Instant, Fast-wet and Drying: the true stroke rendered at lift ends as the same samples drawn live. */
    static void sameAsLive() {
        int n = 0;
        for (String style : styles()) for (PartChoice life : new PartChoice[]{PartChoice.NONE, PartChoice.FAST_WET, PartChoice.DRYING}) {
            ModularBrush b = brush(style, life);
            for (double size : new double[]{60, 200}) {
                ToneDocument q = quick(b, size, null), batched = live(b, size, false, null);
                String what = style + " " + life + " " + size + " px";
                // Composed ahead after every batch the render thread takes (rather than every 100 ms): the same page.
                long every = QuickStroke.composeMs;
                QuickStroke.composeMs = 0;
                ToneDocument ahead = quick(b, size, null);
                QuickStroke.composeMs = every;
                check(same(q, ahead), what + ": every layer the same however often it composed ahead");
                if (life != PartChoice.FAST_WET) check(same(q, batched), what + ": every layer as drawn live");
                else {
                    // Live, a Fast-wet layer keeps the most coverage any compose gave a pixel, so its bytes depend on how
                    // the input was batched; the tones shown don't. Quick composes once: as live, composed once.
                    check(sameShown(q, batched), what + ": the page shows as drawn live");
                    check(same(q, live(b, size, true, null)), what + ": every layer as drawn live and composed once");
                }
                n++;
            }
        }
        check(n == 2 * 3 * styles().length, "every style checked: " + n);
    }

    /**
     * The true stroke renders on its own thread as the samples come, while the pen is down; after lift the host looks
     * in without waiting until it is done, and it ends as finished at once.
     */
    static void slices() {
        for (String style : new String[]{"Ink wash", "Charcoal", "Oils", "Hake"}) for (PartChoice life : new PartChoice[]{PartChoice.NONE, PartChoice.FAST_WET, PartChoice.DRYING}) {
            ModularBrush b = brush(style, life);
            ToneDocument d = InkWashLayers.blank(W, H);
            QuickStroke s = QuickStroke.quickly(d, b, 200, 1, null);
            float[][] p = path();
            draw(s, p, false, null);
            check(!s.develop(0), "Before lift there is nothing to finish");
            // The pen held still a moment: the thread catches up with it.
            for (long until = System.nanoTime() + 10_000_000_000L; s.rendered() < p.length && System.nanoTime() < until; ) Thread.yield();
            check(s.rendered() == p.length && s.samples() == p.length, style + " " + life + ": drawn on its own thread while the pen is down: " + s.rendered());
            check(s.lift() && s.takenBeforeLift == p.length, "lifting leaves the finish to do");
            int looks = 0;
            while (s.develop(0)) looks++;
            check(!s.develop(0) && !s.lift(), "and once done, nothing: " + looks + " looks");
            check(s.finish() && same(d, quick(b, 200, null)), style + " " + life + ": ends as finished at once");
        }
    }

    /** One undo takes back the whole stroke, preview and render alike; redo brings back the render. */
    static void undo() {
        ToneDocument d = InkWashLayers.blank(W, H), blank = InkWashLayers.blank(W, H);
        QuickStroke s = QuickStroke.quickly(d, brush("Ink wash", PartChoice.FAST_WET), 200, 1, null);
        draw(s, path(), false, null);
        check(s.finish(), "The stroke changed the page");
        byte[][] drawn = layers(d);
        check(d.undo() && !d.canUndo(), "One undo step");
        check(ModularStrokeChecks.same(layers(d), layers(blank)), "takes it all back");
        check(d.redo() && ModularStrokeChecks.same(layers(d), drawn), "redo brings the render back");
    }

    /**
     * The preview: along the pen, on the stroke's layer only, nothing far away; without the trail ({@link
     * QuickStroke#trail}) gone at lift until the render is done; with it (these brushes are Instant) nothing is taken
     * back at lift, the page as it showed until the render is written.
     */
    static void preview(boolean trail) {
        boolean was = QuickStroke.trail;
        QuickStroke.trail = trail;
        try { preview(); } finally { QuickStroke.trail = was; }
    }
    private static void preview() {
        for (String style : new String[]{"Watercolor", "Charcoal", "Brush pen", "Oils"}) {
            ModularBrush b = brush(style, PartChoice.NONE);
            ToneDocument d = InkWashLayers.blank(W, H), blank = InkWashLayers.blank(W, H);
            QuickStroke s = QuickStroke.quickly(d, b, 120, 1, null);
            float[][] p = path();
            float[][] half = Arrays.copyOf(p, p.length / 2);
            draw(s, half, false, null);
            int layer = d.activeLayer(), other = layer == InkWashLayers.INK ? InkWashLayers.WASH : InkWashLayers.INK;
            int marked = 0, k0 = half.length / 4;
            for (int k = k0; k < half.length - 20; k += 10) {
                // A dither's dots are on even pixels: look at the 2 × 2 block round each sample.
                int x = (int)half[k][0] & ~1, y = (int)half[k][1] & ~1, darkest = 255;
                for (int j = 0; j < 2; j++) for (int i = 0; i < 2; i++) darkest = Math.min(darkest, d.compositeTone(x + i, y + j));
                if (darkest < 250) marked++;
            }
            check(marked == (half.length - 20 - k0 + 9) / 10, style + ": the preview marks the page along the pen: " + marked);
            check(d.compositeTone(10, 10) == 255 && d.compositeTone(W - 10, H - 10) == 255, style + ": and nothing far from it");
            check(!d.layerDarkness(other, 0, 0, W, H, new int[W * H]), style + ": on the stroke's layer only");
            byte[][] shown = layers(d);
            check(s.lift(), "lift");
            if (QuickStroke.trail) check(ModularStrokeChecks.same(layers(d), shown), style + ": at lift nothing is taken back, the page as it showed");
            else check(ModularStrokeChecks.same(layers(d), layers(blank)), style + ": at lift the preview is gone, the page as the stroke found it");
            s.finish();
            check(!ModularStrokeChecks.same(layers(d), layers(blank)), style + ": then the render shows");
        }
    }

    /** The page reads as wet (the wet indicator) from lift until the render is done; told each time. */
    static void wetWhileRendering() {
        WatercolorSession session = new WatercolorSession();
        int[] told = {0};
        session.wetChanged = () -> told[0]++;
        ToneDocument d = InkWashLayers.blank(W, H);
        QuickStroke s = QuickStroke.quickly(d, brush("Charcoal", PartChoice.NONE), 200, 1, session);
        draw(s, path(), false, null);
        check(!session.wet() && told[0] == 0, "Not wet while the pen is down");
        s.lift();
        check(session.wet() && told[0] == 1, "wet from lift");
        while (s.develop(0)) check(session.wet(), "while it renders");
        check(!session.wet() && told[0] == 2, "dry once it is done");
    }

    /** A Drying wash from a quick stroke settles after lift, tick by tick, ending as one from a live stroke. */
    static void dryingSettlesAlike() {
        ModularBrush b = brush("Ink wash", PartChoice.DRYING);
        WatercolorSession liveSession = new WatercolorSession(), quickSession = new WatercolorSession();
        ToneDocument a = live(b, 200, false, liveSession), q = quick(b, 200, quickSession);
        check(liveSession.settling() && quickSession.settling() && quickSession.wet(), "Both keep settling after lift");
        check(same(a, q), "laid alike at lift");
        int ticks = 0;
        while (liveSession.tickDrying(a)) ticks++;
        int quickTicks = 0;
        while (quickSession.tickDrying(q)) quickTicks++;
        check(ticks > 3 && ticks == quickTicks && same(a, q) && !quickSession.wet(), "and settle alike: " + ticks + " ticks");
    }

    /**
     * A live Drying stroke lifts off the pen's thread ({@link LiftingStroke}): its line laid again, body settled and
     * composed there, then written; the page (and the wash settling after) as finished at once. Other lives, and
     * strokes without a session (samples), finish at once.
     */
    static void liveLiftOffThread() {
        for (String style : new String[]{"Watercolor", "Ink wash", "Gouache", "Oils", "Charcoal", "Brush pen"}) for (double size : new double[]{60, 200}) {
            ModularBrush b = brush(style, PartChoice.DRYING);
            WatercolorSession once = new WatercolorSession(), later = new WatercolorSession();
            ToneDocument a = live(b, size, false, once);
            ToneDocument d = InkWashLayers.blank(W, H);
            ModularStroke s = ModularStroke.start(d, b, size, 1, later);
            draw(s, path(), false, null);
            String what = style + " Drying " + size + " px";
            check(s.developsAfterLift() && s.lift() && later.wet(), what + ": lifts off the pen's thread, the page wet meanwhile");
            int looks = 0;
            while (s.develop(0)) looks++;
            check(s.finish() && !later.onlyRendering(), what + ": then finishes (" + looks + " looks)");
            check(same(a, d), what + ": every layer as finished at once");
            int ticks = 0, laterTicks = 0;
            while (once.tickDrying(a)) ticks++;
            while (later.tickDrying(d)) laterTicks++;
            check(ticks == laterTicks && same(a, d), what + ": and settles alike: " + ticks + " ticks");
        }
        ModularStroke wet = ModularStroke.start(InkWashLayers.blank(W, H), brush("Ink wash", PartChoice.FAST_WET), 200, 1, new WatercolorSession());
        draw(wet, path(), false, null);
        check(!wet.developsAfterLift() && !wet.lift(), "A Fast-wet stroke finishes at once");
        wet.finish();
        ModularStroke sample = ModularStroke.start(InkWashLayers.blank(W, H), brush("Ink wash", PartChoice.DRYING), 200, 1, null);
        draw(sample, path(), false, null);
        check(!sample.developsAfterLift(), "and so does one without a session");
        sample.finish();
    }

    /** A brush that reads the page's tilt (the hand's page tilt, Quick's window, charcoal's dust) renders with the tilt each sample was drawn at. */
    static void tiltReplayed() {
        ModularBrush wash = brush("Watercolor", PartChoice.FAST_WET);
        wash.part(BrushPart.LIFE).set(PartDial.RUNS, .8); wash.part(BrushPart.LIFE).set(PartDial.G_POOL, .6);
        wash.part(BrushPart.HAND).set(PartDial.GS, 1);
        ModularBrush stick = brush("Charcoal", PartChoice.NONE);
        stick.part(BrushPart.MEDIUM).set(PartDial.DUST, 1);
        for (ModularBrush b : new ModularBrush[]{wash, stick}) {
            check(ModularStroke.readsTilt(ResolvedBrush.of(b, 200, 1)), "The brush reads the tilt");
            ToneDocument[] pages = new ToneDocument[2];
            for (int pass = 0; pass < 2; pass++) {
                int[] at = {0};
                WatercolorSession session = new WatercolorSession();
                // The tablet rocks as the stroke goes: the tilt is the sample's.
                session.tilt = () -> PageTilt.of(.7 * Math.sin(at[0] / 40.0), .6);
                ToneDocument d = InkWashLayers.blank(W, H);
                DrawingStroke s = pass == 0 ? ModularStroke.start(d, b, 200, 1, session) : QuickStroke.quickly(d, b, 200, 1, session);
                float[][] p = path();
                for (int i = 0; i < p.length; i++) { at[0] = i; s.sample(p[i][0], p[i][1], p[i][2], p[i][3], p[i][4], (long)p[i][5]); if (pass == 0 && i % 6 == 5) s.present(); }
                at[0] = p.length;
                // The live one's presents: shown alike; layers alike composed once.
                s.finish();
                pages[pass] = d;
            }
            check(sameShown(pages[0], pages[1]), "rendered with the tilt each sample was drawn at, shown as live");
        }
    }

    /** A tap (one sample) renders the live stroke's dot. */
    static void tap() {
        for (String style : new String[]{"Ink wash", "Charcoal", "Oils"}) {
            ModularBrush b = brush(style, PartChoice.NONE);
            ToneDocument a = InkWashLayers.blank(W, H), q = InkWashLayers.blank(W, H);
            DrawingStroke live = ModularStroke.start(a, b, 200, 1, null), quick = QuickStroke.quickly(q, b, 200, 1, null);
            for (DrawingStroke s : new DrawingStroke[]{live, quick}) { s.sample(400, 300, .5f, 0, 0, 10_000); s.present(); s.finish(); }
            // A hake's tap lays nothing: its head needs a few px to find its angle.
            check(same(a, q) && (style.equals("Oils") || a.compositeTone(400, 300) < 255), style + ": a tap renders as live");
        }
    }

    /** The preview costs a fraction of drawing live, for the big strokes the rule makes quick. */
    static void previewIsCheap() {
        float[][] p = path();
        for (String style : new String[]{"Charcoal", "Oils", "Ink wash"}) {
            ModularBrush b = brush(style, PartChoice.FAST_WET);
            long liveNs = Long.MAX_VALUE, previewNs = Long.MAX_VALUE;
            for (int rep = 0; rep < 4; rep++) {
                ToneDocument d = InkWashLayers.blank(W, H);
                ModularStroke live = ModularStroke.start(d, b, 300, 1, null);
                long t0 = System.nanoTime();
                draw(live, p, false, null);
                liveNs = Math.min(liveNs, System.nanoTime() - t0);
                live.finish();
                ToneDocument e = InkWashLayers.blank(W, H);
                QuickStroke quick = QuickStroke.quickly(e, b, 300, 1, null);
                t0 = System.nanoTime();
                draw(quick, p, false, null);
                previewNs = Math.min(previewNs, System.nanoTime() - t0);
                quick.finish();
            }
            // A stick's preview reads its grain and the paper (QuickStroke's GRAIN, the owner, 2026-10-22: "charcoal preview
            // is also darker than the resulting mark"): under half; the flat tones, under a third.
            int part = style.equals("Charcoal") ? 2 : 3;
            Timing.limit(previewNs < liveNs / part, style + ": the preview costs under 1/" + part + " of drawing live: " + previewNs / 1000 + " µs against " + liveNs / 1000);
        }
    }

    /**
     * Placed lines (speed plan F6): the preview sweeps each line's own nib where it is placed, a split's gap left out, so
     * nearly every dot it shows is the render's (the hull of the placed body it swept before showed 28–36% of them).
     */
    static void placedPreview() {
        boolean was = QuickStroke.trail;
        QuickStroke.trail = false;
        try {
            for (String name : new String[]{"Split nib", "Copperplate", "Ballpoint"}) {
                ModularBrush b = null;
                for (ToolPresets.Preset p : ToolPresets.all()) if (p.name.equals(name)) b = ModularCode.decode(p.code);
                ToneDocument d = InkWashLayers.blank(W, H);
                QuickStroke s = QuickStroke.quickly(d, b, 120, 1, null);
                draw(s, path(), false, null);
                boolean[] shown = new boolean[W * H];
                for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) shown[y * W + x] = DotPattern.pixel(d.compositeTone(x, y), x, y) != 0xffffffff;
                s.finish();
                int touched = 0, same = 0;
                for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
                    boolean f = DotPattern.pixel(d.compositeTone(x, y), x, y) != 0xffffffff, p = shown[y * W + x];
                    if (!f && !p) continue;
                    touched++;
                    if (f == p) same++;
                }
                check(touched > 0 && same >= .85 * touched, name + ": the preview shows the render's dots, " + same + " of " + touched);
            }
        } finally { QuickStroke.trail = was; }
    }

    /**
     * Speed plan F7: a render's Fast-wet line (a line alone, no stick's dust) re-tapers when it is next composed, not at
     * each sample the pen's time calls for one, and ends byte for byte as before, every layer: composing after every batch
     * or never before lift, with the trail or without.
     */
    static void retapersWait() {
        long composeMs = QuickStroke.composeMs;
        boolean trail = QuickStroke.trail;
        try {
            for (String name : new String[]{"B pencil", "Split nib", "Fude pen", "Chisel brush", "Toothy line"}) {
                ModularBrush b = null;
                for (ToolPresets.Preset p : ToolPresets.all()) if (p.name.equals(name)) b = ModularCode.decode(p.code);
                for (double size : new double[]{60, 150}) for (long ms : new long[]{0, 100_000}) for (boolean t : new boolean[]{false, true}) {
                    QuickStroke.composeMs = ms; QuickStroke.trail = t;
                    byte[][][] ends = new byte[2][][];
                    for (int defer = 0; defer < 2; defer++) {
                        ModularStroke.deferRetapers = defer == 1;
                        ToneDocument d = InkWashLayers.blank(W, H);
                        QuickStroke s = QuickStroke.quickly(d, b.copy(), size, 1, null);
                        draw(s, path(), false, () -> { for (int k = 0; k < 20 && s.rendered() < s.samples(); k++) Thread.yield(); });
                        s.finish();
                        ends[defer] = TrailChecks.all(d);
                    }
                    check(TrailChecks.same(ends[0], ends[1]), name + " at " + size + " px, composing " + (ms == 0 ? "every batch" : "never") + (t ? ", trail" : "") + ": its re-tapers put off to its composes end as before");
                }
            }
        } finally { QuickStroke.composeMs = composeMs; QuickStroke.trail = trail; ModularStroke.deferRetapers = true; }
    }

    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
