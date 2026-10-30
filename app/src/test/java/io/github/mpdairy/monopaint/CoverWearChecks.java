package io.github.mpdairy.monopaint;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.util.Arrays;

/**
 * The eraser rubbing a cover's design off ({@link CoverWear}, {@link ToneDocument#rubDesign}): the Everything eraser takes
 * the pattern down to white where it goes, never the tape or the label; one Undo step with what it erased; a kind eraser
 * leaves it; Clear page brings it back; a new design is rubbed where the old one was; kept through the file, the
 * recovery, the shelf's picture and the PDF; a notebook never rubbed is written byte for byte as before. {@code write
 * <file>} writes a rubbed notebook for the version before ({@code OlderWearCheck}, ca133f7).
 */
public final class CoverWearChecks {
    static final int W = CoverChecks.W, H = CoverChecks.H;
    static final String NAME = "2026-10-19 brisk-bean notebook";

    public static void main(String[] args) throws Exception {
        if (args.length == 2 && args[0].equals("write")) { Files.write(new File(args[1]).toPath(), CoverChecks.bytes(rubbed())); return; }
        Assets.install(Assets.folder(Assets.FOLDER));
        rubs(); file();
        System.out.println("PASS: cover wear: the eraser rubs a cover's design off to white where it goes, the tape and the label untouched, one Undo step with the marks it took, "
                + "a kind eraser leaves it, Clear page brings it back, a new design is rubbed there too; kept through the file, the recovery, the shelf's tile and the PDF; an unrubbed notebook is written byte for byte as before");
    }
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    /** A dark pixel of the front cover's printed design, clear of the tape and the label: where the checks rub. */
    static int[] dark(ToneDocument front) {
        byte[] paper = front.paperPixels();
        for (int y = 180; y < H - 20; y++) for (int x = 60; x < W - 20; x++) if ((paper[y * W + x] & 255) < 64) return new int[]{x, y};
        throw new AssertionError("The design has ink below the label");
    }
    /** {@link #dark} on a new notebook's front cover, before any rubbing. */
    static int[] dark(DrawingBook fresh) throws IOException { fresh.select(DrawingBook.FRONT); return dark(fresh.current()); }
    static void rub(ToneDocument page, int kinds, int[]... at) {
        EraseBothStroke e = new EraseBothStroke(page, 16f, kinds);
        for (int[] p : at) e.sample(p[0], p[1], 0, Float.NaN, Float.NaN);
        e.finish();
    }
    static int tone(ToneDocument page, int[] p) { return page.compositeTone(p[0], p[1]); }
    /** A notebook whose front cover's design is rubbed off at {@link #dark}. */
    static DrawingBook rubbed() throws IOException {
        DrawingBook book = CoverChecks.book(3, NAME);
        book.select(DrawingBook.FRONT);
        rub(book.current(), 0, dark(book.current()));
        book.offerPicture(book.pictureKey(DrawingBook.FRONT), PagePicture.of(book.current().layerSnapshot()).encode());
        book.select(0);
        return book;
    }

    static void rubs() throws IOException {
        DrawingBook book = CoverChecks.book(2, NAME);
        book.select(DrawingBook.FRONT);
        ToneDocument front = book.current();
        int[] p = dark(front), label = CoverChecks.onLabel(), tape = {6, 200}, beside = {40, 200};
        byte[] made = front.paperPixels();
        int onLabel = tone(front, label), onTape = tone(front, tape);
        // A kind eraser leaves the design.
        rub(front, ToneDocument.KIND_PEN, p);
        check(tone(front, p) < 64 && front.wear() == null && !front.canUndo(), "An eraser limited to a kind leaves the design");
        // A mark on it, then the eraser: mark and design go together, the tape and the label stay.
        CoverChecks.mark(front, p[0] - 2, p[1] - 2, 5, 100);
        int marked = tone(front, p);
        rub(front, 0, p, label, tape, beside);
        check(tone(front, p) == 255, "The eraser rubs the design off to white: " + tone(front, p));
        check(tone(front, label) == onLabel && tone(front, tape) == onTape, "but not the label nor the tape");
        check(front.wear() != null && !front.unmarked() && book.coverDrawn(DrawingBook.FRONT), "A rubbed cover counts as drawn on");
        check(front.undo() && tone(front, p) == marked && front.wear() == null && !front.unmarked(), "One Undo gives the mark and the design back");
        check(front.undo() && front.unmarked() && front.paperPixels() == made, "and the step before it");
        check(front.redo() && front.redo() && tone(front, p) == 255, "Redo rubs it again");
        // A new design is rubbed where the old one was.
        book.setDesign(CoverDesign.TERRAZZO); book.refreshCovers();
        byte[] fresh = NotebookCovers.paper(true, CoverDesign.TERRAZZO, NAME, W, H, null);
        boolean white = true; int inked = 0;
        for (int y = 0; y < H; y++) for (int x = CoverPattern.spine(W); x < W; x++) if (front.wear().at(x, y) && y > 150) { white &= front.compositeTone(x, y) == 255; if ((fresh[y * W + x] & 255) < 128) inked++; }
        check(white && inked > 0 && tone(front, label) == onLabel, "A new design is rubbed off where the old one was (" + inked + " of its ink pixels)");
        // Clear page brings the design back, as one step.
        check(PageClear.offered(front) && front.clearPage() && front.wear() == null && CoverChecks.showsPaper(front), "Clear page brings the design back");
        check(front.undo() && front.wear() != null, "and Undo takes it off again");
        // The back cover too, its tape on the right.
        book.select(DrawingBook.BACK);
        ToneDocument back = book.current();
        int[] spine = {W - 6, 200};
        int onSpine = tone(back, spine);
        rub(back, 0, spine, new int[]{W / 2, 200});
        check(tone(back, spine) == onSpine && back.wear() != null, "The back's tape stays");
    }

