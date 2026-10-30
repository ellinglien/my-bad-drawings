package io.github.mpdairy.monopaint;

/**
 * The side column's sand timer (the owner, 2026-10-27: "just have a sand timer icon, press it to have it open a menu and
 * you can set a timer"): a plain drawing timer, not Timed Pages. A pick starts it at once; it runs across pages, pauses
 * while the app is paused, and at zero says {@link #TIME_UP} and turns off. It is the app's alone: never in the notebook,
 * never the page's clock or its lock ({@link TimedPages}' are). Time is the caller's monotonic clock, ms. Pure, checked
 * on the desktop ({@code SandTimerChecks}).
 */
final class SandTimer {
    /** Its choices, s: Off, then Timed Pages' steps ({@link NotebookOptions#MINUTES}), said as {@link NotebookRules#timeLabel}. */
    static final int[] SECONDS = new int[1 + NotebookOptions.MINUTES.length];
    static { for (int i = 1; i < SECONDS.length; i++) SECONDS[i] = 60 * NotebookOptions.MINUTES[i - 1]; }
    /** Its icon's name, said by a screen reader. */
    static final String NAME = "Sand Timer";
    /** Its popup at zero, and the popup's one button. */
    static final String TIME_UP = "Time's up", DONE = "Done";

    private int seconds;
    /** The time counted before the last pause, ms, and when counting last started (−1: paused or off). */
    private long spent, since = -1;

    /** Set to {@code seconds} (0: off) at {@code now}, counting at once from the whole time. */
    void set(int seconds, long now) { this.seconds = Math.max(0, seconds); spent = 0; since = this.seconds > 0 ? now : -1; }
    /** The time it was set to, s; 0 while off. */
    int seconds() { return seconds; }
    boolean on() { return seconds > 0; }
    /** Counting stops (the app paused), the time so far kept. */
    void pause(long now) { if (since >= 0) { spent += Math.max(0, now - since); since = -1; } }
    /** Counting goes on (the app back). */
    void resume(long now) { if (seconds > 0 && since < 0) since = now; }
    /** The time left, ms; 0 while off. */
    long remaining(long now) {
        if (seconds == 0) return 0;
        return Math.max(0, seconds * 1000L - spent - (since >= 0 ? Math.max(0, now - since) : 0));
    }
    /** Whether it reached zero at {@code now}: true once, and it is off from then. */
    boolean ended(long now) {
        if (seconds == 0 || remaining(now) > 0) return false;
        set(0, now);
        return true;
    }
    /** How long until its countdown says something else or it ends, ms; −1 while off or paused. */
    long nextLook(long now) { return since < 0 ? -1 : Math.max(50, Math.min(remaining(now), NotebookRules.nextCountdown(remaining(now)))); }
}
