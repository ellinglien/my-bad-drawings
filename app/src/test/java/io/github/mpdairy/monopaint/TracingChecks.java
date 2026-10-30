package io.github.mpdairy.monopaint;

import java.io.*;
import java.nio.file.Files;
import java.util.*;

/**
 * Tracing paper, stage 2 (docs/archive/superpowers/specs/2026-10-20-tracing-paper-design.md, "Stage 2: Tracing ghosts";
 * {@link TracingPaper}, {@link GhostPlane}, {@code BookOptions}' PAPM, GHST and STEP, the recovery's PAPER): which pages
 * ghost and at what strength, Half and Faint black at 143 and 209 through the screen's own layers, the plane equal to a
 * reference composite, over the guides; nothing of it in the page, its file, picture, PNG copy, Fill or the eyedropper;
 * the records only when not the default, round-tripped, a bad one ignored, carried by page moves; the plane's key
 * changing only with what it is made of; free under the lock and the rules; the build timed at Manta size. {@code write
 * <file>} writes a Tracing notebook for the version before it ({@code OlderOptionsCheck}, af06002); {@code older <file>}
 * opens what that version saved again.
 */
public final class TracingChecks {
    static final int W = 60, H = 40;

    public static void main(String[] args) throws Exception {
        if (args.length == 2 && args[0].equals("write")) { write(new File(args[1])); return; }
        if (args.length == 2 && args[0].equals("older")) { older(new File(args[1])); return; }
        ghosts();
        levels();
        glass();
        layerSteps();
        notBaked();
        records();
        key();
        rules();
        timing();
        System.out.println("PASS: Tracing ghosts the page before (or the pinned ghost) Half and the page after Faint, Hidden giving none, Plain, covers none; Glass every other page Half (Hidden left out, one plane whatever the count), layer steps page × layer, the page and layer drawn on Solid; Half and Faint black land on 143 and 209 through the screen's layers, the plane equals a reference composite and darkens the guides; the page, its file, picture, PNG copy, Fill and eyedropper are the same with and without ghosts; PAPM, GHST, STEP and PSTR (the default strength, Faint) only when not the default (a Plain notebook byte for byte as before), round-tripped through the file, its pages and the recovery, a bad record ignored, steps and ghost following moves, copies and deletes; the plane's key changes only with a page turn, the Paper, a ghost's step, the ghost or the order; Paper, steps and ghosts free when Sealed, under No Erasing and on a timed-out page");
    }

    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    /** A three-page book drawn on page 1: page 0 a black square at 10..20, page 1 a mark at 40, 30, page 2 a black square at 15..25. */
    static DrawingBook book() throws IOException { return book(0); }
    /** As {@link #book()}, page 0's square of {@code tone}. */
    static DrawingBook book(int tone) throws IOException {
        DrawingBook book = new DrawingBook(InkWashLayers.blank(W, H));
        square(book.current(), 10, 20, tone);
        book.addPage(); book.current().begin(); book.current().setTone(40, 30, 0); book.current().finish();
        book.addPage(); square(book.current(), 15, 25, 0);
        book.select(1);
        return book;
    }
    static void square(ToneDocument page, int from, int to, int tone) {
        page.begin();
        for (int y = from; y < to; y++) for (int x = from; x < to; x++) page.setTone(x, y, tone);
        page.finish();
    }
    static TracingPaper tracing(DrawingBook book) { return book.tracing().withPaper(TracingPaper.TRACING); }
    static byte[] plane(DrawingBook book) throws IOException { GhostPlane.Planes p = GhostPlane.build(book.width, book.height, book.ghostSources()); return p == null ? null : p.under; }

    // ---- which pages ghost ----