    static void file() throws Exception {
        // Never rubbed: written byte for byte as before.
        byte[] covered = CoverChecks.covered();
        check(Arrays.equals(CoverChecks.bytes(CoverChecks.read(covered, NAME)), covered), "A notebook never rubbed is written byte for byte");
        DrawingBook book = rubbed();
        book.select(DrawingBook.FRONT);
        int[] p = dark(CoverChecks.book(1, NAME));
        check(tone(book.current(), p) == 255, "The fixture is rubbed");
        // The file.
        byte[] bytes = CoverChecks.bytes(book);
        DrawingBook read = CoverChecks.read(bytes, NAME);
        read.select(DrawingBook.FRONT);
        check(tone(read.current(), p) == 255 && read.current().wear() != null, "The rubbed design is kept in the file");
        read.select(0); read.select(DrawingBook.BACK); read.select(DrawingBook.FRONT);
        check(tone(read.current(), p) == 255, "and turning pages keeps it");
        // The recovery.
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        RecoveryCodec.write(out, book.snapshot(), "");
        DrawingBook recovered = RecoveryCodec.read(new ByteArrayInputStream(out.toByteArray())).book;
        recovered.name(NAME); recovered.select(DrawingBook.FRONT);
        check(tone(recovered.current(), p) == 255, "The recovery keeps it");
        // The shelf's picture of the front's marks, and its tile.
        PagePicture picture = PagePicture.decode(PagePicture.of(book.current().layerSnapshot()).encode());
        check(picture.marksAlone() && picture.wear() != null && !picture.white(), "The front's picture keeps where it is rubbed");
        byte[] tile = NotebookCovers.shown(true, CoverDesign.DEFAULT, NAME, W, H, null, picture);
        int[] label = CoverChecks.onLabel();
        byte[] plain = NotebookCovers.paper(true, CoverDesign.DEFAULT, NAME, W, H, null);
        check((tile[p[1] * W + p[0]] & 255) == 255 && tile[label[1] * W + label[0]] == plain[label[1] * W + label[0]], "The shelf's tile shows it rubbed, its label whole");
        // The PDF's front cover.
        File dir = Files.createTempDirectory("wear").toFile();
        try {
            DrawingBook.Snapshot s = book.snapshot();
            PackedPage[] covers = s.packedCovers();
            NotebookExport.update(dir, NAME, s.packed(), null, new NotebookExport.Covers(NAME, CoverDesign.DEFAULT, W, H, covers[0], covers[1], s.cover));
            BufferedImage image = javax.imageio.ImageIO.read(new File(new File(dir, NAME), NotebookExport.FRONT_PNG));
            check((image.getRGB(p[0], p[1]) & 255) > 240 && (image.getRGB(label[0], label[1]) & 255) == DotPattern.exportGray(plain[label[1] * W + label[0]] & 255),
                    "The PDF's front cover is rubbed there, its label whole");
        } finally { NotebookMigrationChecks.delete(dir); }
        // The bits themselves.
        CoverWear wear = book.current().wear();
        CoverWear again = CoverWear.decode(wear.encode(), 0, W, H);
        boolean same = true;
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) same &= wear.at(x, y) == again.at(x, y);
        check(same, "The wear reads back as written");
        try { CoverWear.decode(Arrays.copyOf(wear.encode(), 3), 0, W, H); check(false, "A cut wear is refused"); } catch (IOException expected) { }
    }
}
