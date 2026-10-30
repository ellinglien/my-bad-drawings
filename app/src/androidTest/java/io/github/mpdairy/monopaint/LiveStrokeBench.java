package io.github.mpdairy.monopaint;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Matrix;
import android.os.SystemClock;
import android.util.Log;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Locale;

/**
 * Real strokes on the bench build's own drawing screen (ing.ell.mybaddrawings.bench, never the user's app): a six-part
 * brush in slot 1 on a blank page, and a 1.5 s S-curve sent as the Manta's pen sends it, one sample an event at 480 Hz,
 * not waiting for the app. DrawingPad logs each stroke's timing ("stroke timing", tag MonoPaint; with this build's
 * batch lag and the panel driver's share of each present); this reports how long after the pen lifted the screen was
 * idle again. Each case runs with the pen's samples drawn one event at a time on the gray plane (as before), batched on
 * the gray plane, and batched with the stroke on the panel's fast update ({@link StrokePanel}); after each, how long the
 * panel still drove frames ({@link PanelFrames}).
 * Arguments: -e liveStrokes true [-e styles "Ink wash,Brush pen"] [-e sizes 240,360] [-e lives wet,dry] [-e modes gray,fast,livegray,livefast,quickgray,quickfast,sync];
 * -e hold ms: only hold a blank page on the panel that long (for a panel experiment run beside it).
 * -e settle true: wet paint after lift ({@link #settle}): [-e cases "Watercolor:120,@Drips:120:tilt,float"] [-e delays 300,700].
 * -e cases without -e settle: chosen strokes on either screen ({@link #cases}).
 * -e book "name": a long session on one page of a notebook copied into the bench library, for the heap ({@link #heapSession}).
 */
