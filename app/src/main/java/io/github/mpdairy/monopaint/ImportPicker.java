package io.github.mpdairy.monopaint;

import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Import's picker (the owner, 2026-10-26: "there's no way to cancel ... add a cancel to both imports"; one for pictures
 * and PDFs since 2026-10-29, "so it'd be just one import thing", and by folder, "it's also very hard to find anything ...
 * more like supernote file explorer"): the Supernote has no back button and Android's picker no Cancel, so the app shows
 * the files itself, in a dialog as wide as the screen with Cancel. Under the title the folder's crumbs: Storage (the
 * Supernote's own folders, {@link PictureImport#FOLDERS}), then each folder down to the one shown, a tap going there.
 * Then its folders and its pictures and PDFs ({@link PictureImport#entries}), as many rows a page as the screen holds,
 * ‹ and › between pages (nothing scrolls: e-ink), each row a picture's small thumbnail, a PDF's first page or a folder's
 * icon before its name and, for a file, its day. A tap on a folder opens it; on a file picks it ({@code picked} hears its
 * Uri) and closes the dialog. It opens where the last file was picked ({@link PaintPreferences#importFolder}). A PDF
 * picked then asks how ({@link #pdf}).
 */
final class ImportPicker {
    /** A row's height and the gap under it, dp; the room the title, crumbs, page bar and buttons take. */
    private static final int ROW = 76, GAP = 6, CHROME = 300;
    private final PaintActivity app;
    private final Consumer<Uri> picked;
    private final File root = Environment.getExternalStorageDirectory();
    private final int rowCount;
    private final TextView[] names, days;
    private final ImageView[] thumbnails;
    private final View[] rows;
    private final LinearLayout crumbs;
    private final Button previous, next;
    private final TextView count;
    private final AlertDialog dialog;
    private File folder;
    private List<File> files = Collections.emptyList();
    private int page;

    ImportPicker(PaintActivity app, Consumer<Uri> picked) {
        this.app = app; this.picked = picked;
        int high = app.root.getHeight() > 0 ? Ui.dpOf(app, app.root.getHeight()) : 800;
        rowCount = Math.max(4, Math.min(12, (high - CHROME) / (ROW + GAP)));
        names = new TextView[rowCount]; days = new TextView[rowCount]; thumbnails = new ImageView[rowCount]; rows = new View[rowCount];
        LinearLayout content = new LinearLayout(app); content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), 0, dp(20), 0);
        crumbs = new LinearLayout(app); crumbs.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(crumbs, new LinearLayout.LayoutParams(-1, dp(52)));
        for (int i = 0; i < rowCount; i++) content.addView(rows[i] = row(i), rowSize());
        previous = new Button(app); count = new TextView(app); next = new Button(app);
        content.addView(bar(app, previous, count, next, step -> show(page + step)));
        dialog = new AlertDialog.Builder(app).setTitle(PictureImport.IMPORT).setView(content).setNegativeButton(PictureImport.CANCEL, null).create();
        dialog.show();
        // As wide as the screen allows (the owner: "maybe bigger modal").
        app.compactDialog(dialog, 4000);
        open(PictureImport.start(root, app.prefs.importFolder()));
    }

    /**
     * A page bar: ‹, "2 / 5", ›, {@code step} hearing −1 or 1 (a hold on ‹ or ›, −10 or 10); always its room, so the
     * dialog keeps its size as pages turn.
     */
    private static LinearLayout bar(PaintActivity app, Button previous, TextView count, Button next, IntConsumer step) {
        LinearLayout bar = new LinearLayout(app); bar.setGravity(Gravity.CENTER_VERTICAL);
        previous.setText("‹"); previous.setContentDescription("Previous page");
        previous.setOnClickListener(v -> step.accept(-1));
        previous.setOnLongClickListener(v -> { step.accept(-10); return true; });
        count.setGravity(Gravity.CENTER);
        next.setText("›"); next.setContentDescription("Next page");
        next.setOnClickListener(v -> step.accept(1));
        next.setOnLongClickListener(v -> { step.accept(10); return true; });
        bar.addView(previous, new LinearLayout.LayoutParams(Ui.dp(app, 64), Ui.dp(app, 52)));
        bar.addView(count, new LinearLayout.LayoutParams(0, -2, 1));
        bar.addView(next, new LinearLayout.LayoutParams(Ui.dp(app, 64), Ui.dp(app, 52)));
        return bar;
    }
    /** {@code bar}'s buttons and count for page {@code at} (from 0) of {@code pages}: hidden when there is one. */
    private static void barAt(Button previous, TextView count, Button next, int at, int pages) {
        boolean several = pages > 1;
        previous.setVisibility(several ? View.VISIBLE : View.INVISIBLE); previous.setEnabled(at > 0);
        next.setVisibility(several ? View.VISIBLE : View.INVISIBLE); next.setEnabled(at < pages - 1);
        count.setText(several ? (at + 1) + " / " + pages : "");
    }

    private int dp(int size) { return Ui.dp(app, size); }
    private LinearLayout.LayoutParams rowSize() {
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-1, dp(ROW)); size.bottomMargin = dp(GAP); return size;
    }
    /** Row {@code i}: paper on the dialog's grey, a thumbnail or icon, then the name over a file's day. */
    private View row(int i) {
        LinearLayout row = new LinearLayout(app); row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(Theme.box(app, Theme.PAPER, 0, 0)); row.setPadding(dp(8), 0, dp(8), 0);
        ImageView thumbnail = thumbnails[i] = new ImageView(app); thumbnail.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(dp(60), dp(60)); size.setMarginEnd(dp(12));
        row.addView(thumbnail, size);
        LinearLayout words = new LinearLayout(app); words.setOrientation(LinearLayout.VERTICAL);
        names[i] = line(false); days[i] = line(true);
        words.addView(names[i]); words.addView(days[i]);
        row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        row.setOnClickListener(v -> {
            int at = page * rowCount + i;
            if (at >= files.size()) return;
            File file = files.get(at);
            if (file.isDirectory()) { open(file); return; }
            app.prefs.setImportFolder(PictureImport.path(root, folder));
            dialog.dismiss(); picked.accept(Uri.fromFile(file));
        });
        return row;
    }
    private TextView line(boolean day) {
        TextView line = new TextView(app); line.setSingleLine(true); line.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        if (day) line.setTag(SettingsForm.HINT); // secondary: regular, a size smaller (SettingsForm.style)
        return line;
    }
    private static boolean isPdf(File file) { return PictureImport.pdf(null, file.getName()); }

    /** Folder {@code at} from its first page, its crumbs above: Storage, then each folder down to it. */
    private void open(File at) {
        folder = at;
        files = PictureImport.entries(root, at);
        crumbs.removeAllViews();
        crumbs.addView(crumb(PictureImport.STORAGE, root, at.equals(root)));
        String path = PictureImport.path(root, at);
        File step = root;
        if (!path.isEmpty()) for (String name : path.split("/")) {
            step = new File(step, name);
            TextView slash = new TextView(app); slash.setText("/");
            crumbs.addView(slash);
            crumbs.addView(crumb(name, step, step.equals(at)));
        }
        SettingsForm.style(crumbs, app.prefs.largeSettingsText());
        show(0);
    }
    /** A crumb: {@code name}, a tap opening {@code to}; the folder shown's bold, the rest regular. */
    private TextView crumb(String name, File to, boolean here) {
        TextView crumb = new TextView(app); crumb.setText(name); crumb.setGravity(Gravity.CENTER_VERTICAL);
        crumb.setSingleLine(true); crumb.setEllipsize(TextUtils.TruncateAt.END);
        crumb.setPadding(dp(6), dp(12), dp(6), dp(12));
        if (!here) { crumb.setTag(SettingsForm.HINT); crumb.setOnClickListener(v -> open(to)); }
        return crumb;
    }

    /** Page {@code at} of the folder: its rows' words and icons at once, then its pictures' and PDFs' thumbnails together, once read. */
    private void show(int at) {
        int pages = Math.max(1, (files.size() + rowCount - 1) / rowCount);
        page = Math.max(0, Math.min(at, pages - 1));
        for (int i = 0; i < rowCount; i++) {
            int index = page * rowCount + i;
            File file = index < files.size() ? files.get(index) : null;
            boolean isFolder = file != null && file.isDirectory();
            names[i].setText(file == null ? "" : file.getName());
            days[i].setText(file == null || isFolder ? "" : LibraryCard.day(java.time.Instant.ofEpochMilli(file.lastModified()).atZone(java.time.ZoneId.systemDefault()).toLocalDate()));
            days[i].setTextColor(Theme.GREY_800);
            rows[i].setVisibility(file == null ? View.INVISIBLE : View.VISIBLE);
            if (file == null) thumbnails[i].setImageBitmap(null);
            else thumbnails[i].setImageResource(isFolder ? R.drawable.ic_ph_folder : isPdf(file) ? R.drawable.ic_ph_file_pdf : R.drawable.ic_ph_image);
        }
        if (files.isEmpty()) {
            // The note in the first row's place.
            rows[0].setVisibility(View.VISIBLE);
            names[0].setText(PictureImport.NOTHING_FOUND);
        }
        barAt(previous, count, next, page, pages);
        int shown = page, size = dp(60);
        File in = folder;
        List<File> these = files.subList(Math.min(files.size(), page * rowCount), Math.min(files.size(), page * rowCount + rowCount));
        if (these.isEmpty()) return;
        new Thread(() -> {
            Bitmap[] read = new Bitmap[these.size()];
            for (int i = 0; i < read.length; i++) read[i] = thumbnail(these.get(i), size);
            app.runOnUiThread(() -> {
                if (!dialog.isShowing() || page != shown || folder != in) return;
                for (int i = 0; i < read.length; i++) if (read[i] != null) thumbnails[i].setImageBitmap(read[i]);
            });
        }, "import thumbnails").start();
    }
    /** {@code file}'s thumbnail, no smaller than {@code size} px: a picture's, or a PDF's first page; null for a folder, or if it doesn't read. */
    private Bitmap thumbnail(File file, int size) {
        if (file.isDirectory()) return null;
        if (isPdf(file)) {
            try { return PdfImport.page(app, Uri.fromFile(file), 0, size, size).picture; }
            catch (Exception | OutOfMemoryError unreadable) { return null; }
        }
        BitmapFactory.Options options = new BitmapFactory.Options(); options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), options);
        if (options.outWidth <= 0 || options.outHeight <= 0) return null;
        options.inJustDecodeBounds = false; options.inSampleSize = 1;
        while (Math.max(options.outWidth, options.outHeight) / (options.inSampleSize * 2) >= size) options.inSampleSize *= 2;
        try { return BitmapFactory.decodeFile(file.getPath(), options); }
        catch (OutOfMemoryError | RuntimeException unreadable) { return null; }
    }

    /** A PDF page's preview's side, dp. */
    private static final int PREVIEW = 280;
    /**
     * A PDF picked (the owner, 2026-10-29: "allow user to select a specific page from a pdf if they dont want to import
     * the whole thing"): its page shown, ‹ and › between its pages under it ("3 / 12", a hold ten at a time; none for a
     * one-page PDF), and {@link PictureImport#NEW_NOTEBOOK} (the whole PDF, {@code notebook}), {@link
     * PictureImport#THIS_PAGE} ({@code chosen} hears the page shown, from 0) or Cancel. A PDF that doesn't read, or has
     * no pages, says so in a note.
     */
    static void pdf(PaintActivity app, Uri uri, Runnable notebook, IntConsumer chosen) {
        int side = Ui.dp(app, PREVIEW);
        new Thread(() -> {
            PdfImport.Page first;
            try { first = PdfImport.page(app, uri, 0, side, side); }
            catch (Exception | OutOfMemoryError error) {
                Log.w(ProbeActivity.TAG, "Import PDF failed", error);
                app.runOnUiThread(() -> app.message(PictureImport.NO_PAGES.equals(error.getMessage()) ? PictureImport.NO_PAGES : PictureImport.UNREADABLE));
                return;
            }
            app.runOnUiThread(() -> { if (!app.isFinishing()) choose(app, uri, first, side, notebook, chosen); });
        }, "import pdf page").start();
    }
    private static void choose(PaintActivity app, Uri uri, PdfImport.Page first, int side, Runnable notebook, IntConsumer chosen) {
        LinearLayout content = new LinearLayout(app); content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(app, 20), Ui.dp(app, 8), Ui.dp(app, 20), 0);
        ImageView preview = new ImageView(app); preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setImageBitmap(first.picture);
        content.addView(preview, new LinearLayout.LayoutParams(-1, side));
        Button previous = new Button(app), next = new Button(app);
        TextView count = new TextView(app);
        int[] at = {0};
        AlertDialog[] dialog = {null};
        content.addView(bar(app, previous, count, next, step -> {
            int to = Math.max(0, Math.min(first.count - 1, at[0] + step));
            if (to == at[0]) return;
            at[0] = to;
            barAt(previous, count, next, to, first.count);
            new Thread(() -> {
                PdfImport.Page read;
                try { read = PdfImport.page(app, uri, to, side, side); }
                catch (Exception | OutOfMemoryError error) { Log.w(ProbeActivity.TAG, "PDF preview failed", error); return; }
                app.runOnUiThread(() -> { if (dialog[0].isShowing() && at[0] == to) preview.setImageBitmap(read.picture); });
            }, "import preview").start();
        }));
        barAt(previous, count, next, 0, first.count);
        dialog[0] = app.showDialog(new AlertDialog.Builder(app).setTitle(PictureImport.IMPORT).setView(content)
                .setPositiveButton(PictureImport.THIS_PAGE, (d, w) -> chosen.accept(at[0]))
                .setNeutralButton(PictureImport.NEW_NOTEBOOK, (d, w) -> notebook.run())
                .setNegativeButton(PictureImport.CANCEL, null));
    }
}
