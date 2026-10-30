package io.github.mpdairy.monopaint;

/**
 * The base eraser takes everything (the owner, 2026-10-25: "Crayon didn't erase with eraser. Base erase should erase
 * everything."): the owner's Wax crayon, small and large, dark and light grey over dark, and every Basic tool, drawn as
 * the drawing screen draws them (InkWash.stroke: {@link QuickStroke#start}, {@link EraseBothStroke#pen}) and dried, then rubbed with the eraser as it starts (Everything),
 * leaves white paper (but Tone's dots, scraped a share each pass) and nothing on the masking fluid: no wax left to keep a later wash out.
 */
public final class EraseAllChecks {
    /** The owner's toolbar slot 4 (tools/owner-toolbar-2026-10-24.txt). */
    static final String WAX_CRAYON = "wax-crayon.Ma0kp00000000at_7ll0_0_1i0_0_0000000000000000000nz0.2f9";
    static final String FINELINER = "fineliner.M00gx00000000at_000zz0000000000000_0_000_0_8cl0000000000ee0000nz0.b";
    static final int W = 480, H = 360;

    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        int n = 0;
        ModularBrush crayon = ModularCode.decode(WAX_CRAYON);
        check(crayon != null, "the owner's Wax crayon decodes");
        for (double px : new double[]{44, 120, 360}) { erasesAll("Wax crayon " + (int) px + " px", crayon, px, .78, .2); n++; }
        // Dry Media takes a crayon too: its own wax keeps no eraser off it (the wax, no kind of mark, stays).
        ToneDocument d = InkWashLayers.blank(W, H);
        WatercolorSession session = new WatercolorSession();
        draw(d, crayon, 120, .78, session, 180);
        check(notPaper(d) > 1000 && d.hasMask(), "a crayon lays marks and wax");
        DrawingStroke s = EraseBothStroke.pen(d, 160, ToneDocument.KIND_PENCIL);
        for (int y = -40; y <= d.height + 40; y += 30) for (int x = -60; x <= d.width + 60; x += 20) s.sample(x, y, 1f, Float.NaN, Float.NaN);
        s.finish();
        check(notPaper(d) == 0 && d.hasMask(), "the Dry Media eraser takes it, its wax stays: " + notPaper(d) + " px left");
        // Every Basic tool, masking fluid and wax resist among them: fluid lifts on the first pass, all the rest on the next.
        for (ToolPresets.Preset p : ToolPresets.all()) {
            ModularBrush b = ModularCode.decode(p.code);
            if (b == null) continue;
            erasesAll(p.name, b, 60, .78, .2); n++;
        }
        System.out.println("PASS: the base eraser takes everything: the owner's Wax crayon at 44, 120 and 360 px, dark and light over dark, and by Dry Media too, and "
                + n + " brushes in all, dried, back to white paper with no wax or fluid left");
    }
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    /** {@code b} at {@code px} at grey {@code dark}, then over it at grey {@code light}, dried; the base eraser over the page, twice. */
    static void erasesAll(String name, ModularBrush b, double px, double dark, double light) {
        ToneDocument d = InkWashLayers.blank(W, H);
        WatercolorSession session = new WatercolorSession();
        // A black ground first, so white and light marks show (light over dark, white ink).
        draw(d, ModularCode.decode(FINELINER), 120, 1, session, 160);
        draw(d, b, px, dark, session, 120);
        draw(d, b, px, light, session, 200);
        session.settleNow(d);
        int marked = notPaper(d);
        check(marked > 200, name + ": draws (" + marked + " px)");
        // Masking fluid comes off on the first pass and what it covered on the next (EraseBothStroke).
        for (int pass = 0; pass < 2; pass++) erase(d, session);
        int left = notPaper(d);
        check(left == 0, name + ": the base eraser leaves white paper, " + left + " px still marked");
        check(!d.hasMask(), name + ": and nothing on the masking fluid (a crayon's wax, a resist)");
    }
    static void draw(ToneDocument d, ModularBrush b, double px, double darkness, WatercolorSession session, double y) {
        // As InkWash.stroke: what settles laid first, then live or quick (QuickStroke).
        session.settleBefore(d, b);
        DrawingStroke s = QuickStroke.start(d, b, px, darkness, session);
        CarrySmudgeChecks.feed(s, CarrySmudgeChecks.across(60, 420, y));
    }
    /** The eraser as the bar starts it (Everything), 160 px, over the whole page. */
    static void erase(ToneDocument d, WatercolorSession session) {
        session.settleBefore(d, null);
        DrawingStroke s = EraseBothStroke.pen(d, 160, 0);
        for (int y = -40; y <= d.height + 40; y += 30) for (int x = -60; x <= d.width + 60; x += 20) s.sample(x, y, 1f, Float.NaN, Float.NaN);
        s.finish();
        session.settleNow(d);
    }
    /** Pixels the page shows not white, but a Tone dot (or a pixel that reads as one: black at coverage 254), which the eraser scrapes a share of each pass by design. */
    static int notPaper(ToneDocument d) {
        int n = 0;
        java.util.List<ToneDocument.Layer> layers = d.layerSnapshot().layers;
        for (int i = 0; i < d.width * d.height; i++) {
            if (d.compositeTone(i % d.width, i / d.width) == 255) continue;
            boolean dot = false;
            for (ToneDocument.Layer each : layers) for (int p = -1; p < ToneDocument.PLANES; p++) { ToneDocument.Layer l = p < 0 ? each : each.plane(p); if (l != null) dot |= (l.alpha[i] & 255) == PrintScreen.DOT_COVERAGE && (l.tones[i] & 255) <= PrintScreen.DOT_TONE; }
            if (!dot) n++;
        }
        return n;
    }
}
