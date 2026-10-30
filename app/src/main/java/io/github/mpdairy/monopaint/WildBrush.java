package io.github.mpdairy.monopaint;

/**
 * An old Wild brush, as an {@code I}/{@code P} share code describes it (see {@link BrushCode}): read only to convert
 * it to six parts ({@link LegacyBrushes}). A 32-bit seed decided its name and every dial, as the desktop roll tester
 * (docs/wild-slots/roll-tester.html) does; {@link #roll} still gives a seed's brush, which the checks' share codes
 * pin. Its {@link Random} is the six-part engine's generator too. Pure, checked on the desktop.
 */
final class WildBrush {
    enum Kind { INK, PAINT }
    enum Nib { ROUND, CHISEL, FLAT }

    /** The tester's rng(): mulberry32, an unsigned 32-bit state, values in [0, 1). */
    static final class Random {
        private int state;
        Random(long seed) { state = (int)seed; }
        double next() {
            state += 0x6D2B79F5;
            return draw(state);
        }
        /** The {@code n}th draw from now (1: the next), without drawing: each draw depends only on how many came before. */
        double ahead(long n) { return draw(state + (int)(n * 0x6D2B79F5L)); }
        /** Passes over {@code n} draws. */
        void skip(long n) { state += (int)(n * 0x6D2B79F5L); }
        private static double draw(int t) {
            t = (t ^ (t >>> 15)) * (1 | t);
            t = (t + ((t ^ (t >>> 7)) * (61 | t))) ^ t;
            return ((t ^ (t >>> 14)) & 0xffffffffL) / 4294967296.0;
        }
        /** The tester's maybe(p): off unless a first draw falls under p, then a second draw. */
        double maybe(double p) { return next() < p ? next() : 0; }
    }

    /** Dry brush, paper tooth and wobble together never pass this, so a roll stays tasteful. */
    static final double ROUGHNESS_BUDGET = 1.3;
    /**
     * The roll tester's name words: a seed's first two draws pick its old adjective-animal name. Kept only so a
     * seed rolls the name it always had, which brushes saved before Brush Lab keep (see ToolSlots' migration).
     * Never reorder or change them.
     */
    static final String[] OLD_ADJECTIVES = {"wobbly", "soggy", "grumpy", "sneaky", "fancy", "dizzy", "squishy", "cranky",
            "giddy", "snoozy", "bouncy", "crumbly", "fizzy", "goofy", "jiggly", "lumpy", "noodly", "peckish", "rumbly",
            "sassy", "smug", "ticklish", "wonky", "zesty"};
    static final String[] OLD_ANIMALS = {"puffin", "moose", "walrus", "otter", "badger", "heron", "newt", "wombat",
            "lemur", "yak", "ferret", "gecko", "llama", "hedgehog", "octopus", "beaver", "raccoon", "marmot", "toad", "owl", "goose", "sloth", "narwhal", "pangolin"};

    final Kind kind;
    final long seed;
    /** The brush's name: a roll's is its old adjective-animal (see {@link #OLD_ADJECTIVES}). */
    String name;
    // Wild ink.
    Nib nib = Nib.ROUND;
    double aspect = 1, angle, taperIn, taperOut, speed, dry, tooth, wobble, pool, splatter;
    // Both: how far pressure moves the width.
    double pressure;
    // Wild paint.
    boolean wet;
    double soft, gran, feather, hollow, bloom, streaks, backrun;
    /** How long wild paint stayed wet, 0 (quickest, 0.75 s) to 1 (slowest, 5 s); see {@link LegacyBrushes#drying}. */
    double drying;
    /** The paper grain the brush shows (see {@link BrushTexture}); plain is today's paper. */
    BrushTexture texture = BrushTexture.PLAIN;
    /** Its style (see {@link BrushStyle}); a roll is Wild. */
    BrushStyle style;
    /**
     * Which way speed works while its amount is 0: thicker when fast, like the eraser (true), or thinner, like a
     * nib running dry (false, the default). Otherwise the sign of {@link #speed} says it.
     */
    boolean thickerWhenFast;

    /** A brush with every dial off, a round nib, Wild, plain: callers set what they need. The seed is read as 32 bits, unsigned. */
    WildBrush(Kind kind, long seed, String name) {
        this.kind = kind; this.seed = seed & 0xffffffffL; this.name = name; style = BrushStyle.wild(kind);
    }

