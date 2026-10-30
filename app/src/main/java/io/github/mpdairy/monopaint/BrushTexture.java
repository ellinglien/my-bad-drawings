package io.github.mpdairy.monopaint;

import java.io.IOException;

/**
 * The paper grain a brush's Paper part shows (see {@link PaperGrain}; the papers are these, in order): plain, the
 * app's paper, or a CC0 height map from ambientCG (Paper001 cold press, Paper004 laid, Paper002 felt, Paper005
 * crumpled, Fabric036 canvas); then eight Unsplash photos the owner picked (2026-10-22: watercolour, rough
 * watercolour, unryu, old library paper, kraft, old buff, blue fibre, fine speck; credits in THIRD_PARTY_NOTICES.md),
 * stored in assets/textures as 256 × 256 raw 8-bit, tileable, normalised to mean 128, spread 52. Tooth says how
 * strongly it shows. Never reorder: share codes carry the index. Append only.
 */
enum BrushTexture {
    PLAIN("plain", null), COLD_PRESS("cold press", "cold-press"), LAID("laid", "laid"), FELT("felt", "felt"),
    CRUMPLED("crumpled", "crumpled"), CANVAS("canvas", "canvas"),
    WATERCOLOUR("watercolour", "watercolour"), ROUGH("rough", "rough"), UNRYU("unryu", "unryu"), LIBRARY("library", "nypl-old"),
    KRAFT("kraft", "kraft"), OLD_BUFF("old buff", "old-buff"), BLUE_FIBRE("blue fibre", "blue-fibre"), FINE_SPECK("fine speck", "fine-speck");

    /** How many an old Wild brush's I/P code may name (plain to canvas): the eight after came with six-part brushes only. */
    static final int LEGACY = 6;

    final String label;
    private final String file;
    private PaperGrain grain;

    BrushTexture(String label, String file) { this.label = label; this.file = file; }

    /**
     * The grain: the paper's for plain, else the scan's, read the first time. A scan that can't be read fails loudly
     * rather than drawing plain: looks and samples are kept by the brush's settings, texture included, so a plain
     * stand-in would be kept as this texture's look. The scans ship inside the app, so it means a broken build.
     */
    synchronized PaperGrain grain() {
        if (file == null) return PaperGrain.PLAIN;
        if (grain == null) {
            try { grain = PaperGrain.scan(Assets.bytes("textures/" + file + ".bin")); }
            catch (IOException | IllegalArgumentException e) { throw new IllegalStateException("Can't read the " + label + " texture", e); }
        }
        return grain;
    }
}
