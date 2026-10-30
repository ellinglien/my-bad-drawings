# Full-clean (GC16-style) refresh on the Manta: research, 2026-10-20

Status: researched from sources only. **Nothing here was run on a device.**

## What the app does today
- Display goes through `/dev/ebc` (`app/src/main/cpp/direct_eink.c`): region update ioctl `0x48545701`, layout query `0x48545201`. Modes accepted by the JNI: 7 (gray, flags 0/1), 9 (binary pen plane, flags 1), 5 (flags 0, Inkwash strokes). `docs/DIRECT_GRAY.md`.
- `DrawingPad.autoRefresh` = forced mode 7 over the whole page (`DirectEink.refresh`), plus a bar invalidate. This is also what the Refresh button runs. It re-sends every pixel but with the *partial* gray waveform, so it does not flash.
- Tried and rejected: mode 4 (framework `EINK_SHOW_MODE_GL` = 3, minus 3 in `postEinkHostBmpRectFast` selects driver mode 4): drew almost nothing on the Manta (DIRECT_GRAY 0.10). The docs rule out guessing waveform numbers.
- `NativePen` reflects into the Ratta pen controller (`setPWEnabled`, `addWritableRects`, ...) and calls hidden `View.setEinkUpdateMode(3, 7)` / `resetEinkUpdateMode()` on the canvas view. Neither exposes a full-clean mode.

## Candidates
1. **`EinkManager.sendOneFullFrame()` via the "eink" system service** (wired, guarded: `DirectEink.fullFrame`).
   KOReader's launcher (`RK35xxEPDController.kt`, android-luajit-launcher) does `getSystemService("eink")` then `android.os.EinkManager.sendOneFullFrame()` for full refresh on RK3566 devices, and says its mode must be "EPD_FULL". https://github.com/koreader/android-luajit-launcher/blob/master/app/src/main/java/org/koreader/launcher/device/epd/rockchip/RK35xxEPDController.kt
   Unknown: whether Ratta's Chauvet ships that service (the Manta is an RK3566 but with Ratta's own `libeinkutils`). If not, `getSystemService` returns null and nothing happens. If it exists it may flash from the framework's buffer, not the app's `/dev/ebc` plane, so the button always follows it with the mode 7 redraw to put the right pixels back.
2. **Generic Rockchip mode numbers** (`drivers/gpu/drm/rockchip/ebc-dev/ebc_dev.h` in https://github.com/Supernote-Ratta/kernel_Nomad_Manta): `EPD_FULL_GC16 = 2`, `EPD_PART_GC16 = 7`, `EPD_RESET = 17`, `EPD_FORCE_FULL = 21`, ioctls `0x7000..0x700b` (`EBC_SET_FULL_MODE_NUM = 0x7003`). Note "PART_GC16 = 7" matches the app's working mode 7, a hint that the HT driver's mode field may use this enum. But the Manta's own driver is the closed `ht_eink/ht_ebc.px` (HT ioctl family `0x4854xx`, not these), and DIRECT_GRAY already shows framework modes differ from driver numbers (GL 3 -> 4). **Not wired**: sending mode 2 (or 17/21) is a guessed waveform. Best next experiment if candidate 1 fails.
3. **sysfs/debug**: only `/sys/module/htfy_dbg/parameters/ht_ebc_dbg_bits` (debug log bits, no refresh control). No refresh sysfs node found.
4. **The system right-edge swipe**: its mechanism (framework call or broadcast) is not documented anywhere found. Watching it on device (below) is the quickest way to learn it.
5. Web searches for Supernote/Ratta EPD APIs found nothing public; KOReader has no Supernote-specific refresh code that I could find. `libeinkutils.so` (installed in the system, SHA in DIRECT_GRAY.md) is the real source: its exported `postEink*` functions and framework `EinkManager`/`View` hidden methods are the place to look.

## How to test on the Manta
1. Install, open a drawing, scribble, tap Refresh (page bar, column end). Expect: page redraws (mode 7), no crash.
2. `adb logcat -s <ProbeActivity.TAG>`: "No system full frame: ..." means candidate 1 is absent (the exception says which part: null service vs missing class). No such line plus a visible flash means it worked.
3. To find the swipe's mechanism: `adb shell dumpsys activity broadcasts | grep -i eink`, `adb logcat | grep -i -E "eink|ebc|refresh"` while doing the right-edge swipe; `adb shell service list | grep -i eink`; `adb shell dumpsys eink`.
4. To try candidate 2 safely: a one-off debug build sending a single `/dev/ebc` update with mode 2, flags 0, small region, on a scratch page; watch for flash and for the "UIConfict" kernel log (`adb shell dmesg | grep -i ebc`).

## Outcome (2026-10-20)
The owner tried the button on the Manta: it did nothing visible, so it was removed (revert of dc9985f). The system
swipe refresh is fine. Kept for whoever looks again.
