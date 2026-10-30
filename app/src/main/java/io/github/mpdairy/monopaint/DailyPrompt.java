package io.github.mpdairy.monopaint;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Daily Prompt (docs/archive/brush-lab/prototypes/mischief-lab-3.NOTES.md, "3. Daily Prompt"): short drawing and writing
 * cards in our own words, inspired by Lynda Barry's classes (eyes closed, a self-portrait as something, an animal self,
 * four silent panels, the Did, Saw, Heard diary). The owner edits them: they live in a plain text file beside the
 * notebooks ({@link #FILE}, one card a line, {@code #} for notes), read each time a card is wanted, written from
 * {@link #DEFAULTS} whenever it is missing or holds no card. A line is {@code [Diary Page |] [time |] text}; a line that
 * doesn't read is skipped. Shown as the Self-Portrait Prompt (the owner, 2026-10-30: "rename it as 'self portrait
 * prompt'? since it's not always daily unless it's baked into the notebook?"): a notebook setting ({@link
 * NotebookOptions#selfPortrait}) that deals each new page a card at random; Deal Another picks any other. No today's card
 * (the Play menu's row, picked by the day number, until then). Pure, checked on the desktop ({@code DailyPromptChecks}).
 */
final class DailyPrompt {
    private DailyPrompt() {}

    /** The cards' name, Notebook Settings' row and the card's heading; the card's buttons (Title Case: docs/TEXT-STYLE.md). */
    static final String TITLE = "Self-Portrait Prompt", DEAL = "Deal Another", START = "Start", DIARY = "Diary Page";
    /** The cards' file, beside the notebooks (Document/Inkwash). */
    static final String SUFFIX = " prompts.txt", FILE = AppName.NAME + SUFFIX;
    /** The longest a card may be timed, s. */
    static final int MOST_SECONDS = 60 * 60;
    /** The most a page keeps of its card's line, bytes ({@link #line}). */
    static final int MOST_BYTES = 4096;

    /** A card: its time (0: untimed), whether it goes with the Diary Page template ({@link NotebookTemplate#DIARY_PAGE}), its words. */
    static final class Card {
        final int seconds; final boolean diary; final String text;
        Card(int seconds, boolean diary, String text) { this.seconds = seconds; this.diary = diary; this.text = text; }
    }

    /** The template card {@code card} gives the page drawn on alone (its own template, never the notebook's): Diary Page, else null. */
    static NotebookTemplate template(Card card) { return card.diary ? NotebookTemplate.DIARY_PAGE : null; }

    /** The file as first written: how to edit it, then the cards. */
    static final String DEFAULTS = String.join("\n",
        "# " + AppName.NAME + " daily prompts: one card a line. Lines starting with # are notes.",
        "# A time first makes a timed card: \"30 s | ...\" or \"3 min | ...\" (60 min at most). No time: untimed.",
        "# \"Diary Page | ...\" first gives this page alone the Did, Saw and Heard boxes when the card starts.",
        "# Edit, add or delete lines freely. Delete the file, or every card in it, to get these back.",
        "# \"Diary Page times | ...\" sets how long each of the Diary Page's boxes is timed, in order, m:ss.",
        "",
        "Diary Page times | Did 2:30 | Saw 2:30 | Heard 0:30 | Picture 0:30",
        "3 min | Draw yourself skating.",
        "3 min | Draw yourself tiptoeing.",
        "3 min | Draw yourself losing a balloon.",
        "3 min | Draw yourself on a swing.",
        "3 min | Draw yourself getting splashed by a passing car.",
        "3 min | Draw yourself chasing your hat.",
        "3 min | Draw yourself as a deep-sea explorer.",
        "3 min | Draw yourself as a lost traveler.",
        "3 min | Draw yourself as a zombie.",
        "3 min | Draw yourself as a detective.",
        "3 min | Draw yourself singing loudly.",
        "3 min | Draw yourself sneaking.",
        "3 min | Draw yourself dancing sadly.",
        "3 min | Draw yourself as a cactus.",
        "3 min | Draw yourself dropping ice cream.",
        "3 min | Draw yourself vomiting.",
        "3 min | Draw yourself as an ice cream truck driver.",
        "3 min | Draw yourself riding a whale.",
        "3 min | Draw yourself as a mermaid.",
        "3 min | Draw yourself eating spicy food.",
        "3 min | Draw yourself hiding.",
        "3 min | Draw yourself as Batman.",
        "3 min | Draw yourself delivering mail.",
        "3 min | Draw yourself as a world explorer.",
        "3 min | Draw yourself being abducted by aliens.",
        "3 min | Draw yourself being surprised by a loud noise.",
        "3 min | Draw yourself as a melting snowman.",
        "3 min | Draw yourself feeding ducks.",
        "3 min | Draw yourself flying a helicopter.",
        "3 min | Draw yourself blowing bubblegum.",
        "3 min | Draw yourself as a giant.",
        "3 min | Draw yourself as a French person.",
        "3 min | Draw yourself as a robot.",
        "3 min | Draw yourself in the deep sea.",
        "3 min | Draw yourself as a flower.",
        "3 min | Draw yourself as a dragon.",
        "3 min | Draw yourself eating ice cream.",
        "3 min | Draw yourself sneezing.",
        "3 min | Draw yourself wearing clothes that don't fit.",
        "3 min | Draw yourself taking a selfie.",
        "3 min | Draw yourself in a snow globe.",
        "3 min | Draw yourself singing karaoke.",
        "3 min | Draw yourself as a grumpy fairy.",
        "3 min | Draw yourself as a vampire.",
        "3 min | Draw yourself fighting your shadow.",
        "3 min | Draw yourself middle-aged, at work.",
        "3 min | Draw yourself as a werewolf.",
        "3 min | Draw yourself riding a shopping cart.",
        "3 min | Draw yourself running a psychiatry stand.",
        "3 min | Draw yourself as a ninja.",
        "3 min | Draw yourself sailing a bathtub.",
        "3 min | Draw yourself juggling.",
        "3 min | Draw yourself holding balloons.",
        "3 min | Draw yourself as a ghostbuster.",
        "3 min | Draw yourself as a wizard.",
        "3 min | Draw yourself skateboarding.",
        "3 min | Draw yourself at a campfire roasting a marshmallow.",
        "3 min | Draw yourself riding a dinosaur.",
        "3 min | Draw yourself turning into an animal.",
        "3 min | Draw yourself eating something sour.",
        "3 min | Draw yourself fighting a dragon.",
        "3 min | Draw yourself as a knight.",
        "3 min | Draw yourself with a new haircut.",
        "3 min | Draw yourself driving a tiny car.",
        "3 min | Draw yourself as a statue in a museum.",
        "3 min | Draw yourself fighting an alien.",
        "3 min | Draw yourself balancing on a tightrope.",
        "3 min | Draw yourself fighting your twin.",
        "3 min | Draw yourself trying to open a locked door.",
        "3 min | Draw yourself inside a video game.",
        "3 min | Draw yourself winning an Olympic medal.",
        "3 min | Draw yourself as a plant.",
        "3 min | Draw yourself painting a famous person's portrait.",
        "3 min | Draw yourself as a secret agent.",
        "3 min | Draw yourself as a newborn.",
        "3 min | Draw yourself as a kid doing something.",
        "3 min | Draw yourself in outer space.",
        "3 min | Draw yourself riding a snail.",
        "3 min | Draw yourself being struck by lightning.",
        "3 min | Draw yourself as a sleepy pirate.",
        "3 min | Draw yourself as a sea captain.",
        "3 min | Draw yourself balancing something on your head.",
        "3 min | Draw yourself feeding birds.",
        "3 min | Draw yourself crying at a movie.",
        "3 min | Draw yourself as a dinosaur.",
        "3 min | Draw yourself as a lighthouse keeper.",
        "3 min | Draw yourself dancing badly.",
        "3 min | Draw yourself at the North Pole.",
        "3 min | Draw yourself hiding really well.",
        "3 min | Draw yourself as a dog.",
        "3 min | Draw yourself taking a picture.",
        "3 min | Draw yourself riding a broom.",
        "3 min | Draw yourself as a fortune teller.",
        "3 min | Draw yourself as a birdwatcher.",
        "3 min | Draw yourself having tea with Bigfoot.",
        "3 min | Draw yourself lost at sea.",
        "3 min | Draw yourself deep in thought.",
        "3 min | Draw yourself riding a bird.",
        "3 min | Draw yourself trying not to laugh.",
        "3 min | Draw yourself stepping on Lego.",
        "3 min | Draw yourself as Spider-Man.",
        "3 min | Draw yourself as a tiny fairy.",
        "3 min | Draw yourself setting a toaster on fire.",
        "3 min | Draw yourself sitting on a cloud.",
        "3 min | Draw yourself as a tourist.",
        "3 min | Draw yourself getting stuck in mud.",
        "3 min | Draw yourself running from a swarm of bees.",
        "3 min | Draw yourself shivering.",
        "3 min | Draw yourself jumping into a puddle.",
        "3 min | Draw yourself asking: where are my keys?",
        "3 min | Draw yourself riding a turtle.",
        "3 min | Draw yourself waiting.",
        "3 min | Draw yourself sitting on a throne.",
        "3 min | Draw yourself jumping rope.",
        "3 min | Draw yourself eating spaghetti.",
        "3 min | Draw yourself playing hide-and-seek.",
        "3 min | Draw yourself driving a hotdog-shaped car.",
        "3 min | Draw yourself singing opera.",
        "3 min | Draw yourself as a cool cat burglar.",
        "3 min | Draw yourself hiding from a giant.",
        "3 min | Draw yourself being stung by bees.",
        "3 min | Draw yourself bursting into flames.",
        "3 min | Draw yourself flying.",
        "3 min | Draw yourself as a royal frog.",
        "3 min | Draw yourself as a baker.",
        "3 min | Draw yourself turning into a fruit or vegetable.",
        "3 min | Draw yourself as a butterfly collector.",
        "3 min | Draw yourself riding a skateboard.",
        "3 min | Draw yourself wearing a disguise.",
        "3 min | Draw yourself trapped in a bottle.",
        "3 min | Draw yourself falling asleep.",
        "3 min | Draw yourself drawing yourself.",
        "3 min | Draw yourself as a mediocre superhero.",
        "3 min | Draw yourself carrying a huge birthday cake.",
        "3 min | Draw yourself as a cowboy.",
        "3 min | Draw yourself playing chess.",
        "3 min | Draw yourself as an alien.",
        "3 min | Draw yourself turning invisible.",
        "3 min | Draw yourself sweating nervously.",
        "3 min | Draw yourself slipping on a banana peel.",
        "3 min | Draw yourself climbing a skyscraper like King Kong.",
        "3 min | Draw yourself camping.",
        "3 min | Draw yourself as a dentist for a shark.",
        "3 min | Draw yourself wearing a fancy hat.",
        "3 min | Draw yourself lost.",
        "3 min | Draw yourself as a ghost.",
        "3 min | Draw yourself breaking something.",
        "3 min | Draw yourself conducting an orchestra.",
        "3 min | Draw yourself in a hot air balloon.",
        "3 min | Draw yourself digging for treasure.",
        "3 min | Draw yourself jumping out of a cake.",
        "3 min | Draw yourself riding an insect.",
        "3 min | Draw yourself at a picnic.",
        "3 min | Draw yourself as a scientist.",
        "3 min | Draw yourself flying a kite.",
        "3 min | Draw yourself surfing a rainbow.",
        "3 min | Draw yourself as a caveperson.",
        "3 min | Draw yourself laughing.",
        "3 min | Draw yourself as a chef.",
        "3 min | Draw yourself as a superhero.",
        "3 min | Draw yourself running late.",
        "3 min | Draw yourself trying to remember something.",
        "3 min | Draw yourself as a disgruntled teen.",
        "3 min | Draw yourself riding a bicycle.",
        "3 min | Draw yourself turning into a monster.",
        "3 min | Draw yourself fainting.",
        "3 min | Draw yourself spilling coffee.",
        "3 min | Draw yourself getting arrested.",
        "3 min | Draw yourself delivering pizza.",
        "3 min | Draw yourself struggling to open a jar.",
        "3 min | Draw yourself painting.",
        "3 min | Draw yourself as a not-so-great magician.",
        "3 min | Draw yourself as a weather reporter.",
        "3 min | Draw yourself climbing a mountain.",
        "3 min | Draw yourself being too full.",
        "3 min | Draw yourself as a time traveler.",
        "3 min | Draw yourself digging a hole.",
        "3 min | Draw yourself playing an instrument.",
        "3 min | Draw yourself in a spider's web.",
        "3 min | Draw yourself very tiny.",
        "3 min | Draw yourself as a clown.",
        "3 min | Draw yourself on a deserted island.",
        "3 min | Draw yourself dancing with your shadow.",
        "3 min | Draw yourself riding an ostrich.",
        "3 min | Draw yourself as a viking.",
        "3 min | Draw yourself chasing a butterfly.",
        "3 min | Draw yourself running from zombies.",
        "3 min | Draw yourself as a beekeeper.",
        "3 min | Draw yourself as a spy.",
        "3 min | Draw yourself as a pirate.",
        "3 min | Draw yourself holding a knife.",
        "3 min | Draw yourself in a tiny submarine.",
        "3 min | Draw yourself as a skeleton.",
        "");

    private static final Pattern TIMED = Pattern.compile("(\\d{1,4})\\s*(s|sec|secs|seconds?|m|min|mins|minutes?)", Pattern.CASE_INSENSITIVE);

    /** The cards in {@code text}: a card a line, notes and blank lines passed over, a line that doesn't read skipped. */
    static List<Card> parse(String text) {
        List<Card> cards = new ArrayList<>();
        for (String line : text.split("\r?\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#") || times(line) != null) continue;
            String[] fields = line.split("\\|", -1);
            String words = fields[fields.length - 1].trim();
            int seconds = 0;
            boolean diary = false, ok = !words.isEmpty() && fields.length <= 3;
            for (int i = 0; i < fields.length - 1 && ok; i++) {
                String f = fields[i].trim();
                Matcher m = TIMED.matcher(f);
                if (f.equalsIgnoreCase(DIARY) && !diary) diary = true;
                else if (m.matches() && seconds == 0) {
                    seconds = Integer.parseInt(m.group(1)) * (m.group(2).toLowerCase(Locale.ROOT).startsWith("s") ? 1 : 60);
                    ok = seconds > 0 && seconds <= MOST_SECONDS;
                } else ok = false;
            }
            if (ok) cards.add(new Card(seconds, diary, words));
        }
        return cards;
    }
    /**
     * The cards in {@code folder}'s {@link #FILE} (null: the defaults alone); the file written from {@link #DEFAULTS} first
     * when it is missing or holds no card. The defaults if it can be neither read nor written.
     */
    static List<Card> load(File folder) {
        if (folder == null) return parse(DEFAULTS);
        File file = new File(folder, FILE);
        try {
            List<Card> cards = file.isFile() ? parse(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)) : new ArrayList<>();
            if (!cards.isEmpty()) return cards;
            if (!folder.isDirectory() && !folder.mkdirs()) return parse(DEFAULTS);
            Files.write(file.toPath(), DEFAULTS.getBytes(StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException unreadable) { /* the defaults below */ }
        return parse(DEFAULTS);
    }

    /** What follows a template's name on its line of section times ("Diary Page times | Did 2:30 | ..."). */
    static final String TIMES = " times";
    private static final Pattern CLOCK = Pattern.compile("(\\d{1,2}):(\\d{2})");
    /**
     * The template a line of section times names, by its label ({@code NotebookTemplate#label}); null for any other line.
     * Its times follow a bar: an untimed card whose words end in "times" ("A cat drawn three times") is a card.
     */
    private static String times(String line) {
        int bar = line.indexOf('|');
        if (bar < 0) return null;
        String head = line.substring(0, bar).trim();
        return head.toLowerCase(Locale.ROOT).endsWith(TIMES) ? head.substring(0, head.length() - TIMES.length()).trim() : null;
    }
    /**
     * {@code template}'s section times ({@link NotebookTemplate#sectionSeconds}) as {@code text} (the prompts file) sets them:
     * its line "Diary Page times | Did 2:30 | Saw 2:30 | Heard 0:30 | Picture 0:30", each box by its name, its time m:ss or
     * as a card's ("90 s", "3 min"). A box the line leaves out, or whose time doesn't read, keeps the template's. Null for a
     * template without sections.
     */
    static int[] sectionTimes(String text, NotebookTemplate template) {
        if (template == null || !template.timed()) return null;
        int[] seconds = template.sectionSeconds.clone();
        for (String line : (text == null ? "" : text).split("\r?\n")) {
            line = line.trim();
            if (line.startsWith("#") || !template.label.equalsIgnoreCase(times(line))) continue;
            String[] fields = line.split("\\|");
            for (int f = 1; f < fields.length; f++) {
                String field = fields[f].trim();
                for (int i = 0; i < template.sections.length; i++) {
                    String name = template.sections[i];
                    if (!field.regionMatches(true, 0, name, 0, name.length()) || field.length() <= name.length() || field.charAt(name.length()) != ' ') continue;
                    String time = field.substring(name.length()).trim();
                    Matcher c = CLOCK.matcher(time), m = TIMED.matcher(time);
                    int s = c.matches() && Integer.parseInt(c.group(2)) < 60 ? Integer.parseInt(c.group(1)) * 60 + Integer.parseInt(c.group(2))
                            : m.matches() ? Integer.parseInt(m.group(1)) * (m.group(2).toLowerCase(Locale.ROOT).startsWith("s") ? 1 : 60) : 0;
                    if (s > 0 && s <= MOST_SECONDS) seconds[i] = s;
                }
            }
        }
        return seconds;
    }
    /** {@link #sectionTimes(String, NotebookTemplate)} as {@code folder}'s {@link #FILE} sets them (null, or a file missing or unread: the template's). */
    static int[] sectionTimes(File folder, NotebookTemplate template) {
        String text = null;
        try {
            File file = folder == null ? null : new File(folder, FILE);
            if (file != null && file.isFile()) text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException unreadable) { /* the template's */ }
        return sectionTimes(text, template);
    }

    /** {@code card} as a line of the file, as a page keeps it (its prompt, {@link ToneDocument#prompt}). */
    static String line(Card card) { return (card.diary ? DIARY + " | " : "") + (card.seconds > 0 ? card.seconds + " s | " : "") + card.text; }
    /** The card a page's {@code line} holds; null for none, or a line that doesn't read. */
    static Card of(String line) {
        List<Card> cards = line == null ? null : parse(line);
        return cards == null || cards.size() != 1 ? null : cards.get(0);
    }

    /** A card of {@code count} other than {@code current} (itself when it is the only one). */
    static int another(int current, int count, Random random) { return count < 2 ? current : (current + 1 + random.nextInt(count - 1)) % count; }

    /** The clock on a running card: m:ss. */
    static String clock(int seconds) { return seconds / 60 + ":" + (seconds % 60 < 10 ? "0" : "") + seconds % 60; }

    /** The card's heading: "Self-Portrait Prompt, 3 Min", "Self-Portrait Prompt, Diary Page, 3 Min", "Self-Portrait Prompt, Untimed" (docs/TEXT-STYLE.md). */
    static String heading(Card card) {
        int s = card.seconds;
        return TITLE + ", " + (card.diary ? DIARY + ", " : "") + (s == 0 ? "Untimed" : s < 60 ? s + " s" : s % 60 == 0 ? s / 60 + " Min" : clock(s));
    }
}
