package io.github.mpdairy.monopaint;

import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

/**
 * Writes a page and what the Java renderer makes of some of its areas, for compose_check.c to
 * compare with the native renderer (app/src/main/cpp/compose.h), on white paper or on the paper tone given after the
 * file (PageShade), or on a notebook cover's marble ("cover": its paper pixels as a layer under the others). Run by
 * scripts/test_inkwash.sh.
 */
public final class ComposeFixture {
    public static void main(String[] args) throws IOException {
        int width = 203, height = 141;
        Random random = new Random(9);
        ArrayList<ToneDocument.Layer> layers = new ArrayList<>();
        for (int n = 0; n < 4; n++) {
            byte[] tones = new byte[width * height], alpha = new byte[width * height];
            random.nextBytes(tones); random.nextBytes(alpha);
            // Some plain paper, some full coverage, as real pages have.
            for (int i = 0; i < alpha.length; i++) if (i % 7 == n) alpha[i] = 0; else if (i % 11 == n) alpha[i] = (byte)255;
            ToneDocument.Layer layer = new ToneDocument.Layer("Layer " + n, n != 2, tones, alpha);
            layer.opacity = n == 1 ? 60 : 100;
            layers.add(layer);
        }
        ToneDocument doc = new ToneDocument(width, height, layers, 0);
        // A notebook cover's paper pixels (its marble), or a shade.
        boolean cover = args.length > 1 && args[1].equals("cover");
        if (cover) doc.placePaper(CoverPattern.tones(CoverDesign.DEFAULT, "Compose", width, height, false));
        else if (args.length > 1) doc.placePaper(Integer.parseInt(args[1]));
        byte[][] tones = new byte[ToneDocument.STACK + 1][], alpha = new byte[ToneDocument.STACK + 1][];
        int[] opacity = new int[ToneDocument.STACK + 1];
        int count = doc.visibleLayers(tones, alpha, opacity);
        if (count != (cover ? 4 : 3)) throw new AssertionError("visibleLayers leaves out the hidden layer, and puts a cover's paper first");
        int[][] areas = {{0, 0, width, height}, {5, 9, 70, 40}, {150, 100, 203, 141}, {64, 0, 65, 141}};
        try (DataOutputStream out = new DataOutputStream(new java.io.BufferedOutputStream(new FileOutputStream(args[0])))) {
            out.writeInt(width); out.writeInt(height); out.writeInt(count); out.writeInt(doc.paperTone());
            for (int i = 0; i < count; i++) out.writeInt(opacity[i]);
            for (int i = 0; i < count; i++) { out.write(tones[i]); out.write(alpha[i]); }
            for (int tone = 0; tone < 256; tone++) for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) out.writeInt(DotPattern.pixel(tone, x, y));
            out.writeInt(areas.length);
            for (int[] a : areas) {
                for (int v : a) out.writeInt(v);
                int w = a[2] - a[0], h = a[3] - a[1];
                int[] dotted = new int[w * h];
                doc.render(dotted, a[0], a[1], w, h);
                for (int v : dotted) out.writeInt(v);
                for (int y = a[1]; y < a[3]; y++) for (int x = a[0]; x < a[2]; x++) out.writeInt(0xff000000 | doc.compositeTone(x, y) * 0x010101);
            }
        }
    }
}
