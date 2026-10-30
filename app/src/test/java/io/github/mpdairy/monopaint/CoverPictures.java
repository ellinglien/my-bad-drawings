package io.github.mpdairy.monopaint;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * Notebook covers as the Manta shows them, for looking (not a check): {@code java CoverPictures <out folder>}
 * (scripts/cover_pictures.sh). The shelf in portrait with a few notebooks in several designs (plain covers, one with a
 * page as its cover picture, one drawn on), each tile's cover made as the app makes it ({@link NotebookCovers#shown},
 * the label's letters by {@link CoverChecks.Letters}); a Classic Black Marble front and back at the page's size; a
 * front cover drawn on over its cover picture, and its back cover; and the nine designs' fronts side by side, as the
 * chooser shows them. Smooth greys, as the copies.
 */
public final class CoverPictures {
    static final float DP = 1.875f;

    public static void main(String[] args) throws Exception {
        Assets.install(Assets.folder(Assets.FOLDER));
        NotebookCovers.lettering = new CoverChecks.Letters();
        File out = new File(args.length > 0 ? args[0] : ".");
        out.mkdirs();
        // A Classic Black Marble notebook's front and back covers at the page's size, nothing drawn.
        DrawingBook plain = new DrawingBook(new ToneDocument(1920, 2400));
        plain.name("2026-10-19 brisk-bean notebook");
        plain.select(DrawingBook.FRONT);
        write(plain.current(), new File(out, "2-classic-front.png"));
        plain.select(DrawingBook.BACK);
        write(plain.current(), new File(out, "3-classic-back.png"));
        // A drawn front cover with a page as its cover picture, and the back cover, at the page's size, in Swirl.
        DrawingBook book = new DrawingBook(NotebookFixture.sketch(1920, 2400, 7));
        book.addPage(); NotebookBench.copy(NotebookFixture.sketch(1920, 2400, 8), book.current());
        book.select(0);
        String harbour = "2026-10-19 harbour sketches";
        book.name(harbour);
        book.setDesign(CoverDesign.SWIRL);
        book.setCover(1);
        book.select(DrawingBook.FRONT);
        drawOn(book.current(), 11);
        write(book.current(), new File(out, "4-swirl-front-drawn-with-picture.png"));
        byte[] front = PagePicture.of(book.current().layerSnapshot()).encode();
        book.select(DrawingBook.BACK);
        write(book.current(), new File(out, "5-swirl-back.png"));
        book.select(1);
        byte[] pictured = PagePicture.of(book.current().layerSnapshot()).encode();
        // The shelf.
        Tile[] tiles = {
                new Tile("2026-10-19 brisk-bean notebook", "1 page · today", CoverDesign.CLASSIC, 1785, 2560, null, null),
                new Tile(harbour, "2 pages · today", CoverDesign.SWIRL, 1785, 2560, pictured, front),
                new Tile("Loose pages", "43 pages · yesterday", CoverDesign.CLASSIC, 1785, 2560, null, null),
                new Tile("2026-10-17 zesty-kale notebook", "12 pages · Oct 17", CoverDesign.TERRAZZO, 1785, 2560, pictured, null),
                new Tile("2026-10-16 dapper-fig notebook", "5 pages · Oct 16", CoverDesign.WOOD, 1785, 2560, null, null),
                new Tile("Comics", "24 pages · Oct 15", CoverDesign.BOLD, 1785, 2560, null, doodled()),
                new Tile("2026-10-14 fizzy-plum notebook", "3 pages · Oct 14", CoverDesign.CRACKLE, 1785, 2560, null, null),
                new Tile("2026-10-13 plucky-pea notebook", "8 pages · Oct 13", CoverDesign.ENGRAVED, 1785, 2560, null, null),
                new Tile("2026-10-12 jolly-yam notebook", "2 pages · Oct 12", CoverDesign.REVERSE, 1785, 2560, null, null),
                new Tile("2026-10-11 snappy-leek notebook", "6 pages · Oct 11", CoverDesign.SPECKLE, 1785, 2560, null, null),
        };
        shelf(new File(out, "1-shelf-manta.png"), tiles);
        designs(new File(out, "6-all-designs.png"), "2026-10-19 brisk-bean notebook");
        System.out.println("wrote " + out);
    }

