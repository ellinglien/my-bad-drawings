package io.github.mpdairy.monopaint;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Share codes, also how brushes are stored, as the Brush Lab mockup's encode and decode: {@code name.K S DIALS.SEED}:
 * the name as a lowercase slug, I (pen) or P (paint), the style's index, each setting in {@link BrushSetting}'s order
 * (a choice as one digit, a dial as two base-36 digits over its range), then the seed in base 36. A dial is clamped
 * into its range before it is written (the mockup wrote a round nib's width past it, so its round-nib codes didn't
 * read back), and Speed at 0 is written just either side of the middle to keep which way it works.
 * Pure, checked on the desktop.
 */
final class BrushCode {
    /** Steps across a dial's range: the most two base-36 digits hold. */
    static final int STEPS = 1295;
    /** Speed's two steps either side of 0: at amount 0 they keep which way speed works. */
    static final int THINNER_AT_ZERO = 647, THICKER_AT_ZERO = 648;
    private static final Pattern CODE = Pattern.compile("([a-z]+(?:-[a-z]+)*)\\.([IP])(\\d)([0-9a-z]+)\\.([0-9a-z]{1,7})");

    private BrushCode() { }

    /** A name as a code carries it: lowercase letters in hyphenated words, "brush" if nothing is left. */
    static String slug(String name) {
        String slug = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z]+", "-").replaceAll("^-|-$", "");
        return slug.isEmpty() ? "brush" : slug;
    }
    static String encode(WildBrush b) { return slug(b.name) + "." + letter(b.kind) + b.style.index + dials(b) + "." + seed(b); }
    /** What makes two brushes draw alike: the code without name or style. See {@link WildBrush#key}. */
    static String settings(WildBrush b) { return letter(b.kind) + dials(b) + "." + seed(b); }

    /** The brush a code describes, its name the code's slug; null if the code doesn't read. Case doesn't matter: a code pasted in capitals reads. */
    static WildBrush decode(String code) {
        if (code == null) return null;
        Matcher m = CODE.matcher(written(code.trim()));
        if (!m.matches()) return null;
        WildBrush.Kind kind = m.group(2).equals("I") ? WildBrush.Kind.INK : WildBrush.Kind.PAINT;
        List<BrushStyle> styles = BrushStyle.of(kind);
        int style = m.group(3).charAt(0) - '0';
        long seed = Long.parseLong(m.group(5), 36);
        if (style >= styles.size() || seed > 0xffffffffL) return null;
        WildBrush b = new WildBrush(kind, seed, m.group(1));
        b.style = styles.get(style);
        String digits = m.group(4);
        int at = 0;
        for (BrushSetting setting : BrushSetting.of(kind)) {
            if (setting.choice()) {
                if (at >= digits.length()) return null;
                int option = digits.charAt(at++) - '0';
                if (option < 0 || option >= setting.choices.length) return null;
                setting.set(b, option);
                continue;
            }
            if (at + 2 > digits.length()) return null;
            int step = Integer.parseInt(digits.substring(at, at + 2), 36);
            at += 2;
            if (setting == BrushSetting.SPEED && (step == THINNER_AT_ZERO || step == THICKER_AT_ZERO)) {
                b.speed = 0; b.thickerWhenFast = step == THICKER_AT_ZERO;
                continue;
            }
            setting.set(b, setting.min + step / (double)STEPS * (setting.max - setting.min));
        }
        return at == digits.length() ? b.normalize() : null;
    }

    /** {@code code} in the case codes are written in: all lowercase but the kind's letter, which follows the slug's dot. */
    static String written(String code) {
        String lower = code.toLowerCase(Locale.ROOT);
        int kind = lower.indexOf('.') + 1;
        return kind <= 0 || kind >= lower.length() ? lower : lower.substring(0, kind) + Character.toUpperCase(lower.charAt(kind)) + lower.substring(kind + 1);
    }
    private static char letter(WildBrush.Kind kind) { return kind == WildBrush.Kind.INK ? 'I' : 'P'; }
    private static String seed(WildBrush b) { return Long.toString(b.seed & 0xffffffffL, 36); }
    private static String dials(WildBrush b) {
        StringBuilder out = new StringBuilder();
        for (BrushSetting setting : BrushSetting.of(b.kind)) {
            if (setting.choice()) { out.append((int)setting.get(b)); continue; }
            int step;
            if (setting == BrushSetting.SPEED && b.speed == 0) step = b.thickerWhenFast ? THICKER_AT_ZERO : THINNER_AT_ZERO;
            else {
                double v = Math.max(setting.min, Math.min(setting.max, setting.get(b)));
                step = (int)Math.round((v - setting.min) / (setting.max - setting.min) * STEPS);
            }
            String digits = Integer.toString(step, 36);
            if (digits.length() < 2) out.append('0');
            out.append(digits);
        }
        return out.toString();
    }
}
