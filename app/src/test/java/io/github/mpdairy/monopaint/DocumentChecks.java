package io.github.mpdairy.monopaint;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

/** Dependency-free host checks: run with scripts/test_document.sh. */
public final class DocumentChecks {
    public static void main(String[] args) throws Exception {
        calibration(); transactions(); maskAndClipping(); smoothEdges(); watercolor(); clearCanvas(); persistence(); strokeBaseBlocks(); toneBlocks(); eraseBlocks();
        System.out.println("PASS: calibration, coordinate anchoring, opaque logical tones, gesture undo/redo, clipping, codec and damaged-file rejection");
    }
    /** strokeBaseTones gives a block of what strokeBaseTone gives pixel by pixel, mid-gesture, across tiles and off the page. */
    private static void strokeBaseBlocks() {
        ToneDocument doc = new ToneDocument(150, 130);
        doc.begin(); for (int y = 0; y < 130; y++) for (int x = 0; x < 150; x++) doc.setTone(x, y, (x * 7 + y * 3) % 256); doc.finish();
        doc.begin();
        for (int y = 20; y < 90; y++) for (int x = 50; x < 140; x++) doc.setTone(x, y, 3);
        int[][] blocks = {{0, 0, 150, 130}, {60, 60, 10, 10}, {-5, -3, 80, 70}, {120, 100, 50, 50}};
        for (int[] b : blocks) {
            int[] out = new int[b[2] * b[3]];
            doc.strokeBaseTones(b[0], b[1], b[2], b[3], out);
            for (int y = 0; y < b[3]; y++) for (int x = 0; x < b[2]; x++) {
                int px = b[0] + x, py = b[1] + y;
                if (px < 0 || py < 0 || px >= 150 || py >= 130) continue;
                check(out[y * b[2] + x] == doc.strokeBaseTone(px, py), "strokeBaseTones matches strokeBaseTone at " + px + "," + py);
            }
        }
        doc.finish();
    }
    /** setTones lays a block exactly as setTone would pixel by pixel: the same pixels, dirty area and undo. */
    private static void toneBlocks() {
        java.util.Random random = new java.util.Random(3);
        ToneDocument blocks = new ToneDocument(150, 130), pixels = new ToneDocument(150, 130);
        for (ToneDocument doc : new ToneDocument[]{blocks, pixels}) {
            doc.begin(); for (int y = 0; y < 130; y++) for (int x = 0; x < 150; x++) if ((x ^ y) % 5 == 0) doc.setTone(x, y, (x * 7 + y * 3) % 256); doc.finish();
            doc.clearDirty();
        }
        int[][] areas = {{10, 10, 70, 70}, {-20, 90, 90, 60}, {100, -5, 80, 30}};
        for (int[] a : areas) {
            int[] grays = new int[a[2] * a[3]];
            for (int i = 0; i < grays.length; i++) grays[i] = random.nextInt(4) == 0 ? -1 : random.nextInt(4) == 0 ? 255 : random.nextInt(256);
            blocks.begin(); blocks.setTones(a[0], a[1], a[2], a[3], grays); blocks.finish();
            pixels.begin();
            for (int y = 0; y < a[3]; y++) for (int x = 0; x < a[2]; x++) if (grays[y * a[2] + x] >= 0) pixels.setTone(a[0] + x, a[1] + y, grays[y * a[2] + x]);
            pixels.finish();
            check(Arrays.equals(blocks.dirty(), pixels.dirty()), "setTones marks the same dirty area");
            blocks.clearDirty(); pixels.clearDirty();
            check(sameLayers(blocks, pixels), "setTones lays the same pixels as setTone");
        }
        long before = blocks.revision();
        int[] same = new int[20 * 20];
        for (int y = 0; y < 20; y++) for (int x = 0; x < 20; x++) same[y * 20 + x] = blocks.tone(30 + x, 30 + y);
        blocks.begin(); long begun = blocks.revision(); blocks.setTones(30, 30, 20, 20, same);
        check(blocks.revision() == begun && blocks.dirty() == null, "A block that changes nothing leaves the page's revision and dirty area alone");
        check(!blocks.finish() && before != begun, "and is no edit");
        for (int i = 0; i < areas.length; i++) { blocks.undo(); pixels.undo(); check(sameLayers(blocks, pixels), "Undo takes back the same pixels"); }
    }
    /** eraseTones erases a block's marked pixels exactly as eraseTone(x, y, 1) would one by one. */
    private static void eraseBlocks() {
        ToneDocument blocks = new ToneDocument(150, 130), pixels = new ToneDocument(150, 130);
        for (ToneDocument doc : new ToneDocument[]{blocks, pixels}) {
            // Paint, white paint over paper, and bare paper.
            doc.begin();
            for (int y = 0; y < 130; y++) for (int x = 0; x < 150; x++) if ((x + y) % 4 != 0) doc.paintTone(x, y, (x * 5 + y) % 3 == 0 ? 255 : (x * 7 + y * 3) % 256, 1 + (x * y) % 255);
            doc.finish(); doc.clearDirty();
        }
        int[][] areas = {{10, 10, 70, 70}, {-20, 90, 90, 60}, {100, -5, 80, 30}};
        for (int[] a : areas) {
            byte[] mask = new byte[a[2] * a[3]];
            for (int i = 0; i < mask.length; i++) mask[i] = (byte)(i % 3 == 0 ? 0 : i % 5 + 1);
            blocks.begin(); blocks.eraseTones(a[0], a[1], a[2], a[3], mask);
            pixels.begin();
            for (int y = 0; y < a[3]; y++) for (int x = 0; x < a[2]; x++) if (mask[y * a[2] + x] != 0) pixels.eraseTone(a[0] + x, a[1] + y, 1);
            check(blocks.finish() == pixels.finish(), "eraseTones changes the page when eraseTone would");
            check(Arrays.equals(blocks.dirty(), pixels.dirty()), "eraseTones marks the same dirty area");
            blocks.clearDirty(); pixels.clearDirty();
            check(sameLayers(blocks, pixels), "eraseTones leaves the same pixels as eraseTone");
        }
        for (int i = 0; i < areas.length; i++) { blocks.undo(); pixels.undo(); check(sameLayers(blocks, pixels), "Undo restores the same pixels"); }
    }
    private static boolean sameLayers(ToneDocument a, ToneDocument b) {
        ToneDocument.Snapshot x = a.layerSnapshot(), y = b.layerSnapshot();
        for (int i = 0; i < x.layers.size(); i++)
            if (!Arrays.equals(x.layers.get(i).tones, y.layers.get(i).tones) || !Arrays.equals(x.layers.get(i).alpha, y.layers.get(i).alpha)) return false;
        return true;
    }
    private static void calibration() {
        int[] accepted = {0,4,6,7,10,13,16,17,22,27,32,35,38,42,50,64};
        int previous = -1;
        for (int gray = 0; gray < 256; gray++) {
            int whites = 0;
            for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
                int pixel = DotPattern.pixel(gray, x, y);
                check(pixel == 0xffffffff || pixel == 0xff000000, "Only opaque black/white");
                if (pixel == 0xffffffff) whites++;
                check(pixel == DotPattern.pixel(gray, x + 24, y + 56), "Stable tile phase");
            }
            check(whites >= previous, "Monotonic intermediate tones"); previous = whites;
        }
        for (int i = 0; i < accepted.length; i++) check(DotPattern.whiteCount(GrayPalette.VALUES[i]) == accepted[i], "0.12 density " + i);
        boolean[] densities = new boolean[65];
        for (int position = 0; position < 256; position++) {
            int tone = DotPattern.pickerTone(position), density = DotPattern.whiteCount(tone);
            densities[density] = true;
            check(DotPattern.whiteCount(DotPattern.pickerTone(DotPattern.pickerPosition(tone))) == density, "Picker round trip");
        }
        for (boolean reachable : densities) check(reachable, "Every density reachable, including light end");
        int last=-1,distinct=0;
        for(int gray=0;gray<256;gray++) {
            int exported=DotPattern.exportGray(gray);
            check(exported>=last&&exported>=0&&exported<=255,"Smooth export is monotonic and bounded");
            check(Math.abs(exported-DotPattern.whiteCount(gray)*255f/64)<=2.5f,"Export follows calibrated coverage without density rounding");
            if(exported!=last)distinct++;last=exported;
        }
        check(distinct>65,"Smooth export avoids quantizing to 65 dot densities");
        check(DotPattern.exportGray(0)==0&&DotPattern.exportGray(255)==255,"Export preserves pure black/white");
        for(int i=0;i<accepted.length;i++)check(DotPattern.exportGray(GrayPalette.VALUES[i])==Math.round(accepted[i]*255f/64),"Export matches calibration anchor "+i);
    }
    private static void transactions() {
        ToneDocument doc = new ToneDocument(137, 91);
        doc.begin();
        for (int y = 0; y < doc.height; y++) for (int x = 0; x < doc.width; x++) doc.setTone(x, y, (x + y) & 255);
        check(doc.finish(), "Edit captured"); byte[] painted = doc.snapshot();
        check(doc.undo(), "Whole stroke undo");
        for (byte value : doc.snapshot()) check((value & 255) == 255, "Whole stroke restores paper");
        check(!doc.undo(), "One action for many writes");
        check(doc.redo() && Arrays.equals(painted, doc.snapshot()), "Exact redo including partial edge tiles");
        doc.undo(); doc.begin(); doc.setTone(0, 0, 255);
        check(!doc.finish() && doc.canRedo(), "No-op does not discard redo");
        doc.begin(); doc.setTone(0, 0, 80); doc.finish(); check(!doc.canRedo(), "Changed branch discards redo");
        check(doc.tone(0, 0) == 80, "Keep logical shade, not binary dots");
        int[] whole = new int[doc.width * doc.height], patch = new int[17 * 11];
        doc.render(whole, 0, 0, doc.width, doc.height); doc.render(patch, 3, 5, 17, 11);
        for (int y = 0; y < 11; y++) for (int x = 0; x < 17; x++)
            check(patch[y * 17 + x] == whole[(y + 5) * doc.width + x + 3], "Dirty render anchors to document");
        doc.clearDirty(); check(doc.dirty() == null, "Dirty reset");
    }
    private static void maskAndClipping() {
        ToneDocument doc = new ToneDocument(4, 4);
        doc.begin();
        doc.paintMask(new int[]{0, 0xff000000, 0xffffffff, 0}, 2, -1, -1, 2, 2, 80);
        check(!doc.finish(), "Off-canvas and transparent pixels ignored");
        doc.begin(); doc.paintMask(new int[]{0xff000000, 0, 0xff000000, 0xff000000}, 2, 0, 0, 2, 2, 80); doc.finish();
        check(doc.tone(0,0) == 80 && doc.tone(1,0) == 255, "Opaque mask");
        doc.begin(); doc.setTone(0,0,255); doc.finish();
        check(doc.tone(0,0) == 255 && doc.tone(0,1) == 80, "White overpainting retains neighbors");
    }
    /** Anti-aliased edges: partial mask alpha covers partly, and only the strongest contact in a gesture counts. */
    private static void smoothEdges() {
        int half = 0x80000000, quarter = 0x40000000, full = 0xff000000;
        ToneDocument doc = new ToneDocument(4, 1);
        doc.begin(); doc.paintMask(new int[]{half, quarter, full, 0}, 4, 0, 0, 4, 1, 0);
        int partial = doc.tone(0, 0);
        check(partial > 120 && partial < 135 && doc.tone(1, 0) > partial && doc.tone(2, 0) == 0 && doc.tone(3, 0) == 255,
                "Edge pixels cover by their mask share (" + partial + ")");
        doc.paintMask(new int[]{half, half, quarter, half}, 4, 0, 0, 4, 1, 0);
        check(doc.tone(0, 0) == partial && doc.tone(1, 0) == partial && doc.tone(2, 0) == 0,
                "Overlapping edges keep the strongest contact instead of compounding");
        doc.paintMask(new int[]{full, 0, 0, 0}, 4, 0, 0, 4, 1, 0);
        check(doc.tone(0, 0) == 0, "Full contact replaces an earlier edge");
        doc.finish();
        check(doc.undo() && doc.tone(0, 0) == 255 && doc.tone(1, 0) == 255, "Smooth edges undo exactly");
        // Over a transparent layer the edge is the brush's pigment at partial coverage, so it composites cleanly.
        doc = new ToneDocument(1, 1); doc.begin(); doc.paintMask(new int[]{half}, 1, 0, 0, 1, 1, 60); doc.finish();
        check(doc.opacity(0, 0) > 120 && doc.opacity(0, 0) < 135 && doc.compositeTone(0, 0) > 60 && doc.compositeTone(0, 0) < 255,
                "A partial edge carries the brush's tone at partial coverage");
        doc = new ToneDocument(2, 1); doc.begin(); doc.paintMask(new int[]{full, full}, 2, 0, 0, 2, 1, 0); doc.finish();
        doc.begin(); doc.eraseMask(new int[]{half, full}, 2, 0, 0, 2, 1); doc.eraseMask(new int[]{quarter, 0}, 2, 0, 0, 2, 1); doc.finish();
        check(doc.opacity(0, 0) > 120 && doc.opacity(0, 0) < 135 && doc.opacity(1, 0) == 0, "Smooth eraser edges remove only their share");
    }
    private static void watercolor() throws Exception {
        int width=19,height=17,stride=25;
        byte[] original=new byte[width*height];
        for(int i=0;i<original.length;i++)original[i]=(byte)(i%256);
        int[] mask=new int[stride*21];
        for(int i=0;i<mask.length;i++)mask[i]=i%5==0?0:0xff000000;
        for(int gray=0;gray<256;gray++) {
            ToneDocument doc=new ToneDocument(width,height,original);
            doc.begin();doc.washMask(mask,stride,-3,-2,23,21,gray);doc.finish();
            byte[] painted=doc.snapshot();
            int[] rendered=new int[width*height];doc.render(rendered,0,0,width,height);
            for(int y=0;y<height;y++)for(int x=0;x<width;x++) {
                int base=original[y*width+x]&255;
                boolean deposit=mask[(y+2)*stride+x+3]!=0&&DotPattern.pixel(gray,x,y)==0xff000000;
                check(doc.tone(x,y)==(deposit?0:base),"Wash writes only black dots; gaps retain exact underlying tones");
                check(rendered[y*width+x]==(deposit?0xff000000:DotPattern.pixel(base,x,y)),"Wash render preserves existing dots and coordinate phase");
            }
            doc.begin();doc.washMask(mask,stride,-3,-2,23,21,gray);
            check(!doc.finish()&&Arrays.equals(painted,doc.snapshot()),"Repeated wash keeps stable density");
            if(DotPattern.whiteCount(gray)==64)check(!doc.canUndo()&&Arrays.equals(original,painted),"White wash adds no ink or history");
            else {
                check(doc.undo()&&Arrays.equals(original,doc.snapshot()),"Wash undo restores all underlying tones");
                check(doc.redo()&&Arrays.equals(painted,doc.snapshot()),"Wash redo is exact");
            }
            doc.begin();doc.washMask(mask,stride,-3,-2,23,21,0);doc.cancel();
            check(Arrays.equals(painted,doc.snapshot()),"Canceled wash restores underlying tones");
            ByteArrayOutputStream saved=new ByteArrayOutputStream();DocumentCodec.write(saved,width,height,painted);
            check(Arrays.equals(painted,DocumentCodec.read(new ByteArrayInputStream(saved.toByteArray())).snapshot()),"Wash survives save and reload");
        }
        int[] full=new int[64];Arrays.fill(full,0xff000000);
        for(int gray=0;gray<256;gray++) {
            ToneDocument paper=new ToneDocument(8,8);paper.begin();paper.washMask(full,8,0,0,8,8,gray);paper.finish();
            int[] rendered=new int[64];paper.render(rendered,0,0,8,8);
            for(int i=0;i<64;i++)check(rendered[i]==DotPattern.pixel(gray,i%8,i/8),"Wash on paper matches shade picker density exactly");
        }
        System.out.println("PASS: watercolor black-only deposit, untouched gaps, every shade, clipped masks, repeat density, cancel, undo/redo and persistence");
    }
    private static void clearCanvas() {
        ToneDocument doc=new ToneDocument(137,95);
        doc.begin();doc.setTone(0,0,0);doc.setTone(70,55,137);doc.setTone(136,94,249);doc.finish();
        byte[] painted=doc.snapshot();check(doc.clear(),"Clear changes a marked canvas");
        for(byte tone:doc.snapshot())check((tone&255)==255,"Clear includes disconnected marks and partial tiles");
        check(doc.undo()&&Arrays.equals(painted,doc.snapshot()),"Clear is one exact undo");
        check(doc.redo(),"Clear redoes");check(!doc.clear(),"Clear on blank paper is a no-op");
        check(doc.undo()&&Arrays.equals(painted,doc.snapshot()),"No-op clear leaves history intact");
    }
    private static void persistence() throws Exception {
        byte[] tones = new byte[256 * 3];
        for (int i = 0; i < tones.length; i++) tones[i] = (byte)i;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        DocumentCodec.write(output, 256, 3, tones); byte[] file = output.toByteArray();
        ToneDocument restored = DocumentCodec.read(new ByteArrayInputStream(file));
        check(restored.width == 256 && restored.height == 3 && Arrays.equals(tones, restored.snapshot()), "Every tone round trips");
        for (int length : new int[]{0, 3, 19, file.length - 3}) rejects(Arrays.copyOf(file, length));
        byte[] broken = file.clone(); broken[19] ^= 1; rejects(broken);
        broken = file.clone(); broken[4] = 0x7f; rejects(broken);
        broken = file.clone(); broken[0] = 0; rejects(broken);
    }
    private static void rejects(byte[] file) throws Exception {
        try { DocumentCodec.read(new ByteArrayInputStream(file)); throw new AssertionError("Accepted damaged drawing"); }
        catch (IOException expected) { /* required */ }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
