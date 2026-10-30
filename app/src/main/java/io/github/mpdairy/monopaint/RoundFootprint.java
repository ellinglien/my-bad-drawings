package io.github.mpdairy.monopaint;

/**
 * Round dabs along a pen path, in plain Java so wash strokes can be checked on the desktop.
 * Each move is handed over as row spans covering the dabs along it, every pixel once however
 * much the dabs overlap; pixels of earlier moves come again, and callers keep what they need.
 */
final class RoundFootprint {
    /** The pixels {@code xStart} to {@code xEnd} of row {@code y}, {@code xEnd} exclusive. */
    interface Span { void at(int y, int xStart, int xEnd); }
    private float previousX, previousY, previousDiameter;
    private boolean started;
    /** One move's dabs: centers and radii. */
    private float[] dabX = new float[16], dabY = new float[16], dabR = new float[16];
    /** Per row of the move, where its pieces start in {@link #pieceStart} and {@link #pieceEnd}, then how many it has. */
    private int[] rowFirst = new int[256], rowFill = new int[256];
    /** Each dab's run of pixels on a row, inclusive, grouped by row. */
    private int[] pieceStart = new int[1024], pieceEnd = new int[1024];
    /**
     * The move being handed over, while its spans are: the centres and radii it runs between. Its
     * first dab is the previous move's last, already handed over; for the first sample both ends are the same.
     */
    float fromX, fromY, fromRadius, toX, toY, toRadius;

    /** Dabs from the previous sample to this one, the diameter changing evenly between them. */
    void to(float x, float y, float diameter, int width, int height, Span span) {
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(diameter)) return;
        if (!started) {
            started = true; previousX = x; previousY = y; previousDiameter = diameter;
            sweep(x, y, diameter, x, y, diameter, 1, width, height, span); return;
        }
        float distance = (float)Math.hypot(x - previousX, y - previousY);
        float spacing = Math.max(1, Math.min(previousDiameter, diameter) * .25f);
        // The pen reports hundreds of samples a second, often under a pixel apart, and a dab for
        // each repaints nearly the same circle: wait until it has moved, or the size has changed,
        // by a spacing step. The last dab still reaches the tip, which is within a quarter width.
        if (distance < spacing && Math.abs(diameter - previousDiameter) < spacing) return;
        int steps = Math.max(1, (int)Math.ceil(distance / spacing));
        sweep(previousX, previousY, previousDiameter, x, y, diameter, steps, width, height, span);
        previousX = x; previousY = y; previousDiameter = diameter;
    }

    /**
     * The union of dabs 1 to {@code steps} of the way from one center and diameter to the other, as
     * row spans top to bottom. A dab covers the pixels whose centers lie inside its circle, clipped to
     * the page; working out each dab's run on each row, rather than visiting its pixels, makes the cost
     * follow the rows the dabs cross, and the pixels are then visited once by the caller.
     */
    private void sweep(float fromX, float fromY, float fromDiameter, float toX, float toY, float toDiameter,
                       int steps, int width, int height, Span span) {
        this.fromX = fromX; this.fromY = fromY; fromRadius = Math.max(.5f, fromDiameter / 2);
        this.toX = toX; this.toY = toY; toRadius = Math.max(.5f, toDiameter / 2);
        if (dabX.length < steps) { dabX = new float[steps * 2]; dabY = new float[steps * 2]; dabR = new float[steps * 2]; }
        int top = height, bottom = -1;
        for (int i = 1; i <= steps; i++) {
            float t = (float)i / steps, r = Math.max(.5f, (fromDiameter + (toDiameter - fromDiameter) * t) / 2);
            float cy = fromY + (toY - fromY) * t;
            dabX[i - 1] = fromX + (toX - fromX) * t; dabY[i - 1] = cy; dabR[i - 1] = r;
            top = Math.min(top, Math.max(0, (int)Math.floor(cy - r)));
            bottom = Math.max(bottom, Math.min(height - 1, (int)Math.ceil(cy + r)));
        }
        if (top > bottom) return;
        int rows = bottom - top + 1;
        if (rowFirst.length <= rows) { rowFirst = new int[rows * 2]; rowFill = new int[rows * 2]; }
        java.util.Arrays.fill(rowFirst, 0, rows + 1, 0);
        // Room for one piece per dab per row it crosses, rows laid end to end.
        for (int i = 0; i < steps; i++) {
            float cy = dabY[i], r = dabR[i];
            int y0 = Math.max(0, (int)Math.floor(cy - r)), y1 = Math.min(height - 1, (int)Math.ceil(cy + r));
            for (int y = y0; y <= y1; y++) rowFirst[y - top + 1]++;
        }
        for (int row = 0; row < rows; row++) rowFirst[row + 1] += rowFirst[row];
        if (pieceStart.length < rowFirst[rows]) { pieceStart = new int[rowFirst[rows] * 2]; pieceEnd = new int[rowFirst[rows] * 2]; }
        System.arraycopy(rowFirst, 0, rowFill, 0, rows);
        // Dabs left to right, so each row's pieces arrive close to sorted.
        boolean rightward = toX >= fromX;
        for (int k = 0; k < steps; k++) {
            int i = rightward ? k : steps - 1 - k;
            float cx = dabX[i], cy = dabY[i], r = dabR[i], r2 = r * r;
            int lo = Math.max(0, (int)Math.floor(cx - r)), hi = Math.min(width - 1, (int)Math.ceil(cx + r));
            int y0 = Math.max(0, (int)Math.floor(cy - r)), y1 = Math.min(height - 1, (int)Math.ceil(cy + r));
            for (int y = y0; y <= y1; y++) {
                float dy = y + .5f - cy, dy2 = dy * dy;
                if (dy2 > r2 || lo > hi) continue;
                // Estimate the run, then settle its ends with the same test a pixel at a time would use.
                double half = Math.sqrt(r2 - dy2);
                int a = Math.max(lo, (int)Math.ceil(cx - half - .5)), b = Math.min(hi, (int)Math.floor(cx + half - .5));
                while (a > lo && inside(a - 1, cx, dy2, r2)) a--;
                while (a <= b && !inside(a, cx, dy2, r2)) a++;
                while (b < hi && inside(b + 1, cx, dy2, r2)) b++;
                while (b >= a && !inside(b, cx, dy2, r2)) b--;
                if (a > b) continue;
                int at = rowFill[y - top]++;
                pieceStart[at] = a; pieceEnd[at] = b;
            }
        }
        for (int row = 0; row < rows; row++) {
            int first = rowFirst[row], end = rowFill[row];
            if (first == end) continue;
            for (int p = first + 1; p < end; p++) {
                int a = pieceStart[p], b = pieceEnd[p], q = p - 1;
                for (; q >= first && pieceStart[q] > a; q--) { pieceStart[q + 1] = pieceStart[q]; pieceEnd[q + 1] = pieceEnd[q]; }
                pieceStart[q + 1] = a; pieceEnd[q + 1] = b;
            }
            int a = pieceStart[first], b = pieceEnd[first];
            for (int p = first + 1; p < end; p++) {
                if (pieceStart[p] <= b + 1) { b = Math.max(b, pieceEnd[p]); continue; }
                span.at(top + row, a, b + 1);
                a = pieceStart[p]; b = pieceEnd[p];
            }
            span.at(top + row, a, b + 1);
        }
    }
    /** Whether pixel {@code x}'s center lies inside the circle at {@code cx}, on a row {@code dy2} away squared. */
    private static boolean inside(int x, float cx, float dy2, float r2) {
        float dx = x + .5f - cx;
        return dx * dx + dy2 <= r2;
    }
}
