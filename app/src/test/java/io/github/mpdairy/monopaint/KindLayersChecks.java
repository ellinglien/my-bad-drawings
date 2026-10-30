package io.github.mpdairy.monopaint;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * Kind layers (docs/superpowers/specs/2026-10-27-kind-layers-design.md): each kind of mark on a plane of its own,
 * made when it first draws; the page stacks them dry media, washes, pen, opaque paint whatever order they were drawn
 * in; the eraser takes a chosen kind exactly, pen crossing pencil too; Undo, a file round trip (an old one unchanged,
 * byte for byte), the Lasso and a kind's opacity keep to the planes. Prints a plane's memory on the Manta's page.
 */
public final class KindLayersChecks {
    static final int W = ErasableChecks.W, H = ErasableChecks.H;
    static int checks;

    public static void main(String[] args) throws IOException {
        Assets.install(Assets.folder(Assets.FOLDER));
        lazy(); order(); exact(); undo(); oldFile(); newFile(); lasso(); smudge(); shown();
        System.out.println("PASS: Kind layers (" + checks + " checks): a plane is made when its kind first draws on a layer; "
                + "opaque paint lies over pen, pen over washes, washes over dry media, whatever order drawn; Dry Media and Pen each erase "
                + "exactly their own where pen crosses pencil, the other byte for byte; Undo and Redo bring planes back; an old file "
                + "round-trips byte for byte, a new one keeps its planes; the Lasso moves every plane; a stump blends dry media and leaves pen; "
                + "a kind shows Faint or Hidden. A plane costs " + (2L * ToneDocument.MAX_PIXELS / 1000000.0) + " MB on the Manta's page");
    }
    static void check(boolean ok, String what) { checks++; if (!ok) throw new AssertionError(what); }

    /** A {@code w × h} block at x, y of grey {@code gray} at full coverage on {@code layer}, of {@code kinds}, as one gesture. */
    static void block(ToneDocument d, int layer, int kinds, int x, int y, int w, int h, int gray) {
        d.selectLayer(layer);
        d.begin(1, kinds);
        int[] grays = new int[w * h], cover = new int[w * h];
        Arrays.fill(grays, gray); Arrays.fill(cover, 255);
        d.coverTones(x, y, w, h, grays, cover);
        d.finish();
    }
    /** A transparent wash of {@code gray} over the block (darkness only, as a wash lays it). */
    static void wash(ToneDocument d, int x, int y, int w, int h, int gray) {
        d.selectLayer(InkWashLayers.WASH);
        d.begin(1, ToneDocument.KIND_PAINT);
        int[] grays = new int[w * h];
        Arrays.fill(grays, gray);
        d.setTones(x, y, w, h, grays);
        d.finish();
    }
    static ToneDocument.Layer layer(ToneDocument d, int i) { return d.layerSnapshot().layers.get(i); }
    static int covered(ToneDocument.Layer l) { int n = 0; if (l != null) for (byte a : l.alpha) if (a != 0) n++; return n; }
    static boolean samePlane(ToneDocument.Layer a, ToneDocument.Layer b) {
        if (a == null || b == null) return covered(a) == 0 && covered(b) == 0;
        return Arrays.equals(a.tones, b.tones) && Arrays.equals(a.alpha, b.alpha);
    }
    static byte[] write(ToneDocument d) throws IOException { ByteArrayOutputStream out = new ByteArrayOutputStream(); DocumentCodec.write(out, d.layerSnapshot()); return out.toByteArray(); }
    static ToneDocument read(byte[] b) throws IOException { return DocumentCodec.read(new ByteArrayInputStream(b)); }
    static int[] shown(ToneDocument d) { int[] t = new int[W * H]; for (int i = 0; i < t.length; i++) t[i] = d.compositeTone(i % W, i / W); return t; }

