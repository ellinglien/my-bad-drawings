package io.github.mpdairy.monopaint;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Mirrors ({@link Mirrors}, Mischief Lab 2's section 6): the group the mirrors make, the snapping, the cap, the MIRR
 * section, Undo; Copy Strokes' copies ({@link MirrorStroke}: a kaleidoscope's stroke lays six, Undo takes all six
 * back; laid as the stroke's change reflected, {@link ToneDocument#layAgain}, exact on the pixel grid and within a
 * stated tolerance turned; how long they take at lift, bounded); Show Guides; Real Mirror's view ({@link Mirrors.View}: the reflection shown, the covered marks back when cleared, Keep
 * Reflection laid with Undo, three over an existing mark) and its strokes (folded into the kept side from any side and
 * through the mirror, one Undo step; Flip Side); what the canvas shows ({@link MirrorShow}). With
 * {@code picture <file>} it draws a kaleidoscope on a page and writes its PNG.
 */
public final class MirrorChecks {
    static final int W = 600, H = 600;

    public static void main(String[] args) throws Exception {
        Assets.install(Assets.folder(Assets.FOLDER));
        if (args.length == 2 && args[0].equals("picture")) { picture(args[1]); return; }
        if (args.length == 2 && args[0].equals("write")) { writeMirrored(args[1]); return; }
        group(); snapping(); cap(); section(); undo(); copies(); reflected(); real(); realKaleidoscope(); realStroke(); flipSide(); show(); guides(); tilt();
        String timings = timings();
        System.out.println("PASS: mirrors: one mirror makes 2 maps, two at 90° 4, the kaleidoscope's three at 60° 6, two at 45° 8, parallel or non-dividing ones stop at 24 ('copies max'); a stroke snaps to 15° within 3° and through the centre or a crossing within 12 prototype px (37.5 page px), under 75 px is too short; a fifth replaces the oldest; the MIRR section round trips (style, side and Show Guides too; guides shown write it as before), a later version is skipped, damage refused, a page without mirrors writes as before; Undo takes back placing, a kaleidoscope and clearing; a kaleidoscope's stroke lays six copies, drawn again or reflected, the copies culled off the page, one Undo takes all six; the default pens, pencil and washes lay their copies as the stroke's change reflected (Gouache and a settling wash draw them again): on the pixel grid the stroke reflected pixel for pixel, as drawn again for brushes without the page's grain, the eraser leaving of it what it leaves of the stroke within a grey; turned copies within " + (int) TURNED_PEN + " greys on average of copies drawn again for a fine pen, " + (int) TURNED_WASH + " for a wash; over other marks within a grey of a copy drawn again; drawn again with masking fluid on the page; saved and read back as laid; rendered as their boxes apart, a tenth of one round them all for a short stroke; Real Mirror shows the kept side's reflection with the page's layers untouched, the covered marks come back on clear, Keep Reflection lays it with one Undo, three over an existing mark show it six times; a stroke on the covered side lands at its reflection on the kept side, one through the mirror draws on unbroken (a sample at the crossing, pressure, time and tilt continuous), a kaleidoscope folds every cell, each one Undo step, a fold past the cap that jumps goes on as more strokes; Flip Side keeps the other side, round a kaleidoscope; the guides and live copies show on the canvas only, a pen's frame every copy; Show Guides off draws no dash, the mirrors acting, no Undo step, kept by Undo and Remove Mirrors; tilt mirrors with the page. Timings (this Mac): " + timings);
    }

    // ---- the group ----

    static Mirrors.Line at(double x, double y, double deg) { return new Mirrors.Line(x, y, Math.toRadians(deg)); }
    static Mirrors with(Mirrors.Line... lines) { Mirrors m = Mirrors.NONE; for (Mirrors.Line l : lines) m = m.add(l); return m; }
    static void group() {
        check(with(at(300, 300, 90)).group().maps.length == 2, "One mirror: the page and its reflection");
        check(with(at(300, 300, 90), at(300, 300, 0)).group().maps.length == 4, "Two at 90°: four");
        Mirrors k = Mirrors.NONE.kaleidoscope(W, H);
        check(k.size() == 3 && k.group().maps.length == 6 && !k.group().capped && k.caption().equals("3 mirrors, 6 copies"), "The kaleidoscope: three mirrors, six copies: " + k.caption());
        check(with(at(300, 300, 0), at(300, 300, 45)).group().maps.length == 8, "Two at 45°: eight");
        Mirrors parallel = with(at(200, 300, 90), at(260, 300, 90));
        check(parallel.group().maps.length == Mirrors.MOST_COPIES && parallel.group().capped && parallel.caption().endsWith("copies max"), "Parallel mirrors stop at 24: " + parallel.caption());
        check(with(at(300, 300, 0), at(300, 300, 57.3)).group().capped, "Mirrors at an angle that doesn't divide 180° stop at 24");
        // Every map of the kaleidoscope keeps its centre, and the reflections turn the page over.
        int over = 0;
        for (double[] t : k.group().maps) {
            double[] c = Mirrors.Group.apply(t, 300, 300);
            check(Math.abs(c[0] - 300) < 1e-6 && Math.abs(c[1] - 300) < 1e-6, "The kaleidoscope's maps keep the centre");
            if (t[0] * t[3] - t[1] * t[2] < 0) over++;
        }
        check(over == 3, "Three of the six are reflections, three turns");
        check(Mirrors.NONE.caption().equals("No mirrors") && with(at(1, 1, 0)).caption().equals("1 mirror, 2 copies"), "Captions");
        check(with(at(300, 300, 90)).styled(Mirrors.Style.REAL).caption().equals("1 mirror, 1 reflection"), "Real Mirror's caption");
    }

    // ---- snapping ----

