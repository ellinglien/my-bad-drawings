package io.github.mpdairy.monopaint;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.Random;

/**
 * Fridge magnets as the tablets show them, for looking (not a check): {@code java MagnetPictures <out folder>}. The
 * drawing screen with Words open on the Manta and the Nomad (panel pixels, the panel's 16 greys; the page drawn by the
 * app's engine, the magnets and bin by {@link Awt}, which follows {@code MagnetTiles} on the desktop), tiles close up
 * (straight and tilted, at 1:1 and 4×), and a PNG copy made by {@link Magnets#stamp}, the copy's one way.
 */
public final class MagnetPictures {
    static final float DP = 1.875f;

    public static void main(String[] args) throws Exception {
        Assets.install(Assets.folder(Assets.FOLDER));
        File out = new File(args.length > 0 ? args[0] : ".");
        out.mkdirs();
        screen(out, "1-manta-words-open.png", 1024, 1365);
        screen(out, "2-nomad-words-open.png", 749, 998);
        closeUp(out);
        System.out.println("wrote " + out);
    }

    /** The tiles as {@code MagnetTiles} draws them, through Java2D: same sizes, same font, antialiased. */
    static final class Awt implements Magnets.Tiles {
        /** The tiles' face, Roboto Mono Bold; and the app's, iA Writer Duo Bold, for the bin's header. */
        static final Font FACE, BOLD;
        static {
            try {
                FACE = Font.createFont(Font.TRUETYPE_FONT, new File("app/src/main/res/font/roboto_mono_bold.ttf")).deriveFont((float) Magnets.EM);
                BOLD = Font.createFont(Font.TRUETYPE_FONT, new File("app/src/main/res/font/ia_writer_duo_bold.ttf"));
            }
            catch (Exception e) { throw new IllegalStateException(e); }
        }
        BufferedImage picture(String text, int tilt, int turn, boolean picked) {
            int[] e = Magnets.extent(text, tilt, turn);
            BufferedImage b = new BufferedImage(e[2] - e[0], e[3] - e[1], BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = smooth(b.createGraphics());
            g.translate(-e[0], -e[1]);
            g.rotate(Math.toRadians(Magnets.degrees(tilt, turn)));
            float hw = Magnets.width(text) / 2f, hh = Magnets.HEIGHT / 2f, s = Magnets.SHADOW, line = Magnets.LINE, r = 2 * Magnets.CORNER;
            g.setColor(Color.BLACK);
            g.fill(new java.awt.geom.RoundRectangle2D.Float(-hw + s, -hh + s, 2 * hw, 2 * hh, r, r));
            g.setColor(picked ? Color.BLACK : new Color(0x88, 0x88, 0x88));
            g.fill(new java.awt.geom.RoundRectangle2D.Float(-hw, -hh, 2 * hw, 2 * hh, r, r));
            if (!picked) { g.setColor(Color.WHITE); g.fill(new java.awt.geom.RoundRectangle2D.Float(-hw + line, -hh + line, 2 * hw - 2 * line, 2 * hh - 2 * line, r - 2 * line, r - 2 * line)); }
            g.setColor(picked ? Color.WHITE : Color.BLACK);
            g.setFont(FACE);
            float w = (float) FACE.getStringBounds(text, g.getFontRenderContext()).getWidth();
            g.drawString(text, -w / 2, -hh + Magnets.BASELINE);
            g.dispose();
            return b;
        }
        @Override public Magnets.Tile tile(Magnets.Magnet m) {
            BufferedImage b = picture(m.word, m.tilt, m.turn, false);
            return new Magnets.Tile(b.getRGB(0, 0, b.getWidth(), b.getHeight(), null, 0, b.getWidth()), b.getWidth(), b.getHeight());
        }
    }
    static Graphics2D smooth(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g;
    }

    /** A page drawn by the app's engine: a wash wave and a few ink lines. */
    static ToneDocument drawing(int w, int h) {
        ToneDocument d = InkWashLayers.blank(w, h);
        WatercolorSession s = new WatercolorSession();
        ModularBrush wash = CarryPictures.brush("Watercolor", PartChoice.WASH, PartChoice.OFF, PartChoice.NONE);
        ModularBrush pen = CarryPictures.brush("Fineliner", PartChoice.INK, PartChoice.OFF, PartChoice.NONE);
        d.selectLayer(0);
        CarryPictures.stroke(d, s, wash, w / 6.0, .55, CarryPictures.wave(w * .12, w * .88, h * .42, h * .12, .8));
        d.selectLayer(1);
        for (int i = 0; i < 5; i++) CarryPictures.stroke(d, s, pen, 5, 1, CarryPictures.wave(w * .1, w * .9, h * (.25 + i * .06), h * .03, .7));
        s.settleNow(d);
        return d;
    }