    /** No plane until its kind draws; each kind on its own; paint of no kind on the base. */
    static void lazy() {
        ToneDocument d = InkWashLayers.blank(W, H);
        check(layer(d, 0).planes == null && layer(d, 1).planes == null, "A blank page has no planes");
        block(d, InkWashLayers.INK, ToneDocument.KIND_PEN, 10, 10, 20, 20, 0);
        ToneDocument.Layer ink = layer(d, InkWashLayers.INK);
        check(ink.plane(ToneDocument.PLANE_PEN) != null && ink.plane(ToneDocument.PLANE_DRY) == null && !ink.covers() && layer(d, InkWashLayers.WASH).planes == null,
                "Pen makes the ink layer's pen plane, nothing else; the base stays bare");
        block(d, InkWashLayers.INK, 0, 40, 10, 20, 20, 0);
        check(layer(d, InkWashLayers.INK).covers() && covered(layer(d, InkWashLayers.INK).plane(ToneDocument.PLANE_PEN)) == 400, "Paint of no kind lays on the base");
        check(ToneDocument.planeOf(ToneDocument.KIND_PAINT | ToneDocument.KIND_OPAQUE) == ToneDocument.PLANE_OPAQUE && ToneDocument.planeOf(ToneDocument.KIND_PAINT) == ToneDocument.PLANE_WASH
                && ToneDocument.planeOf(ToneDocument.KIND_PENCIL) == ToneDocument.PLANE_DRY && ToneDocument.planeOf(ToneDocument.KIND_PEN) == ToneDocument.PLANE_PEN
                && ToneDocument.planeOf(0) < 0 && ToneDocument.planeOf(ToneDocument.KIND_PEN | ToneDocument.KIND_PENCIL) < 0, "Each kind's plane; none or two kinds, the base");
    }

    /** Dry media, washes, pen, opaque paint, bottom to top, in whatever order they were drawn. */
    static void order() {
        for (boolean reversed : new boolean[]{false, true}) {
            ToneDocument d = InkWashLayers.blank(W, H);
            Runnable dry = () -> block(d, InkWashLayers.INK, ToneDocument.KIND_PENCIL, 20, 20, 200, 100, 150);
            Runnable glaze = () -> wash(d, 60, 20, 200, 100, 128);
            Runnable pen = () -> block(d, InkWashLayers.INK, ToneDocument.KIND_PEN, 100, 20, 40, 100, 0);
            Runnable opaque = () -> block(d, InkWashLayers.WASH, ToneDocument.KIND_PAINT | ToneDocument.KIND_OPAQUE, 120, 60, 100, 20, 230);
            Runnable[] steps = reversed ? new Runnable[]{opaque, pen, glaze, dry} : new Runnable[]{dry, glaze, pen, opaque};
            for (Runnable r : steps) r.run();
            String how = reversed ? " (drawn top first)" : "";
            check(d.compositeTone(30, 30) == 150, "Dry media alone" + how + ": " + d.compositeTone(30, 30));
            int both = d.compositeTone(80, 30), expect = (150 * 128 + 127) / 255;
            check(Math.abs(both - expect) <= 1, "A wash over dry media darkens it" + how + ": " + both + " for " + expect);
            check(d.compositeTone(110, 30) == 0, "Pen over the wash and the dry media" + how);
            check(d.compositeTone(130, 70) == 230 && d.compositeTone(200, 70) == 230, "Opaque paint over pen, washes and dry media" + how + ": " + d.compositeTone(130, 70));
        }
        // The native renderer's arrays: every base and plane in the same order.
        ToneDocument d = InkWashLayers.blank(W, H);
        block(d, InkWashLayers.INK, ToneDocument.KIND_PEN, 10, 10, 10, 10, 0);
        block(d, InkWashLayers.INK, ToneDocument.KIND_PENCIL, 10, 10, 10, 10, 100);
        block(d, InkWashLayers.WASH, ToneDocument.KIND_PAINT | ToneDocument.KIND_OPAQUE, 10, 10, 10, 10, 200);
        wash(d, 10, 10, 10, 10, 128);
        byte[][] t = new byte[ToneDocument.STACK + 3][], a = new byte[t.length][]; int[] o = new int[t.length];
        int n = d.visibleLayers(t, a, o);
        ToneDocument.Layer wash = layer(d, InkWashLayers.WASH), ink = layer(d, InkWashLayers.INK);
        check(n == 6 && t[0] == wash.tones && t[1] == ink.tones && t[2] == ink.plane(ToneDocument.PLANE_DRY).tones && t[3] == wash.plane(ToneDocument.PLANE_WASH).tones
                && t[4] == ink.plane(ToneDocument.PLANE_PEN).tones && t[5] == wash.plane(ToneDocument.PLANE_OPAQUE).tones, "visibleLayers hands bases, then dry, wash, pen, opaque: " + n);
        int i = 15 * W + 15, shown = 255;
        for (int k = 0; k < n; k++) { int c = ((a[k][i] & 255) * o[k] + 50) / 100; shown = ((t[k][i] & 255) * c + shown * (255 - c) + 127) / 255; }
        check(shown == d.compositeTone(15, 15) && shown == 200, "and composite to the page's tone");
    }

