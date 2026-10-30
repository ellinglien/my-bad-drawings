# Tool kits: design

Status: approved by Elling, built on branch `tool-kits` (from `inkwash-v2-lab` 5938755), 2026-10-22. Not merged.

## The ask

The owner, 2026-10-22:

> "the workflow for sumi and the marbling stuff.... i wonder if that could be clarified somehow... like, you have to
> have a certain set of tools ready .. maybe sumi tools load several tools in your toolbar or something by default"

Proposal: picking Sumi (or another marbling brush) on the Tools page offers "Load the marbling kit", which fills
several slots at once with what the workflow needs, each slot saying its job in order; one Undo puts the bar back.
Later kits for other ways of working. The owner: **"yes to kits ! nice idea"**.

## What a kit is

A **kit** is a name, one intro sentence (a whole human sentence, no semicolons, the brush words' rules), and an ordered
list of Basic tools, each with its job in a bar's words (three at most): "Drop ink". On screen a job is numbered:
"1 Drop ink". A kit may end with an eraser step that sets the eraser's kind as it loads.

**Where kits live: a text asset**, `app/src/main/assets/brushes/kits.txt`, beside `personalities.txt`, so the owner
can add or change a kit without code:

```
Kit: Marbling
Intro: Drop ink and clear water in turn to grow rings, then comb them before they print.
Sumi: Drop ink
Clear water: Drop water
Comb: Comb rings
```

`Eraser Dry Media: Erase pencil` as a last line also sets the eraser to Dry Media (Everything, Pen, Paint, Dry Media).
`ToolKits` reads it (pure Java); `ToolKitsChecks` holds every kit to the rules: each brush a Basic tool on the Tools
page (not a retired one), one to eight brushes, no Stamp (This Brush's header has room for Cut Stamp or Load Kit),
the intro one readable sentence, the jobs a bar's words.

## The kits

| Kit | Steps (job, Basic tool) | Why this order |
|---|---|---|
| **Marbling** | 1 Drop ink (Sumi), 2 Drop water (Clear water), 3 Comb rings (Comb) | The bath starts with the first Float stroke that lays paint: Clear water and Comb only move what floats. Alternating 1 and 2 grows rings; the comb comes after; the bath prints on its floating time, a tap on the drop, or any other brush. |
| **Sketch and Ink** | 1 Sketch (Sketch pencil), 2 Ink over (Steady line), 3 Erase pencil (Eraser, Dry Media) | Dry Media takes pencil, charcoal and crayon only, so the ink stays. |
| **Watercolour with Masking** | 1 Mask whites (Masking fluid), 2 Wash over (Soft wash), 3 Blot to lift (Damp tissue), 4 Rub off mask (Eraser, Everything) | The mask must be down before the wash; the eraser's first pass over fluid lifts the fluid and leaves the paint. |

Marbling needs only Float brushes: anything else prints the bath, so a kit mixing in a dry brush would break the flow.
Wash comb is not in it (it combs a wet watercolour wash, not floating ink).

## The Tools page

- **Load Kit** in This Brush's header (where Cut Stamp is for a Stamp) while the brush shown is a kit's Basic tool as
  it comes (`ToolKits.of`; a changed copy isn't). Said "Load the Marbling kit".
- **Kits**, a fifth library tab after All (Basic tools, Recent, Saved, All, Kits). A tab, not a chip: kits are a source
  of cards, not a style, and the chips' rows were full (a Kits chip took the Nomad's portrait grid to one row). A card
  a kit: its first brush's stroke, its name, "3 brushes". No chips under it. The tabs fit every header that holds
  them; on the Nomad in landscape they have their own row, as before.
- Either opens the kit's **sheet** over the page: "Marbling Kit", the intro, the steps (job, then the Basic tool's
  name in grey), where it goes ("Fills the first 3 slots"), **Cancel** and **Load Kit**. A tap outside closes it; the
  test area stops taking the pen while it is open.
- **Where it goes: the first slots.** The kit's brushes go in slots 1 to N in order, the first selected. Chosen because
  Brushes on Bar keeps the bar to its *first* slots (so a kit there is always seen), and the numbers then read along the
  bar from the top. Slots after it keep their brushes.
- **Brushes on Bar.** The page is told the bar's count and whether the notebook is locked. A kit bigger than the count
  raises it to the kit's size (the sheet says "Fills and shows the first 3 slots"); a locked notebook can't, and the
  sheet says "Bar holds 2 brushes" in Load Kit's place.
- **Fixed toolbars** (Each Page, a locked notebook's own): the sheet shows the page's note ("Notebook locked" and the
  like) in Load Kit's place, the same note as a single pick; nothing loads.
- **Undo.** One Tools page Undo takes the whole kit back: every slot it filled, the slot selected before, the bar's
  count and the eraser's kind. Back on the drawing screen the kit's slots are **one** Undo step there too
  (`ToolSlots.useAll`); that Undo puts the slots back but not the bar's count or the eraser's kind (both are one tap
  away and harmless).

## The drawing screen

- A slot picked says its **step** for 1.5 s in its name's place ("3 Comb rings") while that kit's brushes are in the
  first slots in order (`ToolKits.said`, worked out from the bar, nothing stored); otherwise its name as before.
- Marbling's order, said when broken (the rule 7 notes): Clear water or Comb with nothing floating, **"No ink
  floating"**; another brush (or the eraser) while rings float, **"Rings printed"** (it always printed them; Clear
  Water's smudge, which works on the water, says nothing).
- Sumi's, Clear water's and Comb's own words (`personalities.txt`) say the order: "Japanese marbling, and the first
  drop.", "Clear drops between drops of sumi.", "A comb for floating sumi, after the drops."

## Where it lives

- `ToolKits` (pure): the file, `of`, `said`, `floatNote`. `ToolsPage`: the Kits tab, `loadKit`, the kit `Step` (several
  slots, the bar's count, the eraser's kind), `refuses`, `raises`, `together`. `ToolsLayout`: the sheet's sizes,
  `kitWhere`, `brushHeader`. `ToolsActivity`: Load Kit, the sheet, the extras `BAR`, `BAR_LOCKED` in and `BAR`,
  `ERASER`, `TOGETHER` back. `ToolSlots.useAll` and `undone`. `PaintActivity`: the extras, the grouped Undo, the
  marbling notes in `allowStroke`. `InkWashBar.said`, `eraserChanged`.
- Checks: `ToolKitsChecks` (the file's kits valid, parsing, the Kits tab, load and Undo round trips, the bar's count
  raised or refused, a frozen toolbar taking nothing, the drawing screen's one-step Undo past the Undo limit, the step
  said, the notes, the sheet and the header on both tablets in either turn); `ToolsPageChecks` skips Kits where it
  checks the chips.

## Device checks for Elling

Pick Sumi on the Tools page, Load Kit, Back: slots 1 to 3 hold Sumi, Clear water, Comb; tapping each says its step.
Drop ink and water in turn, comb, tap the drop. Undo once on the drawing screen: the three slots come back. In a
notebook with Brushes on Bar 1, load a kit: the bar shows 3. In an Each Page notebook: the sheet says why it won't.
