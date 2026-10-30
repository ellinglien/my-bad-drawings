package io.github.mpdairy.monopaint;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;

/**
 * A toolbar slot's tile, the one look of Brush Lab's TOOLBAR row and the Tools page's ten slots (the owner's Inkwash
 * Design Style): a square tile with no outline, the slot's stroke ({@link WildSample#slotTray}) at its size, filling
 * it but for a margin ({@link #inner}); when the loop is drawn narrower than the slot draws, the size in mm at its
 * bottom right. No number (the owner, 2026-10-19: "no need to have numbers by the brushes"): a slot is where it is and
 * what it holds. Tiles sit butted in a row, parted only by their margins. The selected one has a thick ink underline
 * ({@link DrawLayout#MARK}), its loop drawn as it is (the owner, 2026-10-23: "some brushes are inverted, so in addition to the
 * inverted look of the brush we need to determine another way to indicate that it is active... underline?"; a black
 * fill until then). Drawn by {@link #draw}, for a view (the lab's {@link LoopCard}) or as a
 * {@link Face} (the bar's {@link ToolButton}).
 * <p>
 * On the drawing screen's bar a slot is a <b>row</b> instead ({@link #row}, the owner's "B Pen tray", 2026-10-19): a
 * short row, its brush's tray stroke ({@link WildSample#slotTray}) lying across the row's width (the number's column went to
 * the stroke, 2026-10-19), and the selected one marked by an ink bar down its side ({@link DrawLayout#SIDE_BAR}, the
 * owner, 2026-10-23: "it should be a side line, not an underline. too hard to differentiate a solid line from a
 * stroke"), on its page side, a little shorter than the row ({@link DrawLayout#BAR_INSET}), {@link DrawLayout#BAR_GAP}
 * clear of the stroke, the stroke against the screen's edge on the other side ({@link #row}); no size in mm (a broad
 * stroke is drawn as broad as the row holds; the size face and its modal say the size). The tool buttons under the
 * sizes and greys (the smudge tool, the eraser, the stamp tool, Magnetic Poetry, Mirrors) are drawn the same way, an
 * icon in place of the stroke, the one in use or on marked by the same bar, on the column's edge side of its cell, so a
 * cell's bar never lies between two icons (the tool in use is one choice, so one mark).
 * <p>
 * A slot whose brush runs out of paint (Smudge's Paint Load, the oil paint spec) shows how much it holds for its next
 * stroke in a small upright gauge at its row's right end ({@link #gauge}): a {@link Theme#FINE} dp ink outline, inked from
 * the bottom by the share it holds, which drains as it runs out and fills when a grey is rubbed.
 * <p>
 * A slot whose dirty brush holds paint (Smudge's Keeps Paint) shows it as a small square dab of that paint's grey at its
 * row's right end ({@link #dab}), in a fine ink edge, until Clean Brush.
 */
final class SlotTile {
    private SlotTile() { }

    /**
     * dp: the margin at a tile's sides; over it, so a column's tab stays clear of the next tile, as a face's ({@link
     * DrawLayout#FACE_PAD}); under the loop, the gap over the selected one's tab.
     */
    static final float MARGIN = Theme.BASE, TOP = DrawLayout.FACE_PAD, TAB_GAP = 2;

    /** Where a tile's loop or icon goes, {@code width × height} pixels: inside its margins, the tab's room under it. */
    static Rect inner(Context context, int width, int height) {
        int margin = Ui.dp(context, MARGIN), under = Ui.dp(context, TAB_GAP + SwatchFace.TAB);
        return new Rect(margin, Ui.dp(context, TOP), width - margin, height - under);
    }
    /**
     * Where a row's stroke or icon goes, {@code width × height} pixels: {@link DrawLayout#TRAY_TOP} over and under it
     * ({@link DrawLayout#trayStroke}), the side bar's room ({@link DrawLayout#barRoom}) on its bar's side, at its left
     * when {@code barLeft}, and its margin on the other, none when {@code flush} (a stroke's or a drawing's row, against
     * the screen's edge: the owner, 2026-10-27, "move tools slightly left, touching edge").
     */
    static Rect row(Context context, int width, int height, boolean barLeft, boolean flush) {
        int margin = flush ? 0 : Ui.dp(context, MARGIN), pad = Ui.dp(context, DrawLayout.TRAY_TOP), bar = Ui.dp(context, DrawLayout.barRoom());
        return new Rect(barLeft ? bar : margin, pad, width - (barLeft ? margin : bar), height - pad);
    }
    /** The size in mm a tile shows at {@code look}, {@code width × height} pixels, or null when its loop is true to size. */
    static String mm(ToolSwatches.Look look, int width, int height) {
        return WildSample.shrunk(look, InkWash.tileCap(width, height)) ? ToolSwatches.mm(look.px).replace(" ", "") : null;
    }

