# Stability check, 2026-10-22 (overnight)

The Manta gives Inkwash 192 MB of Java memory ("heap"). Bitmaps live outside it. Everything below was measured with
the desktop harnesses capped at 192 MB and with the bench app on the Manta. The owner's app and its files were never
touched. Branch `stability` holds the fixes. Branch `stability-wet-budget` holds one option (§3.1). Neither is merged.

## 1. What overloads, worst first

| # | What | How bad | Status |
|---|------|---------|--------|
| 1 | **Wet paint never lets go of memory until it dries.** Every 16×16 px tile a Wet stroke touches keeps ~12 KB until the droplet tap or a page turn. | On the Manta, each Medium Soft wash stroke kept **+10.5 MB**. The engine alone reached **188 MB of 201 after 15 strokes**. The real drawing screen already uses 60–150 MB, so it runs out after roughly 8–12 wet strokes. | Option, §3.1 |
| 2 | **One Large wash across most of the page** | Soft wash, Large, scribbled over ~60% of a page: the heap touched **201 MB (the limit)** on the Manta, engine alone, and the stroke took 99 s. In the app it would run out. | **Done** on `wet-spread` (§3.2) |
| 3 | **A notebook's copies (AutoExport) kept every page's PNG in memory to build the PDF** | A 124-page text PDF notebook needed 96 MB just for that. A scanned one (the 200 MB import limit) **ran out at 192 MB**. An out-of-memory error there stopped the whole app. | **Fixed** |
| 4 | **Fill: big short-lived memory** | One fill peaked at **+110 MB above what was kept** on the Manta (176 MB in all) and took 4–19 s. On the desktop, fills were where 2 of 4 long sessions ran out. | Option, §3.3 |
| 5 | **Background jobs crash the app when memory is short.** Page pictures, saves, recovery, opening, exports and covers made ahead all ran out of memory as an *error* the jobs didn't catch, so Android closed the app. | These are the PagePicture.decode / DocumentCodec.readLayers crashes. The job that fails is just whichever one asks when memory is already full. | **Fixed** (they fail alone now) |
| 6 | **The drawing screen kept an 18 MB scratch array forever** after drawing mirrors, timed sections or the eraser preview over the whole page | This is the drawShow/renderAll crash (17 MB allocation). | **Fixed** (1 MB) |
| 7 | **Copies queued on every leave**: each queued export held a full snapshot and pinned the page's layers, so the next stroke copied them | 9–37 MB per queued export, plus a wasted export run | **Fixed** (one per notebook, the latest) |
| 8 | **Notebook screen: flicking pages queued a full-page decode for every page passed** and its neighbours | Sequential, so slow rather than a crash: 1–2 s each on the Manta | **Fixed** (pages passed are skipped) |
| 9 | **Memory that sits there while drawing** (desktop breakdown): the page itself (2 layers, plus shares, print, kinds and masking fluid: **37–70 MB**), Undo (**32 MB**), cover sheets kept from the shelf (**up to 24 + 16 MB**), stick grain (6 MB) | It leaves little room for #1, #2 and #4 | **Done** on `standing-memory` (§3.4) |

The live drawing screen on the Manta (bench app, three Large strokes on a blank page) used 63–147 MB of Java heap.
That leaves under 50 MB for everything in the table above.

## 2. What was fixed (byte-for-byte output; all 120 desktop checks pass; release build OK)

- `628eaf7` **Copies.** The PDF now reads each PNG from its folder as it writes that page. The 124-page text notebook's
  export needs 48 MB instead of 96. The scanned 200 MB one now finishes in 64 MB, where it used to fail at 192.
  Only one export is queued per notebook, and it always uses the latest snapshot. The snapshot is let go once
  packed. If memory runs out, only the copies fail (the next time tries again). Saving a page no longer makes a
  4.6 MB array of zeros. The PDF is identical byte for byte (`StabilityChecks`).
- `7e7a2d5` **Drawing screen.** Mirrors, timed sections and the eraser preview are drawn over the page in 256K-pixel
  bands, not the whole page at once. Each pixel only reads its own place, so the result is the same.
- `65ed446` **Background jobs.** If memory runs out in a page picture, cover, preview, PNG export, blank page or
  cover pattern, that one job fails and the app carries on. A save, recovery or open says "Not enough memory". The
  notebook in memory is untouched and saving can be tried again. A big page that was turned past before its job ran
  is skipped.
- `0f6d6da`, `cc40355` **StabilityBench** (`scripts/bench_device.sh -e stability wash|wet|session`) runs the same heap
  tests on the desktop and the Manta.

## 3. Left open: options (each changes behaviour, so it's your call)

