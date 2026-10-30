package io.github.mpdairy.monopaint;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Record and Replay ({@link StrokeLog}): a stroke's samples keep through the file's bytes (rounded to half pixels), bytes
 * that don't read give no log, the player feeds every sample once, in order, a chunk of pen time a step; and on a page
 * Undo hides a stroke and Redo shows it, a new stroke drops the undone ones, Clear page hides those before it (Undo
 * brings them back), and the page's file keeps the strokes and reads them again.
 */
public final class StrokeLogChecks {
    static final int W = PageShadeChecks.W, H = PageShadeChecks.H;
    static final String CODE = "ink-pen.M0abc.1";

    public static void main(String[] args) throws IOException {
        Assets.install(Assets.folder(Assets.FOLDER));
        roundTrip(); garbage(); player(); ends(); history(); veil(); file(); keepsWet();
        System.out.println("PASS: Record and Replay: a stroke's brush, size, grey and samples (half pixels, pressure, tilt, timing) keep through the log's bytes; "
                + "bytes that are cut, wrong or too big give no log; at the cap the oldest strokes go, not the new one; the player feeds every sample once, in order, a chunk of pen time a step, each stroke begun and ended once; the pen or a finger landing ends a replay, a resting hand (a palm landing, any contact moving or lifting) doesn't; "
                + "on a page Undo hides a stroke and Redo shows it, a new stroke drops the undone ones, Clear page hides the strokes before it and Undo brings them back; "
                + "a replay shows only what the page still shows, a stroke since erased or moved staying paper; "
                + "the page's file keeps the visible strokes and reads them again, a page without any writes as before; "
                + "logging a stroke leaves what its lift left wet (a Slow wash, a halo, a floating bath) to settle as unlogged");
    }
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    /**
     * Logging the stroke just finished (DrawingPad does, after its finish) leaves what its lift left wet to settle: a
     * Slow wash, a halo, a floating bath, each held with the page's revision then. The page ends as unlogged.
     */
    static void keepsWet() {
        for (String name : new String[]{"Slow ink wash", "Mold ink", "Sumi"}) {
            ModularBrush b = null;
            for (ToolPresets.Preset p : ToolPresets.all()) if (p.name.equals(name)) b = ModularCode.decode(p.code);
            byte[][][] ends = new byte[2][][];
            for (int logged = 0; logged < 2; logged++) {
                ToneDocument d = InkWashLayers.blank(W, H);
                WatercolorSession session = new WatercolorSession();
                String[] stopped = {null};
                session.log = m -> stopped[0] = m;
                DrawingStroke s = QuickStroke.start(d, b.copy(), 40, 1, session);
                StrokeLog.Recorder rec = new StrokeLog.Recorder(CODE, 40, 1);
                for (int i = 0; i < 60; i++) { float x = 40 + i * 3, y = 60 + (i % 9); s.sample(x, y, .6f, 0, 30, 1000 + 2L * i); rec.add(x, y, .6f, 0, 30, 1000 + 2L * i); if (i % 6 == 5) s.present(); }
                check(s.finish() && session.settling(), name + ": wet after lift");
                if (logged == 1) d.logStroke(rec);
                int ticks = 0;
                while (session.tickDrying(d)) ticks++;
                check(ticks > 2 && stopped[0] == null, name + ": settles on, logged or not: " + ticks + " ticks, " + stopped[0]);
                ends[logged] = ModularStrokeChecks.layers(d);
            }
            check(ModularStrokeChecks.same(ends[0], ends[1]), name + ": the page ends as unlogged");
        }
    }

    static StrokeLog.Entry stroke(long serial, int n, float from) {
        StrokeLog.Recorder r = new StrokeLog.Recorder(CODE, 6.5, .75);
        for (int i = 0; i < n; i++) r.add(from + i * 1.25f, 20 + (i % 7) * .5f, i / (float) n, i % 3 == 0 ? Float.NaN : -12.4f + i, 5, 1000 + i * 8L);
        return r.done(serial);
    }
    static boolean same(StrokeLog.Entry a, StrokeLog.Entry b) {
        return a.code.equals(b.code) && a.px == b.px && a.darkness == b.darkness && java.util.Arrays.equals(a.x, b.x) && java.util.Arrays.equals(a.y, b.y)
                && java.util.Arrays.equals(a.dt, b.dt) && java.util.Arrays.equals(a.pressure, b.pressure) && java.util.Arrays.equals(a.tiltX, b.tiltX) && java.util.Arrays.equals(a.tiltY, b.tiltY);
    }

