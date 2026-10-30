package io.github.mpdairy.monopaint;

import java.util.Random;

/**
 * RoundFootprint hands each move over as row spans: exactly the pixels its quarter-width dabs
 * covered when it visited them one dab at a time (kept below as the reference), each once.
 */
public final class RoundFootprintChecks {
    public static void main(String[] args) {
        sameAsDabs();
        fastMove(100, 150, 200, 150);
        fastMove(100, 100, 171, 171);
        System.out.println("PASS: footprint spans give exactly the old dabs' pixels, each once a move; a fast move leaves no gaps and visits each pixel once");
    }

    /** Random paths, on and off the page, sizes changing, tiny moves among them: each move's spans against the old dabs. */
    private static void sameAsDabs() {
        Random random = new Random(11);
        for (int run = 0; run < 400; run++) {
            int width = 20 + random.nextInt(380), height = 20 + random.nextInt(380);
            RoundFootprint footprint = new RoundFootprint();
            Reference reference = new Reference();
            float x = random.nextFloat() * width, y = random.nextFloat() * height, d = 1 + random.nextFloat() * 120;
            for (int move = 0, moves = 2 + random.nextInt(12); move < moves; move++) {
                int kind = random.nextInt(4);
                if (kind == 0) { x += random.nextFloat() * 2 - 1; y += random.nextFloat() * 2 - 1; }
                else if (kind == 1) { x = random.nextFloat() * width * 1.6f - width * .3f; y = random.nextFloat() * height * 1.6f - height * .3f; }
                else { x += random.nextFloat() * 80 - 40; y += random.nextFloat() * 80 - 40; }
                if (random.nextInt(3) == 0) d = random.nextFloat() < .1f ? random.nextFloat() : 1 + random.nextFloat() * 240;
                int[] spans = new int[width * height];
                boolean[] dabs = new boolean[width * height];
                footprint.to(x, y, d, width, height, (row, x0, x1) -> {
                    check(row >= 0 && row < height && x0 >= 0 && x1 <= width && x0 < x1, "Spans lie on the page");
                    for (int px = x0; px < x1; px++) spans[row * width + px]++;
                });
                reference.to(x, y, d, width, height, (px, py) -> dabs[py * width + px] = true);
                for (int i = 0; i < spans.length; i++) {
                    check(spans[i] <= 1, "A move gives each pixel once");
                    check((spans[i] == 1) == dabs[i], "A move gives exactly the pixels its dabs covered");
                }
            }
        }
    }

    /**
     * A 100-pixel move at a 120-pixel diameter: each pixel once, and no gaps, against circles
     * every half pixel along it. The dabs fall a quarter width apart, so the edge between two dips
     * in, by up to r - sqrt(r² - (spacing/2)²); that shallow scalloped rim is the only difference.
     */
    private static void fastMove(float fromX, float fromY, float toX, float toY) {
        int width = 400, height = 320;
        float diameter = 120, r = diameter / 2;
        int[] visits = new int[width * height];
        RoundFootprint footprint = new RoundFootprint();
        footprint.to(fromX, fromY, diameter, width, height, (y, x0, x1) -> { for (int x = x0; x < x1; x++) visits[y * width + x] = 1; });
        int[] move = new int[width * height];
        footprint.to(toX, toY, diameter, width, height, (y, x0, x1) -> { for (int x = x0; x < x1; x++) move[y * width + x]++; });
        double length = Math.hypot(toX - fromX, toY - fromY), spacing = length / Math.ceil(length / (diameter * .25));
        double inner = Math.sqrt(r * r - spacing * spacing / 4) - 1;
        int covered = 0, left = (int)(Math.min(fromX, toX) - r - 2), right = (int)(Math.max(fromX, toX) + r + 2);
        int top = (int)(Math.min(fromY, toY) - r - 2), bottom = (int)(Math.max(fromY, toY) + r + 2);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int i = y * width + x;
            check(move[i] <= 1, "A fast move visits each pixel at most once");
            if (move[i] > 0) covered++;
            boolean painted = visits[i] + move[i] > 0;
            if (x < left || x > right || y < top || y > bottom) { check(!painted, "The stroke stays near its path"); continue; }
            double near = Double.MAX_VALUE;
            for (int k = 0, steps = (int)Math.ceil(length / .5); k <= steps; k++) {
                double t = (double)k / steps;
                near = Math.min(near, Math.hypot(x + .5 - (fromX + (toX - fromX) * t), y + .5 - (fromY + (toY - fromY) * t)));
            }
            check(!painted || near <= r + .01, "The stroke stays inside the circles along its path");
            check(painted || near > inner, "The stroke leaves no gaps inside the circles along its path");
        }
        check(covered > 0, "The move paints");
    }

    /** RoundFootprint as it was, visiting each dab's pixels. */
    private static final class Reference {
        interface Pixel { void at(int x, int y); }
        private float previousX, previousY, previousDiameter;
        private boolean started;
        void to(float x, float y, float diameter, int width, int height, Pixel pixel) {
            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(diameter)) return;
            if (!started) {
                started = true; previousX = x; previousY = y; previousDiameter = diameter;
                dab(x, y, diameter, width, height, pixel); return;
            }
            float distance = (float)Math.hypot(x - previousX, y - previousY);
            float spacing = Math.max(1, Math.min(previousDiameter, diameter) * .25f);
            if (distance < spacing && Math.abs(diameter - previousDiameter) < spacing) return;
            int steps = Math.max(1, (int)Math.ceil(distance / spacing));
            for (int i = 1; i <= steps; i++) {
                float t = (float)i / steps;
                dab(previousX + (x - previousX) * t, previousY + (y - previousY) * t,
                        previousDiameter + (diameter - previousDiameter) * t, width, height, pixel);
            }
            previousX = x; previousY = y; previousDiameter = diameter;
        }
        static void dab(float cx, float cy, float diameter, int width, int height, Pixel pixel) {
            float r = Math.max(.5f, diameter / 2), r2 = r * r;
            int x0 = Math.max(0, (int)Math.floor(cx - r)), x1 = Math.min(width - 1, (int)Math.ceil(cx + r));
            int y0 = Math.max(0, (int)Math.floor(cy - r)), y1 = Math.min(height - 1, (int)Math.ceil(cy + r));
            for (int y = y0; y <= y1; y++) {
                float dy = y + .5f - cy;
                for (int x = x0; x <= x1; x++) {
                    float dx = x + .5f - cx;
                    if (dx * dx + dy * dy <= r2) pixel.at(x, y);
                }
            }
        }
    }
    private static void check(boolean valid, String message) { if (!valid) throw new AssertionError(message); }
}
