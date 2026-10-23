package io.github.mpdairy.monopaint;

/**
 * A piece of the active layer lifted by the Lasso, or an imported picture, floating over the
 * page. It moves, scales evenly and turns about its center; the page stays untouched until
 * {@link #place}, which lays it as one undo step.
 *
 * <p>A page point p shows piece point q where p = center + R(angle) · scale · (q − size/2),
 * q in piece pixels.
 */
final class FloatingSelection {
    /** What a pen-down at a point grabs: nothing, the piece itself, its turning knob, or corner {@code SCALE + i}. */
    static final int NONE = -1, MOVE = 0, ROTATE = 1, SCALE = 2;
    /** Turns within this many degrees of a quarter turn settle on it. */
    private static final double SNAP = 4;
    final int width, height;
    /** The piece's raw tones and coverage, row by row; transparent outside what was lifted. */
    final byte[] tones, alpha;
    /** Lifted from the active layer at ({@link #cutX}, {@link #cutY}), which placing clears; an import cuts nothing. */
    final boolean lifted;
    final int cutX, cutY;
    /** Placed as a new layer above the active one rather than onto it. */
    final boolean newLayer;
    private final int pageWidth, pageHeight;
    float centerX, centerY, scale = 1;
    /** Radians, clockwise on the page. */
    double angle;
    // The pose when the current drag began, and the corner a scaling drag holds still.
    private float startX, startY, startScale, anchorX, anchorY;
    private double startAngle;
    /**
     * The edge the knob stands beside, chosen when first shown and kept as the piece turns, so
     * the knob stays where the pen left it; -1 until then. Once inside the piece it stays inside.
     */
    private int knobEdge = -1;
    private boolean knobInside, turning;

    private FloatingSelection(int pageWidth, int pageHeight, int width, int height, byte[] tones, byte[] alpha,
                              boolean lifted, int x, int y, boolean newLayer) {
        this.pageWidth = pageWidth; this.pageHeight = pageHeight;
        this.width = width; this.height = height; this.tones = tones; this.alpha = alpha;
        this.lifted = lifted; cutX = x; cutY = y; this.newLayer = newLayer;
        centerX = x + width / 2f; centerY = y + height / 2f;
    }

