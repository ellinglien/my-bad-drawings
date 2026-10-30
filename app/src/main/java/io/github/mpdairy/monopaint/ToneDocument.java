package io.github.mpdairy.monopaint;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Authoritative 8-bit tones, including black stipples deposited by watercolor.
 * Display dithering is derived from these tones. Confined to its owner thread, but for a gesture's
 * {@linkplain #openBands bands}. */
final class ToneDocument {
    static final int MAX_LAYERS = 8;
    static final int MAX_PIXELS = 1920 * 2560;
    /** Undo's tile side, pixels: also the rows a {@linkplain #openBands band} keeps to. */
    static final int TILE = 64;
    /** Undo's budget: an eighth of the heap (24 MB on the Manta), at most the 32 MB it always was (the checks set it). */
    static int HISTORY_BYTES = (int) Math.min(32L << 20, Runtime.getRuntime().maxMemory() / 8);
    /** A whole-page step (Clear page and the like) keeps the old 32 MB, so the few strokes after a clear don't push it out. */
    private static final int PAGE_STEP_BYTES = 32 << 20;
    int width, height;
    private byte[] tones, alpha;
    /** The bound layer's {@link Layer#share}, or null. */
    private byte[] share;
    /** Inkwash: the bound layer's {@link Layer#print}, or null. */
    private byte[] print;
    /** Inkwash: the bound layer's {@link Layer#kind}, or null. */
    private byte[] kind;
    /** What marks are: a bit each (Pen, Paint, Pencil), {@link Layer#kind}. */
    static final int KIND_PEN = 1, KIND_PAINT = 2, KIND_PENCIL = 4, KIND_ALL = 7;
    /** Inkwash: a {@link Layer#kind} plane's byte holds, above its kinds, those of them laid with a permanent part, shifted this far. */
    static final int PERMANENT = 3;
    /**
     * Inkwash: opaque paint's flag beside {@link #KIND_PAINT} ({@code ResolvedBrush.kind}): it picks {@link #PLANE_OPAQUE},
     * apart from wash's plane. Never a pixel's bit: to the eraser it is Paint.
     */
    static final int KIND_OPAQUE = 8;
    /**
     * Inkwash: a layer's kind planes ({@link Layer#planes}), bottom to top, the owner's order (2026-10-27): dry media, washes,
     * pen, opaque paint; {@link #PLANE_KINDS} the kind each holds.
     */
    static final int PLANE_DRY = 0, PLANE_WASH = 1, PLANE_PEN = 2, PLANE_OPAQUE = 3, PLANES = 4;
    static final int[] PLANE_KINDS = {KIND_PENCIL, KIND_PAINT, KIND_PEN, KIND_PAINT};
    private static final String[] PLANE_NAMES = {"Dry media", "Wash", "Pen", "Opaque"};
    /** Plane {@code p}'s name (never shown). */
    static String planeName(int p) { return PLANE_NAMES[p]; }
    /** Inkwash: the most places a page composites ({@link #stacked}): every layer's base and planes. */
    static final int STACK = MAX_LAYERS * (1 + PLANES);
    /** Where the screen's layer steps ({@link #visibleLayers}) keep Show Kinds' steps, above tracing paper's eight bits. */
    static final int KIND_STEPS = 16;
    /** The plane a gesture of {@code kinds} lays on ({@link #begin(double, int)}); -1, the layer's base, for none or more than one kind. */
    static int planeOf(int kinds) {
        if ((kinds & KIND_OPAQUE) != 0) return (kinds & KIND_ALL) == KIND_PAINT ? PLANE_OPAQUE : -1;
        return kinds == KIND_PENCIL ? PLANE_DRY : kinds == KIND_PAINT ? PLANE_WASH : kinds == KIND_PEN ? PLANE_PEN : -1;
    }
    /** The kinds a gesture begins with to lay on plane {@code p} (-1: the base, 0). */
    static int kindsOf(int p) { return p < 0 ? 0 : p == PLANE_OPAQUE ? KIND_PAINT | KIND_OPAQUE : PLANE_KINDS[p]; }
    /** How many places {@link #stacked} walks for {@code count} layers: each pair's two bases, then its planes kind by kind, a half each. */
    static int stackSize(int count) { return (count + 1) / 2 * 2 * (1 + PLANES); }
    /** The layer place {@code k} of {@link #stacked} belongs to. */
    static int stackOwner(int k) { return k / (2 * (1 + PLANES)) * 2 + k % 2; }
    /**
     * Inkwash: place {@code k} of the page's composite, bottom to top: pair by pair (wash {@code 2k}, ink {@code 2k + 1}, as
     * {@code InkWashLayers}), the two layers' bases, then their planes kind by kind, the wash half's under the ink half's; null
     * where that layer or plane isn't. A page without planes composites its layers in order, as ever.
     */
    static Layer stacked(java.util.List<Layer> layers, int k) {
        int n = stackOwner(k);
        if (n >= layers.size()) return null;
        Layer layer = layers.get(n);
        int slot = k % (2 * (1 + PLANES)) / 2;
        return slot == 0 ? layer : layer.plane(slot - 1);
    }
    /** The opacity, percent, place {@code entry} of layer {@code owner} composites at: the layer's, times a plane's own. */
    static int stackOpacity(Layer owner, Layer entry) { return entry == owner ? owner.opacity : owner.opacity * entry.opacity / 100; }
    /** The kinds this gesture lays ({@link #begin(double, int)}; 0: none), and the kinds the eraser is limited to (0: all, {@link #erasing}). */
    private int laying, erasing;
    /**
     * How much of what this gesture lays the eraser can't take, 0 (all erasable, as every gesture before Erasable) to
     * 255 (permanent): {@link #begin(double)}'s {@code 1 − erasable}.
     */
    private int permanent;
    /** The last tile {@link #baseTile} looked up, so a dab's pixels skip the map; reset with {@link #before}. */
    private byte[] lastBaseTile;
    private int lastBaseKey=-1;
    /**
     * Pixels this gesture's {@link #transparentMask} has fully glazed with {@link #glazedGray},
     * one bit each, a row of a tile per long. Overlapping dabs would only lay the same glaze again.
     */
    private final HashMap<Integer,long[]> glazed=new HashMap<>();
    private int glazedGray=-1;
    private final ArrayList<Layer> layers = new ArrayList<>();
    private int active;
    /**
     * Masking fluid over the whole page, or null: its darkness shields every layer from paint,
     * erasing and wet paint seeping in. It is never part of the artwork.
     */
    private Layer mask;
    /** Gestures paint the masking fluid instead of the active layer; see {@link #paintMaskingFluid}. */
    private boolean maskTarget;
    /** The layer {@link #tones} and {@link #alpha} belong to: the active one, or the mask during a masking gesture. */
    private Layer bound;
    private long revision;
    long revision() { return revision; }
    static final class Layer {
        String name;
        boolean visible;
        int opacity=100;
        byte[] tones, alpha;
        /**
         * Erasable: the share of each pixel's coverage the eraser takes, 0 to 255 (all of it; a pixel with no coverage
         * has 255), or null where every pixel's is all (the layer has never held paint that isn't wholly erasable).
         * What a pixel keeps when erased is its coverage times {@code 1 − share / 255} at the same raw tone.
         */
        byte[] share;
        /**
         * Inkwash: the print tone (Screen, {@link PrintScreen}): at each pixel the tone, 0 to 255, the screened marks laid
         * on this layer would show unscreened, which the screen turns into its dots; 255 where none (white), or null where
         * no screened brush has drawn on the layer. Never shown: it is what a later screened stroke builds on, so a second
         * stroke over the first prints darker. Erasing lowers it, Undo restores it, a file keeps it where any pixel holds some.
         */
        byte[] print;
        /**
         * Inkwash: the kinds of mark each pixel holds (a bit each: {@link #KIND_PEN}, {@link #KIND_PAINT}, {@link #KIND_PENCIL}), for an
         * eraser that takes one kind. Null while the layer holds one kind or none ({@link #kinds}): then every pixel is that.
         * A pixel's 0 means {@link #kindBase}, what the layer held before it had a plane. Under no coverage it means nothing.
         * Bits 3 to 5 ({@link #PERMANENT}): which of its kinds laid paint below Erasable 1 there ({@link #permanentAt}).
         */
        byte[] kind;
        /** Inkwash: every kind laid on the layer (0: none known; paint on such a layer counts as all kinds) and, once it has a plane, the kinds it held then. */
        byte kinds, kindBase;
        /**
         * Inkwash: the layer's kind planes ({@link #PLANES}, bottom to top), each a layer of its own holding one kind's marks
         * ({@link #kinds}), its {@link #opacity} the kind's on the page; null where that kind never drew here (the array
         * null: none). The layer's own pixels are its base, under them: old paint and paint of no kind.
         */
        Layer[] planes;
        volatile boolean shared;
        /**
         * Inkwash: how many snapshots ({@link Snapshot#release releasable}) still hold these arrays; guarded by the layer.
         * A snapshot never released keeps it above 0, so the next write copies them as before.
         */
        private int holds;
        /** On a snapshot's copy: the layer it was copied from (to release), or null. */
        private Layer source;
        Layer(String name, boolean visible, byte[] tones, byte[] alpha) {
            this.name=name; this.visible=visible; this.tones=tones; this.alpha=alpha;
        }
        /** Inkwash: whether any pixel of its base holds coverage. */
        boolean covers() { for(byte a:alpha) if(a!=0) return true; return false; }
        /** Inkwash: its kind plane {@code p}, or null. */
        Layer plane(int p) { return planes==null?null:planes[p]; }
        /** Inkwash: the layer's look as one plane ({@link #sheets}): itself when it has no planes, else a new layer of its pixels (no share, print or kinds). */
        Layer flattened() {
            if(planes==null) return this;
            byte[] t=new byte[alpha.length], a=new byte[alpha.length];
            for(int i=0;i<a.length;i++) { int s=sheets(this,i); t[i]=(byte)(s>>>8); a[i]=(byte)s; }
            Layer out=new Layer(name,visible,t,a); out.opacity=opacity; return out;
        }
        /** Inkwash: whether any pixel holds coverage, on its base or a plane. */
        boolean marked() {
            if(covers()) return true;
            if(planes!=null) for(Layer p:planes) if(p!=null&&p.covers()) return true;
            return false;
        }
        /** Inkwash: whether pixel {@code i} holds coverage, on its base or a plane. */
        boolean marks(int i) {
            if(alpha[i]!=0) return true;
            if(planes!=null) for(Layer p:planes) if(p!=null&&p.alpha[i]!=0) return true;
            return false;
        }
        /** Inkwash: the same layer with {@code transform} made of its base and of each plane (a reflection, a larger or cropped page), name and opacity kept. */
        Layer each(java.util.function.UnaryOperator<Layer> transform) {
            Layer out=transform.apply(this);
            out.name=name; out.visible=visible; out.opacity=opacity;
            if(planes!=null) { out.planes=new Layer[PLANES]; for(int p=0;p<PLANES;p++) if(planes[p]!=null) { out.planes[p]=transform.apply(planes[p]); out.planes[p].name=planes[p].name; out.planes[p].opacity=planes[p].opacity; } }
            return out;
        }
        // Saves own immutable array references. Only a subsequently edited layer is copied.
        Layer copy() {
            Layer copy;
            synchronized(this) { shared=true; holds++; copy=new Layer(name,visible,tones,alpha); copy.opacity=opacity; copy.share=share; copy.print=print; copy.kind=kind; copy.kinds=kinds; copy.kindBase=kindBase; }
            Layer[] own=planes;
            if(own!=null) { copy.planes=new Layer[PLANES]; for(int p=0;p<PLANES;p++) if(own[p]!=null) copy.planes[p]=own[p].copy(); }
            copy.source=this; return copy;
        }
        /** This copy's snapshot is done with: if its layer still holds the same arrays and nothing else holds them, its next write needn't copy them. */
        private void release() {
            if(planes!=null) for(Layer p:planes) if(p!=null) p.release();
            Layer layer=source; source=null;
            if(layer==null) return;
            synchronized(layer) { if(layer.tones==tones&&layer.alpha==alpha&&layer.share==share&&layer.print==print&&layer.kind==kind&&layer.holds>0&&--layer.holds==0) layer.shared=false; }
        }
        /** Inkwash: whether some pixel holds print tone ({@link #print}): what a file must keep ({@link DocumentCodec}). */
        boolean prints() {
            if(print==null) return false;
            for(byte p:print) if(p!=(byte)255) return true;
            return false;
        }
        /** Inkwash: the kinds pixel {@code i} holds: those of its plane, else the layer's; paint of no known kind counts as all. */
        int kindsAt(int i) {
            int k=kind==null?kinds:kind[i]!=0?kind[i]:kindBase;
            return k==0?KIND_ALL:k&KIND_ALL;
        }
        /**
         * Inkwash: the kinds that laid paint the Erasable rule keeps at pixel {@code i} (a plane's bits 3 to 5, {@link #PERMANENT}).
         * Without a plane, or at a base pixel, not known: every kind the pixel holds.
         */
        int permanentAt(int i) { return kind==null||kind[i]==0?kindsAt(i):kind[i]>>PERMANENT&KIND_ALL; }
        /** Inkwash: whether a file must keep this layer's kinds ({@link DocumentCodec}): it has some, and paint they are of. */
        boolean kinded() { return kinds!=0&&covers(); }
        /** Whether some covered pixel holds paint the eraser can't take all of: what a file must keep ({@link DocumentCodec}). */
        boolean keeps() {
            if(share==null) return false;
            for(int i=0;i<share.length;i++) if(share[i]!=(byte)255&&alpha[i]!=0) return true;
            return false;
        }
    }
    static final class Snapshot {
        final int width, height, active;
        final ArrayList<Layer> layers = new ArrayList<>();
        /** The masking fluid, or null when the page has none. */
        final Layer mask;
        /** Inkwash: the page's magnets (immutable, shared). */
        final Magnets magnets;
        /** Inkwash: the page's mirrors (immutable, shared). */
        final Mirrors mirrors;
        /** Inkwash: the page's {@linkplain #paperTone paper}, and a cover's {@linkplain #paperPixels paper pixels} (else null). */
        final int paper;
        final byte[] paperPixels;
        /** Inkwash: where a cover's design is rubbed off ({@link CoverWear}), or null. */
        final CoverWear wear;
        /** Inkwash: a notebook template's guides ({@link ToneDocument#guides}), or null. */
        final byte[] guides;
        /** Inkwash: a timed page's clock ({@link PageClock}). */
        final PageClock clock;
        /** Inkwash: the page's Daily Prompt card ({@link DailyPrompt#line}), or null. */
        final String prompt;
        /** Inkwash: Show Kinds' steps on the page ({@link ToneDocument#kindsShown}). */
        final int kindsShown;
        /** Inkwash: the page's Timed Sections ({@link TimedSections}). */
        final TimedSections sections;
        /** Inkwash: the strokes the page can replay ({@link StrokeLog}), as they stand at the page's Undo step. */
        final StrokeLog log;
        /**
         * Inkwash: the marks fading or darkening by the clock (Fading, Lemon Juice: {@link FadingInk}) on these layers,
         * each on its copy here (immutable, shared), as their pixels show in this snapshot.
         */
        final java.util.List<FadingInk.Mark> fades;
        Snapshot(ToneDocument document) {
            width=document.width; height=document.height; active=document.active; paper=document.paper; paperPixels=document.paperPixels; wear=document.wear; guides=document.guides; clock=document.clock; prompt=document.prompt; kindsShown=document.kindsShown; sections=document.sections; log=document.log.saved(document.top());
            for(Layer layer:document.layers) layers.add(layer.copy());
            mask=document.hasMask()?document.mask.copy():null;
            magnets=document.magnets; mirrors=document.mirrors;
            java.util.List<FadingInk.Mark> on=new ArrayList<>();
            for(FadingInk.Mark m:document.fades) {
                Layer copy=at(layers,address(document.layers,m.layer));
                if(copy!=null&&m.width==width&&m.height==height) on.add(m.on(copy));
            }
            fades=java.util.Collections.unmodifiableList(on);
        }
        /**
         * Inkwash: nothing reads this snapshot any more (a save wrote it, or dropped it): the page's next write needn't copy
         * whole layers for it (10–15 MB, on the drawing thread). Optional: a snapshot never released only keeps that
         * copy. Call it once, from any thread, and read the snapshot no more after (nor any page made from it).
         */
        void release() {
            for(Layer layer:layers) layer.release();
            if(mask!=null) mask.release();
        }
        /** Inkwash: the paper pixels under the layers as the page shows them: a cover's, or the guides on the paper's tone; null: the tone alone. */
        byte[] shownPaper() { return paperPixels!=null?paperPixels:guided(guides,paper); }
        /** Inkwash: whether nothing is on the page, as {@link ToneDocument#unmarked}. */
        boolean unmarked() {
            if(wear!=null||clock.locked||prompt!=null||sections.started()||!magnets.isEmpty()||!mirrors.isEmpty()||mask!=null&&mask.covers()) return false;
            for(Layer layer:layers) if(layer.marked()) return false;
            return true;
        }
    }
    Snapshot layerSnapshot() { return new Snapshot(this); }
    /** Inkwash: where {@code layer} is in {@code layers}: its index, a plane's with its slot (1 to {@link #PLANES}) shifted by 4; -1 when it isn't there. */
    static int address(java.util.List<Layer> layers,Layer layer) {
        for(int n=0;n<layers.size();n++) {
            Layer l=layers.get(n);
            if(l==layer) return n;
            if(l.planes!=null) for(int p=0;p<PLANES;p++) if(l.planes[p]==layer) return n|(p+1)<<4;
        }
        return -1;
    }
    /** Inkwash: the layer or plane at {@link #address} {@code address} of {@code layers}, or null. */
    static Layer at(java.util.List<Layer> layers,int address) {
        int n=address&15, slot=address>>4;
        if(address<0||n>=layers.size()||slot>PLANES) return null;
        return slot==0?layers.get(n):layers.get(n).plane(slot-1);
    }
    /** Inkwash: the index of the layer {@code layer} is or is a plane of; -1 for none (the masking fluid). */
    private int ownerOf(Layer layer) { int a=address(layers,layer); return a<0?-1:a&15; }
    /** A page holding what {@code snapshot} holds, its magnets and paper too, with no history. */
    ToneDocument(Snapshot snapshot) { this(snapshot.width,snapshot.height,snapshot.layers,snapshot.active,snapshot.mask); magnets=snapshot.magnets; mirrors=snapshot.mirrors; paper=snapshot.paper; paperPixels=madePaper=snapshot.paperPixels; wear=snapshot.wear; guides=snapshot.guides; clock=snapshot.clock; prompt=snapshot.prompt; kindsShown=snapshot.kindsShown; sections=snapshot.sections; log=snapshot.log; fades.addAll(snapshot.fades); }

    /**
     * Inkwash: the page's paper (PageShade), the tone every composite of the page starts from: 255, white, as every page
     * before page shades, to 0, black. Not a layer: no gesture writes it and the brushes read their layers over white,
     * so they find blank paper whatever its shade; transparent paint only darkens it, opaque paint covers it.
     */
    private int paper=255;
    int paperTone() { return paper; }
    /** The page's paper as read from a file: no Undo step. */
    void placePaper(int tone) { if(tone<0||tone>255) throw new IllegalArgumentException("Invalid tone"); paper=tone; }
    /** Inkwash: the paper made {@code tone} as a change to save, not an Undo step (a blank page's, when its notebook's Page Colour changes). */
    void setPaper(int tone) { if(tone==paper)return; placePaper(tone); revision++; markAllDirty(); }
    /** Shades the paper {@code tone} as one undoable step; false, changing nothing, when it is that already. */
    boolean shadePaper(int tone) {
        idle(); if(tone<0||tone>255) throw new IllegalArgumentException("Invalid tone");
        if(tone==paper) return false;
        StackState old=new StackState(this); paper=tone; stackEdit(old); return true;
    }
    /**
     * Inkwash: a timed page's clock (Timed Pages, {@link PageClock}): the drawing time spent on it, and whether it has run out
     * and locked. Page state, not a mark: not in Undo, kept in the page's file only once its timer has started. A locked page is
     * never {@link #unmarked}, so it is never taken for a blank page. Changing it is a change to save.
     */
    private PageClock clock=PageClock.NONE;
    PageClock clock() { return clock; }
    /** Inkwash: Record and Replay ({@link StrokeLog}): the pen's strokes on this page, each tied to its Undo step. */
    private StrokeLog log=StrokeLog.NONE;
    /** The serial of the newest Undo step still done: 0 when none is (strokes read from a file are 0 too). */
    long top() { return undo.isEmpty()?0:undo.peekLast().serial; }
    /** The strokes a replay plays: those whose steps are done, since the last Clear page. */
    java.util.List<StrokeLog.Entry> replay() { return log.upTo(top()).strokes(); }
    /** The log as read from a file: no revision. */
    void placeLog(StrokeLog read) { log=read; }
    /**
     * The pen stroke just finished and recorded by {@code recorder} (its step is the newest). No new revision: the
     * stroke's own step made one, and what its lift left wet (a Slow wash settling, a halo, a floating bath) holds that
     * one, so a revision here would drop it as if the page had changed.
     */
    void logStroke(StrokeLog.Recorder recorder) { log=log.added(recorder.done(top()),Long.MAX_VALUE); }
    /** A Clear page just made its step: strokes before it are not replayed (until Undo takes it back). */
    private void logClear() { log=log.added(StrokeLog.Entry.marker(top()),Long.MAX_VALUE); }
    /** Whether the page is locked ({@link PageClock#locks}): no marks, no Undo, no Redo. */
    boolean locked() { return clock.locks(timerLocks); }
    /** The notebook's Lock Pages ({@link NotebookOptions#lockPages}), set by the notebook on the page drawn on ({@link DrawingBook#current}); off on its own. */
    private boolean timerLocks;
    void timerLocks(boolean on) { timerLocks=on; }
    /** The page's clock as read from a file: no revision. */
    void placeClock(PageClock read) { clock=read; }
    /** Changes the clock (the timer started, time spent, the page locked) as a change to save, not an Undo step; false when it is the same. */
    boolean setClock(PageClock next) {
        if(next.equals(clock)) return false;
        clock=next; revision++; return true;
    }
    /**
     * Inkwash: the Daily Prompt card the page was given ({@link DailyPrompt#line}), or null: page state, not in Undo, kept in
     * the page's file. A page with one is never {@link #unmarked}, so it is never taken for a blank page.
     */
    private String prompt;
    String prompt() { return prompt; }
    /**
     * Inkwash: Show Kinds (the owner, 2026-10-28: "same method as the template view levels"; "just per page"): each kind's
     * step on this page's screen ({@link EraseKind#step}: two bits a kind, 0 all Full). A view only: never in a composite of
     * the page or its copies ({@link #visibleLayers}' layer steps alone read it). Page state, not in Undo, kept in the file.
     */
    private int kindsShown;
    int kindsShown() { return kindsShown; }
    /** Show Kinds' steps as read from a file: no revision. */
    void placeKindsShown(int read) { kindsShown=read&EraseKind.STEPS; }
    /** Show Kinds' steps made {@code shown}, a change to save; false when they are that already. */
    boolean setKindsShown(int shown) {
        shown&=EraseKind.STEPS;
        if(shown==kindsShown) return false;
        kindsShown=shown; revision++; return true;
    }
    /** The page's prompt as read from a file: no revision. */
    void placePrompt(String read) { prompt=read; }
    /** Gives the page {@code card} (a card's line) as a change to save; false when it is that already. */
    boolean setPrompt(String card) {
        if(java.util.Objects.equals(card,prompt)) return false;
        prompt=card; revision++; promptTop=card==null?-1:top(); return true;
    }
    /** The Undo step done when the card was given ({@link #top}), kept in memory only; -1: not known (read from a file). */
    private long promptTop=-1;
    /**
     * Inkwash: the card goes when nothing was done with it (the owner, 2026-10-29: "if it's closed without doing it, remove it
     * from the page"): nothing on the page but the card, or, on a page drawn on before, no step made (or all undone) since it
     * was given. A change to save, not Undo; false when it stays.
     */
    boolean dropUnusedPrompt() {
        if(prompt==null||top()!=promptTop&&!unmarkedButPrompt()) return false;
        return setPrompt(null);
    }
    /**
     * Inkwash: the page's Timed Sections ({@link TimedSections}): page state, not in Undo, kept in the page's file once
     * started. A page with them started is never {@link #unmarked}.
     */
    private TimedSections sections=TimedSections.NONE;
    TimedSections sections() { return sections; }
    /** The page's sections as read from a file: no revision. */
    void placeSections(TimedSections read) { sections=read==null?TimedSections.NONE:read; }
    /** The sections made {@code next} (started, skipped), a change to save; false when they are that already. */
    boolean setSections(TimedSections next) {
        if(next.equals(sections)) return false;
        sections=next; revision++; return true;
    }
    /**
     * Inkwash: boxes {x0, y0, x1, y1} (exclusive) no gesture may change (a locked section's, {@link TimedSections}): a
     * stroke may cross one, and at its finish the box is put back as the stroke found it. Not kept: the screen sets it.
     */
    private int[][] fence=new int[0][];
    void fence(int[][] boxes) { fence=boxes==null?new int[0][]:boxes; }
    /** Whether page pixel {@code x, y} is fenced off. */
    boolean fenced(float x,float y) {
        for(int[] b:fence) if(x>=b[0]&&y>=b[1]&&x<b[2]&&y<b[3]) return true;
        return false;
    }
    /** Whether no layer holds any coverage: no ink, whatever else the page holds. */
    boolean inkless() {
        for(Layer layer:layers) if(layer.marked()) return false;
        return true;
    }
    /** Whether nothing is on the page: no layer holds any coverage, no masking fluid, no magnets, no mirrors, no cover design rubbed off (the paper's shade is none), no prompt, no sections started, and it isn't locked. */
    boolean unmarked() { return prompt==null&&unmarkedButPrompt(); }
    /** As {@link #unmarked}, a Daily Prompt card aside. */
    boolean unmarkedButPrompt() {
        if(wear!=null||clock.locked||sections.started()||!magnets.isEmpty()||!mirrors.isEmpty()||hasMask()) return false;
        return inkless();
    }
    /**
     * Inkwash: a notebook cover's paper (its marble, label and picture, {@code NotebookCovers}), a tone a pixel row by row,
     * in place of {@link #paperTone} under every layer; null on any other page. Like the shade it is paper, not a mark: the
     * eraser rubs back to it and Clear page leaves it. It isn't kept in the page's file (the notebook makes it when the
     * cover is shown) nor in Undo. Never changed once placed: a new one replaces it whole, so snapshots may share it.
     * Where the Everything eraser went, it rubs the design off too ({@link #wear}): this is then the paper as made with
     * that part of it as made without the design.
     */
    private byte[] paperPixels;
    byte[] paperPixels() { return paperPixels; }
    /** Inkwash: a cover's paper as made, its design whole; how to make it with the design rubbed off (null: no design to rub), and that, once made. */
    private byte[] madePaper, rubbed;
    private java.util.function.Supplier<byte[]> rubbing;
    /** Inkwash: where the eraser rubbed a cover's design off ({@link CoverWear}), or null: kept in the page's file and in Undo. */
    private CoverWear wear;
    CoverWear wear() { return wear; }
    /** Places a cover's paper pixels ({@link #paperPixels}; null: none): no Undo step, the whole page to show again. */
    void placePaper(byte[] pixels) { placePaper(pixels,null); }
    /** As above, a cover's: {@code bare} makes it with its design rubbed off (white where the pattern was), when first needed. */
    void placePaper(byte[] pixels,java.util.function.Supplier<byte[]> bare) {
        if(pixels!=null&&pixels.length!=width*height) throw new IllegalArgumentException("Invalid paper");
        madePaper=pixels; rubbing=bare; rubbed=null; repaper(); markAllDirty();
    }
    /** The design rubbed off as read from a file: no Undo step. */
    void placeWear(CoverWear read) {
        if(read!=null&&(read.width!=width||read.height!=height)) throw new IllegalArgumentException("Invalid cover wear");
        wear=read; repaper();
    }
    /** Whether the eraser can rub this page's paper: a cover's, with a design. */
    boolean rubs() { return madePaper!=null&&rubbing!=null; }
    /** The cover's paper with its design rubbed off, made the first time it is asked for; null on any other page. */
    private byte[] bare() {
        if(rubbed==null&&rubs()) { byte[] made=rubbing.get(); rubbed=made!=null&&made.length==width*height?made:madePaper; }
        return rubbed;
    }
    private void repaper() { byte[] bare=wear==null?null:bare(); paperPixels=bare==null?madePaper:CoverWear.apply(madePaper,bare,wear); }
    /**
     * Inkwash: the cover's design rubbed off where {@code next} says (the eraser's stroke, {@link CoverWear#with}), as an
     * Undo step joined to the one before when {@code link} (the stroke's own); its pixels in {@code bounds} to show again.
     * False, changing nothing, when it is the same or the page has no design to rub.
     */
    boolean rubDesign(CoverWear next,int[] bounds,boolean link) {
        idle();
        if(next==wear||!rubs()) return false;
        StackState old=new StackState(this); wear=next; repaper();
        revision++; addEdit(new Edit(old,new StackState(this)));
        if(link&&undo.size()>1) undo.peekLast().linked=true;
        markDirty(bounds[0],bounds[1],bounds[2],bounds[3]);
        return true;
    }
    /**
     * Inkwash: a notebook template's guides ({@code NotebookTemplate}), a tone a pixel (255: none), printed on the paper
     * under every layer on the pages of a notebook that has a template. Like a cover's paper they are paper, not marks:
     * the eraser rubs back to them, Clear page leaves them, a page with only guides is {@link #unmarked}. Not kept in the
     * page's file (the notebook keeps its template), nor in Undo. Never changed once placed, so pages share them. The
     * page shows each pixel as the darker of the guide and the paper's shade ({@link #guided}).
     */
    private byte[] guides;
    byte[] guides() { return guides; }
    /** Places the template's guides (null: none): no Undo step, the whole page to show again. */
    void placeGuides(byte[] tones) {
        if(tones!=null&&tones.length!=width*height) throw new IllegalArgumentException("Invalid guides");
        if(tones==guides) return;
        guides=tones; markAllDirty();
    }
    /** The paper pixels under the layers: a cover's, or the guides on the paper's shade; null for the paper's tone alone. */
    private byte[] pixels() {
        if(paperPixels!=null||guides==null) return paperPixels;
        if(guidedNow==null||guidedOf!=guides||guidedTone!=paper) { guidedNow=guided(guides,paper); guidedOf=guides; guidedTone=paper; }
        return guidedNow;
    }
    /** What {@link #pixels} last made, and the guides and tone it was made from (checked each time: the shade may change under Undo). */
    private byte[] guidedNow,guidedOf; private int guidedTone=-1;
    /** The last guided paper made: for the guides and shade it was made from, never changed. */
    private static byte[] cacheGuides,cachePaper; private static int cacheTone=-1;
    /** {@code guides} on paper of tone {@code paper}: each pixel the darker of the two (null for no guides). Kept for the next call; never changed. */
    static synchronized byte[] guided(byte[] guides,int paper) {
        if(guides==null) return null;
        if(cacheGuides!=guides||cacheTone!=paper||cachePaper==null) {
            byte[] out=new byte[guides.length];
            for(int i=0;i<out.length;i++) out[i]=(byte)Math.min(guides[i]&255,paper);
            cacheGuides=guides; cacheTone=paper; cachePaper=out;
        }
        return cachePaper;
    }
    /** The paper's tone at pixel {@code i}. */
    private int paperAt(int i) { byte[] under=pixels(); return under==null?paper:under[i]&255; }
    /** {@link #baseComposite}'s paper meaning the page's own ({@link #paperAt}): its shade or a cover's pixels. */
    static final int PAGE_PAPER=-1;
    /** All 255, {@code n} long: a cover's paper as a layer of full coverage ({@link #visibleLayers}); one kept, never changed. */
    private static byte[] opaque;
    static synchronized byte[] opaque(int n) {
        if(opaque==null||opaque.length!=n) { opaque=new byte[n]; Arrays.fill(opaque,(byte)255); }
        return opaque;
    }

    /**
     * Inkwash: the page's fridge magnets ({@link Magnets}), word tiles lying over the artwork, never in it: no layer,
     * gesture or composite reads them. Only the canvas, the files and the PNG copy do.
     */
    private Magnets magnets=Magnets.NONE;
    /** Where Undo, Redo or {@link #setMagnets} changed the magnets, for the canvas to draw again ({@link #takeMagnetAreas}); apart from {@link #dirty}, so a magnet moved far redraws two small places, not the page between. */
    private final ArrayList<int[]> magnetAreas=new ArrayList<>();
    Magnets magnets() { return magnets; }
    /** The page's magnets as read from a file: no Undo step. */
    void placeMagnets(Magnets read) { magnets=read; }
    /** Changes the magnets as one undoable step; false, changing nothing, when they are the same. */
    boolean setMagnets(Magnets next) {
        idle(); if(next.equals(magnets)) return false;
        Edit edit=new Edit(magnets,next);
        magnetAreas.addAll(Magnets.changed(magnets,next)); magnets=next; revision++;
        addEdit(edit); return true;
    }
    /**
     * Inkwash: the page's mirrors ({@link Mirrors}), lines over the artwork, never in it: no layer or gesture reads them.
     * The canvas draws their guides and Real Mirror's reflection; the files keep them; the copies a page's pictures and
     * PNG copies make show the reflection ({@link #exportPixels}, {@code PagePicture.Composite}).
     */
    private Mirrors mirrors=Mirrors.NONE;
    Mirrors mirrors() { return mirrors; }
    /** The page's mirrors as read from a file: no Undo step. */
    void placeMirrors(Mirrors read) { mirrors=read; }
    /** The mirrors on or off (the Mirrors row): no Undo step, the page to show again and to save. */
    void showMirrors(boolean on) {
        idle(); if(mirrors.on==on) return;
        mirrors=mirrors.turned(on); revision++; markAllDirty();
    }
    /** Their dashed guides shown or not (the Show Guides row), the mirrors acting all the same: no Undo step, the page to show again and to save. */
    void showGuides(boolean shown) {
        idle(); if(mirrors.guides==shown) return;
        mirrors=mirrors.guided(shown); revision++; markAllDirty();
    }
    /** Changes the mirrors as one undoable step (placed, a kaleidoscope, a style, a side kept, cleared); false, changing nothing, when they are the same. */
    boolean setMirrors(Mirrors next) {
        idle(); if(next.equals(mirrors)) return false;
        StackState old=new StackState(this); mirrors=next; stackEdit(old); return true;
    }
    /**
     * Keep Reflection: Real Mirror's view laid into the page as it shows ({@link Mirrors.View}), every layer and the
     * masking fluid, each covered pixel taking the kept pixel it shows (paper where that is off the page), and the
     * mirrors cleared, as one Undo step that holds the old layers by reference, as Clear page does. False with nothing reflected.
     */
    boolean keepReflection() {
        idle(); Mirrors.View view=mirrors.view(); if(view==null) return false;
        StackState old=new StackState(this);
        int[] source=new int[width];
        ArrayList<Layer> next=new ArrayList<>();
        for(Layer layer:layers) next.add(layer.each(l->reflected(l,view,source)));
        Layer fluid=mask==null?null:reflected(mask,view,source);
        layers.clear(); layers.addAll(next); mask=fluid; mirrors=mirrors.cleared();
        selectLayer(active); stackEdit(old); return true;
    }
    /** {@code layer} as {@code view} shows it: each pixel its source's, paper where that is off the page. */
    private Layer reflected(Layer layer,Mirrors.View view,int[] source) {
        byte[] tones=new byte[width*height], alpha=new byte[width*height], kept=layer.share==null?null:new byte[width*height], printed=layer.print==null?null:new byte[width*height], kinded=layer.kind==null?null:new byte[width*height];
        for(int y=0;y<height;y++) {
            view.row(y,0,width,width,height,source);
            for(int x=0,i=y*width;x<width;x++,i++) {
                int s=source[x];
                if(s<0) { tones[i]=(byte)255; if(kept!=null) kept[i]=(byte)255; if(printed!=null) printed[i]=(byte)255; continue; }
                tones[i]=layer.tones[s]; alpha[i]=layer.alpha[s];
                if(kept!=null) kept[i]=layer.share[s];
                if(printed!=null) printed[i]=layer.print[s];
                if(kinded!=null) kinded[i]=layer.kind[s];
            }
        }
        Layer out=new Layer(layer.name,layer.visible,tones,alpha); out.opacity=layer.opacity; out.share=kept; out.print=printed; out.kind=kinded; out.kinds=layer.kinds; out.kindBase=layer.kindBase; return out;
    }
    /**
     * What the gesture in progress has changed, as the page shows it: the composite tone of each pixel of the {@code w}
     * by {@code h} block at {@code x, y} where the gesture's layer differs from how the gesture found it, −1 elsewhere
     * (and off the page), into {@code out} row by row. Copy Strokes' live copies show these ({@code MirrorShow}).
     */
    void gestureTones(int x,int y,int w,int h,int[] out) {
        Arrays.fill(out,0,w*h,-1);
        if(!editing) return;
        int x0=Math.max(0,x), x1=Math.min(width,x+w), y0=Math.max(0,y), y1=Math.min(height,y+h);
        for(int py=y0;py<y1;py++) for(int px=x0;px<x1;) {
            int column=px/TILE, end=Math.min(x1,(column+1)*TILE), key=(py/TILE)*columns()+column;
            byte[] tile=captured[key]?captureOf(key):null;
            if(tile==null) { px=end; continue; }
            int tileWidth=Math.min(TILE,width-column*TILE), half=tile.length/2;
            for(int t=(py%TILE)*tileWidth+px%TILE, i=py*width+px; px<end; px++, t++, i++)
                if(tile[t]!=tones[i]||tile[half+t]!=alpha[i]) out[(py-y)*w+px-x]=compositeTone(px,py);
        }
    }
    /** The last Undo step, or null: a mark for {@link #linkSince}. */
    Object lastStep() { return undo.peekLast(); }
    /**
     * Inkwash: every Undo step after {@code mark} ({@link #lastStep} before them; null: from the first) undone and redone
     * together as one, as {@link #finishWithPrevious} links two: a stroke and its mirrored copies ({@code MirrorStroke}).
     */
    void linkSince(Object mark) {
        ArrayList<Edit> after=new ArrayList<>();
        for(java.util.Iterator<Edit> i=undo.descendingIterator();i.hasNext();) { Edit e=i.next(); if(e==mark) break; after.add(e); }
        for(int k=0;k<after.size()-1;k++) after.get(k).linked=true;
    }
    /**
     * Inkwash: a stroke's change to its layer as each pixel's darkening ({@link #darkeningSince}), to lay again elsewhere
     * ({@link #layAgain}): the factor its tone over white was multiplied by, 1 where it is as it was, NaN where it found
     * black (nothing tells). Immutable.
     */
    static final class Darkening {
        /** Its blocks' side, pixels: a copy looks only where a block of the change lands. */
        static final int BLOCK = 16;
        final Layer layer;
        /** The gesture's permanent share ({@link #begin(double)}), which a copy keeps. */
        final int permanent;
        /** By tile key, each pixel's factor, {@link #TILE} to a row; null for a tile left as it was. */
        final float[][] factors;
        /** Which {@link #BLOCK}-pixel blocks of the page it changed, {@link #across} to a row. */
        final boolean[] blocks;
        final int across;
        /** The pixels it changed: {x0, y0, x1, y1}, exclusive. */
        final int[] box;
        private Darkening(Layer layer,int permanent,float[][] factors,boolean[] blocks,int across,int[] box) { this.layer=layer; this.permanent=permanent; this.factors=factors; this.blocks=blocks; this.across=across; this.box=box; }
    }
    /**
     * Inkwash: the one step since {@code mark} ({@link #lastStep}) as a {@link Darkening}, when it is one gesture's change
     * to the selected layer, linked to nothing, on a page without masking fluid (which would have shielded some of it);
     * null for anything else, or nothing changed. Copy Strokes' copies ({@code MirrorStroke}).
     */
    Darkening darkeningSince(Object mark) {
        idle();
        Edit e=undo.peekLast();
        if(e==null||e==mark||e.tiles==null||e.linked||ownerOf(e.layer)!=active||mask!=null) return null;
        java.util.Iterator<Edit> i=undo.descendingIterator(); i.next();
        if((i.hasNext()?i.next():null)!=mark) return null;
        int columns=columns(), block=Darkening.BLOCK, across=(width+block-1)/block;
        float[][] factors=new float[captured.length][];
        boolean[] blocks=new boolean[across*((height+block-1)/block)];
        int x0=width,y0=height,x1=0,y1=0;
        for(TileEdit tile:e.tiles) {
            int left=tile.key%columns*TILE, top=tile.key/columns*TILE, tileWidth=Math.min(TILE,width-left), half=tile.before.length/2, rows=half/tileWidth;
            byte[] was=tile.before, now=tile.after;
            float[] f=null;
            for(int row=0;row<rows;row++) for(int col=0,t=row*tileWidth;col<tileWidth;col++,t++) {
                if(was[t]==now[t]&&was[half+t]==now[half+t]) continue;
                int u=onWhite(was[t]&255,was[half+t]&255), v=onWhite(now[t]&255,now[half+t]&255);
                if(u==v) continue;
                if(f==null) { f=new float[TILE*TILE]; Arrays.fill(f,1); }
                f[row*TILE+col]=u==0?Float.NaN:v/(float)u;
                int x=left+col, y=top+row;
                blocks[(y/block)*across+x/block]=true;
                if(x<x0) x0=x; if(y<y0) y0=y; if(x>=x1) x1=x+1; if(y>=y1) y1=y+1;
            }
            factors[tile.key]=f;
        }
        return x0<x1?new Darkening(e.layer,permanent,factors,blocks,across,new int[]{x0,y0,x1,y1}):null;
    }
    /**
     * Inkwash: {@code change} laid again on its layer, carried by the affine map {@code t} ({a, b, c, d, e, f}: {@code x' =
     * a x + b y + e}, {@code y' = c x + d y + f}, page px; {@link Mirrors.Group}), as a gesture of its own: each pixel it
     * lands on darkened by the factor its source pixel was (read between the nearest where a turned copy's source falls
     * between pixels, {@link #factorAt}, so its edges stay as sharp and as soft as the stroke's), from the tone it finds
     * there, with the gesture's permanent share, through {@link #setTones} as the stroke's compose wrote it. Where it
     * finds what the stroke found, it writes what the stroke wrote; where it finds other marks, it darkens them as the
     * stroke would have (exactly, for a mark that only multiplies what it finds). In bands on several cores ({@link
     * Cores}). False, as no step, when it lands on nothing or changes nothing.
     */
    boolean layAgain(Darkening change,double[] t) {
        idle();
        if(ownerOf(change.layer)!=active||mask!=null) return false;
        int[] b=change.box;
        double[] box=Mirrors.Group.box(t,b[0],b[1],b[2],b[3]);
        int x0=Math.max(0,(int)Math.floor(box[0])-1), y0=Math.max(0,(int)Math.floor(box[1])-1), x1=Math.min(width,(int)Math.ceil(box[2])+1), y1=Math.min(height,(int)Math.ceil(box[3])+1);
        if(x0>=x1||y0>=y1) return false;
        double[] inv=Mirrors.Group.inverse(t);
        // Which blocks here could land anything: those whose source, a pixel more each way, holds a changed block.
        int block=Darkening.BLOCK, across=change.across, down=change.blocks.length/across, bx0=x0/block, bx1=(x1-1)/block, by0=y0/block, by1=(y1-1)/block, bw=bx1-bx0+1;
        boolean[] lands=new boolean[bw*(by1-by0+1)];
        boolean any=false;
        for(int by=by0;by<=by1;by++) for(int bx=bx0;bx<=bx1;bx++) {
            double[] s=Mirrors.Group.box(inv,bx*block,by*block,(bx+1)*block,(by+1)*block);
            int a0=Math.max(0,(int)Math.floor(s[0])-1)/block, a1=Math.min(across-1,((int)Math.ceil(s[2])+1)/block), c0=Math.max(0,(int)Math.floor(s[1])-1)/block, c1=Math.min(down-1,((int)Math.ceil(s[3])+1)/block);
            boolean hit=false;
            for(int sy=c0;sy<=c1&&!hit;sy++) for(int sx=a0;sx<=a1&&!hit;sx++) hit=change.blocks[sy*across+sx];
            lands[(by-by0)*bw+bx-bx0]=hit; any|=hit;
        }
        if(!any) return false;
        // On the stroke's own plane (or base).
        begin(change.layer,0,1-change.permanent/255.0);
        int left=x0, right=x1;
        Cores.Rows rows=(top,bottom,worker)->{
            int[] grays=new int[right-left], owns=change.permanent>0?new int[right-left]:null;
            for(int y=top;y<=bottom;y++) {
                double cy=y+.5;
                int row=(y/block-by0)*bw;
                for(int bx=bx0;bx<=bx1;) {
                    if(!lands[row+bx-bx0]) { bx++; continue; }
                    // A run of blocks that land, written at once.
                    int first=bx;
                    while(bx<=bx1&&lands[row+bx-bx0]) bx++;
                    int from=Math.max(left,first*block), to=Math.min(right,bx*block);
                    boolean writes=false;
                    for(int x=from,o=0;x<to;x++,o++) {
                        double cx=x+.5;
                        float f=factorAt(change,inv[0]*cx+inv[1]*cy+inv[4]-.5,inv[2]*cx+inv[3]*cy+inv[5]-.5);
                        int gray=-1;
                        if(f==f&&f!=1) {
                            int i=y*width+x, u=onWhite(tones[i]&255,alpha[i]&255), g=Math.max(0,Math.min(255,Math.round(u*f)));
                            if(g!=u) gray=g;
                        }
                        grays[o]=gray;
                        if(owns!=null) owns[o]=gray<0?-1:255-Math.max(0,Math.min(255,Math.round(255*f)));
                        writes|=gray>=0;
                    }
                    if(writes) tones(from,y,to-from,1,grays,null,false,owns);
                }
            }
        };
        long area=(long)(x1-x0)*(y1-y0);
        if(y0/TILE==(y1-1)/TILE||area<Cores.SHARE_PX) rows.run(y0,y1-1,0);
        else { openBands(); try { Cores.inBands(y0,y1-1,TILE,area,rows); } finally { closeBands(); } }
        return finish();
    }
    /**
     * {@code change}'s factor at source point {@code sx, sy} (pixel centres at whole numbers): at an edge Catmull-Rom
     * between the sixteen nearest pixels, kept within the four nearest so the edge stays as sharp as it was and never
     * rings, elsewhere between the four; leaving out those that tell nothing (NaN; NaN if none of the four tells); 1 off
     * its tiles and the page.
     */
    private float factorAt(Darkening change,double sx,double sy) {
        int ix=(int)Math.floor(sx), iy=(int)Math.floor(sy);
        double fx=sx-ix, fy=sy-iy;
        // A map that carries pixel centres onto pixel centres (a mirror on the grid) reads one pixel, exactly.
        if(fx<1e-6) fx=0; else if(fx>1-1e-6) { ix++; fx=0; }
        if(fy<1e-6) fy=0; else if(fy>1-1e-6) { iy++; fy=0; }
        if(fx==0&&fy==0) return factor(change,ix,iy);
        // The four nearest: where they agree (blank paper, a wash's middle) that is the factor.
        float a=factor(change,ix,iy), b=factor(change,ix+1,iy), c=factor(change,ix,iy+1), d=factor(change,ix+1,iy+1);
        if(a==b&&a==c&&a==d) return a;
        double lo=Double.POSITIVE_INFINITY, hi=Double.NEGATIVE_INFINITY;
        if(a==a) { lo=Math.min(lo,a); hi=Math.max(hi,a); }
        if(b==b) { lo=Math.min(lo,b); hi=Math.max(hi,b); }
        if(c==c) { lo=Math.min(lo,c); hi=Math.max(hi,c); }
        if(d==d) { lo=Math.min(lo,d); hi=Math.max(hi,d); }
        if(lo>hi) return Float.NaN;
        double sum=0, weight=0;
        // Where they hardly differ (a wash's grain) between the four is as good, and a quarter of the reading.
        if(hi-lo<EDGE) {
            if(a==a) { double w=(1-fx)*(1-fy); sum+=w*a; weight+=w; }
            if(b==b) { double w=fx*(1-fy); sum+=w*b; weight+=w; }
            if(c==c) { double w=(1-fx)*fy; sum+=w*c; weight+=w; }
            if(d==d) { double w=fx*fy; sum+=w*d; weight+=w; }
            return weight>0?(float)(sum/weight):(float)lo;
        }
        for(int j=0;j<4;j++) {
            double wy=cubic(j-1-fy);
            for(int k=0;k<4;k++) {
                float f=factor(change,ix-1+k,iy-1+j);
                if(f!=f) continue;
                double w=cubic(k-1-fx)*wy;
                sum+=w*f; weight+=w;
            }
        }
        return weight>0?(float)Math.max(lo,Math.min(hi,sum/weight)):(float)lo;
    }
    /** How far apart the four nearest factors must be for {@link #factorAt} to read an edge: a sixteenth of the way to black. */
    private static final double EDGE=1/16.0;
    /** Catmull-Rom's weight for a pixel {@code t} away (its spline's {@code a} = −½). */
    private static double cubic(double t) {
        t=Math.abs(t);
        return t<1?(1.5*t-2.5)*t*t+1:t<2?((-.5*t+2.5)*t-4)*t+2:0;
    }
    private float factor(Darkening change,int x,int y) {
        if(x<0||y<0||x>=width||y>=height) return 1;
        float[] f=change.factors[(y/TILE)*((width+TILE-1)/TILE)+x/TILE];
        return f==null?1:f[(y%TILE)*TILE+x%TILE];
    }
    /** Where the magnets changed since this was last asked, {left, top, right, bottom} each; forgotten once taken. */
    java.util.List<int[]> takeMagnetAreas() {
        if(magnetAreas.isEmpty()) return java.util.Collections.emptyList();
        ArrayList<int[]> areas=new ArrayList<>(magnetAreas); magnetAreas.clear(); return areas;
    }
    /**
     * The visible layers, bottom to top, into {@code tones}, {@code alpha} and {@code opacity} (each at least
     * {@link #STACK} + 1 long: every base and kind plane); returns how many. These are the page's own pixels, not copies: for drawing
     * them at once on the owner thread, where a snapshot would make the next edit copy whole layers. Inkwash: a cover's
     * {@linkplain #paperPixels paper pixels} come first, as a layer that covers the paper's tone everywhere.
     */
    int visibleLayers(byte[][] tones,byte[][] alpha,int[] opacity) { return visibleLayers(tones,alpha,opacity,null); }
    /**
     * As above ({@code tones} and the rest {@link #STACK} + 2 long), with tracing paper's {@code ghost} ({@code
     * GhostPlane}, null: none) right over the paper, as a black layer covering what the plane says: the screen's alone.
     */
    int visibleLayers(byte[][] tones,byte[][] alpha,int[] opacity,byte[] ghost) { return visibleLayers(tones,alpha,opacity,ghost,0); }
    /**
     * As above with tracing paper's layer steps ({@code TracingPaper#layerBits}, 0: all Solid): each pair's opacity times
     * its step's strength, but the pair drawn on's, which stays Solid; and, from bit {@link #KIND_STEPS} up, Show Kinds'
     * ({@link #kindsShown}): each kind plane's opacity times its kind's step's strength, as the guides fade
     * ({@code NotebookOptions#GUIDE_STRENGTH}), the base (marks of no plane) as it is. The screen's alone.
     */
    int visibleLayers(byte[][] tones,byte[][] alpha,int[] opacity,byte[] ghost,int layerSteps) { return visibleLayers(tones,alpha,opacity,ghost,layerSteps,null,null); }
    /**
     * As above ({@code tones} and the rest {@link #STACK} + 3 long) with the layers above this page in its stack
     * flattened ({@code overTones}, {@code overAlpha}; null: none) over its own: they cover it, as layers do.
     */
    int visibleLayers(byte[][] tones,byte[][] alpha,int[] opacity,byte[] ghost,int layerSteps,byte[] overTones,byte[] overAlpha) {
        int count=0, drawing=active/2;
        byte[] under=pixels();
        if(under!=null) { tones[count]=under; alpha[count]=opaque(under.length); opacity[count++]=100; }
        if(ghost!=null&&ghost.length==width*height) { tones[count]=GhostPlane.ink(ghost.length); alpha[count]=ghost; opacity[count++]=100; }
        // Inkwash: each layer's base and kind planes, in the page's order (stacked).
        for(int k=0,end=stackSize(layers.size());k<end;k++) {
            Layer entry=stacked(layers,k);
            if(entry==null) continue;
            int n=stackOwner(k), pair=n/2, shown=stackOpacity(layers.get(n),entry);
            if(entry!=layers.get(n)) shown=shown*NotebookOptions.GUIDE_STRENGTH[EraseKind.stepOf(layerSteps>>>KIND_STEPS,PLANE_KINDS[k%(2*(1+PLANES))/2-1])]/100;
            if(layers.get(n).visible) { tones[count]=entry.tones; alpha[count]=entry.alpha; opacity[count++]=pair==drawing?shown:shown*TracingPaper.layerStrength(layerSteps,pair)/100; }
        }
        if(overTones!=null&&overAlpha!=null&&overAlpha.length==width*height) { tones[count]=overTones; alpha[count]=overAlpha; opacity[count++]=100; }
        return count;
    }
    /**
     * Inkwash: the tone the screen shows at {@code x, y} ({@link #visibleLayers}' layers, with the same planes, laid on the
     * paper), the template's guides left out: the Dropper's (the owner, 2026-10-24: "a dropper tool so people can quickly
     * match color"), every layer of the stack, a cover's design too.
     */
    int shownTone(int x,int y,byte[] ghost,int layerSteps,byte[] overTones,byte[] overAlpha) {
        byte[][] tones=new byte[STACK+3][],alpha=new byte[STACK+3][]; int[] opacity=new int[STACK+3];
        int count=visibleLayers(tones,alpha,opacity,ghost,layerSteps,overTones,overAlpha), i=y*width+x, result=paper;
        // The guided paper is the first layer when there are guides on plain paper: left out, the paper's tone under it.
        for(int n=paperPixels==null&&guides!=null?1:0;n<count;n++) {
            int a=((alpha[n][i]&255)*opacity[n]+50)/100;
            result=((tones[n][i]&255)*a+result*(255-a)+127)/255;
        }
        return result;
    }
    /** Whether the page has a masking-fluid layer, even an empty one: unlike {@link #hasMask}, without reading its pixels. */
    boolean hasMaskLayer() { return mask!=null; }
    int layerCount() { return layers.size(); }
    int activeLayer() { return active; }
    String layerName(int index) { return layers.get(index).name; }
    boolean layerVisible(int index) { return layers.get(index).visible; }
    int layerOpacity(int index) { return layers.get(index).opacity; }
    void setLayerOpacity(int index,int value) {
        idle(); if(value<0||value>100) throw new IllegalArgumentException("Invalid layer opacity");
        if(layers.get(index).opacity==value)return;
        StackState old=new StackState(this); layers.get(index).opacity=value; stackEdit(old);
    }
    private void writable() {
        // Bands: done once when they opened.
        if(bands) return;
        revision++;
        own(bound);
    }
    /** {@code layer}'s arrays its own to write, copied if a snapshot holds them (bound again if it is the bound layer). */
    private void own(Layer layer) {
        // Inkwash: a snapshot's thread may release it meanwhile (Layer.release).
        if(layer.shared) synchronized(layer) {
            if(layer.shared) {
                layer.tones=layer.tones.clone(); layer.alpha=layer.alpha.clone(); if(layer.share!=null) layer.share=layer.share.clone();
                if(layer.print!=null) layer.print=layer.print.clone();
                if(layer.kind!=null) layer.kind=layer.kind.clone();
                layer.shared=false; layer.holds=0;
                if(layer==bound) bind(layer);
            }
        }
    }
    private void bind(Layer layer) { bound=layer; tones=layer.tones; alpha=layer.alpha; share=layer.share; print=layer.print; kind=layer.kind; }
    private void idle() { if(editing) throw new IllegalStateException("Finish the current gesture first"); }
    void selectLayer(int index) {
        idle(); Layer layer=layers.get(index); if(active!=index)revision++;
        active=index; selected=layer; bind(layer);
    }
    /**
     * Inkwash: what reads between gestures ({@link #tone}, {@link #opacity}, {@link #strokeBaseTones} and the like) read: the
     * active layer's base when it was selected, then the plane (or base) its last gesture drew on.
     */
    private Layer selected;
    /** Inkwash: reads between gestures read the active layer's plane for {@code kinds} ({@link #planeOf}; its base for none), made if missing: wet paint's, which reads what it writes. */
    void selectKind(int kinds) {
        idle(); int p=planeOf(kinds);
        selected=p<0?layers.get(active):plane(layers.get(active),p); bind(selected);
    }
    /** Inkwash: the kinds of what reads between gestures read ({@link #selectKind}); 0 for the base. */
    int selectedKinds() { int a=address(layers,selected); return a<0?0:kindsOf((a>>4)-1); }
    /** Gestures from now on lay or, erasing, rub off masking fluid instead of painting the active layer. */
    void paintMaskingFluid(boolean value) { idle(); maskTarget=value; }
    /** Whether any masking fluid is on the page. */
    boolean hasMask() {
        if(mask==null) return false;
        for(byte a:mask.alpha) if(a!=0) return true;
        return false;
    }
    /**
     * Inkwash: the gesture's paint takes on wax ({@link #takesOnWax}): only the fluid the eraser can lift shields it, so
     * a crayon builds up over its own wax. Off at every {@link #begin}.
     */
    private boolean waxTakes;
    /** Inkwash: this gesture's paint takes on wax, the masking fluid's share the eraser can't lift (a crayon's, a wax resist). */
    void takesOnWax() { waxTakes=true; }
    /**
     * How strongly masking fluid shields this pixel, 0 to 255: the fluid's darkness (with {@link #waxTakes}, or for Scribulor's
     * eraser, {@link #eraseKinds}, its liftable part's: wax keeps paint out, not the eraser off the paint under it, the owner 2026-10-25).
     */
    private int shield(int i) { return mask==null?0:waxTakes||erasing!=0?liftable(i):255-onWhite(mask.tones[i]&255,mask.alpha[i]&255); }
    /** {@link #shield} at page pixel {@code x, y}: how much of a change there masking fluid keeps out, 0 to 255. */
    int maskShield(int x,int y) { return shield(y*width+x); }
    /**
     * How strongly the part of the masking fluid the eraser can lift shields pixel {@code i}, 0 to 255: all of it but
     * what its {@link Layer#share} keeps (a wax resist, which stays).
     */
    private int liftable(int i) {
        if(mask==null) return 0;
        int tone=mask.tones[i]&255, a=mask.alpha[i]&255, dark=255-onWhite(tone,a);
        return mask.share==null||dark==0?dark:dark-(255-onWhite(tone,kept(a,mask.share[i]&255)));
    }
    /** {@link #liftable} at page pixel {@code x, y}. */
    int maskLiftable(int x,int y) { return liftable(y*width+x); }
    /** Peel all the masking fluid off the page as one undoable step; false when there is none. */
    boolean peelMask() {
        idle(); if(!hasMask()) { mask=null; return false; }
        StackState old=new StackState(this); mask=null; stackEdit(old); return true;
    }
    /** Spacing of {@link #maskHatch}'s diagonal lines, and how many pixels across each is, thick enough to read as lines at the panel's resolution. */
    static final int HATCH=12, HATCH_LINE=4;
    /**
     * The hatching's dashes along its lines, in x - y: {@code DASH_ON} of every {@code DASH}, so the masking fluid never
     * reads as a 45° Lines screentone (the owner, 2026-10-22: "1 dashed").
     */
    static final int DASH=24, DASH_ON=16, DASH_SHIFT=12;
    /**
     * Dashed diagonal hatching that shows the masking fluid over the painting: black over light
     * paint and white over dark, or -1 where the pixel shows the painting. Only fluid the eraser can lift is hatched:
     * a wax resist ({@link Layer#share}) shows only by what it keeps out.
     */
    int maskHatch(int x,int y) {
        if(mask==null||(x+y)%HATCH>=HATCH_LINE||Math.floorMod(x-y+DASH_SHIFT,DASH)>=DASH_ON||liftable(y*width+x)<32) return -1;
        return compositeTone(x,y)<128?255:0;
    }
    void addLayer() { addLayer(paper(width,height),new byte[width*height]); }
    /** Adds a layer holding these raw tones and coverage above the active one, and selects it. */
    void addLayer(byte[] layerTones,byte[] layerAlpha) {
        idle(); if(layers.size()>=MAX_LAYERS) throw new IllegalStateException("Each page can have up to "+MAX_LAYERS+" layers.");
        if(layerTones.length!=width*height||layerAlpha.length!=width*height) throw new IllegalArgumentException("Invalid layer pixels");
        StackState old=new StackState(this);
        int number=1;
        while(hasName("Layer "+number)) number++;
        layers.add(active+1,new Layer("Layer "+number,true,layerTones,layerAlpha));
        selectLayer(active+1); stackEdit(old);
    }
    /** Copies the active layer's raw tones and coverage in this rectangle, which must lie on the page. */
    void copyLayer(int x,int y,int w,int h,byte[] layerTones,byte[] layerAlpha) {
        Layer layer=layers.get(active);
        for(int row=0;row<h;row++) {
            System.arraycopy(layer.tones,(y+row)*width+x,layerTones,row*w,w);
            System.arraycopy(layer.alpha,(y+row)*width+x,layerAlpha,row*w,w);
        }
    }
    /**
     * Inkwash: the Lasso's lift ({@link LassoPlanes}): layer {@code index}'s (-1: the masking fluid; blank where the page
     * has none) w × h block at x, y, which must lie on the page, every plane row by row: tones, coverage, the share the
     * eraser takes (255 where the layer has none), print tone (255 where none) and kinds as {@link #layPixel} takes them
     * ({@link Layer#kindsAt} with {@link Layer#permanentAt} shifted by {@link #PERMANENT}; 0 under no coverage). Inkwash:
     * its kind plane {@code plane} ({@link #PLANES}; -1: its base; blank where the layer has none).
     */
    void copyPlanes(int index,int plane,int x,int y,int w,int h,byte[] t,byte[] a,byte[] s,byte[] p,byte[] k) {
        Layer layer=index<0?mask:plane<0?layers.get(index):layers.get(index).plane(plane);
        for(int row=0;row<h;row++) for(int col=0,at=row*w,i=(y+row)*width+x;col<w;col++,at++,i++) {
            int c=layer==null?0:layer.alpha[i]&255;
            t[at]=layer==null?(byte)255:layer.tones[i]; a[at]=(byte)c;
            s[at]=layer==null||layer.share==null?(byte)255:layer.share[i];
            p[at]=layer==null||layer.print==null?(byte)255:layer.print[i];
            k[at]=(byte)(c==0?0:layer.kindsAt(i)|layer.permanentAt(i)<<PERMANENT);
        }
    }
    /**
     * Inkwash: before the Lasso's piece is laid in this gesture ({@link #layPixel}): the layer notes the {@code kinds} it
     * brings and gets its {@link Layer#kind} plane, unless every pixel laid holds {@code uniform} (-1: they differ) and the
     * layer reads so without one.
     */
    void layKinds(int kinds,int uniform) {
        if(!editing) throw new IllegalStateException("No active gesture");
        int was=bound.kinds!=0?bound.kinds:bound.covers()?KIND_ALL:0, k=uniform&KIND_ALL;
        boolean same=uniform>=0&&uniform==(k|k<<PERMANENT)&&(was==0||was==k);
        if(kind==null&&!same&&kinds!=0) { writable(); bound.kindBase=(byte)was; bound.kind=new byte[width*height]; kind=bound.kind; }
        bound.kinds=(byte)(was|kinds&KIND_ALL);
    }
    /**
     * Inkwash: the Lasso's piece ({@link LassoPlanes}) laid on pixel x, y in a gesture, as a sheet over what the pixel
     * holds ({@link #overTone}), its share, print tone and kinds ({@link #copyPlanes}) with it: the share the eraser
     * takes weighed by each sheet's part of the coverage, the darker print tone, both pixels' kinds. No masking fluid
     * shields it: it is the paint moved, not painted.
     */
    void layPixel(int x,int y,int gray,int coverage,int kept,int printed,int kinds) {
        if(!editing) throw new IllegalStateException("No active gesture");
        if(x<0||y<0||x>=width||y>=height||coverage==0) return;
        int i=y*width+x, under=alpha[i]&255, laid=sheet(gray,coverage,tones[i]&255,under), a=laid&255, rest=under*(255-coverage);
        int s=share==null?255:share[i]&255, p=print==null?255:print[i]&255;
        s=under==0?kept:Math.min(255,(kept*coverage*255+s*rest+a*255/2)/(a*255));
        p=under==0?printed:Math.min(printed,p);
        if(under!=0&&kind!=null) kinds|=bound.kindsAt(i)|bound.permanentAt(i)<<PERMANENT;
        lay(x,y,laid>>>8,a,s,p,kinds);
    }
    /** Inkwash: the Lasso lifting pixel x, y off the layer in a gesture ({@link LassoPlanes}): bare, every plane. */
    void liftPixel(int x,int y) {
        if(!editing) throw new IllegalStateException("No active gesture");
        if(x>=0&&y>=0&&x<width&&y<height) lay(x,y,255,0,255,255,0);
    }
    /** Writes every plane of a pixel, capturing its tile for Undo first; a share or print tone the layer lacks gives it that plane. */
    private void lay(int x,int y,int gray,int coverage,int kept,int printed,int kinds) {
        int i=y*width+x;
        if((tones[i]&255)==gray&&(alpha[i]&255)==coverage&&(share==null?kept==255:(share[i]&255)==kept)
                &&(print==null?printed==255:(print[i]&255)==printed)&&(kind==null||kind[i]==(byte)kinds)) return;
        capture((y/TILE)*columns()+x/TILE);
        writable();
        if(share==null&&kept!=255) { bound.share=paper(width,height); share=bound.share; }
        if(print==null&&printed!=255) { bound.print=paper(width,height); print=bound.print; }
        tones[i]=(byte)gray; alpha[i]=(byte)coverage;
        if(share!=null) share[i]=(byte)kept;
        if(print!=null) print[i]=(byte)printed;
        if(kind!=null) kind[i]=(byte)kinds;
        markDirty(x,y,x+1,y+1);
    }
    private boolean hasName(String name) { for(Layer layer:layers) if(layer.name.equals(name)) return true; return false; }
    void removeLayer() {
        idle(); if(layers.size()==1) throw new IllegalStateException("Keep at least one layer.");
        StackState old=new StackState(this); layers.remove(active); selectLayer(Math.min(active,layers.size()-1)); stackEdit(old);
    }
    void moveLayer(int target) {
        idle(); if(target<0||target>=layers.size()||target==active) return;
        StackState old=new StackState(this); Layer layer=layers.remove(active); layers.add(target,layer); selectLayer(target); stackEdit(old);
    }
    void setLayerVisible(int index,boolean visible) {
        idle(); if(layers.get(index).visible==visible) return;
        StackState old=new StackState(this); layers.get(index).visible=visible; stackEdit(old);
    }
    void renameLayer(String name) {
        idle(); name=name.trim(); if(name.isEmpty()||name.length()>40) throw new IllegalArgumentException("Use a layer name from 1 to 40 characters.");
        if(layers.get(active).name.equals(name)) return;
        StackState old=new StackState(this); layers.get(active).name=name; stackEdit(old);
    }
    /**
     * Rearranges the layers as one undoable step: new layer {@code i} is old layer {@code order[i]}, or, where
     * that is -1, a new blank layer named {@code names[i]}; old layers left out are removed. Selects
     * {@code active} of the new list. False, changing nothing, when that is the stack as it is.
     */
    boolean restack(int[] order,String[] names,int active) {
        idle();
        if(order.length<1||order.length>MAX_LAYERS) throw new IllegalArgumentException("Each page can have 1 to "+MAX_LAYERS+" layers.");
        if(active<0||active>=order.length) throw new IllegalArgumentException("Invalid layer");
        boolean[] used=new boolean[layers.size()]; boolean same=order.length==layers.size()&&active==this.active;
        for(int i=0;i<order.length;i++) {
            int from=order[i];
            if(from<-1||from>=layers.size()||from>=0&&used[from]) throw new IllegalArgumentException("Invalid layer order");
            if(from<0&&(names==null||names[i]==null||names[i].trim().isEmpty())) throw new IllegalArgumentException("Name each new layer");
            if(from>=0) used[from]=true;
            same&=from==i;
        }
        if(same) return false;
        StackState old=new StackState(this);
        ArrayList<Layer> next=new ArrayList<>();
        for(int i=0;i<order.length;i++) next.add(order[i]>=0?layers.get(order[i]):new Layer(names[i],true,paper(width,height),new byte[width*height]));
        layers.clear(); layers.addAll(next);
        selectLayer(active); stackEdit(old); return true;
    }
    /** Inkwash: the layers made {@code next} (new layers; the old kept for Undo) as one undoable step, {@code active} selected (Merge Old Layers). */
    boolean replaceLayers(java.util.List<Layer> next,int active) {
        idle();
        if(next.isEmpty()||next.size()>MAX_LAYERS||active<0||active>=next.size()) throw new IllegalArgumentException("Invalid layers");
        StackState old=new StackState(this);
        layers.clear(); layers.addAll(next);
        selectLayer(active); stackEdit(old); return true;
    }
    /** Shows or hides layers {@code from} to {@code to} (inclusive) as one undoable step; false when all already are. */
    boolean setLayersVisible(int from,int to,boolean visible) {
        idle(); if(from<0||to>=layers.size()||from>to) throw new IllegalArgumentException("Invalid layer");
        boolean change=false;
        for(int i=from;i<=to;i++) change|=layers.get(i).visible!=visible;
        if(!change) return false;
        StackState old=new StackState(this);
        for(int i=from;i<=to;i++) layers.get(i).visible=visible;
        stackEdit(old); return true;
    }
    private void stackEdit(StackState old) {
        revision++;
        addEdit(new Edit(old,new StackState(this))); markAllDirty();
    }
    /** Add transparent paper around every layer without resampling existing pixels. */
    void expand(int left, int top, int right, int bottom) {
        idle();
        if (left<0 || top<0 || right<0 || bottom<0) throw new IllegalArgumentException("Canvas margins cannot be negative");
        long w=(long)width+left+right, h=(long)height+top+bottom;
        if(w>4096 || h>4096) throw new IllegalArgumentException("This canvas is too large to expand further.");
        validateSize((int)w,(int)h);
        if(w==width && h==height)return;
        StackState old=new StackState(this);
        ArrayList<Layer> expanded=new ArrayList<>();
        int wide=(int)w, high=(int)h;
        for(Layer layer:layers) expanded.add(layer.each(l->expanded(l,wide,high,left,top)));
        Layer fluid=mask==null?null:expanded(mask,(int)w,(int)h,left,top);
        width=(int)w; height=(int)h; layers.clear(); layers.addAll(expanded); mask=fluid;
        captured=new boolean[columns()*((height+TILE-1)/TILE)];
        selectLayer(active); stackEdit(old);
    }
    /** A copy of {@code layer} on a larger transparent canvas, offset by {@code left} and {@code top}. */
    private Layer expanded(Layer layer,int w,int h,int left,int top) {
        byte[] ink=paper(w,h), coverage=new byte[w*h], kept=layer.share==null?null:paper(w,h), printed=layer.print==null?null:paper(w,h), kinded=layer.kind==null?null:new byte[w*h];
        for(int y=0;y<height;y++) {
            System.arraycopy(layer.tones,y*width,ink,(y+top)*w+left,width);
            System.arraycopy(layer.alpha,y*width,coverage,(y+top)*w+left,width);
            if(kept!=null) System.arraycopy(layer.share,y*width,kept,(y+top)*w+left,width);
            if(printed!=null) System.arraycopy(layer.print,y*width,printed,(y+top)*w+left,width);
            if(kinded!=null) System.arraycopy(layer.kind,y*width,kinded,(y+top)*w+left,width);
        }
        Layer next=new Layer(layer.name,layer.visible,ink,coverage); next.opacity=layer.opacity; next.share=kept; next.print=printed; next.kind=kinded; next.kinds=layer.kinds; next.kindBase=layer.kindBase; return next;
    }
    private void markAllDirty() { left=0; top=0; right=width; bottom=height; }
    private void addEdit(Edit edit) {
        for(Edit discarded:redo) historyBytes-=discarded.bytes;
        if(!redo.isEmpty()) log=log.upTo(top());
        redo.clear(); undo.addLast(edit); historyBytes+=edit.bytes; trimHistory();
    }
    /** Bottom-to-top source-over composition on the page's {@linkplain #paperTone paper}. Brushes read only the active layer. */
    int compositeTone(int x,int y) { return compositeToneWithout(x,y,0,-1); }
    /** {@link #compositeTone} without layers {@code from} to {@code to} (inclusive): the page were they clear. */
    int compositeToneWithout(int x,int y,int from,int to) {
        int i=y*width+x, result=paperAt(i);
        for(int k=0,end=stackSize(layers.size());k<end;k++) {
            Layer layer=stacked(layers,k); int n=stackOwner(k);
            if(layer==null||!layers.get(n).visible||n>=from&&n<=to) continue;
            int a=((layer.alpha[i]&255)*stackOpacity(layers.get(n),layer)+50)/100;
            result=((layer.tones[i]&255)*a+result*(255-a)+127)/255;
        }
        return result;
    }
    /**
     * {@link #compositeTone} with layers {@code from} to {@code to} (inclusive) at the coverage the eraser leaves them
     * ({@link Layer#share}): the page were they erased. Layers that hold only erasable paint are left out, as
     * {@link #compositeToneWithout} leaves them.
     */
    int compositeToneKept(int x,int y,int from,int to) { return compositeToneKept(x,y,from,to,0); }
    /**
     * As {@link #compositeToneKept(int, int, int, int)}, for an eraser limited to {@code kinds} ({@link #eraseKinds}): a pixel it
     * doesn't take is kept whole. {@link #KIND_ALL} is the Everything eraser, which takes every pixel whole (the owner, 2026-10-22).
     */
    int compositeToneKept(int x,int y,int from,int to,int kinds) {
        // Inkwash: the Everything eraser rubs a cover's design off too (rubDesign).
        byte[] bare=kinds==0||kinds==KIND_ALL?bare():null;
        int i=y*width+x, result=bare!=null?bare[i]&255:paperAt(i);
        // Inkwash: a plane is a layer of its one kind, so a limited eraser takes it whole or leaves it.
        for(int k=0,end=stackSize(layers.size());k<end;k++) {
            Layer layer=stacked(layers,k); int n=stackOwner(k);
            if(layer==null||!layers.get(n).visible) continue;
            int c=layer.alpha[i]&255;
            if(n>=from&&n<=to&&(kinds==0||c==0||(layer.kindsAt(i)&kinds)!=0)) c=layer.share==null||kinds!=0&&(layer.permanentAt(i)&~kinds)==0?0:kept(c,layer.share[i]&255);
            int a=(c*stackOpacity(layers.get(n),layer)+50)/100;
            result=((layer.tones[i]&255)*a+result*(255-a)+127)/255;
        }
        return result;
    }
    /** The coverage a pixel of coverage {@code a} keeps when erased, its {@link Layer#share} {@code s}: rounded. */
    static int kept(int a,int s) { return (a*(255-s)+127)/255; }
    /** The share ({@link Layer#share}) that leaves {@code kept} of coverage {@code a} ({@code kept <= a}): exactly, {@link #kept} gives {@code kept} back. */
    private static int shareOf(int kept,int a) { return a==0?255:255-divide(kept*255+a/2,a); }
    /** {@code ceil(2^24 / d)} for d 1 to 255: {@link #divide} by a multiply. */
    private static final long[] RECIPROCAL=new long[256];
    static { for(int d=1;d<256;d++) RECIPROCAL[d]=((1L<<24)+d-1)/d; }
    /** {@code n / d}, exact for {@code 0 <= n <= 65280} and {@code 1 <= d <= 255}, without a division. */
    private static int divide(int n,int d) { return (int)((n*RECIPROCAL[d])>>>24); }
    /**
     * Layers {@code from} to {@code to} (inclusive) alone on white paper, shown or hidden, at the first
     * {@code count} pixel indices ({@code y * width + x}) of {@code at}, into {@code out}: what they hold,
     * sampled, for a small picture of them.
     */
    void layerTones(int from,int to,int[] at,int count,int[] out) {
        Arrays.fill(out,0,count,255);
        for(int s=0,end=stackSize(layers.size());s<end;s++) {
            Layer layer=stacked(layers,s); int n=stackOwner(s);
            if(layer==null||n<from||n>to) continue;
            byte[] t=layer.tones,a=layer.alpha; int opacity=stackOpacity(layers.get(n),layer);
            for(int k=0;k<count;k++) {
                int i=at[k], c=a[i]&255;
                if(c==0) continue;
                c=(c*opacity+50)/100;
                out[k]=((t[i]&255)*c+out[k]*(255-c)+127)/255;
            }
        }
    }
    int opacity(int x,int y) { return alpha[y*width+x]&255; }
    void eraseTone(int x,int y,float strength) {
        if(x<0||y<0||x>=width||y>=height) return;
        int i=y*width+x, a=opacity(x,y), removed=Math.max(1,Math.round(a*strength));
        // An eraser that takes one kind of mark leaves the rest.
        if(erasing!=0&&a>0&&!takes(i)) return;
        // Erasable: only the share of the coverage the eraser can take goes.
        if(share!=null&&!overrides(i)) { int keep=kept(a,share[i]&255); if(keep>=a) return; removed=Math.min(removed,a-keep); }
        if(strength<=0) return;
        setPixel(x,y,tones[i]&255,Math.max(0,a-removed));
        // Print tone: as much of it stays as of the coverage (through masking fluid, as much as the fluid let go);
        // where the layer holds none, the eraser's strength past the fluid.
        if(print!=null&&print[i]!=(byte)255) {
            int left=alpha[i]&255, from=a>0?a:255, to=a>0?left:Math.round(255*(1-Math.min(1,strength)*(1-(bound==mask?0:shield(i))/255f)));
            if(to<from) { capture((y/TILE)*columns()+x/TILE); writable(); print[i]=to==0?(byte)255:lowered(print[i],to,from); }
        }
    }
    /** Pixel {@code i}'s kinds once this gesture writes it, found with coverage {@code a}: its own where there was none, else added to what it held. */
    private byte laid(int i,int a) {
        // A base pixel's kinds laid permanent paint if its share, as found, keeps some.
        int was=a==0?0:kind[i]!=0?kind[i]:bound.kindBase|(share!=null&&share[i]!=(byte)255?bound.kindBase<<PERMANENT:0);
        return (byte)(was|laying|(permanent>0?laying<<PERMANENT:0));
    }
    /** Whether the eraser's kinds ({@link #eraseKinds}) hold some of what pixel {@code i} of the bound layer holds. */
    private boolean takes(int i) { return (bound.kindsAt(i)&erasing)!=0; }
    /**
     * Whether the eraser, limited to kinds, takes pixel {@code i} of the bound layer whole, permanent paint too (the owner,
     * 2026-10-20): no other kind laid permanent paint there ({@link Layer#permanentAt}). Where one did, the pixel keeps its
     * permanent part as ever. (Not "holds no other kind": an erasable mark once laid over charcoal tagged its residue for good.)
     */
    private boolean overrides(int i) { return erasing!=0&&(bound.permanentAt(i)&~erasing)==0; }
    /**
     * Limits the eraser to marks of these kinds ({@code KIND_} bits), taken whole where no other kind laid permanent paint
     * ({@link #KIND_ALL}: every pixel, whole, the Everything eraser's, the owner 2026-10-22); 0: every mark by the Erasable rule
     * (MonoPaint's erasers, lifting fluid's wax). {@code eraseTones} and {@code eraseTone} take only those, under wax too ({@link #shield}).
     */
    void eraseKinds(int kinds) { erasing=kinds&KIND_ALL; }
    /** Print tone {@code p} with {@code kept} of its darkness's {@code of} parts left. */
    private static byte lowered(byte p,int kept,int of) { return (byte)(255-((255-(p&255))*kept+of/2)/of); }
    /**
     * {@link #eraseTone}{@code (x, y, 1)} for each pixel of the {@code w} by {@code h} block at {@code x, y}
     * whose {@code erased} entry is not 0, row by row: paper, or what the pixel keeps ({@link Layer#share}), with Undo
     * capture and the dirty area worked out once a tile and a block. The eraser lays this many at once when it lifts.
     */
    void eraseTones(int x,int y,int w,int h,byte[] erased) {
        if(!editing) throw new IllegalStateException("No active gesture");
        if(mask!=null&&bound!=mask) {
            // Masking fluid shields some of these pixels; each one takes its share.
            for(int row=0;row<h;row++) for(int col=0;col<w;col++) if(erased[row*w+col]!=0) eraseTone(x+col,y+row,1);
            return;
        }
        boolean written=false; int lastKey=-1, columns=columns();
        int minX=width,minY=height,maxX=0,maxY=0;
        int rowEnd=Math.min(h,height-y), colStart=Math.max(0,-x), colEnd=Math.min(w,width-x);
        for(int row=Math.max(0,-y);row<rowEnd;row++) {
            int py=y+row, rowKey=(py/TILE)*columns;
            for(int col=colStart,e=row*w+colStart,i=py*width+x+colStart;col<colEnd;col++,e++,i++) {
                // Erasing in full leaves no coverage, which is paper: white, transparent (and no print tone).
                if(erased[e]==0||(tones[i]==(byte)255&&alpha[i]==0&&(print==null||print[i]==(byte)255))) continue;
                if(erasing!=0&&alpha[i]!=0&&!takes(i)) continue;
                // Erasable: what the pixel keeps stays, at its tone; a pixel that keeps all of it is left.
                int keep=share==null||overrides(i)?0:kept(alpha[i]&255,share[i]&255);
                if(keep>=(alpha[i]&255)&&alpha[i]!=0) continue;
                int px=x+col,key=rowKey+px/TILE;
                if(key!=lastKey) { lastKey=key; capture(key); }
                if(!written) { written=true; writable(); }
                // Print tone: as much of it stays as of the coverage.
                if(print!=null) print[i]=keep==0?(byte)255:lowered(print[i],keep,alpha[i]&255);
                if(keep==0) { tones[i]=(byte)255; alpha[i]=0; if(share!=null) share[i]=(byte)255; }
                else { alpha[i]=(byte)keep; share[i]=0; }
                if(px<minX) minX=px; if(px>maxX) maxX=px; if(py<minY) minY=py; if(py>maxY) maxY=py;
            }
        }
        if(written) markDirty(minX,minY,maxX+1,maxY+1);
    }
    /** Anti-aliased edge pixels, with partial mask alpha, remove only that share of the gesture's original coverage. */
    void eraseMask(int[] mask,int stride,int x,int y,int w,int h) {
        for(int row=0;row<h;row++) for(int col=0;col<w;col++) {
            int c=mask[row*stride+col]>>>24, px=x+col, py=y+row;
            if(c==255) eraseTone(px,py,1);
            else if(c!=0 && px>=0 && py>=0 && px<width && py<height) eraseFromBase(px,py,c/255f);
        }
    }
    private boolean[] captured;
    private final LinkedHashMap<Integer, byte[]> before = new LinkedHashMap<>();
    /** Beside {@link #before}, on a layer with a {@link Layer#share}: each captured tile's shares as the gesture found them (under {@link #before}'s lock). */
    private final HashMap<Integer, byte[]> beforeShare = new HashMap<>();
    /** Inkwash: beside {@link #before}, on a layer with a {@link Layer#print}: each captured tile's print tones as the gesture found them (under {@link #before}'s lock). */
    private final HashMap<Integer, byte[]> beforePrint = new HashMap<>();
    /** Inkwash: beside {@link #before}, on a layer with a {@link Layer#kind}: each captured tile's kinds as the gesture found them (under {@link #before}'s lock). */
    private final HashMap<Integer, byte[]> beforeKind = new HashMap<>();
    /**
     * {@link #before} and {@link #beforeShare} by tile key, made at a gesture's begin: looked up without a map, and by a
     * band's writer without taking the lock (a band captures and reads only its own tiles).
     */
    private byte[][] foundTiles, foundShares, foundPrints, foundKinds;
    /**
     * While open, a gesture's writers may run on several threads at once, each in its own rows of whole {@link #TILE}
     * tile rows, with {@link #setTones}, {@link #coverTones}, {@link #restoreSpan}, {@link #strokeBaseTones},
     * {@link #tone} and {@link #touched}: Undo's captures and the dirty area are kept under a lock and {@link #baseTile}'s
     * cache is passed over. The owner thread opens them, does nothing else with the document meanwhile, and closes them.
     */
    private boolean bands;
    /** Opens {@linkplain #bands bands} for the gesture in progress. */
    void openBands() {
        if (!editing) throw new IllegalStateException("No active gesture");
        writable(); bands = true;
    }
    void closeBands() { bands = false; }
    /**
     * While on, another thread may read what the gesture's layer held when the gesture began ({@link #baseTones}): the
     * owner's Undo captures and its gestures' beginning and cancelling are kept under a lock. A quick stroke's true
     * render composes ahead of its lift this way (StrokeTones) while its preview draws on the page.
     */
    private volatile boolean sharing;
    /** Turns {@linkplain #sharing shared} base reads on or off (the owner thread). */
    void shareBase(boolean on) { sharing = on; }
    /**
     * Tile {@code key}'s tones over white as the gesture in progress found them (the active layer's when none is), into
     * {@code out}, {@link #TILE} to a row (a tile at the page's edge fills only its part). Safe from another thread while
     * {@linkplain #shareBase shared}: a tile the gesture has changed is read from its Undo capture, any other from the
     * page, which the owner can't change until it has captured it.
     */
    void baseTones(int key, byte[] out) { baseTones(key, out, null); }
    /**
     * {@link #baseTones} of the masking fluid as it is now (paper where there is none), with its coverages unless null:
     * a crayon's wax composed beside its stroke, which never writes the fluid ({@link StrokeTones#onMask}).
     */
    void maskTones(int key, byte[] out, byte[] coverages) {
        int x0 = key % columns() * TILE, y0 = key / columns() * TILE, w = Math.min(TILE, width - x0), h = Math.min(TILE, height - y0);
        for (int row = 0; row < h; row++)
            for (int col = 0, i = (y0 + row) * width + x0; col < w; col++, i++) {
                int a = mask == null ? 0 : mask.alpha[i] & 255;
                out[row * TILE + col] = (byte)onWhite(mask == null ? 255 : mask.tones[i] & 255, a);
                if (coverages != null) coverages[row * TILE + col] = (byte)a;
            }
    }
    /** {@link #baseTones} with each pixel's coverage into {@code coverages} too (unless null), as {@link #strokeBasePixels} gives them. */
    void baseTones(int key, byte[] out, byte[] coverages) {
        byte[] tile = foundTile(key);
        int tileWidth = Math.min(TILE, width - key % columns() * TILE), half = tile.length / 2, rows = half / tileWidth;
        for (int row = 0; row < rows; row++)
            for (int col = 0, t = row * tileWidth; col < tileWidth; col++, t++) {
                out[row * TILE + col] = (byte)onWhite(tile[t] & 255, tile[half + t] & 255);
                if (coverages != null) coverages[row * TILE + col] = tile[half + t];
            }
    }
    /** Tile {@code key} of the gesture's layer as the gesture found it: its Undo capture, else the page's (see {@link #baseTones}). */
    private byte[] foundTile(int key) { synchronized (before) { byte[] tile = before.get(key); return tile != null ? tile : copyTile(key); } }
    /**
     * Tile {@code key}'s composite over {@code paper} (255: white, the marks alone; {@link #PAGE_PAPER}: the page as
     * shown) of every visible layer from the bottom to layer {@code top} (inclusive), the gesture's layer as the gesture
     * in progress found it, into {@code out}, {@link #TILE} to a row: the page under a tool that reads what is already
     * there, as it was before the tool touched it. Safe as {@link #baseTones} is.
     */
    void baseComposite(int key, int top, int paper, byte[] out) { composite(key, top, bound, foundTile(key), paper, out); }
    /**
     * The tiles of the gesture in progress's layer as the gesture found them (its Undo captures), by tile key: never
     * changed afterwards, so they may be read once the gesture is finished, on any thread. What a tool that grows from a
     * stroke's mark after it reads (Halo: the stroke's own darkness, the page under it).
     */
    Map<Integer, byte[]> foundTiles() { synchronized (before) { return new HashMap<>(before); } }
    /**
     * Tile {@code key}'s composite over white of every visible layer, layer {@code layer} read from {@code tile} (one of
     * {@link #foundTiles}: the page before that gesture), or as it is for null, into {@code out}, {@link #TILE} to a row.
     * Reads the page: on another thread only while nothing changes it.
     */
    void foundComposite(int key, int layer, byte[] tile, byte[] out) { foundComposite(key, layer, 0, tile, out); }
    /** As above, the gesture's having laid {@code kinds}: the tile is of that layer's plane for them ({@link #planeOf}). */
    void foundComposite(int key, int layer, int kinds, byte[] tile, byte[] out) {
        Layer own = null;
        if (tile != null) { int p = planeOf(kinds); own = p < 0 ? layers.get(layer) : layers.get(layer).plane(p); }
        composite(key, layers.size() - 1, own, tile, 255, out);
    }
    /** {@link #baseComposite}'s and {@link #foundComposite}'s: layer {@code own} read from {@code tile} (none for null), every other as it is, over {@code paper}. */
    private void composite(int key, int top, Layer own, byte[] tile, int paper, byte[] out) {
        int x0 = key % columns() * TILE, y0 = key / columns() * TILE, tileWidth = Math.min(TILE, width - x0), half = tileWidth * Math.min(TILE, height - y0), rows = half / tileWidth;
        for (int row = 0; row < rows; row++)
            for (int col = 0, t = row * tileWidth, i = (y0 + row) * width + x0; col < tileWidth; col++, t++, i++) {
                int result = paper >= 0 ? paper : paperAt(i);
                for (int k = 0, end = stackSize(layers.size()); k < end; k++) {
                    Layer layer = stacked(layers, k); int n = stackOwner(k);
                    if (layer == null || n > top || !layers.get(n).visible) continue;
                    boolean found = layer == own;
                    int a = (((found ? tile[half + t] : layer.alpha[i]) & 255) * stackOpacity(layers.get(n), layer) + 50) / 100;
                    result = (((found ? tile[t] : layer.tones[i]) & 255) * a + result * (255 - a) + 127) / 255;
                }
                out[row * TILE + col] = (byte)result;
            }
    }
    private final ArrayDeque<Edit> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private int historyBytes;
    /** Inkwash: marks fading or darkening by the clock (Fading, Lemon Juice: {@link FadingInk}), oldest first; immutable each. */
    private final ArrayList<FadingInk.Mark> fades = new ArrayList<>();
    private boolean editing;
    private int left, top, right, bottom;

    ToneDocument(int width, int height) {
        this(width, height, paper(width, height));
        Arrays.fill(alpha,(byte)0);
    }
    ToneDocument(int width, int height, byte[] source) {
        validateSize(width, height);
        if (source.length != width * height) throw new IllegalArgumentException("Wrong tone count");
        this.width = width; this.height = height;
        byte[] coverage=new byte[source.length]; Arrays.fill(coverage,(byte)255);
        layers.add(new Layer("Layer 1",true,source.clone(),coverage)); selectLayer(0);
        captured = new boolean[columns() * ((height + TILE - 1) / TILE)];
        clearDirty();
    }
    ToneDocument(int width,int height,ArrayList<Layer> source,int active) { this(width,height,source,active,null); }
    /** @param mask the page's masking fluid, or null */
    ToneDocument(int width,int height,ArrayList<Layer> source,int active,Layer mask) {
        validateSize(width,height); this.width=width; this.height=height;
        if(source.isEmpty()||source.size()>MAX_LAYERS) throw new IllegalArgumentException("Invalid layer count");
        ArrayList<Layer> all=new ArrayList<>(source); if(mask!=null) all.add(mask);
        for(Layer layer:source) if(layer.planes!=null) { if(layer.planes.length!=PLANES) throw new IllegalArgumentException("Invalid planes"); for(Layer p:layer.planes) if(p!=null) all.add(p); }
        for(Layer layer:all)
            if(layer.tones.length!=width*height||layer.alpha.length!=width*height||layer.share!=null&&layer.share.length!=width*height
                    ||layer.print!=null&&layer.print.length!=width*height||layer.kind!=null&&layer.kind.length!=width*height) throw new IllegalArgumentException("Invalid layer pixels");
        layers.addAll(source); this.mask=mask;
        selectLayer(active); captured=new boolean[columns()*((height+TILE-1)/TILE)]; clearDirty();
    }
    static void validateSize(int width, int height) {
        if (!validSize(width, height)) throw new IllegalArgumentException("Invalid canvas size");
    }
    static boolean validSize(int width, int height) {
        return width > 0 && height > 0 && width <= 4096 && height <= 4096 && (long)width * height <= MAX_PIXELS;
    }
    static byte[] paper(int width, int height) {
        validateSize(width, height);
        byte[] bytes = new byte[width * height]; Arrays.fill(bytes, (byte)255); return bytes;
    }
    int tone(int x, int y) { int i=y*width+x; return onWhite(tones[i]&255,alpha[i]&255); }
    /** The active layer's tone, even while a gesture lays masking fluid, so fills can mask a painted region. */
    int layerTone(int x, int y) { return look(layers.get(active),y*width+x); }
    /** Inkwash: pixel {@code i} of {@code layer} over white, its base and kind planes as one ({@link #sheets}), opacity left out. */
    private static int look(Layer layer,int i) { int s=sheets(layer,i); return onWhite(s>>>8,s&255); }
    private static int onWhite(int tone,int alpha) { return (tone*alpha+255*(255-alpha)+127)/255; }
    /** Whether this gesture has changed anything in the tile holding page pixel {@code x, y}; where it hasn't, every pixel is as the gesture found it. */
    boolean touched(int x, int y) { return captured[(y/TILE)*columns()+x/TILE]; }
    int strokeBaseTone(int x, int y) {
        int key=(y/TILE)*columns()+x/TILE;
        if(!captured[key]) return tone(x,y);
        int tileWidth=Math.min(TILE,width-(x/TILE)*TILE);
        int offset=(y%TILE)*tileWidth+x%TILE;
        byte[] tile=baseTile(key);
        return onWhite(tile[offset]&255,tile[tile.length/2+offset]&255);
    }
    /**
     * {@link #strokeBaseTone} for the {@code w} by {@code h} block at {@code x, y}, into {@code out} row by
     * row; entries for pixels off the page are left alone. Looks each tile's capture up once, not per pixel.
     */
    void strokeBaseTones(int x, int y, int w, int h, int[] out) {
        int x0 = Math.max(0, x), x1 = Math.min(width, x + w), y0 = Math.max(0, y), y1 = Math.min(height, y + h);
        for (int py = y0; py < y1; py++) {
            int rowKey = (py / TILE) * columns();
            for (int px = x0; px < x1; ) {
                int column = px / TILE, end = Math.min(x1, (column + 1) * TILE), key = rowKey + column;
                int o = (py - y) * w + px - x;
                if (!captured[key]) {
                    for (int i = py * width + px, stop = py * width + end; i < stop; i++, o++) out[o] = onWhite(tones[i] & 255, alpha[i] & 255);
                } else {
                    byte[] tile = baseTile(key);
                    int tileWidth = Math.min(TILE, width - column * TILE), half = tile.length / 2;
                    for (int t = (py % TILE) * tileWidth + px % TILE, stop = t + end - px; t < stop; t++, o++)
                        out[o] = onWhite(tile[t] & 255, tile[half + t] & 255);
                }
                px = end;
            }
        }
    }
    /**
     * {@link #strokeBaseTones} with each pixel's coverage beside it, 0 to 255, into {@code coverages}: the active
     * layer as the gesture found it. A smudge reads the page it moves this way (premultiplied: darkness and coverage).
     */
    void strokeBasePixels(int x, int y, int w, int h, int[] tones, int[] coverages) {
        int x0 = Math.max(0, x), x1 = Math.min(width, x + w), y0 = Math.max(0, y), y1 = Math.min(height, y + h);
        for (int py = y0; py < y1; py++) {
            int rowKey = (py / TILE) * columns();
            for (int px = x0; px < x1; ) {
                int column = px / TILE, end = Math.min(x1, (column + 1) * TILE), key = rowKey + column;
                int o = (py - y) * w + px - x;
                if (!captured[key]) {
                    for (int i = py * width + px, stop = py * width + end; i < stop; i++, o++) { int a = alpha[i] & 255; tones[o] = onWhite(this.tones[i] & 255, a); coverages[o] = a; }
                } else {
                    byte[] tile = captureOf(key);
                    int tileWidth = Math.min(TILE, width - column * TILE), half = tile.length / 2;
                    for (int t = (py % TILE) * tileWidth + px % TILE, stop = t + end - px; t < stop; t++, o++) { int a = tile[half + t] & 255; tones[o] = onWhite(tile[t] & 255, a); coverages[o] = a; }
                }
                px = end;
            }
        }
    }
    /**
     * Inkwash: {@link #strokeBasePixels} of layer {@code index} as it is, for a layer no gesture is changing (a stump's
     * other half of the pair, which it blends as well: {@code CarryPlane}): its tones over white and coverages. Inkwash: its
     * plane for {@code kinds} (its base for none, or a plane it hasn't).
     */
    /** Inkwash: layer {@code index} as it looks, base and kind planes laid as sheets in their order: tones over white and coverages. */
    void layerPixels(int index, int x, int y, int w, int h, int[] tones, int[] coverages) {
        Layer layer = layers.get(index);
        if (layer.planes == null) { layerPixels(index, 0, x, y, w, h, tones, coverages); return; }
        int x0 = Math.max(0, x), x1 = Math.min(width, x + w), y0 = Math.max(0, y), y1 = Math.min(height, y + h);
        for (int py = y0; py < y1; py++)
            for (int px = x0, i = py * width + x0, o = (py - y) * w + x0 - x; px < x1; px++, i++, o++) { int s = sheets(layer, i); tones[o] = onWhite(s >>> 8, s & 255); coverages[o] = s & 255; }
    }
    /**
     * Inkwash: pixel {@code i} of {@code layer} as one plane: its base with each kind plane laid over as a sheet ({@link
     * #sheet}), in their order, raw tone {@code << 8 |} coverage. What a file shows a reader without planes, Merge's input.
     */
    static int sheets(Layer layer, int i) {
        int t = layer.tones[i] & 255, a = layer.alpha[i] & 255;
        if (layer.planes != null) for (Layer p : layer.planes) if (p != null && p.alpha[i] != 0) {
            int s = sheet(p.tones[i] & 255, p.alpha[i] & 255, t, a);
            t = s == 0 ? 255 : s >>> 8; a = s & 255;
        }
        return t << 8 | a;
    }
    void layerPixels(int index, int kinds, int x, int y, int w, int h, int[] tones, int[] coverages) {
        Layer layer = target(index, kinds);
        if (editing && layer == bound) throw new IllegalStateException("The gesture's layer: read it as the gesture found it");
        int x0 = Math.max(0, x), x1 = Math.min(width, x + w), y0 = Math.max(0, y), y1 = Math.min(height, y + h);
        for (int py = y0; py < y1; py++)
            for (int px = x0, i = py * width + x0, o = (py - y) * w + x0 - x; px < x1; px++, i++, o++) { int a = layer.alpha[i] & 255; tones[o] = onWhite(layer.tones[i] & 255, a); coverages[o] = a; }
    }
    /** Inkwash: layer {@code index}'s plane for {@code kinds} ({@link #planeOf}), or its base for none or a plane it hasn't. */
    private Layer target(int index, int kinds) {
        int p = planeOf(kinds); Layer layer = layers.get(index), plane = p < 0 ? null : layer.plane(p);
        return plane != null ? plane : layer;
    }
    /** Inkwash: {@link #strokeBaseKept} of layer {@code index} as it is (beside {@link #layerPixels}); false, leaving {@code out} alone, when it keeps nothing. */
    boolean layerKept(int index, int kinds, int x, int y, int w, int h, int[] out) {
        Layer layer = target(index, kinds);
        if (layer.share == null) return false;
        int x0 = Math.max(0, x), x1 = Math.min(width, x + w), y0 = Math.max(0, y), y1 = Math.min(height, y + h);
        for (int py = y0; py < y1; py++)
            for (int px = x0, i = py * width + x0; px < x1; px++, i++) out[(py - y) * w + px - x] = kept(layer.alpha[i] & 255, layer.share[i] & 255);
        return true;
    }
    /**
     * The coverage the eraser would leave ({@link Layer#share}), 0 to 255, of each pixel of the {@code w} by {@code h}
     * block at {@code x, y} as the gesture found it, into {@code out} row by row (beside {@link #strokeBasePixels});
     * entries off the page are left alone. False, leaving {@code out} alone, when the layer has no share: it keeps nothing.
     */
    boolean strokeBaseKept(int x, int y, int w, int h, int[] out) {
        if (share == null) return false;
        int x0 = Math.max(0, x), x1 = Math.min(width, x + w), y0 = Math.max(0, y), y1 = Math.min(height, y + h);
        for (int py = y0; py < y1; py++)
            for (int px = x0; px < x1; px++) out[(py - y) * w + px - x] = keptFound(px, py);
        return true;
    }
    /**
     * Inkwash: the gesture in progress lays print tone ({@link Layer#print}: a screened brush, {@link PrintScreen}): its
     * layer gets one, every pixel 255, if it has none. Call it after {@link #begin(double)}, before {@link #openBands}.
     */
    void printing() {
        if(!editing) throw new IllegalStateException("No active gesture");
        if(print==null) { bound.print=paper(width,height); print=bound.print; }
    }
    /** Inkwash: layer {@code index}'s print tone at page pixel {@code x, y} ({@link Layer#print}): 255 where none. */
    int printTone(int index,int x,int y) {
        // Inkwash: the darkest of its base's and its kind planes'.
        Layer layer=layers.get(index); int i=y*width+x, tone=layer.print==null?255:layer.print[i]&255;
        if(layer.planes!=null) for(Layer p:layer.planes) if(p!=null&&p.print!=null) tone=Math.min(tone,p.print[i]&255);
        return tone;
    }
    /** Inkwash: {@link #printTone(int, int, int)} on the layer gestures paint (the active one). */
    int printTone(int x,int y) { return print==null?255:print[y*width+x]&255; }
    /**
     * Inkwash: the print tones of pixels {@code x .. x + w − 1} of row {@code y} as the gesture found them
     * ({@link Layer#print}; 255 where none), into {@code out} from 0; entries off the page are left alone.
     */
    void strokeBasePrints(int x,int y,int w,int[] out) {
        int x0=Math.max(0,x), x1=Math.min(width,x+w);
        if(y<0||y>=height||x1<=x0) return;
        if(print==null) { Arrays.fill(out,x0-x,x1-x,255); return; }
        int rowKey=(y/TILE)*columns();
        for(int px=x0;px<x1;) {
            int column=px/TILE, end=Math.min(x1,(column+1)*TILE), key=rowKey+column, o=px-x;
            if(!captured[key]) { for(int i=y*width+px,stop=y*width+end;i<stop;i++,o++) out[o]=print[i]&255; }
            else {
                byte[] p=foundPrints[key];
                if(p==null) Arrays.fill(out,o,o+end-px,255);
                else { int tileWidth=Math.min(TILE,width-column*TILE); for(int t=(y%TILE)*tileWidth+px%TILE,stop=t+end-px;t<stop;t++,o++) out[o]=p[t]&255; }
            }
            px=end;
        }
    }
    /**
     * Inkwash: tile {@code key}'s print tones as the gesture in progress found them (255 where none), into {@code out},
     * {@link #TILE} to a row, as {@link #baseTones} gives its tones: safe from another thread while {@linkplain #shareBase shared}.
     */
    void basePrints(int key,byte[] out) {
        byte[] p;
        synchronized(before) { p=before.containsKey(key)?beforePrint.get(key):print==null?null:copyPlane(print,key); }
        int tileWidth=Math.min(TILE,width-key%columns()*TILE), rows=Math.min(TILE,height-key/columns()*TILE);
        for(int row=0;row<rows;row++) for(int col=0;col<tileWidth;col++) out[row*TILE+col]=p==null?(byte)255:p[row*tileWidth+col];
    }
    /**
     * Inkwash: sets the print tones of pixels {@code x .. x + w − 1} of row {@code y} to {@code prints} (from 0; a
     * negative one leaves its pixel), as the gesture's other writes do (Undo's capture; masking fluid keeps out its share
     * of the change). A layer without print tone gets it first ({@link #printing}; not while bands are open).
     */
    void setPrints(int x,int y,int w,int[] prints) {
        if(!editing) throw new IllegalStateException("No active gesture");
        if(y<0||y>=height) return;
        boolean written=false; int lastKey=-1, rowKey=(y/TILE)*columns();
        int[] found=null;
        for(int col=Math.max(0,-x),end=Math.min(w,width-x);col<end;col++) {
            int p=prints[col];
            if(p<0) continue;
            if(p>255) throw new IllegalArgumentException("Invalid tone");
            if(print==null) { if(bands) throw new IllegalStateException("Call printing() before opening bands"); printing(); }
            int px=x+col, i=y*width+px;
            if(mask!=null&&bound!=mask) {
                int shielded=shield(i);
                if(shielded==255) continue;
                if(shielded>0) { if(found==null) { found=new int[1]; } strokeBasePrints(px,y,1,found); p=Math.round(found[0]+(p-found[0])*(1-shielded/255f)); }
            }
            if((print[i]&255)==p) continue;
            int key=rowKey+px/TILE;
            if(key!=lastKey) { lastKey=key; capture(key); }
            if(!written) { written=true; writable(); }
            print[i]=(byte)p;
        }
    }
    /**
     * How dark layer {@code index} is over white, 0 where clear to 255 for black, for the {@code w} by {@code h}
     * block at {@code x, y}, into {@code out} row by row, 0 off the page. False, leaving {@code out} alone, when the
     * whole block is clear or the layer hidden, so a caller can skip it. Reads the layer as it is, whichever layer
     * a gesture paints.
     */
    boolean layerDarkness(int index, int x, int y, int w, int h, int[] out) {
        Layer layer = layers.get(index);
        if (!layer.visible) return false;
        byte[] layerTones = layer.tones, layerAlpha = layer.alpha;
        // Inkwash: its look, base and kind planes together.
        boolean planed = layer.planes != null;
        boolean any = false;
        int x0 = Math.max(0, x), x1 = Math.min(width, x + w), y0 = Math.max(0, y), y1 = Math.min(height, y + h);
        for (int py = y0; py < y1; py++)
            for (int i = py * width + x0, stop = py * width + x1, o = (py - y) * w + x0 - x; i < stop; i++, o++) {
                int a = layerAlpha[i] & 255;
                if (a == 0 && !planed) continue;
                int dark = 255 - (planed ? look(layer, i) : onWhite(layerTones[i] & 255, a));
                if (dark == 0) continue;
                // Clear blocks, the most common, are only read; the first ink clears the rest of the block.
                if (!any) { Arrays.fill(out, 0, w * h, 0); any = true; }
                out[o] = dark;
            }
        return any;
    }
    byte[] snapshot() {
        byte[] result=new byte[width*height];
        for(int y=0;y<height;y++) for(int x=0;x<width;x++) result[y*width+x]=(byte)compositeTone(x,y);
        return result;
    }
    /** Opaque RGB pixels of the composite, calibrated as in {@link DotPattern#exportGray} or as raw logical tones. */
    int[] exportPixels(boolean calibrated) {
        int[] pixels=new int[width*height];
        // Inkwash: Real Mirror's reflection as the page shows it.
        Mirrors.View view=mirrors.view();
        int[] source=view==null?null:new int[width];
        for(int y=0;y<height;y++) {
            if(view!=null) view.row(y,0,width,width,height,source);
            for(int x=0;x<width;x++) {
            int s=view==null?y*width+x:source[x];
            int g=s<0?paper:compositeTone(s%width,s/width); if(calibrated) g=DotPattern.exportGray(g);
            pixels[y*width+x]=0xff000000|(g<<16)|(g<<8)|g;
            }
        }
        return pixels;
    }
    void begin() { begin(maskTarget,1); }
    /**
     * Begins a gesture whose paint the eraser takes {@code erasable} of, 0 (permanent) to 1 (all, as {@link #begin()}).
     * Below 1 the layer gets its {@link Layer#share} if it has none (every pixel all erasable until then). Masking fluid
     * laid this way ({@link #paintMaskingFluid}) is all erasable, as MonoPaint's tools begin at 1.
     */
    void begin(double erasable) { begin(maskTarget,erasable,0); }
    /**
     * As {@link #begin(double)}, for paint of {@code kinds} ({@code KIND_} bits; 0: of none): the layer notes them
     * ({@link Layer#kinds}) and, holding more than one kind, gets its {@link Layer#kind} plane; each pixel the gesture
     * writes holds them too, for an eraser that takes one kind.
     */
    void begin(double erasable,int kinds) { begin(maskTarget,erasable,kinds); }
    /**
     * Begins a gesture on the page's masking fluid (made if the page has none), whatever {@link #paintMaskingFluid} says:
     * a resist laid as any stroke is. Below 1 the eraser can't lift {@code 1 − erasable} of what it lays (wax).
     */
    void beginFluid(double erasable) { begin(true,erasable); }
    private void begin(boolean fluid) { begin(fluid,1); }
    /** Begins a gesture on the active layer, even while gestures lay masking fluid. */
    void beginLayer() { begin(false); }
    private void begin(boolean fluid,double erasable) { begin(fluid,erasable,0); }
    private void begin(boolean fluid,double erasable,int kinds) {
        if (editing) throw new IllegalStateException("Finish the current gesture first");
        Layer target;
        if(fluid) {
            if(mask==null) mask=new Layer("Masking fluid",true,paper(width,height),new byte[width*height]);
            target=mask; kinds=0;
        } else {
            if(!layerVisible(active)) throw new IllegalStateException("Show this layer before drawing on it.");
            // Inkwash: a kind's paint lays on its plane, made the first time that kind draws on the layer.
            int p=planeOf(kinds);
            target=p<0?layers.get(active):plane(layers.get(active),p);
        }
        begin(target,kinds,erasable);
    }
    /**
     * Inkwash: begins a gesture on plane {@code p} of the active layer ({@link #PLANES}; -1: its base), all erasable, of
     * no kind (the plane is its kind): the eraser and the Lasso, which work plane by plane. With {@code make} a missing
     * plane is made; without, false, beginning nothing.
     */
    boolean beginPlane(int p,boolean make) {
        if(editing) throw new IllegalStateException("Finish the current gesture first");
        Layer layer=layers.get(active), target=p<0?layer:make?plane(layer,p):layer.plane(p);
        if(target==null) return false;
        if(!layer.visible) throw new IllegalStateException("Show this layer before drawing on it.");
        begin(target,0,1);
        return true;
    }
    /** Inkwash: whether layer {@code index} has plane {@code p}. */
    boolean hasPlane(int index,int p) { return layers.get(index).plane(p)!=null; }
    /** Inkwash: plane {@code p} of {@code owner} (a layer of the page), made empty, of its kind and at its kind's opacity, if missing. */
    private Layer plane(Layer owner,int p) {
        Layer made=owner.plane(p);
        if(made!=null) return made;
        made=new Layer(PLANE_NAMES[p],true,paper(width,height),new byte[width*height]);
        made.kinds=(byte)PLANE_KINDS[p]; made.opacity=kindOpacity[p];
        Layer[] planes=owner.planes==null?new Layer[PLANES]:owner.planes.clone();
        planes[p]=made; owner.planes=planes;
        return made;
    }
    /** Inkwash: each kind's opacity on this page, percent ({@link #setKindOpacity}); planes made later take it. */
    private final int[] kindOpacity={100,100,100,100};
    /**
     * Inkwash: shows plane {@code p}'s kind at {@code value} percent on this page (100 shown, 0 hidden), every layer's plane of
     * it and those made later: the step-2 row's (Pen, Paint, Dry Media). Not an Undo step, not kept in the file.
     */
    void setKindOpacity(int p,int value) {
        if(value<0||value>100) throw new IllegalArgumentException("Invalid opacity");
        if(kindOpacity[p]==value) return;
        kindOpacity[p]=value;
        for(Layer layer:layers) { Layer plane=layer.plane(p); if(plane!=null) plane.opacity=value; }
        revision++; markAllDirty();
    }
    int kindOpacity(int p) { return kindOpacity[p]; }
    /** Begins a gesture on {@code target} (a layer, a plane of one, or the masking fluid), laying {@code kinds}. */
    private void begin(Layer target,int kinds,double erasable) {
        if(editing) throw new IllegalStateException("Finish the current gesture first");
        boolean fluid=target==mask;
        bind(target);
        // Reads between gestures go on reading the plane this one draws on, till another layer is selected; a gesture of no
        // kind (its base) leaves them on the last (a Clear Water drop on floating ink, then the float reads the ink's plane).
        if(!fluid&&target!=layers.get(active)) selected=target;
        permanent=!(erasable<1)?0:(int)Math.round(255*(1-Math.max(0,erasable)));
        waxTakes=false;
        writable();
        if(permanent>0&&share==null) { bound.share=paper(width,height); share=bound.share; }
        laying=fluid?0:kinds&KIND_ALL;
        if(laying!=0) {
            // Paint of no known kind (an old layer) counts as all; a second kind gives the layer its plane.
            int was=bound.kinds;
            if(was==0&&bound.covers()) was=KIND_ALL;
            int now=was|laying;
            if(Integer.bitCount(now)>1&&bound.kind==null) { bound.kindBase=(byte)was; bound.kind=new byte[width*height]; kind=bound.kind; }
            bound.kinds=(byte)now;
        }
        if(foundTiles==null||foundTiles.length!=captured.length) { foundTiles=new byte[captured.length][]; foundShares=new byte[captured.length][]; foundPrints=new byte[captured.length][]; foundKinds=new byte[captured.length][]; }
        editing = true; synchronized(before) { before.clear(); beforeShare.clear(); beforePrint.clear(); beforeKind.clear(); } lastBaseTile=null;lastBaseKey=-1;glazed.clear();
    }
    void setTone(int x,int y,int gray) {
        if (!editing) throw new IllegalStateException("No active gesture");
        if(gray<0||gray>255) throw new IllegalArgumentException("Invalid tone");
        if(x<0||y<0||x>=width||y>=height||tone(x,y)==gray) return;
        // Tonal tools carry pigment over transparent paper; opaque brushes use paintTone.
        int coverage=Math.max(opacity(x,y),255-gray);
        setPixel(x,y,rawTone(gray,coverage),coverage);
    }
    /**
     * {@link #setTone} for a block of pixels, {@code grays} row by row, {@code w} to a row; a
     * negative gray leaves its pixel, as do those off the page. Wet paint changes this many
     * at once each frame, and the checks for the whole block are made once.
     */
    void setTones(int x,int y,int w,int h,int[] grays) { tones(x,y,w,h,grays,null,false,null); }
    /**
     * {@link #setTones} with {@code owns} beside {@code grays}: the darkness each pixel's write lays of its own over white
     * paper, 0 to 255, of which the gesture's permanent share ({@link #begin(double)}) is what the eraser will leave;
     * negative, or {@code owns} null, for the darkening since the gesture found the pixel.
     */
    void setTones(int x,int y,int w,int h,int[] grays,int[] owns) { tones(x,y,w,h,grays,null,false,owns); }
    /**
     * Opaque paint: covers what lies beneath on this layer and the layers below by at least {@code coverages}.
     * As {@link #setTones}, {@code coverages} row by row beside {@code grays}, 0 to 255. A pixel's coverage builds on
     * what the gesture found there, not on what the gesture wrote before, so a stroke that covers less than it did a
     * moment ago (a wet window settling) lets the layers below show again.
     */
    void coverTones(int x,int y,int w,int h,int[] grays,int[] coverages) { tones(x,y,w,h,grays,coverages,false,null); }
    /** {@link #coverTones} with {@code owns}, as {@link #setTones(int, int, int, int, int[], int[])}. */
    void coverTones(int x,int y,int w,int h,int[] grays,int[] coverages,int[] owns) { tones(x,y,w,h,grays,coverages,false,owns); }
    /**
     * A smudge's write: as {@link #coverTones}, but {@code coverages} are each pixel's whole coverage floor, in place of
     * what the gesture found there, so a smear can thin a layer as well as darken it (a smeared line's coverage is what
     * the smear left, not the line's). Each pixel covers the more of its floor and what its tone needs.
     */
    void smearTones(int x,int y,int w,int h,int[] grays,int[] coverages) { tones(x,y,w,h,grays,coverages,true,null); }
    /** {@link #smearTones} with {@code owns}, as {@link #setTones(int, int, int, int, int[], int[])}. */
    void smearTones(int x,int y,int w,int h,int[] grays,int[] coverages,int[] owns) { tones(x,y,w,h,grays,coverages,true,owns); }
    /**
     * {@link #setTones}, and with {@code coverages} {@link #coverTones}: each pixel covers at least its coverage; with
     * {@code floors}, {@link #smearTones}: not also what the gesture found. The one block write: on a layer with a
     * {@link Layer#share}, each pixel's share follows what it kept as found and {@code owns}' permanent part.
     */
    private void tones(int x,int y,int w,int h,int[] grays,int[] coverages,boolean floors,int[] owns) {
        if (!editing) throw new IllegalStateException("No active gesture");
        // Inkwash: on a shaded paper (PageShade) transparent paint stays transparent: a pixel holding only darkness (a
        // black raw tone) takes the coverage of its new tone, lighter too, where it would keep its coverage at a lighter
        // raw tone (paint of its own over the paper). Over white the two are the same tone, so white pages write as ever.
        boolean glaze=(paper!=255||paperPixels!=null)&&coverages==null&&!floors;
        if(mask!=null&&bound!=mask) {
            // Masking fluid shields some of these pixels; each one takes its share.
            for(int row=0;row<h;row++) for(int col=0;col<w;col++) {
                int g=row*w+col,gray=grays[g],px=x+col,py=y+row;
                if(gray<0) continue;
                if(gray>255) throw new IllegalArgumentException("Invalid tone");
                if(px<0||py<0||px>=width||py>=height) continue;
                int own=owns==null?-1:owns[g];
                if(coverages==null) {
                    if(tone(px,py)==gray) continue;
                    int coverage=glaze&&tones[py*width+px]==0?255-gray:Math.max(opacity(px,py),255-gray);
                    setPixel(px,py,rawTone(gray,coverage),coverage,own); continue;
                }
                int coverage=Math.max(Math.max(floors?0:strokeBasePixel(px,py)&255,255-gray),coverages[g]);
                setPixel(px,py,rawTone(gray,coverage),coverage,own);
            }
            return;
        }
        // What store() does for each pixel, with its Undo capture, copy-on-write and dirty area worked out once a tile and a block.
        boolean fluid=bound==mask, written=false; int lastKey=-1, baseKey=-1, columns=columns();
        // Opaque paint's coverage builds on what the gesture found: a captured tile's copy, looked up once a tile; an uncaptured tile is as found.
        byte[] base=null; int baseWidth=0;
        // Erasable: a captured tile's coverages and shares as found, looked up once a tile.
        byte[] keptBase=null, keptShare=null; int keptKey=-1, keptWidth=0;
        int minX=width,minY=height,maxX=0,maxY=0;
        int rowEnd=Math.min(h,height-y), colStart=Math.max(0,-x), colEnd=Math.min(w,width-x);
        for(int row=Math.max(0,-y);row<rowEnd;row++) {
            int py=y+row, rowKey=(py/TILE)*columns;
            for(int col=colStart,g=row*w+colStart,i=py*width+x+colStart;col<colEnd;col++,g++,i++) {
                int gray=grays[g];
                if(gray<0) continue;
                if(gray>255) throw new IllegalArgumentException("Invalid tone");
                int tone=tones[i]&255,a=alpha[i]&255;
                // Transparent tones keep the coverage already there (but darkness alone on a shaded paper); opaque paint starts from the gesture's.
                int found=floors||glaze&&tone==0?0:a;
                if(coverages!=null&&!floors) {
                    int bx=x+col,key=rowKey+bx/TILE;
                    if(key!=baseKey) { baseKey=key; base=captured[key]?baseTile(key):null; baseWidth=Math.min(TILE,width-(bx/TILE)*TILE); }
                    if(base!=null) found=base[base.length/2+(py%TILE)*baseWidth+bx%TILE]&255;
                }
                int coverage=found>255-gray?found:255-gray;
                if(coverages!=null&&coverages[g]>coverage) {
                    coverage=coverages[g];
                    if(coverage>255) throw new IllegalArgumentException("Invalid coverage");
                }
                // Without a wider coverage, a pixel already showing this tone is left as it is (but for its share).
                int raw=coverage==a&&onWhite(tone,a)==gray?tone:rawTone(gray,coverage);
                // Erasable: the share this write leaves, from what the pixel kept as the gesture found it; a pixel left
                // as it is by paint that is all erasable keeps its share.
                int kept=-1;
                if(share!=null&&(permanent>0||tone!=raw||a!=coverage)) {
                    int bx=x+col,key=rowKey+bx/TILE;
                    if(!captured[key]) kept=erasableShare(tone,a,share[i]&255,255-gray,coverage,owns==null?-1:owns[g]);
                    else {
                        if(key!=keptKey) { keptKey=key; keptBase=captureOf(key); keptShare=shareOf(key); keptWidth=Math.min(TILE,width-(bx/TILE)*TILE); }
                        int o=(py&(TILE-1))*keptWidth+(bx&(TILE-1)), s0=keptShare==null?255:keptShare[o]&255;
                        // Nothing kept as found and nothing laid to keep: all erasable, as most of a page is.
                        kept=permanent==0&&s0==255?255:erasableShare(keptBase[o]&255,keptBase[keptBase.length/2+o]&255,s0,255-gray,coverage,owns==null?-1:owns[g]);
                    }
                }
                if(tone==raw&&a==coverage&&(kept<0||kept==(share[i]&255))) continue;
                int px=x+col,key=rowKey+px/TILE;
                if(key!=lastKey) { lastKey=key; capture(key); }
                if(!written) { written=true; writable(); }
                if(kind!=null) kind[i]=laid(i,a);
                // The masking fluid shows only as its hatching (render): a write there changes the page shown only where that comes or goes.
                boolean hatched=fluid&&liftable(i)>=32;
                tones[i]=(byte)raw; alpha[i]=(byte)coverage;
                if(kept>=0) share[i]=(byte)kept;
                if(fluid&&hatched==liftable(i)>=32) continue;
                if(px<minX) minX=px; if(px>maxX) maxX=px; if(py<minY) minY=py; if(py>maxY) maxY=py;
            }
        }
        if(minX<=maxX) markDirty(minX,minY,maxX+1,maxY+1);
    }
    private static int rawTone(int onWhite,int coverage) {
        return coverage==0?255:Math.max(0,Math.min(255,(onWhite*255-255*(255-coverage)+coverage/2)/coverage));
    }
    void glazeTone(int x,int y,int gray) { glazeTone(x,y,gray,255); }
    /** @param edge anti-aliased share of the pixel the glaze covers, out of 255 */
    void glazeTone(int x,int y,int gray,int edge) {
        if(gray==255) return;
        int pixel=strokeBasePixel(x,y),coverage=pixel&255;
        int result=transparentTone(onWhite(pixel>>>8,coverage),gray);
        coverage=255-transparentTone(255-coverage,gray);
        cover(x,y,rawTone(result,coverage),coverage,edge);
    }
    /** Moves the gesture's base tone toward {@code gray} by {@code strength}, covering at least that much. */
    void mixTone(int x,int y,int gray,float strength) { mixTone(x,y,gray,strength,255); }
    void mixTone(int x,int y,int gray,float strength,int edge) {
        int base=strokeBaseTone(x,y),result=Math.round(base+(gray-base)*strength);
        int coverage=Math.max(edge==255?opacity(x,y):strokeBasePixel(x,y)&255,Math.max(Math.round(255*strength),255-result));
        cover(x,y,rawTone(result,coverage),coverage,edge);
    }
    /** The active layer's pixel as this gesture found it: raw tone in the high byte, coverage in the low byte. */
    private int strokeBasePixel(int x,int y) {
        int key=(y/TILE)*columns()+x/TILE,i=y*width+x;
        if(!captured[key]) return (tones[i]&255)<<8|(alpha[i]&255);
        byte[] tile=baseTile(key); int w=Math.min(TILE,width-(x/TILE)*TILE),offset=(y%TILE)*w+x%TILE;
        return (tile[offset]&255)<<8|(tile[tile.length/2+offset]&255);
    }
    /** The coverage pixel {@code x, y} keeps when erased, as this gesture found it ({@link Layer#share}); 0 without a share. */
    private int keptFound(int x,int y) { return share==null?0:kept(strokeBasePixel(x,y)&255,shareFound(x,y)); }
    /** Pixel {@code x, y}'s share as this gesture found it; 255 without a share. */
    private int shareFound(int x,int y) {
        if(share==null) return 255;
        int key=(y/TILE)*columns()+x/TILE;
        if(!captured[key]) return share[y*width+x]&255;
        byte[] s=shareOf(key); int w=Math.min(TILE,width-(x/TILE)*TILE);
        return s==null?255:s[(y%TILE)*w+x%TILE]&255;
    }
    /** Tile {@code key}'s shares as this gesture found it, or null (a band's own tile: see {@link #foundShares}). */
    private byte[] shareOf(int key) { return foundShares[key]; }
    /**
     * The share a pixel takes when this gesture leaves it at darkness {@code dark} (over white) and coverage
     * {@code coverage}, found at raw tone {@code raw0}, coverage {@code a0} and share {@code s0}: what it kept as found and the permanent part of {@code own}, the
     * darkness this write lays of its own over white paper (0 to 255; negative: the darkening since found, or for a
     * pixel left lighter, paint of this gesture's taken away, its permanent share from what was kept). In darkness
     * over white, so exact for transparent paint whatever the raw tone: kept {@code k' = own·(1 − e) +
     * k0·(1 − own·(1 − e))}, and the share what the pixel's darkness holds beyond it. A pixel the eraser leaves keeps
     * its raw tone at its coverage times {@code 1 − share}.
     */
    private int erasableShare(int raw0,int a0,int s0,int dark,int coverage,int own) {
        // Nothing kept and nothing laid to keep: all erasable, as most of a page is.
        if(coverage==0||permanent==0&&s0==255) return 255;
        int dark0=s0==255&&own>=0?0:255-onWhite(raw0,a0);
        int keep=dark0==0||s0==255?0:s0==0?dark0:(dark0*(255-s0)+127)/255;
        if(permanent>0) {
            if(own<0&&dark<dark0) keep=Math.max(0,keep-((dark0-dark)*permanent+127)/255);
            else {
                if(own<0) own=dark0>=255?0:((dark-dark0)*255+(255-dark0)/2)/(255-dark0);
                int laid=permanent==255?own:(own*permanent+127)/255;
                keep=keep==0?laid:laid+(keep*(255-laid)+127)/255;
            }
        }
        // Paint that lays no darkness (white): the share is this gesture's, or as found.
        if(dark==0) return permanent>0?255-permanent:a0==0?255:s0;
        return keep<=0?255:keep>=dark?0:255-divide(keep*255+dark/2,dark);
    }
    /** A captured tile as this gesture found it. Captures never change mid-gesture, so the last one is kept at hand. */
    private byte[] baseTile(int key) {
        if(bands) return captureOf(key);
        if(lastBaseKey!=key||lastBaseTile==null) { lastBaseKey=key; lastBaseTile=before.get(key); }
        return lastBaseTile;
    }
    /** Tile {@code key} as this gesture found it, or null if not captured. */
    private byte[] captureOf(int key) {
        // A band's own tile is captured on its own thread: no lock ({@link #foundTiles}).
        return foundTiles[key];
    }
    /**
     * Lays what full contact leaves ({@code gray} at {@code coverage}) on {@code edge}/255 of the pixel,
     * mixing premultiplied with the gesture's base. A partial edge never weakens a stronger contact
     * from an overlapping dab, so smooth strokes do not darken where stamps overlap.
     */
    private void cover(int x,int y,int gray,int coverage,int edge) {
        if(edge>=255) { setPixel(x,y,gray,coverage); return; }
        if(edge<=0||x<0||y<0||x>=width||y>=height) return;
        int base=strokeBasePixel(x,y),baseTone=base>>>8,baseAlpha=base&255,i=y*width+x;
        float t=edge/255f,a=baseAlpha+(coverage-baseAlpha)*t;
        float pigment=baseTone*baseAlpha+(gray*coverage-baseTone*baseAlpha)*t;
        int resultAlpha=Math.round(a),result=resultAlpha==0?255:Math.max(0,Math.min(255,Math.round(pigment/a)));
        int from=onWhite(baseTone,baseAlpha);
        int reach=Math.abs(onWhite(result,resultAlpha)-from)+Math.abs(resultAlpha-baseAlpha);
        if(reach>Math.abs(tone(x,y)-from)+Math.abs(opacity(x,y)-baseAlpha)) setPixel(x,y,result,resultAlpha);
    }
    /** Lays {@code gray} at {@code coverage} over the pixel as it is now, as a separate sheet would lie over it. */
    void overTone(int x,int y,int gray,int coverage) {
        if(x<0||y<0||x>=width||y>=height) return;
        int i=y*width+x, laid=sheet(gray,coverage,tones[i]&255,alpha[i]&255);
        if(laid!=0) setPixel(x,y,laid>>>8,laid&255);
    }
    /** {@code gray} at {@code coverage} over a pixel of {@code tone} at {@code under}, as {@link #overTone} lays it: tone << 8 | coverage, 0 when bare. */
    static int sheet(int gray,int coverage,int tone,int under) {
        int kept=under*(255-coverage), a=coverage+(kept+127)/255;
        if(a==0) return 0;
        return Math.max(0,Math.min(255,(gray*coverage*255+tone*kept+a*255/2)/(a*255)))<<8|Math.min(255,a);
    }
    /** Strongest pencil contact in this gesture, carrying the chosen pigment and its coverage. */
    void pencilTone(int x,int y,int gray,float strength) { pencilTone(x,y,gray,strength,1); }
    /** @param depth how far from white toward {@code gray} this pixel's paper tooth can ever darken, across gestures */
    void pencilTone(int x,int y,int gray,float strength,float depth) {
        int base=strokeBaseTone(x,y),coverage=strokeBasePixel(x,y)&255;
        int target=Math.round(base+(gray-base)*strength),current=tone(x,y);
        if(gray<base) target=Math.max(target,Math.min(base,Math.round(255+(gray-255)*depth)));
        int resultAlpha=Math.round(coverage+(255-coverage)*strength);
        if((gray<base&&target>current)||(gray>base&&target<current)) return;
        if(target==current&&resultAlpha<=opacity(x,y)) return;
        setPixel(x,y,rawTone(target,resultAlpha),resultAlpha);
    }
    /** Source-over toward {@code gray} on the current pixel, always moving at least one step, like {@link #eraseTone}. */
    void sprayTone(int x,int y,int gray,float strength) {
        if(x<0||y<0||x>=width||y>=height||strength<=0) return;
        strength=Math.min(1,strength);
        int i=y*width+x,tone=tones[i]&255,coverage=alpha[i]&255;
        float a=strength*255+coverage*(1-strength);
        int result=Math.round((gray*strength*255+tone*coverage*(1-strength))/a);
        if(result==tone&&tone!=gray&&coverage>0) result+=gray>tone?1:-1;
        setPixel(x,y,result,Math.min(255,Math.max(coverage+1,Math.round(a))));
    }
    /** Remove coverage from the gesture's original layer, retaining its pigment. */
    void eraseFromBase(int x,int y,float strength) {
        int key=(y/TILE)*columns()+x/TILE,i=y*width+x,coverage=alpha[i]&255;
        if(captured[key]) coverage=strokeBasePixel(x,y)&255;
        int result=Math.max(keptFound(x,y),Math.round(coverage*(1-Math.max(0,Math.min(1,strength)))));
        if(result<opacity(x,y)) setPixel(x,y,tones[i]&255,result);
    }
    /** Transparent color endpoint for fills and shapes; shades remain 0–255. */
    static final int ERASE = -1;
    void paintTone(int x,int y,int gray) { paintTone(x,y,gray,255); }
    /** Replace pigment and coverage; transparent fill endpoints must not paint white. */
    void paintTone(int x,int y,int gray,int coverage) { setPixel(x,y,gray,coverage); }
    /** Paint or erase a scanline for geometric shapes; capture undo tiles once, then fill contiguous pixels. */
    void paintSpan(int x, int end, int y, int gray) {
        if(!editing) throw new IllegalStateException("No active gesture");
        if(gray<ERASE || gray>255) throw new IllegalArgumentException("Invalid pixel");
        x=Math.max(0,x);end=Math.min(width,end);
        if(y<0 || y>=height || x>=end) return;
        if(mask!=null&&bound!=mask) {
            // Masking fluid shields some of these pixels; each one takes its share.
            for(int px=x;px<end;px++) if(gray==ERASE) setPixel(px,y,255,0); else setPixel(px,y,gray,255);
            return;
        }
        writable();
        int rowKey=(y/TILE)*columns();
        for(int column=x/TILE;column<=(end-1)/TILE;column++) {
            int key=rowKey+column;
            capture(key);
        }
        Arrays.fill(tones,y*width+x,y*width+end,(byte)(gray==ERASE?255:gray));
        Arrays.fill(alpha,y*width+x,y*width+end,(byte)(gray==ERASE?0:255));
        left=Math.min(left,x);top=Math.min(top,y);right=Math.max(right,end);bottom=Math.max(bottom,y+1);
    }
    /** Restores a span to how this gesture found it, keeping the undo capture: a resizing preview's removed pixels, or a wild ink tail the taper took away. */
    void restoreSpan(int x,int end,int y) {
        if(!editing) throw new IllegalStateException("No active gesture");
        x=Math.max(0,x);end=Math.min(width,end);
        if(y<0 || y>=height || x>=end)return;
        writable();
        for(int column=x/TILE;column<=(end-1)/TILE;column++) {
            int key=(y/TILE)*columns()+column;
            byte[] tile=captureOf(key);
            if(tile==null)continue;
            int tileX=column*TILE,tileWidth=Math.min(TILE,width-tileX);
            int start=Math.max(x,tileX),stop=Math.min(end,tileX+tileWidth);
            int offset=(y%TILE)*tileWidth+start-tileX;
            System.arraycopy(tile,offset,tones,y*width+start,stop-start);
            System.arraycopy(tile,tile.length/2+offset,alpha,y*width+start,stop-start);
            if(share!=null) { byte[] s=shareOf(key); if(s!=null) System.arraycopy(s,offset,share,y*width+start,stop-start); else Arrays.fill(share,y*width+start,y*width+stop,(byte)255); }
            if(print!=null) { byte[] p=foundPrints[key]; if(p!=null) System.arraycopy(p,offset,print,y*width+start,stop-start); else Arrays.fill(print,y*width+start,y*width+stop,(byte)255); }
            if(kind!=null) { byte[] k=foundKinds[key]; if(k!=null) System.arraycopy(k,offset,kind,y*width+start,stop-start); else Arrays.fill(kind,y*width+start,y*width+stop,(byte)0); }
        }
        markDirty(x,y,end,y+1);
    }
    private void setPixel(int x,int y,int gray,int coverage) { setPixel(x,y,gray,coverage,-1); }
    /** @param own the darkness this write lays of its own, as {@code owns} in {@link #setTones(int, int, int, int, int[], int[])} */
    private void setPixel(int x,int y,int gray,int coverage,int own) {
        if (!editing) throw new IllegalStateException("No active gesture");
        if(gray<0||gray>255||coverage<0||coverage>255) throw new IllegalArgumentException("Invalid pixel");
        if(x<0||y<0||x>=width||y>=height) return;
        int i=y*width+x;
        int shield=bound==mask?0:shield(i);
        if(shield==255) return;
        if(shield>0) {
            // Only the unmasked share of the change reaches the paint the gesture found.
            int base=strokeBasePixel(x,y),baseTone=base>>>8,baseAlpha=base&255;
            float t=1-shield/255f,a=baseAlpha+(coverage-baseAlpha)*t;
            float pigment=baseTone*baseAlpha+(gray*coverage-baseTone*baseAlpha)*t;
            coverage=Math.round(a); gray=coverage==0?255:Math.max(0,Math.min(255,Math.round(pigment/a)));
            if(own>0) own=own*(255-shield)/255;
        }
        if(coverage==0) gray=255;
        store(x,y,gray,coverage,own);
    }
    /**
     * Writes a pixel on the page, unless it already holds this, capturing its tile for Undo first; on a layer with a
     * {@link Layer#share}, its share too ({@link #erasableShare}, {@code own} as there).
     */
    private void store(int x,int y,int gray,int coverage,int own) {
        int i=y*width+x, kept=-1;
        if(share!=null&&(permanent>0||(tones[i]&255)!=gray||(alpha[i]&255)!=coverage)) { int found=strokeBasePixel(x,y); kept=erasableShare(found>>>8,found&255,shareFound(x,y),255-onWhite(gray,coverage),coverage,own); }
        if((tones[i]&255)==gray&&(alpha[i]&255)==coverage&&(kept<0||kept==(share[i]&255))) return;
        int key=(y/TILE)*columns()+x/TILE;
        capture(key);
        writable();
        if(kind!=null) kind[i]=laid(i,alpha[i]&255);
        tones[i]=(byte)gray; alpha[i]=(byte)coverage;
        if(kept>=0) share[i]=(byte)kept;
        markDirty(x,y,x+1,y+1);
    }
    /** Keeps a tile as this gesture found it, for Undo, before its first change. */
    private void capture(int key) {
        if(captured[key]) return;
        byte[] tile=shared(copyTile(key),true), kept=share==null?null:shared(copyPlane(share,key),false), printed=print==null?null:shared(copyPlane(print,key),false), kinded=kind==null?null:shared(copyPlane(kind,key),false);
        foundTiles[key]=tile; foundShares[key]=kept; foundPrints[key]=printed; foundKinds[key]=kinded;
        if(bands||sharing) synchronized(before) { before.put(key,tile); if(kept!=null) beforeShare.put(key,kept); if(printed!=null) beforePrint.put(key,printed); if(kinded!=null) beforeKind.put(key,kinded); captured[key]=true; }
        else { before.put(key,tile); if(kept!=null) beforeShare.put(key,kept); if(printed!=null) beforePrint.put(key,printed); if(kinded!=null) beforeKind.put(key,kinded); captured[key]=true; }
    }
    /** Grows the area to render again by {@code left..right, top..bottom}, right and bottom exclusive. */
    private void markDirty(int l,int t,int r,int b) {
        if(bands) synchronized(before) { grow(l,t,r,b); } else grow(l,t,r,b);
    }
    private void grow(int l,int t,int r,int b) { left=Math.min(left,l); top=Math.min(top,t); right=Math.max(right,r); bottom=Math.max(bottom,b); }
    /** Apply opaque logical raster pixels, generated by the accepted Android circle rasterizer; anti-aliased edges cover partly. */
    void paintMask(int[] mask, int stride, int x, int y, int w, int h, int gray) {
        for (int row = 0; row < h; row++) for (int col = 0; col < w; col++) {
            int edge = mask[row * stride + col] >>> 24;
            if (edge == 255) paintTone(x + col, y + row, gray);
            else if (edge != 0) cover(x + col, y + row, gray, 255, edge);
        }
    }
    /** Half-strength multiply keeps even black translucent; white is clear water. */
    static int transparentTone(int base, int gray) { return (base * (255 + gray) + 255) / 510; }
    /** One glaze per gesture, independent of overlapping interpolation stamps. */
    void transparentMask(int[] mask, int stride, int x, int y, int w, int h, int gray) {
        if (gray != glazedGray) { glazed.clear(); glazedGray = gray; }
        long[] bits = null; int bitsKey = -1;
        for (int row = 0; row < h; row++) for (int col = 0; col < w; col++) {
            int px = x + col, py = y + row;
            int edge = mask[row * stride + col] >>> 24;
            if (edge == 0 || px < 0 || py < 0 || px >= width || py >= height) continue;
            // A partly covered edge may yet be covered more; a fully glazed pixel is final.
            if (edge < 255) { glazeTone(px, py, gray, edge); continue; }
            int key = (py / TILE) * columns() + px / TILE;
            if (key != bitsKey) {
                bitsKey = key; bits = glazed.get(key);
                if (bits == null) glazed.put(key, bits = new long[TILE]);
            }
            long bit = 1L << (px % TILE);
            if ((bits[py % TILE] & bit) != 0) continue;
            bits[py % TILE] |= bit;
            glazeTone(px, py, gray, 255);
        }
    }
    /** Darken to the selected logical gray; repeated passes retain the same shade. */
    void flatWashMask(int[] mask, int stride, int x, int y, int w, int h, int gray) {
        for (int row = 0; row < h; row++) for (int col = 0; col < w; col++) {
            int px = x + col, py = y + row;
            if ((mask[row * stride + col] >>> 24) != 0
                    && px >= 0 && py >= 0 && px < width && py < height)
                setTone(px, py, Math.min(gray, tone(px, py)));
        }
    }
    /** Deposit only the shade's black dots; gaps leave the underlying tones intact. */
    void washMask(int[] mask, int stride, int x, int y, int w, int h, int gray) {
        for (int row = 0; row < h; row++) for (int col = 0; col < w; col++) {
            if ((mask[row * stride + col] >>> 24) != 0
                    && DotPattern.pixel(gray, x + col, y + row) == 0xff000000)
                setTone(x + col, y + row, 0);
        }
    }
    boolean finish() { return finish(false); }
    /** Animation belongs to the preceding wet stroke, not a separate Undo step. */
    boolean finishContinuation() { return finish(true); }
    /**
     * Finishes this gesture as part of the edit before it, so Undo and Redo treat both as one
     * step. A change to two layers at once, such as erasing ink and wash together, is two
     * gestures because a gesture binds one layer. Call it only when the previous gesture's
     * finish reported a change; otherwise this links to an unrelated earlier edit.
     */
    boolean finishWithPrevious() {
        boolean changed = finish(false);
        if (changed && undo.size() > 1) undo.peekLast().linked = true;
        return changed;
    }
    private boolean finish(boolean continuation) {
        if (!editing) return false;
        // Inkwash: a fenced box (a locked section) is put back as the gesture found it, in each tile the gesture wrote.
        if (fence.length > 0) for (int key : new ArrayList<>(before.keySet())) for (int[] b : fence) {
            int tx = key % columns() * TILE, ty = key / columns() * TILE;
            int x0 = Math.max(b[0], tx), x1 = Math.min(b[2], Math.min(width, tx + TILE)), y1 = Math.min(b[3], Math.min(height, ty + TILE));
            if (x0 < x1) for (int y = Math.max(b[1], ty); y < y1; y++) restoreSpan(x0, x1, y);
        }
        editing = false;lastBaseTile=null;lastBaseKey=-1;glazed.clear();
        ArrayList<TileEdit> tiles = new ArrayList<>();
        for (Map.Entry<Integer, byte[]> entry : before.entrySet()) {
            captured[entry.getKey()] = false; foundTiles[entry.getKey()] = null; foundShares[entry.getKey()] = null; foundPrints[entry.getKey()] = null; foundKinds[entry.getKey()] = null;
            byte[] kindBefore = beforeKind.get(entry.getKey()), kindAfter = kind == null ? null : shared(copyPlane(kind, entry.getKey()), false);
            byte[] after = shared(copyTile(entry.getKey()), true), shareBefore = beforeShare.get(entry.getKey()), shareAfter = share == null ? null : shared(copyPlane(share, entry.getKey()), false);
            byte[] printBefore = beforePrint.get(entry.getKey()), printAfter = print == null ? null : shared(copyPlane(print, entry.getKey()), false);
            // A stroke that leaves the print tone as it was (any but a screened one) keeps one copy of it, not two.
            if (printBefore != null && Arrays.equals(printBefore, printAfter)) printAfter = printBefore;
            if (kindBefore != null && Arrays.equals(kindBefore, kindAfter)) kindAfter = kindBefore;
            if (!Arrays.equals(entry.getValue(), after) || !samePlane(shareBefore, shareAfter) || !samePlane(printBefore, printAfter) || !samePlane(kindBefore, kindAfter, (byte)0))
                tiles.add(new TileEdit(entry.getKey(), entry.getValue(), after, shareBefore, shareAfter, printBefore, printAfter, kindBefore, kindAfter));
        }
        before.clear(); beforeShare.clear(); beforePrint.clear(); beforeKind.clear();
        Layer edited=bound; bind(selected);
        if (tiles.isEmpty()) return false;
        boolean linked = false;
        if (continuation && !undo.isEmpty() && redo.isEmpty() && undo.peekLast().layer==edited) {
            Edit previous = undo.removeLast(); historyBytes -= previous.bytes; linked = previous.linked;
            LinkedHashMap<Integer, TileEdit> merged = new LinkedHashMap<>();
            for (TileEdit tile : previous.tiles) merged.put(tile.key, tile);
            for (TileEdit tile : tiles) {
                TileEdit old = merged.get(tile.key);
                merged.put(tile.key, old == null ? tile : new TileEdit(tile.key, old.before, tile.after, old.shareBefore, tile.shareAfter, old.printBefore, tile.printAfter, old.kindBefore, tile.kindAfter));
            }
            tiles = new ArrayList<>();
            for (TileEdit tile : merged.values())
                if (!Arrays.equals(tile.before, tile.after) || !samePlane(tile.shareBefore, tile.shareAfter) || !samePlane(tile.printBefore, tile.printAfter) || !samePlane(tile.kindBefore, tile.kindAfter, (byte)0)) tiles.add(tile);
        }
        for (Edit edit : redo) historyBytes -= edit.bytes;
        if (!redo.isEmpty()) log = log.upTo(top());
        redo.clear();
        if (!tiles.isEmpty()) { Edit edit = new Edit(tiles,edited); edit.linked = linked; undo.addLast(edit); historyBytes += edit.bytes; }
        trimHistory();
        return true;
    }
    boolean canUndo() { return !locked() && !undo.isEmpty(); }
    /** The bytes Undo and Redo hold (shared uniform tiles count none: {@link #stepBytes}). */
    int historyBytes() { return historyBytes; }
    /** How many steps Undo holds: a continuation that leaves its step with nothing changed removes it (a Float bath's {@code FloatWater} keeps count). */
    int undoSteps() { return undo.size(); }
    /** The oldest steps dropped past {@link #HISTORY_BYTES}; a page-wide one only past {@link #PAGE_STEP_BYTES}. */
    private void trimHistory() {
        while (historyBytes > HISTORY_BYTES && undo.size() > 1 && (undo.peekFirst().previous == null || historyBytes > PAGE_STEP_BYTES)) historyBytes -= undo.removeFirst().bytes;
    }
    /** Inkwash: no undo or redo left; a page turned away from (the owner, 2026-10-25: its undo can go, the fast switch back stays). */
    void dropHistory() { undo.clear(); redo.clear(); historyBytes = 0; }
    void trimHistory(int budget) {
        while(historyBytes>budget&&undo.size()>1)historyBytes-=undo.removeFirst().bytes;
        while(historyBytes>budget&&!redo.isEmpty())historyBytes-=redo.removeFirst().bytes;
    }
    /** Clear all disconnected marks as one undoable edit, retaining canvas dimensions. */
    boolean clear() {
        begin(false);
        for(int key=0;key<captured.length;key++) {
            byte[] tile=copyTile(key), printed=print==null?null:copyPlane(print,key), kinded=kind==null?null:copyPlane(kind,key); boolean marked=false;
            for(int i=tile.length/2;i<tile.length;i++) if(tile[i]!=0) { marked=true; break; }
            if(printed!=null) for(int i=0;i<printed.length&&!marked;i++) marked=printed[i]!=(byte)255;
            if(marked) {
                before.put(key,tile); if(share!=null) beforeShare.put(key,foundShares[key]=copyPlane(share,key)); foundTiles[key]=tile; captured[key]=true;
                if(printed!=null) beforePrint.put(key,foundPrints[key]=printed);
                if(kinded!=null) beforeKind.put(key,foundKinds[key]=kinded);
            }
        }
        if(!before.isEmpty()) {
            Arrays.fill(tones,(byte)255); Arrays.fill(alpha,(byte)0); if(share!=null) Arrays.fill(share,(byte)255); if(print!=null) Arrays.fill(print,(byte)255); markAllDirty();
        }
        return finish();
    }
    /** Clear the current page, including hidden layers, as one structural undo step. */
    boolean clearAllLayers() {
        idle(); boolean marked=hasMask();
        for(Layer layer:layers) marked|=layer.marked();
        if(!marked)return false;
        StackState old=new StackState(this); blankLayers(); stackEdit(old); logClear(); return true;
    }
    /**
     * Inkwash: Clear page ({@link PageClear}): every layer's marks, hidden ones too, the masking fluid, the magnets and
     * where a cover's design was rubbed off (it comes back), as one Undo step; the layers themselves (names, order, visibility, opacity) and the paper's shade stay. The step holds
     * the page's old layers by reference, never copied, so Undo puts back the very arrays. False, changing nothing, on
     * an {@link #unmarked} page.
     */
    boolean clearPage() {
        idle(); if(unmarked()) return false;
        StackState old=new StackState(this); blankLayers(); magnets=Magnets.NONE; wear=null; repaper(); stackEdit(old); logClear(); return true;
    }
    /** Every layer swapped for a blank one like it, and no masking fluid. */
    private void blankLayers() {
        mask=null;
        for(int i=0;i<layers.size();i++) {
            Layer prior=layers.get(i),blank=new Layer(prior.name,prior.visible,paper(width,height),new byte[width*height]);
            blank.opacity=prior.opacity; layers.set(i,blank);
        }
        selectLayer(active);
    }
    void cancel() {
        if (!editing) return;
        // A shared base's reader sees each tile captured or as restored, never between.
        synchronized (before) {
            for (Map.Entry<Integer, byte[]> tile : before.entrySet()) {
                restoreTile(tile.getKey(), tile.getValue(), beforeShare.get(tile.getKey()), beforePrint.get(tile.getKey()), beforeKind.get(tile.getKey())); captured[tile.getKey()] = false;
                foundTiles[tile.getKey()] = null; foundShares[tile.getKey()] = null; foundPrints[tile.getKey()] = null; foundKinds[tile.getKey()] = null;
            }
            before.clear(); beforeShare.clear(); beforePrint.clear(); beforeKind.clear();
        }
        lastBaseTile=null; lastBaseKey=-1; glazed.clear(); editing = false;
        bind(selected);
    }
    boolean canRedo() { return !locked() && !redo.isEmpty(); }
    boolean undo() { return travel(undo, redo, false); }
    boolean redo() { return travel(redo, undo, true); }
    private boolean travel(ArrayDeque<Edit> source, ArrayDeque<Edit> target, boolean forward) {
        if (editing) throw new IllegalStateException("Finish the current gesture first");
        // Locked means locked (Timed Pages): a page whose time ran out has no Undo and no Redo.
        if (locked() || source.isEmpty()) return false;
        Edit edit = source.removeLast();
        if(edit.magnetsAfter!=null) { Magnets to=forward?edit.magnetsAfter:edit.magnetsBefore; magnetAreas.addAll(Magnets.changed(magnets,to)); magnets=to; revision++; }
        else if(edit.layer==null) { (forward?edit.next:edit.previous).restore(this); revision++; markAllDirty(); }
        else {
            int index=ownerOf(edit.layer);
            // Any other layer is masking fluid, laid since the page last had none. Inkwash: a plane's layer is selected, the plane read.
            if(index<0) { mask=edit.layer; bind(mask); } else { selectLayer(index); selected=edit.layer; bind(selected); }
            for (TileEdit tile : edit.tiles) restoreTile(tile.key, forward ? tile.after : tile.before, forward ? tile.shareAfter : tile.shareBefore, forward ? tile.printAfter : tile.printBefore, forward ? tile.kindAfter : tile.kindBefore);
            if(index<0) bind(selected);
        }
        target.addLast(edit);
        // Linked edits move together: undo continues past a linked edit; redo takes those linked after it.
        if (!forward && edit.linked && !source.isEmpty()) travel(source, target, false);
        if (forward && !source.isEmpty() && source.peekLast().linked) travel(source, target, true);
        return true;
    }
    // ---- Inkwash: Fading and Lemon Juice (FadingInk) ----

    /** {@link #fade}'s answers: some pixel changed; some mark is done (gone, or fully dark) and left the page's list. */
    static final int FADED = 1, FADE_ENDED = 2;
    /** The bytes of fading marks a page holds at most, per page pixel: about one page-wide mark. */
    static final int MOST_FADING = 4;
    /** Beside a pixel's tone and coverage in {@link #stepFade}: it is back to one never painted. */
    private static final int BLANK = 1 << 16;
    /** Whether some mark on the page still changes by the clock. */
    boolean fading() { return !fades.isEmpty(); }
    /** The marks changing by the clock, oldest first (for the checks and the file). */
    java.util.List<FadingInk.Mark> fades() { return java.util.Collections.unmodifiableList(fades); }
    /** The marks read from a file ({@link DocumentCodec}): no Undo step. */
    void placeFades(java.util.List<FadingInk.Mark> read) { fades.clear(); fades.addAll(read); }
    /** The clock time the next mark should step, or {@link Long#MAX_VALUE} when none changes. */
    long nextFade() { long t = Long.MAX_VALUE; for (FadingInk.Mark m : fades) t = Math.min(t, m.next()); return t; }
    /**
     * The tiles every Undo step since {@code mark} ({@link #lastStep} before them; null: from the first) changed on a
     * layer of the page become a mark changing by the clock from {@code now} ({@code spec}: Fading or Lemon Juice), one a
     * layer, as they were before the first and as the last left them; Lemon Juice shows its faint trace at once. Where a
     * mark of the same kind still changing lay under it, the new one changes from what lay under that (fresh fading ink
     * wets the old again). The page keeps at most {@link #MOST_FADING} bytes a pixel of them: past it the oldest ends now.
     */
    void fadeSince(Object mark, FadingInk.Spec spec, long now) {
        idle();
        ArrayList<Edit> since = new ArrayList<>();
        for (java.util.Iterator<Edit> i = undo.descendingIterator(); i.hasNext(); ) { Edit e = i.next(); if (e == mark) break; since.add(0, e); }
        LinkedHashMap<Layer, LinkedHashMap<Integer, byte[][]>> byLayer = new LinkedHashMap<>();
        for (Edit e : since) {
            if (e.tiles == null || ownerOf(e.layer) < 0) continue;
            LinkedHashMap<Integer, byte[][]> tiles = byLayer.get(e.layer);
            if (tiles == null) byLayer.put(e.layer, tiles = new LinkedHashMap<>());
            for (TileEdit t : e.tiles) { byte[][] had = tiles.get(t.key); tiles.put(t.key, new byte[][]{had != null ? had[0] : t.before, t.after}); }
        }
        for (Map.Entry<Layer, LinkedHashMap<Integer, byte[][]>> on : byLayer.entrySet()) {
            int n = on.getValue().size(), j = 0;
            int[] keys = new int[n];
            byte[][] before = new byte[n][], after = new byte[n][];
            for (Map.Entry<Integer, byte[][]> t : on.getValue().entrySet()) { keys[j] = t.getKey(); before[j] = t.getValue()[0]; after[j] = t.getValue()[1]; j++; }
            fades.add(rewet(new FadingInk.Mark(on.getKey(), width, height, spec.lemon, spec.ms, now, -1, keys, before, after)));
            stepFade(fades.size() - 1, now, false);
        }
        long held = 0;
        for (FadingInk.Mark m : fades) held += m.bytes();
        while (held > MOST_FADING * (long) width * height && fades.size() > 1) { held -= fades.get(0).bytes(); stepFade(0, fades.get(0).end(), false); }
    }
    /** {@code m} changing from what lay under a mark of its kind still changing, where it lays over that one's pixels as they show. */
    private FadingInk.Mark rewet(FadingInk.Mark m) {
        byte[][] before = m.before;
        for (FadingInk.Mark old : fades) {
            if (old.layer != m.layer || old.lemon != m.lemon || old.width != m.width || old.height != m.height) continue;
            HashMap<Integer, Integer> at = new HashMap<>();
            for (int j = 0; j < old.keys.length; j++) at.put(old.keys[j], j);
            for (int j = 0; j < m.keys.length; j++) {
                Integer o = at.get(m.keys[j]);
                if (o == null) continue;
                byte[] ob = old.before[o], oa = old.after[o];
                int half = ob.length / 2;
                for (int p = 0; p < half; p++) {
                    if (ob[p] == oa[p] && ob[half + p] == oa[half + p]) continue;
                    int v = FadingInk.mix(ob[p] & 255, ob[half + p] & 255, oa[p] & 255, oa[half + p] & 255, old.share(o, p, old.shown));
                    if ((before[j][p] & 255) != v >>> 8 || (before[j][half + p] & 255) != (v & 255)) continue;
                    if (before == m.before) before = before.clone();
                    if (before[j] == m.before[j]) before[j] = before[j].clone();
                    before[j][p] = ob[p]; before[j][half + p] = ob[half + p];
                }
            }
        }
        return before == m.before ? m : new FadingInk.Mark(m.layer, m.width, m.height, m.lemon, m.ms, m.born, m.shown, m.keys, before, m.after);
    }
    /**
     * Every mark due by clock time {@code now} steps there ({@link FadingInk}): each pixel still showing what the mark
     * last showed shows its share now, and so does every Undo step's copy of it on that layer, so Undo and Redo give the
     * mark as the clock has it, never one faded back. A mark done leaves the list (a fading one gone from the page and,
     * once saved, the file), and an Undo step it leaves changing nothing goes from the history. {@code shown} (or null)
     * runs after each mark that changed pixels, the area to render again being {@link #dirty}. Returns {@link #FADED}
     * and {@link #FADE_ENDED} as they happened. Not during a gesture.
     */
    int fade(long now, Runnable shown) {
        idle();
        int out = 0;
        for (int i = 0; i < fades.size(); ) {
            FadingInk.Mark m = fades.get(i);
            if (m.next() > now) { i++; continue; }
            int size = fades.size();
            if (stepFade(i, Math.min(now, m.end()), true)) { out |= FADED; if (shown != null) shown.run(); }
            if (fades.size() < size) out |= FADE_ENDED; else i++;
        }
        return out;
    }
    /**
     * Mark {@code i} steps to clock time {@code t}, leaving the list when done; whether any pixel of the page changed.
     * {@code drop}: an Undo step left changing nothing leaves the history (not at a stroke's end, where the stroke may
     * hold a step as its mark, {@link #lastStep}).
     */
    private boolean stepFade(int i, long t, boolean drop) {
        FadingInk.Mark m = fades.get(i);
        boolean done = t >= m.end(), changed = false;
        Layer layer = m.layer;
        ArrayList<Edit> steps = new ArrayList<>();
        for (Edit e : undo) if (e.layer == layer && e.tiles != null) steps.add(e);
        for (Edit e : redo) if (e.layer == layer && e.tiles != null) steps.add(e);
        java.util.HashSet<Edit> patched = new java.util.HashSet<>();
        int cols = m.columns();
        int[] at = new int[TILE * TILE], from = new int[TILE * TILE], to = new int[TILE * TILE];
        boolean onPage = m.width == width && m.height == height && ownerOf(layer) >= 0;
        for (int j = 0; j < m.keys.length && t != m.shown; j++) {
            int key = m.keys[j], x0 = key % cols * TILE, y0 = key / cols * TILE, w = Math.min(TILE, m.width - x0), half = m.before[j].length / 2, count = 0;
            byte[] b = m.before[j], a = m.after[j];
            int l = Integer.MAX_VALUE, tp = Integer.MAX_VALUE, r = -1, bt = -1;
            for (int p = 0; p < half; p++) {
                int t0 = b[p] & 255, a0 = b[half + p] & 255, t1 = a[p] & 255, a1 = a[half + p] & 255;
                if (t0 == t1 && a0 == a1) continue;
                int share = m.share(j, p, t), was = FadingInk.mix(t0, a0, t1, a1, m.share(j, p, m.shown)), next = FadingInk.mix(t0, a0, t1, a1, share);
                // Gone at last, a pixel back to one never painted holds no share or print tone either, one that looked gone sooner too.
                if (was == next && !(done && share == 0 && a0 == 0)) continue;
                at[count] = p; from[count] = was; to[count] = next | (a0 == 0 && share == 0 ? BLANK : 0); count++;
                int x = x0 + p % w, y = y0 + p / w, k = y * m.width + x;
                if ((layer.tones[k] & 255) != was >>> 8 || (layer.alpha[k] & 255) != (was & 255)) continue;
                if (!changed) { own(layer); revision++; changed = true; }
                layer.tones[k] = (byte) (next >>> 8); layer.alpha[k] = (byte) next;
                // Back to a pixel never painted: no share or print tone (255) either.
                if ((to[count - 1] & BLANK) != 0) { if (layer.share != null) layer.share[k] = (byte) 255; if (layer.print != null) layer.print[k] = (byte) 255; }
                l = Math.min(l, x); r = Math.max(r, x); tp = Math.min(tp, y); bt = Math.max(bt, y);
            }
            if (r >= 0 && onPage) grow(l, tp, r + 1, bt + 1);
            if (count == 0) continue;
            for (Edit e : steps) for (int k = 0; k < e.tiles.size(); k++) {
                TileEdit tile = e.tiles.get(k);
                if (tile.key != key || tile.before.length != 2 * half) continue;
                byte[][] before = patch(tile.before, tile.shareBefore, tile.printBefore, half, at, from, to, count), after = patch(tile.after, tile.shareAfter, tile.printAfter, half, at, from, to, count);
                if (before == null && after == null) continue;
                if (before == null) before = new byte[][]{tile.before, tile.shareBefore, tile.printBefore};
                if (after == null) after = new byte[][]{tile.after, tile.shareAfter, tile.printAfter};
                e.tiles.set(k, new TileEdit(key, before[0], after[0], before[1], after[1], before[2], after[2], tile.kindBefore, tile.kindAfter));
                patched.add(e);
            }
        }
        if (done) fades.remove(i); else fades.set(i, m.at(t));
        if (drop && !patched.isEmpty()) dropEmpty(patched);
        return changed && onPage;
    }
    /**
     * An Undo step's side of a tile ({@code tile}, its {@code shares} and {@code prints}, null where all 255) with each of
     * its {@code count} pixels {@code at} that holds {@code from} holding {@code to}, and one left with no coverage no
     * share or print tone: copies {tile, shares, prints}, or null if no pixel did.
     */
    private static byte[][] patch(byte[] tile, byte[] shares, byte[] prints, int half, int[] at, int[] from, int[] to, int count) {
        byte[][] out = null;
        for (int c = 0; c < count; c++) {
            int p = at[c];
            if ((tile[p] & 255) != from[c] >>> 8 || (tile[half + p] & 255) != (from[c] & 255)) continue;
            if (out == null) out = new byte[][]{tile.clone(), shares == null ? null : shares.clone(), prints == null ? null : prints.clone()};
            out[0][p] = (byte) (to[c] >>> 8); out[0][half + p] = (byte) to[c];
            if ((to[c] & BLANK) != 0) { if (out[1] != null) out[1][p] = (byte) 255; if (out[2] != null) out[2][p] = (byte) 255; }
        }
        return out;
    }
    /** Of {@code these} (stroke steps), those that now change nothing leave the history; the step linked to one that goes takes its link. */
    private void dropEmpty(java.util.Set<Edit> these) {
        ArrayList<Edit> time = new ArrayList<>(undo);
        int undone = time.size();
        for (java.util.Iterator<Edit> i = redo.descendingIterator(); i.hasNext(); ) time.add(i.next());
        boolean any = false;
        for (int k = 0; k < time.size(); k++) {
            Edit e = time.get(k);
            if (!these.contains(e) || !changesNothing(e)) continue;
            if (k + 1 < time.size() && time.get(k + 1) != null) time.get(k + 1).linked &= e.linked;
            time.set(k, null); historyBytes -= e.bytes; any = true;
        }
        if (!any) return;
        undo.clear(); redo.clear();
        for (int k = 0; k < undone; k++) if (time.get(k) != null) undo.addLast(time.get(k));
        for (int k = time.size() - 1; k >= undone; k--) if (time.get(k) != null) redo.addLast(time.get(k));
    }
    private static boolean changesNothing(Edit e) {
        for (TileEdit t : e.tiles) if (!Arrays.equals(t.before, t.after) || !samePlane(t.shareBefore, t.shareAfter) || !samePlane(t.printBefore, t.printAfter)) return false;
        return true;
    }

    /** Returns [left, top, right, bottom], without clearing it. */
    int[] dirty() { return right > left && bottom > top ? new int[]{left, top, right, bottom} : null; }
    void clearDirty() { left = width; top = height; right = bottom = 0; }
    void render(int[] pixels, int x, int y, int w, int h) {
        for (int row = 0; row < h; row++) for (int col = 0; col < w; col++) {
            int hatch = maskHatch(x + col, y + row);
            pixels[row * w + col] = hatch >= 0 ? (hatch == 0 ? 0xff000000 : 0xffffffff)
                    : DotPattern.pixel(compositeTone(x + col, y + row), x + col, y + row);
        }
    }
    private int columns() { return (width + TILE - 1) / TILE; }
    private byte[] copyTile(int key) {
        int x = key % columns() * TILE, y = key / columns() * TILE;
        int w = Math.min(TILE, width - x), h = Math.min(TILE, height - y);
        byte[] copy = new byte[w * h * 2];
        for (int row = 0; row < h; row++) {
            System.arraycopy(tones, (y + row) * width + x, copy, row * w, w);
            System.arraycopy(alpha, (y + row) * width + x, copy, w*h+row*w, w);
        }
        return copy;
    }
    /** Tile {@code key} of a byte a pixel {@code plane} (the bound layer's {@link Layer#share} or {@link Layer#print}), row by row. */
    /**
     * Inkwash: Undo's uniform tiles, one shared array each (never written: captures and steps are only read, a change
     * copies first). A fill over blank paper captures some 1,100 alike tiles a side (the owner, 2026-10-22: a page fill
     * made Undo slow and the app crash); shared, they hold nothing and {@link #stepBytes} counts them as 0. By length, each
     * half's value and whether the array is {@code halves} (a tile's tones and coverages) or one plane.
     */
    private static final java.util.concurrent.ConcurrentHashMap<Long, byte[]> UNIFORM = new java.util.concurrent.ConcurrentHashMap<>();
    private static long uniformKey(byte[] p, boolean halves) { return (long)p.length << 17 | (halves ? 1 << 16 : 0) | (p[0] & 255) << 8 | (p[halves ? p.length / 2 : 0] & 255); }
    /** {@code p}, or the shared array alike when it is uniform (each half for {@code halves}). */
    static byte[] shared(byte[] p, boolean halves) {
        if (p == null || p.length == 0) return p;
        int half = halves ? p.length / 2 : p.length;
        byte a = p[0], b = p[half % p.length];
        for (int i = 0; i < half; i++) if (p[i] != a) return p;
        for (int i = half; i < p.length; i++) if (p[i] != b) return p;
        long key = uniformKey(p, halves);
        byte[] s = UNIFORM.get(key);
        if (s != null) return s;
        if (UNIFORM.size() >= 512) return p;
        s = UNIFORM.putIfAbsent(key, p);
        return s == null ? p : s;
    }
    /** The bytes {@code p} holds in a step: 0 for none or a shared uniform array. */
    private static int stepBytes(byte[] p) {
        if (p == null || p.length == 0) return 0;
        return UNIFORM.get(uniformKey(p, false)) == p || UNIFORM.get(uniformKey(p, true)) == p ? 0 : p.length;
    }
    private byte[] copyPlane(byte[] plane, int key) {
        int x = key % columns() * TILE, y = key / columns() * TILE;
        int w = Math.min(TILE, width - x), h = Math.min(TILE, height - y);
        byte[] copy = new byte[w * h];
        for (int row = 0; row < h; row++) System.arraycopy(plane, (y + row) * width + x, copy, row * w, w);
        return copy;
    }
    /** Two tiles' shares (or print tones) alike: null (taken before the layer had them) is every pixel 255. */
    private static boolean samePlane(byte[] a, byte[] b) { return samePlane(a, b, (byte)255); }
    /** As {@link #samePlane(byte[], byte[])}, null being every pixel {@code empty} (a kinds plane's is 0). */
    private static boolean samePlane(byte[] a, byte[] b, byte empty) {
        if (a == null || b == null) {
            byte[] c = a == null ? b : a;
            if (c != null) for (byte v : c) if (v != empty) return false;
            return true;
        }
        return Arrays.equals(a, b);
    }
    /**
     * Tile {@code key} back to {@code data}, with its shares {@code kept} on a layer with a share and its print tones
     * {@code printed} on one with a print (null: all 255, as before it had them).
     */
    private void restoreTile(int key, byte[] data, byte[] kept, byte[] printed, byte[] kinded) {
        writable();
        int x = key % columns() * TILE, y = key / columns() * TILE;
        int w = Math.min(TILE, width - x), h = Math.min(TILE, height - y);
        for (int row = 0; row < h; row++) {
            System.arraycopy(data, row * w, tones, (y + row) * width + x, w);
            System.arraycopy(data,w*h+row*w,alpha,(y+row)*width+x,w);
            if (share != null) { if (kept != null) System.arraycopy(kept, row * w, share, (y + row) * width + x, w); else Arrays.fill(share, (y + row) * width + x, (y + row) * width + x + w, (byte)255); }
            if (print != null) { if (printed != null) System.arraycopy(printed, row * w, print, (y + row) * width + x, w); else Arrays.fill(print, (y + row) * width + x, (y + row) * width + x + w, (byte)255); }
            if (kind != null) { if (kinded != null) System.arraycopy(kinded, row * w, kind, (y + row) * width + x, w); else Arrays.fill(kind, (y + row) * width + x, (y + row) * width + x + w, (byte)0); }
        }
        left = Math.min(left, x); top = Math.min(top, y);
        right = Math.max(right, x + w); bottom = Math.max(bottom, y + h);
    }
    private static final class TileEdit {
        final int key; final byte[] before, after;
        /** The tile's shares before and after, on a layer with a {@link Layer#share}; null: every pixel 255. */
        final byte[] shareBefore, shareAfter;
        /** Inkwash: its print tones ({@link Layer#print}) before and after, on a layer with them; null: every pixel 255. */
        final byte[] printBefore, printAfter;
        /** Inkwash: its kinds ({@link Layer#kind}) before and after, on a layer with them; null: every pixel 0. */
        final byte[] kindBefore, kindAfter;
        TileEdit(int key, byte[] before, byte[] after, byte[] shareBefore, byte[] shareAfter, byte[] printBefore, byte[] printAfter, byte[] kindBefore, byte[] kindAfter) {
            this.key = key; this.before = before; this.after = after; this.shareBefore = shareBefore; this.shareAfter = shareAfter;
            this.printBefore = printBefore; this.printAfter = printAfter; this.kindBefore = kindBefore; this.kindAfter = kindAfter;
        }
    }
    private static final class StackState {
        final ArrayList<Layer> layers;
        final Layer mask;
        final String[] names;
        final boolean[] visible;
        final int[] opacity;
        final int active, width, height, paper;
        /** Inkwash: the page's magnets (immutable, shared), which Clear page takes off with the paint. */
        final Magnets magnets;
        /** Inkwash: the page's mirrors (immutable, shared): placed, cleared and kept in structural steps. */
        final Mirrors mirrors;
        /** Inkwash: where a cover's design is rubbed off (immutable, shared). */
        final CoverWear wear;
        StackState(ToneDocument doc) {
            wear=doc.wear;
            layers=new ArrayList<>(doc.layers); mask=doc.mask; active=doc.active; width=doc.width; height=doc.height; paper=doc.paper; magnets=doc.magnets; mirrors=doc.mirrors;
            names=new String[layers.size()]; visible=new boolean[layers.size()]; opacity=new int[layers.size()];
            for(int i=0;i<layers.size();i++) { names[i]=layers.get(i).name; visible[i]=layers.get(i).visible; opacity[i]=layers.get(i).opacity; }
        }
        void restore(ToneDocument doc) {
            // The magnets as they were (the history runs in order: only Clear page changes them in a structural step);
            // the whole page is drawn again after one, the magnets with it.
            doc.width=width; doc.height=height; doc.paper=paper; doc.magnets=magnets;
            if(doc.wear!=wear) { doc.wear=wear; doc.repaper(); }
            // The mirrors as they were, on or off and their guides shown or not as they are now (the Mirrors and Show
            // Guides rows are no Undo step).
            doc.mirrors=mirrors.turned(doc.mirrors.on).guided(doc.mirrors.guides);
            doc.captured=new boolean[doc.columns()*((height+TILE-1)/TILE)];
            doc.layers.clear(); doc.layers.addAll(layers); doc.mask=mask;
            for(int i=0;i<layers.size();i++) { layers.get(i).name=names[i]; layers.get(i).visible=visible[i]; layers.get(i).opacity=opacity[i]; }
            doc.selectLayer(active);
        }
    }
    private static final class Edit {
        final ArrayList<TileEdit> tiles; final int bytes;
        /** Inkwash: counts the steps made, so a stroke's log entry knows its step ({@link StrokeLog}). */
        private static long made;
        final long serial=++made;
        final Layer layer;
        final StackState previous,next;
        /** Undone and redone together with the edit before it; see {@link #finishWithPrevious}. */
        boolean linked;
        /** Inkwash: a magnets step's magnets before and after ({@link #setMagnets}); null for any other edit. */
        final Magnets magnetsBefore, magnetsAfter;
        Edit(Magnets before,Magnets after) {
            layer=null; tiles=null; previous=next=null; magnetsBefore=before; magnetsAfter=after;
            // The lists are shared, immutable: what a step holds is about its references and the magnets it changed.
            bytes=64+16*Math.abs(after.size()-before.size())+32;
        }
        Edit(StackState previous,StackState next) {
            magnetsBefore=magnetsAfter=null;
            this.previous=previous; this.next=next; layer=null; tiles=null;
            // Inkwash: beyond the page itself, a step holds one side's layers, never both: while undoable, those it took
            // off (the ones it put on are the page's); while redoable, those it put on. So the larger side, not the sum:
            // Clear page swaps every layer, and the sum (some 49 MB on the Manta, over the whole budget) would let the
            // next stroke push the clear out of the history.
            long off=0, on=0;
            for(Layer layer:previous.layers) if(!next.layers.contains(layer)) off+=held(layer);
            for(Layer layer:next.layers) if(!previous.layers.contains(layer)) on+=held(layer);
            if(previous.mask!=next.mask) { off+=held(previous.mask); on+=held(next.mask); }
            if(previous.wear!=next.wear) { off+=previous.wear==null?0:previous.wear.bytes(); on+=next.wear==null?0:next.wear.bytes(); }
            bytes=(int)Math.min(Integer.MAX_VALUE,256+Math.max(off,on));
        }
        /** The bytes {@code layer}'s pixels take, its shares, print and kinds too; 0 for none. */
        private static long held(Layer layer) {
            if(layer==null) return 0;
            long bytes=(long)layer.tones.length*(2+(layer.share!=null?1:0)+(layer.print!=null?1:0)+(layer.kind!=null?1:0));
            if(layer.planes!=null) for(Layer p:layer.planes) bytes+=held(p);
            return bytes;
        }
        Edit(ArrayList<TileEdit> tiles,Layer layer) {
            magnetsBefore=magnetsAfter=null;
            this.layer=layer; previous=next=null;
            this.tiles = tiles; int count = 0;
            for (TileEdit tile : tiles) count += 64 + stepBytes(tile.before) + stepBytes(tile.after) + stepBytes(tile.shareBefore) + stepBytes(tile.shareAfter)
                    + stepBytes(tile.printBefore) + (tile.printAfter != tile.printBefore ? stepBytes(tile.printAfter) : 0)
                    + stepBytes(tile.kindBefore) + (tile.kindAfter != tile.kindBefore ? stepBytes(tile.kindAfter) : 0);
            bytes = count;
        }
    }
}
