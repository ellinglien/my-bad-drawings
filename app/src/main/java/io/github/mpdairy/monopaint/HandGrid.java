package io.github.mpdairy.monopaint;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Locale;

/**
 * Brush Lab's hand grid (ui.js handGrid, 275–287; CSS .hgrid, .cell): rows Pressure, Tilt, Speed and Page tilt (the
 * Gravity Lab's fourth: drawn downhill on a tilted page), columns Size, Dark and Nib. A cell fills from the bottom with how much that input drives that output, hatched for some,
 * solid for full, its level written in caps; the speed-size cell says "+" or "−" for Fast is. A tap cycles it off,
 * some, full, and Tilt → size on to full+, the old Fude pen's reach ({@link BrushLab#cycleCell}). Each row ends with its lock and die, lined up with the open card's dial
 * rows. Its text is {@link LabLayout#DENSE_SP}, never smaller: the row names take the longest's width, the cells the rest
 * (LabLayoutChecks fits the heads and levels). Built with the open card.
 */
@SuppressLint("ViewConstructor")
final class HandGrid extends LinearLayout {
    /** The column heads (ui.js 279): the nib is its width under pressure, its angle under tilt and speed. */
    private static final String[] HEADS = {"Size", "Dark", "Nib"};

    /** {@code nameWidth} (the row names') and {@code cellHeight} in dp; a change calls {@code changed}, a lock {@code relocked}. */
    HandGrid(Context context, BrushLab lab, int nameWidth, int cellHeight, Runnable changed, Runnable relocked) {
        super(context);
        setOrientation(VERTICAL);
        ModularBrush.Part hand = lab.parts().part(BrushPart.HAND);
        LinearLayout heads = LabUi.row(context);
        heads.addView(new View(context), new LayoutParams(dp(nameWidth + 4), 1));
        for (String head : HEADS) {
            // A brush that prints dots (Print shading, Screentone): its darkness cells size them.
            TextView text = LabUi.heading(context, head.equals("Dark") ? ModularWords.darkHead(lab.parts()) : head);
            text.setTextSize(LabLayout.DENSE_SP); text.setLetterSpacing(0);
            text.setTextColor(Theme.GREY_800); text.setGravity(Gravity.CENTER | Gravity.BOTTOM); text.setMaxLines(2);
            heads.addView(text, new LayoutParams(0, -1, 1));
        }
        heads.addView(new View(context), new LayoutParams(dp(8 + 2 * LabLayout.LOCK), 1));
        addView(heads, new LayoutParams(-1, dp(LabLayout.handHead())));
        boolean handLocked = lab.locked(BrushPart.HAND);
        for (int r = 0; r < BrushLab.HAND_ROWS.length; r++) {
            int row = r;
            String name = BrushLab.HAND_ROWS[r];
            boolean rowLocked = lab.rowLocked(r);
            LinearLayout line = LabUi.row(context);
            TextView label = LabUi.heading(context, name);
            label.setTextSize(LabLayout.DENSE_SP); label.setLetterSpacing(LabLayout.LABEL_TRACKING);
            label.setGravity(Gravity.CENTER_VERTICAL); label.setMaxLines(1);
            if (rowLocked) label.setTextColor(Theme.GREY_800);
            line.addView(label, new LayoutParams(dp(nameWidth + 4), -1));
            PartDial[] cells = BrushLab.handRow(r);
            for (int c = 0; c < cells.length; c++) {
                PartDial dial = cells[c];
                String level = BrushLab.level(hand, dial);
                Cell cell = new Cell(context, hand.get(dial), level, dial == PartDial.SS ? (int) hand.get(PartDial.DIR) : -1);
                cell.setEnabled(!lab.locked(dial));
                cell.setContentDescription(dial.label + ": " + level);
                cell.setOnClickListener(v -> { lab.cycleCell(dial); changed.run(); });
                // Butted, sharing their 2 dp rules, as the lab's grids.
                LayoutParams place = new LayoutParams(0, -1, 1);
                if (c > 0) place.leftMargin = -dp(2);
                line.addView(cell, place);
            }
            LayoutParams gap = new LayoutParams(dp(LabLayout.LOCK), dp(LabLayout.LOCK)); gap.leftMargin = dp(8);
            line.addView(LabButton.lock(context, name, rowLocked, handLocked ? null : () -> { lab.toggleRowLock(row); relocked.run(); }), gap);
            line.addView(LabButton.die(context, name, rowLocked, () -> { lab.rollRow(row); changed.run(); }), new LayoutParams(dp(LabLayout.LOCK), dp(LabLayout.LOCK)));
            LayoutParams place = new LayoutParams(-1, dp(cellHeight));
            if (r > 0) place.topMargin = -dp(2);
            addView(line, place);
        }
    }

    /** One cell (CSS .cell): a 2 dp frame, the fill, and its level in caps on a patch of paper (of ink, when full). */
    @SuppressLint("ViewConstructor")
    private static final class Cell extends View {
        private final double value;
        private final String text;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        /** {@code level} as {@link BrushLab#level(ModularBrush.Part, PartDial)} writes it; {@code fast}: Fast is (0 thinner, 1 thicker) for the speed-size cell, else -1. */
        Cell(Context context, double value, String level, int fast) {
            super(context);
            this.value = value;
            level = level.toUpperCase(Locale.ROOT);
            text = fast >= 0 && value > .1 ? (fast == 1 ? "+" : "−") + level : level;
            paint.setTypeface(Theme.face(700));
            paint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, LabLayout.DENSE_SP, getResources().getDisplayMetrics()));
            paint.setTextAlign(Paint.Align.CENTER);
        }
        @Override protected void onDraw(Canvas canvas) {
            int w = getWidth(), h = getHeight();
            float rule = dp(2), inner = h - 2 * rule;
            boolean full = value >= .75;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Theme.INK);
            canvas.drawRect(0, 0, w, h, paint);
            paint.setColor(Theme.PAPER);
            canvas.drawRect(rule, rule, w - rule, h - rule, paint);
            float top = h - rule - (float) (Math.max(0, Math.min(1, value)) * inner);
            paint.setColor(Theme.INK);
            if (full) canvas.drawRect(rule, top, w - rule, h - rule, paint);
            else if (top < h - rule) {
                // Hatched: 2 dp of ink in every 5 dp, at -45 degrees (CSS .cell i).
                canvas.save();
                canvas.clipRect(rule, top, w - rule, h - rule);
                paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(2) / 1.414f);
                for (float x = -h; x < w + h; x += dp(5) * 1.414f) canvas.drawLine(x, h, x + h, 0, paint);
                canvas.restore();
                paint.setStyle(Paint.Style.FILL);
            }
            float textWidth = paint.measureText(text), pad = dp(3);
            Paint.FontMetrics m = paint.getFontMetrics();
            float cx = w / 2f, baseline = h / 2f - (m.ascent + m.descent) / 2;
            paint.setColor(full ? Theme.INK : Theme.PAPER);
            canvas.drawRect(cx - textWidth / 2 - pad, baseline + m.ascent, cx + textWidth / 2 + pad, baseline + m.descent, paint);
            paint.setColor(!isEnabled() ? Theme.GREY_800 : full ? Theme.PAPER : Theme.INK);
            canvas.drawText(text, cx, baseline, paint);
        }
        private float dp(float value) { return Ui.dp(getContext(), value); }
    }
    private int dp(float value) { return Ui.dp(getContext(), value); }
}
