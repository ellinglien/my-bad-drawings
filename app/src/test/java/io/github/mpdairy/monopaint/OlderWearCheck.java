package io.github.mpdairy.monopaint;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;

/**
 * Compiled against the version before cover wear (scripts/test_inkwash.sh, from git `ca133f7`): a notebook whose front
 * cover's design is rubbed off ({@code CoverWearChecks write}) opens there, whole and from its file, the cover's WORN
 * section skipped and its design whole; the shelf reads the front cover's picture, the wear after it passed by.
 */
public final class OlderWearCheck {
    public static void main(String[] args) throws Exception {
        File file = new File(args[0]);
        byte[] bytes = Files.readAllBytes(file.toPath());
        Assets.install(Assets.folder(Assets.FOLDER));
        for (DrawingBook book : new DrawingBook[]{BookCodec.open(file), BookCodec.read(new ByteArrayInputStream(bytes))}) {
            book.name("2026-10-19 brisk-bean notebook");
            book.select(DrawingBook.FRONT);
            ToneDocument front = book.current();
            if (book.count() != 3 || front.paperPixels() == null) throw new AssertionError("The version before reads the notebook and its front cover");
            byte[] paper = front.paperPixels();
            for (int i = 0; i < paper.length; i++)
                if (front.compositeTone(i % front.width, i / front.width) != (paper[i] & 255)) throw new AssertionError("and shows its design whole");
        }
        BookPictures.Cover cover = BookPictures.readCover(file);
        if (!cover.frontDrawn || cover.front == null || !PagePicture.decode(cover.front).white()) throw new AssertionError("Its shelf reads the front cover's picture");
        System.out.println("PASS: the version before cover wear (ca133f7) opens a notebook whose cover's design is rubbed off, whole and from its file, the design whole, its shelf picture read");
    }
}
