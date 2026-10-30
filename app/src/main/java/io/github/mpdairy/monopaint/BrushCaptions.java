package io.github.mpdairy.monopaint;

import java.util.ArrayList;
import java.util.List;

/** Card captions, as the Brush Lab mockup's twoLines and describeTool. Pure, checked on the desktop. */
final class BrushCaptions {
    /** A caption line's most characters. */
    static final int LINE = 17;

    private BrushCaptions() { }

    /** {@code items} on at most two lines of {@link #LINE} characters, comma-joined while they fit; never cut off, so what doesn't fit is left out. */
    static List<String> twoLines(List<String> items) {
        List<String> lines = new ArrayList<>();
        for (String item : items) {
            if (item.length() > LINE) continue;
            int last = lines.size() - 1;
            if (last >= 0 && (lines.get(last) + ", " + item).length() <= LINE) lines.set(last, lines.get(last) + ", " + item);
            else if (lines.size() < 2) lines.add(item);
        }
        return lines;
    }
    /**
     * {@code items} comma-joined on one line at most {@code room} wide as {@code measure} measures it, while they fit;
     * the first always (a Tools page card's description: {@link LabText}'s face, as the card draws it).
     */
    static String oneLine(List<String> items, LabText.Measure measure, float room) {
        StringBuilder line = new StringBuilder();
        for (String item : items) {
            if (line.length() == 0) line.append(item);
            else if (measure.width(line + ", " + item) <= room) line.append(", ").append(item);
        }
        return line.toString();
    }
    /** A brush's description, in its own words on two lines ({@link ModularWords#caption}). */
    static List<String> describe(Brush brush) { return twoLines(ModularWords.caption(brush.parts)); }
}
