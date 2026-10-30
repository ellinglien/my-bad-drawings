# A tour of My Bad Drawings

A short map of the screens. The full story of each tool, and why it works the way it does, is in
[CHANGELOG.md](../CHANGELOG.md) and [DESIGN.md](DESIGN.md).

## The shelf and notebooks

The app opens on the notebook you were last in; the shelf shows the rest. A notebook has up to 100 pages and its
own settings: a page template (or one of the tablet's own templates), which tools it keeps, rules such as No
Erasing, One Brush and Timed Pages, a cover design, and an optional Self-Portrait Prompt dealt to each new page.
Notebooks are saved as you draw, in `Document/My Bad Drawings` on the tablet; PNG and PDF copies are made only when
you ask (Export).

## The drawing screen

- **The side bar**: Menu at the top, then ten brush slots, the smudge tool and the eraser, a box for the size and
  grey of the tool in hand, and a few small buttons (Shapes, Play, the timer, show or hide each kind of mark).
  Tap a slot to draw with it; hold it to pick another brush for it.
- **The page bar**, along the top: the notebook, Zoom and the page count, which opens the page previews.
- **Fingers**: one finger does what the menu's Finger Action row says (Eraser, Smudge, a brush, or Nothing); two fingers tap to undo,
  three to redo; with Zoom unlocked, two fingers pinch and pan.
- **Play** opens the mischief, such as Mirrors that copy each stroke and Magnets (fridge-poetry words).
  **Replay Page** draws a page again, stroke by stroke.
- **Lasso** picks up part of a page to move, size or turn; **Import** places a picture or a PDF page.

## Brushes

Every brush is built from six parts (Tip, Medium, Paper, Drying, Control and Smudge), each with a few dials, and saved as
a short share code. The **Tools page** is the library: ready brushes by style (Fineliner, Brush pen, Dip pen, Marker,
Pencil, Charcoal, Ink wash, Watercolor, Gouache, Oils) and your own. **Brush Lab** makes and changes them, with a
test page beside the dials. A few brushes are deliberately unpredictable: Vanishing ink and Lemon juice (ink that
fades, or appears minutes later), Mold, Flick and Fling, Spirograph, Stamp and Crayon. They say what they do, and
the plain pens never surprise you.

[Brush Studio](https://ell.ing/brush-studio/) (source in `brush-lab/studio-src`) reads and writes the same share
codes on a computer, and exports brush sets for Procreate.

## Wet media

Washes and watercolour stay wet for a few seconds, then set. With gravity on, wet paint runs toward whichever edge
of the tablet is tipped down. Hold the eraser to choose what it takes: Everything, or only Pen, Paint or Dry
Media.
