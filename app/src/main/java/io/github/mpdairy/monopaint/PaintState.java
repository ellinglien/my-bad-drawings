package io.github.mpdairy.monopaint;

/** The current color and canvas modes, which apply to whichever tool is selected. Saved with the tools. */
final class PaintState {
    /** Selected logical tone, 0 (black) to 255 (white). */
    int gray;
    /** Tools that support it erase with their own footprint instead of painting {@link #gray}. */
    boolean eraseMode;
    /** Brushes blend into still-wet paint. */
    boolean wetCanvas;
    /** How long paint stays wet, 0–100. */
    int wetness = 65;
    /** Brushes leave existing marks visible beneath the new paint. */
    boolean transparentPaint;
    /** Transparent paint from brushes, shapes and flat fills is watercolor: it lifts, blends and runs. */
    boolean watercolor;
    /** Tools lay masking fluid, or with {@link #eraseMode} rub it off, instead of painting. */
    boolean maskingFluid;
    /** Ink the wet brush pen has soaked up, kept between strokes until it is cleaned. */
    final BrushInk brushInk = new BrushInk();
    /** Ink in brushes in {@link #watercolor} mode, tinted with the last shade tapped and kept between strokes. */
    final BrushInk brushLoad = new BrushInk(true);
}
