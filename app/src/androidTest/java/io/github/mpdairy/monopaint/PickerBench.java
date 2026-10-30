package io.github.mpdairy.monopaint;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.SystemClock;
import android.util.Log;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * The side column's pickers on the checks build's own drawing screen (ing.ell.mybaddrawings.checks, never the user's app):
 * a pen drag and a tap on the grey strip and on the size slider, a tap on a slot, the menu opened and closed, and Undo
 * and Redo of a stroke, sent as the pen sends them. For
 * each: the fast display's presents per move (their mode and area, {@link SelectionFeedback}), the leaf views Android
 * draws again in the column (each an e-ink update by the firmware), and the panel's frames ({@link PanelFrames}) during
 * the drag, after the lift and until it is quiet; and the readout dot's grey against the grey in use.
 * Argument: -e pickerBench true; -e pickerBench press, the slow buttons' presses alone (Menu and Play opened and
 * closed, three rounds); -e pickerBench pages, page turns on the page bar (a notebook of four pages in the checks
 * app's own folder: ‹ and › taps, a turn after a stroke). Lines go to logcat, tag InkBench.
 */
final class PickerBench {
    private static PaintActivity app;
    /** The panel's frame count ({@link PanelFrames}' counter, read through the shell: the app may not read it) and when it last moved. */
    private static volatile long frames = -1, framesMovedAt, firstMove = -1;
    private static volatile boolean polling;
    private static void poll(Instrumentation test) {
        polling = true;
        new Thread(() -> {
            while (polling) {
                long c = -1;
                try (java.io.InputStream in = new android.os.ParcelFileDescriptor.AutoCloseInputStream(test.getUiAutomation().executeShellCommand("grep ebc /proc/interrupts"))) {
                    String s = new String(readAll(in), "US-ASCII").trim();
                    String[] f = s.split("\\s+"); c = 0;
                    for (int i = 1; i < f.length && f[i].matches("\\d+"); i++) c += Long.parseLong(f[i]);
                } catch (Exception e) { c = -1; }
                if (c != frames) { frames = c; framesMovedAt = SystemClock.uptimeMillis(); if (firstMove == 0) firstMove = framesMovedAt; }
            }
        }).start();
    }
    private static byte[] readAll(java.io.InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(); byte[] b = new byte[512]; int r;
        while ((r = in.read(b)) > 0) out.write(b, 0, r); return out.toByteArray();
    }
    /** Waits until the panel drove no frame for {@code quietMs} (at most {@code maxMs}): {when it last moved, frames since {@code from}}. */
    private static long[] settle(long from, long quietMs, long maxMs) {
        long start = SystemClock.uptimeMillis();
        while (SystemClock.uptimeMillis() - Math.max(framesMovedAt, start) < quietMs && SystemClock.uptimeMillis() - start < maxMs) SystemClock.sleep(5);
        return new long[]{framesMovedAt, frames - from};
    }
    private static final List<String> redrawn = new ArrayList<>();
    /** The page bar's previews are listed among the views drawn again (the pages mode). */
    private static boolean withPreviews;
    private static int androidFrames;
    private static volatile long downAt;

    static void run(Instrumentation test, StringBuilder report, String mode) throws Exception {
        app = (PaintActivity) test.startActivitySync(new Intent(test.getTargetContext(), PaintActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        for (int i = 0; i < 100 && app.loading; i++) SystemClock.sleep(100);
        test.waitForIdleSync(); SystemClock.sleep(500);
        main(test, () -> {
            if (app.pad.document == null) call(app, "startNewDrawing", "bench");
            call(get(app, "notebooks"), "hide");
            app.inkWashBar.selectTool(0);
        });
        poll(test);
        // A mark on the page, so a pick on the strip offers no page shade (an offer is for a blank page).
        stroke(test, .5f);
        test.waitForIdleSync(); SystemClock.sleep(2500);
        View column = app.inkWashBar.view;
        View root = column.getRootView();
        ViewTreeObserver.OnPreDrawListener watch = () -> { androidFrames++; redrawn.add("@" + (SystemClock.uptimeMillis() - downAt)); collect(root); return true; };
        main(test, () -> root.getViewTreeObserver().addOnPreDrawListener(watch));
        View strip = (View) get(app.inkWashBar.swatches, "strip"), slider = (View) get(app.inkWashBar.swatches, "slider");
        ToolButton[] tools = (ToolButton[]) get(app.inkWashBar, "tools");
        line(report, "firmware " + android.os.Build.DISPLAY + ", fast binary controls " + DirectEink.fastBinaryControls());
        if ("slots".equals(mode)) {
            // What picks, a stroke, a size and a grey paint of the bar's rows: the presents, the rows Android draws again
            // and how many times a row's face is painted (SlotTile.Face.painted: Android's frames and the captures).
            View lasso = (View) get(app.inkWashBar, "lasso"), dropper = (View) get(app.inkWashBar.swatches, "dropper");
            // A row's selection is its side bar alone (ToolButton.markerArea over a SlotTile.Face).
            for (ToolButton button : new ToolButton[]{tools[0], tools[InkWash.ERASER], (ToolButton) lasso}) {
                Rect area = button.markerArea();
                line(report, (area.width() == Ui.dp(app, DrawLayout.SIDE_BAR) ? "" : "FAILED ") + "marker area " + area + " of " + button.getWidth() + "x" + button.getHeight());
            }
            for (int round = 0; round < 2; round++) {
                drag(test, report, "slot 2 tap", tools[1], .5f, .5f, 0);
                drag(test, report, "slot 1 tap", tools[0], .5f, .5f, 0);
                drag(test, report, "eraser tap", tools[InkWash.ERASER], .5f, .5f, 0);
                drag(test, report, "slot 1 tap", tools[0], .5f, .5f, 0);
                // A press slid off its row picks nothing: its bar (shown on the down, ToolButton.bar) goes again.
                drag(test, report, "slot 2 slid off", tools[1], .5f, 2.5f, 3);
                boolean[] kept = new boolean[2];
                main(test, () -> { kept[0] = tools[1].press.pressed || tools[1].marked; kept[1] = tools[0].marked && app.inkWash.selected == 0; });
                line(report, (kept[0] || !kept[1] ? "FAILED " : "") + "slid off: slot 2 pressed or marked " + kept[0] + ", slot 1 still picked " + kept[1]);
                drag(test, report, "lasso tap", lasso, .5f, .5f, 0);
                drag(test, report, "slot 1 tap", tools[0], .5f, .5f, 0);
                drag(test, report, "dropper tap", dropper, .5f, .5f, 0);
                drag(test, report, "dropper tap", dropper, .5f, .5f, 0);
                drag(test, report, "page stroke", app.pad, .3f + .1f * round, .35f + .1f * round, 10);
                drag(test, report, "slider tap", slider, .3f + .2f * round, .3f + .2f * round, 0);
                drag(test, report, "strip tap", strip, .4f + .2f * round, .4f + .2f * round, 0);
                stale(test, report, "round " + round, tools);
            }
            main(test, () -> root.getViewTreeObserver().removeOnPreDrawListener(watch));
            polling = false;
            app.finish();
            return;
        }
        if ("pages".equals(mode)) {
            // Page turns: the bar's presents and the views Android draws again there, the page bar's previews counted too.
            withPreviews = true;
            main(test, () -> { while (app.book.stacks().count() < 4) app.newPage(); });
            test.waitForIdleSync(); SystemClock.sleep(2500);
            Object turner = app.inkWashBar.turner;
            View previous = (View) get(turner, "previous"), next = (View) get(turner, "next");
            for (int round = 0; round < 2; round++) {
                for (int i = 0; i < 3; i++) drag(test, report, "previous tap", previous, .5f, .5f, 0);
                for (int i = 0; i < 3; i++) drag(test, report, "next tap", next, .5f, .5f, 0);
                // A stroke, its preview sampled (InkWashBar.THUMBNAILS_MS), then a turn off and back.
                stroke(test, .4f + .1f * round);
                test.waitForIdleSync(); SystemClock.sleep(2500);
                drag(test, report, "previous tap after a stroke", previous, .5f, .5f, 0);
                drag(test, report, "next tap back", next, .5f, .5f, 0);
            }
            main(test, () -> root.getViewTreeObserver().removeOnPreDrawListener(watch));
            polling = false;
            app.finish();
            return;
        }
        if ("press".equals(mode)) {
            // A slow button's press (ToolButton.slow): its first frame is the press on the down, then the menu.
            View dot = (View) get(app.inkWashBar.swatches, "readout");
            for (int round = 0; round < 3; round++) for (String name : new String[]{"menu", "play"}) {
                SystemClock.sleep(1500);
                drag(test, report, name + " open", (View) get(app.inkWashBar, name), .5f, .5f, 0);
                drag(test, report, name + " close", dot, .5f, .5f, 0);
            }
            main(test, () -> root.getViewTreeObserver().removeOnPreDrawListener(watch));
            polling = false;
            app.finish();
            return;
        }
        // SelectionFeedback.differences: two marks apart in one patch are two boxes, each with its 2 px border.
        Bitmap a = Bitmap.createBitmap(20, 60, Bitmap.Config.ARGB_8888), b = a.copy(Bitmap.Config.ARGB_8888, true);
        a.eraseColor(android.graphics.Color.WHITE); b.eraseColor(android.graphics.Color.WHITE);
        for (int y = 2; y < 10; y++) b.setPixel(3, y, android.graphics.Color.BLACK);
        for (int y = 40; y < 50; y++) b.setPixel(3, y, android.graphics.Color.BLACK);
        List<Rect> boxes = SelectionFeedback.differences(a, b);
        line(report, (boxes.equals(java.util.Arrays.asList(new Rect(1, 0, 6, 12), new Rect(1, 38, 6, 52))) ? "" : "FAILED ") + "differences " + boxes);
        // What an Android redraw of unchanged pixels costs the panel (the firmware's own update of the view).
        View readout = (View) get(app.inkWashBar.swatches, "readout");
        for (View v : new View[]{slider, strip, slider, readout, tools[0], app.pad}) {
            SystemClock.sleep(500);
            long f0 = frames, at = SystemClock.uptimeMillis();
            main(test, v::invalidate);
            long[] q = settle(f0, 400, 4000);
            line(report, "invalidate " + v.getClass().getSimpleName() + " " + v.getWidth() + "x" + v.getHeight() + " unchanged: panel frames " + q[1] + ", quiet " + (q[0] - at) + " ms");
            Rect g = new Rect(); boolean[] vis = {false};
            main(test, () -> vis[0] = v.getGlobalVisibleRect(g));
            line(report, "  global visible " + vis[0] + " " + g);
        }
        for (int round = 0; round < 2; round++) {
            drag(test, report, "strip drag", strip, .15f, .8f, 24);
            readout(test, report);
            drag(test, report, "slider drag", slider, .2f, .7f, 24);
            readout(test, report);
            drag(test, report, "strip tap", strip, .5f, .5f, 0);
            readout(test, report);
            drag(test, report, "slider tap", slider, .4f, .4f, 0);
            drag(test, report, "slot 2 tap", tools[1], .5f, .5f, 0);
            readout(test, report);
            stale(test, report, "slot 2 tap", tools);
            drag(test, report, "slot 1 tap", tools[0], .5f, .5f, 0);
            readout(test, report);
            stale(test, report, "slot 1 tap", tools);
            drag(test, report, "eraser tap", tools[InkWash.ERASER], .5f, .5f, 0);
            stale(test, report, "eraser tap", tools);
            drag(test, report, "slot 1 tap", tools[0], .5f, .5f, 0);
            stale(test, report, "slot 1 tap", tools);
        }
        // The rest of the drawing screen's buttons, as MonoPaint is timed beside it (2026-10-25): the menu opened and
        // closed (a tap off it, on the readout dot, which does nothing else), after a stroke (no Undo or Redo buttons since 2026-10-28).
        View menu = (View) get(app.inkWashBar, "menu");
        for (int round = 0; round < 2; round++) {
            // The panel quiet first: Redo's frames ran into the second round's opening.
            SystemClock.sleep(1500);
            drag(test, report, "menu open", menu, .5f, .5f, 0);
            drag(test, report, "menu close", readout, .5f, .5f, 0);
            stroke(test, .6f + .1f * round);
            test.waitForIdleSync(); SystemClock.sleep(2500);
        }
        main(test, () -> root.getViewTreeObserver().removeOnPreDrawListener(watch));
        // A picture of the screen as it ends, for the layout (the checks app's own folder, gone with it).
        android.graphics.Bitmap shot = test.getUiAutomation().takeScreenshot();
        if (shot != null) try (java.io.FileOutputStream out = new java.io.FileOutputStream(new java.io.File(app.getExternalFilesDir(null), "pickers.png"))) {
            shot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
        }
        polling = false;
        app.finish();
    }

    /** A short pen stroke across the page at {@code y} of its height. */
    private static void stroke(Instrumentation test, float y) throws Exception {
        int[] page = new int[2];
        main(test, () -> app.pad.getLocationOnScreen(page));
        long down = SystemClock.uptimeMillis();
        for (int i = 0; i <= 20; i++) {
            pen(test, i == 0 ? MotionEvent.ACTION_DOWN : i == 20 ? MotionEvent.ACTION_UP : MotionEvent.ACTION_MOVE, down,
                    page[0] + app.pad.getWidth() * (.3f + .02f * i), page[1] + app.pad.getHeight() * y);
            SystemClock.sleep(8);
        }
    }

    /** A pen down at {@code from} of the view's height, {@code moves} moves to {@code to}, a lift; what the panel did. */
    private static void drag(Instrumentation test, StringBuilder report, String name, View view, float from, float to, int moves) throws Exception {
        int[] at = new int[2];
        main(test, () -> view.getLocationOnScreen(at));
        // The picker box's two views lie over each other: the strip's tab room (left) and the slider's (right) reach each alone.
        // shortcut: right-handed column only; mirror these when the bench runs left-handed.
        float x = at[0] + view.getWidth() * (view instanceof GreyStrip ? .02f : view instanceof SizeSlider ? .98f : .5f);
        SelectionFeedback feedback = app.selectionFeedback;
        main(test, () -> { redrawn.clear(); androidFrames = 0; app.pad.redrawAsks = new ArrayList<>(); });
        long frames0 = frames, down = SystemClock.uptimeMillis();
        int painted0 = SlotTile.Face.painted;
        firstMove = 0; downAt = down;
        int submitted0 = feedback.submitted;
        StringBuilder modes = new StringBuilder();
        long area = 0;
        long[] firstFrameAt = {-1};
        for (int i = 0; i <= moves + 1; i++) {
            int action = i == 0 ? MotionEvent.ACTION_DOWN : i == moves + 1 ? MotionEvent.ACTION_UP : MotionEvent.ACTION_MOVE;
            float t = moves == 0 ? from : from + (to - from) * Math.min(i, moves) / moves;
            int before = feedback.submitted;
            pen(test, action, down, x, at[1] + view.getHeight() * t);
            test.waitForIdleSync();
            if (feedback.submitted > before) {
                Rect r = feedback.lastBufferRegion;
                area += (long) r.width() * r.height();
                modes.append(feedback.lastDisplayMode).append(':').append(r.width()).append('x').append(r.height()).append(feedback.submitted - before > 1 ? "+" + (feedback.submitted - before - 1) : "").append(' ');
            }
            if (action != MotionEvent.ACTION_UP) SystemClock.sleep(16);
        }
        long lift = SystemClock.uptimeMillis(), framesAtLift = frames;
        int liftPresents0 = feedback.submitted;
        // Background previews (a slot's stroke) arrive late: watch a while.
        long[] quiet = settle(framesAtLift, 400, 6000);
        test.waitForIdleSync();
        firstFrameAt[0] = firstMove > 0 ? firstMove - down : -1;
        int presents = feedback.submitted - submitted0, late = feedback.submitted - liftPresents0;
        if (late > 0) modes.append("late ").append(late).append(" last ").append(feedback.lastDisplayMode).append(':').append(feedback.lastBufferRegion.width()).append('x').append(feedback.lastBufferRegion.height());
        String[] drawn = new String[2]; int[] frames = new int[1];
        main(test, () -> { drawn[0] = String.join(",", redrawn); frames[0] = androidFrames; drawn[1] = asks(app.pad.redrawAsks); app.pad.redrawAsks = null; });
        line(report, String.format(java.util.Locale.ROOT,
                "%s: moves %d, presents %d (area %d px; %s), row faces painted %d, android frames %d redrawing [%s], page redraw asked by [%s], panel frames during %d, after lift %d, first frame %d ms, quiet %d ms after lift",
                name, moves, presents, area, modes.toString().trim(), SlotTile.Face.painted - painted0, frames[0], drawn[0], drawn[1],
                framesAtLift - frames0, quiet == null ? -1 : quiet[1], firstFrameAt[0],
                quiet == null ? -1 : quiet[0] - lift));
        SystemClock.sleep(400);
    }

    /**
     * Whether Android's own drawing of the column still matches what the fast display showed after {@code name}: the
     * column alone asked for a frame (as any later change in it does), the window's pixels read back (PixelCopy) against
     * the column drawn now, the ink in each tray row, the eraser's cell and the readout dot. A gathered update once left
     * Android's recorded drawing of the rows it changed as before ("kept jumping to liftith", the owner, 2026-10-24).
     */
    private static void stale(Instrumentation test, StringBuilder report, String name, ToolButton[] tools) throws Exception {
        View column = app.inkWashBar.view, dot = (View) get(app.inkWashBar.swatches, "readout");
        main(test, column::invalidate);
        test.waitForIdleSync(); SystemClock.sleep(300);
        int[] at = new int[2];
        Bitmap[] drawn = new Bitmap[1];
        main(test, () -> {
            column.getLocationInWindow(at);
            drawn[0] = Bitmap.createBitmap(column.getWidth(), column.getHeight(), Bitmap.Config.ARGB_8888);
            drawn[0].eraseColor(android.graphics.Color.WHITE);
            column.draw(new Canvas(drawn[0]));
        });
        Bitmap shown = Bitmap.createBitmap(column.getWidth(), column.getHeight(), Bitmap.Config.ARGB_8888);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        int[] result = {-1};
        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        main(test, () -> android.view.PixelCopy.request(app.getWindow(), new Rect(at[0], at[1], at[0] + column.getWidth(), at[1] + column.getHeight()), shown,
                r -> { result[0] = r; done.countDown(); }, handler));
        done.await(2, java.util.concurrent.TimeUnit.SECONDS);
        StringBuilder rows = new StringBuilder();
        boolean failed = result[0] != android.view.PixelCopy.SUCCESS;
        List<View> parts = new ArrayList<>();
        for (int i = 0; i < ToolSlots.COUNT; i++) parts.add(tools[i]);
        parts.add(tools[InkWash.ERASER]); parts.add(dot);
        for (View part : parts) {
            if (failed) break;
            Rect r = new Rect(0, 0, part.getWidth(), part.getHeight());
            ((ViewGroup) column).offsetDescendantRectToMyCoords(part, r);
            int a = ink(drawn[0], r), b = ink(shown, r);
            rows.append(a).append('/').append(b).append(' ');
            // A side bar is hundreds of pixels; scaling and dithering differ by a few.
            if (Math.abs(a - b) > 60) failed = true;
        }
        line(report, (failed ? "FAILED " : "") + "android frame after " + name + ": ink drawn/shown per row, eraser, dot: " + rows.toString().trim() + (result[0] != android.view.PixelCopy.SUCCESS ? " (no PixelCopy " + result[0] + ")" : ""));
        drawn[0].recycle(); shown.recycle();
    }
    private static int ink(Bitmap b, Rect r) {
        int n = 0;
        for (int y = Math.max(0, r.top); y < Math.min(b.getHeight(), r.bottom); y++)
            for (int x = Math.max(0, r.left); x < Math.min(b.getWidth(), r.right); x++) if ((b.getPixel(x, y) & 0xff) < 128) n++;
        return n;
    }

    /** The readout dot: the share of white in its middle against the grey in use's. */
    private static void readout(Instrumentation test, StringBuilder report) throws Exception {
        View dot = (View) get(app.inkWashBar.swatches, "readout");
        main(test, () -> {
            Bitmap b = Bitmap.createBitmap(dot.getWidth(), dot.getHeight(), Bitmap.Config.ARGB_8888);
            dot.draw(new Canvas(b));
            int cx = b.getWidth() / 2, cy = b.getHeight() / 2, white = 0, n = 0;
            for (int y = cy - 4; y < cy + 4; y++) for (int x = cx - 4; x < cx + 4; x++) { n++; if ((b.getPixel(x, y) & 0xff) > 127) white++; }
            b.recycle();
            ToolSwatches.Look look = app.inkWash.look(app.inkWash.selected);
            Brush brush = app.inkWash.brush(app.inkWash.selected);
            int tone = brush == null ? -1 : ModularSizes.swatch(brush.parts, look.darkness);
            line(report, "  readout: darkness " + String.format(java.util.Locale.ROOT, "%.3f", look.darkness) + " tone " + tone
                    + " (white " + (tone < 0 ? -1 : DotPattern.whiteCount(tone)) + "/64), dot middle white " + white + "/" + n
                    + ", px " + String.format(java.util.Locale.ROOT, "%.1f", look.px) + strip());
        });
    }

    /** The strip's state, to see why a drag on it presents nothing. */
    private static String strip() throws Exception {
        View s = (View) get(app.inkWashBar.swatches, "strip");
        java.lang.reflect.Method m = s.getClass().getDeclaredMethod("squareArea"); m.setAccessible(true);
        Rect area = (Rect) m.invoke(s);
        return "; strip inUse " + get(s, "inUse") + " marked " + get(s, "marked") + " bitmap " + (get(s, "strip") != null) + " area " + area
                + " visible " + PanelCoordinates.fullyVisible(s, area) + " size " + s.getWidth() + "x" + s.getHeight() + " busy " + app.busy();
    }
    /**
     * Who asked the page to be drawn again ({@link DrawingPad#redrawAsks}): each call's first app frames, once each; "none"
     * when DrawingPad is redrawn with no call (Android's own flags, or the firmware).
     */
    private static String asks(List<Throwable> asks) {
        if (asks == null || asks.isEmpty()) return "none";
        java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
        for (Throwable ask : asks) {
            StringBuilder at = new StringBuilder();
            int n = 0;
            boolean leading = true;
            for (StackTraceElement e : ask.getStackTrace()) {
                if (leading && e.getClassName().endsWith(".DrawingPad") && (e.getMethodName().equals("asked") || e.getMethodName().equals("invalidate"))) continue;
                leading = false;
                if (n++ == 5) break;
                if (at.length() > 0) at.append(" < ");
                at.append(e.getClassName().replaceAll(".*\\.", "")).append('.').append(e.getMethodName()).append(':').append(e.getLineNumber());
            }
            seen.add(at.toString());
        }
        return String.join("; ", seen);
    }
    private static void collect(View view) {
        if (view.getVisibility() != View.VISIBLE) return;
        if (view instanceof ViewGroup && ((ViewGroup) view).getChildCount() > 0) {
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) collect(((ViewGroup) view).getChildAt(i));
        } else if (view.isDirty() && (withPreviews || !view.getClass().getSimpleName().equals("Preview"))) redrawn.add(view.getClass().getSimpleName() + " " + view.getWidth() + "x" + view.getHeight());
    }

    private static void pen(Instrumentation test, int action, long down, float x, float y) {
        // Checked on the down alone: a tap off a menu closes it on the down, and the window has its focus back only later.
        if (action == MotionEvent.ACTION_DOWN && !app.hasWindowFocus() && !app.popupOpen()) throw new IllegalStateException("bench stopped: its screen lost the focus, no more pen events sent");
        MotionEvent.PointerProperties prop = new MotionEvent.PointerProperties(); prop.id = 0; prop.toolType = MotionEvent.TOOL_TYPE_STYLUS;
        MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords(); coords.x = x; coords.y = y; coords.pressure = action == MotionEvent.ACTION_UP ? 0 : .5f; coords.size = .1f;
        MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, 1, new MotionEvent.PointerProperties[]{prop}, new MotionEvent.PointerCoords[]{coords}, 0, 0, 1, 1, 0, 0, InputDevice.SOURCE_STYLUS, 0);
        try { test.getUiAutomation().injectInputEvent(event, true); } finally { event.recycle(); }
    }
    private static void line(StringBuilder report, String text) { Log.i("InkBench", text); report.append(text).append('\n'); }

    private interface Work { void run() throws Exception; }
    private static void main(Instrumentation test, Work work) throws Exception {
        Throwable[] error = {null};
        test.runOnMainSync(() -> { try { work.run(); } catch (Throwable e) { error[0] = e; } });
        if (error[0] != null) throw new Exception(error[0]);
    }
    private static Object get(Object owner, String name) throws Exception { Field f = owner.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(owner); }
    private static void call(Object owner, String name, String arg) throws Exception {
        java.lang.reflect.Method m = arg == null ? owner.getClass().getDeclaredMethod(name) : owner.getClass().getDeclaredMethod(name, String.class);
        m.setAccessible(true); if (arg == null) m.invoke(owner); else m.invoke(owner, arg);
    }
    private static void call(Object owner, String name) throws Exception { call(owner, name, null); }
}
