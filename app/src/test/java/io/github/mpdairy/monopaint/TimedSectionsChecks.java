package io.github.mpdairy.monopaint;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

/**
 * Timed Sections ({@link TimedSections}): the Diary Page's boxes timed in order from a start, a skip ahead, the sections
 * resumed from the page's record after a restart, the one-day lock and its fence (a stroke over a locked box put back at its
 * finish), the record's round trip, and the times read from the prompts file. {@code write <file>} writes a page with started
 * sections for the version before them ({@code OlderSectionsCheck}).
 */
public final class TimedSectionsChecks {
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
    static final long T0 = 1_760_000_000_000L, DAY = 24L * 3600_000L;
    static final int[] DIARY = {150, 150, 30, 30};

    public static void main(String[] args) throws Exception {
        if (args.length == 2 && args[0].equals("write")) {
            try (java.io.OutputStream out = new java.io.FileOutputStream(args[1])) { DocumentCodec.write(out, page().layerSnapshot()); }
            return;
        }
        template();
        sequence();
        skip();
        lock();
        record();
        prompts();
        System.out.println("PASS: timed sections: the Diary Page's Did 2:30, Saw 2:30, Heard 0:30, Picture 0:30 run in order from the start, "
                + "a tap skips ahead, a reopened page carries on by the wall clock, a box locks a day after its time and a stroke over it is put back, "
                + "the SECT record reads back (older builds skip it), and the prompts file sets the times");
    }

    static void template() {
        NotebookTemplate t = NotebookTemplate.DIARY_PAGE;
        check(t.timed() && Arrays.equals(t.sections, new String[]{"Did", "Saw", "Heard", "Picture"}) && Arrays.equals(t.sectionSeconds, DIARY), "The Diary Page's sections and times");
        check(!NotebookTemplate.LINED.timed() && NotebookTemplate.LINED.sectionRects(100, 100).length == 0, "Other templates have none");
        int[][] r = t.sectionRects(1404, 1872);
        check(r.length == 4 && r[0][3] < r[1][1] && r[1][3] < r[2][1] && r[0][2] < r[3][0] && r[3][1] == r[0][1] && r[3][3] - r[2][3] < 3, "Three boxes down the left, the picture beside them");
        // The guides are drawn on the boxes' edges.
        byte[] g = t.guides(1404, 1872);
        check((g[r[3][1] * 1404 + (r[3][0] + r[3][2]) / 2] & 255) == NotebookTemplate.PANEL, "The picture box's frame is where its section is");
        // The countdown's widest name fits the side bar's two-line row.
        for (String name : t.sections) check(LabText.width(name.toUpperCase(), 8, TypeStyle.LABEL.tracking) <= new DrawLayout(749, 998).inner, name + " fits at its smallest");
        check(LabText.width("HEARD", TypeStyle.LABEL.sp, TypeStyle.LABEL.tracking) <= new DrawLayout(749, 998).inner, "Heard fits at full size");
        check(DrawLayout.TIMER >= 2 * TypeStyle.LABEL.sp + 8, "The countdown's row holds two lines");
    }

    static void sequence() {
        check(TimedSections.NONE.active(T0) == -1 && !TimedSections.NONE.started() && TimedSections.NONE.nextChange(T0) == -1, "Not started");
        TimedSections s = TimedSections.start(DIARY, T0);
        check(s.started() && s.active(T0) == 0 && s.remaining(T0) == 150_000, "Did first, 2:30");
        check(s.active(T0 + 149_999) == 0 && s.active(T0 + 150_000) == 1 && s.remaining(T0 + 150_000) == 150_000, "Saw at 2:30");
        check(s.active(T0 + 300_000) == 2 && s.active(T0 + 330_000) == 3 && s.active(T0 + 359_999) == 3 && s.active(T0 + 360_000) == 4, "Heard, Picture, then done at 6:00");
        check(s.remaining(T0 + 360_000) == 0 && s.nextChange(T0 + 10_000) == 140_000, "The next change is the running section's end");
        check(TimedSections.countdown("Saw", 102_000).equals("Saw\n1:50") && TimedSections.countdown("Heard", 9_500).equals("Heard\n0:10")
                && TimedSections.countdown(null, 0).equals(TimedSections.DONE), "The countdown: the name over m:ss, or Done");
        check(TimedSections.start(new int[0], T0) == TimedSections.NONE && TimedSections.start(null, T0) == TimedSections.NONE, "Nothing to time");
    }

    static void skip() {
        TimedSections s = TimedSections.start(DIARY, T0).skip(T0 + 20_000);
        check(s.active(T0 + 20_000) == 1 && s.remaining(T0 + 20_000) == 150_000, "A skip ends Did now, Saw gets its whole time");
        check(s.active(T0 + 170_000) == 2 && s.active(T0 + 230_000) == 4, "The rest follow on");
        TimedSections done = s.skip(T0 + 30_000).skip(T0 + 31_000).skip(T0 + 32_000);
        check(done.active(T0 + 32_000) == 4 && done.skip(T0 + 33_000) == done, "Skipped to the end: done; a skip then changes nothing");
        check(TimedSections.NONE.skip(T0) == TimedSections.NONE, "Nothing to skip before the start");
    }