    /** Where pen crosses pencil, Dry Media takes the pencil and Pen the pen, each exactly. */
    static void exact() throws IOException {
        ModularBrush pencil = ErasableChecks.preset("Sketch pencil"), ink = ErasableChecks.preset("Steady line");
        ToneDocument both = InkWashLayers.blank(W, H), pencilAlone = InkWashLayers.blank(W, H), inkAlone = InkWashLayers.blank(W, H);
        ErasableChecks.sketch(both, pencil); ErasableChecks.ink(both, ink);
        ErasableChecks.sketch(pencilAlone, pencil); ErasableChecks.ink(inkAlone, ink);
        int crossings = 0;
        ToneDocument.Layer l = layer(both, InkWashLayers.INK);
        for (int i = 0; i < W * H; i++) if (l.plane(ToneDocument.PLANE_PEN).alpha[i] != 0 && l.plane(ToneDocument.PLANE_DRY).alpha[i] != 0) crossings++;
        check(crossings > 100, "pen crosses pencil: " + crossings + " pixels");
        ToneDocument dry = read(bytesOf(both)), pen = read(bytesOf(both));
        EraseKindChecks.erase(dry, ToneDocument.KIND_PENCIL);
        EraseKindChecks.erase(pen, ToneDocument.KIND_PEN);
        check(covered(layer(dry, InkWashLayers.INK).plane(ToneDocument.PLANE_DRY)) == 0 && samePlane(layer(dry, InkWashLayers.INK).plane(ToneDocument.PLANE_PEN), layer(inkAlone, InkWashLayers.INK).plane(ToneDocument.PLANE_PEN)),
                "Dry Media takes every pencil pixel and leaves the pen as drawn alone, byte for byte");
        check(Arrays.equals(shown(dry), shown(inkAlone)), "and the page shows the ink drawn alone");
        check(covered(layer(pen, InkWashLayers.INK).plane(ToneDocument.PLANE_PEN)) == 0 && samePlane(layer(pen, InkWashLayers.INK).plane(ToneDocument.PLANE_DRY), layer(pencilAlone, InkWashLayers.INK).plane(ToneDocument.PLANE_DRY)),
                "Pen takes every pen pixel and leaves the pencil under it as drawn alone, byte for byte");
        check(Arrays.equals(shown(pen), shown(pencilAlone)), "and the page shows the pencil drawn alone");
        // Paint takes washes and opaque paint, and leaves pen.
        ToneDocument p = InkWashLayers.blank(W, H);
        block(p, InkWashLayers.INK, ToneDocument.KIND_PEN, 100, 100, 50, 50, 0);
        wash(p, 80, 80, 100, 100, 100);
        block(p, InkWashLayers.WASH, ToneDocument.KIND_PAINT | ToneDocument.KIND_OPAQUE, 120, 120, 100, 100, 220);
        ToneDocument.Layer before = layer(p, InkWashLayers.INK).plane(ToneDocument.PLANE_PEN);
        EraseKindChecks.erase(p, ToneDocument.KIND_PAINT);
        check(covered(layer(p, InkWashLayers.WASH).plane(ToneDocument.PLANE_WASH)) == 0 && covered(layer(p, InkWashLayers.WASH).plane(ToneDocument.PLANE_OPAQUE)) == 0
                && samePlane(before, layer(p, InkWashLayers.INK).plane(ToneDocument.PLANE_PEN)), "Paint takes the wash and the opaque paint, and leaves the pen byte for byte");
    }
    static byte[] bytesOf(ToneDocument d) { try { return write(d); } catch (IOException e) { throw new AssertionError(e); } }

