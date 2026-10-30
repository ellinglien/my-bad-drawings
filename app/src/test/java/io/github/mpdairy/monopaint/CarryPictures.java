package io.github.mpdairy.monopaint;

import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Pictures of Smudge drawn by the app's engine, for looking (not a check): {@code java CarryPictures <out folder>}
 * (scripts/smudge_pictures.sh). Each scene is a page of ink-over-wash layer pairs drawn as the drawing screen draws
 * it ({@link WatercolorSession#settleNow} first, then {@link QuickStroke#start}), shown as the Manta shows it: the
 * composite's 8 × 8 dot pattern ({@link DotPattern#pixel}), and beside it the composite at 16 greys.
 */
public final class CarryPictures {
    public static void main(String[] args) throws Exception {
        Assets.install(Assets.folder(Assets.FOLDER));
        File out = new File(args.length > 0 ? args[0] : ".");
        out.mkdirs();
        save(stumpOverPencil(), new File(out, "1-stump-over-pencil"));
        save(dragThroughWashes(), new File(out, "2-drag-wet-and-dry-wash"));
        save(liftOverDark(), new File(out, "3-lift-over-dark"));
        save(smearAcrossInk(), new File(out, "4-smear-across-ink"));
        save(ownLayerOnly(), new File(out, "5-paint-drag-leaves-pen-lines"));
        System.out.println("wrote " + out);
    }

    static final int W = 900, H = 420;

    /** A six-part brush: a plain round tip (no dry brush, wobble or tapers) of {@code medium} on plain paper, steady hand. */
    static ModularBrush brush(String style, PartChoice medium, PartChoice carry, PartChoice life) {
        ModularBrush b = new ModularBrush("pictures", 7, ModularStyle.named(style));
        b.set(BrushPart.MEDIUM, new ModularBrush.Part(medium));
        b.set(BrushPart.CARRY, new ModularBrush.Part(carry));
        b.set(BrushPart.LIFE, new ModularBrush.Part(life));
        if (medium == PartChoice.COVER) { b.part(BrushPart.MEDIUM).set(PartDial.COVER, 1); b.part(BrushPart.MEDIUM).set(PartDial.IMPASTO, 0); }
        return b.normalize();
    }
    /** The ready-made Stump (Tools page, Charcoal): Charcoal, Smear, Own paint 0, Pick up .7, Lay down .6. */
    static ModularBrush stump() { for (ToolPresets.Preset p : ToolPresets.all()) if (p.name.equals("Stump")) return p.brush().parts; throw new AssertionError("no Stump"); }

    /** A stroke as the drawing screen starts one: what still settles laid first, then live or quick. */
    static void stroke(ToneDocument d, WatercolorSession session, ModularBrush b, double size, double darkness, double[][] path) {
        session.settleNow(d);
        DrawingStroke s = QuickStroke.start(d, b, size, darkness, session);
        for (double[] p : path) { s.sample((float) p[0], (float) p[1], ToolSettings.pressure((float) p[2]), 0, 0, (long) p[3]); s.present(); }
        s.finish();
    }
    /** A straight line from {@code x0, y0} to {@code x1, y1} at pressure travel {@code p}, a sample every 2.5 px, 4 ms apart. */
    static double[][] line(double x0, double y0, double x1, double y1, double p) {
        int n = (int) Math.max(2, Math.ceil(Math.hypot(x1 - x0, y1 - y0) / 2.5));
        double[][] o = new double[n + 1][];
        for (int i = 0; i <= n; i++) { double u = (double) i / n; o[i] = new double[]{x0 + (x1 - x0) * u, y0 + (y1 - y0) * u, p, i * 4}; }
        return o;
    }
    /** A wave across the page, pressure easing in and out. */
    static double[][] wave(double x0, double x1, double yc, double amp, double p) {
        int n = (int) Math.ceil((x1 - x0) / 2.5);
        double[][] o = new double[n + 1][];
        for (int i = 0; i <= n; i++) {
            double u = (double) i / n;
            o[i] = new double[]{x0 + (x1 - x0) * u, yc + amp * Math.sin(u * Math.PI * 2), .3 + (p - .3) * Math.pow(Math.sin(Math.PI * u), .5), i * 4};
        }
        return o;
    }

    static ToneDocument stumpOverPencil() {
        ToneDocument d = InkWashLayers.blank(W, H);
        WatercolorSession session = new WatercolorSession();
        ModularBrush pencil = brush("Pencil", PartChoice.GRAPHITE, PartChoice.OFF, PartChoice.NONE);
        pencil.part(BrushPart.MEDIUM).set(PartDial.GRADE, .7);
        for (int x = 80; x < 820; x += 9) stroke(d, session, pencil, 4, 1, line(x, 50, x + 60, 370, .8));
        stroke(d, session, stump(), 28, 1, wave(60, 840, 140, 50, .8));
        stroke(d, session, stump(), 60, 1, wave(60, 840, 290, 40, .8));
        return d;
    }
    static ToneDocument dragThroughWashes() {
        ToneDocument d = InkWashLayers.blank(W, H);
        WatercolorSession session = new WatercolorSession();
        ModularBrush wash = brush("Watercolor", PartChoice.WASH, PartChoice.OFF, PartChoice.DRYING);
        stroke(d, session, wash, 110, .8, line(240, 50, 240, 370, .8));
        session.settleNow(d);   // the left one dry
        stroke(d, session, wash, 110, .8, line(640, 50, 640, 370, .8));   // the right one still settling when the drag begins
        stroke(d, session, brush("Watercolor", PartChoice.WASH, PartChoice.DRAG, PartChoice.FAST_WET), 70, .3, line(40, 150, 860, 150, .8));
        ModularBrush oils = brush("Oils", PartChoice.COVER, PartChoice.DRAG, PartChoice.NONE);
        oils.part(BrushPart.CARRY).set(PartDial.LOAD, .4);
        stroke(d, session, oils, 60, .2, line(40, 300, 860, 300, .8));
        return d;
    }
    static ToneDocument liftOverDark() {
        ToneDocument d = InkWashLayers.blank(W, H);
        d.selectLayer(InkWashLayers.WASH);
        d.begin();
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            int g = x >= 330 && x < 570 ? 40 : y >= 230 ? 150 : 255;
            if (g < 255) d.setTone(x, y, g);
        }
        d.finish();
        WatercolorSession session = new WatercolorSession();
        stroke(d, session, brush("Gouache", PartChoice.COVER, PartChoice.LIFT, PartChoice.FAST_WET), 60, .31, line(40, 110, 860, 110, .8));
        stroke(d, session, brush("Watercolor", PartChoice.WASH, PartChoice.LIFT, PartChoice.NONE), 60, .2, line(40, 320, 860, 320, .8));
        return d;
    }
    static ToneDocument smearAcrossInk() {
        ToneDocument d = InkWashLayers.blank(W, H);
        WatercolorSession session = new WatercolorSession();
        ModularBrush pen = brush("Fineliner", PartChoice.INK, PartChoice.OFF, PartChoice.NONE);
        for (int x = 90; x < 820; x += 48) stroke(d, session, pen, 6, 1, line(x, 30, x, 390, .8));
        stroke(d, session, brush("Charcoal", PartChoice.CHARCOAL, PartChoice.SMUDGE, PartChoice.FAST_WET), 30, 1, line(40, 110, 860, 110, .8));
        ModularBrush finger = brush("Charcoal", PartChoice.CHARCOAL, PartChoice.SMUDGE, PartChoice.NONE);
        finger.part(BrushPart.CARRY).set(PartDial.LOAD, 0);
        stroke(d, session, finger, 30, 1, line(40, 210, 860, 210, .8));
        stroke(d, session, finger, 14, 1, line(40, 300, 860, 300, .8));
        return d;
    }
    /** A paint brush's Drag on the wash layer over pen lines on the ink layer: the lines stay crisp (own layer only). */
    static ToneDocument ownLayerOnly() {
        ToneDocument d = InkWashLayers.blank(W, H);
        WatercolorSession session = new WatercolorSession();
        stroke(d, session, brush("Watercolor", PartChoice.WASH, PartChoice.OFF, PartChoice.DRYING), 110, .8, line(450, 40, 450, 380, .8));
        ModularBrush pen = brush("Fineliner", PartChoice.INK, PartChoice.OFF, PartChoice.NONE);
        for (int x = 90; x < 820; x += 48) stroke(d, session, pen, 6, 1, line(x, 30, x, 390, .8));
        stroke(d, session, brush("Watercolor", PartChoice.WASH, PartChoice.DRAG, PartChoice.FAST_WET), 70, .3, line(40, 200, 860, 200, .8));
        return d;
    }

    /** {@code file}.png: the dots as the Manta shows them, the 16-grey composite beside them. */
    static void save(ToneDocument d, File file) throws Exception {
        BufferedImage img = new BufferedImage(2 * d.width + 12, d.height, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < d.height; y++) for (int x = 0; x < d.width; x++) {
            int g = d.compositeTone(x, y), dot = (DotPattern.pixel(g, x, y) & 0xff), q = Math.round(g / 17f) * 17;
            img.getRaster().setSample(x, y, 0, dot);
            img.getRaster().setSample(d.width + 12 + x, y, 0, q);
        }
        for (int y = 0; y < d.height; y++) for (int x = d.width; x < d.width + 12; x++) img.getRaster().setSample(x, y, 0, 128);
        javax.imageio.ImageIO.write(img, "png", new File(file.getPath() + ".png"));
    }
}