    static void lock() {
        TimedSections s = TimedSections.start(DIARY, T0);
        long didEnd = T0 + 150_000;
        check(!s.locked(0, didEnd + DAY - 1) && s.locked(0, didEnd + DAY) && !s.locked(1, didEnd + DAY), "Did locks a day after its time ran out, to the ms; Saw not yet");
        check(s.nextChange(T0 + 400_000) == didEnd + DAY - (T0 + 400_000), "Once done, the next change is the first lock");
        check(s.nextChange(T0 + 360_000 + DAY) == -1 && !TimedSections.NONE.locked(0, T0 + 10 * DAY), "Nothing left once all are locked; nothing locks before a start");
        // The fence: a stroke across a locked box is put back there at its finish, kept elsewhere; the pen landing there is told.
        ToneDocument d = new ToneDocument(200, 100);
        int[] box = {50, 0, 100, 100};
        d.fence(new int[][]{box});
        check(d.fenced(60, 10) && !d.fenced(10, 10) && !d.fenced(100, 10), "Fenced inside the box alone");
        d.begin();
        for (int x = 0; x < 200; x++) d.setTone(x, 50, 0);
        check(d.compositeTone(75, 50) == 0, "Drawn while the stroke is down");
        d.finish();
        check(d.compositeTone(49, 50) == 0 && d.compositeTone(100, 50) == 0 && d.compositeTone(50, 50) == 255 && d.compositeTone(99, 50) == 255, "Put back inside the box at the finish, kept either side");
        d.undo();
        check(d.compositeTone(10, 50) == 255, "The stroke is one Undo step");
        d.fence(null);
        d.begin(); d.setTone(75, 50, 0); d.finish();
        check(d.compositeTone(75, 50) == 0, "No fence: marks stay");
    }

    static ToneDocument page() {
        ToneDocument d = new ToneDocument(64, 48);
        d.begin(); d.setTone(3, 4, 0); d.finish();
        d.setSections(TimedSections.start(DIARY, T0).skip(T0 + 5_000));
        return d;
    }

    static void record() throws Exception {
        ToneDocument d = page();
        byte[] bytes = write(d);
        ToneDocument back = DocumentCodec.read(new ByteArrayInputStream(bytes));
        check(back.sections().equals(d.sections()) && back.sections().active(T0 + 5_000) == 1 && back.compositeTone(3, 4) == 0, "The sections read back: the page resumes where the clock is");
        check(Arrays.equals(write(back), bytes), "and write the same bytes again");
        check(!back.unmarked(), "A page with its sections started is not blank");
        // Started only: an untouched page keeps no record.
        ToneDocument plain = new ToneDocument(64, 48);
        check(write(plain).length < bytes.length && DocumentCodec.read(new ByteArrayInputStream(write(plain))).sections() == TimedSections.NONE, "None kept before a start");
        // A record this reader can't read opens the page without it.
        byte[] rec = d.sections().encode();
        check(TimedSections.decode(rec, rec.length).equals(d.sections()) && TimedSections.decode(rec, 5) == null && TimedSections.decode(new byte[]{0}, 1) == null
                && TimedSections.decode(new byte[]{(byte) 99}, 1) == null, "A short or odd record doesn't read");
        byte[] longer = Arrays.copyOf(rec, rec.length + 4);
        check(TimedSections.decode(longer, longer.length).equals(d.sections()), "A later version's longer record still reads");
        TimedSections s = d.sections();
        check(d.setSections(TimedSections.NONE) && !d.setSections(TimedSections.NONE) && d.setSections(s), "Setting them is a change, once");
    }
    static byte[] write(ToneDocument d) throws Exception { ByteArrayOutputStream out = new ByteArrayOutputStream(); DocumentCodec.write(out, d.layerSnapshot()); return out.toByteArray(); }

    static void prompts() throws Exception {
        NotebookTemplate t = NotebookTemplate.DIARY_PAGE;
        check(Arrays.equals(DailyPrompt.sectionTimes(DailyPrompt.DEFAULTS, t), DIARY), "The defaults' line gives the template's times");
        check(DailyPrompt.parse(DailyPrompt.DEFAULTS).stream().noneMatch(c -> c.text.contains("Did 2:30")), "and is not a card");
        check(Arrays.equals(DailyPrompt.sectionTimes("diary page TIMES | Did 1:00 | Heard 45 s | Picture 3 min | Saw 0:05\n", t), new int[]{60, 5, 45, 180}), "By name in any order, m:ss, s or min, any case");
        check(Arrays.equals(DailyPrompt.sectionTimes("Diary Page times | Did soon | Saw 0:00 | Heard 99:99 | Picture 61 min | Nap 1:00\n", t), DIARY), "Times that don't read keep the template's");
        check(Arrays.equals(DailyPrompt.sectionTimes("# Diary Page times | Did 1:00\nLined times | Did 1:00\n", t), DIARY), "A note or another template's line changes nothing");
        check(Arrays.equals(DailyPrompt.sectionTimes((String) null, t), DIARY) && DailyPrompt.sectionTimes("", NotebookTemplate.LINED) == null, "No file: the template's; no sections: none");
        check(DailyPrompt.parse("Diary Page times | Did 1:00\n1 min | A card.\n").size() == 1, "A times line is never a card");
        File dir = Files.createTempDirectory("sections").toFile();
        try {
            check(Arrays.equals(DailyPrompt.sectionTimes(dir, t), DIARY) && !new File(dir, DailyPrompt.FILE).exists(), "No file: the template's times, nothing written");
            Files.write(new File(dir, DailyPrompt.FILE).toPath(), "1 min | Draw.\nDiary Page times | Saw 4:00\n".getBytes(StandardCharsets.UTF_8));
            check(Arrays.equals(DailyPrompt.sectionTimes(dir, t), new int[]{150, 240, 30, 30}), "The file's line as edited");
        } finally {
            new File(dir, DailyPrompt.FILE).delete(); dir.delete();
        }
    }
}
