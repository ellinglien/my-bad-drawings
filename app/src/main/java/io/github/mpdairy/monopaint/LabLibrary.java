package io.github.mpdairy.monopaint;

import java.util.function.Supplier;

/**
 * The samplers that draw a brush's pictures in the background: its loop ({@link #sampler}) and its tray stroke ({@link
 * #traySampler}), for the Tools page, the side column, Brush Lab's Open sheet. (Brush Lab's slots
 * and variations lived here until 2026-10-29: the lab shows only the slot it opened on, and Tweak It went.)
 */
final class LabLibrary {
    private LabLibrary() { }

    /**
     * {@code brush}'s loop, drawn in the background from a copy of its own (the lab may change its brush meanwhile),
     * made only when a card's sample changes; at black, a wash at the old black ({@link ModularSizes#sampleDarkness}).
     */
    static Supplier<LoopCard.Sample> sampler(Brush brush) {
        return () -> {
            Brush copy = BrushLab.copyOf(brush);
            return (w, h) -> WildSample.modular(copy.parts, ModularSizes.sampleDarkness(copy.parts), WildSample.Shape.LOOP, w, h, true);
        };
    }
    /**
     * {@code brush}'s tray stroke, the drawing screen's bar's look (the owner, 2026-10-19: "lets use the toolbar stroke
     * look for all of the previews"), for the Tools page: at {@code look}'s size as the bar's row draws it ({@link
     * WildSample#slotTray}: a slot, This Brush), or, {@code look} null, at the size that fits the
     * card ({@link ModularSizes#sampleSize} of its height, a library card's); both at full darkness ({@link
     * WildSample#PREVIEW}), whatever the current grey; a faint one darker so it reads ({@link
     * WildSample#legibleTray}). Drawn in the background from a copy, kept as samples are.
     */
    static Supplier<LoopCard.Sample> traySampler(Brush brush, ToolSwatches.Look look) {
        return () -> {
            Brush copy = BrushLab.copyOf(brush);
            if (look != null) return (w, h) -> WildSample.slotTray(copy.parts, look, w, h);
            return (w, h) -> WildSample.legibleTray(copy.parts, WildSample.PREVIEW, ModularSizes.sampleSize(copy.parts, h), w, h, true);
        };
    }
}