final class LiveStrokeBench {
    static void run(Instrumentation test, android.os.Bundle args, StringBuilder report) throws Exception {
        String st = args.getString("styles"), sz = args.getString("sizes"), lv = args.getString("lives"), md = args.getString("modes");
        String[] styles = st == null ? new String[]{"Ink wash", "Watercolor", "Brush pen", "Oils"} : st.split(",");
        String[] sizes = (sz == null ? "240,360" : sz).split(",");
        PartChoice[] lives = ModularPerfBench.lives(lv == null ? "wet" : lv);
        String[] modes = (md == null ? "gray,fast" : md).split(",");
        if (args.getString("cases") != null && args.getString("settle") == null) { cases(test, args, report); return; }
        if (args.getString("book") != null) { heapSession(test, args, report); return; }
        PaintActivity activity = (PaintActivity)test.startActivitySync(new Intent(test.getTargetContext(), PaintActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        front = activity;
        for (int i = 0; i < 100 && (Boolean)get(activity, "loading"); i++) SystemClock.sleep(100);
        test.waitForIdleSync(); SystemClock.sleep(500);
        Object pad = get(activity, "pad");
        InkWash ink = (InkWash)get(activity, "inkWash");
        int[] page = new int[2];
        openPage(test, activity);
        main(test, () -> { ToneDocument d = (ToneDocument)get(pad, "document"); page[0] = d.width; page[1] = d.height; });
        report.append("page ").append(page[0]).append('x').append(page[1]).append(", firmware ").append(android.os.Build.DISPLAY).append('\n');
        if ("true".equals(args.getString("settle"))) { settle(test, args, report, activity, pad, ink, page); activity.finish(); return; }
        String hold = args.getString("hold");
        if (hold != null) {
            // A blank page held on the panel for a panel experiment run from the shell (scratch tools), then closed.
            main(test, () -> call(activity, "replaceBook", new Class<?>[]{DrawingBook.class}, new DrawingBook(InkWashLayers.blank(page[0], page[1]))));
            test.waitForIdleSync(); SystemClock.sleep(1000);
            Log.i("InkBench", "holding a blank page " + hold + " ms");
            SystemClock.sleep(Long.parseLong(hold));
            activity.finish();
            return;
        }
        for (String style : styles) for (PartChoice life : lives) for (String size : sizes) for (String mode : modes) {
            ModularBrush b = ModularPerfBench.brush(style, life);
            double px = Double.parseDouble(size);
            main(test, () -> {
                call(pad, "finishStroke");
                setStatic(DrawingPad.class, "penBatching", !mode.equals("sync"));
                // What draws and how it shows: "gray" the app's rule (live or quick) on the gray update only, "fast" as the
                // app draws (the rule, StrokePanel's fast update while the pen is down); "live"/"quick" + "gray"/"fast",
                // e.g. livegray (every stroke live on the gray update: as before quick strokes) or quickfast.
                setObject(QuickStroke.class, "benchAlways", mode.startsWith("live") ? Boolean.FALSE : mode.startsWith("quick") ? Boolean.TRUE : null);
                // The fast path is the bench's only (StrokePanel.wants): "…fast" puts quick strokes on it, "livefast" every stroke.
                setObject(StrokePanel.class, "bench", mode.endsWith("gray") ? "gray" : mode.startsWith("live") ? "all" : "quick");
                call(activity, "replaceBook", new Class<?>[]{DrawingBook.class}, new DrawingBook(InkWashLayers.blank(page[0], page[1])));
                ink.slots.use(0, Brush.of(b));
                ink.slots.swatches.setSize(ToolSlots.key(0), 2, px);
                ink.select(0);
            });
            test.waitForIdleSync(); SystemClock.sleep(1500);
            long framesBefore = PanelFrames.count();
            long down = SystemClock.uptimeMillis();
            long lifted = stroke(test, pad, page);
            long framesDown = framesBefore < 0 ? -1 : PanelFrames.count() - framesBefore;
            test.waitForIdleSync();
            long idle = SystemClock.uptimeMillis() - lifted;
            // The panel's own lag: how long after the lift it still drove frames (it shows the stroke when they stop).
            long[] panel = PanelFrames.settle(framesBefore, 80, 15000);
            // Where the app can't read the controller's frames, the pen's times (uptime ms) let a shell frame log be matched.
            String line = String.format(Locale.ROOT, "%-11s %-8s %4s %-9s idle after lift %4d ms, panel after lift %5d ms (frames %d down, %d in all) down=%d lift=%d",
                    style, life == PartChoice.FAST_WET ? "FastWet" : life == PartChoice.DRYING ? "Drying" : "None", size, mode, idle,
                    panel == null ? -1 : panel[0] - lifted, framesDown, panel == null ? -1 : panel[1], down, lifted);
            Log.i("InkBench", line);
            report.append(line).append('\n');
            // Let a Drying wash settle and the panel rest before the next.
            SystemClock.sleep(2500);
        }
        main(test, () -> {
            setStatic(DrawingPad.class, "penBatching", true);
            setObject(QuickStroke.class, "benchAlways", null); setObject(StrokePanel.class, "bench", null); call(pad, "finishStroke");
        });
        activity.finish();
    }

    /**
     * Wet paint after lift on the drawing screen, each case on a blank page: a Slow wash ("Style:px", the bench's
     * S-curve as real pen events, its Drying Settle .5; "@Preset:px", a Tools page preset as it is; ":tilt", the page
     * tilted (0, .8) all along), or "float", a medium Float bath (ModularPerfBench's: Sumi and Clear water taps at three
     * sites) drifting on a page so tilted. First it settles alone: how long, its ticks (their work, on the page's thread
     * or there and the write here), and how long the page's thread was held at a time (every message on its looper).
     * Then for each delay a fresh one, the pen put down that long after the lift's work is done (a short stroke with the
     * same slot; for the bath, the Watercolor's): how long from the pen's down until its stroke had begun on the page's
     * thread (its wait there, and what is wet laid or printed first).
     */
    static void settle(Instrumentation test, android.os.Bundle args, StringBuilder report, PaintActivity activity, Object pad, InkWash ink, int[] page) throws Exception {
        String cs = args.getString("cases"), ds = args.getString("delays");
        String[] cases = (cs == null ? "Watercolor:120,Watercolor:240,Watercolor:360,@Drips:120:tilt,float" : cs).split(",");
        String[] delays = (ds == null ? "150,400,800,1300,2000" : ds).split(",");
        WatercolorSession session = ink.session;
        java.util.List<String> ticks = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        main(test, () -> {
            setStatic(WatercolorSession.class, "timing", true);
            session.log = m -> { if (m.startsWith("settle ")) ticks.add(m); if (trace) Log.i("InkBench", "  +" + (SystemClock.uptimeMillis() - traceFrom) + " " + m); };
        });
        trace = "true".equals(args.getString("trace"));
        Looped looped = new Looped();
        android.os.Looper.getMainLooper().setMessageLogging(looped);
        try {
            for (String c : cases) {
                String[] q = c.split(":");
                boolean bath = q[0].equals("float"), tilt = bath || c.endsWith(":tilt");
                double px = bath ? 120 : Double.parseDouble(q[1]);
                // Where the pen goes down: worked out now, so the down is sent on time whatever the page's thread is doing.
                float[] downAt = {page[0] * .5f, page[1] * .3f};
                main(test, () -> {
                    Matrix toView = (Matrix)get(pad, "pageToView");
                    Matrix toPanel = PanelCoordinates.fromView((View)pad);
                    toView.mapPoints(downAt); toPanel.mapPoints(downAt);
                });
                for (int run = -1; run < delays.length; run++) {
                    long delay = run < 0 ? -1 : Long.parseLong(delays[run]);
                    main(test, () -> {
                        call(pad, "finishStroke");
                        call(activity, "replaceBook", new Class<?>[]{DrawingBook.class}, new DrawingBook(InkWashLayers.blank(page[0], page[1])));
                        ModularBrush b = bath || q[0].startsWith("@") ? ModularPerfBench.brush(bath ? "Watercolor" : q[0], bath ? PartChoice.DRYING : null) : ModularPerfBench.brush(q[0], PartChoice.DRYING);
                        ink.slots.use(0, Brush.of(b));
                        ink.slots.swatches.setSize(ToolSlots.key(0), 2, px);
                        ink.select(0);
                        session.tilt = tilt ? () -> PageTilt.of(0, .8) : () -> PageTilt.LEVEL;
                    });
                    test.waitForIdleSync(); SystemClock.sleep(1500);
                    ticks.clear();
                    traceFrom = SystemClock.uptimeMillis();
                    long lifted;
                    if (bath) {
                        // The bath laid on the page as the bench lays it, then its ticks started as after a stroke.
                        main(test, () -> {
                            ToneDocument d = (ToneDocument)get(pad, "document");
                            ModularPerfBench.bath(d, session, 3, 12, 8);
                            d.clearDirty();
                            call(pad, "renderAll"); call(pad, "flush", new Class<?>[]{boolean.class}, true);
                            Object t = call(pad, "dryingTicker");
                            call(t, "start");
                        });
                        lifted = SystemClock.uptimeMillis();
                    } else {
                        stroke(test, pad, page);
                        // The lift's own work (a Slow wash lifts off the page's thread) done: the wash settles from here.
                        while (get(pad, "stroke") != null) SystemClock.sleep(1);
                        lifted = SystemClock.uptimeMillis();
                    }
                    looped.from(lifted);
                    if (delay < 0) {
                        long until = lifted + 120_000;
                        while (wet(session) && SystemClock.uptimeMillis() < until) SystemClock.sleep(5);
                        long settled = SystemClock.uptimeMillis() - lifted;
                        SystemClock.sleep(50);
                        String line = String.format(Locale.ROOT, "%-16s settles in %5d ms | page's thread held: longest %4d ms, p95 %3d ms of %d over 2 ms, busy %5d ms | ticker %s | there %s",
                                c, settled, looped.longest(), looped.p95(), looped.over(), looped.busy(), looped.ticks(), there(ticks));
                        Log.i("InkBench", line); report.append(line).append('\n');
                        continue;
                    }
                    long due = lifted + delay;
                    while (SystemClock.uptimeMillis() < due) SystemClock.sleep(0);
                    boolean wetThen = wet(session);
                    long held = looped.longest();
                    int seen = ticks.size();
                    long started = penDown(test, pad, downAt);
                    SystemClock.sleep(100);
                    StringBuilder laid = new StringBuilder();
                    synchronized (ticks) { for (int i = seen; i < ticks.size(); i++) if (ticks.get(i).contains(" now ")) laid.append(" [").append(ticks.get(i).substring(7)).append(']'); }
                    String line = String.format(Locale.ROOT, "%-16s pen down %5d ms after: its stroke began after %4d ms%s | page's thread held before it: longest %4d ms%s",
                            c, delay, started, wetThen ? "" : " (dry already)", held, laid);
                    Log.i("InkBench", line); report.append(line).append('\n');
                    test.waitForIdleSync(); SystemClock.sleep(800);
                }
            }
        } finally {
            android.os.Looper.getMainLooper().setMessageLogging(null);
            main(test, () -> { setStatic(WatercolorSession.class, "timing", false); call(pad, "finishStroke"); });
        }
    }
    /** Each wet-paint report as it comes, timed from {@link #traceFrom} (-e trace true). */
    private static volatile boolean trace;
    private static volatile long traceFrom;
    private static boolean wet(WatercolorSession s) throws Exception {
        java.lang.reflect.Method m = WatercolorSession.class.getDeclaredMethod("settling"); m.setAccessible(true);
        return (Boolean)m.invoke(s);
    }
    /** The ticks' work there and writes here ({@link WatercolorSession#timing}): first, mean and longest of each, ms. */
    private static String there(java.util.List<String> all) {
        java.util.List<String> t = new java.util.ArrayList<>();
        StringBuilder ahead = new StringBuilder();
        synchronized (all) { for (String m : all) { if (m.startsWith("settle tick")) t.add(m); else if (m.startsWith("settle ahead")) ahead.append(' ').append(m.substring(13)); } }
        if (t.isEmpty()) return "-";
        double[] work = new double[t.size()], with = new double[t.size()], write = new double[t.size()];
        for (int i = 0; i < t.size(); i++) {
            String[] w = t.get(i).split("[ =]");
            for (int k = 0; k + 1 < w.length; k++) {
                if (w[k].equals("work_ms")) work[i] = Double.parseDouble(w[k + 1]);
                if (w[k].equals("ahead_ms")) with[i] = Double.parseDouble(w[k + 1]);
                // Here: the write and the show.
                if (w[k].equals("write_ms") || w[k].equals("show_ms")) write[i] += Double.parseDouble(w[k + 1]);
            }
        }
        return String.format(Locale.ROOT, "%d ticks, work first %.0f mean %.0f max %.0f ms (and laying after it ahead, mean %.0f ms), written and shown here first %.0f mean %.0f max %.0f ms | ahead:%s", t.size(),
                work[0], mean(work, 1), max(work, 1), mean(with, 1), write[0], mean(write, 1), max(write, 1), ahead.length() > 400 ? ahead.substring(0, 400) + "…" : ahead);
    }
    private static double mean(double[] a, int from) { double s = 0; int n = 0; for (int i = from; i < a.length; i++) { s += a[i]; n++; } return n == 0 ? 0 : s / n; }
    private static double max(double[] a, int from) { double m = 0; for (int i = from; i < a.length; i++) m = Math.max(m, a[i]); return m; }
    /** A short stroke put down at {@code at} (the panel's pixels): returns how long from the pen's down until its stroke had begun, ms. */
    private static long penDown(Instrumentation test, Object pad, float[] at) throws Exception {
        guard(test);
        long down = SystemClock.uptimeMillis(), t0 = System.nanoTime();
        pen(test, MotionEvent.ACTION_DOWN, down, at[0], at[1], .5f);
        while (get(pad, "stroke") == null && System.nanoTime() - t0 < 10_000_000_000L) SystemClock.sleep(1);
        long began = (System.nanoTime() - t0) / 1_000_000;
        for (int i = 1; i <= 40; i++) {
            long due = down + i * 4L;
            while (SystemClock.uptimeMillis() < due) SystemClock.sleep(0);
            pen(test, i == 40 ? MotionEvent.ACTION_UP : MotionEvent.ACTION_MOVE, down, at[0] + 3 * i, at[1] + i, .5f);
        }
        return began;
    }
    /**
     * Every message the page's thread handles, timed ({@link android.os.Looper#setMessageLogging}): how long it is held at
     * a time from a moment on, and the drying ticker's ticks (on that thread before the work moved off it).
     */
    private static final class Looped implements android.util.Printer {
        private final long[] took = new long[100_000];
        private final boolean[] tick = new boolean[100_000];
        private volatile int n;
        private long start, since;
        private boolean ticking;
        synchronized void from(long uptime) { since = uptime; n = 0; }
        @Override public synchronized void println(String x) {
            long now = SystemClock.uptimeMillis();
            if (x.startsWith(">")) { start = now; ticking = x.contains("DryingTicker"); return; }
            if (start < since || n >= took.length) return;
            took[n] = now - start; tick[n] = ticking; n++;
        }
        synchronized long longest() { long m = 0; for (int i = 0; i < n; i++) m = Math.max(m, took[i]); return m; }
        synchronized long busy() { long s = 0; for (int i = 0; i < n; i++) s += took[i]; return s; }
        synchronized int over() { int c = 0; for (int i = 0; i < n; i++) if (took[i] > 2) c++; return c; }
        synchronized long p95() {
            long[] a = new long[over()]; int k = 0;
            for (int i = 0; i < n; i++) if (took[i] > 2) a[k++] = took[i];
            if (k == 0) return 0;
            Arrays.sort(a); return a[(int)(a.length * .95)];
        }
        /** The ticker's messages: how many, the first, the mean of the rest and the longest, ms. */
        synchronized String ticks() {
            int c = 0; long first = -1, sum = 0, max = 0;
            for (int i = 0; i < n; i++) if (tick[i]) { if (first < 0) first = took[i]; else { sum += took[i]; max = Math.max(max, took[i]); } c++; }
            return c == 0 ? "-" : String.format(Locale.ROOT, "%d messages, first %d mean %.0f max %d ms", c, first, c > 1 ? (double)sum / (c - 1) : 0, max);
        }
    }

    /**
     * Strokes on the drawing screen ("pad") or Brush Lab's test page ("lab"), each case
     * "Style[+smear]:life:px[:pressure[:panel[:band]]]" (-e cases "Gouache:none:120,Charcoal:wet:240,Watercolor+smear:dry:240:.5"
     * -e where pad,lab): pressure scales the S-curve's (1 as it is); panel how it shows (gray, fast, redrive, near; see
     * below); band one of -e bands (3) across the page, top first, the page cleared only before band 0 or a whole-page
     * case. Each logged when it begins and lifts; -e holdMs after each (4 s), -e finalHoldMs after the last (a page held
     * for the owner to look at). Used to find why quick strokes stayed ghosted after lift (engine spec, "Lift after the
     * fast update"), beside a frame log and the app's own logs.
     */
    static void cases(Instrumentation test, android.os.Bundle args, StringBuilder report) throws Exception {
        String[] cases = args.getString("cases").split(",");
        String[] where = (args.getString("where") == null ? "pad,lab" : args.getString("where")).split(",");
        long hold = args.getString("holdMs") == null ? 4000 : Long.parseLong(args.getString("holdMs"));
        bands = args.getString("bands") == null ? 3 : Integer.parseInt(args.getString("bands"));
        for (String w : where) {
            boolean lab = w.equals("lab");
            android.app.Activity activity = lab
                    ? test.startActivitySync(new Intent(test.getTargetContext(), BrushLabActivity.class).putExtra(BrushLabActivity.SLOT, 1).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    : test.startActivitySync(new Intent(test.getTargetContext(), PaintActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            front = activity;
            if (!lab) { for (int i = 0; i < 100 && (Boolean)get(activity, "loading"); i++) SystemClock.sleep(100); openPage(test, activity); }
            test.waitForIdleSync(); SystemClock.sleep(2500);
            Object area = lab ? get(activity, "test") : null;
            Object pad = lab ? get(area, "pad") : get(activity, "pad");
            for (String c : cases) {
                String[] q = c.split(":");
                String style = q[0].replace("+smear", "").replace("+halftone", "");
                PartChoice life = ModularPerfBench.lives(q[1])[0];
                ModularBrush b = ModularPerfBench.brush(style, life);
                if (q[0].contains("+smear")) b.set(BrushPart.CARRY, new ModularBrush.Part(PartChoice.SMUDGE));
                // A halftone Screen (the mischief modules spec): pure black and white, for the "screen" panel mode.
                if (q[0].contains("+halftone") && b.part(BrushPart.MEDIUM).has(PartDial.SCREEN)) b.part(BrushPart.MEDIUM).set(PartDial.SCREEN, 1);
                double px = Double.parseDouble(q[2]);
                float pressure = q.length > 3 ? Float.parseFloat(q[3]) : 1;
                // How the panel shows it: "gray" as the app does; on the fast path, "fast" ended the old way (one forced gray
                // update), "redrive" ended by DirectEink.endFast, "near" so asking again at a level next to the final;
                // "screen" every screened stroke, live or quick, ended by endFast (StrokePanel.wants), "screenfast" ended the
                // old way. The band: 0 to bands-1 from the top, or -1 the whole page.
                String panelMode = q.length > 4 ? q[4] : "gray";
                int band = q.length > 5 ? Integer.parseInt(q[5]) : -1;
                setObject(StrokePanel.class, "bench", panelMode.equals("gray") ? "gray" : panelMode.startsWith("screen") ? "screen" : "quick");
                StrokePanel.redrive = !panelMode.equals("fast") && !panelMode.equals("screenfast");
                StrokePanel.endNear = panelMode.equals("near");
                int[] page = new int[2];
                main(test, () -> {
                    if (lab) {
                        call(pad, "finishStroke"); if (band <= 0) call(pad, "clear");
                        ToneDocument d = (ToneDocument)call(pad, "page");
                        page[0] = d.width; page[1] = d.height;
                        ToolSwatches.Look look = new ToolSwatches.Look(InkWashSizes.Preset.LARGE, px, .8);
                        Brush brush = Brush.of(b);
                        WatercolorSession session = (WatercolorSession)get(area, "session");
                        Field f = pad.getClass().getDeclaredField("strokes"); f.setAccessible(true);
                        f.set(pad, (BrushTestPad.Strokes)(pg, eraserEnd) -> InkWash.stroke(pg, brush, look, session));
                    } else {
                        call(pad, "finishStroke");
                        ToneDocument d = (ToneDocument)get(pad, "document");
                        page[0] = d.width; page[1] = d.height;
                        InkWash ink = (InkWash)get(activity, "inkWash");
                        if (band <= 0) call(activity, "replaceBook", new Class<?>[]{DrawingBook.class}, new DrawingBook(InkWashLayers.blank(page[0], page[1])));
                        ink.slots.use(0, Brush.of(b));
                        ink.slots.swatches.setSize(ToolSlots.key(0), 2, px);
                        ink.select(0);
                    }
                });
                test.waitForIdleSync(); SystemClock.sleep(2000);
                Log.i("InkBench", "case " + w + " " + c + " begins");
                long framesBefore = PanelFrames.count();
                long lifted = strokeOn(test, pad, page, lab ? null : (Matrix)get(pad, "pageToView"), pressure, band);
                Log.i("InkBench", "case " + w + " " + c + " lifted");
                long[] panel = PanelFrames.settle(framesBefore, 100, 15000);
                String line = String.format(Locale.ROOT, "%s %-24s panel after lift %5d ms, frames %d, lift=%d", w, c, panel == null ? -1 : panel[0] - lifted, panel == null ? -1 : panel[1], lifted);
                Log.i("InkBench", line); report.append(line).append('\n');
                SystemClock.sleep(hold);
            }
            String fh = args.getString("finalHoldMs");
            if (fh != null) { Log.i("InkBench", "holding the screen " + fh + " ms"); SystemClock.sleep(Long.parseLong(fh)); }
            main(test, () -> call(pad, "finishStroke"));
            setObject(StrokePanel.class, "bench", null); StrokePanel.redrive = true; StrokePanel.endNear = false;
            activity.finish();
            test.waitForIdleSync(); SystemClock.sleep(1000);
        }
    }
    private static int bands = 3;

    /**
     * A long session on one page, for the heap (-e book "name" -e rounds 40 [-e holdMs 60000]): the bench library's
     * notebook {@code name} (a copy) opened where it was left, and each round a finger pinch in and a pan, strokes of a
     * fine pen, a filled shape, a crayon and the eraser, then a pinch back out; the heap kept (after a GC) logged before
     * and after each round, and the screen held {@code holdMs} at the start and the end for a heap dump from the shell.
     */
    static void heapSession(Instrumentation test, android.os.Bundle args, StringBuilder report) throws Exception {
        int rounds = args.getString("rounds") == null ? 40 : Integer.parseInt(args.getString("rounds"));
        long hold = args.getString("holdMs") == null ? 0 : Long.parseLong(args.getString("holdMs"));
        PaintActivity activity = (PaintActivity)test.startActivitySync(new Intent(test.getTargetContext(), PaintActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        front = activity;
        for (int i = 0; i < 100 && (Boolean)get(activity, "loading"); i++) SystemClock.sleep(100);
        test.waitForIdleSync(); SystemClock.sleep(500);
        main(test, () -> call(activity, "openNotebook", new Class<?>[]{String.class}, args.getString("book")));
        SystemClock.sleep(500);
        for (int i = 0; i < 600 && (Boolean)get(activity, "loading"); i++) SystemClock.sleep(100);
        test.waitForIdleSync();
        main(test, () -> { call(get(activity, "notebooks"), "hide"); activity.navigationLocked = false; });
        test.waitForIdleSync(); SystemClock.sleep(3000);
        Object pad = get(activity, "pad");
        InkWash ink = (InkWash)get(activity, "inkWash");
        // The owner's toolbar that day: Steady line, Filled shape, Wax crayon 3, and the eraser (each at its own size then; one size since 2026-10-25, the last).
        String[][] tools = {
                {"Steady line", "steady-line.M00il00000000at_000zz0000000000000_0_00r_1jz_84w0000000000000000nz0.51j8ud", "8.5"},
                {"Filled shape", "filled-shape.M30wd00000000at_1atat00000000_6_000_0_0000000000000000000nz0.2fi", "3.5"},
                {"Wax crayon 3", "wax-crayon.Ma0p600000000at_7zz0_0_ci0_0_0000000000000000000nz0.2f9", "8"}};
        main(test, () -> { for (int k = 0; k < tools.length; k++) { ink.slots.use(k, Brush.read(tools[k][0] + "\t" + tools[k][1])); ink.slots.swatches.setSize(ToolSlots.key(k), 1, Double.parseDouble(tools[k][2])); } });
        int[] view = new int[2];
        main(test, () -> { view[0] = ((View)pad).getWidth(); view[1] = ((View)pad).getHeight(); DrawingBook b = activity.book; report.append("page ").append(b.index() + 1).append(" of ").append(b.count()).append('\n'); });
        heapLine("opened", report);
        if (hold > 0) { Log.i("InkBench", "holding for a heap dump " + hold + " ms"); SystemClock.sleep(hold); }
        // -e away n: the first n rounds on the page before, then back (the notebook keeps two pages decoded).
        int away = args.getString("away") == null ? 0 : Integer.parseInt(args.getString("away"));
        if (away > 0) { main(test, () -> activity.turnPage(-1)); test.waitForIdleSync(); SystemClock.sleep(3000); heapLine("turned back", report); }
        for (int r = 0; r < rounds; r++) {
            if (away > 0 && r == away) { main(test, () -> activity.turnPage(1)); test.waitForIdleSync(); SystemClock.sleep(3000); heapLine("turned on", report); }
            pinch(test, view, 1.8f, 120 * (r % 5 - 2));
            int[] order = {0, 0, 1, 0, InkWash.ERASER, 2, 0};
            for (int k = 0; k < order.length; k++) {
                int tool = order[k];
                main(test, () -> ink.select(tool));
                test.waitForIdleSync(); SystemClock.sleep(400);
                strokeOn(test, pad, view, null, tool == 1 ? 1 : .7f, (r + k) % bands);
                test.waitForIdleSync(); SystemClock.sleep(900);
            }
            pinch(test, view, 1 / 1.8f, 0);
            test.waitForIdleSync(); SystemClock.sleep(1500);
            heapLine("round " + (r + 1), report);
        }
        main(test, () -> call(pad, "finishStroke"));
        test.waitForIdleSync(); SystemClock.sleep(3000);
        heapLine("end", report);
        if (hold > 0) { Log.i("InkBench", "holding for a heap dump " + hold + " ms"); SystemClock.sleep(hold); }
        activity.finish();
    }
    private static void heapLine(String what, StringBuilder report) {
        Runtime rt = Runtime.getRuntime();
        System.gc(); System.runFinalization(); System.gc();
        String line = String.format(Locale.ROOT, "%-9s heap kept %.1f MB", what, (rt.totalMemory() - rt.freeMemory()) / 1e6);
        Log.i("InkBench", line); report.append(line).append('\n');
    }
    /** Two fingers spread by {@code factor} about the view's middle and moved {@code dx} across, as the owner zooms and pans. */
    private static void pinch(Instrumentation test, int[] view, float factor, float dx) throws Exception {
        guard(test);
        float cx = view[0] / 2f, cy = view[1] / 2f, from = 200, to = 200 * factor;
        long down = SystemClock.uptimeMillis();
        int steps = 30;
        for (int i = 0; i <= steps + 1; i++) {
            float t = Math.min(1, i / (float)steps), half = from + (to - from) * t, x = cx + dx * t;
            int action = i == 0 ? MotionEvent.ACTION_DOWN : i == 1 ? MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT)
                    : i == steps + 1 ? MotionEvent.ACTION_POINTER_UP | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT) : MotionEvent.ACTION_MOVE;
            fingers(test, action, down, i == 0 ? 1 : 2, x - half, cy, x + half, cy);
            SystemClock.sleep(16);
        }
        fingers(test, MotionEvent.ACTION_UP, down, 1, cx + dx - to, cy, 0, 0);
        SystemClock.sleep(300);
    }
    private static void fingers(Instrumentation test, int action, long down, int n, float x0, float y0, float x1, float y1) {
        android.app.Activity a = front;
        if (a == null || !a.hasWindowFocus()) throw new IllegalStateException("bench stopped mid-pinch: its screen lost the focus");
        MotionEvent.PointerProperties[] props = new MotionEvent.PointerProperties[n];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[n];
        float[][] at = {{x0, y0}, {x1, y1}};
        for (int i = 0; i < n; i++) {
            props[i] = new MotionEvent.PointerProperties(); props[i].id = i; props[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
            coords[i] = new MotionEvent.PointerCoords(); coords[i].x = at[i][0]; coords[i].y = at[i][1]; coords[i].pressure = .5f; coords[i].size = .1f;
        }
        MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, n, props, coords, 0, 0, 1, 1, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
        try { test.getUiAutomation().injectInputEvent(event, false); } finally { event.recycle(); }
    }
    /** The S-curve scaled to {@code page}, mapped by {@code toView} (null: page px are view px), pressure scaled; returns the lift's time. */
    static long strokeOn(Instrumentation test, Object pad, int[] page, Matrix toView, float pressure, int band) throws Exception {
        float[][] s = ModularPerfBench.path(1500);
        float kx = page[0] / 1920f, ky = page[1] / 2560f;
        if (band >= 0) for (float[] p : s) p[1] = (float)((band + .5) * 2560 / bands + (p[1] - 1280) / (1.07 * bands));
        float[][] at = new float[s.length][2];
        main(test, () -> {
            Matrix toPanel = PanelCoordinates.fromView((View)pad);
            for (int i = 0; i < s.length; i++) { at[i][0] = s[i][0] * kx; at[i][1] = s[i][1] * ky; if (toView != null) toView.mapPoints(at[i]); toPanel.mapPoints(at[i]); }
        });
        guard(test);
        long down = SystemClock.uptimeMillis();
        for (int i = 0; i < s.length; i++) {
            long due = down + (long)s[i][5];
            while (SystemClock.uptimeMillis() < due) SystemClock.sleep(0);
            int action = i == 0 ? MotionEvent.ACTION_DOWN : i == s.length - 1 ? MotionEvent.ACTION_UP : MotionEvent.ACTION_MOVE;
            pen(test, action, down, at[i][0], at[i][1], Math.min(1, s[i][2] * pressure));
        }
        return SystemClock.uptimeMillis();
    }
    /** The bench's S-curve in real time, one sample an event; returns when the pen lifted (uptime ms). */
    static long stroke(Instrumentation test, Object pad, int[] page) throws Exception {
        return strokeOn(test, pad, page, (Matrix)get(pad, "pageToView"), 1, -1);
    }
    /**
     * The app opens on the notebook shelf, which covers the page and takes every touch: starts a notebook if there is no
     * page yet (a fresh install), and closes the shelf.
     */
    private static void openPage(Instrumentation test, android.app.Activity activity) throws Exception {
        main(test, () -> {
            if (get(get(activity, "pad"), "document") == null) call(activity, "startNewDrawing", new Class<?>[]{String.class}, "bench");
            call(get(activity, "notebooks"), "hide");
        });
        test.waitForIdleSync(); SystemClock.sleep(1500);
    }
    /** The bench's own screen, the only one its pen events may reach. */
    private static volatile android.app.Activity front;
    /**
     * Pen events are injected system-wide: they go to whatever window is on top. Before a stroke the bench's screen must
     * be resumed with the window focus; if not, it is brought back once, and if still not the run stops with no event sent.
     */
    static void guard(Instrumentation test) throws Exception {
        android.app.Activity a = front;
        if (a == null) throw new IllegalStateException("bench stopped: no bench screen to draw on");
        for (int attempt = 0; ; attempt++) {
            boolean[] ok = {false};
            test.runOnMainSync(() -> ok[0] = !a.isFinishing() && !a.isDestroyed() && a.hasWindowFocus() && a.getWindow().getDecorView().isShown());
            if (ok[0]) return;
            if (attempt == 1) {
                String msg = "bench stopped: its screen is not in front with the focus (another app or dialog is), no pen events sent";
                Log.e("InkBench", msg);
                throw new IllegalStateException(msg);
            }
            Log.w("InkBench", "bench screen not in front: bringing it back");
            try { ((android.app.ActivityManager)a.getSystemService(android.content.Context.ACTIVITY_SERVICE)).moveTaskToFront(a.getTaskId(), 0); }
            catch (SecurityException e) { a.startActivity(new Intent(a, a.getClass()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)); }
            test.waitForIdleSync(); SystemClock.sleep(1500);
        }
    }
    private static void pen(Instrumentation test, int action, long down, float x, float y, float pressure) {
        // Mid-stroke too (a field read, cheap): a lost focus stops the stroke before the next event goes out.
        android.app.Activity a = front;
        if (a == null || !a.hasWindowFocus()) {
            Log.e("InkBench", "bench stopped mid-stroke: its screen lost the focus, no more pen events sent");
            throw new IllegalStateException("bench stopped mid-stroke: its screen lost the focus");
        }
        MotionEvent.PointerProperties prop = new MotionEvent.PointerProperties(); prop.id = 0; prop.toolType = MotionEvent.TOOL_TYPE_STYLUS;
        MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords(); coords.x = x; coords.y = y; coords.pressure = pressure; coords.size = .1f;
        MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, 1, new MotionEvent.PointerProperties[]{prop}, new MotionEvent.PointerCoords[]{coords}, 0, 0, 1, 1, 0, 0, InputDevice.SOURCE_STYLUS, 0);
        try { test.getUiAutomation().injectInputEvent(event, false); } finally { event.recycle(); }
    }

    private interface Work { void run() throws Exception; }
    private static void main(Instrumentation test, Work work) throws Exception {
        Throwable[] error = {null};
        test.runOnMainSync(() -> { try { work.run(); } catch (Throwable e) { error[0] = e; } });
        if (error[0] != null) throw new Exception(error[0]);
    }
    private static Object get(Object owner, String name) throws Exception { Field f = owner.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(owner); }
    /** Sets a static knob; an older build without it is left as it is. */
    private static void setStatic(Class<?> owner, String name, boolean value) {
        try { Field f = owner.getDeclaredField(name); f.setAccessible(true); f.setBoolean(null, value); } catch (ReflectiveOperationException ignored) { }
    }
    private static void setObject(Class<?> owner, String name, Object value) {
        try { Field f = owner.getDeclaredField(name); f.setAccessible(true); f.set(null, value); } catch (ReflectiveOperationException ignored) { }
    }
    private static Object call(Object owner, String name, Class<?>[] types, Object... args) throws Exception { Method m = owner.getClass().getDeclaredMethod(name, types); m.setAccessible(true); return m.invoke(owner, args); }
    private static Object call(Object owner, String name) throws Exception { return call(owner, name, new Class<?>[0]); }
}