    /** The brush {@code seed} rolls, its low 32 bits read unsigned, as the tester's {@code seed >>> 0}. */
    static WildBrush roll(Kind kind, long seed) {
        Random r = new Random(seed);
        String[] adjectives = OLD_ADJECTIVES, animals = OLD_ANIMALS;
        String name = adjectives[(int)(r.next() * adjectives.length)] + "-" + animals[(int)(r.next() * animals.length)];
        WildBrush b = new WildBrush(kind, seed & 0xffffffffL, name);
        if (kind == Kind.INK) {
            b.nib = Nib.values()[(int)(r.next() * 3)];
            if (b.nib != Nib.ROUND) { double[] widths = BrushStyle.nibWidths(b.nib); b.aspect = lerp(widths[0], widths[1], r.next()); }
            b.angle = lerp(20, 70, r.next());
            b.taperIn = r.maybe(.7); b.taperOut = r.maybe(.75); b.pressure = r.next(); b.speed = lerp(-1, 1, r.next());
            b.dry = r.maybe(.45); b.tooth = r.maybe(.5); b.wobble = r.maybe(.45);
            double rough = b.dry + b.tooth + b.wobble;
            if (rough > ROUGHNESS_BUDGET) { double k = ROUGHNESS_BUDGET / rough; b.dry *= k; b.tooth *= k; b.wobble *= k; }
            // Drawn last in the tester, so its seeds from before these dials kept the rest of their character.
            b.pool = r.maybe(.45); b.splatter = r.maybe(.3);
            return b;
        }
        b.wet = r.next() < .65;
        b.pressure = r.next(); b.soft = r.next();
        b.gran = r.maybe(.55); b.feather = r.maybe(.5);
        // As the tester's `hollow: wet ? maybe(0.7) : 0`: flat paint makes no draw for hollow or bloom.
        b.hollow = b.wet ? r.maybe(.7) : 0; b.bloom = b.wet ? r.maybe(.6) : 0;
        b.streaks = r.maybe(.45);
        // Backrun's draw is made either way, as the tester's `const backrun = maybe(0.4)`.
        double backrun = r.maybe(.4); b.backrun = b.wet ? backrun : 0;
        // Drawn last, as the tester's, so seeds rolled before drying time existed keep the rest of their character.
        b.drying = r.next();
        return b;
    }
    /**
     * This brush with another seed: the same settings, name and style, its own bristles, hairs and splatter. Every
     * setting of both kinds goes through {@link BrushSetting}, so a dial added there is copied too.
     */
    WildBrush copy(long seed) {
        WildBrush c = new WildBrush(kind, seed, name);
        for (BrushSetting s : BrushSetting.values()) s.set(c, s.get(this));
        c.style = style; c.thickerWhenFast = thickerWhenFast;
        return c;
    }
    WildBrush copy() { return copy(seed); }
    /**
     * What makes two brushes the same paint: their settings and seed, as a share code carries them without name or
     * style. Looks, samples and wet paint key on it, so one seed with other settings is another brush.
     */
    String key() { return BrushCode.settings(this); }
    /** Which way speed works: -1 thinner when fast, 1 thicker. */
    int speedDirection() { return speed < 0 ? -1 : speed > 0 ? 1 : thickerWhenFast ? 1 : -1; }
    /**
     * Fits the settings that depend on others, as the mockup's normalize: a round nib is round; a shaped nib's
     * width stays in its range, starting mid-range when it was round; flat paint has no hollow edge, bloom or backruns.
     */
    WildBrush normalize() {
        if (kind == Kind.INK) {
            if (nib == Nib.ROUND) aspect = 1;
            else {
                double[] widths = BrushStyle.nibWidths(nib);
                aspect = aspect == 1 ? (widths[0] + widths[1]) / 2 : Math.max(widths[0], Math.min(widths[1], aspect));
            }
        } else if (!wet) { hollow = 0; bloom = 0; backrun = 0; }
        return this;
    }
    /** {@code a} to {@code b} as {@code t} goes 0 to 1: the dice's, shared by styles and variations. */
    static double lerp(double a, double b, double t) { return StrokeMath.lerp(a, b, t); }
}