    /** A cover drawn on: a title written over the label's name, a frame and some lines across the picture, with a fineliner and a brush pen. */
    static void drawOn(ToneDocument cover, long seed) {
        Random random = new Random(seed);
        WatercolorSession session = new WatercolorSession();
        ModularBrush line = NotebookFixture.brush("Fineliner", "Steady line");
        for (int i = 0; i < 6; i++) NotebookFixture.stroke(cover, line, 5, 1, session, random);
        session.settleNow(cover);
    }
    /** A front cover's marks alone, doodled on. */
    static byte[] doodled() {
        ToneDocument cover = new ToneDocument(1920, 2400);
        cover.placePaper(NotebookCovers.paper(true, CoverDesign.BOLD, "Comics", 1920, 2400, null));
        drawOn(cover, 3);
        return PagePicture.of(cover.layerSnapshot()).encode();
    }
    static void write(ToneDocument page, File file) throws Exception {
        BufferedImage image = new BufferedImage(page.width, page.height, BufferedImage.TYPE_BYTE_GRAY);
        PagePicture.Composite composite = new PagePicture.Composite(page.layerSnapshot());
        int[] row = new int[page.width];
        byte[] bytes = new byte[page.width];
        for (int y = 0; y < page.height; y++) {
            composite.row(y, row);
            for (int x = 0; x < page.width; x++) bytes[x] = (byte) DotPattern.exportGray(row[x]);
            image.getRaster().setDataElements(0, y, page.width, 1, bytes);
        }
        ImageIO.write(image, "png", file);
    }

    static final class Tile {
        final String name, about; final CoverDesign design; final int pageWidth, pageHeight; final byte[] pictured, front;
        Tile(String name, String about, CoverDesign design, int pageWidth, int pageHeight, byte[] pictured, byte[] front) {
            this.name = name; this.about = about; this.design = design; this.pageWidth = pageWidth; this.pageHeight = pageHeight; this.pictured = pictured; this.front = front;
        }
    }
    /** The nine designs' fronts for {@code name}, 480 × 600 each in a row of five and a row of four, named under them. */
    static void designs(File file, String name) throws Exception {
        int w = 480, h = 600, gap = 40, under = 56, columns = 5;
        CoverDesign[] all = CoverDesign.values();
        int rows = (all.length + columns - 1) / columns;
        BufferedImage image = new BufferedImage(columns * w + (columns + 1) * gap, rows * (h + under) + (rows + 1) * gap, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE); g.fillRect(0, 0, image.getWidth(), image.getHeight());
        for (int i = 0; i < all.length; i++) {
            int x = gap + (i % columns) * (w + gap), y = gap + (i / columns) * (h + under + gap);
            image.getRaster().setDataElements(x, y, w, h, grey(NotebookCovers.paper(true, all[i], name, w, h, null)));
            g.setColor(Color.BLACK); g.setStroke(new BasicStroke(2)); g.drawRect(x - 1, y - 1, w + 1, h + 1);
            g.setFont(CoverChecks.Letters.BOLD.deriveFont(28f));
            g.drawString(all[i].label, x, y + h + 40);
        }
        g.dispose();
        ImageIO.write(image, "png", file);
    }
    /** Tones as the copies show them. */
    static byte[] grey(byte[] tones) {
        byte[] grey = new byte[tones.length];
        for (int i = 0; i < tones.length; i++) grey[i] = (byte) DotPattern.exportGray(tones[i] & 255);
        return grey;
    }
    static int px(double dp) { return Math.round((float) dp * DP); }