    static void roundTrip() {
        StrokeLog log = StrokeLog.NONE.added(stroke(1, 40, 3), 1).added(stroke(2, 5, 100.5f), 2);
        check(log.samples() == 45 && log.strokes().size() == 2, "two strokes");
        StrokeLog.Entry first = log.strokes().get(0);
        check(first.dt[0] == 0 && first.dt[1] == 8 && first.x[1] == 9 && first.px(1) == 4.5f, "half pixels, ms since the one before");
        check(Float.isNaN(first.tiltX(0)) && first.tiltX(1) == -11 && first.tiltY(1) == 5 && first.pressure(39) > .97f, "tilt and pressure");
        StrokeLog back = StrokeLog.decode(log.encode());
        check(back != null && back.strokes().size() == 2 && same(first, back.strokes().get(0)) && same(log.strokes().get(1), back.strokes().get(1)), "bytes give the strokes back");
        check(StrokeLog.decode(StrokeLog.NONE.encode()) == StrokeLog.NONE, "an empty log reads empty");
    }

    static void garbage() {
        byte[] good = StrokeLog.NONE.added(stroke(1, 30, 0), 1).encode();
        check(StrokeLog.decode(new byte[0]) == null && StrokeLog.decode(new byte[]{9, 0}) == null, "empty and wrong version");
        for (int cut = 1; cut < good.length; cut += 7) check(StrokeLog.decode(java.util.Arrays.copyOf(good, cut)) == null, "cut at " + cut);
        check(StrokeLog.decode(java.util.Arrays.copyOf(good, good.length + 1)) == null, "extra byte");
        byte[] huge = {1, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, 0x0f};
        check(StrokeLog.decode(huge) == null, "a count too big");
        // At the cap the oldest strokes go, not the new one; a stroke bigger than the cap alone is not kept.
        int half = StrokeLog.MOST_SAMPLES / 2;
        StrokeLog full = StrokeLog.NONE.added(stroke(1, half, 0), 1).added(stroke(2, half, 100), 2);
        check(full.samples() == StrokeLog.MOST_SAMPLES && full.strokes().size() == 2, "a full log");
        StrokeLog next = full.added(stroke(3, 10, 200), 3);
        check(next.strokes().size() == 2 && next.samples() == half + 10 && next.strokes().get(0).serial == 2 && next.strokes().get(1).serial == 3, "the oldest goes for the new");
        check(next.added(stroke(4, StrokeLog.MOST_SAMPLES + 1, 0), 4).samples() == next.samples(), "too big alone");
        // Undone strokes were already dropped, so they don't count against the cap; a marker stays harmless.
        StrokeLog cleared = full.added(StrokeLog.Entry.marker(3), 3).added(stroke(4, 5, 0), 4);
        check(cleared.strokes().size() == 1 && cleared.samples() <= StrokeLog.MOST_SAMPLES, "a clear then a stroke at the cap");
        check(full.upTo(1).strokes().size() == 1 && full.added(stroke(3, 5, 0), 1).strokes().size() == 2, "undone strokes leave first");
    }

    static void player() {
        List<StrokeLog.Entry> list = new ArrayList<>();
        list.add(stroke(1, 40, 0)); list.add(stroke(2, 1, 50)); list.add(stroke(3, 200, 70));
        StrokeLog.Player player = new StrokeLog.Player(list);
        List<String> seen = new ArrayList<>();
        StrokeLog.Sink sink = new StrokeLog.Sink() {
            @Override public void begin(StrokeLog.Entry e) { seen.add("b"); }
            @Override public void sample(StrokeLog.Entry e, int i, long time) { seen.add("s" + list.indexOf(e) + ":" + i + "@" + time); }
            @Override public void end(StrokeLog.Entry e) { seen.add("e"); }
        };
        int steps = 0;
        while (!player.done()) { player.step(100, sink); steps++; check(steps < 100, "ends"); }
        int samples = 0, begins = 0, ends = 0;
        for (String s : seen) { if (s.equals("b")) begins++; else if (s.equals("e")) ends++; else samples++; }
        check(samples == 241 && begins == 3 && ends == 3, "every sample once, every stroke begun and ended once");
        check(seen.get(0).equals("b") && seen.get(1).equals("s0:0@1000") && seen.get(2).equals("s0:1@1008"), "in order, from 1000 ms");
        check(steps >= 15 && steps <= 25, "a chunk of pen time a step: " + steps);
    }

    /** 2026-10-25: a palm resting as the replay began ended it on its next move, so it never showed. */
    static void ends() {
        check(StrokeLog.endsReplay(true, true, 0) && StrokeLog.endsReplay(true, false, 8), "the pen or a finger landing ends it");
        check(!StrokeLog.endsReplay(false, false, 8) && !StrokeLog.endsReplay(false, false, 110) && !StrokeLog.endsReplay(false, true, 0), "a contact moving or lifting doesn't");
        check(!StrokeLog.endsReplay(true, false, FingerTool.PALM_DP + 1), "a palm landing doesn't");
    }

