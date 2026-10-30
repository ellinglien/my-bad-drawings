package io.github.mpdairy.monopaint;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fridge magnets' tiles as pictures ({@link Magnets}' sizes; Paintmageddon's tiles, the owner's, 2026-10-19): the word
 * in Roboto Mono Bold, ink on a paper tile with nearly square corners, a fine grey edge round it and a crisp solid ink
 * "shadow" 2 px down and right, never blurred; a picked tile in the bin is ink with the word in paper. Each is drawn
 * once, tilted and antialiased, and kept (the last {@link #KEPT}), so the page and the bin only copy pictures, and a
 * tilt's edges are smooth greys, not steps.
 */
final class MagnetTiles implements Magnets.Tiles {
    /** How many tile pictures are kept (a few MB). */
    static final int KEPT = 160;
    private final Map<String, Bitmap> kept = new LinkedHashMap<String, Bitmap>(64, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Bitmap> eldest) { return size() > KEPT; }
    };
    private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG), paper = new Paint(Paint.ANTI_ALIAS_FLAG), edge = new Paint(Paint.ANTI_ALIAS_FLAG),
            word = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final RectF box = new RectF();

    MagnetTiles() {
        ink.setColor(Theme.INK); paper.setColor(Theme.PAPER); edge.setColor(Theme.GREY_600);
        word.setTypeface(Theme.magnetFace()); word.setTextSize(Magnets.EM); word.setTextAlign(Paint.Align.CENTER);
    }

    /** {@code m}'s picture, {@link Magnets#extent} big, its centre at {@code -extent left, -extent top}. */
    Bitmap picture(Magnets.Magnet m) { return picture(m.word, m.tilt, m.turn, false); }
    /** A tile's picture; {@code picked}: ink with the word in paper (the bin's chosen word). */
    synchronized Bitmap picture(String text, int tilt, int turn, boolean picked) {
        String key = text + '\u0000' + tilt + '\u0000' + turn + (picked ? "*" : "");
        Bitmap b = kept.get(key);
        if (b == null) { b = draw(text, tilt, turn, picked); kept.put(key, b); }
        return b;
    }
    private Bitmap draw(String text, int tilt, int turn, boolean picked) {
        int[] e = Magnets.extent(text, tilt, turn);
        Bitmap b = Bitmap.createBitmap(e[2] - e[0], e[3] - e[1], Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.translate(-e[0], -e[1]);
        c.rotate((float) Magnets.degrees(tilt, turn));
        float hw = Magnets.width(text) / 2f, hh = Magnets.HEIGHT / 2f, s = Magnets.SHADOW, line = Magnets.LINE, r = Magnets.CORNER;
        box.set(-hw + s, -hh + s, hw + s, hh + s); c.drawRoundRect(box, r, r, ink);
        box.set(-hw, -hh, hw, hh); c.drawRoundRect(box, r, r, picked ? ink : edge);
        if (!picked) { box.inset(line, line); c.drawRoundRect(box, r - line, r - line, paper); }
        word.setColor(picked ? Theme.PAPER : Theme.INK);
        // A word from a fallback face may measure wider than the model's (Magnets.advance): narrowed to its room, never clipped.
        float room = 2 * hw - 2 * Magnets.PAD + 2, drawn = word.measureText(text);
        word.setTextScaleX(drawn > room ? room / drawn : 1);
        c.drawText(text, 0, -hh + Magnets.BASELINE, word);
        word.setTextScaleX(1);
        return b;
    }
    /** Draws {@code magnets} into {@code area} of {@code page} (the page's raster), bottom first, but magnet {@code skip} (-1: none). */
    void draw(Canvas page, Rect area, Magnets magnets, int skip) {
        page.save();
        page.clipRect(area);
        for (int i = 0; i < magnets.size(); i++) {
            if (i == skip) continue;
            Magnets.Magnet m = magnets.get(i);
            int[] b = Magnets.bounds(m);
            if (b[0] >= area.right || b[2] <= area.left || b[1] >= area.bottom || b[3] <= area.top) continue;
            page.drawBitmap(picture(m), b[0], b[1], null);
        }
        page.restore();
    }
    /** The PNG copy's tile ({@link Magnets#stamp}). */
    @Override public Magnets.Tile tile(Magnets.Magnet m) {
        Bitmap b = picture(m);
        int[] px = new int[b.getWidth() * b.getHeight()];
        b.getPixels(px, 0, b.getWidth(), 0, 0, b.getWidth(), b.getHeight());
        return new Magnets.Tile(px, b.getWidth(), b.getHeight());
    }
}
