package io.github.mpdairy.monopaint;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * An eraser that takes one kind of mark (spec docs/archive/superpowers/specs/2026-10-20-erase-kind-design.md), on kind planes
 * since 2026-10-27 (docs/superpowers/specs/2026-10-27-kind-layers-design.md): each Medium's marks on its kind's plane of
 * the layer; a chosen kind erases its planes whole, permanent paint too, and leaves the others byte for byte; paint of no
 * kind (old pages) keeps the per-pixel bitmask; Everything takes every mark whole (the owner, 2026-10-22); undo and redo
 * bring the planes back; the file keeps them in sections older readers skip and old drawings never gain. {@code write
 * <file>} writes the file the older reader's check opens (scripts/test_inkwash.sh).
 */
public final class EraseKindChecks {
    static final int W = ErasableChecks.W, H = ErasableChecks.H;
    static int checks;

    public static void main(String[] args) throws IOException {
        Assets.install(Assets.folder(Assets.FOLDER));
        if (args.length == 2 && args[0].equals("write")) {
            try (java.io.OutputStream out = new java.io.FileOutputStream(args[1])) { DocumentCodec.write(out, mixed().layerSnapshot()); }
            System.out.println(pixelAt(mixed()));
            return;
        }
        media(); tagging(); targeted(); paint(); everything(); oldPages(); ghost(); wet(); undo(); file();
        System.out.println("PASS: Erase kind (" + checks + " checks): each Medium lays Pen, Paint or Pencil on its kind's plane, opaque paint on a plane of its own; "
                + "Pen, Paint and Dry Media each erase their planes whole (permanent paint too) and leave the others byte for byte, pencil under ink coming off exactly; "
                + "charcoal's ghost goes with Dry Media and stays under Pen; "
                + "Everything takes every mark whole, permanent ink too, and its preview agrees; paint from before has no kind and erases as ever; a Slow wash and a Float print are on their planes; "
                + "Undo and Redo bring strokes and erasing back with their kinds; the file keeps the planes, a drawing never drawn on with them is written as before, "
                + "an older reader skips the sections, a corrupt one is refused");
    }

    static void check(boolean ok, String what) { checks++; if (!ok) throw new AssertionError(what); }

    // ---- helpers ----

    /** A pixel of coverage 254 at the Tone dot's tone is a dot to the eraser and scrapes by chance (EraseBothStroke.scrapes): a few stay. */
    static final int STRAY = 3;
    static boolean near(int n, int of) { return n <= of && n >= of - STRAY; }

    /**
     * Layer {@code i} as one: its look (base and kind planes, {@link ToneDocument.Layer#flattened}) and, per pixel, the kinds
     * it holds ({@link ToneDocument.Layer#kindsAt}), its base's (old paint counting as every kind) and each plane's with paint there.
     */
    static ToneDocument.Layer layer(ToneDocument d, int i) {
        ToneDocument.Layer l = d.layerSnapshot().layers.get(i), look = l.flattened(), view = new ToneDocument.Layer(l.name, l.visible, look.tones, look.alpha);
        byte[] kinds = new byte[look.alpha.length];
        int all = 0;
        for (int p = 0; p < kinds.length; p++) {
            int k = l.alpha[p] != 0 ? l.kindsAt(p) : 0;
            for (int q = 0; q < ToneDocument.PLANES; q++) if (l.plane(q) != null && l.plane(q).alpha[p] != 0) k |= ToneDocument.PLANE_KINDS[q];
            kinds[p] = (byte) k; all |= k;
        }
        view.kinds = (byte) all; view.kindBase = (byte) all; view.kind = kinds;
        return view;
    }
    /** Layer {@code i} itself. */
    static ToneDocument.Layer raw(ToneDocument d, int i) { return d.layerSnapshot().layers.get(i); }
    /** The page: pencil sketch and ink over it on the ink layer, a wash on the wash layer. */
    static ToneDocument mixed() {
        ToneDocument d = InkWashLayers.blank(W, H);
        ErasableChecks.stroke(d, ErasableChecks.preset("Soft wash"), 90, ErasableChecks.across(40, 440, 100));
        ErasableChecks.sketch(d, ErasableChecks.preset("Sketch pencil"));
        ErasableChecks.ink(d, ErasableChecks.preset("Steady line"));
        return d;
    }
    static String pixelAt(ToneDocument d) { return "180 " + 100 + " " + d.compositeTone(180, 100); }
    /** The eraser over every pixel: a finger 160 px wide, row after row, limited to {@code kinds}. */
    static void erase(ToneDocument d, int kinds) {
        EraseBothStroke s = new EraseBothStroke(d, 160f, kinds);
        for (int y = -40; y <= d.height + 40; y += 50) for (int x = -60; x <= d.width + 60; x += 20) s.sample(x, y, 1f, Float.NaN, Float.NaN);
        s.finish();
    }
    static int count(ToneDocument.Layer l, int kinds) { int n = 0; for (int i = 0; i < l.alpha.length; i++) if (l.alpha[i] != 0 && l.kindsAt(i) == kinds) n++; return n; }
    static int covered(ToneDocument.Layer l) { int n = 0; for (byte a : l.alpha) if (a != 0) n++; return n; }
    /** Pixels whose tone or coverage differ. */
    static int differ(ToneDocument.Layer a, ToneDocument.Layer b) { int n = 0; for (int i = 0; i < a.alpha.length; i++) if (a.alpha[i] != b.alpha[i] || a.tones[i] != b.tones[i]) n++; return n; }
    /** Pixels of {@code before} that held exactly {@code kinds} and now differ in {@code after}. */
    static int changed(ToneDocument.Layer before, ToneDocument.Layer after, int kinds) {
        int n = 0;
        for (int i = 0; i < before.alpha.length; i++) if (before.alpha[i] != 0 && before.kindsAt(i) == kinds && (before.alpha[i] != after.alpha[i] || before.tones[i] != after.tones[i])) n++;
        return n;
    }