    /**
     * Lifts what the active layer has inside the closed loop through {@code count} points
     * ({@code x, y} pairs, page coordinates), or returns null when it has nothing there.
     */
    static FloatingSelection lift(ToneDocument document, float[] points, int count) {
        if (count < 3) return null;
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            minX = Math.min(minX, points[2*i]); maxX = Math.max(maxX, points[2*i]);
            minY = Math.min(minY, points[2*i+1]); maxY = Math.max(maxY, points[2*i+1]);
        }
        int left = Math.max(0, (int)Math.floor(minX)), top = Math.max(0, (int)Math.floor(minY));
        int right = Math.min(document.width, (int)Math.ceil(maxX)), bottom = Math.min(document.height, (int)Math.ceil(maxY));
        if (left >= right || top >= bottom) return null;
        int w = right - left, h = bottom - top;
        byte[] tones = new byte[w * h], alpha = new byte[w * h];
        document.copyLayer(left, top, w, h, tones, alpha);
        // Keep what lies inside the loop: pixel centers between pairs of edge crossings on their row.
        float[] crossings = new float[count];
        int l = w, t = h, r = 0, b = 0;
        for (int row = 0; row < h; row++) {
            float y = top + row + .5f;
            int found = 0;
            for (int i = 0, j = count - 1; i < count; j = i++) {
                float y0 = points[2*j+1], y1 = points[2*i+1];
                if ((y0 <= y) == (y1 <= y)) continue;
                float x0 = points[2*j], x1 = points[2*i];
                crossings[found++] = x0 + (y - y0) / (y1 - y0) * (x1 - x0);
            }
            java.util.Arrays.sort(crossings, 0, found);
            int pair = 0;
            for (int col = 0; col < w; col++) {
                float x = left + col + .5f;
                while (pair < found && crossings[pair] <= x) pair++;
                int i = row * w + col;
                if (pair % 2 == 0) { tones[i] = (byte)255; alpha[i] = 0; }
                else if (alpha[i] != 0) { l = Math.min(l, col); r = Math.max(r, col + 1); t = Math.min(t, row); b = Math.max(b, row + 1); }
            }
        }
        if (l >= r) return null;
        // Trim to what was found, so the handles hug the artwork.
        int cw = r - l, ch = b - t;
        byte[] pieceTones = new byte[cw * ch], pieceAlpha = new byte[cw * ch];
        for (int row = 0; row < ch; row++) {
            System.arraycopy(tones, (t + row) * w + l, pieceTones, row * cw, cw);
            System.arraycopy(alpha, (t + row) * w + l, pieceAlpha, row * cw, cw);
        }
        return new FloatingSelection(document.width, document.height, cw, ch, pieceTones, pieceAlpha, true, left + l, top + t, false);
    }

    /**
     * A picture to place, centered on the page and turned by {@code angle} so it stands upright
     * in the app's current orientation.
     */
    static FloatingSelection imported(ToneDocument document, int width, int height, byte[] tones, byte[] alpha,
                                      double angle, boolean newLayer) {
        if (width <= 0 || height <= 0 || tones.length != width * height || alpha.length != tones.length)
            throw new IllegalArgumentException("Invalid picture");
        FloatingSelection picture = new FloatingSelection(document.width, document.height, width, height, tones, alpha,
                false, 0, 0, newLayer);
        picture.centerX = document.width / 2f; picture.centerY = document.height / 2f;
        picture.angle = angle;
        picture.align();
        return picture;
    }

    // Geometry

    /** At its own size and a quarter turn, keeps the piece on whole pixels so placing it copies them exactly. */
    private void align() {
        double quarters = angle / (Math.PI / 2);
        if (scale != 1 || quarters != Math.rint(quarters)) return;
        boolean sideways = Math.abs(Math.rint(quarters)) % 2 == 1;
        float across = sideways ? height : width, down = sideways ? width : height;
        centerX = Math.round(centerX - across / 2) + across / 2; centerY = Math.round(centerY - down / 2) + down / 2;
    }

    /** The page point showing piece point ({@code x}, {@code y}). */
    private float[] toPage(float x, float y) {
        double c = Math.cos(angle) * scale, s = Math.sin(angle) * scale, dx = x - width / 2.0, dy = y - height / 2.0;
        return new float[]{(float)(centerX + c * dx - s * dy), (float)(centerY + s * dx + c * dy)};
    }
    /** The four corners on the page, clockwise from the piece's top left: x, y pairs. */
    float[] corners() {
        float[] corners = new float[8];
        float[][] at = {toPage(0, 0), toPage(width, 0), toPage(width, height), toPage(0, height)};
        for (int i = 0; i < 4; i++) { corners[2*i] = at[i][0]; corners[2*i+1] = at[i][1]; }
        return corners;
    }
    /**
     * The page-to-piece transform, {a, b, c, d, e, f}: page pixel (x, y)'s center shows piece
     * point (a·x' + b·y' + c, d·x' + e·y' + f), where x' = x + ½ and y' = y + ½.
     */
    float[] inverse() {
        double cos = Math.cos(angle) / scale, sin = Math.sin(angle) / scale;
        return new float[]{(float)cos, (float)sin, (float)(width / 2.0 - cos * centerX - sin * centerY),
                (float)-sin, (float)cos, (float)(height / 2.0 + sin * centerX - cos * centerY)};
    }
    /** The page pixels the piece covers now: left, top, right, bottom, clipped to the page; empty when off it. */
    int[] bounds() {
        float[] c = corners();
        float minX = Math.min(Math.min(c[0], c[2]), Math.min(c[4], c[6])), maxX = Math.max(Math.max(c[0], c[2]), Math.max(c[4], c[6]));
        float minY = Math.min(Math.min(c[1], c[3]), Math.min(c[5], c[7])), maxY = Math.max(Math.max(c[1], c[3]), Math.max(c[5], c[7]));
        // Ignore rounding error in a quarter turn's corners, which lie on whole pixels.
        float slack = 1e-3f;
        return new int[]{Math.max(0, (int)Math.floor(minX + slack)), Math.max(0, (int)Math.floor(minY + slack)),
                Math.min(pageWidth, (int)Math.ceil(maxX - slack)), Math.min(pageHeight, (int)Math.ceil(maxY - slack))};
    }
    /** Where the piece was lifted from, which shows as a hole until it is placed; null for an import. */
    int[] cutBounds() { return lifted ? new int[]{cutX, cutY, cutX + width, cutY + height} : null; }

    /**
     * The turning knob, {@code distance} beyond the middle of its edge: at first the edge that
     * faces up the screen, ({@code upX}, {@code upY}) on the page. It moves just inside that
     * edge, for good, once beyond it would leave {@code visible} (left, top, right, bottom: the
     * page area on screen), but not during a turn, which would take it from under the pen.
     * @return the knob and the edge's middle: x, y, x, y; then the edge, and 1 when inside
     */
    float[] knob(float upX, float upY, float distance, float[] visible) {
        float[] c = corners();
        if (knobEdge < 0) {
            float best = -Float.MAX_VALUE;
            for (int i = 0; i < 4; i++) {
                float[] normal = outward(c, i);
                float facing = normal[0] * upX + normal[1] * upY;
                if (facing > best) { best = facing; knobEdge = i; }
            }
        }
        float[] knob = knobAt(c, knobEdge, distance, false);
        if (!knobInside && !turning && (knob[0] < visible[0] || knob[1] < visible[1] || knob[0] > visible[2] || knob[1] > visible[3]))
            knobInside = true;
        return knobInside ? knobAt(c, knobEdge, distance, true) : knob;
    }
    private float[] knobAt(float[] c, int edge, float distance, boolean inside) {
        float[] normal = outward(c, edge);
        int next = (edge + 1) % 4;
        float midX = (c[2*edge] + c[2*next]) / 2, midY = (c[2*edge+1] + c[2*next+1]) / 2;
        // Inside, stay within the piece's nearer half.
        if (inside) distance = -Math.min(distance, (edge % 2 == 0 ? height : width) * scale / 2);
        return new float[]{midX + normal[0] * distance, midY + normal[1] * distance, midX, midY, edge, inside ? 1 : 0};
    }
    /** Unit normal pointing out of the piece from edge {@code i}, corner i to corner i + 1. */
    private static float[] outward(float[] c, int i) {
        int next = (i + 1) % 4;
        float dx = c[2*next] - c[2*i], dy = c[2*next+1] - c[2*i+1], length = (float)Math.hypot(dx, dy);
        // Corners run clockwise on the page (y down), so the outside is to the left of each edge.
        return length == 0 ? new float[]{0, -1} : new float[]{dy / length, -dx / length};
    }

    /**
     * What a pen-down at ({@code x}, {@code y}) grabs, given the knob and how far, in page
     * pixels, a handle reaches.
     */
    int hit(float x, float y, float[] knob, float reach) {
        if (Math.hypot(x - knob[0], y - knob[1]) <= reach) return ROTATE;
        float[] q = piecePoint(x, y);
        boolean inside = q[0] >= 0 && q[1] >= 0 && q[0] <= width && q[1] <= height;
        // A small piece is all handle, so it moves instead of scaling from inside itself.
        if (inside && Math.min(width, height) * scale < 3 * reach) return MOVE;
        float[] c = corners();
        int nearest = NONE; double best = reach;
        for (int i = 0; i < 4; i++) {
            double distance = Math.hypot(x - c[2*i], y - c[2*i+1]);
            if (distance <= best) { best = distance; nearest = SCALE + i; }
        }
        if (nearest != NONE) return nearest;
        return inside ? MOVE : NONE;
    }
    private float[] piecePoint(float x, float y) {
        double c = Math.cos(-angle) / scale, s = Math.sin(-angle) / scale, dx = x - centerX, dy = y - centerY;
        return new float[]{(float)(width / 2.0 + c * dx - s * dy), (float)(height / 2.0 + s * dx + c * dy)};
    }

    // Dragging

    /** Starts dragging {@code handle}. */
    void grab(int handle) {
        startX = centerX; startY = centerY; startScale = scale; startAngle = angle;
        turning = handle == ROTATE;
        if (handle >= SCALE) {
            float[] c = corners(); int opposite = (handle - SCALE + 2) % 4;
            anchorX = c[2*opposite]; anchorY = c[2*opposite+1];
        }
    }
    /** Follows a drag of {@code handle} that began at ({@code fromX}, {@code fromY}) and is now at ({@code x}, {@code y}). */
    void drag(int handle, float fromX, float fromY, float x, float y) {
        if (handle == MOVE) {
            // Whole-pixel moves keep an unscaled, unturned piece exactly as it was.
            centerX = startX + Math.round(x - fromX); centerY = startY + Math.round(y - fromY);
        } else if (handle == ROTATE) {
            double turned = startAngle + Math.atan2(y - startY, x - startX) - Math.atan2(fromY - startY, fromX - startX);
            double quarter = Math.PI / 2, nearest = Math.round(turned / quarter) * quarter;
            angle = Math.abs(turned - nearest) <= Math.toRadians(SNAP) ? nearest : turned;
            angle = Math.IEEEremainder(angle, 2 * Math.PI);
            if (Math.abs(angle) < 1e-9) angle = 0;
            centerX = startX; centerY = startY; align();
        } else if (handle >= SCALE) {
            // Scale evenly by how far along the diagonal the corner has moved, holding the opposite corner still.
            float gx = fromX - anchorX, gy = fromY - anchorY;
            double along = ((x - anchorX) * gx + (y - anchorY) * gy) / Math.max(1e-6, (double)gx * gx + (double)gy * gy);
            double largest = Math.max(width, height), smallest = Math.min(width, height);
            double limit = 2.0 * Math.max(pageWidth, pageHeight) / largest, least = Math.max(4 / smallest, 1 / largest);
            scale = (float)Math.max(least, Math.min(limit, startScale * along));
            float ratio = scale / startScale;
            centerX = anchorX + (startX - anchorX) * ratio; centerY = anchorY + (startY - anchorY) * ratio;
        }
    }
    /** Ends a drag. */
    void release() { turning = false; }
    /** Whether the piece would land just as it was lifted, changing nothing. */
    boolean unmoved() {
        return lifted && angle == 0 && scale == 1 && centerX == cutX + width / 2f && centerY == cutY + height / 2f;
    }

    // Placing

    /**
     * The piece's tone and coverage at page pixel ({@code x}, {@code y}) as placed, blended
     * between its nearest pixels, packed as tone << 8 | coverage; 0 where it leaves the page bare.
     */
    private int sample(float[] m, int x, int y) {
        float px = x + .5f, py = y + .5f;
        float ux = m[0] * px + m[1] * py + m[2], uy = m[3] * px + m[4] * py + m[5];
        // The piece ends sharply at its edges, as the preview shows it; within them, blend neighbours.
        if (ux < 0 || uy < 0 || ux >= width || uy >= height) return 0;
        float qx = ux - .5f, qy = uy - .5f;
        int x0 = (int)Math.floor(qx), y0 = (int)Math.floor(qy);
        float fx = qx - x0, fy = qy - y0, pigment = 0, coverage = 0;
        for (int dy = 0; dy < 2; dy++) for (int dx = 0; dx < 2; dx++) {
            int sx = Math.max(0, Math.min(width - 1, x0 + dx)), sy = Math.max(0, Math.min(height - 1, y0 + dy));
            float weight = (dx == 0 ? 1 - fx : fx) * (dy == 0 ? 1 - fy : fy);
            if (weight == 0) continue;
            int i = sy * width + sx, a = alpha[i] & 255;
            coverage += a * weight; pigment += (tones[i] & 255) * a * weight;
        }
        int a = Math.round(coverage);
        if (a == 0) return 0;
        return Math.max(0, Math.min(255, Math.round(pigment / coverage))) << 8 | Math.min(255, a);
    }

    /**
     * Lays the piece on the page as one undo step: onto the active layer, which first loses
     * what was lifted, or as a new layer above it.
     * @return whether the page changed
     */
    boolean place(ToneDocument document) {
        if (unmoved()) return false;
        float[] m = inverse();
        int[] area = bounds();
        if (newLayer) {
            byte[] layerTones = new byte[document.width * document.height], layerAlpha = new byte[layerTones.length];
            java.util.Arrays.fill(layerTones, (byte)255);
            for (int y = area[1]; y < area[3]; y++) for (int x = area[0]; x < area[2]; x++) {
                int pixel = sample(m, x, y);
                if (pixel == 0) continue;
                layerTones[y * document.width + x] = (byte)(pixel >>> 8); layerAlpha[y * document.width + x] = (byte)pixel;
            }
            document.addLayer(layerTones, layerAlpha);
            return true;
        }
        document.beginLayer();
        if (lifted) for (int row = 0; row < height; row++) for (int col = 0; col < width; col++)
            if (alpha[row * width + col] != 0) document.paintTone(cutX + col, cutY + row, 255, 0);
        for (int y = area[1]; y < area[3]; y++) for (int x = area[0]; x < area[2]; x++) {
            int pixel = sample(m, x, y);
            if (pixel != 0) document.overTone(x, y, pixel >>> 8, pixel & 255);
        }
        return document.finish();
    }
}
