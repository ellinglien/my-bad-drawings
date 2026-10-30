package io.github.mpdairy.monopaint;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

/**
 * Import PDF (the owner, 2026-10-22; docs/archive/superpowers/specs/2026-10-22-import-design.md): a PDF made a new notebook named
 * after it, a page a PDF page ({@link PictureImport}: its page locked at the bottom, a clear layer on it), the first
 * {@link PictureImport#MOST_PDF_PAGES} of them and no more than the notebook's 200 MB. Each page is drawn by Android's
 * {@link PdfRenderer} at the notebook's page size, fitted and centred, in greys ({@link PictureImport#greys}), and packed
 * into a file of its own in the app's cache as it goes, so the pages never all sit in memory; once all are done the
 * store writes the notebook's file whole from it ({@link DocumentStore#create}). Cancelled, failed or killed part way,
 * nothing is in the library: never a half notebook. One at a time, on a thread of its own.
 */
final class PdfImport {
    /** How far it is: {@code done} pages of {@code of} (on its thread). */
    interface Progress { void at(int done, int of); }
    /** What it made: the notebook's path, its pages, and whether the PDF had more (the 49, or the 200 MB). */
    static final class Made {
        final String path; final int pages; final boolean cut;
        Made(String path, int pages, boolean cut) { this.path = path; this.pages = pages; this.cut = cut; }
    }
    private volatile boolean cancelled;
    /** Stops it before its next page; nothing is written. */
    void cancel() { cancelled = true; }
    boolean cancelled() { return cancelled; }

    /**
     * Imports the PDF at {@code uri} as a {@code width × height} notebook in covers of {@code design}, made {@code day};
     * {@code done} hears its notebook, or null when cancelled, or the error. Off the main thread.
     */
    void run(Context context, Uri uri, int width, int height, CoverDesign design, long day, DocumentStore store, Progress progress, DocumentStore.Result<Made> done) {
        Thread work = new Thread(() -> {
            File temp = new File(context.getCacheDir(), "pdf import.pages");
            PackedPage.Source[] source = {null};
            // Until the store has the pages, the cache's file goes with whatever ends this (cancelled, failed).
            boolean handed = false;
            try {
                String name = name(context, uri);
                List<long[]> written = new ArrayList<>();
                boolean cut;
                try (ParcelFileDescriptor file = context.getContentResolver().openFileDescriptor(uri, "r")) {
                    if (file == null) throw new IOException(PictureImport.UNREADABLE);
                    try (PdfRenderer pdf = new PdfRenderer(file); FileOutputStream out = new FileOutputStream(temp)) {
                        int count = pdf.getPageCount(), n = PictureImport.pdfPages(count);
                        if (n == 0) throw new IOException(PictureImport.NO_PAGES);
                        cut = n < count;
                        long blank = BlankPages.made(width, height).bytes.length, packed = 0, offset = 0;
                        Bitmap drawn = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                        int[] pixels = new int[width * height];
                        try {
                            for (int i = 0; i < n; i++) {
                                if (cancelled) { done.complete(null, null); return; }
                                progress.at(i, n);
                                draw(pdf, i, drawn);
                                drawn.getPixels(pixels, 0, width, 0, 0, width, height);
                                byte[] bytes = DrawingBook.encode(PictureImport.pdfPage(width, height, PictureImport.greys(pixels)));
                                // The 200 MB: the pages that fit, and the PDF said cut short.
                                if (!PictureImport.fits(packed, bytes.length + blank)) { cut = true; break; }
                                CRC32 crc = new CRC32(); crc.update(bytes);
                                out.write(bytes);
                                written.add(new long[]{offset, bytes.length, crc.getValue()});
                                offset += bytes.length; packed += bytes.length + blank;
                            }
                        } finally { drawn.recycle(); }
                    }
                }
                if (written.isEmpty()) throw new IOException(PictureImport.NO_PAGES);
                if (cancelled) { done.complete(null, null); return; }
                progress.at(written.size(), written.size());
                source[0] = PackedPage.Source.open(temp);
                List<PackedPage> pages = new ArrayList<>();
                for (long[] w : written) pages.add(PackedPage.in(source[0], w[0], (int) w[1], w[2]));
                PackedPage clear = PackedPage.of(BlankPages.made(width, height).bytes);
                boolean shortened = cut;
                handed = true;
                store.create(name, out -> PictureImport.writeBook(out, width, height, pages, clear, design, day), (path, error) -> {
                    source[0].close(); temp.delete();
                    done.complete(path == null ? null : new Made(path, pages.size(), shortened), error);
                });
            } catch (Exception | OutOfMemoryError error) {
                Log.e(ProbeActivity.TAG, "Import PDF failed", error);
                done.complete(null, error instanceof Exception ? (Exception) error : new IOException(error.toString()));
            } finally {
                if (!handed) { if (source[0] != null) source[0].close(); temp.delete(); }
            }
        }, "import pdf");
        work.setPriority(Thread.MIN_PRIORITY);
        work.start();
    }

