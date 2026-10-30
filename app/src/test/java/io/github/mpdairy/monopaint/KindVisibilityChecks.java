package io.github.mpdairy.monopaint;

import java.util.Arrays;

/**
 * Show Kinds (the owner, 2026-10-28; "just per page"): each of the eraser's kinds (Pen, Paint, Dry Media) shown Full,
 * Half, Faint or Hidden on a page, kept in the page's file (a KSHN section only when not all Full; an old page reads all
 * Full); a stroke of a hidden kind is refused, the eraser never takes one; the screen fades a kind's planes as the
 * guides fade, the page's composite and its copies never.
 */
public final class KindVisibilityChecks {
    static final int W = ErasableChecks.W, H = ErasableChecks.H;
    static int checks;

    public static void main(String[] args) throws Exception {
        Assets.install(Assets.folder(Assets.FOLDER));
        kept(); refused(); erasing(); screenOnly();
        System.out.println("PASS: Show Kinds (" + checks + " checks): each kind's step kept with the page, a KSHN section only when "
                + "not all Full, an old page all Full and written byte for byte as before; a pen of a hidden kind refused with its note, Half and Faint drawn; "
                + "the eraser leaves a hidden kind and is refused when all it takes is hidden; the screen fades a kind as the guides fade, the copies show it whole");
    }
    static void check(boolean ok, String what) { checks++; if (!ok) throw new AssertionError(what); }

    static void kept() throws Exception {
        int set = EraseKind.withStep(EraseKind.withStep(0, 1, NotebookOptions.HIDDEN), 3, NotebookOptions.FAINT);
        check(EraseKind.step(set, 1) == NotebookOptions.HIDDEN && EraseKind.step(set, 2) == NotebookOptions.FULL && EraseKind.step(set, 3) == NotebookOptions.FAINT
                && EraseKind.hidden(set) == ToneDocument.KIND_PEN, "Each kind its own step");
        check(EraseKind.withStep(EraseKind.withStep(set, 1, NotebookOptions.FULL), 3, NotebookOptions.FULL) == 0, "All Full again is none");
        check(EraseKind.stepNote(3, NotebookOptions.HALF).equals("Dry media half") && EraseKind.said(1, NotebookOptions.HIDDEN).equals("Pen is hidden"), "Its words");
        ToneDocument d = InkWashLayers.blank(W, H);
        KindLayersChecks.block(d, InkWashLayers.INK, ToneDocument.KIND_PEN, 20, 20, 40, 40, 0);
        byte[] old = KindLayersChecks.write(d);
        check(KindLayersChecks.read(old).kindsShown() == 0, "An old page reads all Full");
        long revision = d.revision();
        check(d.setKindsShown(set) && d.revision() != revision && !d.setKindsShown(set), "A change to save, once");
        byte[] kept = KindLayersChecks.write(d);
        check(KindLayersChecks.read(kept).kindsShown() == set && kept.length > old.length, "The page's file keeps the steps");
        d.setKindsShown(0);
        check(Arrays.equals(KindLayersChecks.write(d), old), "All Full again writes the page byte for byte as before");
    }

    /** A pen stroke of a hidden kind is refused, said; Half and Faint draw. The kind is the brush's medium, as its plane is. */
    static void refused() {
        int pen = ResolvedBrush.of(ErasableChecks.preset("Steady line"), 8, .5).medium & ToneDocument.KIND_ALL;
        int pencil = ResolvedBrush.of(ErasableChecks.preset("Sketch pencil"), 8, .5).medium & ToneDocument.KIND_ALL;
        check(pen == ToneDocument.KIND_PEN && pencil == ToneDocument.KIND_PENCIL, "Steady line is pen, Sketch pencil dry media");
        int shown = EraseKind.withStep(EraseKind.withStep(0, 1, NotebookOptions.HIDDEN), 3, NotebookOptions.FAINT);
        check("Pen is hidden, see Show Kinds".equals(EraseKind.refusal(shown, pen)), "A pen refused: " + EraseKind.refusal(shown, pen));
        check(EraseKind.refusal(shown, pencil) == null && EraseKind.refusal(0, pen) == null, "Faint and Full draw");
        check(EraseKind.stepOf(shown, pencil) == NotebookOptions.FAINT, "and Faint is said when taken up");
        // A texture is Paint (2026-10-29): Paint hidden refuses it, and Show Kinds' Paint step fades it.
        int hatch = ResolvedBrush.of(ErasableChecks.preset("Hatch pen"), 8, .5).medium & ToneDocument.KIND_ALL;
        int paintHidden = EraseKind.withStep(0, 2, NotebookOptions.HIDDEN);
        check(hatch == ToneDocument.KIND_PAINT && "Paint is hidden, see Show Kinds".equals(EraseKind.refusal(paintHidden, hatch)) && EraseKind.refusal(paintHidden, pen) == null,
                "Hatch pen is Paint, refused with Paint hidden: " + EraseKind.refusal(paintHidden, hatch));
    }