    // ---- the checks ----

    /** Each Medium lays one kind; a brush with no paint of its own lays none. */
    static void media() {
        ModularBrush steady = ErasableChecks.preset("Steady line");
        Object[][] kinds = {{PartChoice.INK, 1}, {PartChoice.RESIST, 1}, {PartChoice.TONE, 2}, {PartChoice.WASH, 2}, {PartChoice.COVER, 2 | ToneDocument.KIND_OPAQUE},
                {PartChoice.CHARCOAL, 4}, {PartChoice.GRAPHITE, 4}, {PartChoice.CRAYON, 4}};
        for (Object[] k : kinds) {
            ModularBrush b = ModularCode.decode(ModularCode.encode(steady));
            b.set(BrushPart.MEDIUM, ModularBrush.switchTo(b.part(BrushPart.MEDIUM), (PartChoice) k[0]));
            check(ResolvedBrush.of(b, 8, .6).kind == (Integer) k[1], k[0] + " lays kind " + k[1]);
        }
        // A texture is Paint whatever its medium (the owner, 2026-10-29), so Pen erases pen lines only: a pattern tip, Print Shading.
        for (PartChoice tip : new PartChoice[]{PartChoice.STIPPLE, PartChoice.HATCH, PartChoice.HAND_HATCH}) for (PartChoice m : new PartChoice[]{PartChoice.INK, PartChoice.GRAPHITE}) {
            ModularBrush b = ModularCode.decode(ModularCode.encode(steady));
            b.set(BrushPart.TIP, ModularBrush.switchTo(b.part(BrushPart.TIP), tip)); b.set(BrushPart.MEDIUM, ModularBrush.switchTo(b.part(BrushPart.MEDIUM), m));
            check(ResolvedBrush.of(b, 8, .6).kind == ToneDocument.KIND_PAINT, tip + " in " + m + " lays Paint");
        }
        check(ResolvedBrush.of(ScreenChecks.screened(steady, 6), 8, .6).kind == ToneDocument.KIND_PAINT && ResolvedBrush.of(steady, 8, .6).kind == ToneDocument.KIND_PEN,
                "Ben-Day ink lays Paint, the plain pen Pen");
        check(EraseKind.LABELS.length == 4 && EraseKind.KINDS[0] == 0 && EraseKind.choiceOf(EraseKind.KINDS[3]) == 3 && EraseKind.LABELS[3].equals("Dry Media") && EraseKind.MARKS[0].isEmpty() && EraseKind.choiceOf(7) == 0, "four choices, Everything first");
    }

