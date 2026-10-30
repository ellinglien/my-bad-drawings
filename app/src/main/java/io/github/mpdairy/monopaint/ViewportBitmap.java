package io.github.mpdairy.monopaint;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;

/** Scale logical tones first, then dither at screen resolution so zoom cannot alias shades. */
final class ViewportBitmap {
    static { System.loadLibrary("monopaint_display"); }
    /** Each tone's 8 × 8 dot pattern, as the canvas shows it unzoomed. */
    static final int[] DOTS=new int[256*64];
    static {
        for(int tone=0;tone<256;tone++) for(int y=0;y<8;y++) for(int x=0;x<8;x++)
            DOTS[tone*64+y*8+x]=DotPattern.pixel(tone,x,y);
    }
    private static native void nativeRender(Bitmap source,Bitmap target,float[] inverse,int[] dots,
                                             int left,int top,int right,int bottom,boolean lines);
    private static native void nativeCompose(Bitmap target,byte[][] tones,byte[][] alpha,int[] opacity,int count,int paper,int[] dots,
                                             int left,int top,int right,int bottom);
    /** One logical composite when navigation starts; never derive shades from display dots. */
    static void compose(ToneDocument document,Bitmap target) {
        compose(document,target,false);
    }
    static void compose(ToneDocument document,Bitmap target,boolean dither) {
        ToneDocument.Snapshot snapshot=document.layerSnapshot();
        java.util.ArrayList<byte[]> tones=new java.util.ArrayList<>(),alpha=new java.util.ArrayList<>();
        int[] opacity=new int[ToneDocument.STACK+1];
        // Inkwash: a cover's paper pixels under the layers, as a layer covering the paper's tone (ToneDocument#visibleLayers).
        byte[] under=snapshot.shownPaper();
        if(under!=null) { opacity[0]=100; tones.add(under); alpha.add(ToneDocument.opaque(under.length)); }
        // Inkwash: each layer's base and kind planes, in the page's order.
        for(int k=0,end=ToneDocument.stackSize(snapshot.layers.size());k<end;k++) {
            ToneDocument.Layer entry=ToneDocument.stacked(snapshot.layers,k), owner=snapshot.layers.get(Math.min(ToneDocument.stackOwner(k),snapshot.layers.size()-1));
            if(entry==null||!owner.visible) continue;
            opacity[tones.size()]=ToneDocument.stackOpacity(owner,entry);
            tones.add(entry.tones);alpha.add(entry.alpha);
        }
        nativeCompose(target,tones.toArray(new byte[0][]),alpha.toArray(new byte[0][]),opacity,tones.size(),snapshot.paper,dither?DOTS:null,
                0,0,target.getWidth(),target.getHeight());
    }
    private static final byte[][] areaTones=new byte[ToneDocument.STACK+3][],areaAlpha=new byte[ToneDocument.STACK+3][];
    private static final int[] areaOpacity=new int[ToneDocument.STACK+3];
    /** An area at least this many pixels is rendered in bands of rows on several cores ({@link Cores}): each writes only its own rows. */
    private static final int SHARE_PX=64*1024;
    /**
     * Renders the page's {@code area} into {@code target}, a raster the page's size, as {@link #compose} does
     * the whole page and as {@link ToneDocument#render} would (without masking-fluid hatching). Reads the
     * page's own pixels rather than a snapshot, so call it on the page's owner thread, the main thread; a big
     * area is shared out over the cores while that thread waits.
     */
    static void compose(ToneDocument document,Bitmap target,boolean dither,Rect area) { compose(document,target,dither,area,null,0); }
    /**
     * As above with tracing paper's {@code ghost} planes ({@link GhostPlane.Planes}; null: none), one under the page's
     * layers and one over them (the layers above it in its stack), and its {@code layerSteps} ({@link
     * ToneDocument#visibleLayers}): the drawing screen's.
     */
    static void compose(ToneDocument document,Bitmap target,boolean dither,Rect area,GhostPlane.Planes ghost,int layerSteps) {
        int count=document.visibleLayers(areaTones,areaAlpha,areaOpacity,ghost==null?null:ghost.under,layerSteps,ghost==null?null:ghost.overTones,ghost==null?null:ghost.overAlpha), paper=document.paperTone();
        int[] dots=dither?DOTS:null;
        try {
            int rows=area.height(),bands=Math.min(Cores.WORKERS,rows/16);
            if((long)area.width()*rows<SHARE_PX || bands<2) { nativeCompose(target,areaTones,areaAlpha,areaOpacity,count,paper,dots,area.left,area.top,area.right,area.bottom); return; }
            Cores.each(bands,(k,worker)->nativeCompose(target,areaTones,areaAlpha,areaOpacity,count,paper,dots,
                    area.left,area.top+rows*k/bands,area.right,area.top+rows*(k+1)/bands));
        }
        finally { java.util.Arrays.fill(areaTones,null); java.util.Arrays.fill(areaAlpha,null); }
    }
    /** The page as a screen of that size would show it: smoothly scaled tones, then its dots. */
    static Bitmap dithered(ToneDocument document,int width,int height) {
        Bitmap page=Bitmap.createBitmap(document.width,document.height,Bitmap.Config.ARGB_8888);
        boolean same=width==document.width && height==document.height;
        compose(document,page,same);
        if(same) return page;
        Bitmap scaled=Bitmap.createScaledBitmap(page,width,height,true);
        page.recycle();
        ViewportBitmap screen=new ViewportBitmap(width,height);
        screen.update(scaled,new Matrix(),null);
        scaled.recycle();
        return screen.bitmap;
    }
    final Bitmap bitmap;
    /** Inkwash: what lies beyond the page is the owner's lines ({@link CanvasViewport#backdrop}), not paper; only the drawing screen sets it, so no copy has it. */
    boolean backdrop;
    private final Matrix inverse=new Matrix();
    private final float[] values=new float[9];
    ViewportBitmap(int width,int height) {
        bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        bitmap.setHasAlpha(false);
    }
    Rect update(Bitmap tones, Matrix pageToView, Rect dirty) {
        Rect area;
        if(dirty==null) area=new Rect(0,0,bitmap.getWidth(),bitmap.getHeight());
        else {
            RectF mapped=new RectF(dirty); pageToView.mapRect(mapped);
            area=new Rect(); mapped.roundOut(area); area.inset(-1,-1);
            if(!area.intersect(0,0,bitmap.getWidth(),bitmap.getHeight())) return new Rect();
        }
        if(!pageToView.invert(inverse)) throw new IllegalStateException("Invalid zoom transform");
        inverse.getValues(values);
        nativeRender(tones,bitmap,values,DOTS,area.left,area.top,area.right,area.bottom,backdrop);
        return area;
    }
    void close() { bitmap.recycle(); }
}
