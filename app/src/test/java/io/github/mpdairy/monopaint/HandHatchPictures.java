package io.github.mpdairy.monopaint;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * A contact sheet of Hatching ({@link HandHatchStamper}) and the tonal Print Shading choices ({@link PrintScreen}): per
 * row, a ramp light to dark, an S-curve pressing harder along it and a filled scribble, drawn by the app's engine, into
 * the file given (default /tmp/handhatch-sheet.png). scripts/handhatch_pictures.sh runs it.
 */
public final class HandHatchPictures {
    static final int CELL_W = 520, CELL_H = 300, LABEL = 170, GAP = 12;

    public static void main(String[] args) throws Exception {
        Assets.install(Assets.folder(Assets.FOLDER));
        File out = new File(args.length > 0 ? args[0] : "/tmp/handhatch-sheet.png");
        List<String> names = new ArrayList<>();
        List<BufferedImage[]> rows = new ArrayList<>();
        // Hatching: ramp of pressures; one stroke going over 1 to 4 times, a held pen; the S-curve and the scribble.
        ModularBrush hh = hatching();
        names.add("Hand Hatch, pressure .2 .45 .7 .95 | over 1-4x, held 0-2 s | S-curve, scribble");
        rows.add(new BufferedImage[]{draw(hh, 60, rampStrokes()), draw(hh, 60, passesAndHeld()), draw(hh, 60, sCurveAndScribble(.35))});
        names.add("Hand Hatch dials, neat short blunt | loose | long flicked");
        rows.add(new BufferedImage[]{draw(with(hh, PartDial.HWOB, 0, PartDial.HLEN, .2, PartDial.HENDS, 0), 60, rampStrokes()),
                draw(with(hh, PartDial.HWOB, 1), 60, rampStrokes()), draw(with(hh, PartDial.HLEN, .9, PartDial.HENDS, 1), 60, rampStrokes())});
        names.add("Lines Follow Stroke | old Hatch, compare | Lines Follow, ramp");
        ModularBrush follows = with(hh, PartDial.HEAD_TURN, 0);
        rows.add(new BufferedImage[]{draw(follows, 60, sCurveAndScribble(.35)), draw(StippleChecks.mk(PartChoice.HATCH, PartChoice.STEADY), 60, rampStrokes()), draw(follows, 60, rampStrokes())});
        String[] screens = PartDial.SCREEN.choices;
        for (int s = 1; s < screens.length; s++) {
            if (s < firstNew() && s != 6 && s != 7) continue;
            ModularBrush b = ScreenChecks.screened(ScreenChecks.ink(), s);
            b.set(BrushPart.HAND, new ModularBrush.Part(PartChoice.PRESS_DARKER)); b.part(BrushPart.HAND).set(PartDial.PD, 1); b.normalize();
            names.add("Print Shading: " + screens[s]);
            rows.add(new BufferedImage[]{ramp(PrintScreen.of(b.part(BrushPart.MEDIUM))), draw(b, 40, sCurve(70, .05, 1)), draw(b, 40, scribble(.6))});
        }
        // The Ben-Day brush preset (inkwash-v2-lab's ToolPresets; its Print style may not be on this branch: then a Ben-Day ink).
        ModularBrush benDay = ModularCode.decode("ben-day-brush.Me1zzeezz00000000ll_0i000000000000000000065ji000at_0_000_0_25ess00000000000000nz0.2ef");
        boolean preset = benDay != null;
        if (!preset) {
            benDay = ScreenChecks.screened(ScreenChecks.ink(), 6);
            benDay.set(BrushPart.HAND, new ModularBrush.Part(PartChoice.PRESS_DARKER)); benDay.part(BrushPart.HAND).set(PartDial.PD, 1); benDay.normalize();
        }
        names.add(preset ? "Ben-Day brush preset, 120 px" : "Ben-Day ink, 120 px, pressing darker");
        rows.add(new BufferedImage[]{draw(benDay, 120, rampStrokes()), draw(benDay, 120, sCurve(70, .05, 1)), draw(benDay, 120, scribble(.6))});
        int w = LABEL + 3 * (CELL_W + GAP), h = rows.size() * (CELL_H + GAP);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE); g.fillRect(0, 0, w, h);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font("SansSerif", Font.PLAIN, 15));
        for (int r = 0; r < rows.size(); r++) {
            int y = r * (CELL_H + GAP);
            g.setColor(Color.BLACK);
            int ly = y + 30;
            for (String part : names.get(r).split("[,|] ")) { g.drawString(part, 6, ly); ly += 20; }
            for (int c = 0; c < 3; c++) {
                g.drawImage(rows.get(r)[c], LABEL + c * (CELL_W + GAP), y, null);
                g.setColor(new Color(200, 200, 200)); g.drawRect(LABEL + c * (CELL_W + GAP), y, CELL_W - 1, CELL_H - 1);
            }
        }
        g.dispose();
        javax.imageio.ImageIO.write(img, "png", out);
        System.out.println("Wrote " + out);
    }

    /** The first Print Shading choice this branch adds (all after it are new). */
    static int firstNew() { return 11; }

    static ModularBrush with(ModularBrush b, Object... dials) {
        ModularBrush c = b.copy();
        for (int i = 0; i < dials.length; i += 2) c.part(BrushPart.TIP).set((PartDial) dials[i], ((Number) dials[i + 1]).doubleValue());
        return c.normalize();
    }
    static ModularBrush hatching() { return StippleChecks.mk(PartChoice.HAND_HATCH, PartChoice.STEADY); }

    /** {x, y, travel, time} samples. */
    static final class Path {
        final List<List<double[]>> strokes = new ArrayList<>();
        long t = 1000;
        List<double[]> cur;
        Path begin() { cur = new ArrayList<>(); strokes.add(cur); t += 200; return this; }
        Path to(double x, double y, double p) { cur.add(new double[]{x, y, p, t}); t += 6; return this; }
        Path hold(double x, double y, double p, int ms) { for (int k = 0; k < ms / 8; k++) { cur.add(new double[]{x, y, p, t}); t += 8; } return this; }
    }
    /** Back and forth over a box, rows {@code gap} apart, samples about 3 px apart. */
    static void zigzag(Path P, double x0, double y0, double w, double h, double gap, double p) {
        P.begin();
        boolean right = true;
        for (double y = y0; y <= y0 + h; y += gap) {
            for (double x = 0; x <= w; x += 3) P.to(right ? x0 + x : x0 + w - x, y, p);
            right = !right;
        }
    }
    static Path rampStrokes() {
        Path P = new Path();
        double[] ps = {.2, .45, .7, .95};
        for (int i = 0; i < 4; i++) zigzag(P, 20 + i * 125, 30, 90, 230, 18, ps[i]);
        return P;
    }
    /** Top: one light stroke going back and forth over a box once to four times. Bottom: a light stroke that stops and holds 0, .5, 1 and 2 s. */
    static Path passesAndHeld() {
        Path P = new Path();
        for (int i = 0; i < 4; i++) {
            P.begin();
            for (int k = 0; k <= i; k++) {
                boolean right = true;
                for (double y = 20 + k * 5; y <= 130; y += 18) { for (double x = 0; x <= 90; x += 3) P.to(right ? 20 + i * 125 + x : 110 + i * 125 - x, y, .3); right = !right; }
            }
        }
        int[] ms = {0, 500, 1000, 2000};
        P.begin();
        for (int i = 0; i < 4; i++) {
            double cx = 70 + i * 125;
            for (double x = cx - 50; x <= cx; x += 3) P.to(x, 225, .3);
            P.hold(cx, 225, .3, ms[i]);
            for (double x = cx; x <= cx + 50; x += 3) P.to(x, 225, .3);
        }
        return P;
    }
    static Path sCurve(double amp, double p0, double p1) {
        Path P = new Path().begin();
        for (int k = 0; k <= 300; k++) { double u = k / 300.0; P.to(30 + u * 460, 150 - amp * Math.sin(u * 2 * Math.PI), p0 + (p1 - p0) * u); }
        return P;
    }
    static Path sCurveAndScribble(double p) {
        Path P = sCurve(45, .1, 1);
        // shifted up: a loopy scribble below
        for (double[] s : P.cur) s[1] -= 60;
        Path Q = scribble(p, 200, 220, 230, 60);
        P.strokes.addAll(Q.strokes);
        return P;
    }
    static Path scribble(double p) { return scribble(p, 260, 150, 220, 120); }
    /** A loopy scribble filling an ellipse round {@code cx, cy}. */
    static Path scribble(double p, double cx, double cy, double rx, double ry) {
        Path P = new Path().begin();
        java.util.Random rnd = new java.util.Random(7);
        double a = 0, x = cx - rx * .8, y = cy;
        for (int k = 0; k < 1400; k++) {
            double u = k / 1400.0;
            double px = cx - rx * .85 + 1.7 * rx * u + 26 * Math.cos(k * .21), py = cy + ry * .8 * Math.sin(k * .037 + rnd.nextDouble() * .02) + 26 * Math.sin(k * .21);
            P.to(px, py, p);
        }
        return P;
    }

    static BufferedImage draw(ModularBrush b, double size, Path path) {
        ToneDocument d = InkWashLayers.blank(CELL_W, CELL_H);
        for (List<double[]> s : path.strokes) {
            ModularStroke st = ModularStroke.start(d, b, size, 1, null);
            for (int i = 0; i < s.size(); i++) {
                double[] q = s.get(i);
                st.sample((float) q[0], (float) q[1], ToolSettings.pressure((float) q[2]), Float.NaN, Float.NaN, (long) q[3]);
                if (i % 4 == 3) st.present();
            }
            st.present(); st.finish();
        }
        return image(d);
    }
    static BufferedImage image(ToneDocument d) {
        BufferedImage img = new BufferedImage(d.width, d.height, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < d.height; y++) for (int x = 0; x < d.width; x++) { int t = d.compositeTone(x, y); img.setRGB(x, y, 0xff000000 | t << 16 | t << 8 | t); }
        return img;
    }
    /** The screen itself, darkness 0 at the left to 1 at the right, in eight steps. */
    static BufferedImage ramp(PrintScreen p) {
        BufferedImage img = new BufferedImage(CELL_W, CELL_H, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < CELL_H; y++) for (int x = 0; x < CELL_W; x++) {
            double a = y < CELL_H / 2 ? (x + .5) / CELL_W : (Math.floor(x / (CELL_W / 8.0)) + .5) / 8;
            int t = p.ink(a, x, y, CELL_H) ? 0 : 255;
            img.setRGB(x, y, 0xff000000 | t << 16 | t << 8 | t);
        }
        return img;
    }
}
