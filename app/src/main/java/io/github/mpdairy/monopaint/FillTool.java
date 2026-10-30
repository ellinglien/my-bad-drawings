package io.github.mpdairy.monopaint;

/**
 * Fill's engine, once the bar's paint bucket (the owner, 2026-10-19; retired from the bar 2026-10-23: "maybe this app
 * doesn't use a bucket, just a brush with fill in the lines"; the Fill in brush, Stay in Lines at Flood 1, and a
 * notebook's Page Colour took its work). The area shut in by the crisp ink lines ({@link FillRegion}) round a page
 * pixel is filled with a slot's brush: its medium, paper, grey, Print Shading and drying, laid as a firm, still press
 * of the brush everywhere in the area ({@link ModularStroke#fill}). The fill goes on the selected pair's wash layer,
 * under its ink, so the lines stay on top, untouched; masking fluid keeps it off as it keeps any paint off. One Undo
 * step, as erasable as the brush. With mirrors on, it fills as they draw a stroke ({@link Areas}). Stay in Lines reads
 * its walls ({@link #walls}). Pure, checked on the desktop ({@code FillChecks}).
 *
 * <p>The fill's brush is the slot's, but for two things a stroke has and an area can't: no smudge (nothing to move
 * paint along), and Float lays as Slow (a float is a pen moving on water).
 */
final class FillTool {
    /**
     * The brush a fill lays with {@code slot}'s: a copy with no smudge, Float drying as Slow, and no Gradient (a fill
     * lays its grey at once, with no stroke to shift along).
     */
    static ModularBrush brush(ModularBrush slot) {
        ModularBrush b = slot.copy();
        b.set(BrushPart.CARRY, new ModularBrush.Part(PartChoice.OFF));
        b.part(BrushPart.HAND).set(PartDial.GRADIENT, GreyGradient.OFF);
        if (b.choice(BrushPart.LIFE) == PartChoice.FLOAT) b.set(BrushPart.LIFE, new ModularBrush.Part(PartChoice.DRYING));
        return b;
    }

    /** The layer a fill's walls are read from: the selected pair's ink layer (only its crisp marks hold a fill). */
    static int walls(ToneDocument document) { return InkWashLayers.inkOf(document, InkWashLayers.current(document)); }
    /** The area shut in by the walls and the page's edges round page pixel {@code x, y}; null for none (no room near). */
    static FillRegion.Room region(ToneDocument document, int x, int y) {
        int ink = walls(document);
        return FillRegion.filled(document.width, document.height, (bx, by, bw, bh, out) -> document.layerDarkness(ink, bx, by, bw, bh, out), x, y);
    }

    /**
     * Where one tap fills ({@link #lay}), area by area ({@link #next}): the tap's area at once, the rest as the fill is
     * laid. Mirrors that act ({@link Mirrors}) treat it as they treat a stroke. <b>Copy Strokes</b>, drawn again in each
     * image: the area at the tap and at each of its images ({@link Mirrors.Group#points}), but for an image off the page,
     * on a line or in an area already found (an area that holds several images fills once, found once); a tap whose own
     * place holds no area still fills its images'. <b>Real Mirror</b>, every mark on the kept side: the tap folded into
     * the kept side ({@link Mirrors.View#fold}, as a stroke's samples are), its area as the page shows it (the walls
     * the kept side's, reflected: {@link #seen}, so a shape the mirror closes holds the fill), kept to the kept side,
     * which the view shows on every side. No mirrors: the tap's area.
     */
    static final class Areas {
        private final int width, height;
        private final FillRegion.Ink walls;
        private final int[][] points;
        private final Mirrors.View view;
        private final java.util.List<FillRegion.Room> found = new java.util.ArrayList<>();
        private final int[] one = new int[1];
        private int next;

