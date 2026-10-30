package io.github.mpdairy.monopaint;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * Pictures of the drawing screen's chrome and the inside of a notebook on the desktop (scripts/bar_pictures.sh): the
 * side column as {@link InkWashBar} lays it out from {@link DrawLayout} (its eight tray rows the owner's drawings of
 * Basic tools, {@link DrawLayout#drawing}, or the engine's own tray strokes, as {@code WildSample.slotTray} draws them), the page bar along the page's foot
 * ({@link PageTurner}), and the notebook screen from {@link NotebookLayout}: its one bar, the page, the strip and the
 * same page bar. Like {@link ToolsPagePictures}, whose drawing it shares, a stand-in for the device's views: the icons
 * are drawn plainly, the page is blank, at 1.875 px a dp (both tablets). The column on the page's right, as for the
 * right hand; one picture a screen.
 */
public final class DrawScreenPictures {
    static final Color INK = ToolsPagePictures.INK, PAPER = ToolsPagePictures.PAPER, GREY_400 = ToolsPagePictures.GREY_400, GREY_600 = ToolsPagePictures.GREY_600, GREY_800 = ToolsPagePictures.GREY_800;

    public static void main(String[] args) throws Exception {
        Assets.install(Assets.folder(Assets.FOLDER));
        File out = new File(args.length > 0 ? args[0] : "/tmp/bar-pictures");
        out.mkdirs();
        ToolsPagePictures.regular = Font.createFont(Font.TRUETYPE_FONT, new File("app/src/main/res/font/ia_writer_duo_regular.ttf"));
        ToolsPagePictures.bold = Font.createFont(Font.TRUETYPE_FONT, new File("app/src/main/res/font/ia_writer_duo_bold.ttf"));
        String[] names = {"manta-portrait", "manta-landscape", "nomad-portrait", "nomad-landscape"};
        for (int i = 0; i < DrawLayoutChecks.SCREENS.length; i++) {
            int[] s = DrawLayoutChecks.SCREENS[i];
            File drawing = new File(out, "drawing-" + names[i] + ".png"), notebook = new File(out, "notebook-" + names[i] + ".png");
            drawing(drawing, s[0], s[1], PageStacks.MOST);
            notebook(notebook, s[0], s[1]);
            System.out.println(drawing);
            System.out.println(notebook);
        }
    }

    static int px(double dp) { return ToolsPagePictures.px(dp); }
    static BufferedImage image(int width, int height) {
        BufferedImage image = new BufferedImage(px(width), px(height), BufferedImage.TYPE_BYTE_GRAY);
        return image;
    }
    static Graphics2D graphics(BufferedImage image) {
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(PAPER); g.fillRect(0, 0, image.getWidth(), image.getHeight());
        return g;
    }
    static void rule(Graphics2D g, float x, float y, float w, float h, Color c) { g.setColor(c); g.fillRect(px(x), px(y), Math.max(1, px(w)), Math.max(1, px(h))); }

    /**
     * The drawing screen: the column at the page's left (the right hand's), the page bar along the page's top, Zen
     * Mode's circle in the foot corner under the column and the brush line along the foot from just past it (2026-10-23).
     */
    static void drawing(File file, int width, int height, int layers) throws Exception {
        DrawLayout d = new DrawLayout(width, height);
        Map<String, String> prefs = new HashMap<>();
        BrushLibrary.Store store = new BrushLibrary.Store() {
            public String get(String key) { return prefs.get(key); }
            public void put(String key, String value) { if (value == null) prefs.remove(key); else prefs.put(key, value); }
        };
        ToolSlots slots = new ToolSlots(store, new Random(1));
        // The owner's mockup's tray (2026-10-26): Basic tools showing their drawings, Mold ink (not drawn) its stroke.
        String[] tray = {"Fountain pen", "Split nib", "Springy brush", "Swell brush", "Ballpoint", "HB pencil", "Mold ink", "Dot tone"};
        for (int t = 0; t < tray.length; t++) slots.use(t, ToolKits.preset(tray[t]).brush());
        BufferedImage image = image(width, height);
        Graphics2D g = graphics(image);
        // The column's one grid: its inner margin each side, two equal cells meeting on its axis.
        int x = d.edge, inner = d.inner, half = d.half();
        float left = x, mid = x + (inner - half) / 2f;
        float y = DrawLayout.PAD;
        for (DrawLayout.Part part : DrawLayout.COLUMN) {
            switch (part) {
                case TIMER: y += DrawLayout.TIMER; break;
                // Menu alone, on the axis (layout v3, 2026-10-26).
                case MENU: icon(g, "menu", mid, y, half, d.menuRow()); y += d.menuRow(); break;
                // The Dropper and the Lasso, the Trash in the left cell.
                case BLOCK:
                    icon(g, "eyedropper", left, y, half, d.icon); icon(g, "lasso", left + half, y, half, d.icon); y += d.icon;
                    icon(g, "trash", left, y, half, d.icon); y += d.icon;
                    break;
                case GAP: y += d.gap; break;
                case TRAY:
                    // The tray's rows from the screen's edge (2026-10-27), over the column's margin.
                    for (int t = 0; t < ToolSlots.COUNT; t++) { tray(g, slots, t, 0, Math.round(y), inner + d.edge, d.slot, t == 0); y += d.slot; }
                    break;
                case FACES: {
                    // The readout on top (the owner's sketch, 2026-10-24): one dot on the axis, the size, in the grey.
                    ToolSwatches.Look look = slots.swatches.look(ToolSlots.key(0), slots.brush(0));
                    float rh = DrawLayout.READOUT_ROWS * d.face, dot = Math.min((float) look.px / 2, Math.min(inner, rh) - DrawLayout.FACE_GAP);
                    int grey = 255 - (int) Math.round(255 * look.darkness);
                    java.awt.geom.Ellipse2D.Float disc = new java.awt.geom.Ellipse2D.Float(px(x + inner / 2f - dot / 2), px(y + rh / 2 - dot / 2), px(dot), px(dot));
                    g.setColor(new Color(grey, grey, grey)); g.fill(disc);
                    if (grey > 235) { g.setColor(INK); g.draw(disc); }  // SwatchFace.PAPERY (an Android view)
                    // On plain paper, no frame (the owner, 2026-10-27).
                    float top = y + rh;
                    int side = d.faceSide();
                    float tall = d.box;
                    // The picker box (the owner's drawing, 2026-10-27), two squares and the gap wide across the axis, the
                    // strip's length: its lower-left triangle the grey strip, white at the top to black at the foot, its tab
                    // left of the box at the grey in use; its upper-right one the size slider, ink, its tab right of the box.
                    float sl = x + half - DrawLayout.FACE_GAP / 2f - side, right = x + half + DrawLayout.FACE_GAP / 2f + side, w = right - sl;
                    float st = top + DrawLayout.trackTop(), len = DrawLayout.trackFoot(tall) - DrawLayout.trackTop();
                    Graphics2D bar = (Graphics2D) g.create(); bar.clip(shape("shape_box_shade", sl, st, w, len));
                    for (int k = 0; k < 64; k++) { int v = 255 - k * 255 / 63; rule(bar, sl, st + k * len / 64f, w, len / 64f + .6f, new Color(v, v, v)); }
                    bar.dispose();
                    g.setColor(INK); g.fill(shape("shape_box_size", sl, st, w, len));
                    double t = ToolSwatches.position(look.px);
                    rule(g, right + DrawLayout.FACE_MARK_GAP, top + DrawLayout.sliderY(t, tall) - DrawLayout.STRIP_MARK / 2f, DrawLayout.FACE_MARK, DrawLayout.STRIP_MARK, INK);
                    rule(g, sl - DrawLayout.FACE_MARK_GAP - DrawLayout.FACE_MARK, st + len * .7f - DrawLayout.STRIP_MARK / 2f, DrawLayout.FACE_MARK, DrawLayout.STRIP_MARK, INK);
                    y += tall + rh;
                    break;
                }
                // The wet indicator, dry, and the sand timer (here a flask); Template Visibility and Show Kinds, two eyes.
                case PLAY:
                    icon(g, "drop", left, y, half, d.wet); icon(g, "flask", left + half, y, half, d.wet); y += d.wet;
                    icon(g, "eye", left, y, half, d.wet); icon(g, "eye", left + half, y, half, d.wet); y += d.wet;
                    break;
                case TOOLS:
                    // The smudge tool and the eraser, a tray row each, the owner's drawings against the screen's edge.
                    for (String drawn : new String[]{"tool_smudge", "tool_eraser"}) { drawing(g, drawn, 0, Math.round(y), inner + d.edge, d.slot, 0); y += d.slot; }
                    break;
                case STRETCH: y += d.spare; break;
                default: break;
            }
        }
        // The brush line along the foot, from just past Zen Mode's cell.
        int line = height - d.footHeight(), start = d.lineStart() + DrawLayout.LINE_PAD;
        g.setFont(ToolsPagePictures.regular.deriveFont(px(TypeStyle.CAPTION.sp * LabText.SCALE) * 1f)); g.setColor(INK);
        // The slot's words, a Basic tool's description after its name (the owner, 2026-10-26).
        Brush first = slots.brush(0);
        ToolPresets.Preset preset = ToolPresets.find(first);
        // Cut with … at its fixed room, as the line is.
        String words = LabText.oneLine(DrawLayout.brushWords(first.name(), preset == null ? null : preset.line, ModularWords.caption(first.parts)),
                DrawLayout.LINE_NAME, LabText.face(TypeStyle.CAPTION.sp, 0));
        // No stroke preview before it (the owner, 2026-10-27).
        g.drawString(words, px(start), px(line + DrawLayout.BRUSH_LINE / 2f) + g.getFontMetrics().getAscent() / 2f);
        // Zen Mode's circle in the foot corner under the column, Menu's width and icon, unfilled while off.
        int[] zen = DrawLayout.zenCell(width, height, d.side, half, d.zenHeight(), false);
        icon(g, "circle", zen[0], zen[1], half, d.zenHeight());
        // The page bar over the page.
        Graphics2D bar = (Graphics2D) g.create(); bar.translate(px(d.side), 0);
        pageBar(bar, d.pageWidth(), 0, d.pageButton(), d.pageBar(), true, 2, 24);
        bar.dispose();
        g.dispose();
        ImageIO.write(image, "png", file);
    }
    /** Slot {@code t}'s tray row: its brush's picture ({@link ToolSwatches#picture}); the selected one's side bar at its right, the row's height less its inset. */
    static void tray(Graphics2D g, ToolSlots slots, int t, int x, int y, int inner, int h, boolean selected) throws java.io.IOException {
        Brush brush = slots.brush(t);
        if (selected) rule(g, x + inner - DrawLayout.SIDE_BAR, y + DrawLayout.BAR_INSET, DrawLayout.SIDE_BAR, h - 2 * DrawLayout.BAR_INSET, INK);
        // A Basic tool the owner drew: its picker drawing, its tip out of the row's edge.
        String drawn = DrawLayout.drawing(brush);
        if (drawn != null) { drawing(g, drawn, x, y, inner, h, DrawLayout.TIP_TRIM); return; }
        ToolSwatches.Look look = ToolSwatches.picture(brush);
        int margin = 0, w = inner - margin - DrawLayout.barRoom();
        int sw = px(w), sh = px(h - 2 * DrawLayout.TRAY_TOP);
        int[] stroke = WildSample.slotTray(brush.parts, look, sw, sh);
        ToolsPagePictures.sample(g, stroke, sw, sh, px(x + margin), px(y + DrawLayout.TRAY_TOP));
    }
    /**
     * The owner's drawing {@code name} in a tray row's stroke place, never enlarged, as InkWashBar.drawing: flush left
     * against the row's edge, whole or with {@code trim} of its width cut at its left. A traced vector (res/drawable, in the
     * Manta's pixels) filled from its path; else a PNG (res/drawable-nodpi, the Manta's pixels).
     */
    static void drawing(Graphics2D g, String name, int x, int y, int inner, int h, float trim) throws java.io.IOException {
        double[] size = ToolsPagePictures.artSize(name);
        double aw = size[0], ah = size[1];
        float margin = 0, w = inner - margin - DrawLayout.barRoom(), t = h - 2 * DrawLayout.TRAY_TOP;
        double cut = aw * trim, scale = Math.min(1, Math.min(px(w) / (aw - cut), px(t) / ah));
        Graphics2D clip = (Graphics2D) g.create(px(x + margin), px(y + DrawLayout.TRAY_TOP), px(w), px(t));
        ToolsPagePictures.art(clip, name, -cut * scale, (px(t) - ah * scale) / 2, aw * scale, ah * scale);
        clip.dispose();
    }
    /** The owner's drawn shape {@code name} (a traced vector) stretched to the box {@code x, y, w × h} dp, as Theme.shape. */
    static java.awt.Shape shape(String name, float x, float y, float w, float h) throws java.io.IOException {
        String xml = new String(java.nio.file.Files.readAllBytes(new File("app/src/main/res/drawable/" + name + ".xml").toPath()));
        java.util.regex.Matcher data = java.util.regex.Pattern.compile("android:pathData=\"([^\"]*)\"").matcher(xml);
        if (!data.find()) throw new java.io.IOException(name + ": no path");
        java.awt.geom.AffineTransform place = java.awt.geom.AffineTransform.getTranslateInstance(x * ToolsPagePictures.DP, y * ToolsPagePictures.DP);
        place.scale(w * ToolsPagePictures.DP / ToolsPagePictures.number(xml, "viewportWidth"), h * ToolsPagePictures.DP / ToolsPagePictures.number(xml, "viewportHeight"));
        java.awt.geom.Path2D shape = new java.awt.geom.Path2D.Double(place.createTransformedShape(ToolsPagePictures.path(data.group(1))));
        shape.setWindingRule(xml.contains("evenOdd") ? java.awt.geom.Path2D.WIND_EVEN_ODD : java.awt.geom.Path2D.WIND_NON_ZERO);
        return shape;
    }
    /**
     * The page bar from {@code top}, a page {@code pageWidth} dp wide: no rule (since 2026-10-20), ‹ "n / N" › in the middle, and,
     * {@code notebook}, the Notebook button at the end away from the column (its left: the column is on the right).
     */
    static void pageBar(Graphics2D g, int pageWidth, int top, int button, int bar, boolean notebook, int index, int count) throws java.io.IOException {
        float y = top + DrawLayout.PAGE_RULE, h = bar - DrawLayout.PAGE_RULE;
        int number = DrawLayout.pageNumber(), left = (pageWidth - 2 * button - number) / 2;
        icon(g, "prev", left, y, button, h);
        // "3 / 24" with the owner's drawn slash, a digit's height, for the "/" (PageTurner.Number).
        String words = DrawLayout.pageWords(index, count);
        int cut = words.indexOf('/');
        String before = words.substring(0, cut), after = words.substring(cut + 1);
        java.awt.Font font = ToolsPagePictures.bold.deriveFont(px(TypeStyle.LABEL.sp * LabText.SCALE) * 1f);
        java.awt.geom.Rectangle2D digit = font.createGlyphVector(g.getFontRenderContext(), "0").getVisualBounds();
        float tall = (float) digit.getHeight() / ToolsPagePictures.DP, wide = tall * 72f / 142;
        float x = left + button + (number - LabLayout.labelWidth(before) - wide - LabLayout.labelWidth(after)) / 2f;
        ToolsPagePictures.label(g, before, x, y + h / 2f, INK);
        // label()'s baseline, in dp.
        float baseline = y + h / 2f + (g.getFontMetrics().getAscent() - g.getFontMetrics().getDescent()) / 2f / ToolsPagePictures.DP;
        g.setColor(INK); g.fill(shape("shape_page_slash", x + LabLayout.labelWidth(before), baseline - tall, wide, tall));
        ToolsPagePictures.label(g, after, x + LabLayout.labelWidth(before) + wide, y + h / 2f, INK);
        icon(g, "next", left + button + number, y, button, h);
        if (notebook) icon(g, "notebook", 0, y, button, h);
    }

    /** The inside of a notebook: its one bar, the page as big as fits, the strip, the page bar along the foot. */
    static void notebook(File file, int width, int height) throws Exception {
        NotebookLayout l = new NotebookLayout(width, height, NotebookLayout.aspectOf(new int[]{width, height}));
        BufferedImage image = image(width, height);
        Graphics2D g = graphics(image);
        int x = l.margin, y = l.margin, across = width - 2 * l.margin;
        float by = y + (NotebookLayout.BAR - NotebookLayout.BUTTON) / 2f;
        // ‹ Notebooks, the name (bold, two lines at most), Draw (ink), Use on Cover, Delete Page.
        icon(g, "prev", x + 12 - 1, by, 24, NotebookLayout.BUTTON);
        ToolsPagePictures.label(g, NotebookLayout.BACK, x + 12 + 22 + 8, by + NotebookLayout.BUTTON / 2f, INK);
        int tx = x + NotebookLayout.backWidth() + NotebookLayout.BAR_GAP;
        String name = "Harbour sketches from the ferry, foggy";
        java.util.List<String> lines = LabText.wrap(name, l.titleRoom, LabText.face(TypeStyle.BODY.sp, 0));
        float line = LabText.line(TypeStyle.BODY.sp), ty = y + (NotebookLayout.BAR - Math.min(2, lines.size()) * line) / 2f;
        for (int i = 0; i < Math.min(NotebookLayout.TITLE_LINES, lines.size()); i++) { text(g, lines.get(i), tx, ty + i * line, TypeStyle.BODY.sp); }
        int ax = tx + l.titleRoom + NotebookLayout.BAR_GAP, draw = NotebookLayout.actionWidth(NotebookLayout.OPEN, true);
        ToolsPagePictures.button(g, NotebookLayout.OPEN, true, false, false, ax, Math.round(by), draw, NotebookLayout.BUTTON, 0);
        ax += draw + NotebookLayout.BAR_GAP;
        int cover = NotebookLayout.coverWidth(l.labelled), delete = NotebookLayout.actionWidth(NotebookLayout.DELETE, l.labelled);
        if (l.labelled) {
            ToolsPagePictures.button(g, NotebookCovers.USE_BUTTON, false, false, false, ax, Math.round(by), cover, NotebookLayout.BUTTON, 2);
            ToolsPagePictures.button(g, NotebookLayout.DELETE, false, false, false, ax + cover + NotebookLayout.BAR_GAP, Math.round(by), delete, NotebookLayout.BUTTON, 2);
        } else {
            ToolsPagePictures.box(g, ax, Math.round(by), cover, NotebookLayout.BUTTON, 2, INK);
            icon(g, "cover", ax, by, cover, NotebookLayout.BUTTON);
            ToolsPagePictures.box(g, ax + cover + NotebookLayout.BAR_GAP, Math.round(by), delete, NotebookLayout.BUTTON, 2, INK);
            icon(g, "trash", ax + cover + NotebookLayout.BAR_GAP, by, delete, NotebookLayout.BUTTON);
        }
        y += NotebookLayout.BAR + l.gap;
        // The page, centred in its room, a 2 dp ink outline.
        int px0 = x + (across - l.pageWidth) / 2, py0 = y + (l.spreadHeight - l.pageHeight) / 2;
        ToolsPagePictures.box(g, px0, py0, l.pageWidth, l.pageHeight, 2, INK);
        y += l.spreadHeight + l.gap;
        // The strip: the front cover, pages (the third framed, its tab under it), + at the end.
        for (int k = 0; k < l.visible; k++) {
            int sx = x + k * (l.thumbWidth + l.stripGap);
            boolean current = k == 3;
            if (k == 0) rule(g, sx, y, l.thumbWidth, l.thumbHeight, GREY_400);
            ToolsPagePictures.box(g, sx, y, l.thumbWidth, l.thumbHeight, current ? 3 : 2, current ? INK : GREY_400);
            if (current) rule(g, sx, y + l.thumbHeight + NotebookLayout.TAB_GAP, l.thumbWidth, NotebookLayout.TAB, INK);
        }
        pageBar(g, width, height - l.pageBar, l.pageButton, l.pageBar, false, 2, 24);
        g.dispose();
        ImageIO.write(image, "png", file);
    }
    static void text(Graphics2D g, String s, float x, float top, float sp) {
        g.setFont(ToolsPagePictures.bold.deriveFont(px(sp * LabText.SCALE) * 1f));
        g.setColor(INK);
        g.drawString(s, px(x), px(top) + g.getFontMetrics().getAscent());
    }
    static void caption(Graphics2D g, String s, float centre, float top, boolean bold) {
        g.setFont((bold ? ToolsPagePictures.bold : ToolsPagePictures.regular).deriveFont(px(TypeStyle.CAPTION.sp * LabText.SCALE) * 1f));
        g.setColor(INK);
        java.awt.FontMetrics m = g.getFontMetrics();
        g.drawString(s, px(centre) - m.stringWidth(s) / 2f, px(top) + m.getAscent() / 2f);
    }

    static void icon(Graphics2D g, String kind, float x, float y, float w, float h) { icon(g, kind, x, y, w, h, INK); }
    /** An icon, 24 dp, plainly, in the middle of the box {@code x, y, w, h} dp. */
    static void icon(Graphics2D g, String kind, float bx, float by, float bw, float bh, Color color) {
        float s = 24, x = bx + (bw - s) / 2, m = by + bh / 2, l = x + s * .15f, r = x + s * .85f, t = m - s * .35f, b = m + s * .35f;
        switch (kind) {
            case "prev": case "next": case "flask": case "undo":
                ToolsPagePictures.icon(g, kind, x, m, s, color); return;
            default: break;
        }
        g.setColor(color);
        g.setStroke(new BasicStroke(px(2.4), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        switch (kind) {
            case "circle": g.drawOval(px(x + s * .1f), px(m - s * .4f), px(s * .8f), px(s * .8f)); break;
            case "dashed": for (int i = 0; i < 8; i++) g.drawArc(px(x + s * .1f), px(m - s * .4f), px(s * .8f), px(s * .8f), i * 45, 25); break;
            case "stack": for (int i = 0; i < 3; i++) { float k = m - s * .2f + i * s * .2f; g.drawLine(px(l), px(k), px(x + s * .5f), px(k - s * .12f)); g.drawLine(px(x + s * .5f), px(k - s * .12f), px(r), px(k)); } break;
            case "drop": g.drawOval(px(x + s * .3f), px(m - s * .1f), px(s * .4f), px(s * .4f)); g.drawLine(px(x + s * .32f), px(m), px(x + s * .5f), px(t)); g.drawLine(px(x + s * .68f), px(m), px(x + s * .5f), px(t)); break;
            case "stamp": g.drawRect(px(x + s * .2f), px(m + s * .1f), px(s * .6f), px(s * .2f)); g.drawLine(px(x + s * .5f), px(t), px(x + s * .5f), px(m + s * .1f)); break;
            case "menu": for (int i = -1; i <= 1; i++) g.drawLine(px(l), px(m + i * s * .28f), px(r), px(m + i * s * .28f)); break;
            case "redo": {
                g.drawArc(px(x + s * .3f), px(t), px(s * .6f), px(s * .7f), 90, 180);
                g.drawLine(px(r), px(t), px(r - s * .4f), px(t));
                g.drawLine(px(r), px(t), px(r - s * .2f), px(t - s * .15f));
                g.drawLine(px(r), px(t), px(r - s * .2f), px(t + s * .15f));
                break;
            }
            case "lock": g.drawRect(px(x + s * .3f), px(m - s * .05f), px(s * .4f), px(s * .3f)); g.drawArc(px(x + s * .36f), px(m - s * .3f), px(s * .28f), px(s * .5f), 0, 180); break;
            case "notebook": g.drawRect(px(x + s * .2f), px(t), px(s * .6f), px(s * .7f)); g.drawLine(px(x + s * .35f), px(t), px(x + s * .35f), px(b)); break;
            case "stump": g.drawLine(px(l), px(b), px(r), px(t)); g.drawLine(px(l + s * .1f), px(b), px(l), px(b - s * .1f)); break;
            case "eyedropper": g.drawLine(px(l), px(b), px(x + s * .65f), px(t + s * .35f)); g.drawOval(px(x + s * .6f), px(t), px(s * .3f), px(s * .3f)); break;
            case "bucket": g.drawRect(px(x + s * .25f), px(m - s * .15f), px(s * .45f), px(s * .45f)); g.drawLine(px(x + s * .75f), px(m), px(r), px(b)); break;
            case "eraser": g.drawRect(px(x + s * .2f), px(m - s * .2f), px(s * .6f), px(s * .4f)); g.drawLine(px(x + s * .45f), px(m - s * .2f), px(x + s * .45f), px(m + s * .2f)); break;
            case "hand": g.drawLine(px(x + s * .3f), px(b), px(x + s * .3f), px(t)); g.drawLine(px(x + s * .3f), px(m), px(r), px(m)); g.drawLine(px(r), px(m), px(r), px(b)); break;
            case "eye": g.drawOval(px(l), px(m - s * .22f), px(r - l), px(s * .44f)); g.drawOval(px(x + s * .4f), px(m - s * .1f), px(s * .2f), px(s * .2f)); break;
            case "magnet": g.drawArc(px(l), px(t), px(r - l), px(b - t), 180, 180); g.drawLine(px(l), px(t), px(l), px(m)); g.drawLine(px(r), px(t), px(r), px(m)); break;
            case "mirror": g.drawLine(px(x + s * .5f), px(t), px(x + s * .5f), px(b)); g.drawLine(px(l), px(m), px(x + s * .4f), px(t)); g.drawLine(px(l), px(m), px(x + s * .4f), px(b)); break;
            case "trash": g.drawRect(px(x + s * .28f), px(t + s * .15f), px(s * .44f), px(s * .55f)); g.drawLine(px(l), px(t + s * .15f), px(r), px(t + s * .15f)); break;
            case "cover": g.drawRect(px(x + s * .18f), px(t), px(s * .64f), px(s * .7f)); g.drawLine(px(x + s * .3f), px(t), px(x + s * .3f), px(b));
                g.drawLine(px(x + s * .42f), px(m + s * .2f), px(x + s * .56f), px(m)); g.drawLine(px(x + s * .56f), px(m), px(x + s * .72f), px(m + s * .2f)); break;
            default: g.drawLine(px(l), px(m), px(r), px(m));
        }
    }
}
