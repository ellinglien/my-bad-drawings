package io.github.mpdairy.monopaint;

final class ViewportChecks {
    public static void main(String[] args) {
        CanvasViewport v=new CanvasViewport(); v.configure(1000,1400,1000,1400);
        v.gesture(2,400,500,400,500);
        near(v.scale(),2); near(v.x,-400); near(v.y,-500);
        near((400-v.x)/v.scale(),400); near((500-v.y)/v.scale(),500);
        v.gesture(1,400,500,450,600); near(v.x,-350); near(v.y,-400);
        v.gesture(1,0,0,10000,10000); near(v.x,0); near(v.y,0);
        v.gesture(1,0,0,-10000,-10000); near(v.x,-1000); near(v.y,-1400);
        v.gesture(100,500,700,500,700); near(v.scale(),8);
        v.gesture(.001f,500,700,500,700); near(v.scale(),1); near(v.x,0); near(v.y,0);
        v.configure(500,700,1000,1400); near(v.scale(),1);
        if(v.percent()!=100) throw new AssertionError("A page bigger than the screen opens at 100%, not shrunk");
        v.gesture(.5f,250,350,250,350); near(v.scale(),1);
        v.gesture(4,0,0,0,0); near(v.scale(),4); near(v.x,0); near(v.y,0);
        v.gesture(Float.NaN,0,0,0,0); near(v.scale(),4);
        v.reset(); near(v.x,0); near(v.y,0);
        v.configure(1000,1400,320,480); near(v.x,340); near(v.y,460);
        v.gesture(2,500,700,500,700); near(v.x,180); near(v.y,220);
        v=new CanvasViewport();v.configure(500,700,1000,1400);
        v.hold(.5f,64,48);v.configure(700,1000,1000,1400);
        near(v.scale(),.5f);near(v.x,64);near(v.y,48);
        v.gesture(1,200,200,230,230);v.configure(700,1000,1000,1400);
        near(v.scale(),.5f);near(v.x,94);near(v.y,78);
        v.reset();v.configure(700,1000,1000,1400);near(v.scale(),.7f);near(v.x,0);near(v.y,10);
        // Zen Mode covers the screen, no room round the page, and a pinch never shows any (the owner, 2026-10-29).
        for(float[] c:new float[][]{{900,1400,1000,1400},{1400,1000,1000,1400},{1000,1300,1000,1400}}){
            v=new CanvasViewport();v.cover=true;v.configure(c[0],c[1],c[2],c[3]);
            float s0=v.scale();
            if(c[2]*s0<c[0]-.5f||c[3]*s0<c[1]-.5f||v.x>0||v.y!=0||v.x+c[2]*s0<c[0]-.5f) throw new AssertionError("Zen covers "+c[0]+"x"+c[1]+" at "+s0);
            v.gesture(.01f,c[0]/2,c[1]/2,c[0]/2,c[1]/2);near(v.scale(),s0);
            v.gesture(2,c[0]/2,c[1]/2,c[0]/2,c[1]/2);v.gesture(.01f,0,0,0,0);near(v.scale(),s0);
            if(v.x>0||v.y>0||v.x+c[2]*v.scale()<c[0]-.5f||v.y+c[3]*v.scale()<c[1]-.5f) throw new AssertionError("Zen: no room after a pinch");
        }
        System.out.println("PASS: zoom focus, two-finger translation, page bounds, 800% limit, a pinch never below 100%, reset to the whole page centred; Zen Mode covering the screen, a pinch never below it");
    }
    private static void near(float actual,float wanted) {
        if(Math.abs(actual-wanted)>.01f) throw new AssertionError(actual+" != "+wanted);
    }
}
