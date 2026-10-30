package io.github.mpdairy.monopaint;

/** The eraser's coverage: levels, bounds, round paths, bulk copies, row spans and tiles (the wet-joining half went with the old watercolor). */
public final class WashCoverageChecks {
    public static void main(String[] args) {
        WashCoverage a = new WashCoverage(300, 200);
        check(a.cover(10, 10, 50) && !a.cover(10, 10, 20) && a.level(10, 10) == 50, "First cover reports new; weaker keeps stronger");
        a.cover(10, 10, 90); check(a.level(10, 10) == 90, "Stronger level wins");
        check(!a.cover(-1, 0, 9) && !a.cover(300, 0, 9) && a.level(-1, 0) == 0, "Off-page pixels are ignored");

        WashCoverage line = new WashCoverage(300, 200); RoundFootprint path = new RoundFootprint();
        int[] visits = {0};
        path.to(20, 100, 10, 300, 200, (y, x0, x1) -> { for (int x = x0; x < x1; x++) { visits[0]++; line.cover(x, y, 1); } });
        path.to(120, 100, 10, 300, 200, (y, x0, x1) -> { for (int x = x0; x < x1; x++) line.cover(x, y, 1); });
        check(visits[0] > 0, "The first sample lays a dab");
        for (int x = 20; x <= 120; x++) check(line.level(x, 100) > 0, "A drag covers the whole path");
        check(line.level(70, 96) > 0 && line.level(70, 106) == 0, "The path is about as wide as the diameter");
        int[] b = line.bounds();
        check(b[0] >= 14 && b[0] <= 16 && b[2] >= 125 && b[2] <= 127 && b[1] >= 94 && b[3] <= 106, "Bounds enclose the path");
        check(new WashCoverage(10, 10).bounds() == null, "An empty coverage has no bounds");

        RoundFootprint slow = new RoundFootprint(); int[] slowVisits = {0};
        for (int i = 0; i < 200; i++) slow.to(100 + i * .5f, 100, 100, 300, 200, (y, x0, x1) -> slowVisits[0] += x1 - x0);
        check(slowVisits[0] < 6 * 8000, "Tiny pen moves do not repaint the whole dab each sample");

        WashCoverage spread = new WashCoverage(150, 140);
        for (int y = 0; y < 140; y += 3) for (int x = (y * 7) % 5; x < 150; x += 4) spread.cover(x, y, 1 + (x * 31 + y * 17) % 255);
        int[][] regions = {{0, 0, 150, 140}, {60, 50, 30, 40}, {-10, -7, 50, 30}, {130, 120, 40, 40}, {63, 0, 3, 140}, {200, 0, 10, 10}};
        for (int[] r : regions) {
            byte[] out = new byte[r[2] * r[3]];
            java.util.Arrays.fill(out, (byte)99);
            spread.levels(r[0], r[1], r[2], r[3], out);
            for (int y = 0; y < r[3]; y++) for (int x = 0; x < r[2]; x++)
                check((out[y * r[2] + x] & 255) == spread.level(r[0] + x, r[1] + y), "levels() copies each pixel's level, 0 where uncovered or off the page");
        }
        tiles();
        spansAsPixels();
        System.out.println("PASS: coverage levels, bounds, round paths, bulk level copies, tiles and row spans match the pixel-wise ones");
    }
    /** forEachTile visits each tile holding coverage that meets the area, clipped to it and the page, once. */
    private static void tiles() {
        WashCoverage c = new WashCoverage(300, 200);
        c.cover(10, 10, 5); c.cover(130, 70, 5); c.cover(299, 199, 5);
        java.util.List<int[]> seen = new java.util.ArrayList<>();
        c.forEachTile(0, 0, 300, 200, (l, t, r, bt) -> seen.add(new int[]{l, t, r, bt}));
        check(seen.size() == 3 && java.util.Arrays.equals(seen.get(0), new int[]{0, 0, 64, 64}) && java.util.Arrays.equals(seen.get(2), new int[]{256, 192, 300, 200}),
                "Each covered tile once, clipped to the page");
        seen.clear();
        c.forEachTile(100, 60, 140, 80, (l, t, r, bt) -> seen.add(new int[]{l, t, r, bt}));
        check(seen.size() == 1 && java.util.Arrays.equals(seen.get(0), new int[]{128, 64, 140, 80}), "and clipped to the area");
    }
    private static void spansAsPixels() {
        java.util.Random random = new java.util.Random(9);
        WashCoverage spans = new WashCoverage(200, 150), pixels = new WashCoverage(200, 150);
        int[] wanted = new int[300]; byte[] fresh = new byte[300];
        for (int run = 0; run < 2000; run++) {
            int y = random.nextInt(170) - 10, x0 = random.nextInt(240) - 20, x1 = x0 + random.nextInt(60);
            for (int i = 0; i < x1 - x0; i++) wanted[i] = random.nextInt(20) == 0 ? random.nextInt(300) - 20 : 1 + random.nextInt(255);
            int count = spans.cover(y, x0, x1, wanted, fresh), expected = 0;
            for (int x = x0; x < x1; x++) {
                boolean first = pixels.cover(x, y, wanted[x - x0]);
                check((fresh[x - x0] == 1) == first, "A span marks exactly the pixels it covered first");
                if (first) expected++;
            }
            check(count == expected, "A span counts the pixels it covered first");
            check(java.util.Arrays.equals(spans.bounds(), pixels.bounds()), "Spans grow the bounds as pixels do");
        }
        for (int y = 0; y < 150; y++) for (int x = 0; x < 200; x++) check(spans.level(x, y) == pixels.level(x, y), "Spans keep the stronger level as pixels do");
    }
    private static void check(boolean valid, String message) { if (!valid) throw new AssertionError(message); }
}
