package io.github.mpdairy.monopaint;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The app's bundled files (app/src/main/assets): paper textures and name lists. Where they are read from
 * is installed once at startup: the APK's assets on the device, the repository's assets folder in the
 * desktop checks. Pure, checked on the desktop.
 */
final class Assets {
    /** Opens an asset by its path under the assets folder, e.g. "names/produce.txt". */
    interface Source { InputStream open(String path) throws IOException; }
    /** The repository's assets folder, for the desktop checks, which run from the repository's root. */
    static final String FOLDER = "app/src/main/assets";
    private static volatile Source source;

    private Assets() { }

    /** Where assets are read from. The first install stands, so every activity may call it in onCreate. */
    static synchronized void install(Source from) { if (source == null) source = from; }
    /** Files under {@code directory}, by their asset path. */
    static Source folder(String directory) { return path -> new FileInputStream(new File(directory, path)); }
    static byte[] bytes(String path) throws IOException {
        Source from = source;
        if (from == null) throw new IOException("No assets installed, reading " + path);
        try (InputStream in = from.open(path)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[16384];
            for (int n; (n = in.read(buffer)) > 0; ) out.write(buffer, 0, n);
            return out.toByteArray();
        }
    }
    /** A text asset's lines, trimmed, blank lines left out. */
    static List<String> lines(String path) throws IOException { return lines(bytes(path)); }
    /** UTF-8 text's lines, trimmed, blank lines and a byte order mark (which some editors save) left out. */
    static List<String> lines(byte[] text) {
        List<String> lines = new ArrayList<>();
        for (String line : new String(text, StandardCharsets.UTF_8).replace("\uFEFF", "").split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) lines.add(trimmed);
        }
        return lines;
    }
}
