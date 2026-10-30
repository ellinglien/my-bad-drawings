package io.github.mpdairy.monopaint;

/**
 * Wet (the wet paint spec, docs/archive/superpowers/specs/2026-10-22-wet-paint-design.md; the owner's Option A and decisions,
 * 2026-10-22): MonoPaint's wet canvas in the six-part engine. Pages and brushes that don't use Wet draw byte for byte as
 * before it; a Wet stroke writes each pixel once under the pen, final; it mixes into the wet paint under it, which moves
 * after the lift, runs downhill on a tilted page and stays wet across other strokes until it is dried; one Undo step with
 * its movement; clear water lifts wet or dry paint; a dragging Wet brush gets dirtier; masking fluid keeps it out; its
 * paint is as erasable as its medium; Pastels and Dry brush strokes join it; Draw once writes each pixel once.
 */
public final class WetPaintChecks {
    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        unchanged();
        finalUnderPen(); mixes(); darkStays(); rewets(); flows(); staysWet(); dries(); undo(); downhill(); clearWater(); dirties(); masking(); erasable(); layers(); wetBrush(); wetBrushRange(); wetInkKept();
        pastel(); dryBrush(); drawOnce(); ticked();
        washOnce(); washMerges(); washCrossesWet(); washEdge(); washBlooms(); washGlazes(); washUndo(); washOthers(); inkWash();
        System.out.println("PASS: brushes without Wet draw every page byte for byte as before it (preview and final, 60 and 200 px, 30 presets of every style, the old Gouache and Oils too); "
                + "a Wet stroke writes each pixel once while the pen is down, mixes into the wet paint under it but not blank paper (dark stays dark), into dried paint for Gouache and not for Oils, moves after the lift and rests, runs downhill on a tilted page, stays wet across ink and pencil strokes and the eraser until the droplet's tap dries it, "
                + "is one Undo step with its movement (Redo kept), lifts wet or dry paint as clear water, gets dirtier dragged through dark paint, stays out of masking fluid, lays paint as erasable as its medium on the pair's wash half; "
                + "a Pastels stroke over it sinks into it and a Dry brush stroke drags it, one Undo step; the Wet brush (ink in water) writes each pixel once, holds its ink between strokes and lets it out, a grey's tap dips it, a clean one soaks wet ink up, it lifts dry paint, its ink stays dark on paper and runs downhill, one Undo step, masked; a full black dip lays near black at full pressure and a pale grey at a light touch, running dry along the stroke; each tool's Wet brush ink is kept between launches, a dip clearing it; Draw once writes each pixel of a Pastels stroke once, final; on the drawing screen's ticks (a settle worker) wet paint waits for a Slow wash and moves after, ending as unpaced; "
                + "a wet watercolour (section 9) writes each pixel once under the pen, two strokes laid wet into wet are one wash (no edge where the first ended), one crossing a wash still wet a while after it shows no seam where they meet and is darker where both lie, it dries back darker at its edge than its middle, "
                + "a stroke into a damp wash blooms (a frillier, darker front than one into a dry wash), it only darkens what is under it (dried paint glazed, never lightened), one Undo step with its movement, and Wet gouache, Wet oils and the Wet brush draw as before it; "
                + "Ink wash's wet washes go on the wash half, cross wet into wet lighter than over a dried one, and Line and wash keeps its pen line crisp and dark beneath its wash");
    }

    // ---- brushes and pages ----

    static final String GOUACHE = "opaque-round.M80b70000000054_2zz00at_0_000_1jz_83z0000000000000000nz0.t4mf73";
    static final String OIL = "oil-paint.M99vleei0000090005e_2zza300_2llp6zz00js_5at_1jz_1ss0000000000000000nz0.20l";
    /** {@code code}'s brush made Wet, Stays Wet {@code stays}, Runs Downhill {@code runs}. */
    static ModularBrush wet(String code, double stays, double runs) {
        ModularBrush b = ModularCode.decode(code);
        ModularBrush.Part life = new ModularBrush.Part(PartChoice.WET_PAINT);
        life.set(PartDial.STAYS_WET, stays); life.set(PartDial.RUNS, runs);
        b.set(BrushPart.LIFE, life);
        return b;
    }
    static ModularBrush gouache() { return wet(GOUACHE, .6, 0); }
    static ModularBrush water() { ModularBrush b = gouache(); b.set(BrushPart.CARRY, new ModularBrush.Part(PartChoice.CLEAR_WATER)); return b; }
    /** A table row's brush, by name. */
    static ModularBrush before(String name) {
        for (String[] row : BEFORE) if (row[0].equals(name)) return ModularCode.decode(row[1]);
        throw new IllegalArgumentException(name);
    }
    static final int W = 480, H = 320;
    static ToneDocument page() { return InkWashLayers.blank(W, H); }
    /** A stroke as the drawing screen starts one ({@link InkWash#stroke}): what settles laid first, wet paint left wet. */
    static DrawingStroke start(ToneDocument d, WatercolorSession s, ModularBrush b, double size, double darkness) {
        s.settleBefore(d, b); s.dry("stroke");
        return QuickStroke.start(d, b, size, darkness, s);
    }
    static void stroke(ToneDocument d, WatercolorSession s, ModularBrush b, double size, double darkness, double[][] path) { CarrySmudgeChecks.feed(start(d, s, b, size, darkness), path); }
    static double[][] line(double x0, double y0, double x1, double y1) { return FloatChecks.line(x0, y0, x1, y1, .8); }
    /** Every slice the session's wet things take until nothing moves; how many. */
    static int settle(WatercolorSession s, ToneDocument d) { int n = 0; while (s.tickDrying(d) && n < 2000) n++; return n; }
    static double mean(ToneDocument d, int x0, int x1, int y0, int y1) { return CarrySmudgeChecks.mean(d, x0, x1, y0, y1); }
    /** Every layer's tones and coverage as a CRC: the page byte for byte. */
    static String crc(ToneDocument d) {
        java.util.zip.CRC32 c = new java.util.zip.CRC32();
        for (int l = 0; l < d.layerCount(); l++) layer(d, l, c);
        return Long.toHexString(c.getValue());
    }
    /** Layer {@code l}'s tones and coverage into {@code c}, read as they are (no layer selected: the wet paint would take that for a change). */
    static void layer(ToneDocument d, int l, java.util.zip.CRC32 c) {
        int[] tones = new int[d.width], cover = new int[d.width];
        for (int y = 0; y < d.height; y++) { d.layerPixels(l, 0, y, d.width, 1, tones, cover); for (int x = 0; x < d.width; x++) { c.update(tones[x]); c.update(cover[x]); } }
    }
    static String composite(ToneDocument d) {
        java.util.zip.CRC32 c = new java.util.zip.CRC32();
        for (int y = 0; y < d.height; y++) for (int x = 0; x < d.width; x++) c.update(d.compositeTone(x, y));
        return Long.toHexString(c.getValue());
    }
    static String layerCrc(ToneDocument d, int layer) {
        java.util.zip.CRC32 c = new java.util.zip.CRC32();
        layer(d, layer, c);
        return Long.toHexString(c.getValue());
    }

    // ---- what doesn't use Wet: byte for byte ----

    /**
     * Brushes without Wet, of every style (the old Gouache and Oils presets among them), each drawn over marks at 60 and
     * 200 px as the drawing screen draws it (previews shown until lift): the page mid-stroke (its composite) and settled
     * at the end (every layer), hashed as they were before Wet (46a7a05), when these were taken. Mid-stroke hashes
     * since the previews changed: Split nib's sweeps its placed line's own nib (speed plan F6), washes' and sticks' are
     * their flat tone at every pixel (F5), Stump's and Water brush's at 60 px their smudge drawn live (2026-10-23, its
     * effect under the pen: QuickStroke.CARRY), Halftone brush's black dots in the panel's pattern (2026-10-24, the
     * ink screens' preview: QuickStroke.Sweep#flatScreen); the ends as before.
     */
    static final String[][] BEFORE = {
            // Settled pages of Split nib, Soft charcoal, Sumi, Hog bristle, Soft pastel and Wax crayon at 200 px, Dry round at 60 and
            // Dry glaze at 200 re-pinned 2026-10-29: the hand's travel through the pressure knee comes back 1 ulp apart (28 of
            // 201 samples); every page mid-stroke unchanged.
            // Settled pages of Opaque round, Impasto, Scumble, Dry glaze and Halftone brush re-pinned 2026-10-29: a crisp or
            // oval body's dabs closer (BodyStamper.RIM_DIP, EdgeRippleChecks); every page mid-stroke unchanged.
            // Settled pages of the lines past 16 px re-pinned 2026-10-29 (Steady line, Swell brush, Wet brush, Chisel marker,
            // Line and wash, Sumi, Mold ink, Chisel pastel, Wax crayon, Dot tone, Halftone brush at 200): the line's grid
            // closer, a crisp nib stepped a grid at most (LineStamper, EdgeRippleChecks); every page mid-stroke unchanged.
            {"Steady line", "steady-line.M00il00000000at_000zz000000000000000_0_00r_1jz_84w0000000000000000nz0.51j8ud", "60", "e6194070", "46705366"},
            {"Steady line", "steady-line.M00il00000000at_000zz000000000000000_0_00r_1jz_84w0000000000000000nz0.51j8ud", "200", "539f09e5", "d611d8c6"},
            {"Swell brush", "swell-brush.M10i0q6f40000at_000t7008h00000000000_0_000_1jz_8wgft00dr0000ey0000nz0.1s13pn4", "60", "f6616c5a", "da4e6eae"},
            {"Swell brush", "swell-brush.M10i0q6f40000at_000t7008h00000000000_0_000_1jz_8wgft00dr0000ey0000nz0.1s13pn4", "200", "f0b85432", "b87ecfba"},
            {"Wet brush", "wet-brush.M10i0d2o54w00at_09ls2009s00000000000_0_100_1jz_8yrfo00000000ny0000nz0.16ebhgk", "60", "c2eb2ed7", "9a06d9e8"},
            {"Wet brush", "wet-brush.M10i0d2o54w00at_09ls2009s00000000000_0_100_1jz_8yrfo00000000ny0000nz0.16ebhgk", "200", "ee32a7b0", "7e98fc1"},
            {"Split nib", "split-nib.M24m0okjmi0000y00_000zz000000000000000_0_000_1jz_8lw00wk0000fq000000nz0.51j8ud", "60", "1a8344d8", "20bb42e"},
            {"Split nib", "split-nib.M24m0okjmi0000y00_000zz000000000000000_0_000_1jz_8lw00wk0000fq000000nz0.51j8ud", "200", "f1b87576", "407c7031"},
            {"Chisel marker", "chisel-marker.M31j2eei000000000at_000zzdj005z000000000_0_000_1jz_82o00000000005u0000nz0.t4mf73", "60", "b03d97fe", "59b0d46e"},
            {"Chisel marker", "chisel-marker.M31j2eei000000000at_000zzdj005z000000000_0_000_1jz_82o00000000005u0000nz0.t4mf73", "200", "5546e2da", "91dc7ab0"},
            {"HB pencil", "hb-pencil.M40i000000000at_4e551_0_1v5_1jz_8arsr00dwjs00000000nz0.pg5zxt", "60", "70c70938", "5195dbd2"},
            {"HB pencil", "hb-pencil.M40i000000000at_4e551_0_1v5_1jz_8arsr00dwjs00000000nz0.pg5zxt", "200", "1eaf831f", "4d701514"},
            // Charcoal's and the pastels' quick preview mid-stroke since it reads the stick's grain (QuickStroke.GRAIN, the
            // owner, 2026-10-22: "charcoal preview is also darker than the resulting mark"); their settled pages unchanged.
            {"Soft charcoal", "soft-charcoal.M50i00000000yat_3eqlj_0_1nl_1jz_8pthh00x9is00000000nz0.51j8ud", "60", "e11ec97", "c1537b13"},
            {"Soft charcoal", "soft-charcoal.M50i00000000yat_3eqlj_0_1nl_1jz_8pthh00x9is00000000nz0.51j8ud", "200", "a614565f", "7455f460"},
            {"Stump", "stump.M50i00000000yat_3eqlj_3p6ll00_1nl_1jz_8pthh00x9is00000000nz0.51j8ud", "60", "64e8f270", "ffd6d3cf"},
            {"Stump", "stump.M50i00000000yat_3eqlj_3p6ll00_1nl_1jz_8pthh00x9is00000000nz0.51j8ud", "200", "4c679207", "ffd6d3cf"},
            {"Soft wash", "soft-wash.M60i0000000007q_1wva200000000ee_0_0fk_1jz_8la0000000000000000nz0.t4mf73", "60", "1b416df7", "c8f24c10"},
            {"Soft wash", "soft-wash.M60i0000000007q_1wva200000000ee_0_0fk_1jz_8la0000000000000000nz0.t4mf73", "200", "5d72cc48", "915592ac"},
            {"Drips", "drips.M60i000000000at_1y67700770000ee_0_05e_2ti7x0jsclllat_8g70000000000000000nz0.1rcs7", "60", "7a039c90", "805a95f"},
            {"Drips", "drips.M60i000000000at_1y67700770000ee_0_05e_2ti7x0jsclllat_8g70000000000000000nz0.1rcs7", "200", "21d0ef25", "58c43c06"},
            {"Water brush", "water-brush.M60i000000000at_1ulat00770000ee_4i0ll_000_2g7ll0jsee00at_0000000000000000000nz0.20k", "60", "a82c7363", "68a72ba9"},
            {"Water brush", "water-brush.M60i000000000at_1ulat00770000ee_4i0ll_000_2g7ll0jsee00at_0000000000000000000nz0.20k", "200", "3219a858", "33cd9580"},
            {"Masking fluid", "masking-fluid.M60i000000000at_5_0_000_1jz_0000000000000000000nz0.px", "60", "64e8f270", "ffd6d3cf"},
            {"Masking fluid", "masking-fluid.M60i000000000at_5_0_000_1jz_0000000000000000000nz0.px", "200", "64e8f270", "ffd6d3cf"},
            {"Line and wash", "line-and-wash.M70i07pmj0000wd_0pipk0000ke000000000_0_000_1jz_8ls0000000000000000nz0.1t996q7", "60", "1ce3bd7e", "764e772d"},
            {"Line and wash", "line-and-wash.M70i07pmj0000wd_0pipk0000ke000000000_0_000_1jz_8ls0000000000000000nz0.1t996q7", "200", "2ba460b3", "16236a60"},
            {"Sumi", "sumi.M70i000000000at_000zz000000000000000_0_000_3lli0i0_25ess00000000000000nz0.15t", "60", "bd67b63e", "5061dfe"},
            {"Sumi", "sumi.M70i000000000at_000zz000000000000000_0_000_3lli0i0_25ess00000000000000nz0.15t", "200", "74d3d4e7", "df8260e4"},
            {"Mold ink", "mold-ink.M70i077at0000at_000zz000000000000000_0_000_4jsjsll_25ess00000000000000nz0.wx", "60", "740c05e0", "d22e500b"},
            {"Mold ink", "mold-ink.M70i077at0000at_000zz000000000000000_0_000_4jsjsll_25ess00000000000000nz0.wx", "200", "34a3c1fc", "abeffc0a"},
            // Mold's settled pages since its scouts move four times a tick (HaloChecks.black; the owner, 2026-10-22).
            {"Opaque round", "opaque-round.M80b70000000054_2zz00at_0_000_1jz_83z0000000000000000nz0.t4mf73", "60", "d89c072e", "5132711a"},
            {"Opaque round", "opaque-round.M80b70000000054_2zz00at_0_000_1jz_83z0000000000000000nz0.t4mf73", "200", "7c16648b", "986ce864"},
            // Wide gouache and Cover hake: hakes, their start (preview and final) laid along the settled direction since (HakeRibbon#settle; the owner, 2026-10-22).
            {"Wide gouache", "wide-gouache.M83h30zz0065_2zz00at_0_100_1jz_80g0000000000000000nz0.51j8ud", "60", "63c21eb2", "7a1296a6"},
            {"Wide gouache", "wide-gouache.M83h30zz0065_2zz00at_0_100_1jz_80g0000000000000000nz0.51j8ud", "200", "f82b4bdb", "9342a2b3"},
            {"Impasto", "impasto.M90i00000il003k_2zzh900_0_500_1jz_8d30000ty0000000000nz0.t4mf73", "60", "e99d3307", "bfc6b0c7"},
            {"Impasto", "impasto.M90i00000il003k_2zzh900_0_500_1jz_8d30000ty0000000000nz0.t4mf73", "200", "d0136c60", "12e8745b"},
            {"Hog bristle", "hog-bristle.M96x7i0lli0i0p6_2zzll00_0_5i0_1jz_8i00000000000000000nz0.20a", "60", "3a6cb9b4", "8b610cdb"},
            {"Hog bristle", "hog-bristle.M96x7i0lli0i0p6_2zzll00_0_5i0_1jz_8i00000000000000000nz0.20a", "200", "1ab2d580", "e06af513"},
            {"Oil paint", "oil-paint.M99vleei0000090005e_2zza300_2llp6zz00js_5at_1jz_1ss0000000000000000nz0.20l", "60", "40a0208a", "1831eafb"},
            {"Oil paint", "oil-paint.M99vleei0000090005e_2zza300_2llp6zz00js_5at_1jz_1ss0000000000000000nz0.20l", "200", "a46b6d6f", "933bcc93"},
            // Soft charcoal's note holds for the pastels.
            {"Chisel pastel", "chisel-pastel.Ma1j2eei000008c00at_34z5w_0_2sl_1jz_89i0000omk400000000nz0.t4mf73", "60", "1e855d9a", "653898c7"},
            {"Chisel pastel", "chisel-pastel.Ma1j2eei000008c00at_34z5w_0_2sl_1jz_89i0000omk400000000nz0.t4mf73", "200", "78eb8200", "2f5a85a4"},
            {"Soft pastel", "soft-pastel.Ma0i000007s00at_3b98o_0_3nj_1jz_8hg0000sdb900000000nz0.51j8ud", "60", "d2993d80", "62262235"},
            {"Soft pastel", "soft-pastel.Ma0i000007s00at_3b98o_0_3nj_1jz_8hg0000sdb900000000nz0.51j8ud", "200", "46def0c4", "86bc3e6f"},
            // Wax crayon's quick preview mid-stroke since it lays its ink on the tooth's peaks (QuickStroke.TOOTH, the owner,
            // 2026-10-22: "the preview for crayon is pretty poor"); its settled pages unchanged.
            {"Wax crayon", "wax-crayon.Ma0p600000000at_7ll0_0_1i0_0_0000000000000000000nz0.2f9", "60", "ab509f80", "4157959f"},
            {"Wax crayon", "wax-crayon.Ma0p600000000at_7ll0_0_1i0_0_0000000000000000000nz0.2f9", "200", "40106ebc", "c08f1ef6"},
            {"Dry round", "dry-round.Mb0i00000in0074_2k700at_0_2r6_1jz_8ny0000000000000000nz0.t4mf73", "60", "1e4e3597", "4721f2e5"},
            {"Dry round", "dry-round.Mb0i00000in0074_2k700at_0_2r6_1jz_8ny0000000000000000nz0.t4mf73", "200", "1c605257", "5542de2f"},
            {"Scumble", "scumble.Mb0i00000xd000d_2oi00at_0_4f9_1jz_8ce0000000000000000nz0.1s13pn4", "60", "c965db5e", "6cae0dea"},
            {"Scumble", "scumble.Mb0i00000xd000d_2oi00at_0_4f9_1jz_8ce0000000000000000nz0.1s13pn4", "200", "2c572252", "415f10ba"},
            {"Dry glaze", "dry-glaze.Mb25aeei00000lx0033_1caat00770000ee_0_1l5_1jz_8l40000000000000000nz0.1t996q7", "60", "9c6b002", "504ada4a"},
            {"Dry glaze", "dry-glaze.Mb25aeei00000lx0033_1caat00770000ee_0_1l5_1jz_8l40000000000000000nz0.1t996q7", "200", "a6ab6aed", "466e6372"},
            {"Bristle brush", "bristle-brush.Mb6rolljsqzi0_000zz000000000000000_0_1ll_1jz_8i00000000000000000nz0.209", "60", "cf78371a", "5e4f76dc"},
            {"Bristle brush", "bristle-brush.Mb6rolljsqzi0_000zz000000000000000_0_1ll_1jz_8i00000000000000000nz0.209", "200", "ba5e1735", "a6b01803"},
            {"Cover hake", "cover-hake.Mc3i00zzedct_2lu00at_0_000_1jz_89d0000000000000000nz0.1t996q7", "60", "3967dffa", "e2cd8829"},
            {"Cover hake", "cover-hake.Mc3i00zzedct_2lu00at_0_000_1jz_89d0000000000000000nz0.1t996q7", "200", "f6c71622", "6ede541c"},
            {"Dot tone", "dot-tone.Me0i00000000000_60e0_0_000_0_0000000000000000000nz0.2e5", "60", "f61fd1b3", "cc038080"},
            {"Dot tone", "dot-tone.Me0i00000000000_60e0_0_000_0_0000000000000000000nz0.2e5", "200", "caad4d4", "d7eb6363"},
            {"Halftone brush", "halftone-brush.Me0zzati00000ss_0p60000000000000000001b2i00090_0_000_0_3p6ll00000000000000nz0.2eh", "60", "88e76b2c", "ef6b783"},
            {"Halftone brush", "halftone-brush.Me0zzati00000ss_0p60000000000000000001b2i00090_0_000_0_3p6ll00000000000000nz0.2eh", "200", "369d629c", "bdb24ead"},
    };
    static void unchanged() {
        boolean trail = QuickStroke.trail;
        QuickStroke.trail = false;
        try {
            for (String[] row : BEFORE) {
                String[] got = hashes(ModularCode.decode(row[1]), Double.parseDouble(row[2]));
                check(got[0].equals(row[3]) && got[1].equals(row[4]), row[0] + " at " + row[2] + " px draws as before Wet: " + got[0] + " " + got[1]);
            }
        } finally { QuickStroke.trail = trail; }
    }
    /** {@code b}'s stroke at {@code size} over marks: the page mid-stroke (its composite) and settled at the end (every layer). */
    static String[] hashes(ModularBrush b, double size) {
        ToneDocument d = InkWashLayers.blank(700, 420);
        // The marks on the plane a brush on the wash layer draws on (kind layers: a stroke works on its own kind's plane).
        int kinds = ResolvedBrush.of(b, size, .8).kind;
        d.selectLayer(InkWashLayers.WASH); d.begin(1, ModularStroke.layerOf(d, b) == InkWashLayers.WASH ? kinds : 0);
        int[] line = new int[700];
        for (int y = 0; y < 420; y++) { for (int x = 0; x < 700; x++) line[x] = Math.floorMod(x + y / 3, 12) < 3 ? 70 : x / 100 % 3 == 1 ? 150 : -1; d.setTones(0, y, 700, 1, line); }
        d.finish();
        WatercolorSession s = new WatercolorSession();
        DrawingStroke st = QuickStroke.start(d, b, size, .8, s);
        long t = 0; String mid = "";
        for (int i = 0; i <= 200; i++) {
            double u = i / 200.0; t += 4;
            st.sample((float) (40 + 600 * u), (float) (210 + 120 * Math.sin(2 * Math.PI * u)), ToolSettings.pressure((float) (.2 + .7 * Math.sin(Math.PI * u))), 10, 20, t);
            if (i % 3 == 0) st.present();
            if (i == 120) mid = composite(d);
        }
        st.finish();
        for (int k = 0; k < 400 && s.settling(); k++) s.tickDrying(d);
        java.util.zip.CRC32 c = new java.util.zip.CRC32();
        // Each layer as it looks, its kind planes too (ToneDocument.layerPixels), as tone() and opacity() read it before planes.
        int[] tones = new int[d.width], cover = new int[d.width];
        for (int l = 0; l < d.layerCount(); l++) for (int y = 0; y < d.height; y++) { d.layerPixels(l, 0, y, d.width, 1, tones, cover); for (int x = 0; x < d.width; x++) { c.update(tones[x]); c.update(cover[x]); } }
        return new String[]{mid, Long.toHexString(c.getValue())};
    }

    // ---- a Wet stroke ----

    /** While the pen is down each pixel the stroke reaches is written once, final: a later present never changes it again. */
    static void finalUnderPen() {
        ToneDocument d = page();
        WatercolorSession s = new WatercolorSession();
        stroke(d, s, gouache(), 60, .9, line(40, 160, 440, 160));
        settle(s, d);
        DrawingStroke st = start(d, s, gouache(), 50, .3);
        check(st instanceof WetPaintStroke, "A Wet Opaque brush's stroke is a Wet stroke");
        int[] last = new int[W * H], changes = new int[W * H];
        for (int i = 0; i < W * H; i++) last[i] = d.compositeTone(i % W, i / W);
        double[][] path = line(240, 30, 240, 290);
        int written = 0, rewritten = 0;
        for (int i = 0; i < path.length; i++) {
            st.sample((float) path[i][0], (float) path[i][1], ToolSettings.pressure((float) path[i][2]), 0, 0, (long) path[i][3]);
            st.present();
            for (int p = 0; p < W * H; p++) {
                int t = d.compositeTone(p % W, p / W);
                if (t != last[p]) { if (changes[p]++ == 0) written++; else rewritten++; last[p] = t; }
            }
            if (i == 4) check(written > 0, "The first samples show at once");
        }
        st.finish();
        check(written > 2000 && rewritten == 0, "Each pixel is written once under the pen: " + written + " written, " + rewritten + " again");
    }
    /** A light stroke across a dark wet one mixes with it: where they cross ends between the two, after the paint moves. */
    static void mixes() {
        ToneDocument d = page();
        WatercolorSession s = new WatercolorSession();
        stroke(d, s, gouache(), 70, 1, line(40, 160, 440, 160));
        settle(s, d);
        double dark = mean(d, 100, 140, 150, 170);
        stroke(d, s, gouache(), 50, .1, line(240, 20, 240, 300));
        settle(s, d);
        double cross = mean(d, 232, 248, 152, 168), light = mean(d, 232, 248, 40, 60);
        check(dark < 160 && cross > dark + 20 && cross < light - 10, "A light stroke over wet dark paint mixes with it: dark " + (int) dark + ", crossing " + (int) cross + ", light " + (int) light);
    }
    /** It mixes only with paint (the owner, 2026-10-22): a black stroke on blank paper settles as dark as it was laid. */
    static void darkStays() {
        ToneDocument d = page();
        WatercolorSession s = new WatercolorSession();
        stroke(d, s, gouache(), 70, 1, line(40, 160, 440, 160));
        double laid = mean(d, 150, 330, 155, 165);
        settle(s, d);
        double settled = mean(d, 150, 330, 155, 165);
        check(settled < laid + 8 && settled < 40, "Dark paint on white paper stays dark: " + (int) laid + " laid, " + (int) settled + " settled");
    }
    /**
     * Dried paint (the droplet tapped) wets again under Gouache, which mixes into it; not under Oils (the owner,
     * 2026-10-22): a light oil stroke covers dried dark paint without mixing, and the dried paint beside it doesn't move.
     */
    static void rewets() {
        double[] cross = new double[2], beside = new double[2], before = new double[2];
        for (int k = 0; k < 2; k++) {
            ToneDocument d = page();
            WatercolorSession s = new WatercolorSession();
            ModularBrush b = k == 0 ? gouache() : wet(OIL_PLAIN, .6, 0);
            stroke(d, s, b, 70, 1, line(40, 160, 440, 160));
            settle(s, d); s.settleNow(d);
            before[k] = mean(d, 100, 140, 150, 170);
            stroke(d, s, b, 40, .05, line(240, 20, 240, 300));
            settle(s, d);
            cross[k] = mean(d, 234, 246, 154, 166); beside[k] = mean(d, 100, 140, 150, 170);
        }
        check(cross[0] < cross[1] - 20, "Gouache mixes into dried paint, Oils cover it: crossing " + (int) cross[0] + " gouache, " + (int) cross[1] + " oils");
        check(Math.abs(beside[1] - before[1]) < 1, "Dried paint beside an oil stroke stays as it was: " + before[1] + " to " + beside[1]);
    }
    /** Opaque oils without a smudge, Wet (Oils style): what dried stays dry under them. */
    static final String OIL_PLAIN = "lean-oils.M90i00000sv0020_2zzug00_0_59x_1jz_8kp0000ht0000000000nz0.ib8b3u";

    /** After the lift the paint moves a while (its edges soften), then rests, still wet. */
    static void flows() {
        ToneDocument d = page();
        WatercolorSession s = new WatercolorSession();
        stroke(d, s, gouache(), 60, .9, line(40, 160, 440, 160));
        String lifted = crc(d);
        check(s.settling() && s.wet(), "After the lift the wet paint moves");
        int slices = settle(s, d);
        check(!crc(d).equals(lifted) && slices > 3 && !s.settling() && s.wet(), "It moves " + slices + " frames, then rests, still wet");
    }
    /** Other strokes (ink, pencil, the eraser) leave it wet: the next Wet stroke mixes into it as into wet paint. */
    static void staysWet() {
        ToneDocument d = page();
        WatercolorSession s = new WatercolorSession();
        stroke(d, s, gouache(), 70, 1, line(40, 160, 440, 160));
        settle(s, d);
        WetCanvas canvas = s.canvas();
        stroke(d, s, before("Steady line"), 6, 1, line(20, 100, 460, 220));
        stroke(d, s, before("HB pencil"), 4, 1, line(20, 220, 460, 100));
        settle(s, d);
        check(s.wet() && s.canvas() == canvas, "An ink line and a pencil line leave the paint wet");
        s.settleBefore(d, null);
        CarrySmudgeChecks.feed(new EraseBothStroke(d, 20f), line(400, 140, 400, 180));
        check(s.wet() && s.canvas() == canvas, "So does the eraser");
        stroke(d, s, gouache(), 40, .1, line(240, 60, 240, 260));
        check(s.canvas() == canvas, "The next Wet stroke lays into the same wet paint");
        settle(s, d);
        check(mean(d, 234, 246, 154, 166) < mean(d, 234, 246, 80, 100) - 10, "and mixes into it");
    }
    /** The page put away (or the droplet's tap, once the paint rests: SettleWorkerChecks) dries it: nothing is wet, and the page is as it was. */
    static void dries() {
        ToneDocument d = page();
        WatercolorSession s = new WatercolorSession();
        stroke(d, s, gouache(), 60, .9, line(40, 160, 440, 160));
        String before = crc(d);
        s.settleNow(d);
        check(!s.wet() && s.canvas() == null && crc(d).equals(before), "Put away, the page dries its wet paint where it is, laying nothing");
        stroke(d, s, gouache(), 60, .2, line(240, 40, 240, 280));
        check(s.canvas() != null, "A Wet stroke after makes it wet again");
    }
    /** A Wet stroke and its movement after are one Undo step; Undo takes them back to the page as it was, and Redo stays. */
    static void undo() {
        ToneDocument d = page();
        WatercolorSession s = new WatercolorSession();
        stroke(d, s, gouache(), 60, .9, line(40, 160, 440, 160));
        settle(s, d);
        String before = crc(d);
        int steps = d.undoSteps();
        stroke(d, s, gouache(), 50, .2, line(240, 30, 240, 290));
        settle(s, d);
        String after = crc(d);
        check(d.undoSteps() == steps + 1, "A Wet stroke and its movement are one Undo step (" + (d.undoSteps() - steps) + ")");
        check(s.undo(d) && crc(d).equals(before), "Undo takes them back together");
        settle(s, d);
        check(crc(d).equals(before) && d.redo() && crc(d).equals(after), "The paint rests after Undo, so Redo gives the stroke back as it ended");
    }
    /** Runs Downhill moves wet paint down a tilted page; at 0 it doesn't. */
    static void downhill() {
        double[] centre = new double[2];
        for (int k = 0; k < 2; k++) {
            ToneDocument d = page();
            WatercolorSession s = new WatercolorSession();
            s.tilt = () -> PageTilt.of(0, .9);
            stroke(d, s, wet(GOUACHE, 1, k), 40, 1, line(60, 120, 420, 120));
            settle(s, d);
            double sum = 0, mass = 0;
            for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) { double ink = 255 - d.compositeTone(x, y); sum += ink * y; mass += ink; }
            centre[k] = sum / mass;
        }
        check(centre[1] > centre[0] + 1.5, "Runs Downhill moves the paint down the tilt: " + String.format(java.util.Locale.ROOT, "%.1f to %.1f", centre[0], centre[1]));
    }
    /** Clear water on a Wet brush lifts paint, wet or dry (a dry Opaque stroke too), and lays none. */
    static void clearWater() {
        ToneDocument d = page();
        WatercolorSession s = new WatercolorSession();
        stroke(d, s, ModularCode.decode(GOUACHE), 70, 1, line(40, 100, 440, 100));
        stroke(d, s, gouache(), 70, 1, line(40, 220, 440, 220));
        settle(s, d); s.settleNow(d);
        stroke(d, s, gouache(), 10, 1, line(470, 300, 475, 305));
        double dry = mean(d, 230, 250, 92, 108), wet = mean(d, 230, 250, 212, 228);
        stroke(d, s, water(), 40, .5, line(240, 20, 240, 300));
        settle(s, d);
        double dryAfter = mean(d, 230, 250, 92, 108), wetAfter = mean(d, 230, 250, 212, 228);
        check(dryAfter > dry + 8 && wetAfter > wet + 8, "Clear water lifts dry and wet paint: " + (int) dry + " to " + (int) dryAfter + ", " + (int) wet + " to " + (int) wetAfter);
        ToneDocument blank = page();
        WatercolorSession b = new WatercolorSession();
        stroke(blank, b, water(), 40, .5, line(240, 20, 240, 300));
        settle(b, blank);
        check(mean(blank, 0, W - 1, 0, H - 1) > 254.9, "and lays nothing on blank paper");
    }
    /** A Wet oil with Paint Load starts in its own grey each stroke and gets dirtier dragged through dark wet paint. */
    static void dirties() {
        double[] after = new double[2];
        for (int k = 0; k < 2; k++) {
            ToneDocument d = page();
            WatercolorSession s = new WatercolorSession();
            if (k == 1) { stroke(d, s, gouache(), 120, 1, line(160, 40, 160, 280)); s.settleNow(d); }
            stroke(d, s, wet(OIL, .8, 0), 30, 0, line(40, 160, 440, 160));
            settle(s, d);
            after[k] = mean(d, 228, 260, 155, 165);
            if (k == 1) check(mean(d, 40, 80, 155, 165) > 250, "Each stroke starts dipped in its own grey (white)");
        }
        check(after[1] < after[0] - 15, "Dragged through dark paint it lays it on after: " + (int) after[0] + " clean, " + (int) after[1] + " dirty");
    }
    /** Masking fluid keeps wet paint out, as it does every other. */
    static void masking() {
        ToneDocument d = page();
        WatercolorSession s = new WatercolorSession();
        d.beginFluid(1);
        for (int y = 120; y < 200; y++) for (int x = 200; x < 280; x++) d.setTone(x, y, 0);
        d.finish();
        stroke(d, s, gouache(), 80, 1, line(40, 160, 440, 160));
        stroke(d, s, gouache(), 80, .3, line(240, 20, 240, 300));
        settle(s, d);
        check(mean(d, 210, 270, 130, 190) > 254.9 && mean(d, 60, 120, 150, 170) < 160, "Masking fluid keeps the wet paint out while it moves");
    }
    /** Its paint, the stroke's and its movement's, is as erasable as its medium (Opaque: the eraser lifts .3) and is paint to an eraser of one kind. */
    static void erasable() {
        ToneDocument d = page();
        WatercolorSession s = new WatercolorSession();
        ModularBrush b = gouache();
        double er = b.part(BrushPart.MEDIUM).get(PartDial.ERASABLE);
        stroke(d, s, b, 80, 1, line(40, 160, 440, 160));
        settle(s, d);
        double laid = mean(d, 100, 380, 150, 170);
        s.settleNow(d);
        CarrySmudgeChecks.feed(new EraseBothStroke(d, 140f, ToneDocument.KIND_PEN), line(20, 160, 460, 160));
        check(Math.abs(mean(d, 100, 380, 150, 170) - laid) < .5, "An eraser of pen ink leaves it: it is paint");
        CarrySmudgeChecks.feed(new EraseBothStroke(d, 140f), line(20, 160, 460, 160));
        double left = mean(d, 100, 380, 150, 170);
        check(er < 1 && left < 250 && left > laid + 5, "The eraser lifts only its Erasable share (" + er + "): " + (int) laid + " to " + (int) left);
    }
    /** It lays on the pair's wash half; an ink line on the ink half above it stays as drawn. */
    static void layers() {
        ToneDocument d = page();
        WatercolorSession s = new WatercolorSession();
        stroke(d, s, before("Steady line"), 6, 1, line(20, 160, 460, 160));
        String ink = layerCrc(d, InkWashLayers.INK);
        stroke(d, s, gouache(), 80, .5, line(240, 20, 240, 300));
        settle(s, d);
        check(s.canvas().layer == InkWashLayers.WASH && layerCrc(d, InkWashLayers.INK).equals(ink), "Wet paint lays on the wash half; the ink half stays as drawn");
    }

    // ---- the Wet brush: ink in water (the wet brush spec) ----

    /** The Wet brush preset (Brush pen): MonoPaint's wet brush pen's ink in water, Wet on a Wash. */
    static ModularBrush inkBrush() { for (ToolPresets.Preset p : ToolPresets.all()) if (p.name.equals("Wet brush")) return p.brush().parts; throw new IllegalStateException(); }
    /** The paint's darkness, summed over the page (255 − tone). */
    static double ink(ToneDocument d) { double sum = 0; for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) sum += 255 - d.compositeTone(x, y); return sum; }
    /**
     * The Wet brush (the wet brush spec; MonoPaint 0.98.3's wet brush pen): each pixel written once under the pen, final;
     * its brush holds its ink between strokes, letting it out along the stroke, until a tap on a grey dips it again; a
     * clean brush soaks wet ink up; it lifts dry paint at once; what it lays on blank paper stays as dark as laid (it mixes
     * only with paint); the ink pools downhill; one Undo step with its movement; masking fluid shields it; the wash half.
     */
    static void wetBrush() {
        ModularBrush b = inkBrush();
        // Once under the pen.
        {
            ToneDocument d = page();
            WatercolorSession s = new WatercolorSession();
            stroke(d, s, gouache(), 60, .9, line(40, 160, 440, 160));
            settle(s, d);
            DrawingStroke st = start(d, s, b, 50, 1);
            check(st instanceof WetPaintStroke, "The Wet brush's stroke is a Wet stroke");
            int[] last = new int[W * H], changes = new int[W * H];
            for (int i = 0; i < W * H; i++) last[i] = d.compositeTone(i % W, i / W);
            double[][] path = line(240, 30, 240, 290);
            int written = 0, rewritten = 0;
            for (double[] q : path) {
                st.sample((float) q[0], (float) q[1], ToolSettings.pressure((float) q[2]), 0, 0, (long) q[3]);
                st.present();
                for (int p = 0; p < W * H; p++) {
                    int tone = d.compositeTone(p % W, p / W);
                    if (tone != last[p]) { if (changes[p]++ == 0) written++; else rewritten++; last[p] = tone; }
                }
            }
            st.finish();
            check(written > 2000 && rewritten == 0, "The Wet brush writes each pixel once under the pen: " + written + " written, " + rewritten + " again");
            check(s.canvas() != null && s.canvas().layer == InkWashLayers.WASH, "on the wet paint of the pair's wash half");
        }
        // Holds its ink: it lets it out along a stroke, the next starts where it left off, and a grey's tap dips it again.
        ToneDocument d = page();
        WatercolorSession s = new WatercolorSession();
        stroke(d, s, b, 40, 1, line(40, 60, 440, 60));
        double first = mean(d, 60, 110, 55, 65), firstEnd = mean(d, 390, 430, 55, 65);
        float left = s.held.wetInk(s.holder());
        check(first < 200 && firstEnd > first + 10 && left > 0 && left < 1, "A dipped Wet brush lays its ink and runs out along the stroke: " + (int) first + " to " + (int) firstEnd + ", " + left + " left");
        settle(s, d);
        check(mean(d, 60, 110, 55, 65) < first + 6, "Ink on blank paper settles as dark as laid (it mixes only with paint): " + (int) first + " to " + (int) mean(d, 60, 110, 55, 65));
        stroke(d, s, b, 40, 1, line(40, 140, 440, 140));
        double second = mean(d, 60, 110, 135, 145);
        check(second > first + 10, "The next stroke starts with what the last left: " + (int) first + ", then " + (int) second);
        s.held.dip(s.holder());
        stroke(d, s, b, 40, 1, line(40, 220, 440, 220));
        check(Math.abs(mean(d, 60, 110, 215, 225) - first) < 3, "A grey's tap dips it: the next stroke starts as full as the first");
        settle(s, d);
        // A clean brush soaks wet ink up, and holds it after.
        s.held.keepWetInk(s.holder(), 0);
        double wetBefore = mean(d, 232, 248, 52, 68);
        stroke(d, s, b, 30, 1, line(240, 52, 240, 68));
        settle(s, d);
        check(mean(d, 232, 248, 52, 68) > wetBefore + 8 && s.held.wetInk(s.holder()) > .01, "A clean brush soaks wet ink up: " + (int) wetBefore + " to " + (int) mean(d, 232, 248, 52, 68) + ", holding " + s.held.wetInk(s.holder()));
        // It lifts dry paint at once.
        {
            ToneDocument p = page();
            WatercolorSession q = new WatercolorSession();
            // Its own kind's paint (kind layers: wet paint re-wets its own plane), dried.
            stroke(p, q, b, 70, 1, line(40, 160, 440, 160));
            settle(q, p); q.settleNow(p);
            double dry = mean(p, 232, 248, 152, 168);
            q.held.keepWetInk(q.holder(), 0);
            stroke(p, q, b, 40, 1, line(240, 20, 240, 300));
            settle(q, p);
            check(mean(p, 232, 248, 152, 168) > dry + 8, "A clean Wet brush lifts dried paint at once: " + (int) dry + " to " + (int) mean(p, 232, 248, 152, 168));
        }
        // The ink pools downhill on a tilted page.
        {
            double[] centre = new double[2];
            for (int k = 0; k < 2; k++) {
                ToneDocument p = page();
                WatercolorSession q = new WatercolorSession();
                if (k == 1) q.tilt = () -> PageTilt.of(0, .9);
                stroke(p, q, b, 40, 1, line(60, 120, 420, 120));
                settle(q, p);
                double sum = 0, mass = 0;
                for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) { double v = 255 - p.compositeTone(x, y); sum += v * y; mass += v; }
                centre[k] = sum / mass;
            }
            check(centre[1] > centre[0] + 1, "Its ink runs downhill on a tilted page: " + String.format(java.util.Locale.ROOT, "%.1f to %.1f", centre[0], centre[1]));
        }
        // One Undo step with its movement; Redo stays.
        {
            ToneDocument p = page();
            WatercolorSession q = new WatercolorSession();
            stroke(p, q, gouache(), 60, .9, line(40, 160, 440, 160));
            settle(q, p);
            String before = crc(p);
            int steps = p.undoSteps();
            stroke(p, q, b, 50, 1, line(240, 30, 240, 290));
            settle(q, p);
            String after = crc(p);
            check(p.undoSteps() == steps + 1 && q.undo(p) && crc(p).equals(before), "A Wet brush stroke and its movement are one Undo step");
            settle(q, p);
            check(p.redo() && crc(p).equals(after), "and Redo gives it back as it ended");
        }
        // Masking fluid shields it.
        {
            ToneDocument p = page();
            WatercolorSession q = new WatercolorSession();
            p.beginFluid(1);
            for (int y = 120; y < 200; y++) for (int x = 200; x < 280; x++) p.setTone(x, y, 0);
            p.finish();
            stroke(p, q, b, 80, 1, line(40, 160, 440, 160));
            settle(q, p);
            check(mean(p, 210, 270, 130, 190) > 254.9 && mean(p, 60, 120, 150, 170) < 220, "Masking fluid keeps the Wet brush's ink out while it runs");
        }
    }

    // ---- Pastels and Dry brush over wet paint ----

    /** A Pastels stroke over wet paint draws on its layer and sinks into it (its marks move with it); over a dry page it draws as ever. */
    static void pastel() {
        ModularBrush pastel = before("Soft pastel");
        ToneDocument d = page();
        WatercolorSession s = new WatercolorSession();
        stroke(d, s, gouache(), 120, .4, line(40, 160, 440, 160));
        settle(s, d);
        String ink = layerCrc(d, InkWashLayers.INK);
        DrawingStroke st = start(d, s, pastel, 20, 1);
        check(d.activeLayer() == InkWashLayers.WASH, "A Pastels stroke over wet paint draws on its layer");
        CarrySmudgeChecks.feed(st, line(60, 160, 420, 160));
        String drawn = crc(d);
        check(s.settling(), "and its marks join the wet paint");
        settle(s, d);
        check(!crc(d).equals(drawn) && layerCrc(d, InkWashLayers.INK).equals(ink), "which moves them (the pastel sinks in); the ink half is untouched");
        ToneDocument dry = page();
        WatercolorSession none = new WatercolorSession();
        st = start(dry, none, pastel, 20, 1);
        check(dry.activeLayer() == InkWashLayers.INK, "Without wet paint a Pastels stroke draws on the ink half as ever");
        CarrySmudgeChecks.feed(st, line(60, 160, 420, 160));
    }
    /** A Dry brush stroke over wet paint drags it along its path (past it, darker than over the same paint dried), one Undo step with the stroke. */
    static void dryBrush() {
        ModularBrush dry = before("Dry round");
        double[] past = new double[2];
        for (int k = 0; k < 2; k++) {
            ToneDocument d = page();
            WatercolorSession s = new WatercolorSession();
            stroke(d, s, gouache(), 60, 1, line(240, 20, 240, 300));
            settle(s, d);
            if (k == 0) s.settleNow(d);
            int steps = d.undoSteps();
            String before = crc(d);
            stroke(d, s, dry, 40, .1, line(160, 160, 420, 160));
            String drawn = crc(d);
            settle(s, d);
            past[k] = mean(d, 274, 300, 150, 170);
            if (k == 0) continue;
            check(!crc(d).equals(drawn) && d.undoSteps() == steps + 1, "A Dry brush stroke over wet paint drags it, one Undo step with its stroke");
            check(s.undo(d) && crc(d).equals(before), "Undo takes the stroke and its drag back together");
        }
        check(past[1] < past[0] - 3, "It carries the wet paint on past it: " + (int) past[0] + " over dried paint, " + (int) past[1] + " over wet");
    }

    // ---- Draw once ----

    /** Draw once (off by default): a Pastels stroke writes each pixel once while drawn, final, and ends as its Instant stroke does. */
    static void drawOnce() {
        check(!QuickStroke.drawOnce, "Draw once is off by default");
        ModularBrush pastel = before("Soft pastel");
        String plain;
        { ToneDocument d = page(); WatercolorSession s = new WatercolorSession(); stroke(d, s, ResolvedBrush.asInstant(pastel), 60, 1, line(40, 160, 440, 160)); plain = crc(d); }
        QuickStroke.drawOnce = true;
        try {
            ToneDocument d = page(); WatercolorSession s = new WatercolorSession();
            DrawingStroke st = start(d, s, pastel, 60, 1);
            check(st instanceof QuickStroke, "Draw once draws a Pastels stroke quick");
            double[][] path = line(40, 160, 440, 160);
            int[] seen = new int[W * H];
            java.util.Arrays.fill(seen, -1);
            for (int i = 0; i < path.length; i++) {
                st.sample((float) path[i][0], (float) path[i][1], ToolSettings.pressure((float) path[i][2]), 0, 0, (long) path[i][3]);
                if (i % 3 == 2) { try { Thread.sleep(2); } catch (InterruptedException e) { throw new RuntimeException(e); } st.present(); }
                for (int p = 0; p < W * H; p++) { int t = d.compositeTone(p % W, p / W); if (t != 255 && seen[p] < 0) seen[p] = t; }
            }
            st.finish();
            int shown = 0, changed = 0;
            for (int p = 0; p < W * H; p++) if (seen[p] >= 0) { shown++; if (seen[p] != d.compositeTone(p % W, p / W)) changed++; }
            check(changed == 0, "Each pixel shown while drawn is final: " + changed + " of " + shown + " changed after");
            check(crc(d).equals(plain), "and the stroke ends as its Instant stroke does");
        } finally { QuickStroke.drawOnce = false; }
    }

    // ---- the drawing screen's ticks ----

    /**
     * As the drawing screen runs them ({@link WatercolorSession#tick}, with a settle worker, no pace): wet paint moves
     * slice by slice; a Slow wash drawn while it moves settles first (on the worker), then the wet paint, which took the
     * wash's change as its paint, rests; all ends.
     */
    /**
     * The Wet brush's range (the owner, 2026-10-22: "we can have a wider range, darker"): a full black dip lays near black
     * on fresh paper at full pressure and a pale grey at a light touch, and still runs dry along the stroke.
     */
    static void wetBrushRange() {
        ModularBrush b = inkBrush();
        double[] full = range(b, 1), light = range(b, .1f);
        check(full[0] < 70, "A full black dip lays near black at full pressure: " + (int) full[0]);
        check(light[0] > 190, "and a pale grey at a light touch: " + (int) light[0]);
        check(full[1] > full[0] + 30 && full[2] > full[1] + 30, "It runs dry along the stroke: " + (int) full[0] + ", " + (int) full[1] + ", " + (int) full[2]);
    }
    /** A 20 px Wet brush dipped in black drawn 800 px across fresh paper at pen travel {@code travel}: its tone at the start, 150 px on, 400 px on. */
    private static double[] range(ModularBrush b, float travel) {
        ToneDocument d = InkWashLayers.blank(900, 120);
        WatercolorSession s = new WatercolorSession();
        DrawingStroke st = start(d, s, b, 20, 1);
        for (int i = 0; i <= 200; i++) st.sample(40 + i * 4, 60, ToolSettings.pressure(travel), 0, 0, i * 8L);
        st.finish();
        return new double[]{mean(d, 45, 60, 57, 63), mean(d, 190, 210, 57, 63), mean(d, 430, 450, 57, 63)};
    }
    /** Each tool's Wet brush ink survives a restart (the owner, 2026-10-22), as MonoPaint keeps its BrushInk; a dip clears it; Brush Lab's isn't kept. */
    static void wetInkKept() {
        java.util.Map<String, String> prefs = new java.util.HashMap<>();
        BrushLibrary.Store store = new BrushLibrary.Store() {
            @Override public String get(String key) { return prefs.get(key); }
            @Override public void put(String key, String value) { if (value == null) prefs.remove(key); else prefs.put(key, value); }
        };
        HeldPaint before = new HeldPaint();
        before.saveWetInkIn(store);
        before.keepWetInk("slot3", .37f);
        before.keepWetInk(HeldPaint.ANY, .5f);
        HeldPaint after = new HeldPaint();
        after.saveWetInkIn(store);
        check(Math.abs(after.wetInk("slot3") - .37f) < 1e-3, "A tool's Wet brush ink is kept between launches: " + after.wetInk("slot3"));
        check(Float.isNaN(after.wetInk(HeldPaint.ANY)) && Float.isNaN(after.wetInk("slot4")), "Brush Lab's test brush and a tool never used start dipped");
        after.dip("slot3");
        HeldPaint again = new HeldPaint();
        again.saveWetInkIn(store);
        check(Float.isNaN(again.wetInk("slot3")), "A dip clears what was kept: the brush starts in its grey");
        check(HeldPaint.readWetInk("x") == null && HeldPaint.readWetInk("1.5 0") == 1f, "What doesn't read is no ink kept; fullness stays 0 to 1");
    }
    static void ticked() {
        ToneDocument d = page();
        WatercolorSession s = new WatercolorSession();
        s.worker = new SettleWorker();
        stroke(d, s, gouache(), 60, .9, line(40, 160, 440, 160));
        int slices = run(s, d, 3);
        check(slices >= 3 && s.canvas() != null && s.settling(), "Wet paint moves a slice a tick");
        stroke(d, s, before("Soft wash"), 60, .5, line(240, 40, 240, 280));
        run(s, d, 100000);
        check(!s.settling() && s.wet() && s.canvas() != null && s.drying() == null, "A Slow wash settles, the wet paint rests after, still wet");
        stroke(d, s, gouache(), 50, .3, line(40, 260, 440, 260));
        run(s, d, 100000);
        check(!s.settling() && s.wet(), "and moves again after the next Wet stroke, to rest");
    }
    /** Up to {@code most} ticks while anything is to do; how many. */
    static int run(WatercolorSession s, ToneDocument d, int most) {
        int n = 0;
        for (long next; n < most && (next = s.tick(d, d::clearDirty)) >= 0; n++)
            if (next == SettleWorker.POLL_MS) { try { Thread.sleep(1); } catch (InterruptedException e) { throw new RuntimeException(e); } }
        return n;
    }

    // ---- wet watercolour (section 9) ----

    static ModularBrush wash(String name) {
        for (ToolPresets.Preset p : ToolPresets.all()) if (p.name.equals(name) && p.style.name.equals("Watercolor")) return p.brush().parts;
        throw new IllegalArgumentException(name);
    }
    /** Under the pen each pixel is written once, final, as every Wet stroke; the stroke is a wet watercolour one. */
    static void washOnce() {
        ToneDocument d = page();
        WatercolorSession s = new WatercolorSession();
        DrawingStroke st = start(d, s, wash("Soft wash"), 50, .7);
        check(st instanceof WetPaintStroke && ResolvedBrush.of(wash("Soft wash"), 50, .7).wet.wash != null, "A Watercolor wash's stroke is a wet watercolour stroke");
        int[] last = new int[W * H], changes = new int[W * H];
        for (int i = 0; i < W * H; i++) last[i] = d.compositeTone(i % W, i / W);
        double[][] path = line(60, 160, 420, 160);
        int written = 0, rewritten = 0;
        for (double[] p : path) {
            st.sample((float) p[0], (float) p[1], ToolSettings.pressure((float) p[2]), 0, 0, (long) p[3]);
            st.present();
            for (int q = 0; q < W * H; q++) { int tone = d.compositeTone(q % W, q / W); if (tone != last[q]) { if (changes[q]++ == 0) written++; else rewritten++; last[q] = tone; } }
        }
        st.finish();
        check(written > 2000 && rewritten == 0, "A wash writes each pixel once under the pen: " + written + " written, " + rewritten + " again");
    }
    /** The largest step between neighbouring rows' mean tone, x {@code x0..x1}, rows {@code y0..y1} (the paper's grain averaged out). */
    static double step(ToneDocument d, int x0, int x1, int y0, int y1) {
        double most = 0;
        for (int y = y0; y < y1; y++) most = Math.max(most, Math.abs(mean(d, x0, x1, y + 1, y + 2) - mean(d, x0, x1, y, y + 1)));
        return most;
    }
    /**
     * Two strokes laid wet into wet are one wash: where the first ended inside the second there is no edge (no rim, no
     * step), as there is when the second is laid after the droplet's tap dried the first.
     */
    static void washMerges() {
        double[] edge = new double[2];
        for (int k = 0; k < 2; k++) {
            ToneDocument d = page();
            WatercolorSession s = new WatercolorSession();
            // Soft wash: no tide lines of its own (Backruns 0) to step across.
            stroke(d, s, wash("Soft wash"), 60, .6, line(40, 140, 440, 140));
            if (k == 1) { settle(s, d); s.settleNow(d); }
            stroke(d, s, wash("Soft wash"), 60, .6, line(40, 175, 440, 175));
            settle(s, d);
            // The first stroke's lower edge (about y 170), now inside the second.
            edge[k] = step(d, 120, 360, 160, 185);
        }
        check(edge[0] < edge[1] / 3 && edge[0] < 8, "Wet into wet is one wash, no edge where the first stroke ended (" + (int) edge[0] + "), as there is over a dried one (" + (int) edge[1] + ")");
    }
    /**
     * The owner, 2026-10-23 ("watercolor overlapping wet strokes don't blend"): a stroke crossing a wash 30 frames after it,
     * once that wash has dried back past where a stroke used to join it (its edge further still), still shares its water:
     * no seam where they meet (the steepest step across either stroke's sides inside the other, the paper's grain averaged
     * out, as small as when it crosses at once), darker where both lie; crossing it once dried, it glazes over its edge.
     */
    static void washCrossesWet() {
        String[] names = {"Soft wash", "Wet blooms", "Grey wash", "Flat wash"};
        for (int p = 0; p < names.length; p++) {
            String name = names[p];
            // Two watercolours, then two of Ink wash's at their Medium size.
            ModularBrush b = p < 2 ? wash(name) : inkWash(name);
            double size = p < 2 ? 60 : ModularSizes.size(b, InkWashSizes.Preset.MEDIUM);
            // Crossing at once, 30 frames on, once dried; and the first stroke alone.
            double[] seam = new double[3], middle = new double[4];
            for (int k = 0; k < 4; k++) {
                ToneDocument d = page();
                WatercolorSession s = new WatercolorSession();
                stroke(d, s, b, size, .7, line(40, 160, 440, 160));
                for (int f = 0; f < (k == 0 ? 0 : k == 1 ? 30 : 5000) && s.tickDrying(d); f++) { }
                if (k < 3) stroke(d, s, b, size, .7, line(240, 20, 240, 300));
                settle(s, d);
                middle[k] = mean(d, 235, 245, 155, 165);
                if (k == 3) break;
                double rows = 0, columns = 0;
                for (int y = 90; y < 230; y++) rows = Math.max(rows, Math.abs(mean(d, 235, 245, y + 1, y + 1) - mean(d, 235, 245, y, y)));
                for (int x = 150; x < 330; x++) columns = Math.max(columns, Math.abs(mean(d, x + 1, x + 1, 155, 165) - mean(d, x, x, 155, 165)));
                seam[k] = Math.max(rows, columns);
            }
            check(seam[1] < seam[0] + 16 && seam[2] > seam[0] + 25 && middle[1] < middle[3] - 10,
                    name + ": crossing a wash still wet 30 frames on shows no seam (" + (int) seam[1] + "; at once " + (int) seam[0] + ", over it dried " + (int) seam[2] + "), darker where both lie (" + (int) middle[1] + ", the first alone " + (int) middle[3] + ")");
        }
    }
    /** As it dries back a wash darkens at its edge: its rim darker than its middle; and it rests, still wet. */
    static void washEdge() {
        ToneDocument d = page();
        WatercolorSession s = new WatercolorSession();
        stroke(d, s, wash("Wet blooms"), 70, .6, line(60, 160, 420, 160));
        double laidRim = rim(d), laidMid = mean(d, 150, 330, 154, 166);
        settle(s, d);
        double rim = rim(d), mid = mean(d, 150, 330, 154, 166);
        check(Math.abs(laidRim - laidMid) < 12 && rim < mid - 15 && s.wet() && !s.settling(), "Its edge darkens as it dries back: rim " + (int) laidRim + " to " + (int) rim + ", middle " + (int) laidMid + " to " + (int) mid);
    }
    /** The darkest of the rows just inside the wash's upper edge, averaged along it. */
    static double rim(ToneDocument d) {
        double sum = 0; int n = 0;
        for (int x = 150; x < 330; x++) {
            int top = -1;
            for (int y = 100; y < 160 && top < 0; y++) if (d.compositeTone(x, y) < 250) top = y;
            int darkest = 255;
            for (int y = top; y < top + 5; y++) darkest = Math.min(darkest, d.compositeTone(x, y));
            sum += darkest; n++;
        }
        return sum / n;
    }
    /**
     * A fresh stroke laid beside a wash gone damp blooms: its water floods the damp wash and leaves a frilly dark front
     * there; beside a wash the droplet dried there is only the stroke's own edge.
     */
    static void washBlooms() {
        double[] rough = new double[2];
        for (int k = 0; k < 2; k++) {
            ToneDocument d = page();
            WatercolorSession s = new WatercolorSession();
            stroke(d, s, wash("Backrun"), 70, .6, line(40, 130, 440, 130));
            settle(s, d);
            if (k == 1) s.settleNow(d);
            stroke(d, s, wash("Backrun"), 60, .6, line(40, 175, 440, 175));
            settle(s, d);
            // How much the first wash (rows 100..150) changed: what the bloom did to it.
            double sum = 0; int n = 0;
            for (int x = 60; x < 420; x++) for (int y = 100; y < 150; y++) { sum += Math.abs(d.compositeTone(x, y) - d.compositeTone(x, y + 1)); n++; }
            rough[k] = sum / n;
        }
        check(rough[0] > rough[1] * 1.15, "A stroke into a damp wash blooms into it (" + String.format("%.2f", rough[0]) + " a pixel's change, " + String.format("%.2f", rough[1]) + " beside a dried one)");
    }
    /** Transparent: a wash over dried paint only darkens it, never lightens a pixel. */
    static void washGlazes() {
        ToneDocument d = page();
        WatercolorSession s = new WatercolorSession();
        // Dried wash (kind layers: a wash lies under opaque paint, so it glazes over paint of its own plane).
        stroke(d, s, wash("Soft wash"), 60, .9, line(40, 160, 440, 160));
        settle(s, d); s.settleNow(d);
        int[] before = new int[W * H];
        for (int i = 0; i < W * H; i++) before[i] = d.compositeTone(i % W, i / W);
        stroke(d, s, wash("Soft wash"), 70, .5, line(240, 20, 240, 300));
        settle(s, d); s.settleNow(d);
        int lighter = 0, darker = 0;
        for (int i = 0; i < W * H; i++) { int t = d.compositeTone(i % W, i / W); if (t > before[i] + 1) lighter++; else if (t < before[i] - 1) darker++; }
        check(lighter == 0 && darker > 3000 && mean(d, 232, 248, 154, 166) < mean(before, 232, 248, 154, 166) - 10, "A wash glazes over dried paint, darker, never lighter: " + darker + " darker, " + lighter + " lighter");
    }
    static double mean(int[] tones, int x0, int x1, int y0, int y1) { double sum = 0; int n = 0; for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) { sum += tones[y * W + x]; n++; } return sum / n; }
    /** A wash and its drying back are one Undo step; Redo gives it back as it ended. */
    static void washUndo() {
        ToneDocument d = page();
        WatercolorSession s = new WatercolorSession();
        stroke(d, s, wash("Wet blooms"), 60, .6, line(40, 140, 440, 140));
        settle(s, d);
        String before = crc(d);
        int steps = d.undoSteps();
        stroke(d, s, wash("Wet blooms"), 60, .6, line(240, 30, 240, 290));
        settle(s, d);
        String after = crc(d);
        check(d.undoSteps() == steps + 1 && s.undo(d) && crc(d).equals(before), "A wash and its drying back are one Undo step");
        settle(s, d);
        check(crc(d).equals(before) && d.redo() && crc(d).equals(after), "and Redo gives it back as it ended");
    }
    /**
     * Wet gouache, Wet oils and the Wet brush take none of the watercolour's paths: their pages hash as before it (taken
     * on inkwash-v2-lab 9be0dab), each on a canvas of its own.
     */
    static void washOthers() {
        String[][] before = {{"Wet gouache", "579cc49b 73e7037e"}, {"Wet oils", "f5651f26 e48da720"}, {"Wet brush", "a0e342b2 2ed8d94e"}};
        for (String[] row : before) {
            ModularBrush b = null;
            for (ToolPresets.Preset p : ToolPresets.all()) if (p.name.equals(row[0])) b = p.brush().parts;
            String got = String.join(" ", hashes(b, 120));
            check(got.equals(row[1]), row[0] + " draws as before wet watercolour: " + got);
        }
    }

    /**
     * Ink wash on the wet canvas (the ink wash note, 2026-10-22: "less dark where they overlap, especially when wet"): two
     * Grey wash strokes crossing at once are lighter where they overlap than when the second crosses a dried first; Line
     * and wash lays its pen line beneath its wash, on the wash half with it, crisp (as dark across its width after the
     * wash dries back as laid) and darker than the wash beside it.
     */
    static void inkWash() {
        double[] overlap = new double[2];
        for (int k = 0; k < 2; k++) {
            ToneDocument d = page();
            WatercolorSession s = new WatercolorSession();
            stroke(d, s, inkWash("Grey wash"), 90, .6, line(40, 160, 440, 160));
            if (k == 1) { settle(s, d); s.settleNow(d); }
            stroke(d, s, inkWash("Grey wash"), 90, .6, line(240, 20, 240, 300));
            settle(s, d);
            overlap[k] = mean(d, 225, 255, 145, 175);
        }
        check(overlap[0] > overlap[1] + 15, "Ink wash crossing wet into wet is lighter where they overlap (" + (int) overlap[0] + ") than over a dried one (" + (int) overlap[1] + ")");
        ToneDocument d = page();
        WatercolorSession s = new WatercolorSession();
        stroke(d, s, inkWash("Line and wash"), 8, .7, line(60, 160, 420, 160));
        int[] laid = new int[11];
        for (int y = 155; y <= 165; y++) laid[y - 155] = d.compositeTone(240, y);
        settle(s, d);
        int line = 255, beside = d.compositeTone(240, 176), changed = 0;
        for (int y = 155; y <= 165; y++) { line = Math.min(line, d.compositeTone(240, y)); if (Math.abs(d.compositeTone(240, y) - laid[y - 155]) > 12 && laid[y - 155] < 140) changed++; }
        d.selectLayer(InkWashLayers.INK);
        int inked = 0;
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) if (d.opacity(x, y) > 0) inked++;
        check(line < 110 && beside > line + 60 && beside < 250 && changed == 0 && inked == 0, "Line and wash: its pen line dark (" + line + ") and crisp (" + changed + " line pixels moved) beneath its wash (" + beside + "), all on the wash half (" + inked + " inked)");
    }
    static ModularBrush inkWash(String name) {
        for (ToolPresets.Preset p : ToolPresets.all()) if (p.name.equals(name) && p.style.name.equals("Ink wash")) return p.brush().parts;
        throw new IllegalArgumentException(name);
    }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