    /** Page {@code i} of {@code pdf} drawn on white into {@code drawn}, fitted inside it and centred. */
    private static void draw(PdfRenderer pdf, int i, Bitmap drawn) {
        drawn.eraseColor(Color.WHITE);
        try (PdfRenderer.Page page = pdf.openPage(i)) {
            int[] fit = PictureImport.fit(page.getWidth(), page.getHeight(), drawn.getWidth(), drawn.getHeight());
            Matrix place = new Matrix();
            place.setScale((float) fit[2] / page.getWidth(), (float) fit[3] / page.getHeight());
            place.postTranslate(fit[0], fit[1]);
            page.render(drawn, null, place, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
        }
    }
    /** One page of a PDF ({@link #page}) and how many pages the PDF has. */
    static final class Page {
        final Bitmap picture; final int count;
        Page(Bitmap picture, int count) { this.picture = picture; this.count = count; }
    }
    /**
     * Page {@code i} of the PDF at {@code uri} (its last when it has fewer), as large as fits {@code width × height}, its
     * shape kept, on white: Import's This Page (placed as a picture is) and its preview. Off the main thread.
     */
    static Page page(Context context, Uri uri, int i, int width, int height) throws IOException {
        try (ParcelFileDescriptor file = context.getContentResolver().openFileDescriptor(uri, "r")) {
            if (file == null) throw new IOException(PictureImport.UNREADABLE);
            try (PdfRenderer pdf = new PdfRenderer(file)) {
                int count = pdf.getPageCount();
                if (count == 0) throw new IOException(PictureImport.NO_PAGES);
                int at = Math.max(0, Math.min(i, count - 1));
                int[] fit;
                try (PdfRenderer.Page page = pdf.openPage(at)) { fit = PictureImport.fit(page.getWidth(), page.getHeight(), width, height); }
                Bitmap drawn = Bitmap.createBitmap(fit[2], fit[3], Bitmap.Config.ARGB_8888);
                try { draw(pdf, at, drawn); }
                catch (RuntimeException failed) { drawn.recycle(); throw failed; }
                return new Page(drawn, count);
            }
        }
    }

    /** The PDF's file name without ".pdf", as a notebook's name ({@link NotebookMigration#safeName}); "PDF" when it has none. */
    private static String name(Context context, Uri uri) {
        String name = null;
        try (android.database.Cursor row = context.getContentResolver().query(uri, new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (row != null && row.moveToFirst()) name = row.getString(0);
        } catch (RuntimeException unknown) { name = null; }
        if (name == null) name = uri.getLastPathSegment() == null ? "" : uri.getLastPathSegment();
        if (name.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")) name = name.substring(0, name.length() - 4);
        return NotebookMigration.safeName(name, "PDF");
    }
}
