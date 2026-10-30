package io.github.mpdairy.monopaint;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import java.io.File;

/**
 * Where the drawing library lives. Drawings belong in shared storage, where they outlive
 * uninstalling or reinstalling the app (including with a different signing key), and a
 * reinstalled app finds them again: Document/MonoPaint on a Supernote, whose Files app shows
 * its own Document folder rather than Android's Documents, and the standard
 * Documents/MonoPaint on other tablets. Until the app may write there, drawings stay in its
 * private files.
 */
final class DrawingStorage {
    static final String SHARED_NAME = (Device.supernote() ? "Document" : Environment.DIRECTORY_DOCUMENTS) + "/" + BuildConfig.LIBRARY_FOLDER;

    static File shared() { return new File(Environment.getExternalStorageDirectory(), SHARED_NAME); }
    static File appPrivate(Context context) { return new File(context.getFilesDir(), "drawings"); }
    /** The working drawing's autosave, always private; see {@link DocumentStore}. */
    static File recovery(Context context) { return new File(appPrivate(context), "_recovery" + DrawingFiles.EXTENSION); }

    static boolean sharedAllowed(Context context) {
        boolean allowed = allowed(context);
        if (allowed) carryOver(context);
        return allowed;
    }
    private static boolean allowed(Context context) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ? Environment.isExternalStorageManager()
                : context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    /** The move from the old application id ({@link HandOver}): its folder beside the notebooks. */
    private static File handOver() { return new File(shared(), HandOver.FOLDER); }
    /** Whether this is the old id's build, which leaves the handover rather than taking it. */
    private static boolean handsOver() { return HandOver.OLD_ID.equals(BuildConfig.APPLICATION_ID); }
    /** The preference set once the new app has looked for a handover (and taken it, if there was one). */
    private static final String HANDOVER_CHECKED = "handover_checked";
    /** The old id's build: leaves its preferences and private drawings for the new app, off the main thread. */
    static void leaveHandOver(Context context) {
        if (!handsOver() || !allowed(context)) return;
        java.util.Map<String, ?> prefs = context.getSharedPreferences(PaintPreferences.FILE, Context.MODE_PRIVATE).getAll();
        File drawings = appPrivate(context);
        new Thread(() -> HandOver.leave(handOver(), prefs, drawings), "handover").start();
    }
    /** The new app: whether a handover waits that {@link #takeHandOver} would take now. */
    static boolean handOverWaiting(Context context, android.content.SharedPreferences prefs) {
        return !handsOver() && !prefs.getBoolean(HANDOVER_CHECKED, false) && allowed(context) && HandOver.waiting(handOver());
    }
    /**
     * The new app, before anything reads {@code prefs}: the first time it may read shared storage, takes the old app's
     * preferences in place of its own (a fresh install's) and its private drawings; true if it did. Looks only once.
     */
    static boolean takeHandOver(Context context, android.content.SharedPreferences prefs) {
        if (handsOver() || prefs.getBoolean(HANDOVER_CHECKED, false) || !allowed(context)) return false;
        java.util.Map<String, Object> taken = HandOver.take(handOver(), appPrivate(context));
        android.content.SharedPreferences.Editor edit = prefs.edit();
        if (taken != null) {
            edit.clear();
            for (java.util.Map.Entry<String, Object> e : taken.entrySet()) {
                Object v = e.getValue();
                if (v instanceof String) edit.putString(e.getKey(), (String) v);
                else if (v instanceof Boolean) edit.putBoolean(e.getKey(), (Boolean) v);
                else if (v instanceof Integer) edit.putInt(e.getKey(), (Integer) v);
                else if (v instanceof Long) edit.putLong(e.getKey(), (Long) v);
                else if (v instanceof Float) edit.putFloat(e.getKey(), (Float) v);
            }
        }
        boolean saved = edit.putBoolean(HANDOVER_CHECKED, true).commit();
        return taken != null && saved && HandOver.taken(handOver());
    }

    /** The preference that holds the name whose carry-over finished ({@link #carryOver}). */
    private static final String CARRIED_OVER = "carried_over_to";
    private static volatile boolean carried;

    /**
     * After a rename ({@link AppName#FORMER}), once shared storage may be written: moves the former name's Document,
     * EXPORT and Pictures folders into the current name's and renames the app's own files ({@link AppRename}), until it
     * all moved; then never again for this name (kept in the preferences). Nothing while the app has had no other name.
     * EXPORT is cloud-synced on a Supernote: the sync sees the moved PNGs as new files under the new folder (and may
     * keep or drop its copies of the old folder); nothing on the tablet is lost either way.
     */
    static void carryOver(Context context) {
        if (carried || AppName.FORMER.length == 0) return;
        android.content.SharedPreferences prefs = context.getSharedPreferences(PaintPreferences.FILE, Context.MODE_PRIVATE);
        if (AppName.NAME.equals(prefs.getString(CARRIED_OVER, null))) { carried = true; return; }
        synchronized (DrawingStorage.class) {
            if (carried) return;
            carried = true; // once a launch, done or not: what didn't move is tried again at the next
            File root = Environment.getExternalStorageDirectory();
            File pictures = new File(root, Environment.DIRECTORY_PICTURES), current = new File(pictures, BuildConfig.LIBRARY_FOLDER);
            java.util.List<String> scan = new java.util.ArrayList<>();
            for (String former : AppName.FORMER) {
                String folder = AppRename.formerFolder(BuildConfig.LIBRARY_FOLDER, AppName.NAME, former);
                File[] old = folder == null ? null : new File(pictures, folder).listFiles();
                if (old != null) for (File f : old) scan.add(f.getPath());
            }
            File[] parents = {shared().getParentFile(), new File(root, "EXPORT"), pictures};
            boolean done = AppRename.carryOver(parents, shared(), BuildConfig.LIBRARY_FOLDER, AppName.NAME, AppName.FORMER);
            // Pictures goes through MediaStore elsewhere: tell it what left the old folder and what came to the new.
            File[] now = current.listFiles();
            if (now != null) for (File f : now) scan.add(f.getPath());
            if (!scan.isEmpty()) android.media.MediaScannerConnection.scanFile(context.getApplicationContext(), scan.toArray(new String[0]), null, null);
            if (done) prefs.edit().putString(CARRIED_OVER, AppName.NAME).apply();
        }
    }

    /** The library in use now. */
    static File library(Context context) { return sharedAllowed(context) ? shared() : appPrivate(context); }

    /**
     * Asks Android for shared storage: the "All files access" switch on Android 11 and later,
     * otherwise the storage permission. The answer comes back to the activity's
     * {@code onActivityResult} or {@code onRequestPermissionsResult} with {@code request};
     * returns false if this device cannot ask.
     */
    static boolean request(Activity activity, int request) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            activity.requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, request);
            return true;
        }
        Intent app = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:" + activity.getPackageName()));
        for (Intent intent : new Intent[]{app, new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)}) {
            try { activity.startActivityForResult(intent, request); return true; }
            catch (ActivityNotFoundException ignored) { }
        }
        return false;
    }

    private DrawingStorage() {}
}