    /** The shelf on the Manta in portrait, as {@code NotebookScreens.shelf} lays it out ({@link NotebookLayout}). */
    static void shelf(File file, Tile[] notebooks) throws Exception {
        int[] screen = NotebookLayout.SCREENS[0];
        NotebookLayout l = new NotebookLayout(screen[0], screen[1], NotebookLayout.aspectOf(screen));
        BufferedImage image = new BufferedImage(px(screen[0]), px(screen[1]), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE); g.fillRect(0, 0, image.getWidth(), image.getHeight());
        Font bold = CoverChecks.Letters.BOLD, regular = Font.createFont(Font.TRUETYPE_FONT, new File("app/src/main/res/font/ia_writer_duo_regular.ttf"));
        g.setColor(Color.BLACK);
        g.setFont(bold.deriveFont(px(TypeStyle.HEADING.sp * LabText.SCALE) * 1f));
        g.drawString("Notebooks", px(l.margin), px(l.margin + NotebookLayout.BAR / 2.0 + 8));
        int top = l.margin + NotebookLayout.BAR + l.gap;
        for (int k = 0; k <= notebooks.length && k < l.perPage(); k++) {
            int r = k / l.columns, c = k % l.columns;
            int x = px(l.margin + c * (l.tileWidth + l.columnGap)), y = px(top + r * (l.tileHeight + l.rowGap));
            int w = px(l.tileWidth), h = px(l.coverHeight);
            if (k == 0) {
                g.setColor(new Color(0x888888));
                g.setStroke(new BasicStroke(px(2), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{px(6), px(4)}, 0));
                g.drawRect(x + px(1), y + px(1), w - px(2), h - px(2));
                g.setStroke(new BasicStroke(px(3)));
                int cx = x + w / 2, cy = y + h / 2, s = px(14);
                g.drawLine(cx - s, cy, cx + s, cy); g.drawLine(cx, cy - s, cx, cy + s);
                text(g, regular, "New Notebook", x, y + h + px(NotebookLayout.UNDER_COVER), w, TypeStyle.BODY.sp, Color.BLACK, l.nameLines);
                continue;
            }
            Tile t = notebooks[k - 1];
            // The cover fitted in the tile, as the app draws it, then its outline.
            float scale = Math.min(w / (float) t.pageWidth, h / (float) t.pageHeight);
            int cw = Math.max(1, Math.round(t.pageWidth * scale)), ch = Math.max(1, Math.round(t.pageHeight * scale));
            String shown = DrawingNames.shown(t.name, java.time.LocalDate.of(2026, 10, 19));
            byte[] tones = NotebookCovers.shown(true, t.design, t.name, cw, ch,
                    t.pictured == null ? null : PagePicture.decode(t.pictured), t.front == null ? null : PagePicture.decode(t.front));
            BufferedImage cover = new BufferedImage(cw, ch, BufferedImage.TYPE_BYTE_GRAY);
            cover.getRaster().setDataElements(0, 0, cw, ch, grey(tones));
            g.drawImage(cover, x + (w - cw) / 2, y + (h - ch) / 2, null);
            g.setColor(Color.BLACK);
            g.setStroke(new BasicStroke(px(2)));
            g.drawRect(x + px(1), y + px(1), w - px(2), h - px(2));
            int under = y + h + px(NotebookLayout.UNDER_COVER);
            int lines = text(g, regular, shown, x, under, w, TypeStyle.BODY.sp, Color.BLACK, l.nameLines);
            text(g, regular, t.about, x, under + Math.round(lines * px(LabText.line(TypeStyle.BODY.sp))), w, TypeStyle.CAPTION.sp, new Color(0x555555), NotebookLayout.COUNT_LINES);
        }
        g.dispose();
        ImageIO.write(image, "png", file);
    }
    /** {@code words} wrapped in {@code width} px at {@code sp}, from {@code y} down; how many lines. */
    static int text(Graphics2D g, Font face, String words, int x, int y, int width, float sp, Color color, int most) {
        g.setColor(color);
        g.setFont(face.deriveFont(px(sp * LabText.SCALE) * 1f));
        java.util.List<String> lines = LabText.wrap(words, width / DP, LabText.face(sp, 0));
        float line = px(LabText.line(sp));
        for (int i = 0; i < Math.min(most, lines.size()); i++) g.drawString(lines.get(i), x, y + (i + 1) * line - line * .25f);
        return Math.min(most, lines.size());
    }
}