    static final String[][] POEM = {{"a", "languid", "moon", "s"}, {"whisper", "ing", "of", "ink"}, {"and", "blue", "storm", "y"}, {"dream", "s"}};

    static void screen(File out, String name, int wDp, int hDp) throws Exception {
        DrawLayout chrome = new DrawLayout(wDp, hDp);
        int sw = Math.round(wDp * DP), sh = Math.round(hDp * DP), side = Math.round(chrome.side * DP), pw = sw - side;
        ToneDocument page = drawing(pw, sh);
        Random random = new Random(4);
        Magnets magnets = Magnets.NONE;
        int y = Math.round(sh * .12f);
        for (String[] line : POEM) {
            int x = Math.round(pw * .1f);
            for (String word : line) {
                int w = Magnets.width(word);
                magnets = magnets.with(Magnets.inside(new Magnets.Magnet(word, x + w / 2, y, random.nextInt(51) - 25, 0), pw, sh));
                // Suffixes snug up to their word, as on a fridge.
                x += w + (word.length() <= 3 && !word.equals("and") ? 4 : 18);
            }
            y += Magnets.HEIGHT + 22;
        }
        page.setMagnets(magnets);
        BufferedImage img = new BufferedImage(sw, sh, BufferedImage.TYPE_INT_RGB);
        // The canvas: the page's dots, as the panel shows a page.
        for (int py = 0; py < sh; py++) for (int px = 0; px < pw; px++) img.setRGB(side + px, py, DotPattern.pixel(page.compositeTone(px, py), px, py));
        Graphics2D g = smooth(img.createGraphics());
        Awt tiles = new Awt();
        for (Magnets.Magnet m : page.magnets().list()) { int[] b = Magnets.bounds(m); g.drawImage(tiles.picture(m.word, m.tilt, m.turn, false), side + b[0], b[1], null); }
        // A magnet moving under the pen: its outline (WordsMode), over the wash.
        Magnets.Magnet held = new Magnets.Magnet("whisper", Math.round(pw * .62f), Math.round(sh * .5f), 18, 0);
        int[] hf = Magnets.face(held);
        int line = MagnetDrag.OUTLINE - 1;
        g.setColor(Color.BLACK);
        for (int[] band : MagnetDrag.outline(held)) g.fillRect(side + band[0], band[1], band[2] - band[0], band[3] - band[1]);
        g.setColor(Color.WHITE);
        g.drawRect(side + hf[0] + line, hf[1] + line, hf[2] - hf[0] - 2 * line - 1, hf[3] - hf[1] - 2 * line - 1);
        bar(g, side, sh, chrome);
        // The bin.
        WordBinLayout bin = new WordBinLayout(pw, sh, DP, false);
        int top = sh - bin.height;
        g.setColor(Color.WHITE); g.fillRect(side, top, pw, bin.height);
        g.setColor(Color.BLACK); g.fillRect(side, top, pw, bin.rule);
        int hy = top + bin.rule + bin.pad, cy = hy + bin.header / 2;
        Font label = Awt.BOLD.deriveFont(14 * 1.2f * DP), caption = Font.createFont(Font.TRUETYPE_FONT, new File("app/src/main/res/font/ia_writer_duo_regular.ttf")).deriveFont(13 * 1.2f * DP);
        g.setFont(label);
        int lx = side + bin.pad;
        lx += text(g, "WORDS", lx, cy, Color.BLACK, .06f) + Math.round(8 * DP);
        List<WordPacks.Pack> packs = WordPacks.packs(null, new Random(9), new java.util.HashMap<>());
        List<List<String>> pages = bin.pages(packs.get(0).tiles);
        g.setFont(caption);
        text(g, WordPacks.KIT + " 1/" + pages.size(), lx, cy, new Color(0x55, 0x55, 0x55), 0);
        g.setFont(label);
        int bx = side + pw - bin.pad;
        bx = button(g, "DONE", bx, hy, bin.header, true);
        button(g, "MORE WORDS", bx - Math.round(8 * DP), hy, bin.header, false);
        List<String> first = pages.get(0);
        int[][] at = bin.places(first);
        for (int i = 0; i < first.size(); i++) g.drawImage(tiles.picture(first.get(i), 0, 0, i == 3), side + at[i][0] - Magnets.EDGE, top + at[i][1] - Magnets.EDGE, null);
        g.dispose();
        sixteen(img);
        javax.imageio.ImageIO.write(img, "png", new File(out, name));
        BufferedImage half = new BufferedImage(sw / 2, sh / 2, BufferedImage.TYPE_INT_RGB);
        Graphics2D hg = half.createGraphics();
        hg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        hg.drawImage(img, 0, 0, sw / 2, sh / 2, null); hg.dispose();
        javax.imageio.ImageIO.write(half, "png", new File(out, name.replace(".png", "-half.png")));
        // The PNG copy of that page, through the one shared way.
        int[] copy = page.exportPixels(true);
        Magnets.stamp(copy, pw, sh, page.magnets(), tiles);
        BufferedImage png = new BufferedImage(pw, sh, BufferedImage.TYPE_INT_RGB);
        png.setRGB(0, 0, pw, sh, copy, 0, pw);
        javax.imageio.ImageIO.write(png, "png", new File(out, name.replace("words-open", "png-copy")));
        System.out.println(name + ": page " + pw + "x" + sh + ", bin " + bin.height + " px (" + bin.rows + " rows, " + first.size() + " tiles on its first page, " + pages.size() + " pages)");
    }
    /** A sketch of the bar: its column, the Words button ink-filled. */
    static void bar(Graphics2D g, int side, int h, DrawLayout d) {
        g.setColor(Color.WHITE); g.fillRect(0, 0, side, h);
        g.setColor(new Color(0xbb, 0xbb, 0xbb));
        int y = Math.round(DrawLayout.PAD * DP);
        for (int i = 0; i < 18; i++) { g.fillRect(Math.round(8 * DP), y, side - Math.round(16 * DP), Math.round(d.icon * DP * .7f)); y += Math.round(d.icon * DP * .85f); }
        int wy = h - Math.round((d.wet + d.icon + 40) * DP);
        g.setColor(Color.BLACK); g.fillRect(0, wy, side, Math.round(d.icon * DP));
        g.setColor(Color.WHITE); g.setFont(Awt.BOLD.deriveFont(12 * DP));
        text(g, "W", side / 2 - Math.round(5 * DP), wy + Math.round(d.icon * DP / 2), Color.WHITE, 0);
    }
    static int text(Graphics2D g, String s, int x, int cy, Color c, float tracking) {
        g.setColor(c);
        java.awt.FontMetrics fm = g.getFontMetrics();
        int base = cy + (fm.getAscent() - fm.getDescent()) / 2, w = 0;
        if (tracking == 0) { g.drawString(s, x, base); return fm.stringWidth(s); }
        for (char ch : s.toCharArray()) { g.drawString(String.valueOf(ch), x + w, base); w += fm.charWidth(ch) + Math.round(tracking * g.getFont().getSize2D()); }
        return w;
    }
    /** A label button ending at {@code right}: outlined, or ink-filled; returns its left. */
    static int button(Graphics2D g, String s, int right, int top, int height, boolean solid) {
        int pad = Math.round(12 * DP), w = g.getFontMetrics().stringWidth(s) + Math.round(.06f * g.getFont().getSize2D()) * s.length() + 2 * pad, left = right - w;
        g.setColor(Color.BLACK);
        if (solid) g.fillRect(left, top, w, height);
        else { g.setStroke(new BasicStroke(2 * DP)); g.drawRect(left + 2, top + 2, w - 4, height - 4); }
        text(g, s, left + pad, top + height / 2, solid ? Color.WHITE : Color.BLACK, .06f);
        return left;
    }
    /** The panel's 16 greys. */
    static void sixteen(BufferedImage img) {
        for (int y = 0; y < img.getHeight(); y++) for (int x = 0; x < img.getWidth(); x++) {
            int v = img.getRGB(x, y) & 255, q = Math.round(v / 17f) * 17;
            img.setRGB(x, y, 0xff000000 | q * 0x010101);
        }
    }
    static void closeUp(File out) throws Exception {
        Awt tiles = new Awt();
        String[] words = {"s", "moon", "whisper", "apparatus"};
        int[] tilts = {0, 8, -17, 25};
        BufferedImage img = new BufferedImage(1200, 420, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE); g.fillRect(0, 0, 1200, 420);
        int x = 30;
        for (int i = 0; i < words.length; i++) {
            BufferedImage straight = tiles.picture(words[i], 0, 0, false), tilted = tiles.picture(words[i], tilts[i], 0, false);
            g.drawImage(straight, x, 30, null); g.drawImage(tilted, x, 160, null);
            x += Math.max(straight.getWidth(), tilted.getWidth()) + 30;
        }
        BufferedImage picked = tiles.picture("ink", 0, 0, true);
        g.drawImage(picked, 30, 290, null);
        g.dispose();
        sixteen(img);
        javax.imageio.ImageIO.write(img, "png", new File(out, "3-tiles-1to1.png"));
        // 4x, nearest: the pixels the panel gets, a tilted edge's greys.
        BufferedImage zoom = new BufferedImage(1600, 720, BufferedImage.TYPE_INT_RGB);
        Graphics2D z = zoom.createGraphics();
        z.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        z.drawImage(img.getSubimage(0, 140, 400, 180), 0, 0, 1600, 720, null);
        z.dispose();
        javax.imageio.ImageIO.write(zoom, "png", new File(out, "4-tiles-tilted-4x.png"));
    }
}