    static float[][] line(double x0, double y0, double x1, double y1, int n, double wobble) {
        float[] xs = new float[n], ys = new float[n];
        for (int i = 0; i < n; i++) {
            double u = i / (n - 1.0), w = wobble * Math.sin(i * .9);
            xs[i] = (float) (x0 + (x1 - x0) * u + w); ys[i] = (float) (y0 + (y1 - y0) * u - w);
        }
        return new float[][]{xs, ys};
    }
    static Mirrors.Line fit(Mirrors m, float[][] p) { return m.fit(p[0], p[1], p[0].length, W, H); }
    static void snapping() {
        // Nearly upright (2° off), 20 px right of the centre: upright, through the centre.
        double off = Math.tan(Math.toRadians(2)) * 200;
        Mirrors.Line l = fit(Mirrors.NONE, line(320 - off, 100, 320 + off, 500, 60, 1.5));
        check(l != null && Math.abs(Math.toDegrees(l.angle) - 90) < 1e-9 && Math.abs(l.side(300, 300)) < 1e-9, "A nearly upright line snaps upright through the centre: " + (l == null ? null : Math.toDegrees(l.angle)));
        // 40°: 5° from 45, not snapped; far from the centre: left where it is.
        Mirrors.Line slant = fit(Mirrors.NONE, line(100, 200, 100 + 200 * Math.cos(Math.toRadians(40)), 200 + 200 * Math.sin(Math.toRadians(40)), 50, 0));
        check(slant != null && Math.abs(Math.toDegrees(slant.angle) - 40) < .5 && Math.abs(slant.side(300, 300)) > Mirrors.SPOT, "40° stays 40°, away from the centre");
        // Through a crossing of two mirrors, not the centre: within 12 prototype px of it.
        Mirrors two = with(at(150, 150, 0), at(150, 150, 90));
        Mirrors.Line through = fit(two, line(150 + 30, 50, 150 + 30, 280, 40, 0));
        check(through != null && Math.abs(through.side(150, 150)) < 1e-9, "A line 30 px from a crossing moves through it");
        Mirrors.Line apart = fit(two, line(150 + 45, 50, 150 + 45, 280, 40, 0));
        check(apart != null && Math.abs(apart.side(150, 150)) > 40, "One 45 px off stays put (12 prototype px is " + Mirrors.SPOT + ")");
        check(fit(Mirrors.NONE, line(100, 100, 160, 100, 20, 0)) == null && fit(Mirrors.NONE, line(100, 100, 180, 100, 20, 0)) != null,
                "Under 24 prototype px (" + Mirrors.SHORT + " page px) is too short");
        check(fit(Mirrors.NONE, new float[][]{{100}, {100}}) == null, "A tap is too short");
    }
    static void cap() {
        Mirrors m = Mirrors.NONE;
        for (int i = 0; i < 5; i++) m = m.add(at(100 + 10 * i, 300, 90));
        check(m.size() == Mirrors.MOST && m.lines.get(0).x == 110 && m.lines.get(3).x == 140, "A fifth mirror replaces the oldest");
    }

    // ---- the section ----

    static byte[] write(ToneDocument d) throws IOException { ByteArrayOutputStream out = new ByteArrayOutputStream(); DocumentCodec.write(out, d.layerSnapshot()); return out.toByteArray(); }
    static ToneDocument read(byte[] bytes) throws IOException { return DocumentCodec.read(new ByteArrayInputStream(bytes)); }
    static void section() throws IOException {
        ToneDocument d = InkWashLayers.blank(W, H);
        byte[] plain = write(d);
        check(java.nio.ByteBuffer.wrap(plain).getInt() == 0x54534d33, "A page without mirrors writes TSM3 as before");
        Mirrors k = Mirrors.NONE.kaleidoscope(W, H).styled(Mirrors.Style.REAL).keeping(320, 250);
        d.setMirrors(k);
        byte[] mirrored = write(d);
        check(java.nio.ByteBuffer.wrap(mirrored).getInt() == 0x54534d35, "A page with mirrors writes TSM5 with a MIRR section");
        check(read(mirrored).mirrors().equals(k), "Its mirrors, style and side read back");
        d.setMirrors(k.cleared());
        check(java.util.Arrays.equals(write(d), plain), "Cleared: the page writes as before, byte for byte");
        d.showMirrors(false); d.setMirrors(with(at(10, 20, 30)).turned(false));
        check(!read(write(d)).mirrors().on, "Off is kept");
        byte[] section = k.encode();
        byte[] later = section.clone(); later[0] = 3;
        check(Mirrors.decode(later, W, H) == Mirrors.NONE, "A later version's section is skipped");
        boolean refused = false;
        try { Mirrors.decode(java.util.Arrays.copyOf(section, section.length - 3), W, H); } catch (IOException e) { refused = true; }
        check(refused, "A damaged section is refused");
        refused = false;
        byte[] many = section.clone(); many[18] = 9;
        try { Mirrors.decode(many, W, H); } catch (IOException e) { refused = true; }
        check(refused, "Too many mirrors is refused");
        // Show Guides: on writes the section as before (version 1: on, Real Mirror); off, version 2, its flag too.
        Mirrors hidden = k.guided(false);
        byte[] off = hidden.encode();
        check(section[0] == 1 && section[1] == 3 && section.length == 19 + 24 * 3 && off[0] == 2 && off[1] == 7 && off.length == section.length,
                "Guides shown write the section as before (version 1); hidden, version 2 with their flag");
        d.setMirrors(hidden);
        Mirrors back = read(write(d)).mirrors();
        check(back.equals(hidden) && !back.guides && back.style == Mirrors.Style.REAL && read(write(d)).mirrors().guided(true).equals(k), "Show Guides off round trips, the rest as it was");
        refused = false;
        byte[] flagged = section.clone(); flagged[1] = 7;
        try { Mirrors.decode(flagged, W, H); } catch (IOException e) { refused = true; }
        check(refused, "Version 1 has no guides flag: refused");
    }
    /** A page with a mark and a Real Mirror kaleidoscope, written to {@code file} for the version before mirrors to read (MirrorOlderReaderCheck); prints where the mark is and its tone. */
    static void writeMirrored(String file) throws IOException {
        ToneDocument d = InkWashLayers.blank(W, H);
        block(d, 400, 250, 40, 20);
        d.setMirrors(Mirrors.NONE.styled(Mirrors.Style.REAL).kaleidoscope(W, H).keeping(420, 260));
        try (java.io.OutputStream out = new java.io.FileOutputStream(file)) { DocumentCodec.write(out, d.layerSnapshot()); }
        System.out.println("420 260 " + d.compositeTone(420, 260));
    }
    static void undo() {
        ToneDocument d = InkWashLayers.blank(W, H);
        check(d.setMirrors(with(at(300, 300, 90))) && d.mirrors().size() == 1, "Placing is a step");
        d.setMirrors(d.mirrors().kaleidoscope(W, H));
        d.setMirrors(d.mirrors().cleared());
        check(d.mirrors().isEmpty() && d.undo() && d.mirrors().size() == 3 && d.undo() && d.mirrors().size() == 1 && d.undo() && d.mirrors().isEmpty(), "Undo takes back clearing, the kaleidoscope and placing");
        check(d.redo() && d.mirrors().size() == 1, "Redo puts it back");
        d.showMirrors(false);
        check(!d.mirrors().on && d.undoSteps() == 1 && d.undo() && !d.mirrors().on && d.redo() && !d.mirrors().on, "Off is no step, and stays off through Undo");
        check(!d.setMirrors(d.mirrors()), "The same mirrors again: no step");
    }

    // ---- Copy Strokes ----

