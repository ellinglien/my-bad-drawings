package io.github.mpdairy.monopaint;

import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;

/** Repeatable CPU replay and raster fingerprints; does not measure panel latency. */
final class BrushPerformanceChecks {
    static void run(StringBuilder report) throws Exception {
        for (int scenario=0;scenario<8;scenario++) {
            int size=scenario==7?256:scenario==0||scenario==5?64:128;
            ToolSettings settings=ToolSettings.defaults(ToolSettings.Tool.BRUSH)
                    .head(scenario==4?ToolSettings.Head.FILBERT:ToolSettings.Head.FLAT)
                    .size(size).tilt(true).headThickness(scenario==3?0:10)
                    .bristles(scenario==2?75:0);
            long[] times=new long[3];String fingerprint="";
            for(int run=-1;run<3;run++) {
                ToneDocument doc=new ToneDocument(768,512);
                long start=System.nanoTime();
                PressureStroke stroke=new PressureStroke(doc,settings,0,123);
                for(int i=0;i<120;i++)
                    stroke.sample(100+i*4,220+(float)Math.sin(i*.08)*80,
                            scenario>=5?.45f:.14f+(float)Math.sin(i*.11)*.06f,
                            (float)Math.sin(i*.07)*12,60);
                stroke.finish();
                long elapsed=System.nanoTime()-start;
                if(run>=0)times[run]=elapsed;
                byte[] hash=MessageDigest.getInstance("SHA-256").digest(doc.snapshot());
                StringBuilder hex=new StringBuilder();for(byte value:hash)hex.append(String.format(Locale.US,"%02x",value&255));
                if(run>=0&&!fingerprint.equals(hex.toString()))throw new AssertionError("Non-deterministic brush replay");
                fingerprint=hex.toString();
            }
            Arrays.sort(times);
            report.append(String.format(Locale.US,"case=%d head=%s size=%d thickness=%d texture=%d median_ms=%.2f per_sample_ms=%.3f sha256=%s%n",
                    scenario,settings.head,size,settings.headThickness,settings.bristles,
                    times[1]/1e6,times[1]/120e6,fingerprint));
        }
        toolReplay(report);
        wetReplay(report);
        transparentReplay(report);
    }
    /** Input cost of an opaque brush and a transparent one, plain or with flowing watercolor paint: each pen event's stamps, and the watercolor laid in full after, before any animation. */
    private static void transparentReplay(StringBuilder report) throws Exception {
        for (String mode : new String[]{"opaque", "transparent", "watercolor"}) for (int size : new int[]{48, 96, 128}) for (int step : new int[]{4, 16}) {
            long[] times = new long[3], laying = new long[3]; long worst = 0; String fingerprint = "";
            boolean flows = mode.equals("watercolor");
            for (int run = -1; run < 3; run++) {
                ToneDocument doc = new ToneDocument(1404, 1872);
                WetWatercolor wet = flows ? new WetWatercolor(doc) : null;
                ToolSettings settings = ToolSettings.defaults(ToolSettings.Tool.BRUSH).size(size).flows(flows);
                BrushInk ink = new BrushInk(true); ink.tint(120);
                long start = System.nanoTime(), max = 0;
                DrawingStroke stroke = new PressureStroke(doc, settings, 120, wet, !mode.equals("opaque"), false, false, ink);
                for (int i = 0; i < 60; i++) {
                    long sample = System.nanoTime();
                    stroke.sample(200 + i * step, 900 + (float)Math.sin(i * .08) * 80, .6f, 0, 60);
                    max = Math.max(max, System.nanoTime() - sample);
                }
                stroke.finish();
                long shown = System.nanoTime();
                // Watercolor dabs show at once and are laid in full afterwards.
                if (wet != null) wet.layQueued(false);
                if (run >= 0) { times[run] = shown - start; laying[run] = System.nanoTime() - shown; worst = Math.max(worst, max); }
                byte[] hash = MessageDigest.getInstance("SHA-256").digest(doc.snapshot());
                StringBuilder hex = new StringBuilder();
                for (byte value : hash) hex.append(String.format(Locale.US, "%02x", value & 255));
                fingerprint = hex.toString();
            }
            Arrays.sort(times); Arrays.sort(laying);
            report.append(String.format(Locale.US, "brush %s size=%d step=%d per_sample_ms=%.2f worst_sample_ms=%.2f laid_after_ms=%.0f sha256=%s%n",
                    mode, size, step, times[1] / 60e6, worst / 1e6, laying[1] / 1e6, fingerprint));
        }
    }
    private static void wetReplay(StringBuilder report) throws Exception {
        for (int size : new int[]{48, 128}) {
            long[] input = new long[3], animation = new long[3], worst = new long[3], settle = new long[3];
            int frames = 0;
            String fingerprint = "";
            for (int run = -1; run < 3; run++) {
                ToneDocument doc = new ToneDocument(768, 512);
                doc.begin();
                for (int y = 100; y < 400; y++) for (int x = 80; x < 250; x++) doc.paintTone(x, y, 40);
                doc.finish();
                WetWatercolor wet = new WetWatercolor(doc);
                ToolSettings settings = ToolSettings.defaults(ToolSettings.Tool.WET_BRUSH_PEN).size(size);
                long start = System.nanoTime();
                DrawingStroke stroke = new PressureStroke(doc, settings, 0, wet, false);
                for (int i = 0; i < 120; i++) stroke.sample(100 + i * 4, 220 + (float)Math.sin(i * .08) * 80, .6f, 60, 0);
                stroke.finish();
                long inputTime = System.nanoTime() - start, max = 0;
                start = System.nanoTime();
                for (int frame = 0; frame < 12; frame++) {
                    long slice = System.nanoTime();
                    wet.advance(false, 16, 2_500_000, 192);
                    max = Math.max(max, System.nanoTime() - slice);
                    while (wet.framePending()) {
                        slice = System.nanoTime();
                        wet.advance(false, 16, 2_500_000, 192);
                        max = Math.max(max, System.nanoTime() - slice);
                    }
                }
                long twelve = System.nanoTime() - start;
                // Then run on to the settled look, as flowing water does once the pen lifts.
                frames = 12;
                while (wet.isAnimating()) {
                    wet.advance(false, 64, 5_000_000, 192);
                    while (wet.framePending()) wet.advance(false, 64, 5_000_000, 192);
                    frames++;
                }
                if (run >= 0) { input[run] = inputTime; animation[run] = twelve; worst[run] = max; settle[run] = System.nanoTime() - start; }
                byte[] hash = MessageDigest.getInstance("SHA-256").digest(doc.snapshot());
                StringBuilder hex = new StringBuilder();
                for (byte value : hash) hex.append(String.format(Locale.US, "%02x", value & 255));
                fingerprint = hex.toString();
            }
            Arrays.sort(input); Arrays.sort(animation); Arrays.sort(worst); Arrays.sort(settle);
            report.append(String.format(Locale.US, "wet_pen size=%d input_ms=%.2f animation_ms=%.2f max_slice_ms=%.2f settle_ms=%.0f frames=%d sha256=%s%n",
                    size, input[1]/1e6, animation[1]/1e6, worst[1]/1e6, settle[1]/1e6, frames, fingerprint));
        }
    }
    private static void toolReplay(StringBuilder report) {
        for(ToolSettings.Tool tool:new ToolSettings.Tool[]{ToolSettings.Tool.BRUSH,ToolSettings.Tool.ERASER,ToolSettings.Tool.AIRBRUSH}) {
            long[] times=new long[3];
            for(int run=-1;run<3;run++) {
                ToneDocument doc=new ToneDocument(768,512);
                ToolSettings settings=ToolSettings.defaults(tool).size(128);
                if(tool==ToolSettings.Tool.ERASER) {
                    doc.begin();for(int y=0;y<doc.height;y++)for(int x=0;x<doc.width;x++)doc.paintTone(x,y,0);doc.finish();
                }
                long start=System.nanoTime();
                DrawingStroke stroke=tool==ToolSettings.Tool.BRUSH?new PressureStroke(doc,settings,0)
                        :new ToolStroke(doc,settings,tool==ToolSettings.Tool.ERASER?255:0);
                for(int i=0;i<120;i++)stroke.sample(100+i*4,220+(float)Math.sin(i*.08)*80,.45f,0,60);
                stroke.finish();if(run>=0)times[run]=System.nanoTime()-start;
            }
            Arrays.sort(times);report.append(String.format(Locale.US,"tool=%s size=128 median_ms=%.2f per_sample_ms=%.3f%n",tool,times[1]/1e6,times[1]/120e6));
        }
    }
    private BrushPerformanceChecks() {}
}
