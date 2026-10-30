package io.github.mpdairy.monopaint;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * The Self-Portrait Prompt ({@link DailyPrompt}): the cards' file parsed (notes, times, Diary Page, lines that don't read
 * skipped), written from the defaults when missing or empty and read back as edited; Deal Another; the headings; and the Diary Page template ({@link NotebookTemplate#DIARY_PAGE}).
 */
public final class DailyPromptChecks {
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    public static void main(String[] args) throws Exception {
        List<DailyPrompt.Card> cards = DailyPrompt.parse(DailyPrompt.DEFAULTS);
        check(cards.size() >= 30, "the defaults hold " + cards.size() + " cards");
        Set<String> texts = new HashSet<>();
        for (DailyPrompt.Card c : cards) {
            check(texts.add(c.text) && !c.text.isEmpty() && !c.text.contains("|"), "a card is unique and not empty: " + c.text);
            check(c.seconds >= 0 && c.seconds <= DailyPrompt.MOST_SECONDS, "a known length");
            // Cards speak to the one drawing, "Draw yourself ..." (the owner, 2026-10-27), the exception docs/TEXT-STYLE.md notes.
            check(c.text.startsWith("Draw yourself "), "a self-portrait card: " + c.text);
        }
        // Parsing: notes, times, Diary Page, and lines that don't read.
        List<DailyPrompt.Card> got = DailyPrompt.parse("# a note\n\n  30 s | Thirty.\n2 min|Two.\nDiary Page | 3 Min | Diary.\nUntimed.\n"
                + "90 min | Too long.\n0 s | Zero.\nsoon | Not a time.\n2 min | \n1 min | 2 min | Two times.\nDiary Page | Diary untimed.\r\nA | B | C | D\n");
        check(got.size() == 5, "five cards read, the rest skipped: " + got.size());
        check(got.get(0).seconds == 30 && got.get(0).text.equals("Thirty.") && got.get(1).seconds == 120 && got.get(1).text.equals("Two."), "times in s and min, spaces trimmed");
        check(got.get(2).diary && got.get(2).seconds == 180 && got.get(3).seconds == 0 && !got.get(3).diary && got.get(4).diary && got.get(4).seconds == 0, "Diary Page, untimed");
        check(DailyPrompt.parse("").isEmpty() && DailyPrompt.parse("# only notes\n").isEmpty(), "none in an empty file");
        // A card whose words end in "times" is a card, not a line of section times (those have a bar), and a page keeps it.
        List<DailyPrompt.Card> times = DailyPrompt.parse("A cat drawn three times\nDiary Page | Draw it ten times\n");
        check(times.size() == 2 && times.get(0).text.equals("A cat drawn three times") && times.get(1).diary, "an untimed card ending in times is read: " + times.size());
        check(DailyPrompt.of(DailyPrompt.line(times.get(0))) != null, "a page keeps a card ending in times");
        // The file: written when missing or empty, read as edited, its folder made.
        File dir = Files.createTempDirectory("prompts").toFile(), folder = new File(dir, "Inkwash"), file = new File(folder, DailyPrompt.FILE);
        try {
            check(DailyPrompt.load(folder).size() == cards.size() && file.isFile() && new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).equals(DailyPrompt.DEFAULTS), "missing: written from the defaults");
            Files.write(file.toPath(), "# mine\n1 min | Draw a spoon.\nnonsense | x\n".getBytes(StandardCharsets.UTF_8));
            List<DailyPrompt.Card> mine = DailyPrompt.load(folder);
            check(mine.size() == 1 && mine.get(0).text.equals("Draw a spoon.") && mine.get(0).seconds == 60, "edited: read each time");
            Files.write(file.toPath(), "# nothing left\n".getBytes(StandardCharsets.UTF_8));
            check(DailyPrompt.load(folder).size() == cards.size() && DailyPrompt.parse(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)).size() == cards.size(), "no card left: written again");
            check(DailyPrompt.load(null).size() == cards.size(), "no folder: the defaults");
        } finally { file.delete(); folder.delete(); dir.delete(); }
        // Deal Another, headings (Self-Portrait Prompt, 2026-10-30: no today's card; a new page's is dealt at random).
        int n = cards.size();
        Random random = new Random(1);
        for (int i = 0; i < 500; i++) { int c = random.nextInt(n); int a = DailyPrompt.another(c, n, random); check(a != c && a >= 0 && a < n, "another differs"); }
        check(DailyPrompt.another(0, 1, random) == 0, "one card: itself");
        check(DailyPrompt.heading(got.get(1)).equals("Self-Portrait Prompt, 2 Min") && DailyPrompt.heading(got.get(0)).equals("Self-Portrait Prompt, 30 s")
            && DailyPrompt.heading(got.get(3)).equals("Self-Portrait Prompt, Untimed") && DailyPrompt.heading(got.get(2)).equals("Self-Portrait Prompt, Diary Page, 3 Min"), "headings");
        check(DailyPrompt.TITLE.equals("Self-Portrait Prompt") && DailyPrompt.FILE.endsWith(" prompts.txt"), "shown as the Self-Portrait Prompt; the owner's cards' file keeps its name");
        check(DailyPrompt.clock(120).equals("2:00") && DailyPrompt.clock(30).equals("0:30") && DailyPrompt.clock(65).equals("1:05") && DailyPrompt.clock(1).equals("0:01"), "clock");
        // A notebook setting (2026-10-30): a page added with it on is dealt a card at random, as it is added only; Notebook
        // Settings' row Off and On; no row in Play's menu.
        String app = DrawLayoutChecks.source("PaintActivity"), screens = DrawLayoutChecks.source("NotebookScreens");
        check(app.contains("private void addPage() { addPage(() -> book.addPage()); }") && app.contains("        addPage(() -> book.insertPage());")
                && app.contains("if (book.count() <= pages || !book.options().selfPortrait) return;") && app.contains("dailyPrompt(cards, new java.util.Random().nextInt(cards.size()));")
                && app.split("selfPortrait", -1).length == 3 && !app.contains("DailyPrompt.TITLE")
                && screens.contains("app.setNotebookOptions(book.options().withSelfPortrait(i == 1))"),
                "Self-Portrait Prompt: each page added (the + tile, Next Page, Add page) dealt a card while it is on, nowhere else; set in Notebook Settings");
        // The Diary Page template.
        check(NotebookTemplate.of("diarypage") == NotebookTemplate.DIARY_PAGE && NotebookTemplate.DIARY_PAGE.label.equals("Diary Page"), "key and label");
        byte[] g = NotebookTemplate.DIARY_PAGE.guides(1920, 2400);
        int marked = 0;
        for (byte b : g) if ((b & 255) != NotebookTemplate.NONE) marked++;
        check(g.length == 1920 * 2400 && marked > 0 && marked < g.length / 20, "faint boxes only");
        check(g[1200 * 1920 + 300] == (byte) NotebookTemplate.NONE, "inside a box is paper");
        int labelled = 0, top = Math.round(2400 * .06f), side = Math.round(1920 * .08f);
        for (int y = top + 20; y < top + 150; y++) for (int x = side + 20; x < side + 200; x++) if ((g[y * 1920 + x] & 255) != NotebookTemplate.NONE) labelled++;
        check(labelled > 100, "the first box is labelled");
        // A page given a card keeps it in its own record (PRMT), read back as the card; a page without one writes as before.
        for (DailyPrompt.Card c : cards) {
            DailyPrompt.Card back = DailyPrompt.of(DailyPrompt.line(c));
            check(back != null && back.text.equals(c.text) && back.seconds == c.seconds && back.diary == c.diary, "a card's line reads back as the card: " + c.text);
        }
        check(DailyPrompt.of(null) == null && DailyPrompt.of("a | b | c | d") == null, "no line, or one that doesn't read, is no card");
        ToneDocument page = new ToneDocument(40, 30);
        java.io.ByteArrayOutputStream plain = new java.io.ByteArrayOutputStream(), prompted = new java.io.ByteArrayOutputStream();
        DocumentCodec.write(plain, page.layerSnapshot());
        String line = DailyPrompt.line(cards.get(cards.size() - 4));
        check(page.unmarked() && page.setPrompt(line) && !page.setPrompt(line) && !page.unmarked(), "a prompt is a change to save, and the page is no longer blank");
        DocumentCodec.write(prompted, page.layerSnapshot());
        ToneDocument read = DocumentCodec.read(new java.io.ByteArrayInputStream(prompted.toByteArray()));
        check(line.equals(read.prompt()) && DocumentCodec.read(new java.io.ByteArrayInputStream(plain.toByteArray())).prompt() == null, "the page's prompt reads back from its file");
        // A card not acted on goes when the page is left; a page drawn on after it keeps it (the owner, 2026-10-29).
        ToneDocument left = new ToneDocument(40, 30);
        check(left.setPrompt(line) && left.dropUnusedPrompt() && left.prompt() == null && left.unmarked(), "dealt, left blank: no prompt, a blank page");
        ToneDocument drawn = new ToneDocument(40, 30);
        drawn.setPrompt(line); drawn.begin(); drawn.setTone(5, 5, 0); drawn.finish();
        check(!drawn.dropUnusedPrompt() && line.equals(drawn.prompt()), "dealt, drawn on: kept");
        check(drawn.undo() && drawn.dropUnusedPrompt() && drawn.unmarked(), "drawn, then undone: goes");
        ToneDocument before = new ToneDocument(40, 30);
        before.begin(); before.setTone(5, 5, 0); before.finish();
        check(before.setPrompt(line) && before.dropUnusedPrompt() && before.prompt() == null && !before.unmarked(), "drawn on before, nothing since: goes, the marks stay");
        before.setPrompt(line); before.begin(); before.setTone(9, 9, 0); before.finish();
        check(!before.dropUnusedPrompt(), "drawn on before and after: kept");
        ToneDocument reread = DocumentCodec.read(new java.io.ByteArrayInputStream(prompted.toByteArray()));
        check(reread.dropUnusedPrompt() && reread.unmarked(), "read from a file with nothing but the card: goes");
        java.io.ByteArrayOutputStream kept = new java.io.ByteArrayOutputStream();
        DocumentCodec.write(kept, before.layerSnapshot());
        check(!DocumentCodec.read(new java.io.ByteArrayInputStream(kept.toByteArray())).dropUnusedPrompt(), "read from a file, drawn on: kept");
        System.out.println("PASS: " + n + " default cards, unique, with no you; the cards' file parsed (notes, s and min, Diary Page; a line that doesn't read skipped), written from the defaults when missing or empty and read as edited; today's card fixed by the day and never repeating until the list goes round, Deal Another always another; a page's card kept in its file and read back, and gone from a page left with nothing done after it; the headings, and the Diary Page template's boxes");
    }
}