    /** A short stroke from {@code x0, y0} to {@code x1, y1}, 2 ms a sample. */
    static void stroke(DrawingStroke s, double x0, double y0, double x1, double y1, int n) {
        for (int i = 0; i < n; i++) { double u = i / (n - 1.0); s.sample((float) (x0 + (x1 - x0) * u), (float) (y0 + (y1 - y0) * u), ToolSettings.pressure(1), Float.NaN, Float.NaN, 1000 + 2 * i); if (i % 8 == 7) s.present(); }
    }
    /** {@code b}'s stroke on {@code d} and its copies: laid as its change reflected where the brush allows ({@code reflects}), else drawn again. */
    static MirrorStroke mirrored(ToneDocument d, ModularBrush b, double size, WatercolorSession session, boolean reflects) {
        ResolvedBrush r = ResolvedBrush.of(b, size, 1);
        DrawingStroke primary = QuickStroke.start(d, b, size, 1, session);
        return new MirrorStroke(d, primary, d.mirrors().group(), () -> { if (session != null) session.settleBefore(d, b); return ModularStroke.start(d, b, size, 1, session); },
                2 * ModularStroke.reach(r) + 8, reflects && ModularStroke.laysAgain(r));
    }
    static boolean blank(ToneDocument d) { for (int y = 0; y < d.height; y++) for (int x = 0; x < d.width; x++) if (d.compositeTone(x, y) != 255) return false; return true; }
    static void copies() {
        ModularBrush pen = QuickStrokeChecks.brush("Fineliner", PartChoice.NONE);
        for (boolean reflects : new boolean[]{false, true}) {
            ToneDocument d = InkWashLayers.blank(W, H);
            d.setMirrors(d.mirrors().kaleidoscope(W, H));
            MirrorStroke s = mirrored(d, pen, 6, null, reflects);
            stroke(s, 340, 200, 420, 160, 40);
            check(s.lift() && s.develop(0), "At lift its copies are left to lay, one a look");
            while (s.develop(0)) { }
            s.finish();
            String how = reflects ? " (reflected)" : " (drawn again)";
            check(s.drawn == 5, "A kaleidoscope's stroke lays five copies besides itself" + how + ": " + s.drawn);
            // The stroke's middle and its five images are inked.
            int inked = 0;
            for (double[] p : d.mirrors().group().points(380, 180)) if (d.compositeTone((int) Math.round(p[0]), (int) Math.round(p[1])) < 128) inked++;
            check(inked == 6, "Six copies on the page" + how + ": " + inked);
            check(d.undo() && blank(d), "One Undo takes all six back" + how);
            check(d.redo() && d.compositeTone(380, 180) < 128 && !blank(d), "Redo puts them back" + how);
            // The eraser's copies (always drawn again) erase as it does.
            InkWashSizes.Preset big = InkWashSizes.Preset.LARGER;
            MirrorStroke erase = new MirrorStroke(d, new EraseBothStroke(d, big), d.mirrors().group(), () -> new EraseBothStroke(d, big), 240, false);
            stroke(erase, 340, 200, 420, 160, 40);
            erase.finish();
            check(blank(d), "The eraser's copies take all six away" + how);
            check(d.undo() && d.compositeTone(380, 180) < 128 && !blank(d), "and one Undo brings all six back" + how);
        }
        // A copy wholly off the page is never laid: one mirror near the edge, a stroke well inside.
        for (boolean reflects : new boolean[]{false, true}) {
            ToneDocument e = InkWashLayers.blank(W, H);
            e.setMirrors(with(at(580, 300, 90)));
            MirrorStroke inside = mirrored(e, pen, 6, null, reflects);
            stroke(inside, 100, 300, 200, 300, 30);
            inside.finish();
            check(inside.drawn == 0, "A copy off the page is culled");
        }
    }

    // ---- copies laid as the stroke's change reflected ----

