# Building My Bad Drawings

## What you need

- JDK 21 (Android Studio's bundled one, `jbr`, works), the Android SDK with platform 34, NDK 27.0.12077973 and
  CMake 3.22.1. The Gradle wrapper is in the repo.
- `JAVA_HOME` and `ANDROID_HOME` set (or the SDK's path in a `local.properties`: `sdk.dir=/path/to/sdk`).
  `adb` is in `$ANDROID_HOME/platform-tools`.
- Node.js, for the desktop checks and Brush Studio.

The app is built for arm64 only (`abiFilters 'arm64-v8a'`): the Supernote Manta and Nomad.

## Builds

```sh
./gradlew :app:assembleRelease      # app/build/outputs/apk/release/app-release.apk: what goes on the tablet
./gradlew :app:assembleDebug        # debuggable, several times slower at drawing
```

After installing, `adb shell cmd package compile -m speed -f ing.ell.mybaddrawings` compiles the app ahead of time;
uncompiled, drawing is noticeably slower on the tablet.

### Signing

Without anything set up, every build is signed with Android's debug key (`~/.android/debug.keystore`), so a fresh
clone builds. To sign with your own release key, put a `keystore.properties` at the repo root (it is gitignored;
never commit it):

```properties
storeFile=/Users/you/.android/my-release.keystore
keyAlias=myalias
storePassword=...
keyPassword=...
```

Android only installs an update signed with the same key as the installed app: keep the keystore backed up, and if
you switch keys, uninstall first (notebooks stay in `Document/My Bad Drawings`; brushes and settings are kept in the
app and are lost with it).

### The application id and the handover build

The app id is `ing.ell.mybaddrawings` since 2026-10-30; before that it was `app.helpyy.inkwash`. To bring an old
install's brushes and settings across, `./gradlew -PlegacyId :app:assembleRelease` builds the old id once more,
signed with the old key (`legacy.storeFile`, `legacy.keyAlias`, `legacy.storePassword`, `legacy.keyPassword` in
`keystore.properties`). Installed over the old app and opened once, it leaves its private state in
`Document/My Bad Drawings/.handover`; the new app takes it on its first launch with file access (`HandOver.java`,
`docs/RENAMING.md`). Nobody else needs this.

## Checks

```sh
bash scripts/test_inkwash.sh     # the desktop checks: the brush engine, notebooks, files, layouts (pure Java)
bash scripts/test_document.sh    # also runs the above, plus MonoPaint's document checks
```

Device checks (androidTest) run in a separate install, `ing.ell.mybaddrawings.checks`, with its own library folder
(`Document/My Bad Drawings checks`), never touching your notebooks:

```sh
./gradlew -PisolatedChecks :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e toolbarOnly true ing.ell.mybaddrawings.checks.test/io.github.mpdairy.monopaint.WidthInstrumentation
```

The suites (`-e <name>Only true`) are listed in `WidthInstrumentation`. Many are MonoPaint's and test controls My
Bad Drawings hides; the ones that matter here are named in each change's entry in `docs/INTEGRATION.md`.
`scripts/bench_device.sh` (timings) and `scripts/screens_device.sh` (screenshots) build their own `.bench` install,
run on the first tablet `adb devices` lists (or `SERIAL=...`), and uninstall it afterwards.

## Brush Studio

The web companion is built from `docs/brush-lab/studio-src`:

```sh
node docs/brush-lab/studio-src/build.cjs     # writes docs/brush-lab/studio.html (not tracked); open it in a browser
```

The published copy is at https://ell.ing/brush-studio/.

## PNG conversion

For a `.mpaint` notebook copied to your computer, `bash scripts/export_png.sh drawing.mpaint 2 output.png` exports
page two (add `raw` or `dots` for logical tones or the tablet's dot pattern).

## Names that stay

- The Java package `io.github.mpdairy.monopaint`, the native library `monopaint_display` and the `.mpaint` file
  extension are MonoPaint's and stay, so the fork can keep taking MonoPaint's fixes and its files stay readable.
- The visible name has one switch, `AppName.NAME`; see `docs/RENAMING.md`.