    static void ghosts() throws Exception {
        TracingPaper t = TracingPaper.plain(5);
        check(t.ghosts(2).length == 0 && t.isDefault(), "Plain: nothing shows through");
        t = t.withPaper(TracingPaper.TRACING);
        check(Arrays.deepEquals(t.ghosts(2), new int[][]{{1, 44}, {3, 18}}), "Tracing: the page before Half, the page after Faint");
        check(Arrays.deepEquals(t.ghosts(0), new int[][]{{1, 18}}) && Arrays.deepEquals(t.ghosts(4), new int[][]{{3, 44}}), "the first page has no page before, the last none after");
        check(t.ghosts(DrawingBook.FRONT).length == 0 && t.ghosts(DrawingBook.BACK).length == 0, "A cover never has ghosts");
        check(Arrays.deepEquals(t.withGhost(0).ghosts(2), new int[][]{{0, 44}, {3, 18}}), "A pinned ghost takes the page before's place");
        check(Arrays.deepEquals(t.withGhost(3).ghosts(2), new int[][]{{3, 44}}), "and the page after, pinned, shows once");
        check(Arrays.deepEquals(t.withGhost(2).ghosts(2), t.ghosts(2)), "The page drawn on, pinned, is no ghost of itself");
        check(Arrays.deepEquals(t.withStep(1, TracingPaper.HIDDEN).ghosts(2), new int[][]{{3, 18}}), "A Hidden page gives no ghost, and the page before it doesn't take its place");
        // Stage 3c: a page's step is its step as a layer, times the Paper's strength (Half 44 × Faint 18 = 7).
        check(Arrays.deepEquals(t.withStep(1, TracingPaper.FAINT).withStep(3, TracingPaper.SOLID).ghosts(2), new int[][]{{1, 7}, {3, 18}}), "A page's own step times the Paper's strength");
        // Glass (stage 3): every other page, bottom of the stack first, each at its step (Half if never set), Hidden left out.
        TracingPaper g = t.withPaper(TracingPaper.GLASS);
        check(Arrays.deepEquals(g.ghosts(2), new int[][]{{0, 44}, {1, 44}, {3, 44}, {4, 44}}), "Glass: every other page in stack order, Half");
        check(Arrays.deepEquals(g.withStep(1, TracingPaper.HIDDEN).withStep(4, TracingPaper.FAINT).withGhost(0).ghosts(2), new int[][]{{0, 44}, {3, 44}, {4, 7}}),
                "Glass: a Hidden page left out, a page's own step, the ghost no different");
        TracingPaper g7 = new TracingPaper(TracingPaper.GLASS, -1, new int[7]);
        check(Arrays.deepEquals(g7.ghosts(1), new int[][]{{0, 44}, {2, 44}, {3, 44}, {4, 44}}) && Arrays.deepEquals(g7.withStep(2, TracingPaper.HIDDEN).ghosts(6), new int[][]{{1, 44}, {3, 44}, {4, 44}, {5, 44}}),
                "Glass: only the nearest four, a Hidden page skipped over");
        check(g.ghosts(DrawingBook.FRONT).length == 0 && g.withStep(2, TracingPaper.HIDDEN).ghosts(2).length == 4, "A cover shows Plain; the page drawn on shows Solid, whatever its step");
        check(t.layerShown(3) == TracingPaper.SOLID && t.withStep(3, TracingPaper.FAINT).layerShown(3) == TracingPaper.FAINT, "A layer shows at its own step, Solid when never set");
        check(TracingPaper.STRENGTH[TracingPaper.HALF] == 44 && TracingPaper.STRENGTH[TracingPaper.FAINT] == 18, "Half 44 %, Faint 18 % (the owner's)");
        // The notebook's default strength (stage 3b, Notebook Options' Faint): a page never set shows at it; its own step still wins.
        TracingPaper faint = t.withStrength(TracingPaper.FAINT);
        check(Arrays.deepEquals(faint.ghosts(2), new int[][]{{1, 18}, {3, 18}}) && Arrays.deepEquals(faint.withPaper(TracingPaper.GLASS).withStep(0, TracingPaper.HALF).ghosts(2), new int[][]{{0, 7}, {1, 18}, {3, 18}, {4, 18}})
                && !TracingPaper.plain(3).withStrength(TracingPaper.FAINT).isDefault() && t.withStrength(TracingPaper.SOLID).strength == TracingPaper.HALF,
                "Faint by default: every page never set Faint, a page's own step kept; not the default; only Half or Faint");
    }

    // ---- grey levels, through the screen's own layers ----

