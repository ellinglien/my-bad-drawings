package io.github.mpdairy.monopaint;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * The words the bin offers ({@link WordsMode}): packs of tiles, shuffled anew each time the bin opens (the owner: "always
 * random"; Paintmageddon shuffles its kit). The bundled kit is a plain text file the owner can edit (assets/words/default.txt, a tile a line, repeats
 * on purpose: the "s", "ing" and "the" tiles); any {@code *.words.txt} beside the drawings (Document/Inkwash, where
 * {@link BrushImport} reads too) is a pack of its own, shown first: any text, split into words, lower case, repeats
 * kept. Unlike a brush import the file is left as it is, a pack that stays. Pure but for the files; checked on the
 * desktop ({@code WordPacksChecks}).
 */
final class WordPacks {
    static final String BUNDLED = "words/default.txt", SUFFIX = ".words.txt";
    /** The bundled kit's name in the bin. */
    static final String KIT = "Classic";
    /** The most tiles a pack holds (about 17 of the bin's pages), the most packs read, and the most of a file read. */
    static final int MOST_TILES = 600, MOST_PACKS = 8, MOST_BYTES = 256 * 1024;
    /** What the bin falls back on when the kit's file is missing or holds nothing. */
    static final String[] KIT_DEFAULT = {"a", "the", "and", "ink", "wash", "is", "s", "ing", "line", "you", "I", "dream", "ed", "of"};

    /** A pack: its name and its tiles, in the order the bin shows them. */
    static final class Pack {
        final String name;
        final List<String> tiles;
        Pack(String name, List<String> tiles) { this.name = name; this.tiles = Collections.unmodifiableList(new ArrayList<>(tiles)); }
    }

    private WordPacks() { }

    /** The bundled kit's tiles: a line each as written (case and "&" kept), notes ({@code #}) and blank lines left out, a word no tile holds left out. */
    static List<String> kit(List<String> lines) {
        List<String> tiles = new ArrayList<>();
        for (String line : lines) {
            String tile = line.trim();
            if (tile.isEmpty() || tile.startsWith("#") || !Magnets.validWord(tile)) continue;
            if (tiles.size() < MOST_TILES * 2) tiles.add(tile);
        }
        return tiles.isEmpty() ? Arrays.asList(KIT_DEFAULT) : tiles;
    }
    /**
     * An imported pack's tiles from any text: its words in order, split at spaces and punctuation, lower case, repeats
     * kept; an apostrophe inside a word stays ("don't"); a word longer than a tile holds is left out; at most
     * {@link #MOST_TILES}.
     */
    static List<String> words(String text) {
        List<String> tiles = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        int n = text.length();
        for (int i = 0; i <= n && tiles.size() < MOST_TILES; ) {
            int c = i < n ? text.codePointAt(i) : ' ';
            boolean apostrophe = (c == '\'' || c == '’') && word.length() > 0 && i + 1 < n && Character.isLetterOrDigit(text.codePointAt(i + 1));
            if (Character.isLetterOrDigit(c)) word.appendCodePoint(c);
            else if (apostrophe) word.append('\'');
            else if (word.length() > 0) {
                String w = word.toString().toLowerCase(Locale.ROOT);
                if (Magnets.validWord(w)) tiles.add(w);
                word.setLength(0);
            }
            i += i < n ? Character.charCount(c) : 1;
        }
        return tiles;
    }
    /** Whether a file is a word pack: {@code *.words.txt}, case aside. */
    static boolean isPack(String fileName) {
        String n = fileName.toLowerCase(Locale.ROOT);
        return n.endsWith(SUFFIX) && n.length() > SUFFIX.length();
    }
    /** A pack's name from its file's: "garden.words.txt" is "garden". */
    static String name(String fileName) { return fileName.substring(0, fileName.length() - SUFFIX.length()); }
    /** {@code tiles} shuffled (Fisher-Yates, as Paintmageddon's). */
    static List<String> shuffled(List<String> tiles, Random random) {
        List<String> out = new ArrayList<>(tiles);
        Collections.shuffle(out, random);
        return out;
    }

    // The session's packs

    private static final Random SESSION = new Random();
    /** Each pack as read this session, in its file's order, by what it was read from (a file's name, length and time; the kit). */
    private static final Map<String, Pack> READ = new HashMap<>();
    /** The packs, shuffled anew: every {@code *.words.txt} in {@code folder} (null: none), by name, then the kit. */
    static synchronized List<Pack> session(File folder) { return packs(folder, SESSION, READ); }
    /** {@link #session}'s work, with its own shuffle and memory of what it read; each call shuffles every pack again. */
    static List<Pack> packs(File folder, Random random, Map<String, Pack> read) {
        List<Pack> packs = new ArrayList<>();
        File[] files = folder == null ? null : folder.listFiles();
        if (files != null) {
            Arrays.sort(files);
            for (File f : files) {
                if (packs.size() >= MOST_PACKS) break;
                if (!f.isFile() || !isPack(f.getName())) continue;
                String key = f.getName() + "|" + f.length() + "|" + f.lastModified();
                Pack pack = read.get(key);
                if (pack == null) {
                    List<String> tiles = words(read(f));
                    if (tiles.isEmpty()) continue;
                    pack = new Pack(name(f.getName()), tiles);
                    read.put(key, pack);
                }
                packs.add(new Pack(pack.name, shuffled(pack.tiles, random)));
            }
        }
        Pack kit = read.get(BUNDLED);
        if (kit == null) {
            List<String> lines;
            try { lines = Assets.lines(BUNDLED); } catch (IOException e) { lines = Collections.emptyList(); }
            kit = new Pack(KIT, kit(lines));
            read.put(BUNDLED, kit);
        }
        packs.add(new Pack(kit.name, shuffled(kit.tiles, random)));
        return packs;
    }
    /** The start of {@code file} as text, at most {@link #MOST_BYTES}; nothing if it can't be read. */
    private static String read(File file) {
        try (java.io.InputStream in = new java.io.FileInputStream(file)) {
            byte[] bytes = new byte[MOST_BYTES];
            int length = 0;
            for (int n; length < bytes.length && (n = in.read(bytes, length, bytes.length - length)) > 0; ) length += n;
            return new String(bytes, 0, length, StandardCharsets.UTF_8);
        } catch (IOException e) { return ""; }
    }
}
