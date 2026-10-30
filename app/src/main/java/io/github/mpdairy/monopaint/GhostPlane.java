package io.github.mpdairy.monopaint;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Tracing paper's ghosts as one plane (docs/archive/superpowers/specs/2026-10-20-tracing-paper-design.md, "Rendering ghosts"):
 * the ghost pages' marks flattened at their strengths, a byte a page pixel. It is how much a black layer laid right over
 * the drawing page's paper covers there ({@link ToneDocument#visibleLayers}), so a grey mark g at strength s shows as
 * 255 − (255 − g)·s on white paper and darkens shaded paper and guides the same way; the page's layers go over it, all
 * through the page's own dots. The screen alone reads it: the page, its file, copies, picture, Fill, the eyedropper and
 * Replay never do. A ghost page's guides and paper shade are not ghosted. Stage 3c: a neighbouring stack is flattened
 * first, its layers at their own steps, then laid once at Page Transparency's strength; the layers above the page drawn
 * on in its stack are a second plane, over the page's own layers ({@link Planes}), so they cover it as layers do. Pure,
 * checked on the desktop ({@code TracingChecks}, {@code PageStacksChecks}).
 */
final class GhostPlane {
    /**
     * One ghost: a decoded page's layers (released once read), or its packed bytes, its strength, %, and its layers'
     * steps ({@link TracingPaper#layerBits}); the stack it is a layer of when it shows with that stack flattened ({@code
     * group} ≥ 0, at {@code groupStrength} %: a neighbouring stack under Page Transparency), and whether it lies over the
     * page drawn on ({@code above}: a layer above it in its stack).
     */
    static final class Source {
        final ToneDocument.Snapshot page; final PackedPage packed; final int strength, layers, group, groupStrength; final boolean above;
        Source(ToneDocument.Snapshot page, PackedPage packed, int strength) { this(page, packed, strength, 0); }
        Source(ToneDocument.Snapshot page, PackedPage packed, int strength, int layers) { this(page, packed, strength, layers, -1, 100, false); }
        Source(ToneDocument.Snapshot page, PackedPage packed, int strength, int layers, int group, int groupStrength, boolean above) {
            this.page = page; this.packed = packed; this.strength = strength; this.layers = layers; this.group = group; this.groupStrength = groupStrength; this.above = above;
        }
    }

    /**
     * What shows through the page drawn on: {@link #under}, how much a black layer right over its paper covers (a byte a
     * pixel; null: nothing), and the layers above it in its stack flattened at their steps, {@link #overTones} and
     * {@link #overAlpha} (null: none), a layer over the page's own (Stage 3c: layers above cover the one drawn on).
     */
    static final class Planes {
        final byte[] under, overTones, overAlpha;
        Planes(byte[] under, byte[] overTones, byte[] overAlpha) { this.under = under; this.overTones = overTones; this.overAlpha = overAlpha; }
    }

    /**
     * Replay's veil ({@link PageReplay}): an over plane of the paper wherever {@code page} shows no mark (no visible layer
     * covers the pixel), so its replay shows only what the page holds. The log keeps strokes the eraser or the Lasso (not
     * logged) has since taken away or moved; without it they were laid again as blobs (the owner, 2026-10-26).
     */
    static Planes offMarks(ToneDocument.Snapshot page) {
        int n = page.width * page.height;
        byte[] veil = new byte[n];
        Arrays.fill(veil, (byte) 255);
        for (ToneDocument.Layer layer : page.layers) if (layer.visible) for (int i = 0; i < n; i++) if (layer.marks(i)) veil[i] = 0;
        byte[] paper = page.shownPaper();
        if (paper == null) { paper = new byte[n]; Arrays.fill(paper, (byte) page.paper); }
        return new Planes(null, paper, veil);
    }

