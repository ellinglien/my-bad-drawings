package io.github.mpdairy.monopaint;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Brings a picture file onto the page, for instance a reference to paint from. It arrives as a
 * floating selection, upright on the screen, to move, resize and turn before placing it.
 */
final class ImageImport {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private ImageImport() { }

    /**
     * Shows MonoPaint's own picture browser, starting in the folder last imported from; false
     * without file access, when only Android's file picker ({@link #picker}) can reach pictures.
     */
    static boolean browse(PaintActivity app) {
        if (!DrawingStorage.sharedAllowed(app)) return false;
        PictureFiles pictures = new PictureFiles(Ui.dp(app, 120));
        String folder = app.prefs.importFolder();
        if (!pictures.folderExists(folder)) folder = "";
        DrawingBrowser.pictures(app, pictures, folder, (path, replace) -> {
            app.prefs.setImportFolder(DrawingFiles.parent(path));
            ask(app, Uri.fromFile(pictures.file(path)));
        });
        return true;
    }
    /** The file picker's request: any picture Android can decode, such as PNG or JPEG. */
    static Intent picker() {
        return new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*");
    }

    /**
     * Asks where, how large and which way up to place the chosen picture, with a preview of it on
     * the page, then floats it over the page.
     */
    static void ask(PaintActivity app, Uri picture) {
        app.fullscreen.expandChosenCanvas();
        ToneDocument page = app.pad.document;
        int rotation = app.appRotation;
        boolean sideways = rotation % 2 == 1;
        Placement preview = new Placement(app, sideways ? page.height : page.width, sideways ? page.width : page.height);
        int pad = Ui.dp(app, 20);
        LinearLayout content = new LinearLayout(app); content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(pad, Ui.dp(app, 8), pad, 0);
        content.addView(preview, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout turns = new LinearLayout(app);
        turns.addView(turnButton(app, "Turn left", R.drawable.ic_turn_left, () -> preview.turn(3)), new LinearLayout.LayoutParams(0, Ui.dp(app, 52), 1));
        turns.addView(turnButton(app, "Turn right", R.drawable.ic_turn_right, () -> preview.turn(1)), new LinearLayout.LayoutParams(0, Ui.dp(app, 52), 1));
        content.addView(turns);
        RadioGroup target = new RadioGroup(app);
        RadioButton current = choice(target, "On the current layer"), newLayer = choice(target, "On a new layer");
        boolean room = page.layerCount() < ToneDocument.MAX_LAYERS;
        newLayer.setEnabled(room);
        target.check(room && app.prefs.importNewLayer() ? newLayer.getId() : current.getId());
        content.addView(target);
        if (!room) {
            TextView full = new TextView(app); full.setText("This page already has " + ToneDocument.MAX_LAYERS + " layers.");
            content.addView(full);
        }
        RadioGroup size = new RadioGroup(app);
        RadioButton fit = choice(size, "Fit the page"), actual = choice(size, "Actual size");
        size.check(fit.getId());
        size.setOnCheckedChangeListener((group, id) -> preview.fit(fit.isChecked()));
        content.addView(size);
        TextView hint = new TextView(app);
        hint.setText("You can move, resize and turn it next, then tap outside it to put it down.");
        content.addView(hint);
        android.widget.ScrollView scroll = new android.widget.ScrollView(app); scroll.addView(content);
        app.showDialog(new AlertDialog.Builder(app).setTitle("Import image").setView(scroll)
                .setPositiveButton("Import", (dialog, which) -> {
                    // A full page offers no choice, so it leaves the remembered one alone.
                    if (room) app.prefs.setImportNewLayer(newLayer.isChecked());
                    load(app, picture, newLayer.isChecked(), fit.isChecked(), preview.turns);
                })
                .setNegativeButton("Cancel", null));
        // The page as it is now, read from a snapshot, since the page itself stays on this thread.
        ToneDocument.Snapshot snapshot = page.layerSnapshot();
        WORKER.execute(() -> {
            Bitmap shown = null, artwork = null; int[] bounds = null;
            try {
                bounds = bounds(app, picture);
                shown = scaled(app, picture, bounds, Placement.SIDE);
            } catch (IOException | RuntimeException | OutOfMemoryError ignored) { }
            try {
                ToneDocument copy = new ToneDocument(snapshot.width, snapshot.height, snapshot.layers, snapshot.active);
                artwork = PageOverview.turned(PageOverview.thumbnail(copy), rotation);
            } catch (RuntimeException | OutOfMemoryError ignored) { }
            Bitmap picturePreview = shown, pagePreview = artwork; int[] pictureSize = bounds;
            app.runOnUiThread(() -> preview.show(picturePreview, pictureSize, pagePreview));
        });
    }
    private static RadioButton choice(RadioGroup group, String label) {
        RadioButton button = new RadioButton(group.getContext()); button.setText(label);
        button.setId(View.generateViewId()); group.addView(button);
        return button;
    }
    private static android.widget.Button turnButton(PaintActivity app, String label, int icon, Runnable turn) {
        android.widget.Button button = new android.widget.Button(app); button.setText(label);
        button.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0);
        button.setOnClickListener(v -> turn.run());
        return button;
    }

