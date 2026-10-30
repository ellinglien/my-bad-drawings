package io.github.mpdairy.monopaint;

/**
 * Compiled against the document classes from before kinds of mark (scripts/test_inkwash.sh, from git): a page with a
 * KIND section ({@code EraseKindChecks write}) opens there, the section skipped, showing {@code tone} at {@code x, y}.
 */
public final class OlderKindCheck {
    public static void main(String[] args) throws Exception {
        ToneDocument page;
        try (java.io.InputStream in = new java.io.FileInputStream(args[0])) { page = DocumentCodec.read(in); }
        int x = Integer.parseInt(args[1]), y = Integer.parseInt(args[2]), tone = Integer.parseInt(args[3]);
        if (page.compositeTone(x, y) != tone || page.compositeTone(0, 0) != 255) throw new AssertionError("The older reader opens the page with kinds as drawn");
        System.out.println("PASS: a page with kinds of mark opens in the version before them, its KIND section skipped");
    }
}
