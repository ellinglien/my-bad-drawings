package io.github.mpdairy.monopaint;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.TextAttribute;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.text.AttributedString;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * Pictures of the Tools page on the desktop (scripts/tools_pictures.sh): the page as {@link ToolsActivity} lays it out
 * from {@link ToolsLayout}'s sizes, in the app's face (Ukulele, iA Writer Duo for what it lacks, as {@link Theme#face}),
 * at the app's 1.2× text and 1.875 px a dp (both tablets): the owner's drawings as {@link ToolCard} places them ({@link
 * ToolsLayout#art}), a brush with none its engine stroke ({@link WildSample}, as {@link LabLibrary#traySampler}). A
 * stand-in for the device's views, not them: the icons are drawn plainly and the scratch pad is blank. One picture a
 * screen, and one of the Manta's with the selected slot's underline at the blink's grey-600 step. The vector helpers
 * ({@link #path}, {@link #art}) are DrawScreenPictures' too.
 */
public final class ToolsPagePictures {
    static final float DP = 1.875f;
    static final Color INK = Color.BLACK, PAPER = Color.WHITE, GREY_400 = new Color(0xbbbbbb), GREY_600 = new Color(0x888888), GREY_800 = new Color(0x555555);
    /** The faces {@link #label} and {@link #words} draw in, and what draws a character they lack (null: none). */
    static Font regular, bold, fallback, fallbackBold;
    /** Theme's strokes, dp: base, a header's rule, heavy. */
    static final int BASE = 2, RULE = 3, HEAVY = 4;

    public static void main(String[] args) throws Exception {
        Assets.install(Assets.folder(Assets.FOLDER));
        File out = new File(args.length > 0 ? args[0] : "/tmp/tools-pictures");
        out.mkdirs();
        regular = Font.createFont(Font.TRUETYPE_FONT, new File("app/src/main/res/font/ukulele.ttf"));
        bold = Font.createFont(Font.TRUETYPE_FONT, new File("app/src/main/res/font/ukulele_bold.ttf"));
        fallback = Font.createFont(Font.TRUETYPE_FONT, new File("app/src/main/res/font/ia_writer_duo_regular.ttf"));
        fallbackBold = Font.createFont(Font.TRUETYPE_FONT, new File("app/src/main/res/font/ia_writer_duo_bold.ttf"));
        String[] names = {"manta-portrait", "manta-landscape", "nomad-portrait", "nomad-landscape"};
        for (int i = 0; i < DrawLayoutChecks.SCREENS.length; i++) {
            int[] s = DrawLayoutChecks.SCREENS[i];
            File file = new File(out, "tools-" + names[i] + ".png");
            draw(file, s[0], s[1], INK);
            System.out.println(file);
        }
        File blink = new File(out, "tools-manta-portrait-blink.png");
        draw(blink, 1024, 1365, GREY_600);
        System.out.println(blink);
    }

    static int px(double dp) { return Math.round((float) dp * DP); }
    /** A card's sample, as {@code LoopCard.Sample}: {@code width × height} px of greys. */
    interface Sample { int[] pixels(int width, int height); }

    static void draw(File file, int width, int height, Color underline) throws Exception {
        ToolsLayout l = new ToolsLayout(width, height);
        Map<String, String> prefs = new HashMap<>();
        BrushLibrary.Store store = new BrushLibrary.Store() {
            public String get(String key) { return prefs.get(key); }
            public void put(String key, String value) { if (value == null) prefs.remove(key); else prefs.put(key, value); }
        };
        ToolSlots slots = new ToolSlots(store, new Random(1));
        // As the owner's mockup: the first slot selected, the Tools tab under All.
        ToolsPage page = new ToolsPage(slots, store, 0);
        page.filter(ToolsPage.Chip.ALL);
        page.layout(l.perPage(), l.columns, l.cardWidth);
        BufferedImage image = new BufferedImage(px(width), px(height), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(PAPER); g.fillRect(0, 0, image.getWidth(), image.getHeight());
        bar(g, l);
        int x = l.side, y = ToolsLayout.BAR + l.gap;
        pad(g, l, page, x, y);
        y += l.testHeight + l.label;
        words(g, ToolsLayout.TOOLBAR, false, TypeStyle.CAPTION.sp, x, y - l.gap / 2f - LabText.line(TypeStyle.CAPTION.sp), GREY_800);
        // Tools First at its right, off: a chip.
        float sx = x + l.top - l.switchWidth(), sy = y - l.switchFoot() - ToolsLayout.CHIP;
        rounded(g, sx, sy, l.switchWidth(), ToolsLayout.CHIP, 1, ToolsLayout.CHIP / 2f, INK, null);
        words(g, DrawLayout.TOOLS_FIRST, false, TypeStyle.CAPTION.sp, sx + l.chipPad() + 1, sy + (ToolsLayout.CHIP - LabText.line(TypeStyle.CAPTION.sp)) / 2, INK);
        for (int i = 0; i < l.slotCount; i++) {
            int c = i % l.slotColumns, r = i / l.slotColumns;
            Brush brush = page.current(i);
            ToolSwatches.Look look = page.look(i);
            boolean selected = i == page.slot();
            card(g, brush, ToolsPage.name(brush), selected, !selected, selected ? underline : null, x + c * (l.slotWidth + l.gap), y + r * (l.slotCard + l.gap),
                    l.slotWidth, l.slotCard, l.slotArt(), l.slotArtHeight(), (w, h) -> WildSample.slotTray(brush.parts, look, w, h));
        }
        y += l.slots;
        if (l.wide) { x = l.side + l.top + l.apart; y = ToolsLayout.BAR; } else y += l.apart;
        library(g, l, page, x, y);
        g.dispose();
        ImageIO.write(image, "png", file);
    }

    /** Back (the caret and its word), "Tools" in bold, Undo greyed, Brush Lab in a rounded box. */
    static void bar(Graphics2D g, ToolsLayout l) {
        int y = (ToolsLayout.BAR - 44) / 2;
        button(g, "Back", false, false, true, l.side, y, LabLayout.backWidth(), 44, 0, "prev");
        int x = l.side + LabLayout.backWidth() + 10;
        words(g, ToolsLayout.TITLE, true, TypeStyle.HEADING.sp, x, ToolsLayout.BAR / 2f - LabText.line(TypeStyle.HEADING.sp) / 2, INK);
        int lab = ToolsLayout.buttonWidth(ToolsLayout.LAB), undo = l.undoWidth(), right = l.width - l.side, by = (ToolsLayout.BAR - 40) / 2;
        button(g, ToolsLayout.LAB, false, false, true, right - lab, by, lab, 40, 0, "flask");
        rounded(g, right - lab, by, lab, 40, BASE, ToolsLayout.PAD_RADIUS, INK, null);
        button(g, l.compact ? null : ToolsLayout.UNDO, false, true, true, right - lab - 10 - undo, by, undo, 40, 0, "undo");
    }
    /** The scratch pad: a rounded box, Clear in its corner, the tool's name (bold) and line in its foot. */
    static void pad(Graphics2D g, ToolsLayout l, ToolsPage page, int x, int y) {
        rounded(g, x, y, l.testWidth, l.testHeight, BASE, ToolsLayout.PAD_RADIUS, INK, null);
        float sp = TypeStyle.CAPTION.sp, line = LabText.line(sp);
        float clear = LabText.width("Clear", sp, 0);
        words(g, "Clear", false, sp, x + l.testWidth - l.gap / 2f - l.wet() - 8 - clear, y + (ToolsLayout.PAD_ROW - line) / 2 + 3, GREY_800);
        ToolsPage.Card card = ToolsPage.card(page.current(), l.padRoom() / 2f);
        float top = y + l.testHeight - l.gap - line;
        words(g, card.name, true, sp, x + l.gap, top, INK);
        if (!card.line.isEmpty() && !card.line.equalsIgnoreCase(card.name))
            words(g, " · " + card.line, false, sp, x + l.gap + width(g, card.name, true, sp), top, GREY_800);
    }
    /**
     * A {@link ToolCard} at {@code x, y}, {@code w × h} dp: the drawing (or {@code stroke}) in its {@code aw × ah} art box,
     * its name under it (bold when {@code strong}), underlined in {@code underline} (null: not); a slot not selected
     * ({@code grey}) its ink in grey-600 (ToolCard.GREY), picture and name.
     */
    static void card(Graphics2D g, Brush brush, String name, boolean strong, boolean grey, Color underline, float x, float y, int w, int h, int aw, int ah, Sample stroke) throws java.io.IOException {
        String drawn = DrawLayout.drawing(brush);
        BufferedImage box = new BufferedImage(px(aw), px(ah), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D clip = box.createGraphics();
        clip.setRenderingHints(g.getRenderingHints());
        clip.setColor(PAPER); clip.fillRect(0, 0, box.getWidth(), box.getHeight());
        if (drawn != null) {
            double[] size = artSize(drawn);
            float[] at = ToolsLayout.art((float) size[0], (float) size[1], px(aw), px(ah));
            art(clip, drawn, at[0], at[1], at[2], at[3]);
        } else sample(clip, stroke.pixels(px(aw), px(ah)), px(aw), px(ah), 0, 0);
        clip.dispose();
        // As the mask ToolCard draws in grey: ink lifted to the grey, paper kept.
        int lift = 255 - GREY_600.getRed();
        if (grey) for (int py = 0; py < box.getHeight(); py++) for (int qx = 0; qx < box.getWidth(); qx++) {
            int v = box.getRaster().getSample(qx, py, 0);
            box.getRaster().setSample(qx, py, 0, 255 - (255 - v) * lift / 255);
        }
        g.drawImage(box, px(x), px(y), null);
        float sp = TypeStyle.CAPTION.sp;
        words(g, cut(g, name, strong, sp, w - ToolsLayout.NAME_END), strong, sp, x, y + ah + ToolsLayout.NAME_GAP, grey ? GREY_600 : INK);
        if (underline != null) { g.setColor(underline); g.fillRect(px(x), px(y + h - DrawLayout.MARK_CLEAR - DrawLayout.UNDERLINE), px(Math.min(w, aw)), px(DrawLayout.UNDERLINE)); }
    }
    /** The Library: the tabs (the chosen bold and underlined), the pages at their right, the chips, the grid. */
    static void library(Graphics2D g, ToolsLayout l, ToolsPage page, int x, int y) throws java.io.IOException {
        float body = TypeStyle.BODY.sp, tx = x;
        for (ToolsPage.Tab t : ToolsPage.Tab.values()) {
            boolean on = t == page.tab();
            words(g, t.label, on, body, tx, y + (ToolsLayout.TAB - LabText.line(body)) / 2, INK);
            float tw = width(g, t.label, on, body);
            if (on) { g.setColor(INK); g.fillRect(px(tx), px(y + ToolsLayout.TAB - 3 - RULE), px(tw), px(RULE)); }
            tx += tw + l.tabGap();
        }
        // The search's magnifier, then the pages.
        String pagesSaid = (page.page() + 1) + " / " + page.pages();
        float pagerW = page.pages() > 1 ? 2 * ToolsLayout.PAGE_BUTTON + 8 + LabText.width(pagesSaid, TypeStyle.CAPTION.sp, 0) : 0;
        icon(g, "search", x + l.sections - pagerW - ToolsLayout.PAGE_BUTTON + 7, y + ToolsLayout.TAB / 2f, 18, INK);
        if (page.pages() > 1) {
            String count = pagesSaid;
            float cw = LabText.width(count, TypeStyle.CAPTION.sp, 0), right = x + l.sections;
            icon(g, "next", right - ToolsLayout.PAGE_BUTTON + 7, y + ToolsLayout.TAB / 2f, 18, page.page() < page.pages() - 1 ? INK : GREY_400);
            words(g, count, false, TypeStyle.CAPTION.sp, right - ToolsLayout.PAGE_BUTTON - 4 - cw, y + (ToolsLayout.TAB - LabText.line(TypeStyle.CAPTION.sp)) / 2, GREY_800);
            icon(g, "prev", right - 2 * ToolsLayout.PAGE_BUTTON - 8 - cw + 7, y + ToolsLayout.TAB / 2f, 18, page.page() > 0 ? INK : GREY_400);
        }
        // The chips, a row while they fit (as LabUi.Wrap).
        float cx = x, cy = y + ToolsLayout.TAB;
        for (ToolsPage.Chip chip : page.chipsShown()) {
            int cw = ToolsLayout.chipWidth(chip.label, l.chipPad());
            if (cx > x && cx + cw > x + l.sections) { cx = x; cy += ToolsLayout.CHIP + l.chipGap(); }
            boolean on = chip == page.filter();
            rounded(g, cx, cy, cw, ToolsLayout.CHIP, 1, ToolsLayout.CHIP / 2f, INK, on ? INK : null);
            words(g, chip.label, false, TypeStyle.CAPTION.sp, cx + l.chipPad() + 1, cy + (ToolsLayout.CHIP - LabText.line(TypeStyle.CAPTION.sp)) / 2, on ? PAPER : INK);
            cx += cw + l.chipGap();
        }
        float gy = y + ToolsLayout.TAB + l.chips() + l.gap;
        List<ToolsPage.Card> cards = page.cards();
        for (int i = 0; i < cards.size(); i++) {
            int c = i % l.columns;
            if (c == 0 && i > 0) gy += l.cardHeight + ToolsLayout.ROW_GAP + (page.opens(cards, i) ? ToolsLayout.GROUP_GAP : 0);
            ToolsPage.Card card = cards.get(i);
            if (card == null) continue;
            card(g, card.brush, card.name, false, false, page.now(card) ? INK : null, x + c * l.cardWidth, gy, l.cardWidth, l.cardHeight, l.cardArt(), l.cardArtHeight(),
                    (w, h) -> WildSample.legibleTray(card.brush.parts, WildSample.PREVIEW, ModularSizes.sampleSize(card.brush.parts, h), w, h, true));
        }
    }

    // Words

    /** {@code text} at {@code sp} ({@code strong}: 700) from {@code x}, its line's top at {@code top}, dp; a character the face lacks in its fallback. */
    static void words(Graphics2D g, String text, boolean strong, float sp, float x, float top, Color color) {
        if (text.isEmpty()) return;
        Font f = (strong ? bold : regular).deriveFont(px(sp * LabText.SCALE) * 1f);
        g.setFont(f);
        java.awt.FontMetrics m = g.getFontMetrics();
        float baseline = px(top) + (px(LabText.line(sp)) + m.getAscent() - m.getDescent()) / 2f;
        g.setColor(color);
        g.drawString(attributed(text, f, strong, 0).getIterator(), px(x), baseline);
    }
    /** {@code text}'s width, dp, as {@link #words} draws it. */
    static float width(Graphics2D g, String text, boolean strong, float sp) {
        Font f = (strong ? bold : regular).deriveFont(px(sp * LabText.SCALE) * 1f);
        return new java.awt.font.TextLayout(attributed(text, f, strong, 0).getIterator(), g.getFontRenderContext()).getAdvance() / DP;
    }
    /** {@code text} cut with … to {@code room} dp. */
    static String cut(Graphics2D g, String text, boolean strong, float sp, float room) {
        if (width(g, text, strong, sp) <= room) return text;
        String t = text;
        while (t.length() > 1 && width(g, t + "…", strong, sp) > room) t = t.substring(0, t.length() - 1);
        return t.trim() + "…";
    }
    /** {@code text} in {@code f}, tracked {@code tracking} em, each character {@code f} lacks in the fallback face. */
    static AttributedString attributed(String text, Font f, boolean strong, float tracking) {
        AttributedString s = new AttributedString(text);
        s.addAttribute(TextAttribute.FONT, f);
        if (tracking != 0) s.addAttribute(TextAttribute.TRACKING, tracking);
        Font other = strong ? fallbackBold : fallback;
        if (other != null) for (int i = 0; i < text.length(); i++)
            if (!f.canDisplay(text.charAt(i))) s.addAttribute(TextAttribute.FONT, other.deriveFont(f.getSize2D()), i, i + 1);
        return s;
    }
    /** A label (14 sp, 700, caps, tracked), its middle at {@code middle} dp. */
    static void label(Graphics2D g, String text, float x, float middle, Color color) {
        Font f = bold.deriveFont(px(TypeStyle.LABEL.sp * LabText.SCALE) * 1f);
        g.setFont(f);
        g.setColor(color);
        java.awt.FontMetrics m = g.getFontMetrics();
        g.drawString(attributed(text.toUpperCase(Locale.ROOT), f, true, TypeStyle.LABEL.tracking).getIterator(), px(x), px(middle) + (m.getAscent() - m.getDescent()) / 2f);
    }

    // Shapes

    static void button(Graphics2D g, String text, boolean solid, boolean disabled, boolean icon, int x, int y, int w, int h, float rule) {
        button(g, text, solid, disabled, icon, x, y, w, h, rule, icon ? "plus" : null);
    }
    /** A {@link LabButton}: outlined {@code rule} dp (0: none), ink-filled when {@code solid}, grey-400 when {@code disabled}; an icon then its label. */
    static void button(Graphics2D g, String text, boolean solid, boolean disabled, boolean icon, int x, int y, int w, int h, float rule, String kind) {
        Color ink = disabled ? GREY_400 : INK, face = solid ? PAPER : ink;
        if (solid) { g.setColor(ink); g.fillRect(px(x), px(y), px(w), px(h)); }
        if (rule > 0) box(g, x, y, w, h, rule, ink);
        float iconSide = text == null ? 24 : 22, labelW = text == null ? 0 : LabLayout.labelWidth(text.toUpperCase(Locale.ROOT));
        float content = (icon ? iconSide + (text == null ? 0 : 8) : 0) + labelW, cx = x + (w - content) / 2f, cy = y + h / 2f;
        if (icon) { icon(g, kind, cx, cy, iconSide, face); cx += iconSide + (text == null ? 0 : 8); }
        if (text != null) label(g, text, cx, cy, face);
    }
    /** The icons, plainly: an arrow, carets, Undo's turn, a plus, a flask. */
    static void icon(Graphics2D g, String kind, float x, float middle, float side, Color color) {
        g.setColor(color);
        g.setStroke(new BasicStroke(px(2.4), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        float s = side, l = x + s * .15f, r = x + s * .85f, t = middle - s * .35f, b = middle + s * .35f;
        switch (kind) {
            case "arrow":
                g.drawLine(px(l), px(middle), px(r), px(middle));
                g.drawLine(px(l), px(middle), px(l + s * .3f), px(middle - s * .3f));
                g.drawLine(px(l), px(middle), px(l + s * .3f), px(middle + s * .3f));
                break;
            case "prev": case "next": {
                float tip = kind.equals("prev") ? x + s * .35f : x + s * .65f, back = kind.equals("prev") ? x + s * .62f : x + s * .38f;
                g.drawLine(px(back), px(middle - s * .3f), px(tip), px(middle));
                g.drawLine(px(tip), px(middle), px(back), px(middle + s * .3f));
                break;
            }
            case "search":
                g.drawOval(px(l), px(t), px(s * .5f), px(s * .5f));
                g.drawLine(px(l + s * .43f), px(t + s * .43f), px(r), px(b));
                break;
            case "undo":
                g.drawArc(px(l + s * .1f), px(t), px(s * .6f), px(s * .7f), -90, 180);
                g.drawLine(px(l), px(t), px(l + s * .4f), px(t));
                g.drawLine(px(l), px(t), px(l + s * .2f), px(t - s * .15f));
                g.drawLine(px(l), px(t), px(l + s * .2f), px(t + s * .15f));
                break;
            case "flask":
                g.drawLine(px(x + s * .38f), px(t), px(x + s * .38f), px(middle - s * .05f));
                g.drawLine(px(x + s * .62f), px(t), px(x + s * .62f), px(middle - s * .05f));
                g.drawLine(px(x + s * .38f), px(middle - s * .05f), px(l), px(b));
                g.drawLine(px(x + s * .62f), px(middle - s * .05f), px(r), px(b));
                g.drawLine(px(l), px(b), px(r), px(b));
                break;
            default:
                g.drawLine(px(x + s / 2), px(t), px(x + s / 2), px(b));
                g.drawLine(px(l), px(middle), px(r), px(middle));
        }
    }
    /** An outline {@code rule} dp thick just inside {@code x, y, w, h}. */
    static void box(Graphics2D g, int x, int y, int w, int h, float rule, Color color) {
        g.setColor(color);
        int X = px(x), Y = px(y), W = px(w), H = px(h), R = px(rule);
        g.fillRect(X, Y, W, R); g.fillRect(X, Y + H - R, W, R); g.fillRect(X, Y, R, H); g.fillRect(X + W - R, Y, R, H);
    }
    /** A rounded box at {@code x, y, w × h} dp, {@code radius} dp corners, filled in {@code fill} (null: not), its {@code rule} dp edge in {@code edge}. */
    static void rounded(Graphics2D g, float x, float y, float w, float h, float rule, float radius, Color edge, Color fill) {
        float r = px(rule);
        RoundRectangle2D.Float shape = new RoundRectangle2D.Float(px(x) + r / 2, px(y) + r / 2, px(w) - r, px(h) - r, 2 * px(radius), 2 * px(radius));
        if (fill != null) { g.setColor(fill); g.fill(shape); }
        g.setColor(edge);
        g.setStroke(new BasicStroke(r));
        g.draw(shape);
    }
    /** A sample's pixels (greys) at {@code x, y} px. */
    static void sample(Graphics2D g, int[] argb, int w, int h, int x, int y) {
        BufferedImage s = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        s.setRGB(0, 0, w, h, argb, 0, w);
        g.drawImage(s, x, y, null);
    }

    // The owner's drawings

    /** The owner's drawing {@code name}'s own size, px: a traced vector's (res/drawable) or a PNG's (res/drawable-nodpi). */
    static double[] artSize(String name) throws java.io.IOException {
        File vector = new File("app/src/main/res/drawable/" + name + ".xml");
        if (vector.isFile()) { String xml = new String(java.nio.file.Files.readAllBytes(vector.toPath())); return new double[]{number(xml, "width"), number(xml, "height")}; }
        BufferedImage png = ImageIO.read(new File("app/src/main/res/drawable-nodpi/" + name + ".png"));
        return new double[]{png.getWidth(), png.getHeight()};
    }
    /**
     * The owner's drawing {@code name} drawn whole into {@code left, top, w × h} px of {@code g} (which clips it): a traced
     * vector filled from its path, a {@code <group>}'s scale about its pivot as Android applies it; else a PNG.
     */
    static void art(Graphics2D g, String name, double left, double top, double w, double h) throws java.io.IOException {
        File vector = new File("app/src/main/res/drawable/" + name + ".xml");
        if (!vector.isFile()) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(ImageIO.read(new File("app/src/main/res/drawable-nodpi/" + name + ".png")), (int) Math.round(left), (int) Math.round(top), (int) Math.round(w), (int) Math.round(h), null);
            return;
        }
        String xml = new String(java.nio.file.Files.readAllBytes(vector.toPath()));
        java.awt.geom.AffineTransform place = java.awt.geom.AffineTransform.getTranslateInstance(left, top);
        place.scale(w / number(xml, "viewportWidth"), h / number(xml, "viewportHeight"));
        if (xml.contains("android:scaleX")) {
            double pivotX = number(xml, "pivotX"), pivotY = number(xml, "pivotY");
            place.translate(pivotX, pivotY); place.scale(number(xml, "scaleX"), number(xml, "scaleY")); place.translate(-pivotX, -pivotY);
        }
        java.util.regex.Matcher data = java.util.regex.Pattern.compile("android:pathData=\"([^\"]*)\"").matcher(xml);
        if (!data.find()) throw new java.io.IOException(name + ": no path");
        g.setColor(INK);
        g.fill(place.createTransformedShape(path(data.group(1))));
    }
    static double number(String xml, String attribute) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("android:" + attribute + "=\"([\\d.]+)").matcher(xml);
        if (!m.find()) throw new IllegalArgumentException(attribute);
        return Double.parseDouble(m.group(1));
    }
    /** A potrace path (M, m, l, c, z; relative after a move, a command's numbers repeating it), filled as Android fills it (non-zero). */
    static java.awt.geom.Path2D path(String data) {
        java.awt.geom.Path2D.Double p = new java.awt.geom.Path2D.Double(java.awt.geom.Path2D.WIND_NON_ZERO);
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("[MmLlCcZz]|-?[\\d.]+").matcher(data);
        java.util.List<String> tokens = new java.util.ArrayList<>();
        while (m.find()) tokens.add(m.group());
        char command = 'M';
        double cx = 0, cy = 0;
        for (int i = 0; i < tokens.size(); ) {
            String token = tokens.get(i);
            if (Character.isLetter(token.charAt(0))) {
                command = token.charAt(0); i++;
                if (command == 'z' || command == 'Z') { p.closePath(); cx = p.getCurrentPoint().getX(); cy = p.getCurrentPoint().getY(); }
                continue;
            }
            boolean relative = Character.isLowerCase(command);
            double[] v = new double[command == 'c' || command == 'C' ? 6 : 2];
            for (int k = 0; k < v.length; k++) v[k] = Double.parseDouble(tokens.get(i++)) + (relative ? k % 2 == 0 ? cx : cy : 0);
            switch (Character.toLowerCase(command)) {
                case 'm': p.moveTo(v[0], v[1]); command = relative ? 'l' : 'L'; break;
                case 'l': p.lineTo(v[0], v[1]); break;
                default: p.curveTo(v[0], v[1], v[2], v[3], v[4], v[5]);
            }
            cx = v[v.length - 2]; cy = v[v.length - 1];
        }
        return p;
    }
}