    /** What the screen shows at pixel {@code i} of {@code page} with {@code ghost} under its layers: {@code visibleLayers} composed as {@code PagePicture.Composite} does (as the native compose). */
    static int shown(ToneDocument page, byte[] ghost, int i) {
        byte[][] tones = new byte[ToneDocument.STACK + 2][], alpha = new byte[ToneDocument.STACK + 2][];
        int[] opacity = new int[ToneDocument.STACK + 2];
        int n = page.visibleLayers(tones, alpha, opacity, ghost), result = page.paperTone();
        for (int k = 0; k < n; k++) {
            int a = alpha[k][i] & 255;
            if (a == 0) continue;
            if (opacity[k] != 100) a = (a * opacity[k] + 50) / 100;
            result = ((tones[k][i] & 255) * a + result * (255 - a) + 127) / 255;
        }
        return result;
    }
    static void levels() throws Exception {
        DrawingBook book = book();
        book.setTracing(tracing(book));
        byte[] plane = plane(book);
        ToneDocument page = book.current();
        int half = 12 * W + 12, both = 17 * W + 17, faint = 22 * W + 22, paper = 2 * W + 2;
        check(shown(page, plane, half) == 143, "Half black lands on 143: " + shown(page, plane, half));
        check(shown(page, plane, faint) == 209, "Faint black lands on 209: " + shown(page, plane, faint));
        check(shown(page, plane, paper) == 255 && shown(page, null, half) == 255, "Paper stays paper; without the plane, no ghost");
        check(shown(page, plane, 30 * W + 40) == 0, "The drawing page's mark shows Solid over its ghosts");
        // The plane against a reference composite: each ghost 255 − (255 − c)·s, multiplied.
        for (int i = 0; i < W * H; i++) {
            double light = 1;
            int x = i % W, y = i / W;
            if (i == 30 * W + 40) continue;
            if (x >= 10 && x < 20 && y >= 10 && y < 20) light *= 1 - .44;
            if (x >= 15 && x < 25 && y >= 15 && y < 25) light *= 1 - .18;
            check(Math.abs(shown(page, plane, i) - 255 * light) <= 1, "The plane is the reference composite at " + x + ", " + y + ": " + shown(page, plane, i) + " for " + 255 * light);
        }
        check(Math.abs(shown(page, plane, both) - 255 * .56 * .82) <= 1, "Both ghosts where they cross, in either order");
        // A grey ghost mark: g at strength s shows as 255 − (255 − g)·s.
        DrawingBook grey = book(100);
        grey.setTracing(tracing(grey));
        check(Math.abs(shown(grey.current(), plane(grey), half) - (255 - (255 - 100) * .44)) <= 1, "A grey ghost: 255 − (255 − g)·s");
        // Guides are the drawing page's paper, under the ghost: darkened by it, never doubled.
        DrawingBook guided = book();
        guided.setOptions(NotebookOptions.DEFAULT.withTemplate(NotebookTemplate.DIARY));
        guided.setTracing(tracing(guided));
        byte[] gp = plane(guided), guides = guided.current().guides();
        for (int i = 0; i < W * H; i++) {
            int g = guides[i] & 255, want = (int) Math.round(g * (255 - (gp == null ? 0 : gp[i] & 255)) / 255.0);
            if (i == 30 * W + 40) continue;
            check(Math.abs(shown(guided.current(), gp, i) - want) <= 1, "The guides under the ghost at " + i);
        }
        // An eraser's preview and Real Mirror's reflection put a tone on the ghost as the compose does on white paper.
        check(GhostPlane.over(255, plane, half) == 143 && GhostPlane.over(255, (byte[]) null, half) == 255 && GhostPlane.over(0, plane, half) == 0, "A tone shown over the plane");
    }

    // ---- Glass (stage 3) ----

    /** Glass through the screen's layers: pages 0 and 2 under page 1, each Half; Hidden left out; one plane, whatever the page count. */
    static void glass() throws Exception {
        DrawingBook book = book();
        book.setTracing(book.tracing().withPaper(TracingPaper.GLASS));
        byte[] plane = plane(book);
        ToneDocument page = book.current();
        int only0 = 12 * W + 12, both = 17 * W + 17, only2 = 22 * W + 22;
        check(shown(page, plane, only0) == 143 && shown(page, plane, only2) == 143, "Glass: the pages under and over the page drawn on, each Half (143)");
        check(Math.abs(shown(page, plane, both) - 255 * .56 * .56) <= 1, "where they cross, both");
        check(shown(page, plane, 30 * W + 40) == 0, "the page drawn on Solid on top");
        book.setTracing(book.tracing().withStep(2, TracingPaper.HIDDEN));
        byte[] hidden = plane(book);
        check(shown(page, hidden, only2) == 255 && shown(page, hidden, only0) == 143, "a Hidden page left out");
        // Memory: one plane a page's size, however many pages show through.
        DrawingBook many = book();
        for (int i = 0; i < 20; i++) { many.addPage(); square(many.current(), i, i + 2, 0); }
        many.select(1);
        many.setTracing(many.tracing().withPaper(TracingPaper.GLASS));
        check(many.ghostSources().size() == TracingPaper.GLASS_PAGES && plane(many).length == W * H && plane.length == W * H, "One plane, W x H, for 3 pages or 23; at most GLASS_PAGES ghosts");
        int decoded = 0;
        for (GhostPlane.Source s : many.ghostSources()) if (s.page != null) decoded++;
        check(decoded <= 2, "Glass reads the pages from their bytes, one at a time, never decoding them all: " + decoded + " already decoded");
    }

