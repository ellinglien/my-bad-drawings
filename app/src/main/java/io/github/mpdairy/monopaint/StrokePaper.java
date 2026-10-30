package io.github.mpdairy.monopaint;

/**
 * The paper noise a six-part brush's stroke reads, 0 to 1 (spec "Stampers and buffers"): the prototype's
 * {@code surf.fine} and {@code surf.mid} are the brush texture's grain widened ({@link PaperGrain#widened}), its
 * {@code surf.low} and {@code surf.blob} the app's paper blooms and blobs ({@link PaperNoise}). All fixed to the page. Pure.
 */
final class StrokePaper {
    private final PaperGrain grain;

    StrokePaper(BrushTexture texture) { this(texture.grain()); }
    /** On {@code grain} (the checks give a paper with no texture). */
    StrokePaper(PaperGrain grain) { this.grain = grain; }

    float fine(int x, int y) { return grain.widened(grain.fine(x, y) / 255f); }
    float mid(int x, int y) { return grain.widened(grain.mid(x, y) / 255f); }
    float low(int x, int y) { return PaperNoise.low(x, y) / 255f; }
    float blob(int x, int y) { return PaperNoise.blob(x, y) / 255f; }
}
