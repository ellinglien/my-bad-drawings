package io.github.mpdairy.monopaint;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;

/**
 * Fading and Lemon Juice (the mischief modules spec, Stage 3; {@link FadingInk}): codes appended, every older code byte
 * for byte; rolls rare, from the seed, every other roll as before; words; a fading mark thins out, thin parts first,
 * reaches nothing and leaves the page and the file; a page saved mid-fade and opened later goes on by the clock;
 * lemon juice starts faint and ends exactly as Quick lays it; Undo takes the stroke and never brings back what faded;
 * ink drawn over a fading mark stays; nothing changes by the clock on a page without them.
 */
public final class FadingChecks {
    public static void main(String[] args) throws IOException {
        Assets.install(Assets.folder(Assets.FOLDER));
        codes(); rolls(); words();
        fadesAway(); thinFirst(); reopened(); lemon(); undo(); drawnOver(); rewet(); idle(); steps(); speed();
        System.out.println("PASS: Fading and Lemon Juice are Drying's sixth and seventh choices (Fades In 10 s to 2 min, 20 s; Dries In 10 s to 10 min, 5 min), every older code byte for byte; "
                + "rolled rarely from the seed (Fading 1 in 40 and Lemon Juice 1 in 40 for Anything goes, Lemon Juice 1 in 60 for Ink wash), every other roll as before; \"Fades away in 20 s.\", \"Darkens in as it dries.\"; "
                + "a fading mark thins out unevenly, thin parts first, reaches nothing and leaves the page and its file byte for byte as never drawn; saved mid-fade and opened later it goes on by the clock; "
                + "lemon juice starts at a tenth of its darkness and ends exactly as Quick lays it; Undo takes the stroke, Redo gives it as the clock has it, and once gone nothing brings it back; "
                + "ink over a fading mark stays; a page without them never changes by the clock; Lemon Juice steps once a second, Fading every 2 to 5 s");
    }

    static final int W = 400, H = 240;
    /** The clock the checks set. */
    static long clock;
    static { FadingInk.clock = () -> clock; }

