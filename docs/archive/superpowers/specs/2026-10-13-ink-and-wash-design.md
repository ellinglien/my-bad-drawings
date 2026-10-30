# Inkwash

**Date:** 2026-10-13
**Status:** design approved, not yet implemented

## Problem

Elling draws on a Supernote Manta. MonoPaint has the fast e-ink pen path he
wants, but its tools are too "painty": watercolor that runs, oil that smudges,
textured pencil, 65 shades. The built-in Notes tools come closer (the ink pen's
steady line, the highlighter's flat wash) but the highlighter has no pressure
or tilt. He wants a minimal pen-and-wash sketchbook in the spirit of Tayasui
Sketches: crisp black ink and a grey wash, flat or with some watercolor tone,
and nothing that keeps moving after the pen lifts.

## Principle

See `PHILOSOPHY.md` at the repo root; this is its summary.

Few, clear tools. Limits help Elling stay creative and avoid decision
fatigue, so every tool has one obvious behavior, there is no hidden mode, and
anything that would need a setting is either fixed or left out.

## Goals

- **Ink:** solid black, steady width, subtle pressure response.
- **Wash:** flat grey, width from pressure and tilt, crisp edge, always
  under the ink.
- **Watercolor:** the same, but tone varies; it stays wet for a few seconds,
  when the pen can push it, then sets.
- Few decisions while drawing: three size presets per tool, no settings
  panels, drawings named automatically and saved as you go.
- Each drawing reaches the owner's cloud sync as a PNG.

## Non-goals (first version)

Zoom, landscape or left-handed layouts, multiple pages per drawing, fill,
pencil, paper texture or granulation, color, user-visible layers (since
added, see "Layers"), custom
tools, paint that runs or spreads after the pen lifts.

## Research

Simple drawing apps Elling likes or that are known for simplicity:

- **Tayasui Sketches:** watercolor as two tools, dry and wet, not a
  settings page; pressure, angle and width adapt automatically; tools hide
  while drawing.
- **Paper (FiftyThree):** washes multiply when layered, building up tone;
  a roughly one-second wet window lets you lift and continue the same wash
  without a darker blotch where strokes overlap.
- **Zen Brush 2:** a size slider and three tools; ink tone and dispersion as
  three presets each.
- **Linea Sketch:** three sizes per tool; the pen's opaque-or-additive
  toggle; erasing with a finger at any time is its most praised feature.
- **Watercolor look:** edge darkening (pigment pooling at the boundary of a
  wash) is the main cue, and can be produced from the stroke outline alone,
  with no fluid simulation.

These shaped the separate Wash and Watercolor tools, the wet window, edge darkening,
size presets and finger erasing below.

## Approach

A fork of MonoPaint (MIT), slimmed down. MonoPaint's engine (direct e-ink
presentation, the pen bridge with pressure and tilt, layers and undo,
autosave and recovery, PNG export, device detection) is kept as is. Its tools
and panels are replaced with two new strokes and a small toolbar.

Rejected:
- A new app borrowing only MonoPaint's display and pen code. Smaller, but it
  rebuilds saving, undo and refresh handling that MonoPaint has already
  debugged on this hardware.
- Contributing an "ink and wash" mode upstream. No fork to maintain, but the
  timing depends on the upstream developer. Keeping Ink and Wash as
  self-contained strokes leaves this open later.

## Project

- Repository cloned from
  `github.com/mpdairy/monopaint` with that repo as the `upstream` remote, so
  upstream e-ink fixes can be merged.
- App name **Inkwash**, application id `app.helpyy.inkwash` (changed to `ing.ell.mybaddrawings` on 2026-10-30), folders
  `Document/Inkwash` and `EXPORT/Inkwash`. It installs alongside MonoPaint.
- Launcher icon: Phosphor `scribble-loop` (Bold), black on white, as an
  adaptive icon with a PNG fallback (see `docs/BRANDING.md`); MonoPaint's
  brush logo is gone.
- Its own GitHub repo (`origin`); pushes to `upstream` are disabled.
- MonoPaint's license and third-party notices (AnimInk) stay in the repo and
  the APK.
- Built with MonoPaint's Gradle build (JDK 21, SDK 34, NDK 27.0.12077973,
  CMake 3.22.1; NDK and CMake are a one-time install on the Mac). Signed with
  the owner's local release key.