    /** The planes for {@code sources} on a {@code width × height} page, bottom first; null when they leave nothing to show. Off the drawing thread. */
    static Planes build(int width, int height, List<Source> sources) throws IOException {
        int n = width * height;
        int[] light = new int[n];
        Arrays.fill(light, 255);
        int bands = Math.min(height, 4 * Cores.WORKERS);
        boolean[] any = new boolean[bands], anyOver = new boolean[bands];
        // A neighbouring stack's layers, flattened (their light multiplied) before its strength is laid once.
        byte[] group = null;
        int groupOf = -1, groupStrength = 100;
        byte[] overInk = null, overCover = null;
        for (int k = 0; k <= sources.size(); k++) {
            Source source = k < sources.size() ? sources.get(k) : null;
            if (group != null && (source == null || source.group != groupOf)) { lay(group, light, groupStrength, width, height, bands, any); group = null; groupOf = -1; }
            if (source == null) break;
            ToneDocument.Snapshot page = source.page;
            if (page == null) page = DocumentCodec.read(new ByteArrayInputStream(source.packed.bytes())).layerSnapshot();
            try {
                if (page.width != width || page.height != height) continue;
                PagePicture.Composite marks = new PagePicture.Composite(width, stepped(page.layers, source.layers), 255);
                int s = source.strength;
                if (source.above) {
                    if (overInk == null) { overInk = new byte[n]; overCover = new byte[n]; }
                    byte[] ink = overInk, cover = overCover;
                    Cores.each(bands, (b, worker) -> {
                        int[] inked = new int[width], covered = new int[width];
                        for (int y = height * b / bands; y < height * (b + 1) / bands; y++) {
                            marks.marks(y, inked, covered);
                            for (int x = 0, at = y * width; x < width; x++, at++) {
                                int c = (covered[x] * s + 50) / 100;
                                if (c == 0) continue;
                                int p = (inked[x] * s + 50) / 100, was = cover[at] & 255;
                                // Over what lies under it, premultiplied: its tone times its cover, then the cover.
                                ink[at] = (byte) (p + ((ink[at] & 255) * (255 - c) + 127) / 255);
                                cover[at] = (byte) (c + (was * (255 - c) + 127) / 255);
                                anyOver[b] = true;
                            }
                        }
                    });
                    continue;
                }
                if (source.group >= 0) {
                    if (group == null) { group = new byte[n]; Arrays.fill(group, (byte) 255); groupOf = source.group; groupStrength = source.groupStrength; }
                    lit(marks, group, s, width, height, bands);
                    continue;
                }
                byte[] alone = new byte[n];
                Arrays.fill(alone, (byte) 255);
                lit(marks, alone, s, width, height, bands);
                lay(alone, light, 100, width, height, bands, any);
            } finally { if (source.page != null) source.page.release(); }
        }
        boolean shows = false, over = false;
        for (boolean a : any) shows |= a;
        for (boolean a : anyOver) over |= a;
        byte[] plane = null;
        if (shows) { plane = new byte[n]; for (int i = 0; i < n; i++) plane[i] = (byte) (255 - light[i]); }
        byte[] tones = null;
        if (over) {
            tones = new byte[n];
            for (int i = 0; i < n; i++) { int c = overCover[i] & 255; tones[i] = (byte) (c == 0 ? 255 : Math.min(255, ((overInk[i] & 255) * 255 + c / 2) / c)); }
        }
        return shows || over ? new Planes(plane, tones, over ? overCover : null) : null;
    }
    /** {@code marks}' light (255 less its darkness at {@code s} %) multiplied into {@code light}, a byte a pixel. */
    private static void lit(PagePicture.Composite marks, byte[] light, int s, int width, int height, int bands) {
        Cores.each(bands, (b, worker) -> {
            int[] row = new int[width];
            for (int y = height * b / bands; y < height * (b + 1) / bands; y++) {
                marks.row(y, row);
                for (int x = 0, at = y * width; x < width; x++, at++) {
                    int dark = 255 - row[x];
                    if (dark != 0) light[at] = (byte) ((light[at] & 255) * (255 - (dark * s + 50) / 100) / 255);
                }
            }
        });
    }
    /** {@code shade}'s darkness (a flattened stack's, or one page's) laid into {@code light} at {@code s} %. */
    private static void lay(byte[] shade, int[] light, int s, int width, int height, int bands, boolean[] any) {
        Cores.each(bands, (b, worker) -> {
            for (int at = height * b / bands * width, end = height * (b + 1) / bands * width; at < end; at++) {
                int dark = 255 - (shade[at] & 255);
                if (dark == 0) continue;
                light[at] = light[at] * (255 - (dark * s + 50) / 100) / 255;
                any[b] = true;
            }
        });
    }

    /** {@code layers} with each pair's opacity times its step's strength (page × layer): the same pixels, not copied. */
    static List<ToneDocument.Layer> stepped(List<ToneDocument.Layer> layers, int bits) {
        if (bits == 0) return layers;
        List<ToneDocument.Layer> out = new java.util.ArrayList<>();
        for (int i = 0; i < layers.size(); i++) {
            ToneDocument.Layer layer = layers.get(i), view = new ToneDocument.Layer(layer.name, layer.visible, layer.tones, layer.alpha);
            view.opacity = layer.opacity * TracingPaper.layerStrength(bits, InkWashLayers.pair(i)) / 100;
            view.planes = layer.planes;
            out.add(view);
        }
        return out;
    }

    /** {@code tone} with the planes at its pixel: the plane under it, then the layers over it (an eraser's preview, Real Mirror's reflection). */
    static int over(int tone, Planes planes, int i) {
        if (planes == null) return tone;
        tone = over(tone, planes.under, i);
        if (planes.overAlpha == null || i >= planes.overAlpha.length) return tone;
        int a = planes.overAlpha[i] & 255;
        return a == 0 ? tone : ((planes.overTones[i] & 255) * a + tone * (255 - a) + 127) / 255;
    }
    /** {@code tone} with the plane's {@code cover} at its pixel over it. */
    static int over(int tone, byte[] plane, int i) { return plane == null || i >= plane.length ? tone : (tone * (255 - (plane[i] & 255)) + 127) / 255; }

    /** All 0 (black), {@code n} long: the ghost's tone everywhere ({@link ToneDocument#visibleLayers}); one kept, never changed. */
    private static byte[] ink;
    static synchronized byte[] ink(int n) {
        if (ink == null || ink.length != n) ink = new byte[n];
        return ink;
    }

    private GhostPlane() {}
}
