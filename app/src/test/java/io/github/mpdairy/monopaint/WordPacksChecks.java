package io.github.mpdairy.monopaint;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The bin's words ({@link WordPacks}): the bundled kit as written, its repeats kept; an imported pack's words from any
 * text; every pack shuffled anew each time the bin opens; packs beside the drawings shown first and their files left as they are.
 */
public final class WordPacksChecks {
    public static void main(String[] args) throws IOException {
        Assets.install(Assets.folder(Assets.FOLDER));
        kit(); parsing(); session();
        System.out.println("PASS: word packs: the bundled kit reads " + kitSize + " tiles as written (& and I kept, " + esses + " s, " + ings + " ing, " + thes + " the), "
                + "notes and blank lines left out; an imported text splits at spaces and punctuation, lower case, repeats and inner apostrophes kept, at most "
                + WordPacks.MOST_TILES + " tiles; *.words.txt packs come first by name, each shuffled anew each time the bin opens, read again when changed, their files left untouched");
    }
    static int kitSize, esses, ings, thes;
    static void kit() throws IOException {
        List<String> kit = WordPacks.kit(Assets.lines(WordPacks.BUNDLED));
        kitSize = kit.size();
        esses = Collections.frequency(kit, "s"); ings = Collections.frequency(kit, "ing"); thes = Collections.frequency(kit, "the");
        check(kitSize == 427, "The kit holds the owner's merged list: 258 words, 427 tiles (" + kitSize + ")");
        check(esses == 19 && ings == 6 && thes == 9 && Collections.frequency(kit, "ed") == 4 && Collections.frequency(kit, "ly") == 6,
                "Repeats kept: s " + esses + ", ing " + ings + ", the " + thes);
        check(kit.contains("&") && kit.contains("I") && kit.contains("TV") && kit.contains("young") && kit.contains("symphony"), "Tiles as written");
        for (String t : kit) check(!t.startsWith("#") && Magnets.validWord(t), "Only tiles: " + t);
        check(WordPacks.kit(Arrays.asList("# only a note", "")).equals(Arrays.asList(WordPacks.KIT_DEFAULT)), "An empty kit falls back on a few words");
        check(WordPacks.kit(Arrays.asList("ok", "far too long a word for any tile", "fine")).equals(Arrays.asList("ok", "fine")), "A line no tile holds is left out");
    }
    static void parsing() {
        check(WordPacks.words("The moon, the MOON! Don't stop—singing...").equals(Arrays.asList("the", "moon", "the", "moon", "don't", "stop", "singing")),
                "Split at spaces and punctuation, lower case, repeats and inner apostrophes kept: " + WordPacks.words("The moon, the MOON! Don't stop—singing..."));
        check(WordPacks.words("rock’n’roll 'quoted' tail'").equals(Arrays.asList("rock'n'roll", "quoted", "tail")), "Curly apostrophes inside a word; quotes round one dropped");
        check(WordPacks.words("café naïve 2026").equals(Arrays.asList("café", "naïve", "2026")), "Accents and numbers are words");
        check(WordPacks.words("a supercalifragilisticexpialidocious b").equals(Arrays.asList("a", "b")), "A word longer than a tile is left out");
        StringBuilder many = new StringBuilder();
        for (int i = 0; i < 2000; i++) many.append("word ");
        check(WordPacks.words(many.toString()).size() == WordPacks.MOST_TILES, "At most " + WordPacks.MOST_TILES + " tiles a pack");
        check(WordPacks.isPack("garden.words.txt") && WordPacks.isPack("Birds.WORDS.TXT") && !WordPacks.isPack(".words.txt") && !WordPacks.isPack("garden.txt")
                && !WordPacks.isPack("Inkwash import.txt"), "Packs are *.words.txt");
        check(WordPacks.name("garden.words.txt").equals("garden"), "A pack's name is its file's");
        List<String> tiles = Arrays.asList("a", "b", "c", "d", "e", "f", "g");
        List<String> once = WordPacks.shuffled(tiles, new Random(3));
        check(!once.equals(tiles) && once.containsAll(tiles) && once.size() == tiles.size(), "Shuffled, every tile kept");
    }
    static void session() throws IOException {
        File folder = Files.createTempDirectory("words").toFile();
        try {
            File garden = new File(folder, "garden.words.txt"), birds = new File(folder, "Birds.words.txt");
            Files.write(garden.toPath(), "Rose, rose and thorn.\nleaf".getBytes(StandardCharsets.UTF_8));
            Files.write(birds.toPath(), "wren robin wren".getBytes(StandardCharsets.UTF_8));
            Files.write(new File(folder, "notes.txt").toPath(), "not a pack".getBytes(StandardCharsets.UTF_8));
            Files.write(new File(folder, "empty.words.txt").toPath(), "!!! ...".getBytes(StandardCharsets.UTF_8));
            byte[] gardenBytes = Files.readAllBytes(garden.toPath());
            Map<String, WordPacks.Pack> memory = new HashMap<>();
            Random random = new Random(11);
            List<WordPacks.Pack> packs = WordPacks.packs(folder, random, memory);
            check(packs.size() == 3 && packs.get(0).name.equals("Birds") && packs.get(1).name.equals("garden") && packs.get(2).name.equals(WordPacks.KIT),
                    "Packs by name, then the kit; an empty one and other files left out");
            List<String> rose = packs.get(1).tiles;
            check(rose.size() == 5 && Collections.frequency(rose, "rose") == 2, "A pack keeps its repeats: " + rose);
            List<WordPacks.Pack> again = WordPacks.packs(folder, random, memory);
            List<String> kitSorted = new java.util.ArrayList<>(packs.get(2).tiles), againSorted = new java.util.ArrayList<>(again.get(2).tiles);
            Collections.sort(kitSorted); Collections.sort(againSorted);
            check(kitSorted.equals(againSorted) && !again.get(2).tiles.equals(packs.get(2).tiles), "Shuffled anew each time the bin opens: the same tiles, a new order");
            check(Arrays.equals(Files.readAllBytes(garden.toPath()), gardenBytes) && garden.exists(), "The pack's file is left as it is");
            Files.write(garden.toPath(), "fern moss fern bark".getBytes(StandardCharsets.UTF_8));
            garden.setLastModified(garden.lastModified() + 5000);
            List<WordPacks.Pack> changed = WordPacks.packs(folder, random, memory);
            check(changed.get(1).tiles.size() == 4 && changed.get(1).tiles.contains("moss"), "A changed pack is read again");
            check(WordPacks.packs(null, random, new HashMap<>()).size() == 1, "No folder: the kit alone");
        } finally {
            File[] files = folder.listFiles();
            if (files != null) for (File f : files) f.delete();
            folder.delete();
        }
    }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