## The app

**Screen.** Portrait only. A full white page and a slim toolbar: Ink, Wash,
Watercolor (a droplet icon), Eraser, three size presets (S, M, L) for the selected tool,
each shown as a black dot as wide as that tool draws (its upper limit for wash, watercolor and eraser; a size too
big for its button shows as the largest dot that fits, labelled in millimetres),
Undo, Redo, Menu. No palette, layers panel or settings
popups. The selected tool and preset are remembered between launches.

**Ink.** Solid black on the ink layer. Width is the preset size, varied by
pressure (and, since toolbar v2, lean; see below). No texture.

**Toolbar v2 (from Elling's sketch, after device testing).** The bar stays
across the top. Left to right: Menu, Undo, Redo; the tools (four, five since the
Fude pen), all visible; layers (below) at the right. The first tap on a
tool only selects it. Tapping the selected tool pops a
small panel straight down beneath it: three size dots, then (for tools that
have them) three shade swatches, stacked vertically as in the sketch;
tapping it again while the panel is open closes it (panels open on the
second tap, after device testing). The
panel also closes when a size or shade is picked, when drawing starts, on a
tap elsewhere (another bar button too) or on Back. Sizes and shades never take space on the bar itself; the
tool buttons show only their icons, the selected one inverted (black, its
icon white). Inside a panel the current size and shade share one selected
look: a square-cornered black frame, 3 dp, the same as a selected layer
thumbnail's (after device testing; the size dots had a rounded outline and
the swatches a double one). An open panel joins the button it belongs to like a folder's
tab: one outline, the panel's stroke wide, runs around the button's sides
and top and on down around the panel, with no line between them. The layer
panels (see "Layers") look and close the same way.
- Ink responds more: width varies about ±25% around the preset with
  pressure, and leaning the pen thickens it a little (a light touch stays
  thinnest).
- Ink behaves like a fountain pen: the nib is a slightly flattened oval
  that turns with the direction the pen leans, so strokes across the nib
  come out thicker than strokes along it. The effect is VERY subtle. The
  natural writing angle (about 45° from upright) is the neutral, nearly
  round nib; only leaning further than that flattens it, gradually.
  Leaning further also drives the slight lean thickening above, measured
  from the same 45° neutral. Built on MonoPaint's existing tilt-
  following brush heads (Filbert/Flat with the head following tilt).
  After testing, a little more slant: fully leaning flattens the nib to
  0.6 as wide as long (was 0.75) and thickens a firm line up to 20%.
- Speed, like physical media: a fast ink stroke comes out a little
  thinner (a nib running dry); a fast brush stroke (wash, watercolor)
  thins slightly, and a fast watercolor stroke lays a little less
  pigment; the eraser is the practical exception: slow is small and
  accurate, fast is big. This holds
  for the finger too: its contact size sets how big a fast swipe gets, and a
  slow finger narrows to a fine point. Speed is
  smoothed over about 0.1 s and the width can only change gradually, so
  corners never pinch or bulge.
- The eraser shows a thin grey outline of its current size while the pen
  hovers and while erasing (at the fingertip for a finger). Hovering has no
  pressure or speed yet, so there it shows the preset's full size, as the
  size dot does; erasing, it follows the live size, speed included.
