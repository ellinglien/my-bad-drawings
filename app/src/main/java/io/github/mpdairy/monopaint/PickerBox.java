package io.github.mpdairy.monopaint;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import java.util.function.DoubleConsumer;

/**
 * The picker box, the owner's drawing (2026-10-27: "i'd like to try using it to replace the shade picker and size
 * select"): a box two cells wide cut by a diagonal from its top left to its foot right, its lower-left triangle the grey
 * strip ({@link GreyStrip#boxed}), its upper-right one the size slider ({@link SizeSlider}), each the whole box, lying
 * over each other; a pen goes to the one whose triangle it lands in ({@link DrawLayout#boxShade}). The drawing screen's
 * side bar holds it upright ({@link SwatchBar}); Brush Lab holds the same box turned a quarter, lying across (the owner,
 * 2026-10-29: "we can use the same horizontal picker from the toolbar in place of the pickers there").
 */
@SuppressLint("ViewConstructor")
final class PickerBox extends FrameLayout {
    final GreyStrip strip;
    final SizeSlider slider;
    /** The control this gesture drives: the one whose triangle the pen landed in, or nearer. */
    private View gesture;

    /** {@code hold}: the strip's long press, or null; {@code rub}: a rub on the strip, or null. */
    PickerBox(Context context, SelectionFeedback feedback, SizeSlider.Listener size, GreyStrip.Listener grey, Runnable hold, DoubleConsumer rub) {
        super(context);
        strip = new GreyStrip(context, feedback, grey, hold, rub);
        slider = new SizeSlider(context, feedback, size);
        strip.boxed = true;
        addView(strip, new LayoutParams(-1, -1));
        addView(slider, new LayoutParams(-1, -1));
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN)
            gesture = !strip.hidden && slider.shade(event.getX(), event.getY()) ? strip : slider;
        return gesture != null && gesture.dispatchTouchEvent(event);
    }

    /**
     * Marks {@code look}'s size and, with {@code greys}, its grey ({@code brush} null: none), and says them to a screen
     * reader. Asks Android for no frame: the caller puts the change on the panel, or invalidates.
     */
    void show(ToolSwatches.Look look, Brush brush, boolean greys) {
        double inUse = brush == null ? Double.NaN : look.darkness;
        strip.setContentDescription(brush == null ? "Greys, white to black" : "Greys, white to black: " + ToolSwatches.greyWords(inUse, ModularSizes.swatch(brush.parts, inUse)) + " in use");
        slider.setContentDescription(DrawLayout.sizeSaid(look.px));
        strip.show(greys ? inUse : Double.NaN);
        slider.show(ToolSwatches.position(look.px));
    }
}
