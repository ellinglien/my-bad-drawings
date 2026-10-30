# Procreate export: the iPad test (2026-10-20)

Brush Studio's Export makes `Inkwash.brushset`: This brush plus every brush Saved in the library. Nothing here has
been opened in Procreate yet; this test is the first time. Spec: `docs/superpowers/specs/2026-10-20-brush-export-design.md`.

## Make a set

1. Open Brush Studio. Save four or five brushes that differ (Load, Basic tools, then Save each, or roll some): say a
   Round ink with Taper In and Out, a Chisel or Flat Nib, a Wash on Cold Press, a Charcoal on Canvas, a Stipple or a
   Stamp. Leave one more on screen as This brush.
2. Share, then **Export**. `Inkwash.brushset` downloads (a few hundred KB a brush).

## Get it into Procreate

- **From the Mac:** AirDrop `Inkwash.brushset` to the iPad, then choose Procreate. Or put it in iCloud Drive, open
  Files on the iPad and tap it.
- **On the iPad:** open Studio in Safari, Export, then Downloads (the arrow in Safari's bar), tap the file, Share,
  Procreate.
- Procreate adds a set called **Inkwash** at the top of the brush library. If it says the file can't be imported,
  note the exact words.

## What to look at

For each brush, on a blank canvas, black first, then a colour:

1. **It imports, with its name**, and isn't blank. A brush that draws nothing is the worst case: note which.
2. **The preview in the library** shows a wave stroke.
3. **The dab:** tap once. A Round makes a round dot, a Chisel a slanted oval at its angle, a Stamp its shape, a
   Stipple dots. In Procreate's own Brush Studio, Shape shows the dab: white is what paints.
4. **Size:** does the starting size look close to Studio's sample? (It's a guess; note too big or too small.)
5. **Pressure:** press light then hard. Press Wide, Press Dark and Press Both should do what they say.
6. **Tapers:** a quick stroke with Taper In and Out thins at the ends.
7. **Paper:** a brush on Cold Press, Canvas or another paper shows the paper's texture, still under the stroke, not
   sliding with it; Smooth paper shows none.
8. **Wash:** strokes over each other darken (Multiply), with a darker rim if it had Pooling.
9. **Tilt and speed** (Pencil that reports tilt): Tilt Shade lightens when tilted; Flick thins a fast stroke.
10. **Twice:** import the same file again: a second set, no crash. Then edit one brush in Procreate's Brush Studio and
    draw with it: nothing breaks.

Note the Procreate version (Settings, then Procreate, or the wrench's Help) and the Pencil.

## Expected differences

Drying, Smudge, Bleed, Bloom, Spatter and the rest of Inkwash's living ink don't travel; an exported brush is a
starting point, not a twin. Greys become opacity, so a brush paints in any colour.

## Report back

For each brush: imports (yes or no), dab right, size (small, right, big), pressure, paper, anything odd. A screenshot
of anything blank or broken helps most.