    /** Each kind's marks are on its plane, made when it first draws; the base and its bitmask stay as they were. */
    static void tagging() {
        ToneDocument d = InkWashLayers.blank(W, H);
        ErasableChecks.sketch(d, ErasableChecks.preset("Sketch pencil"));
        ToneDocument.Layer raw = raw(d, InkWashLayers.INK);
        check(raw.plane(ToneDocument.PLANE_DRY) != null && raw.plane(ToneDocument.PLANE_PEN) == null && !raw.covers() && raw.kind == null && raw.kinds == 0, "Pencil alone: on the dry media plane, the base bare, no bitmask");
        ToneDocument.Layer pencil = layer(d, InkWashLayers.INK);
        check(count(pencil, 4) == covered(pencil) && covered(pencil) > 3000, "every pencil pixel is Pencil");
        ErasableChecks.ink(d, ErasableChecks.preset("Steady line"));
        raw = raw(d, InkWashLayers.INK);
        check(raw.plane(ToneDocument.PLANE_PEN) != null && raw.plane(ToneDocument.PLANE_PEN).kind == null && raw.plane(ToneDocument.PLANE_DRY).kind == null && raw.kind == null && !raw.covers(),
                "Ink over pencil: a pen plane beside the dry media one, no bitmask anywhere");
        ToneDocument.Layer both = layer(d, InkWashLayers.INK);
        int pen = count(both, 1), pen4 = count(both, 4), mix = count(both, 5);
        check(pen > 100 && pen4 > 1000 && mix > 100, "ink-only, pencil-only and mixed pixels: " + pen + " " + pen4 + " " + mix);
        check(pen + pen4 + mix == covered(both), "every covered pixel holds only Pen and Pencil");
        // The ink's own pixels all hold Pen but the few a darker pencil pixel left unchanged.
        ToneDocument alone = InkWashLayers.blank(W, H);
        ErasableChecks.ink(alone, ErasableChecks.preset("Steady line"));
        ToneDocument.Layer inkOnly = layer(alone, InkWashLayers.INK);
        check(raw(alone, InkWashLayers.INK).plane(ToneDocument.PLANE_DRY) == null && count(inkOnly, 1) == covered(inkOnly), "Ink alone: on the pen plane only");
        int inked = 0, tagged = 0;
        for (int i = 0; i < inkOnly.alpha.length; i++) if (inkOnly.alpha[i] != 0) { inked++; if ((both.kindsAt(i) & 1) != 0) tagged++; }
        check(tagged > .97 * inked, "the ink's pixels hold Pen: " + tagged + " of " + inked);
        // The wash is on the other layer of the pair, one kind, no plane.
        ToneDocument m = mixed();
        ToneDocument.Layer wash = layer(m, InkWashLayers.WASH);
        check(raw(m, InkWashLayers.WASH).plane(ToneDocument.PLANE_WASH) != null && count(wash, 2) == covered(wash), "A wash: on the wash plane, kind Paint");
    }

