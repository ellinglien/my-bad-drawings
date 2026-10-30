package io.github.mpdairy.monopaint;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** Brush Lab's switch, as the mockup's: a 52 × 26 dp square box, its 18 dp knob black at the left when off, white at the right on black when on. */
@SuppressLint("ViewConstructor")
final class SquareSwitch extends View {
    private final Paint paint = new Paint();
    boolean checked;

    SquareSwitch(Context context, boolean checked) { super(context); this.checked = checked; }
    @Override protected void onMeasure(int widthSpec, int heightSpec) { setMeasuredDimension(dp(52), dp(26)); }
    @Override protected void onDraw(Canvas canvas) {
        boolean enabled = isEnabled(), on = checked && enabled;
        int w = getWidth(), h = getHeight(), border = dp(2), inset = dp(4), knob = dp(18);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(enabled ? Theme.INK : Theme.GREY_400);
        canvas.drawRect(0, 0, w, h, paint);
        paint.setColor(on ? Theme.INK : Theme.PAPER);
        canvas.drawRect(border, border, w - border, h - border, paint);
        paint.setColor(on ? Theme.PAPER : enabled ? Theme.INK : Theme.GREY_400);
        int left = checked ? w - inset - knob : inset;
        canvas.drawRect(left, inset, left + knob, inset + knob, paint);
    }
    private int dp(float value) { return Ui.dp(getContext(), value); }
}
