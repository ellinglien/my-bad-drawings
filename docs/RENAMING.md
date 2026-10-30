# Renaming the app

The visible name has one switch: `NAME` in
`app/src/main/java/io/github/mpdairy/monopaint/AppName.java`. Gradle reads that line for the
launcher label (`app_name`) and the library folders (`BuildConfig.LIBRARY_FOLDER`); every message
and file name in the code builds on it.

## Steps

1. In `AppName.java`: put the old name first in `FORMER` (`{"Inkwash"}` when Inkwash became Scribulor,
   2026-10-24), then change `NAME`. Keep `NAME` on one line as it is (Gradle parses it; `AppRenameChecks` guards that).
2. The loading screen is the owner's drawing of the name (`drawable-nodpi/splash_drawing.png`, since the rename to
   My Bad Drawings, 2026-10-29): ask for a new drawing. (Until then a vector of the name was made with
   `scripts/java/SplashName.java`; `AppRenameChecks` checks it only while `drawable/splash_name.xml` exists.)
3. Run `scripts/test_inkwash.sh`, build, and check the label:
   `aapt dump badging app/build/outputs/apk/release/app-release.apk | grep application-label`.
4. Outside the app:
   - README.md (title, icon alt text), CHANGELOG.md, any store/release notes.
   - GitHub repo name (`ellinglien/inkwash`; GitHub redirects the old URL) and the local remote. Still `inkwash`
     after the Scribulor rename: the owner's call.
   - docs/BRANDING.md (icon), docs/TEXT-STYLE.md (its examples say "EXPORT/Scribulor", "Scribulor's text").
   - Web tools' titles and text: docs/wild-slots/roll-tester.html ("Scribulor Roll Tester"), docs/brush-lab
     (web-lab, studio, studio-src, match-lab, dial-demo) where they say Scribulor.
   - THIRD_PARTY_NOTICES.md / app/src/main/assets/THIRD_PARTY_NOTICES.txt, if a heading names the app.
   - app/src/main/assets/words/default.txt (a comment names Document/Scribulor).
   - Wording in CLAUDE.md and docs (`grep -rn Scribulor --exclude-dir=build .`). Device scripts
     use the app id (`ing.ell.mybaddrawings`), which stays.

## What the renamed app does by itself

On its first launch with shared storage allowed (`DrawingStorage.carryOver`, `AppRename`):

- Any other folder named for the app comes along by itself as long as it is named from `BuildConfig.LIBRARY_FOLDER`
  or `AppName.NAME` (e.g. a Documents/<name>/ folder for PDFs: its new name follows; what is in the old one stays
  unless it is added to `DrawingStorage.carryOver`'s parents).
- Moves Document/Old, EXPORT/Old and Pictures/Old into Document/New, EXPORT/New and Pictures/New
  (and the bench/checks variants' "Old bench" folders likewise). A whole folder is renamed when
  the new one isn't there; otherwise entries merge one by one.
- Never overwrites: a clash keeps both, the incoming file as "name (2).ext".
- Renames "Old brushes.txt", "Old prompts.txt", "Old import.txt", "Old import (added).txt" and the
  "Old stamps" folder to the new name, unless a new one is already there (then the old one stays,
  untouched, beside it).
- Still imports "*.old.txt" and "Old import.txt" brush files.
- Only renames, never copies and deletes; removes a folder only once empty. What fails to move stays
  where it was and is tried again next launch; once everything moved it is recorded in the
  preferences and never runs again for that name.
- Supernote cloud sync: EXPORT is synced, so the moved PNGs appear as new files under EXPORT/New;
  the cloud may keep or drop its copies of EXPORT/Old. Nothing on the tablet is lost. Tell the owner
  before shipping a rename.
- Pictures (other tablets) is rescanned so MediaStore forgets the old paths.

## Never change

- applicationId `ing.ell.mybaddrawings`: another id is another app (no update over the old one, no
  preferences, a second icon).
  It changed once, on 2026-10-30, from `app.helpyy.inkwash` (named for the owner's private project) for the
  public release. The old app can't pass its private files to a new id, so one last build under the old id and key
  (`./gradlew -PlegacyId :app:assembleRelease`) leaves them in `Document/My Bad Drawings/.handover/`, and the new app
  takes them on its first launch with shared storage (`HandOver`, `DrawingStorage.takeHandOver`). Don't do it again
  without the same handover.
- The Java package `io.github.mpdairy.monopaint`.
- Markers inside files: NotebookExport.KEY "Inkwash page", CoverTexture.NAME "Inkwash cover sheet",
  NotebookMigration.HEADER, share-code formats, preference keys "inkwash_*". Files already out there
  hold them.
- Thread names (invisible; fine either way).
