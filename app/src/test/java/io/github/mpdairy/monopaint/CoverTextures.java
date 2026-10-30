package io.github.mpdairy.monopaint;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Locale;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * Makes the covers' sheets ({@link CoverTexture}) from the designs' own generators: {@code java CoverTextures <folder>
 * [design key …]} (scripts/cover_textures.sh, into app/src/main/assets/covers). Each sheet is its design's field for
 * {@link CoverTexture#NAME} over {@link CoverTexture#kind}'s size, {@link CoverTexture#PER_WIDTH} texels a cover's width,
 * a texel's centre paper where the field is above 0 (Swirl's tone at least half). A sheet that tiles is made wider and
 * taller by {@link #OVERLAP} and folded round: its left edge is the part past its right edge, cut in along a seam down
 * the overlap where the two agree best (a shortest path through the texels where they differ, as image quilting cuts
 * one), then its top likewise across, so it wraps round with no edge to see. Written as a 1-bit grey PNG, its rows
 * unfiltered, as {@link CoverTexture#decode} reads it.
 */
public final class CoverTextures {
    /** How much wider and taller a sheet that tiles is made, texels: the room its seams have to wander. */
    static final int OVERLAP = 192;

    public static void main(String[] args) throws Exception {
        File out = new File(args.length > 0 ? args[0] : "app/src/main/assets/covers");
        out.mkdirs();
        long total = 0;
        for (CoverDesign d : CoverDesign.values()) {
            if (args.length > 1 && !java.util.Arrays.asList(args).subList(1, args.length).contains(d.key)) continue;
            long t0 = System.nanoTime();
            boolean[] paper = sheet(d);
            CoverTexture.Kind k = CoverTexture.kind(d);
            byte[] png = png(paper, k.width, k.height);
            File f = new File(out, d.key + ".png");
            Files.write(f.toPath(), png);
            total += png.length;
            System.out.println(String.format(Locale.ROOT, "%s: %d x %d, %s, %d KB (%.1f s)", d.key, k.width, k.height, k.tiles ? "tiles" : "doesn't tile", png.length / 1024, (System.nanoTime() - t0) / 1e9));
        }
        System.out.println("wrote " + out + ", " + total / 1024 + " KB");
    }

    /** {@code design}'s sheet, a texel's paper or ink, row by row. */
    static boolean[] sheet(CoverDesign design) {
        CoverTexture.Kind k = CoverTexture.kind(design);
        int m = k.tiles ? OVERLAP : 0, w = k.width + m, h = k.height + m;
        CoverDesign.Field field = design.sheet(CoverTexture.NAME, k.aspect(), k.widths);
        boolean tone = design.tone();
        boolean[] made = new boolean[w * h];
        double[][] scratch = new double[Cores.WORKERS][3];
        double unit = 1.0 / CoverTexture.PER_WIDTH;
        Cores.each(h, (y, worker) -> {
            for (int x = 0; x < w; x++) {
                double f = field.at((x + .5) * unit, (y + .5) * unit, scratch[worker]);
                made[y * w + x] = tone ? f >= .5 : f > 0;
            }
        });
        if (!k.tiles) return made;
        // Folded round across (each row's seam), then down (each column's).
        boolean[] across = fold(made, w, h, k.width, m, true);
        return fold(across, k.width, h, k.height, m, false);
    }

    /**
     * {@code sheet} ({@code w × h}) folded round along one way ({@code across}: its columns, else its rows) to {@code size}:
     * along each line the other way, the texels before its seam come from {@code size} further on, so the sheet's last
     * texel meets the first as they were made. The seam wanders down the overlap ({@code m}) by at most a texel a line,
     * through where the two agree (a texel where they differ costs 1) and never at the very edge.
     */
    static boolean[] fold(boolean[] sheet, int w, int h, int size, int m, boolean across) {
        int lines = across ? h : w, ow = across ? size : w, oh = across ? h : size;
        // Shortest seam by lines: cost[line][c] for a seam at c (1 to m - 1), with where it came from.
        double[] cost = new double[m], next = new double[m];
        int[][] from = new int[lines][m];
        for (int line = 0; line < lines; line++) {
            for (int c = 1; c < m; c++) {
                double here = differs(sheet, w, across, line, c, size) + 1e-3 * Math.abs(c - m / 2.0) / m;
                if (line == 0) { next[c] = here; continue; }
                int best = c;
                for (int dc = -1; dc <= 1; dc++) { int p = c + dc; if (p >= 1 && p < m && cost[p] < cost[best]) best = p; }
                next[c] = cost[best] + here; from[line][c] = best;
            }
            double[] t = cost; cost = next; next = t;
        }
        int seam = 1;
        for (int c = 1; c < m; c++) if (cost[c] < cost[seam]) seam = c;
        int[] at = new int[lines];
        for (int line = lines - 1; line >= 0; line--) { at[line] = seam; seam = from[line][seam]; }
        boolean[] out = new boolean[ow * oh];
        for (int y = 0; y < oh; y++)
            for (int x = 0; x < ow; x++) {
                int line = across ? y : x, along = across ? x : y;
                int a = along < at[line] ? along + size : along;
                out[y * ow + x] = across ? sheet[y * w + a] : sheet[a * w + x];
            }
        return out;
    }
    private static int differs(boolean[] sheet, int w, boolean across, int line, int c, int size) {
        boolean a = across ? sheet[line * w + c] : sheet[c * w + line], b = across ? sheet[line * w + c + size] : sheet[(c + size) * w + line];
        return a == b ? 0 : 1;
    }

    /** A 1-bit grey PNG of {@code paper} ({@code w × h}): paper white, every row unfiltered, deflated as tightly as it goes. */
    static byte[] png(boolean[] paper, int w, int h) throws IOException {
        int rowBytes = (w + 7) / 8;
        byte[] raw = new byte[(rowBytes + 1) * h];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) if (paper[y * w + x]) raw[y * (rowBytes + 1) + 1 + (x >> 3)] |= (byte) (0x80 >>> (x & 7));
        ByteArrayOutputStream z = new ByteArrayOutputStream();
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        try (DeflaterOutputStream d = new DeflaterOutputStream(z, deflater, 1 << 16)) { d.write(raw); }
        finally { deflater.end(); }
        ByteArrayOutputStream file = new ByteArrayOutputStream();
        file.write(new byte[]{(byte) 137, 'P', 'N', 'G', 13, 10, 26, 10});
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        DataOutputStream hd = new DataOutputStream(header);
        hd.writeInt(w); hd.writeInt(h); hd.write(new byte[]{1, 0, 0, 0, 0});
        chunk(file, "IHDR", header.toByteArray());
        chunk(file, "IDAT", z.toByteArray());
        chunk(file, "IEND", new byte[0]);
        return file.toByteArray();
    }
    private static void chunk(ByteArrayOutputStream file, String type, byte[] body) throws IOException {
        DataOutputStream out = new DataOutputStream(file);
        out.writeInt(body.length);
        byte[] name = type.getBytes("ISO-8859-1");
        out.write(name); out.write(body);
        CRC32 crc = new CRC32(); crc.update(name); crc.update(body);
        out.writeInt((int) crc.getValue());
    }
}