    /** A curve of {@code n} samples from {@code x, y} (page px) by {@code dx, dy}, waving {@code waves} times, pressure rising, 3 ms a sample. */
    static void curve(DrawingStroke s, double x, double y, double dx, double dy, int n, int waves) {
        for (int i = 0; i < n; i++) {
            double u = i / (n - 1.0);
            s.sample((float) (x + dx * u), (float) (y + dy * u + wave(dx, dy, waves, u)), (float) (.4 + .5 * u), Float.NaN, Float.NaN, 1000 + 3 * i);
            if (i % 8 == 7) s.present();
        }
    }
    /** {@link #curve}'s wave at {@code u} of the way along. */
    static double wave(double dx, double dy, int waves, double u) { return .2 * Math.hypot(dx, dy) * Math.sin(u * 2 * Math.PI * waves); }
    /** The default slot {@code slot}'s brush (Fineliner, Brush pen, Pencil, Ink wash, Watercolor, Gouache). */
    static ModularBrush slot(int slot) { return ToolSlotsChecks.old(slot).parts; }
    /** {@code b}'s curve laid at {@code size} with its copies, reflected or drawn again, on {@code d} (null: a blank {@code w × h} page) with mirrors {@code m}; settled. */
    static ToneDocument laid(Mirrors m, int w, int h, ModularBrush b, double size, boolean reflects, ToneDocument d) {
        if (d == null) d = InkWashLayers.blank(w, h);
        d.setMirrors(m);
        WatercolorSession session = new WatercolorSession();
        MirrorStroke s = mirrored(d, b, size, session, reflects);
        curve(s, w / 2.0 + 60, h / 2.0 - 60, 200, -160, 300, 1);
        s.finish();
        session.settleNow(d);
        return d;
    }
    /** The mean difference between two pages' tones over the pixels either marks, and how many differ by more than {@code by}. */
    static double[] difference(ToneDocument a, ToneDocument b, int by) {
        long sum = 0; int n = 0, over = 0;
        for (int y = 0; y < a.height; y++) for (int x = 0; x < a.width; x++) {
            int p = a.compositeTone(x, y), q = b.compositeTone(x, y);
            if (p == 255 && q == 255) continue;
            n++; sum += Math.abs(p - q); if (Math.abs(p - q) > by) over++;
        }
        return new double[]{n == 0 ? 0 : sum / (double) n, over};
    }
    /** How many pixels of {@code d} differ from their mirror image across the page's middle, upright, by more than {@code by} greys. */
    static int unlike(ToneDocument d, int by) {
        int off = 0;
        for (int y = 0; y < d.height; y++) for (int x = 0; x < d.width; x++) if (Math.abs(d.compositeTone(x, y) - d.compositeTone(d.width - 1 - x, y)) > by) off++;
        return off;
    }
    static void reflected() throws IOException {
        // Which brushes: the default slots but the opaque ones (paint mixes what is under it), and no wash that settles after lift.
        check(ModularStroke.laysAgain(ResolvedBrush.of(slot(0), 6, 1)) && ModularStroke.laysAgain(ResolvedBrush.of(slot(4), 6, 1)) && ModularStroke.laysAgain(ResolvedBrush.of(slot(5), 120, 1))
                && !ModularStroke.laysAgain(ResolvedBrush.of(slot(1), 30, 1)) && !ModularStroke.laysAgain(ResolvedBrush.of(slot(3), 30, 1)) && !ModularStroke.laysAgain(ResolvedBrush.of(slot(6), 30, 1))
                && !ModularStroke.laysAgain(ResolvedBrush.of(QuickStrokeChecks.brush("Watercolor", PartChoice.DRYING), 120, 1)),
                "The Fineliner, Sketch pencil and Slow ink wash lay their copies reflected; the Halftone brush, Wax crayon, Flat oils, and a wash that settles after lift, draw them again");
        // A mirror on the pixel grid: the copy is the stroke reflected, pixel for pixel; for a brush without the page's
        // paper grain, the copy drawn again, exactly; the eraser leaves of it what it leaves of the stroke. (The wash at
        // 44 px: from 50 a paint is worked out on the lattice's even pixels, which a copy drawn again lands a pixel over.)
        Mirrors one = with(at(W / 2.0, H / 2.0, 90));
        for (int[] c : new int[][]{{0, 6}, {4, 6}, {5, 20}}) {
            ToneDocument f = laid(one, W, H, slot(c[0]), c[1], true, null), g = laid(one, W, H, slot(c[0]), c[1], false, null);
            int off = unlike(f, 0);
            double[] diff = difference(f, g, 0);
            check(off == 0 && (c[0] >= 4 || diff[1] == 0), "On the grid a reflected copy is the stroke reflected, pixel for pixel (" + off + " off), and the copy drawn again ("
                    + (int) diff[1] + " differ; Sketch pencil's and the Slow ink wash's paper grain is the page's): slot " + c[0]);
            check(f.undo() && blank(f) && f.redo() && unlike(f, 0) == 0, "A reflected copy is one Undo step with its stroke");
            // A pixel black at coverage 254 reads to the eraser as a Tone dot, which Everything scrapes by chance each pass
            // (EraseBothStroke.scrapes, salted by the page's revision): left out on both sides.
            boolean[] dot = new boolean[W * H];
            ToneDocument.Layer look = f.layerSnapshot().layers.get(InkWashLayers.INK).flattened();
            for (int i = 0; i < dot.length; i++) { int a = look.alpha[i] & 255; dot[i] = a == PrintScreen.DOT_COVERAGE && ((look.tones[i] & 255) * a + 255 * (255 - a) + 127) / 255 == PrintScreen.DOT_TONE; }
            // Fineliner: permanent; Watercolor: .4 erasable. Rows across the page, each as the mirror sees it.
            for (int y = 0; y < H; y += 20) {
                EraseBothStroke e = new EraseBothStroke(f, InkWashSizes.Preset.LARGE);
                e.sample(0, y, 1, Float.NaN, Float.NaN); e.sample(W - 1, y, 1, Float.NaN, Float.NaN);
                e.finish();
            }
            // What the eraser can't take is worked out from the copy's own darkness, rounded: within a grey.
            int erasedOff = 0;
            for (int y = 0; y < H; y++) for (int x = 0; x < W; x++)
                if (!dot[y * W + x] && !dot[y * W + W - 1 - x] && Math.abs(f.compositeTone(x, y) - f.compositeTone(W - 1 - x, y)) > 1) erasedOff++;
            check(erasedOff == 0, "The eraser leaves of a reflected copy what it leaves of the stroke, within a grey (" + erasedOff + " off): slot " + c[0]);
        }
        // Turned copies (a kaleidoscope's) read between pixels: within a few greys of copies drawn again.
        Mirrors k = Mirrors.NONE.kaleidoscope(800, 800);
        double[] pen = difference(laid(k, 800, 800, slot(0), 6, true, null), laid(k, 800, 800, slot(0), 6, false, null), 8);
        double[] wash = difference(laid(k, 800, 800, LegacyBrushes.builtin("WATERCOLOR"), 120, true, null), laid(k, 800, 800, LegacyBrushes.builtin("WATERCOLOR"), 120, false, null), 8);
        check(pen[0] < TURNED_PEN && wash[0] < TURNED_WASH, String.format(java.util.Locale.ROOT,
                "Turned copies are within %.0f greys of copies drawn again, on average, for a 6 px pen (%.2f), %.0f for a 120 px wash (%.2f)", TURNED_PEN, pen[0], TURNED_WASH, wash[0]));
        // Over other marks: a copy darkens what it finds by the factor the stroke darkened what it found, as a copy drawn
        // again does (a mark that multiplies what is under it). A grey block on the pen's layer, on the copy's side only.
        ToneDocument[] over = new ToneDocument[2];
        for (int r = 0; r < 2; r++) {
            ToneDocument d = InkWashLayers.blank(W, H);
            ModularStroke.selectLayer(d, slot(0));
            d.begin(); int[] grey = new int[200 * 200]; java.util.Arrays.fill(grey, 160); d.setTones(300, 100, 200, 200, grey); d.finish();
            over[r] = laid(with(at(300, 300, 90)), W, H, slot(0), 6, r == 1, d);
        }
        int worst = 0, darkened = 0;
        for (int y = 100; y < 300; y++) for (int x = 300; x < 500; x++) {
            int t = over[1].compositeTone(x, y);
            worst = Math.max(worst, Math.abs(t - over[0].compositeTone(x, y)));
            if (t < 160) darkened++;
        }
        check(darkened > 200 && worst <= 1, "A reflected copy over other marks darkens them as a copy drawn again does, within a grey (" + worst + ", " + darkened + " px)");
        // The stroke's box and each copy's apart, for the canvas to render (a short stroke far out: six small boxes, not one round the middle).
        ToneDocument apart = InkWashLayers.blank(W, H);
        apart.setMirrors(Mirrors.NONE.kaleidoscope(W, H));
        apart.clearDirty();
        MirrorStroke little = mirrored(apart, slot(0), 6, null, true);
        stroke(little, 480, 260, 500, 240, 12);
        little.finish();
        java.util.List<int[]> areas = little.areas();
        long within = 0;
        int outside = 0;
        for (int[] a : areas) within += (long) (a[2] - a[0]) * (a[3] - a[1]);
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            if (apart.compositeTone(x, y) == 255) continue;
            boolean in = false;
            for (int[] a : areas) in |= x >= a[0] && y >= a[1] && x < a[2] && y < a[3];
            if (!in) outside++;
        }
        int[] all = apart.dirty();
        check(areas.size() == 6 && outside == 0 && within * 10 < (long) (all[2] - all[0]) * (all[3] - all[1]),
                "Each reflected copy's box apart holds all it changed, a tenth of the box round them all (" + areas.size() + " boxes, " + outside + " px outside)");
        check(mirrored(InkWashLayers.blank(W, H), slot(1), 30, null, true).areas() == null, "Copies drawn again have no boxes apart");
        // Masking fluid on the page (it shields as the stroke went): copies drawn again.
        ToneDocument masked = InkWashLayers.blank(W, H);
        masked.paintMaskingFluid(true); masked.begin(); masked.setTone(10, 10, 0); masked.finish(); masked.paintMaskingFluid(false);
        masked.setMirrors(Mirrors.NONE.kaleidoscope(W, H));
        Object before = masked.lastStep();
        MirrorStroke s = mirrored(masked, slot(0), 6, null, true);
        stroke(s, 340, 200, 420, 160, 40);
        s.finish();
        check(s.drawn == 5 && masked.darkeningSince(before) == null, "With masking fluid on the page the copies are drawn again");
        // Saved and read back: the page as it shows, every layer.
        ToneDocument d = laid(Mirrors.NONE.kaleidoscope(W, H), W, H, LegacyBrushes.builtin("WATERCOLOR"), 120, true, null);
        ToneDocument back = read(write(d));
        int changed = 0;
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) if (back.compositeTone(x, y) != d.compositeTone(x, y)) changed++;
        check(changed == 0 && back.layerCount() == d.layerCount() && java.util.Arrays.equals(write(back), write(d)), "A page of reflected copies saves and reads back as it was (" + changed + " px)");
    }
    /** What turned copies keep to: their mean difference from copies drawn again, greys (measured: 3.8 for the pen, .1 for the wash). */
    static final double TURNED_PEN = 5, TURNED_WASH = 1;

    // ---- Real Mirror ----

    /** A dark block at {@code x, y}, {@code w × h}, on the selected pair's ink layer. */
    static void block(ToneDocument d, int x, int y, int w, int h) {
        d.selectLayer(InkWashLayers.inkOf(d, InkWashLayers.current(d)));
        d.begin();
        int[] tones = new int[w * h];
        d.setTones(x, y, w, h, tones);
        d.finish();
    }
    static int tone(int[] pixels, int x, int y) { return pixels[y * W + x] & 255; }
    static void real() {
        ToneDocument d = InkWashLayers.blank(W, H);
        block(d, 100, 100, 40, 40);   // kept: left of the mirror
        block(d, 420, 400, 30, 30);   // covered: right of it
        Mirrors one = with(at(300, 300, 90)).styled(Mirrors.Style.REAL);
        double[] side = Mirrors.mostInk(one, d::compositeTone, W, H);
        check(side[0] < 300 && one.keeping(side[0], side[1]).view().kept(120, 120), "The side with more ink is kept at first");
        d.setMirrors(one.keeping(side[0], side[1]));
        int[] shown = d.exportPixels(false);
        // 100..140 reflects across x = 300 to 460..500.
        check(tone(shown, 480, 120) == 0 && tone(shown, 120, 120) == 0, "The kept block shows in the reflection");
        check(tone(shown, 435, 415) == 255, "The covered block is under the mirror");
        check(d.compositeTone(435, 415) == 0 && d.compositeTone(480, 120) == 255, "and the page's layers are untouched");
        d.setMirrors(d.mirrors().cleared());
        shown = d.exportPixels(false);
        check(tone(shown, 435, 415) == 0 && tone(shown, 480, 120) == 255, "Cleared: the covered marks come back, the reflection goes");
        d.undo();
        d.setMirrors(d.mirrors().styled(Mirrors.Style.COPY));
        check(tone(d.exportPixels(false), 435, 415) == 0, "Back to Copy Strokes: the covered marks show again");
        d.undo();
        check(d.mirrors().reflects() && d.keepReflection(), "Keep Reflection");
        check(d.mirrors().isEmpty() && d.compositeTone(480, 120) == 0 && d.compositeTone(435, 415) == 255, "lays the reflection into the page and removes the mirror");
        check(d.undo() && d.mirrors().reflects() && d.compositeTone(480, 120) == 255 && d.compositeTone(435, 415) == 0, "One Undo puts the page and the mirror back");
        // The page's picture (the shelf, the PDF) shows what is seen.
        PagePicture.Composite c = new PagePicture.Composite(d.layerSnapshot());
        int[] row = new int[W];
        c.row(120, row);
        check(row[480] == 0 && row[120] == 0, "The page's picture shows the reflection");
    }
    static void realKaleidoscope() {
        ToneDocument d = InkWashLayers.blank(W, H);
        // A mark in one of the six sectors (round 15° from the right, between the 30° and 330° ... mirrors).
        block(d, 400, 300 - 50, 40, 20);
        Mirrors k = Mirrors.NONE.styled(Mirrors.Style.REAL).kaleidoscope(W, H);
        double[] side = Mirrors.mostInk(k, d::compositeTone, W, H);
        d.setMirrors(k.keeping(side[0], side[1]));
        int[] shown = d.exportPixels(false);
        int seen = 0;
        for (double[] p : d.mirrors().group().points(420, 260)) if (tone(shown, (int) Math.round(p[0]), (int) Math.round(p[1])) == 0) seen++;
        check(seen == 6, "Three Real Mirrors over a mark show it six times: " + seen);
        check(d.mirrors().caption().equals("3 mirrors, 5 reflections"), d.mirrors().caption());
    }
    /** A stroke that keeps its samples: what Real Mirror's fold feeds the brush. */
    static final class Recorded implements DrawingStroke {
        final java.util.List<float[]> samples = new java.util.ArrayList<>();
        final java.util.List<Long> times = new java.util.ArrayList<>();
        @Override public void sample(float x, float y, float pressure, float tiltX, float tiltY) { sample(x, y, pressure, tiltX, tiltY, PenSpeed.UNTIMED); }
        @Override public void sample(float x, float y, float pressure, float tiltX, float tiltY, long time) { samples.add(new float[]{x, y, pressure, tiltX, tiltY}); times.add(time); }
        @Override public boolean finish() { return false; }
        /** The longest step between two samples, page px. */
        double longestStep() { double most = 0; for (int i = 1; i < samples.size(); i++) most = Math.max(most, Math.hypot(samples.get(i)[0] - samples.get(i - 1)[0], samples.get(i)[1] - samples.get(i - 1)[1])); return most; }
    }
    static MirrorStroke real(ToneDocument d, ModularBrush pen) {
        return new MirrorStroke(d, ModularStroke.start(d, pen, 6, 1, null), d.mirrors().view(), () -> ModularStroke.start(d, pen, 6, 1, null));
    }
    static int beyond(ToneDocument d, int from) {
        int n = 0;
        for (int y = 0; y < H; y++) for (int x = from; x < W; x++) if (d.compositeTone(x, y) < 255) n++;
        return n;
    }
    static void realStroke() {
        ToneDocument d = InkWashLayers.blank(W, H);
        d.setMirrors(with(at(300, 300, 90)).styled(Mirrors.Style.REAL).keeping(100, 300));
        ModularBrush pen = QuickStrokeChecks.brush("Fineliner", PartChoice.NONE);
        // Either side: a stroke on the covered side lands at its reflection on the kept side, and shows on both.
        MirrorStroke covered = real(d, pen);
        stroke(covered, 400, 200, 500, 300, 30);
        check(covered.finish(), "A stroke on the covered side draws");
        int past = beyond(d, 302);
        int[] shown = d.exportPixels(false);
        check(d.compositeTone(150, 250) < 128 && past == 0 && tone(shown, 450, 250) < 128 && tone(shown, 150, 250) < 128,
                "A stroke on the covered side lands at the reflected spot on the kept side (" + past + " px beyond), and shows on both");
        check(d.undo() && blank(d), "One Undo takes it back");
        // Through the mirror: a diagonal crossing at 300, 300 shows whole and unbroken, on the page only on the kept side.
        MirrorStroke across = real(d, pen);
        stroke(across, 200, 200, 450, 450, 61);
        across.finish();
        shown = d.exportPixels(false);
        int gaps = 0;
        for (int i = 0; i <= 250; i++) if (tone(shown, 200 + i, 200 + i) >= 128) gaps++;
        // Only the pen's width spills past the line where the stroke meets it, hidden under the mirror.
        past = beyond(d, 306);
        check(gaps == 0 && past == 0 && d.compositeTone(300, 300) < 128 && d.compositeTone(150, 450) < 128 && across.drawn == 0,
                "A stroke through the mirror draws on, its reflection meeting it at the line, no gap (" + gaps + " px unlit, " + past + " beyond)");
        check(d.undo() && blank(d), "One Undo takes the stroke through the mirror back");
        // What the brush is fed: a sample at the crossing, its pressure, time and tilt between the two; the tilt folded after.
        Recorded fed = new Recorded();
        MirrorStroke rec = new MirrorStroke(d, fed, d.mirrors().view(), () -> null);
        rec.sample(290, 280, .4f, 10, 5, 1000);
        rec.sample(320, 300, .8f, 10, 5, 1006);
        check(fed.samples.size() == 3, "A sample at the crossing: " + fed.samples.size());
        float[] at = fed.samples.get(1), after = fed.samples.get(2);
        check(Math.abs(at[0] - 300) < 1e-3 && Math.abs(at[1] - 280 - 20 / 3f) < 1e-3 && Math.abs(at[2] - (.4f + .4f / 3)) < 1e-4
                && fed.times.get(1) == 1002 && Math.abs(after[0] - 280) < 1e-3 && Math.abs(after[1] - 300) < 1e-3 && Math.abs(after[3] + 10) < 1e-3 && Math.abs(after[4] - 5) < 1e-3,
                "The crossing is a sample on the line, pressure and time between, the tilt mirrored past it");
        // A kaleidoscope folds any cell: a stroke round the centre, through all six cells, feeds the brush the kept cell only, step by step.
        Mirrors k = Mirrors.NONE.kaleidoscope(W, H).styled(Mirrors.Style.REAL);
        ToneDocument e = InkWashLayers.blank(W, H);
        e.setMirrors(k.keeping(400, 260));
        Mirrors.View v = e.mirrors().view();
        Recorded round = new Recorded();
        MirrorStroke circle = new MirrorStroke(e, round, v, () -> null);
        java.util.Set<Integer> cells = new java.util.HashSet<>();
        int n = 400;
        double step = 2 * Math.PI * 120 / n;
        for (int i = 0; i <= n; i++) {
            double a = 2 * Math.PI * i / n + .01;
            float x = (float) (300 + 120 * Math.cos(a)), y = (float) (300 + 120 * Math.sin(a));
            cells.add(v.cell(x, y));
            circle.sample(x, y, .6f, Float.NaN, Float.NaN, 1000 + i);
        }
        boolean allKept = true;
        for (float[] f : round.samples) allKept &= v.kept(f[0], f[1]) || onMirror(e.mirrors(), f[0], f[1]);
        check(cells.size() == 6 && allKept && round.longestStep() <= step + 1e-3 && round.samples.size() == n + 1 + 6,
                "A kaleidoscope folds every cell into the kept one, one sample at each of the six crossings, no step longer than the pen's ("
                        + cells.size() + " cells, " + allKept + ", " + round.longestStep() + ", " + round.samples.size() + ")");
        // A mark on the page from each cell: each lands in the kept cell, where that cell's map takes it back to the pen.
        int landed = 0;
        for (double[] p : e.mirrors().group().points(400, 260)) {
            MirrorStroke dot = real(e, pen);
            stroke(dot, p[0] - 4, p[1] - 4, p[0] + 4, p[1] + 4, 9);
            dot.finish();
            if (e.compositeTone(400, 260) < 128) landed++;
            check(e.undo() && blank(e), "Each is one Undo step");
        }
        check(landed == 6, "A stroke in any of the six cells lands at the kept point: " + landed);
        // Mirrors past the cap (their cells don't tile): a fold that jumps goes on as another stroke at lift, one Undo step with it.
        ToneDocument f = InkWashLayers.blank(W, H);
        f.setMirrors(with(at(300, 300, 0), at(300, 300, 57.3)).styled(Mirrors.Style.REAL).keeping(400, 330));
        MirrorStroke jumps = real(f, pen);
        for (int i = 0; i <= n; i++) { double a = 2 * Math.PI * i / n; jumps.sample((float) (300 + 150 * Math.cos(a)), (float) (300 + 150 * Math.sin(a)), .7f, Float.NaN, Float.NaN, 1000 + 2 * i); }
        jumps.lift();
        while (jumps.develop(0)) { }
        jumps.finish();
        check(jumps.drawn > 0 && !blank(f) && f.undo() && blank(f), "Past the cap a jumping fold goes on as more strokes (" + jumps.drawn + "), one Undo step");
    }
    static boolean onMirror(Mirrors m, double x, double y) { for (Mirrors.Line l : m.lines) if (Math.abs(l.side(x, y)) < 1e-2) return true; return false; }
    static void flipSide() {
        Mirrors one = with(at(300, 300, 90)).styled(Mirrors.Style.REAL).keeping(100, 300);
        check(one.flipped(W, H).view().kept(500, 300) && one.flipped(W, H).flipped(W, H).view().kept(100, 300), "Flip Side keeps one mirror's other side, and back");
        Mirrors k = Mirrors.NONE.kaleidoscope(W, H).styled(Mirrors.Style.REAL).keeping(400, 260);
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        Mirrors m = k;
        Mirrors.View first = k.view();
        for (int i = 0; i < 6; i++) { seen.add(first.cell(m.keepX, m.keepY)); m = m.flipped(W, H); }
        check(seen.size() == 6 && first.cell(m.keepX, m.keepY) == 0, "Flip Side goes round a kaleidoscope's six cells and back");
        Mirrors copy = with(at(300, 300, 90));
        check(copy.flipped(W, H) == copy, "Copy Strokes has no side to flip");
        ToneDocument d = InkWashLayers.blank(W, H);
        d.setMirrors(one);
        check(d.setMirrors(d.mirrors().flipped(W, H)) && d.undo() && d.mirrors().equals(one), "Flip Side is one Undo step");
    }

    // ---- the canvas ----

    static void show() {
        MirrorShow show = new MirrorShow();
        show.page(W, H);
        Mirrors one = with(at(300, 300, 90));
        show.show(one);
        int[] px = new int[W * H];
        java.util.Arrays.fill(px, 0xffffffff);
        check(show.draw(px, 0, 0, W, H, false, (x, y) -> 255, 255), "The guides draw");
        int dashes = 0;
        for (int y = 0; y < H; y++) if ((px[y * W + 300] & 255) == Mirrors.GUIDE_TONE) dashes++;
        check(dashes > H / 3 && dashes < 2 * H / 3 && (px[300 * W + 200] & 255) == 255, "dashed down the mirror, half its length, nothing beside it (" + dashes + ")");
        show.show(one.turned(false));
        check(!show.touches(0, 0, W, H), "Off: no guides");
        // Live copies: a mark at 100..110 shows at 490..500 across the mirror.
        show.show(one);
        show.beginCopies(one.group(), true);
        java.util.List<int[]> areas = show.take(new int[]{100, 100, 110, 110}, (x, y, w, h, out) -> {
            for (int i = 0; i < w * h; i++) out[i] = 0;
        });
        check(areas.size() == 1 && areas.get(0)[0] <= 490 && areas.get(0)[2] >= 500, "The copy's area is rendered again");
        int[] near = new int[40 * 40];
        java.util.Arrays.fill(near, 0xffffffff);
        show.draw(near, 480, 90, 40, 40, false, (x, y) -> 255, 255);
        check((near[15 * 40 + 15] & 255) == 0 && (near[15 * 40 + 5] & 255) == 255, "The live copy shows across the mirror");
        show.end();
        java.util.List<int[]> after = show.take(null, null);
        check(after.size() == 1 && !show.showing(), "At lift the live copies go, their area rendered again");
        // Real Mirror: the view shows the kept side's tones.
        Mirrors real = one.styled(Mirrors.Style.REAL).keeping(100, 300);
        show.show(real);
        java.util.Arrays.fill(px, 0xffffffff);
        show.draw(px, 0, 0, W, H, false, (x, y) -> x < 50 ? 0 : 255, 255);
        check((px[10 * W + 580] & 255) == 0 && (px[10 * W + 10] & 255) == 255, "Real Mirror's view reflects the page's tones (the kept side is the canvas's own render)");
    }
    /** Show Guides off: no dashes drawn, the mirrors acting all the same; no Undo step, and Undo and Remove Mirrors keep it. */
    static void guides() {
        ToneDocument d = InkWashLayers.blank(W, H);
        d.setMirrors(with(at(300, 300, 90)));
        int steps = d.undoSteps();
        d.showGuides(false);
        check(!d.mirrors().guides && d.mirrors().copies() && d.undoSteps() == steps, "Show Guides off is no Undo step, and the mirror still copies");
        d.setMirrors(d.mirrors().add(at(300, 300, 0)));
        check(d.undo() && d.mirrors().size() == 1 && !d.mirrors().guides && d.redo() && !d.mirrors().guides, "Undo and Redo keep the guides hidden");
        check(!d.mirrors().cleared().guides && d.mirrors().cleared().isEmpty() && d.mirrors().guided(true).cleared() == Mirrors.NONE, "Remove Mirrors keeps the setting");
        MirrorShow show = new MirrorShow();
        show.page(W, H);
        show.show(d.mirrors());
        int[] px = new int[W * H];
        java.util.Arrays.fill(px, 0xffffffff);
        check(!show.touches(0, 0, W, H) && !show.draw(px, 0, 0, W, H, false, (x, y) -> 255, 255), "Hidden, no guides are drawn");
        // The live copies still show.
        show.beginCopies(d.mirrors().group(), true);
        java.util.List<int[]> areas = show.take(new int[]{100, 100, 110, 110}, (x, y, w, h, out) -> java.util.Arrays.fill(out, 0, w * h, 0));
        check(areas.size() == 3 && show.draw(px, 0, 0, W, H, false, (x, y) -> 255, 255) && (px[105 * W + 495] & 255) == 0 && (px[300 * W + 300] & 255) == 255, "Hidden, the live copies still show, and no dash");
        show.end();
        d.showGuides(true);
        show.show(d.mirrors());
        java.util.Arrays.fill(px, 0xffffffff);
        check(show.touches(0, 0, W, H) && show.draw(px, 0, 0, W, H, false, (x, y) -> 255, 255), "Shown again, they draw");
        // A pen's line in a frame shows every live copy: the kaleidoscope's five, the cap's 23.
        for (Mirrors m : new Mirrors[]{Mirrors.NONE.kaleidoscope(1920, 2560), with(at(960, 1280, 0), at(960, 1280, 7.5))}) {
            MirrorShow live = new MirrorShow();
            live.page(1920, 2560); live.show(m); live.beginCopies(m.group(), true);
            int n = live.take(new int[]{1160, 980, 1260, 1080}, (x, y, w, h, out) -> { for (int j = 0; j < h; j++) for (int i = 0; i < w; i++) out[j * w + i] = Math.abs(i - j) < 4 ? 0 : -1; }).size();
            check(n == m.group().maps.length - 1, "A pen's frame shows every live copy: " + n + " of " + (m.group().maps.length - 1));
        }
    }
    static void tilt() {
        double[] flip = Mirrors.Group.reflection(at(300, 300, 90));
        float[] t = Mirrors.Group.tilt(flip, 20, -10);
        check(Math.abs(t[0] + 20) < 1e-3 && Math.abs(t[1] + 10) < 1e-3, "Tilt mirrors with the page: " + t[0] + ", " + t[1]);
        float[] none = Mirrors.Group.tilt(flip, Float.NaN, Float.NaN);
        check(Float.isNaN(none[0]), "No tilt stays none");
    }

    // ---- timings ----

    /** The most a kaleidoscope's reflected copies of a long stroke may take at lift on this computer, ms: pens', a 120 px wash's (measured about 5 and 15; the Manta runs about 20 times slower). */
    static final long PEN_LIFT_MS = 60, WASH_LIFT_MS = 150;
    /** Mirrors through the centre of a {@code w × h} page, {@code n} of them evenly turned (3: the kaleidoscope's). */
    static Mirrors through(int w, int h, int n) {
        Mirrors m = Mirrors.NONE;
        for (int i = 0; i < n; i++) m = m.add(at(w / 2.0, h / 2.0, 90 + 180.0 * i / n));
        return m;
    }
    /**
     * How long the copies of a long stroke (about 2000 px of path) take at lift, ms (the median of three), drawn again
     * and laid reflected, for the default Fineliner at 6 px, Brush pen at 30 px and Watercolor at 120 px, with one to
     * four mirrors through the centre of a Manta page (three: the kaleidoscope); what showing a stroke's live copies
     * costs a frame ({@link MirrorShow#take} and its draw over a 200 px box); Real Mirror's frame under the pen with one
     * to four mirrors; a whole page's guides and reflection. Checks that the reflected copies keep within {@link
     * #PEN_LIFT_MS} and {@link #WASH_LIFT_MS}.
     */
    static String timings() {
        StringBuilder out = new StringBuilder();
        int w = 1920, h = 2560;
        double length = 0;
        for (int i = 1; i < 1000; i++) length += Math.hypot(LONG_DX / 999.0, LONG_DY / 999.0 + wave(LONG_DX, LONG_DY, 2, i / 999.0) - wave(LONG_DX, LONG_DY, 2, (i - 1) / 999.0));
        out.append(String.format(java.util.Locale.ROOT, "lift of a %.0f px stroke, drawn again / reflected:", length));
        for (int[] c : new int[][]{{0, 6}, {1, 30}, {4, 120}}) {
            ModularBrush b = slot(c[0]);
            out.append(' ').append(ToolSlotsChecks.old(c[0]).name()).append(' ').append(c[1]).append(" px");
            for (int n = 1; n <= 4; n++) {
                long[] times = new long[2];
                for (int reflects = 0; reflects < 2; reflects++) {
                    long[] lift = new long[3];
                    for (int r = 0; r < 3; r++) {
                        ToneDocument d = InkWashLayers.blank(w, h);
                        d.setMirrors(through(w, h, n));
                        WatercolorSession session = new WatercolorSession();
                        MirrorStroke s = mirrored(d, b, c[1], session, reflects == 1);
                        curve(s, w / 2.0 + 100, h / 2.0 - 150, LONG_DX, LONG_DY, 1000, 2);
                        long t0 = System.nanoTime();
                        s.finish();
                        lift[r] = System.nanoTime() - t0;
                    }
                    java.util.Arrays.sort(lift);
                    times[reflects] = lift[1];
                }
                out.append(String.format(java.util.Locale.ROOT, "%s %d %.0f / %.1f ms", n == 1 ? ":" : ",", n, times[0] / 1e6, times[1] / 1e6));
                if (n == 3) Timing.limit(times[1] / 1_000_000 < (c[1] >= 120 ? WASH_LIFT_MS : PEN_LIFT_MS), "A kaleidoscope's reflected copies of a long stroke lift quickly: " + times[1] / 1_000_000 + " ms");
            }
            out.append(';');
        }
        // A live frame: 24 copies of a 200 px box of marks, kept and drawn.
        MirrorShow show = new MirrorShow();
        show.page(w, h);
        Mirrors cap = with(at(w / 2.0, h / 2.0, 0), at(w / 2.0, h / 2.0, 7.5));
        show.show(cap);
        long best = Long.MAX_VALUE;
        int live = 0;
        int[] block = new int[202 * 202];
        for (int r = 0; r < 5; r++) {
            show.beginCopies(cap.group(), true);
            long t0 = System.nanoTime();
            java.util.List<int[]> areas = show.take(new int[]{w / 2 + 200, h / 2 - 300, w / 2 + 400, h / 2 - 100}, (x, y, bw, bh, o) -> { for (int i = 0; i < bw * bh; i++) o[i] = (i & 7) == 0 ? 0 : -1; });
            for (int[] a : areas) {
                int aw = Math.min(202, a[2] - a[0]), ah = Math.min(202, a[3] - a[1]);
                show.draw(block, a[0], a[1], aw, ah, true, (x, y) -> 255, 255);
            }
            best = Math.min(best, System.nanoTime() - t0);
            live = areas.size();
            show.end(); show.take(null, null);
        }
        // A whole Manta page drawn again (a page turned, an Undo): the guides alone; Real Mirror's kaleidoscope reflecting a page with marks.
        ToneDocument page = InkWashLayers.blank(w, h);
        block(page, w / 2 + 200, h / 2 - 300, 300, 120);
        int[] all = new int[w * h];
        Mirrors k = Mirrors.NONE.kaleidoscope(w, h);
        double[] side = Mirrors.mostInk(k.styled(Mirrors.Style.REAL), page::compositeTone, w, h);
        long guides = Long.MAX_VALUE, real = Long.MAX_VALUE;
        for (int r = 0; r < 3; r++) {
            MirrorShow whole = new MirrorShow();
            whole.page(w, h); whole.show(k);
            long t0 = System.nanoTime();
            whole.draw(all, 0, 0, w, h, true, page::compositeTone, 255);
            guides = Math.min(guides, System.nanoTime() - t0);
            whole.show(k.styled(Mirrors.Style.REAL).keeping(side[0], side[1]));
            t0 = System.nanoTime();
            whole.draw(all, 0, 0, w, h, true, page::compositeTone, 255);
            real = Math.min(real, System.nanoTime() - t0);
        }
        // Real Mirror under the pen: a 60 px box in the kept cell, and its image in every other, drawn.
        out.append(" Real Mirror's frame under the pen:");
        for (int n = 1; n <= 4; n++) {
            Mirrors m = through(w, h, n).styled(Mirrors.Style.REAL);
            double[] kept = Mirrors.mostInk(m, page::compositeTone, w, h);
            MirrorShow view = new MirrorShow();
            view.page(w, h); view.show(m.keeping(kept[0], kept[1]));
            long frame = Long.MAX_VALUE;
            for (int r = 0; r < 40; r++) {
                int x = (int) kept[0] - 30 + r % 10, y = (int) kept[1] - 30;
                long t0 = System.nanoTime();
                java.util.List<int[]> areas = view.take(new int[]{x, y, x + 60, y + 60}, null);
                areas.add(0, new int[]{x, y, x + 60, y + 60});
                for (int[] a : areas) view.draw(all, a[0], a[1], a[2] - a[0], a[3] - a[1], true, page::compositeTone, 255);
                frame = Math.min(frame, System.nanoTime() - t0);
            }
            out.append(String.format(java.util.Locale.ROOT, "%s %d %.2f ms", n == 1 ? "" : ",", n, frame / 1e6));
        }
        out.append(String.format(java.util.Locale.ROOT, "; a whole page's guides %.0f ms, Real Mirror's kaleidoscope over it %.0f ms; ", guides / 1e6, real / 1e6));
        out.append("a live frame of the cap's copies of a 200 px box, as many as LIVE_PX lets show: ").append(live).append(" shown, ").append(String.format(java.util.Locale.ROOT, "%.1f", best / 1e6)).append(" ms");
        return out.toString().trim();
    }
    /** The long stroke's run, page px: across and up a Manta page's upper right, waving twice (about 2000 px of path). */
    static final double LONG_DX = 650, LONG_DY = -900;

    // ---- a picture ----

    /** A kaleidoscope drawn on a page: a few strokes of ink and wash in one sector and across it, its PNG copy to {@code file}. */
    static void picture(String file) throws IOException {
        int w = 1404, h = 1872;
        ToneDocument d = InkWashLayers.blank(w, h);
        d.setMirrors(Mirrors.NONE.kaleidoscope(w, h));
        WatercolorSession session = new WatercolorSession();
        ModularBrush pen = QuickStrokeChecks.brush("Fineliner", PartChoice.NONE), wash = QuickStrokeChecks.brush("Watercolor", PartChoice.NONE);
        double cx = w / 2.0, cy = h / 2.0;
        Object[][] strokes = {{wash, 90.0, 0.0, 120.0, 200.0, 40.0, .45}, {pen, 6.0, 40.0, 60.0, 420.0, 120.0, 1.0}, {pen, 10.0, 160.0, -40.0, 300.0, 140.0, 1.0},
                {pen, 4.0, 100.0, 220.0, 520.0, 230.0, 1.0}, {wash, 50.0, 300.0, 40.0, 560.0, 180.0, .8}};
        for (Object[] st : strokes) {
            ModularBrush b = (ModularBrush) st[0];
            double size = (Double) st[1], dark = (Double) st[6];
            ModularStroke.selectLayer(d, b);
            MirrorStroke s = new MirrorStroke(d, ModularStroke.start(d, b, size, dark, session), d.mirrors().group(), () -> ModularStroke.start(d, b, size, dark, session), 2 * ModularStroke.reach(ResolvedBrush.of(b, size, dark)) + 8, ModularStroke.laysAgain(ResolvedBrush.of(b, size, dark)));
            double x0 = cx + (Double) st[2], y0 = cy - (Double) st[3], x1 = cx + (Double) st[4], y1 = cy - (Double) st[5];
            for (int i = 0; i < 120; i++) {
                double u = i / 119.0, bend = 40 * Math.sin(Math.PI * u);
                s.sample((float) (x0 + (x1 - x0) * u + bend * .3), (float) (y0 + (y1 - y0) * u - bend), (float) (.35 + .5 * Math.sin(Math.PI * u)), Float.NaN, Float.NaN, 1000 + 4 * i);
                if (i % 8 == 7) s.present();
            }
            s.finish();
            session.settleNow(d);
        }
        int[] pixels = d.exportPixels(true);
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, w, h, pixels, 0, w);
        javax.imageio.ImageIO.write(image, "png", new java.io.File(file));
        System.out.println("Wrote " + file);
    }

    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
