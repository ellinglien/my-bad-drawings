package io.github.mpdairy.monopaint;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.View;
import java.nio.ByteBuffer;
import java.util.function.Supplier;

/**
 * A tool on the Tools page (the owner's mockup, 2026-10-28), a toolbar slot or a Library card alike: at its top left an
 * art box ({@link #setArt}) with the owner's drawing of the tool, cut at its handle end and scaled to the box's width
 * as every other ({@link ToolsLayout#art}), or, for a brush with no drawing, its stroke at that size, either drawn in the
 * background ({@link SampleQueue}) and kept across openings ({@link #PICTURES}); under it the name on one line at its
 * left (13 sp, bold for the selected slot, the other slots grey: {@link #setChosen}), cut with … to fit. No frame, no grid lines. The one in use is underlined
 * across the art's width ({@link #setNow}), a slot changed on this visit a small grey dot at its name's right ({@link
 * #setChanged}). Drawn again only when what it shows changes (e-ink).
 */
@SuppressLint("ViewConstructor")
final class ToolCard extends View {
    /**
     * The art boxes' pictures drawn already, by what they show (a drawing's name, else the card's key) and their size in
     * px, kept while the app runs, so the Tools page opens again without drawing them: each a mask, ink where it is dark.
     * At most 4 MB, a Manta's art box about 35 KB.
     */
    private static final LruCache<String, Bitmap> PICTURES = new LruCache<String, Bitmap>(4 << 20) {
        @Override protected int sizeOf(String key, Bitmap picture) { return picture.getAllocationByteCount(); }
    };
    private final SampleQueue samples;
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint();
    /** The art box, dp. */
    private int artWidth, artHeight;
    /** What it shows ({@code key}), its name, its drawing's name (or null), the stroke's sampler, and the picture once drawn. */
    private String key, name = "", drawn;
    private LoopCard.Sample stroke;
    private Bitmap picture;
    /** Its words for a screen reader, made when asked ({@link #describe}), else null. */
    private Supplier<String> described;
    private boolean now, changed, bold, grey;

