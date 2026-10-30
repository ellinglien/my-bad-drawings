package io.github.mpdairy.monopaint;

/** The two-finger hold on the edge strip: Trash on while held, the previous mode back after, a loop in progress finishing first. */
public final class EdgeHoldChecks {
    public static void main(String[] args) {
        EdgeHold h = new EdgeHold();
        EdgeHold.Act none = EdgeHold.Act.NONE, enter = EdgeHold.Act.ENTER, leave = EdgeHold.Act.LEAVE;
        // Both down, then up (either key first).
        check(h.key(301, true, false, false) == none, "One finger: nothing yet");
        check(h.key(300, true, false, false) == enter, "Both down: the Trash");
        check(h.key(301, false, false, false) == leave, "Either up: leave");
        check(h.key(300, false, false, false) == none, "The other up: nothing more");
        h.key(300, true, false, false);
        check(h.key(301, true, false, false) == enter && h.key(300, false, false, false) == leave, "300 first, 300 up first");
        h.key(301, false, false, false);
        // Only 301 (a slide): nothing.
        check(h.key(301, true, false, false) == none && h.key(301, false, false, false) == none, "A slide: nothing");
        // Key repeats.
        h.key(301, true, false, false); h.key(300, true, false, false);
        check(h.key(300, true, true, false) == none && h.key(301, true, true, false) == none, "Repeats ignored");
        check(h.key(301, false, true, false) == none, "An up repeat ignored too");
        check(h.key(301, false, false, false) == leave, "Still held until the real up");
        h.key(300, false, false, false);
        // Up mid-loop: the loop acts first.
        h.key(301, true, false, false); h.key(300, true, false, false);
        check(h.key(300, false, false, true) == none, "Up mid-loop: wait");
        check(h.loopEnded() == leave && h.loopEnded() == none, "The loop acted: leave once");
        h.key(301, false, false, false);
        check(h.key(301, false, false, false) == none, "Nothing left over");
        // Held again before the loop acted: the same hold goes on, left at the next lift.
        h.key(301, true, false, false); h.key(300, true, false, false); h.key(301, false, false, true);
        check(h.key(301, true, false, true) == none, "Held again while waiting: no second Trash");
        check(h.loopEnded() == none, "The loop acting does not leave a hold that goes on");
        check(h.key(300, false, false, false) == leave, "The next lift leaves");
        h.key(301, false, false, false);
        // Trash already on is the bar's to keep (InkWashBar.edgeTrash): the hold reports the same acts whatever the mode.
        check(h.key(300, true, false, false) == none && h.key(301, true, false, false) == enter, "Acts do not depend on the mode");
        System.out.println("PASS: edge hold: both keys enter, either leaves; a slide does nothing; repeats ignored; a loop in progress finishes before leaving; held again keeps one hold");
    }

    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError("FAIL: " + what); }
}
