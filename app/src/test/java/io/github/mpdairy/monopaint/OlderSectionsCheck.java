package io.github.mpdairy.monopaint;

/**
 * Compiled against the classes from before Timed Sections (scripts/test_inkwash.sh, from git): a page with started sections
 * ({@code TimedSectionsChecks write}) opens there, its SECT section skipped, as drawn.
 */
public final class OlderSectionsCheck {
    public static void main(String[] args) throws Exception {
        ToneDocument page;
        try (java.io.InputStream in = new java.io.FileInputStream(args[0])) { page = DocumentCodec.read(in); }
        if (page.compositeTone(3, 4) != 0 || page.compositeTone(0, 0) != 255) throw new AssertionError("The older reader opens a page with timed sections as drawn");
        System.out.println("PASS: a page with timed sections opens in the version before them, its SECT section skipped");
    }
}
