package io.github.mpdairy.monopaint;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Desktop checks for {@link BrushVoice}: a brush in friendly, complete sentences (docs/TEXT-STYLE.md). */
public final class VoiceChecks {
    public static void main(String[] args) throws IOException {
        Assets.install(Assets.folder(Assets.FOLDER));
        personalities();
        presets();
        rolled();
        examples();
        sameAsLab();
        System.out.println("PASS: every brush opens with one written sentence of what it is and what stands out, in one of four shapes"
                + " picked by its facts (the special tools, " + BrushVoice.personalities().size() + " of them, with their own words from"
                + " personalities.txt instead), then how it behaves in complete sentences without saying it again, at most "
                + BrushVoice.MOST_SENTENCES + " sentences, an opener at most " + BrushVoice.OPENER_MOST + " characters; every fact a rolled brush"
                + " can have has its words; no brush text a person reads has a semicolon; the web lab's voice.js has the same rules and says"
                + " every Basic tool and 1,500 rolled brushes as the app does, and Brush Studio carries them");
    }

    /** No semicolons and no dash pileups in text a person reads, every sentence whole: a capital first, a full stop (or ! or ?) last. */
    static void readable(String text, String what) {
        check(!text.contains(";"), what + " has no semicolons: " + text);
        check(!text.contains("—") && !text.contains("--"), what + " has no dashes: " + text);
        for (String s : ModularWords.sentences(text)) {
            check(!s.isEmpty() && (Character.isUpperCase(s.charAt(0)) || Character.isDigit(s.charAt(0))), what + ": a sentence starts with a capital: " + s);
            char last = s.charAt(s.length() - 1);
            check(last == '.' || last == '!' || last == '?', what + ": a sentence ends with its full stop: " + s);
        }
    }

    /** assets/brushes/personalities.txt: the special tools' own words, each line a Basic tool and one or two short sentences. */
    static void personalities() throws IOException {
        Map<String, String> all = BrushVoice.personalities();
        Set<String> names = new HashSet<>();
        for (ToolPresets.Preset p : ToolPresets.all()) check(names.add(p.name), "Basic tools have names of their own: " + p.name);
        check(!all.isEmpty() && all.size() < ToolPresets.all().size() / 3, "Only the special tools have their own words: " + all.size());
        for (Map.Entry<String, String> e : all.entrySet()) {
            check(names.contains(e.getKey()), BrushVoice.PERSONALITIES + " names a Basic tool: " + e.getKey());
            readable(e.getValue(), e.getKey() + "'s own words");
            List<String> sentences = ModularWords.sentences(e.getValue());
            check(sentences.size() <= BrushVoice.PERSONALITY_SENTENCES, e.getKey() + "'s own words are one or two sentences: " + e.getValue());
            for (String s : sentences) check(s.length() <= BrushVoice.SENTENCE_MOST, e.getKey() + "'s sentence is short: " + s);
        }
        for (String special : new String[]{"Damp tissue", "Wax resist", "Masking fluid", "Sumi", "Clear water", "Stump"}) check(all.containsKey(special), special + " keeps its own words");
        // A line without ": " or a # note says nothing.
        Map<String, String> some = BrushVoice.personalities(java.util.Arrays.asList("# Fineliner: notes", "Steady line: A pen.", "nonsense"));
        check(some.size() == 1 && "A pen.".equals(some.get("Steady line")), "notes and stray lines are left out: " + some);
    }

