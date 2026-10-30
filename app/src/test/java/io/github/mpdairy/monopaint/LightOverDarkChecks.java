package io.github.mpdairy.monopaint;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Light over dark (the owner, 2026-10-22: "light colors on opaque materials.. crayon, oils.. they should go on light.
 * like white or grey paint", "pencil and charcoal in lighter colors should do the same", "also the print ones"): a white
 * or light grey stroke of an opaque medium over a black block on its own layer lays its light tone over it; a
 * transparent watercolour or ink wash over the block only darkens (it stays black). White ink (the owner, 2026-10-24):
 * a pen's or marker's white covers black, its light grey partly, and mid grey or darker darkens only, as ever. {@code java LightOverDarkChecks
 * pictures <file.png>} writes a contact sheet of every preset below, white and light grey over black.
 */
public final class LightOverDarkChecks {
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
    static final int W = 360, H = 200, BX0 = 30, BX1 = 330, BY0 = 40, BY1 = 160;
    /** The bar's lightest grey (Lighter) and white. */
    static final double LIGHT = 1 - InkWashShades.INK_LIGHT_GRAY / 255.0, WHITE = 0;

    /** Opaque media: each must lay light over black. */
    static final String[] OPAQUE = {"Wet gouache", "Tacky gouache", "Wet oils", "Flat oils", "Opaque round", "Impasto", "Dry oils", "Dry round", "Scumble", "Cover hake",
            "Wax crayon", "Chisel pastel", "Soft pastel", "HB pencil", "5B pencil", "Messy stick", "Soft charcoal", "Compressed",
            "Dot tone", "Ben-Day brush", "Halftone brush", "Rosette ink", "Engraving pen", "Dither pencil"};
    /** Pens and markers (white ink): white reads white over black, light grey its own grey. */
    static final String[] PENS = {"Steady line", "Press liner", "Gel pen", "Fountain pen", "Fude pen", "Chisel brush", "Pooling brush",
            "Chisel marker", "Round marker", "Felt marker", "Bleed marker", "Wet chisel", "Colouring pen"};
    /** Transparent media: over black they stay black (darken only). */
    static final String[] TRANSPARENT = {"Soft wash", "Grey wash", "Flat wash", "Dry glaze", "Line and wash", "Wet ink"};

    static ModularBrush preset(String name) {
        for (ToolPresets.Preset p : ToolPresets.withRetired()) if (p.name.equals(name)) return ModularCode.decode(p.code);
        throw new IllegalArgumentException(name);
    }
    static double size(ModularBrush b) {
        if (ResolvedBrush.of(b, 10, 1).screen != null) return 10;
        PartChoice m = b.choice(BrushPart.MEDIUM);
        return m == PartChoice.GRAPHITE ? 10 : m == PartChoice.CHARCOAL || m == PartChoice.CRAYON ? 18 : 34;
    }
    /** The layer {@code b}'s strokes go on: a short stroke on a blank page, the layer it changed. */
    static int layerOf(ModularBrush b) {
        ToneDocument d = InkWashLayers.blank(120, 60);
        InkWashLayers.select(d, 0);
        WatercolorSession s = new WatercolorSession();
        stroke(d, s, b, 20, 1, FloatChecks.line(20, 30, 100, 30, .8));
        for (int l = 0; l < d.layerCount(); l++) { int[] t = new int[120], c = new int[120]; d.layerPixels(l, 0, 30, 120, 1, t, c); for (int x = 0; x < 120; x++) if (c[x] > 0 || t[x] < 250) return l; }
        throw new AssertionError("No layer changed");
    }
    static void stroke(ToneDocument d, WatercolorSession s, ModularBrush b, double size, double darkness, double[][] path) {
        s.settleBefore(d, b); s.dry("stroke");
        CarrySmudgeChecks.feed(QuickStroke.start(d, b, size, darkness, s), path);
        for (int k = 0; k < 2000 && s.settling(); k++) s.tickDrying(d);
    }
    /** A page with a black block on {@code layer}, then {@code b} across it in light grey (upper) and white (lower). */
    static ToneDocument drawn(ModularBrush b) { return drawn(b, LIGHT); }
    /** As above, the upper stroke at {@code upper}'s darkness. */
    static ToneDocument drawn(ModularBrush b, double upper) {
        int layer = layerOf(b);
        ToneDocument d = InkWashLayers.blank(W, H);
        InkWashLayers.select(d, 0);
        // The block on the brush's own plane (kind layers: a kind laid on another plane stacks by kind, dry media under pen).
        d.selectLayer(layer); d.begin(1, ResolvedBrush.of(b, 20, 1).kind);
        int[] row = new int[BX1 - BX0];
        java.util.Arrays.fill(row, 0);
        for (int y = BY0; y < BY1; y++) d.setTones(BX0, y, row.length, 1, row);
        d.finish();
        WatercolorSession s = new WatercolorSession();
        double sz = size(b);
        stroke(d, s, b, sz, upper, FloatChecks.line(10, 80, 350, 80, .8));
        stroke(d, s, b, sz, WHITE, FloatChecks.line(10, 125, 350, 125, .8));
        return d;
    }
    /** The mean tone shown along a stroke, inside the block. */
    static double across(ToneDocument d, int y) { return CarrySmudgeChecks.mean(d, BX0 + 40, BX1 - 40, y - 2, y + 2); }

    public static void main(String[] args) throws Exception {
        Assets.install(Assets.folder(Assets.FOLDER));
        if (args.length > 1 && args[0].equals("pictures")) { pictures(new File(args[1])); return; }
        StringBuilder out = new StringBuilder();
        for (String name : OPAQUE) {
            ToneDocument d = drawn(preset(name));
            double light = across(d, 80), white = across(d, 125);
            check(light > 80 && white > 120, name + " lays light over black: light grey " + (int) light + ", white " + (int) white);
            out.append(name).append(' ').append((int) light).append('/').append((int) white).append("; ");
        }
        // White ink: a pen's or marker's white reads white over black; at mid grey or darker it lays no light at all, so
        // it composes exactly as before (the golden hashes) and over black stays black.
        double mid = 1 - InkWashShades.INK_GRAY / 255.0;
        for (String name : PENS) {
            ModularBrush b = preset(name);
            ToneDocument d = drawn(b);
            double light = across(d, 80), white = across(d, 125);
            check(white >= 240 && Math.abs(light - InkWashShades.INK_LIGHT_GRAY) <= 6, name + " lays white ink over black, light grey as itself: light grey " + (int) light + ", white " + (int) white);
            for (double dark : new double[]{1, 1 - InkWashShades.INK_DARK_GRAY / 255.0, mid})
                check(ResolvedBrush.of(b, 10, dark).lightOver() == 0 && ResolvedBrush.of(b, 10, 0).lightOver() == 1, name + ": only lighter than mid grey lays light");
            ToneDocument m = drawn(b, mid);
            check(across(m, 80) < 2, name + " at mid grey only darkens: over black it stays black");
            out.append(name).append(' ').append((int) light).append('/').append((int) white).append("; ");
        }
        // A white pen line over black wash covers it from the ink layer above; erasing that layer, or Undo, shows the black again.
        ModularBrush pen = preset("Steady line");
        ToneDocument e = InkWashLayers.blank(W, H);
        e.selectLayer(InkWashLayers.WASH); e.begin(1);
        int[] black = new int[BX1 - BX0];
        for (int y = BY0; y < BY1; y++) e.setTones(BX0, y, black.length, 1, black);
        e.finish();
        InkWashLayers.select(e, 0);
        stroke(e, new WatercolorSession(), pen, 34, WHITE, FloatChecks.line(10, 100, 350, 100, .8));
        check(across(e, 100) >= 240, "A white pen line covers black wash under it: " + (int) across(e, 100));
        check(e.compositeToneKept(W / 2, 100, InkWashLayers.INK, InkWashLayers.INK) < 2, "Erasing the white pen line shows the black under it");
        e.undo();
        check(across(e, 100) < 2, "Undo takes the white pen line back");
        // A print stays crisp: only black and white over the block.
        for (String name : new String[]{"Halftone brush", "Ben-Day brush", "Dither pencil"}) {
            ToneDocument d = drawn(preset(name));
            for (int y = BY0; y < BY1; y++) for (int x = BX0; x < BX1; x++) { int t = d.compositeTone(x, y); check(t == 0 || t == 255, name + " prints only black and white over black: " + t); }
        }
        // Wet gouache over wet black paint: light paint covers more than it mixes.
        ModularBrush g = preset("Wet gouache");
        ToneDocument p = InkWashLayers.blank(W, H);
        InkWashLayers.select(p, 0);
        WatercolorSession s = new WatercolorSession();
        for (int y = BY0 + 5; y <= BY1 - 5; y += 15) { s.settleBefore(p, g); CarrySmudgeChecks.feed(QuickStroke.start(p, g, 34, 1, s), FloatChecks.line(BX0, y, BX1, y, .8)); }
        stroke(p, s, g, 34, WHITE, FloatChecks.line(10, 125, 350, 125, .8));
        double wet = across(p, 125);
        check(wet > 200, "White wet gouache over wet black covers it: " + (int) wet);
        out.append("Wet gouache over wet black ").append((int) wet).append("; ");
        for (String name : TRANSPARENT) {
            ToneDocument d = drawn(preset(name));
            double light = across(d, 80), white = across(d, 125);
            check(light < 2 && white < 2, name + " only darkens: over black it stays black: " + (int) light + ", " + (int) white);
        }
        System.out.println("PASS: light over dark: light grey and white strokes over a black block (mean tone, light/white): " + out
                + "prints only black and white; pens and markers at mid grey, and transparent washes, stay black over it; white ink erases and undoes");
    }

    static void pictures(File file) throws Exception {
        String[] all = new String[OPAQUE.length + PENS.length + TRANSPARENT.length];
        System.arraycopy(OPAQUE, 0, all, 0, OPAQUE.length);
        System.arraycopy(PENS, 0, all, OPAQUE.length, PENS.length);
        System.arraycopy(TRANSPARENT, 0, all, OPAQUE.length + PENS.length, TRANSPARENT.length);
        int cols = 5, rows = (all.length + cols - 1) / cols, lab = 18;
        BufferedImage img = new BufferedImage(cols * W, rows * (H + lab), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE); g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 13));
        for (int i = 0; i < all.length; i++) {
            ToneDocument d = drawn(preset(all[i]));
            int ox = i % cols * W, oy = i / cols * (H + lab);
            for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) { int t = d.compositeTone(x, y); img.getRaster().setSample(ox + x, oy + lab + y, 0, t); }
            g.setColor(Color.BLACK); g.drawString(all[i] + (i >= OPAQUE.length + PENS.length ? " (transparent)" : ""), ox + 6, oy + 13);
            g.drawRect(ox, oy + lab, W - 1, H - 1);
        }
        javax.imageio.ImageIO.write(img, "png", file);
    }
}
