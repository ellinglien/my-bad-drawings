# Kind layers: each kind of mark on a plane of its own (step 1, the engine)

Status: the owner's direction (2026-10-27); built on branch `kind-layers`. Builds on
`docs/archive/superpowers/specs/2026-10-20-erase-kind-design.md` (the per-pixel kind bitmask, which stays for old paint).

## Why

A layer pixel held one blended grey and a bitmask of the kinds in it. Where pen crossed pencil the targeted eraser could
only guess (the Erasable rule decided), and nothing could hide one kind. Step 1 gives every kind its own pixels; step 2
(a Pen · Paint · Dry Media row, each Shown/Faint/Hidden) is then UI only.

## Mechanism

- **Planes.** Every engine layer (`ToneDocument.Layer`) may hold up to four *kind planes*, `Layer.planes`, each itself a
  `Layer` (tones, coverage, its own Erasable share and print tone, its own `opacity`). Bottom to top, the owner's order:
  `PLANE_DRY` (Graphite, Charcoal, Crayon), `PLANE_WASH` (Wash; Ink wash's wet ink; since 2026-10-29 textures in any
  Medium: Stipple, Hatch and Hand Hatch tips, Print Shading, Screentone), `PLANE_PEN` (Ink, Wet, Resist's sheen), `PLANE_OPAQUE` (Cover, shown as Opaque). The layer's own pixels are its **base**, under its planes: old
  paint and paint of no kind (MonoPaint's tools, picture import), with the old bitmask rules.
- **Lazily made.** A plane is made the first time a gesture of its kind begins on that layer (`begin(erasable, kinds)`,
  `planeOf`), never before. A plane's `kinds` is its kind, so the eraser's existing rules make it exact.
- **Opaque's bit.** `KIND_OPAQUE` (8) is a flag beside `KIND_PAINT` (`ResolvedBrush.kind` of Cover is `PAINT|OPAQUE`): it
  picks the plane only. The eraser's kinds stay three bits, so Paint takes Wash and Opaque; the per-pixel bitmask and the
  `KIND` section are unchanged.
- **The page's order.** The engine layers pair up (wash `2k`, ink `2k+1`, as `InkWashLayers`). A pair composites its two
  bases, then its planes kind by kind, each kind's wash-half plane under its ink-half one: `ToneDocument.stacked(layers, k)`
  is the one walk every composite uses. A page without planes composites exactly as before.
- **Kind visibility (for step 2).** A plane's `opacity` multiplies its layer's. `setKindOpacity(plane, value)` sets it on
  every plane of that kind on the page and on planes made later. Not saved, not an Undo step (step 2 decides).
- **What a stroke works on.** Its own paint's kind's plane. A stroke with no paint of its own that moves paint (Lift,
  Drag, Smear, the smudge tool's stumps, Clear Water) works on its Medium's plane (`ModularStroke.kinds`): a stump blends dry
  media, water lifts washes; on a layer without that plane (old paint) its base, as ever.
- **Reads between gestures** (`tone`, `opacity`, `strokeBase*`) read the plane the last kinded gesture on the selected
  layer drew on (`selected`), until another layer is selected; a stroke selects its plane before it begins
  (`ModularStroke.prepare`), `WetCanvas` before its slices (`selectKind`).

## Everything that reads or writes a layer

| Item | How |
|---|---|
| Compositing, Java | `compositeTone(Without/Kept)`, `composite` (Blend's page under), `layerTones`, `shownTone`, `snapshot`, `exportPixels` walk `stacked`. |
| Compositing, native | `visibleLayers` hands every base and plane in order to `viewport_bitmap.c` (up to 43 arrays, was 11); `ShapePreview` too (`layers.h`: 40). `SelectionPreview` has no caller: left. |
| Per-layer reads | `layerDarkness` (Fill's walls, Stay In, watercolor grooves), `layerTone`, `layerPixels(index, …)` read the layer's look (base and planes); `layerPixels/layerKept(index, kinds, …)` one plane (a stump's wash blend); `printTone(index, …)` the darkest of them. |
| Undo | An Edit's layer may be a plane; Undo finds its layer (`ownerOf`), selects it, restores the plane's tiles. Snapshots copy planes copy-on-write as layers (`Layer.copy`, `release`). |
| File | See below. |
| Export, PNG/PDF, thumbnails | `PagePicture.Composite`, `ViewportBitmap.compose`, `LayerThumbnail` walk `stacked`. |
| Dropper | `shownTone` reads `visibleLayers`: what you see. |
| Eraser | Everything takes the base and every plane. A chosen kind takes its planes whole (exact) and the base by the old bitmask rules; other planes are left byte for byte. The ink layer's Pen plane (its base when it has none or Pen isn't taken) goes as the eraser moves (Tone's scrape is there), the rest at lift, one Undo step, as the wash half already did; the preview shows the result throughout. |
| Smudge, Lift, Drag, Smear | Their Medium's plane (above). Oils (paint of their own) drag their own plane. |
| Wet continuations | Settle, gravity, Halo (`foundComposite(…, kinds, …)`), Float's films and print (the bath keeps its first stroke's kind; strokes on water begin with it), `WetCanvas` (one plane, its first stroke's; later wet strokes mix into it), Pastels joining wet paint (the paint's plane). |
| Fill, stamps, shapes | Fill and stamps are brushes: their kind's plane. MonoPaint's shapes, fill and picture import: the base. |
| Lasso, Cut Stamp | `LassoPlanes` lifts and lays every plane of the pair (base and four kinds, each half, in the page's order) and the fluid. |
| Mirrors | Copies draw the same brush, same plane; Copy Strokes' "change laid again" (`layAgain`) lays on the stroke's own plane. Real Mirror's reflection, expand canvas, resize (`InkWashLayers.resized`) carry every plane (`Layer.each`). |
| Clear Page, Merge, Duplicate | Clear Page swaps in blank layers (planes go with the old ones, back on Undo). Merge flattens planes in the page's order into the new ink. Duplicate copies pages whole. |
| Blend modes | Darken, White, Invert read the page under as shown, planes included. **Behind goes behind within its own plane only**; across planes the kind order decides. |
| Fading, Lemon Juice | Marks on a plane fade there; the file names a plane by its layer and slot (`ToneDocument.address`: `layer | slot << 4`). |
| Tracing paper, Replay's veil, notebook trim | Walk the planes too (`GhostPlane`, `NotebookMigration`). |

## File format

A tagged section, `PLNS` (0x504c4e53), after `KIND`, in TSM5. Written only when some plane holds paint; a page without
writes TSM3/4/5 as before, byte for byte. For a layer with planes the layers' part holds its **look** (base and planes as
one, `ToneDocument.sheets`), so a reader before planes opens the page as it showed (one plane a layer, the pair's order
as before planes, kinds lost) and saves it without losing pixels. In `PLNS`, per layer a byte: bit `p` for each plane
with paint, bit 4 when its base holds paint; then the base's tones and coverage (if bit 4; else the base is bare), then
per plane its tones and coverage, a flag and its shares, a flag and its print tone. The base's shares, print tone and
kinds stay in `ERAS`, `PRNT`, `KIND`. Plane opacity isn't kept. A `FADE` mark on a plane names it `layer | slot << 4`.

## Memory (Manta page, 1920 × 2560 = 4.9 M pixels)

- A plane costs **9.8 MB** (tone and coverage), +4.9 MB with permanent paint (a share), +4.9 MB with Screen (print tone).
- Made only when its kind first draws on that layer. A page drawn in pen only: +9.8 MB over before (the base, now bare,
  is still allocated); pencil and pen: +19.6 MB; all four kinds: +39 MB. The old 4.9 MB bitmask is no longer made for new
  paint (it was for pen over pencil).
- Undo captures are per 64 px tile of the plane drawn on, as before. Snapshots share planes copy-on-write.
- A page's file grows by its planes (the look for older readers plus the planes): about 3× a one-plane-a-layer file on a
  pencil, pen and wash page (the checks' 480 × 360: 4.3 KB against 1.3 KB).

## Looks different

- New marks stack by kind, not by when they were drawn: opaque paint covers pen and washes; pen covers washes and dry
  media; a wash glazes over dry media but lies under opaque paint (it no longer darkens gouache drawn before it).
- Dry media (pencil, charcoal, crayon, pastel, white chalk) lie under pen: a light pastel no longer covers ink.
- Paint of no kind (old pages' paint) lies under every new plane: a new White wash lightens old ink lines too.
- A Half or Faint layer fades each plane by itself: where pen crosses a wash the wash shows a touch more under it.
- A stump or finger smudge blends only its Medium's plane: pencil smears, pen lines stay crisp; water lifts washes, not
  pen or opaque paint. A Wet stroke re-wets only its canvas's plane.
- White chalk erased off ink shows the ink again (it was on its own plane).

## Not done (step 2 and later)

The show/hide row; saving kind visibility; a shared blank base to save 9.8 MB on new pages.
