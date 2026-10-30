package io.github.mpdairy.monopaint;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Compiled against the document classes from before Erasable (scripts/test_document.sh, from git): a drawing in TSM5
 * ({@code ErasableFileChecks write}) is refused as an unknown format, its file left as it was.
 */
public final class OlderReaderCheck {
    public static void main(String[] args) throws IOException {
        byte[] before = Files.readAllBytes(Paths.get(args[0]));
        String refused = null;
        try { BookCodec.read(new ByteArrayInputStream(before)); } catch (IOException e) { refused = e.getMessage(); }
        String preview = null;
        try { BookCodec.readFirstPage(new ByteArrayInputStream(before)); } catch (IOException e) { preview = e.getMessage(); }
        byte[] after = Files.readAllBytes(Paths.get(args[0]));
        if (!"Unknown painting format".equals(refused) || !"Unknown painting format".equals(preview) || !java.util.Arrays.equals(before, after))
            throw new AssertionError("An older reader refuses a TSM5 drawing cleanly: " + refused + " / " + preview);
        System.out.println("PASS: an older version refuses a TSM5 drawing (\"Unknown painting format\") and leaves the file as it was");
    }
}