    /**
     * Each Basic tool: its own words or a written opener of one sentence, then how it behaves, never saying again what
     * the opener said; whole sentences in the Tools page's and Brush Lab's budgets; openers in several shapes.
     */
    static void presets() {
        Map<String, Integer> starts = new HashMap<>();
        int written = 0;
        for (ToolPresets.Preset p : ToolPresets.all()) {
            ModularBrush b = p.brush().parts;
            String about = BrushVoice.about(b), mine = BrushVoice.personality(b);
            readable(about, p.name);
            check(about.length() <= BrushVoice.ABOUT_MOST, p.name + "'s words fit Brush Lab's budget: " + about.length());
            if (!mine.isEmpty()) check(about.startsWith(mine) && about.length() > mine.length(), p.name + "'s own words, then how it behaves: " + about);
            else {
                List<String> used = new ArrayList<>();
                String opener = BrushVoice.opener(ModularWords.plainWords(b), used);
                check(about.startsWith(opener + " ") && ModularWords.sentences(opener).size() == 1 && opener.length() <= BrushVoice.OPENER_MOST, p.name + " opens with one written sentence: " + about);
                String rest = about.substring(opener.length()).toLowerCase(java.util.Locale.ROOT);
                for (String u : used) {
                    String[] row = openerRow(u);
                    check(!rest.contains(row[1]), p.name + ": the behaviour doesn't say again what the opener said (" + row[1] + "): " + about);
                }
                starts.merge(opener.startsWith("Here's") ? "Here's" : opener.startsWith("This is") ? "This is" : opener.startsWith("Meet") ? "Meet" : "plain", 1, Integer::sum);
                written++;
            }
            readable(p.name + ".", p.name + "'s name");
            check(!p.line.contains(";") && !p.name.contains(";"), p.name + "'s card words have no semicolons: " + p.line);
            for (String item : ModularWords.caption(b)) check(!item.contains(";"), p.name + "'s caption has no semicolons: " + item);
            // A brush that isn't a Basic tool as it comes (a copy renamed) has no own words: a written opener.
            ModularBrush renamed = b.copy();
            renamed.name = p.name + " 2";
            check(BrushVoice.personality(renamed).isEmpty(), p.name + " renamed has no own words");
        }
        check(starts.size() == 4, "Openers come in all four shapes: " + starts);
        for (int n : starts.values()) check(n < written / 2, "No one shape for most openers: " + starts);
    }
    /** The opener row a used unit met. */
    private static String[] openerRow(String unit) {
        for (String[] row : BrushVoice.OPENERS) {
            String f = Character.toLowerCase(unit.charAt(0)) + unit.substring(1);
            if (f.matches("(?:" + row[0] + ")") || unit.matches("(?:" + row[0] + ")")) return row;
        }
        throw new AssertionError("no opener for " + unit);
    }

    /** Rolled brushes, every style: every fact has words, in at most four whole sentences. */
    static void rolled() {
        Map<String, String> store = new HashMap<>();
        BrushLibrary.Store s = new BrushLibrary.Store() { public String get(String k) { return store.get(k); } public void put(String k, String v) { if (v == null) store.remove(k); else store.put(k, v); } };
        BrushLab lab = new BrushLab(new ToolSlots(s, new Random(1)), 2, new Random(11));
        BrushVoice.MISSED.clear();
        int n = 0;
        for (ModularStyle style : ModularStyle.all()) {
            lab.pickStyle(style);
            for (int i = 0; i < 300; i++) {
                lab.randomize();
                ModularBrush b = lab.parts();
                String words = BrushVoice.about(b);
                readable(words, "A rolled " + style.name);
                List<String> sentences = ModularWords.sentences(words);
                check(sentences.size() >= 2 && sentences.size() <= BrushVoice.MOST_SENTENCES + 1 && words.length() <= BrushVoice.ABOUT_MOST, "An opener and at most " + BrushVoice.MOST_SENTENCES + " sentences: " + words);
                check(sentences.get(0).length() <= BrushVoice.OPENER_MOST, "A rolled brush's opener is short: " + sentences.get(0));
                check(!words.contains("Erasable") && !words.toLowerCase(java.util.Locale.ROOT).contains("erasable"), "Erasable is the eraser's words: " + words);
                for (String item : ModularWords.caption(b)) check(!item.contains(";"), "A caption has no semicolons: " + item);
                n++;
            }
        }
        check(BrushVoice.MISSED.isEmpty(), "Every fact of " + n + " rolled brushes has its words: " + BrushVoice.MISSED);
    }

