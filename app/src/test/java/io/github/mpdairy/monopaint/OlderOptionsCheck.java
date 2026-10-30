package io.github.mpdairy.monopaint;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Compiled against the document classes of the version before notebook options (scripts/test_inkwash.sh, from git
 * `233ba6b`): a notebook with a template and a toolbar of its own ({@code NotebookOptionsChecks write}) opens
 * there whole and from its file with its pages as drawn, the options' records passed by as records it doesn't know, its
 * shelf tile read; saved again there (without the options), for {@code NotebookOptionsChecks older}.
 */
public final class OlderOptionsCheck {
    public static void main(String[] args) throws Exception {
        File file = new File(args[0]);
        byte[] bytes = Files.readAllBytes(file.toPath());
        DrawingBook opened = BookCodec.open(file), read = BookCodec.read(new ByteArrayInputStream(bytes));
        for (DrawingBook book : new DrawingBook[]{opened, read}) {
            if (book.count() != 2) throw new AssertionError("The version before options reads the notebook's pages");
            for (int i = 0; i < 2; i++) {
                book.select(i);
                if (book.current().unmarked()) throw new AssertionError("Page " + (i + 1) + " reads with its marks in the version before options");
            }
            book.select(0);
        }
        BookPictures.Cover cover = BookPictures.readCover(file);
        if (cover.pages != 2) throw new AssertionError("The version before options' shelf reads the notebook");
        ByteArrayOutputStream again = new ByteArrayOutputStream();
        BookCodec.write(again, read.snapshot());
        Files.write(Paths.get(args[1]), again.toByteArray());
        System.out.println("PASS: the version before options (233ba6b) opens a notebook with a template and a toolbar of its own, whole and from its file, its pages as drawn, its shelf tile too, and saves it again");
    }
}
