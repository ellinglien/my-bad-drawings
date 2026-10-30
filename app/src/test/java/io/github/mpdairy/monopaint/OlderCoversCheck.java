package io.github.mpdairy.monopaint;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Compiled against the document classes from before covers (scripts/test_inkwash.sh, from git `2a352ed`, the version on
 * the Manta): a notebook whose covers are drawn on ({@code CoverChecks write}) opens from its file as that version opens
 * one and read whole, every page as written, its shelf tile read, and is saved again (without its covers: that version
 * doesn't know them). Then it writes a notebook of its own for {@code CoverChecks older}.
 */
public final class OlderCoversCheck {
    public static void main(String[] args) throws Exception {
        File file = new File(args[0]);
        byte[] bytes = Files.readAllBytes(file.toPath());
        DrawingBook opened = BookCodec.open(file), read = BookCodec.read(new ByteArrayInputStream(bytes));
        for (DrawingBook book : new DrawingBook[]{opened, read}) {
            if (book.count() != 3) throw new AssertionError("The older version reads the notebook's three pages: " + book.count());
            for (int i = 0; i < 3; i++) {
                book.select(i);
                int tone = book.current().compositeTone(12 + 5 * i, 22 + 7 * i);
                if (tone != 30 + 40 * i) throw new AssertionError("Page " + (i + 1) + " reads as written in the older version: " + tone);
            }
        }
        BookPictures.Cover cover = BookPictures.readCover(file);
        if (cover.pages != 3) throw new AssertionError("The older version's shelf reads the notebook");
        ByteArrayOutputStream again = new ByteArrayOutputStream();
        BookCodec.write(again, read.snapshot());
        if (BookCodec.read(new ByteArrayInputStream(again.toByteArray())).count() != 3) throw new AssertionError("The older version saves it again");
        // A notebook of its own, for this version to open.
        DrawingBook own = new DrawingBook(InkWashLayers.blank(240, 300));
        for (int i = 0; i < 3; i++) {
            if (i > 0) own.addPage();
            ToneDocument page = own.current();
            page.begin(); for (int dx = 0; dx < 12; dx++) for (int dy = 0; dy < 12; dy++) page.setTone(10 + i * 5 + dx, 20 + i * 7 + dy, 30 + 40 * i); page.finish();
        }
        own.select(0);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        BookCodec.write(out, own.snapshot());
        Files.write(Paths.get(args[1]), out.toByteArray());
        System.out.println("PASS: the version before covers (2a352ed) opens a notebook whose covers are drawn on, from its file and whole, every page as written, its shelf tile too, and saves it again");
    }
}
