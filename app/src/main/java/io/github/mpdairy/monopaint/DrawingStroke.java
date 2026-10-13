package io.github.mpdairy.monopaint;

interface DrawingStroke {
    void sample(float x, float y, float pressure, float tiltX, float tiltY);
    boolean finish();
    /** Wet paint holds still while this stroke is drawn, and moves once it is finished. */
    default boolean holdsWetPaint() { return false; }
}
