package io.github.mpdairy.monopaint;

/**
 * Compiled against the document classes from before mirrors (scripts/test_inkwash.sh): a page with mirrors (MirrorChecks
 * write) opens there, its MIRR section skipped, with the marks it holds ({@code tone} at {@code x, y}).
 */
public final class MirrorOlderReaderCheck {
    public static void main(String[] args) throws Exception {
        ToneDocument page;
        try (java.io.InputStream in = new java.io.FileInputStream(args[0])) { page = DocumentCodec.read(in); }
        int x = Integer.parseInt(args[1]), y = Integer.parseInt(args[2]), tone = Integer.parseInt(args[3]);
        if (page.compositeTone(x, y) != tone || page.compositeTone(0, 0) != 255) throw new AssertionError("The older reader opens the page with mirrors as drawn");
        System.out.println("PASS: a page with mirrors opens in the version before mirrors, its MIRR section skipped");
    }
}
