package io.github.mpdairy.monopaint;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Compiled against the document classes from before magnets (scripts/test_document.sh, from git): a book whose pages
 * hold magnets ({@code MagnetFileChecks write}) opens, every page as drawn, the WORD section skipped as an unknown
 * one; the file is left as it was.
 */
public final class MagnetOlderReaderCheck {
    public static void main(String[] args) throws IOException {
        byte[] before = Files.readAllBytes(Paths.get(args[0]));
        DrawingBook book = BookCodec.read(new ByteArrayInputStream(before));
        String first = hash(book.snapshot().page(0)), second = hash(book.snapshot().page(1));
        String preview = hash(BookCodec.readFirstPage(new ByteArrayInputStream(before)));
        if (!first.equals(args[1]) || !second.equals(args[2]) || !preview.equals(args[1]) || !java.util.Arrays.equals(before, Files.readAllBytes(Paths.get(args[0]))))
            throw new AssertionError("An older reader opens a drawing with magnets as drawn: " + first + " " + second + " " + preview + " expected " + args[1] + " " + args[2]);
        System.out.println("PASS: an older version (before magnets) opens a drawing with magnets, every page as drawn, skipping their section, and leaves the file as it was");
    }
    /** As {@code MagnetFileChecks.hash}. */
    static String hash(ToneDocument d) {
        long h = d.layerCount();
        for (int y = 0; y < d.height; y++) for (int x = 0; x < d.width; x++) h = h * 31 + d.compositeTone(x, y);
        return Long.toHexString(h);
    }
}