    /** Undo and Redo take back and bring back a plane's strokes and erasing; Undo selects the layer it changed. */
    static void undo() {
        ToneDocument d = InkWashLayers.blank(W, H);
        block(d, InkWashLayers.INK, ToneDocument.KIND_PENCIL, 20, 20, 60, 60, 120);
        block(d, InkWashLayers.INK, ToneDocument.KIND_PEN, 40, 10, 10, 90, 0);
        int[] drawn = shown(d);
        ToneDocument.Layer pencilBefore = copy(layer(d, InkWashLayers.INK).plane(ToneDocument.PLANE_DRY));
        EraseKindChecks.erase(d, ToneDocument.KIND_PENCIL);
        int[] erased = shown(d);
        check(covered(layer(d, InkWashLayers.INK).plane(ToneDocument.PLANE_DRY)) == 0, "erased");
        d.selectLayer(InkWashLayers.WASH);
        check(d.undo() && Arrays.equals(shown(d), drawn) && samePlane(layer(d, InkWashLayers.INK).plane(ToneDocument.PLANE_DRY), pencilBefore), "Undo brings the pencil back, byte for byte");
        check(d.activeLayer() == InkWashLayers.INK, "and selects the layer it changed");
        check(d.redo() && Arrays.equals(shown(d), erased), "Redo erases it again");
        while (d.canUndo()) d.undo();
        check(covered(layer(d, InkWashLayers.INK).plane(ToneDocument.PLANE_PEN)) == 0 && covered(layer(d, InkWashLayers.INK).plane(ToneDocument.PLANE_DRY)) == 0, "Undone to blank paper, every plane");
        while (d.canRedo()) d.redo();
        check(Arrays.equals(shown(d), erased), "and redone to the end");
    }
    static ToneDocument.Layer copy(ToneDocument.Layer l) { return new ToneDocument.Layer(l.name, l.visible, l.tones.clone(), l.alpha.clone()); }

    /** A page from before planes (paint of no kind, a kinds bitmask from the eraser's KIND section) reads, shows and writes as it was. */
    static void oldFile() throws IOException {
        ToneDocument old = InkWashLayers.blank(W, H);
        block(old, InkWashLayers.WASH, 0, 20, 20, 120, 80, 140);
        block(old, InkWashLayers.INK, 0, 60, 40, 30, 120, 0);
        // As the 2026-10-20 eraser left an ink layer: two kinds, a bitmask, the base Pencil.
        ToneDocument.Snapshot s = old.layerSnapshot();
        ToneDocument.Layer inkLayer = s.layers.get(InkWashLayers.INK);
        inkLayer.kinds = (byte) (ToneDocument.KIND_PEN | ToneDocument.KIND_PENCIL); inkLayer.kindBase = (byte) ToneDocument.KIND_PENCIL;
        inkLayer.kind = new byte[W * H];
        for (int i = 0; i < W * H; i++) if (inkLayer.alpha[i] != 0 && i % W < 75) inkLayer.kind[i] = (byte) ToneDocument.KIND_PEN;
        ByteArrayOutputStream out = new ByteArrayOutputStream(); DocumentCodec.write(out, s);
        byte[] bytes = out.toByteArray();
        ToneDocument back = read(bytes);
        check(layer(back, 0).planes == null && layer(back, 1).planes == null, "An old page opens with no planes");
        check(Arrays.equals(write(back), bytes), "and writes the same bytes again");
        check(Arrays.equals(shown(back), shown(old)), "and shows as it did");
        check(new java.io.DataInputStream(new ByteArrayInputStream(bytes)).readInt() == 0x54534d35, "(a TSM5 page, its KIND section)");
        // The eraser's old rules on it: Dry Media takes the pencil-only pixels, Pen the pen-only ones.
        EraseKindChecks.erase(back, ToneDocument.KIND_PENCIL);
        int left = 0;
        for (int y = 40; y < 160 && y < H; y++) for (int x = 60; x < 90; x++) if (back.compositeTone(x, y) == 0) left++;
        check(left > 0 && left < 30 * Math.min(120, H - 40), "Dry Media takes an old layer's pencil pixels and leaves its pen: " + left);
        // Drawing a new kind on it makes a plane over the old paint; the old paint stays where it was.
        ToneDocument more = read(bytes);
        block(more, InkWashLayers.INK, ToneDocument.KIND_PENCIL, 0, 0, 40, 40, 90);
        check(Arrays.equals(layer(more, InkWashLayers.INK).tones, layer(back = read(bytes), InkWashLayers.INK).tones) && layer(more, InkWashLayers.INK).plane(ToneDocument.PLANE_DRY) != null, "New paint on an old page lays on a plane; the base is the old paint, untouched");
    }

