package io.github.mpdairy.monopaint;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import java.util.function.IntConsumer;

/**
 * Grey Shift's set in Brush Lab's Control card ({@link SwatchPicker}; mischief-lab-2.html's swatch row): the brush's
 * greys in a row, the toolbar's four 44 dp wide or Any's sixteen sharing the row. One in the set is a full square of
 * its grey in the panel's dots with an ink edge; one left out, a small square with a grey edge. A tap takes it in or
 * out ({@link BrushLab#toggleGrey}). Built with the open card.
 */
@SuppressLint("ViewConstructor")
final class GreySet extends LinearLayout {
    /** dp: a toolbar swatch's width; the gap between swatches. */
    private static final int WIDE = 44, GAP = 4;

    /** {@code tones}: each grey as the brush lays it (0–255); {@code darkness}, as picked, for its words; {@code in}, whether it is in the set. */
    GreySet(Context context, int[] tones, double[] darkness, boolean[] in, boolean enabled, IntConsumer tap) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        boolean few = tones.length <= ToolSwatches.COUNT;
        for (int k = 0; k < tones.length; k++) {
            int index = k;
            Swatch swatch = new Swatch(context, tones[k], in[k]);
            swatch.setContentDescription(ToolSwatches.greyWords(darkness[k], tones[k]) + (in[k] ? "" : ", left out"));
            swatch.setEnabled(enabled);
            swatch.setOnClickListener(v -> tap.accept(index));
            LayoutParams place = few ? new LayoutParams(Ui.dp(context, WIDE), -1) : new LayoutParams(0, -1, 1);
            if (k > 0) place.leftMargin = Ui.dp(context, GAP);
            addView(swatch, place);
        }
    }

    /** One grey: full with an ink edge in the set, small with a grey edge out of it. */
    private static final class Swatch extends View {
        private final boolean in;
        private final Paint dots = new Paint(), edge = new Paint();
        private final Bitmap tile;

        Swatch(Context context, int tone, boolean in) {
            super(context);
            this.in = in;
            tile = DotGray.tile(tone);
            dots.setShader(new BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
            edge.setStyle(Paint.Style.STROKE);
            edge.setStrokeWidth(Ui.dp(context, Theme.BASE));
            edge.setColor(in ? Theme.INK : Theme.GREY_600);
        }
        @Override protected void onDraw(Canvas canvas) {
            float line = edge.getStrokeWidth(), pad = Ui.dp(getContext(), 3), w = getWidth(), h = getHeight() - Ui.dp(getContext(), LabLayout.ROW_RULE);
            float side = Math.min(w, h - 2 * pad), left = (w - side) / 2, top = (h - side) / 2;
            float inset = in ? line : side * .3f;
            canvas.drawRect(left + inset, top + inset, left + side - inset, top + side - inset, dots);
            canvas.drawRect(left + line / 2, top + line / 2, left + side - line / 2, top + side - line / 2, edge);
        }
    }
}
