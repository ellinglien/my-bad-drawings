package io.github.mpdairy.monopaint;

import android.os.Environment;
import android.util.Log;
import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Writes a notebook's copies when asked, Export Now alone (the owner, 2026-10-24: "export only when you need it"; once
 * also whenever a notebook or the app was left): a grey PNG a page in EXPORT/<app folder>/<notebook>
 * and a PDF of them all in the Documents beside the notebooks, Document/<app folder>/<title>.pdf, so it shows in the
 * Supernote's Documents and can be searched ({@link NotebookPdf}; a stack of layers one page, flattened;
 * {@link NotebookExport}: only pages that changed are drawn again), on a worker of its own at the lowest priority.
 * Smooth true greys, not the screen's dots. Drawings' PNG copies from before notebooks (EXPORT/<app folder>/<drawing>.png)
 * stay as they are.
 */
final class AutoExport {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(work -> {
        Thread thread = new Thread(() -> { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_LOWEST); work.run(); }, "notebook copies");
        thread.setDaemon(true);
        return thread;
    });

    static File folder() { return new File(Environment.getExternalStorageDirectory(), PngExport.SUPERNOTE_FOLDER); }
    /** A drawing's PNG copy from before notebooks. */
    static File file(String path) { return new File(folder(), DrawingFiles.name(path) + ".png"); }
    /** Where the notebooks' PDFs go: beside the notebooks (Document/<app folder> on a Supernote). */
    static File documents() { return DrawingStorage.shared(); }
    /** Whether {@code path}'s notebook has its copies as this version writes them (its PDF in Documents recorded). */
    static boolean hasCopies(String path) { return new File(new File(folder(), DrawingFiles.name(path)), NotebookPdf.STATE).isFile(); }
    /** The notebook's PDF's title: its name without a title's hidden date ({@link DrawingNames#titled}). */
    static String title(String path) { String name = DrawingFiles.name(path); return DrawingNames.titled(name) ? name.substring(11) : name; }
    private static NotebookExport.Documents documents(String path) { return new NotebookExport.Documents(documents(), title(path), java.time.LocalDate.now()); }
    /** Tells the media index the PDF is there (the Documents app may list from it). */
    private static void scan(PaintActivity app, NotebookExport.Done done) {
        android.media.MediaScannerConnection.scanFile(app.getApplicationContext(), new String[]{done.pdf.getPath()}, null, null);
    }

    /**
     * Queues {@code path}'s copies, from {@code book} (a snapshot: its pages don't change under the worker); {@code
     * failed} (logged) or {@code done} (the owner, 2026-10-22: it says where the PDF is) run on the main thread after;
     * whether they were queued (not where shared storage isn't allowed).
     */
    static boolean notebook(PaintActivity app, String path, DrawingBook.Snapshot book, Runnable failed, Runnable done) {
        if (path.isEmpty() || book == null || !DrawingStorage.sharedAllowed(app)) return false;
        Queued queued;
        synchronized (QUEUED) {
            // A request for a notebook whose copies are still waiting their turn takes its place: one export, of the latest.
            Queued waiting = QUEUED.get(path);
            if (waiting != null) { waiting.book.release(); waiting.book = book; waiting.failed = both(waiting.failed, failed); waiting.done = both(waiting.done, done); return true; }
            QUEUED.put(path, queued = new Queued(book, failed, done));
        }
        WORKER.execute(() -> {
            DrawingBook.Snapshot latest; Runnable fail, then;
            synchronized (QUEUED) { if (QUEUED.get(path) == queued) QUEUED.remove(path); latest = queued.book; fail = queued.failed; then = queued.done; }
            try {
                List<PackedPage> pages = latest.packed();
                PackedPage[] covers = latest.packedCovers();
                // Packed: the snapshot's page isn't read again, and the page drawn on needn't copy its layers at its next stroke.
                latest.release();
                // The pages' fridge magnets drawn over them, as the PNG copy always had them; the covers first and last.
                scan(app, NotebookExport.update(folder(), DrawingFiles.name(path), pages, WordsMode.exportTiles(), covers(path, latest.width, latest.height, covers, latest.cover, latest.design), latest.options, latest.pageTemplates, latest.tracing, documents(path)));
                if (then != null) app.runOnUiThread(then);
            } catch (Exception | OutOfMemoryError error) {
                // Only the copies are lost (the notebook is saved on its own): the next time tries again.
                latest.release();
                Log.e(ProbeActivity.TAG, "Notebook copies failed", error);
                if (fail != null) app.runOnUiThread(fail);
            }
        });
        return true;
    }
    /**
     * Queues page {@code place}'s (a stack's) {@code choice} copy (Export Page, {@link PageExport}) from {@code book} (a
     * snapshot), in EXPORT/<app folder> beside the notebooks' PNG folders, behind any notebook's copies; {@code done} or
     * {@code failed} (logged) run on the main thread after; whether it was queued (not where shared storage isn't allowed).
     */
    static boolean page(PaintActivity app, String path, DrawingBook.Snapshot book, int place, String choice, Runnable failed, Runnable done) {
        if (path.isEmpty() || book == null || !DrawingStorage.sharedAllowed(app)) return false;
        WORKER.execute(() -> {
            try {
                List<PackedPage> pages = book.packed();
                book.release();
                PageExport.write(folder(), title(path), place, choice, pages, book.options, book.pageTemplates, book.tracing, WordsMode.exportTiles());
                app.runOnUiThread(done);
            } catch (Exception | OutOfMemoryError error) {
                book.release();
                Log.e(ProbeActivity.TAG, "Page copy failed", error);
                app.runOnUiThread(failed);
            }
        });
        return true;
    }
    /** A notebook's copies waiting their turn on {@link #WORKER}, by path: what they are made from and whom they tell. */
    private static final java.util.HashMap<String, Queued> QUEUED = new java.util.HashMap<>();
    private static final class Queued {
        DrawingBook.Snapshot book; Runnable failed, done;
        Queued(DrawingBook.Snapshot book, Runnable failed, Runnable done) { this.book = book; this.failed = failed; this.done = done; }
    }
    private static Runnable both(Runnable a, Runnable b) { return a == null ? b : b == null ? a : () -> { a.run(); b.run(); }; }
    /** Requests for {@code path} after this are queued behind what is queued now (a rename or delete of its copies). */
    private static void seal(String path) { synchronized (QUEUED) { QUEUED.remove(path); } }
    /** {@code path}'s covers for its copies: its name, its pages' size, the covers' pages, the cover picture's page and their design. */
    private static NotebookExport.Covers covers(String path, int width, int height, PackedPage[] covers, int picture, CoverDesign design) {
        return new NotebookExport.Covers(DrawingFiles.name(path), design, width, height, covers[0], covers[1], picture);
    }
    /** A notebook renamed: its copies follow. */
    static void rename(String from, String to) { seal(from); seal(to); WORKER.execute(() -> NotebookExport.rename(folder(), documents(), DrawingFiles.name(from), DrawingFiles.name(to), title(to))); }
    /** A notebook deleted: its copies go (its PDF stays when the Supernote has handwriting for it). */
    static void delete(String path) { seal(path); WORKER.execute(() -> NotebookExport.delete(folder(), documents(), DrawingFiles.name(path))); }
}
