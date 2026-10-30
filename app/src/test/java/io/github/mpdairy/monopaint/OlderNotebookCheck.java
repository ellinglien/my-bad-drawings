package io.github.mpdairy.monopaint;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Compiled against the document classes from before notebooks (scripts/test_document.sh, from git `580a326`): a
 * notebook with its block of pictures ({@code NotebookFileChecks write}) opens, every page decodes as written, and the
 * older version saves it again as a plain book.
 */
public final class OlderNotebookCheck {
    public static void main(String[] args) throws Exception {
        byte[] file = Files.readAllBytes(Paths.get(args[0]));
        DrawingBook book = BookCodec.read(new ByteArrayInputStream(file));
        if (book.count() != 3) throw new AssertionError("An older version reads a notebook's three pages: " + book.count());
        int[][] marks = {{0, 10, 30}, {5, 11, 70}, {10, 12, 110}};
        for (int i = 0; i < 3; i++) {
            book.select(i);
            int tone = book.current().compositeTone(marks[i][0] + 2, marks[i][1] + 2);
            if (tone != marks[i][2]) throw new AssertionError("Page " + (i + 1) + " reads as written in an older version: " + tone);
        }
        if (BookCodec.readFirstPage(new ByteArrayInputStream(file)).compositeTone(2, 12) != 30) throw new AssertionError("An older version previews page 1");
        ByteArrayOutputStream again = new ByteArrayOutputStream();
        BookCodec.write(again, book.snapshot());
        if (BookCodec.read(new ByteArrayInputStream(again.toByteArray())).count() != 3) throw new AssertionError("An older version saves it again");
        System.out.println("PASS: an older version (580a326) opens a notebook with its pictures, reads every page as written, previews page 1 and saves it again");
    }
}
