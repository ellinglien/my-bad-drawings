package io.github.mpdairy.monopaint;

/** One quick, stationary two- or three-finger tap; it is reported only once every finger lifts. */
final class MultiFingerTap {
    private final float[] startX=new float[32],startY=new float[32];
    private final boolean[] seen=new boolean[32];
    private long down;
    private int peak;
    private boolean rejected,lifting;
    void reset() { rejected=true;peak=0; }
    // Android action constants: down=0, up=1, move=2, cancel=3, pointer down=5, pointer up=6.
    /** @return the tap's finger count (2 or 3) on the final lift, otherwise 0 */
    int event(int action,long time,int[] ids,float[] xs,float[] ys,float slop) {
        if(action==0) {
            down=time;peak=0;rejected=false;lifting=false;java.util.Arrays.fill(seen,false);
        }
        if(action==3) {reset();return 0;}
        if(ids.length>3 || time-down>350)rejected=true;
        // Fingers that land again after one lifts are a different gesture.
        if(action==5 && lifting)rejected=true;
        for(int i=0;i<ids.length;i++) {
            int id=ids[i]; if(id<0 || id>=32) { rejected=true;continue; }
            if(!seen[id]) { seen[id]=true; startX[id]=xs[i];startY[id]=ys[i]; }
            if(Math.hypot(xs[i]-startX[id],ys[i]-startY[id])>slop)rejected=true;
        }
        peak=Math.max(peak,ids.length);
        if(action==6)lifting=true;
        if(action!=1 || rejected || peak<2)return 0;
        int result=peak;reset();return result;
    }
}
