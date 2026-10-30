package io.github.mpdairy.monopaint;

import java.util.*;

/**
 * The owner's 2026-10-21 decisions on the notebook's rules ({@link NotebookRules}, {@link NotebookOptions}, {@link DailyPrompt}):
 * <ul>
 * <li>No Erasing is "no eraser, no undo": Undo and Redo off, a page or layer with marks kept from Clear and Delete, a blank
 * one still free to go; with it off, nothing refused;</li>
 * <li>Timed Pages never times a cover (front or back), nor a page timed by its sections;</li>
 * <li>Guides in PDF is offered only when the template, or a page's own, isn't Blank;</li>
 * <li>a Diary Page card sets the page drawn on's own template, never the notebook's.</li>
 * </ul>
 */
public final class NoErasingChecks {
    public static void main(String[] args) throws Exception {
        Assets.install(Assets.folder(Assets.FOLDER));
        noUndo();
        coversUntimed();
        guidesRow();
        diaryCard();
        System.out.println("PASS: No Erasing turns Undo and Redo off and keeps a page or layer with marks from Clear and Delete (a blank one may go), nothing with it off; Timed Pages never times a cover or a sectioned page; Guides in PDF only with guides to print; a Diary Page card sets its page's own template alone");
    }

    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    static void noUndo() {
        NotebookOptions off = NotebookOptions.DEFAULT, on = off.withNoErasing(true);
        check(NotebookRules.undoOff(on) && !NotebookRules.undoOff(off) && !NotebookRules.undoOff(null), "Undo and Redo are off with No Erasing alone");
        check(NotebookRules.keeps(on, true) && !NotebookRules.keeps(on, false), "a page or layer with marks is kept (Clear, Delete Page, Delete layer); a blank one may go");
        check(!NotebookRules.keeps(off, true) && !NotebookRules.keeps(null, true), "and nothing is kept with it off");
        check(NotebookRules.eraserOff(on) && NotebookRules.refuses(on, ToolSlots.ERASER, null), "the eraser stays off");
        for (int slot = 0; slot < ToolSlots.COUNT; slot++) check(!NotebookRules.refuses(on, slot, ToolSlotsChecks.old(slot)), "drawing (white paint too) stays: slot " + slot);
        check(!NotebookRules.UNDO_OFF.isEmpty() && NotebookRules.UNDO_OFF.length() <= 30 && NotebookRules.NO_ERASING_SAID.length() <= 30, "the notes are short (docs/TEXT-STYLE.md)");
    }

    static void coversUntimed() {
        NotebookOptions timed = NotebookOptions.DEFAULT.withSeconds(300);
        check(NotebookRules.timedSeconds(timed, DrawingBook.FRONT, false) == 0 && NotebookRules.timedSeconds(timed, DrawingBook.BACK, false) == 0, "a cover is never timed");
        check(NotebookRules.timedSeconds(timed, 0, false) == 300 && NotebookRules.timedSeconds(timed, 4, false) == 300, "a page is");
        check(NotebookRules.timedSeconds(timed, 0, true) == 0 && NotebookRules.timedSeconds(null, 0, false) == 0 && NotebookRules.timedSeconds(NotebookOptions.DEFAULT, 0, false) == 0, "not a sectioned page, nor with it off");
        // Untimed, the clock never starts: no countdown, no lock.
        ToneDocument cover = new ToneDocument(60, 80);
        TimedPages clock = new TimedPages();
        clock.configure(NotebookRules.timedSeconds(timed, DrawingBook.FRONT, false), 0);
        clock.sync(cover, true, 0);
        clock.strokeBegins(0);
        check(clock.tick(10_000_000, false) == TimedPages.State.IDLE && !cover.locked() && clock.remaining(10_000_000) == 0, "a cover drawn on for hours never locks");
    }

    /**
     * The menu's Template and Guides in PDF rows are cut (the refocus, 2026-10-24): the guides always print, and their
     * step on the screen is Template Visibility's (the side column's button, the Template screen's chips).
     */
    static void guidesRow() throws Exception {
        String app = DrawLayoutChecks.source("PaintActivity");
        check(!app.contains("withPrintGuides") && !app.contains("NotebookLayout.GUIDES") && !app.contains("names.add(NotebookLayout.TEMPLATE)"), "No Guides in PDF or Template row in the menu");
        check(DrawLayoutChecks.source("InkWashBar").contains("app.showGuides(app.book.options().withNextGuidesStep().guidesShown)"), "Template Visibility still steps the guides");
    }

    static void diaryCard() throws Exception {
        DrawingBook book = new DrawingBook(new ToneDocument(300, 400));
        book.addPage(); book.addPage();
        book.setOptions(book.options().withTemplate(NotebookTemplate.LINED));
        List<DailyPrompt.Card> cards = DailyPrompt.parse("Diary Page | 3 min | The day.\n1 min | A cat.");
        DailyPrompt.Card diary = cards.get(0), plain = cards.get(1);
        check(diary.diary && DailyPrompt.template(diary) == NotebookTemplate.DIARY_PAGE && DailyPrompt.template(plain) == null, "a Diary Page card carries Diary Page; another none");
        book.select(1);
        // As PaintActivity's Start does: the page drawn on's own template.
        book.setPageTemplate(book.index(), DailyPrompt.template(diary));
        check(book.template(1) == NotebookTemplate.DIARY_PAGE, "the page drawn on takes Diary Page");
        check(book.options().template == NotebookTemplate.LINED && book.template(0) == NotebookTemplate.LINED && book.template(2) == NotebookTemplate.LINED, "the notebook and its other pages keep theirs");
        check(!DailyPrompt.DEFAULTS.contains("notebook's template"), "the prompts file says the page alone");
    }
}
