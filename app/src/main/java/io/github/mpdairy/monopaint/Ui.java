package io.github.mpdairy.monopaint;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.SeekBar;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.function.IntConsumer;

/** Small view helpers shared by the app's hand-built controls. */
final class Ui {
    private Ui() { }

    static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
    /** {@code pixels} in dp, rounded: {@link #dp}'s inverse. */
    static int dpOf(Context context, int pixels) {
        return Math.round(pixels / context.getResources().getDisplayMetrics().density);
    }

    /** How wide, in dp, the frame marking a selected layer, size or shade is. */
    static final int SELECTED_FRAME_DP = 3;
    private static final Paint FRAME = new Paint();
    static { FRAME.setColor(Color.BLACK); FRAME.setStyle(Paint.Style.STROKE); }
    /**
     * The one selected look for the layer thumbnails and the bar's size and shade choices: a black,
     * square-cornered frame {@link #SELECTED_FRAME_DP} wide, just outside the given rectangle.
     */
    static void selectedFrame(Context context, Canvas canvas, float left, float top, float right, float bottom) {
        int line = dp(context, SELECTED_FRAME_DP);
        FRAME.setStrokeWidth(line);
        float half = line / 2f;
        canvas.drawRect(Math.round(left) - half, Math.round(top) - half, Math.round(right) + half, Math.round(bottom) + half, FRAME);
    }

    /** The dashes outlining an empty place: an empty palette cell or saved brush. */
    static DashPathEffect emptyDashes(Context context) { return new DashPathEffect(new float[]{dp(context, 4), dp(context, 3)}, 0); }

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
    static boolean outside(View view, MotionEvent event) { return outside(view, event, 0); }
    /** {@link #outside(View, MotionEvent)} for the event's pointer {@code index}: raw positions of other pointers keep the first's offset. */
    static boolean outside(View view, MotionEvent event, int index) {
        float[] point = {event.getX(index) + event.getRawX() - event.getX(), event.getY(index) + event.getRawY() - event.getY()};
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
        // Inkwash: a palm (a contact as wide as FingerTool's) never closes one, whoever opened it.
        return !fingersOnly(event) || openedByFinger && widestFingerDp(event) <= FingerTool.PALM_DP;
    }
    /** Inkwash: the widest finger contact in {@code event}, dp (a palm's is wide). */
    static float widestFingerDp(MotionEvent event) {
        float widest = 0;
        for (int i = 0; i < event.getPointerCount(); i++)
            if (event.getToolType(i) == MotionEvent.TOOL_TYPE_FINGER) widest = Math.max(widest, event.getTouchMajor(i));
        return widest / android.content.res.Resources.getSystem().getDisplayMetrics().density;
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
    /**
     * One clean update of the whole window, every pixel driven again: the firmware's default update for Android frames,
     * which redrives even unchanged pixels (twice, with its blink: PickerBench, 2026-10-24). A forced mode 7 refresh
     * cannot clean (the driver passes by any pixel asked for the value it holds), and views under partial grey
     * ({@code setEinkUpdateMode(3, 7)}: the page, the column, the brush line) get one grey pass that leaves the screen
     * before ghosting through. So every view goes back to the firmware's default for one frame, the window is drawn again,
     * and {@code restore} puts the partial grey back once that frame has gone to the panel. For a screen coming back or
     * opening over another (the owner, 2026-10-24: "lots of eink ghosts", back from the Brush Lab or the Tools page).
     * The page alone too ({@code root} the page's view), after a pinch, a pan or the eraser ({@code DrawingPad.ghosted}).
     */
    static void cleanWindow(View root, String reason, Runnable restore) {
        resetEink(root);
        invalidateTree(root);
        // Posted behind the redraw it waits for, then the firmware's update time.
        root.post(() -> root.postDelayed(restore, CLEAN_MS));
        android.util.Log.i(ProbeActivity.TAG, "Clean window (" + reason + "): firmware default update over " + root.getClass().getSimpleName());
    }
    /**
     * As {@link #cleanWindow}, after asking the firmware for its full refresh (the side swipe's flash), which comes with
     * that redraw: {@code EinkManager.screenRefresh(false, 0)} (RefreshProbe; the owner saw it flash, 2026-10-26).
     */
    static void flashWindow(View root, String reason, Runnable restore) {
        // Off a Supernote there is no eink service: the clean redraw alone.
        try {
            Object eink = root.getContext().getSystemService("eink");
            if (eink != null) eink.getClass().getMethod("screenRefresh", boolean.class, int.class).invoke(eink, false, 0);
        } catch (ReflectiveOperationException | RuntimeException absent) { }
        cleanWindow(root, reason + ", flashed", restore);
    }
    /** How long the firmware's default update of a frame runs (about 500 ms) before partial grey comes back. */
    static final long CLEAN_MS = 600;
    private static void resetEink(View view) {
        // Off a Supernote the method is missing: the redraw alone.
        try { View.class.getMethod("resetEinkUpdateMode").invoke(view); } catch (ReflectiveOperationException | RuntimeException absent) { return; }
        if (view instanceof ViewGroup)
            for (int i = 0; i < ((ViewGroup)view).getChildCount(); i++) resetEink(((ViewGroup)view).getChildAt(i));
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

    /** {@code base} with all app text a fifth larger, native dialogs too, on top of the user's own font scale. */
    static Context largerText(Context base) {
        android.content.res.Configuration configuration = new android.content.res.Configuration(base.getResources().getConfiguration());
        configuration.fontScale *= LabText.SCALE;
        return base.createConfigurationContext(configuration);
    }
}
