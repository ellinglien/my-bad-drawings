package io.github.mpdairy.monopaint;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;

/**
 * {@link ModularPerfBench} on the device, with the drawing screen's dirty-rectangle render into a page-sized bitmap.
 * Run by scripts/bench_device.sh (instrumentation argument modularPerf, on the bench build: -Pbench).
 */
final class ModularPerfChecks {
    static void run(android.app.Instrumentation in, Bundle args, StringBuilder report) throws Exception {
        android.content.Context target = in.getTargetContext();
        Assets.install(target.getAssets()::open);
        // Heap under load (StabilityBench): -e stability wash,session [-e presets "Soft wash,Sumi"] [-e actions 120].
        String stability = args.getString("stability");
        if (stability != null) {
            String actions = args.getString("actions");
            StabilityBench.run(stability, args.getString("presets"), actions == null ? 120 : Integer.parseInt(actions), report, () -> {
                for (String line : report.toString().split("\n")) Log.i("InkBench", line);
                report.setLength(0);
            });
            return;
        }
        if ("false".equals(args.getString("paced"))) { ModularPerfBench.PACED = false; report.append("unpaced\n"); }
        String input = args.getString("input");
        if (input != null) ModularPerfBench.INPUT = ModularPerfBench.Input.valueOf(input.toUpperCase(java.util.Locale.ROOT));
        report.append("input ").append(ModularPerfBench.INPUT).append(ModularPerfBench.PACED ? ", paced" : "").append('\n');
        if ("true".equals(args.getString("marks"))) { ModularPerfBench.MARKS = true; report.append("over marks\n"); }
        String marksErasable = args.getString("marksErasable"), erasable = args.getString("erasable");
        if (marksErasable != null) { ModularPerfBench.MARKS_ERASABLE = Double.parseDouble(marksErasable); report.append("marks Erasable ").append(marksErasable).append('\n'); }
        if (erasable != null) { ModularPerfBench.ERASABLE = Double.valueOf(erasable); report.append("every brush Erasable ").append(erasable).append('\n'); }
        String quick = args.getString("quick");
        if (quick != null) { ModularPerfBench.QUICK = quick; report.append("quick ").append(quick).append('\n'); }
        // Draw once (the wet paint spec's experiment): Pastels and Dry brush with no preview, each pixel written once.
        if ("true".equals(args.getString("drawOnce"))) { QuickStroke.drawOnce = true; report.append("draw once\n"); }
        String lattice = args.getString("lattice");
        if (lattice != null) { ModularStroke.latticePx = Double.parseDouble(lattice); report.append("lattice past ").append(lattice).append(" px\n"); }
        Bitmap display = Bitmap.createBitmap(ModularPerfBench.W, ModularPerfBench.H, Bitmap.Config.ARGB_8888);
        boolean noRender = "true".equals(args.getString("noRender"));
        ModularPerfBench.Render render = noRender ? null : (d, dirty) -> {
            long t0 = System.nanoTime();
            Rect r = new Rect(Math.max(0, dirty[0] - 2), Math.max(0, dirty[1] - 2), Math.min(d.width, dirty[2] + 2), Math.min(d.height, dirty[3] + 2));
            ViewportBitmap.compose(d, display, true, r);
            return System.nanoTime() - t0;
        };
        String s = args.getString("styles"), z = args.getString("sizes"), l = args.getString("lives"), ms = args.getString("ms");
        String[] styles = s == null ? null : s.split(",");
        double[] sizes = null;
        if ("presets".equals(z)) sizes = new double[0];
        else if ("edge".equals(z)) sizes = new double[]{Double.NaN};
        else if (z != null) { String[] q = z.split(","); sizes = new double[q.length]; for (int i = 0; i < q.length; i++) sizes[i] = Double.parseDouble(q[i]); }
        PartChoice[] lives = l == null ? null : ModularPerfBench.lives(l);
        String gravity = args.getString("gravity");
        if (gravity != null) {
            // Gravity's ticks: -e gravity 60,120,240 (sizes), -e wide 600 (a wave that wide; else the S-curve over the page).
            String[] q = gravity.split(",");
            double[] sz = new double[q.length];
            for (int i = 0; i < q.length; i++) sz[i] = Double.parseDouble(q[i]);
            String wide = args.getString("wide");
            if ("true".equals(args.getString("level"))) { ModularPerfBench.GRAVITY_LEVEL = true; report.append("level page\n"); }
            String only = args.getString("names");
            if (only != null) ModularPerfBench.GRAVITY_NAMES = only.split(",");
            ModularPerfBench.gravity(report, sz, wide == null ? 0 : Integer.parseInt(wide), render, () -> {
                for (String line : report.toString().split("\n")) Log.i("InkBench", line);
                report.setLength(0);
                Log.i("InkBench", "--");
            });
            for (String line : report.toString().split("\n")) Log.i("InkBench", line);
            return;
        }
        if ("true".equals(args.getString("eraser"))) {
            // The eraser over pages with and without shares, and the heap a pair's shares take (-e eraser true).
            ModularPerfBench.eraserBench(report, () -> {
                for (String line : report.toString().split("\n")) Log.i("InkBench", line);
                report.setLength(0);
                Log.i("InkBench", "--");
            });
            for (String line : report.toString().split("\n")) Log.i("InkBench", line);
            return;
        }
        if ("true".equals(args.getString("wetPaint"))) {
            // Wet paint (the wet paint spec): the Wet presets' pen and their paint's movement after (-e wetPaint true,
            // -e sizes 60,120,240, -e names "Wet gouache,Wet oils").
            String only = args.getString("names");
            if (only != null) ModularPerfBench.WET_NAMES = only.split(",");
            ModularPerfBench.wetBench(report, sizes == null || sizes.length == 0 ? new double[]{60, 120, 240} : sizes, render, () -> {
                for (String line : report.toString().split("\n")) Log.i("InkBench", line);
                report.setLength(0);
                Log.i("InkBench", "--");
            });
            for (String line : report.toString().split("\n")) Log.i("InkBench", line);
            return;
        }
        if ("true".equals(args.getString("float"))) {
            // Float's costs and its drags over a bath (-e float true).
            ModularPerfBench.floatBench(report, render, () -> {
                for (String line : report.toString().split("\n")) Log.i("InkBench", line);
                report.setLength(0);
                Log.i("InkBench", "--");
            });
            for (String line : report.toString().split("\n")) Log.i("InkBench", line);
            return;
        }
        String trace = args.getString("trace");
        java.io.File dir = target.getExternalFilesDir(null);
        if (trace != null) {
            // Warm up, then one sampled method trace of the first config.
            ModularBrush b = ModularPerfBench.brush(styles[0], lives[0]);
            ModularPerfBench.run(b, sizes[0], render, 1500); ModularPerfBench.run(b, sizes[0], render, 1500);
            java.io.File f = new java.io.File(dir, trace);
            android.os.Debug.startMethodTracingSampling(f.getPath(), 64 << 20, 500);
            ModularPerfBench.Result r = ModularPerfBench.run(b, sizes[0], render, 1500);
            android.os.Debug.stopMethodTracing();
            report.append(ModularPerfBench.header()).append(ModularPerfBench.row(styles[0], lives[0].name(), sizes[0], r)).append("trace: ").append(f).append('\n');
            return;
        }
        ModularPerfBench.matrix(report, styles, sizes, lives, render, ms == null ? 1500 : Double.parseDouble(ms), () -> {
            // Progress in logcat as the matrix runs.
            for (String line : report.toString().split("\n")) Log.i("InkBench", line);
            report.setLength(0);
            Log.i("InkBench", "--");
        });
        for (String line : report.toString().split("\n")) Log.i("InkBench", line);
    }
}
