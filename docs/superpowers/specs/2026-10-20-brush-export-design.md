# Brush export: Brush Studio brushes in other apps (2026-10-20, design; Procreate MVP built 2026-10-20)

The owner (2026-10-19, `docs/archive/notes/2026-10-19-owner-ideas.md`, "Brush designer for other apps"): "it'd be cool to think
about how the brush designer could be a tool for developing brushes for other apps... a unique idea that would be amazing
to make cross platform (or just a big one like procreate?)". This spec says which formats, what carries over, and how
Brush Studio would write them. The Procreate MVP is built: see "Built" below; the rest is still the design.

## Built (2026-10-20): Procreate sets, the MVP

The owner's answers (2026-10-20): an iPad with Procreate to test on; **sets** rather than one brush at a time, so
there's less to fiddle with; no Inkwash credit in the brushes for now; Procreate only, Krita and MyPaint later.

- **One button.** Share panel, under Download for tablet: **Export**, with the note "A Procreate set of this brush and
  the saved ones. Drying and smudge stay in Inkwash." It downloads `Inkwash.brushset`; status "Set exported." (or
  "Export failed."). No list of formats and no new settings: one format until there's a second.
- **What goes in the set: This brush, then every Saved brush in the library**, each once (same name and code). Studio
  has no toolbar slots, and Saved is already the brushes the person chose to keep; Basic tools stay out. The set is
  named "Inkwash" (Procreate needs a set name; the brushes keep their own names and no author).
- **Code:** `studio-src/export.js`, built into the page after `qr.js` (`build.cjs`, script `#export`) and into
  `out/node-engine.cjs` for Node. No server, no external scripts:
  - `zipStore`: a stored zip with CRC-32, folder entries as CSP2PC's sets have them.
  - `encodePng`: 8-bit greyscale or RGBA, Sub-filtered, deflated by `CompressionStream("deflate")`.
  - `writeBplist`: bplist00 (reals as doubles, ints, bools, ASCII and UTF-16 strings, data, UIDs, arrays, dicts; equal
    strings shared).
  - `procreateArchive`: the template's `$objects` with this brush's name, `creationDate` now, and the mapped keys; each
    key keeps the template's type (an int stays an int).
- **Template:** `studio-src/procreate-template.json`, the `Brush.archive` of CSP2PC's `Seed.brush` [1] (MIT, its
  notice kept in the file and so in the page), decoded once by `node export-check.cjs --template`: 60 objects, a
  `SilicaBrush` root of 220 keys. Its `Signature/` picture and thumbnail are not used.
- **Pictures, drawn by the engine** (on the page, between brushes, a few hundred ms each):
  - `Shape.png`, 1024 × 1024 greyscale, white paints: one tap of the brush made clean (Smooth paper, Instant, Smudge
    off, no tapers, wobble or flick, Size ×1, a dark blend) at 220 px on a 768 px page, cropped square round the tap to
    the mark plus 6%, scaled up, its darkest point white. Split Nib and Rake tap at half pressure. A tip that draws
    nothing on a tap (Hake) draws a short stroke; Spiro taps as a Round; Resist as Ink. 1024 rather than 2048: the tap
    is drawn at about 400 px, so 2048 would only add bytes.
  - `Grain.png`, 512 × 512: the paper's 256 px scan tiled, stretched to the full range, white at the high points. None
    for Smooth paper or a Texture under .02: the template's bundled blank grain stays.
  - `QuickLook/Thumbnail.png`, 1060 × 324 RGBA: a wave stroke of the whole brush (its paper and drying) in the 16
    greys, black on clear.
- **The mapping** (`procreateValues`; every value 0 to 1):

