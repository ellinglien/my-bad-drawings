package io.github.mpdairy.monopaint;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

/**
 * The device's letters for a notebook cover's label ({@link NotebookCovers#lettering}): each of its words in iA Writer
 * Duo (400 or 700), ink on the label's white, from where {@link NotebookCovers#label} put it along its baseline. Drawn on
 * any thread: the cover's paper is made on the drawing thread, the shelf's and the copies' in the background.
 */
final class CoverLettering implements NotebookCovers.Lettering {
    @Override public void letter(byte[] tones, int width, int height, NotebookCovers.Label label) {
        int w = Math.min(label.width, width - label.x), h = Math.min(label.height, height - label.y);
        if (w <= 0 || h <= 0 || label.x < 0 || label.y < 0) return;
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        paint.setTextAlign(Paint.Align.LEFT); paint.setColor(Theme.INK);
        Bitmap letters = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8);
        try {
            Canvas canvas = new Canvas(letters);
            for (NotebookCovers.Words words : label.words) {
                paint.setTypeface(Theme.face(words.weight)); paint.setTextSize(words.size);
                canvas.drawText(words.text, words.x - label.x, words.baseline - label.y, paint);
            }
            int row = letters.getRowBytes();
            byte[] alpha = new byte[row * h];
            letters.copyPixelsToBuffer(java.nio.ByteBuffer.wrap(alpha));
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) {
                    int a = alpha[y * row + x] & 255;
                    if (a == 0) continue;
                    int o = (label.y + y) * width + label.x + x;
                    tones[o] = (byte) (((tones[o] & 255) * (255 - a) + 127) / 255);
                }
        } finally { letters.recycle(); }
    }
}
