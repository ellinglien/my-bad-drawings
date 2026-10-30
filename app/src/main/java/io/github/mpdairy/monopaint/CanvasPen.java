package io.github.mpdairy.monopaint;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;

/**
 * {@link CardArt}'s pen on the screen: dp to pixels, shapes without smoothing (crisp on e-ink), text in iA Writer Duo
 * ({@link Theme#face}), icons from {@code res/drawable}, pictures as alpha
 * masks drawn in ink ({@link NotebookScreens#mask}). Its {@link ArtView} draws an {@link ArtView.Art} through one.
 */
final class CanvasPen implements CardArt.Pen {
    private final Context context;
    private final float density;
    private Canvas canvas;
    private final Paint shape = new Paint(), text = new Paint(Paint.ANTI_ALIAS_FLAG), picture = new Paint();

    CanvasPen(Context context) {
        this.context = context;
        density = context.getResources().getDisplayMetrics().density;
    }
    CanvasPen on(Canvas canvas) { this.canvas = canvas; return this; }
    float px(float dp) { return dp * density; }
    float dp(float px) { return px / density; }

    @Override public void fill(float l, float t, float r, float b, int color) {
        shape.setStyle(Paint.Style.FILL); shape.setColor(color);
        canvas.drawRect(Math.round(px(l)), Math.round(px(t)), Math.round(px(r)), Math.round(px(b)), shape);
    }
    @Override public void text(String words, float x, float baseline, float sp, CardArt.Face face, int color) {
        text.setTypeface(Theme.face(face.bold ? 700 : 400));
        text.setTextSize(px(LabText.em(sp))); text.setLetterSpacing(face.tracking); text.setColor(color);
        canvas.drawText(words, px(x), px(baseline), text);
    }
    @Override public void icon(CardArt.Icon icon, float l, float t, float size, int color) {
        Drawable d = Theme.tinted(context, resource(icon), color);
        d.setBounds(Math.round(px(l)), Math.round(px(t)), Math.round(px(l + size)), Math.round(px(t + size)));
        d.draw(canvas);
    }
    static int resource(CardArt.Icon icon) {
        switch (icon) {
            case DICE: return R.drawable.ic_ph_dice;
            case EYE: return R.drawable.ic_ph_eye_fill;
            case EYE_CLOSED: return R.drawable.ic_ph_eye_closed;
            case EYE_HALF: return R.drawable.ic_ph_eye_duotone;
            default: return R.drawable.ic_ph_eye_light;
        }
    }
    @Override public void picture(Object made, float l, float t, float r, float b) {
        if (!(made instanceof Bitmap)) return;
        picture.setColor(CardArt.INK);
        canvas.drawBitmap((Bitmap) made, null, new RectF(Math.round(px(l)), Math.round(px(t)), Math.round(px(r)), Math.round(px(b))), picture);
    }

    /** A view that draws its {@link Art} through a pen, in dp. */
    @SuppressLint("ViewConstructor")
    static class ArtView extends View {
        interface Art { void draw(CanvasPen pen, float w, float h); }
        Art art;
        private final CanvasPen pen;
        ArtView(Context context, Art art) { super(context); this.art = art; pen = new CanvasPen(context); }
        @Override protected void onDraw(Canvas canvas) { if (art != null) art.draw(pen.on(canvas), pen.dp(getWidth()), pen.dp(getHeight())); }
        CanvasPen pen() { return pen; }
    }
}