    /** Layer steps: the drawing page's pairs (not the one drawn on) and a ghost page's, page × layer; the file keeps them. */
    static void layerSteps() throws Exception {
        // The drawing page: pair 0 (black square at 10..20) under pair 1, drawn on.
        DrawingBook book = book();
        ToneDocument page = book.current();
        page.selectLayer(InkWashLayers.WASH); square(page, 10, 20, 0);
        InkWashLayers.add(page, true);
        int at = 12 * W + 12;
        check(InkWashLayers.current(page) == 1, "drawing on pair 1");
        TracingPaper half = book.tracing().withLayerStep(1, 0, TracingPaper.HALF);
        check(half.layerStep(1, 0) == TracingPaper.HALF && half.layerStep(1, 1) == TracingPaper.SOLID && half.layerBits(1) == 1, "Pair 0 Half, pair 1 Solid");
        check(shownSteps(page, null, at, 0) == 0, "Solid: as drawn");
        check(shownSteps(page, null, at, half.layerBits(1)) == 143, "Half: 143");
        check(shownSteps(page, null, at, half.withLayerStep(1, 0, TracingPaper.FAINT).layerBits(1)) == 209, "Faint: 209");
        page.selectLayer(InkWashLayers.INK);
        check(shownSteps(page, null, at, half.layerBits(1)) == 0, "The layer drawn on stays Solid, whatever its step");
        page.selectLayer(3);
        // Plain: layer steps still apply.
        book.setTracing(half);
        check(book.layerSteps(page) == 1 && book.ghostKey() == null, "Plain: layer steps on the page drawn on, no ghosts");
        // Page × layer on a ghost: page 0 Half, its only pair Half: 0.44 × 0.44.
        DrawingBook ghosted = book();
        ghosted.setTracing(tracing(ghosted).withLayerStep(0, 0, TracingPaper.HALF));
        byte[] plane = plane(ghosted);
        check(Math.abs(shown(ghosted.current(), plane, at) - (255 - 255 * .44 * .44)) <= 2, "Page × layer: Half × Half shows as " + shown(ghosted.current(), plane, at));
        check(ghosted.ghostKey().contains("0@100x44g0/1"), "A ghost's layer steps are in the plane's key");
        // Steps follow their pairs (TracingPaper.withLayers) and round-trip through the file's STEP record.
        TracingPaper t = TracingPaper.plain(2).withLayerStep(1, 0, TracingPaper.HALF).withLayerStep(1, 2, TracingPaper.FAINT);
        TracingPaper moved = t.withLayers(1, new int[]{1, 2, 0});
        check(moved.layerStep(1, 2) == TracingPaper.HALF && moved.layerStep(1, 1) == TracingPaper.FAINT && moved.layerStep(1, 0) == TracingPaper.SOLID, "Steps follow a pair's move");
        check(t.withLayers(1, new int[]{-1, 0, 1, 2}).layerStep(1, 1) == TracingPaper.HALF && t.withLayers(1, new int[]{-1, 0, 1, 2}).layerStep(1, 0) == TracingPaper.SOLID, "a new pair is Solid");
        check(TracingPaper.read(null, null, t.stepBytes(), 2).equals(t) && t.step(1) == TracingPaper.UNSET, "Layer steps round-trip through STEP, the page's own step unset");
        BookOptions.Covers covers = new BookOptions.Covers(); covers.tracing = t;
        check(covers.any(), "Layer steps alone are a record to keep");
    }
    /** What the screen shows at pixel {@code i} with {@code layerBits} ({@link #shown} with the drawing page's layer steps). */
    static int shownSteps(ToneDocument page, byte[] ghost, int i, int layerBits) {
        byte[][] tones = new byte[ToneDocument.STACK + 2][], alpha = new byte[ToneDocument.STACK + 2][];
        int[] opacity = new int[ToneDocument.STACK + 2];
        int n = page.visibleLayers(tones, alpha, opacity, ghost, layerBits), result = page.paperTone();
        for (int k = 0; k < n; k++) {
            int a = alpha[k][i] & 255;
            if (a == 0) continue;
            if (opacity[k] != 100) a = (a * opacity[k] + 50) / 100;
            result = ((tones[k][i] & 255) * a + result * (255 - a) + 127) / 255;
        }
        return result;
    }