    /** The eraser leaves a hidden kind; refused when all it takes is hidden; Faint is erased. */
    static void erasing() {
        ModularBrush pencil = ErasableChecks.preset("Sketch pencil"), ink = ErasableChecks.preset("Steady line");
        ToneDocument d = InkWashLayers.blank(W, H);
        ErasableChecks.sketch(d, pencil); ErasableChecks.ink(d, ink);
        ToneDocument.Layer before = KindLayersChecks.layer(d, InkWashLayers.INK).plane(ToneDocument.PLANE_PEN);
        int takes = EraseKind.takes(0, ToneDocument.KIND_PEN);
        check(takes == (ToneDocument.KIND_PAINT | ToneDocument.KIND_PENCIL) && EraseKind.takes(ToneDocument.KIND_PENCIL, 0) == ToneDocument.KIND_PENCIL
                && EraseKind.takes(0, 0) == 0, "Everything with Pen hidden takes paint and dry media; nothing hidden, as set");
        EraseKindChecks.erase(d, takes);
        check(KindLayersChecks.covered(KindLayersChecks.layer(d, InkWashLayers.INK).plane(ToneDocument.PLANE_DRY)) == 0
                && KindLayersChecks.samePlane(before, KindLayersChecks.layer(d, InkWashLayers.INK).plane(ToneDocument.PLANE_PEN)), "Pen hidden: the pencil erased, the pen byte for byte");
        check(EraseKind.takes(ToneDocument.KIND_PEN, ToneDocument.KIND_PEN) == 0, "A Pen eraser with Pen hidden takes nothing, so is refused");
    }

    /** The screen's layer steps fade a kind as the guides fade; the page's composite (its copies') and a notebook's copy never read them. */
    static void screenOnly() throws Exception {
        ToneDocument d = InkWashLayers.blank(W, H);
        KindLayersChecks.block(d, InkWashLayers.INK, ToneDocument.KIND_PEN, 20, 20, 40, 40, 0);
        KindLayersChecks.block(d, InkWashLayers.INK, ToneDocument.KIND_PENCIL, 100, 20, 40, 40, 0);
        int half = EraseKind.withStep(0, 1, NotebookOptions.HALF) << ToneDocument.KIND_STEPS, hidden = EraseKind.withStep(0, 1, NotebookOptions.HIDDEN) << ToneDocument.KIND_STEPS;
        check(d.compositeTone(30, 30) == 0 && d.shownTone(30, 30, null, 0, null, null) == 0, "Full: as drawn");
        check(d.shownTone(30, 30, null, half, null, null) == 255 - 255 * NotebookOptions.GUIDE_STRENGTH[NotebookOptions.HALF] / 100, "Half as the guides' Half: " + d.shownTone(30, 30, null, half, null, null));
        check(d.shownTone(30, 30, null, hidden, null, null) == 255 && d.shownTone(110, 30, null, hidden, null, null) == 0, "Pen hidden: paper; the pencil whole");
        int[] plain = KindLayersChecks.shown(d);
        d.setKindsShown(hidden >>> ToneDocument.KIND_STEPS);
        check(Arrays.equals(KindLayersChecks.shown(d), plain), "The page itself, its copies' composite, unchanged by its steps");
        DrawingBook book = NotebookOptionsChecks.book();
        ToneDocument page = book.snapshot().printed(0);
        int pw = page.width;
        int[] copy = new int[pw * page.height];
        for (int i = 0; i < copy.length; i++) copy[i] = page.compositeTone(i % pw, i / pw);
        book.current().setKindsShown(EraseKind.STEPS);
        page = book.snapshot().printed(0);
        boolean same = page.kindsShown() == EraseKind.STEPS;
        for (int i = 0; i < copy.length && same; i++) same = page.compositeTone(i % pw, i / pw) == copy[i];
        check(same, "A notebook's copy of a page with every kind hidden is the same as Full");
    }
}