| Inkwash | Procreate key | As written |
|---|---|---|
| Size (style size × Size, in mm) | `paintSize` | mm / 24, a guess at the slider's scale |
| Taper In, Taper Out | `pencilTaperStartLength`, `pencilTaperEndLength` (and the finger `taper*` twins) | × .35; `pencilTaperSize` .9 when either is on |
| Wobble | `plotJitter` | × .05 |
| Pressure Size, Pressure Dark | `dynamicsPressureSize`, `dynamicsPressureOpacity` | × Response |
| Tilt Size, Tilt Dark | `dynamicsTiltSize`, `dynamicsTiltOpacity` | × Response |
| Speed Size (Fast Strokes Thinner), Speed Dark | `dynamicsSpeedSize`, `dynamicsSpeedOpacity` | × Response; Thicker writes no size |
| Medium Wash, Pooling (Dark Edge) | `blendMode` and `extendedBlend` 1 (Multiply), `paintOpacity` .75, `burntEdgesAmount` | Pooling as it is |
| Opaque's Opacity, Graphite's Grade | `paintOpacity` | Opacity (at least .15); .45 + .55 × Grade |
| Everything else | `blendMode` 0 (Normal), `paintOpacity` 1, `maxOpacity` 1 | |
| Paper Texture | `grainDepth`, `textureMovement` 0 (the grain stays put, as paper) | Texture; + .3 for Charcoal, Graphite, Crayon |
| Stipple | `plotSpacing` .09 (30%), `shapeRandomise`, `shapeFlipXJitter` | |
| Stamp's Spacing | `plotSpacing` | Spacing², Procreate's square |

  Everything else keeps the template's value, among them spacing (5%), StreamLine (none), `minSize`, `maxSize`, size
  and opacity limits, `shapeRotation` 0 (fixed to the page, as a chisel's angle is baked into its shape). Tilt angle,
  Nib responses, wet mix for Smudge, Head Follows and the sets of the toolbar are later (phase 2).
- **Checks:** `studio-src/export-check.cjs` (Node, 25 checks) makes a set of 14 brushes (the Basic tools; Round,
  Chisel, Flat Nib, Filbert, Stipple, Stamp, Hake, Spiro; a White ink; a UTF-16 name), reads it back with its own zip,
  PNG and bplist readers, `unzip -t` and Python's `plistlib`: the template written and read back unchanged; every entry
  stored with a right CRC; `brushset.plist` names the set and lists one upper-case UUID a brush, a folder for each and
  nothing else; per brush the files, an `NSKeyedArchiver` `SilicaBrush` with every template key and no type changed,
  name, date, the mapped values, grain or the blank, Shape.png greyscale with white painting, Grain.png full range, a
  thumbnail; a Round's shape white in the centre and black in the corners; Wash Multiply, pressure and tapers carried,
  grain depth. `check.cjs` clicks Export in the page: the set downloads, with this brush and the saved ones, and
  nothing loads from the network.
- **Download:** a Blob and an `<a download>` link, through the same `saveFile` as Download for tablet (inside claude.ai
  it uses the `downloads` capability when the artifact has it, as that does). Type `application/octet-stream`, so
  Safari keeps the `.brushset` name.
- **Owner's test:** `docs/archive/notes/2026-10-20-procreate-export-test.md`.

## The short answer

- **First target: Procreate** (`.brush`, then `.brushset`). The biggest audience for bought and shared brushes, the
  owner's lean, and feasible: the format is undocumented but well picked apart, and two MIT-licensed tools already write
  it. A brush is a zip of one binary plist and two greyscale PNGs, all of which a web page can make with no server.
- **Second: Krita** (`.kpp`), open source, its format readable in its code, free on the desktop.
- **Third: MyPaint** (`.myb`), plain JSON and the easiest to write, but no textures, so the least of Inkwash survives.
- **Skip Photoshop** (`.abr`) for now.
- What travels is the look of one dab and the paper under it, plus how pressure, tilt and speed change it. Drying,
  Smudge physics and the mischief stay Inkwash's: an exported brush is a snapshot of a stroke, not the living ink.

## The formats

### 1. Procreate `.brush` and `.brushset`

- **Layout.** A `.brush` is a zip holding `Brush.archive`, `Shape.png`, `Grain.png` (optional: without it the archive
  names a bundled grain), `QuickLook/Thumbnail.png` (1060 × 324 RGBA) and, when signed, `Signature/`. A `.brushset` is a
  zip of one folder per brush, each named by an upper-case UUID, plus `brushset.plist` (XML: `name`, `brushes` the UUID
  list). Seen in CSP2PC's `Seed.brush` and its `build_brushset` [1], and in barakbl's writer [2].
- **Brush.archive.** A binary plist written by `NSKeyedArchiver` (`$archiver`, `$version` 100000, `$top.root`, and a
  flat `$objects` table whose entries point at each other by UID). The root is class `SilicaBrush` (superclass
  `ValkyrieBrush`), `version` 2, about 230 keys; curves are `ValkyrieMagnitudinalCurve` objects whose `points` is an
  `NSArray` of strings like `"{0.000000, 0.000000}"`; dates are `NSDate` (`NS.time`, seconds since 2001); colour is 16
  bytes of four floats. Dumped from `Seed.brush` [1] for this spec.
- **Key fields** (names as found in the archive):
  - Shape: `shapeRotation`, `shapeScatter`, `shapeCount`, `shapeRandomise`, `shapeFlipXJitter`, `shapeRoundness`,
    `shapeAzimuth` and `shapeOrientation` (shape follows the pencil's direction), `shapeFilter`, `shapeInverted`.
  - Grain: `textureScale`, `textureZoom`, `textureMovement`, `textureRotation`, `textureInverted`, `textureBrightness`,
    `textureContrast`, `grainDepth`, `grainBlendMode`, `textureOrientation`, `texturizedGrainFollowsCamera`. The handbook's
    two grain behaviours: Moving ("a more streaky and blurred effect") and Texturized, a still texture under the stroke
    ("a crisp and clear effect") [3].
  - Stroke path: `plotSpacing`, `plotJitter`, `plotSmoothing` (StreamLine), `plotJitterTilt`.
  - Taper: `pencilTaperStartLength`, `pencilTaperEndLength`, `pencilTaperSize`, `pencilTaperOpacity`, `taperPressure`;
    the `taper*` twins are for fingers.
  - Pressure: `dynamicsPressureSize`, `dynamicsPressureOpacity`, `dynamicsPressureBleed`, `dynamicsPressureShapeRoundness`,
    each with a `…Curve`; `dynamicsPressureResponse`, `dynamicsPressureSmoothing`.
  - Tilt: `dynamicsTiltSize`, `dynamicsTiltOpacity`, `dynamicsTiltGradation`, `dynamicsTiltBleed`, `dynamicsTiltAngle`
    (the angle where tilt starts; the handbook says tilt does nothing below 15° [3]).
  - Speed: `dynamicsSpeedSize`, `dynamicsSpeedOpacity`.
  - Wet mix: `dynamicsMix` (Dilution), `dynamicsLoad` (Charge), `dynamicsPressureMix` (Attack),
    `dynamicsWetAccumulation` (Pull), `wetEdgesAmount`, `burntEdgesAmount`; per CSP2PC's mapping [1] and the handbook [3].
  - Rendering and limits: `blendMode`, `renderingMaxTransfer`, `renderingModulatedTransfer`, `renderingRecursiveMixing`,
    `minSize`, `maxSize`, `minOpacity`, `maxOpacity`, `name`, `authorName`.
- **Shape.png and Grain.png.** Square, 8-bit greyscale; 2048 × 2048 is the usual size (the seed's shape is). **White
  paints, black is clear, grey is partly transparent** [4][5]; the seed's all-white shape draws a full square, and both
  images have an invert switch (`shapeInverted`, `textureInverted`). This is the opposite of Inkwash's page, where ink is
  dark, so the exporter inverts.
- **Stability.** Not documented by Savage; no spec. The archive carries version numbers (`version`, `plotSpacingVersion`,
  `taperVersion`) and new keys arrive with releases: CSP2PC warns that Procreate 5.4's new brush features "might break
  the script" [1]. Procreate fills in missing keys with defaults in practice (barakbl writes a short key list from
  scratch [2]), but that's seen, not promised.
- **Open-source writers and readers.** CSP2PC (MIT, Python: patches a seed brush, maps spacing, jitter, taper, wet mix)
  [1]; barakbl/procreate_brush_claude_tools (MIT, Python: builds the archive from scratch) [2]; aredridel/mkbrushset
  (packs `.brush` files into a set) [6]; tohsakarat/Brush-Converter (reads `.brushset`, `.abr`, `.sut`; can't write)
  [7]; FreyaLupen's Procreate-to-Krita converter and its fork brushporter (read `.brushset`, write Krita) [8].

### 2. Krita `.kpp`

A PNG (the preset's thumbnail) with two text chunks: `version` ("5.0") and `preset`, the XML [9][10]. The root is
`<Preset paintopid="…" name="…" embedded_resources="n">` with the engine's settings inside, and since 5.0 a
`<resources>` element carrying each brush tip or pattern as base64 in CDATA, with `type`, `md5sum`, `name` and
`filename` [10]. Open source, so the reader is the spec. The settings' key names are many and per engine (the pixel
brush, Color Smudge and others), so the exporter would patch a preset saved from Krita, as with Procreate.

### 3. MyPaint `.myb`

JSON, `"version": 3`, with `settings`: each setting has a `base_value` and `inputs`, curves as point lists per input,
e.g. `"radius_logarithmic": {"base_value": .75, "inputs": {"pressure": [[0, -.69], [.75, .24], [1, .29]]}}` [11]. The
settings and inputs are listed in libmypaint's `brushsettings.json` [12]: pressure, speed, tilt (declination,
ascension), random, stroke, direction; radius, hardness, opaque, dabs per radius, elliptical dab ratio and angle, smudge,
and more. Read by MyPaint, Krita's MyPaint engine [13], Drawpile [14] and GIMP. No tip images and no grain: every dab
is a soft ellipse.

### 4. Photoshop `.abr`

Undocumented since Photoshop 6: `samp`, `patt` and `desc` sections in 8BIM blocks, the settings in serialized
ActionDescriptors [15]. Readers exist (AbrKit, Dart [16]); no open writer for the modern versions, and Brush-Converter
gives up on writing it [7]. Procreate imports `.abr` itself [17], so Photoshop users are better served later by a
plain tip PNG. Not worth it now.

### Ranked

| Rank | Format | Reach | Feasibility | What survives |
|---|---|---|---|---|
| 1 | Procreate | iPad, the largest brush market | Medium: binary plist, reverse-engineered | Tip, paper, pressure, tilt, speed, taper, wet mix |
| 2 | Krita | Desktop (Windows, macOS, Linux), free | Medium: big XML, open | Tip, paper, curves, smudge |
| 3 | MyPaint | MyPaint, Krita, GIMP, Drawpile | Easy: JSON | Size, softness, curves, smudge; no texture |
| 4 | Photoshop | Huge, but Procreate reads it | Hard: undocumented | Skip |

## What maps

Direct: one Inkwash setting, one target setting. Baked: rendered by the engine into Shape.png or Grain.png.
Approximate: a near setting that behaves differently. Inkwash only: left out.

| Inkwash | Procreate | Krita | MyPaint |
|---|---|---|---|
| Tip: Round, Soft Edge | Baked shape: a soft disc | Auto tip, fade | `hardness` |
| Tip: Chisel, Flat Nib (Nib Width, Nib Angle) | Baked ellipse; `shapeRoundness`, rotation fixed to the page | Auto tip ratio, angle | `elliptical_dab_ratio`, `_angle` |
| Tip: Filbert, Hake | Baked shape; Hake's Head Follows: shape follows the stroke | Baked tip | Ellipse, approximate |
| Tip: Split Nib, Rake, Bristle, Hatch | Baked shape at rest (two tines, N lines, hairs, a hatch tile); loses the parting, splay and crossing | Baked tip | Inkwash only |
| Tip: Stipple (Dot Size, Scatter) | Baked dot; `shapeScatter`, `shapeCount`, `plotJitter` | Spray engine, approximate | `offset_by_random`, approximate |
| Tip: Stamp (Spacing, Turns, Ink Fade) | Shape is the stamp; `plotSpacing`; follows stroke; Ink Fade as taper opacity | Baked tip, spacing | Inkwash only |
| Tip: Spiro | Inkwash only | Inkwash only | Inkwash only |
| Size, Taper In, Taper Out | `maxSize`; `pencilTaperStartLength`, `pencilTaperEndLength` | Size, fade sensor | `radius_logarithmic`; `stroke` input |
| Wobble | `plotJitter` | Scatter | `offset_by_random` |
| Dry Brush | Baked into the shape (gaps) and Grain depth | Baked | Inkwash only |
| Medium: Ink (Pen Line, Opacity) | `maxOpacity`, Normal blend | Opacity | `opaque` |
| Medium: Wash (Dark Edge, Bloom) | Multiply blend, `wetEdgesAmount`, `burntEdgesAmount` | Wash mode, approximate | Inkwash only |
| Medium: Opaque, Thick Paint | Normal blend, Max transfer; Thick Paint as Procreate's height (`heightAmount`), approximate | Opacity | `opaque` |
| Medium: Charcoal, Graphite, Crayon | Grain depth high, Moving grain for charcoal, Texturized for graphite | Texture strength | `hardness`, approximate |
| Medium: Screentone | Baked dot pattern as a Texturized grain | Pattern | Inkwash only |
| Wet, Bleed, Spatter, Backruns, Pooling | Wet edges, Dilution: approximate | Approximate | Inkwash only |
| Paper (Texture) | Grain.png from the paper's height map, Texturized; `grainDepth` from Texture | Pattern | Inkwash only |
| Control: Pressure Size, Pressure Dark | `dynamicsPressureSize`, `dynamicsPressureOpacity` | Pressure sensors | `pressure` input on radius, opaque |
| Control: Tilt Size, Tilt Dark | `dynamicsTiltSize`, `dynamicsTiltOpacity`, `dynamicsTiltAngle` | Tilt sensors | `tilt_declination` input |
| Control: Speed Size, Speed Dark, Fast Strokes | `dynamicsSpeedSize`, `dynamicsSpeedOpacity` (sign from Fast Strokes) | Speed sensor | `speed1` input |
| Control: Pressure Nib, Tilt Nib, Tilt Turn | `dynamicsPressureShapeRoundness`; `shapeAzimuth` | Rotation sensors | `elliptical_dab_angle` by `direction`, approximate |
| Response | Scales every amount; the curve is a straight line | Curve | Curve |
| Smudge: Lift, Drag, Smear (Pick Up, Lay Down, Own Paint) | Wet mix: Pull, Attack, Charge; Smear as a second brush for Procreate's Smudge tool | Color Smudge engine | `smudge`, `smudge_length` |
| Keeps Paint, Paint Load | `dynamicsLoad` (Charge runs out along the stroke [3]), approximate | Approximate | Inkwash only |
| Erasable, Resist | Inkwash only | Inkwash only | Inkwash only |
| Drying: Quick, Slow, Float, Mold, Fading, Lemon Juice; gravity (Runs Downhill, Drift Downhill, Dust When Tilted) | Inkwash only | Inkwash only | Inkwash only |
| Clear Water, Stay in Lines, Flick and Fling, Grey Shift, Print Shading | Inkwash only | Inkwash only | Inkwash only |

**Baking.** The exporter draws with the engine Studio already runs (`render.js` jobs on the worker), so the PNGs are
Inkwash's own marks, not redrawn guesses:

- **Shape:** one tap of the tip at a large size, upright, full pressure, on Smooth paper with Instant drying, on a
  clear page; the darkness becomes white, cropped square with a margin and scaled to 2048 × 2048. Tips that change with
  pressure (Split Nib, Rake) are baked at the brush's middle pressure.
- **Grain:** the paper's 256 × 256 height map (`app/src/main/assets/textures`, ambientCG, CC0) tiled to 1024 or 2048,
  white at the high points so paint catches them first, as Inkwash's charcoal does. Smooth paper writes no grain (a
  blank white grain, as the seed's bundled blank).
- **Thumbnail:** Studio's sample loop of the brush, the darks on a clear ground, at 1060 × 324.

## Greys and colour

Inkwash has 16 greys and no colour; Procreate, Krita and MyPaint paint in the colour picked there. The exported brush
carries no colour, only masks and responses, so it paints in any colour, and its greys become amounts:

- Pressure Dark and Tilt Dark become opacity (lighter is more see-through), which on white paper reads as Inkwash's
  greys and on a colour reads as a lighter tint of it. Procreate's tilt Gradation is another choice for Tilt Shade.
- Inkwash's 16-step look (its dither and quantized greys) isn't exported; the other apps draw smooth tones. Screentone
  is the exception: its dots go into the grain.
- Wash's "only darkens what's under it" becomes Multiply, which in colour behaves as a glaze.
- Blend Mode White becomes Normal; the person picks white. Behind and Invert have near blend modes in Procreate
  (`blendMode`); test before mapping.
- No colour dynamics are set (hue, saturation jitter all off), so the brush stays one colour, as Inkwash's are grey.

## In Brush Studio

- **Export** in the Share panel beside Copy code and Download for tablet, with a short list: **Procreate**, later
  **Krita**, **MyPaint**. The file downloads the way Download for tablet does today (`downloadForTablet`, the
  `downloads` capability inside claude.ai, a link otherwise). A line under it says what was left out, in the text
  style's words ("Drying and Smudge stay in Inkwash").
- **All in the page, no server.** New file `studio-src/export.js`, built in by `build.cjs`:
  - `zip`: a stored (uncompressed) zip writer with CRC-32, about 60 lines; PNGs are already compressed.
  - `png`: an 8-bit greyscale PNG encoder using the browser's `CompressionStream("deflate")`, with text chunks for
    `.kpp`; canvas `toBlob` gives RGBA only.
  - `bplist`: a binary plist writer (objects, ints, reals, bools, strings, data, arrays, dicts, UIDs), about 150 lines.
  - `procreate`: the template plus the mapping. The template is a real `Brush.archive` exported from Procreate, decoded
    once to JSON (`$objects` as it stands) and kept in `studio-src/procreate-template.json`; export patches its values
    and writes it out with `bplist`. Patching a real brush keeps every key Procreate expects, as CSP2PC does [1].
- **Checks without Procreate** (`studio-src/check.cjs`, Node): export a set of example brushes, then
  - unzip and check the file list, PNG sizes, greyscale, polarity (a Round's centre white, corner black);
  - read the archive back with Python's `plistlib` (it reads binary plists and UIDs) and with the JS reader, and
    compare every key and type with the template: no key lost, no type changed;
  - run tohsakarat's `brushset-parser.py` and the Procreate-to-Krita converter on the result [7][8]: two readers
    written by others.
- **What the owner tests** (only these need the real app):
  - An iPad with Procreate 5.4 or later, files sent by AirDrop or Files: does each brush import, without a crash or a
    blank brush, and does it look like Studio's sample?
  - Pressure and tilt with an Apple Pencil (tilt needs a Pencil that reports it).
  - The same `.brushset` opened twice (UUIDs stay unique), and Procreate's own edit of an exported brush.
  - Later: Krita on a desktop or tablet; MyPaint or GIMP.

## Phases

1. **MVP: one Procreate brush.** Export from the Share panel, a single `.brush`: baked shape and grain, Size, Taper In
   and Out, Wobble, Opacity, Pressure Size and Dark, Normal or Multiply. Tips: Round, Chisel, Flat Nib, Filbert,
   Stipple, Stamp. Node checks pass, then the owner imports five example brushes on an iPad.
2. **Procreate, whole.** Tilt and speed, Nib responses, wet mix for Smudge, baked Bristle, Rake, Split Nib and Hatch,
   Screentone as grain; `.brushset` of the toolbar or a chosen set from the library.
3. **Krita `.kpp`**, from a preset template saved in Krita, tip and pattern embedded.
4. **MyPaint `.myb`**, for MyPaint, GIMP and Drawpile.

## Risks

- **Procreate changes the format.** A new release may want new keys or reject old ones. The template is dated and
  replaceable; checks catch a template drift, the owner's iPad catches the rest.
- **It won't look the same.** Most of Inkwash's character is in drying and smudge, which don't travel. An exported
  brush is a good starting point, not a twin. The left-out line says so.
- **Silent failures.** Procreate may import a broken brush as blank rather than refuse it. Only the iPad test finds
  these.
- **Names and law.** Writing a format to work with it is fine; Studio says "for Procreate" and uses no Procreate logo or
  look. The template must be the owner's own export or the MIT seed [1], never a bought brush.
- **Size.** Two 2048 PNGs and the archive: about 1–4 MB a brush; a big set is a big download.

## Open questions for the owner

Answered 2026-10-20 (see "Built"): 1. yes, an iPad with Procreate; 2. sets; 3. no credit for now; 4. Procreate only
for now, Krita and MyPaint later.

1. Is there an iPad with Procreate (and which Apple Pencil) to test on, or someone who has one?
2. One brush at a time, or whole sets (the toolbar, the library, a set from the Community library)?
3. Should exported brushes carry a credit ("Made in Inkwash Brush Studio") in Procreate's author field and name?
4. After Procreate: Krita (more of the brush survives) or MyPaint (quicker, reaches GIMP)?

## Sources

1. CSP2PC, MIT: <https://github.com/Leon-Schoenbrunn/CSP2PC> (`csp2procreate.py`, `Seed.brush`)
2. procreate_brush_claude_tools, MIT: <https://github.com/barakbl/procreate_brush_claude_tools>
3. Procreate Handbook, Brush Studio settings: <https://help.procreate.com/procreate/handbook/brushes/brush-studio-settings>
4. Making a binary brush in Procreate: <https://yal.cc/procreate-binary-brush/>
5. Tutkit, Procreate brush creation: <https://www.tutkit.com/en/text-tutorials/22800-procreate-brush-creation-step-by-step-guide>
6. mkbrushset: <https://github.com/aredridel/mkbrushset>
7. Brush-Converter: <https://github.com/tohsakarat/Brush-Converter>
8. procreate-to-krita-brush-converter: <https://invent.kde.org/freyalupen/procreate-to-krita-brush-converter>;
   brushporter: <https://github.com/asterryplace/brushporter>
9. Krita manual, Brush Preset: <https://docs.krita.org/en/reference_manual/resource_management/paintoppresets.html>
10. Krita source, `kis_paintop_preset.cpp`:
    <https://invent.kde.org/graphics/krita/-/blob/master/libs/image/brushengine/kis_paintop_preset.cpp>
11. mypaint-brushes, `deevad/2B_pencil.myb`: <https://github.com/mypaint/mypaint-brushes>
12. libmypaint `brushsettings.json`: <https://github.com/mypaint/libmypaint/blob/master/brushsettings.json>
13. Krita manual, MyPaint engine: <https://docs.krita.org/en/reference_manual/brushes/brush_engines/mypaint_engine.html>
14. Drawpile, MyPaint brush settings: <https://docs.drawpile.net/help/draw/mypaint>
15. Adobe forum, ABR format: <https://community.adobe.com/t5/photoshop-ecosystem-discussions/where-to-find-abr-brushes-file-format-specification/td-p/3537426>
16. AbrKit: <https://pub.dev/packages/abrkit>
17. Procreate Handbook 5.3, Share brushes: <https://help.procreate.com/procreate/handbook/5.3/brushes/brushes-share>