- Sizes: three per tool: smallest, middle, largest. Ink 3 / 8 / 16 px; the
  wash, watercolor and eraser limits 24 / 120 / 220 px (the middle is the
  owner's favourite, about 10 mm).
- Shades: three per tool (see "Shades, three per tool" below); the eraser has none. Every grey is lighter than the first version.

**Shades, three per tool (after device testing).** Every tool with shades
offers three: Ink black / grey / light grey; Wash dark / light / lighter;
Watercolor deep / pale / paler. The third is an even lighter option. The
eraser has none.

**Fude pen (fifth tool, after device testing).** Like a Sailor Fude de
Mannen, whose nib is bent up at the tip: the angle the pen is held at sets
the line. The natural ~45° hold gives a medium line; holding it steeper
writes with the tip (fine); laying it lower brings the bent face down
(broad, about 40x the fine width; the owner asked for lots of variety
with the tilt). Stroke direction adds a little
calligraphic variation (the face is flat), and pressure adds a little on
top. Black ink on the ink layer, with the same three sizes and shades as
Ink. Ink keeps its steady, subtle fountain-pen behaviour. As built (made
more extreme twice after testing): 0.12x
the preset from 30° from upright and steeper, the preset at 45° (and
without a tilt reading), broadening steadily to 4.8x the preset (40x the
fine width) at 60° and lower; pressure ±15%; the nib turns with the lean
like Ink's and flattens past 45° to 0.2 as wide as long at 60°; speed
thins it like Ink. The head never goes finer than 1 px (the small preset's
fine line is 1 px) and its widest, 17 / 44 / 88 px for S / M / L, stays
within the brush's 128 px size limit.

**Wash and Watercolor, common behavior.** Both draw on the wash layer. Width runs from a fine brush tip (2 px) to
the preset's maximum (24, 120, 220 px) with pressure and tilt, reusing MonoPaint's
tilt code. Crisp edge. Ink is never covered by wash, whatever the drawing
order.

**Wash.** One flat grey per shade: dark 205 (about 20%), light 230 (about 10%), lighter 245 (about 4%). Overlapping washes, including a
stroke crossing itself, stay the same grey.

**Watercolor.** Tone varies. Each stroke settles when the pen lifts, and
the wash can still be pushed while wet (below); nothing moves on its own.
While the pen is down the stroke shows as a plain preview, a step darker
than whatever paint it crosses, wet or dry, so it always shows.
- *Pressure sets the tone:* a light touch lays a pale wash, pressing harder
  a darker one.
- *Stays wet for 2 seconds:* each wash stays wet for about 2 seconds
  after the pen last lifted from it, each with its own timer (10 seconds
  in the first version, then 5; the owner found both too long). A stroke that
  touches wet paint joins it: overlaps do not darken, and the edges re-form
  around the joined shape.
- *Push and blend:* a stroke that starts in (or passes through) wet paint
  picks the pigment up and drags it along, fading over a short distance.
  Where it crosses wet paint the two tones blend. Paint only moves when the
  pen moves it; nothing runs on its own.
- *What dries it:* the timer, or any ink, eraser or undo/redo, which sets all
  wet paint at once. A finger that erases nothing (cancelled by a second
  finger, the pen or a palm, or over blank paper) leaves it wet. Each stroke
  is its own undo step.
- *Layering darkens:* a new wash over a settled one multiplies with it,
  building up tone.
- *Edge darkening:* when a wash settles, a band along its outline is drawn
  darker than its middle: gently, crisper for a firm stroke and softer for a
  light one.

*Wet-on-wet blending (added after device testing).* Strokes in a wet wash
must melt into one wash, not show as separate brushstrokes. When a stroke
settles into wet paint, the pigment levels across the joined wet area are
blurred strongly (diffusion at lift, never animated), so tones even out and
inner seams vanish; only the wash's outer edge keeps its pooled line. A
stroke joins wet paint it lands near (within a few pixels), not only paint
it touches. Paint that has dried still glazes with visible edges.

**Auto-refresh (added after device testing).** Fast partial updates leave
ghosts on the panel. Instead of a refresh button, the app does one full,
clean panel refresh at natural pauses, and only if something changed since
the last one: when the pen has left the screen (no hover) for about 2
seconds after drawing; when a menu or the tool panel closes; after an undo or
redo of a large area; and when fast display reconnects (after a dialog, or
the app resuming) with a refresh still pending. A refresh button is added
only if ghosts still build up.

*Pen grooves (added after device testing).* Like a pen scoring paper,
ink lines act as shallow grooves for watercolor: when a watercolor wash
settles over an ink line on the same layer, a little extra pigment pools
right along the line, a soft dark halo stronger where the ink was pressed
harder. A broad line scores a deeper, wider groove: beside a fine line the
halo is a few pixels; beside a line 12 px wide or more it is up to about 2.5
times as dark and fades out over about 12 px (added after device testing:
"thicker = deeper groove"). The flat wash is unaffected. The groove belongs
to the ink: erasing the ink removes it too (no ghost lines).

**Grey on the panel.** MonoPaint's fast path presents only black and white
pixels, so washes show as its existing even dot screen (as Atelier does),
with tone as dot density. The exported PNG uses true smooth greys.

**Layer pairs.** Within each layer, wash sits below ink. Each tool picks its half of the selected layer.

**Layers (added after device testing).** Each layer is its own pair: ink
over its own wash, so a layer above holds both, over everything below. Up
to four layers (the engine's eight, in pairs: layer k is wash 2k and ink
2k+1).
- Two buttons add a layer above or below the current one (Phosphor
  `rows-plus-top`, `rows-plus-bottom`), and select it; both are greyed out
  at four layers.
- Each layer shows as a small thumbnail in the bar, the topmost leftmost:
  its own wash and ink, sampled small in the page's dots, drawn again a
  moment after the page changes, never during a stroke. The selected one
  has a thick black frame (an inverted picture would read as different
  paint); a hidden one is lighter and struck through.
- The first tap on a thumbnail only selects that layer. Tapping the
  selected thumbnail pops its panel straight down beneath it, exactly like
  a tool's panel (same tab look; tapping it again closes it; it also closes
  after an action, on drawing, on a tap elsewhere, on Back, on pause or
  rotation). Stacked vertically: show
  or hide (Phosphor `eye` / `eye-slash`), move higher (`arrow-up`), move
  lower (`arrow-down`), delete (`trash`; no confirmation, undo restores
  it). No long-press and no separate layers screen.
