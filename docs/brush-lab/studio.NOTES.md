# Brush Studio: notes

Page: `studio.html` (one file, about 1.1 MB; the only thing it loads is the Google Fonts stylesheet for the fallback
Roboto Mono). Built by `node docs/brush-lab/studio-src/build.cjs`.

## How it's built
- **The engine is the lab's**, read from `prototypes/modular-lab-src/` at build time and never copied: `port.js`,
  `oldmodel.js`, `engine.js`, `stroke.js`, `float.js`, `parts.js`, `checks.js` in the lab's order, then every other
  engine file there, sorted (the list in `engine-files.cjs`, which Brush Scan's build uses too; today `newmods.js`: Blend, Stipple, Hatch, Resist, Halo, shown as Mold). `ui.js` is the lab's page and is
  left out. A new choice or dial in the lab's tables shows up in Studio's cards, sweeps and grids when Studio is
  rebuilt; nothing in Studio lists the choices.
- The paper scans come from the lab's page (`TEXTURE_SRC` in `modular-lab.html`); iA Writer Duo 400 and 700 from
  `app/src/main/res/font/`, embedded as data URIs, with the OFL notice in the footer.
- `studio-src/`: `page.html` and `studio.css` (the Scribbler Style), `render.js` (one picture from a job; no DOM),
  `studio.js` (the page), `qr.js` (a small QR encoder after Nayuki's, byte mode, level M, versions 1–10).
- **Pictures** run in up to four Web Workers made from blob URLs of the engine and `render.js`, or on the page when
  workers can't start (`?inpage` forces it). A job is plain data; the answer is the page at the Manta's 16 greys. A
  newer request for the same place on the page replaces one still waiting; answers are cached by brush key and size.
  The test page and the live stroke always run on the page.
- Every picture is drawn at the Manta's scale (1.875 device pixels a CSS pixel, as the lab's test area).
- **Loop and wave** (`docs/DESIGN.md`): anything that is a whole brush is the loop (the name's chip, sweep steps, the
  Mix grid and Roll, history rows, the library, shared brushes); part cards' choices are waves.

## Screens
- **The brush bar** (under the tabs, on every tab): the brush's loop and name (click to rename), Undo, Revert, Save,
  Load, then Style and Randomize, and the "This brush" sentence. Why there: Save, Revert and the rest act on the brush,
  so they sit beside its name and loop rather than in the page header's far corner, where a wide window leaves them a
  long way from what they save; top left is where reading starts and the first thing under the tabs. The bar stays in
  view when a narrow window scrolls. The header keeps only the Studio's name and the tabs.
  - Share (beside Load) opens a small panel under it: the brush code with Copy code, its QR code (for a phone: the
    tablets have no camera), and Publish to Community library (see "Community library" below). Load opens the
    library, with Load a code at its top (any code the lab reads: M, B, the app's I and P).
  - A link opens a brush: `studio.html#<share code>` loads it as This brush over what the page remembered, and a new
    hash later does too (codes are letters, digits, `.`, `_` and `-`, which the artifact host passes through). No Copy
    link: inside the artifact host the page runs in a frame that can't see its own claude.ai address, so the link is
    the page's link with `#` and the code (Copy code) added by hand.
  - Export (in Share, under Download for tablet) downloads `Scribulor.brushset`, a Procreate set of This brush and the
    library's Saved brushes, made in the page by `export.js` (the brush export spec,
    `docs/superpowers/specs/2026-10-20-brush-export-design.md`).
  - Save is solid while the brush has changes the library doesn't hold and reads "Saved" with a check, outlined, once
    it does (as the app's Brush Lab), so its state shows at a glance.
  - Revert returns the brush to its last saved version, or to how it was when loaded or opened (the library, a code,
    a tried roll or shared brush, or the brush the Studio opened with); off when there's nothing to revert. It is a
    history step, so Undo takes it back.
  - Randomize rolls the whole brush again in its style (`rollStyle`), keeping every locked part and dial (Erasable's
    lock across media too); Style lists every style, Anything goes among them, and picking one rolls a brush in it.
    Each roll is one history step; Undo goes back one step (to the step's parent, as clicking it there). A part's die
    rolls that part in the style (its choice from the style's weights, so a style with one choice for a part keeps
    it; then its dials), keeping its locked dials; a dial's die rolls that dial only.
- **Tabs**, in order: Community library (brushes people published, newest first, each with its loop, name, "by …"
  and Try, which makes it This brush and opens Adjust; without the db, eight example rolls labelled Examples), Random (12 random brushes made from this one and its locks; not
  people's brushes; Randomize 12), Lab (every part live: the online lab; the tab on first open), Close-up (no Style
  or Randomize there) and Mix (two parts crossed, the rest held still). A saved "understand" or "share" tab opens Lab,
  a saved "explore" Mix.
- **The Lab's layout** (`buildCards`): the cards (in the word list's order, Erasable after Medium) are measured off
  screen and cut into the fewest columns whose tallest fits the window's height, so they stack to fill it, as wide as
  leaves the test page column between 380 px and 1.1 × the height; in order where it can be, else the tallest card
  first into the shortest column. Labels show under the swatches when the window is 1440 px or wider and they still
  fit; otherwise the header names the swatch under the pointer. Each card says in one grey line what its part does.
- **Favourites**: `docs/brush-lab/favourites.txt` (the owner's picks, one code a line) goes into the page at build
  time, and each code is added to the library once, the first time the Studio opens after it was added (deleting one
  keeps it gone). zealous-shallot uses Stipple, so the app can't read it yet.
- **Sizes**: every tab fits the window with no page scroll on a laptop (1280 × 800, 1440 × 900) and a wide screen
  (1560 × 970, 1920 × 1080, 2240 × 1060), checked by `shots.cjs`, also with the brushes that have the most dials. The
  page uses the whole width; the test page, sweep and before/after are a column the window's height, the test page
  taking what the others leave. Text is 13 px, labels
  and values 12 px, never smaller; a dial is one row (name, value, lock, die, the slider along its foot). Choice
  swatches have no captions: the card's header names the swatch under the pointer (in grey), and each has its name
  as its label and tooltip. Narrower than 1180 px (or shorter than 620 px) the columns stack and the page scrolls.
1. **Lab**: every part (Tip, Medium, Paper, Drying, Control, Smudge) and Erasable at once, each with its choices
   as waves and every dial as a slider, with a lock and a die. Dials at their default are grey. Beside them: the test
   page (mouse, pen with pressure and tilt, touch; Eraser, Clear; three sizes from the style's own and three greys),
   the sweep and before/after. Every stroke on the test page is drawn again with the brush whenever it changes (live
   while dragging if a redraw took under 140 ms). Dragging a dial or clicking its name focuses it: the sweep shows it
   in seven steps (or each option), the rest held still; a step is taken by clicking it. Before/after is the last
   change, or, for a dial just focused, that dial at its default (or its far end) against now, with the changed
   pixels in ink and the share of the marked area changed.
2. **Close-up** (Fine-tune until 2026-10-30, the owner: "it's not really a way to fine tune, is it? it's more of a closeup view"; its magnified view is headed Magnified): the test stroke with its pressure, tilt and speed as curves (dragging pulls the curve toward the pen
   around that point; the stroke is drawn again as it moves); "Use my last stroke" takes the test page's last stroke,
   its shape and its curves. The close-up shows the stroke's own pixels at 1, 4 or 16 screen pixels a Manta pixel
   around the dashed cursor. History: every change a node; a change made after jumping back starts a branch (shown
   indented); Compare two side by side with the settings that differ; Save as new (a kin name, into the library).
3. **Mix** and **Roll**: Cross two parts (any two, every choice of each, the rest held still); Anything goes: 12 rolls over
   every choice and the whole range of every dial (about four dials in ten left at their default), past the styles'
   bands, keeping the locked parts (the same locks as the cards).
4. **Community library** and the **Share** panel: as under Tabs and the brush bar above.
- Load and Save: a library in this browser (`localStorage`, key `inkwash.studio.v1`, wrapped in try/catch), with the
  basic tools (the app's four, Sumi and Clear water, Sketch pencil, Steady line, Soft wash). The current brush, the
  history (240 nodes), the locks, the test stroke and the tab are remembered too.

## Checks (`studio-src/`)
- `check.cjs`: the click-through in headless Chrome (puppeteer-core from `~/node_modules`), 119 checks (a link's code among them): the tabs, the Share panel (its Export too) and the Community library (without db, then with a fake `window.claude.use` giving db and user stubs: listing, bad rows left out, names escaped, who, Try, Publish with its inline confirm, Remove, one subscription, a refused write), the cards' lines and packing, the favourite, the brush bar (Save's state, Revert, Undo), Randomize, Style and the dice with locks, workers start
  and draw, a dial changed moves the sweep and before/after, the test page, Close-up's curves, zoom, last stroke,
  history branch and compare, Mix, Roll, codes, QR, load, the library, remembering, drawing without workers, no sideways
  scroll at 1600, 1280, 1024, 768 and 390 px, no console errors, nothing from the network but the fonts.
- `export-check.cjs`: Export's Procreate set made in Node from 14 example brushes and read back (zip, PNG, binary plist,
  `unzip -t`, Python's `plistlib`): see the brush export spec's "Built".
- `java-codes.sh`: 604 Studio codes (every style rolled, and rolls over whole ranges) read and written again by the
  app's `ModularCode`. None wrong. Codes using the lab's newer Erasable and Blend dials come back without them, and
  Stipple, Hatch, Resist and Halo don't read, until the app has them. Since the app's Halo (2026-10-18): 604 of 604;
  since the catch-up (2026-10-27, Print, Wet and the rest): 623 of 623.
- `presets-check.cjs`: every Basic tool's code (`ToolPresets.ROWS`) read and written again by the web engine. Before
  the catch-up 77 of 118 read; now all 118, 115 byte for byte and the three Hand Hatch ones written as Hatch, as the app
  itself writes them (with the app's own writing of each, from `StudioCodeEcho`, as its argument: 118 of 118).
- `shots.cjs`: screenshots of every tab at 1440 × 900, 1280 × 800, 1560 × 970, 1920 × 1080, 2240 × 1060, 900 and 390 px wide, saying
  whether each laptop-sized tab fits without scrolling.

## Community library: the artifact's `db` (as built)
**Publish with these capabilities** (exactly; the Artifact tool's `capabilities` input):
```json
{"db":{"rules":[{"path":"brushes","read":"view","write":"owner"},{"path":"brushes/{self}","write":"interact"}]},"user":{"scopes":["profile"]}}
```
- **Rows**: one document a person, `brushes/<their user id>`: `{ list: [{ code, name, at }] }`, their own published
  brushes, newest first, at most 50 (a code is about 110 bytes, so a document stays far under 256 KiB). `code` is the
  M code, `name` the brush's display name (32 characters at most), `at` the time in ms. Who published a brush is the
  document's id, which the rules tie to its writer; no `by` field (a field could be forged, the id can't) and no
  names (they are resolved with `user.profiles(ids)` when drawn and never stored). Not one document a brush: a
  `{self}` rule names exactly `brushes/<id>` and what is under it, so a person's own brushes have to live in their
  own document (or under it, which would take a subscription a person to list).
- **Rules**: `brushes` is read by everyone the page admits (`view`) and written only by the artifact's owner; each
  person writes `brushes/<their id>` at `interact` (Contributor) or above. The owner can remove anyone's (by hand,
  `ArtifactData`); the page only offers Remove on the viewer's own. The root keeps the defaults (nothing else is used).
- **Who can do what**: anyone who can open the page and is signed in browses and tries. Publishing needs Contributor
  (or Editor, or the owner): share the artifact with general access at Contributor for people to publish. Viewers and
  Commenters, and every signed-in visitor from outside the organization (public link or email invitation), hold `view`:
  they browse and try, and Publish says "Contributor access needed." A signed-out visitor gets no data: the eight
  examples and "Sign in to see and share brushes."
- **The page** (`studio.js`, "Community library"): `await claude.use("db")` and `claude.use("user")` after the page is
  ready, never `window.claude.db`. `null` (signed out, not granted, a saved copy, the file on disk) leaves the eight
  examples, tagged Examples, with the quiet line. Otherwise one `db.collection("brushes").limit(1000).onSnapshot` for
  the page's life (subscribed again only after the bridge stops answering, three times at most); a snapshot rebuilds
  the list, rendering never subscribes. An empty library says "No brushes shared yet." over the examples.
- **Untrusted rows**: each entry must be an object with a string code that `readAnyCode` reads, the name is trimmed to
  32 characters, at most 50 entries a document are read; anything else is left out. Names, who and captions are
  escaped (`escH`) or set as text.
- **Writes**: Publish to Community library (Share panel) asks inline ("Everyone with this page can try it." Publish /
  Cancel), then one `set` of the viewer's own document with the brush first (the same code published again moves to
  the top); once there the button reads Published. Remove on the viewer's own card: one `set` without it, or `delete`
  when it was the last. One write at a time; a transient error is retried once. `invalid_argument` (below Contributor)
  turns Publish off for the visit; `quota_exceeded` says "Library full." `user.can("data.write") === false` turns it
  off up front.
- Try loads the code into Studio as Load a code does; a brush is never changed in place, so a shared one is safe to
  try.

## MonoPaint users, later
- The share code is the whole brush in about 60 to 110 characters of plain text (`name.M…`), so it travels anywhere text
  does: a forum post, a message, a note on the tablet. The app's Tools page already loads codes.
- The Supernotes have no camera: the QR code is for a phone (scan, then paste the text on the tablet, or send it to
  the tablet). A later app version could take a code from a link or a shared text file.
- A public Studio link (or a second, read-only artifact listing the shared rows) would let MonoPaint users without
  Scribulor browse and try brushes in the browser; the app's codes would need the M format in MonoPaint itself to load
  there, which is a MonoPaint change, not Studio's.

## Known gaps
- Wet (Drying's eighth choice) draws as Instant, as the app does where it has no wet canvas: no mixing into wet paint
  under it, evening out or staying wet across strokes. Its Stays Wet is kept in the code.
- Gravity (Quick's and Slow's Runs, Drips, Pools, Grainy, Rock to Blend; Charcoal's Dust; the hand's page tilt) needs a
  tilted page: hidden, kept in codes, not drawn.
- Light ink over dark (the app's `ResolvedBrush.lightOver`: a pen or marker lighter than mid grey covering darker
  marks) isn't ported: a light grey only darkens here.
- Hand Hatch (cut from the app) reads as Hatch, as the app reads it.
- The engine draws on one tone page, as the lab's (not the app's ink and wash layers).
- Before/after and the swatches use a sample's size (sized to the tile); the test page, the stroke and the close-up
  use the real size.
- The test page's replay draws every stroke with the current brush, at the size and grey it was drawn with; Slow
  drying, Float and Halo are finished at once on a redraw (they animate only for a stroke just drawn).
- Halo (shown as Mold) needs other marks near it to bridge; alone on a tile it shows little.
- This brush is a persistent left sidebar (loop, name, words, Undo/Revert/Save/Load/Share, then Adjust / Close-up / Mix); top tabs are only Community library and Random (none of the three filled there); Style and Randomize sit at the top of Adjust only, Random has its own "Generate other random brushes" at the foot of its grid.
- Share has Download for tablet: saves `<brush name>.inkwash.txt` (the brush code) via the claude.ai `downloads` capability (`window.claude.use("downloads")`, as Brush Scan), else an `<a download>` link. The published page needs `capabilities: {downloads: true}`. The tablet (`BrushImport.runAll`) reads such files from Document/Scribulor at launch and on the drawing screen's resume.
- New names say what stands out (the lab's `describedName`, from `names/traits.txt`): a style's roll, the Random tab's
  rolls (`wildRoll`, named last) and Save as new (`kinOf`, keeping the trait's word under a new produce).
