package io.github.mpdairy.monopaint;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextPaint;
import android.util.TypedValue;
import android.view.View;
import java.util.List;
import java.util.function.Supplier;

/**
 * A Brush Lab card, as the mockup's library card, in Scribbler Style's tile (the owner, 2026-10-23): the brush's loop
 * sample in the page's dots on paper, then on grey-200 the name (13 sp, 700, up to two lines, the second cut at a word;
 * library cards only) and its caption under it in grey-800, each item wrapped, never cut; no frame. The caption leaves out
 * the name, then shrinks the sample, to fit ({@link LabText#caption}). The one in use has a {@link SwatchFace#TAB} dp ink tab
 * at its foot ({@link #setNow}); in a
 * menu its words are on paper ({@link #onPaper}); in Brush Lab on paper under a 1 dp ink line
 * ({@link #ruled}). The
 * sample is drawn in the background ({@link SampleQueue}) at the card's own pixel size, so its dots stay whole; the
 * card is white until it arrives. The card fills the size it is given.
 */
@SuppressLint("ViewConstructor")
final class LoopCard extends View {
    /** Draws a sample {@code width × height}: called on the background thread, so it must only read its own copy of the brush. */
    interface Sample { int[] pixels(int width, int height); }
    /** The caption as it fits the card's size now. */
    private LabText.Caption fitted = LabText.Caption.NONE;
    private final SampleQueue samples;
    private List<String> caption;
    private final String name;
    /** The least size its caption may take, sp ({@link #setLeast}). */
    private float least = LabText.CAPTION_SP;
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint();
    private boolean now;
    /** What the sample shows ({@link Brush#identity}), how to draw it, and the drawing once it arrives. */
    private String key;
    private Sample sample;
    private Bitmap picture;

    /** A card; {@code name} null for one without it (swap tiles). */
    LoopCard(Context context, SampleQueue samples, List<String> caption, String name) {
        super(context);
        this.samples = samples; this.caption = caption; this.name = name;
        setContentDescription((name != null ? name + ": " : "") + String.join(", ", caption));
    }

    /**
     * Shows the sample {@code sample} gives, kept as long as {@code key} (what it shows) stays the same: {@code sample}
     * is asked only for a new key, so a copy of the brush for it is made only then. The old sample stays until the
     * new one arrives, so the card is drawn again once, not blanked first (e-ink).
     */
    void setSample(String key, Supplier<Sample> sample) {
        if (key.equals(this.key)) return;
        this.key = key; this.sample = sample.get();
        request();
    }
    /** A new caption, fitted and drawn again only if it differs (a swap strip tile's note). */
    void setCaption(List<String> caption) {
        if (caption.equals(this.caption)) return;
        this.caption = caption;
        int before = sampleHeight();
        fit();
        // A caption of another height changes the sample's: drawn anew at its size.
        if (sampleHeight() != before) { recycle(); request(); }
        invalidate();
    }

