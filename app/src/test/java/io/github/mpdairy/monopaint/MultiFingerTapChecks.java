package io.github.mpdairy.monopaint;
final class MultiFingerTapChecks {
    static int e(MultiFingerTap t,int action,long time,int count,float offset) {
        int[] ids=new int[count];float[] x=new float[count],y=new float[count];
        for(int i=0;i<count;i++){ids[i]=i+5;x[i]=100+i*50+offset;y[i]=200;}
        return t.event(action,time,ids,x,y,18);
    }
    static int chord(MultiFingerTap t,long time,int count) { return chord(t,time,count,80); }
    static int chord(MultiFingerTap t,long time,int count,int length) {
        e(t,0,time,1,0);for(int i=2;i<=count;i++)e(t,5,time+i*10,i,0);
        for(int i=count;i>1;i--)e(t,6,time+length-20+count-i,i,0);
        return e(t,1,time+length,1,0);
    }
    public static void main(String[] args) {
        MultiFingerTap t=new MultiFingerTap();
        check(chord(t,1000,2)==2,"Two-finger tap");
        check(chord(t,2000,3)==3,"Three-finger tap");
        check(chord(t,2100,2)==2 && chord(t,2200,2)==2,"Repeated taps each count");
        check(chord(t,3000,2,600)==0,"Slow holds rejected");
        e(t,0,4000,1,0);e(t,5,4010,2,0);e(t,2,4020,2,60);e(t,6,4030,2,60);check(e(t,1,4040,1,60)==0,"Pinch/pan movement rejected");
        e(t,0,5000,1,0);e(t,5,5010,2,0);e(t,3,5020,2,0);check(e(t,1,5030,1,0)==0,"Cancel rejects the tap");
        e(t,0,6000,1,0);e(t,5,6010,2,0);e(t,6,6020,2,0);e(t,5,6030,2,0);e(t,6,6040,2,0);check(e(t,1,6050,1,0)==0,"Refingering rejected");
        check(chord(t,7000,4)==0,"Palm-sized chords rejected");
        check(chord(t,8000,1)==0,"Single finger never taps");
        System.out.println("PASS: two- and three-finger taps, repeats, release-only activation, timing, pinch/pan movement, cancel, refingering, palms and single fingers");
    }
    static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
}