    static StrokeLog.Recorder rec(float x) {
        StrokeLog.Recorder r = new StrokeLog.Recorder(CODE, 6, .5);
        for (int i = 0; i < 10; i++) r.add(x + i, 40, .5f, Float.NaN, Float.NaN, 100 + i * 5L);
        return r;
    }
    static void stroke(ToneDocument d, int x0) {
        d.begin(0); for (int x = x0; x < x0 + 30; x++) d.setTone(x, 40, 20); check(d.finish(), "marks");
        d.logStroke(rec(x0));
    }

    static void history() {
        ToneDocument d = InkWashLayers.blank(W, H);
        check(d.replay().isEmpty(), "nothing yet");
        stroke(d, 10); stroke(d, 50);
        check(d.replay().size() == 2, "two strokes");
        check(d.undo() && d.replay().size() == 1 && d.replay().get(0).x[0] == 20, "Undo hides the last");
        check(d.redo() && d.replay().size() == 2, "Redo shows it");
        check(d.undo(), "undo again");
        stroke(d, 90);
        check(d.replay().size() == 2 && d.replay().get(1).x[0] == 180, "a new stroke drops the undone one");
        check(!d.redo() && d.replay().size() == 2, "no Redo left");
        check(d.clearPage() && d.replay().isEmpty(), "Clear page hides them");
        stroke(d, 120);
        check(d.replay().size() == 1, "a stroke after the clear");
        check(d.undo() && d.undo() && d.replay().size() == 2, "Undo of the stroke and the clear brings the earlier two back");
        check(d.redo() && d.redo() && d.replay().size() == 1, "Redo clears and draws again");
    }

    /**
     * 2026-10-26, the owner: "Replay was cool but there was a random blob (a previous drawing) overtop that wasn't from
     * that page". The log keeps a stroke whose marks the eraser or the Lasso (neither logged) took away or moved; the
     * replay laid it again where it was drawn. Shown through {@link GhostPlane#offMarks}, the replay shows the paper
     * wherever the page shows no mark, and the page's own marks as they are drawn.
     */
    static void veil() {
        ToneDocument d = InkWashLayers.blank(W, H);
        stroke(d, 10); stroke(d, 50);
        // The eraser takes the first stroke away: the page changes, the log doesn't.
        d.begin(0); d.paintSpan(10, 40, 40, ToneDocument.ERASE); check(d.finish(), "erased");
        check(d.replay().size() == 2, "the log still holds the erased stroke");
        ToneDocument.Snapshot page = d.layerSnapshot();
        ToneDocument copy = new ToneDocument(page);
        check(copy.clearPage(), "a blank copy");
        copy.begin(0);
        for (StrokeLog.Entry e : d.replay()) for (int i = 0; i < e.size(); i++) copy.setTone(Math.round(e.px(i)), Math.round(e.py(i)), 20);
        check(copy.finish(), "replayed");
        GhostPlane.Planes veil = GhostPlane.offMarks(page);
        java.util.function.IntBinaryOperator shown = (x, y) -> copy.shownTone(x, y, null, 0, veil == null ? null : veil.overTones, veil == null ? null : veil.overAlpha);
        for (int x = 10; x < 20; x++) check(shown.applyAsInt(x, 40) == 255, "the erased stroke doesn't show again at " + x + ": " + shown.applyAsInt(x, 40));
        for (int x = 50; x < 60; x++) check(shown.applyAsInt(x, 40) == d.shownTone(x, 40, null, 0, null, null) && shown.applyAsInt(x, 40) < 128, "the page's stroke shows as drawn at " + x);
        check(shown.applyAsInt(70, 40) == 255 && shown.applyAsInt(5, 5) == 255, "paper elsewhere");
    }

    static void file() throws IOException {
        ToneDocument d = InkWashLayers.blank(W, H);
        byte[] plain = DrawingBook.encode(d);
        stroke(d, 10); stroke(d, 50);
        d.undo();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DocumentCodec.write(out, d.layerSnapshot());
        ToneDocument back = DocumentCodec.read(new ByteArrayInputStream(out.toByteArray()));
        check(back.replay().size() == 1 && same(d.replay().get(0), back.replay().get(0)), "the visible stroke is in the file");
        // After a read the strokes are the page's own, before any step: new strokes follow them, Undo takes only those.
        stroke(back, 90);
        check(back.replay().size() == 2 && back.undo() && back.replay().size() == 1, "new strokes follow the loaded ones");
        check(java.util.Arrays.equals(plain, DrawingBook.encode(InkWashLayers.blank(W, H))), "a page without strokes writes as before");
    }
}