    /** A page with planes keeps them through its file, byte for byte, and writes its look where an older reader reads. */
    static void newFile() throws IOException {
        ToneDocument d = InkWashLayers.blank(W, H);
        block(d, InkWashLayers.INK, 0, 0, 0, 30, 30, 60);
        block(d, InkWashLayers.INK, ToneDocument.KIND_PENCIL, 20, 20, 60, 60, 120);
        block(d, InkWashLayers.INK, ToneDocument.KIND_PEN, 40, 10, 10, 90, 0);
        wash(d, 30, 30, 100, 40, 160);
        d.selectLayer(InkWashLayers.WASH); d.begin(.3, ToneDocument.KIND_PAINT | ToneDocument.KIND_OPAQUE);
        int[] g = new int[400], c = new int[400]; Arrays.fill(g, 210); Arrays.fill(c, 255);
        d.coverTones(100, 50, 20, 20, g, c); d.finish();
        d.selectLayer(InkWashLayers.INK); d.begin(1, ToneDocument.KIND_PEN); d.printing(); d.setPrints(41, 50, 5, new int[]{40, 40, 40, 40, 40}); d.finish();
        byte[] bytes = write(d);
        ToneDocument back = read(bytes);
        for (int n = 0; n < 2; n++) for (int p = -1; p < ToneDocument.PLANES; p++) {
            ToneDocument.Layer a = p < 0 ? layer(d, n) : layer(d, n).plane(p), b = p < 0 ? layer(back, n) : layer(back, n).plane(p);
            check((a == null) == (b == null), "layer " + n + " plane " + p + " comes back");
            if (a == null) continue;
            check(Arrays.equals(a.tones, b.tones) && Arrays.equals(a.alpha, b.alpha) && a.kinds == b.kinds
                    && (a.share == null ? b.share == null : Arrays.equals(a.share, b.share)) && (a.print == null ? b.print == null : Arrays.equals(a.print, b.print)),
                    "layer " + n + " plane " + p + ": tones, coverage, shares, print tone and kind, byte for byte");
        }
        check(Arrays.equals(shown(back), shown(d)) && Arrays.equals(write(back), bytes), "it shows as it did and writes the same bytes again");
        // The cost against the same page as one plane a layer (what a reader before planes reads: scripts/test_inkwash.sh's OlderKindCheck).
        ToneDocument.Snapshot looks = d.layerSnapshot();
        for (int n = 0; n < 2; n++) looks.layers.set(n, looks.layers.get(n).flattened());
        ByteArrayOutputStream flat = new ByteArrayOutputStream(); DocumentCodec.write(flat, looks);
        System.out.println("file: " + bytes.length + " bytes with planes, " + flat.size() + " as one plane a layer (" + W + "x" + H + ")");
    }