    // ---- never in the page ----

    static void notBaked() throws Exception {
        DrawingBook plain = book(), traced = book();
        traced.setTracing(tracing(traced).withGhost(2).withStep(0, TracingPaper.SOLID));
        check(plane(traced) != null, "The traced book has ghosts");
        ToneDocument a = plain.current(), b = traced.current();
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) check(a.compositeTone(x, y) == b.compositeTone(x, y), "The eyedropper's tone is the page's at " + x + ", " + y);
        check(Arrays.equals(DrawingBook.encode(a), DrawingBook.encode(b)), "The page's bytes are the same");
        check(Arrays.equals(PagePicture.of(a.layerSnapshot()).encode(), PagePicture.of(b.layerSnapshot()).encode()), "and its picture");
        FillRegion.Room ra = FillTool.region(a, 5, 5), rb = FillTool.region(b, 5, 5);
        check(ra == null && rb == null || ra != null && rb != null && ra.area == rb.area && Arrays.equals(ra.at, rb.at), "Fill finds the same room");
        File dir = Files.createTempDirectory("tracing").toFile();
        try {
            NotebookExport.update(new File(dir, "a"), "nb", plain.snapshot().filed());
            NotebookExport.update(new File(dir, "b"), "nb", traced.snapshot().filed());
            File[] pa = new File(dir, "a/nb").listFiles(), pb = new File(dir, "b/nb").listFiles();
            Arrays.sort(pa); Arrays.sort(pb);
            check(pa.length == pb.length && pa.length >= 3, "The copies: a PNG a page");
            for (int i = 0; i < pa.length; i++) check(Arrays.equals(Files.readAllBytes(pa[i].toPath()), Files.readAllBytes(pb[i].toPath())), "The PNG copy is the same: " + pa[i].getName());
        } finally { NotebookOptionsChecks.delete(dir); }
    }

    // ---- the file ----

    static void records() throws Exception {
        DrawingBook book = book();
        byte[] before = NotebookOptionsChecks.bytes(book);
        BookOptions.Covers none = new BookOptions.Covers(); none.tracing = TracingPaper.plain(3);
        check(!none.any() && BookOptions.bytes(none).length == 0, "Plain, no ghost, no step: no block");
        book.markSaved(book.snapshot());
        TracingPaper full = tracing(book).withGhost(0).withStep(2, TracingPaper.HIDDEN).withStep(0, TracingPaper.FAINT).withStrength(TracingPaper.FAINT);
        book.setTracing(full);
        check(book.unsaved(), "Tracing paper is a change to save");
        byte[] after = NotebookOptionsChecks.bytes(book);
        check(!Arrays.equals(before, after), "and changes the file");
        // Each alone is its own record.
        TracingPaper[] alone = {TracingPaper.plain(3).withPaper(TracingPaper.TRACING), TracingPaper.plain(3).withGhost(2), TracingPaper.plain(3).withStep(1, TracingPaper.SOLID), TracingPaper.plain(3).withStrength(TracingPaper.FAINT)};
        int[] tags = {BookOptions.PAPER, BookOptions.GHOST, BookOptions.STEPS, BookOptions.STRENGTH};
        for (int i = 0; i < alone.length; i++) {
            BookOptions.Covers covers = new BookOptions.Covers(); covers.tracing = alone[i];
            byte[] block = BookOptions.bytes(covers);
            BookOptions.Found found = BookOptions.find(BookOptions.of(block), 0, block.length);
            check(found != null && found.records.size() == 1 && found.records.get(0).tag == tags[i], "Record " + i + " alone is one record");
            check(BookOptions.covers(found, BookOptions.of(block), null, 3).tracing.equals(alone[i]) && BookOptions.length(covers) == block.length, "and reads back");
        }
        // The file, whole and from disk, its pages' reading, the recovery.
        File dir = Files.createTempDirectory("tracing").toFile();
        try {
            File file = new File(dir, "nb.mpaint"); Files.write(file.toPath(), after);
            for (DrawingBook b : new DrawingBook[]{BookCodec.read(new ByteArrayInputStream(after)), BookCodec.open(file)})
                check(b.tracing().equals(full) && !b.unsaved() && b.count() == 3, "Tracing paper reads back from the file, not a change");
            check(BookCodec.openPages(file).covers.tracing.equals(full), "and is what the file's pages record");
            List<PackedPage> a = BookCodec.openPages(file).pages;
            File base = new File(dir, "base.mpaint"); Files.write(base.toPath(), before);
            List<PackedPage> b = BookCodec.openPages(base).pages;
            for (int i = 0; i < 3; i++) check(a.get(i).crc == b.get(i).crc, "The pages are byte for byte as without it");
        } finally { NotebookOptionsChecks.delete(dir); }
        ByteArrayOutputStream recovery = new ByteArrayOutputStream();
        RecoveryCodec.write(recovery, book.snapshot(), "");
        check(RecoveryCodec.read(new ByteArrayInputStream(recovery.toByteArray())).book.tracing().equals(full), "The recovery keeps it");
        ByteArrayOutputStream plainRecovery = new ByteArrayOutputStream();
        RecoveryCodec.write(plainRecovery, book().snapshot(), "");
        check(RecoveryCodec.read(new ByteArrayInputStream(plainRecovery.toByteArray())).book.tracing().isDefault(), "and none for the default");
        // Back to the default: byte for byte as before.
        book.setTracing(TracingPaper.plain(3));
        check(Arrays.equals(NotebookOptionsChecks.bytes(book), before), "Back to Plain, no ghost, no step: the file byte for byte as before");
        // Bad records are ignored.
        byte[] steps = TracingPaper.plain(3).withStep(1, TracingPaper.HALF).stepBytes();
        check(TracingPaper.read(null, null, steps, 3).step(1) == TracingPaper.HALF, "Steps read for their page count");
        check(!TracingPaper.read(null, null, steps, 4).stepped(), "A STEP whose count isn't the page count is ignored");
        byte[] bad = steps.clone(); bad[4 + 2] = 9;
        check(!TracingPaper.read(null, null, bad, 3).stepped(), "and one with a page step out of range");
        bad = steps.clone(); bad[5] = 3;
        check(!TracingPaper.read(null, null, bad, 3).stepped(), "and one with a layer step out of range");
        check(TracingPaper.read(null, TracingPaper.plain(3).withGhost(2).ghostBytes(), null, 2).ghost == -1, "A GHST outside the pages is ignored");
        check(TracingPaper.read(new byte[]{7}, null, null, 3).paper == TracingPaper.PLAIN && TracingPaper.read(new byte[]{2}, null, null, 3).paper == TracingPaper.GLASS, "An unknown Paper is Plain; Glass is kept");
        // Steps and the ghost go with their pages.
        DrawingBook moved = book();
        moved.setTracing(tracing(moved).withGhost(0).withStep(0, TracingPaper.FAINT).withStep(2, TracingPaper.HIDDEN));
        moved.move(0, 2);
        check(moved.tracing().ghost == 2 && moved.tracing().step(2) == TracingPaper.FAINT && moved.tracing().step(1) == TracingPaper.HIDDEN && moved.index() == 0, "A move takes its step and the ghost with it");
        moved.select(2); moved.duplicatePage();
        check(moved.count() == 4 && moved.tracing().step(3) == TracingPaper.FAINT && moved.tracing().ghost == 2, "A copy takes its page's step, the ghost staying on its page");
        moved.deletePage(2);
        // The copy is a layer stacked on it (Stage 3c): the ghost pins the stack, its new bottom.
        check(moved.tracing().ghost == 2 && moved.tracing().count() == 3, "Deleting the ghost's bottom layer: the layer on it keeps the ghost");
        moved.deletePage(2);
        check(moved.tracing().ghost == -1 && moved.tracing().count() == 2, "Deleting the ghost's page ends the ghost");
        moved.addPage();
        check(moved.tracing().count() == 3 && moved.tracing().step(2) == TracingPaper.UNSET, "A new page has no step");
    }

    // ---- the plane is built again only when what it is made of changes ----

    static void key() throws Exception {
        DrawingBook book = book();
        check(book.ghostKey() == null, "Plain: no plane");
        book.setTracing(tracing(book));
        String key = book.ghostKey();
        check(key != null, "Tracing: a plane");
        book.current().begin(); book.current().setTone(45, 30, 0); book.current().finish();
        check(key.equals(book.ghostKey()), "Drawing on the page drawn on leaves it");
        book.setTracing(book.tracing().withStep(1, TracingPaper.HIDDEN));
        check(key.equals(book.ghostKey()), "and so does a step on a page that isn't a ghost");
        book.setTracing(book.tracing().withStep(0, TracingPaper.FAINT));
        check(!key.equals(book.ghostKey()), "A ghost's step changes it");
        key = book.ghostKey();
        book.setTracing(book.tracing().withGhost(2));
        check(!key.equals(book.ghostKey()), "So does the ghost");
        key = book.ghostKey();
        book.setTracing(book.tracing().withPaper(TracingPaper.PLAIN));
        check(book.ghostKey() == null, "Plain again: no plane");
        book.setTracing(book.tracing().withPaper(TracingPaper.TRACING));
        check(key.equals(book.ghostKey()), "and back, the same plane");
        book.move(0, 2);
        check(!key.equals(book.ghostKey()), "A move changes it");
        key = book.ghostKey();
        book.select(2);
        check(!key.equals(book.ghostKey()), "So does a page turn");
    }

    // ---- the lock and the rules ----

    static void rules() throws Exception {
        DrawingBook book = book();
        book.setOptions(NotebookOptions.DEFAULT.withNoErasing(true).withSeconds(180).withLockPages(true));
        book.select(0); book.current().setClock(PageClock.NONE.start().lock(180_000));
        check(book.current().locked(), "A timed-out page");
        book.select(1);
        TracingPaper t = tracing(book).withGhost(0).withStep(0, TracingPaper.FAINT).withStep(2, TracingPaper.HIDDEN);
        book.setTracing(t);
        check(book.tracing().equals(t) && Arrays.deepEquals(t.ghosts(1), new int[][]{{0, 7}}), "Paper, steps and ghosts are free under No Erasing, and a timed-out page can still be a ghost");
    }

    // ---- timing at Manta size ----

    static void timing() throws Exception {
        DrawingBook book = new DrawingBook(NotebookFixture.sketch(1920, 2560, 1));
        book.addPage(); NotebookBench.copy(NotebookFixture.sketch(1920, 2560, 2), book.current());
        book.addPage(); NotebookBench.copy(NotebookFixture.sketch(1920, 2560, 3), book.current());
        book.select(0); book.select(1);
        book.setTracing(tracing(book));
        // The page before decoded (just turned from), the page after from its bytes, as on a page turn.
        double best = Double.MAX_VALUE;
        for (int k = 0; k < 4; k++) { long t = System.nanoTime(); plane(book); best = Math.min(best, (System.nanoTime() - t) / 1e6); }
        System.out.printf(Locale.ROOT, "Tracing plane at 1920 x 2560 (one page decoded, one from its bytes): %.0f ms%n", best);
        Timing.limit(best < 2000, "The Tracing plane builds in time: " + best + " ms");
    }

    // ---- the version before tracing paper ----

    static void write(File file) throws Exception {
        DrawingBook book = NotebookOptionsChecks.book();
        book.setTracing(book.tracing().withPaper(TracingPaper.TRACING).withGhost(1).withStep(0, TracingPaper.FAINT).withStep(1, TracingPaper.HIDDEN).withStrength(TracingPaper.FAINT));
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(file))) { BookCodec.write(out, book.snapshot()); }
        System.out.println("PASS: wrote a Tracing notebook with a ghost and steps");
    }
    static void older(File file) throws Exception {
        DrawingBook read = BookCodec.open(file);
        check(read.tracing().isDefault() && read.count() == 2 && !read.current().unmarked(), "A Tracing notebook the version before it saved again opens Plain, its pages as drawn");
        System.out.println("PASS: the version before tracing paper (af06002) opened a Tracing notebook as drawn and saved it again Plain; it opens here Plain with its pages");
    }
}