    /** Its caption may shrink to {@code sp} rather than lose its first item ({@link LabLayout#swapLeast}). */
    void setLeast(float sp) { least = sp; }
    /** Its caption's inset from either side, dp ({@link LabLayout#swapInset}); {@link LabText#CAPTION_INSET} unless set. */
    void setInset(float dp) { inset = dp; }
    private float inset = LabText.CAPTION_INSET;
    /** The one in use (the brush in the lab, a menu's tool): its tab at its foot. */
    void setNow(boolean now) { if (now != this.now) { this.now = now; invalidate(); } }
    /** Its words on paper, not grey-200: a card in a menu over the page, where no grey goes (the owner, 2026-10-23). */
    boolean onPaper;
    /**
     * Its words on paper under a {@link Theme#FINE} dp ink line, which parts them from the drawing: Brush Lab's tiles
     * (the Tools page's until its cards, 2026-10-28), where grey fills went (the owner, 2026-10-23: "eink grey doesn't quite work the same way").
     */
    boolean ruled;
    /**
     * A slot whose brush changed on this visit (the Tools page's): a small grey-800 dot under its name
     * (docs/archive/design-pass-2026-10-23.md, "Getting back to where you started").
     */
    void setChanged(boolean changed) { if (changed != this.changed) { this.changed = changed; invalidate(); } }
    private boolean changed;
    /** The changed dot's diameter, dp, and its room under it. */
    static final float DOT = 6, DOT_UNDER = 3;

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), MeasureSpec.getSize(heightSpec));
    }
    @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) { fit(); recycle(); request(); }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); if (picture == null) request(); }
    @Override protected void onDetachedFromWindow() { super.onDetachedFromWindow(); samples.cancel(this); recycle(); }
    private void recycle() { if (picture != null) { picture.recycle(); picture = null; } }
    /** Asks for the sample at the size it now has, if it has one. */
    private void request() {
        int w = sampleWidth(), h = sampleHeight();
        if (sample == null || w <= 0 || h <= 0) { samples.cancel(this); return; }
        Sample drawing = sample;
        samples.request(this, () -> Bitmap.createBitmap(drawing.pixels(w, h), w, h, Bitmap.Config.ARGB_8888), bitmap -> {
            if (bitmap.getWidth() != sampleWidth() || bitmap.getHeight() != sampleHeight()) { bitmap.recycle(); return; }
            recycle();
            picture = bitmap;
            invalidate();
        }, Bitmap::recycle);
    }

    // Geometry

    /** The caption fitted to the card's size, measured in the paint it is drawn with (the name's 700, the widest). */
    private void fit() {
        if (getWidth() <= 0) { fitted = LabText.Caption.NONE; return; }
        TextPaint measuring = new TextPaint(text);
        measuring.setTypeface(Theme.face(700));
        float dp = getResources().getDisplayMetrics().density, room = getWidth() - 2 * inset * dp;
        LabText.Sized sized = new LabText.Sized() {
            @Override public LabText.Measure at(float sp) { measuring.setTextSize(px(sp)); return measuring::measureText; }
            @Override public float line(float sp) { return px(sp) * LabText.LINE; }
        };
        fitted = LabText.caption(caption, name, room, getHeight(), dp, least, sized);
        text.setTextSize(px(fitted.sp));
    }
    private float px(float sp) { return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, getResources().getDisplayMetrics()); }
    private float line() { return text.getTextSize() * LabText.LINE; }
    /** The caption's height: its rule, the gap, the lines, the room under them. */
    private int captionHeight() {
        return fitted.count() == 0 ? 0 : Math.round(dp(LabText.CAPTION_CHROME) + fitted.count() * line());
    }
    private int sampleWidth() { return getWidth(); }
    private int sampleHeight() { return getHeight() - captionHeight(); }

    @Override protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        canvas.drawColor(Theme.PAPER);
        int sampleHeight = sampleHeight(), foot = h;
        if (picture != null) canvas.drawBitmap(picture, 0, 0, null);
        drawCaption(canvas, w, sampleHeight, foot);
        if (changed) {
            fill.setColor(Theme.GREY_800);
            fill.setAntiAlias(true);
            float r = dp(DOT) / 2f;
            canvas.drawCircle(w / 2f, foot - dp(DOT_UNDER) - r, r, fill);
            fill.setAntiAlias(false);
        }
        if (now) {
            fill.setColor(Theme.INK);
            int inset = dp(SwatchFace.TAB_INSET);
            canvas.drawRect(inset, h - dp(SwatchFace.TAB), w - inset, h, fill);
        }
    }
    /** Its caption's lines in ink, not grey-800: Brush Lab's swap tiles (the owner, 2026-10-21: legible, no grey). */

    boolean inkCaption;
    /**
     * Under the sample down to {@code foot}, on grey-200 (paper {@link #onPaper}; paper under an ink line {@link #ruled}),
     * the name (700, ink), then the caption's lines (400, grey-800, or ink).
     */
    private void drawCaption(Canvas canvas, int w, int sampleHeight, int foot) {
        if (fitted.count() == 0) return;
        fill.setColor(onPaper || ruled ? Theme.PAPER : Theme.GREY_200);
        canvas.drawRect(0, sampleHeight, w, foot, fill);
        if (ruled) { fill.setColor(Theme.INK); canvas.drawRect(0, sampleHeight, w, sampleHeight + dp(Theme.FINE), fill); }
        float x = dp(inset), y = sampleHeight + dp(Theme.BASE) + dp(4) - text.ascent();
        if (fitted.name != null) {
            text.setTypeface(Theme.face(700)); text.setColor(Theme.INK);
            canvas.drawText(fitted.name, x, y, text);
            y += line();
            if (fitted.name2 != null) { canvas.drawText(fitted.name2, x, y, text); y += line(); }
        }
        for (int i = 0; i < fitted.lines.size(); i++) {
            text.setTypeface(Theme.face(400)); text.setColor(inkCaption ? Theme.INK : Theme.GREY_800);
            canvas.drawText(fitted.lines.get(i), x, y, text);
            y += line();
        }
    }
    private int dp(float value) { return Ui.dp(getContext(), value); }
}