    /** The Lasso lifts every plane and lays each back on its own plane. */
    static void lasso() {
        ToneDocument d = InkWashLayers.blank(W, H);
        block(d, InkWashLayers.INK, ToneDocument.KIND_PENCIL, 20, 20, 30, 30, 120);
        block(d, InkWashLayers.INK, ToneDocument.KIND_PEN, 30, 10, 6, 50, 0);
        wash(d, 25, 25, 20, 20, 150);
        int[] before = shown(d);
        FloatingSelection piece = LassoPlanes.lift(d, new float[]{10, 5, 60, 5, 60, 70, 10, 70}, 4);
        check(piece != null, "lifted");
        piece.grab(FloatingSelection.MOVE); piece.drag(FloatingSelection.MOVE, 0, 0, 100, 40); piece.release();
        check(piece.place(d), "laid");
        boolean same = true;
        for (int y = 0; y < 80; y++) for (int x = 0; x < 70; x++) same &= d.compositeTone(x + 100, y + 40) == before[y * W + x] && d.compositeTone(x, y) == 255;
        check(same, "the piece shows as it did where it went, paper where it was");
        ToneDocument.Layer ink = layer(d, InkWashLayers.INK);
        check(ink.plane(ToneDocument.PLANE_PEN).alpha[(10 + 40) * W + 30 + 100] != 0 && ink.plane(ToneDocument.PLANE_DRY).alpha[(20 + 40) * W + 20 + 100] != 0 && !ink.covers(),
                "each kind lands on its own plane, the base bare");
        EraseKindChecks.erase(d, ToneDocument.KIND_PEN);
        check(covered(layer(d, InkWashLayers.INK).plane(ToneDocument.PLANE_DRY)) == 900, "and erases by kind after: Pen leaves the moved pencil whole");
    }

    /** A stump (no paint of its own) blends dry media on its plane and leaves the pen crisp. */
    static void smudge() {
        ModularBrush stump = null;
        for (ToolPresets.Preset p : ToolPresets.all()) if (p.name.equals("Blend stump")) stump = p.brush().parts;
        ToneDocument d = InkWashLayers.blank(W, H);
        for (int k = 0; k < 6; k++) block(d, InkWashLayers.INK, ToneDocument.KIND_PENCIL, 100 + 30 * k, 150, 8, 60, 60);
        block(d, InkWashLayers.INK, ToneDocument.KIND_PEN, 90, 175, 220, 6, 0);
        ToneDocument.Layer pen = copy(layer(d, InkWashLayers.INK).plane(ToneDocument.PLANE_PEN)), dry = copy(layer(d, InkWashLayers.INK).plane(ToneDocument.PLANE_DRY));
        CarrySmudgeChecks.live(d, stump, 28, 1, null, CarrySmudgeChecks.across(60, 360, 180));
        check(samePlane(pen, layer(d, InkWashLayers.INK).plane(ToneDocument.PLANE_PEN)), "the pen plane is as it was");
        check(!samePlane(dry, layer(d, InkWashLayers.INK).plane(ToneDocument.PLANE_DRY)), "the dry media are blended");
    }

    /** A kind shown Faint or Hidden on the page (step 2's row), new planes too, Shown again as it was. */
    static void shown() {
        ToneDocument d = InkWashLayers.blank(W, H);
        block(d, InkWashLayers.INK, ToneDocument.KIND_PENCIL, 20, 20, 60, 60, 0);
        int[] all = shown(d);
        d.setKindOpacity(ToneDocument.PLANE_DRY, 0);
        check(d.compositeTone(40, 40) == 255, "Dry media hidden: paper");
        d.setKindOpacity(ToneDocument.PLANE_DRY, 25);
        int faint = d.compositeTone(40, 40);
        check(faint > 180 && faint < 200, "Faint: a quarter of it: " + faint);
        d.setKindOpacity(ToneDocument.PLANE_PEN, 0);
        block(d, InkWashLayers.INK, ToneDocument.KIND_PEN, 100, 20, 20, 20, 0);
        check(d.compositeTone(110, 30) == 255 && d.kindOpacity(ToneDocument.PLANE_PEN) == 0, "A plane made while its kind is hidden is hidden too");
        d.setKindOpacity(ToneDocument.PLANE_DRY, 100); d.setKindOpacity(ToneDocument.PLANE_PEN, 100);
        check(d.compositeTone(40, 40) == all[40 * W + 40] && d.compositeTone(110, 30) == 0, "Shown again as it was");
    }
}
