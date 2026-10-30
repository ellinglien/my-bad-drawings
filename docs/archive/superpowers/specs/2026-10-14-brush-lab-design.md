# Brush Lab: design

Approved by Elling on 2026-10-14 ("i think it's ready to go … build it please"),
after eight rounds on the mockup in `docs/brush-lab/brush-lab-mockup.html`
(published at https://claude.ai/artifact/8RS1E8YvpZLdfvsL88vqJV). The mockup
is the reference for layout, wording and behaviour; this spec says what the app
must do and where it differs from the mockup.

## Why

The Wild slots' dice made brushes nobody had seen before, but the brush was a
black box. Brush Lab is the back end where brushes are made, tested, tweaked,
named, saved and shared. The drawing screen stays as minimal as it is
(PHILOSOPHY.md); everything new lives in the lab.

## What a brush is now

- A brush is no longer only a seed. It is its **settings**: the Wild dials
  (`WildBrush` fields), plus a **style**, a **texture**, a **name** and a
  **seed** (the seed now only picks the brush's private randomness: bristles,
  hairs, splatter). Rolling a seed still gives the same starting settings as
  the roll tester, but after any change the settings are what count.
- Kinds stay as they are: ink brushes draw with `WildInkStroke` on the ink
  layer, paint brushes with `WashStroke` in a `WashLook` on the wash layer.
- Everything keyed by seed today (the `WashLook` cache, `WildSample`'s cache,
  wet paint joining "the same look") is keyed by the brush's settings instead,
  so two brushes with one seed but different settings never share a look.
- **Share code** (also the storage format): `name.K S DIALS.SEED`, as the
  mockup's `encode`: the name as a lowercase slug, `I` or `P`, the style's
  index, each dial as two base-36 digits over its range (a choice is one
  digit), then the seed in base 36. The real name (any text, up to 32
  characters) is stored beside the code; codes copied out carry only the slug.

## Settings

As the mockup, per kind, in this order:

- **Ink:** Nib (round, chisel, flat); Texture; Nib width (shaped nibs only);
  Nib angle (shaped nibs only); Taper in; Taper out; Pressure; Speed; Dry brush;
  Paper tooth; Wobble; Pooling; Splatter.
- **Paint:** Paint (wet, flat); Texture; Pressure; Soft edge; Granulation;
  Feathered bleed; Hollow edge (wet only); Bloom (wet only); Brush streaks;
  Backruns (wet only); Drying time.
- **Speed** is one amount slider plus a **switch**: "fast = thinner" (off) or
  "fast = thicker" (on, like the eraser). It is the sign of the existing
  `speed` dial; a brush keeps its direction when the amount is 0.
- **Texture** (new): plain, cold press, laid, felt, crumpled, canvas. A
  texture replaces the paper's fine and mid noise for that brush only (Wild ink
  tooth, dry-brush bristles and wobble; Wild paint granulation and mid noise).
  Paper tooth (ink) or Granulation (paint) says how strongly it shows; picking a
  texture with that dial under .35 sets it to .5. Plain is today's paper, so
  every existing brush, Watercolor and the golden checks are unchanged.
- Textures are CC0 height maps from ambientCG: Paper001 (cold press), Paper004
  (laid), Paper002 (felt), Paper005 (crumpled), Fabric036 (canvas), each scaled
  to a tileable 256 × 256, normalised to mean 128, spread 52, stored as raw
  8-bit files in the app's assets and credited in THIRD_PARTY_NOTICES.

## Styles

Styles guide the dice. Each is a range per setting, from the mockup's `STYLES`:

- **Pens:** Fineliner, Brush pen, Dip pen, Marker, Pencil, Charcoal, Wild.
- Never reorder either list: share codes carry a style's place in it.
- **Paints:** Watercolor, Ink wash, Gouache, Oils, Pastels, Dry brush, Wild.
- Randomize and the per-setting dice stay inside the style's ranges;
  variations stay inside them too, widened to take in a value dragged past them.
- The slider shows the range as a hatched band; dragging past it is allowed.
- The lab's Style chips offer **every style at once** (pens, then paints, then one
  **Wild**), so there's no choosing pen or paint first: picking a style of the other
  kind switches the brush to it, keeping locks both kinds share (added after
  device testing).
- **Wild is every setting there is**: a Wild roll is a pen or a paint at random
  (half each), anywhere in its kind's settings; one Wild chip covers both.
- Wild has no ranges: the roll tester's roll, plus a random texture half the time.
- Picking a style rolls a new brush in it, keeping locked settings.

## Open tool slots (added 2026-10-14)

Elling: "let's throw it open... so there are no presets that can't be replaced
in the app... there are just slots available in the toolbar for tools... so
anything can be replaced by anything."

- The bar's six drawing tools become **six tool slots**. Any slot holds any
  brush from the library: a built-in, an ink brush or a paint brush, in any
  order. The Eraser stays as it is (it isn't a brush).
- A brush's kind decides where it draws (ink on the ink layer, paint on the
  wash layer), not the slot it sits in.