    /** The picture's size on the page, in its own pixels' orientation, after {@code turns} quarter turns. */
    static int[] placedSize(int width, int height, int across, int down, boolean fit, int turns) {
        boolean sideways = turns % 2 == 1;
        int wide = sideways ? height : width, tall = sideways ? width : height;
        double scale = Math.min((double)across / wide, (double)down / tall);
        if (!fit) scale = Math.min(1, scale);
        return new int[]{Math.max(1, (int)Math.round(width * scale)), Math.max(1, (int)Math.round(height * scale))};
    }

    /** The page as the screen shows it, with the picture placed on it as it will arrive. */
    private static final class Placement extends View {
        /** The picture's preview is decoded at most this many pixels a side. */
        static final int SIDE = 400;
        private final int across, down;
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG);
        private Bitmap picture, page;
        private int[] size;
        private boolean fit = true;
        int turns;
        Placement(PaintActivity app, int across, int down) {
            super(app); this.across = across; this.down = down;
            setContentDescription("Preview of the picture on the page");
        }
        void turn(int quarter) { turns = (turns + quarter) % 4; invalidate(); }
        void fit(boolean value) { fit = value; invalidate(); }
        void show(Bitmap picture, int[] size, Bitmap page) { this.picture = picture; this.size = size; this.page = page; invalidate(); }
        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            int width = MeasureSpec.getSize(widthSpec);
            setMeasuredDimension(width, Math.min(Ui.dp(getContext(), 240), Math.round(width * (float)down / across)));
        }
        @Override protected void onDraw(android.graphics.Canvas canvas) {
            float scale = Math.min((float)getWidth() / across, (float)getHeight() / down);
            android.graphics.RectF sheet = new android.graphics.RectF(0, 0, across * scale, down * scale);
            sheet.offset((getWidth() - sheet.width()) / 2, (getHeight() - sheet.height()) / 2);
            paint.setStyle(android.graphics.Paint.Style.FILL); paint.setColor(android.graphics.Color.WHITE);
            canvas.drawRect(sheet, paint);
            if (page != null) canvas.drawBitmap(page, null, sheet, paint);
            if (size != null) {
                int[] placed = placedSize(size[0], size[1], across, down, fit, turns);
                float w = placed[0] * scale, h = placed[1] * scale;
                android.graphics.RectF frame = new android.graphics.RectF(-w / 2, -h / 2, w / 2, h / 2);
                canvas.save();
                canvas.translate(sheet.centerX(), sheet.centerY()); canvas.rotate(90 * turns);
                if (picture != null) canvas.drawBitmap(picture, null, frame, paint);
                paint.setStyle(android.graphics.Paint.Style.STROKE); paint.setStrokeWidth(Ui.dp(getContext(), 2));
                paint.setColor(android.graphics.Color.WHITE); canvas.drawRect(frame, paint);
                paint.setStrokeWidth(Ui.dp(getContext(), 1)); paint.setColor(android.graphics.Color.BLACK); canvas.drawRect(frame, paint);
                canvas.restore();
            }
            paint.setStyle(android.graphics.Paint.Style.STROKE); paint.setStrokeWidth(Ui.dp(getContext(), 2));
            paint.setColor(android.graphics.Color.BLACK); canvas.drawRect(sheet, paint);
        }
    }

    private static void load(PaintActivity app, Uri picture, boolean newLayer, boolean fit, int turns) {
        if (app.busy()) return;
        ToneDocument page = app.pad.document;
        if (!newLayer && !page.layerVisible(page.activeLayer())) {
            app.message("This layer is hidden. Open Layers to show it or select another layer."); return;
        }
        int rotation = app.appRotation, width = page.width, height = page.height;
        WORKER.execute(() -> {
            try {
                boolean sideways = rotation % 2 == 1;
                int[] bounds = bounds(app, picture);
                int[] size = placedSize(bounds[0], bounds[1], sideways ? height : width, sideways ? width : height, fit, turns);
                Pixels pixels = pixels(scaled(app, picture, bounds, size[0], size[1]));
                app.runOnUiThread(() -> {
                    if (app.isDestroyed() || app.pad.document != page) return;
                    app.pad.floatSelection(FloatingSelection.imported(page, pixels.width, pixels.height, pixels.tones, pixels.alpha,
                            (rotation + turns) * Math.PI / 2, newLayer));
                });
            } catch (IOException | RuntimeException | OutOfMemoryError error) {
                android.util.Log.e(ProbeActivity.TAG, "Image import failed", error);
                String reason = error instanceof IOException ? error.getMessage() : "This picture couldn't be read.";
                app.runOnUiThread(() -> { if (!app.isDestroyed()) app.message(reason); });
            }
        });
    }

    private static final class Pixels {
        final int width, height; final byte[] tones, alpha;
        Pixels(int width, int height, byte[] tones, byte[] alpha) { this.width = width; this.height = height; this.tones = tones; this.alpha = alpha; }
    }
    /** The picture's width and height, without decoding it. */
    private static int[] bounds(PaintActivity app, Uri picture) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
        try (InputStream in = app.getContentResolver().openInputStream(picture)) {
            if (in == null) throw new IOException("This picture couldn't be opened.");
            BitmapFactory.decodeStream(in, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IOException("This file isn't a picture MonoPaint can read.");
        return new int[]{bounds.outWidth, bounds.outHeight};
    }
    /** The picture shrunk, keeping its proportions, to at most {@code side} pixels a side. */
    private static Bitmap scaled(PaintActivity app, Uri picture, int[] bounds, int side) throws IOException {
        double scale = Math.min(1, (double)side / Math.max(bounds[0], bounds[1]));
        return scaled(app, picture, bounds, Math.max(1, (int)Math.round(bounds[0] * scale)), Math.max(1, (int)Math.round(bounds[1] * scale)));
    }
    /** The picture decoded at {@code width} × {@code height}. */
    private static Bitmap scaled(PaintActivity app, Uri picture, int[] bounds, int width, int height) throws IOException {
        // Halve large pictures while decoding; smooth scaling then makes the last step.
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888; options.inSampleSize = 1;
        while (bounds[0] / (options.inSampleSize * 2) >= width && bounds[1] / (options.inSampleSize * 2) >= height)
            options.inSampleSize *= 2;
        Bitmap decoded;
        try (InputStream in = app.getContentResolver().openInputStream(picture)) {
            decoded = in == null ? null : BitmapFactory.decodeStream(in, null, options);
        }
        if (decoded == null) throw new IOException("This file isn't a picture MonoPaint can read.");
        if (decoded.getWidth() == width && decoded.getHeight() == height) return decoded;
        Bitmap sized = Bitmap.createScaledBitmap(decoded, width, height, true);
        decoded.recycle();
        return sized;
    }
    /** The picture's tones and coverage, as the tones that export as its grays. */
    private static Pixels pixels(Bitmap picture) {
        int width = picture.getWidth(), height = picture.getHeight();
        int[] argb = new int[width * height];
        picture.getPixels(argb, 0, width, 0, 0, width, height);
        picture.recycle();
        byte[] tones = new byte[argb.length], alpha = new byte[argb.length];
        for (int i = 0; i < argb.length; i++) {
            int pixel = argb[i], a = pixel >>> 24;
            int gray = (((pixel >> 16) & 255) * 299 + ((pixel >> 8) & 255) * 587 + (pixel & 255) * 114 + 500) / 1000;
            tones[i] = (byte)(a == 0 ? 255 : DotPattern.importGray(gray)); alpha[i] = (byte)a;
        }
        return new Pixels(width, height, tones, alpha);
    }
}
