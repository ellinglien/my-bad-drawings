package io.github.mpdairy.monopaint;

import android.util.AtomicFile;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** A single IO queue serializes recovery, named saves and opens. */
final class DocumentStore {
    interface Result<T> { void complete(T value, Exception error); }
    /** Read and replaced only on {@link #IO}. */
    private DrawingFiles files;
    /**
     * The working drawing's recovery file. It stays in private storage wherever the library is:
     * Supernote Cloud syncs Document/, and syncing a file rewritten every few seconds left
     * conflict copies and broken temporary files there until autosave failed.
     */
    private final File recovery;
    // Keep one process-wide queue: a recreated Activity must read after the
    // previous Activity's pending atomic save, not race it with a new worker.
    // Inkwash: at the lowest priority, so the recovery save after each stroke (a whole page compressed, seconds on
    // the Manta) takes only what the pen, the screen and the stroke's helpers leave of the cores.
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Inkwash files"); t.setDaemon(false); t.setPriority(Thread.MIN_PRIORITY); return t;
    });
    /**
     * What a job that ran out of heap reports: the app goes on (a save or a picture fails and is tried again; the
     * notebook in memory is untouched) instead of the IO thread's error stopping it.
     */
    private static Exception failure(Throwable error) { return error instanceof Exception ? (Exception) error : new IOException("Not enough memory", error); }
    private Snapshot pending;
    private Result<Void> pendingResult;
    private boolean scheduled;

    /** A store for the drawing library in {@code library}, autosaving to {@code recovery} (see {@link DrawingStorage}). */
    DocumentStore(File library, File recovery) {
        files = new DrawingFiles(library); this.recovery = recovery;
        IO.execute(this::upgradeLegacyNames);
    }
    /** When drawing {@code path} was last written, ms; 0 when it can't be told. */
    long modified(String path) { try { return files.modified(path); } catch (IOException e) { return 0; } }
    /** Runs on {@link #IO} before anything else reads the library; a failure leaves the old names readable by retrying next time. */
    private void upgradeLegacyNames() {
        try { files.upgradeLegacyNames(); }
        catch (IOException e) { android.util.Log.e(ProbeActivity.TAG, "Could not rename .tsm drawings", e); }
    }
    private File file(String name) throws IOException {
        return name.equals("_recovery") ? recovery : files.drawing(name);
    }
    static boolean validName(String name) {
        return DrawingFiles.validName(name);
    }
    /**
     * Queues {@code snapshot} for the recovery file; one still waiting is dropped for it (its {@code result} never called).
     * Inkwash: each snapshot is released once written or dropped, so the page's next write needn't copy its layers.
     */
    synchronized void recoverLater(Snapshot snapshot, Result<Void> result) {
        if (pending != null) pending.book.release();
        pending = snapshot; pendingResult = result;
        if (scheduled) return;
        scheduled = true;
        IO.execute(() -> {
            while (true) {
                Snapshot next; Result<Void> done;
                synchronized (DocumentStore.this) {
                    next = pending; done = pendingResult; pending = null; pendingResult = null;
                    if (next == null) { scheduled = false; return; }
                }
                Exception error = null;
                try { write("_recovery", next); }
                catch (Exception | OutOfMemoryError e) { error = failure(e); }
                next.book.release();
                done.complete(null, error);
            }
        });
    }
    void save(String name, Snapshot snapshot, Result<Void> result) {
        save(name, snapshot, true, result);
    }
    void save(String name, Snapshot snapshot, boolean replace, Result<Void> result) {
        IO.execute(() -> {
            try {
                File target = files.drawing(name);
                if (!replace && exists(target)) throw new IOException("A painting with that name already exists");
                write(name, snapshot); snapshot.book.release(); result.complete(null, null);
            }
            catch (Exception | OutOfMemoryError e) { snapshot.book.release(); result.complete(null, failure(e)); }
        });
    }
    private static boolean exists(File target) { return DrawingFiles.exists(target); }
    void exists(String name, Result<Boolean> result) {
        IO.execute(() -> {
            try { result.complete(exists(files.drawing(name)), null); }
            catch (Exception | OutOfMemoryError e) { result.complete(null, failure(e)); }
        });
    }
    void open(String name, Result<ToneDocument> result) {
        openBook(name,(book,error) -> result.complete(book==null?null:book.current(),error));
    }
    void openBook(String name, Result<DrawingBook> result) {
        if (name.equals("_recovery")) {
            recover((recovered, error) -> result.complete(recovered == null ? null : recovered.book, error));
            return;
        }
        IO.execute(() -> {
            try { result.complete(BookCodec.open(readable(file(name))), null); }
            catch (Exception | OutOfMemoryError e) { result.complete(null, failure(e)); }
        });
    }
    /** The first page of a saved drawing, for browsing; never loads the whole book. */
    void firstPage(String name, Result<ToneDocument> result) {
        IO.execute(() -> {
            try (FileInputStream input = new AtomicFile(files.drawing(name)).openRead()) {
                result.complete(BookCodec.readFirstPage(input), null);
            } catch (Exception | OutOfMemoryError e) { result.complete(null, failure(e)); }
        });
    }
    void recover(Result<RecoveryCodec.Recovered> result) {
        IO.execute(() -> {
            try {
                AtomicFile atomic = new AtomicFile(recovery);
                if (!exists(atomic.getBaseFile())) { result.complete(null, null); return; }
                RecoveryCodec.Recovered read;
                try (FileInputStream input = atomic.openRead()) { read = RecoveryCodec.read(input, this::drawingFile); }
                catch (RecoveryCodec.FileChanged changed) { read = fileChanged(atomic, changed.path); }
                result.complete(read, null);
            } catch (Exception | OutOfMemoryError e) { result.complete(null, failure(e)); }
        });
    }
    /**
     * The notebook's file changed under its recovery: the recovery is set aside (recovery + ".changed", the latest only)
     * and the notebook opens as last saved, its unsaved strokes lost; the shelf (null) if the file is gone.
     */
    private RecoveryCodec.Recovered fileChanged(AtomicFile atomic, String path) throws IOException {
        File aside = new File(recovery.getPath() + ".changed");
        if (aside.exists() && !aside.delete()) throw new IOException("Couldn't set the recovery aside");
        if (!atomic.getBaseFile().renameTo(aside)) throw new IOException("Couldn't set the recovery aside");
        File file = drawingFile(path);
        return file == null || !file.isFile() ? null : new RecoveryCodec.Recovered(BookCodec.open(readable(file)), path, false);
    }
    void list(String folder, Result<DrawingFiles.Entry[]> result) {
        IO.execute(() -> {
            try { result.complete(files.list(folder), null); }
            catch (Exception | OutOfMemoryError e) { result.complete(null, failure(e)); }
        });
    }
    /** Named drawings in the library, not counting the working drawing's recovery file. */
    void drawingCount(Result<Integer> result) {
        IO.execute(() -> {
            try { result.complete(files.drawingCount(), null); }
            catch (Exception | OutOfMemoryError e) { result.complete(null, failure(e)); }
        });
    }
    void createFolder(String folder, String name, Result<Void> result) {
        IO.execute(() -> {
            try { files.createFolder(folder, name); result.complete(null, null); }
            catch (Exception | OutOfMemoryError e) { result.complete(null, failure(e)); }
        });
    }
    /**
     * Gathers the library's old drawings into notebooks, once ({@link NotebookMigration}), before anything queued after
     * it reads the library; {@code working} is the working drawing with unsaved changes, or null. Reports progress on
     * the IO thread.
     */
    void migrate(NotebookMigration.Working working, NotebookMigration.Progress progress, Result<NotebookMigration.Result> result) {
        IO.execute(() -> {
            try { result.complete(new NotebookMigration(files.root()).run(working, progress), null); }
            catch (Exception | OutOfMemoryError e) { result.complete(null, failure(e)); }
        });
    }
    /** The library's folder, as it is when the queue reaches this. */
    void library(Result<File> result) { IO.execute(() -> result.complete(files.root(), null)); }
    /** Drops the recovery file: its drawing is in a notebook now. */
    void forgetRecovery(Result<Void> result) {
        IO.execute(() -> {
            try { new AtomicFile(recovery).delete(); result.complete(null, null); }
            catch (Exception | OutOfMemoryError e) { result.complete(null, failure(e)); }
        });
    }
    /** A notebook on the shelf: its path, when it last changed, its pages and cover as its file says ({@link BookPictures#readCover}). */
    static final class Notebook {
        /** {@code cover}: the cover picture's page (-1: none). */
        final String path; final long modified; final int pages, cover;
        /** The cover picture's page's picture, or null when the file has none for it; unreadable: the file didn't read. */
        final byte[] picture; final boolean unreadable;
        /** Inkwash: its pages' size (0: not known), whether something is drawn on its front cover and that cover's picture of its marks (null: none kept). */
        final int width, height;
        final boolean frontDrawn;
        final byte[] front;
        /** Its covers' design. */
        final CoverDesign design;
        /** Its other options (template, tools). */
        final NotebookOptions options;
        Notebook(String path, long modified, int pages, int cover, byte[] picture, boolean unreadable) { this(path, modified, pages, cover, picture, unreadable, 0, 0, false, null, CoverDesign.DEFAULT); }
        Notebook(String path, long modified, int pages, int cover, byte[] picture, boolean unreadable, int width, int height, boolean frontDrawn, byte[] front, CoverDesign design) {
            this(path, modified, pages, cover, picture, unreadable, width, height, frontDrawn, front, design, NotebookOptions.DEFAULT);
        }
        Notebook(String path, long modified, int pages, int cover, byte[] picture, boolean unreadable, int width, int height, boolean frontDrawn, byte[] front, CoverDesign design, NotebookOptions options) {
            this.options = options;
            this.path = path; this.modified = modified; this.pages = pages; this.cover = cover; this.picture = picture; this.unreadable = unreadable;
            this.width = width; this.height = height; this.frontDrawn = frontDrawn; this.front = front; this.design = design;
        }
    }
    /** Every notebook in the library, the last changed first, read from its file's end: no page is decoded. */
    void notebooks(Result<java.util.List<Notebook>> result) {
        IO.execute(() -> {
            try {
                java.util.List<Notebook> all = new java.util.ArrayList<>();
                for (DrawingFiles.Entry entry : files.notebooks()) {
                    File file = files.drawing(entry.name), backup = new File(file.getPath() + ".bak");
                    try {
                        BookPictures.Cover cover = BookPictures.cover(backup.isFile() ? backup : file);
                        all.add(new Notebook(entry.name, entry.modified, cover.stacks, cover.cover, cover.picture, false, cover.width, cover.height, cover.frontDrawn, cover.front, cover.design, cover.options));
                    } catch (IOException damaged) { all.add(new Notebook(entry.name, entry.modified, 0, 0, null, true)); }
                }
                result.complete(all, null);
            } catch (Exception | OutOfMemoryError e) { result.complete(null, failure(e)); }
        });
    }
    /** Page {@code page} of a saved notebook decoded, for its picture when its file has none; {@link DrawingBook#FRONT}: its front cover (without its paper; null if nothing is drawn on it). */
    void page(String path, int page, Result<ToneDocument> result) {
        IO.execute(() -> {
            try {
                BookCodec.Pages pages = BookCodec.openPages(readable(files.drawing(path)));
                PackedPage packed = page == DrawingBook.FRONT ? pages.covers.pages[0] : pages.pages.get(Math.min(page, pages.pages.size() - 1));
                // Its picture is made in the result, on this thread: the page's memory is let go after.
                synchronized (DocumentCodec.ONE_PAGE) { result.complete(packed == null ? null : DocumentCodec.read(new java.io.ByteArrayInputStream(packed.bytes())), null); }
            } catch (Exception | OutOfMemoryError e) { result.complete(null, failure(e)); }
        });
    }
    /**
     * Inkwash: a saved notebook's covers' design made {@code design}, after any queued saves: its file opened, the design
     * set and saved again, its pages copied from the old file as they are.
     */
    void plainTracing(String path, Result<Void> result) { rewrite(path, book -> book.setTracing(book.tracing().plainView()), result); }
    void design(String path, CoverDesign design, Result<Void> result) { rewrite(path, book -> book.setDesign(design), result); }
    /** A saved notebook opened after any queued saves, {@code change}d and saved again, its pages copied from the old file as they are. */
    private void rewrite(String path, java.util.function.Consumer<DrawingBook> change, Result<Void> result) {
        IO.execute(() -> {
            java.util.Set<PackedPage.Source> open = new java.util.HashSet<>();
            try {
                DrawingBook book = BookCodec.open(readable(file(path)));
                change.accept(book);
                Snapshot snapshot = new Snapshot(book, path);
                sources(snapshot.book.pages, open);
                write(path, snapshot);
                sources(snapshot.book.written, open);
                result.complete(null, null);
            } catch (Exception | OutOfMemoryError e) { result.complete(null, failure(e)); }
            finally { for (PackedPage.Source source : open) source.close(); }
        });
    }
    private static void sources(java.util.List<PackedPage> pages, java.util.Set<PackedPage.Source> into) {
        if (pages != null) for (PackedPage page : pages) if (page != null && page.file() != null) into.add(page.file());
    }
    /** What writes a whole notebook's file ({@link #create}). */
    interface Book { void write(java.io.OutputStream out) throws IOException; }
    /**
     * Inkwash: a new notebook in the library's top folder named {@code name} ("name 2" and on when that is taken), its
     * file written whole by {@code book} after any queued saves, or not at all (Import PDF, the owner, 2026-10-22: never a
     * half notebook); reports its path.
     */
    void create(String name, Book book, Result<String> result) {
        IO.execute(() -> {
            try {
                String path = files.unusedName("", name);
                write(new AtomicFile(files.drawing(path)), output -> {
                    java.io.BufferedOutputStream buffered = new java.io.BufferedOutputStream(output, 1 << 20);
                    book.write(buffered); buffered.flush();
                });
                result.complete(path, null);
            } catch (Exception | OutOfMemoryError e) { result.complete(null, failure(e)); }
        });
    }
    /** Renames a drawing after any queued saves; reports its new path. */
    void rename(String path, String name, Result<String> result) {
        IO.execute(() -> {
            try { result.complete(files.rename(path, name), null); }
            catch (Exception | OutOfMemoryError e) { result.complete(null, failure(e)); }
        });
    }
    /** Deletes a drawing after any queued saves. */
    void delete(String path, Result<Void> result) {
        IO.execute(() -> {
            try { files.delete(path); result.complete(null, null); }
            catch (Exception | OutOfMemoryError e) { result.complete(null, failure(e)); }
        });
    }
    /**
     * Moves the library's drawings into {@code library} and uses it from then on; queued saves
     * land wherever the library is when they run. The recovery file stays put. On failure the
     * store stays where it was, and moving again finishes the job.
     */
    void relocate(File library, Result<Void> result) {
        IO.execute(() -> {
            try {
                DrawingFiles target = new DrawingFiles(library);
                if (!target.sameLibrary(files)) { target.folder(""); move("", target); }
                files = target; upgradeLegacyNames(); adoptLibraryRecovery(); result.complete(null, null);
            } catch (Exception | OutOfMemoryError e) { result.complete(null, failure(e)); }
        });
    }
    /**
     * Before 0.97 the recovery file lived in the library, where cloud sync copied it as
     * "_recovery_CONFLICT_…" drawings and left "_recovery.mpaint.new" folders behind. Takes
     * that recovery over when there is no private one, keeps it as a named drawing when it
     * has unsaved changes of its own, and removes it and the sync's leftovers.
     */
    private void adoptLibraryRecovery() throws IOException {
        File old = files.recovery();
        if (old.getCanonicalPath().equals(recovery.getCanonicalPath())) return;
        if (exists(old)) {
            AtomicFile source = new AtomicFile(old);
            if (!exists(recovery)) {
                byte[] bytes = source.readFully();
                write(new AtomicFile(recovery), output -> output.write(bytes));
                if (!Arrays.equals(bytes, new AtomicFile(recovery).readFully()))
                    throw new IOException("The private copy of the recovery file did not match; the original was kept");
            } else keepUnsavedRecovery(files, source);
            source.delete();
        }
        File[] entries = files.folder("").listFiles();
        if (entries == null) return;
        for (File entry : entries) {
            String name = entry.getName();
            // A directory is removed only when empty; delete() refuses otherwise.
            if (name.equals(old.getName() + ".new") || name.startsWith("_recovery_CONFLICT_") && name.endsWith(DrawingFiles.EXTENSION))
                entry.delete();
        }
    }
    private static void keepUnsavedRecovery(DrawingFiles target, AtomicFile recovery) throws IOException {
        RecoveryCodec.Recovered recovered;
        try (FileInputStream input = recovery.openRead()) {
            recovered = RecoveryCodec.read(input, path -> { try { return target.drawing(path); } catch (IOException e) { return null; } });
        }
        if (!recovered.book.unsaved()) return;
        String folder = recovered.path.isEmpty() ? "" : DrawingFiles.parent(recovered.path);
        String name = recovered.path.isEmpty() ? "Recovered painting" : DrawingFiles.name(recovered.path) + " recovered";
        try { target.folder(folder); } catch (IOException missing) { folder = ""; }
        DrawingBook.Snapshot book = recovered.book.snapshot();
        write(new AtomicFile(target.drawing(target.unusedName(folder, name))), output -> BookCodec.write(output, book));
    }
    /** Moves one folder level; each source file goes only after its copy is complete. */
    private void move(String path, DrawingFiles target) throws IOException {
        File[] entries = files.folder(path).listFiles();
        if (entries == null) throw new IOException("Could not read painting folder");
        LinkedHashSet<String> drawings = new LinkedHashSet<>();
        for (File entry : entries) {
            String name = entry.getName();
            if (entry.isDirectory() && DrawingFiles.validName(name)) {
                if (!new File(target.folder(path), name).isDirectory()) target.createFolder(path, name);
                move(DrawingFiles.child(path, name), target);
                entry.delete();
            } else if (name.endsWith(DrawingFiles.EXTENSION) || name.endsWith(DrawingFiles.EXTENSION + ".bak")) {
                drawings.add(name.substring(0, name.lastIndexOf(DrawingFiles.EXTENSION)));
            }
        }
        for (String name : drawings) {
            if (!DrawingFiles.validName(name)) continue;
            AtomicFile source = new AtomicFile(files.drawing(DrawingFiles.child(path, name)));
            byte[] bytes = source.readFully();
            File destination = target.drawing(DrawingFiles.child(path, name));
            if (exists(destination)) {
                if (Arrays.equals(bytes, new AtomicFile(destination).readFully())) { source.delete(); continue; }
                destination = target.drawing(target.unusedName(path, name));
            }
            write(new AtomicFile(destination), output -> output.write(bytes));
            if (!Arrays.equals(bytes, new AtomicFile(destination).readFully()))
                throw new IOException("The moved copy of " + DrawingFiles.child(path, name) + " did not match; the original was kept");
            source.delete();
        }
    }
    private void write(String name, Snapshot snapshot) throws IOException {
        if (name.equals("_recovery")) { write(new AtomicFile(file(name)), output -> RecoveryCodec.write(output, snapshot.book, snapshot.path)); return; }
        if (snapshot.prepare != null) synchronized (DocumentCodec.ONE_PAGE) { snapshot.prepare.run(); }
        // The pages it didn't change stream from the old file into the new one. Once the new one is in place, every page
        // it wrote is read from it: the lock keeps any read off the old file from its rename until then.
        AtomicFile atomic = new AtomicFile(file(name));
        FileOutputStream output = null;
        try {
            output = atomic.startWrite();
            java.io.BufferedOutputStream buffered = new java.io.BufferedOutputStream(output, 1 << 20);
            BookCodec.write(buffered, snapshot.book);
            buffered.flush();
            FileOutputStream written = output;
            output = null;
            BookCodec.replaced(snapshot.book, atomic.getBaseFile(), () -> atomic.finishWrite(written));
        } catch (IOException | RuntimeException | OutOfMemoryError error) {
            if (output != null) atomic.failWrite(output);
            throw error;
        }
    }
    /** {@code file} ready to read as AtomicFile would: an interrupted save's backup, if any, put back in its place first. */
    private static File readable(File file) throws IOException {
        AtomicFile atomic = new AtomicFile(file);
        atomic.openRead().close();
        return atomic.getBaseFile();
    }
    /** A library path's file, or null if the path isn't valid. */
    private File drawingFile(String path) { try { return files.drawing(path); } catch (IOException e) { return null; } }
    private interface Contents { void writeTo(FileOutputStream output) throws IOException; }
    private static void write(AtomicFile atomic, Contents contents) throws IOException {
        FileOutputStream output = null;
        try {
            output = atomic.startWrite();
            contents.writeTo(output);
            atomic.finishWrite(output);
        } catch (IOException | RuntimeException | OutOfMemoryError error) {
            if (output != null) atomic.failWrite(output);
            throw error;
        }
    }
    static final class Snapshot {
        final DrawingBook.Snapshot book;
        final String path;
        /** Run on the IO thread before a named save writes it (Inkwash: the page being drawn on's picture). */
        Runnable prepare;
        Snapshot(ToneDocument document) {
            this(new DrawingBook(document));
        }
        Snapshot(DrawingBook book){this(book, "");}
        Snapshot(DrawingBook book, String path){this.book=book.snapshot();this.path=path;}
    }
}