    static ModularBrush brush(PartChoice life, double time) {
        ModularBrush b = FloatChecks.preset("Swell brush").copy();
        ModularBrush.Part p = new ModularBrush.Part(life);
        if (life == PartChoice.FADE) p.set(PartDial.FADE_IN, time);
        if (life == PartChoice.LEMON) p.set(PartDial.DRY_IN, time);
        b.set(BrushPart.LIFE, p);
        return b.normalize();
    }
    static double[][] line(double x0, double x1, double y) {
        int n = 60;
        double[][] o = new double[n + 1][];
        for (int i = 0; i <= n; i++) o[i] = new double[]{x0 + (x1 - x0) * i / n, y + 30 * Math.sin(i / 9.0), .3 + .6 * i / n, 1000 + 8 * i};
        return o;
    }
    static void draw(ToneDocument d, ModularBrush b, double[][] path) {
        CarrySmudgeChecks.feed(ModularStroke.start(d, b, 14, 1, new WatercolorSession()), path);
    }
    static ToneDocument drawn(ModularBrush b) { ToneDocument d = InkWashLayers.blank(W, H); draw(d, b, line(40, 360, 120)); return d; }
    static byte[] file(ToneDocument d) throws IOException { ByteArrayOutputStream out = new ByteArrayOutputStream(); DocumentCodec.write(out, d.layerSnapshot()); return out.toByteArray(); }
    static ToneDocument read(byte[] file) throws IOException { return DocumentCodec.read(new ByteArrayInputStream(file)); }
    static int[] page(ToneDocument d) { int[] o = new int[W * H]; for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) o[y * W + x] = d.compositeTone(x, y); return o; }
    /** The page's darkness, summed. */
    static long dark(ToneDocument d) { long n = 0; for (int t : page(d)) n += 255 - t; return n; }
    static int inked(ToneDocument d) { int n = 0; for (int t : page(d)) if (t < 250) n++; return n; }

    // ---- codes, rolls, words ----

    static void codes() {
        check(BrushPart.LIFE.choices().indexOf(PartChoice.FADE) == 5 && BrushPart.LIFE.choices().indexOf(PartChoice.LEMON) == 6
                && PartChoice.FADE.key.equals("fade") && PartChoice.LEMON.key.equals("lemon") && PartChoice.FADE.label.equals("Fading") && PartChoice.LEMON.label.equals("Lemon Juice"), "Fading and Lemon Juice are Drying's sixth and seventh, keyed fade and lemon");
        check(PartChoice.FADE.dials.equals(Arrays.asList(PartDial.FADE_IN)) && PartChoice.LEMON.dials.equals(Arrays.asList(PartDial.DRY_IN))
                && PartDial.FADE_IN.key.equals("fadeIn") && PartDial.DRY_IN.key.equals("dryIn") && PartDial.FADE_IN.label.equals("Fades In") && PartDial.DRY_IN.label.equals("Dries In")
                && PartDial.FADE_IN.min == 10 && PartDial.FADE_IN.max == 120 && PartDial.FADE_IN.def == 20 && PartDial.DRY_IN.min == 10 && PartDial.DRY_IN.max == 600 && PartDial.DRY_IN.def == 300
                && PartDial.FADE_IN.shown(20).equals("20 s") && PartDial.DRY_IN.shown(300).equals("5:00") && PartDial.DRY_IN.shown(45).equals("45 s") && PartDial.DRY_IN.shown(195).equals("3:15"), "their dials: Fades In 10 s to 2 min (20 s), Dries In 10 s to 10 min (5:00)");
        for (ToolPresets.Preset p : ToolPresets.all()) check((ModularCode.encode(ModularCode.decode(p.code)).equals(p.code) || HandHatchChecks.handHatch(p.code)), p.name + "'s code reads and writes byte for byte");
        for (PartChoice life : new PartChoice[]{PartChoice.FADE, PartChoice.LEMON}) {
            ModularBrush b = brush(life, life == PartChoice.FADE ? 45 : 150);
            String code = ModularCode.encode(b);
            ModularBrush back = ModularCode.decode(code);
            check(back != null && ModularCode.encode(back).equals(code) && back.choice(BrushPart.LIFE) == life && Math.abs(back.part(BrushPart.LIFE).get(life.dials.get(0)) - (life == PartChoice.FADE ? 45 : 150)) < .5,
                    life + " reads back from its code: " + code);
            check(code.split("_")[4].charAt(0) == (life == PartChoice.FADE ? '5' : '6'), life + " is written as its choice index: " + code);
        }
    }

    static void rolls() {
        int n = 6000, fades = 0, lemons = 0, inkWash = 0, inkWashRolls = 0;
        for (ModularStyle s : ModularStyle.all()) for (int i = 0; i < (s.name.equals("Anything goes") ? n : s.name.equals("Ink wash") ? n : 200); i++) {
            ModularBrush on = s.roll(new Random(52000L + 977L * s.index + i)), off;
            ModularStyle.fadeRolls = false; ModularStyle.lemonRolls = false;
            try { off = s.roll(new Random(52000L + 977L * s.index + i)); } finally { ModularStyle.fadeRolls = true; ModularStyle.lemonRolls = true; }
            PartChoice life = on.choice(BrushPart.LIFE);
            if (life != PartChoice.FADE && life != PartChoice.LEMON) {
                check(ModularCode.encode(on).equals(ModularCode.encode(off)), s.name + ": a roll that neither fades nor darkens in is as before");
                continue;
            }
            check(s.fadeOdds() > 0 || life == PartChoice.LEMON && s.lemonOdds() > 0, s.name + " rolls " + life + " only with odds");
            ModularBrush c = on.copy(); c.set(BrushPart.LIFE, off.part(BrushPart.LIFE).copy());
            check(ModularCode.settings(c.normalize()).equals(ModularCode.settings(off)), s.name + ": " + life + " changes nothing but the drying");
            double v = on.part(BrushPart.LIFE).get(life.dials.get(0));
            check(life == PartChoice.FADE ? v >= 15 && v <= 60 : v >= 180 && v <= 420, life + "'s time from the seed: " + v);
            if (s.name.equals("Anything goes")) { if (life == PartChoice.FADE) fades++; else lemons++; }
            if (s.name.equals("Ink wash")) { check(life == PartChoice.LEMON, "Ink wash never fades"); inkWash++; }
        }
        double wantFade = n / 40.0, wantLemon = n * 39 / 40.0 / 40, wantInk = n / 60.0;
        check(Math.abs(fades - wantFade) < 4 * Math.sqrt(wantFade) + 3 && Math.abs(lemons - wantLemon) < 4 * Math.sqrt(wantLemon) + 3 && Math.abs(inkWash - wantInk) < 4 * Math.sqrt(wantInk) + 3,
                "rolls: Anything goes " + fades + " fading and " + lemons + " lemon juice of " + n + ", Ink wash " + inkWash + " lemon juice");
        check(ModularFeatures.seedOnly(PartChoice.FADE) && ModularFeatures.seedOnly(PartChoice.LEMON), "a variation never swaps to either");
    }

    static void words() {
        ModularBrush f = brush(PartChoice.FADE, 20), l = brush(PartChoice.LEMON, 300);
        String pf = ModularWords.plainWords(f), pl = ModularWords.plainWords(l);
        check(pf.contains("Fades away in 20 s.") && pl.contains("Darkens in as it dries."), "This brush: \"" + pf + "\", \"" + pl + "\"");
        check(ModularWords.caption(f).contains("fading") && ModularWords.caption(l).contains("lemon juice"), "captions: " + ModularWords.caption(f) + ", " + ModularWords.caption(l));
        check(ModularWords.choiceWords(f, BrushPart.LIFE).equals("Fading 20 s") && ModularWords.choiceWords(l, BrushPart.LIFE).equals("Lemon juice")
                && ModularWords.swapNote(f, BrushPart.LIFE).equals("fades 20 s") && ModularWords.swapNote(l, BrushPart.LIFE).equals("darkens 5:00"), "the Drying card and its swap notes");
        check(java.util.Arrays.asList("fading", "ghostly").contains(BrushNames.plainWords(f).get(1)) && java.util.Arrays.asList("secret", "sour").contains(BrushNames.plainWords(l).get(1)),
                "a rolled one is named for it: sly-fading-pen, sneaky-sour-pen (" + BrushNames.plainWords(f) + ", " + BrushNames.plainWords(l) + ")");
    }

    // ---- the page ----

    /** A fading mark reaches nothing: the page, its Undo and its file are as if it was never drawn. */
    static void fadesAway() throws IOException {
        clock = 1_000_000;
        ToneDocument blank = InkWashLayers.blank(W, H), d = drawn(brush(PartChoice.FADE, 20));
        check(d.fading() && inked(d) > 1000, "a fading stroke lays a mark that changes by the clock (" + inked(d) + " px)");
        byte[] laid = file(d);
        check(!Arrays.equals(laid, file(blank)), "its file keeps it, fading");
        long before = dark(d), last = before; boolean thinning = true;
        for (long t = clock; t <= clock + 26_000; t += 1000) { d.fade(t, null); long now = dark(d); thinning &= now <= last; last = now; }
        check(thinning && !d.fading() && Arrays.equals(page(d), page(blank)), "it thins out to nothing in Fades In times 1.25 at most (darkness " + before + " to " + dark(d) + ")");
        check(Arrays.equals(file(d), file(blank)), "once gone it leaves the file as a page never drawn on, byte for byte");
        check(!d.canUndo(), "once gone there is nothing to undo (" + d.undoSteps() + " steps)");
    }
    /** Thin parts go first: half way, the mark's faint edge has gone faster than its core. */
    static void thinFirst() {
        clock = 2_000_000;
        ToneDocument d = drawn(brush(PartChoice.FADE, 20)), quick = drawn(brush(PartChoice.FAST_WET, 0));
        check(Arrays.equals(page(d), page(quick)), "Fading lays as Quick does, at first");
        int[] start = page(d);
        d.fade(clock + 12_000, null);
        int[] mid = page(d);
        int faint = 0, faintGone = 0, core = 0, coreGone = 0;
        for (int i = 0; i < start.length; i++) {
            if (start[i] >= 250) continue;
            boolean gone = mid[i] >= 250;
            if (start[i] > 160) { faint++; if (gone) faintGone++; } else if (start[i] < 60) { core++; if (gone) coreGone++; }
        }
        check(faint > 50 && core > 50 && faintGone / (double) faint > coreGone / (double) core + .2,
                "thin parts first: at 12 s " + faintGone + " of " + faint + " faint pixels are gone, " + coreGone + " of " + core + " dark ones");
        boolean uneven = false;
        for (int i = 0; i < start.length; i++) if (start[i] < 60 && mid[i] > start[i] + 20 && mid[i] < 250) uneven = true;
        check(uneven, "it goes unevenly: some dark pixels are part way");
    }
    /** Saved mid-fade and opened later, it is as the clock has it, and goes on. */
    static void reopened() throws IOException {
        clock = 3_000_000;
        ToneDocument d = drawn(brush(PartChoice.FADE, 30)), same = drawn(brush(PartChoice.FADE, 30));
        d.fade(clock + 9_000, null); same.fade(clock + 9_000, null);
        byte[] saved = file(d);
        ToneDocument opened = read(saved);
        check(opened.fading() && Arrays.equals(page(opened), page(d)) && Arrays.equals(file(opened), saved), "a page saved mid-fade opens as saved and saves again byte for byte");
        // Closed 15 s; opened, it shows what the page kept open all along shows.
        opened.fade(clock + 24_000, null); same.fade(clock + 24_000, null);
        check(Arrays.equals(page(opened), page(same)) && dark(opened) < dark(d), "opened 15 s later it has gone on by the clock, as one kept open");
        ToneDocument late = read(saved);
        late.fade(clock + 3_600_000, null);
        check(!late.fading() && Arrays.equals(page(late), page(InkWashLayers.blank(W, H))) && Arrays.equals(file(late), file(InkWashLayers.blank(W, H))), "opened an hour later it is gone, from the page and the file");
    }
    /** Lemon juice starts at about a tenth of its darkness and ends exactly as Quick lays it. */
    static void lemon() throws IOException {
        clock = 4_000_000;
        ToneDocument d = drawn(brush(PartChoice.LEMON, 300)), quick = drawn(brush(PartChoice.FAST_WET, 0));
        long full = dark(quick), start = dark(d);
        check(d.fading() && start > full * .06 && start < full * .16, "lemon juice goes on faint: " + start + " of " + full);
        d.fade(clock + 150_000, null);
        long mid = dark(d);
        check(mid > start && mid < full, "it darkens in: " + mid + " at 2.5 min");
        ToneDocument opened = read(file(d));
        opened.fade(clock + 400_000, null); d.fade(clock + 400_000, null);
        check(!d.fading() && Arrays.equals(page(d), page(quick)), "after Dries In times 1.3 it is an ordinary dried mark, exactly as Quick lays it");
        check(Arrays.equals(page(opened), page(quick)), "also from a page saved mid-way (" + dark(opened) + " of " + full + ", " + opened.fading() + ")");
        check(Arrays.equals(file(d), file(quick)), "its file is Quick's");
    }
    /** Undo during the fade takes the stroke; Redo gives it as the clock has it; once gone nothing brings it back. */
    static void undo() {
        clock = 5_000_000;
        ToneDocument d = drawn(brush(PartChoice.FADE, 20)), same = drawn(brush(PartChoice.FADE, 20)), blank = InkWashLayers.blank(W, H);
        d.fade(clock + 10_000, null); same.fade(clock + 10_000, null);
        check(d.undo() && Arrays.equals(page(d), page(blank)), "Undo mid-fade takes the stroke");
        d.fade(clock + 14_000, null); same.fade(clock + 14_000, null);
        check(Arrays.equals(page(d), page(blank)) && d.redo() && Arrays.equals(page(d), page(same)), "Redo gives it back as the clock has it, not as laid");
        d.undo();
        d.fade(clock + 30_000, null);
        check(!d.fading() && !d.canRedo() && !d.redo() && Arrays.equals(page(d), page(blank)), "once gone Redo has nothing to bring back");
        ToneDocument l = drawn(brush(PartChoice.LEMON, 300)), q = drawn(brush(PartChoice.FAST_WET, 0));
        check(l.undo() && l.redo() && dark(l) < dark(q) * .16, "Redo gives lemon juice back faint, as the clock has it");
    }
    /** Ink drawn over a fading mark stays when the mark has gone, and Undo of it shows the mark as the clock has it. */
    static void drawnOver() {
        clock = 6_000_000;
        ToneDocument d = drawn(brush(PartChoice.FADE, 20)), ink = InkWashLayers.blank(W, H);
        double[][] across = {{200, 20, .8, 1000}, {200, 80, .8, 1010}, {200, 140, .8, 1020}, {200, 220, .8, 1030}};
        draw(d, brush(PartChoice.FAST_WET, 0), across);
        draw(ink, brush(PartChoice.FAST_WET, 0), across);
        d.fade(clock + 30_000, null);
        int[] a = page(d), b = page(ink);
        int kept = 0, inked = 0;
        for (int i = 0; i < a.length; i++) if (b[i] < 128) { inked++; if (a[i] < 128) kept++; }
        check(!d.fading() && inked > 100 && kept == inked, "ink over a fading mark stays (" + kept + " of " + inked + " px)");
        check(d.undo() && Arrays.equals(page(d), page(InkWashLayers.blank(W, H))) && !d.canUndo(), "Undo of the ink shows the page as the clock has it: the mark gone too, its step with it");
    }
    /** Fresh fading ink over a fading mark wets it again: where they cross both go together, and in the end nothing is left. */
    static void rewet() {
        clock = 6_500_000;
        ToneDocument d = drawn(brush(PartChoice.FADE, 20));
        d.fade(clock + 10_000, null);
        long at = clock + 10_000;
        clock = at;
        double[][] across = {{200, 20, .8, 1000}, {200, 80, .8, 1010}, {200, 140, .8, 1020}, {200, 220, .8, 1030}};
        draw(d, brush(PartChoice.FADE, 20), across);
        check(d.fades().size() == 2, "two marks fading");
        d.fade(at + 16_000, null);
        check(d.fades().size() == 1, "the first is done, the second still fading");
        d.fade(at + 26_000, null);
        check(!d.fading() && Arrays.equals(page(d), page(InkWashLayers.blank(W, H))) && !d.canUndo(), "where they crossed nothing is left of either, nor any step to undo");
    }
    /** A wide fading wash across a Manta page steps quickly enough for the drawing thread. */
    static void speed() {
        clock = 9_000_000;
        int w = 1404, h = 1872;
        ToneDocument d = InkWashLayers.blank(w, h);
        double[][] path = new double[121][];
        for (int i = 0; i <= 120; i++) path[i] = new double[]{100 + 10 * i, 300 + 1200 * i / 120.0 + 200 * Math.sin(i / 12.0), .8, 1000 + 8 * i};
        CarrySmudgeChecks.feed(ModularStroke.start(d, brush(PartChoice.FADE, 20), 160, 1, new WatercolorSession()), path);
        long most = 0;
        for (long t = clock + 2000; d.fading(); t += 2000) { long t0 = System.nanoTime(); d.fade(t, null); most = Math.max(most, System.nanoTime() - t0); }
        Timing.limit(most < 400_000_000L, "a page-wide fading stroke's slowest step: " + most / 1_000_000 + " ms");
        System.out.println("Fading: a 160 px stroke across a Manta page steps in at most " + most / 1_000_000 + " ms on this machine");
    }
    /** Nothing changes by the clock on a page without fading marks. */
    static void idle() {
        clock = 7_000_000;
        ToneDocument d = drawn(brush(PartChoice.FAST_WET, 0));
        int[] before = page(d);
        check(!d.fading() && d.nextFade() == Long.MAX_VALUE && d.fade(clock + 60_000, null) == 0 && Arrays.equals(page(d), before), "a page without them has nothing to step");
        ToneDocument sample = InkWashLayers.blank(W, H);
        CarrySmudgeChecks.feed(ModularStroke.start(sample, brush(PartChoice.LEMON, 300), 14, 1, null), line(40, 360, 120));
        check(!sample.fading(), "a sample (no session) is laid as it ends, nothing to tick");
    }
    /** Coarse steps for e-ink: Lemon Juice a twentieth of its time, 1 to 15 s, Fading a tenth of its time, 2 to 5 s; the next due when it says. */
    static void steps() {
        check(FadingInk.stepMs(true, 4000) == 1000 && FadingInk.stepMs(true, 300_000) == 15_000 && FadingInk.stepMs(false, 10_000) == 2000 && FadingInk.stepMs(false, 30_000) == 3000 && FadingInk.stepMs(false, 120_000) == 5000, "lemon juice's steps of 1 to 15 s, fading's 2 to 5 s");
        clock = 8_000_000;
        ToneDocument d = drawn(brush(PartChoice.FADE, 20));
        check(d.nextFade() == clock + 2000, "the next step is due 2 s after the lift: " + (d.nextFade() - clock));
        int steps = 0;
        for (long t = clock; d.fading() && t < clock + 60_000; t += 100) if (d.nextFade() <= t) { d.fade(t, null); steps++; }
        check(steps >= 10 && steps <= 14, "a 20 s fade steps " + steps + " times");
    }

    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
