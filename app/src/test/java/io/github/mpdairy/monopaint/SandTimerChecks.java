package io.github.mpdairy.monopaint;

import java.util.Arrays;

/**
 * The side column's sand timer ({@link SandTimer}): its choices, its countdown from the pick on, its pause while the app is
 * paused, its zero once, and that it holds nothing of a page (no clock, no lock, no notebook).
 */
public final class SandTimerChecks {
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    public static void main(String[] args) {
        String[] said = new String[SandTimer.SECONDS.length];
        for (int i = 0; i < said.length; i++) said[i] = NotebookRules.timeLabel(SandTimer.SECONDS[i]);
        check(Arrays.equals(said, new String[]{"Off", "1 min", "3 min", "5 min", "10 min", "30 min"}), "Its choices, Timed Pages' steps: " + Arrays.toString(said));

        SandTimer t = new SandTimer();
        check(!t.on() && t.remaining(0) == 0 && t.nextLook(0) == -1 && !t.ended(0), "Off at first: no countdown, nothing to look at");
        // A pick counts at once, with no stroke.
        t.set(180, 1000);
        check(t.on() && t.remaining(1000) == 180_000 && NotebookRules.countdown(t.remaining(31_000)).equals("2:30"), "Counts from the pick: " + t.remaining(31_000));
        check(t.nextLook(31_000) == 10_000, "Its next look when the countdown changes: " + t.nextLook(31_000));
        // Paused with the app: no time passes.
        t.pause(61_000);
        check(t.remaining(500_000) == 120_000 && t.nextLook(500_000) == -1, "Paused, it keeps its time");
        t.resume(500_000);
        check(t.remaining(560_000) == 60_000 && !t.ended(560_000), "Resumed, it counts on");
        // A new pick starts over; Off stops it.
        t.set(60, 600_000);
        check(t.remaining(600_000) == 60_000, "A new pick starts over");
        check(!t.ended(659_999) && t.ended(660_000) && !t.on() && !t.ended(700_000), "Ends once, at zero, then off");
        t.set(300, 0); t.set(0, 10);
        check(!t.on() && !t.ended(1_000_000), "Off stops it, no popup");

        // A page's clock and lock are Timed Pages': the sand timer holds no page, no clock and no notebook.
        for (java.lang.reflect.Field f : SandTimer.class.getDeclaredFields())
            check(f.getType().isPrimitive() || f.getType() == int[].class || f.getType() == String.class, "Holds nothing of a page: " + f);
        for (java.lang.reflect.Method m : SandTimer.class.getDeclaredMethods())
            for (Class<?> p : m.getParameterTypes()) check(p.isPrimitive(), "Takes nothing of a page: " + m);
        System.out.println("PASS: the sand timer: Off and Timed Pages' steps, counting from the pick, paused with the app, ending once at zero and then off, holding no page, clock or lock");
    }
}