        Areas(ToneDocument document, int x, int y) {
            width = document.width; height = document.height;
            Mirrors mirrors = document.mirrors();
            int ink = walls(document);
            view = mirrors.view();
            if (view != null) {
                int k = view.cell(x + .5, y + .5);
                double[] p = view.fold(k, x + .5, y + .5);
                points = new int[][]{{(int)Math.floor(p[0]), (int)Math.floor(p[1])}};
                walls = seen(document, ink, view);
                return;
            }
            walls = (bx, by, bw, bh, out) -> document.layerDarkness(ink, bx, by, bw, bh, out);
            double[][] images = mirrors.copies() ? mirrors.group().points(x + .5, y + .5) : new double[][]{{x + .5, y + .5}};
            points = new int[images.length][];
            points[0] = new int[]{x, y};
            for (int i = 1; i < images.length; i++) points[i] = new int[]{(int)Math.floor(images[i][0]), (int)Math.floor(images[i][1])};
        }
        /** The next area to fill, or null when none is left. */
        FillRegion.Room next() {
            while (next < points.length) {
                int i = next++, x = points[i][0], y = points[i][1];
                if (i > 0 && (x < 0 || y < 0 || x >= width || y >= height || inFound(x, y) || walls.darkness(x, y, 1, 1, one) && one[0] > FillRegion.LINE)) continue;
                FillRegion.Room room = FillRegion.filled(width, height, walls, x, y);
                if (room != null && view != null) room = keptSide(room, view);
                if (room == null) continue;
                found.add(room);
                return room;
            }
            return null;
        }
        /** Whether a place is left to look at. */
        boolean more() { return next < points.length; }
        private boolean inFound(int x, int y) {
            for (FillRegion.Room r : found) if (r.inside(x, y)) return true;
            return false;
        }
    }
    /**
     * The fill of {@code areas} ({@code first} its first, already found), each area laid by {@code start} as a fill of
     * its own on the page as the last left it: the first now, the rest at lift ({@link MirrorStroke}), all one Undo step.
     */
    static DrawingStroke lay(ToneDocument document, Areas areas, FillRegion.Room first, java.util.function.Function<FillRegion.Room, DrawingStroke> start) {
        DrawingStroke fill = start.apply(first);
        if (!areas.more()) return fill;
        return new MirrorStroke(document, fill, () -> {
            FillRegion.Room room = areas.next();
            return room == null ? null : start.apply(room);
        });
    }
    /**
     * The walls on {@code document}'s layer {@code ink} as Real Mirror's {@code view} shows them: each pixel the ink of
     * the kept side's pixel it shows ({@link Mirrors.View#row}), none where that is off the page. A block's sources are
     * read at once, as a box round them.
     */
    static FillRegion.Ink seen(ToneDocument document, int ink, Mirrors.View view) {
        int width = document.width, height = document.height;
        return new FillRegion.Ink() {
            private int[] from = new int[0], row = new int[0], read = new int[0];
            @Override public boolean darkness(int x, int y, int w, int h, int[] out) {
                if (from.length < w * h) from = new int[w * h];
                if (row.length < w) row = new int[w];
                int sx0 = width, sy0 = height, sx1 = -1, sy1 = -1;
                for (int r = 0; r < h; r++) {
                    view.row(y + r, x, x + w, width, height, row);
                    System.arraycopy(row, 0, from, r * w, w);
                    for (int i = 0; i < w; i++) {
                        int s = row[i];
                        if (s < 0) continue;
                        int sx = s % width, sy = s / width;
                        if (sx < sx0) sx0 = sx; if (sx > sx1) sx1 = sx; if (sy < sy0) sy0 = sy; if (sy > sy1) sy1 = sy;
                    }
                }
                if (sx1 < 0) return false;
                int bw = sx1 - sx0 + 1, bh = sy1 - sy0 + 1;
                if (read.length < bw * bh) read = new int[bw * bh];
                if (!document.layerDarkness(ink, sx0, sy0, bw, bh, read)) return false;
                boolean any = false;
                for (int i = 0; i < w * h && !any; i++) { int s = from[i]; any = s >= 0 && read[(s / width - sy0) * bw + s % width - sx0] != 0; }
                if (!any) return false;
                for (int i = 0; i < w * h; i++) { int s = from[i]; out[i] = s < 0 ? 0 : read[(s / width - sy0) * bw + s % width - sx0]; }
                return true;
            }
        };
    }
    /** {@code room} kept to {@code view}'s kept side: its pixels in any other cell left out (the same room when it has none there; null for none left). */
    static FillRegion.Room keptSide(FillRegion.Room room, Mirrors.View view) {
        // The cells are convex: a box whose corners are all on the kept side is wholly on it.
        if (view.kept(room.x0 + .5, room.y0 + .5) && view.kept(room.x1 + .5, room.y0 + .5) && view.kept(room.x0 + .5, room.y1 + .5) && view.kept(room.x1 + .5, room.y1 + .5)) return room;
        byte[] at = room.at.clone();
        int area = 0, x0 = room.x1 + 1, y0 = room.y1 + 1, x1 = -1, y1 = -1;
        for (int y = room.y0, i = 0; y <= room.y1; y++) for (int x = room.x0; x <= room.x1; x++, i++) {
            if (at[i] == FillRegion.OUT) continue;
            if (!view.kept(x + .5, y + .5)) { at[i] = FillRegion.OUT; continue; }
            area++;
            if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y;
        }
        if (area == 0) return null;
        int w = x1 - x0 + 1;
        byte[] tight = new byte[w * (y1 - y0 + 1)];
        for (int y = y0; y <= y1; y++) System.arraycopy(at, (y - room.y0) * room.width + x0 - room.x0, tight, (y - y0) * w, w);
        return new FillRegion.Room(x0, y0, x1, y1, tight, area);
    }

    /**
     * The fill of {@code room} with {@code slot}'s brush at {@code size} page px and {@code darkness} (its slot's look),
     * its gesture begun on the selected pair's wash layer (a resist's on the masking fluid), laid; the caller presents
     * and finishes it as a stroke. A Drying wash still settling is laid where it is first.
     */
    static ModularStroke start(ToneDocument document, ModularBrush slot, double size, double darkness, WatercolorSession session, FillRegion.Room room) {
        return start(document, slot, size, darkness, session, room, false);
    }
    /** As above; {@code shape}: Filled Shape's ({@link FilledShape}), on the layer the slot's strokes go to, as its outline is, and as dark ({@link ModularStroke#fillShape}). */
    static ModularStroke start(ToneDocument document, ModularBrush slot, double size, double darkness, WatercolorSession session, FillRegion.Room room, boolean shape) {
        ModularBrush brush = brush(slot);
        if (session != null) { session.settleBefore(document, brush); session.dry("fill"); }
        if (shape) ModularStroke.selectLayer(document, brush);
        else document.selectLayer(InkWashLayers.washOf(document, InkWashLayers.current(document)));
        ModularStroke s = new ModularStroke(document, brush, size, darkness, session);
        if (shape) s.fillShape(room); else s.fill(room);
        return s;
    }

    private FillTool() {}
}
