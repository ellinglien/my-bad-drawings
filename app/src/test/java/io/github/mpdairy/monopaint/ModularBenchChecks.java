package io.github.mpdairy.monopaint;

import java.util.Arrays;
import java.util.Locale;
import java.util.Random;

/**
 * Desktop timing of every style's brush on a 1400 × 900 wave with tilt, three samples an event, after warm-up strokes:
 * Fast-wet at 16, 120 and 360 px, and each style's Drying lift at 16 and 120 px. The Manta runs this engine about 20
 * times slower than this Mac (scripts/bench_device.sh against scripts/bench_desktop.sh), so a mean of 0.1 ms a sample
 * here is about all of a 480 Hz pen's time there. The budgets sit about twice over the slowest style here (Charcoal,
 * Pastels and Pencil, the device's slowest too): a change that doubles a style's cost fails. A case over budget is
 * timed once more before it fails. Any argument prints the table.
 */
public final class ModularBenchChecks {
    /** Per size: mean ms a sample, p95 ms a sample. */
    static final double[][] BUDGET = {{16, .3, 1.2}, {120, .5, 2}, {360, 1.5, 7}};
    /** Lift ms: Fast-wet (nothing to do), a Drying lift (lays the end again, settles, composes). */
    static final double LIFT = 10, DRYING_LIFT = 100;

    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        StringBuilder table = new StringBuilder();
        for (ModularStyle style : ModularStyle.all()) {
            ModularBrush b = style.roll(new Random(100 + style.index));
            ModularBrush.Part life = new ModularBrush.Part(PartChoice.FAST_WET); b.set(BrushPart.LIFE, life);
            for (double[] budget : BUDGET) {
                double size = budget[0];
                // Two warm-up strokes: a style's first stroke often meets code the JIT compiled for another style's
                // branches, and its recompiling would be timed with the second.
                run(b, size); run(b, size);
                double[] t = run(b, size);
                // Over budget, once more: the JIT recompiling for this style, or the machine busy, shows in one stroke, not two.
                if (t[0] > budget[1] || t[1] > budget[2]) { run(b, size); t = best(t, run(b, size)); }
                table.append(String.format(Locale.ROOT, "%-13s %-28s %4.0f px  mean %6.3f ms  p95 %6.3f ms  lift %5.1f ms%n", style.name,
                        ModularWords.choiceWords(b, BrushPart.TIP) + " · " + ModularWords.choiceWords(b, BrushPart.MEDIUM), size, t[0], t[1], t[2]));
                Timing.limit(t[0] <= budget[1] && t[1] <= budget[2], style.name + " at " + size + " px is within budget (mean " + budget[1] + ", p95 " + budget[2] + " ms):\n" + table);
                Timing.limit(t[2] <= LIFT, style.name + " lifts within " + LIFT + " ms:\n" + table);
            }
            ModularBrush drying = b.copy();
            ModularBrush.Part d = new ModularBrush.Part(PartChoice.DRYING); d.set(PartDial.SETTLE, .5); drying.set(BrushPart.LIFE, d);
            for (double size : new double[]{16, 120}) {
                run(drying, size);
                double[] t = run(drying, size);
                if (t[2] > DRYING_LIFT) t = best(t, run(drying, size));
                table.append(String.format(Locale.ROOT, "%-13s Drying %4.0f px lift %5.1f ms%n", style.name, size, t[2]));
                Timing.limit(t[2] <= DRYING_LIFT, style.name + " in Drying lifts within " + DRYING_LIFT + " ms at " + size + " px:\n" + table);
            }
        }
        carry(table);
        erasable(table);
        if (args.length > 0) System.out.print(table);
        System.out.println("PASS: every style draws a 1400 × 900 wave within the desktop budgets, about twice its cost and a twentieth of the Manta's (16 px: mean ≤ 0.3 ms, p95 ≤ 1.2 ms a sample; 120 px: 0.5, 2; 360 px: 1.5, 7; lift ≤ 10 ms, a Drying lift ≤ 100 ms); Smudge's ten bench cases over marks within theirs; Erasable's three over permanent marks within theirs");
    }
    /** Each figure the lesser of two strokes'. */
    static double[] best(double[] a, double[] b) { return new double[]{Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.min(a[2], b[2])}; }
    /**
     * Smudge's cases (the Smudge spec's bench list), Fast-wet over a page of marks on the stroke's own layer (a smudge
     * over blank paper flatters), each budgeted about twice this Mac's time: name, code, size, mean and p95 ms a sample.
     */
    static final Object[][] CARRY = {
            {"Pencil Smear", "pencil-smear.M40i074000000at_4ee00_3p6js00_000_0_0000000000000000000nz0.1wyw208", 6, 0.06, 0.2},
            {"Charcoal Smear", "charcoal-smear.M50i000000000at_3atee_3p6js00_000_0_0000000000000000000nz0.13ax4gw", 30, 0.2, 0.5},
            {"Charcoal Smear", "charcoal-smear.M50i000000000at_3atee_3p6js00_000_0_0000000000000000000nz0.13ax4gw", 120, 0.7, 1.5},
            {"Stump", "stump.M50i00000000yat_3eqlj_3p6ll00_1nl_1jz_8pthh00x9is00000000nz0.51j8ud", 28, 0.3, 0.6},
            {"Stump", "stump.M50i00000000yat_3eqlj_3p6ll00_1nl_1jz_8pthh00x9is00000000nz0.51j8ud", 90, 0.6, 1.2},
            {"Gouache Lift", "gouache-lift.M80bp000000004t_2zz00_177at_000_0_0000000000000000000nz0.13ztx3e", 60, 0.1, 0.4},
            {"Gouache Lift", "gouache-lift.M80bp000000004t_2zz00_177at_000_0_0000000000000000000nz0.13ztx3e", 240, 0.1, 0.4},
            {"Oils Drag", "oils-drag.M90i00000000042_2zz00_2llp600_000_0_0000000000000000000nz0.1vtkv3y", 60, 0.25, 0.8},
            {"Oils Drag", "oils-drag.M90i00000000042_2zz00_2llp600_000_0_0000000000000000000nz0.1vtkv3y", 240, 0.75, 1.5},
            {"Hake Drag", "hake-drag.Mc3i00zzhf59_1orat00770000_2llp6ul_000_1jz_85b0000000000000000nz0.t4mf73", 360, 1.4, 3},
    };
    static void carry(StringBuilder table) {
        for (Object[] c : CARRY) {
            ModularBrush b = ModularCode.decode((String)c[1]);
            b.set(BrushPart.LIFE, new ModularBrush.Part(PartChoice.FAST_WET));
            double size = ((Number)c[2]).doubleValue(), mean = ((Number)c[3]).doubleValue(), p95 = ((Number)c[4]).doubleValue();
            run(b, size, true); run(b, size, true);
            double[] t = run(b, size, true);
            if (t[0] > mean || t[1] > p95) { run(b, size, true); t = best(t, run(b, size, true)); }
            table.append(String.format(Locale.ROOT, "%-13s %-28s %4.0f px  mean %6.3f ms  p95 %6.3f ms  lift %5.1f ms  (over marks)%n", "Smudge", c[0], size, t[0], t[1], t[2]));
            Timing.limit(t[0] <= mean && t[1] <= p95 && t[2] <= LIFT, c[0] + " at " + size + " px is within budget (mean " + mean + ", p95 " + p95 + " ms):\n" + table);
        }
    }
    /**
     * Erasable's cases (the Erasable spec's check 12): Fast-wet over the marks laid permanent (the stroke's layer has a
     * share, every write keeps it), each budgeted about twice this Mac's time: preset, size, mean and p95 ms a sample.
     */
    static final Object[][] ERASABLE = {{"Steady line", 8, 0.06, 0.25}, {"Soft wash", 120, 0.2, 0.8}, {"Stump", 90, 0.6, 1.2}};
    static void erasable(StringBuilder table) {
        for (Object[] c : ERASABLE) {
            ModularBrush b = GravityChecks.preset((String)c[0]);
            b.set(BrushPart.LIFE, new ModularBrush.Part(PartChoice.FAST_WET));
            double size = ((Number)c[1]).doubleValue(), mean = ((Number)c[2]).doubleValue(), p95 = ((Number)c[3]).doubleValue();
            run(b, size, true, 0); run(b, size, true, 0);
            double[] t = run(b, size, true, 0);
            if (t[0] > mean || t[1] > p95) { run(b, size, true, 0); t = best(t, run(b, size, true, 0)); }
            table.append(String.format(Locale.ROOT, "%-13s %-28s %4.0f px  mean %6.3f ms  p95 %6.3f ms  lift %5.1f ms  (over permanent marks)%n", "Erasable", c[0], size, t[0], t[1], t[2]));
            Timing.limit(t[0] <= mean && t[1] <= p95 && t[2] <= LIFT, c[0] + " over permanent marks at " + size + " px is within budget (mean " + mean + ", p95 " + p95 + " ms):\n" + table);
        }
    }
    /** {mean ms a sample, p95 ms a sample, lift ms}. */
    static double[] run(ModularBrush b, double size) { return run(b, size, false); }
    /** As {@link #run(ModularBrush, double)}; {@code marked}: over hatching and bands on the stroke's own layer. */
    static double[] run(ModularBrush b, double size, boolean marked) { return run(b, size, marked, 1); }
    /** As {@link #run(ModularBrush, double, boolean)}, the marks as erasable as {@code marks} (below 1: the layer has a share). */
    static double[] run(ModularBrush b, double size, boolean marked, double marks) {
        PartChoice m = b.choice(BrushPart.MEDIUM);
        ToneDocument d = InkWashLayers.blank(1400, 900);
        d.selectLayer(m == PartChoice.WASH || m == PartChoice.COVER ? InkWashLayers.WASH : InkWashLayers.INK);
        if (marked) {
            d.begin(marks);
            int[] row = new int[1400];
            for (int y = 0; y < 900; y++) {
                for (int x = 0; x < 1400; x++) row[x] = Math.floorMod(x + y / 3, 12) < 3 ? 70 : x / 100 % 3 == 1 ? 150 : -1;
                d.setTones(0, y, 1400, 1, row);
            }
            d.finish();
        }
        ModularStroke s = new ModularStroke(d, b, size, 1, new WatercolorSession());
        int n = (int)Math.ceil(1320 / 3.0);
        double[] per = new double[(n + 3) / 3]; int events = 0; long time = 0;
        for (int i = 0; i <= n; i += 3) {
            long start = System.nanoTime();
            for (int j = i; j < Math.min(n + 1, i + 3); j++) {
                double u = (double)j / n, env = Math.sin(Math.PI * u);
                time += Math.round(2 + 4 * (1 - env));
                s.sample((float)(40 + 1320 * u), (float)(450 + (size > 100 ? 220 : 300) * Math.sin(2.5 * Math.PI * u)), ToolSettings.pressure((float)(.15 + .75 * Math.pow(env, .7))),
                        (float)(30 * Math.cos(3 * u)), 25, time);
            }
            s.present();
            if (events < per.length) per[events++] = (System.nanoTime() - start) / 3e6;
        }
        long lift = System.nanoTime();
        s.finish();
        double liftMs = (System.nanoTime() - lift) / 1e6;
        double[] sorted = Arrays.copyOf(per, events); Arrays.sort(sorted);
        double sum = 0; for (double v : sorted) sum += v;
        return new double[]{sum / events, sorted[(int)Math.floor(events * .95)], liftMs};
    }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