    /** The owner's examples (2026-10-22), and a few of the rules' turns. */
    static void examples() {
        String benDay = BrushVoice.about(preset("Ben-Day brush"));
        check(benDay.contains("Pressing harder makes the dots bigger.") && benDay.endsWith("It dries as soon as you lift the pen."), "Ben-Day: " + benDay);
        String gouache = BrushVoice.about(preset("Wet gouache"));
        check(gouache.contains("gouache") && gouache.contains("wet until you dry it") && !gouache.contains("It stays wet until you tap"), "Wet gouache says it stays wet once: " + gouache);
        String tissue = BrushVoice.about(preset("Damp tissue"));
        check(tissue.startsWith("Blot paint back up"), "Damp tissue keeps its own words: " + tissue);
        // The opener: what it is and what stands out, in the brush's own shape; the same brush the same words.
        String dust = BrushVoice.opener("Vine charcoal. Sheds dust when tilted.", new ArrayList<>());
        check(dust.toLowerCase(java.util.Locale.ROOT).contains("stick of vine charcoal") && dust.matches(".*(leaves|leaving) dust in its wake\\."), "A charcoal stick leaves dust in its wake: " + dust);
        check(BrushVoice.about("Ink line. Pressure widens.", "").equals(BrushVoice.about("Ink line. Pressure widens.", "")), "The same facts, the same words");
        String runny = BrushVoice.about(preset("Runny gouache"));
        check(runny.contains("Tilt the page and it runs downhill."), "Runny gouache: " + runny);
        // The facts, said: what it is, two causes doing one thing, one cause doing two, two marks in one sentence.
        check(BrushVoice.say("Ink line, chisel tip, on felt paper. Pressure and tilt widen.", true)
                .equals("It's an ink line with a chisel tip, on felt paper. Pressing harder and tilting the pen both make it wider."), "Two causes, one thing");
        check(BrushVoice.say("HB graphite. Pressure widens and darkens.", true).equals("It's an HB pencil. Pressing harder makes it wider and darker."), "One cause, two things");
        check(BrushVoice.say("Ink line. Pressure widens, speed thins.", true).equals("It's an ink line. Pressing harder makes it wider, and moving faster makes it thinner."), "Two causes, two things");
        check(BrushVoice.say("Vine charcoal. Soft speckled edge, picks up the texture.", false).equals("It has a soft, speckled edge and catches the paper's grain."), "Two marks in one sentence");
        // What matters most first: a Wet's drying before Control, the marks after; drying at once last.
        check(BrushVoice.say("Wet wash. Blooms, dark edges. Stays wet until dried. Merges while wet, blooms when damp. Pressure widens.", false)
                .equals("It stays wet until you tap the drop to dry it. Wet strokes mix together and bloom as they dry. Pressing harder makes it wider. It dries with dark edges."), "Wet first, a bloom said once");
        check(BrushVoice.say("Ink line. Dries at once. Pressure grows dots.", false).equals("Pressing harder makes the dots bigger. It dries as soon as you lift the pen."), "Dries at once last");
        // Erasable goes unsaid (the eraser's own words), Permanent is said.
        check(BrushVoice.say("Ink line. Erasable.", true).equals("It's an ink line. " + BrushVoice.STEADY) && BrushVoice.say("Ink line. Permanent.", true).endsWith("The eraser can't touch it."), "Erasable unsaid, Permanent said");
        check(BrushVoice.say("Ink line. Wet for 5.6 s.", false).equals("It stays wet for about 6 seconds.") && BrushVoice.say("Ink line. Floats 16 s.", false).equals("It floats on the water for 16 seconds."), "Times in seconds");
    }
    static ModularBrush preset(String name) {
        for (ToolPresets.Preset p : ToolPresets.all()) if (p.name.equals(name)) return p.brush().parts;
        throw new AssertionError("no preset " + name);
    }

