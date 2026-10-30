package io.github.mpdairy.monopaint;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import java.util.Arrays;

/**
 * Over the size slider and the grey strip ({@link SwatchBar}; the owner, 2026-10-24: "put the size selected and the
 * current shade below", then "merge the current size and shade, it can be one thing", then on top in the owner's
 * sketch): one dot, on the column's axis, at
 * the size in use's true size (page pixels at 100%, as the Tools page's size faces) up to the room it has, filled with
 * the grey in use in the panel's dots as the brush lays it; a light grey, or a dot too small for its dots to show, gets a
 * fine ink edge. The dot is the brush's nib as it lies at rest, its shape, long and short sides and angle (the owner,
 * 2026-10-26: "preview the shape of the drawing tool in the square preview"; {@link FootprintSweep#atRest}), its longer side
 * the size; a round nib, and the eraser, a circle. The eraser and the smudge tool, which have no grey, show paper in that edge. It sits on plain paper, no frame (the owner, 2026-10-27: "remove square frame from toolbar (no outline)"; the owner's drawn square from 2026-10-26, a hairline box before). It changes only when the
 * size or the grey does, which a lift on the slider or the strip sets, so a drag never draws it. It takes no pen.
 */
@SuppressLint("ViewConstructor")
final class SwatchReadout extends View {
    /** No anti-aliasing: the dot is ink and paper alone, so it changes through the panel's binary update, no grey flash (2026-10-24). */
    private final Paint ink = new Paint(), dots = new Paint();
    /** The size in use, px; the grey's tone as laid, 0 to 255, or -1 for none. */
    private double px = -1;
    private int tone = -1;
    /** The nib's marks at rest ({@link FootprintSweep#atRest}); null or all round: a circle. */
    private double[][] marks;
    private final Path shape = new Path(), mark = new Path();
    private final Matrix turn = new Matrix();
    private final RectF oval = new RectF();
    private Bitmap tile;

    SwatchReadout(Context context) { super(context); }

    /**
     * Shows size {@code px} and grey {@code tone} (-1: none) in the nib's shape {@code marks} (null: round). Asks Android for no frame: the caller puts the change on the
     * panel ({@link SelectionFeedback#updateBinary}), its dots ink and paper.
     */
    void show(double px, int tone, double[][] marks) {
        if (px == this.px && tone == this.tone && Arrays.deepEquals(marks, this.marks)) return;
        this.px = px; this.marks = marks;
        if (tone != this.tone && tone >= 0) {
            Bitmap previous = tile;
            tile = DotGray.tile(tone);
            dots.setShader(new BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
            if (previous != null) previous.recycle();
        }
        this.tone = tone;
        setContentDescription(DrawLayout.sizeSaid(px));
    }

    /**
     * Shows {@code look} as {@code brush} draws it (null: the eraser or the smudge tool, round), its grey only with {@code
     * greys}: the side bar's ({@link SwatchBar}) and Brush Lab's beside its picker box.
     */
    void show(ToolSwatches.Look look, Brush brush, boolean greys) {
        show(look.px, greys && brush != null ? ModularSizes.swatch(brush.parts, look.darkness) : -1,
                brush == null ? null : FootprintSweep.atRest(ResolvedBrush.of(brush.parts, look.px, look.darkness)));
    }

    @Override protected void onDraw(Canvas canvas) {
        canvas.drawColor(Theme.PAPER);
        ink.setColor(Theme.INK); ink.setStyle(Paint.Style.STROKE);
        if (px < 0) return;
        float gap = dp(DrawLayout.FACE_GAP), room = Math.max(1, Math.min(getWidth(), getHeight()) - gap);
        float x = getWidth() / 2f, y = getHeight() / 2f, r = Math.max(.8f, Math.min((float) px, room) / 2);
        // The marks' box, half its sides, and whether they are all round; the longer side is the size, as the round dot's.
        double wide = 0, tall = 0;
        boolean round = true;
        if (marks != null) for (double[] m : marks) {
            wide = Math.max(wide, Math.hypot(m[0] * m[2], m[1] * m[3])); tall = Math.max(tall, Math.hypot(m[0] * m[3], m[1] * m[2]));
            round &= m[0] == m[1];
        }
        ink.setStrokeWidth(dp(Theme.FINE));
        if (round) {
            if (tone >= 0) canvas.drawCircle(x, y, r, dots);
            if (edged(2 * r)) canvas.drawCircle(x, y, r, ink);
            return;
        }
        float k = (float) (r / Math.max(wide, tall));
        shape.reset();
        for (double[] m : marks) {
            float a = Math.max(.8f, (float) m[0] * k), b = Math.max(.8f, (float) m[1] * k);
            oval.set(-a, -b, a, b);
            mark.reset(); mark.addOval(oval, Path.Direction.CW);
            turn.setRotate((float) Math.toDegrees(Math.atan2(m[3], m[2]))); turn.postTranslate(x, y);
            mark.transform(turn);
            shape.op(mark, Path.Op.UNION);
        }
        if (tone >= 0) canvas.drawPath(shape, dots);
        if (edged(2 * k * (float) Math.min(wide, tall))) canvas.drawPath(shape, ink);
    }
    /** Whether the dot gets the fine ink edge: no grey, a light one, or {@code across} px at its narrowest too few for its dots to show. */
    private boolean edged(float across) { return tone < 0 || tone > PAPERY || across < dp(EDGED); }
    /** Under this many dp across, the dot's grey dots may not show: it gets the edge too. */
    private static final int EDGED = 6;
    /** A grey lighter than this would vanish on paper without its ink edge (the swatch faces' old rule). */
    private static final int PAPERY = 235;
    private int dp(float value) { return Ui.dp(getContext(), value); }
}
