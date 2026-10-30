package io.github.mpdairy.monopaint;

/** Bounded page magnification and translation in view pixels. */
final class CanvasViewport {
    float zoom = 1, fitScale = 1, x, y;
    private boolean heldLayout, untouched = true, filled;
    /**
     * Zen Mode: the page covers the whole screen, no room round it, and a pinch never shows any (the owner, 2026-10-29:
     * "zen mode... we need to [limit] the min zoom so that no background will show"; both ways up).
     */
    boolean cover;
    private float width, height, pageWidth, pageHeight;

    void configure(float width, float height, float pageWidth, float pageHeight) {
        float heldScale=scale();
        this.width=width; this.height=height;
        this.pageWidth=pageWidth; this.pageHeight=pageHeight;
        // Turned sideways, an upright page fills the width, its top showing, a finger pans down (the owner, 2026-10-28: "if
        // it's horizontal i want the top part of the page to show... and vertical it should fit perfectly"), past 100% if
        // that's what fills it ("that % should be the max percent, whatever it is").
        filled=cover || fillsWidth(width,height,pageWidth,pageHeight);
        fitScale=cover ? Math.max(width/pageWidth,height/pageHeight) : filled ? width/pageWidth : Math.min(1,Math.min(width/pageWidth,height/pageHeight));
        // Fitted (the owner, 2026-10-26: "let's make it fit neatly in both"): the whole page, centred in this view, the
        // room the bars leave, fitted again whenever that room changes (Zen Mode, a turn) until a pinch or pan.
        if(untouched) { zoom=1; x=Math.round((width-pageWidth*fitScale)/2); y=filled ? 0 : Math.round((height-pageHeight*fitScale)/2); return; }
        if(cover)heldLayout=false;
        zoom=heldLayout ? heldScale/fitScale : Math.max(minZoom(),Math.min(8/fitScale,zoom));
        if(!heldLayout)clamp();
    }
    float scale() { return fitScale*zoom; }
    /** A view wider than tall showing an upright page: fitted to the width, not whole. */
    static boolean fillsWidth(float width, float height, float pageWidth, float pageHeight) { return width>height && pageHeight>pageWidth; }
    /**
     * A pinch never goes below 100% (the fitted view of a page bigger than the screen may: it shows the whole page), nor
     * below the width filled sideways (the owner, 2026-10-29: "you cant go LOWER % zoom than 136 on horizontal").
     */
    float minZoom() { return filled ? 1 : 1/fitScale; }
    int percent() { return Math.round(scale()*100); }
    /** Back to the fitted view ({@link #configure}). */
    void reset() { heldLayout=false; zoom=1; x=y=0; untouched=true; }
    /** Still fitted: no pan, zoom or chrome change since the reset. */
    boolean untouched() { return untouched; }
    /**
     * 100% placement where the artwork began, for MonoPaint's Expand canvas ({@code FullscreenController}): the far
     * edges on the canvas edges away from the bars, right or left (by hand), and the top.
     */
    float homeX(boolean leftHanded) { return leftHanded?0:width-pageWidth; }
    float homeY() { return 0; }
    void actualSize(boolean leftHanded) { hold(1,homeX(leftHanded),homeY()); }
    /** Retain the exact view while chrome changes around it. */
    void hold(float scale,float x,float y) { heldLayout=true; untouched=false; zoom=scale/fitScale; this.x=x; this.y=y; }
    void gesture(float factor, float oldX, float oldY, float newX, float newY) {
        if(!Float.isFinite(factor) || factor<=0) return;
        // A hidden bar may leave a previously fitted page below the new fit scale.
        // Panning must not unexpectedly magnify it; pinch out or reset to adopt the new fit.
        float next=Math.max(Math.min(minZoom(),zoom),Math.min(8/fitScale,zoom*factor));
        heldLayout=next<minZoom(); untouched=false;
        float ratio=next/zoom;
        x=newX-(oldX-x)*ratio; y=newY-(oldY-y)*ratio; zoom=next;
        clamp();
    }
    private void clamp() {
        x=Math.max(Math.min(0,width-pageWidth*scale()),Math.min(Math.max(0,width-pageWidth*scale()),x));
        y=Math.max(Math.min(0,height-pageHeight*scale()),Math.min(Math.max(0,height-pageHeight*scale()),y));
    }
}
