package io.github.mpdairy.monopaint;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/**
 * One size swatch (the prototype's .tslot), on the Tools page, over Brush Lab's test page and in the Sizes window,
 * with no border (spec "The toolbar", 3). A size is a black dot as wide as the tool draws, in screen pixels
 * (page pixels at 100%: the size on the page, which the dot shows whatever the zoom, as MonoPaint's size swatches do;
 * zoomed in, the eraser's ring on the page shows its width there), up to 80 % of the face; a broader one shows as that
 * largest dot with its size in millimetres. The one in use has a 5 dp black tab under it, the only marker (grey
 * swatches went with the one grey, 2026-10-24). A tap picks it; one that can be set ({@link #editable}) is edited by a long press (the
 * owner, 2026-10-19: no ▾ corner; the whole set is edited from the Edit button beside the faces, {@link
 * SwatchFaces#editAll}). The view keeps {@link #TAB_ROOM} dp under the face for the marker. (The drawing screen's
 * column had them in pairs until 2026-10-24, when its size slider and grey strip took their place.)
 */
@SuppressLint("ViewConstructor")
final class SwatchFace extends View {
    /** dp: around the face; under it, the gap and the marker tab; the marker's height and inset. */
    static final int PAD = DrawLayout.FACE_PAD, TAB_ROOM = SwatchSetLayout.TAB_ROOM, TAB = 5, TAB_INSET = 3;
    /** How tall a face {@code width} dp wide is, dp: its square face, the padding over it and the marker's room under it. */
    static int tall(int width) { return SwatchSetLayout.faceTall(width); }
    private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** Its diameter in screen pixels. */
    private float diameter;
    private boolean picked;
    private Runnable edit;

    /** A size swatch {@code diameter} screen pixels wide; a tap runs {@code pick}. */
    static SwatchFace size(Context context, double diameter, String description, Runnable pick) {
        SwatchFace face = new SwatchFace(context, description, pick);
        face.setDiameter(diameter);
        return face;
    }
    private SwatchFace(Context context, String description, Runnable pick) {
        super(context);
        setContentDescription(description);
        ink.setTypeface(Theme.face(700)); ink.setTextAlign(Paint.Align.CENTER);
        setOnClickListener(v -> pick.run());
        setOnLongClickListener(v -> { if (edit == null) return false; edit.run(); return true; });
    }

    /** {@code sizes} show {@code swatches}' sizes in screen pixels (page pixels at 100%, whatever the zoom), size {@code size} marked. */
    static void show(SwatchFace[] sizes, ToolSwatches swatches, int size) {
        for (int i = 0; i < sizes.length; i++) { sizes[i].setDiameter(swatches.size(i)); sizes[i].setPicked(i == size); }
    }
    /** A long press edits it with {@code edit} (null: it can't be set). Nothing it shows changes. */
    void editable(Runnable edit) { this.edit = edit; }
    void setPicked(boolean picked) { if (this.picked != picked) { this.picked = picked; setSelected(picked); invalidate(); } }
    void setDiameter(double diameter) { if (this.diameter != (float)diameter) { this.diameter = (float)diameter; invalidate(); } }

    /** The face's side, pixels: the view less its padding (a pair's none at its sides) and the marker's room. */
    private float side() {
        return Math.max(1, Math.min(getWidth() - 2 * dp(PAD), getHeight() - dp(PAD) - dp(TAB_ROOM)));
    }
    private float left() { return (getWidth() - side()) / 2f; }

    @Override protected void onDraw(Canvas canvas) {
        canvas.drawColor(Theme.PAPER);
        float side = side(), left = left(), top = dp(PAD), right = left + side, bottom = top + side;
        ink.setStyle(Paint.Style.FILL); ink.setColor(Theme.INK);
        // As wide as it draws, up to 80 % of the face; broader, that largest dot, its size written in it.
        float x = left + side / 2, y = top + side / 2, cap = side * .8f;
        canvas.drawCircle(x, y, Math.max(.8f, Math.min(diameter, cap) / 2), ink);
        if (diameter > cap) {
            // The number over its unit, each on its own line, so neither is squeezed to fit ("10mm" was).
            String mm = ToolSwatches.mm(diameter), number = mm.substring(0, mm.indexOf(' ')), unit = "mm";
            ink.setColor(Theme.ON_INK); ink.setTextSize(cap / 2.6f);
            ink.setTextSize(ink.getTextSize() * Math.min(1, cap * .7f / ink.measureText(number)));
            float numberSize = ink.getTextSize(), unitSize = numberSize * .62f, gap = numberSize * .08f;
            float high = numberSize * .72f + gap + unitSize * .72f, baseline = y - high / 2 + numberSize * .72f;
            canvas.drawText(number, x, baseline, ink);
            ink.setTextSize(unitSize);
            canvas.drawText(unit, x, baseline + gap + unitSize * .72f, ink);
            ink.setColor(Theme.INK);
        }
        if (picked) canvas.drawRect(left + dp(TAB_INSET), bottom + dp(TAB_ROOM - TAB), right - dp(TAB_INSET), bottom + dp(TAB_ROOM), ink);
    }
    private int dp(float value) { return Ui.dp(getContext(), value); }
}