- All tools draw on the selected layer; the eraser clears only it (while
  erasing, the screen shows the layers beneath and above where it went).
  Switching layers dries wet watercolor. Drawing on a hidden layer shows
  it again rather than refusing.
- Each layer action is one undo step. Undoing a stroke selects the layer it
  was on, as MonoPaint does.
- Drawings save the layers and the selected one; the PNG has the visible
  ones. A first-version drawing (two layers) is one layer; a one-layer
  MonoPaint drawing draws everything on its layer until its first layer
  action pairs it with a blank ink.
- A new drawing starts with one layer.

**Erasing.**
- The Eraser tool, the pen's eraser end or side button (as MonoPaint already
  supports), and a single finger on the page all erase.
- They erase ink and wash together: whatever is under the eraser goes. No
  layer choice, no label. Undo covers a wash erased by mistake. Ink and
  wash both disappear from the screen as the eraser moves; the page erases
  the ink then and the wash when it lifts (one engine gesture covers one
  layer), and both are one undo step.
- A finger starts erasing only once it moves, so a quick two- or
  three-finger tap still undoes or redoes; a second finger or the pen
  cancels it and restores what it erased.
- Width follows the preset. A finger erases about as wide as it touches,
  fixed when it starts to move (12 to 90 px), so a capacitive stylus tip
  erases finely and a fingertip broadly, with no setting.
- Finger erasing is ignored while the pen is near the screen, using
  MonoPaint's existing pen-hover guard, so a resting palm does not erase.
  A capacitive stylus tip counts as a finger.
- MonoPaint's one-finger swipe to hide the toolbars is removed; it would
  conflict with finger erasing.

**Undo and redo.** Toolbar buttons, plus MonoPaint's two-finger tap to undo
and three-finger tap to redo.

