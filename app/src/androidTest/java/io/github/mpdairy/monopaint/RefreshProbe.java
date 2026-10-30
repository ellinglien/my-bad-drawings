package io.github.mpdairy.monopaint;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Which of the system's own full refreshes flashes the panel when the app asks (the owner watches the screen and
 * names the letter; 2026-10-26, the shell's {@code service call eink 6} didn't). Bench build only, its own window.
 */
final class RefreshProbe {
    private RefreshProbe() { }

    static void run(Instrumentation in, StringBuilder report) throws Exception {
        Intent intent = new Intent(in.getTargetContext(), ProbeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Activity activity = in.startActivitySync(intent);
        TextView label = new TextView(activity);
        label.setTextSize(64); label.setGravity(Gravity.CENTER); label.setTextColor(0xff000000); label.setBackgroundColor(0xffffffff);
        in.runOnMainSync(() -> activity.setContentView(label));
        show(in, label, "Refresh test\nwatch for a flash\nstarting in 5 s"); SystemClock.sleep(5000);
        Object eink = activity.getSystemService("eink");
        report.append("eink service: ").append(eink == null ? "none" : eink.getClass().getName()).append('\n');
        // Run 2 (the owner saw A flash only at the next label): A, then a redraw straight away.
        try_(in, label, report, "A again", () -> {
            call(eink, "screenRefresh", new Class<?>[]{boolean.class, int.class}, false, 0);
            show(in, label, "A again\nnow");
        });
        if (eink != null) { show(in, label, "Done"); SystemClock.sleep(1500); activity.finish(); return; }
        try_(in, label, report, "A", () -> call(eink, "screenRefresh", new Class<?>[]{boolean.class, int.class}, false, 0));
        try_(in, label, report, "B", () -> call(eink, "screenRefresh", new Class<?>[]{boolean.class, int.class}, true, 0));
        try_(in, label, report, "C", () -> {
            Class<?> c = Class.forName("android.view.ViewRootImplEink");
            Method m = c.getDeclaredMethod("forceFullScreenUpdate", View.class, boolean.class);
            m.setAccessible(true);
            Object target = Modifier.isStatic(m.getModifiers()) ? null : label.getRootView().getParent();
            in.runOnMainSync(() -> { try { m.invoke(target, label, true); } catch (Exception e) { throw new RuntimeException(e); } });
        });
        try_(in, label, report, "D", () -> {
            call(eink, "sendOneFullFrame", new Class<?>[0]);
            in.runOnMainSync(label::invalidate);
        });
        show(in, label, "Done"); SystemClock.sleep(1500);
        activity.finish();
    }

    private interface Step { void run() throws Exception; }

    private static void try_(Instrumentation in, TextView label, StringBuilder report, String name, Step step) {
        show(in, label, name + "\nin 3 s"); SystemClock.sleep(3000);
        try { step.run(); report.append(name).append(": called\n"); }
        catch (Throwable e) { report.append(name).append(": failed ").append(e).append('\n'); }
        SystemClock.sleep(5000);
    }

    private static void show(Instrumentation in, TextView label, String text) { in.runOnMainSync(() -> label.setText(text)); }

    private static void call(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        Method m = target.getClass().getMethod(name, types);
        m.setAccessible(true);
        m.invoke(target, args);
    }
}