    private static final Paint INK = new Paint(), TEXT = new Paint(Paint.ANTI_ALIAS_FLAG);
    /**
     * A tile {@code width × height} pixels: {@code loop} (or {@code icon}, centred, {@code iconSide} pixels at most) in
     * {@link #inner}, or a {@code row}'s in {@link #row}; the size {@code mm} (null: none), and when {@code selected}
     * underlined (a row's across its stroke's width, a tile's tab at its foot).
     */
    static void draw(Canvas canvas, Context context, int width, int height, Bitmap loop, Drawable icon, int iconSide,
                     String mm, boolean selected, boolean row) {
        draw(canvas, context, width, height, loop, icon, iconSide, mm, selected, row, false, Double.NaN, -1);
    }
    /** dp: a row's paint gauge ({@link #gauge}): its width, and the paper kept clear at its left. */
    static final float GAUGE = 6, GAUGE_GAP = 2;
    /**
     * As {@link #draw(Canvas, Context, int, int, Bitmap, Drawable, int, String, boolean, boolean)}, a row with its bar at
     * its left when {@code barLeft}, its brush's paint gauge filled {@code load} (0 to 1; NaN: none) and the dab of the
     * paint it holds in grey tone {@code held} (-1: none).
     */
    static void draw(Canvas canvas, Context context, int width, int height, Bitmap loop, Drawable icon, int iconSide,
                     String mm, boolean selected, boolean row, boolean barLeft, double load, int held) {
        Rect in = row ? row(context, width, height, barLeft, icon == null) : inner(context, width, height);
        canvas.save();
        canvas.clipRect(0, 0, width, height);
        canvas.drawColor(Theme.PAPER);
        if (loop != null) canvas.drawBitmap(loop, in.left, in.top, null);
        if (icon != null) {
            icon.setBounds(iconBox(in, iconSide));
            icon.draw(canvas);
        }
        INK.setColor(Theme.INK);
        if (mm != null) {
            // The caption size, bold, on paper at the loop's bottom right.
            TEXT.setTypeface(Theme.face(700));
            float pad = Ui.dp(context, 3), size = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, TypeStyle.CAPTION.sp, context.getResources().getDisplayMetrics());
            TEXT.setTextSize(size);
            // A narrow tile (the Nomad's Brush Lab row) shrinks the words to its width rather than cut their first digit (".1mm").
            float words = TEXT.measureText(mm), room = in.width() - 2 * pad;
            if (words > room && room > 0) { TEXT.setTextSize(size * room / words); words = TEXT.measureText(mm); }
            float cap = -TEXT.ascent() * .72f;
            float w = words + 2 * pad, h = cap + 2 * pad, right = in.right, bottom = in.bottom;
            INK.setColor(Theme.PAPER);
            canvas.drawRect(right - w, bottom - h, right, bottom, INK);
            TEXT.setColor(Theme.INK);
            canvas.drawText(mm, right - w + pad, bottom - pad, TEXT);
            INK.setColor(Theme.INK);
        }
        if (row && !Double.isNaN(load)) gauge(canvas, context, in, load);
        if (row && held >= 0) dab(canvas, context, in, held, !Double.isNaN(load));
        // The slot or tool in use: an ink bar down its side, its mark as drawn (the owner, 2026-10-23: "it should be a side
        // line, not an underline"); a slot's the row's height less its inset, an icon's its icon's.
        if (selected && row) canvas.drawRect(sideBar(context, width, height, barLeft, icon != null, iconSide), INK);
        else if (selected) {
            int inset = in.left + Ui.dp(context, SwatchFace.TAB_INSET);
            canvas.drawRect(inset, height - Ui.dp(context, SwatchFace.TAB), width - inset, height, INK);
        }
        canvas.restore();
    }

    /** An icon's square in {@code in}, centred, {@code iconSide} pixels at most. */
    private static Rect iconBox(Rect in, int iconSide) {
        int side = Math.min(iconSide, Math.min(in.width(), in.height())), x = in.centerX() - side / 2, y = in.centerY() - side / 2;
        return new Rect(x, y, x + side, y + side);
    }
    /**
     * A selected row's side bar, {@code width × height} pixels: on its bar's side (its left when {@code barLeft}), the
     * row's height less {@link DrawLayout#BAR_INSET} at its top and foot (the owner, 2026-10-27: "make it slightly
     * shorter"), or its icon's ({@code icon}, {@code iconSide} pixels at most). All a row's selection changes.
     */
    static Rect sideBar(Context context, int width, int height, boolean barLeft, boolean icon, int iconSide) {
        int bar = Ui.dp(context, DrawLayout.SIDE_BAR), left = barLeft ? 0 : width - bar, inset = Ui.dp(context, DrawLayout.BAR_INSET);
        Rect reach = icon ? iconBox(row(context, width, height, barLeft, false), iconSide) : new Rect(0, inset, width, height - inset);
        return new Rect(left, reach.top, left + bar, reach.bottom);
    }

    /** A row's paint gauge at the right end of its stroke's place {@code in}, filled {@code load} from the bottom: paper behind it and its gap, a fine ink outline, ink inside. */
    private static void gauge(Canvas canvas, Context context, Rect in, double load) {
        float line = Ui.dp(context, Theme.FINE), right = in.right, left = right - Ui.dp(context, GAUGE), top = in.top, bottom = in.bottom;
        INK.setColor(Theme.PAPER);
        canvas.drawRect(left - Ui.dp(context, GAUGE_GAP), top, right, bottom, INK);
        INK.setColor(Theme.INK);
        canvas.drawRect(left, top, right, top + line, INK);
        canvas.drawRect(left, bottom - line, right, bottom, INK);
        canvas.drawRect(left, top, left + line, bottom, INK);
        canvas.drawRect(right - line, top, right, bottom, INK);
        float inside = bottom - top - 2 * line, filled = (float) (inside * Math.max(0, Math.min(1, load)));
        if (filled > 0) canvas.drawRect(left + line, bottom - line - filled, right - line, bottom - line, INK);
    }

    /** dp: a dirty brush's dab ({@link #dab}), square. */
    static final float DAB = 8;
    /**
     * A dirty brush's dab at the right end of its row's stroke's place {@code in} (left of its gauge when {@code gauge}),
     * centred on it: paper round it, a fine ink edge, its held paint's grey {@code tone} inside.
     */
    private static void dab(Canvas canvas, Context context, Rect in, int tone, boolean gauge) {
        float side = Ui.dp(context, DAB), line = Ui.dp(context, Theme.FINE), gap = Ui.dp(context, GAUGE_GAP);
        float right = in.right - (gauge ? Ui.dp(context, GAUGE) + gap : 0), left = right - side, top = in.exactCenterY() - side / 2, bottom = top + side;
        INK.setColor(Theme.PAPER);
        canvas.drawRect(left - gap, top - gap, right, bottom + gap, INK);
        INK.setColor(Theme.INK);
        canvas.drawRect(left, top, right, bottom, INK);
        INK.setColor(0xff000000 | tone * 0x010101);
        canvas.drawRect(left + line, top + line, right - line, bottom - line, INK);
        INK.setColor(Theme.INK);
    }

    /**
     * A bar's row as a drawable, filling its bounds ({@link #row}: no size in mm): for a {@link ToolButton}, which draws
     * it as its icon, one {@link Face} for its selected look and one for the other ({@link ToolButton#iconOnly(Drawable,
     * Drawable)}); and, bare, a finger choice's row in its menu.
     */
    static final class Face extends Drawable {
        private final Context context;
        private final Bitmap stroke;
        private final Drawable icon;
        private final int iconSide;
        private final boolean selected, barLeft;
        /** Its brush's paint gauge ({@link #gauge}), 0 to 1; NaN for none. */
        private final double load;
        /** The grey of the paint its dirty brush holds ({@link #dab}), -1 for none. */
        private final int held;
        /** A row {@code selected} or not, its side bar at its left when {@code barLeft}, its gauge filled {@code load} (NaN: none), its dab {@code held} (-1: none). */
        Face(Context context, Bitmap stroke, Drawable icon, int iconSide, boolean selected, boolean barLeft, double load, int held) {
            this.context = context; this.stroke = stroke; this.icon = icon; this.iconSide = iconSide; this.selected = selected; this.barLeft = barLeft; this.load = load; this.held = held;
        }
        /** How many times a row has been painted, Android's frames and the fast display's captures alike (PickerBench). */
        static int painted;
        @Override public void draw(Canvas canvas) {
            painted++;
            Rect b = getBounds();
            canvas.save();
            canvas.translate(b.left, b.top);
            SlotTile.draw(canvas, context, b.width(), b.height(), stroke, icon, iconSide, null, selected, true, barLeft, load, held);
            canvas.restore();
        }
        /**
         * Where its side bar goes when drawn in {@code bounds}: the only pixels a row's two faces don't share, so a selection
         * change is put on the panel there alone ({@link ToolButton#markerArea}).
         */
        Rect sideBar(Rect bounds) {
            Rect bar = SlotTile.sideBar(context, bounds.width(), bounds.height(), barLeft, icon != null, iconSide);
            bar.offset(bounds.left, bounds.top);
            return bar;
        }
        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(ColorFilter filter) { }
        @Override public int getOpacity() { return PixelFormat.OPAQUE; }
    }
}