    /** voice.js (the web lab's, built into Brush Studio) has {@link BrushVoice#RULES}, row for row. */
    static void sameAsLab() throws IOException {
        String js = new String(Files.readAllBytes(Paths.get("docs/brush-lab/prototypes/modular-lab-src/voice.js")), StandardCharsets.UTF_8);
        String java = new String(Files.readAllBytes(Paths.get("app/src/main/java/io/github/mpdairy/monopaint/BrushVoice.java")), StandardCharsets.UTF_8);
        List<String> jsRows = rows(js, Pattern.compile("(?m)^  \\[(\"(?:(?!\\], \\[).)*)\\],$")), javaRows = rows(java, Pattern.compile("(?m)^ {12}\\{(\".*)\\},$"));
        check(javaRows.size() == BrushVoice.RULES.length + BrushVoice.OPENERS.length, "BrushVoice's rows read: " + javaRows.size());
        check(jsRows.equals(javaRows), "voice.js has BrushVoice's rules, row for row (" + jsRows.size() + " / " + javaRows.size() + ")");
        String studio = new String(Files.readAllBytes(Paths.get("docs/brush-lab/studio.html")), StandardCharsets.UTF_8);
        for (String row : jsRows) check(studio.contains("[" + row + "]"), "Brush Studio is built with voice.js (node docs/brush-lab/studio-src/build.cjs): " + row);
        twins();
    }
    /** voice.js says the same as BrushVoice for every Basic tool and 1,500 rolled brushes (by Node; skipped without it). */
    static void twins() throws IOException {
        List<String[]> brushes = new ArrayList<>();
        for (ToolPresets.Preset p : ToolPresets.all()) brushes.add(new String[]{ModularWords.plainWords(p.brush().parts), BrushVoice.personality(p.brush().parts)});
        Map<String, String> store = new HashMap<>();
        BrushLibrary.Store s = new BrushLibrary.Store() { public String get(String k) { return store.get(k); } public void put(String k, String v) { if (v == null) store.remove(k); else store.put(k, v); } };
        BrushLab lab = new BrushLab(new ToolSlots(s, new Random(1)), 2, new Random(23));
        for (int i = 0; i < 1500; i++) { lab.pickStyle(ModularStyle.all().get(i % ModularStyle.all().size())); lab.randomize(); brushes.add(new String[]{ModularWords.plainWords(lab.parts()), ""}); }
        StringBuilder in = new StringBuilder();
        for (String[] b : brushes) in.append(b[0]).append('\t').append(b[1]).append('\n');
        List<String> said;
        try {
            Process node = new ProcessBuilder("node", "docs/brush-lab/studio-src/voice-check.cjs").redirectErrorStream(true).start();
            node.getOutputStream().write(in.toString().getBytes(StandardCharsets.UTF_8));
            node.getOutputStream().close();
            said = java.util.Arrays.asList(new String(readAll(node.getInputStream()), StandardCharsets.UTF_8).split("\n"));
            node.waitFor();
        } catch (IOException | InterruptedException e) { System.out.println("SKIP: voice.js against BrushVoice needs Node (" + e.getMessage() + ")"); return; }
        check(said.size() == brushes.size(), "voice.js said every brush: " + said.size() + " of " + brushes.size() + (said.isEmpty() ? "" : ": " + said.get(0)));
        for (int i = 0; i < brushes.size(); i++) {
            String app = BrushVoice.about(brushes.get(i)[0], brushes.get(i)[1]);
            check(app.equals(said.get(i)), "voice.js says it as the app does: \"" + app + "\" / \"" + said.get(i) + "\" (" + brushes.get(i)[0] + ")");
        }
    }
    private static byte[] readAll(java.io.InputStream in) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[16384];
        for (int n; (n = in.read(buffer)) > 0; ) out.write(buffer, 0, n);
        return out.toByteArray();
    }
    private static List<String> rows(String text, Pattern row) {
        List<String> out = new ArrayList<>();
        for (Matcher m = row.matcher(text); m.find(); ) out.add(m.group(1));
        return out;
    }

    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
