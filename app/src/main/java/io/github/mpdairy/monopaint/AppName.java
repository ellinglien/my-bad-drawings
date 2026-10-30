package io.github.mpdairy.monopaint;

/**
 * The app's name as people see it: THE one switch (docs/RENAMING.md). app/build.gradle reads {@link #NAME} from this
 * file for the launcher label ({@code app_name}) and the library folders (BuildConfig.LIBRARY_FOLDER: Document/,
 * EXPORT/, Pictures/), and every message and file name that says the name builds it from here. It lives in a plain
 * Java class rather than BuildConfig so the desktop checks, which compile without Android, see it too.
 *
 * <p>To rename: put the old name first in {@link #FORMER}, then change {@link #NAME}. On its first launch the renamed
 * app carries the owner's folders and files over from the former names ({@link AppRename}).
 *
 * <p>Never follows the name (invisible, and changing it would break installs or files already out there): the
 * applicationId {@code ing.ell.mybaddrawings} (a new id is a different app: no update, no preferences; it changed once,
 * 2026-10-30, with a handover, {@link HandOver}), the Java package,
 * the markers inside files (NotebookExport.KEY "Inkwash page", CoverTexture.NAME "Inkwash cover sheet",
 * NotebookMigration.HEADER, share-code formats, preference keys "inkwash_*"), and thread names.
 */
final class AppName {
    /** The visible name. app/build.gradle parses this exact line: keep it {@code static final String NAME = "...";}. */
    static final String NAME = "My Bad Drawings";
    /** The names the app had before, newest first: their folders and files are carried over to {@link #NAME}'s. */
    static final String[] FORMER = {"Scribulor", "Inkwash"};

    private AppName() { }
}
