package io.github.mpdairy.monopaint;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Folders and pictures on the tablet's shared storage, for {@link DrawingBrowser} to choose a
 * picture to import from. Needs the file access the painting library uses.
 */
final class PictureFiles implements DrawingBrowser.Source {
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final String[] EXTENSIONS = {".png", ".jpg", ".jpeg", ".webp"};
    private final File root = Environment.getExternalStorageDirectory();
    /** Previews are decoded no larger than this many pixels a side. */
    private final int previewSize;

    PictureFiles(int previewSize) { this.previewSize = previewSize; }

    static boolean isPicture(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (String extension : EXTENSIONS) if (lower.endsWith(extension)) return true;
        return false;
    }
    /** The file at {@code path}, relative to shared storage. */
    File file(String path) { return path.isEmpty() ? root : new File(root, path); }
    /** Whether {@code folder} still exists, to start the browser there. */
    boolean folderExists(String folder) { return file(folder).isDirectory(); }

    @Override public void list(String folder, DocumentStore.Result<DrawingFiles.Entry[]> result) {
        IO.execute(() -> {
            try {
                File[] found = file(folder).listFiles();
                if (found == null) throw new IOException("This folder can't be read");
                ArrayList<DrawingFiles.Entry> folders = new ArrayList<>(), pictures = new ArrayList<>();
                for (File each : found) {
                    String name = each.getName();
                    if (name.startsWith(".")) continue;
                    if (each.isDirectory() && !(folder.isEmpty() && name.equals("Android"))) folders.add(new DrawingFiles.Entry(name, true));
                    else if (each.isFile() && isPicture(name)) pictures.add(new DrawingFiles.Entry(name, false));
                }
                java.util.Comparator<DrawingFiles.Entry> byName = (a, b) -> a.name.compareToIgnoreCase(b.name);
                folders.sort(byName); pictures.sort(byName);
                folders.addAll(pictures);
                result.complete(folders.toArray(new DrawingFiles.Entry[0]), null);
            } catch (Exception e) { result.complete(null, e); }
        });
    }
    @Override public void preview(String path, DocumentStore.Result<Bitmap> result) {
        IO.execute(() -> {
            try {
                String file = file(path).getPath();
                BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(file, bounds);
                BitmapFactory.Options options = new BitmapFactory.Options(); options.inSampleSize = 1;
                while (bounds.outWidth / (options.inSampleSize * 2) >= previewSize || bounds.outHeight / (options.inSampleSize * 2) >= previewSize)
                    options.inSampleSize *= 2;
                result.complete(BitmapFactory.decodeFile(file, options), null);
            } catch (Exception | OutOfMemoryError e) { result.complete(null, e instanceof Exception ? (Exception) e : null); }
        });
    }
}
