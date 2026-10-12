package io.github.mpdairy.monopaint;

import java.io.*;
import java.util.Arrays;

public final class WetWatercolorChecks {
    public static void main(String[] args) throws Exception {
        blendingAndHistory(); dryPaperAndLinework(); dryGaps(); drying(); pausedStrokes(false); pausedStrokes(true);
        liveStroke(); boundedSlices(); adaptiveBudget(); presets(); gravity(); brushPen(false); brushPen(true); carriedPaint(255); carriedPaint(128); wetBrushPen(); brushInk(); smoothFlow(); flowingGravity(); flowingGlaze(); queuedGlaze(); stuckPaint(); washOverWash(); pacing(); flowingSlices();
        System.out.println("PASS: live wet blending, gravity drift, brush pen seeping and sharp dry edges, wet brush pen ink flow by freshness, adaptive work limits, bounded spatial/time slices, pause/resume, retained water, dry boundaries/linework, queued watercolor dabs, drying, exact undo/redo, saved tones and TSP9 migration");
    }
    private static void check(boolean pass, String message) { if (!pass) throw new AssertionError(message); }
    private static void dab(ToneDocument doc, WetWatercolor wet, int x, int y, int w, int h, int gray) {
        doc.begin(); wet.beginStroke(); mask(wet, x, y, w, h, gray); wet.finishStroke();
    }
    private static void mask(WetWatercolor wet, int x, int y, int w, int h, int gray) {
        int[] mask = new int[w * h]; Arrays.fill(mask, 0xff000000);
        wet.paintMask(mask, w, x, y, w, h, gray);
    }
    private static void settle(WetWatercolor wet) {
        int frames = 0;
        while (wet.isAnimating()) { wet.advance(false); check(++frames < 1000, "Animation terminates"); }
    }
    private static void blendingAndHistory() throws Exception {
        ToneDocument doc = new ToneDocument(160, 96);
        WetWatercolor wet = new WetWatercolor(doc);
        byte[] blank = doc.snapshot();
        dab(doc, wet, 10, 10, 120, 70, 180); settle(wet);
        byte[] first = doc.snapshot();
        check(doc.tone(60, 40) == 180, "A uniform wash retains its shade after settling");
        dab(doc, wet, 55, 10, 45, 70, 20);
        check(doc.tone(75, 40) == 20, "New stroke appears immediately at its selected shade");
        wet.advance(false);
        int initialBlend = doc.tone(75, 40);
        check(initialBlend > 20 && initialBlend < 90, "Overlap begins fading gradually");
        settle(wet);
        check(doc.tone(75, 40) > initialBlend && doc.tone(75, 40) < 180, "Previously settled paint stays wet for later mixing");
        check(doc.tone(53, 40) < 180, "Pigment spreads into adjacent wet paint");
        byte[] mixed = doc.snapshot();
        check(doc.undo() && Arrays.equals(doc.snapshot(), first), "One Undo removes the stroke and all its animation across tiles");
        check(doc.undo() && Arrays.equals(doc.snapshot(), blank) && !doc.canUndo(), "No animation frames leak into history");
        check(doc.redo() && Arrays.equals(doc.snapshot(), first), "First wash redoes exactly");
        check(doc.redo() && Arrays.equals(doc.snapshot(), mixed) && !doc.canRedo(), "Animated stroke redoes to its final appearance");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DocumentCodec.write(bytes, doc.width, doc.height, doc.snapshot());
        check(Arrays.equals(DocumentCodec.read(new ByteArrayInputStream(bytes.toByteArray())).snapshot(), mixed), "Save/load retains blended tones without transient water");
    }
    private static void gravity() {
        int[] level = gravityTones(0, 0), downhill = gravityTones(0, 1), sideways = gravityTones(-.7f, 0);
        check(level[0] == level[1], "Flat canvas spreads evenly up and down");
        check(downhill[1] < level[1] && downhill[0] > level[0], "Tilted canvas runs paint downhill, away from uphill");
        check(downhill[1] < 255, "Downhill paint reaches beyond the usual wet margin");
        check(sideways[2] < level[2] && sideways[3] > level[3], "Tilt toward the left runs paint left");
    }
    /** Brush pen water dragged out of a dry black block, on dry paper or a wet canvas. */
    private static void brushPen(boolean wetCanvas) {
        int light = brushPenTones(wetCanvas, .15f)[0], full = brushPenTones(wetCanvas, 1)[0];
        check(full < light, "Pressing harder pulls more paint into the water (light " + light + ", full " + full + ")");
    }
    /** Carry drags white or gray from the left further into a dry black block; without it the pen trades its load within a few dabs. */
    private static void carriedPaint(int shade) {
        int none = carriedTone(shade, 0), half = carriedTone(shade, .5f), full = carriedTone(shade, 1);
        String tones = " (0% " + none + ", 50% " + half + ", 100% " + full + ")";
        check(none < half && half + 20 < full, "More carry pushes " + shade + " further into black" + tones);
        check(Math.abs(full - shade / 2) <= 2, "Full carry keeps its first load the whole stroke, laid at the pen's strength" + tones);
    }
    private static int carriedTone(int shade, float carry) {
        ToneDocument doc = new ToneDocument(200, 64);
        doc.begin(); for (int y = 0; y < 64; y++) for (int x = 0; x < 200; x++) doc.paintTone(x, y, x < 50 ? shade : 0); doc.finish();
        byte[] dry = doc.snapshot();
        WetWatercolor wet = new WetWatercolor(doc);
        int[] mask = new int[8 * 16]; Arrays.fill(mask, 0xff000000);
        doc.begin(); wet.beginStroke();
        for (int x = 10; x <= 170; x += 3) wet.waterMask(mask, 8, x, 24, 8, 16, 1, false, carry);
        wet.finishStroke(); settle(wet);
        int tone = doc.tone(100, 32);
        check(doc.tone(100, 22) == 0, "Carried paint keeps sharp edges on dry paper");
        check(doc.undo() && Arrays.equals(doc.snapshot(), dry), "One undo removes the carried paint");
        return tone;
    }
    /** Wet a spot, let it age, then bridge it to a black block: the ink runs through the fresh bridge and seeps unevenly into the older spot. */
    private static void wetBrushPen() {
        ToneDocument doc = new ToneDocument(200, 48);
        doc.begin(); for (int y = 12; y < 36; y++) for (int x = 0; x < 24; x++) doc.paintTone(x, y, 0); doc.finish();
        byte[] dry = doc.snapshot();
        WetWatercolor wet = new WetWatercolor(doc);
        int[] spot = new int[40 * 24]; Arrays.fill(spot, 0xff000000);
        doc.begin(); wet.beginStroke(); wet.waterMask(spot, 40, 130, 12, 40, 24, 1, true); wet.finishStroke();
        for (int frame = 0; frame < 60; frame++) wet.advance(false);
        check(wet.isAnimating(), "Flowing water is still wet after sixty frames");
        int[] bridge = new int[8 * 8]; Arrays.fill(bridge, 0xff000000);
        doc.begin(); wet.beginStroke();
        for (int x = 2; x <= 128; x += 3) wet.waterMask(bridge, 8, x, 20, 8, 8, 1, true);
        wet.finishStroke();
        for (int frame = 0; frame < 7; frame++) wet.advance(false);
        int early = doc.tone(110, 24);
        check(early < 240, "Within a second ink runs 90 px through fresh water (" + early + ")");
        settle(wet);
        int mid = doc.tone(80, 24), entry = doc.tone(134, 24), far = doc.tone(165, 24);
        String tones = " (bridge " + mid + ", spot entry " + entry + ", spot far side " + far + ")";
        check(mid < 230, "Ink runs well along the fresh bridge" + tones);
        check(entry < 255 && entry < far, "Ink seeps into the older spot but does not even out across it" + tones);
        check(doc.tone(80, 18) == 255 && doc.tone(150, 10) == 255, "On dry paper the water's edges stay sharp" + tones);
        check(doc.tone(5, 14) == 0, "Dry paint the pen never touched stays put" + tones);
        check(doc.tone(10, 24) > 60, "Wetting the ink draws it away, lightening where it was (" + doc.tone(10, 24) + ")");
        // Clear water on white paper changes nothing, so only the bridge is an undo step.
        check(doc.undo() && Arrays.equals(doc.snapshot(), dry), "One undo removes the bridge and the ink it carried");
        // Rubbing faded ink never darkens it: water carries paint no darker than its source.
        ToneDocument faded = new ToneDocument(64, 64);
        faded.begin(); for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) faded.paintTone(x, y, 160); faded.finish();
        WetWatercolor rub = new WetWatercolor(faded);
        int[] dab = new int[16 * 16]; Arrays.fill(dab, 0xff000000);
        faded.begin(); rub.beginStroke();
        for (int x = 8; x <= 40; x += 2) rub.waterMask(dab, 16, x, 24, 16, 16, 1, true);
        int during = faded.tone(30, 32);
        rub.finishStroke(); settle(rub);
        int darkest = 255; for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) darkest = Math.min(darkest, faded.tone(x, y));
        check(during >= 159 && darkest >= 159, "Wetting gray ink leaves it gray (" + during + " while rubbing, darkest " + darkest + ")");
    }
    /**
     * The wet brush pen keeps ink between strokes: a clean brush soaks it up, less once it
     * is fuller, and lets it out over clean paper elsewhere until it is cleaned.
     */
    private static void brushInk() {
        ToneDocument doc = new ToneDocument(256, 64);
        doc.begin(); for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) doc.paintTone(x, y, 0); doc.finish();
        BrushInk ink = new BrushInk();
        int[] mask = new int[16 * 16]; Arrays.fill(mask, 0xff000000);
        float[] soaked = new float[2];
        for (int pass = 0; pass < 2; pass++) {
            float before = ink.fullness();
            rub(doc, ink, mask, 8, 40, 24);
            soaked[pass] = ink.fullness() - before;
        }
        String held = " (first rub " + soaked[0] + ", second " + soaked[1] + ")";
        check(soaked[0] > 0 && soaked[1] < soaked[0], "A clean brush soaks up black, and less once it holds some" + held);
        check(soaked[0] > .15f && doc.tone(30, 32) > 10 && doc.tone(30, 32) < 80, "Lifted paint holds more ink than shows: the brush fills with it while the black only lightens a little ("
                + doc.tone(30, 32) + held + ")");
        float full = ink.fullness();
        rub(doc, ink, mask, 150, 230, 24);
        check(doc.tone(190, 32) < 250 && ink.fullness() < full, "The brush lets its ink out over clean paper in a later stroke (" + doc.tone(190, 32) + ")");
        ink.clean();
        check(ink.fullness() == 0, "Cleaning empties the brush");
        rub(doc, ink, mask, 150, 230, 0);
        check(doc.tone(190, 8) == 255, "A clean brush leaves clean paper clean (" + doc.tone(190, 8) + ")");
        ink.restore(1, 2); check(ink.fullness() == 1, "A brush holds no more than full");
    }
    /** One flowing stroke back and forth from {@code from} to {@code to} at row {@code top}, settled. */
    private static void rub(ToneDocument doc, BrushInk ink, int[] mask, int from, int to, int top) {
        WetWatercolor wet = new WetWatercolor(doc);
        doc.begin(); wet.beginStroke();
        for (int pass = 0; pass < 3; pass++) for (int x = from; x <= to; x += 3)
            wet.waterMask(mask, 16, pass % 2 == 0 ? x : from + to - x, top, 16, 16, 1, true, 0, ink);
        wet.finishStroke(); settle(wet);
    }
    /**
     * A brush's flowing transparent paint is the wet brush pen's water tinted with the selected
     * shade: never lighter than it, it soaks up less ink the darker it is, and pools with gravity.
     */
    private static void flowingGlaze() {
        ToneDocument doc = new ToneDocument(128, 128);
        doc.begin(); for (int y = 0; y < 128; y++) for (int x = 96; x < 128; x++) doc.paintTone(x, y, 40); doc.finish();
        byte[] dry = doc.snapshot();
        WetWatercolor wet = new WetWatercolor(doc); wet.setGravity(0, .3f);
        int[] mask = new int[16 * 16]; Arrays.fill(mask, 0xff000000);
        BrushInk brush = new BrushInk(true); brush.tint(128);
        doc.begin(); wet.beginStroke();
        for (int y = 0; y <= 112; y += 6) for (int x = 0; x <= 112; x += 6) wet.waterMask(mask, 16, x, y, 16, 16, 1, true, 0, brush);
        String laid = " (paper " + doc.tone(40, 64) + ", over dark paint " + doc.tone(110, 64) + ")";
        check(doc.tone(40, 64) <= 130 && doc.tone(40, 64) > 90 && Math.abs(doc.tone(110, 64) - 40 * 128 / 255) <= 6,
                "The tint is the lightest the brush lays, and glazes over dry paint like transparent paint" + laid);
        wet.finishStroke(); settle(wet);
        String pooled = " (top " + doc.tone(40, 8) + ", bottom " + doc.tone(40, 120) + ")";
        check(doc.tone(40, 120) + 30 < doc.tone(40, 8), "Its ink runs downhill and pools at the wash's low edge" + pooled);
        check(doc.undo() && Arrays.equals(dry, doc.snapshot()), "One undo removes the flowing paint");
        // A gray brush soaks up less from black than clear water does, and a tap resets what it took.
        float clear = soaked(255), gray = soaked(128);
        check(gray > 0 && gray < clear * .7f, "A darker tint soaks up less ink (clear " + clear + ", gray " + gray + ")");
        brush.trade(255, 1); brush.tint(128);
        check(Math.abs(brush.fullness() - 127 / 255f) < .001f, "Tapping a shade resets the brush's ink to it");
        for (int i = 0; i < 50; i++) brush.trade(0, 1);
        check(Math.abs(brush.fullness() - 127 / 255f) < .001f, "Over clear water the brush never gives out its tint");
    }
    /**
     * A watercolor brush's dabs show at once as its tint glazed over the paint, and are laid in
     * full later, in order, across strokes: the result is the same as laying each at once.
     */
    private static void queuedGlaze() {
        byte[] now = null;
        for (boolean queue : new boolean[]{false, true}) {
            ToneDocument doc = new ToneDocument(160, 96);
            doc.begin(); for (int y = 20; y < 70; y++) for (int x = 60; x < 90; x++) doc.paintTone(x, y, 50); doc.finish();
            WetWatercolor wet = new WetWatercolor(doc);
            int[] mask = new int[16 * 16]; Arrays.fill(mask, 0xff000000);
            for (int i = 0; i < 16; i++) mask[i] = 0x80000000;
            BrushInk brush = new BrushInk(true); brush.tint(140);
            for (int stroke = 0; stroke < 2; stroke++) {
                doc.begin(); wet.beginStroke();
                for (int pass = 0; pass < 3; pass++) for (int x = 10; x <= 130; x += 4) {
                    int at = pass % 2 == 0 ? x : 140 - x, top = 30 + stroke * 8;
                    if (queue) wet.queueWater(mask, 16, at, top, 16, 16, 1, true, 0, brush);
                    else wet.waterMask(mask, 16, at, top, 16, 16, 1, true, 0, brush);
                }
                if (queue && stroke == 0) {
                    check(wet.queuedDabs() > 0 && doc.tone(30, 40) == Math.round(255 * 140 / 255f)
                            && doc.tone(75, 40) == Math.round(50 * 140 / 255f),
                            "Queued dabs show the tint glazed over paper and paint at once (" + doc.tone(30, 40) + ", " + doc.tone(75, 40) + ")");
                }
                wet.finishStroke();
            }
            if (queue) { wet.layQueued(false); check(wet.queuedDabs() == 0, "Laying empties the queue"); }
            if (!queue) now = doc.snapshot();
            else check(Arrays.equals(now, doc.snapshot()), "Queued dabs lay the same paint as dabs laid at once, across strokes");
        }
    }
    /** How much ink a brush tinted {@code gray} soaks up from one stroke over black. */
    private static float soaked(int gray) {
        ToneDocument doc = new ToneDocument(96, 64);
        doc.begin(); for (int y = 0; y < 64; y++) for (int x = 0; x < 96; x++) doc.paintTone(x, y, 0); doc.finish();
        WetWatercolor wet = new WetWatercolor(doc);
        int[] mask = new int[16 * 16]; Arrays.fill(mask, 0xff000000);
        BrushInk brush = new BrushInk(); brush.tint(gray);
        float before = brush.fullness();
        doc.begin(); wet.beginStroke();
        for (int x = 8; x <= 72; x += 3) wet.waterMask(mask, 16, x, 24, 16, 16, 1, true, 0, brush);
        wet.finishStroke();
        return brush.fullness() - before;
    }
    /**
     * Under a watercolor brush, dry paint stays stuck: each dab soaks it a little, so a couple
     * of passes over black line art leave it while scrubbing it more lifts it.
     * Soaked paint never drains into the wash by itself.
     */
    private static void stuckPaint() {
        int[] pen = grazed(false, 255, 1, 0), two = grazed(true, 220, 2, 0), clearTwo = grazed(true, 255, 2, 0),
                rubbed = grazed(true, 220, 8, 0), clearRubbed = grazed(true, 255, 8, 0), slow = grazed(true, 255, 8, 4);
        StringBuilder curve = new StringBuilder();
        for (int passes = 1; passes <= 8; passes++) curve.append(grazed(true, 255, passes, 0)[0]).append(' ');
        String tones = " (spot, downstream: pen " + pen[0] + ", " + pen[1] + "; wash twice " + two[0] + ", " + two[1]
                + "; clear twice " + clearTwo[0] + "; wash rubbed " + rubbed[0] + ", " + rubbed[1]
                + "; clear rubbed " + clearRubbed[0] + ", slowly " + slow[0] + "; clear by rubs " + curve + ")";
        check(pen[0] > 200, "The wet brush pen lifts dry paint at once" + tones);
        check(two[0] < 30 && clearTwo[0] < 30 && Math.abs(two[1] - 220) <= 6,
                "A couple of passes over a black line leave it, and the wash keeps its own tint" + tones);
        check(rubbed[0] > two[0] + 60 && clearRubbed[0] > clearTwo[0] + 60, "Rubbing several times lifts the soaked paint" + tones);
        check(Math.abs(slow[0] - clearRubbed[0]) <= 30, "Pausing between passes does not soak the paint by itself" + tones);
        // Lifted ink the brush put down nearby may run back over the spot; the spot's own paint never drains away.
        check(rubbed[0] <= rubbed[2] + 6, "Once the brush leaves, soaked paint stays put instead of draining into the wash (at pen-up "
                + rubbed[2] + ", settled " + rubbed[0] + ")");
    }
    /** Fresh water runs as fast as it can; then a tiny splotch slows down for a while, and a big wash does not. */
    private static void pacing() {
        int[] tiny = paced(12), big = paced(200);
        String waits = " (tiny splotch fresh " + tiny[0] + " ms, then " + tiny[1] + " ms; big wash " + big[0] + ", " + big[1] + " ms)";
        check(tiny[0] == 0 && big[0] == 0, "Fresh water's frames run at once" + waits);
        check(tiny[1] >= 30, "A tiny splotch's slowing frames wait, leaving time to tilt" + waits);
        check(big[1] == 0, "A big wash never waits" + waits);
    }
    /** Frame waits for a {@code size}-px square watercolor wash, fresh and after 30 frames. */
    private static int[] paced(int size) {
        ToneDocument doc = new ToneDocument(256, 256); WetWatercolor wet = new WetWatercolor(doc);
        int[] mask = new int[size * size]; Arrays.fill(mask, 0xff000000);
        BrushInk ink = new BrushInk(true); ink.tint(120);
        doc.begin(); wet.beginStroke(); wet.waterMask(mask, size, 20, 20, size, size, 1, true, 0, ink); wet.finishStroke();
        wet.advance(false); while (wet.framePending()) wet.advance(false);
        int fresh = wet.flowFrameMillis();
        for (int frame = 0; frame < 30; frame++) { wet.advance(false); while (wet.framePending()) wet.advance(false); }
        return new int[]{fresh, wet.flowFrameMillis()};
    }
    /**
     * A light watercolor scribble over a dark wash: right away it runs into the wet paint,
     * but once it slows, though the water still runs, it glazes over the paint instead of
     * lifting it, and the old paint in the scribble's gaps stays put too.
     */
    private static void washOverWash() {
        int[] wetInWet = washedOver(2), glazed = washedOver(30);
        String tones = " (dark wash under, beside the light: at once " + wetInWet[0] + ", " + wetInWet[1] + "; once slowing "
                + glazed[0] + ", " + glazed[1] + "; before " + glazed[2] + ", " + glazed[3] + ")";
        check(wetInWet[0] > wetInWet[2] + 20, "A wash straight into wet paint lifts it" + tones);
        check(glazed[0] <= glazed[2] + 4 && glazed[1] <= glazed[3] + 4, "A wash once it slows leaves the paint under it and in its gaps" + tones);
    }
    /** A dark wash after a light scribble goes over it {@code frames} later, under the scribble and in its gap; and both before. */
    private static int[] washedOver(int frames) {
        ToneDocument doc = new ToneDocument(128, 64);
        WetWatercolor wet = new WetWatercolor(doc);
        int[] mask = new int[16 * 16]; Arrays.fill(mask, 0xff000000);
        BrushInk dark = new BrushInk(true); dark.tint(60);
        doc.begin(); wet.beginStroke();
        for (int x = 20; x <= 60; x += 3) wet.waterMask(mask, 16, x, 24, 16, 16, 1, true, 0, dark);
        wet.finishStroke();
        for (int frame = 0; frame < frames; frame++) { wet.advance(false); while (wet.framePending()) wet.advance(false); }
        int under = doc.tone(40, 26), gap = doc.tone(40, 34);
        BrushInk light = new BrushInk(true); light.tint(230);
        doc.begin(); wet.beginStroke();
        // Two quick passes leave a gap between them, from y 30 to 38.
        for (int pass = 0; pass < 2; pass++)
            for (int x = 0; x <= 100; x += 3) wet.waterMask(mask, 16, pass == 0 ? x : 100 - x, pass == 0 ? 14 : 38, 16, 16, 1, true, 0, light);
        wet.finishStroke(); settle(wet);
        return new int[]{doc.tone(40, 26), doc.tone(40, 34), under, gap};
    }
    /**
     * Tones of a black spot, the wash beyond it, and the spot at pen-up, after a stroke tinted
     * {@code gray} crosses it {@code passes} times, with {@code frames} of wet animation between passes.
     */
    private static int[] grazed(boolean watercolor, int gray, int passes, int frames) {
        ToneDocument doc = new ToneDocument(160, 64);
        doc.begin(); for (int y = 0; y < 64; y++) for (int x = 40; x < 52; x++) doc.paintTone(x, y, 0); doc.finish();
        WetWatercolor wet = new WetWatercolor(doc);
        int[] mask = new int[16 * 16]; Arrays.fill(mask, 0xff000000);
        BrushInk brush = new BrushInk(watercolor); brush.tint(gray);
        doc.begin(); wet.beginStroke();
        for (int pass = 0; pass < passes; pass++) {
            for (int x = 0; x <= 64; x += 3) wet.waterMask(mask, 16, pass % 2 == 0 ? x : 64 - x, 24, 16, 16, 1, true, 0, brush);
            for (int frame = 0; frame < frames; frame++) { wet.advance(true); while (wet.framePending()) wet.advance(true); }
        }
        for (int x = 64; x <= 140; x += 3) wet.waterMask(mask, 16, x, 24, 16, 16, 1, true, 0, brush);
        wet.finishStroke(); int lifted = doc.tone(46, 32); settle(wet);
        return new int[]{doc.tone(46, 32), doc.tone(120, 32), lifted};
    }
    /** Ink running through a wide wash of flowing water pools at its downhill edge on a tilted tablet. */
    private static void flowingGravity() {
        float level = inkCenter(0, 0), down = inkCenter(0, .3f), right = inkCenter(.3f, 0);
        String centers = " (level " + level + ", tilted down " + down + ", tilted right " + right + ")";
        check(Math.abs(level - 64) < 4, "On a level tablet the ink spreads evenly around its band" + centers);
        check(down > level + 15, "Tilted, the ink runs down through the water and pools at its low edge" + centers);
        check(right > level + 15 && Math.abs(inkCenterY - 64) < 4, "Sideways tilt runs the ink sideways only" + centers);
    }
    private static float inkCenterY;
    /** Where a dark band's ink settles in a wide wash of flowing water: along the tilt's axis, and across it in {@link #inkCenterY}. */
    private static float inkCenter(float gx, float gy) {
        ToneDocument doc = new ToneDocument(128, 128);
        boolean sideways = gx != 0;
        doc.begin();
        for (int a = 0; a < 128; a++) for (int b = 52; b < 76; b++) doc.paintTone(sideways ? b : a, sideways ? a : b, 0);
        doc.finish();
        WetWatercolor wet = new WetWatercolor(doc); wet.setGravity(gx, gy);
        int[] mask = new int[16 * 16]; Arrays.fill(mask, 0xff000000);
        doc.begin(); wet.beginStroke();
        for (int y = 0; y <= 112; y += 6) for (int x = 0; x <= 112; x += 6) wet.waterMask(mask, 16, x, y, 16, 16, 1, true);
        wet.finishStroke(); settle(wet);
        double ink = 0, along = 0, across = 0;
        for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
            double k = 255 - doc.tone(x, y); ink += k; along += k * (sideways ? x : y); across += k * (sideways ? y : x);
        }
        inkCenterY = (float)(across / ink);
        return (float)(along / ink);
    }
    /**
     * A broad, fast flowing stroke from a black block across white: ink running between
     * tiles must not leave square steps on the 16 px tile grid.
     */
    private static void smoothFlow() {
        ToneDocument doc = new ToneDocument(256, 128);
        doc.begin(); for (int y = 0; y < 128; y++) for (int x = 0; x < 48; x++) doc.paintTone(x, y, 0); doc.finish();
        WetWatercolor wet = new WetWatercolor(doc);
        int[] mask = new int[40 * 80]; Arrays.fill(mask, 0xff000000);
        doc.begin(); wet.beginStroke();
        for (int x = 20; x <= 200; x += 12) wet.waterMask(mask, 40, x, 24, 40, 80, 1, true);
        wet.finishStroke();
        float seams = 0, inside = 0; int seamCount = 0, insideCount = 0;
        for (int frame = 0; frame < 12; frame++) {
            wet.advance(false);
            // Steps between neighboring pixels across tile edges, against steps within tiles, inside the wet stroke.
            for (int y = 32; y < 96; y++) for (int x = 60; x < 230; x++) {
                float step = Math.abs(doc.tone(x, y) - doc.tone(x - 1, y)) + Math.abs(doc.tone(x, y) - doc.tone(x, y - 1));
                if (x % 16 == 0 || y % 16 == 0) { seams += step; seamCount++; } else { inside += step; insideCount++; }
            }
        }
        seams /= seamCount; inside /= insideCount;
        check(seams < inside * 1.5f + .5f, "Running ink leaves no steps on the tile grid (tile edges " + seams + ", within tiles " + inside + ")");
    }
    /** Even spread planning must yield; splitting it must not change the fluid result. */
    private static void flowingSlices() {
        ToneDocument full = new ToneDocument(160, 64), sliced = new ToneDocument(160, 64);
        WetWatercolor a = new WetWatercolor(full), b = new WetWatercolor(sliced);
        int[] mask = new int[140 * 40]; Arrays.fill(mask, 0xff000000);
        for (ToneDocument doc : new ToneDocument[]{full, sliced}) {
            doc.begin();
            for (int y = 8; y < 56; y++) for (int x = 0; x < 32; x++) doc.paintTone(x, y, 40);
            doc.finish(); doc.begin();
        }
        a.beginStroke(); b.beginStroke();
        a.waterMask(mask, 140, 0, 12, 140, 40, .8f, true);
        b.waterMask(mask, 140, 0, 12, 140, 40, .8f, true);
        a.finishStroke(); b.finishStroke();
        byte[] before = sliced.snapshot();
        b.advance(false, 1, 1, 192);
        check(b.advancedTiles() == 0 && b.framePending() && Arrays.equals(before, sliced.snapshot()),
                "Flow planning yields before pixel edits when its deadline expires");
        for (int frame = 0; frame < 8; frame++) {
            a.advance(false, 1, Long.MAX_VALUE, 192);
            while (a.framePending()) a.advance(false, 1, Long.MAX_VALUE, 192);
            int calls = 0;
            do {
                b.advance(false, 1, 1, 192);
                check(++calls < 10000, "Tiny-budget spread planning makes progress");
            } while (b.framePending());
            check(Arrays.equals(full.snapshot(), sliced.snapshot()), "Sliced spread preserves frame " + frame);
        }
        byte[] result = sliced.snapshot();
        check(sliced.undo() && Arrays.equals(before, sliced.snapshot()), "Sliced flowing animation stays one undo step");
        check(sliced.redo() && Arrays.equals(result, sliced.snapshot()), "Sliced flow redoes exactly");
        b.advance(false, 1, 1, 192); // Suspend planning, then wake tiles with another stroke.
        sliced.begin(); b.beginStroke();
        b.waterMask(mask, 140, 20, 0, 140, 40, 1, true);
        int calls = 0;
        do {
            b.advance(true, 1, 1, 192);
            check(++calls < 10000, "Pen input between planning slices does not stall the frame");
        } while (b.framePending());
        b.finishStroke();
        check(sliced.undo() && Arrays.equals(result, sliced.snapshot()),
                "Input that wakes tiles during spread planning remains one undo step");
    }
    /** Seeped, beside and untouched tones; checks the shared expectations at any pull. */
    private static int[] brushPenTones(boolean wetCanvas, float pull) {
        ToneDocument doc = new ToneDocument(160, 64);
        doc.begin(); for (int y = 16; y < 48; y++) for (int x = 10; x < 50; x++) doc.paintTone(x, y, 0); doc.finish();
        byte[] dry = doc.snapshot();
        WetWatercolor wet = wetCanvas ? new WetWatercolor(doc, 65) : new WetWatercolor(doc);
        doc.begin(); wet.beginStroke();
        int[] mask = new int[8 * 16]; Arrays.fill(mask, 0xff000000);
        for (int x = 40; x <= 110; x += 3) wet.waterMask(mask, 8, x, 24, 8, 16, pull, false);
        wet.finishStroke(); settle(wet);
        int seeped = doc.tone(80, 32), beyond = doc.tone(80, 22), untouched = doc.tone(20, 32);
        String tones = " (seeped " + seeped + ", beside " + beyond + ", untouched " + untouched + ")";
        check(seeped < (wetCanvas || pull < 1 ? 255 : 235) && seeped > 40, "Black seeps into the new water, thinned" + tones);
        check(doc.tone(105, 32) > seeped, "The pigment thins with distance from the block" + tones);
        if (wetCanvas) check(pull < 1 || beyond < 255 && doc.tone(45, 20) > 0, "On a wet canvas the water's edges and the block soften" + tones);
        else {
            check(beyond == 255 && doc.tone(80, 23) == 255, "On dry paper the water's edge stays sharp" + tones);
            check(untouched == 0 && doc.tone(45, 20) == 0, "Dry paint the pen never touched stays put" + tones);
        }
        check(doc.undo() && Arrays.equals(doc.snapshot(), dry), "One undo removes the stroke and its seeping");
        return new int[]{seeped, beyond, untouched};
    }
    /** Tones just above, below, left and right of a settled dark dab on a wet canvas. */
    private static int[] gravityTones(float x, float y) {
        ToneDocument doc = new ToneDocument(128, 128);
        WetWatercolor wet = new WetWatercolor(doc, 100);
        wet.setGravity(x, y);
        dab(doc, wet, 56, 56, 16, 16, 0); settle(wet);
        return new int[]{doc.tone(64, 51), doc.tone(64, 76), doc.tone(51, 64), doc.tone(76, 64)};
    }
    private static void dryPaperAndLinework() {
        ToneDocument doc = new ToneDocument(67, 53);
        doc.begin(); for (int x=0; x<67; x++) doc.setTone(x, 25, 0); doc.finish();
        WetWatercolor wet = new WetWatercolor(doc);
        dab(doc, wet, -5, -5, 50, 50, 160);
        dab(doc, wet, 20, 10, 60, 30, 50); settle(wet);
        for (int x=0; x<67; x++) check(doc.tone(x,25)==0, "Dry linework stays black beneath blending");
        check(doc.tone(60,45)==255 && doc.tone(0,50)==255, "Blending stays inside the painted wet footprint");
    }
    private static void dryGaps() {
        ToneDocument doc = new ToneDocument(48, 32);
        WetWatercolor wet = new WetWatercolor(doc);
        dab(doc, wet, 0, 0, 23, 32, 40);
        dab(doc, wet, 24, 0, 24, 32, 200); settle(wet);
        check(doc.tone(22,16)==40 && doc.tone(24,16)==200 && doc.tone(23,16)==255, "Water does not jump a one-pixel dry gap");
    }
    private static void drying() {
        ToneDocument doc = new ToneDocument(64, 64);
        WetWatercolor wet = new WetWatercolor(doc);
        dab(doc, wet, 5, 5, 50, 50, 60); settle(wet);
        // The dryer discards the transient engine; the next wash has a new dry base.
        wet = new WetWatercolor(doc);
        byte[] dried = doc.snapshot();
        dab(doc, wet, 5, 5, 50, 50, 255); settle(wet);
        check(Arrays.equals(dried, doc.snapshot()), "Water cannot lift pigment after drying");
        dab(doc, wet, 5, 5, 50, 50, 128); settle(wet);
        check(doc.tone(30,30)<60, "New paint glazes over the fixed dry wash");
        check(doc.undo() && Arrays.equals(doc.snapshot(), dried), "No-op water stroke does not merge a new wash into older history");
    }
    private static void pausedStrokes(boolean canvas) {
        ToneDocument doc = new ToneDocument(96, 64);
        WetWatercolor wet = canvas ? new WetWatercolor(doc, 100) : new WetWatercolor(doc);
        byte[] blank = doc.snapshot();
        dab(doc, wet, 5, 5, 85, 50, 180);
        wet.advance(false);
        check(wet.isAnimating(), "Start a second stroke while old paint is still seeping");
        byte[] before = doc.snapshot();
        doc.begin(); wet.beginStroke(); mask(wet, 10, 10, 30, 40, 20);
        byte[] held = doc.snapshot(); doc.clearDirty();
        for(int i=0;i<40;i++) check(!wet.advance(true, 0, 0, 96), "Busy drawing leaves no animation budget");
        check(Arrays.equals(held, doc.snapshot()) && doc.dirty() == null,
                "Holding the pen freezes all paint, including older strokes, without dirtying the display");
        check(wet.isAnimating(), "Holding longer than the animation duration preserves pending frames");
        mask(wet, 50, 10, 30, 40, 20);
        check(doc.tone(25,30)==20 && doc.tone(65,30)==20, "The whole active stroke stays crisp");
        mask(wet, 10, 10, 30, 40, 20);
        wet.finishStroke();
        check(wet.advance(false) && doc.tone(25,30)>20, "Blending resumes after pen-up");
        settle(wet); byte[] mixed = doc.snapshot();
        check(doc.undo() && Arrays.equals(doc.snapshot(), before), "Resumed animation shares the new stroke's undo step");
        check(doc.undo() && Arrays.equals(doc.snapshot(), blank) && !doc.canUndo(), "Paused frames add no undo steps");
        check(doc.redo() && Arrays.equals(doc.snapshot(), before), "Interrupted old stroke redoes exactly");
        check(doc.redo() && Arrays.equals(doc.snapshot(), mixed) && !doc.canRedo(), "Resumed blending redoes exactly");
    }
    private static void liveStroke() {
        ToneDocument doc = new ToneDocument(96, 64);
        WetWatercolor wet = new WetWatercolor(doc, 100);
        dab(doc, wet, 5, 5, 85, 50, 180); settle(wet);
        byte[] before = doc.snapshot();
        doc.begin(); wet.beginStroke(); mask(wet, 10, 10, 30, 40, 20);
        check(wet.strokePixels() == 1200, "Measure unique active stroke area");
        mask(wet, 10, 10, 30, 40, 20);
        check(wet.strokePixels() == 1200, "Overlapping stamps do not inflate stroke area");
        for (int i=0;i<12;i++) wet.advance(true, 2, Long.MAX_VALUE, 96);
        int trailing = doc.tone(25,30);
        check(trailing > 20, "Small strokes blend while the pen is down");
        mask(wet, 50, 10, 30, 40, 20);
        check(doc.tone(65,30) == 20, "Fresh tip appears immediately beside a blended tail");
        mask(wet, 10, 10, 30, 40, 20);
        check(doc.tone(25,30) == trailing, "Repeated stamps preserve live blending");
        wet.finishStroke(); settle(wet); byte[] after = doc.snapshot();
        check(doc.undo() && Arrays.equals(before, doc.snapshot()), "Live and idle slices share stroke undo");
        check(doc.redo() && Arrays.equals(after, doc.snapshot()), "Sliced animation redoes exactly");
    }
    private static void boundedSlices() {
        ToneDocument doc = new ToneDocument(512, 320);
        WetWatercolor wet = new WetWatercolor(doc, 100);
        byte[] before = doc.snapshot();
        dab(doc, wet, 0, 0, 512, 320, 30);
        int count = wet.activeTiles();
        check(count > 96, "Exercise more wet tiles than the old frame cap");
        wet.advance(false, 16, 1, 96);
        check(wet.advancedTiles() == 1, "Elapsed time stops calculation at the first tile boundary");
        int processed = 1;
        while (wet.framePending()) {
            doc.clearDirty();
            wet.advance(false, 4, Long.MAX_VALUE, 96);
            check(wet.advancedTiles() > 0 && wet.advancedTiles() <= 4, "Bounded slices continue to make progress");
            int[] bounds = doc.dirty();
            check(bounds == null || (bounds[2]-bounds[0] <= 96 && bounds[3]-bounds[1] <= 96), "Dirty render bounds stay local");
            processed += wet.advancedTiles();
            check(processed <= count, "Each tile gets at most one turn per frame");
        }
        check(processed == count, "Busy wet canvas updates every queued tile fairly");
        settle(wet); byte[] after = doc.snapshot();
        check(doc.undo() && Arrays.equals(before, doc.snapshot()), "Large sliced wash undoes as one stroke");
        check(doc.redo() && Arrays.equals(after, doc.snapshot()), "Large sliced wash redoes exactly");
    }
    private static void adaptiveBudget() {
        WetWorkBudget budget = new WetWorkBudget();
        long small = budget.nanos(true, 500, 4096, 0);
        check(small > 0 && budget.nanos(true, 100000, 4096, 0) < small, "Larger active strokes leave less seep time");
        check(budget.nanos(true, 500, 307200, 0) < small, "More wet paint reduces live seep work");
        budget.input(9_000_000, 0, 100);
        check(budget.nanos(true, 500, 4, 101) == 0, "Expensive pen work pauses seeping");
        check(budget.nanos(false, 500, 4, 101) > small, "Pen-up permits more work immediately");
        check(budget.nanos(true, 500, 4, 201) > 0, "A held still pen can resume blending");
        budget.input(1, 30, 300);
        check(budget.nanos(true, 500, 4, 301) == 0, "Delayed input takes priority over seeping");
        check(budget.tiles(0, true) == 0, "No budget means no tiles");
        int initial = budget.tiles(5_000_000, false);
        budget.completed(10_000_000, 1, true, 500);
        check(budget.tiles(5_000_000, false) < initial && budget.nanos(true, 500, 4, 501) == 0,
                "Measured calculation plus presentation cost throttles the next slice");
    }
    private static void presets() throws Exception {
        ToolLibrary library = new ToolLibrary(); library.select(ToolSettings.Tool.WET_WATERCOLOR);
        library.selectHead(ToolSettings.Head.FLAT); library.edit(library.current().size(91).bristles(43).pressureResponse(75));
        ToolLibrary.Preset preset = library.add();
        ToolLibrary restored = ToolLibrary.decode(library.encode());
        check(restored.current().equals(library.current()) && restored.activeId().equals(preset.id), "Wet brush and custom head settings round trip");
        check(restored.builtin(ToolSettings.Tool.WATERCOLOR).equals(ToolSettings.defaults(ToolSettings.Tool.WATERCOLOR)), "Glazing watercolor settings remain independent");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(0x54535039);
        for(int i=0;i<6;i++) oldSetting(out, ToolSettings.defaults(ToolSettings.Tool.values()[i]));
        for(ToolSettings.Tool tool:new ToolSettings.Tool[]{ToolSettings.Tool.BRUSH, ToolSettings.Tool.WATERCOLOR})
            for(ToolSettings.Head head:ToolSettings.Head.values()) oldSetting(out, ToolSettings.defaults(tool).head(head));
        ToolSettings custom = ToolSettings.defaults(ToolSettings.Tool.WATERCOLOR).head(ToolSettings.Head.FILBERT).bristles(82);
        oldSetting(out, custom); out.writeUTF(preset.id); out.writeInt(1);
        out.writeUTF(preset.id); out.writeUTF("Existing wash"); oldSetting(out, custom); out.flush();
        restored = ToolLibrary.decode(bytes.toByteArray());
        check(restored.current().equals(custom.automaticHead()) && restored.activeId().equals(preset.id), "TSP9 preserves existing watercolor presets");
        check(restored.builtin(ToolSettings.Tool.WET_WATERCOLOR).equals(ToolSettings.defaults(ToolSettings.Tool.WET_WATERCOLOR)), "Older libraries receive the new brush defaults");
    }
    private static void oldSetting(DataOutputStream out, ToolSettings s) throws IOException {
        out.writeByte(s.tool.ordinal()); out.writeInt(s.maximum); out.writeInt(s.tip); out.writeInt(s.softness); out.writeBoolean(s.tilt);
        out.writeInt(s.hardness); out.writeInt(s.minimum); out.writeInt(s.tolerance); out.writeInt(s.strength); out.writeInt(s.pressureResponse);
        out.writeByte(s.head.ordinal()); out.writeInt(s.angle); out.writeInt(s.bristles);
    }
}
