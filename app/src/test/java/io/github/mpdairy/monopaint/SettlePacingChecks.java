package io.github.mpdairy.monopaint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Settling's pace ({@link SettlePacer}): lagging input and slow work back it off for 100 ms, and it shows at most every
 * 120 ms; pacing changes when the work happens and shows, never the page it ends with (Drying ticks after lift, layers and
 * all; Fast-wet re-settles under the pen, whose layers' coverage may end lower at a few pixels, as composing once an
 * input event's may). And what the wet indicator reads: whether anything is still wet.
 */
public final class SettlePacingChecks {
    static final long MS = 1_000_000;

    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        pacer(); dryingSame(); fastWetSame(); wetness();
        System.out.println("PASS: settling backs off 100 ms after lagging input or slow work and shows at most every 120 ms; paced Drying ticks and Fast-wet re-settles end on the same page as unpaced ones; something is wet exactly while a Drying wash settles after lift, and the indicator is told each time that changes");
    }

    static void pacer() {
        long[] clock = {5_000 * MS};
        SettlePacer p = new SettlePacer(() -> clock[0]);
        check(p.waitMs() == 0 && p.mayShow(), "A fresh pace lets settling run and show");
        p.input(2 * MS, 5);
        check(p.waitMs() == 0, "Input on time doesn't back off");
        p.input(2 * MS, SettlePacer.LATE_EVENT_MS);
        check(p.waitMs() == SettlePacer.BACK_OFF_MS && !p.mayShow(), "A late event backs off 100 ms: " + p.waitMs());
        clock[0] += 60 * MS;
        check(p.waitMs() == 40, "and counts down");
        clock[0] += 40 * MS;
        check(p.waitMs() == 0 && p.mayShow(), "then settling runs again");
        p.input(SettlePacer.SLOW_INPUT_MS * MS, 0);
        check(p.waitMs() == SettlePacer.BACK_OFF_MS, "Slowly handled input backs off too");
        clock[0] += 100 * MS;
        p.worked(SettlePacer.TICK_BUDGET_MS * MS, SettlePacer.TICK_BUDGET_MS);
        check(p.waitMs() == 0, "Work within its budget doesn't");
        p.worked(SettlePacer.TICK_BUDGET_MS * MS + 1, SettlePacer.TICK_BUDGET_MS);
        check(p.waitMs() == SettlePacer.BACK_OFF_MS, "Work over its budget does");
        clock[0] += 100 * MS;
        p.shown();
        clock[0] += 119 * MS;
        check(!p.mayShow(), "Shown, it shows again no sooner than 120 ms");
        clock[0] += MS;
        check(p.mayShow(), "and from 120 ms");
    }

    /** A Drying wash ticked to the end with the pace backing off and holding shows back ends as an unpaced one. */
    static void dryingSame() {
        ModularBrush b = DryingWaterChecks.brush(.8, .3);
        WatercolorSession plain = new WatercolorSession();
        ToneDocument expected = DryingWaterChecks.drawn(plain, b);
        int plainTicks = 0;
        while (plain.tickDrying(expected)) plainTicks++;

        long[] clock = {0};
        WatercolorSession paced = new WatercolorSession();
        paced.pace = new SettlePacer(() -> clock[0]);
        ToneDocument d = DryingWaterChecks.drawn(paced, b);
        List<Long> shows = new ArrayList<>();
        int[] ticks = {0};
        long next;
        do {
            int tick = ticks[0]++;
            // Every fifth tick's show is slow (over the tick's budget), and every seventh the pen lags just before it.
            if (tick % 7 == 3) paced.input(0, 40);
            next = paced.tick(d, () -> { shows.add(clock[0]); d.clearDirty(); if (ticks[0] % 5 == 0) clock[0] += 60 * MS; });
            if (next >= 0) clock[0] += next * MS;
        } while (next >= 0 && ticks[0] < 10_000);
        check(next < 0 && !paced.settling(), "The paced wash settles: " + ticks[0] + " tick calls for " + plainTicks + " ticks");
        check(paced.pace.backOffs > 0 && ticks[0] > plainTicks + 1, "It backed off: " + paced.pace.backOffs + " times");
        for (int i = 1; i < shows.size() - 1; i++)
            check(shows.get(i) - shows.get(i - 1) >= SettlePacer.SHOW_MS * MS, "Shows are at least 120 ms apart");
        check(shows.size() < plainTicks, "fewer shows than ticks: " + shows.size() + " for " + plainTicks);
        check(ModularStrokeChecks.same(ModularStrokeChecks.layers(expected), ModularStrokeChecks.layers(d)), "A paced Drying wash ends on the same page as an unpaced one");
    }

    /** Fast-wet strokes whose re-settles show when the pace lets them end on the page unpaced ones do. */
    static void fastWetSame() {
        int compared = 0, deferred = 0, lower = 0;
        for (String name : new String[]{"wet-wash", "thick-oil", "brave-onion", "wide-hake", "rake"})
            for (double size : new double[]{40, 160}) {
                ModularBrush b = FastWetChecks.wet(name);
                ToneDocument expected = fastWet(b, size, null);
                long[] clock = {0};
                SettlePacer pace = new SettlePacer(() -> clock[0]);
                ToneDocument d = fastWet(b, size, new Object[]{pace, clock});
                String at = " (" + name + " at " + size + ")";
                check(Arrays.equals(DryingWaterChecks.tones(expected), DryingWaterChecks.tones(d)), "A paced Fast-wet stroke ends on the same page as an unpaced one" + at);
                // As with composing once an input event: a layer keeps the most coverage any compose gave a pixel, so
                // where a re-settle shown unpaced but not paced darkened a pixel the next one lightened, the paced
                // layer's coverage is lower there (its tone the same). Never higher.
                byte[][] le = ModularStrokeChecks.layers(expected), ld = ModularStrokeChecks.layers(d);
                for (int k = 1; k < 4; k += 2) for (int i = 0; i < le[k].length; i++) {
                    if (le[k][i] == ld[k][i]) continue;
                    check((ld[k][i] & 255) < (le[k][i] & 255), "A paced layer's coverage is never higher" + at);
                    lower++;
                }
                compared++;
                deferred += pace.backOffs;
            }
        check(deferred > 0, "Fast-wet's re-settles were held back " + deferred + " times over " + compared + " strokes");
        check(lower < 1000, "Coverage ends lower at only a few pixels: " + lower);
    }
    /** A 3 s Fast-wet wave on a wide page, an event every 3 samples; {@code paced} = {pace, clock}: the pen lags now and then. */
    static ToneDocument fastWet(ModularBrush b, double size, Object[] paced) {
        PartChoice m = b.choice(BrushPart.MEDIUM);
        ToneDocument d = InkWashLayers.blank(1200, 500);
        d.selectLayer(m == PartChoice.WASH || m == PartChoice.COVER ? InkWashLayers.WASH : InkWashLayers.INK);
        WatercolorSession session = new WatercolorSession();
        long[] clock = null;
        if (paced != null) { session.pace = (SettlePacer)paced[0]; clock = (long[])paced[1]; }
        ModularStroke s = new ModularStroke(d, b, size, 1, session);
        for (int i = 0; i <= 600; i++) {
            double t = i / 600.0;
            long time = i * 5L;
            if (clock != null) clock[0] = time * MS;
            s.sample((float)(60 + 1080 * t), (float)(250 + 150 * Math.sin(3 * Math.PI * t)), ToolSettings.pressure((float)(.3 + .6 * Math.sin(Math.PI * t))), 0, 0, time);
            if (i % 3 == 2) {
                s.present();
                // The pen lags for a stretch now and then: samples queue.
                if (clock != null && i % 120 < 40) session.input(0, 30);
            }
        }
        s.finish();
        return d;
    }

    /** Whether anything is still wet, and the indicator told whenever that may have changed. */
    static void wetness() {
        WatercolorSession session = new WatercolorSession();
        int[] told = {0};
        session.wetChanged = () -> told[0]++;
        check(!session.wet(), "Nothing is wet on a fresh page");
        ToneDocument d = DryingWaterChecks.drawn(session, DryingWaterChecks.brush(.8, .2));
        check(session.wet() && told[0] == 1, "A Slow wash is wet after lift, and the indicator is told");
        int ticks = 0;
        while (session.tickDrying(d)) ticks++;
        check(!session.wet() && told[0] == 2, "and dry once it has settled (" + ticks + " ticks)");

        DryingWaterChecks.drawn(session, DryingWaterChecks.brush(.8, 1));
        ToneDocument laid = d;
        check(session.wet(), "Wet again after the next Slow wash");
        int before = told[0];
        session.settleNow(InkWashLayers.blank(10, 10));
        check(!session.wet() && told[0] == before + 1, "Laid now (the indicator's tap, a new stroke, a pause), it is dry");
        session.settleNow(laid);
        session.dry("again");
        check(told[0] == before + 1, "Nothing more to tell when it is already dry");

        ToneDocument e = DryingWaterChecks.drawn(session, DryingWaterChecks.brush(.8, 1));
        session.dry("layer changed");
        check(!session.wet(), "Dropped (a layer change), it is dry");
        e = DryingWaterChecks.drawn(session, DryingWaterChecks.brush(.8, 1));
        e.begin(); e.setTone(5, 5, 0); e.finish();
        check(session.wet() && !session.tickDrying(e) && !session.wet(), "Stopped by another change to the page (undo), it is dry");

        for (PartChoice life : new PartChoice[]{PartChoice.FAST_WET, PartChoice.NONE}) {
            int t0 = told[0];
            ModularBrush b = ModularStrokeChecks.life(ModularCodeChecks.fixture("wet-wash"), life);
            DryingWaterChecks.drawn(session, b);
            check(!session.wet(), life + " leaves nothing wet after lift");
            check(told[0] == t0, life + ": nothing to tell");
        }
        ModularBrush cover = DryingWaterChecks.brush(.8, .2); cover.set(BrushPart.MEDIUM, new ModularBrush.Part(PartChoice.COVER));
        DryingWaterChecks.drawn(session, cover);
        check(!session.wet(), "Opaque paint leaves nothing wet");
    }

    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
