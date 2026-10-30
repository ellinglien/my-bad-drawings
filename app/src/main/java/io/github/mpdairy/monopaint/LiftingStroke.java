package io.github.mpdairy.monopaint;

/**
 * A stroke whose work at the pen's lift runs off the pen's thread: a quick stroke's render ({@link QuickStroke}), a
 * Drying stroke's settle ({@link ModularStroke}). The host ({@link QuickLift}: the drawing screen, Brush Lab's test
 * page) keeps it as its stroke meanwhile, looks in with {@link #develop} and then calls {@link #finish}; anything that
 * needs the page first finishes it at once, as a stroke still down.
 */
interface LiftingStroke extends DrawingStroke {
    /** Whether the lift would hand work to another thread now (else the host finishes it at once, as any stroke). */
    boolean developsAfterLift();
    /** The pen lifted: starts the work after lift. Returns whether it is still to do (false once done, or none). */
    boolean lift();
    /** After {@link #lift}: waits up to about {@code nanos} for it; returns whether more is left before {@link #finish}. */
    boolean develop(long nanos);
    /** Whether its brush lays through a print screen, pure black and white ({@link ResolvedBrush#screen}): the bench's "screen" fast panel path ({@link StrokePanel#wants}). */
    default boolean screened() { return false; }
}