**Drawings.** One page each. A new drawing is named at once as
`YYYY-MM-DD adjective-noun`, deliberately silly (e.g. `2026-10-13 wobbly-puffin`) and saves as you
draw; there is no name prompt. The menu has New, Open (a list of drawings),
Rename and Delete.
As built (2026-10-18, the owner's "date + descriptors + random word"): before its first
save (the autosave a minute in, or leaving it) a new drawing's name trades its adjective for the
styles it was drawn with most, once: `2026-10-18 ink-wash fig`, `2026-10-18 charcoal-hatch
ink-wash fig` (a style's label, Anything goes' medium, "stipple" or "hatch" after it; a second
style only if it drew at least a third as many strokes). Nothing is on disk under the old name
then but perhaps its PNG copy, which follows; a name the owner gave (Rename, Save as) or one
already saved is never changed (`DrawingNames.described`, `PaintActivity.describeName`).

## Code changes

Kept as is unless a change is needed: `DrawingPad`'s presentation and
gesture code, `DirectEink`/`NativePen` and `src/main/cpp`, `ToneDocument`,
`DrawingBook`, `DocumentStore` and its codecs, `PngExport`, `Device`.

Added:
- `InkStroke` and `WashStroke`, `DrawingStroke`s created through
  `ToolStrokes.create` in the documented way. Each writes only to its layer.
  `WashStroke` serves both Wash and Watercolor; the watercolor settle
  step (tone, wet-window merge, multiply, edge darkening) is a pure function
  of the stroke's coverage and pressure and the layer beneath, so it can be
  unit-tested off the device.
- A two-layer document setup (wash, ink) used for every new drawing: one
  layer pair (see "Layers").
- A small toolbar replacing `Toolbar`, `ToolSettingsPanel`,
  `PaletteEditor`, `LayersPanel` and the color bar.
- Finger erasing in `DrawingPad`'s touch handling, alongside the existing
  two- and three-finger taps.
- A namer producing `YYYY-MM-DD adjective-noun`, with a suffix if the name
  is taken.

Removed: the other tools and their settings UI, and the swipe-to-hide bars.
The first working version only hides MonoPaint's header, color bar and side
toolbar (they are still built, so nothing that refers to them breaks); a
final cleanup step deletes them once the app works. Engine files are left
alone where possible, to keep upstream merges manageable.

## Saving and export

- Drawings are MonoPaint `.mpaint` files in `Document/Inkwash/`, saved
  continuously by MonoPaint's autosave. The recovery file stays in private
  storage, as upstream does, to avoid Drive sync conflicts.
- A PNG copy goes to `EXPORT/Inkwash/<name>.png` at the page's full
  resolution (the drawing area below the toolbar): black
  ink, the wash in true greys, white paper. It is written when the drawing is
  left (switching drawings, app paused, screen off), not after every stroke,
  so Drive never sees a file rewritten every few seconds.
- The Manta syncs `EXPORT/` to the cloud, so drawings reach the owner's
  notes with no extra work.
- Rename renames both files. Delete removes both; a synced copy stays.

## Failure handling

- A failed PNG write never interrupts drawing. It is logged and retried the
  next time the drawing is left.
- Autosave and crash recovery are MonoPaint's existing code.

## Testing

Unit tests, in MonoPaint's existing test setup:
- Ink width stays within about ±25% of the preset, plus up to 20% leaning;
  a fast stroke thins it further, to about 0.6× the preset at its lightest.
- Wash width follows pressure and tilt and stays within the preset's range.
- Each drawing tool writes only to its own layer; erasing clears both.
- Wash: overlapping and self-crossing washes produce one grey.
- Watercolor: lighter pressure gives a paler tone; strokes touching wet
  paint join without darkening; a stroke starting in wet paint carries its
  pigment beyond it; several washes stay wet independently; ink, eraser or
  undo dries everything; a later wash over a settled one darkens it; the
  outline band is darker than the middle; nothing changes after settling.
- Namer format and collision suffix.
- The exported PNG contains only black, white and wash greys.

On the Manta (Elling): ink keeps up with the pen; the dotted greys look
right; tilt and pressure feel right for the wash; watercolor settling is
quick and looks like watercolor; finger erasing works and a resting palm
does not erase; a drawing reaches the cloud.

## Sources

- Tayasui Sketches: https://apps.apple.com/us/app/tayasui-sketches/id641900855
- Paper watercolor update: https://mademistakes.com/mastering-paper/watercolor-brush-update/
- Zen Brush 2: https://apps.apple.com/app/id1012274888
- Linea: https://www.macstories.net/reviews/linea-an-elegant-sketching-app-for-the-ipad-from-the-iconfactory/
- Edge darkening: https://patents.google.com/patent/US6198489

## Wild slots (designed and built 2026-10-13)

_Updated 2026-10-13: built; see "What was built" at the end of this section._
_Superseded 2026-10-14 by Brush Lab and open tool slots (`2026-10-14-brush-lab-design.md`): the bar's six
drawing tools are slots that hold any brush; a brush is its settings (style, texture, name, seed), not a
seed; the four saved seeds per slot became an unlimited library whose four most recently used brushes a
slot's panel offers; the replace-when-full step is gone. The roll tester still rolls the same starting
settings for a seed, and old Wild brushes kept their names._

Two extra tools whose character is rolled at random, so the five fixed
tools stay exactly as they are and variety lives in its own place.

- **Wild ink** sits after Fude and draws on the ink layer. **Wild paint**
  sits after Watercolor and draws on the wash layer. Bar order:
  `Ink · Fude · Wild ink | Wash · Watercolor · Wild paint | Eraser`.
- They behave like the other tools: the first tap selects, the second
  opens the panel, and each has the same 3 sizes and 3 shades.
- **Panel:** sizes and shades, then a current-brush row (sample stroke,
  silly name, dice, save), then up to **4 saved brushes** shown as sample
  strokes. Tap one to use it. When all four are full, save asks you to tap
  the one to replace.
- **Nothing changes until the dice is tapped** (the owner chose this over
  a daily brush, as closer to "nothing moves on its own"). Strokes already
  drawn never change.
- **A brush is a seed.** The seed decides every dial and the name, so a
  saved brush is one number. The current and saved seeds persist.
- **Wild ink dials:**
  - nib (round, chisel, flat), with its aspect and angle;
  - taper in and taper out;
  - pressure range;
  - speed effect (fast = bigger or thinner);
  - dry-brush streaking, which runs out with distance and speed, while
    pressing pushes ink out;
  - paper tooth (ragged edges);
  - wobble.

  Each roughness dial is off about half the time, and dry, tooth and
  wobble share a budget so a roll stays tasteful.
- **Wild paint dials:**
  - flat wash or wet watercolor;
  - pressure range (tip to limit);
  - soft edge;
  - granulation;
  - feathered bleed;
  - hollow edge and bloom, which apply to wet paint only;
  - drying time (added after device testing): how long the paint stays
    wet after the pen lifts, 0.75 to 5 s, most brushes near Watercolor's
    2 s. Drawn last, so earlier seeds keep the rest of their character.
- The ranges were tuned on a desktop roll tester, kept in the repo as
  `docs/wild-slots/roll-tester.html`; a seed rolls the same brush there
  and in the app.

**Approved from the mockup (2026-10-13):**
- Icons: Phosphor `sparkle` (Bold) for Wild ink and `sparkle` (Fill) for Wild paint.
- The panel is one button wide, top to bottom:
  - the 3 size dots;
  - a rule and the 3 shades;
  - a rule, the dice (`dice-five`) and save (`bookmark-simple`, filled when the brush is already saved);
  - a rule and the 4 saved tiles, each the brush's sample stroke, with the brush in use framed.
- The panel stays open while rolling.
- The brush's name shows as a small label under the bar for two seconds after a roll or a pick. "Tap a brush to replace" stays up while replace mode is on.
- **Undo a roll:** Undo, right after a roll or a pick (before any stroke), brings the previous brush back.

**What was built (2026-10-13):**
- Bar order as designed: `Ink · Fude · Wild ink | Wash · Watercolor · Wild paint | Eraser`.
  Both wild tools share one panel (dice, save, four saved tiles, name label,
  undo a roll), driven by the tool's slot.
- **Wild ink** (`WildInkStroke`) is a port of the tester's ink. Its nib keeps a
  fixed italic angle: tilt doesn't turn it (a deviation from the tester's
  original note).
- **Wild paint** is Watercolor's engine in the brush's look (`WashLook`).
  Watercolor's own look is the old constants, so its pixels didn't change.
  Wild paint follows the tester's dials with these differences:
  - Wet paint joins only wet paint of the same brush. Another brush, a roll
    or Watercolor dries it first, as a shade change does.
  - Wet wild paint keeps Watercolor's edge band and joins with its
    diffusion, so seams vanish. Flat or streaky paint doesn't diffuse, and
    a lone stroke never does, as in the tester.
  - The hollow edge is the tester's rim (the paint minus its blur), not a
    widened edge band. A soft edge fades the band's few pixels, so the
    band can't carry it.
  - Streaks are laid once, on lift, from the nearest point of the stroke's
    whole path. Laying hairs dab by dab, as the tester does, made rays round
    curves at the app's dab spacing.
  - Flat paint has no blooms, as in the tester. Wet paint blooms at least as
    much as Watercolor.
  - The darkest shade's firmest paint is .34 dark (the tester's), with
    Watercolor's three shade steps.
- **Samples:** paint tiles are drawn through the real stroke at S, on a
  longer and flatter S curve than ink's.
