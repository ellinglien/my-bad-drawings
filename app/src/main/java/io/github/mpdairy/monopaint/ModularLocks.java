package io.github.mpdairy.monopaint;

import java.util.EnumSet;

/** What Randomize, dice and variations leave alone: whole parts, and dials (kept while their part's choice stays). Pure. */
final class ModularLocks {
    final EnumSet<BrushPart> parts = EnumSet.noneOf(BrushPart.class);
    final EnumSet<PartDial> dials = EnumSet.noneOf(PartDial.class);
}