    ToolCard(Context context, SampleQueue samples) {
        super(context);
        this.samples = samples;
        text.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, TypeStyle.CAPTION.sp, getResources().getDisplayMetrics()));
    }

    /** The art box, {@code width × height} dp. */
    void setArt(int width, int height) { artWidth = width; artHeight = height; }
    /**
     * Shows {@code brush} as {@code name}: the owner's drawing of it ({@link DrawLayout#drawing}), else its stroke from
     * {@code stroke}, asked only when {@code key} (what it shows) changed.
     */
    void show(String key, Brush brush, Supplier<LoopCard.Sample> stroke, String name) {
        if (!name.equals(this.name)) { this.name = name; invalidate(); }
        if (key.equals(this.key)) return;
        this.key = key;
        drawn = DrawLayout.drawing(brush);
        this.stroke = drawn == null ? stroke.get() : null;
        request();
    }
    /** Its screen-reader words, made only when a screen reader asks (a brush's words take a while: a page of cards opens without them). */
    void describe(Supplier<String> said) { described = said; }
    @Override public CharSequence getContentDescription() { return described != null ? described.get() : super.getContentDescription(); }
    /** The one in use (the selected slot, the card holding its tool): underlined. */
    void setNow(boolean now) { if (now != this.now) { this.now = now; invalidate(); } }
    /** A slot: the selected one black, its name bold; the others' picture and name grey (the owner, 2026-10-28: "make the non-selected tools a little grey so it's clear that they're not selected"). */
    void setChosen(boolean chosen) { if (chosen != bold || chosen == grey) { bold = chosen; grey = !chosen; invalidate(); } }
    /** A slot whose brush changed on this visit: its dot. */
    void setChanged(boolean changed) { if (changed != this.changed) { this.changed = changed; invalidate(); } }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), MeasureSpec.getSize(heightSpec));
    }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); if (picture == null) request(); }
    // Never recycled: the picture is kept (PICTURES).
    @Override protected void onDetachedFromWindow() { super.onDetachedFromWindow(); samples.cancel(this); }
    /**
     * Its picture at the art box's size: the one kept, at once (a page opened before draws whole in its first frame), else
     * drawn in the background, the one shown till then staying.
     */
    private void request() {
        int w = dp(artWidth), h = dp(artHeight);
        if (key == null || w <= 0 || h <= 0) { samples.cancel(this); return; }
        String kept = (drawn != null ? drawn : key) + " " + w + "x" + h;
        Bitmap ready = PICTURES.get(kept);
        if (ready != null) { samples.cancel(this); if (ready != picture) { picture = ready; invalidate(); } return; }
        Context context = getContext();
        String art = drawn;
        LoopCard.Sample sample = stroke;
        samples.request(this, () -> mask(art != null ? drawing(context, art, w, h) : sample.pixels(w, h), w, h), made -> {
            PICTURES.put(kept, made);
            picture = made;
            invalidate();
        });
    }
    /** The owner's drawing {@code name} on paper in a {@code w × h} px art box, placed as {@link ToolsLayout#art}: greys. */
    private static int[] drawing(Context context, String name, int w, int h) {
        Bitmap paper = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        paper.eraseColor(Theme.PAPER);
        int id = context.getResources().getIdentifier(name, "drawable", context.getPackageName());
        if (id != 0) {
            // Its own copy: the drawable's state is shared with the drawing screen's, drawn on the main thread.
            Drawable art = context.getDrawable(id).mutate();
            float[] at = ToolsLayout.art(art.getIntrinsicWidth(), art.getIntrinsicHeight(), w, h);
            art.setBounds(Math.round(at[0]), Math.round(at[1]), Math.round(at[0] + at[2]), Math.round(at[1] + at[3]));
            art.draw(new Canvas(paper));
        }
        int[] greys = new int[w * h];
        paper.getPixels(greys, 0, w, 0, 0, w, h);
        paper.recycle();
        return greys;
    }
    /** {@code greys} ({@code w × h}) as a mask, ink where they are dark (a quarter of their size), drawn in its paint's colour. */
    private static Bitmap mask(int[] greys, int w, int h) {
        Bitmap mask = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8);
        int row = mask.getRowBytes();
        byte[] ink = new byte[row * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) ink[y * row + x] = (byte) (255 - (greys[y * w + x] & 0xff));
        mask.copyPixelsFromBuffer(ByteBuffer.wrap(ink));
        return mask;
    }

    @Override protected void onDraw(Canvas canvas) {
        int w = getWidth(), aw = dp(artWidth);
        canvas.drawColor(Theme.PAPER);
        int ink = grey ? GREY : Theme.INK;
        fill.setColor(ink);
        if (picture != null) canvas.drawBitmap(picture, 0, 0, fill);
        float dot = dp(LoopCard.DOT), room = w - dp(ToolsLayout.NAME_END) - (changed ? dot + dp(6) : 0);
        text.setTypeface(Theme.face(bold ? 700 : 400));
        text.setColor(ink);
        float baseline = dp(artHeight) + dp(ToolsLayout.NAME_GAP) - text.ascent();
        String shown = TextUtils.ellipsize(name, text, room, TextUtils.TruncateAt.END).toString();
        canvas.drawText(shown, 0, baseline, text);
        if (changed) {
            fill.setColor(Theme.GREY_800);
            fill.setAntiAlias(true);
            canvas.drawCircle(text.measureText(shown) + dp(6) + dot / 2, baseline + (text.ascent() + text.descent()) / 2, dot / 2, fill);
            fill.setAntiAlias(false);
        }
        if (now) {
            fill.setColor(Theme.INK);
            int bottom = getHeight() - dp(DrawLayout.MARK_CLEAR);
            canvas.drawRect(0, bottom - dp(DrawLayout.UNDERLINE), Math.min(w, aw), bottom, fill);
        }
    }
    /** The slots not selected: their picture's ink and their name in this solid grey (e-ink: no see-through). */
    static final int GREY = Theme.GREY_600;
    private int dp(float value) { return Ui.dp(getContext(), value); }
}
