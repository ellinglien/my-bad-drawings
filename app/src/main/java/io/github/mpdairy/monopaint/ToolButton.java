package io.github.mpdairy.monopaint;

import android.annotation.SuppressLint;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.widget.Button;

/**
 * A square icon control used by the toolbar, color bar, header and panels.
 * It draws a centered icon plus optional selection markers, caption, favorite dots
 * and a settings arrow, all of which can be updated through {@link SelectionFeedback}.
 */
@SuppressLint("ViewConstructor")
final class ToolButton extends Button {
    /** Draws extra glyphs on top of the button, such as the zoom lock. */
    interface Overlay { void draw(Canvas canvas, ToolButton button); }

    private final ControlHost host;
    final PressOutline press;
    /** Selected state, shown by {@link #markerArea()}. */
    boolean marked;
    /** Show the selection dot even when fast feedback is disabled. */
    boolean alwaysDot;
    boolean markerLeft, markerBelow;
    /** The icon stays upright when the header strip is turned. */
    boolean headerIcon;
    /** Selection draws a full outline instead of a dot. */
    boolean selectionOutline;
    int iconHalf = 14, iconOffset, presetNumber;
    String presetId;
    String caption;
    Overlay overlay;
    Drawable settingsArrow;
    private Drawable centerIcon;
    /** Drawn instead of the icon while unmarked, so the icon itself shows the selection, with no dot; or null. */
    private Drawable unmarkedIcon;
    private int iconResource;
    private final Paint markerPaint = new Paint();
    private final ControlRaster controlRaster = new ControlRaster();

    ToolButton(android.content.Context context, ControlHost host) {
        super(context);
        this.host = host; press = new PressOutline(this, null);
        // Flat icon controls have no pressed elevation to animate after a tap.
        setStateListAnimator(null); setElevation(0);
    }
    private int dp(float value) { return Ui.dp(getContext(), value); }

