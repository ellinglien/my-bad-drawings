package io.github.mpdairy.monopaint;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.drawable.GradientDrawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.SeekBar;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.function.IntConsumer;
import io.github.mpdairy.fasteink.PanelCoordinates;

/** Small view helpers shared by the app's hand-built controls. */
final class Ui {
    private Ui() { }

    static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    /** A white panel or control background with a solid outline. */
    static GradientDrawable outline(int strokeWidth, int strokeColor, float radius) {
        return outline(Color.WHITE, strokeWidth, strokeColor, radius);
    }
    static GradientDrawable outline(int fill, int strokeWidth, int strokeColor, float radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill); drawable.setStroke(strokeWidth, strokeColor); drawable.setCornerRadius(radius);
        return drawable;
    }

    /** Whether a screen event lies outside a view, including views turned with the app. */
    static boolean outside(View view, MotionEvent event) {
        float[] point = {event.getRawX(), event.getRawY()};
        Matrix inverse = new Matrix();
        PanelCoordinates.fromView(view).invert(inverse); inverse.mapPoints(point);
        return point[0] < 0 || point[1] < 0 || point[0] >= view.getWidth() || point[1] >= view.getHeight();
    }

    /** Whether every pointer is a finger, so no pen is involved. */
    static boolean fingersOnly(MotionEvent event) {
        for (int i = 0; i < event.getPointerCount(); i++)
            if (event.getToolType(i) != MotionEvent.TOOL_TYPE_FINGER) return false;
        return true;
    }

    /** Whether the latest press on any of the app's windows used fingers only. */
    private static boolean fingerPress;
    /**
     * Notes which tools make up a press; every window passes its touch events here. A pen
     * joining a resting hand makes it a pen press.
     */
    static void notePress(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) fingerPress = fingersOnly(event);
        else if (action == MotionEvent.ACTION_POINTER_DOWN) fingerPress &= fingersOnly(event);
    }
    /** Whether the press now opening a panel used fingers only. */
    static boolean fingerPress() { return fingerPress; }
    /** Notes the presses a dialog's window receives, which never reach the activity. */
    static void notePresses(Window window) {
        Window.Callback callback = window.getCallback();
        if (callback == null || Proxy.isProxyClass(callback.getClass())) return;
        window.setCallback((Window.Callback)Proxy.newProxyInstance(Window.Callback.class.getClassLoader(),
                new Class<?>[]{Window.Callback.class}, (proxy, method, args) -> {
                    if (method.getName().equals("dispatchTouchEvent")) notePress((MotionEvent)args[0]);
                    try { return method.invoke(callback, args); }
                    catch (InvocationTargetException error) { throw error.getCause(); }
                }));
    }

    /**
     * Whether a touch outside a panel closes it. The pen always does; fingers only close a
     * panel a finger opened, so a hand resting while the pen works a panel leaves it open.
     */
    static boolean closesPanel(boolean openedByFinger, MotionEvent event) {
        return openedByFinger || !fingersOnly(event);
    }
    /** Whether a touch outside a panel is a resting hand to swallow instead of closing it. */
    static boolean restingOutside(View panel, MotionEvent event, boolean openedByFinger) {
        return !closesPanel(openedByFinger, event)
                && (event.getActionMasked() == MotionEvent.ACTION_OUTSIDE || outside(panel, event));
    }

    static void detach(View view) {
        if (view.getParent() instanceof ViewGroup) ((ViewGroup)view.getParent()).removeView(view);
    }

    static void invalidateTree(View view) {
        view.invalidate();
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup)view;
            for (int i = 0; i < group.getChildCount(); i++) invalidateTree(group.getChildAt(i));
        }
    }

    static void setDimmed(View view, boolean enabled, float dimmed) {
        view.setEnabled(enabled); view.setAlpha(enabled ? 1 : dimmed);
    }

    /** Reports every progress change, from the user or from code. */
    static void onProgress(SeekBar bar, IntConsumer change) {
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar slider, int value, boolean user) { change.accept(value); }
            @Override public void onStartTrackingTouch(SeekBar slider) { }
            @Override public void onStopTrackingTouch(SeekBar slider) { }
        });
    }
}
