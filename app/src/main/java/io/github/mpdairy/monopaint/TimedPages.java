package io.github.mpdairy.monopaint;

/**
 * Timed Pages (docs/archive/superpowers/specs/2026-10-19-notebook-options-design.md, "Rules"): the clock of the page being drawn on.
 * The time starts at the page's first stroke ({@link #strokeBegins}) and counts only real drawing time on the page, so it
 * <b>pauses</b> whenever the page or the app isn't showing (the app paused, the shelf or the notebook's pages over it, another
 * page turned to) and counts on when the page shows again; time is taken from the monotonic clock the caller passes, never
 * the wall clock, so a notebook closed for a week has lost nothing. At zero the page's clock runs out ({@link PageClock#lock}):
 * set on the page itself ({@link ToneDocument#setClock}), so it is in the page's record and survives a save and a reopen.
 * The page is locked by it only under the notebook's Lock Pages ({@link PageClock#locks}); otherwise Time's up is a guide and
 * the page stays editable (the owner, 2026-10-27). A stroke still in progress at zero finishes first. A page run out never
 * counts again, whatever Timed Pages is set to later. Pure and driven by the caller's time, checked on the desktop
 * ({@code NotebookRulesChecks}).
 */
final class TimedPages {
    /** What {@link #tick} found. */
    enum State { IDLE, RUNNING, BLINKING, TIME_UP }

    private ToneDocument page;
    private int seconds;
    /** When the page last started counting (the caller's clock, ms), or −1 while it is paused or not counting. */
    private long since = -1;

    /** Timed Pages is {@code seconds} (0: off) from {@code now}: counting stops and restarts under the new time. */
    void configure(int seconds, long now) {
        if (seconds == this.seconds) return;
        pause(now);
        this.seconds = seconds;
    }
    int seconds() { return seconds; }
    long limit() { return seconds * 1000L; }
    boolean running() { return since >= 0; }
    ToneDocument page() { return page; }

    /**
     * The page now is {@code current}, and whether it is {@code showing} (the app resumed and the drawing screen up):
     * told on every tick and whenever the page or the screen changes. A page left keeps the time it was given.
     */
    void sync(ToneDocument current, boolean showing, long now) {
        if (current != page) { pause(now); page = current; }
        if (showing) resume(now); else pause(now);
    }
    /** The pen or a finger starts a mark on the shown page: its first stroke starts its timer. */
    void strokeBegins(long now) {
        if (page == null || seconds == 0 || page.clock().locked) return;
        if (!page.clock().started) page.setClock(page.clock().start());
        if (since < 0) since = now;
    }
    /** Counting stops, the time so far given to the page (a change to save). */
    void pause(long now) {
        if (since < 0) return;
        long spent = page.clock().spent + Math.max(0, now - since);
        since = -1;
        if (!page.clock().locked) page.setClock(page.clock().spentTo(spent));
    }
    /** The time so far is given to the page and counting goes on: before the page is saved, so its record holds it. */
    void commit(long now) {
        if (since < 0) return;
        pause(now);
        resume(now);
    }
    private void resume(long now) {
        if (since >= 0 || seconds == 0 || page == null || page.clock().locked || !page.clock().started) return;
        since = now;
    }

    /** The time the page has left, ms: all of it before the first stroke (or with Timed Pages off, 0), none once run out. */
    long remaining(long now) {
        if (page == null || seconds == 0) return 0;
        if (page.clock().locked) return 0;
        long used = page.clock().spent + (since >= 0 ? Math.max(0, now - since) : 0);
        return Math.max(0, limit() - used);
    }
    /** Whether the page's number is hidden now, in the blink window. */
    boolean numberHidden(long now) { return since >= 0 && NotebookRules.numberHidden(remaining(now), seconds); }
    /**
     * Looks at the clock. At zero the page's clock runs out, unless {@code drawing} (a stroke is down: it finishes first, and
     * the clock runs out at the next look). {@link State#TIME_UP} once, as it does.
     */
    State tick(long now, boolean drawing) {
        if (since < 0) return State.IDLE;
        long left = remaining(now);
        if (left <= 0) {
            if (drawing) return State.BLINKING;
            page.setClock(page.clock().lock(limit()));
            since = -1;
            return State.TIME_UP;
        }
        return NotebookRules.blinks(left, seconds) ? State.BLINKING : State.RUNNING;
    }
    /** How long until the clock next needs a look, ms: the next blink, the countdown's next change or zero; −1 when nothing is counting. */
    long nextLook(long now) {
        if (since < 0) return -1;
        long left = remaining(now);
        if (left <= 0) return 250;
        return Math.max(50, Math.min(left, Math.min(NotebookRules.nextBlink(left, seconds), NotebookRules.nextCountdown(left))));
    }
}