    @Override public void draw(Canvas canvas) {
        controlRaster.draw(canvas,this,super::draw);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        press.onTouch(event, isEnabled() && !host.busy());
        boolean handled = super.onTouchEvent(event);
        if (event.getActionMasked() == MotionEvent.ACTION_UP) press.releaseLater();
        return handled;
    }
    @Override public boolean performClick() {
        if (press.feedback != null && (!isEnabled() || host.busy())) return false;
        press.set(true);
        boolean handled = super.performClick();
        press.releaseLater();
        return handled;
    }
    @Override protected void onDetachedFromWindow() { press.detached(); controlRaster.close(); super.onDetachedFromWindow(); }
    @Override public boolean isSelected() {
        return selectionOutline || settingsArrow != null || alwaysDot ? marked : super.isSelected();
    }
    void iconOnly(int resource) {
        if (iconResource == resource) return;
        iconOnly(getContext().getDrawable(resource), null);
        iconResource = resource;
    }
    /**
     * An icon drawn as {@code marked} while selected and as {@code unmarked} otherwise, so it
     * shows the selection itself, with no dot; or with {@code unmarked} null, always the same.
     */
    void iconOnly(Drawable marked, Drawable unmarked) {
        // Changing the selected variant only changes the drawable; keep the
        // toolbar's measured geometry stable while its settings panel is open.
        if (centerIcon == null) {
            setText(""); setCompoundDrawables(null, null, null, null); setMinWidth(0); setMinimumWidth(0);
            setTextColor(Color.BLACK); setHintTextColor(Color.BLACK); setLinkTextColor(Color.BLACK);
        }
        iconResource = 0; centerIcon = marked; unmarkedIcon = unmarked; invalidate();
    }
    /** Selects or deselects the control through the fast display path. */
    void mark(boolean selected) {
        if (marked == selected) return;
        host.selectionFeedback().update(this, markerArea(), () -> {
            marked = selected;
            sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
        });
    }
    /** The pixels that change when {@link #marked} changes. */
    Rect markerArea() {
        if (selectionOutline || unmarkedIcon != null) return new Rect(0, 0, getWidth(), getHeight());
        if (markerBelow) { Rect bounds = new Rect(); belowSquare().roundOut(bounds); return bounds; }
        return new Rect(markerLeft ? 0 : Math.max(0, getWidth()-dp(24)), 0,
                markerLeft ? Math.min(getWidth(), dp(24)) : getWidth(), Math.min(getHeight(), dp(24)));
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // Single-line text scrolls the canvas; overlays belong to the control's own bounds.
        int save = canvas.save();
        canvas.translate(getScrollX(), getScrollY());
        try { drawOverlays(canvas); } finally { canvas.restoreToCount(save); }
    }
    private void drawOverlays(Canvas canvas) {
        if (centerIcon != null) {
            int half = dp(iconHalf), x = getWidth()/2+dp(iconOffset), y = getHeight()/2-(caption != null ? dp(9) : 0);
            canvas.save();
            if (headerIcon) canvas.rotate(-host.toolbarTurn(), x, y);
            Drawable icon = marked || unmarkedIcon == null ? centerIcon : unmarkedIcon;
            icon.setBounds(x-half, y-half, x+half, y+half); icon.draw(canvas);
            canvas.restore();
        }
        if (caption != null) {
            markerPaint.setColor(Color.BLACK); markerPaint.setTextAlign(Paint.Align.CENTER);
            markerPaint.setTextSize(dp(12)); markerPaint.setAntiAlias(true);
            canvas.drawText(caption, getWidth()/2f, getHeight()-dp(7), markerPaint);
            markerPaint.setAntiAlias(false);
        }
        if (overlay != null) overlay.draw(canvas, this);
        if (presetId != null) drawPresetDots(canvas);
        if (settingsArrow != null) {
            int x = getWidth()-dp(10), y = getHeight()/2;
            settingsArrow.setBounds(x-dp(8), y-dp(10), x+dp(8), y+dp(10)); settingsArrow.draw(canvas);
        }
        if (press.pressed || (selectionOutline && marked)) press.drawBox(canvas);
        if (selectionOutline || unmarkedIcon != null || !marked || (!alwaysDot && !host.selectionFeedback().enabled)) return;
        markerPaint.setColor(Color.BLACK);
        if (markerBelow) { canvas.drawRect(belowSquare(), markerPaint); return; }
        float[] center = markerPoint();
        markerPaint.setColor(Color.WHITE); canvas.drawCircle(center[0], center[1], dp(6), markerPaint);
        markerPaint.setColor(Color.BLACK); canvas.drawCircle(center[0], center[1], dp(4), markerPaint);
    }
    /**
     * The {@link #markerBelow} square, where the shade strip's marker runs: along the color
     * bar's canvas side, so selecting this control looks like the marker moved onto it. It
     * stays with the bar as the bar turns, beside the upright icon in landscape.
     */
    private RectF belowSquare() {
        float top = host.colorMarkersOnTop() ? dp(1) : getHeight()-dp(7);
        return new RectF(getWidth()/2f-dp(3), top, getWidth()/2f+dp(3), top+dp(6));
    }
    /** A point the selection marker paints solid black, in this view's coordinates. */
    float[] markerPoint() {
        if (selectionOutline) return new float[]{getWidth()/2f, dp(PressOutline.BOX_INSET_DP)};
        if (!markerBelow) return new float[]{markerLeft ? dp(12) : getWidth()-dp(12), dp(12)};
        return new float[]{getWidth()/2f, belowSquare().centerY()};
    }
    /** Favorites show their number as dots, so similar tools stay distinguishable. */
    private void drawPresetDots(Canvas canvas) {
        markerPaint.setColor(Color.BLACK);
        float center = getWidth()/2f+dp(iconOffset);
        float halfWidth = Math.max(0, Math.min(center, getWidth()-center)-dp(9));
        int columns = Math.max(1, Math.min(10, 1+(int)(2*halfWidth/dp(6))));
        int rows = (presetNumber+columns-1)/columns;
        // Keep identification dots below the centered icon and inside its selection box.
        float bottom = getHeight()-dp(7), top = getHeight()/2f+dp(iconHalf+3);
        float band = Math.max(1, bottom-top);
        float spacing = rows > 1 ? Math.min(dp(6), band/(rows-1)) : dp(6);
        float radius = Math.min(dp(2), Math.min(band, spacing/3));
        for (int i = 0; i < presetNumber; i++) {
            int row = i/columns, count = Math.min(columns, presetNumber-row*columns);
            float x = center+(i%columns-(count-1)/2f)*dp(6);
            canvas.drawCircle(x, getHeight()-dp(7)-(rows-1-row)*spacing, radius, markerPaint);
        }
    }
}