- The fixed "Wild ink" and "Wild paint" tools and the ink | paint divider go
  away; there are no Wild slots, only slots.
- Each slot's button shows **its brush's sample stroke** instead of a fixed
  icon, so the bar shows what each slot draws (Elling's earlier toolbar-preview
  idea). On the bar the sample is a **swoosh**, not the loop (too thin at this
  size): one short S stroke, light to firm to light, from the bottom left to the
  top right, filling a **72 × 48 dp** slot button (Elling chose this over the
  loop and over a 48 dp square after a true-pixel comparison). Ink at about 6 px,
  paint at about .3 of the button's height. If landscape gets too tight, fall
  back to a 48 dp square with the same swoosh.
- First launch fills the slots as the bar is today: Ink, Fude, the Wild ink
  brush, Wash, Watercolor, the Wild paint brush.
- Each slot keeps its own size and shade, as tools do now.

## Built-in tools

Ink, Fude, Wash and Watercolor keep their own engines and draw exactly as they
do now, in whatever slot they sit. They are library entries like any other,
except that they can't be deleted, so they can always be put back. In the lab they show with their own sample strokes
and descriptions, their settings shown read-only as the mockup's approximations
(Ink: Fineliner; Fude: Brush pen; Wash: Gouache; Watercolor: Watercolor).
Changing anything about a built-in in the lab makes a Wild-engine copy named
"my-ink", "my-fude", and so on, starting from those approximations.

## The lab screen

A second activity, `BrushLabActivity`, opened from a **Brush Lab** button in
each slot's panel; it opens on that slot's brush and edits it for that slot. Brutalist and minimal, as the mockup: square corners,
hard black rules, no explainer text on screen.

- **Bar:** back, "BRUSH LAB", the brush's name with a pencil to rename, then
  How it works, Undo, and a prominent black **Save**.
- **Test area** fills the left column beside the settings: real strokes in the
  brush, through the same strokes the drawing screen uses, on a scratch page of
  its own with ink over wash. Clear (top left) and S, M, L (top right). The app
  icon's loop is redrawn in its top-right corner whenever the brush changes.
- **Variations** under the test area: six cards, each the loop in a variation
  and a two-line caption of at most 17 characters a line ("more splatter /
  less taper in"); tap one to switch to it; a dice button (icon only, no text) makes six new ones.
  Each variation pushes two or three unlocked settings one way, within the style.
- **Settings** column: Style chips, then a full-width **Randomize** with a large
  dice, solid black with white text like Save, then each setting with a lock (left), its control, and its own dice
  (right). Locked settings stay through Randomize, style changes and
  variations.
- **Library** under a heavy rule: every tool as a card, the loop in the brush
  and a two-line caption, description first (style, then what stands out:
  "Pencil, toothy / felt"), the name small underneath. The six slots' brushes
  first, in bar order, then the built-ins not in a slot, then saved brushes,
  newest first. Tapping a card loads it into the lab. The library is
  **paged**, not scrolled (e-ink): as many cards as fit in two rows, with
  previous and next page buttons. Long-press a saved brush to delete it (with
  a confirm in the page, not a system dialog).
- **Share code** row and **Load a code** row at the bottom; Copy uses the
  clipboard; Load reads a pasted code, and a bad code says so.
- **How it works** opens a dialog with the mockup's notes.
- **Status line** under the work area: short, factual feedback.
- **Undo** in the lab takes back the lab's own changes (switching, settings,
  randomize, rename), latest first.
- **Back** returns to the drawing with the lab's brush in the slot the lab was
  opened from, and that slot selected. Any brush can go in any slot.

## A slot's panel on the drawing screen

Stays small: sizes and shades, then the dice and save, then the four most
recently used library brushes (any kind; tapping one puts it in this slot),
then a **Brush Lab** button. The dice rolls a new brush in the slot brush's
style (for a built-in, its approximation's style) into the slot. Save adds the current brush to the library (the "full, tap one to
replace" step goes away: the library has no limit). Undo of a roll or pick
works as now.

## Names

- New brushes are named adjective-produce ("dapper-fig"). Variations keep the
  family part under a new adjective. (Ballplayer names were dropped at
  Elling's request, 2026-10-14; an older two-word name becomes
  adjective-family in its variations.)
- The lists move to plain text files, one name per line, so Elling can edit
  them: `app/src/main/assets/names/adjectives.txt` and `produce.txt`. Adjectives don't end in -y or -ish; produce is fruit or
  vegetables of one or two syllables (kiwi removed at Elling's request).
- Drawing names (`DrawingNames.next`) use the same adjective and produce lists.
- Brushes store their names, so editing the lists never renames a brush.

## Keeping what's there

- Migration on first launch: the bar's tools become the six slots (see Open
  tool slots), with each tool's size and shade kept. Each Wild slot's current seed and each saved seed
  becomes a stored brush with the settings its seed rolls, style Wild, texture
  plain and **its old name** (worked out with the old adjective and animal
  lists, kept in the code for this only).
- `WashLook.WATERCOLOR`, Ink, Fude, Wash and Watercolor draw exactly as before;
  the golden checks must not change.
- The roll tester (`docs/wild-slots/roll-tester.html`) still rolls the same
  starting settings for a seed.

## Sample strokes

Every sample except the bar's slot buttons (which use the swoosh, see Open tool
slots) — variation cards, library cards, a slot panel's recent brushes, the test
area's corner — follows the app icon's loop, Phosphor scribble-loop's centre
line, as the mockup's `LOOP` and `loopPoints`: light at the ends, firm through
the middle, slower where firm.

