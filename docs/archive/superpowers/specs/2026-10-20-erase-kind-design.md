# Eraser that takes one kind of mark: design

Status: approved by the owner as a direction (2026-10-20); built on branch `inkwash-erasekind`. Builds on
`2026-10-16-erasable-design.md` (the Erasable byte this plane is modelled on).

## Open questions (first one needs the owner)

1. **Answered (the owner, 2026-10-20): a chosen kind overrides Erasable.** Pen, Paint and Dry Media erase their
   kind fully, permanent paint too (default ink included). **Everything keeps today's rule**: it never takes permanent
   paint. Limit of the one-byte bitmask: a pixel that holds the chosen kind *and another* (pencil under ink) keeps its
   permanent part as ever: Pencil takes exactly the pencil there, and Pen takes only what is erasable there (the pencil
   under the ink) and leaves the ink. Open: should Pen take the whole pixel there (ink and pencil)?
2. **The fifth choice, Screentone and Print.** Left out: four choices fit one row, five crowd it. Screentone (a medium)
   counts as Pen (a crisp ink-layer mark); Print Shading (Float's print) takes its brush's own kind.
3. **Answered (the owner, 2026-10-20): the label is "Dry Media"** (a display label; was "Pencil, Charcoal").
4. **Memory of the choice.** It is not kept between launches (as Fill's on/off), so a new session erases Everything.
   While another kind is chosen, selecting the Eraser says so in a note. **Answered (the owner, 2026-10-20): yes, the
   tile carries a mark**: two letters in a small paper patch at its lower right (Pe, Pa, Dr), none for Everything.
5. **Old drawings.** Paint made before this has no kind; it counts as every kind (a targeted eraser takes it, as
   today's eraser does). Could instead count as none (a targeted eraser leaves old paint alone).

## What the owner sees

Hold the Eraser tile (long press, the app's discover/adjust gesture). A row of buttons appears beside it, the chosen one
marked, as Replay's speeds do: **Everything** (default, today), **Pen**, **Paint**, **Dry Media**; Clear Page
joins the row when the page has marks (it was the long press's only offer). A tap picks and closes. The eraser, the
pen's eraser end and a finger set to Eraser all use the choice, with the same three sizes. Words (TEXT-STYLE): Title
Case buttons, two words at most; the note "Erasing pen" / "Erasing paint" / "Erasing dry media" is the result in two
words, shown on picking and when the Eraser is selected while not on Everything. Nothing changes for Everything.

Groups, by the Medium dial:

| Choice | Media |
|---|---|
| Pen | Ink (also Wet), Resist's ink-layer marks, Screentone |
| Paint | Wash, Opaque |
| Dry Media | Graphite, Charcoal, Crayon |

Everything keeps the Erasable rule. A chosen kind takes its marks whole, permanent or not (question 1); where a pixel
holds another kind too, the Erasable rule decides, so pencil under ink goes and the ink stays. Masking fluid and
wax are not marks of any kind: a targeted eraser neither lifts nor is shielded differently by them (fluid still
shields the paint under it).

## Mechanism

A *kind* is a bit: Pen 1, Paint 2, Pencil 4 (All = 7). Every gesture that lays paint carries its kind
(`ToneDocument.begin(erasable, kind)`, from `ResolvedBrush.kind` by Medium); the wet continuations (settle ticks,
gravity, Halo, Float's print, a Stump's wash half) begin with the stroke's kind as they do with its Erasable. MonoPaint's
own tools, Fill and the replayed old paths begin with kind 0 and tag nothing.

Per layer, like the Erasable byte:

- `Layer.kinds`: the union of kinds ever laid on it (0: nothing known, or an old layer).
- `Layer.kind`: a full-page `byte[]`, the kinds present at each pixel, **allocated only when `kinds` holds more than one
  bit** (sketch in pencil, ink over it: the ink layer). A layer of one kind has no plane: every pixel is `kinds`.
  Plane value 0 means "the layer's base kind" (`Layer.kindBase`: what the layer held before the plane existed; an old
  layer with paint is All), so allocating costs a zeroed array and undo of a tile from before it restores zeros.
- **Overlap: a bitmask, OR-ed.** The write sets the pixel to its gesture's kind when the pixel had no coverage, else
  adds the bit to what it holds (idempotent, so repeated dabs never drift). Chosen over last-writer because it is the
  same one byte and a pixel never loses a kind it holds: the eraser never reads "pen" off a pixel that has pencil
  under it. Cost of the bitmask: no amounts, so on a pixel holding two kinds a targeted eraser takes the pixel's
  erasable part as a whole (pencil under ink: the ink is permanent, so exactly the pencil, the case that matters; wash
  under pencil: both go, in proportion to their Erasable). It never touches a pixel that holds no bit of the target.
- **Erasing** (`eraseTone`, `eraseTones`): with a target, a covered pixel is taken only if its kinds hold a target bit
  (a pixel with unknown kind, 0 on a layer of kind 0, counts as All); then, if the pixel holds no kind outside the target, all of it (Erasable overridden), else the Erasable rule as today. Nothing writes
  the plane: a pixel emptied by an erase is empty, and a later write onto empty starts from its own kind, so a stale
  value under no coverage is never read. Everything (target 0) skips the test: today's code path.
- **Preview** (`compositeToneKept`): a pixel not taken shows at its full coverage, so the grey updates agree.
- **Paths:** the two sinks that write a pixel (`tones`, the block write behind `setTones`, `coverTones`,
  `smearTones`; `store`, behind `setPixel` and so every fill, stamp, span and masked write) update the plane from the
  gesture's kind. Mirror copies run the same brush through the same gesture. A brush with no paint of its own (a
  finger's Lift, Drag, Smear) lays kind 0: it adds no bit, and onto an empty pixel it leaves "base".
- **Undo:** a tile capture holds the plane beside the share and print planes (`beforeKind`, `TileEdit`); restore writes
  it back, a null capture restores zeros. `clear` leaves it (stale under no coverage); `clearAllLayers` and Clear Page
  swap in fresh layers. Reflection (Real Mirror), expanding the canvas and resizing carry it as they carry the share.
- **Native renderer:** the C code only shows the page; it lays no paint. Nothing to keep equal.

## File format (`.mpaint`)

A new tagged section in TSM5, `KIND` (0x4b494e44), after `PRNT`: per layer one byte `kinds`; if it has more than one
bit, one byte `kindBase` and the plane (`w × h` bytes). Written only when some layer has `kinds` set. An old drawing
opened and saved without new paint has none, so it is byte for byte as before (TSM3 or TSM4 where it was). A new
drawing gains a section (about 30 bytes for single-kind layers; a deflated plane for mixed ones, measured by the
checks). TSM5 readers that predate it skip the tag (the older-reader check); versions that predate TSM5 refuse a TSM5
drawing, as they already do for Erasable and print build-up. A truncated or inconsistent section is refused.

## Edge cases

- **Smudge:** paint a finger moves keeps the tags of the pixels it lands on (pencil dragged over empty paper lands as
  "base", the layer's kinds, never narrower than what the layer holds, so a targeted eraser errs toward taking it). A
  brush with its own paint (a Stump) tags what it lays as its medium.
- **Fill:** begins with kind 0 (a fill of grey is the page's, not a medium's); old paint around it untouched.
- **Layer with unknown paint and a new kind:** allocated once at the first tagged gesture (one scan of the layer's
  coverage), base All.
- **Fading ink / lemon juice:** change tones only; kinds stay.
- **No Erasing rule:** unchanged (the eraser is off).
- **Memory:** +4.9 MB (Manta page) only for a layer holding two kinds; Undo's captures grow by a third there.

## Checks (`EraseKindChecks`, registered in `scripts/test_inkwash.sh`)

Tagged by kind per medium; one-kind layers have no plane, mixed ones do; targeted erase leaves other kinds intact and
takes its own (Pen, Paint, Dry Media), Everything equals the eraser of this branch's base byte for byte, the
Erasable rule still respected (permanent ink survives a Pen erase), pencil under ink erased by Pencil leaves the ink,
wet paint (a Slow wash) and a Stump tag, undo and redo of a stroke and of an erase restore the plane, file round trip
(single-kind and mixed), old files byte-identical, an older reader skips the section, a corrupt section is refused.

## As built (2026-10-20)

- Four choices, no Screentone and Print (Screentone is Pen, a print takes its brush's kind). The long press shows the row
  (`InkWashBar.eraserMenu`), Clear Page last; the choice is `InkWash.eraseKinds`, not saved.
- Kinds by Medium (`ResolvedBrush.kind`): Ink, Resist, Screentone Pen; Wash, Opaque Paint; Graphite, Charcoal, Crayon Pencil;
  0 for a brush with no paint of its own. Masking fluid and wax are no kind; a targeted eraser neither lifts nor erases them.
- A pixel is saved with the layer only while it has coverage (`Layer.kinded`), so a layer erased to paper writes nothing.
- File: the section costs 11 bytes on a one-kind page and about 1.1 KB on a 480 x 360 pencil-and-ink page (a plane
  deflates to a few percent of the layer's tones; the Manta's 1920 x 2560 page scales it to about 40 to 60 KB); the white
  test page's file crc in `PageShadeChecks` changed for the section.
- `EraseKindChecks`: 56 checks; `OlderKindCheck` for the older reader (the base `e288fe9`, which skips the tag).
- The Eraser tile's mark (`InkWashBar.drawEraserMark`, `EraseKind.MARKS`), redrawn through the fast path on a pick.
- Not done: Brush Lab's test pad erases Everything.

## Ghost fix (2026-10-20, branch `inkwash-ghostfix`)

The owner's report: after erasing, a ghost stayed on page 34 of "Loose pages" that neither Everything nor Dry Media took.
It was charcoal's permanent tenth (Erasable .9, 4,230 pixels at coverage 1 to 22 of 255, black, share 0) on pixels that
also held Pen, from an erasable pen mark laid over the charcoal and since erased. The plane is a union no erase narrows,
so Dry Media saw "Pencil and Pen" and fell back to the Erasable rule, which keeps a share-0 pixel: nothing could take it.

- **Fix:** a plane byte's bits 3 to 5 (`ToneDocument.PERMANENT`) note which of its kinds laid paint below Erasable 1
  there (`Layer.permanentAt`; a base pixel's from its share as found, unknown otherwise counts as all its kinds). A chosen
  kind takes a pixel whole when no *other* kind laid permanent paint there (was: when it holds no other kind). So the
  ghost goes with Dry Media and stays under Pen; ink over a clean-erasing pencil goes whole with Pen (question 1, for that
  case: yes); charcoal at .9 under permanent ink keeps its permanent part as before. Older readers mask the plane to its
  kinds. Plane bytes written before the fix have no permanence bits: a chosen kind takes such a mixed pixel whole.
- **Charcoal's default Erasable is 1** (the owner): it erases clean, like graphite. The Charcoal and Pastels tools'
  codes drop their `00we` (back to their codes before Erasable; `ToolPresetsChecks` wants a tool at its medium's real
  value), as does the Charcoal slot's first brush. A code that says .9 (a brush made before, a slot holding a tool as
  it came) keeps .9 and its words now say "Part erasable"; no slot is rewritten.
- `EraseKindChecks.ghost`: charcoal at .9 crossed by an all-erasable pen line, Everything, then Pen leaves the ghost and
  Dry Media takes it to paper.

## Everything takes everything (the owner, 2026-10-22, branch `erase-template`)

"Pen isn't erased by eraser (everything)"; "i was toying with a restriction that everything behave like physical media but i realize that isnt what i want". Question 1's "Everything keeps today's rule" is reversed: Everything takes every mark whole,
permanent paint too, wherever it is the choice (eraser, pen's eraser end, finger). It is the eraser limited to every kind
(`KIND_ALL`), so a pixel is always taken whole; masking fluid (lifted first, its wax kept, the paint under it shielded),
Tone's scrape and the cover's design rub stay Everything's own. The targeted kinds are unchanged. The ghost of
2026-10-20 can no longer be made by Everything (only by Pen over charcoal at .9); `EraseKindChecks.ghost` makes it with the
Erasable rule, as old pages hold it, and Everything takes it. The words are unchanged ("Everything", "Erasing everything").

## Kind layers (2026-10-27, branch `kind-layers`)

New paint no longer shares a pixel with other kinds: each kind lays on a plane of its own
(`docs/superpowers/specs/2026-10-27-kind-layers-design.md`), so a chosen kind takes exactly its own. The bitmask above stays for paint laid
before (a layer's base), with these rules.
