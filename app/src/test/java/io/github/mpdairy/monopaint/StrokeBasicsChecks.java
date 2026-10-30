package io.github.mpdairy.monopaint;

import java.util.Random;

public final class StrokeBasicsChecks {
    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        blur(); hashes(); grain(); tilt(); hand(); tiles(); lattice(); points(); paper();
        System.out.println("PASS: the engine's blur, hashes, noise, stick grain and tilt give the prototype's numbers; float tiles keep the strongest value, clear and copy, read and write rows, and on a big stroke's lattice keep the even pixels and stretch back between them; samples skip under 0.6 px, smooth speed and direction and keep the peak pressure");
    }
    static void blur() {
        float[] a = {0, 1, 0, 0, 2, 0, 0, 0, 3, 1, 1, 1};
        float[] want = {0.6666666865348816f, 0.4444444477558136f, 0.2222222238779068f, 0f, 1.3333333730697632f, 0.8888888955116272f,
                0.4444444477558136f, 0.3333333432674408f, 2f, 1.3333332538604736f, 0.6666666865348816f, 0.6666666865348816f};
        check(java.util.Arrays.equals(StrokeMath.blur(a, 4, 3, 1), want), "The box blur is the prototype's, to the bit");
        Random random = new Random(1); int w = 50, h = 40, r = 3; float[] src = new float[w * h];
        for (int i = 0; i < src.length; i++) src[i] = random.nextFloat();
        float[] got = StrokeMath.blur(src, w, h, r);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            double row = 0;
            for (int dy = -r; dy <= r; dy++) { double col = 0; for (int dx = -r; dx <= r; dx++) col += src[clamp(y + dy, h) * w + clamp(x + dx, w)]; row += col / (2 * r + 1); }
            check(Math.abs(got[y * w + x] - row / (2 * r + 1)) < 1e-5, "and a box blur, edges clamped");
        }
    }
    static int clamp(int v, int n) { return v < 0 ? 0 : v >= n ? n - 1 : v; }
    static void hashes() {
        check(StrokeMath.hash01(12345, 7) == 0.4938501762226224 && StrokeMath.hash01(0xdeadbeef, -3) == 0.5925729863811284, "hash01 as the prototype's");
        check(StrokeMath.noise1(99, 3.7) == 0.6352493548375787 && StrokeMath.noise1((int)4_000_000_000L, .25) == 0.3719984561874298, "noise1 as the prototype's");
        WildBrush.Random rng = new WildBrush.Random(99);
        check(rng.next() == 0.2604658124037087 && rng.next() == 0.8048227655235678, "WildBrush.Random is the prototype's rng");
    }
    static void grain() {
        StickGrain g = StickGrain.of(3);
        check(g.fine(0, 0) == 0.2557336688041687f && g.mid(488, 1) == 0.6832239031791687f && g.low(511, 511) == 0.0683593824505806f, "The stick grain is the prototype's");
        check(g.fine(5, 7) == g.fine(517, 519) && g.fine(5, 7) == g.fine(-507, 7) && StickGrain.of(3) == g, "It tiles every 512 px and is kept");
    }
    static void tilt() {
        check(Math.abs(BrushDirection.azimuth(30, 25) - 0.6793994308245329) < 1e-12 && Math.abs(BrushDirection.lean(30, 25) - 0.5263449511455924) < 1e-6, "Tilt's direction and lean as the prototype's tiltOf");
        check(BrushDirection.tiltLean(30, 25) == 0.5263449511455924 && BrushDirection.tiltLean(-12.3, 47.75) == 0.7216336812779313
                && BrushDirection.azimuth(-12.3, 47.75) == 1.766315498834923 && BrushDirection.tiltLean(2, 2) == 0 && BrushDirection.tiltLean(Double.NaN, 0) == 0,
                "and at a tilt between samples, in doubles, to the bit");
        check(Double.isNaN(BrushDirection.azimuth(2, 2)) && BrushDirection.lean(2, 2) == 0 && Double.isNaN(BrushDirection.azimuth(Float.NaN, 0)) && Double.isNaN(BrushDirection.azimuth(95, 0)), "No direction near upright or without a reading");
        check(Math.abs(StrokeMath.turnToward(0, Math.PI / 2, .5) - Math.PI / 4) < 1e-12 && Math.abs(StrokeMath.turnToward(0, Math.PI, 1)) < 1e-12, "turnToward treats a head as symmetric");
    }
    static void hand() {
        check(StrokeMath.handDark(0, 0, 0, .3, 2, .5) == 1, "The hand's darkness is exactly 1 with every cell off");
        check(Math.abs(StrokeMath.handDark(.5, 0, 0, .2, 0, 0) - .68) < 1e-12 && Math.abs(StrokeMath.handDark(0, 1, 0, 1, 2.5, 0) - .45) < 1e-12 && Math.abs(StrokeMath.handDark(0, 0, 1, 1, 0, 1) - .55) < 1e-12, "pressure, speed and tilt lighten it");
        check(StrokeMath.inkBig(8) == 1 && StrokeMath.inkBig(32) == 2 && StrokeMath.paintBig(220) == 1 && Math.abs(StrokeMath.paintBig(880) - 2) < 1e-12, "Size scaling starts past 16 px lines and 220 px paints");
    }
    static void tiles() {
        FloatTiles t = new FloatTiles(300, 200, 0);
        check(t.get(10, 10) == 0 && t.tile(0, 0, false) == null, "Empty until written");
        check(t.raise(10, 10, .5f) && !t.raise(10, 10, .4f) && t.get(10, 10) == .5f, "raise keeps the stronger");
        t.set(299, 199, 1); check(t.get(299, 199) == 1, "set");
        t.clear(new int[]{0, 0, 299, 199}); check(t.get(10, 10) == 0 && t.get(299, 199) == 0, "clear");
        FloatTiles m = new FloatTiles(300, 200, -1); check(m.get(5, 5) == -1 && m.raise(5, 5, 0) && m.get(5, 5) == 0, "Another fill");
        FloatTiles from = new FloatTiles(300, 200, 0), to = new FloatTiles(300, 200, 0);
        from.set(70, 70, .3f); from.set(200, 10, .6f); to.set(71, 70, .9f); to.set(10, 150, .8f); to.set(250, 150, .7f);
        to.copy(from, new int[]{60, 0, 299, 160});
        check(to.get(70, 70) == .3f && to.get(200, 10) == .6f && to.get(71, 70) == 0 && to.get(250, 150) == 0 && to.get(10, 150) == .8f && to.tile(0, 3, false) == null,
                "copy takes another buffer's values inside a rectangle, nothing outside it");
    }
    static void lattice() {
        FloatTiles t = new FloatTiles(300, 201, 0, 1);
        check(t.latticeWidth == 150 && t.latticeHeight == 101 && t.step() == 2 && t.up(7) == 8 && t.up(8) == 8 && t.radius(3) == 2 && t.radius(1) == 1, "A lattice holds the even pixels");
        t.set(10, 20, .5f); t.raise(11, 21, .7f);
        check(t.get(10, 20) == .7f && t.get(11, 20) == .7f && t.get(12, 20) == 0, "A pixel off the lattice reads and writes the point at or before it");
        float[] row = new float[4]; t.row(4, 10, 4, row, 0);
        check(row[0] == 0 && row[1] == .7f && row[2] == 0, "Rows are read in lattice places");
        t.setRow(140, 100, 3, new float[]{0, .25f, 0}, 0);
        check(t.get(282, 200) == .25f && t.get(280, 200) == 0, "and written");
        t.clear(new int[]{9, 19, 11, 21}); check(t.get(10, 20) == 0, "clear takes the points inside a page rectangle");
        FloatTiles from = new FloatTiles(300, 201, 0, 1); from.set(100, 100, .4f); t.copy(from, new int[]{99, 99, 101, 101});
        check(t.get(100, 100) == .4f, "copy too");
        FloatTiles l = new FloatTiles(20, 20, 0, 1), page = new FloatTiles(20, 20, 0);
        l.set(4, 4, 1); l.set(6, 4, .5f); l.set(4, 6, .25f);
        l.expand(page, new int[]{0, 0, 19, 19}, new float[64]);
        check(page.get(4, 4) == 1 && page.get(5, 4) == .75f && page.get(4, 5) == .625f && page.get(5, 5) == (1 + .5f + .25f) / 4 && page.get(3, 4) == .5f && page.get(9, 9) == 0,
                "expand keeps the lattice's values and takes the mean of the two or four around between them");
    }
    static void points() {
        StrokePoints p = new StrokePoints();
        check(p.add(0, 0, .5f, 0, Float.NaN, Float.NaN) && !p.add(.3f, .3f, .5f, 4, 0, 0), "A sample under 0.6 px from the last is skipped");
        check(p.add(10, 0, .7f, 10, 30, 25) && p.count == 2 && p.d[1] == 10 && Math.abs(p.v[1] - .25) < 1e-6 && p.pk[1] == .7f && p.ux[0] == 1 && p.ux[1] == 1, "distance, speed a quarter at a time, peak, direction");
        p.add(10, 10, .2f, 20, 0, 0);
        double k = 1 - Math.exp(-10 / 12.0), sx = 1 + (0 - 1) * k, sy = k, m = Math.hypot(sx, sy);
        check(Math.abs(p.v[2] - (.25 * .75 + .25)) < 1e-6 && p.pk[2] == .7f && Math.abs(p.ux[2] - sx / m) < 1e-6 && Math.abs(p.uy[2] - sy / m) < 1e-6 && p.tx[1] == 30 && p.ty[1] == 25, "direction smoothed over 12 px; tilt kept");
        check(p.firstAt(15) == 2 && p.firstAt(0) == 1 && Math.abs(p.total() - 20) < 1e-6, "firstAt and total");
        StrokePoints u = new StrokePoints(); u.add(0, 0, .5f, PenSpeed.UNTIMED, 0, 0); u.add(10, 0, .5f, PenSpeed.UNTIMED, 0, 0);
        check(u.v[1] == 0, "Without times the speed stays 0");
    }
    static void paper() {
        StrokePaper plain = new StrokePaper(BrushTexture.PLAIN), laid = new StrokePaper(BrushTexture.LAID);
        PaperGrain g = PaperGrain.PLAIN;
        float want = Math.max(0, Math.min(1, .5f + g.spread * (g.fine(3, 4) / 255f - .5f)));
        check(Math.abs(plain.fine(3, 4) - want) < 1e-6 && plain.low(9, 9) == PaperNoise.low(9, 9) / 255f && plain.blob(9, 9) == PaperNoise.blob(9, 9) / 255f, "The paper's noise is the app's, widened as the old Wild ink widened it");
        int differ = 0; for (int x = 0; x < 64; x++) if (laid.fine(x, 7) != plain.fine(x, 7)) differ++;
        check(differ > 32, "and follows the paper's texture");
    }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