## Not in this version

- Copying a real stroke from a photo (wanted later, no AI).
- Breeding or mixing brushes (dropped by Elling).
- Filtering the library by style (worth adding when it grows).

## As built

- Pure model, checked on the desktop: `BrushSetting` (settings), `BrushStyle` (styles, dice), `BrushCode`
  (share codes), `BrushTexture` and `PaperGrain` (textures), `BrushNames` (name lists via `Assets`),
  `BuiltinBrush`, `Brush` (what a slot holds), `BrushCaptions`, `BrushVariations`, `BrushLibrary`,
  `ToolSlots` (replaces `WildBrushes`; migration) and `BrushLab` (the lab's state and Undo). The lab's
  views (`BrushLabActivity`, `LabSettings`, `LabLibrary`, `BrushTestPad`) only show and call them.
- A brush's identity is `WildBrush.key()`: its share code without name and style. Stored brushes are
  their share codes, so a fresh roll's dials are rounded to 1/1295 of their range when first stored.
  Migrated Wild brushes therefore draw almost, not bit for bit, exactly as their seed did before; the
  roll itself (`WildBrush.roll`) is unchanged, checked bit for bit over 40,000 rolls.
- Share codes differ from the mockup's in two places: a dial is clamped into its range before it is
  written (the mockup's round-nib codes didn't read back), and Speed at 0 is written 647 (thinner when
  fast) or 648 (thicker), keeping its direction. A code pasted in capitals reads too.
- The pen styles are in the code's order, Fineliner, Brush pen, Dip pen, Marker, Pencil, Charcoal, Wild
  (fixed above, where this spec first listed Dip pen second); share codes index this order.
- Textures: plain is the old paper tables, untouched; a scan's mid noise is the scan at 3×, bilinear,
  a 768-pixel tile. Tooth's 1.22 spread stays for plain paper only. A scan that can't be read fails
  loudly rather than drawing plain, since looks and samples are kept by settings, texture included.
- One texture rule, `WildBrush.pickTexture`: picking a texture with its strength under .35 sets the
  strength to .5, unless the strength is locked. The lab's chips, variations (the mockup raised any
  strength under .5 there) and Wild rolls (the mockup's left it) all use it; a style's roll keeps the
  strength its range gives, so it stays in the style.
- Variations leave locked settings alone even where they depend on what is pushed: the nib isn't pushed
  while Nib width is locked, the paint doesn't go flat while a locked wet-only setting is on, and a pushed
  texture doesn't raise a locked strength.
- Samples: pens at Small on tiles under 120 px, Medium on cards; paint at Small. The bar's swoosh: pens
  at Medium (ink's 8 px preset, a little over the "about 6 px" above), paint at Small. The test area's
  corner loop is 300 × 220 px, framed, pens at the S/M/L picked, paint at Small; the pen can't start a
  stroke in it. Every sample, built-in or Wild, starts its stroke in `InkWash.stroke`.
- The test area is `BrushTestPad`, not `DrawingPad` (which belongs to the drawing screen): the same
  strokes (`InkWash.stroke`), the same pen reading (`PenSample`), mode 7 presentation, a full refresh
  2 s after the pen goes away. The lab pans up for the keyboard, so Load a code stays in view.
- The panel's four tiles are the library entries saved or put in a slot most recently; on a fresh
  install, the built-ins.
- The library lists the six slots' brushes first; a slot brush that is also saved stands for that
  entry (long-press deletes the saved copy; the slot keeps its brush). A brush stored twice is listed once.
- Save on a built-in, or on a brush already saved, says it is in the library already.
- Back from a lab that changed nothing only selects the slot: no Undo step, no "recently used" stamp.
- Migration writes the slots, sizes, shades and selection before removing any old key, so a first
  launch cut short runs again whole and loses nothing.
- Name lists are edited by hand, so naming never breaks on them: a byte order mark, blank lines and a
  player without a surname are left out, an empty or missing list falls back to a short built-in one,
  and a name with no family part gets a fresh name in a variation.
- Slot buttons are 72 × 48 dp; below a 924 dp bar they fall back to 48 dp squares.
- The Brush Lab button's icon is Phosphor `flask` (Bold).
- The ambientCG textures are credited in both THIRD_PARTY_NOTICES files (the repository's and the app's).