### 3.1 Wet paint memory (#1): recommended
- **A. Dry when wet paint gets big (branch `stability-wet-budget`, ready).** Before a stroke, if the wet tiles take
  more than a quarter of the heap (48 MB, about 4–5 Medium wash strokes' worth), it dries first, exactly as if you'd
  tapped the droplet. On the Manta it stayed at 40–75 MB over 15 strokes, where it used to climb to 188.
  Downside: after about 4 big wet strokes, the older ones stop moving.
- B. Free only the tiles that have stopped moving and keep the rest wet. Nicer, but it needs a careful change
  inside MonoPaint's watercolour engine (`WetWatercolor`).
- C. Make each tile smaller (e.g. 16-bit instead of 32-bit numbers, and dropping per-frame scratch once a tile is
  still). This would also change how strokes look slightly.

### 3.2 One huge wash (#2)
A's check runs between strokes, so one enormous stroke can still run out. Option: past the budget, the stroke stops
wetting new tiles (paint lands but doesn't flow). It's the same engine change as B.

Done on branch `wet-spread` (2026-10-23), a little differently: past 64 MB of its own new tiles (about 5,000, a third
of a Manta page) a stroke dries the tiles it painted longest ago, down to 7/8 of that, so its water stays wet along its
last stretch only. And when all the wet paint passes `WET_BYTES` during a stroke, the older strokes' tiles dry first,
least recently painted first (`WetWatercolor.roomFor`). Smaller strokes are untouched (golden pictures unchanged).
Desktop, Large Soft wash over the page: wet tiles 185 → 65 MB, most heap 316 → 209 MB (`-Xmx512m`) and 200 → 176 MB
(`-Xmx192m`), 4.4–7.6 s → 2.1–2.2 s. Wet gouache: 174 → 59 MB.
In `wet` (15 Medium Soft wash strokes), the older strokes now dry a few tiles at a time once the share is reached, so
the heap kept stays near it (about 75 MB at `-Xmx192m`, 135 at `-Xmx512m`) instead of climbing and drying all at once.
Branch `wet-blend` (2026-10-23) drops A's dry-it-all before a stroke: past `WET_BYTES` the stroke's first mask dries the
tiles painted longest ago (`roomFor`, the same ceiling), so the stroke painted just before stays wet to blend with.

### 3.3 Fill (#4)
The fill finder holds 4 page-sized arrays (~32 MB), and the fill stroke settles the whole region at once. Options:
settle the fill in bands of rows, as the stroke code already does elsewhere, or limit a single fill to part of the
page. Fills are also slow on the Manta (4–19 s).

Speed, measured on the Manta (2026-10-23, `scripts/bench_device.sh -e stability fill -e presets "Bleed marker,Fountain
pen,Soft pastel"`, warm, ms). Finding the area was not most of it: a wet marker's settle is.

| | find, before → after | of which refine | lay (Bleed marker / Fountain pen / Soft pastel) |
|---|---|---|---|
| blank page, held | 790 → 60 (no search) | 510 → 0 | 9,400 / 1,400 / 1,360 (lay 290, settle 8,200, compose 700 for the marker) |
| box 3 Mpx | 755 → 520 | 400 → 160 | 6,140 / 990 / 900 (settle 5,400 for the marker) |
| box with a 6 px gap | 755 → 520 | 400 → 160 | as the box |
| small box 85 kpx | 49 → 47 | 18 → 11 | 250 / 60 / 70 |

Left in the box's find: the flood 290 (ink read 80, distance bands 50, gap test 60), under 25, pockets 18. The first
fill in a fresh process also spent 24 s in the brush's first resolve (ResolvedBrush.of: loading textures), not in the
fill. Not measured: the panel's render of the fill (the bench is engine only).

### 3.4 Memory that sits there (#9)
- Free the cover sheets (CoverPattern 24 MB, CoverTexture 16 MB) when you leave the shelf, or let the system take
  them back when memory is short. Turning to a cover would then sometimes wait while they're remade.
- Size the Undo budget from the heap (e.g. 24 MB on the Manta instead of 32). That means fewer Undo steps on a
  heavy page.
- Let only one background job decode a full page at a time (copies, save, notebook pictures and ghosts can all do
  it at once right after you leave a notebook).

Done on branch `standing-memory` (2026-10-23), all three, plus the print plane and the shelf's map from §3.5. Going back
to drawing lets go of the cover sheets. Undo's budget is an eighth of the heap (24 MB on the Manta), and a whole-page
step like Clear page keeps the old 32 MB so it stays undoable after more strokes. Background jobs take turns on whole
pages (`DocumentCodec.ONE_PAGE`). Desktop, `-Xmx192m`, Manta page, 60 Large strokes after the shelf's covers: 117 MB
kept before, 76 after. Undo held 14–15 of those strokes before, 10–12 after.

### 3.5 Small things noticed, not changed
- The Undo size count for a layer step leaves out the print plane, so the budget is under by 4.6 MB a layer.
- The page cache's size guess ignores shares, print, kinds and masking fluid. In practice it still keeps one page.
- The shelf's map of covers made from files grows by one entry each time a notebook changes. That's a few hundred
  KB per edit until the app restarts.
- A picture that failed once is never retried until the app restarts.
- After a save, the old notebook file stays open until the system tidies up.

## 4. How it was tested
- Desktop (`-Xmx192m`): long sessions on a 250-page notebook (500 actions: heavy presets at Large including wet
  paint, float and halo, fills, Undo/Redo runs, adding, deleting and merging layers, page turns, saves, recovery,
  pictures and ghosts). All 4 runs ran out of memory in the engine alone (in a fill, a save, or a ghost decode),
  which matches #1, #2, #4 and #9. Each action was also run alone to find the least heap it needs, and the PDF
  notebook export was tested at 124 and 58 pages.
- Manta (bench app only, ~20 min): Large washes, 18 wet strokes before and after option A, a 40-action session,
  and live strokes with the app's memory sampled.
- The bench and checks apps were uninstalled afterwards and their export and bench folders removed. The device was
  left on its home screen. `Document/Inkwash checks` (from an earlier day) was left as it was.
