package io.github.mpdairy.monopaint;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Brush Lab's and the Tools page's builders, in Scribbler Style ({@link Theme}'s tokens: square corners, iA Writer Duo;
 * on these two screens paper and {@link Theme#FINE} dp ink lines, not grey fills, which e-ink renders poorly (the owner,
 * 2026-10-23); ink fills for what's on), as the owner's mockup (LabDaily).
 */
final class LabUi {
    private LabUi() { }

    static int dp(Context context, float value) { return Ui.dp(context, value); }
    /**
     * Shrinks {@code view}'s text to fit, between {@code a} and {@code b} sp in either order. Android throws when the
     * largest size isn't above the smallest, which a tight budget can make so; then the text is simply that size.
     */
    static void autoSize(TextView view, float a, float b) {
        int small = (int) Math.min(a, b), large = (int) Math.max(a, b);
        if (large <= small) {
            view.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_NONE);
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, large);
        } else view.setAutoSizeTextTypeUniformWithConfiguration(small, large, 1, TypedValue.COMPLEX_UNIT_SP);
    }
    /** Text in {@code type} ({@link Ui#style}). */
    static TextView text(Context context, String text, TypeStyle type) {
        TextView view = Theme.style(new TextView(context), type);
        view.setText(text);
        return view;
    }
    /** Text {@code sp} large at {@code weight}, in the lab's face, otherwise as {@link Ui#style}'s body. */
    static TextView text(Context context, String text, float sp, int weight) {
        TextView view = text(context, text, TypeStyle.BODY);
        view.setTextSize(sp); view.setTypeface(Theme.face(weight));
        return view;
    }
    /** A label: caps, tracked, 700 ({@link TypeStyle#LABEL}). */
    static TextView heading(Context context, String text) { return text(context, text, TypeStyle.LABEL); }
    /**
     * A section's header row (Brush Lab's TOOLBAR, LIBRARY, ADJUST; a part's choices): the one header style, nothing
     * drawn behind it; its words {@link #barText}, room for buttons at its right.
     */
    static LinearLayout bar(Context context) {
        LinearLayout bar = row(context);
        bar.setPadding(dp(context, 10), 0, dp(context, 10), 0);
        return bar;
    }
    /**
     * A section's header (the Tools page's Test Area, This Brush, Toolbar Slots and Library): the one header style of
     * Scribbler Style (the owner, 2026-10-23), its label at the left ({@link #barText}: LABEL in grey-800), no rule, room
     * for buttons at its right.
     */
    static LinearLayout header(Context context, String label) {
        LinearLayout header = row(context);
        header.addView(barText(context, label), new LinearLayout.LayoutParams(-2, -2));
        return header;
    }
    /** A section header's words: a {@link #heading} (LABEL) in grey-800, one line. */
    static TextView barText(Context context, String text) {
        TextView view = heading(context, text);
        view.setTextColor(Theme.GREY_800); view.setSingleLine(true);
        return view;
    }
    static LinearLayout row(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }
    static LinearLayout column(Context context) {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        return column;
    }
    /** A rule {@code thickness} dp thick across a vertical layout. */
    static void rule(LinearLayout parent, float thickness, int color) {
        View rule = new View(parent.getContext()); rule.setBackgroundColor(color);
        parent.addView(rule, new LinearLayout.LayoutParams(-1, dp(parent.getContext(), thickness)));
    }
    /** A place in a row {@code width} pixels wide (or -2) and {@code height} dp tall, {@code gap} dp after what comes before. */
    static LinearLayout.LayoutParams after(Context context, int width, float height, float gap) {
        LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(width, dp(context, height));
        place.leftMargin = dp(context, gap);
        return place;
    }
    /**
     * A grid of cards (Brush Lab's Open sheet, the Tools page's slots and library): a column of rows on ink,
     * which shows {@link Theme#FINE} dp between its cells ({@link #cell}, {@link #gridRow}) and round them, so the cards
     * are parted by thin ink lines; each card its drawing on paper over its words on paper under a line ({@link
     * LoopCard#ruled}).
     */
    static LinearLayout grid(Context context) {
        LinearLayout grid = column(context);
        grid.setBackgroundColor(Theme.INK);
        int line = dp(context, Theme.FINE);
        grid.setPadding(line, line, line, line);
        return grid;
    }
    /** Grid cell {@code c} of {@code columns}: an equal share of its row, a line after all but the last. */
    static LinearLayout.LayoutParams cell(Context context, int c, int columns) {
        LinearLayout.LayoutParams cell = new LinearLayout.LayoutParams(0, -1, 1);
        if (c < columns - 1) cell.rightMargin = dp(context, Theme.FINE);
        return cell;
    }
    /** Grid row {@code r} of {@code rows}: an equal share of the grid, a line under all but the last. */
    static LinearLayout.LayoutParams gridRow(Context context, int r, int rows) {
        LinearLayout.LayoutParams row = new LinearLayout.LayoutParams(-1, 0, 1);
        if (r < rows - 1) row.bottomMargin = dp(context, Theme.FINE);
        return row;
    }
    /**
     * A {@link Theme#FINE} dp ink line across a band {@code side} pixels inside its frame, from edge to edge: between a
     * band's groups, in the middle of the {@code gap} pixels between them (the line's own pixel out of it), so the band
     * keeps its height.
     */
    static void line(LinearLayout band, int side, int gap) {
        View line = new View(band.getContext());
        line.setBackgroundColor(Theme.INK);
        int fine = dp(band.getContext(), Theme.FINE);
        LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(-1, fine);
        place.setMargins(-side, (gap - fine) / 2, -side, gap - fine - (gap - fine) / 2);
        band.addView(line, place);
    }
    /** Takes up the rest of a horizontal layout. */
    static void spring(LinearLayout row) { row.addView(new View(row.getContext()), new LinearLayout.LayoutParams(0, 1, 1)); }

    /** Children in rows, wrapping onto a new row when the width runs out, {@code gap} pixels apart: the mockup's chip rows. */
    static final class Wrap extends ViewGroup {
        private final int gap;
        Wrap(Context context, int gap) { super(context); this.gap = gap; }
        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            int width = MeasureSpec.getSize(widthSpec), x = 0, y = 0, rowHeight = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child.getVisibility() == GONE) continue;
                child.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                int w = child.getMeasuredWidth(), h = child.getMeasuredHeight();
                if (x > 0 && x + w > width) { x = 0; y += rowHeight + gap; rowHeight = 0; }
                x += w + gap; rowHeight = Math.max(rowHeight, h);
            }
            setMeasuredDimension(width, resolveSize(y + rowHeight, heightSpec));
        }
        @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            int width = right - left, x = 0, y = 0, rowHeight = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child.getVisibility() == GONE) continue;
                int w = child.getMeasuredWidth(), h = child.getMeasuredHeight();
                if (x > 0 && x + w > width) { x = 0; y += rowHeight + gap; rowHeight = 0; }
                child.layout(x, y, x + w, y + h);
                x += w + gap; rowHeight = Math.max(rowHeight, h);
            }
        }
    }
}