    /** A chosen kind takes its planes whole, permanent too, and nothing else: where pen crosses pencil, exactly the one chosen. */
    static void targeted() {
        ToneDocument base = mixed();
        ToneDocument.Layer before = layer(base, InkWashLayers.INK), washBefore = layer(base, InkWashLayers.WASH);
        ToneDocument.Layer drawn = raw(base, InkWashLayers.INK);
        int pen = count(before, 1), pencil = count(before, 4), mix = count(before, 5);
        // Pen: every pen mark goes (the ink is permanent), where it crosses pencil too; the pencil stays byte for byte, the wash too.
        ToneDocument d = mixed();
        erase(d, ToneDocument.KIND_PEN);
        ToneDocument.Layer after = layer(d, InkWashLayers.INK), was = raw(d, InkWashLayers.INK);
        check(covered(was.plane(ToneDocument.PLANE_PEN)) == 0 && near(changed(before, after, 1), pen), "Pen takes every ink pixel, permanent as it is (" + pen + ")");
        check(differ(drawn.plane(ToneDocument.PLANE_DRY), was.plane(ToneDocument.PLANE_DRY)) == 0, "Pen leaves the pencil byte for byte, under the ink too (" + mix + " crossings)");
        ToneDocument pencilAlone = InkWashLayers.blank(W, H);
        ErasableChecks.sketch(pencilAlone, ErasableChecks.preset("Sketch pencil"));
        check(differ(after, layer(pencilAlone, InkWashLayers.INK)) == 0, "Ink over pencil, Pen erased: the pencil as drawn alone, every pixel");
        check(differ(washBefore, layer(d, InkWashLayers.WASH)) == 0, "Pen leaves the wash");
        // Dry Media: every pencil mark goes, the ink stays byte for byte: pencil under ink comes off exactly.
        d = mixed();
        erase(d, ToneDocument.KIND_PENCIL);
        after = layer(d, InkWashLayers.INK);
        check(covered(raw(d, InkWashLayers.INK).plane(ToneDocument.PLANE_DRY)) == 0 && near(changed(before, after, 4), pencil), "Pencil takes every pencil pixel (" + pencil + ")");
        check(changed(before, after, 1) == 0, "Pencil leaves the ink");
        ToneDocument alone = InkWashLayers.blank(W, H);
        ErasableChecks.ink(alone, ErasableChecks.preset("Steady line"));
        int off = differ(after, layer(alone, InkWashLayers.INK));
        check(off == 0, "Pencil under ink, erased: the ink as drawn alone, every pixel (" + off + " off)");
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) if (d.compositeTone(x, y) != compositeWithWash(alone, base, x, y)) { check(false, "and the page shows the ink over the wash at " + x + ", " + y); }
        check(differ(washBefore, layer(d, InkWashLayers.WASH)) == 0, "Pencil leaves the wash");
        check(mix > 100, "there were crossings: " + mix);
    }
    /** {@code ink}'s page with {@code wash}'s wash layer under it: the mixed page with its pencil gone. */
    static int compositeWithWash(ToneDocument ink, ToneDocument wash, int x, int y) {
        return CACHE.computeIfAbsent(ink, k -> {
            java.util.ArrayList<ToneDocument.Layer> layers = new java.util.ArrayList<>();
            layers.add(wash.layerSnapshot().layers.get(InkWashLayers.WASH)); layers.add(ink.layerSnapshot().layers.get(InkWashLayers.INK));
            return new ToneDocument(W, H, layers, InkWashLayers.INK);
        }).compositeTone(x, y);
    }
    private static final java.util.Map<ToneDocument, ToneDocument> CACHE = new java.util.IdentityHashMap<>();

    /** Paint takes a wash whole, as Everything does, and leaves the ink layer. */
    static void paint() {
        ToneDocument base = mixed();
        ToneDocument.Layer inkBefore = layer(base, InkWashLayers.INK);
        ToneDocument every = mixed(), only = mixed();
        erase(every, 0);
        erase(only, ToneDocument.KIND_PAINT);
        check(covered(layer(every, InkWashLayers.WASH)) == 0, "Everything takes a wash at .4 whole");
        check(covered(layer(only, InkWashLayers.WASH)) == 0, "Paint takes the wash whole");
        check(differ(inkBefore, layer(only, InkWashLayers.INK)) == 0, "Paint leaves the ink layer");
        // The preview agrees: the kept page.
        ToneDocument p = mixed();
        EraseBothStroke e = new EraseBothStroke(p, 120f, ToneDocument.KIND_PAINT);
        for (int x = 0; x <= W; x += 20) e.sample(x, 100, 1f, Float.NaN, Float.NaN);
        int[] shown = new int[W * H];
        e.erasedTones(0, 0, W, H, shown);
        e.finish();
        int seen = 0, wrong = 0;
        for (int i = 0; i < shown.length; i++) if (shown[i] >= 0) { seen++; if (shown[i] != p.compositeTone(i % W, i / W)) wrong++; }
        check(seen > 1000 && wrong == 0, "the preview is the page as erased, over " + seen + " pixels (" + wrong + " wrong)");
    }

    /**
     * Everything takes every mark whole, permanent ink too (the owner, 2026-10-22: "Pen isn't erased by eraser
     * (everything)"), with the constructor that predates kinds alike; its preview is the page as erased.
     */
    static void everything() {
        ToneDocument d = mixed(), plain = mixed();
        erase(d, 0);
        // The same stroke with the constructor that predates kinds.
        EraseBothStroke s = new EraseBothStroke(plain, 160f);
        for (int y = -40; y <= plain.height + 40; y += 50) for (int x = -60; x <= plain.width + 60; x += 20) s.sample(x, y, 1f, Float.NaN, Float.NaN);
        s.finish();
        for (int n = 0; n < 2; n++) check(differ(layer(d, n), layer(plain, n)) == 0, "Everything is the kindless constructor's eraser, layer " + n);
        for (int n = 0; n < 2; n++) check(covered(layer(d, n)) <= STRAY, "Everything takes every mark to paper, permanent ink too (but a scraped dot or two), layer " + n + ": " + covered(layer(d, n)));
        ToneDocument p = mixed();
        EraseBothStroke e = new EraseBothStroke(p, 120f, 0);
        for (int x = 0; x <= W; x += 20) e.sample(x, 100, 1f, Float.NaN, Float.NaN);
        int[] shown = new int[W * H];
        e.erasedTones(0, 0, W, H, shown);
        e.finish();
        int seen = 0, wrong = 0;
        for (int i = 0; i < shown.length; i++) if (shown[i] >= 0) { seen++; if (shown[i] != p.compositeTone(i % W, i / W)) wrong++; }
        check(seen > 1000 && wrong == 0, "Everything's preview is the page as erased, over " + seen + " pixels (" + wrong + " wrong)");
    }

    /** Paint from before kinds has none: a chosen kind erases it as the eraser did, and a file of it is written as before. */
    static void oldPages() throws IOException {
        ToneDocument d = InkWashLayers.blank(W, H);
        d.begin();
        for (int x = 100; x < 300; x++) for (int y = 100; y < 120; y++) d.setTone(x, y, 40);
        d.finish();
        ToneDocument.Layer l = raw(d, InkWashLayers.INK);
        check(covered(l) > 0, "painted");
        // Without a plane or kinds.
        check(l.kinds == 0 && l.kind == null && !l.kinded() && l.planes == null, "A gesture of no kind leaves the layer's kinds alone, on its base");
        int magic = new java.io.DataInputStream(new ByteArrayInputStream(write(d))).readInt();
        check(magic == 0x54534d33, "and the file is TSM3 as before");
        // A kind laid over paint of no known kind: on its plane; the old paint, on the base, counts as every kind.
        d.selectLayer(InkWashLayers.INK);
        d.begin(1, ToneDocument.KIND_PEN);
        d.setTone(50, 50, 0);
        d.finish();
        ToneDocument.Layer r = raw(d, InkWashLayers.INK);
        l = layer(d, InkWashLayers.INK);
        check(r.kinds == 0 && r.kind == null && covered(r.plane(ToneDocument.PLANE_PEN)) == 1 && l.kindsAt(50 * W + 50) == 1 && l.kindsAt(110 * W + 150) == 7, "Old paint counts as every kind; a kind laid over it is on its plane");
        erase(d, ToneDocument.KIND_PENCIL);
        check(covered(layer(d, InkWashLayers.INK)) == 1, "a chosen kind erases old paint as the eraser did, leaving only the Pen pixel (permanent, and Pencil is not Pen)");
        ToneDocument g = InkWashLayers.blank(W, H);
        g.begin(); for (int x = 100; x < 300; x++) g.setTone(x, 100, 40); g.finish();
        erase(g, ToneDocument.KIND_PEN);
        check(covered(layer(g, InkWashLayers.INK)) == 0, "a page of paint from before is erased by any chosen kind");
    }

    /**
     * The owner's ghost (page 34 of "Loose pages", 2026-10-20): charcoal at Erasable .9 crossed by an erasable pen mark,
     * erased by the Erasable rule (the Eraser on Everything until 2026-10-22, {@link ErasableChecks#eraseAll}), leaves
     * charcoal's faint permanent tenth; on planes it is charcoal's alone, where the pen mark was too. Dry Media takes it,
     * Pen does not, Everything does.
     */
    static void ghost() {
        ToneDocument d = ghostPage();
        ErasableChecks.eraseAll(d);
        ToneDocument.Layer residue = layer(d, InkWashLayers.INK);
        check(count(residue, 5) == 0 && count(residue, 1) == 0 && count(residue, 4) > 1000, "The Erasable rule leaves charcoal's tenth, on its plane alone: no pixel holds Pen");
        ToneDocument every = ghostPage();
        ErasableChecks.eraseAll(every);
        erase(every, 0);
        check(covered(layer(every, InkWashLayers.INK)) == 0, "Everything takes the ghost");
        ToneDocument pen = ghostPage();
        ErasableChecks.eraseAll(pen);
        erase(pen, ToneDocument.KIND_PEN);
        check(differ(residue, layer(pen, InkWashLayers.INK)) == 0, "Pen leaves the charcoal's ghost: charcoal is not Pen");
        erase(d, ToneDocument.KIND_PENCIL);
        check(covered(layer(d, InkWashLayers.INK)) == 0, "Dry Media takes the ghost, where the pen mark was too: " + covered(layer(d, InkWashLayers.INK)) + " pixels left");
    }
    /** Charcoal's sketch at .9 and a broad pen line all erasable across it, on the ink layer. */
    static ToneDocument ghostPage() {
        ToneDocument d = InkWashLayers.blank(W, H);
        ErasableChecks.sketch(d, ErasableChecks.erasable(ErasableChecks.preset("Crisp charcoal"), .9));
        ErasableChecks.stroke(d, ErasableChecks.allErasable(ErasableChecks.preset("Steady line")), 30, ErasableChecks.across(40, 440, 100));
        return d;
    }

    /** A Slow wash settled over its ticks and a Float print carry their brush's kind. */
    static void wet() {
        GravityChecks.Drawn s = GravityChecks.draw(ErasableChecks.preset("Slow bloom"), t -> PageTilt.LEVEL, GravityChecks.washPts(90, 430, 140, 25), 110, false);
        check(s.ticks > 3, "settled over ticks");
        ToneDocument.Layer wash = layer(s.page, InkWashLayers.WASH);
        check(wash.kinds == 2 && raw(s.page, InkWashLayers.WASH).plane(ToneDocument.PLANE_WASH) != null && !raw(s.page, InkWashLayers.WASH).covers(), "A Slow wash lays Paint, settle and all on the wash plane");
        long was = 0; for (byte a : wash.alpha) was += a & 255;
        erase(s.page, ToneDocument.KIND_PAINT);
        long now = 0; for (byte a : layer(s.page, InkWashLayers.WASH).alpha) now += a & 255;
        check(was > 0 && now == 0, "and Paint takes it whole, settle and all");
        GravityChecks.Drawn f = GravityChecks.draw(ErasableChecks.erasable(ErasableChecks.preset("Sumi"), 0), t -> PageTilt.LEVEL, GravityChecks.washPts(90, 430, 140, 25), 30, false);
        ToneDocument.Layer print = layer(f.page, InkWashLayers.INK);
        check(print.kinds == 1 && covered(print) > 0 && !raw(f.page, InkWashLayers.INK).covers(), "A Float print of ink lays Pen, on the pen plane");
        ErasableChecks.eraseAll(f.page);
        check(covered(layer(f.page, InkWashLayers.INK)) > 0, "The Erasable rule leaves the permanent print");
        erase(f.page, ToneDocument.KIND_PEN);
        check(covered(layer(f.page, InkWashLayers.INK)) == 0, "Pen takes it, permanent as it is");
    }

    /** Undo and redo bring the kinds back with the strokes and the erasing. */
    static void undo() {
        ToneDocument d = InkWashLayers.blank(W, H);
        ErasableChecks.sketch(d, ErasableChecks.preset("Sketch pencil"));
        ToneDocument.Layer sketched = copyOf(d);
        ErasableChecks.ink(d, ErasableChecks.preset("Steady line"));
        ToneDocument.Layer inked = copyOf(d);
        erase(d, ToneDocument.KIND_PENCIL);
        ToneDocument.Layer erased = copyOf(d);
        check(d.undo() && same(copyOf(d), inked), "Undo the erasing: pixels and kinds as they were");
        check(d.undo() && same(copyOf(d), sketched), "Undo the ink: the pencil alone again, every pixel Pencil");
        check(d.redo() && same(copyOf(d), inked) && d.redo() && same(copyOf(d), erased), "Redo both, kinds and all");
        while (d.canUndo()) d.undo();
        check(covered(layer(d, InkWashLayers.INK)) == 0, "Undone to blank paper");
        while (d.canRedo()) d.redo();
        check(same(copyOf(d), erased), "and redone to the end");
    }
    /** A page's ink layer as pixels with the kinds each holds ({@link #layer}: its planes as one). */
    static ToneDocument.Layer copyOf(ToneDocument d) { return layer(d, InkWashLayers.INK); }
    static boolean same(ToneDocument.Layer a, ToneDocument.Layer b) {
        if (differ(a, b) != 0) return false;
        for (int i = 0; i < a.alpha.length; i++) if (a.alpha[i] != 0 && a.kindsAt(i) != b.kindsAt(i)) return false;
        return true;
    }

    static byte[] write(ToneDocument d) throws IOException { ByteArrayOutputStream out = new ByteArrayOutputStream(); DocumentCodec.write(out, d.layerSnapshot()); return out.toByteArray(); }

    /** The file keeps the kinds; a drawing without any is written as it was; a corrupt section is refused. */
    static void file() throws IOException {
        ToneDocument d = mixed();
        byte[] bytes = write(d);
        ToneDocument back = DocumentCodec.read(new ByteArrayInputStream(bytes));
        for (int n = 0; n < 2; n++) {
            ToneDocument.Layer a = layer(d, n), b = layer(back, n);
            check(a.kinds == b.kinds && Arrays.equals(a.kind, b.kind) && differ(a, b) == 0, "A round trip keeps layer " + n + "'s planes and kinds");
            for (int p = 0; p < ToneDocument.PLANES; p++) check((raw(d, n).plane(p) == null) == (raw(back, n).plane(p) == null), "and which planes it has, layer " + n);
        }
        check(Arrays.equals(write(back), bytes), "and writes the same bytes again");
        // Single-kind drawing: its one plane.
        ToneDocument one = InkWashLayers.blank(W, H);
        ErasableChecks.ink(one, ErasableChecks.preset("Steady line"));
        ToneDocument oneBack = DocumentCodec.read(new ByteArrayInputStream(write(one)));
        check(layer(oneBack, InkWashLayers.INK).kinds == 1 && raw(oneBack, InkWashLayers.INK).plane(ToneDocument.PLANE_PEN) != null && raw(oneBack, InkWashLayers.INK).plane(ToneDocument.PLANE_DRY) == null, "A one-kind layer comes back on its one plane");
        // The cost: the planes and the look written for older readers, against the same page as one plane a layer.
        int cost = bytes.length - withoutKinds(d);
        System.out.println("file cost: " + cost + " bytes for a mixed pencil-and-ink page at " + W + "x" + H + " (" + bytes.length + " in all); one-kind page " + (write(one).length - withoutKinds(one)));
        check(cost > 0 && cost < bytes.length, "the planes cost less than the page: " + cost);
        // A page with no kinds writes as before: its magic and its bytes unchanged by a round trip.
        ToneDocument old = InkWashLayers.blank(W, H);
        old.begin(); for (int x = 20; x < 90; x++) old.setTone(x, 40, 30); old.finish();
        byte[] oldBytes = write(old);
        check(Arrays.equals(oldBytes, write(DocumentCodec.read(new ByteArrayInputStream(oldBytes)))) && raw(old, 1).kinds == 0, "A page of no kinds round-trips byte for byte");
        // A corrupt section.
        byte[] bad = tamper(bytes);
        boolean refused = false;
        try { DocumentCodec.read(new ByteArrayInputStream(bad)); } catch (IOException e) { refused = true; }
        check(refused, "A section whose plane has no base kind is refused");
    }
    /** The drawing's file without its kinds (the snapshot's copies of the layers flattened, losing them), to size the sections. */
    static int withoutKinds(ToneDocument d) throws IOException {
        ToneDocument.Snapshot s = d.layerSnapshot();
        for (int i = 0; i < s.layers.size(); i++) { ToneDocument.Layer l = s.layers.get(i).flattened(); l.kinds = 0; l.kind = null; s.layers.set(i, l); }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DocumentCodec.write(out, s);
        return out.size();
    }
    /** {@code bytes} read and written again with the first layer holding three kinds and no base: a section the reader must refuse. */
    static byte[] tamper(byte[] bytes) throws IOException {
        ToneDocument.Snapshot s = DocumentCodec.read(new ByteArrayInputStream(bytes)).layerSnapshot();
        s.layers.get(0).kinds = 7; s.layers.get(0).kindBase = 0; s.layers.get(0).alpha[0] = 1; // paint on its base, so the section holds it
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DocumentCodec.write(out, s);
        return out.toByteArray();
    }
}
