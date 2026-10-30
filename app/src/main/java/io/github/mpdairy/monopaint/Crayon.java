package io.github.mpdairy.monopaint;

import static io.github.mpdairy.monopaint.StrokeMath.clamp;
import static io.github.mpdairy.monopaint.StrokeMath.smoothstep;

/**
 * Crayon (mischief-lab-2.html's fourth pad, its crayon4; {@link PartChoice#CRAYON}): wax on the paper's tooth. Its
 * noises are fixed to the page and the same for every crayon, so a stroke drawn again matches pixel for pixel and wax
 * catches the same grain stroke after stroke: the tooth (bumps 3 px across with a pixel of grit), the ragged edge
 * (notches about 7 px apart, a waver of 2.5 px, a pixel of grit) and the wobble of its width along a stroke (about 22
 * px). They wrap every {@link StickGrain#SIDE} px, as charcoal's grain does. The line holds how hard the crayon presses
 * at each pixel ({@link #press}, {@link LineStamper}); the compose turns that into wax over what is already there
 * ({@link #wax}, {@link ModularCompose}); its wax goes on the page's masking fluid too ({@link #mask}). Pure, checked on
 * the desktop.
 */
final class Crayon {
    /** How far past its rim a dab reaches: flecks, lone grains caught just past the edge. */
    static final double REACH = 1.35;
    /** The Wax dial's default: the prototype's rates of build-up and of shedding washes. */
    static final double WAX = .6;
    private static final int SIDE = StickGrain.SIDE, SALT = 0x0c7a10;
    private Crayon() { }

    /** The tooth and the ragged edge, 0 to 1, made on first use (2 MB): a holder class, so made once whatever thread asks. */
    private static final class Maps {
        static final float[] TOOTH, RAG;
        static {
            WildBrush.Random r = new WildBrush.Random(SALT);
            float[] t = StickGrain.tileNoise(SIDE, 3, r), big = StickGrain.tileNoise(SIDE, 7, r), small = StickGrain.tileNoise(SIDE, 2.5, r);
            for (int i = 0; i < t.length; i++) {
                int x = i & SIDE - 1, y = i >> 9;
                t[i] = (float)(.55 * t[i] + .45 * PrintScreen.hash(x, y, 89));
                big[i] = (float)(.55 * big[i] + .3 * small[i] + .15 * PrintScreen.hash(x, y, 103));
            }
            TOOTH = t; RAG = big;
        }
    }
    private static int at(int x, int y) { return ((y & SIDE - 1) << 9) | (x & SIDE - 1); }
    /** The paper's tooth as wax finds it at page pixel {@code x, y}, 0 (a pit) to 1 (a peak). */
    static double tooth(int x, int y) { return Maps.TOOTH[at(x, y)]; }
    /** The ragged edge's map at {@code x, y}, 0 to 1: the outline there is .8 to 1.3 of the rim. */
    static double rag(int x, int y) { return Maps.RAG[at(x, y)]; }
    /** The width's wobble at a dab centred on {@code x, y}: .9 to 1.1, smooth along the stroke (noise about 22 px across). */
    static double wobble(double x, double y) {
        double u = x / 22, v = y / 22, iu = Math.floor(u), iv = Math.floor(v), fu = u - iu, fv = v - iv;
        int i = (int) iu, j = (int) iv;
        fu = fu * fu * (3 - 2 * fu); fv = fv * fv * (3 - 2 * fv);
        double top = lerp(PrintScreen.hash(i, j, 101), PrintScreen.hash(i + 1, j, 101), fu), bottom = lerp(PrintScreen.hash(i, j + 1, 101), PrintScreen.hash(i + 1, j + 1, 101), fu);
        return .9 + .2 * lerp(top, bottom, fv);
    }
    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }

    /**
     * What the line holds at page pixel {@code x, y}, {@code q} from a dab's centre in rims (the wobble taken in), the
     * distance growing {@code g} rims a pixel there, pressed at {@code pr}: how hard the wax meets the paper, 0 to 1. The
     * outline there is .8 to 1.3 of the rim ({@link #rag}); toward it (past .6) less, so only the peaks take wax and the
     * edge breaks up more than the middle. Just past it, now and then a high grain catches a fleck.
     */
    static double press(double q, double g, double pr, int x, int y) {
        double edge = .8 + .5 * rag(x, y);
        if (q > edge) return q < REACH && tooth(x, y) > .62 && PrintScreen.hash(x, y, 107) > .9 ? clamp(.8 * pr - .22) : 0;
        double f = clamp((edge - q) / Math.max(g, 1e-6) + .5), e = smoothstep(.6, 1, q / edge);
        return clamp(f * (pr - .22 * e));
    }
    /**
     * The wax the line laid at a pixel, 0 to 1: {@code v} its {@link #press}, {@code t} the tooth there, {@code ex} how
     * dark the layer already is there (wax laid lifts the surface, so layers build up), {@code wax} the Wax dial. Wax goes
     * where the tooth plus what is there clears {@code 1.08 − v}: a light touch only the peaks; pressed past .75 it also
     * fills the valleys, all but the deepest pits. The prototype's dabs overlap about eight deep, each laying .4 of what
     * reaches: so many here at once.
     */
    static double wax(double v, double t, double ex, double wax) {
        double a = clamp((t + .35 * wax / WAX * ex - 1.08 + v) * 3.5);
        if (v > .75) a = Math.max(a, (v - .75) * 2.4 * clamp(t * 5));
        double k = 1 - .4 * a;
        k *= k; k *= k; k *= k;
        return 1 - k;
    }
    /** How strongly wax {@code dark} dark over white paper (its shade taken in) shields what comes later, 0 to 1: the prototype's wash × (1 − clamp(1.6 wax)). */
    static double mask(double dark, double wax) { return clamp(1.6 * dark * wax / WAX); }
    /** Scratch's blade at {@code x, y}: a ragged edge, a pixel in seven scraping half. */
    static double scrape(int x, int y) { return PrintScreen.hash(x, y, 71) < .15 ? .5 : 1; }
}
