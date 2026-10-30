package io.github.mpdairy.monopaint;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Compiled against the document classes of the covers version (scripts/test_inkwash.sh, from git `6e3bae8`, before
 * designs): a notebook in a design whose covers are drawn on ({@code CoverDesignChecks write}) opens there whole and
 * from its file, its covers and cover picture read, the design's record passed by as a record it doesn't know; its
 * shelf tile read; saved again there (without the design), for {@code CoverDesignChecks older}.
 */
public final class OlderDesignCheck {
    public static void main(String[] args) throws Exception {
        File file = new File(args[0]);
        byte[] bytes = Files.readAllBytes(file.toPath());
        DrawingBook opened = BookCodec.open(file), read = BookCodec.read(new ByteArrayInputStream(bytes));
        for (DrawingBook book : new DrawingBook[]{opened, read}) {
            book.name("2026-10-19 brisk-bean notebook", "Oct 19 brisk-bean notebook");
            if (book.count() != 3 || !book.coverDrawn(DrawingBook.FRONT) || !book.coverDrawn(DrawingBook.BACK) || book.cover() != 2)
                throw new AssertionError("The covers version reads the notebook's pages, covers and cover picture");
            for (int i = 0; i < 3; i++) {
                book.select(i);
                int tone = book.current().compositeTone(12 + 5 * i, 22 + 7 * i);
                if (tone != 30 + 40 * i) throw new AssertionError("Page " + (i + 1) + " reads as written in the covers version: " + tone);
            }
            book.select(DrawingBook.FRONT);
            if (book.current().paperPixels() == null) throw new AssertionError("and makes its front cover");
            book.select(0);
        }
        BookPictures.Cover cover = BookPictures.readCover(file);
        if (cover.pages != 3 || cover.cover != 2 || !cover.frontDrawn) throw new AssertionError("The covers version's shelf reads the notebook");
        ByteArrayOutputStream again = new ByteArrayOutputStream();
        BookCodec.write(again, read.snapshot());
        Files.write(Paths.get(args[1]), again.toByteArray());
        System.out.println("PASS: the covers version (6e3bae8) opens a notebook in a design, whole and from its file, its covers and cover picture as drawn, its shelf tile too, and saves it again");
    }
}
